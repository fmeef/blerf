package com.invalid.lesnoop.db.entity

import android.bluetooth.BluetoothGattService
import androidx.room.Embedded
import androidx.room.Relation

data class ServicesWithChildren(
    @Embedded
    val discoveredService: DiscoveredService,

    @Relation(
        parentColumn = "uid",
        entityColumn = "parentService",
        entity = Characteristic::class
    )
    val characteristics: List<CharacteristicWithDescriptors>
) {
    constructor(service: BluetoothGattService): this(
        discoveredService = DiscoveredService(service),
        characteristics = service.characteristics.map { c -> CharacteristicWithDescriptors(c) }
    )
}