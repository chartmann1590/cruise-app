package com.charles.cruiseapp.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.data.hotspot.HotspotState
import com.charles.cruiseapp.data.local.Message
import com.charles.cruiseapp.data.local.PartyMember
import com.charles.cruiseapp.util.generateQrBitmap
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PartyScreen(
    partyVm: PartyViewModel,
    onBack:()->Unit,
    onNavigateToHome: (() -> Unit)? = null,
    onNavigateToPorts: (() -> Unit)? = null
){
    val members by partyVm.members.collectAsState()
    val localMessages by partyVm.localMessages.collectAsState()
    val status by partyVm.nearbyStatus.collectAsState()
    val discovered by partyVm.discovered.collectAsState()
    val connected by partyVm.connected.collectAsState()
    val hotspotState by partyVm.hotspotState.collectAsState()
    val hotspotGuestCount by partyVm.hotspotGuestCount.collectAsState()

    var displayName by remember { mutableStateOf(partyVm.getSelfName()) }
    var chatText by remember { mutableStateOf("")}
    var selectedRecipient by remember { mutableStateOf<PartyMember?>(null) }
    var showMyQr by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Collapsible state – remember across recompositions & config changes
    var nearbyExpanded by rememberSaveable { mutableStateOf(false) }
    var guestExpanded by rememberSaveable { mutableStateOf(true) }
    var membersExpanded by rememberSaveable { mutableStateOf(true) }
    var debugExpanded by rememberSaveable { mutableStateOf(false) }

    // Auto-expand nearby when something interesting happens (first discovery/connection, or permission missing)
    LaunchedEffect(connected.size, discovered.size) {
        if ((connected.isNotEmpty() || discovered.isNotEmpty()) && !nearbyExpanded) {
            // keep collapsed by default to reduce clutter – only auto-expand once if user hasn't manually opened?
            // Don't force; leave as user left it unless it's still in initial false and now has content.
        }
    }

    LaunchedEffect(displayName){
        if (displayName.isNotBlank()) partyVm.setSelfName(displayName)
    }
    LaunchedEffect(showMyQr, displayName){
        if (showMyQr) {
            val data = partyVm.getQrData()
            qrBitmap = generateQrBitmap(data, 600)
        }
    }
    LaunchedEffect(localMessages){
        if (localMessages.any { !it.isFromSelf && it.status == "DELIVERED" }) {
            kotlinx.coroutines.delay(800)
            partyVm.markAllDeliveredAsRead()
        }
    }
    LaunchedEffect(localMessages.size){
        if (localMessages.isNotEmpty()) {
            try {
                val target = 4 + localMessages.size // approx header offset
                listState.animateScrollToItem(target)
            } catch (_: Exception) {}
        }
    }

    val perms = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
        rememberMultiplePermissionsState(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES))
    } else {
        rememberMultiplePermissionsState(listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    val cameraPerms = rememberMultiplePermissionsState(listOf(Manifest.permission.CAMERA))
    val hotspotPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberMultiplePermissionsState(listOf(Manifest.permission.NEARBY_WIFI_DEVICES))
    } else {
        rememberMultiplePermissionsState(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    val qrLauncher = rememberLauncherForActivityResult(contract = com.journeyapps.barcodescanner.ScanContract()) { result ->
        if (result.contents != null) {
            val content = result.contents
            try {
                val json = org.json.JSONObject(content)
                val name = json.optString("n", json.optString("name", ""))
                val code = json.optString("c", json.optString("code", ""))
                if (name.isNotBlank() && code.isNotBlank()) partyVm.addMemberWithCode(name, code)
                else if (content.contains("|")) {
                    val p = content.split("|")
                    if (p.size >= 2) partyVm.addMemberWithCode(p[0], p[1]) else partyVm.addMember(content)
                } else partyVm.addMember(content)
            } catch (_: Exception) {
                if (content.contains("|")) {
                    val p = content.split("|")
                    if (p.size >= 2) partyVm.addMemberWithCode(p[0], p[1]) else partyVm.addMember(content)
                } else partyVm.addMember(content)
            }
        }
    }

    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar={
            TopAppBar(
                title={
                    Column{
                        Text("Party Chat", fontWeight = FontWeight.Bold)
                        Text("${members.size} members • ${connected.size} connected${if(hotspotState is HotspotState.Running) " • $hotspotGuestCount guests" else ""}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon={ IconButton(onClick=onBack){ Icon(Icons.Default.ArrowBack,null) }},
                actions={
                    IconButton(onClick={ showMyQr = true }){ Icon(Icons.Default.QrCode, "My QR") }
                    IconButton(onClick={ partyVm.clearMessages() }){ Icon(Icons.Default.DeleteSweep, null) }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                BannerAd(modifier = Modifier.fillMaxWidth())
                Surface(
                    shadowElevation = 8.dp, tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        if (members.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("To:", style = MaterialTheme.typography.labelMedium)
                                FilterChip(
                                    selected = selectedRecipient == null,
                                    onClick = { selectedRecipient = null },
                                    label = { Text("Everyone") },
                                    leadingIcon = { Icon(Icons.Default.Group, null, Modifier.size(16.dp)) }
                                )
                                members.filter { !it.isSelf }.take(3).forEach { m ->
                                    FilterChip(
                                        selected = selectedRecipient?.id == m.id,
                                        onClick = { selectedRecipient = if (selectedRecipient?.id == m.id) null else m },
                                        label = { Text(m.displayName, maxLines = 1) },
                                        leadingIcon = { Box(Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center){ Text(m.displayName.take(1).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold) } }
                                    )
                                }
                                if (members.filter { !it.isSelf }.size > 3) {
                                    Text("+${members.filter { !it.isSelf }.size - 3}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)){
                            OutlinedTextField(
                                value=chatText, onValueChange={chatText=it},
                                placeholder={ Text(if (selectedRecipient == null) "Message everyone..." else "To ${selectedRecipient?.displayName}...")},
                                modifier=Modifier.weight(1f),
                                shape= RoundedCornerShape(24.dp),
                                maxLines = 4
                            )
                            FilledIconButton(
                                onClick={
                                    if(chatText.isNotBlank()){
                                        val target = selectedRecipient
                                        if (target == null) partyVm.sendLocalMessage(chatText)
                                        else partyVm.sendToMember(chatText, target)
                                        chatText=""
                                    }
                                },
                                modifier=Modifier.size(48.dp),
                                colors= IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ){
                                Icon(Icons.Default.Send, null, tint=Color.White)
                            }
                        }
                    }
                }
                NavigationBar(windowInsets = WindowInsets(0)) {
                    NavigationBarItem(
                        selected = false,
                        onClick = { if (onNavigateToHome != null) onNavigateToHome() else onBack() },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { onNavigateToPorts?.invoke() },
                        icon = { Icon(Icons.Default.Place, contentDescription = "Ports") },
                        label = { Text("Ports") }
                    )
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = { Icon(Icons.Default.Group, contentDescription = "Party") },
                        label = { Text("Party") }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.surface
    ){ pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState
        ){
            // Quick summary header – shows at a glance, with collapse toggles hint
            item {
                Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.6f)), shape= RoundedCornerShape(12.dp)){
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween){
                        Column(Modifier.weight(1f)){
                            Text("Connections & members", style=MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("${if(connected.isNotEmpty()) "Bluetooth: ${connected.size} connected" else status.take(36)} • ${members.size} in party${if(hotspotState is HotspotState.Running) " • hotspot on" else ""}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1)
                        }
                        if (hotspotState is HotspotState.Running) Badge(containerColor = Color(0xFF2E7D32)){ Text("${hotspotGuestCount} guests") }
                    }
                }
            }

            // Bluetooth Nearby – collapsible (default collapsed to reduce clutter)
            item {
                ExpandableSection(
                    title = if(connected.isNotEmpty()) "Bluetooth • Connected" else "Bluetooth Nearby",
                    subtitle = if(connected.isNotEmpty()) "${connected.size} peer${if(connected.size==1) "" else "s"} • ${discovered.size} found" else if(discovered.isNotEmpty()) "${discovered.size} found • tap to connect" else status,
                    icon = if(connected.isNotEmpty()) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    badge = if(connected.isNotEmpty()) "${connected.size}" else if(discovered.isNotEmpty()) "${discovered.size}" else null,
                    defaultExpanded = nearbyExpanded,
                    onExpandedChange = { nearbyExpanded = it },
                    headerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    // Display name field
                    OutlinedTextField(
                        value=displayName, onValueChange={displayName=it},
                        label={ Text("Your display name")},
                        placeholder={ Text("Enter your real name")},
                        modifier=Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, null)},
                        shape = RoundedCornerShape(12.dp)
                    )
                    if(!perms.allPermissionsGranted){
                        Button(onClick={ perms.launchMultiplePermissionRequest()}, modifier=Modifier.fillMaxWidth(), colors= ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)){ Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(8.dp)); Text("Grant Bluetooth Permissions")}
                        Text("Needed: BLUETOOTH_SCAN / ADVERTISE / CONNECT", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.error)
                    } else {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
                            Button(onClick={ if(displayName.isNotBlank()) partyVm.nearby.startAdvertising(displayName) }, modifier=Modifier.weight(1f), enabled=displayName.isNotBlank()){
                                Icon(Icons.Default.Campaign,null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Advertise")
                            }
                            Button(onClick={ partyVm.nearby.startDiscovery() }, modifier=Modifier.weight(1f)){
                                Icon(Icons.Default.Radar,null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Discover")
                            }
                            FilledTonalIconButton(onClick={ partyVm.nearby.stopAll() }){ Icon(Icons.Default.Stop, null, Modifier.size(16.dp)) }
                        }
                    }
                    if(discovered.isNotEmpty()){
                        Text("Nearby cruisers — tap Connect", style=MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        discovered.take(6).forEach{ ep ->
                            Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation= CardDefaults.cardElevation(1.dp)){
                                Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier=Modifier.weight(1f)){
                                        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center){
                                            Text(ep.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color=MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)){
                                            Text(ep.name, style=MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines=1)
                                            Text(ep.id.take(10), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1)
                                            ep.code?.let { Text("ID: ${it.take(8)}…", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary) }
                                        }
                                    }
                                    FilledTonalButton(onClick={ partyVm.nearby.requestConnection(ep.id) }){ Text("Connect") }
                                }
                            }
                        }
                        if (discovered.size > 6) Text("+ ${discovered.size - 6} more", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(connected.isNotEmpty()){
                        Text("Connected peers", style=MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
                            connected.forEach{ c ->
                                AssistChip(onClick={}, label={ Text(c.name)}, leadingIcon={ Icon(Icons.Default.CheckCircle,null, Modifier.size(16.dp), tint=Color(0xFF2E7D32))}, colors= AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface))
                            }
                        }
                    }
                }
            }

            // Guest Wi-Fi Chat – collapsible but default expanded when idle/running so primary action is visible
            item {
                val isRunning = hotspotState is HotspotState.Running
                val isStarting = hotspotState is HotspotState.Starting
                val subtitle = when(hotspotState){
                    is HotspotState.Running -> "$hotspotGuestCount guest${if(hotspotGuestCount==1) "" else "s"} • tap to ${if(isRunning) "manage" else "start"}"
                    is HotspotState.Starting -> "Starting hotspot…"
                    is HotspotState.Error -> "Failed — tap to retry"
                    else -> "Browser guests, no app needed"
                }
                ExpandableSection(
                    title = "Guest Wi-Fi Chat",
                    subtitle = subtitle,
                    icon = Icons.Default.WifiTethering,
                    badge = when {
                        isRunning && hotspotGuestCount>0 -> "$hotspotGuestCount"
                        isRunning -> "ON"
                        isStarting -> "…"
                        else -> null
                    },
                    defaultExpanded = guestExpanded,
                    onExpandedChange = { guestExpanded = it },
                    headerColor = if(isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    GuestWifiChatContent(
                        hotspotState = hotspotState,
                        guestCount = hotspotGuestCount,
                        hotspotPermsGranted = hotspotPerms.allPermissionsGranted,
                        onRequestPerms = { hotspotPerms.launchMultiplePermissionRequest() },
                        onStart = { partyVm.startGuestChat(context) },
                        onStop = { partyVm.stopGuestChat(context) }
                    )
                }
            }

            // Party members – collapsible
            item {
                ExpandableSection(
                    title = "Party Members",
                    subtitle = if(members.isEmpty()) "No members yet — scan a QR" else "${members.size} member${if(members.size==1) "" else "s"} • tap chip for private chat",
                    icon = Icons.Default.Group,
                    badge = "${members.size}",
                    defaultExpanded = membersExpanded,
                    onExpandedChange = { membersExpanded = it }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
                        FilledTonalButton(onClick={ showMyQr = true }, modifier=Modifier.weight(1f), contentPadding = PaddingValues(vertical=8.dp)){
                            Icon(Icons.Default.QrCode, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("My QR")
                        }
                        FilledTonalButton(onClick={
                            if (cameraPerms.allPermissionsGranted) qrLauncher.launch(com.journeyapps.barcodescanner.ScanOptions().setPrompt("Scan party QR").setBeepEnabled(true).setOrientationLocked(false))
                            else cameraPerms.launchMultiplePermissionRequest()
                        }, modifier=Modifier.weight(1f), contentPadding = PaddingValues(vertical=8.dp)){
                            Icon(Icons.Default.QrCodeScanner, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Scan")
                        }
                    }
                    if (!cameraPerms.allPermissionsGranted) {
                        Text("Camera permission needed to scan QR", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.error)
                    }
                    Text("Add by scanning QR — no typing, guarantees correct identity for direct messages.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
                        var newMember by remember { mutableStateOf("") }
                        OutlinedTextField(value=newMember, onValueChange={newMember=it}, label={ Text("Add manually")}, modifier=Modifier.weight(1f), placeholder={ Text("Enter real name")}, singleLine = true, shape= RoundedCornerShape(12.dp))
                        Button(onClick={ if(newMember.isNotBlank()){ partyVm.addMember(newMember); newMember="" }}, shape= RoundedCornerShape(12.dp)){
                            Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                        }
                    }
                    if(members.isEmpty()){
                        Box(Modifier.fillMaxWidth().padding(vertical=8.dp), contentAlignment = Alignment.Center){
                            Text("No members yet. Show your QR to shipmates to get scanned.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ){
                            members.forEach { m ->
                                val isSelected = selectedRecipient?.id == m.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedRecipient = if (isSelected) null else m },
                                    label = { Text(m.displayName) },
                                    leadingIcon = {
                                        Box(Modifier.size(22.dp).clip(CircleShape).background(if(m.isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center){
                                            Text(m.displayName.take(1).uppercase(), fontSize=10.sp, fontWeight=FontWeight.Bold, color= if(m.isSelf) Color.White else MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    },
                                    trailingIcon = {
                                        if (!m.isSelf) {
                                            IconButton(onClick={ partyVm.removeMember(m)}, modifier=Modifier.size(20.dp)){ Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)
                                )
                            }
                        }
                        if (selectedRecipient != null) {
                            Card(colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.6f)), shape=RoundedCornerShape(8.dp)) {
                                Text("Private: messages to ${selectedRecipient?.displayName} only. Tap Everyone to broadcast.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onPrimaryContainer, modifier=Modifier.padding(8.dp))
                            }
                        } else {
                            Text("Broadcasting to everyone nearby — or select a member chip to message privately.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Tools & Help – moved ABOVE chat so chat is at the bottom, directly above the composer
            item {
                ExpandableSection(
                    title = "Tools",
                    subtitle = "Test & debug",
                    icon = Icons.Default.Build,
                    badge = null,
                    defaultExpanded = debugExpanded,
                    onExpandedChange = { debugExpanded = it }
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick = { partyVm.sendLocalMessage("Hello everyone! Test broadcast") }, modifier = Modifier.weight(1f)) { Text("Broadcast Test", maxLines=1) }
                        Button(onClick = { val t = members.firstOrNull { !it.isSelf }; if (t != null) partyVm.sendToMember("Hi private test!", t) else partyVm.sendLocalMessage("Private test - add member first") }, modifier = Modifier.weight(1f)) { Text("Private Test", maxLines=1) }
                    }
                    OutlinedButton(onClick={ partyVm.clearMessages() }, modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Clear all messages")
                    }
                }
            }

            // Chat thread header – always visible (not collapsible, this is the main content) – now at BOTTOM, just above composer
            item {
                Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape= RoundedCornerShape(12.dp)){
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween){
                        Row(verticalAlignment = Alignment.CenterVertically){
                            Icon(Icons.Default.ChatBubble, null, tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Chat", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primary){ Text("${localMessages.size}") }
                            if (selectedRecipient != null) {
                                Spacer(Modifier.width(6.dp))
                                AssistChip(onClick={ selectedRecipient = null }, label={ Text("${selectedRecipient?.displayName}") }, leadingIcon={ Icon(Icons.Default.Person, null, Modifier.size(14.dp))})
                            }
                        }
                        Text("Retry until delivered", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Messages – now at the very bottom, directly above the composer input
            if(localMessages.isEmpty()){
                item {
                    Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape= RoundedCornerShape(16.dp)){
                        Box(Modifier.fillMaxWidth().height(160.dp).padding(16.dp), contentAlignment = Alignment.Center){
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)){
                                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center){
                                    Icon(Icons.Default.Chat, null, Modifier.size(24.dp), tint=MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Text("No messages yet", fontWeight = FontWeight.Bold)
                                Text("Send a message — it'll deliver as soon as someone's in range.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(horizontal=16.dp))
                            }
                        }
                    }
                }
            } else {
                val filtered = if (selectedRecipient == null) localMessages else localMessages.filter {
                    (it.isFromSelf && (it.targetCode == selectedRecipient?.code || it.targetCode == null)) ||
                    (!it.isFromSelf && it.senderName == selectedRecipient?.displayName) ||
                    it.targetCode == null
                }
                val toShow = if (selectedRecipient == null) localMessages else filtered
                items(toShow, key={ it.clientMessageId }){ msg ->
                    MessageBubble(msg = msg, timeFmt = timeFmt)
                }
            }

            // Bottom spacer for better scroll
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showMyQr) {
        val data = partyVm.getQrData()
        val bmp = remember(data) { generateQrBitmap(data, 700) }
        AlertDialog(
            onDismissRequest = { showMyQr = false },
            title = { Text("My Party QR") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()){
                    Text("Let others scan this to add you instantly. Works offline.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    if (bmp != null) {
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                    } else {
                        Text("Could not generate QR", color=MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(displayName.ifBlank { partyVm.getSelfName().ifBlank { "Cruiser" } }, fontWeight = FontWeight.Bold)
                    Text("ID: ${partyVm.getSelfCode().take(8)}…", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(data, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            },
            confirmButton = { TextButton(onClick = { showMyQr = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String? = null,
    defaultExpanded: Boolean = true,
    onExpandedChange: ((Boolean)->Unit)? = null,
    headerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    // Keep external state in sync if callback provided
    LaunchedEffect(defaultExpanded) { expanded = defaultExpanded }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(if(expanded) 2.dp else 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded; onExpandedChange?.invoke(expanded) }
                    .background(headerColor ?: Color.Transparent)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center){
                    Icon(icon, null, tint=MaterialTheme.colorScheme.onSecondaryContainer, modifier=Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){
                    Text(title, style=MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines=1)
                    Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1)
                }
                if (badge != null) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary, modifier=Modifier.padding(end=8.dp)) { Text(badge) }
                }
                Icon(
                    if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if(expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(Modifier.padding(horizontal=14.dp).padding(bottom=14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(modifier=Modifier.padding(bottom=6.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun GuestWifiChatContent(
    hotspotState: HotspotState,
    guestCount: Int,
    hotspotPermsGranted: Boolean,
    onRequestPerms: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    when (hotspotState) {
        is HotspotState.Idle -> {
            Text("Let anyone nearby join the chat from their browser — no app install needed. Your phone creates a temporary Wi-Fi network (no internet) and hosts the chat page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!hotspotPermsGranted) {
                Button(
                    onClick = onRequestPerms,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(8.dp)); Text("Grant Wi-Fi Permission") }
                Text("Needed to create the guest Wi-Fi network (Nearby Wi-Fi / Location).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.WifiTethering, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Start Guest Chat")
                }
                Text("Guests will see 2 QR codes after you start: one to join Wi-Fi, one to open the chat page.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is HotspotState.Starting -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Starting hotspot…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Starting…") }
        }
        is HotspotState.Running -> {
            val running = hotspotState as HotspotState.Running
            val wifiQrText = "WIFI:T:WPA;S:${escapeWifiString(running.ssid)};P:${escapeWifiString(running.password)};;"
            val urlText = "http://${running.hostIp}:${running.port}/"
            val wifiQr = remember(wifiQrText) { generateQrBitmap(wifiQrText, 500) }
            val urlQr = remember(urlText) { generateQrBitmap(urlText, 500) }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("$guestCount guest${if (guestCount==1) "" else "s"} connected", fontWeight = FontWeight.Bold)
                    }
                    Text(urlText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines=1, modifier=Modifier.weight(1f).padding(start=8.dp))
                }
            }
            Text("Two steps for guests (order matters!):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Step 1 — Join Wi-Fi", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Network:", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(6.dp))
                                Text(running.ssid, fontWeight = FontWeight.Bold, modifier=Modifier.weight(1f, fill=false))
                                IconButton(onClick = { copyToClipboard(context, "Wi-Fi name", running.ssid) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Password:", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(6.dp))
                                Text(running.password.ifBlank { "(none)" }, fontWeight = FontWeight.Bold, maxLines = 1, modifier=Modifier.weight(1f, fill=false))
                                IconButton(onClick = { copyToClipboard(context, "Wi-Fi password", running.password) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp))
                                }
                            }
                            Text("Scan with camera to join automatically, or enter manually in Wi-Fi settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (wifiQr != null) {
                            Image(bitmap = wifiQr.asImageBitmap(), contentDescription = "Wi-Fi QR", modifier = Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp))
                        }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Step 2 — Open the chat page", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(urlText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { copyToClipboard(context, "Chat URL", urlText) }, contentPadding = PaddingValues(4.dp)) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Copy URL")
                            }
                            Text("Guests open this in their browser after joining Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (urlQr != null) {
                            Image(bitmap = urlQr.asImageBitmap(), contentDescription = "URL QR", modifier = Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp))
                        }
                    }
                }
            }
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("Stop Guest Chat") }
            Text("Guests will be disconnected when you stop. The hotspot and web chat keep running even if you leave this screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is HotspotState.Error -> {
            val reason = (hotspotState as HotspotState.Error).reason
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Could not start guest chat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (!hotspotPermsGranted) {
                Button(onClick = onRequestPerms, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(8.dp)); Text("Grant Wi-Fi Permission")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Try Again")
                }
            }
        }
    }
}

private fun escapeWifiString(s: String): String {
    return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

@Composable
private fun MessageBubble(msg: Message, timeFmt: SimpleDateFormat){
    val isSelf = msg.isFromSelf
    val bubbleColor = if(isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if(isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = if(isSelf) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    Column(modifier=Modifier.fillMaxWidth(), horizontalAlignment = if(isSelf) Alignment.End else Alignment.Start){
        if(!isSelf){
            Row(verticalAlignment = Alignment.CenterVertically){
                Text(msg.senderName, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary, modifier=Modifier.padding(start=8.dp, bottom=2.dp))
                if (msg.targetName != null) {
                    Text(" → ${msg.targetName}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(start=4.dp, bottom=2.dp))
                }
            }
        } else if (msg.targetName != null) {
            Text("To ${msg.targetName}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary, modifier=Modifier.padding(end=8.dp, bottom=2.dp))
        }
        Box(
            modifier=Modifier
                .widthIn(max=280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal=14.dp, vertical=10.dp)
        ){
            Column{
                Text(msg.text, color=textColor, style=MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.align(Alignment.End)){
                    Text(timeFmt.format(Date(msg.timestamp)), style=MaterialTheme.typography.labelSmall, color= if(isSelf) textColor.copy(alpha=0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize=11.sp)
                    if(isSelf){
                        Spacer(Modifier.width(6.dp))
                        MessageStatusIcon(status = msg.status)
                    }
                }
            }
        }
        if(isSelf){
            val statusText = when(msg.status){
                "PENDING" -> "Queued • retrying..."
                "SENT" -> "Sent • waiting"
                "DELIVERED" -> "Delivered ✓✓"
                "READ" -> "Read ✓✓"
                else -> msg.status
            }
            val statusColor = when(msg.status){
                "READ" -> Color(0xFF2E7D32)
                "DELIVERED" -> Color(0xFF2E7D32)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(statusText, style=MaterialTheme.typography.labelSmall, color=statusColor, fontSize=10.sp, modifier=Modifier.padding(end=4.dp, top=2.dp))
        } else {
            if(msg.status == "READ"){
                Text("Read", style=MaterialTheme.typography.labelSmall, color=Color(0xFF2E7D32), fontSize=10.sp, modifier=Modifier.padding(start=4.dp, top=2.dp))
            } else if (msg.status == "DELIVERED") {
                Text("Delivered", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant, fontSize=10.sp, modifier=Modifier.padding(start=4.dp, top=2.dp))
            }
        }
    }
}

@Composable
private fun MessageStatusIcon(status: String){
    when(status){
        "PENDING" -> Icon(Icons.Default.Schedule, "pending", Modifier.size(14.dp), tint= Color(0xFF9E9E9E))
        "SENT" -> Icon(Icons.Default.Done, "sent", Modifier.size(14.dp), tint= Color(0xFFB0BEC5))
        "DELIVERED" -> Icon(Icons.Default.DoneAll, "delivered", Modifier.size(14.dp), tint= Color(0xFFB0BEC5))
        "READ" -> Icon(Icons.Default.DoneAll, "read", Modifier.size(14.dp), tint= Color(0xFF4FC3F7))
        else -> Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint= Color.Gray)
    }
}
