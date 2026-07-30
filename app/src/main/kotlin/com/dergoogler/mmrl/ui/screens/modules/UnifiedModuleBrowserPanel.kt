package com.dergoogler.mmrl.ui.screens.modules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.model.unified.UnifiedBadgeSeverity
import com.dergoogler.mmrl.model.unified.UnifiedModuleBadge
import com.dergoogler.mmrl.model.unified.UnifiedModuleBrowserAction
import com.dergoogler.mmrl.model.unified.UnifiedModuleBrowserActionPlanner
import com.dergoogler.mmrl.model.unified.UnifiedModuleBrowserActionResult
import com.dergoogler.mmrl.model.unified.UnifiedModuleBrowserActionTone
import com.dergoogler.mmrl.model.unified.UnifiedModuleBrowserControls
import com.dergoogler.mmrl.model.unified.UnifiedModuleBrowserControlsState
import com.dergoogler.mmrl.model.unified.UnifiedModuleBrowserStats
import com.dergoogler.mmrl.model.unified.UnifiedModuleDensityMode
import com.dergoogler.mmrl.model.unified.UnifiedModuleHealthFilter
import com.dergoogler.mmrl.model.unified.UnifiedModuleItem
import com.dergoogler.mmrl.model.unified.UnifiedModuleProblem
import com.dergoogler.mmrl.model.unified.UnifiedModuleProblemReport
import com.dergoogler.mmrl.model.unified.UnifiedModuleSortMode
import com.dergoogler.mmrl.model.unified.UnifiedModuleSourceType
import com.dergoogler.mmrl.model.unified.UnifiedModuleView
import com.dergoogler.mmrl.model.unified.UnifiedProviderCompatibility
import com.dergoogler.mmrl.model.unified.UnifiedScopeFilter
import com.dergoogler.mmrl.model.unified.UnifiedScopeState

/**
 * Phase 12 bridge UI for the unified module browser.
 *
 * The intent is deliberately modest: expose the Phase 10/11 data plane inside
 * the existing Modules screen without replacing the installed-module card stack.
 * Installed keeps its mature actions. Non-installed buckets use these read-only
 * cards until Phase 13/14 can add deeper actions and adaptive polish.
 */
@Composable
fun UnifiedModuleBrowserHeader(
    controls: UnifiedModuleBrowserControlsState,
    allItems: List<UnifiedModuleItem>,
    shownItems: List<UnifiedModuleItem>,
    onViewSelected: (UnifiedModuleView) -> Unit,
    onDensitySelected: (UnifiedModuleDensityMode) -> Unit,
    onSortSelected: (UnifiedModuleSortMode, Boolean) -> Unit,
    onHealthFilterSelected: (UnifiedModuleHealthFilter) -> Unit,
    onScopeFilterSelected: (UnifiedScopeFilter) -> Unit,
    onSourceTypesSelected: (Set<UnifiedModuleSourceType>) -> Unit,
    onProviderStatesSelected: (Set<UnifiedProviderCompatibility>) -> Unit,
    onClearFilters: () -> Unit,
) {
    val stats = remember(allItems) { UnifiedModuleBrowserControls.stats(allItems) }
    val summary = remember(controls) { UnifiedModuleBrowserControls.filterSummary(controls) }
    val availableSourceTypes = remember(allItems) {
        UnifiedModuleSourceType.entries.filter { type -> allItems.any { type in it.sourceTypes } }
    }
    val availableProviderStates = remember(allItems) {
        UnifiedProviderCompatibility.entries.filter { state -> allItems.any { it.state.providerCompatibility == state } }
    }

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = "Unified browser",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = buildString {
                        append("${shownItems.size} shown from ${stats.total} canonical rows. ")
                        append("Search supports name:, id:, alias:, source:, scope:, and badge:.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UnifiedModuleView.entries.forEach { view ->
                    FilterChip(
                        selected = controls.view == view,
                        onClick = { onViewSelected(view) },
                        label = { Text("${view.label} ${stats.countFor(view)}") },
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UnifiedModuleDensityMode.entries.forEach { density ->
                    FilterChip(
                        selected = controls.density == density,
                        onClick = { onDensitySelected(density) },
                        label = { Text(density.label) },
                    )
                }
                UnifiedSortButton(
                    controls = controls,
                    onSortSelected = onSortSelected,
                )
                UnifiedHealthButton(
                    selected = controls.healthFilter,
                    onSelected = onHealthFilterSelected,
                )
                UnifiedScopeButton(
                    selected = controls.scopeFilter,
                    onSelected = onScopeFilterSelected,
                )
                if (controls.hasExplicitFilters) {
                    TextButton(onClick = onClearFilters) {
                        Text("Clear")
                    }
                }
            }

            if (availableSourceTypes.isNotEmpty()) {
                UnifiedFilterSection(label = "Sources") {
                    availableSourceTypes.forEach { type ->
                        FilterChip(
                            selected = type in controls.sourceTypes,
                            onClick = { onSourceTypesSelected(controls.sourceTypes.toggled(type)) },
                            label = { Text(type.label) },
                        )
                    }
                }
            }

            if (availableProviderStates.isNotEmpty()) {
                UnifiedFilterSection(label = "Provider") {
                    availableProviderStates.forEach { state ->
                        FilterChip(
                            selected = state in controls.providerStates,
                            onClick = { onProviderStatesSelected(controls.providerStates.toggled(state)) },
                            label = { Text(state.label) },
                        )
                    }
                }
            }

            if (summary.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    summary.forEach { label -> UnifiedTinyPill(text = label) }
                }
            }
        }
    }
}

@Composable
private fun UnifiedSortButton(
    controls: UnifiedModuleBrowserControlsState,
    onSortSelected: (UnifiedModuleSortMode, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) {
        Text("Sort: ${controls.sortMode.label()}${if (controls.descending) " desc" else ""}")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        UnifiedModuleSortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.label()) },
                onClick = {
                    expanded = false
                    val descending = if (mode == controls.sortMode) !controls.descending else false
                    onSortSelected(mode, descending)
                },
            )
        }
    }
}

@Composable
private fun UnifiedHealthButton(
    selected: UnifiedModuleHealthFilter,
    onSelected: (UnifiedModuleHealthFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) {
        Text("Health: ${selected.label}")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        UnifiedModuleHealthFilter.entries.forEach { filter ->
            DropdownMenuItem(
                text = { Text(filter.label) },
                onClick = {
                    expanded = false
                    onSelected(filter)
                },
            )
        }
    }
}


@Composable
private fun UnifiedScopeButton(
    selected: UnifiedScopeFilter,
    onSelected: (UnifiedScopeFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) {
        Text("Scope: ${selected.label}")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        UnifiedScopeFilter.entries.forEach { filter ->
            DropdownMenuItem(
                text = { Text(filter.label) },
                onClick = {
                    expanded = false
                    onSelected(filter)
                },
            )
        }
    }
}

@Composable
private fun UnifiedFilterSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
fun UnifiedModuleBrowserEmptyState(
    controls: UnifiedModuleBrowserControlsState,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "No ${controls.view.label.lowercase()} modules match",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Try another unified view, clear filters, or search with a field such as problem:scope, severity:error, badge:warning, or source:github.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
fun UnifiedModuleActionResultCard(result: UnifiedModuleBrowserActionResult) {
    val color = result.tone.color()
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth(),
        color = color.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.36f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (result.followUp.isNotBlank()) {
                        Text(
                            text = result.followUp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    color = color.copy(alpha = 0.12f),
                    contentColor = color,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.34f)),
                ) {
                    Text(
                        text = result.tone.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                UnifiedModuleBrowserActionPlanner.resultSummary(result).forEach { summary ->
                    UnifiedTinyPill(summary)
                }
            }
        }
    }
}

@Composable
fun UnifiedModuleProblemDigest(report: UnifiedModuleProblemReport) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth(),
        color = if (report.healthy) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
        },
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = report.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                UnifiedTinyPill("${report.errors} errors")
                UnifiedTinyPill("${report.warnings} warnings")
                UnifiedTinyPill("${report.notices} notes")
                UnifiedTinyPill("${report.actionCount} actions")
            }
        }
    }
}

@Composable
fun UnifiedModuleProblemCard(
    problem: UnifiedModuleProblem,
    onAction: (UnifiedModuleBrowserAction) -> Unit,
) {
    val color = problem.severity.color()
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.40f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = problem.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(problem.moduleTitle, problem.sourceLabel).joinToString(" · ").ifBlank { problem.kind.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    color = color.copy(alpha = 0.10f),
                    contentColor = color,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = problem.severity.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Text(
                text = problem.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (problem.evidence.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    problem.evidence.take(4).forEach { evidence ->
                        UnifiedDiagnosticText(evidence.label, evidence.value)
                    }
                }
            }

            if (problem.actions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    problem.actions.forEach { action ->
                        val browserAction = remember(problem, action) {
                            UnifiedModuleBrowserActionPlanner.forProblem(problem, action)
                        }
                        AssistChip(
                            enabled = browserAction.enabled,
                            onClick = { onAction(browserAction) },
                            label = { Text(browserAction.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnifiedModuleBrowserCard(
    item: UnifiedModuleItem,
    density: UnifiedModuleDensityMode,
    onAction: (UnifiedModuleBrowserAction) -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, item.problemColor().copy(alpha = 0.36f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitle.ifBlank { item.displayId },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (density == UnifiedModuleDensityMode.COMPACT) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                UnifiedStatePill(item.state.installState.label)
            }

            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = density.maxDescriptionLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                UnifiedTinyPill(item.sourceMode.label)
                item.repositoryName?.takeIf(String::isNotBlank)?.let { UnifiedTinyPill(it) }
                item.sourceTypes.forEach { UnifiedTinyPill(it.label) }
            }

            if (density.showBadges && item.badges.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    item.badges.take(density.badgeLimit()).forEach { badge -> UnifiedBadgePill(badge) }
                    val hidden = item.badges.size - density.badgeLimit()
                    if (hidden > 0) UnifiedTinyPill("+$hidden badges")
                }
            }

            val actions = remember(item) { UnifiedModuleBrowserActionPlanner.forItem(item) }
            if (actions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    actions.take(density.actionLimit()).forEach { action ->
                        AssistChip(
                            enabled = action.enabled,
                            onClick = { onAction(action) },
                            label = { Text(action.label) },
                        )
                    }
                    val hiddenActions = actions.size - density.actionLimit()
                    if (hiddenActions > 0) UnifiedTinyPill("+$hiddenActions actions")
                }
            }

            if (density.showDiagnostics) {
                HorizontalDivider()
                UnifiedDiagnosticText("ID", item.displayId)
                UnifiedDiagnosticText("Canonical", item.canonicalId)
                UnifiedDiagnosticText("Match", "${item.match.reason} · ${item.match.confidence}% · ${item.match.explanation}")
                if (item.aliases.isNotEmpty()) {
                    UnifiedDiagnosticText("Aliases", item.aliases.joinToString())
                }
                item.sourceUrl?.takeIf(String::isNotBlank)?.let { UnifiedDiagnosticText("Source", it) }
                when (val scope = item.state.scope) {
                    is UnifiedScopeState.Lsposed -> UnifiedDiagnosticText(
                        "Scope",
                        buildString {
                            append("${scope.scopedPackageCount} packages · ")
                            append(if (scope.enabled) "enabled" else "disabled")
                            if (scope.autoInclude) append(" · auto")
                        },
                    )
                    UnifiedScopeState.None -> Unit
                }
            }
        }
    }
}

@Composable
private fun UnifiedStatePill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnifiedBadgePill(badge: UnifiedModuleBadge) {
    val color = badge.severity.color()
    Surface(
        modifier = Modifier.widthIn(max = 240.dp).heightIn(min = 30.dp),
        color = color.copy(alpha = 0.10f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Text(
            text = badge.label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnifiedTinyPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnifiedDiagnosticText(
    label: String,
    value: String,
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun UnifiedModuleBrowserStats.countFor(view: UnifiedModuleView): Int = when (view) {
    UnifiedModuleView.INSTALLED -> installed
    UnifiedModuleView.REPOSITORY -> repository
    UnifiedModuleView.UPDATES -> updates
    UnifiedModuleView.SCOPES -> scopes
    UnifiedModuleView.PROBLEMS -> problems
    UnifiedModuleView.GITHUB_SOURCES -> githubSources
}

private fun UnifiedModuleSortMode.label(): String = when (this) {
    UnifiedModuleSortMode.INSTALLED_FIRST -> "Installed first"
    UnifiedModuleSortMode.UPDATE_AVAILABLE_FIRST -> "Updates first"
    UnifiedModuleSortMode.PROBLEM_SEVERITY -> "Problem severity"
    UnifiedModuleSortMode.RECENTLY_UPDATED -> "Recently updated"
    UnifiedModuleSortMode.RECENTLY_INSTALLED -> "Recently installed"
    UnifiedModuleSortMode.MOST_SCOPED_APPS -> "Most scoped apps"
    UnifiedModuleSortMode.PROVIDER_COMPATIBILITY -> "Provider compatibility"
    UnifiedModuleSortMode.NAME_A_Z -> "Name A-Z"
}

private fun UnifiedModuleDensityMode.badgeLimit(): Int = when (this) {
    UnifiedModuleDensityMode.COMPACT -> 4
    UnifiedModuleDensityMode.COMFORTABLE -> 8
    UnifiedModuleDensityMode.DIAGNOSTIC -> Int.MAX_VALUE
}

private fun UnifiedModuleDensityMode.actionLimit(): Int = when (this) {
    UnifiedModuleDensityMode.COMPACT -> 2
    UnifiedModuleDensityMode.COMFORTABLE -> 4
    UnifiedModuleDensityMode.DIAGNOSTIC -> Int.MAX_VALUE
}

@Composable
private fun UnifiedModuleBrowserActionTone.color(): Color = when (this) {
    UnifiedModuleBrowserActionTone.SUCCESS -> MaterialTheme.colorScheme.tertiary
    UnifiedModuleBrowserActionTone.INFO -> MaterialTheme.colorScheme.primary
    UnifiedModuleBrowserActionTone.WARNING -> MaterialTheme.colorScheme.error
    UnifiedModuleBrowserActionTone.BLOCKED -> MaterialTheme.colorScheme.error
}

@Composable
private fun UnifiedBadgeSeverity.color(): Color = when (this) {
    UnifiedBadgeSeverity.INFO -> MaterialTheme.colorScheme.primary
    UnifiedBadgeSeverity.SUCCESS -> MaterialTheme.colorScheme.tertiary
    UnifiedBadgeSeverity.WARNING -> MaterialTheme.colorScheme.error
    UnifiedBadgeSeverity.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
private fun UnifiedModuleItem.problemColor(): Color = when (badges.maxByOrNull { it.severity.ordinal }?.severity) {
    UnifiedBadgeSeverity.WARNING,
    UnifiedBadgeSeverity.ERROR,
    -> MaterialTheme.colorScheme.error
    UnifiedBadgeSeverity.SUCCESS -> MaterialTheme.colorScheme.tertiary
    UnifiedBadgeSeverity.INFO,
    null,
    -> MaterialTheme.colorScheme.outlineVariant
}

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value
