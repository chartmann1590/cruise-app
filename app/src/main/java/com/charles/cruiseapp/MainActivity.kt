package com.charles.cruiseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.charles.cruiseapp.ui.navigation.Screen
import com.charles.cruiseapp.ui.screens.*
import com.charles.cruiseapp.ui.theme.CruiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CruiseTheme {
                Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background){
                    AppNav()
                }
            }
        }
    }
}

@Composable
fun AppNav(){
    val navController = rememberNavController()
    val dashboardVm: DashboardViewModel = viewModel()
    val weatherVm: WeatherViewModel = viewModel()
    val partyVm: PartyViewModel = viewModel()
    val cruise by dashboardVm.cruise.collectAsState()

    NavHost(navController, startDestination = Screen.Dashboard.route){
        composable(Screen.Dashboard.route){
            DashboardScreen(
                cruise=cruise,
                portsFlow=dashboardVm.ports,
                eventsFlow=dashboardVm.events,
                upcomingFlow=dashboardVm.upcoming,
                onNavigateToSetup={ navController.navigate(Screen.CruiseSetup.route)},
                onNavigateToDay={ date -> navController.navigate(Screen.DayDetail.create(date))},
                onNavigateToPorts={ navController.navigate(Screen.PortList.route)},
                onNavigateToParty={ navController.navigate(Screen.Party.route)},
                onDeleteEvent={ dashboardVm.deleteEvent(it)},
                onAddEvent={ title,date,h,m,loc,cat,rem,desc -> dashboardVm.addEvent(title,date,h,m,loc,cat,rem,desc)},
                generateDays={ dashboardVm.generateDays() },
                onNavigateToWeather={ port -> navController.navigate(Screen.Weather.create(port.id))}
            )
        }
        composable(Screen.CruiseSetup.route){
            CruiseSetupScreen(
                onSave={ name,start,end ->
                    dashboardVm.createCruise(name,start,end)
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
                onWeatherClick={ p -> navController.navigate(Screen.Weather.create(p.id))},
                onBack={ navController.popBackStack()},
                weatherVm=weatherVm
            )
        }
        composable(Screen.Weather.route, arguments=listOf(navArgument("portId"){type=NavType.LongType})){
            val portId = it.arguments?.getLong("portId") ?: 0L
            WeatherDetailScreen(portId=portId, portsFlow=dashboardVm.ports, weatherVm=weatherVm, onBack={ navController.popBackStack() })
        }
        composable(Screen.Party.route){
            PartyScreen(partyVm=partyVm, onBack={ navController.popBackStack()})
        }
    }
}
