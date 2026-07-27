package com.dergoogler.mmrl.lsposed

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedManagerSealContractTest {
    @Test
    fun `provider status models a sealed manager open mode`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")

        assertTrue(models.contains("enum class LsposedManagerOpenMode"))
        assertTrue(models.contains("INSTALLED_MANAGER"))
        assertTrue(models.contains("PROVIDER_ACTION"))
        assertTrue(models.contains("BUNDLED_MANAGER_APK"))
        assertTrue(models.contains("managerOpenMode: LsposedManagerOpenMode"))
        assertTrue(models.contains("managerOpenMode == LsposedManagerOpenMode.INSTALLED_MANAGER"))
        assertTrue(models.contains("managerOpenMode == LsposedManagerOpenMode.PROVIDER_ACTION"))
    }

    @Test
    fun `repository chooses installed manager then provider action then bundled apk`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")

        assertTrue(repository.contains("managerInstalled -> LsposedManagerOpenMode.INSTALLED_MANAGER"))
        assertTrue(repository.contains("active?.actionAvailable == true -> LsposedManagerOpenMode.PROVIDER_ACTION"))
        assertTrue(repository.contains("selected?.managerApkPresent == true -> LsposedManagerOpenMode.BUNDLED_MANAGER_APK"))
        assertTrue(repository.contains("""actionAvailable = active && File(directory, "action.sh").isFile"""))
        assertTrue(repository.contains("""managerApkPresent = File(directory, "manager.apk").isFile"""))
    }

    @Test
    fun `open lsposed still uses the provider action bridge fallback`() {
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/LsposedViewModel.kt")
        val screens = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")

        assertTrue(viewModel.contains("repository.lsposedManagerIntent()"))
        assertTrue(viewModel.contains("repository.lsposedProviderActionModuleId()"))
        assertTrue(viewModel.contains("Event.RunProviderAction(ModId(providerModuleId))"))
        assertTrue(screens.contains("providerStatus.managerOpenMode"))
        assertTrue(screens.contains("lsposed_provider_manager_action_bridge"))
        assertTrue(screens.contains("lsposed_provider_update_pending"))
    }

    @Test
    fun `manager seal is documented`() {
        val doc = source("docs/LSPOSED_MANAGER_SEAL.md")

        assertTrue(doc.contains("normal installed manager app launch intent"))
        assertTrue(doc.contains("active root provider action bridge"))
        assertTrue(doc.contains("bundled `manager.apk`"))
    }

    private fun source(path: String): String {
        val cwd = Paths.get(System.getProperty("user.dir") ?: error("user.dir is not set")).toAbsolutePath()
        val repoRoot = if (cwd.fileName.toString() == "app") cwd.parent else cwd
        return String(Files.readAllBytes(repoRoot.resolve(path)), StandardCharsets.UTF_8)
    }
}
