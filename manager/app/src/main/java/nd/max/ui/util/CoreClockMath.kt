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
/*
 * حساب ساعة الأنوية — **الأرقام وحدها**، بلا Compose وبلا مسار ملفّ.
 *
 * كان القراران (`السقف` و`النسبة`) دالّتين خاصّتين داخل `CpuCoreControlScreen`، فلا يُقاسان
 * إلا بعين على هاتف. وطلب المالك في `MAX-MANAGER-LEVEL-UP.md` §12 (المرحلة ٣) يجعلهما **حكمًا
 * مقيسًا**: «نواة offline ≠ 0 MHz» و«نواة بلا max = MHz بلا %». والحكم لا يُقاس داخل دالّة
 * خاصة في ملفّ Compose، فصُود هنا ليُقاس في JVM بقراءات مُصنَّعة (`CoreClockMathTest`).
 *
 * **وثلاث حالات لا رقم واحد** — وهذا نصّ العقد لا تجميل:
 *
 * 1. **`null` = لم يُقرأ** (النواة تخفي `cpufreq` أو قراءتها فشلت) ⇒ لا نسبة ولا «0 MHz».
 * 2. **`0` = مسكّنة** (عقد `CpuTopologyUtil.coreFreqMhz`: صفر يعني النواة مطفأة/مسكّنة لا أنها
 *    تعمل بتردّد صفر — وهي حالة ثالثة تُكتب باسمها). وكانت الشاشة تكتبها `0 MHz` قبل هذا
 *    الفصل، وهو ما ينهى عنه الأمر نفسه.
 * 3. **موجب = حيّ**، ونسبته من **السقف المعلن** لعنقودها لا من أعلى عيّنة رأيناها.
 *
 * **والسقف المعلن هو المقام الوحيد الصالح:** لو قسمنا على أعلى عيّنة لصار الرقم يتحرّك من نفسه
 * (٠٫٦ ثم ٠٫٨ لأن نواة أخرى صعدت)، ولو قسمنا على `scaling_max_freq` الحيّ لصار حدّ الخنق الحراري
 * «١٠٠٪». و`cpuinfo_max_freq` هو الرقم الذي لا يتحرّك، وهو نفسه الذي تعرضه بقية الشاشة.
 *
 * **وسقفٌ مجهول (`0`) لا يُنتج نسبةً كاذبة:** تُعرض MHz بلا «%» — لا تُخترع نسبة من مقام مجهول.
 */
package nd.max.ui.util

/** حالات عرض تردّد نواة واحدة — ثلاث حالات معلنة يقابلها نصّ في الشاشة. */
enum class CoreClockState {
    /** النواة غير متصلة (`online` = 0). */
    OFFLINE,

    /** النواة متصلة لكنّ التردّد **لم يُقرأ** — لا يُكتب مكانه صفر. */
    HIDDEN,

    /** النواة متصلة والقراءة صفر: مسكّنة/مطفأة، لا «تعمل بـ0 MHz». */
    PARKED,

    /** قراءة موجبة: التردّد معروض، ونسبته محسوبة من سقف عنقودها. */
    LIVE,
}

/**
 * قراءة نواة واحدة كما سترسمها البلاطة.
 *
 * @param state أيّ الحالات الثلاث (أو الحيّة).
 * @param percent النسبة من السقف، أو `null` حين **لا مقام صالح** — وهي لا تُعرض «0%».
 */
data class CoreClockReading(val state: CoreClockState, val percent: Int?)

object CoreClockMath {

    /**
     * سقف عنقود النواة من `cpuinfo_max_freq`، أو `0` إن لم يُقرأ.
     * و`0` تُقرأ «مجهول» لا «صفر هرتز» — ولذلك تمنع النسبة في [sharePercent].
     */
    fun ceilingMhz(policyPath: String, clusterMaxFreqMhz: Map<String, Int>): Int =
        clusterMaxFreqMhz[policyPath] ?: 0

    /**
     * نسبة التردّد من السقف، أو `null` إن كانت القراءة أو السقف غير صالحين.
     *
     * والقصّ عند ١٠٠٪ مقصود: `scaling_cur_freq` قد يتجاوز `cpuinfo_max_freq` على أنظمة ترفع
     * السقف ديناميكيًّا، فلا يُعرض «١٠٣٪» لنواة عند حدّها.
     */
    fun sharePercent(freqMhz: Int?, ceilingMhz: Int): Int? {
        if (freqMhz == null || freqMhz <= 0 || ceilingMhz <= 0) return null
        return ((freqMhz.toFloat() / ceilingMhz) * 100f).toInt().coerceIn(0, 100)
    }

    /** القراءة الكاملة لبلاطة نواة: `online` + التردّد + سقف العنقود ⇒ حالة ونسبة. */
    fun reading(online: Boolean, freqMhz: Int?, ceilingMhz: Int): CoreClockReading = when {
        !online -> CoreClockReading(CoreClockState.OFFLINE, null)
        freqMhz == null -> CoreClockReading(CoreClockState.HIDDEN, null)
        freqMhz <= 0 -> CoreClockReading(CoreClockState.PARKED, null)
        else -> CoreClockReading(CoreClockState.LIVE, sharePercent(freqMhz, ceilingMhz))
    }

    /**
     * متوسط النسب في بطل الشاشة، أو `null` إن لم تُقَس نواة واحدة.
     *
     * والمتوسط على **ما قيس وحده**: نواة مطفأة أو مقروءة `null` لا تدخل الحساب بصفر، وإلا
     * كتب البطل «٪نصف» على جهاز يعمل بكامل قوّته.
     */
    fun averagePercent(shares: List<Int>): Int? =
        shares.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
}
