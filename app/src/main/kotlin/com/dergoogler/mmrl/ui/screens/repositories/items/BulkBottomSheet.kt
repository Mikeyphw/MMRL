package com.dergoogler.mmrl.ui.screens.repositories.items

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ext.fadingEdge
import com.dergoogler.mmrl.ext.ignoreParentPadding
import com.dergoogler.mmrl.model.local.BulkModule
import com.dergoogler.mmrl.platform.file.SuFile.Companion.toFormattedFileSize
import com.dergoogler.mmrl.ui.component.BottomSheet
import com.dergoogler.mmrl.ui.component.PageIndicator
import com.dergoogler.mmrl.viewmodel.BulkInstallViewModel

@Composable
fun BulkBottomSheet(
    onClose: () -> Unit,
    modules: List<BulkModule>,
    bulkInstallViewModel: BulkInstallViewModel,
    onDownload: (List<BulkModule>, Boolean) -> Unit,
) = BottomSheet(onDismissRequest = onClose) {
    val isDownloading by bulkInstallViewModel.isDownloading.collectAsStateWithLifecycle()
    var selectedIds by remember(modules.map { it.id }) {
        mutableStateOf(modules.map { it.id }.toSet())
    }
    val selected = remember(modules, selectedIds) { modules.filter { it.id in selectedIds } }
    val totalSize = selected.sumOf { (it.versionItem.size ?: 0).toLong() }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = stringResource(R.string.bulk_module_install),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.bulk_selected_count, selected.size, modules.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (totalSize > 0L) {
            Text(
                text = stringResource(R.string.bulk_total_size, totalSize.toFormattedFileSize()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = { selectedIds = modules.map { it.id }.toSet() },
                enabled = selected.size != modules.size,
            ) {
                Text(stringResource(R.string.bulk_select_all))
            }
            TextButton(
                onClick = { selectedIds = emptySet() },
                enabled = selected.isNotEmpty(),
            ) {
                Text(stringResource(R.string.bulk_clear_selection))
            }
        }
    }

    val topBottomFade =
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.03f to Color.Red,
            0.97f to Color.Red,
            1f to Color.Transparent,
        )

    if (modules.isEmpty()) {
        PageIndicator(
            modifier = Modifier.weight(1f),
            icon = R.drawable.cloud,
            text = R.string.search_empty,
        )
    } else {
        LazyColumn(
            modifier = Modifier.weight(1f).animateContentSize().fadingEdge(topBottomFade),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(items = modules, key = { it.id }) { module ->
                BulkModuleItem(
                    modifier = Modifier.animateItem(),
                    module = module,
                    checked = module.id in selectedIds,
                    enabled = !isDownloading,
                    onCheckedChange = { checked ->
                        selectedIds =
                            if (checked) selectedIds + module.id else selectedIds - module.id
                    },
                    removeBulkModule = {
                        selectedIds = selectedIds - module.id
                        bulkInstallViewModel.removeBulkModule(module)
                    },
                )

                val progress = bulkInstallViewModel.getProgress(module)
                if (progress != 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        strokeCap = StrokeCap.Round,
                        modifier =
                            Modifier
                                .height(2.dp)
                                .padding(horizontal = 52.dp)
                                .ignoreParentPadding(vertical = 2.dp)
                                .fillMaxWidth(),
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isDownloading) {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = bulkInstallViewModel::cancelDownloads,
            ) {
                Text(stringResource(R.string.bulk_cancel_downloads))
            }
        } else {
            FilledTonalButton(
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = { onDownload(selected, false) },
            ) {
                Text(stringResource(R.string.bulk_download_selected))
            }
            Button(
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = { onDownload(selected, true) },
            ) {
                Text(stringResource(R.string.bulk_install_selected))
            }
        }
    }
}

@Composable
fun BulkModuleItem(
    modifier: Modifier = Modifier,
    module: BulkModule,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    removeBulkModule: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Column(
            modifier = Modifier.padding(start = 6.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = module.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = module.versionItem.versionDisplay,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            module.versionItem.size?.takeIf { it > 0L }?.let { size ->
                Text(
                    text = size.toFormattedFileSize(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = removeBulkModule, enabled = enabled) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.trash),
                contentDescription = stringResource(R.string.module_remove),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
