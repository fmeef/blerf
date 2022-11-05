package com.invalid.lesnoop

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


@Composable
fun ThingList(padding: PaddingValues, model: ScanViewModel) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val state = rememberSwipeRefreshState(isRefreshing = false)
    var scanInProgress by remember {
        model.scanInProgress
    }
    val compositeDisposable by remember {
        mutableStateOf(CompositeDisposable())
    }
    
    DisposableEffect(key1 = compositeDisposable) {
        onDispose { compositeDisposable.dispose() }
    }

    SwipeRefresh(modifier = Modifier.padding(padding),state = state, onRefresh = {
            val scan = context.getScan(model.scanBuilder)
            state.isRefreshing = true
            val disp = scan.startScan()
                .onErrorComplete()
                .distinct { s -> s.bleDevice.macAddress }
                .doOnNext { r -> Log.v("debug", "r $r") }
                .doOnSubscribe { d ->
                    if (scanInProgress != null) {
                        scanInProgress?.dispose()
                        scanInProgress = null
                    }
                    scanInProgress = d
                    state.isRefreshing = false
                    model.currentScans.clear()
                }
                .doFinally {
                    scanInProgress = null
                }
                .doOnDispose {
                    scanInProgress = null
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { v ->
                    model.currentScans.add(v)
                }

            compositeDisposable.add(disp)
    }) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = lazyListState) {
            for (result in model.currentScans) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp, bottom = 5.dp)
                            .height(60.dp)
                    ) {
                        ScanResultView(scanResult = result)
                    }
                }
            }
        }
    }
}

@Composable
fun ScanResultView(scanResult: ScanResult) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = scanResult.bleDevice.name ?: "N/A",
                style = MaterialTheme.typography.labelLarge
            )
            Text(text = scanResult.bleDevice.macAddress)
        }
        Text(text = "${scanResult.rssi} dBm")
        Button(onClick = {
            Toast.makeText(context, "Don't get ahead of yourself, kiddo", Toast.LENGTH_LONG).show()
        }) {
            Text(text = "Connect")
        }
    }
}

@Composable
fun AlignedText(x: String) {
    Text(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .wrapContentHeight()
            .wrapContentWidth(),
        text = x,
        textAlign = TextAlign.Center
    )
}