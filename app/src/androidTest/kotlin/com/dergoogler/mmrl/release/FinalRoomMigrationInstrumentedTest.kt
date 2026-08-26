package com.dergoogler.mmrl.release

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
        assertEquals(22, AppDatabase.CURRENT_SCHEMA_VERSION)
        assertEquals(1, AppDatabase.supportedMigrationStarts.first)
        assertEquals(AppDatabase.CURRENT_SCHEMA_VERSION - 1, AppDatabase.supportedMigrationStarts.last)
        assertTrue(AppDatabase.supportedMigrationStarts.contains(15))
        assertTrue(AppDatabase.supportedMigrationStarts.contains(20))
        assertTrue(AppDatabase.supportedMigrationStarts.contains(21))
    }

    @Test
    fun localModuleSourceStrayIndexIsRemovedIdempotently() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "mmrl-room-index-repair-${System.nanoTime()}.db"
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(databaseName)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    )
                    .build(),
            )

        try {
            val db = helper.writableDatabase
            db.execSQL(
                """
                CREATE TABLE `localModules_source` (
                    `id` TEXT NOT NULL,
                    `repoUrl` TEXT NOT NULL,
                    `mode` TEXT NOT NULL,
                    `installedVersion` TEXT NOT NULL,
                    `installedVersionCode` INTEGER NOT NULL,
                    `sourceUrl` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX `index_localModules_source_id_repoUrl` ON `localModules_source` (`id`, `repoUrl`)",
            )

            AppDatabase.MIGRATION_21_22.migrate(db)
            assertFalse(hasStrayLocalModuleSourceIndex(db))

            // DROP INDEX IF EXISTS must remain safe if a repair is retried.
            AppDatabase.MIGRATION_21_22.migrate(db)
            assertFalse(hasStrayLocalModuleSourceIndex(db))
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun hasStrayLocalModuleSourceIndex(db: androidx.sqlite.db.SupportSQLiteDatabase): Boolean =
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_localModules_source_id_repoUrl'",
        ).use { cursor -> cursor.moveToFirst() }
}
