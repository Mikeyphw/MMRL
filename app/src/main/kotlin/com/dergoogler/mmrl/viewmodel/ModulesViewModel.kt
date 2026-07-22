package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ash.AshReXcueManager
import com.dergoogler.mmrl.ash.model.AshModuleFilter
import com.dergoogler.mmrl.ash.model.AshModuleProtection
import com.dergoogler.mmrl.ash.model.matches
import com.dergoogler.mmrl.ash.model.moduleProtections
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.datastore.model.ModulesMenu
import com.dergoogler.mmrl.datastore.model.Option
import com.dergoogler.mmrl.model.json.UpdateJson
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.local.LocalModule
import com.dergoogler.mmrl.model.local.State
import com.dergoogler.mmrl.model.state.Permissions
import com.dergoogler.mmrl.service.ModuleService
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.VersionItem
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.hasAction
import com.dergoogler.mmrl.platform.content.LocalModule.Companion.hasWebUI
import com.dergoogler.mmrl.platform.content.ModuleCompatibility
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.repository.LocalRepository
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import com.dergoogler.mmrl.service.DownloadService
import com.dergoogler.mmrl.utils.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class ModulesScreenState(
    val items: List<LocalModule> = listOf(),
    val isRefreshing: Boolean = false,
)

data class ModuleUpdateInfo(
    val local: LocalModule,
    val version: VersionItem,
    val online: OnlineModule?,
    val repositoryName: String?,
    val compatible: Boolean,
    val hasBootScripts: Boolean,
    val hasSensitiveChanges: Boolean,
)

@HiltViewModel
class ModulesViewModel
    @Inject
    constructor(
        localRepository: LocalRepository,
        modulesRepository: ModulesRepository,
        userPreferencesRepository: UserPreferencesRepository,
        operationHistoryRepository: OperationHistoryRepository,
        private val ashManager: AshReXcueManager,
        application: Application,
    ) : MMRLViewModel(
            application = application,
            localRepository = localRepository,
            modulesRepository = modulesRepository,
            userPreferencesRepository = userPreferencesRepository,
        ) {
        private val historyRepository = operationHistoryRepository

        val ashState = ashManager.state
        private val ashFilterFlow = MutableStateFlow(AshModuleFilter.All)
        val ashFilter = ashFilterFlow.asStateFlow()
        private val ashMessagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val ashMessages = ashMessagesFlow.asSharedFlow()

        val moduleCompatibility: ModuleCompatibility
            get() =
                PlatformManager.get(
                    ModuleCompatibility(
                        hasMagicMount = false,
                        canRestoreModules = false,
                    ),
                ) {
                    with(moduleManager) {
                        moduleCompatibility
                    }
                }

        fun getBlacklist(id: String?) = runBlocking { getBlacklistById(id) }

        private val modulesMenu
            get() =
                userPreferencesRepository.data
                    .map { it.modulesMenu }

        var isSearch by mutableStateOf(false)
            private set
        private val keyFlow = MutableStateFlow("")
        val query get() = keyFlow.asStateFlow()

        val listState: LazyListState = LazyListState()

        private val cacheFlow = MutableStateFlow(listOf<LocalModule>())
        private val localFlow = MutableStateFlow(listOf<LocalModule>())
        val local get() = localFlow.asStateFlow()

        private var isLoadingFlow = MutableStateFlow(false)
        val isLoading get() = isLoadingFlow.asStateFlow()

        private inline fun <T> T.refreshing(callback: T.() -> Unit) {
            isLoadingFlow.update { true }
            callback()
            isLoadingFlow.update { false }
        }

        private val versionItemCache = mutableStateMapOf<ModId, VersionItem?>()

        private val opsTasks = mutableStateListOf<ModId>()
        private val pendingOperations = mutableMapOf<ModId, PendingModuleOperation>()
        private val opsCallback =
            object : IModuleOpsCallback.Stub() {
                override fun onSuccess(id: ModId) {
                    viewModelScope.launch {
                        val pending = pendingOperations.remove(id)
                        pending?.let {
                            historyRepository.succeed(
                                id = it.historyId,
                                summary = it.successSummary,
                                requiresReboot = true,
                                rollbackAction = it.rollbackAction,
                            )
                        }
                        modulesRepository.getLocal(id)
                        opsTasks.remove(id)
                    }
                }

                override fun onFailure(
                    id: ModId,
                    msg: String?,
                ) {
                    viewModelScope.launch {
                        pendingOperations.remove(id)?.let {
                            historyRepository.fail(
                                id = it.historyId,
                                summary = msg ?: "Module operation failed",
                            )
                        }
                        opsTasks.remove(id)
                    }
                    Timber.w("$id: $msg")
                }
            }

        init {
            Timber.d("ModulesViewModel init")
            providerObserver()
            dataObserver()
            keyObserver()
            viewModelScope.launch { ashManager.refreshIfStale() }
        }

        private fun providerObserver() {
            PlatformManager.isAliveFlow
                .onEach {
                    if (it) getLocalAll()
                }.launchIn(viewModelScope)
        }

        val screenState: StateFlow<ModulesScreenState> =
            localRepository
                .getLocalAllAsFlow()
                .combine(isLoadingFlow) { items, isRefreshing ->
                    ModulesScreenState(items = items, isRefreshing = isRefreshing)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = ModulesScreenState(),
                )

        val updates: StateFlow<List<ModuleUpdateInfo>> =
            combine(
                localRepository.getLocalAllAsFlow(),
                localRepository.getOnlineAllAsFlow(),
                localRepository.getRepoAllAsFlow(),
                localRepository.getUpdatableTagsAsFlow(),
                localRepository.getLocalSourcesAsFlow(),
            ) { localModules, onlineModules, repositories, updatableTags, localSources ->
                val repoNames = repositories.associate { it.url to it.name }
                val updateTracking = updatableTags.associate { ModuleIdentity.normalize(it.id) to it.updatable }
                val sourceTracking = localSources.associateBy { ModuleIdentity.normalize(it.id) }
                val platform = PlatformManager.platform
                val rootVersion =
                    PlatformManager.get(0) {
                        with(moduleManager) { versionCode }
                    }

                buildList {
                    localModules.forEach { local ->
                        val normalizedId = ModuleIdentity.normalize(local.id.id)
                        if (updateTracking[normalizedId] == false) return@forEach
                        val source = sourceTracking[normalizedId]

                        val online =
                            onlineModules
                                .filter {
                                    ModuleIdentity.matches(it.id, local.id.id) &&
                                        (source == null || it.repoUrl == source.repoUrl)
                                }
                                .maxByOrNull { it.versionCode }

                        val version =
                            if (local.updateJson.isNotBlank()) {
                                UpdateJson.loadToVersionItem(local.updateJson)
                            } else if (source != null) {
                                online?.versions?.maxByOrNull { it.versionCode }
                                    ?: localRepository.getVersionByIdAndUrl(local.id.toString(), source.repoUrl).maxByOrNull { it.versionCode }
                            } else {
                                online?.versions?.maxByOrNull { it.versionCode }
                                    ?: localRepository.getVersionById(local.id.toString()).maxByOrNull { it.versionCode }
                            }

                        val installedVersionCode = source?.installedVersionCode ?: local.versionCode
                        if (version == null || version.versionCode <= installedVersionCode) return@forEach

                        val managerCompatible =
                            online?.manager(platform)?.isCompatible(rootVersion) ?: true
                        val androidCompatible =
                            online?.let { module ->
                                (module.minApi == null || Build.VERSION.SDK_INT >= module.minApi) &&
                                    (module.maxApi == null || Build.VERSION.SDK_INT <= module.maxApi)
                            } ?: true
                        val permissions = online?.permissions.orEmpty()
                        val features = online?.features
                        val hasBootScripts =
                            Permissions.MAGISK_SERVICE in permissions ||
                                Permissions.MAGISK_POST_FS_DATA in permissions ||
                                Permissions.KERNELSU_POST_MOUNT in permissions ||
                                Permissions.KERNELSU_BOOT_COMPLETED in permissions ||
                                features?.service == true ||
                                features?.postFsData == true ||
                                features?.postMount == true ||
                                features?.bootCompleted == true
                        val hasSensitiveChanges =
                            Permissions.MAGISK_SEPOLICY in permissions ||
                                Permissions.MAGISK_RESETPROP in permissions ||
                                features?.sepolicy == true ||
                                features?.resetprop == true

                        add(
                            ModuleUpdateInfo(
                                local = local,
                                version = version,
                                online = online,
                                repositoryName = repoNames[online?.repoUrl ?: version.repoUrl],
                                compatible = managerCompatible && androidCompatible,
                                hasBootScripts = hasBootScripts,
                                hasSensitiveChanges = hasSensitiveChanges,
                            ),
                        )
                    }
                }.sortedWith(
                    compareByDescending<ModuleUpdateInfo> { it.hasSensitiveChanges || it.hasBootScripts }
                        .thenBy { it.local.name.lowercase() },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        private fun dataObserver() {
            combine(
                localRepository.getLocalAllAsFlow(),
                modulesMenu,
            ) { list, menu ->
                if (list.isEmpty()) return@combine

                cacheFlow.value =
                    list
                        .sortedWith(
                            comparator(menu.option, menu.descending),
                        ).let { v ->
                            val a =
                                if (menu.pinEnabled) {
                                    v.sortedByDescending { it.state == State.ENABLE }
                                } else {
                                    v
                                }

                            val b =
                                if (menu.pinAction) {
                                    a.sortedByDescending { it.hasAction }
                                } else {
                                    a
                                }

                            if (menu.pinWebUI) {
                                b.sortedByDescending { it.hasWebUI }
                            } else {
                                b
                            }
                        }

                isLoadingFlow.update { false }
            }.launchIn(viewModelScope)
        }

        private fun keyObserver() {
            combine(
                keyFlow,
                cacheFlow,
                ashFilterFlow,
                ashManager.state,
            ) { key, source, ashFilter, ashState ->
                val protections = ashState.moduleProtections()
                val newKey =
                    when {
                        key.startsWith("id:", ignoreCase = true) -> key.removePrefix("id:")
                        key.startsWith("name:", ignoreCase = true) -> key.removePrefix("name:")
                        key.startsWith("author:", ignoreCase = true) -> key.removePrefix("author:")
                        else -> key
                    }.trim()

                localFlow.value =
                    source.filter { module ->
                        val protection = protections[ModuleIdentity.normalize(module.id.id)]
                        if (!protection.matches(ashFilter)) return@filter false
                        if (key.isNotBlank() || newKey.isNotBlank()) {
                            when {
                                key.startsWith("id:", ignoreCase = true) ->
                                    module.id.equals(newKey, ignoreCase = true)

                                key.startsWith("name:", ignoreCase = true) ->
                                    module.name.equals(newKey, ignoreCase = true)

                                key.startsWith("author:", ignoreCase = true) ->
                                    module.author.equals(newKey, ignoreCase = true)

                                else ->
                                    module.name.contains(key, ignoreCase = true) ||
                                        module.author.contains(key, ignoreCase = true) ||
                                        module.description.contains(key, ignoreCase = true)
                            }
                        } else {
                            true
                        }
                    }
            }.launchIn(viewModelScope)
        }

        private fun comparator(
            option: Option,
            descending: Boolean,
        ): Comparator<LocalModule> =
            if (descending) {
                when (option) {
                    Option.Name -> compareByDescending { it.name.lowercase() }
                    Option.UpdatedTime -> compareBy { it.lastUpdated }
                    Option.Size -> compareByDescending { it.size }
                }
            } else {
                when (option) {
                    Option.Name -> compareBy { it.name.lowercase() }
                    Option.UpdatedTime -> compareByDescending { it.lastUpdated }
                    Option.Size -> compareBy { it.size }
                }
            }

        fun search(key: String) {
            keyFlow.value = key
        }

        fun openSearch() {
            isSearch = true
        }

        fun closeSearch() {
            isSearch = false
            keyFlow.value = ""
        }

        fun getLocalAll() =
            viewModelScope.launch {
                refreshing {
                    modulesRepository.getLocalAll()
                }
            }

        fun setModulesMenu(value: ModulesMenu) {
            viewModelScope.launch {
                userPreferencesRepository.setModulesMenu(value)
            }
        }

        fun setAshFilter(value: AshModuleFilter) {
            ashFilterFlow.value = value
        }

        fun refreshAshProtection() {
            viewModelScope.launch {
                runCatching { ashManager.refresh() }
                    .onFailure { ashMessagesFlow.emit(it.message ?: "Unable to refresh AshReXcue") }
            }
        }

        override fun onCleared() {
            ashManager.releaseRootSession()
            super.onCleared()
        }

        fun ashProtection(module: LocalModule): AshModuleProtection? =
            ashManager.state.value.moduleProtections()[ModuleIdentity.normalize(module.id.id)]

        fun setAshTrust(module: LocalModule, trust: String) {
            val protection = ashProtection(module)
            if (protection == null) {
                ashMessagesFlow.tryEmit("AshReXcue has not indexed this module yet")
                return
            }
            viewModelScope.launch {
                runCatching { ashManager.setTrust(protection.folder, trust) }
                    .onSuccess { result ->
                        ashMessagesFlow.emit(result.message)
                        ashManager.refresh()
                    }
                    .onFailure { ashMessagesFlow.emit(it.message ?: "Unable to change AshReXcue trust") }
            }
        }

        fun testRestore(module: LocalModule) {
            val protection = ashProtection(module)
            if (protection?.quarantined != true) {
                ashMessagesFlow.tryEmit("This module is not quarantined")
                return
            }
            viewModelScope.launch {
                runCatching { ashManager.restoreOne(protection.folder) }
                    .onSuccess { result ->
                        ashMessagesFlow.emit(result.message)
                        ashManager.refresh()
                    }
                    .onFailure { ashMessagesFlow.emit(it.message ?: "Unable to start restoration trial") }
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
                            module = module,
                            useShell = useShell,
                            kind = OperationKind.DISABLE,
                            action = OperationAction.DISABLE,
                            rollbackAction = OperationAction.ENABLE,
                            successSummary = "Module disabled; reboot required",
                        ) {
                            PlatformManager.moduleManager.disable(module.id, useShell, opsCallback)
                        }
                    },
                    change = {
                        launchModuleOperation(
                            module = module,
                            useShell = useShell,
                            kind = OperationKind.REMOVE,
                            action = OperationAction.REMOVE,
                            rollbackAction =
                                if (moduleCompatibility.canRestoreModules) OperationAction.ENABLE else null,
                            successSummary = "Module marked for removal; reboot required",
                        ) {
                            PlatformManager.moduleManager.remove(module.id, useShell, opsCallback)
                        }
                    },
                )

            State.DISABLE ->
                ModuleOps(
                    isOpsRunning = opsTasks.contains(module.id),
                    toggle = {
                        launchModuleOperation(
                            module = module,
                            useShell = useShell,
                            kind = OperationKind.ENABLE,
                            action = OperationAction.ENABLE,
                            rollbackAction = OperationAction.DISABLE,
                            successSummary = "Module enabled; reboot required",
                        ) {
                            PlatformManager.moduleManager.enable(module.id, useShell, opsCallback)
                        }
                    },
                    change = {
                        launchModuleOperation(
                            module = module,
                            useShell = useShell,
                            kind = OperationKind.REMOVE,
                            action = OperationAction.REMOVE,
                            rollbackAction =
                                if (moduleCompatibility.canRestoreModules) OperationAction.ENABLE else null,
                            successSummary = "Module marked for removal; reboot required",
                        ) {
                            PlatformManager.moduleManager.remove(module.id, useShell, opsCallback)
                        }
                    },
                )

            State.REMOVE ->
                ModuleOps(
                    isOpsRunning = opsTasks.contains(module.id),
                    toggle = {},
                    change = {
                        launchModuleOperation(
                            module = module,
                            useShell = useShell,
                            kind = OperationKind.RESTORE,
                            action = OperationAction.ENABLE,
                            rollbackAction = OperationAction.REMOVE,
                            successSummary = "Module removal cancelled; reboot required",
                        ) {
                            PlatformManager.moduleManager.enable(module.id, useShell, opsCallback)
                        }
                    },
                )

            State.UPDATE ->
                ModuleOps(
                    isOpsRunning = opsTasks.contains(module.id),
                    toggle = {},
                    change = {},
                )
        }

        private fun launchModuleOperation(
            module: LocalModule,
            useShell: Boolean,
            kind: OperationKind,
            action: OperationAction,
            rollbackAction: OperationAction?,
            successSummary: String,
            execute: () -> Unit,
        ) {
            if (opsTasks.contains(module.id)) return
            opsTasks.add(module.id)
            viewModelScope.launch {
                val historyId =
                    historyRepository.start(
                        kind = kind,
                        title = module.name,
                        summary = kind.name.lowercase().replaceFirstChar(Char::uppercaseChar),
                        moduleId = module.id.id,
                        moduleName = module.name,
                        retryAction = action,
                        rollbackAction = rollbackAction,
                        useShell = useShell,
                    )
                pendingOperations[module.id] =
                    PendingModuleOperation(
                        historyId = historyId,
                        successSummary = successSummary,
                        rollbackAction = rollbackAction,
                    )
                historyRepository.appendLog(historyId, "Module ID: ${module.id.id}")
                historyRepository.appendLog(historyId, "Root backend: ${PlatformManager.platform.name}")
                try {
                    execute()
                } catch (error: Throwable) {
                    pendingOperations.remove(module.id)
                    opsTasks.remove(module.id)
                    historyRepository.fail(
                        id = historyId,
                        summary = error.message ?: "Unable to start module operation",
                        error = error,
                    )
                }
            }
        }

        private data class PendingModuleOperation(
            val historyId: String,
            val successSummary: String,
            val rollbackAction: OperationAction?,
        )

        @Composable
        fun getVersionItem(module: LocalModule): VersionItem? {
            val item by remember {
                derivedStateOf { versionItemCache[module.id] }
            }

            LaunchedEffect(key1 = module) {
                if (!localRepository.hasUpdatableTag(module.id.toString())) {
                    versionItemCache.remove(module.id)
                    return@LaunchedEffect
                }

                if (versionItemCache.containsKey(module.id)) return@LaunchedEffect

                val versionItem =
                    if (module.updateJson.isNotBlank()) {
                        UpdateJson.loadToVersionItem(module.updateJson)
                    } else {
                        val source = localRepository.getLocalSourceByIdOrNull(module.id.toString())
                        if (source != null) {
                            localRepository
                                .getVersionByIdAndUrl(module.id.toString(), source.repoUrl)
                                .maxByOrNull { it.versionCode }
                        } else {
                            localRepository
                                .getVersionById(module.id.toString())
                                .firstOrNull()
                        }
                    }

                versionItemCache[module.id] = versionItem
            }

            return item
        }

        fun setUpdateIgnored(moduleId: String, ignored: Boolean) {
            viewModelScope.launch {
                localRepository.insertUpdatableTag(moduleId, !ignored)
                userPreferencesRepository.clearNotifiedModuleUpdate(moduleId)
                if (ignored) ModuleService.cancelUpdateNotification(context, moduleId)
            }
        }

        fun downloader(
            context: Context,
            module: LocalModule,
            item: VersionItem,
            onSuccess: (File) -> Unit,
            onFailure: (Throwable) -> Unit = { Timber.d(it) },
            onOperationStarted: (String) -> Unit = {},
        ) {
            viewModelScope.launch {
                val downloadPath =
                    userPreferencesRepository.data
                        .first()
                        .downloadPath

                val filename =
                    Utils.getFilename(
                        name = module.name,
                        version = item.version,
                        versionCode = item.versionCode,
                        extension = "zip",
                    )

                val task =
                    DownloadService.TaskItem(
                        key = item.hashCode(),
                        url = item.zipUrl,
                        filename = filename,
                        title = module.name,
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

                        override fun onSuccess() {
                            onSuccess(File(downloadPath).resolve(filename))
                        }

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
        }

        @Composable
        fun getProgress(item: VersionItem?): Float {
            val progress by DownloadService
                .getProgressByKey(item.hashCode())
                .collectAsStateWithLifecycle(initialValue = 0f)

            return progress
        }

        data class ModuleOps(
            val isOpsRunning: Boolean,
            val toggle: (Boolean) -> Unit,
            val change: () -> Unit,
        )
    }
