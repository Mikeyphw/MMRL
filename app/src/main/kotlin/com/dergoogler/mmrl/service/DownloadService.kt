package com.dergoogler.mmrl.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.app.Const
import com.dergoogler.mmrl.app.utils.NotificationUtils
import com.dergoogler.mmrl.compat.MediaStoreCompat.createPendingDownloadUri
import com.dergoogler.mmrl.compat.MediaStoreCompat.publishPendingDownloadUri
import com.dergoogler.mmrl.compat.PermissionCompat
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.ext.parcelable
import com.dergoogler.mmrl.github.GitHubArtifactArchivePolicy
import com.dergoogler.mmrl.installer.ArtifactDigest
import com.dergoogler.mmrl.installer.AtomicFilePublication
import com.dergoogler.mmrl.service.DownloadPublicationPolicy.PublishMode
import com.dergoogler.mmrl.github.GitHubTokenStore
import com.dergoogler.mmrl.network.NetworkPolicy
import com.dergoogler.mmrl.network.NetworkUtils
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import com.dergoogler.mmrl.ui.activity.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.dergoogler.mmrl.compat.BuildCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import okhttp3.Call
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@AndroidEntryPoint
class DownloadService : LifecycleService() {
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var operationHistoryRepository: OperationHistoryRepository
    @Inject lateinit var downloadReceiptStore: DownloadReceiptStore

    private val tasks = mutableListOf<TaskItem>()
    private val taskJobs = mutableMapOf<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val downloadSlots = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    init {
        lifecycleScope.launch {
            while (isActive) {
                delay(10_000L)
                if (tasks.isEmpty()) stopSelf()
            }
        }

        progressFlow
            .drop(1)
            .sample(500)
            .flowOn(Dispatchers.IO)
            .onEach { (item, progress) ->
                if (progress != 0f) {
                    onProgressChanged(item, progress)
                    if (progress >= 0f) item.operationId?.let { operationHistoryRepository.progress(it, progress) }
                }
            }.launchIn(lifecycleScope)
    }

    override fun onCreate() {
        Timber.d("onCreate")
        super.onCreate()
        setForeground()
    }

    override fun onDestroy() {
        activeCalls.values.forEach(Call::cancel)
        activeCalls.clear()
        taskJobs.values.forEach { it.cancel(CancellationException("Download service destroyed")) }
        taskJobs.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Timber.d("onDestroy")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val operationId = intent.getStringExtra(EXTRA_OPERATION_ID)
            if (!operationId.isNullOrBlank()) {
                cancelRequests += operationId
                activeCalls.remove(operationId)?.cancel()
                taskJobs.remove(operationId)?.cancel(CancellationException("Cancelled by user"))
                lifecycleScope.launch {
                    cleanupTemporaryFile(operationId)
                    operationHistoryRepository.cancel(operationId, "Download cancelled")
                }
                NotificationManagerCompat.from(this).cancel(operationId.hashCode())
            }
            return START_NOT_STICKY
        }

        lifecycleScope.launch {
            val item = intent?.taskItemOrNull ?: return@launch
            val userPreferences = userPreferencesRepository.data.first()
            val downloadPath = userPreferences.downloadPath
            val requestedDestination = DownloadPathPolicy.destination(
                configuredPath = downloadPath,
                filename = item.filename,
                publicDownloads = Const.PUBLIC_DOWNLOADS,
            )
            val reusableReceipt = downloadReceiptStore.verifyDestination(requestedDestination.absolutePath, item.url)
            val destination = when {
                reusableReceipt != null -> requestedDestination
                requestedDestination.exists() -> nonConflictingDestination(requestedDestination)
                else -> requestedDestination
            }
            val historyId =
                item.operationId ?: operationHistoryRepository.start(
                    kind = OperationKind.DOWNLOAD,
                    title = item.title ?: item.filename,
                    summary = item.desc.orEmpty(),
                    sourceUrl = item.url,
                    destinationPath = destination.absolutePath,
                    retryAction = OperationAction.DOWNLOAD,
                    parentId = item.parentId,
                )

            taskJobs[historyId] = coroutineContext.job
            listeners[item]?.onStarted(historyId)
            val trackedItem = item.copy(operationId = historyId)
            tasks.add(trackedItem)
            operationHistoryRepository.phase(historyId, OperationPhase.REVIEW, "Queued for download")
            operationHistoryRepository.appendLog(historyId, "Source: ${item.url}")
            operationHistoryRepository.appendLog(historyId, "Destination: ${destination.absolutePath}")
            onDownloadQueued(trackedItem, tasks.indexOf(trackedItem) + 1)

            val listener = trackedListener(item, trackedItem, historyId)

            try {
                downloadSlots.withPermit {
                    coroutineContext.ensureActive()
                    operationHistoryRepository.phase(historyId, OperationPhase.DOWNLOAD, "Downloading module archive")
                    operationHistoryRepository.appendLog(historyId, "Download slot acquired")

                    if (reusableReceipt != null) {
                        val authoritativeUri = Uri.parse(reusableReceipt.sourceUri)
                        operationHistoryRepository.appendLog(historyId, "Reused verified download receipt ${reusableReceipt.sha256}")
                        val sourceCommitted = operationHistoryRepository.sourceUri(historyId, authoritativeUri.toString())
                        val terminalCommitted = operationHistoryRepository.succeed(
                            id = historyId,
                            summary = getString(R.string.file_already_exists),
                        )
                        DownloadCompletionPolicy.requireDurableSuccess(sourceCommitted, terminalCommitted)
                        DownloadCompletionPolicy.runPostCommitBestEffort { listeners[item]?.onFileExists() }
                            ?.let { Timber.w(it, "Download reuse listener failed after durable commit") }
                        listener.onSuccess(authoritativeUri)
                        return@withPermit
                    }

                    val temporary = temporaryFile(historyId)
                    temporary.parentFile?.mkdirs()
                    temporary.delete()

                    val result =
                        temporary.outputStream().buffered().use { output ->
                            download(
                                operationId = historyId,
                                url = item.url,
                                output = output,
                                onProgress = listener::getProgress,
                            )
                        }

                    result.onFailure {
                        temporary.delete()
                        listener.onFailure(it)
                        return@withPermit
                    }

                    coroutineContext.ensureActive()
                    if (!temporary.isFile || temporary.length() <= 0L) {
                        temporary.delete()
                        listener.onFailure(IOException("Server returned an empty download"))
                        return@withPermit
                    }

                    operationHistoryRepository.phase(historyId, OperationPhase.VERIFY, "Publishing completed download")
                    operationHistoryRepository.appendLog(historyId, "Downloaded ${temporary.length()} bytes")

                    var publishSource: File? = null
                    var publishedUri: Uri? = null
                    try {
                        publishSource = unwrapGitHubArtifactIfNeeded(item.url, temporary, historyId)
                        val published = publishTemporaryFile(publishSource, destination)
                        publishedUri = published.uri
                        downloadReceiptStore.record(
                            uri = published.uri,
                            sourceUrl = item.url,
                            sha256 = published.sha256,
                            size = published.size,
                            destinationPath = destination.absolutePath,
                        )
                        operationHistoryRepository.appendLog(historyId, "Published verified artifact ${published.sha256} to ${published.uri}")
                        val sourceCommitted = operationHistoryRepository.sourceUri(historyId, published.uri.toString())
                        val terminalCommitted = operationHistoryRepository.succeed(
                            id = historyId,
                            summary = getString(R.string.message_download_success),
                        )
                        DownloadCompletionPolicy.requireDurableSuccess(sourceCommitted, terminalCommitted)
                        temporary.delete()
                        publishedUri = null // publication and terminal history are durable before success is externally observable
                        listener.onSuccess(published.uri)
                    } catch (error: Throwable) {
                        publishedUri?.let(::deleteDestination)
                        destination.takeIf { it.exists() && it.length() == 0L }?.delete()
                        temporary.delete()
                        listener.onFailure(error)
                    } finally {
                        publishSource?.takeIf { it != temporary }?.delete()
                    }
                }
            } catch (cancelled: CancellationException) {
                cleanupTemporaryFile(historyId)
                destination.takeIf { it.exists() && it.length() == 0L }?.delete()
                listener.onFailure(cancelled)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun trackedListener(
        original: TaskItem,
        tracked: TaskItem,
        historyId: String,
    ) = object : IDownloadListener {
        override fun getProgress(value: Float) {
            listeners[original]?.getProgress(value)
            progressFlow.value = tracked to value
        }

        override fun onSuccess(uri: Uri) {
            DownloadCompletionPolicy.runPostCommitBestEffort { listeners[original]?.onSuccess(uri) }
                ?.let { Timber.w(it, "Download success listener failed after durable commit") }
            progressFlow.value = tracked to 0f
            DownloadCompletionPolicy.runPostCommitBestEffort { onDownloadSucceeded(tracked) }
                ?.let { Timber.w(it, "Download success notification failed after durable commit") }
            DownloadCompletionPolicy.runPostCommitBestEffort { cleanupTask(original, tracked, historyId) }
                ?.let { Timber.w(it, "Download task cleanup failed after durable commit") }
        }

        override fun onFailure(e: Throwable) {
            listeners[original]?.onFailure(e)
            progressFlow.value = tracked to 0f
            val cancelled = historyId in cancelRequests || e is CancellationException
            lifecycleScope.launch {
                cleanupTemporaryFile(historyId)
                if (cancelled) {
                    operationHistoryRepository.cancel(historyId, "Download cancelled")
                } else {
                    Timber.e(e)
                    operationHistoryRepository.fail(
                        id = historyId,
                        summary = e.message ?: getString(R.string.unknown_error),
                        error = e,
                        retryable = DownloadRetryPolicy.isRetryable(e),
                    )
                }
            }
            if (!cancelled) onDownloadFailed(tracked, e.message)
            cleanupTask(original, tracked, historyId)
        }
    }

    private fun temporaryFile(operationId: String): File =
        cacheDir.resolve("downloads/${operationId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.part")

    private suspend fun unwrapGitHubArtifactIfNeeded(
        url: String,
        archive: File,
        operationId: String,
    ): File = withContext(Dispatchers.IO) {
        if (!GitHubArtifactArchivePolicy.isActionsArtifactArchive(url)) return@withContext archive

        val materialized = GitHubArtifactArchivePolicy.materializeModuleZip(
            archive = archive,
            targetDirectory = cacheDir.resolve("downloads"),
            outputNamePrefix = operationId,
        ) { name -> githubArtifactScore(name) }
        operationHistoryRepository.appendLog(operationId, "GitHub artifact selected ${materialized.analysis.summary}")
        materialized.file
    }

    private fun githubArtifactScore(name: String): Int {
        val lower = name.lowercase()
        val abiScore =
            android.os.Build.SUPPORTED_ABIS
                .flatMap { abi ->
                    when (abi.lowercase()) {
                        "arm64-v8a" -> listOf("arm64-v8a", "aarch64", "arm64")
                        "armeabi-v7a" -> listOf("armeabi-v7a", "armv7", "arm")
                        "x86_64" -> listOf("x86_64", "amd64")
                        else -> listOf(abi.lowercase())
                    }
                }.distinct()
                .mapIndexedNotNull { index, alias -> if (lower.contains(alias)) 400 - index else null }
                .maxOrNull() ?: 0
        val zipScore = if (lower.endsWith(".zip")) 40 else 0
        val releaseScore = if (lower.contains("release")) 80 else 0
        val penalty = if (lower.contains("source") || lower.contains("symbols") || lower.contains("mapping") || lower.contains("debug")) -80 else 0
        return abiScore + zipScore + releaseScore + penalty
    }

    private suspend fun download(
        operationId: String,
        url: String,
        output: OutputStream,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val usesGitHubApi = NetworkPolicy.shouldAttachGitHubToken(url) ||
                GitHubArtifactArchivePolicy.isActionsArtifactArchive(url)
            val token = if (usesGitHubApi) {
                GitHubTokenStore(this@DownloadService).getToken()?.trim()?.takeIf(String::isNotBlank)
            } else null
            val request = Request.Builder().url(url).apply {
                if (usesGitHubApi) {
                    header("Accept", githubDownloadAccept(url))
                    header("X-GitHub-Api-Version", "2022-11-28")
                    token?.let { header("Authorization", "Bearer $it") }
                }
            }.build()
            val call = NetworkUtils.createOkHttpClient().newCall(request)
            activeCalls[operationId] = call
            if (operationId in cancelRequests) {
                call.cancel()
                throw CancellationException("Cancelled before HTTP execution")
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = NetworkPolicy.readErrorSnippet(response.body)
                        if (usesGitHubApi) {
                            throw DownloadHttpException(
                                response.code,
                                GitHubArtifactArchivePolicy.downloadFailureMessage(url, response.code, token != null, detail),
                            )
                        }
                        throw DownloadHttpException(
                            response.code,
                            "Download failed with HTTP ${response.code}${detail.take(256).takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}",
                        )
                    }
                    val body = response.body ?: error("Empty download response")
                    val total = body.contentLength()
                    require(DownloadTransferPolicy.declaredLengthAllowed(total)) {
                        "Download exceeds the ${DownloadTransferPolicy.MAX_DOWNLOAD_BYTES} byte safety limit"
                    }
                    if (total <= 0L) onProgress(-1f)
                    var finished = 0L
                    body.byteStream().buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            finished = DownloadTransferPolicy.addReceived(finished, read)
                            output.write(buffer, 0, read)
                            if (total > 0L) onProgress((finished.toDouble() / total).toFloat().coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            } finally {
                activeCalls.remove(operationId, call)
            }
        }
    }

    private fun publishAtomicFile(
        temporary: File,
        destination: File,
        expected: ArtifactDigest.Digest,
    ): Uri {
        destination.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create download directory: ${parent.absolutePath}")
        }
        val partial = destination.parentFile!!.resolve(".${destination.name}.${UUID.randomUUID()}.part")
        try {
            temporary.inputStream().buffered().use { input ->
                java.io.FileOutputStream(partial).use { raw ->
                    val output = raw.buffered()
                    input.copyTo(output)
                    output.flush()
                    raw.fd.sync()
                }
            }
            require(ArtifactDigest.of(partial) == expected) { "Download staging digest mismatch" }
AtomicFilePublication.move(partial, destination)
            return destination.toUri()
        } catch (error: Throwable) {
            partial.delete()
            destination.takeIf { it.length() == 0L }?.delete()
            throw error
        }
    }

    private fun githubDownloadAccept(url: String): String =
        if (GitHubArtifactArchivePolicy.isActionsArtifactArchive(url)) {
            "application/vnd.github+json"
        } else {
            "application/octet-stream"
        }

    private fun cleanupTemporaryFile(operationId: String) {
        temporaryFile(operationId).delete()
    }

    private suspend fun publishTemporaryFile(temporary: File, destination: File): PublishedArtifact =
        withContext(Dispatchers.IO) {
        val expected = ArtifactDigest.of(temporary)
        require(expected.size > 0L) { "Cannot publish an empty download" }
        val inPublicDownloads = destination.isInside(Const.PUBLIC_DOWNLOADS)
        if (!inPublicDownloads) {
            destination.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create download directory: ${parent.absolutePath}")
            }
        }

        val publishMode = DownloadPublicationPolicy.forDestination(
            sdkInt = Build.VERSION.SDK_INT,
            inPublicDownloads = inPublicDownloads,
        )
        val uri = when (publishMode) {
            PublishMode.MEDIASTORE_PENDING -> {
                val relativePath = destination.relativeTo(Const.PUBLIC_DOWNLOADS).path
                val pending = createPendingDownloadUri(path = relativePath, mimeType = "application/zip")
                try {
                    contentResolver.openOutputStream(pending, "w")?.use { output ->
                        temporary.inputStream().buffered().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Cannot open download destination")
                    val actual = contentResolver.openInputStream(pending)?.buffered()?.use(ArtifactDigest::of)
                        ?: throw IOException("Cannot verify published download")
                    require(actual == expected) { "Published download digest mismatch" }
                    publishPendingDownloadUri(pending)
                    pending
                } catch (error: Throwable) {
                    deleteDestination(pending)
                    throw error
                }
            }

            PublishMode.ATOMIC_FILE -> publishAtomicFile(temporary, destination, expected)
        }
        try {
            val finalDigest = if (uri.scheme == "file") {
                ArtifactDigest.of(uri.toFile())
            } else {
                contentResolver.openInputStream(uri)?.buffered()?.use(ArtifactDigest::of)
                    ?: throw IOException("Cannot re-open published download")
            }
            require(finalDigest == expected) { "Published download changed before receipt creation" }
            PublishedArtifact(uri, finalDigest.sha256, finalDigest.size)
        } catch (error: Throwable) {
            deleteDestination(uri)
            throw error
        }
    }

    private fun nonConflictingDestination(destination: File): File {
        val parent = destination.parentFile ?: return destination
        val name = destination.name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = parent.resolve("$stem ($index)$ext")
            if (!candidate.exists()) return candidate
            index += 1
        }
    }

    private data class PublishedArtifact(val uri: Uri, val sha256: String, val size: Long)

    private fun File.isInside(parent: File): Boolean = runCatching {
        val parentPath = parent.canonicalFile.toPath()
        canonicalFile.toPath().startsWith(parentPath)
    }.getOrDefault(false)

    private fun deleteDestination(uri: Uri) {
        runCatching {
            if (uri.scheme == "file") uri.toFile().delete() else contentResolver.delete(uri, null, null)
        }.onFailure { Timber.w(it, "Unable to remove failed download destination") }
    }

    private fun cleanupTask(original: TaskItem, tracked: TaskItem, operationId: String) {
        listeners.remove(original)
        tasks.remove(tracked)
        taskJobs.remove(operationId)
        activeCalls.remove(operationId)?.cancel()
        cancelRequests.remove(operationId)
    }

    private fun onDownloadQueued(item: TaskItem, position: Int) {
        val notification =
            baseNotificationBuilder()
                .setContentTitle(item.title)
                .setSubText(item.desc)
                .setContentText(getString(R.string.download_queued, position))
                .setSilent(true)
                .setOngoing(true)
                .setGroup(GROUP_KEY)
                .apply { item.operationId?.let { addAction(0, getString(R.string.cancel), cancelPendingIntent(it)) } }
                .build()
        notify(item.notificationId, notification)
    }

    private fun onProgressChanged(item: TaskItem, progress: Float) {
        val notification =
            baseNotificationBuilder()
                .setContentTitle(item.title)
                .setSubText(item.desc)
                .setSilent(true)
                .setOngoing(true)
                .setGroup(GROUP_KEY)
                .apply {
                    if (progress < 0f) {
                        setProgress(0, 0, true)
                        setContentText("Downloading · Tap to view Activity")
                    } else {
                        setProgress(100, (progress * 100).toInt(), false)
                        setContentText("${(progress * 100).toInt()}% · Tap to view Activity")
                    }
                }
                .apply { item.operationId?.let { addAction(0, getString(R.string.cancel), cancelPendingIntent(it)) } }
                .build()
        notify(item.notificationId, notification)
    }

    private fun onDownloadSucceeded(item: TaskItem) {
        val notification =
            baseNotificationBuilder()
                .setContentTitle(item.title)
                .setSubText(item.desc)
                .setContentText(getString(R.string.message_download_success))
                .setSilent(true)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        notify(item.notificationId, notification)
    }

    private fun onDownloadFailed(item: TaskItem, message: String?) {
        val notification =
            baseNotificationBuilder()
                .setContentTitle(item.title)
                .setSubText(item.desc)
                .setContentText(message ?: getString(R.string.unknown_error))
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        notify(item.notificationId, notification)
    }

    private fun setForeground() {
        val notification =
            baseNotificationBuilder()
                .setContentTitle(getString(R.string.notification_name_download))
                .setSilent(true)
                .setOngoing(true)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .build()
        startForeground(NotificationUtils.NOTIFICATION_ID_DOWNLOAD, notification)
    }

    private fun baseNotificationBuilder() =
        NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID_DOWNLOAD)
            .setSmallIcon(R.drawable.launcher_outline)
            .setContentIntent(activityPendingIntent())
            .setAutoCancel(false)

    private fun activityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_ACTIVITY, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            711,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelPendingIntent(operationId: String): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_OPERATION_ID, operationId)
        }
        return PendingIntent.getService(
            this,
            operationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: Notification) {
        val granted =
            if (BuildCompat.atLeastT) {
                PermissionCompat.checkPermissions(this, listOf(Manifest.permission.POST_NOTIFICATIONS)).allGranted
            } else {
                true
            }
        if (granted) NotificationManagerCompat.from(this).notify(id, notification)
    }

    @Parcelize
    data class TaskItem(
        val key: Int,
        val url: String,
        val filename: String,
        val title: String?,
        val desc: String?,
        val operationId: String? = null,
        val parentId: String? = null,
        val requestId: String = UUID.randomUUID().toString(),
    ) : Parcelable {
        val notificationId: Int get() = operationId?.hashCode() ?: key

        companion object {
            fun empty() = TaskItem(-1, "", "", null, null)
        }
    }

    interface IDownloadListener {
        fun onStarted(operationId: String) {}
        fun getProgress(value: Float) {}
        fun onFileExists() {}
        fun onSuccess(uri: Uri) {}
        fun onFailure(e: Throwable) {}
    }

    companion object {
        private const val GROUP_KEY = "DOWNLOAD_SERVICE_GROUP_KEY"
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private const val EXTRA_TASK = "com.mikeyphw.mmrl.extra.TASK"
        private const val EXTRA_OPERATION_ID = "com.mikeyphw.mmrl.extra.OPERATION_ID"
        private const val ACTION_CANCEL = "com.mikeyphw.mmrl.action.CANCEL_DOWNLOAD"
        private val Intent.taskItemOrNull: TaskItem? get() = parcelable(EXTRA_TASK)

        private val listeners = ConcurrentHashMap<TaskItem, IDownloadListener>()
        private val progressFlow = MutableStateFlow(TaskItem.empty() to 0f)
        private val cancelRequests = ConcurrentHashMap.newKeySet<String>()

        fun getProgressByKey(key: Int): Flow<Float> =
            progressFlow.filter { (item, _) -> item.key == key }.map { (_, progress) -> progress }

        fun cancel(context: Context, operationId: String): Boolean =
            runCatching {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra(EXTRA_OPERATION_ID, operationId)
                }
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrDefault(false)

        fun startFromAutomation(context: Context, task: TaskItem): Boolean {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT <= 29) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (!PermissionCompat.checkPermissions(context, permissions).allGranted) return false

            listeners[task] = object : IDownloadListener {}
            return runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadService::class.java).putExtra(EXTRA_TASK, task),
                )
                true
            }.getOrElse {
                listeners.remove(task)
                false
            }
        }

        fun start(context: Context, task: TaskItem, listener: IDownloadListener) {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT <= 29) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

            PermissionCompat.requestPermissions(context, permissions) { state ->
                if (state.allGranted) {
                    listeners[task] = listener
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, DownloadService::class.java).putExtra(EXTRA_TASK, task),
                    )
                } else {
                    listener.onFailure(SecurityException("Download permission denied"))
                }
            }
        }
    }
}
