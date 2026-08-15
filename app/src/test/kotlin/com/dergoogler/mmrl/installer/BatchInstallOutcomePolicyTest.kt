package com.dergoogler.mmrl.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchInstallOutcomePolicyTest {
    @Test fun `all installed is one successful batch`() {
        val result = BatchInstallOutcomePolicy.classify(3, 3, false)
        assertEquals(BatchInstallOutcomePolicy.Kind.SUCCEEDED, result.kind)
        assertTrue(result.requiresReboot)
    }

    @Test fun `some installed then failure is explicit partial success`() {
        val result = BatchInstallOutcomePolicy.classify(3, 1, true)
        assertEquals(BatchInstallOutcomePolicy.Kind.PARTIAL_SUCCESS, result.kind)
        assertTrue(result.summary.contains("1 of 3"))
        assertTrue(result.requiresReboot)
    }

    @Test fun `zero installed is known batch failure`() {
        val result = BatchInstallOutcomePolicy.classify(3, 0, true)
        assertEquals(BatchInstallOutcomePolicy.Kind.FAILED, result.kind)
        assertFalse(result.requiresReboot)
    }
}
