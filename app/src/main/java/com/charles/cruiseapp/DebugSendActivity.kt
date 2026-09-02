package com.charles.cruiseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DebugSendActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra("text") ?: intent.getStringExtra("android.intent.extra.TEXT") ?: "Debug hello"
        val app = application as CruiseApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.partyChatRepository.sendLocalMessage(text)
            } catch (_: Exception) {}
        }
        // Finish quickly, don't show UI
        finish()
    }
}
