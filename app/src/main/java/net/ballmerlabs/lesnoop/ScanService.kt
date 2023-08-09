package net.ballmerlabs.lesnoop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@AndroidEntryPoint
class ScanService : Service() {

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
            .setLegacy(false)
            .build()
        client.backgroundScanner.scanBleDeviceInBackground(pendingIntent, settings, filter)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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
       return null
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_FOREGROUND = "foreground"

    }
}