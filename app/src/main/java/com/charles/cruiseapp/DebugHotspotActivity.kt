package com.charles.cruiseapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.charles.cruiseapp.data.hotspot.HotspotChatService

class DebugHotspotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra("action") ?: "start"
        val svc = Intent(this, HotspotChatService::class.java)
        if (action == "stop") {
            svc.action = HotspotChatService.ACTION_STOP
            startService(svc)
        } else {
            svc.action = HotspotChatService.ACTION_START
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        }
        finish()
    }
}
