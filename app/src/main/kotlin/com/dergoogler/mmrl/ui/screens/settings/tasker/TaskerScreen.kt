package com.dergoogler.mmrl.ui.screens.settings.tasker

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.datastore.model.TaskerApprovalPolicy
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.ui.component.SettingsScaffold
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.RadioDialogItem
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.Section
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.SwitchItem
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.TextEditDialogItem
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.item.Description
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.item.Title
import com.dergoogler.mmrl.ui.providable.LocalSettings
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph

@Destination<RootGraph>
@Composable
fun TaskerScreen() {
    val preferences = LocalUserPreferences.current
    val settings = LocalSettings.current
    val enabled = preferences.taskerIntegrationEnabled

    SettingsScaffold(title = R.string.settings_tasker) {
        SwitchItem(
            checked = enabled,
            onChange = settings::setTaskerIntegrationEnabled,
        ) {
            Title(R.string.settings_tasker_enabled)
            Description(R.string.settings_tasker_enabled_desc)
        }

        Section(title = stringResource(R.string.settings_tasker_capabilities)) {
            SwitchItem(
                enabled = enabled,
                checked = preferences.taskerAllowDownloads,
                onChange = settings::setTaskerAllowDownloads,
            ) {
                Title(R.string.settings_tasker_downloads)
                Description(R.string.settings_tasker_downloads_desc)
            }
            SwitchItem(
                enabled = enabled,
                checked = preferences.taskerAllowStateChanges,
                onChange = settings::setTaskerAllowStateChanges,
            ) {
                Title(R.string.settings_tasker_state_changes)
                Description(R.string.settings_tasker_state_changes_desc)
            }
            SwitchItem(
                enabled = enabled,
                checked = preferences.taskerAllowModuleActions,
                onChange = settings::setTaskerAllowModuleActions,
            ) {
                Title(R.string.settings_tasker_module_actions)
                Description(R.string.settings_tasker_module_actions_desc)
            }
            SwitchItem(
                enabled = enabled,
                checked = preferences.taskerAllowRemovals,
                onChange = settings::setTaskerAllowRemovals,
            ) {
                Title(R.string.settings_tasker_removals)
                Description(R.string.settings_tasker_removals_desc)
            }
            SwitchItem(
                enabled = enabled,
                checked = preferences.taskerAllowReviewedInstalls,
                onChange = settings::setTaskerAllowReviewedInstalls,
            ) {
                Title(R.string.settings_tasker_reviewed_installs)
                Description(R.string.settings_tasker_reviewed_installs_desc)
            }
        }

        Section(title = stringResource(R.string.settings_tasker_approval)) {
            RadioDialogItem(
                enabled = enabled,
                selection = preferences.taskerApprovalPolicy,
                options = listOf(
                    RadioDialogItem(TaskerApprovalPolicy.ALWAYS_ASK, stringResource(R.string.settings_tasker_policy_always_ask), stringResource(R.string.settings_tasker_policy_always_ask_desc)),
                    RadioDialogItem(TaskerApprovalPolicy.DEVICE_UNLOCKED, stringResource(R.string.settings_tasker_policy_unlocked), stringResource(R.string.settings_tasker_policy_unlocked_desc)),
                    RadioDialogItem(TaskerApprovalPolicy.MODULE_ALLOWLIST, stringResource(R.string.settings_tasker_policy_allowlist), stringResource(R.string.settings_tasker_policy_allowlist_desc)),
                    RadioDialogItem(TaskerApprovalPolicy.NEVER, stringResource(R.string.settings_tasker_policy_never), stringResource(R.string.settings_tasker_policy_never_desc)),
                ),
                onConfirm = { settings.setTaskerApprovalPolicy(it.value) },
            ) {
                Title(R.string.settings_tasker_approval_policy)
                Description(it.title.orEmpty())
            }

            TextEditDialogItem(
                enabled = enabled,
                value = preferences.taskerAllowedModules.sorted().joinToString("\n"),
                strict = false,
                onConfirm = { value ->
                    settings.setTaskerAllowedModules(
                        value.lineSequence()
                            .flatMap { it.split(',').asSequence() }
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .map(ModuleIdentity::normalize)
                            .toSet(),
                    )
                },
            ) {
                Title(R.string.settings_tasker_allowlist)
                Description(
                    if (preferences.taskerAllowedModules.isEmpty()) {
                        stringResource(R.string.settings_tasker_allowlist_empty)
                    } else {
                        stringResource(R.string.settings_tasker_allowlist_count, preferences.taskerAllowedModules.size)
                    },
                )
            }
        }
    }
}
