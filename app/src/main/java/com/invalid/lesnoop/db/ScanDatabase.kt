package com.invalid.lesnoop.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.invalid.lesnoop.db.entity.*

@Database(
    entities = [
        DbScanResult::class,
        Characteristic::class,
        DiscoveredService::class,
        Descriptor::class,
        DbLocation::class
               ],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
abstract class ScanDatabase: RoomDatabase() {
    abstract fun scanResultsDao(): ScanResultDao
}