package com.charles.cruiseapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.widget.Toast
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.BuildConfig
import com.charles.cruiseapp.ads.AdConfig
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.ads.GlobalInterstitial
import com.charles.cruiseapp.util.UnitSystem
import com.charles.cruiseapp.util.UnitUtils
import kotlinx.coroutines.launch
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToCruiseSetup: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(UnitUtils.getUnitSystem(context)) }

    // Observe external changes
    LaunchedEffect(Unit) {
        UnitUtils.observeUnitSystem(context).collect { selected = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        bottomBar = { BannerAd(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()){
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚙️ Units", style = MaterialTheme.typography.titleMedium)
                        Text("Choose how temperatures, wind and distances appear everywhere in the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // Metric card
                        Card(
                            onClick = {
                                selected = UnitSystem.METRIC
                                UnitUtils.setUnitSystem(context, UnitSystem.METRIC)
                            },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected == UnitSystem.METRIC) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("Metric", style = MaterialTheme.typography.titleMedium, color = if (selected == UnitSystem.METRIC) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    Text("°C • km/h • mm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Celsius, kilometers per hour, millimeters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (selected == UnitSystem.METRIC) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        // Imperial card
                        Card(
                            onClick = {
                                selected = UnitSystem.IMPERIAL
                                UnitUtils.setUnitSystem(context, UnitSystem.IMPERIAL)
                            },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected == UnitSystem.IMPERIAL) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("Imperial", style = MaterialTheme.typography.titleMedium, color = if (selected == UnitSystem.IMPERIAL) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    Text("°F • mph • in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Fahrenheit, miles per hour, inches", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (selected == UnitSystem.IMPERIAL) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { selected = UnitSystem.METRIC; UnitUtils.setUnitSystem(context, UnitSystem.METRIC) }, label = { Text("Example: 22°C → ${UnitUtils.formatTemp(22.0, true)}") })
                            AssistChip(onClick = { selected = UnitSystem.IMPERIAL; UnitUtils.setUnitSystem(context, UnitSystem.IMPERIAL) }, label = { Text("22°C → ${UnitUtils.formatTemp(22.0, false)}") })
                        }
                        Text("Applies everywhere temperatures, wind and distances are shown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🚢 Cruise setup", style = MaterialTheme.typography.titleSmall)
                        Text("Change ship name or sail dates.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onNavigateToCruiseSetup, modifier = Modifier.fillMaxWidth()) { Text("Edit cruise") }
                    }
                }

                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Privacy & support", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "See how CruiseLoom handles local cruise data, location, nearby connections, diagnostics, and advertising.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://cruise-app-2026.web.app/privacy"))
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open privacy policy") }
                        if (UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
                            TextButton(
                                onClick = {
                                    val activity = context as? android.app.Activity ?: return@TextButton
                                    UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Manage ad privacy choices") }
                        }
                    }
                }

                // ── Ads debug / info ──
                if (BuildConfig.DEBUG) Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📢 Ads", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (AdConfig.USE_TEST_ADS) "Using Google test ads (safe for debug). Banners show on every screen; interstitials after every ${AdConfig.INTERSTITIAL_MIN_ACTIONS} navigations (90s cooldown)."
                            else "Using your production AdMob IDs.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Banner: ${AdConfig.effectiveBannerId.take(32)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Interstitial: ${AdConfig.effectiveInterstitialId.take(32)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("App ID: ${AdConfig.ADMOB_APP_ID}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val ready = remember { mutableStateOf(GlobalInterstitial.manager.isReady) }
                        LaunchedEffect(Unit) {
                            // poll ready state briefly
                            while (true) {
                                ready.value = GlobalInterstitial.manager.isReady
                                kotlinx.coroutines.delay(1500)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val act = context as? android.app.Activity
                                    if (act == null) {
                                        Toast.makeText(context, "No activity", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (GlobalInterstitial.manager.isReady) {
                                        val shown = GlobalInterstitial.manager.showIfReady(act) {
                                            Toast.makeText(context, "Interstitial dismissed — preloading next", Toast.LENGTH_SHORT).show()
                                        }
                                        if (!shown) Toast.makeText(context, "Show failed", Toast.LENGTH_SHORT).show()
                                    } else {
                                        GlobalInterstitial.manager.preload(context)
                                        Toast.makeText(context, "Preloading interstitial… try again in 2-3s", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (ready.value) "Show interstitial" else "Preload interstitial")
                            }
                            FilledTonalButton(onClick = {
                                GlobalInterstitial.manager.resetCounters()
                                GlobalInterstitial.manager.preload(context)
                                Toast.makeText(context, "Ads reset & preloading", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            }
                        }
                        Text("Banners are above — you should see a test banner at the bottom of this screen. Interstitial triggers automatically at navigation breaks; use the button above to test immediately (ignores cooldown).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!BuildConfig.DEBUG && AdConfig.USE_TEST_ADS) {
                            Text("⚠️ Test ads are enabled in a release build — set AdConfig.USE_TEST_ADS = false before Play Store release.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Text(
                "v${BuildConfig.VERSION_NAME} • ${selected.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)
            )
        }
    }
}
