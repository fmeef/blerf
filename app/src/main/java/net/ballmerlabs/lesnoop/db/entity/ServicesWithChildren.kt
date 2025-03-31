package net.ballmerlabs.lesnoop.db.entity

import android.bluetooth.BluetoothGattService
import androidx.room.Embedded
import androidx.room.Relation
import com.polidea.rxandroidble3.PhyPair

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
    constructor(service: BluetoothGattService, phy: PhyPair? = null): this(
        discoveredService = DiscoveredService(service, phy = phy),
        characteristics = service.characteristics.map { c -> CharacteristicWithDescriptors(c) }
    )
}