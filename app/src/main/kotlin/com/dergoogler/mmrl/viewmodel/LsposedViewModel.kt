package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.lsposed.LsposedInstalledModule
import com.dergoogler.mmrl.lsposed.LsposedRepoModule
import com.dergoogler.mmrl.lsposed.LsposedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    )

    sealed interface Event {
        data class InstallApk(val uri: Uri) : Event
        data class OpenIntent(val intent: android.content.Intent) : Event
        data class Message(val text: String) : Event
    }

    private val repository = LsposedRepository(application.applicationContext)
    private val stateFlow = MutableStateFlow(UiState())
    val state = stateFlow.asStateFlow()
    private val eventsFlow = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = eventsFlow.asSharedFlow()

    init {
        refresh(force = false)
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
                stateFlow.update {
                    it.copy(
                        loading = false,
                        modules = modules,
                        installed = installed,
                        managerAvailable = repository.lsposedManagerIntent() != null,
                    )
                }
            }.onFailure { throwable ->
                stateFlow.update { it.copy(loading = false, error = throwable.message ?: "Unable to load LSPosed modules") }
            }
        }
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
        if (intent == null) {
            eventsFlow.tryEmit(Event.Message("LSPosed Manager is not installed or is hidden."))
        } else {
            eventsFlow.tryEmit(Event.OpenIntent(intent))
        }
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
