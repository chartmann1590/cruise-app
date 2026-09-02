package com.charles.cruiseapp.data.hotspot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.MainActivity
import com.charles.cruiseapp.notifications.NotificationHelper
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

class HotspotChatService : Service() {
    companion object {
        const val ACTION_START = "com.charles.cruiseapp.action.START_HOTSPOT_CHAT"
        const val ACTION_STOP = "com.charles.cruiseapp.action.STOP_HOTSPOT_CHAT"
        const val CHAT_PORT = 8085
        private const val NOTIFICATION_ID = 4200
    }

    private lateinit var hotspotController: HotspotController
    private var server: ChatWebServer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        hotspotController = (application as CruiseApplication).hotspotController
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startChat()
            ACTION_STOP -> stopChat()
        }
        return START_NOT_STICKY
    }

    private fun startChat() {
        val app = application as CruiseApplication
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Starting guest chat…"))
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
        }

        // Ensure notification channel exists for this service (in case CruiseApplication didn't create it yet)
        ensureHotspotChannel()

        val webServer = ChatWebServer(
            port = CHAT_PORT,
            repo = app.partyChatRepository,
            hotspotController = hotspotController,
            assetLoader = { path ->
                try {
                    // Strip leading slash already done by server, handle both with and without prefix
                    val assetPath = if (path.startsWith("hotspot_chat/")) path else "hotspot_chat/$path"
                    app.assets.open(assetPath).readBytes()
                } catch (e: Exception) {
                    // Try fallback without prefix for root assets
                    try { app.assets.open(path).readBytes() } catch (_: Exception) { null }
                }
            }
        )
        try {
            // Use 0 timeout (no socket read timeout) so WebSocket connections stay open indefinitely.
            // Default NanoHTTPD SOCKET_READ_TIMEOUT=5000 would close idle WebSockets every 5s causing the page to "refresh" / reconnect loop.
            webServer.start(0, false)
            server = webServer
            FirebaseCrashlyticsUtils.log("ChatWebServer started on port $CHAT_PORT (timeout=0)")
            hotspotController.start(CHAT_PORT)
        } catch (e: IOException) {
            FirebaseCrashlyticsUtils.recordException(e)
            hotspotController.reportServerBindFailure("Could not start chat server: ${e.message}")
            stopSelf()
            return
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            hotspotController.reportServerBindFailure("Could not start chat server: ${e.message}")
            stopSelf()
            return
        }

        serviceScope.launch {
            hotspotController.state.collect { state ->
                when (state) {
                    is HotspotState.Running -> updateNotification("Guest chat running — ${state.ssid}")
                    is HotspotState.Error -> {
                        updateNotification("Guest chat failed to start")
                        // Don't call stopChat() re-entrantly from collect; schedule stop
                        stopChat()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun stopChat() {
        try { hotspotController.stop() } catch (e: Exception) { FirebaseCrashlyticsUtils.recordException(e) }
        try { server?.stop(); server = null } catch (e: Exception) { FirebaseCrashlyticsUtils.recordException(e) }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun ensureHotspotChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val existing = mgr.getNotificationChannel(NotificationHelper.HOTSPOT_CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    NotificationHelper.HOTSPOT_CHANNEL_ID,
                    "Guest Wi-Fi Chat",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Shown while guest Wi-Fi chat is running" }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationHelper.buildHotspotChatNotification(this, text)
    }

    private fun updateNotification(text: String) {
        try {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try { server?.stop() } catch (_: Exception) {}
        try { hotspotController.stop() } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
