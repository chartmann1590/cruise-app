package com.charles.cruiseapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.charles.cruiseapp.data.local.CruiseDatabase
import com.charles.cruiseapp.data.remote.WeatherRepository
import com.charles.cruiseapp.notifications.NotificationHelper

class CruiseApplication : Application() {
    val database by lazy { CruiseDatabase.getDatabase(this) }
    val weatherRepository by lazy { WeatherRepository() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationHelper.CHANNEL_ID,
                "Cruise Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for planned cruise events"
                enableVibration(true)
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)

            val partyChannel = NotificationChannel(
                NotificationHelper.PARTY_CHANNEL_ID,
                "Party Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Messages from cruise party"
                enableVibration(true)
            }
            mgr.createNotificationChannel(partyChannel)
        }
    }
}
