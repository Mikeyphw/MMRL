package com.dergoogler.mmrl.ui.screens.lsposed

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.lsposed.LsposedInstalledModule
import com.dergoogler.mmrl.lsposed.LsposedModulePolicy
import com.dergoogler.mmrl.lsposed.LsposedRepoModule
import com.dergoogler.mmrl.lsposed.LsposedRepository
import com.dergoogler.mmrl.viewmodel.LsposedViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LsposedRepositoryTab(
    innerPadding: PaddingValues,
    contentTopPadding: Dp? = null,
    viewModel: LsposedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LsposedEvents(viewModel)
    val query = state.query
    val modules = state.modules.filter { LsposedModulePolicy.matchesQuery(it, query) }
    val installedPackages = state.installed.map { it.packageName }.toSet()

    LsposedTabContent(
        innerPadding = innerPadding,
        title = stringResource(R.string.lsposed_repository_title),
        description = stringResource(R.string.lsposed_repository_description),
        query = query,
        onQueryChange = viewModel::search,
        loading = state.loading,
        error = state.error,
        onRefresh = { viewModel.refresh(force = true) },
        contentTopPadding = contentTopPadding,
    ) {
        item {
            GuidanceCard(
                title = stringResource(R.string.lsposed_install_guidance_title),
                description = stringResource(R.string.lsposed_install_guidance_description),
                action = stringResource(R.string.lsposed_open_manager),
                onAction = viewModel::openLsposed,
                actionEnabled = state.managerAvailable,
            )
        }
        items(modules, key = { it.packageName }) { module ->
            LsposedRepoModuleCard(
                module = module,
                installed = module.packageName in installedPackages,
                installing = state.installingPackage == module.packageName,
                onInstall = { viewModel.install(module) },
            )
        }
        if (!state.loading && modules.isEmpty()) {
            item { EmptyLsposedCard(text = stringResource(R.string.lsposed_empty_repository)) }
        }
    }
}

@Composable
fun LsposedModulesTab(
    innerPadding: PaddingValues,
    contentTopPadding: Dp? = null,
    viewModel: LsposedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LsposedEvents(viewModel)
    val query = state.query.trim()
    val modules =
        state.installed.filter { installed ->
            query.isBlank() ||
                installed.displayName.contains(query, ignoreCase = true) ||
                installed.packageName.contains(query, ignoreCase = true) ||
                installed.description.contains(query, ignoreCase = true)
        }

    LsposedTabContent(
        innerPadding = innerPadding,
        title = stringResource(R.string.lsposed_installed_title),
        description = stringResource(R.string.lsposed_installed_description),
        query = state.query,
        onQueryChange = viewModel::search,
        loading = state.loading,
        error = state.error,
        onRefresh = { viewModel.refresh(force = true) },
        contentTopPadding = contentTopPadding,
    ) {
        item {
            GuidanceCard(
                title = stringResource(R.string.lsposed_activation_title),
                description = stringResource(R.string.lsposed_activation_description),
                action = stringResource(R.string.lsposed_open_manager),
                onAction = viewModel::openLsposed,
                actionEnabled = state.managerAvailable,
            )
        }
        items(modules, key = { it.packageName }) { module ->
            LsposedInstalledModuleCard(
                module = module,
                installing = state.installingPackage == module.packageName,
                onOpenApp = { viewModel.openApp(module.packageName) },
                onOpenLsposed = viewModel::openLsposed,
                onUpdate = { viewModel.update(module) },
            )
        }
        if (!state.loading && modules.isEmpty()) {
            item { EmptyLsposedCard(text = stringResource(R.string.lsposed_empty_installed)) }
        }
    }
}

@Composable
private fun LsposedTabContent(
    innerPadding: PaddingValues,
    title: String,
    description: String,
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    contentTopPadding: Dp?,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = (contentTopPadding ?: innerPadding.calculateTopPadding()) + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.refresh))
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.lsposed_search_hint)) },
                )
            }
        }
        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
        if (error != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun GuidanceCard(
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit,
    actionEnabled: Boolean,
) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onAction, enabled = actionEnabled) {
                Text(action)
            }
        }
    }
}

@Composable
private fun LsposedRepoModuleCard(
    module: LsposedRepoModule,
    installed: Boolean,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painter = painterResource(R.drawable.brand_android),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = module.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = module.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = module.displayDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(text = LsposedModulePolicy.latestVersionDisplay(module))
                if (installed) StatusChip(text = stringResource(R.string.module_installed))
                module.latestStableTime?.let { StatusChip(text = it.take(10)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onInstall, enabled = !installing) {
                    Text(if (installed) stringResource(R.string.lsposed_update_apk) else stringResource(R.string.lsposed_install_apk))
                }
                module.sourceUrl?.takeIf { it.isNotBlank() }?.let { source ->
                    OutlinedButton(onClick = { uriHandler.openUri(source) }) {
                        Text(stringResource(R.string.lsposed_source))
                    }
                }
                module.homepageUrl?.takeIf { it.isNotBlank() }?.let { website ->
                    OutlinedButton(onClick = { uriHandler.openUri(website) }) {
                        Text(stringResource(R.string.lsposed_website))
                    }
                }
            }
        }
    }
}

@Composable
private fun LsposedInstalledModuleCard(
    module: LsposedInstalledModule,
    installing: Boolean,
    onOpenApp: () -> Unit,
    onOpenLsposed: () -> Unit,
    onUpdate: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painter = painterResource(R.drawable.brand_android),
                    contentDescription = null,
                    tint = if (module.hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = module.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = module.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = module.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(text = stringResource(R.string.module_installed))
                module.installedVersionName?.let { StatusChip(text = it) }
                module.repoVersion?.let { StatusChip(text = stringResource(R.string.lsposed_repo_version, it.versionName)) }
                if (module.hasUpdate) StatusChip(text = stringResource(R.string.module_update_available))
                if (!module.sourceMatched) StatusChip(text = stringResource(R.string.lsposed_not_in_repo))
                if (module.detectedByXposedMetadata) StatusChip(text = stringResource(R.string.lsposed_xposed_metadata))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenApp, enabled = module.launchable) {
                    Text(stringResource(R.string.lsposed_open_app))
                }
                OutlinedButton(onClick = onOpenLsposed) {
                    Text(stringResource(R.string.lsposed_open_manager))
                }
                if (module.hasUpdate) {
                    Button(onClick = onUpdate, enabled = !installing) {
                        Text(stringResource(R.string.lsposed_update_apk))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        enabled = false,
    )
}

@Composable
private fun EmptyLsposedCard(text: String) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LsposedEvents(viewModel: LsposedViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LsposedViewModel.Event.InstallApk -> {
                    runCatching {
                        context.startActivity(LsposedRepository.packageInstallerIntent(event.uri))
                    }.onFailure { throwable ->
                        val message =
                            if (throwable is ActivityNotFoundException) {
                                context.getString(R.string.lsposed_no_apk_installer)
                            } else {
                                throwable.message ?: context.getString(R.string.unknown_error)
                            }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
                is LsposedViewModel.Event.OpenIntent -> context.startActivity(event.intent)
                is LsposedViewModel.Event.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }
}
