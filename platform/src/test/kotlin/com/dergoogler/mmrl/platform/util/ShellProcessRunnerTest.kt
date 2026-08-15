package com.dergoogler.mmrl.platform.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellProcessRunnerTest {
    @Test(timeout = 10_000)
    fun `large stdout and stderr are drained without deadlock`() {
        val capture = ShellProcessRunner.run(
            "i=0; while [ $i -lt 4000 ]; do echo out-$i; echo err-$i >&2; i=$((i+1)); done",
        )
        assertEquals(0, capture.exitCode)
        assertEquals(4000, capture.stdout.size)
        assertEquals(4000, capture.stderr.size)
        assertTrue(capture.stderr.last().startsWith("err-"))
    }
}
