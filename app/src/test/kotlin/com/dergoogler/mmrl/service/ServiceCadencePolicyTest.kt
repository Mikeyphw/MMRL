package com.dergoogler.mmrl.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceCadencePolicyTest {
    @Test
    fun `service cadences are bounded`() {
        assertEquals(1L, ServiceCadencePolicy.clampHours(0L))
        assertEquals(168L, ServiceCadencePolicy.clampHours(Long.MAX_VALUE))
        assertEquals(6L, ServiceCadencePolicy.clampHours(6L))
    }
}
