package com.dergoogler.mmrl.ash.model

data class Dashboard(
    val version: String = "—",
    val versionCode: Int = 0,
    val rootManager: String = "Unknown",
    val bootState: String = "unknown",
    val bootReason: String = "",
    val loops: Int = 0,
    val threshold: Int = 0,
    val rescueStage: Int = 0,
    val rescueStageLabel: String = "unknown",
    val nextRescue: String = "Unknown",
    val quarantined: Int = 0,
    val restoreState: String = "idle",
    val restoreCount: Int = 0,
    val timeout: Int = 0,
    val timeoutMinimum: Int = 0,
    val timeoutMaximum: Int = 0,
    val stability: Int = 0,
    val enabledModules: Int = 0,
    val disabledModules: Int = 0,
    val protectedModules: Int = 0,
    val trustedModules: Int = 0,
    val suspectModules: Int = 0,
    val latestRescueId: String = "",
    val latestRescueStatus: String = "",
    val latestRescueReason: String = "",
    val repairCount: Int = 0,
)

data class ModuleItem(
    val folder: String,
    val id: String,
    val name: String,
    val version: String,
    val versionCode: String,
    val enabled: Boolean,
    val quarantined: Boolean,
    val trust: String,
)

data class QuarantineItem(
    val folder: String,
    val id: String,
    val name: String,
    val trust: String,
    val rescueId: String,
    val disabledAt: Long,
    val exists: Boolean,
    val disablePresent: Boolean,
)

data class ActivityItem(
    val id: String,
    val timestamp: Long,
    val type: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val details: String,
)

data class SettingItem(
    val key: String,
    val value: String,
    val queuedValue: String? = null,
    val editable: Boolean = true,
)

data class PendingSetting(
    val key: String,
    val value: String,
    val current: String,
)

data class OperationResult(
    val ok: Boolean,
    val message: String,
    val path: String? = null,
)

data class AshCapabilities(
    val apiVersion: Int = 0,
    val minimumClientApi: Int = 0,
    val moduleVersion: String = "",
    val moduleVersionCode: Int = 0,
    val features: Set<String> = emptySet(),
) {
    fun supports(feature: String): Boolean = feature in features
}

data class AshModuleInstallation(
    val installed: Boolean = false,
    val active: Boolean = false,
    val folder: String = "",
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val versionCode: Int = 0,
    val source: String = "none",
    val controlAvailable: Boolean = false,
    val disabled: Boolean = false,
    val removalPending: Boolean = false,
    val updatePending: Boolean = false,
)

data class AshBundledModuleMetadata(
    val id: String = "AshLooper",
    val name: String = "AshReXcue BootLoop Protector",
    val version: String = "",
    val versionCode: Int = 0,
)

enum class AshModuleLifecycleState {
    Missing,
    Installed,
    Disabled,
    Incompatible,
    Outdated,
    RebootPending,
    Current,
    Newer,
    Broken,
}

data class AshModuleLifecycle(
    val state: AshModuleLifecycleState = AshModuleLifecycleState.Missing,
    val installation: AshModuleInstallation = AshModuleInstallation(),
    val bundled: AshBundledModuleMetadata = AshBundledModuleMetadata(),
    val compatible: Boolean = false,
    val compatibilityMessage: String = "",
    val updateAvailable: Boolean = false,
    val reinstallRecommended: Boolean = false,
    val rebootRequired: Boolean = false,
)

enum class AshSnapshotSource {
    None,
    Live,
    Cache,
}

data class AshSnapshot(
    val schemaVersion: Int = 0,
    val generatedAt: Long = 0,
    val capabilities: AshCapabilities = AshCapabilities(),
    val dashboard: Dashboard = Dashboard(),
    val modules: List<ModuleItem> = emptyList(),
    val quarantine: List<QuarantineItem> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val settings: List<SettingItem> = emptyList(),
    val pendingSettings: List<PendingSetting> = emptyList(),
)

data class AshManagerState(
    val rootAvailable: Boolean = false,
    val lifecycle: AshModuleLifecycle = AshModuleLifecycle(),
    val snapshot: AshSnapshot? = null,
    val source: AshSnapshotSource = AshSnapshotSource.None,
    val readOnly: Boolean = true,
    val lastSuccessfulAt: Long = 0,
    val liveError: String? = null,
)

enum class AshInstallMode {
    Install,
    Update,
    Reinstall,
}

object AshModuleLifecycleResolver {
    const val SUPPORTED_API_MIN = 2
    const val SUPPORTED_API_MAX = 2

    fun resolve(
        installation: AshModuleInstallation,
        bundled: AshBundledModuleMetadata,
        capabilities: AshCapabilities?,
        liveError: String? = null,
    ): AshModuleLifecycle {
        val updateAvailable = installation.versionCode > 0 &&
            bundled.versionCode > installation.versionCode
        val apiCompatible = capabilities?.apiVersion in SUPPORTED_API_MIN..SUPPORTED_API_MAX &&
            (capabilities?.minimumClientApi ?: 0) <= SUPPORTED_API_MAX
        val featureCompatible = capabilities?.supports("snapshot") == true &&
            capabilities.supports("capabilities")
        val compatible = installation.controlAvailable && apiCompatible && featureCompatible
        val rebootRequired = installation.updatePending || installation.removalPending ||
            (installation.installed && !installation.active)

        val state = when {
            !installation.installed && !installation.updatePending -> AshModuleLifecycleState.Missing
            rebootRequired -> AshModuleLifecycleState.RebootPending
            installation.disabled -> AshModuleLifecycleState.Disabled
            !installation.controlAvailable -> AshModuleLifecycleState.Broken
            updateAvailable -> AshModuleLifecycleState.Outdated
            !compatible -> AshModuleLifecycleState.Incompatible
            installation.versionCode > bundled.versionCode && bundled.versionCode > 0 ->
                AshModuleLifecycleState.Newer
            installation.versionCode == bundled.versionCode && installation.versionCode > 0 ->
                AshModuleLifecycleState.Current
            else -> AshModuleLifecycleState.Installed
        }

        val compatibilityMessage = when {
            !installation.installed -> "AshReXcue is not installed"
            !installation.controlAvailable -> "The installed module has no usable typed control service"
            capabilities == null -> liveError ?: "The installed module did not report API capabilities"
            capabilities.apiVersion < SUPPORTED_API_MIN ->
                "Module API ${capabilities.apiVersion} is older than supported API $SUPPORTED_API_MIN"
            capabilities.apiVersion > SUPPORTED_API_MAX ->
                "Module API ${capabilities.apiVersion} is newer than supported API $SUPPORTED_API_MAX"
            capabilities.minimumClientApi > SUPPORTED_API_MAX ->
                "The module requires client API ${capabilities.minimumClientApi}"
            !capabilities.supports("snapshot") || !capabilities.supports("capabilities") ->
                "The installed module does not provide the required snapshot API"
            else -> "Compatible API ${capabilities.apiVersion}"
        }

        return AshModuleLifecycle(
            state = state,
            installation = installation,
            bundled = bundled,
            compatible = compatible,
            compatibilityMessage = compatibilityMessage,
            updateAvailable = updateAvailable,
            reinstallRecommended = installation.installed &&
                (!installation.controlAvailable || (!compatible && !updateAvailable)),
            rebootRequired = rebootRequired,
        )
    }
}

enum class MainDestination(val label: String, val glyph: String) {
    Status("Status", "S"),
    Modules("Modules", "M"),
    Recovery("Recovery", "R"),
    Activity("Activity", "A"),
    Settings("Settings", "⚙"),
}

enum class ThemePreset(val label: String) {
    MMRL("MMRL"),
    Dracula("Dracula"),
    Nord("Nord"),
    Monokai("Monokai"),
    Monet("Monet"),
}
