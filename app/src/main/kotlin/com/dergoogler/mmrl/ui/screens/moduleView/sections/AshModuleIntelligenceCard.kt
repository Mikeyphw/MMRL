package com.dergoogler.mmrl.ui.screens.moduleView.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ash.AshOperationKind
import com.dergoogler.mmrl.ash.AshViewModel
import com.dergoogler.mmrl.ash.model.AshModuleIntelligence
import com.dergoogler.mmrl.ash.model.AshModuleIntelligenceEngine
import com.dergoogler.mmrl.ash.model.AshModuleRiskBand
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.AshUpdateSafetyInput
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.state.Permissions
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.isEmpty
import com.dergoogler.mmrl.ui.component.FlatSectionCard
import com.dergoogler.mmrl.ui.component.StatusPill
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.dergoogler.mmrl.ui.providable.LocalModule
import com.dergoogler.mmrl.ui.providable.LocalOnlineModule
import com.dergoogler.mmrl.ui.providable.LocalVersionItem
import com.ramcosta.composedestinations.generated.destinations.AshScreenDestination

@Composable
internal fun AshModuleIntelligenceCard(viewModel: AshViewModel = hiltViewModel()) {
    val local = LocalModule.current
    val online = LocalOnlineModule.current
    val version = LocalVersionItem.current
    if (local.isEmpty || ModuleIdentity.matches(online.id, "AshLooper")) return

    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalDestinationsNavigator.current
    val intelligence = findIntelligence(
        state.moduleIntelligence,
        local.id.id,
        online.id,
    ) ?: return
    val permissions = online.permissions.orEmpty()
    @Suppress("DEPRECATION")
    val features = online.features
    val safety = AshModuleIntelligenceEngine.assessUpdate(
        AshUpdateSafetyInput(
            installed = true,
            updateAvailable = version.versionCode > local.versionCode,
            hasBootScripts =
                Permissions.MAGISK_SERVICE in permissions ||
                    Permissions.MAGISK_POST_FS_DATA in permissions ||
                    Permissions.KERNELSU_POST_MOUNT in permissions ||
                    Permissions.KERNELSU_BOOT_COMPLETED in permissions ||
                    features?.service == true ||
                    features?.postFsData == true ||
                    features?.postMount == true ||
                    features?.bootCompleted == true,
            hasNativeCode = Permissions.MAGISK_ZYGISK in permissions || features?.zygisk == true,
            changesSePolicy = Permissions.MAGISK_SEPOLICY in permissions || features?.sepolicy == true,
            changesSystemProperties = Permissions.MAGISK_RESETPROP in permissions || features?.resetprop == true,
            bundlesApks = Permissions.MMRL_APKS in permissions || features?.apks == true,
            intelligence = intelligence,
        ),
    )
    val riskColor = riskColor(intelligence.riskBand)
    val safetyColor = riskColor(safety.riskBand)
    val writable = !state.readOnly && state.snapshotSource == AshSnapshotSource.Live
    var expanded by rememberSaveable(intelligence.folder) { mutableStateOf(false) }

    FlatSectionCard(
        title = stringResource(R.string.ash_intelligence_title),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                IntelligenceTag(stringResource(R.string.ash_intelligence_score, intelligence.riskBand.labelText(), intelligence.riskScore), riskColor)
                IntelligenceTag(trustLabelText(intelligence.trust), trustColor(intelligence.trust))
                if (intelligence.changedSinceStable) {
                    IntelligenceTag(stringResource(R.string.ash_changed_since_stable), MaterialTheme.colorScheme.tertiary)
                }
                if (intelligence.quarantined) {
                    IntelligenceTag(stringResource(R.string.recovery_quarantined), MaterialTheme.colorScheme.error)
                }
                if (intelligence.source == AshSnapshotSource.Cache || intelligence.readOnly) {
                    IntelligenceTag(stringResource(R.string.ash_read_only_evidence), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                intelligence.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                intelligence.recommendedAction,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )

            val hasDetails = intelligence.factors.isNotEmpty() ||
                version.versionCode > local.versionCode || safety.shouldReviewBeforeInstall
            if (hasDetails) {
                val expandedDescription = if (expanded) {
                    stringResource(R.string.accessibility_expanded)
                } else {
                    stringResource(R.string.accessibility_collapsed)
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth().semantics {
                        role = Role.Button
                        stateDescription = expandedDescription
                    },
                ) {
                    Text(if (expanded) stringResource(R.string.hide_details) else stringResource(R.string.show_details))
                }
            }

            if (expanded) {
                if (intelligence.factors.isNotEmpty()) {
                    Text(stringResource(R.string.ash_evidence), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    intelligence.factors.take(3).forEach { factor ->
                        Text(
                            stringResource(R.string.bullet_title_detail, factor.title, factor.detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (version.versionCode > local.versionCode || safety.shouldReviewBeforeInstall) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.ash_update_safety), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            IntelligenceTag(stringResource(R.string.ash_intelligence_score, safety.riskBand.labelText(), safety.riskScore), safetyColor)
                        }
                        Text(safety.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            safety.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        safety.reasons.take(3).forEach { reason ->
                            Text(
                                stringResource(R.string.bullet_text, reason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { navigator.navigate(AshScreenDestination) }) {
                    Text(stringResource(R.string.recovery_open_center))
                }
                OutlinedButton(onClick = viewModel::refreshAll, enabled = !state.refreshing) {
                    Text(if (state.refreshing) stringResource(R.string.refreshing) else stringResource(R.string.ash_refresh_evidence))
                }
                if (writable && intelligence.riskBand >= AshModuleRiskBand.High && intelligence.trust != "suspect") {
                    OutlinedButton(
                        onClick = { viewModel.setTrust(intelligence.folder, "suspect") },
                        enabled = !state.isOperationRunning(AshOperationKind.SetTrust, intelligence.folder),
                    ) {
                        Text(
                            if (state.isOperationRunning(AshOperationKind.SetTrust, intelligence.folder)) {
                                stringResource(R.string.ash_marking)
                            } else {
                                stringResource(R.string.ash_mark_suspect)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun findIntelligence(
    index: Map<String, AshModuleIntelligence>,
    vararg identities: String,
): AshModuleIntelligence? = identities
    .asSequence()
    .map(ModuleIdentity::normalize)
    .mapNotNull(index::get)
    .firstOrNull()

@Composable
private fun AshModuleRiskBand.labelText(): String = when (this) {
    AshModuleRiskBand.Unknown -> stringResource(R.string.ash_risk_band_unknown)
    AshModuleRiskBand.Low -> stringResource(R.string.ash_risk_band_low)
    AshModuleRiskBand.Elevated -> stringResource(R.string.ash_risk_band_elevated)
    AshModuleRiskBand.High -> stringResource(R.string.ash_risk_band_high)
    AshModuleRiskBand.Critical -> stringResource(R.string.ash_risk_band_critical)
}

@Composable
private fun trustLabelText(trust: String): String = when (trust) {
    "protected" -> stringResource(R.string.ash_filter_protected)
    "trusted" -> stringResource(R.string.ash_filter_trusted)
    "suspect" -> stringResource(R.string.ash_filter_suspect)
    "normal" -> stringResource(R.string.ash_filter_normal)
    else -> trust.replaceFirstChar(Char::uppercaseChar)
}

@Composable
private fun riskColor(band: AshModuleRiskBand): Color = when (band) {
    AshModuleRiskBand.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    AshModuleRiskBand.Low -> MaterialTheme.colorScheme.primary
    AshModuleRiskBand.Elevated -> MaterialTheme.colorScheme.tertiary
    AshModuleRiskBand.High,
    AshModuleRiskBand.Critical,
    -> MaterialTheme.colorScheme.error
}

@Composable
private fun trustColor(trust: String): Color = when (trust) {
    "protected" -> MaterialTheme.colorScheme.primary
    "trusted" -> MaterialTheme.colorScheme.tertiary
    "suspect" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun IntelligenceTag(text: String, color: Color) {
    StatusPill(text = text, color = color)
}
