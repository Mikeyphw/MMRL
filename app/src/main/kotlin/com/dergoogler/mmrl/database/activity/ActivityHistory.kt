package com.dergoogler.mmrl.database.activity

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.mikeyphw.mmrl.shared.OperationEvent
import io.github.mikeyphw.mmrl.shared.OperationFailure
import io.github.mikeyphw.mmrl.shared.OperationId
import io.github.mikeyphw.mmrl.shared.OperationKind
import io.github.mikeyphw.mmrl.shared.OperationOrigin
import io.github.mikeyphw.mmrl.shared.OperationPhase
import io.github.mikeyphw.mmrl.shared.OperationRecord
import io.github.mikeyphw.mmrl.shared.OperationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Only normalized identifiers and state. Never persist names, URLs, tokens or stack traces. */
@Entity(tableName = "activity_operations")
data class ActivityOperationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val origin: String,
    val status: String,
    val phase: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val progressPercent: Int?,
    val parentId: String?,
    val failureCode: String?,
    val rebootRequired: Boolean,
    val schemaVersion: Int,
)

@Dao
interface ActivityHistoryDao {
    @Query("SELECT * FROM activity_operations ORDER BY updatedAtMillis DESC, id DESC LIMIT 200")
    fun observeRecent(): Flow<List<ActivityOperationEntity>>

    @Query("SELECT * FROM activity_operations WHERE id = :id LIMIT 1")
    suspend fun find(id: String): ActivityOperationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun store(entity: ActivityOperationEntity)

    /** Process startup only: no success inference and no silent action replay. */
    @Query("""UPDATE activity_operations SET status = 'INTERRUPTED', phase = 'FINISHED',
        updatedAtMillis = :cutoff WHERE status IN ('RUNNING','QUEUED')
        AND updatedAtMillis < :cutoff""")
    suspend fun interruptUnfinished(cutoff: Long): Int
}

/** Explicit 15 -> 16 migration retains existing repositories, modules and user data. */
object ActivityHistoryMigration : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `activity_operations` (
            `id` TEXT NOT NULL, `kind` TEXT NOT NULL, `origin` TEXT NOT NULL,
            `status` TEXT NOT NULL, `phase` TEXT NOT NULL,
            `createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL,
            `progressPercent` INTEGER, `parentId` TEXT, `failureCode` TEXT,
            `rebootRequired` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL,
            PRIMARY KEY(`id`))""")
    }
}

private fun OperationRecord.toEntity() = ActivityOperationEntity(
    id.value, kind.name, origin.name, status.name, phase.name, createdAtMillis,
    updatedAtMillis, progressPercent, parentId?.value, failure?.code,
    rebootRequired, schemaVersion,
)

private fun ActivityOperationEntity.toRecord(): OperationRecord? = runCatching {
    val parsedId = OperationId.parse(id) ?: return@runCatching null
    val parent = parentId?.let { OperationId.parse(it) ?: return@runCatching null }
    val error = failureCode?.let { OperationFailure.parse(it) ?: return@runCatching null }
    OperationRecord.restore(
        parsedId, OperationKind.valueOf(kind), OperationOrigin.valueOf(origin),
        OperationStatus.valueOf(status), OperationPhase.valueOf(phase),
        createdAtMillis, updatedAtMillis, progressPercent, parent,
        error, rebootRequired, schemaVersion,
    )
}.getOrNull()

/** Hilt-owned, serialized transition writer. Device operations never originate here. */
@Singleton
class ActivityHistoryRepository @Inject constructor(private val dao: ActivityHistoryDao) {
    private val mutex = Mutex()
    val recent: Flow<List<ActivityOperationEntity>> = dao.observeRecent()

    suspend fun recoverAtProcessStart(cutoff: Long): Int = mutex.withLock {
        require(cutoff > 0)
        dao.interruptUnfinished(cutoff)
    }

    suspend fun beginDownload(): String = mutex.withLock {
        val id = OperationId.parse("activity:${UUID.randomUUID()}")!!
        val time = System.currentTimeMillis().coerceAtLeast(1L)
        val initial = checkNotNull(OperationRecord.create(id, OperationKind.DOWNLOAD,
            OperationOrigin.USER, time))
        dao.store(initial.toEntity())
        val begin = checkNotNull(OperationEvent.of(id, OperationStatus.QUEUED,
            OperationStatus.RUNNING, OperationPhase.PREPARING, time + 1))
        val running = checkNotNull(initial.reduce(begin))
        dao.store(running.toEntity())
        id.value
    }

    suspend fun progress(id: String, percentage: Int) = transition(
        id, OperationStatus.RUNNING, OperationPhase.TRANSFERRING,
        percentage.coerceIn(0, 100), null,
    )

    suspend fun success(id: String) = transition(
        id, OperationStatus.SUCCEEDED, OperationPhase.FINISHED, null, null,
    )

    suspend fun failed(id: String) = transition(
        id, OperationStatus.FAILED, OperationPhase.FINISHED, null,
        OperationFailure.parse("DOWNLOAD_FAILED"),
    )

    private suspend fun transition(id: String, next: OperationStatus,
                                   phase: OperationPhase, progress: Int?, failure: OperationFailure?) {
        mutex.withLock {
            val old = dao.find(id)?.toRecord() ?: return@withLock
            if (old.status != OperationStatus.RUNNING) return@withLock
            val time = maxOf(System.currentTimeMillis(), old.updatedAtMillis + 1)
            val event = OperationEvent.of(old.id, old.status, next, phase, time,
                progressPercent = progress, failure = failure) ?: return@withLock
            val updated = old.reduce(event) ?: return@withLock
            dao.store(updated.toEntity())
        }
    }
}
