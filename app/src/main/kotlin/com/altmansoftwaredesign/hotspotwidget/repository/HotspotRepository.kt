package com.altmansoftwaredesign.hotspotwidget.repository

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Read-only hotspot state detection.
 *
 * TetheringManager is present on supported Android releases but is a hidden
 * / SystemApi in the Android 34 public SDK. All access is therefore reflective
 * so the app compiles against the normal SDK and remains installable on API 31+.
 *
 * We first register the system tethering event callback. If that is unavailable
 * on an OEM build, we fall back to ConnectivityManager's legacy tethered-ifaces
 * query, including Samsung's swlan* interface naming.
 */
class HotspotRepository(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isHotspotEnabled(): Boolean? {
        readStateFromTetheringCallback()?.let { return it }
        return readStateFromLegacyFallback()
    }

    private fun readStateFromTetheringCallback(): Boolean? {
        val manager = appContext.getSystemService("tethering") ?: return null
        var callback: Any? = null

        return try {
            val callbackClass = Class.forName(
                "android.net.TetheringManager\$TetheringEventCallback"
            )
            if (!callbackClass.isInterface) return null

            val latch = CountDownLatch(1)
            var state: Boolean? = null

            val handler = InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "onTetheredInterfacesChanged" -> {
                        val entries = args?.firstOrNull()
                        val names = when (entries) {
                            is Iterable<*> -> entries.mapNotNull(::extractInterfaceName)
                            is Array<*> -> entries.mapNotNull(::extractInterfaceName)
                            else -> emptyList()
                        }
                        state = names.any(::isWifiTetherInterface)
                        Log.d(TAG, "Tethering callback interfaces=$names, hotspot=$state")
                        latch.countDown()
                        null
                    }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    "toString" -> "HotspotTetheringEventCallback"
                    else -> null
                }
            }

            callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
                handler
            )

            val register = manager.javaClass.getMethod(
                "registerTetheringEventCallback",
                Executor::class.java,
                callbackClass
            )
            register.invoke(manager, Executor { command -> command.run() }, callback)

            if (!latch.await(CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for initial tethering callback")
                null
            } else {
                state
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reflective tethering callback unavailable; using legacy state query", e)
            null
        } finally {
            if (callback != null) {
                try {
                    val callbackClass = callback.javaClass.interfaces.firstOrNull()
                    val unregister = callbackClass?.let {
                        manager.javaClass.getMethod("unregisterTetheringEventCallback", it)
                    }
                    unregister?.invoke(manager, callback)
                } catch (e: Exception) {
                    Log.d(TAG, "Could not unregister tethering event callback", e)
                }
            }
        }
    }

    private fun extractInterfaceName(value: Any?): String? {
        if (value == null) return null
        if (value is String) return value

        return try {
            value.javaClass.getMethod("getInterface").invoke(value) as? String
        } catch (_: Exception) {
            value.toString()
        }
    }

    private fun readStateFromLegacyFallback(): Boolean? {
        return try {
            @Suppress("DiscouragedPrivateApi", "SoonBlockedPrivateApi")
            val method = connectivityManager.javaClass.getDeclaredMethod("getTetheredIfaces")
            method.isAccessible = true
            val tethered = method.invoke(connectivityManager) as? Array<*>
                ?: return null
            val names = tethered.filterIsInstance<String>()
            val enabled = names.any(::isWifiTetherInterface)
            Log.d(TAG, "Legacy tethered interfaces=$names, hotspot=$enabled")
            enabled
        } catch (e: Exception) {
            Log.w(TAG, "Could not read active tethered interfaces", e)
            null
        }
    }

    private fun isWifiTetherInterface(name: String): Boolean {
        val iface = name.lowercase()
        return iface.startsWith("wlan") ||
            iface.startsWith("swlan") ||
            iface.startsWith("ap") ||
            iface.startsWith("softap") ||
            iface.startsWith("wifi")
    }

    private companion object {
        const val TAG = "HotspotWidget"
        const val CALLBACK_TIMEOUT_MS = 1200L
    }
}
