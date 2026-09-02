package com.charles.cruiseapp.data.hotspot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.charles.cruiseapp.util.FirebaseCrashlyticsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

sealed class HotspotState {
    object Idle : HotspotState()
    object Starting : HotspotState()
    data class Running(val ssid: String, val password: String, val hostIp: String, val port: Int) : HotspotState()
    data class Error(val reason: String) : HotspotState()
}

class HotspotController(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _state = MutableStateFlow<HotspotState>(HotspotState.Idle)
    val state: StateFlow<HotspotState> = _state

    // Guest count for Phase 6 UI (updated by ChatWebServer via service)
    private val _guestCount = MutableStateFlow(0)
    val guestCount: StateFlow<Int> = _guestCount
    fun updateGuestCount(count: Int) { _guestCount.value = count }

    fun reportServerBindFailure(reason: String) {
        _state.value = HotspotState.Error(reason)
    }

    fun start(port: Int) {
        if (_state.value is HotspotState.Running || _state.value is HotspotState.Starting) return
        _state.value = HotspotState.Starting
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = res
                    try {
                        val (ssid, password) = getSsidAndPassword(res)
                        // Resolve IP – may be race where AP interface not yet assigned; we try immediate then retry shortly
                        var ip = resolveHostIpAddress() ?: "192.168.49.1"
                        _state.value = HotspotState.Running(ssid, password, ip, port)
                        FirebaseCrashlyticsUtils.log("LocalOnlyHotspot started: ssid len=${ssid.length} ip=$ip port=$port")
                        // Re-resolve after short delays to catch late AP interface bring-up (fixes 10.x vs 192.x race)
                        CoroutineScope(Dispatchers.Default).launch {
                            delay(600)
                            val ip2 = resolveHostIpAddress()
                            if (ip2 != null && ip2 != ip) {
                                FirebaseCrashlyticsUtils.log("Hotspot IP re-resolved after 600ms: $ip2 (was $ip)")
                                _state.value = HotspotState.Running(ssid, password, ip2, port)
                                ip = ip2
                            }
                            delay(1400)
                            val ip3 = resolveHostIpAddress()
                            if (ip3 != null && ip3 != ip) {
                                FirebaseCrashlyticsUtils.log("Hotspot IP re-resolved after 2000ms: $ip3 (was $ip)")
                                _state.value = HotspotState.Running(ssid, password, ip3, port)
                            }
                        }
                    } catch (e: Exception) {
                        FirebaseCrashlyticsUtils.recordException(e)
                        // Still mark running with fallback values if we failed to read config
                        val ip = resolveHostIpAddress() ?: "192.168.49.1"
                        _state.value = HotspotState.Running("CruisePlanner-Hotspot", "", ip, port)
                    }
                }

                override fun onStopped() {
                    _state.value = HotspotState.Idle
                    reservation = null
                    _guestCount.value = 0
                }

                override fun onFailed(reason: Int) {
                    val message = when (reason) {
                        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "No Wi-Fi channel available"
                        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "Could not start hotspot"
                        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Wi-Fi is in an incompatible mode (try turning off Wi-Fi Direct/other hotspot features)"
                        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "Hotspot disabled by device policy"
                        else -> "Hotspot failed (code $reason)"
                    }
                    _state.value = HotspotState.Error(message)
                    FirebaseCrashlyticsUtils.log("LocalOnlyHotspot failed: $message")
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            _state.value = HotspotState.Error("Missing permission to start hotspot")
            FirebaseCrashlyticsUtils.recordException(e)
        } catch (e: Exception) {
            _state.value = HotspotState.Error("Could not start hotspot: ${e.message}")
            FirebaseCrashlyticsUtils.recordException(e)
        }
    }

    fun stop() {
        try {
            reservation?.close()
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
        }
        reservation = null
        _state.value = HotspotState.Idle
        _guestCount.value = 0
    }

    private fun getSsidAndPassword(res: WifiManager.LocalOnlyHotspotReservation): Pair<String, String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ non-deprecated path
            val config = res.softApConfiguration
            val ssid = config?.ssid ?: "Unknown"
            val pass = config?.passphrase ?: ""
            ssid to pass
        } else {
            @Suppress("DEPRECATION")
            val config = res.wifiConfiguration
            val ssid = config?.SSID?.trim('"') ?: "Unknown"
            val pass = config?.preSharedKey?.trim('"') ?: ""
            ssid to pass
        }
    }

    private fun resolveHostIpAddress(): String? {
        // Try NetworkInterface enumeration first (most reliable for AP interface)
        val candidates = mutableListOf<Pair<String, String>>() // (ifaceName, ip)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                val ifaceName = iface.name ?: "unknown"
                val isUp = try { iface.isUp } catch (_: Exception) { true }
                val isLoop = try { iface.isLoopback } catch (_: Exception) { false }
                // Collect all IPv4 non-loopback, even if iface is down briefly (AP may be transitioning)
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        // Filter out link-local 169.254.x.x
                        if (host.startsWith("169.254.")) continue
                        candidates.add(ifaceName to host)
                        FirebaseCrashlyticsUtils.log("Hotspot candidate: $ifaceName isUp=$isUp loop=$isLoop ip=$host")
                    }
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
        }

        // Also try ConnectivityManager LinkProperties for more context (especially for API 23+)
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.allNetworks?.forEach { network ->
                try {
                    val caps = cm.getNetworkCapabilities(network)
                    val props = cm.getLinkProperties(network)
                    if (props != null) {
                        val iface = props.interfaceName ?: "unknown-cm"
                        for (linkAddr in props.linkAddresses) {
                            val addr = linkAddr.address
                            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                                val host = addr.hostAddress ?: continue
                                if (host.startsWith("169.254.")) continue
                                // Avoid duplicates
                                if (candidates.none { it.second == host }) {
                                    val transport = when {
                                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
                                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) == true -> "WIFI_AWARE"
                                        else -> "OTHER"
                                    }
                                    candidates.add(iface to host)
                                    FirebaseCrashlyticsUtils.log("CM candidate: $iface ($transport) ip=$host")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            FirebaseCrashlyticsUtils.recordException(e)
        }

        if (candidates.isEmpty()) return null

        FirebaseCrashlyticsUtils.log("All hotspot IP candidates: ${candidates.joinToString { "${it.first}:${it.second}" }}")

        // Scoring: prefer AP-like interface names with 192.168.x
        val scored = candidates.sortedWith(compareBy(
            // Primary: 192.168.x is most likely hotspot AP
            { if (it.second.startsWith("192.168.")) 0 else 1 },
            // Secondary: interface name hints AP
            {
                val n = it.first.lowercase()
                when {
                    n.contains("ap") -> 0
                    n.contains("softap") -> 0
                    n == "wlan1" -> 0
                    n.contains("p2p") -> 1
                    n == "wlan0" -> 2 // client Wi-Fi, least preferred when hotspot expected
                    else -> 1
                }
            },
            // Tertiary: prefer site-local over other (10.x is siteLocal but less preferred than 192.168)
            { if (it.second.startsWith("10.")) 2 else if (it.second.startsWith("172.")) 1 else 0 }
        ))

        // If the best candidate is wlan0 with 10.x and we have another 192.168 candidate, the sort already prefers 192.168
        // If we ONLY have wlan0 10.x (e.g., no AP found), we still return it but log warning – better than nothing, but fallback to hardcoded 192.168.49.1 is even better for hotspot guests
        val best = scored.firstOrNull()?.second
        // If best is a 10.x from wlan0 and we expected hotspot, but we found no 192.168.x, log and still return best but caller may want hardcoded fallback
        if (best != null && best.startsWith("10.") && candidates.none { it.second.startsWith("192.168.") }) {
            FirebaseCrashlyticsUtils.log("No 192.168 candidate found, falling back to 10.x candidate $best – hotspot guests may need to try 192.168.49.1 manually")
            // Return null to trigger caller's hardcoded fallback 192.168.49.1 which is more likely correct for hotspot than 10.x client IP
            // Only return 10.x if we truly have no other choice and want to show something
            // We return null so caller falls back to 192.168.49.1
            return null
        }
        FirebaseCrashlyticsUtils.log("Chosen hotspot IP: $best from ${scored.firstOrNull()?.first}")
        return best
    }
}
