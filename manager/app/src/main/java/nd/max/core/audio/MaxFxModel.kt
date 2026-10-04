/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

/**
 * عقود `maxfx` من الطرف الكوتلنّيّ — **الـTSV هو الحقيقة الوحيدة**:
 * `fixtures/contracts/maxfx_params.tsv` و`maxfx_identity.tsv` يُقرآن من طرفَي القياس
 * (`maxfx/tests/dsp_test.c` و`MaxFxModelTest.kt`) ويُقارنان بهذا النموذج؛ فأيّ حقلٍ هنا
 * بلا مقابلٍ في العقد يُسقط الاختبار، والعكس. والجدول المُصرَّف نفسه يعيش في
 * `maxfx/src/maxfx_dsp.c` (`maxfx_param_at`) — والطفرة على أيّ طرفٍ تُسقط مجموعة القياس.
 */
object MaxFxModel {

    /** معرّف تدقيق العمليات — يُقرأ في `MaxManager.log` فيقاس سطر المحكّم بسطره. */
    const val OP_PREFIX = "maxfx"

    // ── الهويّة (maxfx_identity.tsv): ما يراه audioserver وqueryEffects() ────────────
    const val TYPE_UUID = "8f2c4a19-5d3b-4c8e-9a71-2b60d41f8c33"
    const val IMPL_UUID = "3b7e5c02-91af-4d6b-8c14-7e2a50b9d641"
    const val LIBRARY_FILE = "libmaxfx.so"

    /** اسم المكتبة كما يظهر في `queryEffects()` — نصٌّ حرفيٌّ قد يحوي مسافات. */
    const val LIBRARY_NAME = "MaxManager MaxFx"

    /** اسم المؤثّر كما يظهر في وصف `queryEffects()` — نصٌّ حرفيٌّ. */
    const val EFFECT_NAME = "MaxFx Deep Audio Engine"

    /** معرّف المؤثّر في وثيقة التهيئة — **حروفٌ معروفة فقط** لأنّه اسمُ عقدة XML. */
    const val EFFECT_CONFIG_NAME = "MaxFx"

    /** بادئة خاصيّات التحكّم — لاحقة الجدول `<key>` تحتها، **بنقطة الطرف الأخيرة**. */
    const val PROP_PREFIX = "persist.audio.maxfx."

    /**
     * معرّف المكتبة في وثيقة التهيئة — يُشتقّ من اسم الملفّ (صيغة AOSP:
     * `libbundlewrapper.so` ⇐ `bundle`)، و`path` في التهيئة هو **اسم الملفّ مجرّدًا**
     * فيبحث عنه الحمّال في مجلّدات `soundfx` — وهذا ما تعمل به مكوّنات AOSP نفسها على
     * أغلب الأجهزة. ويُوضع [LIBRARY_FILE] في `system/lib64/soundfx/` عند توليد الوحدة.
     */
    const val CONFIG_LIBRARY_ID = "maxfx"

    // ── جدول المعاملات (maxfx_params.tsv) — id فريد وثابت لا يُعاد استخدامه بعد الحذف ──

    /**
     * معاملٌ واحد كما يراه `effect_param_t` (`id`) وخاصيّة النظام (`key`) —
     * والافتراض والنطاق كما في العقد حرفيًّا، و`isInt` يحدّد صيغة القيمة
     * (صحيح ⇐ `int32`، وعائم ⇐ `float`).
     */
    data class Param(
        val id: Int,
        val key: String,
        val isInt: Boolean,
        val def: Double,
        val min: Double,
        val max: Double,
    )

    /** المعاملات **بترتيب الـTSV حرفيًّا** — الترتيب مُلزم لأنّ id هو مفتاح القراءة. */
    val params: List<Param> = listOf(
        Param(1, "enable", true, 0.0, 0.0, 1.0),
        Param(2, "input_gain_db", false, 0.0, -24.0, 12.0),
        Param(3, "output_gain_db", false, 0.0, -24.0, 12.0),
        Param(4, "bass_gain_db", false, 0.0, -12.0, 15.0),
        Param(5, "bass_freq_hz", false, 120.0, 40.0, 300.0),
        Param(6, "width", false, 1.0, 0.0, 2.0),
        Param(7, "clarity_db", false, 0.0, -12.0, 12.0),
        Param(8, "tube_amount", false, 0.0, 0.0, 1.0),
        Param(9, "comp_threshold_db", false, -3.0, -60.0, 0.0),
        Param(10, "comp_ratio", false, 1.0, 1.0, 20.0),
        Param(11, "comp_attack_ms", false, 10.0, 0.5, 200.0),
        Param(12, "comp_release_ms", false, 120.0, 10.0, 2000.0),
        Param(13, "limiter_ceiling_db", false, -0.5, -24.0, 0.0),
        Param(14, "eq1_gain_db", false, 0.0, -12.0, 12.0),
        Param(15, "eq2_gain_db", false, 0.0, -12.0, 12.0),
        Param(16, "eq3_gain_db", false, 0.0, -12.0, 12.0),
        Param(17, "eq4_gain_db", false, 0.0, -12.0, 12.0),
        Param(18, "eq5_gain_db", false, 0.0, -12.0, 12.0),
        Param(19, "eq1_freq_hz", false, 60.0, 20.0, 20000.0),
        Param(20, "eq2_freq_hz", false, 230.0, 20.0, 20000.0),
        Param(21, "eq3_freq_hz", false, 910.0, 20.0, 20000.0),
        Param(22, "eq4_freq_hz", false, 3600.0, 20.0, 20000.0),
        Param(23, "eq5_freq_hz", false, 14000.0, 20.0, 20000.0),
        Param(24, "eq1_q", false, 1.0, 0.3, 10.0),
        Param(25, "eq2_q", false, 1.0, 0.3, 10.0),
        Param(26, "eq3_q", false, 1.0, 0.3, 10.0),
        Param(27, "eq4_q", false, 1.0, 0.3, 10.0),
        Param(28, "eq5_q", false, 1.0, 0.3, 10.0),
    )

    private val byKey: Map<String, Param> = params.associateBy { it.key }

    /**
     * اسم الخاصية كاملًا كما يقرأه الطرف C (`%s%s` من البادئة واللاّحقة)،
     * أو `null` لمفتاحٍ خارج العقد — فلا تُكتب خاصيةٌ لا يقرأها أحد.
     */
    fun propKey(key: String): String? = byKey[key]?.let { PROP_PREFIX + it.key }

    /**
     * قيمة الخاصية كما تُكتب بعد الاسم (الاسم من [propKey]): **نصٌّ عشريٌّ** يقرأه
     * `strtol`/`strtof` في `maxfx_effect.c` **مقصوصٌ عند حدود العقد** — والحدّ يفرضه الطرفان
     * من الجدول نفسه. والصحيح يُقرَّب، و`NaN`/`∞` لا تُمرَّر (رقمٌ فاسدٌ يُقرأ قيمةً)،
     * والمجهول لا يُمرَّر — والغياب ليس صفرًا (ADR-07).
     */
    fun propValue(key: String, raw: Double): String? {
        val p = byKey[key] ?: return null
        if (!raw.isFinite()) return null
        return if (p.isInt) {
            Math.round(raw).toInt().coerceIn(p.min.toInt(), p.max.toInt()).toString()
        } else {
            formatDecimal(raw.coerceIn(p.min, p.max))
        }
    }

    /** نصٌّ عشريٌّ بلا أسّ وبلا أصفار طرفية: `120.0` ⇐ `120`، و`1.5` ⇐ `1.5`. */
    private fun formatDecimal(v: Double): String {
        val s = v.toString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    /**
     * سطر الإضافة إلى وحدة الطبقة النظاميّة (`AQ-09`) — **الأداة الوحيدة للتثبيت**:
     * `معرّف المكتبة|اسم ملفّها|معرّف المؤثّر|uuid|أجهزة|النوع`، وهو ما يقبله محلّل الشاشة
     * (`audioEffectAdditionOf`) ويكتبه مولّد الوحدة — وuuid التنفيذ هو uuid المؤثّر في التهيئة.
     *
     * **والحقل الخامس فارغٌ عن قصد** (لا ربط بجهازٍ بعينه): مؤثّرنا يُعلَن ولا يُفرض على مخرج،
     * فمن أراد ربطه بمخرجٍ يكتبه هو. **والسادس [TYPE_UUID] لا `IMPL_UUID`** — قُرئ في
     * `EffectConfig::parseLibrary` أنّ `type` هو نوع المؤثّر وأنّ `findUuid` **يُعيد `false`
     * بغيابه فيتخطّى المصنع المؤثّر ولا يفتح مكتبته أصلًا**؛ فسطرٌ بلا نوع يبدو مكتملًا وهو
     * في الجهاز غير موجود. ويُشتقّ من [TYPE_UUID] ولا يُكتب حرفيًّا ثانيًا، فلا ينحرف عن العقد.
     */
    fun installAddition(): String =
        "$CONFIG_LIBRARY_ID|$LIBRARY_FILE|$EFFECT_CONFIG_NAME|$IMPL_UUID||$TYPE_UUID"
}
