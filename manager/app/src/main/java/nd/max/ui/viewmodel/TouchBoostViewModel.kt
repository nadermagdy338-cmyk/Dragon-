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
import nd.max.core.hardware.WriteVerification
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
        // Report-rate command values are not standardized. They remain hidden
        // until an adapter can obtain supported values from the driver without
        // mutating user state.
        private val SAMPLE_RATE_CANDIDATES = emptyList<TouchNode>()
        private val DOUBLE_TAP_CANDIDATES = listOf(
            TouchNode("/proc/touchpanel/double_tap_enable", "1", "0"),
            TouchNode("/sys/android_touch/doubletap2wake", "1", "0")
        )

        /**
         * زمن الانتظار بعد محاولة فاشلة: العقدة التي لا تُكتب لا تُعاد محاولتها كل ٥٠٠ م.ث.
         *
         * والسبب مقيس لا مفترض: كل محاولة تكتب سطر `WRITE_CHECK`، فتكون عقدة معطوبة سببًا
         * لدَفق سجلات يدفن العطل الذي جاء الملف ليشرحه. والمهلة تقصر كل الأثر على سطر واحد كل
         * دقيقة — وهذا حدّ أعلى معلَن، لا صمت. وتُصفَّر المهلة عند نجاح لاحق، فلا رجوع تدريجيًا.
         */
        private const val RETRY_BACKOFF_MS = 60_000L
        @Volatile private var retryNotBeforeMs = 0L

        /** آخر قرار أُرسل إلى محوّل المنصّة — فلا يُنادى في كل دورة بلا تغيّر مطلوب. */
        @Volatile private var lastVendorRequest: Boolean? = null

        /**
         * **يُصلح حالة المقبض عند الحاجة فقط** — والقراءة قبل الكتابة هي الفرق بين «حلقة كتابة»
         * و«حلقة مراقبة».
         *
         * ولماذا لزم: كان `AppMonitor.buildStatus` ينادي الكتابة في كل دورة (٥٠٠ م.ث)، فسُجّلت على
         * جهاز حقيقي ٣٠١٦ كتابة إلى `/proc/touch_boost/enable` في ٣٦ دقيقة — ٩٤% من ملف السجل،
         * وكلها بالقيمة نفسها (`wrote=0 read=0`). فأفسدت شيئين معًا: **قابلية التشخيص** (العطل
         * الحقيقي مدفون تحت ثلاثة آلاف سطر)، و**كلفة بلا فائدة** على محرّك اللمس كل ٠.٧ ثانية.
         *
         * والقاعدة الآن: لا كتابة إلا إذا كانت العقدة **لا تحمل** القيمة المطلوبة. والقراءة أرخص
         * من الكتابة هنا (لا `chmod` ولا `printf`)، وهي **أدقّ** من الحفظ في الذاكرة: تكشف أن
         * شيئًا آخر غيّر العقدة، فيُصلَح في نفس الدورة لا بعد مهلة.
         *
         * @return `null` حين لا شيء يُفعَل (العقدة تحمل المطلوب، أو لا مسار متحقّق على هذا الجهاز)،
         *         `true` إذا كُتبت وتحقّقت، `false` إذا فشلت.
         */
        fun reconcileBestEffortBoost(enabled: Boolean): Boolean? {
            val provider = discoverBoostNode()
            if (provider == null) {
                // لا مسار sysfs متحقّق: تبقى قناة محوّل المنصّة (وهي بلا عقد مُثبَت اليوم، فغيابها
                // لا يعني فشلًا). ولا تُنادى إلا عند تغيّر القرار، فلا تُربَط خدمة AIDL كل ٥٠٠ م.ث.
                if (lastVendorRequest == enabled) return null
                val applied = runCatching { XiaomiVendorFeatures.applyTouchBoost(enabled) }.getOrDefault(false)
                if (applied) lastVendorRequest = enabled
                return applied
            }
            if (nodeHolds(provider, enabled)) return null
            val now = System.currentTimeMillis()
            if (now < retryNotBeforeMs) return null
            val written = writeAndVerify(provider, enabled)
            if (written) retryNotBeforeMs = 0L else retryNotBeforeMs = now + RETRY_BACKOFF_MS
            return written
        }

        /**
         * هل تحمل العقدة القيمة المطلوبة **الآن**؟ — سؤال قراءة لا كتابة.
         *
         * والمقارنة تمرّ بـ[WriteVerification.compare] لا بمقارنة نصّية، فتُقبل التكافؤ العددي
         * (`1` و`01`) وتوحيد الفراغات — وإلا قرأنا «مختلفة» في عقدة سليمة فكتبنا بلا داعٍ.
         */
        private fun nodeHolds(node: TouchNode, enabled: Boolean): Boolean {
            val wanted = if (enabled) node.onValue else node.offValue
            val live = RootFileAccess.read(node.path) ?: return false
            return WriteVerification.compare(wanted, live) == WriteVerification.Outcome.MATCHED
        }

        private fun discoverBoostNode(): TouchNode? =
            (GAME_MODE_CANDIDATES + SAMPLE_RATE_CANDIDATES).firstOrNull(::isVerifiedNode)

        private fun isVerifiedNode(node: TouchNode): Boolean =
            RootFileAccess.exists(node.path) && RootFileAccess.read(node.path) != null

        private fun writeAndVerify(node: TouchNode, enabled: Boolean): Boolean {
            if (!isVerifiedNode(node)) return false
            val value = if (enabled) node.onValue else node.offValue
            // `PEER-8`: كان التحقّق اليدوي (write + read + مقارنة نصّية) مكتوبًا هنا وحده.
            // صار يمرّ بالبدائية المشتركة `writeVerified` — **تغيير مقصود ومعلَن**: المقارنة
            // صارت تقبل التكافؤ العددي (`1` و`01`) وتوحّد الفراغات، لأن السؤال هو «هل استقرّت
            // القيمة؟» لا «هل النصّ حرفيًّا نفسه؟». ومَن يريد الحرفية يقرأ العقدة بنفسه.
            return RootFileAccess.writeVerified(node.path, value) ==
                WriteVerification.Outcome.MATCHED
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
