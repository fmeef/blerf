package com.invalid.lesnoop

import android.content.Context
import android.location.LocationManager
import android.location.LocationProvider
import android.location.provider.ProviderProperties
import androidx.room.Database
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.invalid.lesnoop.db.ScanDatabase
import com.invalid.lesnoop.db.ScanResultDao
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.File
import java.nio.file.Path
import javax.inject.Named
import javax.inject.Singleton
@InstallIn(SingletonComponent::class)
@dagger.Module()
class Module {

    companion object {
        const val DB_SCHEDULER = "dbsched"
        const val DB_PATH = "dbpath"
    }

    @Provides
    @Singleton
    @Named(DB_SCHEDULER)
    fun provideDbSceduler(): Scheduler {
        return RxJavaPlugins.createIoScheduler { r ->
            Thread(r)
        }
    }

    @Provides
    fun providesDao(database: ScanDatabase): ScanResultDao {
        return database.scanResultsDao()
    }

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext ctx: Context): ScanDatabase {
        return Room.databaseBuilder(ctx, ScanDatabase::class.java, "ScanDatabase").build()
    }

    @Provides
    @Singleton
    fun providesLocationManager(@ApplicationContext ctx: Context): LocationManager {
        return ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @Provides
    @Singleton
    @Named(DB_PATH)
    fun providesDatabasePath(database: ScanDatabase): File {
        return File(database.openHelper.readableDatabase.path)
    }
}