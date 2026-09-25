/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * جدول OPP الخاص بMediaTek — القدرة، والفهرس، والقفل الثابت.
 *
 * وفُصل في ملفه لأمرين مقيسين لا تجميليين:
 *
 * 1. **الحدّ**: `GpuHardwareBackend.kt` بلغ ١١٢٤ سطرًا بعد عمل سقف GPU فأسقط بوابة `code_health`
 *    (الدَّين مقيَّد بسقف لا ينمو). والحدّ هنا حقيقي: هذه الكتلة وحدة قائمة بذاتها — تقرأ أسطح
 *    `/proc/gpufreqv2` وحدها وتُعيد قدرةً وفهرسة، ولا تعرف شيئًا عن الاختيار ولا عن الكتابة.
 * 2. **الوضوح عند الفشل**: هذا هو الموضع الذي يُبنى عليه *تثبيت* تردد واحد، وهو المسار الذي
 *    يفسّر «عالقًا على ٦٥٠» حين يُخطئ الفهرس أو يثبت من جلسة سابقة. فوجوده ملفًا يُقرأ وحده
 *    يجعل مراجعته ممكنة بلا سياق ألف سطر.
 */
package nd.max.core.hardware

internal object MtkGpuOppTable {

    /**
     * أسطح OPP التي تُعلنها النواة، بترتيب قراءتها كلها لا أولها.
     *
     * والسبب مكتوب في [discover]: جدول واحد قد يكون *مُرشَّحًا وقت التشغيل* (ينتهي عند ٥٤٦
     * مثلًا) بينما `gpufreq_opp_dump` يحمل قدرة العتاد. وقراءة أول ملف غير فارغ وحدها تحوّل
     * سقفًا حيًّا إلى قدرة عتاد صامتة.
     */
    val OPP_TABLES = listOf(
        "/proc/gpufreqv2/stack_signed_opp_table",
        "/proc/gpufreqv2/gpu_working_opp_table",
        "/proc/gpufreq/gpufreq_opp_dump",
    )

    /** مسارات قفل الفهرس الثابت (`fix_target_opp_index` وجديله في الإصدارات الأقدم). */
    val LOCK_PATHS = listOf(
        "/proc/gpufreqv2/fix_target_opp_index",
        "/proc/gpufreq/gpufreq_opp_freq",
    )

    /** Capacity frequencies and safe fixed-index mappings are different facts. */
    data class Discovery(val frequencies: Set<Long>, val indexed: Map<Long, String>)

    /**
     * يقرأ أسطح OPP ويُعيد القدرة والفهرسة، ويُسقط كل تردد **تعارضت** فهرسته.
     *
     * ```
     * MTK exposes more than one OPP surface. The signed table can be a filtered
     * runtime table (for example ending at 546 MHz), while gpufreq_opp_dump
     * still exposes the hardware capability (1300 MHz on the reported device).
     * Reading only the first non-empty file silently turns a runtime ceiling into
     * a hardware maximum, which made Performance 100% write 546 successfully.
     * ```
     *
     * وترّدد ظهر بفهرسين مختلفين في جدولين مختلفين:
     *
     * ولماذا يُسقَط الفهرسان معًا بدل أن يأخذ الأخير: عقدة الكتابة (`fix_target_opp_index`) تُفسّر
     * رقمها **على جدول واحد** في النواة، بينما الرحى هنا تجمع ثلاثة أسطح والفهرس المأخوذ من سطح
     * آخر يُقصّ على **تردد غيره** — فلا يُخطئ الهدف فقط، بل يُعلن «نجاحًا» لأن التحقّق من صدى
     * الفهرس لا من التردد.
     *
     * ففهرس متعارض = «هذا التردد لا يُفهرَس بأمان» لا «اختر آخر». وإسقاطه يُبطل مسار القفل كلّه
     * (`mtkDiscovery.frequencies.all { it in mtkMap }` يفشل)، فيُعالَج الجهاز عبر مسار المدى
     * العادي: **نرفض التخمين ونسلك الطريق الذي يقيس**.
     */
    fun discover(io: GpuHardwareBackend.ReadIo): Discovery {
        val contents = OPP_TABLES.mapNotNull { path ->
            io.read(path)?.takeIf(String::isNotBlank)
        }
        if (contents.isEmpty()) return Discovery(emptySet(), emptyMap())
        val frequencies = linkedSetOf<Long>()
        val indexedResult = linkedMapOf<Long, String>()
        val conflicted = linkedSetOf<Long>()
        contents.asSequence().flatMap { it.lineSequence() }.forEach { line ->
            // Kernels expose both indexed tables (`[3] freq=...`) and the legacy
            // dump format (`freq = 1300000`). The latter is still authoritative
            // capability evidence, but it has no explicit index in some builds.
            // Give only a labelled, unit-bearing frequency a bounded ordinal; bare
            // voltage/frequency-looking numbers remain rejected below.
            val indexed = Regex("""\[\s*(\d+)\s*]""").find(line)?.groupValues?.getOrNull(1)
            val tail = line.substringAfterLast(']').trim()
            val labelledFrequency = Regex("""(?i)\bfreq(?:uency)?\s*[=:]\s*""").containsMatchIn(tail)
            if (indexed == null && !labelledFrequency) return@forEach
            // A synthetic ordinal is not a kernel OPP index. It may be used for
            // capability discovery only, never for a write.
            val index = indexed
            val matches = Regex("""(?i)(\d+(?:\.\d+)?)\s*(GHz|MHz|kHz)?""").findAll(tail).toList()
            fun isFrequencyLabeled(candidate: MatchResult): Boolean {
                val prefix = tail.substring(0, candidate.range.first)
                return listOf("freq", "frequency").any { prefix.contains(it, true) }
            }
            // Selection is proof-driven, never a guess: prefer a labeled frequency,
            // then any unit-bearing number, then a single bare number. Multiple bare
            // numbers without labels are ambiguous (frequency vs voltage) and the
            // whole line is rejected rather than misread.
            val match = matches.firstOrNull { candidate ->
                candidate.groupValues.getOrNull(2)?.isNotBlank() == true && isFrequencyLabeled(candidate)
            } ?: matches.firstOrNull { candidate -> isFrequencyLabeled(candidate) }
                ?: matches.firstOrNull { candidate -> candidate.groupValues.getOrNull(2)?.isNotBlank() == true }
                ?: matches.singleOrNull()
                ?: return@forEach
            val value = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            val hz = when (match.groupValues.getOrNull(2)?.lowercase().orEmpty()) {
                "ghz" -> (value * 1_000_000_000.0).toLong()
                "mhz" -> (value * 1_000_000.0).toLong()
                "khz" -> (value * 1_000.0).toLong()
                else -> when {
                    value >= 10_000_000.0 -> value.toLong()
                    value >= 10_000.0 -> (value * 1_000.0).toLong()
                    else -> (value * 1_000_000.0).toLong()
                }
            }
            if (hz in 1_000_000L..10_000_000_000L) {
                frequencies += hz
                if (index != null) {
                    val previous = indexedResult[hz]
                    when {
                        previous == null -> indexedResult[hz] = index
                        previous != index -> conflicted += hz
                    }
                }
            }
        }
        conflicted.forEach(indexedResult::remove)
        return Discovery(frequencies, indexedResult)
    }

    /**
     * يحوّل قراءة عقدة القفل إلى فهرس — و«لا قفل» نصًّا.
     *
     * والعقدة لا تُعيد رقمًا فقط: بعض الإصدارات تُعيد جملة (`fix GPU/STACK OPP index is disabled`)،
     * وهي **حالة تحرير** لا قيمة. وقراءة لا تحمل رقمًا تعني `-1` كذلك، فالمجهول يُعالَج كتحرير —
     * وهو الاختيار الآمن لأن `-1` لا تُثبّت شيئًا، بخلاف فهرس مُخترَع كان سيجعل الجهاز يقرأ
     * «مُثبَّت» على تردد لم يطلبه أحد.
     */
    fun parseIndex(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty() || clean == "-1" || clean.contains("disabled", true) || clean.contains("dynamic", true)) return "-1"
        return Regex("-?\\d+").findAll(clean).map { it.value }.toList().lastOrNull() ?: "-1"
    }

    /**
     * هل لا يوجد قفل OPP ثابت يقيّد التردد؟ صحيح أيضًا لجهاز لا يملك مسار قفل أصلًا (لا شيء
     * لنحرّره)، وخاطئ إن كان المسار موجودًا وغير مقروء — لا يُدّعى تحرير قفل لم يُقرأ.
     *
     * وبقي في [GpuHardwareBackend] مقابلًا بنفس الاسم لأن صيغة الصيغة (`encodeLive`) تناديه،
     * وواجهة الصيغة تخصّ ذلك الملف.
     */
    fun isReleased(device: GpuHardwareBackend.Device, io: GpuHardwareBackend.ReadIo): Boolean {
        val path = device.mtkFixedIndexPath ?: return true
        val raw = io.read(path) ?: return false
        return parseIndex(raw) == "-1"
    }
}
