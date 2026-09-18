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

package nd.max.ui.util

import java.util.Locale

/**
 * `GAP-11` — **جرد المستشعرات**، بلا ادّعاء قدرة.
 *
 * والسبب في وجود الملف ليس عرض قائمة؛ السبب قاعدة كشفها فحصُ الكود: في المستودع قراءة مستشعر
 * واحدة، وكانت **تفشل دائمًا**: `registerListener` ثم `unregisterListener` في الكتلة نفسها —
 * فلا يصل حدث قطّ، والنتيجة تبقى `0f`. وصفر لوكس يعني «مظلم»، لا «لم أقرأ». فالكود كان يقول
 * **«مظلم»** كلما عجز عن القراءة، وهذا أسوأ من أن يقول «لا أعرف».
 *
 * فالقاعدة الحاكمة هنا ثلاثية لا ثنائية:
 *
 * 1. **`ABSENT`** — المنصّة تقول: هذا الجهاز لا يملك هذا المستشعر. تُقال بصراحة.
 * 2. **`REPORTED`** — وصلت قيمة حقيقية.
 * 3. **`UNREADABLE` / لا قراءة** — المستشعر موجود ولم تصل قيمة في المهلة. وهنا **لا نُخترع
 *    صفرًا**: القيمة `null` وتُعرض «لم أقرأ».
 *
 * ولا ندّعي معايرة ولا «صحة مستشعر» ولا درجة: نعرض ما تُعلنه المنصّة عن العتاد، وما قرأناه
 * فعلًا. أي تشخيص أبعد من ذلك يحتاج مرجعًا ماديًّا لا نملكه.
 */
object SensorInventory {

    /** تصنيف المستشعر كما تصنّفه أندرويد — للتجميع في العرض لا للحكم. */
    enum class Kind(val id: String) {
        MOTION("motion"),
        ENVIRONMENT("environment"),
        POSITION("position"),
        BODY("body"),
        UNPOSITIONED("unpositioned"),
        OTHER("other"),
    }

    /**
     * تصنيف النوع الرقمي. والمجهول يصير [Kind.OTHER] — **وذلك مقصود**: أندرويد تعرّف أنواعًا
     * خاصة بالمُصنّعين أرقامها فوق ٦٥٥٣٥، وتخمين تصنيفها أسوأ من قول «غير مصنَّف» واسمها كما هو.
     */
    fun kindOf(typeId: Int): Kind = when (typeId) {
        1, 4, 9, 10, 11 -> Kind.MOTION                       // ACCELEROMETER, GYROSCOPE
        2, 5, 6, 8, 12, 13, 14, 15 -> Kind.ENVIRONMENT        // MAGNETIC, LIGHT, PRESSURE…
        3, 16, 17, 18, 19, 20 -> Kind.POSITION                // ORIENTATION, GRAVITY, ROTATION…
        21, 22, 23, 24, 25, 26, 31, 34, 35 -> Kind.BODY      // HEART_RATE, STEP_COUNTER…
        28, 29 -> Kind.UNPOSITIONED                           // SIGNIFICANT_MOTION, STEP_DETECTOR
        else -> Kind.OTHER
    }

    /** حالة قراءة واحدة: ثلاث نتائج لا اثنتان. */
    enum class ReadingState(val id: String) {
        REPORTED("reported"),
        ABSENT("absent"),
        UNREADABLE("unreadable"),
    }

    /** قراءة الضوء — وأهمّ ما فيها أنها **لا تُعيد صفرًا عند العجز**. */
    data class LightReading(val lux: Float?, val state: ReadingState) {
        /** هل تُصلح هذه القيمة لتوجيه سياسة؟ لا إلا إذا كانت مقيسة. */
        val usable: Boolean get() = state == ReadingState.REPORTED && lux != null
    }

    /**
     * حكم قراءة الضوء — دالّة خالصة تُثبّت القاعدة وتمنع تكرار الخطأ.
     *
     * @param sensorAbsent المنصّة أعلنت عدم وجود المستشعر.
     * @param timedOut انقضت المهلة بلا حدث (أو رُفض التسجيل).
     * @param lux القيمة التي وصلت إن وصلت.
     */
    fun lightReading(sensorAbsent: Boolean, timedOut: Boolean, lux: Float?): LightReading = when {
        sensorAbsent -> LightReading(null, ReadingState.ABSENT)
        // قيمة فارغة **مع** مستشعر ⇒ لم نقرأ، ولا نسمّيها صفرًا.
        lux == null || timedOut -> LightReading(null, ReadingState.UNREADABLE)
        else -> LightReading(lux, ReadingState.REPORTED)
    }

    /** مستشعر كما تُعلنه المنصّة. ولا شيء هنا مُشتقّ أو مُقدَّر. */
    data class Item(
        val name: String,
        val vendor: String,
        val typeId: Int,
        val kind: Kind,
        val powerMilliAmp: Float,
        val maxRange: Float,
        val resolution: Float,
        val minDelayUs: Int,
        val isWakeUp: Boolean,
    )

    /**
     * الجرد كاملًا: ما تُعلنه المنصّة، والأصناف **الغائبة**، وقراءة الضوء.
     *
     * وذكر الأصناف الغائبة هو الفائدة الحقيقية: المستخدم الذي يسأل «هل جهازي فيه مستشعر قرب؟»
     * يجد الجواب صريحًا بدل أن يستنتج من غياب صفّ في قائمة.
     */
    data class Report(
        val items: List<Item>,
        val kindsReported: List<Kind>,
        val light: LightReading,
    ) {
        val count: Int get() = items.size

        /** أجهزة تعمل في وضع الإيقاف — تُذكّر بأنها مستهلكة للطاقة. */
        val wakeUpCount: Int get() = items.count { it.isWakeUp }
    }

    /** بناء التقرير من قائمة المنصّة **المقيسة**. */
    fun report(items: List<Item>, light: LightReading): Report = Report(
        items = items.sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() })),
        kindsReported = items.map { it.kind }.distinct().sortedBy { it.ordinal },
        light = light,
    )

    /**
     * تنسيق الاستهلاك: المنصّة تُعلنه بالمللي أمبير، و«0» تعني «غير معلَن» لا «صفر».
     * والمنطقة ثابتة ([Locale.US]) كي لا يختلف الناتج باختلاف لغة الجهاز.
     */
    fun powerLabel(powerMilliAmp: Float): String? =
        if (powerMilliAmp <= 0f) null else String.format(Locale.US, "%.2f", powerMilliAmp)

    /** تنسيق أقصى مدى — و«0» تعني غير معلَن كذلك. */
    fun rangeLabel(maxRange: Float): String? =
        if (maxRange <= 0f) null else String.format(Locale.US, "%.3g", maxRange)

    /** أدنى تأخير: تُعلنه المنصّة بالميكروثانية، و«0» تعني «غير معلَن» لا «فوري». */
    fun delayLabel(minDelayUs: Int): String? =
        if (minDelayUs <= 0) null else minDelayUs.toString()
}
