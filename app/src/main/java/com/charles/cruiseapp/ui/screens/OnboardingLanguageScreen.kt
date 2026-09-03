package com.charles.cruiseapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.charles.cruiseapp.CruiseApplication
import com.charles.cruiseapp.data.translation.DownloadState
import com.charles.cruiseapp.data.translation.SupportedLanguage
import com.charles.cruiseapp.data.translation.SupportedLanguages
import com.charles.cruiseapp.ui.translation.LanguageViewModel
import com.charles.cruiseapp.ui.translation.TText
import com.charles.cruiseapp.util.formatDate
import com.charles.cruiseapp.util.startOfDay
import kotlinx.coroutines.launch
import java.util.Calendar

enum class OnboardingStep {
    LANGUAGE,
    FEATURE_TOUR,
    CRUISE_SETUP
}

data class FeatureTourItem(
    val emoji: String,
    val icon: ImageVector,
    val badge: String,
    val title: String,
    val subtitle: String,
    val bullets: List<Pair<String, String>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingLanguageScreen(
    onComplete: () -> Unit,
    onSkipToEnglish: () -> Unit = onComplete,
    onCreateCruise: ((String, Long, Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as CruiseApplication
    val translationManager = app.translationManager
    val scope = rememberCoroutineScope()

    val vm = remember { LanguageViewModel(translationManager, context.applicationContext) }

    val selectedCode by vm.selectedCode.collectAsState()
    val isDownloading by vm.isDownloading.collectAsState()
    val downloadState by vm.downloadState.collectAsState()
    val error by vm.error.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val downloadedCodes by vm.downloadedCodes.collectAsState()

    var currentStep by remember { mutableStateOf(OnboardingStep.LANGUAGE) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingLang by remember { mutableStateOf<SupportedLanguage?>(null) }

    val selected = SupportedLanguages.fromCode(selectedCode)
    val isSelectedDownloaded = downloadedCodes.contains(selectedCode) || selectedCode == "en"

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                slideInHorizontally(tween(350)) { it } + fadeIn(tween(350)) togetherWith
                        slideOutHorizontally(tween(350)) { -it } + fadeOut(tween(350))
            } else {
                slideInHorizontally(tween(350)) { -it } + fadeIn(tween(350)) togetherWith
                        slideOutHorizontally(tween(350)) { it } + fadeOut(tween(350))
            }
        },
        label = "onboarding_step_transition"
    ) { step ->
        when (step) {
            OnboardingStep.LANGUAGE -> {
                LanguageSelectionView(
                    vm = vm,
                    selectedCode = selectedCode,
                    selected = selected,
                    isDownloading = isDownloading,
                    downloadState = downloadState,
                    error = error,
                    searchQuery = searchQuery,
                    downloadedCodes = downloadedCodes,
                    isSelectedDownloaded = isSelectedDownloaded,
                    onContinue = {
                        currentStep = OnboardingStep.FEATURE_TOUR
                    },
                    onSkipToEnglish = {
                        scope.launch {
                            vm.downloadAndApplyLanguage("en") {
                                currentStep = OnboardingStep.FEATURE_TOUR
                            }
                        }
                    },
                    onRequestDownloadDialog = { lang ->
                        pendingLang = lang
                        showDownloadDialog = true
                    }
                )
            }
            OnboardingStep.FEATURE_TOUR -> {
                FeatureTourView(
                    onBack = { currentStep = OnboardingStep.LANGUAGE },
                    onFinishTour = { currentStep = OnboardingStep.CRUISE_SETUP },
                    onSkip = { currentStep = OnboardingStep.CRUISE_SETUP }
                )
            }
            OnboardingStep.CRUISE_SETUP -> {
                InitialCruiseSetupView(
                    onBack = { currentStep = OnboardingStep.FEATURE_TOUR },
                    onSaveCruise = { ship, start, end ->
                        vm.completeOnboarding()
                        onCreateCruise?.invoke(ship, start, end)
                        onComplete()
                    },
                    onSkipSetup = {
                        vm.completeOnboarding()
                        onComplete()
                    }
                )
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
                    TText("We'll download the ${lang.displayName} translation model (~30 MB) using Google ML Kit. Once downloaded, everything in the app will automatically appear in your language and work offline at sea.")
                    TText("Keep an active internet connection until it finishes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (error != null) TText(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (isDownloading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.downloadAndApplyLanguage(lang.code) { success ->
                            if (success) {
                                showDownloadDialog = false
                                currentStep = OnboardingStep.FEATURE_TOUR
                            }
                        }
                    },
                    enabled = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp))
                        TText("Downloading…")
                    } else {
                        TText("Download & Continue")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isDownloading) showDownloadDialog = false }, enabled = !isDownloading) {
                    TText("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelectionView(
    vm: LanguageViewModel,
    selectedCode: String,
    selected: SupportedLanguage,
    isDownloading: Boolean,
    downloadState: DownloadState,
    error: String?,
    searchQuery: String,
    downloadedCodes: Set<String>,
    isSelectedDownloaded: Boolean,
    onContinue: () -> Unit,
    onSkipToEnglish: () -> Unit,
    onRequestDownloadDialog: (SupportedLanguage) -> Unit
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TText("Choose Your Language") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (error != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                TText(
                                    error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.dismissError() }) { TText("Dismiss") }
                            }
                        }
                    }
                    if (isDownloading || downloadState == DownloadState.DOWNLOADING) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        TText(
                            "Downloading ${selected.displayName} offline model (~30 MB)… Keep connection active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onSkipToEnglish,
                            modifier = Modifier.weight(1f),
                            enabled = !isDownloading
                        ) {
                            TText("English (Default)")
                        }
                        Button(
                            onClick = {
                                if (isSelectedDownloaded) {
                                    scope.launch {
                                        vm.downloadAndApplyLanguage(selectedCode) {
                                            onContinue()
                                        }
                                    }
                                } else {
                                    onRequestDownloadDialog(selected)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isDownloading
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                TText("Downloading…")
                            } else if (isSelectedDownloaded) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                TText("Continue")
                            } else {
                                Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                TText("Download & Continue")
                            }
                        }
                    }
                    TText(
                        "You can change this anytime in Settings. Free on-device Google ML Kit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(
                Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        TText("Welcome Aboard! 🌊", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    TText(
                        "Select your native language. We'll download a free ML Kit translation model so every single screen in the app appears in your language — completely offline at sea!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TText(
                        "Powered by Google ML Kit — 100% free, private on-device, no account needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

            val filtered = remember(searchQuery, selectedCode, downloadedCodes) { vm.getFilteredLanguages() }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.code }) { lang ->
                    val isSelected = lang.code == selectedCode
                    val isDownloaded = downloadedCodes.contains(lang.code) || lang.code == "en"

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.selectLanguage(lang.code)
                            if (isDownloaded || lang.code == "en") {
                                vm.downloadAndApplyLanguage(lang.code) {}
                            } else {
                                onRequestDownloadDialog(lang)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(lang.flag, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        lang.nativeName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    TText(
                                        "${lang.displayName} • ${lang.code}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (lang.code == "en") {
                                        TText("Built-in • No download needed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    } else if (isDownloaded) {
                                        TText("✓ Downloaded • Ready offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        TText("Tap to select (~30 MB download)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureTourView(
    onBack: () -> Unit,
    onFinishTour: () -> Unit,
    onSkip: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val tourItems = remember {
        listOf(
            FeatureTourItem(
                emoji = "🚢",
                icon = Icons.Default.DirectionsBoat,
                badge = "DAILY PLANNER",
                title = "Cruise Countdown & Daily Itinerary",
                subtitle = "Stay organized from home to port and sea days",
                bullets = listOf(
                    "⏳ Live countdown" to "Shows days remaining and destination weather highlights.",
                    "📅 Sea & Port timeline" to "Organize dinners, shows, and excursion schedules day by day.",
                    "🔔 Timely reminders" to "Automatic alerts for all planned events and daily 9 AM countdown."
                )
            ),
            FeatureTourItem(
                emoji = "🏝️",
                icon = Icons.Default.Place,
                badge = "PORT DESTINATIONS",
                title = "Port Explorer & Shore Attractions",
                subtitle = "Your comprehensive destination companion",
                bullets = listOf(
                    "🗺️ Interactive port maps" to "Offline GPS pinpoints and visual island guides.",
                    "🏛️ Wikipedia & OSM guides" to "Rich attraction descriptions and history for each port.",
                    "☀️ 7-day marine weather" to "Forecasts, winds, and temperatures for smooth excursions."
                )
            ),
            FeatureTourItem(
                emoji = "🛳️",
                icon = Icons.Default.Map,
                badge = "SHIP BLUEPRINTS",
                title = "Interactive Ship Deck Plans",
                subtitle = "Official deck layouts for top cruise lines",
                bullets = listOf(
                    "🚢 Top cruise lines" to "Royal Caribbean, Carnival, Celebrity, Princess, NCL & MSC.",
                    "🧭 Locate venues" to "Easily find staterooms, dining rooms, pools, and theaters.",
                    "📴 100% offline access" to "Deck blueprints stored locally on your device at sea."
                )
            ),
            FeatureTourItem(
                emoji = "💬",
                icon = Icons.Default.Chat,
                badge = "OFFLINE CHAT",
                title = "SeaMesh™ Offline Party Chat",
                subtitle = "Stay in touch without expensive satellite packages",
                bullets = listOf(
                    "📶 Local mesh connection" to "Chat over onboard Wi-Fi or mobile hotspots.",
                    "💸 No cell data needed" to "Direct device-to-device communication with cabin mates.",
                    "👥 Group coordination" to "Meet up, share deck locations, and plan group dinners."
                )
            ),
            FeatureTourItem(
                emoji = "🌐",
                icon = Icons.Default.Language,
                badge = "MULTI-LANGUAGE",
                title = "100% Free Offline Translation",
                subtitle = "Your cruise companion in your native language",
                bullets = listOf(
                    "⚡ Google ML Kit powered" to "Translates all itinerary items, guides, and alerts on-device.",
                    "🏝️ Works in the middle of the ocean" to "No internet connection needed once downloaded.",
                    "⚙️ Switch anytime" to "Change between 33 languages anytime in Settings."
                )
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { tourItems.size })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TText("App Tour (${pagerState.currentPage + 1}/${tourItems.size})") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onSkip) {
                        TText("Skip Tour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pager Indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(tourItems.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .height(8.dp)
                                    .width(if (isSelected) 24.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pagerState.currentPage > 0) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                TText("Previous")
                            }
                        }
                        Button(
                            onClick = {
                                if (pagerState.currentPage < tourItems.size - 1) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                } else {
                                    onFinishTour()
                                }
                            },
                            modifier = Modifier.weight(if (pagerState.currentPage > 0) 1f else 2f)
                        ) {
                            if (pagerState.currentPage < tourItems.size - 1) {
                                TText("Next Feature")
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                            } else {
                                TText("Continue to Cruise Setup")
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { pad ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) { pageIndex ->
            val item = tourItems[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Visual Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(item.emoji, style = MaterialTheme.typography.displayMedium)
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            TText(
                                item.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        TText(
                            item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        TText(
                            item.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                // Detailed Bullets
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TText("Key Capabilities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        item.bullets.forEach { (heading, desc) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    TText(heading, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    TText(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InitialCruiseSetupView(
    onBack: () -> Unit,
    onSaveCruise: (String, Long, Long) -> Unit,
    onSkipSetup: () -> Unit
) {
    var ship by remember { mutableStateOf("") }
    var start by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, 30) // Default 30 days ahead
            }.timeInMillis
        )
    }
    var end by remember { mutableStateOf(start + 6 * 24 * 60 * 60 * 1000L) }
    var showRangePicker by remember { mutableStateOf(false) }

    val durationDays = ((end - start) / (24 * 60 * 60 * 1000) + 1).coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TText("Cruise Setup (Optional)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val finalShip = ship.trim().ifBlank { "My Cruise" }
                            val finalEnd = if (end < start) start else end
                            onSaveCruise(finalShip, startOfDay(start), startOfDay(finalEnd))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DirectionsBoat, null)
                        Spacer(Modifier.width(8.dp))
                        TText(if (ship.isNotBlank()) "Start with $ship ($durationDays days)" else "Start with My Cruise ($durationDays days)")
                    }
                    OutlinedButton(
                        onClick = onSkipSetup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TText("Explore with Clean Empty Itinerary")
                    }
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚢", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        TText(
                            "Ready to Embark!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    TText(
                        "Enter your cruise details below to immediately start planning your itinerary, or skip to start with a fresh clean empty dashboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }

            OutlinedTextField(
                value = ship,
                onValueChange = { ship = it },
                label = { TText("Cruise Ship Name") },
                placeholder = { TText("e.g., Symphony of the Seas, Carnival Jubilee") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.DirectionsBoat, null) },
                shape = RoundedCornerShape(12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TText("Cruise Dates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FilledTonalButton(onClick = { showRangePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            TText("Pick Dates")
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                TText(
                                    "$durationDays days total",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                TText(
                                    "${formatDate(start, "MMM d")} → ${formatDate(end, "MMM d, yyyy")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Icon(
                                Icons.Default.DateRange,
                                null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    TText(
                        "Tip: Day-by-day itinerary will automatically generate after starting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showRangePicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start,
            initialSelectedEndDateMillis = end
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null) start = startOfDay(s)
                    if (e != null) end = startOfDay(e)
                    if (s != null && e != null && e < s) end = s
                    showRangePicker = false
                }) { TText("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { TText("Cancel") } }
        ) {
            DateRangePicker(
                state = state,
                title = { TText("Select cruise dates", modifier = Modifier.padding(16.dp)) },
                headline = {
                    TText(
                        if (state.selectedStartDateMillis != null && state.selectedEndDateMillis != null)
                            "${state.selectedStartDateMillis?.let { formatDate(it, "MMM d") }} → ${state.selectedEndDateMillis?.let { formatDate(it, "MMM d, yyyy") }}"
                        else "Choose start and end",
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )
                }
            )
        }
    }
}