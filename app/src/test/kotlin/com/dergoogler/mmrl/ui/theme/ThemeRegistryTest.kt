package com.dergoogler.mmrl.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRegistryTest {
    @Test
    fun stableIdsRemainUnique() {
        val ids = ThemeRegistry.builtIns.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ThemeRegistry.DEFAULT_ID in ids)
        assertTrue(ThemeRegistry.DRACULA_ID in ids)
        assertTrue(ThemeRegistry.SWEET_DARK_ID in ids)
        assertTrue(ThemeRegistry.NORD_ID in ids)
        assertTrue(ThemeRegistry.MONOKAI_ID in ids)
    }

    @Test
    fun legacyIntegerIdsHaveStableMigrationTargets() {
        assertEquals("legacy_pourville", ThemeRegistry.migrateLegacyId(0))
        assertEquals("legacy_wild_roses", ThemeRegistry.migrateLegacyId(6))
        assertEquals(ThemeRegistry.DEFAULT_ID, ThemeRegistry.migrateLegacyId(7))
        assertEquals(ThemeRegistry.DEFAULT_ID, ThemeRegistry.migrateLegacyId(-1))
        assertEquals(ThemeRegistry.DEFAULT_ID, ThemeRegistry.migrateLegacyId(999))
    }
}
