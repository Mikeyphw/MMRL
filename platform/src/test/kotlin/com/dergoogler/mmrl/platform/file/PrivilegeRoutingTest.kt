package com.dergoogler.mmrl.platform.file

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivilegeRoutingTest {
    @Test
    fun `selected root mode fails closed when root service is unavailable`() {
        assertEquals(
            PrivilegeRouting.Backend.UNAVAILABLE,
            PrivilegeRouting.select(privilegedPlatformSelected = true, rootServiceReady = false),
        )
    }

    @Test
    fun `non privileged mode may use local filesystem`() {
        assertEquals(
            PrivilegeRouting.Backend.LOCAL,
            PrivilegeRouting.select(privilegedPlatformSelected = false, rootServiceReady = false),
        )
    }
}
