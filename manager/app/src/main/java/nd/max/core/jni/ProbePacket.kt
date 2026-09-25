/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.jni

/**
 * صيغة حزمة الدفعات — **نفس الصيغة في Rust حرفيًّا** (`src/probe.rs`).
 *
 * الوجود منفصلًا عن الجسر عمدًا: التحويل نصًّا هو **العقد بين اللغتين**، ويفسده أن يكون
 * مدفونًا في نداء `external` لا يُنفَّذ في اختبارات JVM. فهنا دالتان نقيتان تقيسهما
 * اختبارات الوحدة، وعلى الجانب الآخر اختبار `same_vectors` بنفس المُدخلات — فأي انحراف
 * في أي من الجانبين يُسقط اختبارًا هناك أو هنا، لا يمرّ صامتًا.
 *
 * القواعد (مطبَّقة على الجانبين):
 *  - `paths`: سطر لكل مسار · الأسطر الفارغة تُتجاهل · `\r\n` يُطبَّع.
 *  - `values`: سطر لكل مسار **بالترتيب** · السطر الفارغ = لم تُقرأ.
 *  - `\u0001` داخل القيمة = سطر جديد داخلها، فلا ينكسر عدّ الأسطر.
 *  - القيمة الفارغة بعد القصّ = غير مقروءة (نفس دلالة `RootFileAccess.read`).
 */
internal object ProbePacket {

    /** فاصل السطر الجديد داخل القيمة — مطابق لـ`probe::NEWLINE_ESCAPE` في Rust. */
    const val NEWLINE_ESCAPE: Char = '\u0001'

    /** مسارات الدفعة كما تُشحن إلى Rust. */
    fun packPaths(paths: List<String>): String = paths.joinToString("\n") { it.trim() }

    /**
     * هل تصلح هذه المسارات للحزمة؟ مسار فيه سطر جديد أو الفاصل نفسه لا يمكن تمثيله
     * بلا إرباك المحاذاة — فيُرفض الطلب الأصلي كله ويعود المتصل للطريق المصرَّح.
     */
    fun packable(paths: List<String>): Boolean = paths.none { path ->
        path.isBlank() || path.any { it == '\n' || it == '\r' || it == NEWLINE_ESCAPE }
    }

    /**
     * تفكيك القيم: `null` لكل سطر فارغ، والقيمة مطبَّعة (قصّ الأطراف + فكّ فاصل السطر).
     * وإن اختلف عدد الأسطر عن المتوقّع فالعائد كله `null` — **لا محاذاة مخمَّنة**؛ فالحزمة
     * المشوَّهة أخطر من غيابها، لأنها تنسب قيمة عقدة إلى عقدة أخرى.
     */
    fun unpackValues(packed: String, expected: Int): List<String?> {
        if (expected <= 0) return emptyList()
        val rows = packed.split('\n')
        if (rows.size != expected) return List(expected) { null }
        return rows.map { row ->
            val trimmed = row.trim()
            if (trimmed.isEmpty()) null else trimmed.replace(NEWLINE_ESCAPE, '\n')
        }
    }

    /**
     * تفكيك أعلام الوجود: سطر `1` = موجود، و`0` = غير موجود.
     * وإن اختلف العدد عن المتوقّع فالعائد `null` — **لا تخمين وجود** لكل مسار.
     */
    fun unpackFlags(packed: String, expected: Int): List<Boolean>? {
        if (expected <= 0) return if (packed.isEmpty()) emptyList() else null
        val rows = packed.split('\n')
        if (rows.size != expected) return null
        return rows.map { it.trim() == "1" }
    }

    /**
     * شحن سطور **كما هي** — بلا قصّ أطراف: محلّل السجلّات يقرأ السطر خامًا، وقصّه يغيّر
     * الحكم (سطر فيـه مسافة بادئة يُسقَط عند Kotlin ولا يُطابق، وبعد القصّ يُطابق).
     */
    fun packRawLines(lines: List<String>): String = lines.joinToString("\n")

    /** نوع سطر سجلّ بعد التحليل: حقل يبدأ بالرمز، أو `null` بمعنى «خارج النموذج». */
    class LogRow(val kind: Char, val fields: List<String>)

    /**
     * صفّ واحد من مخرجات محلّل الدفعة في Rust.
     *
     * `S` = تُسقَط (Kotlin كانت تُعيد `null`) · `F` = احتياط (Kotlin تبني المدخلة من النصّ
     * الخام) · `P` = محلَّلة وحقولها بعد الرمز مفصولةً بالفاصل، بترتيب الجانبين المُتفَق
     * عليه: logcat = (تاريخ · وقت · pid · tid · مستوى · وسم · نصّ) والموقَّع = (ختم · مستوى ·
     * وسم · نصّ). والفاصل المزاح (`\u0002`) يُعاد إلى `\u0001`، فلا يكسر محرف تحكّم نادر عدّ
     * الحقول.
     */
    fun unpackLogRows(packed: String, expected: Int): List<LogRow>? {
        if (expected <= 0) return if (packed.isEmpty()) emptyList() else null
        val rows = packed.split('\n')
        if (rows.size != expected) return null
        return rows.map { row ->
            when (row.firstOrNull()) {
                'S' -> LogRow('S', emptyList())
                'F' -> LogRow('F', emptyList())
                'P' -> LogRow(
                    'P',
                    row.drop(1).removePrefix(FIELD.toString())
                        .split(FIELD)
                        .map { it.replace(ESCAPED_FIELD, FIELD).replace(ESCAPED_ESCAPE, ESCAPED_FIELD) },
                )
                else -> LogRow('S', emptyList())
            }
        }
    }

    /** الفاصل داخل السطر الواحد — مطابق لـ`logparse::FIELD`. */
    const val FIELD: Char = '\u0001'
    private const val ESCAPED_FIELD: Char = '\u0002'
    private const val ESCAPED_ESCAPE: Char = '\u0003'

    /** أسماء مجلد من نصّ مرتّب: بلا فارغ وبلا تكرار (نفس عقد `listNames`). */
    fun unpackNames(packed: String): List<String> =
        packed.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
