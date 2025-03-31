package net.ballmerlabs.lesnoop.db.entity

import android.bluetooth.BluetoothGattService
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.polidea.rxandroidble3.PhyPair
import java.util.UUID

@Entity(tableName = "discovered_services")
data class DiscoveredService(
    @PrimaryKey
    val uid: UUID,
    @ColumnInfo(defaultValue = "null")
    val rxPhy: Int?,
    @ColumnInfo(defaultValue = "null")
    val txPhy: Int?,
    @ColumnInfo(defaultValue = "null")
    val rxPhyMask: Int?,
    @ColumnInfo(defaultValue = "null")
    val txPhyMask: Int?,
) {
    constructor(bluetoothGattService: BluetoothGattService, phy: PhyPair? = null): this (
        uid = bluetoothGattService.uuid,
        rxPhy = phy?.rxPhy?.value,
        txPhy = phy?.txPhy?.value,
        rxPhyMask = phy?.rxPhy?.mask,
        txPhyMask = phy?.txPhy?.mask,
    )
}