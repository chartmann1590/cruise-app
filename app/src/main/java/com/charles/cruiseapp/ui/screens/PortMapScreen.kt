package com.charles.cruiseapp.ui.screens

import com.charles.cruiseapp.ui.translation.TText
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.navigationBarsPadding
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.data.local.PortStop
import com.charles.cruiseapp.ui.components.EmptyState
import com.charles.cruiseapp.util.formatDate
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.StateFlow
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PortMapScreen(
    portsFlow: StateFlow<List<PortStop>>,
    onBack: () -> Unit,
    onWeatherClick: (PortStop) -> Unit
) {
    val context = LocalContext.current
    val ports by portsFlow.collectAsState()
    var useOfflineOnly by remember { mutableStateOf(false) }
    var selectedPort by remember { mutableStateOf<PortStop?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var showLocationDot by remember { mutableStateOf(false) }

    val locationPerm = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Keep map centered on itinerary
    LaunchedEffect(ports, useOfflineOnly) {
        val mv = mapViewRef ?: return@LaunchedEffect
        updateMap(mv, context, ports, selectedPort, myLocationOverlay, showLocationDot, useOfflineOnly)
    }
    LaunchedEffect(selectedPort) {
        val mv = mapViewRef ?: return@LaunchedEffect
        updateMap(mv, context, ports, selectedPort, myLocationOverlay, showLocationDot, useOfflineOnly)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TText("Port Map • ${ports.size} stops") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        // recenter
                        mapViewRef?.let { mv ->
                            if (ports.isNotEmpty()) {
                                val bbox = BoundingBox.fromGeoPoints(ports.map { GeoPoint(it.latitude, it.longitude) })
                                mv.zoomToBoundingBox(bbox.increaseByScale(1.4f), true)
                            }
                        }
                    }) { Icon(Icons.Default.CenterFocusStrong, "recenter") }
                    IconButton(onClick = { useOfflineOnly = !useOfflineOnly }) {
                        Icon(if (useOfflineOnly) Icons.Default.WifiOff else Icons.Default.Wifi, if (useOfflineOnly) "offline" else "online")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                BannerAd(modifier = Modifier.fillMaxWidth())
                Column(Modifier.padding(12.dp)) {
                    if (selectedPort != null) {
                    val p = selectedPort!!
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            TText("🏝️ ${p.name}", style = MaterialTheme.typography.titleMedium)
                            if (p.country.isNotEmpty()) TText(p.country, style = MaterialTheme.typography.bodySmall)
                            TText("${formatDate(p.arrivalDate)} → ${formatDate(p.departureDate)}", style = MaterialTheme.typography.bodySmall)
                            TText("${p.latitude}, ${p.longitude}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onWeatherClick(p) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.WbSunny, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); TText("Weather")
                                }
                                OutlinedButton(onClick = {
                                    val uri = Uri.parse("geo:${p.latitude},${p.longitude}?q=${p.latitude},${p.longitude}(${Uri.encode(p.name)})")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }) { Icon(Icons.Default.Directions, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); TText("Navigate") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {
                            if (locationPerm.status.isGranted) {
                                showLocationDot = !showLocationDot
                                mapViewRef?.let { mv ->
                                    if (showLocationDot) {
                                        myLocationOverlay?.enableMyLocation()
                                        myLocationOverlay?.enableFollowLocation()
                                    } else {
                                        myLocationOverlay?.disableMyLocation()
                                        myLocationOverlay?.disableFollowLocation()
                                    }
                                    mv.invalidate()
                                }
                            } else {
                                locationPerm.launchPermissionRequest()
                            }
                        },
                        label = { TText(if (showLocationDot) "My Location ON" else "My Location") },
                        leadingIcon = { Icon(Icons.Default.MyLocation, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            // Download offline tiles for bbox of all ports at z10-15
                            val mv = mapViewRef ?: return@AssistChip
                            val bbox = if (ports.isNotEmpty()) BoundingBox.fromGeoPoints(ports.map { GeoPoint(it.latitude, it.longitude) }).increaseByScale(1.8f)
                            else BoundingBox(85.0, 180.0, -85.0, -180.0)
                            // Use CacheManager to download
                            try {
                                val cm = org.osmdroid.tileprovider.cachemanager.CacheManager(mv)
                                @Suppress("DEPRECATION")
                                cm.downloadAreaAsync(context, bbox, 9, 15)
                            } catch (_: Exception) {}
                        },
                        label = { TText("Download offline") },
                        leadingIcon = { Icon(Icons.Default.Download, null, Modifier.size(16.dp)) }
                    )
                }
                TText("© OpenStreetMap contributors (ODbL) • Tap Download offline before you lose signal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ports.isEmpty()) {
                EmptyState(
                    emoji = "🗺️",
                    title = "No ports yet",
                    subtitle = "Add ports in Port List to see them on the map here."
                )
            }
            // MapView via AndroidView
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        // world center if no ports
                        controller.setZoom(3.5)
                        controller.setCenter(GeoPoint(20.0, 0.0))
                        // location overlay
                        val loc = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                        myLocationOverlay = loc
                        overlays.add(loc)
                        mapViewRef = this
                        // Initial update after layout
                        post {
                            if (ports.isNotEmpty()) {
                                try {
                                    val bbox = BoundingBox.fromGeoPoints(ports.map { GeoPoint(it.latitude, it.longitude) })
                                    zoomToBoundingBox(bbox.increaseByScale(1.5f), false)
                                } catch (_: Exception) {
                                    controller.setCenter(GeoPoint(ports.first().latitude, ports.first().longitude))
                                    controller.setZoom(5.0)
                                }
                            }
                        }
                        updateMap(this, ctx, ports, selectedPort, loc, showLocationDot, useOfflineOnly)
                    }
                },
                update = { mv ->
                    mapViewRef = mv
                    // Ensure tile source respects offline toggle
                    mv.setUseDataConnection(!useOfflineOnly)
                    updateMap(mv, context, ports, selectedPort, myLocationOverlay, showLocationDot, useOfflineOnly)
                    // Recenter after layout if needed
                    if (ports.isNotEmpty() && mv.zoomLevelDouble < 3.0) {
                        mv.post {
                            try {
                                val bbox = BoundingBox.fromGeoPoints(ports.map { GeoPoint(it.latitude, it.longitude) })
                                mv.zoomToBoundingBox(bbox.increaseByScale(1.5f), true)
                            } catch (_: Exception) {}
                        }
                    }
                }
            )
            // Port list quick jump — moved down + visible background + scrollable
            if (ports.isNotEmpty()) {
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .padding(horizontal = 12.dp)
                            .padding(top = 28.dp, bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ports.forEachIndexed { idx, p ->
                                val isSel = selectedPort?.id == p.id
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        selectedPort = if (isSel) null else p
                                        mapViewRef?.let { mv ->
                                            mv.controller.animateTo(GeoPoint(p.latitude, p.longitude))
                                            mv.controller.setZoom(10.0)
                                        }
                                    },
                                    label = { TText("${idx + 1}. ${p.name}") },
                                    leadingIcon = { Icon(Icons.Default.Place, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun updateMap(
    mv: MapView,
    ctx: Context,
    ports: List<PortStop>,
    selected: PortStop?,
    myLoc: MyLocationNewOverlay?,
    showLoc: Boolean,
    offlineOnly: Boolean
) {
    mv.setUseDataConnection(!offlineOnly)
    // Preserve my location overlay, clear others and re-add
    val keep = myLoc
    mv.overlays.removeAll { it !== keep }
    if (keep != null && !mv.overlays.contains(keep)) mv.overlays.add(keep)
    if (showLoc) {
        try { keep?.enableMyLocation() } catch (_: Exception) {}
    }

    if (ports.isEmpty()) {
        mv.invalidate()
        return
    }
    // Polyline itinerary
    if (ports.size > 1) {
        val line = Polyline().apply {
            outlinePaint.strokeWidth = 6f
            outlinePaint.color = android.graphics.Color.parseColor("#00897B")
            setPoints(ports.sortedBy { it.orderIndex }.map { GeoPoint(it.latitude, it.longitude) })
        }
        mv.overlays.add(line)
    }
    // Markers
    ports.sortedBy { it.orderIndex }.forEachIndexed { idx, p ->
        val marker = Marker(mv).apply {
            position = GeoPoint(p.latitude, p.longitude)
            title = p.name
            snippet = "${p.country} • ${formatDate(p.arrivalDate)}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            // Use custom tint by selected
            icon = ctx.getDrawable(android.R.drawable.ic_menu_mylocation)?.apply {
                setTint(if (selected?.id == p.id) android.graphics.Color.parseColor("#FF6F59") else android.graphics.Color.parseColor("#00897B"))
            }
            setOnMarkerClickListener { _, _ ->
                // Toggle selection via caller? For now handle local selection
                // We can't directly update compose state here, but we can show info window
                showInfoWindow()
                true
            }
        }
        mv.overlays.add(marker)
    }
    mv.invalidate()
}