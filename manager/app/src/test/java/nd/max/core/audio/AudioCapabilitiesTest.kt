/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * الصوت — **الطبقة الصافية وحدها، مُختبَرة على الـJVM**.
 *
 * **والحدّ المُعلن أوّلًا:** ما لا يُقاس هنا هو **القراءة**: `AudioManager.getDevices` و
 * `AudioManager.getProperty` و`AudioEffect.queryEffects` تلمس المنصّة، ولا `android.jar` في هذه
 * البيئة، فالاختبار **لا يصنع `AudioManager` مزيّفًا**. والمقيس هو **ما يُفعل بما أعلنته المنصّة**:
 * الرموز، والترتيب الحتميّ، وتحويل الخاصيّتين النصّيّتين إلى رقم أو `null`.
 *
 * **وثلاثة أعطاب تُغلقها هذه البوابة، كلها واقعة لا مُتخيَّلة:**
 *
 * ١. **رقم مصنوع من غياب قراءة:** المنصّة تُعيد الخاصيّتين **نصًّا** (`getProperty`)، و`toIntOrNull`
 * وحدها تُحوّل `"0"` إلى `0` — فيُعرض «0 هرتز» على جهاز لا يُعلن معدّلًا. والقاعدة هنا:
 * **الموجب وحده قراءة**، وما عداه `null` يُعرض «—» (ADR-07).
 * ٢. **ترتيب يتبع المنصّة:** `getDevices` تُعيد ما شاء ترتيب النظام الداخليّ؛ فقائمة تُعرض بترتيب
 * مختلف في كل فتح تُقرأ كقائمة مختلفة. والاختبار يُغذّي الترتيب الرسميّ مقلوبًا ويطالب بالترتيب
 * نفسه، فيثبت أن الترتيب **من طبقتنا** لا من المنصّة.
 * ٣. **رمز مجهول يُسمّى:** `TYPE_ECHO_REFERENCE` (`28`) متروك عمدًا (مُعلَّم `@hide` ويطلب
 * `CAPTURE_AUDIO_OUTPUT`)، وأي رمز لم نعرفه يعود `null` — فلا يُلحق بأقرب رمز معروف.
 */
class AudioCapabilitiesTest {

    // ── رموز أنواع الأجهزة ────────────────────────────────────────────

    @Test
    fun `every device type the platform defines has a token, and 28 is left out on purpose`() {
        // المقروء من `AudioDeviceInfo.java` (AOSP، فرع `android16-release`): ١..٢٧ ثم ٢٩..٣١.
        val expected = mapOf(
            1 to "builtin_earpiece",
            2 to "builtin_speaker",
            3 to "wired_headset",
            4 to "wired_headphones",
            5 to "line_analog",
            6 to "line_digital",
            7 to "bluetooth_sco",
            8 to "bluetooth_a2dp",
            9 to "hdmi",
            10 to "hdmi_arc",
            11 to "usb_device",
            12 to "usb_accessory",
            13 to "dock",
            14 to "fm",
            15 to "builtin_mic",
            16 to "fm_tuner",
            17 to "tv_tuner",
            18 to "telephony",
            19 to "aux_line",
            20 to "ip",
            21 to "bus",
            22 to "usb_headset",
            23 to "hearing_aid",
            24 to "builtin_speaker_safe",
            25 to "remote_submix",
            26 to "ble_headset",
            27 to "ble_speaker",
            29 to "hdmi_earc",
            30 to "ble_broadcast",
            31 to "dock_analog",
        )
        expected.forEach { (code, token) ->
            assertEquals("رمز $code", token, audioDeviceKindToken(code))
        }
        assertEquals("عدد الرموز المُسمّاة", 30, expected.size)

        // والمتروك والمجهول: `null` لا اسم مخترع.
        assertNull("28 متروك عمدًا (@hide + CAPTURE_AUDIO_OUTPUT)", audioDeviceKindToken(28))
        assertNull(audioDeviceKindToken(0))
        assertNull(audioDeviceKindToken(32))
        assertNull(audioDeviceKindToken(-1))
    }

    // ── الترتيب الحتميّ (AU-02: «مرتَّبة ومُوسَمة لا عشوائية») ────────

    @Test
    fun `sinks come first, then unknown kinds last, and the order does not follow the platform`() {
        // الترتيب الذي «أعلنته» المنصّة عمدًا مقلوبًا ومبعثرًا: مدخل، ثم مجهول، ثم مخرج.
        val asDeclared = listOf(
            descriptor(id = 5, type = 15, sink = false, name = "Built-in mic"),      // مدخل
            descriptor(id = 3, type = 99, sink = false, name = "Vendor port"),        // نوع لا نعرفه
            descriptor(id = 9, type = 2, sink = true, name = "Speaker"),             // مخرج
            descriptor(id = 2, type = 8, sink = true, name = "BT buds"),             // مخرج
        )
        val sorted = audioDevicesSorted(asDeclared)

        assertEquals(
            listOf("BT buds", "Speaker", "Built-in mic", "Vendor port"),
            sorted.map { it.productName },
        )
        // والمخرجان قبل كل مدخل، والمجهول أخيرًا.
        assertEquals(listOf(true, true, false, false), sorted.map { it.isSink })
        assertEquals("Vendor port", sorted.last().productName)
    }

    @Test
    fun `the same list in any input order yields the same order`() {
        val devices = listOf(
            descriptor(id = 1, type = 2, sink = true, name = "speaker"),
            descriptor(id = 4, type = 4, sink = true, name = "Headphones"),
            descriptor(id = 2, type = 2, sink = true, name = "SPEAKER"),
            descriptor(id = 7, type = 15, sink = false, name = "mic"),
            descriptor(id = 6, type = 99, sink = true, name = "zz"),
        )
        val once = audioDevicesSorted(devices)
        val twice = audioDevicesSorted(devices.reversed())
        val thrice = audioDevicesSorted(devices.shuffled())
        assertEquals(once, twice)
        assertEquals(once, thrice)

        // والاسم يُقارن بأحرف صغيرة، والتماثل يُحسم بالمعرّف — فالقائمة واحدة دائمًا.
        val sameName = listOf(
            descriptor(id = 5, type = 2, sink = true, name = "Same"),
            descriptor(id = 4, type = 2, sink = true, name = "same"),
            descriptor(id = 6, type = 2, sink = true, name = "same"),
        )
        assertEquals(listOf(4, 5, 6), audioDevicesSorted(sameName).map { it.id })
    }

    // ── تحويل خاصيّتَي المنصّة النصّيّتين ────────────────────────────

    @Test
    fun `a platform property that is not a positive number is unread, never zero`() {
        val devices = listOf(descriptor(id = 1, type = 2, sink = true, name = "Speaker"))

        // القراءة الحقيقية: النصّ كما تُخرجه المنصّة.
        val read = audioOutputCapabilitiesOf("48000", "240", devices)
        assertEquals(48000, read.sampleRateHz)
        assertEquals(240, read.framesPerBuffer)
        assertEquals(devices, read.devices)

        // والفراغ حول النصّ لا يُفسده (`getProperty` لا تعد بأن لا فراغ).
        assertEquals(44100, audioOutputCapabilitiesOf(" 44100 ", null, emptyList()).sampleRateHz)

        // وما ليس رقمًا موجبًا = «غير مقروء»: لا صفر ولا تخمين.
        listOf("0", "", " ", "-1", "-48000", "abc", "48k", "48.0").forEach { text ->
            val unread = audioOutputCapabilitiesOf(text, text, emptyList())
            assertNull("«$text» ليست معدّلًا مقروءًا", unread.sampleRateHz)
            assertNull("«$text» ليست دورة مقروءة", unread.framesPerBuffer)
        }
        assertNull(audioOutputCapabilitiesOf(null, null, emptyList()).sampleRateHz)
        assertNull(audioOutputCapabilitiesOf(null, null, emptyList()).framesPerBuffer)
    }

    @Test
    fun `the capabilities order their own devices too`() {
        val capabilities = audioOutputCapabilitiesOf(
            sampleRateText = "48000",
            framesPerBufferText = "192",
            devices = listOf(
                descriptor(id = 1, type = 15, sink = false, name = "mic"),
                descriptor(id = 2, type = 2, sink = true, name = "Speaker"),
            ),
        )
        assertEquals(listOf("Speaker", "mic"), capabilities.devices.map { it.productName })
    }

    // ── رموز أنواع المؤثرات (المعرّفات من AudioEffect.java في AOSP) ──

    @Test
    fun `every effect type the platform defines maps to its token, and an unknown one does not`() {
        val declared = mapOf(
            "0bed4300-ddd6-11db-8f34-0002a5d5c51b" to "equalizer",
            "0634f220-ddd4-11db-a0fc-0002a5d5c51b" to "bass_boost",
            "37cc2c00-dddd-11db-8577-0002a5d5c51b" to "virtualizer",
            "47382d60-ddd8-11db-bf3a-0002a5d5c51b" to "preset_reverb",
            "c2e5d5f0-94bd-4763-9cac-4e234d06839e" to "env_reverb",
            "7b491460-8d4d-11e0-bd61-0002a5d5c51b" to "aec",
            "0a8abfe0-654c-11e0-ba26-0002a5d5c51b" to "agc",
            "58b4b260-8e06-11e0-aa8e-0002a5d5c51b" to "ns",
            "fe3199be-aed0-413f-87bb-11260eb63cf1" to "loudness_enhancer",
            "7261676f-6d75-7369-6364-28e2fd3ac39e" to "dynamics_processing",
            "1411e6d6-aecd-4021-a1cf-a6aceb0d71e5" to "haptic_generator",
        )
        assertEquals("عدد الأنواع المُسمّاة", 11, declared.size)
        declared.forEach { (uuid, token) ->
            assertEquals(token, audioEffectTypeToken(uuid))
            // والمنصّة تُعيد المعرّف بأحرفه كما هي؛ والمقارنة لا تتأثر بحالة الأحرف.
            assertEquals("$token (كبير)", token, audioEffectTypeToken(uuid.uppercase()))
        }

        // و`EFFECT_TYPE_NULL` ليس نوعًا يُعرض: `null` لا اسم.
        assertNull(audioEffectTypeToken("ec7178ec-e5e1-4432-a3f4-4657e6795210"))
        assertNull(audioEffectTypeToken("00000000-0000-0000-0000-000000000000"))
        assertNull(audioEffectTypeToken(null))
    }

    @Test
    fun `connect mode is read as the platform spells it, and a third spelling is not guessed`() {
        assertEquals("insert", audioEffectConnectToken("Insert"))
        assertEquals("auxiliary", audioEffectConnectToken("Auxiliary"))
        assertEquals("insert", audioEffectConnectToken(" Insert "))

        assertNull(audioEffectConnectToken("insert"))
        assertNull(audioEffectConnectToken("Post"))
        assertNull(audioEffectConnectToken(""))
        assertNull(audioEffectConnectToken(null))
    }

    @Test
    fun `effects are ordered by type, unknown types last, then by name`() {
        val effects = listOf(
            effect(name = "Zeta", type = null),
            effect(name = "Bass", type = "0634f220-ddd4-11db-a0fc-0002a5d5c51b"),
            effect(name = "Eq", type = "0bed4300-ddd6-11db-8f34-0002a5d5c51b"),
            effect(name = "Alpha", type = null),
        )
        val sorted = audioEffectsSorted(effects)
        assertEquals(listOf("Bass", "Eq", "Alpha", "Zeta"), sorted.map { it.name })
        assertEquals(sorted, audioEffectsSorted(effects.reversed()))
    }
}

private fun descriptor(
    id: Int,
    type: Int,
    sink: Boolean,
    name: String?,
) = AudioDeviceDescriptor(
    id = id,
    productName = name,
    typeCode = type,
    isSink = sink,
    sampleRatesHz = listOf(48000),
    channelCounts = listOf(2),
    encodings = listOf(1),
)

private fun effect(name: String?, type: String?) = AudioEffectInfo(
    name = name,
    typeUuid = type,
    implementor = "MaxManager",
    connectMode = "Insert",
)
