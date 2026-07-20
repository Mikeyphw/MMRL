package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.ash.AshReXcueManager
import com.dergoogler.mmrl.ash.model.ActivityItem
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.repository.LocalRepository
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import com.dergoogler.mmrl.service.DownloadService
import com.dergoogler.mmrl.tasker.TaskerRootDispatcher
import com.dergoogler.mmrl.tasker.TaskerRootRequestStore
import com.dergoogler.mmrl.ui.activity.terminal.action.ActionActivity
import com.dergoogler.mmrl.ui.activity.terminal.install.InstallActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ActivityViewModel
    @Inject
    constructor(
        application: Application,
        localRepository: LocalRepository,
        modulesRepository: ModulesRepository,
        userPreferencesRepository: UserPreferencesRepository,
        private val historyRepository: OperationHistoryRepository,
        private val ashManager: AshReXcueManager,
    ) : MMRLViewModel(
            application = application,
            localRepository = localRepository,
            modulesRepository = modulesRepository,
            userPreferencesRepository = userPreferencesRepository,
        ) {
        private val filterFlow = MutableStateFlow(ActivityFilter.ALL)
        val filter = filterFlow.asStateFlow()

        private val messagesFlow = MutableSharedFlow<String>()
        val messages = messagesFlow.asSharedFlow()

        val allHistory =
            historyRepository.observeAll().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

        val pendingRebootCount =
            historyRepository.observePendingRebootCount().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

        init {
            viewModelScope.launch {
                historyRepository.recoverStaleOperations()
                ashManager.refreshIfStale()
            }
        }

        val visibleHistory =
            combine(allHistory, ashManager.state, filterFlow) { entries, ashState, filter ->
                val ashEntries = ashState.snapshot?.activity.orEmpty().map { it.toHistoryEntry() }
                (entries + ashEntries)
                    .distinctBy(OperationHistoryEntity::id)
                    .sortedByDescending(OperationHistoryEntity::startedAt)
                    .filter { entry ->
                        when (filter) {
                            ActivityFilter.ALL -> true
                            ActivityFilter.RUNNING -> entry.isRunning
                            ActivityFilter.DOWNLOADS -> entry.kind == OperationKind.DOWNLOAD.name
                            ActivityFilter.FAILED -> entry.isFailed
                            ActivityFilter.PENDING_REBOOT -> entry.isPendingReboot
                            ActivityFilter.ASHREXCUE -> entry.origin == ASH_ORIGIN
                        }
                    }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

        fun setFilter(value: ActivityFilter) {
            filterFlow.value = value
        }

        fun clearHistory() {
            viewModelScope.launch {
                historyRepository.clearCompleted()
                messagesFlow.emit("Completed activity history cleared")
            }
        }

        fun delete(entry: OperationHistoryEntity) {
            if (entry.origin == ASH_ORIGIN) {
                emitMessage("AshReXcue events are managed by the protection module")
                return
            }
            viewModelScope.launch {
                historyRepository.delete(entry.id)
                messagesFlow.emit("Activity entry removed")
            }
        }

        fun markRebootCompleted() {
            viewModelScope.launch {
                historyRepository.markPendingRebootsCompleted()
                messagesFlow.emit("Pending reboot markers cleared")
            }
        }

        fun retry(
            context: Context,
            entry: OperationHistoryEntity,
        ) {
            val action = entry.retryAction.toOperationActionOrNull()
            if (action == null) {
                emitMessage("This operation cannot be retried")
                return
            }

            when (action) {
                OperationAction.DOWNLOAD -> retryDownload(context, entry)
                OperationAction.INSTALL -> retryInstall(context, entry)
                OperationAction.RUN_ACTION -> retryModuleAction(context, entry)
                OperationAction.ENABLE,
                OperationAction.DISABLE,
                OperationAction.REMOVE,
                -> executeModuleState(entry, action, rollback = false)

                OperationAction.CANCEL_DOWNLOAD -> cancel(context, entry)
            }
        }

        fun rollback(context: Context, entry: OperationHistoryEntity) {
            val action = entry.rollbackAction.toOperationActionOrNull()
            if (action == null) {
                emitMessage("No safe rollback is available for this operation")
                return
            }
            if (action == OperationAction.INSTALL) {
                restoreArchive(context, entry)
            } else {
                executeModuleState(entry, action, rollback = true)
            }
        }


        fun approveTaskerRequest(
            context: Context,
            entry: OperationHistoryEntity,
        ) {
            viewModelScope.launch {
                val request = TaskerRootRequestStore.findByOperationId(context, entry.id)
                if (request == null) {
                    messagesFlow.emit("The pending Tasker request is no longer available")
                    return@launch
                }
                context.getSystemService(NotificationManager::class.java).cancel(request.id.hashCode())
                historyRepository.appendLog(entry.id, "Approved by user from Activity")
                try {
                    TaskerRootDispatcher.enqueue(context, request.id)
                    messagesFlow.emit("Tasker action approved")
                } catch (error: Throwable) {
                    request.reviewToken?.let { token ->
                        com.dergoogler.mmrl.tasker.TaskerReviewTokenStore.releaseClaim(
                            context,
                            token,
                            entry.id,
                        )
                    }
                    TaskerRootRequestStore.remove(context, request.id)
                    historyRepository.fail(
                        entry.id,
                        error.message ?: "Unable to queue approved Tasker action",
                        error,
                    )
                    messagesFlow.emit(error.message ?: "Unable to queue Tasker action")
                }
            }
        }

        fun denyTaskerRequest(
            context: Context,
            entry: OperationHistoryEntity,
        ) {
            viewModelScope.launch {
                val request = TaskerRootRequestStore.findByOperationId(context, entry.id)
                if (request == null) {
                    messagesFlow.emit("The pending Tasker request is no longer available")
                    return@launch
                }
                request.reviewToken?.let { token ->
                    com.dergoogler.mmrl.tasker.TaskerReviewTokenStore.releaseClaim(
                        context,
                        token,
                        entry.id,
                    )
                }
                context.getSystemService(NotificationManager::class.java).cancel(request.id.hashCode())
                TaskerRootRequestStore.remove(context, request.id)
                historyRepository.appendLog(entry.id, "Denied by user from Activity")
                historyRepository.fail(entry.id, "Tasker action denied by user")
                messagesFlow.emit("Tasker action denied")
            }
        }

        fun cancel(context: Context, entry: OperationHistoryEntity) {
            if (!entry.isRunning || entry.kind != OperationKind.DOWNLOAD.name) {
                emitMessage("This operation cannot be cancelled")
                return
            }
            DownloadService.cancel(context, entry.id)
            emitMessage("Download cancellation requested")
        }

        private fun restoreArchive(
            context: Context,
            entry: OperationHistoryEntity,
        ) {
            val path = entry.rollbackArchivePath
            val file = path?.let(::File)
            if (file == null || !file.isFile) {
                emitMessage("The rollback archive is no longer available")
                return
            }
            InstallActivity.start(
                context = context,
                uri = Uri.fromFile(file),
                confirm = false,
                parentOperationId = entry.id,
                rollbackMode = true,
            )
            emitMessage("Rollback installation started")
        }

        private fun retryDownload(
            context: Context,
            entry: OperationHistoryEntity,
        ) {
            val url = entry.sourceUrl
            val path = entry.destinationPath
            if (url.isNullOrBlank() || path.isNullOrBlank()) {
                emitMessage("The original download source is unavailable")
                return
            }

            DownloadService.start(
                context = context,
                task =
                    DownloadService.TaskItem(
                        key = (System.nanoTime() and Int.MAX_VALUE.toLong()).toInt(),
                        url = url,
                        filename = File(path).name,
                        title = entry.moduleName ?: entry.title,
                        desc = entry.summary,
                        parentId = entry.id,
                    ),
                listener = object : DownloadService.IDownloadListener {},
            )
            emitMessage("Download retry started")
        }

        private fun retryInstall(
            context: Context,
            entry: OperationHistoryEntity,
        ) {
            val uris =
                entry.sourceUri
                    ?.lineSequence()
                    ?.filter { it.isNotBlank() }
                    ?.map { Uri.parse(it) }
                    ?.toList()
                    .orEmpty()
            if (uris.isEmpty()) {
                emitMessage("The original installation file is unavailable")
                return
            }
            InstallActivity.start(
                context = context,
                uri = uris,
                confirm = false,
                parentOperationId = entry.id,
            )
        }

        private fun retryModuleAction(
            context: Context,
            entry: OperationHistoryEntity,
        ) {
            val moduleId = entry.moduleId
            if (moduleId.isNullOrBlank()) {
                emitMessage("The original module is unavailable")
                return
            }
            ActionActivity.start(context, ModId(moduleId))
        }

        private fun executeModuleState(
            entry: OperationHistoryEntity,
            action: OperationAction,
            rollback: Boolean,
        ) {
            val moduleId = entry.moduleId
            if (moduleId.isNullOrBlank()) {
                emitMessage("The original module is unavailable")
                return
            }

            viewModelScope.launch {
                if (!PlatformManager.isAlive) {
                    messagesFlow.emit("The root backend is not available")
                    return@launch
                }

                val id = ModId(moduleId)
                val historyId =
                    historyRepository.start(
                        kind = if (rollback) OperationKind.ROLLBACK else action.toOperationKind(),
                        title = entry.moduleName ?: entry.title,
                        summary = if (rollback) "Rolling back ${entry.kind.lowercase()}" else "Retrying ${entry.kind.lowercase()}",
                        moduleId = moduleId,
                        moduleName = entry.moduleName,
                        retryAction = action,
                        rollbackAction = action.inverse(),
                        useShell = entry.useShell,
                        parentId = entry.id,
                    )
                historyRepository.appendLog(historyId, "Parent operation: ${entry.id}")
                historyRepository.appendLog(historyId, "Requested action: ${action.name}")

                val callback =
                    object : IModuleOpsCallback.Stub() {
                        override fun onSuccess(id: ModId) {
                            viewModelScope.launch {
                                historyRepository.succeed(
                                    id = historyId,
                                    summary = if (rollback) "Rollback completed; reboot required" else "Retry completed; reboot required",
                                    requiresReboot = true,
                                    rollbackAction = action.inverse(),
                                )
                                modulesRepository.getLocal(id)
                                messagesFlow.emit(if (rollback) "Rollback completed" else "Operation completed")
                            }
                        }

                        override fun onFailure(
                            id: ModId,
                            msg: String?,
                        ) {
                            viewModelScope.launch {
                                historyRepository.fail(
                                    id = historyId,
                                    summary = msg ?: "Module operation failed",
                                )
                                messagesFlow.emit(msg ?: "Module operation failed")
                            }
                        }
                    }

                try {
                    when (action) {
                        OperationAction.ENABLE -> PlatformManager.moduleManager.enable(id, entry.useShell, callback)
                        OperationAction.DISABLE -> PlatformManager.moduleManager.disable(id, entry.useShell, callback)
                        OperationAction.REMOVE -> PlatformManager.moduleManager.remove(id, entry.useShell, callback)
                        else -> error("Unsupported module state action: $action")
                    }
                } catch (error: Throwable) {
                    historyRepository.fail(
                        id = historyId,
                        summary = error.message ?: "Unable to start module operation",
                        error = error,
                    )
                    messagesFlow.emit(error.message ?: "Unable to start module operation")
                }
            }
        }

        private fun emitMessage(message: String) {
            viewModelScope.launch { messagesFlow.emit(message) }
        }
    }

enum class ActivityFilter {
    ALL,
    RUNNING,
    DOWNLOADS,
    FAILED,
    PENDING_REBOOT,
    ASHREXCUE,
}


private const val ASH_ORIGIN = "ashrexcue"

private fun ActivityItem.toHistoryEntry(): OperationHistoryEntity {
    val operationKind =
        when (type.lowercase()) {
            "restoration", "restore", "trial", "recovery-plan" -> OperationKind.ASH_RESTORATION
            "settings", "setting", "trust" -> OperationKind.ASH_SETTINGS
            "diagnostics" -> OperationKind.ASH_DIAGNOSTICS
            else -> OperationKind.ASH_RESCUE
        }
    val operationStatus =
        when (status.lowercase()) {
            "failed", "error" -> com.dergoogler.mmrl.database.entity.history.OperationStatus.FAILED
            "running", "active" -> com.dergoogler.mmrl.database.entity.history.OperationStatus.RUNNING
            "cancelled", "canceled" -> com.dergoogler.mmrl.database.entity.history.OperationStatus.CANCELLED
            else -> com.dergoogler.mmrl.database.entity.history.OperationStatus.SUCCEEDED
        }
    val timestampMs = if (timestamp in 1..9_999_999_999L) timestamp * 1_000L else timestamp
    return OperationHistoryEntity(
        id = "ash:$type:$id:$timestamp",
        kind = operationKind.name,
        status = operationStatus.name,
        title = title.ifBlank { "AshReXcue event" },
        summary = subtitle.ifBlank { details.ifBlank { type } },
        startedAt = timestampMs,
        completedAt = timestampMs.takeIf { operationStatus != com.dergoogler.mmrl.database.entity.history.OperationStatus.RUNNING },
        requiresReboot = status.equals("queued", ignoreCase = true),
        technicalLog = details,
        errorMessage = details.takeIf { operationStatus == com.dergoogler.mmrl.database.entity.history.OperationStatus.FAILED },
        phase = type,
        origin = ASH_ORIGIN,
    )
}

private fun String?.toOperationActionOrNull(): OperationAction? =
    this?.let { value -> runCatching { OperationAction.valueOf(value) }.getOrNull() }

private fun OperationAction.toOperationKind(): OperationKind =
    when (this) {
        OperationAction.DOWNLOAD -> OperationKind.DOWNLOAD
        OperationAction.INSTALL -> OperationKind.INSTALL
        OperationAction.ENABLE -> OperationKind.ENABLE
        OperationAction.DISABLE -> OperationKind.DISABLE
        OperationAction.REMOVE -> OperationKind.REMOVE
        OperationAction.RUN_ACTION -> OperationKind.MODULE_ACTION
        OperationAction.CANCEL_DOWNLOAD -> OperationKind.DOWNLOAD
    }

private fun OperationAction.inverse(): OperationAction? =
    when (this) {
        OperationAction.ENABLE -> OperationAction.DISABLE
        OperationAction.DISABLE -> OperationAction.ENABLE
        OperationAction.REMOVE -> OperationAction.ENABLE
        OperationAction.CANCEL_DOWNLOAD -> null
        else -> null
    }
