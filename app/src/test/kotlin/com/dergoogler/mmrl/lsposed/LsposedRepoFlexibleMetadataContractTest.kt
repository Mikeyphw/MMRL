package com.dergoogler.mmrl.lsposed

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedRepoFlexibleMetadataContractTest {
    private val root = repositoryRoot()

    @Test
    fun `LSPosed repository tolerates null scope and mixed additional authors`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        val repo = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")

        assertTrue(models.contains("@param:Json(name = \"scope\") val repositoryScope: List<String>? = null"))
        assertTrue(models.contains("val scope: List<String>"))
        assertTrue(models.contains("get() = repositoryScope.orEmpty()"))
        assertFalse(models.contains("val additionalAuthors: List<String>"))
        assertFalse(models.contains("additionalAuthors = detail.additionalAuthors"))
        assertTrue(repo.contains("backup.modules.lsposed.org/modules.json"))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
