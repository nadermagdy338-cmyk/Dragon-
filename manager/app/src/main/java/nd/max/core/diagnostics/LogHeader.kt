/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.diagnostics

/**
 * الترويسة التي تُكتب **داخل ملف السجل** — الجهاز، والإعداد، وكيف يُقرأ الملف.
 *
 * لماذا وُجد
 * ----------
 * الغرض: أن يُرسَل ملف السجل وحده فيُشخَّص منه. وملف بلا ترويسة لا يحمل: أيّ جهاز، وأيّ إصدار،
 * وبأيّ إعداد — فيبقى السؤال «هل هذا عطل جهاز أم إعداد؟» بلا جواب. والترويسة تحمله، وتُكتب **في
 * بداية الجلسة** لا في تقرير منفصل: لأن الملف المُرسَل هو الذي يجب أن يشرح نفسه.
 *
 * والتقسيم ثلاث كتل عمدًا:
 *
 * - **الجهاز** يُكتب مرّة لكل عملية تشغيل: لا يتغيّر داخل العملية.
 * - **الإعداد** يُكتب مرّة لكل عملية تشغيل أيضًا: تغيّر الإعداد نفسه يُسجَّل بـ`USER_ACTION`،
 *   فالترويسة تصف نقطة البداية لا كل تغيّر.
 * - **الجلسة** تُكتب مع كل تطبيق: هي التي تفصل أحداث تطبيق عن تطبيق داخل الملف الواحد.
 *
 * والقاموس يُكتب **مع الترويسة كاملة** (الحقول والوحدات والرموز): أن يُشرح الرمز في مستند آخر
 * يعني ألّا يُشرح حين يُقرأ الملف وحده.
 *
 * وقاعدة ثابتة: كل سطر يبدأ بـ`EVENT=LOG_HEADER`، فيُفكَّك كبقية الأحداث، ويُستبعد من العرض
 * الافتراضي بمرشّح المصدر/المستوى إن أزعج — لكنه يبقى في الملف.
 */
object LogHeader {

    /** إصدار شكل الترويسة. تغيّر ترتيب الحقول أو معناها يستوجب رفعه. */
    const val SCHEMA: Int = 1

    const val EVENT: String = "LOG_HEADER"

    /**
     * الحدّ الذي يُقتطع عنده القاموس — وقاية من سرد بلا نهاية، لا هدف يُصان.
     *
     * ورُفع من ١٦٠ إلى ٢٥٦ حين صارت كتلتا `kind=fix` و`kind=howto` جزءًا من الترويسة: الحدّ
     * الأدنى كان يقطع جدول الإصلاح — أي يقطع **ما يُقرأ الملف من أجله**. والترويسة تُكتب مرّة
     * لكل عملية تشغيل، فسعة الأسطر هنا لا تُقاس بالحجم.
     */
    const val MAX_LINES: Int = 256

    private fun header(fields: List<Pair<String, String>>): String =
        "EVENT=$EVENT schema=$SCHEMA " + fields.joinToString(" ") { (key, value) -> "$key=$value" }

    /** هوية الجهاز والإصدارات — `kind=device`. */
    fun deviceLines(facts: DeviceFacts): List<String> = listOf(
        header(
            listOf(
                "kind" to "device",
                "app" to DeviceFacts.render(facts.appVersion),
                "module" to DeviceFacts.render(facts.moduleVersion),
                "soc" to "${DeviceFacts.render(facts.socManufacturer)}/${DeviceFacts.render(facts.socModel)}",
                "hardware" to DeviceFacts.render(facts.hardware),
                "board" to DeviceFacts.render(facts.board),
                "abi" to DeviceFacts.render(facts.abi),
                "api" to facts.apiLevel.toString(),
                "kernel" to DeviceFacts.render(facts.kernel),
                "root" to if (facts.rooted) "yes" else "no",
            ),
        ),
    )

    /** الإعداد الذي كان قائمًا عند بدء العملية — `kind=settings`. */
    fun settingsLines(settings: List<Pair<String, String>>): List<String> = settings.map { (key, value) ->
        header(listOf("kind" to "settings", "key" to key, "value" to value.ifBlank { "unset" }))
    }

    /** كيف يُقرأ الملف: شكل السطر، ومعنى كل حقل، ووحدة كل مقبض — `kind=field` / `kind=unit`. */
    fun guideLines(): List<String> = LogCodeGlossary.guideLogLines().map { line ->
        // الأسطر المُجهَّزة تأتي بصيغة `kind=... name=... meaning=...` جاهزة من القاموس، فهي
        // تُلحَق كما هي ببادئة الحدث وحدها — بلا إعادة صياغة ولا نسخة ثانية من النصّ.
        "EVENT=$EVENT schema=$SCHEMA $line"
    }

    /** معنى كل رمز — `kind=code`. */
    fun legendLines(): List<String> = LogCodeGlossary.codeLogLines().map { line ->
        "EVENT=$EVENT schema=$SCHEMA kind=code $line"
    }

    /**
     * ما يُفعل عند كل عطل — `kind=fix`.
     *
     * ويُكتب كاملًا في الترويسة لا مقتصرًا على ما وقع منها: الملف يُرسَل **بعد** وقوع العطل،
     * وحينها لا مجال لكتابة إصلاح رمز لم يظهر في لقطة بيضاء. فالمكتوب هنا مرجع، والمقتصر على
     * ما وقع يُبنى في الحزمة (`-- what to do --`) من الأسطر نفسها.
     */
    fun remedyLines(): List<String> = LogCodeGlossary.fixLogLines().map { line ->
        "EVENT=$EVENT schema=$SCHEMA kind=fix $line"
    }

    /** ترتيب القراءة — `kind=howto`. */
    fun howToLines(): List<String> = LogCodeGlossary.howToLogLines().map { line ->
        "EVENT=$EVENT schema=$SCHEMA $line"
    }

    /**
     * كتلة الجلسة: من بدأت، وأيّ تطبيق، وما الذي طُلب له فعلًا.
     *
     * و`knobs=` هي جوهر الفائدة: بدونها يُعرف أن الكتابة فشلت ولا يُعرف أن المطلوب أصلًا كان
     * 1.3GHz — فيصير السؤال «هل فشل التطبيق أم فشل الطلب؟» بلا جواب.
     */
    fun sessionLines(
        sessionId: String,
        packageName: String?,
        startedAt: String,
        knobs: List<Pair<String, String>>,
    ): List<String> {
        val head = listOf(
            header(
                listOf(
                    "kind" to "session",
                    "id" to sessionId,
                    "pkg" to (packageName ?: "global"),
                    "started" to startedAt,
                ),
            ),
        )
        if (knobs.isEmpty()) return head
        return head + knobs.map { (key, value) ->
            header(listOf("kind" to "session-knob", "id" to sessionId, "knob" to key, "desired" to value))
        }
    }

    /**
     * الترويسة الكاملة التي تُكتب مرّة لكل عملية تشغيل (بلا كتلة جلسة).
     *
     * والترتيب مقصود ليكون ترتيب القراءة: كيف يُقرأ ← أي جهاز ← أي إعداد ← شكل السطر ووحدته ←
     * معنى الرموز ← إصلاحها. فمن يفتح الملف من أوله يجد الجواب قبل أن يصل إلى حدث واحد.
     */
    fun startupLines(facts: DeviceFacts, settings: List<Pair<String, String>>): List<String> {
        val lines = howToLines() + deviceLines(facts) + settingsLines(settings) +
            guideLines() + legendLines() + remedyLines()
        // الحدّ وقاية أخيرة لا قَصّ: القواميس تُصان بحجمها، ولو تجاوزت الحدّ يُعلَن ذلك بدل أن
        // يُقتطع القاموس صامتًا فيبدو ناقصًا بلا سبب.
        if (lines.size <= MAX_LINES) return lines
        return lines.take(MAX_LINES - 1) + header(
            listOf("kind" to "truncated", "written" to (MAX_LINES - 1).toString(), "total" to lines.size.toString()),
        )
    }
}
