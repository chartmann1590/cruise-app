package com.charles.cruiseapp.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.charles.cruiseapp.MainActivity
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.util.formatDate
import com.charles.cruiseapp.util.startOfDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CountdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shipName = intent.getStringExtra("shipName") ?: "Your cruise"
                val startDate = intent.getLongExtra("startDate", 0L)
                val cruiseId = intent.getLongExtra("cruiseId", -1L)

                if (startDate == 0L) { pending.finish(); return@launch }

                val today = startOfDay(System.currentTimeMillis())
                val startDay = startOfDay(startDate)
                val daysUntil = ((startDay - today) / (24 * 60 * 60 * 1000L)).toInt()

                // If cruise started or passed, cancel future and show bon voyage once
                if (daysUntil <= 0) {
                    if (daysUntil == 0) {
                        showNotification(context, "🚢 Bon voyage!", "$shipName sets sail today — have an amazing cruise! \uD83C\uDF0A", cruiseId)
                    }
                    NotificationHelper.cancelDailyCountdown(context)
                    pending.finish()
                    return@launch
                }

                // Try to enrich with weather for departure port if available (respects unit setting)
                var weatherSnippet: String? = null
                try {
                    val app = context.applicationContext as? CruiseApplication
                    val db = app?.database
                    val isMetric = com.charles.cruiseapp.util.UnitUtils.isMetric(context)
                    if (db != null && cruiseId != -1L) {
                        val ports = db.portStopDao().getForCruiseOnce(cruiseId)
                        val firstPort = ports.minByOrNull { it.arrivalDate }
                        if (firstPort != null) {
                            val cache = db.weatherCacheDao().getForPort(firstPort.id)
                            if (cache != null) {
                                if (cache.tempMax != null && cache.tempMin != null) {
                                    weatherSnippet = "${com.charles.cruiseapp.util.UnitUtils.formatTemp(cache.tempMin, isMetric)}–${com.charles.cruiseapp.util.UnitUtils.formatTemp(cache.tempMax, isMetric)} at ${firstPort.name}"
                                } else if (cache.summary.isNotEmpty()) {
                                    weatherSnippet = cache.summary
                                }
                            }
                            // Fallback to live fetch if no cache (respect units via conversion)
                            if (weatherSnippet == null) {
                                try {
                                    val repo = app?.weatherRepository
                                    val res = repo?.getForecast(firstPort.latitude, firstPort.longitude, 7)?.getOrNull()
                                    val cur = res?.current
                                    if (cur?.temperature2m != null) {
                                        weatherSnippet = "${com.charles.cruiseapp.util.UnitUtils.formatTemp(cur.temperature2m, isMetric)} now at ${firstPort.name}"
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}

                val title = when (daysUntil) {
                    1 -> "\uD83D\uDEA2 1 day until $shipName!"
                    else -> "\uD83D\uDEA2 $daysUntil days until $shipName"
                }
                val dateStr = formatDate(startDate, "EEE, MMM d, yyyy")
                var text = "Sets sail $dateStr"
                if (weatherSnippet != null) text += " • $weatherSnippet"
                else text += " • Get ready to board! \uD83C\uDF0A"

                showNotification(context, title, text, cruiseId)

                // Reschedule for next day 9 AM
                NotificationHelper.scheduleDailyCountdown(context, cruiseId, shipName, startDate)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, text: String, cruiseId: Long) {
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(context, NotificationHelper.COUNTDOWN_REQUEST_CODE, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(context, NotificationHelper.COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NotificationHelper.COUNTDOWN_REQUEST_CODE, notif)
            }
        } catch (_: SecurityException) {}
    }
}
