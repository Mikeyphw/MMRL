package com.dergoogler.mmrl.platform.manager

import com.dergoogler.mmrl.platform.model.ShellResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellFailurePolicyTest {
    @Test
    fun `stderr is preferred over stdout for manager command failure`() {
        val result = ShellResult(false, listOf("generic output"), listOf("specific failure"), 7)
        assertEquals("specific failure", ShellFailurePolicy.message(result))
    }

    @Test
    fun `exit code is reported when command produced no diagnostics`() {
        val result = ShellResult(false, emptyList(), emptyList(), 23)
        assertEquals("Command exited with code 23", ShellFailurePolicy.message(result))
    }
}
