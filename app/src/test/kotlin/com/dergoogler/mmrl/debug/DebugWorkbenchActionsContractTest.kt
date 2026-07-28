package com.dergoogler.mmrl.debug

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkbenchActionsContractTest {
    private val root = repositoryRoot()

    @Test
    fun `debug actions are guarded and reuse existing app bridges`() {
        val actions = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugActionRunner.kt")
        val screen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/debug/DebugWorkbenchScreen.kt")

        assertTrue(actions.contains("class DebugActionRunner"))
        assertTrue(actions.contains("LsposedRepository(context).lsposedManagerIntent()"))
        assertTrue(actions.contains("repository.providerRefreshPlan()"))
        assertTrue(actions.contains("LsposedProviderRefreshMode.ACTION_BRIDGE"))
        assertTrue(actions.contains("ActionActivity.start(context, ModId(moduleId))"))
        assertTrue(actions.contains("RepositoryService.start(context, interval = 1L)"))
        assertTrue(actions.contains("RepositoryService.stop(context)"))
        assertTrue(actions.contains("No arbitrary shell"))
        assertFalse(actions.contains("Runtime.getRuntime().exec"))
        assertFalse(actions.contains("newSuperUserPty("))

        assertTrue(screen.contains("Guarded actions"))
        assertTrue(screen.contains("Open resolved manager"))
        assertTrue(screen.contains("Run provider action bridge"))
        assertTrue(screen.contains("Start repository refresh"))
        assertTrue(screen.contains("Stop repository refresh"))
        assertTrue(screen.contains("lastAction"))
    }

    @Test
    fun `phase 3 documentation preserves redaction and non arbitrary shell policy`() {
        val doc = source("docs/DEBUG_WORKBENCH.md")

        assertTrue(doc.contains("Phase 3 guarded remediation actions"))
        assertTrue(doc.contains("must not expose arbitrary shell input"))
        assertTrue(doc.contains("must not") && doc.contains("print tokens"))
        assertTrue(doc.contains("provider refresh plan"))
        assertTrue(doc.contains("redacted"))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
