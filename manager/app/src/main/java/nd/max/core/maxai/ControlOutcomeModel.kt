package nd.max.core.maxai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * نواة التعلّم — Kotlin أصالةً، بلا اعتماد على مكتبة أصلية.
 *
 * لماذا لا نكتفي بالوكيل القديم (RLAgent في Rust)؟ لأنه يتعلّم ستة
 * أفعال خشنة ثابتة (ملف أداء/توفير/متوازن...)، فحتى بتعلّم مثالي يظل
 * سقفه سقف مبدّل ملفات. والأخطر: يتطلب تجريب كل فعل ثماني مرات على
 * عتادك الحقيقي كي "يستكشف" — أي تسخين جهازك لأجل التعلّم. وهو صامت
 * تمامًا (rlSteps=0) على أي جهاز بلا .so.
 *
 * هنا يتعلّم النظام على مستوى **المقبض الواحد في سياقه**:
 *   المفتاح = (مقبض، اتجاه، سياق التطبيق)
 *   المتعلَّم = توزيع أثر حقيقي مقيس: Δدرجة الهدف، وΔالحرارة.
 *
 * كل نتيجة تُعزى إلى المقبض الذي تحرّك فعلًا — لا إلى كتلة غامضة.
 * التقدير تزايدي (Welford) فلا يخزن تاريخًا ولا يحتاج دفعات إعادة
 * تشغيل، ويعمل من أول ملاحظة بثقة معلنة بدل ادعاء يقين.
 */
@Singleton
class ControlOutcomeModel @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * إحصاء تزايدي لأثر مقبض: المتوسط والتباين وعدد العينات.
     * نحتفظ بأثرين لأن القرار الجيد يوازنهما: مكسب الهدف مقابل
     * التكلفة الحرارية. مقبض يرفع الأداء 0.1 ويرفع الحرارة 4° ليس
     * "نجاحًا" حتى لو تحسنت الدرجة.
     */
    data class Effect(
        val samples: Int = 0,
        val meanGain: Float = 0f,
        val m2Gain: Float = 0f,
        val meanThermal: Float = 0f,
    ) {
        /** الانحراف المعياري لمكسب الهدف (عدم اليقين المقيس). */
        val gainStdDev: Float
            get() = if (samples < 2) DEFAULT_PRIOR_STD else sqrt(m2Gain / (samples - 1))

        /** ثقة [0..1] تنمو مع العينات — لا يقين مصطنع من عينة واحدة. */
        val confidence: Float
            get() = (samples.toFloat() / (samples + CONFIDENCE_HALF_LIFE)).coerceIn(0f, 1f)

        /** إضافة ملاحظة جديدة بخوارزمية Welford (عددياً مستقرة). */
        fun observe(gain: Float, thermalDelta: Float): Effect {
            val n = samples + 1
            val delta = gain - meanGain
            val newMean = meanGain + delta / n
            val newM2 = m2Gain + delta * (gain - newMean)
            val newThermal = meanThermal + (thermalDelta - meanThermal) / n
            return Effect(n, newMean, newM2, newThermal)
        }
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val effects = linkedMapOf<String, Effect>()

    init {
        runCatching {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                root.keys().forEach { key ->
                    val o = root.getJSONObject(key)
                    effects[key] = Effect(
                        samples = o.optInt("n", 0),
                        meanGain = o.optDouble("g", 0.0).toFloat(),
                        m2Gain = o.optDouble("m2", 0.0).toFloat(),
                        meanThermal = o.optDouble("t", 0.0).toFloat(),
                    )
                }
            }
        }
    }

    /**
     * الأثر المتوقع لخطوة لم تُنفَّذ بعد.
     *
     * السياق يتدرج بصدق: تاريخ هذا المقبض مع هذا التطبيق أولًا، فإن لم
     * يكن كافيًا يُستعار تاريخه العام على هذا الجهاز (قرار #18: تاريخ
     * التطبيق سابقة، والقياس الحالي يحكم). لا اختراع قيمة من العدم:
     * بلا أي عينة تعود الثقة صفرًا ويقرر المخطِّط بالحذر.
     */
    fun expectedEffect(key: String, direction: ControlRegistry.Direction, appContext: String): Effect {
        val specific = effects[compose(key, direction, appContext)]
        if (specific != null && specific.samples >= MIN_SPECIFIC_SAMPLES) return specific
        val global = effects[compose(key, direction, GLOBAL_CONTEXT)]
        return when {
            specific != null && global != null -> blend(specific, global)
            specific != null -> specific
            global != null -> global
            else -> Effect()
        }
    }

    /**
     * يسجّل نتيجة مقيسة فعليًا. يُكتب في السياقين (التطبيق والعام) كي
     * ينتقل التعلّم إلى تطبيق جديد لم يُجرَّب بعد بدل البدء من الصفر.
     */
    fun observe(
        key: String,
        direction: ControlRegistry.Direction,
        appContext: String,
        objectiveGain: Float,
        thermalDeltaC: Float,
    ) {
        listOf(appContext, GLOBAL_CONTEXT).forEach { ctx ->
            val k = compose(key, direction, ctx)
            effects[k] = (effects[k] ?: Effect()).observe(objectiveGain, thermalDeltaC)
        }
        persist()
    }

    /**
     * الحد الأعلى للثقة (UCB): المتوسط + عدم اليقين المرجّح.
     *
     * هذا هو الاستكشاف الموجَّه الذي يستبدل "جرّب كل فعل 8 مرات":
     * المقبض غير المجرَّب يحمل عدم يقين عاليًا فيُرشَّح للتجربة مرة،
     * ثم تضيق حدوده بالقياس. لا عشوائية، ولا تسخين متعمد للجهاز.
     */
    fun optimisticGain(key: String, direction: ControlRegistry.Direction, appContext: String): Float {
        val e = expectedEffect(key, direction, appContext)
        if (e.samples == 0) return EXPLORATION_PRIOR
        return e.meanGain + EXPLORATION_WEIGHT * e.gainStdDev / sqrt(e.samples.toFloat())
    }

    /**
     * هل ثبت أن هذا المقبض ضار حراريًا؟ (يسخّن بلا مكسب يذكر)
     * يُستبعد من الترشيح حتى لو بدا مغريًا على ورق الأداء.
     */
    fun isThermallyHarmful(key: String, direction: ControlRegistry.Direction, appContext: String): Boolean {
        val e = expectedEffect(key, direction, appContext)
        return e.samples >= MIN_SPECIFIC_SAMPLES &&
            e.meanThermal > HARMFUL_THERMAL_C &&
            e.meanGain <= abs(e.meanThermal) * THERMAL_GAIN_RATIO
    }

    fun snapshot(): Map<String, Effect> = effects.toMap()

    /** دمج مرجّح بالثقة: الخاص يقود، والعام يستقر. */
    private fun blend(specific: Effect, global: Effect): Effect {
        val w = specific.confidence
        return Effect(
            samples = specific.samples,
            meanGain = specific.meanGain * w + global.meanGain * (1f - w),
            m2Gain = specific.m2Gain,
            meanThermal = specific.meanThermal * w + global.meanThermal * (1f - w),
        )
    }

    private fun compose(key: String, direction: ControlRegistry.Direction, ctx: String): String =
        "$key|${direction.name}|$ctx"

    private fun persist() {
        runCatching {
            val root = JSONObject()
            effects.forEach { (k, e) ->
                root.put(
                    k,
                    JSONObject()
                        .put("n", e.samples)
                        .put("g", e.meanGain.toDouble())
                        .put("m2", e.m2Gain.toDouble())
                        .put("t", e.meanThermal.toDouble())
                )
            }
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        }
    }

    companion object {
        private const val FILE_NAME = "maxai_control_effects.json"

        /** السياق العام لهذا الجهاز — يُستعار عند تطبيق جديد. */
        const val GLOBAL_CONTEXT = "*"

        /** أقل عينات لاعتماد التاريخ الخاص بالتطبيق وحده. */
        private const val MIN_SPECIFIC_SAMPLES = 3

        /** نصف عمر الثقة: عند هذا العدد تبلغ الثقة 0.5. */
        private const val CONFIDENCE_HALF_LIFE = 5f

        /** انحراف مفترض قبل توفر عينتين — عدم يقين معلن لا صفر كاذب. */
        private const val DEFAULT_PRIOR_STD = 0.15f

        /** جاذبية المقبض غير المجرَّب: يُجرَّب مرة، لا ثماني مرات. */
        private const val EXPLORATION_PRIOR = 0.08f

        /** وزن عدم اليقين في UCB. */
        private const val EXPLORATION_WEIGHT = 1.2f

        /** فوق هذا الارتفاع الحراري المتوسط يُعد المقبض مكلفًا. */
        private const val HARMFUL_THERMAL_C = 1.5f

        /** نسبة المكسب المطلوبة لتبرير كل درجة حرارة. */
        private const val THERMAL_GAIN_RATIO = 0.03f
    }
}
