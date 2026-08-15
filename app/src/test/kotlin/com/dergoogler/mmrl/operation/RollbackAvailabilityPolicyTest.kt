package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RollbackAvailabilityPolicyTest {
    @Test fun `archive rollback is unavailable after its managed archive is pruned`() {
        assertFalse(
            RollbackAvailabilityPolicy.isAvailable(
                status = OperationStatus.FAILED,
                rollbackAction = OperationAction.INSTALL,
                rollbackArchivePath = "/data/rollback.zip",
                rollbackArchiveExists = false,
            ),
        )
        assertTrue(
            RollbackAvailabilityPolicy.isAvailable(
                status = OperationStatus.FAILED,
                rollbackAction = OperationAction.INSTALL,
                rollbackArchivePath = "/data/rollback.zip",
                rollbackArchiveExists = true,
            ),
        )
    }

    @Test fun `unknown outcome never advertises rollback even when an archive exists`() {
        assertFalse(
            RollbackAvailabilityPolicy.isAvailable(
                status = OperationStatus.OUTCOME_UNKNOWN,
                rollbackAction = OperationAction.INSTALL,
                rollbackArchivePath = "/data/rollback.zip",
                rollbackArchiveExists = true,
            ),
        )
    }

    @Test fun `known terminal state rollback does not require an archive for state reversal`() {
        assertTrue(
            RollbackAvailabilityPolicy.isAvailable(
                status = OperationStatus.SUCCEEDED,
                rollbackAction = OperationAction.DISABLE,
                rollbackArchivePath = null,
                rollbackArchiveExists = false,
            ),
        )
        assertFalse(
            RollbackAvailabilityPolicy.isAvailable(
                status = OperationStatus.RUNNING,
                rollbackAction = OperationAction.DISABLE,
                rollbackArchivePath = null,
                rollbackArchiveExists = false,
            ),
        )
    }
}
