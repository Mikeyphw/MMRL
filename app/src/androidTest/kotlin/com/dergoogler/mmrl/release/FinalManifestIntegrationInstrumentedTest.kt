package com.dergoogler.mmrl.release

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinalManifestIntegrationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageInfo: PackageInfo = installedPackageInfo()

    @Test
    fun privilegedActivitiesAreInternalOnInstalledPackage() {
        assertFalse(activityEnding(".ui.activity.terminal.install.InstallActivity").exported)
        assertFalse(activityEnding(".ui.activity.terminal.action.ActionActivity").exported)
        assertFalse(activityEnding(".ui.activity.CrashHandlerActivity").exported)
        assertTrue(activityEnding(".ui.activity.MainActivity").exported)
        assertTrue(activityEnding(".ui.activity.terminal.install.ExternalInstallActivity").exported)
    }

    @Test
    fun bootReceiversServicesAndFileProviderAreNotExternallyCallable() {
        packageInfo.receivers.orEmpty().filter { it.name.contains("Boot") || it.name.contains("Receiver") }.forEach { receiver ->
            assertFalse("receiver must be internal: ${receiver.name}", receiver.exported)
        }
        packageInfo.services.orEmpty().forEach { service ->
            assertFalse("service must be internal: ${service.name}", service.exported)
        }
        val provider = packageInfo.providers.orEmpty().firstOrNull { it.name == "androidx.core.content.FileProvider" }
        assertNotNull("FileProvider must be installed", provider)
        assertFalse(provider!!.exported)
        assertTrue(provider.grantUriPermissions)
        assertTrue(provider.authority.startsWith(context.packageName))
    }

    private fun activityEnding(suffix: String) = packageInfo.activities.orEmpty()
        .firstOrNull { it.name.endsWith(suffix) }
        ?: error("Missing activity ending with $suffix")

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo {
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
    }
}
