package com.dergoogler.mmrl.activity

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dergoogler.mmrl.database.activity.ActivityHistoryLogMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Run the actual 16→17 migration against an Android SQLite v16 fixture.
 * The fixture stays in this test's private database; no user data or privileged APIs.
 * A full production Room schema upgrade is a separate independent acceptance gate.
 */
@RunWith(AndroidJUnit4::class)
class M1BActivityMigrationInstrumentedTest {
    @Test fun version16ActivitySurvivesVersion17LogMigration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "m1b-migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("""CREATE TABLE activity_operations (
                id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, origin TEXT NOT NULL,
                status TEXT NOT NULL, phase TEXT NOT NULL,
                createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL,
                progressPercent INTEGER, parentId TEXT, failureCode TEXT,
                rebootRequired INTEGER NOT NULL, schemaVersion INTEGER NOT NULL)""")
            old.execSQL("""INSERT INTO activity_operations VALUES (
                'activity:legacy','DOWNLOAD','USER','INTERRUPTED','FINISHED',
                100,200,NULL,NULL,NULL,0,1)""")
            old.version = 16
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(17) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        error("Unexpected empty database; v16 fixture must exist")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        assertEquals(16, oldVersion)
                        assertEquals(17, newVersion)
                        ActivityHistoryLogMigration.migrate(db)
                    }
                }).build()
        )
        try {
            val upgraded = helper.writableDatabase
            upgraded.query("SELECT id,status,updatedAtMillis FROM activity_operations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("activity:legacy", cursor.getString(0))
                assertEquals("INTERRUPTED", cursor.getString(1))
                assertEquals(200L, cursor.getLong(2))
                assertTrue(!cursor.moveToNext())
            }
            upgraded.query("SELECT COUNT(*) FROM activity_diagnostics").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
