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
 * نموذج المازج والطيف — **مقيسٌ على JVM**.
 *
 * **وما يُقاس هنا بالضبط:**
 * ① بصمة السمة تُكتب وتُقرأ بنفسها (فالمحكِّم يقارن نصًّا، والبصمة هي النصّ).
 * ② `bit-perfect` تُقرأ من السلوك المُعلَن لا من نيّتنا.
 * ③ **البايت في `getFft` بلا إشارة** — وطرح ١٢٨ من بايتٍ مُوقّع كان يجعل الصمت أعلى مقدار في الإطار،
 *    وهذا هو العطب الذي يقلب الرسم كاملًا ولا يُنتجه مُصرّف.
 * ④ الطيف **مطبَّع على أقصى الإطار نفسه**، فالإطار الصامت يُرسم أصفارًا لا فراغًا.
 */
class AudioMixerSpectrumTest {

    @Test
    fun `mixer label is ordered and drops what was not read`() {
        assertEquals(
            "48 kHz · 2 ch · PCM 24-bit · bit-perfect",
            audioMixerLabel(AudioMixerAttribute(48000, 2, AUDIO_ENCODING_PCM_24, true)),
        )
        // وما لم يُقرأ يُسقط من الوصف — ولا يُكتب `0` ولا `?` مكانه.
        assertEquals("", audioMixerLabel(AudioMixerAttribute(null, null, null, false)))
        assertEquals("2 ch", audioMixerLabel(AudioMixerAttribute(null, 2, null, false)))
        assertEquals("44100 Hz", audioMixerLabel(AudioMixerAttribute(44100, null, null, false)))
    }

    @Test
    fun `signature round trips so the read back is comparable`() {
        val attribute = AudioMixerAttribute(48000, 2, AUDIO_ENCODING_PCM_16, true)
        assertEquals(attribute, audioMixerOfSignature(audioMixerSignature(attribute)))
        // والقيم الفارغة تُحفظ فارغة لا أصفارًا.
        assertEquals(AudioMixerAttribute(null, null, null, false), audioMixerOfSignature(audioMixerSignature(AudioMixerAttribute(null, null, null, false))))
        assertNull(audioMixerOfSignature(null))
        assertNull(audioMixerOfSignature(""))
    }

    @Test
    fun `matching is tolerant about fields we did not request`() {
        val noPreference = AudioMixerAttribute(null, null, null, false)
        assertTrue(audioMixerMatches(noPreference, AudioMixerAttribute(44100, 1, AUDIO_ENCODING_PCM_8, false)))
        // وحقلٌ طلبناه يُقارَن بحرفه.
        assertFalse(audioMixerMatches(AudioMixerAttribute(48000, null, null, false), AudioMixerAttribute(44100, 2, null, false)))
        assertFalse(audioMixerMatches(noPreference.copy(bitPerfect = true), AudioMixerAttribute(44100, 2, null, false)))
        assertTrue(audioMixerMatches(noPreference.copy(bitPerfect = true), AudioMixerAttribute(44100, 2, null, true)))
    }

    @Test
    fun `decibels are formatted and infinity is not a reading`() {
        assertEquals("-12.3 dB", audioDbFormat(-12.34f))
        assertEquals("0 dB", audioDbFormat(0f))
        // والمنصّة تُعيد `-Infinity` للصامت/المكتوم، وهي ليست قيمة تُعرض.
        assertNull(audioDbFormat(Float.NEGATIVE_INFINITY))
        assertNull(audioDbFormat(Float.NaN))
        assertNull(audioDbFormat(null))
    }

    @Test
    fun `silent frame is drawn as zeros not as an empty reading`() {
        // ‏`0x80` (‏128 بلا إشارة) هو صفر طيف المنصّة.
        val silence = ByteArray(64) { 0x80.toByte() }
        assertEquals(List(4) { 0f }, audioSpectrumBandsOf(silence, 4))
        // وإطارٌ فارغ أو غائب يُعطي نفس العدد من المستويات، لا قائمة فارغة تُقرأ «لا رسم».
        assertEquals(List(4) { 0f }, audioSpectrumBandsOf(null, 4))
        assertEquals(List(2) { 0f }, audioSpectrumBandsOf(ByteArray(0), 2))
        assertTrue(audioSpectrumBandsOf(silence, 0).isEmpty())
    }

    @Test
    fun `loud half of the frame is normalised and silence stays zero`() {
        // النصف الثاني بمقدار أقصى (‏0xFF = ‏255 بلا إشارة)، والأوّل صمت.
        val fft = ByteArray(64) { index -> if (index >= 32) 0xFF.toByte() else 0x80.toByte() }
        val bands = audioSpectrumBandsOf(fft, 4)
        assertEquals(4, bands.size)
        assertEquals(0f, bands[0], 0.001f)
        assertEquals(0f, bands[1], 0.001f)
        assertEquals(1f, bands[2], 0.001f)
        assertEquals(1f, bands[3], 0.001f)
    }

    @Test
    fun `frame interval follows the capture rate and never collapses`() {
        assertEquals(50L, spectrumFrameIntervalMs(20))
        assertEquals(100L, spectrumFrameIntervalMs(10))
        // ومعدّل أقصى غير معقول لا يُنتج فاصلًا أقل من إطار الشاشة.
        assertEquals(16L, spectrumFrameIntervalMs(1000))
        assertEquals(50L, spectrumFrameIntervalMs(null))
    }

    @Test
    fun `capture refuses without the permission`() {
        assertFalse(spectrumCapturable(permissionGranted = false))
        assertTrue(spectrumCapturable(permissionGranted = true))
    }
}
