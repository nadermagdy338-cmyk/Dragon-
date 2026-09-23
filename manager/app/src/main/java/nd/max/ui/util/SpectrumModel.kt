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
 * **هندسة طيف الحمل** — القاعدة التي تمنع الطيف أن يبدو ناقصًا في بدايته.
 *
 * القاعدة: **عدد الفتحات ثابت دائمًا** (٢٦)، والرسم يبدأ من اليمين (الأحدث في الطرف)،
 * وما لم تصل عيّنته بعد يبقى **فتحة كاملة فارغة** لا فراغًا. ففي الثانية الأولى يُرى
 * إطار كامل تملؤه العيّنات تدريجيًّا، بدل شريطين عريضين يشغلان الشاشة ثم يضيقان كلما
 * كبر السجل — وهو ما كان يجعل البداية تبدو عطبًا لا بداية.
 *
 * وGPU هنا **زوج** لا ضيف: العمودان يُرسمان معًا من نفس المقياس (٠–١٠٠٪)، فيُرى من
 * يضغط ومن ينتظر. و`paired` تُحسب من النافذة لا من الجهاز: هاتف لا يُقرأ فيه عقد GPU
 * يرسم أعمدة CPU بعرض الفتحة كامل بدل أن يترك نصف الأعمدة فارغًا أبدًا.
 *
 * والحساب هنا خالص بلا Compose: يُقاس في JVM، ويُختبر بلا جهاز (انظر `SpectrumModelTest`).
 */
package nd.max.ui.util

/** شريط واحد في الطيف: نسبة CPU، ونسبة GPU حين توجد قراءة في تلك العيّنة. */
data class SpectrumBar(val cpu: Float, val gpu: Float?)

/**
 * إطار جاهز للرسم. [bars] بطول ثابت دائمًا، مرتّب زمنيًّا (الأقدم أولًا)، و`null` تعني
 * «فتحة لم تصلها عيّنة» — وهي تُرسم فارغة، لا تُحذف.
 */
data class SpectrumFrame(val paired: Boolean, val bars: List<SpectrumBar?>)

/** ملخّص النافذة المعروضة: المتوسط والذروة على CPU، حيث يكون الطيف هو المرجع. */
data class LoadSummary(val average: Int, val peak: Int)

object Spectrum {

    /** عرض النافذة، بوحدة الفتحة. ٢٦ فتحة على شاشة هاتف = أعمدة تُقرأ ولا تتلاصق. */
    const val SLOTS: Int = 26

    fun frame(samples: List<LoadSample>, slots: Int = SLOTS): SpectrumFrame {
        val count = slots.coerceAtLeast(1)
        val window = samples.takeLast(count)
        if (window.isEmpty()) return SpectrumFrame(paired = false, bars = List(count) { null })
        val bars = ArrayList<SpectrumBar?>(count)
        repeat(count - window.size) { bars += null }
        for (sample in window) {
            val cpu = measured(sample.cpu)
            // عيّنة لا نسبة CPU فيها ليست صفرًا: «لم تُقس» غير «قُيست فكانت هادئة».
            // فتُترك فتحتها فارغة، وموضع الزمن لا ينزاح كما لو حُذفت العيّنة.
            bars += if (cpu == null) null
            else SpectrumBar(cpu = cpu, gpu = sample.gpu?.let(::measured))
        }
        return SpectrumFrame(paired = window.any { it.gpu?.isFinite() == true }, bars = bars)
    }

    /** المتوسط والذروة مقرّبان لعدد صحيح — كما تُعرض في مربّعات الطيف. */
    fun summary(samples: List<LoadSample>): LoadSummary {
        val window = samples.takeLast(SLOTS).mapNotNull { measured(it.cpu) }
        if (window.isEmpty()) return LoadSummary(average = 0, peak = 0)
        return LoadSummary(
            average = (window.sum() / window.size).toInt(),
            peak = window.max().toInt(),
        )
    }

    /**
     * القيمة الصالحة الوحيدة: **رقم** (لا `NaN` ولا ما لا نهاية) داخل ٠–١٠٠. وما عدا ذلك
     * `null` — تُعرض فتحة فارغة، لا شريط مؤلَّف. والقاعدة نفسها في `LoadHistoryCodec`،
     * فلا يختلف الشكل الذي يُحفظ عن الشكل الذي يُرسم.
     */
    private fun measured(value: Float): Float? =
        value.takeIf { it.isFinite() }?.coerceIn(0f, 100f)
}
