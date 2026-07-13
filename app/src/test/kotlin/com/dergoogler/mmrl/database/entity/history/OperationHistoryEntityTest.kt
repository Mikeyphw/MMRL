package com.dergoogler.mmrl.database.entity.history

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
