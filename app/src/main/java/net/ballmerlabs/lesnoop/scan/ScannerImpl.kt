package net.ballmerlabs.lesnoop.scan

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.preference.PreferenceManager
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.RxBlePhy
import com.polidea.rxandroidble3.RxBlePhyOption
import com.polidea.rxandroidble3.exceptions.BleDisconnectedException
import com.polidea.rxandroidble3.exceptions.BleScanException
import com.polidea.rxandroidble3.scan.IsConnectable
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanResult
import com.polidea.rxandroidble3.scan.ScanSettings
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.ballmerlabs.lesnoop.*
import net.ballmerlabs.lesnoop.ScanSnoopService.Companion.PHY_1M
import net.ballmerlabs.lesnoop.ScanSnoopService.Companion.PHY_2M
import net.ballmerlabs.lesnoop.ScanSnoopService.Companion.PHY_CODED
import net.ballmerlabs.lesnoop.ScanSnoopService.Companion.PREF_PHY
import net.ballmerlabs.lesnoop.ScanSnoopService.Companion.PREF_PRIMARY_PHY
import net.ballmerlabs.lesnoop.db.ScanResultDao
import net.ballmerlabs.lesnoop.db.entity.DbScanResult
import net.ballmerlabs.lesnoop.db.entity.ServicesWithChildren
import java.lang.IllegalArgumentException
import java.util.*
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
    private val database: ScanResultDao,
    @Named(Module.DB_SCHEDULER)
    private val dbScheduler: Scheduler,
    private val context: Context,
    private val locationTagger: LocationTagger,
    private val service: ScanSnoopService,
    @Named(Module.CONNECT_SCHEDULER)
    private val connectScheduler: Scheduler,
    @Named(Module.TIMEOUT_SCHEDULER)
    private val timeoutScheduler: Scheduler
) : Scanner {
    private val disp = CompositeDisposable()
    private lateinit var mService: BackgroundScanService
    private var mBound = MutableLiveData<Boolean>()

    /** Defines callbacks for service binding, passed to bindService().  */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance.
            val binder = service as BackgroundScanService.LocalBinder
            mService = binder.getService()
            mBound.postValue(true)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound.postValue(false)
        }
    }

    override fun serviceState(): LiveData<Boolean> {
        return mBound.switchMap { v-> if(v) mService.running else mBound}
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun updatePrefScan(isScanning: Boolean) {
        context.rxPrefs.updateDataAsync() { prefs ->
            val p = prefs.toMutablePreferences()
            p[PREF_BACKGROUND_SCAN] =  isScanning
            Single.just(p)
        }.subscribeOn(dbScheduler)
            .timeout(1, TimeUnit.SECONDS)
            .ignoreElement()
            .subscribe(
                { Log.v(NAME, "updated prefs") },
                { err -> Log.e(NAME, "failed to update prefs $err") }
            )
    }

    override fun scanBackground() {
        val intent = Intent(context.applicationContext, BackgroundScanService::class.java)

        context.applicationContext.startService(intent)
        context.applicationContext.bindService(intent, connection, 0)
        updatePrefScan(true)
    }

    override fun stopScanBackground() {
        //ontext.unregisterReceiver(reciever)
        try {
            context.applicationContext.stopService(
                Intent(context.applicationContext, BackgroundScanService::class.java)
            )
        } catch (exc: IllegalArgumentException) {
            Log.w("debug", "service already stopped: $exc")
        }
        updatePrefScan(false)
    }

    override fun insertResult(scanResult: ScanResult): Single<Pair<Long, ScanResult>> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val phy = prefs.getString(PREF_PRIMARY_PHY, null)
        val phyVal = service.phyToVal(phy)
        return locationTagger.tagLocation(scanResult)
            .onErrorReturn {
                DbScanResult(scanResult, phy = phyVal)
            }
            .defaultIfEmpty(DbScanResult(scanResult = scanResult))
            .flatMap { result ->
                database.insertScanResult(result)
                    .doOnSubscribe { Log.v(NAME, "inserting scan result") }
                    .doOnSuccess { Log.v(NAME, "insert complete") }

                    .doOnError { e -> Log.v(NAME, "insert error $e") }
                    .subscribeOn(dbScheduler)

            }
            .map { r -> Pair(r, scanResult) }
    }

    private fun <T : Any> smartRetry(connection: Observable<T>, times: Int): Observable<T> {
        return connection
            .subscribeOn(connectScheduler)
            .retryWhen { errs: Observable<Throwable> ->
            errs.zipWith(
                Observable.range(
                    1,
                    times
                )
            ) { err: Throwable, i: Int -> err }
                .flatMapSingle { err ->
                    when (err) {
                        is BleDisconnectedException -> {
                            when (err.state) {
                                133 -> Single.timer(
                                    250,
                                    TimeUnit.MILLISECONDS,
                                    connectScheduler
                                ) // Unknown gatt error
                                257 -> Single.timer(
                                    10,
                                    TimeUnit.SECONDS,
                                    connectScheduler
                                ) // Weird connect throttling
                                else -> Single.error(err)
                            }
                        }

                        else -> Single.error(err)
                    }
                }
        }
    }

    private fun <T : Any> smartRetry(connection: Single<T>, times: Int): Single<T> {
        return connection.retryWhen { errs: Flowable<Throwable> ->
            errs.zipWith(
                Flowable.range(
                    1,
                    times
                )
            ) { err: Throwable, i: Int -> err }
                .flatMapSingle { err ->
                    when (err) {
                        is BleDisconnectedException -> {
                            when (err.state) {
                                133 -> Single.timer(
                                    250,
                                    TimeUnit.MILLISECONDS,
                                    timeoutScheduler
                                ) // Unknown gatt error
                                22 -> Single.timer(
                                    250,
                                    TimeUnit.MILLISECONDS,
                                    timeoutScheduler
                                ) // GATT_CONN_TERMINATE_LOCAL_HOST
                                257 -> Single.timer(
                                    20,
                                    TimeUnit.SECONDS,
                                    timeoutScheduler
                                ) // Weird connect throttling
                                else -> Single.error(err)
                            }
                        }

                        else -> Single.error(err)
                    }
                }
        }
    }

    private fun discoverServices(device: RxBleDevice, dbid: Long? = null): Completable {
        return Completable.defer {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getBoolean(ScanSnoopService.PREF_CONNECT, false)) {
                smartRetry(device.establishConnection(false), 3)
                    .concatMapSingle { c ->
                        val tx = service.phyToRxBle()
                        if (tx.isNotEmpty()) {
                            smartRetry(c.setPreferredPhy(tx, tx, RxBlePhyOption.PHY_OPTION_NO_PREFERRED), 2)
                                .flatMapObservable { phy ->
                                    smartRetry(c.discoverServices(), 3)
                                        .flatMapObservable { s -> Observable.fromIterable(s.bluetoothGattServices) }
                                        .map { s -> ServicesWithChildren(s, phy = phy) }
                                }
                                .onErrorResumeNext{
                                    smartRetry(c.discoverServices(), 3)
                                    .flatMapObservable { s -> Observable.fromIterable(s.bluetoothGattServices) }
                                    .map { s -> ServicesWithChildren(s, phy = null) }
                                }
                                .flatMapCompletable { services ->
                                    database.insertService(services, scanResult = dbid)
                                        .subscribeOn(dbScheduler)
                                }
                                .doOnError { err ->
                                    Log.e(
                                        NAME,
                                        "failed to discover services for ${device.macAddress}: $err"
                                    )
                                }
                                .doOnComplete {
                                    Log.v(
                                        NAME,
                                        "successfully discovered services for ${device.macAddress}"
                                    )
                                }
                                .onErrorComplete()
                                .toSingleDefault(c)
                                .doOnError { err ->
                                    Log.w(
                                        NAME,
                                        "failed to set new phy $tx, continuing without: $err"
                                    )
                                }
                        } else {
                            Single.just(c)
                        }
                    }

                    .doOnError { err -> Log.e("debug", "connection error $err") }
                    .firstOrError()
                    .ignoreElement()
            } else {
                Completable.complete()
            }
        }

    }

    override fun discoverServices(scanResult: ScanResult, dbid: Long?): Completable {
        return when (scanResult.isConnectable) {
            IsConnectable.CONNECTABLE -> discoverServices(scanResult.bleDevice, dbid)
            IsConnectable.NOT_CONNECTABLE -> Completable.complete()
            IsConnectable.LEGACY_UNKNOWN -> discoverServices(scanResult.bleDevice, dbid)
            else -> Completable.complete()
        }
    }


    private fun scanInternal(): Observable<ScanResult> {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setLegacy(false)
            .build()
        val filter = ScanFilter.Builder().build()
        return client.scanBleDevices(
            settings,
            filter
        )
            .doOnSubscribe { d -> disp.add(d) }
            .retryWhen { e -> handleUndocumentedScanThrottling<ScanResult>(e) }
            .doOnNext { r -> Log.v(NAME, "scan result $r") }
            .doOnError { e -> Log.e(NAME, "scan error $e") }
    }

    override fun startScan(): Observable<ScanResult> {
        return scanInternal()
            .flatMapSingle { r ->
                insertResult(r).ignoreElement().toSingleDefault(r)
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