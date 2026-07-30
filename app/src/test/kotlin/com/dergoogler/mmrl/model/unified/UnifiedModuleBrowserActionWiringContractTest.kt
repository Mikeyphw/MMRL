package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class UnifiedModuleBrowserActionWiringContractTest {
    private val projectRoot: Path = Paths.get(System.getProperty("user.dir") ?: ".").let { cwd ->
        if (cwd.fileName?.toString() == "app") cwd.parent else cwd
    }

    private fun source(path: String): String =
        String(Files.readAllBytes(projectRoot.resolve(path)), StandardCharsets.UTF_8)

    @Test
    fun `action contract is safe and non destructive by default`() {
        val actions = source("app/src/main/kotlin/com/dergoogler/mmrl/model/unified/UnifiedModuleBrowserActions.kt")

        assertTrue(actions.contains("enum class UnifiedModuleBrowserActionKind"))
        assertTrue(actions.contains("destructive: Boolean = false"))
        assertTrue(actions.contains("COPY_EVIDENCE"))
        assertTrue(actions.contains("COPY_SOURCE_URL"))
        assertTrue(actions.contains("REFRESH_PROVIDER"))
        assertTrue(actions.contains("OPEN_GITHUB_SOURCE_RULES"))
        assertTrue(actions.contains("RUN_DEBUG_PROBE"))
        assertTrue(actions.contains("UnifiedModuleBrowserActionDestination"))
        assertTrue(actions.contains("UnifiedModuleBrowserActionTone"))
        assertTrue(actions.contains("blockedResult(action: UnifiedModuleBrowserAction)"))
        assertTrue(actions.contains("resultSummary(result: UnifiedModuleBrowserActionResult)"))
        assertFalse(actions.contains("INSTALL_MODULE"))
        assertFalse(actions.contains("REMOVE_MODULE"))
        assertFalse(actions.contains("APPLY_SCOPE"))
    }

    @Test
    fun `view model handles actions through clipboard refresh and guarded messages`() {
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/ModulesViewModel.kt")

        assertTrue(viewModel.contains("fun runUnifiedBrowserAction(action: UnifiedModuleBrowserAction)"))
        assertTrue(viewModel.contains("UnifiedModuleBrowserActionPlanner.resultFor(action)"))
        assertTrue(viewModel.contains("val unifiedBrowserActionResult = unifiedBrowserActionResultFlow.asStateFlow()"))
        assertTrue(viewModel.contains("unifiedBrowserActionResultFlow.value = result"))
        assertTrue(viewModel.contains("private suspend fun executeUnifiedBrowserAction"))
        assertTrue(viewModel.contains("ClipData.newPlainText(label, text)"))
        assertTrue(viewModel.contains("UnifiedModuleBrowserActionKind.REFRESH_PROVIDER"))
        assertTrue(viewModel.contains("UnifiedModuleBrowserActionKind.REFRESH_REPOSITORY"))
        assertTrue(viewModel.contains("UnifiedModuleBrowserActionKind.RUN_DEBUG_PROBE"))
        assertTrue(viewModel.contains("setUnifiedBrowserView(UnifiedModuleView.INSTALLED)"))
        assertTrue(viewModel.contains("search(\"id:\$moduleId\")"))
        assertTrue(viewModel.contains("UnifiedModuleBrowserActionPlanner.blockedResult(action)"))
        assertTrue(viewModel.contains("result.userMessage"))
    }

    @Test
    fun `modules UI binds problem and row action chips`() {
        val panel = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/modules/UnifiedModuleBrowserPanel.kt")
        val list = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/modules/ModulesList.kt")

        assertTrue(panel.contains("AssistChip"))
        assertTrue(panel.contains("UnifiedModuleBrowserActionPlanner.forProblem(problem, action)"))
        assertTrue(panel.contains("UnifiedModuleBrowserActionPlanner.forItem(item)"))
        assertTrue(panel.contains("onAction(browserAction)"))
        assertTrue(panel.contains("onAction(action)"))
        assertTrue(panel.contains("fun UnifiedModuleActionResultCard"))
        assertTrue(panel.contains("UnifiedModuleBrowserActionPlanner.resultSummary(result)"))
        assertTrue(list.contains("viewModel::runUnifiedBrowserAction"))
        assertTrue(list.contains("UnifiedModuleActionResultCard(result = result)"))
    }
}
