package com.invalid.lesnoop.db.entity

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "characteristics",
    indices = [
        Index(
            value = ["uuid", "parentService"],
            unique = true
        )
    ]
)
data class Characteristic(
    @PrimaryKey(autoGenerate = true)
    val uid: Long? = null,
    @ColumnInfo(name = "parentService")
    var parentService: Long? = null,
    @ColumnInfo(name = "uuid")
    val uuid: UUID
) {
    constructor(characteristic: BluetoothGattCharacteristic): this(
        parentService = null,
        uuid = characteristic.uuid
    )
}