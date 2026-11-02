package com.dergoogler.mmrl.lsposed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real-device contract: inventory consistency, read-only. No module/scope mutations. */
@RunWith(AndroidJUnit4::class)
class VectorManagerDeviceTest {
    @Test fun inspectVectorConfigurationReadOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = JSONObject()
            .put("schema", "mmrl.lsposed.vector-manager-device.v1")
            .put("target_package", context.packageName)
            .put("read_only", true)
            .put("module_writes", 0)
            .put("scope_writes", 0)
            .put("hooks_verified", false)
            .put("injection_verified", false)
            .put("stage", "preflight")
        try {
            assertTrue("Unexpected target package", context.packageName == "com.dergoogler.mmrl.debug" ||
                context.packageName.matches(Regex("""com\.dergoogler\.mmrl\.phase06\.r[0-9]+a[0-9]+""")))
            report.put("stage", "initial_inventory")
            val observed = VectorManager.inspect(context)
            val items = JSONArray()
            observed.modules.forEach { item ->
                report.put("stage", "scope:" + item.packageName.take(180))
                val scoped = VectorManager.scopes(context, item.packageName)
                items.put(JSONObject()
                    .put("package", item.packageName)
                    .put("configured_enabled", item.enabled)
                    .put("format", item.format)
                    .put("scope", JSONArray(scoped.map { entry ->
                        "${entry.packageName}/${entry.userId}"
                    })))
            }
            report.put("stage", "inventory_recheck")
            val secondRead = VectorManager.inspect(context)
            report.put("framework_version", observed.frameworkVersion)
                .put("api_version", observed.apiVersion)
                .put("modules", items)
                .put("inventory_consistent", observed.modules.map { it.packageName to it.enabled } ==
                    secondRead.modules.map { it.packageName to it.enabled })
                .put("status", if (report.optBoolean("inventory_consistent"))
                    "PASS_READ_ONLY_INVENTORY" else "INCONCLUSIVE_INVENTORY_CHANGED")
        } catch (exception: Exception) {
            report.put("status", "INCONCLUSIVE_MANAGER_UNAVAILABLE")
                .put("error_type", exception.javaClass.simpleName)
                // Keep a bounded exception message, never a stack trace, root output,
                // full command log, or unbounded user/device data.
                .put("error_detail", (exception.message ?: "No exception message").take(240))
        } finally {
            val committed = File(context.filesDir, "mmrl_vector_manager_device.json")
            val pending = File(context.filesDir, "mmrl_vector_manager_device.json.tmp")
            pending.writeText(report.toString(2) + "\n")
            check(pending.renameTo(committed))
        }
        assertTrue("Unexpected evidence schema", report.optString("schema") ==
            "mmrl.lsposed.vector-manager-device.v1")
        // The JSON is already committed, even on failure; JUnit must not pass
        // when only an IOException was caught and labelled INCONCLUSIVE.
        assertTrue(
            "Phase06 acceptance failed at " + report.optString("stage") + ": " +
                report.optString("error_type") + " / " + report.optString("error_detail"),
            report.optString("status") == "PASS_READ_ONLY_INVENTORY"
        )
    }
}
