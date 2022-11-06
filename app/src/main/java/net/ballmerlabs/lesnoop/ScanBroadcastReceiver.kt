package net.ballmerlabs.lesnoop

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.polidea.rxandroidble3.RxBleClient
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import net.ballmerlabs.lesnoop.scan.BroadcastReceiverState
import net.ballmerlabs.lesnoop.scan.LocationTagger
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

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
    lateinit var state: BroadcastReceiverState
    
    private var disp: Disposable? = null

    companion object {
        fun newPendingIntent(context: Context): PendingIntent =
            Intent(context, ScanBroadcastReceiver::class.java).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.getBroadcast(
                        context,
                        SCAN_REQUEST_CODE,
                        it,
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                    )
                } else {
                    PendingIntent.getBroadcast(context, SCAN_REQUEST_CODE, it, PendingIntent.FLAG_CANCEL_CURRENT)
                }
            }
    }


    override fun onReceive(context: Context, intent: Intent) {
        try {
            val b = state.busy.getAndSet(true)
            if (!b) {
                val result = client.backgroundScanner.onScanResultReceived(intent)
                val scanner = context.getScan(scanBuilder)
                Observable.fromIterable(result).concatMapCompletable { r ->
                    scanner.insertResult(r).andThen(scanner.discoverServices(r))
                }
                    .doOnSubscribe { d -> disp = d }
                    .doOnDispose {
                        disp = null
                        scanner.dispose()
                        state.busy.set(false)
                    }
                    .doFinally {
                        disp = null
                        scanner.dispose()
                        state.busy.set(false)
                    }
                    .subscribe(
                    { Log.v("debug", "inserted background scan") },
                    { err -> Log.e("debug", "failed to insert background scan $err")}
                )

                Log.v("debug", "background scan result $result")
            }
        } catch (exc: Exception) {
            Log.w("debug", "exception in broadcastreceiver $exc")
            exc.printStackTrace()
        }
    }
}