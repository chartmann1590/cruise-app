package com.charles.cruiseapp.data.nearby

import android.content.Context
import android.util.Log
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WireMessage(
    val type: String, // CHAT, DELIVERED, READ
    val messageId: String,
    val sender: String,
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val refId: String? = null, // for receipts, original messageId
    val senderCode: String? = null,
    val targetCode: String? = null,
    val targetName: String? = null
)

@Serializable
data class ChatPayloadLegacy(val sender: String, val text: String, val timestamp: Long = System.currentTimeMillis())

class NearbyManager(private val context: Context) {
    companion object {
        const val SERVICE_ID = "com.charles.cruiseapp.NEARBY"
        val STRATEGY = Strategy.P2P_CLUSTER
    }
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status
    private val _discovered = MutableStateFlow<List<DiscoveredEndpoint>>(emptyList())
    val discovered: StateFlow<List<DiscoveredEndpoint>> = _discovered
    private val _connected = MutableStateFlow<List<ConnectedEndpoint>>(emptyList())
    val connected: StateFlow<List<ConnectedEndpoint>> = _connected
    // For backwards compat simple chat display
    private val _messages = MutableStateFlow<List<ChatPayloadLegacy>>(emptyList())
    val messages: StateFlow<List<ChatPayloadLegacy>> = _messages

    // Reliable delivery flows
    private val _incomingChats = MutableStateFlow<WireMessage?>(null)
    // we expose as flow of list for UI; but for logic we need to observe each incoming
    private val _incomingWire = MutableStateFlow<List<WireMessage>>(emptyList())
    val incomingWire: StateFlow<List<WireMessage>> = _incomingWire

    // callback for partyViewModel to handle receipts and chats
    var onWireReceived: ((WireMessage, String) -> Unit)? = null

    var localName: String = "Cruiser"
    var localCode: String = ""

    data class DiscoveredEndpoint(val id: String, val name: String, val code: String? = null)
    data class ConnectedEndpoint(val id: String, val name: String, val code: String? = null)

    private val endpointNames = mutableMapOf<String, String>()
    private val endpointCodes = mutableMapOf<String, String>()

    private fun parseNameAndCode(raw: String): Pair<String, String?> {
        // format "Name|code" or JSON
        return if (raw.contains("|")) {
            val p = raw.split("|", limit = 2)
            p[0] to p.getOrNull(1)
        } else {
            // try JSON
            try {
                val j = org.json.JSONObject(raw)
                val n = j.optString("n", j.optString("name", raw))
                val c = j.optString("c", j.optString("code", null))
                n to c
            } catch (_: Exception) { raw to null }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            _status.value = "Connection initiated with ${info.endpointName}"
            FirebaseCrashlyticsUtils.log("Nearby onConnectionInitiated $endpointId ${info.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            val (name, code) = parseNameAndCode(info.endpointName)
            endpointNames[endpointId] = name
            if (code != null) endpointCodes[endpointId] = code
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val trace = FirebasePerfUtils.startTrace("nearby_connection_result")
            trace?.putAttribute("endpoint", endpointId)
            try {
                if (result.status.isSuccess) {
                    val name = endpointNames[endpointId] ?: endpointId
                    val code = endpointCodes[endpointId]
                    if (_connected.value.none { it.id == endpointId }) {
                        _connected.value = _connected.value + ConnectedEndpoint(endpointId, name, code)
                    }
                    _status.value = "Connected to $name"
                    FirebaseCrashlyticsUtils.log("Nearby connected to $name ($endpointId)")
                    FirebaseCrashlyticsUtils.setCustomKey("nearby_connected_count", _connected.value.size)
                    trace?.putMetric("success", 1)
                } else {
                    _status.value = "Connection failed: ${result.status.statusCode}"
                    FirebaseCrashlyticsUtils.log("Nearby connection failed $endpointId code=${result.status.statusCode}")
                    trace?.putMetric("failure", 1)
                    trace?.putAttribute("status_code", result.status.statusCode.toString())
                }
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                trace?.putMetric("error", 1)
            } finally { try { trace?.stop() } catch (_: Exception) {} }
        }
        override fun onDisconnected(endpointId: String) {
            _connected.value = _connected.value.filter { it.id != endpointId }
            endpointNames.remove(endpointId)
            endpointCodes.remove(endpointId)
            _status.value = "Disconnected"
            FirebaseCrashlyticsUtils.log("Nearby disconnected $endpointId")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val (name, code) = parseNameAndCode(info.endpointName)
            FirebaseCrashlyticsUtils.log("Nearby discovered $name ($endpointId)")
            val current = _discovered.value
            if (current.none { it.id == endpointId }) {
                _discovered.value = current + DiscoveredEndpoint(endpointId, name, code)
                _status.value = "Found $name"
            }
        }
        override fun onEndpointLost(endpointId: String) {
            _discovered.value = _discovered.value.filter { it.id != endpointId }
            FirebaseCrashlyticsUtils.log("Nearby lost endpoint $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val trace = FirebasePerfUtils.startTrace("nearby_payload_received")
            trace?.putAttribute("endpoint", endpointId)
            try {
                payload.asBytes()?.let { bytes ->
                    val str = String(bytes)
                    FirebaseCrashlyticsUtils.log("Nearby payload from $endpointId size=${bytes.size}")
                    // Try WireMessage first
                    try {
                        val wire = json.decodeFromString<WireMessage>(str)
                        // dispatch
                        _incomingWire.value = _incomingWire.value + wire
                        onWireReceived?.invoke(wire, endpointId)
                        // also for UI legacy
                        if (wire.type == "CHAT") {
                            _messages.value = _messages.value + ChatPayloadLegacy(sender = wire.sender, text = wire.text, timestamp = wire.timestamp)
                        }
                        trace?.putMetric("wire_message", 1)
                        trace?.putAttribute("type", wire.type)
                        return@let
                    } catch (e: Exception) {
                        FirebaseCrashlyticsUtils.recordException(e)
                        // try legacy
                    }
                    try {
                        val chat = json.decodeFromString<ChatPayloadLegacy>(str)
                        _messages.value = _messages.value + chat
                        // convert to wire for uniform handling
                        val wire = WireMessage(type = "CHAT", messageId = "legacy-${System.currentTimeMillis()}", sender = chat.sender, text = chat.text, timestamp = chat.timestamp)
                        onWireReceived?.invoke(wire, endpointId)
                        trace?.putMetric("legacy_message", 1)
                    } catch (e: Exception) {
                        FirebaseCrashlyticsUtils.recordException(e)
                        val chat = ChatPayloadLegacy(sender = endpointNames[endpointId] ?: endpointId, text = str)
                        _messages.value = _messages.value + chat
                        trace?.putMetric("raw_message", 1)
                    }
                }
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                trace?.putMetric("error", 1)
            } finally {
                try { trace?.stop() } catch (_: Exception) {}
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun setSelfInfo(name: String, code: String) {
        localName = name
        localCode = code
    }

    fun startAdvertising(name: String) {
        if (name.isBlank()) return
        localName = name
        val advertiseName = if (localCode.isNotBlank()) "$name|$localCode" else name
        _status.value = "Advertising as $name..."
        FirebaseCrashlyticsUtils.log("Nearby startAdvertising $name")
        val trace = FirebasePerfUtils.startTrace("nearby_start_advertising")
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(advertiseName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                _status.value = "Advertising as $name (visible)"
                trace?.putMetric("success", 1)
                try { trace?.stop() } catch (_: Exception) {}
            }
            .addOnFailureListener { e ->
                _status.value = "Advertise failed: ${e.message}"
                FirebaseCrashlyticsUtils.recordException(e)
                FirebaseCrashlyticsUtils.log("Advertising failed: ${e.message}")
                trace?.putMetric("error", 1)
                try { trace?.putAttribute("error", e.message ?: "unknown") } catch (_: Exception) {}
                try { trace?.stop() } catch (_: Exception) {}
            }
    }

    fun startAdvertisingWithCode(name: String, code: String) {
        localName = name
        localCode = code
        startAdvertising(name)
    }

    fun startDiscovery() {
        _status.value = "Discovering..."
        FirebaseCrashlyticsUtils.log("Nearby startDiscovery")
        val trace = FirebasePerfUtils.startTrace("nearby_start_discovery")
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                _status.value = "Discovering nearby cruisers..."
                trace?.putMetric("success", 1)
                try { trace?.stop() } catch (_: Exception) {}
            }
            .addOnFailureListener { e ->
                _status.value = "Discovery failed: ${e.message}"
                FirebaseCrashlyticsUtils.recordException(e)
                trace?.putMetric("error", 1)
                try { trace?.stop() } catch (_: Exception) {}
            }
    }

    fun stopAdvertising() { connectionsClient.stopAdvertising(); _status.value = "Stopped advertising" }
    fun stopDiscovery() { connectionsClient.stopDiscovery(); _status.value = "Stopped discovery" }
    fun stopAll() { connectionsClient.stopAllEndpoints(); stopAdvertising(); stopDiscovery(); _discovered.value = emptyList(); _connected.value = emptyList(); _status.value = "Stopped all" }

    fun requestConnection(endpointId: String) {
        val advertiseName = if (localCode.isNotBlank()) "$localName|$localCode" else localName
        connectionsClient.requestConnection(advertiseName, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener { _status.value = "Requesting connection..." }
            .addOnFailureListener { e -> _status.value = "Request failed: ${e.message}" }
    }

    // Reliable send - returns true if at least one endpoint was targeted
    fun sendWireMessage(wire: WireMessage): Boolean {
        return sendWireMessageTo(wire, null)
    }

    fun sendWireMessageTo(wire: WireMessage, targetCode: String?): Boolean {
        val trace = FirebasePerfUtils.startTrace("nearby_send_wire")
        trace?.putAttribute("type", wire.type)
        trace?.putAttribute("target", targetCode ?: "broadcast")
        try {
            val targets = if (targetCode == null) {
                _connected.value
            } else {
                _connected.value.filter { it.code == targetCode }
            }
            if (targets.isEmpty()) {
                trace?.putMetric("no_target", 1)
                return false
            }
            try {
                val bytes = json.encodeToString(wire).toByteArray()
                val p = Payload.fromBytes(bytes)
                targets.forEach { endpoint ->
                    connectionsClient.sendPayload(endpoint.id, p)
                }
                FirebaseCrashlyticsUtils.log("Sent wire ${wire.type} ${wire.messageId} to ${targets.size} peers")
                trace?.putMetric("sent", 1)
                trace?.putMetric("target_count", targets.size.toLong())
                return true
            } catch (e: Exception) {
                _status.value = "Send failed: ${e.message}"
                FirebaseCrashlyticsUtils.recordException(e)
                trace?.putMetric("error", 1)
                return false
            }
        } finally {
            try { trace?.stop() } catch (_: Exception) {}
        }
    }

    fun sendChatWithId(messageId: String, sender: String, text: String, timestamp: Long, senderCode: String? = localCode, targetCode: String? = null, targetName: String? = null): Boolean {
        val wire = WireMessage(type = "CHAT", messageId = messageId, sender = sender, text = text, timestamp = timestamp, senderCode = senderCode, targetCode = targetCode, targetName = targetName)
        val sent = if (targetCode != null) sendWireMessageTo(wire, targetCode) else sendWireMessage(wire)
        if (sent) {
            _messages.value = _messages.value + ChatPayloadLegacy(sender = "You: $sender", text = text, timestamp = timestamp)
        }
        return sent
    }

    fun sendDeliveredReceipt(refId: String): Boolean {
        val wire = WireMessage(type = "DELIVERED", messageId = "delivered-${System.currentTimeMillis()}-${(0..9999).random()}", sender = localName, text = "", timestamp = System.currentTimeMillis(), refId = refId, senderCode = localCode)
        return sendWireMessage(wire)
    }

    fun sendReadReceipt(refId: String): Boolean {
        val wire = WireMessage(type = "READ", messageId = "read-${System.currentTimeMillis()}-${(0..9999).random()}", sender = localName, text = "", timestamp = System.currentTimeMillis(), refId = refId, senderCode = localCode)
        return sendWireMessage(wire)
    }

    // legacy
    fun sendMessage(text: String) {
        // for backwards compat where no id tracked; will not have receipt
        sendChatWithId(java.util.UUID.randomUUID().toString(), localName, text, System.currentTimeMillis())
    }

    fun addLocalMessage(text: String) {
        _messages.value = _messages.value + ChatPayloadLegacy(sender = "You: $localName", text = text)
    }

    fun getConnectedIds(): List<String> = _connected.value.map { it.id }
}
