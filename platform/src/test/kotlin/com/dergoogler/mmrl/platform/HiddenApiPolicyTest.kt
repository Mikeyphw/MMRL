package com.dergoogler.mmrl.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenApiPolicyTest {
    @Test
    fun `default exemptions are the exact approved framework surfaces`() {
        assertTrue(HiddenApiPolicy.areNarrow(HiddenApiPolicy.DEFAULT_PREFIXES.toList()))
    }

    @Test
    fun `empty broad or unknown exemptions are rejected`() {
        assertFalse(HiddenApiPolicy.areNarrow(emptyList()))
        assertFalse(HiddenApiPolicy.areNarrow(listOf("")))
        assertFalse(HiddenApiPolicy.areNarrow(listOf("L")))
        assertFalse(HiddenApiPolicy.areNarrow(listOf("Landroid/")))
        assertFalse(HiddenApiPolicy.areNarrow(listOf("Landroid/os/Binder;")))
    }

    @Test
    fun `approved strict subsets remain allowed`() {
        assertTrue(HiddenApiPolicy.areNarrow(listOf("Landroid/os/ServiceManager;")))
    }
}
