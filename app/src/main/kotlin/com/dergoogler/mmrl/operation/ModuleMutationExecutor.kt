package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.model.local.LocalModule
import com.dergoogler.mmrl.model.local.State
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** Application-scoped bridge for callback-based privileged module state mutations. */
@Singleton
class ModuleMutationExecutor @Inject constructor(
    private val history: OperationHistoryRepository,
    private val coordinator: PrivilegedOperationCoordinator,
    private val modulesRepository: ModulesRepository,
) {
    suspend fun execute(
        module: LocalModule,
        useShell: Boolean,
        kind: OperationKind,
        action: OperationAction,
        rollbackAction: OperationAction?,
        successSummary: String,
        parentId: String? = null,
    ): Result<String> = runCatching {
        require(action in STATE_ACTIONS) { "Unsupported module state action: $action" }
        val historyId = history.start(
            kind = kind,
            title = module.name,
            summary = kind.name.lowercase().replaceFirstChar(Char::uppercaseChar),
            moduleId = module.id.id,
            moduleName = module.name,
            retryAction = action,
            rollbackAction = rollbackAction,
            useShell = useShell,
            parentId = parentId,
            initialStatus = com.dergoogler.mmrl.database.entity.history.OperationStatus.QUEUED,
        )
        val key = "module-state:${module.id.id}:${action.name}"
        if (!history.claimIdempotencyKey(historyId, key)) {
            history.fail(historyId, "An identical module mutation is already active")
            error("An identical module mutation is already active")
        }
        when (val completion = executeExisting(
            historyId = historyId,
            module = module,
            useShell = useShell,
            action = action,
            rollbackAction = rollbackAction,
            successSummary = successSummary,
            claimIdempotency = false,
        )) {
            is PrivilegedOperationCoordinator.OperationCompletion.Success -> historyId
            is PrivilegedOperationCoordinator.OperationCompletion.Failure -> error(completion.summary)
            is PrivilegedOperationCoordinator.OperationCompletion.Cancelled -> error(completion.summary)
            is PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown -> error(completion.summary)
        }
    }

    /** Uses a pre-existing history row (for durable workers such as Tasker) without creating a second operation. */
    suspend fun executeExisting(
        historyId: String,
        module: LocalModule,
        useShell: Boolean,
        action: OperationAction,
        rollbackAction: OperationAction?,
        successSummary: String,
        claimIdempotency: Boolean = true,
    ): PrivilegedOperationCoordinator.OperationCompletion<Unit> {
        require(action in STATE_ACTIONS) { "Unsupported module state action: $action" }
        if (claimIdempotency) {
            val key = "module-state:${module.id.id}:${action.name}"
            if (!history.claimIdempotencyKey(historyId, key)) {
                return PrivilegedOperationCoordinator.OperationCompletion.Failure(
                    "An identical module mutation is already active",
                )
            }
        }
        history.appendLog(historyId, "Module ID: ${module.id.id}")
        history.appendLog(historyId, "Root backend: ${PlatformManager.platform.name}")

        return coordinator.execute(historyId, CALLBACK_TIMEOUT_MS) {
            markMutationStarted()
            phase(OperationPhase.INSTALL, "Applying ${action.name.lowercase()} state")
            val callbackResult = CompletableDeferred<CallbackResult>()
            val callback = object : IModuleOpsCallback.Stub() {
                override fun onSuccess(id: ModId) {
                    callbackResult.complete(CallbackResult(true, id, null))
                }

                override fun onFailure(id: ModId, msg: String?) {
                    callbackResult.complete(CallbackResult(false, id, msg))
                }
            }
            when (action) {
                OperationAction.ENABLE -> PlatformManager.moduleManager.enable(module.id, useShell, callback)
                OperationAction.DISABLE -> PlatformManager.moduleManager.disable(module.id, useShell, callback)
                OperationAction.REMOVE -> PlatformManager.moduleManager.remove(module.id, useShell, callback)
                else -> error("Unsupported module state action: $action")
            }
            val signal = withTimeout(CALLBACK_TIMEOUT_MS) { callbackResult.await() }
            phase(OperationPhase.RECONCILE, "Reconciling module state from backend")
            val actual = runCatching { PlatformManager.moduleManager.getModuleById(module.id) }.getOrNull()
            val expected = expectedState(action)
            when (
                ModuleMutationReconciliationPolicy.classify(
                    callbackSucceeded = signal.success,
                    callbackIdentityMatches = signal.id == module.id,
                    backendMatchesExpected = actual?.state == expected,
                    backendIsUnchanged = actual?.state == module.state,
                )
            ) {
                ModuleMutationReconciliationPolicy.Outcome.SUCCESS -> {
                    val finalizationError = runCatching {
                        actual?.let { modulesRepository.getLocal(it.id) }
                    }.exceptionOrNull()
                    when (
                        VerifiedMutationFinalizationPolicy.classify(
                            backendVerified = true,
                            finalizationSucceeded = finalizationError == null,
                        )
                    ) {
                        VerifiedMutationFinalizationPolicy.Outcome.SUCCESS ->
                            PrivilegedOperationCoordinator.OperationCompletion.Success(
                                value = Unit,
                                summary = successSummary,
                                requiresReboot = true,
                                rollbackAction = rollbackAction,
                            )
                        VerifiedMutationFinalizationPolicy.Outcome.KNOWN_APPLIED_FINALIZATION_FAILED ->
                            PrivilegedOperationCoordinator.OperationCompletion.Failure(
                                summary = "Backend state confirms the module mutation was applied, but local state refresh failed",
                                error = finalizationError,
                                requiresReboot = true,
                                rollbackAction = rollbackAction,
                                retryable = false,
                            )
                        VerifiedMutationFinalizationPolicy.Outcome.OUTCOME_UNKNOWN ->
                            PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                                summary = "Module backend state could not be verified; reconcile before retrying",
                                requiresReboot = true,
                                rollbackAction = rollbackAction,
                            )
                    }
                }
                ModuleMutationReconciliationPolicy.Outcome.FAILURE ->
                    PrivilegedOperationCoordinator.OperationCompletion.Failure(
                        summary = signal.message ?: "Module operation failed",
                        retryable = ModuleMutationRetryPolicy.isRetryable(signal.message),
                    )
                ModuleMutationReconciliationPolicy.Outcome.OUTCOME_UNKNOWN ->
                    PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                        summary = signal.message ?: "Module callback/backend state disagree; reconcile before retrying",
                        requiresReboot = true,
                        rollbackAction = rollbackAction,
                    )
            }
        }
    }

    private fun expectedState(action: OperationAction): State = when (action) {
        OperationAction.ENABLE -> State.ENABLE
        OperationAction.DISABLE -> State.DISABLE
        OperationAction.REMOVE -> State.REMOVE
        else -> error("Unsupported module state action: $action")
    }

    private data class CallbackResult(val success: Boolean, val id: ModId, val message: String?)

    companion object {
        private val STATE_ACTIONS = setOf(OperationAction.ENABLE, OperationAction.DISABLE, OperationAction.REMOVE)
        const val CALLBACK_TIMEOUT_MS = 30_000L
    }
}
