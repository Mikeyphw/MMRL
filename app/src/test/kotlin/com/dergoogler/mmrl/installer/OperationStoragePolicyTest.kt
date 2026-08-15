package com.dergoogler.mmrl.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationStoragePolicyTest {
    @Test fun `operation ids are confined to one safe path component`() {
        assertEquals("op_.._child", OperationStoragePolicy.safeOperationId("op/../child"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank operation id is rejected`() { OperationStoragePolicy.safeOperationId("") }

    @Test fun `quota check fails closed on overflow and excess`() {
        assertTrue(OperationStoragePolicy.canFit(40, 2, 42))
        assertFalse(OperationStoragePolicy.canFit(41, 2, 42))
        assertFalse(OperationStoragePolicy.canFit(Long.MAX_VALUE, 1, Long.MAX_VALUE))
    }

    @Test fun `age retention expires only older staging`() {
        assertFalse(OperationStoragePolicy.isExpired(900, 1_000, 100))
        assertTrue(OperationStoragePolicy.isExpired(899, 1_000, 100))
    }
    @Test fun `active staging lease cannot be pruned while unrelated staging can`() {
        val leased = setOf("active-op")
        assertFalse(OperationStoragePolicy.canPruneOperation("active-op", leased))
        assertTrue(OperationStoragePolicy.canPruneOperation("finished-op", leased))
    }

    @Test fun `streaming byte counter enforces actual emitted limit`() {
        var total = 0L
        total = OperationStoragePolicy.addWithinLimit(total, 6, 10)
        assertEquals(6L, total)
        total = OperationStoragePolicy.addWithinLimit(total, 4, 10)
        assertEquals(10L, total)
        val failure = runCatching { OperationStoragePolicy.addWithinLimit(total, 1, 10) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

}
