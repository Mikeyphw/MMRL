package com.dergoogler.mmrl.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SuperUserInventoryPolicyTest {
    @Test
    fun `headless privileged packages are retained alongside visible apps`() {
        assertEquals(
            listOf("visible.app", "headless.root"),
            SuperUserInventoryPolicy.mergeVisibleWithPrivileged(listOf("visible.app"), listOf("headless.root")),
        )
    }
}
