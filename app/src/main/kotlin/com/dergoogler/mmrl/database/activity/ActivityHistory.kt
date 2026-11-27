package com.dergoogler.mmrl.database.activity

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.withTransaction
import com.dergoogler.mmrl.database.AppDatabase
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

/** Stable code-only diagnostics. Never capture URLs, shell output or exception messages. */
@Entity(tableName = "activity_diagnostics")
data class ActivityDiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val operationId: String,
    val atMillis: Long,
    val code: String,
)

/** Only these predefined machine codes may be persisted. */
enum class ActivityDiagnosticCode {
    STARTED, PROGRESS, COMPLETED, FAILED, INTERRUPTED, RECONCILIATION_REQUIRED,
}

@Dao
interface ActivityHistoryDao {
    @Query("SELECT * FROM activity_operations ORDER BY updatedAtMillis DESC, id DESC LIMIT 200")
    fun observeRecent(): Flow<List<ActivityOperationEntity>>

    /** Room invalidates both flows after durable inserts/updates. Only a page is loaded. */
    @Query("SELECT COUNT(*) FROM activity_operations")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM activity_operations ORDER BY updatedAtMillis DESC, id DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<ActivityOperationEntity>>

    @Query("SELECT * FROM activity_operations WHERE id = :id LIMIT 1")
    suspend fun find(id: String): ActivityOperationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun store(entity: ActivityOperationEntity)

    @Insert
    suspend fun appendDiagnostic(entity: ActivityDiagnosticEntity)

    @Query("SELECT * FROM activity_diagnostics ORDER BY atMillis DESC, sequence DESC LIMIT 600")
    fun observeDiagnostics(): Flow<List<ActivityDiagnosticEntity>>

    @Query("DELETE FROM activity_diagnostics WHERE sequence NOT IN (SELECT sequence FROM activity_diagnostics ORDER BY atMillis DESC, sequence DESC LIMIT 600)")
    suspend fun pruneDiagnostics()

    /** Read unfinished rows once per app process, under the writer transaction. */
    @Query("SELECT * FROM activity_operations WHERE status IN ('RUNNING','QUEUED') ORDER BY id LIMIT :limit")
    suspend fun unfinished(limit: Int): List<ActivityOperationEntity>
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

/** Explicit 16 -> 17 migration. Keeps existing operations and existing module data. */
object ActivityHistoryLogMigration : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS activity_diagnostics (
            sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            operationId TEXT NOT NULL, atMillis INTEGER NOT NULL,
            code TEXT NOT NULL)""")
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
class ActivityHistoryRepository @Inject constructor(
    private val dao: ActivityHistoryDao,
    private val database: AppDatabase,
) {
    private val mutex = Mutex()
    val recent: Flow<List<ActivityOperationEntity>> = dao.observeRecent()
    val totalCount: Flow<Int> = dao.observeTotalCount()

    /** Bounded read-only query; no implicit deletion of older operations. */
    fun page(index: Int, size: Int = 50): Flow<List<ActivityOperationEntity>> {
        require(index >= 0 && size in 1..100 && index <= Int.MAX_VALUE / size) {
            "Invalid Activity history page"
        }
        return dao.observePage(size, index * size)
    }

    // This Hilt singleton has one writer in the app process. Recovery and begin
    // share the same mutex, so startup scheduling and clock changes cannot make
    // a new operation look like a previous-process operation.
    private var recoveryComplete = false

    suspend fun recoverAtProcessStart(): Int = mutex.withLock { recoverLocked() }

    private suspend fun recoverLocked(): Int {
        if (recoveryComplete) return 0
        val count = database.withTransaction {
            var recovered = 0
            while (true) {
                val rows = dao.unfinished(100)
                if (rows.isEmpty()) break
                for (row in rows) {
                    val record = checkNotNull(row.toRecord()) { "Invalid unfinished Activity record" }
                    val updated = ActivityRecoveryPolicy.recover(record, System.currentTimeMillis())
                    dao.store(updated.toEntity())
                    val code = if (updated.status == OperationStatus.RECONCILIATION_REQUIRED)
                        ActivityDiagnosticCode.RECONCILIATION_REQUIRED else ActivityDiagnosticCode.INTERRUPTED
                    dao.appendDiagnostic(ActivityDiagnosticEntity(
                        operationId = updated.id.value, atMillis = updated.updatedAtMillis, code = code.name))
                    recovered++
                }
            }
            dao.pruneDiagnostics()
            recovered
        }
        recoveryComplete = true
        return count
    }

    val diagnostics: Flow<List<ActivityDiagnosticEntity>> = dao.observeDiagnostics()

    /** Synchronous with respect to privileged dispatch: callers await this durable write first. */
    suspend fun beginOperation(kind: OperationKind, parentId: String? = null): String = mutex.withLock {
        recoverLocked()
        val id = OperationId.parse("activity:${UUID.randomUUID()}")!!
        val parent = parentId?.let { OperationId.parse(it) ?: error("Invalid parent operation ID") }
        // No orphaned or fabricated parent relationship may be persisted.
        if (parent != null) checkNotNull(dao.find(parent.value)?.toRecord()) {
            "Parent operation missing or invalid"
        }
        val time = System.currentTimeMillis().coerceAtLeast(1L)
        val initial = checkNotNull(OperationRecord.create(id, kind, OperationOrigin.USER, time, parent))
        val phase = if (kind == OperationKind.DOWNLOAD) OperationPhase.PREPARING
            else OperationPhase.APPLYING
        val running = checkNotNull(initial.reduce(checkNotNull(OperationEvent.of(
            id, OperationStatus.QUEUED, OperationStatus.RUNNING, phase, time + 1))))
        database.withTransaction {
            dao.store(running.toEntity())
            dao.appendDiagnostic(ActivityDiagnosticEntity(
                operationId = id.value, atMillis = time, code = ActivityDiagnosticCode.STARTED.name))
            dao.pruneDiagnostics()
        }
        id.value
    }

    suspend fun beginDownload(): String = beginOperation(OperationKind.DOWNLOAD)

    suspend fun progress(id: String, percentage: Int) = transition(
        id, OperationStatus.RUNNING, OperationPhase.TRANSFERRING,
        percentage.coerceIn(0, 100), null,
    )

    suspend fun success(id: String, rebootRequired: Boolean = false) = transition(
        id, OperationStatus.SUCCEEDED, OperationPhase.FINISHED, null, null,
        rebootRequired = rebootRequired,
    )

    suspend fun failed(id: String, code: String = "DOWNLOAD_FAILED") = transition(
        id, OperationStatus.FAILED, OperationPhase.FINISHED, null,
        // Allowlisted error codes only; no exception text is ever persisted.
        checkNotNull(OperationFailure.parse(when (code) {
            "DOWNLOAD_FAILED", "INSTALL_FAILED", "MUTATION_FAILED", "ACTION_FAILED",
            "OPERATION_FAILED" -> code
            else -> "OPERATION_FAILED"
        })),
    )

    /** Explicit acknowledgement records unknown outcome, not success, retry or rollback. */
    suspend fun acknowledgeUnknown(id: String) = mutex.withLock {
        val old = checkNotNull(dao.find(id)?.toRecord()) { "Unknown Activity record" }
        check(old.status == OperationStatus.RECONCILIATION_REQUIRED) {
            "Activity record does not need reconciliation"
        }
        val at = maxOf(System.currentTimeMillis(), old.updatedAtMillis + 1)
        val event = checkNotNull(OperationEvent.of(old.id, old.status,
            OperationStatus.INTERRUPTED, OperationPhase.FINISHED, at,
            reconciliationReviewed = true))
        val updated = checkNotNull(old.reduce(event))
        database.withTransaction {
            dao.store(updated.toEntity())
            dao.appendDiagnostic(ActivityDiagnosticEntity(
                operationId = id, atMillis = at, code = ActivityDiagnosticCode.INTERRUPTED.name))
            dao.pruneDiagnostics()
        }
    }

    /** A backend acknowledgement without independent post-state proof is NOT a success. */
    suspend fun needsReconciliation(id: String) = transition(
        id, OperationStatus.RECONCILIATION_REQUIRED,
        OperationPhase.WAITING_FOR_USER, null, null,
    )

    private suspend fun transition(id: String, next: OperationStatus,
                                   phase: OperationPhase, progress: Int?, failure: OperationFailure?,
                                   rebootRequired: Boolean = false) {
        mutex.withLock {
            // A persistence request must either commit the new state or fail. Returning
            // Unit without a write would make runCatching(...).isSuccess lie to callers.
            val old = checkNotNull(dao.find(id)?.toRecord()) {
                "Activity operation missing or invalid"
            }
            check(old.status == OperationStatus.RUNNING) {
                "Activity operation not running"
            }
            val time = maxOf(System.currentTimeMillis(), old.updatedAtMillis + 1)
            val event = checkNotNull(OperationEvent.of(old.id, old.status, next, phase, time,
                progressPercent = progress, failure = failure,
                rebootRequired = rebootRequired)) {
                "Invalid Activity event"
            }
            val updated = checkNotNull(old.reduce(event)) {
                "Activity transition rejected"
            }
            database.withTransaction {
                dao.store(updated.toEntity())
                if (next != OperationStatus.RUNNING || updated.progressPercent == 100) {
                    val code = when (next) {
                        OperationStatus.SUCCEEDED -> ActivityDiagnosticCode.COMPLETED
                        OperationStatus.FAILED -> ActivityDiagnosticCode.FAILED
                        OperationStatus.RECONCILIATION_REQUIRED -> ActivityDiagnosticCode.RECONCILIATION_REQUIRED
                        else -> ActivityDiagnosticCode.PROGRESS
                    }
                    dao.appendDiagnostic(ActivityDiagnosticEntity(
                        operationId = updated.id.value, atMillis = time, code = code.name))
                    dao.pruneDiagnostics()
                }
            }
        }
    }
}
