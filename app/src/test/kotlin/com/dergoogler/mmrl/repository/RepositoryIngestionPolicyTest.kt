package com.dergoogler.mmrl.repository

import com.dergoogler.mmrl.model.online.ModulesJson
import com.dergoogler.mmrl.model.online.ModulesJsonMetadata
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.TrackJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RepositoryIngestionPolicyTest {
    @Test
    fun `repository ingestion rejects duplicate canonical module ids`() {
        val json = ModulesJson(
            name = "Repo",
            metadata = ModulesJsonMetadata(version = ModulesJson.CURRENT_VERSION, timestamp = 1f),
            modules = listOf(module("Module.ID"), module("module.id")),
        )

        assertThrows(IllegalArgumentException::class.java) {
            RepositoryIngestionPolicy.validateModulesJson(json)
        }
    }

    @Test
    fun `repository url input canonicalizes https host and rejects credentials`() {
        assertEquals("https://example.com/repo/json/modules.json", normalizeRepositoryUrlInput("HTTPS://Example.COM//repo/json/modules.json"))
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRepositoryUrlInput("https://user:token@example.com/repo")
        }
    }

    @Test
    fun `repository ingestion keeps valid module generations intact`() {
        val json = ModulesJson(
            name = "Repo",
            metadata = ModulesJsonMetadata(version = ModulesJson.CURRENT_VERSION, timestamp = 1f),
            modules = listOf(module("one"), module("two")),
        )

        assertEquals(2, RepositoryIngestionPolicy.validateModulesJson(json).modules.size)
    }

    private fun module(id: String) =
        OnlineModule(
            id = id,
            name = id,
            version = "1",
            versionCode = 1,
            author = "Author",
            track = TrackJson(typeName = "ONLINE_JSON", source = "https://repo.invalid"),
            versions = emptyList(),
        )
}
