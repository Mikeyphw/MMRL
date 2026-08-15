package com.dergoogler.mmrl.installer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallReconciliationPolicyTest {
    @Test fun `backend identity and reviewed version must both match`() {
        assertTrue(InstallReconciliationPolicy.matchesExpected("module", 7, "module", 7))
        assertFalse(InstallReconciliationPolicy.matchesExpected("other", 7, "module", 7))
        assertFalse(InstallReconciliationPolicy.matchesExpected("module", 6, "module", 7))
    }

    @Test fun `missing backend record never proves install success`() {
        assertFalse(InstallReconciliationPolicy.matchesExpected(null, null, "module", 7))
    }

    @Test fun `version may be unconstrained only when caller has no reviewed version`() {
        assertTrue(InstallReconciliationPolicy.matchesExpected("module", 42, "module", null))
    }
}
