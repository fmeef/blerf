package net.ballmerlabs.lesnoop

import android.app.PendingIntent
import android.app.Service
import android.bluetooth.le.BluetoothLeScanner
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import net.ballmerlabs.lesnoop.db.ScanResultDao
import com.polidea.rxandroidble3.scan.ScanResult
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import net.ballmerlabs.lesnoop.scan.Scanner
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class ScanSnoopService : Service() {

    @Inject
    lateinit var dao: ScanResultDao

    @Inject
    lateinit var scanBuilder: ScanSubcomponent.Builder

    @Inject
    lateinit var broadcastReceiver: ScanBroadcastReceiver

    private val scan = AtomicReference<Scanner?>(null)

    fun startScanToDb() {
        val scanner = applicationContext.getScan(scanBuilder)
        val old = scan.getAndSet(scanner);
        old?.stopScanBackground()
        old?.dispose()
        scanner.scanBackground()
    }

    fun stopScan() {
        val s = scan.getAndSet(null)
        if (s != null) {
            s.stopScanBackground()
            s.dispose()
        } else {
            applicationContext.getScan(scanBuilder).stopScanBackground()
        }
    }

    private val binder = SnoopBinder()


    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }

    override fun onCreate() {
        super.onCreate()
        stopScan()
    }

    inner class SnoopBinder: Binder() {
        fun getService(): ScanSnoopService = this@ScanSnoopService
    }
}