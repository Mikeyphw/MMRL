package com.dergoogler.mmrl.ui.screens.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ext.none
import com.dergoogler.mmrl.installer.ArchiveInspector
import com.dergoogler.mmrl.model.local.BulkModule
import com.dergoogler.mmrl.service.ModuleService
import com.dergoogler.mmrl.ui.activity.terminal.install.InstallActivity
import com.dergoogler.mmrl.ui.component.BottomSheet
import com.dergoogler.mmrl.ui.component.LocalScreenProvider
import com.dergoogler.mmrl.ui.component.PageIndicator
import com.dergoogler.mmrl.ui.component.scaffold.Scaffold
import com.dergoogler.mmrl.ui.component.toolbar.BlurToolbar
import com.dergoogler.mmrl.ui.component.toolbar.ToolbarTitle
import com.dergoogler.mmrl.ui.providable.LocalBulkInstall
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.dergoogler.mmrl.ui.providable.LocalMainScreenInnerPaddings
import com.dergoogler.mmrl.ui.providable.LocalSnackbarHost
import com.dergoogler.mmrl.ui.screens.moduleView.items.InstallReviewBottomSheet
import com.dergoogler.mmrl.ui.screens.moduleView.items.InstallReviewPhase
import com.dergoogler.mmrl.ui.screens.moduleView.items.InstallReviewState
import com.dergoogler.mmrl.ui.theme.LocalSemanticColors
import com.dergoogler.mmrl.ui.theme.LocalMMRLSurfaces
import com.dergoogler.mmrl.viewmodel.BulkDownloadException
import com.dergoogler.mmrl.viewmodel.ModuleUpdateInfo
import com.dergoogler.mmrl.viewmodel.ModulesViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Destination<RootGraph>
@Composable
fun ModuleUpdatesScreen(viewModel: ModulesViewModel = hiltViewModel()) =
    LocalScreenProvider {
        val updates by viewModel.updates.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val bulkInstall = LocalBulkInstall.current
        val navigator = LocalDestinationsNavigator.current
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        val bottomPadding = LocalMainScreenInnerPaddings.current.calculateBottomPadding()
        val wide = LocalConfiguration.current.screenWidthDp >= 900
        val scope = rememberCoroutineScope()
        val snackbar = LocalSnackbarHost.current

        val ignoreUpdate: (ModuleUpdateInfo) -> Unit = { update ->
            viewModel.setUpdateIgnored(update.local.id.id, true)
            ModuleService.cancelUpdateNotification(context, update.local.id.id)
            scope.launch {
                val result = snackbar.showSnackbar(
                    message = context.getString(R.string.updates_ignored),
                    actionLabel = context.getString(R.string.undo),
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.setUpdateIgnored(update.local.id.id, false)
                }
            }
        }

        var selectedUpdate by remember { mutableStateOf<ModuleUpdateInfo?>(null) }
        var reviewUpdate by remember { mutableStateOf<ModuleUpdateInfo?>(null) }
        var reviewState by remember { mutableStateOf(InstallReviewState()) }
        var reviewAll by remember { mutableStateOf(false) }

        LaunchedEffect(updates, wide) {
            if (wide && (selectedUpdate == null || updates.none { it.local.id == selectedUpdate?.local?.id })) selectedUpdate = updates.firstOrNull()
        }

        reviewUpdate?.let { update ->
            InstallReviewBottomSheet(
                module = update.online,
                moduleName = update.local.name,
                version = update.version,
                installedVersion = update.local.version,
                repositoryName = update.repositoryName ?: stringResource(R.string.updates_module_source),
                compatible = update.compatible,
                requiresReboot = true,
                requiresCount = 0,
                progress = viewModel.getProgress(update.version),
                state = reviewState,
                onClose = {
                    reviewUpdate = null
                    reviewState = InstallReviewState()
                },
                onDownloadAndInspect = {
                    reviewState = InstallReviewState(phase = InstallReviewPhase.DOWNLOADING)
                    viewModel.downloader(
                        context = context,
                        module = update.local,
                        item = update.version,
                        onSuccess = { file ->
                            scope.launch {
                                reviewState = InstallReviewState(phase = InstallReviewPhase.VERIFYING, file = file, operationId = reviewState.operationId)
                                delay(120)
                                reviewState = reviewState.copy(phase = InstallReviewPhase.INSPECTING)
                                reviewState = runCatching {
                                    withContext(Dispatchers.IO) { ArchiveInspector.inspect(file) }
                                }.fold(
                                    onSuccess = { inspection ->
                                        InstallReviewState(
                                            phase = if (inspection.canInstall) InstallReviewPhase.READY else InstallReviewPhase.BLOCKED,
                                            file = file,
                                            inspection = inspection,
                                            operationId = reviewState.operationId,
                                        )
                                    },
                                    onFailure = { error ->
                                        InstallReviewState(phase = InstallReviewPhase.FAILED, file = file, error = error.message, operationId = reviewState.operationId)
                                    },
                                )
                            }
                        },
                        onFailure = { error ->
                            reviewState = InstallReviewState(phase = InstallReviewPhase.FAILED, error = error.message, operationId = reviewState.operationId)
                        },
                        onOperationStarted = { operationId -> reviewState = reviewState.copy(operationId = operationId) },
                    )
                },
                onInstall = {
                    reviewState.file?.let { file ->
                        val parentOperationId = reviewState.operationId
                        reviewUpdate = null
                        reviewState = InstallReviewState()
                        InstallActivity.start(
                            context = context,
                            uri = file.toUri(),
                            confirm = false,
                            parentOperationId = parentOperationId,
                        )
                    }
                },
                onInstallWithDependencies = {},
            )
        }

        if (reviewAll) {
            ReviewAllUpdatesSheet(
                updates = updates,
                onClose = { reviewAll = false },
                onInstall = { compatible ->
                    val bulkModules =
                        compatible.map {
                            BulkModule(
                                id = it.local.id.id,
                                name = it.local.name,
                                versionItem = it.version,
                            )
                        }
                    bulkInstall.downloadMultiple(
                        items = bulkModules,
                        onAllSuccess = { uris ->
                            reviewAll = false
                            InstallActivity.start(context = context, uri = uris)
                        },
                        onFailure = { error ->
                            Timber.e(error)
                            if (error is BulkDownloadException && error.successes.isNotEmpty()) {
                                reviewAll = false
                                InstallActivity.start(context = context, uri = error.successes.map { it.uri })
                            }
                            scope.launch {
                                snackbar.showSnackbar(error.message ?: context.getString(R.string.unknown_error))
                            }
                        },
                    )
                },
            )
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                BlurToolbar(
                    navigationIcon = {
                        IconButton(onClick = { navigator.popBackStack() }) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_left),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    title = {
                        ToolbarTitle(
                            title = stringResource(R.string.page_updates),
                            subtitle = stringResource(R.string.updates_subtitle, updates.size),
                        )
                    },
                    actions = {
                        if (updates.isNotEmpty()) {
                            TextButton(onClick = { reviewAll = true }) {
                                Text(stringResource(R.string.updates_review_all))
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            contentWindowInsets = WindowInsets.none,
        ) { innerPadding ->
            if (updates.isEmpty()) {
                PageIndicator(
                    icon = R.drawable.circle_check_filled,
                    text = R.string.updates_empty,
                )
                return@Scaffold
            }

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(.44f).fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item(key = "summary") { UpdatesSummary(updates = updates) }
                        items(items = updates, key = { it.local.id.id }) { update ->
                            UpdateRow(
                                update = update,
                                selected = selectedUpdate?.local?.id == update.local.id,
                                onReview = { selectedUpdate = update },
                                onIgnore = { ignoreUpdate(update) },
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(.56f).fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                    ) {
                        selectedUpdate?.let { update ->
                            UpdateDetailPane(
                                update = update,
                                modifier = Modifier.fillMaxSize(),
                                onReview = {
                                    reviewState = InstallReviewState()
                                    reviewUpdate = update
                                },
                                onIgnore = { ignoreUpdate(update) },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            bottom = bottomPadding + 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "summary") { UpdatesSummary(updates = updates) }
                    items(items = updates, key = { it.local.id.id }) { update ->
                        UpdateRow(
                            update = update,
                            selected = false,
                            onReview = {
                                reviewState = InstallReviewState()
                                reviewUpdate = update
                            },
                            onIgnore = { ignoreUpdate(update) },
                        )
                    }
                }
            }
        }
    }

@Composable
private fun UpdatesSummary(updates: List<ModuleUpdateInfo>) {
    val semantic = LocalSemanticColors.current
    val warnings = updates.count { it.hasBootScripts || it.hasSensitiveChanges }
    val compatible = updates.count { it.compatible }

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.updates_ready, compatible),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.updates_summary_description),
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UpdateTag(
                    text = stringResource(R.string.updates_compatible_count, compatible),
                    color = semantic.success,
                )
                if (warnings > 0) {
                    UpdateTag(
                        text = stringResource(R.string.updates_attention_count, warnings),
                        color = semantic.warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    update: ModuleUpdateInfo,
    selected: Boolean,
    onReview: () -> Unit,
    onIgnore: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    val surfaces = LocalMMRLSurfaces.current
    val changelog =
        update.version.changelog
            .takeIf { it.isNotBlank() }
            ?.let {
                if (it.startsWith("http://") || it.startsWith("https://")) {
                    stringResource(R.string.updates_changelog_available)
                } else {
                    it
                }
            } ?: stringResource(R.string.updates_no_changelog)

    Surface(
        onClick = onReview,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) surfaces.selected.copy(alpha = .45f) else surfaces.row,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 0.dp,
            ) {
                Icon(
                    painter = painterResource(R.drawable.device_mobile_down),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                )
            }

            Column(
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
            ) {
                Text(
                    text = update.local.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.updates_version_change,
                        update.local.version,
                        update.version.version,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = changelog,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            R.string.updates_source,
                            update.repositoryName ?: stringResource(R.string.updates_module_source),
                        ),
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    UpdateTag(
                        text = stringResource(if (update.compatible) R.string.repo_compatible else R.string.repo_incompatible),
                        color = if (update.compatible) semantic.success else semantic.incompatible,
                    )
                    if (update.hasBootScripts) {
                        UpdateTag(
                            text = stringResource(R.string.updates_boot_scripts),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (update.hasSensitiveChanges) {
                        UpdateTag(
                            text = stringResource(R.string.updates_sensitive_changes),
                            color = semantic.error,
                        )
                    }
                    UpdateTag(
                        text = stringResource(R.string.modules_reboot_required),
                        color = semantic.rebootRequired,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onReview) {
                    Text(stringResource(R.string.updates_review))
                }
                TextButton(onClick = onIgnore) {
                    Text(stringResource(R.string.updates_ignore))
                }
            }
        }
    }
}

@Composable
private fun UpdateDetailPane(
    update: ModuleUpdateInfo,
    modifier: Modifier = Modifier,
    onReview: () -> Unit,
    onIgnore: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(update.local.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.updates_version_change, update.local.version, update.version.version),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            UpdateTag(
                text = stringResource(if (update.compatible) R.string.repo_compatible else R.string.repo_incompatible),
                color = if (update.compatible) semantic.success else semantic.incompatible,
            )
            if (update.hasBootScripts) UpdateTag(stringResource(R.string.updates_boot_scripts), semantic.warning)
            if (update.hasSensitiveChanges) UpdateTag(stringResource(R.string.updates_sensitive_changes), semantic.error)
            UpdateTag(stringResource(R.string.modules_reboot_required), semantic.rebootRequired)
        }
        UpdateDetailSection(stringResource(R.string.module_details_changelog)) {
            Text(
                update.version.changelog.ifBlank { stringResource(R.string.updates_no_changelog) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        UpdateDetailSection(stringResource(R.string.updates_source, update.repositoryName ?: stringResource(R.string.updates_module_source))) {
            Text(update.repositoryName ?: stringResource(R.string.updates_module_source), style = MaterialTheme.typography.bodyMedium)
        }
        UpdateDetailSection(stringResource(R.string.module_details_files)) {
            Text(
                stringResource(R.string.updates_archive_inspection_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(enabled = update.compatible, onClick = onReview, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_review_and_update))
        }
        TextButton(onClick = onIgnore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.updates_ignore_module))
        }
    }
}

@Composable
private fun UpdateDetailSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ReviewAllUpdatesSheet(
    updates: List<ModuleUpdateInfo>,
    onClose: () -> Unit,
    onInstall: (List<ModuleUpdateInfo>) -> Unit,
) = BottomSheet(onDismissRequest = onClose) {
    val compatible = updates.filter { it.compatible }
    val excluded = updates.size - compatible.size

    Text(
        text = stringResource(R.string.updates_review_all),
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = stringResource(R.string.updates_review_all_description, compatible.size),
        modifier = Modifier.padding(horizontal = 18.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (excluded > 0) {
        Text(
            text = stringResource(R.string.updates_excluded_incompatible, excluded),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(compatible, key = { it.local.id.id }) { update ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
                    Text(update.local.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.updates_version_change, update.local.version, update.version.version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (update.hasBootScripts || update.hasSensitiveChanges) {
                        Text(
                            text = stringResource(R.string.updates_requires_review),
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
    ) {
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            enabled = compatible.isNotEmpty(),
            onClick = { onInstall(compatible) },
        ) {
            Text(stringResource(R.string.updates_install_compatible))
        }
    }
}

@Composable
private fun UpdateTag(
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
