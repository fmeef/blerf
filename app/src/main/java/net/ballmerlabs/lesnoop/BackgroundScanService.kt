package net.ballmerlabs.lesnoop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BackgroundScanService : Service() {

    val running = MutableLiveData(false)

    private val binder = LocalBinder()

    @Inject
    lateinit var client: RxBleClient

    @Inject
    lateinit var scanBroadcastReceiver: ScanBroadcastReceiver

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val pendingIntent = scanBroadcastReceiver.newPendingIntent()
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_FOREGROUND)
            .setContentTitle("Subrosa")
            .setContentText("Scanning...\n(this uses location permission, but not actual geolocation)")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setTicker("fmef am tire")
            .build()
        val filter = ScanFilter.Builder()
            .build()
        startForeground(1, notification)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setLegacy(true)
            .build()
        client.backgroundScanner.scanBleDeviceInBackground(pendingIntent, settings, filter)
        running.postValue(true)
        return START_STICKY
    }
    override fun onDestroy() {
        super.onDestroy()
        running.postValue(false)
        val pendingIntent = scanBroadcastReceiver.newPendingIntent()
        client.backgroundScanner.stopBackgroundBleScan(pendingIntent)
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_FOREGROUND,
            "subrosanotif",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    override fun onBind(intent: Intent): IBinder? {
       return binder
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_FOREGROUND = "foreground"
    }
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods.
        fun getService(): BackgroundScanService = this@BackgroundScanService
    }

}