package com.altmansoftwaredesign.hotspotwidget.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.widget.HotspotWidgetProvider

/**
 * Opens the OEM/system hotspot controls and refreshes the widget when the
 * user returns, so its ON/OFF display reflects the current system state.
 */
class HotspotSettingsActivity : Activity() {

    private var settingsOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            openSystemHotspotSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        if (settingsOpened) {
            // Settings may not return a meaningful activity result. onResume
            // is the reliable signal that the user came back from Samsung Settings.
            HotspotWidgetProvider.updateWidgets(this)
            finish()
        }
    }

    @Suppress("DEPRECATION")
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
        const val ACTION_WIFI_TETHER_SETTINGS = "com.android.settings.WIFI_TETHER_SETTINGS"
        const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
    }
}
