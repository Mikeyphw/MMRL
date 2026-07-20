package com.dergoogler.mmrl.ash.data

import android.content.Context
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ash.database.ActivityDao
import com.dergoogler.mmrl.ash.database.ActivityEntity
import com.dergoogler.mmrl.ash.model.ActivityItem
import com.dergoogler.mmrl.ash.model.AshCapabilities
import com.dergoogler.mmrl.ash.model.AshGuidanceOutcome
import com.dergoogler.mmrl.ash.model.AshModuleHealth
import com.dergoogler.mmrl.ash.model.AshModuleInstallation
import com.dergoogler.mmrl.ash.model.AshModuleReleaseGate
import com.dergoogler.mmrl.ash.model.AshRecoveryPlan
import com.dergoogler.mmrl.ash.model.AshReleaseCheck
import com.dergoogler.mmrl.ash.model.AshReleaseCheckState
import com.dergoogler.mmrl.ash.model.AshReleaseGateStatus
import com.dergoogler.mmrl.ash.model.AshSnapshot
import com.dergoogler.mmrl.ash.model.Dashboard
import com.dergoogler.mmrl.ash.model.ModuleItem
import com.dergoogler.mmrl.ash.model.OperationResult
import com.dergoogler.mmrl.ash.model.PendingSetting
import com.dergoogler.mmrl.ash.model.QuarantineItem
import com.dergoogler.mmrl.ash.model.SettingItem
import com.dergoogler.mmrl.ash.root.RootServiceClient
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AshRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val rootClient: RootServiceClient,
    private val activityDao: ActivityDao,
) {
    suspend fun rootAvailable(): Boolean = rootClient.rootAvailable()
    suspend fun moduleStateRaw(): String = rootClient.moduleState()
    suspend fun snapshotRaw(activityLimit: Int = 150): String = rootClient.snapshot(activityLimit)
    suspend fun releaseGateRaw(): String = rootClient.releaseGate()

    fun parseModuleInstallation(raw: String): AshModuleInstallation {
        val root = parse(raw)
        return AshModuleInstallation(
            installed = root.optBoolean("installed"),
            active = root.optBoolean("active"),
            folder = root.optString("folder"),
            id = root.optString("id"),
            name = root.optString("name"),
            version = root.optString("version"),
            versionCode = root.optInt("versionCode"),
            source = root.optString("source", "none"),
            controlAvailable = root.optBoolean("controlAvailable"),
            disabled = root.optBoolean("disabled"),
            removalPending = root.optBoolean("removalPending"),
            updatePending = root.optBoolean("updatePending"),
        )
    }

    fun parseReleaseGate(raw: String): AshModuleReleaseGate {
        val root = parse(raw)
        val checks = root.optJSONArray("checks") ?: JSONArray()
        return AshModuleReleaseGate(
            protocolVersion = root.optString("protocolVersion"),
            generatedAt = root.optLong("generatedAt"),
            moduleVersion = root.optString("moduleVersion"),
            moduleVersionCode = root.optInt("moduleVersionCode"),
            status = parseReleaseStatus(root.optString("status")),
            checks = buildList {
                for (index in 0 until checks.length()) {
                    val item = checks.optJSONObject(index) ?: continue
                    add(
                        AshReleaseCheck(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            state = parseReleaseCheckState(item.optString("state")),
                            detail = item.optString("detail"),
                        ),
                    )
                }
            },
        )
    }

    suspend fun parseSnapshot(raw: String): AshSnapshot {
        val root = parse(raw)
        val schemaVersion = root.optInt("schemaVersion")
        require(schemaVersion in SUPPORTED_SNAPSHOT_SCHEMA_MIN..SUPPORTED_SNAPSHOT_SCHEMA_MAX) {
            "Unsupported AshReXcue snapshot schema $schemaVersion"
        }

        val capabilities = parseCapabilities(root.optJSONObject("capabilities") ?: JSONObject())
        val pendingSettings = parsePendingSettings(root.optJSONArray("pendingSettings") ?: JSONArray())
        val pendingByKey = pendingSettings.associate { item -> item.key to item.value }
        val remoteActivity = parseActivity(root.optJSONArray("activity") ?: JSONArray())
        val localActivity = activityDao.recent(150).map { entity -> entity.toModel() }

        return AshSnapshot(
            schemaVersion = schemaVersion,
            generatedAt = root.optLong("generatedAt"),
            recoveryRevision = root.optString("recoveryRevision"),
            capabilities = capabilities,
            dashboard = parseDashboard(root.optJSONObject("dashboard") ?: JSONObject()),
            modules = parseModules(root.optJSONArray("modules") ?: JSONArray()),
            quarantine = parseQuarantine(root.optJSONArray("quarantine") ?: JSONArray()),
            activity = (remoteActivity + localActivity)
                .distinctBy(ActivityItem::id)
                .sortedByDescending(ActivityItem::timestamp)
                .take(200),
            settings = parseSettings(
                root.optJSONArray("settings") ?: JSONArray(),
                pendingByKey,
            ),
            pendingSettings = pendingSettings,
            health = parseHealth(root.optJSONObject("health") ?: JSONObject()),
        )
    }

    suspend fun setSetting(key: String, value: String): OperationResult =
        mutation("setting", context.getString(R.string.ash_activity_updated_setting, key), "$key=$value") { rootClient.setSetting(key, value) }

    suspend fun setSettings(values: Map<String, String>): OperationResult =
        mutation(
            type = "settings",
            title = context.getString(R.string.ash_activity_updated_protection_settings),
            details = values.entries.joinToString("\n") { (key, value) -> "$key=$value" },
        ) { rootClient.setSettings(values) }

    suspend fun setTrust(folder: String, trust: String): OperationResult =
        mutation("trust", context.getString(R.string.ash_activity_changed_module_trust), "$folder → $trust") {
            rootClient.setTrust(folder, trust)
        }

    suspend fun restoreOne(folder: String): OperationResult =
        mutation("restoration", context.getString(R.string.ash_activity_started_restoration_trial), folder) {
            rootClient.restoreOne(folder)
        }

    suspend fun restoreHalf(): OperationResult =
        mutation("restoration", context.getString(R.string.ash_activity_started_half_restoration_trial), context.getString(R.string.ash_activity_binary_search_batch)) {
            rootClient.restoreHalf()
        }

    suspend fun restoreBatch(folders: List<String>): OperationResult =
        mutation(
            "restoration",
            context.getString(R.string.ash_activity_started_guided_restoration_trial),
            folders.joinToString("\n"),
        ) {
            rootClient.restoreBatch(folders)
        }

    suspend fun executeRecoveryPlan(plan: AshRecoveryPlan): OperationResult =
        mutation(
            "recovery-plan",
            context.getString(R.string.ash_activity_started_plan, plan.title),
            buildString {
                append("plan=").append(plan.id).append('\n')
                append("preset=").append(plan.preset.name.lowercase()).append('\n')
                append("revision=").append(plan.recoveryRevision).append('\n')
                append("modules=").append(plan.affectedFolders.joinToString(",")).append('\n')
                append("rollback=").append(plan.rollbackStrategy)
            },
        ) {
            rootClient.executeRecoveryPlan(plan.id, plan.recoveryRevision, plan.affectedFolders)
        }

    suspend fun restoreAll(): OperationResult =
        mutation("restoration", context.getString(R.string.ash_activity_started_full_restoration_trial), context.getString(R.string.ash_activity_all_quarantined_modules)) {
            rootClient.restoreAll()
        }

    suspend fun completeTrial(): OperationResult =
        mutation("restoration", context.getString(R.string.ash_activity_completed_restoration_trial), context.getString(R.string.ash_activity_restored_batch_accepted)) {
            rootClient.completeTrial()
        }

    suspend fun rollbackTrial(): OperationResult =
        mutation("restoration", context.getString(R.string.ash_activity_rolled_back_restoration_trial), context.getString(R.string.ash_activity_restored_batch_requarantined)) {
            rootClient.rollbackTrial()
        }

    suspend fun discardPending(): OperationResult =
        mutation("settings", context.getString(R.string.ash_activity_discarded_queued_settings), context.getString(R.string.ash_activity_pending_changes_removed)) {
            rootClient.discardPendingSettings()
        }

    suspend fun exportDiagnostics(): OperationResult =
        mutation("diagnostics", context.getString(R.string.ash_activity_exported_diagnostics), context.getString(R.string.ash_activity_sanitized_diagnostic_archive)) {
            rootClient.exportDiagnostics()
        }

    suspend fun repairState(): OperationResult =
        mutation("state-repair", context.getString(R.string.ash_activity_repaired_state), context.getString(R.string.ash_activity_repaired_state_detail)) {
            rootClient.repairState()
        }

    suspend fun recordGuidanceOutcome(
        recommendationId: String,
        moduleFolder: String,
        outcome: AshGuidanceOutcome,
    ): OperationResult {
        require(recommendationId.length in 1..128 && recommendationId.all(::isGuidanceTokenCharacter)) {
            "Invalid guidance recommendation"
        }
        require(moduleFolder.length <= 128 && moduleFolder.all(::isGuidanceTokenCharacter)) {
            "Invalid module folder"
        }
        val now = System.currentTimeMillis() / 1000L
        activityDao.insert(
            ActivityEntity(
                id = "guidance-${UUID.randomUUID()}",
                timestamp = now,
                type = "guidance",
                title = context.getString(R.string.ash_activity_recovery_guidance_outcome),
                subtitle = context.getString(outcome.titleResource),
                status = outcome.wireValue,
                details = buildString {
                    append("recommendation=").append(recommendationId).append('\n')
                    append("module=").append(moduleFolder).append('\n')
                    append("outcome=").append(outcome.wireValue)
                },
            ),
        )
        activityDao.trim(300)
        return OperationResult(
            ok = true,
            message = context.getString(R.string.ash_activity_guidance_recorded_message, outcome.wireValue),
        )
    }

    private suspend fun mutation(
        type: String,
        title: String,
        details: String,
        block: suspend () -> String,
    ): OperationResult {
        val root = parseObject(block())
        val result = OperationResult(
            ok = root.optBoolean("ok"),
            message = root.optString(
                "message",
                if (root.optBoolean("ok")) context.getString(R.string.completed) else context.getString(R.string.operation_failed),
            ),
            path = root.optString("path").takeIf(String::isNotBlank),
        )
        val now = System.currentTimeMillis() / 1000L
        activityDao.insert(
            ActivityEntity(
                id = "app-${UUID.randomUUID()}",
                timestamp = now,
                type = type,
                title = title,
                subtitle = result.message,
                status = when {
                    !result.ok -> "failed"
                    result.message.contains("queued", ignoreCase = true) -> "queued"
                    else -> "success"
                },
                details = buildString {
                    append(details)
                    result.path?.let { path -> append("\n").append(path) }
                },
            ),
        )
        activityDao.trim(300)
        return result
    }

    private fun parseObject(raw: String): JSONObject = runCatching { JSONObject(raw) }
        .getOrElse { throw IllegalStateException("Invalid AshReXcue response") }

    private fun parse(raw: String): JSONObject = parseObject(raw).also { json ->
        if (json.optBoolean("ok", true).not()) {
            throw IllegalStateException(json.optString("message", "AshReXcue operation failed"))
        }
    }

    private fun parseReleaseStatus(value: String): AshReleaseGateStatus = when (value.lowercase()) {
        "ready" -> AshReleaseGateStatus.Ready
        "ready-with-warnings", "ready_with_warnings", "warning" -> AshReleaseGateStatus.ReadyWithWarnings
        else -> AshReleaseGateStatus.Blocked
    }

    private fun parseReleaseCheckState(value: String): AshReleaseCheckState = when (value.lowercase()) {
        "pass", "passed", "ok" -> AshReleaseCheckState.Pass
        "warning", "warn" -> AshReleaseCheckState.Warning
        else -> AshReleaseCheckState.Blocker
    }

    private fun parseHealth(root: JSONObject): AshModuleHealth = AshModuleHealth(
        schemaVersion = root.optInt("schemaVersion"),
        status = root.optString("status", "unknown"),
        issueCount = root.optInt("issueCount"),
        repairCount = root.optInt("repairCount"),
        lastRepairAt = root.optLong("lastRepairAt"),
        summary = root.optString("summary"),
    )

    private fun parseCapabilities(root: JSONObject): AshCapabilities {
        val features = root.optJSONArray("features") ?: JSONArray()
        return AshCapabilities(
            apiVersion = root.optInt("apiVersion"),
            minimumClientApi = root.optInt("minimumClientApi"),
            moduleVersion = root.optString("moduleVersion"),
            moduleVersionCode = root.optInt("moduleVersionCode"),
            features = buildSet {
                for (index in 0 until features.length()) {
                    features.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            },
        )
    }

    private fun parseModules(items: JSONArray): List<ModuleItem> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            add(
                ModuleItem(
                    folder = item.optString("folder"),
                    id = item.optString("id"),
                    name = item.optString("name", item.optString("id")),
                    version = item.optString("version"),
                    versionCode = item.optString("versionCode"),
                    enabled = item.optBoolean("enabled"),
                    quarantined = item.optBoolean("quarantined"),
                    trust = item.optString("trust", "normal"),
                    baseTrust = item.optString("baseTrust", item.optString("trust", "normal")),
                    fingerprint = item.optString("fingerprint"),
                    changedSinceStable = item.optBoolean("changedSinceStable"),
                ),
            )
        }
    }

    private fun parseQuarantine(items: JSONArray): List<QuarantineItem> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            add(
                QuarantineItem(
                    folder = item.optString("folder"),
                    id = item.optString("id"),
                    name = item.optString("name", item.optString("id")),
                    trust = item.optString("trust", "normal"),
                    rescueId = item.optString("rescueId"),
                    disabledAt = item.optLong("disabledAt"),
                    exists = item.optBoolean("exists"),
                    disablePresent = item.optBoolean("disablePresent"),
                    reason = item.optString("reason"),
                ),
            )
        }
    }

    private fun parseActivity(items: JSONArray): List<ActivityItem> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            add(
                ActivityItem(
                    id = item.optString("id"),
                    timestamp = item.optLong("timestamp"),
                    type = item.optString("type"),
                    title = item.optString("title"),
                    subtitle = item.optString("subtitle"),
                    status = item.optString("status"),
                    details = item.optString("details"),
                ),
            )
        }
    }

    private fun parseSettings(
        items: JSONArray,
        pendingByKey: Map<String, String>,
    ): List<SettingItem> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val key = item.optString("key")
            add(
                SettingItem(
                    key = key,
                    value = item.optString("value"),
                    queuedValue = pendingByKey[key],
                    editable = item.optBoolean("editable", true),
                ),
            )
        }
    }

    private fun parsePendingSettings(items: JSONArray): List<PendingSetting> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            add(
                PendingSetting(
                    key = item.optString("key"),
                    value = item.optString("value"),
                    current = item.optString("current"),
                ),
            )
        }
    }

    private fun parseDashboard(root: JSONObject): Dashboard {
        val module = root.optJSONObject("module") ?: JSONObject()
        val boot = root.optJSONObject("boot") ?: JSONObject()
        val rescue = root.optJSONObject("rescue") ?: JSONObject()
        val timing = root.optJSONObject("timing") ?: JSONObject()
        val modules = root.optJSONObject("modules") ?: JSONObject()
        val latest = root.optJSONObject("latestRescue") ?: JSONObject()
        val settings = root.optJSONObject("settings") ?: JSONObject()
        return Dashboard(
            version = module.optString("version", "—"),
            versionCode = module.optInt("versionCode"),
            rootManager = module.optString("root", "Unknown"),
            bootState = boot.optString("state", "unknown"),
            bootReason = boot.optString("reason"),
            loops = boot.optInt("loops"),
            threshold = boot.optInt("threshold"),
            rescueStage = rescue.optInt("stage"),
            rescueStageLabel = rescue.optString("stageLabel", "unknown"),
            nextRescue = rescue.optString("next", "Unknown"),
            quarantined = rescue.optInt("quarantined"),
            restoreState = rescue.optString("restoreState", "idle"),
            restoreCount = rescue.optInt("restoreCount"),
            timeout = timing.optInt("timeout"),
            timeoutMinimum = timing.optInt("minimum"),
            timeoutMaximum = timing.optInt("maximum"),
            stability = timing.optInt("stability"),
            enabledModules = modules.optInt("enabled"),
            disabledModules = modules.optInt("disabled"),
            protectedModules = modules.optInt("protected"),
            trustedModules = modules.optInt("trusted"),
            suspectModules = modules.optInt("suspect"),
            latestRescueId = latest.optString("id"),
            latestRescueStatus = latest.optString("status"),
            latestRescueReason = latest.optString("reason"),
            repairCount = settings.optInt("repairCount"),
        )
    }

    private fun isGuidanceTokenCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character == '.' || character == '_' || character == '-'

    private fun ActivityEntity.toModel(): ActivityItem = ActivityItem(
        id = id,
        timestamp = timestamp,
        type = type,
        title = title,
        subtitle = subtitle,
        status = status,
        details = details,
    )

    private val AshGuidanceOutcome.titleResource: Int
        get() = when (this) {
            AshGuidanceOutcome.Helped -> R.string.guidance_outcome_helped
            AshGuidanceOutcome.Failed -> R.string.guidance_outcome_failed
            AshGuidanceOutcome.Inconclusive -> R.string.guidance_outcome_inconclusive
        }

    private companion object {
        const val SUPPORTED_SNAPSHOT_SCHEMA_MIN = 1
        const val SUPPORTED_SNAPSHOT_SCHEMA_MAX = 2
    }
}
