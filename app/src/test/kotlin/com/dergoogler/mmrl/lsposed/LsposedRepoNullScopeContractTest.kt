package com.dergoogler.mmrl.lsposed

import com.dergoogler.mmrl.app.moshi
import com.squareup.moshi.Types
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedRepoNullScopeContractTest {
    private val root = repositoryRoot()

    @Test
    fun `repository modules tolerate null and missing scope fields`() {
        val adapter = moshi.adapter<List<LsposedRepoModule>>(
            Types.newParameterizedType(List::class.java, LsposedRepoModule::class.java),
        )

        val modules = adapter.fromJson(
            """
            [
              {"name":"one.module","scope":["android"]},
              {"name":"two.module","scope":null},
              {"name":"three.module"}
            ]
            """.trimIndent(),
        ).orEmpty()

        assertEquals(listOf("android"), modules[0].scope)
        assertTrue(modules[1].scope.isEmpty())
        assertTrue(modules[2].scope.isEmpty())
    }

    @Test
    fun `repo model keeps non nullable public scope while accepting nullable json scope`() {
        val model = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        val probe = source("app/src/main/kotlin/com/dergoogler/mmrl/debug/LsposedDebugProbe.kt")

        assertTrue(model.contains("@param:Json(name = \"scope\") val repositoryScope: List<String>? = null"))
        assertTrue(model.contains("val scope: List<String>"))
        assertTrue(model.contains("get() = repositoryScope.orEmpty()"))
        assertTrue(model.contains("repositoryScope = detail.scope.ifEmpty { scope }"))

        assertTrue(probe.contains("provider action bridge"))
        assertTrue(probe.contains("com.android.shell/.BugreportWarningActivity"))
        assertTrue(probe.contains("No installed manager package is visible, but the active provider action bridge can open the bundled manager."))
    }

    private fun source(path: String): String = File(root, path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file ->
        file.resolve("settings.gradle.kts").isFile &&
            file.resolve("app/src/main/kotlin/com/dergoogler/mmrl").isDirectory
    }
}
