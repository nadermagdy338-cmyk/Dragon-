/*
 * Copyright (C) 2026 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max

import android.content.Context
import android.provider.Settings
import android.util.Log
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Method

/**
 * XiaomiVendorFeatures
 * ─────────────────────────────────────────────────────────────────────────
 * Ported from the "Xiaomi Parts" system app concept (TouchSamplingService /
 * ColorService), adapted to run safely inside MaxManager's headless companion
 * process (sys.maxmanager-appmonitoring) instead of as a privileged
 * platform_apis=true system app.
 *
 * WHY REFLECTION INSTEAD OF A BUNDLED .aidl FILE:
 *   The real interfaces (vendor.xiaomi.hw.touchfeature.ITouchFeature and
 *   vendor.xiaomi.hardware.displayfeature_aidl.IDisplayFeature) are only
 *   distributed as prebuilt Java stubs inside Xiaomi's proprietary ROM
 *   source tree (Soong static_libs). They are NOT on the public Android
 *   SDK or any Maven repo, so a normal Gradle app cannot compile against
 *   them. Re-declaring a *local* copy of the .aidl file from scratch would
 *   be guessing at the exact method order/signature Xiaomi's compiler used
 *   for THIS specific ROM build — get it wrong and the binder transaction
 *   silently calls a different method than intended on real touch/display
 *   hardware, which is not an acceptable risk on someone's actual phone.
 *
 *   Instead, at runtime we look for the *real* on-device jar that already
 *   contains the ROM's own compiled Stub/Proxy class (same jar the system
 *   Settings app or the real Xiaomi Parts app would use, if present), load
 *   it with a PathClassLoader, and call its `setTouchMode` / `setFeature`
 *   method by reflection. Because we're invoking the ROM's own compiled
 *   Proxy object, the transaction code is always the one that ROM build
 *   actually uses — we never invent it ourselves.
 *
 * SAFETY / COMPATIBILITY:
 *   Every public entry point below degrades to a fast no-op the moment any
 *   expected jar, class, method, or service is missing. This keeps MaxManager
 *   safe to run unmodified on every non-Xiaomi device and on any Xiaomi
 *   device/kernel generation where these specific AIDL services don't
 *   exist (older HIDL-based touchfeature@1.0 devices are intentionally
 *   NOT handled here — see PROJECT_NOTES.md).
 */
object XiaomiVendorFeatures {
    private const val TAG = "XiaomiVendorFeatures"
    private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

    private val CANDIDATE_DIRS = listOf(
        "/system/framework",
        "/system_ext/framework",
        "/product/framework",
        "/vendor/framework",
    )

    // ── Touch sampling boost ────────────────────────────────────────────
    private const val TOUCH_IFACE = "vendor.xiaomi.hw.touchfeature.ITouchFeature"
    private const val TOUCH_SERVICE = "$TOUCH_IFACE/default"
    private var touchProxy: Any? = null
    private var touchSetModeMethod: Method? = null
    private var touchProbed = false
    private var lastTouchState = -1

    // ── Display color mode ──────────────────────────────────────────────
    private const val DISPLAY_IFACE = "vendor.xiaomi.hardware.displayfeature_aidl.IDisplayFeature"
    private const val DISPLAY_SERVICE = "$DISPLAY_IFACE/default"
    private var displayProxy: Any? = null
    private var displaySetFeatureMethod: Method? = null
    private var displayProbed = false
    private var lastForcedAod = false

    /**
     * Settings.System.DISPLAY_COLOR_MODE id -> (mode, value, cookie) triplet
     * for the setFeature() AIDL call. Copied verbatim from ColorService.kt's
     * ColorMode enum so restoring the user's actual selection (not just a
     * hardcoded STANDARD) works correctly once the screen turns back on.
     * Expert (wide-gamut) entries are intentionally left out: those also
     * require the extra EXPERT_MODE follow-up call in ColorService.kt and
     * are out of scope for this AOD-only override.
     */
    private val COLOR_MODE_MAP = mapOf(
        258 to Triple(0, 2, 255), // VIVID
        256 to Triple(1, 2, 255), // SATURATED
        257 to Triple(2, 2, 255), // STANDARD
    )
    private val STANDARD_TRIPLE = COLOR_MODE_MAP.getValue(257)

    // ─────────────────────────────────────────────────────────────────────
    // Generic on-device discovery helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun findJar(nameContains: String): File? {
        for (dir in CANDIDATE_DIRS) {
            val files = runCatching { File(dir).listFiles() }.getOrNull() ?: continue
            for (file in files) {
                if (file.name.endsWith(".jar") && file.name.contains(nameContains, ignoreCase = true)) {
                    return file
                }
            }
        }
        return null
    }

    private fun getDeclaredBinder(serviceName: String): android.os.IBinder? = runCatching {
        val sm = Class.forName("android.os.ServiceManager")
        runCatching {
            sm.getMethod("waitForDeclaredService", String::class.java)
                .invoke(null, serviceName) as? android.os.IBinder
        }.getOrNull() ?: sm.getMethod("getService", String::class.java)
            .invoke(null, serviceName) as? android.os.IBinder
    }.getOrNull()

    /**
     * Loads [jarNameContains]'s jar (if present), binds to [serviceName] via
     * its real on-device Stub class, and returns (proxyInstance, method)
     * for [methodName], or null if any step is unavailable.
     */
    private fun bind(jarNameContains: String, ifaceDescriptor: String, serviceName: String, methodName: String, paramTypes: Array<Class<*>>): Pair<Any, Method>? {
        return runCatching {
            val jar = findJar(jarNameContains) ?: run {
                if (DEBUG) Log.d(TAG, "No jar matching '$jarNameContains' on this ROM")
                return null
            }
            val loader = PathClassLoader(jar.absolutePath, javaClass.classLoader)
            val stubClass = Class.forName("$ifaceDescriptor\$Stub", true, loader)
            val binder = getDeclaredBinder(serviceName) ?: run {
                if (DEBUG) Log.d(TAG, "Service '$serviceName' not declared on this device")
                return null
            }
            val proxy = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                .invoke(null, binder) ?: return null
            val method = proxy.javaClass.getMethod(methodName, *paramTypes)
            if (DEBUG) Log.d(TAG, "Bound to $serviceName via ${jar.name}")
            proxy to method
        }.onFailure { e ->
            if (DEBUG) Log.d(TAG, "bind($serviceName) failed: ${e.message}")
        }.getOrNull()
    }

    private fun ensureTouchBound(): Boolean {
        if (touchProbed) return touchProxy != null
        touchProbed = true
        val bound = bind(
            "touchfeature", TOUCH_IFACE, TOUCH_SERVICE, "setTouchMode",
            arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
        )
        touchProxy = bound?.first
        touchSetModeMethod = bound?.second
        return touchProxy != null
    }

    private fun ensureDisplayBound(): Boolean {
        if (displayProbed) return displayProxy != null
        displayProbed = true
        val bound = bind(
            "displayfeature", DISPLAY_IFACE, DISPLAY_SERVICE, "setFeature",
            arrayOf<Class<*>>(
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
            ),
        )
        displayProxy = bound?.first
        displaySetFeatureMethod = bound?.second
        return displayProxy != null
    }

    // ─────────────────────────────────────────────────────────────────────
    // Touch sampling boost — mirrors TouchSamplingService.applyTouchSamplingRate
    // ─────────────────────────────────────────────────────────────────────

    /** Returns true only when the real Xiaomi touch-feature AIDL can be bound. */
    fun isTouchFeatureAvailable(): Boolean = ensureTouchBound()

    /**
     * @param boost true = high polling rate / max sensitivity (game focused),
     *              false = return touch hardware to baseline.
     */
    fun applyTouchBoost(boost: Boolean): Boolean {
        val state = if (boost) 1 else 0
        if (state == lastTouchState) return true
        if (!ensureTouchBound()) return false

        val proxy = touchProxy ?: return false
        val method = touchSetModeMethod ?: return false

        // Same six calls as Xiaomi Parts' TouchSamplingService, each wrapped
        // individually so one unsupported mode index doesn't block the rest.
        val calls = listOf(
            Triple(0, 0, state),
            Triple(0, 202, state),
            Triple(0, 1, state),
            Triple(0, 3, if (state == 1) 34 else 0),
            Triple(0, 2, if (state == 1) 99 else 0),
            Triple(0, 7, if (state == 1) 0 else 1),
        )
        var anySucceeded = false
        for ((a, b, c) in calls) {
            runCatching { method.invoke(proxy, a, b, c) }.onSuccess { anySucceeded = true }
        }
        if (anySucceeded) {
            lastTouchState = state
            if (DEBUG) Log.d(TAG, "Touch boost applied: state=$state")
        }
        return anySucceeded
    }

    // ─────────────────────────────────────────────────────────────────────
    // Display colour mode — mirrors ColorService's AOD STANDARD override
    // ─────────────────────────────────────────────────────────────────────

    /**
     * While the screen is off with AOD showing, forces STANDARD colour mode
     * (vivid/saturated panels can look wrong in an always-on-display strip).
     * Restores the user's actual chosen colour mode as soon as the screen
     * turns back on. Purely best-effort: if the AIDL service isn't present
     * this simply never touches display state.
     */
    fun applyAodColorOverride(context: Context?, screenAwake: Boolean, aodEnabled: Boolean) {
        if (context == null) return
        val shouldForce = !screenAwake && aodEnabled

        if (shouldForce == lastForcedAod) return
        if (!ensureDisplayBound()) return

        val proxy = displayProxy ?: return
        val method = displaySetFeatureMethod ?: return

        runCatching {
            val (mode, value, cookie) = if (shouldForce) {
                STANDARD_TRIPLE
            } else {
                // Re-read and re-apply whatever mode the user actually has
                // selected in Settings instead of hardcoding one. Falls back
                // to STANDARD if the stored id isn't one of the basic modes
                // (e.g. an expert/wide-gamut mode, out of scope here).
                val modeId = Settings.System.getInt(
                    context.contentResolver, "display_color_mode", 257,
                )
                COLOR_MODE_MAP[modeId] ?: STANDARD_TRIPLE
            }
            method.invoke(proxy, 0, mode, value, cookie)
            lastForcedAod = shouldForce
            if (DEBUG) Log.d(TAG, "AOD colour override: forced=$shouldForce")
        }
    }
}
