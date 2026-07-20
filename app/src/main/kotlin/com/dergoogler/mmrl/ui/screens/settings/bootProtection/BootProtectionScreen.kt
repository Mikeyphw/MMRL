package com.dergoogler.mmrl.ui.screens.settings.bootProtection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ash.AshOperationKind
import com.dergoogler.mmrl.ash.AshViewModel
import com.dergoogler.mmrl.ash.model.SettingItem
import com.dergoogler.mmrl.ext.none
import com.dergoogler.mmrl.ui.component.LocalScreenProvider
import com.dergoogler.mmrl.ui.component.scaffold.Scaffold
import com.dergoogler.mmrl.ui.component.toolbar.BlurNavigateUpToolbar
import com.dergoogler.mmrl.ui.providable.LocalMainScreenInnerPaddings
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.ui.providable.LocalNavController
import com.dergoogler.mmrl.ui.providable.LocalSnackbarHost
import com.dergoogler.mmrl.ui.providable.mainContentBottomPadding
import com.dergoogler.mmrl.viewmodel.SettingsViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph

private data class NumericRule(val range: IntRange)

private val recommendedDefaults =
    linkedMapOf(
        "threshold" to "2",
        "timeout" to "60",
        "stability_time" to "60",
        "extra_stability" to "false",
        "missing_process_action" to "rescue",
        "boot_animation_required" to "true",
        "ce_storage_required" to "true",
        "first_boot_grace" to "90",
        "ota_grace_time" to "180",
        "monitored_processes" to "servicemanager,vold",
    )

@Destination<RootGraph>
@Composable
fun BootProtectionScreen(viewModel: AshViewModel = hiltViewModel()) =
    LocalScreenProvider {
        val settingsViewModel: SettingsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val preferences = LocalUserPreferences.current
        val navController = LocalNavController.current
        val snackbar = LocalSnackbarHost.current
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        val bottomPadding = LocalMainScreenInnerPaddings.current.mainContentBottomPadding()

        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                viewModel.clearMessage()
            }
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                BlurNavigateUpToolbar(
                    title = stringResource(R.string.settings_boot_protection),
                    navController = navController,
                    scrollBehavior = scrollBehavior,
                )
            },
            contentWindowInsets = WindowInsets.none,
        ) { innerPadding ->
            BootProtectionContent(
                settings = state.settings,
                readOnly = state.readOnly,
                lifecycleText = state.lifecycle.compatibilityMessage,
                saving = state.isOperationRunning(AshOperationKind.SaveSettings, "protection"),
                discarding = state.isOperationRunning(AshOperationKind.DiscardPending),
                healthChecksEnabled = preferences.ashHealthChecksEnabled,
                healthCheckIntervalHours = preferences.ashHealthCheckIntervalHours,
                incidentNotifications = preferences.ashIncidentNotifications,
                rebootReminders = preferences.ashRebootReminders,
                restorationReminders = preferences.ashRestorationReminders,
                onHealthChecksEnabled = settingsViewModel::setAshHealthChecksEnabled,
                onHealthCheckInterval = settingsViewModel::setAshHealthCheckIntervalHours,
                onIncidentNotifications = settingsViewModel::setAshIncidentNotifications,
                onRebootReminders = settingsViewModel::setAshRebootReminders,
                onRestorationReminders = settingsViewModel::setAshRestorationReminders,
                onCheckNow = settingsViewModel::runAshHealthCheckNow,
                onSave = viewModel::setProtectionSettings,
                onDiscardPending = viewModel::discardPending,
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = innerPadding.calculateTopPadding())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = bottomPadding),
            )
        }
    }

@Composable
private fun BootProtectionContent(
    settings: List<SettingItem>,
    readOnly: Boolean,
    lifecycleText: String,
    saving: Boolean,
    discarding: Boolean,
    healthChecksEnabled: Boolean,
    healthCheckIntervalHours: Long,
    incidentNotifications: Boolean,
    rebootReminders: Boolean,
    restorationReminders: Boolean,
    onHealthChecksEnabled: (Boolean) -> Unit,
    onHealthCheckInterval: (Long) -> Unit,
    onIncidentNotifications: (Boolean) -> Unit,
    onRebootReminders: (Boolean) -> Unit,
    onRestorationReminders: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
    onDiscardPending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val byKey = settings.associateBy(SettingItem::key)
    fun value(key: String): String =
        byKey[key]?.queuedValue ?: byKey[key]?.value ?: recommendedDefaults.getValue(key)

    var threshold by remember(settings) { mutableStateOf(value("threshold")) }
    var timeout by remember(settings) { mutableStateOf(value("timeout")) }
    var stability by remember(settings) { mutableStateOf(value("stability_time")) }
    var firstBootGrace by remember(settings) { mutableStateOf(value("first_boot_grace")) }
    var otaGrace by remember(settings) { mutableStateOf(value("ota_grace_time")) }
    var monitoredProcesses by remember(settings) { mutableStateOf(value("monitored_processes").trim('"')) }
    var extraStability by remember(settings) { mutableStateOf(value("extra_stability") == "true") }
    var bootAnimationRequired by remember(settings) { mutableStateOf(value("boot_animation_required") == "true") }
    var ceStorageRequired by remember(settings) { mutableStateOf(value("ce_storage_required") == "true") }
    var missingProcessAction by remember(settings) { mutableStateOf(value("missing_process_action")) }

    val errors =
        listOfNotNull(
            numericError(threshold, NumericRule(1..5), "Failed boots"),
            numericError(timeout, NumericRule(60..300), "Boot timeout"),
            numericError(stability, NumericRule(30..600), "Stability window"),
            numericError(firstBootGrace, NumericRule(0..600), "First-boot grace"),
            numericError(otaGrace, NumericRule(0..900), "OTA grace"),
            monitoredProcesses.takeIf { it.contains('\n') }?.let { "Monitored processes must be comma separated" },
        )
    val pending = settings.filter { it.queuedValue != null }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProtectionSection("Integration status") {
            Text(lifecycleText.ifBlank { "AshReXcue status unavailable" })
            Text(
                if (readOnly) "Live controls are unavailable; cached values are read only." else "Changes are validated by AshReXcue before they are applied.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ProtectionSection("Proactive recovery") {
            BooleanSetting(
                title = "Background health checks",
                checked = healthChecksEnabled,
                enabled = true,
                onCheckedChange = onHealthChecksEnabled,
            )
            Text(
                "Checks reuse MMRL's typed AshReXcue snapshot and only notify when recovery action is required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Check interval", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1L, 3L, 6L, 12L, 24L).forEach { hours ->
                    FilterChip(
                        selected = healthCheckIntervalHours == hours,
                        onClick = { onHealthCheckInterval(hours) },
                        enabled = healthChecksEnabled,
                        label = { Text(if (hours == 1L) "1 hour" else "$hours hours") },
                    )
                }
            }
            BooleanSetting(
                title = "Recovery incident alerts",
                checked = incidentNotifications,
                enabled = healthChecksEnabled,
                onCheckedChange = onIncidentNotifications,
            )
            BooleanSetting(
                title = "Reboot-state reminders",
                checked = rebootReminders,
                enabled = healthChecksEnabled,
                onCheckedChange = onRebootReminders,
            )
            BooleanSetting(
                title = "Restoration-trial reminders",
                checked = restorationReminders,
                enabled = healthChecksEnabled,
                onCheckedChange = onRestorationReminders,
            )
            OutlinedButton(onClick = onCheckNow, enabled = healthChecksEnabled) {
                Text("Check now")
            }
        }

        if (pending.isNotEmpty()) {
            ProtectionSection("Queued for next boot") {
                pending.forEach { setting ->
                    Text(
                        "${setting.key}: ${setting.value} → ${setting.queuedValue}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onDiscardPending, enabled = !readOnly && !discarding && !saving) {
                    Text(if (discarding) "Discarding…" else "Discard queued changes")
                }
            }
        }

        ProtectionSection("Rescue thresholds") {
            NumericField("Failed boots before rescue", threshold, 1..5, !readOnly) { threshold = it }
            NumericField("Boot timeout (seconds)", timeout, 60..300, !readOnly) { timeout = it }
            NumericField("Stability window (seconds)", stability, 30..600, !readOnly) { stability = it }
        }

        ProtectionSection("Boot readiness") {
            BooleanSetting("Require boot animation completion", bootAnimationRequired, !readOnly) { bootAnimationRequired = it }
            BooleanSetting("Require credential-encrypted storage", ceStorageRequired, !readOnly) { ceStorageRequired = it }
            BooleanSetting("Extra process monitoring", extraStability, !readOnly) { extraStability = it }
            NumericField("First-boot grace (seconds)", firstBootGrace, 0..600, !readOnly) { firstBootGrace = it }
            NumericField("OTA grace (seconds)", otaGrace, 0..900, !readOnly) { otaGrace = it }
            OutlinedTextField(
                value = monitoredProcesses,
                onValueChange = { monitoredProcesses = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Monitored processes") },
                supportingText = { Text("Comma-separated process names") },
                enabled = !readOnly,
                singleLine = true,
            )
            Text("Missing process action", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("warn", "rescue").forEach { action ->
                    FilterChip(
                        selected = missingProcessAction == action,
                        onClick = { missingProcessAction = action },
                        enabled = !readOnly,
                        label = { Text(action.replaceFirstChar(Char::uppercaseChar)) },
                    )
                }
            }
        }

        if (errors.isNotEmpty()) {
            ProtectionSection("Fix before saving") {
                errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    onSave(
                        linkedMapOf(
                            "threshold" to threshold,
                            "timeout" to timeout,
                            "stability_time" to stability,
                            "extra_stability" to extraStability.toString(),
                            "missing_process_action" to missingProcessAction,
                            "boot_animation_required" to bootAnimationRequired.toString(),
                            "ce_storage_required" to ceStorageRequired.toString(),
                            "first_boot_grace" to firstBootGrace,
                            "ota_grace_time" to otaGrace,
                            "monitored_processes" to monitoredProcesses,
                        ),
                    )
                },
                enabled = !readOnly && !saving && !discarding && errors.isEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (saving) "Saving…" else if (pending.isEmpty()) "Save settings" else "Update queued settings")
            }
            OutlinedButton(
                onClick = {
                    threshold = recommendedDefaults.getValue("threshold")
                    timeout = recommendedDefaults.getValue("timeout")
                    stability = recommendedDefaults.getValue("stability_time")
                    extraStability = false
                    missingProcessAction = "rescue"
                    bootAnimationRequired = true
                    ceStorageRequired = true
                    firstBootGrace = recommendedDefaults.getValue("first_boot_grace")
                    otaGrace = recommendedDefaults.getValue("ota_grace_time")
                    monitoredProcesses = recommendedDefaults.getValue("monitored_processes")
                },
                enabled = !readOnly,
            ) {
                Text("Recommended defaults")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProtectionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    range: IntRange,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val parsed = value.toIntOrNull()
    val invalid = parsed == null || parsed !in range
    OutlinedTextField(
        value = value,
        onValueChange = { candidate -> if (candidate.all(Char::isDigit)) onChange(candidate) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text("Allowed: ${range.first}–${range.last}") },
        isError = invalid,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun BooleanSetting(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun numericError(value: String, rule: NumericRule, label: String): String? {
    val parsed = value.toIntOrNull()
    return if (parsed == null || parsed !in rule.range) {
        "$label must be between ${rule.range.first} and ${rule.range.last}"
    } else {
        null
    }
}
