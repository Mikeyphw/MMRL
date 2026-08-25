package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSealGradlePolicyTest {
    private val root = repositoryRoot()

    @Test
    fun `full lint mode is fatal and dependency-aware`() {
        val app = source("app/build.gradle.kts")
        assertTrue(app.contains("val fullLintReport = providers.gradleProperty(\"mmrl.fullLint\")"))
        assertTrue(app.contains("if (!fullLintReport)"))
        assertTrue(app.contains("checkOnly += \"Instantiatable\""))
        assertTrue(app.contains("abortOnError = true"))
        assertTrue(app.contains("warningsAsErrors = true"))
        assertTrue(app.contains("checkDependencies = true"))
    }

    @Test
    fun `final seal includes personal use debug release and instrumentation compilation`() {
        val app = source("app/build.gradle.kts")
        val rootBuild = source("build.gradle.kts")
        listOf(
            "testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\"",
            "assembleOfficialDebug",
            "assembleOfficialRelease",
            "androidTestImplementation(libs.androidx.test.runner)",
            "androidTestImplementation(libs.androidx.junit)",
            "releaseSigningProperties = project.releaseSigningProperties()",
            "refusing to create an unsigned or debug-signed release artifact",
        ).forEach { assertTrue("missing Gradle final-seal token $it", app.contains(it)) }
        assertTrue(rootBuild.contains("mmrlDeviceValidation"))
        assertTrue(rootBuild.contains(":app:connectedOfficialDebugAndroidTest"))
    }

    @Test
    fun `devtool final validation metadata is complete and uses supported mode`() {
        val app = source("app/build.gradle.kts")
        val devtool = source(".devtool.toml")
        assertTrue(devtool.contains(":app:assembleOfficialDebug"))
        assertTrue(devtool.contains(":app:assembleOfficialRelease"))
        assertFalse(devtool.contains(":app:assembleOfficialPlaystore"))
        assertFalse(app.contains("create(\"playstore\")"))
        assertFalse(app.contains("IS_GOOGLE_PLAY_BUILD"))
        assertFalse(devtool.contains("-Pmmrl.fullLint=true"))
        assertTrue(devtool.contains("ndk_version = \"29.0.14206865\""))
        assertTrue(devtool.contains("ndk_host_provider = \"auto\""))
        val releaseSeal = source("scripts/run-mmrl-release-seal.sh")
        assertTrue(releaseSeal.contains("ORG_GRADLE_PROJECT_mmrl.fullLint=true"))
        assertTrue(devtool.contains(":platform:testDebugUnitTest"))
        assertTrue(devtool.contains(":platform:testNativeContracts"))
        assertTrue(devtool.contains("verifyRepositoryHygiene"))
        assertFalse(devtool.contains("minimum_phase"))
        assertFalse(devtool.contains("final-overlay"))
    }

    @Test
    fun `Gradle and Devtool do not impose metaspace caps`() {
        val properties = source("gradle.properties")
        val devtool = source(".devtool.toml")

        assertFalse(properties.contains("MaxMetaspaceSize"))
        listOf("gradle_metaspace_mb", "gradle_packaging_metaspace_mb", "gradle_lint_metaspace_mb").forEach { key ->
            val values = Regex("(?m)^$key\\s*=\\s*(\\d+)\\s*$")
                .findAll(devtool)
                .map { it.groupValues[1] }
                .toList()
            assertTrue("missing Devtool metaspace policy for $key", values.isNotEmpty())
            assertTrue("$key must be disabled in every validation profile", values.all { it == "0" })
        }
    }

    @Test
    fun `api 27 navigation bar theme attribute is version qualified`() {
        val baseTheme = source("app/src/main/res/values/themes.xml")
        val api27Theme = source("app/src/main/res/values-v27/themes.xml")
        assertFalse(baseTheme.contains("android:windowLightNavigationBar"))
        assertTrue(api27Theme.contains("android:windowLightNavigationBar"))
    }

    @Test
    fun `platform native tests are first class Gradle verification inputs`() {
        val platform = source("platform/build.gradle.kts")
        assertTrue(platform.contains("ndkVersion = NDK_VERSION"))
        assertTrue(platform.contains("testNativeContracts"))
        assertTrue(platform.contains("src/testNative"))
        assertTrue(platform.contains("check"))
    }

    @Test
    fun `release build metadata is archive safe and variant aligned`() {
        val ext = source("build-logic/src/main/kotlin/ProjectExt.kt")
        assertTrue(ext.contains("mmrl.versionCode"))
        assertTrue(ext.contains("mmrl.commitCount"))
        assertTrue(ext.contains("execOrNull"))
        assertTrue(ext.contains("ReleaseSigningProperties"))
        val datastore = source("datastore/build.gradle.kts")
        val preferences = source("datastore/src/main/kotlin/com/dergoogler/mmrl/datastore/model/UserPreferences.kt")
        assertTrue(datastore.contains("WEBUIX_PACKAGE_NAME"))
        assertTrue(preferences.contains("BuildConfig.WEBUIX_PACKAGE_NAME"))
    }

    private fun source(path: String): String = root.resolve(path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file -> file.resolve("settings.gradle.kts").isFile && file.resolve("app/build.gradle.kts").isFile }
}
