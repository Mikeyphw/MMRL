package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.database.entity.Repo
import com.dergoogler.mmrl.database.entity.Repo.Companion.UPDATE_JSON
import com.dergoogler.mmrl.database.entity.Repo.Companion.toRepo
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.installer.OperationStagingStore
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.json.UpdateJson
import com.dergoogler.mmrl.model.local.LocalModule
import com.dergoogler.mmrl.model.local.State
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.OtherSources
import com.dergoogler.mmrl.model.online.TrackJson
import com.dergoogler.mmrl.model.online.VersionItem
import com.dergoogler.mmrl.model.state.OnlineState
import com.dergoogler.mmrl.operation.ModuleMutationExecutor
import com.dergoogler.mmrl.service.ModuleService
import com.dergoogler.mmrl.model.state.OnlineState.Companion.createState
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.repository.LocalRepository
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.service.DownloadService
import com.dergoogler.mmrl.utils.Utils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class ModuleOps(
    val isOpsRunning: Boolean,
    val toggle: (Boolean) -> Unit,
    val change: () -> Unit,
)

@HiltViewModel(assistedFactory = ModuleViewModel.Factory::class)
class ModuleViewModel
    @AssistedInject
    constructor(
        @Assisted val repo: Repo,
        @Assisted val module: OnlineModule,
        localRepository: LocalRepository,
        modulesRepository: ModulesRepository,
        userPreferencesRepository: UserPreferencesRepository,
        application: Application,
        private val operationStagingStore: OperationStagingStore,
        private val moduleMutationExecutor: ModuleMutationExecutor,
    ) : MMRLViewModel(
            localRepository = localRepository,
            modulesRepository = modulesRepository,
            userPreferencesRepository = userPreferencesRepository,
            application = application,
        ) {
        var installConfirm by mutableStateOf(false)
        var menuExpanded by mutableStateOf(false)
        var versionSelectBottomSheet by mutableStateOf(false)
        var viewTrackBottomSheet by mutableStateOf(false)

        val version: String
            get() =
                PlatformManager.get("") {
                    with(moduleManager) { version }
                }

        val versionCode: Int
            get() =
                PlatformManager.get(0) {
                    with(moduleManager) { versionCode }
                }

        var online: OnlineModule by mutableStateOf(OnlineModule.example())
            private set
        val lastVersionItem by derivedStateOf {
            val firstRepo = versions.getOrNull(0)?.first
            val item =
                if (firstRepo?.name == UPDATE_JSON) {
                    versions.getOrNull(1)
                } else {
                    versions.getOrNull(0)
                }
            item?.second
        }
        val isEmptyAbout
            get() =
                online.homepage.orEmpty().isBlank() &&
                    online.track.source.isBlank() &&
                    online.support.orEmpty().isBlank()

        val isEmptyReadme get() = !online.hasReadme
        val readme get() = online.readme.orEmpty()
        var local: LocalModule? by mutableStateOf(null)
            private set

        private val installed get() = local?.let { ModuleIdentity.matches(it.id.id, online.id) } ?: false
        var notifyUpdates by mutableStateOf(false)
            private set

        val localVersionCode
            get() =
                if (notifyUpdates && installed) local!!.versionCode else Int.MAX_VALUE
        val updatableSize by derivedStateOf {
            versions.count { it.second.versionCode > localVersionCode }
        }

        val otherSources = mutableStateListOf<OtherSources>()
        val versions = mutableStateListOf<Pair<Repo, VersionItem>>()
        val tracks = mutableStateListOf<Pair<Repo, TrackJson>>()

        init {
            Timber.d("ModuleViewModel init: ${module.id}")
            loadData()
            modulesAll()
        }

        private fun loadData() =
            viewModelScope.launch {
                localRepository.getOnlineByIdAndUrl(module.id, repo.url).let {
                    online = it
                }

                localRepository.getOnlineAllById(module.id).let {
                    val filtered = it.filter { f -> f.repoUrl != repo.url }

                    otherSources.addAll(
                        filtered.map { module ->
                            OtherSources(
                                repo = localRepository.getRepoByUrl(module.repoUrl),
                                online = module,
                                state =
                                    module.createState(
                                        local = localRepository.getLocalByIdOrNull(module.id),
                                        hasUpdatableTag = localRepository.hasUpdatableTag(module.id),
                                    ),
                            )
                        },
                    )
                }

                localRepository.getLocalByIdOrNull(module.id)?.let {
                    local = it
                    notifyUpdates = localRepository.hasUpdatableTag(module.id)
                }

                localRepository.getVersionByIdAndUrl(module.id, repo.url).forEach {
                    val repo = localRepository.getRepoByUrl(it.repoUrl)

                    val item = repo to it
                    val track =
                        repo to
                            localRepository
                                .getOnlineByIdAndUrl(
                                    id = online.id,
                                    repoUrl = it.repoUrl,
                                ).track

                    versions.add(item)
                    if (track !in tracks) tracks.add(track)
                }

                if (installed) {
                    UpdateJson.loadToVersionItem(local!!.updateJson)?.let {
                        versions.add(0, UPDATE_JSON.toRepo() to it)
                    }
                }
            }

        private val onlineAllFlow = MutableStateFlow(listOf<Pair<OnlineState, OnlineModule>>())
        val onlineAll get() = onlineAllFlow.asStateFlow()

        private fun modulesAll() {
            combine(
                localRepository.getOnlineAllAsFlow(),
            ) { list ->
                onlineAllFlow.value =
                    list.first().map {
                        it.createState(
                            local = localRepository.getLocalByIdOrNull(it.id),
                            hasUpdatableTag = localRepository.hasUpdatableTag(it.id),
                        ) to it
                    }
            }.launchIn(viewModelScope)
        }

        fun setUpdatesTag(updatable: Boolean) {
            viewModelScope.launch {
                notifyUpdates = updatable
                localRepository.insertUpdatableTag(module.id, updatable)
                userPreferencesRepository.clearNotifiedModuleUpdate(module.id)
                if (!updatable) ModuleService.cancelUpdateNotification(context, module.id)
            }
        }

        fun downloader(
            context: Context,
            item: VersionItem,
            onSuccess: (Uri) -> Unit,
            onFailure: (Throwable) -> Unit = {},
            onOperationStarted: (String) -> Unit = {},
        ) {
            val filename =
                Utils.getFilename(
                    name = online.name,
                    version = item.version,
                    versionCode = item.versionCode,
                    extension = "zip",
                )

            val task =
                DownloadService.TaskItem(
                    key = item.hashCode(),
                    url = item.zipUrl,
                    filename = filename,
                    title = online.name,
                    desc = item.versionDisplay,
                )

            val listener =
                object : DownloadService.IDownloadListener {
                    override fun onStarted(operationId: String) = onOperationStarted(operationId)

                    override fun getProgress(value: Float) {}

                    override fun onFileExists() {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.file_already_exists),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }

                    override fun onSuccess(uri: Uri) = onSuccess(uri)

                    override fun onFailure(e: Throwable) {
                        Timber.d(e)
                        onFailure(e)
                    }
                }

            DownloadService.start(
                context = context,
                task = task,
                listener = listener,
            )
        }

        suspend fun stageForReview(uri: Uri): OperationStagingStore.StagedArtifact = operationStagingStore.stage(uri)

        suspend fun releaseReviewStage(operationId: String?) {
            if (!operationId.isNullOrBlank()) operationStagingStore.release(operationId)
        }

        @Composable
        fun getProgress(item: VersionItem): Float {
            val progress by DownloadService
                .getProgressByKey(item.hashCode())
                .collectAsStateWithLifecycle(initialValue = 0f)

            return progress
        }

        private val opsTasks = mutableStateListOf<ModId>()

        private fun launchModuleOperation(
            module: LocalModule,
            useShell: Boolean,
            kind: com.dergoogler.mmrl.database.entity.history.OperationKind,
            action: com.dergoogler.mmrl.database.entity.history.OperationAction,
            rollbackAction: com.dergoogler.mmrl.database.entity.history.OperationAction?,
            successSummary: String,
        ) {
            if (module.id in opsTasks) return
            opsTasks.add(module.id)
            viewModelScope.launch {
                moduleMutationExecutor.execute(
                    module = module,
                    useShell = useShell,
                    kind = kind,
                    action = action,
                    rollbackAction = rollbackAction,
                    successSummary = successSummary,
                ).onSuccess {
                    local = localRepository.getLocalByIdOrNull(module.id.id)
                }.onFailure { error ->
                    Timber.w(error, "${module.id}: module mutation failed")
                }
                opsTasks.remove(module.id)
            }
        }

        fun createModuleOps(
            useShell: Boolean,
            module: LocalModule,
        ) = when (module.state) {
            State.ENABLE ->
                ModuleOps(
                    isOpsRunning = opsTasks.contains(module.id),
                    toggle = {
                        launchModuleOperation(
                            module, useShell,
                            com.dergoogler.mmrl.database.entity.history.OperationKind.DISABLE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.DISABLE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.ENABLE,
                            "Module disabled",
                        )
                    },
                    change = {
                        launchModuleOperation(
                            module, useShell,
                            com.dergoogler.mmrl.database.entity.history.OperationKind.REMOVE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.REMOVE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.ENABLE,
                            "Module marked for removal",
                        )
                    },
                )

            State.DISABLE ->
                ModuleOps(
                    isOpsRunning = opsTasks.contains(module.id),
                    toggle = {
                        launchModuleOperation(
                            module, useShell,
                            com.dergoogler.mmrl.database.entity.history.OperationKind.ENABLE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.ENABLE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.DISABLE,
                            "Module enabled",
                        )
                    },
                    change = {
                        launchModuleOperation(
                            module, useShell,
                            com.dergoogler.mmrl.database.entity.history.OperationKind.REMOVE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.REMOVE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.ENABLE,
                            "Module marked for removal",
                        )
                    },
                )

            State.REMOVE ->
                ModuleOps(
                    isOpsRunning = opsTasks.contains(module.id),
                    toggle = {},
                    change = {
                        launchModuleOperation(
                            module, useShell,
                            com.dergoogler.mmrl.database.entity.history.OperationKind.ENABLE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.ENABLE,
                            com.dergoogler.mmrl.database.entity.history.OperationAction.REMOVE,
                            "Module removal reverted",
                        )
                    },
                )

            State.UPDATE -> ModuleOps(isOpsRunning = opsTasks.contains(module.id), toggle = {}, change = {})
        }

        @AssistedFactory
        interface Factory {
            fun create(
                repo: Repo,
                module: OnlineModule,
            ): ModuleViewModel
        }

        companion object {
            @Composable
            fun build(
                repo: Repo,
                module: OnlineModule,
            ): ModuleViewModel =
                hiltViewModel<ModuleViewModel, Factory> { factory ->
                    factory.create(repo, module)
                }
        }
    }
