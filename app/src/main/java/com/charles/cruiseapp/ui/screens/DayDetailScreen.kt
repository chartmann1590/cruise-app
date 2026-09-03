package com.charles.cruiseapp.ui.screens

import com.charles.cruiseapp.ui.translation.TText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.data.local.PlannedEvent
import com.charles.cruiseapp.ui.components.EmptyState
import com.charles.cruiseapp.ui.components.PopIn
import com.charles.cruiseapp.util.formatDate
import com.charles.cruiseapp.util.formatTime
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(dateMillis: Long, eventsFlow: Flow<List<PlannedEvent>>, onAddEvent:(String,Int,Int,String,String,Int,String)->Unit, onDeleteEvent:(PlannedEvent)->Unit, onBack:()->Unit){
    val events by eventsFlow.collectAsState(initial=emptyList())
    var showAdd by remember{ mutableStateOf(false)}
    var title by remember{ mutableStateOf("")}
    var loc by remember{ mutableStateOf("")}
    var hour by remember{ mutableStateOf("10")}
    var min by remember{ mutableStateOf("00")}
    var cat by remember{ mutableStateOf("General")}
    var rem by remember{ mutableStateOf("15")}

    Scaffold(
        topBar={ TopAppBar(title={ TText(formatDate(dateMillis,"EEEE, MMM d"))}, navigationIcon={ IconButton(onClick=onBack){ Icon(Icons.Default.ArrowBack,null)}}) },
        floatingActionButton={ FloatingActionButton(onClick={ showAdd=true }){ Icon(Icons.Default.Add,null)}},
        bottomBar = { BannerAd(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) }
    ){ pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp)){
            item{
                TText("Plan for ${formatDate(dateMillis,"EEEE")}", style=MaterialTheme.typography.titleMedium)
                TText("${events.size} events • Notifications will remind you", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
            }
            if(events.isEmpty()){
                item{
                    Card(Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.large, colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
                        EmptyState(
                            emoji = "🌴",
                            title = "No plans yet",
                            subtitle = "Add breakfast, excursion, show, dinner...",
                            action = { Button(onClick={ showAdd=true }){ TText("Add Event")} }
                        )
                    }
                }
            }
            items(events){ ev ->
                PopIn {
                    Card(Modifier.fillMaxWidth().padding(vertical=6.dp), shape=MaterialTheme.shapes.medium, elevation=CardDefaults.cardElevation(2.dp)){
                        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){
                            Column(Modifier.weight(1f)){
                                Text(ev.title, style=MaterialTheme.typography.titleMedium)
                                if(ev.location.isNotEmpty()) TText("📍 ${ev.location}", style=MaterialTheme.typography.bodySmall)
                                TText("${formatTime(ev.startTimeMillis)} • ${ev.category} • remind ${ev.reminderMinutesBefore}m before", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(ev.description.isNotEmpty()) Text(ev.description, style=MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick={ onDeleteEvent(ev)} ){ Icon(Icons.Default.Delete,null, tint=MaterialTheme.colorScheme.error)}
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if(showAdd){
        AlertDialog(
            onDismissRequest={ showAdd=false},
            title={ TText("Add Event for ${formatDate(dateMillis)}")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(value=title, onValueChange={title=it}, label={ TText("Title *")}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=loc, onValueChange={loc=it}, label={ TText("Location")}, modifier=Modifier.fillMaxWidth())
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedTextField(value=hour, onValueChange={hour=it}, label={ TText("Hour 0-23")}, modifier=Modifier.weight(1f))
                        OutlinedTextField(value=min, onValueChange={min=it}, label={ TText("Min")}, modifier=Modifier.weight(1f))
                        OutlinedTextField(value=rem, onValueChange={rem=it}, label={ TText("Remind")}, modifier=Modifier.weight(1f))
                    }
                    OutlinedTextField(value=cat, onValueChange={cat=it}, label={ TText("Category")}, modifier=Modifier.fillMaxWidth(), placeholder={ TText("Dining, Excursion, Show...")})
                }
            },
            confirmButton={
                Button(onClick={
                    if(title.isNotBlank()){
                        val h = hour.toIntOrNull()?:10; val m = min.toIntOrNull()?:0; val r = rem.toIntOrNull()?:15
                        onAddEvent(title,h,m,loc,cat,r,"")
                        title=""; loc=""; showAdd=false
                    }
                }){ TText("Save")}
            },
            dismissButton={ TextButton(onClick={ showAdd=false}){ TText("Cancel")}}
        )
    }
}