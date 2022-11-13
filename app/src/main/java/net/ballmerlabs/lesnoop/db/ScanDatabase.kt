package net.ballmerlabs.lesnoop.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import net.ballmerlabs.lesnoop.db.entity.*
import java.util.*

class UuidTypeConverter {
    @TypeConverter
    fun uuidToString(uuid: UUID): String {
        return uuid.toString()
    }

    @TypeConverter
    fun stringToUUID(string: String): UUID {
        return UUID.fromString(string)
    }
}

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