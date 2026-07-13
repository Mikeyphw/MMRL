package com.dergoogler.mmrl.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.local.LocalModuleEntity
import com.dergoogler.mmrl.database.entity.online.OnlineModuleEntity
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import org.json.JSONObject

class UpdateDiscoveredEventRunner :
    TaskerPluginRunnerConditionEvent<TaskerEmptyInput, TaskerResultOutput, TaskerUpdateEvent>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<TaskerEmptyInput>,
        update: TaskerUpdateEvent?,
    ): TaskerPluginResultCondition<TaskerResultOutput> {
        update ?: return TaskerPluginResultConditionUnsatisfied()
        val output = TaskerResultOutput(
            status = "UPDATE_DISCOVERED",
            message = "Update available for ${update.moduleName.orEmpty()}",
            moduleId = update.moduleId.orEmpty(),
            moduleName = update.moduleName.orEmpty(),
            installed = true,
            installedVersion = update.installedVersion.orEmpty(),
            installedVersionCode = update.installedVersionCode,
            availableVersion = update.availableVersion.orEmpty(),
            availableVersionCode = update.availableVersionCode,
            updateAvailable = true,
            repository = update.repository.orEmpty(),
            resultJson = JSONObject()
                .put("event", "UPDATE_DISCOVERED")
                .put("module_id", update.moduleId.orEmpty())
                .put("module_name", update.moduleName.orEmpty())
                .put("installed_version", update.installedVersion.orEmpty())
                .put("installed_version_code", update.installedVersionCode)
                .put("available_version", update.availableVersion.orEmpty())
                .put("available_version_code", update.availableVersionCode)
                .put("repository", update.repository.orEmpty())
                .toString(),
        )
        return TaskerPluginResultConditionSatisfied(context, output)
    }
}

class OperationFailedEventRunner :
    TaskerPluginRunnerConditionEvent<TaskerEmptyInput, TaskerResultOutput, TaskerFailureEvent>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<TaskerEmptyInput>,
        update: TaskerFailureEvent?,
    ): TaskerPluginResultCondition<TaskerResultOutput> {
        update ?: return TaskerPluginResultConditionUnsatisfied()
        val output = TaskerResultOutput(
            success = false,
            status = "OPERATION_FAILED",
            message = update.errorMessage.orEmpty(),
            operationId = update.operationId.orEmpty(),
            operationType = update.operationType.orEmpty(),
            phase = update.phase.orEmpty(),
            moduleId = update.moduleId.orEmpty(),
            moduleName = update.moduleName.orEmpty(),
            errorCode = "OPERATION_FAILED",
            errorMessage = update.errorMessage.orEmpty(),
            resultJson = JSONObject()
                .put("event", "OPERATION_FAILED")
                .put("operation_id", update.operationId.orEmpty())
                .put("operation_type", update.operationType.orEmpty())
                .put("phase", update.phase.orEmpty())
                .put("module_id", update.moduleId.orEmpty())
                .put("module_name", update.moduleName.orEmpty())
                .put("error_message", update.errorMessage.orEmpty())
                .toString(),
        )
        return TaskerPluginResultConditionSatisfied(context, output)
    }
}

class UpdateDiscoveredEventHelper(config: TaskerPluginConfig<TaskerEmptyInput>) :
    TaskerPluginConfigHelper<TaskerEmptyInput, TaskerResultOutput, UpdateDiscoveredEventRunner>(config) {
    override val runnerClass = UpdateDiscoveredEventRunner::class.java
    override val inputClass = TaskerEmptyInput::class.java
    override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerEmptyInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("When MMRL discovers a module update")
    }
}

class OperationFailedEventHelper(config: TaskerPluginConfig<TaskerEmptyInput>) :
    TaskerPluginConfigHelper<TaskerEmptyInput, TaskerResultOutput, OperationFailedEventRunner>(config) {
    override val runnerClass = OperationFailedEventRunner::class.java
    override val inputClass = TaskerEmptyInput::class.java
    override val outputClass = TaskerResultOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<TaskerEmptyInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("When an MMRL operation fails")
    }
}

abstract class TaskerEventConfigActivity : Activity(), TaskerPluginConfig<TaskerEmptyInput> {
    override val context: Context get() = applicationContext
    override fun assignFromInput(input: TaskerInput<TaskerEmptyInput>) = Unit
    override val inputForTasker get() = TaskerInput(TaskerEmptyInput())
    protected abstract fun finishEventConfig()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishEventConfig()
    }
}

class UpdateDiscoveredEventConfigActivity : TaskerEventConfigActivity() {
    private val helper by lazy { UpdateDiscoveredEventHelper(this) }
    override fun finishEventConfig() { helper.finishForTasker() }
}

class OperationFailedEventConfigActivity : TaskerEventConfigActivity() {
    private val helper by lazy { OperationFailedEventHelper(this) }
    override fun finishEventConfig() { helper.finishForTasker() }
}

object TaskerEventPublisher {
    fun updateDiscovered(context: Context, local: LocalModuleEntity, online: OnlineModuleEntity) {
        runCatching {
            UpdateDiscoveredEventConfigActivity::class.java.requestQuery(
                context,
                TaskerUpdateEvent(
                    moduleId = local.id,
                    moduleName = local.name,
                    installedVersion = local.version,
                    installedVersionCode = local.versionCode,
                    availableVersion = online.version,
                    availableVersionCode = online.versionCode,
                    repository = online.repoUrl,
                ),
            )
        }
    }

    fun operationFailed(context: Context, entry: OperationHistoryEntity) {
        runCatching {
            OperationFailedEventConfigActivity::class.java.requestQuery(
                context,
                TaskerFailureEvent(
                    operationId = entry.id,
                    operationType = entry.kind,
                    moduleId = entry.moduleId,
                    moduleName = entry.moduleName,
                    errorMessage = entry.errorMessage ?: entry.summary,
                    phase = entry.phase,
                ),
            )
        }
    }
}
