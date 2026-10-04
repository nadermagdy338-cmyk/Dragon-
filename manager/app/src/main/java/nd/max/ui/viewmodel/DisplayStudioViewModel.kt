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
import nd.max.core.hardware.RootFileAccess

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nd.max.core.platform.PropertyUtils
import nd.max.ui.util.XiaomiVendorHalUtil

/**
 * Display panel feature nodes, like touch nodes, are vendor-specific rather than
 * a common kernel API. Each feature probes a short list of real-world OEM sysfs
 * paths and only exposes the toggle if a matching node is actually present.
 */
class DisplayStudioViewModel : ViewModel() {

    data class FeatureNode(val path: String, val onValue: String, val offValue: String)

    companion object {
        private val SUNLIGHT_CANDIDATES = listOf(
            FeatureNode("/sys/class/leds/lcd-backlight/hbm", "1", "0"),
            FeatureNode("/sys/devices/platform/mtk-hbm/hbm_enable", "1", "0")
        )
        private val SILKY_CANDIDATES = listOf(
            FeatureNode("/sys/class/graphics/fb0/aod_brightness_smooth", "1", "0"),
            FeatureNode("/proc/silky_brightness/enable", "1", "0")
        )
        private val VIDEO_ENH_CANDIDATES = listOf(
            FeatureNode("/sys/class/graphics/fb0/video_enhance", "1", "0"),
            FeatureNode("/proc/mtk_video_enhance/enable", "1", "0")
        )
        private val HDR_CANDIDATES = listOf(
            FeatureNode("/sys/class/graphics/fb0/hdr_enable", "1", "0"),
            FeatureNode("/proc/dolby_vision/enable", "1", "0")
        )

        private const val PROP_SUNLIGHT = MaxManagerProps.Display.SUNLIGHT_MODE
        private const val PROP_SILKY = MaxManagerProps.Display.SILKY_BRIGHTNESS
        private const val PROP_VIDEO = MaxManagerProps.Display.VIDEO_ENHANCE
        private const val PROP_HDR = MaxManagerProps.Display.HDR_ENABLE

        /** Preset window/transition/animator durations, in the order shown to the user. "Off" is 0. */
        val ANIMATION_SCALE_PRESETS = listOf(0f, 0.5f, 1f, 1.5f, 2f)
        /** Preset font scales, in the order shown to the user. 1.0 is the system default. */
        val FONT_SCALE_PRESETS = listOf(0.85f, 1f, 1.15f, 1.3f)
        /** Preset screen-off timeouts in seconds, in the order shown to the user. */
        val SCREEN_TIMEOUT_PRESETS = listOf(15, 30, 60, 120, 300, 600, 1800)
    }

    var animationScale by mutableStateOf(1f); private set
    var fontScale by mutableStateOf(1f); private set
    var screenTimeoutSeconds by mutableStateOf(30); private set
    var nightLightEnabled by mutableStateOf(false); private set
    var colorInversionEnabled by mutableStateOf(false); private set

    var isLoaded by mutableStateOf(false)
        private set
    // Set when none of the sysfs feature nodes exist but the device declares
    // Xiaomi's vendor display HAL instead — see XiaomiVendorHalUtil.
    var vendorHalDetected by mutableStateOf(false)
        private set

    var sunlightNode by mutableStateOf<FeatureNode?>(null); private set
    var silkyNode by mutableStateOf<FeatureNode?>(null); private set
    var videoEnhanceNode by mutableStateOf<FeatureNode?>(null); private set
    var hdrNode by mutableStateOf<FeatureNode?>(null); private set

    var sunlightEnabled by mutableStateOf(false); private set
    var silkyEnabled by mutableStateOf(false); private set
    var videoEnhanceEnabled by mutableStateOf(false); private set
    var hdrEnabled by mutableStateOf(false); private set

    fun loadState(context: Context) {
        val resolver = context.contentResolver
        viewModelScope.launch(Dispatchers.IO) {
            // أربع قوائم مرشّحين: كانت ثمانية نداءات وجود متتابعة (`test -e` عبر صدفة لكل
            // مرشّح)، وصارت نداءً واحدًا لكل قائمة عبر الدفعة الأصلية.
            sunlightNode = RootFileAccess.firstExisting(SUNLIGHT_CANDIDATES) { it.path }
            silkyNode = RootFileAccess.firstExisting(SILKY_CANDIDATES) { it.path }
            videoEnhanceNode = RootFileAccess.firstExisting(VIDEO_ENH_CANDIDATES) { it.path }
            hdrNode = RootFileAccess.firstExisting(HDR_CANDIDATES) { it.path }

            if (sunlightNode == null && silkyNode == null && videoEnhanceNode == null && hdrNode == null) {
                vendorHalDetected = XiaomiVendorHalUtil.hasDisplayFeatureHal()
            }

            sunlightEnabled = PropertyUtils.get(PROP_SUNLIGHT) == "1"
            silkyEnabled = PropertyUtils.get(PROP_SILKY) == "1"
            videoEnhanceEnabled = PropertyUtils.get(PROP_VIDEO) == "1"
            hdrEnabled = PropertyUtils.get(PROP_HDR) == "1"

            if (sunlightEnabled) apply(sunlightNode, true)
            if (silkyEnabled) apply(silkyNode, true)
            if (videoEnhanceEnabled) apply(videoEnhanceNode, true)
            if (hdrEnabled) apply(hdrNode, true)

            animationScale = readSetting(resolver, "global", "window_animation_scale")?.toFloatOrNull() ?: 1f
            fontScale = readSetting(resolver, "system", "font_scale")?.toFloatOrNull() ?: 1f
            screenTimeoutSeconds = ((readSetting(resolver, "system", "screen_off_timeout")?.toLongOrNull() ?: 30_000L) / 1000L).toInt()
            nightLightEnabled = readSetting(resolver, "secure", "night_display_activated") == "1"
            colorInversionEnabled = readSetting(resolver, "secure", "accessibility_display_inversion_enabled") == "1"

            isLoaded = true
        }
    }

    /**
     * قراءة إعداد واحد — **بالواجهة المباشرة أوّلًا، والصدفة احتياطًا**.
     *
     * ** ولماذا (عطب سرعة مُبلَّغ عنه في فتح الشاشات):** كانت `Shell.cmd("settings get …")`
     * لكل مفتاح — **خمس رحلات صدفة** في كل فتح لهذه الشاشة (ولادة عملية + تفسير صدفة لكل واحدة)،
     * وهي رحلات تُصطفّ على صدَفة الجذر الواحدة في التطبيق. والإعداد نفسه تقرؤه واجهة أندرويد
     * بلا جذر وبلا صدفة (`Settings`)، فالقراءة المباشرة تُلغي الخمس.
     *
     * **وحدّها معلن:** إن رفضت المنصّة المفتاح (‏`SecurityException` في المفاتيح المحميّة) عادت
     * الصدفة كما كانت حرفيًّا — فالسلوك على جهاز لا تُقرأ فيه الواجهة **محفوظ**، لا مُفترَض.
     * و`null` من الواجهة يعني «غير مضبوط» — وهي بالضبط ما تعنيه `settings get` بـ`null`.
     */
    private fun readSetting(resolver: ContentResolver, namespace: String, key: String): String? {
        val direct = runCatching {
            when (namespace) {
                "global" -> Settings.Global.getString(resolver, key)
                "system" -> Settings.System.getString(resolver, key)
                "secure" -> Settings.Secure.getString(resolver, key)
                else -> null
            }
        }
        if (direct.isSuccess) {
            return direct.getOrNull()?.takeIf { it.isNotEmpty() && it != "null" }
        }
        return Shell.cmd("settings get $namespace $key").exec().out.joinToString("").trim()
            .takeIf { it.isNotEmpty() && it != "null" }
    }


    private fun apply(node: FeatureNode?, enabled: Boolean) {
        node?.let {
            val value = if (enabled) it.onValue else it.offValue
            Shell.cmd("echo $value > ${it.path} 2>/dev/null").exec()
        }
    }

    fun setSunlight(enabled: Boolean) {
        sunlightEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            apply(sunlightNode, enabled)
            PropertyUtils.set(PROP_SUNLIGHT, if (enabled) "1" else "0")
        }
    }

    fun setSilky(enabled: Boolean) {
        silkyEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            apply(silkyNode, enabled)
            PropertyUtils.set(PROP_SILKY, if (enabled) "1" else "0")
        }
    }

    fun setVideoEnhance(enabled: Boolean) {
        videoEnhanceEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            apply(videoEnhanceNode, enabled)
            PropertyUtils.set(PROP_VIDEO, if (enabled) "1" else "0")
        }
    }

    fun setHdr(enabled: Boolean) {
        hdrEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            apply(hdrNode, enabled)
            PropertyUtils.set(PROP_HDR, if (enabled) "1" else "0")
        }
    }

    fun updateAnimationScale(scale: Float) {
        animationScale = scale
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd(
                "settings put global window_animation_scale $scale",
                "settings put global transition_animation_scale $scale",
                "settings put global animator_duration_scale $scale"
            ).exec()
        }
    }

    fun updateFontScale(scale: Float) {
        fontScale = scale
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("settings put system font_scale $scale").exec()
        }
    }

    fun setScreenTimeout(seconds: Int) {
        screenTimeoutSeconds = seconds
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("settings put system screen_off_timeout ${seconds * 1000}").exec()
        }
    }

    fun setNightLight(enabled: Boolean) {
        nightLightEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("settings put secure night_display_activated ${if (enabled) 1 else 0}").exec()
        }
    }

    fun setColorInversion(enabled: Boolean) {
        colorInversionEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("settings put secure accessibility_display_inversion_enabled ${if (enabled) 1 else 0}").exec()
        }
    }
}
