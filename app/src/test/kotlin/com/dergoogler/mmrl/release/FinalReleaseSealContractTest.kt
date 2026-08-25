package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalReleaseSealContractTest {
    private val root = repositoryRoot()

    private fun source(path: String): String = root.resolve(path).readText()

    @Test
    fun `root build owns one current host release seal`() {
        val rootBuild = source("build.gradle.kts")
        val appBuild = source("app/build.gradle.kts")
        assertTrue(rootBuild.contains("tasks.register(\"mmrlReleaseSeal\")"))
        assertTrue(rootBuild.contains("tasks.register(\"verifyRepositoryHygiene\")"))
        listOf(
            "verifyStableToolchainBaseline",
            "verifyMmrlProductBoundary",
            "verifyRepositoryHygiene",
            ":platform:testNativeContracts",
            ":app:testOfficialDebugUnitTest",
            ":app:fullLintOfficialDebug",
            ":app:compileOfficialDebugAndroidTestKotlin",
            ":app:verifyReleaseArtifacts",
        ).forEach { assertTrue("missing final release task $it", rootBuild.contains(it)) }
        assertFalse(appBuild.contains("tasks.register(\"mmrlReleaseSeal\")"))
        assertFalse(appBuild.contains("tasks.register(\"mmrlConnectedReleaseSeal\")"))
    }

    @Test
    fun `release runner uses bounded Termux phases and automatic device validation`() {
        val runner = source("scripts/run-mmrl-release-seal.sh")
        assertTrue(runner.contains("--memory-guard-mb 0"))
        assertTrue(runner.contains("MMRL_GRADLE_HEAP_MB:-768"))
        assertTrue(runner.contains("MMRL_LINT_HEAP_MB:-640"))
        assertTrue(runner.contains("verifyRepositoryHygiene"))
        assertTrue(runner.contains("MMRL_RUN_CONNECTED_TESTS:-auto"))
        assertTrue(runner.contains("scripts/run-mmrl-device-validation.sh"))
    }

    @Test
    fun `device validation is explicit device aware and root optional`() {
        val device = source("scripts/run-mmrl-device-validation.sh")
        assertTrue(device.contains("${'$'}2 == \"device\""))
        assertTrue(device.contains("MMRL_ADB_SERIAL"))
        assertTrue(device.contains("connectedOfficialDebugAndroidTest"))
        assertTrue(device.contains("su -c id"))
        assertTrue(device.contains("uid=0"))
        assertTrue(device.contains("root unavailable; root smoke skipped"))
    }

    @Test
    fun `final release documentation names current toolchain and complete gates`() {
        val doc = source("docs/MMRL_FINAL_RELEASE_SEAL.md")
        listOf(
            "Android Gradle Plugin 9.3.2",
            "Kotlin 2.4.10",
            "KSP 2.3.11",
            "Hilt 2.60.1",
            "NDK 29.0.14206865",
            "verifyRepositoryHygiene",
            ":app:assembleOfficialRelease",
            "MMRL_RUN_CONNECTED_TESTS=auto",
        ).forEach { assertTrue("missing final documentation token $it", doc.contains(it)) }
    }

    @Test
    fun `obsolete repository backup files are absent`() {
        val excluded = setOf(".git", ".gradle", ".idea", ".kotlin", ".devtool", "build", "build-logs")
        val offenders = root.walkTopDown()
            .onEnter { it == root || it.name !in excluded }
            .filter { it.isFile }
            .filter { file ->
                val name = file.name
                name.endsWith(".bak") || name.endsWith(".orig") || name.endsWith(".rej") ||
                    name.endsWith("~") || ".before-" in name
            }
            .toList()
        assertTrue("stale repository backups: $offenders", offenders.isEmpty())
    }

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
        .first { it.resolve("settings.gradle.kts").isFile && it.resolve("app/build.gradle.kts").isFile }
}
