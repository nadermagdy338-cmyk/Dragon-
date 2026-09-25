/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.jni

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * جسر JNI لمحرك السياق المكتوب بلغة Rust (contextual_engine.rs).
 *
 * يولّد توصيات قابلة للتنفيذ من السياق المقيس. التصنيف الداخلي
 * (UsageMode) يبقى داخل Rust — المواصفة: التنبؤ والتصنيف داخليان
 * يقودان إجراءً لا عرضًا، لذا لا يوجد سطح JNI لهما.
 *
 * المكتبة الأصلية (libmaxmanager_native.so) تُبنى في CI من
 * manager/src/main/rust وتُحقن في jniLibs قبل بناء APK، لكن يبقى
 * التحمل الآمن إلزاميًا: لا انهيار إذا غابت. عند غياب المكتبة
 * (أو فشل استدعائها) يعمل بديل Kotlin حقيقي: توصيات مولدة من قواعد
 * فوق القياسات الفعلية (حرارة/بطارية/حمل) بنفس الصياغة التي يفهمها
 * RecommendationTextClassifier الذي يغذي محرك MAX AI.
 */
object ContextBridge {

    /** هل تحميل libmaxmanager_native.so نجح؟ يُحسب مرة واحدة فقط. */
    val nativeAvailable: Boolean = runCatching {
        System.loadLibrary("maxmanager_native")
    }.isSuccess

    private external fun nativeGenerateRecommendations(
        foregroundPackage: String,
        isScreenOn: Boolean,
        ambientLight: Float,
        audioVolume: Int,
        isCharging: Boolean,
        batteryLevel: Int,
        cpuLoad: Float,
        thermalMax: Float
    ): Array<String>

    fun generateRecommendations(
        foregroundPackage: String,
        isScreenOn: Boolean,
        ambientLight: Float,
        audioVolume: Int,
        isCharging: Boolean,
        batteryLevel: Int,
        cpuLoad: Float,
        thermalMax: Float
    ): Array<String> =
        if (nativeAvailable) {
            runCatching {
                nativeGenerateRecommendations(
                    foregroundPackage, isScreenOn, ambientLight,
                    audioVolume, isCharging, batteryLevel,
                    cpuLoad, thermalMax
                )
            }.getOrDefault(
                ruleBasedRecommendations(isScreenOn, isCharging, batteryLevel, cpuLoad, thermalMax)
            )
        } else {
            ruleBasedRecommendations(isScreenOn, isCharging, batteryLevel, cpuLoad, thermalMax)
        }

    suspend fun generateRecommendationsAsync(
        foregroundPackage: String,
        isScreenOn: Boolean,
        ambientLight: Float,
        audioVolume: Int,
        isCharging: Boolean,
        batteryLevel: Int,
        cpuLoad: Float,
        thermalMax: Float
    ): Array<String> = withContext(Dispatchers.IO) {
        generateRecommendations(
            foregroundPackage, isScreenOn, ambientLight,
            audioVolume, isCharging, batteryLevel,
            cpuLoad, thermalMax
        )
    }

    /**
     * توصيات احتياطية مبنية على قواعد فوق القياسات الحقيقية
     * (loadavg من /proc، الحرارة من مناطق thermal، البطارية من بث
     * النظام). الصياغة تتطابق عمدًا مع الكلمات المفتاحية التي يفهمها
     * RecommendationTextClassifier حتى تتحول كل توصية إلى إجراء قابل
     * للتنفيذ فعليًا داخل محرك MAX AI.
     */
    private fun ruleBasedRecommendations(
        isScreenOn: Boolean,
        isCharging: Boolean,
        batteryLevel: Int,
        cpuLoad: Float,
        thermalMax: Float
    ): Array<String> {
        val recs = mutableListOf<String>()

        // حرارة مرتفعة: خفض التردد يقلل التسريب الحراري فعليًا
        if (thermalMax >= 50f) {
            recs += "تحذير حراري (${thermalMax.toInt()}°): يُنصح بخفض تردد المعالج فورًا"
        } else if (thermalMax >= 45f) {
            recs += "حرارة مرتفعة (${thermalMax.toInt()}°): يُنصح بخفض تردد المعالج لتبريد الجهاز"
        }

        // بطارية منخفضة: ملف توفير الطاقة / الشحن
        if (batteryLevel in 1..10 && !isCharging) {
            recs += "تحذير: البطارية منخفضة جدًا (${batteryLevel}%)، شحن الجهاز قريبًا ضروري"
        } else if (batteryLevel in 11..20 && !isCharging) {
            recs += "بطارية منخفضة (${batteryLevel}%): يُنصح بتفعيل ملف توفير الطاقة"
        }

        // حمل مرتفع (loadavg فوق 3.5): ملف الأداء يستجيب للحمل الفعلي
        if (cpuLoad > 3.5f && thermalMax < 45f) {
            recs += "حمل المعالج مرتفع (${String.format("%.1f", cpuLoad)}): يُنصح بوضع الأداء لتحسين الاستجابة"
        }

        // حمل ثقيل جدًا بلا حرارة: وضع الألعاب (أداء + cpufreq boost)
        // — تكافؤًا مع مسار المحرك الأصلي الذي يولّد توصية الألعاب
        // من تصنيف GAMING
        if (cpuLoad > 4f && thermalMax < 45f) {
            recs += "حمل ثقيل جدًا (${String.format("%.1f", cpuLoad)}): يُنصح بتفعيل وضع الألعاب لتحسين الأداء"
        }

        // استنزاف خلفي: الشاشة مطفأة لكن الحمل لم يهدأ
        if (!isScreenOn && cpuLoad > 1.5f) {
            recs += "نشاط خلفي أثناء الخمول (حمل ${String.format("%.1f", cpuLoad)}): يُنصح بإيقاف التطبيقات غير المستخدمة"
        }

        // المسار المتمم: خُفِّض السقف سابقًا (حرارة مثلًا) ثم تحسّن الوضع —
        // بدون هذه القاعدة لم يكن لأي توصية قاعدية أن تحرر السقف مجددًا
        if (cpuLoad < 0.8f && thermalMax < 43f) {
            recs += "حمل المعالج منخفض والتبريد مستقر: يُنصح برفع تردد المعالج لاستعادة الاستجابة"
        }

        return recs.toTypedArray()
    }
}
