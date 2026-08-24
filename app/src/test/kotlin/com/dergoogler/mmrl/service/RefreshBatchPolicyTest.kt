package com.dergoogler.mmrl.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshBatchPolicyTest {
    @Test
    fun `partial refresh preserves previously observed notification keys`() {
        assertEquals(
            setOf("old:10", "new:20"),
            RefreshBatchPolicy.mergeObservedKeys(
                previous = setOf("old:10"),
                current = setOf("new:20"),
                refreshComplete = false,
            ),
        )
    }

    @Test
    fun `complete refresh may retire stale notification keys`() {
        assertEquals(
            setOf("new:20"),
            RefreshBatchPolicy.mergeObservedKeys(
                previous = setOf("old:10"),
                current = setOf("new:20"),
                refreshComplete = true,
            ),
        )
    }

    @Test
    fun `retry is bounded`() {
        assertTrue(RefreshBatchPolicy.shouldRetry(1, 0, 2))
        assertFalse(RefreshBatchPolicy.shouldRetry(1, 2, 2))
        assertFalse(RefreshBatchPolicy.shouldRetry(0, 0, 2))
    }
}
