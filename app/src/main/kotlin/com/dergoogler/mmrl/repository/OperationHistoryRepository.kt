package com.dergoogler.mmrl.repository

import android.content.Context
import com.dergoogler.mmrl.database.dao.OperationHistoryDao
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationHistoryRepository
    @Inject
    constructor(
        private val dao: OperationHistoryDao,
        @param:ApplicationContext private val context: Context,
    ) {
        fun observeAll(): Flow<List<OperationHistoryEntity>> = dao.observeAll()

        fun observePendingRebootCount(): Flow<Int> = dao.observePendingRebootCount()

        suspend fun getById(id: String): OperationHistoryEntity? =
            withContext(Dispatchers.IO) { dao.getById(id) }

        suspend fun getAll(): List<OperationHistoryEntity> =
            withContext(Dispatchers.IO) { dao.getAll() }

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
        ): String =
            withContext(Dispatchers.IO) {
                val id = existingId ?: UUID.randomUUID().toString()
                dao.insert(
                    OperationHistoryEntity(
                        id = id,
                        kind = kind.name,
                        status = OperationStatus.RUNNING.name,
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
                    ),
                )
                dao.prune(MAX_HISTORY_ENTRIES)
                id
            }

        suspend fun progress(
            id: String,
            progress: Float,
        ) = withContext(Dispatchers.IO) {
            dao.updateProgress(id, (progress.coerceIn(0f, 1f) * 100).toInt())
        }

        suspend fun phase(
            id: String,
            phase: OperationPhase,
            summary: String,
        ) = withContext(Dispatchers.IO) {
            dao.updatePhase(id, phase.name, summary)
        }

        suspend fun attachRollbackArchive(
            id: String,
            path: String?,
            previousVersion: String?,
            targetVersion: String?,
        ) = withContext(Dispatchers.IO) {
            dao.attachRollbackArchive(id, path, previousVersion, targetVersion)
        }

        suspend fun inspectionSummary(
            id: String,
            summary: String?,
        ) = withContext(Dispatchers.IO) {
            dao.updateInspectionSummary(id, summary)
        }

        suspend fun appendLog(
            id: String,
            line: String,
        ) = withContext(Dispatchers.IO) {
            if (line.isNotBlank()) dao.appendLog(id, line.take(MAX_LOG_LINE_LENGTH), MAX_LOG_LENGTH)
        }

        suspend fun succeed(
            id: String,
            summary: String,
            requiresReboot: Boolean = false,
            rollbackAction: OperationAction? = null,
        ) = withContext(Dispatchers.IO) {
            dao.finish(
                id = id,
                status = OperationStatus.SUCCEEDED.name,
                summary = summary,
                completedAt = System.currentTimeMillis(),
                requiresReboot = requiresReboot,
                errorMessage = null,
                rollbackAction = rollbackAction?.name,
            )
        }

        suspend fun fail(
            id: String,
            summary: String,
            error: Throwable? = null,
            requiresReboot: Boolean = false,
            rollbackAction: OperationAction? = null,
        ) = withContext(Dispatchers.IO) {
            dao.finish(
                id = id,
                status = OperationStatus.FAILED.name,
                summary = summary,
                completedAt = System.currentTimeMillis(),
                requiresReboot = requiresReboot,
                errorMessage = error?.message ?: summary,
                rollbackAction = rollbackAction?.name,
            )
            error?.stackTraceToString()?.lineSequence()?.take(MAX_STACKTRACE_LINES)?.forEach {
                dao.appendLog(id, it.take(MAX_LOG_LINE_LENGTH), MAX_LOG_LENGTH)
            }
            runCatching {
                val entry = dao.getById(id) ?: return@runCatching
                com.dergoogler.mmrl.tasker.TaskerEventPublisher.operationFailed(context, entry)
            }
        }

        suspend fun cancel(
            id: String,
            summary: String,
        ) = withContext(Dispatchers.IO) {
            dao.finish(
                id = id,
                status = OperationStatus.CANCELLED.name,
                summary = summary,
                completedAt = System.currentTimeMillis(),
                requiresReboot = false,
                errorMessage = null,
                rollbackAction = null,
            )
        }

        suspend fun markPendingRebootsCompleted() =
            withContext(Dispatchers.IO) {
                dao.markPendingRebootsCompleted(System.currentTimeMillis())
            }

        suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.deleteById(id) }

        suspend fun clearCompleted() = withContext(Dispatchers.IO) { dao.deleteCompleted() }

        suspend fun recoverAfterBoot() =
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                dao.markRunningInterrupted(
                    summary = "Operation interrupted by device reboot",
                    completedAt = now,
                )
                dao.markPendingRebootsCompleted(now)
            }


        suspend fun recoverStaleOperations() =
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                dao.markStaleRunningInterrupted(
                    cutoff = now - STALE_OPERATION_AGE_MS,
                    summary = "Operation interrupted before completion",
                    completedAt = now,
                )
            }

        companion object {
            private const val MAX_HISTORY_ENTRIES = 500
            private const val MAX_LOG_LINE_LENGTH = 16_384
            private const val MAX_LOG_LENGTH = 512 * 1024
            private const val MAX_STACKTRACE_LINES = 80
            private const val STALE_OPERATION_AGE_MS = 12L * 60L * 60L * 1000L
        }
    }
