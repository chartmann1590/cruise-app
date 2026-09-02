package com.charles.cruiseapp.ads

import com.charles.cruiseapp.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Central place for AdMob IDs + ad policy.
 *
 * **Security: Production IDs are NEVER hardcoded here.**
 * They are injected at build time from `app/admob.properties` (gitignored)
 * via BuildConfig fields `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`.
 * See `app/admob.properties.example` for the setup template.
 * If that file is missing (fresh clone, CI, public PR) the build falls back to
 * Google's official test IDs so the project still builds and shows test ads.
 *
 * Build wiring: `app/build.gradle.kts` reads `admob.properties` (and
 * `local.properties`/`secrets.properties`/env vars) and sets:
 *  - manifestPlaceholders["admobAppId"] → AndroidManifest <meta-data>
 *  - buildConfigField ADMOB_* → BuildConfig
 *  - resValue admob_app_id → @string/admob_app_id (legacy manifest string)
 * - So no file that is checked into git ever contains your real ca-app-pub-... IDs.
 */
object AdConfig {

    /** Updated by UMP after the consent check on every app launch. */
    var consentAllowsAds by mutableStateOf(false)
        private set

    fun updateConsent(canRequestAds: Boolean) {
        consentAllowsAds = canRequestAds
    }

    // ── Injected from BuildConfig (real prod locally, test fallback on CI) ──
    /** AdMob App ID — also used for <meta-data com.google.android.gms.ads.APPLICATION_ID> */
    val ADMOB_APP_ID: String get() = BuildConfig.ADMOB_APP_ID
    val BANNER_AD_UNIT_ID: String get() = BuildConfig.ADMOB_BANNER_ID
    val INTERSTITIAL_AD_UNIT_ID: String get() = BuildConfig.ADMOB_INTERSTITIAL_ID

    // ── Test IDs (for reference / detecting unconfigured builds) ──
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    /**
     * True when the effective IDs are still Google's test IDs, meaning
     * `admob.properties` has not been configured with real IDs.
     * Useful for debug UI (e.g. SettingsScreen badge).
     */
    val USE_TEST_ADS: Boolean
        get() = ADMOB_APP_ID.contains("3940256099942544") ||
            BANNER_AD_UNIT_ID.contains("3940256099942544")

    /** Effective banner ID that should be passed to AdView */
    val effectiveBannerId: String get() = BANNER_AD_UNIT_ID

    /** Effective interstitial ID that should be passed to InterstitialAd.load() */
    val effectiveInterstitialId: String get() = INTERSTITIAL_AD_UNIT_ID

    /** Global kill switch — set false to disable all ads (e.g. paid remove-ads variant) */
    const val ENABLE_ADS = true

    /** Also show banners/interstitials on debug builds? (if false, ads only on release) */
    const val SHOW_ADS_ON_DEBUG = true

    // ── Interstitial frequency / policy ──
    /** Minimum time between two interstitial shows */
    const val INTERSTITIAL_COOLDOWN_MS = 90_000L // 90s — tweak as needed (AdMob recommends not too frequent)
    /** Require N user actions (navigations) before first interstitial can show */
    const val INTERSTITIAL_MIN_ACTIONS = 2
    /** Preload next interstitial automatically after showing/dismissing */
    const val AUTO_RELOAD_INTERSTITIAL = true

    /** Whether ads should be attempted in this build at runtime */
    fun shouldShowAds(isDebugBuild: Boolean): Boolean {
        if (!ENABLE_ADS) return false
        if (!consentAllowsAds) return false
        if (isDebugBuild && !SHOW_ADS_ON_DEBUG) return false
        return true
    }
}
