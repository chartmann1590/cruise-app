package com.cruiseapp.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cruiseapp.MainActivity
import com.cruiseapp.util.formatTime

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_ID, 0)
        val title = intent.getStringExtra("title") ?: "Cruise Event"
        val location = intent.getStringExtra("location") ?: ""
        val start = intent.getLongExtra("start", 0)
        val timeStr = if (start != 0L) formatTime(start) else ""

        val launch = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pi = PendingIntent.getActivity(context, eventId.toInt(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Upcoming: $title")
            .setContentText(if (location.isNotEmpty()) "$timeStr • $location" else "Starts at $timeStr")
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
