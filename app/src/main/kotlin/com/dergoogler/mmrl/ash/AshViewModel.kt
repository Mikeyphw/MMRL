package com.dergoogler.mmrl.ash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.ash.data.AshModuleInstaller
import com.dergoogler.mmrl.ash.model.ActivityItem
import com.dergoogler.mmrl.ash.model.AshCapabilities
import com.dergoogler.mmrl.ash.model.AshGuidanceOutcome
import com.dergoogler.mmrl.ash.model.AshInstallMode
import com.dergoogler.mmrl.ash.model.AshModuleIntelligence
import com.dergoogler.mmrl.ash.model.AshModuleLifecycle
import com.dergoogler.mmrl.ash.model.AshModuleLifecycleState
import com.dergoogler.mmrl.ash.model.AshRecoveryPlan
import com.dergoogler.mmrl.ash.model.AshReleaseGateReport
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.AshStateHealth
import com.dergoogler.mmrl.ash.model.Dashboard
import com.dergoogler.mmrl.ash.model.ModuleItem
import com.dergoogler.mmrl.ash.model.OperationResult
import com.dergoogler.mmrl.ash.model.QuarantineItem
import com.dergoogler.mmrl.ash.model.SettingItem
import com.dergoogler.mmrl.ash.model.moduleIntelligence
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

enum class AshOperationKind {
    Refresh,
    PrepareModuleInstall,
    SaveSettings,
    SetTrust,
    RestoreModule,
    ExecuteRecoveryPlan,
    CompleteTrial,
    RollbackTrial,
    DiscardPending,
    ExportDiagnostics,
    RepairState,
    RecordGuidanceOutcome,
}

data class AshOperation(
    val kind: AshOperationKind,
    val target: String = "",
)

data class AshUiState(
    val connection: ConnectionState = ConnectionState.Checking,
    val readOnly: Boolean = true,
    val snapshotSource: AshSnapshotSource = AshSnapshotSource.None,
    val lastSuccessfulAt: Long = 0,
    val snapshotGeneratedAt: Long = 0,
    val recoveryRevision: String = "",
    val lifecycle: AshModuleLifecycle = AshModuleLifecycle(),
    val capabilities: AshCapabilities = AshCapabilities(),
    val dashboard: Dashboard = Dashboard(),
    val modules: List<ModuleItem> = emptyList(),
    val moduleIntelligence: Map<String, AshModuleIntelligence> = emptyMap(),
    val quarantine: List<QuarantineItem> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val settings: List<SettingItem> = emptyList(),
    val health: AshStateHealth = AshStateHealth(),
    val releaseGate: AshReleaseGateReport = AshReleaseGateReport(),
    val lastOperation: OperationResult? = null,
    val activeOperations: Set<AshOperation> = emptySet(),
    val message: String? = null,
) {
    /** Compatibility for older surfaces. New recovery UI should query a specific operation. */
    val loading: Boolean
        get() = activeOperations.isNotEmpty()

    val refreshing: Boolean
        get() = isOperationRunning(AshOperationKind.Refresh)

    fun isOperationRunning(kind: AshOperationKind, target: String? = null): Boolean =
        activeOperations.any { operation ->
            operation.kind == kind && (target == null || operation.target == target)
        }
}

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
        val operation = AshOperation(AshOperationKind.Refresh)
        viewModelScope.launch {
            if (!begin(operation)) return@launch
            _state.update { it.copy(connection = ConnectionState.Checking, message = null) }
            try {
                refreshFromManager()
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        connection = ConnectionState.Error(
                            error.message ?: "Unable to refresh AshReXcue",
                        ),
                    )
                }
            } finally {
                end(operation)
            }
        }
    }

    fun prepareModuleInstall(mode: AshInstallMode) {
        val operation = AshOperation(AshOperationKind.PrepareModuleInstall, mode.name)
        viewModelScope.launch {
            if (!begin(operation)) return@launch
            _state.update { it.copy(message = null) }
            try {
                val prepared = manager.prepareModuleInstall(mode)
                _moduleInstalls.emit(prepared)
            } catch (error: Throwable) {
                _state.update {
                    it.copy(message = error.message ?: "Unable to prepare the AshReXcue module")
                }
            } finally {
                end(operation)
            }
        }
    }

    fun setSetting(key: String, value: String) =
        operate(AshOperation(AshOperationKind.SaveSettings, key)) { manager.setSetting(key, value) }

    fun setProtectionSettings(values: Map<String, String>) =
        operate(AshOperation(AshOperationKind.SaveSettings, "protection")) { manager.setSettings(values) }

    fun setTrust(folder: String, trust: String) =
        operate(AshOperation(AshOperationKind.SetTrust, folder)) { manager.setTrust(folder, trust) }

    fun restoreOne(folder: String) =
        operate(AshOperation(AshOperationKind.RestoreModule, folder)) { manager.restoreOne(folder) }

    fun restoreHalf() =
        operate(AshOperation(AshOperationKind.ExecuteRecoveryPlan, "legacy-half"), manager::restoreHalf)

    fun restoreBatch(folders: List<String>) =
        operate(AshOperation(AshOperationKind.ExecuteRecoveryPlan, folders.sorted().joinToString("|"))) {
            manager.restoreBatch(folders)
        }

    fun restoreAll() =
        operate(AshOperation(AshOperationKind.ExecuteRecoveryPlan, "legacy-all"), manager::restoreAll)

    fun executeRecoveryPlan(plan: AshRecoveryPlan) =
        operate(AshOperation(AshOperationKind.ExecuteRecoveryPlan, plan.id)) {
            manager.executeRecoveryPlan(plan)
        }

    fun completeTrial() =
        operate(AshOperation(AshOperationKind.CompleteTrial), manager::completeTrial)

    fun rollbackTrial() =
        operate(AshOperation(AshOperationKind.RollbackTrial), manager::rollbackTrial)

    fun discardPending() =
        operate(AshOperation(AshOperationKind.DiscardPending), manager::discardPending)

    fun exportDiagnostics() =
        operate(AshOperation(AshOperationKind.ExportDiagnostics), manager::exportDiagnostics)

    fun repairState() =
        operate(AshOperation(AshOperationKind.RepairState), manager::repairState)

    fun recordGuidanceOutcome(
        recommendationId: String,
        moduleFolder: String,
        outcome: AshGuidanceOutcome,
    ) = operate(AshOperation(AshOperationKind.RecordGuidanceOutcome, recommendationId)) {
        manager.recordGuidanceOutcome(recommendationId, moduleFolder, outcome)
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    private fun operate(
        operation: AshOperation,
        block: suspend () -> OperationResult,
    ) {
        viewModelScope.launch {
            if (!begin(operation)) return@launch
            _state.update { it.copy(message = null) }
            try {
                val result = block()
                _state.update {
                    it.copy(
                        lastOperation = result,
                        message = result.message,
                    )
                }
                refreshFromManager()
            } catch (error: Throwable) {
                _state.update { it.copy(message = error.message ?: "Operation failed") }
            } finally {
                end(operation)
            }
        }
    }

    private suspend fun refreshFromManager() {
        val managerState = manager.refresh()
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

        _state.update {
            it.copy(
                connection = connection,
                readOnly = managerState.readOnly,
                snapshotSource = managerState.source,
                lastSuccessfulAt = managerState.lastSuccessfulAt,
                snapshotGeneratedAt = snapshot?.generatedAt ?: 0L,
                recoveryRevision = snapshot?.recoveryRevision.orEmpty(),
                lifecycle = managerState.lifecycle,
                capabilities = snapshot?.capabilities ?: AshCapabilities(),
                dashboard = snapshot?.dashboard ?: Dashboard(),
                modules = snapshot?.modules.orEmpty(),
                moduleIntelligence = managerState.moduleIntelligence(),
                quarantine = snapshot?.quarantine.orEmpty(),
                activity = snapshot?.activity.orEmpty(),
                settings = snapshot?.settings.orEmpty(),
                health = managerState.health,
                releaseGate = managerState.releaseGate,
            )
        }
    }

    private fun begin(operation: AshOperation): Boolean {
        if (operation in _state.value.activeOperations) return false
        _state.update { it.copy(activeOperations = it.activeOperations + operation) }
        return true
    }

    private fun end(operation: AshOperation) {
        _state.update { it.copy(activeOperations = it.activeOperations - operation) }
    }
}
