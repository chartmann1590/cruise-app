package com.charles.cruiseapp.ui.screens

import com.charles.cruiseapp.ui.translation.TText
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.data.decks.DeckRepository
import com.charles.cruiseapp.data.decks.ShipEntry
import com.charles.cruiseapp.data.decks.bestImageUrl
import com.charles.cruiseapp.data.decks.findBestMatch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipCatalogScreen(
    cruiseShipName: String?,
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { DeckRepository(context) }
    var catalog by remember { mutableStateOf<com.charles.cruiseapp.data.decks.DeckCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf(cruiseShipName ?: "") }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        val res = repo.loadCatalog()
        if (res.isSuccess) {
            catalog = res.getOrNull()
            error = null
        } else error = res.exceptionOrNull()?.message
        loading = false
        // If cruise ship name matches, highlight via search
        if (!cruiseShipName.isNullOrBlank() && catalog != null) {
            val match = catalog!!.findBestMatch(cruiseShipName)
            if (match != null) search = match.displayName
        }
    }

    val filtered = remember(catalog, search) {
        val c = catalog ?: return@remember emptyList<ShipEntry>()
        if (search.isBlank()) c.ships else {
            val q = search.lowercase().trim()
            c.ships.filter {
                it.displayName.lowercase().contains(q) ||
                it.line.lowercase().contains(q) ||
                it.aliases.any { a -> a.lowercase().contains(q) } ||
                it.id.lowercase().contains(q.replace(" ", "-"))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TText("Ship Deck Maps") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            loading = true
                            val res = repo.loadCatalog(forceRefresh = true)
                            if (res.isSuccess) { catalog = res.getOrNull(); error = null } else error = res.exceptionOrNull()?.message
                            loading = false
                        }
                    }) { Icon(Icons.Default.Refresh, null) }
                }
            )
        },
        bottomBar = { BannerAd(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { TText("Search ship") },
                    placeholder = { TText("e.g. Symphony, Mardi Gras, Prima") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (cruiseShipName != null && catalog != null) {
                    val match = catalog!!.findBestMatch(cruiseShipName)
                    if (match != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    TText("Suggested for your cruise: ${match.displayName}", style = MaterialTheme.typography.titleSmall)
                                    TText("${match.line} • ${match.deckCount} decks • CC0 offline after download", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { onOpenDeck(match.id) }) { TText("View") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (loading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    Spacer(Modifier.height(8.dp))
                    TText("Loading ships…", style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            TText("Error: $error", color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = {
                                scope.launch { loading = true; val r = repo.loadCatalog(true); catalog = r.getOrNull(); error = r.exceptionOrNull()?.message; loading = false }
                            }) { TText("Retry") }
                        }
                    }
                }
                if (catalog != null) {
                    TText("${filtered.size} ships • ${catalog!!.ships.count { it.decks.isNotEmpty() }} with offline decks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(filtered) { ship ->
                val downloaded = repo.installedDecksCount(ship.id)
                val total = ship.decks.size
                val isDownloaded = downloaded == total && total > 0
                Card(
                    Modifier.fillMaxWidth().clickable { if (ship.decks.isNotEmpty()) onOpenDeck(ship.id) else {
                        ship.externalUrl?.let { url -> try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch(_:Exception){} }
                    } },
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ship.displayName, style = MaterialTheme.typography.titleMedium)
                                TText("${ship.line} • ${ship.shipClass} • ${if (total==0) "External link" else "$total decks"}", style = MaterialTheme.typography.bodySmall)
                                TText(ship.attribution.take(90), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            Badge(containerColor = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer) {
                                TText(if (isDownloaded) "Offline" else if (downloaded>0) "$downloaded/$total" else if (total==0) "Link" else "Online")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (total > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onOpenDeck(ship.id) },
                                    modifier = Modifier.weight(1f)
                                ) { Icon(Icons.Default.Map, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); TText("View decks") }
                                if (!isDownloaded) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                downloadingId = ship.id
                                                var done = 0
                                                ship.decks.forEachIndexed { idx, deck ->
                                                    progressText = "${idx+1}/${ship.decks.size} ${deck.file}"
                                                    val url = ship.bestImageUrl(deck)
                                                    val res = repo.downloadDeckImage(ship.id, deck, url)
                                                    if (res.isSuccess) done++
                                                }
                                                progressText = "Done $done/${ship.decks.size}"
                                                downloadingId = null
                                            }
                                        },
                                        enabled = downloadingId == null
                                    ) {
                                        if (downloadingId == ship.id) CircularProgressIndicator(Modifier.size(16.dp))
                                        else Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp)); TText("Download")
                                    }
                                } else {
                                    AssistChip(onClick = {}, label = { TText("✓ ${total} decks offline") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) })
                                }
                            }
                            if (downloadingId == ship.id && progressText.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp)); TText(progressText, style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            TText("Size ~${(ship.decks.sumOf { it.bytes } / 1024)} KB • Tap View to browse & pinch-zoom. Downloaded to filesDir/decks/ — works at sea.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            OutlinedButton(onClick = {
                                ship.externalUrl?.let { url -> try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch(_:Exception){} }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); TText("Open official deck plan")
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                TText("Deck images are original schematics (CC0), not official cruise line documents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}