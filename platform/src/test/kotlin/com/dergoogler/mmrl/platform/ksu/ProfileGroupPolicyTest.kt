package com.dergoogler.mmrl.platform.ksu

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileGroupPolicyTest {
    @Test
    fun `empty group selection stays empty`() {
        assertEquals(emptyList<Int>(), ProfileGroupPolicy.merge(emptyList(), emptyList()))
    }

    @Test
    fun `unknown gids survive editing known choices`() {
        assertEquals(listOf(4242, 0, 2000), ProfileGroupPolicy.merge(listOf(4242), listOf(0, 2000)))
    }
}
