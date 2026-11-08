package com.altmansoftwaredesign.hotspotwidget.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.widget.HotspotWidgetProvider

/**
 * Opens Samsung/Android hotspot controls. While Settings is in front, this
 * Activity remains registered for Wi-Fi AP state changes so the widget can be
 * redrawn as soon as Samsung reports ON or OFF.
 */
class HotspotSettingsActivity : Activity() {

    private var settingsOpened = false
    private var receiverRegistered = false

    private val wifiApReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_WIFI_AP_STATE_CHANGED) return
            val state = HotspotRepository(this@HotspotSettingsActivity)
                .parseWifiApStateIntent(intent)
            if (state != null) {
                HotspotWidgetProvider.updateWidgets(
                    this@HotspotSettingsActivity,
                    hotspotStateOverride = state
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerWifiApReceiver()
        if (savedInstanceState == null) openSystemHotspotSettings()
    }

    override fun onResume() {
        super.onResume()
        if (settingsOpened) {
            // Refresh twice after returning: once immediately and once after the
            // Settings UI has completed its last Soft AP transition/broadcast.
            HotspotWidgetProvider.updateWidgets(this)
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    HotspotWidgetProvider.updateWidgets(this)
                    finish()
                }
            }, RETURN_REFRESH_DELAY_MS)
        }
    }

    override fun onDestroy() {
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
        const val RETURN_REFRESH_DELAY_MS = 900L
        const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val ACTION_WIFI_TETHER_SETTINGS = "com.android.settings.WIFI_TETHER_SETTINGS"
        const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
    }
}
