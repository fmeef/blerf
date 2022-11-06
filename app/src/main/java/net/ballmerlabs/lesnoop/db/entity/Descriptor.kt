package net.ballmerlabs.lesnoop.db.entity

import android.bluetooth.BluetoothGattDescriptor
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID


@Entity(
    tableName = "descriptors",
    indices = [
        Index(value = ["parentCharacteristic", "uuid"], unique = true)
    ]
)
data class Descriptor(
    @PrimaryKey(autoGenerate = true)
    val uid: Long?,
    @ColumnInfo(name = "parentCharacteristic")
    var parentCharacteristic: Long? = null,
    @ColumnInfo(name = "uuid")
    val uuid: UUID
) {
    constructor(descriptor: BluetoothGattDescriptor): this (
                uid = null,
                parentCharacteristic = null,
                uuid = descriptor.uuid
    )
}