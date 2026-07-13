package com.dergoogler.mmrl.tasker

import android.content.Context
import com.dergoogler.mmrl.installer.ArchiveInspection
import com.dergoogler.mmrl.installer.ArchiveInspector
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TaskerReviewToken(
    val token: String = UUID.randomUUID().toString(),
    val operationId: String,
    val archivePath: String,
    val sha256: String,
    val moduleId: String,
    val moduleName: String,
    val installedVersion: String?,
    val targetVersion: String,
    val targetVersionCode: Int,
    val repository: String,
    val verified: Boolean,
    val inspectionSummary: String,
    val warnings: List<String>,
    val blockedReasons: List<String>,
    val routine: Boolean,
    val expiresAt: Long = System.currentTimeMillis() + VALIDITY_MS,
) {
    val canExecute get() = blockedReasons.isEmpty() && System.currentTimeMillis() < expiresAt

    fun toJson() = JSONObject()
        .put("token", token)
        .put("operation_id", operationId)
        .put("archive_path", archivePath)
        .put("sha256", sha256)
        .put("module_id", moduleId)
        .put("module_name", moduleName)
        .put("installed_version", installedVersion)
        .put("target_version", targetVersion)
        .put("target_version_code", targetVersionCode)
        .put("repository", repository)
        .put("verified", verified)
        .put("inspection_summary", inspectionSummary)
        .put("warnings", JSONArray(warnings))
        .put("blocked_reasons", JSONArray(blockedReasons))
        .put("routine", routine)
        .put("expires_at", expiresAt)

    companion object {
        const val VALIDITY_MS = 30L * 60L * 1000L
        fun fromJson(value: JSONObject) = TaskerReviewToken(
            token = value.getString("token"),
            operationId = value.getString("operation_id"),
            archivePath = value.getString("archive_path"),
            sha256 = value.getString("sha256"),
            moduleId = value.getString("module_id"),
            moduleName = value.optString("module_name"),
            installedVersion = value.optString("installed_version").takeIf(String::isNotBlank),
            targetVersion = value.optString("target_version"),
            targetVersionCode = value.optInt("target_version_code", -1),
            repository = value.optString("repository"),
            verified = value.optBoolean("verified"),
            inspectionSummary = value.optString("inspection_summary"),
            warnings = value.optJSONArray("warnings").strings(),
            blockedReasons = value.optJSONArray("blocked_reasons").strings(),
            routine = value.optBoolean("routine"),
            expiresAt = value.getLong("expires_at"),
        )
    }
}

internal object TaskerReviewTokenStore {
    private fun rootDirectory(context: Context) = File(context.filesDir, "tasker-reviews").apply { mkdirs() }
    private fun tokenDirectory(context: Context) = File(rootDirectory(context), "tokens").apply { mkdirs() }
    fun archiveDirectory(context: Context) = File(rootDirectory(context), "archives").apply { mkdirs() }

    private fun safeToken(token: String) = token.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun file(context: Context, token: String) = File(tokenDirectory(context), "${safeToken(token)}.json")
    private fun claimFile(context: Context, token: String) = File(tokenDirectory(context), "${safeToken(token)}.claim")

    @Synchronized
    fun put(context: Context, value: TaskerReviewToken) {
        prune(context)
        file(context, value.token).writeText(value.toJson().toString())
    }

    fun get(context: Context, token: String): TaskerReviewToken? = runCatching {
        TaskerReviewToken.fromJson(JSONObject(file(context, token).readText()))
    }.getOrNull()

    @Synchronized
    fun claim(context: Context, token: String, operationId: String) {
        require(file(context, token).isFile) { "Review token not found" }
        val claim = claimFile(context, token)
        if (claim.exists()) {
            require(claim.readText() == operationId) { "Review token is already being used" }
            return
        }
        require(claim.createNewFile()) { "Unable to claim review token" }
        claim.writeText(operationId)
    }

    fun requireClaim(context: Context, token: String, operationId: String) {
        val claim = claimFile(context, token)
        require(claim.isFile && claim.readText() == operationId) { "Review token is not claimed by this operation" }
    }

    @Synchronized
    fun releaseClaim(context: Context, token: String, operationId: String) {
        val claim = claimFile(context, token)
        if (claim.isFile && claim.readText() == operationId) claim.delete()
    }

    @Synchronized
    fun remove(context: Context, token: String, deleteArchive: Boolean = true) {
        val value = get(context, token)
        file(context, token).delete()
        claimFile(context, token).delete()
        if (deleteArchive) value?.archivePath?.let(::File)?.takeIf { isManagedArchive(context, it) }?.delete()
    }

    suspend fun validate(context: Context, token: String): TaskerReviewToken {
        val value = get(context, token) ?: throw NoSuchElementException("Review token not found")
        require(value.expiresAt > System.currentTimeMillis()) { "Review token expired" }
        val archive = File(value.archivePath)
        require(archive.isFile && archive.length() > 0L) { "Reviewed archive is unavailable" }
        val inspection = ArchiveInspector.inspect(archive)
        require(inspection.sha256 == value.sha256) { "Reviewed archive changed after approval" }
        require(inspection.canInstall) { "Archive no longer passes safety inspection" }
        return value
    }

    suspend fun validateClaimed(context: Context, token: String, operationId: String): TaskerReviewToken {
        requireClaim(context, token, operationId)
        return validate(context, token)
    }

    private fun isManagedArchive(context: Context, file: File): Boolean = runCatching {
        val root = archiveDirectory(context).canonicalFile
        val candidate = file.canonicalFile
        candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)
    }.getOrDefault(false)

    private fun prune(context: Context) {
        val now = System.currentTimeMillis()
        tokenDirectory(context).listFiles { source -> source.extension == "json" }.orEmpty().forEach { source ->
            val token = runCatching { TaskerReviewToken.fromJson(JSONObject(source.readText())) }.getOrNull()
            if (token == null || token.expiresAt <= now) {
                token?.archivePath?.let(::File)?.takeIf { isManagedArchive(context, it) }?.delete()
                token?.token?.let { claimFile(context, it).delete() }
                source.delete()
            }
        }
        // Orphaned claims are safe to discard when their token metadata no longer exists.
        tokenDirectory(context).listFiles { source -> source.extension == "claim" }.orEmpty().forEach { claim ->
            val tokenName = claim.nameWithoutExtension
            if (!File(tokenDirectory(context), "$tokenName.json").isFile) claim.delete()
        }
    }
}

internal fun ArchiveInspection.isRoutineAutomation(verified: Boolean): Boolean =
    verified && canInstall && !hasBootScripts && apks.isEmpty() && sePolicyFiles.isEmpty() &&
        remoteExecutionFiles.isEmpty() && propertyFiles.isEmpty()

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }
