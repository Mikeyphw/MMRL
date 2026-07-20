package com.dergoogler.mmrl.ui.screens.moduleView.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ash.AshOperationKind
import com.dergoogler.mmrl.ash.AshViewModel
import com.dergoogler.mmrl.ash.model.AshInstallMode
import com.dergoogler.mmrl.ash.model.AshModuleLifecycleState
import com.dergoogler.mmrl.ui.activity.terminal.install.InstallActivity
import com.dergoogler.mmrl.ui.component.FlatSectionCard
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.ramcosta.composedestinations.generated.destinations.AshScreenDestination

@Composable
internal fun AshReXcueIntegrationCard(viewModel: AshViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = LocalDestinationsNavigator.current
    val lifecycle = state.lifecycle
    val preparingUpdate = state.isOperationRunning(
        AshOperationKind.PrepareModuleInstall,
        AshInstallMode.Update.name,
    )
    val preparingReinstall = state.isOperationRunning(
        AshOperationKind.PrepareModuleInstall,
        AshInstallMode.Reinstall.name,
    )
    val preparingModule = state.isOperationRunning(AshOperationKind.PrepareModuleInstall)

    LaunchedEffect(viewModel, context) {
        viewModel.moduleInstalls.collect { prepared ->
            InstallActivity.start(context = context, uri = prepared.uri)
        }
    }

    FlatSectionCard(
        title = stringResource(R.string.ash_integration_title),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                stringResource(
                    R.string.ash_integration_versions,
                    lifecycle.installation.version.ifBlank { stringResource(R.string.unknown) },
                    lifecycle.installation.versionCode,
                    lifecycle.bundled.version.ifBlank { stringResource(R.string.unknown) },
                    lifecycle.bundled.versionCode,
                ),
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
                    Text(stringResource(R.string.recovery_open_center))
                }
                if (lifecycle.updateAvailable || lifecycle.state == AshModuleLifecycleState.Outdated) {
                    OutlinedButton(
                        onClick = { viewModel.prepareModuleInstall(AshInstallMode.Update) },
                        enabled = !preparingModule,
                    ) {
                        Text(
                            if (preparingUpdate) {
                                stringResource(R.string.ash_preparing_update)
                            } else {
                                stringResource(R.string.ash_update_bundled_module)
                            },
                        )
                    }
                }
                if (lifecycle.reinstallRecommended || lifecycle.state == AshModuleLifecycleState.Broken) {
                    OutlinedButton(
                        onClick = { viewModel.prepareModuleInstall(AshInstallMode.Reinstall) },
                        enabled = !preparingModule,
                    ) {
                        Text(
                            if (preparingReinstall) {
                                stringResource(R.string.ash_preparing_reinstall)
                            } else {
                                stringResource(R.string.recovery_reinstall_module)
                            },
                        )
                    }
                }
            }
            if (lifecycle.rebootRequired) {
                Text(
                    stringResource(R.string.ash_reboot_required_controls),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
