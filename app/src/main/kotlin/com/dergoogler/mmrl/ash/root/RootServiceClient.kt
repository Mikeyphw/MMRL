package com.dergoogler.mmrl.ash.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class RootServiceClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val bindMutex = Mutex()
    private val callMutex = Mutex()

    @Volatile
    private var remote: IAshReXcueService? = null

    @Volatile
    private var activeConnection: ServiceConnection? = null

    suspend fun rootAvailable(): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(ROOT_TIMEOUT_MS) {
            runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        } ?: false
    }

    suspend fun moduleState(): String = call(MODULE_STATE_TIMEOUT_MS) { it.moduleState() }
    suspend fun serviceInfo(): String = call { it.serviceInfo() }
    suspend fun capabilities(): String = call(CAPABILITIES_TIMEOUT_MS) { it.capabilities() }
    suspend fun snapshot(activityLimit: Int = 150): String = call(SNAPSHOT_TIMEOUT_MS) { it.snapshot(activityLimit) }
    suspend fun releaseGate(): String = call(RELEASE_GATE_TIMEOUT_MS) { it.releaseGate() }
    suspend fun setSetting(key: String, value: String): String = call { it.setSetting(key, value) }

    suspend fun setSettings(values: Map<String, String>): String {
        val entries = values.entries.toList()
        return call { service ->
            service.setSettings(
                entries.map { entry -> entry.key }.toTypedArray(),
                entries.map { entry -> entry.value }.toTypedArray(),
            )
        }
    }

    suspend fun setTrust(folder: String, trust: String): String = call { it.setTrust(folder, trust) }
    suspend fun restoreOne(folder: String): String = call { it.restoreOne(folder) }
    suspend fun restoreHalf(): String = call { it.restoreHalf() }
    suspend fun restoreBatch(folders: List<String>): String = call { it.restoreBatch(folders.toTypedArray()) }
    suspend fun executeRecoveryPlan(planId: String, recoveryRevision: String, folders: List<String>): String =
        call { it.executeRecoveryPlan(planId, recoveryRevision, folders.toTypedArray()) }
    suspend fun restoreAll(): String = call { it.restoreAll() }
    suspend fun completeTrial(): String = call { it.completeTrial() }
    suspend fun rollbackTrial(): String = call { it.rollbackTrial() }
    suspend fun discardPendingSettings(): String = call { it.discardPendingSettings() }
    suspend fun exportDiagnostics(): String = call(EXPORT_TIMEOUT_MS) { it.exportDiagnostics() }
    suspend fun repairState(): String = call(REPAIR_TIMEOUT_MS) { it.repairState() }

    fun release() {
        invalidateConnection()
    }

    private suspend fun call(
        timeoutMs: Long = CALL_TIMEOUT_MS,
        block: (IAshReXcueService) -> String,
    ): String = try {
        withTimeout(timeoutMs) {
            callMutex.withLock {
                withContext(Dispatchers.IO) {
                    var lastError: Throwable? = null
                    repeat(MAX_CALL_ATTEMPTS) { attempt ->
                        try {
                            return@withContext block(service())
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            lastError = error
                            invalidateConnection()
                            if (attempt + 1 < MAX_CALL_ATTEMPTS) return@repeat
                        }
                    }
                    throw requireNotNull(lastError)
                }
            }
        }
    } catch (error: TimeoutCancellationException) {
        invalidateConnection()
        """{"ok":false,"message":"AshReXcue root call timed out"}"""
    }

    private fun invalidateConnection() {
        remote = null
        activeConnection?.let { connection ->
            activeConnection = null
            runCatching { RootService.unbind(connection) }
        }
    }

    private suspend fun service(): IAshReXcueService = remote ?: bindMutex.withLock {
        remote ?: bindService().also { remote = it }
    }

    private suspend fun bindService(): IAshReXcueService =
        withTimeout(BIND_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    val intent = Intent(context, AshRootService::class.java)
                    lateinit var connection: ServiceConnection

                    connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            val service = IAshReXcueService.Stub.asInterface(binder)
                            remote = service
                            activeConnection = this
                            if (continuation.isActive) continuation.resume(service)
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            if (activeConnection === this) activeConnection = null
                            remote = null
                        }

                        override fun onBindingDied(name: ComponentName) {
                            onServiceDisconnected(name)
                        }

                        override fun onNullBinding(name: ComponentName) {
                            if (activeConnection === this) activeConnection = null
                            remote = null
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException("Root service returned no binder"),
                                )
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        if (activeConnection === connection) activeConnection = null
                        runCatching { RootService.unbind(connection) }
                    }

                    runCatching { RootService.bind(intent, connection) }
                        .onFailure { error ->
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                }
            }
        }

    private companion object {
        const val ROOT_TIMEOUT_MS = 8_000L
        const val BIND_TIMEOUT_MS = 20_000L
        const val MODULE_STATE_TIMEOUT_MS = 25_000L
        const val CAPABILITIES_TIMEOUT_MS = 25_000L
        const val SNAPSHOT_TIMEOUT_MS = 90_000L
        const val RELEASE_GATE_TIMEOUT_MS = 35_000L
        const val CALL_TIMEOUT_MS = 40_000L
        const val EXPORT_TIMEOUT_MS = 135_000L
        const val REPAIR_TIMEOUT_MS = 90_000L
        const val MAX_CALL_ATTEMPTS = 2
    }
}
