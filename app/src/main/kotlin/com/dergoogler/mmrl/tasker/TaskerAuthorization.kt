package com.dergoogler.mmrl.tasker

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.datastore.model.TaskerApprovalPolicy
import com.dergoogler.mmrl.datastore.model.UserPreferences
import com.dergoogler.mmrl.model.ModuleIdentity
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class TaskerCapability {
    STATE_CHANGE,
    MODULE_ACTION,
    REMOVAL,
    REVIEWED_INSTALL,
}

enum class TaskerAuthorizationDecision { EXECUTE, REQUIRE_APPROVAL, DENY }

internal object TaskerAuthorizationPolicy {
    fun decide(
        context: Context,
        preferences: UserPreferences,
        capability: TaskerCapability,
        moduleId: String,
    ): TaskerAuthorizationDecision {
        val manager = context.getSystemService(KeyguardManager::class.java)
        return decide(
            preferences = preferences,
            capability = capability,
            moduleId = moduleId,
            deviceUnlocked = manager?.isDeviceLocked == false,
        )
    }

    fun decide(
        preferences: UserPreferences,
        capability: TaskerCapability,
        moduleId: String,
        deviceUnlocked: Boolean,
    ): TaskerAuthorizationDecision {
        if (!preferences.taskerIntegrationEnabled) return TaskerAuthorizationDecision.DENY
        val capabilityAllowed = when (capability) {
            TaskerCapability.STATE_CHANGE -> preferences.taskerAllowStateChanges
            TaskerCapability.MODULE_ACTION -> preferences.taskerAllowModuleActions
            TaskerCapability.REMOVAL -> preferences.taskerAllowRemovals
            TaskerCapability.REVIEWED_INSTALL -> preferences.taskerAllowReviewedInstalls
        }
        if (!capabilityAllowed) return TaskerAuthorizationDecision.DENY
        return when (preferences.taskerApprovalPolicy) {
            TaskerApprovalPolicy.ALWAYS_ASK -> TaskerAuthorizationDecision.REQUIRE_APPROVAL
            TaskerApprovalPolicy.DEVICE_UNLOCKED ->
                if (deviceUnlocked) TaskerAuthorizationDecision.EXECUTE else TaskerAuthorizationDecision.REQUIRE_APPROVAL
            TaskerApprovalPolicy.MODULE_ALLOWLIST -> {
                val normalized = ModuleIdentity.normalize(moduleId)
                if (preferences.taskerAllowedModules.any { ModuleIdentity.matches(it, normalized) }) {
                    TaskerAuthorizationDecision.EXECUTE
                } else {
                    TaskerAuthorizationDecision.REQUIRE_APPROVAL
                }
            }
            TaskerApprovalPolicy.NEVER -> TaskerAuthorizationDecision.DENY
        }
    }

    fun reviewedInstallDecision(
        policyDecision: TaskerAuthorizationDecision,
        routine: Boolean,
    ): TaskerAuthorizationDecision =
        if (!routine && policyDecision == TaskerAuthorizationDecision.EXECUTE) {
            TaskerAuthorizationDecision.REQUIRE_APPROVAL
        } else {
            policyDecision
        }
}

data class TaskerRootRequest(
    val id: String = UUID.randomUUID().toString(),
    val operationId: String,
    val command: String,
    val moduleId: String,
    val moduleName: String,
    val reviewToken: String? = null,
    val targetOperationId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson() = JSONObject()
        .put("id", id)
        .put("operation_id", operationId)
        .put("command", command)
        .put("module_id", moduleId)
        .put("module_name", moduleName)
        .put("review_token", reviewToken)
        .put("target_operation_id", targetOperationId)
        .put("created_at", createdAt)

    companion object {
        fun fromJson(value: JSONObject) = TaskerRootRequest(
            id = value.getString("id"),
            operationId = value.getString("operation_id"),
            command = value.getString("command"),
            moduleId = value.getString("module_id"),
            moduleName = value.optString("module_name"),
            reviewToken = value.optString("review_token").takeIf(String::isNotBlank),
            targetOperationId = value.optString("target_operation_id").takeIf(String::isNotBlank),
            createdAt = value.optLong("created_at"),
        )
    }
}

internal object TaskerRootRequestStore {
    private const val RETENTION_MS = 24L * 60L * 60L * 1000L
    private fun directory(context: Context) = File(context.filesDir, "tasker-root-requests").apply { mkdirs() }
    private fun safeId(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun file(context: Context, id: String) = File(directory(context), "${safeId(id)}.json")
    private fun queuedFile(context: Context, id: String) = File(directory(context), "${safeId(id)}.queued")

    fun put(context: Context, request: TaskerRootRequest) {
        prune(context)
        file(context, request.id).writeText(request.toJson().toString())
    }

    fun get(context: Context, id: String): TaskerRootRequest? = runCatching {
        TaskerRootRequest.fromJson(JSONObject(file(context, id).readText()))
    }.getOrNull()

    fun findByOperationId(context: Context, operationId: String): TaskerRootRequest? {
        prune(context)
        return directory(context)
            .listFiles()
            .orEmpty()
            .asSequence()
            .mapNotNull { source ->
                runCatching { TaskerRootRequest.fromJson(JSONObject(source.readText())) }.getOrNull()
            }
            .firstOrNull { request -> request.operationId == operationId }
    }

    @Synchronized
    fun markEnqueued(context: Context, id: String): Boolean = queuedFile(context, id).createNewFile()

    fun clearEnqueued(context: Context, id: String) {
        queuedFile(context, id).delete()
    }

    fun remove(context: Context, id: String) {
        file(context, id).delete()
        queuedFile(context, id).delete()
    }

    private fun prune(context: Context) {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        directory(context).listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach(File::delete)
    }
}

internal object TaskerRootDispatcher {
    private const val CHANNEL = "tasker_approvals"
    private const val EXTRA_REQUEST_ID = "request_id"

    fun dispatch(context: Context, request: TaskerRootRequest, decision: TaskerAuthorizationDecision): String {
        TaskerRootRequestStore.put(context, request)
        return when (decision) {
            TaskerAuthorizationDecision.EXECUTE -> {
                enqueue(context, request.id)
                "QUEUED"
            }
            TaskerAuthorizationDecision.REQUIRE_APPROVAL -> {
                notifyApproval(context, request)
                "AWAITING_APPROVAL"
            }
            TaskerAuthorizationDecision.DENY -> {
                TaskerRootRequestStore.remove(context, request.id)
                "DENIED"
            }
        }
    }

    fun enqueue(context: Context, requestId: String) {
        if (!TaskerRootRequestStore.markEnqueued(context, requestId)) return
        try {
            val data = Data.Builder().putString(EXTRA_REQUEST_ID, requestId).build()
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<TaskerAutomationWorker>().setInputData(data).build(),
            )
        } catch (error: Throwable) {
            TaskerRootRequestStore.clearEnqueued(context, requestId)
            throw error
        }
    }

    fun requestId(data: Data): String? = data.getString(EXTRA_REQUEST_ID)

    private fun notifyApproval(context: Context, request: TaskerRootRequest) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Tasker approvals", NotificationManager.IMPORTANCE_HIGH))
        val openIntent = Intent(context, TaskerApprovalActivity::class.java)
            .putExtra(EXTRA_REQUEST_ID, request.id)
        val approveIntent = Intent(context, TaskerApprovalActivity::class.java)
            .putExtra(EXTRA_REQUEST_ID, request.id)
            .putExtra("approve", true)
        val denyIntent = Intent(context, TaskerApprovalActivity::class.java)
            .putExtra(EXTRA_REQUEST_ID, request.id)
            .putExtra("approve", false)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.launcher_outline)
            .setContentTitle("Approve MMRL Tasker action")
            .setContentText("${request.command.replace('_', ' ').lowercase()}: ${request.moduleName.ifBlank { request.moduleId }}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    request.id.hashCode() xor 0x33cc,
                    openIntent,
                    flags,
                ),
            )
            .setAutoCancel(true)
            .addAction(0, "Deny", PendingIntent.getActivity(context, request.id.hashCode(), denyIntent, flags))
            .addAction(0, "Approve", PendingIntent.getActivity(context, request.id.hashCode() xor 0x55aa, approveIntent, flags))
            .build()
        manager.notify(request.id.hashCode(), notification)
    }
}
