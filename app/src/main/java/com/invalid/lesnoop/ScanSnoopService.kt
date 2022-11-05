package com.invalid.lesnoop

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.invalid.lesnoop.db.ScanResultDao
import com.invalid.lesnoop.scan.Scanner
import com.polidea.rxandroidble3.scan.ScanResult
import dagger.hilt.EntryPoints
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class ScanSnoopService : Service() {

    @Inject
    lateinit var dao: ScanResultDao

    @Inject
    lateinit var scanBuilder: ScanSubcomponent.Builder

    private val scan = AtomicReference<ScanHandle>()

    fun startScanToDb(): Observable<ScanResult> {
        val scanner = applicationContext.getScan(scanBuilder)
        val subj = PublishSubject.create<ScanResult>()
        val handle = ScanHandle(
            subject = subj,
            disp = scanner
        )
        scan.getAndSet(handle)?.disp?.dispose()

        scanner.startScanAndDiscover().subscribe(subj)

        return handle.subject
    }

    fun stopScan() {
        scan.getAndSet(null)?.disp?.dispose()
    }


    private val binder = SnoopBinder()


    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    data class ScanHandle(
        val subject: PublishSubject<ScanResult>,
        val disp: Disposable
    )

    inner class SnoopBinder: Binder() {
        fun getService(): ScanSnoopService = this@ScanSnoopService
    }
}