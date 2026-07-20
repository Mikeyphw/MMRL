package com.dergoogler.mmrl.ui.screens.moduleView

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.database.entity.Repo
import com.dergoogler.mmrl.ext.fadingEdge
import com.dergoogler.mmrl.ext.none
import com.dergoogler.mmrl.ext.nullable
import com.dergoogler.mmrl.ext.systemBarsPaddingEnd
import com.dergoogler.mmrl.installer.ArchiveInspector
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.local.BulkModule
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.VersionItem
import com.dergoogler.mmrl.model.online.isBlacklisted
import com.dergoogler.mmrl.ui.activity.terminal.install.InstallActivity
import com.dergoogler.mmrl.ui.component.Cover
import com.dergoogler.mmrl.ui.component.LocalScreenProvider
import com.dergoogler.mmrl.ui.component.scaffold.Scaffold
import com.dergoogler.mmrl.ui.providable.LocalBulkInstall
import com.dergoogler.mmrl.ui.providable.LocalDestinationsNavigator
import com.dergoogler.mmrl.ui.providable.LocalHazeState
import com.dergoogler.mmrl.ui.providable.LocalMainScreenInnerPaddings
import com.dergoogler.mmrl.ui.providable.LocalModule
import com.dergoogler.mmrl.ui.providable.LocalOnlineModule
import com.dergoogler.mmrl.ui.providable.LocalRepo
import com.dergoogler.mmrl.ui.providable.LocalScrollBehavior
import com.dergoogler.mmrl.ui.providable.LocalSnackbarHost
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.ui.providable.LocalVersionItem
import com.dergoogler.mmrl.ui.screens.moduleView.items.InstallReviewBottomSheet
import com.dergoogler.mmrl.ui.screens.moduleView.items.InstallReviewPhase
import com.dergoogler.mmrl.ui.screens.moduleView.items.InstallReviewState
import com.dergoogler.mmrl.ui.screens.moduleView.items.ViewTrackBottomSheet
import com.dergoogler.mmrl.ui.screens.moduleView.providable.LocalModuleViewDownloader
import com.dergoogler.mmrl.ui.screens.moduleView.providable.LocalModuleViewModel
import com.dergoogler.mmrl.ui.screens.moduleView.providable.LocalRequireModules
import com.dergoogler.mmrl.ui.screens.moduleView.sections.AshModuleIntelligenceCard
import com.dergoogler.mmrl.ui.screens.moduleView.sections.AshReXcueIntegrationCard
import com.dergoogler.mmrl.ui.screens.moduleView.sections.Header
import com.dergoogler.mmrl.ui.screens.moduleView.sections.Toolbar
import com.dergoogler.mmrl.viewmodel.ModuleViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val listItemContentPaddingValues = PaddingValues(vertical = 16.dp, horizontal = 16.dp)
internal val subListItemContentPaddingValues = PaddingValues(vertical = 8.dp, horizontal = 16.dp)

@Composable
@Destination<RootGraph>
fun NewViewScreen(
    repo: Repo,
    module: OnlineModule,
) = LocalScreenProvider {
    val viewModel = ModuleViewModel.build(repo, module)

    val navigator = LocalDestinationsNavigator.current
    val bulkInstallViewModel = LocalBulkInstall.current
    val userPreferences = LocalUserPreferences.current
    val repositoryMenu = userPreferences.repositoryMenu
    val module = viewModel.online
    val moduleAll by viewModel.onlineAll.collectAsStateWithLifecycle()
    val local = viewModel.local
    val lastVersionItem = viewModel.lastVersionItem
    val context = LocalContext.current
    val density = LocalDensity.current
    val browser = LocalUriHandler.current
    val hazeState = LocalHazeState.current

    val listState = rememberLazyListState()

    val screenshotsLazyListState = rememberLazyListState()
    val categoriesLazyListState = rememberLazyListState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var installReviewState by remember { mutableStateOf(InstallReviewState()) }
    var reviewVersionItem by remember { mutableStateOf<VersionItem?>(null) }

    LaunchedEffect(viewModel.installConfirm) {
        if (viewModel.installConfirm) {
            installReviewState = InstallReviewState()
            if (reviewVersionItem == null) reviewVersionItem = lastVersionItem
        }
    }

    val download: (VersionItem, Boolean) -> Unit = { item, install ->
        if (install) {
            reviewVersionItem = item
            installReviewState = InstallReviewState()
            viewModel.installConfirm = true
        } else {
            viewModel.downloader(context, item, onSuccess = {})
        }
    }

    val manager = module.manager(viewModel.platform)
    val compatible =
        manager.isCompatible(viewModel.versionCode) &&
            (module.minApi == null || Build.VERSION.SDK_INT >= module.minApi) &&
            (module.maxApi == null || Build.VERSION.SDK_INT <= module.maxApi)
    val requires =
        manager.require?.let {
            moduleAll
                .filter { onlineModules ->
                    onlineModules.second.id in it
                }.map { it2 -> it2.second }
        } ?: emptyList()

    if (viewModel.installConfirm && (reviewVersionItem ?: lastVersionItem) != null) {
        val reviewVersion = checkNotNull(reviewVersionItem ?: lastVersionItem)
        InstallReviewBottomSheet(
            module = module,
            moduleName = module.name,
            version = reviewVersion,
            installedVersion = local?.version,
            repositoryName = repo.name,
            compatible = compatible,
            requiresReboot = true,
            requiresCount = requires.size,
            progress = viewModel.getProgress(reviewVersion),
            state = installReviewState,
            onClose = {
                viewModel.installConfirm = false
                installReviewState = InstallReviewState()
                reviewVersionItem = null
            },
            onDownloadAndInspect = {
                installReviewState = InstallReviewState(phase = InstallReviewPhase.DOWNLOADING)
                viewModel.downloader(
                    context = context,
                    item = reviewVersion,
                    onSuccess = { file ->
                        scope.launch {
                            installReviewState = InstallReviewState(
                                phase = InstallReviewPhase.VERIFYING,
                                file = file,
                                operationId = installReviewState.operationId,
                            )
                            delay(120)
                            installReviewState = installReviewState.copy(phase = InstallReviewPhase.INSPECTING)
                            val inspected = runCatching {
                                withContext(Dispatchers.IO) { ArchiveInspector.inspect(file) }
                            }
                            installReviewState = inspected.fold(
                                onSuccess = { inspection ->
                                    InstallReviewState(
                                        phase = if (inspection.canInstall) InstallReviewPhase.READY else InstallReviewPhase.BLOCKED,
                                        file = file,
                                        inspection = inspection,
                                        operationId = installReviewState.operationId,
                                    )
                                },
                                onFailure = { error ->
                                    InstallReviewState(
                                        phase = InstallReviewPhase.FAILED,
                                        file = file,
                                        error = error.message,
                                        operationId = installReviewState.operationId,
                                    )
                                },
                            )
                        }
                    },
                    onFailure = { error ->
                        installReviewState = InstallReviewState(
                            phase = InstallReviewPhase.FAILED,
                            error = error.message,
                            operationId = installReviewState.operationId,
                        )
                    },
                    onOperationStarted = { operationId ->
                        installReviewState = installReviewState.copy(operationId = operationId)
                    },
                )
            },
            onInstall = {
                installReviewState.file?.let { file ->
                    viewModel.installConfirm = false
                    val parentOperationId = installReviewState.operationId
                    reviewVersionItem = null
                    InstallActivity.start(
                        context = context,
                        uri = file.toUri(),
                        confirm = false,
                        parentOperationId = parentOperationId,
                    )
                }
            },
            onInstallWithDependencies = {
                val bulkModules = mutableListOf<BulkModule>()
                bulkModules += BulkModule(
                    id = module.id,
                    name = module.name,
                    versionItem = reviewVersion,
                )
                bulkModules += requires.map { required ->
                    BulkModule(
                        id = required.id,
                        name = required.name,
                        versionItem = required.versions.first(),
                    )
                }
                bulkInstallViewModel.downloadMultiple(
                    items = bulkModules,
                    onAllSuccess = { uris ->
                        viewModel.installConfirm = false
                        reviewVersionItem = null
                        InstallActivity.start(context = context, uri = uris, confirm = false)
                    },
                    onFailure = { error ->
                        installReviewState = InstallReviewState(
                            phase = InstallReviewPhase.FAILED,
                            error = error.message,
                        )
                    },
                )
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val isBlacklisted by module.isBlacklisted

    if (viewModel.versionSelectBottomSheet) {
        VersionSelectBottomSheet(
            onClose = { viewModel.versionSelectBottomSheet = false },
            versions = viewModel.versions,
            localVersionCode = viewModel.localVersionCode,
            isProviderAlive = viewModel.isProviderAlive,
            getProgress = { viewModel.getProgress(it) },
            onDownload = download,
            isBlacklisted = isBlacklisted,
        )
    }

    if (viewModel.viewTrackBottomSheet) {
        ViewTrackBottomSheet(
            onClose = { viewModel.viewTrackBottomSheet = false },
            tracks = viewModel.tracks,
        )
    }

    CompositionLocalProvider(
        LocalSnackbarHost provides snackbarHostState,
        LocalOnlineModule provides module,
        LocalVersionItem provides (lastVersionItem ?: VersionItem.EMPTY),
        LocalModule provides (local ?: com.dergoogler.mmrl.platform.content.LocalModule.EMPTY),
        LocalRepo provides repo,
        LocalModuleViewModel provides viewModel,
        LocalModuleViewDownloader provides download,
        LocalRequireModules provides requires,
        LocalScrollBehavior provides scrollBehavior,
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Toolbar()
            },
            contentWindowInsets = WindowInsets.none,
        ) { innerPadding ->
            this@Scaffold.ResponsiveContent {
                Column(
                    modifier =
                        Modifier
                            .let {
                                if (repositoryMenu.showCover && module.hasCover) {
                                    Modifier
                                } else {
                                    it.padding(innerPadding)
                                }
                            }.verticalScroll(rememberScrollState())
                            .hazeSource(state = hazeState),
                ) {
                    module.cover.nullable(repositoryMenu.showCover) {
                        if (it.isNotEmpty()) {
                            Cover(
                                modifier =
                                    Modifier.fadingEdge(
                                        Brush.verticalGradient(
                                            colors =
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black,
                                                ),
                                            startY = Float.POSITIVE_INFINITY,
                                            endY = 0f,
                                        ),
                                    ),
                                url = it,
                            )
                        }
                    }

                    Column(
                        modifier =
                            Modifier
                                .systemBarsPaddingEnd(),
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Header()
                        if (ModuleIdentity.matches(module.id, "AshLooper")) {
                            AshReXcueIntegrationCard()
                        } else {
                            AshModuleIntelligenceCard()
                        }
                        ModuleDecisionSummary()
                        ModuleDetailsTabs()

                        Spacer(modifier = Modifier.navigationBarsPadding())

                        val paddingValues = LocalMainScreenInnerPaddings.current
                        Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
                    }
                }
            }
        }
    }
}
