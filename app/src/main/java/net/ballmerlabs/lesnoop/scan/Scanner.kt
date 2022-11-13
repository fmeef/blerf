package net.ballmerlabs.lesnoop.scan

import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable

interface Scanner : Disposable {
    fun startScan(): Observable<ScanResult>
    fun startScanAndDiscover(): Observable<ScanResult>
    fun scanBackground()
    fun stopScanBackground()
    fun insertResult(scanResult: ScanResult): Single<Long>
    fun discoverServices(scanResult: ScanResult, dbid: Long? = null): Completable
}