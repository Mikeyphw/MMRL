package com.dergoogler.mmrl.debug

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkbenchFinalSealContractTest {
    private val root = repositoryRoot()

    @Test
    fun `final seal documents the complete debug workbench roadmap`() {
        val doc = source("docs/DEBUG_WORKBENCH.md")
        val seal = source("docs/DEBUG_WORKBENCH_FINAL_SEAL.md")

        assertTrue(doc.contains("Phase 7 final seal"))
        assertTrue(seal.contains("Phase 1/2 probe pack"))
        assertTrue(seal.contains("Phase 3 guarded actions"))
        assertTrue(seal.contains("Phase 4 support bundle"))
        assertTrue(seal.contains("Phase 5 history and comparisons"))
        assertTrue(seal.contains("Phase 6 guided diagnostics flows"))
        assertTrue(seal.contains("Phase 7 final seal"))
        assertTrue(seal.contains("Manager not recognized") || seal.contains("manager not recognized"))
        assertTrue(seal.contains("Xposed repo 403"))
        assertTrue(seal.contains("AshReXcue not detected"))
        assertTrue(seal.contains("GitHub token problems"))
    }

    @Test
    fun `final seal keeps support bundle history and guide exports redacted`() {
        val exporter = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugSupportBundleExporter.kt")
        val screen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/debug/DebugWorkbenchScreen.kt")
        val seal = source("docs/DEBUG_WORKBENCH_FINAL_SEAL.md")

        listOf(
            "debug-report.txt",
            "debug-report.json",
            "debug-history.txt",
            "debug-history.json",
            "debug-guide.txt",
            "debug-guide.json",
            "README.txt",
        ).forEach { entry ->
            assertTrue("missing support bundle entry $entry", exporter.contains(entry))
            assertTrue("missing support bundle contract for $entry", seal.contains(entry))
        }

        assertTrue(exporter.contains("FileProvider.getUriForFile"))
        assertTrue(exporter.contains("DebugRedactor.redact(content)"))
        assertTrue(exporter.contains("DebugHistoryFormatter.historyJson(history)"))
        assertTrue(exporter.contains("DebugGuideFormatter.asJson(activeGuide)"))
        assertTrue(screen.contains("Share support bundle"))
        assertTrue(screen.contains("Copy redacted report"))
        assertTrue(screen.contains("Guided diagnostics"))
        assertTrue(screen.contains("Clear local history"))
        assertTrue(screen.contains("LocalClipboard"))
        assertFalse(screen.contains("LocalClipboardManager"))
    }

    @Test
    fun `debug package remains narrow and does not grow unsafe mutation surfaces`() {
        val debugSource = debugSources()
        val actions = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugActionRunner.kt")
        val history = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugHistoryStore.kt")
        val guided = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugGuidedDiagnostics.kt")
        val seal = source("docs/DEBUG_WORKBENCH_FINAL_SEAL.md")

        assertTrue(actions.contains("LsposedRepository(context).lsposedManagerIntent()"))
        assertTrue(actions.contains("repository.providerRefreshPlan()"))
        assertTrue(actions.contains("RepositoryService.start(context, interval = 1L)"))
        assertTrue(history.contains("DEFAULT_MAX_SNAPSHOTS = 10"))
        assertTrue(guided.contains("DebugIssueFlow"))

        assertFalse(debugSource.contains("Runtime.getRuntime().exec"))
        assertFalse(debugSource.contains("newSuperUserPty("))
        assertFalse(debugSource.contains("applyScopePlan("))
        assertFalse(debugSource.contains("modules_config.db") && debugSource.contains("SQLiteDatabase.openDatabase"))
        assertFalse(debugSource.contains("GitHubTokenStore(context).save"))
        assertTrue(seal.contains("must not"))
        assertTrue(seal.contains("expose arbitrary shell input"))
        assertTrue(seal.contains("write LSPosed scope databases"))
        assertTrue(seal.contains("mutate provider module folders"))
        assertTrue(seal.contains("mutate GitHub token storage"))
    }

    @Test
    fun `final redaction contract removes tokens authorization headers and cookies`() {
        val value = buildString {
            append("Authorization: Bearer ghp_1234567890abcdef\n")
            append("authorization=Bearer github_pat_1234567890abcdefghijklmnop\n")
            append("Cookie: session=secret-value; theme=dark\n")
            append("token gho_1234567890abcdef")
        }

        val redacted = DebugRedactor.redact(value)

        assertFalse(redacted.contains("ghp_1234567890abcdef"))
        assertFalse(redacted.contains("github_pat_1234567890abcdefghijklmnop"))
        assertFalse(redacted.contains("session=secret-value"))
        assertFalse(redacted.contains("gho_1234567890abcdef"))
        assertTrue(redacted.contains("Authorization: Bearer <redacted>"))
        assertTrue(redacted.contains("authorization=Bearer <redacted>"))
        assertTrue(redacted.contains("Cookie: <redacted>; theme=dark"))
        assertTrue(redacted.contains("<github-token-redacted>") || redacted.contains("<redacted>"))
    }

    private fun debugSources(): String = File(root, "app/src/main/kotlin/com/dergoogler/mmrl/debug")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .joinToString("\n") { it.readText() }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
