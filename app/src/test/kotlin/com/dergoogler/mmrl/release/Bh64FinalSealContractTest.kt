package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Bh64FinalSealContractTest {
    private val root = repositoryRoot()

    @Test
    fun `final seal documentation names the complete O11 gate and no O12`() {
        val doc = source("docs/MMRL_FINAL_RELEASE_SEAL.md")
        assertTrue(doc.contains("O11 is the final remediation overlay"))
        assertTrue(doc.contains("There is no O12"))
        listOf(
            ":app:testOfficialDebugUnitTest",
            ":app:lintOfficialDebug -Pmmrl.fullLint=true",
            ":app:assembleOfficialDebug",
            ":app:assembleOfficialRelease",
            ":app:assembleOfficialPlaystore",
            ":platform:testDebugUnitTest",
            ":platform:testNativeContracts",
            "validate-ashrexcue-release.sh --static-only",
        ).forEach { assertTrue("missing final gate $it", doc.contains(it)) }
    }

    @Test
    fun `github workflow runs wrapper validation and every release variant`() {
        val workflow = source(".github/workflows/mmrl-release-seal.yml")
        assertTrue(workflow.contains("gradle/actions/setup-gradle@v4"))
        assertTrue(workflow.contains("validate-wrappers: true"))
        assertTrue(workflow.contains(":app:assembleOfficialDebug"))
        assertTrue(workflow.contains(":app:assembleOfficialRelease"))
        assertTrue(workflow.contains(":app:assembleOfficialPlaystore"))
        assertTrue(workflow.contains(":app:compileOfficialDebugAndroidTestKotlin"))
        assertTrue(workflow.contains(":platform:testDebugUnitTest"))
        assertTrue(workflow.contains(":platform:testNativeContracts"))
    }

    @Test
    fun `source hygiene script guards every O11 release-hygiene promise`() {
        val script = source("scripts/validate-mmrl-source-hygiene.py")
        listOf(
            "distributionSha256Sum",
            "assembleOfficialPlaystore",
            "testNativeContracts",
            ".devtool/build-artifacts/",
            ".tar.zst",
            "releaseSigningProperties = project.releaseSigningProperties()",
            "DataStore WebUIX package default must be variant-owned through BuildConfig",
            "generated Ash assets must be wired through variant sources",
            "release-hygiene: PASS",
        ).forEach { assertTrue("missing source hygiene check $it", script.contains(it)) }
    }

    private fun source(path: String): String = root.resolve(path).readText()

    private fun repositoryRoot(): File = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
        file.parentFile
    }.first { file -> file.resolve("settings.gradle.kts").isFile && file.resolve("docs/MMRL_FINAL_RELEASE_SEAL.md").isFile }
}
