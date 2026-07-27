package com.dergoogler.mmrl.ui.screens.settings.other

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.github.GitHubTokenStore
import com.dergoogler.mmrl.service.ProviderService
import com.dergoogler.mmrl.ui.component.SettingsScaffold
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.SwitchItem
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.TextEditDialogItem
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.item.Description
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.item.Title
import com.dergoogler.mmrl.ui.providable.LocalSettings
import com.dergoogler.mmrl.ui.providable.LocalSnackbarHost
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.ui.screens.settings.appearance.items.DownloadPathItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Destination<RootGraph>
@Composable
fun OtherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = LocalSnackbarHost.current
    val viewModel = LocalSettings.current
    val userPreferences = LocalUserPreferences.current
    val githubTokenStore = remember(context) { GitHubTokenStore(context) }
    var githubTokenSaved by remember { mutableStateOf(githubTokenStore.hasToken()) }
    var githubTokenEditorRevision by remember { mutableStateOf(0) }

    SettingsScaffold(
        title = R.string.settings_other,
    ) {
        DownloadPathItem(
            downloadPath = userPreferences.downloadPath,
            onChange = viewModel::setDownloadPath,
        )

        SwitchItem(
            checked = userPreferences.useDoh,
            onChange = viewModel::setUseDoh,
        ) {
            Title(R.string.settings_doh)
            Description(R.string.settings_doh_desc)
        }


        key(githubTokenEditorRevision) {
            TextEditDialogItem(
                value = "",
                strict = false,
                onConfirm = { token ->
                    githubTokenStore.saveToken(token)
                    githubTokenSaved = githubTokenStore.hasToken()
                    githubTokenEditorRevision++
                    scope.launch {
                        snackbarHost.showSnackbar(
                            context.getString(
                                if (githubTokenSaved) {
                                    R.string.settings_github_api_token_saved
                                } else {
                                    R.string.settings_github_api_token_cleared
                                },
                            ),
                        )
                    }
                },
            ) {
                Title(R.string.settings_github_api_token)
                Description(
                    if (githubTokenSaved) {
                        R.string.settings_github_api_token_desc_saved
                    } else {
                        R.string.settings_github_api_token_desc_empty
                    },
                )
            }
        }

        SwitchItem(
            checked = ProviderService.isActive,
            onChange = {
                scope.launch {
                    if (it) {
                        ProviderService.start(context, userPreferences.workingMode)
                        viewModel.setProviderServiceEnabled(true)
                        snackbarHost.showSnackbar(context.getString(R.string.provider_service_started))
                    } else {
                        ProviderService.stop(context)
                        while (ProviderService.isActive) {
                            delay(100)
                        }
                        viewModel.setProviderServiceEnabled(false)
                        snackbarHost.showSnackbar(context.getString(R.string.provider_service_stopped))
                    }
                }
            },
        ) {
            Title(R.string.settings_provider_service)
            Description(R.string.settings_provider_service_desc)
        }

        TextEditDialogItem(
            value = userPreferences.webuixPackageName,
            onConfirm = viewModel::setWebuixPackageName,
        ) {
            Title(context.getString(R.string.settings_set_spoofed_wxp))
            Description(context.getString(R.string.settings_set_spoofed_wxp_desc))
        }
    }
}
