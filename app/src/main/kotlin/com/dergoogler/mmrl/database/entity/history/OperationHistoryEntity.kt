package com.dergoogler.mmrl.database.entity.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "operationHistory",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["status"]),
        Index(value = ["moduleId"]),
        Index(value = ["requiresReboot", "rebootCompletedAt"]),
        Index(value = ["idempotencyKey"], unique = true),
    ],
)
data class OperationHistoryEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val status: String,
    val title: String,
    val summary: String,
    val moduleId: String? = null,
    val moduleName: String? = null,
    val sourceUri: String? = null,
    val sourceUrl: String? = null,
    val destinationPath: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
    val progress: Int? = null,
    val requiresReboot: Boolean = false,
    val rebootCompletedAt: Long? = null,
    /** Compatibility field only. New logs live in operationTechnicalLog and list queries synthesize empty text. */
    val technicalLog: String = "",
    val errorMessage: String? = null,
    val retryAction: String? = null,
    val rollbackAction: String? = null,
    val useShell: Boolean = false,
    val parentId: String? = null,
    val phase: String? = null,
    val rollbackArchivePath: String? = null,
    val previousVersion: String? = null,
    val targetVersion: String? = null,
    val inspectionSummary: String? = null,
    val origin: String? = null,
    val idempotencyKey: String? = null,
    val sourceOperationId: String? = null,
    val mutationStartedAt: Long? = null,
    val reconciledAt: Long? = null,
) {
    val isRunning: Boolean get() = status in OperationStatus.activeNames
    val isFailed: Boolean get() = status == OperationStatus.FAILED.name || status == OperationStatus.OUTCOME_UNKNOWN.name
    val isPendingReboot: Boolean get() = requiresReboot && rebootCompletedAt == null
    val canRetry: Boolean get() =
        !retryAction.isNullOrBlank() && status == OperationStatus.FAILED.name
    val canRollback: Boolean get() =
        !rollbackAction.isNullOrBlank() &&
            status in OperationStatus.terminalNames &&
            status != OperationStatus.OUTCOME_UNKNOWN.name
    val canDelete: Boolean get() = !isRunning && !isPendingReboot && status != OperationStatus.OUTCOME_UNKNOWN.name
}

@Entity(tableName = "operationTechnicalLog")
data class OperationTechnicalLogEntity(
    @PrimaryKey val id: String,
    val technicalLog: String = "",
)

enum class OperationKind {
    DOWNLOAD,
    INSTALL,
    UPDATE,
    ENABLE,
    DISABLE,
    REMOVE,
    RESTORE,
    MODULE_ACTION,
    ROLLBACK,
    CHECK_UPDATES,
    EXPORT_LOG,
    PREPARE_INSTALL,
}

enum class OperationStatus {
    QUEUED,
    RUNNING,
    WAITING_APPROVAL,
    CANCEL_REQUESTED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    OUTCOME_UNKNOWN;

    companion object {
        val activeNames = setOf(QUEUED.name, RUNNING.name, WAITING_APPROVAL.name, CANCEL_REQUESTED.name)
        val terminalNames = setOf(SUCCEEDED.name, FAILED.name, CANCELLED.name, OUTCOME_UNKNOWN.name)
    }
}

enum class OperationAction {
    DOWNLOAD,
    INSTALL,
    ENABLE,
    DISABLE,
    REMOVE,
    RUN_ACTION,
    CANCEL_DOWNLOAD,
}

enum class OperationPhase {
    REVIEW,
    APPROVAL,
    DOWNLOAD,
    VERIFY,
    INSPECT,
    STAGE,
    INSTALL,
    RECONCILE,
    RESULT,
    ROLLBACK,
    CHECK_UPDATES,
    EXPORT_LOG,
    PREPARE_INSTALL,
}
