/*
 * MaxManager per-app refresh-rate controller.
 *
 * Inspired by Adaptive-Hz's vendor-aware Xiaomi strategy, while keeping
 * MaxManager's existing generic nodes as fallbacks for other devices/ROMs.
 */
package nd.max

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import java.util.Locale

object PerAppRefreshRateController {
    data class Snapshot(
        val vendorNamespace: String,
        val vendorKey: String,
        val vendorValue: String,
        val vendorAltKey: String,
        val vendorAltValue: String,
        val peak: String,
        val min: String
    )

    private data class XiaomiProfile(val namespace: String, val key: String)

    fun supportedRates(context: Context): List<Int> = runCatching {
        // `Context#display` يطلب API 30 و`minSdk` هنا 29، و`DisplayManager.getDisplay` متاح من API 17
        // (`supportedModes` من API 23) — نفس النتيجة، وبلا سقف إصدار يرمي استثناءً على 29.
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.supportedModes
            ?.map { it.refreshRate.toInt() }
            ?.filter { it > 0 }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
    }.getOrDefault(emptyList())

    fun normalizeRequestedRate(context: Context, requested: Int): Int? {
        if (requested <= 0) return null
        val rates = supportedRates(context)
        if (rates.isEmpty()) return requested
        return rates.minByOrNull { kotlin.math.abs(it - requested) }
    }

    /**
     * Reads the strongest currently enforced refresh-rate value from the same
     * settings surfaces used by [apply]. Null means the ROM does not expose a
     * usable value, so the drift guard should leave it alone rather than guess.
     */
    fun currentEnforcedRate(context: Context): Int? {
        currentContext = context.applicationContext
        val profile = resolveXiaomiProfile()
        val vendor = shellRead("settings get ${profile.namespace} ${profile.key}").toFloatOrNull()?.toInt()
        if (vendor != null && vendor > 0) return vendor

        // Some HyperOS builds expose the alternate Xiaomi key instead of the
        // primary key resolved above. Check it before falling back to the
        // generic system setting so the drift guard does not repeatedly
        // re-apply an override that is already active.
        val alternateKey = if (profile.key == "user_refresh_rate") "miui_refresh_rate" else "user_refresh_rate"
        val alternate = shellRead("settings get ${profile.namespace} $alternateKey")
            .toFloatOrNull()?.toInt()
        if (alternate != null && alternate > 0) return alternate

        return shellRead("settings get system peak_refresh_rate").toFloatOrNull()?.toInt()
            ?.takeIf { it > 0 }
    }

    fun snapshot(context: Context): Snapshot {
        currentContext = context.applicationContext
        val profile = resolveXiaomiProfile()
        return Snapshot(
            vendorNamespace = profile.namespace,
            vendorKey = profile.key,
            vendorValue = shellRead("settings get ${profile.namespace} ${profile.key}"),
            // Xiaomi builds commonly keep both keys. Snapshot both so restore
            // never leaves a stale vendor override behind.
            vendorAltKey = if (profile.key == "user_refresh_rate") "miui_refresh_rate" else "user_refresh_rate",
            vendorAltValue = shellRead("settings get ${profile.namespace} ${if (profile.key == "user_refresh_rate") "miui_refresh_rate" else "user_refresh_rate"}"),
            peak = shellRead("settings get system peak_refresh_rate"),
            min = shellRead("settings get system min_refresh_rate")
        )
    }

    /**
     * Apply a per-app refresh-rate override. The vendor key is attempted first;
     * generic peak/min nodes remain a compatibility fallback for non-Xiaomi ROMs
     * and kernels that do not expose the vendor key.
     */
    fun apply(context: Context, hz: Int): Boolean {
        currentContext = context.applicationContext
        val value = normalizeRequestedRate(context, hz) ?: return false
        val profile = resolveXiaomiProfile()

        // Xiaomi/HyperOS may consult either secure key depending on the build.
        // Keep both synchronized for the duration of the per-app override.
        val primaryOk = writeSetting(profile.namespace, profile.key, value)
        val alternateKey = if (profile.key == "user_refresh_rate") "miui_refresh_rate" else "user_refresh_rate"
        val alternateOk = writeSetting(profile.namespace, alternateKey, value)

        // peak=max requested; min=requested while the per-app override is active.
        // This prevents HyperOS from selecting a lower mode (60/30) underneath us.
        val peakOk = writeSetting("system", "peak_refresh_rate", value)
        val minOk = writeSetting("system", "min_refresh_rate", value)

        // A requested rate is considered applied only after the actual settings
        // database values confirm it. This avoids reporting success when a ROM
        // silently rejects one of the writes.
        val vendorVerified = shellRead("settings get ${profile.namespace} ${profile.key}") == value.toString()
        val alternateVerified = shellRead("settings get ${profile.namespace} $alternateKey") == value.toString()
        val peakVerified = shellRead("settings get system peak_refresh_rate") == value.toString()
        val minVerified = shellRead("settings get system min_refresh_rate") == value.toString()

        return (primaryOk || alternateOk || peakOk || minOk) &&
                (vendorVerified || alternateVerified || (peakVerified && minVerified))
    }

    fun restore(context: Context, snapshot: Snapshot) {
        currentContext = context.applicationContext
        restoreSetting(snapshot.vendorNamespace, snapshot.vendorKey, snapshot.vendorValue)
        restoreSetting(snapshot.vendorNamespace, snapshot.vendorAltKey, snapshot.vendorAltValue)
        restoreSetting("system", "peak_refresh_rate", snapshot.peak)
        restoreSetting("system", "min_refresh_rate", snapshot.min)
    }

    private fun restoreSetting(namespace: String, key: String, value: String) {
        if (value.isBlank() || value == "null") {
            shellExec("settings delete $namespace '$key'")
            return
        }
        shellExec("settings put $namespace '$key' '$value'")
    }

    private fun writeSetting(namespace: String, key: String, value: Int): Boolean {
        // Keep the normal Android API path first.
        val apiOk = runCatching {
            when (namespace) {
                "secure" -> Settings.Secure.putInt((currentContext ?: return@runCatching false).contentResolver, key, value)
                "global" -> Settings.Global.putInt((currentContext ?: return@runCatching false).contentResolver, key, value)
                else -> Settings.System.putInt((currentContext ?: return@runCatching false).contentResolver, key, value)
            }
        }.getOrDefault(false)
        if (apiOk) return true

        // Root/shell fallback is important on HyperOS where the app's resolver
        // may reject a privileged settings write even though the service shell
        // is allowed to perform it.
        shellExec("settings put $namespace '$key' '$value'")
        return shellRead("settings get $namespace '$key'") == value.toString()
    }

    private var currentContext: Context? = null

    private fun resolveXiaomiProfile(): XiaomiProfile {
        val major = detectHyperOsMajor()
        return when (major) {
            1 -> XiaomiProfile("secure", "user_refresh_rate")
            else -> XiaomiProfile("secure", "miui_refresh_rate")
        }
    }

    private fun detectHyperOsMajor(): Int? {
        val props = listOf(
            "ro.mi.os.version.name",
            "ro.mi.os.version.incremental",
            "ro.build.version.incremental",
            "ro.build.display.id"
        )
        for (key in props) {
            val raw = shellRead("getprop '$key'").uppercase(Locale.ROOT)
            val match = Regex("(?:HYPER\\s*OS|HYPEROS|OS)[_\\- ]?(\\d+)").find(raw)
            val major = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (major != null) return major
        }
        return null
    }

    private fun shellRead(command: String): String = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out.lineSequence().firstOrNull()?.trim().orEmpty()
    }.getOrDefault("")

    private fun shellExec(command: String) {
        runCatching {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", command)).waitFor()
        }
    }

}
