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

class ChargingViewModel : ViewModel() {

    companion object {
        // power_supply node names vary (battery/bms/main), so we probe a few.
        private val BATTERY_DIRS = listOf(
            "/sys/class/power_supply/battery",
            "/sys/class/power_supply/bms"
        )
        private val FAST_CHARGE_CURRENT_CANDIDATES = listOf(
            "constant_charge_current_max",
            "fast_charge_current_max",
            "input_current_limit"
        )
        // Xiaomi's silicon-carbon battery boost charging node ("sic_mode"), seen
        // exposed under a few different power_supply/proc locations depending on
        // the platform (Qualcomm vs MediaTek) and MIUI/HyperOS version.
        private val SIC_MODE_CANDIDATES = listOf(
            "/sys/class/power_supply/battery/sic_mode",
            "/sys/class/power_supply/bms/sic_mode",
            "/sys/class/qcom-battery/sic_mode",
            "/proc/mtk_battery_cmd/sic_mode"
        )
        private const val SIC_MODE_BOOST_VALUE = "8"
        private const val SIC_MODE_OFF_VALUE = "0"
        private const val PROP_FASTCHG_MA = MaxManagerProps.Charging.FASTCHARGE_MA
        private const val PROP_SIC_BOOST = MaxManagerProps.Mali.SIC_BOOST
        private const val PROP_CHARGE_LIMIT = MaxManagerProps.Charging.CHARGE_LIMIT_PERCENT
        // Most common node for MIUI/HyperOS and several AOSP-derived kernels; a
        // percentage 0-100 written here stops charging once capacity reaches it.
        private const val CHARGE_LIMIT_NODE_NAME = "charge_control_limit"
        private const val CHARGE_LIMIT_DEFAULT_ON_PERCENT = 80
        private const val CHARGE_LIMIT_DISABLED_PERCENT = 100
    }

    var isCharging by mutableStateOf(false)
        private set
    var capacityPercent by mutableStateOf(0)
        private set
    var voltageMv by mutableStateOf(0)
        private set
    /** Current into the battery in mA. Positive always means charging. */
    var currentMa by mutableStateOf(0)
        private set
    var temperatureC by mutableStateOf(0f)
        private set
    var chargerType by mutableStateOf("Unknown")
        private set
    var usbConnected by mutableStateOf(false)
        private set

    var sicModeNodePath by mutableStateOf<String?>(null)
        private set
    var sicBoostEnabled by mutableStateOf(false)
        private set
    var sicModeCurrentValue by mutableStateOf<String?>(null)
        private set

    var fastChargeAvailable by mutableStateOf(false)
        private set
    var fastChargeNodePath by mutableStateOf<String?>(null)
        private set
    var fastChargeMaxMa by mutableStateOf(0)
        private set
    var fastChargeCurrentMa by mutableStateOf(0)
        private set

    /** Wear level as a percentage of design capacity (charge_full / charge_full_design). Null if unreadable. */
    var batteryHealthPercent by mutableStateOf<Int?>(null)
        private set
    var cycleCount by mutableStateOf<Int?>(null)
        private set
    var batteryTechnology by mutableStateOf("-")
        private set

    var chargeLimitSupported by mutableStateOf(false)
        private set
    var chargeLimitNodePath by mutableStateOf<String?>(null)
        private set
    /** 100 means "no limit" (charge to full); anything below is the enabled cutoff. */
    var chargeLimitPercent by mutableStateOf(CHARGE_LIMIT_DISABLED_PERCENT)
        private set

    var batterySaverEnabled by mutableStateOf(false)
        private set

    private var batteryDir: String? = null
    private var pollJob: kotlinx.coroutines.Job? = null

    fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            batteryDir = BATTERY_DIRS.firstOrNull {
                Shell.cmd("test -d $it && echo 1 || echo 0").exec().out.joinToString("").trim() == "1"
            }

            val dir = batteryDir
            if (dir != null) {
                for (name in FAST_CHARGE_CURRENT_CANDIDATES) {
                    val path = "$dir/$name"
                    if (Shell.cmd("test -e $path && echo 1 || echo 0").exec().out.joinToString("").trim() == "1") {
                        fastChargeNodePath = path
                        break
                    }
                }
                fastChargeNodePath?.let { path ->
                    val maxPath = "${path}_max" // some drivers expose a *_max sibling; harmless if absent
                    fastChargeMaxMa = (Shell.cmd("cat $maxPath 2>/dev/null").exec().out.joinToString("").trim()
                        .toLongOrNull()?.div(1000))?.toInt() ?: 4000
                    fastChargeAvailable = true
                }

                val limitPath = "$dir/$CHARGE_LIMIT_NODE_NAME"
                if (Shell.cmd("test -e $limitPath && echo 1 || echo 0").exec().out.joinToString("").trim() == "1") {
                    chargeLimitNodePath = limitPath
                    chargeLimitSupported = true
                }

                loadStaticBatteryInfo(dir)
            }

            val savedMa = PropertyUtils.get(PROP_FASTCHG_MA).toIntOrNull()
            savedMa?.let { fastChargeCurrentMa = it }

            sicModeNodePath = SIC_MODE_CANDIDATES.firstOrNull {
                Shell.cmd("test -e $it && echo 1 || echo 0").exec().out.joinToString("").trim() == "1"
            }
            sicBoostEnabled = PropertyUtils.get(PROP_SIC_BOOST) == "1"
            if (sicBoostEnabled) applySicBoostInternal(true)

            val savedLimit = PropertyUtils.get(PROP_CHARGE_LIMIT).toIntOrNull()
            if (savedLimit != null && chargeLimitSupported) {
                chargeLimitPercent = savedLimit
                if (savedLimit < CHARGE_LIMIT_DISABLED_PERCENT) applyChargeLimitInternal(savedLimit)
            }

            batterySaverEnabled = Shell.cmd("settings get global low_power").exec()
                .out.joinToString("").trim() == "1"

            refreshStats()
            startPolling()
        }
    }

    /** Reads battery facts that don't change during a charge session, so they're read once instead of every poll tick. */
    private fun loadStaticBatteryInfo(dir: String) {
        batteryTechnology = Shell.cmd("cat $dir/technology 2>/dev/null").exec()
            .out.joinToString("").trim().ifEmpty { "-" }
        cycleCount = readInt("$dir/cycle_count")

        val full = readLong("$dir/charge_full")
        val design = readLong("$dir/charge_full_design")
        batteryHealthPercent = if (full != null && design != null && design > 0) {
            ((full.toDouble() / design.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            null
        }
    }

    private fun refreshStats() {
        val dir = batteryDir ?: return
        capacityPercent = readInt("$dir/capacity") ?: capacityPercent
        voltageMv = (readLong("$dir/voltage_now")?.div(1000))?.toInt() ?: voltageMv
        temperatureC = (readInt("$dir/temp")?.div(10f)) ?: temperatureC
        val status = Shell.cmd("cat $dir/status 2>/dev/null").exec().out.joinToString("").trim()
        isCharging = status.equals("Charging", ignoreCase = true) || status.equals("Full", ignoreCase = true)

        // Kernels disagree on current_now polarity: many report a negative value
        // while current enters the battery. The UI contract is intentionally
        // direction-stable: positive means charging, negative means discharging.
        val rawCurrentMa = (readLong("$dir/current_now")?.div(1000))?.toInt()
        currentMa = rawCurrentMa?.let { current ->
            when {
                isCharging -> kotlin.math.abs(current)
                else -> -kotlin.math.abs(current)
            }
        } ?: currentMa

        // USB type/online live under power_supply/usb, a sibling of the battery node.
        chargerType = Shell.cmd("cat /sys/class/power_supply/usb/type 2>/dev/null")
            .exec().out.joinToString("").trim().ifEmpty { "-" }
        usbConnected = Shell.cmd("cat /sys/class/power_supply/usb/online 2>/dev/null")
            .exec().out.joinToString("").trim() == "1"

        fastChargeNodePath?.let { path ->
            fastChargeCurrentMa = (readLong(path)?.div(1000))?.toInt() ?: fastChargeCurrentMa
        }
        sicModeNodePath?.let { path ->
            sicModeCurrentValue = Shell.cmd("cat $path 2>/dev/null").exec().out.joinToString("").trim()
                .ifEmpty { null }
        }
    }

    private fun readInt(path: String): Int? =
        Shell.cmd("cat $path 2>/dev/null").exec().out.joinToString("").trim().toIntOrNull()

    private fun readLong(path: String): Long? =
        Shell.cmd("cat $path 2>/dev/null").exec().out.joinToString("").trim().toLongOrNull()

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshStats()
                delay(3000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    fun applyFastChargeCurrentMa(ma: Int) {
        val path = fastChargeNodePath ?: return
        fastChargeCurrentMa = ma
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("echo ${ma * 1000} > $path 2>/dev/null").exec()
            PropertyUtils.set(PROP_FASTCHG_MA, ma.toString())
        }
    }

    fun setSicBoost(enabled: Boolean) {
        sicBoostEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            applySicBoostInternal(enabled)
            PropertyUtils.set(PROP_SIC_BOOST, if (enabled) "1" else "0")
        }
    }

    private fun applySicBoostInternal(enabled: Boolean) {
        val path = sicModeNodePath ?: return
        val value = if (enabled) SIC_MODE_BOOST_VALUE else SIC_MODE_OFF_VALUE
        Shell.cmd("echo $value > $path 2>/dev/null").exec()
    }

    /** Toggles the charge limit on/off, defaulting a fresh enable to [CHARGE_LIMIT_DEFAULT_ON_PERCENT]. */
    fun setChargeLimitEnabled(enabled: Boolean) {
        applyChargeLimit(if (enabled) CHARGE_LIMIT_DEFAULT_ON_PERCENT else CHARGE_LIMIT_DISABLED_PERCENT)
    }

    /** Sets the exact cutoff percentage; [CHARGE_LIMIT_DISABLED_PERCENT] (100) turns the limit off. */
    fun applyChargeLimit(percent: Int) {
        chargeLimitPercent = percent
        viewModelScope.launch(Dispatchers.IO) {
            applyChargeLimitInternal(percent)
            PropertyUtils.set(PROP_CHARGE_LIMIT, percent.toString())
        }
    }

    private fun applyChargeLimitInternal(percent: Int) {
        val path = chargeLimitNodePath ?: return
        Shell.cmd("echo $percent > $path 2>/dev/null").exec()
    }

    fun setBatterySaver(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val value = if (enabled) "1" else "0"
            Shell.cmd("settings put global low_power $value").exec()
            var live = Shell.cmd("settings get global low_power").exec().out.joinToString("").trim()
            if (live != value) {
                Shell.cmd("cmd power set-mode ${if (enabled) 1 else 0}").exec()
                live = Shell.cmd("settings get global low_power").exec().out.joinToString("").trim()
            }
            withContext(Dispatchers.Main) { batterySaverEnabled = live == value }
        }
    }
}
