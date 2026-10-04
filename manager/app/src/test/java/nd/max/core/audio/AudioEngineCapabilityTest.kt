/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * أحكام القدرات — **مقيسة على JVM بلا جهاز ولا `android.jar`**.
 *
 * **وما يُقاس هنا بالضبط:** أيّ إدخال يُنتج أيّ حالة، وأنّ «لم تُقرأ» لا تُقرأ «غير متاحة»، وأنّ الرفض
 * صار حكمًا (`needs_adapter`) لا فشلًا مُخفى، وأنّ كل حكم له **سببٌ** من الرموز المُعلَنة — وأنّ الرقمين
 * الثابتين (لكل تطبيق · الالتفاف) لا يتبدّلان بجهاز لأنهما مقيسان من المنصّة نفسها.
 *
 * وما يحتاج جهازًا (أن تُقبل جلسة ٠ فعلًا · أن تُعلن أجهزة مكالمة · أن يُدعم المازج) لا يُدّعى هنا:
 * تُقاس **قاعدته** على JVM، ويُقاس **رقمه** على الجهاز.
 */
class AudioEngineCapabilityTest {

    /** إدخال «كل شيء سليم» — يُستعمل كخط أساس، وكل اختبار يُبدّل ما يقيسه وحده. */
    private fun abilities(
        declared: List<String>? = listOf(
            "equalizer", "dynamics_processing", "bass_boost", "virtualizer", "loudness_enhancer",
        ),
        globalAttach: GlobalAttach = GlobalAttach.ACCEPTED,
        recordAudio: Boolean = false,
        mixer: Boolean? = true,
        routing: Boolean? = true,
        groups: Boolean? = true,
        systemLayer: Boolean? = null,
    ) = DeclaredAudioAbilities(
        declaredEffects = declared,
        globalAttach = globalAttach,
        spectrumPermissionGranted = recordAudio,
        mixerAttributesSupported = mixer,
        communicationRoutingSupported = routing,
        volumeGroupsSupported = groups,
        systemLayerInstalled = systemLayer,
    )

    private fun verdict(abilities: DeclaredAudioAbilities, feature: AudioFeature): AudioFeatureVerdict =
        audioCapabilityVerdicts(abilities).first { it.feature == feature }

    /** ولا ميزة بلا حكم: كل عضو في المفردة له صفٌّ — وإلا عرضنا بعض الجهاز وسكتنا عن بعضه. */
    @Test
    fun `every feature gets exactly one verdict`() {
        val verdicts = audioCapabilityVerdicts(abilities())
        assertEquals(AudioFeature.entries.size, verdicts.size)
        assertEquals(verdicts.map { it.feature }.distinct().size, verdicts.size)
    }

    /** **والقلب:** مؤثّر مُعلَن + مزجٌ مقبول ⇒ يُكتب ويُقرأ. */
    @Test
    fun `a declared effect over an accepted global mix is writable`() {
        val equalizer = verdict(abilities(), AudioFeature.EQUALIZER)
        assertEquals(AudioSupport.WRITABLE, equalizer.support)
        assertEquals(AudioCapabilityReason.DECLARED_GLOBAL_ACCEPTED, equalizer.reason)
    }

    /**
     * **والرفض نتيجةٌ تُقال لا فشلٌ يُخفى:** الجهاز يعلن المعادل، والجلسة ٠ رُفضت ⇒ الميزة حقيقيّة
     * لكنها **تحتاج محوّلًا** (جلسةً نملكها) — ولا تُقال «غير متاحة» لأنّ ذلك كذبٌ على الجهاز.
     */
    @Test
    fun `a declared effect behind a refused global mix needs an adapter, not absence`() {
        val equalizer = verdict(abilities(globalAttach = GlobalAttach.DENIED), AudioFeature.EQUALIZER)
        assertEquals(AudioSupport.NEEDS_ADAPTER, equalizer.support)
        assertEquals(AudioCapabilityReason.GLOBAL_DENIED, equalizer.reason)
    }

    /** ولم يُجرَّب الإرفاق ⇒ لا تُدّعى كتابةٌ ولا تُنفى ميزة: «يحتاج محوّلًا» بسببٍ يقول إنه لم يُقس. */
    @Test
    fun `an unmeasured global mix never claims writability`() {
        val equalizer = verdict(abilities(globalAttach = GlobalAttach.NOT_ATTEMPTED), AudioFeature.EQUALIZER)
        assertEquals(AudioSupport.NEEDS_ADAPTER, equalizer.support)
        assertEquals(AudioCapabilityReason.GLOBAL_NOT_MEASURED, equalizer.reason)
        assertEquals(
            AudioSupport.UNKNOWN,
            verdict(abilities(globalAttach = GlobalAttach.NOT_ATTEMPTED), AudioFeature.GLOBAL_ATTACH).support,
        )
    }

    /**
     * **و«لم تُقرأ» ليست «غير متاحة» (ADR-07):** قائمةٌ غير مقروءة ⇒ `unknown`، وقائمةٌ مقروءة بلا
     * المعادل ⇒ `unavailable`. والفرق بينهما هو الفرق بين «لا أعرف» و«أعرف أنه لا».
     */
    @Test
    fun `an unreadable effect list stays unknown while a read list without the effect is unavailable`() {
        val unreadable = verdict(abilities(declared = null), AudioFeature.EQUALIZER)
        assertEquals(AudioSupport.UNKNOWN, unreadable.support)
        assertEquals(AudioCapabilityReason.EFFECTS_UNREADABLE, unreadable.reason)

        val readButAbsent = verdict(abilities(declared = listOf("virtualizer")), AudioFeature.EQUALIZER)
        assertEquals(AudioSupport.UNAVAILABLE, readButAbsent.support)
        assertEquals(AudioCapabilityReason.NOT_DECLARED, readButAbsent.reason)

        // والقائمة الفارغة **قراءةٌ**: «لا مؤثّرات على هذا الجهاز» — لا «لم أقرأ».
        val empty = verdict(abilities(declared = emptyList()), AudioFeature.EQUALIZER)
        assertEquals(AudioSupport.UNAVAILABLE, empty.support)
    }

    /** والمزج العامّ نفسه صفٌّ يُقرأ: مقبول ⇒ يُكتب · مرفوض ⇒ محوّل · لم يُقس ⇒ مجهول. */
    @Test
    fun `the global mix verdict follows what was measured`() {
        assertEquals(
            AudioSupport.WRITABLE,
            verdict(abilities(globalAttach = GlobalAttach.ACCEPTED), AudioFeature.GLOBAL_ATTACH).support,
        )
        assertEquals(
            AudioSupport.NEEDS_ADAPTER,
            verdict(abilities(globalAttach = GlobalAttach.DENIED), AudioFeature.GLOBAL_ATTACH).support,
        )
        assertEquals(
            AudioSupport.UNKNOWN,
            verdict(abilities(globalAttach = GlobalAttach.UNKNOWN), AudioFeature.GLOBAL_ATTACH).support,
        )
    }

    /**
     * والطيف: **صادقٌ في الحالين** — بلا إذنٍ يحتاج الإذن، وبه يحتاج جلسة. ولا يُقال «غير متاح» عنه
     * في حال، لأنه واجهةٌ موجودة في المنصّة دائمًا (مقيس بـ`javap`).
     */
    @Test
    fun `the spectrum analyser always needs an adapter and names which one`() {
        val without = verdict(abilities(recordAudio = false), AudioFeature.SPECTRUM)
        assertEquals(AudioSupport.NEEDS_ADAPTER, without.support)
        assertEquals(AudioCapabilityReason.PERMISSION_RECORD_AUDIO, without.reason)

        val with = verdict(abilities(recordAudio = true), AudioFeature.SPECTRUM)
        assertEquals(AudioSupport.NEEDS_ADAPTER, with.support)
        assertEquals(AudioCapabilityReason.SESSION_REQUIRED, with.reason)
    }

    /** والثلاثيّة في ميزات المنصّة: `true` ⇒ يُكتب · `false` ⇒ غير متاح · `null` ⇒ لم تُقرأ. */
    @Test
    fun `platform features keep the three-way answer`() {
        assertEquals(AudioSupport.WRITABLE, verdict(abilities(mixer = true), AudioFeature.MIXER_ATTRIBUTES).support)
        assertEquals(
            AudioSupport.UNAVAILABLE,
            verdict(abilities(mixer = false), AudioFeature.MIXER_ATTRIBUTES).support,
        )
        assertEquals(AudioSupport.UNKNOWN, verdict(abilities(mixer = null), AudioFeature.MIXER_ATTRIBUTES).support)
        assertEquals(
            AudioSupport.UNAVAILABLE,
            verdict(abilities(routing = false), AudioFeature.COMMUNICATION_ROUTING).support,
        )
        assertEquals(AudioSupport.UNKNOWN, verdict(abilities(groups = null), AudioFeature.VOLUME_GROUPS).support)
    }

    /**
     * **والحكمان الثابتان — وهما أهمّ ما يمنع الوعد الكاذب:** «لكل تطبيق» و«الالتفاف» **غير متاحين دائمًا
     * وبسببهما المقيس** (`AudioPlaybackConfiguration` بلا `uid`/`session`، ولا صنف مؤثّر التفاف في
     * المنصّة) — ولا يتبدّلان بجهاز، ولا يُقرآن «مجهول» لأنهما لم يُقرآ.
     */
    @Test
    fun `per-app and convolver are always unavailable with their measured reason`() {
        // حتى مع "أفضل" إدخال ممكن: مؤثّرات مُعلَنة ومزج مقبول وإذنٌ ممنوح.
        val best = abilities(recordAudio = true)
        assertEquals(
            AudioSupport.UNAVAILABLE,
            verdict(best, AudioFeature.PER_APP_EFFECTS).support,
        )
        assertEquals(
            AudioCapabilityReason.NO_UID_OR_SESSION,
            verdict(best, AudioFeature.PER_APP_EFFECTS).reason,
        )
        assertEquals(AudioSupport.UNAVAILABLE, verdict(best, AudioFeature.CONVOLVER).support)
        assertEquals(AudioCapabilityReason.NO_EFFECT_CLASS, verdict(best, AudioFeature.CONVOLVER).reason)

        // ومعهما التداخل: لا واحدة منهما تصير "مجهولة" ولو لم تُقرأ المؤثّرات.
        val unreadable = abilities(declared = null)
        assertEquals(AudioSupport.UNAVAILABLE, verdict(unreadable, AudioFeature.PER_APP_EFFECTS).support)
        assertEquals(AudioSupport.UNAVAILABLE, verdict(unreadable, AudioFeature.CONVOLVER).support)
    }

    /** وكل حكم **له سبب مكتوب**: حكمٌ بلا سبب لا يُتّهم به أحد ولا يُصلَح. */
    @Test
    fun `every verdict carries a non-blank reason`() {
        listOf(
            abilities(),
            abilities(declared = null),
            abilities(declared = emptyList()),
            abilities(globalAttach = GlobalAttach.DENIED),
            abilities(globalAttach = GlobalAttach.NOT_ATTEMPTED),
            abilities(mixer = null, routing = null, groups = null),
        ).forEach { input ->
            audioCapabilityVerdicts(input).forEach { item ->
                assertTrue("سبب فارغ للميزة ${item.feature.token}", item.reason.isNotBlank())
            }
        }
    }

    /** والحصيلة تُقرأ من الأحكام ولا تُعدّ في الشاشة — فمجموعها يساوي عدد الأحكام دائمًا. */
    @Test
    fun `the summary counts every verdict exactly once`() {
        audioCapabilityVerdicts(abilities()).let { verdicts ->
            val summary = audioCapabilitySummary(verdicts)
            assertEquals(verdicts.size, summary.total)
            assertEquals(AudioFeature.entries.size, summary.total)
            assertNotNull(summary)
        }
    }

    /** وترتيب العرض ثابت ومرتَّب: المفتاح قبل ما يُفتح به، فلا يتقدّم صفٌّ على أساسه. */
    @Test
    fun `the display order starts with the global mix and keeps the fixed verdicts last`() {
        val order = audioCapabilityVerdicts(abilities()).map { it.feature }
        assertEquals(AudioFeature.GLOBAL_ATTACH, order.first())
        // والثلاثة الثابتة في الذيل، بترتيبها: المرفوضان بقرارٍ ثمّ ما لا صنف له في المنصّة.
        assertEquals(AudioFeature.PER_APP_EFFECTS, order[order.size - 3])
        assertEquals(AudioFeature.CAPTURE_PROCESSING, order[order.size - 2])
        assertEquals(AudioFeature.CONVOLVER, order.last())
    }

    // ─────────────── ميزات محرّكنا: الحكم يتبع المحوّل لا الجهاز ───────────────

    /**
     * **والفرق الذي يمنع كذبتين:** الطبقة النظاميّة غير مُركَّبة ⇒ `needs_adapter` بسببٍ يسمّي المحوّل،
     * **لا** `unavailable`. والجهاز في هذه الحال قد يكون قادرًا تمامًا، ومحرّكنا مكتوبًا ومُختبَرًا
     * (‏٢٤٦ دعوى في `maxfx/tests`) — فقول «غير متاح» كذبٌ على الجهاز، وقول «يعمل» كذبٌ على الواقع.
     */
    @Test
    fun `our engine features follow the system layer, not the device`() {
        val without = verdict(abilities(systemLayer = false), AudioFeature.PARAM_EQ)
        assertEquals(AudioSupport.NEEDS_ADAPTER, without.support)
        assertEquals(AudioCapabilityReason.SYSTEM_LAYER_MISSING, without.reason)

        val with = verdict(abilities(systemLayer = true), AudioFeature.LIMITER)
        assertEquals(AudioSupport.WRITABLE, with.support)
        assertEquals(AudioCapabilityReason.SYSTEM_LAYER_INSTALLED, with.reason)

        // ولم يُقس وجودها ⇒ «مجهولة» لا نفيٌ ولا ادّعاء (ADR-07).
        val unmeasured = verdict(abilities(systemLayer = null), AudioFeature.COMPRESSOR)
        assertEquals(AudioSupport.UNKNOWN, unmeasured.support)
        assertEquals(AudioCapabilityReason.SYSTEM_LAYER_NOT_MEASURED, unmeasured.reason)
    }

    /** وكل ميزةٍ محرّكنا نفّذها (`implemented`) لها حكم المحوّل نفسه — ولا واحدة تُفلت من الصفّ. */
    @Test
    fun `every implemented feature of ours shares the same rule`() {
        val ours = AudioFeature.entries.filter { it.implemented && it.group == AudioFeatureGroup.MAXFX }
        assertEquals(8, ours.size)
        ours.forEach { feature ->
            assertEquals(feature.token, AudioSupport.NEEDS_ADAPTER, verdict(abilities(systemLayer = false), feature).support)
        }
    }

    // ─────────── ميزات المشاريع المرجعيّة: نفيٌ عن شجرتنا لا عن الجهاز ───────────

    /**
     * **وتُعلن بأسمائها ولا تُوعَد:** ميزةٌ للمشاريع المرجعيّة لم تُكتب عندنا ⇒ `unavailable` بسبب
     * `no-engine-implementation-in-this-tree` **وتفصيلٌ يسمّي صاحبها** — فالمستخدم يرى أنّ الطلب لم يُنسَ،
     * والقارئ يعرف أنّ النفي عن كودنا لا عن جهازه. ولا تتبدّل بجهاز (تُقاس بـ`grep` لا بإرفاق).
     */
    @Test
    fun `reference-project features are named with their owner and never claimed`() {
        val best = abilities(systemLayer = true, recordAudio = true, globalAttach = GlobalAttach.ACCEPTED)
        val fir = verdict(best, AudioFeature.FIR_EQUALIZER)
        assertEquals(AudioSupport.UNAVAILABLE, fir.support)
        assertEquals(AudioCapabilityReason.NO_ENGINE_IMPLEMENTATION, fir.reason)
        assertEquals("ViPER4Android", fir.detail)

        assertEquals("JamesDSP / WEcho", verdict(best, AudioFeature.CONVOLUTION_IR).detail)

        // وحتى بلا أيّ قراءةٍ ناجحة: لا تصير «مجهولة» — لأنّ قياسها ليس على الجهاز أصلًا.
        val nothingKnown = abilities(declared = null, systemLayer = null)
        assertEquals(AudioSupport.UNAVAILABLE, verdict(nothingKnown, AudioFeature.MASTER_GATE).support)
        assertEquals(AudioSupport.UNAVAILABLE, verdict(nothingKnown, AudioFeature.DYNAMIC_SYSTEM).support)
    }

    /** والتقاطٌ داخليّ: مرفوضٌ بقرارٍ مكتوب، ورمزه يفرّقه عن «لا نعرف كيف». */
    @Test
    fun `internal capture is refused by policy, not by inability`() {
        val capture = verdict(abilities(systemLayer = true), AudioFeature.CAPTURE_PROCESSING)
        assertEquals(AudioSupport.UNAVAILABLE, capture.support)
        assertEquals(AudioCapabilityReason.CAPTURE_REFUSED, capture.reason)
    }

    /** وكل ميزةٍ في الكاتالوج لها مجموعةٌ معلنة — فلا صفٌّ بلا كتلة في العرض. */
    @Test
    fun `every feature belongs to a declared group`() {
        AudioFeature.entries.forEach { feature ->
            assertNotNull("مجموعة ${feature.token}", feature.group)
            assertTrue("رمز ${feature.token}", feature.token.isNotBlank())
        }
        // و«نفّذناه» و«المنصّة» لا تختلطان: ميزةٌ `implemented` يجب أن تكون لمحرّكنا أو قياسًا لنا.
        AudioFeature.entries.filter { it.implemented }.forEach { feature ->
            assertTrue(
                "${feature.token} مُعلَنة منفَّذة فلتكن لمحرّكنا أو منهجٍ لنا",
                feature.group == AudioFeatureGroup.MAXFX ||
                    feature == AudioFeature.GLOBAL_ATTACH ||
                    feature == AudioFeature.SPECTRUM,
            )
        }
    }
}
