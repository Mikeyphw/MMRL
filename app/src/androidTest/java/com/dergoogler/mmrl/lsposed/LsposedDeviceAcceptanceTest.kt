package com.dergoogler.mmrl.lsposed

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.platform.PlatformManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device evidence, not release authority. Never modifies Xposed modules, scopes,
 * root-module settings, or daemon configuration.
 *
 * One instrumentation test produces one structured evidence file even if provider
 * setup or Vector is missing. The external acceptance runner interprets statuses;
 * an "OK (1 test)" alone must NEVER be read as a qualified device pass.
 */
@RunWith(AndroidJUnit4::class)
class LsposedDeviceAcceptanceTest {
    @Test
    fun captureReadOnlyDeviceEvidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val report = JSONObject()
            .put("schema", "mmrl.lsposed.device-acceptance.v1")
            .put("target_package", context.packageName)
            .put("device_manufacturer", Build.MANUFACTURER)
            .put("device_model", Build.MODEL)
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("build_fingerprint", Build.FINGERPRINT)
            .put("device_time_ms", System.currentTimeMillis())
            .put("readonly", true)
            .put("injection_verified", false)
            .put("hooks_verified", false)

        try {
            require(context.packageName == "com.dergoogler.mmrl.debug") {
                "Unexpected instrumentation target package"
            }

            // Launch normally so MMRL's existing provider can initialize. Do not
            // bypass onboarding, install modules, or change provider authorization.
            val launched = runCatching {
                val intent = Intent(Intent.ACTION_MAIN)
                    .setClassName(context.packageName, "com.dergoogler.mmrl.ui.activity.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                instrumentation.startActivitySync(intent)
                instrumentation.waitForIdleSync()
            }
            report.put("app_launch", if (launched.isSuccess) "PASS" else "INCONCLUSIVE")

            val scan = LsposedDiscovery.scanInstalledApks(context)
            val xposed = JSONArray()
            scan.modules.forEach { module ->
                xposed.put(JSONObject()
                    .put("package", module.packageName)
                    .put("format", module.format))
            }
            report.put("examined_packages", scan.examined)
                .put("xposed_apks", xposed)
                .put("installed_managers", JSONArray(scan.managers.map { it.packageName }))

            // Best-effort UI navigation: onboarding or different layouts are
            // reported as INCONCLUSIVE rather than being mistaken for app failures.
            val uiStatus = runCatching {
                val device = UiDevice.getInstance(instrumentation)
                val modules = device.wait(
                    Until.findObject(By.text(context.getString(R.string.page_modules))), 8000L)
                modules?.click()
                val entry = device.wait(
                    Until.findObject(By.desc(context.getString(R.string.lsposed_open_discovery))), 8000L)
                if (entry == null) "INCONCLUSIVE_NAVIGATION" else {
                    entry.click()
                    val runtimeButton = device.wait(
                        Until.findObject(By.text(context.getString(R.string.lsposed_runtime_inspect))), 8000L)
                    if (runtimeButton == null) "INCONCLUSIVE_NAVIGATION" else "PASS"
                }
            }.getOrDefault("INCONCLUSIVE_UI")
            report.put("ui_navigation", uiStatus)

            val providerDeadline = SystemClock.elapsedRealtime() + 12000L
            while (!PlatformManager.isAlive && SystemClock.elapsedRealtime() < providerDeadline) {
                SystemClock.sleep(250L)
            }
            report.put("module_provider_available", PlatformManager.isAlive)

            val status = if (!PlatformManager.isAlive) {
                "INCONCLUSIVE_PROVIDER_UNAVAILABLE"
            } else {
                val snapshot = runCatching { LsposedRuntime.inspect() }
                snapshot.fold(onSuccess = { runtime ->
                    val installed = runtime.frameworks.map { module ->
                        JSONObject()
                            .put("id", module.id)
                            .put("family", module.family)
                            .put("version", module.version)
                            .put("installed_state", module.state)
                    }
                    report.put("frameworks", JSONArray(installed))
                        .put("vector_installed", runtime.vectorInstalled)
                        .put("vector_enabled_at_boot", runtime.vectorEnabledAtNextBoot)
                        .put("daemon_responded", runtime.daemonResponded)
                        .put("vector_version", runtime.frameworkVersion ?: JSONObject.NULL)
                        .put("xposed_api_version", runtime.apiVersion ?: JSONObject.NULL)
                        .put("reported_enabled_modules", runtime.reportedEnabledCount ?: JSONObject.NULL)
                        .put("runtime_detail", runtime.detail)
                    val states = JSONArray()
                    runtime.modules.orEmpty().forEach { module ->
                        states.put(JSONObject()
                            .put("package", module.packageName)
                            .put("enabled_in_configuration", module.enabled))
                    }
                    report.put("vector_module_configuration", states)
                    when {
                        !runtime.vectorInstalled -> "NOT_APPLICABLE_VECTOR_ABSENT"
                        !runtime.vectorEnabledAtNextBoot -> "INCONCLUSIVE_VECTOR_DISABLED"
                        !runtime.daemonResponded -> "INCONCLUSIVE_DAEMON_UNVERIFIED"
                        runtime.modules == null -> "INCONCLUSIVE_MODULE_QUERY"
                        else -> "PASS_VECTOR_DAEMON_AND_MODULE_QUERY"
                    }
                }, onFailure = { exception ->
                    report.put("runtime_exception", exception.javaClass.simpleName)
                    "INCONCLUSIVE_RUNTIME_PROBE"
                })
            }
            report.put("status", status)
        } catch (error: Exception) {
            report.put("status", "FAIL_TEST_HARNESS")
                .put("test_error", error.javaClass.simpleName)
        } finally {
            // Atomic replacement of app-owned evidence only; no external storage,
            // root filesystem writes, scope changes or daemon commands.
            val directory = context.filesDir
            val staged = File(directory, "mmrl_lsposed_device_acceptance.json.tmp")
            val committed = File(directory, "mmrl_lsposed_device_acceptance.json")
            staged.writeText(report.toString(2) + "\n")
            check(staged.renameTo(committed)) { "Unable to commit device evidence" }
            println("MMRL_DEVICE_ACCEPTANCE=" + report.optString("status", "MISSING"))
        }
        assertTrue("Instrumentation harness failed: see app-owned evidence",
            report.optString("status") != "FAIL_TEST_HARNESS")
        assertTrue("Package scan failed: see app-owned evidence",
            report.optInt("examined_packages", 0) > 0)
    }
}
