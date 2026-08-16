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
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

class UpdateDiscoveredEventRunner :
    TaskerPluginRunnerConditionEvent<TaskerEmptyInput, TaskerResultOutput, TaskerUpdateEvent>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<TaskerEmptyInput>,
        update: TaskerUpdateEvent?,
    ): TaskerPluginResultCondition<TaskerResultOutput> {
        update ?: return TaskerPluginResultConditionUnsatisfied()
        val output = taskerResultOutput(
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
        val output = taskerResultOutput(
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
    override val inputForTasker get() = TaskerInput(taskerEmptyInput())
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
        if (!taskerEventsEnabled(context)) return
        TaskerEventDeliveryStore.drainDue(context, ::deliverQueued)
        val event = taskerUpdateEvent(
            moduleId = local.id,
            moduleName = local.name,
            installedVersion = local.version,
            installedVersionCode = local.versionCode,
            availableVersion = online.version,
            availableVersionCode = online.versionCode,
            repository = online.repoUrl,
        )
        publishOrQueue(context, "UPDATE_DISCOVERED", updatePayload(event)) {
            UpdateDiscoveredEventConfigActivity::class.java.requestQuery(context, event)
        }
    }

    fun operationFailed(context: Context, entry: OperationHistoryEntity) {
        if (!taskerEventsEnabled(context)) return
        TaskerEventDeliveryStore.drainDue(context, ::deliverQueued)
        val event = taskerFailureEvent(
            operationId = entry.id,
            operationType = entry.kind,
            moduleId = entry.moduleId,
            moduleName = entry.moduleName,
            errorMessage = entry.errorMessage ?: entry.summary,
            phase = entry.phase,
        )
        publishOrQueue(context, "OPERATION_FAILED", failurePayload(event)) {
            OperationFailedEventConfigActivity::class.java.requestQuery(context, event)
        }
    }

    private fun taskerEventsEnabled(context: Context): Boolean = runCatching {
        runBlocking(Dispatchers.IO) {
            TaskerRuntime.repositories(context).userPreferencesRepository().data.first().taskerIntegrationEnabled
        }
    }.getOrDefault(false)

    private fun publishOrQueue(
        context: Context,
        type: String,
        payload: JSONObject,
        send: () -> Unit,
    ) {
        runCatching { send() }
            .onFailure { error ->
                TaskerEventDeliveryStore.recordFailure(
                    context = context,
                    type = type,
                    payload = payload,
                    error = error,
                )
            }
    }

    private fun deliverQueued(context: Context, record: TaskerEventDeliveryRecord) {
        when (record.type) {
            "UPDATE_DISCOVERED" -> UpdateDiscoveredEventConfigActivity::class.java.requestQuery(context, updateEvent(record.payload))
            "OPERATION_FAILED" -> OperationFailedEventConfigActivity::class.java.requestQuery(context, failureEvent(record.payload))
            else -> error("Unknown Tasker event type: ${record.type}")
        }
    }

    private fun updatePayload(event: TaskerUpdateEvent): JSONObject = JSONObject()
        .put("module_id", event.moduleId.orEmpty())
        .put("module_name", event.moduleName.orEmpty())
        .put("installed_version", event.installedVersion.orEmpty())
        .put("installed_version_code", event.installedVersionCode)
        .put("available_version", event.availableVersion.orEmpty())
        .put("available_version_code", event.availableVersionCode)
        .put("repository", event.repository.orEmpty())

    private fun failurePayload(event: TaskerFailureEvent): JSONObject = JSONObject()
        .put("operation_id", event.operationId.orEmpty())
        .put("operation_type", event.operationType.orEmpty())
        .put("module_id", event.moduleId.orEmpty())
        .put("module_name", event.moduleName.orEmpty())
        .put("error_message", event.errorMessage.orEmpty())
        .put("phase", event.phase.orEmpty())

    private fun updateEvent(payload: JSONObject): TaskerUpdateEvent = taskerUpdateEvent(
        moduleId = payload.optString("module_id").takeIf(String::isNotBlank),
        moduleName = payload.optString("module_name").takeIf(String::isNotBlank),
        installedVersion = payload.optString("installed_version").takeIf(String::isNotBlank),
        installedVersionCode = payload.optInt("installed_version_code", -1),
        availableVersion = payload.optString("available_version").takeIf(String::isNotBlank),
        availableVersionCode = payload.optInt("available_version_code", -1),
        repository = payload.optString("repository").takeIf(String::isNotBlank),
    )

    private fun failureEvent(payload: JSONObject): TaskerFailureEvent = taskerFailureEvent(
        operationId = payload.optString("operation_id").takeIf(String::isNotBlank),
        operationType = payload.optString("operation_type").takeIf(String::isNotBlank),
        moduleId = payload.optString("module_id").takeIf(String::isNotBlank),
        moduleName = payload.optString("module_name").takeIf(String::isNotBlank),
        errorMessage = payload.optString("error_message").takeIf(String::isNotBlank),
        phase = payload.optString("phase").takeIf(String::isNotBlank),
    )
}

internal data class TaskerEventDeliveryRecord(
    val id: String,
    val type: String,
    val payload: JSONObject,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type)
        .put("payload", payload)
        .put("attempts", attempts)
        .put("next_attempt_at", nextAttemptAt)
        .put("last_error", lastError)

    companion object {
        fun fromJson(value: String): TaskerEventDeliveryRecord {
            val root = JSONObject(value)
            return TaskerEventDeliveryRecord(
                id = root.getString("id"),
                type = root.getString("type"),
                payload = root.getJSONObject("payload"),
                attempts = root.optInt("attempts", 0),
                nextAttemptAt = root.optLong("next_attempt_at", 0L),
                lastError = root.optString("last_error"),
            )
        }
    }
}

internal object TaskerEventDeliveryStore {
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 5L * 60L * 1000L

    fun recordFailure(context: Context, type: String, payload: JSONObject, error: Throwable) {
        val id = "${type.lowercase()}-${System.currentTimeMillis()}-${payload.toString().hashCode()}"
        write(
            context,
            TaskerEventDeliveryRecord(
                id = id,
                type = type,
                payload = payload,
                attempts = 1,
                nextAttemptAt = System.currentTimeMillis() + RETRY_DELAY_MS,
                lastError = error.message ?: error.javaClass.simpleName,
            ),
        )
    }

    fun drainDue(context: Context, deliver: (Context, TaskerEventDeliveryRecord) -> Unit) {
        val now = System.currentTimeMillis()
        directory(context).listFiles().orEmpty()
            .filter { it.extension == "json" }
            .mapNotNull { source -> read(source)?.let { source to it } }
            .filter { (_, record) -> record.nextAttemptAt <= now }
            .forEach { (source, record) ->
                if (record.attempts >= MAX_ATTEMPTS) {
                    source.delete()
                    return@forEach
                }
                runCatching { deliver(context, record) }
                    .onSuccess { source.delete() }
                    .onFailure { error ->
                        write(
                            context,
                            record.copy(
                                attempts = record.attempts + 1,
                                nextAttemptAt = now + RETRY_DELAY_MS,
                                lastError = error.message ?: error.javaClass.simpleName,
                            ),
                        )
                    }
            }
    }

    private fun directory(context: Context): File = File(context.filesDir, "tasker-event-delivery").apply { mkdirs() }

    private fun file(context: Context, id: String): File = File(
        directory(context),
        id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json",
    )

    private fun write(context: Context, record: TaskerEventDeliveryRecord) {
        val atomic = AtomicFile(file(context, record.id))
        val stream = atomic.startWrite()
        try {
            stream.write(record.toJson().toString().toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun read(source: File): TaskerEventDeliveryRecord? = runCatching {
        AtomicFile(source).openRead().use { input ->
            TaskerEventDeliveryRecord.fromJson(input.readBytes().toString(StandardCharsets.UTF_8))
        }
    }.getOrNull()
}
