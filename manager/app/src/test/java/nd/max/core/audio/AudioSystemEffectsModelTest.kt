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
 * ملفّ تهيئةٍ يمثّل ما يُقرأ من جهاز: مكتبةٌ ومؤثّرٌ وربطٌ بمخرجٍ بعينه — **مشتركٌ مع اختبار مولّد
 * الوحدة** (في الملفّ نفسه، فلا يُنسخ نموذجٌ ينحرف عن النموذج المُختبَر).
 */
internal val DEVICE_XML = """
    <?xml version="1.0" encoding="utf-8"?>
    <audio_effects version="2.0">
        <libraries>
            <library name="bundle" path="libbundlewrapper.so"/>
        </libraries>
        <effects>
            <effect name="bassboost" library="bundle" uuid="1d4033c0-8557-11df-9f2d-0002a5d5c51b"/>
        </effects>
        <deviceEffects>
            <device type="AUDIO_DEVICE_OUT_SPEAKER">
                <apply effect="bassboost"/>
            </device>
        </deviceEffects>
    </audio_effects>
""".trimIndent()

private const val EXISTING_UUID = "1d4033c0-8557-11df-9f2d-0002a5d5c51b"

private const val NEW_UUID = "2c1a0f4e-0d2b-4b6a-9f31-5c7d8e9f0a1b"

private fun deviceDocument(): AudioEffectsDocument = AudioEffectsDocument.parse(DEVICE_XML)!!

private fun addition(
    libraryName: String = "myfx",
    libraryPath: String = "libmyfx.so",
    effectName: String = "myeffect",
    effectUuid: String = NEW_UUID,
    deviceTypes: List<String> = emptyList(),
    effectType: String? = null,
) = AudioEffectAddition(
    libraryName = libraryName,
    libraryPath = libraryPath,
    effectName = effectName,
    effectUuid = effectUuid,
    deviceTypes = deviceTypes,
    effectType = effectType,
)

private fun refusalReason(result: AudioOverlayResult): String =
    (result as AudioOverlayResult.Refused).reason

private fun overlayOf(result: AudioOverlayResult): AudioOverlayResult.Overlay =
    result as AudioOverlayResult.Overlay

/** تطبيعٌ للمقارنة: ترتيب السمات يُسقَط (لا دلالة له)، وترتيب الأبناء يبقى (له دلالة). */
private fun normalize(node: AudioXmlNode): AudioXmlNode = node.copy(
    attributes = node.attributes.sortedBy { it.first },
    children = node.children.map(::normalize),
)

/**
 * وثيقة `audio_effects.xml` والدمج الآمن — **مقيسةٌ على JVM وحدها**.
 *
 * **والعطب الذي يُغلق هذا الملفّ أثقل عطبٍ في الموجة كلها:** الملفّ المكتوب **يحلّ محلّ ملفّ النظام**،
 * فالكتابة الخاطئة ليست «إعدادًا لم يُطبَّق» بل تهيئةً لا يقرأها `audioserver`. ولذلك تُقاس هنا
 * **الرفوس** قبل النجاحات: تناقضُ مكتبة · تناقضُ مؤثّر · طلبٌ مُعلَنٌ سابقًا · إضافةٌ تُكسر XML.
 */
class AudioSystemEffectsModelTest {

    // ── قراءة ما على الجهاز ────────────────────────────────────────────

    @Test
    fun `the document reports exactly what the file declared`() {
        val document = deviceDocument()
        assertEquals("2.0", document.version)
        assertEquals(listOf(AudioEffectLibrary("bundle", "libbundlewrapper.so")), document.libraries)
        assertEquals(
            listOf(AudioEffectDeclaration("bassboost", "bundle", EXISTING_UUID)),
            document.effects,
        )
        assertEquals(
            listOf(AudioEffectDeviceAttachment("AUDIO_DEVICE_OUT_SPEAKER", listOf("bassboost"))),
            document.deviceAttachments,
        )
    }

    @Test
    fun `a version that is not declared is null and not a number we invented`() {
        val document = AudioEffectsDocument.parse("<audio_effects><libraries/></audio_effects>")!!
        assertNull(document.version)
        assertEquals(emptyList<AudioEffectLibrary>(), document.libraries)
        assertEquals(emptyList<AudioEffectDeclaration>(), document.effects)
        assertEquals(emptyList<AudioEffectDeviceAttachment>(), document.deviceAttachments)
    }

    @Test
    fun `a file whose root is not audio_effects is not a document`() {
        assertNull(AudioEffectsDocument.parse("<audio_effects_config version=\"2.0\"/>"))
        assertNull(AudioEffectsDocument.parse("<audio_effects><libraries>"))
        assertNull(AudioEffectsDocument.parse(""))
    }

    // ── المسار: الطبقة تقع على الملفّ الذي تقرأه المنصّة فعلًا ─────────

    @Test
    fun `every root the platform searches maps under the module's system folder`() {
        // **وهذا ليس تفصيلًا شكليًّا:** مجلّد `system` وحده هو الذي يُدمجه Magisk في النظام؛ وما في
        // جذر الوحدة من `vendor`/`product` روابط يولّدها Magisk نفسه ولا تُركَّب. فخطّ `vendor/etc/...`
        // المباشر كان طبقةً لا يراها أحد.
        assertEquals("system/odm/etc/audio_effects.xml", AudioEffectsPaths.moduleRelativePath("/odm/etc/audio_effects.xml"))
        assertEquals("system/vendor/etc/audio_effects.xml", AudioEffectsPaths.moduleRelativePath("/vendor/etc/audio_effects.xml"))
        assertEquals("system/etc/audio_effects.xml", AudioEffectsPaths.moduleRelativePath("/system/etc/audio_effects.xml"))
        assertEquals("system/product/etc/audio_effects.xml", AudioEffectsPaths.moduleRelativePath("/product/etc/audio_effects.xml"))
        assertEquals("system/system_ext/etc/audio_effects.xml", AudioEffectsPaths.moduleRelativePath("/system_ext/etc/audio_effects.xml"))
    }

    @Test
    fun `the SELinux label follows the partition, as a declared best guess`() {
        // وسمُ `/vendor` و`/odm` ملفّات تهيئة مصنّع؛ وما عداه نظاميّ. والوسم الخاطئ يمنع `audioserver`
        // من قراءة الطبقة — وهو مُعلَن هنا كي يُقاس على أوّل جهاز.
        assertEquals(
            AudioEffectsPaths.VENDOR_CONFIGS_LABEL,
            AudioEffectsPaths.overlayLabel("system/vendor/etc/audio_effects.xml"),
        )
        assertEquals(
            AudioEffectsPaths.VENDOR_CONFIGS_LABEL,
            AudioEffectsPaths.overlayLabel("system/odm/etc/audio_effects.xml"),
        )
        assertEquals(
            AudioEffectsPaths.SYSTEM_FILE_LABEL,
            AudioEffectsPaths.overlayLabel("system/etc/audio_effects.xml"),
        )
        assertEquals(
            AudioEffectsPaths.SYSTEM_FILE_LABEL,
            AudioEffectsPaths.overlayLabel("system/product/etc/audio_effects.xml"),
        )
    }

    @Test
    fun `a path that would not be mounted is refused instead of promised`() {
        assertNull(AudioEffectsPaths.moduleRelativePath("/data/adb/audio_effects.xml"))
        assertNull(AudioEffectsPaths.moduleRelativePath("/etc/audio_effects.xml"))
        assertNull(AudioEffectsPaths.moduleRelativePath("vendor/etc/audio_effects.xml"))
        assertNull(AudioEffectsPaths.moduleRelativePath("/vendor//etc/audio_effects.xml"))
        assertNull(AudioEffectsPaths.moduleRelativePath("/vendor/../etc/audio_effects.xml"))
        assertNull(AudioEffectsPaths.moduleRelativePath("/vendor/etc/audio_effect.xml"))
        assertNull(AudioEffectsPaths.moduleRelativePath(""))
    }

    // ── الإضافة: صحة الإدخال ───────────────────────────────────────────

    @Test
    fun `an addition is parsed from one line`() {
        val parsed = audioEffectAdditionOf(
            "myfx|libmyfx.so|myeffect|$NEW_UUID|AUDIO_DEVICE_OUT_SPEAKER,AUDIO_DEVICE_OUT_BLUETOOTH_A2DP",
        )
        assertEquals(
            addition(
                deviceTypes = listOf("AUDIO_DEVICE_OUT_SPEAKER", "AUDIO_DEVICE_OUT_BLUETOOTH_A2DP"),
            ),
            parsed,
        )
    }

    @Test
    fun `a short or broken line is null, never a guess`() {
        assertNull(audioEffectAdditionOf("myfx|libmyfx.so|myeffect"))
        assertNull(audioEffectAdditionOf("myfx|libmyfx.so|myeffect|not-a-uuid"))
        assertNull(audioEffectAdditionOf("my fx|libmyfx.so|myeffect|$NEW_UUID"))
        assertNull(audioEffectAdditionOf(""))
        // والخامس يُهمَل فلا يُغيّر النتيجة، والفارغ يعني «بلا أجهزة».
        assertEquals(addition(), audioEffectAdditionOf("myfx|libmyfx.so|myeffect|$NEW_UUID"))
        assertEquals(addition(), audioEffectAdditionOf("myfx|libmyfx.so|myeffect|$NEW_UUID|"))
    }

    @Test
    fun `validation refuses anything that would break the file or the module`() {
        assertTrue(isValidAddition(addition()))
        assertFalse(isValidAddition(addition(libraryName = "bad<name")))
        assertFalse(isValidAddition(addition(effectName = "with space")))
        assertFalse(isValidAddition(addition(libraryPath = "lib path.so")))
        assertFalse(isValidAddition(addition(effectUuid = "not-a-uuid")))
        assertFalse(isValidAddition(addition(deviceTypes = listOf("bad type"))))
        // ولا جهازٌ مكرّر ولا سقفٌ يُتجاوز: الملفّ يُبنى من إدخال، فلا يُبنى من إدخالٍ عابر ملفٌّ ضخم.
        assertFalse(isValidAddition(addition(deviceTypes = listOf("d1", "d1"))))
        assertFalse(isValidAddition(addition(deviceTypes = (1..17).map { "d$it" })))
        assertTrue(isValidAddition(addition(deviceTypes = (1..16).map { "d$it" })))
        assertFalse(isValidAddition(addition(libraryName = "a".repeat(65))))
        assertFalse(isValidAddition(addition(libraryPath = "a".repeat(161))))
    }

    // ── الدمج: الإضافة تُلحق، وما قُرئ لا يُمسّ ────────────────────────

    @Test
    fun `a new library and effect are appended and nothing read is rewritten`() {
        val overlay = overlayOf(audioEffectsOverlay(deviceDocument(), addition()))
        assertTrue(overlay.addedLibrary)
        assertTrue(overlay.addedEffect)
        // والمخرج معلَنٌ أصلًا في هذا الملفّ فلا يُضاف مرّة ثانية.
        assertTrue(overlay.addedDeviceTypes.isEmpty())

        val document = overlay.document
        assertEquals(listOf("bundle", "myfx"), document.libraries.map { it.name })
        assertEquals(listOf("bassboost", "myeffect"), document.effects.map { it.name })
        assertEquals(1, document.deviceAttachments.size)

        val xml = document.toXml()
        assertTrue(xml.contains("libbundlewrapper.so"))
        assertTrue(xml.contains(EXISTING_UUID))
        assertTrue(xml.contains("<deviceEffects>"))
        assertTrue(xml.contains("myeffect"))
    }

    @Test
    fun `a new output device is attached after the one already there`() {
        val overlay = overlayOf(
            audioEffectsOverlay(
                deviceDocument(),
                addition(deviceTypes = listOf("AUDIO_DEVICE_OUT_BLUETOOTH_A2DP")),
            ),
        )
        assertEquals(listOf("AUDIO_DEVICE_OUT_BLUETOOTH_A2DP"), overlay.addedDeviceTypes)
        assertEquals(
            listOf(
                AudioEffectDeviceAttachment("AUDIO_DEVICE_OUT_SPEAKER", listOf("bassboost")),
                AudioEffectDeviceAttachment("AUDIO_DEVICE_OUT_BLUETOOTH_A2DP", listOf("myeffect")),
            ),
            overlay.document.deviceAttachments,
        )
    }

    @Test
    fun `a library already declared with the same path is reused, never duplicated`() {
        val overlay = overlayOf(
            audioEffectsOverlay(
                deviceDocument(),
                addition(libraryName = "bundle", libraryPath = "libbundlewrapper.so"),
            ),
        )
        assertFalse(overlay.addedLibrary)
        assertTrue(overlay.addedEffect)
        assertEquals(listOf("bundle"), overlay.document.libraries.map { it.name })
    }

    @Test
    fun `sections that do not exist yet are created at the end of the root`() {
        val bare = AudioEffectsDocument.parse("<audio_effects version=\"2.0\"/>")!!
        val overlay = overlayOf(
            audioEffectsOverlay(bare, addition(deviceTypes = listOf("AUDIO_DEVICE_OUT_SPEAKER"))),
        )
        assertEquals(listOf("libraries", "effects", "deviceEffects"), overlay.document.root.children.map { it.name })
        assertEquals("2.0", overlay.document.version)
        assertTrue(overlay.changed)
    }

    @Test
    fun `a section we do not understand survives the merge`() {
        val withUnknown = AudioEffectsDocument.parse(
            "<audio_effects version=\"2.0\"><postprocess><stream type=\"AUDIO_STREAM_MUSIC\" path=\"libpost.so\"/></postprocess></audio_effects>",
        )!!
        val overlay = overlayOf(audioEffectsOverlay(withUnknown, addition()))
        assertEquals(listOf("postprocess", "libraries", "effects"), overlay.document.root.children.map { it.name })
        assertTrue(overlay.document.toXml().contains("libpost.so"))
    }

    @Test
    fun `the merged document reparses into the same tree it will be written from`() {
        val overlay = overlayOf(audioEffectsOverlay(deviceDocument(), addition(deviceTypes = listOf("AUDIO_DEVICE_OUT_SPEAKER"))))
        val reparsed = AudioEffectsDocument.parse(overlay.document.toXml())!!
        // المقارنة على **مجموعة السمات وقيمها** لا على ترتيبها: المُحلِّل يرتّب سمات العنصر بالاسم
        // (مقيس في `AudioEffectsXmlTest`)، والترتيب في XML لا دلالة له. والمقيس هنا أنّ ما أُضيف
        // وما قُرئ يعودان كما كُتبا.
        assertEquals(normalize(overlay.document.root), normalize(reparsed.root))
    }

    // ── الرفوس: كلٌّ بسببه المكتوب ─────────────────────────────────────

    @Test
    fun `a conflicting library is refused`() {
        assertEquals(
            AudioOverlayReason.LIBRARY_CONFLICT,
            refusalReason(audioEffectsOverlay(deviceDocument(), addition(libraryName = "bundle", libraryPath = "libother.so"))),
        )
    }

    @Test
    fun `a conflicting effect is refused whether the library or the uuid differs`() {
        // المكتبة مطابقة تمامًا (اسمًا ومسارًا)، فيقع التناقض في المؤثّر وحده لا في المكتبة.
        assertEquals(
            AudioOverlayReason.EFFECT_CONFLICT,
            refusalReason(
                audioEffectsOverlay(
                    deviceDocument(),
                    addition(
                        effectName = "bassboost",
                        libraryName = "bundle",
                        libraryPath = "libbundlewrapper.so",
                        effectUuid = NEW_UUID,
                    ),
                ),
            ),
        )
        assertEquals(
            AudioOverlayReason.EFFECT_CONFLICT,
            refusalReason(
                audioEffectsOverlay(
                    deviceDocument(),
                    addition(effectName = "bassboost", libraryName = "other", effectUuid = EXISTING_UUID),
                ),
            ),
        )
    }

    @Test
    fun `an addition that is already declared in full writes nothing`() {
        assertEquals(
            AudioOverlayReason.NOTHING_TO_ADD,
            refusalReason(
                audioEffectsOverlay(
                    deviceDocument(),
                    addition(
                        libraryName = "bundle",
                        libraryPath = "libbundlewrapper.so",
                        effectName = "bassboost",
                        effectUuid = EXISTING_UUID,
                        deviceTypes = listOf("AUDIO_DEVICE_OUT_SPEAKER"),
                    ),
                ),
            ),
        )
    }

    // ─────────────────── الاسم الثاني والتشخيص: ما كشفه جهازٌ حقيقيّ ───────────────────

    /**
     * **الاسم الثاني يُبحث عنه** — وعلى `Xiaomi 24129RT7CC` يقرأ مصنع المؤثّرات
     * `audio_effects_config.xml` حرفيًّا، فتجاهله يعني «لا ملفّ» لجهازٍ يملكه.
     */
    @Test
    fun `both config file names are searched, one partition at a time`() {
        val candidates = AudioEffectsPaths.DEVICE_CANDIDATES
        assertTrue("/vendor/etc/${AudioEffectsPaths.FILE_NAME}" in candidates)
        assertTrue("/vendor/etc/${AudioEffectsPaths.ALT_FILE_NAME}" in candidates)
        assertTrue("/odm/etc/${AudioEffectsPaths.ALT_FILE_NAME}" in candidates)
        // والقسم لا يُخلط: كلّ `/odm` قبل أوّل `/vendor`.
        assertTrue(
            candidates.indexOf("/odm/etc/${AudioEffectsPaths.ALT_FILE_NAME}") <
                candidates.indexOf("/vendor/etc/${AudioEffectsPaths.FILE_NAME}"),
        )
    }

    /** والطبقة تُكتب على **الاسم الذي قرأه الجهاز** — فلا تُنتج اسمًا جديدًا لا يقرأه أحد. */
    @Test
    fun `the module path keeps the file name the device actually used`() {
        assertEquals(
            "system/vendor/etc/${AudioEffectsPaths.ALT_FILE_NAME}",
            AudioEffectsPaths.moduleRelativePath("/vendor/etc/${AudioEffectsPaths.ALT_FILE_NAME}"),
        )
        assertNull(AudioEffectsPaths.moduleRelativePath("/vendor/etc/other.xml"))
    }

    /**
     * **والتشخيص يحوّل طريقًا مسدودًا إلى قياس:** «لا يُحلَّل» بلا تفصيلٍ لا تُصلَح، واسمُ الجذر
     * الفعليّ يقول هل الملف بصيغةٍ لا نعرفها أم فاسد.
     */
    @Test
    fun `a foreign root is reported by name, and non-xml is reported as nothing`() {
        assertEquals("not_audio_effects", AudioEffectsDocument.parseDiagnosis("<not_audio_effects/>"))
        assertNull(AudioEffectsDocument.parseDiagnosis("<audio_effects version=\"2.0\"/>"))
        assertNull(AudioEffectsDocument.parseDiagnosis("<unclosed"))
    }

    @Test
    fun `an invalid addition is refused before the file is considered at all`() {
        assertEquals(
            AudioOverlayReason.INVALID_ADDITION,
            refusalReason(audioEffectsOverlay(deviceDocument(), addition(effectUuid = "not-a-uuid"))),
        )
        assertEquals(
            AudioOverlayReason.INVALID_ADDITION,
            refusalReason(audioEffectsOverlay(deviceDocument(), addition(libraryPath = "lib path.so"))),
        )
    }

    // ──────────────── سمة `type`: العطب الذي يجعل مؤثّرًا صحيحًا غير محمَّل بصمت ────────────────

    /**
     * **النوع يُكتب على عنصر `<effect>` لا على المكتبة ولا يُسقَط.**
     *
     * وقُرئ في `EffectConfig::parseLibrary` (AOSP) أنّ `type` تُقرأ من عنصر المؤثّر، وأنّ `findUuid`
     * **يُعيد `false` بغيابها** ⇒ `loadEffectLibs` يطبع `skipping` ولا يفتح المكتبة. وعلى جهاز
     * المالك ظهر الأثر نفسه: `libbassboostsw.so` و`libequalizersw.so` و`libvolumesw.so` في
     * `parseLibrary` ولم تُفتح بـ`openEffectLibrary` قطّ — لأنّ مفعولاتها بلا `type`.
     */
    @Test
    fun `the effect type is written on the effect element when declared`() {
        val overlay = overlayOf(
            audioEffectsOverlay(deviceDocument(), addition(effectType = NEW_UUID)),
        )
        val effect = overlay.document.root
            .childrenNamed(AudioEffectsDocument.SECTION_EFFECTS)
            .flatMap { it.childrenNamed(AudioEffectsDocument.NODE_EFFECT) }
            .first { it.attribute(AudioEffectsDocument.ATTR_NAME) == "myeffect" }
        assertEquals(NEW_UUID, effect.attribute(AudioEffectsDocument.ATTR_TYPE))
        // والقراءة المُسمّاة تُعلنه كذلك — فلا تنحرف عن الشجرة التي ستُكتب.
        assertEquals(
            NEW_UUID,
            overlay.document.effects.first { it.name == "myeffect" }.type,
        )
    }

    /** وما لم يُعلن يبقى غير مكتوب — **لا يُخترع نوعٌ من العدم** (ADR-07). */
    @Test
    fun `an undeclared type is not invented`() {
        val overlay = overlayOf(audioEffectsOverlay(deviceDocument(), addition()))
        val effect = overlay.document.effects.first { it.name == "myeffect" }
        assertNull(effect.type)
    }

    /**
     * **ومؤثّرٌ قائم بلا `type` يُرفض ولا يُصلَح صامتًا:** الطبقة تُضيف فقط ولا تُعدّل عقدةً كتبها غيرنا
     * (ADR-18) — ولا سبيل إلى إسناد سمةٍ لعنصرٍ قائم. فالرفض المُعلَن أصدق من ملفٍّ يُظنّ أنّه أصلح.
     */
    @Test
    fun `an existing effect without type cannot be given one by append-only overlay`() {
        assertEquals(
            AudioOverlayReason.EFFECT_CONFLICT,
            refusalReason(
                audioEffectsOverlay(
                    deviceDocument(),
                    addition(
                        libraryName = "bundle",
                        libraryPath = "libbundlewrapper.so",
                        effectName = "bassboost",
                        effectUuid = EXISTING_UUID,
                        effectType = NEW_UUID,
                    ),
                ),
            ),
        )
    }

    /** ونوعٌ مشوّه يُسقِط قراءة التهيئة كلها كما يُسقطها `uuid` مشوّهة. */
    @Test
    fun `a malformed effect type is refused`() {
        assertEquals(
            AudioOverlayReason.INVALID_ADDITION,
            refusalReason(audioEffectsOverlay(deviceDocument(), addition(effectType = "not-a-uuid"))),
        )
    }

    /** والسطر السادس نوعٌ يُقرأ، والخامس أجهزةٌ كما كان — فسطرٌ قديم بخمسة حقول لا يتغيّر معناه. */
    @Test
    fun `the sixth field carries the type and the fifth still carries devices`() {
        val parsed = audioEffectAdditionOf("myfx|libmyfx.so|myeffect|$NEW_UUID|AUDIO_DEVICE_OUT_SPEAKER|$EXISTING_UUID")
        requireNotNull(parsed)
        assertEquals(listOf("AUDIO_DEVICE_OUT_SPEAKER"), parsed.deviceTypes)
        assertEquals(EXISTING_UUID, parsed.effectType)

        val legacy = audioEffectAdditionOf("myfx|libmyfx.so|myeffect|$NEW_UUID|AUDIO_DEVICE_OUT_SPEAKER")
        requireNotNull(legacy)
        assertEquals(listOf("AUDIO_DEVICE_OUT_SPEAKER"), legacy.deviceTypes)
        assertNull(legacy.effectType)

        // وحقلٌ فارغ = «لم يُعلن» لا «نوعٌ فارغ».
        assertNull(requireNotNull(audioEffectAdditionOf("myfx|libmyfx.so|myeffect|$NEW_UUID||")).effectType)
    }
}
