package com.charles.cruiseapp.data.hotspot

import com.charles.cruiseapp.data.local.Message
import com.charles.cruiseapp.data.party.PartyChatRepository
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class WireChatDto(
    val type: String = "chat",
    val id: String,
    val sender: String,
    val text: String,
    val ts: Long
)

@Serializable
data class WireHistoryItem(
    val id: String,
    val sender: String,
    val text: String,
    val ts: Long,
    val self: Boolean
)

@Serializable
data class WireHistoryDto(
    val type: String = "history",
    val messages: List<WireHistoryItem>
)

@Serializable
data class WireErrorDto(
    val type: String = "error",
    val code: String,
    val message: String
)

class ChatWebServer(
    private val port: Int,
    private val repo: PartyChatRepository,
    private val hotspotController: HotspotController?,
    private val assetLoader: (String) -> ByteArray?
) : NanoWSD(port) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    private val sessions = CopyOnWriteArrayList<GuestSession>()
    private val inFlightIds = Collections.synchronizedSet(mutableSetOf<String>())

    init {
        // Bridge: whenever repository persists a message from ANY source (Nearby peer, self, web guest),
        // fan it out to connected browser guests (except the originator via inFlight tracking).
        try {
            repo.onMessagePersisted = { message -> broadcastChat(message) }
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
        }
    }

    // ---- HTTP routing (non-WebSocket requests) ----
    override fun serve(session: IHTTPSession): Response {
        // Check websocket handshake first – if it's a WS upgrade, let super handle it
        val isWebSocket = session.headers["upgrade"]?.lowercase() == "websocket" ||
            session.headers["Upgrade"]?.lowercase() == "websocket" ||
            session.uri == "/ws"
        if (isWebSocket && session.uri == "/ws") {
            return super.serve(session)
        }

        // Captive portal / hotspot sign-in auto-open (so guests see chat without typing URL)
        // When a device joins a Wi-Fi with no internet, OS probes http://connectivitycheck.gstatic.com/generate_204 etc.
        // Intercept those and redirect to our chat page so OS shows "Sign in to network" that opens the chat.
        if (isCaptivePortalRequest(session)) {
            return handleCaptivePortalRequest(session)
        }

        // Static asset serving for the web client (Phase 5 assets under hotspot_chat/)
        // Supported paths: "/", "/index.html", "/styles.css", "/chat.js", "/ws" is websocket, not http
        val rawUri = session.uri ?: "/"
        // Normalize: strip query param if present
        val pathOnly = rawUri.substringBefore("?")
        val effectivePath = when {
            pathOnly == "/" -> "index.html"
            pathOnly.startsWith("/") -> pathOnly.trimStart('/')
            else -> pathOnly
        }
        // Special handling: /ws over HTTP should return 426 or 400 – but let WS handle it
        if (effectivePath == "ws") {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "WebSocket endpoint")
        }

        val bytes = try { assetLoader(effectivePath) } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            null
        }
        if (bytes == null) {
            // Try fallback to index.html for SPA? But we only have one page – return 404 for unknown
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found: $effectivePath")
        }
        val mime = when {
            effectivePath.endsWith(".html") -> "text/html; charset=utf-8"
            effectivePath.endsWith(".css") -> "text/css"
            effectivePath.endsWith(".js") -> "application/javascript"
            effectivePath.endsWith(".svg") -> "image/svg+xml"
            effectivePath.endsWith(".png") -> "image/png"
            effectivePath.endsWith(".json") -> "application/json"
            else -> "application/octet-stream"
        }
        return newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
    }

    private fun isCaptivePortalRequest(session: IHTTPSession): Boolean {
        val uri = session.uri?.lowercase() ?: ""
        val host = session.headers["host"]?.lowercase() ?: session.headers["Host"]?.lowercase() ?: ""
        // Common captive portal probes (Android, iOS, Windows, Firefox)
        return uri.contains("generate_204") || uri.contains("gen_204") ||
               uri.contains("hotspot-detect") || uri.contains("ncsi.txt") ||
               uri.contains("connecttest.txt") || uri.contains("success.txt") ||
               uri.contains("canonical.html") || uri == "/generate_204" ||
               uri == "/gen_204" ||
               host.contains("connectivitycheck") || host.contains("clients3.google") ||
               host.contains("captive.apple") || host.contains("apple.com") ||
               host.contains("msftconnecttest") || host.contains("msftncsi") ||
               host.contains("detectportal")
    }

    private fun handleCaptivePortalRequest(session: IHTTPSession): Response {
        val hostIp = getHostIpForRedirect()
        val location = "http://$hostIp:$port/"
        FirebaseCrashlyticsUtils.log("Captive portal probe ${session.uri} Host=${session.headers["host"]} -> redirect $location")
        // Try to serve the chat page directly with 200 for probes that expect HTML, and also send 302 Location for OS that follows redirects.
        // We return 302 with Location header; NanoHTTPD will send 302. Some OSes also accept 200 with our page.
        // To maximize compatibility, we return 302 and include our page as body for browsers that don't follow redirect automatically.
        return try {
            val indexBytes = assetLoader("index.html")
            if (indexBytes != null) {
                val resp = newFixedLengthResponse(Response.Status.FOUND, "text/html", ByteArrayInputStream(indexBytes), indexBytes.size.toLong())
                resp.addHeader("Location", location)
                resp.addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                resp
            } else {
                val html = "<html><head><meta http-equiv=\"refresh\" content=\"0; url=$location\" /></head><body>Redirecting to <a href=\"$location\">Cruise Chat</a></body></html>"
                val resp = newFixedLengthResponse(Response.Status.FOUND, "text/html", html)
                resp.addHeader("Location", location)
                resp
            }
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            val html = "<html><head><meta http-equiv=\"refresh\" content=\"0; url=$location\" /></head><body><a href=\"$location\">Cruise Chat</a></body></html>"
            val resp = newFixedLengthResponse(Response.Status.FOUND, "text/html", html)
            resp.addHeader("Location", location)
            resp
        }
    }

    private fun getHostIpForRedirect(): String {
        val state = hotspotController?.state?.value
        if (state is HotspotState.Running) return state.hostIp
        return "192.168.49.1"
    }

    // ---- WebSocket handling ----
    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return GuestWebSocket(handshake)
    }

    private inner class GuestWebSocket(private val handshake: IHTTPSession) : WebSocket(handshake) {
        // Per-connection session (null until join succeeds)
        @Volatile
        private var session: GuestSession? = null

        override fun onOpen() {
            // Wait for "join" before doing anything; nothing to send yet
            FirebaseCrashlyticsUtils.log("WebSocket onOpen from ${handshake.remoteIpAddress}")
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            session?.let { s ->
                sessions.remove(s)
                hotspotController?.updateGuestCount(sessions.size)
                FirebaseCrashlyticsUtils.log("WebSocket onClose guestCode=${s.guestCode} remaining=${sessions.size}")
            }
            session = null
        }

        override fun onMessage(frame: WebSocketFrame) {
            val text = try { frame.textPayload } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                return
            } ?: return
            try {
                val element = json.parseToJsonElement(text)
                val obj = element.jsonObject
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "join" -> handleJoin(obj)
                    "chat" -> handleChat(obj)
                    "ping" -> {
                        // Keepalive – respond with pong so client knows we're alive, don't log as error
                        trySendRaw(this, json.encodeToString(kotlinx.serialization.json.buildJsonObject { put("type", "pong") }))
                    }
                    "pong" -> { /* ignore */ }
                    else -> {
                        trySendRaw(frameToSocket = this, rawJson = json.encodeToString(WireErrorDto(code = "BAD_REQUEST", message = "Unknown message type")))
                    }
                }
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                trySendRaw(frameToSocket = this, rawJson = json.encodeToString(WireErrorDto(code = "BAD_REQUEST", message = "Malformed message")))
            }
        }

        private fun handleJoin(envelope: JsonObject) {
            val guestCode = envelope["guestId"]?.jsonPrimitive?.contentOrNull
            val name = envelope["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            // Also support "guestCode"/"code" variants if client drifts
            val codeToUse = guestCode ?: envelope["code"]?.jsonPrimitive?.contentOrNull
            if (codeToUse.isNullOrBlank() || name.isBlank()) {
                trySendRaw(this, json.encodeToString(WireErrorDto(code = "NAME_REQUIRED", message = "A display name is required")))
                return
            }
            val trimmedName = name.take(40)
            // Replace any stale session for reconnecting guest
            sessions.removeAll { it.guestCode == codeToUse }
            val s = GuestSession(codeToUse, trimmedName, this)
            sessions.add(s)
            session = s
            hotspotController?.updateGuestCount(sessions.size)
            FirebaseCrashlyticsUtils.log("Guest join: $trimmedName code=${codeToUse.take(8)} sessions=${sessions.size}")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Deterministic color if available, else fallback
                    try { repo.upsertGuestMemberDeterministic(codeToUse, trimmedName) } catch (_: Exception) { repo.upsertGuestMember(codeToUse, trimmedName) }
                    val history = repo.messageHistorySnapshot()
                    val historyItems = history.map { msg ->
                        WireHistoryItem(
                            id = msg.clientMessageId,
                            sender = msg.senderName,
                            text = msg.text,
                            ts = msg.timestamp,
                            self = msg.clientMessageId.let { _ -> false } // placeholder, computed below
                        )
                    }.mapIndexed { idx, item ->
                        // Compute self flag per guest: a message is self if senderName == trimmedName AND clientMessageId corresponds to messages from this guest?
                        // Better: determine if this message originated from this guest's code.
                        // Since Message doesn't store origin guestCode, we approximate: messages where senderName==trimmedName and endpointId=="" (guest origin) and status DELIVERED?
                        // For reliability, we use repository logic: message.senderName == trimmedName is imperfect if duplicate names.
                        // We improve by checking if message was from web guest at all (endpointId=="") and senderName matches – still ambiguous but per spec use guestCode matching via workaround:
                        // Instead store that we cannot know exact origin guestCode from Message row alone; fallback to name matching for history's self flag.
                        // Since plan.md §6.2 says server computes self per-connection relative to that guest's own UUID, we would need to store originGuestCode per message.
                        // As we don't yet persist originGuestCode, we use name matching as best effort.
                        // Phase 3 TODO: Enhance Message to carry originGuestCode if needed.
                        val orig = history[idx]
                        val isSelf = orig.clientMessageId.let { _ ->
                            // Heuristic: if message isFromSelf==false && orig.endpointId=="" && orig.senderName==trimmedName
                            // then it *might* be this guest's. Check inFlightIds? no.
                            // For now use senderName matching
                            orig.senderName == trimmedName && orig.endpointId == ""
                        }
                        item.copy(self = isSelf)
                    }
                    val historyDto = WireHistoryDto(messages = historyItems)
                    val payload = json.encodeToString(historyDto)
                    trySendRaw(s.socket, payload)
                } catch (e: Exception) {
                    FirebaseCrashlyticsUtils.recordException(e)
                    trySendRaw(s.socket, json.encodeToString(WireErrorDto(code = "SERVER_ERROR", message = "Failed to load history")))
                }
            }
        }

        private fun handleChat(envelope: JsonObject) {
            val s = session
            if (s == null) {
                trySendRaw(this, json.encodeToString(WireErrorDto(code = "NOT_JOINED", message = "Send a join message first")))
                return
            }
            val id = envelope["id"]?.jsonPrimitive?.contentOrNull ?: return
            val text = envelope["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.isBlank() || text.length > 2000) return
            // Track in-flight to avoid echoing back to originator
            inFlightIds.add(id)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Use deterministic upsert as ensure guest member still exists (name might have changed, but we keep original displayName for this send)
                    repo.receiveFromWebGuest(s.guestCode, s.displayName, text, id)
                    // repo.onMessagePersisted will trigger broadcastChat which will skip originator via inFlightIds
                    // Remove from inFlight after short delay to allow broadcast to process
                    kotlinx.coroutines.delay(2000)
                    inFlightIds.remove(id)
                } catch (e: Exception) {
                    FirebaseCrashlyticsUtils.recordException(e)
                    inFlightIds.remove(id)
                }
            }
        }

        override fun onPong(pong: WebSocketFrame) {
            // no-op
        }

        override fun onException(exception: IOException) {
            FirebaseCrashlyticsUtils.recordException(exception)
        }
    }

    private fun broadcastChat(message: Message) {
        try {
            FirebaseCrashlyticsUtils.log("broadcastChat sender=${message.senderName} id=${message.clientMessageId.take(8)} sessions=${sessions.size} inFlight=${inFlightIds.size}")
            val dto = WireChatDto(id = message.clientMessageId, sender = message.senderName, text = message.text, ts = message.timestamp)
            val payload = json.encodeToString(dto)
            // Snapshot sessions to avoid concurrent modification
            val snapshot = sessions.toList()
            for (s in snapshot) {
                // Skip echoing a guest's own message back to the socket that sent it (inFlight check)
                if (inFlightIds.contains(message.clientMessageId) && s.displayName == message.senderName) {
                    // If this message corresponds to an in-flight id from this guest, skip.
                    // However we need to know which guest sent it; we use displayName match + inFlight set.
                    // Better would be to track originGuestCode -> but for now this heuristic.
                    // Continue to skip this session.
                    // Note: if two guests have same displayName at same time sending same id (unlikely, ids are UUIDs), this could incorrectly skip.
                    // Acceptable per spec's recommended Option 1.
                    continue
                }
                trySendRaw(s.socket, payload)
            }
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
        }
    }

    private fun trySendRaw(frameToSocket: WebSocket?, rawJson: String) {
        if (frameToSocket == null) return
        try {
            frameToSocket.send(rawJson)
        } catch (e: IOException) {
            FirebaseCrashlyticsUtils.recordException(e)
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
        }
    }

    // Cleanup override to clear repo callback when server stops
    override fun stop() {
        try {
            // Clear callback only if we set it
            if (repo.onMessagePersisted != null) {
                // Check if still pointing to our lambda (broadly clear)
                repo.onMessagePersisted = null
            }
        } catch (_: Exception) {}
        super.stop()
    }
}

// Extension to ensure generic JSON helpers are available
private fun kotlinx.serialization.json.JsonObject.getStringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull
