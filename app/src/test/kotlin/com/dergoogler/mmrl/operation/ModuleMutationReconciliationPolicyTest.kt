package com.dergoogler.mmrl.operation

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleMutationReconciliationPolicyTest {
    @Test fun `callback success needs matching callback identity and backend state`() {
        assertEquals(
            ModuleMutationReconciliationPolicy.Outcome.SUCCESS,
            ModuleMutationReconciliationPolicy.classify(true, true, true, false),
        )
    }

    @Test fun `success callback for another module is outcome unknown`() {
        assertEquals(
            ModuleMutationReconciliationPolicy.Outcome.OUTCOME_UNKNOWN,
            ModuleMutationReconciliationPolicy.classify(true, false, true, false),
        )
    }

    @Test fun `failure callback is known failure only when backend stayed unchanged`() {
        assertEquals(
            ModuleMutationReconciliationPolicy.Outcome.FAILURE,
            ModuleMutationReconciliationPolicy.classify(false, true, false, true),
        )
    }

    @Test fun `callback and backend disagreement is outcome unknown`() {
        assertEquals(
            ModuleMutationReconciliationPolicy.Outcome.OUTCOME_UNKNOWN,
            ModuleMutationReconciliationPolicy.classify(false, true, true, false),
        )
    }
}
