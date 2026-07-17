package com.dergoogler.mmrl.ash.automation

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.app.utils.NotificationUtils
import com.dergoogler.mmrl.ui.activity.MainActivity

class AshAutomationNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private val store = AshAutomationStateStore(context)

    @SuppressLint("MissingPermission")
    fun publish(
        signals: List<AshAlertSignal>,
        managedKinds: Set<AshAlertKind> = AshAlertKind.entries.toSet(),
        notifyRecoveryCleared: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ) {
        val activeByKind = signals.associateBy(AshAlertSignal::kind)
        val incidentWasActive = store.hasActive(AshAlertKind.Incident)

        signals.forEach { signal ->
            if (signal.kind !in managedKinds || !store.shouldNotify(signal, now)) return@forEach
            if (manager.areNotificationsEnabled()) {
                manager.notify(signal.kind.notificationId, buildNotification(signal))
                store.markNotified(signal, now)
            }
        }

        managedKinds.forEach { kind ->
            if (kind !in activeByKind) {
                manager.cancel(kind.notificationId)
                store.clear(kind)
            }
        }

        if (
            AshAlertKind.Incident in managedKinds &&
            notifyRecoveryCleared &&
            incidentWasActive &&
            AshAlertKind.Incident !in activeByKind &&
            manager.areNotificationsEnabled()
        ) {
            manager.notify(RECOVERY_CLEARED_NOTIFICATION_ID, buildRecoveryClearedNotification())
        }
    }

    fun clearAll() {
        AshAlertKind.entries.forEach { manager.cancel(it.notificationId) }
        manager.cancel(RECOVERY_CLEARED_NOTIFICATION_ID)
        store.clearAllSignals()
    }

    private fun buildNotification(signal: AshAlertSignal) =
        NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID_ASH_RECOVERY)
            .setSmallIcon(R.drawable.box)
            .setContentTitle(signal.title)
            .setContentText(signal.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(signal.message))
            .setContentIntent(openRecoveryIntent(signal.kind.notificationId))
            .addAction(R.drawable.box, "Open Recovery Center", openRecoveryIntent(signal.kind.notificationId + 100))
            .addAction(R.drawable.box, "Check now", recheckIntent(signal.kind.notificationId + 200))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setCategory(if (signal.urgent) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (signal.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun buildRecoveryClearedNotification() =
        NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID_ASH_RECOVERY)
            .setSmallIcon(R.drawable.box)
            .setContentTitle("AshReXcue reports a stable state")
            .setContentText("The previously active recovery incident is no longer reported.")
            .setContentIntent(openRecoveryIntent(RECOVERY_CLEARED_NOTIFICATION_ID))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun openRecoveryIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_RECOVERY, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun recheckIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, AshAutomationReceiver::class.java)
            .setAction(AshAutomationReceiver.ACTION_CHECK_NOW)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val AshAlertKind.notificationId: Int
        get() = when (this) {
            AshAlertKind.Incident -> NotificationUtils.NOTIFICATION_ID_ASH_INCIDENT
            AshAlertKind.RebootRequired -> NotificationUtils.NOTIFICATION_ID_ASH_REBOOT
            AshAlertKind.RestorationTrial -> NotificationUtils.NOTIFICATION_ID_ASH_TRIAL
        }

    companion object {
        private const val RECOVERY_CLEARED_NOTIFICATION_ID = 5027
    }
}
