package com.charles.cruiseapp.ui.screens

import com.charles.cruiseapp.ui.translation.TText
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.data.decks.DeckRepository
import com.charles.cruiseapp.data.decks.ShipEntry
import com.charles.cruiseapp.data.decks.bestImageUrl
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipDeckScreen(
    shipId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { DeckRepository(context) }
    var ship by remember { mutableStateOf<ShipEntry?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var downloadMsg by remember { mutableStateOf("") }

    LaunchedEffect(shipId) {
        loading = true
        val catRes = repo.loadCatalog()
        if (catRes.isSuccess) {
            ship = catRes.getOrNull()?.ships?.find { it.id == shipId }
            if (ship == null) error = "Ship not found: $shipId"
        } else error = catRes.exceptionOrNull()?.message
        loading = false
    }

    val decks = ship?.decks ?: emptyList()
    val pagerState = rememberPagerState(pageCount = { decks.size.coerceAtLeast(1) })

    LaunchedEffect(pagerState.currentPage) { activeIndex = pagerState.currentPage }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { TText(ship?.displayName ?: shipId); ship?.let { TText("${it.line} • ${it.decks.size} decks", style = MaterialTheme.typography.bodySmall) } } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    if (ship != null && decks.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                downloading = true
                                downloadMsg = "Downloading ${decks.size} decks…"
                                val ok = repo.downloadAllDecksForShip(ship!!) { cur, total -> downloadMsg = "$cur/$total" }
                                downloadMsg = "Downloaded $ok/${decks.size}"
                                downloading = false
                            }
                        }) { Icon(Icons.Default.Download, null) }
                    }
                    ship?.externalUrl?.let { url ->
                        IconButton(onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                        }) { Icon(Icons.Default.OpenInNew, null) }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                BannerAd(modifier = Modifier.fillMaxWidth())
                if (ship != null && decks.isNotEmpty()) {
                    Column(Modifier.padding(12.dp)) {
                        if (downloading) { LinearProgressIndicator(Modifier.fillMaxWidth()); TText(downloadMsg, style = MaterialTheme.typography.bodySmall) }
                        TText("Deck ${decks.getOrNull(activeIndex)?.number ?: "--"} — ${decks.getOrNull(activeIndex)?.name ?: ""}", style = MaterialTheme.typography.titleSmall)
                        TText("Pinch to zoom • drag to pan • swipe to change deck • ${if (repo.isDeckDownloaded(ship!!.id, decks[activeIndex])) "Offline ✓" else "Tap Download for offline sea days"}", style = MaterialTheme.typography.bodySmall)
                        ScrollableTabRow(selectedTabIndex = activeIndex) {
                            decks.forEachIndexed { idx, d ->
                                val dl = repo.isDeckDownloaded(ship!!.id, d)
                                Tab(selected = idx == activeIndex, onClick = { scope.launch { pagerState.animateScrollToPage(idx) } }, text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TText("${d.number}")
                                        if (dl) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.CheckCircle, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary) }
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when {
                loading -> { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                error != null -> { Column(Modifier.padding(24.dp)) { TText("Error: $error", color = MaterialTheme.colorScheme.error); Button(onClick = { scope.launch { loading = true; val r = repo.loadCatalog(true); ship = r.getOrNull()?.ships?.find { it.id == shipId }; error = r.exceptionOrNull()?.message; loading = false } }) { TText("Retry") } } }
                ship == null -> { TText("Ship not found") }
                decks.isEmpty() -> {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        TText("No offline deck images for ${ship!!.displayName}", style = MaterialTheme.typography.titleMedium)
                        TText(ship!!.attribution, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            ship!!.externalUrl?.let { url -> try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch(_:Exception){} }
                        }) { TText("Open official deck plan") }
                    }
                }
                else -> {
                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                        val deck = decks[page]
                        val file = repo.localDeckFile(ship!!.id, deck)
                        DeckZoomableImage(
                            imageUrl = ship!!.bestImageUrl(deck),
                            localFile = file,
                            contentDesc = deck.name
                        )
                    }
                }
            }
            if (ship != null) {
                TText("CC0-1.0 • Original schematic, not affiliated with ${ship!!.line} • © CruiseLoom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun DeckZoomableImage(imageUrl: String, localFile: File, contentDesc: String) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.6f, 5f)
        offset += panChange
    }
    // Reset on page change? keep per deck via key
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .transformable(state),
        contentAlignment = Alignment.Center
    ) {
        val model = if (localFile.exists() && localFile.length() > 1024) localFile else imageUrl
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = contentDesc,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .padding(8.dp)
        )
        // Zoom controls
        Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallFloatingActionButton(onClick = { scale = (scale * 1.25f).coerceAtMost(5f) }) { Icon(Icons.Default.ZoomIn, null) }
            SmallFloatingActionButton(onClick = { scale = (scale * 0.8f).coerceAtLeast(0.6f); if (scale <= 0.9f) offset = Offset.Zero }) { Icon(Icons.Default.ZoomOut, null) }
            SmallFloatingActionButton(onClick = { scale = 1f; offset = Offset.Zero }) { Icon(Icons.Default.CenterFocusStrong, null) }
        }
    }
}