/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import nd.max.core.hardware.DeviceStateCollector.DeviceSnapshot

/**
 * الهدف لا الوضع (قرار #2).
 *
 * Performance / Balanced / Eco لم تعد ملفات ثابتة، بل ثلاثة أوزان
 * مقيسة يوازن بينها المخطِّط. هذا ما يسمح بـ«توفير خفيف» بدل قفزة
 * كاملة إلى ملف Eco، ويجعل الشاشة المطفأة هدفًا مختلفًا (طاقة+حرارة)
 * لا مجرد "إيقاف" (قرار #11).
 */
data class Objective(
    /** وزن الأداء: كم يهم رفع السقوف والاستجابة. */
    val performance: Float,
    /** وزن البطارية: كم يهم خفض الاستهلاك. */
    val battery: Float,
    /** وزن الهامش الحراري: كم يهم إبقاء الحرارة بعيدة عن السقف. */
    val thermalHeadroom: Float,
) {
    /**
     * درجة الحالة تحت هذا الهدف: أعلى = أقرب لما يريده المستخدم.
     * كل حد يقيس شيئًا فعليًا من اللقطة ويوزن بأولوية المستخدم — فلا
     * يتدخل العقل إلا حين تنخفض هذه الدرجة عن رضا مقيس (فجوة حقيقية).
     */
    fun score(s: DeviceSnapshot): Float {
        val perfTerm = (1f - s.cpuLoad) * performance
        val batteryTerm = s.battery * battery
        val thermalTerm = (1f - s.thermal.coerceIn(0f, 1f)) * thermalHeadroom
        val total = performance + battery + thermalHeadroom
        if (total <= 0f) return 0f
        return ((perfTerm + batteryTerm + thermalTerm) / total).coerceIn(0f, 1f)
    }

    /** الاتجاه الذي يخدم الهدف الآن: رفع الأداء أم توفير الطاقة. */
    fun preferredDirection(s: DeviceSnapshot): ControlRegistry.Direction {
        // حِمل مرتفع وأولوية أداء ⇒ ارفع؛ غير ذلك (حرارة/بطارية) ⇒ وفّر.
        val wantsPerformance = performance >= battery + thermalHeadroom &&
            s.cpuLoad > 0.7f && s.thermal < 0.45f
        return if (wantsPerformance) ControlRegistry.Direction.RAISE_PERFORMANCE
        else ControlRegistry.Direction.SAVE_ENERGY
    }

    companion object {
        /** أوزان مسبقة تُترجم عنها أزرار الأداء/التوازن/التوفير في الواجهة. */
        val PERFORMANCE = Objective(performance = 0.7f, battery = 0.1f, thermalHeadroom = 0.2f)
        val BALANCED = Objective(performance = 0.4f, battery = 0.3f, thermalHeadroom = 0.3f)
        val ECO = Objective(performance = 0.15f, battery = 0.7f, thermalHeadroom = 0.15f)

        /** هدف الشاشة المطفأة: طاقة وحرارة فقط، بلا أداء (قرار #11). */
        val SCREEN_OFF = Objective(performance = 0f, battery = 0.7f, thermalHeadroom = 0.3f)

        fun fromLegacyProfile(profileId: String?): Objective = when (profileId) {
            "1" -> PERFORMANCE
            "3" -> ECO
            else -> BALANCED
        }

        /**
         * تفضيل المستخدم الصريح (قرار #10): يُسأل مرة عند التشغيل.
         * يتقدم على الملف الحالي لأن نية المستخدم أصدق من وضع ورثناه.
         */
        fun fromPreference(preference: String?): Objective = when (preference) {
            "performance" -> PERFORMANCE
            "battery" -> ECO
            "balanced" -> BALANCED
            else -> BALANCED
        }

        /** القيمة المعاكسة: تسمية Prop من كائن هدف. */
        fun labelFor(objective: Objective): String = when {
            objective.performance >= objective.battery + objective.thermalHeadroom -> "performance"
            objective.battery >= objective.performance + objective.thermalHeadroom -> "battery"
            else -> "balanced"
        }
    }
}
