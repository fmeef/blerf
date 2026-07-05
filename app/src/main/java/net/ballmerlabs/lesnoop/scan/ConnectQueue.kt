package net.ballmerlabs.lesnoop.scan

import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.SharedPreferences
import com.jakewharton.rxrelay3.PublishRelay
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.exceptions.BleAlreadyConnectedException
import com.polidea.rxandroidble3.exceptions.BleDisconnectedException
import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.ScannerFactory
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ConnectQueue @Inject constructor(
    val prefs: SharedPreferences,
    val state: Provider<BroadcastReceiverState>,
    @param:Named(Module.TIMEOUT_SCHEDULER)
    val timeoutScheduler: Scheduler
)  {

    private val connectedStats = PublishRelay.create<Int>()

    private val inflight = ConcurrentHashMap<String, Disposable>()

    fun observeConnected(): Observable<Int> {
        return connectedStats
    }

    fun accept(result: ScanResult, value: Single<Boolean>, legacy: Boolean) {
        val device = result.bleDevice
        val max = prefs.getInt(ScannerFactory.PREF_MAX_CONNECTION, 7)
        if (inflight.size <= max) {
             val res = inflight.putIfAbsent(device.macAddress,   value
                 .timeout(prefs.getLong(ScannerFactory.PREF_CONNECT_TIMEOUT, 7), TimeUnit.SECONDS, timeoutScheduler)
                 .doFinally { inflight.remove(device.macAddress) }
                 .doOnDispose { inflight.remove(device.macAddress) }
                 .onErrorResumeNext { err: Throwable ->
                     when(err) {
                         is BleDisconnectedException -> {
                             when (err.state) {
                                 GATT_SUCCESS -> Single.just(true)
                                 else -> Single.error(err)
                             }
                         }
                         else -> Single.error(err)

                     }
                 }
                 .subscribe(
                     { v ->
                         Timber.w("connect success!")
                         // shutdown()
                     },
                     { err ->
                         when (err) {
//                                is BleDisconnectedException -> {
//                                    when (err.state) {
//                                        133 -> shutdown()
//                                        else -> Unit
//                                    }
//                                }


                             is BleAlreadyConnectedException -> {
                                 shutdown()
                             }

                             else -> Unit
                         }
                         Timber.e(" ${device.macAddress} queue connect error $err")
                     }

                 ))
            if (res != null) {
                state.get().insertWithoutConnecting(result, legacy)
            }


            connectedStats.accept(inflight.size)
        }
    }

    @Synchronized
    fun shutdown() {
        val values = inflight.values.toList()
        values.forEach { v ->
            v.dispose()
        }

        inflight.clear()
        connectedStats.accept(inflight.size)
    }
}