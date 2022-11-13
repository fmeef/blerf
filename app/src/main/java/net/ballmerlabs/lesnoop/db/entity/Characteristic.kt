package net.ballmerlabs.lesnoop.db.entity

import android.bluetooth.BluetoothGattCharacteristic
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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
    ],
    foreignKeys = [
        ForeignKey(
            childColumns = ["parentService"],
            parentColumns = ["uid"],
            entity = DiscoveredService::class,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Characteristic(
    @PrimaryKey(autoGenerate = true)
    val uid: Long? = null,
    @ColumnInfo(name = "parentService")
    var parentService: UUID? = null,
    @ColumnInfo(name = "uuid")
    val uuid: UUID
) {
    constructor(characteristic: BluetoothGattCharacteristic): this(
        parentService = null,
        uuid = characteristic.uuid
    )
}