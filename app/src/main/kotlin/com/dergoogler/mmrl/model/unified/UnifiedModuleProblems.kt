package com.dergoogler.mmrl.model.unified

import java.util.Locale

/**
 * Phase 13 problem center for the unified module browser.
 *
 * It turns canonical module rows and future repo/debug signals into actionable
 * problem cards. The model is UI-toolkit free so repository refreshes, Debug
 * Workbench probes, support bundles, and the unified Modules screen can all use
 * the same vocabulary.
 */
data class UnifiedModuleProblemReport(
    val problems: List<UnifiedModuleProblem>,
) {
    val total: Int get() = problems.size
    val errors: Int get() = problems.count { it.severity == UnifiedBadgeSeverity.ERROR }
    val warnings: Int get() = problems.count { it.severity == UnifiedBadgeSeverity.WARNING }
    val notices: Int get() = problems.count { it.severity < UnifiedBadgeSeverity.WARNING }
    val actionCount: Int get() = problems.sumOf { it.actions.size }
    val healthy: Boolean get() = problems.none { it.severity >= UnifiedBadgeSeverity.WARNING }

    val headline: String
        get() = when {
            errors > 0 -> "$errors blocker${if (errors == 1) "" else "s"} need attention"
            warnings > 0 -> "$warnings warning${if (warnings == 1) "" else "s"} need review"
            notices > 0 -> "$notices note${if (notices == 1) "" else "s"} available"
            else -> "No unified browser problems detected"
        }

    val summary: String
        get() = when {
            problems.isEmpty() -> "Repository, provider, update, and scope signals look quiet."
            else -> "$total signal${if (total == 1) "" else "s"} · $errors error${if (errors == 1) "" else "s"} · $warnings warning${if (warnings == 1) "" else "s"} · $notices note${if (notices == 1) "" else "s"}."
        }
}

data class UnifiedModuleProblem(
    val id: String,
    val kind: UnifiedModuleProblemKind,
    val severity: UnifiedBadgeSeverity,
    val title: String,
    val summary: String,
    val moduleId: String? = null,
    val moduleTitle: String? = null,
    val sourceLabel: String? = null,
    val evidence: List<UnifiedProblemEvidence> = emptyList(),
    val actions: List<UnifiedProblemAction> = emptyList(),
) {
    val searchableText: String
        get() = listOf(
            id,
            kind.name,
            kind.label,
            severity.name,
            title,
            summary,
            moduleId.orEmpty(),
            moduleTitle.orEmpty(),
            sourceLabel.orEmpty(),
        ).plus(evidence.flatMap { listOf(it.label, it.value) })
            .plus(actions.flatMap { listOf(it.kind.label, it.label, it.detail) })
            .joinToString(" ")
            .lowercase(Locale.ROOT)
}

enum class UnifiedModuleProblemKind(val label: String) {
    PRIMARY_REPO_403("Primary repo 403"),
    BACKUP_REPO_FALLBACK("Backup repo fallback"),
    MALFORMED_REPO_ENTRIES("Malformed entries skipped"),
    CACHE_FALLBACK("Cache fallback"),
    GITHUB_ARTIFACT_EXPIRED("GitHub artifact expired"),
    GITHUB_TOKEN_REQUIRED("GitHub token required"),
    GITHUB_REGEX_MISMATCH("GitHub regex mismatch"),
    MANAGER_UNAVAILABLE("Manager unavailable"),
    PROVIDER_BRIDGE_AVAILABLE("Provider bridge available"),
    SCOPE_DB_UNAVAILABLE("Scope DB unavailable"),
    INSTALLED_NOT_IN_REPOSITORY("Installed but not in repo"),
    ALIAS_MATCH_ONLY("Alias match only"),
    MODULE_DISABLED("Disabled module"),
    FAILED_UPDATE("Failed update"),
    BADGE_WARNING("Badge warning"),
}

enum class UnifiedProblemActionKind(val label: String) {
    OPEN_MODULE("Open module"),
    RUN_PROBE("Run probe"),
    COPY_EVIDENCE("Copy evidence"),
    SUGGEST_FIX("Suggested fix"),
    EDIT_GITHUB_SOURCE("Edit GitHub source"),
    REFRESH_PROVIDER("Refresh provider"),
    OPEN_MANAGER("Open manager"),
    REVIEW_SCOPE("Review scope"),
    CHECK_REPOSITORY("Check repository"),
}

data class UnifiedProblemAction(
    val kind: UnifiedProblemActionKind,
    val label: String = kind.label,
    val detail: String = "",
)

data class UnifiedProblemEvidence(
    val label: String,
    val value: String,
)

data class UnifiedProblemSignal(
    val kind: UnifiedModuleProblemKind,
    val severity: UnifiedBadgeSeverity,
    val title: String,
    val summary: String,
    val sourceLabel: String? = null,
    val moduleId: String? = null,
    val evidence: List<UnifiedProblemEvidence> = emptyList(),
    val actions: List<UnifiedProblemAction> = emptyList(),
)

object UnifiedModuleProblemCenter {
    fun build(
        items: List<UnifiedModuleItem>,
        signals: List<UnifiedProblemSignal> = emptyList(),
    ): UnifiedModuleProblemReport {
        val moduleProblems = items.flatMap(::moduleProblems)
        val signalProblems = signals.map(::signalProblem)
        return UnifiedModuleProblemReport(
            problems = (signalProblems + moduleProblems)
                .distinctBy { it.id }
                .sortedWith(problemComparator),
        )
    }

    fun filter(
        report: UnifiedModuleProblemReport,
        text: String,
    ): UnifiedModuleProblemReport {
        val query = text.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) return report
        return report.copy(
            problems = report.problems.filter { query in it.searchableText },
        )
    }

    private fun moduleProblems(item: UnifiedModuleItem): List<UnifiedModuleProblem> = buildList {
        if (item.installed && !item.hasRepositoryEvidence()) {
            add(item.installedNotInRepositoryProblem())
        }
        if (item.match.reason == UnifiedMatchReason.ALIAS_ID) {
            add(item.aliasMatchOnlyProblem())
        }
        if (item.state.installState == UnifiedInstallState.DISABLED) {
            add(item.disabledProblem())
        }
        if (item.state.installState == UnifiedInstallState.UPDATE_PENDING) {
            add(item.failedUpdateWatchProblem())
        }
        if (item.state.providerCompatibility == UnifiedProviderCompatibility.UNAVAILABLE) {
            add(item.managerUnavailableProblem())
        }
        if (item.state.providerCompatibility == UnifiedProviderCompatibility.LIMITED) {
            add(item.providerBridgeProblem())
        }
        if (item.sourceTypes.any { it == UnifiedModuleSourceType.LSPOSED_INSTALLED || it == UnifiedModuleSourceType.LSPOSED_REPOSITORY } && item.state.scope == UnifiedScopeState.None) {
            add(item.scopeDbProblem())
        }
        item.badges
            .filter { it.severity >= UnifiedBadgeSeverity.WARNING }
            .forEach { badge -> add(item.badgeProblem(badge)) }
    }

    private fun signalProblem(signal: UnifiedProblemSignal): UnifiedModuleProblem = UnifiedModuleProblem(
        id = "signal:${signal.kind.name.lowercase(Locale.ROOT)}:${signal.moduleId.orEmpty()}:${signal.sourceLabel.orEmpty()}".stableKey(),
        kind = signal.kind,
        severity = signal.severity,
        title = signal.title,
        summary = signal.summary,
        moduleId = signal.moduleId,
        moduleTitle = signal.moduleId,
        sourceLabel = signal.sourceLabel,
        evidence = signal.evidence,
        actions = if (signal.actions.isNotEmpty()) signal.actions else defaultActions(signal.kind),
    )

    private fun UnifiedModuleItem.installedNotInRepositoryProblem(): UnifiedModuleProblem = problem(
        kind = UnifiedModuleProblemKind.INSTALLED_NOT_IN_REPOSITORY,
        severity = UnifiedBadgeSeverity.WARNING,
        title = "Installed module is not linked to a repository",
        summary = "The module is installed, but the unified browser did not find repository or saved-source evidence for updates.",
        evidence = listOf(
            UnifiedProblemEvidence("Module", displayId),
            UnifiedProblemEvidence("Sources", sourceTypes.joinToString { it.label }),
        ),
        actions = listOf(openModuleAction(), copyEvidenceAction(), suggestedFix("Add or repair the source mapping, then refresh repositories.")),
    )

    private fun UnifiedModuleItem.aliasMatchOnlyProblem(): UnifiedModuleProblem = problem(
        kind = UnifiedModuleProblemKind.ALIAS_MATCH_ONLY,
        severity = UnifiedBadgeSeverity.INFO,
        title = "Matched by alias registry",
        summary = "This row was merged through aliases rather than an exact id. Review it if the repository name looks unexpected.",
        evidence = listOf(
            UnifiedProblemEvidence("Match", match.explanation),
            UnifiedProblemEvidence("Aliases", aliases.joinToString()),
        ),
        actions = listOf(openModuleAction(), copyEvidenceAction(), suggestedFix("Keep the alias when it is correct, or adjust the source id if it points to the wrong module.")),
    )

    private fun UnifiedModuleItem.disabledProblem(): UnifiedModuleProblem = problem(
        kind = UnifiedModuleProblemKind.MODULE_DISABLED,
        severity = UnifiedBadgeSeverity.WARNING,
        title = "Module is disabled",
        summary = "The module is installed but disabled, so related manager, scope, or update actions may not reflect the active runtime.",
        evidence = listOf(UnifiedProblemEvidence("State", state.installState.label)),
        actions = listOf(openModuleAction(), copyEvidenceAction(), suggestedFix("Enable the module if it should participate in runtime checks, then reboot if required.")),
    )

    private fun UnifiedModuleItem.failedUpdateWatchProblem(): UnifiedModuleProblem = problem(
        kind = UnifiedModuleProblemKind.FAILED_UPDATE,
        severity = UnifiedBadgeSeverity.WARNING,
        title = "Update is pending verification",
        summary = "The installed module is already marked update-pending. Treat this as a watch item until the next refresh confirms success.",
        evidence = listOf(UnifiedProblemEvidence("State", state.installState.label)),
        actions = listOf(openModuleAction(), copyEvidenceAction(), suggestedFix("Refresh module state after reboot or retry the update if it remains pending.")),
    )

    private fun UnifiedModuleItem.managerUnavailableProblem(): UnifiedModuleProblem = problem(
        kind = UnifiedModuleProblemKind.MANAGER_UNAVAILABLE,
        severity = UnifiedBadgeSeverity.ERROR,
        title = "Provider manager unavailable",
        summary = "A related LSPosed or provider row cannot be opened through the currently detected manager path.",
        evidence = listOf(UnifiedProblemEvidence("Provider", state.providerCompatibility.label)),
        actions = listOf(runProbeAction(), openManagerAction(), copyEvidenceAction(), suggestedFix("Install or repair the matching manager/provider bridge, then refresh provider status.")),
    )

    private fun UnifiedModuleItem.providerBridgeProblem(): UnifiedModuleProblem = problem(
        kind = UnifiedModuleProblemKind.PROVIDER_BRIDGE_AVAILABLE,
        severity = UnifiedBadgeSeverity.INFO,
        title = "Provider bridge is partial",
        summary = "The provider exists, but the unified browser only has limited capability evidence for this module.",
        evidence = listOf(UnifiedProblemEvidence("Provider", state.providerCompatibility.label)),
        actions = listOf(runProbeAction(), refreshProviderAction(), copyEvidenceAction()),
    )

    private fun UnifiedModuleItem.scopeDbProblem(): UnifiedModuleProblem = problem(
        kind = UnifiedModuleProblemKind.SCOPE_DB_UNAVAILABLE,
        severity = UnifiedBadgeSeverity.WARNING,
        title = "Scope database evidence unavailable",
        summary = "The item looks like an LSPosed module, but no readable scope state is attached to the canonical row.",
        evidence = listOf(
            UnifiedProblemEvidence("Module", displayId),
            UnifiedProblemEvidence("Provider", state.providerCompatibility.label),
        ),
        actions = listOf(runProbeAction(), reviewScopeAction(), copyEvidenceAction(), suggestedFix("Refresh the provider and run the LSPosed scope probe before editing scope.")),
    )

    private fun UnifiedModuleItem.badgeProblem(badge: UnifiedModuleBadge): UnifiedModuleProblem = problem(
        kind = badge.problemKind(),
        severity = badge.severity,
        title = badge.label,
        summary = badge.detail.ifBlank { "Unified browser badge requires review." },
        evidence = listOf(
            UnifiedProblemEvidence("Badge", badge.kind.name),
            UnifiedProblemEvidence("Severity", badge.severity.name),
        ),
        actions = defaultActions(badge.problemKind()),
        keySuffix = badge.kind.name + badge.label,
    )

    private fun UnifiedModuleItem.problem(
        kind: UnifiedModuleProblemKind,
        severity: UnifiedBadgeSeverity,
        title: String,
        summary: String,
        evidence: List<UnifiedProblemEvidence>,
        actions: List<UnifiedProblemAction>,
        keySuffix: String = kind.name,
    ): UnifiedModuleProblem = UnifiedModuleProblem(
        id = "${canonicalId}:${kind.name}:$keySuffix".stableKey(),
        kind = kind,
        severity = severity,
        title = title,
        summary = summary,
        moduleId = displayId,
        moduleTitle = this.title,
        sourceLabel = repositoryName ?: sourceUrl,
        evidence = evidence,
        actions = actions,
    )

    private fun UnifiedModuleItem.hasRepositoryEvidence(): Boolean = sourceTypes.any {
        it == UnifiedModuleSourceType.REPOSITORY ||
            it == UnifiedModuleSourceType.GITHUB_SOURCE ||
            it == UnifiedModuleSourceType.LSPOSED_REPOSITORY
    }

    private fun UnifiedModuleBadge.problemKind(): UnifiedModuleProblemKind = when (kind) {
        UnifiedBadgeKind.PROVIDER_COMPATIBILITY -> UnifiedModuleProblemKind.MANAGER_UNAVAILABLE
        UnifiedBadgeKind.ARTIFACT_STRATEGY -> UnifiedModuleProblemKind.GITHUB_REGEX_MISMATCH
        UnifiedBadgeKind.SOURCE_MODE -> UnifiedModuleProblemKind.GITHUB_REGEX_MISMATCH
        UnifiedBadgeKind.INSTALL_STATE -> UnifiedModuleProblemKind.MODULE_DISABLED
        UnifiedBadgeKind.UPDATE -> UnifiedModuleProblemKind.FAILED_UPDATE
        UnifiedBadgeKind.SCOPE -> UnifiedModuleProblemKind.SCOPE_DB_UNAVAILABLE
        UnifiedBadgeKind.PROBLEM -> UnifiedModuleProblemKind.BADGE_WARNING
    }

    private fun defaultActions(kind: UnifiedModuleProblemKind): List<UnifiedProblemAction> = when (kind) {
        UnifiedModuleProblemKind.PRIMARY_REPO_403,
        UnifiedModuleProblemKind.BACKUP_REPO_FALLBACK,
        UnifiedModuleProblemKind.MALFORMED_REPO_ENTRIES,
        UnifiedModuleProblemKind.CACHE_FALLBACK,
        -> listOf(checkRepositoryAction(), runProbeAction(), copyEvidenceAction())
        UnifiedModuleProblemKind.GITHUB_ARTIFACT_EXPIRED,
        UnifiedModuleProblemKind.GITHUB_TOKEN_REQUIRED,
        UnifiedModuleProblemKind.GITHUB_REGEX_MISMATCH,
        -> listOf(editGithubSourceAction(), copyEvidenceAction(), suggestedFix("Refresh the saved GitHub source and verify token/rules."))
        UnifiedModuleProblemKind.MANAGER_UNAVAILABLE -> listOf(openManagerAction(), runProbeAction(), copyEvidenceAction())
        UnifiedModuleProblemKind.PROVIDER_BRIDGE_AVAILABLE -> listOf(refreshProviderAction(), runProbeAction(), copyEvidenceAction())
        UnifiedModuleProblemKind.SCOPE_DB_UNAVAILABLE -> listOf(reviewScopeAction(), runProbeAction(), copyEvidenceAction())
        UnifiedModuleProblemKind.INSTALLED_NOT_IN_REPOSITORY,
        UnifiedModuleProblemKind.ALIAS_MATCH_ONLY,
        UnifiedModuleProblemKind.MODULE_DISABLED,
        UnifiedModuleProblemKind.FAILED_UPDATE,
        UnifiedModuleProblemKind.BADGE_WARNING,
        -> listOf(openModuleAction(), copyEvidenceAction())
    }

    private val problemComparator = compareByDescending<UnifiedModuleProblem> { it.severity.ordinal }
        .thenBy { it.kind.ordinal }
        .thenBy { it.moduleTitle.orEmpty().lowercase(Locale.ROOT) }
        .thenBy { it.title.lowercase(Locale.ROOT) }

    private fun openModuleAction() = UnifiedProblemAction(UnifiedProblemActionKind.OPEN_MODULE)
    private fun runProbeAction() = UnifiedProblemAction(UnifiedProblemActionKind.RUN_PROBE, detail = "Run the matching Debug Workbench probe.")
    private fun copyEvidenceAction() = UnifiedProblemAction(UnifiedProblemActionKind.COPY_EVIDENCE, detail = "Copy redacted evidence for support.")
    private fun editGithubSourceAction() = UnifiedProblemAction(UnifiedProblemActionKind.EDIT_GITHUB_SOURCE)
    private fun refreshProviderAction() = UnifiedProblemAction(UnifiedProblemActionKind.REFRESH_PROVIDER)
    private fun openManagerAction() = UnifiedProblemAction(UnifiedProblemActionKind.OPEN_MANAGER)
    private fun reviewScopeAction() = UnifiedProblemAction(UnifiedProblemActionKind.REVIEW_SCOPE)
    private fun checkRepositoryAction() = UnifiedProblemAction(UnifiedProblemActionKind.CHECK_REPOSITORY)
    private fun suggestedFix(detail: String) = UnifiedProblemAction(UnifiedProblemActionKind.SUGGEST_FIX, detail = detail)
}

private fun String.stableKey(): String = trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9._:-]+"), "-")
    .trim('-')
