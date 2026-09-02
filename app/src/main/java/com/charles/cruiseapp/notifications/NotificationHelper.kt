package com.charles.cruiseapp.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.charles.cruiseapp.MainActivity
import com.charles.cruiseapp.data.local.PlannedEvent

object NotificationHelper {
    const val CHANNEL_ID = "cruise_reminders"
    const val PARTY_CHANNEL_ID = "party_messages"
    const val HOTSPOT_CHANNEL_ID = "hotspot_chat"
    const val COUNTDOWN_CHANNEL_ID = "cruise_countdown"
    const val EXTRA_EVENT_ID = "event_id"
    const val COUNTDOWN_REQUEST_CODE = 9001
    const val COUNTDOWN_DAILY_HOUR = 9 // 9 AM local

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
        alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun cancelNotification(context: Context, eventId: Long) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EventReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, eventId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmMgr.cancel(pi)
        pi.cancel()
    }

    fun buildHotspotChatNotification(context: Context, text: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Stop action
        val stopIntent = Intent(context, com.charles.cruiseapp.data.hotspot.HotspotChatService::class.java).apply {
            action = com.charles.cruiseapp.data.hotspot.HotspotChatService.ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val smallIcon = android.R.drawable.stat_sys_data_bluetooth // fallback system icon; app icon is @android:drawable/sym_def_app_icon
        return NotificationCompat.Builder(context, HOTSPOT_CHANNEL_ID)
            .setContentTitle("Guest Wi-Fi Chat")
            .setContentText(text)
            .setSmallIcon(smallIcon)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun scheduleDailyCountdown(context: Context, cruiseId: Long, shipName: String, startDateMillis: Long) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancel existing first
        cancelDailyCountdown(context)
        // Don't schedule if already started/past
        val startDay = com.charles.cruiseapp.util.startOfDay(startDateMillis)
        val today = com.charles.cruiseapp.util.startOfDay(System.currentTimeMillis())
        if (startDay <= today) return
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, COUNTDOWN_DAILY_HOUR)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, CountdownReceiver::class.java).apply {
            putExtra("cruiseId", cruiseId)
            putExtra("shipName", shipName)
            putExtra("startDate", startDateMillis)
        }
        val pi = PendingIntent.getBroadcast(context, COUNTDOWN_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        // Persist for boot reschedule
        context.getSharedPreferences("cruise_countdown_prefs", Context.MODE_PRIVATE).edit()
            .putLong("countdown_cruiseId", cruiseId)
            .putString("countdown_shipName", shipName)
            .putLong("countdown_startDate", startDateMillis)
            .apply()
    }

    fun cancelDailyCountdown(context: Context) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CountdownReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, COUNTDOWN_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmMgr.cancel(pi)
        pi.cancel()
        context.getSharedPreferences("cruise_countdown_prefs", Context.MODE_PRIVATE).edit()
            .remove("countdown_cruiseId")
            .remove("countdown_shipName")
            .remove("countdown_startDate")
            .apply()
    }

    fun rescheduleCountdownIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("cruise_countdown_prefs", Context.MODE_PRIVATE)
        val cruiseId = prefs.getLong("countdown_cruiseId", -1)
        val shipName = prefs.getString("countdown_shipName", null)
        val startDate = prefs.getLong("countdown_startDate", 0)
        if (cruiseId != -1L && shipName != null && startDate != 0L) {
            scheduleDailyCountdown(context, cruiseId, shipName, startDate)
        }
    }
}
