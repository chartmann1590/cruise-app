package com.cruiseapp.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cruiseapp.CruiseApplication
import com.cruiseapp.data.local.Cruise
import com.cruiseapp.data.local.PlannedEvent
import com.cruiseapp.data.local.PortStop
import com.cruiseapp.notifications.NotificationHelper
import com.cruiseapp.util.addDays
import com.cruiseapp.util.startOfDay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardViewModel(app: Application): AndroidViewModel(app){
    private val db = (app as CruiseApplication).database
    private val context: Context = app.applicationContext

    val cruise: StateFlow<Cruise?> = db.cruiseDao().getLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ports: StateFlow<List<PortStop>> = cruise.flatMapLatest { c ->
        if(c==null) flowOf(emptyList()) else db.portStopDao().getForCruise(c.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<PlannedEvent>> = cruise.flatMapLatest { c ->
        if(c==null) flowOf(emptyList()) else db.plannedEventDao().getForCruise(c.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcoming: StateFlow<List<PlannedEvent>> = db.plannedEventDao().getUpcoming()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCruise(shipName: String, start: Long, end: Long){
        viewModelScope.launch {
            db.cruiseDao().insert(Cruise(shipName=shipName, startDate=startOfDay(start), endDate=startOfDay(end)))
        }
    }

    fun addPort(name: String, lat: Double, lon: Double, arrival: Long, departure: Long, country:String=""){
        viewModelScope.launch {
            val c = cruise.value ?: return@launch
            val list = db.portStopDao().getForCruiseOnce(c.id)
            db.portStopDao().insert(PortStop(cruiseId=c.id, name=name, latitude=lat, longitude=lon, arrivalDate=arrival, departureDate=departure, country=country, orderIndex=list.size))
        }
    }

    fun deletePort(port: PortStop){
        viewModelScope.launch { db.portStopDao().delete(port) }
    }

    fun addEvent(title: String, dateMillis: Long, hour: Int, minute: Int, location:String, category:String, reminder: Int, description:String=""){
        viewModelScope.launch {
            val c = cruise.value ?: return@launch
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
            val start = cal.timeInMillis
            val ev = PlannedEvent(cruiseId=c.id, title=title, dateMillis=startOfDay(dateMillis), startTimeMillis=start, location=location, category=category, reminderMinutesBefore=reminder, description=description)
            val id = db.plannedEventDao().insert(ev)
            val saved = ev.copy(id=id)
            NotificationHelper.scheduleEventNotification(context, saved)
        }
    }

    fun deleteEvent(event: PlannedEvent){
        viewModelScope.launch {
            db.plannedEventDao().delete(event)
            NotificationHelper.cancelNotification(context, event.id)
        }
    }

    fun generateDays(): List<Long> {
        val c = cruise.value ?: return emptyList()
        val days = mutableListOf<Long>()
        var cur = c.startDate
        while(cur <= c.endDate){
            days.add(cur)
            cur = addDays(cur, 1)
        }
        return days
    }

    fun eventsForDate(date: Long): Flow<List<PlannedEvent>> {
        val c = cruise.value ?: return flowOf(emptyList())
        return db.plannedEventDao().getForDate(c.id, startOfDay(date))
    }
}
