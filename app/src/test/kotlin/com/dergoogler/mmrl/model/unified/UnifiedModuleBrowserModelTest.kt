package com.dergoogler.mmrl.model.unified

import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshSnapshot
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.ModuleItem
import com.dergoogler.mmrl.database.entity.local.LocalModuleSource
import com.dergoogler.mmrl.github.GitHubArtifactStrategy
import com.dergoogler.mmrl.lsposed.LsposedInstalledModule
import com.dergoogler.mmrl.lsposed.LsposedManagerOpenMode
import com.dergoogler.mmrl.lsposed.LsposedModuleScope
import com.dergoogler.mmrl.lsposed.LsposedProviderStatus
import com.dergoogler.mmrl.lsposed.LsposedRepoModule
import com.dergoogler.mmrl.lsposed.LsposedScopeTarget
import com.dergoogler.mmrl.model.local.LocalModule
import com.dergoogler.mmrl.model.local.State
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.TrackJson
import com.dergoogler.mmrl.model.online.VersionItem
import com.dergoogler.mmrl.platform.content.ModuleCompatibility
import com.dergoogler.mmrl.platform.model.ModId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedModuleBrowserModelTest {
    @Test
    fun `root repo and saved github source collapse into one canonical row`() {
        val items = UnifiedModuleBrowserModel.build(
            UnifiedModuleInputs(
                rootModules = listOf(rootModule(id = "demo", versionCode = 10)),
                repositoryModules = listOf(repoModule(id = "demo", versionCode = 12)),
                savedSources = listOf(
                    LocalModuleSource(
                        id = "demo",
                        repoUrl = "https://github.com/example/demo",
                        mode = "nightly",
                        installedVersion = "1.0",
                        installedVersionCode = 10,
                        sourceUrl = "https://github.com/example/demo?mmrlSource=nightly&artifactStrategy=directZip",
                        updatedAt = 123L,
                    ),
                ),
                rootCompatibility = ModuleCompatibility(
                    hasMagicMount = true,
                    canRestoreModules = true,
                ),
                updateCandidates = mapOf(
                    "demo" to UnifiedModuleUpdate(
                        installedVersion = "1.0 (10)",
                        installedVersionCode = 10,
                        availableVersion = "1.2 (12)",
                        availableVersionCode = 12,
                        sourceLabel = "Example Repo",
                    ),
                ),
            ),
        )

        val item = items.single { it.canonicalId == "demo" }
        assertEquals("Demo", item.title)
        assertEquals(UnifiedInstallState.UPDATE_AVAILABLE, item.state.installState)
        assertEquals(UnifiedModuleSourceMode.MIXED, item.sourceMode)
        assertTrue(item.sourceTypes.contains(UnifiedModuleSourceType.INSTALLED_ROOT))
        assertTrue(item.sourceTypes.contains(UnifiedModuleSourceType.REPOSITORY))
        assertTrue(item.sourceTypes.contains(UnifiedModuleSourceType.GITHUB_SOURCE))
        assertEquals(GitHubArtifactStrategy.DIRECT_MODULE_ZIP, item.artifactStrategy)
        assertTrue(item.badges.any { it.kind == UnifiedBadgeKind.UPDATE && it.label == "Update available" })
        assertTrue(item.badges.any { it.kind == UnifiedBadgeKind.ARTIFACT_STRATEGY && it.label == "Direct module ZIP" })
        assertTrue(item.searchTokens.any { it.contains("example/demo") })
    }

    @Test
    fun `alias registry explains AshReXcue rescue matches`() {
        val items = UnifiedModuleBrowserModel.build(
            UnifiedModuleInputs(
                rootModules = listOf(rootModule(id = "AshLooper", name = "AshReXcue Bootloop Protector")),
                ashState = AshManagerState(
                    rootAvailable = true,
                    source = AshSnapshotSource.Live,
                    snapshot = AshSnapshot(
                        modules = listOf(
                            ModuleItem(
                                folder = "AshLooper",
                                id = "AshLooper",
                                name = "AshReXcue Bootloop Protector",
                                version = "2.0",
                                versionCode = "200",
                                enabled = false,
                                quarantined = true,
                                trust = "suspect",
                                changedSinceStable = true,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val item = items.single { it.canonicalId == "ashrexcue" }
        assertTrue(item.aliases.contains("ashlooper"))
        assertTrue(item.sourceTypes.contains(UnifiedModuleSourceType.RESCUE))
        assertEquals(UnifiedInstallState.PROBLEM, item.state.installState)
        assertTrue(item.badges.any { it.kind == UnifiedBadgeKind.RESCUE && it.label == "Quarantined" })
        assertTrue(item.hasProblems)
    }

    @Test
    fun `lsposed installed repo and scope state expose provider and scope badges`() {
        val repo = LsposedRepoModule(
            name = "io.github.demo.hooks",
            summary = "Demo Hooks",
            description = "Hooks system packages for testing.",
            latestRelease = "12-v1.2",
            repositoryScope = listOf("android", "com.android.systemui"),
            sourceUrl = "https://github.com/example/demo-hooks",
        )
        val installed = LsposedInstalledModule(
            packageName = "io.github.demo.hooks",
            label = "Demo Hooks",
            installedVersionName = "v1.0",
            installedVersionCode = 10,
            repoModule = repo,
            launchable = true,
            detectedByXposedMetadata = true,
            scope = LsposedModuleScope(
                modulePackageName = "io.github.demo.hooks",
                mid = 42,
                apkPath = "/data/app/demo/base.apk",
                enabled = true,
                autoInclude = false,
                targets = listOf(
                    LsposedScopeTarget("android"),
                    LsposedScopeTarget("com.android.systemui", "System UI"),
                ),
            ),
        )

        val item = UnifiedModuleBrowserModel.build(
            UnifiedModuleInputs(
                lsposedRepositoryModules = listOf(repo),
                lsposedInstalledModules = listOf(installed),
                lsposedProviderStatus = LsposedProviderStatus(
                    installed = true,
                    moduleId = "zygisk_lsposed",
                    managerOpenMode = LsposedManagerOpenMode.INSTALLED_MANAGER,
                ),
            ),
        ).single { it.canonicalId == "io.github.demo.hooks" }

        assertEquals(UnifiedInstallState.UPDATE_AVAILABLE, item.state.installState)
        assertEquals(UnifiedProviderCompatibility.COMPATIBLE, item.state.providerCompatibility)
        val scope = item.state.scope as UnifiedScopeState.Lsposed
        assertEquals(2, scope.scopedPackageCount)
        assertTrue(item.badges.any { it.kind == UnifiedBadgeKind.SCOPE && it.label == "2 scoped apps" })
        assertTrue(item.searchTokens.contains("com.android.systemui"))
    }

    @Test
    fun `prepared query and sorting support phase eleven filters`() {
        val items = UnifiedModuleBrowserModel.build(
            UnifiedModuleInputs(
                rootModules = listOf(
                    rootModule(id = "alpha", name = "Alpha", versionCode = 1),
                    rootModule(id = "beta", name = "Beta", state = State.DISABLE, versionCode = 1),
                ),
                updateCandidates = mapOf(
                    "alpha" to UnifiedModuleUpdate(
                        installedVersion = "1.0 (1)",
                        installedVersionCode = 1,
                        availableVersion = "2.0 (2)",
                        availableVersionCode = 2,
                    ),
                ),
            ),
        )

        val updates = UnifiedModuleBrowserModel.applyQuery(items, UnifiedModuleQuery(updatesOnly = true))
        assertEquals(listOf("alpha"), updates.map { it.canonicalId })

        val disabled = UnifiedModuleBrowserModel.applyQuery(
            items,
            UnifiedModuleQuery(installStates = setOf(UnifiedInstallState.DISABLED)),
        )
        assertEquals(listOf("beta"), disabled.map { it.canonicalId })

        val sorted = UnifiedModuleBrowserModel.sort(items, UnifiedModuleSortMode.UPDATE_AVAILABLE_FIRST)
        assertEquals("alpha", sorted.first().canonicalId)
        assertFalse(UnifiedModuleBrowserModel.applyQuery(items, UnifiedModuleQuery(text = "missing")).isNotEmpty())
    }

    private fun rootModule(
        id: String,
        name: String = "Demo",
        state: State = State.ENABLE,
        versionCode: Int = 10,
    ) = LocalModule(
        id = ModId(id),
        name = name,
        version = "1.0",
        versionCode = versionCode,
        author = "Tester",
        description = "A module used by the unified browser contract.",
        updateJson = "",
        state = state,
        size = 1024L,
        lastUpdated = 500L + versionCode,
    )

    private fun repoModule(
        id: String,
        versionCode: Int,
    ) = OnlineModule(
        repoUrl = "https://repo.example/modules.json",
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        version = "1.$versionCode",
        versionCode = versionCode,
        author = "Repository",
        description = "Repository module $id",
        track = TrackJson(typeName = "ONLINE_JSON"),
        versions = listOf(
            VersionItem(
                repoUrl = "https://repo.example/modules.json",
                timestamp = versionCode.toFloat(),
                version = "1.$versionCode",
                versionCode = versionCode,
                zipUrl = "https://repo.example/$id.zip",
            ),
        ),
    )
}
