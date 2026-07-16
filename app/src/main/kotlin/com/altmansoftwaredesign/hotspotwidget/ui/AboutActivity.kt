package com.altmansoftwaredesign.hotspotwidget.ui

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.altmansoftwaredesign.hotspotwidget.R
import com.altmansoftwaredesign.hotspotwidget.util.appVersionName

/**
 * Tiny transparent dialog shown when the battery side of the widget is tapped.
 * Displays the app name and version.
 */
class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.about_version, appVersionName()))
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
