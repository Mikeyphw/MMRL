package com.dergoogler.mmrl

import android.app.Application
import android.content.Context
import com.dergoogler.mmrl.app.utils.NotificationUtils
import com.dergoogler.mmrl.database.activity.ActivityHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.dergoogler.mmrl.network.NetworkUtils
import com.dergoogler.mmrl.platform.PlatformManager
import com.toxicbakery.logging.Arbor
import com.toxicbakery.logging.LogCatSeedling
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    @Inject lateinit var activityHistoryRepository: ActivityHistoryRepository
    init {
        Arbor.sow(LogCatSeedling())
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        PlatformManager.setHiddenApiExemptions()

        NotificationUtils.init(this)
        NetworkUtils.setCacheDir(cacheDir)

        // App process restart: never infer completion or automatically retry an operation.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { activityHistoryRepository.recoverAtProcessStart() }
                .onFailure { android.util.Log.w("MMRLActivity", "Activity recovery unavailable") }
        }
    }

    companion object {
        private lateinit var app: App
        val context: Context get() = app
    }
}
