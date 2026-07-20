package com.dergoogler.mmrl.ash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
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
import com.dergoogler.mmrl.ui.component.FlatSectionCard

@Composable
internal fun GuidedRecoveryContent(
    state: AshUiState,
    onExecutePlan: (AshRecoveryPlan) -> Unit,
    onMarkSuspect: (String) -> Unit,
    onCompleteTrial: () -> Unit,
    onRollbackTrial: () -> Unit,
    onOutcome: (String, String, AshGuidanceOutcome) -> Unit,
    bottomPadding: Dp,
    showPlanningTools: Boolean = true,
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
            modifier = Modifier.imePadding(),
            onDismissRequest = { trialDecision = null },
            title = { Text(if (decision == AshGuidanceOutcome.Helped) stringResource(R.string.recovery_complete_trial_title) else stringResource(R.string.recovery_rollback_trial_title)) },
            text = {
                ScrollableRecoveryDialogContent {
                    Text(
                        if (decision == AshGuidanceOutcome.Helped) {
                            stringResource(R.string.guidance_complete_trial_confirm_desc)
                        } else {
                            stringResource(R.string.recovery_rollback_trial_desc)
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        trialDecision = null
                        if (decision == AshGuidanceOutcome.Helped) onCompleteTrial() else onRollbackTrial()
                    },
                ) { Text(if (decision == AshGuidanceOutcome.Helped) stringResource(R.string.recovery_complete) else stringResource(R.string.recovery_rollback)) }
            },
            dismissButton = { TextButton(onClick = { trialDecision = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GuidanceCard(if (showPlanningTools) stringResource(R.string.guidance_title) else stringResource(R.string.guidance_investigate_title)) {
                Text(
                    if (guidance.activeIncident) {
                        stringResource(R.string.guidance_active_incident_desc)
                    } else {
                        stringResource(R.string.guidance_no_active_incident_desc)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.guidance_revision_recheck_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (showPlanningTools) {
            item {
                Text(stringResource(R.string.guidance_recovery_plans), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.guidance_recovery_plans_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(recoveryPlans, key = AshRecoveryPlan::id) { plan ->
                RecoveryPlanCard(
                    plan = plan,
                    readOnly = state.readOnly,
                    running = state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan, plan.id),
                    anotherPlanRunning = state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan),
                    onPreview = { pendingPlan = plan },
                )
            }

            if (state.quarantine.isNotEmpty()) {
                item {
                    GuidanceCard(stringResource(R.string.guidance_custom_plan)) {
                        Text(
                            stringResource(R.string.guidance_custom_plan_desc),
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
                                    enabled = !unavailable && !state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan),
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
                            enabled = selectedFolders.isNotEmpty() && !state.readOnly && !state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.guidance_preview_custom_plan)) }
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.guidance_recommended_steps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(guidance.recommendations, key = AshRecoveryRecommendation::id) { recommendation ->
            RecommendationCard(
                recommendation = recommendation,
                readOnly = state.readOnly,
                actionRunning = recommendation.affectedFolders.any { folder ->
                    state.isOperationRunning(AshOperationKind.SetTrust, folder)
                } || state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan),
                feedbackRunning = state.isOperationRunning(
                    AshOperationKind.RecordGuidanceOutcome,
                    recommendation.id,
                ),
                completingTrial = state.isOperationRunning(AshOperationKind.CompleteTrial),
                rollingBackTrial = state.isOperationRunning(AshOperationKind.RollbackTrial),
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
            Text(stringResource(R.string.guidance_likely_culprits), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.guidance_likely_culprits_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (guidance.candidates.isEmpty()) {
            item {
                GuidanceCard(stringResource(R.string.guidance_not_enough_evidence)) {
                    Text(stringResource(R.string.guidance_not_enough_evidence_desc))
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
    running: Boolean,
    anotherPlanRunning: Boolean,
    onPreview: () -> Unit,
) {
    GuidanceCard(plan.title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(plan.risk.labelText(), riskColor(plan.risk))
            StatusPill(stringResource(R.string.module_count, plan.affectedFolders.size))
            if (!plan.canExecute) StatusPill(stringResource(R.string.recovery_blocked), MaterialTheme.colorScheme.error)
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
            enabled = !readOnly && !anotherPlanRunning,
            modifier = Modifier.fillMaxWidth(),
        ) { RecoveryActionLabel(running, if (plan.canExecute) stringResource(R.string.guidance_preview_plan) else stringResource(R.string.guidance_review_blockers)) }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: AshRecoveryRecommendation,
    readOnly: Boolean,
    actionRunning: Boolean,
    feedbackRunning: Boolean,
    completingTrial: Boolean,
    rollingBackTrial: Boolean,
    recordedOutcome: AshGuidanceOutcome?,
    onPreview: () -> Unit,
    onCompleteTrial: () -> Unit,
    onRollbackTrial: () -> Unit,
    onOutcome: (AshGuidanceOutcome) -> Unit,
) {
    GuidanceCard(recommendation.title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(recommendation.riskLabel)
            if (recommendation.affectedFolders.isNotEmpty()) {
                StatusPill("${recommendation.affectedFolders.size} module(s)")
            }
        }
        Text(recommendation.summary)
        Text(recommendation.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (recommendation.kind) {
            AshRecoveryRecommendationKind.ReviewTrial -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCompleteTrial, enabled = !readOnly && !completingTrial && !rollingBackTrial, modifier = Modifier.weight(1f)) {
                        RecoveryActionLabel(completingTrial, stringResource(R.string.guidance_stable))
                    }
                    OutlinedButton(onClick = onRollbackTrial, enabled = !readOnly && !rollingBackTrial && !completingTrial, modifier = Modifier.weight(1f)) {
                        RecoveryActionLabel(rollingBackTrial, stringResource(R.string.install_failure))
                    }
                }
            }
            AshRecoveryRecommendationKind.Observe -> Unit
            else -> FilledTonalButton(
                onClick = onPreview,
                enabled = !readOnly && !actionRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RecoveryActionLabel(
                    actionRunning,
                    if (recommendation.kind == AshRecoveryRecommendationKind.RestoreSelected) stringResource(R.string.guidance_build_guarded_plan) else stringResource(R.string.guidance_preview_action),
                )
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.guidance_feedback_question), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AshGuidanceOutcome.entries.forEach { outcome ->
                AssistChip(
                    onClick = { onOutcome(outcome) },
                    enabled = !feedbackRunning,
                    label = {
                        val label = outcome.labelText()
                        Text(if (recordedOutcome == outcome) stringResource(R.string.guidance_recorded_outcome, label) else label)
                    },
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
            Text(
                stringResource(
                    R.string.guidance_candidate_score_confidence,
                    candidate.score,
                    candidate.confidence.labelText(),
                ),
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(progress = { candidate.score / 100f }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (candidate.quarantined) StatusPill(stringResource(R.string.recovery_quarantined), MaterialTheme.colorScheme.error)
            if (candidate.protected) StatusPill(stringResource(R.string.ash_filter_protected), MaterialTheme.colorScheme.primary)
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
internal fun RecoveryPlanPreviewDialog(
    plan: AshRecoveryPlan,
    moduleNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmation by remember(plan.id) { mutableStateOf("") }
    val phraseAccepted = plan.confirmationPhrase.isBlank() || confirmation == plan.confirmationPhrase
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        title = { Text(plan.title) },
        text = {
            ScrollableRecoveryDialogContent {
                Text(plan.summary)
                Text(stringResource(R.string.recovery_risk_value, plan.risk.labelText()), fontWeight = FontWeight.SemiBold)
                if (moduleNames.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.recovery_exact_restoration_batch), style = MaterialTheme.typography.labelLarge)
                    moduleNames.forEach { Text(stringResource(R.string.bullet_text, it)) }
                }
                HorizontalDivider()
                Text(stringResource(R.string.recovery_safety_preflight), style = MaterialTheme.typography.labelLarge)
                plan.guards.forEach { guard ->
                    Text(stringResource(R.string.recovery_guard_line, guard.severity.symbol, guard.title, guard.detail), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.recovery_rollback), style = MaterialTheme.typography.labelLarge)
                Text(plan.rollbackStrategy, style = MaterialTheme.typography.bodySmall)
                if (plan.confirmationPhrase.isNotBlank()) {
                    Text(stringResource(R.string.recovery_type_phrase, plan.confirmationPhrase))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.recovery_confirmation_phrase)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = plan.canExecute && phraseAccepted) {
                Text(if (plan.canExecute) stringResource(R.string.recovery_start_trial) else stringResource(R.string.recovery_blocked))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.guidance_preview_recovery_action)) },
        text = {
            ScrollableRecoveryDialogContent {
                Text(recommendation.title, fontWeight = FontWeight.SemiBold)
                Text(recommendation.rationale)
                if (moduleNames.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.guidance_affected_modules), style = MaterialTheme.typography.labelLarge)
                    moduleNames.forEach { Text(stringResource(R.string.bullet_text, it)) }
                }
                Text(stringResource(R.string.recovery_risk_value, recommendation.riskLabel), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.guidance_run_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun GuidanceCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    FlatSectionCard(title = title, content = content)
}

@Composable
private fun AshGuidanceOutcome.labelText(): String = when (this) {
    AshGuidanceOutcome.Helped -> stringResource(R.string.guidance_outcome_helped)
    AshGuidanceOutcome.Failed -> stringResource(R.string.guidance_outcome_failed)
    AshGuidanceOutcome.Inconclusive -> stringResource(R.string.guidance_outcome_inconclusive)
}

@Composable
private fun AshGuidanceConfidence.labelText(): String = when (this) {
    AshGuidanceConfidence.Low -> stringResource(R.string.guidance_confidence_low)
    AshGuidanceConfidence.Medium -> stringResource(R.string.guidance_confidence_medium)
    AshGuidanceConfidence.High -> stringResource(R.string.guidance_confidence_high)
    AshGuidanceConfidence.VeryHigh -> stringResource(R.string.guidance_confidence_very_high)
}

@Composable
private fun riskColor(risk: AshRecoveryPlanRisk) = when (risk) {
    AshRecoveryPlanRisk.Low -> MaterialTheme.colorScheme.primary
    AshRecoveryPlanRisk.Moderate -> MaterialTheme.colorScheme.tertiary
    AshRecoveryPlanRisk.High,
    AshRecoveryPlanRisk.Blocked,
    -> MaterialTheme.colorScheme.error
}

@Composable
private fun AshRecoveryPlanRisk.labelText(): String = when (this) {
    AshRecoveryPlanRisk.Low -> stringResource(R.string.recovery_plan_risk_low)
    AshRecoveryPlanRisk.Moderate -> stringResource(R.string.recovery_plan_risk_moderate)
    AshRecoveryPlanRisk.High -> stringResource(R.string.recovery_plan_risk_high)
    AshRecoveryPlanRisk.Blocked -> stringResource(R.string.recovery_blocked)
}

private val AshRecoveryGuardSeverity.symbol: String
    get() = when (this) {
        AshRecoveryGuardSeverity.Info -> "✓"
        AshRecoveryGuardSeverity.Warning -> "!"
        AshRecoveryGuardSeverity.Blocker -> "×"
    }
