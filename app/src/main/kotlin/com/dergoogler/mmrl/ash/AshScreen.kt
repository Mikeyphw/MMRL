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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ext.none
import com.dergoogler.mmrl.ash.model.AshInstallMode
import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshModuleLifecycleState
import com.dergoogler.mmrl.ash.model.AshRecoveryOverview
import com.dergoogler.mmrl.ash.model.AshRecoverySession
import com.dergoogler.mmrl.ash.model.AshRecoverySessionKind
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.QuarantineItem
import com.dergoogler.mmrl.ash.model.isStale
import com.dergoogler.mmrl.ash.model.recoveryOverview
import com.dergoogler.mmrl.ash.model.recoverySessions
import com.dergoogler.mmrl.ui.activity.terminal.install.InstallActivity
import com.dergoogler.mmrl.ui.component.LocalScreenProvider
import com.dergoogler.mmrl.ui.component.scaffold.Scaffold
import com.dergoogler.mmrl.ui.component.toolbar.BlurToolbar
import com.dergoogler.mmrl.ui.component.toolbar.ToolbarTitle
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.dergoogler.mmrl.ui.providable.LocalMainScreenInnerPaddings
import com.dergoogler.mmrl.ui.providable.LocalSnackbarHost
import com.dergoogler.mmrl.ui.providable.mainContentBottomPadding
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ActivityScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BootProtectionScreenDestination
import java.util.Locale

private enum class RecoverySection(val label: String) {
    Overview("Overview"),
    Guidance("Guidance"),
    Quarantine("Quarantine"),
    Sessions("Sessions"),
    Diagnostics("Diagnostics"),
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
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        var section by remember { mutableStateOf(RecoverySection.Overview) }
        var confirmation by remember { mutableStateOf<RecoveryConfirmation?>(null) }
        var selectedSession by remember { mutableStateOf<AshRecoverySession?>(null) }

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
            RecoverySessionDialog(session = session, onDismiss = { selectedSession = null })
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
                        IconButton(onClick = viewModel::refreshAll, enabled = !state.loading) {
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
                if (state.loading) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = innerPadding.calculateTopPadding()),
                    )
                } else {
                    Spacer(Modifier.height(innerPadding.calculateTopPadding()))
                }

                RecoverySectionPicker(
                    selected = section,
                    quarantineCount = state.quarantine.size,
                    sessionCount = state.snapshotSessions.size,
                    onSelected = { section = it },
                )

                LifecycleBanner(
                    state = state,
                    onInstall = { viewModel.prepareModuleInstall(AshInstallMode.Install) },
                    onUpdate = { viewModel.prepareModuleInstall(AshInstallMode.Update) },
                    onReinstall = { viewModel.prepareModuleInstall(AshInstallMode.Reinstall) },
                )

                when (section) {
                    RecoverySection.Overview -> RecoveryOverviewContent(
                        state = state,
                        onShowQuarantine = { section = RecoverySection.Quarantine },
                        onShowSessions = { section = RecoverySection.Sessions },
                        onCompleteTrial = { confirmation = RecoveryConfirmation.CompleteTrial },
                        onRollbackTrial = { confirmation = RecoveryConfirmation.RollbackTrial },
                        onSessionClick = { selectedSession = it },
                        bottomPadding = bottomPadding,
                    )

                    RecoverySection.Guidance -> GuidedRecoveryContent(
                        state = state,
                        onExecutePlan = viewModel::executeRecoveryPlan,
                        onMarkSuspect = { folder -> viewModel.setTrust(folder, "suspect") },
                        onCompleteTrial = viewModel::completeTrial,
                        onRollbackTrial = viewModel::rollbackTrial,
                        onOutcome = viewModel::recordGuidanceOutcome,
                        bottomPadding = bottomPadding,
                    )

                    RecoverySection.Quarantine -> QuarantineContent(
                        items = state.quarantine,
                        readOnly = state.readOnly,
                        loading = state.loading,
                        onRestoreOne = viewModel::restoreOne,
                        onRestoreHalf = {
                            section = RecoverySection.Guidance
                            viewModel.showMessage("Review the balanced guarded plan before restoring a batch")
                        },
                        onRestoreAll = {
                            section = RecoverySection.Guidance
                            viewModel.showMessage("Review the rapid guarded plan and type its confirmation phrase")
                        },
                        bottomPadding = bottomPadding,
                    )

                    RecoverySection.Sessions -> RecoverySessionsContent(
                        sessions = state.snapshotSessions,
                        onSessionClick = { selectedSession = it },
                        onOpenActivity = { navigator.navigate(ActivityScreenDestination) },
                        bottomPadding = bottomPadding,
                    )

                    RecoverySection.Diagnostics -> RecoveryDiagnosticsContent(
                        state = state,
                        onExport = viewModel::exportDiagnostics,
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
    get() = com.dergoogler.mmrl.ash.model.AshSnapshot(
        activity = activity,
        dashboard = dashboard,
    ).recoverySessions()

@Composable
private fun RecoverySectionPicker(
    selected: RecoverySection,
    quarantineCount: Int,
    sessionCount: Int,
    onSelected: (RecoverySection) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecoverySection.entries.forEach { section ->
            val suffix = when (section) {
                RecoverySection.Quarantine -> " · $quarantineCount"
                RecoverySection.Sessions -> " · $sessionCount"
                else -> ""
            }
            FilterChip(
                selected = selected == section,
                onClick = { onSelected(section) },
                label = { Text(section.label + suffix) },
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
    val action: Pair<String, () -> Unit>? = when (lifecycle.state) {
        AshModuleLifecycleState.Missing -> "Install bundled module" to onInstall
        AshModuleLifecycleState.Outdated -> "Update module" to onUpdate
        AshModuleLifecycleState.Broken,
        AshModuleLifecycleState.Incompatible,
        AshModuleLifecycleState.Disabled,
        -> "Reinstall module" to onReinstall
        else -> null
    }
    if (state.connection == ConnectionState.Ready && !state.readOnly && action == null) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (state.readOnly) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(connectionTitle(state), fontWeight = FontWeight.SemiBold)
            Text(
                state.lifecycle.compatibilityMessage.ifBlank {
                    state.message ?: "Live recovery controls are unavailable. Cached information remains read only."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.let { (label, callback) ->
                FilledTonalButton(onClick = callback, enabled = !state.loading) { Text(label) }
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
        snapshot = com.dergoogler.mmrl.ash.model.AshSnapshot(
            dashboard = state.dashboard,
            activity = state.activity,
            quarantine = state.quarantine,
        ),
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
                    loading = state.loading,
                    onComplete = onCompleteTrial,
                    onRollback = onRollbackTrial,
                )
            }
        }
        if (overview.quarantineCount > 0) {
            item {
                RecoveryActionCard(
                    title = "Quarantine requires review",
                    description = "${overview.quarantineCount} module(s) are isolated and disabled by AshReXcue.",
                    actionLabel = "Review quarantine",
                    onAction = onShowQuarantine,
                )
            }
        }
        item {
            RecoveryMetrics(overview)
        }
        item {
            SectionHeader("Recent rescue sessions", "Open the unified Activity screen for the complete history.")
        }
        if (overview.recentSessions.isEmpty()) {
            item { EmptyRecoveryCard("No rescue or restoration sessions have been recorded.") }
        } else {
            items(overview.recentSessions, key = { "overview:${it.kind}:${it.id}:${it.timestamp}" }) { session ->
                RecoverySessionRow(session = session, onClick = { onSessionClick(session) })
            }
            item {
                OutlinedButton(onClick = onShowSessions, modifier = Modifier.fillMaxWidth()) {
                    Text("Show all recovery sessions")
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
        overview.restorationTrialActive -> "Restoration trial in progress"
        active -> "Rescue incident ${overview.activeIncidentId}"
        overview.quarantineCount > 0 -> "Rescue completed with quarantine"
        else -> "No active recovery incident"
    }
    val description = when {
        overview.restorationTrialActive -> "${overview.restorationTrialCount} restored module(s) are being tested for boot stability."
        active -> overview.activeIncidentReason.ifBlank { "AshReXcue is handling a failed-boot incident." }
        overview.quarantineCount > 0 -> "Review quarantined modules before restoring them in controlled batches."
        else -> "Boot protection is observing the device and no intervention is currently required."
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active || overview.restorationTrialActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(if (readOnly) "Read only" else overview.bootState.title()) })
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (overview.failureThreshold > 0) {
                LinearProgressIndicator(
                    progress = { (overview.failedBoots.toFloat() / overview.failureThreshold).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Failed boots ${overview.failedBoots}/${overview.failureThreshold}",
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
    loading: Boolean,
    onComplete: () -> Unit,
    onRollback: () -> Unit,
) {
    RecoveryCard("Restoration trial") {
        Text("${overview.restorationTrialCount} module(s) are temporarily enabled for validation.")
        Text(
            "Accept the trial only after the device has remained stable. Rollback re-quarantines the tested batch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onComplete, enabled = !readOnly && !loading, modifier = Modifier.weight(1f)) {
                Text("Complete trial")
            }
            OutlinedButton(onClick = onRollback, enabled = !readOnly && !loading, modifier = Modifier.weight(1f)) {
                Text("Rollback")
            }
        }
    }
}

@Composable
private fun RecoveryMetrics(overview: AshRecoveryOverview) {
    RecoveryCard("Protection state") {
        MetricRow("Boot state", overview.bootState.title())
        MetricRow("Rescue stage", overview.rescueStage.title())
        MetricRow("Next escalation", overview.nextRescue)
        MetricRow("Quarantined", overview.quarantineCount.toString())
        overview.bootReason.takeIf(String::isNotBlank)?.let { MetricRow("Boot reason", it) }
    }
}

@Composable
private fun QuarantineContent(
    items: List<QuarantineItem>,
    readOnly: Boolean,
    loading: Boolean,
    onRestoreOne: (String) -> Unit,
    onRestoreHalf: () -> Unit,
    onRestoreAll: () -> Unit,
    bottomPadding: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            RecoveryCard("Controlled restoration") {
                Text("Restore one module for a targeted trial, or restore a batch to perform a faster binary search.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRestoreHalf, enabled = items.size > 1 && !readOnly && !loading, modifier = Modifier.weight(1f)) {
                        Text("Restore half")
                    }
                    OutlinedButton(onClick = onRestoreAll, enabled = items.isNotEmpty() && !readOnly && !loading, modifier = Modifier.weight(1f)) {
                        Text("Restore all")
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item { EmptyRecoveryCard("Quarantine is empty. No isolated modules require restoration.") }
        } else {
            items(items, key = QuarantineItem::folder) { item ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name.ifBlank { item.id }, fontWeight = FontWeight.SemiBold)
                                Text(item.folder, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                            AssistChip(onClick = {}, label = { Text(if (item.isStale) "Stale" else item.trust.title()) })
                        }
                        Text(
                            if (item.isStale) "The quarantine record no longer matches an existing disabled module." else "Rescue ${item.rescueId.ifBlank { "unknown" }} · ${relativeTime(item.disabledAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { onRestoreOne(item.folder) },
                            enabled = !readOnly && !loading && !item.isStale,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Start restoration trial")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoverySessionsContent(
    sessions: List<AshRecoverySession>,
    onSessionClick: (AshRecoverySession) -> Unit,
    onOpenActivity: () -> Unit,
    bottomPadding: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            RecoveryActionCard(
                title = "Unified operation history",
                description = "Recovery Center highlights rescue sessions. MMRL Activity keeps the complete cross-feature audit trail.",
                actionLabel = "Open Activity",
                onAction = onOpenActivity,
            )
        }
        if (sessions.isEmpty()) {
            item { EmptyRecoveryCard("No recovery sessions have been recorded.") }
        } else {
            items(sessions, key = { "session:${it.kind}:${it.id}:${it.timestamp}" }) { session ->
                RecoverySessionRow(session = session, onClick = { onSessionClick(session) })
            }
        }
    }
}

@Composable
private fun RecoverySessionRow(session: AshRecoverySession, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(statusColor(session.status))
                Text(session.title.ifBlank { session.kind.label }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relativeTime(session.timestamp), style = MaterialTheme.typography.labelSmall)
            }
            Text(session.summary.ifBlank { session.status.title() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text(session.kind.label) })
                AssistChip(onClick = {}, label = { Text(session.status.title()) })
                if (session.active) AssistChip(onClick = {}, label = { Text("Active") })
            }
        }
    }
}

@Composable
private fun RecoveryDiagnosticsContent(
    state: AshUiState,
    onExport: () -> Unit,
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
            RecoveryCard("Connection and compatibility") {
                MetricRow("Snapshot", state.snapshotSource.name)
                MetricRow("Module", state.lifecycle.installation.version.ifBlank { "Not installed" })
                MetricRow("Bundled", state.lifecycle.bundled.version.ifBlank { "Unknown" })
                MetricRow("API", state.capabilities.apiVersion.toString())
                MetricRow("Transport", if (state.lifecycle.compatible) "Compatible typed service" else state.lifecycle.compatibilityMessage)
                MetricRow("Last refresh", relativeTime(state.lastSuccessfulAt))
            }
        }
        item {
            RecoveryCard("Sanitized diagnostic export") {
                Text("Creates a redacted archive containing module state, rescue manifests, restoration history, and recent logs.")
                Button(onClick = onExport, enabled = !state.readOnly && !state.loading, modifier = Modifier.fillMaxWidth()) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Export diagnostics")
                }
                state.lastOperation?.path?.let { path ->
                    HorizontalDivider()
                    Text(path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onCopyPath(path) }, modifier = Modifier.fillMaxWidth()) { Text("Copy archive path") }
                }
            }
        }
        item {
            RecoveryActionCard(
                title = "Boot protection settings",
                description = "Tune rescue thresholds and readiness signals without leaving MMRL.",
                actionLabel = "Open settings",
                onAction = onOpenSettings,
            )
        }
        item {
            RecoveryActionCard(
                title = "Recovery audit trail",
                description = "Review, copy, and share AshReXcue records alongside normal MMRL operations.",
                actionLabel = "Open Activity",
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
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun EmptyRecoveryCard(message: String) {
    RecoveryCard("All clear") { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, modifier = Modifier.weight(0.58f), fontWeight = FontWeight.Medium)
    }
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
        RecoveryConfirmation.CompleteTrial -> Triple("Complete restoration trial?", "The currently tested modules will remain enabled and leave quarantine.", "Complete")
        RecoveryConfirmation.RollbackTrial -> Triple("Rollback restoration trial?", "The tested modules will be disabled and returned to quarantine.", "Rollback")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecoverySessionDialog(session: AshRecoverySession, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.title.ifBlank { session.kind.label }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricRow("Status", session.status.title())
                MetricRow("Time", relativeTime(session.timestamp))
                if (session.summary.isNotBlank()) Text(session.summary)
                if (session.details.isNotBlank()) {
                    HorizontalDivider()
                    Text(session.details, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun connectionSubtitle(state: AshUiState): String = when {
    state.snapshotSource == AshSnapshotSource.Cache -> "Cached snapshot · read only"
    state.connection == ConnectionState.Ready && !state.readOnly -> "Live recovery controls"
    state.lifecycle.rebootRequired -> "Reboot required"
    else -> connectionTitle(state)
}

private fun connectionTitle(state: AshUiState): String = when (val connection = state.connection) {
    ConnectionState.Checking -> "Checking AshReXcue"
    ConnectionState.Ready -> if (state.readOnly) "Read-only recovery state" else "AshReXcue connected"
    is ConnectionState.Cached -> connection.message
    ConnectionState.RootDenied -> "Root access required"
    ConnectionState.ModuleMissing -> "AshReXcue is not installed"
    ConnectionState.ModuleDisabled -> "AshReXcue is disabled"
    is ConnectionState.ModuleOutdated -> "AshReXcue update required"
    is ConnectionState.ModuleIncompatible -> "AshReXcue is incompatible"
    is ConnectionState.RebootPending -> "AshReXcue change pending"
    is ConnectionState.Error -> connection.message
}

private val AshRecoverySessionKind.label: String
    get() = name.replaceFirstChar(Char::uppercaseChar)

private fun String.title(): String =
    replace('-', ' ').replace('_', ' ').replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
    }

private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "Unknown"
    val millis = if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
    return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}

private fun statusColor(status: String): Color = when (status.lowercase(Locale.ROOT)) {
    "success", "stable", "completed", "complete" -> Color(0xFF2E7D32)
    "failed", "error", "rolled-back", "rollback" -> Color(0xFFC62828)
    "testing", "active", "queued", "running" -> Color(0xFFEF6C00)
    else -> Color(0xFF607D8B)
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("AshReXcue diagnostic archive", text))
}
