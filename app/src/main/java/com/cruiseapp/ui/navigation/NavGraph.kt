package com.cruiseapp.ui.navigation

sealed class Screen(val route: String){
    object Dashboard: Screen("dashboard")
    object CruiseSetup: Screen("cruise_setup")
    object DayDetail: Screen("day_detail/{dateMillis}"){ fun create(date: Long) = "day_detail/$date" }
    object PortList: Screen("ports")
    object Weather: Screen("weather/{portId}"){ fun create(id: Long) = "weather/$id" }
    object Party: Screen("party")
    object Chat: Screen("chat")
}
