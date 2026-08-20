package com.dergoogler.mmrl.lsposed

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedScopeDiscoveryContractTest {
    @Test
    fun `scope discovery copies provider database instead of scraping manager ui`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedScopeRepository.kt")

        assertTrue(models.contains("/data/adb/lspd/modules_config.db"))
        assertTrue(repository.contains("copyConfigDbForRead"))
        assertTrue(repository.contains("SQLiteDatabase.OPEN_READONLY"))
        assertTrue(repository.contains("SELECT mid, module_pkg_name, apk_path, enabled"))
        assertTrue(repository.contains("SELECT app_pkg_name, user_id FROM scope"))
        assertTrue(repository.contains("withNewRootShell"))
    }

    @Test
    fun `root copy command shell quotes paths safely`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedScopeRepository.kt")

        assertTrue(repository.contains("""value.replace("'", "'\\''")"""))
    }

    @Test
    fun `repository tab can degrade while local provider state still loads`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/LsposedViewModel.kt")

        assertTrue(repository.contains("cache.takeIf { it.isFile && it.length() > 0L }?.readText()"))
        assertTrue(repository.contains(".orEmpty()"))
        assertTrue(repository.contains("""header("Accept", "application/json")"""))
        assertTrue(viewModel.contains("val providerStatus = repository.providerStatus()"))
        assertTrue(viewModel.contains("val scopeState = repository.scopeState()"))
        assertTrue(viewModel.contains("val moduleState = modulesResult.getOrNull()"))
        assertTrue(viewModel.contains("val modules = moduleState?.modules.orEmpty()"))
    }

    @Test
    fun `installed lsposed cards expose read only scope state`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        val screens = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")

        assertTrue(models.contains("val scope: LsposedModuleScope? = null"))
        assertTrue(screens.contains("LsposedScopeDetailsDialog"))
        assertTrue(screens.contains("lsposed_view_scope"))
        assertTrue(screens.contains("LsposedProviderStatusCard"))
    }

    private fun source(path: String): String {
        val cwd = Paths.get(System.getProperty("user.dir") ?: error("user.dir is not set")).toAbsolutePath()
        val repoRoot = if (cwd.fileName.toString() == "app") cwd.parent else cwd
        return String(Files.readAllBytes(repoRoot.resolve(path)), StandardCharsets.UTF_8)
    }
}
