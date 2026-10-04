/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حكم الطبقة النظاميّة — **مقيسٌ على JVM وحدها**.
 *
 * **وما يقيسه هذا الملفّ ليس التصنيف بل الترتيب:** الترتيب هو ما يجعل الشاشة تقول السبب **الحقيقيّ**
 * لا أوّل سبب عثرنا عليه. فمن لا جذر عنده ليس «مساره غير قابل للكتابة»، ومن ملفّه لم يُقرأ ليس
 * «إضافته مرفوضة». والاختبار يُغذّي **كل** العلل معًا ويطالب بالسبب الأسبق — فلا يُخفي عطبٌ عطبًا.
 */
class AudioSystemVerdictTest {

    private fun evidence(
        rootAvailable: Boolean = true,
        sourcePath: String? = "/vendor/etc/audio_effects.xml",
        sourceParsed: Boolean = true,
        additionReason: String? = null,
        overlayReady: Boolean = true,
        modulePathWritable: Boolean = true,
    ) = AudioSystemEvidence(
        rootAvailable = rootAvailable,
        sourcePath = sourcePath,
        sourceParsed = sourceParsed,
        additionReason = additionReason,
        overlayReady = overlayReady,
        modulePathWritable = modulePathWritable,
    )

    @Test
    fun `everything measured ready means ready and it says a reboot is required`() {
        val verdict = audioSystemLayerVerdict(evidence())
        assertEquals(AudioSystemLayerStatus.READY, verdict.status)
        assertEquals(AudioSystemReason.READY, verdict.reason)
        assertTrue(verdict.requiresReboot)
        assertTrue(verdict.isReady)
    }

    @Test
    fun `the earliest missing condition is the one reported`() {
        val everythingBroken = evidence(
            rootAvailable = false,
            sourcePath = null,
            sourceParsed = false,
            additionReason = AudioOverlayReason.LIBRARY_CONFLICT,
            overlayReady = false,
            modulePathWritable = false,
        )

        val noRoot = audioSystemLayerVerdict(everythingBroken)
        assertEquals(AudioSystemLayerStatus.NEEDS_ROOT, noRoot.status)
        assertEquals(AudioSystemReason.ROOT_REQUIRED, noRoot.reason)
        assertFalse(noRoot.requiresReboot)

        val noFile = audioSystemLayerVerdict(everythingBroken.copy(rootAvailable = true))
        assertEquals(AudioSystemLayerStatus.SOURCE_UNAVAILABLE, noFile.status)
        assertEquals(AudioSystemReason.NO_EFFECTS_FILE, noFile.reason)

        val unreadable = audioSystemLayerVerdict(
            everythingBroken.copy(rootAvailable = true, sourcePath = "/odm/etc/audio_effects.xml"),
        )
        assertEquals(AudioSystemLayerStatus.SOURCE_UNREADABLE, unreadable.status)
        assertEquals(AudioSystemReason.SOURCE_UNPARSABLE, unreadable.reason)

        val refused = audioSystemLayerVerdict(
            everythingBroken.copy(
                rootAvailable = true,
                sourcePath = "/odm/etc/audio_effects.xml",
                sourceParsed = true,
            ),
        )
        assertEquals(AudioSystemLayerStatus.ADDITION_REFUSED, refused.status)
        // والسبب يُنقل حرفيًّا: من يصنّفه يعرف أيّه، والحكم لا يُعيد تصنيفه من جديد.
        assertEquals(AudioOverlayReason.LIBRARY_CONFLICT, refused.reason)

        val unwritable = audioSystemLayerVerdict(
            everythingBroken.copy(
                rootAvailable = true,
                sourcePath = "/odm/etc/audio_effects.xml",
                sourceParsed = true,
                additionReason = null,
                overlayReady = true,
            ),
        )
        assertEquals(AudioSystemLayerStatus.MODULE_UNWRITABLE, unwritable.status)
        assertEquals(AudioSystemReason.MODULE_PATH_UNWRITABLE, unwritable.reason)
    }

    @Test
    fun `a ready overlay with no writable module directory is not ready`() {
        val verdict = audioSystemLayerVerdict(evidence(modulePathWritable = false))
        assertEquals(AudioSystemLayerStatus.MODULE_UNWRITABLE, verdict.status)
        assertFalse(verdict.isReady)
        assertFalse(verdict.requiresReboot)
    }

    @Test
    fun `an addition that was not built is refused for having nothing to add`() {
        val verdict = audioSystemLayerVerdict(evidence(overlayReady = false))
        assertEquals(AudioSystemLayerStatus.ADDITION_REFUSED, verdict.status)
        assertEquals(AudioSystemReason.NO_ADDITION, verdict.reason)
        assertFalse(verdict.requiresReboot)
    }

    @Test
    fun `every status has its own token`() {
        val tokens = AudioSystemLayerStatus.entries.map { it.token }
        assertEquals(tokens.size, tokens.distinct().size)
        assertEquals("ready", AudioSystemLayerStatus.READY.token)
        assertEquals("needs_root", AudioSystemLayerStatus.NEEDS_ROOT.token)
        assertEquals("source_unavailable", AudioSystemLayerStatus.SOURCE_UNAVAILABLE.token)
        assertEquals("source_unreadable", AudioSystemLayerStatus.SOURCE_UNREADABLE.token)
        assertEquals("addition_refused", AudioSystemLayerStatus.ADDITION_REFUSED.token)
        assertEquals("module_unwritable", AudioSystemLayerStatus.MODULE_UNWRITABLE.token)
    }
}
