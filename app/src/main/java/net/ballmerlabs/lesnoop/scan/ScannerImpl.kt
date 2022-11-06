package net.ballmerlabs.lesnoop.scan

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.ScanScope
import net.ballmerlabs.lesnoop.ScanSubcomponent
import net.ballmerlabs.lesnoop.db.ScanResultDao
import net.ballmerlabs.lesnoop.db.entity.DbScanResult
import net.ballmerlabs.lesnoop.db.entity.ServicesWithChildren
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.exceptions.BleScanException
import com.polidea.rxandroidble3.scan.IsConnectable
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanResult
import com.polidea.rxandroidble3.scan.ScanSettings
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import net.ballmerlabs.lesnoop.ScanBroadcastReceiver
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

private fun <T : Any> handleUndocumentedScanThrottling(
    e: Observable<Throwable>,
    defaultDelay: Long = 10
): Observable<T> {
    return e.concatMap { err ->
        if (err is BleScanException && err.retryDateSuggestion != null) {
            val delay = err.retryDateSuggestion!!.time - Date().time
            Log.e("debug", "undocumented scan throttling. Waiting $delay seconds")
            Completable.complete().delay(delay, TimeUnit.SECONDS)
                .andThen(Observable.error(err))
        } else {
            Completable.complete().delay(defaultDelay, TimeUnit.SECONDS)
                .andThen(Observable.error(err))
        }
    }
}

@ScanScope
class ScannerImpl @Inject constructor(
    private val client: RxBleClient,
    @Named(ScanSubcomponent.SCHEDULER_SCAN)
    private val scheduler: Scheduler,
    private val database: ScanResultDao,
    @Named(Module.DB_SCHEDULER)
    private val dbScheduler: Scheduler,
    context: Context,
    private val locationTagger: LocationTagger
) : Scanner {
    private val disp = CompositeDisposable()

    private val pendingIntent: PendingIntent = ScanBroadcastReceiver.newPendingIntent(context)


    override fun scanBackground() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setLegacy(false)
            .build()
        val filter = ScanFilter.Builder().build()
        client.backgroundScanner.scanBleDeviceInBackground(pendingIntent, settings, filter)
    }

    override fun stopScanBackground() {
        client.backgroundScanner.stopBackgroundBleScan(pendingIntent)
    }

    override fun insertResult(scanResult: ScanResult): Completable {
        return locationTagger.tagLocation(scanResult)
            .onErrorReturn {
                DbScanResult(scanResult)
            }
            .flatMapCompletable { result ->
                database.insertScanResult(result)
                    .doOnSubscribe { Log.v(NAME, "inserting scan result") }
                    .doOnComplete { Log.v(NAME, "insert complete") }

                    .doOnError { e -> Log.v(NAME, "insert error $e") }
                    .subscribeOn(dbScheduler)
            }
    }

    private fun discoverServices(device: RxBleDevice): Completable {
        return device.establishConnection(false)
            .subscribeOn(scheduler)
            .concatMapCompletable { connection ->
                connection.discoverServices()
                    .flatMapObservable { s -> Observable.fromIterable(s.bluetoothGattServices) }
                    .map { s -> ServicesWithChildren(s) }
                    .flatMapCompletable { services ->
                        database.insertService(services)
                    }
                    .doOnError { err -> Log.e(NAME, "failed to discover services for ${device.macAddress}: $err") }
                    .doOnComplete { Log.v(NAME, "successfully discovered services for ${device.macAddress}") }
                    .onErrorComplete()
            }

    }

    override fun discoverServices(scanResult: ScanResult): Completable {
        return when (scanResult.isConnectable) {
            IsConnectable.CONNECTABLE -> discoverServices(scanResult.bleDevice)
            IsConnectable.NOT_CONNECTABLE -> Completable.complete()
            IsConnectable.LEGACY_UNKNOWN -> discoverServices(scanResult.bleDevice)
            else -> Completable.complete()
        }
    }


    private fun scanInternal(): Observable<ScanResult> {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setLegacy(false)
            .build()
        val filter = ScanFilter.Builder().build()
        return client.scanBleDevices(
            settings,
            filter
        )
            .delay(0, TimeUnit.SECONDS, scheduler)
            .doOnSubscribe { d -> disp.add(d) }
            .retryWhen { e -> handleUndocumentedScanThrottling<ScanResult>(e) }
            .doOnNext { r -> Log.v(NAME, "scan result $r") }
            .doOnError { e -> Log.e(NAME, "scan error $e") }
    }

    override fun startScan(): Observable<ScanResult> {
        return scanInternal()
            .flatMapSingle { r ->
                insertResult(r).toSingleDefault(r)
            }

    }

    override fun startScanAndDiscover(): Observable<ScanResult> {
        return scanInternal()
            .flatMapSingle { result -> discoverServices(result).toSingleDefault(result) }
    }

    override fun dispose() {
        disp.dispose()
    }

    override fun isDisposed(): Boolean {
        return disp.isDisposed
    }

    companion object {
        const val NAME = "Scanner"
    }
}