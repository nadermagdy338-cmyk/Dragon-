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
 * سلّم المحرّكات والاختيار وقت التشغيل — **مقيسٌ على JVM بلا جهاز**.
 *
 * **وما يُقاس هنا:** أنّ الترتيب بالقدرة لا باسم المصنّع · وأنّ الاختيار لا يرقّي حالةً (`AVAILABLE`
 * يسبق `NEEDS_ADAPTER` مهما كان موضعه) · وأنّ **السقوط اللطيف** حقيقيّ (Dolby مكتشفٌ ولا مسار ⇒
 * تبقى المنصّة تقود، وتُقال الحالتان) · وأنّ «قاس فوجد لا شيء» تُفرّق عن «لم يقس».
 */
class AudioBackendLadderTest {

    private fun abilities(
        declared: List<String>? = listOf(
            "equalizer", "dynamics_processing", "bass_boost", "virtualizer", "loudness_enhancer",
        ),
        globalAttach: GlobalAttach = GlobalAttach.ACCEPTED,
    ) = DeclaredAudioAbilities(
        declaredEffects = declared,
        globalAttach = globalAttach,
        spectrumPermissionGranted = false,
        mixerAttributesSupported = true,
        communicationRoutingSupported = true,
        volumeGroupsSupported = true,
    )

    private fun vendor(presence: VendorAudioPresence, reason: String) = VendorAudioVerdict(
        presence = presence,
        family = VendorEffectFamily.DOLBY_DAP,
        evidence = VendorEvidence.SOURCE_VERIFIED,
        effectUuid = VendorEffectCatalog.DOLBY_DAP_UUID,
        reason = reason,
    )

    private fun candidate(ladder: List<AudioBackendCandidate>, id: AudioBackendId) =
        ladder.first { it.id == id }

    /** رِفادةٌ لكل عضو في المفردة، وبترتيب [`AudioBackendId`] ملزمًا — لا رِفادة تُسقط. */
    @Test
    fun `the ladder has exactly one rung per backend in the declared order`() {
        val ladder = audioBackendLadder(AudioBackendEvidence(abilities = abilities()))
        assertEquals(AudioBackendId.entries.size, ladder.size)
        assertEquals(AudioBackendId.entries.toList(), ladder.map { it.id })
        // ولا رِفادة مرّتين: عدد المعرّفات المميّزة يساوي عدد الأعضاء — فلا صفّ مكرّر في الشاشة.
        assertEquals(AudioBackendId.entries.size, ladder.map { it.id }.distinct().size)
    }

    /** **القلب:** كل شيء مُعلَن والمزج مقبول ⇒ أفضلُ محرّك منصّة يقود، والباقي بدائل تُقال. */
    @Test
    fun `a fully declared platform picks the richest backend and names its fallbacks`() {
        val ladder = audioBackendLadder(AudioBackendEvidence(abilities = abilities()))
        AudioBackendId.entries
            .filter { it != AudioBackendId.VENDOR_EFFECT && it != AudioBackendId.SYSTEM_LAYER }
            .forEach { id ->
                assertEquals(id.token, AudioBackendAvailability.AVAILABLE, candidate(ladder, id).availability)
            }
        val selection = audioBackendSelection(ladder)
        assertEquals(AudioBackendId.PLATFORM_DYNAMICS, selection.primary)
        assertEquals(listOf(AudioBackendId.PLATFORM_EQUALIZER, AudioBackendId.PLATFORM_SIMPLE), selection.fallbacks)
        assertTrue(selection.hasBackend)
    }

    /** ومعرّف مصنّعٍ بنملك تحكّمه ⇒ **هو الأوّل**، لأنّ الجهاز مضبوطٌ عليه من المصنّع. */
    @Test
    fun `a controllable vendor effect takes the first rung`() {
        val ladder = audioBackendLadder(
            AudioBackendEvidence(
                abilities = abilities(),
                vendor = vendor(VendorAudioPresence.DETECTED_CONTROLLABLE, VendorAudioReason.ATTACHED_AND_OWNED),
            ),
        )
        val rung = candidate(ladder, AudioBackendId.VENDOR_EFFECT)
        assertEquals(AudioBackendAvailability.AVAILABLE, rung.availability)
        assertEquals(VendorEffectCatalog.DOLBY_DAP_UUID, rung.detail)
        val selection = audioBackendSelection(ladder)
        assertEquals(AudioBackendId.VENDOR_EFFECT, selection.primary)
        // وأفضل محرّك منصّة يبقى بديلًا معلنًا — فلا يُخفي المختار أن غيره كان صالحًا.
        assertTrue(selection.fallbacks.contains(AudioBackendId.PLATFORM_DYNAMICS))
    }

    /**
     * **والسقوط اللطيف:** Dolby مكتشفٌ ولا مسار ⇒ رِفادته `detected_but_unavailable`، والمنصّة تقود،
     * والحالتان تُعرضان معًا. وهذا هو نصّ المالك: «إذا تمّ اكتشاف Dolby لكنه غير قابل للوصول فيجب
     * تسجيل `DETECTED_BUT_UNAVAILABLE` بدل إظهار أنه يعمل».
     */
    @Test
    fun `a detected but unreachable vendor effect never drives and never hides the platform`() {
        val ladder = audioBackendLadder(
            AudioBackendEvidence(
                abilities = abilities(),
                vendor = vendor(VendorAudioPresence.DETECTED_BUT_UNAVAILABLE, VendorAudioReason.HIDDEN_API_BLOCKED),
            ),
        )
        val rung = candidate(ladder, AudioBackendId.VENDOR_EFFECT)
        assertEquals(AudioBackendAvailability.DETECTED_BUT_UNAVAILABLE, rung.availability)
        assertEquals(VendorAudioReason.HIDDEN_API_BLOCKED, rung.reason)
        val selection = audioBackendSelection(ladder)
        assertEquals(AudioBackendId.PLATFORM_DYNAMICS, selection.primary)
        assertFalse(selection.fallbacks.contains(AudioBackendId.VENDOR_EFFECT))
    }

    /** وغيابٌ مقيس ⇒ `unavailable`؛ وقائمةٌ لم تُقرأ ⇒ `unknown` — ولا يُخلطان. */
    @Test
    fun `an absent vendor effect is unavailable while an unreadable list stays unknown`() {
        val absent = audioBackendLadder(
            AudioBackendEvidence(
                abilities = abilities(),
                vendor = vendor(VendorAudioPresence.ABSENT, VendorAudioReason.NO_VENDOR_EFFECT),
            ),
        )
        assertEquals(
            AudioBackendAvailability.UNAVAILABLE,
            candidate(absent, AudioBackendId.VENDOR_EFFECT).availability,
        )
        assertEquals(VendorAudioReason.NO_VENDOR_EFFECT, candidate(absent, AudioBackendId.VENDOR_EFFECT).reason)

        val unreadable = audioBackendLadder(
            AudioBackendEvidence(
                abilities = abilities(),
                vendor = vendor(VendorAudioPresence.UNKNOWN, VendorAudioReason.EFFECTS_UNREADABLE),
            ),
        )
        assertEquals(
            AudioBackendAvailability.UNKNOWN,
            candidate(unreadable, AudioBackendId.VENDOR_EFFECT).availability,
        )

        // وبلا قياسٍ أصلًا: `unknown` كذلك — ولا تُنفى رِفادة لم تُسأل.
        val unmeasured = audioBackendLadder(AudioBackendEvidence(abilities = abilities()))
        assertEquals(
            AudioBackendAvailability.UNKNOWN,
            candidate(unmeasured, AudioBackendId.VENDOR_EFFECT).availability,
        )
        assertEquals(AudioBackendReason.VENDOR_NOT_MEASURED, candidate(unmeasured, AudioBackendId.VENDOR_EFFECT).reason)
    }

    /** والمزج المرفوض ⇒ `needs_adapter` بسببٍ يقول «الرفض» لا «لم يُقس». */
    @Test
    fun `a refused global mix is an adapter route with its own reason`() {
        val ladder = audioBackendLadder(
            AudioBackendEvidence(abilities = abilities(globalAttach = GlobalAttach.DENIED)),
        )
        val rung = candidate(ladder, AudioBackendId.PLATFORM_DYNAMICS)
        assertEquals(AudioBackendAvailability.NEEDS_ADAPTER, rung.availability)
        assertEquals(AudioCapabilityReason.GLOBAL_DENIED, rung.reason)

        val notMeasured = audioBackendLadder(
            AudioBackendEvidence(abilities = abilities(globalAttach = GlobalAttach.NOT_ATTEMPTED)),
        )
        assertEquals(
            AudioCapabilityReason.GLOBAL_NOT_MEASURED,
            candidate(notMeasured, AudioBackendId.PLATFORM_DYNAMICS).reason,
        )
    }

    /**
     * **والاختيار لا يرقّي حالة:** محرّكٌ مُثبت (`AVAILABLE`) يقود ولو كان متأخّرًا في السلّم،
     * ولا يُقدَّم عليه مسارٌ محتمل — لأنّ «مُثبت» أقوى من «مُحتمل».
     */
    @Test
    fun `a proven backend outranks a merely attempted one regardless of rung order`() {
        val ladder = audioBackendLadder(
            AudioBackendEvidence(abilities = abilities(declared = listOf("equalizer"), globalAttach = GlobalAttach.ACCEPTED)),
        )
        assertEquals(
            AudioBackendAvailability.UNAVAILABLE,
            candidate(ladder, AudioBackendId.PLATFORM_DYNAMICS).availability,
        )
        assertEquals(
            AudioBackendAvailability.AVAILABLE,
            candidate(ladder, AudioBackendId.PLATFORM_EQUALIZER).availability,
        )
        assertEquals(AudioBackendId.PLATFORM_EQUALIZER, audioBackendSelection(ladder).primary)

        // وحين لا مُثبت أصلًا: المسار الذي **نملك تجربته** يقود، بسبب الرفض نفسه.
        val adapterOnly = audioBackendLadder(
            AudioBackendEvidence(
                abilities = abilities(declared = listOf("dynamics_processing"), globalAttach = GlobalAttach.DENIED),
            ),
        )
        val selection = audioBackendSelection(adapterOnly)
        assertEquals(AudioBackendId.PLATFORM_DYNAMICS, selection.primary)
        assertEquals(AudioCapabilityReason.GLOBAL_DENIED, selection.reason)
    }

    /** ولا محرّك ⇒ **يُقال**، ويُفرّق السبب بين «قاس فوجد لا شيء» و«لم يقس» (ADR-07). */
    @Test
    fun `no backend at all is reported and its reason separates measured from unmeasured`() {
        val measuredNothing = audioBackendLadder(
            AudioBackendEvidence(abilities = abilities(declared = emptyList())),
        )
        val selection = audioBackendSelection(measuredNothing)
        assertNull(selection.primary)
        assertFalse(selection.hasBackend)
        assertEquals(AudioBackendReason.NO_BACKEND_AVAILABLE, selection.reason)

        val unmeasured = audioBackendLadder(AudioBackendEvidence(abilities = null))
        assertEquals(AudioBackendReason.NO_BACKEND_MEASURED, audioBackendSelection(unmeasured).reason)
        // وبلا قياسٍ: كل رِفادةٍ منصّة `unknown` لا `unavailable` — فلا يُنفى ما لم يُسأل.
        assertEquals(
            AudioBackendAvailability.UNKNOWN,
            candidate(unmeasured, AudioBackendId.PLATFORM_DYNAMICS).availability,
        )
        assertEquals(
            AudioBackendReason.ABILITIES_NOT_MEASURED,
            candidate(unmeasured, AudioBackendId.PLATFORM_DYNAMICS).reason,
        )
    }

    /** وقائمة مؤثّراتٍ غير مقروءة **ليست** «لا مؤثّرات» — تبقى `unknown` بسببها. */
    @Test
    fun `an unreadable effect list keeps every platform backend unknown`() {
        val ladder = audioBackendLadder(AudioBackendEvidence(abilities = abilities(declared = null)))
        listOf(
            AudioBackendId.PLATFORM_DYNAMICS,
            AudioBackendId.PLATFORM_EQUALIZER,
            AudioBackendId.PLATFORM_SIMPLE,
        ).forEach { id ->
            assertEquals(AudioBackendAvailability.UNKNOWN, candidate(ladder, id).availability)
            assertEquals(AudioCapabilityReason.EFFECTS_UNREADABLE, candidate(ladder, id).reason)
        }
    }

    /** طبقة النظام: مثبَّتة ⇒ تقود · وغير مثبّتة ومصدرها مكتوب ⇒ مسار تثبيت · وغير مقيسة ⇒ مجهولة. */
    @Test
    fun `the system layer rung follows what was measured about the module`() {
        val installed = audioBackendLadder(
            AudioBackendEvidence(abilities = abilities(), systemLayerInstalled = true, systemLayerWritable = true),
        )
        assertEquals(AudioBackendAvailability.AVAILABLE, candidate(installed, AudioBackendId.SYSTEM_LAYER).availability)
        assertEquals(
            AudioBackendReason.SYSTEM_LAYER_INSTALLED,
            candidate(installed, AudioBackendId.SYSTEM_LAYER).reason,
        )

        val installable = audioBackendLadder(
            AudioBackendEvidence(abilities = abilities(), systemLayerInstalled = false, systemLayerWritable = true),
        )
        assertEquals(
            AudioBackendAvailability.NEEDS_ADAPTER,
            candidate(installable, AudioBackendId.SYSTEM_LAYER).availability,
        )

        val noRoot = audioBackendLadder(
            AudioBackendEvidence(abilities = abilities(), systemLayerInstalled = false, systemLayerWritable = false),
        )
        assertEquals(
            AudioBackendAvailability.DETECTED_BUT_UNAVAILABLE,
            candidate(noRoot, AudioBackendId.SYSTEM_LAYER).availability,
        )

        val unmeasured = audioBackendLadder(AudioBackendEvidence(abilities = abilities()))
        assertEquals(
            AudioBackendAvailability.UNKNOWN,
            candidate(unmeasured, AudioBackendId.SYSTEM_LAYER).availability,
        )
    }

    /** وكل رِفادة **لها سبب مكتوب** في كل تركيبة — فحكمٌ بلا سبب لا يُتّهم به أحد ولا يُصلَح. */
    @Test
    fun `every rung carries a non blank reason in every combination`() {
        val abilitiesOptions = listOf(
            null,
            abilities(),
            abilities(declared = null),
            abilities(declared = emptyList()),
            abilities(globalAttach = GlobalAttach.DENIED),
            abilities(globalAttach = GlobalAttach.NOT_ATTEMPTED),
        )
        val vendorOptions = listOf(
            null,
            vendor(VendorAudioPresence.ABSENT, VendorAudioReason.NO_VENDOR_EFFECT),
            vendor(VendorAudioPresence.DETECTED_CONTROLLABLE, VendorAudioReason.ATTACHED_AND_OWNED),
            vendor(VendorAudioPresence.DETECTED_BUT_UNAVAILABLE, VendorAudioReason.HIDDEN_API_BLOCKED),
            vendor(VendorAudioPresence.UNKNOWN, VendorAudioReason.EFFECTS_UNREADABLE),
        )
        listOf<Boolean?>(null, true, false).forEach { installed ->
            abilitiesOptions.forEach { option ->
                vendorOptions.forEach { vendorVerdict ->
                    val ladder = audioBackendLadder(
                        AudioBackendEvidence(
                            abilities = option,
                            vendor = vendorVerdict,
                            systemLayerInstalled = installed,
                            systemLayerWritable = installed,
                        ),
                    )
                    assertEquals(AudioBackendId.entries.size, ladder.size)
                    ladder.forEach { rung ->
                        assertTrue("سبب فارغ على ${rung.id.token}", rung.reason.isNotBlank())
                    }
                    assertTrue(audioBackendSelection(ladder).reason.isNotBlank())
                }
            }
        }
    }

    // ─────────────────────────── دور الرِفادة في الاختيار الجاري ───────────────────────────

    /** **والرِفادة التي تقود تُقال، والبدائل تُقال، وما عداهما ليس بديلًا.** */
    @Test
    fun `the role reports the driving rung and the fallbacks, and nothing else`() {
        val ladder = listOf(
            rung(AudioBackendId.VENDOR_EFFECT, AudioBackendAvailability.DETECTED_BUT_UNAVAILABLE),
            rung(AudioBackendId.PLATFORM_DYNAMICS, AudioBackendAvailability.AVAILABLE),
            rung(AudioBackendId.PLATFORM_EQUALIZER, AudioBackendAvailability.AVAILABLE),
            rung(AudioBackendId.PLATFORM_SIMPLE, AudioBackendAvailability.UNAVAILABLE),
            rung(AudioBackendId.SYSTEM_LAYER, AudioBackendAvailability.UNKNOWN),
        )
        val selection = audioBackendSelection(ladder)
        assertEquals(AudioBackendId.PLATFORM_DYNAMICS, selection.primary)
        assertEquals(AudioBackendRole.PRIMARY, audioBackendRoleOf(AudioBackendId.PLATFORM_DYNAMICS, selection))
        assertEquals(AudioBackendRole.FALLBACK, audioBackendRoleOf(AudioBackendId.PLATFORM_EQUALIZER, selection))
        // وهو المهمّ: «موجود ولا مسار» ليست بديلًا مقبولًا — فلا تُعرض «متاحةً» بجانب ما يقود.
        assertEquals(AudioBackendRole.OTHER, audioBackendRoleOf(AudioBackendId.VENDOR_EFFECT, selection))
        assertEquals(AudioBackendRole.OTHER, audioBackendRoleOf(AudioBackendId.SYSTEM_LAYER, selection))
        assertEquals(AudioBackendRole.OTHER, audioBackendRoleOf(AudioBackendId.PLATFORM_EQUALIZER, null))
    }

    /** وبلا محرّك قاطع (`NEEDS_ADAPTER` يقود) — الدور يبقى صادقًا: المسار الذي نجرّبه هو `PRIMARY`. */
    @Test
    fun `when nothing is proven the rung we can try is still reported as driving`() {
        val ladder = listOf(
            rung(AudioBackendId.VENDOR_EFFECT, AudioBackendAvailability.DETECTED_BUT_UNAVAILABLE),
            rung(AudioBackendId.PLATFORM_DYNAMICS, AudioBackendAvailability.NEEDS_ADAPTER),
            rung(AudioBackendId.PLATFORM_EQUALIZER, AudioBackendAvailability.UNKNOWN),
        )
        val selection = audioBackendSelection(ladder)
        assertEquals(AudioBackendId.PLATFORM_DYNAMICS, selection.primary)
        assertEquals(AudioBackendRole.PRIMARY, audioBackendRoleOf(AudioBackendId.PLATFORM_DYNAMICS, selection))
        assertEquals(AudioBackendRole.OTHER, audioBackendRoleOf(AudioBackendId.PLATFORM_EQUALIZER, selection))
    }

    /** ولا محرّك: كلّ الرِفادات `OTHER` — فلا يُنتسب «من يقود» بلا محرّك. */
    @Test
    fun `with no backend at all no rung claims to drive`() {
        val selection = audioBackendSelection(
            listOf(
                rung(AudioBackendId.VENDOR_EFFECT, AudioBackendAvailability.UNAVAILABLE),
                rung(AudioBackendId.PLATFORM_SIMPLE, AudioBackendAvailability.UNKNOWN),
            ),
        )
        assertNull(selection.primary)
        AudioBackendId.entries.forEach { id ->
            assertEquals(AudioBackendRole.OTHER, audioBackendRoleOf(id, selection))
        }
    }

    private fun rung(id: AudioBackendId, availability: AudioBackendAvailability) =
        AudioBackendCandidate(id = id, availability = availability, reason = "reason-${id.token}")
}
