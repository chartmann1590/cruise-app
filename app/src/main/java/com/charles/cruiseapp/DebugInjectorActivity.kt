package com.charles.cruiseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.charles.cruiseapp.data.local.Cruise
import com.charles.cruiseapp.data.local.PortStop
import com.charles.cruiseapp.util.startOfDay
import kotlinx.coroutines.launch
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
        lifecycleScope.launch {
            val db = app.database
            // check if cruise exists, if not create
            val existing = db.cruiseDao().getById(1)
            var cruiseId = existing?.id
            if (cruiseId == null) {
                val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis
                val end = start + 6*24*60*60*1000L
                val id = db.cruiseDao().insert(Cruise(shipName = ship, startDate = startOfDay(start), endDate = startOfDay(end)))
                cruiseId = id
            }
            if (addPort && cruiseId != null) {
                val cruise = db.cruiseDao().getById(cruiseId)
                if (cruise != null) {
                    val arrival = cruise.startDate + 1*24*60*60*1000L
                    db.portStopDao().insert(PortStop(cruiseId = cruise.id, name = portName, latitude = lat, longitude = lon, arrivalDate = arrival, departureDate = arrival, country = "Bahamas", orderIndex = 0))
                    // also add a second port for demo
                    val arrival2 = cruise.startDate + 3*24*60*60*1000L
                    db.portStopDao().insert(PortStop(cruiseId = cruise.id, name = "Cozumel", latitude = 20.42, longitude = -86.92, arrivalDate = arrival2, departureDate = arrival2, country = "Mexico", orderIndex = 1))
                }
            }
            // also ensure party self exists if needed handled by PartyViewModel, but we can also add a sample message if requested
            if (intent.getBooleanExtra("addMessage", false)) {
                val cruise = cruiseId?.let { db.cruiseDao().getById(it) }
                if (cruise != null) {
                    val date = cruise.startDate
                    val cal = Calendar.getInstance().apply { timeInMillis = date; set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0) }
                    val start = cal.timeInMillis
                    db.plannedEventDao().insert(com.charles.cruiseapp.data.local.PlannedEvent(cruiseId = cruise.id, title = "Welcome Dinner", dateMillis = startOfDay(date), startTimeMillis = start, location = "Main Dining", category = "Dining", reminderMinutesBefore = 15))
                }
            }
            finish()
        }
    }
}
