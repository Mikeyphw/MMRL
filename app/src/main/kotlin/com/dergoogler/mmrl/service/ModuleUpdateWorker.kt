package com.dergoogler.mmrl.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.app.utils.NotificationUtils
import com.dergoogler.mmrl.database.AppDatabase
import com.dergoogler.mmrl.database.entity.local.LocalModuleEntity
import com.dergoogler.mmrl.database.entity.online.OnlineModuleEntity
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.online.Blacklist
import com.dergoogler.mmrl.repository.RepositoryEntryPoints
import com.dergoogler.mmrl.repository.RepositorySourceLoader
import com.dergoogler.mmrl.ui.activity.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ModuleUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ModuleService.markRuntimeRunning(true)
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                RepositoryEntryPoints::class.java,
            )
            val database = AppDatabase.build(applicationContext)
            val preferences = entryPoint.userPreferencesRepository().data.first()
            entryPoint.modulesRepository().getLocalAll()
            val localModules = entryPoint.localRepository().getLocalAll()
            val online = mutableListOf<OnlineModuleEntity>()
            var failures = 0
            database.repoDao().getAll().filter { it.enable }.forEach { repo ->
                runCatching { RepositorySourceLoader.load(repo.url).getOrThrow().modules }
                    .onSuccess { entries -> online += entries.map { OnlineModuleEntity(it, repo.url, Blacklist.EMPTY) } }
                    .onFailure { error ->
                        failures += 1
                        Timber.e(error, "Error while fetching repo: ${repo.url}")
                    }
            }

            val activeNotificationKeys = mutableSetOf<String>()
            localModules.forEach { local ->
                val newest = online
                    .filter { candidate -> candidate.id == local.id && candidate.versionCode > local.versionCode }
                    .maxByOrNull(OnlineModuleEntity::versionCode)
                    ?: return@forEach
                val key = "${ModuleIdentity.normalize(local.id)}:${newest.versionCode}"
                activeNotificationKeys += key
                if (key !in preferences.notifiedModuleUpdates) {
                    notifyUpdate(local, newest)
                }
            }
            val persistedNotificationKeys = RefreshBatchPolicy.mergeObservedKeys(
                previous = preferences.notifiedModuleUpdates,
                current = activeNotificationKeys,
                refreshComplete = failures == 0,
            )
            entryPoint.userPreferencesRepository().replaceNotifiedModuleUpdates(persistedNotificationKeys)
            if (RefreshBatchPolicy.shouldRetry(failures, runAttemptCount, MAX_RETRIES)) Result.retry() else Result.success()
        } finally {
            ModuleService.markRuntimeRunning(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyUpdate(local: LocalModuleEntity, online: OnlineModuleEntity) {
        if (!notificationsAllowed()) return
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationIdFor(local.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, NotificationUtils.CHANNEL_ID_MODULE)
            .setSmallIcon(R.drawable.device_mobile_down)
            .setContentTitle(applicationContext.getString(R.string.has_a_new_update, local.name))
            .setContentText(
                applicationContext.getString(
                    R.string.update_available_from_to,
                    local.version,
                    local.versionCode,
                    online.version,
                    online.versionCode,
                ),
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notificationIdFor(local.id), notification)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val PERIODIC_WORK = "mmrl-module-update-periodic"
        const val ONE_SHOT_WORK = "mmrl-module-update-now"
        private const val MAX_RETRIES = 2

        fun schedule(context: Context, intervalHours: Long) {
            val interval = ServiceCadencePolicy.clampHours(intervalHours)
            val request = PeriodicWorkRequestBuilder<ModuleUpdateWorker>(interval, TimeUnit.HOURS)
                .setConstraints(defaultConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(PERIODIC_WORK)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueueOnce(context: Context, reason: String = "manual") {
            val request = OneTimeWorkRequestBuilder<ModuleUpdateWorker>()
                .setInputData(Data.Builder().putString("reason", reason).build())
                .setConstraints(defaultConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(ONE_SHOT_WORK)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                ONE_SHOT_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK)
        }

        private fun notificationIdFor(moduleId: String): Int =
            ("module-update:${ModuleIdentity.normalize(moduleId)}").hashCode()

        private fun defaultConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
