package com.cruiseapp.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,weather_code",
        @Query("current") current: String = "temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): ForecastResponse

    @GET("v1/forecast")
    suspend fun getForecast16(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max,weather_code",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 16
    ): ForecastResponse
}

interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 5,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}

@Serializable
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("current") val current: CurrentWeather? = null,
    @SerialName("current_units") val currentUnits: Map<String,String>? = null,
    @SerialName("daily") val daily: DailyForecast? = null,
    @SerialName("daily_units") val dailyUnits: Map<String,String>? = null
)

@Serializable
data class CurrentWeather(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature2m: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null
)

@Serializable
data class DailyForecast(
    val time: List<String>? = null,
    @SerialName("temperature_2m_max") val tempMax: List<Double>? = null,
    @SerialName("temperature_2m_min") val tempMin: List<Double>? = null,
    @SerialName("precipitation_probability_max") val precipProb: List<Int?>? = null,
    @SerialName("precipitation_sum") val precipSum: List<Double?>? = null,
    @SerialName("wind_speed_10m_max") val windMax: List<Double?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null
)

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

@Serializable
data class GeocodingResult(
    val id: Long? = null,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("admin1") val admin1: String? = null
)
