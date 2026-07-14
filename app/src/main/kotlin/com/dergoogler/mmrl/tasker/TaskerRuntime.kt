package com.dergoogler.mmrl.tasker

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.local.State
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.repository.RepositoryEntryPoints
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object TaskerRuntime {
    fun repositories(context: Context): RepositoryEntryPoints =
        EntryPointAccessors.fromApplication(context.applicationContext, RepositoryEntryPoints::class.java)

    suspend fun newestOnline(context: Context, moduleId: String): OnlineModule? {
        val normalized = ModuleIdentity.normalize(moduleId)
        return repositories(context)
            .localRepository()
            .getOnlineAllAsFlow(duplicates = true)
            .first()
            .filter { ModuleIdentity.normalize(it.id) == normalized }
            .maxByOrNull { it.versionCode }
    }

    suspend fun moduleStatus(context: Context, moduleId: String): TaskerResultOutput {
        val repos = repositories(context)
        runCatching { repos.modulesRepository().getLocalAll() }
        val normalized = ModuleIdentity.normalize(moduleId)
        val local = repos.localRepository().getLocalByIdOrNull(normalized)
        val online = newestOnline(context, normalized)
        val tracked = repos.localRepository().hasUpdatableTag(normalized)
        val pendingReboot = repos.operationHistoryRepository().getAll().any { entry ->
            entry.isPendingReboot && entry.moduleId?.let { ModuleIdentity.matches(it, normalized) } == true
        }
        val updateAvailable = local != null && online != null && online.versionCode > local.versionCode
        val state = local?.state?.name.orEmpty()
        return taskerResultOutput(
            success = true,
            status = state.ifBlank { if (online != null) "NOT_INSTALLED" else "NOT_FOUND" },
            message = when {
                local != null -> "Module status loaded"
                online != null -> "Module is available but not installed"
                else -> "Module was not found"
            },
            moduleId = local?.id?.id ?: online?.id ?: normalized,
            moduleName = local?.name ?: online?.name.orEmpty(),
            installed = local != null,
            enabled = local?.state == State.ENABLE || local?.state == State.UPDATE,
            installedVersion = local?.version.orEmpty(),
            installedVersionCode = local?.versionCode ?: -1,
            availableVersion = online?.version.orEmpty(),
            availableVersionCode = online?.versionCode ?: -1,
            updateAvailable = updateAvailable,
            updateIgnored = !tracked,
            repository = online?.repoUrl.orEmpty(),
            rebootRequired = pendingReboot,
            resultJson = JSONObject()
                .put("module_id", local?.id?.id ?: online?.id ?: normalized)
                .put("installed", local != null)
                .put("enabled", local?.state == State.ENABLE || local?.state == State.UPDATE)
                .put("installed_version", local?.version.orEmpty())
                .put("installed_version_code", local?.versionCode ?: -1)
                .put("available_version", online?.version.orEmpty())
                .put("available_version_code", online?.versionCode ?: -1)
                .put("update_available", updateAvailable)
                .put("update_ignored", !tracked)
                .put("repository", online?.repoUrl.orEmpty())
                .put("reboot_required", pendingReboot)
                .toString(),
        )
    }

    fun operationOutput(entry: OperationHistoryEntity, logUri: String = ""): TaskerResultOutput =
        taskerResultOutput(
            success = entry.status == OperationStatus.SUCCEEDED.name || entry.status == OperationStatus.RUNNING.name,
            status = entry.status,
            message = entry.summary,
            operationId = entry.id,
            operationType = entry.kind,
            phase = entry.phase.orEmpty(),
            progress = entry.progress ?: -1,
            moduleId = entry.moduleId.orEmpty(),
            moduleName = entry.moduleName.orEmpty(),
            rebootRequired = entry.isPendingReboot,
            rollbackAvailable = entry.canRollback || !entry.rollbackArchivePath.isNullOrBlank(),
            errorCode = if (entry.isFailed) "OPERATION_FAILED" else "",
            errorMessage = entry.errorMessage.orEmpty(),
            logUri = logUri,
            resultJson = JSONObject()
                .put("operation_id", entry.id)
                .put("type", entry.kind)
                .put("status", entry.status)
                .put("phase", entry.phase.orEmpty())
                .put("progress", entry.progress ?: -1)
                .put("module_id", entry.moduleId.orEmpty())
                .put("module_name", entry.moduleName.orEmpty())
                .put("reboot_required", entry.isPendingReboot)
                .put("rollback_available", entry.canRollback || !entry.rollbackArchivePath.isNullOrBlank())
                .put("error", entry.errorMessage.orEmpty())
                .toString(),
        )

    fun listJson(items: List<TaskerResultOutput>): String {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject(item.resultJson)) }
        return array.toString()
    }
}

internal object TaskerLogExporter {
    private const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"

    fun export(context: Context, entry: OperationHistoryEntity): String {
        val directory = File(context.cacheDir, "tasker-logs").apply { mkdirs() }
        val expiry = System.currentTimeMillis() - LOG_RETENTION_MS
        directory.listFiles()?.filter { it.isFile && it.lastModified() < expiry }?.forEach { it.delete() }
        val safeId = entry.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(directory, "$safeId.log")
        file.writeText(
            buildString {
                appendLine("MMRL operation ${entry.id}")
                appendLine("Type: ${entry.kind}")
                appendLine("Status: ${entry.status}")
                appendLine("Phase: ${entry.phase.orEmpty()}")
                appendLine("Module: ${entry.moduleName.orEmpty()} (${entry.moduleId.orEmpty()})")
                appendLine("Summary: ${entry.summary}")
                if (!entry.errorMessage.isNullOrBlank()) appendLine("Error: ${entry.errorMessage}")
                appendLine()
                append(entry.technicalLog)
            },
        )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        runCatching {
            context.grantUriPermission(TASKER_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return uri.toString()
    }

    private const val LOG_RETENTION_MS = 24L * 60L * 60L * 1000L
}
