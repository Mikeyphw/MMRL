package com.dergoogler.mmrl.ui.screens.lsposed

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.dergoogler.mmrl.lsposed.LsposedIdentity
import com.dergoogler.mmrl.lsposed.LsposedInstalledModule
import com.dergoogler.mmrl.lsposed.LsposedModulePolicy
import com.dergoogler.mmrl.lsposed.LsposedRepoModule
import com.dergoogler.mmrl.lsposed.LsposedRepository
import com.dergoogler.mmrl.lsposed.LsposedScopeState
import com.dergoogler.mmrl.lsposed.LsposedScopeTarget
import com.dergoogler.mmrl.lsposed.LsposedSafetyClassifier
import com.dergoogler.mmrl.lsposed.LsposedSafetyLevel
import com.dergoogler.mmrl.lsposed.LsposedSafetyNotice
import com.dergoogler.mmrl.lsposed.LsposedSnapshot
import com.dergoogler.mmrl.lsposed.LsposedSnapshotPlanItem
import com.dergoogler.mmrl.lsposed.LsposedUiContract
import com.dergoogler.mmrl.lsposed.LsposedVersionPolicy
import com.dergoogler.mmrl.ui.activity.terminal.action.ActionActivity
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
    var pendingInstall by remember { mutableStateOf<LsposedRepoModule?>(null) }

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
        detailRail = {
            LsposedRepositorySideRail(
                visibleCount = modules.size,
                installedCount = modules.count { it.packageName in installedPackages },
                sourceCount = modules.count { !it.sourceUrl.isNullOrBlank() },
                managerAvailable = state.managerAvailable,
                onOpenLsposed = viewModel::openLsposed,
            )
        },
    ) { railActive ->
        if (!railActive) {
            item {
                GuidanceCard(
                    title = stringResource(R.string.lsposed_install_guidance_title),
                    description = stringResource(R.string.lsposed_install_guidance_description),
                    action = stringResource(R.string.lsposed_open_manager),
                    onAction = viewModel::openLsposed,
                    actionEnabled = state.managerAvailable,
                )
            }
        }
        items(modules, key = { it.packageName }) { module ->
            LsposedRepoModuleCard(
                module = module,
                installed = module.packageName in installedPackages,
                installing = state.installingPackage == module.packageName,
                onInstall = { pendingInstall = module },
            )
        }
        if (!state.loading && modules.isEmpty()) {
            item { EmptyLsposedCard(text = stringResource(R.string.lsposed_empty_repository)) }
        }
    }
    pendingInstall?.let { module ->
        LsposedApkReviewDialog(
            module = module,
            installed = module.packageName in installedPackages,
            notices = LsposedSafetyClassifier.repositoryNotices(module),
            onDismiss = { pendingInstall = null },
            onConfirm = {
                pendingInstall = null
                viewModel.install(module)
            },
        )
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
    var pendingUpdate by remember { mutableStateOf<LsposedInstalledModule?>(null) }
    var scopeDetails by remember { mutableStateOf<LsposedInstalledModule?>(null) }
    var scopeEditor by remember { mutableStateOf<LsposedInstalledModule?>(null) }

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
        detailRail = {
            LsposedInstalledSideRail(
                installed = state.installed,
                policies = state.policies,
                managerAvailable = state.managerAvailable,
                scopeState = state.scopeState,
                snapshots = state.snapshots,
                plan = state.snapshotPlan,
                onOpenLsposed = viewModel::openLsposed,
                onSaveSnapshot = { viewModel.saveSnapshot() },
                onCompareSnapshot = viewModel::compareSnapshot,
                onDeleteSnapshot = viewModel::deleteSnapshot,
            )
        },
    ) { railActive ->
        items(modules, key = { it.packageName }) { module ->
            val policy = state.policies[LsposedIdentity.normalize(module.packageName)]
            LsposedInstalledModuleCard(
                module = module,
                policy = policy,
                managerAvailable = state.managerAvailable,
                installing = state.installingPackage == module.packageName,
                onOpenApp = { viewModel.openApp(module.packageName) },
                onOpenLsposed = viewModel::openLsposed,
                onUpdate = { pendingUpdate = module },
                onViewScope = { scopeDetails = module },
                onEditScope = { scopeEditor = module },
                onFollowLatest = { viewModel.followLatest(module.packageName) },
                onIgnoreUpdates = { viewModel.ignoreUpdates(module.packageName) },
                onPinCurrent = { viewModel.pinCurrent(module) },
                onMaxCurrent = { viewModel.maxCurrent(module) },
            )
        }
        if (!state.loading && modules.isEmpty()) {
            item { EmptyLsposedCard(text = stringResource(R.string.lsposed_empty_installed)) }
        }
        if (!railActive) {
            item {
                GuidanceCard(
                    title = stringResource(R.string.lsposed_activation_title),
                    description = stringResource(R.string.lsposed_activation_description),
                    action = stringResource(R.string.lsposed_open_manager),
                    onAction = viewModel::openLsposed,
                    actionEnabled = state.managerAvailable,
                )
            }
            item {
                LsposedSnapshotCard(
                    installedCount = state.installed.size,
                    snapshots = state.snapshots,
                    plan = state.snapshotPlan,
                    onSaveSnapshot = { viewModel.saveSnapshot() },
                    onCompareSnapshot = viewModel::compareSnapshot,
                    onDeleteSnapshot = viewModel::deleteSnapshot,
                )
            }
        }
    }
    scopeDetails?.let { module ->
        LsposedScopeDetailsDialog(
            module = module,
            scopeState = state.scopeState,
            onDismiss = { scopeDetails = null },
        )
    }

    scopeEditor?.let { module ->
        LsposedScopeEditorDialog(
            module = module,
            availableTargets = state.scopeTargets,
            applying = state.applyingScopePackage == module.packageName,
            onDismiss = { scopeEditor = null },
            onApply = { enabled, autoInclude, targets ->
                scopeEditor = null
                viewModel.applyScope(module, enabled, autoInclude, targets)
            },
        )
    }

    pendingUpdate?.let { installed ->
        val module = installed.repoModule
        if (module == null) {
            pendingUpdate = null
        } else {
            LsposedApkReviewDialog(
                module = module,
                installed = true,
                notices = LsposedSafetyClassifier.installedNotices(
                    module = installed,
                    managerAvailable = state.managerAvailable,
                    updateBlocked = false,
                ),
                onDismiss = { pendingUpdate = null },
                onConfirm = {
                    pendingUpdate = null
                    viewModel.update(installed)
                },
            )
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
    detailRail: (@Composable () -> Unit)? = null,
    content: LazyListScope.(Boolean) -> Unit,
) {
    val topPadding = (contentTopPadding ?: innerPadding.calculateTopPadding()) + 12.dp
    val bottomPadding = innerPadding.calculateBottomPadding() + 24.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val railActive = detailRail != null && LsposedUiContract.useListDetail(maxWidth.value.toInt())

        if (railActive) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = topPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 0.dp,
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    lsposedHeaderItems(
                        title = title,
                        description = description,
                        query = query,
                        onQueryChange = onQueryChange,
                        loading = loading,
                        error = error,
                        onRefresh = onRefresh,
                    )
                    content(true)
                }
                Column(
                    modifier = Modifier
                        .weight(0.72f)
                        .widthIn(min = LsposedUiContract.detailRailMinWidthDp.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(end = 16.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    detailRail()
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                lsposedHeaderItems(
                    title = title,
                    description = description,
                    query = query,
                    onQueryChange = onQueryChange,
                    loading = loading,
                    error = error,
                    onRefresh = onRefresh,
                )
                content(false)
            }
        }
    }
}

private fun LazyListScope.lsposedHeaderItems(
    title: String,
    description: String,
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
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
                maxLines = LsposedUiContract.phoneDescriptionMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(text = LsposedModulePolicy.latestVersionDisplay(module))
                if (installed) StatusChip(text = stringResource(R.string.module_installed))
                module.latestStableTime?.let { StatusChip(text = it.take(10)) }
                LsposedSafetyClassifier.highestLevel(LsposedSafetyClassifier.repositoryNotices(module))?.let { level ->
                    StatusChip(text = safetyLevelLabel(level))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    policy: LsposedVersionPolicy?,
    managerAvailable: Boolean,
    installing: Boolean,
    onOpenApp: () -> Unit,
    onOpenLsposed: () -> Unit,
    onUpdate: () -> Unit,
    onViewScope: () -> Unit,
    onEditScope: () -> Unit,
    onFollowLatest: () -> Unit,
    onIgnoreUpdates: () -> Unit,
    onPinCurrent: () -> Unit,
    onMaxCurrent: () -> Unit,
) {
    val updateBlocked = module.hasUpdate && policy?.blocks(module.repoVersion?.versionCode) == true
    val notices = LsposedSafetyClassifier.installedNotices(module, managerAvailable, updateBlocked)
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
                maxLines = LsposedUiContract.phoneDescriptionMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(text = stringResource(R.string.module_installed))
                module.installedVersionName?.let { StatusChip(text = it) }
                module.repoVersion?.let { StatusChip(text = stringResource(R.string.lsposed_repo_version, it.versionName)) }
                module.scope?.let { scope ->
                    StatusChip(text = stringResource(if (scope.enabled) R.string.lsposed_scope_enabled else R.string.lsposed_scope_disabled))
                    StatusChip(text = stringResource(R.string.lsposed_scope_count, scope.scopeCount))
                    if (scope.autoInclude) StatusChip(text = stringResource(R.string.lsposed_scope_auto_include))
                } ?: StatusChip(text = stringResource(R.string.lsposed_scope_unknown))
                if (module.hasUpdate) StatusChip(text = stringResource(R.string.module_update_available))
                if (updateBlocked) StatusChip(text = stringResource(R.string.lsposed_update_blocked))
                policy?.takeIf { it.isLocked }?.let { StatusChip(text = it.statusLabel(module.repoVersion?.versionName)) }
                if (!module.sourceMatched) StatusChip(text = stringResource(R.string.lsposed_not_in_repo))
                if (module.detectedByXposedMetadata) StatusChip(text = stringResource(R.string.lsposed_xposed_metadata))
                LsposedSafetyClassifier.highestLevel(notices)?.let { level ->
                    StatusChip(text = safetyLevelLabel(level))
                }
            }
            SafetyNoticeSummary(notices = notices)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onOpenApp, enabled = module.launchable) {
                    Text(stringResource(R.string.lsposed_open_app))
                }
                OutlinedButton(onClick = onOpenLsposed) {
                    Text(stringResource(R.string.lsposed_open_manager))
                }
                OutlinedButton(onClick = onViewScope, enabled = module.scope != null) {
                    Text(stringResource(R.string.lsposed_view_scope))
                }
                OutlinedButton(onClick = onEditScope, enabled = module.scope != null) {
                    Text(stringResource(R.string.lsposed_edit_scope))
                }
                if (module.hasUpdate) {
                    Button(onClick = onUpdate, enabled = !installing && !updateBlocked) {
                        Text(if (updateBlocked) stringResource(R.string.lsposed_update_locked) else stringResource(R.string.lsposed_update_apk))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (policy?.isLocked == true) {
                    TextButton(onClick = onFollowLatest) { Text(stringResource(R.string.lsposed_follow_latest)) }
                } else {
                    TextButton(onClick = onPinCurrent) { Text(stringResource(R.string.lsposed_lock_current)) }
                    TextButton(onClick = onMaxCurrent) { Text(stringResource(R.string.lsposed_allow_up_to_current)) }
                    TextButton(onClick = onIgnoreUpdates) { Text(stringResource(R.string.lsposed_ignore_updates)) }
                }
            }
        }
    }
}

@Composable
private fun LsposedScopeDetailsDialog(
    module: LsposedInstalledModule,
    scopeState: LsposedScopeState,
    onDismiss: () -> Unit,
) {
    val scope = module.scope
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lsposed_scope_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = module.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = module.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (scope == null) {
                    Text(
                        text = scopeState.message ?: stringResource(R.string.lsposed_scope_unknown_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(text = stringResource(if (scope.enabled) R.string.lsposed_scope_enabled else R.string.lsposed_scope_disabled))
                        StatusChip(text = stringResource(R.string.lsposed_scope_count, scope.scopeCount))
                        if (scope.autoInclude) StatusChip(text = stringResource(R.string.lsposed_scope_auto_include))
                    }
                    Text(text = stringResource(R.string.lsposed_scope_db_path, scopeState.dbPath), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (scope.targets.isEmpty()) {
                        Text(text = stringResource(R.string.lsposed_scope_empty), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            scope.targets.take(12).forEach { target ->
                                Text(
                                    text = stringResource(R.string.lsposed_scope_target, target.label, target.packageName, target.userId),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (scope.targets.size > 12) {
                                Text(
                                    text = stringResource(R.string.lsposed_scope_more_targets, scope.targets.size - 12),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
    )
}

@Composable
private fun LsposedScopeEditorDialog(
    module: LsposedInstalledModule,
    availableTargets: List<LsposedScopeTarget>,
    applying: Boolean,
    onDismiss: () -> Unit,
    onApply: (Boolean, Boolean, List<LsposedScopeTarget>) -> Unit,
) {
    val scope = module.scope ?: return
    var enabled by remember(module.packageName) { mutableStateOf(scope.enabled) }
    var autoInclude by remember(module.packageName) { mutableStateOf(scope.autoInclude) }
    var selected by remember(module.packageName) {
        mutableStateOf(scope.targets.map { "${it.userId}:${it.packageName}" }.toSet())
    }
    val targets = remember(availableTargets, scope) {
        (availableTargets + scope.targets)
            .distinctBy { "${it.userId}:${it.packageName}" }
            .sortedWith(compareBy<LsposedScopeTarget> { it.label.lowercase() }.thenBy { it.packageName })
    }
    val selectedTargets = targets.filter { "${it.userId}:${it.packageName}" in selected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lsposed_edit_scope_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = module.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = stringResource(R.string.lsposed_scope_review_backup_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text(text = stringResource(R.string.lsposed_scope_enable_module), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = autoInclude, onCheckedChange = { autoInclude = it })
                    Text(text = stringResource(R.string.lsposed_scope_auto_include), style = MaterialTheme.typography.bodySmall)
                }
                Text(text = stringResource(R.string.lsposed_scope_selected_count, selectedTargets.size), style = MaterialTheme.typography.labelLarge)
                targets.take(60).forEach { target ->
                    val key = "${target.userId}:${target.packageName}"
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = key in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + key else selected - key
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = target.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = "${target.packageName} · user ${target.userId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (targets.size > 60) {
                    Text(text = stringResource(R.string.lsposed_scope_editor_more_targets, targets.size - 60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(enabled, autoInclude, selectedTargets) },
                enabled = !applying,
            ) {
                Text(stringResource(R.string.lsposed_apply_scope_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SafetyNoticeSummary(notices: List<LsposedSafetyNotice>) {
    val primary = notices.firstOrNull { it.level != LsposedSafetyLevel.INFO } ?: notices.firstOrNull() ?: return
    Surface(
        color = when (primary.level) {
            LsposedSafetyLevel.ACTION -> MaterialTheme.colorScheme.errorContainer
            LsposedSafetyLevel.WARNING -> MaterialTheme.colorScheme.secondaryContainer
            LsposedSafetyLevel.INFO -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when (primary.level) {
            LsposedSafetyLevel.ACTION -> MaterialTheme.colorScheme.onErrorContainer
            LsposedSafetyLevel.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
            LsposedSafetyLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = primary.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(text = primary.body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LsposedApkReviewDialog(
    module: LsposedRepoModule,
    installed: Boolean,
    notices: List<LsposedSafetyNotice>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (installed) R.string.lsposed_review_update_title else R.string.lsposed_review_install_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = module.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = module.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = module.displayDescription, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(text = LsposedModulePolicy.latestVersionDisplay(module))
                    module.sourceUrl?.takeIf { it.isNotBlank() }?.let { StatusChip(text = stringResource(R.string.lsposed_source_available)) }
                    LsposedSafetyClassifier.highestLevel(notices)?.let { StatusChip(text = safetyLevelLabel(it)) }
                }
                SafetyNoticeSummary(notices = notices)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.lsposed_download_open_installer))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun safetyLevelLabel(level: LsposedSafetyLevel): String = when (level) {
    LsposedSafetyLevel.INFO -> stringResource(R.string.lsposed_scope_review_needed)
    LsposedSafetyLevel.WARNING -> stringResource(R.string.lsposed_review_warning)
    LsposedSafetyLevel.ACTION -> stringResource(R.string.lsposed_action_required)
}

@Composable
private fun LsposedSnapshotCard(
    installedCount: Int,
    snapshots: List<LsposedSnapshot>,
    plan: List<LsposedSnapshotPlanItem>,
    onSaveSnapshot: () -> Unit,
    onCompareSnapshot: (LsposedSnapshot) -> Unit,
    onDeleteSnapshot: (String) -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.lsposed_snapshot_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.lsposed_snapshot_description, installedCount, snapshots.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSaveSnapshot) {
                    Text(stringResource(R.string.lsposed_save_snapshot))
                }
                snapshots.firstOrNull()?.let { latest ->
                    TextButton(onClick = { onCompareSnapshot(latest) }) {
                        Text(stringResource(R.string.lsposed_compare_latest_snapshot))
                    }
                }
            }
            snapshots.firstOrNull()?.let { latest ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(text = latest.label)
                    StatusChip(text = stringResource(R.string.lsposed_snapshot_count, latest.installedCount))
                    TextButton(onClick = { onDeleteSnapshot(latest.id) }) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
            if (plan.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.take(4).forEach { item ->
                        Text(
                            text = "${item.title}: ${item.summary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (plan.size > 4) {
                        Text(
                            text = stringResource(R.string.lsposed_snapshot_more_items, plan.size - 4),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LsposedRepositorySideRail(
    visibleCount: Int,
    installedCount: Int,
    sourceCount: Int,
    managerAvailable: Boolean,
    onOpenLsposed: () -> Unit,
) {
    GuidanceCard(
        title = stringResource(R.string.lsposed_install_guidance_title),
        description = stringResource(R.string.lsposed_install_guidance_description),
        action = stringResource(R.string.lsposed_open_manager),
        onAction = onOpenLsposed,
        actionEnabled = managerAvailable,
    )
    LsposedMetricCard(
        title = stringResource(R.string.lsposed_adaptive_repository_summary_title),
        metrics = listOf(
            stringResource(R.string.lsposed_repo_total_modules, visibleCount),
            stringResource(R.string.lsposed_repo_installed_modules, installedCount),
            stringResource(R.string.lsposed_repo_source_links, sourceCount),
        ),
    )
}

@Composable
private fun LsposedInstalledSideRail(
    installed: List<LsposedInstalledModule>,
    policies: Map<String, LsposedVersionPolicy>,
    managerAvailable: Boolean,
    scopeState: LsposedScopeState,
    snapshots: List<LsposedSnapshot>,
    plan: List<LsposedSnapshotPlanItem>,
    onOpenLsposed: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onCompareSnapshot: (LsposedSnapshot) -> Unit,
    onDeleteSnapshot: (String) -> Unit,
) {
    LsposedProviderStatusCard(
        managerAvailable = managerAvailable,
        scopeState = scopeState,
        onOpenLsposed = onOpenLsposed,
    )
    LsposedMetricCard(
        title = stringResource(R.string.lsposed_adaptive_installed_summary_title),
        metrics = listOf(
            stringResource(R.string.lsposed_installed_total, installed.size),
            stringResource(R.string.lsposed_installed_updates, installed.count { it.hasUpdate }),
            stringResource(R.string.lsposed_installed_locked, policies.values.count { it.isLocked }),
            stringResource(R.string.lsposed_installed_unmatched, installed.count { !it.sourceMatched }),
        ),
    )
    LsposedSnapshotCard(
        installedCount = installed.size,
        snapshots = snapshots,
        plan = plan,
        onSaveSnapshot = onSaveSnapshot,
        onCompareSnapshot = onCompareSnapshot,
        onDeleteSnapshot = onDeleteSnapshot,
    )
}

@Composable
private fun LsposedProviderStatusCard(
    managerAvailable: Boolean,
    scopeState: LsposedScopeState,
    onOpenLsposed: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = stringResource(R.string.lsposed_provider_status_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(text = if (managerAvailable) stringResource(R.string.lsposed_provider_manager_available) else stringResource(R.string.lsposed_provider_manager_missing))
                StatusChip(text = if (scopeState.readable) stringResource(R.string.lsposed_scope_db_readable) else stringResource(R.string.lsposed_scope_db_unreadable))
                if (scopeState.readable) StatusChip(text = stringResource(R.string.lsposed_scope_modules_count, scopeState.moduleCount))
            }
            Text(
                text = scopeState.message ?: stringResource(R.string.lsposed_provider_status_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenLsposed, enabled = managerAvailable) {
                Text(stringResource(R.string.lsposed_open_manager))
            }
        }
    }
}

@Composable
private fun LsposedMetricCard(
    title: String,
    metrics: List<String>,
) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            metrics.forEach { metric ->
                Text(
                    text = metric,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                is LsposedViewModel.Event.RunProviderAction -> ActionActivity.start(context, event.moduleId)
                is LsposedViewModel.Event.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }
}
