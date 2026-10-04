/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **حالة كل ميزة، وأسبابها**: الطبقة الصافية الوحيدة التي تحكم على ما يمكن فعلًا.
 *
 * **ووُلدت من أمر المالك (تكملة ٢٢٨):** «أريد تحليل ما يمكن التحكّم به فعليًا… وأريد أن تكون حالة كل
 * ميزة واضحة: مدعومة · قابلة للكتابة · للقراءة فقط · تحتاج Adapter · غير متاحة — وتعطيل غير المدعوم
 * بشكل واضح بدل إظهاره وكأنه يعمل.» والخطّة المفصّلة في `docs/ai/AUDIO-ADVANCED-PLAN.md`.
 *
 * **ولماذا هذه الحالات خمسٌ لا أكثر — ولماذا تدخل «مجهولة» معها:**
 * الحالات التي طلبها المالك أربعٌ فعليّات (`مدعومة` تدخل في «قابلة للكتابة»، فالميزة التي تُعلنها
 * المنصّة ولا تُكتب هي بعينها «للقراءة فقط»). و**«مجهولة» تُضاف معها لا عليها**: لأنّ قاعدة المستودع
 * (ADR-07) تمنع أن يُقال «غير متاحة» عن شيء **لم يُقس بعد** — والقراءة الفاشلة ليست نفيًا. فخمسٌ في
 * الشاشة، وقاعدة واحدة: كل حكم **له سبب مكتوب**، ولا حكم بلا سبب.
 *
 * **والأسباب رموز لا جُمل:** تُكتب في السجلّ كما هي، وتُترجم أوصافها في الموارد — فما يُقاس في الطبقة
 * الصافية لا يحتاج جهازًا ولا `android.jar`، وهذا شرط قياسه على JVM بلا مُصرّف أندرويد.
 */
package nd.max.core.audio

/**
 * مجموعة العرض — **تجمع الميزات في كتلٍ يفهمها المستخدم** بدل قائمةٍ مصطفتة من ٤٣ صفًّا.
 *
 * **والكتل مشتقّة من السؤال الذي تُجيب عنه، لا من تقنيةٍ داخليّة:** ما يُقرأ من المنصّة كقدرة، ثمّ
 * مؤثّراتٌ تُعلنها المنصّة، ثمّ ما **نفّذناه بأنفسنا**، ثمّ ما للمشاريع المرجعيّة وليس عندنا، ثمّ ما رُفض
 * بقرار، ثمّ ما هو قياسٌ لا تحكّم (الطيف).
 */
enum class AudioFeatureGroup(val token: String) {
    GLOBAL("global"),
    PLATFORM("platform"),
    SESSION_EFFECTS("session_effects"),
    MAXFX("maxfx"),
    PROJECTS("projects"),
    DIAGNOSTIC("diagnostic"),
    REFUSED("refused"),
}

/**
 * ميزة صوتيّة واحدة — **والترتيب هنا هو ترتيب العرض**: ما يُبنى ويُكتب أوّلًا، وما لا يُبنى آخرًا.
 * ولا يُضاف عضو بلا موضع في [`audioCapabilityVerdicts`]، فلا تبقى ميزةٌ بلا حكم.
 *
 * **و [`group`] و[`implemented`] عقدٌ لا زينة:** الأولى تجمع الصفوف في العرض، والثانية **نفيٌ عن كودنا
 * لا عن الجهاز** — تُقاس بـ`grep` على شجرة `maxfx/` (والمخبر `dsp_test.c` يقيس الجدول نفسه)، ولا تُدَّعى
 * من اسم ميزة. وهي التي تفرّق بين «الجهاز لا يقدّمها» و«نحن لم نكتبها بعد» — وهما حالتان مختلفتان
 * تمامًا كان يُخلط بينهما في عرضٍ واحدٍ مسطّح.
 */
enum class AudioFeature(
    val token: String,
    val group: AudioFeatureGroup,
    val implemented: Boolean,
) {
    /** المزج العامّ: هل تُقبل جلسة الإرفاق ٠ على هذا الجهاز؟ — وهي سؤال كل ميزة مؤثّر تحته. */
    GLOBAL_ATTACH("global_attach", AudioFeatureGroup.GLOBAL, implemented = true),

    // ── مؤثّرات المنصّة المُعلَنة: توجد في تهيئة الجهاز أو لا توجد ───────────────
    EQUALIZER("equalizer", AudioFeatureGroup.SESSION_EFFECTS, implemented = false),
    DYNAMICS_PROCESSING("dynamics_processing", AudioFeatureGroup.SESSION_EFFECTS, implemented = false),
    BASS_BOOST("bass_boost", AudioFeatureGroup.SESSION_EFFECTS, implemented = false),
    VIRTUALIZER("virtualizer", AudioFeatureGroup.SESSION_EFFECTS, implemented = false),
    LOUDNESS_ENHANCER("loudness_enhancer", AudioFeatureGroup.SESSION_EFFECTS, implemented = false),
    PRESET_REVERB("preset_reverb", AudioFeatureGroup.SESSION_EFFECTS, implemented = false),
    ENVIRONMENTAL_REVERB("env_reverb", AudioFeatureGroup.SESSION_EFFECTS, implemented = false),

    // ── ما نفّذناه فعلًا في محرّكنا (`maxfx` — عقود `maxfx_params.tsv`) ─────────────
    PARAM_EQ("param_eq", AudioFeatureGroup.MAXFX, implemented = true),
    BASS_SHELF("bass_shelf", AudioFeatureGroup.MAXFX, implemented = true),
    CLARITY_SHELF("clarity_shelf", AudioFeatureGroup.MAXFX, implemented = true),
    STEREO_WIDTH("stereo_width", AudioFeatureGroup.MAXFX, implemented = true),
    TUBE_SATURATION("tube_saturation", AudioFeatureGroup.MAXFX, implemented = true),
    COMPRESSOR("compressor", AudioFeatureGroup.MAXFX, implemented = true),
    LIMITER("limiter", AudioFeatureGroup.MAXFX, implemented = true),
    GLOBAL_GAIN("global_gain", AudioFeatureGroup.MAXFX, implemented = true),
    
    // ── ميزاتٌ للمشاريع المرجعيّة وليس عندنا: نفيٌ عن كودنا لا عن الجهاز ────────────
    FIR_EQUALIZER("fir_equalizer", AudioFeatureGroup.PROJECTS, implemented = false),
    CONVOLUTION_IR("convolution_ir", AudioFeatureGroup.PROJECTS, implemented = false),
    DDC_CORRECTION("ddc_correction", AudioFeatureGroup.PROJECTS, implemented = false),
    SPECTRUM_EXTENSION("spectrum_extension", AudioFeatureGroup.PROJECTS, implemented = false),
    VIRTUAL_BASS("virtual_bass", AudioFeatureGroup.PROJECTS, implemented = false),
    MULTIBAND_LIMITER("multiband_limiter", AudioFeatureGroup.PROJECTS, implemented = false),
    TRANSIENT_ENHANCEMENT("transient_enhancement", AudioFeatureGroup.PROJECTS, implemented = false),
    EVEN_HARMONICS("even_harmonics", AudioFeatureGroup.PROJECTS, implemented = false),
    FIELD_SURROUND("field_surround", AudioFeatureGroup.PROJECTS, implemented = false),
    DIFF_SURROUND("diff_surround", AudioFeatureGroup.PROJECTS, implemented = false),
    REVERBERATION("reverberation", AudioFeatureGroup.PROJECTS, implemented = false),
    DYNAMIC_SYSTEM("dynamic_system", AudioFeatureGroup.PROJECTS, implemented = false),
    AGC("agc", AudioFeatureGroup.PROJECTS, implemented = false),
    CROSSFEED("crossfeed", AudioFeatureGroup.PROJECTS, implemented = false),
    ANALOG_X("analog_x", AudioFeatureGroup.PROJECTS, implemented = false),
    FET_COMPRESSOR("fet_compressor", AudioFeatureGroup.PROJECTS, implemented = false),
    SPEAKER_OPTIMIZATION("speaker_optimization", AudioFeatureGroup.PROJECTS, implemented = false),
    MASTER_GATE("master_gate", AudioFeatureGroup.PROJECTS, implemented = false),
    LOW_CUT("low_cut", AudioFeatureGroup.PROJECTS, implemented = false),
    CHANNEL_BALANCE("channel_balance", AudioFeatureGroup.PROJECTS, implemented = false),

    // ── قدرات المنصّة (تُقرأ ثلاثيًّا) وتشخيصها ───────────────────────────
    SPECTRUM("spectrum", AudioFeatureGroup.DIAGNOSTIC, implemented = true),
    MIXER_ATTRIBUTES("mixer_attributes", AudioFeatureGroup.PLATFORM, implemented = false),
    COMMUNICATION_ROUTING("communication_routing", AudioFeatureGroup.PLATFORM, implemented = false),
    VOLUME_GROUPS("volume_groups", AudioFeatureGroup.PLATFORM, implemented = false),

    /** لا يُبنى ولا يُوعد — **والسبب مقيس** لا رأي (راجع `AUDIO-ADVANCED-PLAN` §2.4). */
    PER_APP_EFFECTS("per_app_effects", AudioFeatureGroup.REFUSED, implemented = false),

    /** التقاطٌ داخليّ (أُسرة `RootlessJamesDSP`) — مرفوض بقرارٍ مكتوب، لا لعدم قدرة. */
    CAPTURE_PROCESSING("capture_processing", AudioFeatureGroup.REFUSED, implemented = false),

    /** لا صنف مؤثّر التفاف في المنصّة كلها (مقيس بـ`javap`، `AUDIO-ADVANCED-PLAN` §2.1). */
    CONVOLVER("convolver", AudioFeatureGroup.REFUSED, implemented = false),
}

/** حالة ميزة واحدة — أربعٌ يعرضها المالك + «مجهولة» التي تفرضها ADR-07. */
enum class AudioSupport(val token: String) {
    /** تُعلنها المنصّة ونكتبها ونقرؤها بعد الكتابة. */
    WRITABLE("writable"),

    /** تُعلنها المنصّة ولا نملك كتابتها (أو نقرؤها فقط). */
    READ_ONLY("read_only"),

    /** حقيقيّة على الجهاز، **لكن تحتاج محوّلًا**: جلسةً نملكها، أو إذنًا، أو مكوّنًا نظاميًّا. */
    NEEDS_ADAPTER("needs_adapter"),

    /** **مقيسٌ** أنّ المنصّة/الجهاز لا يقدّمها. */
    UNAVAILABLE("unavailable"),

    /** **لم تُقس** بعد (قراءة فاشلة أو إرفاق لم يُجرَّب) — والنفي لا يُبنى على غياب قراءة (ADR-07). */
    UNKNOWN("unknown"),
}

/**
 * حكم ميزة واحدة.
 *
 * @param reason رمزٌ مكتوب لا جملة: يُقرأ في السجلّ حرفيًّا، ولا يُصاغ في الطبقة الصافية.
 * @param detail تفصيلٌ اختياريّ من القياس (اسم مؤثّر · رقم واجهة) — ولا يُخترع عند غيابه.
 */
data class AudioFeatureVerdict(
    val feature: AudioFeature,
    val support: AudioSupport,
    val reason: String,
    val detail: String? = null,
)

/**
 * ما قِيس فعلًا من المنصّة — **المُدخَل الوحيد للحكم**.
 *
 * وكل حقل فيه **ثلاثيّ** لا ثنائيّ: `null` تعني «لم تُقرأ» لا «لا». وهذا هو الفرق الذي يمنع الشاشة
 * من أن تكذب على جهازٍ عجزت عن قراءته.
 *
 * @param declaredEffects رموز المؤثّرات المُعلَنة (`audioEffectTypeToken`) — و`null` = القائمة غير مقروءة.
 * @param globalAttach نتيجة محاولة الإرفاق على الجلسة ٠ — **محاولةٌ تُقاس ثمّ تُحرَّر**، لا وعد.
 */
data class DeclaredAudioAbilities(
    val declaredEffects: List<String>?,
    val globalAttach: GlobalAttach,
    val spectrumPermissionGranted: Boolean,
    val mixerAttributesSupported: Boolean?,
    val communicationRoutingSupported: Boolean?,
    val volumeGroupsSupported: Boolean?,
    /**
     * هل طبقة `maxfx` النظاميّة مُثبَّتة ومُحمَّلة؟ — **محوّلُ ميزات محرّكنا**.
     *
     * و`null` = لم يُقس (لا «لا») — فالجهاز الذي لم نقرأ وحدته لا يُنفى عنه محرّكه (ADR-07).
     * وقياسها على جهاز المالك أعطى `false` بأدلّةٍ مسمّاة (تكملة ٢٣٦: صفر حدث `system_layer_install`).
     */
    val systemLayerInstalled: Boolean? = null,
)

/** نتيجة محاولة الإرفاق على المزج العامّ (الجلسة ٠) — أربعٌ لا خامسة. */
enum class GlobalAttach(val token: String) {
    /** قبلها المنصّة وأنشأت المؤثّر. */
    ACCEPTED("accepted"),

    /** رفضتها (استثناء من المنصّة) — **والرفض نتيجةٌ تُقال** لا فشلٌ يُخفى. */
    DENIED("denied"),

    /** لم تُجرَّب أصلًا (بلا `AudioManager` مثلًا) — ولا يُبنى حكمٌ على ما لم يُجرَّب. */
    NOT_ATTEMPTED("not_attempted"),

    /** جُرّبت ولم يُفهم جوابها. */
    UNKNOWN("unknown"),
}

/** رموز الأسباب — في موضع واحد، فلا يتفرّق نصّها بين الطبقة والشاشة والاختبار. */
object AudioCapabilityReason {
    const val EFFECTS_UNREADABLE = "effects-unreadable"
    const val NOT_DECLARED = "not-declared"
    const val DECLARED_GLOBAL_ACCEPTED = "declared-global-attach-accepted"
    const val GLOBAL_DENIED = "global-attach-denied-needs-own-session"
    const val GLOBAL_NOT_MEASURED = "global-attach-not-measured"
    const val GLOBAL_ACCEPTED = "global-attach-accepted"
    const val PERMISSION_RECORD_AUDIO = "permission:RECORD_AUDIO-required"
    const val SESSION_REQUIRED = "requires-audio-session"
    const val SUPPORTED_BY_DEVICE = "supported-by-device"
    const val NOT_SUPPORTED = "not-supported-by-device"
    const val UNREADABLE = "unreadable"
    const val NO_UID_OR_SESSION = "platform-exposes-no-uid-or-session"
    const val NO_EFFECT_CLASS = "no-effect-class-in-platform"

    /** الطبقة النظاميّة مُثبَّتة فميزات محرّكنا متاحة. */
    const val SYSTEM_LAYER_INSTALLED = "system-layer-installed"

    /** الطبقة نظاميّة لم تُثبَّت/لم تُحمَّل — **وهو حال جهاز المالك في تكملة ٢٣٦**.
     *  والحكم عن **المحوّل** (الطبقة) لا عن الجهاز: الميزة حقيقيّة ومحرّكها مكتوب ومقيس. */
    const val SYSTEM_LAYER_MISSING = "system-layer-missing"

    /** لم يُقس وجود الطبقة — فلا تُدَّعى كتابةٌ ولا تُنفى ميزة (ADR-07). */
    const val SYSTEM_LAYER_NOT_MEASURED = "system-layer-not-measured"

    /** **نفيٌ عن شجرتنا لا عن الجهاز:** الميزة موجودة في مشروعٍ مرجعيّ ولم تُكتب عندنا بعدُ —
     *  وهذا يُقاس بـ`grep` على `maxfx/` لا يُستنتج من اسم الميزة. */
    const val NO_ENGINE_IMPLEMENTATION = "no-engine-implementation-in-this-tree"

    /** التقاطٌ داخليّ — مرفوض بقرارٍ مكتوب (`AUDIO-ADVANCED-PLAN` §4)، لا لعجز المنصّة. */
    const val CAPTURE_REFUSED = "internal-capture-refused-by-policy"
}

/**
 * الأحكام كلها، بترتيب [`AudioFeature`] — **قاعدة واحدة لكل صفّ:**
 *
 * 1. مؤثّر مُعلَن **والمزج العامّ مقبول** ⇒ يُكتب ويُقرأ ⇒ `writable`.
 * 2. مؤثّر مُعلَن **والمزج مرفوض** ⇒ حقيقيّ لكنه يحتاج **جلسة نملكها** ⇒ `needs_adapter`
 *    (وهذا هو معنى «محوّل» عندنا: نملك مدخل الصوت فنُرفق عليه).
 * 3. مؤثّر مُعلَن **والمزج لم يُجرَّب/مجهول** ⇒ `needs_adapter` كذلك: الكتابة حيث لا نملك جلسةً
 *    **لا تثبت**، فلا تُدَّعى.
 * 4. غير مُعلَن ⇒ `unavailable` بسبب `not-declared` — **نفيٌ مبنيّ على قراءة ناجحة**.
 * 5. القائمة نفسها غير مقروءة ⇒ `unknown` — **ولا يُقال «غير متاح» عمّا لم يُقرأ** (ADR-07).
 *
 * وما لا يعتمد على المؤثّرات (`المزج` · `الطيف` · `سمات المازج` · `التوجيه` · `المجموعات`) ولكلٍّ
 * قاعدةٌ في موضعه أدناه — و«لكل تطبيق» و«الالتفاف» حكمهما ثابتٌ لأنّه **مقيسٌ من المنصّة**، لا لأنّها
 * لم تُقرأ.
 */
fun audioCapabilityVerdicts(abilities: DeclaredAudioAbilities): List<AudioFeatureVerdict> {
    val declared = abilities.declaredEffects

    /** حكم مؤثّر مثبتٍ في القائمة المُعلَنة — والقاعدة ١…٣ أعلاه. */
    fun effect(feature: AudioFeature): AudioFeatureVerdict {
        if (declared == null) {
            return AudioFeatureVerdict(feature, AudioSupport.UNKNOWN, AudioCapabilityReason.EFFECTS_UNREADABLE)
        }
        if (feature.token !in declared) {
            return AudioFeatureVerdict(feature, AudioSupport.UNAVAILABLE, AudioCapabilityReason.NOT_DECLARED)
        }
        return when (abilities.globalAttach) {
            GlobalAttach.ACCEPTED -> AudioFeatureVerdict(
                feature, AudioSupport.WRITABLE, AudioCapabilityReason.DECLARED_GLOBAL_ACCEPTED,
            )
            GlobalAttach.DENIED -> AudioFeatureVerdict(
                feature, AudioSupport.NEEDS_ADAPTER, AudioCapabilityReason.GLOBAL_DENIED,
            )
            GlobalAttach.NOT_ATTEMPTED, GlobalAttach.UNKNOWN -> AudioFeatureVerdict(
                feature, AudioSupport.NEEDS_ADAPTER, AudioCapabilityReason.GLOBAL_NOT_MEASURED,
            )
        }
    }

    /** حكم ميزة المنصّة الثلاثيّة (`true`/`false`/`null`) — بلا مؤثّر معلَن، وبتفصيلٍ اختياريّ. */
    fun platformValue(feature: AudioFeature, supported: Boolean?, detail: String? = null): AudioFeatureVerdict =
        when (supported) {
            true -> AudioFeatureVerdict(
                feature, AudioSupport.WRITABLE, AudioCapabilityReason.SUPPORTED_BY_DEVICE, detail,
            )
            false -> AudioFeatureVerdict(
                feature, AudioSupport.UNAVAILABLE, AudioCapabilityReason.NOT_SUPPORTED, detail,
            )
            null -> AudioFeatureVerdict(feature, AudioSupport.UNKNOWN, AudioCapabilityReason.UNREADABLE, detail)
        }

    val globalAttach = when (abilities.globalAttach) {
        GlobalAttach.ACCEPTED -> AudioFeatureVerdict(
            AudioFeature.GLOBAL_ATTACH, AudioSupport.WRITABLE, AudioCapabilityReason.GLOBAL_ACCEPTED,
        )
        GlobalAttach.DENIED -> AudioFeatureVerdict(
            AudioFeature.GLOBAL_ATTACH, AudioSupport.NEEDS_ADAPTER, AudioCapabilityReason.GLOBAL_DENIED,
        )
        GlobalAttach.NOT_ATTEMPTED, GlobalAttach.UNKNOWN -> AudioFeatureVerdict(
            AudioFeature.GLOBAL_ATTACH, AudioSupport.UNKNOWN, AudioCapabilityReason.GLOBAL_NOT_MEASURED,
        )
    }

    // والطيف: حقيقيّ دائمًا كواجهة، وشرطه إذنٌ **يُطلب بشرح** (أمر المالك) ثمّ جلسةٌ نملكها —
    // فلا يُقال «غير متاح» عنه، ولا يُقال «يعمل» قبل الإذن.
    val spectrum = if (abilities.spectrumPermissionGranted) {
        AudioFeatureVerdict(AudioFeature.SPECTRUM, AudioSupport.NEEDS_ADAPTER, AudioCapabilityReason.SESSION_REQUIRED)
    } else {
        AudioFeatureVerdict(
            AudioFeature.SPECTRUM, AudioSupport.NEEDS_ADAPTER, AudioCapabilityReason.PERMISSION_RECORD_AUDIO,
        )
    }

    /**
     * حكم ميزةٍ نفّذها محرّكنا — **تتبع وجود المحوّل (الطبقة النظاميّة) لا وجود الجهاز**.
     *
     * والقاعدة التي تحكمها: الجهاز قد يكون قادرًا تمامًا ومحرّكنا مكتوبًا ومُختبَرًا (‏٢٤٦ دعوى)،
     * ومع ذلك لا تُسمع الميزة لأنّ الطبقة لم تُركَّب. فالحالتان مختلفتان، ومن خلطهما إمّا كذب على
     * الجهاز («غير مدعوم») أو كذب على المحرّك («يعمل»).
     */
    fun engine(feature: AudioFeature): AudioFeatureVerdict = when (abilities.systemLayerInstalled) {
        true -> AudioFeatureVerdict(
            feature, AudioSupport.WRITABLE, AudioCapabilityReason.SYSTEM_LAYER_INSTALLED,
        )
        false -> AudioFeatureVerdict(
            feature, AudioSupport.NEEDS_ADAPTER, AudioCapabilityReason.SYSTEM_LAYER_MISSING,
        )
        null -> AudioFeatureVerdict(
            feature, AudioSupport.UNKNOWN, AudioCapabilityReason.SYSTEM_LAYER_NOT_MEASURED,
        )
    }

    /**
     * ونفيٌ عن **شجرتنا** لا عن الجهاز: ميزةٌ للمشاريع المرجعيّة لم تُكتب عندنا (`implemented=false`).
     *
     * **ولماذا `UNAVAILABLE` هنا صادقة:** الرمز يقول «غير متاح **في هذا البناء**» لا «جهازك لا يقدّرها»
     * — والفرق مكتوبٌ في السبب نفسه، والتفصيل يسمّي المشروع الذي يملكها كي لا يضيع الطلب.
     */
    fun absent(feature: AudioFeature): AudioFeatureVerdict = AudioFeatureVerdict(
        feature,
        AudioSupport.UNAVAILABLE,
        AudioCapabilityReason.NO_ENGINE_IMPLEMENTATION,
        sourceOf(feature),
    )

    return listOfNotNull(
        globalAttach,
        effect(AudioFeature.EQUALIZER),
        effect(AudioFeature.DYNAMICS_PROCESSING),
        effect(AudioFeature.BASS_BOOST),
        effect(AudioFeature.VIRTUALIZER),
        effect(AudioFeature.LOUDNESS_ENHANCER),
        effect(AudioFeature.PRESET_REVERB),
        effect(AudioFeature.ENVIRONMENTAL_REVERB),
        // وميزات محرّكنا: تُحكم بالمحوّل لا بالجهاز.
        engine(AudioFeature.PARAM_EQ),
        engine(AudioFeature.BASS_SHELF),
        engine(AudioFeature.CLARITY_SHELF),
        engine(AudioFeature.STEREO_WIDTH),
        engine(AudioFeature.TUBE_SATURATION),
        engine(AudioFeature.COMPRESSOR),
        engine(AudioFeature.LIMITER),
        engine(AudioFeature.GLOBAL_GAIN),
        spectrum,
        platformValue(AudioFeature.MIXER_ATTRIBUTES, abilities.mixerAttributesSupported),
        platformValue(AudioFeature.COMMUNICATION_ROUTING, abilities.communicationRoutingSupported),
        platformValue(AudioFeature.VOLUME_GROUPS, abilities.volumeGroupsSupported),
        // وميزاتٌ للمشاريع المرجعيّة وليست عندنا: تُعلن بأسمائها ولا تُوعَد.
        absent(AudioFeature.FIR_EQUALIZER),
        absent(AudioFeature.CONVOLUTION_IR),
        absent(AudioFeature.DDC_CORRECTION),
        absent(AudioFeature.SPECTRUM_EXTENSION),
        absent(AudioFeature.VIRTUAL_BASS),
        absent(AudioFeature.MULTIBAND_LIMITER),
        absent(AudioFeature.TRANSIENT_ENHANCEMENT),
        absent(AudioFeature.EVEN_HARMONICS),
        absent(AudioFeature.FIELD_SURROUND),
        absent(AudioFeature.DIFF_SURROUND),
        absent(AudioFeature.REVERBERATION),
        absent(AudioFeature.DYNAMIC_SYSTEM),
        absent(AudioFeature.AGC),
        absent(AudioFeature.CROSSFEED),
        absent(AudioFeature.ANALOG_X),
        absent(AudioFeature.FET_COMPRESSOR),
        absent(AudioFeature.SPEAKER_OPTIMIZATION),
        absent(AudioFeature.MASTER_GATE),
        absent(AudioFeature.LOW_CUT),
        absent(AudioFeature.CHANNEL_BALANCE),
        // والمرفوضات لا تُقاس على الجهاز أصلًا: حكمها **ثابتٌ من قياس المنصّة/القرار** —
        // فلا تتبدّل بجهاز، ولا يُدَّعى أنها «لم تُقرأ».
        AudioFeatureVerdict(AudioFeature.PER_APP_EFFECTS, AudioSupport.UNAVAILABLE, AudioCapabilityReason.NO_UID_OR_SESSION),
        AudioFeatureVerdict(AudioFeature.CAPTURE_PROCESSING, AudioSupport.UNAVAILABLE, AudioCapabilityReason.CAPTURE_REFUSED),
        AudioFeatureVerdict(AudioFeature.CONVOLVER, AudioSupport.UNAVAILABLE, AudioCapabilityReason.NO_EFFECT_CLASS),
    )
}

/**
 * من يملك الميزة — **المصدر يُسمّى في التفصيل ولا يُخترع**: مُشتقٌّ من المجموعة والمفردة، ومكتوب
 * بالاسم ليعرف القارئ أنّ الطلب لم يُنسَ.
 */
fun sourceOf(feature: AudioFeature): String = when (feature) {
    AudioFeature.FIR_EQUALIZER, AudioFeature.DDC_CORRECTION, AudioFeature.SPECTRUM_EXTENSION,
    AudioFeature.FIELD_SURROUND, AudioFeature.DYNAMIC_SYSTEM, AudioFeature.AGC, AudioFeature.CROSSFEED,
    AudioFeature.ANALOG_X, AudioFeature.FET_COMPRESSOR, AudioFeature.SPEAKER_OPTIMIZATION,
    AudioFeature.MASTER_GATE, AudioFeature.REVERBERATION, AudioFeature.DIFF_SURROUND,
    -> "ViPER4Android"

    AudioFeature.CONVOLUTION_IR, AudioFeature.VIRTUAL_BASS, AudioFeature.MULTIBAND_LIMITER,
    AudioFeature.LOW_CUT, AudioFeature.TRANSIENT_ENHANCEMENT, AudioFeature.EVEN_HARMONICS,
    AudioFeature.CHANNEL_BALANCE,
    -> "JamesDSP / WEcho"

    AudioFeature.CAPTURE_PROCESSING -> "RootlessJamesDSP"

    else -> "platform"
}

/** حصيلة العرض: ما يُكتب فعلًا اليوم، وما لا يُبنى — تُقرأ من الأحكام ولا تُعدّ في الشاشة. */
fun audioCapabilitySummary(verdicts: List<AudioFeatureVerdict>): AudioCapabilitySummary =
    AudioCapabilitySummary(
        writable = verdicts.count { it.support == AudioSupport.WRITABLE },
        needsAdapter = verdicts.count { it.support == AudioSupport.NEEDS_ADAPTER },
        unavailable = verdicts.count { it.support == AudioSupport.UNAVAILABLE },
        unknown = verdicts.count { it.support == AudioSupport.UNKNOWN },
    )

/** أربعة أعداد لا خامس لها — ولا تُعرض قبل قراءة (فالصفر هنا قراءةٌ لا افتراض). */
data class AudioCapabilitySummary(
    val writable: Int,
    val needsAdapter: Int,
    val unavailable: Int,
    val unknown: Int,
) {
    val total: Int get() = writable + needsAdapter + unavailable + unknown
}
