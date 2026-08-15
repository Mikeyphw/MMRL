package com.dergoogler.mmrl.installer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagingOwnershipTest {
    @Test fun `caller owns staging until privileged coordinator accepts handoff`() {
        val ownership = StagingOwnership()
        assertTrue(ownership.callerMayRelease())
        ownership.handoffToCoordinator()
        assertFalse(ownership.callerMayRelease())
    }

    @Test fun `handoff is one way so caller cancellation cannot reclaim live coordinator input`() {
        val ownership = StagingOwnership()
        ownership.handoffToCoordinator()
        ownership.handoffToCoordinator()
        assertFalse(ownership.callerMayRelease())
    }
}
