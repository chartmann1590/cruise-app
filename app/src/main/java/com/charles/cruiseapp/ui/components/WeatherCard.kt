package com.charles.cruiseapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.data.remote.ForecastResponse
import com.charles.cruiseapp.util.wmoToDescription
import com.charles.cruiseapp.util.wmoToEmoji

@Composable
fun WeatherCard(forecast: ForecastResponse?, loading: Boolean, error: String?, onRetry: ()->Unit, modifier: Modifier=Modifier, isMetric: Boolean = true){
    var expanded by remember { mutableStateOf(true) }
    Card(modifier.fillMaxWidth(), shape=MaterialTheme.shapes.large, colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer), elevation=CardDefaults.cardElevation(4.dp)){
        Column(Modifier.padding(16.dp)){
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("☀️ Weather Forecast", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.onPrimaryContainer)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    when{
                        loading -> { CircularProgressIndicator(color=MaterialTheme.colorScheme.onPrimaryContainer); Spacer(Modifier.height(8.dp)); Text("Fetching forecast...", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onPrimaryContainer) }
                        error != null -> { Text("Error: $error", color=MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)); Button(onClick=onRetry){ Text("Retry") } }
                        forecast == null -> Text("No data. Tap refresh when online.", color=MaterialTheme.colorScheme.onPrimaryContainer)
                        else -> {
                            val cur = forecast.current
                            if(cur!=null){
                                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){
                                    Column{
                                        Text("${wmoToEmoji(cur.weatherCode)} ${wmoToDescription(cur.weatherCode)}", style=MaterialTheme.typography.titleLarge)
                                        val tempStr = com.charles.cruiseapp.util.UnitUtils.formatTemp(cur.temperature2m, isMetric)
                                        val windStr = com.charles.cruiseapp.util.UnitUtils.formatWind(cur.windSpeed, isMetric)
                                        Text("$tempStr  • $windStr")
                                        cur.humidity?.let{ Text("Humidity $it%") }
                                    }
                                    cur.time?.let{ Text(it, style=MaterialTheme.typography.bodySmall)}
                                }
                                HorizontalDivider(Modifier.padding(vertical=8.dp))
                            }
                            val daily = forecast.daily
                            if(daily?.time!=null){
                                Text("Next ${daily.time.size} days", style=MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(4.dp))
                                daily.time.forEachIndexed{ i, date ->
                                    val max = daily.tempMax?.getOrNull(i) ?: 0.0
                                    val min = daily.tempMin?.getOrNull(i) ?: 0.0
                                    val code = daily.weatherCode?.getOrNull(i)
                                    val precip = daily.precipProb?.getOrNull(i)
                                    val wind = daily.windMax?.getOrNull(i)
                                    Card(Modifier.fillMaxWidth().padding(vertical=4.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
                                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){
                                            Column{ Text(date, style=MaterialTheme.typography.bodyMedium); Text("${wmoToEmoji(code)} ${wmoToDescription(code)}", style=MaterialTheme.typography.bodySmall) }
                                            Column(horizontalAlignment=Alignment.End){
                                                Text(com.charles.cruiseapp.util.UnitUtils.formatTempRange(min, max, isMetric), style=MaterialTheme.typography.bodyMedium)
                                                precip?.let{ Text("Rain $it%", style=MaterialTheme.typography.bodySmall)}
                                                wind?.let{
                                                    val wStr = com.charles.cruiseapp.util.UnitUtils.formatWind(it, isMetric)
                                                    Text(wStr, style=MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val unitHint = if (isMetric) "°C • km/h • mm" else "°F • mph • in"
                            Text("Units: $unitHint", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
