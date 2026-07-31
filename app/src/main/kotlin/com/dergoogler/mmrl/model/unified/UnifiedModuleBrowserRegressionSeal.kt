package com.dergoogler.mmrl.model.unified

/**
 * Phase 17 final integration seal for the unified module browser.
 *
 * The previous phases introduced the canonical rows, controls, problems, actions,
 * and presentation policy. This small pure model lets tests and future diagnostics
 * validate those pieces together without coupling the seal to Compose layout or
 * repository/network work.
 */
data class UnifiedModuleBrowserRegressionSealReport(
    val stats: UnifiedModuleBrowserStats,
    val viewCounts: Map<UnifiedModuleView, Int>,
    val sourceTypesCovered: Set<UnifiedModuleSourceType>,
    val sourceModesCovered: Set<UnifiedModuleSourceMode>,
    val problemReport: UnifiedModuleProblemReport,
    val actionKindsCovered: Set<UnifiedModuleBrowserActionKind>,
    val destructiveActionKinds: Set<UnifiedModuleBrowserActionKind>,
    val densityReports: Map<UnifiedModuleDensityMode, UnifiedModuleDensitySeal>,
) {
    val sealed: Boolean
        get() = destructiveActionKinds.isEmpty()

    val hasProblems: Boolean
        get() = problemReport.total > 0

    val summary: String
        get() = buildString {
            append("${stats.total} canonical rows")
            append(" · ${sourceTypesCovered.size} source types")
            append(" · ${actionKindsCovered.size} action kinds")
            if (problemReport.total > 0) append(" · ${problemReport.summary}")
            if (destructiveActionKinds.isNotEmpty()) {
                append(" · blocked destructive kinds: ${destructiveActionKinds.joinToString { it.name }}")
            }
        }
}

data class UnifiedModuleDensitySeal(
    val density: UnifiedModuleDensityMode,
    val badgeLimit: Int,
    val actionLimit: Int,
    val rowsWithHiddenBadges: Int,
    val rowsWithDiagnostics: Int,
)

object UnifiedModuleBrowserRegressionSeal {
    val expectedSourceTypes: Set<UnifiedModuleSourceType> = UnifiedModuleSourceType.entries.toSet()

    val fieldedSearchPrefixes: List<String> = listOf(
        "name",
        "id",
        "package",
        "alias",
        "author",
        "desc",
        "source",
        "repo",
        "folder",
        "scope",
        "badge",
        "problem",
        "issue",
        "severity",
    )

    fun build(
        items: List<UnifiedModuleItem>,
        controls: UnifiedModuleBrowserControlsState = UnifiedModuleBrowserControlsState(),
        signals: List<UnifiedProblemSignal> = emptyList(),
    ): UnifiedModuleBrowserRegressionSealReport {
        val problemReport = UnifiedModuleProblemCenter.build(items, signals)
        val actionKinds = itemActionKinds(items) + problemActionKinds(problemReport)
        val destructiveKinds = (actionKinds + UnifiedModuleBrowserActionKind.entries.filter { it.destructive })
            .filter { it.destructive }
            .toSet()

        return UnifiedModuleBrowserRegressionSealReport(
            stats = UnifiedModuleBrowserControls.stats(items),
            viewCounts = UnifiedModuleView.entries.associateWith { view ->
                UnifiedModuleBrowserControls.apply(
                    items = items,
                    controls = controls.copy(
                        view = view,
                        sortMode = UnifiedModuleBrowserControls.defaultSortForView(view),
                    ),
                ).size
            },
            sourceTypesCovered = items.flatMap { it.sourceTypes }.toSet(),
            sourceModesCovered = items.map { it.sourceMode }.toSet(),
            problemReport = problemReport,
            actionKindsCovered = actionKinds,
            destructiveActionKinds = destructiveKinds,
            densityReports = UnifiedModuleDensityMode.entries.associateWith { density ->
                densitySeal(items, density)
            },
        )
    }

    fun missingSourceTypes(report: UnifiedModuleBrowserRegressionSealReport): Set<UnifiedModuleSourceType> =
        expectedSourceTypes - report.sourceTypesCovered

    fun supportsFieldedSearch(prefix: String): Boolean =
        prefix.trim().removeSuffix(":").lowercase() in fieldedSearchPrefixes

    private fun itemActionKinds(items: List<UnifiedModuleItem>): Set<UnifiedModuleBrowserActionKind> = items
        .flatMap { item -> UnifiedModuleBrowserActionPlanner.forItem(item) }
        .map { it.kind }
        .toSet()

    private fun problemActionKinds(report: UnifiedModuleProblemReport): Set<UnifiedModuleBrowserActionKind> = report.problems
        .flatMap { problem ->
            problem.actions.map { action ->
                UnifiedModuleBrowserActionPlanner.forProblem(problem, action).kind
            }
        }
        .toSet()

    private fun densitySeal(
        items: List<UnifiedModuleItem>,
        density: UnifiedModuleDensityMode,
    ): UnifiedModuleDensitySeal {
        val cards = items.map { item -> UnifiedModuleBrowserPresentation.card(item, density) }
        return UnifiedModuleDensitySeal(
            density = density,
            badgeLimit = UnifiedModuleBrowserPresentation.badgeLimit(density),
            actionLimit = UnifiedModuleBrowserPresentation.actionLimit(density),
            rowsWithHiddenBadges = cards.count { it.hiddenBadgeCount > 0 },
            rowsWithDiagnostics = cards.count { it.diagnosticLines.isNotEmpty() },
        )
    }
}
