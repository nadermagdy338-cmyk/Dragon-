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
 * حكم مؤثّر المصنّع — **مقيسٌ على JVM بلا جهاز وبلا `android.jar`**.
 *
 * **وما يُقاس هنا بالضبط:** أنّ «لم أقرأ» ليست «غير موجود» · وأنّ الغياب لا يُثبت إلّا بقراءة ناجحة ·
 * وأنّ `DETECTED_BUT_UNAVAILABLE` هي نتيجةُ «اكتُشف ولا مسار» · وأنّ **نجاح الإرفاق وحده لا يكفي**
 * (التحكّم يُقرأ) · وأنّ الدلالة تُعلَن (`SOURCE_VERIFIED` للمعرّف و`INFERRED` للتخمين) ولا تُرقّى.
 *
 * وما يحتاج جهازًا (هل تُقبل جلسة ٠ فعلًا · هل يمنع نظام الحماية الواجهات المخفيّة) لا يُدّعى هنا:
 * تُقاس **قاعدته**، ويُقاس **رقمه** على الجهاز.
 */
class VendorAudioModelTest {

    private fun descriptor(
        uuid: String,
        name: String? = null,
        implementor: String? = null,
        typeUuid: String? = VendorEffectCatalog.EFFECT_TYPE_NULL_UUID,
    ) = VendorEffectDescriptor(
        uuid = uuid,
        typeUuid = typeUuid,
        name = name,
        implementor = implementor,
        connectMode = "Insert",
    )

    /** معادلٌ من المنصّة: معرّف تنفيذٍ لا نعرفه ولا علامة مصنّع فيه ⇒ **ليس مؤثّر مصنّع**. */
    private val platformEqualizer = descriptor(
        uuid = "0bed4300-ddd6-11db-8f34-0002a5d5c51b",
        name = "Equalizer",
        implementor = "The Android Open Source Project",
        typeUuid = "0bed4300-ddd6-11db-8f34-0002a5d5c51b",
    )

    // ─────────────────────────────── الحضور: ثالثيّةٌ لا ثنائيّة ───────────────────────────────

    /** **و«لم تُقرأ» ليست «غير موجود» (ADR-07):** قائمة غير مقروءة ⇒ `UNKNOWN` بسببها. */
    @Test
    fun `an unreadable effect list stays unknown and never claims absence`() {
        val verdict = vendorAudioVerdict(VendorAudioEvidence(descriptors = null))
        assertEquals(VendorAudioPresence.UNKNOWN, verdict.presence)
        assertEquals(VendorAudioReason.EFFECTS_UNREADABLE, verdict.reason)
        assertNull(verdict.family)
        assertFalse(verdict.isDetected)
        assertFalse(verdict.isControllable)
    }

    /** والقائمة الفارغة **قراءة**: «قرأتُ فلم أجد» — وهذا هو الغياب المُثبت وحده. */
    @Test
    fun `a read but empty list is a proven absence`() {
        val verdict = vendorAudioVerdict(VendorAudioEvidence(descriptors = emptyList()))
        assertEquals(VendorAudioPresence.ABSENT, verdict.presence)
        assertEquals(VendorAudioReason.NO_VENDOR_EFFECT, verdict.reason)
    }

    /** ومؤثّرات المنصّة العاديّة ليست مؤثّرات مصنّع — ولا تُنتسب عائلةً بلا دليل. */
    @Test
    fun `platform effects are not vendor effects and keep the family unclaimed`() {
        val verdict = vendorAudioVerdict(VendorAudioEvidence(descriptors = listOf(platformEqualizer)))
        assertEquals(VendorAudioPresence.ABSENT, verdict.presence)
        assertNull(verdict.family)
        assertNull(verdict.evidence)
    }

    // ──────────────────────────────── العائلة: المعرّف قبل العلامة ────────────────────────────────

    /** **المعرّف المقيس من مصدر المصنّع ⇒ `SOURCE_VERIFIED`** — والرمز كما هو في المصدر. */
    @Test
    fun `the measured dolby dap uuid is a source verified identity`() {
        val identities = vendorEffectIdentities(listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID, "Dolby Audio")))
        assertEquals(1, identities.size)
        assertEquals(VendorEffectFamily.DOLBY_DAP, identities.first().family)
        assertEquals(VendorEvidence.SOURCE_VERIFIED, identities.first().evidence)
    }

    /** والمعرّف يُقارَن بلا حساسيّة لحالة الأحرف — فجهاز يُعلنه كبيرًا ليس جهازًا آخر. */
    @Test
    fun `the effect uuid is matched case insensitively`() {
        val identities = vendorEffectIdentities(
            listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID.uppercase(), null)),
        )
        assertEquals(VendorEvidence.SOURCE_VERIFIED, identities.first().evidence)
        assertEquals(VendorEffectCatalog.DOLBY_DAP_UUID, identities.first().uuid)
    }

    /** والاسم وحده **تخمينٌ يُعلن** (`INFERRED`) ولا يُرقّى إلى يقين مهما بدا صريحًا. */
    @Test
    fun `a name marker is inferred evidence and never promoted`() {
        val identities = vendorEffectIdentities(
            listOf(descriptor("11111111-2222-3333-4444-555555555555", name = "Dolby Atmos")),
        )
        assertEquals(1, identities.size)
        assertEquals(VendorEffectFamily.DOLBY_DAP, identities.first().family)
        assertEquals(VendorEvidence.INFERRED, identities.first().evidence)

        val verdict = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = listOf(descriptor("11111111-2222-3333-4444-555555555555", name = "Dolby Atmos")),
            ),
        )
        assertEquals(VendorEvidence.INFERRED, verdict.evidence)
    }

    /** وعائلةٌ أخرى بعلامتها (`dirac`) تُعرف باسمها لا باسم Dolby. */
    @Test
    fun `another vendor marker resolves to the generic vendor family`() {
        val identities = vendorEffectIdentities(
            listOf(descriptor("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", implementor = "Dirac Research")),
        )
        assertEquals(VendorEffectFamily.VENDOR_DSP, identities.first().family)
    }

    /** وما لا معرّف له ولا علامة **يُسقط ولا يُسمّى** — لا «عائلة مجهولة» تُخترع له. */
    @Test
    fun `an unknown effect without a marker yields no identity`() {
        val identities = vendorEffectIdentities(
            listOf(descriptor("ffffffff-0000-0000-0000-000000000000", name = "Some DSP 3000")),
        )
        assertTrue(identities.isEmpty())
    }

    /** والترتيب حتميّ والتكرار يُطوى — فلا تتبدّل القائمة بين فتحين بلا سبب. */
    @Test
    fun `identities are deduplicated and deterministically ordered`() {
        val a = descriptor("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", implementor = "Dirac Research")
        val dolby = descriptor(VendorEffectCatalog.DOLBY_DAP_UUID, "Dolby Audio")
        val sorted = vendorEffectIdentities(listOf(a, dolby, dolby))
        assertEquals(2, sorted.size)
        assertEquals(sorted, vendorEffectIdentities(listOf(dolby, a, a, dolby, a)))
        // والترتيب بالعائلة ثمّ بالمعرّف: `dolby_dap` قبل `vendor_dsp`.
        assertEquals(VendorEffectFamily.DOLBY_DAP, sorted.first().family)
    }

    // ────────────────────────── الإرفاق: النجاح وحده ليس دليلًا ──────────────────────────

    /** **القلب:** اكتُشف ولم تُجرَّب المحاولة ⇒ `DETECTED_BUT_UNAVAILABLE` بسبب «لم يُقس». */
    @Test
    fun `a detected effect without a measured attempt is detected but unavailable`() {
        val verdict = vendorAudioVerdict(
            VendorAudioEvidence(descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID))),
        )
        assertEquals(VendorAudioPresence.DETECTED_BUT_UNAVAILABLE, verdict.presence)
        assertEquals(VendorAudioReason.ATTACH_NOT_MEASURED, verdict.reason)
        assertEquals(VendorEffectCatalog.DOLBY_DAP_UUID, verdict.effectUuid)
        assertTrue(verdict.isDetected)
        assertFalse(verdict.isControllable)
    }

    /** ولذلك يُضاف سبب المحاولة عند وجوده: `NOT_ATTEMPTED` بمسار الانعكاس بسببٍ مكتوب. */
    @Test
    fun `an explicit not attempted outcome keeps its own reason and route`() {
        val verdict = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID)),
                attach = VendorAttachEvidence(
                    route = VendorAccessRoute.HIDDEN_API,
                    outcome = VendorAttachOutcome.NOT_ATTEMPTED,
                    reason = VendorAudioReason.HIDDEN_API_BLOCKED,
                ),
            ),
        )
        assertEquals(VendorAudioPresence.DETECTED_BUT_UNAVAILABLE, verdict.presence)
        assertEquals(VendorAudioReason.HIDDEN_API_BLOCKED, verdict.reason)
        assertEquals(VendorAccessRoute.HIDDEN_API, verdict.route)
    }

    /** **وأُرفق ونملكه ⇒ الوحيد الذي يُقال عنه قابل للتحكّم.** */
    @Test
    fun `an attached and owned effect is the only controllable state`() {
        val verdict = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID, "Dolby Audio")),
                attach = VendorAttachEvidence(
                    route = VendorAccessRoute.HIDDEN_API,
                    outcome = VendorAttachOutcome.ATTACHED,
                    controlOwned = true,
                ),
            ),
        )
        assertEquals(VendorAudioPresence.DETECTED_CONTROLLABLE, verdict.presence)
        assertEquals(VendorAudioReason.ATTACHED_AND_OWNED, verdict.reason)
        assertTrue(verdict.isControllable)
    }

    /** وأُرفق ولم نكن مالكيه ⇒ **ليس قابلًا للتحكّم** — وهذا أخطر ما يُخفى، فيُسمّى صراحةً. */
    @Test
    fun `an attached effect owned by another app is not controllable`() {
        val ownedByOther = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID)),
                attach = VendorAttachEvidence(
                    VendorAccessRoute.HIDDEN_API, VendorAttachOutcome.ATTACHED, controlOwned = false,
                ),
            ),
        )
        assertEquals(VendorAudioPresence.DETECTED_BUT_UNAVAILABLE, ownedByOther.presence)
        assertEquals(VendorAudioReason.NOT_CONTROLLABLE, ownedByOther.reason)
        assertFalse(ownedByOther.isControllable)

        val unmeasured = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID)),
                attach = VendorAttachEvidence(
                    VendorAccessRoute.HIDDEN_API, VendorAttachOutcome.ATTACHED, controlOwned = null,
                ),
            ),
        )
        assertEquals(VendorAudioReason.CONTROL_UNMEASURED, unmeasured.reason)
        assertFalse(unmeasured.isControllable)
    }

    /** والرفض نتيجةٌ تُقال بسببها — والمسار يُحفظ ليُعرف **ما جُرِّب**. */
    @Test
    fun `a refused attach is reported with the route that refused it`() {
        val refused = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID)),
                attach = VendorAttachEvidence(
                    VendorAccessRoute.HIDDEN_API, VendorAttachOutcome.REFUSED, reason = VendorAudioReason.HIDDEN_API_BLOCKED,
                ),
            ),
        )
        assertEquals(VendorAudioPresence.DETECTED_BUT_UNAVAILABLE, refused.presence)
        assertEquals(VendorAudioReason.HIDDEN_API_BLOCKED, refused.reason)
        assertEquals(VendorAccessRoute.HIDDEN_API, refused.route)

        // وبلا سببٍ خاصّ يبقى السبب العامّ للرفض — ولا سطرٌ بلا سبب.
        val bare = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID)),
                attach = VendorAttachEvidence(VendorAccessRoute.HIDDEN_API, VendorAttachOutcome.REFUSED),
            ),
        )
        assertEquals(VendorAudioReason.ATTACH_REFUSED, bare.reason)
        assertEquals(VendorAccessRoute.HIDDEN_API, bare.route)
    }

    /** و«لا مسار على هذه المنصّة» تُفرّق عن «رُفضت محاولتنا» — وإلّا ضاع أيّهما حدث. */
    @Test
    fun `an unsupported route is distinct from a refusal`() {
        val unsupported = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID)),
                attach = VendorAttachEvidence(
                    VendorAccessRoute.PUBLIC_SDK, VendorAttachOutcome.UNSUPPORTED,
                ),
            ),
        )
        assertEquals(VendorAudioReason.ROUTE_UNSUPPORTED, unsupported.reason)
        assertEquals(VendorAccessRoute.PUBLIC_SDK, unsupported.route)
        assertEquals(VendorAudioPresence.DETECTED_BUT_UNAVAILABLE, unsupported.presence)
    }

    /**
     * وترجمة جردة المنصّة ([`AudioEffectInfo`]) إلى جردة الاكتشاف: **تُنقل المعلومات كما هي**،
     * ويُسقَط ما لا معرّف تنفيذ له — فالمعرّف هو مفتاح الهويّة الذي يُبنى عليه المفتاح والاختيار،
     * واسمٌ بلا مفتاح يُنتج ادّعاءً لا يُثبَت.
     */
    @Test
    fun `the platform inventory is translated and only keyed effects survive`() {
        val effects = listOf(
            AudioEffectInfo(
                name = "Dolby Audio",
                typeUuid = VendorEffectCatalog.EFFECT_TYPE_NULL_UUID,
                implementor = "Dolby Laboratories",
                connectMode = "Insert",
                uuid = VendorEffectCatalog.DOLBY_DAP_UUID,
            ),
            // وبلا معرّف تنفيذ: يُسقَط ولا يُخترع له معرّف (وإلا صار اكتشافًا كاذبًا).
            AudioEffectInfo(name = "Nameless", typeUuid = null, implementor = null, connectMode = null, uuid = null),
            AudioEffectInfo(name = "Blank", typeUuid = null, implementor = null, connectMode = null, uuid = "  "),
        )
        val descriptors = vendorDescriptorsOf(effects)
        assertEquals(1, descriptors.size)
        assertEquals(VendorEffectCatalog.DOLBY_DAP_UUID, descriptors.first().uuid)
        assertEquals("Dolby Laboratories", descriptors.first().implementor)
        assertEquals("Insert", descriptors.first().connectMode)
        assertEquals(
            VendorEffectFamily.DOLBY_DAP,
            vendorAudioVerdict(VendorAudioEvidence(descriptors)).family,
        )
    }

    // ────────────────── المصدر الثاني: تهيئة النظام (`audio_effects.xml`) ──────────────────

    /** وثيقة تهيئة تحمل مؤثّر Dolby بمعرّفه — **والحروف كبيرة عن قصد: القياس يُطبّعها**. */
    private val dolbyConfig = AudioEffectsDocument.parse(
        """
        <audio_effects version="2.0">
          <libraries><library name="dolby" path="libdolby.so"/></libraries>
          <effects>
            <effect name="dolby_dap" library="dolby" uuid="9D4921DA-8225-4F29-AEFA-39537A04BCAA"/>
          </effects>
        </audio_effects>
        """.trimIndent(),
    )!!

    /** **والحكم الذي وُلد من أجله المصدر الثاني:** مُعرَّفٌ في التهيئة ولم تُحمّله المنصّة ⇒
     * `DETECTED_BUT_UNAVAILABLE` — **لا «غير موجود» ولا «يعمل»**. */
    @Test
    fun `declared in config but not loaded is detected but unavailable`() {
        val verdict = vendorAudioVerdict(
            VendorAudioEvidence(descriptors = emptyList(), config = vendorConfigIdentities(dolbyConfig)),
        )
        assertEquals(VendorAudioPresence.DETECTED_BUT_UNAVAILABLE, verdict.presence)
        assertEquals(VendorAudioReason.DECLARED_IN_CONFIG_ONLY, verdict.reason)
        assertEquals(VendorEffectFamily.DOLBY_DAP, verdict.family)
        assertEquals(VendorDiscoverySource.AUDIO_EFFECTS_CONFIG, verdict.source)
        assertEquals(VendorEffectCatalog.DOLBY_DAP_UUID, verdict.effectUuid)
        assertTrue(verdict.isDetected)
        assertFalse(verdict.isControllable)
    }

    /** **ولا يُرقّى تعريفُ التهيئة إلى تحكّم ولو نجح إرفاق:** بلا جردةٍ تُعلنه لا نعرف ماذا أرفقنا. */
    @Test
    fun `a config declaration alone never becomes controllable`() {
        val verdict = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = emptyList(),
                attach = VendorAttachEvidence(VendorAccessRoute.HIDDEN_API, VendorAttachOutcome.ATTACHED, true),
                config = vendorConfigIdentities(dolbyConfig),
            ),
        )
        assertEquals(VendorAudioPresence.DETECTED_BUT_UNAVAILABLE, verdict.presence)
        assertFalse(verdict.isControllable)
    }

    /** **والقياس يُقدَّم على الشاهد:** مؤثّرٌ في الجردة وفي التهيئة ⇒ مصدره الجردة، ويُقال إنّ التهيئة
     * تشهد له — فلا تُطوى معلومة. */
    @Test
    fun `the loaded effect list stays authoritative and config corroborates it`() {
        val verdict = vendorAudioVerdict(
            VendorAudioEvidence(
                descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID, "Dolby Audio")),
                config = vendorConfigIdentities(dolbyConfig),
            ),
        )
        assertEquals(VendorDiscoverySource.EFFECT_LIST, verdict.source)
        assertTrue(verdict.corroboratedByConfig)
        assertEquals(VendorEffectCatalog.DOLBY_DAP_UUID, verdict.effectUuid)
    }

    /** وتخمينُ التهيئة يبقى تخمينًا: علامةٌ في الاسم بلا معرّف ⇒ `INFERRED` لا `SOURCE_VERIFIED`. */
    @Test
    fun `a config declaration matched by name only stays inferred`() {
        val document = AudioEffectsDocument.parse(
            """
            <audio_effects version="2.0">
              <effects><effect name="Dolby Atmos" library="vendor_dolby" uuid="11111111-2222-3333-4444-555555555555"/></effects>
            </audio_effects>
            """.trimIndent(),
        )!!
        val identities = vendorConfigIdentities(document)
        assertEquals(1, identities.size)
        assertEquals(VendorEvidence.INFERRED, identities.first().evidence)
        assertEquals(VendorDiscoverySource.AUDIO_EFFECTS_CONFIG, identities.first().source)
    }

    /** ومؤثّرات المنصّة المُعرَّفة في التهيئة ليست مؤثّرات مصنّع — القاعدة نفسها على المصدرين. */
    @Test
    fun `platform effects declared in config are not vendor effects`() {
        val document = AudioEffectsDocument.parse(
            """
            <audio_effects version="2.0">
              <effects>
                <effect name="Equalizer" library="bundle" uuid="0bed4300-ddd6-11db-8f34-0002a5d5c51b"/>
              </effects>
            </audio_effects>
            """.trimIndent(),
        )!!
        assertTrue(vendorConfigIdentities(document).isEmpty())
    }

    /** ولا وثيقة بلا نصّ صالح: جذرٌ غير `audio_effects` لا يُحلّل — فلا تُبنى هويّة على ملفّ آخر. */
    @Test
    fun `a document with the wrong root is rejected`() {
        assertNull(AudioEffectsDocument.parse("<not_audio_effects/>"))
    }

    /** ولا سطر بلا سبب: كل مسارات القياس تُنتج حكمًا مكتوب السبب. */
    @Test
    fun `every verdict carries a non blank reason`() {
        val descriptors = listOf(descriptor(VendorEffectCatalog.DOLBY_DAP_UUID, "Dolby Audio"))
        val inputs = buildList {
            add(VendorAudioEvidence(descriptors = null))
            add(VendorAudioEvidence(descriptors = emptyList()))
            add(VendorAudioEvidence(descriptors = descriptors))
            add(VendorAudioEvidence(descriptors = emptyList(), config = vendorConfigIdentities(dolbyConfig)))
            VendorAccessRoute.entries.forEach { route ->
                VendorAttachOutcome.entries.forEach { outcome ->
                    listOf<Boolean?>(null, true, false).forEach { owned ->
                        add(
                            VendorAudioEvidence(
                                descriptors = descriptors,
                                attach = VendorAttachEvidence(route, outcome, owned),
                            ),
                        )
                    }
                }
            }
        }
        inputs.forEach { evidence ->
            val verdict = vendorAudioVerdict(evidence)
            assertTrue("سبب فارغ: $verdict", verdict.reason.isNotBlank())
        }
    }
}
