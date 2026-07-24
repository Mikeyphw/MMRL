package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.lsposed.LsposedIdentity
import com.dergoogler.mmrl.lsposed.LsposedInstalledModule
import com.dergoogler.mmrl.lsposed.LsposedPolicyStore
import com.dergoogler.mmrl.lsposed.LsposedProviderStatus
import com.dergoogler.mmrl.lsposed.LsposedSnapshot
import com.dergoogler.mmrl.lsposed.LsposedSnapshotPlanItem
import com.dergoogler.mmrl.lsposed.LsposedSnapshotPlanner
import com.dergoogler.mmrl.lsposed.LsposedVersionPolicy
import com.dergoogler.mmrl.lsposed.toSnapshotItem
import com.dergoogler.mmrl.lsposed.LsposedRepoModule
import com.dergoogler.mmrl.lsposed.LsposedRepository
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
        val managerAvailable: Boolean = false,
        val providerStatus: LsposedProviderStatus = LsposedProviderStatus(),
        val policies: Map<String, LsposedVersionPolicy> = emptyMap(),
        val snapshots: List<LsposedSnapshot> = emptyList(),
        val snapshotPlan: List<LsposedSnapshotPlanItem> = emptyList(),
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
        viewModelScope.launch {
            stateFlow.update { it.copy(loading = true, error = null) }
            runCatching {
                val modules = repository.loadModules(forceRefresh = force)
                val installed = repository.installedModules(modules)
                val providerStatus = repository.providerStatus()
                stateFlow.update {
                    it.copy(
                        loading = false,
                        modules = modules,
                        installed = installed,
                        managerAvailable = providerStatus.canOpen,
                        providerStatus = providerStatus,
                    )
                }
            }.onFailure { throwable ->
                stateFlow.update { it.copy(loading = false, error = throwable.message ?: "Unable to load LSPosed modules") }
            }
        }
    }


    fun followLatest(packageName: String) {
        viewModelScope.launch {
            policyStore.setPolicy(LsposedVersionPolicy.follow(packageName))
            eventsFlow.tryEmit(Event.Message("LSPosed module follows latest updates."))
        }
    }

    fun ignoreUpdates(packageName: String) {
        viewModelScope.launch {
            policyStore.setPolicy(LsposedVersionPolicy.ignore(packageName))
            eventsFlow.tryEmit(Event.Message("LSPosed APK updates ignored."))
        }
    }

    fun pinCurrent(module: LsposedInstalledModule) {
        viewModelScope.launch {
            policyStore.setPolicy(LsposedVersionPolicy.pinCurrent(module))
            eventsFlow.tryEmit(Event.Message("LSPosed APK version locked."))
        }
    }

    fun maxCurrent(module: LsposedInstalledModule) {
        viewModelScope.launch {
            policyStore.setPolicy(LsposedVersionPolicy.maxCurrent(module))
            eventsFlow.tryEmit(Event.Message("LSPosed APK updates limited to current version."))
        }
    }

    fun saveSnapshot(label: String = "Known-good LSPosed APK modules") {
        viewModelScope.launch {
            val snapshot = policyStore.saveSnapshot(
                label = label,
                modules = stateFlow.value.installed,
                policies = stateFlow.value.policies,
            )
            eventsFlow.tryEmit(Event.Message("Saved LSPosed snapshot with ${snapshot.installedCount} APK modules."))
        }
    }

    fun deleteSnapshot(snapshotId: String) {
        viewModelScope.launch {
            policyStore.deleteSnapshot(snapshotId)
            stateFlow.update { it.copy(snapshotPlan = emptyList()) }
            eventsFlow.tryEmit(Event.Message("Deleted LSPosed snapshot."))
        }
    }

    fun compareSnapshot(snapshot: LsposedSnapshot) {
        val current = stateFlow.value.installed.map { module ->
            val normalizedPackage = LsposedIdentity.normalize(module.packageName)
            module.toSnapshotItem(policy = stateFlow.value.policies[normalizedPackage])
        }
        stateFlow.update { it.copy(snapshotPlan = LsposedSnapshotPlanner.compare(snapshot, current)) }
    }


    fun install(module: LsposedRepoModule) {
        viewModelScope.launch {
            stateFlow.update { it.copy(installingPackage = module.packageName, error = null) }
            runCatching { repository.prepareApk(module) }
                .onSuccess { prepared ->
                    eventsFlow.tryEmit(Event.InstallApk(prepared.uri))
                }.onFailure { throwable ->
                    eventsFlow.tryEmit(Event.Message(throwable.message ?: "Unable to prepare APK installer"))
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
            eventsFlow.tryEmit(Event.OpenIntent(intent))
            return
        }
        val providerModuleId = repository.lsposedProviderActionModuleId()
        if (providerModuleId != null) {
            eventsFlow.tryEmit(Event.RunProviderAction(ModId(providerModuleId)))
            return
        }
        eventsFlow.tryEmit(Event.Message("LSPosed, Vector, or another compatible framework provider is not installed or cannot be opened."))
    }

    fun openApp(packageName: String) {
        val intent = repository.launchAppIntent(packageName)
        if (intent == null) {
            eventsFlow.tryEmit(Event.Message("This module does not expose a launcher activity."))
        } else {
            eventsFlow.tryEmit(Event.OpenIntent(intent))
        }
    }
}
