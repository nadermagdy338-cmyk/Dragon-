/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * قياس نواة الأنماط (`AudioSoundPreset`) — **صافٍ، بلا أندرويد**.
 *
 * والقاعدة التي وُجد من أجلها هذا الملفّ: كل رقمٍ في جدول الأنماط يُقاس قبل أن يصل إلى جهاز أحد.
 * فما يُختبَر هنا ليس «هل الكود يعمل» بل **هل القرار صحيح**: هل كل نمط يحمي الكلام؟ هل كل رفعٍ
 * يُعوَّض؟ هل يُعرض زرٌّ على ميزةٍ لم تُقس؟
 */
package nd.max.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSoundPresetTest {

    // ─────────────────────────── ① جدول الأنماط: كل نمط مكتمل وقيمه في حدّها ① ───────────────────────────

    @Test
    fun `كل نمط له قيم صالحة والبنية ترفض ما يخرج عن الحد`() {
        AudioSoundPreset.entries.forEach { preset ->
            val values = audioPresetValues(preset)
            assertTrue("$preset: bassPercent", values.bassPercent in 0..100)
            assertTrue("$preset: virtualizerPercent", values.virtualizerPercent in 0..100)
            assertTrue("$preset: loudnessMb", values.loudnessMb >= 0)
            assertTrue("$preset: preAmpDb", values.preAmpDb in -24..12)
            // الإيقاف بجدولٍ فارغ عن قصد (لا يُكتب شيء)، والباقي بجدولٍ سباعيّ كامل.
            if (preset != AudioSoundPreset.OFF) {
                assertTrue("$preset: جدول EQ", values.eqGainsDb.size >= 5)
            }
        }
        // والبنية نفسها تحرس: قيمةٌ خارج الحدّ تُرفض عند البناء لا عند الكتابة على الجهاز.
        assertThrowsIllegalArgument { AudioPresetValues(101, 0, 0, emptyList(), 0) }
        assertThrowsIllegalArgument { AudioPresetValues(0, -1, 0, emptyList(), 0) }
        assertThrowsIllegalArgument { AudioPresetValues(0, 0, -5, emptyList(), 0) }
        assertThrowsIllegalArgument { AudioPresetValues(0, 0, 0, emptyList(), -99) }
    }

    @Test
    fun `الإيقاف هو الأصفار وحدها — وهو النمط الوحيد الصامت`() {
        assertTrue(audioPresetValues(AudioSoundPreset.OFF).isSilent)
        AudioSoundPreset.buttons.forEach { preset ->
            assertFalse("$preset يجب ألّا يكون صامتًا", audioPresetValues(preset).isSilent)
        }
    }

    @Test
    fun `الأزرار تُعرض بلا الإيقاف — والإيقاف مفتاح مستقلّ`() {
        assertFalse(AudioSoundPreset.OFF in AudioSoundPreset.buttons)
        assertEquals(AudioSoundPreset.entries.size - 1, AudioSoundPreset.buttons.size)
    }

    // ─────────────────────────── ② حماية الكلام: القاعدة الأهمّ ② ───────────────────────────

    /** Table shape regression only; speech intelligibility requires measurement on a device. */
    @Test
    fun `لا نمط يرفع الجهير ويُخفّض الوسط معًا`() {
        val bassy = listOf(
            AudioSoundPreset.BASS, AudioSoundPreset.MOVIE, AudioSoundPreset.GAME,
            AudioSoundPreset.WIDE,
        )
        bassy.forEach { preset ->
            val gains = audioPresetValues(preset).eqGainsDb
            assertFalse(
                "$preset: يُخفّض نطاق الكلام ⇒ الجهير سيخنق الصوت",
                eqLowersSpeechRange(gains),
            )
        }
    }

    @Test
    fun `النمط الذي يرفع الجهير يرفع الوسط أيضًا — لا محايدة`() {
        listOf(AudioSoundPreset.BASS, AudioSoundPreset.MOVIE).forEach { preset ->
            val gains = audioPresetValues(preset).eqGainsDb
            // موضعا الوسط في الجدول السباعيّ: ٣ و٤.
            assertTrue("$preset: وسطٌ مرتفع", (gains.getOrNull(3) ?: 0) > 0 || (gains.getOrNull(4) ?: 0) > 0)
        }
    }

    @Test
    fun `الكلام هو النقيض المقصود للجهير — يُخفّض الجهير ويرفع الوسط`() {
        val speech = audioPresetValues(AudioSoundPreset.SPEECH)
        val bass = audioPresetValues(AudioSoundPreset.BASS)
        assertTrue("الكلام: جهير مُخفَّض", speech.eqGainsDb.first() < 0)
        assertTrue("الكلام: وسطٌ مرتفع", (speech.eqGainsDb.getOrNull(4) ?: 0) >= 3)
        assertTrue("الجهير: جهيرٌ مرفوع", bass.eqGainsDb.first() > 0)
        assertEquals("الكلام بلا جهير مُعزَّز", 0, speech.bassPercent)
    }

    @Test
    fun `قياس الوسط يعمل على جدولٍ أصغر بلا فهرس ثابت`() {
        // جدول خماسيّ: تخفيض في وسط النصف الأعلى يجب أن يُمسك.
        assertTrue(eqLowersSpeechRange(listOf(0, 0, -3, 0, 0)))
        assertFalse(eqLowersSpeechRange(listOf(0, 0, 1, 0, 0)))
        assertFalse(eqLowersSpeechRange(emptyList()))
    }

    // ─────────────────────────── ③ التعويض: كل رفعٍ يُقابله خفض ③ ───────────────────────────

    @Test
    fun `جدول الأنماط يحقق قاعدة التعويض النظرية دون إثبات التشويه`() {
        AudioSoundPreset.entries.forEach { preset ->
            val values = audioPresetValues(preset)
            assertNull(
                "$preset: تعويضه غير كافٍ (${values.preAmpDb} dB مقابل رفع)",
                presetCompensationRefusal(values),
            )
        }
    }

    @Test
    fun `نمطٌ بلا تعويض يُرفض — والتكذيب مُجرَّب`() {
        // رفعٌ ٤dB بلا تعويض ⇒ يجب أن يُرفض.
        val unsafe = AudioPresetValues(
            bassPercent = 50, virtualizerPercent = 0, loudnessMb = 0,
            eqGainsDb = listOf(4, 3, 2, 2, 3, 2, 1), preAmpDb = 0,
        )
        assertEquals(AudioPresetRefusal.OUT_OF_RANGE, presetCompensationRefusal(unsafe))
        // وتعويضٌ ضئيل جدًّا يُرفض كذلك.
        assertEquals(
            AudioPresetRefusal.OUT_OF_RANGE,
            presetCompensationRefusal(unsafe.copy(preAmpDb = -1)),
        )
        // وبالتعويض الكافي يمرّ.
        assertNull(presetCompensationRefusal(unsafe.copy(preAmpDb = -2)))
    }

    @Test
    fun `نمطٌ بلا رفع لا يحتاج تعويضًا`() {
        assertNull(
            presetCompensationRefusal(
                AudioPresetValues(0, 0, 0, listOf(0, 0, 0, 0, 0, 0, 0), 0),
            ),
        )
    }

    // ─────────────────────────── ④ الترجمة إلى سلّم المنصّة ④ ───────────────────────────

    @Test
    fun `النسبة تُترجم إلى سلّم المنصّة المجرّد وتُقيَّد`() {
        assertEquals(0, strengthFromPercent(0))
        assertEquals(AudioStrengthBounds.PLATFORM_STRENGTH_MAX, strengthFromPercent(100))
        assertEquals(500, strengthFromPercent(50))
        // والقيد يعمل: ما خرج عن 0..100 لا يُنتج قيمةً خارج السلّم.
        assertEquals(0, strengthFromPercent(-40))
        assertEquals(AudioStrengthBounds.PLATFORM_STRENGTH_MAX, strengthFromPercent(400))
    }

    // ─────────────────────────── ⑤ إعادة تشكيل EQ لعدد نطاقات الجهاز ⑤ ───────────────────────────

    @Test
    fun `الاستيفاء يحفظ شكل المنحنى على أيّ عدد نطاقات`() {
        val table = listOf(0, 0, 0, 0, 10)
        // بنطاقين: الطرفان (0 و10) — لا قصّ.
        assertEquals(listOf(0, 10), eqGainsForBands(table, 2))
        // بخمسة نطاقات: الجدول كما هو.
        assertEquals(table, eqGainsForBands(table, 5))
        // وبعشرة: قيمٌ متوسّطة بين الجدول وقيمه المجاورة — لا صفرٌ مصنوع.
        val ten = eqGainsForBands(table, 10)
        assertEquals(10, ten.size)
        assertEquals(0, ten.first())
        assertEquals(10, ten.last())
        assertTrue("يجب أن ترتفع تدريجيًّا", ten.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun `حالةٌ بلا نطاقات أو بجدول فارغ تُعيد فراغًا لا أصفارًا`() {
        assertTrue(eqGainsForBands(listOf(1, 2, 3), 0).isEmpty())
        assertTrue(eqGainsForBands(listOf(1, 2, 3), -5).isEmpty())
        assertTrue(eqGainsForBands(emptyList(), 10).isEmpty())
    }

    /**
     * **الحالة التي كسرت القصّ:** جهازٌ بخمسة نطاقات كان القصّ يُخفي فيه وسط الجدول — فيبقى الجهير
     * ويضيع الكلام. والاستيفاء يحفظ وسط الجدول لأنّه يقرأ بالموقع لا بالفهرس.
     */
    @Test
    fun `وسط الجدول يصل إلى جهاز بخمسة نطاقات`() {
        val table = audioPresetValues(AudioSoundPreset.BASS).eqGainsDb
        val five = eqGainsForBands(table, 5)
        // وسط الجدول (الفهرس ٣ في السباعيّ = ١kHz) يجب أن يظهر مرفوعًا في الخماسيّ.
        assertEquals(listOf(4, 2, 0, 1, 0), five)
    }

    // ─────────────────────────── ⑥ حكم الزرّ: لا زرّ على ما لم يُقس ⑥ ───────────────────────────

    private fun writable(feature: AudioFeature) =
        AudioFeatureVerdict(feature, AudioSupport.WRITABLE, "ok")

    @Test
    fun `الإيقاف يُعرض دائمًا — ولو لم تُقس القدرات`() {
        val verdict = presetVerdict(AudioSoundPreset.OFF, verdicts = null, bandCount = null)
        assertTrue(verdict.enabled)
        assertNull(verdict.reason)
    }

    @Test
    fun `قدراتٌ غير مقيسة تعطّل الزرّ بسببٍ مكتوب — لا تخفيه`() {
        AudioSoundPreset.buttons.forEach { preset ->
            val verdict = presetVerdict(preset, verdicts = null, bandCount = 6)
            assertFalse("$preset يجب أن يُعطَّل", verdict.enabled)
            assertEquals(AudioPresetRefusal.FEATURE_UNAVAILABLE, verdict.reason)
        }
    }

    @Test
    fun `نمطٌ يحتاج ميزةً غير معلَنة لا يُعرض زرّه`() {
        val verdicts = listOf(
            writable(AudioFeature.EQUALIZER),
            // BassBoost غائب عن القائمة ⇒ نمط الجهير لا يُعرض (فهو يُعرّفه).
        )
        assertFalse(presetVerdict(AudioSoundPreset.BASS, verdicts, 6).enabled)
        // وأمّا نمط الكلام فيُعرّفه الـEQ وحده فيمرّ — وغياب الجهارة يُقال لا يُسقط.
        val speech = presetVerdict(AudioSoundPreset.SPEECH, verdicts, 6)
        assertTrue(speech.enabled)
        assertTrue(speech.isPartial)
    }

    @Test
    fun `نمطٌ الـEQ فيه مُعرِّف لا يُعرض على جهازٍ بلا نطاقات`() {
        val verdicts = listOf(
            writable(AudioFeature.EQUALIZER), writable(AudioFeature.BASS_BOOST),
            writable(AudioFeature.VIRTUALIZER), writable(AudioFeature.LOUDNESS_ENHANCER),
        )
        // الكلام يُعرّفه منحنى الـEQ ⇒ بلا نطاقات لا يُعرض.
        assertFalse(presetVerdict(AudioSoundPreset.SPEECH, verdicts, bandCount = 0).enabled)
        assertFalse(presetVerdict(AudioSoundPreset.SPEECH, verdicts, bandCount = null).enabled)
        // والجهير يُعرّفه `BassBoost` ⇒ يُعرض ويُقال إنّ الـEQ (تعويضٌ) سيُتخطّى.
        val bass = presetVerdict(AudioSoundPreset.BASS, verdicts, bandCount = 0)
        assertTrue(bass.enabled)
        assertTrue(bass.isPartial)
        assertTrue(AudioFeature.EQUALIZER in bass.skipped)
    }

    @Test
    fun `زرٌّ معطَّل بلا سبب يُرفض عند البناء — لا يُقرأ معطوبًا`() {
        assertThrowsIllegalArgument { PresetVerdict(enabled = false, reason = null) }
        assertNotNull(PresetVerdict(enabled = false, reason = "x").reason)
        // والزرّ المفعَّل بلا سبب مشروع — السبب خاصّيّةُ التعطيل وحده.
        assertNull(PresetVerdict(enabled = true, reason = null).reason)
    }

    /**
     * **الميزة الثانوية لا تُسقط الزرّ** — وهذا قياسٌ أمسك عطبًا حقيقيًّا في أوّل كتابة للحكم:
     * كان نمط «كلام واضح» يُختفي على جهازٍ بلا `LoudnessEnhancer`، مع أنّ قوّته في منحنى الـEQ
     * لا في تعزيز الجهارة (‏`200 mB` فيه ثانويّة).
     */
    @Test
    fun `غياب ميزةٍ ثانوية لا يُسقط الزرّ بل يُقال`() {
        val verdicts = listOf(
            writable(AudioFeature.EQUALIZER),
            writable(AudioFeature.BASS_BOOST),
            writable(AudioFeature.VIRTUALIZER),
            // LoudnessEnhancer غائب — وهو ثانويّ في «كلام واضح» و«فيلم» و«ألعاب».
        )
        val speech = presetVerdict(AudioSoundPreset.SPEECH, verdicts, 6)
        assertTrue("نمط الكلام يجب أن يبقى معروضًا", speech.enabled)
        assertTrue("ونقصه يجب أن يُقال", speech.isPartial)
        assertEquals(listOf(AudioFeature.LOUDNESS_ENHANCER), speech.skipped)

        // وأمّا غياب الميزة المُعرِّفة فيُسقطه — وهذا هو الفرق.
        val withoutBass = listOf(writable(AudioFeature.EQUALIZER), writable(AudioFeature.VIRTUALIZER))
        assertFalse(presetVerdict(AudioSoundPreset.BASS, withoutBass, 6).enabled)
    }

    @Test
    fun `الميزات المُعرِّفة لكل نمط مُعلنة ولا تُخلط بالثانوية`() {
        AudioSoundPreset.entries.forEach { preset ->
            val required = audioPresetRequiredFeatures(preset)
            val optional = audioPresetOptionalFeatures(preset)
            assertTrue(
                "$preset: ميزةٌ في المُعرِّفة والثانوية معًا",
                required.intersect(optional).isEmpty(),
            )
            if (preset == AudioSoundPreset.OFF) {
                assertTrue(required.isEmpty())
                assertTrue(optional.isEmpty())
            } else {
                assertTrue("$preset: بلا ميزة مُعرِّفة", required.isNotEmpty())
            }
        }
        assertEquals(setOf(AudioFeature.BASS_BOOST), audioPresetRequiredFeatures(AudioSoundPreset.BASS))
        assertEquals(setOf(AudioFeature.EQUALIZER), audioPresetRequiredFeatures(AudioSoundPreset.SPEECH))
    }

    // ─────────────────────────── ⑦ شريط القوّة الواحد ⑦ ───────────────────────────

    @Test
    fun `القوة تبدأ خفيفة والمتوازن يطابق الوصفة والقوي يزيدها`() {
        assertEquals(0.35, presetIntensityScale(0), 0.0001)
        assertEquals(1.0, presetIntensityScale(50), 0.0001)
        assertEquals(1.4, presetIntensityScale(100), 0.0001)
        assertEquals(0.35, presetIntensityScale(-50), 0.0001)
        assertEquals(1.4, presetIntensityScale(999), 0.0001)

        val values = audioPresetValues(AudioSoundPreset.BASS)
        val unchanged = values.scaledBy(50)
        assertEquals(values, unchanged)
        assertTrue(values.scaledBy(0).bassPercent < values.bassPercent)
        assertEquals(210, audioPresetValues(AudioSoundPreset.LOUD).scaledBy(0).loudnessMb)

        // وشدّةٌ أعلى تزيد ولا تُنقص — وهذا هو القياس الذي يمنع شريطًا معكوسًا.
        val stronger = values.scaledBy(100)
        assertTrue(stronger.bassPercent >= values.bassPercent)
        assertTrue(stronger.preAmpDb <= values.preAmpDb) // التعويض أعمق (أكثر سالبيّة)
        assertTrue(stronger.bassPercent <= 100)
    }

    @Test
    fun `التعويض يبقى سالبًا بعد الشدّة — لا ينقلب موجبًا`() {
        AudioSoundPreset.buttons.forEach { preset ->
            val scaled = audioPresetValues(preset).scaledBy(100)
            assertTrue("$preset: التعويض انقلب", scaled.preAmpDb <= 0)
        }
    }

    // ─────────────────────────── ⑧ الرموز ثابتة ⑧ ───────────────────────────

    @Test
    fun `رموز الأنماط فريدة ولا تتغيّر`() {
        val tokens = AudioSoundPreset.entries.map { it.token }
        assertEquals(tokens.size, tokens.toSet().size)
        assertEquals(AudioSoundPreset.BASS, AudioSoundPreset.ofToken("bass"))
        assertEquals(AudioSoundPreset.OFF, AudioSoundPreset.ofToken("off"))
        assertNull(AudioSoundPreset.ofToken("nope"))
    }

    @Test
    fun `لكل نمط رمز اسم وشرح — فلا زرّ بلا كلام يُفهم`() {
        AudioSoundPreset.entries.forEach { preset ->
            assertTrue("$preset: بلا رمز اسم", preset.labelToken.isNotBlank())
            assertTrue("$preset: بلا رمز شرح", preset.noteToken.isNotBlank())
            assertTrue("$preset: رمز الاسم بلا بادئة موحّدة", preset.labelToken.startsWith("audio_preset_"))
            assertTrue("$preset: رمز الشرح بلا لاحقة موحّدة", preset.noteToken.endsWith("_note"))
        }
        // والرموز فريدة — فلا نمطان يتشاركان الاسم نفسه.
        val labels = AudioSoundPreset.entries.map { it.labelToken }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `الكتابة الفعلية تستعمل مراكز ملي هرتز ومنحنى خفض وحدود الجهاز`() {
        val values = audioPresetValues(AudioSoundPreset.SPEECH)
        val bands = listOf(60_000, 1_000_000, 2_500_000, 15_000_000).mapIndexed { index, hz ->
            AudioEqBand(index, hz, null, null, 0, -600, 600)
        }
        assertEquals(mapOf(0 to -600, 1 to -200, 2 to 0, 3 to -300), audioPresetEqTargets(values, bands))
        assertNull(audioPresetEqTargets(values, bands.map { it.copy(centerHz = null) }))
        assertNull(audioPresetEqTargets(values, bands.map { it.copy(levelMb = null) }))
        assertNull(audioPresetEqTargets(values, emptyList()))
    }

    @Test
    fun `النطاق المتوسط يستوفى لوغاريتميا ولا يتبع عدد النطاقات`() {
        val band = AudioEqBand(7, 100_000, null, null, 0, -1500, 1500)
        assertEquals(-644, audioPresetEqTargets(audioPresetValues(AudioSoundPreset.SPEECH), listOf(band))?.get(7))
    }

    @Test
    fun `الفشل يوقف الباقي ويرجع حتى المقبض الفاشل`() {
        val live = linkedMapOf("a" to 1, "b" to 2, "c" to 3)
        val calls = mutableListOf<Pair<String, Int>>()
        val result = audioPresetTransaction(mapOf("a" to 10, "b" to 20, "c" to 30), live.toMap()) { key, value ->
            calls += key to value
            live[key] = value
            if (key == "b" && value == 20) AudioKnobVerdict(AudioWriteOutcome.BLOCKED, reason = "owner")
            else AudioKnobVerdict(AudioWriteOutcome.APPLIED)
        }
        assertFalse(result.applied)
        assertEquals("owner", result.reason)
        assertEquals(mapOf("a" to 1, "b" to 2, "c" to 3), live)
        assertEquals(listOf("a" to 10, "b" to 20, "b" to 2, "a" to 1), calls)
    }

    @Test
    fun `تعذر الاسترجاع يعلن المقابض ولا يختلق نجاحا`() {
        val result = audioPresetTransaction(mapOf("a" to 10), mapOf("a" to 1)) { _, _ ->
            AudioKnobVerdict(AudioWriteOutcome.FAILED, reason = "refused")
        }
        assertEquals(listOf("a"), result.failedRestore)
        var writes = 0
        val unknown = audioPresetTransaction(mapOf("a" to 10), emptyMap()) { _, _ ->
            writes++
            AudioKnobVerdict(AudioWriteOutcome.APPLIED)
        }
        assertFalse(unknown.applied)
        assertEquals(0, writes)
    }

    @Test
    fun `كل قوة تحترم سقف الجهارة والمنحنى الفعلي لا يرفع EQ`() {
        AudioSoundPreset.buttons.forEach { preset ->
            (0..100).forEach { intensity ->
                val values = audioPresetValues(preset).scaledBy(intensity)
                assertTrue(values.loudnessMb <= AudioStrengthBounds.UI_LOUDNESS_MAX_MB)
                val bands = listOf(AudioEqBand(0, 1_000_000, null, null, 0, -1500, 1500))
                assertTrue(audioPresetEqTargets(values, bands)!!.values.all { it <= 0 })
            }
        }
    }

    @Test
    fun `الاسترجاع يعيد التمكين قبل القيم والتعطيل بعدها`() {
        val calls = mutableListOf<String>()
        val baseline = linkedMapOf("bass/enabled" to 1, "bass/value" to 50)
        val result = audioPresetTransaction(mapOf("bass/enabled" to 0, "bass/value" to 100), baseline) { key, value ->
            calls += "$key=$value"
            if (key == "bass/value" && value == 100) AudioKnobVerdict(AudioWriteOutcome.FAILED)
            else AudioKnobVerdict(AudioWriteOutcome.APPLIED)
        }
        assertFalse(result.applied)
        assertEquals(listOf("bass/enabled=0", "bass/value=100", "bass/enable_first=1", "bass/value=50", "bass/enabled=1"), calls)
    }

    @Test
    fun `تشخيص الخطة يميز التطابق والمخالفة والقراءة الغائبة`() {
        val expected = mapOf("equalizer/enabled" to 1, "equalizer/band/0" to -200)
        assertEquals(true, audioPresetPlanMatches(expected, expected))
        assertEquals(false, audioPresetPlanMatches(expected, expected + ("equalizer/enabled" to 0)))
        assertNull(audioPresetPlanMatches(expected, mapOf("equalizer/enabled" to 1)))
        assertNull(audioPresetPlanMatches(emptyMap(), expected))
        assertEquals(true, audioPresetPlanMatches(expected, expected + ("bass/value" to 300)))
    }

    @Test
    fun `القوة الخفيفة تقلل الجهير والتوسيع والجهارة والمنحنى دون قفزة عند المنتصف`() {
        val scales = (0..100).map(::presetIntensityScale)
        assertTrue(scales.zipWithNext().all { (a, b) -> b >= a && b - a <= 0.014 })
        AudioSoundPreset.buttons.forEach { preset ->
            val base = audioPresetValues(preset)
            val light = base.scaledBy(0)
            assertTrue(light.bassPercent <= base.bassPercent)
            assertTrue(light.virtualizerPercent <= base.virtualizerPercent)
            assertTrue(light.loudnessMb <= base.loudnessMb)
            light.eqGainsDb.zip(base.eqGainsDb).forEach { (a, b) ->
                assertTrue(kotlin.math.abs(a) <= kotlin.math.abs(b))
            }
        }
    }

    @Test
    fun `الرجوع إلى مؤثر كان مطفأ يمكنه كتابة القيم ثم يعيد إطفاءه`() {
        var enabled = false
        var value = 40
        val baseline = mapOf("bass/enabled" to 0, "bass/enable_first" to 0, "bass/value" to 40)
        val plan = audioPresetOrderedTargets(mapOf("bass/enabled" to 1, "bass/value" to 800))
        val result = audioPresetTransaction(plan, baseline) { key, target ->
            when {
                key.endsWith("/enabled") || key.endsWith("/enable_first") -> enabled = target == 1
                !enabled -> return@audioPresetTransaction AudioKnobVerdict(AudioWriteOutcome.FAILED)
                else -> value = target
            }
            if (target == 800) AudioKnobVerdict(AudioWriteOutcome.FAILED)
            else AudioKnobVerdict(AudioWriteOutcome.APPLIED)
        }
        assertFalse(result.applied)
        assertTrue(result.failedRestore.isEmpty())
        assertFalse(enabled)
        assertEquals(40, value)
    }

    @Test
    fun `بعد المقارنة تعيد الخطة الكاملة قيم المؤثر غير المستخدم أيضا`() {
        val original = mapOf("bass/enabled" to 1, "bass/value" to 400, "loudness/enabled" to 0, "loudness/value" to 0)
        val speech = mapOf("bass/enabled" to 0, "loudness/enabled" to 1, "loudness/value" to 200)
        val complete = audioPresetCompletePlan(original, speech)
        assertEquals(400, complete["bass/value"])
        assertEquals(0, complete["bass/enabled"])
        assertEquals(200, complete["loudness/value"])
        val ordered = audioPresetOrderedTargets(complete).keys.toList()
        assertTrue(ordered.indexOf("bass/enable_first") < ordered.indexOf("bass/value"))
        assertTrue(ordered.indexOf("bass/value") < ordered.indexOf("bass/enabled"))
    }

    @Test
    fun `مدى EQ موجب فقط أو فهرس مكرر يرفض بدلا من رفع الصوت أو إسقاط نطاق`() {
        val values = audioPresetValues(AudioSoundPreset.SPEECH)
        val band = AudioEqBand(0, 1_000_000, null, null, 0, 100, 500)
        assertNull(audioPresetEqTargets(values, listOf(band)))
        val normal = band.copy(levelMinMb = -500)
        assertNull(audioPresetEqTargets(values, listOf(normal, normal.copy(centerHz = 2_000_000))))
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("كان يجب أن يُرفض ولم يُرفض")
        } catch (_: IllegalArgumentException) {
            // متوقّع.
        }
    }
}
