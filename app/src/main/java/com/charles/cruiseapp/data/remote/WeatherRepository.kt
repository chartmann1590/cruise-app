package com.charles.cruiseapp.data.remote

import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.google.firebase.perf.FirebasePerformance
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class WeatherRepository {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val meteoApi: OpenMeteoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(OpenMeteoApi::class.java)
    }

    private val geoApi: GeocodingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(GeocodingApi::class.java)
    }

    suspend fun getForecast(lat: Double, lon: Double, days: Int = 7): Result<ForecastResponse> {
        val trace = FirebasePerfUtils.startTrace("weather_forecast_fetch")
        trace?.putAttribute("lat", lat.toString())
        trace?.putAttribute("lon", lon.toString())
        trace?.putAttribute("days", days.toString())
        val url = "https://api.open-meteo.com/v1/forecast"
        val httpMetric = FirebasePerfUtils.newHttpMetric(url, "GET")
        httpMetric?.start()
        val start = System.currentTimeMillis()
        return try {
            FirebaseCrashlyticsUtils.log("Fetching forecast lat=$lat lon=$lon days=$days")
            val res = if (days <= 7) meteoApi.getForecast(lat, lon, forecastDays = days) else meteoApi.getForecast16(lat, lon, forecastDays = days)
            trace?.putMetric("success", 1)
            httpMetric?.setHttpResponseCode(200)
            httpMetric?.setResponseContentType("application/json")
            Result.success(res)
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            FirebaseCrashlyticsUtils.log("Forecast fetch failed: ${e.message}")
            trace?.putMetric("error", 1)
            trace?.putAttribute("error", e.message ?: e.javaClass.simpleName)
            httpMetric?.setHttpResponseCode(500)
            Result.failure(e)
        } finally {
            try {
                val dur = System.currentTimeMillis() - start
                trace?.putMetric("duration_ms", dur)
                trace?.stop()
                httpMetric?.setRequestPayloadSize(0)
                httpMetric?.stop()
            } catch (_: Exception) {}
        }
    }

    suspend fun searchLocation(query: String): Result<List<GeocodingResult>> {
        val trace = FirebasePerfUtils.startTrace("geocoding_search")
        val httpMetric = FirebasePerfUtils.newHttpMetric("https://geocoding-api.open-meteo.com/v1/search", "GET")
        httpMetric?.start()
        val start = System.currentTimeMillis()
        return try {
            FirebaseCrashlyticsUtils.log("Geocoding search: $query")
            val resp = geoApi.search(query)
            trace?.putMetric("result_count", (resp.results?.size ?: 0).toLong())
            trace?.putMetric("success", 1)
            httpMetric?.setHttpResponseCode(200)
            Result.success(resp.results ?: emptyList())
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
            FirebaseCrashlyticsUtils.log("Geocoding failed for '$query': ${e.message}")
            trace?.putMetric("error", 1)
            httpMetric?.setHttpResponseCode(500)
            Result.failure(e)
        } finally {
            try {
                val dur = System.currentTimeMillis() - start
                trace?.putMetric("duration_ms", dur)
                trace?.stop()
                httpMetric?.stop()
            } catch (_: Exception) {}
        }
    }
}
