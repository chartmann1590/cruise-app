package com.charles.cruiseapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.charles.cruiseapp.ads.AdConfig
import com.charles.cruiseapp.ads.GlobalInterstitial
import com.charles.cruiseapp.data.local.CruiseDatabase
import com.charles.cruiseapp.data.party.PartyChatRepository
import com.charles.cruiseapp.data.remote.PlacesRepository
import com.charles.cruiseapp.data.remote.WeatherRepository
import com.charles.cruiseapp.notifications.NotificationHelper
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean

class CruiseApplication : Application(), ImageLoaderFactory {
    private val adsInitialized = AtomicBoolean(false)
    val database by lazy { CruiseDatabase.getDatabase(this) }
    val weatherRepository by lazy { WeatherRepository() }
    val placesRepository by lazy { PlacesRepository() }
    val partyChatRepository by lazy { PartyChatRepository(applicationContext, database) }
    // HotspotController is app-scoped for HotspotChatService + UI to share same state (Phase 4)
    // Lazy so it only initializes if/when the guest chat feature is used.
    val hotspotController by lazy { com.charles.cruiseapp.data.hotspot.HotspotController(applicationContext) }
    // Translation — ML Kit on-device (free, offline after model download)
    val translationManager by lazy { com.charles.cruiseapp.data.translation.TranslationManager(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        createNotificationChannels()
        initOsm()
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

            val hotspotChannel = NotificationChannel(
                NotificationHelper.HOTSPOT_CHANNEL_ID,
                "Guest Wi-Fi Chat",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while guest Wi-Fi chat is running"
            }
            mgr.createNotificationChannel(hotspotChannel)

            val countdownChannel = NotificationChannel(
                NotificationHelper.COUNTDOWN_CHANNEL_ID,
                "Cruise Countdown",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily countdown to your next cruise"
            }
            mgr.createNotificationChannel(countdownChannel)
        }
    }

    override fun newImageLoader(): ImageLoader {
        // Wikimedia's image CDN (upload.wikimedia.org) returns 403 for requests with a
        // generic/missing User-Agent — Coil's default OkHttp client gets blocked without this.
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                            .header("User-Agent", "CruiseLoomApp/1.0 (offline cruise itinerary app; no contact url)")
                            .build()
                        chain.proceed(req)
                    }
                    .build()
            }
            .build()
    }

    private fun initOsm() {
        try {
            val cfg = org.osmdroid.config.Configuration.getInstance()
            val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
            cfg.load(this, prefs)
            cfg.userAgentValue = BuildConfig.APPLICATION_ID
            cfg.osmdroidBasePath = getExternalFilesDir(null) ?: filesDir
            cfg.osmdroidTileCache = java.io.File(cfg.osmdroidBasePath, "osmdroid/tiles")
            // Ensure cache dir exists
            cfg.osmdroidTileCache.mkdirs()
        } catch (e: Exception) {
            Log.w("CruiseApp", "OSM init failed", e)
        }
    }

    fun initializeAdsAfterConsent() {
        if (!adsInitialized.compareAndSet(false, true)) return
        try {
            if (!AdConfig.shouldShowAds(BuildConfig.DEBUG)) {
                Log.i("CruiseApp", "Ads disabled via AdConfig — skipping MobileAds init")
                return
            }
            // MobileAds init is async and safe to call early; no ad will show until a banner/interstitial is requested.
            MobileAds.initialize(this) { status ->
                val map = status.adapterStatusMap
                Log.i("CruiseApp", "MobileAds init complete: ${map.keys.joinToString()}")
                map.forEach { (adapter, st) ->
                    Log.d("CruiseApp", "  adapter $adapter latency=${st.latency} state=${st.initializationState} desc=${st.description}")
                }
                // Preload the first interstitial so it's ready by first natural break
                try { GlobalInterstitial.manager.preload(this) } catch (e: Exception) {
                    Log.w("CruiseApp", "Interstitial preload failed", e)
                }
            }
        } catch (e: Exception) {
            Log.w("CruiseApp", "MobileAds init failed", e)
            try { FirebaseCrashlyticsUtils.recordException(e) } catch (_: Exception) {}
        }
    }
}
