package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.lsposed.LsposedCacheFreshness
import com.dergoogler.mmrl.lsposed.LsposedIdentity
import com.dergoogler.mmrl.lsposed.LsposedInstalledModule
import com.dergoogler.mmrl.lsposed.LsposedPolicyStore
import com.dergoogler.mmrl.lsposed.LsposedProviderRefreshMode
import com.dergoogler.mmrl.lsposed.LsposedProviderStatus
import com.dergoogler.mmrl.lsposed.LsposedSnapshot
import com.dergoogler.mmrl.lsposed.LsposedSnapshotPlanItem
import com.dergoogler.mmrl.lsposed.LsposedSnapshotPlanner
import com.dergoogler.mmrl.lsposed.LsposedVersionPolicy
import com.dergoogler.mmrl.lsposed.toSnapshotItem
import com.dergoogler.mmrl.lsposed.LsposedRepoModule
import com.dergoogler.mmrl.lsposed.LsposedRepository
import com.dergoogler.mmrl.lsposed.LsposedScopePlanner
import com.dergoogler.mmrl.lsposed.LsposedScopeState
import com.dergoogler.mmrl.lsposed.LsposedScopeTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.dergoogler.mmrl.platform.model.ModId
import javax.inject.Inject

@HiltViewModel
class LsposedViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {
    data class UiState(
        val loading: Boolean = false,
        val modules: List<LsposedRepoModule> = emptyList(),
        val installed: List<LsposedInstalledModule> = emptyList(),
        val query: String = "",
        val error: String? = null,
        val installingPackage: String? = null,
        val applyingScopePackage: String? = null,
        val managerAvailable: Boolean = false,
        val providerStatus: LsposedProviderStatus = LsposedProviderStatus(),
        val providerRefreshRecommended: Boolean = false,
        val scopeState: LsposedScopeState = LsposedScopeState(),
        val scopeTargets: List<LsposedScopeTarget> = emptyList(),
        val policies: Map<String, LsposedVersionPolicy> = emptyMap(),
        val snapshots: List<LsposedSnapshot> = emptyList(),
        val snapshotPlan: List<LsposedSnapshotPlanItem> = emptyList(),
        val repositoryFreshness: LsposedCacheFreshness = LsposedCacheFreshness.EMPTY,
        val repositoryFetchedAt: Long = 0L,
        val repositorySourceUrl: String = "",
        val repositoryPartial: Boolean = false,
        val repositoryStale: Boolean = false,
        val pendingEvent: Event? = null,
        val pendingEventId: Long = 0L,
    )

    sealed interface Event {
        data class InstallApk(val uri: Uri) : Event
        data class OpenIntent(val intent: android.content.Intent) : Event
        data class RunProviderAction(val moduleId: ModId) : Event
        data class Message(val text: String) : Event
    }

    private val repository = LsposedRepository(application.applicationContext)
    private val policyStore = LsposedPolicyStore(application.applicationContext)
    private val stateFlow = MutableStateFlow(UiState())
    val state = stateFlow.asStateFlow()
    private val eventsFlow = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = eventsFlow.asSharedFlow()
    private var refreshGeneration: Long = 0L
    private var nextEventId: Long = 0L

    fun acknowledgeEvent(id: Long) {
        stateFlow.update { current ->
            if (current.pendingEventId == id) current.copy(pendingEvent = null) else current
        }
    }

    private fun emitEvent(event: Event) {
        val id = ++nextEventId
        stateFlow.update { it.copy(pendingEvent = event, pendingEventId = id) }
        eventsFlow.tryEmit(event)
    }

    init {
        refresh(force = false)
        viewModelScope.launch {
            policyStore.policies.collectLatest { policies ->
                stateFlow.update { it.copy(policies = policies) }
            }
        }
        viewModelScope.launch {
            policyStore.snapshots.collectLatest { snapshots ->
                stateFlow.update { it.copy(snapshots = snapshots) }
            }
        }
    }

    fun search(query: String) {
        stateFlow.update { it.copy(query = query) }
    }

    fun refresh(force: Boolean = true) {
        val generation = ++refreshGeneration
        viewModelScope.launch {
            stateFlow.update { it.copy(loading = true, error = null) }
            val providerStatus = repository.providerStatus()
            val scopeState = repository.scopeState()
            val modulesResult = runCatching { repository.loadModulesWithState(forceRefresh = force) }
            val moduleState = modulesResult.getOrNull()
            val modules = moduleState?.modules.orEmpty()
            val installed = repository.installedModules(modules, scopeState)
            val targets = runCatching { repository.installedTargets() }.getOrDefault(emptyList())
            if (generation != refreshGeneration) return@launch
            stateFlow.update {
                it.copy(
                    loading = false,
                    modules = modules,
                    installed = installed,
                    managerAvailable = providerStatus.canOpen,
                    providerStatus = providerStatus,
                    providerRefreshRecommended = false,
                    scopeState = scopeState,
                    scopeTargets = targets,
                    repositoryFreshness = moduleState?.freshness ?: LsposedCacheFreshness.EMPTY,
                    repositoryFetchedAt = moduleState?.fetchedAt ?: 0L,
                    repositorySourceUrl = moduleState?.sourceUrl.orEmpty(),
                    repositoryPartial = moduleState?.partial == true,
                    repositoryStale = moduleState?.stale == true,
                    error = modulesResult.exceptionOrNull()?.message ?: moduleState?.errorMessage,
                )
            }
        }
    }


    fun followLatest(packageName: String) {
        viewModelScope.launch {
            policyStore.setPolicy(LsposedVersionPolicy.follow(packageName))
            emitEvent(Event.Message("LSPosed module follows latest updates."))
        }
    }

    fun ignoreUpdates(packageName: String) {
        viewModelScope.launch {
            policyStore.setPolicy(LsposedVersionPolicy.ignore(packageName))
            emitEvent(Event.Message("LSPosed APK updates ignored."))
        }
    }

    fun pinCurrent(module: LsposedInstalledModule) {
        viewModelScope.launch {
            policyStore.setPolicy(LsposedVersionPolicy.pinCurrent(module))
            emitEvent(Event.Message("LSPosed APK version locked."))
        }
    }

    fun maxCurrent(module: LsposedInstalledModule) {
        viewModelScope.launch {
            policyStore.setPolicy(LsposedVersionPolicy.maxCurrent(module))
            emitEvent(Event.Message("LSPosed APK updates limited to current version."))
        }
    }

    fun saveSnapshot(label: String = "Known-good LSPosed APK modules") {
        viewModelScope.launch {
            val snapshot = policyStore.saveSnapshot(
                label = label,
                modules = stateFlow.value.installed,
                policies = stateFlow.value.policies,
            )
            emitEvent(Event.Message("Saved LSPosed snapshot with ${snapshot.installedCount} APK modules."))
        }
    }

    fun deleteSnapshot(snapshotId: String) {
        viewModelScope.launch {
            policyStore.deleteSnapshot(snapshotId)
            stateFlow.update { it.copy(snapshotPlan = emptyList()) }
            emitEvent(Event.Message("Deleted LSPosed snapshot."))
        }
    }

    fun compareSnapshot(snapshot: LsposedSnapshot) {
        val current = stateFlow.value.installed.map { module ->
            val normalizedPackage = LsposedIdentity.normalize(module.packageName)
            module.toSnapshotItem(policy = stateFlow.value.policies[normalizedPackage])
        }
        stateFlow.update { it.copy(snapshotPlan = LsposedSnapshotPlanner.compare(snapshot, current)) }
    }

    fun applyScope(
        module: LsposedInstalledModule,
        enabled: Boolean,
        autoInclude: Boolean,
        targets: List<LsposedScopeTarget>,
    ) {
        viewModelScope.launch {
            val plan = runCatching { LsposedScopePlanner.plan(module, enabled, autoInclude, targets) }
                .getOrElse { error ->
                    emitEvent(Event.Message(error.message ?: "Unable to prepare LSPosed scope changes"))
                    return@launch
                }
            stateFlow.update { it.copy(applyingScopePackage = module.packageName, error = null) }
            runCatching { repository.applyScopePlan(plan) }
                .onSuccess { scopeState ->
                    val modules = stateFlow.value.modules
                    val installed = repository.installedModules(modules, scopeState)
                    val refreshPlan = repository.providerRefreshPlan(stateFlow.value.providerStatus)
                    stateFlow.update {
                        it.copy(
                            scopeState = scopeState,
                            installed = installed,
                            providerRefreshRecommended = true,
                        )
                    }
                    val message = when (refreshPlan.mode) {
                        LsposedProviderRefreshMode.OPEN_MANAGER -> "Applied LSPosed scope changes. Open the manager to refresh provider state."
                        LsposedProviderRefreshMode.ACTION_BRIDGE -> "Applied LSPosed scope changes. Use Refresh provider to run the active provider bridge."
                        LsposedProviderRefreshMode.REBOOT_REQUIRED -> "Applied LSPosed scope changes. Reboot if the provider does not refresh immediately."
                    }
                    emitEvent(Event.Message(message))
                }
                .onFailure { error ->
                    emitEvent(Event.Message(error.message ?: "Unable to apply LSPosed scope changes"))
                }
            stateFlow.update { it.copy(applyingScopePackage = null) }
        }
    }


    fun install(module: LsposedRepoModule) {
        viewModelScope.launch {
            stateFlow.update { it.copy(installingPackage = module.packageName, error = null) }
            runCatching { repository.prepareApk(module) }
                .onSuccess { prepared ->
                    emitEvent(Event.InstallApk(prepared.uri))
                }.onFailure { throwable ->
                    emitEvent(Event.Message(throwable.message ?: "Unable to prepare APK installer"))
                }
            stateFlow.update { it.copy(installingPackage = null) }
        }
    }

    fun update(installed: LsposedInstalledModule) {
        installed.repoModule?.let(::install)
    }

    fun openLsposed() {
        val intent = repository.lsposedManagerIntent()
        if (intent != null) {
            emitEvent(Event.OpenIntent(intent))
            return
        }
        val providerModuleId = repository.lsposedProviderActionModuleId()
        if (providerModuleId != null) {
            val canonicalId = ModId.parseOrNull(providerModuleId)
            if (canonicalId != null) {
                emitEvent(Event.RunProviderAction(canonicalId))
                return
            }
        }
        emitEvent(Event.Message("LSPosed, Vector, or another compatible framework provider is not installed or cannot be opened."))
    }

    fun refreshLsposedProvider() {
        val refreshPlan = repository.providerRefreshPlan(stateFlow.value.providerStatus)
        when (refreshPlan.mode) {
            LsposedProviderRefreshMode.OPEN_MANAGER -> {
                val intent = repository.lsposedManagerIntent()
                if (intent != null) {
                    emitEvent(Event.OpenIntent(intent))
                } else {
                    emitEvent(Event.Message("LSPosed manager is no longer available. Refresh the provider status."))
                }
            }
            LsposedProviderRefreshMode.ACTION_BRIDGE -> {
                val moduleId = refreshPlan.moduleId
                if (moduleId.isNullOrBlank()) {
                    emitEvent(Event.Message("Provider action bridge is unavailable. Reboot if scope changes do not appear."))
                } else {
                    val canonicalId = ModId.parseOrNull(moduleId)
                    if (canonicalId == null) {
                        emitEvent(Event.Message("Provider action bridge returned an invalid module id."))
                    } else {
                        emitEvent(Event.RunProviderAction(canonicalId))
                    }
                }
            }
            LsposedProviderRefreshMode.REBOOT_REQUIRED -> {
                emitEvent(Event.Message("Provider refresh bridge is unavailable. Reboot if scope changes do not appear."))
            }
        }
    }

    fun openApp(packageName: String) {
        val intent = repository.launchAppIntent(packageName)
        if (intent == null) {
            emitEvent(Event.Message("This module does not expose a launcher activity."))
        } else {
            emitEvent(Event.OpenIntent(intent))
        }
    }
}
