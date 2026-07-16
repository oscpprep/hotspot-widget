package com.altmansoftwaredesign.hotspotwidget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Small first-run / setup screen. The app is driven entirely from the home-screen
 * widget; this screen just explains that and lets the user grant the one-time
 * WRITE_SETTINGS permission the hotspot toggle needs.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var grantButton: Button

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        statusView = findViewById(R.id.permission_status)
        grantButton = findViewById(R.id.grant_button)
        grantButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    /**
     * POST_NOTIFICATIONS is declared but is denied by default on Android 13+, and
     * nothing was ever asking for it — so on a fresh install there the ongoing
     * status notification would simply never appear. This screen is the only
     * foreground surface the app has, so it is the only place that can ask.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check on return from the settings screen.
        val granted = Settings.System.canWrite(this)
        statusView.text = getString(
            if (granted) R.string.setup_status_granted else R.string.setup_status_needed
        )
        grantButton.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }
}
