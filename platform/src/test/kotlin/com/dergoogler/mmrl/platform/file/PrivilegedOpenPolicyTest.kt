package com.dergoogler.mmrl.platform.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedOpenPolicyTest {
    @Test
    fun `read open remains read and adds close-on-exec plus no-follow`() {
        val plan = PrivilegedOpenPolicy.plan(PrivilegedOpenPolicy.O_RDONLY, 0)
        assertEquals(PrivilegedPathPolicy.Access.READ, plan.access)
        assertTrue(plan.flags and PrivilegedOpenPolicy.O_CLOEXEC != 0)
        assertTrue(plan.flags and PrivilegedOpenPolicy.O_NOFOLLOW != 0)
    }

    @Test
    fun `creating write is classified as mutation and preserves requested mode`() {
        val requested = PrivilegedOpenPolicy.O_WRONLY or PrivilegedOpenPolicy.O_CREAT or PrivilegedOpenPolicy.O_TRUNC
        val plan = PrivilegedOpenPolicy.plan(requested, 0x1A4) // 0644
        assertEquals(PrivilegedPathPolicy.Access.MUTATE, plan.access)
        assertTrue(plan.flags and requested == requested)
        assertEquals(0x1A4, plan.mode)
    }

    @Test
    fun `append and read-write opens cannot bypass mutation classification`() {
        assertEquals(
            PrivilegedPathPolicy.Access.MUTATE,
            PrivilegedOpenPolicy.plan(PrivilegedOpenPolicy.O_APPEND or PrivilegedOpenPolicy.O_WRONLY, 0).access,
        )
        assertEquals(
            PrivilegedPathPolicy.Access.MUTATE,
            PrivilegedOpenPolicy.plan(PrivilegedOpenPolicy.O_RDWR, 0).access,
        )
    }
}
