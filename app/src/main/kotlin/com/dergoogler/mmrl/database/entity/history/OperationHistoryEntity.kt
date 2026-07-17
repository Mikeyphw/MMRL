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
) {
    val isRunning: Boolean get() = status == OperationStatus.RUNNING.name
    val isFailed: Boolean get() = status == OperationStatus.FAILED.name
    val isPendingReboot: Boolean get() = requiresReboot && rebootCompletedAt == null
    val canRetry: Boolean get() = !retryAction.isNullOrBlank()
    val canRollback: Boolean get() = !rollbackAction.isNullOrBlank()
}

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
    ASH_RESCUE,
    ASH_RESTORATION,
    ASH_SETTINGS,
    ASH_DIAGNOSTICS,
}

enum class OperationStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
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
    RESULT,
    ROLLBACK,
    CHECK_UPDATES,
    EXPORT_LOG,
    PREPARE_INSTALL,
}
