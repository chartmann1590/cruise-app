package com.charles.cruiseapp.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.data.local.PlaceCache
import com.charles.cruiseapp.data.local.PortStop
import com.charles.cruiseapp.data.remote.ForecastResponse
import com.charles.cruiseapp.data.remote.PlaceOfInterest
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class WeatherViewModel(app: Application): AndroidViewModel(app){
    private val cruiseApp = app as CruiseApplication
    private val repo = cruiseApp.weatherRepository
    private val placesRepo = cruiseApp.placesRepository
    private val db = cruiseApp.database
    private val placesJson = Json { ignoreUnknownKeys = true }

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _forecast = MutableStateFlow<ForecastResponse?>(null)
    val forecast: StateFlow<ForecastResponse?> = _forecast

    private val _placesLoading = MutableStateFlow(false)
    val placesLoading: StateFlow<Boolean> = _placesLoading
    private val _placesError = MutableStateFlow<String?>(null)
    val placesError: StateFlow<String?> = _placesError
    private val _places = MutableStateFlow<List<PlaceOfInterest>>(emptyList())
    val places: StateFlow<List<PlaceOfInterest>> = _places

    // Shared with PlaceDetailScreen (navigated to from within this port's places list).
    private val _activePort = MutableStateFlow<PortStop?>(null)
    val activePort: StateFlow<PortStop?> = _activePort
    private val _selectedPlace = MutableStateFlow<PlaceOfInterest?>(null)
    val selectedPlace: StateFlow<PlaceOfInterest?> = _selectedPlace

    fun setActivePort(port: PortStop){ _activePort.value = port }
    fun selectPlace(place: PlaceOfInterest){ _selectedPlace.value = place; _placeFullExtract.value = null }

    private val _placeExtractLoading = MutableStateFlow(false)
    val placeExtractLoading: StateFlow<Boolean> = _placeExtractLoading
    private val _placeFullExtract = MutableStateFlow<String?>(null)
    val placeFullExtract: StateFlow<String?> = _placeFullExtract

    fun loadFullExtract(title: String){
        viewModelScope.launch {
            _placeExtractLoading.value = true
            try {
                val r = placesRepo.getFullExtract(title)
                if(r.isSuccess) _placeFullExtract.value = r.getOrNull()
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
            } finally {
                _placeExtractLoading.value = false
            }
        }
    }

    /** Always tries a fresh fetch first (so it refreshes whenever there's a data
     * connection) and only falls back to the cached list if that fetch fails. */
    fun loadPlacesForPort(portId: Long, lat: Double, lon: Double){
        viewModelScope.launch {
            val trace = FirebasePerfUtils.startTrace("places_load_for_port")
            trace?.putAttribute("portId", portId.toString())
            _placesLoading.value = true
            _placesError.value = null
            try {
                val result = placesRepo.getNearbyPlaces(lat, lon)
                if(result.isSuccess){
                    val list = result.getOrNull() ?: emptyList()
                    _places.value = list
                    try {
                        val jsonStr = placesJson.encodeToString(ListSerializer(PlaceOfInterest.serializer()), list)
                        db.placeCacheDao().insert(PlaceCache(portStopId=portId, fetchedAt=System.currentTimeMillis(), json=jsonStr))
                    } catch (_: Exception) {}
                    trace?.putMetric("success", 1)
                } else {
                    val err = result.exceptionOrNull()
                    if (err != null) FirebaseCrashlyticsUtils.recordException(err)
                    val cache = db.placeCacheDao().getForPort(portId)
                    if(cache != null){
                        try {
                            _places.value = placesJson.decodeFromString(ListSerializer(PlaceOfInterest.serializer()), cache.json)
                            _placesError.value = "Showing saved results from ${java.text.SimpleDateFormat("MM/dd HH:mm").format(java.util.Date(cache.fetchedAt))}"
                        } catch (_: Exception) {
                            _placesError.value = "Couldn't load things to do — no connection"
                        }
                    } else {
                        _places.value = emptyList()
                        _placesError.value = "Couldn't load things to do — no connection"
                    }
                    trace?.putMetric("error", 1)
                }
            } catch (e: Exception) {
                FirebaseCrashlyticsUtils.recordException(e)
                _placesError.value = e.message
                trace?.putMetric("error", 1)
            } finally {
                _placesLoading.value = false
                try { trace?.stop() } catch (_: Exception) {}
            }
        }
    }

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
