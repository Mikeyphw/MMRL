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
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.operation.ModuleMutationExecutor
import com.dergoogler.mmrl.operation.OperationReconciliationPolicy
import com.dergoogler.mmrl.operation.PrivilegedOperationCoordinator
import com.dergoogler.mmrl.operation.RollbackAvailabilityPolicy
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
        private val moduleMutationExecutor: ModuleMutationExecutor,
        private val operationCoordinator: PrivilegedOperationCoordinator,
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

        val activityAttentionCount =
            combine(allHistory, ashManager.state) { entries, ashState ->
                val ashEntries = ashState.snapshot?.activity.orEmpty().map { it.toHistoryEntry() }
                (entries + ashEntries)
                    .distinctBy(OperationHistoryEntity::id)
                    .count { entry -> entry.isFailed || entry.isPendingReboot || entry.isRunning }
            }.stateIn(
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

        override fun onCleared() {
            ashManager.releaseRootSession()
            super.onCleared()
        }

        val visibleHistory =
            combine(allHistory, ashManager.state, filterFlow) { entries, ashState, filter ->
                val ashEntries = ashState.snapshot?.activity.orEmpty().map { it.toHistoryEntry() }
                (entries + ashEntries)
                    .distinctBy(OperationHistoryEntity::id)
                    .map(::withTruthfulRollbackAvailability)
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

        suspend fun loadDetails(entry: OperationHistoryEntity): OperationHistoryEntity =
            withTruthfulRollbackAvailability(
                if (entry.origin == ASH_ORIGIN) entry else historyRepository.getById(entry.id) ?: entry,
            )

        fun clearHistory() {
            viewModelScope.launch {
                historyRepository.clearCompleted()
                messagesFlow.emit("Safely removable completed activity history cleared; protected reconciliation/reboot records were preserved")
            }
        }

        fun delete(entry: OperationHistoryEntity) {
            if (entry.origin == ASH_ORIGIN) {
                emitMessage("AshReXcue events are managed by the protection module")
                return
            }
            if (!entry.canDelete) {
                emitMessage(if (entry.isPendingReboot) "Pending-reboot activity must be preserved" else "Active or unresolved activity cannot be deleted")
                return
            }
            viewModelScope.launch {
                if (historyRepository.delete(entry.id)) {
                    messagesFlow.emit("Activity entry removed")
                } else {
                    messagesFlow.emit("Activity entry is active, unresolved, or still requires reboot reconciliation")
                }
            }
        }

        fun reconcile(entry: OperationHistoryEntity) {
            viewModelScope.launch {
                val current = historyRepository.getById(entry.id)
                if (current?.status != OperationStatus.OUTCOME_UNKNOWN.name) {
                    messagesFlow.emit("This operation no longer requires reconciliation")
                    return@launch
                }
                val kind = runCatching { OperationKind.valueOf(current.kind) }.getOrNull()
                val moduleId = current.moduleId?.let(ModId::parseOrNull)
                if (kind == null || moduleId == null || !PlatformManager.isAlive) {
                    historyRepository.appendLog(current.id, "Reconciliation unavailable: authoritative module backend is not available")
                    messagesFlow.emit("Unable to reconcile this outcome automatically; backend evidence is unavailable")
                    return@launch
                }
                val actual = runCatching { PlatformManager.moduleManager.getModuleById(moduleId) }.getOrNull()
                when (
                    OperationReconciliationPolicy.classify(
                        kind = kind,
                        targetVersion = current.targetVersion,
                        previousVersion = current.previousVersion,
                        modulePresent = actual != null,
                        actualVersion = actual?.version,
                        actualState = actual?.state?.name,
                    )
                ) {
                    OperationReconciliationPolicy.Resolution.SUCCEEDED -> {
                        historyRepository.resolveUnknown(
                            current.id,
                            succeeded = true,
                            summary = "Reconciliation confirmed the requested backend state",
                        )
                        messagesFlow.emit("Reconciliation confirmed success")
                    }
                    OperationReconciliationPolicy.Resolution.FAILED_RETRYABLE -> {
                        historyRepository.resolveUnknown(
                            current.id,
                            succeeded = false,
                            summary = "Reconciliation confirmed the requested module state was not applied",
                            retryable = true,
                        )
                        messagesFlow.emit("Reconciliation confirmed failure; retry is now available")
                    }
                    OperationReconciliationPolicy.Resolution.FAILED_NON_RETRYABLE -> {
                        historyRepository.resolveUnknown(
                            current.id,
                            succeeded = false,
                            summary = "Reconciliation confirmed the reviewed install target was not applied; review a fresh artifact before retrying",
                            retryable = false,
                        )
                        messagesFlow.emit("Reconciliation confirmed failure; a fresh review is required before another install")
                    }
                    OperationReconciliationPolicy.Resolution.UNRESOLVED -> {
                        historyRepository.appendLog(current.id, "Reconciliation could not prove success or a known-safe failure; outcome remains unknown")
                        messagesFlow.emit("Outcome is still unknown; retry and rollback remain blocked")
                    }
                }
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
            if (!entry.canRetry) {
                emitMessage(
                    if (entry.status == com.dergoogler.mmrl.database.entity.history.OperationStatus.OUTCOME_UNKNOWN.name)
                        "Reconcile the unknown privileged outcome before retrying"
                    else
                        "This operation cannot be retried",
                )
                return
            }
            val action = entry.retryAction.toOperationActionOrNull()
            if (action == null) {
                emitMessage("This operation cannot be retried")
                return
            }

            when (action) {
                OperationAction.DOWNLOAD -> retryDownload(context, entry)
                OperationAction.INSTALL -> emitMessage("Install retry requires a fresh archive review")
                OperationAction.RUN_ACTION -> emitMessage("Module actions are not automatically retryable")
                OperationAction.ENABLE,
                OperationAction.DISABLE,
                OperationAction.REMOVE,
                -> executeModuleState(entry, action, rollback = false)

                OperationAction.CANCEL_DOWNLOAD -> cancel(context, entry)
            }
        }

        fun rollback(context: Context, entry: OperationHistoryEntity) {
            val action = entry.rollbackAction.toOperationActionOrNull()
            val status = runCatching { OperationStatus.valueOf(entry.status) }.getOrNull()
            val archiveExists = entry.rollbackArchivePath?.let(::File)?.isFile == true
            val available = status != null && RollbackAvailabilityPolicy.isAvailable(
                status = status,
                rollbackAction = action,
                rollbackArchivePath = entry.rollbackArchivePath,
                rollbackArchiveExists = archiveExists,
            )
            if (!available || action == null) {
                emitMessage(
                    if (entry.status == OperationStatus.OUTCOME_UNKNOWN.name)
                        "Reconcile the unknown privileged outcome before rollback"
                    else
                        "No safe rollback is available for this operation",
                )
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
                val queued = historyRepository.transition(
                    id = entry.id,
                    to = OperationStatus.QUEUED,
                    from = setOf(OperationStatus.WAITING_APPROVAL, OperationStatus.RUNNING),
                    summary = "Approved; queued for execution",
                    phase = com.dergoogler.mmrl.database.entity.history.OperationPhase.STAGE,
                )
                if (!queued) {
                    request.reviewToken?.let { token ->
                        com.dergoogler.mmrl.tasker.TaskerReviewTokenStore.releaseClaim(context, token, entry.id)
                    }
                    TaskerRootRequestStore.remove(context, request.id)
                    historyRepository.appendLog(entry.id, "Approval ignored because the operation is no longer waiting")
                    messagesFlow.emit("This Tasker request is no longer waiting for approval")
                    return@launch
                }
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
            if (!entry.isRunning) {
                emitMessage("This operation cannot be cancelled")
                return
            }
            if (entry.kind == OperationKind.DOWNLOAD.name) {
                DownloadService.cancel(context, entry.id)
                emitMessage("Download cancellation requested")
                return
            }
            viewModelScope.launch {
                if (operationCoordinator.requestCancellation(entry.id)) {
                    messagesFlow.emit("Privileged cancellation requested")
                } else {
                    messagesFlow.emit("This operation can no longer be cancelled")
                }
            }
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
                confirm = true,
                parentOperationId = entry.id,
                rollbackMode = true,
                expectedModuleId = entry.moduleId,
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
                val module = localRepository.getLocalByIdOrNull(moduleId)
                if (module == null) {
                    messagesFlow.emit("The original module is unavailable; reconcile installed state first")
                    return@launch
                }
                moduleMutationExecutor.execute(
                    module = module,
                    useShell = entry.useShell,
                    kind = if (rollback) OperationKind.ROLLBACK else action.toOperationKind(),
                    action = action,
                    rollbackAction = action.inverse(),
                    successSummary = if (rollback) "Rollback completed; reboot required" else "Retry completed; reboot required",
                    parentId = entry.id,
                ).onSuccess {
                    messagesFlow.emit(if (rollback) "Rollback completed" else "Operation completed")
                }.onFailure { error ->
                    messagesFlow.emit(error.message ?: "Module operation failed")
                }
            }
        }

        private fun withTruthfulRollbackAvailability(entry: OperationHistoryEntity): OperationHistoryEntity {
            val action = entry.rollbackAction.toOperationActionOrNull()
            val status = runCatching { OperationStatus.valueOf(entry.status) }.getOrNull()
            val archiveExists = entry.rollbackArchivePath?.let(::File)?.isFile == true
            val available = status != null && RollbackAvailabilityPolicy.isAvailable(
                status = status,
                rollbackAction = action,
                rollbackArchivePath = entry.rollbackArchivePath,
                rollbackArchiveExists = archiveExists,
            )
            return if (available || entry.rollbackAction.isNullOrBlank()) entry else entry.copy(rollbackAction = null)
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
            "outcome_unknown", "unknown" -> com.dergoogler.mmrl.database.entity.history.OperationStatus.OUTCOME_UNKNOWN
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
