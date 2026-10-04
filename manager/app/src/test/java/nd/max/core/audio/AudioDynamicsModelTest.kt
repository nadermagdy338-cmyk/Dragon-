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
 * نموذج الديناميكيّ وقوّة المؤثّرات — **مقيسٌ على JVM**.
 *
 * **وما يُقاس هنا بالضبط:** أنّ التوازن **لا يرفع الصوت أبدًا** وأنّ وسطه على صفر، وأنّ العكس
 * (`dynamicsBalanceOf`) يعيد الموضع نفسه فلا يُفقد بعد إعادة القراءة، وأنّ القوّة لا تُخرج عن المُعلَن،
 * وأنّ `null` تبقى `null` في العرض (فالغياب ليس صفرًا)، وأنّ الحدود المُعلَنة **حدُّنا** مسبوقة بـ`UI_`.
 */
class AudioDynamicsModelTest {

    @Test
    fun `balance at centre changes nothing`() {
        val (left, right) = dynamicsBalanceGains(0f)
        assertEquals(0f, left, 0.001f)
        assertEquals(0f, right, 0.001f)
        assertEquals(true, dynamicsBalanceIsCentered(listOf(0f, 0f)))
    }

    @Test
    fun `balance never raises a channel and never exceeds the declared maximum`() {
        var balance = -1f
        while (balance <= 1f) {
            val (left, right) = dynamicsBalanceGains(balance)
            assertTrue("left must only attenuate", left <= 0.0001f)
            assertTrue("right must only attenuate", right <= 0.0001f)
            assertTrue(left >= -AudioDynamicsBounds.UI_BALANCE_MAX_DB - 0.001f)
            assertTrue(right >= -AudioDynamicsBounds.UI_BALANCE_MAX_DB - 0.001f)
            balance += 0.1f
        }
    }

    @Test
    fun `balance extremes attenuate exactly one channel`() {
        val (leftAtLeft, rightAtLeft) = dynamicsBalanceGains(-1f)
        assertEquals(-AudioDynamicsBounds.UI_BALANCE_MAX_DB, leftAtLeft, 0.001f)
        assertEquals(0f, rightAtLeft, 0.001f)

        val (leftAtRight, rightAtRight) = dynamicsBalanceGains(1f)
        assertEquals(0f, leftAtRight, 0.001f)
        assertEquals(-AudioDynamicsBounds.UI_BALANCE_MAX_DB, rightAtRight, 0.001f)
    }

    @Test
    fun `balance position survives a read back`() {
        listOf(-1f, -0.5f, 0f, 0.5f, 1f, 7f).forEach { requested ->
            val gains = dynamicsBalanceGains(requested)
            val read = dynamicsBalanceOf(listOf(gains.first, gains.second))
            assertEquals(requested.coerceIn(-1f, 1f), read!!, 0.02f)
        }
        // وقناةٌ واحدة لا تكفي لحكم.
        assertNull(dynamicsBalanceOf(listOf(0f)))
    }

    @Test
    fun `clamp keeps a value inside the declared window`() {
        assertEquals(5f, dynamicsClamp(5f, 0f, 10f), 0.001f)
        assertEquals(0f, dynamicsClamp(-1f, 0f, 10f), 0.001f)
        assertEquals(10f, dynamicsClamp(99f, 0f, 10f), 0.001f)
        // ومدًى صفريّ يُعيد الحدّ الأدنى ولا يقسم على صفر.
        assertEquals(3f, dynamicsClamp(9f, 3f, 3f), 0.001f)
    }

    @Test
    fun `formatting keeps one decimal so the read back matches`() {
        assertEquals("-12.3 dB", dynamicsFormatDb(-12.34f))
        assertEquals("+3.5 dB", dynamicsFormatDb(3.5f))
        assertEquals("120 ms", dynamicsFormatMs(120.4f))
        assertEquals("4.0:1", dynamicsFormatRatio(4f))
        assertEquals("60 Hz", dynamicsFormatHz(60f))
        assertEquals("1.2 kHz", dynamicsFormatHz(1200f))
        assertNull(dynamicsFormatDb(null))
        assertNull(dynamicsFormatMs(null))
        assertNull(dynamicsFormatRatio(null))
        assertNull(dynamicsFormatHz(null))
        assertNull(dynamicsFormatHz(0f))
    }

    @Test
    fun `every declared bound is marked as ours not the hardware`() {
        val bounds = AudioDynamicsBounds::class.java.declaredFields
            // و`INSTANCE` هو حقل الكائن المفرد الذي يولّده Kotlin، وليس حدًّا.
            .filter {
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    // و`INSTANCE` حقل الكائن المفرد، و`$stable` حقل يضيفه مُصرّف Compose.
                    it.name != "INSTANCE" && !it.name.startsWith("$")
            }
            .map { it.name }
        assertTrue(bounds.isNotEmpty())
        assertTrue(
            "كل ثابت ديناميكيّ يجب أن يبدأ بـUI_ فلا يُقرأ حكمًا عتاديًّا: $bounds",
            bounds.all { it.startsWith("UI_") },
        )
    }

    @Test
    fun `strength fraction and value are inverse and stay inside the platform scale`() {
        val min = AudioStrengthBounds.PLATFORM_STRENGTH_MIN
        val max = AudioStrengthBounds.PLATFORM_STRENGTH_MAX
        assertEquals(0.5f, strengthFractionOf(500, min, max)!!, 0.001f)
        assertEquals(500, strengthValueFrom(0.5f, min, max))
        assertEquals(min, strengthValueFrom(-4f, min, max))
        assertEquals(max, strengthValueFrom(4f, min, max))
        assertNull(strengthFractionOf(null, min, max))
        assertNull(strengthFractionOf(1, 0, 0))
    }

    @Test
    fun `strength snapshot refuses a knob when the device reports no support`() {
        val unsupported = AudioStrengthSnapshot(AudioEffectKind.BASS_BOOST, supported = false, value = 300)
        assertFalse(unsupported.isWritable)
        assertNull(AudioStrengthSnapshot(AudioEffectKind.BASS_BOOST, supported = null, value = null).value)
        assertTrue(AudioStrengthSnapshot(AudioEffectKind.BASS_BOOST, supported = true, value = 300).isWritable)
        assertEquals("+3.5 dB", loudnessFormat(350))
        assertNull(loudnessFormat(null))
    }

    @Test
    fun `reverb presets are the platform set and none is invented`() {
        assertEquals(7, AudioReverbPreset.tokens.size)
        assertEquals(AudioReverbPreset.NONE, AudioReverbPreset.tokens.first())
    }

    // ─────────────────── ‏AQ-05: سلّم الإرفاق — **العطب المقيس في تكملة ٢٣٩** ───────────────────

    @Test
    fun `attach ladder starts with the platform own architecture and never repeats a step`() {
        val tokens = DYNAMICS_ATTACH_LADDER.map { it.token }
        // **الترتيب مقيس لا ذوق:** البداية بما تعرفه المنصّة عن نفسها (بلا `Config`)، ثمّ هندستنا.
        assertEquals(DynamicsAttachStep.PLATFORM_DEFAULT, DYNAMICS_ATTACH_LADDER.first())
        assertEquals(DynamicsAttachStep.TIME_VARIANT, DYNAMICS_ATTACH_LADDER.last())
        assertEquals(tokens.size, tokens.toSet().size)
        // **وثلاثٌ لا أكثر**: الخطوة الرابعة تعني أننا نجرّب بلا سببٍ مكتوب.
        assertEquals(DynamicsAttachStep.values().size, DYNAMICS_ATTACH_LADDER.size)
    }

    @Test
    fun `platform default step asks for no band counts at all`() {
        val plan = dynamicsAttachPlan(DynamicsAttachStep.PLATFORM_DEFAULT, channels = 2)
        assertTrue(plan.usePlatformDefault)
        // **والأظهر أنّه لا عددَ نطلبه** — وهذا هو الفرق الجوهريّ عن الإرفاق الواحد السابق.
        assertEquals(0, plan.preEqBands)
        assertEquals(0, plan.mbcBands)
        assertEquals(0, plan.postEqBands)
    }

    @Test
    fun `a config step uses the measured architecture when the device told us one`() {
        val measured = DynamicsMeasuredArchitecture(
            variant = 2,
            channels = 2,
            preEqBands = 10,
            mbcBands = 5,
            postEqBands = 10,
        )
        val plan = dynamicsAttachPlan(
            step = DynamicsAttachStep.RESOLUTION_VARIANT,
            channels = 2,
            requestedEqBands = 6,
            requestedMbcBands = 4,
            measured = measured,
        )
        // المقيس أولى من المطلوب — فلا نفرض ٦/٤ على عتادٍ قال ١٠/٥.
        assertEquals(10, plan.preEqBands)
        assertEquals(5, plan.mbcBands)
        assertEquals(10, plan.postEqBands)
        assertFalse(plan.usePlatformDefault)
    }

    @Test
    fun `a config step falls back to the declared request when nothing was measured`() {
        val plan = dynamicsAttachPlan(
            step = DynamicsAttachStep.TIME_VARIANT,
            channels = 2,
            requestedEqBands = 6,
            requestedMbcBands = 4,
            measured = null,
        )
        assertEquals(6, plan.preEqBands)
        assertEquals(4, plan.mbcBands)
        assertEquals(6, plan.postEqBands)
    }

    @Test
    fun `band counts never drop below one and a zero measurement is not trusted`() {
        assertEquals(6, dynamicsBandCounts(requested = 6, measured = null))
        // قياسٌ صفريّ لا يُبنى به محرّك بلا نطاقات — فالمطلوب هو الاحتياطيّ المُعلَن.
        assertEquals(6, dynamicsBandCounts(requested = 6, measured = 0))
        assertEquals(3, dynamicsBandCounts(requested = 6, measured = 3))
        assertEquals(1, dynamicsBandCounts(requested = 0, measured = null))
        // وقناةٌ واحدة مُعلَنة تُحترم، وما دونها لا يُمرَّر للمُهيّئ.
        assertEquals(1, dynamicsAttachPlan(DynamicsAttachStep.RESOLUTION_VARIANT, channels = 0).channels)
    }

    @Test
    fun `each step refuses with its own token and the ladder has one displayed verdict`() {
        val tokens = DynamicsAttachStep.values().map { dynamicsAttachRefusal(it) }
        assertEquals(tokens.size, tokens.toSet().size)
        assertFalse(tokens.contains(DYNAMICS_ATTACH_ALL_REFUSED))
        assertEquals(AudioEffectReason.ATTACH_PLATFORM_DEFAULT_REFUSED, tokens.first())
        // والحكم المعروض **مغايرٌ لرموز الخطوات**، فلا يُظنّ أنّ الخطوة الأخيرة وحدها جُرّبت.
        assertEquals(AudioEffectReason.ATTACH_ALL_STEPS_REFUSED, DYNAMICS_ATTACH_ALL_REFUSED)
    }

    @Test
    fun `the declared requests are the ones the ladder uses`() {
        // عددٌ مكتوبٌ مرّتين ينحرف يومًا — والثابت هنا هو نفسه الذي يقرأه المحرّك والواجهة.
        assertEquals(6, DEFAULT_REQUESTED_EQ_BANDS)
        assertEquals(4, DEFAULT_REQUESTED_MBC_BANDS)
    }
}
