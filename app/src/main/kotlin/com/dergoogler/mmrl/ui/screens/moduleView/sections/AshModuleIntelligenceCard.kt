package com.dergoogler.mmrl.ui.screens.moduleView.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.ash.AshViewModel
import com.dergoogler.mmrl.ash.model.AshModuleIntelligence
import com.dergoogler.mmrl.ash.model.AshModuleIntelligenceEngine
import com.dergoogler.mmrl.ash.model.AshModuleRiskBand
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.AshUpdateSafetyInput
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.state.Permissions
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.isEmpty
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

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "AshReXcue module intelligence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                IntelligenceTag("${intelligence.riskBand.name} · ${intelligence.riskScore}/100", riskColor)
                IntelligenceTag(intelligence.trust.replaceFirstChar(Char::uppercaseChar), trustColor(intelligence.trust))
                if (intelligence.changedSinceStable) {
                    IntelligenceTag("Changed since stable", MaterialTheme.colorScheme.tertiary)
                }
                if (intelligence.quarantined) {
                    IntelligenceTag("Quarantined", MaterialTheme.colorScheme.error)
                }
                if (intelligence.source == AshSnapshotSource.Cache || intelligence.readOnly) {
                    IntelligenceTag("Read-only evidence", MaterialTheme.colorScheme.onSurfaceVariant)
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

            if (intelligence.factors.isNotEmpty()) {
                Text("Evidence", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                intelligence.factors.take(3).forEach { factor ->
                    Text(
                        "• ${factor.title}: ${factor.detail}",
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
                        Text("Update safety", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        IntelligenceTag("${safety.riskBand.name} · ${safety.riskScore}/100", safetyColor)
                    }
                    Text(safety.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        safety.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    safety.reasons.take(3).forEach { reason ->
                        Text(
                            "• $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { navigator.navigate(AshScreenDestination) }) {
                    Text("Open Recovery Center")
                }
                OutlinedButton(onClick = viewModel::refreshAll, enabled = !state.loading) {
                    Text("Refresh evidence")
                }
                if (writable && intelligence.riskBand >= AshModuleRiskBand.High && intelligence.trust != "suspect") {
                    OutlinedButton(
                        onClick = { viewModel.setTrust(intelligence.folder, "suspect") },
                        enabled = !state.loading,
                    ) {
                        Text("Mark suspect")
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
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
