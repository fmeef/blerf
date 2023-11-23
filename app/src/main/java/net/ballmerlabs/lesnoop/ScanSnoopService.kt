package net.ballmerlabs.lesnoop

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.preference.PreferenceManager
import com.polidea.rxandroidble3.RxBlePhy
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import net.ballmerlabs.lesnoop.db.ScanResultDao
import net.ballmerlabs.lesnoop.scan.Scanner
import java.lang.IllegalArgumentException
import java.util.Collections
import java.util.EnumSet
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

    private fun scanBackground(intent: Intent) {
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

    fun phyToMask(override: List<String>? = null): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val phy = override ?: (prefs.getStringSet(PREF_PHY, mutableSetOf()) ?: mutableSetOf())
        var phyval = 0
        for (p in phy) {
            phyval = phyval or when(p) {
                PHY_1M -> BluetoothDevice.PHY_LE_1M_MASK
                PHY_2M -> BluetoothDevice.PHY_LE_2M_MASK
                PHY_CODED -> BluetoothDevice.PHY_LE_CODED_MASK
                else -> 0
            }
            Log.e("debug", "startScanToDb $p $phyval")
        }
        return phyval
    }

    fun phyToRxBle(override: List<String>? = null): EnumSet<RxBlePhy> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val phy = override ?: (prefs.getStringSet(PREF_PHY, mutableSetOf()) ?: mutableSetOf())
        val phyList = EnumSet.allOf(RxBlePhy::class.java)
        for (p in phy) {
            val v = when(p) {
                PHY_1M -> RxBlePhy.PHY_1M
                PHY_2M -> RxBlePhy.PHY_2M
                PHY_CODED -> RxBlePhy.PHY_CODED
                else -> RxBlePhy.PHY_UNKNOWN
            }
        }
        return phyList
    }

    private fun phyToVal(override: String? = null): Int {
        val phy = if (override != null)  {
            override
        } else {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            prefs.getString(PREF_PRIMARY_PHY, PHY_1M) ?: PHY_1M
        }
        return when(phy) {
            PHY_1M -> BluetoothDevice.PHY_LE_1M
            PHY_2M -> BluetoothDevice.PHY_LE_2M
            PHY_CODED -> BluetoothDevice.PHY_LE_CODED
            else -> ScanSettings.PHY_LE_ALL_SUPPORTED
        }
    }

    fun startScanToDb(legacy: Boolean, phy: String? = null) {
        try {
            val phyVal = phyToVal(phy)
            val intent = Intent(applicationContext, BackgroundScanService::class.java)
                .apply {
                    putExtra(BackgroundScanService.EXTRA_LEGACY_MODE, legacy)
                    putExtra(BackgroundScanService.EXTRA_PHY, phyVal)
                }
            val scanner = applicationContext.getScan(scanBuilder)
            val old = scan.getAndSet(scanner)
            stopScanBackground()
            old?.dispose()
            scanBackground(intent)
            applicationContext.bindService(intent, connection, 0)
        } catch (exc: Exception) {
            Log.e("debug", "failed to start scan $exc")
        }
    }

    fun getScanPhy(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return prefs.getString(PREF_PRIMARY_PHY, PHY_1M)?: PHY_1M
    }

    fun getPhy(): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val set = prefs.getStringSet(PREF_PHY, mutableSetOf())?: mutableSetOf()
        return set.toList()
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

    companion object {
        const val PHY_CODED = "coded"
        const val PHY_1M = "1M"
        const val PHY_2M = "2M"
        const val PREF_PHY = "phy"
        const val PREF_PRIMARY_PHY = "primaryphy"
        const val PREF_LEGACY = "legacy"
    }
}