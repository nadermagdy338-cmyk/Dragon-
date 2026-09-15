package nd.max.core.maxai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * دفتر القرارات — الذاكرة السردية لـMax AI.
 *
 * المشكلة التي يحلّه: المحرك كان يتعلّم فعلًا (ControlOutcomeModel +
 * ResponseModel) وينفّذ فعلًا (HardwareControlArbiter)، لكن كل ما وصل
 * المستخدم هو أربعة عدادات و"آخر إجراء" واحد يُستبدل في الدورة
 * التالية. أي أن سلسلة السبب/النتيجة — أهم ما يجعل النظام مفهومًا —
 * كانت تُهدر بعد ثلاثين ثانية.
 *
 * هنا تُحفظ كل دورة قرار كحلقة كاملة موثّقة بالقياس:
 *   لاحظ → لماذا يهم → قرر → ماذا غيّر → ماذا حدث → هل نجح →
 *   ما الأثر → ماذا تعلّم.
 *
 * القاعدة الملزِمة: لا حقل في هذا الملف يمكن توليده من العدم. كل رقم
 * إما قراءة من العتاد، أو ناتج نموذج تعلّم حقيقي، أو خرج مُحكِّم.
 * الحقول التي لا يوجد لها قياس تبقى null وتُعرض كذلك.
 */

/** لقطة قابلة للعرض من قياسات دورة واحدة. */
data class MaxAiReading(
    val cpuLoadPercent: Int,
    val thermalC: Float,
    val batteryPercent: Int,
    val memoryPercent: Int,
    val networkPercent: Int,
    val screenOn: Boolean,
    /** درجة الحالة تحت الهدف النشط وقت القياس (0..1). */
    val objectiveScore: Float,
)

/** عيّنة في شريط التطور الزمني — من دورات المحرك الحقيقية فقط. */
data class MaxAiSample(
    val timestampMs: Long,
    val cpuLoadPercent: Int,
    val thermalC: Float,
    val batteryPercent: Int,
    val memoryPercent: Int,
    val objectiveScore: Float,
)

/** سبب استبعاد مرشّح — قيم ثابتة كي تترجمها الواجهة بلا تخمين. */
object MaxAiRejection {
    const val UNREADABLE = "unreadable"
    const val NO_STEP = "no_step"
    const val MEASURED_HARM = "measured_harm"
    const val PREDICTED_HARM = "predicted_harm"
}

/** مرشّح واحد كما رآه المخطِّط في هذه الدورة بالضبط. */
data class MaxAiCandidate(
    val key: String,
    val label: String,
    val from: String?,
    val to: String?,
    val utility: Float,
    val credibility: Float,
    val predictedGain: Float?,
    val predictedThermalC: Float?,
    val predictionConfidence: Float?,
    val samples: Int,
    /** null = مؤهل للترشيح؛ غير ذلك أحد ثوابت [MaxAiRejection]. */
    val rejection: String?,
    val chosen: Boolean,
)

/** الحكم النهائي على الحلقة — مشتق من قياس، لا من نيّة. */
enum class MaxAiVerdict {
    /** تحسّن مقيس بعد التنفيذ. */
    IMPROVED,

    /** تراجع مقيس فاسترجع المحرك خط الأساس وأثبت الاسترجاع. */
    REGRESSED_ROLLED_BACK,

    /** تراجع مقيس وتعذّر إثبات الاسترجاع. */
    REGRESSED_STUCK,

    /** حجبته أسبقية السلامة قبل الكتابة أو بعدها. */
    BLOCKED_SAFETY,

    /** لم تثبت الكتابة على العتاد. */
    WRITE_FAILED,

    /** ثبتت الكتابة وتعذّر قياس الأثر. */
    UNMEASURED,

    /** فجوة مقيسة لكن كل المرشحين مستبعدون — مراقبة واعية لا خمول. */
    NO_ACTION,
}

/** حلقة قرار واحدة كاملة. */
data class MaxAiEpisode(
    val id: Long,
    val appContext: String,
    val objectiveLabel: String,
    /** "user" تفضيل صريح / "learned" استنتاج سلوكي / "screen_off". */
    val objectiveSource: String,
    val weightPerformance: Float,
    val weightBattery: Float,
    val weightThermal: Float,
    val satisfactionTarget: Float,
    val gap: Float,
    val before: MaxAiReading,
    val after: MaxAiReading?,
    val knobKey: String?,
    val knobLabel: String?,
    val direction: String?,
    val fromValue: String?,
    val toValue: String?,
    val appliedValue: String?,
    val stepFraction: Float,
    val predictedGain: Float?,
    val predictedThermalC: Float?,
    val predictionConfidence: Float?,
    val candidates: List<MaxAiCandidate>,
    val verdict: MaxAiVerdict,
    /** حقيقة الآلة: خرج المُحكِّم/السلامة كما هو. */
    val detail: String,
    val objectiveDelta: Float?,
    val thermalDeltaC: Float?,
    val cpuDeltaPercent: Int?,
    val batteryDeltaPercent: Int?,
    val samplesBefore: Int,
    val samplesAfter: Int,
    val confidenceBefore: Float,
    val confidenceAfter: Float,
    /** |تنبؤ − مقيس| حين وُجد تنبؤ — صدق النموذج معروضًا لا مدّعى. */
    val predictionErrorGain: Float?,
    val safetyLevel: String,
) {
    val acted: Boolean get() = knobKey != null && verdict != MaxAiVerdict.NO_ACTION
}

@Singleton
class MaxAiJournal @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val _episodes = MutableStateFlow<List<MaxAiEpisode>>(emptyList())
    val episodes: StateFlow<List<MaxAiEpisode>> = _episodes.asStateFlow()
    private val lock = Any()

    init {
        synchronized(lock) {
            runCatching {
                if (file.exists()) {
                    val array = JSONArray(file.readText())
                    val loaded = ArrayList<MaxAiEpisode>(array.length())
                    for (i in 0 until array.length()) {
                        array.optJSONObject(i)?.let(::parseEpisode)?.let(loaded::add)
                    }
                    _episodes.value = loaded
                }
            }
        }
    }

    /** يسجّل حلقة جديدة في المقدمة ويقصّ الدفتر عند الحد. */
    fun record(episode: MaxAiEpisode) {
        synchronized(lock) {
            _episodes.value = (listOf(episode) + _episodes.value).take(MAX_EPISODES)
            persist()
        }
    }

    fun clear() {
        synchronized(lock) {
            _episodes.value = emptyList()
            runCatching { file.delete() }
        }
    }

    private fun persist() {
        runCatching {
            val array = JSONArray()
            _episodes.value.forEach { array.put(serializeEpisode(it)) }
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(array.toString())
                tmp.delete()
            }
        }
    }

    // ── تسلسل صريح: حقل بحقل، بلا انعكاس ولا تبعية مكتبة ────────────

    private fun serializeReading(reading: MaxAiReading): JSONObject = JSONObject()
        .put("cpu", reading.cpuLoadPercent)
        .put("temp", reading.thermalC.toDouble())
        .put("bat", reading.batteryPercent)
        .put("mem", reading.memoryPercent)
        .put("net", reading.networkPercent)
        .put("screen", reading.screenOn)
        .put("score", reading.objectiveScore.toDouble())

    private fun parseReading(o: JSONObject): MaxAiReading = MaxAiReading(
        cpuLoadPercent = o.optInt("cpu", 0),
        thermalC = o.optDouble("temp", 0.0).toFloat(),
        batteryPercent = o.optInt("bat", 0),
        memoryPercent = o.optInt("mem", 0),
        networkPercent = o.optInt("net", 0),
        screenOn = o.optBoolean("screen", false),
        objectiveScore = o.optDouble("score", 0.0).toFloat(),
    )

    private fun serializeCandidate(candidate: MaxAiCandidate): JSONObject = JSONObject()
        .put("key", candidate.key)
        .put("label", candidate.label)
        .putOrNull("from", candidate.from)
        .putOrNull("to", candidate.to)
        .put("utility", candidate.utility.toDouble())
        .put("cred", candidate.credibility.toDouble())
        .putFloatOrNull("pg", candidate.predictedGain)
        .putFloatOrNull("pt", candidate.predictedThermalC)
        .putFloatOrNull("pc", candidate.predictionConfidence)
        .put("n", candidate.samples)
        .putOrNull("rej", candidate.rejection)
        .put("chosen", candidate.chosen)

    private fun parseCandidate(o: JSONObject): MaxAiCandidate = MaxAiCandidate(
        key = o.optString("key"),
        label = o.optString("label"),
        from = o.optStringOrNull("from"),
        to = o.optStringOrNull("to"),
        utility = o.optDouble("utility", 0.0).toFloat(),
        credibility = o.optDouble("cred", 0.0).toFloat(),
        predictedGain = o.optFloatOrNull("pg"),
        predictedThermalC = o.optFloatOrNull("pt"),
        predictionConfidence = o.optFloatOrNull("pc"),
        samples = o.optInt("n", 0),
        rejection = o.optStringOrNull("rej"),
        chosen = o.optBoolean("chosen", false),
    )

    private fun serializeEpisode(episode: MaxAiEpisode): JSONObject {
        val candidates = JSONArray()
        episode.candidates.forEach { candidates.put(serializeCandidate(it)) }
        return JSONObject()
            .put("id", episode.id)
            .put("ctx", episode.appContext)
            .put("obj", episode.objectiveLabel)
            .put("objSrc", episode.objectiveSource)
            .put("wp", episode.weightPerformance.toDouble())
            .put("wb", episode.weightBattery.toDouble())
            .put("wt", episode.weightThermal.toDouble())
            .put("target", episode.satisfactionTarget.toDouble())
            .put("gap", episode.gap.toDouble())
            .put("before", serializeReading(episode.before))
            .apply { episode.after?.let { put("after", serializeReading(it)) } }
            .putOrNull("knob", episode.knobKey)
            .putOrNull("knobLabel", episode.knobLabel)
            .putOrNull("dir", episode.direction)
            .putOrNull("from", episode.fromValue)
            .putOrNull("to", episode.toValue)
            .putOrNull("applied", episode.appliedValue)
            .put("step", episode.stepFraction.toDouble())
            .putFloatOrNull("pg", episode.predictedGain)
            .putFloatOrNull("pt", episode.predictedThermalC)
            .putFloatOrNull("pc", episode.predictionConfidence)
            .put("cands", candidates)
            .put("verdict", episode.verdict.name)
            .put("detail", episode.detail)
            .putFloatOrNull("dScore", episode.objectiveDelta)
            .putFloatOrNull("dTemp", episode.thermalDeltaC)
            .putIntOrNull("dCpu", episode.cpuDeltaPercent)
            .putIntOrNull("dBat", episode.batteryDeltaPercent)
            .put("nBefore", episode.samplesBefore)
            .put("nAfter", episode.samplesAfter)
            .put("cBefore", episode.confidenceBefore.toDouble())
            .put("cAfter", episode.confidenceAfter.toDouble())
            .putFloatOrNull("pErr", episode.predictionErrorGain)
            .put("safety", episode.safetyLevel)
    }

    private fun parseEpisode(o: JSONObject): MaxAiEpisode? = runCatching {
        val candidatesJson = o.optJSONArray("cands")
        val candidates = buildList {
            if (candidatesJson != null) {
                for (i in 0 until candidatesJson.length()) {
                    candidatesJson.optJSONObject(i)?.let { add(parseCandidate(it)) }
                }
            }
        }
        MaxAiEpisode(
            id = o.optLong("id", 0L),
            appContext = o.optString("ctx", "system"),
            objectiveLabel = o.optString("obj", "balanced"),
            objectiveSource = o.optString("objSrc", "learned"),
            weightPerformance = o.optDouble("wp", 0.0).toFloat(),
            weightBattery = o.optDouble("wb", 0.0).toFloat(),
            weightThermal = o.optDouble("wt", 0.0).toFloat(),
            satisfactionTarget = o.optDouble("target", 0.0).toFloat(),
            gap = o.optDouble("gap", 0.0).toFloat(),
            before = o.optJSONObject("before")?.let(::parseReading)
                ?: return@runCatching null,
            after = o.optJSONObject("after")?.let(::parseReading),
            knobKey = o.optStringOrNull("knob"),
            knobLabel = o.optStringOrNull("knobLabel"),
            direction = o.optStringOrNull("dir"),
            fromValue = o.optStringOrNull("from"),
            toValue = o.optStringOrNull("to"),
            appliedValue = o.optStringOrNull("applied"),
            stepFraction = o.optDouble("step", 0.0).toFloat(),
            predictedGain = o.optFloatOrNull("pg"),
            predictedThermalC = o.optFloatOrNull("pt"),
            predictionConfidence = o.optFloatOrNull("pc"),
            candidates = candidates,
            verdict = MaxAiVerdict.values()
                .firstOrNull { it.name == o.optString("verdict") }
                ?: MaxAiVerdict.UNMEASURED,
            detail = o.optString("detail", ""),
            objectiveDelta = o.optFloatOrNull("dScore"),
            thermalDeltaC = o.optFloatOrNull("dTemp"),
            cpuDeltaPercent = o.optIntOrNull("dCpu"),
            batteryDeltaPercent = o.optIntOrNull("dBat"),
            samplesBefore = o.optInt("nBefore", 0),
            samplesAfter = o.optInt("nAfter", 0),
            confidenceBefore = o.optDouble("cBefore", 0.0).toFloat(),
            confidenceAfter = o.optDouble("cAfter", 0.0).toFloat(),
            predictionErrorGain = o.optFloatOrNull("pErr"),
            safetyLevel = o.optString("safety", SafetyLevel.NORMAL.name),
        )
    }.getOrNull()

    private fun JSONObject.putOrNull(key: String, value: String?): JSONObject =
        if (value == null) this else put(key, value)

    private fun JSONObject.putFloatOrNull(key: String, value: Float?): JSONObject =
        if (value == null) this else put(key, value.toDouble())

    private fun JSONObject.putIntOrNull(key: String, value: Int?): JSONObject =
        if (value == null) this else put(key, value)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optFloatOrNull(key: String): Float? =
        if (has(key) && !isNull(key)) optDouble(key, Double.NaN)
            .takeIf { !it.isNaN() }?.toFloat() else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    companion object {
        private const val FILE_NAME = "maxai_journal.json"

        /**
         * سقف الدفتر. الغرض سرد مفهوم لا أرشيف: ~80 حلقة تغطي ساعات من
         * دورات الثلاثين ثانية، وتبقى قابلة للقراءة والتحليل بلا كلفة.
         */
        const val MAX_EPISODES = 80
    }
}
