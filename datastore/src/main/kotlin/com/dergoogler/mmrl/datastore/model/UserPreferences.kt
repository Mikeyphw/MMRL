@file:OptIn(ExperimentalSerializationApi::class)

package com.dergoogler.mmrl.datastore.model

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Environment
import com.dergoogler.mmrl.datastore.BuildConfig
import com.dergoogler.mmrl.ui.theme.ThemeColorSource
import com.dergoogler.mmrl.ui.theme.ThemeContrast
import com.dergoogler.mmrl.ui.theme.ThemeRegistry
import com.dergoogler.mmrl.ui.theme.ThemeSurfaceStyle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private const val LEGACY_DYNAMIC_THEME_ID = -1
private const val LEGACY_MMRL_THEME_ID = 7
private const val DEFAULT_PUBLIC_DOWNLOADS_PATH = "/storage/emulated/0/Download"

@Serializable
data class UserPreferences(
    @ProtoNumber(1) val workingMode: WorkingMode = WorkingMode.FIRST_SETUP,
    @ProtoNumber(2) val darkMode: DarkMode = DarkMode.AlwaysOn,
    @ProtoNumber(3) val themeColor: Int = LEGACY_MMRL_THEME_ID,
    @ProtoNumber(4) val deleteZipFile: Boolean = false,
    @ProtoNumber(5) val downloadPath: String = DEFAULT_PUBLIC_DOWNLOADS_PATH,
    @ProtoNumber(6) val homepage: Homepage = Homepage.Home,
    @ProtoNumber(7) val repositoryMenu: RepositoryMenu = RepositoryMenu(),
    @ProtoNumber(8) val modulesMenu: ModulesMenu = ModulesMenu(),
    @ProtoNumber(9) val repositoriesMenu: RepositoriesMenu = RepositoriesMenu(),
    @ProtoNumber(10) val useDoh: Boolean = false,
    @ProtoNumber(11) val confirmReboot: Boolean = true,
    @ProtoNumber(12) val terminalTextWrap: Boolean = false,
    @ProtoNumber(13) val datePattern: String = "d MMMM yyyy",
    @Deprecated("Replaced by RepositoryService.isActive")
    @ProtoNumber(14) val autoUpdateRepos: Boolean = false,
    @ProtoNumber(15) val autoUpdateReposInterval: Long = 6,
    @Deprecated("Replaced by ModuleService.isActive")
    @ProtoNumber(16) val checkModuleUpdates: Boolean = false,
    @ProtoNumber(17) val checkModuleUpdatesInterval: Long = 6,
    @ProtoNumber(18) val checkAppUpdates: Boolean = true,
    @ProtoNumber(19) val checkAppUpdatesPreReleases: Boolean = false,
    @ProtoNumber(20) val hideFingerprintInHome: Boolean = true,
    @ProtoNumber(21) val webUiDevUrl: String = "https://127.0.0.1:8080",
    @ProtoNumber(22) val developerMode: Boolean = false,
    @ProtoNumber(23) val useWebUiDevUrl: Boolean = false,
    @ProtoNumber(24) val useShellForModuleStateChange: Boolean = false,
    @ProtoNumber(25) val useShellForModuleAction: Boolean = true,
    @ProtoNumber(26) val clearInstallTerminal: Boolean = true,
    @ProtoNumber(27) val allowCancelInstall: Boolean = false,
    @ProtoNumber(28) val allowCancelAction: Boolean = false,
    @ProtoNumber(29) val blacklistAlerts: Boolean = true,
    @Deprecated("This is no longer used")
    @ProtoNumber(30) val injectEruda: List<String> = emptyList(),
    @Deprecated("This is no longer used")
    @ProtoNumber(31) val allowedFsModules: List<String> = emptyList(),
    @Deprecated("This is no longer used")
    @ProtoNumber(32) val allowedKsuModules: List<String> = emptyList(),
    @Deprecated("Replaced by ProviderService.isActive")
    @ProtoNumber(33) val useProviderAsBackgroundService: Boolean = false,
    @ProtoNumber(34) val strictMode: Boolean = true,
    @ProtoNumber(35) val enableErudaConsole: Boolean = false,
    @ProtoNumber(36) val enableToolbarEvents: Boolean = true,
    @ProtoNumber(37) val webuiEngine: WebUIEngine = WebUIEngine.PREFER_MODULE,
    @ProtoNumber(38) val showTerminalLineNumbers: Boolean = true,
    @ProtoNumber(39) val devAlwaysShowUpdateAlert: Boolean = false,
    @ProtoNumber(40) val webuixPackageName: String = "com.dergoogler.mmrl.wx${if (BuildConfig.DEBUG) ".debug" else ""}",
    @ProtoNumber(41) val enableBlur: Boolean = false,
    @ProtoNumber(42) val hideBottomBarLabels: Boolean = false,
    @ProtoNumber(43) val superUserMenu: SuperUserMenu = SuperUserMenu(),
    @ProtoNumber(44) val repositoryServiceEnabled: Boolean = false,
    @ProtoNumber(45) val moduleServiceEnabled: Boolean = true,
    @ProtoNumber(46) val providerServiceEnabled: Boolean = false,
    @ProtoNumber(47) val notifiedModuleUpdates: Set<String> = emptySet(),
    @ProtoNumber(48) val taskerIntegrationEnabled: Boolean = false,
    @ProtoNumber(49) val taskerAllowDownloads: Boolean = true,
    @ProtoNumber(50) val taskerAllowStateChanges: Boolean = false,
    @ProtoNumber(51) val taskerAllowModuleActions: Boolean = false,
    @ProtoNumber(52) val taskerAllowRemovals: Boolean = false,
    @ProtoNumber(53) val taskerAllowReviewedInstalls: Boolean = false,
    @ProtoNumber(54) val taskerApprovalPolicy: TaskerApprovalPolicy = TaskerApprovalPolicy.ALWAYS_ASK,
    @ProtoNumber(55) val taskerAllowedModules: Set<String> = emptySet(),
    /** Stable palette ID. Empty means this profile still needs legacy integer migration. */
    @ProtoNumber(56) val themePaletteId: String = "",
    @ProtoNumber(57) val themeColorSource: ThemeColorSource = ThemeColorSource.BUILT_IN,
    @ProtoNumber(58) val dynamicFallbackPaletteId: String = ThemeRegistry.NORD_ID,
    @ProtoNumber(59) val themeSurfaceStyle: ThemeSurfaceStyle = ThemeSurfaceStyle.FLAT,
    @ProtoNumber(60) val themeContrast: ThemeContrast = ThemeContrast.STANDARD,
    @ProtoNumber(61) val themePureBlack: Boolean = false,
    @ProtoNumber(62) val themeAccentIntensity: Float = 0.72f,
    @ProtoNumber(63) val enhancedStatusDistinction: Boolean = true,
    @ProtoNumber(64) val batterySaverForcesDark: Boolean = false,
    @ProtoNumber(65) val customThemeJson: String = "",
    @ProtoNumber(66) val ashHealthChecksEnabled: Boolean = true,
    @ProtoNumber(67) val ashHealthCheckIntervalHours: Long = 6,
    @ProtoNumber(68) val ashIncidentNotifications: Boolean = true,
    @ProtoNumber(69) val ashRebootReminders: Boolean = true,
    @ProtoNumber(70) val ashRestorationReminders: Boolean = true,
    @ProtoNumber(71) val taskerAllowAshRecovery: Boolean = false,
) {
    fun isDarkMode(context: Context? = null): Boolean {
        val batteryForcesDark = batterySaverForcesDark && context?.let {
            (it.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode
        } == true
        if (batteryForcesDark) return true
        return when (darkMode) {
            DarkMode.AlwaysOff -> false
            DarkMode.AlwaysOn -> true
            DarkMode.FollowSystem -> isSystemInDarkTheme()
        }
    }

    fun resolvedThemePaletteId(): String =
        themePaletteId.takeIf { it.isNotBlank() } ?: ThemeRegistry.migrateLegacyId(themeColor)

    fun resolvedThemeColorSource(): ThemeColorSource =
        if (themePaletteId.isBlank() && themeColor == LEGACY_DYNAMIC_THEME_ID) {
            ThemeColorSource.DYNAMIC_FULL
        } else {
            themeColorSource
        }

    private fun isSystemInDarkTheme(): Boolean {
        val uiMode = Resources.getSystem().configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun encodeTo(output: OutputStream) =
        output.write(
            ProtoBuf.encodeToByteArray(this),
        )

    @OptIn(ExperimentalContracts::class)
    fun developerMode(also: UserPreferences.() -> Boolean): Boolean {
        contract {
            callsInPlace(also, InvocationKind.AT_MOST_ONCE)
        }

        return developerMode && also()
    }

    companion object {
        /** Runtime Android path retained for callers that need Environment resolution. */
        val PUBLIC_DOWNLOADS: File by lazy(LazyThreadSafetyMode.PUBLICATION) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: File(DEFAULT_PUBLIC_DOWNLOADS_PATH)
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun decodeFrom(input: InputStream): UserPreferences = ProtoBuf.decodeFromByteArray(input.readBytes())
    }
}
