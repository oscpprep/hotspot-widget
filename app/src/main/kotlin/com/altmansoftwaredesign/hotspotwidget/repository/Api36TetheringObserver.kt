package com.altmansoftwaredesign.hotspotwidget.repository

import android.content.Context
import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Android 16+ public tethering observer.
 *
 * Kept in its own class so devices below API 36 never need to load classes
 * that do not exist on their platform.
 */
@RequiresApi(36)
object Api36TetheringObserver {

    fun register(
        context: Context,
        onStateChanged: (Boolean) -> Unit
    ): () -> Unit {
        check(Build.VERSION.SDK_INT >= 36) {
            "The public TetheringManager callback requires Android 16 (API 36)"
        }

        val appContext = context.applicationContext
        val manager = appContext.getSystemService(TetheringManager::class.java)
            ?: throw IllegalStateException("TetheringManager is unavailable")

        val callback = object : TetheringManager.TetheringEventCallback {
            override fun onTetheredInterfacesChanged(interfaces: MutableSet<TetheringInterface>) {
                val wifiIsTethered = interfaces.any {
                    it.type == TetheringManager.TETHERING_WIFI
                }
                onStateChanged(wifiIsTethered)
            }
        }

        manager.registerTetheringEventCallback(appContext.mainExecutor, callback)
        return {
            try {
                manager.unregisterTetheringEventCallback(callback)
            } catch (_: Exception) {
                // The system may already have removed the callback on process teardown.
            }
        }
    }
}
