package com.charles.cruiseapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.data.local.PortStop
import com.charles.cruiseapp.data.remote.PlaceOfInterest
import com.charles.cruiseapp.ui.components.PlacesCard
import com.charles.cruiseapp.ui.components.WeatherCard
import com.charles.cruiseapp.util.UnitUtils
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailScreen(
    portId: Long,
    portsFlow: StateFlow<List<PortStop>>,
    weatherVm: WeatherViewModel,
    onBack:()->Unit,
    onOpenPlace: (PlaceOfInterest)->Unit,
    onAddEvent: (String, Long, Int, Int, String, String, Int, String)->Unit,
){
    val ports by portsFlow.collectAsState()
    val port = ports.find{ it.id == portId }
    val forecast by weatherVm.forecast.collectAsState()
    val loading by weatherVm.loading.collectAsState()
    val error by weatherVm.error.collectAsState()
    val places by weatherVm.places.collectAsState()
    val placesLoading by weatherVm.placesLoading.collectAsState()
    val placesError by weatherVm.placesError.collectAsState()

    val context = LocalContext.current
    var isMetric by remember { mutableStateOf(UnitUtils.isMetric(context)) }
    LaunchedEffect(Unit) { UnitUtils.observeIsMetric(context).collect { isMetric = it } }

    LaunchedEffect(port){
        port?.let{
            weatherVm.setActivePort(it)
            weatherVm.loadForPort(it.id, it.latitude, it.longitude)
            weatherVm.loadPlacesForPort(it.id, it.latitude, it.longitude)
        }
    }

    Scaffold(topBar={
        TopAppBar(title={ Text(port?.name ?: "Weather")}, navigationIcon={ IconButton(onClick=onBack){ Icon(Icons.Default.ArrowBack,null)}}, actions={ IconButton(onClick={ port?.let{ weatherVm.loadForPort(it.id, it.latitude, it.longitude); weatherVm.loadPlacesForPort(it.id, it.latitude, it.longitude) }}){ Icon(Icons.Default.Refresh,null)}})
    }, bottomBar = { BannerAd(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) }){
        Column(Modifier.padding(it).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(16.dp)){
            if(port==null){
                Text("Port not found")
                return@Column
            }
            Card(Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.large){
                Column(Modifier.padding(16.dp)){
                    Text("🏝️ ${port.name}", style=MaterialTheme.typography.headlineSmall)
                    if(port.country.isNotEmpty()) Text(port.country)
                    val unitLabel = if (isMetric) "°C • km/h" else "°F • mph"
                    Text("Arrival ${com.charles.cruiseapp.util.formatDate(port.arrivalDate)} • $unitLabel", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            WeatherCard(forecast=if (port.id != portId) null else forecast, loading=loading, error=error, onRetry={ weatherVm.loadForPort(port.id, port.latitude, port.longitude)}, isMetric=isMetric)
            PlacesCard(
                places=places, loading=placesLoading, error=placesError,
                onRetry={ weatherVm.loadPlacesForPort(port.id, port.latitude, port.longitude) },
                onOpenPlace={ place -> weatherVm.selectPlace(place); onOpenPlace(place) },
                onAddToItinerary={ place ->
                    onAddEvent(place.title, port.arrivalDate, 10, 0, place.address ?: port.name, "Excursion", 30, place.extract)
                    Toast.makeText(context, "Added to itinerary", Toast.LENGTH_SHORT).show()
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
