package net.ballmerlabs.lesnoop.scan

import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.Timeout
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.ScannerFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Named

data class QueueItem<T>(
    val obs: T
)

abstract class AbstractQueue<T : Any>(
    val scanner: ScannerFactory,
    @param:Named(Module.DB_SCHEDULER) val dbScheduler: Scheduler
) {
    private val connectQueueDisposable = AtomicReference<Disposable?>(null)

    val lock = PublishSubject.create<QueueItem<T>>()

    fun accept( item: T) {
        lock.onNext(
            QueueItem(
                obs = item
            )
        )
    }

    abstract fun process(item: T): Completable

    fun processDumb() {
        val disp = lock
            .observeOn(dbScheduler)
            .flatMapCompletable { i ->
                Completable.defer {
                    process(i.obs)
                        .onErrorComplete()
                }
            }
            .subscribe(
                { Timber.e( "queue completed") },
                { err -> Timber.e( "queue error: $err") }
            )
        connectQueueDisposable.getAndSet(disp)?.dispose()
    }

    fun stopProcess() {
        connectQueueDisposable.getAndSet(null)?.dispose()
    }
}