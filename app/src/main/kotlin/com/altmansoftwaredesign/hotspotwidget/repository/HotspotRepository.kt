package com.altmansoftwaredesign.hotspotwidget.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.util.Log

/**
 * Read-only Wi-Fi tethering state detection.
 *
 * Android's tether-state broadcast is a sticky system broadcast containing the
 * active tethered interface list. It works without hidden API callbacks and is
 * the primary source on older Android versions and OEM builds.
 */
class HotspotRepository(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isHotspotEnabled(): Boolean? {
        readStateFromStickyBroadcast()?.let { return it }
        return readStateFromLegacyFallback()
    }

    @Suppress("DEPRECATION")
    private fun readStateFromStickyBroadcast(): Boolean? {
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

    private fun readInterfaceList(intent: Intent): List<String>? {
        // TetheringManager's key on current releases, and ConnectivityManager's
        // legacy key on older releases/OEM implementations.
        val keys = listOf(
            EXTRA_ACTIVE_TETHER,
            EXTRA_ACTIVE_TETHER_LEGACY,
            EXTRA_ACTIVE_LOCAL_ONLY
        )

        for (key in keys) {
            val list = intent.getStringArrayListExtra(key)
            if (list != null) return list

            val array = intent.getStringArrayExtra(key)
            if (array != null) return array.toList()
        }
        return null
    }

    fun parseTetherStateIntent(intent: Intent): Boolean? {
        val names = readInterfaceList(intent) ?: return null
        val enabled = names.any(::isWifiTetherInterface)
        Log.d(TAG, "Tether state broadcast interfaces=$names hotspot=$enabled")
        return enabled
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
        const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
        const val EXTRA_ACTIVE_TETHER = "tetherArray"
        const val EXTRA_ACTIVE_TETHER_LEGACY = "activeArray"
        const val EXTRA_ACTIVE_LOCAL_ONLY = "android.net.extra.ACTIVE_LOCAL_ONLY"
    }
}
