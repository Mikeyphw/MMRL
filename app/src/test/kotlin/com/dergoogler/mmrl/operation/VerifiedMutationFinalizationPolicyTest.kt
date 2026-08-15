package com.dergoogler.mmrl.operation

import org.junit.Assert.assertEquals
import org.junit.Test

class VerifiedMutationFinalizationPolicyTest {
    @Test
    fun `verified backend with successful local finalization is success`() {
        assertEquals(
            VerifiedMutationFinalizationPolicy.Outcome.SUCCESS,
            VerifiedMutationFinalizationPolicy.classify(backendVerified = true, finalizationSucceeded = true),
        )
    }

    @Test
    fun `verified backend with failed local finalization is known applied failure not unknown`() {
        assertEquals(
            VerifiedMutationFinalizationPolicy.Outcome.KNOWN_APPLIED_FINALIZATION_FAILED,
            VerifiedMutationFinalizationPolicy.classify(backendVerified = true, finalizationSucceeded = false),
        )
    }

    @Test
    fun `unverified backend remains outcome unknown`() {
        assertEquals(
            VerifiedMutationFinalizationPolicy.Outcome.OUTCOME_UNKNOWN,
            VerifiedMutationFinalizationPolicy.classify(backendVerified = false, finalizationSucceeded = true),
        )
    }
}
