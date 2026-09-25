package nd.max.core.jni

/**
 * صيغة ردّ الضغط الأصلية — **نفس الصيغة في Rust حرفيًّا** (`src/archive.rs`).
 *
 * الوجود منفصلًا عن الجسر كما في [ProbePacket]: التحويل نصًّا هو **العقد بين اللغتين**،
 * ويفسده أن يكون مدفونًا في نداء `external` لا يُنفَّذ في اختبارات JVM. فهنا دالتان نقيتان
 * تقيسهما اختبارات الوحدة، وعلى الجانب الآخر `archive::tests::result_packet_round_trips`
 * بنفس المتجهات — فأي انحراف على أي جانب يُسقط اختبارًا، لا يمرّ صامتًا.
 *
 * الصيغة (سطر واحد، ثلاثة حقول بفاصل `\u0001`):
 *  - `OK<sep>entries<sep>bytes` — نجاح: عدد المداخل والبایتات المقروءة من المصادر.
 *  - `FAIL<sep>reason<sep>subject` — فشل بصيغته الرمزية (`no_sources` · `unreadable` ·
 *    `write_failed`) وعينته (مسار أو نصّ خطأ، وقد تكون فارغة).
 *
 * و**الرموز لا جمل**: النصّ الظاهر للمستخدم يُصاغ في طبقة الواجهة وحدها، فلا تتسرّب نصوص
 * عربية إلى العقد بين اللغتين (ولا تتضاعف مع كل لغة).
 */
internal object ArchivePacket {

    /** الفاصل بين حقول الردّ — مطابق لـ`archive::FIELD_SEP` في Rust. */
    const val FIELD_SEP: Char = '\u0001'

    /** أقصى عدد حقول نقبله: ثلاثة. والزائد يعني حزمة أطول من العقد ⇒ تُرفض كاملة. */
    private const val FIELDS = 3

    /** سبب فشل مقروء من القيمة الرمزية — وما لا نعرفه لا يُخمَّن. */
    sealed interface Result {
        data class Ok(val entries: Int, val bytes: Long) : Result

        data class Failed(val reason: String, val subject: String?) : Result
    }

    /**
     * مسارات المصادر كما تُشحن — **بنفس عقد الدفعة** ([ProbePacket.packPaths]): مسار لكل سطر.
     * فلا عقد ثانٍ للمسارات في المستودع، ولا اختلاف في التطبيع بين مسارين.
     */
    fun packSources(sources: List<String>): String = ProbePacket.packPaths(sources)

    /** هل تصلح المسارات للحزمة؟ (سطر جديد أو الفاصل داخل مسار يكسر المحاذاة.) */
    fun packable(sources: List<String>): Boolean = ProbePacket.packable(sources)

    /**
     * تفكيك ردّ واحد. والعائد `null` لكل ما لا يطابق العقد: عدد حقول مخالف، أو رقم غير
     * رقميّ، أو رمز نجاح/فشل غير معروف.
     *
     * **ولا محاذاة مخمَّنة هنا إطلاقًا:** ردّ مشوَّه لا يُقرأ «نجاحًا بأصفار» — لأن المتصل
     * حينها سيُعلن أرشيفًا «نُفِّذ وتُحقّق» وهو لم يُكتب. والجهل يُعلن ([null]) فيعود المتصل
     * إلى تنفيذ Kotlin.
     */
    fun unpack(text: String): Result? {
        if (text.isEmpty()) return null
        val fields = text.split(FIELD_SEP)
        if (fields.size != FIELDS) return null
        return when (fields[0]) {
            "OK" -> {
                val entries = fields[1].toIntOrNull() ?: return null
                val bytes = fields[2].toLongOrNull() ?: return null
                if (entries < 0 || bytes < 0) return null
                Result.Ok(entries, bytes)
            }
            "FAIL" -> {
                val reason = fields[1]
                if (reason.isBlank()) return null
                Result.Failed(reason, fields[2].ifBlank { null })
            }
            else -> null
        }
    }
}
