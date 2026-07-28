package com.dergoogler.mmrl.debug

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugHistoryContractTest {
    private val root = repositoryRoot()

    @Test
    fun `history persists only redacted status snapshots and bounded comparisons`() {
        val history = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugHistoryStore.kt")
        val screen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/debug/DebugWorkbenchScreen.kt")

        assertTrue(history.contains("class DebugHistoryStore"))
        assertTrue(history.contains("DEFAULT_MAX_SNAPSHOTS = 10"))
        assertTrue(history.contains("probe-history.tsv"))
        assertTrue(history.contains("DebugRedactor.redact(result.summary)"))
        assertTrue(history.contains("fun compare("))
        assertTrue(history.contains("newlyFailing"))
        assertTrue(history.contains("fixedSinceLast"))
        assertTrue(history.contains("regressed"))
        assertTrue(history.contains("improved"))
        assertFalse(history.contains("evidence ="))
        assertFalse(history.contains("Runtime.getRuntime().exec"))
        assertFalse(history.contains("newSuperUserPty("))

        assertTrue(screen.contains("DebugHistoryStore(context)"))
        assertTrue(screen.contains("historyStore.record(next)"))
        assertTrue(screen.contains("DebugHistoryStore.compare(next, previous)"))
        assertTrue(screen.contains("Clear local history"))
        assertTrue(screen.contains("supportBundleExporter.share(results, lastAction, history, activeGuide)"))
    }

    @Test
    fun `history comparison classifies fixed and regressed probes`() {
        val previous = DebugHistorySnapshot(
            createdAtMillis = 1L,
            entries = listOf(
                DebugHistoryEntry("manager", "Manager", DebugProbeGroup.LSPOSED, DebugProbeStatus.PASS, "ok"),
                DebugHistoryEntry("repo", "Repo", DebugProbeGroup.REPOSITORY, DebugProbeStatus.FAIL, "403"),
            ),
        )
        val current = listOf(
            DebugProbeResult("manager", "Manager", DebugProbeGroup.LSPOSED, DebugProbeStatus.WARN, "visible but not launchable"),
            DebugProbeResult("repo", "Repo", DebugProbeGroup.REPOSITORY, DebugProbeStatus.PASS, "backup mirror ok"),
            DebugProbeResult("ash", "Ash", DebugProbeGroup.ASH_REXCUE, DebugProbeStatus.FAIL, "missing"),
        )

        val comparison = DebugHistoryStore.compare(current, previous)!!

        assertEquals(1, comparison.regressed.size)
        assertEquals("manager", comparison.regressed.single().id)
        assertEquals(1, comparison.fixedSinceLast.size)
        assertEquals("repo", comparison.fixedSinceLast.single().id)
        assertEquals(1, comparison.newlyFailing.size)
        assertEquals("ash", comparison.newlyFailing.single().id)
        assertTrue(DebugHistoryFormatter.comparisonText(comparison).contains("regressed"))
    }

    @Test
    fun `support bundle includes redacted debug history`() {
        val exporter = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugSupportBundleExporter.kt")
        val doc = source("docs/DEBUG_WORKBENCH.md")

        assertTrue(exporter.contains("history: List<DebugHistorySnapshot> = emptyList()"))
        assertTrue(exporter.contains("debug-history.txt"))
        assertTrue(exporter.contains("debug-history.json"))
        assertTrue(exporter.contains("DebugHistoryFormatter.historyText(history)"))
        assertTrue(exporter.contains("DebugHistoryFormatter.historyJson(history)"))
        assertTrue(exporter.contains("historyCount"))

        assertTrue(doc.contains("Phase 5 history and comparisons"))
        assertTrue(doc.contains("debug-history.txt"))
        assertTrue(doc.contains("debug-history.json"))
        assertTrue(doc.contains("at most 10 snapshots"))
        assertTrue(doc.contains("only redacted summaries are persisted"))
        assertTrue(doc.contains("must not touch modules"))
    }

    @Test
    fun `history formatter keeps secrets redacted`() {
        val history = listOf(
            DebugHistorySnapshot(
                createdAtMillis = 2L,
                entries = listOf(
                    DebugHistoryEntry(
                        id = "token",
                        title = "Token",
                        group = DebugProbeGroup.SECURITY,
                        status = DebugProbeStatus.WARN,
                        summary = "Authorization: Bearer ghp_1234567890abcdef",
                    ),
                ),
            ),
        )

        val text = DebugHistoryFormatter.historyText(history)
        val json = DebugHistoryFormatter.historyJson(history)

        assertFalse(text.contains("ghp_1234567890abcdef"))
        assertFalse(json.contains("ghp_1234567890abcdef"))
        assertTrue(text.contains("<redacted>"))
        assertTrue(json.contains("<redacted>"))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
