package com.invalid.lesnoop

import android.content.Context
import com.invalid.lesnoop.scan.Scanner
import com.invalid.lesnoop.scan.ScannerImpl
import com.polidea.rxandroidble3.RxBleClient
import dagger.Binds
import dagger.BindsInstance
import dagger.Provides
import dagger.Subcomponent
import dagger.Subcomponent.Builder
import dagger.hilt.DefineComponent
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Named

fun Context.getScan(scanBuilder: ScanSubcomponent.Builder): Scanner {
    return EntryPoints.get(
        scanBuilder.context(applicationContext).build()!!,
        ScanEntryPoint::class.java
    ).scanner()
}

@DefineComponent(parent = SingletonComponent::class)
@ScanScope
interface ScanSubcomponent {

    companion object {
        const val SCHEDULER_SCAN = "scansched"
        const val SCHEDULER_COMPUTE = "computesched"
        const val EXECUTOR_COMPUTE = "computeexec"
    }

    @DefineComponent.Builder
    interface Builder {
        @BindsInstance
        fun context(context: Context): Builder
        fun build(): ScanSubcomponent?
    }

    @InstallIn(ScanSubcomponent::class)
    @dagger.Module
    abstract class ScanModule {

        @Binds
        @ScanScope
        abstract fun bindsScanner(scannerImpl: ScannerImpl): Scanner

        companion object {

            @Provides
            @ScanScope
            @Named(SCHEDULER_SCAN)
            fun provideScheduler(): Scheduler {
                return RxJavaPlugins.createSingleScheduler { r ->
                    Thread(r)
                }
            }

            @Provides
            @ScanScope
            @Named(SCHEDULER_COMPUTE)
            fun providesComputeScheduler(): Scheduler {
                return RxJavaPlugins.createComputationScheduler { r ->
                    Thread(r)
                }
            }

            @Provides
            @ScanScope
            fun providesLeClient(context: Context): RxBleClient {
                return RxBleClient.create(context)
            }

            @Provides
            @ScanScope
            @Named(EXECUTOR_COMPUTE)
            fun provideScanExecutor(@Named(SCHEDULER_COMPUTE) scheduler: Scheduler): Executor {
                return Executor { t ->
                    scheduler.scheduleDirect(t)
                }
            }
        }
    }
}