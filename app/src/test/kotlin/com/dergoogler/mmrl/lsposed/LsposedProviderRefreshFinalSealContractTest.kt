package com.dergoogler.mmrl.lsposed

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedProviderRefreshFinalSealContractTest {
    @Test
    fun `scope provider repository token and refresh stack remains sealed`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")
        val scopeRepository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedScopeRepository.kt")
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/LsposedViewModel.kt")
        val screens = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")
        val settings = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/other/OtherScreen.kt")
        val preferences = source("datastore/src/main/kotlin/com/dergoogler/mmrl/datastore/model/UserPreferences.kt")

        assertTrue(models.contains("enum class LsposedManagerOpenMode"))
        assertTrue(models.contains("enum class LsposedProviderRefreshMode"))
        assertTrue(models.contains("data class LsposedProviderRefreshPlan"))
        assertTrue(models.contains("refreshBridgeAvailable"))

        assertTrue(repository.contains("\"zygisk_vector\""))
        assertTrue(repository.contains("\"zygisk_lsposed\""))
        assertTrue(repository.contains("\"riru_lsposed\""))
        assertTrue(repository.contains("\"lsposed\""))
        assertTrue(repository.contains("managerInstalled -> LsposedManagerOpenMode.INSTALLED_MANAGER"))
        assertTrue(repository.contains("active?.actionAvailable == true -> LsposedManagerOpenMode.PROVIDER_ACTION"))
        assertTrue(repository.contains("selected?.managerApkPresent == true -> LsposedManagerOpenMode.BUNDLED_MANAGER_APK"))
        assertTrue(repository.contains("fun providerRefreshPlan"))
        assertTrue(repository.contains("LsposedProviderRefreshMode.OPEN_MANAGER"))
        assertTrue(repository.contains("LsposedProviderRefreshMode.ACTION_BRIDGE"))
        assertTrue(repository.contains("LsposedProviderRefreshMode.REBOOT_REQUIRED"))
        assertTrue(repository.contains("GitHubTokenStore(context)"))
        assertTrue(repository.contains("LSPOSED_MODULES_FALLBACK_URLS"))
        assertTrue(repository.contains("cdn.jsdelivr.net/gh/Xposed-Modules-Repo/modules@gh-pages/modules.json"))
        assertTrue(repository.contains("applyGitHubAuthentication(url, githubTokenStore.getToken())"))
        assertTrue(repository.contains("HTTP 403 from modules.lsposed.org; mirror fallback will be tried"))

        assertTrue(scopeRepository.contains("SQLiteDatabase.OPEN_READONLY"))
        assertTrue(scopeRepository.contains("db.beginTransaction()"))
        assertTrue(scopeRepository.contains("db.setTransactionSuccessful()"))
        assertTrue(scopeRepository.contains("mmrl-bak-"))
        assertTrue(scopeRepository.contains("db-wal"))
        assertTrue(scopeRepository.contains("db-shm"))
        assertTrue(scopeRepository.contains("rm -f"))
        assertFalse(scopeRepository.contains("rm -rf"))

        assertTrue(viewModel.contains("providerRefreshRecommended = true"))
        assertTrue(viewModel.contains("fun refreshLsposedProvider()"))
        assertTrue(viewModel.contains("ModId.parseOrNull(moduleId)"))
        assertTrue(viewModel.contains("Event.RunProviderAction(canonicalId)"))
        assertTrue(screens.contains("onRefreshProvider = viewModel::refreshLsposedProvider"))
        assertTrue(screens.contains("enabled = providerStatus.refreshBridgeAvailable"))
        assertTrue(screens.contains("lsposed_refresh_provider"))

        assertTrue(settings.contains("GitHubTokenStore(context)"))
        assertTrue(settings.contains("settings_github_api_token"))
        assertTrue(settings.contains("githubTokenEditorRevision++"))
        assertFalse(preferences.contains("githubApiToken"))

    }

    @Test
    fun `documentation seals the completed roadmap`() {
        val finalSeal = source("docs/LSPOSED_FINAL_INTEGRATION_SEAL.md")
        val scopeEditor = source("docs/LSPOSED_SCOPE_EDITOR.md")
        val refreshBridge = source("docs/LSPOSED_PROVIDER_REFRESH_BRIDGE.md")
        val roadmapSeal = source("docs/LSPOSED_SCOPE_PROVIDER_FINAL_SEAL.md")
        val tokenHotfix = source("docs/REPOSITORY_TOKEN_HOTFIX.md")

        assertTrue(finalSeal.contains("scope discovery"))
        assertTrue(finalSeal.contains("provider refresh bridge"))
        assertTrue(finalSeal.contains("GitHub API token"))
        assertTrue(scopeEditor.contains("Refresh provider"))
        assertFalse(scopeEditor.contains("reopen LSPosed or reboot"))
        assertTrue(refreshBridge.contains("installed manager"))
        assertTrue(refreshBridge.contains("active provider action bridge"))
        assertTrue(refreshBridge.contains("reboot fallback"))
        assertTrue(tokenHotfix.contains("one token can reduce GitHub 403/rate-limit failures across the app"))
        assertTrue(roadmapSeal.contains("Scope discovery"))
        assertTrue(roadmapSeal.contains("Guarded scope editor"))
        assertTrue(roadmapSeal.contains("LSPosed manager seal"))
        assertTrue(roadmapSeal.contains("Provider refresh bridge"))
        assertTrue(roadmapSeal.contains("Repository/token hotfix"))
        assertTrue(roadmapSeal.contains("Final integration/regression seal"))
    }

    private fun source(path: String): String = repositoryRoot().resolve(path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
