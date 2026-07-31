package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedModuleBrowserReleaseSealTest {
    @Test
    fun `release seal summarizes phases ten through eighteen`() {
        val report = UnifiedModuleBrowserReleaseSeal.build()

        assertTrue(report.ready)
        assertEquals((10..18).map(Int::toString), report.phases.map { it.id })
        assertTrue(report.summary.contains("phases 10-18"))
        assertTrue(report.documents.contains("docs/UNIFIED_MODULE_BROWSER_RELEASE_NOTES.md"))
        assertTrue(UnifiedModuleBrowserReleaseSeal.docsAreComplete(report.documents))
    }

    @Test
    fun `release seal covers source lanes views and validation tasks`() {
        val report = UnifiedModuleBrowserReleaseSeal.build()

        assertTrue(report.sourceTypes.containsAll(UnifiedModuleSourceType.entries))
        assertTrue(report.views.containsAll(UnifiedModuleView.entries))
        assertEquals(
            listOf(":app:testOfficialDebugUnitTest", ":app:lintOfficialDebug"),
            report.validationTasks,
        )
        assertTrue(UnifiedModuleBrowserReleaseSeal.blockers(report).isEmpty())
    }

    @Test
    fun `release polish keeps destructive actions outside executable browser actions`() {
        val report = UnifiedModuleBrowserReleaseSeal.build()

        assertEquals(UnifiedModuleBrowserActionKind.entries.toSet(), report.actionKinds)
        assertTrue(report.blockedActionKinds.isEmpty())
        assertFalse(UnifiedModuleBrowserActionKind.entries.any { it.destructive })
        assertTrue(report.guardrails.any { it.contains("confirmed flow") })
    }

    @Test
    fun `release checks are explicit and human readable`() {
        val checks = UnifiedModuleBrowserReleaseSeal.checks()

        assertEquals(5, checks.size)
        assertTrue(checks.all { it.passed })
        assertTrue(checks.any { it.label == "Phase docs" })
        assertTrue(checks.any { it.label == "Safe action boundary" })
        assertTrue(checks.all { it.detail.isNotBlank() })
    }
}
