package com.dergoogler.mmrl.ui.screens.modules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshModuleFilter
import com.dergoogler.mmrl.ash.model.AshModuleProtection
import com.dergoogler.mmrl.ash.model.AshModuleRiskBand
import com.dergoogler.mmrl.ash.model.moduleProtections
import com.dergoogler.mmrl.ext.isPackageInstalled
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.local.InstalledModuleGroupKey
import com.dergoogler.mmrl.model.local.ModuleSnapshot
import com.dergoogler.mmrl.model.local.ModuleSnapshotPlanItem
import com.dergoogler.mmrl.model.local.ModuleSnapshotPlanStatus
import com.dergoogler.mmrl.model.local.ModuleVersionPolicy
import com.dergoogler.mmrl.model.local.State
import com.dergoogler.mmrl.model.local.groupInstalledModules
import com.dergoogler.mmrl.model.local.versionDisplay
import com.dergoogler.mmrl.model.online.Blacklist
import com.dergoogler.mmrl.model.online.VersionItem
import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.hasAction
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.hasModConf
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.hasWebUI
import com.dergoogler.mmrl.ui.activity.terminal.action.ActionActivity
import com.dergoogler.mmrl.ui.component.VersionItemBottomSheet
import com.dergoogler.mmrl.ui.component.scaffold.ScaffoldScope
import com.dergoogler.mmrl.ui.component.scrollbar.VerticalFastScrollbar
import com.dergoogler.mmrl.ui.providable.LocalHazeState
import com.dergoogler.mmrl.ui.providable.LocalMainScreenInnerPaddings
import com.dergoogler.mmrl.ui.providable.LocalModule as LocalModuleProvider
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.ui.providable.mainContentBottomPadding
import com.dergoogler.mmrl.utils.webUILauncher
import com.dergoogler.mmrl.viewmodel.ModuleUpdateInfo
import com.dergoogler.mmrl.viewmodel.ModulesViewModel
import dev.chrisbanes.haze.hazeSource
import java.text.DateFormat
import java.util.Date
import com.dergoogler.mmrl.model.local.LocalModule as InstalledModule

@Composable
fun ScaffoldScope.ModulesList(
    innerPadding: PaddingValues,
    list: List<InstalledModule>,
    allModules: List<InstalledModule>,
    updates: List<ModuleUpdateInfo>,
    lockedUpdates: Map<String, ModuleUpdateInfo>,
    versionPolicies: Map<String, ModuleVersionPolicy>,
    moduleSnapshots: List<ModuleSnapshot>,
    state: LazyListState,
    onDownload: (InstalledModule, VersionItem, Boolean) -> Unit,
    viewModel: ModulesViewModel,
    isProviderAlive: Boolean,
    ashState: AshManagerState,
    ashFilter: AshModuleFilter,
    onAshFilterSelected: (AshModuleFilter) -> Unit,
) = Box(
    modifier = Modifier.fillMaxSize(),
) {
    val paddingValues = LocalMainScreenInnerPaddings.current
    val layoutDirection = LocalLayoutDirection.current
    val updateMap = remember(updates) { updates.associateBy { ModuleIdentity.normalize(it.local.id.id) } }
    val ashProtectionMap = remember(ashState) { ashState.moduleProtections() }
    val ashWritable = !ashState.readOnly && ashState.lifecycle.compatible
    var snapshotDialogOpen by remember { mutableStateOf(false) }
    var selectedSnapshot by remember { mutableStateOf<ModuleSnapshot?>(null) }

    val groups =
        remember(list, updateMap) {
            groupInstalledModules(
                modules = list,
                updateIds = updateMap.keys.map { com.dergoogler.mmrl.platform.model.ModId(it) }.toSet(),
            )
        }

    if (snapshotDialogOpen) {
        SnapshotsDialog(
            snapshots = moduleSnapshots,
            selected = selectedSnapshot ?: moduleSnapshots.firstOrNull(),
            onSelect = { selectedSnapshot = it },
            onDismiss = { snapshotDialogOpen = false },
            onSave = viewModel::saveCurrentSnapshot,
            onDelete = viewModel::deleteSnapshot,
            planProvider = viewModel::snapshotPlan,
        )
    }

    this@ModulesList.ResponsiveContent {
        LazyColumn(
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeSource(state = LocalHazeState.current),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 10.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    bottom = paddingValues.mainContentBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "device_status") {
                DeviceStatusHeader(
                    platform = viewModel.platform,
                    modules = allModules,
                    updateCount = updates.size,
                    lockedCount = versionPolicies.size,
                    snapshotCount = moduleSnapshots.size,
                    providerAlive = isProviderAlive,
                    onSaveSnapshot = { viewModel.saveCurrentSnapshot() },
                    onOpenSnapshots = { snapshotDialogOpen = true },
                )
            }

            if (ashState.snapshot != null) {
                item(key = "ash_filters") {
                    AshProtectionFilters(
                        selected = ashFilter,
                        protections = ashProtectionMap.values,
                        onSelected = onAshFilterSelected,
                    )
                }
            }

            groups.forEach { group ->
                item(key = "group_${group.key}") {
                    ModuleGroupHeader(
                        title = stringResource(group.key.titleResource),
                        count = group.modules.size,
                    )
                }
                moduleRows(
                    modules = group.modules,
                    updateMap = updateMap,
                    lockedUpdates = lockedUpdates,
                    versionPolicies = versionPolicies,
                    viewModel = viewModel,
                    isProviderAlive = isProviderAlive,
                    onDownload = onDownload,
                    ashProtectionMap = ashProtectionMap,
                    ashWritable = ashWritable,
                    onOpenSnapshots = { snapshotDialogOpen = true },
                )
            }
        }
    }

    VerticalFastScrollbar(
        state = state,
        modifier =
            Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = paddingValues.mainContentBottomPadding(),
                ),
    )
}

private fun LazyListScope.moduleRows(
    modules: List<InstalledModule>,
    updateMap: Map<String, ModuleUpdateInfo>,
    lockedUpdates: Map<String, ModuleUpdateInfo>,
    versionPolicies: Map<String, ModuleVersionPolicy>,
    viewModel: ModulesViewModel,
    isProviderAlive: Boolean,
    onDownload: (InstalledModule, VersionItem, Boolean) -> Unit,
    ashProtectionMap: Map<String, AshModuleProtection>,
    ashWritable: Boolean,
    onOpenSnapshots: () -> Unit,
) {
    items(
        items = modules,
        key = { it.id.id },
        contentType = { "installed_module_card" },
    ) { module ->
        val normalizedId = ModuleIdentity.normalize(module.id.id)
        CompositionLocalProvider(LocalModuleProvider provides module) {
            InstalledModuleCard(
                update = updateMap[normalizedId],
                lockedUpdate = lockedUpdates[normalizedId],
                policy = versionPolicies[normalizedId],
                viewModel = viewModel,
                isProviderAlive = isProviderAlive,
                onDownload = onDownload,
                ashProtection = ashProtectionMap[normalizedId],
                ashWritable = ashWritable,
                onOpenSnapshots = onOpenSnapshots,
            )
        }
    }
}

private val InstalledModuleGroupKey.titleResource: Int
    get() =
        when (this) {
            InstalledModuleGroupKey.NEEDS_ATTENTION -> R.string.modules_group_attention
            InstalledModuleGroupKey.UPDATES_AVAILABLE -> R.string.modules_group_updates
            InstalledModuleGroupKey.ENABLED -> R.string.modules_group_enabled
            InstalledModuleGroupKey.DISABLED -> R.string.modules_group_disabled
            InstalledModuleGroupKey.PENDING_REMOVAL -> R.string.modules_group_pending_removal
        }

@Composable
private fun DeviceStatusHeader(
    platform: Platform,
    modules: List<InstalledModule>,
    updateCount: Int,
    lockedCount: Int,
    snapshotCount: Int,
    providerAlive: Boolean,
    onSaveSnapshot: () -> Unit,
    onOpenSnapshots: () -> Unit,
) {
    val activeCount = modules.count { it.state == State.ENABLE || it.state == State.UPDATE }
    val rebootRequired = modules.any { it.state == State.UPDATE || it.state == State.REMOVE }
    val platformName = platform.displayLabel()

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platformName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.modules_installed_summary, activeCount, modules.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = stringResource(if (providerAlive) R.string.root_available else R.string.root_unavailable),
                    color = if (providerAlive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricPill(stringResource(R.string.modules_updates_count, updateCount))
                MetricPill(stringResource(R.string.module_policy_locked_count, lockedCount))
                MetricPill(stringResource(R.string.module_snapshot_count, snapshotCount))
                if (rebootRequired) {
                    StatusPill(
                        text = stringResource(R.string.modules_reboot_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onSaveSnapshot) {
                    Text(stringResource(R.string.module_snapshot_save_now))
                }
                OutlinedButton(onClick = onOpenSnapshots) {
                    Text(stringResource(R.string.module_snapshot_open))
                }
            }
        }
    }
}

@Composable
private fun AshProtectionFilters(
    selected: AshModuleFilter,
    protections: Collection<AshModuleProtection>,
    onSelected: (AshModuleFilter) -> Unit,
) {
    val counts =
        mapOf(
            AshModuleFilter.NeedsReview to protections.count(AshModuleProtection::needsReview),
            AshModuleFilter.Changed to protections.count(AshModuleProtection::changedSinceStable),
            AshModuleFilter.Protected to protections.count { it.trust == "protected" },
            AshModuleFilter.Trusted to protections.count { it.trust == "trusted" },
            AshModuleFilter.Normal to protections.count { it.trust == "normal" && !it.quarantined && !it.needsReview },
            AshModuleFilter.Suspect to protections.count { it.trust == "suspect" },
            AshModuleFilter.Quarantined to protections.count { it.quarantined },
        )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.ash_protection_filter_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier = Modifier.padding(top = 7.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AshModuleFilter.entries.forEach { filter ->
                val count = if (filter == AshModuleFilter.All) protections.size else counts[filter] ?: 0
                FilterChip(
                    selected = selected == filter,
                    onClick = { onSelected(filter) },
                    modifier = Modifier.semantics {
                        role = Role.Tab
                        this.selected = selected == filter
                    },
                    label = { Text(stringResource(R.string.label_with_count, filter.displayLabel(), count)) },
                )
            }
        }
    }
}

@Composable
private fun ModuleGroupHeader(
    title: String,
    count: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
                .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InstalledModuleCard(
    update: ModuleUpdateInfo?,
    lockedUpdate: ModuleUpdateInfo?,
    policy: ModuleVersionPolicy?,
    viewModel: ModulesViewModel,
    isProviderAlive: Boolean,
    onDownload: (InstalledModule, VersionItem, Boolean) -> Unit,
    ashProtection: AshModuleProtection?,
    ashWritable: Boolean,
    onOpenSnapshots: () -> Unit,
) {
    val context = LocalContext.current
    val module = LocalModuleProvider.current
    val preferences = LocalUserPreferences.current
    val ops =
        remember(preferences.useShellForModuleStateChange, module.state) {
            viewModel.createModuleOps(preferences.useShellForModuleStateChange, module)
        }
    val blacklist = remember(module.id) { viewModel.getBlacklist(module.id.toString()) }
    val isBlacklisted by Blacklist.isBlacklisted(blacklist)
    val updateItem = update?.version
    val lockedUpdateItem = lockedUpdate?.version
    val progress = viewModel.getProgress(updateItem)

    val switchChecked = module.state == State.ENABLE
    val switchEnabled =
        remember(module.state, isProviderAlive, viewModel.platform) {
            val providerSupportsStateChange =
                with(viewModel.platform) {
                    when {
                        isKernelSuNext || isKernelSU || isAPatch -> isProviderAlive && module.state != State.UPDATE
                        else -> isProviderAlive
                    }
                }
            providerSupportsStateChange && module.state != State.REMOVE
        }
    val actionEnabled = isProviderAlive && module.state != State.REMOVE && module.state != State.DISABLE
    val removeEnabled =
        remember(preferences.useShellForModuleStateChange, module.state, isProviderAlive) {
            isProviderAlive &&
                (
                    !(viewModel.moduleCompatibility.canRestoreModules && preferences.useShellForModuleStateChange) ||
                        module.state != State.REMOVE
                )
        }

    var updateSheetOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var requiredAppBottomSheet by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }

    if (updateSheetOpen && updateItem != null) {
        VersionItemBottomSheet(
            isUpdate = true,
            item = updateItem,
            isProviderAlive = isProviderAlive,
            onDownload = { onDownload(module, updateItem, it) },
            onClose = { updateSheetOpen = false },
            isBlacklisted = isBlacklisted,
        )
    }

    if (requiredAppBottomSheet) {
        BottomSheetForWXP { requiredAppBottomSheet = false }
    }

    if (detailsOpen) {
        ModuleMetadataDialog(
            module = module,
            policy = policy,
            update = update,
            lockedUpdate = lockedUpdate,
            ashProtection = ashProtection,
            onDismiss = { detailsOpen = false },
        )
    }

    val canOpenWebUi =
        isProviderAlive &&
            (module.hasWebUI || module.hasModConf) &&
            module.state != State.REMOVE
    val launchWebUi = preferences.webUILauncher(context, module)
    val webUiXMissing = !context.isPackageInstalled(preferences.webuixPackageName)
    val alpha = if (module.state == State.DISABLE || module.state == State.REMOVE) 0.72f else 1f
    val moduleDescription = module.description.ifBlank { stringResource(R.string.module_description_unavailable) }

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = true) { detailsOpen = true }
                        .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 12.dp)
                        .alpha(alpha),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                text = module.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (module.state == State.REMOVE) TextDecoration.LineThrough else TextDecoration.None,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isBlacklisted) {
                                Icon(
                                    painter = painterResource(R.drawable.alert_triangle),
                                    contentDescription = stringResource(R.string.blacklisted),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.module_compact_metadata, module.versionDisplay, module.author, module.id.id),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val switchDescription =
                        stringResource(
                            if (switchChecked) R.string.module_enabled_switch else R.string.module_disabled_switch,
                            module.name,
                        )
                    Switch(
                        modifier = Modifier.semantics { contentDescription = switchDescription },
                        checked = switchChecked,
                        onCheckedChange = ops.toggle,
                        enabled = switchEnabled,
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.dots_vertical),
                                contentDescription = stringResource(R.string.module_manage),
                            )
                        }
                        ModuleActionsMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            module = module,
                            canOpenWebUi = canOpenWebUi,
                            actionEnabled = actionEnabled,
                            updateItem = updateItem,
                            ashProtection = ashProtection,
                            ashWritable = ashWritable,
                            removeEnabled = removeEnabled,
                            viewModel = viewModel,
                            opsChange = ops.change,
                            launchWebUi = {
                                if (webUiXMissing) requiredAppBottomSheet = true else launchWebUi()
                            },
                            openUpdateSheet = { updateSheetOpen = true },
                            openDetails = { detailsOpen = true },
                            onOpenSnapshots = onOpenSnapshots,
                        )
                    }
                }

                Text(
                    text = moduleDescription,
                    modifier = Modifier.padding(top = 9.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (policy != null) {
                        StatusPill(
                            text = policy.statusLabel(lockedUpdateItem?.versionDisplay),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (lockedUpdateItem != null) {
                        StatusPill(
                            text = stringResource(R.string.module_update_locked, lockedUpdateItem.versionDisplay),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (update != null) {
                        StatusPill(
                            text = stringResource(R.string.module_update_available),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (module.hasWebUI || module.hasModConf) {
                        StatusPill(
                            text = stringResource(R.string.view_module_features_webui),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (module.hasAction) {
                        StatusPill(
                            text = stringResource(R.string.module_action_available),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    ashProtection?.let { protection ->
                        StatusPill(
                            text = protection.displayLabel(),
                            color = protection.statusColor(),
                        )
                    }
                    when (module.state) {
                        State.UPDATE -> StatusPill(
                            text = stringResource(R.string.modules_needs_attention),
                            color = MaterialTheme.colorScheme.error,
                        )
                        State.REMOVE -> StatusPill(
                            text = stringResource(R.string.modules_pending_removal),
                            color = MaterialTheme.colorScheme.error,
                        )
                        State.DISABLE -> StatusPill(
                            text = stringResource(R.string.module_disabled),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Unit
                    }
                }
            }

            when {
                ops.isOpsRunning ->
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        strokeCap = StrokeCap.Round,
                    )
                progress != 0f ->
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        strokeCap = StrokeCap.Round,
                    )
            }
        }
    }
}

@Composable
private fun ModuleActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    module: InstalledModule,
    canOpenWebUi: Boolean,
    actionEnabled: Boolean,
    updateItem: VersionItem?,
    ashProtection: AshModuleProtection?,
    ashWritable: Boolean,
    removeEnabled: Boolean,
    viewModel: ModulesViewModel,
    opsChange: () -> Unit,
    launchWebUi: () -> Unit,
    openUpdateSheet: () -> Unit,
    openDetails: () -> Unit,
    onOpenSnapshots: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.module_view_metadata)) },
            leadingIcon = { Icon(painterResource(R.drawable.info_circle), null) },
            onClick = {
                onDismiss()
                openDetails()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.module_policy_lock_current)) },
            leadingIcon = { Icon(painterResource(R.drawable.shield_lock), null) },
            onClick = {
                onDismiss()
                viewModel.lockCurrentVersion(module)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.module_policy_cap_current)) },
            leadingIcon = { Icon(painterResource(R.drawable.shield_bolt), null) },
            onClick = {
                onDismiss()
                viewModel.capAtCurrentVersion(module)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.module_policy_ignore_updates)) },
            leadingIcon = { Icon(painterResource(R.drawable.shield_off), null) },
            onClick = {
                onDismiss()
                viewModel.ignoreUpdates(module)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.module_policy_follow_latest)) },
            leadingIcon = { Icon(painterResource(R.drawable.refresh), null) },
            onClick = {
                onDismiss()
                viewModel.followLatest(module)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.module_snapshot_save_now)) },
            leadingIcon = { Icon(painterResource(R.drawable.database_edit), null) },
            onClick = {
                onDismiss()
                viewModel.saveCurrentSnapshot()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.module_snapshot_open)) },
            leadingIcon = { Icon(painterResource(R.drawable.clock_24), null) },
            onClick = {
                onDismiss()
                onOpenSnapshots()
            },
        )
        if (canOpenWebUi) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.view_module_features_webui)) },
                leadingIcon = { Icon(painterResource(R.drawable.sandbox), null) },
                onClick = {
                    onDismiss()
                    launchWebUi()
                },
            )
        }
        if (module.hasAction) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.module_action)) },
                leadingIcon = { Icon(painterResource(R.drawable.player_play), null) },
                enabled = actionEnabled,
                onClick = {
                    onDismiss()
                    ActionActivity.start(context = context, modId = module.id)
                },
            )
        }
        if (updateItem != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.module_update)) },
                leadingIcon = { Icon(painterResource(R.drawable.device_mobile_down), null) },
                onClick = {
                    onDismiss()
                    openUpdateSheet()
                },
            )
        }
        ashProtection?.let { protection ->
            if (protection.quarantined) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ash_test_restore_next_boot)) },
                    leadingIcon = { Icon(painterResource(R.drawable.reload), null) },
                    enabled = ashWritable,
                    onClick = {
                        onDismiss()
                        viewModel.testRestore(module)
                    },
                )
            }
            listOf(
                "protected" to R.string.ash_protect_with_ashrexcue,
                "trusted" to R.string.ash_mark_trusted,
                "normal" to R.string.ash_mark_normal,
                "suspect" to R.string.ash_mark_suspect,
            ).forEach { (trust, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (trust == "suspect") R.drawable.alert_triangle else R.drawable.shield_bolt),
                            null,
                        )
                    },
                    enabled = ashWritable && protection.trust != trust &&
                        !(ModuleIdentity.matches(module.id.id, "AshLooper") && trust != "protected"),
                    onClick = {
                        onDismiss()
                        viewModel.setAshTrust(module, trust)
                    },
                )
            }
        }
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (module.state == State.REMOVE) R.string.module_restore else R.string.module_remove,
                    ),
                )
            },
            leadingIcon = {
                Icon(
                    painterResource(if (module.state == State.REMOVE) R.drawable.rotate else R.drawable.trash),
                    null,
                )
            },
            enabled = removeEnabled,
            onClick = {
                onDismiss()
                opsChange()
            },
        )
    }
}

@Composable
private fun ModuleMetadataDialog(
    module: InstalledModule,
    policy: ModuleVersionPolicy?,
    update: ModuleUpdateInfo?,
    lockedUpdate: ModuleUpdateInfo?,
    ashProtection: AshModuleProtection?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        title = { Text(module.name) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetadataRow(stringResource(R.string.module_id), module.id.id)
                MetadataRow(stringResource(R.string.module_version), module.versionDisplay)
                MetadataRow(stringResource(R.string.module_author), module.author)
                MetadataRow(stringResource(R.string.module_description), module.description.ifBlank { stringResource(R.string.module_description_unavailable) })
                MetadataRow(stringResource(R.string.module_state), module.state.name)
                MetadataRow(stringResource(R.string.module_size), module.size.toString())
                policy?.let { MetadataRow(stringResource(R.string.module_policy), it.statusLabel(lockedUpdate?.version?.versionDisplay)) }
                update?.let { MetadataRow(stringResource(R.string.module_update), it.version.versionDisplay) }
                lockedUpdate?.let { MetadataRow(stringResource(R.string.module_update_locked_title), it.version.versionDisplay) }
                ashProtection?.let { MetadataRow(stringResource(R.string.ashrexcue), it.displayLabel()) }
            }
        },
    )
}

@Composable
private fun SnapshotsDialog(
    snapshots: List<ModuleSnapshot>,
    selected: ModuleSnapshot?,
    onSelect: (ModuleSnapshot) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (ModuleSnapshot) -> Unit,
    planProvider: (ModuleSnapshot) -> List<ModuleSnapshotPlanItem>,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        dismissButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.module_snapshot_save_now)) }
        },
        title = { Text(stringResource(R.string.module_snapshot_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.module_snapshot_review_first_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (snapshots.isEmpty()) {
                    Text(stringResource(R.string.module_snapshot_none))
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        snapshots.forEach { snapshot ->
                            AssistChip(
                                onClick = { onSelect(snapshot) },
                                label = { Text(snapshot.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.widthIn(max = 220.dp).semantics { this.selected = selected == snapshot },
                            )
                        }
                    }
                    selected?.let { snapshot ->
                        SnapshotSummary(snapshot = snapshot, date = dateFormat.format(Date(snapshot.createdAt)))
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.module_snapshot_restore_plan),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val plan = planProvider(snapshot)
                        plan.take(12).forEach { item -> SnapshotPlanRow(item) }
                        if (plan.size > 12) {
                            Text(
                                text = stringResource(R.string.module_snapshot_more_items, plan.size - 12),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { onDelete(snapshot) }) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SnapshotSummary(
    snapshot: ModuleSnapshot,
    date: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = snapshot.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                R.string.module_snapshot_summary,
                date,
                snapshot.modules.size,
                snapshot.enabledCount,
                snapshot.manager,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusPill(
            text = if (snapshot.metadataOnly) stringResource(R.string.module_snapshot_metadata_only) else stringResource(R.string.module_snapshot_full),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SnapshotPlanRow(item: ModuleSnapshotPlanItem) {
    val color = when (item.status) {
        ModuleSnapshotPlanStatus.CURRENT -> MaterialTheme.colorScheme.primary
        ModuleSnapshotPlanStatus.VERSION_CHANGED -> MaterialTheme.colorScheme.tertiary
        ModuleSnapshotPlanStatus.STATE_CHANGED -> MaterialTheme.colorScheme.tertiary
        ModuleSnapshotPlanStatus.MISSING -> MaterialTheme.colorScheme.error
        ModuleSnapshotPlanStatus.EXTRA -> MaterialTheme.colorScheme.error
        ModuleSnapshotPlanStatus.REVIEW -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        color = color.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = item.status.displayLabel(), color = color)
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.summary,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MetricPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Platform.displayLabel(): String = when (this) {
    Platform.KsuNext -> "KernelSU Next"
    Platform.KernelSU -> "KernelSU"
    Platform.Magisk -> "Magisk"
    Platform.APatch -> "APatch"
    Platform.MKSU -> "MKSU"
    Platform.SukiSU -> "SukiSU Ultra"
    Platform.RKSU -> "RKSU"
    Platform.Shizuku -> "Shizuku"
    Platform.NonRoot -> stringResource(R.string.non_root)
    Platform.Unknown -> stringResource(R.string.unknown)
}

@Composable
private fun AshModuleFilter.displayLabel(): String = when (this) {
    AshModuleFilter.All -> stringResource(R.string.ash_filter_all)
    AshModuleFilter.NeedsReview -> stringResource(R.string.ash_filter_needs_review)
    AshModuleFilter.Changed -> stringResource(R.string.ash_filter_changed)
    AshModuleFilter.Protected -> stringResource(R.string.ash_filter_protected)
    AshModuleFilter.Trusted -> stringResource(R.string.ash_filter_trusted)
    AshModuleFilter.Normal -> stringResource(R.string.ash_filter_normal)
    AshModuleFilter.Suspect -> stringResource(R.string.ash_filter_suspect)
    AshModuleFilter.Quarantined -> stringResource(R.string.recovery_quarantined)
}

@Composable
private fun AshModuleProtection.displayLabel(): String = when {
    quarantined -> stringResource(R.string.recovery_quarantined)
    riskBand >= AshModuleRiskBand.High -> stringResource(R.string.ash_protection_risk_score, riskBand.displayLabel(), riskScore)
    changedSinceStable -> stringResource(R.string.ash_changed_since_stable)
    trust == "protected" -> stringResource(R.string.ash_filter_protected)
    trust == "trusted" -> stringResource(R.string.ash_filter_trusted)
    trust == "suspect" -> stringResource(R.string.ash_filter_suspect)
    trust == "normal" -> stringResource(R.string.ash_filter_normal)
    else -> trust.replaceFirstChar(Char::uppercaseChar)
}

@Composable
private fun AshModuleRiskBand.displayLabel(): String = when (this) {
    AshModuleRiskBand.Unknown -> stringResource(R.string.ash_risk_band_unknown)
    AshModuleRiskBand.Low -> stringResource(R.string.ash_risk_band_low)
    AshModuleRiskBand.Elevated -> stringResource(R.string.ash_risk_band_elevated)
    AshModuleRiskBand.High -> stringResource(R.string.ash_risk_band_high)
    AshModuleRiskBand.Critical -> stringResource(R.string.ash_risk_band_critical)
}

@Composable
private fun AshModuleProtection.statusColor(): Color = when {
    quarantined -> MaterialTheme.colorScheme.error
    riskBand >= AshModuleRiskBand.High -> MaterialTheme.colorScheme.error
    changedSinceStable -> MaterialTheme.colorScheme.tertiary
    trust == "suspect" -> MaterialTheme.colorScheme.error
    trust == "protected" -> MaterialTheme.colorScheme.primary
    trust == "trusted" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ModuleSnapshotPlanStatus.displayLabel(): String = when (this) {
    ModuleSnapshotPlanStatus.CURRENT -> stringResource(R.string.module_snapshot_status_current)
    ModuleSnapshotPlanStatus.VERSION_CHANGED -> stringResource(R.string.module_snapshot_status_version)
    ModuleSnapshotPlanStatus.STATE_CHANGED -> stringResource(R.string.module_snapshot_status_state)
    ModuleSnapshotPlanStatus.MISSING -> stringResource(R.string.module_snapshot_status_missing)
    ModuleSnapshotPlanStatus.EXTRA -> stringResource(R.string.module_snapshot_status_extra)
    ModuleSnapshotPlanStatus.REVIEW -> stringResource(R.string.module_snapshot_status_review)
}
