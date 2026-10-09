/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

/**
 * بصمة إعداد التطبيق **من غير مقابض سقف الـGPU**.
 *
 * لماذا وُجدت
 * -----------
 * تغيير بروفايل الـGPU لتطبيق في المقدّمة كان يمرّ بتراجع شامل ثم تطبيق شامل (عشرات الكتابات
 * وعمليات shell) لتغيير مقبض واحد. المسار السريع في `AppMonitor` يحتاج أن يعرف: هل تغيّر شيء
 * **غير** سقف الـGPU؟ وهذه الدالة تجيب بمقارنة نصّين: بصمة وقت التطبيق، وبصمة الآن.
 *
 * وهي نقيّة (نصّ داخل ونصّ خارج) كي تُختبر بلا جهاز: كتلة التطبيق تُستخرج بنفس نمط
 * `readAppConfigField`، ثم تُحذف منها المفاتيح الثلاثة التي تخصّ سقف الـGPU وحدها، ويُطبَّع ما
 * بقي (مسافات وفواصل) فلا تُفرّق بين غياب مفتاح وحضوره بقيمته الافتراضية في الترتيب نفسه.
 */
object PerAppConfigFingerprint {

    /** المفاتيح التي يملكها مقبض سقف الـGPU: بروفايل، وتردد صريح، واسم قديم للبروفايل. */
    private val gpuCeilingFields =
        Regex(""""(?:gpu_profile|gpu_max_freq|thermal_profile)"\s*:\s*(?:"[^"]*"|[^,}\s]+)""")

    /**
     * @return البصمة، أو `null` إن لم يوجد إدخال للحزمة (فلا مسار سريع لما لا إدخال له).
     */
    fun withoutGpuCeiling(json: String?, packageName: String): String? {
        if (json.isNullOrEmpty() || packageName.isBlank()) return null
        val block = Regex(""""${Regex.escape(packageName)}"\s*:\s*\{([^}]+)\}""")
            .find(json)?.groupValues?.getOrNull(1) ?: return null
        return gpuCeilingFields.replace(block, "")
            .filterNot(Char::isWhitespace)
            .replace(Regex(",{2,}"), ",")
            .trim(',')
    }
}
