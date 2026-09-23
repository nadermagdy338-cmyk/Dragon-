package nd.max.core.atlas

/**
 * هامش الحرارة المتنبَّأ من المنصة (`PowerManager.getThermalHeadroom`) — إشارة أمامية مجانية.
 *
 * كل ما يملكه المشروع اليوم عن الحرارة **حالٌ لا مستقبل**: `ThermalUtil` تقرأ درجة الآن، والتنبؤ
 * في `core/jni` يُقدّر منحدرًا من قياساتنا. أما هذه الإشارة فتأتي من المنصة نفسها، وهي التي تعرف
 * جدول الخنق الحقيقي لأنها **هي** من يخنق. فهي تكمل المتنبئ وتكذّبه، لا تحلّ محلّه.
 *
 * ⚠️ والدلالة هنا خطّافة وتستحق التثبيت بالحرف — أكبر = أسوأ:
 *
 * | القيمة | المعنى |
 * | --- | --- |
 * | ‏٠٫٠ | بارد |
 * | ‏٠٫٧ | اقتراب |
 * | ‏١٫٠ | **عتبة الخنق الشديد** |
 *
 * فلذلك اتجاهها في [AtlasEffectMetric.THERMAL_HEADROOM] هو `LOWER_IS_BETTER` — وهذا أحد أشهر
 * مواضع القلب في هذا الباب (يُظنّ «headroom» مزيدًا من الهامش، وهو في الحقيقة **نسبة اقتراب**).
 *
 * ⚠️ وحدّ آخر موثَّق: استقصاء أسرع من عشر ثوانٍ قد تُعيد المنصة `NaN` — فلا وقت أسرع، ولا تُخزَّن
 * قيمة غير مقروءة كأنها قياس. فـ[AtlasThermalHeadroomPolicy.mayPoll] تفرض الفاصل، و[band] تُعلن
 * `UNMEASURABLE` بدل أن تُقارب `NaN` إلى صفر.
 *
 * وما لا يُثبته هذا الملف: **سلوك الإشارة على جهاز حقيقي** (هل تُعيد قيمًا على MediaTek؟ بأي
 * عتبات؟) — يبقى **يحتاج جهازًا**، والأداة هنا تعطي القواعد والقياس الشكلي لا الحكم العتادي.
 */

/** قراءة واحدة للإشارة. `headroom` = `null` تعني «لم تُقَس» (واجهة غائبة أو `NaN`). */
data class AtlasHeadroomReading(
    val headroom: Double?,
    val forecastSeconds: Int,
    val observedAtElapsedMs: Long,
    val thermalStatus: Int? = null,
) {
    init {
        require(forecastSeconds > 0) { "a forecast window is positive" }
        require(observedAtElapsedMs >= 0L) { "elapsed time is non-negative" }
        require(headroom == null || headroom.isFinite()) { "NaN is not a measurement" }
    }
}

/** فرقة الحرارة — أسماء سلوكية لا أرقامًا مُخترعة، والعتبتان المعلنتان من المنصة وما يليهما. */
enum class AtlasHeadroomBand {
    /** بعيد عن الخنق. */
    COOL,

    /** يقترب، ولا تدخّل لاحق. */
    WARMING,

    /** قريب جدًا من الخنق: هنا يُستحبّ التدخّل الاستباقي. */
    NEAR_THROTTLE,

    /** عند العتبة أو فوقها: المنصة تخنق الآن أو كادت. */
    THROTTLED,

    /** لا قياس: `NaN` أو واجهة غائبة أو استقصاء أسرع من الحدّ. */
    UNMEASURABLE,
}

object AtlasThermalHeadroomPolicy {

    /** الفاصل الأدنى الموثَّق بين استقصاءين (أسرع منه تُعيد المنصة `NaN`). */
    const val MIN_POLL_INTERVAL_MS: Long = 10_000L

    /** نافذة التنبؤ الافتراضية بالثواني — المنصة تطلب نافذة مستقبلية لا اللحظة. */
    const val DEFAULT_FORECAST_SECONDS: Int = 10

    /** عتبة المنصة المعلنة: ‏١٫٠ = الخنق الشديد. */
    const val THROTTLE_THRESHOLD: Double = 1.0

    /** وعتبة **لنا** معلنة صريحة (ليست من المنصة): فوقها يُعدّ التدخّل الاستباقي مباحًا. */
    const val NEAR_THROTTLE_BAND: Double = 0.85

    /** وتحت هذه: بارد. */
    const val COOL_BAND: Double = 0.5

    fun band(reading: AtlasHeadroomReading): AtlasHeadroomBand {
        val value = reading.headroom ?: return AtlasHeadroomBand.UNMEASURABLE
        if (!value.isFinite()) return AtlasHeadroomBand.UNMEASURABLE
        return when {
            value >= THROTTLE_THRESHOLD -> AtlasHeadroomBand.THROTTLED
            value >= NEAR_THROTTLE_BAND -> AtlasHeadroomBand.NEAR_THROTTLE
            value >= COOL_BAND -> AtlasHeadroomBand.WARMING
            value >= 0.0 -> AtlasHeadroomBand.COOL
            else -> AtlasHeadroomBand.UNMEASURABLE
        }
    }

    /**
     * هل يجوز الاستقصاء الآن؟
     *
     * الاستقصاء الأول مباح (`last == null`)، وما بعده يُشترط فيه الفاصل الموثَّق. والسبب عملي:
     * قيمة `NaN` تُخزَّن عند بعض الأجهزة إن استُقصي أسرع، و`NaN` **ليست** «بارد» — فحفظها سيُنتج
     * «حرارة جيدة» كاذبة في السجل.
     */
    fun mayPoll(lastPollAtElapsedMs: Long?, nowElapsedMs: Long): Boolean {
        if (nowElapsedMs < 0L) return false
        val last = lastPollAtElapsedMs ?: return true
        return nowElapsedMs - last >= MIN_POLL_INTERVAL_MS
    }

    /**
     * يحوّل القراءة إلى عيّنة أثر قابلة للقبول، أو `null` إن لم تُقَس.
     *
     * والحالة الدلالية هنا `REVIEWED_MATCH` بحقّ: الدلالة (‏٠ بارد · ‏١ عتبة) **موثَّقة في واجهة
     * المنصة** التي نستدعيها، لا مُستنتجة من قيم العقد. أما القيمة نفسها على جهاز بعينه فتبقى
     * موضع قياس ميداني — وهذا فرق بين «نعرف معنى الواجهة» و«نعرف كيف تُجيب هاتفك».
     */
    fun asEffectSample(
        reading: AtlasHeadroomReading,
        source: String,
        bootGeneration: Long,
        privilegeGeneration: Long = 0L,
    ): AtlasEffectSample? {
        val value = reading.headroom ?: return null
        if (!value.isFinite()) return null
        return AtlasEffectSample(
            metric = AtlasEffectMetric.THERMAL_HEADROOM,
            value = value,
            observedAtElapsedMs = reading.observedAtElapsedMs,
            source = source,
            bootGeneration = bootGeneration,
            privilegeGeneration = privilegeGeneration,
            semanticStatus = AtlasSemanticStatus.REVIEWED_MATCH,
        )
    }
}

enum class AtlasCrossCheckOutcome { AGREE, CONTRADICTED, UNMEASURABLE }

/** نتيجة تقاطع مصدرين، وتحمل **القيمتين** معها — فلا يُختار أحدهما بالصمت. */
data class AtlasCrossCheckResult(
    val outcome: AtlasCrossCheckOutcome,
    val reason: String,
    val headroom: Double?,
    val predictedCelsius: Double?,
    val currentCelsius: Double?,
)

/**
 * تقاطع مصادر الحرارة (فكرة `I-21` في وثيقة الفجوات): هل تتفق إشارة المنصة مع متنبّئنا؟
 *
 * ولا يُدمج الرقمان في رقم واحد: كلاهما يُعرض، والاختلاف يُعلَن. والسبب مبدئي لا تجميلي —
 * لو دمجناهما لصار الخطأ في أحدهما **غير مرئي**، وهذا أسوأ من الخطأ المعلَن.
 *
 * والقاعدتان المعلنتان (وكلتاهما قابلة للتكذيب):
 * - المنصة تقول «خنق أمامًا» (‏≥ ٠٫٨٥) والمتنبئ يقول «يبرد» ⇒ تناقض.
 * - المنصة تقول «بارد» (< ٠٫٥) والمتنبئ يقول «يسخن بقوة» ⇒ تناقض.
 */
object AtlasThermalCrossCheck {

    /** تسامح التقاطع بالدرجات: فروق أصغر لا تُعدّ اتجاهًا. */
    const val FORECAST_TOLERANCE_CELSIUS: Double = 2.0

    fun evaluate(
        reading: AtlasHeadroomReading?,
        predictedCelsius: Double?,
        currentCelsius: Double?,
    ): AtlasCrossCheckResult {
        val headroom = reading?.headroom
        val unusable = AtlasCrossCheckResult(
            outcome = AtlasCrossCheckOutcome.UNMEASURABLE,
            reason = AtlasCrossCheckReasons.UNMEASURABLE,
            headroom = headroom,
            predictedCelsius = predictedCelsius,
            currentCelsius = currentCelsius,
        )
        if (headroom == null || !headroom.isFinite()) return unusable
        if (predictedCelsius == null || !predictedCelsius.isFinite()) return unusable
        if (currentCelsius == null || !currentCelsius.isFinite()) return unusable

        val predictedDelta = predictedCelsius - currentCelsius
        val platformSaysHot = headroom >= AtlasThermalHeadroomPolicy.NEAR_THROTTLE_BAND
        val platformSaysCool = headroom < AtlasThermalHeadroomPolicy.COOL_BAND
        val predictorSaysCooling = predictedDelta < -FORECAST_TOLERANCE_CELSIUS
        val predictorSaysHeating = predictedDelta > FORECAST_TOLERANCE_CELSIUS

        return when {
            platformSaysHot && predictorSaysCooling -> unusable.copy(
                outcome = AtlasCrossCheckOutcome.CONTRADICTED,
                reason = AtlasCrossCheckReasons.HOT_VERSUS_COOLING,
            )

            platformSaysCool && predictorSaysHeating -> unusable.copy(
                outcome = AtlasCrossCheckOutcome.CONTRADICTED,
                reason = AtlasCrossCheckReasons.COOL_VERSUS_HEATING,
            )

            else -> unusable.copy(
                outcome = AtlasCrossCheckOutcome.AGREE,
                reason = AtlasCrossCheckReasons.AGREE,
            )
        }
    }
}

object AtlasCrossCheckReasons {
    const val AGREE = "cross-agree"
    const val HOT_VERSUS_COOLING = "cross-platform-hot-predictor-cooling"
    const val COOL_VERSUS_HEATING = "cross-platform-cool-predictor-heating"
    const val UNMEASURABLE = "cross-unmeasurable"
}
