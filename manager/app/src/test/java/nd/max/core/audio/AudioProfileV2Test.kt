/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import nd.max.core.hardware.HardwareControlKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * بصمات الصوت — **مقيسةٌ على JVM بلا جهاز** (`AQ-07`).
 *
 * **وما يُقاس هنا بالضبط — وهو شرط القبول بالنصّ:**
 * ① الذهاب والعودة يحفظان المفاتيح والقيم كما هي، والحروف الفاصلة (`|` و`%`) مُهرَّبة فلا تُفسد السطر.
 * ② **ترحيل نسخة أقدم يُقاس فعلًا** لا يُدَّعى: ملفّ `v1` يُقرأ ويُرحَّل، ويُعدّ.
 * ③ نسخةٌ **أحدث** من نسختنا تُترك ولا تُفسَّر بتخمين.
 * ④ **لا نمط يَعِد بمؤثّر غير موجود:** كل مدخل يخصّ مؤثّرًا غير قابل للاستعمال يُسقط **ويُسمّى**.
 */
class AudioProfileV2Test {

    @Test
    fun `encode and decode preserve keys and values`() {
        val profile = AudioProfileV2(
            id = "night",
            name = "Night",
            deviceFingerprint = "Google Pixel",
            entries = mapOf(
                HardwareControlKey.audioStream("media") to "9",
                HardwareControlKey.audioEffect("equalizer", "eq_band_0") to "250",
            ),
        )
        val decoded = decodeProfiles(encodeProfiles(listOf(profile)))
        assertEquals(1, decoded.profiles.size)
        assertEquals(profile, decoded.profiles.first())
        assertEquals(0, decoded.migratedFromV1)
        assertEquals(0, decoded.skipped)
    }

    @Test
    fun `separators inside a name survive the round trip`() {
        val profile = AudioProfileV2(
            id = "odd",
            name = "My | Profile % 100",
            entries = mapOf("key|with|pipes" to "value % 5"),
        )
        val decoded = decodeProfiles(encodeProfiles(listOf(profile)))
        assertEquals("My | Profile % 100", decoded.profiles.first().name)
        assertEquals("value % 5", decoded.profiles.first().entries["key|with|pipes"])
    }

    @Test
    fun `version one is migrated and counted`() {
        val v1 = """
            #maxmanager-audio-profile v1
            My Profile
            entry|audio_stream:media|7
            end
        """.trimIndent()
        val decoded = decodeProfiles(v1)
        assertEquals(1, decoded.migratedFromV1)
        assertEquals(1, decoded.profiles.size)
        val profile = decoded.profiles.first()
        assertEquals(AUDIO_PROFILE_SCHEMA, profile.schemaVersion)
        assertEquals("my-profile", profile.id)
        assertEquals("My Profile", profile.name)
        // ولا جهاز مربوطًا: النسخة الأولى لم تحمل بصمة جهاز، فلا يُخترع لها واحد.
        assertEquals(null, profile.deviceFingerprint)
        assertEquals("7", profile.entries[HardwareControlKey.audioStream("media")])
    }

    @Test
    fun `a newer schema is left alone instead of being guessed`() {
        val future = "#maxmanager-audio-profile v99\nprofile|a|b|\nentry|k|v\nend\n"
        val decoded = decodeProfiles(future)
        assertTrue(decoded.profiles.isEmpty())
        assertEquals(1, decoded.skipped)
    }

    @Test
    fun `a malformed file never throws`() {
        assertEquals(0, decodeProfiles(null).profiles.size)
        assertEquals(0, decodeProfiles("").profiles.size)
        // ترويسة غائبة ⇒ تُترك وتُعدّ، ولا يُفسَّر نصٌّ حرّ على أنه ملفّ بصمات.
        assertEquals(1, decodeProfiles("hello world").skipped)
        // وسطر مشوّه داخل ملفّ صالح يُتجاهل وحده.
        val mixed = "#maxmanager-audio-profile v2\nprofile|a|A||\nnonsense\nentry|k|v\nend\n"
        val decoded = decodeProfiles(mixed)
        assertEquals(1, decoded.profiles.size)
        assertEquals("v", decoded.profiles.first().entries["k"])
    }

    @Test
    fun `pruning drops entries whose effect the device does not offer`() {
        val profile = AudioProfileV2(
            id = "p",
            name = "P",
            entries = mapOf(
                HardwareControlKey.audioStream("media") to "9",
                HardwareControlKey.audioEffect("bass_boost", "strength") to "500",
                HardwareControlKey.audioEffect("equalizer", "eq_band_0") to "250",
                HardwareControlKey.audioEffect("not_a_real_effect", "x") to "1",
            ),
        )
        val pruned = pruneProfileForCapabilities(profile, attachableEffectTokens = setOf("equalizer"))
        assertEquals(
            setOf(HardwareControlKey.audioEffect("equalizer", "eq_band_0"), HardwareControlKey.audioStream("media")),
            pruned.profile.entries.keys,
        )
        assertEquals(2, pruned.droppedKeys.size)
    }

    @Test
    fun `slug keeps letters and digits and never collapses to nothing`() {
        assertEquals("my-profile", slugOf("  My  Profile!!  "))
        assertEquals("profile", slugOf("!!!"))
        assertEquals("profile", slugOf(null))
        assertEquals("a-b", slugOf("a b"))
    }
}
