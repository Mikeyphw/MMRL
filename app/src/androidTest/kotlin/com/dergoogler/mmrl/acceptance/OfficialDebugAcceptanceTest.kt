package com.dergoogler.mmrl.acceptance

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Deliberately read-only acceptance: no module toggles, root writes or LSPosed DB changes. */
@RunWith(AndroidJUnit4::class)
class OfficialDebugAcceptanceTest {
    @Test
    fun targetPackageIsOfficialDebugAndInstalled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.mikeyphw.mmrl.debug", context.packageName)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals(context.packageName, info.packageName)
    }

    @Test
    fun launcherIntentIsResolvable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("OfficialDebug launcher activity must resolve", intent)
    }

    @Test
    fun launcherActivityCanReachResumedState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("OfficialDebug must supply a launch intent", intent)
        ActivityScenario.launch<Activity>(requireNotNull(intent)).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("MMRL launch activity finished immediately", activity.isFinishing)
            }
        }
    }
}
