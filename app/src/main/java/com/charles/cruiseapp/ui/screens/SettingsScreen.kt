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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.BuildConfig
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.ads.AdConfig
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.ads.GlobalInterstitial
import com.charles.cruiseapp.data.translation.DownloadState
import com.charles.cruiseapp.data.translation.LanguagePreferences
import com.charles.cruiseapp.data.translation.SupportedLanguages
import com.charles.cruiseapp.ui.translation.LanguageViewModel
import com.charles.cruiseapp.ui.translation.TText
import com.charles.cruiseapp.ui.translation.rememberTranslatedText
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
    val app = context.applicationContext as CruiseApplication
    val translationManager = app.translationManager
    val langVm = remember { LanguageViewModel(translationManager, context.applicationContext) }
    var currentLang by remember { mutableStateOf(LanguagePreferences.getLanguage(context)) }
    var downloadState by remember { mutableStateOf(translationManager.downloadState.value) }
    var isDownloading by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }
    var langError by remember { mutableStateOf<String?>(null) }
    var searchLangQuery by remember { mutableStateOf("") }

    // Observe external changes
    LaunchedEffect(Unit) {
        UnitUtils.observeUnitSystem(context).collect { selected = it }
    }
    LaunchedEffect(Unit) {
        LanguagePreferences.observeLanguage(context).collect { currentLang = it }
    }
    LaunchedEffect(Unit) {
        translationManager.downloadState.collect { downloadState = it }
    }
    LaunchedEffect(Unit) {
        langVm.isDownloading.collect { isDownloading = it }
    }
    LaunchedEffect(Unit) {
        langVm.error.collect { langError = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TText("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        bottomBar = { BannerAd(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()){
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // ── Language card ──
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TText("🌐 Language", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        TText("Every screen is translated on-device via free ML Kit — works offline at sea after download.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                        val curLang = SupportedLanguages.fromCode(currentLang)
                        Card(
                            onClick = { showLangPicker = true },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(curLang.flag, style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        TText("${curLang.nativeName} • ${curLang.displayName}", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Text(curLang.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (currentLang != "en") {
                                            val stateLabel = when (downloadState) {
                                                DownloadState.DOWNLOADED -> "✓ Ready offline"
                                                DownloadState.DOWNLOADING -> "Downloading…"
                                                DownloadState.FAILED -> "Download failed"
                                                else -> "Tap to manage model (~30 MB)"
                                            }
                                            Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = when (downloadState) { DownloadState.FAILED -> MaterialTheme.colorScheme.error; DownloadState.DOWNLOADED -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                                        } else {
                                            TText("No download needed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (isDownloading || downloadState == DownloadState.DOWNLOADING) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            TText("Downloading language model… keep internet on. Translation works offline after this.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        if (langError != null) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TText(langError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { langVm.dismissError() }) { TText("Dismiss") }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(onClick = { showLangPicker = true }, modifier = Modifier.weight(1f), enabled = !isDownloading) { TText("Change language") }
                            if (downloadState == DownloadState.FAILED) {
                                Button(onClick = { langVm.retryDownload {} }, enabled = !isDownloading) { TText("Retry") }
                            }
                        }
                        TText("Powered by Google ML Kit — free, on-device. Change anytime.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }

                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TText("⚙️ Units", style = MaterialTheme.typography.titleMedium)
                        TText("Choose how temperatures, wind and distances appear everywhere in the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                                    TText("Metric", style = MaterialTheme.typography.titleMedium, color = if (selected == UnitSystem.METRIC) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    TText("°C • km/h • mm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TText("Celsius, kilometers per hour, millimeters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    TText("Imperial", style = MaterialTheme.typography.titleMedium, color = if (selected == UnitSystem.IMPERIAL) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    TText("°F • mph • in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TText("Fahrenheit, miles per hour, inches", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (selected == UnitSystem.IMPERIAL) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { selected = UnitSystem.METRIC; UnitUtils.setUnitSystem(context, UnitSystem.METRIC) }, label = { TText("Example: 22°C → ${UnitUtils.formatTemp(22.0, true)}") })
                            AssistChip(onClick = { selected = UnitSystem.IMPERIAL; UnitUtils.setUnitSystem(context, UnitSystem.IMPERIAL) }, label = { TText("22°C → ${UnitUtils.formatTemp(22.0, false)}") })
                        }
                        TText("Applies everywhere temperatures, wind and distances are shown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TText("🚢 Cruise setup", style = MaterialTheme.typography.titleSmall)
                        TText("Change ship name or sail dates.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onNavigateToCruiseSetup, modifier = Modifier.fillMaxWidth()) { TText("Edit cruise") }
                    }
                }

                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TText("Privacy & support", style = MaterialTheme.typography.titleSmall)
                        TText(
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
                        ) { TText("Open privacy policy") }
                        if (UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
                            TextButton(
                                onClick = {
                                    val activity = context as? android.app.Activity ?: return@TextButton
                                    UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { TText("Manage ad privacy choices") }
                        }
                    }
                }

                // ── Ads debug / info ──
                if (BuildConfig.DEBUG) Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TText("📢 Ads", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (AdConfig.USE_TEST_ADS) "Using Google test ads (safe for debug). Banners show on every screen; interstitials after every ${AdConfig.INTERSTITIAL_MIN_ACTIONS} navigations (90s cooldown)."
                            else "Using your production AdMob IDs.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TText("Banner: ${AdConfig.effectiveBannerId.take(32)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TText("Interstitial: ${AdConfig.effectiveInterstitialId.take(32)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TText("App ID: ${AdConfig.ADMOB_APP_ID}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                TText(if (ready.value) "Show interstitial" else "Preload interstitial")
                            }
                            FilledTonalButton(onClick = {
                                GlobalInterstitial.manager.resetCounters()
                                GlobalInterstitial.manager.preload(context)
                                Toast.makeText(context, "Ads reset & preloading", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            }
                        }
                        TText("Banners are above — you should see a test banner at the bottom of this screen. Interstitial triggers automatically at navigation breaks; use the button above to test immediately (ignores cooldown).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!BuildConfig.DEBUG && AdConfig.USE_TEST_ADS) {
                            TText("⚠️ Test ads are enabled in a release build — set AdConfig.USE_TEST_ADS = false before Play Store release.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            TText("v${BuildConfig.VERSION_NAME} • ${selected.name.lowercase()} • ${SupportedLanguages.fromCode(currentLang).nativeName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)
            )
        }
    }

    // ── Language picker dialog ──
    if (showLangPicker) {
        var localQuery by remember { mutableStateOf(searchLangQuery) }
        val filtered = remember(localQuery) {
            if (localQuery.isBlank()) SupportedLanguages.ALL
            else SupportedLanguages.ALL.filter {
                it.displayName.contains(localQuery, ignoreCase = true) ||
                it.nativeName.contains(localQuery, ignoreCase = true) ||
                it.code.contains(localQuery, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showLangPicker = false },
            title = { TText("Select language") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = localQuery,
                        onValueChange = { localQuery = it; searchLangQuery = it },
                        placeholder = { TText("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    if (isDownloading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        TText("Downloading…", style = MaterialTheme.typography.bodySmall)
                    }
                    if (langError != null) {
                        TText(langError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered.size) { idx ->
                            val lang = filtered[idx]
                            val isSelected = lang.code == currentLang
                            Card(
                                onClick = {
                                    if (isDownloading) return@Card
                                    langVm.changeLanguage(lang.code) { ok ->
                                        if (ok) showLangPicker = false
                                    }
                                },
                                shape = MaterialTheme.shapes.small,
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(lang.flag)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            TText("${lang.nativeName} • ${lang.displayName}", style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null)
                                            Text(lang.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    TText("Model ~30 MB — free via Google ML Kit, works offline after download.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { if (!isDownloading) showLangPicker = false }) { TText("Close") }
            }
        )
    }
}