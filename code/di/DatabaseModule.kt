package kr.com.chappiet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kr.com.chappiet.data.local.AppDatabase
import kr.com.chappiet.data.local.SummaryDao
import kr.com.chappiet.data.local.DeviceProfileDao
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {
    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideSummaryDao(appDatabase: AppDatabase): SummaryDao {
        return appDatabase.summaryDao()
    }

    @Provides
    fun provideUserProfileDao(appDatabase: AppDatabase): DeviceProfileDao {
        return appDatabase.deviceProfileDao()
    }
}