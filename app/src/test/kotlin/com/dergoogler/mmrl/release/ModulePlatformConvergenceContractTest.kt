package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModulePlatformConvergenceContractTest {
    private fun root(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (current.parentFile != null) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile
        }
        error("Could not locate repository root")
    }

    private fun source(relative: String) = File(root(), relative).readText()

    @Test
    fun `LSPosed repository presentation follows upstream metadata semantics`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        assertTrue(models.contains("get() = repositoryTitle ?: fallbackDisplayName(name)"))
        assertTrue(models.contains("get() = repositorySummary"))
        assertFalse(models.contains("get() = summary?.takeIf { it.isNotBlank() }\n            ?: name.substringAfterLast"))
    }

    @Test
    fun `LSPosed cache validates remote generation before publication`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")
        val parse = repository.indexOf("val parsed = parseRepositoryModules(remote.body)")
        val publish = repository.indexOf("writeAtomic(cache, remote.body)")
        assertTrue(parse >= 0)
        assertTrue(publish > parse)
    }

    @Test
    fun `partial update refresh preserves known state and GitHub download cleans partials`() {
        val worker = source("app/src/main/kotlin/com/dergoogler/mmrl/service/ModuleUpdateWorker.kt")
        assertTrue(worker.contains("RefreshBatchPolicy.mergeObservedKeys"))
        val github = source("app/src/main/kotlin/com/dergoogler/mmrl/github/GitHubModuleResolver.kt")
        assertTrue(github.contains("GitHubReleaseSelectionPolicy.select"))
        assertTrue(github.contains("catch (error: Throwable)"))
        assertTrue(github.contains("destination.delete()"))
    }
}
