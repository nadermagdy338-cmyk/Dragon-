/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Backing types/helpers for the Doze Mode screen. DozeModeScreen.kt was
 * committed importing these from nd.max.ui.util but the file itself
 * never existed in the repo — this fills that gap.
 *
 * Everything here rides on Android's own DeviceIdleController shell
 * interface (`dumpsys deviceidle ...`) and the public PowerManager /
 * ActivityManager "app standby bucket" APIs — there's no MaxManager-specific
 * daemon involvement (the Rust/C services have no doze-related code), so
 * this talks to the platform directly via root shell where a public API
 * isn't available.
 *
 * Two things worth flagging explicitly rather than guessing silently:
 *
 * 1. "Idle cycles" / "Light idle cycles" stats: Android doesn't expose a
 *    historical counter of deep/light idle cycles through any stable API.
 *    What's implemented here is a cheap approximation — each time state is
 *    refreshed (screen open / after an action), it's compared against the
 *    last-seen state persisted in SharedPreferences, and the counter bumps
 *    on a transition into IDLE. That only catches transitions that happen
 *    while this screen (or a refresh) is active, not a true 24/7 system
 *    count — a real historical counter would need a background service
 *    listening for ACTION_DEVICE_IDLE_MODE_CHANGED.
 *
 * 2. GmsDozeMode (Default/Standard/Aggressive): there's no 3-tier "how
 *    strictly is this app doze-restricted" knob in Android — the closest
 *    real, stable mechanism is the App Standby Bucket
 *    (`am get/set-standby-bucket`), which *does* have graduated strictness.
 *    This maps the three UI options onto standby buckets (active / working_set
 *    / restricted). If a different mechanism was intended, this is the
 *    piece to swap out — everything else here is independent of it.
 *
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PowerManager
import com.topjohnwu.superuser.Shell

/** A single installed app as shown on the Doze Mode whitelist list. */
data class DozeAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isWhitelisted: Boolean
)

/** Mirrors DeviceIdleController's deep-doze state machine (`mState`). */
enum class DozeState {
    ACTIVE, IDLE_PENDING, SENSING, LOCATING, MAINTENANCE, IDLE, UNKNOWN
}

/**
 * How strictly Google Play Services is app-standby-restricted while the
 * device is idle. Backed by App Standby Buckets — see file header.
 */
enum class GmsDozeMode(val bucket: String) {
    DEFAULT("active"),
    STANDARD("working_set"),
    AGGRESSIVE("restricted")
}

object DozeModeUtil {

    private const val GMS_PACKAGE = "com.google.android.gms"
    private const val PREFS_NAME = "maxmanager_doze_stats"

    data class DozeStats(val idleCount: Int, val lightIdleCount: Int)

    /** Whether this device's DeviceIdleController responds to `dumpsys deviceidle`. */
    fun isSupported(): Boolean {
        val result = Shell.cmd("dumpsys deviceidle get deep").exec()
        return result.isSuccess && result.out.joinToString("").isNotBlank()
    }

    /** Reads the current deep-doze state via `dumpsys deviceidle get deep`. */
    fun getDozeState(): DozeState {
        val direct = Shell.cmd("dumpsys deviceidle get deep").exec().out
            .joinToString("") { it.trim() }
            .trim()
        val stateName = if (direct.isNotBlank()) {
            direct
        } else {
            // Fallback for devices/OEM forks where `get deep` isn't wired up:
            // parse the full dump for "mState=<NAME>".
            val fullDump = Shell.cmd("dumpsys deviceidle").exec().out
            fullDump.firstOrNull { it.trim().startsWith("mState=") }
                ?.substringAfter("mState=")
                ?.trim()
                ?: ""
        }
        return mapState(stateName)
    }

    /** Reads the current light-doze state via `dumpsys deviceidle get light`. */
    private fun getLightDozeState(): DozeState {
        val raw = Shell.cmd("dumpsys deviceidle get light").exec().out
            .joinToString("") { it.trim() }
            .trim()
        return mapState(raw)
    }

    private fun mapState(name: String): DozeState = when (name.uppercase()) {
        "ACTIVE", "INACTIVE" -> DozeState.ACTIVE
        "IDLE_PENDING" -> DozeState.IDLE_PENDING
        "SENSING" -> DozeState.SENSING
        "LOCATING" -> DozeState.LOCATING
        "IDLE_MAINTENANCE" -> DozeState.MAINTENANCE
        "IDLE" -> DozeState.IDLE
        "QUICK_DOZE_DELAY" -> DozeState.IDLE_PENDING
        else -> DozeState.UNKNOWN
    }

    fun forceIdle(): Boolean = Shell.cmd("dumpsys deviceidle force-idle").exec().isSuccess

    fun stepIdleState(): Boolean = Shell.cmd("dumpsys deviceidle step").exec().isSuccess

    fun unforceIdle(): Boolean = Shell.cmd("dumpsys deviceidle unforce").exec().isSuccess

    /**
     * Reads/updates the local idle-cycle counters. Call after any state
     * refresh (see file header for what this does and doesn't capture).
     */
    fun refreshStats(context: Context, currentDeep: DozeState): DozeStats {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDeep = prefs.getString("last_deep_state", null)
        val lastLight = prefs.getString("last_light_state", null)
        val currentLight = getLightDozeState()

        var idleCount = prefs.getInt("idle_count", 0)
        var lightIdleCount = prefs.getInt("light_idle_count", 0)

        if (currentDeep == DozeState.IDLE && lastDeep != DozeState.IDLE.name) {
            idleCount += 1
        }
        if (currentLight == DozeState.IDLE && lastLight != DozeState.IDLE.name) {
            lightIdleCount += 1
        }

        prefs.edit()
            .putString("last_deep_state", currentDeep.name)
            .putString("last_light_state", currentLight.name)
            .putInt("idle_count", idleCount)
            .putInt("light_idle_count", lightIdleCount)
            .apply()

        return DozeStats(idleCount, lightIdleCount)
    }

    fun resetStats(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("idle_count", 0)
            .putInt("light_idle_count", 0)
            .apply()
    }

    /** Public, no-root read of whether [packageName] is exempt from battery optimizations. */
    fun isIgnoringBatteryOptimizations(context: Context, packageName: String): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
        return pm?.isIgnoringBatteryOptimizations(packageName) == true
    }

    /** Adds/removes [packageName] from the deep-doze whitelist ("unrestricted battery"). */
    fun setWhitelisted(packageName: String, whitelisted: Boolean): Boolean {
        val op = if (whitelisted) "+" else "-"
        return Shell.cmd("dumpsys deviceidle whitelist $op$packageName").exec().isSuccess
    }

    fun getInstalledApps(context: Context): List<DozeAppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        return packages.mapNotNull { appInfo ->
            try {
                DozeAppInfo(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null },
                    isWhitelisted = isIgnoringBatteryOptimizations(context, appInfo.packageName)
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase() }
    }

    fun getGmsDozeMode(): GmsDozeMode {
        val raw = Shell.cmd("am get-standby-bucket $GMS_PACKAGE").exec().out
            .joinToString("") { it.trim() }
            .trim()
            .lowercase()
        return GmsDozeMode.entries.firstOrNull { it.bucket == raw } ?: GmsDozeMode.DEFAULT
    }

    fun setGmsDozeMode(mode: GmsDozeMode): Boolean =
        Shell.cmd("am set-standby-bucket $GMS_PACKAGE ${mode.bucket}").exec().isSuccess
}
