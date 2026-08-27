package com.charles.cruiseapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.charles.cruiseapp.data.local.CruiseDatabase
import com.charles.cruiseapp.data.remote.WeatherRepository
import com.charles.cruiseapp.notifications.NotificationHelper
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

class CruiseApplication : Application() {
    val database by lazy { CruiseDatabase.getDatabase(this) }
    val weatherRepository by lazy { WeatherRepository() }

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        createNotificationChannels()
        // App startup performance trace
        try {
            val startupTrace = FirebasePerformance.getInstance().newTrace("app_startup")
            startupTrace.start()
            startupTrace.putAttribute("app_version", BuildConfig.VERSION_NAME)
            startupTrace.putAttribute("build_type", BuildConfig.BUILD_TYPE)
            // Record basic startup metric
            startupTrace.putMetric("startup_time_ms", 1)
            startupTrace.stop()
            FirebaseCrashlyticsUtils.log("CruiseApplication onCreate complete")
        } catch (e: Exception) {
            Log.w("CruiseApp", "Startup trace failed", e)
        }
    }

    private fun initFirebase() {
        try {
            // google-services.json auto-initializes FirebaseApp, but ensure explicit init
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            // Crashlytics: enable collection (disabled in debug by default if you want, here we enable always)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            FirebaseCrashlytics.getInstance().log("App initialized: ${BuildConfig.APPLICATION_ID} v${BuildConfig.VERSION_NAME}")
            FirebaseCrashlytics.getInstance().setCustomKey("app_version", BuildConfig.VERSION_NAME)
            FirebaseCrashlytics.getInstance().setCustomKey("build_type", BuildConfig.BUILD_TYPE)
            FirebaseCrashlytics.getInstance().setCustomKey("os_version", Build.VERSION.SDK_INT)

            // Performance Monitoring: enable collection
            FirebasePerformance.getInstance().isPerformanceCollectionEnabled = true

            // Optional: log that Firebase is ready via utils (also tests Crashlytics pipeline)
            FirebaseCrashlyticsUtils.log("Firebase Crashlytics & Performance initialized")
            Log.i("CruiseApp", "Firebase initialized for ${FirebaseApp.getInstance().options.projectId}")

            // Global uncaught exception handler -> also logs to Crashlytics before default
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    FirebaseCrashlytics.getInstance().recordException(throwable)
                    FirebaseCrashlytics.getInstance().log("Uncaught on ${thread.name}: ${throwable.message}")
                    // Ensure crashlytics sends immediately
                } catch (_: Exception) {}
                defaultHandler?.uncaughtException(thread, throwable)
            }
        } catch (e: Exception) {
            Log.e("CruiseApp", "Firebase init failed", e)
            // Record init failure as non-fatal so we can see it if Crashlytics partially works
            try { FirebaseCrashlyticsUtils.recordException(e) } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationHelper.CHANNEL_ID,
                "Cruise Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for planned cruise events"
                enableVibration(true)
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)

            val partyChannel = NotificationChannel(
                NotificationHelper.PARTY_CHANNEL_ID,
                "Party Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Messages from cruise party"
                enableVibration(true)
            }
            mgr.createNotificationChannel(partyChannel)
        }
    }
}
