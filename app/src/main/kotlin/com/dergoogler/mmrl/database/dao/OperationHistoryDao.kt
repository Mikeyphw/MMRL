package com.dergoogler.mmrl.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationHistoryDao {
    @Query("SELECT * FROM operationHistory ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<OperationHistoryEntity>>

    @Query("SELECT COUNT(*) FROM operationHistory WHERE requiresReboot = 1 AND rebootCompletedAt IS NULL")
    fun observePendingRebootCount(): Flow<Int>

    @Query("SELECT * FROM operationHistory WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): OperationHistoryEntity?

    @Query("SELECT * FROM operationHistory ORDER BY startedAt DESC")
    suspend fun getAll(): List<OperationHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(value: OperationHistoryEntity)

    @Query("UPDATE operationHistory SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int?)

    @Query("UPDATE operationHistory SET phase = :phase, summary = :summary WHERE id = :id")
    suspend fun updatePhase(id: String, phase: String, summary: String)

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
        SET status = :status,
            summary = :summary,
            completedAt = :completedAt,
            progress = NULL,
            requiresReboot = :requiresReboot,
            errorMessage = :errorMessage,
            rollbackAction = :rollbackAction,
            phase = 'RESULT'
        WHERE id = :id
        """,
    )
    suspend fun finish(
        id: String,
        status: String,
        summary: String,
        completedAt: Long,
        requiresReboot: Boolean,
        errorMessage: String?,
        rollbackAction: String?,
    )

    @Query(
        """
        UPDATE operationHistory
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
    suspend fun appendLog(id: String, line: String, maxChars: Int)

    @Query(
        """
        UPDATE operationHistory
        SET rebootCompletedAt = :completedAt
        WHERE requiresReboot = 1 AND rebootCompletedAt IS NULL
        """,
    )
    suspend fun markPendingRebootsCompleted(completedAt: Long)

    @Query("DELETE FROM operationHistory WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM operationHistory WHERE status != 'RUNNING'")
    suspend fun deleteCompleted()

    @Query(
        """
        UPDATE operationHistory
        SET status = 'FAILED',
            summary = :summary,
            completedAt = :completedAt,
            errorMessage = :summary,
            progress = NULL
        WHERE status = 'RUNNING'
        """,
    )
    suspend fun markRunningInterrupted(
        summary: String,
        completedAt: Long,
    )

    @Query(
        """
        UPDATE operationHistory
        SET status = 'FAILED',
            summary = :summary,
            completedAt = :completedAt,
            errorMessage = :summary,
            progress = NULL
        WHERE status = 'RUNNING' AND startedAt < :cutoff
        """,
    )
    suspend fun markStaleRunningInterrupted(
        cutoff: Long,
        summary: String,
        completedAt: Long,
    )

    @Query(
        """
        DELETE FROM operationHistory
        WHERE status != 'RUNNING'
          AND id NOT IN (
              SELECT id FROM operationHistory ORDER BY startedAt DESC LIMIT :keep
          )
        """,
    )
    suspend fun prune(keep: Int)
}
