/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

/**
 * اختيار واحد لكل مقبض في شاشة الحكام.
 *
 * لماذا وُجد: كانت الشاشة تعرض **ثلاثة** قوائم لكل مقبض (متوازن/أداء/توفير)، فيقرأ
 * المستخدم ثلاثة أرقام لحقيقة واحدة، ويسأل: أيّها الساري الآن؟ والقيمة المعروضة هنا
 * تأتي من **النواة** (`scaling_governor` · قوسان في عقدة الجدولة · عقدة `governor`
 * لـmali)، فلا يعني «المُختار» إلا ما يقرؤه النظام فعلًا — وهذا هو نفس شرط ADR-07
 * في الواجهة: لا عرض بلا قراءة.
 *
 * والدالّتان خالصتان (بلا أندرويد وبلا جذر) لتُقاسا باختبار وحدة — ومسار «ماذا يعرض
 * الصف» هو أوّل ما يفسد صامتًا حين تتغيّر صيغة عقدة.
 */
object HardwareChoice {

    /**
     * القيمة السارية داخل قوسَي عقدة الجدولة: `none [cfq] mq-deadline` ← `cfq`.
     *
     * و`null` تعني «لا قوسان» أو «قوسان فارغان» — وهي «لا جواب» لا «القيمة الأولى»،
     * فالقارئ يستعمل بديله المصرَّح بدل أن يُخمّن.
     */
    fun bracketed(text: String?): String? {
        val source = text ?: return null
        val open = source.indexOf('[')
        if (open < 0) return null
        val close = source.indexOf(']', open + 1)
        if (close <= open + 1) return null
        return source.substring(open + 1, close).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * فهرس القيمة المعروضة: الحيّة أولًا، ثم أول بديل غير فارغ معروف في القائمة، ثم 0.
     *
     * والمطابقة بحالة الأحرف غير مهمّة: أسماء الحكام تُكتب في العقد صغيرة، ويمكن أن
     * تُكتب في خصيصة الوحدة بحالة أخرى — والفهرس يجب أن يجدها لا أن يرجع إلى الصفر.
     * ولماذا 0 أخيرًا: قائمة غير فارغة واختيار بلا قيمة يعني أن الجدول تغيّر تحتنا؛
     * وعرض أول عنصر (وهو الساري في أكثر الأجهزة) أصدق من مؤشر وهمي على لا شيء.
     */
    fun indexOf(available: List<String>, live: String?, vararg fallbacks: String?): Int {
        if (available.isEmpty()) return 0
        val candidates = buildList {
            add(live)
            fallbacks.forEach { add(it) }
        }
        for (candidate in candidates) {
            val value = candidate?.trim().orEmpty()
            if (value.isEmpty()) continue
            val found = available.indexOfFirst { it.equals(value, ignoreCase = true) }
            if (found >= 0) return found
        }
        return 0
    }
}
