package com.dergoogler.mmrl.lsposed

import com.dergoogler.mmrl.datastore.model.Option
import com.dergoogler.mmrl.datastore.model.RepositoryMenu
import org.junit.Assert.assertEquals
import org.junit.Test

class LsposedRepositorySortPolicyTest {
    @Test
    fun `sorts by name in both directions`() {
        val modules = listOf(module("z.pkg", "Zulu"), module("a.pkg", "Alpha"))

        assertEquals(
            listOf("Alpha", "Zulu"),
            sortedNames(modules, RepositoryMenu(option = Option.Name, descending = false)),
        )
        assertEquals(
            listOf("Zulu", "Alpha"),
            sortedNames(modules, RepositoryMenu(option = Option.Name, descending = true)),
        )
    }

    @Test
    fun `sorts by repository update time and keeps missing metadata last`() {
        val modules = listOf(
            module("old.pkg", "Old", updatedAt = "2025-01-01T00:00:00Z"),
            module("missing.pkg", "Missing"),
            module("new.pkg", "New", updatedAt = "2026-09-01T00:00:00Z"),
        )

        assertEquals(
            listOf("New", "Old", "Missing"),
            sortedNames(modules, RepositoryMenu(option = Option.UpdatedTime, descending = true)),
        )
    }

    @Test
    fun `sorts by selected apk asset size and keeps missing size last`() {
        val modules = listOf(
            module("large.pkg", "Large", apkSize = 30_000),
            module("missing.pkg", "Missing"),
            module("small.pkg", "Small", apkSize = 10_000),
        )

        assertEquals(
            listOf("Small", "Large", "Missing"),
            sortedNames(modules, RepositoryMenu(option = Option.Size, descending = false)),
        )
    }

    @Test
    fun `pin updatable and installed follows normal repository precedence`() {
        val updatable = module("update.pkg", "Zulu update", versionCode = 2)
        val installed = module("installed.pkg", "Alpha installed", versionCode = 1)
        val remote = module("remote.pkg", "Beta remote", versionCode = 1)
        val installedModules = listOf(
            installedModule(updatable, installedVersionCode = 1),
            installedModule(installed, installedVersionCode = 1),
        )

        val sorted = LsposedRepositorySortPolicy.sort(
            modules = listOf(installed, remote, updatable),
            installed = installedModules,
            menu = RepositoryMenu(
                option = Option.Name,
                descending = false,
                pinInstalled = true,
                pinUpdatable = true,
            ),
        )

        assertEquals(listOf("Zulu update", "Alpha installed", "Beta remote"), sorted.map { it.displayName })
    }

    private fun sortedNames(modules: List<LsposedRepoModule>, menu: RepositoryMenu): List<String> =
        LsposedRepositorySortPolicy.sort(modules, emptyList(), menu).map { it.displayName }

    private fun module(
        packageName: String,
        title: String,
        updatedAt: String? = null,
        apkSize: Int? = null,
        versionCode: Long = 1,
    ): LsposedRepoModule =
        LsposedRepoModule(
            name = packageName,
            description = title,
            latestRelease = "$versionCode-1.0.0",
            latestReleaseTime = updatedAt,
            releases = if (apkSize == null) {
                emptyList()
            } else {
                listOf(
                    LsposedRelease(
                        name = "$versionCode-1.0.0",
                        releaseAssets = listOf(
                            LsposedReleaseAsset(
                                name = "$packageName.apk",
                                downloadUrl = "https://example.invalid/$packageName.apk",
                                size = apkSize,
                            ),
                        ),
                    ),
                )
            },
        )

    private fun installedModule(
        module: LsposedRepoModule,
        installedVersionCode: Long,
    ): LsposedInstalledModule =
        LsposedInstalledModule(
            packageName = module.packageName,
            label = module.displayName,
            installedVersionName = "1.0.0",
            installedVersionCode = installedVersionCode,
            repoModule = module,
            launchable = false,
            detectedByXposedMetadata = true,
        )
}
