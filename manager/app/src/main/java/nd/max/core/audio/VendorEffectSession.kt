/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **جلسة مؤثّر مصنّع مفتوحة** (تكملة ٢٣٠): الملفّ الذي **يُبقي** المقبض حيًّا بين نداءين.
 *
 * **ولماذا فصلُها عن المقبض ([`VendorEffectHandle`]):** المقبض يعرف الانعكاس، والجلسة تعرف **الهوية**
 * (‏معرّف التنفيذ) والتحرير. ومَن يحمل مقبضًا بلا هويّة لا يستطيع أن يُسمّي ما فتحه في السجلّ ولا أن
 * يبني مفتاح المقبض في المحكِّم.
 *
 * **وحدّها:** لا تحكم ولا تعرض — حالةٌ للقراءة وتحريرٌ صريح، والحكم كلّه في
 * [`VendorAudioBackend`]، والعرض في الشاشة. وهذا هو نفس الفصل المقصود في [`AudioEffectSession`].
 */
package nd.max.core.audio

/**
 * @param effectUuid معرّف التنفيذ الذي فُتح به — بأحرف صغيرة كما يُبنى به المفتاح.
 * @param handle المقبض الحيّ — `internal` فلا يُلمس من خارج `core/audio` (ADR-11: لا كتابة من `ui/`).
 */
class VendorEffectSession internal constructor(
    val effectUuid: String,
    internal val handle: VendorEffectHandle,
) {

    /**
     * هل التحكّم بأيدينا؟ — **ثلاثيّات لا ثنائيّات**: `null` تعني «لم يُقس»، و`false` نفيٌ مقيس.
     */
    val hasControl: Boolean? get() = handle.hasControl

    /** حالة التمكين كما تقولها المنصّة — و`null` حين لا تُقرأ. */
    val enabled: Boolean? get() = handle.enabled

    /** معرّف المؤثّر — يُسجَّل في التدقيق ولا يُعرض للمستخدم. */
    val id: Int get() = handle.id

    /** تحرير المؤثّر — **الشرط الذي بدونه يبقى أثرُنا على صوت المستخدم بعد مغادرة الشاشة**. */
    fun release() {
        handle.release()
    }
}
