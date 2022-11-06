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

    private val scan = AtomicReference<ScanHandle>()

    fun startScanToDb(): Observable<ScanResult> {
        val scanner = applicationContext.getScan(scanBuilder)
        val subj = PublishSubject.create<ScanResult>()
        val handle = ScanHandle(
            subject = subj,
            disp = scanner
        )
        scan.getAndSet(handle)?.disp?.dispose()

        scanner.scanBackground()

        return handle.subject
    }

    fun stopScan() {
        scan.get()?.disp?.stopScanBackground()
        scan.getAndSet(null)?.disp?.dispose()
    }

    private val binder = SnoopBinder()


    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    data class ScanHandle(
        val subject: PublishSubject<ScanResult>,
        val disp: Scanner
    )

    inner class SnoopBinder: Binder() {
        fun getService(): ScanSnoopService = this@ScanSnoopService
    }
}