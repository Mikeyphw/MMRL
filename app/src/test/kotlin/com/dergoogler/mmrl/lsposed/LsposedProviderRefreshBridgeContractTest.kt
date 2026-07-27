package com.dergoogler.mmrl.lsposed

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedProviderRefreshBridgeContractTest {
    @Test
    fun `provider refresh plan is explicit and guarded`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")

        assertTrue(models.contains("enum class LsposedProviderRefreshMode"))
        assertTrue(models.contains("OPEN_MANAGER"))
        assertTrue(models.contains("ACTION_BRIDGE"))
        assertTrue(models.contains("REBOOT_REQUIRED"))
        assertTrue(models.contains("data class LsposedProviderRefreshPlan"))
        assertTrue(models.contains("refreshBridgeAvailable"))
        assertTrue(repository.contains("fun providerRefreshPlan"))
        assertTrue(repository.contains("status.managerPackageInstalled -> LsposedProviderRefreshPlan(LsposedProviderRefreshMode.OPEN_MANAGER)"))
        assertTrue(repository.contains("status.active && status.actionAvailable"))
        assertTrue(repository.contains("LsposedProviderRefreshMode.ACTION_BRIDGE"))
        assertTrue(repository.contains("LsposedProviderRefreshMode.REBOOT_REQUIRED"))
    }

    @Test
    fun `scope writes surface refresh guidance and action`() {
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/LsposedViewModel.kt")
        val screens = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")
        val strings = source("app/src/main/res/values/strings.xml")

        assertTrue(viewModel.contains("providerRefreshRecommended = true"))
        assertTrue(viewModel.contains("repository.providerRefreshPlan(stateFlow.value.providerStatus)"))
        assertTrue(viewModel.contains("fun refreshLsposedProvider()"))
        assertTrue(viewModel.contains("Event.RunProviderAction(ModId(moduleId))"))
        assertTrue(screens.contains("onRefreshProvider"))
        assertTrue(screens.contains("providerStatus.refreshBridgeAvailable"))
        assertTrue(screens.contains("lsposed_refresh_provider"))
        assertTrue(strings.contains("lsposed_provider_refresh_ready"))
        assertTrue(strings.contains("lsposed_provider_refresh_reboot_required"))
    }

    @Test
    fun `provider refresh bridge is documented`() {
        val doc = source("docs/LSPOSED_PROVIDER_REFRESH_BRIDGE.md")

        assertTrue(doc.contains("installed manager"))
        assertTrue(doc.contains("active provider action bridge"))
        assertTrue(doc.contains("reboot fallback"))
    }

    private fun source(path: String): String {
        val cwd = Paths.get(System.getProperty("user.dir") ?: error("user.dir is not set")).toAbsolutePath()
        val repoRoot = if (cwd.fileName.toString() == "app") cwd.parent else cwd
        return String(Files.readAllBytes(repoRoot.resolve(path)), StandardCharsets.UTF_8)
    }
}
