package nd.max.core.atlas

import org.json.JSONArray
import org.json.JSONObject

/**
 * سجل الأثر — «القبل» و«البعد» كمُعطى محفوظ، لا كقراءتين في لحظة واحدة.
 *
 * الفرق بين هذا السجل وسجل الأدلّة ([AtlasEvidenceStore]) ليس تفصيلًا: سجل الأدلّة يحفظ
 * **حالة واجهة** («هذه العقدة مقروءة الآن»)، وهذا السجل يحفظ **قياس نتيجة** في لحظة معلومة.
 * ومن دون حفظ «قبل» لا توجد مقارنة أصلًا: قراءة الاثنين في اللحظة نفسها تقيس الضجيج لا الأثر.
 *
 * وثلاث قواعد موروثة من نموذج أطلس نفسه، لا مُخترعة هنا:
 *
 * 1. **الفساد يُحذف ولا يُرقَّع.** مدخل غير مقروء أو بمخطط مجهول أو يتجاوز الحدّ ⇒ يُحذف ويُبلَّغ
 *    بسبب، فلا يبقى نصف مدخل يُقرأ لاحقًا كأنه قياس.
 * 2. **لا رقم بلا منشأ.** كل عيّنة تحمل `source` وجيل الإقلاع وجيل الصلاحية، ويرفضها المُنشئ بلا ذلك.
 * 3. **جيل مختلف ليس قياسًا قديمًا.** عيّنة من إقلاع سابق **لا تُحذف** (فهي حقيقة عن ذلك الإقلاع)
 *    لكنها لا تُقارن — والمقارنة نفسها ([AtlasEffectMath]) هي التي ترفضها بـ`BOOT_CHANGED`.
 */
class AtlasEffectStore(
    private val io: AtlasStoreIo,
    private val maxEntryBytes: Int = MAX_ENTRY_BYTES,
    private val maxRecordsPerMetric: Int = MAX_RECORDS_PER_METRIC,
) {

    init {
        require(maxEntryBytes > 0) { "the entry bound must be positive" }
        require(maxRecordsPerMetric > 0) { "the history bound must be positive" }
    }

    /**
     * يضيف عيّنة إلى سجل هدف واحد. يُعيد `false` إن لم يمكن تمثيلها أو كتابتها.
     *
     * والتقليم (`prune`) يقع **قبل** الكتابة: سجل غير محدود النمو يصير في النهاية ملفًا لا يُقرأ،
     * وقراءته الفاشلة ستحذف كل التاريخ — فالحدّ هو ما يحفظ البقية.
     */
    fun record(record: AtlasEffectRecord): Boolean {
        if (!AtlasIds.isValidObservationId(record.target)) return false
        val existing = when (val ledger = load(record.target)) {
            is AtlasEffectLedger.Present -> ledger.records
            is AtlasEffectLedger.Missing -> emptyList()
        }
        val merged = prune(existing + record)
        val text = encode(record.target, merged)
        if (text.length > maxEntryBytes) return false
        return io.write(nameOf(record.target), text)
    }

    fun load(target: String): AtlasEffectLedger {
        if (!AtlasIds.isValidObservationId(target)) return AtlasEffectLedger.Missing(AtlasEffectMiss.ABSENT)
        val name = nameOf(target)
        val text = io.read(name) ?: return AtlasEffectLedger.Missing(AtlasEffectMiss.ABSENT)
        if (text.length > maxEntryBytes) {
            io.delete(name)
            return AtlasEffectLedger.Missing(AtlasEffectMiss.OVERSIZE)
        }
        val decoded = decode(text)
        if (decoded == null) {
            io.delete(name)
            return AtlasEffectLedger.Missing(AtlasEffectMiss.CORRUPT)
        }
        if (decoded.unsupportedSchema) {
            io.delete(name)
            return AtlasEffectLedger.Missing(AtlasEffectMiss.UNSUPPORTED_SCHEMA)
        }
        return AtlasEffectLedger.Present(decoded.records)
    }

    /** أحدث عيّنة لهذا المقياس، اختياريًّا مقيَّدة بحزمة تطبيق (أثر per-app). */
    fun latest(target: String, metric: AtlasEffectMetric, packageName: String? = null): AtlasEffectSample? =
        history(target, metric, packageName).lastOrNull()

    /**
     * أحدث عيّنة **قبل** لحظة معلنة — وهذا هو «قبل» النافذة.
     *
     * وشرط `<= atMs` صارم عن قصد: عيّنة وقعت بعد التدخّل ليست «قبل»، واستعمالها سيقلب الحكم.
     */
    fun latestBefore(
        target: String,
        metric: AtlasEffectMetric,
        atMs: Long,
        packageName: String? = null,
    ): AtlasEffectSample? = history(target, metric, packageName)
        .lastOrNull { it.observedAtElapsedMs <= atMs }

    /** تاريخ مقياس واحد، من الأقدم إلى الأحدث، مقيَّدًا بالحزمة إن مُرّرت. */
    fun history(
        target: String,
        metric: AtlasEffectMetric,
        packageName: String? = null,
        limit: Int = MAX_RECORDS_PER_METRIC,
    ): List<AtlasEffectSample> {
        require(limit > 0) { "the limit must be positive" }
        val records = when (val ledger = load(target)) {
            is AtlasEffectLedger.Present -> ledger.records
            is AtlasEffectLedger.Missing -> return emptyList()
        }
        return records
            .filter { it.sample.metric == metric && (packageName == null || it.packageName == packageName) }
            .sortedBy { it.sample.observedAtElapsedMs }
            .takeLast(limit)
            .map { it.sample }
    }

    /** الأهداف المسجَّلة حاليًّا. */
    fun targets(): List<String> = io.list()
        .filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
        .map { it.removePrefix(NAME_PREFIX).removeSuffix(NAME_SUFFIX) }
        .sorted()

    /** يحذف كل ما يملكه هذا السجل. يُعيد عدد ما حُذف. */
    fun clear(): Int = io.list().count { it.startsWith(NAME_PREFIX) && io.delete(it) }

    /** يحذف سجل هدف واحد — يُستعمل عند «إعادة العدّ» لا عند فشل قياس. */
    fun forget(target: String): Boolean = io.delete(nameOf(target))

    // ---- حدود وتقليم ------------------------------------------------------------------------

    private fun prune(records: List<AtlasEffectRecord>): List<AtlasEffectRecord> {
        val byMetric = records.groupBy { it.sample.metric }
        return byMetric.values.flatMap { group ->
            group.sortedBy { it.sample.observedAtElapsedMs }.takeLast(maxRecordsPerMetric)
        }.sortedBy { it.sample.observedAtElapsedMs }
    }

    private fun nameOf(target: String): String = "$NAME_PREFIX$target$NAME_SUFFIX"

    // ---- تمثيل صريح --------------------------------------------------------------------------

    private fun encode(target: String, records: List<AtlasEffectRecord>): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA, SCHEMA)
        root.put(KEY_TARGET, target)
        val array = JSONArray()
        records.forEach { record ->
            val entry = JSONObject()
            entry.put(KEY_METRIC, record.sample.metric.name)
            entry.put(KEY_VALUE, record.sample.value)
            entry.put(KEY_AT, record.sample.observedAtElapsedMs)
            entry.put(KEY_SOURCE, record.sample.source)
            entry.put(KEY_BOOT, record.sample.bootGeneration)
            entry.put(KEY_PRIVILEGE, record.sample.privilegeGeneration)
            entry.put(KEY_SEMANTIC, record.sample.semanticStatus.name)
            entry.put(KEY_PACKAGE, record.packageName ?: JSONObject.NULL)
            array.put(entry)
        }
        root.put(KEY_RECORDS, array)
        return root.toString()
    }

    private class Decoded(val unsupportedSchema: Boolean, val records: List<AtlasEffectRecord>)

    private fun decode(text: String): Decoded? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        // «مخطط لا نعرفه» ليست «فسادًا»: الأولى يعني أن كاتبًا أحدث كتب هنا، والثانية تعني أن
        // الملف نفسه معطوب. ودمجهما يُفقد السبب الذي به نعرف ماذا نفعل (قاعدة أسباب الفشل
        // المميّزة في `AtlasModels`).
        if (root.optInt(KEY_SCHEMA, -1) != SCHEMA) return Decoded(unsupportedSchema = true, records = emptyList())
        val array = root.optJSONArray(KEY_RECORDS) ?: return null
        val decoded = mutableListOf<AtlasEffectRecord>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: return null
            if (!REQUIRED_KEYS.all { entry.has(it) }) return null
            val record = runCatching {
                val packageName = if (entry.isNull(KEY_PACKAGE)) null else entry.getString(KEY_PACKAGE)
                val target = root.getString(KEY_TARGET)
                AtlasEffectRecord(
                    target = target,
                    packageName = packageName,
                    sample = AtlasEffectSample(
                        metric = AtlasEffectMetric.valueOf(entry.getString(KEY_METRIC)),
                        value = entry.getDouble(KEY_VALUE),
                        observedAtElapsedMs = entry.getLong(KEY_AT),
                        source = entry.getString(KEY_SOURCE),
                        bootGeneration = entry.getLong(KEY_BOOT),
                        privilegeGeneration = entry.getLong(KEY_PRIVILEGE),
                        semanticStatus = AtlasSemanticStatus.valueOf(entry.getString(KEY_SEMANTIC)),
                    ),
                )
            }.getOrNull() ?: return null
            decoded += record
        }
        return Decoded(unsupportedSchema = false, records = decoded)
    }

    companion object {
        const val SCHEMA: Int = 1

        /** سجل هدف واحد: عيّنات قليلة صغيرة، لا تفريغ ذاكرة. */
        const val MAX_ENTRY_BYTES: Int = 16 * 1024

        /** وبحدّ لكل مقياس على حِدة، فلا يبتلع مقياسٌ نشط تاريخَ مقياس نادر. */
        const val MAX_RECORDS_PER_METRIC: Int = 24

        const val NAME_PREFIX: String = "effect."
        const val NAME_SUFFIX: String = ".json"

        private const val KEY_SCHEMA = "schema"
        private const val KEY_TARGET = "target"
        private const val KEY_RECORDS = "records"
        private const val KEY_METRIC = "metric"
        private const val KEY_VALUE = "value"
        private const val KEY_AT = "at"
        private const val KEY_SOURCE = "source"
        private const val KEY_BOOT = "boot"
        private const val KEY_PRIVILEGE = "privilege"
        private const val KEY_SEMANTIC = "semantic"
        private const val KEY_PACKAGE = "package"

        private val REQUIRED_KEYS: List<String> = listOf(
            KEY_METRIC,
            KEY_VALUE,
            KEY_AT,
            KEY_SOURCE,
            KEY_BOOT,
            KEY_PRIVILEGE,
            KEY_SEMANTIC,
            KEY_PACKAGE,
        )
    }
}

/** لماذا لم يُقرأ سجل؟ وكل واحدة حقيقة مختلفة — ولا واحدة منها «صفر قياس». */
enum class AtlasEffectMiss {
    /** لا سجل لهذا الهدف. */
    ABSENT,

    /** سجل موجود ومعطوب: يُحذف ويُبلَّغ، ولا يُرقَّع. */
    CORRUPT,

    /** أكبر من الحدّ: يُحذف ولا يُقتطع إلى «قيمة معقولة». */
    OVERSIZE,

    /** كتبه مخطط أحدث من هذا البناء: يُحذف بسببه لا بوصفه فسادًا. */
    UNSUPPORTED_SCHEMA,
}

sealed interface AtlasEffectLedger {
    data class Present(val records: List<AtlasEffectRecord>) : AtlasEffectLedger
    data class Missing(val reason: AtlasEffectMiss) : AtlasEffectLedger
}

/** عيّنة أثر منسوبة إلى هدف (وربما إلى حزمة، في قياس per-app). */
data class AtlasEffectRecord(
    val target: String,
    val sample: AtlasEffectSample,
    val packageName: String? = null,
) {
    init {
        require(AtlasIds.isValidObservationId(target)) { "effect target is not canonical: $target" }
        require(packageName == null || PACKAGE_NAME.matches(packageName)) {
            "packageName must be an Android package identifier"
        }
    }

    companion object {
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }
}
