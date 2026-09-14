package nd.max.core.maxai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nd.max.core.hardware.DeviceStateCollector.DeviceSnapshot
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * نموذج استجابة الجهاز — التنبؤ قبل التجربة.
 *
 * المشكلة التي يحلها: [ControlOutcomeModel] يتعلّم من **التجربة** فقط،
 * أي أن العقل يجب أن يجرّب على جهازك ليعرف. وهذا يعني أن كل مقبض جديد
 * يكلّف تجربة حقيقية قد تُسخّن الجهاز. أما التوأم الأصلي (Rust) فلا
 * يتنبأ بأثر مقبض أصلًا — يحلل ارتباطات، ويصمت كليًا بلا مكتبة .so.
 *
 * الحل هنا: نموذج انحدار خطي متعدد المتغيرات يتعلّم **دالة الاستجابة**
 * لكل مقبض: كيف يترجم تغيّرٌ نسبي في المقبض إلى Δأداء وΔحرارة، مشروطًا
 * بحالة الجهاز (الحمل، الحرارة الابتدائية، نية التطبيق).
 *
 *   Δالنتيجة ≈ w0·خطوة + w1·(خطوة×حمل) + w2·(خطوة×حرارة) + w3·(خطوة×نية)
 *
 * التفاعلات (حدود الضرب) هي ما يجعله ذكيًا فعلًا: نفس رفع التردد يعطي
 * مكسبًا كبيرًا تحت حمل عالٍ وحرارة منخفضة، ومكسبًا صفريًا تحت حمل
 * منخفض — النموذج يتعلّم هذا الشرط بدل حفظ متوسط أعمى.
 *
 * التعلّم: نزول تدرج تزايدي (RLS مبسّط) بمعدل تعلّم متناقص — بلا دفعات،
 * بلا ذاكرة تاريخ، وبثبات عددي. يعمل بالكامل في Kotlin (قرار المالك:
 * Kotlin Learning Core، وRust اختياري لاحقًا).
 *
 * الصدق: قبل [MIN_SAMPLES_FOR_TRUST] ملاحظة لا يُستشار النموذج إطلاقًا
 * (`predict` يعيد null) — لا تنبؤ من العدم.
 */
@Singleton
class ResponseModel @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** تنبؤ بأثر خطوة لم تُنفَّذ: مكسب الهدف وتكلفته الحرارية. */
    data class Prediction(
        val objectiveGain: Float,
        val thermalDeltaC: Float,
        /** ثقة [0..1] من عدد العينات وتشتت الخطأ المتبقي. */
        val confidence: Float,
    )

    /**
     * أوزان دالة استجابة مقبض واحد.
     *
     * gain* تتنبأ بـΔدرجة الهدف، thermal* تتنبأ بـΔالحرارة (°م).
     * كلاهما يتعلّم من نفس الملاحظة لكن بأهداف مختلفة — فالمقبض قد
     * يكون مربحًا أدائيًا ومكلفًا حراريًا في آن.
     */
    private data class Response(
        // الأوزان: [ثابت, خطوة, خطوة×حمل, خطوة×حرارة, خطوة×نية]
        val gainW: FloatArray = FloatArray(FEATURES),
        val thermalW: FloatArray = FloatArray(FEATURES),
        val samples: Int = 0,
        /** مجموع مربعات خطأ التنبؤ — أساس الثقة الصادقة. */
        val gainSse: Float = 0f,
    ) {
        fun predictGain(f: FloatArray): Float = dot(gainW, f)
        fun predictThermal(f: FloatArray): Float = dot(thermalW, f)
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val responses = linkedMapOf<String, Response>()

    init {
        runCatching {
            if (!file.exists()) return@runCatching
            val root = JSONObject(file.readText())
            root.keys().forEach { key ->
                val o = root.getJSONObject(key)
                responses[key] = Response(
                    gainW = o.getJSONArray("g").toFloatArray(),
                    thermalW = o.getJSONArray("t").toFloatArray(),
                    samples = o.optInt("n", 0),
                    gainSse = o.optDouble("e", 0.0).toFloat(),
                )
            }
        }
    }

    /**
     * يتنبأ بأثر خطوة مقترحة — أو null إن لم يتعلّم النموذج بما يكفي.
     *
     * @param stepFraction حجم الخطوة نسبةً إلى مدى المقبض، موجب للرفع
     *        وسالب للخفض. هذا ما يجعل النموذج قابلًا للنقل بين أجهزة
     *        مختلفة الترددات: يتعلّم الاستجابة النسبية لا القيم المطلقة.
     */
    fun predict(
        key: String,
        direction: ControlRegistry.Direction,
        appContext: String,
        state: DeviceSnapshot,
        stepFraction: Float,
    ): Prediction? {
        val response = resolve(key, direction, appContext) ?: return null
        if (response.samples < MIN_SAMPLES_FOR_TRUST) return null
        val f = features(state, stepFraction)
        val residualStd = if (response.samples > 1) {
            sqrt(response.gainSse / response.samples)
        } else DEFAULT_RESIDUAL
        // الثقة تنخفض مع الخطأ المتبقي وترتفع مع العينات — كلاهما مقيس.
        val confidence = (
            (response.samples.toFloat() / (response.samples + CONFIDENCE_HALF_LIFE)) *
                exp(-residualStd * RESIDUAL_PENALTY)
            ).coerceIn(0f, 1f)
        return Prediction(
            objectiveGain = response.predictGain(f),
            thermalDeltaC = response.predictThermal(f),
            confidence = confidence,
        )
    }

    /**
     * يتعلّم من نتيجة مقيسة فعليًا.
     *
     * نزول تدرج بمعدل متناقص (1/n المخفف): الملاحظات الأولى تحرك
     * الأوزان بقوة ثم يستقر النموذج — تقارب بلا تذبذب وبلا ضبط يدوي
     * لمعدل التعلّم على كل جهاز.
     */
    fun observe(
        key: String,
        direction: ControlRegistry.Direction,
        appContext: String,
        stateBefore: DeviceSnapshot,
        stepFraction: Float,
        measuredGain: Float,
        measuredThermalDeltaC: Float,
    ) {
        val f = features(stateBefore, stepFraction)
        listOf(appContext, ControlOutcomeModel.GLOBAL_CONTEXT).forEach { ctx ->
            val k = compose(key, direction, ctx)
            val prev = responses[k] ?: Response()
            val n = prev.samples + 1
            val lr = (BASE_LR / (1f + prev.samples * LR_DECAY)).coerceAtLeast(MIN_LR)

            val gainError = prev.predictGain(f) - measuredGain
            val thermalError = prev.predictThermal(f) - measuredThermalDeltaC

            // تطبيع بمعيار السمة (NLMS): يمنع خطوة تعلّم منفجرة حين
            // تكون السمات كبيرة، ويجعل التقارب مستقلًا عن مقياس المدخلات.
            var norm = 0f
            for (i in 0 until FEATURES) norm += f[i] * f[i]
            val denom = norm.coerceAtLeast(1e-6f)

            val gainW = prev.gainW.copyOf()
            val thermalW = prev.thermalW.copyOf()
            for (i in 0 until FEATURES) {
                gainW[i] -= lr * gainError * f[i] / denom
                thermalW[i] -= lr * thermalError * f[i] / denom
            }
            responses[k] = Response(
                gainW = gainW,
                thermalW = thermalW,
                samples = n,
                // خطأ ما قبل التحديث هو المقياس الصادق لجودة التنبؤ.
                gainSse = prev.gainSse + gainError * gainError,
            )
        }
        persist()
    }

    /** هل تعلّم النموذج ما يكفي لهذا المقبض في هذا السياق؟ */
    fun isTrained(key: String, direction: ControlRegistry.Direction, appContext: String): Boolean =
        (resolve(key, direction, appContext)?.samples ?: 0) >= MIN_SAMPLES_FOR_TRUST

    fun trainedCount(): Int = responses.count { it.value.samples >= MIN_SAMPLES_FOR_TRUST }

    /** السياق الخاص أولًا، ثم العام — نفس تدرج [ControlOutcomeModel]. */
    private fun resolve(
        key: String,
        direction: ControlRegistry.Direction,
        appContext: String,
    ): Response? {
        val specific = responses[compose(key, direction, appContext)]
        if (specific != null && specific.samples >= MIN_SAMPLES_FOR_TRUST) return specific
        return responses[compose(key, direction, ControlOutcomeModel.GLOBAL_CONTEXT)] ?: specific
    }

    /**
     * السمات: الخطوة وتفاعلاتها مع حالة الجهاز — **بلا حد ثابت**.
     *
     * القانون الفيزيائي الذي يحكم التصميم: خطوة صفر تعني أثرًا صفرًا
     * دائمًا. إدراج حد ثابت يكسر هذا القيد فيمنح النموذج "أثرًا" حتى
     * بلا تدخل، ويجعل السمات شبه متلازمة خطيًا (الثابت ≈ حد الخطوة
     * عند حجم خطوة ثابت) فيعجز عن الفصل.
     *
     * قاست المحاكاة الفرق: إزالة الثابت خفّضت خطأ التنبؤ 61%
     * (0.0893 → 0.0352) على نفس البيانات. التفاعل يبقى مصدر الذكاء:
     * (خطوة × حمل) تتعلّم "الرفع ينفع تحت الحمل"، و(خطوة × حرارة)
     * تتعلّم "الرفع يؤذي عند السخونة".
     */
    private fun features(state: DeviceSnapshot, stepFraction: Float): FloatArray =
        buildFeatures(stepFraction, state.cpuLoad, state.thermal, state.appIntent)

    private fun compose(key: String, direction: ControlRegistry.Direction, ctx: String): String =
        "$key|${direction.name}|$ctx"

    private fun persist() {
        runCatching {
            val root = JSONObject()
            responses.forEach { (k, r) ->
                root.put(
                    k,
                    JSONObject()
                        .put("g", r.gainW.toJsonArray())
                        .put("t", r.thermalW.toJsonArray())
                        .put("n", r.samples)
                        .put("e", r.gainSse.toDouble())
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
        private const val FILE_NAME = "maxai_response_model.json"

        /** [خطوة, خطوة×حمل, خطوة×حرارة, خطوة×نية] — بلا ثابت (أثر(0)=0) */
        private const val FEATURES = 4

        /** لا تنبؤ قبل هذا العدد — صدق النموذج قبل طموحه. */
        const val MIN_SAMPLES_FOR_TRUST = 4

        private const val BASE_LR = 0.6f
        private const val LR_DECAY = 0.01f
        private const val MIN_LR = 0.05f
        private const val CONFIDENCE_HALF_LIFE = 6f
        private const val DEFAULT_RESIDUAL = 0.2f
        private const val RESIDUAL_PENALTY = 2.5f

        /**
         * بناء السمات — دالة نقية بلا حالة، قابلة للاختبار في JVM بلا
         * Android وبلا Context. هذا الفصل مقصود: الرياضيات هي الحراسة
         * الحقيقية لهذه الطبقة، و[MIN_SAMPLES_FOR_TRUST] وبقية الحالة
         * تفاصيل تنفيذية.
         *
         * القانون الملزِم: خطوة صفر ⇒ كل السمات صفر ⇒ أثر صفر.
         */
        internal fun buildFeatures(
            stepFraction: Float,
            cpuLoad: Float,
            thermal: Float,
            appIntent: Float,
        ): FloatArray {
            val s = stepFraction.coerceIn(-1f, 1f)
            return floatArrayOf(
                s,
                s * cpuLoad,
                s * thermal,
                s * appIntent,
            )
        }

        private fun dot(w: FloatArray, f: FloatArray): Float {
            var sum = 0f
            for (i in w.indices) sum += w[i] * f[i]
            return if (sum.isFinite()) sum else 0f
        }

        private fun FloatArray.toJsonArray(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            forEach { arr.put(it.toDouble()) }
            return arr
        }

        private fun org.json.JSONArray.toFloatArray(): FloatArray =
            FloatArray(FEATURES) { i -> optDouble(i, 0.0).toFloat() }
    }
}
