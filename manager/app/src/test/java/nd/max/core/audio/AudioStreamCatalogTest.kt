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
 * دفقات الصوت — **الطبقة الصافية وحدها، مُختبَرة على الـJVM** (`AU-03`).
 *
 * **والحدّ المُعلن أوّلًا:** القراءة نفسها (`AudioManager.getStreamVolume` ·
 * `getStreamMaxVolume` · `isStreamMute`) **لا تُقاس هنا** — تلمس المنصّة، ولا `android.jar` في
 * هذه البيئة. والمقيس هو **ما يُفعل بما أعلنته المنصّة**: أيّ الدفقات نُسمّي، وكيف تُحوَّل قراءةٌ
 * إلى نسبة وبالعكس.
 *
 * **وثلاثة أعطاب تُغلقها هذه البوابة، كلها واقعة لا مُتخيَّلة:**
 *
 * ١. **«لا قراءة» تُصبح صفرًا:** نسبةٌ تُحسب من `null` تُعيد `0f` فيُعرض الدفق صامتًا وهو لم يُقرأ.
 * والقاعدة: `null` تبقى `null` (ADR-07) — والصفر **مستوى حقيقيّ** يُعرض صفرًا.
 * ٢. **مستوى يتجاوز سقف الجهاز:** نسبةٌ من واجهة (٠٫٨) على سقف ١٥ لا يجوز أن تُنتج ١٦.
 * ٣. **دفقٌ نجهله يُكتب على أقرب دفق معروف:** الرمز المجهول يُعاد `null`، فلا كتابة بلا معرّف.
 */
class AudioStreamCatalogTest {

    @Test
    fun `the six streams are named once, and a token we did not name is unknown`() {
        val tokens = AudioStreamCatalog.streams.map { it.token }

        assertEquals("عدد الدفقات", 6, tokens.size)
        assertEquals("لا رمز مكرّر", tokens.size, tokens.toSet().size)
        assertEquals(
            listOf("media", "call", "ring", "notification", "alarm", "system"),
            tokens,
        )
        tokens.forEach { assertTrue(it, AudioStreamCatalog.isKnown(it)) }

        // والمجهول يبقى مجهولًا: لا يُلحق بأقرب رمز، ولا يُكتب عليه.
        assertNull(AudioStreamCatalog.stream("dtmf"))
        assertNull(AudioStreamCatalog.stream("system_enforced"))
        assertNull(AudioStreamCatalog.stream(""))
        assertFalse(AudioStreamCatalog.isKnown(""))
    }

    @Test
    fun `a missing reading is null, and a real silent stream is zero`() {
        // غياب القراءة: `null` لا صفر.
        assertNull(AudioStreamCatalog.fractionOf(level = null, maxLevel = 15))
        assertNull(AudioStreamCatalog.fractionOf(level = 7, maxLevel = null))
        // وسقفٌ غير صالح ليس سقفًا: لا قسمة على صفر ولا نسبة سالبة.
        assertNull(AudioStreamCatalog.fractionOf(level = 7, maxLevel = 0))
        assertNull(AudioStreamCatalog.fractionOf(level = 7, maxLevel = -15))

        // والقراءة الحقيقية — ومنها الصفر — تُحوَّل كما هي.
        assertEquals(0f, AudioStreamCatalog.fractionOf(level = 0, maxLevel = 15)!!, 1e-6f)
        assertEquals(1f, AudioStreamCatalog.fractionOf(level = 15, maxLevel = 15)!!, 1e-6f)
        // وما فوق السقف (قراءةٌ من منصّة تُجمع فيها الدرجات) لا تُعرض نسبةً تتجاوز الواحد.
        assertEquals(1f, AudioStreamCatalog.fractionOf(level = 20, maxLevel = 15)!!, 1e-6f)
        assertEquals(0f, AudioStreamCatalog.fractionOf(level = -3, maxLevel = 15)!!, 1e-6f)
    }

    @Test
    fun `a level from a fraction never passes the ceiling the device declared`() {
        assertEquals(0, AudioStreamCatalog.levelOf(fraction = 0f, maxLevel = 15))
        assertEquals(15, AudioStreamCatalog.levelOf(fraction = 1f, maxLevel = 15))
        // والنسبة الخارجة تُقصّ على السقف، لا تُترك ترفعه.
        assertEquals(15, AudioStreamCatalog.levelOf(fraction = 9f, maxLevel = 15))
        assertEquals(0, AudioStreamCatalog.levelOf(fraction = -1f, maxLevel = 15))
        // والتدوير عاديّ (٧٫٥ ⇒ ٨)، فالكتابة على مقبض المنصّة رقمٌ صحيح لا كسر.
        assertEquals(8, AudioStreamCatalog.levelOf(fraction = 0.5f, maxLevel = 15))
        // وسقفٌ غير صالح لا يُنتج مستوى مخترعًا.
        assertEquals(0, AudioStreamCatalog.levelOf(fraction = 0.5f, maxLevel = 0))
        assertEquals(0, AudioStreamCatalog.levelOf(fraction = 0.5f, maxLevel = -3))
    }

    @Test
    fun `the level text is the number and its ceiling, never a percentage`() {
        assertEquals("7 / 15", AudioStreamCatalog.levelText(7, 15))
        assertEquals("0 / 0", AudioStreamCatalog.levelText(0, 0))
    }
}
