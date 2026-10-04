/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * حكم كتابة مستوى دفق — **صافٍ وقابل للقياس على JVM**: يحوّل نتيجة المحكِّم إلى حكمٍ يُعرض.
 *
 * **ولماذا هو ملفٌّ لا سطران في الشاشة:** شرط قبول `AU-03` بالنصّ «كتابة يحجبها «لا تزعج» ⇒
 * `outcome=blocked` مع `reason` واضح، **لا `applied=true` كاذبة**». والحكم الذي يُكتب في الشاشة
 * لا يُقاس؛ وهذا يُقاس بلا منصّة.
 */
package nd.max.core.audio

/** حصيلة محاولة كتابة واحدة. ولا رابع لهذه الأربعة. */
enum class AudioWriteOutcome {
    /** كُتبت وقُرئت مطابقة — الدليل قراءة لا ادّعاء. */
    APPLIED,

    /** المنصّة لم تُنفّذ (أو منعها مالكٌ أعلى: «لا تزعج» · قفل يدويّ) — **ولا يُكتب «طُبِّق»**. */
    BLOCKED,

    /** حاولنا وكتبنا ولم تُقرأ المطابقة، فاستُرجعت الحالة الأولى. */
    FAILED,

    /** لم تُحاول: مخزن الملكيّة غير مهيّأ أو دفقٌ لا نعرفه — والمجهول يبقى مجهولًا (ADR-07). */
    NOT_ATTEMPTED,
}

/**
 * @param reason سببٌ مكتوب من المحكِّم (`manual-lock` · `apply-not-verified-baseline-restored` · …)
 *   أو `null` حين نجحت الكتابة.
 * @param liveLevel ما قُرئ **بعد** المحاولة — و`null` غياب قراءة لا صفر.
 */
data class AudioWriteVerdict(
    val outcome: AudioWriteOutcome,
    val reason: String? = null,
    val liveLevel: Int? = null,
) {
    val isApplied: Boolean get() = outcome == AudioWriteOutcome.APPLIED
}

/**
 * نتيجة المحكِّم ← حكم. **والترتيب مقصود:** «محجوب» تُفحص قبل «لم يُطبَّق»، لأن المحجوب هو
 * الحالة التي يُخفيها الصدق الأكثر: كتابةٌ مُنعها «لا تزعج» تُقرأ فشلًا عامًّا فيَظنّ المستخدم
 * أنّ الشاشة معطوبة لا أنّ المنصّة رفضت.
 */
fun audioWriteVerdict(
    attempted: Boolean,
    applied: Boolean,
    verified: Boolean,
    blocked: Boolean,
    error: String?,
    liveLevel: Int?,
): AudioWriteVerdict = when {
    !attempted -> AudioWriteVerdict(AudioWriteOutcome.NOT_ATTEMPTED, error, liveLevel)
    blocked -> AudioWriteVerdict(AudioWriteOutcome.BLOCKED, error, liveLevel)
    applied && verified -> AudioWriteVerdict(AudioWriteOutcome.APPLIED, null, liveLevel)
    else -> AudioWriteVerdict(AudioWriteOutcome.FAILED, error, liveLevel)
}
