package com.dergoogler.mmrl.platform.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModIdTest {
    @Test
    fun equalsStringHonorsIgnoreCaseWithoutJavaStringCast() {
        val modId = ModId("KernelSU")

        assertFalse(modId.equals("kernelsu"))
        assertTrue(modId.equals("kernelsu", ignoreCase = true))
        assertTrue(modId.equals("KernelSU", ignoreCase = true))
        assertFalse(modId.equals(null, ignoreCase = true))
    }
}
