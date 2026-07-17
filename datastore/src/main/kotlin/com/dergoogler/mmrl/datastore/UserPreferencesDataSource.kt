package com.dergoogler.mmrl.datastore

import androidx.datastore.core.DataStore
import com.dergoogler.mmrl.datastore.model.DarkMode
import com.dergoogler.mmrl.datastore.model.Homepage
import com.dergoogler.mmrl.datastore.model.ModulesMenu
import com.dergoogler.mmrl.datastore.model.RepositoriesMenu
import com.dergoogler.mmrl.datastore.model.RepositoryMenu
import com.dergoogler.mmrl.datastore.model.SuperUserMenu
import com.dergoogler.mmrl.datastore.model.UserPreferences
import com.dergoogler.mmrl.datastore.model.WebUIEngine
import com.dergoogler.mmrl.datastore.model.WorkingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

class UserPreferencesDataSource
    @Inject
    constructor(
        private val userPreferences: DataStore<UserPreferences>,
    ) {
        val data get() = userPreferences.data

        suspend fun setWorkingMode(value: WorkingMode) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        workingMode = value,
                    )
                }
            }

        suspend fun setDarkTheme(value: DarkMode) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        darkMode = value,
                    )
                }
            }

        suspend fun setThemeColor(value: Int) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        themeColor = value,
                    )
                }
            }

        suspend fun setThemePaletteId(value: String) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(themePaletteId = value)
                }
            }

        suspend fun setThemeColorSource(value: com.dergoogler.mmrl.ui.theme.ThemeColorSource) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        themeColorSource = value,
                        themePaletteId = it.themePaletteId.ifBlank {
                            com.dergoogler.mmrl.ui.theme.ThemeRegistry.migrateLegacyId(it.themeColor)
                        },
                    )
                }
            }

        suspend fun setDynamicFallbackPaletteId(value: String) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(dynamicFallbackPaletteId = value)
                }
            }

        suspend fun setThemeSurfaceStyle(value: com.dergoogler.mmrl.ui.theme.ThemeSurfaceStyle) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(themeSurfaceStyle = value)
                }
            }

        suspend fun setThemeContrast(value: com.dergoogler.mmrl.ui.theme.ThemeContrast) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(themeContrast = value)
                }
            }

        suspend fun setThemePureBlack(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(themePureBlack = value)
                }
            }

        suspend fun setThemeAccentIntensity(value: Float) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(themeAccentIntensity = value.coerceIn(0f, 1f))
                }
            }

        suspend fun setEnhancedStatusDistinction(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(enhancedStatusDistinction = value)
                }
            }

        suspend fun setBatterySaverForcesDark(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(batterySaverForcesDark = value)
                }
            }

        suspend fun setCustomThemeJson(value: String) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(customThemeJson = value)
                }
            }

        suspend fun setDeleteZipFile(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        deleteZipFile = value,
                    )
                }
            }

        suspend fun setUseDoh(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        useDoh = value,
                    )
                }
            }

        suspend fun setDownloadPath(value: String) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        downloadPath = value,
                    )
                }
            }

        suspend fun setConfirmReboot(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        confirmReboot = value,
                    )
                }
            }

        suspend fun setTerminalTextWrap(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        terminalTextWrap = value,
                    )
                }
            }

        suspend fun setDatePattern(value: String) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        datePattern = value,
                    )
                }
            }

        suspend fun setAutoUpdateRepos(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        autoUpdateRepos = value,
                    )
                }
            }

        suspend fun setAutoUpdateReposInterval(value: Long) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        autoUpdateReposInterval = value,
                    )
                }
            }

        suspend fun setCheckModuleUpdates(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        checkModuleUpdates = value,
                    )
                }
            }

        suspend fun setEnableBlur(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        enableBlur = value,
                    )
                }
            }

        suspend fun setCheckModuleUpdatesInterval(value: Long) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        checkModuleUpdatesInterval = value,
                    )
                }
            }

        suspend fun setCheckAppUpdates(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        checkAppUpdates = value,
                    )
                }
            }

        suspend fun setCheckAppUpdatesPreReleases(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        checkAppUpdatesPreReleases = value,
                    )
                }
            }

        suspend fun setHideFingerprintInHome(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        hideFingerprintInHome = value,
                    )
                }
            }

        suspend fun setStrictMode(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        strictMode = value,
                    )
                }
            }

        suspend fun setHomepage(value: Homepage) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        homepage = value,
                    )
                }
            }

        suspend fun setWebUiDevUrl(value: String) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        webUiDevUrl = value,
                    )
                }
            }

        suspend fun setWebuixPackageName(value: String) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        webuixPackageName = value,
                    )
                }
            }

        suspend fun setDeveloperMode(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        developerMode = value,
                    )
                }
            }

        suspend fun setUseWebUiDevUrl(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        useWebUiDevUrl = value,
                    )
                }
            }

        suspend fun setUseShellForModuleStateChange(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        useShellForModuleStateChange = value,
                    )
                }
            }

        suspend fun setUseShellForModuleAction(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        useShellForModuleAction = value,
                    )
                }
            }

        suspend fun setClearInstallTerminal(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        clearInstallTerminal = value,
                    )
                }
            }

        suspend fun setAllowCancelInstall(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        allowCancelInstall = value,
                    )
                }
            }

        suspend fun setAllowCancelAction(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        allowCancelAction = value,
                    )
                }
            }

        suspend fun setBlacklistAlerts(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        blacklistAlerts = value,
                    )
                }
            }

        suspend fun setInjectEruda(value: List<String>) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        injectEruda = value,
                    )
                }
            }

        suspend fun setAllowedFsModules(value: List<String>) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        allowedFsModules = value,
                    )
                }
            }

        suspend fun setAllowedKsuModules(value: List<String>) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        allowedKsuModules = value,
                    )
                }
            }

        suspend fun setRepositoryMenu(value: RepositoryMenu) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        repositoryMenu = value,
                    )
                }
            }

        suspend fun setSuperUserMenu(value: SuperUserMenu) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        superUserMenu = value,
                    )
                }
            }

        suspend fun setRepositoriesMenu(value: RepositoriesMenu) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        repositoriesMenu = value,
                    )
                }
            }

        suspend fun setModulesMenu(value: ModulesMenu) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        modulesMenu = value,
                    )
                }
            }

        suspend fun setEnableEruda(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        enableErudaConsole = value,
                    )
                }
            }

        suspend fun setEnableToolbarEvents(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        enableToolbarEvents = value,
                    )
                }
            }

        suspend fun setShowTerminalLineNumbers(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        showTerminalLineNumbers = value,
                    )
                }
            }

        suspend fun setDevAlwaysShowUpdateAlert(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        devAlwaysShowUpdateAlert = value,
                    )
                }
            }

        suspend fun setHideBottomBarLabels(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        hideBottomBarLabels = value,
                    )
                }
            }

        suspend fun setWebUIEngine(value: WebUIEngine) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        webuiEngine = value,
                    )
                }
            }

        suspend fun setRepositoryServiceEnabled(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        repositoryServiceEnabled = value,
                    )
                }
            }

        suspend fun setModuleServiceEnabled(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        moduleServiceEnabled = value,
                    )
                }
            }

        suspend fun setProviderServiceEnabled(value: Boolean) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(
                        providerServiceEnabled = value,
                    )
                }
            }

        suspend fun replaceNotifiedModuleUpdates(values: Set<String>) =
            withContext(Dispatchers.IO) {
                userPreferences.updateData {
                    it.copy(notifiedModuleUpdates = values)
                }
            }

        suspend fun clearNotifiedModuleUpdate(moduleId: String) =
            withContext(Dispatchers.IO) {
                val normalizedId = moduleId.trim().lowercase(Locale.ROOT)
                userPreferences.updateData { preferences ->
                    preferences.copy(
                        notifiedModuleUpdates =
                            preferences.notifiedModuleUpdates.filterNot { key ->
                                key.substringBeforeLast(':') == normalizedId
                            }.toSet(),
                    )
                }
            }

        suspend fun setTaskerIntegrationEnabled(value: Boolean) = update { copy(taskerIntegrationEnabled = value) }
        suspend fun setTaskerAllowDownloads(value: Boolean) = update { copy(taskerAllowDownloads = value) }
        suspend fun setTaskerAllowStateChanges(value: Boolean) = update { copy(taskerAllowStateChanges = value) }
        suspend fun setTaskerAllowModuleActions(value: Boolean) = update { copy(taskerAllowModuleActions = value) }
        suspend fun setTaskerAllowRemovals(value: Boolean) = update { copy(taskerAllowRemovals = value) }
        suspend fun setTaskerAllowReviewedInstalls(value: Boolean) = update { copy(taskerAllowReviewedInstalls = value) }
        suspend fun setTaskerApprovalPolicy(value: com.dergoogler.mmrl.datastore.model.TaskerApprovalPolicy) = update { copy(taskerApprovalPolicy = value) }
        suspend fun setTaskerAllowedModules(value: Set<String>) = update { copy(taskerAllowedModules = value) }

        suspend fun setAshHealthChecksEnabled(value: Boolean) = update { copy(ashHealthChecksEnabled = value) }
        suspend fun setAshHealthCheckIntervalHours(value: Long) = update {
            copy(ashHealthCheckIntervalHours = value.coerceIn(1L, 24L))
        }
        suspend fun setAshIncidentNotifications(value: Boolean) = update { copy(ashIncidentNotifications = value) }
        suspend fun setAshRebootReminders(value: Boolean) = update { copy(ashRebootReminders = value) }
        suspend fun setAshRestorationReminders(value: Boolean) = update { copy(ashRestorationReminders = value) }

        private suspend fun update(block: com.dergoogler.mmrl.datastore.model.UserPreferences.() -> com.dergoogler.mmrl.datastore.model.UserPreferences) =
            withContext(Dispatchers.IO) { userPreferences.updateData { it.block() } }
}
