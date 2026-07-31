package com.dergoogler.mmrl.model.unified

/**
 * Phase 18 release polish seal for the unified module browser.
 *
 * The regression seal proves behavior. This release seal documents the shipped
 * surface area and keeps final release notes, phase docs, source lanes, actions,
 * and guardrails aligned without touching Compose or provider execution paths.
 */
data class UnifiedModuleBrowserReleasePhase(
    val id: String,
    val title: String,
    val layer: String,
    val document: String,
)

data class UnifiedModuleBrowserReleaseCheck(
    val label: String,
    val passed: Boolean,
    val detail: String,
)

data class UnifiedModuleBrowserReleaseSealReport(
    val phases: List<UnifiedModuleBrowserReleasePhase>,
    val sourceTypes: Set<UnifiedModuleSourceType>,
    val views: Set<UnifiedModuleView>,
    val actionKinds: Set<UnifiedModuleBrowserActionKind>,
    val documents: List<String>,
    val validationTasks: List<String>,
    val guardrails: List<String>,
) {
    val blockedActionKinds: Set<UnifiedModuleBrowserActionKind>
        get() = actionKinds.filter { it.destructive }.toSet()

    val ready: Boolean
        get() = phases.map { it.id } == UnifiedModuleBrowserReleaseSeal.phaseIds &&
            sourceTypes.containsAll(UnifiedModuleSourceType.entries) &&
            views.containsAll(UnifiedModuleView.entries) &&
            blockedActionKinds.isEmpty() &&
            UnifiedModuleBrowserReleaseSeal.requiredDocuments.all(documents::contains)

    val summary: String
        get() = buildString {
            append("Unified browser release polish")
            append(" · phases ${phases.firstOrNull()?.id.orEmpty()}-${phases.lastOrNull()?.id.orEmpty()}")
            append(" · ${sourceTypes.size} source lanes")
            append(" · ${views.size} views")
            append(" · ${actionKinds.size} safe action kinds")
            if (blockedActionKinds.isNotEmpty()) {
                append(" · blocked: ${blockedActionKinds.joinToString { it.name }}")
            }
        }
}

object UnifiedModuleBrowserReleaseSeal {
    val phaseIds: List<String> = (10..18).map(Int::toString)

    val requiredDocuments: List<String> = phaseIds.map { phase ->
        "docs/UNIFIED_MODULE_BROWSER_PHASE$phase.md"
    } + "docs/UNIFIED_MODULE_BROWSER_RELEASE_NOTES.md"

    val validationTasks: List<String> = listOf(
        ":app:testOfficialDebugUnitTest",
        ":app:lintOfficialDebug",
    )

    val safetyGuardrails: List<String> = listOf(
        "The unified browser may copy evidence, guide review, refresh evidence, and narrow search.",
        "Install, remove, enable, disable, and LSPosed scope writes stay out of the unified browser safe-action layer.",
        "Any future mutating operation must be modeled as a confirmed flow before it is executable.",
    )

    fun build(
        regressionReport: UnifiedModuleBrowserRegressionSealReport? = null,
    ): UnifiedModuleBrowserReleaseSealReport = UnifiedModuleBrowserReleaseSealReport(
        phases = phases(),
        sourceTypes = regressionReport?.sourceTypesCovered ?: UnifiedModuleSourceType.entries.toSet(),
        views = regressionReport?.viewCounts?.keys ?: UnifiedModuleView.entries.toSet(),
        actionKinds = regressionReport?.actionKindsCovered?.ifEmpty { safeActionKinds() } ?: safeActionKinds(),
        documents = requiredDocuments,
        validationTasks = validationTasks,
        guardrails = safetyGuardrails,
    )

    fun checks(report: UnifiedModuleBrowserReleaseSealReport = build()): List<UnifiedModuleBrowserReleaseCheck> = listOf(
        UnifiedModuleBrowserReleaseCheck(
            label = "Phase docs",
            passed = report.phases.map { it.id } == phaseIds && requiredDocuments.all(report.documents::contains),
            detail = "Phase 10-18 docs and release notes are indexed.",
        ),
        UnifiedModuleBrowserReleaseCheck(
            label = "Source lanes",
            passed = report.sourceTypes.containsAll(UnifiedModuleSourceType.entries),
            detail = "Installed, repo, GitHub, LSPosed, local, and rescue lanes are represented.",
        ),
        UnifiedModuleBrowserReleaseCheck(
            label = "Views",
            passed = report.views.containsAll(UnifiedModuleView.entries),
            detail = "Installed, Repo, Updates, Scopes, Problems, and GitHub Sources are represented.",
        ),
        UnifiedModuleBrowserReleaseCheck(
            label = "Safe action boundary",
            passed = report.blockedActionKinds.isEmpty(),
            detail = "No destructive browser action kind is release-executable.",
        ),
        UnifiedModuleBrowserReleaseCheck(
            label = "Validation tasks",
            passed = report.validationTasks.containsAll(validationTasks),
            detail = "Unit tests and lint remain the release gate.",
        ),
    )

    fun blockers(report: UnifiedModuleBrowserReleaseSealReport = build()): List<UnifiedModuleBrowserReleaseCheck> =
        checks(report).filterNot { it.passed }

    fun docsAreComplete(paths: Collection<String>): Boolean = requiredDocuments.all(paths::contains)

    private fun phases(): List<UnifiedModuleBrowserReleasePhase> = listOf(
        UnifiedModuleBrowserReleasePhase("10", "Unified module browser model", "canonical model", "docs/UNIFIED_MODULE_BROWSER_PHASE10.md"),
        UnifiedModuleBrowserReleasePhase("11", "Unified browser controls", "filter, sort, search", "docs/UNIFIED_MODULE_BROWSER_PHASE11.md"),
        UnifiedModuleBrowserReleasePhase("12", "Unified browser UI wiring", "Compose bridge", "docs/UNIFIED_MODULE_BROWSER_PHASE12.md"),
        UnifiedModuleBrowserReleasePhase("13", "Unified browser Problems", "health and evidence", "docs/UNIFIED_MODULE_BROWSER_PHASE13.md"),
        UnifiedModuleBrowserReleasePhase("14", "Unified browser safe actions", "action planner", "docs/UNIFIED_MODULE_BROWSER_PHASE14.md"),
        UnifiedModuleBrowserReleasePhase("15", "Unified browser action results", "guided feedback", "docs/UNIFIED_MODULE_BROWSER_PHASE15.md"),
        UnifiedModuleBrowserReleasePhase("16", "Unified browser UX polish", "presentation policy", "docs/UNIFIED_MODULE_BROWSER_PHASE16.md"),
        UnifiedModuleBrowserReleasePhase("17", "Unified browser regression seal", "cross-source seal", "docs/UNIFIED_MODULE_BROWSER_PHASE17.md"),
        UnifiedModuleBrowserReleasePhase("18", "Unified browser release polish", "release seal", "docs/UNIFIED_MODULE_BROWSER_PHASE18.md"),
    )

    private fun safeActionKinds(): Set<UnifiedModuleBrowserActionKind> = UnifiedModuleBrowserActionKind.entries
        .filterNot { it.destructive }
        .toSet()
}
