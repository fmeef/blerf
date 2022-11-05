package com.invalid.lesnoop.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.invalid.lesnoop.Module
import com.invalid.lesnoop.ScanScope
import com.invalid.lesnoop.ScanSubcomponent
import com.invalid.lesnoop.db.ScanResultDao
import com.invalid.lesnoop.db.entity.DbScanResult
import com.invalid.lesnoop.db.entity.ServicesWithChildren
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
    private val locationService: LocationManager,
    @Named(ScanSubcomponent.EXECUTOR_COMPUTE)
    private val executor: Executor,
    private val context: Context
) : Scanner {
    private val disp = CompositeDisposable()


    private fun tagLocation(scanResult: ScanResult): Maybe<DbScanResult> {
        return Single.just(scanResult).flatMapMaybe<DbScanResult?> { r ->
            val criteria = Criteria().apply {
                accuracy = Criteria.ACCURACY_FINE
                isCostAllowed = false
            }
            val provider = locationService.getBestProvider(criteria, true)
            if (provider != null && locationService.isProviderEnabled(provider)) {
                Maybe.create { m ->
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            locationService.getCurrentLocation(provider, null, executor) { l ->
                                m.onSuccess(DbScanResult(r, location = l))
                            }
                        } else {
                            locationService.requestSingleUpdate(provider, { l ->
                                m.onSuccess(DbScanResult(r, location = l))
                            }, null)
                        }
                    } else {
                        m.onError(SecurityException("missing location permission"))
                    }
                }
            } else {
                Maybe.empty()
            }

        }.doOnSubscribe { Log.v(NAME, "getting location for ${scanResult.bleDevice.macAddress}") }
            .doOnError { e -> Log.e(NAME, "failed to get location for ${scanResult.bleDevice.macAddress}: $e") }
    }

    private fun insertResult(scanResult: ScanResult): Completable {
        return tagLocation(scanResult)
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
            .concatMapSingle { connection -> connection.discoverServices() }
            .flatMap { s -> Observable.fromIterable(s.bluetoothGattServices) }
            .map { s -> ServicesWithChildren(s) }
            .flatMapCompletable { services ->
                database.insertService(services)
            }
            .doOnError { err -> Log.e(NAME, "failed to discover services for $device: $err") }
            .onErrorComplete()
    }

    private fun discoverServices(scanResult: ScanResult): Completable {
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