package com.charles.cruiseapp.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.charles.cruiseapp.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val TAG = "InterstitialAd"

/**
 * Manages preload + show for interstitial ads.
 *
 * Usage (Compose / Activity):
 *   // In your NavHost / Activity onCreate:
 *   val interstitial = remember { InterstitialAdManager() }
 *   LaunchedEffect(Unit) { interstitial.preload(context) }
 *
 *   // At a natural break (e.g. after tapping a port's Weather, or after navigating):
 *   if (interstitial.canShow()) {
 *       interstitial.show(activity) { interstitial.preload(context) }
 *   }
 *
 * Frequency is throttled via AdConfig.INTERSTITIAL_COOLDOWN_MS and a simple
 * action counter so you don't spam the user.
 */
class InterstitialAdManager {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var lastShowMs = 0L
    private var loadFailCount = 0

    // Counts navigations / key actions since last interstitial; incremented externally via onUserAction()
    private var actionsSinceLastAd = 0

    val isReady: Boolean get() = interstitialAd != null
    val isCurrentlyLoading: Boolean get() = isLoading

    /**
     * Call on transitions you consider "user actions" (navigations, button taps that change screen).
     * When [AdConfig.INTERSTITIAL_MIN_ACTIONS] is reached, the next [canShow] will be true.
     */
    fun onUserAction() {
        actionsSinceLastAd++
    }

    /** Whether it's a reasonable time to show an interstitial (respects cooldown + min actions). */
    fun canShow(): Boolean {
        if (!AdConfig.shouldShowAds(BuildConfig.DEBUG)) return false
        if (interstitialAd == null) return false
        val now = System.currentTimeMillis()
        if (now - lastShowMs < AdConfig.INTERSTITIAL_COOLDOWN_MS) return false
        if (actionsSinceLastAd < AdConfig.INTERSTITIAL_MIN_ACTIONS) return false
        return true
    }

    /** For debugging / settings screen: force canShow ignoring counters (but still needs loaded ad). */
    fun canShowIgnoringCooldown(): Boolean = interstitialAd != null

    /** Preload next interstitial. Safe to call repeatedly — deduped while loading. */
    fun preload(context: Context) {
        if (!AdConfig.shouldShowAds(BuildConfig.DEBUG)) {
            Log.d(TAG, "Ads disabled — skipping preload")
            return
        }
        if (isLoading) return
        if (interstitialAd != null) return // already have one queued

        val appCtx = context.applicationContext
        val adId = AdConfig.effectiveInterstitialId
        isLoading = true
        Log.d(TAG, "Loading interstitial: $adId")
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            appCtx,
            adId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial loaded")
                    isLoading = false
                    loadFailCount = 0
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.i(TAG, "Interstitial failed: ${error.code} ${error.message} domain=${error.domain} response=${error.responseInfo?.responseId}")
                    isLoading = false
                    interstitialAd = null
                    loadFailCount++
                    // Don't busy-retry; caller can retry after delay or next navigation.
                }
            }
        )
    }

    /**
     * Show if loaded. Returns true if shown, false if not ready.
     * [onDismissedOrFailed] is called after dismiss OR if show failed, so caller can preload next.
     */
    fun show(
        activity: Activity,
        onDismissedOrFailed: (() -> Unit)? = null
    ): Boolean {
        val ad = interstitialAd
        if (ad == null) {
            Log.d(TAG, "show() called but no ad ready")
            onDismissedOrFailed?.invoke()
            return false
        }
        // Extra safety: don't show if finishing
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity finishing — not showing interstitial")
            onDismissedOrFailed?.invoke()
            return false
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed")
                interstitialAd = null
                lastShowMs = System.currentTimeMillis()
                actionsSinceLastAd = 0
                onDismissedOrFailed?.invoke()
                if (AdConfig.AUTO_RELOAD_INTERSTITIAL) {
                    preload(activity.applicationContext)
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.i(TAG, "Interstitial failed to show: ${error.code} ${error.message}")
                interstitialAd = null
                onDismissedOrFailed?.invoke()
                if (AdConfig.AUTO_RELOAD_INTERSTITIAL) {
                    preload(activity.applicationContext)
                }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial showed")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Interstitial clicked")
            }

            override fun onAdImpression() {
                Log.d(TAG, "Interstitial impression")
            }
        }

        return try {
            ad.show(activity)
            // Clear reference immediately; callback will reset bookkeeping on dismiss
            interstitialAd = null
            lastShowMs = System.currentTimeMillis() // set early to enforce cooldown even if dismissed quickly
            true
        } catch (e: Exception) {
            Log.w(TAG, "ad.show threw", e)
            interstitialAd = null
            onDismissedOrFailed?.invoke()
            false
        }
    }

    /** Force show ignoring cooldown/action-count (for testing from a button). */
    fun showIfReady(
        activity: Activity,
        onDismissedOrFailed: (() -> Unit)? = null
    ): Boolean {
        if (interstitialAd == null) {
            Log.d(TAG, "showIfReady: no ad cached — preloading now")
            preload(activity)
            return false
        }
        return show(activity, onDismissedOrFailed)
    }

    /** Reset cooldown/action counters — useful for tests or after user opts-in to ads. */
    fun resetCounters() {
        lastShowMs = 0L
        actionsSinceLastAd = AdConfig.INTERSTITIAL_MIN_ACTIONS // allow next canShow immediately
    }
}

/**
 * App-wide singleton — keep one instance so preload state is shared across screens.
 * Prefer injecting/remembering via app, but singleton is simplest for immediate use.
 */
object GlobalInterstitial {
    val manager = InterstitialAdManager()
}
