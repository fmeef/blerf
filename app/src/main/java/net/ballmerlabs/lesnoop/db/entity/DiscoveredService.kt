package net.ballmerlabs.lesnoop.db.entity

import android.bluetooth.BluetoothGattService
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "discovered_services")
data class DiscoveredService(
    @PrimaryKey
    val uid: UUID
) {
    constructor(bluetoothGattService: BluetoothGattService): this (
        uid = bluetoothGattService.uuid
    )
}