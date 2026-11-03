package com.dergoogler.mmrl.lsposed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R8 opt-in write acceptance. Changes only the disposable Phase05 hook-lab module
 * and target scope. An independent host journal MUST be committed before invoking
 * this test, so a killed test process does not lose the recovery instructions.
 */
@RunWith(AndroidJUnit4::class)
class VectorManagerLabDeviceTest {
    private companion object {
        const val MODULE = "com.mikeyphw.mmrl.hooklab.module"
        const val TARGET = "com.mikeyphw.mmrl.hooklab.target"
        const val CONSENT = "AUTHORIZE_DISPOSABLE_LAB_ONLY"
    }

    @Suppress("DEPRECATION")
    @Test fun verifyReversibleLabConfigurationWrites() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val report = JSONObject()
            .put("schema", "mmrl.lsposed.phase06.lab-write-device.v1")
            .put("target_package", context.packageName)
            .put("module_package", MODULE)
            .put("scope_target", "$TARGET/0")
            .put("scope_writes_attempted", 0)
            .put("module_writes_attempted", 0)
            .put("hooks_verified", false)
            .put("injection_verified", false)
            .put("stage", "preflight")
            .put("status", "NOT_QUALIFIED")

        val evidence = File(context.filesDir, "mmrl_vector_manager_lab_write.json")
        val pending = File(context.filesDir, "mmrl_vector_manager_lab_write.json.tmp")
        fun commit() {
            pending.writeText(report.toString(2) + "\n")
            check(pending.renameTo(evidence))
        }
        var baseline: List<VectorManager.Scope>? = null
        var otherModules: Map<String, Boolean>? = null
        var authorizedToRestore = false
        var error: Exception? = null

        try {
            require(InstrumentationRegistry.getArguments().getString("mmrl_phase06_lab_authorization") == CONSENT) {
                "Explicit disposable-lab consent missing"
            }
            require(context.packageName.matches(Regex("""com\.dergoogler\.mmrl\.phase06\.r[0-9]+a[0-9]+"""))) {
                "Only CI-isolated Phase06 test package may change lab configuration"
            }
            val nonce = InstrumentationRegistry.getArguments().getString("mmrl_phase06_lab_nonce").orEmpty()
            require(nonce.matches(Regex("[a-f0-9]{48}"))) { "Host authorization nonce missing" }
            val ticket = File(context.filesDir, "mmrl_vector_manager_lab_authorization.token")
            val expectedTicket = "${context.packageName}:$nonce:$MODULE:$TARGET"
            require(ticket.isFile && ticket.readText() == expectedTicket) {
                "Source-bound host authorization ticket missing or mismatched"
            }
            require(ticket.delete()) { "Cannot consume one-time host authorization ticket" }
            for (name in listOf(MODULE, TARGET)) {
                context.packageManager.getApplicationInfo(name, 0)
            }
            val before = VectorManager.inspect(context)
            val lab = before.modules.singleOrNull { it.packageName == MODULE }
                ?: throw IllegalStateException("Disposable module not uniquely present")
            require(!lab.enabled) { "Disposable module must start disabled" }
            otherModules = before.modules.filterNot { it.packageName == MODULE }
                .associate { it.packageName to it.enabled }
            val original = VectorManager.scopes(context, MODULE)
            val labScope = VectorManager.Scope(TARGET, 0)
            require(original.isEmpty() || original == listOf(labScope)) {
                "Disposable module has unexpected scope; refusing writes"
            }
            baseline = original
            report.put("original_enabled", false)
                .put("original_scope", if (original.isEmpty()) "EMPTY" else "LAB_TARGET")
                .put("stage", "BASELINE_JOURNALED")
            commit() // On-device journal before first mutation; host journal is independent.
            authorizedToRestore = true

            val flip = if (original.isEmpty()) VectorManager.Action.ADD_SCOPE
                       else VectorManager.Action.REMOVE_SCOPE
            report.put("stage", "INTEND_FIRST_SCOPE_WRITE")
                .put("scope_writes_attempted", 1)
            commit()
            VectorManager.change(context, VectorManager.Change(
                MODULE, flip, expectedEnabled = false, expectedScope = original,
                targetPackage = TARGET))
            val flipped = VectorManager.scopes(context, MODULE)
            require(flipped == if (original.isEmpty()) listOf(labScope) else emptyList()) {
                "First scope write did not match requested lab state"
            }

            report.put("stage", "INTEND_ENABLE")
                .put("module_writes_attempted", 1)
            commit()
            VectorManager.change(context, VectorManager.Change(
                MODULE, VectorManager.Action.ENABLE, expectedEnabled = false,
                expectedScope = flipped))

            report.put("stage", "INTEND_DISABLE")
                .put("module_writes_attempted", 2)
            commit()
            VectorManager.change(context, VectorManager.Change(
                MODULE, VectorManager.Action.DISABLE, expectedEnabled = true,
                expectedScope = flipped))

            val undo = if (original.isEmpty()) VectorManager.Action.REMOVE_SCOPE
                       else VectorManager.Action.ADD_SCOPE
            report.put("stage", "INTEND_FINAL_SCOPE_WRITE")
                .put("scope_writes_attempted", 2)
            commit()
            VectorManager.change(context, VectorManager.Change(
                MODULE, undo, expectedEnabled = false, expectedScope = flipped,
                targetPackage = TARGET))
        } catch (e: Exception) {
            error = e
            report.put("error_type", e.javaClass.simpleName)
                .put("error_detail", (e.message ?: "Unknown exception").take(200))
        } finally {
            // Restore ONLY the lab module. Never rewrite other module or scope state.
            if (authorizedToRestore) {
                report.put("stage", "RESTORING_LAB")
                commit()
                try {
                    val original = checkNotNull(baseline)
                    val live = VectorManager.inspect(context).modules.single { it.packageName == MODULE }
                    val scope = VectorManager.scopes(context, MODULE)
                    val labTarget = VectorManager.Scope(TARGET, 0)
                    require(scope.isEmpty() || scope == listOf(labTarget)) {
                        "Unexpected lab scope; host recovery required"
                    }
                    if (live.enabled) {
                        report.put("module_writes_attempted", report.getInt("module_writes_attempted") + 1)
                        commit()
                        VectorManager.change(context, VectorManager.Change(
                            MODULE, VectorManager.Action.DISABLE, expectedEnabled = true,
                            expectedScope = scope))
                    }
                    val current = VectorManager.scopes(context, MODULE)
                    if (current != original) {
                        report.put("scope_writes_attempted", report.getInt("scope_writes_attempted") + 1)
                        commit()
                        VectorManager.change(context, VectorManager.Change(
                            MODULE, if (original.isEmpty()) VectorManager.Action.REMOVE_SCOPE
                                else VectorManager.Action.ADD_SCOPE,
                            expectedEnabled = false, expectedScope = current, targetPackage = TARGET))
                    }
                    val finalModule = VectorManager.inspect(context).modules.single { it.packageName == MODULE }
                    require(!finalModule.enabled && VectorManager.scopes(context, MODULE) == original) {
                        "Lab state not restored"
                    }
                    val liveOthers = VectorManager.inspect(context).modules
                        .filterNot { it.packageName == MODULE }
                        .associate { it.packageName to it.enabled }
                    require(liveOthers == otherModules) {
                        "Unrelated module state changed during lab acceptance"
                    }
                    report.put("unrelated_module_states_unchanged", true)
                        .put("restored", true)
                } catch (restore: Exception) {
                    report.put("restored", false)
                        .put("restore_error", (restore.message ?: restore.javaClass.simpleName).take(200))
                    error = error ?: restore
                }
            } else {
                report.put("restored", false)
            }
            report.put("stage", if (authorizedToRestore && report.optBoolean("restored")) "RESTORED" else "NOT_RESTORED")
            report.put("status", if (error == null && report.optBoolean("restored") &&
                report.optInt("module_writes_attempted") == 2 &&
                report.optInt("scope_writes_attempted") == 2)
                    "PASS_LAB_WRITE_AND_RESTORE" else "NOT_QUALIFIED")
            commit()
        }
        assertTrue("R8 lab writes/restoration incomplete: " + report.optString("stage") + "; " +
            report.optString("error_detail") + "; " + report.optString("restore_error"),
            report.optString("status") == "PASS_LAB_WRITE_AND_RESTORE")
    }
}
