package com.dergoogler.mmrl.model.local

import com.dergoogler.mmrl.platform.model.ModId.Companion.toModId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledModuleGroupingTest {
    @Test
    fun `runtime states take priority and modules appear exactly once`() {
        val attention = module("attention", State.UPDATE)
        val update = module("update", State.ENABLE)
        val enabled = module("enabled", State.ENABLE)
        val disabled = module("disabled", State.DISABLE)
        val removal = module("removal", State.REMOVE)

        val groups =
            groupInstalledModules(
                modules = listOf(attention, update, enabled, disabled, removal),
                updateIds = setOf(attention.id, update.id, removal.id),
            )

        assertEquals(
            listOf(
                InstalledModuleGroupKey.NEEDS_ATTENTION,
                InstalledModuleGroupKey.UPDATES_AVAILABLE,
                InstalledModuleGroupKey.ENABLED,
                InstalledModuleGroupKey.DISABLED,
                InstalledModuleGroupKey.PENDING_REMOVAL,
            ),
            groups.map { it.key },
        )
        assertEquals(listOf("attention"), groups[0].modules.map { it.id.id })
        assertEquals(listOf("update"), groups[1].modules.map { it.id.id })
        assertEquals(listOf("enabled"), groups[2].modules.map { it.id.id })
        assertEquals(listOf("disabled"), groups[3].modules.map { it.id.id })
        assertEquals(listOf("removal"), groups[4].modules.map { it.id.id })

        val flattened = groups.flatMap { it.modules }
        assertEquals(5, flattened.size)
        assertEquals(5, flattened.distinctBy { it.id }.size)
    }

    @Test
    fun `empty groups are omitted`() {
        val groups = groupInstalledModules(listOf(module("enabled", State.ENABLE)), emptySet())

        assertEquals(listOf(InstalledModuleGroupKey.ENABLED), groups.map { it.key })
        assertTrue(groups.none { it.modules.isEmpty() })
    }

    private fun module(id: String, state: State) =
        LocalModule(
            id = id.toModId(),
            name = id,
            version = "1.0",
            versionCode = 1,
            author = "Author",
            description = "",
            updateJson = "",
            state = state,
            size = 1,
            lastUpdated = 1,
        )
}
