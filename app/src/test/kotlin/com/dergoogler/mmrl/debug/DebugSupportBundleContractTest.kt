package com.dergoogler.mmrl.debug

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugSupportBundleContractTest {
    private val root = repositoryRoot()

    @Test
    fun `support bundle export is redacted read only and provider backed`() {
        val exporter = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugSupportBundleExporter.kt")
        val screen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/debug/DebugWorkbenchScreen.kt")

        assertTrue(exporter.contains("class DebugSupportBundleExporter"))
        assertTrue(exporter.contains("FileProvider.getUriForFile"))
        assertTrue(exporter.contains("ZipOutputStream"))
        assertTrue(exporter.contains("debug-report.txt"))
        assertTrue(exporter.contains("debug-report.json"))
        assertTrue(exporter.contains("README.txt"))
        assertTrue(exporter.contains("debug-history.txt"))
        assertTrue(exporter.contains("debug-history.json"))
        assertTrue(exporter.contains("DebugRedactor.redact"))
        assertTrue(exporter.contains("FLAG_GRANT_READ_URI_PERMISSION"))
        assertFalse(exporter.contains("Runtime.getRuntime().exec"))
        assertFalse(exporter.contains("newSuperUserPty("))
        assertFalse(exporter.contains("applyScopePlan"))

        assertTrue(screen.contains("Share support bundle"))
        assertTrue(screen.contains("DebugSupportBundleExporter(context)"))
        assertTrue(screen.contains("supportBundleExporter.share(results, lastAction, history)"))
        assertTrue(screen.contains("Clear local history"))
    }

    @Test
    fun `debug screen uses modern clipboard api`() {
        val screen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/debug/DebugWorkbenchScreen.kt")

        assertTrue(screen.contains("LocalClipboard.current"))
        assertTrue(screen.contains("ClipEntry(ClipData.newPlainText"))
        assertTrue(screen.contains("clipboard.setClipEntry"))
        assertFalse(screen.contains("LocalClipboardManager"))
        assertFalse(screen.contains("AnnotatedString"))
    }

    @Test
    fun `phase 4 documentation describes support bundle safeguards`() {
        val doc = source("docs/DEBUG_WORKBENCH.md")

        assertTrue(doc.contains("Phase 4 support bundle export"))
        assertTrue(doc.contains("debug-report.txt"))
        assertTrue(doc.contains("debug-report.json"))
        assertTrue(doc.contains("README.txt"))
        assertTrue(doc.contains("does not run shell commands"))
        assertTrue(doc.contains("does not mutate provider modules"))
        assertTrue(doc.contains("LocalClipboard"))
    }

    @Test
    fun `redactor still protects exported support content`() {
        val secretReport = "Authorization: Bearer ghp_1234567890abcdef\nCookie: session=secret; ok=true"
        val redacted = DebugRedactor.redact(secretReport)

        assertFalse(redacted.contains("ghp_1234567890abcdef"))
        assertFalse(redacted.contains("session=secret"))
        assertTrue(redacted.contains("Authorization: Bearer <redacted>"))
        assertTrue(redacted.contains("Cookie: <redacted>; ok=true"))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
