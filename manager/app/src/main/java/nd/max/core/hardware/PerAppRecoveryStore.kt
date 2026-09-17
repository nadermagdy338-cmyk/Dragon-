package nd.max.core.hardware

import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/** Crash/restart journal for per-app overrides. */
object PerAppRecoveryStore {
    private const val PATH = "/data/adb/.config/MaxManager/API/per-app-recovery.json"
    private val file: File get() = File(PATH)

    data class Snapshot(
        val bootId: String,
        val pid: Int = android.os.Process.myPid(),
        val packageName: String,
        val gpuNode: String,
        val gpuGovernor: String,
        val gpuMin: String,
        val gpuMax: String,
        val cpuGovernors: Map<String, String>,
        val cpuMinFreqs: Map<String, String> = emptyMap(),
        val cpuMaxFreqs: Map<String, String> = emptyMap(),
        val zenMode: String = "",
        val peakRefresh: String = "",
        val minRefresh: String = "",
        val vendorRefreshNamespace: String = "",
        val vendorRefreshKey: String = "",
        val vendorRefreshValue: String = "",
        val vendorRefreshAltKey: String = "",
        val vendorRefreshAltValue: String = "",
        val thermalProfile: String = "",
        val hwUiProp: String = "",
        val disableHwProp: String = "",
        val hapticEnabled: String = "",
    )


    @Synchronized
    fun markActive(packageName: String, baselines: Map<String, String>) {
        val json = JSONObject().apply {
            put("active", true)
            put("package", packageName)
            put("pid", android.os.Process.myPid())
            put("boot_id", bootId())
            val cpu = JSONObject()
            baselines.filterKeys { it.startsWith("cpu_governor:") }.forEach { (k, v) ->
                cpu.put(k.removePrefix("cpu_governor:"), v)
            }
            put("cpu_governors", cpu)
            put("gpu_node", baselines["gpu_node"].orEmpty())
            put("gpu_governor", baselines["gpu_governor"].orEmpty())
            put("gpu_min_freq", baselines["gpu_min_freq"].orEmpty())
            put("gpu_max_freq", baselines["gpu_max_freq"].orEmpty())
        }
        write(json)
    }

    @Synchronized
    fun capture(snapshot: Snapshot) {
        val json = JSONObject().apply {
            put("active", true)
            put("package", snapshot.packageName)
            put("pid", snapshot.pid)
            put("boot_id", snapshot.bootId)
            put("gpu_node", snapshot.gpuNode)
            put("gpu_governor", snapshot.gpuGovernor)
            put("gpu_min_freq", snapshot.gpuMin)
            put("gpu_max_freq", snapshot.gpuMax)
            put("zen_mode", snapshot.zenMode)
            put("peak_refresh", snapshot.peakRefresh)
            put("min_refresh", snapshot.minRefresh)
            put("thermal_profile", snapshot.thermalProfile)
            put("hw_ui_prop", snapshot.hwUiProp)
            put("disable_hw_prop", snapshot.disableHwProp)
            put("haptic_enabled", snapshot.hapticEnabled)
            val cpu = JSONObject()
            snapshot.cpuGovernors.forEach { (k, v) -> cpu.put(k, v) }
            put("cpu_governors", cpu)
            val cpuMin = JSONObject()
            snapshot.cpuMinFreqs.forEach { (k, v) -> cpuMin.put(k, v) }
            put("cpu_min_freqs", cpuMin)
            val cpuMax = JSONObject()
            snapshot.cpuMaxFreqs.forEach { (k, v) -> cpuMax.put(k, v) }
            put("cpu_max_freqs", cpuMax)
            put("vendor_refresh_namespace", snapshot.vendorRefreshNamespace)
            put("vendor_refresh_key", snapshot.vendorRefreshKey)
            put("vendor_refresh_value", snapshot.vendorRefreshValue)
            put("vendor_refresh_alt_key", snapshot.vendorRefreshAltKey)
            put("vendor_refresh_alt_value", snapshot.vendorRefreshAltValue)
        }
        write(json)
    }

    @Synchronized
    fun read(): Snapshot? = runCatching {
        if (!file.exists()) return@runCatching null
        val j = JSONObject(file.readText())
        if (!j.optBoolean("active", false)) return@runCatching null
        val cpuObj = j.optJSONObject("cpu_governors")
        val cpu = linkedMapOf<String, String>()
        cpuObj?.keys()?.forEach { key -> cpu[key] = cpuObj.optString(key) }
        val cpuMin = linkedMapOf<String, String>()
        j.optJSONObject("cpu_min_freqs")?.keys()?.forEach { key -> cpuMin[key] = j.optJSONObject("cpu_min_freqs")?.optString(key).orEmpty() }
        val cpuMax = linkedMapOf<String, String>()
        j.optJSONObject("cpu_max_freqs")?.keys()?.forEach { key -> cpuMax[key] = j.optJSONObject("cpu_max_freqs")?.optString(key).orEmpty() }
        Snapshot(
            packageName = j.optString("package"),
            bootId = j.optString("boot_id"),
            pid = j.optInt("pid"),
            gpuNode = j.optString("gpu_node"),
            gpuGovernor = j.optString("gpu_governor"),
            gpuMin = j.optString("gpu_min_freq"),
            gpuMax = j.optString("gpu_max_freq"),
            cpuGovernors = cpu,
            cpuMinFreqs = cpuMin,
            cpuMaxFreqs = cpuMax,
            zenMode = j.optString("zen_mode"),
            peakRefresh = j.optString("peak_refresh"),
            minRefresh = j.optString("min_refresh"),
            vendorRefreshNamespace = j.optString("vendor_refresh_namespace"),
            vendorRefreshKey = j.optString("vendor_refresh_key"),
            vendorRefreshValue = j.optString("vendor_refresh_value"),
            vendorRefreshAltKey = j.optString("vendor_refresh_alt_key"),
            vendorRefreshAltValue = j.optString("vendor_refresh_alt_value"),
            thermalProfile = j.optString("thermal_profile"),
            hwUiProp = j.optString("hw_ui_prop"),
            disableHwProp = j.optString("disable_hw_prop"),
            hapticEnabled = j.optString("haptic_enabled"),
        )
    }.getOrNull()

    @Synchronized
    fun restore(state: Snapshot): Boolean {
        var ok = true
        // Restore limits as a pair. If the target minimum is above the live
        // maximum, raise the maximum first; otherwise a kernel may reject the
        // minimum write. Then restore the final maximum.
        val cpuPolicyPaths = (state.cpuMinFreqs.keys + state.cpuMaxFreqs.keys).distinct()
        cpuPolicyPaths.forEach { path ->
            val targetMin = state.cpuMinFreqs[path]?.toLongOrNull()
            val targetMax = state.cpuMaxFreqs[path]?.toLongOrNull()
            val liveMax = RootFileAccess.read("$path/scaling_max_freq")?.trim()?.toLongOrNull()
            if (targetMin != null && liveMax != null && targetMin > liveMax) {
                if (targetMax != null && targetMax >= targetMin) {
                    ok = RootFileAccess.write("$path/scaling_max_freq", targetMax.toString()) && ok
                } else {
                    ok = false
                }
            }
            if (targetMin != null) ok = RootFileAccess.write("$path/scaling_min_freq", targetMin.toString()) && ok
            if (targetMax != null) ok = RootFileAccess.write("$path/scaling_max_freq", targetMax.toString()) && ok
        }
        state.cpuGovernors.forEach { (path, value) ->
            if (value.isNotBlank()) ok = RootFileAccess.write("$path/scaling_governor", value) && ok
        }
        if (state.gpuNode.isNotBlank()) {
            if (state.gpuMin.isNotBlank()) ok = RootFileAccess.write("${state.gpuNode}/min_freq", state.gpuMin) && ok
            if (state.gpuMax.isNotBlank()) ok = RootFileAccess.write("${state.gpuNode}/max_freq", state.gpuMax) && ok
            if (state.gpuGovernor.isNotBlank()) ok = RootFileAccess.write("${state.gpuNode}/governor", state.gpuGovernor) && ok
        }
        // Best-effort restoration of non-sysfs knobs captured by AppMonitor.
        // These are deliberately allow-listed; the recovery journal must never
        // turn into an arbitrary root command source.
        if (state.zenMode.isNotBlank()) ok = putSetting("global", "zen_mode", state.zenMode) && ok
        if (state.peakRefresh.isNotBlank()) ok = putSetting("system", "peak_refresh_rate", state.peakRefresh) && ok
        if (state.minRefresh.isNotBlank()) ok = putSetting("system", "min_refresh_rate", state.minRefresh) && ok
        if (state.hapticEnabled.isNotBlank()) ok = putSetting("system", "haptic_feedback_enabled", state.hapticEnabled) && ok
        if (state.thermalProfile.isNotBlank()) ok = setProp("sys.thermal.profile", state.thermalProfile) && ok
        if (state.hwUiProp.isNotBlank()) ok = setProp("persist.sys.ui.hw", state.hwUiProp) && ok
        if (state.disableHwProp.isNotBlank()) ok = setProp("debug.viewroot.disableHW", state.disableHwProp) && ok
        return ok
    }

    private fun putSetting(namespace: String, key: String, value: String): Boolean {
        if (namespace !in setOf("system", "secure", "global")) return false
        return RootFileAccess.exec("settings put $namespace ${quote(key)} ${quote(value)}") == 0
    }

    private fun setProp(key: String, value: String): Boolean =
        RootFileAccess.exec("setprop ${quote(key)} ${quote(value)}") == 0

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    @Synchronized
    fun clear() { runCatching { file.delete() } }

    fun bootId(): String = runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("")

    private fun write(value: JSONObject) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.parentFile?.mkdirs()
        tmp.outputStream().use { out ->
            out.write(value.toString().toByteArray(StandardCharsets.UTF_8))
            out.flush()
            runCatching { out.fd.sync() }
        }
        if (!tmp.renameTo(file)) throw IllegalStateException("failed to replace ${file.absolutePath}")
    }
}
