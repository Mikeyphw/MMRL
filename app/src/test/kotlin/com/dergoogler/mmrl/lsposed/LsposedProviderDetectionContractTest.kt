package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LsposedProviderDetectionContractTest {
    private val root = File(System.getProperty("user.dir") ?: error("user.dir is not set"))
        .let { workingDirectory ->
            if (File(workingDirectory, "app/src/main").isDirectory) workingDirectory
            else workingDirectory.parentFile ?: workingDirectory
        }

    @Test
    fun `vector root module is treated as lsposed provider`() {
        val source = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")

        assertTrue(source.contains("zygisk_vector"))
        assertTrue(source.contains("framework/lspd.dex"))
        assertTrue(source.contains("manager.apk"))
        assertTrue(source.contains("xposed-compatible"))
        assertTrue(source.contains("lsposedProviderActionModuleId"))
    }

    @Test
    fun `open lsposed can fall back to provider module action`() {
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/LsposedViewModel.kt")
        val screens = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")

        assertTrue(viewModel.contains("RunProviderAction"))
        assertTrue(viewModel.contains("repository.lsposedProviderActionModuleId()"))
        assertTrue(screens.contains("ActionActivity.start(context, event.moduleId)"))
    }

    @Test
    fun `provider status counts vector action as manager availability`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/LsposedViewModel.kt")

        assertTrue(models.contains("data class LsposedProviderStatus"))
        assertTrue(models.contains("managerPackageInstalled || actionAvailable"))
        assertTrue(viewModel.contains("managerAvailable = providerStatus.canOpen"))
    }


    private fun source(path: String): String =
        String(File(root, path).readBytes(), Charsets.UTF_8)
}
