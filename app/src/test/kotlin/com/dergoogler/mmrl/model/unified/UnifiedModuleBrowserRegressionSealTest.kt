package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedModuleBrowserRegressionSealTest {
    @Test
    fun `seal covers installed repo github lsposed and local lanes without recovery coupling`() {
        val report = UnifiedModuleBrowserRegressionSeal.build(crossSourceItems())

        assertTrue(report.sealed)
        assertTrue(UnifiedModuleBrowserRegressionSeal.missingSourceTypes(report).isEmpty())
        assertEquals(UnifiedModuleSourceType.entries.size, report.sourceTypesCovered.size)
        assertTrue(report.viewCounts.getValue(UnifiedModuleView.INSTALLED) > 0)
        assertTrue(report.viewCounts.getValue(UnifiedModuleView.REPOSITORY) > 0)
        assertTrue(report.viewCounts.getValue(UnifiedModuleView.UPDATES) > 0)
        assertTrue(report.viewCounts.getValue(UnifiedModuleView.SCOPES) > 0)
        assertTrue(report.viewCounts.getValue(UnifiedModuleView.PROBLEMS) > 0)
        assertTrue(report.viewCounts.getValue(UnifiedModuleView.GITHUB_SOURCES) > 0)
        assertTrue(report.summary.contains("canonical rows"))
    }

    @Test
    fun `seal keeps action coverage useful while destructive actions stay absent`() {
        val report = UnifiedModuleBrowserRegressionSeal.build(crossSourceItems())

        assertTrue(report.actionKindsCovered.contains(UnifiedModuleBrowserActionKind.OPEN_MODULE))
        assertTrue(report.actionKindsCovered.contains(UnifiedModuleBrowserActionKind.COPY_SOURCE_URL))
        assertTrue(report.actionKindsCovered.contains(UnifiedModuleBrowserActionKind.OPEN_GITHUB_SOURCE_RULES))
        assertTrue(report.actionKindsCovered.contains(UnifiedModuleBrowserActionKind.REFRESH_REPOSITORY))
        assertTrue(report.actionKindsCovered.contains(UnifiedModuleBrowserActionKind.REFRESH_PROVIDER))
        assertTrue(report.actionKindsCovered.contains(UnifiedModuleBrowserActionKind.REVIEW_SCOPE))
        assertTrue(report.actionKindsCovered.contains(UnifiedModuleBrowserActionKind.COPY_EVIDENCE))
        assertTrue(report.actionKindsCovered.contains(UnifiedModuleBrowserActionKind.RUN_DEBUG_PROBE))
        assertTrue(report.destructiveActionKinds.isEmpty())
        assertFalse(UnifiedModuleBrowserActionKind.entries.any { it.destructive })
    }

    @Test
    fun `seal verifies density behavior without snapshotting compose layout`() {
        val report = UnifiedModuleBrowserRegressionSeal.build(crossSourceItems())
        val compact = report.densityReports.getValue(UnifiedModuleDensityMode.COMPACT)
        val diagnostic = report.densityReports.getValue(UnifiedModuleDensityMode.DIAGNOSTIC)

        assertEquals(3, compact.badgeLimit)
        assertEquals(2, compact.actionLimit)
        assertTrue(compact.rowsWithHiddenBadges > 0)
        assertEquals(0, compact.rowsWithDiagnostics)
        assertEquals(Int.MAX_VALUE, diagnostic.badgeLimit)
        assertEquals(Int.MAX_VALUE, diagnostic.actionLimit)
        assertTrue(diagnostic.rowsWithDiagnostics > 0)
    }

    @Test
    fun `seal keeps fielded search prefixes synchronized with presentation help`() {
        val items = crossSourceItems()
        val chrome = UnifiedModuleBrowserPresentation.chrome(
            controls = UnifiedModuleBrowserControlsState(),
            stats = UnifiedModuleBrowserControls.stats(items),
            shownCount = items.size,
        )

        listOf("name", "id", "alias", "source", "scope", "badge", "problem", "severity").forEach { prefix ->
            assertTrue(UnifiedModuleBrowserRegressionSeal.supportsFieldedSearch(prefix))
            assertTrue(chrome.searchHelp.contains("$prefix:"))
        }
        assertFalse(UnifiedModuleBrowserRegressionSeal.supportsFieldedSearch("write_scope"))
    }

    private fun crossSourceItems(): List<UnifiedModuleItem> = listOf(
        item(
            id = "zygisk_lsposed", title = "LSPosed",
            sourceTypes = setOf(UnifiedModuleSourceType.INSTALLED_ROOT, UnifiedModuleSourceType.REPOSITORY),
            sourceMode = UnifiedModuleSourceMode.MIXED,
            sourceUrl = "https://repo.example/lsposed.json",
            installState = UnifiedInstallState.UPDATE_AVAILABLE,
            badges = richBadges(
                UnifiedModuleBadge(UnifiedBadgeKind.UPDATE, "Update available", severity = UnifiedBadgeSeverity.SUCCESS),
                UnifiedModuleBadge(UnifiedBadgeKind.PROVIDER_COMPATIBILITY, "Compatible", severity = UnifiedBadgeSeverity.SUCCESS),
            ),
        ),
        item(
            id = "githubish", title = "GitHubish",
            sourceTypes = setOf(UnifiedModuleSourceType.GITHUB_SOURCE, UnifiedModuleSourceType.REPOSITORY),
            sourceMode = UnifiedModuleSourceMode.NIGHTLY,
            sourceUrl = "https://github.com/example/githubish",
            badges = richBadges(UnifiedModuleBadge(UnifiedBadgeKind.ARTIFACT_STRATEGY, "Direct module ZIP")),
        ),
        item(
            id = "io.github.demo.hooks", title = "Demo Hooks",
            sourceTypes = setOf(UnifiedModuleSourceType.LSPOSED_INSTALLED, UnifiedModuleSourceType.LSPOSED_REPOSITORY),
            sourceMode = UnifiedModuleSourceMode.INSTALLED,
            sourceUrl = "https://github.com/example/demo-hooks",
            installState = UnifiedInstallState.INSTALLED,
            providerCompatibility = UnifiedProviderCompatibility.UNAVAILABLE,
            scope = UnifiedScopeState.Lsposed(true, false, 2, listOf("android", "com.android.systemui")),
            badges = richBadges(
                UnifiedModuleBadge(UnifiedBadgeKind.PROBLEM, "Provider unavailable", severity = UnifiedBadgeSeverity.ERROR),
                UnifiedModuleBadge(UnifiedBadgeKind.SCOPE, "2 scoped apps"),
            ),
        ),
        item(
            id = "localzip", title = "Local ZIP",
            sourceTypes = setOf(UnifiedModuleSourceType.LOCAL_FILE),
            sourceMode = UnifiedModuleSourceMode.LOCAL,
            installState = UnifiedInstallState.AVAILABLE,
            badges = richBadges(UnifiedModuleBadge(UnifiedBadgeKind.SOURCE_MODE, "Local")),
        ),
    )

    private fun item(
        id: String, title: String, sourceTypes: Set<UnifiedModuleSourceType>, sourceMode: UnifiedModuleSourceMode,
        sourceUrl: String? = null, installState: UnifiedInstallState = UnifiedInstallState.AVAILABLE,
        providerCompatibility: UnifiedProviderCompatibility = UnifiedProviderCompatibility.NOT_APPLICABLE,
        scope: UnifiedScopeState = UnifiedScopeState.None,
        match: UnifiedModuleMatch = UnifiedModuleMatch(UnifiedMatchReason.EXACT_ID, 100, "Synthetic exact match.", setOf(id)),
        aliases: Set<String> = emptySet(), badges: List<UnifiedModuleBadge>,
    ): UnifiedModuleItem = UnifiedModuleItem(
        canonicalId = id.lowercase(), displayId = id, title = title, subtitle = installState.label,
        description = "Regression seal row for $title.", author = "Tester", sourceTypes = sourceTypes,
        sourceMode = sourceMode, sourceUrl = sourceUrl, repositoryName = "Regression repo", artifactStrategy = null,
        aliases = aliases, state = UnifiedModuleState(installState = installState, providerCompatibility = providerCompatibility, scope = scope),
        match = match, badges = badges,
        searchTokens = setOf(id, title, sourceUrl.orEmpty(), "Regression repo") + aliases + when (scope) {
            is UnifiedScopeState.Lsposed -> scope.packages
            UnifiedScopeState.None -> emptyList()
        },
    )

    private fun richBadges(vararg badges: UnifiedModuleBadge): List<UnifiedModuleBadge> = badges.toList() + listOf(
        UnifiedModuleBadge(UnifiedBadgeKind.INSTALL_STATE, "State"),
        UnifiedModuleBadge(UnifiedBadgeKind.SOURCE_MODE, "Mode"),
        UnifiedModuleBadge(UnifiedBadgeKind.ARTIFACT_STRATEGY, "Artifact"),
        UnifiedModuleBadge(UnifiedBadgeKind.SCOPE, "Scope"),
        UnifiedModuleBadge(UnifiedBadgeKind.PROBLEM, "Review", severity = UnifiedBadgeSeverity.WARNING),
    )
}
