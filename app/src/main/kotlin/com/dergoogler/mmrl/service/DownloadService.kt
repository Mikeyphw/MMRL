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
import com.dergoogler.mmrl.compat.MediaStoreCompat.createDownloadUri
import com.dergoogler.mmrl.compat.NetworkCompat
import com.dergoogler.mmrl.compat.PermissionCompat
import com.dergoogler.mmrl.database.entity.history.OperationAction
import com.dergoogler.mmrl.database.entity.history.OperationKind
import com.dergoogler.mmrl.database.entity.history.OperationPhase
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.ext.parcelable
import com.dergoogler.mmrl.github.GitHubTokenStore
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
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@AndroidEntryPoint
class DownloadService : LifecycleService() {
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var operationHistoryRepository: OperationHistoryRepository

    private val tasks = mutableListOf<TaskItem>()
    private val taskJobs = mutableMapOf<String, Job>()
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
                    item.operationId?.let { operationHistoryRepository.progress(it, progress) }
                }
            }.launchIn(lifecycleScope)
    }

    override fun onCreate() {
        Timber.d("onCreate")
        super.onCreate()
        setForeground()
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Timber.d("onDestroy")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val operationId = intent.getStringExtra(EXTRA_OPERATION_ID)
            if (!operationId.isNullOrBlank()) {
                cancelRequests += operationId
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
            val destination = DownloadPathPolicy.destination(
                configuredPath = downloadPath,
                filename = item.filename,
                publicDownloads = Const.PUBLIC_DOWNLOADS,
            )
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

                    when (DownloadTargetPolicy.classify(destination)) {
                        ExistingDownload.VALID -> {
                            Timber.d("File already exists: ${destination.absolutePath}")
                            operationHistoryRepository.succeed(
                                id = historyId,
                                summary = getString(R.string.file_already_exists),
                            )
                            listeners[item]?.onFileExists()
                            listeners[item]?.onSuccess(destination.toUri())
                            onDownloadSucceeded(trackedItem)
                            cleanupTask(item, trackedItem, historyId)
                            return@withPermit
                        }

                        ExistingDownload.EMPTY -> {
                            operationHistoryRepository.appendLog(
                                historyId,
                                getString(R.string.download_empty_file_recovered),
                            )
                            destination.delete()
                        }

                        ExistingDownload.MISSING -> Unit
                    }

                    val temporary = temporaryFile(historyId)
                    temporary.parentFile?.mkdirs()
                    temporary.delete()

                    val result =
                        temporary.outputStream().buffered().use { output ->
                            download(
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

                    try {
                        val publishSource = unwrapGitHubArtifactIfNeeded(item.url, temporary, historyId)
                        val publishedUri = publishTemporaryFile(publishSource, destination)
                        operationHistoryRepository.appendLog(historyId, "Published to $publishedUri")
                        if (publishSource != temporary) publishSource.delete()
                        temporary.delete()
                        listener.onSuccess(publishedUri)
                    } catch (error: Throwable) {
                        destination.takeIf { it.exists() && it.length() == 0L }?.delete()
                        temporary.delete()
                        listener.onFailure(error)
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
            listeners[original]?.onSuccess(uri)
            progressFlow.value = tracked to 0f
            lifecycleScope.launch {
                operationHistoryRepository.succeed(
                    id = historyId,
                    summary = getString(R.string.message_download_success),
                )
            }
            onDownloadSucceeded(tracked)
            cleanupTask(original, tracked, historyId)
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
    ): File {
        if (!url.contains("/actions/artifacts/", ignoreCase = true)) return archive
        val nested =
            runCatching {
                ZipFile(archive).use { zip ->
                    zip
                        .entries()
                        .asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".zip", ignoreCase = true) }
                        .maxByOrNull { nestedZipScore(it.name) }
                        ?.let { entry ->
                            val output = temporaryFile("$operationId-nested")
                            zip.getInputStream(entry).buffered().use { input ->
                                output.outputStream().buffered().use { outputStream ->
                                    input.copyTo(outputStream)
                                }
                            }
                            output.takeIf { it.isFile && it.length() > 0L }
                        }
                }
            }.getOrNull()

        if (nested != null) {
            operationHistoryRepository.appendLog(operationId, "Extracted nested module archive from GitHub artifact")
            return nested
        }
        operationHistoryRepository.appendLog(operationId, "GitHub artifact did not contain a nested ZIP; publishing artifact archive")
        return archive
    }

    private fun nestedZipScore(name: String): Int {
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
        val penalty = if (lower.contains("source") || lower.contains("debug")) -80 else 0
        return abiScore + penalty
    }

    private suspend fun download(
        url: String,
        output: OutputStream,
        onProgress: (Float) -> Unit,
    ): Result<*> {
        if (!url.startsWith("https://api.github.com/", ignoreCase = true)) {
            return NetworkCompat.download(url = url, output = output, onProgress = onProgress)
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "application/octet-stream")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .apply {
                            GitHubTokenStore(this@DownloadService).getToken()?.takeIf(String::isNotBlank)?.let {
                                header("Authorization", "Bearer $it")
                            }
                        }.build()

                NetworkUtils.createOkHttpClient().newCall(request).execute().use { response ->
                    require(response.isSuccessful) { "HTTP ${response.code} while downloading GitHub file" }
                    val body = response.body ?: error("Empty GitHub download response")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    val all = body.contentLength()
                    var finished = 0L
                    var read: Int
                    body.byteStream().buffered().use { input ->
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            finished += read
                            if (all > 0L) onProgress((finished.toDouble() / all).toFloat())
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    private fun cleanupTemporaryFile(operationId: String) {
        temporaryFile(operationId).delete()
    }

    private fun publishTemporaryFile(temporary: File, destination: File): Uri {
        val expectedBytes = temporary.length()
        require(expectedBytes > 0L) { "Cannot publish an empty download" }

        val inPublicDownloads = destination.isInside(Const.PUBLIC_DOWNLOADS)
        if (!inPublicDownloads) {
            destination.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Cannot create download directory: ${parent.absolutePath}")
                }
            }
        }

        return if (inPublicDownloads) {
            val relativePath = destination.relativeTo(Const.PUBLIC_DOWNLOADS).path
            val uri = createDownloadUri(path = relativePath, mimeType = "application/zip")
            try {
                val copiedBytes = contentResolver.openOutputStream(uri, "w")?.use { output ->
                    temporary.inputStream().buffered().use { input -> input.copyTo(output) }
                } ?: throw IOException("Cannot open download destination")
                if (copiedBytes != expectedBytes) {
                    throw IOException("Incomplete publish: $copiedBytes of $expectedBytes bytes")
                }
                uri
            } catch (error: Throwable) {
                deleteDestination(uri)
                throw error
            }
        } else {
            if (destination.exists() && destination.length() > 0L) {
                throw IOException("Download already exists: ${destination.absolutePath}")
            }
            destination.delete()
            try {
                val copiedBytes = temporary.inputStream().buffered().use { input ->
                    destination.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                if (copiedBytes != expectedBytes || destination.length() != expectedBytes) {
                    throw IOException("Incomplete publish: $copiedBytes of $expectedBytes bytes")
                }
                destination.toUri()
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }
    }

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
                .setProgress(100, (progress * 100).toInt(), false)
                .setContentText("${(progress * 100).toInt()}% · Tap to view Activity")
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
        fun onSuccess() {}
        fun onSuccess(uri: Uri) {
            onSuccess()
        }
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
        private val cancelRequests = mutableSetOf<String>()

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
