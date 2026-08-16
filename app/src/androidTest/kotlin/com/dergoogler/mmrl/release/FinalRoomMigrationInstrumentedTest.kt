package com.dergoogler.mmrl.release

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dergoogler.mmrl.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinalRoomMigrationInstrumentedTest {
    @Test
    fun appDatabaseDeclaresNonDestructiveMigrationCoverage() {
        assertFalse("release builds must not silently use destructive Room fallback", AppDatabase.destructiveFallbackEnabled())
        assertEquals(21, AppDatabase.CURRENT_SCHEMA_VERSION)
        assertEquals(1, AppDatabase.supportedMigrationStarts.first)
        assertEquals(AppDatabase.CURRENT_SCHEMA_VERSION - 1, AppDatabase.supportedMigrationStarts.last)
        assertTrue(AppDatabase.supportedMigrationStarts.contains(15))
        assertTrue(AppDatabase.supportedMigrationStarts.contains(20))
    }
}
