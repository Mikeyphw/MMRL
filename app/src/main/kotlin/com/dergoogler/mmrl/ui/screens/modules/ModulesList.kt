package com.dergoogler.mmrl.ui.screens.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshModuleFilter
import com.dergoogler.mmrl.ash.model.AshModuleProtection
import com.dergoogler.mmrl.ash.model.moduleProtections
import com.dergoogler.mmrl.ext.isPackageInstalled
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.local.InstalledModuleGroupKey
import com.dergoogler.mmrl.model.local.LocalModule as InstalledModule
import com.dergoogler.mmrl.model.local.groupInstalledModules
import com.dergoogler.mmrl.model.local.State
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
import com.dergoogler.mmrl.ui.providable.mainContentBottomPadding
import com.dergoogler.mmrl.ui.providable.LocalModule as LocalModuleProvider
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.utils.webUILauncher
import com.dergoogler.mmrl.viewmodel.ModuleUpdateInfo
import com.dergoogler.mmrl.viewmodel.ModulesViewModel
import dev.chrisbanes.haze.hazeSource

@Composable
fun ScaffoldScope.ModulesList(
    innerPadding: PaddingValues,
    list: List<InstalledModule>,
    allModules: List<InstalledModule>,
    updates: List<ModuleUpdateInfo>,
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
    val updateMap = remember(updates) { updates.associateBy { it.local.id } }
    val ashProtectionMap = remember(ashState) { ashState.moduleProtections() }
    val ashWritable = !ashState.readOnly && ashState.lifecycle.compatible
    val groups =
        remember(list, updateMap) {
            groupInstalledModules(
                modules = list,
                updateIds = updateMap.keys,
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "device_status") {
                DeviceStatusHeader(
                    platform = viewModel.platform,
                    modules = allModules,
                    updateCount = updates.size,
                    providerAlive = isProviderAlive,
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
                    viewModel = viewModel,
                    isProviderAlive = isProviderAlive,
                    onDownload = onDownload,
                    ashProtectionMap = ashProtectionMap,
                    ashWritable = ashWritable,
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
    updateMap: Map<com.dergoogler.mmrl.platform.model.ModId, ModuleUpdateInfo>,
    viewModel: ModulesViewModel,
    isProviderAlive: Boolean,
    onDownload: (InstalledModule, VersionItem, Boolean) -> Unit,
    ashProtectionMap: Map<String, AshModuleProtection>,
    ashWritable: Boolean,
) {
    items(
        items = modules,
        key = { it.id.id },
        contentType = { "compact_module_row" },
    ) { module ->
        CompositionLocalProvider(LocalModuleProvider provides module) {
            CompactInstalledModuleRow(
                update = updateMap[module.id],
                viewModel = viewModel,
                isProviderAlive = isProviderAlive,
                onDownload = onDownload,
                ashProtection = ashProtectionMap[ModuleIdentity.normalize(module.id.id)],
                ashWritable = ashWritable,
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
    providerAlive: Boolean,
) {
    val activeCount = modules.count { it.state == State.ENABLE || it.state == State.UPDATE }
    val rebootRequired = modules.any { it.state == State.UPDATE || it.state == State.REMOVE }
    val platformName =
        when (platform) {
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

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platformName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.modules_active_count, activeCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusDot(
                    text = stringResource(if (providerAlive) R.string.root_available else R.string.root_unavailable),
                    color = if (providerAlive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusDot(
                    text = stringResource(R.string.modules_updates_count, updateCount),
                    color = if (updateCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rebootRequired) {
                    StatusDot(
                        text = stringResource(R.string.modules_reboot_required),
                        color = MaterialTheme.colorScheme.error,
                    )
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
            AshModuleFilter.Protected to protections.count { it.trust == "protected" },
            AshModuleFilter.Trusted to protections.count { it.trust == "trusted" },
            AshModuleFilter.Normal to protections.count { it.trust == "normal" && !it.quarantined },
            AshModuleFilter.Suspect to protections.count { it.trust == "suspect" },
            AshModuleFilter.Quarantined to protections.count { it.quarantined },
        )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "AshReXcue protection",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AshModuleFilter.entries.forEach { filter ->
                val count = if (filter == AshModuleFilter.All) protections.size else counts[filter] ?: 0
                androidx.compose.material3.FilterChip(
                    selected = selected == filter,
                    onClick = { onSelected(filter) },
                    label = { Text("${filter.name} ($count)") },
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
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 5.dp),
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
private fun CompactInstalledModuleRow(
    update: ModuleUpdateInfo?,
    viewModel: ModulesViewModel,
    isProviderAlive: Boolean,
    onDownload: (InstalledModule, VersionItem, Boolean) -> Unit,
    ashProtection: AshModuleProtection?,
    ashWritable: Boolean,
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

    val canOpenWebUi =
        isProviderAlive &&
            (module.hasWebUI || module.hasModConf) &&
            module.state != State.REMOVE
    val launchWebUi = preferences.webUILauncher(context, module)
    val webUiXMissing = !context.isPackageInstalled(preferences.webuixPackageName)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = canOpenWebUi) {
                    if (webUiXMissing) {
                        requiredAppBottomSheet = true
                    } else {
                        launchWebUi()
                    }
                },
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .alpha(if (module.state == State.DISABLE || module.state == State.REMOVE) 0.72f else 1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            text = module.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
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
                        text = stringResource(R.string.module_version_author, module.versionDisplay, module.author),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    FlowRow(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (update != null) {
                            StatusDot(
                                text = stringResource(R.string.module_update_available),
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (module.hasWebUI || module.hasModConf) {
                            StatusDot(
                                text = stringResource(R.string.view_module_features_webui),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (module.hasAction) {
                            StatusDot(
                                text = stringResource(R.string.module_action_available),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        ashProtection?.let { protection ->
                            StatusDot(
                                text = protection.label,
                                color =
                                    when {
                                        protection.quarantined -> MaterialTheme.colorScheme.error
                                        protection.trust == "suspect" -> MaterialTheme.colorScheme.error
                                        protection.trust == "protected" -> MaterialTheme.colorScheme.primary
                                        protection.trust == "trusted" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                        when (module.state) {
                            State.UPDATE ->
                                StatusDot(
                                    text = stringResource(R.string.modules_needs_attention),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            State.REMOVE ->
                                StatusDot(
                                    text = stringResource(R.string.modules_pending_removal),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            else -> Unit
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))
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
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        if (canOpenWebUi) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.view_module_features_webui)) },
                                leadingIcon = { Icon(painterResource(R.drawable.sandbox), null) },
                                onClick = {
                                    menuOpen = false
                                    if (webUiXMissing) requiredAppBottomSheet = true else launchWebUi()
                                },
                            )
                        }
                        if (module.hasAction) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.module_action)) },
                                leadingIcon = { Icon(painterResource(R.drawable.player_play), null) },
                                enabled = actionEnabled,
                                onClick = {
                                    menuOpen = false
                                    ActionActivity.start(context = context, modId = module.id)
                                },
                            )
                        }
                        if (updateItem != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.module_update)) },
                                leadingIcon = { Icon(painterResource(R.drawable.device_mobile_down), null) },
                                onClick = {
                                    menuOpen = false
                                    updateSheetOpen = true
                                },
                            )
                        }
                        ashProtection?.let { protection ->
                            if (protection.quarantined) {
                                DropdownMenuItem(
                                    text = { Text("Test restore next boot") },
                                    leadingIcon = { Icon(painterResource(R.drawable.reload), null) },
                                    enabled = ashWritable,
                                    onClick = {
                                        menuOpen = false
                                        viewModel.testRestore(module)
                                    },
                                )
                            }
                            listOf(
                                "protected" to "Protect with AshReXcue",
                                "trusted" to "Mark trusted",
                                "normal" to "Mark normal",
                                "suspect" to "Mark suspect",
                            ).forEach { (trust, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(
                                                if (trust == "suspect") R.drawable.alert_triangle else R.drawable.shield_bolt,
                                            ),
                                            null,
                                        )
                                    },
                                    enabled = ashWritable && protection.trust != trust &&
                                        !(ModuleIdentity.matches(module.id.id, "AshLooper") && trust != "protected"),
                                    onClick = {
                                        menuOpen = false
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
                                menuOpen = false
                                ops.change()
                            },
                        )
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
private fun StatusDot(
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
