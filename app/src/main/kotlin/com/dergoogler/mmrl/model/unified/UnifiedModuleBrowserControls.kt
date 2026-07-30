package com.dergoogler.mmrl.model.unified

import java.util.Locale

/**
 * Phase 11 control model for the unified module browser.
 *
 * It is intentionally UI-toolkit agnostic. Compose screens can keep the current
 * installed-module layout while adopting this state object for view buckets,
 * filtering, sorting, search, and density decisions.
 */
enum class UnifiedModuleView(val label: String) {
    INSTALLED("Installed"),
    REPOSITORY("Repo"),
    UPDATES("Updates"),
    SCOPES("Scopes"),
    PROBLEMS("Problems"),
    GITHUB_SOURCES("GitHub Sources"),
}

enum class UnifiedModuleDensityMode(
    val label: String,
    val maxDescriptionLines: Int,
    val showBadges: Boolean,
    val showDiagnostics: Boolean,
) {
    COMFORTABLE(
        label = "Comfortable",
        maxDescriptionLines = 3,
        showBadges = true,
        showDiagnostics = false,
    ),
    COMPACT(
        label = "Compact",
        maxDescriptionLines = 1,
        showBadges = true,
        showDiagnostics = false,
    ),
    DIAGNOSTIC(
        label = "Diagnostic",
        maxDescriptionLines = 5,
        showBadges = true,
        showDiagnostics = true,
    ),
}

enum class UnifiedScopeFilter(val label: String) {
    ANY("Any scope"),
    SCOPED("Has scope"),
    UNSCOPED("No scope"),
    ENABLED("Scope enabled"),
    DISABLED("Scope disabled"),
    AUTO_INCLUDE("Auto include"),
}

enum class UnifiedModuleHealthFilter(val label: String) {
    ANY("Any health"),
    HEALTHY("Healthy"),
    WARNINGS("Warnings"),
    ERRORS("Errors"),
    PROBLEMS("Problems"),
}

data class UnifiedModuleBrowserControlsState(
    val view: UnifiedModuleView = UnifiedModuleView.INSTALLED,
    val searchText: String = "",
    val installStates: Set<UnifiedInstallState> = emptySet(),
    val sourceTypes: Set<UnifiedModuleSourceType> = emptySet(),
    val sourceModes: Set<UnifiedModuleSourceMode> = emptySet(),
    val providerStates: Set<UnifiedProviderCompatibility> = emptySet(),
    val scopeFilter: UnifiedScopeFilter = UnifiedScopeFilter.ANY,
    val healthFilter: UnifiedModuleHealthFilter = UnifiedModuleHealthFilter.ANY,
    val sortMode: UnifiedModuleSortMode = UnifiedModuleSortMode.INSTALLED_FIRST,
    val descending: Boolean = false,
    val density: UnifiedModuleDensityMode = UnifiedModuleDensityMode.COMFORTABLE,
) {
    val hasExplicitFilters: Boolean
        get() = searchText.isNotBlank() ||
            installStates.isNotEmpty() ||
            sourceTypes.isNotEmpty() ||
            sourceModes.isNotEmpty() ||
            providerStates.isNotEmpty() ||
            scopeFilter != UnifiedScopeFilter.ANY ||
            healthFilter != UnifiedModuleHealthFilter.ANY
}

data class UnifiedModuleBrowserStats(
    val total: Int,
    val installed: Int,
    val repository: Int,
    val updates: Int,
    val scopes: Int,
    val problems: Int,
    val githubSources: Int,
)

object UnifiedModuleBrowserControls {
    fun apply(
        items: List<UnifiedModuleItem>,
        controls: UnifiedModuleBrowserControlsState,
    ): List<UnifiedModuleItem> {
        val filtered = items.filter { item ->
            item.matchesView(controls.view) &&
                item.matchesSearch(controls.searchText) &&
                item.matchesInstallStates(controls.installStates) &&
                item.matchesSourceTypes(controls.sourceTypes) &&
                item.matchesSourceModes(controls.sourceModes) &&
                item.matchesProviderStates(controls.providerStates) &&
                item.matchesScopeFilter(controls.scopeFilter) &&
                item.matchesHealthFilter(controls.healthFilter)
        }
        return UnifiedModuleBrowserModel.sort(
            items = filtered,
            mode = controls.sortMode,
            descending = controls.descending,
        )
    }

    fun defaultSortForView(view: UnifiedModuleView): UnifiedModuleSortMode = when (view) {
        UnifiedModuleView.INSTALLED -> UnifiedModuleSortMode.INSTALLED_FIRST
        UnifiedModuleView.REPOSITORY -> UnifiedModuleSortMode.NAME_A_Z
        UnifiedModuleView.UPDATES -> UnifiedModuleSortMode.UPDATE_AVAILABLE_FIRST
        UnifiedModuleView.SCOPES -> UnifiedModuleSortMode.MOST_SCOPED_APPS
        UnifiedModuleView.PROBLEMS -> UnifiedModuleSortMode.PROBLEM_SEVERITY
        UnifiedModuleView.GITHUB_SOURCES -> UnifiedModuleSortMode.RECENTLY_UPDATED
    }

    fun stats(items: List<UnifiedModuleItem>): UnifiedModuleBrowserStats = UnifiedModuleBrowserStats(
        total = items.size,
        installed = items.count { it.matchesView(UnifiedModuleView.INSTALLED) },
        repository = items.count { it.matchesView(UnifiedModuleView.REPOSITORY) },
        updates = items.count { it.matchesView(UnifiedModuleView.UPDATES) },
        scopes = items.count { it.matchesView(UnifiedModuleView.SCOPES) },
        problems = items.count { it.matchesView(UnifiedModuleView.PROBLEMS) },
        githubSources = items.count { it.matchesView(UnifiedModuleView.GITHUB_SOURCES) },
    )

    fun filterSummary(controls: UnifiedModuleBrowserControlsState): List<String> = buildList {
        controls.searchText.trim().takeIf(String::isNotBlank)?.let { add("Search: $it") }
        if (controls.installStates.isNotEmpty()) add("State: ${controls.installStates.joinToString { it.label }}")
        if (controls.sourceTypes.isNotEmpty()) add("Source: ${controls.sourceTypes.joinToString { it.label }}")
        if (controls.sourceModes.isNotEmpty()) add("Mode: ${controls.sourceModes.joinToString { it.label }}")
        if (controls.providerStates.isNotEmpty()) add("Provider: ${controls.providerStates.joinToString { it.label }}")
        if (controls.scopeFilter != UnifiedScopeFilter.ANY) add(controls.scopeFilter.label)
        if (controls.healthFilter != UnifiedModuleHealthFilter.ANY) add(controls.healthFilter.label)
    }

    private fun UnifiedModuleItem.matchesView(view: UnifiedModuleView): Boolean = when (view) {
        UnifiedModuleView.INSTALLED -> installed ||
            sourceTypes.any { it == UnifiedModuleSourceType.INSTALLED_ROOT || it == UnifiedModuleSourceType.LSPOSED_INSTALLED }
        UnifiedModuleView.REPOSITORY -> sourceTypes.any {
            it == UnifiedModuleSourceType.REPOSITORY || it == UnifiedModuleSourceType.LSPOSED_REPOSITORY
        }
        UnifiedModuleView.UPDATES -> updateAvailable ||
            state.installState == UnifiedInstallState.UPDATE_PENDING ||
            state.installState == UnifiedInstallState.LOCKED ||
            state.installState == UnifiedInstallState.IGNORED
        UnifiedModuleView.SCOPES -> state.scope is UnifiedScopeState.Lsposed ||
            sourceTypes.any { it == UnifiedModuleSourceType.LSPOSED_INSTALLED || it == UnifiedModuleSourceType.LSPOSED_REPOSITORY }
        UnifiedModuleView.PROBLEMS -> hasProblems || state.installState == UnifiedInstallState.PROBLEM
        UnifiedModuleView.GITHUB_SOURCES -> sourceTypes.contains(UnifiedModuleSourceType.GITHUB_SOURCE)
    }

    private fun UnifiedModuleItem.matchesSearch(rawText: String): Boolean {
        val terms = rawText
            .trim()
            .lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
        if (terms.isEmpty()) return true
        return terms.all { term -> matchesSearchTerm(term) }
    }

    private fun UnifiedModuleItem.matchesSearchTerm(term: String): Boolean {
        val field = term.substringBefore(':', missingDelimiterValue = "")
        val value = term.substringAfter(':', missingDelimiterValue = term).trim()
        if (value.isBlank()) return true
        return when (field) {
            "name", "title" -> title.contains(value, ignoreCase = true)
            "id", "module", "moduleid" -> canonicalId.contains(value, ignoreCase = true) || displayId.contains(value, ignoreCase = true)
            "package", "pkg", "packageid" -> displayId.contains(value, ignoreCase = true) || canonicalId.contains(value, ignoreCase = true)
            "alias", "aliases" -> aliases.any { it.contains(value, ignoreCase = true) }
            "author" -> author.orEmpty().contains(value, ignoreCase = true)
            "desc", "description" -> description.contains(value, ignoreCase = true)
            "source", "repo", "repository" -> repositoryName.orEmpty().contains(value, ignoreCase = true) ||
                sourceUrl.orEmpty().contains(value, ignoreCase = true)
            "folder" -> searchTokens.any { it.contains(value, ignoreCase = true) }
            "scope" -> scopeSearchValues().any { it.contains(value, ignoreCase = true) }
            "badge" -> badges.any { badge ->
                badge.label.contains(value, ignoreCase = true) || badge.detail.contains(value, ignoreCase = true)
            }
            "problem", "issue" -> hasProblems &&
                badges.any { badge ->
                    badge.label.contains(value, ignoreCase = true) ||
                        badge.detail.contains(value, ignoreCase = true) ||
                        badge.kind.name.contains(value, ignoreCase = true)
                }
            "severity" -> badges.any { badge ->
                badge.severity.name.contains(value, ignoreCase = true)
            }
            else -> searchTokens.any { it.contains(term, ignoreCase = true) } ||
                badges.any { badge ->
                    badge.label.contains(term, ignoreCase = true) || badge.detail.contains(term, ignoreCase = true)
                }
        }
    }

    private fun UnifiedModuleItem.scopeSearchValues(): List<String> = when (val scope = state.scope) {
        is UnifiedScopeState.Lsposed -> scope.packages + listOf(
            scope.scopedPackageCount.toString(),
            if (scope.enabled) "enabled" else "disabled",
            if (scope.autoInclude) "auto" else "manual",
        )
        UnifiedScopeState.None -> emptyList()
    }

    private fun UnifiedModuleItem.matchesInstallStates(states: Set<UnifiedInstallState>): Boolean =
        states.isEmpty() || state.installState in states

    private fun UnifiedModuleItem.matchesSourceTypes(types: Set<UnifiedModuleSourceType>): Boolean =
        types.isEmpty() || sourceTypes.any(types::contains)

    private fun UnifiedModuleItem.matchesSourceModes(modes: Set<UnifiedModuleSourceMode>): Boolean =
        modes.isEmpty() ||
            sourceMode in modes ||
            badges.any { badge ->
                badge.kind == UnifiedBadgeKind.SOURCE_MODE &&
                    modes.any { mode -> badge.label.equals(mode.label, ignoreCase = true) }
            }

    private fun UnifiedModuleItem.matchesProviderStates(states: Set<UnifiedProviderCompatibility>): Boolean =
        states.isEmpty() || state.providerCompatibility in states

    private fun UnifiedModuleItem.matchesScopeFilter(filter: UnifiedScopeFilter): Boolean {
        val scope = state.scope
        return when (filter) {
            UnifiedScopeFilter.ANY -> true
            UnifiedScopeFilter.SCOPED -> scope is UnifiedScopeState.Lsposed && scope.scopedPackageCount > 0
            UnifiedScopeFilter.UNSCOPED -> scope !is UnifiedScopeState.Lsposed || scope.scopedPackageCount == 0
            UnifiedScopeFilter.ENABLED -> scope is UnifiedScopeState.Lsposed && scope.enabled
            UnifiedScopeFilter.DISABLED -> scope is UnifiedScopeState.Lsposed && !scope.enabled
            UnifiedScopeFilter.AUTO_INCLUDE -> scope is UnifiedScopeState.Lsposed && scope.autoInclude
        }
    }

    private fun UnifiedModuleItem.matchesHealthFilter(filter: UnifiedModuleHealthFilter): Boolean = when (filter) {
        UnifiedModuleHealthFilter.ANY -> true
        UnifiedModuleHealthFilter.HEALTHY -> !hasProblems
        UnifiedModuleHealthFilter.WARNINGS -> badges.any { it.severity == UnifiedBadgeSeverity.WARNING }
        UnifiedModuleHealthFilter.ERRORS -> badges.any { it.severity == UnifiedBadgeSeverity.ERROR }
        UnifiedModuleHealthFilter.PROBLEMS -> hasProblems
    }
}
