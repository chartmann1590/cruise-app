package com.cruiseapp.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cruiseapp.data.local.PlannedEvent

object NotificationHelper {
    const val CHANNEL_ID = "cruise_reminders"
    const val PARTY_CHANNEL_ID = "party_messages"
    const val EXTRA_EVENT_ID = "event_id"

    fun scheduleEventNotification(context: Context, event: PlannedEvent) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = event.startTimeMillis - event.reminderMinutesBefore * 60 * 1000L
        if (triggerAt <= System.currentTimeMillis()) return
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, event.id)
            putExtra("title", event.title)
            putExtra("location", event.location)
            putExtra("start", event.startTimeMillis)
        }
        val pi = PendingIntent.getBroadcast(context, event.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmMgr.canScheduleExactAlarms()) {
                    alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelNotification(context: Context, eventId: Long) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EventReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, eventId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmMgr.cancel(pi)
        pi.cancel()
    }
}
