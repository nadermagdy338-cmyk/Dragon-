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

/**
 * **هندسة موجة الساعة** — تاريخ تردّد CPU/GPU كما يُرسم في بطاقتَي الرئيسية.
 *
 * الموجة ليست زينة: الشريط المستقيم يقول «أين الساعة الآن»، والمنحنى يقول ما لا يقوله رقم
 * واحد — **هل هذه الساعة ثابتة أم تتقافز؟**. جهاز يثبت على ٧٠٪ من مداه درجةٌ واحدة،
 * وجهاز يقفز بين ٧٠٪ و١٠٠٪ كل ثانيتين مشكلةٌ أخرى تمامًا (تذبذب يؤدي إلى حرارة وتباطؤ).
 *
 * وثلاث قواعد تحكم التحويل، وكلها لئلا تُرسم معلومة لم تُقس:
 *
 * 1. **بلا سقف معلَن لا موجة**: بلا `cpuinfo_max_freq` أو `max_freq` للرسوم، لا مدى
 *    يُقاس إليه ⇒ لا نقاط. اختراع سقف من أعلى قيمة رأيناها يجعل الموجة تقيس تاريخ قياساتنا.
 * 2. **العيّنة غير المقروءة `null` لا صفرًا**: صفر يعني «سكون تام» وهو ادّعاء عن العتاد؛
 *    و`null` تعني «لم تُقس» فيُكسر الخط عندها بدل أن ينزل إلى القاع كذبًا.
 * 3. **التاريخ بلا هرتز**: هذه الطبقة تأخذ أرقامًا فقط (MHz)، فلا تعرف عقدةً ولا ملفًّا.
 */
package nd.max.ui.util

/** ما تقوله عيّنتان متجاورتان عن الاتجاه. */
enum class ClockTrend { RISING, FALLING, STEADY, UNKNOWN }

object ClockWave {

    /**
     * أقل عدد عيّنات تُرسم به موجة. عيّنة واحدة «نقطة»، ورسمها كمنحنى يُوهم بتاريخ لم يُقَس.
     */
    const val MIN_POINTS: Int = 2

    /**
     * نسبة كل عيّنة إلى السقف المعلَن: `0..1`، أو `null` حين لا تُرسم (سقف غائب أو قراءة غائبة).
     *
     * والقراءة **فوق** السقف تُقصّ إلى ١: التجاوز يقع فعلًا (تعزيز لحظي أو سقف قديم في
     * الكاش)، لكنه في رسمٍ يُقرأ عطبًا في المحور لا حقيقةً عن الشريحة.
     */
    fun series(clocks: List<Int?>, ceilingMhz: Int?): List<Float?> {
        if (!canMeasure(ceilingMhz)) return clocks.map { null }
        val ceiling = ceilingMhz ?: return clocks.map { null }
        return clocks.map { mhz ->
            if (mhz == null || mhz <= 0) null
            else (mhz.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
        }
    }

    /** هل تصلح هذه القياسات لرسم موجة أصلًا؟ */
    fun canDraw(clocks: List<Int?>, ceilingMhz: Int?): Boolean =
        canMeasure(ceilingMhz) && clocks.count { it != null && it > 0 } >= MIN_POINTS

    /** سقف صالح = رقم موجب معلن. فلا يُشتق سقف من البيانات نفسها. */
    fun canMeasure(ceilingMhz: Int?): Boolean = ceilingMhz != null && ceilingMhz > 0

    /** رأس الموجة («الدودة»): آخر قراءة مقيسة، أو `null` حين لا قراءة بعد. */
    fun head(series: List<Float?>): Float? = series.lastOrNull { it != null }

    /**
     * اتجاه آخر خطوة **بين عيّنتين مقروءتين**، مع تجاوز الفراغات.
     *
     * القرآن في الوسط (`null`) ليس سكونًا: لو قارنّا آخر عيّنتين بتتابعهما لصار انقطاع
     * قراءة «هبوطًا» في السهم — وهذا ادّعاء عن العتاد سببه فشل قراءتنا.
     */
    fun trend(series: List<Float?>, threshold: Float = STEADY_BAND): ClockTrend {
        val measured = series.filterNotNull()
        if (measured.size < 2) return ClockTrend.UNKNOWN
        val delta = measured.last() - measured[measured.size - 2]
        return when {
            delta > threshold -> ClockTrend.RISING
            delta < -threshold -> ClockTrend.FALLING
            else -> ClockTrend.STEADY
        }
    }

    /** عتبة «ثابت»: تغيّر أصغر من ٥٪ من المدى لا يُقرأ كاتجاه في رسم بهذا الحجم. */
    const val STEADY_BAND: Float = 0.05f
}
