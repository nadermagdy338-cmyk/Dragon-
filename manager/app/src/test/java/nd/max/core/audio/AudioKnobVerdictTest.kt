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
 * حكم المقبض وقاعدة الإرفاق — **مقيسان على JVM**.
 *
 * **وما يُقاس هنا بالضبط:** أنّ «محجوب» تُفحص **قبل** «لم يُطبَّق» (فالقفل اليدويّ ليس فشلًا عامًّا)،
 * وأنّ `applied` بلا قراءة مطابقة **فشل** لا نجاح، وأنّ «لم تُجرَّب» تحمل سببها، وأنّ محاولة الإرفاق
 * تُسمح للقابل للكتابة **ولمن يحتاج محوّلًا** — وما لم يُقس (`null`) أو نُفي (`unavailable`) لا يُجرَّب.
 */
class AudioKnobVerdictTest {

    @Test
    fun `not attempted wins over every other outcome`() {
        val verdict = audioKnobVerdict(
            attempted = false, blocked = true, applied = true, verified = true,
            expected = "5", actual = "5", error = "whatever",
        )
        assertEquals(AudioWriteOutcome.NOT_ATTEMPTED, verdict.outcome)
        assertFalse(verdict.isApplied)
    }

    @Test
    fun `blocked is reported before a failed apply`() {
        val verdict = audioKnobVerdict(
            attempted = true, blocked = true, applied = false, verified = false,
            expected = "5", actual = "3", error = "manual-lock",
        )
        assertEquals(AudioWriteOutcome.BLOCKED, verdict.outcome)
        assertEquals("manual-lock", verdict.reason)
        assertEquals("3", verdict.actual)
    }

    @Test
    fun `applied requires a verified read back`() {
        assertEquals(
            AudioWriteOutcome.APPLIED,
            audioKnobVerdict(true, false, applied = true, verified = true, expected = "3", actual = "3", error = null)
                .outcome,
        )
        // وما كُتب ولم يُقرأ مطابقًا **فشل** — ولا تُكتب `applied=true` كاذبة.
        val unverified = audioKnobVerdict(
            true, false, applied = true, verified = false, expected = "3", actual = "0",
            error = "apply-not-verified-baseline-restored",
        )
        assertEquals(AudioWriteOutcome.FAILED, unverified.outcome)
        assertEquals("apply-not-verified-baseline-restored", unverified.reason)
    }

    @Test
    fun `a knob that was never tried still carries its reason`() {
        val verdict = audioKnobNotAttempted(AudioEffectReason.CONTROL_NOT_OWNED, expected = "500", actual = "120")
        assertEquals(AudioWriteOutcome.NOT_ATTEMPTED, verdict.outcome)
        assertEquals(AudioEffectReason.CONTROL_NOT_OWNED, verdict.reason)
        assertEquals("500", verdict.expected)
        assertEquals("120", verdict.actual)
    }

    private fun verdicts(vararg pairs: Pair<AudioFeature, AudioSupport>) = pairs.map { (feature, support) ->
        AudioFeatureVerdict(feature, support, "test")
    }

    @Test
    fun `writable and needs-adapter may be attempted, nothing else`() {
        val writable = verdicts(AudioFeature.EQUALIZER to AudioSupport.WRITABLE)
        val adapter = verdicts(AudioFeature.EQUALIZER to AudioSupport.NEEDS_ADAPTER)
        val unavailable = verdicts(AudioFeature.EQUALIZER to AudioSupport.UNAVAILABLE)
        val unknown = verdicts(AudioFeature.EQUALIZER to AudioSupport.UNKNOWN)
        val readOnly = verdicts(AudioFeature.EQUALIZER to AudioSupport.READ_ONLY)

        assertTrue(audioEffectAttachable(writable, AudioEffectKind.EQUALIZER))
        assertTrue(audioEffectAttachable(adapter, AudioEffectKind.EQUALIZER))
        assertFalse(audioEffectAttachable(unavailable, AudioEffectKind.EQUALIZER))
        assertFalse(audioEffectAttachable(unknown, AudioEffectKind.EQUALIZER))
        // والقراءة فقط ليست كتابةً — فلا يُبنى مقبض عليها.
        assertFalse(audioEffectAttachable(readOnly, AudioEffectKind.EQUALIZER))
        // ولم تُقس بعد ⇒ لا محاولة.
        assertFalse(audioEffectAttachable(null, AudioEffectKind.EQUALIZER))
    }

    @Test
    fun `each effect kind reads its own verdict row`() {
        val rows = verdicts(
            AudioFeature.EQUALIZER to AudioSupport.WRITABLE,
            AudioFeature.DYNAMICS_PROCESSING to AudioSupport.NEEDS_ADAPTER,
        )
        assertEquals(AudioSupport.WRITABLE, audioEffectSupport(rows, AudioEffectKind.EQUALIZER)?.support)
        assertEquals(AudioSupport.NEEDS_ADAPTER, audioEffectSupport(rows, AudioEffectKind.DYNAMICS)?.support)
        // ونوعٌ لا صفّ له ⇒ `null` ولا حكم مصنوع.
        assertEquals(null, audioEffectSupport(rows, AudioEffectKind.BASS_BOOST))
        assertEquals(null, audioEffectSupport(null, AudioEffectKind.BASS_BOOST))
    }

    @Test
    fun `effect tokens match the capability vocabulary literally`() {
        AudioEffectKind.entries.forEach { kind ->
            assertEquals(kind, AudioEffectKind.ofToken(kind.token))
            assertEquals(kind.feature.token, kind.token)
        }
        assertEquals(null, AudioEffectKind.ofToken("not_a_kind"))
    }

    // ─────────────── ‏٣ح-أ (تكملة ٢٤٣): **حالة التحكّم المُعلَنة** — الحالتان التي لا تُقاس بالتخمين ───────────────

    @Test
    fun `before the platform announces anything we neither claim nor deny control`() {
        val fresh = AudioEffectControlState()
        // **الجهل لا يمنع الكتابة:** مقبضٌ لم تُقَس ملكيّته لا يُقعد (والقياس الحيّ `hasControl()` هو
        // المرجع عند الكتابة) — وهو نفس مبدأ «الجهل ليس نفيًا».
        assertTrue(fresh.mayWrite)
        assertNull(fresh.blockReason)
        assertNull(fresh.disabledReason)
        assertFalse(fresh.disabledByEngine)
    }

    @Test
    fun `losing control blocks the write with the same token the screen shows`() {
        val lost = AudioEffectControlState().onControlStatus(false)
        assertFalse(lost.mayWrite)
        // **ورمزٌ واحد لا رمزان للشيء نفسه:** ما يُبحَث به هنا هو ما يُعرض في الشاشة بالحرف.
        assertEquals(AudioEffectReason.CONTROL_NOT_OWNED, lost.blockReason)
        // و«لا نملك» ليس «الجهاز عطّل»: السياقان يُفرَّقان فلا يُخبر المستخدم بسببين لعلّةٍ واحدة.
        assertNull(lost.disabledReason)
        // واستعادة التحكّم ترفع المنع
        assertTrue(lost.onControlStatus(true).mayWrite)
        assertNull(lost.onControlStatus(true).blockReason)
    }

    @Test
    fun `an effect we own but the device disabled is refused before the write, not after`() {
        // **وهذا هو العطب المسموع:** الكتابة تُقبل وتُقرأ مطابقةً ولا تُسمع — «مطبَّق» و«صفر تغيير».
        val muted = AudioEffectControlState().onEnableStatus(false)
        assertTrue(muted.mayWrite)
        assertNull(muted.blockReason)
        assertTrue(muted.disabledByEngine)
        assertEquals(AudioEffectReason.DISABLED_BY_ENGINE, muted.disabledReason)
        // وتعطيلٌ مع فقد التحكّم يُنسب إلى **فقد التحكّم** (الأسبق)، ولا يُخبَر بسببين متضادّين.
        val both = AudioEffectControlState().onEnableStatus(false).onControlStatus(false)
        assertEquals(AudioEffectReason.CONTROL_NOT_OWNED, both.blockReason)
        assertFalse(both.disabledByEngine)
        assertNull(both.disabledReason)
        // وتمكينٌ عادي لا يمنع شيئًا.
        val healthy = AudioEffectControlState().onControlStatus(true).onEnableStatus(true)
        assertTrue(healthy.mayWrite)
        assertNull(healthy.disabledReason)
        assertFalse(healthy.disabledByEngine)
    }
}
