package net.ballmerlabs.lesnoop.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.polidea.rxandroidble3.PhyPair
import net.ballmerlabs.lesnoop.db.entity.*
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

@Dao
interface ScanResultDao {

    @Transaction
    @Query("SELECT * FROM discovered_services")
    fun getServices(): Single<List<ServicesWithChildren>>

    @Query("SELECT COUNT(DISTINCT macAddress) FROM scan_results")
    fun scanResultCount(): Single<Int>

    @Transaction
    @Query("SELECT * FROM scan_results")
    fun getScanResults(): Single<List<DbScanResult>>

    @Insert
    fun insertScanResult(scanResult: DbScanResult): Single<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCharacteristic(characteristic: Characteristic): Single<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDescriptors(descriptors: List<Descriptor>): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDiscoveredService(service: DiscoveredService): Completable

    @Insert
    fun insertMapping(mapping: ServiceScanResultMapping): Completable


    @Update
    fun updateScanResult(scanResult: DbScanResult): Completable


    @Transaction
    @Insert
    fun insertService(service: ServicesWithChildren, scanResult: Long? = null, phy: PhyPair? = null): Completable {
        return insertDiscoveredService(service.discoveredService).andThen(Completable.defer {
            Observable.fromIterable(service.characteristics).flatMapCompletable { char ->
                char.characteristic.parentService = service.discoveredService.uid
                insertCharacteristic(char.characteristic).flatMapCompletable { l ->
                    val descriptors = char.descriptors
                    descriptors.forEach { d -> d.parentCharacteristic = l }
                    insertDescriptors(descriptors).andThen(
                        if (scanResult != null) {
                            insertMapping(
                                ServiceScanResultMapping(
                                    service = service.discoveredService.uid,
                                    scanResult = scanResult
                                )
                            )
                        } else {
                            Completable.complete()
                        }
                    )
                }
            }
        })
    }
}