package com.charles.cruiseapp.ui.navigation

sealed class Screen(val route: String){
    object Dashboard: Screen("dashboard")
    object CruiseSetup: Screen("cruise_setup")
    object DayDetail: Screen("day_detail/{dateMillis}"){ fun create(date: Long) = "day_detail/$date" }
    object PortList: Screen("ports")
    object Weather: Screen("weather/{portId}"){ fun create(id: Long) = "weather/$id" }
    object PlaceDetail: Screen("place_detail")
    object Party: Screen("party")
    object Chat: Screen("chat")
    object PortMap: Screen("port_map")
    object ShipCatalog: Screen("ship_catalog")
    object ShipDeck: Screen("ship_deck/{shipId}"){ fun create(id: String) = "ship_deck/$id" }
    object Settings: Screen("settings")
}
