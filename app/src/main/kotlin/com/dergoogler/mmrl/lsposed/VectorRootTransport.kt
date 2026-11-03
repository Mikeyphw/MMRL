package com.dergoogler.mmrl.lsposed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.dergoogler.mmrl.platform.stub.IVectorCli
import com.topjohnwu.superuser.ipc.RootService
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * Root IPC bootstrap on main looper, CLI calls on caller's IO worker.
 * One non-daemon libsu connection per application process; no raw su child.
 */
internal object VectorRootTransport {
    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val CALL_TIMEOUT_SECONDS = 17L
    private const val MAX_BYTES = 131072
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    @Volatile private var connected: IVectorCli? = null
    private var binding: ServiceConnection? = null

    fun execute(context: Context, operation: String, module: String, target: String): JSONObject {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw IOException("Vector root IPC must run off the main thread")
        val remote = connect(context.applicationContext)
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "mmrl-vector-ipc").apply { isDaemon = true }
        }
        val raw = try {
            executor.submit(Callable {
                remote.execute(operation, module, target)
            }).get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            reset()
            throw IOException("Vector root IPC command timed out", e)
        } catch (e: Exception) {
            reset()
            throw IOException("Vector root IPC unavailable (${e.javaClass.simpleName})", e)
        } finally {
            executor.shutdownNow()
        }
        if (raw.length > MAX_BYTES) throw IOException("Vector IPC response too large")
        val reply = try { JSONObject(raw) } catch (e: Exception) {
            throw IOException("Vector root IPC returned invalid JSON", e)
        }
        if (reply.has("mmrl_transport_error"))
            throw IOException("Vector root transport: " + reply.optString("mmrl_transport_error").take(160))
        return reply
    }

    private fun connect(context: Context): IVectorCli = synchronized(lock) {
        connected?.takeIf { it.asBinder().isBinderAlive }?.let { return@synchronized it }
        binding?.let { old -> main.post { runCatching { RootService.unbind(old) } } }
        connected = null
        binding = null
        val ready = CountDownLatch(1)
        val service = AtomicReference<IVectorCli?>(null)
        val problem = AtomicReference<String?>(null)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service.set(IVectorCli.Stub.asInterface(binder))
                ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                connected = null
                problem.set("Root service disconnected")
                ready.countDown()
            }
            override fun onBindingDied(name: ComponentName?) {
                connected = null
                problem.set("Root service binding died")
                ready.countDown()
            }
            override fun onNullBinding(name: ComponentName) {
                problem.set("Root service returned null binder")
                ready.countDown()
            }
        }
        val scheduled = main.post {
            try {
                RootService.bind(Intent(context, VectorCliRootService::class.java), conn)
            } catch (e: Exception) {
                problem.set("Root service bind failed (${e.javaClass.simpleName})")
                ready.countDown()
            }
        }
        if (!scheduled) {
            main.post { runCatching { RootService.unbind(conn) } }
            throw IOException("Vector root IPC [BIND_DISPATCH_FAILED]: main looper rejected binding")
        }
        val signalled = try {
            ready.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            main.post { runCatching { RootService.unbind(conn) } }
            throw IOException("Vector root IPC [BIND_INTERRUPTED]: service binding interrupted", e)
        }
        if (!signalled) {
            main.post { runCatching { RootService.unbind(conn) } }
            throw IOException("Vector root IPC [BIND_TIMEOUT]: service did not connect within 20 seconds")
        }
        val bound = service.get()
        if (bound == null || !bound.asBinder().isBinderAlive) {
            main.post { runCatching { RootService.unbind(conn) } }
            throw IOException("Vector root IPC [BIND_FAILED]: " +
                (problem.get() ?: "service returned no live binder"))
        }
        binding = conn
        connected = bound
        bound
    }

    private fun reset() = synchronized(lock) {
        connected = null
        binding?.let { old -> main.post { runCatching { RootService.unbind(old) } } }
        binding = null
    }
}
