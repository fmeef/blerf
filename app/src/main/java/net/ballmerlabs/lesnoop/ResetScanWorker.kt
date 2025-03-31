package net.ballmerlabs.lesnoop

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.polidea.rxandroidble3.RxBleClient
import java.util.UUID
import javax.inject.Inject

val SCAN_RESET_WORKER_ID = UUID.fromString("560E0A9D-AEE2-4A00-9165-CE786B339C09")

@HiltWorker
class ResetScanWorker(
    val context: Context,
    workerParameters: WorkerParameters
): Worker(context, workerParameters) {

    @Inject
    lateinit var client: RxBleClient

    @Inject
    lateinit var scanSnoopService: ScanSnoopService

    override fun doWork(): Result {
        Log.w(TAG, "reset scan worker fired, resetting scan")
        val intent = scanSnoopService.getStartIntent().apply {
            action = BackgroundScanService.ACTION_RELOAD
        }
        context.startService(intent)

        return Result.success()
    }

    companion object {
        const val TAG = "ResetScanWorker"
    }
}