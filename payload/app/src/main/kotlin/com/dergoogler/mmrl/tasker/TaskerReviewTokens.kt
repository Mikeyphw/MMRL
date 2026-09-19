package com.dergoogler.mmrl.tasker

import android.content.Context
import android.util.AtomicFile
import com.dergoogler.mmrl.installer.ArchiveInspection
import com.dergoogler.mmrl.installer.ArchiveInspector
import com.dergoogler.mmrl.installer.OperationStoragePolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
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
    private fun rootDirectory(context: Context) = File(context.filesDir, "tasker-reviews").also { directory ->
        if (!directory.exists()) require(directory.mkdirs()) { "Unable to create Tasker review root" }
        require(directory.isDirectory) { "Tasker review root is not a directory" }
    }
    private fun tokenDirectory(context: Context) = File(rootDirectory(context), "tokens").also { directory ->
        if (!directory.exists()) require(directory.mkdir()) { "Unable to create Tasker token directory" }
        require(directory.isDirectory) { "Tasker token path is not a directory" }
    }
    fun archiveDirectory(context: Context) = File(rootDirectory(context), "archives").also { directory ->
        if (!directory.exists()) require(directory.mkdir()) { "Unable to create Tasker archive root" }
        require(directory.isDirectory) { "Tasker archive root is not a directory" }
    }
    fun archiveDirectory(context: Context, operationId: String): File {
        val root = archiveDirectory(context)
        val directory = OperationStoragePolicy.operationDirectory(root, operationId)
        if (!directory.exists()) require(directory.mkdir()) { "Unable to create review archive directory" }
        require(directory.isDirectory) { "Review archive path is not a directory" }
        return directory
    }

    fun deleteArchiveDirectory(context: Context, operationId: String): Boolean =
        OperationStoragePolicy.deleteOperationDirectory(archiveDirectory(context), operationId)

    // Tokens are logical identifiers too: never expose caller-controlled token text as a filename.
    private fun safeToken(token: String) = OperationStoragePolicy.safeOperationId(token).removePrefix("op-")
    private fun file(context: Context, token: String) = File(tokenDirectory(context), "${safeToken(token)}.json")
    private fun claimFile(context: Context, token: String) = File(tokenDirectory(context), "${safeToken(token)}.claim")

    private fun deleteTokenFile(file: File): Boolean = Files.deleteIfExists(file.toPath())

    @Synchronized
    fun put(context: Context, value: TaskerReviewToken) {
        prune(context)
        val target = AtomicFile(file(context, value.token))
        val stream = target.startWrite()
        try {
            stream.write(value.toJson().toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Throwable) {
            target.failWrite(stream)
            throw error
        }
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
        try {
            Files.writeString(
                claim.toPath(),
                operationId,
                Charsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            )
        } catch (error: java.nio.file.FileAlreadyExistsException) {
            require(claim.readText() == operationId) { "Review token is already being used" }
        }
    }

    fun requireClaim(context: Context, token: String, operationId: String) {
        val claim = claimFile(context, token)
        require(claim.isFile && claim.readText() == operationId) { "Review token is not claimed by this operation" }
    }

    @Synchronized
    fun releaseClaim(context: Context, token: String, operationId: String) {
        val claim = claimFile(context, token)
        if (claim.isFile && claim.readText() == operationId) deleteTokenFile(claim)
    }

    @Synchronized
    fun remove(context: Context, token: String, deleteArchive: Boolean = true) {
        val value = get(context, token)
        if (deleteArchive) value?.archivePath?.let(::File)?.takeIf { isManagedArchive(context, it) }?.let { archive ->
            val operationDirectory = archive.parentFile ?: error("Reviewed archive has no operation directory")
            OperationStoragePolicy.deleteDirectChild(archiveDirectory(context), operationDirectory)
        }
        deleteTokenFile(file(context, token))
        deleteTokenFile(claimFile(context, token))
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
        OperationStoragePolicy.requireManagedFile(archiveDirectory(context), file)
        true
    }.getOrDefault(false)

    private fun prune(context: Context) {
        val now = System.currentTimeMillis()
        tokenDirectory(context).listFiles { source -> source.extension == "json" }.orEmpty().forEach { source ->
            val token = runCatching { TaskerReviewToken.fromJson(JSONObject(source.readText())) }.getOrNull()
            if (token == null || token.expiresAt <= now) {
                token?.archivePath?.let(::File)?.takeIf { isManagedArchive(context, it) }?.let { archive ->
                    val operationDirectory = archive.parentFile ?: error("Reviewed archive has no operation directory")
                    OperationStoragePolicy.deleteDirectChild(archiveDirectory(context), operationDirectory)
                }
                token?.token?.let { deleteTokenFile(claimFile(context, it)) }
                deleteTokenFile(source)
            }
        }
        archiveDirectory(context).listFiles().orEmpty()
            .filter { it.isDirectory || java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .map { OperationStoragePolicy.requireDirectChild(archiveDirectory(context), it) }
            .filter { it.lastModified() < now - TaskerReviewToken.VALIDITY_MS - 60L * 60L * 1000L }
            .forEach { OperationStoragePolicy.deleteDirectChild(archiveDirectory(context), it) }
        // Orphaned claims are safe to discard when their token metadata no longer exists.
        tokenDirectory(context).listFiles { source -> source.extension == "claim" }.orEmpty().forEach { claim ->
            val tokenName = claim.nameWithoutExtension
            if (!File(tokenDirectory(context), "$tokenName.json").isFile) deleteTokenFile(claim)
        }
    }
}

internal fun ArchiveInspection.isRoutineAutomation(verified: Boolean): Boolean =
    verified && canInstall && !hasBootScripts && apks.isEmpty() && sePolicyFiles.isEmpty() &&
        remoteExecutionFiles.isEmpty() && propertyFiles.isEmpty()

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }
