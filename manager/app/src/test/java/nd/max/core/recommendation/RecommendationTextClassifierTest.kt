package nd.max.core.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد نصوص التوصيات ↔ الأفعال.
 *
 * النصوص هنا **نسخ حرفية** من مصادرها الفعلية:
 *  - القواعد الاحتياطية: ContextBridge.ruleBasedRecommendations (Kotlin)
 *  - المحرك الأصلي: contextual_engine.rs → generate_recommendations (Rust/JNI)
 *  - التوأم الرقمي: digital_twin.rs → analyze_behavior (عرض فقط)
 *
 * أي تعديل لصياغة نص في تلك المصادر **يجب** أن يمرّ هنا أيضًا — فشل هذا
 * الاختبار يعني أن نصًا ما سيُرمى بصمت (NoAction بلا قصد) أو أسوأ:
 * نصيحة استشارية ستتحول إلى إجراء يُطبَّق آليًا.
 *
 * هذا العقد أنتج حادثتين موثقتين قبل إنشائه (انظر اختبارات الانحدار):
 *  1. "ضبط التردد على الوضع المتوازن لتوفير الطاقة" كانت تصنَّف توفير
 *     طاقة (الفرع الأسبق) رغم أن معناها الصريح متوازن.
 *  2. "خفض مستوى الصوت قليلاً لتوفير الطاقة" — نصيحة صوت استشارية كانت
 *     ستصبح ApplyPowerSaveProfile وتُبدِّل الملف العام آليًا مع AI مفعّلًا.
 */
class RecommendationTextClassifierTest {

    private val c = RecommendationTextClassifier

    // ── القواعد الاحتياطية (ContextBridge.ruleBasedRecommendations) ──

    @Test
    fun `kotlin fallback - thermal warning maps to reduce frequency`() {
        assertEquals(
            RecommendationAction.ReduceCpuFrequency,
            c.classify("تحذير حراري (55°): يُنصح بخفض تردد المعالج فورًا")
        )
        assertEquals(
            RecommendationCategory.THERMAL,
            c.category("تحذير حراري (55°): يُنصح بخفض تردد المعالج فورًا")
        )
        assertEquals(1, c.priority("تحذير حراري (55°): يُنصح بخفض تردد المعالج فورًا"))
    }

    @Test
    fun `kotlin fallback - elevated temperature maps to reduce frequency`() {
        assertEquals(
            RecommendationAction.ReduceCpuFrequency,
            c.classify("حرارة مرتفعة (47°): يُنصح بخفض تردد المعالج لتبريد الجهاز")
        )
    }

    @Test
    fun `kotlin fallback - critical battery maps to suggest charging`() {
        assertEquals(
            RecommendationAction.SuggestCharging,
            c.classify("تحذير: البطارية منخفضة جدًا (8%)، شحن الجهاز قريبًا ضروري")
        )
    }

    @Test
    fun `kotlin fallback - low battery maps to power save profile`() {
        assertEquals(
            RecommendationAction.ApplyPowerSaveProfile,
            c.classify("بطارية منخفضة (15%): يُنصح بتفعيل ملف توفير الطاقة")
        )
    }

    @Test
    fun `kotlin fallback - high load maps to performance profile`() {
        assertEquals(
            RecommendationAction.ApplyPerformanceProfile,
            c.classify("حمل المعالج مرتفع (4.2): يُنصح بوضع الأداء لتحسين الاستجابة")
        )
    }

    @Test
    fun `kotlin fallback - very heavy load maps to gaming mode`() {
        // القاعدة المضافة لتكافؤ المسارين: الإجراء كان له منطق تنفيذ
        // كامل بلا أي مُنتِج في المسار الاحتياطي
        assertEquals(
            RecommendationAction.EnableGamingMode,
            c.classify("حمل ثقيل جدًا (4.5): يُنصح بتفعيل وضع الألعاب لتحسين الأداء")
        )
    }

    @Test
    fun `kotlin fallback - background drain maps to suggest closing apps`() {
        assertEquals(
            RecommendationAction.SuggestClosingApps,
            c.classify("نشاط خلفي أثناء الخمول (حمل 2.1): يُنصح بإيقاف التطبيقات غير المستخدمة")
        )
    }

    @Test
    fun `kotlin fallback - low load cool device maps to increase frequency`() {
        // المسار المتمم: لا شيء غير هذه القاعدة كان يحرر سقفًا خُفِّض سابقًا
        assertEquals(
            RecommendationAction.IncreaseCpuFrequency,
            c.classify("حمل المعالج منخفض والتبريد مستقر: يُنصح برفع تردد المعالج لاستعادة الاستجابة")
        )
    }

    // ── المحرك الأصلي (contextual_engine.rs → generate_recommendations) ──

    @Test
    fun `rust engine - gaming mode recommendation maps correctly`() {
        // صيغت "تفعيل وضع الألعاب" بدل "Performance Mode" السابقة التي
        // لم تكن تطابق فرع EnableGamingMode أصلًا
        assertEquals(
            RecommendationAction.EnableGamingMode,
            c.classify("تفعيل وضع الألعاب لتحسين الأداء")
        )
    }

    @Test
    fun `rust engine - gaming thermal throttle maps to reduce frequency`() {
        assertEquals(
            RecommendationAction.ReduceCpuFrequency,
            c.classify("خفض تردد المعالج قليلاً لتقليل الحرارة ومنع الخنق الحراري")
        )
    }

    @Test
    fun `rust engine - low battery while gaming maps to suggest charging`() {
        assertEquals(
            RecommendationAction.SuggestCharging,
            c.classify("البطارية منخفضة، يُنصح بتوصيل الشاحن")
        )
    }

    @Test
    fun `rust engine - balanced during media maps to balanced not power save`() {
        // اختبار انحدار للحادثة 1: النص القديم كان "...المتوازن لتوفير
        // الطاقة" فيصنَّف توفير طاقة (الفرع الأسبق في when) خلافًا
        // لمعناه الصريح. النص الحالي لا يجمع الكلمتين.
        assertEquals(
            RecommendationAction.ApplyBalancedProfile,
            c.classify("ضبط التردد على الوضع المتوازن أثناء تشغيل الوسائط")
        )
    }

    @Test
    fun `rust engine - power save during video maps to power save`() {
        assertEquals(
            RecommendationAction.ApplyPowerSaveProfile,
            c.classify("تفعيل وضع توفير الطاقة لتقليل الاستهلاك أثناء الفيديو")
        )
    }

    @Test
    fun `rust engine - audio advice is advisory only`() {
        // اختبار انحدار للحادثة 2: "خفض مستوى الصوت قليلاً لتوفير
        // الطاقة" كانت ستُطبِّق تبديل الملف العام آليًا مع AI مفعّل —
        // نصيحة صوت لا علاقة لها بملفات الطاقة.
        assertEquals(
            RecommendationAction.NoAction,
            c.classify("خفض مستوى الصوت قليلاً لحماية السمع")
        )
    }

    @Test
    fun `rust engine - browsing low load maps to reduce frequency`() {
        assertEquals(
            RecommendationAction.ReduceCpuFrequency,
            c.classify("الحمل منخفض، يُنصح بخفض التردد لتوفير البطارية")
        )
    }

    @Test
    fun `rust engine - idle maps to suggest closing apps`() {
        // "تعليق العمليات الخلفية" القديمة كانت لا تطابق شيئًا فتُرمى
        assertEquals(
            RecommendationAction.SuggestClosingApps,
            c.classify("الجهاز في وضع الخمول، يُنصح بإيقاف التطبيقات غير المستخدمة")
        )
    }

    @Test
    fun `rust engine - calling maps to balanced`() {
        assertEquals(
            RecommendationAction.ApplyBalancedProfile,
            c.classify("تحسين أولوية المكالمة عبر ضبط الوضع المتوازن")
        )
    }

    @Test
    fun `rust engine - charging with load maps to increase frequency not charging`() {
        // "رفع تردد" يُفحص قبل "شاحن" — توصية الشحن المصحوبة برفع التردد
        // تُنفَّذ كتحرير سقف لا كإيقاف للتنفيذ
        assertEquals(
            RecommendationAction.IncreaseCpuFrequency,
            c.classify("الجهاز متصل بالشاحن: يُنصح برفع تردد المعالج للاستفادة من الطاقة الخارجية")
        )
    }

    @Test
    fun `rust engine - extreme heat warning maps to closing apps`() {
        assertEquals(
            RecommendationAction.SuggestClosingApps,
            c.classify("تحذير: درجة الحرارة مرتفعة جداً، يُنصح بإيقاف تشغيل التطبيقات الثقيلة")
        )
        assertEquals(1, c.priority("تحذير: درجة الحرارة مرتفعة جداً، يُنصح بإيقاف تشغيل التطبيقات الثقيلة"))
    }

    // ── التوأم الرقمي (digital_twin.rs → analyze_behavior — عرض فقط) ──

    @Test
    fun `digital twin - high load and heat is advisory only`() {
        assertEquals(
            RecommendationAction.NoAction,
            c.classify("الحمل مرتفع جداً والحرارة مرتفعة. يُنصح بتخفيف الحمل أو تبريد الجهاز.")
        )
    }

    @Test
    fun `digital twin - low battery maps to power save`() {
        assertEquals(
            RecommendationAction.ApplyPowerSaveProfile,
            c.classify("البطارية منخفضة. يُنصح بتفعيل وضع توفير الطاقة.")
        )
    }

    @Test
    fun `digital twin - high memory maps to closing apps`() {
        assertEquals(
            RecommendationAction.SuggestClosingApps,
            c.classify("استهلاك الذاكرة مرتفع. يُنصح بإيقاف التطبيقات غير المستخدمة.")
        )
    }

    @Test
    fun `digital twin - gaming intent maps to increase frequency`() {
        assertEquals(
            RecommendationAction.IncreaseCpuFrequency,
            c.classify("تم الكشف عن نية ألعاب. يُنصح برفع التردد لتحسين الأداء.")
        )
    }

    // ── التشكيل والأتمتة ──

    @Test
    fun `diacritics are stripped before matching`() {
        // "يُنصح" (بضمة U+064F) يجب أن تطابق "ينصح" — الحشرة الأصلية
        // التي كسرت المطابقة كلها قبل إضافة التطبيع
        assertEquals("ينصح", c.stripArabicDiacritics("يُنصح"))
        assertEquals("قليلا", c.stripArabicDiacritics("قليلاً"))
        assertEquals(
            RecommendationAction.ApplyPowerSaveProfile,
            c.classify("يُنصح بتفعيل ملف توفير الطاقة") // synthetic
        )
    }

    @Test
    fun `only profile actions are automated`() {
        // الأتمتة مقصورة على تغيير الملفات — لا شيء جذري (إعادة تشغيل،
        // إغلاق تطبيقات) يجري دون موافقة صريحة
        assertTrue(c.isAutomated(RecommendationAction.ApplyBalancedProfile))
        assertTrue(c.isAutomated(RecommendationAction.ApplyPowerSaveProfile))
        assertTrue(c.isAutomated(RecommendationAction.ApplyPerformanceProfile))
        assertFalse(c.isAutomated(RecommendationAction.RebootDevice))
        assertFalse(c.isAutomated(RecommendationAction.SuggestClosingApps))
        assertFalse(c.isAutomated(RecommendationAction.EnableGamingMode))
        assertFalse(c.isAutomated(RecommendationAction.NoAction))
    }

    @Test
    fun `unknown text is no action with lowest urgency`() {
        assertEquals(RecommendationAction.NoAction, c.classify("نص عشوائي بلا كلمات مفتاحية")) // synthetic
        assertEquals(4, c.priority("نص عشوائي بلا كلمات مفتاحية")) // synthetic
        assertEquals(RecommendationCategory.GENERAL, c.category("نص عشوائي بلا كلمات مفتاحية")) // synthetic
    }
}
