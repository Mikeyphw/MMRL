package com.dergoogler.mmrl.ash.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCallPolicyTest {
    @Test
    fun `reads may retry but mutations never retry blindly`() {
        assertEquals(2, RootCallPolicy.maxAttempts(RootCallKind.READ_ONLY))
        assertEquals(1, RootCallPolicy.maxAttempts(RootCallKind.MUTATION))
    }

    @Test
    fun `mutation transport failure is explicitly outcome unknown`() {
        val result = RootCallPolicy.transportFailure(RootCallKind.MUTATION, "binder died")
        assertTrue(result.contains("\"outcome\":\"UNKNOWN\""))
        assertTrue(result.contains("reconcile state before retrying"))
        assertFalse(RootCallPolicy.transportFailure(RootCallKind.READ_ONLY, "binder died").contains("UNKNOWN"))
    }
}
