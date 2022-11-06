package net.ballmerlabs.lesnoop.db.entity

import android.bluetooth.BluetoothGattCharacteristic
import androidx.room.Embedded
import androidx.room.Relation

data class CharacteristicWithDescriptors(
    @Embedded val characteristic: Characteristic,
    @Relation(
        parentColumn = "uid",
        entityColumn = "parentCharacteristic"
    )
    val descriptors: List<Descriptor>
) {
    constructor(characteristic: BluetoothGattCharacteristic): this(
        characteristic = Characteristic(characteristic),
        descriptors = characteristic.descriptors.map { d -> Descriptor(d) }
    )
}