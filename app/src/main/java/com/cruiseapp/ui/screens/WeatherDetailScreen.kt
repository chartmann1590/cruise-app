package com.cruiseapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cruiseapp.data.local.PortStop
import com.cruiseapp.ui.components.WeatherCard
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailScreen(portId: Long, portsFlow: StateFlow<List<PortStop>>, weatherVm: WeatherViewModel, onBack:()->Unit){
    val ports by portsFlow.collectAsState()
    val port = ports.find{ it.id == portId }
    val forecast by weatherVm.forecast.collectAsState()
    val loading by weatherVm.loading.collectAsState()
    val error by weatherVm.error.collectAsState()

    LaunchedEffect(port){
        port?.let{ weatherVm.loadForPort(it.id, it.latitude, it.longitude) }
    }

    Scaffold(topBar={
        TopAppBar(title={ Text(port?.name ?: "Weather")}, navigationIcon={ IconButton(onClick=onBack){ Icon(Icons.Default.ArrowBack,null)}}, actions={ IconButton(onClick={ port?.let{ weatherVm.loadForPort(it.id, it.latitude, it.longitude)}}){ Icon(Icons.Default.Refresh,null)}})
    }){
        Column(Modifier.padding(it).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(16.dp)){
            if(port==null){
                Text("Port not found")
                return@Column
            }
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp)){
                    Text(port.name, style=MaterialTheme.typography.headlineSmall)
                    if(port.country.isNotEmpty()) Text(port.country)
                    Text("${port.latitude}, ${port.longitude}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Arrival ${com.cruiseapp.util.formatDate(port.arrivalDate)} • Free weather via Open-Meteo", style=MaterialTheme.typography.bodySmall)
                }
            }
            WeatherCard(forecast=if (port.id != portId) null else forecast, loading=loading, error=error, onRetry={ weatherVm.loadForPort(port.id, port.latitude, port.longitude)})
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)){
                Column(Modifier.padding(12.dp)){
                    Text("How it works", style=MaterialTheme.typography.labelLarge)
                    Text("• Open-Meteo.com Forecast API: https://api.open-meteo.com/v1/forecast?latitude=${port.latitude}&longitude=${port.longitude}&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto\n• No API key, no signup, CC BY 4.0\n• Cached for 3h, works offline after first fetch\n• Geocoding search also via Open-Meteo (free)", style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
