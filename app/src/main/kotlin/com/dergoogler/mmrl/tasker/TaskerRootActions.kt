package com.dergoogler.mmrl.tasker

import android.content.Context
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.model.ModuleIdentity
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private fun rootAction(
    context: Context,
    moduleIdInput: String?,
    command: String,
    capability: TaskerCapability,
): TaskerResultOutput = runBlocking(Dispatchers.IO) {
    val moduleId = moduleIdInput?.takeIf(String::isNotBlank)
        ?.let(TaskerAutomationPolicy::requireSafeModuleId)
        ?: throw IllegalArgumentException("Module ID is required")
    val repos = TaskerRuntime.repositories(context)
    runCatching { repos.modulesRepository().getLocalAll() }
    val local = repos.localRepository().getLocalByIdOrNull(ModuleIdentity.normalize(moduleId))
        ?: throw NoSuchElementException("Installed module not found: $moduleId")
    val prefs = repos.userPreferencesRepository().data.first()
    val decision = TaskerAuthorizationPolicy.decide(context, prefs, capability, local.id.id)
    val kind = when (command) {
        "ENABLE" -> OperationKind.ENABLE
        "DISABLE" -> OperationKind.DISABLE
        "REMOVE" -> OperationKind.REMOVE
        "RUN_ACTION" -> OperationKind.MODULE_ACTION
        else -> error("Unsupported action")
    }
    val operationId = repos.operationHistoryRepository().start(
        kind = kind,
        title = local.name,
        summary = "Requested by Tasker",
        moduleId = local.id.id,
        moduleName = local.name,
        retryAction = when (command) {
            "ENABLE" -> OperationAction.ENABLE
            "DISABLE" -> OperationAction.DISABLE
            "REMOVE" -> OperationAction.REMOVE
            else -> OperationAction.RUN_ACTION
        },
        rollbackAction = when (command) {
            "ENABLE" -> OperationAction.DISABLE
            "DISABLE", "REMOVE" -> OperationAction.ENABLE
            else -> null
        },
        useShell = prefs.useShellForModuleStateChange,
        origin = "TASKER",
    )
    repos.operationHistoryRepository().appendLog(operationId, "Tasker requested $command")
    if (decision == TaskerAuthorizationDecision.DENY) {
        repos.operationHistoryRepository().fail(operationId, "Tasker capability is disabled by MMRL policy")
        throw IllegalStateException("Tasker capability is disabled in MMRL settings")
    }
    val status = try {
        TaskerRootDispatcher.dispatch(
            context,
            TaskerRootRequest(
                operationId = operationId,
                command = command,
                moduleId = local.id.id,
                moduleName = local.name,
            ),
            decision,
        )
    } catch (error: Throwable) {
        repos.operationHistoryRepository().fail(
            operationId,
            error.message ?: "Unable to queue Tasker action",
            error,
        )
        throw error
    }
    if (status == "AWAITING_APPROVAL") {
        repos.operationHistoryRepository().phase(
            operationId,
            com.dergoogler.mmrl.database.entity.history.OperationPhase.APPROVAL,
            "Waiting for MMRL approval",
        )
    }
    TaskerResultOutput(
        status = status,
        message = if (status == "AWAITING_APPROVAL") "Waiting for MMRL approval" else "Root action queued",
        operationId = operationId,
        operationType = kind.name,
        moduleId = local.id.id,
        moduleName = local.name,
        approvalRequired = status == "AWAITING_APPROVAL",
        resultJson = JSONObject()
            .put("operation_id", operationId)
            .put("status", status)
            .put("command", command)
            .put("module_id", local.id.id)
            .put("approval_required", status == "AWAITING_APPROVAL")
            .toString(),
    )
}

class EnableModuleRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction { rootAction(context, input.regular.moduleId, "ENABLE", TaskerCapability.STATE_CHANGE) }
}
class DisableModuleRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction { rootAction(context, input.regular.moduleId, "DISABLE", TaskerCapability.STATE_CHANGE) }
}
class RemoveModuleRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction { rootAction(context, input.regular.moduleId, "REMOVE", TaskerCapability.REMOVAL) }
}
class RunModuleActionRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction { rootAction(context, input.regular.moduleId, "RUN_ACTION", TaskerCapability.MODULE_ACTION) }
}

class RestoreModuleRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> = taskerAction {
        val sourceId = input.regular.operationId?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Operation ID is required")
        runBlocking(Dispatchers.IO) {
            val repos = TaskerRuntime.repositories(context)
            val source = repos.operationHistoryRepository().getById(sourceId)
                ?: throw NoSuchElementException("Operation not found")
            require(!source.rollbackArchivePath.isNullOrBlank()) { "No retained previous version is available" }
            val moduleId = source.moduleId ?: throw IllegalArgumentException("Rollback operation has no module ID")
            val prefs = repos.userPreferencesRepository().data.first()
            val decision = TaskerAuthorizationPolicy.decide(context, prefs, TaskerCapability.REMOVAL, moduleId)
            val operationId = repos.operationHistoryRepository().start(
                kind = OperationKind.ROLLBACK,
                title = source.moduleName ?: source.title,
                summary = "Restore requested by Tasker",
                moduleId = moduleId,
                moduleName = source.moduleName,
                parentId = source.id,
                origin = "TASKER",
            )
            if (decision == TaskerAuthorizationDecision.DENY) {
                repos.operationHistoryRepository().fail(operationId, "Tasker restore is disabled by MMRL policy")
                throw IllegalStateException("Tasker restore is disabled in MMRL settings")
            }
            val status = try {
                TaskerRootDispatcher.dispatch(
                    context,
                    TaskerRootRequest(
                        operationId = operationId,
                        command = "RESTORE",
                        moduleId = moduleId,
                        moduleName = source.moduleName.orEmpty(),
                        targetOperationId = source.id,
                    ),
                    decision,
                )
            } catch (error: Throwable) {
                repos.operationHistoryRepository().fail(
                    operationId,
                    error.message ?: "Unable to queue restore",
                    error,
                )
                throw error
            }
            if (status == "AWAITING_APPROVAL") {
                repos.operationHistoryRepository().phase(
                    operationId,
                    com.dergoogler.mmrl.database.entity.history.OperationPhase.APPROVAL,
                    "Waiting for MMRL approval",
                )
            }
            TaskerResultOutput(
                status = status,
                message = if (status == "AWAITING_APPROVAL") "Waiting for MMRL approval" else "Restore queued",
                operationId = operationId,
                operationType = OperationKind.ROLLBACK.name,
                moduleId = moduleId,
                moduleName = source.moduleName.orEmpty(),
                approvalRequired = status == "AWAITING_APPROVAL",
                rollbackAvailable = true,
                resultJson = JSONObject().put("operation_id", operationId).put("status", status).put("source_operation_id", source.id).toString(),
            )
        }
    }
}
