package com.dergoogler.mmrl.lsposed

import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.dergoogler.mmrl.platform.stub.IVectorCli
import com.topjohnwu.superuser.ipc.RootService
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Root-only, deliberately narrow Vector command executor. No general shell service. */
class VectorCliRootService : RootService() {
    private val bridge = object : IVectorCli.Stub() {
        override fun execute(operation: String?, modulePackage: String?, targetPackage: String?): String {
            if (Process.myUid() != 0) return failed("Root service is not UID 0")
            val module = modulePackage.orEmpty()
            val target = targetPackage.orEmpty()
            val args = when (operation) {
                "status" -> if (module.isEmpty() && target.isEmpty()) listOf("status") else null
                "modules ls" -> if (module.isEmpty() && target.isEmpty()) listOf("modules", "ls") else null
                "modules enable", "modules disable", "scope ls" -> {
                    if (valid(module) && target.isEmpty())
                        listOf(operation.substringBefore(' '), operation.substringAfter(' '), module)
                    else null
                }
                "scope add", "scope rm" -> {
                    if (valid(module) && target.endsWith("/0") && valid(target.removeSuffix("/0")))
                        listOf("scope", operation.substringAfter(' '), module, target)
                    else null
                }
                else -> null
            } ?: return failed("Operation not allowlisted")
            return try {
                executeFixed(args)
            } catch (e: Exception) {
                // Do not return root logs or arbitrary command stdout on failures.
                failed("Vector command unavailable (${e.javaClass.simpleName})")
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = bridge

    private fun failed(detail: String): String =
        JSONObject().put("mmrl_transport_error", detail.take(160)).toString()

    private fun valid(pkg: String): Boolean = pkg.length <= 180 && PACKAGE.matches(pkg)

    private fun executeFixed(args: List<String>): String {
        val process = ProcessBuilder(listOf(CLI, "--json") + args)
            .redirectErrorStream(true).start()
        val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "mmrl-vector-root-reader").apply { isDaemon = true }
        }
        try {
            val output = worker.submit(Callable {
                process.inputStream.use { stream ->
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        if (bytes.size() + n > MAX_BYTES) throw IOException("Vector output too large")
                        bytes.write(buffer, 0, n)
                    }
                    bytes.toString(Charsets.UTF_8.name())
                }
            })
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                return failed("Vector command timed out")
            val text = output.get(2L, TimeUnit.SECONDS).trim()
            if (process.exitValue() != 0) return failed("Vector CLI exited nonzero")
            if (text.isEmpty()) return failed("Vector CLI returned no JSON")
            // Never claim success for a non-JSON CLI response.
            JSONObject(text)
            return text
        } finally {
            process.destroyForcibly()
            worker.shutdownNow()
        }
    }

    private companion object {
        const val CLI = "/data/adb/modules/zygisk_vector/cli"
        const val MAX_BYTES = 131072
        const val TIMEOUT_SECONDS = 12L
        val PACKAGE = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    }
}
