package com.dergoogler.mmrl.ash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.ash.model.AshCulpritCandidate
import com.dergoogler.mmrl.ash.model.AshGuidanceConfidence
import com.dergoogler.mmrl.ash.model.AshGuidanceEngine
import com.dergoogler.mmrl.ash.model.AshGuidanceOutcome
import com.dergoogler.mmrl.ash.model.AshRecoveryGuardSeverity
import com.dergoogler.mmrl.ash.model.AshRecoveryPlan
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanEngine
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanRisk
import com.dergoogler.mmrl.ash.model.AshRecoveryRecommendation
import com.dergoogler.mmrl.ash.model.AshRecoveryRecommendationKind
import com.dergoogler.mmrl.ash.model.AshSnapshot

@Composable
internal fun GuidedRecoveryContent(
    state: AshUiState,
    onExecutePlan: (AshRecoveryPlan) -> Unit,
    onMarkSuspect: (String) -> Unit,
    onCompleteTrial: () -> Unit,
    onRollbackTrial: () -> Unit,
    onOutcome: (String, String, AshGuidanceOutcome) -> Unit,
    bottomPadding: Dp,
) {
    val snapshot = remember(
        state.snapshotGeneratedAt,
        state.recoveryRevision,
        state.capabilities,
        state.dashboard,
        state.modules,
        state.quarantine,
        state.activity,
    ) {
        AshSnapshot(
            generatedAt = state.snapshotGeneratedAt,
            recoveryRevision = state.recoveryRevision,
            capabilities = state.capabilities,
            dashboard = state.dashboard,
            modules = state.modules,
            quarantine = state.quarantine,
            activity = state.activity,
        )
    }
    val guidance = remember(snapshot) { AshGuidanceEngine.build(snapshot) }
    val recoveryPlans = remember(snapshot, guidance) { AshRecoveryPlanEngine.presets(snapshot, guidance) }
    var pendingRecommendation by remember { mutableStateOf<AshRecoveryRecommendation?>(null) }
    var pendingPlan by remember { mutableStateOf<AshRecoveryPlan?>(null) }
    var trialDecision by remember { mutableStateOf<AshGuidanceOutcome?>(null) }
    var selectedFolders by remember(snapshot.recoveryRevision) { mutableStateOf(emptySet<String>()) }

    pendingRecommendation?.let { recommendation ->
        RecommendationPreviewDialog(
            recommendation = recommendation,
            moduleNames = recommendation.affectedFolders.map { folder ->
                state.modules.firstOrNull { it.folder == folder }?.name ?: folder
            },
            onDismiss = { pendingRecommendation = null },
            onConfirm = {
                pendingRecommendation = null
                recommendation.affectedFolders.firstOrNull()?.let(onMarkSuspect)
            },
        )
    }

    pendingPlan?.let { plan ->
        RecoveryPlanPreviewDialog(
            plan = plan,
            moduleNames = plan.affectedFolders.map { folder ->
                state.modules.firstOrNull { it.folder == folder }?.name ?: folder
            },
            onDismiss = { pendingPlan = null },
            onConfirm = {
                pendingPlan = null
                onExecutePlan(plan)
            },
        )
    }

    trialDecision?.let { decision ->
        AlertDialog(
            onDismissRequest = { trialDecision = null },
            title = { Text(if (decision == AshGuidanceOutcome.Helped) "Complete restoration trial?" else "Roll back restoration trial?") },
            text = {
                Text(
                    if (decision == AshGuidanceOutcome.Helped) {
                        "Complete only after confirming the restored batch survived the stability window."
                    } else {
                        "The tested modules will be disabled and returned to quarantine."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        trialDecision = null
                        if (decision == AshGuidanceOutcome.Helped) onCompleteTrial() else onRollbackTrial()
                    },
                ) { Text(if (decision == AshGuidanceOutcome.Helped) "Complete" else "Rollback") }
            },
            dismissButton = { TextButton(onClick = { trialDecision = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GuidanceCard("Guided recovery") {
                Text(
                    if (guidance.activeIncident) {
                        "Evidence is ranked from the current incident, stable-snapshot changes, quarantine ownership, and recovery history."
                    } else {
                        "No active rescue is reported. Guidance remains available for quarantine and restoration decisions."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Phase F binds every plan to the exact live recovery revision and rechecks it before module state changes.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            Text("Recovery plans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Plans are previews, not automation. A failed test boot re-quarantines only the tested batch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(recoveryPlans, key = AshRecoveryPlan::id) { plan ->
            RecoveryPlanCard(
                plan = plan,
                readOnly = state.readOnly,
                loading = state.loading,
                onPreview = { pendingPlan = plan },
            )
        }

        if (state.quarantine.isNotEmpty()) {
            item {
                GuidanceCard("Custom plan") {
                    Text(
                        "Select an exact batch. Protected, missing, stale, oversized, or changed-state plans are blocked during preview and again at execution.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.quarantine.forEach { item ->
                            val module = state.modules.firstOrNull { it.folder == item.folder }
                            val unavailable = !item.exists || !item.disablePresent ||
                                module?.baseTrust.equals("protected", ignoreCase = true)
                            FilterChip(
                                selected = item.folder in selectedFolders,
                                enabled = !unavailable && !state.loading,
                                onClick = {
                                    selectedFolders = if (item.folder in selectedFolders) {
                                        selectedFolders - item.folder
                                    } else {
                                        selectedFolders + item.folder
                                    }
                                },
                                label = { Text(module?.name ?: item.name.ifBlank { item.folder }) },
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            pendingPlan = AshRecoveryPlanEngine.custom(snapshot, selectedFolders.sorted())
                        },
                        enabled = selectedFolders.isNotEmpty() && !state.readOnly && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Preview custom plan") }
                }
            }
        }

        item {
            Text("Recommended next steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(guidance.recommendations, key = AshRecoveryRecommendation::id) { recommendation ->
            RecommendationCard(
                recommendation = recommendation,
                readOnly = state.readOnly,
                loading = state.loading,
                recordedOutcome = guidance.feedback[recommendation.id]?.outcome,
                onPreview = {
                    if (recommendation.kind == AshRecoveryRecommendationKind.RestoreSelected) {
                        pendingPlan = AshRecoveryPlanEngine.forRecommendation(snapshot, recommendation)
                    } else {
                        pendingRecommendation = recommendation
                    }
                },
                onCompleteTrial = { trialDecision = AshGuidanceOutcome.Helped },
                onRollbackTrial = { trialDecision = AshGuidanceOutcome.Failed },
                onOutcome = { outcome ->
                    onOutcome(
                        recommendation.id,
                        recommendation.affectedFolders.firstOrNull().orEmpty(),
                        outcome,
                    )
                },
            )
        }

        item {
            Text("Likely culprits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Ranking is explanatory evidence, not proof. Protected modules are deliberately penalized and excluded from guided mutations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (guidance.candidates.isEmpty()) {
            item {
                GuidanceCard("Not enough evidence") {
                    Text("Another failed boot, rescue manifest, or module fingerprint change is needed before ranking modules.")
                }
            }
        } else {
            items(guidance.candidates, key = AshCulpritCandidate::folder) { candidate ->
                CulpritCandidateCard(candidate)
            }
        }
    }
}

@Composable
private fun RecoveryPlanCard(
    plan: AshRecoveryPlan,
    readOnly: Boolean,
    loading: Boolean,
    onPreview: () -> Unit,
) {
    GuidanceCard(plan.title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(plan.risk.label) })
            AssistChip(onClick = {}, label = { Text("${plan.affectedFolders.size} module(s)") })
            if (!plan.canExecute) AssistChip(onClick = {}, label = { Text("Blocked") })
        }
        Text(plan.summary)
        plan.guards.take(3).forEach { guard ->
            Text(
                "${guard.severity.symbol} ${guard.title}: ${guard.detail}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = onPreview,
            enabled = !readOnly && !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (plan.canExecute) "Preview plan" else "Review blockers") }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: AshRecoveryRecommendation,
    readOnly: Boolean,
    loading: Boolean,
    recordedOutcome: AshGuidanceOutcome?,
    onPreview: () -> Unit,
    onCompleteTrial: () -> Unit,
    onRollbackTrial: () -> Unit,
    onOutcome: (AshGuidanceOutcome) -> Unit,
) {
    GuidanceCard(recommendation.title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(recommendation.riskLabel) })
            if (recommendation.affectedFolders.isNotEmpty()) {
                AssistChip(onClick = {}, label = { Text("${recommendation.affectedFolders.size} module(s)") })
            }
        }
        Text(recommendation.summary)
        Text(recommendation.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (recommendation.kind) {
            AshRecoveryRecommendationKind.ReviewTrial -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCompleteTrial, enabled = !readOnly && !loading, modifier = Modifier.weight(1f)) { Text("Stable") }
                    OutlinedButton(onClick = onRollbackTrial, enabled = !readOnly && !loading, modifier = Modifier.weight(1f)) { Text("Failed") }
                }
            }
            AshRecoveryRecommendationKind.Observe -> Unit
            else -> FilledTonalButton(
                onClick = onPreview,
                enabled = !readOnly && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (recommendation.kind == AshRecoveryRecommendationKind.RestoreSelected) "Build guarded plan" else "Preview action") }
        }

        HorizontalDivider()
        Text("Did this recommendation help?", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AshGuidanceOutcome.entries.forEach { outcome ->
                AssistChip(
                    onClick = { onOutcome(outcome) },
                    enabled = !loading,
                    label = { Text(if (recordedOutcome == outcome) "✓ ${outcome.label}" else outcome.label) },
                )
            }
        }
    }
}

@Composable
private fun CulpritCandidateCard(candidate: AshCulpritCandidate) {
    GuidanceCard(candidate.name) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(candidate.moduleId.ifBlank { candidate.folder }, style = MaterialTheme.typography.bodySmall)
            Text("${candidate.score}/100 · ${candidate.confidence.label}", fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(progress = { candidate.score / 100f }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (candidate.quarantined) AssistChip(onClick = {}, label = { Text("Quarantined") })
            if (candidate.protected) AssistChip(onClick = {}, label = { Text("Protected") })
        }
        candidate.evidence.take(4).forEach { evidence ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildString {
                        append(if (evidence.points >= 0) "+" else "")
                        append(evidence.points)
                        append(" · ")
                        append(evidence.title)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(evidence.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecoveryPlanPreviewDialog(
    plan: AshRecoveryPlan,
    moduleNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmation by remember(plan.id) { mutableStateOf("") }
    val phraseAccepted = plan.confirmationPhrase.isBlank() || confirmation == plan.confirmationPhrase
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plan.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.summary)
                Text("Risk: ${plan.risk.label}", fontWeight = FontWeight.SemiBold)
                if (moduleNames.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Exact restoration batch", style = MaterialTheme.typography.labelLarge)
                    moduleNames.forEach { Text("• $it") }
                }
                HorizontalDivider()
                Text("Safety preflight", style = MaterialTheme.typography.labelLarge)
                plan.guards.forEach { guard ->
                    Text("${guard.severity.symbol} ${guard.title}: ${guard.detail}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Rollback", style = MaterialTheme.typography.labelLarge)
                Text(plan.rollbackStrategy, style = MaterialTheme.typography.bodySmall)
                if (plan.confirmationPhrase.isNotBlank()) {
                    Text("Type ${plan.confirmationPhrase} to unlock this high-risk plan.")
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        singleLine = true,
                        label = { Text("Confirmation phrase") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = plan.canExecute && phraseAccepted) {
                Text(if (plan.canExecute) "Start trial" else "Blocked")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecommendationPreviewDialog(
    recommendation: AshRecoveryRecommendation,
    moduleNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview recovery action") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recommendation.title, fontWeight = FontWeight.SemiBold)
                Text(recommendation.rationale)
                if (moduleNames.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Affected modules", style = MaterialTheme.typography.labelLarge)
                    moduleNames.forEach { Text("• $it") }
                }
                Text("Risk: ${recommendation.riskLabel}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Run action") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GuidanceCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private val AshGuidanceOutcome.label: String
    get() = name.replaceFirstChar(Char::uppercaseChar)

private val AshGuidanceConfidence.label: String
    get() = when (this) {
        AshGuidanceConfidence.Low -> "Low confidence"
        AshGuidanceConfidence.Medium -> "Medium confidence"
        AshGuidanceConfidence.High -> "High confidence"
        AshGuidanceConfidence.VeryHigh -> "Very high confidence"
    }

private val AshRecoveryPlanRisk.label: String
    get() = when (this) {
        AshRecoveryPlanRisk.Low -> "Low risk"
        AshRecoveryPlanRisk.Moderate -> "Moderate risk"
        AshRecoveryPlanRisk.High -> "High risk"
        AshRecoveryPlanRisk.Blocked -> "Blocked"
    }

private val AshRecoveryGuardSeverity.symbol: String
    get() = when (this) {
        AshRecoveryGuardSeverity.Info -> "✓"
        AshRecoveryGuardSeverity.Warning -> "!"
        AshRecoveryGuardSeverity.Blocker -> "×"
    }
