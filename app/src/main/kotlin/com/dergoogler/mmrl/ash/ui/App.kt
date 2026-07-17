package com.dergoogler.mmrl.ash.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.ash.AshUiState
import com.dergoogler.mmrl.ash.AshViewModel
import com.dergoogler.mmrl.ash.ConnectionState
import com.dergoogler.mmrl.ash.model.ActivityItem
import com.dergoogler.mmrl.ash.model.Dashboard
import com.dergoogler.mmrl.ash.model.MainDestination
import com.dergoogler.mmrl.ash.model.ModuleItem
import com.dergoogler.mmrl.ash.model.QuarantineItem
import com.dergoogler.mmrl.ash.model.SettingItem
import com.dergoogler.mmrl.ash.model.ThemePreset
import com.dergoogler.mmrl.ui.activity.terminal.install.InstallActivity
import java.io.File
import java.util.Locale

@Composable
fun AshReXcueApp(viewModel: AshViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var theme by remember {
        mutableStateOf(
            runCatching { ThemePreset.valueOf(prefs.getString("theme", ThemePreset.MMRL.name) ?: ThemePreset.MMRL.name) }
                .getOrDefault(ThemePreset.MMRL),
        )
    }
    AshReXcueTheme(theme) {
        MainShell(
            state = state,
            viewModel = viewModel,
            theme = theme,
            onThemeChanged = {
                theme = it
                prefs.edit().putString("theme", it.name).apply()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    state: AshUiState,
    viewModel: AshViewModel,
    theme: ThemePreset,
    onThemeChanged: (ThemePreset) -> Unit,
) {
    val appContext = LocalContext.current
    var destination by remember { mutableStateOf(MainDestination.Status) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("AshReXcue", fontWeight = FontWeight.SemiBold)
                            Text(destination.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        TextButton(onClick = viewModel::refreshAll, enabled = !state.loading) { Text("Refresh") }
                    },
                )
            },
            bottomBar = {
                if (!expanded) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        MainDestination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Glyph(item.glyph, destination == item) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { innerPadding ->
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                if (expanded) {
                    NavigationRail(Modifier.fillMaxHeight()) {
                        Spacer(Modifier.height(8.dp))
                        MainDestination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Glyph(item.glyph, destination == item) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (val connection = state.connection) {
                        ConnectionState.Checking -> CenterMessage("Checking root and module…", loading = true)
                        ConnectionState.RootDenied -> CenterMessage("Root access is required. Grant AshReXcue root access in your root manager, then refresh.")
                        ConnectionState.ModuleMissing -> MissingModuleMessage(
                            onInstall = {
                                installBundledAshModule(
                                    context = appContext,
                                    onError = viewModel::showMessage,
                                )
                            },
                            onRefresh = viewModel::refreshAll,
                        )
                        is ConnectionState.Error -> CenterMessage(connection.message)
                        ConnectionState.Ready -> when (destination) {
                            MainDestination.Status -> StatusScreen(state.dashboard, state.loading, viewModel)
                            MainDestination.Modules -> ModulesScreen(state.modules, viewModel)
                            MainDestination.Recovery -> RecoveryScreen(state.dashboard, state.quarantine, viewModel)
                            MainDestination.Activity -> ActivityScreen(state.activity)
                            MainDestination.Settings -> SettingsScreen(state.settings, theme, onThemeChanged, viewModel)
                        }
                    }
                    if (state.loading && state.connection == ConnectionState.Ready) {
                        CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(16.dp).size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Glyph(value: String, selected: Boolean) {
    Box(
        modifier = Modifier.size(28.dp).background(
            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            RoundedCornerShape(9.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(value, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CenterMessage(message: String, loading: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (loading) CircularProgressIndicator()
        if (loading) Spacer(Modifier.height(20.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MissingModuleMessage(
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "AshReXcue module is not installed.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Install the bundled AshLooper module, reboot, then refresh this screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onInstall) { Text("Install module") }
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }
    }
}

private fun installBundledAshModule(
    context: Context,
    onError: (String) -> Unit,
) {
    runCatching {
        val directory = File(context.cacheDir, "ashrexcue")
        directory.mkdirs()
        val module = File(directory, ASH_MODULE_ZIP_ASSET)
        context.assets.open(ASH_MODULE_ZIP_ASSET).use { input ->
            module.outputStream().use { output -> input.copyTo(output) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", module)
        InstallActivity.start(context = context, uri = uri)
    }.onFailure { error ->
        onError(error.message ?: "Unable to prepare AshReXcue module ZIP")
    }
}

private const val ASH_MODULE_ZIP_ASSET = "AshReXcue_Bootloop_Protector.zip"

@Composable
private fun StatusScreen(dashboard: Dashboard, loading: Boolean, viewModel: AshViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeroCard(
                title = when (dashboard.bootState) {
                    "stable" -> "Protection is stable"
                    "monitoring" -> "Monitoring this boot"
                    "booting" -> "Boot transaction active"
                    else -> "Protection state: ${dashboard.bootState}"
                },
                subtitle = dashboard.bootReason.ifBlank { "AshReXcue ${dashboard.version} • ${dashboard.rootManager}" },
                badge = "${dashboard.loops}/${dashboard.threshold} strikes",
            )
        }
        item {
            MetricGrid(
                listOf(
                    "Timeout" to "${dashboard.timeout}s",
                    "Stability" to "${dashboard.stability}s",
                    "Quarantined" to dashboard.quarantined.toString(),
                    "Restore trial" to dashboard.restoreState,
                    "Enabled" to dashboard.enabledModules.toString(),
                    "Disabled" to dashboard.disabledModules.toString(),
                ),
            )
        }
        item {
            SectionCard("Rescue escalation") {
                KeyValue("Current stage", "${dashboard.rescueStage}: ${dashboard.rescueStageLabel}")
                KeyValue("Next intervention", dashboard.nextRescue)
                KeyValue("Trusted modules", dashboard.trustedModules.toString())
                KeyValue("Suspect modules", dashboard.suspectModules.toString())
                KeyValue("Protected modules", dashboard.protectedModules.toString())
            }
        }
        if (dashboard.latestRescueId.isNotBlank()) {
            item {
                SectionCard("Latest rescue") {
                    KeyValue("ID", dashboard.latestRescueId)
                    KeyValue("Result", dashboard.latestRescueStatus)
                    Text(dashboard.latestRescueReason.ifBlank { "No reason recorded" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::exportDiagnostics, enabled = !loading) { Text("Export diagnostics") }
                if (dashboard.restoreState == "testing") {
                    OutlinedButton(onClick = viewModel::rollbackTrial, enabled = !loading) { Text("Rollback trial") }
                }
            }
        }
    }
}

@Composable
private fun ModulesScreen(modules: List<ModuleItem>, viewModel: AshViewModel) {
    var query by remember { mutableStateOf("") }
    var stateFilter by remember { mutableStateOf("all") }
    var trustFilter by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<ModuleItem?>(null) }
    val filtered = remember(modules, query, stateFilter, trustFilter) {
        modules.filter { module ->
            val textMatch = query.isBlank() || listOf(module.name, module.id, module.folder).any { it.contains(query, ignoreCase = true) }
            val stateMatch = when (stateFilter) {
                "enabled" -> module.enabled
                "disabled" -> !module.enabled
                "quarantined" -> module.quarantined
                else -> true
            }
            val trustMatch = trustFilter == "all" || module.trust == trustFilter
            textMatch && stateMatch && trustMatch
        }.sortedWith(compareBy<ModuleItem> { it.trust }.thenBy { it.name.lowercase(Locale.ROOT) })
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search modules") }, singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all", "enabled", "disabled", "quarantined").forEach { value ->
                    FilterChip(selected = stateFilter == value, onClick = { stateFilter = value }, label = { Text(value.title()) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all", "protected", "trusted", "normal", "suspect", "quarantined").forEach { value ->
                    FilterChip(selected = trustFilter == value, onClick = { trustFilter = value }, label = { Text(value.title()) })
                }
            }
            Text("${filtered.size} of ${modules.size} modules", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(filtered, key = { it.folder }) { module -> ModuleRow(module) { selected = module } }
        }
    }
    selected?.let { module ->
        ModuleDialog(module, onDismiss = { selected = null }, onTrust = {
            viewModel.setTrust(module.folder, it)
            selected = null
        })
    }
}

@Composable
private fun ModuleRow(module: ModuleItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(if (module.quarantined) MaterialTheme.colorScheme.error else if (module.enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(module.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${module.id} • ${module.version.ifBlank { "version unknown" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AssistChip(onClick = onClick, label = { Text(module.trust.title()) })
    }
    HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
}

@Composable
private fun ModuleDialog(module: ModuleItem, onDismiss: () -> Unit, onTrust: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(module.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyValue("ID", module.id)
                KeyValue("Folder", module.folder)
                KeyValue("State", if (module.quarantined) "Quarantined" else if (module.enabled) "Enabled" else "Disabled")
                KeyValue("Trust", module.trust.title())
                Text("Set trust category", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("protected", "trusted", "normal", "suspect").forEach { trust ->
                        FilterChip(selected = module.trust == trust, onClick = { onTrust(trust) }, label = { Text(trust.title()) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun RecoveryScreen(dashboard: Dashboard, quarantine: List<QuarantineItem>, viewModel: AshViewModel) {
    var confirm by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("Restoration trial") {
                KeyValue("State", dashboard.restoreState.title())
                KeyValue("Modules in trial", dashboard.restoreCount.toString())
                if (dashboard.restoreState == "testing") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::completeTrial) { Text("Mark stable") }
                        OutlinedButton(onClick = { confirm = "rollback" }) { Text("Rollback") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirm = "half" }, enabled = quarantine.isNotEmpty()) { Text("Restore half") }
                OutlinedButton(onClick = { confirm = "all" }, enabled = quarantine.isNotEmpty()) { Text("Restore all") }
            }
        }
        item { Text("Quarantined modules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (quarantine.isEmpty()) item { EmptyCard("No modules are currently quarantined.") }
        items(quarantine, key = { it.folder }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Medium)
                        Text("${item.id} • ${item.trust.title()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!item.exists || !item.disablePresent) Text("Stale quarantine record", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { confirm = "one:${item.folder}" }, enabled = item.exists && item.disablePresent) { Text("Test") }
                }
            }
        }
    }
    confirm?.let { operation ->
        val text = when {
            operation == "half" -> "Restore half of the quarantined modules for a binary-search trial?"
            operation == "all" -> "Restore every quarantined module in one trial?"
            operation == "rollback" -> "Re-quarantine every module in the active restoration trial?"
            else -> "Restore this module for a boot trial?"
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Confirm recovery action") },
            text = { Text(text) },
            confirmButton = {
                Button(onClick = {
                    when {
                        operation == "half" -> viewModel.restoreHalf()
                        operation == "all" -> viewModel.restoreAll()
                        operation == "rollback" -> viewModel.rollbackTrial()
                        operation.startsWith("one:") -> viewModel.restoreOne(operation.substringAfter(':'))
                    }
                    confirm = null
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActivityScreen(activity: List<ActivityItem>) {
    var selected by remember { mutableStateOf<ActivityItem?>(null) }
    if (activity.isEmpty()) {
        CenterMessage("No rescue or restoration activity has been recorded yet.")
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(activity, key = { "${it.type}:${it.id}:${it.timestamp}" }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selected = item },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(statusColor(item.status))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Medium)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(relativeTime(item.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(item.status.title(), style = MaterialTheme.typography.labelMedium, color = statusColor(item.status))
                    }
                }
            }
        }
    }
    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(item.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyValue("Type", item.type.title())
                    KeyValue("Status", item.status.title())
                    KeyValue("Time", relativeTime(item.timestamp))
                    Text(item.details.ifBlank { item.subtitle })
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun SettingsScreen(settings: List<SettingItem>, theme: ThemePreset, onThemeChanged: (ThemePreset) -> Unit, viewModel: AshViewModel) {
    val map = settings.associateBy { it.key }
    var threshold by remember(settings) { mutableStateOf(map["threshold"]?.queuedValue ?: map["threshold"]?.value ?: "2") }
    var timeout by remember(settings) { mutableStateOf(map["timeout"]?.queuedValue ?: map["timeout"]?.value ?: "60") }
    var stability by remember(settings) { mutableStateOf(map["stability_time"]?.queuedValue ?: map["stability_time"]?.value ?: "60") }
    var extra by remember(settings) { mutableStateOf((map["extra_stability"]?.queuedValue ?: map["extra_stability"]?.value) == "true") }
    var missingAction by remember(settings) { mutableStateOf(map["missing_process_action"]?.queuedValue ?: map["missing_process_action"]?.value ?: "rescue") }
    val queued = settings.count { it.queuedValue != null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Protection") {
            NumericSetting("Failed boots before rescue", threshold) { threshold = it }
            NumericSetting("Boot timeout (seconds)", timeout) { timeout = it }
            NumericSetting("Stability window (seconds)", stability) { stability = it }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Extra process monitoring", fontWeight = FontWeight.Medium)
                    Text("Also monitor configured system daemons", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = extra, onCheckedChange = { extra = it })
            }
            Text("Missing process action", fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("warn", "rescue").forEach { value ->
                    FilterChip(selected = missingAction == value, onClick = { missingAction = value }, label = { Text(value.title()) })
                }
            }
            Button(onClick = {
                viewModel.setProtectionSettings(
                    linkedMapOf(
                        "threshold" to threshold,
                        "timeout" to timeout,
                        "stability_time" to stability,
                        "extra_stability" to extra.toString(),
                        "missing_process_action" to missingAction,
                    ),
                )
            }) { Text("Save protection settings") }
            if (queued > 0) {
                Text("$queued change(s) are queued for the next boot because protection is currently active.", color = MaterialTheme.colorScheme.tertiary)
                OutlinedButton(onClick = viewModel::discardPending) { Text("Discard queued changes") }
            }
        }
        SectionCard("Appearance") {
            Text("Theme palette", fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreset.entries.forEach { preset ->
                    FilterChip(selected = theme == preset, onClick = { onThemeChanged(preset) }, label = { Text(preset.label) })
                }
            }
        }
        SectionCard("Native companion") {
            Text("This app communicates through the fixed ashrexcuectl root API. The boot protector remains fully autonomous when the app is absent.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = viewModel::exportDiagnostics) { Text("Export sanitized diagnostics") }
        }
    }
}

@Composable
private fun NumericSetting(label: String, value: String, onValueChanged: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate -> if (candidate.all(Char::isDigit)) onValueChanged(candidate) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun HeroCard(title: String, subtitle: String, badge: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AssistChip(onClick = {}, label = { Text(badge) })
        }
    }
}

@Composable
private fun MetricGrid(metrics: List<Pair<String, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.forEach { (label, value) ->
            Card(modifier = Modifier.width(150.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(14.dp)) {
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Text(message, Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
}

@Composable
private fun statusColor(status: String): Color = when (status.lowercase(Locale.ROOT)) {
    "stable", "completed", "success" -> MaterialTheme.colorScheme.secondary
    "failed", "rolled-back", "quarantined" -> MaterialTheme.colorScheme.error
    "testing", "monitoring", "queued" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.outline
}

private fun String.title(): String = replace('-', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

@Composable
private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "Unknown time"
    return DateUtils.getRelativeTimeSpanString(timestamp * 1000L, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}
