package net.ballmerlabs.lesnoop

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import net.ballmerlabs.lesnoop.db.ScanResultDao
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
        try {
            val scanner = applicationContext.getScan(scanBuilder)
            val old = scan.getAndSet(scanner)
            old?.stopScanBackground()
            old?.dispose()
            scanner.scanBackground()
        } catch (exc: Exception) {
            Log.e("debug", "failed to start scan $exc")
        }
    }

    fun stopScan() {
        try {
            val s = scan.getAndSet(null)
            if (s != null) {
                s.stopScanBackground()
                s.dispose()
            } else {
                applicationContext.getScan(scanBuilder).stopScanBackground()
            }
            unregisterReceiver(broadcastReceiver)
        } catch (exc: Exception) {
            Log.e("debug", "failed to stop scan $exc")
        }
    }

    private val binder = SnoopBinder()


    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class SnoopBinder : Binder() {
        fun getService(): ScanSnoopService = this@ScanSnoopService
    }
}