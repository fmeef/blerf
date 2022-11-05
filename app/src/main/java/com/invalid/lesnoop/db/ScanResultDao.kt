package com.invalid.lesnoop.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.invalid.lesnoop.db.entity.*
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
    fun insertScanResult(scanResult: DbScanResult): Completable

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertCharacteristic(characteristic: Characteristic): Single<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDescriptors(descriptors: List<Descriptor>): Completable

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDiscoveredService(service: DiscoveredService): Single<Long>

    fun insertCharacteristic(characteristic: CharacteristicWithDescriptors): Completable {
        return Single.just(characteristic).flatMap { c ->
            insertCharacteristic(c.characteristic)
        }.map { l ->
            val descriptors = characteristic.descriptors
            descriptors.forEach { d -> d.parentCharacteristic = l }
            descriptors
        }.flatMapCompletable { d -> insertDescriptors(d) }
    }

    fun insertService(service: ServicesWithChildren): Completable {
        return Single.just(service)
            .flatMap { s -> insertDiscoveredService(s.discoveredService) }
            .flatMapObservable { l ->
                val chars = service.characteristics
                chars.forEach { c ->
                    c.characteristic.parentService = l
                }
                Observable.fromIterable(chars)
            }
            .flatMapCompletable { c -> insertCharacteristic(c) }

    }
}