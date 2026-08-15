package com.dergoogler.mmrl.platform.ksu

import com.dergoogler.mmrl.platform.Platform
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KsuNativeQueryPolicyTest {
    @Test
    fun `authorization is only attempted for a live KernelSU family platform`() {
        assertTrue(KsuNativeQueryPolicy.canAuthorize(true, Platform.KernelSU))
        assertTrue(KsuNativeQueryPolicy.canAuthorize(true, Platform.SukiSU))
        assertFalse(KsuNativeQueryPolicy.canAuthorize(false, Platform.KernelSU))
        assertFalse(KsuNativeQueryPolicy.canAuthorize(true, Platform.Magisk))
        assertFalse(KsuNativeQueryPolicy.canAuthorize(true, Platform.Unknown))
    }

    @Test
    fun `feature queries require authorization for the exact detected platform`() {
        assertFalse(KsuNativeQueryPolicy.canQuery(true, Platform.KernelSU, 7L, null, -1L))
        assertTrue(KsuNativeQueryPolicy.canQuery(true, Platform.KernelSU, 7L, Platform.KernelSU, 7L))
        assertFalse(KsuNativeQueryPolicy.canQuery(true, Platform.KernelSU, 8L, Platform.KernelSU, 7L))
        assertFalse(KsuNativeQueryPolicy.canQuery(true, Platform.KernelSU, 7L, Platform.KsuNext, 7L))
        assertFalse(KsuNativeQueryPolicy.canQuery(false, Platform.KernelSU, 7L, Platform.KernelSU, 7L))
        assertFalse(KsuNativeQueryPolicy.canQuery(true, Platform.Magisk, 7L, Platform.Magisk, 7L))
    }

}
