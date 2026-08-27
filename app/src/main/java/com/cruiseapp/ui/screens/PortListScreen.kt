package com.cruiseapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cruiseapp.data.local.Cruise
import com.cruiseapp.data.local.PortStop
import com.cruiseapp.data.remote.GeocodingResult
import com.cruiseapp.util.formatDate
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortListScreen(cruise: Cruise?, portsFlow: StateFlow<List<PortStop>>, onAddPort:(String,Double,Double,Long,Long,String)->Unit, onDeletePort:(PortStop)->Unit, onWeatherClick:(PortStop)->Unit, onBack:()->Unit, weatherVm: WeatherViewModel){
    val ports by portsFlow.collectAsState()
    var showAdd by remember{ mutableStateOf(false)}
    var name by remember{ mutableStateOf("")}
    var latStr by remember{ mutableStateOf("")}
    var lonStr by remember{ mutableStateOf("")}
    var country by remember{ mutableStateOf("")}
    var arrival by remember{ mutableStateOf(cruise?.startDate ?: Calendar.getInstance().timeInMillis)}
    var departure by remember{ mutableStateOf(arrival)}
    var searchResults by remember{ mutableStateOf<List<GeocodingResult>>(emptyList())}
    var searching by remember{ mutableStateOf(false)}
    var error by remember{ mutableStateOf<String?>(null)}

    Scaffold(
        topBar={ TopAppBar(title={ Text("Port Stops")}, navigationIcon={ IconButton(onClick=onBack){ Icon(Icons.Default.ArrowBack,null)}}) },
        floatingActionButton={ FloatingActionButton(onClick={ showAdd=true }){ Icon(Icons.Default.Add,null)}}
    ){ pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp)){
            item{
                if(ports.isEmpty()){
                    Card(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(16.dp)){
                            Text("No ports yet", style=MaterialTheme.typography.titleMedium)
                            Text("Add your stops — weather will be fetched per port via Open-Meteo (free, no key). Works offline with cache.", style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    Text("${ports.size} ports • Weather integrated", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
            }
            items(ports){ port ->
                Card(Modifier.fillMaxWidth().padding(vertical=6.dp), elevation=CardDefaults.cardElevation(2.dp)){
                    Column(Modifier.padding(16.dp)){
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){
                            Column(Modifier.weight(1f)){
                                Text(port.name, style=MaterialTheme.typography.titleMedium)
                                if(port.country.isNotEmpty()) Text(port.country, style=MaterialTheme.typography.bodySmall)
                                Text("${formatDate(port.arrivalDate)} → ${formatDate(port.departureDate)}", style=MaterialTheme.typography.bodySmall)
                                Text("${port.latitude}, ${port.longitude}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick={ onDeletePort(port)} ){ Icon(Icons.Default.Delete,null, tint=MaterialTheme.colorScheme.error)}
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Button(onClick={ onWeatherClick(port)}, modifier=Modifier.weight(1f)){ Icon(Icons.Default.WbSunny,null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Weather")}
                        }
                    }
                }
            }
            item{
                Spacer(Modifier.height(8.dp))
                Text("Tip: Search your real port city to auto-fill coordinates via Open-Meteo real geocoding (no API key). Weather is real data, cached for offline use.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if(showAdd){
        AlertDialog(
            onDismissRequest={ showAdd=false },
            title={ Text("Add Port Stop")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(value=name, onValueChange={name=it}, label={ Text("Port / City *")}, modifier=Modifier.fillMaxWidth(), placeholder={ Text("Enter real port name")})
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedTextField(value=latStr, onValueChange={latStr=it}, label={ Text("Lat")}, modifier=Modifier.weight(1f), placeholder={ Text("Latitude")})
                        OutlinedTextField(value=lonStr, onValueChange={lonStr=it}, label={ Text("Lon")}, modifier=Modifier.weight(1f), placeholder={ Text("Longitude")})
                    }
                    OutlinedTextField(value=country, onValueChange={country=it}, label={ Text("Country")}, modifier=Modifier.fillMaxWidth())
                    // arrival/departure offset selectors simplified
                    Text("Arrival: ${formatDate(arrival)}  Departure: ${formatDate(departure)}", style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        FilledTonalButton(onClick={ arrival += 24*60*60*1000L; departure = arrival }){ Text("Arr +1d")}
                        FilledTonalButton(onClick={ arrival -= 24*60*60*1000L; departure = arrival }){ Text("Arr -1d")}
                    }
                    Button(onClick={
                        searching=true; error=null
                        weatherVm.searchPlaces(name, { results ->
                            searching=false; searchResults=results
                            if(results.isNotEmpty()){
                                val first = results.first()
                                latStr = first.latitude.toString(); lonStr = first.longitude.toString(); country = first.country ?: ""
                            }
                        }, { err -> searching=false; error=err })
                    }, enabled=name.isNotBlank()){ if(searching) CircularProgressIndicator(Modifier.size(16.dp)) else Icon(Icons.Default.Search,null); Spacer(Modifier.width(8.dp)); Text("Search location (Open-Meteo)")}
                    if(error!=null) Text(error!!, color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall)
                    if(searchResults.isNotEmpty()){
                        Text("Results:", style=MaterialTheme.typography.labelMedium)
                        searchResults.take(3).forEach{ r ->
                            ListItem(headlineContent={ Text(r.name)}, supportingContent={ Text("${r.country ?: ""} ${r.admin1 ?: ""} • ${r.latitude}, ${r.longitude}")}, trailingContent={ TextButton(onClick={ latStr=r.latitude.toString(); lonStr=r.longitude.toString(); country=r.country?:""; name=r.name}){ Text("Use")} })
                        }
                    }
                    Text("Weather needs lat/lon — search fills it automatically (no API key).", style=MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton={
                Button(onClick={
                    val lat = latStr.toDoubleOrNull(); val lon = lonStr.toDoubleOrNull()
                    if(name.isNotBlank() && lat!=null && lon!=null){
                        onAddPort(name, lat, lon, arrival, departure, country)
                        showAdd=false; name=""; latStr=""; lonStr=""; country=""; searchResults=emptyList()
                    }
                }){ Text("Add Port")}
            },
            dismissButton={ TextButton(onClick={ showAdd=false }){ Text("Cancel")}}
        )
    }
}
