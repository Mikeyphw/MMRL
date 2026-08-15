package com.dergoogler.mmrl.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SePolicyParserBehaviorTest {
    @Test
    fun `trailing garbage is rejected`() {
        assertTrue(SePolicyParser.parseSepolicy("allow a b file { read } trailing", strict = true).isFailure)
    }

    @Test
    fun `type state token requires a boundary`() {
        assertTrue(SePolicyParser.parseSepolicy("enforcingfoo test_type", strict = true).isFailure)
    }

    @Test
    fun `enforce spelling is normalized to enforcing`() {
        val statement = SePolicyParser.parseSepolicy("enforce test_type", strict = true).getOrThrow().single()
        val state = (statement as PolicyStatement.TypeStateStmt).state
        assertEquals("enforcing", state.op)
        assertEquals(2u, state.toAtomicStatements().single().subcmd)
    }

    @Test
    fun `enforcing spelling remains valid`() {
        assertTrue(SePolicyParser.parseSepolicy("enforcing test_type", strict = true).isSuccess)
    }

    @Test
    fun `native policy token capacity is enforced by utf8 bytes not characters`() {
        val token = "é".repeat(PolicyCommands.SEPOLICY_MAX_LEN / 2)
        assertTrue(token.length < PolicyCommands.SEPOLICY_MAX_LEN)
        assertThrows(IllegalArgumentException::class.java) { PolicyObject.fromString(token) }
    }
}
