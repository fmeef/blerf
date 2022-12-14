package net.ballmerlabs.lesnoop

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.runtime.*
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.rxjava3.rxPreferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import net.ballmerlabs.lesnoop.db.OuiParser
import net.ballmerlabs.lesnoop.ui.theme.BlerfTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

const val NAV_SCAN = "scan"
const val NAV_DB = "database"
const val NAV_DIALOG = "dialog"
const val PREF_NAME = "scanprefs"

val Context.rxPrefs by rxPreferencesDataStore(PREF_NAME)

val PREF_BACKGROUND_SCAN = booleanPreferencesKey("background_scan")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var service: ScanSnoopService
    private var bound = false

    @Inject
    lateinit var ouiParser: OuiParser

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, bindService: IBinder) {
            val binder = bindService as ScanSnoopService.SnoopBinder
            service = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(p0: ComponentName) {
            bound = false
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlerfTheme {
                Body {
                    service
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, ScanSnoopService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        bound = false
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    val model = hiltViewModel<ScanViewModel>()
    TopAppBar(
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(model.topText.value, style = MaterialTheme.typography.headlineLarge)
                val d = model.scanInProgress.value
                if (d != null) {
                    Button(
                        onClick = {
                            model.scanInProgress.value = null
                            d.dispose()
                        }
                    ) {
                        Text(text = stringResource(id = R.string.stop_scan))
                    }
                }
            }
        },
        scrollBehavior = pinnedScrollBehavior()
    )
}


@Composable
@ExperimentalPermissionsApi
fun ScopePermissions(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val permissions = mutableListOf(
        rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        for (x in listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )) {
            val p = rememberPermissionState(permission = x)
            permissions.add(p)
        }
    }

    val granted = permissions.all { s ->
        s.status == com.google.accompanist.permissions.PermissionStatus.Granted
    }
    if (granted) {
        Box(modifier = modifier) {
            content()
        }
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            val p =
                permissions.first { p -> p.status != com.google.accompanist.permissions.PermissionStatus.Granted }
            Button(
                onClick = { p.launchPermissionRequest() }
            ) {
                Text(text = stringResource(id = R.string.not_granted, p.permission))
            }
        }
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
@Composable
@ExperimentalPermissionsApi
fun ScanDialog(s: () -> ScanSnoopService) {

    val service by remember { derivedStateOf(s) }
    val context = LocalContext.current

    val p = context.rxPrefs.data().map { p -> p[PREF_BACKGROUND_SCAN]?: false }.subscribeAsState(initial = false)

    ScopePermissions {
        Surface(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = stringResource(id = R.string.background_scan),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(text = stringResource(id = R.string.scan_disclaimer))
                Row {
                    Button(
                        onClick = { service.startScanToDb() },
                        enabled = !p.value
                    ) {
                        Text(text = stringResource(id = R.string.start_scan))
                    }
                    Button(
                        onClick = { service.stopScan() },
                        enabled = p.value
                    ) {
                        Text(text = stringResource(id = R.string.stop_scan))
                    }
                }
            }

        }
    }
}

@Composable
fun BottomBar(navController: NavController) {
    BottomAppBar {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.Start) {
                Button(
                    modifier = Modifier.padding(start = 5.dp, end = 5.dp),
                    onClick = { navController.navigate(NAV_SCAN) }
                ) {
                    Text(text = stringResource(id = R.string.scan))
                }
                Button(
                    modifier = Modifier.padding(start = 5.dp, end = 5.dp),
                    onClick = { navController.navigate(NAV_DB) }
                ) {
                    Text(text = stringResource(id = R.string.database))
                }

            }
            Button(
                onClick = { navController.navigate(NAV_DIALOG) }
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_baseline_perm_scan_wifi_24),
                    stringResource(id = R.string.toggle)
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
@ExperimentalPermissionsApi
fun Body(service: () -> ScanSnoopService) {
    val navController = rememberNavController()
    val model = hiltViewModel<ScanViewModel>()
    Scaffold(
        content = { padding ->
            NavHost(navController = navController, startDestination = NAV_SCAN) {
                composable(NAV_SCAN) {
                    model.topText.value = stringResource(id = R.string.nearby)
                    ScopePermissions(modifier = Modifier.fillMaxSize()) {
                        DeviceList(padding, model)
                    }
                }
                composable(NAV_DB) {
                    model.topText.value = stringResource(id = R.string.database)
                    EmptyTest(padding, model)
                }
                dialog(NAV_DIALOG) { ScanDialog(service) }
            }
        },
        bottomBar = { BottomBar(navController) },
        topBar = { TopBar() }
    )
}

