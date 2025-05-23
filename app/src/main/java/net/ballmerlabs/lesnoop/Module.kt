package net.ballmerlabs.lesnoop

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import androidx.core.content.getSystemService
import androidx.room.Room
import com.polidea.rxandroidble3.RxBleClient
import net.ballmerlabs.lesnoop.db.ScanDatabase
import net.ballmerlabs.lesnoop.db.ScanResultDao
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import net.ballmerlabs.lesnoop.db.MIGATION_2_3
import net.ballmerlabs.lesnoop.scan.Scanner
import java.io.File
import java.util.concurrent.Executor
import javax.inject.Named
import javax.inject.Singleton
@InstallIn(SingletonComponent::class)
@dagger.Module()
class Module {

    companion object {
        const val DB_SCHEDULER = "dbsched"
        const val DB_PATH = "dbpath"
        const val EXECUTOR_COMPUTE = "computeexec"
        const val GLOBAL_SCAN = "globalscan"
        const val CONNECT_SCHEDULER = "connect"
        const val TIMEOUT_SCHEDULER = "timeout"
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
    @Singleton
    @Named(CONNECT_SCHEDULER)
    fun provideConnectScheduler(): Scheduler {
        return RxJavaPlugins.createSingleScheduler { r ->
            Thread(r)
        }
    }

    @Provides
    @Singleton
    @Named(TIMEOUT_SCHEDULER)
    fun provideTimeoutScheduler(): Scheduler {
        return RxJavaPlugins.createIoScheduler { r ->
            Thread(r)
        }
    }

    @Provides
    @Singleton
    fun providesDao(database: ScanDatabase): ScanResultDao {
        return database.scanResultsDao()
    }

    @Provides
    @Singleton
    fun providesLeClient(@ApplicationContext context: Context): RxBleClient {
        return RxBleClient.create(context)
    }

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext ctx: Context): ScanDatabase {
        return Room.databaseBuilder(ctx, ScanDatabase::class.java, "ScanDatabase")
            .addMigrations(MIGATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun providesRawBluetoothManager(@ApplicationContext ctx: Context): BluetoothManager {
        return ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    @Provides
    @Singleton
    fun providesLocationManager(@ApplicationContext ctx: Context): LocationManager {
        return ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @Provides
    @Singleton
    @Named(GLOBAL_SCAN)
    fun providesGlobalScanner(@ApplicationContext context: Context, b: ScanSubcomponent.Builder): Scanner {
        return context.getScan(b)
    }

    @Provides
    @Singleton
    @Named(EXECUTOR_COMPUTE)
    fun provideScanExecutor(@Named(DB_SCHEDULER) scheduler: Scheduler): Executor {
        return Executor { t ->
            scheduler.scheduleDirect(t)
        }
    }

    @Provides
    @Singleton
    @Named(DB_PATH)
    fun providesDatabasePath(database: ScanDatabase): File {
        return File(database.openHelper.readableDatabase.path)
    }
}