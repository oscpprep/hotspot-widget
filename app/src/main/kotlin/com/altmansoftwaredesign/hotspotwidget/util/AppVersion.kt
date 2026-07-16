package com.altmansoftwaredesign.hotspotwidget.util

import android.content.Context

/**
 * The app's versionName, or "?" if it can't be read. Shared by the setup screen
 * and the About dialog so the two can't drift apart.
 */
fun Context.appVersionName(): String = try {
    packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
} catch (_: Exception) {
    "?"
}
