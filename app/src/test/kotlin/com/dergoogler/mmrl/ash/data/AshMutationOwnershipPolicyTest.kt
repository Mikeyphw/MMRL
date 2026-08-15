package com.dergoogler.mmrl.ash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AshMutationOwnershipPolicyTest {
    @Test fun `normal Ash mutation creates its own durable history identity`() {
        val ownership = AshMutationOwnershipPolicy.resolve(null, null, "ash:generated")
        assertTrue(ownership.createHistory)
        assertEquals("ash:generated", ownership.idempotencyKey)
        assertEquals(null, ownership.existingHistoryId)
    }

    @Test fun `Tasker Ash mutation reuses exact external history and idempotency identity`() {
        val ownership = AshMutationOwnershipPolicy.resolve("tasker-op", "external-key", "ash:generated")
        assertFalse(ownership.createHistory)
        assertEquals("tasker-op", ownership.existingHistoryId)
        assertEquals("external-key", ownership.idempotencyKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `partial external ownership is rejected`() {
        AshMutationOwnershipPolicy.resolve("tasker-op", null, "ash:generated")
    }
}
