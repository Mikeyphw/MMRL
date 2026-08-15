package com.dergoogler.mmrl.database.entity.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationHistoryEntityTest {
    @Test
    fun `successful reboot operation remains pending until boot is recorded`() {
        val entry =
            entry(
                status = OperationStatus.SUCCEEDED,
                requiresReboot = true,
                rebootCompletedAt = null,
            )

        assertTrue(entry.isPendingReboot)
        assertFalse(entry.isRunning)
        assertFalse(entry.isFailed)
    }

    @Test
    fun `completed reboot clears pending state`() {
        val entry =
            entry(
                status = OperationStatus.SUCCEEDED,
                requiresReboot = true,
                rebootCompletedAt = 2L,
            )

        assertFalse(entry.isPendingReboot)
    }

    @Test
    fun `retry and rollback capabilities follow persisted action contracts`() {
        val entry =
            entry(
                status = OperationStatus.FAILED,
                retryAction = OperationAction.ENABLE.name,
                rollbackAction = OperationAction.DISABLE.name,
            )

        assertTrue(entry.isFailed)
        assertTrue(entry.canRetry)
        assertTrue(entry.canRollback)
    }

    @Test
    fun `retained update archive exposes manual rollback`() {
        val entry =
            OperationHistoryEntity(
                id = "update-id",
                kind = OperationKind.UPDATE.name,
                status = OperationStatus.FAILED.name,
                title = "Module",
                summary = "Update failed",
                startedAt = 1L,
                rollbackAction = OperationAction.INSTALL.name,
                rollbackArchivePath = "/data/user/0/app/files/update-rollbacks/module-1.zip",
                previousVersion = "1.0",
                targetVersion = "2.0",
                phase = OperationPhase.RESULT.name,
            )

        assertTrue(entry.canRollback)
        assertTrue(entry.rollbackArchivePath!!.endsWith(".zip"))
        assertTrue(entry.previousVersion == "1.0")
    }

    @Test
    fun `outcome unknown blocks retry and rollback until it is classified into a real terminal state`() {
        val unresolved = OperationHistoryEntity(
            id = "unknown",
            kind = OperationKind.UPDATE.name,
            status = OperationStatus.OUTCOME_UNKNOWN.name,
            title = "Module",
            summary = "Unknown",
            startedAt = 1L,
            retryAction = OperationAction.INSTALL.name,
            rollbackAction = OperationAction.INSTALL.name,
            reconciledAt = null,
        )
        assertFalse(unresolved.canRetry)
        assertFalse(unresolved.canRollback)
        val merelyStamped = unresolved.copy(reconciledAt = 2L)
        assertFalse(merelyStamped.canRetry)
        assertFalse(merelyStamped.canRollback)
    }

    private fun entry(
        status: OperationStatus,
        requiresReboot: Boolean = false,
        rebootCompletedAt: Long? = null,
        retryAction: String? = null,
        rollbackAction: String? = null,
    ) = OperationHistoryEntity(
        id = "history-id",
        kind = OperationKind.ENABLE.name,
        status = status.name,
        title = "Module",
        summary = "Summary",
        startedAt = 1L,
        requiresReboot = requiresReboot,
        rebootCompletedAt = rebootCompletedAt,
        retryAction = retryAction,
        rollbackAction = rollbackAction,
    )
    @Test
    fun `success and cancellation never expose retry even when legacy retry action remains`() {
        assertFalse(entry(status = OperationStatus.SUCCEEDED, retryAction = OperationAction.ENABLE.name).canRetry)
        assertFalse(entry(status = OperationStatus.CANCELLED, retryAction = OperationAction.ENABLE.name).canRetry)
    }

    @Test
    fun `rollback is terminal only and unknown rollback requires classification`() {
        assertFalse(entry(status = OperationStatus.RUNNING, rollbackAction = OperationAction.DISABLE.name).canRollback)
        val unknown = entry(
            status = OperationStatus.OUTCOME_UNKNOWN,
            rollbackAction = OperationAction.DISABLE.name,
        )
        assertFalse(unknown.canRollback)
        assertFalse(unknown.copy(reconciledAt = 9L).canRollback)
    }

    @Test
    fun `active and pending reboot rows cannot be deleted`() {
        assertFalse(entry(status = OperationStatus.RUNNING).canDelete)
        assertFalse(entry(status = OperationStatus.WAITING_APPROVAL).canDelete)
        assertFalse(entry(status = OperationStatus.SUCCEEDED, requiresReboot = true).canDelete)
        assertFalse(entry(status = OperationStatus.OUTCOME_UNKNOWN).canDelete)
        assertTrue(entry(status = OperationStatus.SUCCEEDED).canDelete)
        assertTrue(entry(status = OperationStatus.FAILED).canDelete)
    }

}
