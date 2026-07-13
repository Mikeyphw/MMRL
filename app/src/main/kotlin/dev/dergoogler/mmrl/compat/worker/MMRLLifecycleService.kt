package dev.dergoogler.mmrl.compat.worker

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.database.AppDatabase
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.ext.isAppForeground
import com.dergoogler.mmrl.repository.ModulesRepository
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
open class MMRLLifecycleService : LifecycleService() {
    @Inject
    lateinit var modulesRepository: ModulesRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    val database get() = AppDatabase.build(applicationContext)

    @StringRes
    open val title: Int = 0
    open val channelId: String = ""
    open val notificationId: Int = -1
    open val groupKey: String = ""

    override fun onCreate() {
        Timber.d("onCreate")
        super.onCreate()
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Timber.d("onDestroy")
        super.onDestroy()
    }

    private val notificationManager get() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun baseNotificationBuilder() =
        NotificationCompat
            .Builder(this, channelId)
            .setSmallIcon(R.drawable.launcher_outline)

    protected fun setForeground() {
        val notification =
            baseNotificationBuilder()
                .setContentTitle(baseContext.getString(title))
                .setSilent(true)
                .setOngoing(true)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .build()

        startForeground(notificationId, notification)
    }

    fun pushNotification(
        id: Int = Random.nextInt(0, 100),
        title: String,
        message: String,
        pendingIntent: PendingIntent? = null,
        @DrawableRes icon: Int = R.drawable.box,
    ): Boolean {
        if (baseContext.isAppForeground) return false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val notification =
            baseNotificationBuilder()
                .apply {
                    setContentTitle(title)
                    setContentText(message)
                    setSmallIcon(icon)
                    setContentIntent(pendingIntent)
                    setGroup(groupKey)
                    setAutoCancel(true)
                    priority = NotificationCompat.PRIORITY_HIGH
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                }.build()

        notificationManager.notify(id, notification)
        return true
    }
}
