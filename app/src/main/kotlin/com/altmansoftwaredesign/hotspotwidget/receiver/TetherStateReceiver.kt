package com.altmansoftwaredesign.hotspotwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.widget.HotspotWidgetProvider

/**
 * Receives Android Wi-Fi AP state transitions and tethering changes.
 * The Wi-Fi AP state is the authoritative primary signal; tether interface
 * broadcasts remain as a fallback for device-specific implementations.
 */
class TetherStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = HotspotRepository(context)
        val state = when (intent.action) {
            ACTION_WIFI_AP_STATE_CHANGED -> repository.parseWifiApStateIntent(intent)
            ACTION_TETHER_STATE_CHANGED -> repository.parseTetherStateIntent(intent)
            else -> null
        }

        Log.d(TAG, "Received ${intent.action} state=$state")
        if (state != null) {
            HotspotWidgetProvider.updateWidgets(
                context,
                hotspotStateOverride = state
            )
        }
    }

    private companion object {
        const val TAG = "TetherStateReceiver"
        const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
    }
}
