package com.dergoogler.mmrl.operation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneShotOperationGateTest {
    @Test fun `configuration recreation cannot start a second orchestration from the retained view model`() {
        val gate = OneShotOperationGate()
        assertTrue(gate.tryStart())
        assertTrue(gate.hasStarted())
        assertFalse(gate.tryStart())
    }
}
