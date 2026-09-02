package com.charles.cruiseapp.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

enum class UnitSystem { METRIC, IMPERIAL }

object UnitUtils {
    const val PREFS_NAME = "cruise_settings"
    const val KEY_UNIT = "unit_system" // "metric" or "imperial"

    fun getUnitSystem(context: Context): UnitSystem {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_UNIT, "metric")) {
            "imperial" -> UnitSystem.IMPERIAL
            else -> UnitSystem.METRIC
        }
    }

    fun isMetric(context: Context): Boolean = getUnitSystem(context) == UnitSystem.METRIC

    fun setUnitSystem(context: Context, system: UnitSystem) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_UNIT, if (system == UnitSystem.IMPERIAL) "imperial" else "metric").apply()
    }

    fun observeUnitSystem(context: Context): Flow<UnitSystem> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_UNIT) trySend(getUnitSystem(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getUnitSystem(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun observeIsMetric(context: Context): Flow<Boolean> = observeUnitSystem(context).map { it == UnitSystem.METRIC }

    // Conversions
    fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0
    fun fahrenheitToCelsius(f: Double): Double = (f - 32) * 5.0 / 9.0
    fun kmhToMph(kmh: Double): Double = kmh * 0.621371
    fun mphToKmh(mph: Double): Double = mph / 0.621371
    fun mmToInches(mm: Double): Double = mm / 25.4
    fun inchesToMm(inch: Double): Double = inch * 25.4
    fun kmToMiles(km: Double): Double = km * 0.621371

    // Formatting helpers — input is always metric (Open-Meteo returns metric)
    fun formatTemp(celsius: Double?, isMetric: Boolean): String {
        if (celsius == null) return "--"
        return if (isMetric) "${celsius.toInt()}°C" else "${celsiusToFahrenheit(celsius).toInt()}°F"
    }
    fun formatTempRange(minC: Double?, maxC: Double?, isMetric: Boolean): String {
        if (minC == null || maxC == null) return "--"
        return if (isMetric) "${minC.toInt()}° / ${maxC.toInt()}°C"
        else "${celsiusToFahrenheit(minC).toInt()}° / ${celsiusToFahrenheit(maxC).toInt()}°F"
    }
    fun formatWind(kmh: Double?, isMetric: Boolean): String {
        if (kmh == null) return "--"
        return if (isMetric) "${kmh.toInt()} km/h" else "${kmhToMph(kmh).toInt()} mph"
    }
    fun formatWindShort(kmh: Double?, isMetric: Boolean): String {
        if (kmh == null) return "--"
        return if (isMetric) "Wind ${kmh.toInt()} km/h" else "Wind ${kmh.toInt()} mph".let { "${kmhToMph(kmh).toInt()} mph" }
    }
    fun formatPrecipitation(prob: Int?): String = if (prob == null) "" else "Rain $prob%"

    // For cached temps stored as Double in WeatherCache (metric) — convert on display
    fun formatCachedTempRange(minC: Double?, maxC: Double?, isMetric: Boolean): String =
        formatTempRange(minC, maxC, isMetric)
}
