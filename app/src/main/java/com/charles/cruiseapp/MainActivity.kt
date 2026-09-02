package com.charles.cruiseapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.charles.cruiseapp.ads.GlobalInterstitial
import com.charles.cruiseapp.ads.AdConfig
import com.charles.cruiseapp.ui.navigation.Screen
import com.charles.cruiseapp.ui.screens.*
import com.charles.cruiseapp.ui.theme.CruiseTheme
import kotlinx.coroutines.launch
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import com.charles.cruiseapp.util.FirebasePerfUtils
import com.google.firebase.perf.FirebasePerformance
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class MainActivity : ComponentActivity() {
    private var screenTrace: com.google.firebase.perf.metrics.Trace? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Screen load performance trace
        try {
            screenTrace = FirebasePerformance.getInstance().newTrace("main_activity_onCreate")
            screenTrace?.start()
            FirebaseCrashlyticsUtils.log("MainActivity onCreate")
        } catch (e: Exception) { Log.w("MainActivity", "perf trace failed", e) }

        setContent {
            CruiseTheme {
                Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background){
                    AppNav()
                }
            }
        }
        requestAdConsent()
        try {
            screenTrace?.putMetric("success", 1)
            screenTrace?.stop()
        } catch (_: Exception) {}
    }

    private fun requestAdConsent() {
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        val params = ConsentRequestParameters.Builder().build()

        fun startAdsWhenAllowed() {
            val allowed = consentInformation.canRequestAds()
            AdConfig.updateConsent(allowed)
            if (allowed) {
                (application as? CruiseApplication)?.initializeAdsAfterConsent()
            }
        }

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                    startAdsWhenAllowed()
                }
            },
            {
                // A previous valid decision may still allow requests when refresh fails.
                startAdsWhenAllowed()
            }
        )

        // Avoid delaying ads when a valid choice from the prior session is available.
        startAdsWhenAllowed()
    }

    override fun onResume() {
        super.onResume()
        try {
            FirebaseCrashlyticsUtils.log("MainActivity onResume")
            val trace = FirebasePerformance.getInstance().newTrace("screen_main")
            trace.start()
            trace.putAttribute("screen", "Main")
            trace.stop()
        } catch (_: Exception) {}
    }
}

@Composable
fun AppNav(){
    val navController = rememberNavController()
    val dashboardVm: DashboardViewModel = viewModel()
    val weatherVm: WeatherViewModel = viewModel()
    val partyVm: PartyViewModel = viewModel()
    val cruise by dashboardVm.cruise.collectAsState()

    // ── AdMob interstitial wiring ──
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val activity = ctx as? android.app.Activity
    val scope = rememberCoroutineScope()
    val interstitial = remember { GlobalInterstitial.manager }
    LaunchedEffect(Unit) { try { interstitial.preload(ctx) } catch (_: Exception) {} }
    fun onNavActionMaybeShowAd() {
        try {
            interstitial.onUserAction()
            if (!interstitial.isReady && !interstitial.isCurrentlyLoading) interstitial.preload(ctx)
            // Delay slightly so navigation transition starts, then show if eligible (cooldown + min-actions respected)
            scope.launch {
                kotlinx.coroutines.delay(350)
                val act = activity ?: return@launch
                if (act.isFinishing || act.isDestroyed) return@launch
                if (interstitial.canShow()) {
                    interstitial.show(act)
                }
            }
        } catch (_: Exception) {}
    }

    // Dashboard countdown weather enrichment (respects unit setting)
    var countdownExtra by remember { mutableStateOf<String?>(null) }
    // Observe unit changes to recompute countdownExtra
    val appCtx = navController.context.applicationContext
    var isMetric by remember { mutableStateOf(com.charles.cruiseapp.util.UnitUtils.isMetric(appCtx)) }
    LaunchedEffect(Unit) {
        com.charles.cruiseapp.util.UnitUtils.observeIsMetric(appCtx).collect { isMetric = it }
    }
    LaunchedEffect(cruise?.id, isMetric) {
        val c = cruise; if (c != null && c.startDate > com.charles.cruiseapp.util.startOfDay(System.currentTimeMillis())) {
            // enrich countdown with weather if port exists
            try {
                val app = navController.context.applicationContext as? CruiseApplication
                val db = app?.database
                if (db != null) {
                    val portsOnce = db.portStopDao().getForCruiseOnce(c.id)
                    val first = portsOnce.minByOrNull { it.arrivalDate }
                    if (first != null) {
                        val cache = db.weatherCacheDao().getForPort(first.id)
                        if (cache?.tempMax != null) {
                            val minStr = com.charles.cruiseapp.util.UnitUtils.formatTemp(cache.tempMin, isMetric)
                            val maxStr = com.charles.cruiseapp.util.UnitUtils.formatTemp(cache.tempMax, isMetric)
                            countdownExtra = "$minStr–$maxStr at ${first.name}"
                        } else {
                            val repo = app.weatherRepository
                            val res = repo.getForecast(first.latitude, first.longitude, 7)
                            val f = res.getOrNull()
                            if (f?.current?.temperature2m != null) {
                                val tStr = com.charles.cruiseapp.util.UnitUtils.formatTemp(f.current.temperature2m, isMetric)
                                countdownExtra = "$tStr now at ${first.name}"
                            } else countdownExtra = null
                        }
                    } else countdownExtra = null
                }
            } catch (_: Exception) {}
        } else countdownExtra = null
    }

    NavHost(navController, startDestination = Screen.Dashboard.route){
        composable(Screen.Dashboard.route){
            DashboardScreen(
                cruise=cruise,
                portsFlow=dashboardVm.ports,
                eventsFlow=dashboardVm.events,
                upcomingFlow=dashboardVm.upcoming,
                onNavigateToSetup={ onNavActionMaybeShowAd(); navController.navigate(Screen.CruiseSetup.route)},
                onNavigateToDay={ date -> onNavActionMaybeShowAd(); navController.navigate(Screen.DayDetail.create(date))},
                onNavigateToPorts={ onNavActionMaybeShowAd(); navController.navigate(Screen.PortList.route)},
                onNavigateToParty={ onNavActionMaybeShowAd(); navController.navigate(Screen.Party.route)},
                onDeleteEvent={ dashboardVm.deleteEvent(it)},
                onAddEvent={ title,date,h,m,loc,cat,rem,desc -> dashboardVm.addEvent(title,date,h,m,loc,cat,rem,desc)},
                generateDays={ dashboardVm.generateDays() },
                onNavigateToWeather={ port -> onNavActionMaybeShowAd(); navController.navigate(Screen.Weather.create(port.id))},
                onNavigateToPortMap={ onNavActionMaybeShowAd(); navController.navigate(Screen.PortMap.route)},
                onNavigateToShipMaps={ onNavActionMaybeShowAd(); navController.navigate(Screen.ShipCatalog.route)},
                onNavigateToSettings={ onNavActionMaybeShowAd(); navController.navigate(Screen.Settings.route)},
                countdownExtra=countdownExtra
            )
        }
        composable(Screen.CruiseSetup.route){
            CruiseSetupScreen(
                onSave={ name,start,end ->
                    dashboardVm.createCruise(name,start,end)
                    onNavActionMaybeShowAd()
                    navController.popBackStack()
                },
                onBack={ navController.popBackStack() }
            )
        }
        composable(Screen.DayDetail.route, arguments=listOf(navArgument("dateMillis"){ type=NavType.LongType })){
            val date = it.arguments?.getLong("dateMillis") ?: 0L
            DayDetailScreen(
                dateMillis=date,
                eventsFlow=dashboardVm.eventsForDate(date),
                onAddEvent={ title,h,m,loc,cat,rem,desc -> dashboardVm.addEvent(title,date,h,m,loc,cat,rem,desc)},
                onDeleteEvent={ dashboardVm.deleteEvent(it)},
                onBack={ navController.popBackStack()}
            )
        }
        composable(Screen.PortList.route){
            PortListScreen(
                cruise=cruise,
                portsFlow=dashboardVm.ports,
                onAddPort={ name,lat,lon,arr,dep,country -> dashboardVm.addPort(name,lat,lon,arr,dep,country)},
                onDeletePort={ dashboardVm.deletePort(it)},
                onWeatherClick={ p -> onNavActionMaybeShowAd(); navController.navigate(Screen.Weather.create(p.id))},
                onBack={ navController.popBackStack()},
                weatherVm=weatherVm
            )
        }
        composable(Screen.Weather.route, arguments=listOf(navArgument("portId"){type=NavType.LongType})){
            val portId = it.arguments?.getLong("portId") ?: 0L
            WeatherDetailScreen(
                portId=portId, portsFlow=dashboardVm.ports, weatherVm=weatherVm,
                onBack={ navController.popBackStack() },
                onOpenPlace={ onNavActionMaybeShowAd(); navController.navigate(Screen.PlaceDetail.route) },
                onAddEvent={ title,date,h,m,loc,cat,rem,desc -> dashboardVm.addEvent(title,date,h,m,loc,cat,rem,desc) },
            )
        }
        composable(Screen.PlaceDetail.route){
            PlaceDetailScreen(
                weatherVm=weatherVm,
                onBack={ navController.popBackStack() },
                onAddEvent={ title,date,h,m,loc,cat,rem,desc -> dashboardVm.addEvent(title,date,h,m,loc,cat,rem,desc) },
            )
        }
        composable(Screen.Party.route){
            PartyScreen(
                partyVm=partyVm,
                onBack={ navController.popBackStack()},
                onNavigateToHome={
                    onNavActionMaybeShowAd()
                    navController.navigate(Screen.Dashboard.route){
                        popUpTo(Screen.Dashboard.route){ inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateToPorts={
                    onNavActionMaybeShowAd()
                    navController.navigate(Screen.PortList.route){
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.PortMap.route){
            PortMapScreen(
                portsFlow=dashboardVm.ports,
                onBack={ navController.popBackStack()},
                onWeatherClick={ p -> onNavActionMaybeShowAd(); navController.navigate(Screen.Weather.create(p.id))}
            )
        }
        composable(Screen.ShipCatalog.route){
            ShipCatalogScreen(
                cruiseShipName=cruise?.shipName,
                onBack={ navController.popBackStack()},
                onOpenDeck={ shipId -> onNavActionMaybeShowAd(); navController.navigate(Screen.ShipDeck.create(shipId))}
            )
        }
        composable(Screen.ShipDeck.route, arguments = listOf(navArgument("shipId"){ type=NavType.StringType})){
            val shipId = it.arguments?.getString("shipId") ?: ""
            ShipDeckScreen(shipId=shipId, onBack={ navController.popBackStack()})
        }
        composable(Screen.Settings.route){
            SettingsScreen(
                onBack={ navController.popBackStack()},
                onNavigateToCruiseSetup={ onNavActionMaybeShowAd(); navController.navigate(Screen.CruiseSetup.route)}
            )
        }
    }
}
