package com.dergoogler.mmrl.debug

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugGuidedDiagnosticsContractTest {
    private val root = repositoryRoot()

    @Test
    fun `guided diagnostics expose focused issue flows without new mutation surfaces`() {
        val guided = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugGuidedDiagnostics.kt")
        val screen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/debug/DebugWorkbenchScreen.kt")

        assertTrue(guided.contains("enum class DebugIssueFlow"))
        assertTrue(guided.contains("MANAGER_NOT_RECOGNIZED"))
        assertTrue(guided.contains("XPOSED_REPO_403"))
        assertTrue(guided.contains("GITHUB_TOKEN_PROBLEMS"))
        assertTrue(guided.contains("lsposed-manager-packages"))
        assertTrue(guided.contains("lsposed-repo-endpoints"))
        assertTrue(guided.contains("github-token-store"))
        assertFalse(guided.contains("Runtime.getRuntime().exec"))
        assertFalse(guided.contains("newSuperUserPty("))
        assertFalse(guided.contains("applyScopePlan"))

        assertTrue(screen.contains("Guided diagnostics"))
        assertTrue(screen.contains("DebugIssueFlow.entries"))
        assertTrue(guided.contains("buttonTitle = \"Diagnose manager not recognized\""))
        assertTrue(guided.contains("buttonTitle = \"Diagnose Xposed repo 403\""))
        assertTrue(guided.contains("buttonTitle = \"Diagnose GitHub token problems\""))
        assertTrue(screen.contains("Title(flow.buttonTitle)"))
        assertTrue(screen.contains("DebugGuidedDiagnostics.evaluate(it, next)"))
        assertTrue(screen.contains("activeGuide"))
    }

    @Test
    fun `guided diagnostics classify repo 403 and token problems from focused probes`() {
        val results = listOf(
            DebugProbeResult(
                id = "lsposed-repo-endpoints",
                title = "Repo",
                group = DebugProbeGroup.REPOSITORY,
                status = DebugProbeStatus.FAIL,
                summary = "Repository endpoints returned HTTP 403 and no fallback succeeded.",
                remedies = listOf("Save a GitHub API token in Settings > Other and retry."),
            ),
            DebugProbeResult(
                id = "github-token-store",
                title = "Token",
                group = DebugProbeGroup.SECURITY,
                status = DebugProbeStatus.SKIPPED,
                summary = "No app-wide GitHub token is saved.",
            ),
        )

        val repoGuide = DebugGuidedDiagnostics.evaluate(DebugIssueFlow.XPOSED_REPO_403, results)
        val tokenGuide = DebugGuidedDiagnostics.evaluate(DebugIssueFlow.GITHUB_TOKEN_PROBLEMS, results)

        assertEquals(DebugProbeStatus.FAIL, repoGuide.status)
        assertEquals(2, repoGuide.steps.size)
        assertTrue(repoGuide.summary.contains("at least one focused probe failed"))
        assertTrue(repoGuide.steps.any { it.remedy.contains("GitHub API token") })
        assertEquals(DebugProbeStatus.FAIL, tokenGuide.status)
        assertEquals(listOf("github-token-store", "lsposed-repo-endpoints"), DebugGuidedDiagnostics.relevantResults(DebugIssueFlow.GITHUB_TOKEN_PROBLEMS, results).map { it.id })
    }

    @Test
    fun `support bundle records active guided flow without secrets`() {
        val exporter = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugSupportBundleExporter.kt")
        val guided = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugGuidedDiagnostics.kt")
        val doc = source("docs/DEBUG_WORKBENCH.md")

        assertTrue(exporter.contains("activeGuide: DebugGuideResult? = null"))
        assertTrue(exporter.contains("debug-guide.txt"))
        assertTrue(exporter.contains("debug-guide.json"))
        assertTrue(exporter.contains("activeIssueFlow"))
        assertTrue(exporter.contains("DebugGuideFormatter.asText(activeGuide)"))
        assertTrue(exporter.contains("DebugGuideFormatter.asJson(activeGuide)"))
        assertTrue(guided.contains("DebugRedactor.redact(guide.summary)"))
        assertTrue(guided.contains("DebugRedactor.redact(step.remedy)"))
        assertFalse(guided.contains("println("))

        assertTrue(doc.contains("Phase 6 guided diagnostics flows"))
        assertTrue(doc.contains("Manager not recognized"))
        assertTrue(doc.contains("Xposed repo 403"))
        assertTrue(doc.contains("GitHub token problems"))
        assertTrue(doc.contains("debug-guide.txt"))
        assertTrue(doc.contains("debug-guide.json"))
        assertTrue(doc.contains("must not mutate"))
    }

    @Test
    fun `guided formatter redacts exported remedies and summaries`() {
        val guide = DebugGuideResult(
            flow = DebugIssueFlow.GITHUB_TOKEN_PROBLEMS,
            status = DebugProbeStatus.WARN,
            summary = "Authorization: Bearer ghp_1234567890abcdef",
            steps = listOf(
                DebugGuideStep(
                    title = "Token",
                    status = DebugProbeStatus.WARN,
                    summary = "Cookie: session=secret; ok=true",
                    remedy = "Replace github_pat_1234567890abcdefghijklmnop",
                ),
            ),
        )

        val text = DebugGuideFormatter.asText(guide)
        val json = DebugGuideFormatter.asJson(guide)

        assertFalse(text.contains("ghp_1234567890abcdef"))
        assertFalse(json.contains("ghp_1234567890abcdef"))
        assertFalse(text.contains("session=secret"))
        assertFalse(json.contains("github_pat_1234567890abcdefghijklmnop"))
        assertTrue(text.contains("<redacted>") || text.contains("<github-token-redacted>"))
        assertTrue(json.contains("<redacted>") || json.contains("<github-token-redacted>"))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
