package com.dergoogler.mmrl.ash.di

import android.content.Context
import androidx.room.Room
import com.dergoogler.mmrl.ash.database.ActivityDao
import com.dergoogler.mmrl.ash.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ashrexcue.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideActivityDao(database: AppDatabase): ActivityDao = database.activityDao()
}
