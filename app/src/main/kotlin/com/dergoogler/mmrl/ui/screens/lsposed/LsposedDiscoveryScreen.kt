package com.dergoogler.mmrl.ui.screens.lsposed

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.lsposed.LsposedDiscovery
import com.dergoogler.mmrl.lsposed.LsposedRuntime
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Phase 02: discovery and explicit manager opening only. No writes, shell or scope edits. */
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun LsposedDiscoveryScreen() {
    val context = LocalContext.current
    val navigator = LocalDestinationsNavigator.current
    val scope = rememberCoroutineScope()

    var apkSnapshot by remember { mutableStateOf<LsposedDiscovery.ApkSnapshot?>(null) }
    var apkError by remember { mutableStateOf<String?>(null) }
    var providerSnapshot by remember { mutableStateOf<List<LsposedDiscovery.FrameworkModule>?>(null) }
    var providerError by remember { mutableStateOf<String?>(null) }
    var inspecting by remember { mutableStateOf(false) }
    var launchError by remember { mutableStateOf<String?>(null) }
    var runtimeSnapshot by remember { mutableStateOf<LsposedRuntime.Snapshot?>(null) }
    var runtimeError by remember { mutableStateOf<String?>(null) }
    var checkingRuntime by remember { mutableStateOf(false) }

    // Package scan is not privileged. Never read root module state on screen entry.
    LaunchedEffect(context) {
        val result = withContext(Dispatchers.IO) {
            runCatching { LsposedDiscovery.scanInstalledApks(context.applicationContext) }
        }
        result.onSuccess { apkSnapshot = it }
            .onFailure { apkError = it.javaClass.simpleName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lsposed_discovery_title)) },
                navigationIcon = {
                    TextButton(onClick = { navigator.navigateUp() }) {
                        Text(stringResource(R.string.lsposed_back))
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.lsposed_phase02_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { Text(stringResource(R.string.lsposed_module_apks), style = MaterialTheme.typography.titleMedium) }
            if (apkError != null) {
                item { Text(stringResource(R.string.lsposed_scan_failed, apkError.orEmpty())) }
            } else if (apkSnapshot == null) {
                item { Text(stringResource(R.string.lsposed_scanning)) }
            } else {
                if (apkSnapshot?.modules.isNullOrEmpty()) {
                    item { Text(stringResource(R.string.lsposed_no_apks)) }
                }
                items(apkSnapshot?.modules.orEmpty(), key = { "apk:${it.packageName}" }) { module ->
                    DiscoveryCard(
                        title = module.label,
                        detail = "${module.packageName} • ${module.format}",
                    )
                }
            }

            item { Text(stringResource(R.string.lsposed_manager_apks), style = MaterialTheme.typography.titleMedium) }
            if (apkSnapshot?.managers.isNullOrEmpty() && apkSnapshot != null) {
                item { Text(stringResource(R.string.lsposed_no_manager)) }
            }
            items(apkSnapshot?.managers.orEmpty(), key = { "manager:${it.packageName}" }) { manager ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(manager.label, style = MaterialTheme.typography.titleSmall)
                        Text(manager.packageName, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            launchError = null
                            val intent = context.packageManager.getLaunchIntentForPackage(manager.packageName)
                            if (intent == null) {
                                launchError = context.getString(R.string.lsposed_manager_unlaunchable)
                            } else {
                                val opened = runCatching {
                                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.isSuccess
                                if (!opened) {
                                    launchError = context.getString(R.string.lsposed_manager_unlaunchable)
                                }
                            }
                        }) {
                            Text(stringResource(R.string.lsposed_open_manager))
                        }
                    }
                }
            }
            if (launchError != null) {
                item { Text(launchError.orEmpty()) }
            }

            item { Text(stringResource(R.string.lsposed_framework_modules), style = MaterialTheme.typography.titleMedium) }
            item {
                Button(
                    enabled = !inspecting,
                    onClick = {
                        inspecting = true
                        providerError = null
                        providerSnapshot = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    if (!PlatformManager.isAlive) {
                                        error("MMRL module provider unavailable")
                                    }
                                    LsposedDiscovery.inspectFrameworkModules()
                                }
                            }
                            result.onSuccess { providerSnapshot = it }
                                .onFailure { providerError = it.message ?: it.javaClass.simpleName }
                            inspecting = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.lsposed_inspect_root))
                }
            }
            if (inspecting) item { Text(stringResource(R.string.lsposed_scanning)) }
            if (providerError != null) item { Text(stringResource(R.string.lsposed_scan_failed, providerError.orEmpty())) }
            if (providerSnapshot?.isEmpty() == true) item { Text(stringResource(R.string.lsposed_no_frameworks)) }
            items(providerSnapshot.orEmpty(), key = { "provider:${it.id}" }) { module ->
                DiscoveryCard(
                    title = module.name,
                    detail = "${module.family} • ${module.id} • ${module.version} • ${module.state}",
                )
            }
            item {
                Text(
                    stringResource(R.string.lsposed_runtime_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    stringResource(R.string.lsposed_runtime_notice),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Button(
                    enabled = !checkingRuntime,
                    onClick = {
                        checkingRuntime = true
                        runtimeSnapshot = null
                        runtimeError = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { LsposedRuntime.inspect() }
                            }
                            result.onSuccess { runtimeSnapshot = it }
                                .onFailure { runtimeError = it.javaClass.simpleName }
                            checkingRuntime = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.lsposed_runtime_inspect))
                }
            }
            if (checkingRuntime) {
                item { Text(stringResource(R.string.lsposed_runtime_running)) }
            }
            if (runtimeError != null) {
                item {
                    Text(stringResource(R.string.lsposed_runtime_provider_failed, runtimeError.orEmpty()))
                }
            }
            if (runtimeSnapshot == null && !checkingRuntime && runtimeError == null) {
                item { Text(stringResource(R.string.lsposed_runtime_unverified)) }
            }
            runtimeSnapshot?.let { runtime ->
                item {
                    val yes = stringResource(R.string.lsposed_runtime_yes)
                    val no = stringResource(R.string.lsposed_runtime_no)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.lsposed_runtime_installed,
                            if (runtime.vectorInstalled) yes else no))
                        Text(stringResource(R.string.lsposed_runtime_boot_enabled,
                            if (runtime.vectorEnabledAtNextBoot) yes else no))
                        Text(stringResource(R.string.lsposed_runtime_daemon,
                            if (runtime.daemonResponded) yes else no))
                        runtime.frameworkVersion?.let {
                            Text(stringResource(R.string.lsposed_runtime_version, it))
                        }
                        runtime.versionCode?.let {
                            Text(stringResource(R.string.lsposed_runtime_version_code, it.toString()))
                        }
                        runtime.apiVersion?.let {
                            Text(stringResource(R.string.lsposed_runtime_api, it.toString()))
                        }
                        runtime.reportedEnabledCount?.let {
                            Text(stringResource(R.string.lsposed_runtime_enabled_count, it.toString()))
                        }
                        Text(stringResource(R.string.lsposed_runtime_details, runtime.detail),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(runtime.modules.orEmpty(), key = { "configured:${it.packageName}" }) { module ->
                    DiscoveryCard(
                        title = module.packageName,
                        detail = if (module.enabled) {
                            stringResource(R.string.lsposed_runtime_enabled)
                        } else {
                            stringResource(R.string.lsposed_runtime_disabled)
                        },
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.lsposed_runtime_unknown),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DiscoveryCard(title: String, detail: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
