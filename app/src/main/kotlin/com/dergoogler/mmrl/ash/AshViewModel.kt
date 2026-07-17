package com.dergoogler.mmrl.ash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.ash.data.AshRepository
import com.dergoogler.mmrl.ash.model.ActivityItem
import com.dergoogler.mmrl.ash.model.Dashboard
import com.dergoogler.mmrl.ash.model.ModuleItem
import com.dergoogler.mmrl.ash.model.OperationResult
import com.dergoogler.mmrl.ash.model.QuarantineItem
import com.dergoogler.mmrl.ash.model.SettingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ConnectionState {
    data object Checking : ConnectionState
    data object Ready : ConnectionState
    data object RootDenied : ConnectionState
    data object ModuleMissing : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class AshUiState(
    val connection: ConnectionState = ConnectionState.Checking,
    val loading: Boolean = false,
    val dashboard: Dashboard = Dashboard(),
    val modules: List<ModuleItem> = emptyList(),
    val quarantine: List<QuarantineItem> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val settings: List<SettingItem> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class AshViewModel @Inject constructor(
    private val repository: AshRepository,
) : ViewModel() {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(AshUiState())
    val state: kotlinx.coroutines.flow.StateFlow<AshUiState> = _state.asStateFlow()

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

            val hasRoot = withTimeoutOrNull(ROOT_CHECK_TIMEOUT_MS) {
                repository.rootAvailable()
            } ?: false

            if (!hasRoot) {
                _state.value = _state.value.copy(
                    loading = false,
                    connection = ConnectionState.RootDenied,
                )
                return@launch
            }

            val hasModule = withTimeoutOrNull(MODULE_CHECK_TIMEOUT_MS) {
                repository.moduleAvailable()
            } ?: false

            if (!hasModule) {
                _state.value = _state.value.copy(
                    loading = false,
                    connection = ConnectionState.ModuleMissing,
                )
                return@launch
            }

            val dashboardResult = runCatching {
                withTimeoutOrNull(PRIMARY_LOAD_TIMEOUT_MS) {
                    repository.dashboard()
                } ?: throw IllegalStateException("Timed out reading AshReXcue status")
            }

            dashboardResult
                .onSuccess { dashboard ->
                    _state.value = _state.value.copy(
                        loading = false,
                        connection = ConnectionState.Ready,
                        dashboard = dashboard,
                    )
                    refreshSecondary()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        connection = ConnectionState.Error(
                            error.message ?: "Unable to read AshReXcue status",
                        ),
                    )
                }
        }
    }

    private fun refreshSecondary() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)

            val modules = readOptional(emptyList()) { repository.modules() }
            _state.value = _state.value.copy(modules = modules)

            val quarantine = readOptional(emptyList()) { repository.quarantine() }
            _state.value = _state.value.copy(quarantine = quarantine)

            val activity = readOptional(emptyList()) { repository.activity() }
            _state.value = _state.value.copy(activity = activity)

            val settings = readOptional(emptyList()) { repository.settings() }
            _state.value = _state.value.copy(settings = settings, loading = false)
        }
    }

    private suspend fun <T> readOptional(
        fallback: T,
        block: suspend () -> T,
    ): T = runCatching {
        withTimeoutOrNull(SECONDARY_LOAD_TIMEOUT_MS) { block() } ?: fallback
    }.getOrDefault(fallback)

    fun setSetting(key: String, value: String) = operate { repository.setSetting(key, value) }
    fun setProtectionSettings(values: Map<String, String>) = operate { repository.setSettings(values) }
    fun setTrust(folder: String, trust: String) = operate { repository.setTrust(folder, trust) }
    fun restoreOne(folder: String) = operate { repository.restoreOne(folder) }
    fun restoreHalf() = operate { repository.restoreHalf() }
    fun restoreAll() = operate { repository.restoreAll() }
    fun completeTrial() = operate { repository.completeTrial() }
    fun rollbackTrial() = operate { repository.rollbackTrial() }
    fun discardPending() = operate { repository.discardPending() }
    fun exportDiagnostics() = operate { repository.exportDiagnostics() }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun showMessage(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    private fun operate(block: suspend () -> OperationResult) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            try {
                val result = block()
                _state.value = _state.value.copy(loading = false, message = result.message)
                refreshAll()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = error.message ?: "Operation failed",
                )
            }
        }
    }

    private companion object {
        const val ROOT_CHECK_TIMEOUT_MS = 8_000L
        const val MODULE_CHECK_TIMEOUT_MS = 10_000L
        const val PRIMARY_LOAD_TIMEOUT_MS = 15_000L
        const val SECONDARY_LOAD_TIMEOUT_MS = 12_000L
    }
}
