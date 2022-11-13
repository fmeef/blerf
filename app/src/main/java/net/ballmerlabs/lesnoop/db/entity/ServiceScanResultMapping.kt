package net.ballmerlabs.lesnoop.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "scan_service_mapping")
data class ServiceScanResultMapping(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    val service: UUID,
    val scanResult: Long
)