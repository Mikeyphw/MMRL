package com.dergoogler.mmrl.tasker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dergoogler.mmrl.BuildConfig
import com.dergoogler.mmrl.ash.automation.ASH_EXTERNAL_CONTROL_API_VERSION
import com.dergoogler.mmrl.ash.automation.ASH_EXTERNAL_CONTROL_SCHEMA
import com.dergoogler.mmrl.ash.automation.AshAutomationEntryPoint
import com.dergoogler.mmrl.ash.automation.AshExternalControlStore
import com.dergoogler.mmrl.ash.automation.toJson
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import com.dergoogler.mmrl.installer.ArchiveInspector
import com.dergoogler.mmrl.operation.PrivilegedOperationCoordinator
import com.dergoogler.mmrl.operation.WorkerCancellationPolicy
import com.dergoogler.mmrl.operation.VerifiedMutationFinalizationPolicy
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.hasAction
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.model.ModId.Companion.actionFile
import com.dergoogler.mmrl.platform.util.ShellCommand
import com.dergoogler.mmrl.utils.initPlatform
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class TaskerAutomationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val requestId = TaskerRootDispatcher.requestId(inputData) ?: return Result.failure()
        val request = TaskerRootRequestStore.get(applicationContext, requestId) ?: return Result.failure()
        val repos = TaskerRuntime.repositories(applicationContext)
        val history = repos.operationHistoryRepository()
        return try {
            val preferences = repos.userPreferencesRepository().data.first()
            TaskerAuthorizationPolicy.requireExecutionAllowed(applicationContext, preferences, request)
            if (request.command != "ASH_EXECUTE_PLAN") {
                if (!PlatformManager.isAlive) {
                    initPlatform(applicationContext, preferences.workingMode.toPlatform())
                }
                check(PlatformManager.isAlive) { "Root backend is unavailable" }
            }
            history.appendLog(request.operationId, "Tasker automation worker started after execution-time policy check")
            when (request.command) {
                "ENABLE", "DISABLE", "REMOVE" -> executeModuleState(request)
                "RUN_ACTION" -> executeModuleAction(request)
                "RESTORE" -> restorePreviousVersion(request)
                "EXECUTE_REVIEW" -> executeReviewedInstall(request)
                "ASH_EXECUTE_PLAN" -> executeAshRecoveryPlan(request)
                else -> error("Unsupported Tasker command: ${request.command}")
            }
            TaskerRootRequestStore.remove(applicationContext, request.id)
            Result.success()
        } catch (cancelled: CancellationException) {
            when (WorkerCancellationPolicy.disposition(repos.privilegedOperationCoordinator().isActive(request.operationId))) {
                WorkerCancellationPolicy.Disposition.DETACH_FROM_ACTIVE_COORDINATOR -> {
                    history.appendLog(
                        request.operationId,
                        "Worker caller cancelled; application-scoped privileged execution remains authoritative",
                    )
                }
                WorkerCancellationPolicy.Disposition.CANCEL_QUEUED_OPERATION -> {
                    history.appendLog(request.operationId, "Worker cancelled before a process-scoped privileged execution was active")
                    repos.privilegedOperationCoordinator().requestCancellation(request.operationId)
                    request.reviewToken?.let { TaskerReviewTokenStore.remove(applicationContext, it) }
                    TaskerRootRequestStore.remove(applicationContext, request.id)
                }
            }
            throw cancelled
        } catch (error: Throwable) {
            val current = history.getById(request.operationId)
            if (current?.isRunning != false) {
                history.fail(request.operationId, error.message ?: "Tasker operation failed", error)
            } else {
                history.appendLog(request.operationId, "Worker stopped after terminal state: ${error.message}")
            }
            if (request.command == "EXECUTE_REVIEW") {
                request.reviewToken?.let { TaskerReviewTokenStore.remove(applicationContext, it) }
            }
            if (request.command == "ASH_EXECUTE_PLAN") {
                completeAshFailureReceipt(request, error)
            }
            TaskerRootRequestStore.remove(applicationContext, request.id)
            Result.failure()
        }
    }

    private suspend fun executeAshRecoveryPlan(request: TaskerRootRequest) = withContext(Dispatchers.IO) {
        val token = request.ashAutomationToken ?: error("AshReXcue automation token is required")
        val idempotencyKey = request.idempotencyKey ?: error("AshReXcue idempotency key is required")
        val store = AshExternalControlStore(applicationContext)
        val record = store.claim(token, idempotencyKey, request.operationId)
        val history = TaskerRuntime.repositories(applicationContext).operationHistoryRepository()
        history.phase(request.operationId, OperationPhase.VERIFY, "Revalidating guarded AshReXcue recovery plan")
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AshAutomationEntryPoint::class.java,
        )
        val result = entryPoint.manager().executeRecoveryPlanTracked(
            plan = record.prepared.plan,
            existingHistoryId = request.operationId,
            externalIdempotencyKey = idempotencyKey,
        )
        val terminal = history.getById(request.operationId)
        val receiptStatus = when (terminal?.status) {
            OperationStatus.SUCCEEDED.name -> "SUCCEEDED"
            OperationStatus.OUTCOME_UNKNOWN.name -> "OUTCOME_UNKNOWN"
            OperationStatus.CANCELLED.name -> "CANCELLED"
            else -> "FAILED"
        }
        val data = JSONObject()
            .put("apiVersion", ASH_EXTERNAL_CONTROL_API_VERSION)
            .put("schema", ASH_EXTERNAL_CONTROL_SCHEMA)
            .put("operationId", request.operationId)
            .put("plan", record.prepared.plan.toJson())
            .put("message", result.message)
            .put("path", result.path.orEmpty())
            .put("status", receiptStatus)
        store.complete(
            tokenValue = token,
            operationId = request.operationId,
            status = receiptStatus,
            message = result.message,
            resultJson = data.toString(),
        )
        if (receiptStatus == "FAILED") error(result.message)
    }

    private suspend fun completeAshFailureReceipt(request: TaskerRootRequest, error: Throwable) {
        val token = request.ashAutomationToken ?: return
        val operationId = request.operationId
        val current = TaskerRuntime.repositories(applicationContext).operationHistoryRepository().getById(operationId)
        val receiptStatus = when (current?.status) {
            OperationStatus.OUTCOME_UNKNOWN.name -> "OUTCOME_UNKNOWN"
            OperationStatus.CANCELLED.name -> "CANCELLED"
            OperationStatus.SUCCEEDED.name -> "SUCCEEDED"
            else -> "FAILED"
        }
        runCatching {
            AshExternalControlStore(applicationContext).complete(
                tokenValue = token,
                operationId = operationId,
                status = receiptStatus,
                message = error.message ?: "AshReXcue recovery plan failed",
                resultJson = JSONObject()
                    .put("apiVersion", ASH_EXTERNAL_CONTROL_API_VERSION)
                    .put("schema", ASH_EXTERNAL_CONTROL_SCHEMA)
                    .put("operationId", operationId)
                    .put("status", receiptStatus)
                    .put("error", error.message ?: "AshReXcue recovery plan failed")
                    .toString(),
            )
        }
    }

    private suspend fun executeModuleState(request: TaskerRootRequest) {
        check(PlatformManager.isAlive) { "Root backend is unavailable" }
        val repos = TaskerRuntime.repositories(applicationContext)
        val prefs = repos.userPreferencesRepository().data.first()
        val module = repos.localRepository().getLocalByIdOrNull(request.moduleId)
            ?: error("Installed module not found")
        val action = when (request.command) {
            "ENABLE" -> OperationAction.ENABLE
            "DISABLE" -> OperationAction.DISABLE
            "REMOVE" -> OperationAction.REMOVE
            else -> error("Unsupported module state command: ${request.command}")
        }
        val completion = repos.moduleMutationExecutor().executeExisting(
            historyId = request.operationId,
            module = module,
            useShell = prefs.useShellForModuleStateChange,
            action = action,
            rollbackAction = when (action) {
                OperationAction.ENABLE -> OperationAction.DISABLE
                OperationAction.DISABLE, OperationAction.REMOVE -> OperationAction.ENABLE
                else -> null
            },
            successSummary = "${request.command.lowercase().replaceFirstChar(Char::uppercase)} completed; reboot required",
        )
        check(completion is PrivilegedOperationCoordinator.OperationCompletion.Success) {
            when (completion) {
                is PrivilegedOperationCoordinator.OperationCompletion.Failure -> completion.summary
                is PrivilegedOperationCoordinator.OperationCompletion.Cancelled -> completion.summary
                is PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown -> completion.summary
                else -> "Module state change failed"
            }
        }
    }

    private suspend fun executeModuleAction(request: TaskerRootRequest) = withContext(Dispatchers.IO) {
        check(PlatformManager.isAlive) { "Root backend is unavailable" }
        val repos = TaskerRuntime.repositories(applicationContext)
        val history = repos.operationHistoryRepository()
        val local = repos.localRepository().getLocalByIdOrNull(request.moduleId)
            ?: error("Installed module not found")
        check(local.hasAction) { "Module does not define action.sh" }
        check(local.state.name != "DISABLE" && local.state.name != "REMOVE") { "Module is disabled or pending removal" }
        val key = "module-action:${local.id.id}"
        check(history.claimIdempotencyKey(request.operationId, key)) { "An identical module action is already active" }
        val preferences = repos.userPreferencesRepository().data.first()
        val canonicalModuleId =
            ModId.parseOrNull(request.moduleId)?.requireOperational()
                ?: error("Invalid module ID")
        val environment = mapOf(
            "ASH_STANDALONE" to "1",
            "MMRL" to "true",
            "MMRL_VER" to BuildConfig.VERSION_NAME,
            "MMRL_VER_CODE" to BuildConfig.VERSION_CODE.toString(),
            "BOOTMODE" to "true",
        )
        val completion = repos.privilegedOperationCoordinator().execute<Unit>(request.operationId) {
            val command = if (preferences.useShellForModuleAction || PlatformManager.platform.isMagisk) {
                ShellCommand.of(
                    "busybox",
                    "sh",
                    canonicalModuleId.actionFile.path,
                )
            } else {
                PlatformManager.moduleManager.getActionCommand(canonicalModuleId)
            }
            check(command.isNotBlank()) { "No module action command is available" }
            phase(OperationPhase.INSTALL, "Running module action")
            markMutationStarted()
            val result = repos.privilegedProcessExecutor().execute(
                command = command,
                environment = environment,
                onLine = { line -> log(line) },
            )
            if (result.isSuccess) {
                PrivilegedOperationCoordinator.OperationCompletion.Success(Unit, "Module action completed")
            } else {
                PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                    "Module action exited with code ${result.exitCode} after privileged execution began; reconcile before retrying",
                )
            }
        }
        check(completion is PrivilegedOperationCoordinator.OperationCompletion.Success) {
            when (completion) {
                is PrivilegedOperationCoordinator.OperationCompletion.Failure -> completion.summary
                is PrivilegedOperationCoordinator.OperationCompletion.Cancelled -> completion.summary
                is PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown -> completion.summary
                else -> "Module action failed"
            }
        }
    }

    private suspend fun restorePreviousVersion(request: TaskerRootRequest) {
        val repos = TaskerRuntime.repositories(applicationContext)
        val history = repos.operationHistoryRepository()
        val source = request.targetOperationId?.let { history.getById(it) }
            ?: error("Rollback source operation not found")
        val archive = source.rollbackArchivePath?.let(::File)?.takeIf(File::isFile)
            ?: error("Rollback archive is unavailable")
        val moduleId = source.moduleId?.let(ModId::parseOrNull) ?: error("Rollback module identity is unavailable")
        val expectedVersion = source.previousVersion
        val key = "module-rollback:${moduleId.id}:${source.id}"
        check(history.claimIdempotencyKey(request.operationId, key)) { "An identical rollback is already active" }
        val completion = repos.privilegedOperationCoordinator().execute<Unit>(request.operationId) {
            phase(OperationPhase.VERIFY, "Revalidating rollback archive")
            check(ArchiveInspector.inspect(archive).canInstall) { "Rollback archive failed safety inspection" }
            val command = PlatformManager.moduleManager.getInstallCommand(archive.absolutePath)
            check(!command.isNullOrBlank()) { "Unable to create rollback install command" }
            markMutationStarted()
            phase(OperationPhase.ROLLBACK, "Restoring previous module version")
            val result = repos.privilegedProcessExecutor().execute(command, onLine = { line -> log(line) })
            phase(OperationPhase.RECONCILE, "Reconciling restored module state")
            val actual = runCatching { PlatformManager.moduleManager.getModuleById(moduleId) }.getOrNull()
            if (result.isSuccess && actual != null && (expectedVersion.isNullOrBlank() || actual.version == expectedVersion)) {
                val finalizationError = runCatching { repos.modulesRepository().getLocal(actual.id) }.exceptionOrNull()
                when (
                    VerifiedMutationFinalizationPolicy.classify(
                        backendVerified = true,
                        finalizationSucceeded = finalizationError == null,
                    )
                ) {
                    VerifiedMutationFinalizationPolicy.Outcome.SUCCESS ->
                        PrivilegedOperationCoordinator.OperationCompletion.Success(
                            Unit,
                            "Previous module version restored",
                            requiresReboot = true,
                        )
                    VerifiedMutationFinalizationPolicy.Outcome.KNOWN_APPLIED_FINALIZATION_FAILED ->
                        PrivilegedOperationCoordinator.OperationCompletion.Failure(
                            "Backend confirms the previous version was restored, but local state refresh failed",
                            error = finalizationError,
                            requiresReboot = true,
                            retryable = false,
                        )
                    VerifiedMutationFinalizationPolicy.Outcome.OUTCOME_UNKNOWN ->
                        PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                            "Rollback backend state could not be verified; reconcile before retrying",
                            requiresReboot = true,
                        )
                }
            } else {
                PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                    summary = "Rollback command/backend state disagree; reconcile before retrying",
                    requiresReboot = true,
                )
            }
        }
        check(completion is PrivilegedOperationCoordinator.OperationCompletion.Success) {
            when (completion) {
                is PrivilegedOperationCoordinator.OperationCompletion.Failure -> completion.summary
                is PrivilegedOperationCoordinator.OperationCompletion.Cancelled -> completion.summary
                is PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown -> completion.summary
                else -> "Previous version restore failed"
            }
        }
    }

    private suspend fun executeReviewedInstall(request: TaskerRootRequest) = withContext(Dispatchers.IO) {
        val tokenValue = request.reviewToken ?: error("Review token is required")
        val token = TaskerReviewTokenStore.validateClaimed(
            applicationContext,
            tokenValue,
            request.operationId,
        )
        val repos = TaskerRuntime.repositories(applicationContext)
        val history = repos.operationHistoryRepository()
        val archive = File(token.archivePath)
        history.phase(request.operationId, OperationPhase.VERIFY, "Revalidating reviewed archive")
        val inspection = ArchiveInspector.inspect(archive)
        history.inspectionSummary(request.operationId, inspection.summary)
        history.appendLog(request.operationId, "SHA-256: ${inspection.sha256}")
        check(inspection.canInstall && inspection.sha256.equals(token.sha256, ignoreCase = true)) {
            "Reviewed archive changed or no longer passes safety inspection"
        }
        val module = PlatformManager.moduleManager.getModuleInfo(archive.absolutePath)
            ?: error("Unable to read module metadata")
        val tokenModuleId = TaskerAutomationPolicy.requireSafeModuleId(token.moduleId)
        TaskerAutomationPolicy.requireExactModuleMatch(expected = tokenModuleId, actual = module.id.id)
        val previous = repos.localRepository().getLocalByIdOrNull(module.id.id)
        history.phase(request.operationId, OperationPhase.STAGE, "Creating rollback backup and staging update")
        val rollback = if (previous != null) repos.updateRollbackStore().create(previous, request.operationId).getOrNull() else null
        history.attachRollbackArchive(request.operationId, rollback?.absolutePath, previous?.version, module.version)
        if (previous != null && rollback == null) history.appendLog(request.operationId, "Warning: rollback backup could not be created")
        val key = "reviewed-install:${module.id.id}:${inspection.sha256}"
        check(history.claimIdempotencyKey(request.operationId, key)) { "An identical reviewed install is already active" }
        val completion = repos.privilegedOperationCoordinator().execute<Unit>(
            historyId = request.operationId,
            cleanup = { TaskerReviewTokenStore.remove(applicationContext, tokenValue) },
        ) {
            phase(OperationPhase.VERIFY, "Rechecking reviewed archive immediately before root execution")
            val finalInspection = ArchiveInspector.inspect(archive)
            check(finalInspection.canInstall && finalInspection.sha256.equals(token.sha256, ignoreCase = true)) {
                "Reviewed archive changed before privileged execution"
            }
            val finalModule = PlatformManager.moduleManager.getModuleInfo(archive.absolutePath)
                ?: error("Unable to re-read module metadata before privileged execution")
            TaskerAutomationPolicy.requireExactModuleMatch(expected = tokenModuleId, actual = finalModule.id.id)
            val command = PlatformManager.moduleManager.getInstallCommand(archive.absolutePath)
            check(!command.isNullOrBlank()) { "Unable to create install command" }
            markMutationStarted()
            phase(OperationPhase.INSTALL, if (previous == null) "Installing reviewed module" else "Installing reviewed update")
            val result = repos.privilegedProcessExecutor().execute(
                command = command,
                environment = mapOf(
                    "ASH_STANDALONE" to "1",
                    "MMRL" to "true",
                    "MMRL_VER" to BuildConfig.VERSION_NAME,
                    "MMRL_VER_CODE" to BuildConfig.VERSION_CODE.toString(),
                ),
                onLine = { line -> log(line) },
            )
            phase(OperationPhase.RECONCILE, "Reconciling installed module from root backend")
            val actual = runCatching { PlatformManager.moduleManager.getModuleById(module.id) }.getOrNull()
            if (result.isSuccess && actual != null && actual.id == module.id && actual.versionCode == module.versionCode) {
                val rollbackAction = when {
                    rollback != null -> OperationAction.INSTALL
                    previous == null -> OperationAction.REMOVE
                    else -> null
                }
                val finalizationError = runCatching { repos.localRepository().insertLocal(actual) }.exceptionOrNull()
                when (
                    VerifiedMutationFinalizationPolicy.classify(
                        backendVerified = true,
                        finalizationSucceeded = finalizationError == null,
                    )
                ) {
                    VerifiedMutationFinalizationPolicy.Outcome.SUCCESS ->
                        PrivilegedOperationCoordinator.OperationCompletion.Success(
                            Unit,
                            if (previous == null) "Reviewed module installed" else "Reviewed update installed",
                            requiresReboot = true,
                            rollbackAction = rollbackAction,
                        )
                    VerifiedMutationFinalizationPolicy.Outcome.KNOWN_APPLIED_FINALIZATION_FAILED ->
                        PrivilegedOperationCoordinator.OperationCompletion.Failure(
                            "Backend confirms the reviewed module was installed, but local state finalization failed",
                            error = finalizationError,
                            requiresReboot = true,
                            rollbackAction = rollbackAction,
                            retryable = false,
                        )
                    VerifiedMutationFinalizationPolicy.Outcome.OUTCOME_UNKNOWN ->
                        PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                            "Reviewed install backend state could not be verified; reconcile before retrying",
                            requiresReboot = true,
                            rollbackAction = rollbackAction,
                        )
                }
            } else if (!result.isSuccess && previous != null && rollback != null) {
                phase(OperationPhase.ROLLBACK, "Install failed; restoring previous version")
                val rollbackCommand = PlatformManager.moduleManager.getInstallCommand(rollback.absolutePath)
                if (rollbackCommand.isNullOrBlank()) {
                    PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                        "Install failed and rollback command is unavailable; reconcile before retrying",
                        requiresReboot = true,
                        rollbackAction = OperationAction.INSTALL,
                    )
                } else {
                    val rollbackResult = repos.privilegedProcessExecutor().execute(rollbackCommand, onLine = { line -> log(line) })
                    phase(OperationPhase.RECONCILE, "Verifying automatic rollback")
                    val restored = runCatching { PlatformManager.moduleManager.getModuleById(module.id) }.getOrNull()
                    if (rollbackResult.isSuccess && restored != null && restored.versionCode == previous.versionCode) {
                        val finalizationError = runCatching { repos.localRepository().insertLocal(restored) }.exceptionOrNull()
                        PrivilegedOperationCoordinator.OperationCompletion.Failure(
                            if (finalizationError == null) {
                                "Install failed; previous version restored"
                            } else {
                                "Install failed and the backend restored the previous version, but local state finalization failed"
                            },
                            error = finalizationError,
                            requiresReboot = true,
                            retryable = false,
                        )
                    } else {
                        PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                            "Install and automatic rollback outcome are unknown; reconcile before retrying",
                            requiresReboot = true,
                            rollbackAction = OperationAction.INSTALL,
                        )
                    }
                }
            } else if (!result.isSuccess && actual?.versionCode == previous?.versionCode) {
                PrivilegedOperationCoordinator.OperationCompletion.Failure("Reviewed installation failed; installed state is unchanged")
            } else {
                PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown(
                    "Install command/backend state disagree; reconcile before retrying",
                    requiresReboot = true,
                    rollbackAction = when {
                        rollback != null -> OperationAction.INSTALL
                        previous == null -> OperationAction.REMOVE
                        else -> null
                    },
                )
            }
        }
        check(completion is PrivilegedOperationCoordinator.OperationCompletion.Success) {
            when (completion) {
                is PrivilegedOperationCoordinator.OperationCompletion.Failure -> completion.summary
                is PrivilegedOperationCoordinator.OperationCompletion.Cancelled -> completion.summary
                is PrivilegedOperationCoordinator.OperationCompletion.OutcomeUnknown -> completion.summary
                else -> "Reviewed installation failed"
            }
        }
    }

}
