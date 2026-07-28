package com.dergoogler.mmrl.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class UiTopologyDensitySealTest {
    private val projectRoot: Path = Paths.get(System.getProperty("user.dir") ?: error("user.dir is not set")).let { cwd ->
        if (cwd.fileName?.toString() == "app") cwd.parent else cwd
    }

    private fun source(path: String): String =
        String(Files.readAllBytes(projectRoot.resolve(path)), StandardCharsets.UTF_8)

    @Test
    fun statusPillDoesNotPretendToBeAnImage() {
        val flatUi = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/component/FlatUi.kt")
        assertFalse(flatUi.contains("Role.Image"))
        assertFalse(flatUi.contains("this.role = Role.Image"))
        assertTrue(flatUi.contains("this.stateDescription = stateDescription"))
    }

    @Test
    fun moduleQuickActionsWrapAndAreNotDuplicatedAsStatusPills() {
        val modulesList = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/modules/ModulesList.kt")
        assertTrue(modulesList.contains("FlowRow("))
        assertFalse(modulesList.contains("module_action_available"))
        assertFalse(modulesList.contains("StatusPill(\n                            text = stringResource(R.string.view_module_features_webui)"))
    }

    @Test
    fun repositoryTabContentDoesNotDoubleApplyToolbarTopPadding() {
        val repositories = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/repositories/RepositoriesScreen.kt")
        assertTrue(repositories.contains("RepositoryTabs("))
        assertTrue(repositories.contains("innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())"))
    }

    @Test
    fun githubSourceDialogLabelsAreResourceBacked() {
        val repositories = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/repositories/RepositoriesScreen.kt")
        assertTrue(repositories.contains("R.string.github_source_add_title"))
        assertTrue(repositories.contains("R.string.github_source_repository_url"))
        assertTrue(repositories.contains("R.string.github_source_mode_nightly"))
        assertFalse(repositories.contains("github_source_mode_nightly_link"))
        assertFalse(repositories.contains("Text(\"Add GitHub source\")"))
        assertFalse(repositories.contains("Text(\"Repository URL\")"))
    }

    @Test
    fun lsposedInstalledPhoneListPrecedesGuidanceAndSnapshots() {
        val lsposed = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")
        val itemsIndex = lsposed.indexOf("items(modules, key = { it.packageName })")
        val guidanceIndex = lsposed.indexOf("GuidanceCard(\n                    title = stringResource(R.string.lsposed_activation_title)")
        val snapshotIndex = lsposed.indexOf("LsposedSnapshotCard(\n                    installedCount = state.installed.size")
        assertTrue(itemsIndex >= 0)
        assertTrue(guidanceIndex > itemsIndex)
        assertTrue(snapshotIndex > itemsIndex)
    }
}
