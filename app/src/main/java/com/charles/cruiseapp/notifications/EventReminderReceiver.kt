package com.charles.cruiseapp.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.MainActivity
import com.charles.cruiseapp.util.formatTime

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_ID, 0)
        val title = intent.getStringExtra("title") ?: "Cruise Event"
        val location = intent.getStringExtra("location") ?: ""
        val start = intent.getLongExtra("start", 0)
        val timeStr = if (start != 0L) formatTime(start) else ""

        val launch = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pi = PendingIntent.getActivity(context, eventId.toInt(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val tm = (context.applicationContext as? CruiseApplication)?.translationManager
        val upcomingPrefix = tm?.translateCached("Upcoming") ?: "Upcoming"
        val startsAtPrefix = tm?.translateCached("Starts at") ?: "Starts at"

        val notifTitle = "$upcomingPrefix: $title"
        val notifText = if (location.isNotEmpty()) "$timeStr • $location" else "$startsAtPrefix $timeStr"

        val notif = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notifTitle)
            .setContentText(notifText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(eventId.toInt(), notif)
            }
        } catch (e: SecurityException) {}
    }
}
