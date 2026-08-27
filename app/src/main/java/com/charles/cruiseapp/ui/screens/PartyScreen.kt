package com.charles.cruiseapp.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import com.charles.cruiseapp.data.local.Message
import com.charles.cruiseapp.data.local.PartyMember
import com.charles.cruiseapp.util.generateQrBitmap
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PartyScreen(partyVm: PartyViewModel, onBack:()->Unit){
    val members by partyVm.members.collectAsState()
    val localMessages by partyVm.localMessages.collectAsState()
    val status by partyVm.nearbyStatus.collectAsState()
    val discovered by partyVm.discovered.collectAsState()
    val connected by partyVm.connected.collectAsState()

    var displayName by remember { mutableStateOf(partyVm.getSelfName()) }
    var chatText by remember { mutableStateOf("")}
    var selectedRecipient by remember { mutableStateOf<com.charles.cruiseapp.data.local.PartyMember?>(null) }
    var showMyQr by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val listState = rememberLazyListState()

    // keep nearby and prefs in sync
    LaunchedEffect(displayName){
        if (displayName.isNotBlank()) {
            partyVm.setSelfName(displayName)
        }
    }
    // generate QR when needed
    LaunchedEffect(showMyQr, displayName){
        if (showMyQr) {
            val data = partyVm.getQrData()
            qrBitmap = generateQrBitmap(data, 600)
        }
    }
    // mark delivered as read when screen visible and messages exist
    LaunchedEffect(localMessages){
        if (localMessages.any { !it.isFromSelf && it.status == "DELIVERED" }) {
            kotlinx.coroutines.delay(800)
            partyVm.markAllDeliveredAsRead()
        }
    }
    // auto-scroll to bottom on new message
    LaunchedEffect(localMessages.size){
        if (localMessages.isNotEmpty()) {
            // scroll outer list to bottom (last message)
            // outer list size = headers (3) + messages + footer (1) => approximate
            // For simplicity, scroll to last index
            try {
                // estimate: 3 headers + messages +1 footer
                val target = 3 + localMessages.size
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

    val qrLauncher = rememberLauncherForActivityResult(contract = com.journeyapps.barcodescanner.ScanContract()) { result ->
        if (result.contents != null) {
            val content = result.contents
            try {
                val json = org.json.JSONObject(content)
                val name = json.optString("n", json.optString("name", ""))
                val code = json.optString("c", json.optString("code", ""))
                if (name.isNotBlank() && code.isNotBlank()) {
                    partyVm.addMemberWithCode(name, code)
                } else if (content.contains("|")) {
                    val p = content.split("|")
                    if (p.size >= 2) partyVm.addMemberWithCode(p[0], p[1]) else partyVm.addMember(content)
                } else {
                    partyVm.addMember(content)
                }
            } catch (_: Exception) {
                if (content.contains("|")) {
                    val p = content.split("|")
                    if (p.size >= 2) partyVm.addMemberWithCode(p[0], p[1]) else partyVm.addMember(content)
                } else {
                    partyVm.addMember(content)
                }
            }
        }
    }

    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar={
            TopAppBar(
                title={
                    Column{
                        Text("Party Chat", fontWeight = FontWeight.Bold)
                        Text("${members.size} members • ${connected.size} connected", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
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
            Surface(shadowElevation = 8.dp, tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
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
                            members.filter { !it.isSelf }.forEach { m ->
                                FilterChip(
                                    selected = selectedRecipient?.id == m.id,
                                    onClick = { selectedRecipient = if (selectedRecipient?.id == m.id) null else m },
                                    label = { Text(m.displayName, maxLines = 1) },
                                    leadingIcon = { Box(Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center){ Text(m.displayName.take(1).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold) } }
                                )
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
        },
        containerColor = MaterialTheme.colorScheme.surface
    ){ pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            state = listState
        ){
            // Connection card
            item {
                Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), elevation = CardDefaults.cardElevation(2.dp)){
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)){
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()){
                            Row(verticalAlignment = Alignment.CenterVertically){
                                Box(Modifier.size(36.dp).clip(CircleShape).background(if(connected.isNotEmpty()) Color(0xFF2E7D32) else Color(0xFF7A7A7A)), contentAlignment = Alignment.Center){
                                    Icon(if(connected.isNotEmpty()) Icons.Default.Wifi else Icons.Default.WifiOff, null, tint=Color.White, modifier=Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column{
                                    Text(if(connected.isNotEmpty()) "Connected • Offline mesh active" else "Offline • Bluetooth mesh", style=MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(status, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f), maxLines=1)
                                }
                            }
                            if (connected.isNotEmpty()) Badge(containerColor = Color(0xFF2E7D32)){ Text("${connected.size}") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)){
                            AssistChip(onClick={}, label={ Text("Found ${discovered.size}") }, leadingIcon={ Icon(Icons.Default.Search, null, Modifier.size(14.dp))}, colors= AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface))
                            AssistChip(onClick={}, label={ Text("Party ${members.size}") }, leadingIcon={ Icon(Icons.Default.Group, null, Modifier.size(14.dp))}, colors= AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface))
                            AssistChip(onClick={}, label={ Text(if(connected.isNotEmpty()) "Ready" else "Not connected") }, leadingIcon={ Icon(if(connected.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.CloudOff, null, Modifier.size(14.dp))})
                        }
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
                                OutlinedButton(onClick={ partyVm.nearby.stopAll() }){ Icon(Icons.Default.Stop, null, Modifier.size(16.dp)) }
                            }
                        }
                        if(discovered.isNotEmpty()){
                            Text("Nearby cruisers — tap Connect", style=MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            discovered.forEach{ ep ->
                                Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation= CardDefaults.cardElevation(1.dp)){
                                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                                        Row(verticalAlignment = Alignment.CenterVertically){
                                            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center){
                                                Text(ep.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color=MaterialTheme.colorScheme.onTertiaryContainer)
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column{
                                                Text(ep.name, style=MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                                Text(ep.id.take(10), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                                ep.code?.let { Text("ID: ${it.take(8)}…", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary) }
                                            }
                                        }
                                        FilledTonalButton(onClick={ partyVm.nearby.requestConnection(ep.id) }){ Text("Connect") }
                                    }
                                }
                            }
                        }
                        if(connected.isNotEmpty()){
                            Text("Connected peers", style=MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
                                connected.forEach{ c ->
                                    AssistChip(onClick={}, label={ Text(c.name)}, leadingIcon={ Icon(Icons.Default.CheckCircle,null, Modifier.size(16.dp), tint=Color(0xFF2E7D32))}, colors= AssistChipDefaults.assistChipColors(containerColor = Color.White))
                                }
                            }
                        }
                    }
                }
            }
            // Party members with QR
            item {
                Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)){
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)){
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()){
                            Text("Party members", style=MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)){
                                FilledTonalButton(onClick={ showMyQr = true }, contentPadding = PaddingValues(horizontal=10.dp, vertical=6.dp)){
                                    Icon(Icons.Default.QrCode, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("My QR")
                                }
                                FilledTonalButton(onClick={
                                    if (cameraPerms.allPermissionsGranted) qrLauncher.launch(com.journeyapps.barcodescanner.ScanOptions().setPrompt("Scan party QR").setBeepEnabled(true).setOrientationLocked(false))
                                    else cameraPerms.launchMultiplePermissionRequest()
                                }, contentPadding = PaddingValues(horizontal=10.dp, vertical=6.dp)){
                                    Icon(Icons.Default.QrCodeScanner, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Scan")
                                }
                            }
                        }
                        if (!cameraPerms.allPermissionsGranted) {
                            Text("Camera permission needed to scan QR", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.error)
                        }
                        Text("Add members by scanning their QR — no typing, and guarantees correct identity for direct messages.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
                            // keep manual add as fallback
                            var newMember by remember { mutableStateOf("") }
                            OutlinedTextField(value=newMember, onValueChange={newMember=it}, label={ Text("Add manually")}, modifier=Modifier.weight(1f), placeholder={ Text("Enter real name")}, singleLine = true, shape= RoundedCornerShape(12.dp))
                            Button(onClick={ if(newMember.isNotBlank()){ partyVm.addMember(newMember); newMember="" }}, shape= RoundedCornerShape(12.dp)){
                                Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add")
                            }
                        }
                        if(members.isEmpty()){
                            Box(Modifier.fillMaxWidth().padding(vertical=8.dp), contentAlignment = Alignment.Center){
                                Text("No members yet. Show your QR to shipmates to get scanned.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            // chips
                            androidx.compose.foundation.layout.FlowRow(
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
                                Text("Chatting privately with ${selectedRecipient?.displayName} — messages will be delivered only to them and retry until in range.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.primary)
                            } else {
                                Text("Broadcasting to everyone nearby — or select a member chip to message individually.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // Chat thread header
            item {
                Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape= RoundedCornerShape(12.dp)){
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween){
                        Row(verticalAlignment = Alignment.CenterVertically){
                            Icon(Icons.Default.ChatBubble, null, tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Chat thread", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primary){ Text("${localMessages.size}") }
                            if (selectedRecipient != null) {
                                Spacer(Modifier.width(8.dp))
                                AssistChip(onClick={ selectedRecipient = null }, label={ Text("with ${selectedRecipient?.displayName}") }, leadingIcon={ Icon(Icons.Default.Person, null, Modifier.size(14.dp))})
                            }
                        }
                        Text("Retry until delivered", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Messages
            if(localMessages.isEmpty()){
                item {
                    Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = Color(0xFFF6F8FA)), shape= RoundedCornerShape(16.dp)){
                        Box(Modifier.fillMaxWidth().height(180.dp).padding(16.dp), contentAlignment = Alignment.Center){
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)){
                                Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center){
                                    Icon(Icons.Default.Chat, null, Modifier.size(28.dp), tint=MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Text("No messages yet", fontWeight = FontWeight.Bold)
                                Text("Send a message — it queues as PENDING if no one is in range and retries every 5s until delivered. You'll see SENT → DELIVERED → READ.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                // filter by selected recipient? Show all but highlight target, or filter to only conversation with selected? For individual, show only messages to/from that person or broadcast.
                val filtered = if (selectedRecipient == null) localMessages else localMessages.filter {
                    (it.isFromSelf && (it.targetCode == selectedRecipient?.code || it.targetCode == null)) ||
                    (!it.isFromSelf && it.senderName == selectedRecipient?.displayName) ||
                    it.targetCode == null // also show broadcasts in individual view for context? maybe keep all
                }
                // For simplicity, show filtered: if selected, show only messages where target is that person or sender is that person, plus broadcasts
                val toShow = if (selectedRecipient == null) localMessages else filtered
                items(toShow, key={ it.clientMessageId }){ msg ->
                    MessageBubble(msg = msg, timeFmt = timeFmt)
                }
            }
            // Demo buttons for testing retry/delivery (real messages, not mock DB)
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick = { partyVm.sendLocalMessage("Hello everyone! Test broadcast") }, modifier = Modifier.weight(1f)) { Text("Broadcast Test") }
                    Button(onClick = { val t = members.firstOrNull { !it.isSelf }; if (t != null) partyVm.sendToMember("Hi private test!", t) else partyVm.sendLocalMessage("Private test - add member first") }, modifier = Modifier.weight(1f)) { Text("Private Test") }
                }
            }
            // How it works footer
            item {
                Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape= RoundedCornerShape(12.dp)){
                    Column(Modifier.padding(14.dp)){
                        Row(verticalAlignment = Alignment.CenterVertically){
                            Icon(Icons.Default.Info, null, tint=MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("How offline delivery works", style=MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("• Tap My QR to let shipmates scan you — no typing, and guarantees correct identity\n• Scan their QR to add them instantly\n• Select a member chip to message them privately — otherwise broadcasts to everyone\n• PENDING = queued (not in range) • SENT = sent, waiting • DELIVERED = on their phone • READ = they opened chat (blue ticks)\n• App retries every 5s until a peer connects — even if you close and reopen on the ship", style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    // QR Dialog
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
                "PENDING" -> "Queued • retrying until in range..."
                "SENT" -> "Sent • waiting for delivery"
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

@Composable
private fun FilledButton(onClick:()->Unit, content: @Composable RowScope.()->Unit){
    Button(onClick=onClick, shape= RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal=12.dp, vertical=8.dp), content=content)
}
