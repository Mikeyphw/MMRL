package com.dergoogler.mmrl.service

import android.content.Context
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
import com.dergoogler.mmrl.database.AppDatabase
import com.dergoogler.mmrl.database.entity.Repo.Companion.toRepo
import com.dergoogler.mmrl.repository.RepositoryEntryPoints
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class RepositoryRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        RepositoryService.markRuntimeRunning(true)
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                RepositoryEntryPoints::class.java,
            )
            val database = AppDatabase.build(applicationContext)
            val repos = database.repoDao().getAll().filter { it.enable }
            val results = coroutineScope {
                repos.map { repo ->
                    async {
                        runCatching { entryPoint.modulesRepository().getRepo(repo.url.toRepo()).isSuccess }
                            .onFailure { Timber.e(it, "Repository refresh failed for ${repo.url}") }
                            .getOrDefault(false)
                    }
                }.awaitAll()
            }
            if (results.any { !it } && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } finally {
            RepositoryService.markRuntimeRunning(false)
        }
    }

    companion object {
        const val PERIODIC_WORK = "mmrl-repository-refresh-periodic"
        const val ONE_SHOT_WORK = "mmrl-repository-refresh-now"
        private const val MAX_RETRIES = 2

        fun schedule(context: Context, intervalHours: Long) {
            val interval = ServiceCadencePolicy.clampHours(intervalHours)
            val request = PeriodicWorkRequestBuilder<RepositoryRefreshWorker>(interval, TimeUnit.HOURS)
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
            val request = OneTimeWorkRequestBuilder<RepositoryRefreshWorker>()
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

        private fun defaultConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
