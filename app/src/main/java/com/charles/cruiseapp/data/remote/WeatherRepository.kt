package com.charles.cruiseapp.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
        return try {
            val res = if (days <= 7) meteoApi.getForecast(lat, lon, forecastDays = days) else meteoApi.getForecast16(lat, lon, forecastDays = days)
            Result.success(res)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun searchLocation(query: String): Result<List<GeocodingResult>> {
        return try {
            val resp = geoApi.search(query)
            Result.success(resp.results ?: emptyList())
        } catch (e: Exception) { Result.failure(e) }
    }
}
