package com.charles.cruiseapp.data.translation

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

object LanguagePreferences {
    const val PREFS_NAME = "cruise_settings" // reuse same file as UnitUtils for simplicity
    const val KEY_LANGUAGE = "app_language" // e.g. "en", "es"
    const val KEY_LANGUAGE_ONBOARDED = "language_onboarded"

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun setLanguage(context: Context, code: String) {
        val normalized = code.lowercase().ifBlank { "en" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            .apply()
    }

    /** Called strictly when onboarding walkthrough is completed */
    fun setOnboarded(context: Context, onboarded: Boolean = true) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LANGUAGE_ONBOARDED, onboarded).apply()
    }

    fun isOnboarded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LANGUAGE_ONBOARDED, false)
    }

    fun isLanguageSelected(context: Context): Boolean = isOnboarded(context)

    fun observeLanguage(context: Context): Flow<String> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LANGUAGE) trySend(getLanguage(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getLanguage(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun observeOnboarded(context: Context): Flow<Boolean> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LANGUAGE_ONBOARDED) trySend(isOnboarded(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(isOnboarded(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun observeIsEnglish(context: Context): Flow<Boolean> = observeLanguage(context).map { it == "en" }
}