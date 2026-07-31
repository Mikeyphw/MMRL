package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class UnifiedModuleBrowserReleaseDocsContractTest {
    private val projectRoot: Path = Paths.get(System.getProperty("user.dir") ?: ".").let { cwd ->
        if (cwd.fileName?.toString() == "app") cwd.parent else cwd
    }

    private fun source(path: String): String =
        String(Files.readAllBytes(projectRoot.resolve(path)), StandardCharsets.UTF_8)

    @Test
    fun `release note indexes every unified browser phase`() {
        val releaseNotes = source("docs/UNIFIED_MODULE_BROWSER_RELEASE_NOTES.md")
        val phase18 = source("docs/UNIFIED_MODULE_BROWSER_PHASE18.md")

        UnifiedModuleBrowserReleaseSeal.requiredDocuments.forEach { doc ->
            assertTrue("release notes should mention $doc", releaseNotes.contains(doc.substringAfterLast('/')))
        }
        assertTrue(phase18.contains("Release polish / cleanup"))
        assertTrue(phase18.contains("UnifiedModuleBrowserReleaseSeal.kt"))
    }

    @Test
    fun `release docs keep the safe action boundary visible`() {
        val releaseNotes = source("docs/UNIFIED_MODULE_BROWSER_RELEASE_NOTES.md")
        val phase18 = source("docs/UNIFIED_MODULE_BROWSER_PHASE18.md")
        val releaseSeal = source("app/src/main/kotlin/com/dergoogler/mmrl/model/unified/UnifiedModuleBrowserReleaseSeal.kt")

        assertTrue(releaseNotes.contains("No install, remove, enable, disable, or LSPosed scope-write execution is added"))
        assertTrue(phase18.contains("confirmed flow"))
        assertTrue(releaseSeal.contains("Any future mutating operation must be modeled as a confirmed flow"))
    }
}
