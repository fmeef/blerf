package net.ballmerlabs.lesnoop.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import com.polidea.rxandroidble3.scan.ScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.db.entity.DbScanResult
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

fun Context.checkAirplaneMode(): Boolean {
    return Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
}

@Singleton
class LocationTagger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationService: LocationManager,
    @Named(Module.EXECUTOR_COMPUTE)
    private val executor: Executor){
    fun tagLocation(scanResult: ScanResult): Maybe<DbScanResult> {
        return Single.just(scanResult).flatMapMaybe { r ->
            val criteria = Criteria().apply {
                accuracy = Criteria.ACCURACY_FINE
                isCostAllowed = false
            }
            val provider = if(context.checkAirplaneMode())
                    LocationManager.GPS_PROVIDER
                else
                    locationService.getBestProvider(criteria, true)

            if (provider != null && locationService.isProviderEnabled(provider)) {
                Maybe.create { m ->
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            locationService.getCurrentLocation(provider, null, executor) { l ->
                                m.onSuccess(DbScanResult(r, location = l))
                            }
                        } else {
                            locationService.requestSingleUpdate(provider, { l ->
                                m.onSuccess(DbScanResult(r, location = l))
                            }, null)
                        }
                    } else {
                        m.onError(SecurityException("missing location permission"))
                    }
                }
            } else {
                Maybe.empty<DbScanResult>()
            }

        }.doOnSubscribe { Log.v(ScannerImpl.NAME, "getting location for ${scanResult.bleDevice.macAddress}") }
            .doOnError { e -> Log.e(ScannerImpl.NAME, "failed to get location for ${scanResult.bleDevice.macAddress}: $e") }
    }
}