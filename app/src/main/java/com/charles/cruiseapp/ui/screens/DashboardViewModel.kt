package com.charles.cruiseapp.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.data.local.Cruise
import com.charles.cruiseapp.data.local.PlannedEvent
import com.charles.cruiseapp.data.local.PortStop
import com.charles.cruiseapp.notifications.NotificationHelper
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import com.charles.cruiseapp.util.addDays
import com.charles.cruiseapp.util.startOfDay
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
            val trace = FirebasePerfUtils.startTrace("create_cruise")
            try {
                FirebaseCrashlyticsUtils.log("Creating cruise $shipName $start -> $end")
                val s = startOfDay(start); val e = startOfDay(end)
                val id = db.cruiseDao().insert(Cruise(shipName=shipName, startDate=s, endDate=e))
                // Schedule countdown if future
                if (s > startOfDay(System.currentTimeMillis())) {
                    NotificationHelper.scheduleDailyCountdown(context, id, shipName, s)
                } else {
                    NotificationHelper.cancelDailyCountdown(context)
                }
                trace?.putMetric("success", 1)
                FirebaseCrashlyticsUtils.setCustomKey("last_cruise_name", shipName.take(50))
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                trace?.putMetric("error", 1)
                throw e
            } finally {
                try { trace?.stop() } catch (_: Exception) {}
            }
        }
    }

    fun rescheduleCountdownForCurrentCruise() {
        viewModelScope.launch {
            val c = cruise.value ?: return@launch
            if (c.startDate > startOfDay(System.currentTimeMillis())) {
                NotificationHelper.scheduleDailyCountdown(context, c.id, c.shipName, c.startDate)
            } else {
                NotificationHelper.cancelDailyCountdown(context)
            }
        }
    }

    fun addPort(name: String, lat: Double, lon: Double, arrival: Long, departure: Long, country:String=""){
        viewModelScope.launch {
            val trace = FirebasePerfUtils.startTrace("add_port")
            try {
                FirebaseCrashlyticsUtils.log("Adding port $name $lat,$lon")
                val c = cruise.value ?: return@launch
                val list = db.portStopDao().getForCruiseOnce(c.id)
                db.portStopDao().insert(PortStop(cruiseId=c.id, name=name, latitude=lat, longitude=lon, arrivalDate=arrival, departureDate=departure, country=country, orderIndex=list.size))
                trace?.putMetric("success", 1)
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                trace?.putMetric("error", 1)
                throw e
            } finally { try { trace?.stop() } catch (_: Exception) {} }
        }
    }

    fun deletePort(port: PortStop){
        viewModelScope.launch { db.portStopDao().delete(port) }
    }

    fun addEvent(title: String, dateMillis: Long, hour: Int, minute: Int, location:String, category:String, reminder: Int, description:String=""){
        viewModelScope.launch {
            val trace = FirebasePerfUtils.startTrace("add_planned_event")
            trace?.putAttribute("category", category)
            try {
                FirebaseCrashlyticsUtils.log("Adding event $title on $dateMillis $hour:$minute")
                val c = cruise.value ?: return@launch
                val cal = Calendar.getInstance().apply { timeInMillis = dateMillis; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
                val start = cal.timeInMillis
                val ev = PlannedEvent(cruiseId=c.id, title=title, dateMillis=startOfDay(dateMillis), startTimeMillis=start, location=location, category=category, reminderMinutesBefore=reminder, description=description)
                val id = db.plannedEventDao().insert(ev)
                val saved = ev.copy(id=id)
                NotificationHelper.scheduleEventNotification(context, saved)
                trace?.putMetric("success", 1)
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                trace?.putMetric("error", 1)
                throw e
            } finally { try { trace?.stop() } catch (_: Exception) {} }
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
