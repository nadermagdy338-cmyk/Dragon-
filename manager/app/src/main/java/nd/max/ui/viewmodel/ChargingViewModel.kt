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
import nd.max.core.diagnostics.DiagnosticCenter
import nd.max.core.hardware.ChargingHardwareBackend

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

/**
 * The device's own charging state, as a code rather than a sentence.
 *
 * It replaces the English literal that the old battery screen built inline
 * ("Charging"/"Discharging"/…), which was untranslatable and mixed into an
 * Arabic UI.
 */
enum class BatteryStatus { Charging, Discharging, Full, NotCharging, Unknown }

/**
 * The kernel/BatteryManager verdict on the pack's condition.
 *
 * Deliberately separate from [ChargingViewModel.batteryHealthPercent]: that one
 * is our own ratio (full ÷ design), and this one is the driver's opinion. When
 * they disagree the user should see both, not one of them silently chosen.
 */
enum class BatteryHealthVerdict { Good, Overheat, Dead, OverVoltage, Cold, Unknown }

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

    /** What the node says right now. Always a code; the screen owns the wording. */
    var batteryStatus by mutableStateOf(BatteryStatus.Unknown)
        private set
    /** The driver's own verdict on the pack, read once. Independent of our own ratio. */
    var healthVerdict by mutableStateOf(BatteryHealthVerdict.Unknown)
        private set
    /** Nameplate capacity, so a worn pack can be read against what it was built as. */
    var designCapacityMah by mutableStateOf<Int?>(null)
        private set
    /** Capacity the pack reports as full right now. */
    var fullCapacityMah by mutableStateOf<Int?>(null)
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
        // µAh on the node, mAh in the UI. `null` when the node has nothing to say —
        // never 0, which would read as "this battery holds no charge".
        designCapacityMah = design?.takeIf { it > 0 }?.div(1000)?.toInt()
        fullCapacityMah = full?.takeIf { it > 0 }?.div(1000)?.toInt()

        healthVerdict = when (
            Shell.cmd("cat $dir/health 2>/dev/null").exec().out.joinToString("").trim().lowercase()
        ) {
            "good" -> BatteryHealthVerdict.Good
            "overheat", "hot" -> BatteryHealthVerdict.Overheat
            "dead" -> BatteryHealthVerdict.Dead
            "over voltage" -> BatteryHealthVerdict.OverVoltage
            "cold" -> BatteryHealthVerdict.Cold
            else -> BatteryHealthVerdict.Unknown
        }
    }

    private fun refreshStats() {
        val dir = batteryDir ?: return
        capacityPercent = readInt("$dir/capacity") ?: capacityPercent
        voltageMv = (readLong("$dir/voltage_now")?.div(1000))?.toInt() ?: voltageMv
        temperatureC = (readInt("$dir/temp")?.div(10f)) ?: temperatureC
        val status = Shell.cmd("cat $dir/status 2>/dev/null").exec().out.joinToString("").trim()
        batteryStatus = when (status.lowercase()) {
            "charging" -> BatteryStatus.Charging
            "discharging" -> BatteryStatus.Discharging
            "full" -> BatteryStatus.Full
            "not charging" -> BatteryStatus.NotCharging
            else -> BatteryStatus.Unknown
        }
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
        // العقدة المقروءة هي الحقيقة الوحيدة للمفتاح: كتابة رفضها السائق أو أزالها
        // شيء آخر تُظهر نفسها خلال دورة القراءة بدل أن يبقى المفتاح معروضًا "مفعّلًا".
        // وعقدة غير مقروءة (null) لا تُغيّر الحالة — فالمجهول ليس إثباتًا ولا نفيًا.
        sicModeCurrentValue?.let { live -> sicBoostEnabled = live == SIC_MODE_BOOST_VALUE }
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

    /**
     * حكم الكتابة على الحالة والقيمة المحفوظة — من **محصّلة العتاد** لا من الطلب.
     *
     * كان الثلاثة يكتبون ثم يُضبطون الحالة والخاصية على المطلوب بلا قراءة بعده، فحدُّ
     * شحن رفضه السائق يبقى معروضًا «مفعّلًا» والجهاز يشحن إلى ١٠٠٪. والقاعدة الآن:
     * ما ثبت يُعتمد ويُحفظ · وما رُفض لا يُحفظ وتعود الحالة إلى ما كانت (فلا تغيير
     * وقع فعلًا) · وما كُتب ولم يُمكن التحقّق منه يُعتمد لكن يُسجّل صريحًا بلا ادّعاء
     * تحقّق (انظر `ChargingHardwareBackend.Verdict`).
     *
     * @return true إن كُتب الطلب (متحقَّقًا أو غير قابل للتحقق) فيُعتمد ويُحفظ.
     */
    private fun acceptChargingWrite(
        knob: String,
        path: String,
        outcome: ChargingHardwareBackend.Outcome,
    ): Boolean {
        when (outcome.verdict) {
            ChargingHardwareBackend.Verdict.REFUSED -> {
                DiagnosticCenter.record(
                    "charging",
                    "charging write refused knob=$knob path=$path requested=${outcome.requested} " +
                        "actual=${outcome.actual ?: "none"} reason=${outcome.error ?: "unknown"}",
                )
                return false
            }
            ChargingHardwareBackend.Verdict.APPLIED_UNVERIFIED -> DiagnosticCenter.record(
                "charging",
                "charging write unverifiable knob=$knob path=$path requested=${outcome.requested} " +
                    "reason=${outcome.error ?: "node-not-readable-back"}",
                level = DiagnosticCenter.Level.WARN,
            )
            ChargingHardwareBackend.Verdict.VERIFIED -> Unit
        }
        return true
    }

    fun applyFastChargeCurrentMa(ma: Int) {
        val path = fastChargeNodePath ?: return
        val previous = fastChargeCurrentMa
        fastChargeCurrentMa = ma
        viewModelScope.launch(Dispatchers.IO) {
            // العقدة بالميكروأمبير والحالة بالمليأمبير — نفس تحويل fastChargeMaxMa أعلاه.
            val outcome = ChargingHardwareBackend.write(path, (ma * 1000).toString())
            if (acceptChargingWrite("fast_charge_current", path, outcome)) {
                PropertyUtils.set(PROP_FASTCHG_MA, ma.toString())
            } else {
                // الكبح المعلن أولى من العودة: إن أعاد السائق قيمة أخرى (وهو الشائع —
                // يثبّت أقرب تردد/تيار معلن) فالمعروض يصير ما يقرؤه العتاد لا ما طلبناه.
                fastChargeCurrentMa = outcome.actual?.toLongOrNull()?.div(1000)?.toInt() ?: previous
            }
        }
    }

    fun setSicBoost(enabled: Boolean) {
        val previous = sicBoostEnabled
        sicBoostEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = applySicBoostInternal(enabled)
            if (acceptChargingWrite("sic_mode", outcome.path ?: "none", outcome)) {
                PropertyUtils.set(PROP_SIC_BOOST, if (enabled) "1" else "0")
            } else {
                sicBoostEnabled = outcome.actual?.let { it == SIC_MODE_BOOST_VALUE } ?: previous
            }
        }
    }

    private fun applySicBoostInternal(enabled: Boolean): ChargingHardwareBackend.Outcome {
        val value = if (enabled) SIC_MODE_BOOST_VALUE else SIC_MODE_OFF_VALUE
        return ChargingHardwareBackend.write(sicModeNodePath, value)
    }

    /** Toggles the charge limit on/off, defaulting a fresh enable to [CHARGE_LIMIT_DEFAULT_ON_PERCENT]. */
    fun setChargeLimitEnabled(enabled: Boolean) {
        applyChargeLimit(if (enabled) CHARGE_LIMIT_DEFAULT_ON_PERCENT else CHARGE_LIMIT_DISABLED_PERCENT)
    }

    /** Sets the exact cutoff percentage; [CHARGE_LIMIT_DISABLED_PERCENT] (100) turns the limit off. */
    fun applyChargeLimit(percent: Int) {
        val previous = chargeLimitPercent
        chargeLimitPercent = percent
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = applyChargeLimitInternal(percent)
            if (acceptChargingWrite("charge_limit", outcome.path ?: "none", outcome)) {
                PropertyUtils.set(PROP_CHARGE_LIMIT, percent.toString())
            } else {
                // لا تُحفظ رغبة لم تثبت على العقدة: وإلا فُعِّل الحفظ من جديد عند
                // الإقلاع التالي على قيمة السائق لم يقبلها. والمعروض يتبع الحيّ إن قُرئ.
                chargeLimitPercent = outcome.actual?.toIntOrNull() ?: previous
            }
        }
    }

    private fun applyChargeLimitInternal(percent: Int): ChargingHardwareBackend.Outcome {
        val path = chargeLimitNodePath
        return ChargingHardwareBackend.write(path, percent.toString())
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
