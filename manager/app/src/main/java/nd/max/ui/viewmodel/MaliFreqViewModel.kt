/*
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

package nd.max.ui.viewmodel

import nd.max.MaxManagerProps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.ui.util.PropertyUtils

private const val MALI_HISTORY_CAPACITY = 75

/**
 * Preset definition. minMhz/maxMhz are resolved against the device's own
 * available_frequencies list, never hardcoded, so this works across chipsets.
 */
data class MaliPreset(
    val id: String,
    val labelRes: String,
    val descRes: String,
    val minFraction: Float, // 0f = lowest available freq
    val maxFraction: Float  // 1f = highest available freq
)

class MaliFreqViewModel : ViewModel() {

    companion object {
        private val DEVFREQ_CANDIDATES = listOf(
            "/sys/class/devfreq/13000000.mali",
            "/sys/class/misc/mali0/device/devfreq/13000000.mali",
            "/sys/devices/platform/soc/13000000.mali/devfreq/13000000.mali",
            "/sys/devices/platform/13000000.mali/devfreq/13000000.mali"
        )
        private const val PROP_MIN = MaxManagerProps.Mali.MINFREQ
        private const val PROP_MAX = MaxManagerProps.Mali.MAXFREQ
        private const val PROP_PRESET = MaxManagerProps.Mali.PRESET
        private const val PROP_THROTTLE_BYPASS = MaxManagerProps.Mali.THROTTLE_BYPASS
        private const val PROP_GED_BOOST = MaxManagerProps.Mali.GED_BOOST
        private const val PROP_POWER_POLICY = MaxManagerProps.Mali.POWER_POLICY

        // Best-effort MediaTek GED (Graphics Engine Driver) instant-boost nodes seen
        // across custom MTK kernels. Probed the same way the GPU cooling device is
        // (test for existence first) instead of assuming one is present.
        private val GED_BOOST_CANDIDATES = listOf(
            "/proc/ged/ged_kpi_boost",
            "/sys/kernel/ged/hal/ged_kpi_boost",
            "/proc/ged/boost_gpu_enable"
        )

        // Real Mali CSF (Command Stream Frontend / Valhall) kbase driver sysfs
        // attribute (power_policy / available_power_policies under the kbase
        // misc device). The misc device index is usually mali0 but isn't
        // guaranteed, so both common indices are probed.
        private val POWER_POLICY_DIR_CANDIDATES = listOf(
            "/sys/class/misc/mali0/device",
            "/sys/class/misc/mali1/device"
        )

        val PRESETS = listOf(
            MaliPreset("battery_saver", "mali_preset_battery", "mali_preset_battery_desc", 0.0f, 0.45f),
            MaliPreset("balanced", "mali_preset_balanced", "mali_preset_balanced_desc", 0.0f, 1.0f),
            MaliPreset("gaming_dynamic", "mali_preset_gaming", "mali_preset_gaming_desc", 0.65f, 1.0f),
            MaliPreset("max_performance", "mali_preset_max", "mali_preset_max_desc", 1.0f, 1.0f)
        )
    }

    var isAvailable by mutableStateOf<Boolean?>(null)
        private set
    var gpuName by mutableStateOf("Mali GPU")
        private set
    var availableFrequenciesKhz by mutableStateOf<List<Long>>(emptyList())
        private set
    var minFreqKhz by mutableStateOf(0L)
        private set
    var maxFreqKhz by mutableStateOf(0L)
        private set
    var curFreqKhz by mutableStateOf(0L)
        private set
    var loadPercent by mutableStateOf(0)
        private set
    var selectedPresetId by mutableStateOf("balanced")
        private set
    var throttleBypassEnabled by mutableStateOf(false)
        private set
    var throttleCoolingDevicePath by mutableStateOf<String?>(null)
        private set
    var thermalClampState by mutableStateOf<Int?>(null)
        private set

    var gedBoostNode by mutableStateOf<String?>(null)
        private set
    var gedBoostEnabled by mutableStateOf(false)
        private set

    var powerPolicyDir by mutableStateOf<String?>(null)
        private set
    var availablePowerPolicies by mutableStateOf<List<String>>(emptyList())
        private set
    var currentPowerPolicy by mutableStateOf<String?>(null)
        private set

    /** Raw thermal cooling device state (null = unknown/unavailable, 0 = unthrottled). Screen layer maps this to a localized label. */

    // Rolling telemetry window feeding the Live Activity graph on the screen.
    // Each entry is 0f..1f: frequency normalized against the hardware max,
    // load taken directly from devfreq's own percentage. ~2.5 minutes of
    // history at the current 2s poll interval.
    var freqHistory by mutableStateOf<List<Float>>(emptyList())
        private set
    var loadHistory by mutableStateOf<List<Float>>(emptyList())
        private set

    private fun pushHistory(freqFraction: Float, loadFraction: Float) {
        freqHistory = (freqHistory + freqFraction.coerceIn(0f, 1f)).takeLast(MALI_HISTORY_CAPACITY)
        loadHistory = (loadHistory + loadFraction.coerceIn(0f, 1f)).takeLast(MALI_HISTORY_CAPACITY)
    }

    private var pollJob: kotlinx.coroutines.Job? = null

    fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            val devfreq = findDevfreqNode()
            if (devfreq == null) {
                isAvailable = false
                return@launch
            }

            val freqsRaw = Shell.cmd("cat '$devfreq/available_frequencies' 2>/dev/null")
                .exec().out.joinToString(" ")
            val freqs = freqsRaw.trim().split(Regex("\\s+"))
                .mapNotNull { it.toLongOrNull() }
                .map(::rawFreqToKhz)
                .filter { it > 0 }
                .sorted()

            if (freqs.isEmpty()) {
                isAvailable = false
                return@launch
            }

            availableFrequenciesKhz = freqs
            isAvailable = true

            val savedMin = PropertyUtils.get(PROP_MIN).toLongOrNull()
            val savedMax = PropertyUtils.get(PROP_MAX).toLongOrNull()
            selectedPresetId = PropertyUtils.get(PROP_PRESET).ifEmpty { "balanced" }

            // Older MaxManager builds persisted the raw devfreq unit (usually Hz).
            // Normalize persisted values as well as live values so an upgrade does
            // not turn 1300000000 into an impossible 1.3e9 kHz setting.
            minFreqKhz = savedMin?.let(::rawFreqToKhz)?.coerceIn(freqs.first(), freqs.last()) ?: freqs.first()
            maxFreqKhz = savedMax?.let(::rawFreqToKhz)?.coerceIn(freqs.first(), freqs.last()) ?: freqs.last()
            if (minFreqKhz > maxFreqKhz) minFreqKhz = freqs.first()

            detectGpuCoolingDevice()
            throttleBypassEnabled = PropertyUtils.get(PROP_THROTTLE_BYPASS) == "1"
            refreshThermalClamp()

            detectGedBoostNode()
            if (gedBoostNode != null) {
                gedBoostEnabled = PropertyUtils.get(PROP_GED_BOOST) == "1"
                if (gedBoostEnabled) applyGedBoostInternal(true)
            }

            detectPowerPolicy()
            if (powerPolicyDir != null) {
                val savedPolicy = PropertyUtils.get(PROP_POWER_POLICY)
                if (savedPolicy.isNotEmpty() && savedPolicy in availablePowerPolicies) {
                    applyPowerPolicyInternal(savedPolicy)
                } else {
                    currentPowerPolicy = readCurrentPowerPolicy()
                }
            }

            startPolling()
        }
    }

    private fun refreshThermalClamp() {
        val path = throttleCoolingDevicePath ?: return
        thermalClampState = Shell.cmd("cat \"$path/cur_state\" 2>/dev/null")
            .exec().out.joinToString("").trim().toIntOrNull()
    }

    /**
     * Probes known MediaTek GED "instant boost" nodes and picks the first that
     * actually exists on this kernel, mirroring detectGpuCoolingDevice()'s
     * probe-then-use approach instead of assuming a fixed vendor layout.
     */
    private fun detectGedBoostNode() {
        gedBoostNode = GED_BOOST_CANDIDATES.firstOrNull { path ->
            Shell.cmd("test -e $path && echo 1 || echo 0").exec().out.joinToString("").trim() == "1"
        }
    }

    private fun applyGedBoostInternal(enabled: Boolean) {
        val node = gedBoostNode ?: return
        Shell.cmd("echo ${if (enabled) 1 else 0} > $node 2>/dev/null").exec()
    }

    fun setGedBoost(enabled: Boolean) {
        gedBoostEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            applyGedBoostInternal(enabled)
            PropertyUtils.set(PROP_GED_BOOST, if (enabled) "1" else "0")
        }
    }

    /** Finds the kbase CSF misc device and reads its real supported power policy list — never a hardcoded pair of options. */
    private fun detectPowerPolicy() {
        val dir = POWER_POLICY_DIR_CANDIDATES.firstOrNull { d ->
            Shell.cmd("test -f $d/power_policy && echo 1 || echo 0").exec().out.joinToString("").trim() == "1"
        }
        powerPolicyDir = dir
        if (dir != null) {
            val raw = Shell.cmd("cat $dir/available_power_policies 2>/dev/null").exec()
                .out.joinToString(" ")
            availablePowerPolicies = raw
                .replace("[", "").replace("]", "")
                .trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        }
    }

    private fun readCurrentPowerPolicy(): String? {
        val dir = powerPolicyDir ?: return null
        val raw = Shell.cmd("cat $dir/power_policy 2>/dev/null").exec().out.joinToString("")
        return Regex("\\[(.*?)]").find(raw)?.groupValues?.get(1) ?: raw.trim().ifEmpty { null }
    }

    private fun applyPowerPolicyInternal(policy: String) {
        val dir = powerPolicyDir ?: return
        Shell.cmd("echo $policy > $dir/power_policy 2>/dev/null").exec()
        currentPowerPolicy = policy
    }

    fun setPowerPolicy(policy: String) {
        viewModelScope.launch(Dispatchers.IO) {
            applyPowerPolicyInternal(policy)
            PropertyUtils.set(PROP_POWER_POLICY, policy)
        }
    }

    /**
     * Finds the thermal cooling device tied to the GPU by matching type/binding,
     * instead of hardcoding a fixed cooling_deviceN index (which varies per device).
     */
    private fun detectGpuCoolingDevice() {
        val matches = Shell.cmd(
            "for d in /sys/class/thermal/cooling_device*; do " +
                "t=\$(cat \"\$d/type\" 2>/dev/null); " +
                "echo \"\$d|\$t\"; " +
                "done"
        ).exec().out

        val gpuDevice = matches.firstOrNull { line ->
            val type = line.substringAfter('|', "").lowercase()
            type.contains("gpu") || type.contains("mali")
        }?.substringBefore('|')

        throttleCoolingDevicePath = gpuDevice
    }

    /**
     * Forces the GPU thermal cooling device to its unthrottled state and locks the
     * node read-only so the kernel's thermal governor cannot re-throttle it.
     *
     * WARNING: this removes the device's own overheat protection for the GPU.
     * Sustained heavy load with this enabled can push the SoC well past its normal
     * operating temperature, which can cause thermal shutdowns, accelerated battery
     * wear, visible performance/skin-temperature discomfort, and in the worst case
     * permanent hardware damage. This is applied only on explicit user confirmation
     * and can be reverted at any time with disableThrottleBypass().
     */
    fun enableThrottleBypass() {
        val path = throttleCoolingDevicePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd(
                "echo 0 > \"$path/cur_state\" 2>/dev/null",
                "chmod 0444 \"$path/cur_state\" 2>/dev/null"
            ).exec()
            throttleBypassEnabled = true
            PropertyUtils.set(PROP_THROTTLE_BYPASS, "1")
        }
    }

    fun disableThrottleBypass() {
        val path = throttleCoolingDevicePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd(
                "chmod 0644 \"$path/cur_state\" 2>/dev/null",
                "cat \"$path/max_state\" > \"$path/cur_state\" 2>/dev/null"
            ).exec()
            throttleBypassEnabled = false
            PropertyUtils.set(PROP_THROTTLE_BYPASS, "0")
        }
    }

    private fun findDevfreqNode(): String? = DEVFREQ_CANDIDATES.firstOrNull { path ->
        Shell.cmd("test -d '$path' && test -f '$path/available_frequencies' && echo 1 || echo 0")
            .exec().out.joinToString("").trim() == "1"
    }

    private fun rawFreqToKhz(raw: Long): Long = if (raw >= 10_000_000L) raw / 1000L else raw
    private fun khzToRawFreq(khz: Long): Long = if (khz < 10_000_000L) khz * 1000L else khz

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val devfreq = findDevfreqNode()
                val cur = devfreq?.let { Shell.cmd("cat '$it/cur_freq' 2>/dev/null")
                    .exec().out.joinToString("").trim().toLongOrNull()?.let(::rawFreqToKhz) }
                val load = devfreq?.let { Shell.cmd("cat '$it/load' 2>/dev/null")
                    .exec().out.joinToString("").trim()
                    .substringBefore('@').trim().toIntOrNull() }

                val clampState = throttleCoolingDevicePath?.let { path ->
                    Shell.cmd("cat \"$path/cur_state\" 2>/dev/null").exec().out.joinToString("").trim().toIntOrNull()
                }

                withContext(Dispatchers.Main) {
                    cur?.let { curFreqKhz = it }
                    load?.let { loadPercent = it }
                    thermalClampState = clampState

                    val hwMax = availableFrequenciesKhz.lastOrNull()?.toFloat()?.coerceAtLeast(1f)
                    if (hwMax != null) {
                        pushHistory(
                            freqFraction = curFreqKhz.toFloat() / hwMax,
                            loadFraction = loadPercent / 100f
                        )
                    }
                }
                delay(2000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    fun applyPreset(preset: MaliPreset) {
        val freqs = availableFrequenciesKhz
        if (freqs.isEmpty()) return

        val lastIndex = freqs.lastIndex
        val newMin = freqs[(preset.minFraction * lastIndex).toInt().coerceIn(0, lastIndex)]
        val newMax = freqs[(preset.maxFraction * lastIndex).toInt().coerceIn(0, lastIndex)]

        selectedPresetId = preset.id
        applyFrequencies(newMin, newMax.coerceAtLeast(newMin))
        PropertyUtils.set(PROP_PRESET, preset.id)
    }

    fun applyCustomMax(freqKhz: Long) {
        selectedPresetId = "custom"
        applyFrequencies(minFreqKhz.coerceAtMost(freqKhz), freqKhz)
        PropertyUtils.set(PROP_PRESET, "custom")
    }

    fun applyCustomMin(freqKhz: Long) {
        selectedPresetId = "custom"
        applyFrequencies(freqKhz, maxFreqKhz.coerceAtLeast(freqKhz))
        PropertyUtils.set(PROP_PRESET, "custom")
    }

    private fun applyFrequencies(minKhz: Long, maxKhz: Long) {
        minFreqKhz = minKhz
        maxFreqKhz = maxKhz
        viewModelScope.launch(Dispatchers.IO) {
            val devfreq = findDevfreqNode() ?: return@launch
            val minRaw = khzToRawFreq(minKhz)
            val maxRaw = khzToRawFreq(maxKhz)
            // Keep the kernel's min <= max invariant during transitions.
            val currentMin = Shell.cmd("cat '$devfreq/min_freq' 2>/dev/null").exec().out.joinToString("").trim().toLongOrNull()
                ?.let(::rawFreqToKhz) ?: minKhz
            val currentMax = Shell.cmd("cat '$devfreq/max_freq' 2>/dev/null").exec().out.joinToString("").trim().toLongOrNull()
                ?.let(::rawFreqToKhz) ?: maxKhz
            if (minKhz > currentMax) Shell.cmd("echo $maxRaw > '$devfreq/max_freq' 2>/dev/null").exec()
            if (maxKhz < currentMin) Shell.cmd("echo $minRaw > '$devfreq/min_freq' 2>/dev/null").exec()
            if (minKhz <= currentMax && maxKhz >= currentMin) {
                Shell.cmd("echo $minRaw > '$devfreq/min_freq' 2>/dev/null").exec()
                Shell.cmd("echo $maxRaw > '$devfreq/max_freq' 2>/dev/null").exec()
            } else {
                Shell.cmd("echo $minRaw > '$devfreq/min_freq' 2>/dev/null").exec()
                Shell.cmd("echo $maxRaw > '$devfreq/max_freq' 2>/dev/null").exec()
            }
            PropertyUtils.set(PROP_MIN, minKhz.toString())
            PropertyUtils.set(PROP_MAX, maxKhz.toString())
        }
    }
}
