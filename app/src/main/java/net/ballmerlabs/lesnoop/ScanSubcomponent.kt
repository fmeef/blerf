package net.ballmerlabs.lesnoop

import android.content.Context
import net.ballmerlabs.lesnoop.scan.Scanner
import net.ballmerlabs.lesnoop.scan.ScannerImpl
import com.polidea.rxandroidble3.RxBleClient
import dagger.Binds
import dagger.BindsInstance
import dagger.Provides
import dagger.hilt.DefineComponent
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.util.concurrent.Executor
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
        }
    }
}