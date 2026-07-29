package com.dergoogler.mmrl.debug

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkbenchServiceLifecycleContractTest {
    private val root = repositoryRoot()

    @Test
    fun `debug repository refresh is one shot and service job is not duplicated`() {
        val service = source("app/src/main/kotlin/com/dergoogler/mmrl/service/RepositoryService.kt")
        val actions = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugActionRunner.kt")

        assertTrue(service.contains("private var repositoryJob: Job? = null"))
        assertTrue(service.contains("repositoryJob?.cancel()"))
        assertTrue(service.contains("ONE_SHOT_KEY"))
        assertTrue(service.contains("fun refreshOnce(context: Context)"))
        assertTrue(service.contains("return if (oneShot) START_NOT_STICKY else START_STICKY"))
        assertTrue(service.contains("stopSelf(startId)"))
        assertTrue(actions.contains("RepositoryService.refreshOnce(context)"))
        assertFalse(actions.contains("RepositoryService.start(context, interval = 1L)"))
    }

    @Test
    fun `ash rescue runtime has locator fallback before debug probes are needed`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/ash/data/AshRepository.kt")
        assertTrue(repository.contains("AshModuleLocator().inspect()"))
        assertTrue(repository.contains("locatorModuleStateRaw"))
        assertTrue(repository.contains("if (rootServiceInstalled) return rootServiceRaw"))
        assertTrue(repository.contains("return if (locatorInstalled) locatorRaw else rootServiceRaw"))
    }

    @Test
    fun `parked warnings are fixed`() {
        val tokenProbe = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/GitHubTokenDebugProbe.kt")
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        assertFalse(tokenProbe.contains("token!!"))
        assertTrue(tokenProbe.contains("token.orEmpty().length"))
        assertTrue(models.contains("@param:Json(name = \"scope\")"))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
