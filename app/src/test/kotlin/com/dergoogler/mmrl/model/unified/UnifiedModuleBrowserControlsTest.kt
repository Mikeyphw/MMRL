package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedModuleBrowserControlsTest {
    @Test
    fun `view buckets split installed repo updates scopes problems and github sources`() {
        val items = listOf(
            item(
                id = "installed",
                sourceTypes = setOf(UnifiedModuleSourceType.INSTALLED_ROOT),
                installState = UnifiedInstallState.INSTALLED,
            ),
            item(
                id = "repo",
                sourceTypes = setOf(UnifiedModuleSourceType.REPOSITORY),
                installState = UnifiedInstallState.AVAILABLE,
            ),
            item(
                id = "github",
                sourceTypes = setOf(UnifiedModuleSourceType.GITHUB_SOURCE),
                sourceMode = UnifiedModuleSourceMode.NIGHTLY,
                installState = UnifiedInstallState.INSTALLED,
            ),
            item(
                id = "scoped",
                sourceTypes = setOf(UnifiedModuleSourceType.LSPOSED_INSTALLED),
                installState = UnifiedInstallState.INSTALLED,
                scope = UnifiedScopeState.Lsposed(
                    enabled = true,
                    autoInclude = false,
                    scopedPackageCount = 2,
                    packages = listOf("android", "com.android.systemui"),
                ),
            ),
            item(
                id = "broken",
                sourceTypes = setOf(UnifiedModuleSourceType.LOCAL_FILE),
                installState = UnifiedInstallState.PROBLEM,
                badges = listOf(
                    UnifiedModuleBadge(
                        kind = UnifiedBadgeKind.PROBLEM,
                        label = "Quarantined",
                        severity = UnifiedBadgeSeverity.ERROR,
                    ),
                ),
            ),
        )

        val stats = UnifiedModuleBrowserControls.stats(items)
        assertEquals(3, stats.installed)
        assertEquals(1, stats.repository)
        assertEquals(1, stats.scopes)
        assertEquals(1, stats.problems)
        assertEquals(1, stats.githubSources)

        val problems = UnifiedModuleBrowserControls.apply(
            items,
            UnifiedModuleBrowserControlsState(view = UnifiedModuleView.PROBLEMS),
        )
        assertEquals(listOf("broken"), problems.map { it.canonicalId })
    }

    @Test
    fun `fielded search covers aliases author source badges and scope packages`() {
        val scoped = item(
            id = "io.github.demo.hooks",
            title = "Demo Hooks",
            author = "Example Author",
            aliases = setOf("demo-hooks", "system-hooks"),
            sourceUrl = "https://github.com/example/demo-hooks",
            repositoryName = "Example GitHub",
            sourceTypes = setOf(UnifiedModuleSourceType.LSPOSED_INSTALLED),
            installState = UnifiedInstallState.INSTALLED,
            scope = UnifiedScopeState.Lsposed(
                enabled = true,
                autoInclude = false,
                scopedPackageCount = 2,
                packages = listOf("android", "com.android.systemui"),
            ),
            badges = listOf(
                UnifiedModuleBadge(
                    kind = UnifiedBadgeKind.PROVIDER_COMPATIBILITY,
                    label = "Compatible",
                    detail = "Provider bridge available",
                    severity = UnifiedBadgeSeverity.SUCCESS,
                ),
            ),
        )
        val other = item(id = "other", sourceTypes = setOf(UnifiedModuleSourceType.REPOSITORY))
        val items = listOf(scoped, other)

        assertEquals(listOf("io.github.demo.hooks"), search(items, "alias:system-hooks").map { it.canonicalId })
        assertEquals(listOf("io.github.demo.hooks"), search(items, "author:example").map { it.canonicalId })
        assertEquals(listOf("io.github.demo.hooks"), search(items, "source:demo-hooks").map { it.canonicalId })
        assertEquals(listOf("io.github.demo.hooks"), search(items, "scope:systemui").map { it.canonicalId })
        assertEquals(listOf("io.github.demo.hooks"), search(items, "badge:bridge").map { it.canonicalId })
        assertTrue(search(items, "missing").isEmpty())
    }

    @Test
    fun `filters combine source mode scope provider install and health`() {
        val goodNightly = item(
            id = "good",
            sourceTypes = setOf(UnifiedModuleSourceType.GITHUB_SOURCE),
            sourceMode = UnifiedModuleSourceMode.NIGHTLY,
            installState = UnifiedInstallState.UPDATE_AVAILABLE,
            providerCompatibility = UnifiedProviderCompatibility.COMPATIBLE,
        )
        val disabledScopedWarning = item(
            id = "warning",
            sourceTypes = setOf(UnifiedModuleSourceType.LSPOSED_INSTALLED),
            installState = UnifiedInstallState.DISABLED,
            providerCompatibility = UnifiedProviderCompatibility.LIMITED,
            scope = UnifiedScopeState.Lsposed(
                enabled = false,
                autoInclude = true,
                scopedPackageCount = 1,
                packages = listOf("android"),
            ),
            badges = listOf(
                UnifiedModuleBadge(
                    kind = UnifiedBadgeKind.SCOPE,
                    label = "Scope disabled",
                    severity = UnifiedBadgeSeverity.WARNING,
                ),
            ),
        )
        val items = listOf(goodNightly, disabledScopedWarning)

        val nightly = UnifiedModuleBrowserControls.apply(
            items,
            UnifiedModuleBrowserControlsState(
                view = UnifiedModuleView.GITHUB_SOURCES,
                sourceModes = setOf(UnifiedModuleSourceMode.NIGHTLY),
                installStates = setOf(UnifiedInstallState.UPDATE_AVAILABLE),
                providerStates = setOf(UnifiedProviderCompatibility.COMPATIBLE),
            ),
        )
        assertEquals(listOf("good"), nightly.map { it.canonicalId })

        val scopedWarnings = UnifiedModuleBrowserControls.apply(
            items,
            UnifiedModuleBrowserControlsState(
                view = UnifiedModuleView.SCOPES,
                scopeFilter = UnifiedScopeFilter.AUTO_INCLUDE,
                healthFilter = UnifiedModuleHealthFilter.WARNINGS,
            ),
        )
        assertEquals(listOf("warning"), scopedWarnings.map { it.canonicalId })
    }

    @Test
    fun `density and defaults are stable contracts for compose rows`() {
        assertEquals(UnifiedModuleSortMode.UPDATE_AVAILABLE_FIRST, UnifiedModuleBrowserControls.defaultSortForView(UnifiedModuleView.UPDATES))
        assertEquals(UnifiedModuleSortMode.PROBLEM_SEVERITY, UnifiedModuleBrowserControls.defaultSortForView(UnifiedModuleView.PROBLEMS))
        assertEquals(1, UnifiedModuleDensityMode.COMPACT.maxDescriptionLines)
        assertFalse(UnifiedModuleDensityMode.COMPACT.showDiagnostics)
        assertTrue(UnifiedModuleDensityMode.DIAGNOSTIC.showDiagnostics)
    }

    private fun search(
        items: List<UnifiedModuleItem>,
        text: String,
    ): List<UnifiedModuleItem> = UnifiedModuleBrowserControls.apply(
        items,
        UnifiedModuleBrowserControlsState(
            view = UnifiedModuleView.SCOPES,
            searchText = text,
        ),
    )

    private fun item(
        id: String,
        title: String = id,
        author: String? = null,
        sourceTypes: Set<UnifiedModuleSourceType> = setOf(UnifiedModuleSourceType.REPOSITORY),
        sourceMode: UnifiedModuleSourceMode = UnifiedModuleSourceMode.REPOSITORY,
        sourceUrl: String? = null,
        repositoryName: String? = null,
        aliases: Set<String> = emptySet(),
        installState: UnifiedInstallState = UnifiedInstallState.AVAILABLE,
        providerCompatibility: UnifiedProviderCompatibility = UnifiedProviderCompatibility.NOT_APPLICABLE,
        scope: UnifiedScopeState = UnifiedScopeState.None,
        badges: List<UnifiedModuleBadge> = emptyList(),
    ): UnifiedModuleItem {
        val normalizedBadges = listOf(
            UnifiedModuleBadge(
                kind = UnifiedBadgeKind.INSTALL_STATE,
                label = installState.label,
            ),
        ) + badges
        return UnifiedModuleItem(
            canonicalId = id,
            displayId = id,
            title = title,
            subtitle = installState.label,
            description = "Description for $title",
            author = author,
            sourceTypes = sourceTypes,
            sourceMode = sourceMode,
            sourceUrl = sourceUrl,
            repositoryName = repositoryName,
            aliases = aliases,
            state = UnifiedModuleState(
                installState = installState,
                providerCompatibility = providerCompatibility,
                scope = scope,
            ),
            match = UnifiedModuleMatch(
                reason = UnifiedMatchReason.EXACT_ID,
                confidence = 100,
                explanation = "Synthetic item for controls contracts.",
                matchedValues = setOf(id),
            ),
            badges = normalizedBadges,
            searchTokens = setOf(
                id,
                title,
                author.orEmpty(),
                sourceUrl.orEmpty(),
                repositoryName.orEmpty(),
            ) + aliases + when (scope) {
                is UnifiedScopeState.Lsposed -> scope.packages
                UnifiedScopeState.None -> emptyList()
            },
            sort = UnifiedModuleSortKeys(
                name = title.lowercase(),
                scopeCount = when (scope) {
                    is UnifiedScopeState.Lsposed -> scope.scopedPackageCount
                    UnifiedScopeState.None -> 0
                },
                problemSeverity = normalizedBadges.maxByOrNull { it.severity.ordinal }?.severity ?: UnifiedBadgeSeverity.INFO,
            ),
        )
    }
}
