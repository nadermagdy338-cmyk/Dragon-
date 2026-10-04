/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **سلّم المحرّكات**: «ما الذي يقود الصوت فعلًا على هذا الجهاز؟» — صافيًا، وقابلًا للقياس
 * على JVM بلا جهاز.
 *
 * **ووُلد من أمر المالك بنصّه (تكملة ٢٣٠):** «أنشئ Audio Engine لا يعتمد على Dolby أصلًا، ويستخدم
 * أفضل Audio Backend حقيقي متاح على الجهاز… ويتم اختيار الـBackend بناءً على capability discovery
 * + verification **وليس اسم الشركة المصنّعة**.»
 *
 * ---
 * ## القرار المعماريّ الذي وُلد من هذا الملفّ (ومنه السؤال كلّه)
 *
 * المالك عرض ثلاثة خيارات: (أ) اكتشاف Dolby كأساس · (ب) محرّك شامل مستقلّ · (ج) هجين.
 * **والجواب المقيس هو (ج) — وليس تفضيلًا، بل لأنّ (أ) وحدها غير ممكنة أصلًا على هذا المستودع،
 * وهذا قياسٌ لا رأي:**
 *
 * | السؤال | القياس | الموضع |
 * | --- | --- | --- |
 * | هل نستطيع **رؤية** مؤثّر مصنّع بلا جذر؟ | **نعم** — `AudioEffect.queryEffects()` عامّة، و`Descriptor.uuid` عامّ | `javap` على `android.jar` (compileSdk 37) |
 * | هل نستطيع **إرفاقه** بلا امتياز؟ | **لا** — `AudioEffect(UUID,UUID,int,int)` موسوم `@hide` | المصدر نفسه: ليس في الـjar |
 * | هل نستطيع **قراءة معاملاته/كتابتها**؟ | **لا** — `setParameter`/`getParameter` موسومان `@hide` | ليسا في الـjar |
 * | هل نستطيع الإرفاق بـ**جذر + مكتبة نظاميّة**؟ | ممكن نظريًّا، **ولا يُقاس هنا** | `AQ-09` + يحتاج جهازًا |
 * ⇒ (أ) كأساس تعني وعدًا لا يُوفى على أكثر الأجهزة: الشاشة كلّها تصير «Dolby موجود ولا يعمل».
 * و(ب) وحدها تُسقط ما هو **حقيقيّ ومقاس** على أجهزة تُعلن Dolby. و(ج) هي الوحيدة التي تعمل من
 * `minSdk 29` على أيّ جهاز، وتستفيد من Dolby **حين يكون الوصول إليه مُثبتًا**، ولا تكذب حين لا يكون.
 *
 * ---
 * ## ولماذا لا رِفادة «Software DSP» في هذا السلّم
 *
 * اقترح المالك `SoftwareDSPBackend` في المسار. **وهي غير موجودة عندنا لسبب مقيس لا لتصميم:** هذا
 * تطبيق تحكّم لا مشغّل وسائط، ومن يعالج الصوت داخل عمليّته يحتاج أن **يملك مسار الصوت** — أي التقاط
 * التشغيل (`AudioPlaybackCapture`/`MediaProjection`) — وهو مرفوض صراحةً في `AUDIO-ADVANCED-PLAN` §4
 * (يكسر تطبيقات تحجب الالتقاط، ويضيف تأخيرًا، ويحتاج إذنًا غير مُعلَن). فالمسار الشرعيّ الوحيد لمحرّك
 * DSP حقيقيّ هو **مكتبة مؤثّر في طبقة النظام** (`AQ-09` · `AS-06`) — وهي في هذا السلّم رِفادةٌ أخيرة
 * مسمّاة بصدق، **لا رِفادة تُوعد**.
 *
 * ## قواعد السلّم الأربعة
 *
 * 1. **الترتيب بالقدرة لا بالمصنّع:** مؤثّر المصنّع أوّلًا **فقط** لأنّ الجهاز مبنيٌّ حوله، ثمّ محرّك
 *    المنصّة الديناميكيّ (أكمل واجهةً)، ثمّ المعادل، ثمّ المؤثّرات البسيطة، ثمّ طبقة النظام.
 * 2. **حالة كل رِفادة قياسٌ لا أمنية:** `AVAILABLE` يعني «قِيست وأمكن قيادتها»، و`NEEDS_ADAPTER`
 *    يعني «حقيقيّة وتحتاج مسارًا نجرّبه»، و`DETECTED_BUT_UNAVAILABLE` تعني «موجودة ولا مسار».
 * 3. **الاختيار لا يرقّي حالة:** الحالة مقياسٌ ثابت، و`primary` قرارٌ — فلا يُخلط الاثنان في حقل واحد.
 * 4. **البدائل تُقال:** من هم أفضل لا يحجب إخبار المستخدم بما كان سيُستخدم لولاه.
 */
package nd.max.core.audio

/**
 * رِفادة محرّك واحدة — **والرمز هو ما يُخزَّن ويُقارن**، والنصّ ثابت في الموارد.
 *
 * والترتيب هنا هو ترتيب السلّم: كلٌّ يُقاس بعد الذي فوقه، والاختيار يمرّ عليها بهذا الترتيب.
 */
enum class AudioBackendId(val token: String) {
    /** مؤثّر المصنّع (Dolby DAP وغيره) — أوّلًا لأنّ الجهاز مضبوطٌ عليه من المصنّع. */
    VENDOR_EFFECT("vendor_effect"),

    /** `DynamicsProcessing`: معادل متعدّد النطاقات + ضاغط + مُحدِّد + دخل القنوات. */
    PLATFORM_DYNAMICS("platform_dynamics"),

    /** `Equalizer`: نطاقات وأنماط المنصّة. */
    PLATFORM_EQUALIZER("platform_equalizer"),

    /** `BassBoost` · `Virtualizer` · `LoudnessEnhancer` · `PresetReverb`. */
    PLATFORM_SIMPLE("platform_simple"),

    /** طبقة نظاميّة (`audio_effects.xml` — `AQ-09`): مسار محرّك DSP حقيقيّ، ويحتاج جذرًا وجهازًا. */
    SYSTEM_LAYER("system_layer"),
}

/**
 * حالة رِفادة — **خمسٌ لا سادسة**، ونفس مفردات مصفوفة القدرات عن قصد: مصدر حقيقة واحد، فلا تقول
 * المصفوفة «تحتاج محوّلًا» ويقول السلّم «غير متاحة» عن الميزة نفسها.
 */
enum class AudioBackendAvailability(val token: String) {
    /** قِيستْ وأمكن قيادتها الآن. */
    AVAILABLE("available"),

    /** حقيقيّة على الجهاز، وتحتاج **محوّلًا نجرّبه** (جلسةً نملكها · مسارًا مخفيًّا · طبقةً). */
    NEEDS_ADAPTER("needs_adapter"),

    /** **موجودة ومُعلَنة، ولا مسار مقيسٌ لقيادتها** — تُعرض بحالتها ولا تُعرض عاملة. */
    DETECTED_BUT_UNAVAILABLE("detected_but_unavailable"),

    /** **قياسٌ** أنّ الجهاز/المنصّة لا تقدّمها. */
    UNAVAILABLE("unavailable"),

    /** **لم تُقس** بعد — والنفي لا يُبنى على غياب قراءة (ADR-07). */
    UNKNOWN("unknown"),
}

/**
 * رِفادة واحدة بحالتها وسببها.
 *
 * @param reason رمزٌ يُترجم في الموارد (ومن مفردات [`AudioCapabilityReason`] و[`VendorAudioReason`]).
 * @param detail تفصيلٌ من القياس (اسم المؤثّر · عدد النطاقات…) — و`null` حين لا قياس.
 */
data class AudioBackendCandidate(
    val id: AudioBackendId,
    val availability: AudioBackendAvailability,
    val reason: String,
    val detail: String? = null,
)

/**
 * المُدخَل الوحيد: **قياسٌ سابقٌ مُعاد استعماله لا قياسٌ ثانٍ**.
 *
 * @param abilities ما قِيسه [`AudioCapabilityProbe`] (المؤثّرات المُعلَنة + المزج العامّ).
 * @param vendor حكم مؤثّر المصنّع ([`vendorAudioVerdict`]) — و`null` تعني «لم يُقس».
 * @param systemLayerInstalled هل طبقة `AQ-09` مثبّتة الآن؟ — و`null` لم تُقس.
 * @param systemLayerWritable هل مصدر ملفّ الطبقة مقروء ومجلّد الوحدات قابل للكتابة؟ — و`null` لم يُقس.
 */
data class AudioBackendEvidence(
    val abilities: DeclaredAudioAbilities?,
    val vendor: VendorAudioVerdict? = null,
    val systemLayerInstalled: Boolean? = null,
    val systemLayerWritable: Boolean? = null,
)

/** رموز أسباب السلّم التي لا تخصّ مصفوفة القدرات. */
object AudioBackendReason {
    const val ABILITIES_NOT_MEASURED = "abilities-not-measured"
    const val GLOBAL_ATTACH_DENIED = AudioCapabilityReason.GLOBAL_DENIED
    const val GLOBAL_ATTACH_NOT_MEASURED = AudioCapabilityReason.GLOBAL_NOT_MEASURED
    const val VENDOR_NOT_MEASURED = "vendor-not-measured"
    const val SYSTEM_LAYER_INSTALLED = "system-layer-installed"
    const val SYSTEM_LAYER_NOT_INSTALLED = "system-layer-not-installed"
    const val SYSTEM_LAYER_NOT_MEASURED = "system-layer-not-measured"
    const val NO_BACKEND_AVAILABLE = "no-backend-available"
    const val NO_BACKEND_MEASURED = "no-backend-measured"
}

/** مؤثّرات كل رِفادة من المنصّة — **الرموز هي مخرَج `audioEffectTypeToken` حرفيًّا**. */
private val DYNAMICS_TOKENS = setOf("dynamics_processing")
private val EQUALIZER_TOKENS = setOf("equalizer")
private val SIMPLE_TOKENS = setOf("bass_boost", "virtualizer", "loudness_enhancer", "preset_reverb")

/**
 * سلّم المحرّكات لهذا الجهاز — **رِفادة واحدة لكل عضو، وبترتيب [`AudioBackendId`] ملزمًا**.
 *
 * والقاعدة واحدة على كل رِفادة منصّة (وهي بالضبط قاعدة مصفوفة القدرات، فلا رَأيان في تطبيق واحد):
 * قائمة غير مقروءة ⇒ `UNKNOWN` · غير مُعلَنة ⇒ `UNAVAILABLE` · مُعلَنة والمزج مقبول ⇒ `AVAILABLE` ·
 * مُعلَنة والمزج مرفوض/لم يُقس ⇒ `NEEDS_ADAPTER` **بسببها الذي يقول أيّها** (فالرفض يفتح مسارًا
 * والمجهول لا يفتحه — ولا يُخلطان).
 */
fun audioBackendLadder(evidence: AudioBackendEvidence): List<AudioBackendCandidate> {
    val abilities = evidence.abilities

    /** حكم رِفادة منصّة من مؤثّراتها المُعلَنة + نتيجة المزج العامّ. */
    fun platform(tokens: Set<String>, id: AudioBackendId): AudioBackendCandidate {
        if (abilities == null) {
            return AudioBackendCandidate(id, AudioBackendAvailability.UNKNOWN, AudioBackendReason.ABILITIES_NOT_MEASURED)
        }
        val declared = abilities.declaredEffects
            ?: return AudioBackendCandidate(id, AudioBackendAvailability.UNKNOWN, AudioCapabilityReason.EFFECTS_UNREADABLE)
        val present = tokens.filter { it in declared }
        if (present.isEmpty()) {
            return AudioBackendCandidate(id, AudioBackendAvailability.UNAVAILABLE, AudioCapabilityReason.NOT_DECLARED)
        }
        val detail = present.joinToString("+")
        return when (abilities.globalAttach) {
            GlobalAttach.ACCEPTED -> AudioBackendCandidate(
                id, AudioBackendAvailability.AVAILABLE, AudioCapabilityReason.DECLARED_GLOBAL_ACCEPTED, detail,
            )
            GlobalAttach.DENIED -> AudioBackendCandidate(
                id, AudioBackendAvailability.NEEDS_ADAPTER,
                AudioBackendReason.GLOBAL_ATTACH_DENIED, detail,
            )
            GlobalAttach.NOT_ATTEMPTED, GlobalAttach.UNKNOWN -> AudioBackendCandidate(
                id, AudioBackendAvailability.NEEDS_ADAPTER,
                AudioBackendReason.GLOBAL_ATTACH_NOT_MEASURED, detail,
            )
        }
    }

    /**
     * رِفادة المصنّع — **والحالة تُترجم من حكم الاكتشاف لا تُعاد قياسها**: حضورٌ مع تحكّم ⇒ `AVAILABLE`،
     * وحضورٌ بلا مسار مقيس ⇒ `DETECTED_BUT_UNAVAILABLE` (وهو نصّ المالك)، وغيابٌ مقيس ⇒ `UNAVAILABLE`،
     * وقائمة غير مقروءة ⇒ `UNKNOWN`.
     */
    val vendor = when (evidence.vendor?.presence) {
        VendorAudioPresence.DETECTED_CONTROLLABLE -> AudioBackendCandidate(
            AudioBackendId.VENDOR_EFFECT,
            AudioBackendAvailability.AVAILABLE,
            VendorAudioReason.ATTACHED_AND_OWNED,
            evidence.vendor.effectUuid,
        )
        VendorAudioPresence.DETECTED_BUT_UNAVAILABLE -> AudioBackendCandidate(
            AudioBackendId.VENDOR_EFFECT,
            AudioBackendAvailability.DETECTED_BUT_UNAVAILABLE,
            evidence.vendor!!.reason,
            evidence.vendor.effectUuid,
        )
        VendorAudioPresence.ABSENT -> AudioBackendCandidate(
            AudioBackendId.VENDOR_EFFECT, AudioBackendAvailability.UNAVAILABLE, VendorAudioReason.NO_VENDOR_EFFECT,
        )
        VendorAudioPresence.UNKNOWN -> AudioBackendCandidate(
            AudioBackendId.VENDOR_EFFECT, AudioBackendAvailability.UNKNOWN,
            evidence.vendor?.reason ?: VendorAudioReason.EFFECTS_UNREADABLE,
        )
        null -> AudioBackendCandidate(
            AudioBackendId.VENDOR_EFFECT, AudioBackendAvailability.UNKNOWN, AudioBackendReason.VENDOR_NOT_MEASURED,
        )
    }

    /**
     * رِفادة الطبقة النظاميّة — **سؤالها غير سؤال المؤثّرات**: ليست شيئًا تُعلنه المنصّة بل شيئًا
     * نثبّته. فالمثبَّت ⇒ `AVAILABLE`؛ وغير المثبَّت ومصدره مكتوبٌ ⇒ `NEEDS_ADAPTER` (المحوّل هو
     * التثبيت، ويحتاج جذرًا وجهازًا)؛ وغير المقيس ⇒ `UNKNOWN`.
     */
    val systemLayer = when (evidence.systemLayerInstalled) {
        true -> AudioBackendCandidate(
            AudioBackendId.SYSTEM_LAYER, AudioBackendAvailability.AVAILABLE,
            AudioBackendReason.SYSTEM_LAYER_INSTALLED,
        )
        false -> when (evidence.systemLayerWritable) {
            true -> AudioBackendCandidate(
                AudioBackendId.SYSTEM_LAYER, AudioBackendAvailability.NEEDS_ADAPTER,
                AudioBackendReason.SYSTEM_LAYER_NOT_INSTALLED,
            )
            false -> AudioBackendCandidate(
                AudioBackendId.SYSTEM_LAYER, AudioBackendAvailability.DETECTED_BUT_UNAVAILABLE,
                AudioCapabilityReason.UNREADABLE,
            )
            null -> AudioBackendCandidate(
                AudioBackendId.SYSTEM_LAYER, AudioBackendAvailability.UNKNOWN,
                AudioBackendReason.SYSTEM_LAYER_NOT_MEASURED,
            )
        }
        null -> AudioBackendCandidate(
            AudioBackendId.SYSTEM_LAYER, AudioBackendAvailability.UNKNOWN,
            AudioBackendReason.SYSTEM_LAYER_NOT_MEASURED,
        )
    }

    return listOf(
        vendor,
        platform(DYNAMICS_TOKENS, AudioBackendId.PLATFORM_DYNAMICS),
        platform(EQUALIZER_TOKENS, AudioBackendId.PLATFORM_EQUALIZER),
        platform(SIMPLE_TOKENS, AudioBackendId.PLATFORM_SIMPLE),
        systemLayer,
    )
}

/**
 * **دور رِفادةٍ في الاختيار الجاري** — يُحسب من [`AudioBackendSelection`] ولا يُعاد قياسه.
 *
 * **ولماذا دورٌ ثالث (`OTHER`) لا اثنان:** رِفادةٌ في السلّم لكنّها ليست المختارة ولا بديلًا مُصدَّقًا
 * عنه (`DETECTED_BUT_UNAVAILABLE` · `UNAVAILABLE` · `UNKNOWN`) — وخلطُها بالبديل يجعل «متاح» و«موجود
 * ولا مسار» وجهًا واحدًا، وهو العطب الذي يحاربه السلّم كله.
 */
enum class AudioBackendRole(val token: String) {
    /** **يقود** — هو `primary` في الاختيار الجاري. */
    PRIMARY("primary"),

    /** بديلٌ مقبول بترتيب السلّم — يُقال، ولا يُعرض كمن يقود. */
    FALLBACK("fallback"),

    /** في السلّم ولا يقود ولا يُعرض بديلًا (غير متاح · مجهول · موجود ولا مسار). */
    OTHER("other"),
}

/**
 * دور رِفادة في الاختيار — **حكمٌ على نتيجةٍ محسوبة، لا قياسٌ ثانٍ**: لا يُقرأ من الحقل `availability`
 * ولا يُشتقّ من السبب، فلا يختلف عمّا تقرؤه الشاشة في السلّم.
 */
fun audioBackendRoleOf(id: AudioBackendId, selection: AudioBackendSelection?): AudioBackendRole = when {
    selection == null -> AudioBackendRole.OTHER
    selection.primary == id -> AudioBackendRole.PRIMARY
    id in selection.fallbacks -> AudioBackendRole.FALLBACK
    else -> AudioBackendRole.OTHER
}

/**
 * نتيجة الاختيار وقت التشغيل.
 *
 * @param primary المحرّك الذي **يقود** — ولا يكون إلا `AVAILABLE` أو `NEEDS_ADAPTER`؛ و`null` تعني
 *   «لا محرّك مُثبت» — وهذا حكمٌ يُقال ولا يُخفى.
 * @param reason سبب الاختيار (أو سبب عدمه) — و`NEEDS_ADAPTER` في السبب تعني «هذا مسارٌ نجرّبه».
 * @param fallbacks البدائل المتاحة بترتيب السلّم — تُقال، فلا يُخفي الأقوى أنّ غيره كان صالحًا.
 */
data class AudioBackendSelection(
    val primary: AudioBackendId?,
    val reason: String,
    val fallbacks: List<AudioBackendId> = emptyList(),
) {
    val hasBackend: Boolean get() = primary != null
}

/**
 * الاختيار: **الأوّل المُثبت (`AVAILABLE`) وإلّا الأوّل الذي له مسار (`NEEDS_ADAPTER`)**.
 *
 * ولماذا لا يُقدَّم `NEEDS_ADAPTER` على `AVAILABLE`: لأنّ «مُثبت» أقوى من «مُحتمل»، ولو قدّمنا
 * المحتمل لصار المحرّك المختار في الشاشة عكس ما يعمل فعلًا. ولماذا يُقدَّم `NEEDS_ADAPTER` على
 * `UNAVAILABLE`/`UNKNOWN`: لأنّه المسار الوحيد الذي **نملك تجربته** (جلسة نملكها · انعكاس · تثبيت)،
 * ولأنّ إسقاطه يعني إسقاط الميزة نفسها بلا محاولة.
 */
fun audioBackendSelection(ladder: List<AudioBackendCandidate>): AudioBackendSelection {
    val available = ladder.filter { it.availability == AudioBackendAvailability.AVAILABLE }
    val adapter = ladder.filter { it.availability == AudioBackendAvailability.NEEDS_ADAPTER }

    available.firstOrNull()?.let { first ->
        return AudioBackendSelection(
            primary = first.id,
            reason = first.reason,
            fallbacks = available.drop(1).map { it.id },
        )
    }

    adapter.firstOrNull()?.let { first ->
        return AudioBackendSelection(primary = first.id, reason = first.reason, fallbacks = adapter.drop(1).map { it.id })
    }

    // ولا محرّك: السبب يفرّق بين «قاس فوجد لا شيء» و«لم يقس» — فلا يُخلطان في جملة واحدة.
    val measured = ladder.any { it.availability != AudioBackendAvailability.UNKNOWN }
    return AudioBackendSelection(
        primary = null,
        reason = if (measured) AudioBackendReason.NO_BACKEND_AVAILABLE else AudioBackendReason.NO_BACKEND_MEASURED,
    )
}
