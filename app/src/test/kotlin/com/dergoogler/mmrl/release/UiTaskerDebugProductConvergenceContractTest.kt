package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTaskerDebugProductConvergenceContractTest {
    private fun root(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (current.parentFile != null) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile
        }
        error("Could not locate MMRL repository root")
    }

    private fun source(relative: String) = File(root(), relative).readText()

    @Test
    fun `compact navigation exposes four primary destinations plus More without wrapping labels`() {
        val main = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/main/MainScreen.kt")
        val compact = main.substringAfter("private val compactDestinations =").substringBefore("private val overflowDestinations")
        listOf(
            "MainDestination.Home",
            "MainDestination.Repository",
            "MainDestination.Modules",
            "MainDestination.Activity",
        ).forEach { assertTrue(compact.contains(it)) }
        assertFalse(compact.contains("MainDestination.SuperUser"))
        assertFalse(compact.contains("MainDestination.Settings"))
        assertTrue(main.contains("DebugWorkbenchDestinationItem(onNavigate = onDismiss)"))
        assertTrue(main.contains("maxLines = 1"))
        assertTrue(main.contains("overflow = TextOverflow.Ellipsis"))
        assertTrue(main.contains("count > 99 -> \"99+\""))
    }

    @Test
    fun `Activity owns one attention view for active failed unknown approval and reboot work`() {
        val vm = source("app/src/main/kotlin/com/dergoogler/mmrl/viewmodel/ActivityViewModel.kt")
        val screen = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/activity/ActivityScreen.kt")
        assertTrue(vm.contains("ATTENTION"))
        assertTrue(vm.contains("ActivityFilter.ATTENTION -> entry.needsActivityAttention()"))
        assertTrue(vm.contains("isFailed || isPendingReboot || isRunning"))
        assertTrue(screen.contains("R.string.activity_filter_attention"))
    }

    @Test
    fun `Tasker shows the stable contract and routes approvals to Activity`() {
        val tasker = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/tasker/TaskerScreen.kt")
        assertTrue(tasker.contains("TaskerPublicContract.VERSION"))
        assertTrue(tasker.contains("TaskerPublicContract.SCHEMA"))
        assertTrue(tasker.contains("ActivityScreenDestination"))
        assertTrue(tasker.contains("settings_tasker_review_activity"))
    }

    @Test
    fun `Debug Workbench stays available as the canonical diagnostics surface`() {
        val main = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/main/MainScreen.kt")
        val developer = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/developer/DeveloperScreen.kt")
        val debug = source("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/debug/DebugWorkbenchScreen.kt")
        assertTrue(main.contains("DebugWorkbenchScreenDestination"))
        assertTrue(main.contains("DebugWorkbenchDestinationItem(onNavigate = onDismiss)"))
        assertTrue(developer.contains("R.string.settings_debug_workbench"))
        assertTrue(developer.contains("enabled = userPreferences.developerMode"))
        assertTrue(debug.contains("Run read-only probes"))
    }

    @Test
    fun `personal build has no Play Store variant`() {
        val app = source("app/build.gradle.kts")
        assertFalse(app.contains("playstore", ignoreCase = true))
        assertFalse(app.contains("IS_GOOGLE_PLAY_BUILD"))
    }
}
