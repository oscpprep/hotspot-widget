package com.altmansoftwaredesign.hotspotwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.widget.HotspotWidgetProvider

/**
 * Receives Android's protected tether-state broadcast. The system sends a
 * sticky broadcast with active interface names whenever tethering changes.
 * The action is protected by Android, so ordinary apps cannot spoof it.
 */
class TetherStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TETHER_STATE_CHANGED) return

        val state = HotspotRepository(context).parseTetherStateIntent(intent)
        Log.d(TAG, "Received tether-state update: $state")
        if (state != null) {
            HotspotWidgetProvider.updateWidgets(
                context,
                hotspotStateOverride = state
            )
        }
    }

    private companion object {
        const val TAG = "TetherStateReceiver"
        const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
    }
}
