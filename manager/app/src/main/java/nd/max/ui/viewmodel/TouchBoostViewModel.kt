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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nd.max.core.hardware.RootFileAccess
import nd.max.ui.util.PropertyUtils
import nd.max.XiaomiVendorFeatures

/**
 * Touch controller nodes are vendor-specific (there's no common kernel API like
 * devfreq for touch panels), so this probes a handful of paths that real OEM
 * touch drivers commonly expose and uses whichever ones actually exist on the
 * running device instead of assuming one vendor's layout.
 */
class TouchBoostViewModel : ViewModel() {

    data class TouchNode(
        val path: String,
        val onValue: String,
        val offValue: String,
    )

    companion object {
        private const val PROP_ENABLED = MaxManagerProps.Touch.BOOST
        private const val PROP_DT2W = MaxManagerProps.Touch.DT2W

        private val GAME_MODE_CANDIDATES = listOf(
            TouchNode("/proc/touchpanel/game_switch_enable", "1", "0"),
            TouchNode("/sys/touchpanel/game_switch_enable", "1", "0"),
            TouchNode("/proc/touch_boost/enable", "1", "0")
        )
        // These explicit report-rate nodes are only offered after their current
        // value is readable. Unknown touch nodes are never treated as generic
        // boost controls because their command values are vendor-defined.
        private val SAMPLE_RATE_CANDIDATES = listOf(
            TouchNode("/proc/touchpanel/touch_sample_rate", "240", "120"),
            TouchNode("/sys/class/touch/touch_dev/report_rate", "240", "120"),
            TouchNode("/sys/devices/platform/goodix_ts.0/switch_report_rate", "480", "240")
        )
        private val DOUBLE_TAP_CANDIDATES = listOf(
            TouchNode("/proc/touchpanel/double_tap_enable", "1", "0"),
            TouchNode("/sys/android_touch/doubletap2wake", "1", "0")
        )

        /**
         * The daemon and UI share this single ordered provider selection. Xiaomi
         * HAL takes precedence only when no verified sysfs provider is present.
         */
        fun applyBestEffortBoost(enabled: Boolean): Boolean {
            val provider = discoverBoostNode()
            return if (provider != null) {
                writeAndVerify(provider, enabled)
            } else {
                runCatching { XiaomiVendorFeatures.applyTouchBoost(enabled) }.getOrDefault(false)
            }
        }

        private fun discoverBoostNode(): TouchNode? =
            (GAME_MODE_CANDIDATES + SAMPLE_RATE_CANDIDATES).firstOrNull(::isVerifiedNode)

        private fun isVerifiedNode(node: TouchNode): Boolean =
            RootFileAccess.exists(node.path) && RootFileAccess.read(node.path) != null

        private fun writeAndVerify(node: TouchNode, enabled: Boolean): Boolean {
            if (!isVerifiedNode(node)) return false
            val value = if (enabled) node.onValue else node.offValue
            return RootFileAccess.write(node.path, value) && RootFileAccess.read(node.path)?.trim() == value
        }


    }

    var isAvailable by mutableStateOf<Boolean?>(null)
        private set
    // Reported only after the canonical Xiaomi provider itself can bind.
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
            gameModeNode = GAME_MODE_CANDIDATES.firstOrNull(::isVerifiedNode)
            sampleRateNode = if (gameModeNode == null) SAMPLE_RATE_CANDIDATES.firstOrNull(::isVerifiedNode) else null
            doubleTapNode = DOUBLE_TAP_CANDIDATES.firstOrNull(::isVerifiedNode)

            val hasNodes = gameModeNode != null || sampleRateNode != null
            vendorHalDetected = if (!hasNodes) XiaomiVendorFeatures.isTouchFeatureAvailable() else false
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
        RootFileAccess.exists(path) && RootFileAccess.read(path) != null

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
        val provider = gameModeNode ?: sampleRateNode
        return if (provider != null) {
            writeAndVerify(provider, enabled)
        } else if (vendorHalDetected) {
            runCatching { XiaomiVendorFeatures.applyTouchBoost(enabled) }.getOrDefault(false)
        } else false
    }

    fun setDoubleTapToWake(enabled: Boolean) {
        doubleTapEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            if (applyDoubleTapInternal(enabled)) {
                PropertyUtils.set(PROP_DT2W, if (enabled) "1" else "0")
            } else {
                doubleTapEnabled = !enabled
            }
        }
    }

    private fun applyDoubleTapInternal(enabled: Boolean): Boolean {
        val node = doubleTapNode ?: return false
        return writeAndVerify(node, enabled)
    }
}
