/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * ───────────────────────── Attribution (Apache-2.0) ─────────────────────────
 * The DAP effect identifier this file matches against is taken from the **DolbyUI**
 * implementation — branch `rodin` of `Digimend-X-Rodin/packages_apps_DolbyUI` (app module
 * `LunarisDolby`), licensed **Apache-2.0** as stated in its own file headers, and used with the
 * copyright holder's written permission. Credit: `docs/PROVENANCE.md`.
 */
/*
 * الصوت — **اكتشاف مؤثّرات المصنّع** (Dolby وغيره): النموذج النقيّ وحده، بلا قارئ وبلا انعكاس.
 *
 * **ووُلد من سؤال المالك بنصّه (تكملة ٢٣٠):** «اجعل MaxManager يبحث تلقائيًّا عن Dolby في الجهاز
 * والـROM الحالي، بدل افتراض مكان ثابت له… وإذا تمّ اكتشافه لكنه غير قابل للوصول فيجب تسجيل
 * `DETECTED_BUT_UNAVAILABLE` بدل إظهار أنه يعمل».
 *
 * **والقياس الذي يوجب هذا الشكل — مقيسٌ على الـ`android.jar` الذي يُصرَّف عليه المشروع نفسه
 * (‏`javap`، `compileSdk 37`) ومُقابَل بمصدر AOSP (`frameworks/base`، فرع `main`):**
 *
 * | الواجهة | هل هي في سطح SDK العامّ؟ | ما يعنيه |
 * | --- | --- | --- |
 * | `AudioEffect.queryEffects()` | **نعم** (`public static Descriptor[]`) | **الاكتشاف ممكن دائمًا وبلا جذر** |
 * | `AudioEffect.Descriptor.uuid` | **نعم** (`public UUID uuid`) | معرّف **التنفيذ** — وهو ما يميّز مؤثّر المصنّع |
 * | `AudioEffect.EFFECT_TYPE_NULL` | **لا** (`@hide` · `ec7178ec-…`) | لا نستطيع بناء نوع المؤثّر العامّ |
 * | `AudioEffect(UUID,UUID,int,int)` | **لا** (`@hide`، سطر ٤٧٢ في المصدر) | **لا سبيل عامّاً لإرفاق مؤثّر مصنّع** |
 * | `setParameter(byte[],byte[])` / `getParameter(byte[],byte[])` | **لا** (`@hide`) | ولا قراءة معاملاته ولا كتابتها |
 *
 * ⇒ **الحكم المقيس:** الـSDK العامّ **يرى** مؤثّر المصنّع ولا **يلمسه**. وهذا هو الفرق الذي يفصل
 * «Dolby موجود على جهازك» عن «Dolby يعمل في MaxManager» — ولا يجوز أن يُخلطا في شاشة واحدة.
 *
 * **وثلاثيّات لا ثنائيّات (ADR-07):** قائمة مؤثّرات غير مقروءة ⇒ `UNKNOWN`، ولا تُقرأ «غير موجود».
 * والقائمة المقروءة الخالية من مؤثّر مصنّع هي **الدليل الوحيد** على الغياب.
 *
 * **وحدّ الدلالة معلن:** المعرّف المطابق لمعرّف من مصدر المصنّع هو `SOURCE_VERIFIED`، والتخمين على
 * الاسم/المُنفّذ `INFERRED` — ولا يُرقّى تخمينٌ إلى يقين في أيّ موضع (النمط نفسه في `core/atlas`).
 */
package nd.max.core.audio

import java.util.Locale

/**
 * عائلة مؤثّر مصنّع نعرفها.
 *
 * **والمعرفان مصدرهما مقيس لا مُقدَّر:** `DOLBY_DAP` من مستودع
 * `Digimend-X-Rodin/packages_apps_DolbyUI` (فرع `rodin`، وحدة `LunarisDolby`، رخصة Apache-2.0 —
 * `audio/DolbyAudioEffect.kt`: `EFFECT_TYPE_DAP = 9d4921da-8225-4f29-aefa-39537a04bcaa`)،
 * و`VENDOR_DSP` عائلة **بلا معرّف** لأننا لم نقرأ لها معرّفًا من مصدر — تُعرف بالاسم/المُنفّذ فقط.
 */
enum class VendorEffectFamily(val token: String) {
    /** Dolby Audio Processing — المعرّف مقيس من مصدر التطبيق المذكور أعلاه. */
    DOLBY_DAP("dolby_dap"),

    /** معالج مصنّع آخر — يُعرف بعلامة في اسمه أو مُنفّذه، **ولا معرّف ثابت له عندنا**. */
    VENDOR_DSP("vendor_dsp"),
}

/** قوّة الدلالة على العائلة — ولا يُرقّى `INFERRED` يومًا. */
enum class VendorEvidence(val token: String) {
    /** المعرّف (‏`Descriptor.uuid`) مطابقٌ حرفيًّا لمعرّفٍ مقروءٍ من مصدر المصنّع. */
    SOURCE_VERIFIED("source-verified"),

    /** الاسم أو المُنفّذ يحمل علامة المصنّع — **تخمينٌ يبقى تخمينًا**. */
    INFERRED("inferred"),
}

/**
 * **مصدر الاكتشاف** — يُقال، فلا يُخلط مؤثّرٌ أعلنته المنصّة بمؤثّرٍ مُعرَّفٍ في تهيئة النظام.
 *
 * والاثنان ليسا واحدًا: `AudioEffect.queryEffects()` تسأل عمّا **حمّلته** المنصّة فعلًا، ووثيقة
 * `audio_effects.xml` تقول ما **ستُحمّله** — فقد يُعرَّف مؤثّرٌ في التهيئة ولا يظهر في الجردة (مكتبةٌ
 * غائبة · تعطيلٌ من المصنّع · فشلُ تحميل). وحين يختلف المصدران **لا يُقدَّم التخمين على القياس**:
 * الجردة أوّلًا، والتهيئة شاهدٌ يقول «هو مذكور هناك» ولا يقول «هو يعمل».
 */
enum class VendorDiscoverySource(val token: String) {
    /** `AudioEffect.queryEffects()` — ما أعلنته المنصّة أنّه **مُحمَّل** الآن. */
    EFFECT_LIST("effect-list"),

    /** وثيقة `audio_effects.xml` — ما أعلنته تهيئة النظام أنّه **سيُحمَّل** (وقراءتها جذريّة). */
    AUDIO_EFFECTS_CONFIG("audio-effects-config"),
}

/**
 * المسار الذي نجرّبه للوصول — **والثلاثة منقولةٌ من أسئلة المالك بنصّها** («هل يحتاج Root؟ هل يحتاج
 * system/privileged access؟ هل يحتاج platform signature؟»)، فلا مسار رابع يُخترع.
 */
enum class VendorAccessRoute(val token: String) {
    /** SDK العامّ — **مقيسٌ أنه يصل للاكتشاف ولا يصل للتحكّم**. */
    PUBLIC_SDK("public-sdk"),

    /** واجهة مخفيّة بالانعكاس (`AudioEffect(UUID,UUID,int,int)` و`setParameter`/`getParameter`). */
    HIDDEN_API("hidden-api"),

    /** طبقة نظاميّة (وحدة Magisk بمكتبة مؤثّر + `audio_effects.xml`) — تحتاج **جذرًا وجهازًا**. */
    SYSTEM_LAYER("system-layer"),
}

/**
 * مؤثّر كما أعلنته المنصّة — **ومعرّف التنفيذ [uuid] هو الحقل الحاسم**، فمؤثّرات المصنّع تُسجَّل
 * في `audio_effects.xml` بنوع `EFFECT_TYPE_NULL` (‏`ec7178ec-…`) و**معرّف تنفيذٍ خاصّ** — فالمقارنة
 * على النوع لا تميّزها أبدًا، والمقارنة على المعرّف تميّزها دائمًا.
 */
data class VendorEffectDescriptor(
    val uuid: String,
    val typeUuid: String?,
    val name: String?,
    val implementor: String?,
    val connectMode: String?,
)

/** مؤثّر مصنّع عُرف — عائلته وقوّة دلالته ومعرّفه ومصدره. */
data class VendorEffectIdentity(
    val family: VendorEffectFamily,
    val evidence: VendorEvidence,
    val uuid: String,
    val name: String? = null,
    val implementor: String? = null,
    val source: VendorDiscoverySource = VendorDiscoverySource.EFFECT_LIST,
)

/**
 * نتيجة محاولة الإرفاق — أربعٌ لا خامسة.
 *
 * و`UNSUPPORTED` **ليست** `REFUSED`: الأولى «لا مسار أصلًا في هذه المنصّة» (مقيس)، والثانية
 * «جرّبنا فردّت المنصّة». وخلطُهما يُخفي أيّ الاثنين حدث — وهو عطبٌ صامت.
 */
enum class VendorAttachOutcome { NOT_ATTEMPTED, ATTACHED, REFUSED, UNSUPPORTED }

/**
 * ما قِيس من محاولة الإرفاق.
 *
 * @param controlOwned ثلاثيّ: `true` نملك التحكّم، `false` يملكه غيرنا، `null` لم يُقس.
 */
data class VendorAttachEvidence(
    val route: VendorAccessRoute,
    val outcome: VendorAttachOutcome,
    val controlOwned: Boolean? = null,
    val reason: String? = null,
)

/** رموز أسباب مؤثّرات المصنّع — موضعٌ واحد، فلا تتفرّق بين الطبقة والشاشة. */
object VendorAudioReason {
    const val EFFECTS_UNREADABLE = "effects-unreadable"
    const val NO_VENDOR_EFFECT = "no-vendor-effect-declared"

    /** مُعرَّفٌ في تهيئة النظام (`audio_effects.xml`) **ولا يظهر** في جردة المنصّة. */
    const val DECLARED_IN_CONFIG_ONLY = "declared-in-audio-effects-config-not-loaded"
    const val ATTACH_NOT_MEASURED = "vendor-attach-not-measured"
    const val ATTACHED_AND_OWNED = "vendor-effect-attached-and-owned"
    const val NOT_CONTROLLABLE = "vendor-effect-control-owned-by-another-app"
    const val CONTROL_UNMEASURED = "vendor-effect-control-not-measured"
    const val ATTACH_REFUSED = "vendor-attach-refused"
    const val ROUTE_UNSUPPORTED = "vendor-route-unsupported-on-this-platform"
    const val HIDDEN_API_BLOCKED = "hidden-api-blocked-or-absent"
    const val NO_PUBLIC_ROUTE = "public-sdk-exposes-no-constructor-for-a-vendor-effect"
    const val INVALID_UUID = "vendor-effect-uuid-invalid"
    const val ATTACH_THREW = "vendor-attach-threw"
    const val SESSION_CLOSED = "vendor-session-not-open"
    const val PARAM_LENGTH_MISMATCH = "vendor-param-length-mismatch"
}

/**
 * ترجمة جردة المنصّة ([`AudioEffectInfo`]) إلى جردة الاكتشاف — **ولا تُطوى معلومة**: النوع والمُنفّذ
 * ونمط الوصل تُنقل كما هي.
 *
 * **والحدّ معلن:** مؤثّرٌ بلا معرّف تنفيذ يُسقَط من الجردة — لأنّ المعرّف هو **مفتاح الهويّة** الذي
 * يُبنى عليه المفتاح والاختيار، واسمٌ بلا مفتاح يُنتج ادّعاءً لا يُثبَت. وهذا الإسقاط يُقاس في
 * الاختبار، ولا يُحوَّل إلى «غائب» في حكم الحضور إلّا إذا كانت القائمة كلها مقروءة (وهي كذلك هنا).
 */
fun vendorDescriptorsOf(effects: List<AudioEffectInfo>): List<VendorEffectDescriptor> =
    effects.mapNotNull { effect ->
        val uuid = effect.uuid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        VendorEffectDescriptor(
            uuid = uuid,
            typeUuid = effect.typeUuid,
            name = effect.name,
            implementor = effect.implementor,
            connectMode = effect.connectMode,
        )
    }

/**
 * المُلخَص الذي يُقاس — **وهو المُدخَل الوحيد للحكم**.
 *
 * @param descriptors قائمة مؤثّرات المنصّة، و`null` = **لم تُقرأ** (لا «فارغة»).
 * @param attach نتيجة محاولة الإرفاق، و`null` = لم تُجرَّب.
 */
data class VendorAudioEvidence(
    val descriptors: List<VendorEffectDescriptor>?,
    val attach: VendorAttachEvidence? = null,
    /**
     * ما أعلنته **تهيئة النظام** (`audio_effects.xml`) — فارغةٌ تعني «لم تُعرَّف مؤثّرات مصنّع هناك»
     * أو «لم تُقرأ التهيئة» (وقراءتها جذريّة، فلا تُفترض)، ولا تعني شيئًا آخر.
     */
    val config: List<VendorEffectIdentity> = emptyList(),
)

/**
 * حضور مؤثّر المصنّع — **والرمز الذي نصّه المالك محفوظٌ حرفيًّا:** `DETECTED_BUT_UNAVAILABLE`.
 */
enum class VendorAudioPresence(val token: String) {
    /** قُرئت القائمة ولم تُعلن مؤثّرًا مصنّعًا — **غيابٌ مبنيّ على قراءة**. */
    ABSENT("absent"),

    /** اكتُشف **ونملك التحكّم به** (إرفاقٌ نُجّح وقُرئ بعده `hasControl()`). */
    DETECTED_CONTROLLABLE("detected_controllable"),

    /** اكتُشف ولا مسار مقيسٌ للتحكّم به — **يُقال بهذا الاسم ولا يُعرض عاملًا**. */
    DETECTED_BUT_UNAVAILABLE("detected_but_unavailable"),

    /** القائمة نفسها لم تُقرأ — **ولا يُقال «غير موجود» عمّا لم يُقرأ** (ADR-07). */
    UNKNOWN("unknown"),
}

/**
 * حكم حضور مؤثّر المصنّع.
 *
 * @param reason رمزٌ من [`VendorAudioReason`] — يُترجم في الموارد، فلا تُصاغ الجملة هنا.
 * @param detail تفصيلٌ من القياس (اسم المؤثّر · مُنفّذه · المسار) — و`null` حين لا قياس.
 */
data class VendorAudioVerdict(
    val presence: VendorAudioPresence,
    val family: VendorEffectFamily? = null,
    val evidence: VendorEvidence? = null,
    val effectUuid: String? = null,
    val route: VendorAccessRoute? = null,
    /** من أين عُرف — `null` حين لم يُعرف شيء (غيابٌ أو جهل)، فلا يُنسب اكتشافٌ لمصدرٍ بلا اكتشاف. */
    val source: VendorDiscoverySource? = null,
    val reason: String,
    val detail: String? = null,
    /** **وُجد في الجردة أيضًا** — شاهدٌ ثانٍ يُقال، لا يُطوى. */
    val corroboratedByConfig: Boolean = false,
) {
    /**
     * هل **قيس** أنّ مؤثّرًا موجود؟ — وهي ليست نقيض «غائب»: `presence == UNKNOWN` تعني «لم يُقرأ»،
     * فتعود `false` هنا **مع** بقاء الحالة مجهولة — فلا تُقرأ `false` «غير موجود».
     */
    val isDetected: Boolean
        get() = presence == VendorAudioPresence.DETECTED_CONTROLLABLE ||
            presence == VendorAudioPresence.DETECTED_BUT_UNAVAILABLE

    val isControllable: Boolean get() = presence == VendorAudioPresence.DETECTED_CONTROLLABLE
}

/**
 * المعرّفات المقيسة من مصادرها — **ولا يُضاف معرّف من الذاكرة**.
 *
 * ومُلاحظةٌ مقيسة عن حدود التدقيق: كلمة `Dolby` وحدها ليست دليلًا (هي اسم مؤثّر عتاديّ يظهر في
 * وصف أجهزة)، فالمطابقة هنا على **المعرّف** أوّلًا، وعليه تُبنى `SOURCE_VERIFIED`.
 */
object VendorEffectCatalog {
    /** `packages_apps_DolbyUI` (Apache-2.0) — `EFFECT_TYPE_DAP`. */
    const val DOLBY_DAP_UUID = "9d4921da-8225-4f29-aefa-39537a04bcaa"

    /**
     * نوع المؤثّر العامّ (`EFFECT_TYPE_NULL`) — **مقيس من مصدر AOSP** (`frameworks/base`،
     * `AudioEffect.java`، `@hide @TestApi`). يُستعمل لبناء النوع عند الإرفاق بالانعكاس،
     * **ولا يعني أن الإرفاق متاح**: الـ`android.jar` لا يحمله أصلًا.
     */
    const val EFFECT_TYPE_NULL_UUID = "ec7178ec-e5e1-4432-a3f4-4657e6795210"

    /** مطابقةٌ حرفيّة على معرّف التنفيذ — والمفتاح بأحرف صغيرة. */
    private val byUuid: Map<String, VendorEffectFamily> = mapOf(
        DOLBY_DAP_UUID to VendorEffectFamily.DOLBY_DAP,
    )

    /**
     * علامات العائلات في الاسم والمُنفّذ — **تخمينٌ معلنٌ (`INFERRED`) لا يقين**.
     * والترتيب مقصود: الأخصّ أوّلًا، فلا تُبتلع عائلةٌ في أخرى.
     */
    private val markers: List<Pair<String, VendorEffectFamily>> = listOf(
        "dolby" to VendorEffectFamily.DOLBY_DAP,
        "dirac" to VendorEffectFamily.VENDOR_DSP,
        "dts" to VendorEffectFamily.VENDOR_DSP,
        "harman" to VendorEffectFamily.VENDOR_DSP,
        "misound" to VendorEffectFamily.VENDOR_DSP,
    )

    /** الاسم/المُنفّذ بأحرف صغيرة للبحث — بلا صياغة عرض. */
    private fun normalize(text: String?): String = text?.lowercase(Locale.US)?.trim().orEmpty()

    /**
     * عائلة مؤثّر واحد — **بالمعرّف أوّلًا، ثمّ بالعلامة**.
     *
     * ولماذا هذا الترتيب: المعرّف دليلٌ مأخوذ من مصدر المصنّع، والعلامة قرينة. ولو قدّمنا العلامة
     * لصار مؤثّرٌ باسم فيه «dolby» يقينًا، وليس كذلك.
     */
    fun familyOf(descriptor: VendorEffectDescriptor): Pair<VendorEffectFamily, VendorEvidence>? =
        familyOfRaw(descriptor.uuid, descriptor.name, descriptor.implementor)

    /**
     * العائلة من **حقولٍ خامّة** — نفس قاعدة التمييز بالضبط، لكنَّها تُستدعى من مصدرين: الجردة
     * وتهيئة النظام. وواحدةٌ لا اثنتان، فلا تُبتلع عائلةٌ في أخرى ولا يفترق الرأيان في تطبيقٍ واحد.
     */
    fun familyOfRaw(
        uuid: String?,
        name: String?,
        implementor: String?,
    ): Pair<VendorEffectFamily, VendorEvidence>? {
        uuid?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }?.let { key ->
            byUuid[key]?.let { return it to VendorEvidence.SOURCE_VERIFIED }
        }
        val haystack = normalize(name) + " " + normalize(implementor)
        markers.firstOrNull { (marker, _) -> haystack.contains(marker) }?.let { (_, family) ->
            return family to VendorEvidence.INFERRED
        }
        return null
    }
}

/**
 * مؤثّرات المصنّع في قائمة المنصّة — مرتَّبةً حتميًّا (بالمعرّف)، فلا يتبدّل ترتيب العرض بين فتحين.
 *
 * والقائمة الفارغة **قراءةٌ** («لا مؤثّرات مصنّع») — والفرق بينها وبين `null` هو الفرق بين
 * «أعرف أنه لا» و«لم أقرأ» (ADR-07).
 */
fun vendorEffectIdentities(descriptors: List<VendorEffectDescriptor>?): List<VendorEffectIdentity> =
    descriptors.orEmpty()
        .distinctBy { it.uuid.trim().lowercase(Locale.US) }
        .mapNotNull { descriptor ->
            val family = VendorEffectCatalog.familyOf(descriptor) ?: return@mapNotNull null
            VendorEffectIdentity(
                family = family.first,
                evidence = family.second,
                uuid = descriptor.uuid.trim().lowercase(Locale.US),
                name = descriptor.name,
                implementor = descriptor.implementor,
            )
        }
        .sortedWith(compareBy({ it.family.token }, { it.uuid }))

/**
 * مؤثّرات المصنّع **المُعرَّفة في تهيئة النظام** (`audio_effects.xml`) — المصدر الثاني للاكتشاف.
 *
 * **وهذا هو ما يجيب سؤال «أين يوجد؟»:** الجردة تقول «مُحمَّلٌ الآن»، والوثيقة تقول **أين** وبأيّ
 * مكتبة. والمطابقة تجري على `uuid` (**`SOURCE_VERIFIED`**) أو على علامةٍ في الاسم/المكتبة
 * (**`INFERRED`**) — بنفس قاعدة الجردة حرفيًّا، فلا رَأيان في تطبيقٍ واحد.
 *
 * **وحدّها معلن:** التعريف في التهيئة **ليس دليل عمل** — مكتبةٌ قد تكون غائبة فتسقط التهيئة كلها. فما
 * يُبنى عليه حكمٌ إلّا `DETECTED_BUT_UNAVAILABLE`، ولا يُرقّى إلى قابلٍ للتحكّم إلّا بقياس إرفاقٍ حقيقيّ.
 */
fun vendorConfigIdentities(document: AudioEffectsDocument): List<VendorEffectIdentity> =
    document.effects
        .mapNotNull { declaration ->
            val family = VendorEffectCatalog.familyOfRaw(
                declaration.uuid,
                declaration.name,
                declaration.library,
            ) ?: return@mapNotNull null
            VendorEffectIdentity(
                family = family.first,
                evidence = family.second,
                uuid = declaration.uuid?.trim()?.lowercase(Locale.US).orEmpty(),
                name = declaration.name,
                implementor = declaration.library,
                source = VendorDiscoverySource.AUDIO_EFFECTS_CONFIG,
            )
        }
        // والمفتاح: الـ`uuid` إن وُجد، وإلّا الاسم — فلا يُكرَّر مؤثّرٌ واحد بمفتاحٍ فارغ.
        .distinctBy { if (it.uuid.isNotEmpty()) it.uuid else it.name.orEmpty() }
        .sortedWith(compareBy({ it.family.token }, { it.uuid }, { it.name.orEmpty() }))

/**
 * الحكم — **وقاعدة واحدة لكل حالة، بترتيبٍ ملزم** (والترتيب هو ما يمنع حكمًا كاذبًا):
 *
 * 1. القائمة غير مقروءة ⇒ `UNKNOWN` — **ولا يُقال «غير موجود» عمّا لم يُقرأ**.
 * 2. مقروءة بلا مؤثّر مصنّع **وتهيئة النظام لا تُعرّف شيئًا** ⇒ `ABSENT` — **غيابٌ مثبتٌ بقراءتين**.
 * 2ب. مقروءة بلا مؤثّر مصنّع **وتُعرّفه التهيئة** ⇒ `DETECTED_BUT_UNAVAILABLE` (مُعرَّفٌ لا مُحمَّلٌ).
 * 3. مؤثّرٌ موجود ومحاولة الإرفاق **لم تُجرَّب** ⇒ `DETECTED_BUT_UNAVAILABLE` بسببٍ يقول
 *    «لم يُقس» — ولا يُقال «يعمل» بلا قياس، ولا يُقال «غير متاح» بلا سبب.
 * 4. أُرفق ونملكه ⇒ `DETECTED_CONTROLLABLE` — **الدليل `hasControl()` لا نجاح النداء**.
 * 5. أُرفق ولا نملكه / لم يُقس التحكّم ⇒ `DETECTED_BUT_UNAVAILABLE` بسببه المحدَّد.
 * 6. رُفض أو لا مسار ⇒ `DETECTED_BUT_UNAVAILABLE` بسبب المسار المقيس.
 *
 * **والترتيب ٥ قبل ٦ مقصود:** إرفاقٌ نجح ولا نملكه أخطر ما يُخفى، لأنّه يُقرأ «يعمل» بينما كتابتنا
 * عليه تُرفض.
 */
fun vendorAudioVerdict(evidence: VendorAudioEvidence): VendorAudioVerdict {
    val descriptors = evidence.descriptors
        ?: return VendorAudioVerdict(
            presence = VendorAudioPresence.UNKNOWN,
            reason = VendorAudioReason.EFFECTS_UNREADABLE,
        )

    val found = vendorEffectIdentities(descriptors).firstOrNull()
    // والمصدر الثاني يُسأل **قبل الحكم بالغياب**: مؤثّرٌ مُعرَّفٌ في `audio_effects.xml` ولم يُحمّله
    // المنصّة ليس «غير موجود» — بل **`DETECTED_BUT_UNAVAILABLE`**، وهي الحالة التي سُمّيت بأمر المالك.
    val declared = evidence.config.firstOrNull()
    if (found == null && declared != null) {
        return VendorAudioVerdict(
            presence = VendorAudioPresence.DETECTED_BUT_UNAVAILABLE,
            family = declared.family,
            evidence = declared.evidence,
            effectUuid = declared.uuid.takeIf { it.isNotBlank() },
            source = declared.source,
            reason = VendorAudioReason.DECLARED_IN_CONFIG_ONLY,
            detail = declared.name ?: declared.implementor,
        )
    }
    if (found == null) {
        return VendorAudioVerdict(
            presence = VendorAudioPresence.ABSENT,
            reason = VendorAudioReason.NO_VENDOR_EFFECT,
        )
    }

    val base = VendorAudioVerdict(
        presence = VendorAudioPresence.DETECTED_BUT_UNAVAILABLE,
        family = found.family,
        evidence = found.evidence,
        effectUuid = found.uuid,
        route = evidence.attach?.route,
        // والجردة تُقدَّم على التهيئة: **القياس أوّلًا، والشاهد بعده** — والمصدران يُذكران معًا إن اجتمعا.
        source = found.source,
        reason = VendorAudioReason.ATTACH_NOT_MEASURED,
        detail = found.name ?: found.implementor,
        corroboratedByConfig = declared != null,
    )

    val attach = evidence.attach ?: return base

    return when (attach.outcome) {
        VendorAttachOutcome.NOT_ATTEMPTED -> base.copy(
            route = attach.route,
            reason = attach.reason ?: VendorAudioReason.ATTACH_NOT_MEASURED,
        )
        VendorAttachOutcome.ATTACHED -> when (attach.controlOwned) {
            true -> base.copy(
                presence = VendorAudioPresence.DETECTED_CONTROLLABLE,
                reason = VendorAudioReason.ATTACHED_AND_OWNED,
            )
            false -> base.copy(reason = VendorAudioReason.NOT_CONTROLLABLE)
            null -> base.copy(reason = VendorAudioReason.CONTROL_UNMEASURED)
        }
        VendorAttachOutcome.REFUSED -> base.copy(
            reason = attach.reason ?: VendorAudioReason.ATTACH_REFUSED,
        )
        VendorAttachOutcome.UNSUPPORTED -> base.copy(
            reason = attach.reason ?: VendorAudioReason.ROUTE_UNSUPPORTED,
        )
    }
}
