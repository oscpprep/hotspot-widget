package com.altmansoftwaredesign.hotspotwidget.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.repository.Api36TetheringObserver
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.widget.HotspotWidgetProvider

/**
 * Opens Samsung/Android hotspot controls. On Android 16 it keeps the public
 * TetheringManager listener active while Settings is in front. Earlier Android
 * versions retain the Wi-Fi AP broadcast listener as a best-effort fallback.
 */
class HotspotSettingsActivity : Activity() {

    private var settingsOpened = false
    private var receiverRegistered = false
    private var unregisterTetheringCallback: (() -> Unit)? = null
    @Volatile private var lastObservedApState: Boolean? = null

    private val wifiApReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_WIFI_AP_STATE_CHANGED) return
            val state = HotspotRepository(this@HotspotSettingsActivity)
                .parseWifiApStateIntent(intent)
            if (state != null) {
                lastObservedApState = state
                HotspotWidgetProvider.updateWidgets(
                    this@HotspotSettingsActivity,
                    hotspotStateOverride = state
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 36) {
            registerPublicTetheringCallback()
        } else {
            registerWifiApReceiver()
        }
        if (savedInstanceState == null) openSystemHotspotSettings()
    }

    override fun onResume() {
        super.onResume()
        if (settingsOpened) {
            refreshFromBestAvailableState()
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    refreshFromBestAvailableState()
                    finish()
                }
            }, RETURN_REFRESH_DELAY_MS)
        }
    }

    private fun refreshFromBestAvailableState() {
        val observed = lastObservedApState
        if (observed != null) {
            HotspotWidgetProvider.updateWidgets(this, hotspotStateOverride = observed)
        } else {
            HotspotWidgetProvider.updateWidgets(this)
        }
    }

    private fun registerPublicTetheringCallback() {
        try {
            unregisterTetheringCallback = Api36TetheringObserver.register(this) { state ->
                lastObservedApState = state
                Log.i(TAG, "API 36 public tethering callback: wifi=$state")
                HotspotWidgetProvider.updateWidgets(
                    this@HotspotSettingsActivity,
                    hotspotStateOverride = state
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register public API 36 tethering callback", e)
        }
    }

    override fun onDestroy() {
        unregisterTetheringCallback?.invoke()
        unregisterTetheringCallback = null
        if (receiverRegistered) {
            try {
                unregisterReceiver(wifiApReceiver)
            } catch (e: Exception) {
                Log.d(TAG, "Wi-Fi AP receiver already unregistered", e)
            }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun registerWifiApReceiver() {
        if (receiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                this,
                wifiApReceiver,
                IntentFilter(ACTION_WIFI_AP_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register Wi-Fi AP state receiver", e)
        }
    }

    private fun openSystemHotspotSettings() {
        val candidates = listOf(
            Intent(ACTION_WIFI_TETHER_SETTINGS),
            Intent(ACTION_TETHER_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in candidates) {
            try {
                settingsOpened = true
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                settingsOpened = false
                Log.i(TAG, "Settings action not available: ${intent.action}")
            } catch (e: SecurityException) {
                settingsOpened = false
                Log.w(TAG, "Settings action blocked: ${intent.action}", e)
            }
        }

        Toast.makeText(this, R.string.open_settings_manually, Toast.LENGTH_LONG).show()
        finish()
    }

    private companion object {
        const val TAG = "HotspotSettings"
        const val RETURN_REFRESH_DELAY_MS = 1000L
        const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val ACTION_WIFI_TETHER_SETTINGS = "com.android.settings.WIFI_TETHER_SETTINGS"
        const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
    }
}
