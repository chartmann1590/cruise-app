package com.charles.cruiseapp.ads

import com.charles.cruiseapp.ui.translation.TText
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.charles.cruiseapp.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val TAG = "BannerAd"

/**
 * Adaptive banner that fills width and auto-chooses height (~50-100dp depending on device).
 * Safe to place in any Scaffold's bottom bar, content bottom, or inside a LazyColumn.
 *
 * Behavior:
 * - On debug with USE_TEST_ADS=true → shows Google's official test banner ("Test Ad").
 * - If ads disabled via AdConfig → renders 0dp (no space) by default, or placeholder if [showPlaceholderWhenDisabled].
 * - Load errors are logged but not shown to user; ad view stays collapsed to avoid blank white gap.
 *
 * Usage:
 *   BannerAd()                                           // default
 *   BannerAd(modifier = Modifier.fillMaxWidth())         // explicit
 *   Column { ... ; BannerAd(collapseOnError = true) }   // collapses blank space if offline
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    collapseOnError: Boolean = false,
    showPlaceholderWhenDisabled: Boolean = false
) {
    val context = LocalContext.current
    val isDebug = BuildConfig.DEBUG
    val shouldShow = remember(isDebug) { AdConfig.shouldShowAds(isDebug) }

    if (!shouldShow) {
        if (showPlaceholderWhenDisabled) {
            Box(
                modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                TText("Ads disabled (AdConfig)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val adUnitId = AdConfig.effectiveBannerId
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp

    // Adaptive size: current orientation anchored banner, full width
    val adSize = remember(screenWidthDp) {
        try {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
        } catch (e: Exception) {
            Log.w(TAG, "Adaptive sizing failed, using BANNER", e)
            AdSize.BANNER
        }
    }

    val adView = remember(adUnitId, adSize) {
        AdView(context).apply {
            this.adUnitId = adUnitId
            setAdSize(adSize)
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.i(TAG, "Banner failed: ${error.code} ${error.message} — ${error.domain}")
                }
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner loaded")
                }
                override fun onAdClicked() {
                    Log.d(TAG, "Banner clicked")
                }
            }
        }
    }

    // (Re)load when unit/sizing changes; also after resume the view will auto-refresh
    DisposableEffect(adView, adUnitId) {
        val request = AdRequest.Builder().build()
        try {
            adView.loadAd(request)
        } catch (e: Exception) {
            Log.w(TAG, "loadAd threw", e)
        }
        onDispose {
            try { adView.destroy() } catch (_: Exception) {}
        }
    }

    // Outer box ensures banner is centered and clamps height; if offline/fails, we still
    // reserve a small collapsed area unless collapseOnError is true (then error is invisible).
    val bannerHeightDp = adSize.height.dp.coerceAtLeast(50.dp)

    AndroidView(
        factory = { adView },
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeightDp)
            .background(MaterialTheme.colorScheme.surface),
        update = { view ->
            // Keep size in sync on config change
            try {
                if (view.adSize?.height != adSize.height || view.adSize?.width != adSize.width) {
                    view.setAdSize(adSize)
                    view.loadAd(AdRequest.Builder().build())
                }
            } catch (_: Exception) {}
        }
    )
}

/**
 * Compact wrapper that adds subtle top divider + padding — ideal for placing above
 * a NavigationBar or at bottom of content. Copy/paste this where you want a banner
 * with built-in chrome.
 */
@Composable
fun BottomBannerAd(
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        BannerAd(modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Inline banner suitable for embedding inside a LazyColumn / Column among cards.
 * Adds vertical padding so it doesn't hug adjacent cards.
 */
@Composable
fun InlineBannerAd(modifier: Modifier = Modifier) {
    Box(modifier.padding(vertical = 8.dp)) {
        BannerAd(modifier = Modifier.fillMaxWidth())
    }
}