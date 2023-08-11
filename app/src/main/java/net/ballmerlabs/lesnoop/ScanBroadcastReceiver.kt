package net.ballmerlabs.lesnoop

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.scan.ScanResult
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import net.ballmerlabs.lesnoop.scan.BroadcastReceiverState
import net.ballmerlabs.lesnoop.scan.LocationTagger
import net.ballmerlabs.lesnoop.scan.Scanner
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

private const val SCAN_REQUEST_CODE = 44

@AndroidEntryPoint
class ScanBroadcastReceiver @Inject constructor() : BroadcastReceiver() {
    @Inject
    lateinit var locationTagger: LocationTagger

    @Inject
    lateinit var client: RxBleClient

    @Inject
    lateinit var scanBuilder: ScanSubcomponent.Builder

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var state: BroadcastReceiverState

    @Inject
    @Named(Module.GLOBAL_SCAN)
    lateinit var scanner: Scanner

    fun newPendingIntent(): PendingIntent =
        Intent(context, ScanBroadcastReceiver::class.java).let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getBroadcast(
                    context,
                    SCAN_REQUEST_CODE,
                    it,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getBroadcast(
                    context,
                    SCAN_REQUEST_CODE,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        }


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
                    .flatMapCompletable { dbIds ->
                        Observable.fromIterable(dbIds).flatMapCompletable { scanResult ->
                            scanner.discoverServices(scanResult.second, scanResult.first)
                                .onErrorComplete()
                        }.timeout(60, TimeUnit.SECONDS)

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