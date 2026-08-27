package com.charles.cruiseapp.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.data.local.Message
import com.charles.cruiseapp.data.local.PartyMember
import com.charles.cruiseapp.data.nearby.NearbyManager
import com.charles.cruiseapp.data.nearby.WireMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class PartyViewModel(app: Application): AndroidViewModel(app){
    private val db = (app as CruiseApplication).database
    val nearby = NearbyManager(app.applicationContext)
    private val prefs = app.getSharedPreferences("cruise_party_prefs", Context.MODE_PRIVATE)

    val members: StateFlow<List<PartyMember>> = db.partyMemberDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val localMessages: StateFlow<List<Message>> = db.messageDao().getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyStatus = nearby.status
    val discovered = nearby.discovered
    val connected = nearby.connected
    val nearbyMessages = nearby.messages

    private var retryJob: Job? = null

    init {
        // ensure self has code and sync to nearby
        viewModelScope.launch {
            val self = ensureSelfMember()
            nearby.setSelfInfo(self.displayName.ifBlank { "Cruiser" }, self.code)
        }
        nearby.onWireReceived = { wire, endpointId ->
            viewModelScope.launch {
                handleIncomingWire(wire, endpointId)
            }
        }
        startRetryLoop()
        viewModelScope.launch {
            nearby.connected.collect {
                if (it.isNotEmpty()) {
                    // update endpointId mapping for party members by code
                    for (c in it) {
                        c.code?.let { code ->
                            val member = db.partyMemberDao().getByCode(code)
                            if (member != null) {
                                db.partyMemberDao().updateEndpoint(code, c.id)
                            }
                        }
                    }
                    flushPending()
                }
            }
        }
        // also watch discovered to auto-add party members? no, QR is explicit
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

    fun setSelfName(name: String) {
        prefs.edit().putString("self_name", name).apply()
        nearby.localName = name
        viewModelScope.launch {
            val self = db.partyMemberDao().getSelf()
            val code = getSelfCode()
            if (self == null) {
                db.partyMemberDao().insert(PartyMember(displayName = name, isSelf = true, code = code))
            } else {
                db.partyMemberDao().insert(self.copy(displayName = name, code = code))
            }
            nearby.setSelfInfo(name, code)
        }
    }

    private suspend fun ensureSelfMember(): PartyMember {
        val code = getSelfCode()
        var self = db.partyMemberDao().getSelf()
        if (self == null) {
            val name = getSelfName().ifBlank { "Cruiser" }
            self = PartyMember(displayName = name, isSelf = true, code = code)
            db.partyMemberDao().insert(self)
            // re-fetch to get id
            self = db.partyMemberDao().getSelf() ?: self
        } else if (self.code.isBlank()) {
            self = self.copy(code = code)
            db.partyMemberDao().insert(self)
        }
        // sync name from prefs if exists
        val prefName = getSelfName()
        if (prefName.isNotBlank() && self.displayName != prefName) {
            self = self.copy(displayName = prefName)
            db.partyMemberDao().insert(self)
        }
        return self
    }

    fun getQrData(): String {
        val code = getSelfCode()
        val name = getSelfName().ifBlank { nearby.localName.ifBlank { "Cruiser" } }
        // Use pipe format for compactness + JSON fallback
        // We'll create JSON for robustness
        val j = JSONObject()
        j.put("n", name)
        j.put("c", code)
        return j.toString()
    }

    fun getQrDisplayString(): String = getQrData()

    private suspend fun handleIncomingWire(wire: WireMessage, endpointId: String) {
        // Filter by targetCode if present: only process if for us or broadcast
        val selfCode = getSelfCode()
        if (wire.targetCode != null && wire.targetCode != selfCode) {
            // not for us, ignore
            return
        }
        // also ignore our own messages echoed? check senderCode == selfCode and isFromSelf? but we already filtered outgoing via isFromSelf check
        when (wire.type) {
            "CHAT" -> {
                // Avoid duplicate
                val existing = db.messageDao().getByClientId(wire.messageId)
                if (existing == null) {
                    // Determine sender name: use wire.sender
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
                    // update party member endpoint mapping if senderCode known
                    wire.senderCode?.let { sc ->
                        val member = db.partyMemberDao().getByCode(sc)
                        if (member == null && wire.sender.isNotBlank()) {
                            // optionally auto-add unknown sender as party member? No, QR is explicit, but we can at least track.
                            // For now, don't auto-add, but update discovered mapping already done.
                        } else if (member != null) {
                            db.partyMemberDao().updateEndpoint(sc, endpointId)
                        }
                    }
                    nearby.sendDeliveredReceipt(wire.messageId)
                    delay(1200)
                    val inserted = db.messageDao().getByClientId(wire.messageId)
                    if (inserted != null) {
                        db.messageDao().update(inserted.copy(status = "READ"))
                    }
                    nearby.sendReadReceipt(wire.messageId)
                } else {
                    nearby.sendDeliveredReceipt(wire.messageId)
                }
            }
            "DELIVERED" -> {
                val ref = wire.refId ?: return
                val original = db.messageDao().getByClientId(ref)
                if (original != null && original.status != "READ") {
                    db.messageDao().updateStatus(ref, "DELIVERED")
                }
            }
            "READ" -> {
                val ref = wire.refId ?: return
                val original = db.messageDao().getByClientId(ref)
                if (original != null) {
                    db.messageDao().updateStatus(ref, "READ")
                }
            }
        }
    }

    fun addMember(name: String){
        viewModelScope.launch {
            val member = PartyMember(displayName=name, code = UUID.randomUUID().toString())
            db.partyMemberDao().insert(member)
        }
    }

    fun addMemberWithCode(name: String, code: String){
        viewModelScope.launch {
            val existing = db.partyMemberDao().getByCode(code)
            if (existing != null) {
                // update name if changed
                if (existing.displayName != name) {
                    db.partyMemberDao().insert(existing.copy(displayName = name))
                }
            } else {
                db.partyMemberDao().insert(PartyMember(displayName=name, code=code))
            }
        }
    }

    fun removeMember(m: PartyMember){
        viewModelScope.launch { db.partyMemberDao().delete(m) }
    }

    fun sendLocalMessage(text: String){
        if (text.isBlank()) return
        viewModelScope.launch {
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
            attemptSend(msg)
        }
    }

    fun sendToMember(text: String, target: PartyMember){
        if (text.isBlank()) return
        viewModelScope.launch {
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
            attemptSend(msg)
        }
    }

    private suspend fun attemptSend(msg: Message) {
        val senderCode = getSelfCode()
        val sent = nearby.sendChatWithId(msg.clientMessageId, msg.senderName, msg.text, msg.timestamp, senderCode, msg.targetCode, msg.targetName)
        if (sent) {
            db.messageDao().updateStatus(msg.clientMessageId, "SENT")
        } else {
            db.messageDao().incrementRetry(msg.clientMessageId, "PENDING")
        }
    }

    private suspend fun flushPending() {
        val pending = db.messageDao().getPending()
        for (msg in pending) {
            if (msg.isFromSelf) {
                // For targeted messages, only flush if target is connected or broadcast and any connected
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
        retryJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (nearby.connected.value.isNotEmpty()) {
                    val pending = db.messageDao().getPending()
                    if (pending.isNotEmpty()) {
                        for (msg in pending) {
                            if (msg.retryCount > 100) continue
                            // only retry if target is available or broadcast
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

    fun markAllDeliveredAsRead() {
        viewModelScope.launch {
            val delivered = localMessages.value.filter { !it.isFromSelf && it.status == "DELIVERED" }
            for (msg in delivered) {
                db.messageDao().updateStatus(msg.clientMessageId, "READ")
                nearby.sendReadReceipt(msg.clientMessageId)
            }
        }
    }

    fun clearMessages(){
        viewModelScope.launch { db.messageDao().clearAll() }
    }

    override fun onCleared() {
        super.onCleared()
        retryJob?.cancel()
        nearby.onWireReceived = null
    }
}
