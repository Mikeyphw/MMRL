package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class UnifiedModuleBrowserFinalSealContractTest {
    private val projectRoot: Path = Paths.get(System.getProperty("user.dir") ?: ".").let { cwd ->
        if (cwd.fileName?.toString() == "app") cwd.parent else cwd
    }

    private fun source(path: String): String =
        String(Files.readAllBytes(projectRoot.resolve(path)), StandardCharsets.UTF_8)

    @Test
    fun `final seal remains pure model and documentation backed`() {
        val seal = source("app/src/main/kotlin/com/dergoogler/mmrl/model/unified/UnifiedModuleBrowserRegressionSeal.kt")
        val docs = source("docs/UNIFIED_MODULE_BROWSER_PHASE17.md")

        assertTrue(seal.contains("object UnifiedModuleBrowserRegressionSeal"))
        assertTrue(seal.contains("UnifiedModuleBrowserControls.stats"))
        assertTrue(seal.contains("UnifiedModuleProblemCenter.build"))
        assertTrue(seal.contains("UnifiedModuleBrowserActionPlanner.forItem"))
        assertTrue(seal.contains("UnifiedModuleBrowserPresentation.card"))
        assertTrue(docs.contains("Final integration / regression seal"))
        assertTrue(docs.contains("installed, repo, GitHub, LSPosed, local, and rescue"))
    }

    @Test
    fun `final seal docs keep destructive actions out of phase seventeen`() {
        val actions = source("app/src/main/kotlin/com/dergoogler/mmrl/model/unified/UnifiedModuleBrowserActions.kt")
        val docs = source("docs/UNIFIED_MODULE_BROWSER_PHASE17.md")

        assertTrue(actions.contains("blockedResult(action: UnifiedModuleBrowserAction)"))
        assertTrue(actions.contains("Install, remove, enable, disable, and scope writes require an explicit confirmed flow."))
        assertTrue(docs.contains("No install, remove, enable, disable, or scope-write execution is added here."))
    }
}
