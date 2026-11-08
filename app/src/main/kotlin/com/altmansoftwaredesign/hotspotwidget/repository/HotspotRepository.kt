package com.altmansoftwaredesign.hotspotwidget.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Read-only hotspot state detection.
 *
 * Primary source: WifiManager#getWifiApState, which returns the current AP
 * state synchronously. This avoids relying on a possibly stale sticky broadcast.
 *
 * Fallbacks: the Wi-Fi AP state broadcast, tether-state broadcast, and active
 * tethered-interface list.
 */
class HotspotRepository(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun isHotspotEnabled(): Boolean? {
        // Query the current state first. Sticky broadcasts can contain an old
        // value on some OEM builds and must never override the live system state.
        readStateFromWifiManager()?.let { return it }
        readStateFromStickyWifiApBroadcast()?.let { return it }
        readStateFromStickyTetherBroadcast()?.let { return it }
        return readStateFromLegacyFallback()
    }

    fun parseWifiApStateIntent(intent: Intent): Boolean? {
        val state = intent.getIntExtra(EXTRA_WIFI_AP_STATE, STATE_UNKNOWN)
        val enabled = stateToBoolean(state)
        Log.d(TAG, "Wi-Fi AP state broadcast state=$state enabled=$enabled")
        return enabled
    }

    fun parseTetherStateIntent(intent: Intent): Boolean? {
        val names = readInterfaceList(intent) ?: return null
        val enabled = names.any(::isWifiTetherInterface)
        Log.d(TAG, "Tether state broadcast interfaces=$names hotspot=$enabled")
        return enabled
    }

    @Suppress("DEPRECATION")
    private fun readStateFromStickyWifiApBroadcast(): Boolean? {
        return try {
            val intent = appContext.registerReceiver(
                null,
                IntentFilter(ACTION_WIFI_AP_STATE_CHANGED)
            ) ?: return null
            parseWifiApStateIntent(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read sticky Wi-Fi AP state broadcast", e)
            null
        }
    }

    @Suppress("DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    private fun readStateFromWifiManager(): Boolean? {
        return try {
            val method = wifiManager.javaClass.getMethod("getWifiApState")
            val state = (method.invoke(wifiManager) as? Number)?.toInt()
                ?: return null
            val enabled = stateToBoolean(state)
            Log.d(TAG, "WifiManager#getWifiApState returned $state => $enabled")
            enabled
        } catch (e: Exception) {
            Log.d(TAG, "WifiManager#getWifiApState unavailable on this build", e)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun readStateFromStickyTetherBroadcast(): Boolean? {
        return try {
            val intent = appContext.registerReceiver(
                null,
                IntentFilter(ACTION_TETHER_STATE_CHANGED)
            ) ?: return null
            val activeInterfaces = readInterfaceList(intent) ?: return null
            val enabled = activeInterfaces.any(::isWifiTetherInterface)
            Log.d(TAG, "Sticky tether broadcast interfaces=$activeInterfaces hotspot=$enabled")
            enabled
        } catch (e: Exception) {
            Log.w(TAG, "Could not read sticky tether-state broadcast", e)
            null
        }
    }

    private fun readStateFromLegacyFallback(): Boolean? {
        return try {
            @Suppress("DiscouragedPrivateApi", "SoonBlockedPrivateApi")
            val method = connectivityManager.javaClass.getDeclaredMethod("getTetheredIfaces")
            method.isAccessible = true
            val tethered = method.invoke(connectivityManager) as? Array<*>
                ?: return null
            val names = tethered.filterIsInstance<String>()
            val enabled = names.any(::isWifiTetherInterface)
            Log.d(TAG, "Legacy tethered interfaces=$names hotspot=$enabled")
            enabled
        } catch (e: Exception) {
            Log.w(TAG, "Could not read tethered interfaces", e)
            null
        }
    }

    private fun stateToBoolean(state: Int): Boolean? = when (state) {
        WIFI_AP_STATE_ENABLED -> true
        WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_FAILED -> false
        // Do not momentarily flip the UI while the access point is transitioning.
        WIFI_AP_STATE_ENABLING, STATE_UNKNOWN -> null
        else -> null
    }

    private fun readInterfaceList(intent: Intent): List<String>? {
        val keys = listOf(
            EXTRA_ACTIVE_TETHER,
            EXTRA_ACTIVE_TETHER_LEGACY,
            EXTRA_ACTIVE_LOCAL_ONLY
        )
        for (key in keys) {
            intent.getStringArrayExtra(key)?.let { return it.toList() }
            intent.getStringArrayListExtra(key)?.let { return it }
        }
        return null
    }

    private fun isWifiTetherInterface(name: String): Boolean {
        val iface = name.lowercase()
        return iface.startsWith("wlan") ||
            iface.startsWith("swlan") ||
            iface.startsWith("ap") ||
            iface.startsWith("softap") ||
            iface.startsWith("wifi")
    }

    private companion object {
        const val TAG = "HotspotWidget"
        const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val EXTRA_WIFI_AP_STATE = "wifi_state"
        const val WIFI_AP_STATE_DISABLING = 10
        const val WIFI_AP_STATE_DISABLED = 11
        const val WIFI_AP_STATE_ENABLING = 12
        const val WIFI_AP_STATE_ENABLED = 13
        const val WIFI_AP_STATE_FAILED = 14
        const val STATE_UNKNOWN = -1
        const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
        const val EXTRA_ACTIVE_TETHER = "tetherArray"
        const val EXTRA_ACTIVE_TETHER_LEGACY = "activeArray"
        const val EXTRA_ACTIVE_LOCAL_ONLY = "android.net.extra.ACTIVE_LOCAL_ONLY"
    }
}
