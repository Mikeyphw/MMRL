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
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class RootServiceClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val bindMutex = Mutex()
    private val callMutex = Mutex()
    private val connectionStateLock = Any()

    @Volatile
    private var remote: IAshReXcueService? = null

    @Volatile
    private var activeConnection: ServiceConnection? = null

    @Volatile
    private var activeBinder: IBinder? = null

    @Volatile
    private var deathRecipient: IBinder.DeathRecipient? = null

    suspend fun rootAvailable(): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(ROOT_TIMEOUT_MS) {
            runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        } ?: false
    }

    suspend fun moduleState(): String = call(RootCallKind.READ_ONLY, MODULE_STATE_TIMEOUT_MS) { it.moduleState() }
    suspend fun serviceInfo(): String = call(RootCallKind.READ_ONLY) { it.serviceInfo() }
    suspend fun capabilities(): String = call(RootCallKind.READ_ONLY, CAPABILITIES_TIMEOUT_MS) { it.capabilities() }
    suspend fun snapshot(activityLimit: Int = 150): String = call(RootCallKind.READ_ONLY, SNAPSHOT_TIMEOUT_MS) { it.snapshot(activityLimit) }
    suspend fun releaseGate(): String = call(RootCallKind.READ_ONLY, RELEASE_GATE_TIMEOUT_MS) { it.releaseGate() }
    suspend fun setSetting(key: String, value: String): String = call(RootCallKind.MUTATION) { it.setSetting(key, value) }

    suspend fun setSettings(values: Map<String, String>): String {
        val entries = values.entries.toList()
        return call(RootCallKind.MUTATION) { service ->
            service.setSettings(
                entries.map { entry -> entry.key }.toTypedArray(),
                entries.map { entry -> entry.value }.toTypedArray(),
            )
        }
    }

    suspend fun setTrust(folder: String, trust: String): String = call(RootCallKind.MUTATION) { it.setTrust(folder, trust) }
    suspend fun restoreOne(folder: String): String = call(RootCallKind.MUTATION) { it.restoreOne(folder) }
    suspend fun restoreHalf(): String = call(RootCallKind.MUTATION) { it.restoreHalf() }
    suspend fun restoreBatch(folders: List<String>): String = call(RootCallKind.MUTATION) { it.restoreBatch(folders.toTypedArray()) }
    suspend fun executeRecoveryPlan(planId: String, recoveryRevision: String, folders: List<String>): String =
        call(RootCallKind.MUTATION) { it.executeRecoveryPlan(planId, recoveryRevision, folders.toTypedArray()) }
    suspend fun restoreAll(): String = call(RootCallKind.MUTATION) { it.restoreAll() }
    suspend fun completeTrial(): String = call(RootCallKind.MUTATION) { it.completeTrial() }
    suspend fun rollbackTrial(): String = call(RootCallKind.MUTATION) { it.rollbackTrial() }
    suspend fun discardPendingSettings(): String = call(RootCallKind.MUTATION) { it.discardPendingSettings() }
    suspend fun exportDiagnostics(): String = call(RootCallKind.READ_ONLY, EXPORT_TIMEOUT_MS) { it.exportDiagnostics() }
    suspend fun repairState(): String = call(RootCallKind.MUTATION, REPAIR_TIMEOUT_MS) { it.repairState() }

    fun release() {
        invalidateConnection()
    }

    private suspend fun call(
        kind: RootCallKind,
        timeoutMs: Long = CALL_TIMEOUT_MS,
        block: (IAshReXcueService) -> String,
    ): String = try {
        withTimeout(timeoutMs) {
            callMutex.withLock {
                withContext(Dispatchers.IO) {
                    var lastError: Throwable? = null
                    val attempts = RootCallPolicy.maxAttempts(kind)
                    repeat(attempts) { attempt ->
                        try {
                            return@withContext block(service())
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            lastError = error
                            invalidateConnection()
                            if (attempt + 1 < attempts) return@repeat
                        }
                    }
                    throw requireNotNull(lastError)
                }
            }
        }
    } catch (error: TimeoutCancellationException) {
        invalidateConnection()
        RootCallPolicy.transportFailure(kind, "AshReXcue root call timed out")
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        invalidateConnection()
        RootCallPolicy.transportFailure(kind, error.message ?: "AshReXcue root transport failed")
    }

    /**
     * Clear only the connection/binder generation that the caller observed.  A delayed death
     * callback from an older Binder must never invalidate a freshly reconnected Binder.
     */
    private fun invalidateConnection(
        expectedConnection: ServiceConnection? = null,
        expectedBinder: IBinder? = null,
        releaseBinding: Boolean = true,
    ): Boolean {
        val snapshot = synchronized(connectionStateLock) {
            if (expectedConnection != null && activeConnection !== expectedConnection) return false
            if (expectedBinder != null && activeBinder !== expectedBinder) return false

            val binder = activeBinder
            val recipient = deathRecipient
            val connection = activeConnection
            remote = null
            activeBinder = null
            deathRecipient = null
            if (releaseBinding) activeConnection = null
            Triple(binder, recipient, if (releaseBinding) connection else null)
        }

        val (binder, recipient, connection) = snapshot
        if (binder != null && recipient != null) {
            runCatching { binder.unlinkToDeath(recipient, 0) }
        }
        if (connection != null) runCatching { RootService.unbind(connection) }
        return true
    }

    private fun liveRemote(): IAshReXcueService? {
        val snapshot = synchronized(connectionStateLock) { remote to activeBinder }
        val service = snapshot.first ?: return null
        val binder = snapshot.second
        if (binder != null && binder.isBinderAlive && binder.pingBinder()) return service
        invalidateConnection(expectedBinder = binder, releaseBinding = true)
        return null
    }

    private fun hasTrackedBinding(): Boolean =
        synchronized(connectionStateLock) { activeConnection != null }

    private fun isTrackedConnection(connection: ServiceConnection): Boolean =
        synchronized(connectionStateLock) { activeConnection === connection }

    private suspend fun awaitFrameworkReconnect(): IAshReXcueService? =
        withTimeoutOrNull(FRAMEWORK_RECONNECT_GRACE_MS) {
            while (hasTrackedBinding()) {
                liveRemote()?.let { return@withTimeoutOrNull it }
                delay(RECONNECT_POLL_MS)
            }
            null
        }

    private suspend fun service(): IAshReXcueService = liveRemote() ?: bindMutex.withLock {
        liveRemote()?.let { return@withLock it }
        when (RootBindingLifecyclePolicy.acquireAction(false, hasTrackedBinding())) {
            RootBindingLifecyclePolicy.AcquireAction.REUSE_LIVE -> liveRemote()
            RootBindingLifecyclePolicy.AcquireAction.WAIT_FOR_TRACKED_RECONNECT -> {
                awaitFrameworkReconnect() ?: run {
                    invalidateConnection(releaseBinding = true)
                    null
                }
            }
            RootBindingLifecyclePolicy.AcquireAction.BIND_NEW -> null
        } ?: bindService()
    }

    private suspend fun bindService(): IAshReXcueService =
        withTimeout(BIND_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    val intent = Intent(context, AshRootService::class.java)
                    val cancelled = AtomicBoolean(false)
                    val initialDelivered = AtomicBoolean(false)
                    val bindingReleased = AtomicBoolean(false)
                    val binderRef = AtomicReference<IBinder?>(null)
                    val candidateRef = AtomicReference<RootBinderCandidateGate?>(null)
                    lateinit var connection: ServiceConnection

                    fun releaseUnpublishedBinding() {
                        if (bindingReleased.compareAndSet(false, true)) {
                            runCatching { RootService.unbind(connection) }
                        }
                    }

                    connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            if (bindingReleased.get()) {
                                runCatching { RootService.unbind(this) }
                                return
                            }

                            val service = IAshReXcueService.Stub.asInterface(binder)
                            val candidate = RootBinderCandidateGate()
                            candidateRef.set(candidate)
                            binderRef.set(binder)

                            lateinit var recipient: IBinder.DeathRecipient
                            recipient = IBinder.DeathRecipient {
                                candidate.lose {
                                    val cleared = invalidateConnection(
                                        expectedConnection = this,
                                        expectedBinder = binder,
                                        releaseBinding = true,
                                    )
                                    if (cleared) bindingReleased.set(true)
                                }
                            }

                            try {
                                binder.linkToDeath(recipient, 0)
                            } catch (error: Throwable) {
                                releaseUnpublishedBinding()
                                if (continuation.isActive) continuation.resumeWithException(error)
                                return
                            }

                            val initial = !initialDelivered.get()
                            val published = candidate.publish(
                                eligible = {
                                    !bindingReleased.get() &&
                                        RootBindingLifecyclePolicy.canPublish(initial, isTrackedConnection(this)) &&
                                        (!initial || (!cancelled.get() && continuation.isActive)) &&
                                        binder.isBinderAlive && binder.pingBinder()
                                },
                            ) {
                                synchronized(connectionStateLock) {
                                    // If cancellation races after eligibility, candidate.cancel() waits for this
                                    // publication and then generation-matched cleanup removes it before cancellation returns.
                                    activeConnection = this
                                    activeBinder = binder
                                    deathRecipient = recipient
                                    remote = service
                                }
                            }

                            if (!published) {
                                runCatching { binder.unlinkToDeath(recipient, 0) }
                                if (initial) {
                                    releaseUnpublishedBinding()
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(
                                            IllegalStateException("Root service Binder died or bind was cancelled before publication"),
                                        )
                                    }
                                }
                                return
                            }

                            if (initial) {
                                initialDelivered.set(true)
                                continuation.resume(service) { _ ->
                                    cancelled.set(true)
                                    candidate.cancel {
                                        invalidateConnection(
                                            expectedConnection = this,
                                            expectedBinder = binder,
                                            releaseBinding = true,
                                        )
                                    }
                                    bindingReleased.set(true)
                                }
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            val binder = binderRef.getAndSet(null)
                            if (!initialDelivered.get()) {
                                releaseUnpublishedBinding()
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        IllegalStateException("Root service disconnected before initial connection"),
                                    )
                                }
                                return
                            }
                            val action = RootBindingLifecyclePolicy.action(RootBindingLifecyclePolicy.Event.DISCONNECTED)
                            if (binder != null && action.clearRemote) {
                                invalidateConnection(
                                    expectedConnection = this,
                                    expectedBinder = binder,
                                    releaseBinding = action.releaseBinding,
                                )
                            }
                        }

                        override fun onBindingDied(name: ComponentName) {
                            val action = RootBindingLifecyclePolicy.action(RootBindingLifecyclePolicy.Event.BINDING_DIED)
                            bindingReleased.set(action.releaseBinding)
                            invalidateConnection(expectedConnection = this, releaseBinding = action.releaseBinding)
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("Root service binding died"))
                            }
                        }

                        override fun onNullBinding(name: ComponentName) {
                            val action = RootBindingLifecyclePolicy.action(RootBindingLifecyclePolicy.Event.NULL_BINDING)
                            bindingReleased.set(action.releaseBinding)
                            invalidateConnection(expectedConnection = this, releaseBinding = action.releaseBinding)
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("Root service returned no binder"))
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        cancelled.set(true)
                        val binder = binderRef.get()
                        candidateRef.get()?.cancel {
                            invalidateConnection(
                                expectedConnection = connection,
                                expectedBinder = binder,
                                releaseBinding = true,
                            )
                        }
                        bindingReleased.set(true)
                        if (!invalidateConnection(expectedConnection = connection, releaseBinding = true)) {
                            runCatching { RootService.unbind(connection) }
                        }
                    }

                    runCatching { RootService.bind(intent, connection) }
                        .onFailure { error ->
                            bindingReleased.set(true)
                            runCatching { RootService.unbind(connection) }
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                }
            }
        }

    private companion object {
        const val ROOT_TIMEOUT_MS = 8_000L
        const val BIND_TIMEOUT_MS = 20_000L
        const val FRAMEWORK_RECONNECT_GRACE_MS = 2_000L
        const val RECONNECT_POLL_MS = 25L
        const val MODULE_STATE_TIMEOUT_MS = 25_000L
        const val CAPABILITIES_TIMEOUT_MS = 25_000L
        const val SNAPSHOT_TIMEOUT_MS = 90_000L
        const val RELEASE_GATE_TIMEOUT_MS = 35_000L
        const val CALL_TIMEOUT_MS = 40_000L
        const val EXPORT_TIMEOUT_MS = 135_000L
        const val REPAIR_TIMEOUT_MS = 90_000L
    }
}
