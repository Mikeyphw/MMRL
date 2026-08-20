package com.dergoogler.mmrl.datastore.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.dergoogler.mmrl.datastore.UserPreferencesSerializer
import com.dergoogler.mmrl.datastore.model.UserPreferences
import com.dergoogler.mmrl.ui.theme.Colors
import com.dergoogler.mmrl.ui.theme.ThemeColorSource
import com.dergoogler.mmrl.ui.theme.ThemeRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DataStoreProvider(
    private val fileName: String = "preferences.pb",
) {
    @Provides
    @Singleton
    fun providesUserPreferencesDataStore(
        @ApplicationContext context: Context,
        userPreferencesSerializer: UserPreferencesSerializer,
    ): DataStore<UserPreferences> =
        DataStoreFactory.create(
            serializer = userPreferencesSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserPreferences() },
            migrations = listOf(ThemePreferenceIdMigration, ServicePreferenceMigration),
        ) {
            context.dataStoreFile(fileName)
        }
}


private object ThemePreferenceIdMigration : DataMigration<UserPreferences> {
    override suspend fun shouldMigrate(currentData: UserPreferences): Boolean =
        currentData.themePaletteId.isBlank()

    override suspend fun migrate(currentData: UserPreferences): UserPreferences =
        currentData.copy(
            themePaletteId = ThemeRegistry.migrateLegacyId(currentData.themeColor),
            themeColorSource = if (currentData.themeColor == Colors.DYNAMIC_ID) {
                ThemeColorSource.DYNAMIC_FULL
            } else {
                ThemeColorSource.BUILT_IN
            },
        )

    override suspend fun cleanUp() = Unit
}


private object ServicePreferenceMigration : DataMigration<UserPreferences> {
    override suspend fun shouldMigrate(currentData: UserPreferences): Boolean =
        currentData.autoUpdateReposInterval != clampInterval(currentData.autoUpdateReposInterval) ||
            currentData.checkModuleUpdatesInterval != clampInterval(currentData.checkModuleUpdatesInterval) ||
            currentData.repositoryServiceEnabled ||
            currentData.moduleServiceEnabled ||
            currentData.providerServiceEnabled

    override suspend fun migrate(currentData: UserPreferences): UserPreferences =
        currentData.copy(
            autoUpdateReposInterval = clampInterval(currentData.autoUpdateReposInterval),
            checkModuleUpdatesInterval = clampInterval(currentData.checkModuleUpdatesInterval),
            repositoryServiceEnabled = false,
            moduleServiceEnabled = false,
            providerServiceEnabled = false,
        )

    override suspend fun cleanUp() = Unit

    private fun clampInterval(value: Long): Long =
        value.coerceIn(MIN_SERVICE_INTERVAL_HOURS, MAX_SERVICE_INTERVAL_HOURS)
}

private const val MIN_SERVICE_INTERVAL_HOURS = 1L
private const val MAX_SERVICE_INTERVAL_HOURS = 24L * 14L
