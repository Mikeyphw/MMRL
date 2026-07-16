package com.dergoogler.mmrl.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.app.utils.NotificationUtils
import com.dergoogler.mmrl.database.entity.local.LocalModuleEntity
import com.dergoogler.mmrl.database.entity.online.OnlineModuleEntity
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.online.Blacklist
import com.dergoogler.mmrl.repository.RepositorySourceLoader
import com.dergoogler.mmrl.tasker.TaskerEventPublisher
import com.dergoogler.mmrl.ui.activity.MainActivity
import dev.dergoogler.mmrl.compat.worker.MMRLLifecycleService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ModuleService : MMRLLifecycleService() {
    override val title = R.string.notification_name_module
    override val notificationId = NotificationUtils.NOTIFICATION_ID_MODULE
    override val channelId = NotificationUtils.CHANNEL_ID_MODULE
    override val groupKey = "MODULE_SERVICE_GROUP_KEY"

    private var updateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isActive = true
    }

    override fun onDestroy() {
        updateJob?.cancel()
        super.onDestroy()
        isActive = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent == null) return START_NOT_STICKY

        setForeground()
        val intervalHours = intent.getLongExtra(INTERVAL_KEY, DEFAULT_INTERVAL_HOURS).coerceAtLeast(1L)
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {
            while (isActive) {
                runCatching { checkForUpdatesAndNotify() }
                    .onFailure { Timber.e(it, "Update check failed") }
                delay(TimeUnit.HOURS.toMillis(intervalHours))
            }
        }
        return START_STICKY
    }

    private suspend fun checkForUpdatesAndNotify() = withContext(Dispatchers.IO) {
        val preferences = userPreferencesRepository.data.first()
        val previouslyNotified = preferences.notifiedModuleUpdates
        val fetchResult = fetchOnlineModules()
        val cachedModules = database.onlineDao().getAll()
        val onlineModules =
            fetchResult.modules + cachedModules.filter { it.repoUrl in fetchResult.failedRepositories }
        val localModules = database.localDao().getAll()
        val newestById =
            onlineModules
                .groupBy { ModuleIdentity.normalize(it.id) }
                .mapValues { (_, modules) -> modules.maxByOrNull { it.versionCode } }

        val updateTracking = database.localDao().getUpdatableTagAll()
            .groupBy { ModuleIdentity.normalize(it.id) }
            .mapValues { (normalizedId, matches) ->
                (matches.firstOrNull { it.id == normalizedId } ?: matches.last()).updatable
            }
        val activeNotificationKeys = mutableSetOf<String>()
        localModules.forEach { local ->
            val normalizedId = ModuleIdentity.normalize(local.id)
            val updateTrackingEnabled = updateTracking[normalizedId] != false
            if (!updateTrackingEnabled) {
                cancelUpdateNotification(local.id)
                return@forEach
            }

            val newest = newestById[normalizedId] ?: run {
                cancelUpdateNotification(local.id)
                return@forEach
            }
            if (!isNewerVersion(newest, local)) {
                cancelUpdateNotification(local.id)
                return@forEach
            }

            val notificationKey = "$normalizedId:${newest.versionCode}"
            if (notificationKey in previouslyNotified) {
                activeNotificationKeys += notificationKey
            } else {
                TaskerEventPublisher.updateDiscovered(applicationContext, local, newest)
                sendUpdateNotification(local, newest)
                activeNotificationKeys += notificationKey
            }
        }

        (previouslyNotified - activeNotificationKeys).forEach { staleKey ->
            cancelUpdateNotification(staleKey.substringBeforeLast(':'))
        }
        userPreferencesRepository.replaceNotifiedModuleUpdates(activeNotificationKeys)
    }

    private suspend fun fetchOnlineModules(): ModuleFetchResult = withContext(Dispatchers.IO) {
        val modules = mutableListOf<OnlineModuleEntity>()
        val failedRepositories = mutableSetOf<String>()
        database.repoDao().getAll().filter { it.enable }.forEach { repo ->
            val result = runCatching {
                RepositorySourceLoader.load(repo.url).getOrThrow().modules
            }
            result.onSuccess { entries ->
                modules += entries.map { OnlineModuleEntity(it, repo.url, Blacklist.EMPTY) }
            }.onFailure { error ->
                failedRepositories += repo.url
                Timber.e(error, "Error while fetching repo: ${repo.url}")
            }
        }
        ModuleFetchResult(modules = modules, failedRepositories = failedRepositories)
    }

    private fun isNewerVersion(online: OnlineModuleEntity, local: LocalModuleEntity): Boolean =
        online.versionCode > local.versionCode

    private fun sendUpdateNotification(local: LocalModuleEntity, online: OnlineModuleEntity): Boolean {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            local.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return pushNotification(
            id = notificationIdFor(local.id),
            icon = R.drawable.device_mobile_down,
            title = applicationContext.getString(R.string.has_a_new_update, local.name),
            message = applicationContext.getString(
                R.string.update_available_from_to,
                local.version,
                local.versionCode,
                online.version,
                online.versionCode,
            ),
            pendingIntent = pendingIntent,
        )
    }

    private fun cancelUpdateNotification(moduleId: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(notificationIdFor(moduleId))
    }

    private data class ModuleFetchResult(
        val modules: List<OnlineModuleEntity>,
        val failedRepositories: Set<String>,
    )

    companion object {
        var isActive by mutableStateOf(false)
            private set
        private const val INTERVAL_KEY = "INTERVAL"
        private const val DEFAULT_INTERVAL_HOURS = 6L

        private fun notificationIdFor(moduleId: String): Int =
            ("module-update:${ModuleIdentity.normalize(moduleId)}").hashCode()

        fun start(context: Context, interval: Long) {
            val intent = Intent().apply {
                component = ComponentName(context.packageName, ModuleService::class.java.name)
                putExtra(INTERVAL_KEY, interval.coerceAtLeast(1L))
            }
            context.startForegroundService(intent)
        }

        fun restart(context: Context, interval: Long) = start(context, interval)

        fun checkNow(context: Context, interval: Long = DEFAULT_INTERVAL_HOURS) =
            start(context, interval)

        fun cancelUpdateNotification(context: Context, moduleId: String) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(notificationIdFor(moduleId))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ModuleService::class.java))
        }
    }
}
