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
import kotlinx.coroutines.launch
import nd.max.ui.util.PropertyUtils
import nd.max.ui.util.XiaomiVendorHalUtil
import nd.max.XiaomiVendorFeatures

/**
 * Touch controller nodes are vendor-specific (there's no common kernel API like
 * devfreq for touch panels), so this probes a handful of paths that real OEM
 * touch drivers commonly expose and uses whichever ones actually exist on the
 * running device instead of assuming one vendor's layout.
 */
class TouchBoostViewModel : ViewModel() {

    data class TouchNode(val path: String, val onValue: String, val offValue: String)

    companion object {
        private const val PROP_ENABLED = MaxManagerProps.Touch.BOOST
        private const val PROP_DT2W = MaxManagerProps.Touch.DT2W

        // Candidate nodes seen across common MediaTek/Qualcomm OEM touch drivers.
        private val GAME_MODE_CANDIDATES = listOf(
            TouchNode("/proc/touchpanel/game_switch_enable", "1", "0"),
            TouchNode("/sys/touchpanel/game_switch_enable", "1", "0"),
            TouchNode("/proc/touchpanel/oplus_tp_direction", "1", "0"),
            TouchNode("/proc/touch_boost/enable", "1", "0")
        )
        private val SAMPLE_RATE_CANDIDATES = listOf(
            TouchNode("/proc/touchpanel/touch_sample_rate", "240", "120"),
            TouchNode("/sys/class/touch/touch_dev/report_rate", "240", "120"),
            TouchNode("/sys/devices/platform/goodix_ts.0/switch_report_rate", "480", "240")
        )
        // Double-tap-to-wake is a distinct capability from touch sampling boost
        // (it fires while the screen is off), so it gets its own candidate list
        // and its own node rather than being folded into GAME_MODE_CANDIDATES.
        private val DOUBLE_TAP_CANDIDATES = listOf(
            TouchNode("/proc/touchpanel/double_tap_enable", "1", "0"),
            TouchNode("/sys/android_touch/doubletap2wake", "1", "0"),
            TouchNode("/proc/tp_gesture", "1", "0")
        )
    }

    var isAvailable by mutableStateOf<Boolean?>(null)
        private set
    // Set when no sysfs node was found but the device declares Xiaomi's vendor
    // touch HAL instead — lets the UI explain *why* instead of implying the
    // device has no touch-boost capability at all. See XiaomiVendorHalUtil.
    var vendorHalDetected by mutableStateOf(false)
        private set
    var gameModeNode by mutableStateOf<TouchNode?>(null)
        private set
    var sampleRateNode by mutableStateOf<TouchNode?>(null)
        private set
    var boostEnabled by mutableStateOf(false)
        private set
    var doubleTapNode by mutableStateOf<TouchNode?>(null)
        private set
    var doubleTapEnabled by mutableStateOf(false)
        private set

    fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            gameModeNode = GAME_MODE_CANDIDATES.firstOrNull { nodeExists(it.path) }
            sampleRateNode = SAMPLE_RATE_CANDIDATES.firstOrNull { nodeExists(it.path) }
            doubleTapNode = DOUBLE_TAP_CANDIDATES.firstOrNull { nodeExists(it.path) }

            val hasNodes = gameModeNode != null || sampleRateNode != null
            vendorHalDetected = if (!hasNodes) {
                XiaomiVendorHalUtil.hasTouchFeatureHal() && XiaomiVendorFeatures.isTouchFeatureAvailable()
            } else false
            isAvailable = hasNodes || vendorHalDetected || doubleTapNode != null

            if (isAvailable == true) {
                boostEnabled = PropertyUtils.get(PROP_ENABLED) == "1"
                if (boostEnabled) applyInternal(true)

                if (doubleTapNode != null) {
                    doubleTapEnabled = PropertyUtils.get(PROP_DT2W) == "1"
                    applyDoubleTapInternal(doubleTapEnabled)
                }
            }
        }
    }

    private fun nodeExists(path: String): Boolean =
        Shell.cmd("test -e $path && echo 1 || echo 0").exec().out.joinToString("").trim() == "1"

    fun setBoost(enabled: Boolean) {
        boostEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            val applied = applyInternal(enabled)
            if (applied) {
                PropertyUtils.set(PROP_ENABLED, if (enabled) "1" else "0")
            } else {
                boostEnabled = !enabled
            }
        }
    }

    private fun applyInternal(enabled: Boolean): Boolean {
        var applied = false
        if (vendorHalDetected) {
            applied = XiaomiVendorFeatures.applyTouchBoost(enabled)
        }
        gameModeNode?.let { node ->
            val value = if (enabled) node.onValue else node.offValue
            applied = Shell.cmd("echo $value > ${node.path} 2>/dev/null").exec().isSuccess || applied
        }
        sampleRateNode?.let { node ->
            val value = if (enabled) node.onValue else node.offValue
            applied = Shell.cmd("echo $value > ${node.path} 2>/dev/null").exec().isSuccess || applied
        }
        return applied
    }

    fun setDoubleTapToWake(enabled: Boolean) {
        doubleTapEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            applyDoubleTapInternal(enabled)
            PropertyUtils.set(PROP_DT2W, if (enabled) "1" else "0")
        }
    }

    private fun applyDoubleTapInternal(enabled: Boolean) {
        val node = doubleTapNode ?: return
        val value = if (enabled) node.onValue else node.offValue
        Shell.cmd("echo $value > ${node.path} 2>/dev/null").exec()
    }
}
