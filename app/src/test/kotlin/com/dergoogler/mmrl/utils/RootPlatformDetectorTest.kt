package com.dergoogler.mmrl.utils

import com.dergoogler.mmrl.platform.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class RootPlatformDetectorTest {
    @Test
    fun `specific forks are detected before generic KernelSU`() {
        assertEquals(Platform.KsuNext, RootPlatformDetector.fromSuVersion("KernelSU Next 1.2"))
        assertEquals(Platform.SukiSU, RootPlatformDetector.fromSuVersion("SukiSU Ultra"))
        assertEquals(Platform.RKSU, RootPlatformDetector.fromSuVersion("RKSU KernelSU"))
        assertEquals(Platform.MKSU, RootPlatformDetector.fromSuVersion("MKSU KernelSU"))
    }

    @Test
    fun `preference cannot manufacture detection`() {
        assertEquals(Platform.Unknown, RootPlatformDetector.fromSuVersion(null))
        assertEquals(Platform.Unknown, RootPlatformDetector.fromSuVersion("su 1.0"))
    }
}
