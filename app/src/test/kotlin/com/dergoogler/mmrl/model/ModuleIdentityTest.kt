package com.dergoogler.mmrl.model

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
    fun `ashrexcue aliases match bundled module identity`() {
        assertTrue(ModuleIdentity.isAshReXcue("AshLooper"))
        assertTrue(ModuleIdentity.isAshReXcue("AshReXcue_Bootloop_Protector"))
        assertTrue(ModuleIdentity.isAshReXcue("ashrexcue-bootloop-protector"))
    }

    @Test
    fun `ashrexcue aliases are canonical installed identities`() {
        assertTrue(ModuleIdentity.matches("ashlooper", "ashrexcue"))
        assertTrue(ModuleIdentity.matches("AshReXcue Bootloop Protector", "ashrexcue"))
        assertTrue(ModuleIdentity.aliasesFor("ashrexcue").contains("ashlooper"))
    }

}
