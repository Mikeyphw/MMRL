package com.dergoogler.mmrl.datastore.model

import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesJvmInitializationTest {
    @Test
    fun `defaults initialize without Android runtime`() {
        val preferences = UserPreferences()

        assertTrue(preferences.downloadPath.isNotBlank())
        assertTrue(preferences.dynamicFallbackPaletteId.isNotBlank())
    }
}
