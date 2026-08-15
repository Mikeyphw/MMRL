package com.dergoogler.mmrl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.history.OperationTechnicalLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationHistoryDao {
    @Query(
        """
        SELECT id, kind, status, title, summary, moduleId, moduleName, sourceUri, sourceUrl,
               destinationPath, startedAt, completedAt, progress, requiresReboot, rebootCompletedAt,
               '' AS technicalLog, errorMessage, retryAction, rollbackAction, useShell, parentId,
               phase, rollbackArchivePath, previousVersion, targetVersion, inspectionSummary, origin,
               idempotencyKey, sourceOperationId, mutationStartedAt, reconciledAt
        FROM operationHistory ORDER BY startedAt DESC
        """,
    )
    fun observeAll(): Flow<List<OperationHistoryEntity>>

    @Query("SELECT COUNT(*) FROM operationHistory WHERE requiresReboot = 1 AND rebootCompletedAt IS NULL")
    fun observePendingRebootCount(): Flow<Int>

    @Query(
        """
        SELECT id, kind, status, title, summary, moduleId, moduleName, sourceUri, sourceUrl,
               destinationPath, startedAt, completedAt, progress, requiresReboot, rebootCompletedAt,
               '' AS technicalLog, errorMessage, retryAction, rollbackAction, useShell, parentId,
               phase, rollbackArchivePath, previousVersion, targetVersion, inspectionSummary, origin,
               idempotencyKey, sourceOperationId, mutationStartedAt, reconciledAt
        FROM operationHistory WHERE id = :id LIMIT 1
        """,
    )
    suspend fun getById(id: String): OperationHistoryEntity?

    @Query(
        """
        SELECT id, kind, status, title, summary, moduleId, moduleName, sourceUri, sourceUrl,
               destinationPath, startedAt, completedAt, progress, requiresReboot, rebootCompletedAt,
               '' AS technicalLog, errorMessage, retryAction, rollbackAction, useShell, parentId,
               phase, rollbackArchivePath, previousVersion, targetVersion, inspectionSummary, origin,
               idempotencyKey, sourceOperationId, mutationStartedAt, reconciledAt
        FROM operationHistory ORDER BY startedAt DESC
        """,
    )
    suspend fun getAll(): List<OperationHistoryEntity>

    @Query(
        """
        SELECT id, kind, status, title, summary, moduleId, moduleName, sourceUri, sourceUrl,
               destinationPath, startedAt, completedAt, progress, requiresReboot, rebootCompletedAt,
               '' AS technicalLog, errorMessage, retryAction, rollbackAction, useShell, parentId,
               phase, rollbackArchivePath, previousVersion, targetVersion, inspectionSummary, origin,
               idempotencyKey, sourceOperationId, mutationStartedAt, reconciledAt
        FROM operationHistory WHERE idempotencyKey = :key LIMIT 1
        """,
    )
    suspend fun getByIdempotencyKey(key: String): OperationHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(value: OperationHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(value: OperationTechnicalLogEntity): Long

    @Query("SELECT technicalLog FROM operationTechnicalLog WHERE id = :id LIMIT 1")
    suspend fun getTechnicalLog(id: String): String?

    @Query("UPDATE operationHistory SET sourceUri = :sourceUri WHERE id = :id AND completedAt IS NULL")
    suspend fun updateSourceUri(id: String, sourceUri: String): Int

    @Query("UPDATE operationHistory SET retryAction = NULL WHERE id = :id AND completedAt IS NULL")
    suspend fun clearRetryAction(id: String): Int

    @Query("UPDATE operationHistory SET progress = :progress WHERE id = :id AND status IN ('QUEUED','RUNNING','WAITING_APPROVAL','CANCEL_REQUESTED')")
    suspend fun updateProgress(id: String, progress: Int?)

    @Query("UPDATE operationHistory SET phase = :phase, summary = :summary WHERE id = :id AND status IN ('QUEUED','RUNNING','WAITING_APPROVAL','CANCEL_REQUESTED')")
    suspend fun updatePhase(id: String, phase: String, summary: String)

    @Query(
        """
        UPDATE operationHistory
        SET kind = :kind,
            title = :title,
            moduleId = :moduleId,
            moduleName = :moduleName,
            targetVersion = :targetVersion
        WHERE id = :id AND status IN ('QUEUED','RUNNING','WAITING_APPROVAL','CANCEL_REQUESTED')
        """,
    )
    suspend fun updateIdentity(
        id: String,
        kind: String,
        title: String,
        moduleId: String?,
        moduleName: String?,
        targetVersion: String?,
    )

    @Query(
        """
        UPDATE operationHistory
        SET rollbackArchivePath = :path,
            previousVersion = :previousVersion,
            targetVersion = :targetVersion
        WHERE id = :id
        """,
    )
    suspend fun attachRollbackArchive(
        id: String,
        path: String?,
        previousVersion: String?,
        targetVersion: String?,
    )

    @Query("UPDATE operationHistory SET inspectionSummary = :summary WHERE id = :id")
    suspend fun updateInspectionSummary(id: String, summary: String?)

    @Query(
        """
        UPDATE operationHistory
        SET status = :toStatus,
            summary = COALESCE(:summary, summary),
            phase = COALESCE(:phase, phase)
        WHERE id = :id AND status IN (:fromStatuses) AND completedAt IS NULL
        """,
    )
    suspend fun transition(
        id: String,
        fromStatuses: List<String>,
        toStatus: String,
        summary: String?,
        phase: String?,
    ): Int

    @Query(
        """
        UPDATE operationHistory
        SET idempotencyKey = :key
        WHERE id = :id AND completedAt IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM operationHistory other
              WHERE other.idempotencyKey = :key AND other.id != :id
          )
        """,
    )
    suspend fun claimIdempotencyKey(id: String, key: String): Int

    @Query(
        """
        UPDATE operationHistory
        SET mutationStartedAt = COALESCE(mutationStartedAt, :startedAt),
            status = 'RUNNING'
        WHERE id = :id AND status IN ('QUEUED','RUNNING') AND completedAt IS NULL
        """,
    )
    suspend fun markMutationStarted(id: String, startedAt: Long): Int

    @Query(
        """
        UPDATE operationHistory
        SET status = :resolvedStatus,
            summary = :summary,
            errorMessage = CASE WHEN :resolvedStatus = 'FAILED' THEN :summary ELSE NULL END,
            phase = 'RESULT',
            reconciledAt = :at,
            idempotencyKey = NULL,
            retryAction = CASE WHEN :resolvedStatus = 'FAILED' AND :retryable = 1 THEN retryAction ELSE NULL END
        WHERE id = :id AND status = 'OUTCOME_UNKNOWN'
        """,
    )
    suspend fun resolveUnknown(
        id: String,
        resolvedStatus: String,
        summary: String,
        retryable: Boolean,
        at: Long,
    ): Int

    @Query(
        """
        UPDATE operationHistory
        SET status = :status,
            summary = :summary,
            completedAt = :completedAt,
            progress = NULL,
            requiresReboot = :requiresReboot,
            errorMessage = :errorMessage,
            rollbackAction = :rollbackAction,
            phase = 'RESULT',
            reconciledAt = :reconciledAt,
            idempotencyKey = CASE WHEN :status = 'OUTCOME_UNKNOWN' THEN idempotencyKey ELSE NULL END
        WHERE id = :id
          AND status IN (:fromStatuses)
          AND completedAt IS NULL
        """,
    )
    suspend fun finish(
        id: String,
        fromStatuses: List<String>,
        status: String,
        summary: String,
        completedAt: Long,
        requiresReboot: Boolean,
        errorMessage: String?,
        rollbackAction: String?,
        reconciledAt: Long?,
    ): Int

    @Query(
        """
        UPDATE operationTechnicalLog
        SET technicalLog = substr(
            CASE
                WHEN technicalLog = '' THEN :line
                ELSE technicalLog || char(10) || :line
            END,
            -1 * :maxChars
        )
        WHERE id = :id
        """,
    )
    suspend fun appendLog(id: String, line: String, maxChars: Int): Int

    @Query(
        """
        UPDATE operationHistory
        SET rebootCompletedAt = :completedAt
        WHERE requiresReboot = 1 AND rebootCompletedAt IS NULL
        """,
    )
    suspend fun markPendingRebootsCompleted(completedAt: Long)

    @Query("DELETE FROM operationTechnicalLog WHERE id = :id")
    suspend fun deleteLogById(id: String)

    @Query(
        """
        DELETE FROM operationHistory
        WHERE id = :id
          AND status NOT IN ('QUEUED','RUNNING','WAITING_APPROVAL','CANCEL_REQUESTED','OUTCOME_UNKNOWN')
          AND NOT (requiresReboot = 1 AND rebootCompletedAt IS NULL)
        """,
    )
    suspend fun deleteIfRemovable(id: String): Int


    @Query("DELETE FROM operationTechnicalLog WHERE id NOT IN (SELECT id FROM operationHistory)")
    suspend fun deleteOrphanLogs()

    @Query(
        """
        SELECT id, kind, status, title, summary, moduleId, moduleName, sourceUri, sourceUrl,
               destinationPath, startedAt, completedAt, progress, requiresReboot, rebootCompletedAt,
               '' AS technicalLog, errorMessage, retryAction, rollbackAction, useShell, parentId,
               phase, rollbackArchivePath, previousVersion, targetVersion, inspectionSummary, origin,
               idempotencyKey, sourceOperationId, mutationStartedAt, reconciledAt
        FROM operationHistory
        WHERE status IN (:statuses)
        """,
    )
    suspend fun getInterruptibleActive(statuses: List<String>): List<OperationHistoryEntity>

    @Query(
        """
        SELECT id, kind, status, title, summary, moduleId, moduleName, sourceUri, sourceUrl,
               destinationPath, startedAt, completedAt, progress, requiresReboot, rebootCompletedAt,
               '' AS technicalLog, errorMessage, retryAction, rollbackAction, useShell, parentId,
               phase, rollbackArchivePath, previousVersion, targetVersion, inspectionSummary, origin,
               idempotencyKey, sourceOperationId, mutationStartedAt, reconciledAt
        FROM operationHistory
        WHERE status IN (:statuses) AND startedAt < :cutoff
        """,
    )
    suspend fun getStaleInterruptibleActive(cutoff: Long, statuses: List<String>): List<OperationHistoryEntity>

}
