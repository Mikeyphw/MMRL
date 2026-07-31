package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedModuleBrowserPresentationTest {
    @Test
    fun `badge hierarchy promotes problems warnings and action signals first`() {
        val badges = listOf(
            UnifiedModuleBadge(
                kind = UnifiedBadgeKind.INSTALL_STATE,
                label = "Installed",
                severity = UnifiedBadgeSeverity.INFO,
            ),
            UnifiedModuleBadge(
                kind = UnifiedBadgeKind.SCOPE,
                label = "Scope disabled",
                severity = UnifiedBadgeSeverity.WARNING,
            ),
            UnifiedModuleBadge(
                kind = UnifiedBadgeKind.PROBLEM,
                label = "Provider unavailable",
                severity = UnifiedBadgeSeverity.ERROR,
            ),
            UnifiedModuleBadge(
                kind = UnifiedBadgeKind.UPDATE,
                label = "Update available",
                severity = UnifiedBadgeSeverity.SUCCESS,
            ),
        )

        val ordered = UnifiedModuleBrowserPresentation.orderedBadges(badges)

        assertEquals("Provider unavailable", ordered[0].label)
        assertEquals("Scope disabled", ordered[1].label)
        assertEquals("Update available", ordered[2].label)
        assertEquals("Installed", ordered[3].label)
    }

    @Test
    fun `density drives badge metadata action and diagnostic presentation`() {
        val item = item(
            badges = listOf(
                UnifiedModuleBadge(UnifiedBadgeKind.PROBLEM, "Provider unavailable", severity = UnifiedBadgeSeverity.ERROR),
                UnifiedModuleBadge(UnifiedBadgeKind.UPDATE, "Update available", severity = UnifiedBadgeSeverity.SUCCESS),
                UnifiedModuleBadge(UnifiedBadgeKind.SCOPE, "2 scoped apps", severity = UnifiedBadgeSeverity.INFO),
                UnifiedModuleBadge(UnifiedBadgeKind.ARTIFACT_STRATEGY, "Direct module ZIP", severity = UnifiedBadgeSeverity.INFO),
                UnifiedModuleBadge(UnifiedBadgeKind.SOURCE_MODE, "Nightly", severity = UnifiedBadgeSeverity.INFO),
            ),
        )

        val compact = UnifiedModuleBrowserPresentation.card(item, UnifiedModuleDensityMode.COMPACT)
        val diagnostic = UnifiedModuleBrowserPresentation.card(item, UnifiedModuleDensityMode.DIAGNOSTIC)

        assertEquals(3, compact.badges.size)
        assertEquals(2, compact.hiddenBadgeCount)
        assertTrue(compact.metadataPills.size <= 3)
        assertEquals(2, compact.actionLimit)
        assertTrue(compact.diagnosticLines.isEmpty())

        assertEquals(0, diagnostic.hiddenBadgeCount)
        assertTrue(diagnostic.diagnosticLines.any { it.label == "Match" })
        assertTrue(diagnostic.diagnosticLines.any { it.label == "Scope" })
    }

    @Test
    fun `empty state copy distinguishes filtered and plain buckets`() {
        val filtered = UnifiedModuleBrowserPresentation.emptyState(
            UnifiedModuleBrowserControlsState(
                view = UnifiedModuleView.PROBLEMS,
                searchText = "severity:error",
            ),
        )
        val plain = UnifiedModuleBrowserPresentation.emptyState(
            UnifiedModuleBrowserControlsState(view = UnifiedModuleView.PROBLEMS),
        )

        assertTrue(filtered.canClearFilters)
        assertTrue(filtered.title.contains("match"))
        assertTrue(filtered.suggestions.contains("Clear filters"))

        assertFalse(plain.canClearFilters)
        assertTrue(plain.body.contains("Warnings and errors"))
    }

    private fun item(
        badges: List<UnifiedModuleBadge>,
    ): UnifiedModuleItem = UnifiedModuleItem(
        canonicalId = "io.github.demo.hooks",
        displayId = "io.github.demo.hooks",
        title = "Demo Hooks",
        subtitle = "Installed from GitHub",
        description = "Demo module used by the Phase 16 presentation contract.",
        author = "Tester",
        sourceTypes = setOf(
            UnifiedModuleSourceType.INSTALLED_ROOT,
            UnifiedModuleSourceType.GITHUB_SOURCE,
            UnifiedModuleSourceType.LSPOSED_INSTALLED,
        ),
        sourceMode = UnifiedModuleSourceMode.MIXED,
        sourceUrl = "https://github.com/example/demo-hooks",
        repositoryName = "Example GitHub",
        aliases = setOf("demo-hooks"),
        state = UnifiedModuleState(
            installState = UnifiedInstallState.UPDATE_AVAILABLE,
            providerCompatibility = UnifiedProviderCompatibility.LIMITED,
            scope = UnifiedScopeState.Lsposed(
                enabled = true,
                autoInclude = false,
                scopedPackageCount = 2,
                packages = listOf("android", "com.android.systemui"),
            ),
        ),
        match = UnifiedModuleMatch(
            reason = UnifiedMatchReason.EXACT_ID,
            confidence = 100,
            explanation = "Synthetic row for presentation tests.",
            matchedValues = setOf("io.github.demo.hooks"),
        ),
        badges = badges,
        searchTokens = setOf("io.github.demo.hooks", "demo-hooks", "com.android.systemui"),
    )
}
