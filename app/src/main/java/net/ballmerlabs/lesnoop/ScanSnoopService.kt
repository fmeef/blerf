package net.ballmerlabs.lesnoop

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import net.ballmerlabs.lesnoop.db.ScanResultDao
import net.ballmerlabs.lesnoop.scan.Scanner
import java.lang.IllegalArgumentException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanSnoopService @Inject constructor(
    val scanBuilder: ScanSubcomponent.Builder,
    @ApplicationContext val applicationContext: Context
) {
    private lateinit var mService: BackgroundScanService
    private var mBound = MutableLiveData(false)
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

    private val scan = AtomicReference<Scanner?>(null)

    private fun scanBackground() {
        val intent = Intent(applicationContext.applicationContext, BackgroundScanService::class.java)

        applicationContext.applicationContext.startService(intent)
        applicationContext.applicationContext.bindService(intent, connection, 0)
    }

    private fun stopScanBackground() {
        try {
            applicationContext.applicationContext.stopService(
                Intent(applicationContext.applicationContext, BackgroundScanService::class.java)
            )
        } catch (exc: IllegalArgumentException) {
            Log.w("debug", "service already stopped: $exc")
        }
    }


    fun serviceState(): LiveData<Boolean> {
        return mBound.switchMap { v-> if(v) mService.running else MutableLiveData(false)}
    }
    fun startScanToDb() {
        try {
            val intent = Intent(applicationContext, BackgroundScanService::class.java)
            val scanner = applicationContext.getScan(scanBuilder)
            val old = scan.getAndSet(scanner)
            stopScanBackground()
            old?.dispose()
            scanBackground()
            applicationContext.bindService(intent, connection, 0)
        } catch (exc: Exception) {
            Log.e("debug", "failed to start scan $exc")
        }
    }

    fun stopScan() {
        try {
            val s = scan.getAndSet(null)
            stopScanBackground()
            if (s != null) {
                s.dispose()
            } else {
                applicationContext.getScan(scanBuilder).stopScanBackground()
            }
        } catch (exc: Exception) {
            Log.e("debug", "failed to stop scan $exc")
            exc.printStackTrace()
        }
    }
}