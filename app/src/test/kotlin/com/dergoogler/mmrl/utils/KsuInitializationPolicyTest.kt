package com.dergoogler.mmrl.utils

import com.dergoogler.mmrl.platform.Platform
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KsuInitializationPolicyTest {
    @Test
    fun `authorization is attempted only after successful KSU platform initialization`() {
        assertTrue(KsuInitializationPolicy.shouldAttemptManagerAuthorization(true, Platform.KernelSU))
        assertTrue(KsuInitializationPolicy.shouldAttemptManagerAuthorization(true, Platform.KsuNext))
        assertFalse(KsuInitializationPolicy.shouldAttemptManagerAuthorization(false, Platform.KernelSU))
        assertFalse(KsuInitializationPolicy.shouldAttemptManagerAuthorization(true, Platform.Magisk))
        assertFalse(KsuInitializationPolicy.shouldAttemptManagerAuthorization(true, Platform.Unknown))
    }
}
