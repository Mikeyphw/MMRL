package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSealSourceHygieneTest {
    private val root = repositoryRoot()

    @Test
    fun `gradle wrapper is checksum pinned to the declared distribution`() {
        val props = source("gradle/wrapper/gradle-wrapper.properties")
        assertTrue(props.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.0-bin.zip"))
        assertTrue(props.contains("distributionSha256Sum=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"))
    }

    @Test
    fun `source snapshots exclude generated build and devtool artifacts`() {
        val gitignore = source(".gitignore")
        listOf(
            ".devtool/build-artifacts/",
            ".devtool/artifacts.d/",
            "*.apk",
            "*.aab",
            "*.aar",
            "build-logs/",
        ).forEach { assertTrue("missing ignore rule $it", gitignore.contains(it)) }
    }

    @Test
    fun `release packaging builds playstore when all variants are requested`() {
        val script = source("build-release-apk.sh")
        assertTrue(script.contains("build_variant \"${'$'}FLAVOR\" debug"))
        assertTrue(script.contains("build_variant \"${'$'}FLAVOR\" release"))
        assertTrue(script.contains("build_variant \"${'$'}FLAVOR\" playstore"))
        assertTrue(script.contains("if [[ \"${'$'}BUILD_TYPE\" == \"all\" ]]"))
    }

    @Test
    fun `repo archive extension matches zstd compressor`() {
        val script = source("pack_repo.sh")
        assertTrue(script.contains(".tar.zst"))
        assertFalse(script.contains(".tar.gz\""))
        assertTrue(script.contains("--zstd -cf"))
    }

    @Test
    fun `root clean and generated ash assets are variant wired`() {
        val rootBuild = source("build.gradle.kts")
        val appBuild = source("app/build.gradle.kts")
        assertTrue(rootBuild.contains("delete(subprojects.map { it.layout.buildDirectory })"))
        assertTrue(appBuild.contains("variant.sources.assets?.addGeneratedSourceDirectory"))
        assertFalse(appBuild.contains("startsWith(\"merge\") && name.endsWith(\"Assets\")"))
    }

    @Test
    fun `app room schemas are exported and self-identifying`() {
        val schemaDir = root.resolve("app/schemas/com.dergoogler.mmrl.database.AppDatabase")
        val versions = schemaDir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()
        assertTrue("schema directory must not be empty", versions.isNotEmpty())
        assertTrue("latest exported schema should cover at least the durable-operation era", versions.last() >= 19)
        schemaDir.listFiles { file -> file.extension == "json" }.orEmpty().forEach { schema ->
            val declared = Regex("\\\"version\\\"\\s*:\\s*(\\d+)").find(schema.readText())?.groupValues?.get(1)?.toInt()
            assertEquals("bad declared version in ${schema.name}", schema.nameWithoutExtension.toInt(), declared)
        }
    }

    private fun source(path: String): String = root.resolve(path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file -> file.resolve("settings.gradle.kts").isFile && file.resolve("app/build.gradle.kts").isFile }
}
