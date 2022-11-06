package net.ballmerlabs.lesnoop

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bytebeats.views.charts.pie.PieChart
import me.bytebeats.views.charts.pie.PieChartData


@Composable
fun EmptyTest(padding: PaddingValues, model: ScanViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-sqlite3")
    ){ uri: Uri? ->
        scope.launch(Dispatchers.IO) {
            val f = model.dbPath.inputStream()
            if (uri != null) {
                val out = context.contentResolver.openOutputStream(uri)
                if (out != null) {
                    Log.e("debug", "writing $uri")
                    f.copyTo(out)
                    out.close()
                }
            } else {
               withContext(Dispatchers.Main)  {
                   Toast.makeText(context, context.getText(R.string.invalid_file_path), Toast.LENGTH_LONG).show()
               }
            }
        }
    }
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ScanResultsCount(model = model)
            Button(onClick = {
                launcher.launch("output.sqlite")
            }) {
                Text(text = stringResource(id = R.string.export))
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
    Text(modifier = modifier, text = stringResource(id = R.string.indexed, count.value))
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