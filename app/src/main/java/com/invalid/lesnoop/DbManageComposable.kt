package com.invalid.lesnoop

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.reactivex.rxjava3.schedulers.Schedulers
import me.bytebeats.views.charts.pie.PieChart
import me.bytebeats.views.charts.pie.PieChartData


@Composable
fun EmptyTest(padding: PaddingValues, model: ScanViewModel) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ScanResultsCount(model = model)
            Button(onClick = {
                Toast.makeText(context, "Try again later you export weenie", Toast.LENGTH_LONG)
                    .show()
            }) {
                Text(text = "Export database")
            }
        }
        Legend(modifier = Modifier.fillMaxWidth(), model = model)
        OuiPieChart(model = model)
    }
}

@Composable
fun ScanResultsCount(model: ScanViewModel, modifier: Modifier = Modifier) {
    val count = model.scanResultDao.scanResultCount()
        .subscribeOn(Schedulers.io())
        .subscribeAsState(initial = 0)
    Text(modifier = modifier, text = "Currently indexed ${count.value} devices")
}

@Composable
fun Legend(model: ScanViewModel, modifier: Modifier = Modifier) {
    val data = model.legendState().subscribeAsState(initial = listOf())
    LazyColumn(modifier = modifier) {
        for (v in data.value) {
            item {
                Column {
                    Text(text = v.first)
                    Surface(modifier = Modifier.size(16.dp), color = v.second) {

                    }
                }
            }
        }
    }
}

@Composable
fun OuiPieChart(model: ScanViewModel, modifier: Modifier = Modifier) {
    val data = model.pieChartState().subscribeAsState(initial = listOf())
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        PieChart(
            modifier = Modifier
                .aspectRatio(1.0.toFloat())
                .fillMaxWidth(),
            pieChartData = PieChartData(data.value)
        )
    }
}