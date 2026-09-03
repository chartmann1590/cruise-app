package com.charles.cruiseapp.ui.screens

import com.charles.cruiseapp.ui.translation.TText
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.charles.cruiseapp.ads.BannerAd
import com.charles.cruiseapp.ui.theme.CruiseGradients

private val HERO_HEIGHT = 300.dp

@Composable
fun PlaceDetailScreen(
    weatherVm: WeatherViewModel,
    onBack: () -> Unit,
    onAddEvent: (String, Long, Int, Int, String, String, Int, String) -> Unit,
){
    val place by weatherVm.selectedPlace.collectAsState()
    val port by weatherVm.activePort.collectAsState()
    val fullExtract by weatherVm.placeFullExtract.collectAsState()
    val extractLoading by weatherVm.placeExtractLoading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(place?.title) {
        place?.let { weatherVm.loadFullExtract(it.title) }
    }

    Scaffold(bottomBar = { BannerAd(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) }) { pad ->
        if (place == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { TText("Not found") }
            return@Scaffold
        }
        val p = place!!

        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            // Hero image with scrim, eyebrow label, and title overlaid directly on it.
            Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
                if (p.imageUrl != null) {
                    AsyncImage(
                        model = p.imageUrl,
                        contentDescription = p.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Box(
                        Modifier.matchParentSize().background(Brush.linearGradient(CruiseGradients.oceanSunset)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.85f))
                    }
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.5f to Color.Transparent,
                            0.85f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.75f),
                        )
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .align(Alignment.TopStart),
                ) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }

                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp)) {
                    if (port != null) {
                        TText("🏝️ NEAR ${port!!.name.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        p.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            // Content sheet overlapping the hero image for a layered look.
            Surface(
                modifier = Modifier.fillMaxWidth().offset(y = (-24).dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (p.address != null || p.phone != null || p.website != null) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (p.address != null) InfoChip(Icons.Default.LocationOn, p.address)
                            if (p.phone != null) InfoChip(
                                Icons.Default.Phone, p.phone, accent = true,
                                onClick = { try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}"))) } catch (_: Exception) {} },
                            )
                            if (p.website != null) InfoChip(Icons.Default.Language, p.website)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TText("ABOUT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            fullExtract ?: p.extract,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                        )
                        if (extractLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                TText("Loading more...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val activePort = port
                            if (activePort != null) {
                                onAddEvent(p.title, activePort.arrivalDate, 10, 0, p.address ?: activePort.name, "Excursion", 30, fullExtract ?: p.extract)
                                Toast.makeText(context, "Added to itinerary", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = port != null,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        TText("Add to Itinerary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, accent: Boolean = false, onClick: (() -> Unit)? = null){
    val container = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .widthIn(max = 220.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, textAlign = TextAlign.Start)
    }
}