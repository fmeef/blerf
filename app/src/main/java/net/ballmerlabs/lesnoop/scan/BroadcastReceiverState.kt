package net.ballmerlabs.lesnoop.scan

import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.CompletableSubject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastReceiverState @Inject constructor() {
    private val disposable = AtomicReference<Disposable?>(null)
    private val batchCounter = AtomicInteger()
    private val batch = ConcurrentHashMap<ScanResult, Boolean>()
    fun batch(scanResult: List<ScanResult>, count: Int, func: (result: Flowable<ScanResult>) -> Completable) {
        batch.putAll(scanResult.map { v -> Pair(v, true) })
        val c = batchCounter.accumulateAndGet(count) { v, acc ->
            val next = v + 1
            if (next > acc) {
                0
            } else {
                next
            }
        }
        if (batch.isNotEmpty() && c >= count) {
            val out = batch.keys().toList()
            batch.clear()
            addTask {
                val f = Flowable.fromIterable(out)
                    .distinct { v -> v.bleDevice.macAddress }
                func(f)
            }
        }
    }

    fun addTask(func: () -> Completable) {
        val comp = CompletableSubject.create()
        if (disposable.compareAndSet(null, comp.subscribe())) {
            func()
                .doFinally {
                    disposable.set(null)
                }
                .onErrorComplete()
                .subscribe(comp)
        }
    }
}