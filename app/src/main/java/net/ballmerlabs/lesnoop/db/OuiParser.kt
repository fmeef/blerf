package net.ballmerlabs.lesnoop.db

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import net.ballmerlabs.lesnoop.R
import com.polidea.rxandroidble3.RxBleDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

@Singleton
class OuiParser @Inject constructor(
    @ApplicationContext val context: Context
)  {
    val oui: Single<HashMap<String, String>>  = readOuiFile()
    private val macaddr = "[0-9A-F]{2}-[0-9A-F]{2}-[0-9A-F]{2}".toRegex()

    private fun readOuiFile(): Single<HashMap<String, String>> {
        return  Single.just(context.resources.openRawResource(R.raw.oui))
            .flatMapObservable { r -> Observable.fromStream(r.bufferedReader().lines()) }
            .flatMapMaybe { l -> parseLine(l) }
            .reduce(HashMap<String, String>()) { map, v ->
                map[v.first] = v.second
                map
            }
            .cache()
    }

    private fun parseLine(line: String): Maybe<Pair<String, String>> {
        return Maybe.defer {
            val mac = macaddr.find(line)
            if (line.contains("(hex)") && mac != null) {
                val hex = "(hex)\t\t"
                val name = line.slice(line.indexOf(hex) + hex.length until line.length)
                val newmac = mac.value.replace("-", ":")
                Maybe.just(Pair(newmac, name))
            } else {
                Maybe.empty()
            }
        }
    }

    private fun ouiFromMac(mac: String): String {
        return mac.slice(0 until   8).uppercase()
    }


    fun ouiForDevice(device: RxBleDevice): Single<String> {
        return oui.map { map -> map[ouiFromMac(device.macAddress)]?:"N/A" }
    }
    fun ouiForDevice(device: String): Single<String> {
        return oui.map { map -> map[ouiFromMac(device)]?:"N/A" }
    }

}