package com.invalid.lesnoop.scan

import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable

interface Scanner : Disposable {
    fun startScan(): Observable<ScanResult>
    fun startScanAndDiscover(): Observable<ScanResult>
}