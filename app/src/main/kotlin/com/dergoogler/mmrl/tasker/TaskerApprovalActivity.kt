package com.dergoogler.mmrl.tasker

import android.app.AlertDialog
import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class TaskerApprovalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestId = intent.getStringExtra("request_id") ?: return finish()
        val request = TaskerRootRequestStore.get(this, requestId) ?: return finish()
        if (intent.hasExtra("approve")) {
            finishDecision(request, intent.getBooleanExtra("approve", false))
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Approve Tasker action?")
            .setMessage("${request.command.replace('_', ' ')}\n\n${request.moduleName.ifBlank { request.moduleId }}")
            .setNegativeButton("Deny") { _, _ -> finishDecision(request, false) }
            .setPositiveButton("Approve") { _, _ -> finishDecision(request, true) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun finishDecision(request: TaskerRootRequest, approved: Boolean) {
        getSystemService(NotificationManager::class.java).cancel(request.id.hashCode())
        lifecycleScope.launch {
            val history = TaskerRuntime.repositories(this@TaskerApprovalActivity).operationHistoryRepository()
            if (approved) {
                history.appendLog(request.operationId, "Approved by user")
                try {
                    TaskerRootDispatcher.enqueue(this@TaskerApprovalActivity, request.id)
                } catch (error: Throwable) {
                    request.reviewToken?.let { token ->
                        TaskerReviewTokenStore.releaseClaim(
                            this@TaskerApprovalActivity,
                            token,
                            request.operationId,
                        )
                    }
                    TaskerRootRequestStore.remove(this@TaskerApprovalActivity, request.id)
                    history.fail(
                        request.operationId,
                        error.message ?: "Unable to queue approved Tasker action",
                        error,
                    )
                }
            } else {
                request.reviewToken?.let {
                    TaskerReviewTokenStore.releaseClaim(
                        this@TaskerApprovalActivity,
                        it,
                        request.operationId,
                    )
                }
                TaskerRootRequestStore.remove(this@TaskerApprovalActivity, request.id)
                history.fail(request.operationId, "Tasker action denied by user")
            }
            finish()
        }
    }
}
