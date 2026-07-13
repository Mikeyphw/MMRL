package com.dergoogler.mmrl

import android.app.Application
import android.content.Context
import com.dergoogler.mmrl.app.utils.NotificationUtils
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.network.NetworkUtils
import com.dergoogler.mmrl.service.ModuleService
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
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    init {
        Arbor.sow(LogCatSeedling())
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        PlatformManager.setHiddenApiExemptions()

        NotificationUtils.init(this)
        NetworkUtils.setCacheDir(cacheDir)

        applicationScope.launch {
            val preferences = userPreferencesRepository.data.first()
            if (preferences.moduleServiceEnabled) {
                runCatching { ModuleService.start(this@App, preferences.checkModuleUpdatesInterval) }
            }
        }
    }

    companion object {
        private lateinit var app: App
        val context: Context get() = app
    }
}
