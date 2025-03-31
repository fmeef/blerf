package net.ballmerlabs.lesnoop

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.scan.ScanResult
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import net.ballmerlabs.lesnoop.scan.BroadcastReceiverState
import net.ballmerlabs.lesnoop.scan.LocationTagger
import net.ballmerlabs.lesnoop.scan.Scanner
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

const val SCAN_REQUEST_CODE = 44

@AndroidEntryPoint
class ScanBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var client: RxBleClient


    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var state: BroadcastReceiverState

    @Inject
    lateinit var scanSnoopService: ScanSnoopService

    @Inject
    @Named(Module.GLOBAL_SCAN)
    lateinit var scanner: Scanner

    @Inject
    @Named(Module.CONNECT_SCHEDULER)
    lateinit var connectScheduler: Scheduler

    @Inject
    @Named(Module.TIMEOUT_SCHEDULER)
    lateinit var timeoutScheduler: Scheduler

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val result = client.backgroundScanner.onScanResultReceived(intent)
            state.batch(result, 5) { results ->
                results.flatMapSingle { r ->
                    Log.w("debug", "batch results ${r.bleDevice.macAddress}")
                    scanner.insertResult(r)
                }.reduce(mutableListOf<Pair<Long, ScanResult>>()) { list, v ->
                    list.add(v)
                    list
                }.timeout(30, TimeUnit.SECONDS)
                    .concatMapCompletable { dbIds ->
                        Observable.fromIterable(dbIds)
                            .delay(1, TimeUnit.SECONDS, timeoutScheduler )
                            .concatMapCompletable { scanResult ->
                                scanner.discoverServices(scanResult.second, scanResult.first)
                                    .timeout(25, TimeUnit.SECONDS,  timeoutScheduler)
                                    .onErrorComplete()
                            }

                    }
                    .doOnComplete { Log.w("debug", "connect complete") }
                    .onErrorComplete()
            }
        } catch (exc: Exception) {
            Log.w("debug", "exception in broadcastreceiver $exc")
            exc.printStackTrace()
        }
    }
}