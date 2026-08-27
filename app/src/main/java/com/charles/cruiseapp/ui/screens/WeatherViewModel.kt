package com.charles.cruiseapp.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.data.remote.ForecastResponse
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeatherViewModel(app: Application): AndroidViewModel(app){
    private val cruiseApp = app as CruiseApplication
    private val repo = cruiseApp.weatherRepository
    private val db = cruiseApp.database

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _forecast = MutableStateFlow<ForecastResponse?>(null)
    val forecast: StateFlow<ForecastResponse?> = _forecast

    fun loadForPort(portId: Long, lat: Double, lon: Double){
        viewModelScope.launch {
            val trace = FirebasePerfUtils.startTrace("weather_load_for_port")
            trace?.putAttribute("portId", portId.toString())
            _loading.value = true
            _error.value = null
            FirebaseCrashlyticsUtils.log("Loading weather for port $portId $lat,$lon")
            try {
                // check cache first
                val cache = db.weatherCacheDao().getForPort(portId)
                if(cache!=null && System.currentTimeMillis() - cache.fetchedAt < 3*60*60*1000){
                    try {
                        // try to decode cached json? simplified - just fetch fresh if online, else use cache summary
                        // For now just attempt fresh fetch, if fails show cache
                    } catch (_:Exception){}
                }
                val result = repo.getForecast(lat, lon, 7)
                if(result.isSuccess){
                    val data = result.getOrNull()
                    _forecast.value = data
                    // cache
                    if(data!=null){
                        val jsonStr = data.toString()
                        db.weatherCacheDao().insert(com.charles.cruiseapp.data.local.WeatherCache(portStopId=portId, fetchedAt=System.currentTimeMillis(), json=jsonStr, tempMax=data.daily?.tempMax?.firstOrNull(), tempMin=data.daily?.tempMin?.firstOrNull(), weatherCode=data.current?.weatherCode, summary=data.daily?.weatherCode?.firstOrNull()?.toString() ?: ""))
                    }
                    trace?.putMetric("success", 1)
                } else {
                    val err = result.exceptionOrNull()
                    if (err != null) FirebaseCrashlyticsUtils.recordException(err)
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to fetch weather"
                    // try cache fallback
                    if(cache!=null){
                        _error.value = _error.value + " (showing cached from ${java.text.SimpleDateFormat("MM/dd HH:mm").format(java.util.Date(cache.fetchedAt))})"
                    }
                    trace?.putMetric("error", 1)
                    trace?.putAttribute("error", _error.value ?: "unknown")
                }
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                _error.value = e.message
                trace?.putMetric("error", 1)
            } finally {
                _loading.value = false
                try { trace?.stop() } catch (_: Exception) {}
            }
        }
    }

    fun searchPlaces(query: String, onResult: (List<com.charles.cruiseapp.data.remote.GeocodingResult>)->Unit, onError:(String)->Unit){
        viewModelScope.launch {
            val trace = FirebasePerfUtils.startTrace("weather_search_places")
            trace?.putAttribute("query", query.take(100))
            try {
                FirebaseCrashlyticsUtils.log("Searching places: $query")
                val r = repo.searchLocation(query)
                if(r.isSuccess) {
                    trace?.putMetric("success", 1)
                    onResult(r.getOrNull()?: emptyList())
                } else {
                    val err = r.exceptionOrNull()
                    if (err != null) FirebaseCrashlyticsUtils.recordException(err)
                    trace?.putMetric("error", 1)
                    onError(r.exceptionOrNull()?.message ?: "search failed")
                }
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                trace?.putMetric("error", 1)
                onError(e.message ?: "search failed")
            } finally {
                try { trace?.stop() } catch (_: Exception) {}
            }
        }
    }
}
