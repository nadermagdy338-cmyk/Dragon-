/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **اكتشاف مؤثّر المصنّع**: قياسٌ يعمل بالـSDK العامّ وحده، ثمّ محاولةُ لمسٍ مقيسة.
 *
 * **وفصلُ المرحلتين هو جوهر التصميم:**
 *
 * 1. **الاكتشاف** — `AudioEffect.queryEffects()` (عامّة، بلا جذر، بلا جلسة، بلا أثر على الصوت):
 *    تُقرأ الجردة كما أعلنتها المنصّة، ويُترجم معرّف كل مؤثّر إلى عائلة إن عُرفت. **وهذه المرحلة
 *    لا تلمس شيئًا** — فالشاشة تعرف «هل Dolby على هذا الجهاز؟» بمجرّد فتحها.
 * 2. **المحاولة** — إرفاقٌ بالانعكاس ثمّ **تحريرٌ في اللحظة نفسها** ([`probeAttach`]): تُقاس إمكانيّة
 *    التحكّم فعلًا، ولا يبقى مؤثّرٌ معلّقًا على صوت المستخدم من مجرّد سؤال. وهي **خيارٌ صريح** لا
 *    يُنادى من كل فتحة شاشة.
 *
 * **ولا شيء هنا يُخزَّن:** لا مفاتيح ولا ملفّات، فترتيب القراءة هو المصدر الوحيد للحقيقة.
 */
package nd.max.core.audio

/**
 * لقطة الاكتشاف: الجردة كما قُرئت، وحكم الحضور المشتقّ منها.
 *
 * @param descriptors `null` = **لم تُقرأ الجردة** (ولا تُقرأ «فارغة»).
 */
data class VendorAudioSnapshot(
    val descriptors: List<VendorEffectDescriptor>?,
    val verdict: VendorAudioVerdict,
) {
    val detected: List<VendorEffectIdentity> get() = vendorEffectIdentities(descriptors)
}

/**
 * أثر محاولة اللمس: الحكم، ومعه **ما قُرء في اللحظة التي كنّا فيها مالكين**.
 *
 * @param dapEnabled قيمة `EFFECT_PARAM_ENABLE` كما قرأتها المادّة — و`null` حين لا تُقرأ.
 * @param profile رقم الملفّ الشخصيّ المقروء — و`null` كذلك.
 */
data class VendorAttachProbe(
    val evidence: VendorAttachEvidence,
    val dapEnabled: Int? = null,
    val profile: Int? = null,
)

object VendorAudioDiscovery {

    /** الجردة المُعلَنة من المنصّة — و`null` حين لا تُقرأ (لا صفرًا ولا قائمة مصنوعة). */
    fun descriptors(): List<VendorEffectDescriptor>? =
        AudioEffectProbe.effects()?.let { vendorDescriptorsOf(it) }

    /**
     * قياسٌ سلبيّ كامل: جردةٌ ثمّ حكم، **بلا أيّ محاولة إرفاق** — فلا يُلمس الصوت.
     *
     * @param config مؤثّرات المصنّع **المُعرَّفة في تهيئة النظام** ([`vendorConfigIdentities`] على
     *   وثيقة `audio_effects.xml`) — مصدرٌ ثانٍ يجيب «أين يوجد؟». والفارغة تعني «لم تُعرَّف هناك»
     *   أو «لم تُقرأ التهيئة» (قراءتها جذريّة) — ولا تعني «غير موجود».
     */
    fun snapshot(config: List<VendorEffectIdentity> = emptyList()): VendorAudioSnapshot {
        val descriptors = descriptors()
        return VendorAudioSnapshot(
            descriptors,
            vendorAudioVerdict(VendorAudioEvidence(descriptors, config = config)),
        )
    }

    /**
     * **محاولةُ اللمس المقيسة**: يجرّب الإرفاق بمعرّفٍ مكتشَف، يقرأ تحكّمه **وحالتيه المقروءتين**
     * (تمكين المعالج · الملفّ الشخصيّ)، **ثمّ يُحرّره في اللحظة نفسها** مهما حدث — ويُعيد الأثر وحده
     * (لا مقبضًا؛ فمن يريد الإبقاء يفتح بمساره الخاصّ).
     *
     * **ولماذا التحرير الفوريّ شرطٌ لا نيّة:** مؤثّرٌ يُبقي نفسه معلّقًا على المزج العامّ يغيّر صوت
     * المستخدم بعد مغادرة الشاشة — وهذا ممنوع في المستودع (شرط قبول `AQ-02`).
     *
     * **ولماذا تُقرأ الحالتان هنا لا في الشاشة:** القراءة تحتاج مقبضًا حيًّا، ولا نُبقي مؤثّرًا مفتوحًا
     * لمجرّد عرض رقم — فما يُقرأ يُقرأ في اللحظة التي نكون فيها مالكين، ثمّ يُفلَت. والكتابة وحدها
     * (طلب المستخدم) تُبقي الجلسة.
     */
    fun probeAttach(effectUuid: String, session: Int = VendorAudioAdapter.GLOBAL_SESSION): VendorAttachProbe {
        val attempt = VendorAudioAdapter.attach(effectUuid, session)
        val handle = attempt.handle ?: return VendorAttachProbe(attempt.evidence)
        return try {
            VendorAttachProbe(
                evidence = attempt.evidence,
                dapEnabled = VendorAudioAdapter.readIntParam(handle, DolbyDapProtocol.ENABLE_PARAM),
                profile = VendorAudioAdapter.readIntParam(handle, DolbyDapProtocol.PROFILE_PARAM),
            )
        } finally {
            handle.release()
        }
    }
}
