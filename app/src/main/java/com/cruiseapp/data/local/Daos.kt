package com.cruiseapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CruiseDao {
    @Query("SELECT * FROM cruises ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Cruise>>
    @Query("SELECT * FROM cruises ORDER BY createdAt DESC LIMIT 1")
    fun getLatest(): Flow<Cruise?>
    @Query("SELECT * FROM cruises WHERE id = :id")
    suspend fun getById(id: Long): Cruise?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cruise: Cruise): Long
    @Update
    suspend fun update(cruise: Cruise)
    @Delete
    suspend fun delete(cruise: Cruise)
    @Query("DELETE FROM cruises")
    suspend fun clearAll()
}

@Dao
interface PortStopDao {
    @Query("SELECT * FROM port_stops WHERE cruiseId = :cruiseId ORDER BY arrivalDate ASC, orderIndex ASC")
    fun getForCruise(cruiseId: Long): Flow<List<PortStop>>
    @Query("SELECT * FROM port_stops WHERE cruiseId = :cruiseId ORDER BY arrivalDate ASC")
    suspend fun getForCruiseOnce(cruiseId: Long): List<PortStop>
    @Query("SELECT * FROM port_stops WHERE id = :id")
    suspend fun getById(id: Long): PortStop?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(port: PortStop): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ports: List<PortStop>)
    @Update
    suspend fun update(port: PortStop)
    @Delete
    suspend fun delete(port: PortStop)
    @Query("DELETE FROM port_stops WHERE cruiseId = :cruiseId")
    suspend fun deleteForCruise(cruiseId: Long)
}

@Dao
interface PlannedEventDao {
    @Query("SELECT * FROM planned_events WHERE cruiseId = :cruiseId ORDER BY startTimeMillis ASC")
    fun getForCruise(cruiseId: Long): Flow<List<PlannedEvent>>
    @Query("SELECT * FROM planned_events WHERE cruiseId = :cruiseId AND dateMillis = :dateMillis ORDER BY startTimeMillis ASC")
    fun getForDate(cruiseId: Long, dateMillis: Long): Flow<List<PlannedEvent>>
    @Query("SELECT * FROM planned_events WHERE dateMillis = :date ORDER BY startTimeMillis ASC")
    suspend fun getForDateOnce(date: Long): List<PlannedEvent>
    @Query("SELECT * FROM planned_events WHERE startTimeMillis >= :now ORDER BY startTimeMillis ASC LIMIT 5")
    fun getUpcoming(now: Long = System.currentTimeMillis()): Flow<List<PlannedEvent>>
    @Query("SELECT * FROM planned_events WHERE id = :id")
    suspend fun getById(id: Long): PlannedEvent?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PlannedEvent): Long
    @Update
    suspend fun update(event: PlannedEvent)
    @Delete
    suspend fun delete(event: PlannedEvent)
    @Query("SELECT * FROM planned_events WHERE startTimeMillis > :now ORDER BY startTimeMillis ASC")
    suspend fun getFutureEvents(now: Long = System.currentTimeMillis()): List<PlannedEvent>
}

@Dao
interface PartyMemberDao {
    @Query("SELECT * FROM party_members ORDER BY isSelf DESC, displayName ASC")
    fun getAll(): Flow<List<PartyMember>>
    @Query("SELECT * FROM party_members")
    suspend fun getAllOnce(): List<PartyMember>
    @Query("SELECT * FROM party_members WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): PartyMember?
    @Query("SELECT * FROM party_members WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): PartyMember?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: PartyMember): Long
    @Delete
    suspend fun delete(member: PartyMember)
    @Query("DELETE FROM party_members WHERE isSelf = 0")
    suspend fun clearNonSelf()
    @Query("UPDATE party_members SET endpointId = :endpointId WHERE code = :code")
    suspend fun updateEndpoint(code: String, endpointId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<Message>>
    @Query("SELECT * FROM messages WHERE status = :status ORDER BY timestamp ASC")
    fun getByStatus(status: String): Flow<List<Message>>
    @Query("SELECT * FROM messages WHERE status IN ('PENDING','SENT') ORDER BY timestamp ASC")
    suspend fun getPending(): List<Message>
    @Query("SELECT * FROM messages WHERE clientMessageId = :id LIMIT 1")
    suspend fun getByClientId(id: String): Message?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long
    @Update
    suspend fun update(message: Message)
    @Query("UPDATE messages SET status = :status WHERE clientMessageId = :id")
    suspend fun updateStatus(id: String, status: String)
    @Query("UPDATE messages SET status = :status, retryCount = retryCount + 1 WHERE clientMessageId = :id")
    suspend fun incrementRetry(id: String, status: String)
    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE portStopId = :portId")
    suspend fun getForPort(portId: Long): WeatherCache?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: WeatherCache)
}
