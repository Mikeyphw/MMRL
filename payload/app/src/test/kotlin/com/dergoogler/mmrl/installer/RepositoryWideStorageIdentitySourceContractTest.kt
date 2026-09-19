package com.dergoogler.mmrl.installer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepositoryWideStorageIdentitySourceContractTest {
    private fun source(path: String): String = File("src/main/kotlin/com/dergoogler/mmrl/$path").readText()

    @Test fun taskerExportNamesCannotCollideAfterCharacterReplacement() {
        val text = source("tasker/TaskerAutomationPolicy.kt")
        assertTrue(text.contains("MessageDigest.getInstance(\"SHA-256\")"))
        assertTrue(text.contains("return \"\$base-\$digest.zip\""))
    }

    @Test fun githubMaterializationNamesCarryOpaqueCollisionSuffixes() {
        listOf("github/GitHubArtifactArchivePolicy.kt", "github/GitHubModuleResolver.kt").forEach { path ->
            val text = source(path)
            assertTrue(text.contains("MessageDigest.getInstance(\"SHA-256\")"))
            assertTrue(text.contains("return \"\$stem-\$digest\$extension\""))
            assertFalse(text.contains("private fun safeFileName(value: String): String ="))
        }
    }
}
