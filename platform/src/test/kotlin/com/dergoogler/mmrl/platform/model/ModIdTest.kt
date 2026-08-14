package com.dergoogler.mmrl.platform.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModIdTest {
    @Test
    fun acceptsDocumentedModuleIdGrammar() {
        listOf("a_module", "a.module", "module-101", "A1", "x_y.z-9").forEach { value ->
            assertTrue(value, ModId.isValidId(value))
            assertEquals(value, ModId(value).id)
        }
    }

    @Test
    fun rejectsTraversalShellSyntaxAndInvalidLeadingCharacters() {
        listOf(
            "../evil",
            "/absolute",
            "a module",
            "1_module",
            "-module",
            "a;id",
            "a$(id)",
            "a`id`",
            "a/b",
            "a\nb",
        ).forEach { value ->
            assertNull(value, ModId.parseOrNull(value))
        }
    }
    @Test(expected = IllegalArgumentException::class)
    fun constructorRejectsAlternateRootAuthority() {
        ModId("safe_module", "/tmp")
    }

    @Test
    fun parserRejectsAlternateRootAuthority() {
        assertNull(ModId.parseOrNull("safe_module", "/tmp"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySentinelCannotBecomeFilesystemOrRootAuthority() {
        ModId.EMPTY.requireOperational()
    }

    @Test
    fun canonicalIdIsOperational() {
        assertEquals("safe_module", ModId("safe_module").requireOperational().id)
    }

}
