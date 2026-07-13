package com.dergoogler.mmrl.tasker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dergoogler.mmrl.BuildConfig
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.installer.ArchiveInspector
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.hasAction
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.utils.initPlatform
import com.dergoogler.mmrl.utils.withNewRootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

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
            if (!PlatformManager.isAlive) {
                initPlatform(applicationContext, preferences.workingMode.toPlatform())
            }
            check(PlatformManager.isAlive) { "Root backend is unavailable" }
            history.appendLog(request.operationId, "Tasker automation worker started")
            when (request.command) {
                "ENABLE", "DISABLE", "REMOVE" -> executeModuleState(request)
                "RUN_ACTION" -> executeModuleAction(request)
                "RESTORE" -> restorePreviousVersion(request)
                "EXECUTE_REVIEW" -> executeReviewedInstall(request)
                else -> error("Unsupported Tasker command: ${request.command}")
            }
            TaskerRootRequestStore.remove(applicationContext, request.id)
            Result.success()
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
            TaskerRootRequestStore.remove(applicationContext, request.id)
            Result.failure()
        }
    }

    private suspend fun executeModuleState(request: TaskerRootRequest) {
        check(PlatformManager.isAlive) { "Root backend is unavailable" }
        val history = TaskerRuntime.repositories(applicationContext).operationHistoryRepository()
        history.phase(request.operationId, OperationPhase.INSTALL, "Applying module state change")
        val prefs = TaskerRuntime.repositories(applicationContext).userPreferencesRepository().data.first()
        val success = suspendCancellableCoroutine<Boolean> { continuation ->
            val callback = object : IModuleOpsCallback.Stub() {
                override fun onSuccess(id: ModId) { if (continuation.isActive) continuation.resume(true) }
                override fun onFailure(id: ModId, msg: String?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            val id = ModId(request.moduleId)
            when (request.command) {
                "ENABLE" -> PlatformManager.moduleManager.enable(id, prefs.useShellForModuleStateChange, callback)
                "DISABLE" -> PlatformManager.moduleManager.disable(id, prefs.useShellForModuleStateChange, callback)
                "REMOVE" -> PlatformManager.moduleManager.remove(id, prefs.useShellForModuleStateChange, callback)
            }
        }
        check(success) { "Module state change failed" }
        TaskerRuntime.repositories(applicationContext).modulesRepository().getLocal(ModId(request.moduleId))
        history.succeed(
            request.operationId,
            "${request.command.lowercase().replaceFirstChar(Char::uppercase)} completed; reboot required",
            requiresReboot = true,
            rollbackAction = when (request.command) {
                "ENABLE" -> OperationAction.DISABLE
                "DISABLE", "REMOVE" -> OperationAction.ENABLE
                else -> null
            },
        )
    }

    private suspend fun executeModuleAction(request: TaskerRootRequest) = withContext(Dispatchers.IO) {
        check(PlatformManager.isAlive) { "Root backend is unavailable" }
        val repos = TaskerRuntime.repositories(applicationContext)
        val local = repos.localRepository().getLocalByIdOrNull(request.moduleId)
            ?: error("Installed module not found")
        check(local.hasAction) { "Module does not define action.sh" }
        check(local.state.name != "DISABLE" && local.state.name != "REMOVE") { "Module is disabled or pending removal" }
        repos.operationHistoryRepository().phase(request.operationId, OperationPhase.INSTALL, "Running module action")
        val preferences = repos.userPreferencesRepository().data.first()
        val command = if (preferences.useShellForModuleAction || PlatformManager.platform.isMagisk) {
            "busybox sh /data/adb/modules/${request.moduleId}/action.sh"
        } else {
            PlatformManager.moduleManager.getActionCommand(ModId(request.moduleId))
        }
        check(command.isNotBlank()) { "No module action command is available" }
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val result = withNewRootShell {
            newJob().add(
                "export MMRL=true",
                "export MMRL_VER=${BuildConfig.VERSION_NAME}",
                "export MMRL_VER_CODE=${BuildConfig.VERSION_CODE}",
                command,
            ).to(output, errors).exec()
        }
        output.forEach { repos.operationHistoryRepository().appendLog(request.operationId, it) }
        errors.forEach { repos.operationHistoryRepository().appendLog(request.operationId, "stderr: $it") }
        check(result.isSuccess) { "Module action failed" }
        repos.operationHistoryRepository().succeed(request.operationId, "Module action completed")
    }

    private suspend fun restorePreviousVersion(request: TaskerRootRequest) {
        val repos = TaskerRuntime.repositories(applicationContext)
        val source = request.targetOperationId?.let { repos.operationHistoryRepository().getById(it) }
            ?: error("Rollback source operation not found")
        val archive = source.rollbackArchivePath?.let(::File)?.takeIf(File::isFile)
            ?: error("Rollback archive is unavailable")
        repos.operationHistoryRepository().phase(request.operationId, OperationPhase.ROLLBACK, "Restoring previous module version")
        val command = PlatformManager.moduleManager.getInstallCommand(archive.absolutePath)
        check(!command.isNullOrBlank()) { "Unable to create rollback install command" }
        val result = withContext(Dispatchers.IO) { withNewRootShell { newJob().add(command).exec() } }
        check(result.isSuccess) { "Previous version restore failed" }
        repos.operationHistoryRepository().succeed(request.operationId, "Previous module version restored", requiresReboot = true)
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
        val module = PlatformManager.moduleManager.getModuleInfo(archive.absolutePath)
            ?: error("Unable to read module metadata")
        val archiveModuleId = TaskerAutomationPolicy.requireSafeModuleId(module.id.id)
        check(archiveModuleId.equals(token.moduleId, ignoreCase = true)) {
            "Reviewed module ID no longer matches archive"
        }
        val previous = repos.localRepository().getLocalByIdOrNull(module.id.id)
        history.phase(request.operationId, OperationPhase.STAGE, "Creating rollback backup and staging update")
        val rollback = if (previous != null) repos.updateRollbackStore().create(previous).getOrNull() else null
        history.attachRollbackArchive(request.operationId, rollback?.absolutePath, previous?.version, module.version)
        if (previous != null && rollback == null) history.appendLog(request.operationId, "Warning: rollback backup could not be created")
        val command = PlatformManager.moduleManager.getInstallCommand(archive.absolutePath)
        check(!command.isNullOrBlank()) { "Unable to create install command" }
        history.phase(request.operationId, OperationPhase.INSTALL, if (previous == null) "Installing reviewed module" else "Installing reviewed update")
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val result = withNewRootShell {
            newJob().add(
                "export ASH_STANDALONE=1",
                "export MMRL=true",
                "export MMRL_VER=${BuildConfig.VERSION_NAME}",
                "export MMRL_VER_CODE=${BuildConfig.VERSION_CODE}",
                command,
            ).to(output, errors).exec()
        }
        output.forEach { history.appendLog(request.operationId, it) }
        errors.forEach { history.appendLog(request.operationId, "stderr: $it") }
        if (!result.isSuccess) {
            if (rollback != null) {
                history.phase(request.operationId, OperationPhase.ROLLBACK, "Install failed; restoring previous version")
                val rollbackCommand = PlatformManager.moduleManager.getInstallCommand(rollback.absolutePath)
                val restored = !rollbackCommand.isNullOrBlank() && withNewRootShell { newJob().add(rollbackCommand).exec() }.isSuccess
                if (restored) {
                    history.fail(request.operationId, "Install failed; previous version restored", requiresReboot = true)
                } else {
                    history.fail(request.operationId, "Install failed; manual rollback remains available", rollbackAction = OperationAction.INSTALL)
                }
            } else {
                history.fail(request.operationId, "Reviewed installation failed")
            }
            error("Reviewed installation failed")
        }
        repos.localRepository().insertLocal(module)
        history.succeed(
            request.operationId,
            if (previous == null) "Reviewed module installed" else "Reviewed update installed",
            requiresReboot = true,
            rollbackAction = when {
                rollback != null -> OperationAction.INSTALL
                previous == null -> OperationAction.REMOVE
                else -> null
            },
        )
        TaskerReviewTokenStore.remove(applicationContext, tokenValue)
    }
}
