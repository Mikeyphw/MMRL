package com.dergoogler.mmrl.ash.root

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AshRuntimeRepairContractTest {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun `root service passes app context so jq can be repaired from bundled assets`() {
        val source = source("app/src/main/kotlin/com/dergoogler/mmrl/ash/root/AshRootService.kt")

        assertTrue(source.contains("AshCtlExecutor(context = applicationContext)"))
    }

    @Test
    fun `ash executor reports and repairs bundled jq runtime before live commands`() {
        val source = source("app/src/main/kotlin/com/dergoogler/mmrl/ash/root/AshCtlExecutor.kt")

        assertTrue(source.contains("repairBundledJqIfNeeded"))
        assertTrue(source.contains("AshBundledModuleProvider.ASH_MODULE_ZIP_ASSET"))
        assertTrue(source.contains("jqRepaired"))
        assertTrue(source.contains("JQ_RELATIVE_PATH = \"jq/jq\""))
    }

    @Test
    fun `moshi codegen is stripped from hilt java annotation processor path at execution time`() {
        val source = source("app/build.gradle.kts")

        assertTrue(source.contains("ksp(libs.square.moshi.kotlin)"))
        assertTrue(source.contains("doFirst(\"stripMoshiCodegenFromHiltJavaAnnotationProcessors\")"))
        assertTrue(source.contains("!file.name.startsWith(\"moshi-kotlin-codegen-\")"))
    }

    @Test
    fun `activity primary navigation uses attention badge count`() {
        val viewModel = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/ActivityViewModel.kt")
        val mainScreen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/main/MainScreen.kt")

        assertTrue(viewModel.contains("activityAttentionCount"))
        assertTrue(viewModel.contains("entry.isFailed || entry.isPendingReboot || entry.isRunning"))
        assertTrue(mainScreen.contains("activityAttentionCount.coerceAtMost(99)"))
        assertTrue(mainScreen.contains("screen == MainDestination.Activity && activityAttentionCount > 0"))
    }

    private fun source(path: String): String {
        val candidates = buildList {
            add(File(root, path))
            root.parentFile?.let { add(File(it, path)) }
            if (path.startsWith("app/")) {
                add(File(root, path.removePrefix("app/")))
            }
        }
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing source file for $path. Checked: ${candidates.joinToString { it.path }}")
        return String(file.readBytes(), Charsets.UTF_8)
    }
}
