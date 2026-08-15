package com.dergoogler.mmrl.operation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleMutationRetryPolicyTest {
    @Test fun `only clearly transient callback failures are retryable`() {
        assertTrue(ModuleMutationRetryPolicy.isRetryable("service unavailable, try again"))
        assertTrue(ModuleMutationRetryPolicy.isRetryable("Timed out waiting for root service"))
        assertFalse(ModuleMutationRetryPolicy.isRetryable("permission denied"))
        assertFalse(ModuleMutationRetryPolicy.isRetryable("invalid module id"))
        assertFalse(ModuleMutationRetryPolicy.isRetryable(null))
    }
}
