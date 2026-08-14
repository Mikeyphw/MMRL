package com.dergoogler.mmrl.ui.activity.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivilegedLaunchIntentContractTest {
    private val root = repositoryRoot()

    @Test
    fun privilegedActivitiesCarryOnlyOpaqueSessionTokenInIntentExtras() {
        val install =
            root.resolve(
                "app/src/main/kotlin/com/dergoogler/mmrl/ui/activity/terminal/install/InstallActivity.kt",
            ).readText()
        val action =
            root.resolve(
                "app/src/main/kotlin/com/dergoogler/mmrl/ui/activity/terminal/action/ActionActivity.kt",
            ).readText()

        listOf(install, action).forEach { source ->
            assertTrue(source.contains("EXTRA_SESSION_ID"))
            assertTrue(source.contains("intent.getStringExtra(EXTRA_SESSION_ID)"))
        }

        listOf(
            "EXTRA_CONFIRM",
            "EXTRA_ROLLBACK_MODE",
            "EXTRA_PARENT_OPERATION_ID",
            "EXTRA_EXPECTED_MODULE_ID",
            "EXTRA_EXPECTED_MODULE_IDS",
            "INTENT_MOD_ID_AS_PARCELABLE",
            "putModId(",
            "getModId(",
        ).forEach { forbidden ->
            assertFalse("InstallActivity must not serialize $forbidden", install.contains(forbidden))
            assertFalse("ActionActivity must not serialize $forbidden", action.contains(forbidden))
        }
    }

    private fun repositoryRoot(): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
            file.parentFile
        }.first { candidate ->
            candidate.resolve("settings.gradle.kts").isFile &&
                candidate.resolve("app/src/main/AndroidManifest.xml").isFile
        }
}
