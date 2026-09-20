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
 * **هندسة مقياس التردد** — القراءة الحالية مقابل السقف الذي تعلنه النواة.
 *
 * المقياس يعرض سؤالًا واحدًا: «إلى أي مدى وصلت الساعة فعلاً من مداها؟» وثلاث حالات تختلف
 * اختلافًا يجب أن يُرى، لا أن يُطوى في رقم:
 *
 * 1. **مقروء** (`MEASURED`): قراءة موجبة وسقف موجب ⇒ تعبئة `current/ceiling`.
 * 2. **قراءة بلا سقف** (`CEILING_UNREADABLE`): الساعة تُقرأ لكن النواة لا تُعلن حدًّا أعلى
 *    (أو تُعلن صفرًا) ⇒ **لا تعبئة أصلًا**. اختراع سقف من أعلى قيمة رأيناها يجعل الشريط
 *    يقيس شيئًا آخر ثم يُقرأ كأنه المدى.
 * 3. **لا قراءة** (`NO_READING`): النواة مُطفأة أو العقد غير موجود ⇒ فراغ، لا «٠ هرتز»
 *    الذي يُقرأ كسكون تام.
 *
 * وتقرير هذا في ملف صافٍ بلا Compose مقصود: نفس سبب `Spectrum` — القاعدة تُقاس في JVM
 * بلا جهاز (`ClockMeterModelTest`)، وطبقة الرسم تستهلكها ولا تعيد اشتقاقها.
 */
package nd.max.ui.util

/** ما يعرفه المقياس عن قراءة واحدة. */
enum class ClockTrust {
    /** قراءة وسقف معًا: الشريط يقيس نسبة حقيقية. */
    MEASURED,

    /** الساعة تُقرأ، والسقف غير معلَن ⇒ رقم بلا شريط. */
    CEILING_UNREADABLE,

    /** لا قراءة صالحة ⇒ لا رقم ولا شريط. */
    NO_READING,
}

object ClockMeter {

    /** مقاطع السقف في المدرّج. ١٢ مقطعًا تُقرأ بالعين على أضيق شاشة بلا عدّ. */
    const val STEPS: Int = 12

    fun trust(currentMhz: Int, ceilingMhz: Int?): ClockTrust = when {
        currentMhz <= 0 -> ClockTrust.NO_READING
        ceilingMhz == null || ceilingMhz <= 0 -> ClockTrust.CEILING_UNREADABLE
        else -> ClockTrust.MEASURED
    }

    /**
     * نسبة التعبئة، أو `null` حين لا سقف.
     *
     * وقراءة **تتجاوز** السقف تُقصّ إلى ١: التجاوز يقع فعلًا (تعزيز لحظي أو سقف قديم في
     * الكاش)، لكن مدىً أطول من المعلَن يقرأه المستخدم كعطب في الشريط لا كحقيقة عن الشريحة.
     */
    fun fraction(currentMhz: Int, ceilingMhz: Int?): Float? {
        if (trust(currentMhz, ceilingMhz) != ClockTrust.MEASURED) return null
        val ceiling = ceilingMhz ?: return null
        return (currentMhz.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * عدد المقاطع المضاءة من [steps].
     *
     * وقراءة موجبة تُضيء مقطعًا واحدًا على الأقل: «١٪ ≠ ٠٪» فرق يُرى على شريحةٍ ما زالت
     * تعمل، بينما تقريبٌ للأسفل يجعل أدنى تردّد وأعلى راحة متطابقين في الصورة.
     */
    fun litSteps(fraction: Float?, steps: Int = STEPS): Int {
        if (fraction == null || steps <= 0) return 0
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped <= 0f) return 0
        return kotlin.math.ceil(clamped * steps).toInt().coerceIn(1, steps)
    }

    /**
     * مقطع [index] (من اليسار) مضاء أم لا — والدالة موجودة لأن حدود الإضاءة تُختبر، لا
     * تُترك لتعبير منطقي مكتوب داخل Canvas لا يصل إليه اختبار.
     */
    fun isLit(index: Int, fraction: Float?, steps: Int = STEPS): Boolean {
        if (index < 0 || index >= steps) return false
        return index < litSteps(fraction, steps)
    }
}
