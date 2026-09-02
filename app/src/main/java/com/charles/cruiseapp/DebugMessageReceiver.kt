package com.charles.cruiseapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DebugMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.charles.cruiseapp.DEBUG_SEND") {
            val text = intent.getStringExtra("text") ?: "Debug test"
            val app = context.applicationContext as CruiseApplication
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.partyChatRepository.sendLocalMessage(text)
                } catch (_: Exception) {}
            }
        }
    }
}
