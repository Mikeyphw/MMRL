package com.dergoogler.mmrl.tasker

import android.content.Context
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.service.DownloadService
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val ERROR_INVALID_INPUT = 1001
private const val ERROR_NOT_FOUND = 1002
private const val ERROR_QUEUE = 1003
private const val ERROR_INTERNAL = 1099

@Suppress("UNCHECKED_CAST")
private fun taskerError(code: Int, message: String): TaskerPluginResult<TaskerResultOutput> =
    TaskerPluginResultError(code, message) as TaskerPluginResult<TaskerResultOutput>

internal fun taskerAction(block: () -> TaskerResultOutput): TaskerPluginResult<TaskerResultOutput> =
    try {
        TaskerPluginResultSucess(block())
    } catch (error: IllegalArgumentException) {
        taskerError(ERROR_INVALID_INPUT, error.message ?: "Invalid input")
    } catch (error: NoSuchElementException) {
        taskerError(ERROR_NOT_FOUND, error.message ?: "Not found")
    } catch (error: IllegalStateException) {
        taskerError(ERROR_QUEUE, error.message ?: "Operation could not be queued")
    } catch (error: Throwable) {
        taskerError(ERROR_INTERNAL, error.message ?: error.javaClass.simpleName)
    }

class GetModuleStatusRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            val moduleId = requireNotNull(input.regular.moduleId?.trim()?.takeIf(String::isNotEmpty)) {
                "Module ID is required"
            }
            runBlocking(Dispatchers.IO) { TaskerRuntime.moduleStatus(context, moduleId) }
        }
}

class ListModulesRunner : TaskerPluginRunnerAction<TaskerEmptyInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerEmptyInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val repos = TaskerRuntime.repositories(context)
                runCatching { repos.modulesRepository().getLocalAll() }
                val locals = repos.localRepository().getLocalAll()
                val online = repos.localRepository().getOnlineAllAsFlow(duplicates = true).first()
                    .groupBy { ModuleIdentity.normalize(it.id) }
                    .mapValues { (_, values) -> values.maxByOrNull { it.versionCode } }
                val ignored = locals.associate { it.id to !repos.localRepository().hasUpdatableTag(it.id) }
                val items = locals.map { local ->
                    val remote = online[ModuleIdentity.normalize(local.id)]
                    val update = remote != null && remote.versionCode > local.versionCode
                    JSONObject()
                        .put("module_id", local.id)
                        .put("module_name", local.name)
                        .put("version", local.version)
                        .put("version_code", local.versionCode)
                        .put("state", local.state)
                        .put("enabled", local.state == "ENABLE" || local.state == "UPDATE")
                        .put("update_available", update)
                        .put("update_ignored", ignored[local.id] == true)
                        .put("available_version", remote?.version.orEmpty())
                        .put("available_version_code", remote?.versionCode ?: -1)
                        .put("repository", remote?.repoUrl.orEmpty())
                }
                taskerResultOutput(
                    status = "OK",
                    message = "${locals.size} installed modules",
                    count = locals.size,
                    moduleIds = locals.map { it.id }.toTypedArray(),
                    moduleNames = locals.map { it.name }.toTypedArray(),
                    versions = locals.map { it.version }.toTypedArray(),
                    states = locals.map { it.state }.toTypedArray(),
                    resultJson = JSONArray(items).toString(),
                )
            }
        }
}

class CheckUpdatesRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val repos = TaskerRuntime.repositories(context)
                val history = repos.operationHistoryRepository()
                val operationId = history.start(
                    kind = OperationKind.CHECK_UPDATES,
                    title = "Tasker update check",
                    summary = "Checking module repositories",
                    origin = "TASKER",
                )
                try {
                    history.appendLog(operationId, "Requested by Tasker")
                    runCatching { repos.modulesRepository().getLocalAll() }
                        .onFailure { history.appendLog(operationId, "Installed-module refresh failed; using cached state: ${it.message}") }
                    if (input.regular.forceRefresh) {
                        history.phase(operationId, OperationPhase.DOWNLOAD, "Refreshing repositories")
                        repos.modulesRepository().getRepoAll()
                    }
                    history.phase(operationId, OperationPhase.VERIFY, "Comparing installed versions")
                    val locals = repos.localRepository().getLocalAll()
                    val online = repos.localRepository().getOnlineAllAsFlow(duplicates = true).first()
                        .groupBy { ModuleIdentity.normalize(it.id) }
                        .mapValues { (_, values) -> values.maxByOrNull { it.versionCode } }
                    val updates = locals.mapNotNull { local ->
                        val remote = online[ModuleIdentity.normalize(local.id)] ?: return@mapNotNull null
                        val tracked = repos.localRepository().hasUpdatableTag(local.id)
                        if (remote.versionCode <= local.versionCode || !tracked) return@mapNotNull null
                        JSONObject()
                            .put("module_id", local.id)
                            .put("module_name", local.name)
                            .put("installed_version", local.version)
                            .put("installed_version_code", local.versionCode)
                            .put("available_version", remote.version)
                            .put("available_version_code", remote.versionCode)
                            .put("repository", remote.repoUrl)
                    }
                    history.succeed(operationId, "${updates.size} module updates found")
                    taskerResultOutput(
                        status = "SUCCEEDED",
                        message = "${updates.size} module updates found",
                        operationId = operationId,
                        operationType = OperationKind.CHECK_UPDATES.name,
                        count = updates.size,
                        moduleIds = updates.map { it.getString("module_id") }.toTypedArray(),
                        moduleNames = updates.map { it.getString("module_name") }.toTypedArray(),
                        versions = updates.map { it.getString("available_version") }.toTypedArray(),
                        states = Array(updates.size) { "UPDATE_AVAILABLE" },
                        resultJson = JSONArray(updates).toString(),
                    )
                } catch (error: Throwable) {
                    history.fail(operationId, error.message ?: "Update check failed", error)
                    throw error
                }
            }
        }
}

class GetOperationResultRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            val operationId = requireNotNull(input.regular.operationId?.trim()?.takeIf(String::isNotEmpty)) {
                "Operation ID is required"
            }
            runBlocking(Dispatchers.IO) {
                val entry = TaskerRuntime.repositories(context).operationHistoryRepository().getById(operationId)
                    ?: throw NoSuchElementException("Operation not found: $operationId")
                TaskerRuntime.operationOutput(entry)
            }
        }
}

class DownloadModuleRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            runBlocking(Dispatchers.IO) {
                val request = input.regular
                val module = request.moduleId?.trim()?.takeIf(String::isNotEmpty)?.let {
                    TaskerRuntime.newestOnline(context, it)
                        ?: throw NoSuchElementException("Module not found: $it")
                }
                val version = module?.versions?.maxByOrNull { it.versionCode }
                val url = request.url?.trim()?.takeIf(String::isNotEmpty) ?: version?.zipUrl
                    ?: throw IllegalArgumentException("Provide a repository module ID or download URL")
                val validatedUrl = TaskerAutomationPolicy.requireSupportedDownloadUrl(url)
                val uri = java.net.URI(validatedUrl)
                val defaultName = module?.let { "${it.id}-${version?.versionCode ?: it.versionCode}.zip" }
                    ?: uri.path.substringAfterLast('/').takeIf { it.endsWith(".zip", true) }
                    ?: "mmrl-tasker-${System.currentTimeMillis()}.zip"
                val filename = TaskerAutomationPolicy.sanitizeFilename(
                    request.filename?.trim()?.takeIf(String::isNotEmpty) ?: defaultName,
                )
                val repos = TaskerRuntime.repositories(context)
                val preferences = repos.userPreferencesRepository().data.first()
                require(preferences.taskerIntegrationEnabled && preferences.taskerAllowDownloads) {
                    "Tasker downloads are disabled in MMRL settings"
                }
                val history = repos.operationHistoryRepository()
                val downloadPath = preferences.downloadPath
                val operationId = history.start(
                    kind = OperationKind.DOWNLOAD,
                    title = module?.name ?: filename,
                    summary = "Queued by Tasker",
                    moduleId = module?.id,
                    moduleName = module?.name,
                    sourceUrl = validatedUrl,
                    destinationPath = File(downloadPath, filename).absolutePath,
                    retryAction = OperationAction.DOWNLOAD,
                    origin = "TASKER",
                )
                history.appendLog(operationId, "Tasker queued download")
                val queued = DownloadService.startFromAutomation(
                    context = context,
                    task = DownloadService.TaskItem(
                        key = operationId.hashCode(),
                        url = validatedUrl,
                        filename = filename,
                        title = module?.name ?: filename,
                        desc = module?.let { "${version?.version ?: it.version} · ${it.repoUrl}" } ?: "Tasker download",
                        operationId = operationId,
                    ),
                )
                if (!queued) {
                    history.fail(operationId, "Download could not be queued")
                    throw IllegalStateException("Download could not be queued; check storage permission and background restrictions")
                }
                taskerResultOutput(
                    status = OperationStatus.RUNNING.name,
                    message = "Download queued",
                    operationId = operationId,
                    operationType = OperationKind.DOWNLOAD.name,
                    phase = OperationPhase.REVIEW.name,
                    moduleId = module?.id.orEmpty(),
                    moduleName = module?.name.orEmpty(),
                    availableVersion = version?.version ?: module?.version.orEmpty(),
                    availableVersionCode = version?.versionCode ?: module?.versionCode ?: -1,
                    repository = module?.repoUrl.orEmpty(),
                    resultJson = JSONObject()
                        .put("operation_id", operationId)
                        .put("status", "RUNNING")
                        .put("url", validatedUrl)
                        .put("filename", filename)
                        .toString(),
                )
            }
        }

}

class CancelDownloadRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            val operationId = requireNotNull(input.regular.operationId?.trim()?.takeIf(String::isNotEmpty)) {
                "Operation ID is required"
            }
            runBlocking(Dispatchers.IO) {
                val history = TaskerRuntime.repositories(context).operationHistoryRepository()
                val entry = history.getById(operationId) ?: throw NoSuchElementException("Operation not found")
                require(entry.kind == OperationKind.DOWNLOAD.name) { "Operation is not a download" }
                require(entry.status == OperationStatus.RUNNING.name) { "Download is not running" }
                history.appendLog(operationId, "Cancellation requested by Tasker")
                check(DownloadService.cancel(context, operationId)) { "Unable to dispatch download cancellation" }
                taskerResultOutput(
                    status = "CANCELLATION_REQUESTED",
                    message = "Download cancellation requested",
                    operationId = operationId,
                    operationType = entry.kind,
                    moduleId = entry.moduleId.orEmpty(),
                    moduleName = entry.moduleName.orEmpty(),
                    resultJson = JSONObject().put("operation_id", operationId).put("status", "CANCELLATION_REQUESTED").toString(),
                )
            }
        }
}

class ExportOperationLogRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> =
        taskerAction {
            val operationId = requireNotNull(input.regular.operationId?.trim()?.takeIf(String::isNotEmpty)) {
                "Operation ID is required"
            }
            runBlocking(Dispatchers.IO) {
                val repos = TaskerRuntime.repositories(context)
                val source = repos.operationHistoryRepository().getById(operationId)
                    ?: throw NoSuchElementException("Operation not found")
                val exportId = repos.operationHistoryRepository().start(
                    kind = OperationKind.EXPORT_LOG,
                    title = "Export Tasker operation log",
                    summary = "Exporting ${source.id}",
                    parentId = source.id,
                    origin = "TASKER",
                )
                try {
                    val uri = TaskerLogExporter.export(context, source)
                    repos.operationHistoryRepository().appendLog(exportId, "Exported log for ${source.id} to $uri")
                    repos.operationHistoryRepository().succeed(exportId, "Technical log exported")
                    TaskerRuntime.operationOutput(source, uri).copyForExport(uri)
                } catch (error: Throwable) {
                    repos.operationHistoryRepository().fail(exportId, error.message ?: "Log export failed", error)
                    throw error
                }
            }
        }

    private fun TaskerResultOutput.copyForExport(uri: String) = taskerResultOutput(
        success = success,
        status = status,
        message = "Technical log exported",
        operationId = operationId,
        operationType = operationType,
        phase = phase,
        progress = progress,
        moduleId = moduleId,
        moduleName = moduleName,
        rebootRequired = rebootRequired,
        rollbackAvailable = rollbackAvailable,
        errorCode = errorCode,
        errorMessage = errorMessage,
        logUri = uri,
        resultJson = JSONObject(resultJson).put("log_uri", uri).toString(),
    )
}
