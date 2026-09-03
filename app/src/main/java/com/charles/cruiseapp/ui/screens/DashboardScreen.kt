package com.charles.cruiseapp.ui.screens

import com.charles.cruiseapp.ui.translation.TText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.data.local.Cruise
import com.charles.cruiseapp.data.local.PlannedEvent
import com.charles.cruiseapp.data.local.PortStop
import com.charles.cruiseapp.ui.components.EmptyState
import com.charles.cruiseapp.ui.components.GradientHeroBanner
import com.charles.cruiseapp.ui.components.PopIn
import com.charles.cruiseapp.util.formatDate
import com.charles.cruiseapp.util.formatTime
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    cruise: Cruise?,
    portsFlow: StateFlow<List<PortStop>>,
    eventsFlow: StateFlow<List<PlannedEvent>>,
    upcomingFlow: StateFlow<List<PlannedEvent>>,
    onNavigateToSetup: () -> Unit,
    onNavigateToDay: (Long) -> Unit,
    onNavigateToPorts: () -> Unit,
    onNavigateToParty: () -> Unit,
    onDeleteEvent: (PlannedEvent)->Unit,
    onAddEvent: (String, Long, Int, Int, String, String, Int, String)->Unit,
    generateDays: ()->List<Long>,
    onNavigateToWeather: (PortStop)->Unit,
    onNavigateToPortMap: () -> Unit = {},
    onNavigateToShipMaps: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    countdownExtra: String? = null
){
    val ports by portsFlow.collectAsState()
    val events by eventsFlow.collectAsState()
    val upcoming by upcomingFlow.collectAsState()
    var showAddEvent by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { TopAppBar(title={ TText("🚢 CruiseLoom") }, actions={ IconButton(onClick=onNavigateToSettings){ Icon(Icons.Default.Settings,"settings") }}) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                BannerAd(modifier = Modifier.fillMaxWidth())
                NavigationBar(windowInsets = WindowInsets(0)){
                    NavigationBarItem(selected=true, onClick={}, icon={ Icon(Icons.Default.Home,"home")}, label={ TText("Dashboard")})
                    NavigationBarItem(selected=false, onClick=onNavigateToPorts, icon={ Icon(Icons.Default.Place,"ports")}, label={ TText("Ports (${ports.size})")})
                    NavigationBarItem(selected=false, onClick=onNavigateToParty, icon={ Icon(Icons.Default.Person,"party")}, label={ TText("Party")})
                }
            }
        },
        floatingActionButton = {
            if(cruise!=null) FloatingActionButton(onClick={ showAddEvent=true }){ Icon(Icons.Default.Add,"add")}
        }
    ){ padding ->
        if(cruise==null){
            Box(Modifier.padding(padding).fillMaxSize()){
                EmptyState(
                    emoji = "🏝️",
                    title = "Welcome Aboard!",
                    subtitle = "Set up your cruise to start planning",
                    modifier = Modifier.align(Alignment.Center),
                    action = { Button(onClick=onNavigateToSetup){ TText("Create Cruise") } }
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding).padding(16.dp)){
                item{
                    GradientHeroBanner(Modifier.fillMaxWidth()){
                        Text(cruise.shipName, style=MaterialTheme.typography.headlineSmall, color=Color.White)
                        TText("${formatDate(cruise.startDate, "MMM d, yyyy")} - ${formatDate(cruise.endDate, "MMM d, yyyy")} • ${((cruise.endDate - cruise.startDate)/(24*60*60*1000)+1)} days", style=MaterialTheme.typography.bodyMedium, color=Color.White.copy(alpha=0.9f))
                            if(cruise.notes.isNotEmpty()) Text(cruise.notes, style=MaterialTheme.typography.bodySmall, color=Color.White.copy(alpha=0.85f))
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                AssistChip(onClick=onNavigateToPorts, label={ TText("Manage Ports")}, leadingIcon={ Icon(Icons.Default.Place,null, Modifier.size(16.dp))}, colors=AssistChipDefaults.assistChipColors(containerColor=Color.White.copy(alpha=0.16f), labelColor=Color.White, leadingIconContentColor=Color.White))
                                AssistChip(onClick=onNavigateToParty, label={ TText("Party Chat")}, leadingIcon={ Icon(Icons.Default.Chat,null, Modifier.size(16.dp))}, colors=AssistChipDefaults.assistChipColors(containerColor=Color.White.copy(alpha=0.16f), labelColor=Color.White, leadingIconContentColor=Color.White))
                            }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Countdown card if cruise is future
                    val todayMid = com.charles.cruiseapp.util.startOfDay(System.currentTimeMillis())
                    val startDay = com.charles.cruiseapp.util.startOfDay(cruise.startDate)
                    val daysUntil = ((startDay - todayMid) / (24*60*60*1000L)).toInt()
                    if (daysUntil > 0) {
                        var tick by remember { mutableStateOf(0) }
                        LaunchedEffect(cruise.startDate) {
                            while (true) {
                                kotlinx.coroutines.delay(60_000)
                                tick++
                            }
                        }
                        val effectiveDays = ((com.charles.cruiseapp.util.startOfDay(cruise.startDate) - com.charles.cruiseapp.util.startOfDay(System.currentTimeMillis())) / (24*60*60*1000L)).toInt()
                        Card(Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.large, colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.tertiaryContainer), elevation=CardDefaults.cardElevation(4.dp)){
                            Column(Modifier.padding(16.dp)){
                                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                                    Column(Modifier.weight(1f)){
                                        Text(
                                            when(effectiveDays){
                                                1 -> "1 day to go!"
                                                else -> "$effectiveDays days to go!"
                                            }, style=MaterialTheme.typography.headlineSmall, color=MaterialTheme.colorScheme.onTertiaryContainer)
                                        TText(cruise.shipName + " • " + formatDate(cruise.startDate, "EEE, MMM d, yyyy"), style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onTertiaryContainer)
                                        if (countdownExtra != null) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(countdownExtra, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                        TText("Daily 9 AM reminder enabled — you'll get a notification each morning.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha=0.8f))
                                    }
                                    Icon(Icons.Default.Event, contentDescription=null, modifier=Modifier.size(48.dp), tint=MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { 1f - (effectiveDays.coerceIn(0,365) / 365f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    } else if (daysUntil == 0) {
                        Card(Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.large, colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)){
                            Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically){
                                Icon(Icons.Default.Celebration, null, tint=MaterialTheme.colorScheme.primary, modifier=Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Column{
                                    TText("Bon voyage! 🚢", style=MaterialTheme.typography.titleMedium)
                                    TText("Your cruise is today — have an amazing trip!", style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Map / Deck quick actions
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        AssistChip(onClick=onNavigateToPortMap, label={ TText("Port Map")}, leadingIcon={ Icon(Icons.Default.Map, null, Modifier.size(16.dp))})
                        AssistChip(onClick=onNavigateToShipMaps, label={ TText("Ship Decks")}, leadingIcon={ Icon(Icons.Default.DirectionsBoat, null, Modifier.size(16.dp))})
                    }
                    Spacer(Modifier.height(16.dp))

                    if(upcoming.isNotEmpty()){
                        TText("Upcoming Events", style=MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        upcoming.take(3).forEach{ ev ->
                            Card(Modifier.fillMaxWidth().padding(vertical=4.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)){
                                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){
                                    Column{ TText(ev.title, style=MaterialTheme.typography.bodyLarge); TText(ev.location, style=MaterialTheme.typography.bodySmall); TText("${formatDate(ev.dateMillis)} ${formatTime(ev.startTimeMillis)}", style=MaterialTheme.typography.bodySmall)}
                                    Icon(Icons.Default.Notifications, contentDescription=null)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    TText("Daily Itinerary", style=MaterialTheme.typography.titleMedium)
                    TText("Tap a day to plan events", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                val days = generateDays()
                items(days){ day ->
                    val dayEvents = events.filter{ it.dateMillis == day || it.dateMillis == com.charles.cruiseapp.util.startOfDay(day) }
                    val portForDay = ports.find{ day >= com.charles.cruiseapp.util.startOfDay(it.arrivalDate) && day <= com.charles.cruiseapp.util.startOfDay(it.departureDate) }
                    PopIn {
                        Card(
                            Modifier.fillMaxWidth().padding(vertical=6.dp).clickable{ onNavigateToDay(day) },
                            shape=MaterialTheme.shapes.medium,
                            colors=CardDefaults.cardColors(containerColor = if(portForDay!=null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
                            elevation=CardDefaults.cardElevation(2.dp)
                        ){
                            Column(Modifier.padding(16.dp)){
                                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                                    Column{
                                        TText(formatDate(day, "EEEE, MMM d"), style=MaterialTheme.typography.titleMedium)
                                        if(portForDay!=null) TText("📍 ${portForDay.name}", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.primary, fontWeight=FontWeight.Bold)
                                        else TText("🌊 Sea Day", style=MaterialTheme.typography.bodySmall)
                                    }
                                    Badge(containerColor=MaterialTheme.colorScheme.secondary){ TText("${dayEvents.size}") }
                                }
                                Spacer(Modifier.height(8.dp))
                                if(dayEvents.isEmpty()) TText("No events yet — tap to add", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                else {
                                    dayEvents.take(3).forEach{ ev ->
                                        Row(verticalAlignment=Alignment.CenterVertically){
                                            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                                            Spacer(Modifier.width(8.dp))
                                            TText("${formatTime(ev.startTimeMillis)} ${ev.title}", style=MaterialTheme.typography.bodySmall, maxLines=1)
                                            if(ev.location.isNotEmpty()) TText(" • ${ev.location}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1)
                                        }
                                    }
                                    if(dayEvents.size>3) TText("+ ${dayEvents.size-3} more", style=MaterialTheme.typography.bodySmall)
                                }
                                if(portForDay!=null){
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick={ onNavigateToWeather(portForDay) }, modifier=Modifier.fillMaxWidth()){ Icon(Icons.Default.WbSunny,null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); TText("Weather for ${portForDay.name}")}
                                }
                            }
                        }
                    }
                }
                item{
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    if(showAddEvent && cruise!=null){
        var title by remember{ mutableStateOf("") }
        var location by remember{ mutableStateOf("") }
        var hour by remember{ mutableStateOf("10") }
        var minute by remember{ mutableStateOf("00") }
        var dateChoice by remember{ mutableStateOf(cruise.startDate) }
        var reminder by remember{ mutableStateOf("15") }
        var category by remember{ mutableStateOf("General") }
        val days = generateDays()
        AlertDialog(
            onDismissRequest={ showAddEvent=false },
            title={ TText("Add Event")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(value=title, onValueChange={title=it}, label={ TText("Title *")}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=location, onValueChange={location=it}, label={ TText("Location")}, modifier=Modifier.fillMaxWidth())
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedTextField(value=hour, onValueChange={hour=it}, label={ TText("Hour")}, modifier=Modifier.weight(1f))
                        OutlinedTextField(value=minute, onValueChange={minute=it}, label={ TText("Min")}, modifier=Modifier.weight(1f))
                        OutlinedTextField(value=reminder, onValueChange={reminder=it}, label={ TText("Remind min")}, modifier=Modifier.weight(1f))
                    }
                    // date picker simplified
                    TText("Date:", style=MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier=Modifier.height(120.dp)){
                        items(days){ d ->
                            val sel = d==dateChoice
                            ListItem(
                                headlineContent={ Text(formatDate(d))},
                                trailingContent={ if(sel) Icon(Icons.Default.Check,null)},
                                modifier=Modifier.clickable{ dateChoice=d }
                            )
                        }
                    }
                    OutlinedTextField(value=category, onValueChange={category=it}, label={ TText("Category")}, modifier=Modifier.fillMaxWidth())
                }
            },
            confirmButton={
                Button(onClick={
                    if(title.isNotBlank()){
                        val h = hour.toIntOrNull() ?: 10; val m = minute.toIntOrNull() ?: 0; val rem = reminder.toIntOrNull() ?: 15
                        onAddEvent(title, dateChoice, h, m, location, category, rem, "")
                        showAddEvent=false
                    }
                }){ TText("Save & Notify")}
            },
            dismissButton={ TextButton(onClick={ showAddEvent=false }){ TText("Cancel")}}
        )
    }
}