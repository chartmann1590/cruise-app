package com.charles.cruiseapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.charles.cruiseapp.data.local.CruiseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.MY_PACKAGE_REPLACED" || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = CruiseDatabase.getDatabase(context)
                    val events = db.plannedEventDao().getFutureEvents(System.currentTimeMillis())
                    events.forEach { NotificationHelper.scheduleEventNotification(context, it) }
                    // Reschedule countdown if cruise is still future
                    NotificationHelper.rescheduleCountdownIfNeeded(context)
                } finally { pending.finish() }
            }
        }
    }
}
