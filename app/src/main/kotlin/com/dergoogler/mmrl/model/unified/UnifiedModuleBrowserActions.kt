package com.dergoogler.mmrl.model.unified

import java.util.Locale

/**
 * Phase 14/15 safe action contract for the unified module browser.
 *
 * Phase 14 introduced non-destructive browser actions. Phase 15 adds a small
 * execution/result vocabulary so snackbars, inline result cards, contracts, and
 * future deep links all describe the same outcome instead of each caller
 * inventing its own copy.
 */
enum class UnifiedModuleBrowserActionKind(
    val label: String,
    val destructive: Boolean = false,
) {
    OPEN_MODULE("Open module"),
    COPY_EVIDENCE("Copy evidence"),
    COPY_SOURCE_URL("Copy source URL"),
    REFRESH_PROVIDER("Refresh provider"),
    REFRESH_REPOSITORY("Refresh evidence"),
    OPEN_GITHUB_SOURCE_RULES("Edit GitHub source"),
    OPEN_MANAGER("Open manager"),
    REVIEW_SCOPE("Review scope"),
    REVIEW_RESCUE("Review rescue"),
    RUN_DEBUG_PROBE("Run safe probe"),
    SUGGEST_FIX("Suggested fix"),
}

enum class UnifiedModuleBrowserActionTone(val label: String) {
    SUCCESS("Done"),
    INFO("Next step"),
    WARNING("Review"),
    BLOCKED("Blocked"),
}

enum class UnifiedModuleBrowserActionDestination(val label: String) {
    NONE("Stay here"),
    INSTALLED_VIEW("Installed modules"),
    GITHUB_SOURCE_RULES("GitHub source rules"),
    LSPOSED_MANAGER("LSPosed manager"),
    RESCUE_CONTROLS("Rescue controls"),
    DEBUG_WORKBENCH("Debug Workbench"),
    REPOSITORY_REFRESH("Repository refresh"),
}

data class UnifiedModuleBrowserAction(
    val kind: UnifiedModuleBrowserActionKind,
    val label: String = kind.label,
    val moduleId: String? = null,
    val moduleTitle: String? = null,
    val sourceUrl: String? = null,
    val detail: String = "",
    val evidence: List<UnifiedProblemEvidence> = emptyList(),
    val clipboardText: String = "",
    val enabled: Boolean = true,
) {
    val destructive: Boolean get() = kind.destructive
}

data class UnifiedModuleBrowserActionResult(
    val handled: Boolean,
    val message: String,
    val actionKind: UnifiedModuleBrowserActionKind,
    val copiedText: String? = null,
    val destination: UnifiedModuleBrowserActionDestination = UnifiedModuleBrowserActionDestination.NONE,
    val followUp: String = "",
    val tone: UnifiedModuleBrowserActionTone = if (handled) UnifiedModuleBrowserActionTone.SUCCESS else UnifiedModuleBrowserActionTone.INFO,
) {
    val safe: Boolean get() = !actionKind.destructive && tone != UnifiedModuleBrowserActionTone.BLOCKED
    val hasGuidance: Boolean get() = destination != UnifiedModuleBrowserActionDestination.NONE || followUp.isNotBlank()
    val userMessage: String
        get() = listOf(message, followUp)
            .filter(String::isNotBlank)
            .joinToString(" ")
}

object UnifiedModuleBrowserActionPlanner {
    fun forProblem(
        problem: UnifiedModuleProblem,
        action: UnifiedProblemAction,
    ): UnifiedModuleBrowserAction {
        val kind = action.kind.browserActionKind()
        return UnifiedModuleBrowserAction(
            kind = kind,
            label = action.label.ifBlank { kind.label },
            moduleId = problem.moduleId,
            moduleTitle = problem.moduleTitle,
            sourceUrl = problem.sourceLabel?.takeIf { it.looksLikeUrl() },
            detail = action.detail.ifBlank { problem.summary },
            evidence = problem.evidence,
            clipboardText = if (kind == UnifiedModuleBrowserActionKind.COPY_EVIDENCE) problem.asEvidenceText() else "",
            enabled = true,
        )
    }

    fun forItem(item: UnifiedModuleItem): List<UnifiedModuleBrowserAction> = buildList {
        if (item.installed) {
            add(
                item.action(
                    kind = UnifiedModuleBrowserActionKind.OPEN_MODULE,
                    label = "Open installed card",
                    detail = "Switch to Installed view and search id:${item.displayId}.",
                ),
            )
        }
        if (item.sourceUrl.isNullOrBlank().not()) {
            add(
                item.action(
                    kind = UnifiedModuleBrowserActionKind.COPY_SOURCE_URL,
                    label = "Copy source URL",
                    sourceUrl = item.sourceUrl,
                    clipboardText = item.sourceUrl.orEmpty(),
                ),
            )
        }
        if (UnifiedModuleSourceType.GITHUB_SOURCE in item.sourceTypes) {
            add(
                item.action(
                    kind = UnifiedModuleBrowserActionKind.OPEN_GITHUB_SOURCE_RULES,
                    label = "Edit source rules",
                    detail = "Open the saved GitHub source editor for this module, then review branch, workflow, artifact, asset, and reject/preferred regex rules.",
                ),
            )
        }
        if (
            item.state.providerCompatibility == UnifiedProviderCompatibility.UNAVAILABLE ||
            item.state.providerCompatibility == UnifiedProviderCompatibility.LIMITED
        ) {
            add(
                item.action(
                    kind = UnifiedModuleBrowserActionKind.REFRESH_PROVIDER,
                    label = "Refresh provider",
                    detail = "Refresh local module, provider, LSPosed, and rescue signals.",
                ),
            )
        }
        if (item.sourceTypes.any { it == UnifiedModuleSourceType.REPOSITORY || it == UnifiedModuleSourceType.LSPOSED_REPOSITORY }) {
            add(
                item.action(
                    kind = UnifiedModuleBrowserActionKind.REFRESH_REPOSITORY,
                    label = "Refresh evidence",
                    detail = "Refresh local module evidence used by the unified row.",
                ),
            )
        }
        if (item.state.scope is UnifiedScopeState.Lsposed) {
            add(
                item.action(
                    kind = UnifiedModuleBrowserActionKind.REVIEW_SCOPE,
                    label = "Review scope",
                    detail = item.scopeSummary(),
                ),
            )
        }
        if (item.state.rescue is UnifiedRescueState.AshReXcue) {
            add(
                item.action(
                    kind = UnifiedModuleBrowserActionKind.REVIEW_RESCUE,
                    label = "Review rescue",
                    detail = item.rescueSummary(),
                ),
            )
        }
        if (item.hasProblems) {
            add(
                item.action(
                    kind = UnifiedModuleBrowserActionKind.COPY_EVIDENCE,
                    label = "Copy row evidence",
                    clipboardText = item.asEvidenceText(),
                ),
            )
        }
    }.distinctBy { action -> action.kind to action.label }

    fun resultFor(action: UnifiedModuleBrowserAction): UnifiedModuleBrowserActionResult = when (action.kind) {
        UnifiedModuleBrowserActionKind.COPY_EVIDENCE -> UnifiedModuleBrowserActionResult(
            handled = true,
            message = "Copied unified evidence for ${action.subjectLabel()}.",
            actionKind = action.kind,
            copiedText = action.clipboardText.ifBlank { action.detail },
            followUp = "Paste it into a bug report, support bundle, or issue comment.",
            tone = UnifiedModuleBrowserActionTone.SUCCESS,
        )
        UnifiedModuleBrowserActionKind.COPY_SOURCE_URL -> UnifiedModuleBrowserActionResult(
            handled = true,
            message = "Copied source URL for ${action.subjectLabel()}.",
            actionKind = action.kind,
            copiedText = action.clipboardText.ifBlank { action.sourceUrl.orEmpty() },
            followUp = "Use it to verify the upstream release, workflow artifact, or repository entry.",
            tone = UnifiedModuleBrowserActionTone.SUCCESS,
        )
        UnifiedModuleBrowserActionKind.REFRESH_PROVIDER -> UnifiedModuleBrowserActionResult(
            handled = true,
            message = "Refreshing provider, module, LSPosed, and rescue signals.",
            actionKind = action.kind,
            destination = UnifiedModuleBrowserActionDestination.DEBUG_WORKBENCH,
            followUp = "Reopen Problems after refresh if a badge still looks stale.",
            tone = UnifiedModuleBrowserActionTone.INFO,
        )
        UnifiedModuleBrowserActionKind.REFRESH_REPOSITORY -> UnifiedModuleBrowserActionResult(
            handled = true,
            message = "Refreshing unified repository evidence.",
            actionKind = action.kind,
            destination = UnifiedModuleBrowserActionDestination.REPOSITORY_REFRESH,
            followUp = "Saved GitHub source rules and repository rows will be re-evaluated from the local cache/service.",
            tone = UnifiedModuleBrowserActionTone.INFO,
        )
        UnifiedModuleBrowserActionKind.RUN_DEBUG_PROBE -> UnifiedModuleBrowserActionResult(
            handled = true,
            message = "Refreshing safe diagnostics evidence for ${action.subjectLabel()}.",
            actionKind = action.kind,
            destination = UnifiedModuleBrowserActionDestination.DEBUG_WORKBENCH,
            followUp = "No install, remove, enable, disable, or scope write was attempted.",
            tone = UnifiedModuleBrowserActionTone.INFO,
        )
        UnifiedModuleBrowserActionKind.OPEN_MODULE -> UnifiedModuleBrowserActionResult(
            handled = true,
            message = "Opening Installed view for ${action.subjectLabel()}.",
            actionKind = action.kind,
            destination = UnifiedModuleBrowserActionDestination.INSTALLED_VIEW,
            followUp = "Search was narrowed to id:${action.moduleId.orEmpty()}.",
            tone = UnifiedModuleBrowserActionTone.INFO,
        )
        UnifiedModuleBrowserActionKind.OPEN_GITHUB_SOURCE_RULES -> UnifiedModuleBrowserActionResult(
            handled = false,
            message = "GitHub source editing is guided from the installed card menu for now.",
            actionKind = action.kind,
            destination = UnifiedModuleBrowserActionDestination.GITHUB_SOURCE_RULES,
            followUp = action.detail.ifBlank { "Open the installed module card, then edit branch, workflow, artifact, asset, reject, and preferred variant regex rules." },
            tone = UnifiedModuleBrowserActionTone.INFO,
        )
        UnifiedModuleBrowserActionKind.OPEN_MANAGER -> UnifiedModuleBrowserActionResult(
            handled = false,
            message = "Manager review continues from the LSPosed tab or Debug Workbench card.",
            actionKind = action.kind,
            destination = UnifiedModuleBrowserActionDestination.LSPOSED_MANAGER,
            followUp = action.detail.ifBlank { "Use the manager card to check install state, package visibility, and provider bridge health." },
            tone = UnifiedModuleBrowserActionTone.WARNING,
        )
        UnifiedModuleBrowserActionKind.REVIEW_SCOPE -> UnifiedModuleBrowserActionResult(
            handled = false,
            message = "Scope review is ready for ${action.subjectLabel()}.",
            actionKind = action.kind,
            destination = UnifiedModuleBrowserActionDestination.LSPOSED_MANAGER,
            followUp = action.detail.ifBlank { "Open LSPosed modules to review scoped packages before changing anything." },
            tone = UnifiedModuleBrowserActionTone.INFO,
        )
        UnifiedModuleBrowserActionKind.REVIEW_RESCUE -> UnifiedModuleBrowserActionResult(
            handled = false,
            message = "Rescue review is ready for ${action.subjectLabel()}.",
            actionKind = action.kind,
            destination = UnifiedModuleBrowserActionDestination.RESCUE_CONTROLS,
            followUp = action.detail.ifBlank { "Use installed-module rescue controls to review AshReXcue state before restore or trust changes." },
            tone = UnifiedModuleBrowserActionTone.WARNING,
        )
        UnifiedModuleBrowserActionKind.SUGGEST_FIX -> UnifiedModuleBrowserActionResult(
            handled = false,
            message = "Suggested fix for ${action.subjectLabel()}.",
            actionKind = action.kind,
            destination = UnifiedModuleBrowserActionDestination.NONE,
            followUp = action.detail.ifBlank { "Review the evidence before making any changes." },
            tone = UnifiedModuleBrowserActionTone.INFO,
        )
    }

    fun blockedResult(action: UnifiedModuleBrowserAction): UnifiedModuleBrowserActionResult = UnifiedModuleBrowserActionResult(
        handled = false,
        message = "Unified browser blocked ${action.label.lowercase(Locale.ROOT)} without confirmation.",
        actionKind = action.kind,
        destination = UnifiedModuleBrowserActionDestination.NONE,
        followUp = "Install, remove, enable, disable, and scope writes require an explicit confirmed flow.",
        tone = UnifiedModuleBrowserActionTone.BLOCKED,
    )

    fun resultSummary(result: UnifiedModuleBrowserActionResult): List<String> = buildList {
        add(result.tone.label)
        if (result.destination != UnifiedModuleBrowserActionDestination.NONE) add("Destination: ${result.destination.label}")
        if (result.copiedText.isNullOrBlank().not()) add("Clipboard ready")
        if (!result.handled) add("Guided")
    }

    private fun UnifiedProblemActionKind.browserActionKind(): UnifiedModuleBrowserActionKind = when (this) {
        UnifiedProblemActionKind.OPEN_MODULE -> UnifiedModuleBrowserActionKind.OPEN_MODULE
        UnifiedProblemActionKind.RUN_PROBE -> UnifiedModuleBrowserActionKind.RUN_DEBUG_PROBE
        UnifiedProblemActionKind.COPY_EVIDENCE -> UnifiedModuleBrowserActionKind.COPY_EVIDENCE
        UnifiedProblemActionKind.SUGGEST_FIX -> UnifiedModuleBrowserActionKind.SUGGEST_FIX
        UnifiedProblemActionKind.EDIT_GITHUB_SOURCE -> UnifiedModuleBrowserActionKind.OPEN_GITHUB_SOURCE_RULES
        UnifiedProblemActionKind.REFRESH_PROVIDER -> UnifiedModuleBrowserActionKind.REFRESH_PROVIDER
        UnifiedProblemActionKind.OPEN_MANAGER -> UnifiedModuleBrowserActionKind.OPEN_MANAGER
        UnifiedProblemActionKind.REVIEW_SCOPE -> UnifiedModuleBrowserActionKind.REVIEW_SCOPE
        UnifiedProblemActionKind.REVIEW_RESCUE -> UnifiedModuleBrowserActionKind.REVIEW_RESCUE
        UnifiedProblemActionKind.CHECK_REPOSITORY -> UnifiedModuleBrowserActionKind.REFRESH_REPOSITORY
    }

    private fun UnifiedModuleItem.action(
        kind: UnifiedModuleBrowserActionKind,
        label: String = kind.label,
        sourceUrl: String? = this.sourceUrl,
        detail: String = "",
        clipboardText: String = "",
    ): UnifiedModuleBrowserAction = UnifiedModuleBrowserAction(
        kind = kind,
        label = label,
        moduleId = displayId,
        moduleTitle = title,
        sourceUrl = sourceUrl,
        detail = detail,
        evidence = evidenceLines(),
        clipboardText = clipboardText,
    )

    private fun UnifiedModuleBrowserAction.subjectLabel(): String = moduleTitle
        ?.takeIf(String::isNotBlank)
        ?: moduleId?.takeIf(String::isNotBlank)
        ?: "this module"

    private fun UnifiedModuleProblem.asEvidenceText(): String = buildList {
        add("Unified Browser Problem")
        add("Title: $title")
        add("Kind: ${kind.label}")
        add("Severity: ${severity.name}")
        moduleId?.let { add("Module: $it") }
        moduleTitle?.let { add("Module title: $it") }
        sourceLabel?.let { add("Source: $it") }
        add("Summary: $summary")
        evidence.forEach { add("${it.label}: ${it.value}") }
    }.joinToString("\n")

    private fun UnifiedModuleItem.asEvidenceText(): String = buildList {
        add("Unified Browser Module")
        add("Title: $title")
        add("ID: $displayId")
        add("Canonical: $canonicalId")
        add("State: ${state.installState.label}")
        add("Mode: ${sourceMode.label}")
        add("Sources: ${sourceTypes.joinToString { it.label }}")
        sourceUrl?.let { add("Source URL: $it") }
        repositoryName?.let { add("Repository: $it") }
        add("Provider: ${state.providerCompatibility.label}")
        add("Match: ${match.reason} · ${match.confidence}% · ${match.explanation}")
        badges.forEach { add("Badge: ${it.label}${if (it.detail.isBlank()) "" else " · ${it.detail}"} [${it.severity.name}]") }
    }.joinToString("\n")

    private fun UnifiedModuleItem.evidenceLines(): List<UnifiedProblemEvidence> = buildList {
        add(UnifiedProblemEvidence("Module", displayId))
        add(UnifiedProblemEvidence("Canonical", canonicalId))
        add(UnifiedProblemEvidence("State", state.installState.label))
        sourceUrl?.takeIf(String::isNotBlank)?.let { add(UnifiedProblemEvidence("Source", it)) }
        repositoryName?.takeIf(String::isNotBlank)?.let { add(UnifiedProblemEvidence("Repository", it)) }
    }

    private fun UnifiedModuleItem.scopeSummary(): String = when (val scope = state.scope) {
        is UnifiedScopeState.Lsposed -> "Scope has ${scope.scopedPackageCount} package${if (scope.scopedPackageCount == 1) "" else "s"}; ${if (scope.enabled) "enabled" else "disabled"}${if (scope.autoInclude) "; auto include" else ""}."
        UnifiedScopeState.None -> "No LSPosed scope evidence is attached to this row."
    }

    private fun UnifiedModuleItem.rescueSummary(): String = when (val rescue = state.rescue) {
        is UnifiedRescueState.AshReXcue -> "AshReXcue folder ${rescue.folder}; trust=${rescue.trust}; risk=${rescue.riskBand.name.lowercase(Locale.ROOT)}."
        UnifiedRescueState.None -> "No rescue evidence is attached to this row."
    }

    private fun String.looksLikeUrl(): Boolean = startsWith("http://") || startsWith("https://") || startsWith("github:")
}
