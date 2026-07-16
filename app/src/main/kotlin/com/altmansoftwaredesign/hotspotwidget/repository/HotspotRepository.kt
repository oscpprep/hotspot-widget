package com.altmansoftwaredesign.hotspotwidget.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.altmansoftwaredesign.hotspotwidget.model.HotspotState
import kotlinx.coroutines.delay
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds

/**
 * Toggles and queries the Wi-Fi hotspot.
 *
 * Android exposes no public API for a non-system app to toggle tethering, so we
 * drive the hidden TetheringManager via reflection. start/stopTethering require
 * WRITE_SETTINGS (which the user grants once) or the system-only TETHER_PRIVILEGED.
 * This is inherently fragile — the hidden signatures may change in future Android
 * releases — hence the suppressed reflection/spell-check warnings.
 */
class HotspotRepository(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @SuppressLint("MissingPermission")
    suspend fun getHotspotState(): HotspotState {
        return try {
            HotspotState(isEnabled = isHotspotEnabled())
        } catch (e: Exception) {
            // Unknown, NOT off. Reporting "off" here would be a confident lie the
            // moment the hidden API changes under us.
            Log.w("HotspotRepo", "Could not read hotspot state", e)
            HotspotState(isEnabled = null)
        }
    }

    suspend fun enableHotspot(): Boolean {
        return try {
            startWifiTethering()
            delay(1.seconds)
            true
        } catch (e: Exception) {
            Log.w("HotspotRepo", "enableHotspot failed", e)
            false
        }
    }

    suspend fun disableHotspot(): Boolean {
        return try {
            stopWifiTethering()
            delay(1.seconds)
            true
        } catch (e: Exception) {
            Log.w("HotspotRepo", "disableHotspot failed", e)
            false
        }
    }

    // TetheringManager.startTethering(TetheringRequest, Executor, StartTetheringCallback).
    // The callback is an interface, so a Proxy satisfies it (an OnStartTetheringCallback
    // abstract class could not be). Type 0 == TETHERING_WIFI.
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    private fun startWifiTethering() {
        val tetheringManager = appContext.getSystemService("tethering")
            ?: throw IllegalStateException("TetheringManager unavailable")

        val builderClass =
            Class.forName("android.net.TetheringManager" + '$' + "TetheringRequest" + '$' + "Builder")
        val builder = builderClass.getConstructor(Int::class.javaPrimitiveType)
            .newInstance(TETHERING_WIFI)
        val request = builderClass.getMethod("build").invoke(builder)
        val requestClass = Class.forName("android.net.TetheringManager" + '$' + "TetheringRequest")

        val callbackClass = Class.forName("android.net.TetheringManager" + '$' + "StartTetheringCallback")
        val callback = Proxy.newProxyInstance(
            callbackClass.classLoader,
            arrayOf(callbackClass),
            noopInvocationHandler
        )

        val startMethod = tetheringManager.javaClass.getMethod(
            "startTethering", requestClass, Executor::class.java, callbackClass
        )
        startMethod.invoke(tetheringManager, request, Executor { it.run() }, callback)
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    private fun stopWifiTethering() {
        val tetheringManager = appContext.getSystemService("tethering")
            ?: throw IllegalStateException("TetheringManager unavailable")
        val stopMethod =
            tetheringManager.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
        stopMethod.invoke(tetheringManager, TETHERING_WIFI)
    }

    // Proxy handler for the StartTetheringCallback interface. We don't act on the
    // result here — the widget/notification re-read the true state shortly after —
    // so callback methods (onTetheringStarted/onTetheringFailed) are no-ops.
    private val noopInvocationHandler = InvocationHandler { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.getOrNull(0)
            "toString" -> "StartTetheringCallbackProxy"
            else -> null
        }
    }

    // "getTetheredIfaces" is the real (hidden) ConnectivityManager method name,
    // and "wlan"/"ap" are the actual OS network-interface name prefixes for the
    // Wi-Fi tethering interface (e.g. wlan0, ap0). They are external identifiers,
    // not typos, so spell-checking is suppressed for this function only.
    // Throws if the reflection fails; the caller turns that into an "unknown"
    // state. Do not swallow it into `false` — see [getHotspotState].
    @Suppress("SpellCheckingInspection")
    @SuppressLint("MissingPermission", "PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    private fun isHotspotEnabled(): Boolean {
        val method = connectivityManager.javaClass.getDeclaredMethod("getTetheredIfaces")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tethered = method.invoke(connectivityManager) as Array<String>
        return tethered.any { it.startsWith("wlan") || it.startsWith("ap") }
    }

    private companion object {
        const val TETHERING_WIFI = 0
    }
}
