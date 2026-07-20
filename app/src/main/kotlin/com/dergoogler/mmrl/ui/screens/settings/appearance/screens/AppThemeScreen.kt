package com.dergoogler.mmrl.ui.screens.settings.appearance.screens

import android.content.ClipData
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ui.component.ScaffoldDefaults
import com.dergoogler.mmrl.ui.component.SettingsScaffold
import com.dergoogler.mmrl.ui.providable.LocalSettings
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.ui.screens.settings.appearance.items.DarkModeItem
import com.dergoogler.mmrl.ui.screens.settings.appearance.items.ThemePaletteItem
import com.dergoogler.mmrl.ui.screens.settings.appearance.items.ThemePreviewDialog
import com.dergoogler.mmrl.ui.screens.settings.appearance.items.TitleItem
import com.dergoogler.mmrl.ui.theme.CustomThemeDocument
import com.dergoogler.mmrl.ui.theme.ThemeColorSource
import com.dergoogler.mmrl.ui.theme.ThemeContrast
import com.dergoogler.mmrl.ui.theme.ThemeDefinition
import com.dergoogler.mmrl.ui.theme.ThemeRegistry
import com.dergoogler.mmrl.ui.theme.ThemeSurfaceStyle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.launch

@Destination<RootGraph>
@Composable
fun AppThemeScreen() {
    val preferences = LocalUserPreferences.current
    val viewModel = LocalSettings.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var preview by remember { mutableStateOf<ThemeDefinition?>(null) }
    var previewSource by remember { mutableStateOf<ThemeColorSource?>(null) }
    var previewCustomJson by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var importValue by remember(preferences.customThemeJson) {
        mutableStateOf(preferences.customThemeJson.ifBlank { CustomThemeDocument().encode() })
    }
    var importError by remember { mutableStateOf<String?>(null) }
    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to open theme file")
            }.onSuccess { json ->
                importValue = json
                importError = CustomThemeDocument.decode(json).exceptionOrNull()?.message
                showImport = true
            }.onFailure { error ->
                importError = error.message ?: "Unable to import theme"
                showImport = true
            }
        }
    }
    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val json = preferences.customThemeJson.ifBlank { CustomThemeDocument().encode() }
                CustomThemeDocument.decode(json).getOrThrow()
                context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                    ?: error("Unable to create theme file")
            }.onFailure { error ->
                importError = error.message ?: "Unable to export theme"
                showImport = true
            }
        }
    }

    SettingsScaffold(
        modifier = ScaffoldDefaults.settingsScaffoldScrollModifier,
        title = R.string.settings_app_theme,
    ) {
        TitleItem(text = "Appearance")
        DarkModeItem(
            darkMode = preferences.darkMode,
            onChange = viewModel::setDarkTheme,
        )
        SwitchSettingRow(
            title = "Battery saver forces dark",
            description = "Temporarily use dark appearance while Android battery saver is active.",
            checked = preferences.batterySaverForcesDark,
            onCheckedChange = viewModel::setBatterySaverForcesDark,
        )

        TitleItem(text = "Color source")
        ChoiceRow(
            options = listOf(
                ThemeColorSource.BUILT_IN to "Palette",
                ThemeColorSource.DYNAMIC_ACCENT to "Wallpaper accent",
                ThemeColorSource.DYNAMIC_FULL to "Full Monet",
                ThemeColorSource.CUSTOM to "Custom",
            ),
            selected = preferences.resolvedThemeColorSource(),
            onSelect = viewModel::setThemeColorSource,
        )
        if (preferences.resolvedThemeColorSource() == ThemeColorSource.DYNAMIC_ACCENT || preferences.resolvedThemeColorSource() == ThemeColorSource.DYNAMIC_FULL) {
            Text(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (preferences.resolvedThemeColorSource() == ThemeColorSource.DYNAMIC_ACCENT) {
                        "Wallpaper colors affect actions and selection while MMRL keeps neutral surfaces."
                    } else {
                        "Wallpaper colors control the full Material color scheme."
                    }
                } else {
                    "Dynamic colors require Android 12. The selected fallback palette is used on this device."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            TitleItem(text = "Dynamic color fallback")
            ChoiceRow(
                options = ThemeRegistry.builtIns.take(5).map { it.id to it.displayName },
                selected = preferences.dynamicFallbackPaletteId,
                onSelect = viewModel::setDynamicFallbackPaletteId,
            )
        }

        if (preferences.resolvedThemeColorSource() == ThemeColorSource.CUSTOM) {
            TitleItem(text = "Custom theme")
            CustomThemeDocument.decode(preferences.customThemeJson).getOrNull()?.let { document ->
                Text(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    text = "Active: ${document.name} · ${document.id}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { showImport = true }) { Text("Import or edit") }
                OutlinedButton(onClick = { importFileLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }) {
                    Text("Import file")
                }
                OutlinedButton(
                    onClick = {
                        val text = preferences.customThemeJson.ifBlank { CustomThemeDocument().encode() }
                        coroutineScope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("MMRL theme", text)),
                            )
                        }
                    },
                ) { Text("Copy JSON") }
                OutlinedButton(onClick = { exportFileLauncher.launch("mmrl-theme.json") }) {
                    Text("Export file")
                }
                if (preferences.customThemeJson.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            ThemeRegistry.customDefinition(preferences.customThemeJson).getOrNull()?.let { definition ->
                                previewSource = ThemeColorSource.CUSTOM
                                previewCustomJson = preferences.customThemeJson
                                preview = definition
                            }
                        },
                    ) { Text("Preview") }
                }
            }
        }

        TitleItem(text = "Theme palette")
        ThemePaletteItem(
            selectedPaletteId = preferences.resolvedThemePaletteId(),
            onPreview = { definition ->
                previewSource = ThemeColorSource.BUILT_IN
                previewCustomJson = null
                preview = definition
            },
        )

        TitleItem(text = "Surface style")
        ChoiceRow(
            options = listOf(
                ThemeSurfaceStyle.FLAT to "Flat",
                ThemeSurfaceStyle.SOFT_TONAL to "Soft tonal",
                ThemeSurfaceStyle.HIGH_CONTRAST to "High contrast",
            ),
            selected = preferences.themeSurfaceStyle,
            onSelect = viewModel::setThemeSurfaceStyle,
        )

        TitleItem(text = "Contrast")
        ChoiceRow(
            options = listOf(
                ThemeContrast.STANDARD to "Standard",
                ThemeContrast.MEDIUM to "Medium",
                ThemeContrast.HIGH to "High",
            ),
            selected = preferences.themeContrast,
            onSelect = viewModel::setThemeContrast,
        )

        SwitchSettingRow(
            title = "Pure black background",
            description = "Use black only for the lowest OLED background layer; sheets and dialogs remain distinguishable.",
            checked = preferences.themePureBlack,
            onCheckedChange = viewModel::setThemePureBlack,
        )
        SwitchSettingRow(
            title = "Enhanced status distinction",
            description = "Keep update, reboot, verification, failure and rollback states visually distinct and color-blind friendly.",
            checked = preferences.enhancedStatusDistinction,
            onCheckedChange = viewModel::setEnhancedStatusDistinction,
        )

        TitleItem(text = "Accent intensity")
        Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text(
                text = "${(preferences.themeAccentIntensity * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = preferences.themeAccentIntensity,
                onValueChange = viewModel::setThemeAccentIntensity,
                valueRange = 0f..1f,
            )
        }
    }

    preview?.let { definition ->
        ThemePreviewDialog(
            definition = definition,
            darkMode = preferences.isDarkMode(),
            colorSource = previewSource ?: preferences.resolvedThemeColorSource(),
            fallbackId = preferences.dynamicFallbackPaletteId,
            surfaceStyle = preferences.themeSurfaceStyle,
            contrast = preferences.themeContrast,
            pureBlack = preferences.themePureBlack,
            accentIntensity = preferences.themeAccentIntensity,
            enhancedStatuses = preferences.enhancedStatusDistinction,
            customThemeJson = previewCustomJson ?: preferences.customThemeJson,
            onDismiss = {
                preview = null
                previewSource = null
                previewCustomJson = null
            },
            onApply = {
                previewCustomJson?.let(viewModel::setCustomThemeJson)
                viewModel.setThemePaletteId(definition.id)
                previewSource?.let(viewModel::setThemeColorSource)
                preview = null
                previewSource = null
                previewCustomJson = null
            },
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import custom theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste a schema 1 theme document. Colors accept #RRGGBB or #AARRGGBB.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = importValue,
                        onValueChange = {
                            importValue = it
                            importError = null
                        },
                        minLines = 10,
                        maxLines = 18,
                        isError = importError != null,
                        supportingText = if (importError != null) ({ Text(importError.orEmpty()) }) else null,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CustomThemeDocument.decode(importValue)
                            .onSuccess { document ->
                                val definition = ThemeRegistry.customDefinition(importValue).getOrThrow()
                                previewSource = ThemeColorSource.CUSTOM
                                previewCustomJson = document.encode()
                                preview = definition
                                importError = null
                                showImport = false
                            }
                            .onFailure { importError = it.message ?: "Invalid custom theme" }
                    },
                ) { Text("Preview") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
