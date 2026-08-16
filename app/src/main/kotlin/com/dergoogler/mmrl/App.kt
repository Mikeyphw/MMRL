package com.dergoogler.mmrl

import android.app.Application
import android.content.Context
import com.dergoogler.mmrl.app.AppCrashHandler
import com.dergoogler.mmrl.app.AppStartupCoordinator
import com.dergoogler.mmrl.app.utils.NotificationUtils
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.network.NetworkUtils
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import com.dergoogler.mmrl.platform.PlatformManager
import com.toxicbakery.logging.Arbor
import com.toxicbakery.logging.LogCatSeedling
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var operationHistoryRepository: OperationHistoryRepository
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    init {
        Arbor.sow(LogCatSeedling())
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        if (!PlatformManager.setHiddenApiExemptions()) {
            android.util.Log.e("MMRL", "Required narrow hidden-API exemptions could not be installed")
        }

        AppCrashHandler.install(this)
        NetworkUtils.setCacheDir(cacheDir)
        applicationScope.launch {
            val preferences = userPreferencesRepository.data.first()
            NetworkUtils.setEnableDoh(preferences.useDoh)
            NotificationUtils.init(this@App)
            AppStartupCoordinator.restore(
                context = this@App,
                preferences = preferences,
                operationHistoryRepository = operationHistoryRepository,
            )
        }
    }

    companion object {
        private lateinit var app: App
        val context: Context get() = app
    }
}
