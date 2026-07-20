package com.dergoogler.mmrl.ash.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dergoogler.mmrl.ash.AshReXcueManager
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class AshHealthCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val reason = inputData.getString(KEY_REASON).orEmpty().ifBlank { "periodic" }
        val store = AshAutomationStateStore(applicationContext)
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AshAutomationEntryPoint::class.java,
        )
        val preferences = runCatching { entryPoint.preferences().data.first() }
            .getOrElse { error ->
                store.recordFailure(now, error.message ?: "Unable to read automation preferences")
                return if (runAttemptCount < 2) Result.retry() else Result.success()
            }

        if (!preferences.ashHealthChecksEnabled) {
            AshAutomationNotifier(applicationContext).clearAll()
            store.recordSuccess(now, "Health checks disabled")
            return Result.success()
        }

        return runCatching {
            val state = entryPoint.manager().refresh()
            val config = AshAutomationConfig(
                incidentAlerts = preferences.ashIncidentNotifications,
                rebootReminders = preferences.ashRebootReminders,
                restorationReminders = preferences.ashRestorationReminders,
            )
            val signals = state.automationSignals(applicationContext, config)
            val managedKinds = if (state.source == AshSnapshotSource.Live) {
                AshAlertKind.entries.toSet()
            } else {
                setOf(AshAlertKind.RebootRequired)
            }
            AshAutomationNotifier(applicationContext).publish(
                signals = signals,
                managedKinds = managedKinds,
                notifyRecoveryCleared = preferences.ashIncidentNotifications,
                now = now,
            )
            store.recordSuccess(
                now,
                "$reason · " + if (signals.isEmpty()) {
                    applicationContext.getString(com.dergoogler.mmrl.R.string.ash_alert_no_recovery_action)
                } else {
                    signals.joinToString { it.kind.name }
                },
            )
            Result.success()
        }.getOrElse { error ->
            store.recordFailure(now, error.message ?: "AshReXcue health check failed")
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_REASON = "reason"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AshAutomationEntryPoint {
    fun manager(): AshReXcueManager
    fun preferences(): UserPreferencesRepository
}
