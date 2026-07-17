package com.dergoogler.mmrl.ash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.ash.data.AshModuleInstaller
import com.dergoogler.mmrl.ash.model.ActivityItem
import com.dergoogler.mmrl.ash.model.AshCapabilities
import com.dergoogler.mmrl.ash.model.AshInstallMode
import com.dergoogler.mmrl.ash.model.AshModuleLifecycle
import com.dergoogler.mmrl.ash.model.AshModuleLifecycleState
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.Dashboard
import com.dergoogler.mmrl.ash.model.ModuleItem
import com.dergoogler.mmrl.ash.model.OperationResult
import com.dergoogler.mmrl.ash.model.QuarantineItem
import com.dergoogler.mmrl.ash.model.SettingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionState {
    data object Checking : ConnectionState
    data object Ready : ConnectionState
    data class Cached(val message: String) : ConnectionState
    data object RootDenied : ConnectionState
    data object ModuleMissing : ConnectionState
    data object ModuleDisabled : ConnectionState
    data class ModuleOutdated(val message: String) : ConnectionState
    data class ModuleIncompatible(val message: String) : ConnectionState
    data class RebootPending(val message: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class AshUiState(
    val connection: ConnectionState = ConnectionState.Checking,
    val loading: Boolean = false,
    val readOnly: Boolean = true,
    val snapshotSource: AshSnapshotSource = AshSnapshotSource.None,
    val lastSuccessfulAt: Long = 0,
    val lifecycle: AshModuleLifecycle = AshModuleLifecycle(),
    val capabilities: AshCapabilities = AshCapabilities(),
    val dashboard: Dashboard = Dashboard(),
    val modules: List<ModuleItem> = emptyList(),
    val quarantine: List<QuarantineItem> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val settings: List<SettingItem> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class AshViewModel @Inject constructor(
    private val manager: AshReXcueManager,
) : ViewModel() {
    private val _state = MutableStateFlow(AshUiState())
    val state: StateFlow<AshUiState> = _state.asStateFlow()

    private val _moduleInstalls = MutableSharedFlow<AshModuleInstaller.PreparedInstall>(
        extraBufferCapacity = 1,
    )
    val moduleInstalls: SharedFlow<AshModuleInstaller.PreparedInstall> =
        _moduleInstalls.asSharedFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                message = null,
                connection = ConnectionState.Checking,
            )

            val managerState = runCatching { manager.refresh() }
                .getOrElse { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        connection = ConnectionState.Error(
                            error.message ?: "Unable to refresh AshReXcue",
                        ),
                    )
                    return@launch
                }

            val snapshot = managerState.snapshot
            val connection = when {
                snapshot != null && managerState.source == AshSnapshotSource.Cache ->
                    ConnectionState.Cached(
                        managerState.liveError ?: "Showing the last successful AshReXcue snapshot",
                    )
                snapshot != null -> ConnectionState.Ready
                !managerState.rootAvailable -> ConnectionState.RootDenied
                else -> when (managerState.lifecycle.state) {
                    AshModuleLifecycleState.Missing -> ConnectionState.ModuleMissing
                    AshModuleLifecycleState.Disabled -> ConnectionState.ModuleDisabled
                    AshModuleLifecycleState.RebootPending -> ConnectionState.RebootPending(
                        "AshReXcue has a staged module change. Reboot before using live controls.",
                    )
                    AshModuleLifecycleState.Outdated -> ConnectionState.ModuleOutdated(
                        managerState.lifecycle.compatibilityMessage,
                    )
                    AshModuleLifecycleState.Incompatible,
                    AshModuleLifecycleState.Broken,
                    -> ConnectionState.ModuleIncompatible(
                        managerState.lifecycle.compatibilityMessage,
                    )
                    else -> ConnectionState.Error(
                        managerState.liveError ?: "AshReXcue live status is unavailable",
                    )
                }
            }

            _state.value = _state.value.copy(
                loading = false,
                connection = connection,
                readOnly = managerState.readOnly,
                snapshotSource = managerState.source,
                lastSuccessfulAt = managerState.lastSuccessfulAt,
                lifecycle = managerState.lifecycle,
                capabilities = snapshot?.capabilities ?: AshCapabilities(),
                dashboard = snapshot?.dashboard ?: Dashboard(),
                modules = snapshot?.modules.orEmpty(),
                quarantine = snapshot?.quarantine.orEmpty(),
                activity = snapshot?.activity.orEmpty(),
                settings = snapshot?.settings.orEmpty(),
            )
        }
    }

    fun prepareModuleInstall(mode: AshInstallMode) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            runCatching { manager.prepareModuleInstall(mode) }
                .onSuccess { prepared ->
                    _state.value = _state.value.copy(loading = false)
                    _moduleInstalls.emit(prepared)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        message = error.message ?: "Unable to prepare the AshReXcue module",
                    )
                }
        }
    }

    fun setSetting(key: String, value: String) = operate { manager.setSetting(key, value) }
    fun setProtectionSettings(values: Map<String, String>) = operate { manager.setSettings(values) }
    fun setTrust(folder: String, trust: String) = operate { manager.setTrust(folder, trust) }
    fun restoreOne(folder: String) = operate { manager.restoreOne(folder) }
    fun restoreHalf() = operate(manager::restoreHalf)
    fun restoreAll() = operate(manager::restoreAll)
    fun completeTrial() = operate(manager::completeTrial)
    fun rollbackTrial() = operate(manager::rollbackTrial)
    fun discardPending() = operate(manager::discardPending)
    fun exportDiagnostics() = operate(manager::exportDiagnostics)

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun showMessage(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    private fun operate(block: suspend () -> OperationResult) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            runCatching { block() }
                .onSuccess { result ->
                    _state.value = _state.value.copy(loading = false, message = result.message)
                    refreshAll()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        message = error.message ?: "Operation failed",
                    )
                }
        }
    }

}
