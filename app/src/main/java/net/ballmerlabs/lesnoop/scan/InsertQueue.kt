package net.ballmerlabs.lesnoop.scan

import com.polidea.rxandroidble3.Timeout
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Scheduler
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.ScannerFactory
import timber.log.Timber

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class InsertQueue @Inject constructor(
    scanner: ScannerFactory,
    @Named(Module.DB_SCHEDULER) dbScheduler: Scheduler
) : AbstractQueue<Completable>(scanner, dbScheduler) {
    override fun process(item: Completable): Completable {
        return item.doOnComplete { Timber.v( "insert without connect!") }
    }
}