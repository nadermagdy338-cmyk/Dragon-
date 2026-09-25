/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import org.json.JSONArray
import org.json.JSONObject

/**
 * ترميز الدفتر وفكّه — دالّات نقية بلا Android وبلا ملفات.
 *
 * سبب فصلها عن [MaxAiJournal]: كل حقل جديد في الحلقة كان يُفقد صامتًا
 * إن نُسي هنا، ولا شيء يكشف ذلك. بعد الفصل صار الترميز سطحًا مستقلًا
 * يحرسه اختبار ذهاب/عودة على JVM (كتابة → قراءة → تطابق)، فأي حقل
 * ينساه أحدٌ لاحقًا يسقط الاختبار لا سرد المستخدم.
 *
 * القاعدة نفسها سارية: ما لا يوجد له قياس يُكتب null ويُقرأ null.
 */
internal object MaxAiJournalCodec {

    // ── قراءات ودورات ────────────────────────────────────

    fun encodeReading(reading: MaxAiReading): JSONObject = JSONObject()
        .put("cpu", reading.cpuLoadPercent)
        .put("temp", reading.thermalC.toDouble())
        .put("bat", reading.batteryPercent)
        .put("mem", reading.memoryPercent)
        .put("net", reading.networkPercent)
        .put("screen", reading.screenOn)
        .put("score", reading.objectiveScore.toDouble())

    fun decodeReading(o: JSONObject): MaxAiReading = MaxAiReading(
        cpuLoadPercent = o.optInt("cpu", 0),
        thermalC = o.optDouble("temp", 0.0).toFloat(),
        batteryPercent = o.optInt("bat", 0),
        memoryPercent = o.optInt("mem", 0),
        networkPercent = o.optInt("net", 0),
        screenOn = o.optBoolean("screen", false),
        objectiveScore = o.optDouble("score", 0.0).toFloat(),
    )

    fun encodeCandidate(candidate: MaxAiCandidate): JSONObject = JSONObject()
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

    fun decodeCandidate(o: JSONObject): MaxAiCandidate = MaxAiCandidate(
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

    // ── حلقة كاملة ───────────────────────────────────────

    fun encodeEpisode(episode: MaxAiEpisode): JSONObject {
        val candidates = JSONArray()
        episode.candidates.forEach { candidates.put(encodeCandidate(it)) }
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
            .put("before", encodeReading(episode.before))
            .apply { episode.after?.let { put("after", encodeReading(it)) } }
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
            .put("probe", episode.exploration)
            .put("reverted", episode.reverted)
            .putOrNull("kBefore", episode.knowledgeBefore)
            .putOrNull("kAfter", episode.knowledgeAfter)
            .putFloatOrNull("eBefore", episode.epistemicBefore)
            .putFloatOrNull("eAfter", episode.epistemicAfter)
            .putFloatOrNull("ig", episode.informationGain)
            .putFloatOrNull("pCost", episode.probeCost)
            .putOrNull("pBlock", episode.probeBlockReason)
            // نوع الحلقة: قرار، تجربة معرفية، تدخل سلامة، أو انحراف.
            .put("kind", episode.kind.name)
            // رد فعل المستخدم (المرحلة التاسعة).
            .putLongOrNull("ovAt", episode.userOverrideAtMs)
            .putOrNull("ovKind", episode.userOverrideKind)
            // الحكم المؤجل: ما لا تقيسه نافذة الثوانٍ العشر.
            .putLongOrNull("dfAt", episode.deferredAtMs)
            .putIntOrNull("dfBat", episode.deferredBatteryDeltaPercent)
            .putFloatOrNull("dfTemp", episode.deferredThermalDeltaC)
            .putFloatOrNull("dfScore", episode.deferredObjectiveDelta)
    }

    fun decodeEpisode(o: JSONObject): MaxAiEpisode? = runCatching {
        val candidatesJson = o.optJSONArray("cands")
        val candidates = buildList {
            if (candidatesJson != null) {
                for (i in 0 until candidatesJson.length()) {
                    candidatesJson.optJSONObject(i)?.let { add(decodeCandidate(it)) }
                }
            }
        }
        val exploration = o.optBoolean("probe", false)
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
            before = o.optJSONObject("before")?.let(::decodeReading)
                ?: return@runCatching null,
            after = o.optJSONObject("after")?.let(::decodeReading),
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
            exploration = exploration,
            reverted = o.optBoolean("reverted", false),
            knowledgeBefore = o.optStringOrNull("kBefore"),
            knowledgeAfter = o.optStringOrNull("kAfter"),
            epistemicBefore = o.optFloatOrNull("eBefore"),
            epistemicAfter = o.optFloatOrNull("eAfter"),
            informationGain = o.optFloatOrNull("ig"),
            probeCost = o.optFloatOrNull("pCost"),
            probeBlockReason = o.optStringOrNull("pBlock"),
            // دفاتر قديمة لا تحمل "kind": التجربة تُعرف من علم probe،
            // وما عداها قرار — لا اختراع نوع لم يُسجَّل.
            kind = MaxAiEpisodeKind.values()
                .firstOrNull { it.name == o.optString("kind") }
                ?: if (exploration) MaxAiEpisodeKind.PROBE else MaxAiEpisodeKind.DECISION,
            userOverrideAtMs = o.optLongOrNull("ovAt"),
            userOverrideKind = o.optStringOrNull("ovKind"),
            deferredAtMs = o.optLongOrNull("dfAt"),
            deferredBatteryDeltaPercent = o.optIntOrNull("dfBat"),
            deferredThermalDeltaC = o.optFloatOrNull("dfTemp"),
            deferredObjectiveDelta = o.optFloatOrNull("dfScore"),
        )
    }.getOrNull()

    /** الدفتر كاملًا كنص JSON — نفس ترتيب القائمة (الأحدث أولًا). */
    fun encodeAll(episodes: List<MaxAiEpisode>): String {
        val array = JSONArray()
        episodes.forEach { array.put(encodeEpisode(it)) }
        return array.toString()
    }

    /** يفكّ ما يمكن فكّه ويُسقط الحلقات التالفة بلا إسقاط الملف كله. */
    fun decodeAll(text: String): List<MaxAiEpisode> = runCatching {
        val array = JSONArray(text)
        val loaded = ArrayList<MaxAiEpisode>(array.length())
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let(::decodeEpisode)?.let(loaded::add)
        }
        loaded as List<MaxAiEpisode>
    }.getOrDefault(emptyList())

    // ── مساعدات: null تعني "لا قياس"، فلا تُكتب أصلًا ──────

    private fun JSONObject.putOrNull(key: String, value: String?): JSONObject =
        if (value == null) this else put(key, value)

    private fun JSONObject.putFloatOrNull(key: String, value: Float?): JSONObject =
        if (value == null) this else put(key, value.toDouble())

    private fun JSONObject.putIntOrNull(key: String, value: Int?): JSONObject =
        if (value == null) this else put(key, value)

    private fun JSONObject.putLongOrNull(key: String, value: Long?): JSONObject =
        if (value == null) this else put(key, value)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optFloatOrNull(key: String): Float? =
        if (has(key) && !isNull(key)) optDouble(key, Double.NaN)
            .takeIf { !it.isNaN() }?.toFloat() else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null
}
