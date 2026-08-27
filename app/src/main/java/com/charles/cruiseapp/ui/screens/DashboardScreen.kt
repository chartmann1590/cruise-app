package com.charles.cruiseapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.data.local.Cruise
import com.charles.cruiseapp.data.local.PlannedEvent
import com.charles.cruiseapp.data.local.PortStop
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
    onNavigateToWeather: (PortStop)->Unit
){
    val ports by portsFlow.collectAsState()
    val events by eventsFlow.collectAsState()
    val upcoming by upcomingFlow.collectAsState()
    var showAddEvent by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { TopAppBar(title={ Text("🚢 Cruise Planner") }, actions={ IconButton(onClick=onNavigateToSetup){ Icon(Icons.Default.Settings,"setup") }}) },
        bottomBar = {
            NavigationBar{
                NavigationBarItem(selected=true, onClick={}, icon={ Icon(Icons.Default.Home,"home")}, label={ Text("Dashboard")})
                NavigationBarItem(selected=false, onClick=onNavigateToPorts, icon={ Icon(Icons.Default.Place,"ports")}, label={ Text("Ports (${ports.size})")})
                NavigationBarItem(selected=false, onClick=onNavigateToParty, icon={ Icon(Icons.Default.Person,"party")}, label={ Text("Party")})
            }
        },
        floatingActionButton = {
            if(cruise!=null) FloatingActionButton(onClick={ showAddEvent=true }){ Icon(Icons.Default.Add,"add")}
        }
    ){ padding ->
        if(cruise==null){
            Column(Modifier.padding(padding).padding(24.dp).fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center){
                Icon(Icons.Default.DirectionsBoat, contentDescription=null, modifier=Modifier.size(80.dp), tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Welcome Aboard!", style=MaterialTheme.typography.headlineMedium)
                Text("Set up your cruise to start planning", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick=onNavigateToSetup, modifier=Modifier.fillMaxWidth()){ Text("Create Cruise") }
            }
        } else {
            LazyColumn(Modifier.padding(padding).padding(16.dp)){
                item{
                    Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
                        Column(Modifier.padding(16.dp)){
                            Text(cruise.shipName, style=MaterialTheme.typography.headlineSmall)
                            Text("${formatDate(cruise.startDate, "MMM d, yyyy")} - ${formatDate(cruise.endDate, "MMM d, yyyy")} • ${((cruise.endDate - cruise.startDate)/(24*60*60*1000)+1)} days", style=MaterialTheme.typography.bodyMedium)
                            if(cruise.notes.isNotEmpty()) Text(cruise.notes, style=MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                AssistChip(onClick=onNavigateToPorts, label={ Text("Manage Ports")}, leadingIcon={ Icon(Icons.Default.Place,null, Modifier.size(16.dp))})
                                AssistChip(onClick=onNavigateToParty, label={ Text("Party Chat")}, leadingIcon={ Icon(Icons.Default.Chat,null, Modifier.size(16.dp))})
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if(upcoming.isNotEmpty()){
                        Text("Upcoming Events", style=MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        upcoming.take(3).forEach{ ev ->
                            Card(Modifier.fillMaxWidth().padding(vertical=4.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)){
                                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){
                                    Column{ Text(ev.title, style=MaterialTheme.typography.bodyLarge); Text(ev.location, style=MaterialTheme.typography.bodySmall); Text("${formatDate(ev.dateMillis)} ${formatTime(ev.startTimeMillis)}", style=MaterialTheme.typography.bodySmall)}
                                    Icon(Icons.Default.Notifications, contentDescription=null)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text("Daily Itinerary", style=MaterialTheme.typography.titleMedium)
                    Text("Tap a day to plan events", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                val days = generateDays()
                items(days){ day ->
                    val dayEvents = events.filter{ it.dateMillis == day || it.dateMillis == com.charles.cruiseapp.util.startOfDay(day) }
                    val portForDay = ports.find{ day >= com.charles.cruiseapp.util.startOfDay(it.arrivalDate) && day <= com.charles.cruiseapp.util.startOfDay(it.departureDate) }
                    Card(Modifier.fillMaxWidth().padding(vertical=6.dp).clickable{ onNavigateToDay(day) }, elevation=CardDefaults.cardElevation(2.dp)){
                        Column(Modifier.padding(16.dp)){
                            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                                Column{
                                    Text(formatDate(day, "EEEE, MMM d"), style=MaterialTheme.typography.titleMedium)
                                    if(portForDay!=null) Text("📍 ${portForDay.name}", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.primary)
                                    else Text("🌊 Sea Day", style=MaterialTheme.typography.bodySmall)
                                }
                                Badge(containerColor=MaterialTheme.colorScheme.primary){ Text("${dayEvents.size}") }
                            }
                            Spacer(Modifier.height(8.dp))
                            if(dayEvents.isEmpty()) Text("No events yet — tap to add", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                            else {
                                dayEvents.take(3).forEach{ ev ->
                                    Row(verticalAlignment=Alignment.CenterVertically){
                                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                        Spacer(Modifier.width(8.dp))
                                        Text("${formatTime(ev.startTimeMillis)} ${ev.title}", style=MaterialTheme.typography.bodySmall, maxLines=1)
                                        if(ev.location.isNotEmpty()) Text(" • ${ev.location}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1)
                                    }
                                }
                                if(dayEvents.size>3) Text("+ ${dayEvents.size-3} more", style=MaterialTheme.typography.bodySmall)
                            }
                            if(portForDay!=null){
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick={ onNavigateToWeather(portForDay) }, modifier=Modifier.fillMaxWidth()){ Icon(Icons.Default.WbSunny,null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Weather for ${portForDay.name}")}
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
            title={ Text("Add Event")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(value=title, onValueChange={title=it}, label={ Text("Title *")}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=location, onValueChange={location=it}, label={ Text("Location")}, modifier=Modifier.fillMaxWidth())
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedTextField(value=hour, onValueChange={hour=it}, label={ Text("Hour")}, modifier=Modifier.weight(1f))
                        OutlinedTextField(value=minute, onValueChange={minute=it}, label={ Text("Min")}, modifier=Modifier.weight(1f))
                        OutlinedTextField(value=reminder, onValueChange={reminder=it}, label={ Text("Remind min")}, modifier=Modifier.weight(1f))
                    }
                    // date picker simplified
                    Text("Date:", style=MaterialTheme.typography.labelMedium)
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
                    OutlinedTextField(value=category, onValueChange={category=it}, label={ Text("Category")}, modifier=Modifier.fillMaxWidth())
                }
            },
            confirmButton={
                Button(onClick={
                    if(title.isNotBlank()){
                        val h = hour.toIntOrNull() ?: 10; val m = minute.toIntOrNull() ?: 0; val rem = reminder.toIntOrNull() ?: 15
                        onAddEvent(title, dateChoice, h, m, location, category, rem, "")
                        showAddEvent=false
                    }
                }){ Text("Save & Notify")}
            },
            dismissButton={ TextButton(onClick={ showAddEvent=false }){ Text("Cancel")}}
        )
    }
}
