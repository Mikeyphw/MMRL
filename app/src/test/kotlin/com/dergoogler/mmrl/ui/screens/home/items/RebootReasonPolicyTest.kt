package com.dergoogler.mmrl.ui.screens.home.items

import org.junit.Assert.assertEquals
import org.junit.Test

class RebootReasonPolicyTest {
    @Test
    fun `confirmation preserves selected reboot reason`() {
        assertEquals("recovery", RebootReasonPolicy.confirmed("recovery"))
        assertEquals("bootloader", RebootReasonPolicy.confirmed("bootloader"))
    }
}
