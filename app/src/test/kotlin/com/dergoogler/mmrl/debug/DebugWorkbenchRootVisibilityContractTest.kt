package com.dergoogler.mmrl.debug

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkbenchRootVisibilityContractTest {
    private val root = repositoryRoot()

    @Test
    fun `provider probe exposes root visibility evidence`() {
        val probe = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/LsposedDebugProbe.kt")

        assertTrue(probe.contains("SuFile"))
        assertTrue(probe.contains("rootSnapshot"))
        assertTrue(probe.contains("childrenPreview"))
        assertTrue(probe.contains("list error"))
        assertTrue(probe.contains("module.prop"))
        assertTrue(probe.contains("framework/lspd.dex"))
        assertTrue(probe.contains("visible manager-like package scan"))
    }

    @Test
    fun `runtime provider detection uses root aware file reads`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")

        assertTrue(repository.contains("import com.dergoogler.mmrl.platform.file.SuFile"))
        assertTrue(repository.contains("import com.dergoogler.mmrl.platform.file.useLines"))
        assertTrue(repository.contains("SuFile(root.absolutePath)"))
        assertTrue(repository.contains("rootFile.list()?.toList().orEmpty()"))
        assertTrue(repository.contains("suChild("))
        assertTrue(repository.contains("safeIsDirectory"))
        assertTrue(repository.contains("safeIsFile"))
        assertTrue(repository.contains("providerCandidateFromDirectory"))
        assertTrue(repository.contains("framework/lspd.dex"))
    }

    @Test
    fun `vector manager intent and package visibility are explicit`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")
        val probe = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/LsposedDebugProbe.kt")
        val manifest = source("app/src/main/AndroidManifest.xml")

        assertTrue(repository.contains("org.matrix.vector.manager"))
        assertTrue(repository.contains("org.matrix.vector.daemon") || manifest.contains("org.matrix.vector.daemon"))
        assertTrue(repository.contains("managerLaunchIntents"))
        assertTrue(repository.contains("$" + "packageName.LAUNCH_MANAGER"))
        assertTrue(repository.contains("org.lsposed.manager.LAUNCH_MANAGER"))
        assertTrue(repository.contains("resolveActivity(pm, intent)"))
        assertTrue(probe.contains("actionLaunchResolved"))
        assertTrue(probe.contains("QUERY_ALL_PACKAGES declared"))
        assertTrue(manifest.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertTrue(manifest.contains("org.matrix.vector.manager"))
        assertTrue(manifest.contains("org.matrix.vector.daemon"))
    }

    @Test
    fun `xposed repo runtime prefers backup before generated jsdelivr main index fallback`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")
        val repoProbe = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/LsposedRepoDebugProbe.kt")

        assertTrue(repository.contains("https://modules.lsposed.org/modules.json"))
        assertTrue(repository.contains("https://backup.modules.lsposed.org/modules.json"))
        assertTrue(repository.contains("https://cdn.jsdelivr.net/gh/Xposed-Modules-Repo/modules@gh-pages/modules.json"))
        assertTrue(repoProbe.contains("jsDelivr main-index fallback"))
        assertTrue(repoProbe.contains("backup.modules.lsposed.org is preferred after primary 403"))
    }

    @Test
    fun `phase 8 documentation explains support bundle evidence from root scans`() {
        val doc = source("docs/DEBUG_WORKBENCH.md")
        val phaseDoc = source("docs/DEBUG_WORKBENCH_ROOT_VISIBILITY.md")

        assertTrue(doc.contains("Phase 8 root visibility hardening"))
        assertTrue(phaseDoc.contains("/data/adb/modules"))
        assertTrue(phaseDoc.contains("children preview"))
        assertTrue(phaseDoc.contains("module.prop"))
        assertTrue(phaseDoc.contains("backup.modules.lsposed.org"))
        assertTrue(phaseDoc.contains("org.matrix.vector.manager"))
        assertTrue(phaseDoc.contains("read-only"))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
