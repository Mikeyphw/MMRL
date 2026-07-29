package com.dergoogler.mmrl.debug

import android.content.Context
import android.content.Intent
import com.dergoogler.mmrl.lsposed.LsposedProviderRefreshMode
import com.dergoogler.mmrl.lsposed.LsposedRepository
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.service.RepositoryService
import com.dergoogler.mmrl.ui.activity.terminal.action.ActionActivity

/** Guarded, non-arbitrary actions exposed from the Debug Workbench. */
class DebugActionRunner(private val context: Context) {
    fun openLsposedManager(): DebugActionResult {
        val intent = LsposedRepository(context).lsposedManagerIntent()
            ?: return DebugActionResult(
                status = DebugProbeStatus.FAIL,
                message = "No launchable LSPosed/libxposed/Vector manager intent was resolved.",
            )
        return startIntent(intent, "Opened the resolved LSPosed/libxposed/Vector manager.")
    }

    fun runProviderActionBridge(): DebugActionResult {
        val repository = LsposedRepository(context)
        val plan = repository.providerRefreshPlan()
        if (plan.mode != LsposedProviderRefreshMode.ACTION_BRIDGE) {
            return DebugActionResult(
                status = DebugProbeStatus.WARN,
                message = "Provider action bridge is not the selected refresh mode: ${plan.mode}. No arbitrary shell is exposed.",
            )
        }
        val moduleId = plan.moduleId?.takeIf { it.isNotBlank() }
            ?: return DebugActionResult(
                status = DebugProbeStatus.FAIL,
                message = "Provider action bridge did not include a module id.",
            )
        return runCatching {
            ActionActivity.start(context, ModId(moduleId))
            DebugActionResult(
                status = DebugProbeStatus.PASS,
                message = "Started provider action bridge for $moduleId.",
            )
        }.getOrElse { error ->
            DebugActionResult(
                status = DebugProbeStatus.FAIL,
                message = error.message ?: "Unable to start provider action bridge.",
            )
        }
    }

    fun startRepositoryRefresh(): DebugActionResult = runCatching {
        RepositoryService.refreshOnce(context)
        DebugActionResult(
            status = DebugProbeStatus.PASS,
            message = "Started a one-time repository refresh. The foreground notification will close after the pass completes.",
        )
    }.getOrElse { error ->
        DebugActionResult(
            status = DebugProbeStatus.FAIL,
            message = error.message ?: "Unable to start repository refresh service.",
        )
    }

    fun stopRepositoryRefresh(): DebugActionResult = runCatching {
        RepositoryService.stop(context)
        DebugActionResult(
            status = DebugProbeStatus.PASS,
            message = "Stopped repository refresh service.",
        )
    }.getOrElse { error ->
        DebugActionResult(
            status = DebugProbeStatus.FAIL,
            message = error.message ?: "Unable to stop repository refresh service.",
        )
    }

    private fun startIntent(
        intent: Intent,
        successMessage: String,
    ): DebugActionResult = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        DebugActionResult(DebugProbeStatus.PASS, successMessage)
    }.getOrElse { error ->
        DebugActionResult(
            status = DebugProbeStatus.FAIL,
            message = error.message ?: "Unable to open resolved intent.",
        )
    }
}

data class DebugActionResult(
    val status: DebugProbeStatus,
    val message: String,
)
