package com.dergoogler.mmrl.platform.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSignalGateTest {
    @Test
    fun `only first terminal signal is accepted`() {
        val gate = TerminalSignalGate()
        assertTrue(gate.claim())
        assertFalse(gate.claim())
        assertFalse(gate.claim())
    }
}
