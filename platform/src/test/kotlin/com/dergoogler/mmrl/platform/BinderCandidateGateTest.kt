package com.dergoogler.mmrl.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinderCandidateGateTest {
    @Test
    fun deathBeforePublicationRejectsDeadBinder() {
        val gate = BinderCandidateGate()
        var published = 0
        var cleaned = 0

        assertFalse(gate.lose { cleaned++ })
        assertFalse(gate.publish(eligible = { true }) { published++ })
        assertEquals(0, published)
        assertEquals(0, cleaned)
    }

    @Test
    fun cancellationBeforePublicationRejectsLateConnectionCallback() {
        val gate = BinderCandidateGate()
        var published = 0

        assertFalse(gate.cancel {})
        assertFalse(gate.publish(eligible = { true }) { published++ })
        assertEquals(0, published)
    }

    @Test
    fun deathAfterPublicationRunsGenerationCleanupOnce() {
        val gate = BinderCandidateGate()
        var cleaned = 0

        assertTrue(gate.publish(eligible = { true }) {})
        assertTrue(gate.lose { cleaned++ })
        assertFalse(gate.lose { cleaned++ })
        assertEquals(1, cleaned)
    }
}
