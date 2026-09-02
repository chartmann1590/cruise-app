package com.charles.cruiseapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Cruise::class, PortStop::class, PlannedEvent::class, PartyMember::class, Message::class, WeatherCache::class, PlaceCache::class],
    version = 4,
    exportSchema = false
)
abstract class CruiseDatabase : RoomDatabase() {
    abstract fun cruiseDao(): CruiseDao
    abstract fun portStopDao(): PortStopDao
    abstract fun plannedEventDao(): PlannedEventDao
    abstract fun partyMemberDao(): PartyMemberDao
    abstract fun messageDao(): MessageDao
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun placeCacheDao(): PlaceCacheDao

    companion object {
        @Volatile private var INSTANCE: CruiseDatabase? = null
        fun getDatabase(context: Context): CruiseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CruiseDatabase::class.java,
                    "cruise_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
