package com.invalid.lesnoop

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.invalid.lesnoop.db.OuiParser
import com.invalid.lesnoop.db.ScanResultDao
import com.polidea.rxandroidble3.scan.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import me.bytebeats.views.charts.pie.PieChartData
import java.io.File
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ScanViewModel @Inject constructor(
    val scanResultDao: ScanResultDao,
    val scanBuilder: ScanSubcomponent.Builder,
    @Named(Module.DB_PATH) val dbPath: File,
    private val ouiParser: OuiParser
) : ViewModel() {
    val currentScans = mutableStateListOf<ScanResult>()
    val topText = mutableStateOf("Nearby devices")
    var scanInProgress = mutableStateOf<Disposable?>(null)

    private val colorList = listOf(Color.Cyan, Color.Red, Color.Blue, Color.DarkGray, Color.Green, Color.Magenta, Color.Yellow)

    private val colors: Flowable<Color> = Flowable.defer {
        Flowable.fromIterable(
            colorList
        ).repeat()
    }

    init {
        Log.e("debug", "viewmodel reconstructed")
    }

    private fun getTopOuis(size: Int): Single<Map<String, Int>> {
        return scanResultDao.getScanResults()
            .subscribeOn(Schedulers.io())
            .flatMapObservable { r -> Observable.fromIterable(r) }
            .flatMapSingle { r -> ouiParser.ouiForDevice(r.macAddress) }
            .reduce(HashMap<String, Int>()) { set, r ->
                set.compute(r) { _, i ->
                    if (i == null) {
                        0
                    } else {
                        i + 1
                    }
                }
                set
            }
            .map { m ->
                val s = m.size - size
                val drop = if (s < 0) {
                    0
                } else {
                    s
                }
                m.toList().sortedBy { (_, v) -> v }.dropLast(drop).toMap()
            }
    }

    fun legendState(): Single<List<Pair<String, Color>>> {
        return getTopOuis(colorList.size)
            .flatMapObservable { v -> Observable.fromIterable(v.keys) }
            .zipWith(Observable.fromIterable(colorList)) { name, color ->
                Pair(name, color)
            }
            .toList()
    }

    fun pieChartState(): Single<List<PieChartData.Slice>> {
        return getTopOuis(colorList.size)
            .flatMap { sv ->
                val sum = sv.values.sum()
                Observable.fromIterable(sv.entries)
                    .toFlowable(BackpressureStrategy.BUFFER)
                    .zipWith(colors) { s, color ->
                        Log.v("debug", "slice $color ${s.key}")
                        PieChartData.Slice(
                             s.value.toFloat() / sum.toFloat(),
                            color
                        )
                    }
                    .toList()
                    .doOnSuccess { s -> Log.v("debug", "result: ${s.size}") }
            }

            .observeOn(AndroidSchedulers.mainThread())


    }
}