package nd.max.core.diagnostics

/**
 * التقرير التشخيصي — ما يُشارَك بدل تصوير الشاشة، وحزمة كاملة حين يُرسَل الملف وحده.
 *
 * لماذا وُجد
 * ----------
 * المستودع يملك تقريرًا آخر: `AtlasSupportReport` (مسح قدرات الجهاز، JSON مُقنَّم) وسكربت
 * `dumpDiagnosticLogs` الذي يجمع أرشيفًا كاملًا. وكلاهما **لا يصلح لهذا الموضع**: المسح لا يعرف
 * شيئًا عن تشغيل التطبيق الآن، والأرشيف الكامل ثقيل ولا يُقرأ بعين واحدة.
 *
 * والمطلوب شرطان:
 *
 * 1. **الفشل أولًا، مرتّبًا بالتكرار.** فشل واحد عارض ليس كفشل يتكرّر ستّين مرّة، والترتيب
 *    الزمني يخلط الاثنين.
 * 2. **الملف يشرح نفسه.** من يُرسل السجل لا يرسل معه جهازه ولا إعداده ولا معنى رموزه؛ فإن لم
 *    يكن ذلك في الملف صار التشخيص سؤالًا وجوابًا. ولذلك يدخل التقرير: هوية الجهاز، وإعداد
 *    التشغيل، ودليل قراءة السطر والوحدات، وقاموس كل رمز ([LogCodeGlossary])، ثم الفشل.
 *
 * وثلاثة قرارات أخرى:
 *
 * - **السبب يُنقل كما هو:** `reason=apply-not-verified-baseline-restored` رمز ثابت وُجد في
 *   المحرّك من أجله، فلا يُترجم ولا يُعاد صياغته ولا يُقصّ. ويُتبع بمقطع `-- what to do --`
 *   يحمل إصلاح **كل عطل وقع في هذا الملف** من [LogCodeGlossary.remedies] — فالحزمة تُحلّ من
 *   نفسها لا من معرفة سابقة؛ وما لا إصلاح مُرمَّز له يُعلَن `no-fix-encoded` بدل أن يُمرّ عليه.
 * - **الحدّ قبل البناء**، والمقتطَع يُعلَن (`window_truncated`، وسطر `truncated`) بدل أن يبدو
 *   تقريرًا كاملًا.
 * - **النقاء:** لا قراءة ملف ولا عتاد ولا وقت. الجهاز يُمرَّر كـ[DeviceFacts]، والسطور
 *   كـ[ReportLine]، والمقاطع الإضافية كـ`extraSections`. فما يُقاس باختبار هو التقرير نفسه.
 */
data class DeviceFacts(
    /** `null` حين لا يُقرأ رقم الإصدار — وليس `"unknown"` ولا `"-"`: الغياب يبقى غيابًا. */
    val appVersion: String?,
    val moduleVersion: String?,
    val socModel: String?,
    val socManufacturer: String?,
    val hardware: String?,
    val apiLevel: Int,
    val kernel: String?,
    val rooted: Boolean,
    val board: String? = null,
    val abi: String? = null,
) {
    companion object {
        /** حدّ كل حقل في الترويسة: بصمة نظام طويلة لا يجوز أن تُغرِق التقرير. */
        const val MAX_FACT_CHARS: Int = 96

        fun clamp(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_FACT_CHARS)

        /**
         * `null` حين لا نعرفه — لا `"unknown"`.
         *
         * والفرق مقصود: نصّ بديل يشبه قيمة حقيقية يُقرأ لاحقًا كمعرفة عن الجهاز، بينما `null`
         * يجعل السطر يظهر كـ`-` أي «لم نقرأه».
         */
        fun render(value: String?): String = clamp(value) ?: "-"
    }
}

/** سطر واحد في التقرير: ما يجب أن يُعرض ويُحتسب، منفصلًا عن مصدره. */
data class ReportLine(
    val time: String,
    val level: String,
    val source: String,
    val event: String?,
    val area: LogArea,
    val verdict: LogVerdict,
    val reason: String,
    val raw: String,
) {
    companion object {
        /** حدّ السطر الواحد: رسالة استثناء طويلة تُقصّ في العرض، والعرض هو ما يُقرأ. */
        const val MAX_LINE_CHARS: Int = 300

        fun clampLine(raw: String): String = raw.replace(Regex("\\s+"), " ").trim().take(MAX_LINE_CHARS)
    }
}

object LogDiagnosticReport {

    /** سقف أسطر الفشل: التقرير ملخّص يُقرأ، لا تفريغ كامل. */
    const val MAX_FAILURES: Int = 40

    /** سقف أسطر السياق الأخيرة في التقرير المختصر. */
    const val MAX_TAIL: Int = 60

    /**
     * سقف الحجم الكلي — **وقاية لا قصّ**.
     *
     * والحزمة الكاملة تحمل السجل الخام، فيجوز أن تبلغ أضعاف التقرير المختصر. والحدّ هنا لئلا
     * يُنتج ملفًا لا يُشارَك ولا يُفتح؛ وبلوغه يُعلَن في آخر سطر بدل أن يُقتطع صامتًا.
     */
    const val MAX_CHARS: Int = 6_000_000

    /** علامة السطر المقتطَع — تُبحَث نصًّا في الاختبار وفي القارئ. */
    const val TRUNCATION_MARK: String = "-- truncated at $MAX_CHARS characters --"

    private const val NOT_AVAILABLE = "n/a"

    /**
     * يبني التقرير.
     *
     * @param lines المقروء من السجل، **الأقدم أولًا** كترتيبه في الملف. والاتجاه مقصود: التقرير
     *   يمشي مع الزمن، ومن يقرأ يقرأ السبب قبل النتيجة.
     * @param truncated هل قُطع المصدر عند حدّه (نافذة العرض لا الملف كله)؟ يُعلَن ولا يُخفي.
     * @param settings الإعداد القائم وقت البناء — يُدرَج كما هو، وهو ما يجيب «عطل جهاز أم عطل إعداد؟».
     * @param rawTailLimit كم سطرًا خامًّا في آخر التقرير. [MAX_TAIL] للتقرير المختصر، وحدّ كبير
     *   للحزمة الكاملة التي تُرسَل وحدها.
     * @param extraSections مقاطع إضافية جاهزة (العنوان ثم أسطره) — مثل حالة مقابض التطبيق
     *   المستقاة من ملفها. تُدرَج كما هي: الباني لا يقرأ ملفات.
     */
    fun build(
        facts: DeviceFacts,
        lines: List<ReportLine>,
        generatedAt: String,
        truncated: Boolean,
        settings: List<Pair<String, String>> = emptyList(),
        rawTailLimit: Int = MAX_TAIL,
        extraSections: List<Pair<String, List<String>>> = emptyList(),
        includeGuide: Boolean = true,
    ): String {
        val failures = lines.filter { it.verdict == LogVerdict.FAIL }
        val byArea = failures.groupBy { it.area }.entries.sortedByDescending { it.value.size }
        val repeated = failures
            .groupBy { it.reason }
            .entries
            .sortedByDescending { it.value.size }
            .take(MAX_FAILURES)

        // أسباب الأعطال الواردة في المقاطع الإضافية (حالة كل مقبض لكل تطبيق) تُدخَل في «ما يُفعل»
        // كذلك: ذلك المقطع هو الجواب المباشر عن «لماذا لم يعمل؟»، وإغفاله كان يجعل أهمّ قسم في
        // الحزمة هو الوحيد بلا إصلاح. وتُقرأ بنفس مفكّك الأسطر ونفس قواعد الحكم، فلا تتباعد
        // نسختان من «ما يُعدّ فشلًا».
        val sectionReasons = extraSections.flatMap { it.second }.mapNotNull(::sectionFailureReason)
        val reasonCounts = LinkedHashMap<String, Int>()
        repeated.forEach { (reason, entries) -> if (reason.isNotBlank()) reasonCounts[reason] = entries.size }
        sectionReasons.forEach { reason -> reasonCounts[reason] = (reasonCounts[reason] ?: 0) + 1 }
        val needsAction = reasonCounts.entries.sortedByDescending { it.value }

        // العنوان يتبع المحتوى: ما يحمل السجل كامًلا حزمة، وما يحمل ذيله تقرير. وعنوان واحد
        // للموضعين كان سيجعل التقرير المختصر يدّعي أنه يشمل كل شيء.
        val complete = rawTailLimit >= lines.size
        val text = buildString {
            appendLine(if (complete) "MaxManager log bundle" else "MaxManager diagnostic report")
            appendLine("schema=1")
            appendLine("generated_at=$generatedAt")
            appendLine("app_version=${DeviceFacts.render(facts.appVersion)}")
            appendLine("module_version=${DeviceFacts.render(facts.moduleVersion)}")
            appendLine("soc=${DeviceFacts.render(facts.socManufacturer)}/${DeviceFacts.render(facts.socModel)}")
            appendLine("hardware=${DeviceFacts.render(facts.hardware)}")
            appendLine("board=${DeviceFacts.render(facts.board)}")
            appendLine("abi=${DeviceFacts.render(facts.abi)}")
            appendLine("android_api=${facts.apiLevel}")
            appendLine("kernel=${DeviceFacts.render(facts.kernel)}")
            appendLine("root=${if (facts.rooted) "yes" else "no"}")
            appendLine("lines_read=${lines.size}")
            appendLine("failures=${failures.size}")
            appendLine("window_truncated=$truncated")
            appendLine()

            appendLine("-- configured --")
            if (settings.isEmpty()) {
                appendLine("none")
            } else {
                settings.forEach { (key, value) -> appendLine("$key=${value.ifBlank { "unset" }}") }
            }
            appendLine()

            if (includeGuide) {
                appendLine("-- how to read --")
                LogCodeGlossary.howToRead.forEachIndexed { index, step -> appendLine("${index + 1}) $step") }
                LogCodeGlossary.fieldGuideLines().forEach { appendLine(it) }
                appendLine()

                appendLine("-- code legend --")
                LogCodeGlossary.codeLines().forEach { appendLine(it) }
                appendLine()
            }

            appendLine("-- failures by area --")
            if (byArea.isEmpty()) {
                appendLine("none")
            } else {
                byArea.forEach { (area, entries) -> appendLine("${area.token}=${entries.size}") }
            }
            appendLine()

            appendLine("-- repeated reasons --")
            if (repeated.isEmpty()) {
                appendLine("none")
            } else {
                repeated.forEach { (reason, entries) ->
                    val first = entries.first()
                    appendLine("${entries.size}x ${first.area.token}/${first.event ?: NOT_AVAILABLE} reason=$reason")
                }
            }
            appendLine()

            // الغرض من الحزمة: أن تُحلّ من الملف وحده. وهذا المقطع هو الجواب عن «وبعدين؟» — رمز
            // واحد لكل عطل وقع فعليًّا (لا قاموس كامل يُقرأ بلا داعٍ).
            appendLine("-- what to do --")
            if (needsAction.isEmpty()) {
                appendLine("no failures in this file")
            } else {
                needsAction.forEach { (reason, count) ->
                    val fix = LogCodeGlossary.remedyOf(reason)
                    appendLine(
                        "reason=$reason count=$count " +
                            (fix?.let { "fix=$it" } ?: "fix=no-fix-encoded (a new code: read the failure lines above and encode a remedy)"),
                    )
                }
            }
            appendLine()

            appendLine("-- failure lines (oldest first) --")
            if (failures.isEmpty()) {
                appendLine("none")
            } else {
                failures.take(MAX_FAILURES).forEach { line -> appendLine(format(line)) }
                if (failures.size > MAX_FAILURES) {
                    appendLine("... ${failures.size - MAX_FAILURES} more failure lines omitted")
                }
            }
            appendLine()

            extraSections.forEach { (title, sectionLines) ->
                appendLine("-- $title --")
                if (sectionLines.isEmpty()) appendLine("none") else sectionLines.forEach { appendLine(it) }
                appendLine()
            }

            appendLine("-- raw log (oldest first) --")
            val tail = if (complete) lines else lines.takeLast(rawTailLimit)
            // السجل الخام يُنقل كما هو لا بترويسة مُضافة: من يقارنه بالملف الأصلي يجب أن يجد السطر
            // نفسه، والتنقية تقتصر على ما يفسد السطر المفرد (أسطر متعدّدة داخل قيمة).
            if (tail.isEmpty()) appendLine("none") else tail.forEach { appendLine(ReportLine.clampLine(it.raw)) }
        }
        if (text.length <= MAX_CHARS) return text
        return text.take(MAX_CHARS) + "\n$TRUNCATION_MARK\n"
    }

    /**
     * رمز سبب العطل في سطر مقطع إضافي، أو `null` حين لا يكون السطر عطلًا.
     *
     * وغياب `EVENT` مقبول: سطور حالة المقابض تُكتب `knob=... outcome=... reason=...` بلا حدث،
     * وهي نفس مفردات الحدث — ولذلك يُعطى اسم حدث بديل يصلح للحكم عليها، ثم يُحكم عليها بقواعد
     * [LogVerdict] نفسها بلا مستوى سطر (فلا مستوى في ملف الحالة).
     */
    private fun sectionFailureReason(text: String): String? {
        val fields = LogEventParser.fields(text)
        if (fields.isEmpty()) return null
        val event = fields.firstOrNull { it.key == "EVENT" }?.value ?: "PERAPP_KNOB"
        val parsed = LogEventLine(event, fields.filterNot { it.key == "EVENT" })
        if (LogVerdict.of(event, parsed, "") != LogVerdict.FAIL) return null
        return parsed.field("reason")?.takeIf { it.isNotBlank() }
    }

    /** سطر واحد: `HH:mm:ss L source EVENT reason | raw`. */
    private fun format(line: ReportLine): String {
        val event = line.event ?: "-"
        val reason = line.reason.takeIf(String::isNotBlank)?.let { " reason=$it" } ?: ""
        return "${line.time} ${line.level} ${line.source} $event$reason | ${ReportLine.clampLine(line.raw)}"
    }
}
