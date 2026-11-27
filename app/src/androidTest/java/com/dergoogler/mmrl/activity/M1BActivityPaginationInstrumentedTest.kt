package com.dergoogler.mmrl.activity

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dergoogler.mmrl.database.activity.ActivityOperationEntity
import com.dergoogler.mmrl.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Room gate; test-owned in-memory database, no root or user database. */
@RunWith(AndroidJUnit4::class)
class M1BActivityPaginationInstrumentedTest {
    @Test fun olderOperationsRemainQueryableWithoutUnboundedPageLoads() = runBlocking {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        try {
            val dao = db.activityHistoryDao()
            repeat(253) { i ->
                dao.store(ActivityOperationEntity(
                    id = "test:$i", kind = "DOWNLOAD", origin = "USER",
                    status = "SUCCEEDED", phase = "FINISHED", createdAtMillis = 1,
                    updatedAtMillis = (i + 2).toLong(), progressPercent = 100,
                    parentId = null, failureCode = null, rebootRequired = false, schemaVersion = 1
                ))
            }
            assertEquals(253, dao.observeTotalCount().first())
            assertEquals(50, dao.observePage(50, 0).first().size)
            assertEquals(50, dao.observePage(50, 200).first().size)
            val oldest = dao.observePage(50, 250).first()
            assertEquals(3, oldest.size)
            assertTrue(oldest.any { it.id == "test:0" })
            assertEquals("test:252", dao.observePage(1, 0).first().single().id)
        } finally { db.close() }
    }
}
