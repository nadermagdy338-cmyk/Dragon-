/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حكم الكتابة — **صافٍ ومُختبَر على الـJVM** (شرط قبول `AU-03`).
 *
 * **ولماذا يستحقّ اختبارًا وهو أربعة أسطر:** الشرط المقيس هو ألّا تُكتب `applied=true` كاذبة.
 * والأسطر الأربعة هي التي تقرّر متى تُقال، فلا يجوز أن تقرّرها الشاشة — الشاشة لا تُقاس، وهذه
 * تُقاس.
 *
 * **والعطب الواقع الذي يمنعه ترتيب الفروع:** كتابةٌ منعتها «لا تزعج» يردّها الـarbiter
 * `blocked=true` **مع** خطأ، فالفرع الذي يفحص «لم تُطبَّق» أوّلًا يقول `FAILED` — فيَظنّ المستخدم
 * أنّ الشاشة معطوبة لا أنّ المنصّة رفضت. فالفحص `blocked` قبل `failed`، وهذا ما يُثبته الاختبار.
 */
class AudioStreamVerdictTest {

    @Test
    fun `a write that never happened is not attempted, and its reason is kept`() {
        val verdict = audioWriteVerdict(
            attempted = false,
            applied = false,
            verified = false,
            blocked = false,
            error = "control-store-unconfigured",
            liveLevel = null,
        )

        assertEquals(AudioWriteOutcome.NOT_ATTEMPTED, verdict.outcome)
        assertEquals("control-store-unconfigured", verdict.reason)
        assertNull("غياب القراءة لا صفر", verdict.liveLevel)
        assertFalse(verdict.isApplied)
    }

    @Test
    fun `blocked is decided before failed, so a platform refusal is not read as a broken screen`() {
        val verdict = audioWriteVerdict(
            attempted = true,
            applied = false,
            verified = false,
            blocked = true,
            error = "manual-lock",
            liveLevel = 7,
        )

        assertEquals(AudioWriteOutcome.BLOCKED, verdict.outcome)
        assertEquals("manual-lock", verdict.reason)
        assertEquals(7, verdict.liveLevel)
        assertFalse(verdict.isApplied)
    }

    @Test
    fun `applied needs the write and the read-back together`() {
        val applied = audioWriteVerdict(
            attempted = true,
            applied = true,
            verified = true,
            blocked = false,
            error = null,
            liveLevel = 9,
        )
        assertEquals(AudioWriteOutcome.APPLIED, applied.outcome)
        assertTrue(applied.isApplied)
        // ونجاحٌ بلا سبب يُكتب: لا سطر «لماذا» حيث لا شيء يُفسَّر.
        assertNull(applied.reason)

        // وكُتبت ولم تُقرأ مطابقة ⇒ **فشل** بسبب مكتوب، لا نجاح.
        val unverified = audioWriteVerdict(
            attempted = true,
            applied = true,
            verified = false,
            blocked = false,
            error = "apply-not-verified-baseline-restored",
            liveLevel = 5,
        )
        assertEquals(AudioWriteOutcome.FAILED, unverified.outcome)
        assertEquals("apply-not-verified-baseline-restored", unverified.reason)
        assertFalse(unverified.isApplied)

        // ومحاولةٌ لم تُطبَّق ولا أحد حجبها ⇒ فشل أيضًا، لا «لم تُحاول».
        val refused = audioWriteVerdict(
            attempted = true,
            applied = false,
            verified = false,
            blocked = false,
            error = null,
            liveLevel = null,
        )
        assertEquals(AudioWriteOutcome.FAILED, refused.outcome)
    }

    @Test
    fun `the default verdict carries no reason and no reading`() {
        val verdict = AudioWriteVerdict(AudioWriteOutcome.NOT_ATTEMPTED)

        assertNull(verdict.reason)
        assertNull(verdict.liveLevel)
        assertFalse(verdict.isApplied)
    }
}
