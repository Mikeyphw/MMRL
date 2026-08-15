package com.dergoogler.mmrl.ash.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootBinderCandidateGateTest {
    @Test
    fun `death before publication prevents dead proxy from being published`() {
        val gate = RootBinderCandidateGate()
        var published = 0
        var cleaned = 0
        assertFalse(gate.lose { cleaned++ })
        assertFalse(gate.publish({ true }) { published++ })
        assertEquals(0, published)
        assertEquals(0, cleaned)
    }

    @Test
    fun `cancellation before publication prevents late callback resurrection`() {
        val gate = RootBinderCandidateGate()
        var published = false
        gate.cancel { error("nothing was published") }
        assertFalse(gate.publish({ true }) { published = true })
        assertFalse(published)
    }

    @Test
    fun `death after publication cleans exactly once`() {
        val gate = RootBinderCandidateGate()
        var cleaned = 0
        assertTrue(gate.publish({ true }) {})
        assertTrue(gate.lose { cleaned++ })
        assertFalse(gate.lose { cleaned++ })
        assertEquals(1, cleaned)
    }
}
