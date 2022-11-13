package net.ballmerlabs.lesnoop.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
    fun insertCharacteristic(characteristic: Characteristic): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDescriptors(descriptors: List<Descriptor>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDiscoveredService(service: DiscoveredService)

    @Insert
    fun insertMapping(mapping: ServiceScanResultMapping)



    @Transaction
    fun insertService(service: ServicesWithChildren, scanResult: Long? = null) {
        insertDiscoveredService(service.discoveredService)
        val chars = service.characteristics
        for (char in chars) {
            char.characteristic.parentService = service.discoveredService.uid
            val l = insertCharacteristic(char.characteristic)
            val descriptors = char.descriptors
            descriptors.forEach { d -> d.parentCharacteristic = l }
            insertDescriptors(descriptors)
            if (scanResult != null) {
                insertMapping(
                    ServiceScanResultMapping(
                        service = service.discoveredService.uid,
                        scanResult = scanResult
                    )
                )
            }
        }
    }
}