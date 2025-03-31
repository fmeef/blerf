package net.ballmerlabs.lesnoop.scan

import androidx.lifecycle.LiveData
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
    fun insertResult(scanResult: ScanResult): Single<Pair<Long, ScanResult>>
    fun discoverServices(scanResult: ScanResult, dbid: Long? = null): Completable
    fun serviceState(): LiveData<Boolean>
}