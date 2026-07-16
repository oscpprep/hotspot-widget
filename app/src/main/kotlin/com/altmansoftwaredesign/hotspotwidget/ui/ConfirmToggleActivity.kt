package com.altmansoftwaredesign.hotspotwidget.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.repository.HotspotRepository
import com.altmansoftwaredesign.hotspotwidget.service.BatteryMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfirmToggleActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A widget tap is an allowed context to start a foreground service, so
        // ensure the live battery/hotspot monitor is running from here.
        BatteryMonitorService.start(this)

        // Determine current state to phrase the dialog correctly.
        scope.launch {
            val isEnabled = withContext(Dispatchers.IO) {
                HotspotRepository(applicationContext).getHotspotState().isEnabled
            }
            if (isEnabled == null) showUnavailableDialog() else showDialog(isEnabled)
        }
    }

    override fun onDestroy() {
        // Cancel first so a slow state read can't call showDialog on a dead
        // window, then drop the dialog so it isn't leaked on rotation.
        scope.cancel()
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    /**
     * We couldn't read the hotspot state, so we can't say whether "confirm" would
     * turn it on or off. Offering the toggle anyway risks doing the opposite of
     * what the user intends, so the honest move is to say so and do nothing.
     */
    private fun showUnavailableDialog() {
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.state_unavailable_title)
            .setMessage(R.string.state_unavailable_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showDialog(isCurrentlyEnabled: Boolean) {
        val messageRes = if (isCurrentlyEnabled) {
            R.string.confirm_turn_off
        } else {
            R.string.confirm_turn_on
        }
        val confirmLabel = if (isCurrentlyEnabled) {
            getString(R.string.turn_off)
        } else {
            getString(R.string.turn_on)
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.toggle_hotspot)
            .setMessage(messageRes)
            .setPositiveButton(confirmLabel) { _, _ ->
                if (!Settings.System.canWrite(this)) {
                    // Toggling tethering needs WRITE_SETTINGS; send the user to grant
                    // it once, then they tap the widget again to actually toggle.
                    Toast.makeText(
                        this,
                        R.string.grant_write_settings,
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } else {
                    vibrate()
                    // Send the state the user just agreed to, not "flip whatever
                    // is current" — those can differ if the hotspot changed while
                    // the dialog was up.
                    BatteryMonitorService.setHotspot(this, enable = !isCurrentlyEnabled)
                }
                finish()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
    }
}
