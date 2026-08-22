package com.dergoogler.mmrl.debug

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkbenchContractTest {
    private val root = repositoryRoot()

    @Test
    fun `debug workbench is reachable from developer settings`() {
        val developer = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/developer/DeveloperScreen.kt")
        val screen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/debug/DebugWorkbenchScreen.kt")

        assertTrue(developer.contains("DebugWorkbenchScreenDestination"))
        assertTrue(developer.contains("Debug Workbench"))
        assertTrue(developer.contains("enabled = userPreferences.developerMode"))
        assertTrue(screen.contains("Run read-only probes"))
        assertTrue(screen.contains("Copy redacted report"))
        assertTrue(screen.contains("DebugProbeRunner(context)"))
    }

    @Test
    fun `lsposed vector probes explain manager packages and provider files`() {
        val probe = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/LsposedDebugProbe.kt")

        assertTrue(probe.contains("LsposedRepository.LSPOSED_MANAGER_PACKAGES"))
        assertTrue(probe.contains("org.matrix.vector.manager"))
        assertTrue(probe.contains("$" + "packageName.LAUNCH_MANAGER"))
        assertTrue(probe.contains("/data/adb/modules"))
        assertTrue(probe.contains("/data/adb/modules_update"))
        assertTrue(probe.contains("action.sh"))
        assertTrue(probe.contains("manager.apk"))
        assertTrue(probe.contains("daemon.apk"))
        assertTrue(probe.contains("framework/lspd.dex"))
    }

    @Test
    fun `repo and token probes are read only and redacted`() {
        val runner = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugProbeRunner.kt")
        val repo = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/LsposedRepoDebugProbe.kt")
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/DebugModels.kt")

        assertTrue(runner.contains("GitHubTokenDebugProbe(context).run()"))
        assertTrue(runner.contains("LsposedRepoDebugProbe(context).endpointMatrixProbe()"))
        assertTrue(repo.contains("modules.lsposed.org/modules.json"))
        assertTrue(repo.contains("backup.modules.lsposed.org/modules.json"))
        assertTrue(repo.contains("jsDelivr main-index fallback"))
        assertTrue(repo.contains("cdn.jsdelivr.net/gh/Xposed-Modules-Repo/modules@gh-pages/modules.json"))
        assertTrue(models.contains("<github-token-redacted>"))
        assertFalse(models.contains("println("))

        assertEquals("Authorization: Bearer <redacted>", DebugRedactor.redact("Authorization: Bearer ghp_1234567890abcdef"))
        assertEquals("authorization=Bearer <redacted>", DebugRedactor.redact("authorization=Bearer secret-token-value"))
        assertEquals("Cookie: <redacted>; theme=dark", DebugRedactor.redact("Cookie: session=secret; theme=dark"))
    }

    @Test
    fun `debug workbench is documented as non mutating`() {
        val doc = source("docs/DEBUG_WORKBENCH.md")

        assertTrue(doc.contains("read-only"))
        assertTrue(doc.contains("must not mutate"))
        assertTrue(doc.contains("org.matrix.vector.manager") || source("app/src/main/kotlin/com/dergoogler/mmrl/debug/LsposedDebugProbe.kt").contains("org.matrix.vector.manager"))
        assertTrue(doc.contains("Authorization headers are redacted"))
        assertTrue(doc.contains("Root module paths are preserved"))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
