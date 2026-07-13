package com.dergoogler.mmrl.model.sort

import com.dergoogler.mmrl.datastore.model.Option
import com.dergoogler.mmrl.datastore.model.RepositoryMenu
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.TrackJson
import com.dergoogler.mmrl.model.online.VersionItem
import com.dergoogler.mmrl.model.state.OnlineState
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineModuleSortTest {
    @Test
    fun `size sorting uses module metadata instead of preserving source order`() {
        val source =
            listOf(
                entry(id = "large", name = "Large", size = 900),
                entry(id = "small", name = "Small", size = 100),
                entry(id = "medium", name = "Medium", size = 500),
            )

        val ascending =
            source.sortedForRepository(
                RepositoryMenu(
                    option = Option.Size,
                    descending = false,
                    pinInstalled = false,
                    pinUpdatable = false,
                ),
            )
        val descending =
            source.sortedForRepository(
                RepositoryMenu(
                    option = Option.Size,
                    descending = true,
                    pinInstalled = false,
                    pinUpdatable = false,
                ),
            )

        assertEquals(listOf("small", "medium", "large"), ascending.map { it.second.id })
        assertEquals(listOf("large", "medium", "small"), descending.map { it.second.id })
    }

    @Test
    fun `updated time respects the same direction semantics as other fields`() {
        val source =
            listOf(
                entry(id = "old", name = "Old", updated = 100f),
                entry(id = "new", name = "New", updated = 300f),
                entry(id = "middle", name = "Middle", updated = 200f),
            )

        val newestFirst =
            source.sortedForRepository(
                RepositoryMenu(
                    option = Option.UpdatedTime,
                    descending = true,
                    pinInstalled = false,
                    pinUpdatable = false,
                ),
            )

        assertEquals(listOf("new", "middle", "old"), newestFirst.map { it.second.id })
    }

    @Test
    fun `missing sort metadata remains last in either direction`() {
        val source =
            listOf(
                entry(id = "unknown", name = "Unknown", size = null),
                entry(id = "known", name = "Known", size = 100),
            )

        val ascending = source.sortedForRepository(menu(Option.Size, descending = false))
        val descending = source.sortedForRepository(menu(Option.Size, descending = true))

        assertEquals(listOf("known", "unknown"), ascending.map { it.second.id })
        assertEquals(listOf("known", "unknown"), descending.map { it.second.id })
    }

    @Test
    fun `pins are deterministic and preserve the selected sort inside each group`() {
        val source =
            listOf(
                entry(id = "installed-z", name = "Zulu", installed = true),
                entry(id = "plain-a", name = "Alpha"),
                entry(id = "update-b", name = "Beta", installed = true, updatable = true),
                entry(id = "installed-a", name = "Able", installed = true),
            )

        val sorted =
            source.sortedForRepository(
                RepositoryMenu(
                    option = Option.Name,
                    descending = false,
                    pinInstalled = true,
                    pinUpdatable = true,
                ),
            )

        assertEquals(
            listOf("update-b", "installed-a", "installed-z", "plain-a"),
            sorted.map { it.second.id },
        )
    }

    private fun menu(option: Option, descending: Boolean) =
        RepositoryMenu(
            option = option,
            descending = descending,
            pinInstalled = false,
            pinUpdatable = false,
        )

    private fun entry(
        id: String,
        name: String,
        size: Int? = null,
        updated: Float = 1f,
        installed: Boolean = false,
        updatable: Boolean = false,
    ): OnlineModuleEntry {
        val module =
            OnlineModule(
                id = id,
                name = name,
                version = "1.0",
                versionCode = 1,
                author = "Author",
                track = TrackJson(typeName = "ONLINE_JSON"),
                versions =
                    listOf(
                        VersionItem(
                            timestamp = updated,
                            version = "1.0",
                            versionCode = 1,
                            zipUrl = "https://example.invalid/$id.zip",
                            size = size,
                        ),
                    ),
                size = size,
            )
        val state =
            OnlineState(
                installed = installed,
                updatable = updatable,
                hasLicense = false,
                lastUpdated = updated,
            )
        return state to module
    }
}
