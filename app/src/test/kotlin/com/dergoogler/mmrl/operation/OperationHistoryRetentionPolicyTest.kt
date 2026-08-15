package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationHistoryRetentionPolicyTest {
    @Test fun `new admission evicts oldest safe resolved row but preserves unknown active and pending reboot evidence`() {
        val entries = listOf(
            entry("success-old", OperationStatus.SUCCEEDED, startedAt = 1),
            entry("unknown", OperationStatus.OUTCOME_UNKNOWN, startedAt = 2),
            entry("active", OperationStatus.RUNNING, startedAt = 3),
            entry("pending", OperationStatus.SUCCEEDED, startedAt = 4, pendingReboot = true),
        )
        assertEquals(
            listOf("success-old"),
            OperationHistoryRetentionPolicy.evictionsForInsert(entries, maxEntries = 4),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `admission fails closed when protected evidence consumes the entire hard cap`() {
        val entries = listOf(
            entry("unknown", OperationStatus.OUTCOME_UNKNOWN, 1),
            entry("active", OperationStatus.RUNNING, 2),
            entry("pending", OperationStatus.SUCCEEDED, 3, pendingReboot = true),
        )
        OperationHistoryRetentionPolicy.evictionsForInsert(entries, maxEntries = 3)
    }

    @Test fun `clear completed removes only safe resolved rows`() {
        val entries = listOf(
            entry("success", OperationStatus.SUCCEEDED, 1),
            entry("failed", OperationStatus.FAILED, 2),
            entry("cancelled", OperationStatus.CANCELLED, 3),
            entry("unknown", OperationStatus.OUTCOME_UNKNOWN, 4),
            entry("pending", OperationStatus.SUCCEEDED, 5, pendingReboot = true),
        )
        assertEquals(listOf("success", "failed", "cancelled"), OperationHistoryRetentionPolicy.clearable(entries))
        assertFalse(OperationHistoryRetentionPolicy.isSafelyEvictable(entries[3]))
        assertFalse(OperationHistoryRetentionPolicy.isSafelyEvictable(entries[4]))
        assertTrue(OperationHistoryRetentionPolicy.isSafelyEvictable(entries[0]))
    }

    private fun entry(
        id: String,
        status: OperationStatus,
        startedAt: Long,
        pendingReboot: Boolean = false,
    ) = OperationHistoryEntity(
        id = id,
        kind = OperationKind.INSTALL.name,
        status = status.name,
        title = id,
        summary = id,
        startedAt = startedAt,
        requiresReboot = pendingReboot,
        rebootCompletedAt = null,
    )
}
