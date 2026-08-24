package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildGraphQualityConvergenceContractTest {
    private fun root(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (current.parentFile != null) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile
        }
        error("Could not locate MMRL repository root")
    }

    private fun source(path: String): String = File(root(), path).readText()

    @Test
    fun `datastore owns its Hilt binding through the shared KSP convention`() {
        val datastore = source("datastore/build.gradle.kts")
        val convention = source("build-logic/src/main/kotlin/HiltConventionPlugin.kt")
        assertTrue(datastore.contains("alias(libs.plugins.self.hilt)"))
        assertFalse(datastore.contains("alias(libs.plugins.hilt)"))
        assertFalse(datastore.contains("implementation(libs.hilt.android)"))
        assertFalse(datastore.contains("libs.hilt.compiler"))
        assertTrue(convention.contains("apply(plugin = \"dagger.hilt.android.plugin\")"))
        assertTrue(convention.contains("apply(plugin = \"com.google.devtools.ksp\")"))
        assertTrue(convention.contains("\"ksp\"(libs.findLibrary(\"hilt.compiler\").get())"))
        assertFalse(
            File(root(), "app/src/main/kotlin/com/dergoogler/mmrl/datastore/di/DataStoreModule.kt").exists(),
        )
    }

    @Test
    fun `runtime locale and lint policy are explicit`() {
        val app = source("app/build.gradle.kts")
        listOf(
            "MissingTranslation",
            "UnusedResources",
            "GradleDependency",
            "NewerVersionAvailable",
            "PluralsCandidate",
        ).forEach { assertTrue(app.contains("\"$it\"")) }
        assertTrue(app.contains("enableSplit = false"))
        assertTrue(app.contains("warningsAsErrors = true"))
        assertFalse(app.contains("implementation(\"com.joaomgcd:taskerpluginlibrary"))
        assertFalse(app.contains("implementation(\"dev.chrisbanes.haze"))
    }

    @Test
    fun `JVM safe defaults do not eagerly call Android Environment APIs`() {
        val preferences = source("datastore/src/main/kotlin/com/dergoogler/mmrl/datastore/model/UserPreferences.kt")
        val constants = source("app/src/main/kotlin/com/dergoogler/mmrl/app/Const.kt")
        assertTrue(preferences.contains("runCatching"))
        assertFalse(preferences.contains("PUBLIC_DOWNLOADS: File by lazy"))
        assertTrue(constants.contains("val PUBLIC_DOWNLOADS: File\n        get()"))
        assertTrue(constants.contains("runCatching"))
    }

    @Test
    fun `native tool declarations converge on r29`() {
        val mise = source("mise.toml")
        assertTrue(mise.contains("29.0.14206865"))
        assertFalse(mise.contains("28.2.13676358"))
        val platform = source("platform/build.gradle.kts")
        assertTrue(platform.contains("ndkVersion = NDK_VERSION"))
        assertTrue(platform.contains("MMRL_HOST_CXX"))
    }
}
