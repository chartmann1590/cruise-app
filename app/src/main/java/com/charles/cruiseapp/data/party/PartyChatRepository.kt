package com.charles.cruiseapp.data.party

import android.content.Context
import com.charles.cruiseapp.data.local.CruiseDatabase
import com.charles.cruiseapp.data.local.Message
import com.charles.cruiseapp.data.local.PartyMember
import com.charles.cruiseapp.data.nearby.NearbyManager
import com.charles.cruiseapp.data.nearby.WireMessage
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class PartyChatRepository(
    private val context: Context,
    private val db: CruiseDatabase
) {
    val nearby = NearbyManager(context)
    private val prefs = context.getSharedPreferences("cruise_party_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val members: Flow<List<PartyMember>> = db.partyMemberDao().getAll()
    val localMessages: Flow<List<Message>> = db.messageDao().getAll()
    val nearbyStatus: StateFlow<String> = nearby.status
    val discovered: StateFlow<List<NearbyManager.DiscoveredEndpoint>> = nearby.discovered
    val connected: StateFlow<List<NearbyManager.ConnectedEndpoint>> = nearby.connected

    // Hook for Phase 3's ChatWebServer to fan out messages to browser guests
    var onMessagePersisted: ((Message) -> Unit)? = null

    private var retryJob: Job? = null

    // Palette for guest color assignment (distinct from self's default color)
    private val palette = listOf(
        "#FF6200EE", "#FF03DAC6", "#FFFF5722", "#FF4CAF50",
        "#FFFF9800", "#FF009688", "#FF3F51B5", "#FFE91E63",
        "#FF9C27B0", "#FF2196F3", "#FF00BCD4", "#FF8BC34A"
    )

    init {
        try {
            val code = getSelfCode()
            FirebaseCrashlyticsUtils.setUserId(code)
            FirebaseCrashlyticsUtils.setCustomKey("party_self_code", code.take(8))
        } catch (_: Exception) {}

        nearby.onWireReceived = { wire, endpointId ->
            scope.launch { handleIncomingWire(wire, endpointId) }
        }
        startRetryLoop()
        scope.launch { ensureSelfMember() }
        scope.launch {
            nearby.connected.collect { connectedList ->
                if (connectedList.isNotEmpty()) {
                    for (c in connectedList) {
                        c.code?.let { code ->
                            try {
                                db.partyMemberDao().getByCode(code)?.let {
                                    db.partyMemberDao().updateEndpoint(code, c.id)
                                }
                            } catch (e: Exception) {
                                FirebaseCrashlyticsUtils.recordException(e)
                            }
                        }
                    }
                    flushPending()
                }
            }
        }
    }

    fun getSelfCode(): String {
        var code = prefs.getString("self_code", null)
        if (code.isNullOrBlank()) {
            code = UUID.randomUUID().toString()
            prefs.edit().putString("self_code", code).apply()
        }
        return code
    }

    fun getSelfName(): String = prefs.getString("self_name", "") ?: ""

    // Suspend version used by thin ViewModel delegate (mirrors original behavior)
    suspend fun setSelfNameSuspend(name: String) {
        prefs.edit().putString("self_name", name).apply()
        nearby.localName = name
        try {
            val self = db.partyMemberDao().getSelf()
            val code = getSelfCode()
            if (self == null) {
                db.partyMemberDao().insert(PartyMember(displayName = name, isSelf = true, code = code))
            } else {
                db.partyMemberDao().insert(self.copy(displayName = name, code = code))
            }
            nearby.setSelfInfo(name, code)
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
        }
    }

    // Non-suspend wrapper for direct callers (keeps original PartyViewModel sync prefs behavior)
    fun setSelfName(name: String) {
        scope.launch { setSelfNameSuspend(name) }
    }

    fun getQrData(): String {
        val code = getSelfCode()
        val name = getSelfName().ifBlank { nearby.localName.ifBlank { "Cruiser" } }
        val j = JSONObject()
        j.put("n", name)
        j.put("c", code)
        return j.toString()
    }

    suspend fun sendLocalMessage(text: String) {
        if (text.isBlank()) return
        val sender = getSelfName().ifBlank { nearby.localName.ifBlank { "You" } }
        val selfCode = getSelfCode()
        val msgId = UUID.randomUUID().toString()
        val msg = Message(
            clientMessageId = msgId,
            senderName = sender,
            text = text,
            timestamp = System.currentTimeMillis(),
            isFromSelf = true,
            status = "PENDING",
            targetCode = null,
            targetName = null
        )
        db.messageDao().insert(msg)
        // not yet persisted callback? For self messages, onMessagePersisted should still fan out to web guests
        try {
            FirebaseCrashlyticsUtils.log("sendLocalMessage invoke onMessagePersisted sessions? ${onMessagePersisted != null} text=${text.take(20)}")
            onMessagePersisted?.invoke(msg.copy(status = "SENT"))
        } catch (e: Exception) { FirebaseCrashlyticsUtils.recordException(e) }
        attemptSend(msg)
    }

    suspend fun sendToMember(text: String, target: PartyMember) {
        if (text.isBlank()) return
        val sender = getSelfName().ifBlank { nearby.localName.ifBlank { "You" } }
        val msgId = UUID.randomUUID().toString()
        val msg = Message(
            clientMessageId = msgId,
            senderName = sender,
            text = text,
            timestamp = System.currentTimeMillis(),
            isFromSelf = true,
            status = "PENDING",
            targetCode = target.code,
            targetName = target.displayName
        )
        db.messageDao().insert(msg)
        try { onMessagePersisted?.invoke(msg.copy(status = "SENT")) } catch (_: Exception) {}
        attemptSend(msg)
    }

    // Called by ChatWebServer when a browser guest sends a message
    suspend fun receiveFromWebGuest(guestCode: String, senderName: String, text: String, clientMessageId: String) {
        val existing = db.messageDao().getByClientId(clientMessageId)
        if (existing != null) return
        val msg = Message(
            clientMessageId = clientMessageId,
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis(),
            isFromSelf = false,
            endpointId = "",
            status = "DELIVERED",
            targetCode = null,
            targetName = null
        )
        db.messageDao().insert(msg)
        // Ensure guest member exists (upsert) – will be no-op if already exists
        try { upsertGuestMember(guestCode, senderName) } catch (_: Exception) {}
        onMessagePersisted?.invoke(msg)
        nearby.sendChatWithId(clientMessageId, senderName, text, msg.timestamp, guestCode, null, null)
    }

    // --- Upsert guest member (added in Phase 3 but included here for completeness) ---
    suspend fun upsertGuestMember(guestCode: String, name: String) {
        val existing = db.partyMemberDao().getByCode(guestCode)
        if (existing == null) {
            db.partyMemberDao().insert(PartyMember(displayName = name, code = guestCode, isSelf = false, colorHex = nextPaletteColor()))
        } else if (existing.displayName != name) {
            db.partyMemberDao().insert(existing.copy(displayName = name))
        }
    }

    suspend fun messageHistorySnapshot(): List<Message> = try {
        db.messageDao().getAll().first()
    } catch (e: Exception) {
        FirebaseCrashlyticsUtils.recordException(e)
        emptyList()
    }

    private fun nextPaletteColor(): String {
        return try {
            // Use member count to pick next color; run blocking? We'll use scope value not available synchronously.
            // Fallback to random palette entry based on current time
            palette[(System.currentTimeMillis() % palette.size).toInt()]
        } catch (_: Exception) { palette.first() }
    }

    // Synchronous palette assignment based on existing count (call when we have access)
    private suspend fun nextPaletteColorSuspend(): String {
        return try {
            val count = db.partyMemberDao().getAllOnce().size
            palette[count % palette.size]
        } catch (_: Exception) { palette.first() }
    }

    // Override upsert to use count-based color when possible
    // (keep the simpler version above as fallback; this suspend version is more deterministic)
    // We add explicit method for server to use
    suspend fun upsertGuestMemberDeterministic(guestCode: String, name: String) {
        val existing = db.partyMemberDao().getByCode(guestCode)
        if (existing == null) {
            val color = nextPaletteColorSuspend()
            db.partyMemberDao().insert(PartyMember(displayName = name, code = guestCode, isSelf = false, colorHex = color))
        } else if (existing.displayName != name) {
            db.partyMemberDao().insert(existing.copy(displayName = name))
        }
    }

    private suspend fun handleIncomingWire(wire: WireMessage, endpointId: String) {
        val trace = FirebasePerfUtils.startTrace("party_handle_incoming")
        trace?.putAttribute("type", wire.type)
        try {
            val selfCode = getSelfCode()
            if (wire.targetCode != null && wire.targetCode != selfCode) {
                trace?.putAttribute("filtered", "true")
                return
            }
            FirebaseCrashlyticsUtils.log("Handling incoming ${wire.type} from ${wire.sender} id=${wire.messageId}")
            when (wire.type) {
                "CHAT" -> {
                    val existing = db.messageDao().getByClientId(wire.messageId)
                    if (existing == null) {
                        val msg = Message(
                            clientMessageId = wire.messageId,
                            senderName = wire.sender,
                            text = wire.text,
                            timestamp = wire.timestamp,
                            isFromSelf = false,
                            endpointId = endpointId,
                            status = "DELIVERED",
                            targetCode = wire.targetCode,
                            targetName = wire.targetName
                        )
                        db.messageDao().insert(msg)
                        wire.senderCode?.let { sc ->
                            val member = db.partyMemberDao().getByCode(sc)
                            if (member != null) {
                                db.partyMemberDao().updateEndpoint(sc, endpointId)
                            }
                        }
                        // Fan out to web guests
                        try { onMessagePersisted?.invoke(msg) } catch (e: Exception) { FirebaseCrashlyticsUtils.recordException(e) }
                        nearby.sendDeliveredReceipt(wire.messageId)
                        delay(1200)
                        val inserted = db.messageDao().getByClientId(wire.messageId)
                        if (inserted != null) {
                            db.messageDao().update(inserted.copy(status = "READ"))
                        }
                        nearby.sendReadReceipt(wire.messageId)
                        trace?.putMetric("chat_handled", 1)
                    } else {
                        nearby.sendDeliveredReceipt(wire.messageId)
                        trace?.putMetric("duplicate", 1)
                    }
                }
                "DELIVERED" -> {
                    val ref = wire.refId
                    if (ref == null) {
                        trace?.putAttribute("error", "missing_refId")
                        return
                    }
                    val original = db.messageDao().getByClientId(ref)
                    if (original != null && original.status != "READ") {
                        db.messageDao().updateStatus(ref, "DELIVERED")
                    }
                    trace?.putMetric("delivered_handled", 1)
                }
                "READ" -> {
                    val ref = wire.refId
                    if (ref == null) {
                        trace?.putAttribute("error", "missing_refId")
                        return
                    }
                    val original = db.messageDao().getByClientId(ref)
                    if (original != null) {
                        db.messageDao().updateStatus(ref, "READ")
                    }
                    trace?.putMetric("read_handled", 1)
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            FirebaseCrashlyticsUtils.log("handleIncomingWire failed: ${e.message}")
            trace?.putMetric("error", 1)
            try { trace?.putAttribute("error", e.message ?: "unknown") } catch (_: Exception) {}
        } finally {
            try { trace?.stop() } catch (_: Exception) {}
        }
    }

    private suspend fun attemptSend(msg: Message) {
        val trace = FirebasePerfUtils.startTrace("party_attempt_send")
        trace?.putAttribute("target", msg.targetCode ?: "broadcast")
        trace?.putAttribute("msgId", msg.clientMessageId.take(8))
        try {
            FirebaseCrashlyticsUtils.log("Attempting send ${msg.clientMessageId} to ${msg.targetCode ?: "broadcast"}")
            val senderCode = getSelfCode()
            val sent = nearby.sendChatWithId(msg.clientMessageId, msg.senderName, msg.text, msg.timestamp, senderCode, msg.targetCode, msg.targetName)
            if (sent) {
                db.messageDao().updateStatus(msg.clientMessageId, "SENT")
                trace?.putMetric("sent", 1)
            } else {
                db.messageDao().incrementRetry(msg.clientMessageId, "PENDING")
                trace?.putMetric("pending", 1)
            }
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            trace?.putMetric("error", 1)
        } finally {
            try { trace?.stop() } catch (_: Exception) {}
        }
    }

    private suspend fun flushPending() {
        val pending = db.messageDao().getPending()
        for (msg in pending) {
            if (msg.isFromSelf) {
                val shouldSend = if (msg.targetCode != null) {
                    nearby.connected.value.any { it.code == msg.targetCode }
                } else {
                    nearby.connected.value.isNotEmpty()
                }
                if (!shouldSend) continue
                val sent = nearby.sendChatWithId(msg.clientMessageId, msg.senderName, msg.text, msg.timestamp, getSelfCode(), msg.targetCode, msg.targetName)
                if (sent && msg.status == "PENDING") {
                    db.messageDao().updateStatus(msg.clientMessageId, "SENT")
                }
                delay(200)
            }
        }
    }

    private fun startRetryLoop() {
        retryJob?.cancel()
        retryJob = scope.launch {
            while (isActive) {
                delay(5000)
                if (nearby.connected.value.isNotEmpty()) {
                    val pending = db.messageDao().getPending()
                    if (pending.isNotEmpty()) {
                        for (msg in pending) {
                            if (msg.retryCount > 100) continue
                            val targetAvailable = if (msg.targetCode != null) nearby.connected.value.any { it.code == msg.targetCode } else true
                            if (!targetAvailable) continue
                            attemptSend(msg)
                            delay(500)
                        }
                    }
                }
            }
        }
    }

    suspend fun markAllDeliveredAsRead() {
        // Use DB query rather than localMessages flow value to work outside ViewModel
        val all = try { db.messageDao().getAll().first() } catch (_: Exception) { emptyList() }
        val delivered = all.filter { !it.isFromSelf && it.status == "DELIVERED" }
        for (msg in delivered) {
            db.messageDao().updateStatus(msg.clientMessageId, "READ")
            nearby.sendReadReceipt(msg.clientMessageId)
        }
    }

    suspend fun clearMessages() { db.messageDao().clearAll() }

    private suspend fun ensureSelfMember(): PartyMember {
        val code = getSelfCode()
        var self = db.partyMemberDao().getSelf()
        if (self == null) {
            val name = getSelfName().ifBlank { "Cruiser" }
            self = PartyMember(displayName = name, isSelf = true, code = code)
            db.partyMemberDao().insert(self)
            self = db.partyMemberDao().getSelf() ?: self
        } else if (self.code.isBlank()) {
            self = self.copy(code = code)
            db.partyMemberDao().insert(self)
        }
        val prefName = getSelfName()
        if (prefName.isNotBlank() && self.displayName != prefName) {
            self = self.copy(displayName = prefName)
            db.partyMemberDao().insert(self)
        }
        // Sync to nearby
        try { nearby.setSelfInfo(self.displayName.ifBlank { "Cruiser" }, self.code) } catch (_: Exception) {}
        return self
    }

    fun shutdown() { retryJob?.cancel(); nearby.onWireReceived = null; scope.cancel() }
}
