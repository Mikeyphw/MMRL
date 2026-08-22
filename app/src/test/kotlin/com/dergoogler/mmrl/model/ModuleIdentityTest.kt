package com.dergoogler.mmrl.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleIdentityTest {
    @Test
    fun `identity ignores case and surrounding whitespace`() {
        assertTrue(ModuleIdentity.matches("  Tricky_Store ", "tricky_store"))
    }

    @Test
    fun `different module ids never match`() {
        assertFalse(ModuleIdentity.matches("module_a", "module_b"))
    }

    @Test
    fun `ordinary module identity has no hidden alias expansion`() {
        assertEquals("example.module", ModuleIdentity.canonical("  EXAMPLE.MODULE "))
        assertEquals(setOf("example.module"), ModuleIdentity.aliasesFor(" Example.Module "))
    }
}
