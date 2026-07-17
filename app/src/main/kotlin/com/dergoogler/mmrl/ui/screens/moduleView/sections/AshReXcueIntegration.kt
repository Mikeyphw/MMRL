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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.ash.AshViewModel
import com.dergoogler.mmrl.ash.model.AshInstallMode
import com.dergoogler.mmrl.ash.model.AshModuleLifecycleState
import com.dergoogler.mmrl.ui.activity.terminal.install.InstallActivity
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.ramcosta.composedestinations.generated.destinations.AshScreenDestination

@Composable
internal fun AshReXcueIntegrationCard(viewModel: AshViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalDestinationsNavigator.current
    val lifecycle = state.lifecycle

    LaunchedEffect(viewModel, context) {
        viewModel.moduleInstalls.collect { prepared ->
            InstallActivity.start(context = context, uri = prepared.uri)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("AshReXcue integration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Installed ${lifecycle.installation.version.ifBlank { "unknown" }} (${lifecycle.installation.versionCode}) • " +
                    "Bundled ${lifecycle.bundled.version.ifBlank { "unknown" }} (${lifecycle.bundled.versionCode})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                lifecycle.compatibilityMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (lifecycle.compatible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { navigator.navigate(AshScreenDestination) }) {
                    Text("Open protection")
                }
                if (lifecycle.updateAvailable || lifecycle.state == AshModuleLifecycleState.Outdated) {
                    OutlinedButton(
                        onClick = { viewModel.prepareModuleInstall(AshInstallMode.Update) },
                        enabled = !state.loading,
                    ) {
                        Text("Update bundled module")
                    }
                }
                if (lifecycle.reinstallRecommended || lifecycle.state == AshModuleLifecycleState.Broken) {
                    OutlinedButton(
                        onClick = { viewModel.prepareModuleInstall(AshInstallMode.Reinstall) },
                        enabled = !state.loading,
                    ) {
                        Text("Reinstall module")
                    }
                }
            }
            if (lifecycle.rebootRequired) {
                Text(
                    "Reboot required before live protection controls are available.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
