package net.ballmerlabs.lesnoop

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import com.polidea.rxandroidble3.scan.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.disposables.Disposable
import me.bytebeats.views.charts.pie.PieChartData
import net.ballmerlabs.lesnoop.db.OuiParser
import net.ballmerlabs.lesnoop.db.ScanResultDao
import net.ballmerlabs.lesnoop.scan.Scanner
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

@HiltViewModel
class ScanViewModel @Inject constructor(
    val scanResultDao: ScanResultDao,
    val scanBuilder: ScanSubcomponent.Builder,
    @Named(Module.DB_PATH) val dbPath: File,
    private val ouiParser: OuiParser,
    @Named(Module.DB_SCHEDULER) private val dbScheduler: Scheduler,
) : ViewModel() {
    val currentScans = mutableStateListOf<ScanResult>()
    val topText = mutableStateOf("")
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
            .subscribeOn(dbScheduler)
            .flatMapObservable { r -> Observable.fromIterable(r) }
            .flatMapSingle { r -> ouiParser.ouiForDevice(r.macAddress) }
           // .filter { r -> r != "N/A" }
            .reduce(HashMap<String, Int>()) { set, r ->
                set.compute(r) { s, i ->
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
                m.toList().sortedByDescending { (_, v) -> v }.dropLast(drop).toMap()
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
                        Log.v("debug", "slice $color ${s.key}, ${s.value}")
                        PieChartData.Slice(
                             s.value.toFloat(),
                            color
                        )
                    }
                    .toList()
                    .doOnSuccess { s -> Log.v("debug", "result: ${s.size}") }
            }

            .observeOn(AndroidSchedulers.mainThread())


    }
}