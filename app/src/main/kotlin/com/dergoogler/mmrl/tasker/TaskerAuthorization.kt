package com.dergoogler.mmrl.tasker

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.util.AtomicFile
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.datastore.model.TaskerApprovalPolicy
import com.dergoogler.mmrl.datastore.model.UserPreferences
import com.dergoogler.mmrl.model.ModuleIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TASKER_ROOT_REQUEST_TTL_MS = 10L * 60L * 1000L

enum class TaskerCapability {
    STATE_CHANGE,
    MODULE_ACTION,
    REMOVAL,
    REVIEWED_INSTALL,
    ASH_RECOVERY,
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
        return decideForModules(
            preferences = preferences,
            capability = capability,
            moduleIds = listOf(moduleId),
            deviceUnlocked = manager?.isDeviceLocked == false,
        )
    }

    fun decideForModules(
        context: Context,
        preferences: UserPreferences,
        capability: TaskerCapability,
        moduleIds: Collection<String>,
    ): TaskerAuthorizationDecision {
        val manager = context.getSystemService(KeyguardManager::class.java)
        return decideForModules(
            preferences = preferences,
            capability = capability,
            moduleIds = moduleIds,
            deviceUnlocked = manager?.isDeviceLocked == false,
        )
    }

    fun decide(
        preferences: UserPreferences,
        capability: TaskerCapability,
        moduleId: String,
        deviceUnlocked: Boolean,
    ): TaskerAuthorizationDecision = decideForModules(
        preferences = preferences,
        capability = capability,
        moduleIds = listOf(moduleId),
        deviceUnlocked = deviceUnlocked,
    )

    fun decideForModules(
        preferences: UserPreferences,
        capability: TaskerCapability,
        moduleIds: Collection<String>,
        deviceUnlocked: Boolean,
    ): TaskerAuthorizationDecision {
        if (!preferences.taskerIntegrationEnabled) return TaskerAuthorizationDecision.DENY
        val capabilityAllowed = when (capability) {
            TaskerCapability.STATE_CHANGE -> preferences.taskerAllowStateChanges
            TaskerCapability.MODULE_ACTION -> preferences.taskerAllowModuleActions
            TaskerCapability.REMOVAL -> preferences.taskerAllowRemovals
            TaskerCapability.REVIEWED_INSTALL -> preferences.taskerAllowReviewedInstalls
            TaskerCapability.ASH_RECOVERY -> preferences.taskerAllowAshRecovery
        }
        if (!capabilityAllowed) return TaskerAuthorizationDecision.DENY
        return when (preferences.taskerApprovalPolicy) {
            TaskerApprovalPolicy.ALWAYS_ASK -> TaskerAuthorizationDecision.REQUIRE_APPROVAL
            TaskerApprovalPolicy.DEVICE_UNLOCKED ->
                if (deviceUnlocked) TaskerAuthorizationDecision.EXECUTE else TaskerAuthorizationDecision.REQUIRE_APPROVAL
            TaskerApprovalPolicy.MODULE_ALLOWLIST -> {
                val normalized = moduleIds.map(ModuleIdentity::normalize).filter(String::isNotBlank)
                if (normalized.isNotEmpty() && normalized.all { moduleId ->
                        preferences.taskerAllowedModules.any { ModuleIdentity.matches(it, moduleId) }
                    }) {
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

    fun capabilityForRequest(request: TaskerRootRequest): TaskerCapability =
        request.capability
            .takeIf(String::isNotBlank)
            ?.let { runCatching { TaskerCapability.valueOf(it) }.getOrNull() }
            ?: capabilityFromCommand(request.command)

    fun capabilityFromCommand(command: String): TaskerCapability = when (command) {
        "ENABLE", "DISABLE" -> TaskerCapability.STATE_CHANGE
        "REMOVE", "RESTORE" -> TaskerCapability.REMOVAL
        "RUN_ACTION" -> TaskerCapability.MODULE_ACTION
        "EXECUTE_REVIEW" -> TaskerCapability.REVIEWED_INSTALL
        "ASH_EXECUTE_PLAN" -> TaskerCapability.ASH_RECOVERY
        else -> TaskerCapability.MODULE_ACTION
    }

    fun requireExecutionAllowed(
        context: Context,
        preferences: UserPreferences,
        request: TaskerRootRequest,
    ) {
        if (request.isExpired()) error("Tasker root request expired before execution")
        val decision = decide(context, preferences, capabilityForRequest(request), request.moduleId)
        if (request.approvedAt != null && decision != TaskerAuthorizationDecision.DENY) return
        check(decision == TaskerAuthorizationDecision.EXECUTE) {
            when (decision) {
                TaskerAuthorizationDecision.DENY -> "Tasker action is no longer allowed by MMRL policy"
                TaskerAuthorizationDecision.REQUIRE_APPROVAL -> "Tasker action still requires MMRL approval at execution time"
                TaskerAuthorizationDecision.EXECUTE -> "Tasker action is allowed"
            }
        }
    }
}

data class TaskerRootRequest(
    val id: String = UUID.randomUUID().toString(),
    val operationId: String,
    val command: String,
    val moduleId: String,
    val moduleName: String,
    val capability: String = "",
    val reviewToken: String? = null,
    val targetOperationId: String? = null,
    val ashAutomationToken: String? = null,
    val idempotencyKey: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,
    val expiresAt: Long = createdAt + TASKER_ROOT_REQUEST_TTL_MS,
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now > expiresAt

    fun approved(now: Long = System.currentTimeMillis()): TaskerRootRequest = copy(
        approvedAt = now,
        expiresAt = maxOf(expiresAt, now + TASKER_ROOT_REQUEST_TTL_MS),
    )

    fun toJson(): String = buildJsonObject {
        put("id", id)
        put("operation_id", operationId)
        put("command", command)
        put("module_id", moduleId)
        put("module_name", moduleName)
        put("capability", capability)
        reviewToken?.let { put("review_token", it) }
        targetOperationId?.let { put("target_operation_id", it) }
        ashAutomationToken?.let { put("ash_automation_token", it) }
        idempotencyKey?.let { put("idempotency_key", it) }
        put("created_at", createdAt)
        approvedAt?.let { put("approved_at", it) }
        put("expires_at", expiresAt)
    }.toString()

    companion object {
        private val rootRequestJson = Json { ignoreUnknownKeys = true }

        fun fromJson(value: String): TaskerRootRequest {
            val root = rootRequestJson.parseToJsonElement(value).jsonObject
            fun requiredString(name: String): String = root[name]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: error("Missing Tasker root request field: $name")
            fun optionalString(name: String): String? = root[name]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf(String::isNotBlank)

            return TaskerRootRequest(
                id = requiredString("id"),
                operationId = requiredString("operation_id"),
                command = requiredString("command"),
                moduleId = requiredString("module_id"),
                moduleName = optionalString("module_name").orEmpty(),
                capability = optionalString("capability").orEmpty(),
                reviewToken = optionalString("review_token"),
                targetOperationId = optionalString("target_operation_id"),
                ashAutomationToken = optionalString("ash_automation_token"),
                idempotencyKey = optionalString("idempotency_key"),
                createdAt = root["created_at"]?.jsonPrimitive?.longOrNull ?: 0L,
                approvedAt = root["approved_at"]?.jsonPrimitive?.longOrNull,
                expiresAt = root["expires_at"]?.jsonPrimitive?.longOrNull
                    ?: ((root["created_at"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()) + TASKER_ROOT_REQUEST_TTL_MS),
            )
        }

        fun fromJson(value: JSONObject): TaskerRootRequest = fromJson(value.toString())
    }
}

internal object TaskerRootRequestStore {
    private const val RETENTION_MS = 24L * 60L * 60L * 1000L
    private fun directory(context: Context) = File(context.filesDir, "tasker-root-requests").apply { mkdirs() }
    private fun safeId(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun file(context: Context, id: String) = File(directory(context), "${safeId(id)}.json")
    private fun queuedFile(context: Context, id: String) = File(directory(context), "${safeId(id)}.queued")

    @Synchronized
    fun put(context: Context, request: TaskerRootRequest) {
        prune(context)
        writeAtomic(file(context, request.id), request.toJson())
    }

    @Synchronized
    fun get(context: Context, id: String): TaskerRootRequest? = runCatching {
        AtomicFile(file(context, id)).openRead().use { input ->
            TaskerRootRequest.fromJson(input.readBytes().toString(StandardCharsets.UTF_8))
        }
    }.getOrNull()?.takeUnless { request ->
        val expired = request.isExpired()
        if (expired) remove(context, id)
        expired
    }

    @Synchronized
    fun markApproved(context: Context, id: String): TaskerRootRequest? {
        val request = get(context, id) ?: return null
        val approved = request.approved()
        writeAtomic(file(context, id), approved.toJson())
        return approved
    }

    @Synchronized
    fun findByOperationId(context: Context, operationId: String): TaskerRootRequest? {
        prune(context)
        return directory(context)
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.extension == "json" }
            .mapNotNull { source ->
                runCatching {
                    AtomicFile(source).openRead().use { input ->
                        TaskerRootRequest.fromJson(input.readBytes().toString(StandardCharsets.UTF_8))
                    }
                }.getOrNull()
            }
            .firstOrNull { request -> request.operationId == operationId && !request.isExpired() }
    }

    @Synchronized
    fun markEnqueued(context: Context, id: String): Boolean {
        val request = get(context, id) ?: return false
        if (request.isExpired()) {
            remove(context, id)
            return false
        }
        return queuedFile(context, id).createNewFile()
    }

    @Synchronized
    fun clearEnqueued(context: Context, id: String) {
        queuedFile(context, id).delete()
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        file(context, id).delete()
        queuedFile(context, id).delete()
    }

    private fun writeAtomic(target: File, value: String) {
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.write(value.toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun prune(context: Context) {
        val now = System.currentTimeMillis()
        val cutoff = now - RETENTION_MS
        directory(context).listFiles().orEmpty().forEach { source ->
            val expiredJson = source.extension == "json" && runCatching {
                AtomicFile(source).openRead().use { input ->
                    TaskerRootRequest.fromJson(input.readBytes().toString(StandardCharsets.UTF_8)).isExpired(now)
                }
            }.getOrDefault(false)
            if (source.lastModified() < cutoff || expiredJson) source.delete()
        }
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
            val request = OneTimeWorkRequestBuilder<TaskerAutomationWorker>()
                .setInputData(data)
                .addTag("mmrl-tasker-root")
                .addTag("mmrl-tasker-root-$requestId")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "mmrl-tasker-root-$requestId",
                ExistingWorkPolicy.KEEP,
                request,
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
