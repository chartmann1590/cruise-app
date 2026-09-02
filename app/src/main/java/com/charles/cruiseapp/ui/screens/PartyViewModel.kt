package com.charles.cruiseapp.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.data.hotspot.HotspotChatService
import com.charles.cruiseapp.data.hotspot.HotspotState
import com.charles.cruiseapp.data.local.Message
import com.charles.cruiseapp.data.local.PartyMember
import com.charles.cruiseapp.data.nearby.NearbyManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class PartyViewModel(app: Application) : AndroidViewModel(app) {
    private val cruiseApp = app as CruiseApplication
    private val repo = cruiseApp.partyChatRepository
    private val db = cruiseApp.database

    val members: StateFlow<List<PartyMember>> = repo.members.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val localMessages: StateFlow<List<Message>> = repo.localMessages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyStatus: StateFlow<String> = repo.nearbyStatus
    val discovered: StateFlow<List<NearbyManager.DiscoveredEndpoint>> = repo.discovered
    val connected: StateFlow<List<NearbyManager.ConnectedEndpoint>> = repo.connected
    val nearbyMessages = repo.nearby.messages

    // Expose NearbyManager directly so PartyScreen can call startAdvertising/startDiscovery etc without changes
    val nearby: NearbyManager get() = repo.nearby

    // Hotspot state for Phase 6 UI (exposed via CruiseApplication hotspotController)
    val hotspotState: StateFlow<HotspotState> = cruiseApp.hotspotController.state
    val hotspotGuestCount: StateFlow<Int> = cruiseApp.hotspotController.guestCount

    fun getSelfCode(): String = repo.getSelfCode()
    fun getSelfName(): String = repo.getSelfName()
    fun setSelfName(name: String) {
        viewModelScope.launch { repo.setSelfNameSuspend(name) }
    }

    fun getQrData(): String = repo.getQrData()
    fun getQrDisplayString(): String = repo.getQrData()

    fun sendLocalMessage(text: String) {
        viewModelScope.launch { repo.sendLocalMessage(text) }
    }

    fun sendToMember(text: String, target: PartyMember) {
        viewModelScope.launch { repo.sendToMember(text, target) }
    }

    fun markAllDeliveredAsRead() {
        viewModelScope.launch { repo.markAllDeliveredAsRead() }
    }

    fun clearMessages() {
        viewModelScope.launch { repo.clearMessages() }
    }

    fun addMember(name: String) {
        viewModelScope.launch {
            val member = PartyMember(displayName = name, code = UUID.randomUUID().toString())
            db.partyMemberDao().insert(member)
        }
    }

    fun addMemberWithCode(name: String, code: String) {
        viewModelScope.launch {
            val existing = db.partyMemberDao().getByCode(code)
            if (existing != null) {
                if (existing.displayName != name) {
                    db.partyMemberDao().insert(existing.copy(displayName = name))
                }
            } else {
                db.partyMemberDao().insert(PartyMember(displayName = name, code = code))
            }
        }
    }

    fun removeMember(m: PartyMember) {
        viewModelScope.launch { db.partyMemberDao().delete(m) }
    }

    // Phase 6: Hotspot chat controls
    fun startGuestChat(context: Context) {
        val intent = Intent(context, HotspotChatService::class.java).apply { action = HotspotChatService.ACTION_START }
        // Use startForegroundService on O+ as required for foreground services
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopGuestChat(context: Context) {
        val intent = Intent(context, HotspotChatService::class.java).apply { action = HotspotChatService.ACTION_STOP }
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT shutdown repo – it outlives this ViewModel for service use
        // Only clear the wire callback if needed? Repo owns it for its entire lifecycle.
    }
}
