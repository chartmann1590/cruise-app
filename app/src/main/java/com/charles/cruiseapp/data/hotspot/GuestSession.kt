package com.charles.cruiseapp.data.hotspot

import fi.iki.elonen.NanoWSD

/**
 * One entry per live WebSocket connection.
 * guestCode is the browser's localStorage-persisted UUID (from "join" message)
 * NOT the same as the WebSocket connection identity, since a guest can reconnect
 * (new socket) while keeping the same PartyMember identity.
 */
data class GuestSession(val guestCode: String, var displayName: String, val socket: NanoWSD.WebSocket)
