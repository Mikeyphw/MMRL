package com.dergoogler.mmrl.lsposed

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedVectorManagerRepoContractTest {
    private val root = repositoryRoot()

    @Test
    fun `vector manager package is visible and recognized as a manager`() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")

        assertTrue(manifest.contains("org.matrix.vector.manager"))
        assertTrue(repository.contains("org.matrix.vector.manager"))
        assertTrue(repository.contains("managerCategoryLaunchIntent"))
        assertTrue(repository.contains("${'$'}packageName.LAUNCH_MANAGER"))
        assertTrue(repository.contains("Intent(Intent.ACTION_MAIN)"))
        assertTrue(repository.contains("Intent.CATEGORY_DEFAULT"))
        assertTrue(repository.contains("packageInstalled(pm, packageName)"))
    }

    @Test
    fun `repository tries primary backup and generated mirrors in order`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")

        assertTrue(repository.contains("https://modules.lsposed.org/modules.json"))
        assertTrue(repository.contains("https://backup.modules.lsposed.org/modules.json"))
        assertTrue(repository.contains("https://cdn.jsdelivr.net/gh/Xposed-Modules-Repo/modules@gh-pages/modules.json"))
        assertTrue(repository.contains("https://backup.modules.lsposed.org/module/${'$'}packageName.json"))
        assertTrue(repository.contains("lsposedModuleFallbackUrls(packageName)"))
        assertTrue(repository.contains("applyGitHubAuthentication(url, githubTokenStore.getToken())"))
    }

    @Test
    fun `vector manager hotfix is documented`() {
        val doc = source("docs/LSPOSED_VECTOR_MANAGER_REPO_HOTFIX.md")

        assertTrue(doc.contains("org.matrix.vector.manager"))
        assertTrue(doc.contains("org.matrix.vector.manager.LAUNCH_MANAGER"))
        assertTrue(doc.contains("backup.modules.lsposed.org"))
        assertTrue(doc.contains("without leaking the token"))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
