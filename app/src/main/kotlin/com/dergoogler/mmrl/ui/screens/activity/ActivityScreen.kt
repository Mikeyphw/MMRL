package com.dergoogler.mmrl.ui.screens.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.database.entity.history.OperationStatus
import com.dergoogler.mmrl.ext.none
import com.dergoogler.mmrl.ui.component.BottomSheet
import com.dergoogler.mmrl.ui.component.LocalScreenProvider
import com.dergoogler.mmrl.ui.component.PageIndicator
import com.dergoogler.mmrl.ui.component.scaffold.Scaffold
import com.dergoogler.mmrl.ui.component.toolbar.BlurToolbar
import com.dergoogler.mmrl.ui.component.toolbar.ToolbarTitle
import com.dergoogler.mmrl.ui.providable.LocalMainScreenInnerPaddings
import com.dergoogler.mmrl.ui.providable.mainContentBottomPadding
import com.dergoogler.mmrl.ui.providable.LocalSnackbarHost
import com.dergoogler.mmrl.ui.theme.LocalSemanticColors
import com.dergoogler.mmrl.ui.theme.LocalMMRLSurfaces
import com.dergoogler.mmrl.viewmodel.ActivityFilter
import com.dergoogler.mmrl.viewmodel.ActivityViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Destination<RootGraph>
@Composable
fun ActivityScreen(viewModel: ActivityViewModel = hiltViewModel()) =
    LocalScreenProvider {
        val context = LocalContext.current
        val snackbar = LocalSnackbarHost.current
        val entries by viewModel.visibleHistory.collectAsStateWithLifecycle()
        val filter by viewModel.filter.collectAsStateWithLifecycle()
        val pendingReboots by viewModel.pendingRebootCount.collectAsStateWithLifecycle()
        val bottomPadding = LocalMainScreenInnerPaddings.current.mainContentBottomPadding()
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

        var selectedEntry by remember { mutableStateOf<OperationHistoryEntity?>(null) }
        var confirmClear by remember { mutableStateOf(false) }

        LaunchedEffect(viewModel) {
            viewModel.messages.collectLatest(snackbar::showSnackbar)
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.activity_clear_title)) },
                text = { Text(stringResource(R.string.activity_clear_description)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmClear = false
                            viewModel.clearHistory()
                        },
                    ) {
                        Text(stringResource(R.string.activity_clear_all))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        selectedEntry?.let { entry ->
            ActivityDetailsSheet(
                entry = entry,
                onClose = { selectedEntry = null },
                onRetry = {
                    selectedEntry = null
                    viewModel.retry(context, entry)
                },
                onRollback = {
                    selectedEntry = null
                    viewModel.rollback(context, entry)
                },
                onCancel = {
                    selectedEntry = null
                    viewModel.cancel(context, entry)
                },
                onApproveTasker = {
                    selectedEntry = null
                    viewModel.approveTaskerRequest(context, entry)
                },
                onDenyTasker = {
                    selectedEntry = null
                    viewModel.denyTaskerRequest(context, entry)
                },
                onDelete = {
                    selectedEntry = null
                    viewModel.delete(entry)
                },
                onCopyLog = { copyLog(context, entry) },
                onShareLog = { shareLog(context, entry) },
            )
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                BlurToolbar(
                    title = {
                        ToolbarTitle(
                            title = stringResource(R.string.page_activity),
                            subtitle = stringResource(R.string.activity_subtitle),
                        )
                    },
                    actions = {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(
                                painter = painterResource(R.drawable.clear_all),
                                contentDescription = stringResource(R.string.activity_clear_title),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            contentWindowInsets = WindowInsets.none,
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize()) {
                ActivityFilters(
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                    selected = filter,
                    pendingReboots = pendingReboots,
                    onSelected = viewModel::setFilter,
                )

                if (pendingReboots > 0 && filter != ActivityFilter.PENDING_REBOOT) {
                    PendingRebootBanner(
                        count = pendingReboots,
                        onShow = { viewModel.setFilter(ActivityFilter.PENDING_REBOOT) },
                        onClear = viewModel::markRebootCompleted,
                    )
                }

                if (entries.isEmpty()) {
                    PageIndicator(
                        icon = R.drawable.logs,
                        text = if (filter == ActivityFilter.ALL) R.string.activity_empty else R.string.activity_filter_empty,
                    )
                    return@Column
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = 6.dp,
                            bottom = bottomPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, entry -> entry.id },
                    ) { index, entry ->
                        if (index == 0 || !isSameDay(entries[index - 1].startedAt, entry.startedAt)) {
                            ActivityDayHeader(entry.startedAt)
                        }
                        ActivityRow(
                            entry = entry,
                            onClick = { selectedEntry = entry },
                        )
                    }
                }
            }
        }
    }

@Composable
private fun ActivityFilters(
    modifier: Modifier,
    selected: ActivityFilter,
    pendingReboots: Int,
    onSelected: (ActivityFilter) -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ActivityFilter.entries.forEach { filter ->
            val label =
                when (filter) {
                    ActivityFilter.ALL -> stringResource(R.string.activity_filter_all)
                    ActivityFilter.RUNNING -> stringResource(R.string.activity_filter_running)
                    ActivityFilter.DOWNLOADS -> stringResource(R.string.activity_filter_downloads)
                    ActivityFilter.FAILED -> stringResource(R.string.activity_filter_failed)
                    ActivityFilter.PENDING_REBOOT ->
                        if (pendingReboots > 0) {
                            stringResource(R.string.activity_filter_reboot_count, pendingReboots)
                        } else {
                            stringResource(R.string.activity_filter_reboot)
                        }
                    ActivityFilter.ASHREXCUE -> stringResource(R.string.activity_filter_ashrexcue)
                }
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun PendingRebootBanner(
    count: Int,
    onShow: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.reload),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.activity_pending_reboot, count, count),
                modifier = Modifier.padding(start = 9.dp).weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FilledTonalButton(onClick = onShow) { Text(stringResource(R.string.activity_view)) }
            TextButton(onClick = onClear) { Text(stringResource(R.string.activity_mark_rebooted)) }
        }
    }
}


@Composable
private fun ActivityDayHeader(timestamp: Long) {
    Text(
        text = dayLabel(timestamp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ActivityRow(
    entry: OperationHistoryEntity,
    onClick: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    val surfaces = LocalMMRLSurfaces.current
    val color = entry.statusColor()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = surfaces.row,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = color.copy(alpha = 0.14f),
                    contentColor = color,
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Icon(
                        painter = painterResource(entry.icon()),
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp),
                    )
                }

                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.displayTitle(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatTime(entry.startedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = entry.kindLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                    if (entry.summary.isNotBlank()) {
                        Text(
                            text = entry.displaySummary(),
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FlowRow(
                        modifier = Modifier.padding(top = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ActivityTag(entry.statusLabel(), color)
                        if (entry.origin == "TASKER") {
                            ActivityTag(stringResource(R.string.activity_source_tasker), MaterialTheme.colorScheme.primary)
                        }
                        entry.phase?.takeIf { entry.isRunning }?.let { phase ->
                            ActivityTag(
                                phase.lowercase().replaceFirstChar(Char::uppercase),
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (entry.isPendingReboot) {
                            ActivityTag(stringResource(R.string.modules_reboot_required), semantic.rebootRequired)
                        }
                        if (entry.canOfferRetry && entry.isFailed) {
                            ActivityTag(stringResource(R.string.activity_retry_available), MaterialTheme.colorScheme.primary)
                        }
                        if (entry.canRollback && !entry.isRunning) {
                            ActivityTag(stringResource(R.string.activity_rollback), semantic.rollbackAvailable)
                        }
                    }
                }
            }

            if (entry.isRunning) {
                val value = entry.progress?.div(100f)
                if (value != null && value > 0f) {
                    LinearProgressIndicator(
                        progress = { value },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityTag(
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

@Composable
private fun ActivityDetailsSheet(
    entry: OperationHistoryEntity,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onRollback: () -> Unit,
    onCancel: () -> Unit,
    onApproveTasker: () -> Unit,
    onDenyTasker: () -> Unit,
    onDelete: () -> Unit,
    onCopyLog: () -> Unit,
    onShareLog: () -> Unit,
) = BottomSheet(onDismissRequest = onClose) {
    Text(
        text = entry.displayTitle(),
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = entry.kindLabel(),
        modifier = Modifier.padding(horizontal = 18.dp),
        style = MaterialTheme.typography.labelLarge,
        color = entry.statusColor(),
    )

    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        DetailLine(stringResource(R.string.activity_status), entry.statusLabel())
        entry.origin?.let { origin ->
            DetailLine(
                stringResource(R.string.activity_origin),
                when (origin) {
                    "TASKER" -> stringResource(R.string.activity_source_tasker)
                    "ashrexcue" -> "AshReXcue"
                    else -> origin
                },
            )
        }
        DetailLine(stringResource(R.string.activity_started), formatDateTime(entry.startedAt))
        entry.completedAt?.let { DetailLine(stringResource(R.string.activity_completed), formatDateTime(it)) }
        entry.moduleId?.let { DetailLine(stringResource(R.string.activity_module_id), it) }
        entry.phase?.let { DetailLine(stringResource(R.string.activity_phase), it.lowercase().replaceFirstChar(Char::uppercase)) }
        entry.previousVersion?.let { DetailLine(stringResource(R.string.activity_previous_version), it) }
        entry.targetVersion?.let { DetailLine(stringResource(R.string.activity_target_version), it) }
        entry.inspectionSummary?.let { DetailLine(stringResource(R.string.activity_inspection), it) }
        entry.rollbackArchivePath?.let { DetailLine(stringResource(R.string.activity_rollback_archive), it) }
        entry.sourceUrl?.let { DetailLine(stringResource(R.string.activity_source), it) }
        entry.destinationPath?.let { DetailLine(stringResource(R.string.activity_destination), it) }
        if (entry.isPendingReboot) {
            DetailLine(stringResource(R.string.activity_reboot_state), stringResource(R.string.modules_reboot_required))
        }
        entry.errorMessage?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(humanizeActivityMessage(error), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            text = stringResource(R.string.activity_technical_log),
            modifier = Modifier.padding(top = 18.dp, bottom = 7.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        val logPreview = entry.technicalLog.takeLast(20_000)
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = logPreview.ifBlank { stringResource(R.string.activity_no_log) },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (entry.technicalLog.length > logPreview.length) {
            Text(
                text = stringResource(R.string.activity_log_truncated),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onCopyLog, enabled = entry.technicalLog.isNotBlank()) {
                Text(stringResource(R.string.activity_copy_log))
            }
            TextButton(onClick = onShareLog, enabled = entry.technicalLog.isNotBlank()) {
                Text(stringResource(R.string.activity_share_log))
            }
            if (entry.origin != "ashrexcue") {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.activity_delete_entry), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            val awaitingTaskerApproval =
                entry.origin == "TASKER" &&
                    entry.isRunning &&
                    entry.phase == OperationPhase.APPROVAL.name
            if (awaitingTaskerApproval) {
                TextButton(onClick = onDenyTasker) {
                    Text(
                        text = stringResource(R.string.activity_deny_tasker),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = onApproveTasker) {
                    Text(stringResource(R.string.activity_approve_tasker))
                }
            }
            if (entry.isRunning && entry.kind == OperationKind.DOWNLOAD.name) {
                Button(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
            if (entry.canRollback && !entry.isRunning) {
                Button(onClick = onRollback) { Text(stringResource(R.string.activity_rollback)) }
            }
            if (entry.canOfferRetry && entry.isFailed) {
                Button(onClick = onRetry) { Text(stringResource(R.string.activity_retry)) }
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OperationHistoryEntity.statusColor(): Color {
    val semantic = LocalSemanticColors.current
    return when (status) {
        OperationStatus.RUNNING.name -> semantic.info
        OperationStatus.SUCCEEDED.name -> semantic.success
        OperationStatus.FAILED.name -> semantic.error
        else -> semantic.disabled
    }
}

@Composable
private fun OperationHistoryEntity.statusLabel(): String =
    when (status) {
        OperationStatus.RUNNING.name -> stringResource(R.string.activity_status_running)
        OperationStatus.SUCCEEDED.name -> stringResource(R.string.activity_status_succeeded)
        OperationStatus.FAILED.name -> stringResource(R.string.activity_status_failed)
        else -> stringResource(R.string.activity_status_cancelled)
    }

@Composable
private fun OperationHistoryEntity.kindLabel(): String =
    when (runCatching { OperationKind.valueOf(kind) }.getOrNull()) {
        OperationKind.DOWNLOAD -> stringResource(R.string.activity_kind_download)
        OperationKind.INSTALL -> stringResource(R.string.activity_kind_install)
        OperationKind.UPDATE -> stringResource(R.string.activity_kind_update)
        OperationKind.ENABLE -> stringResource(R.string.activity_kind_enable)
        OperationKind.DISABLE -> stringResource(R.string.activity_kind_disable)
        OperationKind.REMOVE -> stringResource(R.string.activity_kind_remove)
        OperationKind.RESTORE -> stringResource(R.string.activity_kind_restore)
        OperationKind.MODULE_ACTION -> stringResource(R.string.activity_kind_action)
        OperationKind.ROLLBACK -> stringResource(R.string.activity_rollback)
        OperationKind.CHECK_UPDATES -> stringResource(R.string.activity_kind_check_updates)
        OperationKind.EXPORT_LOG -> stringResource(R.string.activity_kind_export_log)
        OperationKind.PREPARE_INSTALL -> stringResource(R.string.activity_kind_prepare_install)
        OperationKind.ASH_RESCUE -> stringResource(R.string.activity_kind_ash_rescue)
        OperationKind.ASH_RESTORATION -> stringResource(R.string.activity_kind_ash_restoration)
        OperationKind.ASH_SETTINGS -> stringResource(R.string.activity_kind_ash_settings)
        OperationKind.ASH_DIAGNOSTICS -> stringResource(R.string.activity_kind_ash_diagnostics)
        null -> kind
    }

private fun OperationHistoryEntity.icon(): Int =
    when (runCatching { OperationKind.valueOf(kind) }.getOrNull()) {
        OperationKind.DOWNLOAD -> R.drawable.cloud_download
        OperationKind.INSTALL -> R.drawable.package_import
        OperationKind.UPDATE -> R.drawable.refresh
        OperationKind.ENABLE -> R.drawable.player_play
        OperationKind.DISABLE -> R.drawable.disabled
        OperationKind.REMOVE -> R.drawable.trash
        OperationKind.RESTORE -> R.drawable.reload
        OperationKind.MODULE_ACTION -> R.drawable.terminal_2_outlined
        OperationKind.ROLLBACK -> R.drawable.rotate
        OperationKind.CHECK_UPDATES -> R.drawable.refresh
        OperationKind.EXPORT_LOG -> R.drawable.logs
        OperationKind.PREPARE_INSTALL -> R.drawable.package_import
        OperationKind.ASH_RESCUE -> R.drawable.shield_bolt
        OperationKind.ASH_RESTORATION -> R.drawable.reload
        OperationKind.ASH_SETTINGS -> R.drawable.settings
        OperationKind.ASH_DIAGNOSTICS -> R.drawable.logs
        null -> R.drawable.logs
    }


private val OperationHistoryEntity.canOfferRetry: Boolean
    get() =
        canRetry &&
            (
                !sourceUrl.isNullOrBlank() ||
                    !sourceUri.isNullOrBlank() ||
                    !destinationPath.isNullOrBlank()
            )

private fun OperationHistoryEntity.displayTitle(): String {
    moduleName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    val candidate =
        title.takeIf { it.isNotBlank() }
            ?: destinationPath
            ?: sourceUri
            ?: moduleId
            ?: return "MMRL operation"

    val decoded = runCatching { Uri.decode(candidate) }.getOrDefault(candidate)
    val leaf = decoded.substringBefore('?').substringAfterLast('/')
    return leaf
        .removeSuffix(".zip")
        .trim()
        .trimStart('.', '_')
        .ifBlank { candidate }
}

@Composable
private fun OperationHistoryEntity.displaySummary(): String =
    humanizeActivityMessage(summary)

@Composable
private fun humanizeActivityMessage(message: String): String {
    val normalized = message.lowercase()
    return when {
        "archive does not exist" in normalized ||
            "no such file" in normalized ->
            stringResource(R.string.activity_file_missing)

        "unable to resolve the selected file" in normalized ||
            "could not be opened" in normalized ->
            stringResource(R.string.activity_file_unreadable)

        "not a zip" in normalized ||
            "invalid archive" in normalized ||
            "zip exception" in normalized ->
            stringResource(R.string.activity_archive_invalid)

        else -> message
    }
}

private fun isSameDay(
    first: Long,
    second: Long,
): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun dayLabel(timestamp: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    if (isSameDay(target.timeInMillis, today.timeInMillis)) return stringResource(R.string.activity_today)
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (isSameDay(target.timeInMillis, today.timeInMillis)) return stringResource(R.string.activity_yesterday)
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}

private fun formatTime(value: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(value))

private fun formatDateTime(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))

private fun copyLog(
    context: Context,
    entry: OperationHistoryEntity,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(entry.title, entry.technicalLog))
}

private fun shareLog(
    context: Context,
    entry: OperationHistoryEntity,
) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MMRL activity log — ${entry.title}")
                putExtra(Intent.EXTRA_TEXT, entry.technicalLog)
            },
            null,
        ),
    )
}
