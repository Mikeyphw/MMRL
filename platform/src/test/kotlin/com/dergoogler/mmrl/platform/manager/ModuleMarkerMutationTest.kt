package com.dergoogler.mmrl.platform.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleMarkerMutationTest {
    @Test
    fun absentStateIsIdempotentAndFailedDeleteIsFailure() {
        var calls = 0
        assertTrue(ModuleMarkerMutation.ensureAbsent(false) { calls++; false })
        assertTrue(calls == 0)

        assertFalse(ModuleMarkerMutation.ensureAbsent(true) { calls++; false })
        assertTrue(calls == 1)
        assertTrue(ModuleMarkerMutation.ensureAbsent(true) { true })
    }

    @Test
    fun presentStateIsIdempotentAndFailedCreateIsFailure() {
        var calls = 0
        assertTrue(ModuleMarkerMutation.ensurePresent(true) { calls++; false })
        assertTrue(calls == 0)

        assertFalse(ModuleMarkerMutation.ensurePresent(false) { calls++; false })
        assertTrue(calls == 1)
        assertTrue(ModuleMarkerMutation.ensurePresent(false) { true })
    }
}
