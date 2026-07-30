package com.dergoogler.mmrl.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class UnifiedModuleBrowserUiContractTest {
    private val projectRoot: Path = Paths.get(System.getProperty("user.dir") ?: ".").let { cwd ->
        if (cwd.fileName?.toString() == "app") cwd.parent else cwd
    }

    private fun source(path: String): String =
        String(Files.readAllBytes(projectRoot.resolve(path)), StandardCharsets.UTF_8)

    @Test
    fun `modules screen collects unified browser streams`() {
        val screen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/modules/ModulesScreen.kt")
        assertTrue(screen.contains("viewModel.unifiedBrowserControls.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("viewModel.unifiedModules.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("viewModel.filteredUnifiedModules.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("viewModel.filteredUnifiedProblemReport.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("unifiedControls = unifiedControls"))
        assertTrue(screen.contains("filteredUnifiedModules = filteredUnifiedModules"))
        assertTrue(screen.contains("filteredUnifiedProblemReport = filteredUnifiedProblemReport"))
    }

    @Test
    fun `modules list renders unified header and non installed cards`() {
        val list = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/modules/ModulesList.kt")
        assertTrue(list.contains("UnifiedModuleBrowserHeader("))
        assertTrue(list.contains("unifiedControls.view == UnifiedModuleView.INSTALLED"))
        assertTrue(list.contains("UnifiedModuleBrowserCard("))
        assertTrue(list.contains("UnifiedModuleProblemDigest(report = filteredUnifiedProblemReport)"))
        assertTrue(list.contains("UnifiedModuleProblemCard(problem = problem)"))
        assertTrue(list.contains("UnifiedModuleBrowserEmptyState(unifiedControls)"))
        assertTrue(list.contains("viewModel::setUnifiedBrowserSort"))
        assertTrue(list.contains("viewModel::setUnifiedBrowserHealthFilter"))
        assertTrue(list.contains("viewModel::setUnifiedBrowserScopeFilter"))
        assertTrue(list.contains("viewModel::setUnifiedBrowserSourceTypes"))
        assertTrue(list.contains("viewModel::setUnifiedBrowserProviderStates"))
    }

    @Test
    fun `unified panel exposes views badges density filters and diagnostics`() {
        val panel = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/modules/UnifiedModuleBrowserPanel.kt")
        assertTrue(panel.contains("UnifiedModuleView.entries.forEach"))
        assertTrue(panel.contains("UnifiedModuleDensityMode.entries.forEach"))
        assertTrue(panel.contains("UnifiedModuleHealthFilter.entries.forEach"))
        assertTrue(panel.contains("UnifiedScopeFilter.entries.forEach"))
        assertTrue(panel.contains("UnifiedModuleSourceType.entries.filter"))
        assertTrue(panel.contains("UnifiedProviderCompatibility.entries.filter"))
        assertTrue(panel.contains("item.badges.take"))
        assertTrue(panel.contains("density.showDiagnostics"))
        assertTrue(panel.contains("item.match.explanation"))
        assertTrue(panel.contains("fun UnifiedModuleProblemDigest"))
        assertTrue(panel.contains("fun UnifiedModuleProblemCard"))
        assertTrue(panel.contains("problem.actions.forEach"))
    }
}
