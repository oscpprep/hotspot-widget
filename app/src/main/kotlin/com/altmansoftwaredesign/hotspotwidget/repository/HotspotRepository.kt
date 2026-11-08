package com.altmansoftwaredesign.hotspotwidget.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.altmansoftwaredesign.hotspotwidget.model.HotspotState
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/**
 * Toggles and queries the Wi-Fi hotspot.
 *
 * Android does not provide a generally available public API for third-party apps
 * to toggle tethering. We use TetheringManager through reflection and wait for
 * its asynchronous callback so callers can see the actual system result.
 */
class HotspotRepository(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    data class ToggleResult(
        val succeeded: Boolean,
        val message: String,
        val errorCode: Int? = null
    )

    @SuppressLint("MissingPermission")
    suspend fun getHotspotState(): HotspotState {
        return try {
            HotspotState(isEnabled = isHotspotEnabled())
        } catch (e: Exception) {
            Log.w(TAG, "Could not read hotspot state", e)
            HotspotState(isEnabled = null)
        }
    }

    suspend fun enableHotspot(): ToggleResult {
        return try {
            val errorCode = startWifiTethering()
            if (errorCode != TETHER_ERROR_NO_ERROR) {
                val reason = describeTetheringError(errorCode)
                Log.w(TAG, "Android rejected Wi-Fi tethering start: $errorCode ($reason)")
                return ToggleResult(
                    succeeded = false,
                    message = "Android rejected the hotspot request. $reason (error $errorCode).",
                    errorCode = errorCode
                )
            }

            when (waitForHotspotState(expected = true)) {
                true -> ToggleResult(true, "The Wi-Fi hotspot is ON.")
                false -> ToggleResult(
                    false,
                    "Android accepted the request, but the hotspot did not become active. Check Quick Settings > Mobile Hotspot and try enabling it manually. Your carrier or Samsung's tethering service may be blocking the request."
                )
                null -> ToggleResult(
                    false,
                    "Android accepted the request, but this app could not verify that the hotspot became active."
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "enableHotspot failed", e)
            ToggleResult(false, "Could not start the hotspot: ${describeException(e)}")
        }
    }

    suspend fun disableHotspot(): ToggleResult {
        return try {
            stopWifiTethering()
            when (waitForHotspotState(expected = false)) {
                true -> ToggleResult(true, "The Wi-Fi hotspot is OFF.")
                false -> ToggleResult(
                    false,
                    "Android accepted the stop request, but the hotspot still appears to be active. Turn it off from Quick Settings > Mobile Hotspot."
                )
                null -> ToggleResult(
                    false,
                    "Android accepted the stop request, but this app could not verify that the hotspot turned off."
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "disableHotspot failed", e)
            ToggleResult(false, "Could not stop the hotspot: ${describeException(e)}")
        }
    }

    /**
     * TetheringManager.startTethering(TetheringRequest, Executor, StartTetheringCallback).
     * StartTetheringCallback is an interface on supported Android releases, so a
     * Proxy can receive both success and failure. Do not report success merely
     * because the asynchronous request was submitted.
     */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    private suspend fun startWifiTethering(): Int {
        val tetheringManager = appContext.getSystemService("tethering")
            ?: throw IllegalStateException("Android did not provide TetheringManager")

        val builderClass = Class.forName(
            "android.net.TetheringManager\$TetheringRequest\$Builder"
        )
        val builder = builderClass.getConstructor(Int::class.javaPrimitiveType)
            .newInstance(TETHERING_WIFI)
        val request = builderClass.getMethod("build").invoke(builder)
        val requestClass = Class.forName("android.net.TetheringManager\$TetheringRequest")
        val callbackClass = Class.forName(
            "android.net.TetheringManager\$StartTetheringCallback"
        )

        if (!callbackClass.isInterface) {
            throw IllegalStateException(
                "This Android version exposes an unsupported StartTetheringCallback type"
            )
        }

        val startMethod = tetheringManager.javaClass.getMethod(
            "startTethering", requestClass, Executor::class.java, callbackClass
        )

        return withTimeout(45.seconds) {
            suspendCancellableCoroutine { continuation ->
                val callbackHandler = InvocationHandler { proxy, method, args ->
                    when (method.name) {
                        "onTetheringStarted" -> {
                            Log.i(TAG, "Android callback: onTetheringStarted")
                            if (continuation.isActive) continuation.resume(TETHER_ERROR_NO_ERROR)
                        }
                        "onTetheringFailed" -> {
                            val error = (args?.firstOrNull() as? Number)?.toInt() ?: UNKNOWN_ERROR
                            Log.w(TAG, "Android callback: onTetheringFailed($error)")
                            if (continuation.isActive) continuation.resume(error)
                        }
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.getOrNull(0)
                        "toString" -> "StartTetheringCallbackProxy"
                        else -> null
                    }
                }

                try {
                    val callback = Proxy.newProxyInstance(
                        callbackClass.classLoader,
                        arrayOf(callbackClass),
                        callbackHandler
                    )
                    startMethod.invoke(
                        tetheringManager,
                        request,
                        Executor { command -> command.run() },
                        callback
                    )
                } catch (e: InvocationTargetException) {
                    val cause = e.targetException ?: e
                    if (continuation.isActive) continuation.resumeWithException(cause)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    private fun stopWifiTethering() {
        val tetheringManager = appContext.getSystemService("tethering")
            ?: throw IllegalStateException("Android did not provide TetheringManager")
        val stopMethod = tetheringManager.javaClass.getMethod(
            "stopTethering", Int::class.javaPrimitiveType
        )
        stopMethod.invoke(tetheringManager, TETHERING_WIFI)
    }

    private suspend fun waitForHotspotState(expected: Boolean): Boolean? {
        var lastState: Boolean? = null
        repeat(24) {
            lastState = try {
                isHotspotEnabled()
            } catch (e: Exception) {
                Log.w(TAG, "Could not verify hotspot state while waiting", e)
                null
            }
            if (lastState == expected) return true
            delay(500)
        }
        return if (lastState == null) null else false
    }

    @Suppress("SpellCheckingInspection")
    @SuppressLint("MissingPermission", "PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    private fun isHotspotEnabled(): Boolean {
        val method = connectivityManager.javaClass.getDeclaredMethod("getTetheredIfaces")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tethered = method.invoke(connectivityManager) as Array<String>
        return tethered.any { it.startsWith("wlan") || it.startsWith("ap") }
    }

    private fun describeTetheringError(code: Int): String = when (code) {
        1 -> "Android could not find the Wi-Fi interface."
        2 -> "The Android tethering service is unavailable."
        3 -> "Wi-Fi hotspot tethering is unsupported on this device or current configuration."
        4 -> "The Wi-Fi interface is currently unavailable."
        5 -> "Android reported an internal tethering error."
        6 -> "Android could not configure the Wi-Fi hotspot interface."
        7 -> "Android could not stop the Wi-Fi hotspot interface."
        8 -> "Android could not enable network forwarding."
        9 -> "Android could not disable network forwarding."
        10 -> "Android could not configure the hotspot interface."
        11 -> "The carrier tethering/provisioning check failed."
        12 -> "Android could not start the hotspot DHCP server."
        13 -> "The carrier tethering entitlement check could not be completed."
        14 -> "Android denied permission to change tethering. Re-check the app's Modify system settings access."
        15 -> "Android denied access to the tethering/network state."
        16 -> "Android does not recognize the Wi-Fi tethering request."
        else -> "Android returned an unrecognized tethering error."
    }

    private fun describeException(error: Exception): String {
        val cause = if (error is InvocationTargetException) error.targetException ?: error else error
        val detail = cause.message?.takeIf { it.isNotBlank() }
        return if (detail == null) {
            cause.javaClass.simpleName
        } else {
            "${cause.javaClass.simpleName}: $detail"
        }
    }

    private companion object {
        const val TAG = "HotspotRepo"
        const val TETHERING_WIFI = 0
        const val TETHER_ERROR_NO_ERROR = 0
        const val UNKNOWN_ERROR = -1
    }
}
