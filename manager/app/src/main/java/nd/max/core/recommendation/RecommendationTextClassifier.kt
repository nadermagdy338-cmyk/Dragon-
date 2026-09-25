/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.recommendation

/**
 * مصنِّف نصوص التوصيات → أفعال قابلة للتنفيذ.
 *
 * منطق **نقي** بلا أي تبعية أندرويد، قابل للاختبار الوحدوي
 * (RecommendationTextClassifierTest).
 *
 * هذا العقد هو أكثر موضع أنتج حشرات في المشروع: نصوص التوصيات تأتي من
 * مسارين (محرك Rust الأصلي عبر JNI، والقواعد الاحتياطية في
 * ContextBridge.ruleBasedRecommendations) وتحوَّل إلى أفعال بمطابقة
 * كلمات مفتاحية عربية. أي انزياح صياغة في مصدر النصوص دون تحديث
 * المطابقة (أو العكس) ينتج: توصية تُرمى بصمت (NoAction)، أو أسوأ —
 * نصيحة استشارية تتحول إلى إجراء يُطبَّق آليًا (راجع الحوادث
 * الموثقة في الاختبارات).
 *
 * عند تعديل نصوص contextual_engine.rs أو ContextBridge.kt يجب تحديث
 * الاختبار المرافق — الاختبار هو الضامن الوحيد لهذا العقد.
 */
object RecommendationTextClassifier {

    /** يزيل التشكيل العربي (فتحة/ضمة/كسرة/شدة/سكون...) قبل المطابقة. */
    fun stripArabicDiacritics(text: String): String =
        text.replace(Regex("[\\u064B-\\u0652]"), "")

    /**
     * يحوّل نص توصية خامًا إلى إجراء قابل للتنفيذ.
     *
     * ترتيب الفروع **دلالي وليس اعتباطيًا** (المطابقة الأولى تفوز):
     * - "رفع/خفض تردد" قبل "شاحن": توصية الشحن المصحوبة برفع التردد
     *   تُنفَّذ كتحرير سقف لا كإيقاف للتنفيذ.
     * - "وضع الأداء"/"توفير الطاقة" قبل "متوازن": أي نص يجمع بين
     *   متوازن وأحد الوضعين يصنَّف بالوضع الأكثر تحديدًا — لذلك
     *   صيغت نصوص "متوازن" في المصادر بلا "توفير الطاقة" في الجملة
     *   نفسها.
     */
    fun classify(text: String): RecommendationAction {
        val n = stripArabicDiacritics(text)
        return when {
            n.contains("رفع تردد") || n.contains("رفع التردد") -> RecommendationAction.IncreaseCpuFrequency
            n.contains("خفض تردد") || n.contains("خفض التردد") -> RecommendationAction.ReduceCpuFrequency
            n.contains("وضع الأداء") || n.contains("Performance") -> RecommendationAction.ApplyPerformanceProfile
            n.contains("توفير الطاقة") || n.contains("Power Save") -> RecommendationAction.ApplyPowerSaveProfile
            n.contains("متوازن") || n.contains("Balanced") -> RecommendationAction.ApplyBalancedProfile
            n.contains("ألعاب") || n.contains("Gaming") -> RecommendationAction.EnableGamingMode
            n.contains("شاحن") || n.contains("شحن") -> RecommendationAction.SuggestCharging
            n.contains("إيقاف") && n.contains("التطبيقات") -> RecommendationAction.SuggestClosingApps
            n.contains("إعادة التشغيل") -> RecommendationAction.RebootDevice
            else -> RecommendationAction.NoAction
        }
    }

    /** تصنيف العرض (لون/أيقونة البطاقة في الواجهة). */
    fun category(text: String): RecommendationCategory {
        val n = stripArabicDiacritics(text)
        return when {
            n.contains("حرارة") || n.contains("حراري") -> RecommendationCategory.THERMAL
            n.contains("بطارية") || n.contains("طاقة") -> RecommendationCategory.BATTERY
            n.contains("أداء") || n.contains("تردد") -> RecommendationCategory.PERFORMANCE
            n.contains("أمان") || n.contains("حماية") -> RecommendationCategory.SECURITY
            else -> RecommendationCategory.GENERAL
        }
    }

    /** أولوية العرض: 1 = عاجل جدًا، 4 = إعلامي. */
    fun priority(text: String): Int {
        val n = stripArabicDiacritics(text)
        return when {
            n.contains("تحذير") -> 1
            n.contains("عاجل") -> 2
            n.contains("ينصح") -> 3
            else -> 4
        }
    }

    /** الإجراءات المسموح تنفيذها آليًا (تغيير ملفات فقط — لا شيء جذريًا). */
    fun isAutomated(action: RecommendationAction): Boolean =
        action is RecommendationAction.ApplyBalancedProfile ||
            action is RecommendationAction.ApplyPowerSaveProfile ||
            action is RecommendationAction.ApplyPerformanceProfile
}
