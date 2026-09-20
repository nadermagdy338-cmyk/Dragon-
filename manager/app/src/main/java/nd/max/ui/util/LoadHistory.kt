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
 * **تاريخ الحمل بين جلسات التطبيق** — طيف الشاشة الرئيسية لا يبدأ من الصفر كل مرة.
 *
 * ولماذا يُحفظ أصلًا: الطيف يقيس «توزيع الحمل في آخر فترة»؛ فإذا مُحي عند كل إغلاق صار
 * يقيس «منذ فتحتَ التطبيق» — وهي معلومة أخرى، أقلّ فائدة. والكتابة هنا **عيّنة واحدة
 * لكل دورة** (كل ثانيتين)، ولا تُكتب على القرص إلا كل دفعةٍ من الدقائق (انظر
 * `HomeDashboardViewModel`) فلا تتحوّل الشاشة إلى مسجّل كتابة.
 *
 * وقاعدتان تحكمان الترميز، كما في `FileStore`:
 *  1. **ما لا يُفهم لا يُسقط الشاشة** — سطر مشوّه يُسقط نفسه وحده، والبقية تُقرأ.
 *  2. **لا يُعاد معلومة قديمة على أنها «الآن»** — عيّنة أقدم من نافذة الصلاحية تُسقط،
 *     وعيّنة بطابع زمني في المستقبل تُسقط (ساعة الجهاز تحرّكت، فلا نُرجعها «حديثة»).
 */
package nd.max.ui.util

import java.io.File

/** عيّنة حمل واحدة. [gpu] غائبة (`null`) حين لا يُقرأ عقد GPU في تلك الدورة — لا صفرًا. */
data class LoadSample(
    val atMs: Long,
    val cpu: Float,
    val gpu: Float? = null,
)

/** ثوابت التاريخ: سقف الطول ونافذة الصلاحية، في مكان واحد يقرأه النموذج والتخزين. */
object LoadHistory {

    /** كما في حلقة القياس: ٣٦ عيّنة = ٧٢ ثانية بمعدّل عيّنة كل ثانيتين. */
    const val LIMIT: Int = 36

    /**
     * نافذة الصلاحية: ما بين الجلستين لا يُعرض كأنه اللحظة. ساعة واحدة حدٌّ مقصود —
     * فتح التطبيق بعد دقائق يكمل الطيف، وبعد ساعات يبدأ نظيفًا بدل أن يعلن «الآن» عن
     * حمل الأمس. والطيف نفسه يبقى مرسومًا بفتحاته الفارغة الكاملة فلا يبدو ناقصًا.
     */
    const val STALE_AFTER_MS: Long = 60L * 60L * 1000L
}

/**
 * ترميز سطر لكل عيّنة: `الطابع الزمني` ثم نسبة CPU ثم نسبة GPU (أو `-` لغيابها).
 * سطرٌ يُقرأ بالعين عند الحاجة، وبلا مكتبة تُضاف إلى المشروع لأجل قياسين.
 */
object LoadHistoryCodec {

    /** فاصل محجوز لا يظهر في الأرقام. */
    private const val FIELD = "\u001F"

    /** علامة غياب قراءة GPU في العيّنة. */
    private const val ABSENT = "-"

    fun encode(samples: List<LoadSample>): String =
        samples.takeLast(LoadHistory.LIMIT).joinToString("\n") { sample ->
            val cpu = sample.cpu.takeIf { it.isFinite() } ?: 0f
            val gpu = sample.gpu?.takeIf { it.isFinite() }
            "${sample.atMs}$FIELD$cpu$FIELD${gpu?.toString() ?: ABSENT}"
        }

    /**
     * يفهم ما فُهم ويُسقط ما لم يُفهم: طابع زمني غير صالح أو في المستقبل · نسبة غير
     * رقمية · سطر أقدم من نافذة الصلاحية. والنتيجة مرتّبة زمنيًّا (الأقدم أولًا) ومقصوصة
     * عند [LoadHistory.LIMIT]، فتُغذّي الطيف مباشرة.
     */
    fun decode(text: String?, nowMs: Long): List<LoadSample> {
        if (text.isNullOrBlank()) return emptyList()
        val result = ArrayList<LoadSample>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val parts = line.split(FIELD)
            val atMs = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: continue
            if (atMs <= 0L || atMs > nowMs) continue
            if (nowMs - atMs > LoadHistory.STALE_AFTER_MS) continue
            val cpu = parts.getOrNull(1)?.trim()?.toFloatOrNull() ?: continue
            if (!cpu.isFinite()) continue
            val rawGpu = parts.getOrNull(2)?.trim().orEmpty()
            val gpu = if (rawGpu.isEmpty() || rawGpu == ABSENT) null else rawGpu.toFloatOrNull()
            result += LoadSample(
                atMs = atMs,
                cpu = cpu.coerceIn(0f, 100f),
                gpu = gpu?.takeIf { it.isFinite() }?.coerceIn(0f, 100f),
            )
        }
        return result.sortedBy { it.atMs }.takeLast(LoadHistory.LIMIT)
    }
}

/** مخزن ملف واحد لتاريخ الحمل — فشل القراءة أو الكتابة لا يُسقط الشاشة. */
class LoadHistoryStore(private val file: File) {

    fun load(nowMs: Long): List<LoadSample> = LoadHistoryCodec.decode(read(), nowMs)

    fun save(samples: List<LoadSample>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(LoadHistoryCodec.encode(samples))
        }
    }

    private fun read(): String? = runCatching {
        if (file.isFile) file.readText() else null
    }.getOrNull()
}
