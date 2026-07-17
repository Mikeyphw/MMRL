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
