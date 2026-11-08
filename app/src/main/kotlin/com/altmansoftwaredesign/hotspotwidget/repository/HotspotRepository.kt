package com.altmansoftwaredesign.hotspotwidget.repository

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log

/**
 * Reads whether Wi-Fi tethering is currently active.
 *
 * The old app also tried to start/stop tethering through a hidden API. That code
 * has been removed: normal apps are not guaranteed permission to control tethering,
 * even when WRITE_SETTINGS is granted. Changing hotspot state is now left to the
 * operating system's own Settings screen.
 *
 * Reading the state still uses the same hidden interface-list query used by the
 * widget's earlier versions. If an OEM blocks it, null is returned rather than
 * claiming that the hotspot is off.
 */
class HotspotRepository(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isHotspotEnabled(): Boolean? {
        return try {
            @Suppress("DiscouragedPrivateApi", "SoonBlockedPrivateApi")
            val method = connectivityManager.javaClass.getDeclaredMethod("getTetheredIfaces")
            method.isAccessible = true
            val tethered = method.invoke(connectivityManager) as? Array<*>
                ?: return null
            tethered.filterIsInstance<String>().any {
                it.startsWith("wlan", ignoreCase = true) ||
                    it.startsWith("ap", ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read active tethered interfaces", e)
            null
        }
    }

    private companion object {
        const val TAG = "HotspotWidget"
    }
}
