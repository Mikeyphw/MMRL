package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationStatus

/** Truthful rollback authority: archive-backed rollback is available only while its immutable archive exists. */
object RollbackAvailabilityPolicy {
    fun isAvailable(
        status: OperationStatus,
        rollbackAction: OperationAction?,
        rollbackArchivePath: String?,
        rollbackArchiveExists: Boolean,
    ): Boolean {
        if (rollbackAction == null || status !in OperationStatus.entries.filter { it.name in OperationStatus.terminalNames }) {
            return false
        }
        if (status == OperationStatus.OUTCOME_UNKNOWN) return false
        return if (rollbackAction == OperationAction.INSTALL) {
            !rollbackArchivePath.isNullOrBlank() && rollbackArchiveExists
        } else {
            true
        }
    }
}
