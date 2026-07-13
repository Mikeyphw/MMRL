package com.dergoogler.mmrl.tasker

import android.content.Context
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.installer.ArchiveInspector
import com.dergoogler.mmrl.model.ModuleIdentity
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipFile

class PrepareReviewedInstallRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> = taskerAction {
        runBlocking(Dispatchers.IO) {
            val request = input.regular
            val moduleIdInput = request.moduleId?.trim()?.takeIf(String::isNotEmpty)
            val sourceOperationId = request.operationId?.trim()?.takeIf(String::isNotEmpty)
            require(moduleIdInput != null || sourceOperationId != null) { "Provide a module ID or completed download operation ID" }
            val repos = TaskerRuntime.repositories(context)
            val prefs = repos.userPreferencesRepository().data.first()
            require(prefs.taskerIntegrationEnabled && prefs.taskerAllowReviewedInstalls) {
                "Reviewed Tasker installs are disabled in MMRL settings"
            }
            val online = moduleIdInput?.let { TaskerRuntime.newestOnline(context, it) }
            val operationId = repos.operationHistoryRepository().start(
                kind = OperationKind.PREPARE_INSTALL,
                title = online?.name ?: moduleIdInput ?: "Prepare reviewed module",
                summary = "Preparing Tasker review token",
                moduleId = online?.id ?: moduleIdInput,
                moduleName = online?.name,
                parentId = sourceOperationId,
                origin = "TASKER",
            )
            try {
                val archive = when {
                    sourceOperationId != null -> {
                        val source = repos.operationHistoryRepository().getById(sourceOperationId)
                            ?: throw NoSuchElementException("Download operation not found")
                        require(source.status == "SUCCEEDED") { "Download operation has not completed successfully" }
                        source.destinationPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
                            ?: throw IllegalStateException("Downloaded archive is unavailable")
                    }
                    online != null -> {
                        val version = online.versions.maxByOrNull { it.versionCode }
                        val url = version?.zipUrl ?: throw IllegalArgumentException("Repository module has no download URL")
                        repos.operationHistoryRepository().phase(operationId, OperationPhase.DOWNLOAD, "Downloading review archive")
                        downloadReviewArchive(context, url)
                    }
                    else -> throw NoSuchElementException("Repository module not found: $moduleIdInput")
                }
                repos.operationHistoryRepository().phase(operationId, OperationPhase.VERIFY, "Calculating archive digest")
                repos.operationHistoryRepository().phase(operationId, OperationPhase.INSPECT, "Inspecting reviewed archive")
                val inspection = ArchiveInspector.inspect(archive)
                repos.operationHistoryRepository().inspectionSummary(operationId, inspection.summary)
                repos.operationHistoryRepository().appendLog(operationId, "SHA-256: ${inspection.sha256}")
                inspection.warnings.forEach { repos.operationHistoryRepository().appendLog(operationId, "Warning: $it") }
                inspection.blockedReasons.forEach { repos.operationHistoryRepository().appendLog(operationId, "Blocked: $it") }
                val manifest = readModuleManifest(archive)
                val manifestId = manifest["id"]
                    ?.let(TaskerAutomationPolicy::requireSafeModuleId)
                    ?: throw IllegalArgumentException("Archive module.prop does not contain a valid module ID")
                if (online != null) {
                    require(ModuleIdentity.matches(online.id, manifestId)) {
                        "Repository module ID does not match the downloaded archive"
                    }
                }
                if (moduleIdInput != null) {
                    require(ModuleIdentity.matches(moduleIdInput, manifestId)) {
                        "Requested module ID does not match the downloaded archive"
                    }
                }
                val moduleId = manifestId
                val moduleName = online?.name ?: manifest["name"].orEmpty().ifBlank { moduleId }
                val targetVersion = manifest["version"].orEmpty().ifBlank { online?.version.orEmpty() }
                val targetVersionCode = manifest["versionCode"]?.toIntOrNull() ?: online?.versionCode ?: -1
                val local = repos.localRepository().getLocalByIdOrNull(ModuleIdentity.normalize(moduleId))
                val verified = online?.isVerified == true
                val routine = inspection.isRoutineAutomation(verified)
                val token = TaskerReviewToken(
                    operationId = operationId,
                    archivePath = archive.absolutePath,
                    sha256 = inspection.sha256,
                    moduleId = moduleId,
                    moduleName = moduleName,
                    installedVersion = local?.version,
                    targetVersion = targetVersion,
                    targetVersionCode = targetVersionCode,
                    repository = online?.repoUrl.orEmpty(),
                    verified = verified,
                    inspectionSummary = inspection.summary,
                    warnings = inspection.warnings,
                    blockedReasons = inspection.blockedReasons,
                    routine = routine,
                )
                TaskerReviewTokenStore.put(context, token)
                if (inspection.canInstall) {
                    repos.operationHistoryRepository().succeed(operationId, "Review token prepared")
                } else {
                    repos.operationHistoryRepository().fail(operationId, "Archive failed safety inspection")
                }
                TaskerResultOutput(
                    success = inspection.canInstall,
                    status = if (inspection.canInstall) "REVIEW_READY" else "BLOCKED",
                    message = if (inspection.canInstall) "Reviewed installation is ready" else "Archive was blocked by inspection",
                    operationId = operationId,
                    operationType = OperationKind.PREPARE_INSTALL.name,
                    moduleId = moduleId,
                    moduleName = moduleName,
                    installed = local != null,
                    installedVersion = local?.version.orEmpty(),
                    installedVersionCode = local?.versionCode ?: -1,
                    availableVersion = targetVersion,
                    availableVersionCode = targetVersionCode,
                    repository = online?.repoUrl.orEmpty(),
                    reviewToken = if (inspection.canInstall) token.token else "",
                    reviewExpiresAt = token.expiresAt,
                    safetyLevel = if (!inspection.canInstall) "BLOCKED" else if (routine) "ROUTINE" else "REVIEW_REQUIRED",
                    inspectionSummary = inspection.summary,
                    resultJson = token.toJson()
                        .put("scripts", JSONArray(inspection.scripts))
                        .put("binaries", JSONArray(inspection.nativeBinaries))
                        .put("apks", JSONArray(inspection.apks))
                        .put("selinux", JSONArray(inspection.sePolicyFiles))
                        .put("properties", JSONArray(inspection.propertyFiles))
                        .toString(),
                )
            } catch (error: Throwable) {
                repos.operationHistoryRepository().fail(operationId, error.message ?: "Unable to prepare reviewed install", error)
                throw error
            }
        }
    }
}

class ExecuteReviewedInstallRunner : TaskerPluginRunnerAction<TaskerRequestInput, TaskerResultOutput>() {
    override fun run(context: Context, input: TaskerInput<TaskerRequestInput>): TaskerPluginResult<TaskerResultOutput> = taskerAction {
        val tokenInput = input.regular.reviewToken?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Review token is required")
        runBlocking(Dispatchers.IO) {
            val token = TaskerReviewTokenStore.validate(context, tokenInput)
            val repos = TaskerRuntime.repositories(context)
            val prefs = repos.userPreferencesRepository().data.first()
            val policyDecision = TaskerAuthorizationPolicy.decide(
                context,
                prefs,
                TaskerCapability.REVIEWED_INSTALL,
                token.moduleId,
            )
            // A module that adds privileged boot behavior, APKs, SELinux policy,
            // remote execution, or property changes always requires explicit approval.
            val decision = TaskerAuthorizationPolicy.reviewedInstallDecision(
                policyDecision = policyDecision,
                routine = token.routine,
            )
            val local = repos.localRepository().getLocalByIdOrNull(token.moduleId)
            val kind = if (local == null) OperationKind.INSTALL else OperationKind.UPDATE
            val operationId = repos.operationHistoryRepository().start(
                kind = kind,
                title = token.moduleName,
                summary = "Reviewed Tasker ${kind.name.lowercase()}",
                moduleId = token.moduleId,
                moduleName = token.moduleName,
                sourceUri = File(token.archivePath).toURI().toString(),
                destinationPath = token.archivePath,
                parentId = token.operationId,
                origin = "TASKER",
            )
            repos.operationHistoryRepository().appendLog(operationId, "Review token: ${token.token.take(8)}…")
            repos.operationHistoryRepository().appendLog(operationId, "Reviewed SHA-256: ${token.sha256}")
            if (decision == TaskerAuthorizationDecision.DENY) {
                repos.operationHistoryRepository().fail(operationId, "Reviewed Tasker installs are disabled by MMRL policy")
                throw IllegalStateException("Reviewed Tasker installs are disabled in MMRL settings")
            }
            try {
                TaskerReviewTokenStore.claim(context, token.token, operationId)
                val status = TaskerRootDispatcher.dispatch(
                    context,
                    TaskerRootRequest(
                        operationId = operationId,
                        command = "EXECUTE_REVIEW",
                        moduleId = token.moduleId,
                        moduleName = token.moduleName,
                        reviewToken = token.token,
                    ),
                    decision,
                )
                if (status == "AWAITING_APPROVAL") {
                    repos.operationHistoryRepository().phase(
                        operationId,
                        OperationPhase.APPROVAL,
                        if (token.routine) "Waiting for MMRL approval" else "Non-routine changes require MMRL approval",
                    )
                }
                TaskerResultOutput(
                    status = status,
                    message = if (status == "AWAITING_APPROVAL") "Waiting for MMRL approval" else "Reviewed install queued",
                    operationId = operationId,
                    operationType = kind.name,
                    moduleId = token.moduleId,
                    moduleName = token.moduleName,
                    installed = local != null,
                    installedVersion = local?.version.orEmpty(),
                    availableVersion = token.targetVersion,
                    availableVersionCode = token.targetVersionCode,
                    repository = token.repository,
                    approvalRequired = status == "AWAITING_APPROVAL",
                    safetyLevel = if (token.routine) "ROUTINE" else "REVIEW_REQUIRED",
                    inspectionSummary = token.inspectionSummary,
                    rollbackAvailable = local != null,
                    resultJson = JSONObject()
                        .put("operation_id", operationId)
                        .put("status", status)
                        .put("review_token", token.token)
                        .put("approval_required", status == "AWAITING_APPROVAL")
                        .put("safety_level", if (token.routine) "ROUTINE" else "REVIEW_REQUIRED")
                        .toString(),
                )
            } catch (error: Throwable) {
                TaskerReviewTokenStore.releaseClaim(context, token.token, operationId)
                val current = repos.operationHistoryRepository().getById(operationId)
                if (current?.isRunning == true) {
                    repos.operationHistoryRepository().fail(
                        operationId,
                        error.message ?: "Unable to queue reviewed install",
                        error,
                    )
                }
                throw error
            }
        }
    }
}

private fun downloadReviewArchive(context: Context, rawUrl: String): File {
    val url = TaskerAutomationPolicy.requireSupportedDownloadUrl(rawUrl)
    val directory = TaskerReviewTokenStore.archiveDirectory(context)
    val partial = File(directory, "${UUID.randomUUID()}.part")
    val destination = File(directory, "${UUID.randomUUID()}.zip")
    var connection: HttpURLConnection? = null
    try {
        connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        connection.connect()
        require(connection.responseCode in 200..299) { "Download failed with HTTP ${connection.responseCode}" }
        var total = 0L
        connection.inputStream.buffered().use { input ->
            partial.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        total += count
                        require(total <= 512L * 1024L * 1024L) { "Review archive exceeds 512 MiB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        require(total > 0L) { "Downloaded archive is empty" }
        require(partial.renameTo(destination)) { "Unable to stage reviewed archive" }
        return destination
    } catch (error: Throwable) {
        partial.delete()
        destination.delete()
        throw error
    } finally {
        connection?.disconnect()
    }
}

private fun readModuleManifest(file: File): Map<String, String> = ZipFile(file).use { zip ->
    val entry = zip.getEntry("module.prop") ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("/module.prop") }
        ?: return@use emptyMap()
    zip.getInputStream(entry).bufferedReader().useLines { lines ->
        lines.mapNotNull { line ->
            val clean = line.trim()
            if (clean.isBlank() || clean.startsWith('#') || '=' !in clean) null
            else clean.substringBefore('=').trim() to clean.substringAfter('=').trim()
        }.toMap()
    }
}
