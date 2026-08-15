package com.dergoogler.mmrl.platform.ksu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KsuInputPolicyTest {
    @Test
    fun `utf8 byte length not utf16 count controls native buffers`() {
        assertTrue(KsuInputPolicy.validPackage("a".repeat(255)))
        assertFalse(KsuInputPolicy.validPackage("a".repeat(256)))
        assertFalse(KsuInputPolicy.validPackage("é".repeat(128)))
    }

    @Test
    fun `profile group and selinux capacities are bounded before JNI`() {
        assertTrue(KsuInputPolicy.validProfile(Profile("pkg", groups = List(32) { it }, context = "u:r:su:s0")))
        assertFalse(KsuInputPolicy.validProfile(Profile("pkg", groups = List(33) { it })))
        assertFalse(KsuInputPolicy.validProfile(Profile("pkg", context = "x".repeat(64))))
        assertFalse(KsuInputPolicy.validProfile(Profile("pkg", capabilities = listOf(64))))
    }
}
