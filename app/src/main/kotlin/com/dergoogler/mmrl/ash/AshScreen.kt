package com.dergoogler.mmrl.ash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ext.none
import com.dergoogler.mmrl.ash.model.AshGuidanceEngine
import com.dergoogler.mmrl.ash.model.AshInstallMode
import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshModuleLifecycleState
import com.dergoogler.mmrl.ash.model.AshRecoveryOverview
import com.dergoogler.mmrl.ash.model.AshRecoveryPlan
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanEngine
import com.dergoogler.mmrl.ash.model.AshSnapshot
import com.dergoogler.mmrl.ash.model.AshRecoverySession
import com.dergoogler.mmrl.ash.model.AshRecoverySessionKind
import com.dergoogler.mmrl.ash.model.AshReleaseCheckState
import com.dergoogler.mmrl.ash.model.AshReleaseGateStatus
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.QuarantineItem
import com.dergoogler.mmrl.ash.model.isStale
import com.dergoogler.mmrl.ash.model.recoveryOverview
import com.dergoogler.mmrl.ash.model.recoverySessions
import com.dergoogler.mmrl.ui.component.FlatClickableRow
import com.dergoogler.mmrl.ui.component.FlatSectionCard
import com.dergoogler.mmrl.ui.component.FlatSectionHeader
import com.dergoogler.mmrl.ui.activity.terminal.install.InstallActivity
import com.dergoogler.mmrl.ui.component.MetadataRow
import com.dergoogler.mmrl.ui.component.LocalScreenProvider
import com.dergoogler.mmrl.ui.component.scaffold.Scaffold
import com.dergoogler.mmrl.ui.component.toolbar.BlurToolbar
import com.dergoogler.mmrl.ui.component.toolbar.ToolbarTitle
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.dergoogler.mmrl.ui.providable.LocalMainScreenInnerPaddings
import com.dergoogler.mmrl.ui.providable.LocalSnackbarHost
import com.dergoogler.mmrl.ui.providable.LocalWindowSizeClass
import com.dergoogler.mmrl.ui.providable.mainContentBottomPadding
import com.dergoogler.mmrl.ui.theme.LocalSemanticColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ActivityScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BootProtectionScreenDestination
import java.util.Locale

private enum class RecoveryTask(val labelRes: Int) {
    Status(R.string.recovery_task_status),
    Restore(R.string.recovery_task_restore),
    Investigate(R.string.recovery_task_investigate),
    Trial(R.string.recovery_task_trial),
    History(R.string.recovery_task_history),
    Advanced(R.string.recovery_task_advanced),
}

private enum class RecoveryConfirmation {
    CompleteTrial,
    RollbackTrial,
}

@Destination<RootGraph>
@Composable
fun AshScreen(viewModel: AshViewModel = hiltViewModel()) =
    LocalScreenProvider {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val navigator = LocalDestinationsNavigator.current
        val snackbar = LocalSnackbarHost.current
        val context = LocalContext.current
        val bottomPadding = LocalMainScreenInnerPaddings.current.mainContentBottomPadding()
        val expandedLayout = LocalWindowSizeClass.current.widthSizeClass == WindowWidthSizeClass.Expanded
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        var task by rememberSaveable { mutableStateOf(RecoveryTask.Status) }
        var confirmation by remember { mutableStateOf<RecoveryConfirmation?>(null) }
        var selectedSession by remember { mutableStateOf<AshRecoverySession?>(null) }
        var pendingPlan by remember { mutableStateOf<AshRecoveryPlan?>(null) }
        val recoverySnapshot = remember(
            state.snapshotGeneratedAt,
            state.recoveryRevision,
            state.capabilities,
            state.dashboard,
            state.modules,
            state.quarantine,
            state.activity,
        ) { state.asRecoverySnapshot() }

        DisposableEffect(viewModel) {
            onDispose { viewModel.releaseRootSession() }
        }
        LaunchedEffect(viewModel, context) {
            viewModel.moduleInstalls.collect { prepared ->
                InstallActivity.start(context = context, uri = prepared.uri)
            }
        }
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                viewModel.clearMessage()
            }
        }

        confirmation?.let { action ->
            RecoveryConfirmationDialog(
                action = action,
                onDismiss = { confirmation = null },
                onConfirm = {
                    confirmation = null
                    when (action) {
                        RecoveryConfirmation.CompleteTrial -> viewModel.completeTrial()
                        RecoveryConfirmation.RollbackTrial -> viewModel.rollbackTrial()
                    }
                },
            )
        }
        selectedSession?.let { session ->
            if (!expandedLayout) {
                RecoverySessionDialog(session = session, onDismiss = { selectedSession = null })
            }
        }
        pendingPlan?.let { plan ->
            RecoveryPlanPreviewDialog(
                plan = plan,
                moduleNames = plan.affectedFolders.map { folder ->
                    state.modules.firstOrNull { it.folder == folder }?.name ?: folder
                },
                onDismiss = { pendingPlan = null },
                onConfirm = {
                    pendingPlan = null
                    viewModel.executeRecoveryPlan(plan)
                },
            )
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                BlurToolbar(
                    title = {
                        ToolbarTitle(
                            title = stringResource(R.string.recovery_center_title),
                            subtitle = connectionSubtitle(state),
                        )
                    },
                    actions = {
                        IconButton(onClick = viewModel::refreshAll, enabled = !state.refreshing) {
                            Icon(
                                painter = painterResource(R.drawable.refresh),
                                contentDescription = stringResource(R.string.recovery_center_refresh),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            contentWindowInsets = WindowInsets.none,
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.refreshing) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = innerPadding.calculateTopPadding()),
                    )
                } else {
                    Spacer(Modifier.height(innerPadding.calculateTopPadding()))
                }

                RecoveryTaskPicker(
                    selected = task,
                    quarantineCount = state.quarantine.size,
                    sessionCount = state.snapshotSessions.size,
                    trialActive = state.dashboard.restoreState.equals("testing", ignoreCase = true),
                    onSelected = { task = it },
                )

                LifecycleBanner(
                    state = state,
                    onInstall = { viewModel.prepareModuleInstall(AshInstallMode.Install) },
                    onUpdate = { viewModel.prepareModuleInstall(AshInstallMode.Update) },
                    onReinstall = { viewModel.prepareModuleInstall(AshInstallMode.Reinstall) },
                )

                when (task) {
                    RecoveryTask.Status -> RecoveryOverviewContent(
                        state = state,
                        onShowQuarantine = { task = RecoveryTask.Restore },
                        onShowSessions = { task = RecoveryTask.History },
                        onCompleteTrial = { confirmation = RecoveryConfirmation.CompleteTrial },
                        onRollbackTrial = { confirmation = RecoveryConfirmation.RollbackTrial },
                        onSessionClick = { selectedSession = it },
                        bottomPadding = bottomPadding,
                    )

                    RecoveryTask.Restore -> QuarantineContent(
                        snapshot = recoverySnapshot,
                        readOnly = state.readOnly,
                        anyPlanRunning = state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan),
                        isPlanRunning = { planId ->
                            state.isOperationRunning(AshOperationKind.ExecuteRecoveryPlan, planId)
                        },
                        onPreviewPlan = { pendingPlan = it },
                        bottomPadding = bottomPadding,
                    )

                    RecoveryTask.Investigate -> GuidedRecoveryContent(
                        state = state,
                        onExecutePlan = viewModel::executeRecoveryPlan,
                        onMarkSuspect = { folder -> viewModel.setTrust(folder, "suspect") },
                        onCompleteTrial = viewModel::completeTrial,
                        onRollbackTrial = viewModel::rollbackTrial,
                        onOutcome = viewModel::recordGuidanceOutcome,
                        bottomPadding = bottomPadding,
                        showPlanningTools = false,
                    )

                    RecoveryTask.Trial -> RecoveryTrialContent(
                        state = state,
                        onCompleteTrial = { confirmation = RecoveryConfirmation.CompleteTrial },
                        onRollbackTrial = { confirmation = RecoveryConfirmation.RollbackTrial },
                        onOpenRestore = { task = RecoveryTask.Restore },
                        onSessionClick = { selectedSession = it },
                        bottomPadding = bottomPadding,
                    )

                    RecoveryTask.History -> RecoverySessionsContent(
                        sessions = state.snapshotSessions,
                        selectedSession = selectedSession,
                        expandedLayout = expandedLayout,
                        onSessionClick = { selectedSession = it },
                        onOpenActivity = { navigator.navigate(ActivityScreenDestination) },
                        bottomPadding = bottomPadding,
                    )

                    RecoveryTask.Advanced -> RecoveryDiagnosticsContent(
                        state = state,
                        onExport = viewModel::exportDiagnostics,
                        onRepair = viewModel::repairState,
                        onRunReleaseGate = viewModel::refreshAll,
                        onCopyPath = { path -> copyText(context, path) },
                        onOpenActivity = { navigator.navigate(ActivityScreenDestination) },
                        onOpenSettings = { navigator.navigate(BootProtectionScreenDestination) },
                        bottomPadding = bottomPadding,
                    )
                }
            }
        }
    }

private val AshUiState.snapshotSessions: List<AshRecoverySession>
    get() = asRecoverySnapshot().recoverySessions()

private fun AshUiState.asRecoverySnapshot() = AshSnapshot(
    generatedAt = snapshotGeneratedAt,
    recoveryRevision = recoveryRevision,
    capabilities = capabilities,
    dashboard = dashboard,
    modules = modules,
    quarantine = quarantine,
    activity = activity,
)

@Composable
private fun RecoveryTaskPicker(
    selected: RecoveryTask,
    quarantineCount: Int,
    sessionCount: Int,
    trialActive: Boolean,
    onSelected: (RecoveryTask) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecoveryTask.entries.forEach { task ->
            val suffix = when (task) {
                RecoveryTask.Restore -> stringResource(R.string.recovery_task_suffix_count, quarantineCount)
                RecoveryTask.Trial -> if (trialActive) stringResource(R.string.recovery_task_suffix_active) else ""
                RecoveryTask.History -> stringResource(R.string.recovery_task_suffix_count, sessionCount)
                else -> ""
            }
            val label = stringResource(task.labelRes) + suffix
            FilterChip(
                selected = selected == task,
                onClick = { onSelected(task) },
                modifier = Modifier.semantics {
                    role = Role.Tab
                    this.selected = selected == task
                },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun LifecycleBanner(
    state: AshUiState,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onReinstall: () -> Unit,
) {
    val lifecycle = state.lifecycle
    val action: Triple<String, AshInstallMode, () -> Unit>? = when (lifecycle.state) {
        AshModuleLifecycleState.Missing -> Triple(stringResource(R.string.recovery_install_bundled_module), AshInstallMode.Install, onInstall)
        AshModuleLifecycleState.Outdated -> Triple(stringResource(R.string.recovery_update_module), AshInstallMode.Update, onUpdate)
        AshModuleLifecycleState.Broken,
        AshModuleLifecycleState.Incompatible,
        AshModuleLifecycleState.Disabled,
        -> Triple(stringResource(R.string.recovery_reinstall_module), AshInstallMode.Reinstall, onReinstall)
        else -> null
    }
    if (state.connection == ConnectionState.Ready && !state.readOnly && action == null) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (state.readOnly) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    connectionTitle(state),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.lifecycle.rebootRequired) {
                    StatusPill(stringResource(R.string.recovery_reboot_required), MaterialTheme.colorScheme.tertiary)
                }
            }
            Text(
                state.lifecycle.compatibilityMessage.ifBlank {
                    state.message ?: stringResource(R.string.recovery_live_controls_unavailable)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.let { (label, mode, callback) ->
                val running = state.isOperationRunning(AshOperationKind.PrepareModuleInstall, mode.name)
                FilledTonalButton(onClick = callback, enabled = !running) {
                    RecoveryActionLabel(running, label)
                }
            }
        }
    }
}

@Composable
private fun RecoveryOverviewContent(
    state: AshUiState,
    onShowQuarantine: () -> Unit,
    onShowSessions: () -> Unit,
    onCompleteTrial: () -> Unit,
    onRollbackTrial: () -> Unit,
    onSessionClick: (AshRecoverySession) -> Unit,
    bottomPadding: Dp,
) {
    val managerState = AshManagerState(
        rootAvailable = state.connection != ConnectionState.RootDenied,
        lifecycle = state.lifecycle,
        snapshot = state.asRecoverySnapshot(),
        source = state.snapshotSource,
        readOnly = state.readOnly,
        lastSuccessfulAt = state.lastSuccessfulAt,
    )
    val overview = managerState.recoveryOverview()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { IncidentHero(overview = overview, readOnly = state.readOnly) }
        if (overview.restorationTrialActive) {
            item {
                RestorationTrialCard(
                    overview = overview,
                    readOnly = state.readOnly,
                    completing = state.isOperationRunning(AshOperationKind.CompleteTrial),
                    rollingBack = state.isOperationRunning(AshOperationKind.RollbackTrial),
                    onComplete = onCompleteTrial,
                    onRollback = onRollbackTrial,
                )
            }
        }
        if (overview.quarantineCount > 0) {
            item {
                RecoveryActionCard(
                    title = stringResource(R.string.recovery_quarantine_review_title),
                    description = stringResource(R.string.recovery_quarantine_review_desc, overview.quarantineCount),
                    actionLabel = stringResource(R.string.recovery_review_quarantine),
                    onAction = onShowQuarantine,
                )
            }
        }
        item {
            RecoveryMetrics(overview)
        }
        item {
            SectionHeader(stringResource(R.string.recovery_recent_sessions), stringResource(R.string.recovery_recent_sessions_desc))
        }
        if (overview.recentSessions.isEmpty()) {
            item { EmptyRecoveryCard(stringResource(R.string.recovery_no_recent_sessions)) }
        } else {
            items(overview.recentSessions, key = { "overview:${it.kind}:${it.id}:${it.timestamp}" }) { session ->
                RecoverySessionRow(session = session, onClick = { onSessionClick(session) })
            }
            item {
                OutlinedButton(onClick = onShowSessions, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recovery_show_all_sessions))
                }
            }
        }
    }
}

@Composable
private fun IncidentHero(overview: AshRecoveryOverview, readOnly: Boolean) {
    val active = overview.activeIncidentId.isNotBlank() &&
        !overview.activeIncidentStatus.equals("stable", ignoreCase = true)
    val title = when {
        overview.restorationTrialActive -> stringResource(R.string.recovery_trial_in_progress)
        active -> stringResource(R.string.recovery_rescue_incident, overview.activeIncidentId)
        overview.quarantineCount > 0 -> stringResource(R.string.recovery_rescue_completed_quarantine)
        else -> stringResource(R.string.recovery_no_active_incident)
    }
    val description = when {
        overview.restorationTrialActive -> stringResource(R.string.recovery_trial_in_progress_desc, overview.restorationTrialCount)
        active -> overview.activeIncidentReason.ifBlank { stringResource(R.string.recovery_failed_boot_incident) }
        overview.quarantineCount > 0 -> stringResource(R.string.recovery_quarantine_restore_desc)
        else -> stringResource(R.string.recovery_no_intervention_desc)
    }
    Surface(
        color = if (active || overview.restorationTrialActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, modifier = Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                StatusPill(if (readOnly) stringResource(R.string.recovery_read_only) else overview.bootState.title())
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (overview.failureThreshold > 0) {
                LinearProgressIndicator(
                    progress = { (overview.failedBoots.toFloat() / overview.failureThreshold).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.recovery_failed_boots_count, overview.failedBoots, overview.failureThreshold),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun RestorationTrialCard(
    overview: AshRecoveryOverview,
    readOnly: Boolean,
    completing: Boolean,
    rollingBack: Boolean,
    onComplete: () -> Unit,
    onRollback: () -> Unit,
) {
    RecoveryCard(stringResource(R.string.recovery_restoration_trial)) {
        Text(stringResource(R.string.recovery_restoration_trial_count, overview.restorationTrialCount))
        Text(
            stringResource(R.string.recovery_restoration_trial_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onComplete, enabled = !readOnly && !completing && !rollingBack, modifier = Modifier.weight(1f)) {
                RecoveryActionLabel(completing, stringResource(R.string.recovery_complete_trial))
            }
            OutlinedButton(onClick = onRollback, enabled = !readOnly && !rollingBack && !completing, modifier = Modifier.weight(1f)) {
                RecoveryActionLabel(rollingBack, stringResource(R.string.recovery_rollback))
            }
        }
    }
}

@Composable
private fun RecoveryMetrics(overview: AshRecoveryOverview) {
    RecoveryCard(stringResource(R.string.recovery_protection_state)) {
        MetricRow(stringResource(R.string.recovery_boot_state), overview.bootState.title())
        MetricRow(stringResource(R.string.recovery_rescue_stage), overview.rescueStage.title())
        MetricRow(stringResource(R.string.recovery_next_escalation), overview.nextRescue)
        MetricRow(stringResource(R.string.recovery_quarantined), overview.quarantineCount.toString())
        overview.bootReason.takeIf(String::isNotBlank)?.let { MetricRow(stringResource(R.string.recovery_boot_reason), it) }
    }
}

@Composable
private fun QuarantineContent(
    snapshot: AshSnapshot,
    readOnly: Boolean,
    anyPlanRunning: Boolean,
    isPlanRunning: (String) -> Boolean,
    onPreviewPlan: (AshRecoveryPlan) -> Unit,
    bottomPadding: Dp,
) {
    val guidance = remember(snapshot) { AshGuidanceEngine.build(snapshot) }
    val presetPlans = remember(snapshot, guidance) {
        AshRecoveryPlanEngine.presets(snapshot, guidance)
    }
    val items = snapshot.quarantine

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            RecoveryCard(stringResource(R.string.recovery_choose_guarded_restoration)) {
                Text(
                    stringResource(R.string.recovery_guarded_restoration_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                presetPlans.forEach { plan ->
                    FilledTonalButton(
                        onClick = { onPreviewPlan(plan) },
                        enabled = !readOnly && !anyPlanRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RecoveryActionLabel(isPlanRunning(plan.id), stringResource(R.string.recovery_review_plan, plan.title.lowercase(Locale.ROOT)))
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item { EmptyRecoveryCard(stringResource(R.string.recovery_quarantine_empty)) }
        } else {
            item {
                SectionHeader(
                    title = stringResource(R.string.recovery_quarantined_modules),
                    subtitle = stringResource(R.string.recovery_quarantined_modules_desc),
                )
            }
            items(items, key = QuarantineItem::folder) { item ->
                val plan = remember(snapshot, item.folder) {
                    AshRecoveryPlanEngine.custom(snapshot, listOf(item.folder))
                }
                FlatSectionCard(title = item.name.ifBlank { item.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            item.folder,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        StatusPill(
                            text = if (item.isStale) stringResource(R.string.recovery_stale) else trustLabelText(item.trust),
                            color = if (item.isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        if (item.isStale) {
                            stringResource(R.string.recovery_stale_quarantine_desc)
                        } else {
                            stringResource(R.string.recovery_rescue_relative, item.rescueId.ifBlank { stringResource(R.string.unknown) }, relativeTimeText(item.disabledAt))
                        }
                        ,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { onPreviewPlan(plan) },
                        enabled = !readOnly && !anyPlanRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RecoveryActionLabel(isPlanRunning(plan.id), stringResource(R.string.recovery_review_one_module_trial))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryTrialContent(
    state: AshUiState,
    onCompleteTrial: () -> Unit,
    onRollbackTrial: () -> Unit,
    onOpenRestore: () -> Unit,
    onSessionClick: (AshRecoverySession) -> Unit,
    bottomPadding: Dp,
) {
    val managerState = AshManagerState(
        rootAvailable = state.connection != ConnectionState.RootDenied,
        lifecycle = state.lifecycle,
        snapshot = state.asRecoverySnapshot(),
        source = state.snapshotSource,
        readOnly = state.readOnly,
        lastSuccessfulAt = state.lastSuccessfulAt,
    )
    val overview = managerState.recoveryOverview()
    val trialSessions = state.snapshotSessions.filter { it.kind == AshRecoverySessionKind.Restoration }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (overview.restorationTrialActive) {
            item {
                RestorationTrialCard(
                    overview = overview,
                    readOnly = state.readOnly,
                    completing = state.isOperationRunning(AshOperationKind.CompleteTrial),
                    rollingBack = state.isOperationRunning(AshOperationKind.RollbackTrial),
                    onComplete = onCompleteTrial,
                    onRollback = onRollbackTrial,
                )
            }
        } else {
            item {
                RecoveryActionCard(
                    title = stringResource(R.string.recovery_no_trial_title),
                    description = stringResource(R.string.recovery_no_trial_desc),
                    actionLabel = stringResource(R.string.recovery_task_restore),
                    onAction = onOpenRestore,
                )
            }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.recovery_recent_trial_outcomes),
                subtitle = stringResource(R.string.recovery_recent_trial_outcomes_desc),
            )
        }
        if (trialSessions.isEmpty()) {
            item { EmptyRecoveryCard(stringResource(R.string.recovery_no_trials)) }
        } else {
            items(trialSessions, key = { "trial:${it.id}:${it.timestamp}" }) { session ->
                RecoverySessionRow(session = session, onClick = { onSessionClick(session) })
            }
        }
    }
}

@Composable
private fun RecoverySessionsContent(
    sessions: List<AshRecoverySession>,
    selectedSession: AshRecoverySession?,
    expandedLayout: Boolean,
    onSessionClick: (AshRecoverySession) -> Unit,
    onOpenActivity: () -> Unit,
    bottomPadding: Dp,
) {
    if (expandedLayout) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RecoverySessionsList(
                sessions = sessions,
                onSessionClick = onSessionClick,
                onOpenActivity = onOpenActivity,
                bottomPadding = bottomPadding,
                modifier = Modifier.weight(0.44f),
            )
            Surface(
                modifier = Modifier.weight(0.56f).fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                selectedSession?.let {
                    Column(Modifier.padding(18.dp)) {
                        RecoverySessionDetailsContent(it)
                    }
                } ?: EmptyRecoveryCard(stringResource(R.string.recovery_select_session))
            }
        }
        return
    }

    RecoverySessionsList(
        sessions = sessions,
        onSessionClick = onSessionClick,
        onOpenActivity = onOpenActivity,
        bottomPadding = bottomPadding,
    )
}

@Composable
private fun RecoverySessionsList(
    sessions: List<AshRecoverySession>,
    onSessionClick: (AshRecoverySession) -> Unit,
    onOpenActivity: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            RecoveryActionCard(
                title = stringResource(R.string.recovery_unified_history),
                description = stringResource(R.string.recovery_unified_history_desc),
                actionLabel = stringResource(R.string.recovery_open_activity),
                onAction = onOpenActivity,
            )
        }
        if (sessions.isEmpty()) {
            item { EmptyRecoveryCard(stringResource(R.string.recovery_no_sessions)) }
        } else {
            items(sessions, key = { "session:${it.kind}:${it.id}:${it.timestamp}" }) { session ->
                RecoverySessionRow(session = session, onClick = { onSessionClick(session) })
            }
        }
    }
}

@Composable
private fun RecoverySessionRow(session: AshRecoverySession, onClick: () -> Unit) {
    FlatClickableRow(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(statusColor(session.status))
                Text(session.title.ifBlank { session.kind.labelText() }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relativeTimeText(session.timestamp), style = MaterialTheme.typography.labelSmall)
            }
            Text(session.summary.ifBlank { statusLabelText(session.status) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(session.kind.labelText())
                StatusPill(statusLabelText(session.status), statusColor(session.status))
                if (session.active) StatusPill(stringResource(R.string.recovery_active), MaterialTheme.colorScheme.tertiary)
            }
    }
}

@Composable
private fun RecoveryDiagnosticsContent(
    state: AshUiState,
    onExport: () -> Unit,
    onRepair: () -> Unit,
    onRunReleaseGate: () -> Unit,
    onCopyPath: (String) -> Unit,
    onOpenActivity: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomPadding: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RecoveryCard(stringResource(R.string.recovery_connection_compatibility)) {
                MetricRow(stringResource(R.string.recovery_snapshot), state.snapshotSource.name)
                MetricRow(stringResource(R.string.recovery_module), state.lifecycle.installation.version.ifBlank { stringResource(R.string.recovery_not_installed) })
                MetricRow(stringResource(R.string.recovery_bundled), state.lifecycle.bundled.version.ifBlank { stringResource(R.string.unknown) })
                MetricRow(stringResource(R.string.recovery_api), state.capabilities.apiVersion.toString())
                MetricRow(stringResource(R.string.recovery_transport), if (state.lifecycle.compatible) stringResource(R.string.recovery_compatible_typed_service) else state.lifecycle.compatibilityMessage)
                MetricRow(stringResource(R.string.recovery_last_refresh), relativeTimeText(state.lastSuccessfulAt))
            }
        }
        item {
            RecoveryCard(stringResource(R.string.recovery_release_readiness)) {
                MetricRow(stringResource(R.string.activity_status), releaseGateStatusTitle(state.releaseGate.status))
                MetricRow(stringResource(R.string.recovery_passed), state.releaseGate.passedCount.toString())
                MetricRow(stringResource(R.string.recovery_warnings), state.releaseGate.warningCount.toString())
                MetricRow(stringResource(R.string.recovery_blockers), state.releaseGate.blockerCount.toString())
                Text(state.releaseGate.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val visibleChecks = state.releaseGate.checks
                    .filter { check -> check.state != AshReleaseCheckState.Pass }
                    .take(8)
                if (visibleChecks.isEmpty()) {
                    Text(
                        stringResource(R.string.recovery_release_checks_passed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    visibleChecks.forEach { check ->
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(check.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            StatusPill(check.state.name, releaseCheckColor(check.state))
                        }
                        Text(
                            check.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = onRunReleaseGate,
                    enabled = !state.refreshing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RecoveryActionLabel(state.refreshing, stringResource(R.string.recovery_run_final_release_gate))
                }
            }
        }
        item {
            RecoveryCard(stringResource(R.string.recovery_state_health)) {
                MetricRow(stringResource(R.string.activity_status), healthLevelLabelText(state.health.level.name))
                MetricRow(stringResource(R.string.recovery_state_schema), state.health.module.schemaVersion.toString())
                MetricRow(stringResource(R.string.recovery_repairs), state.health.module.repairCount.toString())
                Text(state.health.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.health.issues.take(6).forEach { issue ->
                    HorizontalDivider()
                    Text(issue.title, fontWeight = FontWeight.SemiBold)
                    Text(issue.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.health.repairRecommended) {
                    Button(
                        onClick = onRepair,
                        enabled = !state.readOnly &&
                            !state.isOperationRunning(AshOperationKind.RepairState) &&
                            state.capabilities.supports("state-repair"),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RecoveryActionLabel(
                            state.isOperationRunning(AshOperationKind.RepairState),
                            stringResource(R.string.recovery_repair_state),
                        )
                    }
                }
            }
        }
        item {
            RecoveryCard(stringResource(R.string.recovery_sanitized_diagnostic_export)) {
                Text(stringResource(R.string.recovery_sanitized_diagnostic_export_desc))
                val exporting = state.isOperationRunning(AshOperationKind.ExportDiagnostics)
                Button(onClick = onExport, enabled = !state.readOnly && !exporting, modifier = Modifier.fillMaxWidth()) {
                    RecoveryActionLabel(exporting, stringResource(R.string.recovery_export_diagnostics))
                }
                state.lastOperation?.path?.let { path ->
                    HorizontalDivider()
                    Text(path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onCopyPath(path) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.recovery_copy_archive_path)) }
                }
            }
        }
        item {
            RecoveryActionCard(
                title = stringResource(R.string.recovery_boot_protection_settings),
                description = stringResource(R.string.recovery_boot_protection_settings_desc),
                actionLabel = stringResource(R.string.recovery_open_settings),
                onAction = onOpenSettings,
            )
        }
        item {
            RecoveryActionCard(
                title = stringResource(R.string.recovery_audit_trail),
                description = stringResource(R.string.recovery_audit_trail_desc),
                actionLabel = stringResource(R.string.recovery_open_activity),
                onAction = onOpenActivity,
            )
        }
    }
}

@Composable
private fun RecoveryActionCard(title: String, description: String, actionLabel: String, onAction: () -> Unit) {
    RecoveryCard(title) {
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
    }
}

@Composable
private fun RecoveryCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    FlatSectionCard(title = title, content = content)
}

@Composable
private fun EmptyRecoveryCard(message: String) {
    RecoveryCard(stringResource(R.string.recovery_all_clear)) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    FlatSectionHeader(title = title, subtitle = subtitle)
}

@Composable
private fun MetricRow(label: String, value: String) {
    MetadataRow(label = label, value = value)
}

@Composable
private fun StatusDot(color: Color) {
    Surface(modifier = Modifier.size(9.dp), shape = RoundedCornerShape(50), color = color) {}
}

@Composable
private fun RecoveryConfirmationDialog(
    action: RecoveryConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (title, description, confirm) = when (action) {
        RecoveryConfirmation.CompleteTrial -> Triple(stringResource(R.string.recovery_complete_trial_title), stringResource(R.string.recovery_complete_trial_desc), stringResource(R.string.recovery_complete))
        RecoveryConfirmation.RollbackTrial -> Triple(stringResource(R.string.recovery_rollback_trial_title), stringResource(R.string.recovery_rollback_trial_desc), stringResource(R.string.recovery_rollback))
    }
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { ScrollableRecoveryDialogContent { Text(description) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RecoverySessionDialog(session: AshRecoverySession, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        title = { Text(session.title.ifBlank { session.kind.labelText() }) },
        text = { ScrollableRecoveryDialogContent { RecoverySessionDetailsContent(session) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun RecoverySessionDetailsContent(session: AshRecoverySession) {
    MetadataRow(stringResource(R.string.activity_status), statusLabelText(session.status))
    MetadataRow(stringResource(R.string.recovery_time), relativeTimeText(session.timestamp))
    if (session.summary.isNotBlank()) Text(session.summary)
    if (session.details.isNotBlank()) {
        HorizontalDivider()
        Text(session.details, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun releaseCheckColor(state: AshReleaseCheckState): Color = when (state) {
    AshReleaseCheckState.Pass -> MaterialTheme.colorScheme.primary
    AshReleaseCheckState.Warning -> MaterialTheme.colorScheme.tertiary
    AshReleaseCheckState.Blocker -> MaterialTheme.colorScheme.error
}

@Composable
private fun releaseGateStatusTitle(status: AshReleaseGateStatus): String = when (status) {
    AshReleaseGateStatus.Ready -> stringResource(R.string.recovery_ready)
    AshReleaseGateStatus.ReadyWithWarnings -> stringResource(R.string.recovery_ready_with_warnings)
    AshReleaseGateStatus.Blocked -> stringResource(R.string.recovery_blocked)
}

@Composable
private fun connectionSubtitle(state: AshUiState): String = when {
    state.snapshotSource == AshSnapshotSource.Cache -> stringResource(R.string.recovery_cached_snapshot_read_only)
    state.connection == ConnectionState.Ready && !state.readOnly -> stringResource(R.string.recovery_live_controls)
    state.lifecycle.rebootRequired -> stringResource(R.string.recovery_reboot_required)
    else -> connectionTitle(state)
}

@Composable
private fun connectionTitle(state: AshUiState): String = when (val connection = state.connection) {
    ConnectionState.Checking -> stringResource(R.string.recovery_checking_ashrexcue)
    ConnectionState.Ready -> if (state.readOnly) stringResource(R.string.recovery_read_only_state) else stringResource(R.string.recovery_ashrexcue_connected)
    is ConnectionState.Cached -> connection.message
    ConnectionState.RootDenied -> stringResource(R.string.recovery_root_required)
    ConnectionState.ModuleMissing -> stringResource(R.string.recovery_module_missing)
    ConnectionState.ModuleDisabled -> stringResource(R.string.recovery_module_disabled)
    is ConnectionState.ModuleOutdated -> stringResource(R.string.recovery_module_outdated)
    is ConnectionState.ModuleIncompatible -> stringResource(R.string.recovery_module_incompatible)
    is ConnectionState.RebootPending -> stringResource(R.string.recovery_change_pending)
    is ConnectionState.Error -> connection.message
}

@Composable
private fun AshRecoverySessionKind.labelText(): String = when (this) {
    AshRecoverySessionKind.Rescue -> stringResource(R.string.recovery_session_kind_rescue)
    AshRecoverySessionKind.Restoration -> stringResource(R.string.recovery_session_kind_restoration)
    AshRecoverySessionKind.Diagnostics -> stringResource(R.string.recovery_session_kind_diagnostics)
    AshRecoverySessionKind.Configuration -> stringResource(R.string.recovery_session_kind_configuration)
    AshRecoverySessionKind.Other -> stringResource(R.string.recovery_session_kind_other)
}

@Composable
private fun trustLabelText(trust: String): String = when (trust.lowercase(Locale.ROOT)) {
    "protected" -> stringResource(R.string.ash_filter_protected)
    "trusted" -> stringResource(R.string.ash_filter_trusted)
    "suspect" -> stringResource(R.string.ash_filter_suspect)
    "normal" -> stringResource(R.string.ash_filter_normal)
    else -> trust.title()
}

@Composable
private fun statusLabelText(status: String): String = when (status.lowercase(Locale.ROOT)) {
    "success", "completed", "complete" -> stringResource(R.string.activity_status_success)
    "stable" -> stringResource(R.string.guidance_stable)
    "failed", "error" -> stringResource(R.string.activity_status_failed)
    "rolled-back", "rollback" -> stringResource(R.string.recovery_rollback)
    "testing", "active" -> stringResource(R.string.recovery_active)
    "queued" -> stringResource(R.string.activity_status_queued)
    "running" -> stringResource(R.string.activity_status_running)
    "blocked" -> stringResource(R.string.recovery_blocked)
    else -> status.title()
}

@Composable
private fun healthLevelLabelText(level: String): String = when (level.lowercase(Locale.ROOT)) {
    "healthy", "ok", "success" -> stringResource(R.string.recovery_health_healthy)
    "warning", "warnings" -> stringResource(R.string.recovery_warnings)
    "critical", "error", "failed" -> stringResource(R.string.recovery_health_critical)
    else -> level.title()
}

private fun String.title(): String =
    replace('-', ' ').replace('_', ' ').replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
    }

@Composable
private fun relativeTimeText(timestamp: Long): String {
    if (timestamp <= 0) return stringResource(R.string.unknown)
    val millis = if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
    return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
private fun statusColor(status: String): Color {
    val semantic = LocalSemanticColors.current
    return when (status.lowercase(Locale.ROOT)) {
        "success", "stable", "completed", "complete" -> semantic.success
        "failed", "error", "rolled-back", "rollback" -> MaterialTheme.colorScheme.error
        "testing", "active", "queued", "running" -> semantic.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.recovery_diagnostic_archive_clip_label), text))
}
