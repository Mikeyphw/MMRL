package com.dergoogler.mmrl.release

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.dergoogler.mmrl.ash.automation.AshHealthCheckWorker
import com.dergoogler.mmrl.service.ModuleUpdateWorker
import com.dergoogler.mmrl.service.RepositoryRefreshWorker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinalWorkManagerAndLifecycleInstrumentedTest {
    @Test
    fun uniqueWorkerNamesAreStableForSchedulerRestoration() {
        assertEqualsString("mmrl-repository-refresh-periodic", RepositoryRefreshWorker.PERIODIC_WORK)
        assertEqualsString("mmrl-repository-refresh-now", RepositoryRefreshWorker.ONE_SHOT_WORK)
        assertEqualsString("mmrl-module-update-periodic", ModuleUpdateWorker.PERIODIC_WORK)
        assertEqualsString("mmrl-module-update-now", ModuleUpdateWorker.ONE_SHOT_WORK)
        assertTrue(AshHealthCheckWorker::class.java.name.contains("AshHealthCheckWorker"))
    }

    @Test
    fun workManagerIsInitializableFromInstalledApplication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertNotNull(WorkManager.getInstance(context))
    }

    private fun assertEqualsString(expected: String, actual: String) {
        assertTrue("expected $expected but was $actual", expected == actual)
    }
}
