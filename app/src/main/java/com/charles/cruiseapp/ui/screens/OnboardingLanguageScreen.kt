package com.charles.cruiseapp.ui.screens

import com.charles.cruiseapp.ui.translation.TText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.data.translation.DownloadState
import com.charles.cruiseapp.data.translation.SupportedLanguage
import com.charles.cruiseapp.data.translation.SupportedLanguages
import com.charles.cruiseapp.ui.translation.LanguageViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingLanguageScreen(
    onComplete: () -> Unit,
    onSkipToEnglish: () -> Unit = onComplete
) {
    val context = LocalContext.current
    val app = context.applicationContext as CruiseApplication
    val translationManager = app.translationManager
    val scope = rememberCoroutineScope()

    // Create VM manually (no DI framework)
    val vm = remember { LanguageViewModel(translationManager, context.applicationContext) }

    val selectedCode by vm.selectedCode.collectAsState()
    val isDownloading by vm.isDownloading.collectAsState()
    val downloadState by vm.downloadState.collectAsState()
    val error by vm.error.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()

    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingLang by remember { mutableStateOf<SupportedLanguage?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TText("Choose your language") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (error != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                TText(error ?: "", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.dismissError() }) { TText("Dismiss") }
                            }
                        }
                    }
                    if (isDownloading || downloadState == DownloadState.DOWNLOADING) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        TText("Downloading language model (~30 MB) — works offline after this. Keep Wi-Fi on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val selected = SupportedLanguages.fromCode(selectedCode)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    vm.selectLanguage("en")
                                    val ok = vm.confirmSelection()
                                    if (ok) onSkipToEnglish()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isDownloading
                        ) { TText("Continue in English") }
                        Button(
                            onClick = {
                                if (selectedCode == "en") {
                                    scope.launch {
                                        val ok = vm.confirmSelection()
                                        if (ok) onComplete()
                                    }
                                } else {
                                    // Show confirm download dialog
                                    pendingLang = selected
                                    showDownloadDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isDownloading
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                TText("Downloading…")
                            } else {
                                Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                TText(if (selectedCode == "en") "Continue" else "Download & Continue")
                            }
                        }
                    }
                    TText("You can change this anytime in Settings. English is always available offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        TText("Welcome aboard! 🌊", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    TText("Select your native language. We'll download a free offline translation model so every screen appears in your language — even at sea with no internet.", style = MaterialTheme.typography.bodyMedium)
                    TText("Powered by Google ML Kit — free, on-device, no account needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.onSearchChange(it) },
                placeholder = { TText("Search languages") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.height(8.dp))

            val filtered = remember(searchQuery, selectedCode) { vm.getFilteredLanguages() }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.code }) { lang ->
                    val isSelected = lang.code == selectedCode
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.selectLanguage(lang.code)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(lang.flag, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(lang.nativeName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    TText(lang.displayName + " • ${lang.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (lang.code != "en") TText("Tap Continue to download ~30 MB offline model", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else TText("No download needed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (showDownloadDialog && pendingLang != null) {
        val lang = pendingLang!!
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showDownloadDialog = false },
            title = { TText("Download ${lang.nativeName}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TText("We'll download the ${lang.displayName} translation model (~30 MB) using Google ML Kit. It's free and works offline after download — perfect for cruise sea days.")
                    TText("Keep Wi-Fi on until it finishes. You can still use the app in English meanwhile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (isDownloading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = vm.confirmSelection()
                            if (ok) {
                                showDownloadDialog = false
                                onComplete()
                            }
                        }
                    },
                    enabled = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp))
                        TText("Downloading…")
                    } else TText("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isDownloading) showDownloadDialog = false }, enabled = !isDownloading) { TText("Cancel") }
            }
        )
    }
}