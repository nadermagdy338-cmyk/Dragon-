/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

/**
 * Canonical identities for physical hardware controls.
 *
 * Policy layers must never invent their own key for the same kernel/provider
 * transaction: owner priority is meaningful only when every contender names
 * the physical knob identically.
 */
object HardwareControlKey {
    /**
     * بادئة مفتاح مقبض سياسة cpufreq.
     *
     * وليست خاصة لأن **التوثيق نفسه يحتاجها**: دليل السجل يشرح وحدة `cpu_limits:<policy>`، ولو
     * نسخ البادئة لصار للمقبض الواحد كتابتان — وهو بالضبط ما تمنعه بوابة
     * `ControlPlaneArchitectureTest.canonicalKeysAreNotReinvented`. فالتوثيق يُبنى من هنا أيضًا.
     */
    const val CPU_LIMITS_PREFIX = "cpu_limits:"

    /** بادئة مفتاح مقبض تردد جهاز GPU — عامّة للسبب نفسه. */
    const val GPU_FREQUENCY_PREFIX = "gpu_frequency:"

    fun cpuLimits(policyName: String): String = CPU_LIMITS_PREFIX + policyName
    fun gpuFrequency(deviceName: String): String = GPU_FREQUENCY_PREFIX + deviceName
    const val CPU_BOOST = "cpu_boost"

    /**
     * بادئة مفتاح **مستوى دفق صوتيّ** — كتابةٌ ليست `sysfs`: تمرّ بالـarbiter كما تمرّ غيرها.
     *
     * ولماذا تُوضع هنا لا في `core/audio/`: العقد الملزم في `SOUND-SCREEN-PLAN` §7 بند ١ — الاستوديو
     * يرسل نيّة، و`AudioStreamBackend` يمرّرها بمفتاح `HardwareControlKey.audioStream(...)`. ومفتاحٌ
     * يُبنى في ملفّ آخر يعني مقبضًا له كتابتان — وهو بالضبط ما تمنعه بوابة
     * `ControlPlaneArchitectureTest.canonicalKeysAreNotReinvented`.
     */
    const val AUDIO_STREAM_PREFIX = "audio_stream:"

    fun audioStream(streamToken: String): String = AUDIO_STREAM_PREFIX + streamToken

    /** True when [key] identifies one audio stream's level knob. */
    fun isAudioStream(key: String): Boolean = key.startsWith(AUDIO_STREAM_PREFIX)

    /** The stream token inside an audio_stream key; null for any other knob. */
    fun audioStreamToken(key: String): String? =
        if (isAudioStream(key)) key.substring(AUDIO_STREAM_PREFIX.length).takeIf(String::isNotEmpty) else null

    /**
     * بادئة مفتاح **مقبض مؤثّر صوتيّ** — `audio_effect:<type>:<param>` (تكملة ٢٢٨، `AQ-02`…`AQ-05`).
     *
     * ولماذا البادئة والوسيطان: المؤثّر الواحد **مقابض عدّة** (كسب نطاق، عتبة ضاغط، قوّة تعزيز) — ولو
     * صار لكلٍّ منها مفتاحٌ مبتكر في موضعه لصار للمؤثّر الواحد كاتبان، وهو ما تمنعه بوابة
     * `ControlPlaneArchitectureTest.canonicalKeysAreNotReinvented`. فالمفتاح يُبنى هنا، والوسيط يبقى
     * **نصًّا حرًّا** (اسم النطاق أو معامله) لأنّ الطبقة الصافية هي التي تُسمّي معاملاتها.
     */
    const val AUDIO_EFFECT_PREFIX = "audio_effect:"

    fun audioEffect(effectToken: String, param: String): String =
        AUDIO_EFFECT_PREFIX + effectToken + ":" + param

    /** True when [key] identifies one audio effect parameter knob. */
    fun isAudioEffect(key: String): Boolean = key.startsWith(AUDIO_EFFECT_PREFIX)

    /**
     * رمز المؤثّر داخل المفتاح — يُفصل عند **أوّل** `:` بعد البادئة، فمعاملٌ يحتوي `:` (مثل
     * `band:3`) لا يُقتطع.
     */
    fun audioEffectToken(key: String): String? {
        if (!isAudioEffect(key)) return null
        val rest = key.substring(AUDIO_EFFECT_PREFIX.length)
        return rest.substringBefore(':', missingDelimiterValue = "").takeIf(String::isNotEmpty)
    }

    /** المعامل داخل المفتاح (كلّ ما بعد أوّل `:`) — و`null` حين لا معامل، فلا يُخترع فراغ. */
    fun audioEffectParam(key: String): String? {
        if (!isAudioEffect(key)) return null
        val rest = key.substring(AUDIO_EFFECT_PREFIX.length)
        val index = rest.indexOf(':')
        if (index < 0) return null
        return rest.substring(index + 1).takeIf(String::isNotEmpty)
    }

    /**
     * بادئة مفتاح **إعداد محرّك التزييف** — `spoof_engine:<file>` (تكملة `SP-05`).
     *
     * **ولماذا هنا لا في `core/spoof/`:** هذا مقبضٌ يكتب ملفّ إعدادٍ **داخل وحدة Magisk غريبة**
     * يقرؤه المحرّك — وكتابتان لمقبضٍ واحد تعنيان طبقتين تتنازعان الملفّ نفسه. وموضعه الواحد هو ما
     * تفرضه بوابة `ControlPlaneArchitectureTest.canonicalKeysAreNotReinvented`.
     *
     * **والوسيط اسم الملفّ لا مساره** — فمسار `core/spoof/` يملكه عقد المحرّك وحده، ولو دخل المفتاح
     * لصار للمقبض الواحد مفتاحان بحسب موضعه.
     */
    const val SPOOF_ENGINE_PREFIX = "spoof_engine:"

    fun spoofEngine(target: String): String = SPOOF_ENGINE_PREFIX + target

    fun isSpoofEngine(key: String): Boolean = key.startsWith(SPOOF_ENGINE_PREFIX)

    fun spoofEngineTarget(key: String): String? =
        if (isSpoofEngine(key)) key.substring(SPOOF_ENGINE_PREFIX.length).takeIf(String::isNotEmpty) else null

    /**
     * بادئة مفتاح **سمات المازج** (معدّل/قناة/سلوك bit-perfect) — `audio_mixer:<deviceId>:<attr>`.
     *
     * ومعرّف الجهاز جزءٌ من المفتاح لأنّ السمات **على جهاز بعينه** لا على المنصّة كلها: طلبُ
     * `bit-perfect` على مكبّر داخليّ وطلبُه على مخرج USB مقبضان مختلفان، ولا يجوز أن يتزاحما.
     */
    const val AUDIO_MIXER_PREFIX = "audio_mixer:"

    fun audioMixer(deviceId: Int, attr: String): String = AUDIO_MIXER_PREFIX + deviceId + ":" + attr

    fun isAudioMixer(key: String): Boolean = key.startsWith(AUDIO_MIXER_PREFIX)

    /** بادئة مفتاح **التوجيه** (جهاز صوت المكالمة) — `audio_route:<name>`؛ والاسم من الطبقة الصافية. */
    const val AUDIO_ROUTE_PREFIX = "audio_route:"

    fun audioRoute(name: String): String = AUDIO_ROUTE_PREFIX + name

    fun isAudioRoute(key: String): Boolean = key.startsWith(AUDIO_ROUTE_PREFIX)

    fun audioRouteName(key: String): String? =
        if (isAudioRoute(key)) key.substring(AUDIO_ROUTE_PREFIX.length).takeIf(String::isNotEmpty) else null

    /**
     * بادئة مفتاح **طبقة مؤثّرات نظاميّة** (`AQ-09`) — `audio_system:<target>`.
     *
     * **ولماذا هي هنا لا في `core/audio/`:** هذا المقبض **أخطر كتابةٍ في الموجة**: يكتب ملفّ تهيئةٍ
     * داخل وحدة Magisk يحلّ محلّ ملفّ النظام — وكتابتان لمقبضٍ واحد تعنيان طبقتين تتنازعان الملفّ
     * نفسه. وموضعها الواحد هو ما تفرضه بوابة `ControlPlaneArchitectureTest.canonicalKeysAreNotReinvented`.
     *
     * **والهدف اسم الملفّ لا مساره:** مصدر الملفّ يتبدّل على الجهاز (`/odm` أو `/vendor`…)، فلو
     * دخل المسار في المفتاح لصار للمقبض الواحد مفتاحان بحسب قسم الجهاز — وهو العطب نفسه بعينه.
     */
    const val AUDIO_SYSTEM_PREFIX = "audio_system:"

    fun audioSystem(target: String): String = AUDIO_SYSTEM_PREFIX + target

    fun isAudioSystem(key: String): Boolean = key.startsWith(AUDIO_SYSTEM_PREFIX)

    fun audioSystemTarget(key: String): String? =
        if (isAudioSystem(key)) key.substring(AUDIO_SYSTEM_PREFIX.length).takeIf(String::isNotEmpty) else null

    /**
     * بادئة مفتاح **معامل مؤثّر مصنّع** (‏Dolby DAP وغيره) — `audio_vendor:<effectUuid>:<param>`
     * (تكملة ٢٣٠).
     *
     * **ولماذا مفتاحٌ ثالث للمؤثّرات لا `audio_effect:` نفسه:** ذاك مفتاح مؤثّرٍ تفتحه المنصّة
     * بأنواعها المعروفة، وهذا مفتاح مؤثّرٍ **يُفتح بمعرّفه** عبر مسار مختلف (انعكاس أو طبقة
     * نظاميّة). والمقبض الفيزيائي واحدٌ لكنّ **الوصول مختلف** — والاختلاف في الوصول هو ما يجب أن
     * يُفرّق بينهما في السجلّ: طبقةٌ تفشل هنا لا تعني أنّ الأخرى فشلت.
     *
     * **وقيمة المعرّف داخل المفتاح بأحرف صغيرة دائمًا** — فمعرّفٌ بأحرف كبيرة لا يصنع مقبضًا ثانيًا.
     */
    const val AUDIO_VENDOR_PREFIX = "audio_vendor:"

    fun audioVendor(effectUuid: String, param: String): String =
        AUDIO_VENDOR_PREFIX + effectUuid.trim().lowercase() + ":" + param

    fun isAudioVendor(key: String): Boolean = key.startsWith(AUDIO_VENDOR_PREFIX)

    /** رمز المؤثّر (معرّف التنفيذ) داخل المفتاح — يُفصل عند **أوّل** `:` بعد البادئة. */
    fun audioVendorEffect(key: String): String? {
        if (!isAudioVendor(key)) return null
        val rest = key.substring(AUDIO_VENDOR_PREFIX.length)
        return rest.substringBefore(':', missingDelimiterValue = "").takeIf(String::isNotEmpty)
    }

    /** المعامل داخل المفتاح (كلّ ما بعد أوّل `:`) — و`null` حين لا معامل، فلا يُخترع فراغ. */
    fun audioVendorParam(key: String): String? {
        if (!isAudioVendor(key)) return null
        val rest = key.substring(AUDIO_VENDOR_PREFIX.length)
        val index = rest.indexOf(':')
        if (index < 0) return null
        return rest.substring(index + 1).takeIf(String::isNotEmpty)
    }

    /**
     * بادئة مفتاح **معامل مؤثّر MaxFx** — `audio_maxfx:<param>` (تكملة ٢٣٥، `ADR-59`).
     *
     * **ولماذا مفتاحٌ رابع لا `audio_vendor:` نفسه:** ذاك يُفتح بمعرّف التنفيذ عبر جلسة مؤثّر
     * (انعكاس أو طبقة نظاميّة)، وهذا **يُكتب خاصيةَ نظام** يقرأها المؤثّر داخل `audioserver` —
     * والوصول مختلف، والاختلاف في الوصول هو ما يجب أن يُفرّق بين المقابض في السجلّ (القاعدة نفسها
     * التي فصّلت `audio_vendor:` عن `audio_effect:`). ولو اشترك المفتاحان لفُهم فشلُ كتابةِ خاصيةٍ
     * على أنه فشلُ جلسةٍ، والعكس — وهما صنفا عطبٍ لا يُعالَجان بالطريقة نفسها.
     *
     * **والوسيط مفتاح العقد نفسه** (`maxfx_params.tsv`) — فلا تسميةٌ ثانية لاسمٍ واحد.
     */
    const val AUDIO_MAXFX_PREFIX = "audio_maxfx:"

    fun audioMaxFx(param: String): String = AUDIO_MAXFX_PREFIX + param

    fun isAudioMaxFx(key: String): Boolean = key.startsWith(AUDIO_MAXFX_PREFIX)

    /** المعامل داخل المفتاح — و`null` حين لا معامل، فلا يُخترع فراغ. */
    fun audioMaxFxParam(key: String): String? =
        if (isAudioMaxFx(key)) key.substring(AUDIO_MAXFX_PREFIX.length).takeIf(String::isNotEmpty) else null

    /** True when [key] identifies one cpufreq policy's limits knob. */
    fun isCpuLimits(key: String): Boolean = key.startsWith(CPU_LIMITS_PREFIX)

    /** True when [key] identifies one GPU device's frequency knob. */
    fun isGpuFrequency(key: String): Boolean = key.startsWith(GPU_FREQUENCY_PREFIX)

    /** The device name inside a gpu_frequency key; null for any other knob. */
    fun gpuFrequencyDevice(key: String): String? =
        if (isGpuFrequency(key)) key.substring(GPU_FREQUENCY_PREFIX.length).takeIf(String::isNotEmpty) else null

    /** The policy name inside a cpu_limits key; null for any other knob. */
    fun cpuLimitsPolicy(key: String): String? =
        if (isCpuLimits(key)) key.substring(CPU_LIMITS_PREFIX.length).takeIf(String::isNotEmpty) else null
}
