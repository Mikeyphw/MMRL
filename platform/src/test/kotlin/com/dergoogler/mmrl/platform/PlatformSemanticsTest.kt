package com.dergoogler.mmrl.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlatformSemanticsTest {
    @Test
    fun `unknown platform remains unknown and invalid`() {
        assertEquals(Platform.Unknown, Platform.from("not-a-manager"))
        assertFalse(Platform.Unknown.isValid)
    }

    @Test
    fun `hidden api defaults never include broad empty exemption`() {
        assertFalse(HiddenApiPolicy.DEFAULT_PREFIXES.any { it.isBlank() })
        assertFalse(HiddenApiPolicy.areNarrow(listOf("")))
        assertFalse(HiddenApiPolicy.areNarrow(listOf("L")))
        assertFalse(HiddenApiPolicy.areNarrow(listOf("Landroid/os/")))
    }
}
