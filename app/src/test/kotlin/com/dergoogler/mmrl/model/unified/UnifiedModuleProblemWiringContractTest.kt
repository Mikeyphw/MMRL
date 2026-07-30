package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class UnifiedModuleProblemWiringContractTest {
    private val projectRoot: Path = Paths.get(System.getProperty("user.dir") ?: ".").let { cwd ->
        if (cwd.fileName?.toString() == "app") cwd.parent else cwd
    }

    private fun source(path: String): String =
        String(Files.readAllBytes(projectRoot.resolve(path)), StandardCharsets.UTF_8)

    @Test
    fun `view model exposes problem reports derived from unified modules`() {
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/ModulesViewModel.kt")
        assertTrue(viewModel.contains("UnifiedModuleProblemCenter::build"))
        assertTrue(viewModel.contains("val unifiedProblemReport"))
        assertTrue(viewModel.contains("val filteredUnifiedProblemReport"))
    }

    @Test
    fun `problem model carries roadmap diagnostics and action kinds`() {
        val model = source("app/src/main/kotlin/com/dergoogler/mmrl/model/unified/UnifiedModuleProblems.kt")
        assertTrue(model.contains("PRIMARY_REPO_403"))
        assertTrue(model.contains("BACKUP_REPO_FALLBACK"))
        assertTrue(model.contains("MALFORMED_REPO_ENTRIES"))
        assertTrue(model.contains("CACHE_FALLBACK"))
        assertTrue(model.contains("GITHUB_ARTIFACT_EXPIRED"))
        assertTrue(model.contains("GITHUB_TOKEN_REQUIRED"))
        assertTrue(model.contains("GITHUB_REGEX_MISMATCH"))
        assertTrue(model.contains("MANAGER_UNAVAILABLE"))
        assertTrue(model.contains("PROVIDER_BRIDGE_AVAILABLE"))
        assertTrue(model.contains("SCOPE_DB_UNAVAILABLE"))
        assertTrue(model.contains("INSTALLED_NOT_IN_REPOSITORY"))
        assertTrue(model.contains("ALIAS_MATCH_ONLY"))
        assertTrue(model.contains("FAILED_UPDATE"))
        assertTrue(model.contains("RUN_PROBE"))
        assertTrue(model.contains("COPY_EVIDENCE"))
        assertTrue(model.contains("SUGGEST_FIX"))
    }
}
