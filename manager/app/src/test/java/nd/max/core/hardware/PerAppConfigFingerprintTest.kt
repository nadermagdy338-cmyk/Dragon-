/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * بصمة إعداد التطبيق **من غير مقابض سقف الـGPU** — وهي البوابة التي يقرّر بها `AppMonitor`:
 * «هل لمس المستخدم سقف الـGPU وحده؟» فإن نعم يُعاد المقبض وحده، وإلا فالمسار الكامل.
 *
 * ولأن الخطأ هنا ليس شكلًا بل **مسار تنفيذ**: بصمة تُساوي ما لا يتساوى تُقرأ «تغيّر المقبض وحده»
 * فيُتخطّى تراجع بقية الإعدادات. ولهذا الملف كُتب نصًّا داخلًا ونصًّا خارجًا بلا جهاز.
 */
class PerAppConfigFingerprintTest {

    private val json = """
        {
          "com.game.one": {"gpu_profile":"gaming","cpu_governor":"performance","dnd_on_gaming":"on"},
          "com.game.ab": {"gpu_profile":"power"},
          "com.other": {"gpu_max_freq":"650000000"}
        }
    """.trimIndent()

    private fun fingerprintFor(pkg: String, text: String = json): String? =
        PerAppConfigFingerprint.withoutGpuCeiling(text, pkg)

    @Test
    fun `no entry means no fast path`() {
        assertNull(fingerprintFor("com.missing"))
        assertNull(PerAppConfigFingerprint.withoutGpuCeiling(null, "com.game.one"))
        assertNull(PerAppConfigFingerprint.withoutGpuCeiling("", "com.game.one"))
        assertNull(PerAppConfigFingerprint.withoutGpuCeiling(json, "   "))
    }

    @Test
    fun `the three gpu ceiling keys are invisible and the rest is not`() {
        val base = fingerprintFor("com.game.one")

        val otherProfile = fingerprintFor(
            "com.game.one",
            json.replace("\"gpu_profile\":\"gaming\"", "\"gpu_profile\":\"performance\""),
        )
        val otherCeiling = fingerprintFor(
            "com.game.one",
            json.replace("\"gpu_profile\":\"gaming\"", "\"gpu_max_freq\":\"650000000\""),
        )
        val otherLegacy = fingerprintFor(
            "com.game.one",
            json.replace("\"gpu_profile\":\"gaming\"", "\"thermal_profile\":\"powersave\""),
        )

        assertEquals("a ceiling-only change must not move the fingerprint", base, otherProfile)
        assertEquals(base, otherCeiling)
        assertEquals(base, otherLegacy)

        val otherGovernor = fingerprintFor(
            "com.game.one",
            json.replace("\"cpu_governor\":\"performance\"", "\"cpu_governor\":\"powersave\""),
        )
        val otherToggle = fingerprintFor(
            "com.game.one",
            json.replace("\"dnd_on_gaming\":\"on\"", "\"dnd_on_gaming\":\"off\""),
        )
        assertNotEquals("any other field must move the fingerprint", base, otherGovernor)
        assertNotEquals(base, otherToggle)
        assertTrue("the fingerprint keeps the remaining fields", base!!.contains("\"cpu_governor\""))
    }

    @Test
    fun `a longer package name is not mistaken for the entry`() {
        // «com.game.a» نصّ ظاهر داخل «com.game.ab» — والقراءة يجب أن تخصّ الإدخال لا النصّ.
        assertNull(fingerprintFor("com.game.a"))
        assertNull(fingerprintFor("com.game."))
        assertNotEquals(fingerprintFor("com.game.one"), fingerprintFor("com.game.ab"))
    }

    @Test
    fun `whitespace and separators are normalized, not the content`() {
        val roomy = """
            {
              "com.game.one": {
                  "gpu_profile" : "gaming" ,
                  "cpu_governor" : "performance" ,
                  "dnd_on_gaming" : "on"
              }
            }
        """.trimIndent()
        val compact = "{\"com.game.one\":{\"gpu_profile\":\"gaming\",\"cpu_governor\":\"performance\",\"dnd_on_gaming\":\"on\"}}"
        assertEquals(fingerprintFor("com.game.one", compact), fingerprintFor("com.game.one", roomy))
        assertEquals("gapless output", compact.let { fingerprintFor("com.game.one", it) }!!.replace(" ", ""),
            fingerprintFor("com.game.one", roomy))
    }

    @Test
    fun `a block that only holds ceiling keys yields an empty fingerprint and no dangling comma`() {
        val only = "{\"com.other\":{\"gpu_profile\":\"gaming\",\"gpu_max_freq\":\"650000000\"}}"
        val fingerprint = fingerprintFor("com.other", only)
        assertEquals("", fingerprint)
        val kept = "{\"com.other\":{\"gpu_profile\":\"gaming\",\"cpu_boost\":\"on\",\"gpu_max_freq\":\"650000000\"}}"
        val mixed = fingerprintFor("com.other", kept)!!
        assertTrue("no dangling separator: $mixed", !mixed.startsWith(",") && !mixed.endsWith(",") && !mixed.contains(",,"))
        assertTrue(mixed.contains("cpu_boost"))
    }
}
