package com.dergoogler.mmrl.lsposed

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedFinalIntegrationSealTest {
    @Test
    fun `repository and modules screens expose LSPosed tabs`() {
        val repositoryScreen = source("src/main/kotlin/com/dergoogler/mmrl/ui/screens/repositories/RepositoriesScreen.kt")
        val modulesScreen = source("src/main/kotlin/com/dergoogler/mmrl/ui/screens/modules/ModulesScreen.kt")

        assertTrue(repositoryScreen.contains("RepositoryTab.LSPosed"))
        assertTrue(repositoryScreen.contains("LsposedRepositoryTab("))
        assertTrue(repositoryScreen.contains("repository_tab_lsposed"))

        assertTrue(modulesScreen.contains("ModulesTab.LSPosed"))
        assertTrue(modulesScreen.contains("LsposedModulesTab("))
        assertTrue(modulesScreen.contains("modules_tab_lsposed"))
    }

    @Test
    fun `apk install remains review-first and manager guided`() {
        val lsposedScreen = source("src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")

        assertTrue(lsposedScreen.contains("LsposedApkReviewDialog("))
        assertTrue(lsposedScreen.contains("viewModel.install(module)"))
        assertTrue(lsposedScreen.contains("viewModel.update(installed)"))
        assertTrue(lsposedScreen.contains("viewModel::openLsposed"))
        assertTrue(lsposedScreen.contains("LsposedSafetyClassifier"))
    }

    @Test
    fun `blocked updates stay visible while normal install action is disabled`() {
        val installed = installedModule(versionCode = 7L, versionName = "1.0.0")
        val policy = LsposedVersionPolicy.pinCurrent(installed)

        assertTrue(installed.hasUpdate)
        assertTrue(policy.blocks(installed.repoVersion?.versionCode))
        assertTrue(policy.statusLabel(installed.repoVersion?.versionName).contains("newer"))
    }

    @Test
    fun `snapshot planner seals LSPosed apk compare states`() {
        val saved = installedModule(versionCode = 7L, versionName = "1.0.0").toSnapshotItem()
        val snapshot = LsposedSnapshot(
            id = "known-good",
            label = "Known good",
            createdAt = 1L,
            modules = listOf(saved),
        )
        val current = listOf(
            installedModule(versionCode = 8L, versionName = "1.1.0").toSnapshotItem(),
            installedModule(packageName = "io.github.extra", label = "Extra", versionCode = 1L).toSnapshotItem(),
        )

        val statuses = LsposedSnapshotPlanner.compare(snapshot, current).map { it.status }.toSet()

        assertEquals(setOf(LsposedSnapshotPlanStatus.EXTRA, LsposedSnapshotPlanStatus.VERSION_CHANGED), statuses)
    }

    @Test
    fun `adaptive polish warning cleanup stays fixed`() {
        val lsposedScreen = source("src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")

        assertFalse(lsposedScreen.contains("detailRail?.invoke()"))
        assertTrue(lsposedScreen.contains("detailRail()"))
    }

    private fun source(relativeToApp: String): String {
        val cwd = Path.of(System.getProperty("user.dir"))
        val candidates = listOf(
            cwd.resolve(relativeToApp),
            cwd.resolve("app").resolve(relativeToApp),
            cwd.resolve("..").resolve("app").resolve(relativeToApp).normalize(),
            cwd.resolve("..").resolve(relativeToApp).normalize(),
        )

        val file = candidates.firstOrNull { Files.exists(it) && Files.isRegularFile(it) }
            ?: error("Could not locate source file $relativeToApp from $cwd; checked $candidates")
        return String(Files.readAllBytes(file), Charsets.UTF_8)
    }

    private fun installedModule(
        packageName: String = "io.github.example",
        label: String = "Example",
        versionCode: Long = 7L,
        versionName: String? = "1.0.0",
    ) = LsposedInstalledModule(
        packageName = packageName,
        label = label,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        repoModule = LsposedRepoModule(
            name = packageName,
            summary = label,
            latestRelease = "${versionCode + 1}-${versionName ?: "next"}",
        ),
        launchable = true,
        detectedByXposedMetadata = true,
    )
}
