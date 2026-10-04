/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary or confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **نموذج المعالجة الديناميكيّة** (`DynamicsProcessing`): معادلان (قبل/بعد)، وضاغط متعدّد النطاقات،
 * ووحدُهما المُحدِّد، و**دخل كل قناة**. صافٍ يُقاس على JVM (`AQ-04`).
 *
 * **وهذا هو المحرّك الحقيقيّ الذي يقابل ما طلبه المالك** (ضاغط · مُحدِّد · توازن القنوات · تعزيز الجهير
 * والعلوّ) — وكله من واجهة **عامّة** موجودة في المنصّة (مقيس بـ`javap` في الخطّة §2.2)، لا من محرّك
 * مملوك ولا من التقاط.
 *
 * **وموضع الصدق في هذا الملفّ:** المنصّة **لا تُعلن مدًى** لمعاملات الضاغط والمُحدِّد (`getConfig()` تُعيد
 * القيم لا حدودها). فالمدى المعروض في الشاشة **حدُّنا المُعلَن** لا حدُّ العتاد، **والحكم على ما يُقرأ
 * بعد الكتابة لا على ما نتمنّاه**: إن ردّت المنصّة القيمة مقيَّدة، عرضت الشاشة قيمة المنصّة — وهي الحقيقة.
 * ولهذا كل ثابت هنا مسبوق بـ`UI_` صراحةً، فلا يُقرأ يومًا حكمًا عتاديًّا.
 *
 * **والتوازن ليس معاملًا مصنوعًا:** هو **دخل القناتين** في المحرّك نفسه (`setInputGainbyChannel`) — أي
 * أنّ ما يُكتب هنا يُقرأ هناك، ولا DSP من عندنا.
 */
package nd.max.core.audio

import kotlin.math.abs
import kotlin.math.roundToInt

/** نطاق معادل واحد في المحرّك (قبل أو بعد): عتبة قطع + كسب — **ولا `Q`**: المنصّة لا تُعلنه (الخطّة §2.2). */
data class DynamicsEqBand(
    val index: Int,
    val cutoffHz: Float,
    val gainDb: Float,
    val enabled: Boolean,
)

/** نطاق ضاغط واحد بكل معامله — أسماء الحقول من واجهة المنصّة نفسها. */
data class DynamicsMbcBand(
    val index: Int,
    val cutoffHz: Float,
    val enabled: Boolean,
    val thresholdDb: Float,
    val ratio: Float,
    val attackMs: Float,
    val releaseMs: Float,
    val kneeDb: Float,
    val noiseGateDb: Float,
    val expanderRatio: Float,
    val preGainDb: Float,
    val postGainDb: Float,
)

/** المُحدِّد: عتبة · نسبة · هجوم · تحرير · كسب بعديّ — ومعه `inUse` كما أعلنته المنصّة. */
data class DynamicsLimiter(
    val enabled: Boolean,
    val inUse: Boolean,
    val thresholdDb: Float,
    val ratio: Float,
    val attackMs: Float,
    val releaseMs: Float,
    val postGainDb: Float,
)

/** لقطة المحرّك كاملة — **وكل حقل معاملٌ حقيقيّ يُقرأ بعد الكتابة**. */
data class AudioDynamicsSnapshot(
    val channels: Int,
    val preEqInUse: Boolean,
    val postEqInUse: Boolean,
    val mbcInUse: Boolean,
    val limiterInUse: Boolean,
    val preEq: List<DynamicsEqBand>,
    val postEq: List<DynamicsEqBand>,
    val mbc: List<DynamicsMbcBand>,
    val limiter: DynamicsLimiter?,
    /** دخل كل قناة بالديسيبل — **وهو التوازن** حين تكون القناتان اثنتين. */
    val inputGainsDb: List<Float>,
)

/** حدود الواجهة المُعلَنة — **حدُّنا لا حدُّ العتاد** (انظر رأس الملفّ)، وتُکتَب في الشاشة كذلك. */
object AudioDynamicsBounds {
    const val UI_CUTOFF_MIN_HZ = 20f
    const val UI_CUTOFF_MAX_HZ = 20000f
    const val UI_GAIN_MIN_DB = -12f
    const val UI_GAIN_MAX_DB = 12f
    const val UI_THRESHOLD_MIN_DB = -60f
    const val UI_THRESHOLD_MAX_DB = 0f
    const val UI_RATIO_MIN = 1f
    const val UI_RATIO_MAX = 20f
    const val UI_ATTACK_MIN_MS = 1f
    const val UI_ATTACK_MAX_MS = 200f
    const val UI_RELEASE_MIN_MS = 10f
    const val UI_RELEASE_MAX_MS = 1000f
    const val UI_KNEE_MIN_DB = 0f
    const val UI_KNEE_MAX_DB = 30f
    const val UI_GATE_MIN_DB = -80f
    const val UI_GATE_MAX_DB = 0f
    const val UI_POST_GAIN_MIN_DB = -12f
    const val UI_POST_GAIN_MAX_DB = 12f

    /** أقصى إزاحة توازن لكل قناة — حدُّنا المُعلَن، ويُقرأ بعده ما خزّنته المنصّة. */
    const val UI_BALANCE_MAX_DB = 6f
}

/** قيَّد قيمة في مدًى مُعلَن — بلا قسمةٍ ولا تحويل، والاستدعاء يمرّره صريحًا. */
fun dynamicsClamp(value: Float, min: Float, max: Float): Float {
    if (max <= min) return min
    return value.coerceIn(min, max)
}

/**
 * **التوازن ← دخل القناتين.** موضع القيمة `-1..1` (يسار..يمين):
 *
 * - `0` ⇒ **القناتان على صفر**: التوازن في وسطه لا يخفّض شيئًا. (وهذا هو الفرق بين موازنةٍ وعقابٍ:
 *   صيغة «خفضٌ متقابل» تُخفض القناتين معًا في الوسط فينخفض الصوت بمجرّد لمس المقبض.)
 * - `-1` ⇒ اليسار مكتوم (`-UI_BALANCE_MAX_DB`) واليمين كما هو — **لا «رفعٌ» للقناة الأخرى**، فلا
 *   يرتفع الصوت الكلّي حين يُطلب التوازن (وهو الفرق بين موازنةٍ وتضخيمٍ مقنّع).
 *
 * والقيمة تُقرَّب إلى `0.1 dB` لأنّ ما يُكتب Float يُقرأ Float، والعرض بمنزلتين يجعل الحكم حرفيًّا.
 */
fun dynamicsBalanceGains(balance: Float): Pair<Float, Float> {
    val position = balance.coerceIn(-1f, 1f)
    val max = AudioDynamicsBounds.UI_BALANCE_MAX_DB
    // **خفضٌ فقط، والوسط على صفر:** القناة التي نبتعد عنها تبقى كما هي، والقناة التي نقترب منها
    // تُخفَّض وحدها. وصيغة «خفضٍ متقابل» كانت ستُخفّض القناتين معًا في الوسط فينخفض الصوت بمجرّد
    // أن يُلمس المقبض — وهو تفاعل غير مقصود يُخفى، وقد أمسكته هذه الجولة بالقياس.
    val left = (-max * (-position).coerceAtLeast(0f)).round1()
    val right = (-max * position.coerceAtLeast(0f)).round1()
    return left to right
}

/**
 * وعكسها: دخل القناتين ← موضع التوازن — **والعكس مُشتقّ من الصيغة نفسها** لا من صيغة ثانية تنحرف
 * عنها: القناة المخفّضة هي التي تحمل الموضع (يمينٌ ⇒ موجب، ويسارٌ ⇒ سالب)، والقناتان على صفر ⇒ الوسط.
 */
fun dynamicsBalanceOf(inputGainsDb: List<Float>): Float? {
    if (inputGainsDb.size < 2) return null
    val left = inputGainsDb[0]
    val right = inputGainsDb[1]
    val max = AudioDynamicsBounds.UI_BALANCE_MAX_DB
    if (max <= 0f) return null
    val position = when {
        right < 0f -> -right / max
        left < 0f -> left / max
        else -> 0f
    }
    return position.coerceIn(-1f, 1f)
}

private fun Float.round1(): Float = (this * 10f).roundToInt() / 10f

/** هل التوازن متساوٍ؟ — يُستعمل لعرض «متوازن» بلا صفر مصنوع من غياب قراءة. */
fun dynamicsBalanceIsCentered(inputGainsDb: List<Float>): Boolean? {
    val position = dynamicsBalanceOf(inputGainsDb) ?: return null
    return abs(position) < 0.01f
}

/** عرض الديسيبل بمنزلة واحدة دائمًا — فما يُقرأ بعد الكتابة يطابق ما عرضناه قبلها. */
fun dynamicsFormatDb(value: Float?): String? {
    if (value == null) return null
    val text = ((value * 10f).roundToInt() / 10f).toString()
    return if (value > 0f) "+$text dB" else "$text dB"
}

/** عرض الزمن بالميلي‑ثانية، وتحت الثانية يبقى `ms` ويُقرَّب إلى صحيح. */
fun dynamicsFormatMs(value: Float?): String? {
    if (value == null) return null
    return "${value.roundToInt()} ms"
}

/** عرض النسبة `4.0:1` — الصيغة المعروفة للضغط، وهي تحمل معناها بلا شرح. */
fun dynamicsFormatRatio(value: Float?): String? {
    if (value == null) return null
    return "${((value * 10f).roundToInt() / 10f)}:1"
}

/** التردد داخل المحرّك هرتزًا: تحت 1000 يُعرض صحيحًا، وفوقها كيلوهرتز. */
fun dynamicsFormatHz(value: Float?): String? {
    if (value == null || value <= 0f) return null
    if (value < 1000f) return "${value.roundToInt()} Hz"
    val khz = (value / 100f).roundToInt() / 10f
    return "$khz kHz"
}

/**
 * مناسب لرسم: موضع النطاق على المحور (لوغاريتميّ) — يُستعمل في مخطط النطاقات لا في الحكم.
 * و`null` حين يكون التردد غير صالح، فلا نقطة مُخترعة.
 */
fun dynamicsBandPosition(value: Float?): Float? {
    if (value == null || value <= 0f) return null
    val min = AudioDynamicsBounds.UI_CUTOFF_MIN_HZ
    val max = AudioDynamicsBounds.UI_CUTOFF_MAX_HZ
    val logMin = kotlin.math.ln(min)
    val logMax = kotlin.math.ln(max)
    if (logMax <= logMin) return null
    return ((kotlin.math.ln(value) - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
}

// ───────────────────────── ‏AQ-05: سلّم الإرفاق — **قِيس الخطأ، فأُصلح** (تكملة ٢٣٩) ─────────────────────────

/**
 * سبب وجود هذا السلّم — **مقيس لا مُفترض**:
 *
 * كان الإرفاق محاولةً **واحدة**: `DynamicsProcessing(0, 0, Config(VARIANT_FAVOR_FREQUENCY_RESOLUTION،
 * ٦ نطاقات، ٤ ضواغط، ٦ نطاقات))` — أي أنّنا كنّا **نفرض هندسةً اخترعناها** على عتادٍ لم نسأله عن هندسته،
 * و**أيُّ** فشلٍ في أيّ خطوةٍ يُطوى في رمزٍ واحد `effect-attach-refused` فلا يُعرف أين سقطنا. وهذا يُفسّر
 * لوق المالك: `effect-attach-refused` بلا تفصيل، ولا نسخةَ واحدة نجحت. وقد كانت `openDynamics` بلا
 * أيّ محاولةٍ أُخرى، فبقي المحرّك — وهو موجودٌ في المنصّة — **مُغلقًا على جهازه**.
 *
 * **وهندسة المنصّة الافتراضيّة** (`DynamicsProcessing(جلسة)` بلا `Config` — مقيسة في المصدر: صورة
 * `DynamicsProcessing(int session)` تنادي `(0, session, null)`): ليس فيها عددٌ اخترعناه. فأوّل خطوةٍ
 * هنا **تسأل المنصّة**، ولا تُخبر أحدًا عن عدد نطاقاته.
 */
enum class DynamicsAttachStep(val token: String) {
    /** هندسة المنصّة نفسها — **بلا `Config` إطلاقًا**: لا عدد نطلبه، ولا هندسة نفترضها. */
    PLATFORM_DEFAULT("platform-default"),

    /** هندسة **دقّة التردّد** بأعداد النطاقات (مُقاسةً إن قيسناها، وإلا فهي طلبنا المُعلَن). */
    RESOLUTION_VARIANT("resolution-variant"),

    /** وآخر ما يُجرَّب: هندسة **زمن الاستقرار** — فبعض المعالجات تُفضّلها. */
    TIME_VARIANT("time-variant"),
}

/**
 * سلّم المحاولة **بترتيبه** — والأوّل **أرجحها** لا أضعفها: نبدأ بما تعرفه المنصّة عن نفسها،
 * ثمّ نضيّق إلى ما نطلبه نحن. والترتيب ثابت من التعريف لا من ترتيب وصول قياس، فالشاشة واللوق يتّفقان.
 */
val DYNAMICS_ATTACH_LADDER: List<DynamicsAttachStep> = listOf(
    DynamicsAttachStep.PLATFORM_DEFAULT,
    DynamicsAttachStep.RESOLUTION_VARIANT,
    DynamicsAttachStep.TIME_VARIANT,
)

/** ما أخبرتنا به المنصّة عن هندستها **بعد إرفاقٍ ناجح** — يُستعمل في الخطوات التالية ولا يُخترع. */
data class DynamicsMeasuredArchitecture(
    val variant: Int?,
    val channels: Int,
    val preEqBands: Int,
    val mbcBands: Int,
    val postEqBands: Int,
)

/**
 * خطة إرفاق واحدة: أيّ خطوة، وأيّ أعداد نطاقات تُبنى بها.
 *
 * و[usePlatformDefault] هو الفارق الحقيقيّ: `true` ⇒ **لا يُبنى `Config` أصلًا**.
 */
data class DynamicsAttachPlan(
    val step: DynamicsAttachStep,
    val channels: Int,
    val preEqBands: Int,
    val mbcBands: Int,
    val postEqBands: Int,
    val usePlatformDefault: Boolean,
)

/**
 * عدد النطاقات الذي **يُبنى به** في خطوةٍ مطلوبة: **المقيسُ أولى من المطلوب**، وما دون نطاقٍ واحد لا يُبنى.
 * ومعلومٌ أنّ هذا حكمٌ على العدد لا على صحّة الهندسة — فالصحّة يُثبتها الإرفاق نفسه لا حسابٌ هنا.
 */
fun dynamicsBandCounts(requested: Int, measured: Int?): Int {
    val value = if (measured != null && measured > 0) measured else requested
    return if (value < 1) 1 else value
}

/**
 * يبني خطة خطوةٍ واحدة — **وهي نقيّة**: لا تنادي شيئًا ولا تعرف المنصّة.
 *
 * والقاعدة: خطوةُ المنصّة لا تُبنى لها أعداد (لا `Config`)، وغيرها يأخذ **المقيس إن وُجد** ثمّ المطلوب.
 */
fun dynamicsAttachPlan(
    step: DynamicsAttachStep,
    channels: Int,
    requestedEqBands: Int = DEFAULT_REQUESTED_EQ_BANDS,
    requestedMbcBands: Int = DEFAULT_REQUESTED_MBC_BANDS,
    measured: DynamicsMeasuredArchitecture? = null,
): DynamicsAttachPlan {
    val safeChannels = if (channels < 1) 1 else channels
    if (step == DynamicsAttachStep.PLATFORM_DEFAULT) {
        return DynamicsAttachPlan(
            step = step,
            channels = safeChannels,
            preEqBands = 0,
            mbcBands = 0,
            postEqBands = 0,
            usePlatformDefault = true,
        )
    }
    return DynamicsAttachPlan(
        step = step,
        channels = safeChannels,
        preEqBands = dynamicsBandCounts(requestedEqBands, measured?.preEqBands),
        mbcBands = dynamicsBandCounts(requestedMbcBands, measured?.mbcBands),
        postEqBands = dynamicsBandCounts(requestedEqBands, measured?.postEqBands),
        usePlatformDefault = false,
    )
}

/** عدد نطاقات المعادل الذي **نطلبه** حين لا قياس — وهو المُعلَن في الشاشة، لا سحرٌ في موضعين. */
const val DEFAULT_REQUESTED_EQ_BANDS = 6

/** عدد نطاقات الضاغط متعدّد النطاقات الذي **نطلبه** حين لا قياس. */
const val DEFAULT_REQUESTED_MBC_BANDS = 4

/**
 * رمز رفض خطوةٍ بعينها — **فالرمز الواحد كان يخفي ثلاث خطوات**.
 * ولأنّ الخطوة الأخيرة هي التي تُجرَّب بعد غيرها، فالفشل **يُعلن بنفسه**: `TIME_VARIANT` ⇒ جُرّب الثلاثة.
 */
fun dynamicsAttachRefusal(step: DynamicsAttachStep): String = when (step) {
    DynamicsAttachStep.PLATFORM_DEFAULT -> AudioEffectReason.ATTACH_PLATFORM_DEFAULT_REFUSED
    DynamicsAttachStep.RESOLUTION_VARIANT -> AudioEffectReason.ATTACH_RESOLUTION_VARIANT_REFUSED
    DynamicsAttachStep.TIME_VARIANT -> AudioEffectReason.ATTACH_TIME_VARIANT_REFUSED
}

/**
 * الرمز **الذي يُعرض** بعد فشل السلّم كلّه: واحدٌ صريحٌ لا يترك القارئ يظنّ أنّ محاولةً تُركت.
 * وما دونه من رموز الخطوات يُسجَّل للتشخيص في `audioOp` (وهو موضعها الطبيعيّ) لا كحكمِ جهاز.
 */
const val DYNAMICS_ATTACH_ALL_REFUSED = AudioEffectReason.ATTACH_ALL_STEPS_REFUSED
