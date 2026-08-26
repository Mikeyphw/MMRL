package com.dergoogler.mmrl.database

import com.dergoogler.mmrl.database.entity.VersionItemEntity
import com.dergoogler.mmrl.database.entity.online.ModuleManagerEntity
import com.dergoogler.mmrl.database.entity.online.OnlineModuleEntity
import com.dergoogler.mmrl.model.online.Blacklist
import com.dergoogler.mmrl.model.online.ModuleManager
import com.dergoogler.mmrl.model.online.ModuleManagerSolution
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.TrackJson
import com.dergoogler.mmrl.model.online.VersionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistenceRoundTripPolicyTest {
    @Test
    fun `room database exposes explicit migrations for every supported old version and no destructive fallback`() {
        assertEquals(22, AppDatabase.CURRENT_SCHEMA_VERSION)
        assertEquals((1 until 22).toList(), AppDatabase.supportedMigrationStarts.toList())
        assertEquals(false, AppDatabase.destructiveFallbackEnabled())
    }

    @Test
    fun `version row preserves size provenance and integer timestamp`() {
        val item = VersionItem(
            timestamp = 1_724_000_000f,
            version = "1.2.3",
            versionCode = 123,
            zipUrl = "https://example.invalid/module.zip",
            size = 42,
            changelog = "release notes",
            sourceProvenance = "mode=nightly; run=99; artifact=7; commit=abc",
        )

        val entity = VersionItemEntity(item, id = "mod", repoUrl = "https://repo.invalid")
        val roundTrip = entity.toItem()

        assertEquals(1_724_000_000L, entity.timestamp)
        assertEquals(42, roundTrip.size)
        assertEquals("mode=nightly; run=99; artifact=7; commit=abc", roundTrip.sourceProvenance)
    }

    @Test
    fun `ksunext manager compatibility survives database conversion`() {
        val manager = ModuleManager(ksunext = ModuleManagerSolution(min = 12000, arch = listOf("arm64-v8a")))

        val roundTrip = ModuleManagerEntity(manager).toManager()

        assertEquals(12000, roundTrip.ksunext?.min)
        assertEquals(listOf("arm64-v8a"), roundTrip.ksunext?.arch)
    }

    @Test
    fun `online rows do not persist stale blacklist snapshots`() {
        val module = OnlineModule(
            id = "module.id",
            name = "Module",
            version = "1",
            versionCode = 1,
            author = "Author",
            track = TrackJson(typeName = "ONLINE_JSON", source = "https://repo.invalid"),
            versions = emptyList(),
            blacklist = Blacklist.EMPTY,
        )

        val row = OnlineModuleEntity(module, repoUrl = "https://repo.invalid", blacklist = Blacklist.EMPTY)

        assertNull(row.toModule().blacklist)
    }
}
