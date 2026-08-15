package com.dergoogler.mmrl.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformReadinessPolicyTest {
    @Test
    fun `live binder is not ready without independently detected root`() {
        assertFalse(PlatformReadinessPolicy.isReady(true, true, Platform.Unknown))
        assertFalse(PlatformReadinessPolicy.isReady(true, true, Platform.NonRoot))
    }

    @Test
    fun `authorized live detected root is ready`() {
        assertTrue(PlatformReadinessPolicy.isReady(true, true, Platform.KernelSU))
    }

    @Test
    fun `dead binder is never ready`() {
        assertFalse(PlatformReadinessPolicy.isReady(true, false, Platform.Magisk))
    }
}
