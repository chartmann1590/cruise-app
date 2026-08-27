package com.cruiseapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cruises")
data class Cruise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipName: String,
    val startDate: Long, // epoch millis UTC midnight
    val endDate: Long,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "port_stops")
data class PortStop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cruiseId: Long,
    val name: String,
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
    val arrivalDate: Long,
    val departureDate: Long,
    val orderIndex: Int = 0
)

@Entity(tableName = "planned_events")
data class PlannedEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cruiseId: Long,
    val portStopId: Long? = null,
    val title: String,
    val description: String = "",
    val dateMillis: Long, // which day (midnight millis)
    val startTimeMillis: Long, // full timestamp
    val endTimeMillis: Long? = null,
    val location: String = "",
    val category: String = "General",
    val reminderMinutesBefore: Int = 15
)

@Entity(tableName = "party_members")
data class PartyMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val endpointId: String = "",
    val isSelf: Boolean = false,
    val colorHex: String = "#FF6200EE",
    val code: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientMessageId: String = java.util.UUID.randomUUID().toString(),
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromSelf: Boolean = false,
    val endpointId: String = "",
    val status: String = "PENDING", // PENDING, SENT, DELIVERED, READ, FAILED
    val retryCount: Int = 0,
    val targetCode: String? = null, // null = broadcast to everyone, else specific member code
    val targetName: String? = null
)

@Entity(tableName = "weather_cache")
data class WeatherCache(
    @PrimaryKey val portStopId: Long,
    val fetchedAt: Long,
    val json: String,
    val tempMax: Double? = null,
    val tempMin: Double? = null,
    val weatherCode: Int? = null,
    val summary: String = ""
)
