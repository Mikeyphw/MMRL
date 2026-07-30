package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedModuleBrowserActionExecutionTest {
    @Test
    fun `open module result targets installed view and explains narrowed search`() {
        val result = UnifiedModuleBrowserActionPlanner.resultFor(
            UnifiedModuleBrowserAction(
                kind = UnifiedModuleBrowserActionKind.OPEN_MODULE,
                moduleId = "zygisk_lsposed",
                moduleTitle = "LSPosed",
            ),
        )

        assertTrue(result.handled)
        assertTrue(result.safe)
        assertEquals(UnifiedModuleBrowserActionDestination.INSTALLED_VIEW, result.destination)
        assertEquals(UnifiedModuleBrowserActionTone.INFO, result.tone)
        assertTrue(result.userMessage.contains("id:zygisk_lsposed"))
    }

    @Test
    fun `github source rules result is guided and non destructive`() {
        val result = UnifiedModuleBrowserActionPlanner.resultFor(
            UnifiedModuleBrowserAction(
                kind = UnifiedModuleBrowserActionKind.OPEN_GITHUB_SOURCE_RULES,
                moduleId = "githubish",
                moduleTitle = "GitHubish",
                detail = "Review artifact strategy before saving.",
            ),
        )

        assertFalse(result.handled)
        assertTrue(result.safe)
        assertTrue(result.hasGuidance)
        assertEquals(UnifiedModuleBrowserActionDestination.GITHUB_SOURCE_RULES, result.destination)
        assertTrue(result.userMessage.contains("Review artifact strategy"))
    }

    @Test
    fun `blocked result is explicit and unsafe to execute`() {
        val result = UnifiedModuleBrowserActionPlanner.blockedResult(
            UnifiedModuleBrowserAction(
                kind = UnifiedModuleBrowserActionKind.SUGGEST_FIX,
                label = "Pretend write",
            ),
        )

        assertFalse(result.handled)
        assertFalse(result.safe)
        assertEquals(UnifiedModuleBrowserActionTone.BLOCKED, result.tone)
        assertTrue(result.userMessage.contains("require an explicit confirmed flow"))
    }

    @Test
    fun `result summary reports guidance and clipboard state`() {
        val result = UnifiedModuleBrowserActionResult(
            handled = false,
            message = "Guided step",
            actionKind = UnifiedModuleBrowserActionKind.COPY_EVIDENCE,
            copiedText = "evidence",
            destination = UnifiedModuleBrowserActionDestination.DEBUG_WORKBENCH,
            followUp = "Open diagnostics.",
        )

        val summary = UnifiedModuleBrowserActionPlanner.resultSummary(result)

        assertTrue("Clipboard ready" in summary)
        assertTrue("Guided" in summary)
        assertTrue(summary.any { it.contains("Debug Workbench") })
    }
}
