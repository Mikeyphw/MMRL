package com.dergoogler.mmrl.platform.ksu

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileWireCompatibilityTest {
    @Test
    fun rootFlagsSurviveOrdinaryProfileCopies() {
        val flags = 0x1234_5678_9abc_def0L
        val original = Profile(
            name = "com.example.root",
            currentUid = 10_123,
            allowSu = true,
            rootFlags = flags,
        )

        val edited = original.copy(gid = 2000, context = "u:r:su:s0")

        assertEquals(flags, edited.rootFlags)
    }

    @Test
    fun legacyDefaultHasNoInventedRootFlags() {
        assertEquals(0L, Profile("com.example.legacy").rootFlags)
    }
}
