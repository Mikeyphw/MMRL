package com.dergoogler.mmrl.repository

import android.content.Context
import com.dergoogler.mmrl.database.dao.OperationHistoryDao
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import com.dergoogler.mmrl.database.entity.history.OperationTechnicalLogEntity
import com.dergoogler.mmrl.operation.OperationHistoryRetentionPolicy
import com.dergoogler.mmrl.operation.OperationRecoveryPolicy
import com.dergoogler.mmrl.operation.OperationStateMachine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationHistoryRepository
    @Inject
    constructor(
        private val dao: OperationHistoryDao,
        @param:ApplicationContext private val context: Context,
    ) {
        private val historyGate = Mutex()

        fun observeAll(): Flow<List<OperationHistoryEntity>> = dao.observeAll()

        fun observePendingRebootCount(): Flow<Int> = dao.observePendingRebootCount()

        suspend fun getById(id: String): OperationHistoryEntity? =
            withContext(Dispatchers.IO) {
                dao.getById(id)?.let { it.copy(technicalLog = dao.getTechnicalLog(id).orEmpty()) }
            }

        suspend fun getAll(): List<OperationHistoryEntity> =
            withContext(Dispatchers.IO) { dao.getAll() }

        suspend fun technicalLog(id: String): String =
            withContext(Dispatchers.IO) { dao.getTechnicalLog(id).orEmpty() }

        suspend fun start(
            kind: OperationKind,
            title: String,
            summary: String = "",
            moduleId: String? = null,
            moduleName: String? = null,
            sourceUri: String? = null,
            sourceUrl: String? = null,
            destinationPath: String? = null,
            retryAction: OperationAction? = null,
            rollbackAction: OperationAction? = null,
            useShell: Boolean = false,
            parentId: String? = null,
            existingId: String? = null,
            origin: String? = null,
            initialStatus: OperationStatus = OperationStatus.RUNNING,
            idempotencyKey: String? = null,
            sourceOperationId: String? = null,
        ): String =
            withContext(Dispatchers.IO) {
                historyGate.withLock {
                idempotencyKey?.let { key ->
                    dao.getByIdempotencyKey(key)?.let { return@withLock it.id }
                }
                evictForInsertLocked()
                val id = existingId ?: UUID.randomUUID().toString()
                val value =
                    OperationHistoryEntity(
                        id = id,
                        kind = kind.name,
                        status = initialStatus.name,
                        title = title,
                        summary = summary,
                        moduleId = moduleId,
                        moduleName = moduleName,
                        sourceUri = sourceUri,
                        sourceUrl = sourceUrl,
                        destinationPath = destinationPath,
                        startedAt = System.currentTimeMillis(),
                        progress = 0,
                        retryAction = retryAction?.name,
                        rollbackAction = rollbackAction?.name,
                        useShell = useShell,
                        parentId = parentId,
                        phase = OperationPhase.REVIEW.name,
                        origin = origin,
                        idempotencyKey = idempotencyKey,
                        sourceOperationId = sourceOperationId,
                    )
                val inserted = dao.insert(value)
                if (inserted == -1L && idempotencyKey != null) {
                    return@withLock dao.getByIdempotencyKey(idempotencyKey)?.id
                        ?: error("Unable to resolve idempotent operation")
                }
                require(inserted != -1L) { "Operation ID already exists: $id" }
                dao.insertLog(OperationTechnicalLogEntity(id))
                dao.deleteOrphanLogs()
                id
                }
            }

        suspend fun queue(
            kind: OperationKind,
            title: String,
            summary: String,
            moduleId: String? = null,
            moduleName: String? = null,
            idempotencyKey: String,
            parentId: String? = null,
            sourceOperationId: String? = null,
            origin: String? = null,
        ): String =
            start(
                kind = kind,
                title = title,
                summary = summary,
                moduleId = moduleId,
                moduleName = moduleName,
                parentId = parentId,
                sourceOperationId = sourceOperationId,
                origin = origin,
                initialStatus = OperationStatus.QUEUED,
                idempotencyKey = idempotencyKey,
            )

        suspend fun sourceUri(id: String, sourceUri: String): Boolean = withContext(Dispatchers.IO) {
            require(sourceUri.isNotBlank()) { "Source URI must not be blank" }
            dao.updateSourceUri(id, sourceUri) == 1
        }

        suspend fun progress(id: String, progress: Float) = withContext(Dispatchers.IO) {
            dao.updateProgress(id, (progress.coerceIn(0f, 1f) * 100).toInt())
        }

        suspend fun phase(id: String, phase: OperationPhase, summary: String) = withContext(Dispatchers.IO) {
            dao.updatePhase(id, phase.name, summary)
        }

        suspend fun updateIdentity(
            id: String,
            kind: OperationKind,
            title: String,
            moduleId: String?,
            moduleName: String?,
            targetVersion: String?,
        ) = withContext(Dispatchers.IO) {
            dao.updateIdentity(id, kind.name, title, moduleId, moduleName, targetVersion)
        }

        suspend fun attachRollbackArchive(id: String, path: String?, previousVersion: String?, targetVersion: String?) =
            withContext(Dispatchers.IO) { dao.attachRollbackArchive(id, path, previousVersion, targetVersion) }

        suspend fun inspectionSummary(id: String, summary: String?) =
            withContext(Dispatchers.IO) { dao.updateInspectionSummary(id, summary) }

        suspend fun appendLog(id: String, line: String) = withContext(Dispatchers.IO) {
            if (line.isNotBlank()) {
                dao.insertLog(OperationTechnicalLogEntity(id))
                dao.appendLog(id, line.take(MAX_LOG_LINE_LENGTH), MAX_LOG_LENGTH)
            }
        }

        suspend fun transition(
            id: String,
            to: OperationStatus,
            from: Set<OperationStatus>,
            summary: String? = null,
            phase: OperationPhase? = null,
        ): Boolean = withContext(Dispatchers.IO) {
            val current = dao.getById(id) ?: return@withContext false
            val currentStatus = runCatching { OperationStatus.valueOf(current.status) }.getOrNull()
                ?: return@withContext false
            if (currentStatus !in from || !OperationStateMachine.canTransition(currentStatus, to)) {
                return@withContext false
            }
            dao.transition(id, listOf(currentStatus.name), to.name, summary, phase?.name) == 1
        }

        suspend fun claimIdempotencyKey(id: String, key: String): Boolean = withContext(Dispatchers.IO) {
            require(key.isNotBlank()) { "Idempotency key must not be blank" }
            dao.claimIdempotencyKey(id, key) == 1
        }

        suspend fun markMutationStarted(id: String): Boolean = withContext(Dispatchers.IO) {
            dao.markMutationStarted(id, System.currentTimeMillis()) == 1
        }

        suspend fun requestCancel(id: String, summary: String = "Cancellation requested"): Boolean =
            transition(
                id = id,
                to = OperationStatus.CANCEL_REQUESTED,
                from = setOf(OperationStatus.QUEUED, OperationStatus.RUNNING, OperationStatus.WAITING_APPROVAL),
                summary = summary,
            )

        suspend fun succeed(
            id: String,
            summary: String,
            requiresReboot: Boolean = false,
            rollbackAction: OperationAction? = null,
        ) = terminal(
            id = id,
            status = OperationStatus.SUCCEEDED,
            summary = summary,
            requiresReboot = requiresReboot,
            errorMessage = null,
            rollbackAction = rollbackAction,
            reconciled = true,
        )

        suspend fun fail(
            id: String,
            summary: String,
            error: Throwable? = null,
            requiresReboot: Boolean = false,
            rollbackAction: OperationAction? = null,
            retryable: Boolean = false,
        ) {
            if (!retryable) withContext(Dispatchers.IO) { dao.clearRetryAction(id) }
            val changed = terminal(
                id = id,
                status = OperationStatus.FAILED,
                summary = summary,
                requiresReboot = requiresReboot,
                errorMessage = error?.message ?: summary,
                rollbackAction = rollbackAction,
                reconciled = true,
            )
            if (!changed) return
            error?.stackTraceToString()?.lineSequence()?.take(MAX_STACKTRACE_LINES)?.forEach {
                appendLog(id, it.take(MAX_LOG_LINE_LENGTH))
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    val entry = getById(id) ?: return@runCatching
                    com.dergoogler.mmrl.tasker.TaskerEventPublisher.operationFailed(context, entry)
                }
            }
        }

        suspend fun cancel(id: String, summary: String): Boolean =
            terminal(
                id = id,
                status = OperationStatus.CANCELLED,
                summary = summary,
                requiresReboot = false,
                errorMessage = null,
                rollbackAction = null,
                reconciled = true,
            )

        suspend fun outcomeUnknown(
            id: String,
            summary: String,
            error: Throwable? = null,
            requiresReboot: Boolean = false,
            rollbackAction: OperationAction? = null,
        ): Boolean {
            error?.message?.takeIf(String::isNotBlank)?.let { appendLog(id, "Outcome unknown: $it") }
            return terminal(
                id = id,
                status = OperationStatus.OUTCOME_UNKNOWN,
                summary = summary,
                requiresReboot = requiresReboot,
                errorMessage = error?.message ?: summary,
                rollbackAction = rollbackAction,
                reconciled = false,
            )
        }

        private suspend fun terminal(
            id: String,
            status: OperationStatus,
            summary: String,
            requiresReboot: Boolean,
            errorMessage: String?,
            rollbackAction: OperationAction?,
            reconciled: Boolean,
        ): Boolean = withContext(Dispatchers.IO) {
            val current = dao.getById(id) ?: return@withContext false
            val currentStatus = runCatching { OperationStatus.valueOf(current.status) }.getOrNull()
                ?: return@withContext false
            if (!OperationStateMachine.canTransition(currentStatus, status)) return@withContext false
            dao.finish(
                id = id,
                fromStatuses = listOf(currentStatus.name),
                status = status.name,
                summary = summary,
                completedAt = System.currentTimeMillis(),
                requiresReboot = requiresReboot,
                errorMessage = errorMessage,
                rollbackAction = rollbackAction?.name,
                reconciledAt = if (reconciled) System.currentTimeMillis() else null,
            ) == 1
        }

        suspend fun resolveUnknown(
            id: String,
            succeeded: Boolean,
            summary: String,
            retryable: Boolean = false,
        ): Boolean = withContext(Dispatchers.IO) {
            dao.resolveUnknown(
                id = id,
                resolvedStatus = if (succeeded) OperationStatus.SUCCEEDED.name else OperationStatus.FAILED.name,
                summary = summary,
                retryable = !succeeded && retryable,
                at = System.currentTimeMillis(),
            ) == 1
        }

        suspend fun markPendingRebootsCompleted() = withContext(Dispatchers.IO) {
            dao.markPendingRebootsCompleted(System.currentTimeMillis())
        }

        suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
            historyGate.withLock {
                val removed = dao.deleteIfRemovable(id) == 1
                if (removed) dao.deleteLogById(id)
                removed
            }
        }

        suspend fun clearCompleted() = withContext(Dispatchers.IO) {
            historyGate.withLock {
                OperationHistoryRetentionPolicy.clearable(dao.getAll()).forEach { id ->
                    if (dao.deleteIfRemovable(id) == 1) dao.deleteLogById(id)
                }
                dao.deleteOrphanLogs()
            }
        }

        suspend fun enforceRetention() = withContext(Dispatchers.IO) {
            historyGate.withLock {
                val entries = dao.getAll()
                val overBy = (entries.size - MAX_HISTORY_ENTRIES).coerceAtLeast(0)
                if (overBy > 0) {
                    val candidates = entries
                        .filter(OperationHistoryRetentionPolicy::isSafelyEvictable)
                        .sortedBy(OperationHistoryEntity::startedAt)
                        .take(overBy)
                    candidates.forEach { entry ->
                        if (dao.deleteIfRemovable(entry.id) == 1) dao.deleteLogById(entry.id)
                    }
                    dao.deleteOrphanLogs()
                }
            }
        }

        private suspend fun evictForInsertLocked() {
            val entries = dao.getAll()
            OperationHistoryRetentionPolicy.evictionsForInsert(entries, MAX_HISTORY_ENTRIES).forEach { id ->
                check(dao.deleteIfRemovable(id) == 1) { "Operation history changed during retention enforcement" }
                dao.deleteLogById(id)
            }
        }

        suspend fun recoverAfterProcessRestart() = withContext(Dispatchers.IO) {
            recoverInterrupted(
                dao.getInterruptibleActive(OperationRecoveryPolicy.interruptibleStatusNames).filter { entry ->
                    val status = runCatching { OperationStatus.valueOf(entry.status) }.getOrNull()
                    status != null && OperationRecoveryPolicy.recoverImmediately(status, entry.origin)
                },
                "after app process restart",
            )
        }

        suspend fun recoverAfterBoot() = withContext(Dispatchers.IO) {
            recoverInterrupted(
                dao.getInterruptibleActive(OperationRecoveryPolicy.interruptibleStatusNames).filter { entry ->
                    val status = runCatching { OperationStatus.valueOf(entry.status) }.getOrNull()
                    status != null && OperationRecoveryPolicy.recoverImmediately(status, entry.origin)
                },
                "after device reboot",
            )
            // A completed device reboot satisfies records that were explicitly waiting for reboot.
            dao.markPendingRebootsCompleted(System.currentTimeMillis())
        }

        suspend fun recoverStaleOperations() = withContext(Dispatchers.IO) {
            recoverInterrupted(
                dao.getStaleInterruptibleActive(
                    System.currentTimeMillis() - STALE_OPERATION_AGE_MS,
                    OperationRecoveryPolicy.interruptibleStatusNames,
                ).filter { entry ->
                    val status = runCatching { OperationStatus.valueOf(entry.status) }.getOrNull()
                    status != null && OperationRecoveryPolicy.recoverWhenStale(status)
                },
                "after client interruption",
            )
        }

        private suspend fun recoverInterrupted(
            entries: List<OperationHistoryEntity>,
            reason: String,
        ) {
            entries.forEach { entry ->
                if (entry.mutationStartedAt == null) {
                    cancel(entry.id, "Operation cancelled $reason before privileged mutation began")
                } else {
                    outcomeUnknown(
                        entry.id,
                        "Privileged outcome unknown $reason; reconcile before retrying",
                        requiresReboot = entry.requiresReboot,
                        rollbackAction = entry.rollbackAction?.let { runCatching { OperationAction.valueOf(it) }.getOrNull() },
                    )
                }
            }
        }

        companion object {
            private const val MAX_HISTORY_ENTRIES = 500
            private const val MAX_LOG_LINE_LENGTH = 16_384
            private const val MAX_LOG_LENGTH = 512 * 1024
            private const val MAX_STACKTRACE_LINES = 80
            private const val STALE_OPERATION_AGE_MS = 12L * 60L * 60L * 1000L
        }
    }
