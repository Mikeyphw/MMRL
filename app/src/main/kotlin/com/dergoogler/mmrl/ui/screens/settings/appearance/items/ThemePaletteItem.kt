package com.dergoogler.mmrl.ui.screens.settings.appearance.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ui.theme.LocalSemanticColors
import com.dergoogler.mmrl.ui.theme.MMRLAppTheme
import com.dergoogler.mmrl.ui.theme.ThemeColorSource
import com.dergoogler.mmrl.ui.theme.ThemeContrast
import com.dergoogler.mmrl.ui.theme.ThemeDefinition
import com.dergoogler.mmrl.ui.theme.ThemeRegistry
import com.dergoogler.mmrl.ui.theme.ThemeSurfaceStyle

@Composable
fun ThemePaletteItem(
    selectedPaletteId: String,
    onPreview: (ThemeDefinition) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeRegistry.builtIns.take(5).forEach { definition ->
            ThemePaletteCard(
                definition = definition,
                selected = definition.id == selectedPaletteId,
                onClick = { onPreview(definition) },
            )
        }

        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "Legacy palettes",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThemeRegistry.builtIns.drop(5).forEach { definition ->
            ThemePaletteCard(
                definition = definition,
                selected = definition.id == selectedPaletteId,
                onClick = { onPreview(definition) },
            )
        }
    }
}

@Composable
private fun ThemePaletteCard(
    definition: ThemeDefinition,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                definition.previewColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(definition.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    definition.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.circle_check_filled),
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun ThemePreviewDialog(
    definition: ThemeDefinition,
    darkMode: Boolean,
    colorSource: ThemeColorSource,
    fallbackId: String,
    surfaceStyle: ThemeSurfaceStyle,
    contrast: ThemeContrast,
    pureBlack: Boolean,
    accentIntensity: Float,
    enhancedStatuses: Boolean,
    customThemeJson: String,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        MMRLAppTheme(
            paletteId = definition.id,
            colorSource = colorSource,
            dynamicFallbackPaletteId = fallbackId,
            surfaceStyle = surfaceStyle,
            contrast = contrast,
            pureBlack = pureBlack,
            accentIntensity = accentIntensity,
            enhancedStatusDistinction = enhancedStatuses,
            customThemeJson = customThemeJson,
            darkMode = darkMode,
        ) {
            val semantic = LocalSemanticColors.current
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = definition.displayName,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        FilledTonalButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = onApply) { Text("Apply theme") }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("MMRL component preview", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Repository rows, installed state, status colors, controls and operation feedback.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PreviewModuleRow("Audio Compatibility Patch", "v2.6 · AndroidAudioMods", "Update available")
                        PreviewModuleRow("Zygisk Next", "v1.2.8 · Enabled", "Running normally")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PreviewStatusChip("Enabled", semantic.success)
                            PreviewStatusChip("Update", semantic.updateAvailable)
                            PreviewStatusChip("Reboot", semantic.rebootRequired)
                            PreviewStatusChip("Verified", semantic.verified)
                            PreviewStatusChip("Rollback", semantic.rollbackAvailable)
                            PreviewStatusChip("Failed", semantic.error)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Needs attention", color = MaterialTheme.colorScheme.onErrorContainer)
                                Text(
                                    "This update adds a boot service and requires review.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Module enabled")
                                Text("Switch and progress states", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = true, onCheckedChange = {})
                        }
                        HorizontalDivider()
                        Text("Downloading module", style = MaterialTheme.typography.titleSmall)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Box(Modifier.height(8.dp).fillMaxWidth(.68f).background(MaterialTheme.colorScheme.primary))
                        }
                        FilledTonalButton(onClick = {}) { Text("Open review") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewModuleRow(title: String, metadata: String, status: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(title.first().toString(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}


@Composable
private fun PreviewStatusChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = .16f),
        contentColor = color,
        shape = CircleShape,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
