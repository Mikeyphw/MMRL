package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinCompileRegressionSourceHygieneTest {
    private val root = repositoryRoot()

    @Test
    fun `lsposed repository imports Timber compatibility alias`() {
        val source = root.resolve(
            "app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt",
        ).readText()

        assertTrue(source.contains("import timber.log.Timber"))
        assertTrue(source.contains("Timber.w(error, \"Ignoring invalid LSPosed repository cache\")"))
    }

    @Test
    fun `modules list has no repeated composable annotations`() {
        val source = root.resolve(
            "app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/modules/ModulesList.kt",
        ).readText()
        val repeatedComposable = Regex("@Composable\\s*@Composable")

        assertFalse(
            "Repeated @Composable annotations fail Kotlin compilation because Composable is not repeatable",
            repeatedComposable.containsMatchIn(source),
        )
    }

    private fun repositoryRoot(): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { it.resolve("settings.gradle.kts").isFile && it.resolve("app/build.gradle.kts").isFile }
}
