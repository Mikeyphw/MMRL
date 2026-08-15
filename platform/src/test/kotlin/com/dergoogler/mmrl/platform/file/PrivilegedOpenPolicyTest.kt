package com.dergoogler.mmrl.platform.file

import android.system.OsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedOpenPolicyTest {
    @Test
    fun `read open remains read and adds close-on-exec plus no-follow`() {
        val plan = PrivilegedOpenPolicy.plan(OsConstants.O_RDONLY, 0)
        assertEquals(PrivilegedPathPolicy.Access.READ, plan.access)
        assertTrue(plan.flags and OsConstants.O_CLOEXEC != 0)
        assertTrue(plan.flags and OsConstants.O_NOFOLLOW != 0)
    }

    @Test
    fun `creating write is classified as mutation and preserves requested mode`() {
        val requested = OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_TRUNC
        val plan = PrivilegedOpenPolicy.plan(requested, 0x1A4) // 0644
        assertEquals(PrivilegedPathPolicy.Access.MUTATE, plan.access)
        assertTrue(plan.flags and requested == requested)
        assertEquals(0x1A4, plan.mode)
    }

    @Test
    fun `append and read-write opens cannot bypass mutation classification`() {
        assertEquals(
            PrivilegedPathPolicy.Access.MUTATE,
            PrivilegedOpenPolicy.plan(OsConstants.O_APPEND or OsConstants.O_WRONLY, 0).access,
        )
        assertEquals(
            PrivilegedPathPolicy.Access.MUTATE,
            PrivilegedOpenPolicy.plan(OsConstants.O_RDWR, 0).access,
        )
    }
}
