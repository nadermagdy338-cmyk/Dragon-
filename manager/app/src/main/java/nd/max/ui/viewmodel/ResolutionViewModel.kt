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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nd.max.core.platform.PropertyUtils
import kotlin.math.roundToInt

data class ResolutionPreset(
    val id: String,
    val widthPx: Int,
    val heightPx: Int,
    val dpi: Int,
    val badge: String
)

class ResolutionViewModel : ViewModel() {

    companion object {
        private const val PROP_CUSTOM_W = MaxManagerProps.Display.RES_WIDTH
        private const val PROP_CUSTOM_H = MaxManagerProps.Display.RES_HEIGHT
        private const val PROP_CUSTOM_DPI = MaxManagerProps.Display.RES_DPI

        /** علّمتان تفصلان مخرجات الأوامر الثلاثة داخل **نداء واحد** (لا تظهران من الأوامر). */
        private const val MARK_DENSITY = "#MAX_WM_DENSITY#"
        private const val MARK_FPS = "#MAX_WM_FPS#"
    }

    var isLoaded by mutableStateOf(false)
        private set

    // Physical/native panel values, read once from the hardware itself.
    var nativeWidthPx by mutableStateOf(0)
        private set
    var nativeHeightPx by mutableStateOf(0)
        private set
    var nativeDpi by mutableStateOf(0)
        private set
    var refreshRateHz by mutableStateOf(0)
        private set

    // Currently active wm override values (may equal native if unset).
    var activeWidthPx by mutableStateOf(0)
        private set
    var activeHeightPx by mutableStateOf(0)
        private set
    var activeDpi by mutableStateOf(0)
        private set

    var lockAspectRatio by mutableStateOf(true)

    var presets by mutableStateOf<List<ResolutionPreset>>(emptyList())
        private set

    /**
     * فتح الشاشة: **ثلاث رحلات صدفة صارت واحدة** (عطب سرعة مُبلَّغ عنه).
     *
     * `wm size` ثم `wm density` ثم `dumpsys display | grep fps=` — وكلّها في الصدفة الواحدة
     * المُسلسَلة، و`dumpsys display` من أبطأ أوامرها. صارت الأوامر الثلاثة **تنفيذًا واحدًا**
     * (`Shell.cmd(a,b,c)` تُكتب كلّها في مدخل الصدفة نفسه بترتيبها)، والفصل بعلامتين —
     * **ونصّ كل أمر وتحليله لم يُمسّا**: الأنماط الثلاثة أدناه هي هي، والنتيجة نفسها.
     */
    fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            val out = runCatching {
                Shell.cmd(
                    "wm size",
                    "echo $MARK_DENSITY",
                    "wm density",
                    "echo $MARK_FPS",
                    "dumpsys display | grep -m1 'fps=' ",
                ).exec().out.map { it.trim() }
            }.getOrDefault(emptyList())

            val densityAt = out.indexOf(MARK_DENSITY)
            val fpsAt = out.indexOf(MARK_FPS)

            // Native physical size ("wm size" without override reports "Physical size: WxH")
            val sizeOut = out.take(densityAt.takeIf { it >= 0 } ?: out.size).joinToString("\n")
            val physical = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(sizeOut)
            val override = Regex("Override size:\\s*(\\d+)x(\\d+)").find(sizeOut)

            val densityOut = out.subList(
                (densityAt + 1).coerceAtMost(out.size),
                fpsAt.takeIf { it > densityAt } ?: out.size,
            ).joinToString("\n")
            val physicalDensity = Regex("Physical density:\\s*(\\d+)").find(densityOut)
            val overrideDensity = Regex("Override density:\\s*(\\d+)").find(densityOut)

            val refreshOut = if (fpsAt >= 0) out.drop(fpsAt + 1).joinToString("") else ""
            val refresh = Regex("fps=([0-9.]+)").find(refreshOut)?.groupValues?.get(1)
                ?.toFloatOrNull()?.roundToInt() ?: 0

            val pw = physical?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val ph = physical?.groupValues?.get(2)?.toIntOrNull() ?: 0
            val pd = physicalDensity?.groupValues?.get(1)?.toIntOrNull() ?: 0

            nativeWidthPx = pw
            nativeHeightPx = ph
            nativeDpi = pd
            refreshRateHz = refresh

            activeWidthPx = override?.groupValues?.get(1)?.toIntOrNull() ?: pw
            activeHeightPx = override?.groupValues?.get(2)?.toIntOrNull() ?: ph
            activeDpi = overrideDensity?.groupValues?.get(1)?.toIntOrNull() ?: pd

            presets = buildPresets(pw, ph, pd)
            isLoaded = true
        }
    }

    /**
     * Presets are derived as fractions of the device's own native panel resolution,
     * so this works correctly regardless of the physical panel's real size.
     */
    private fun buildPresets(nativeW: Int, nativeH: Int, nativeD: Int): List<ResolutionPreset> {
        if (nativeW <= 0 || nativeH <= 0 || nativeD <= 0) return emptyList()

        fun scaled(fraction: Float, badge: String): ResolutionPreset {
            // Keep even width for encoder/renderer friendliness.
            val w = (nativeW * fraction).roundToInt().let { it - (it % 2) }
            val h = (w.toFloat() / nativeW * nativeH).roundToInt()
            val d = (nativeD * fraction).roundToInt()
            return ResolutionPreset(badge, w, h, d, badge)
        }

        return listOf(
            scaled(1.0f, "Native").copy(id = "native"),
            scaled(0.8f, "Balanced").copy(id = "balanced"),
            scaled(0.6f, "Performance").copy(id = "performance"),
            scaled(0.45f, "Battery Saver").copy(id = "battery_saver")
        )
    }

    fun applyPreset(preset: ResolutionPreset) {
        applyResolution(preset.widthPx, preset.heightPx, preset.dpi)
    }

    fun applyResolution(width: Int, height: Int, dpi: Int) {
        activeWidthPx = width
        activeHeightPx = height
        activeDpi = dpi
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd(
                "wm size ${width}x${height}",
                "wm density $dpi"
            ).exec()
            PropertyUtils.set(PROP_CUSTOM_W, width.toString())
            PropertyUtils.set(PROP_CUSTOM_H, height.toString())
            PropertyUtils.set(PROP_CUSTOM_DPI, dpi.toString())
        }
    }

    /** Reverts to the panel's true native resolution and density. */
    fun resetToNative() {
        if (nativeWidthPx <= 0 || nativeHeightPx <= 0 || nativeDpi <= 0) return
        activeWidthPx = nativeWidthPx
        activeHeightPx = nativeHeightPx
        activeDpi = nativeDpi
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("wm size reset", "wm density reset").exec()
            PropertyUtils.set(PROP_CUSTOM_W, "")
            PropertyUtils.set(PROP_CUSTOM_H, "")
            PropertyUtils.set(PROP_CUSTOM_DPI, "")
        }
    }
}
