package com.dergoogler.mmrl.ash.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AshAutomationScheduler {
    private const val PERIODIC_WORK = "ashrexcue-periodic-health-check"
    private const val IMMEDIATE_WORK = "ashrexcue-immediate-health-check"
    private const val MINIMUM_INTERVAL_HOURS = 1L

    fun synchronize(context: Context, enabled: Boolean, intervalHours: Long) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            workManager.cancelUniqueWork(IMMEDIATE_WORK)
            AshAutomationNotifier(context).clearAll()
            return
        }

        val request = PeriodicWorkRequestBuilder<AshHealthCheckWorker>(
            intervalHours.coerceAtLeast(MINIMUM_INTERVAL_HOURS),
            TimeUnit.HOURS,
        )
            .setConstraints(defaultConstraints())
            .addTag(PERIODIC_WORK)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )

        if (AshAutomationStateStore(context).lastCheckAt == 0L) {
            enqueueImmediate(context, reason = "first_schedule")
        }
    }

    fun enqueueImmediate(
        context: Context,
        reason: String,
        delayMinutes: Long = 0L,
    ) {
        val request = OneTimeWorkRequestBuilder<AshHealthCheckWorker>()
            .setConstraints(defaultConstraints())
            .setInputData(Data.Builder().putString(AshHealthCheckWorker.KEY_REASON, reason).build())
            .setInitialDelay(delayMinutes.coerceAtLeast(0L), TimeUnit.MINUTES)
            .addTag(IMMEDIATE_WORK)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun defaultConstraints(): Constraints = Constraints.Builder().build()
}
