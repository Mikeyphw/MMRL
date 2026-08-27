package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModulePlatformConvergenceContractTest {
    private fun root(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (current.parentFile != null) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile
        }
        error("Could not locate repository root")
    }

    private fun source(relative: String) = File(root(), relative).readText()

    @Test
    fun `LSPosed repository presentation follows upstream metadata semantics`() {
        val models = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedModels.kt")
        assertTrue(models.contains("get() = repositoryTitle ?: fallbackDisplayName(name)"))
        assertTrue(models.contains("get() = repositorySummary"))
        assertFalse(models.contains("get() = summary?.takeIf { it.isNotBlank() }\n            ?: name.substringAfterLast"))
    }

    @Test
    fun `LSPosed cache validates remote generation before publication`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt")
        val parse = repository.indexOf("val parsed = parseRepositoryModules(remote.body)")
        val publish = repository.indexOf("writeAtomic(cache, remote.body)")
        assertTrue(parse >= 0)
        assertTrue(publish > parse)
    }

    @Test
    fun `partial update refresh preserves known state and GitHub download cleans partials`() {
        val worker = source("app/src/main/kotlin/com/dergoogler/mmrl/service/ModuleUpdateWorker.kt")
        assertTrue(worker.contains("RefreshBatchPolicy.mergeObservedKeys"))
        val github = source("app/src/main/kotlin/com/dergoogler/mmrl/github/GitHubModuleResolver.kt")
        assertTrue(github.contains("GitHubReleaseSelectionPolicy.select"))
        assertTrue(github.contains("catch (error: Throwable)"))
        assertTrue(github.contains("destination.delete()"))
    }

    @Test
    fun `platform reads survive Binder death and refresh after reconnect`() {
        val manager = source("platform/src/main/kotlin/com/dergoogler/mmrl/platform/PlatformManager.kt")
        val remember = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/remember/platform.kt")

        assertTrue(manager.contains("Remote platform call failed; using fallback"))
        assertTrue(manager.contains("if (!isAlive || mServiceOrNull == null)"))
        assertTrue(remember.contains("val alive = isAlive"))
        assertTrue(remember.contains("val service = mServiceOrNull"))
        assertTrue(remember.contains("produceState(initialValue = fallback, fallback, alive, service)"))
        assertTrue(remember.contains("get(fallback, block)"))
        assertFalse(remember.contains("isAliveFlow"))
    }

    @Test
    fun `KernelSU JNI entrypoints keep stable JVM names and linkage cannot kill Binder service`() {
        val native = source("platform/src/main/kotlin/com/dergoogler/mmrl/platform/ksu/KsuNative.kt")
        val manager = source("platform/src/main/kotlin/com/dergoogler/mmrl/platform/manager/KernelSUModuleManager.kt")
        val jni = source("platform/src/main/jni/kernelsu/jni.cpp")

        assertFalse(native.contains("internal external fun"))
        listOf(
            "nativeGetVersion",
            "nativeGetAllowList",
            "nativeIsSafeMode",
            "nativeIsLkmMode",
            "nativeUidShouldUmount",
            "nativeIsSuEnabled",
            "nativeSetSuEnabled",
        ).forEach { name ->
            assertTrue(native.contains("private external fun $name"))
            assertTrue(jni.contains("Java_com_dergoogler_mmrl_platform_ksu_KsuNative_$name"))
        }
        assertTrue(native.contains("catch (error: LinkageError)"))
        assertTrue(native.contains("nativeLoaded = false"))
        assertTrue(manager.contains("KsuNative.rawGetVersion()"))
        assertFalse(manager.contains("KsuNative.nativeGetVersion()"))
    }

    @Test
    fun `module repository preserves cached state across Binder death`() {
        val repository = source("app/src/main/kotlin/com/dergoogler/mmrl/repository/ModulesRepository.kt")
        assertTrue(repository.contains("PlatformManager.get<List<LocalModule>?>(null) { moduleManager.modules }"))
        assertTrue(repository.contains("?: return@withContext"))
        assertFalse(repository.contains("replaceLocalGeneration(PlatformManager.moduleManager.modules)"))
    }

}
