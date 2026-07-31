package com.dergoogler.mmrl.model.unified

/**
 * Phase 16 presentation contract for the unified browser.
 *
 * This file keeps visual policy out of Compose call sites: badge order,
 * empty-state copy, density limits, metadata rows, and diagnostics are derived
 * from canonical module rows in one deterministic place. The UI stays thin and
 * Phase 17 can seal behavior without snapshotting layout trivia.
 */
enum class UnifiedBadgeEmphasis {
    HIGH,
    MEDIUM,
    LOW,
}

data class UnifiedBadgePresentation(
    val badge: UnifiedModuleBadge,
    val emphasis: UnifiedBadgeEmphasis,
)

data class UnifiedDiagnosticLine(
    val label: String,
    val value: String,
)

data class UnifiedModuleCardPresentation(
    val title: String,
    val subtitle: String,
    val description: String,
    val stateLabel: String,
    val metadataPills: List<String>,
    val badges: List<UnifiedBadgePresentation>,
    val hiddenBadgeCount: Int,
    val actionLimit: Int,
    val diagnosticLines: List<UnifiedDiagnosticLine>,
)

data class UnifiedBrowserChromePresentation(
    val title: String,
    val summary: String,
    val statPills: List<String>,
    val searchHelp: String,
)

data class UnifiedBrowserEmptyPresentation(
    val title: String,
    val body: String,
    val suggestions: List<String>,
    val canClearFilters: Boolean,
)

object UnifiedModuleBrowserPresentation {
    fun chrome(
        controls: UnifiedModuleBrowserControlsState,
        stats: UnifiedModuleBrowserStats,
        shownCount: Int,
    ): UnifiedBrowserChromePresentation = UnifiedBrowserChromePresentation(
        title = "Unified browser",
        summary = buildString {
            append("${shownCount} shown from ${stats.total} canonical rows")
            if (stats.updates > 0) append(" · ${stats.updates} updates")
            if (stats.problems > 0) append(" · ${stats.problems} problems")
            if (controls.hasExplicitFilters) append(" · filtered")
        },
        statPills = listOf(
            "Installed ${stats.installed}",
            "Repo ${stats.repository}",
            "Updates ${stats.updates}",
            "Scopes ${stats.scopes}",
            "Problems ${stats.problems}",
            "GitHub ${stats.githubSources}",
        ),
        searchHelp = "Fields: name:, id:, alias:, source:, scope:, badge:, problem:, severity:",
    )

    fun emptyState(controls: UnifiedModuleBrowserControlsState): UnifiedBrowserEmptyPresentation {
        val viewLabel = controls.view.label.lowercase()
        return if (controls.hasExplicitFilters) {
            UnifiedBrowserEmptyPresentation(
                title = "No $viewLabel rows match these controls",
                body = "Clear filters or loosen search. Fielded search accepts id:, alias:, source:, scope:, badge:, problem:, and severity:.",
                suggestions = listOf(
                    "Clear filters",
                    sampleSearchFor(controls.view),
                    "Switch view",
                ),
                canClearFilters = true,
            )
        } else {
            UnifiedBrowserEmptyPresentation(
                title = "No $viewLabel rows yet",
                body = emptyBodyFor(controls.view),
                suggestions = listOf(
                    sampleSearchFor(controls.view),
                    "Refresh evidence",
                    "Check Problems",
                ),
                canClearFilters = false,
            )
        }
    }

    fun card(
        item: UnifiedModuleItem,
        density: UnifiedModuleDensityMode,
    ): UnifiedModuleCardPresentation {
        val orderedBadges = orderedBadges(item.badges)
        val visibleBadges = orderedBadges.take(badgeLimit(density))
        return UnifiedModuleCardPresentation(
            title = item.title,
            subtitle = item.subtitle.ifBlank { item.displayId },
            description = item.description,
            stateLabel = item.state.installState.label,
            metadataPills = metadataPills(item, density),
            badges = visibleBadges.mapIndexed { index, badge ->
                UnifiedBadgePresentation(
                    badge = badge,
                    emphasis = emphasisFor(badge, index),
                )
            },
            hiddenBadgeCount = (orderedBadges.size - visibleBadges.size).coerceAtLeast(0),
            actionLimit = actionLimit(density),
            diagnosticLines = if (density.showDiagnostics) diagnosticsFor(item) else emptyList(),
        )
    }

    fun orderedBadges(badges: List<UnifiedModuleBadge>): List<UnifiedModuleBadge> = badges.sortedWith(
        compareByDescending<UnifiedModuleBadge> { it.severity.score }
            .thenBy { it.kind.priority }
            .thenBy { it.label.lowercase() }
            .thenBy { it.detail.lowercase() },
    )

    fun badgeLimit(density: UnifiedModuleDensityMode): Int = when (density) {
        UnifiedModuleDensityMode.COMPACT -> 3
        UnifiedModuleDensityMode.COMFORTABLE -> 6
        UnifiedModuleDensityMode.DIAGNOSTIC -> Int.MAX_VALUE
    }

    fun actionLimit(density: UnifiedModuleDensityMode): Int = when (density) {
        UnifiedModuleDensityMode.COMPACT -> 2
        UnifiedModuleDensityMode.COMFORTABLE -> 4
        UnifiedModuleDensityMode.DIAGNOSTIC -> Int.MAX_VALUE
    }

    private fun metadataPills(
        item: UnifiedModuleItem,
        density: UnifiedModuleDensityMode,
    ): List<String> {
        val base = buildList {
            add(item.sourceMode.label)
            item.repositoryName?.takeIf(String::isNotBlank)?.let(::add)
            item.sourceTypes.forEach { add(it.label) }
            if (item.state.providerCompatibility != UnifiedProviderCompatibility.NOT_APPLICABLE) {
                add(item.state.providerCompatibility.label)
            }
            when (val scope = item.state.scope) {
                is UnifiedScopeState.Lsposed -> add("${scope.scopedPackageCount} scoped apps")
                UnifiedScopeState.None -> Unit
            }
        }.distinct()
        return when (density) {
            UnifiedModuleDensityMode.COMPACT -> base.take(3)
            UnifiedModuleDensityMode.COMFORTABLE -> base.take(6)
            UnifiedModuleDensityMode.DIAGNOSTIC -> base
        }
    }

    private fun diagnosticsFor(item: UnifiedModuleItem): List<UnifiedDiagnosticLine> = buildList {
        add(UnifiedDiagnosticLine("ID", item.displayId))
        add(UnifiedDiagnosticLine("Canonical", item.canonicalId))
        add(UnifiedDiagnosticLine("Match", "${item.match.reason} · ${item.match.confidence}% · ${item.match.explanation}"))
        if (item.aliases.isNotEmpty()) {
            add(UnifiedDiagnosticLine("Aliases", item.aliases.sorted().joinToString()))
        }
        item.sourceUrl?.takeIf(String::isNotBlank)?.let { add(UnifiedDiagnosticLine("Source", it)) }
        when (val scope = item.state.scope) {
            is UnifiedScopeState.Lsposed -> add(
                UnifiedDiagnosticLine(
                    "Scope",
                    buildString {
                        append("${scope.scopedPackageCount} packages · ")
                        append(if (scope.enabled) "enabled" else "disabled")
                        if (scope.autoInclude) append(" · auto")
                    },
                ),
            )
            UnifiedScopeState.None -> Unit
        }
    }

    private fun emphasisFor(
        badge: UnifiedModuleBadge,
        index: Int,
    ): UnifiedBadgeEmphasis = when {
        badge.severity >= UnifiedBadgeSeverity.WARNING -> UnifiedBadgeEmphasis.HIGH
        index <= 1 -> UnifiedBadgeEmphasis.MEDIUM
        else -> UnifiedBadgeEmphasis.LOW
    }

    private fun sampleSearchFor(view: UnifiedModuleView): String = when (view) {
        UnifiedModuleView.INSTALLED -> "id:lsposed"
        UnifiedModuleView.REPOSITORY -> "source:repo"
        UnifiedModuleView.UPDATES -> "badge:update"
        UnifiedModuleView.SCOPES -> "scope:systemui"
        UnifiedModuleView.PROBLEMS -> "severity:error"
        UnifiedModuleView.GITHUB_SOURCES -> "source:github"
    }

    private fun emptyBodyFor(view: UnifiedModuleView): String = when (view) {
        UnifiedModuleView.INSTALLED -> "Installed root and LSPosed rows appear here after module evidence is loaded."
        UnifiedModuleView.REPOSITORY -> "Repository rows appear here after repo sources finish loading."
        UnifiedModuleView.UPDATES -> "Modules with update, locked, ignored, or pending-update evidence appear here."
        UnifiedModuleView.SCOPES -> "LSPosed rows with package scope evidence appear here."
        UnifiedModuleView.PROBLEMS -> "Warnings and errors from repo, GitHub, provider, scope, and rescue evidence appear here."
        UnifiedModuleView.GITHUB_SOURCES -> "Saved GitHub module sources appear here after local sources are read."
    }

    private val UnifiedBadgeSeverity.score: Int
        get() = when (this) {
            UnifiedBadgeSeverity.ERROR -> 4
            UnifiedBadgeSeverity.WARNING -> 3
            UnifiedBadgeSeverity.SUCCESS -> 2
            UnifiedBadgeSeverity.INFO -> 1
        }

    private val UnifiedBadgeKind.priority: Int
        get() = when (this) {
            UnifiedBadgeKind.PROBLEM -> 0
            UnifiedBadgeKind.UPDATE -> 1
            UnifiedBadgeKind.PROVIDER_COMPATIBILITY -> 2
            UnifiedBadgeKind.RESCUE -> 3
            UnifiedBadgeKind.SCOPE -> 4
            UnifiedBadgeKind.ARTIFACT_STRATEGY -> 5
            UnifiedBadgeKind.SOURCE_MODE -> 6
            UnifiedBadgeKind.INSTALL_STATE -> 7
        }
}
