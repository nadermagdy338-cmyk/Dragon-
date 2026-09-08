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

private const val ADRENO_HISTORY_CAPACITY = 75

/**
 * Preset definition, structurally identical to MaliPreset. minFraction/
 * maxFraction are resolved against the device's own available_frequencies
 * list, never hardcoded, so this works across Adreno generations.
 */
data class AdrenoPreset(
    val id: String,
    val labelRes: String,
    val descRes: String,
    val minFraction: Float, // 0f = lowest available freq
    val maxFraction: Float  // 1f = highest available freq
)

/**
 * Qualcomm Adreno counterpart to MaliFreqViewModel.
 *
 * Not a straight port of ZKM's AdrenoUtils: ZKM drives the legacy
 * /sys/class/kgsl/kgsl-3d0 nodes (min_clock_mhz / max_clock_mhz / gpuclk)
 * directly, and their units are inconsistent across OEM kernel forks (some
 * report Hz, some already-scaled MHz despite the node name). KGSL also
 * exposes a standard devfreq node at kgsl-3d0/devfreq/ - the same generic
 * devfreq API Mali's kbase driver uses, with the same
 * min_freq/max_freq/cur_freq/available_frequencies/load/governor/
 * available_governors set - so this ViewModel targets that instead. It keeps
 * unit handling and polling identical to MaliFreqViewModel rather than
 * re-deriving a second, inconsistent code path for a second chipset family.
 */
class AdrenoGpuViewModel : ViewModel() {

    companion object {
        private const val KGSL_DIR = "/sys/class/kgsl/kgsl-3d0"
        private const val DEVFREQ_DIR = "$KGSL_DIR/devfreq"

        // Most Qualcomm kernels expose a single documented on/off throttling
        // switch here, separate from the generic thermal cooling-device
        // framework Mali relies on.
        private const val THROTTLE_NODE = "$KGSL_DIR/throttling"

        private const val PROP_MIN = MaxManagerProps.Adreno.MINFREQ
        private const val PROP_MAX = MaxManagerProps.Adreno.MAXFREQ
        private const val PROP_PRESET = MaxManagerProps.Adreno.PRESET
        private const val PROP_GOVERNOR = MaxManagerProps.Adreno.GOVERNOR
        private const val PROP_THROTTLE_BYPASS = MaxManagerProps.Adreno.THROTTLE_BYPASS

        val PRESETS = listOf(
            AdrenoPreset("battery_saver", "adreno_preset_battery", "adreno_preset_battery_desc", 0.0f, 0.45f),
            AdrenoPreset("balanced", "adreno_preset_balanced", "adreno_preset_balanced_desc", 0.0f, 1.0f),
            AdrenoPreset("gaming_dynamic", "adreno_preset_gaming", "adreno_preset_gaming_desc", 0.65f, 1.0f),
            AdrenoPreset("max_performance", "adreno_preset_max", "adreno_preset_max_desc", 1.0f, 1.0f)
        )
    }

    var isAvailable by mutableStateOf<Boolean?>(null)
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

    var availableGovernors by mutableStateOf<List<String>>(emptyList())
        private set
    var currentGovernor by mutableStateOf<String?>(null)
        private set

    // Direct kgsl throttling toggle when present. Falls back to the same
    // generic thermal cooling-device search MaliFreqViewModel uses when a
    // kernel doesn't expose it.
    var hasDirectThrottleNode by mutableStateOf(false)
        private set
    var throttleCoolingDevicePath by mutableStateOf<String?>(null)
        private set
    var throttleBypassEnabled by mutableStateOf(false)
        private set
    var thermalClampState by mutableStateOf<Int?>(null)
        private set

    // Rolling telemetry window feeding the Live Activity graph on the
    // screen, same convention as Mali: ~2.5 minutes of history at the
    // current 2s poll interval.
    var freqHistory by mutableStateOf<List<Float>>(emptyList())
        private set
    var loadHistory by mutableStateOf<List<Float>>(emptyList())
        private set

    private fun pushHistory(freqFraction: Float, loadFraction: Float) {
        freqHistory = (freqHistory + freqFraction.coerceIn(0f, 1f)).takeLast(ADRENO_HISTORY_CAPACITY)
        loadHistory = (loadHistory + loadFraction.coerceIn(0f, 1f)).takeLast(ADRENO_HISTORY_CAPACITY)
    }

    private var pollJob: kotlinx.coroutines.Job? = null

    fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            val exists = Shell.cmd("test -d $DEVFREQ_DIR && echo 1 || echo 0")
                .exec().out.joinToString("").trim() == "1"

            if (!exists) {
                isAvailable = false
                return@launch
            }

            val freqsRaw = Shell.cmd("cat $DEVFREQ_DIR/available_frequencies 2>/dev/null")
                .exec().out.joinToString(" ")
            val freqs = freqsRaw.trim().split(Regex("\\s+"))
                .mapNotNull { it.toLongOrNull() }
                .map { if (it >= 10_000_000L) it / 1000L else it }
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

            minFreqKhz = savedMin?.let { if (it >= 10_000_000L) it / 1000L else it }
                ?.coerceIn(freqs.first(), freqs.last()) ?: freqs.first()
            maxFreqKhz = savedMax?.let { if (it >= 10_000_000L) it / 1000L else it }
                ?.coerceIn(freqs.first(), freqs.last()) ?: freqs.last()
            if (minFreqKhz > maxFreqKhz) minFreqKhz = freqs.first()

            detectGovernors()
            if (availableGovernors.isNotEmpty()) {
                val savedGovernor = PropertyUtils.get(PROP_GOVERNOR)
                if (savedGovernor.isNotEmpty() && savedGovernor in availableGovernors) {
                    applyGovernorInternal(savedGovernor)
                } else {
                    currentGovernor = readCurrentGovernor()
                }
            }

            detectThrottleControl()
            throttleBypassEnabled = PropertyUtils.get(PROP_THROTTLE_BYPASS) == "1"
            if (throttleBypassEnabled) applyThrottleBypassInternal(true)
            refreshThermalClamp()

            startPolling()
        }
    }

    private fun detectGovernors() {
        val raw = Shell.cmd("cat $DEVFREQ_DIR/available_governors 2>/dev/null").exec()
            .out.joinToString(" ")
        availableGovernors = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private fun readCurrentGovernor(): String? {
        val raw = Shell.cmd("cat $DEVFREQ_DIR/governor 2>/dev/null").exec().out.joinToString("").trim()
        return raw.ifEmpty { null }
    }

    private fun applyGovernorInternal(governor: String) {
        Shell.cmd("echo $governor > $DEVFREQ_DIR/governor 2>/dev/null").exec()
        currentGovernor = governor
    }

    fun setGovernor(governor: String) {
        viewModelScope.launch(Dispatchers.IO) {
            applyGovernorInternal(governor)
            PropertyUtils.set(PROP_GOVERNOR, governor)
        }
    }

    /**
     * Prefers the direct kgsl throttling toggle (present on most Qualcomm
     * kernels) since it's a single documented switch. Falls back to hunting
     * for a GPU-bound thermal cooling device - the same probe
     * MaliFreqViewModel uses - for kernels that don't expose it.
     */
    private fun detectThrottleControl() {
        hasDirectThrottleNode = Shell.cmd("test -e $THROTTLE_NODE && echo 1 || echo 0")
            .exec().out.joinToString("").trim() == "1"

        if (!hasDirectThrottleNode) {
            val matches = Shell.cmd(
                "for d in /sys/class/thermal/cooling_device*; do " +
                    "t=\$(cat \"\$d/type\" 2>/dev/null); " +
                    "echo \"\$d|\$t\"; " +
                    "done"
            ).exec().out

            throttleCoolingDevicePath = matches.firstOrNull { line ->
                val type = line.substringAfter('|', "").lowercase()
                type.contains("gpu") || type.contains("kgsl") || type.contains("adreno")
            }?.substringBefore('|')
        }
    }

    private fun refreshThermalClamp() {
        val path = throttleCoolingDevicePath ?: return
        thermalClampState = Shell.cmd("cat \"$path/cur_state\" 2>/dev/null")
            .exec().out.joinToString("").trim().toIntOrNull()
    }

    /**
     * WARNING: this removes the device's own GPU overheat protection - via
     * the direct throttling switch when present, otherwise by forcing the
     * matched thermal cooling device to its unthrottled state and locking it
     * read-only, exactly as MaliFreqViewModel does. Sustained heavy load
     * with this enabled can push the SoC well past its normal operating
     * temperature: thermal shutdowns, accelerated battery wear, visible
     * skin-temperature discomfort, and in the worst case permanent hardware
     * damage are all possible. Applied only on explicit user confirmation
     * and reversible at any time via disableThrottleBypass().
     */
    private fun applyThrottleBypassInternal(enabled: Boolean) {
        if (hasDirectThrottleNode) {
            Shell.cmd(
                "echo ${if (enabled) 0 else 1} > $THROTTLE_NODE 2>/dev/null",
                if (enabled) "chmod 0444 $THROTTLE_NODE 2>/dev/null" else "chmod 0644 $THROTTLE_NODE 2>/dev/null"
            ).exec()
            return
        }

        val path = throttleCoolingDevicePath ?: return
        if (enabled) {
            Shell.cmd(
                "echo 0 > \"$path/cur_state\" 2>/dev/null",
                "chmod 0444 \"$path/cur_state\" 2>/dev/null"
            ).exec()
        } else {
            Shell.cmd(
                "chmod 0644 \"$path/cur_state\" 2>/dev/null",
                "cat \"$path/max_state\" > \"$path/cur_state\" 2>/dev/null"
            ).exec()
        }
    }

    fun enableThrottleBypass() {
        if (!hasDirectThrottleNode && throttleCoolingDevicePath == null) return
        viewModelScope.launch(Dispatchers.IO) {
            applyThrottleBypassInternal(true)
            throttleBypassEnabled = true
            PropertyUtils.set(PROP_THROTTLE_BYPASS, "1")
        }
    }

    fun disableThrottleBypass() {
        if (!hasDirectThrottleNode && throttleCoolingDevicePath == null) return
        viewModelScope.launch(Dispatchers.IO) {
            applyThrottleBypassInternal(false)
            throttleBypassEnabled = false
            PropertyUtils.set(PROP_THROTTLE_BYPASS, "0")
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val cur = Shell.cmd("cat $DEVFREQ_DIR/cur_freq 2>/dev/null")
                    .exec().out.joinToString("").trim().toLongOrNull()
                    ?.let { if (it >= 10_000_000L) it / 1000L else it }
                val load = Shell.cmd("cat $DEVFREQ_DIR/load 2>/dev/null")
                    .exec().out.joinToString("").trim()
                    .substringBefore('@').trim().toIntOrNull()

                // Only the cooling-device fallback has a numeric clamp state
                // to poll; the direct throttling node is a plain on/off
                // switch already reflected by throttleBypassEnabled.
                val clampState = if (!hasDirectThrottleNode) {
                    throttleCoolingDevicePath?.let { path ->
                        Shell.cmd("cat \"$path/cur_state\" 2>/dev/null").exec().out.joinToString("").trim().toIntOrNull()
                    }
                } else null

                withContext(Dispatchers.Main) {
                    cur?.let { curFreqKhz = it }
                    load?.let { loadPercent = it }
                    if (!hasDirectThrottleNode) thermalClampState = clampState

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

    fun applyPreset(preset: AdrenoPreset) {
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
            // Write max first when raising, min first when lowering, to
            // always keep min <= max valid for the kernel's devfreq bounds
            // check.
            Shell.cmd(
                "echo $minKhz > $DEVFREQ_DIR/min_freq 2>/dev/null",
                "echo $maxKhz > $DEVFREQ_DIR/max_freq 2>/dev/null",
                "echo $minKhz > $DEVFREQ_DIR/min_freq 2>/dev/null"
            ).exec()
            PropertyUtils.set(PROP_MIN, minKhz.toString())
            PropertyUtils.set(PROP_MAX, maxKhz.toString())
        }
    }
}
