package com.dergoogler.mmrl.lsposed

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedScopeEditorContractTest {
    @Test
    fun `scope writes are transaction guarded and backed up`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedScopeRepository.kt")

        assertTrue(repository.contains("beginTransaction()"))
        assertTrue(repository.contains("setTransactionSuccessful()"))
        assertTrue(repository.contains("SQLiteDatabase.OPEN_READWRITE"))
        assertTrue(repository.contains("mmrl-bak-"))
        assertTrue(repository.contains("cp -p"))
        assertTrue(repository.contains("backup-wal"))
        assertTrue(repository.contains("backup-shm"))
        assertTrue(repository.contains("rm -f"))
    }

    @Test
    fun `scope planner sanitizes package targets before root writes`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")

        assertTrue(models.contains("object LsposedScopePlanner"))
        assertTrue(models.contains("PACKAGE_RE.matches(module.packageName)"))
        assertTrue(models.contains("distinctBy { \"${'$'}{it.userId}:${'$'}{it.packageName}\" }"))
    }

    @Test
    fun `scope editor is explicit and provider refresh guidance is visible`() {
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/LsposedViewModel.kt")
        val screens = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedScreens.kt")

        assertTrue(viewModel.contains("applyScope("))
        assertTrue(viewModel.contains("repository.providerRefreshPlan(stateFlow.value.providerStatus)"))
        assertTrue(viewModel.contains("providerRefreshRecommended = true"))
        assertTrue(viewModel.contains("Open the manager to refresh provider state"))
        assertTrue(viewModel.contains("ModId.parseOrNull(moduleId)"))
        assertTrue(viewModel.contains("Event.RunProviderAction(canonicalId)"))
        assertTrue(viewModel.contains("Reboot if the provider does not refresh immediately"))
        assertTrue(screens.contains("LsposedScopeEditorDialog"))
        assertTrue(screens.contains("lsposed_scope_review_backup_notice"))
        assertTrue(screens.contains("lsposed_apply_scope_changes"))
        assertTrue(screens.contains("onRefreshProvider"))
        assertTrue(screens.contains("lsposed_refresh_provider"))
    }

    private fun source(path: String): String {
        val cwd = Paths.get(System.getProperty("user.dir") ?: error("user.dir is not set")).toAbsolutePath()
        val repoRoot = if (cwd.fileName.toString() == "app") cwd.parent else cwd
        return String(Files.readAllBytes(repoRoot.resolve(path)), StandardCharsets.UTF_8)
    }
}
