package com.dergoogler.mmrl.ash.data

import com.dergoogler.mmrl.ash.database.ActivityDao
import com.dergoogler.mmrl.ash.database.ActivityEntity
import com.dergoogler.mmrl.ash.model.ActivityItem
import com.dergoogler.mmrl.ash.model.AshCapabilities
import com.dergoogler.mmrl.ash.model.AshModuleInstallation
import com.dergoogler.mmrl.ash.model.AshSnapshot
import com.dergoogler.mmrl.ash.model.Dashboard
import com.dergoogler.mmrl.ash.model.ModuleItem
import com.dergoogler.mmrl.ash.model.OperationResult
import com.dergoogler.mmrl.ash.model.PendingSetting
import com.dergoogler.mmrl.ash.model.QuarantineItem
import com.dergoogler.mmrl.ash.model.SettingItem
import com.dergoogler.mmrl.ash.root.RootServiceClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AshRepository @Inject constructor(
    private val rootClient: RootServiceClient,
    private val activityDao: ActivityDao,
) {
    suspend fun rootAvailable(): Boolean = rootClient.rootAvailable()
    suspend fun moduleStateRaw(): String = rootClient.moduleState()
    suspend fun snapshotRaw(activityLimit: Int = 150): String = rootClient.snapshot(activityLimit)

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

    suspend fun parseSnapshot(raw: String): AshSnapshot {
        val root = parse(raw)
        val schemaVersion = root.optInt("schemaVersion")
        require(schemaVersion == SUPPORTED_SNAPSHOT_SCHEMA) {
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
        )
    }

    suspend fun setSetting(key: String, value: String): OperationResult =
        mutation("setting", "Updated $key", "$key=$value") { rootClient.setSetting(key, value) }

    suspend fun setSettings(values: Map<String, String>): OperationResult =
        mutation(
            type = "settings",
            title = "Updated protection settings",
            details = values.entries.joinToString("\n") { (key, value) -> "$key=$value" },
        ) { rootClient.setSettings(values) }

    suspend fun setTrust(folder: String, trust: String): OperationResult =
        mutation("trust", "Changed module trust", "$folder → $trust") {
            rootClient.setTrust(folder, trust)
        }

    suspend fun restoreOne(folder: String): OperationResult =
        mutation("restoration", "Started restoration trial", folder) {
            rootClient.restoreOne(folder)
        }

    suspend fun restoreHalf(): OperationResult =
        mutation("restoration", "Started half restoration trial", "Binary-search batch") {
            rootClient.restoreHalf()
        }

    suspend fun restoreAll(): OperationResult =
        mutation("restoration", "Started full restoration trial", "All quarantined modules") {
            rootClient.restoreAll()
        }

    suspend fun completeTrial(): OperationResult =
        mutation("restoration", "Completed restoration trial", "Restored batch accepted") {
            rootClient.completeTrial()
        }

    suspend fun rollbackTrial(): OperationResult =
        mutation("restoration", "Rolled back restoration trial", "Restored batch re-quarantined") {
            rootClient.rollbackTrial()
        }

    suspend fun discardPending(): OperationResult =
        mutation("settings", "Discarded queued settings", "Pending changes removed") {
            rootClient.discardPendingSettings()
        }

    suspend fun exportDiagnostics(): OperationResult =
        mutation("diagnostics", "Exported diagnostics", "Sanitized diagnostic archive") {
            rootClient.exportDiagnostics()
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
                if (root.optBoolean("ok")) "Completed" else "Operation failed",
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

    private fun ActivityEntity.toModel(): ActivityItem = ActivityItem(
        id = id,
        timestamp = timestamp,
        type = type,
        title = title,
        subtitle = subtitle,
        status = status,
        details = details,
    )

    private companion object {
        const val SUPPORTED_SNAPSHOT_SCHEMA = 1
    }
}
