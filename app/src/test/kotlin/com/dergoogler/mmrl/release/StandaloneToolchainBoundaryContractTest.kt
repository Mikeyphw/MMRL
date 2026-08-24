package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneToolchainBoundaryContractTest {
    private fun repositoryRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (current.parentFile != null) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile
        }
        error("Could not locate MMRL repository root")
    }

    @Test
    fun `stable toolchain and Devtool execution policy are normalized`() {
        val root = repositoryRoot()
        val catalog = File(root, "gradle/libs.versions.toml").readText()
        val wrapper = File(root, "gradle/wrapper/gradle-wrapper.properties").readText()
        val projectExt = File(root, "build-logic/src/main/kotlin/ProjectExt.kt").readText()
        val devtool = File(root, ".devtool.toml").readText()

        listOf(
            "androidGradlePlugin = \"9.3.2\"",
            "kotlin = \"2.4.10\"",
            "kotlinReflect = \"2.4.10\"",
            "ksp = \"2.3.11\"",
            "hilt = \"2.60.1\"",
        ).forEach { assertTrue("missing toolchain pin: $it", catalog.contains(it)) }

        assertTrue(wrapper.contains("gradle-9.7.1-bin.zip"))
        assertTrue(projectExt.contains("const val COMPILE_SDK = 36"))
        assertTrue(projectExt.contains("const val TARGET_SDK = 36"))
        assertTrue(projectExt.contains("const val BUILD_TOOLS_VERSION = \"36.0.0\""))
        assertTrue(projectExt.contains("const val NDK_VERSION = \"29.0.14206865\""))

        assertTrue(devtool.contains("provider = \"wrapper\""))
        assertTrue(devtool.contains("version = \"9.7.1\""))
        assertTrue(devtool.contains("memory_guard_mb = 0"))
        assertTrue(devtool.contains("parallel = false"))
    }

    @Test
    fun `repository exposes exactly the declared MMRL Gradle modules`() {
        val root = repositoryRoot()
        val expected = setOf(
            "app", "hidden-api", "platform", "ui", "ext", "datastore",
            "terminal-compat", "webui-core-compat", "compat", "build-logic",
        )
        val actual = root.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "build.gradle.kts").isFile }
            .map { it.name }
            .toSet()

        assertEquals(expected, actual)
    }

    @Test
    fun `application boundary has no undeclared app source set or AIDL`() {
        val root = repositoryRoot()
        val appMain = File(root, "app/src/main")
        val allowed = setOf("AndroidManifest.xml", "assets", "java", "kotlin", "res")
        val actual = appMain.listFiles().orEmpty().map { it.name }.toSet()

        assertTrue((actual - allowed).isEmpty())
        assertFalse(appMain.walkTopDown().any { it.isFile && it.extension == "aidl" })
        assertTrue(File(root, "app/build.gradle.kts").readText().contains("aidl = false"))
    }

    @Test
    fun `historical overlay-specific release task names are not active`() {
        val root = repositoryRoot()
        val rootBuild = File(root, "build.gradle.kts").readText()
        val appBuild = File(root, "app/build.gradle.kts").readText()
        val devtool = File(root, ".devtool.toml").readText()

        assertTrue(rootBuild.contains("verifyStableToolchainBaseline"))
        assertTrue(rootBuild.contains("verifyMmrlProductBoundary"))
        assertTrue(appBuild.contains("fullLintOfficialDebug"))
        assertTrue(appBuild.contains("verifyReleaseArtifacts"))
        assertFalse(rootBuild.contains("tasks.register(\"verifyOv"))
        assertFalse(devtool.contains("verifyOv"))
    }
}
