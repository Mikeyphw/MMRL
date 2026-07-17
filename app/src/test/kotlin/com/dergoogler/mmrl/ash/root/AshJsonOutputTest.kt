package com.dergoogler.mmrl.ash.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AshJsonOutputTest {
    @Test
    fun `extracts pretty json after shell warning`() {
        val output = """
            warning: optional state file is absent
            {
              "ok": true,
              "message": "ready"
            }
        """.trimIndent()

        assertEquals(
            """
            {
              "ok": true,
              "message": "ready"
            }
            """.trimIndent(),
            AshJsonOutput.extractObject(output),
        )
    }

    @Test
    fun `keeps braces inside strings balanced`() {
        val output = """{"ok":true,"message":"value {still} valid"}"""

        assertEquals(output, AshJsonOutput.extractObject(output))
    }

    @Test
    fun `returns the last complete object`() {
        val output = """
            {"debug":true}
            intermediate output
            {"ok":true}
        """.trimIndent()

        assertEquals("""{"ok":true}""", AshJsonOutput.extractObject(output))
    }

    @Test
    fun `ignores incomplete object`() {
        assertNull(AshJsonOutput.extractObject("warning\n{\"ok\":true"))
    }
}
