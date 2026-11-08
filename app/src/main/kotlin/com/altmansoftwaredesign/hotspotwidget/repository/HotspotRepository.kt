package com.altmansoftwaredesign.hotspotwidget.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.TetheringManager
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reads the real Wi-Fi tethering state through Android's public tethering
 * event callback. A legacy hidden-API query is retained only as a fallback
 * for OEM builds where the callback is unavailable or does not respond.
 */
class HotspotRepository(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isHotspotEnabled(): Boolean? {
        val publicApiState = readStateFromTetheringCallback()
        if (publicApiState != null) return publicApiState

        return readStateFromLegacyFallback()
    }

    private fun readStateFromTetheringCallback(): Boolean? {
        val manager = appContext.getSystemService(TetheringManager::class.java)
            ?: return null
        val latch = CountDownLatch(1)
        var state: Boolean? = null

        val callback = object : TetheringManager.TetheringEventCallback() {
            override fun onTetheredInterfacesChanged(interfaces: List<String>) {
                state = interfaces.any(::isWifiTetherInterface)
                latch.countDown()
            }
        }

        return try {
            manager.registerTetheringEventCallback({ command -> command.run() }, callback)
            if (!latch.await(1500, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for Android tethering state callback")
                null
            } else {
                state
            }
        } catch (e: Exception) {
            Log.w(TAG, "Public tethering state callback unavailable", e)
            null
        } finally {
            try {
                manager.unregisterTetheringEventCallback(callback)
            } catch (e: Exception) {
                Log.d(TAG, "Tethering callback was not registered or could not be removed", e)
            }
        }
    }

    private fun readStateFromLegacyFallback(): Boolean? {
        return try {
            @Suppress("DiscouragedPrivateApi", "SoonBlockedPrivateApi")
            val method = connectivityManager.javaClass.getDeclaredMethod("getTetheredIfaces")
            method.isAccessible = true
            val tethered = method.invoke(connectivityManager) as? Array<*>
                ?: return null
            tethered.filterIsInstance<String>().any(::isWifiTetherInterface)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read active tethered interfaces", e)
            null
        }
    }

    private fun isWifiTetherInterface(name: String): Boolean {
        val iface = name.lowercase()
        return iface.startsWith("wlan") ||
            iface.startsWith("ap") ||
            iface.startsWith("wifi")
    }

    private companion object {
        const val TAG = "HotspotWidget"
    }
}
