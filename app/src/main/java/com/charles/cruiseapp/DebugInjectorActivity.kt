package com.charles.cruiseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.charles.cruiseapp.data.local.Cruise
import com.charles.cruiseapp.data.local.PortStop
import com.charles.cruiseapp.util.startOfDay
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class DebugInjectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CruiseApplication
        val ship = intent.getStringExtra("shipName") ?: "Demo Cruise"
        val addPort = intent.getBooleanExtra("addPort", false)
        val portName = intent.getStringExtra("portName") ?: "Nassau"
        val lat = intent.getDoubleExtra("lat", 25.06)
        val lon = intent.getDoubleExtra("lon", -77.34)
        // Use runBlocking to ensure DB operations complete before finish (avoid lifecycleScope cancellation)
        runBlocking {
            val db = app.database
            // For demo screenshots, clear previous and create fresh
            if (intent.getBooleanExtra("clearFirst", true)) {
                try { db.cruiseDao().clearAll() } catch (_: Exception) {}
                try { db.portStopDao().deleteForCruise(9999) } catch (_: Exception) {}
                // clear all cruises' ports via deleting cruises will cascade? just clear cruises
                // Also clear events
                try {
                    val all = db.cruiseDao().getById(1)
                    // we will just clear via direct query would need, but for now rely on clearAll
                } catch (_: Exception) {}
            }
            val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
            val end = start + 6*24*60*60*1000L
            val cruiseId = db.cruiseDao().insert(Cruise(shipName = ship, startDate = startOfDay(start), endDate = startOfDay(end)))
            if (addPort) {
                val arrival = startOfDay(start) + 1*24*60*60*1000L
                db.portStopDao().insert(PortStop(cruiseId = cruiseId, name = portName, latitude = lat, longitude = lon, arrivalDate = arrival, departureDate = arrival, country = "Bahamas", orderIndex = 0))
                val arrival2 = startOfDay(start) + 3*24*60*60*1000L
                db.portStopDao().insert(PortStop(cruiseId = cruiseId, name = "Cozumel", latitude = 20.42, longitude = -86.92, arrivalDate = arrival2, departureDate = arrival2, country = "Mexico", orderIndex = 1))
            }
            if (intent.getBooleanExtra("addMessage", false)) {
                val date = startOfDay(start)
                val cal = Calendar.getInstance().apply { timeInMillis = date; set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                val st = cal.timeInMillis
                db.plannedEventDao().insert(com.charles.cruiseapp.data.local.PlannedEvent(cruiseId = cruiseId, title = "Welcome Dinner", dateMillis = date, startTimeMillis = st, location = "Main Dining", category = "Dining", reminderMinutesBefore = 15))
                // also add a second event for variety
                val cal2 = Calendar.getInstance().apply { timeInMillis = date+1*24*60*60*1000L; set(Calendar.HOUR_OF_DAY, 14); set(Calendar.MINUTE, 30) }
                db.plannedEventDao().insert(com.charles.cruiseapp.data.local.PlannedEvent(cruiseId = cruiseId, title = "Shore Excursion", dateMillis = startOfDay(date+1*24*60*60*1000L), startTimeMillis = cal2.timeInMillis, location = portName, category = "Excursion", reminderMinutesBefore = 30))
            }
        }
        finish()
    }
}
