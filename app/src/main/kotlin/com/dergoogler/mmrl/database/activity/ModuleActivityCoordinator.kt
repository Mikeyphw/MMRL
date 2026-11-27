package com.dergoogler.mmrl.database.activity

import androidx.compose.runtime.mutableStateListOf
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.content.State
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.repository.ModulesRepository
import io.github.mikeyphw.mmrl.shared.OperationKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** App-process ownership, independent of the initiating screen's lifecycle. No automatic retry. */
@Singleton
class ModuleActivityCoordinator internal constructor(
    private val history: ActivityHistoryRepository,
    private val modules: ModulesRepository,
    private val scope: CoroutineScope,
    private val callbackTimeoutMillis: Long,
) {
    @Inject constructor(history: ActivityHistoryRepository, modules: ModulesRepository) : this(
        history, modules, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), 60_000L,
    )
    private val tickets = ModuleMutationTickets<ModId>()
    private val busy = mutableStateListOf<ModId>()
    fun isRunning(id: ModId): Boolean = busy.contains(id)

    fun run(id: ModId, kind: OperationKind, dispatch: (IModuleOpsCallback) -> Unit) {
        scope.launch {
            // Reserve before any suspending DB operation; shared across both screens.
            val ticket = tickets.reserve(id) ?: return@launch
            busy.add(id)
            fun release() {
                if (tickets.release(ticket)) busy.remove(id)
            }
            val operation = try { history.beginOperation(kind) } catch (_: Exception) {
                android.util.Log.e("MMRLActivity", "Operation admission unavailable; action not dispatched")
                release()
                return@launch
            }
            val outcome = CompletableDeferred<Boolean>()
            fun receive(reportedId: ModId, acknowledged: Boolean) {
                // Binder callbacks can race, repeat, or report a different identity.
                if (tickets.claim(ticket, reportedId)) outcome.complete(acknowledged)
            }
            val callback = object : IModuleOpsCallback.Stub() {
                override fun onSuccess(id: ModId) = receive(id, true)
                override fun onFailure(id: ModId, msg: String?) = receive(id, false)
            }
            var dispatchReturned = false
            try {
                // Do not complete or release the ticket until the dispatch itself returns.
                withContext(Dispatchers.IO) { dispatch(callback) }
                dispatchReturned = true
            } catch (_: Exception) {
                android.util.Log.w("MMRLActivity", "Module dispatch outcome unknown")
            }
            // A missing callback is not proof of failure or a license to dispatch again.
            val acknowledged = if (dispatchReturned)
                withTimeoutOrNull(callbackTimeoutMillis) { outcome.await() } else null
            try {
                val observed = if (acknowledged == true) runCatching {
                    withContext(Dispatchers.IO) {
                        PlatformManager.moduleManager.getModuleById(id)
                            ?.takeIf { it.id.id == id.id && it.id.baseDir == id.baseDir }?.state
                    }
                }.getOrNull() else null
                val verified = when (kind) {
                    OperationKind.ENABLE -> observed == State.ENABLE
                    OperationKind.DISABLE -> observed == State.DISABLE
                    else -> false
                }
                // Verified configured state is not proof of active hooks or runtime changes.
                if (verified) history.success(operation, rebootRequired = true)
                else history.needsReconciliation(operation)
            } catch (_: Exception) {
                android.util.Log.e("MMRLActivity", "Operation outcome persistence unavailable")
                // Fail closed: do not allow another module request while its durable state is uncertain.
                return@launch
            }
            // Hold an uncertain dispatch until the real callback arrives. Its late result
            // never upgrades the stored unknown outcome, and nothing is automatically retried.
            if (!outcome.isCompleted) outcome.await()
            try { modules.getLocal(id) } catch (_: Exception) {
                android.util.Log.w("MMRLActivity", "Module refresh unavailable")
            } finally { release() }
        }
    }
}
