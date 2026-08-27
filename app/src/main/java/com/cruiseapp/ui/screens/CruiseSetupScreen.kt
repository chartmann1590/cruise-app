package com.cruiseapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cruiseapp.util.formatDate
import com.cruiseapp.util.startOfDay
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CruiseSetupScreen(onSave: (String, Long, Long)->Unit, onBack:()->Unit){
    var ship by remember{ mutableStateOf("") }
    var start by remember{ mutableStateOf(Calendar.getInstance().apply{ set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)}.timeInMillis)}
    var end by remember{ mutableStateOf(start + 6*24*60*60*1000L) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    fun addDays(base: Long, delta: Int): Long {
        val cal = Calendar.getInstance().apply{ timeInMillis=base }; cal.add(Calendar.DAY_OF_YEAR, delta); return startOfDay(cal.timeInMillis)
    }
    val durationDays = ((end - start)/(24*60*60*1000) + 1).coerceAtLeast(1)

    Scaffold(topBar={ TopAppBar(title={ Text("Cruise Setup")}, navigationIcon={ IconButton(onClick=onBack){ Icon(Icons.Default.ArrowBack,null) }}) }){
        Column(Modifier.padding(it).padding(16.dp).fillMaxSize(), verticalArrangement=Arrangement.spacedBy(16.dp)){
            OutlinedTextField(value=ship, onValueChange={ship=it}, label={ Text("Cruise Ship Name")}, modifier=Modifier.fillMaxWidth(), placeholder={ Text("e.g., Symphony of the Seas")}, singleLine = true)
            
            Card(Modifier.fillMaxWidth(), colors= CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)){
                Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()){
                        Text("Dates", style=MaterialTheme.typography.titleMedium)
                        FilledTonalButton(onClick = { showRangePicker = true }){
                            Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Calendar")
                        }
                    }
                    Text("Tap calendar to pick start & end, or adjust below", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        OutlinedCard(modifier = Modifier.weight(1f).clickable { showStartPicker = true }){
                            Column(Modifier.padding(12.dp)){
                                Text("Start", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary)
                                Text(formatDate(start,"EEE, MMM d, yyyy"), style=MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(4.dp))
                                Text("Tap to pick", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    FilledTonalButton(onClick={ start=addDays(start,-1)} ) { Text("-1d")}
                                    FilledTonalButton(onClick={ start=addDays(start,1)} ) { Text("+1d")}
                                }
                            }
                        }
                        OutlinedCard(modifier = Modifier.weight(1f).clickable { showEndPicker = true }){
                            Column(Modifier.padding(12.dp)){
                                Text("End", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary)
                                Text(formatDate(end,"EEE, MMM d, yyyy"), style=MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(4.dp))
                                Text("Tap to pick", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    FilledTonalButton(onClick={ end=addDays(end,-1)} ) { Text("-1d")}
                                    FilledTonalButton(onClick={ end=addDays(end,1)} ) { Text("+1d")}
                                }
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)){
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically){
                            Column{
                                Text("$durationDays days", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("${formatDate(start,"MMM d")} → ${formatDate(end,"MMM d, yyyy")}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Icon(Icons.Default.DateRange, null, tint=MaterialTheme.colorScheme.onPrimaryContainer, modifier=Modifier.size(32.dp))
                        }
                    }
                    Text("Tip: day-by-day planner auto-generates after saving.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick={
                if(end < start) end = start
                onSave(ship, startOfDay(start), startOfDay(end))
            }, modifier=Modifier.fillMaxWidth(), enabled=ship.isNotBlank()){
                Icon(Icons.Default.DateRange,null); Spacer(Modifier.width(8.dp)); Text("Save Cruise — $durationDays days")
            }
            Text("Weather uses Open-Meteo real data (no API key) per port. Party chat works via Bluetooth/Nearby even offline with retry until delivered.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Range picker dialog (both dates at once)
    if (showRangePicker) {
        val state = rememberDateRangePickerState(initialSelectedStartDateMillis = start, initialSelectedEndDateMillis = end)
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null) start = startOfDay(s)
                    if (e != null) end = startOfDay(e)
                    if (s != null && e != null && e < s) end = s
                    showRangePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = state, title = { Text("Select cruise dates", modifier = Modifier.padding(16.dp)) }, headline = {
                Text(
                    if (state.selectedStartDateMillis != null && state.selectedEndDateMillis != null)
                        "${state.selectedStartDateMillis?.let { formatDate(it,"MMM d") }} → ${state.selectedEndDateMillis?.let { formatDate(it,"MMM d, yyyy") }}"
                    else "Choose start and end",
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                )
            })
        }
    }
    // Single date pickers for start/end
    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { start = startOfDay(it) }; if (end < start) end = start; showStartPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = end)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { end = startOfDay(it) }; if (end < start) end = start; showEndPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}
