package nd.max.core.diagnostics

import nd.max.core.hardware.HardwareControlKey

/**
 * فكّ سطر السجل إلى **حدث وحقول** — الطبقة التي تجعل السجل مفهومًا لا نصًّا.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * كل الأحداث في هذا المشروع تُكتب بصيغة واحدة موحّدة: `EVENT=<NAME> k=v k=v ...` (انظر
 * `EventLog.kt` · `AppMonitorLogger.kt` · `log_zenith()/external_log()` في `SystemLogger.c`).
 * أي أن **بناء** السطر منظَّم، لكن **قراءته** في `LogsViewerScreen` كانت تُبرز `EVENT=` وحده
 * وتترك البقية مدفونة في سطر واحد طويل: `knob` و`outcome` و`reason` موجودة، ولا يراها إلا من
 * يقرأ النصّ بعينه.
 *
 * والفرق ليس تجميليًّا. الحقول هي ما يُبنى عليه:
 *
 * 1. **التصفية بالميزة** — «أرني كل ما يتعلّق بالحرارة» يحتاج معرفة الميزة من الحدث أو المقبض.
 * 2. **التصفية بالنتيجة** — «أرني الفشل» يحتاج حكمًا مشتقًّا من `outcome`/`ok`/`verified`/
 *    `verdict`، لا من مستوى السطر وحده. وفشلٌ كُتب `W` أو حتى `I` يبقى فشلًا.
 * 3. **تاريخ كل مقبض** — «ما الذي حدث لهذا المقبض آخر عشر مرّات» يحتاج تجميعًا بالمقبض.
 * 4. **التقرير التشخيصي** — يحتاج حقولًا لا سطورًا.
 *
 * وقواعد النقاء التي تحكم هذا الملف:
 *
 * - **لا قراءة ملف ولا عتاد ولا وقت.** كل شيء يُشتقّ من نصّ السطر. فالمنطق كله يُقاس باختبار
 *   وحدة بلا جهاز، وهو ما لم يكن ممكنًا حين كانت القراءة داخل الواجهة.
 * - **المجهول يبقى مجهولًا.** سطر لا نفهمه يُعاد بـ[LogVerdict.UNKNOWN]، ولا يُصنَّف نجاحًا
 *   ولا فشلًا. وحقل غير موجود `null` لا نصّ بديل يشبه قيمة حقيقية.
 * - **التصنيف قائم على قواعد معلَنة لا على جدول يدوي لكل حدث.** الجدول اليدوي ينمو مع كل حدث
 *   جديد ثم يشيخ؛ والقاعدة (`PERAPP_*` · `THERMAL*` · `GPU_*` …) تصمد.
 * - **لا يُخترع حقل.** `LogField` يحمل ما في السطر حرفيًّا، والواجهة تعرضه كما هو.
 */
data class LogField(val key: String, val value: String)

/** حدث واحد مفكوك. [event] بلا بادئة `EVENT=`، والحقول بترتيب ظهورها في السطر. */
data class LogEventLine(
    val event: String,
    val fields: List<LogField>,
) {
    /** قيمة حقل، أو `null` حين لا يكون الحقل موجودًا في هذا الحدث. */
    fun field(key: String): String? = fields.firstOrNull { it.key == key }?.value

    /** أول حقل موجود من [keys] — لأن نفس المعنى يُسمّى `path` في حدث و`target` في آخر. */
    fun firstField(vararg keys: String): String? = keys.firstNotNullOfOrNull(::field)

    /**
     * المقبض أو العقدة التي يخصّها الحدث، إن كان يخصّ واحدًا.
     *
     * و`path` تُقبل هنا لأن `WRITE_CHECK` تسمّي العقدة بمسارها لا بمفتاح مُشرَّع — وتاريخها
     * بالمقبض يبقى مفيدًا بعد ذلك (كم مرّة كُتب إلى هذه العقدة وما قرأناه في آخر مرّة).
     */
    val target: String? get() = firstField("knob", "path")

    /** التطبيق الذي وقع عليه الحدث، إن كان مقيَّدًا بتطبيق. */
    val packageName: String? get() = field("pkg")
}

object LogEventParser {

    /**
     * بداية حقل: `k=` في بداية السطر أو بعد فراغ، بمفتاح من حروف/أرقام/شرطة سفلية.
     *
     * وهذا هو حدّ الفصل الوحيد الذي يعمل هنا: **قيمة قد تحتوي فراغًا** (EventLog.error يضع
     * رسالة استثناء في `detail=`)، فلا يجوز الفصل على الفراغ. والفصل على «بداية حقل» يبقي
     * القيمة كاملة ما لم تحتوِ هي نفسها `x=y` في وسطها — وهي حالة معلَنة ومقبولة: أسوأ أثرها
     * حقل زائد في العرض، لا قراءة خاطئة لقيمة.
     */
    private val FIELD_START = Regex("(?:^|\\s)([A-Za-z_][A-Za-z0-9_]*)=")

    private const val EVENT_KEY = "EVENT"

    /**
     * يفكّ **نصّ الرسالة** (الجزء بعد `tag: `) إلى حدث وحقول، أو `null` حين لا يكون حدثًا أصلًا.
     *
     * ويُشترط وجود `EVENT=`؛ فسطر عادي بلا حدث يمرّ كما هو إلى العرض الخام بدل أن يُصنَّف
     * حدثًا بحقول فارغة.
     */
    fun parse(message: String): LogEventLine? {
        val text = message.trim()
        if (text.isEmpty()) return null
        val fields = fields(text)
        val event = fields.firstOrNull { it.key == EVENT_KEY }?.value?.takeIf(String::isNotEmpty) ?: return null
        return LogEventLine(
            event = event,
            fields = fields.filterNot { it.key == EVENT_KEY },
        )
    }

    /** كل حقول السطر بترتيب ظهورها — بلا اشتراط وجود `EVENT`. */
    fun fields(text: String): List<LogField> {
        val starts = FIELD_START.findAll(text).toList()
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexed { index, match ->
            val key = match.groupValues[1]
            val valueStart = match.range.last + 1
            val valueEnd = starts.getOrNull(index + 1)?.range?.first ?: text.length
            // الطرف الأيمن يُقتطع عند بداية الحقل التالي؛ والفراغ الذي فُصل به الحقل نفسه
            // ليس جزءًا من القيمة.
            val value = text.substring(valueStart.coerceAtMost(valueEnd), valueEnd).trim()
            LogField(key, value)
        }
    }
}

/**
 * الميزة التي يخصّها الحدث — ما يُبنى عليه «أرني الحرارة» أو «أرني GPU».
 *
 * والترتيب مقصود: الحدث الأكثر تخصيصًا يفوز. `PERAPP_THERMAL_GUARD` يخصّ **الحرارة** وإن بدأ
 * بـ`PERAPP_`، ولو فاز `PER_APP` لأن التطبيق كان مقيَّدًا به لضاع الفرق الذي يبحث عنه من
 * يشخّص عطلًا حراريًّا.
 */
enum class LogArea(val token: String) {
    THERMAL("thermal"),
    GPU("gpu"),
    CPU("cpu"),
    DISPLAY("display"),
    CHARGING("charging"),
    MEMORY("memory"),
    PER_APP("per-app"),
    ENGINE("engine"),
    APPS("apps"),
    SYSTEM("system"),
    USER("user"),
    MEASURED("measured"),
    OTHER("other");

    companion object {

        /**
         * قواعد البادئات، مرتّبة من الأخصّ إلى الأعمّ.
         *
         * وهي بادئات **قائمة** في المستودع لا مُفترَضة: كل بادئة هنا لها منتجها في الكود.
         */
        private val RULES: List<Pair<LogArea, List<String>>> = listOf(
            THERMAL to listOf("THERMAL"),
            GPU to listOf("GPU_"),
            DISPLAY to listOf("REFRESH_RATE", "RESOLUTION", "RENDERER"),
            CHARGING to listOf("BYPASS_CHARGE", "CHARGING"),
            MEMORY to listOf("ZRAM", "MEMORY"),
            CPU to listOf("CPU_", "GOVERNOR"),
            PER_APP to listOf("PERAPP_"),
            APPS to listOf("APP_", "APPLIST_", "GAMELIST", "GAME_", "PRELOAD_", "PID_", "FOREGROUND_", "PRIORITY_"),
            ENGINE to listOf(
                "APPLY_", "REVERT_", "PROFILE_", "CONFIG_", "STATE_", "LIVE_CONFIG_",
                "DYNAMIC_PROFILE", "GRACE_PERIOD", "FREQOFFSET", "DRIFT_",
            ),
            SYSTEM to listOf(
                "MODULE_", "INTEGRITY_", "JAVA_", "DAEMON_", "SERVICE_", "STALE_PROP_", "PROP_",
                "RUNTIME_CLEANUP", "SHELL_", "TOAST_", "ATLAS_", "THERMAL_ROUTER",
            ),
            USER to listOf("USER_ACTION", "USER_TRIGGERED", "UI_ERROR"),
            MEASURED to listOf("OP_RESULT", "SYMPTOM", "WRITE_CHECK"),
        )

        /**
         * كلمات العتاد التي يُسمّى بها الحدث نفسه — وهي التي تفوز على استنتاج المقبض.
         *
         * ولماذا لم تكفِ [RULES] وحدها: تلك تشترط أن تكون الكلمة **بادئة**، و`PERAPP_THERMAL_GUARD`
         * ليست بادئتها `THERMAL` بل `PERAPP_`. وكان أثر ذلك مزدوجًا: مع مقبض يحتوي `gpu` كان السطر
         * يُصنَّف GPU، وبدون مقبض كان يُصنَّف «تطبيق» — في الحالتين **يغيب سطر العطل الحراري عن
         * مرشّح الحرارة**، وهو بالضبط السطر الذي يُفتح المرشّح من أجله.
         */
        private val HARDWARE_TOKENS: List<Pair<LogArea, String>> = listOf(
            THERMAL to "THERMAL",
            GPU to "GPU",
            CPU to "CPU",
            MEMORY to "MEMORY",
            CHARGING to "CHARGE",
        )

        /**
         * الميزة على ثلاث مراحل مرتّبة، وكل مرحلة أعمّ من سابقتها.
         *
         * 1. **اسم الحدث إن سمّى عتادًا** ([HARDWARE_TOKENS]): `PERAPP_THERMAL_GUARD` حراري وإن بدأ
         *    بـ`PERAPP_`، و`knob=gpu_profile` لا يقلبه GPU. والسبب أن اسم الحدث هو **من كتب السطر**
         *    وهو أعلم بما يقصده.
         * 2. **المقبض**: `PERAPP_KNOB` لا يسمّي عتادًا، والمقبض هو ما يقول أين وقع العمل —
         *    `cpu_limits:policy0` يخصّ CPU وإن كان الحدث «لتطبيق». ويُستعمل [HardwareControlKey]
         *    هنا بدل تكرار بادئاته، فلا تصير البادئة معرَّفة في موضعين تتباعد نسختاهما.
         * 3. **البادئات العامة** ([RULES]): للتطبيقات والمحرّك والنظام والمقارير — وهي أعمّ من أن
         *    تحكم على سطر يسمّي عتاده.
         */
        fun of(event: String, target: String? = null): LogArea {
            val name = event.uppercase()

            HARDWARE_TOKENS.forEach { (area, token) ->
                if (name.contains(token)) return area
            }

            val knob = target?.trim().orEmpty()
            if (knob.isNotEmpty()) {
                HardwareControlKey.gpuFrequencyDevice(knob)?.let { return GPU }
                HardwareControlKey.cpuLimitsPolicy(knob)?.let { return CPU }
                when {
                    knob.contains("gpu", ignoreCase = true) -> return GPU
                    knob.contains("thermal", ignoreCase = true) -> return THERMAL
                    knob.contains("refresh", ignoreCase = true) -> return DISPLAY
                    knob.contains("charge", ignoreCase = true) -> return CHARGING
                    knob.contains("zram", ignoreCase = true) || knob.contains("memory", ignoreCase = true) -> return MEMORY
                    // `cpu_boost` يسقط هنا أيضًا، فلا يُعاد ذكره شرطًا آخر: نفس الفرع يغطّيه.
                    knob.contains("cpu", ignoreCase = true) -> return CPU
                }
            }

            RULES.forEach { (area, prefixes) ->
                if (prefixes.any { name.startsWith(it) }) return area
            }
            return OTHER
        }
    }
}

/**
 * حكم الحدث: هل ما قصدَه الجهاز وقع؟
 *
 * وثلاث حالات لا اثنتان، لأن الخلط بين «لم يُحاول» و«نجح» هو ما يجعل سجلًا يبدو سليمًا على
 * جهاز لا يعمل فيه شيء: `USER_ACTION` ليس نجاحًا ولا فشلًا، و`PERAPP_KNOB outcome=skipped`
 * تعني «تُرك عمدًا» لا «طُبِّق».
 */
enum class LogVerdict(val token: String) {
    OK("ok"),
    FAIL("fail"),
    UNKNOWN("unknown");

    companion object {

        /** رموز النتيجة التي تعني «لم يتحقّق الطلب» — من `PerAppHardwareStatus.Outcome`. */
        private val FAIL_OUTCOMES = setOf("not-verified", "not-writable", "unsupported", "blocked", "failed")

        /** كلمات حكم الكتابة التي تعني «الجهاز لم يقبل القيمة» — من `WriteVerification.Outcome`. */
        private val FAIL_VERDICTS = setOf("differs", "write_failed")

        /**
         * كلمات الحدث التي تعني فشلًا أو رفضًا.
         *
         * وهي مستخرَجة من المفردات الفعلية في المستودع (`APPLY_FAILED` · `GOVERNOR_REJECTED` ·
         * `THERMAL_UNSUPPORTED` · `PID_FETCH_GAVE_UP` …) لا من قائمة مُتخيَّلة.
         */
        private val FAIL_TOKENS = listOf(
            "FAILED", "FAILURE", "REJECTED", "UNSUPPORTED", "BLOCKED", "ABORTED",
            "TIMEOUT", "GAVE_UP", "DROPPED", "MISSING", "UNAVAILABLE", "_ERROR",
        )

        /** كلمات الحدث التي تعني نجاحًا معلَنًا. */
        private val OK_TOKENS = listOf(
            "APPLIED", "CAPTURED", "RESTORED", "SUCCESS", "COMPLETE", "READY", "PASSED",
            "ENABLED", "ACQUIRED", "RELEASED", "STARTED", "LOADED", "SET", "_DONE",
        )

        /**
         * الحكم من الحقول الصريحة أولًا، ثم من مستوى السطر، ثم من اسم الحدث.
         *
         * والترتيب هو المهم: `PERAPP_KNOB outcome=applied` نجاح **مهما كان اسم الحدث**، لأن
         * الحقل هو ما كتبه من نفّذ العملية. ولو بدأنا من الاسم لصار كل حدث اسمه يحتوي `FAILED`
         * فشلًا حتى لو قال حقلُه إنه نُفِّذ.
         */
        fun of(event: String, line: LogEventLine?, levelLetter: String): LogVerdict {
            val name = event.uppercase()

            line?.field("ok")?.let { return if (it.equals("false", true)) FAIL else if (it.equals("true", true)) OK else UNKNOWN }
            line?.field("verified")?.let { return if (it.equals("false", true)) FAIL else if (it.equals("true", true)) OK else UNKNOWN }
            line?.field("outcome")?.let { outcome ->
                val token = outcome.lowercase()
                if (token in FAIL_OUTCOMES) return FAIL
                // `skipped` مقصودة: لم يُحاول، فلا نجاح ولا فشل.
                if (token == "applied") return OK
                if (token == "skipped") return UNKNOWN
            }
            line?.field("verdict")?.let { verdict ->
                val token = verdict.lowercase()
                if (token in FAIL_VERDICTS) return FAIL
                if (token == "matched") return OK
                // `unreadable` لا حكم فيها — لا نقول «نجحت» ولا «فشلت».
                if (token == "unreadable") return UNKNOWN
            }

            if (levelLetter.equals("E", true) || levelLetter.equals("F", true)) return FAIL
            if (FAIL_TOKENS.any { name.contains(it) }) return FAIL
            if (name.contains("SKIPPED")) return UNKNOWN
            if (OK_TOKENS.any { name.contains(it) }) return OK
            return UNKNOWN
        }
    }
}

/** ملاحظة واحدة على هدف (مقبض أو عقدة) — المدخل الخالص لتجميع التاريخ. */
data class LogObservation(
    val id: Long,
    val time: String,
    val target: String,
    val verdict: LogVerdict,
    val reason: String,
    val expected: String,
    val live: String,
    val source: String,
)

/** آخر ما عُرف عن هدف واحد، وكم مرّة ظهر. */
data class LogTargetSummary(
    val target: String,
    val verdict: LogVerdict,
    val reason: String,
    val expected: String,
    val live: String,
    val observations: Int,
    val failures: Int,
    val lastId: Long,
    val lastTime: String,
) {
    /** آخر ملاحظة تحمل قيمة مقروءة — لعرض `expected -> live` في الملخّص. */
    val hasReadback: Boolean get() = expected.isNotEmpty() || live.isNotEmpty()
}

object LogTargetHistory {

    /** سقف الملخّصات: القائمة شاشة تشخيص لا أرشيفًا. */
    const val MAX_TARGETS: Int = 60

    /**
     * يجمّع الملاحظات بالمقبض: **الأحدث يقود**، والعدد يكشف ما لا يظهره سطر واحد.
     *
     * والعدد مقصود: مقبض فشل مرّة وفشل ستّين مرّة ليسا العطل نفسه، وسطر واحد في السجل لا
     * يُظهر الفرق. ولهذا يُحمل `failures` مع `observations` بدل الاكتفاء بآخر حكم.
     *
     * والترتيب: الفشل أولًا (وإن كان أقدم)، ثم الأكثر ملاحظات. الترتيب الافتراضي «الأحدث
     * أولًا» يدفن الفشل تحت سطور سليمة كثيرة بعده — وهو عكس ما يُفتح هذا العرض من أجله.
     */
    fun summarise(observations: List<LogObservation>, max: Int = MAX_TARGETS): List<LogTargetSummary> {
        if (max <= 0) return emptyList()
        val byTarget = observations.groupBy { it.target }
        return byTarget.mapNotNull { (target, entries) ->
            val ordered = entries.sortedByDescending { it.id }
            val last = ordered.firstOrNull() ?: return@mapNotNull null
            // آخر ملاحظة تحمل `expected/live`، لا مجرّد آخر ملاحظة: سطر ناجح بلا قيم لا
            // يجوز أن يمحو آخر قياس حقيقي عرفناه.
            val lastMeasured = ordered.firstOrNull { it.expected.isNotEmpty() || it.live.isNotEmpty() } ?: last
            LogTargetSummary(
                target = target,
                verdict = last.verdict,
                reason = last.reason,
                expected = lastMeasured.expected,
                live = lastMeasured.live,
                observations = entries.size,
                failures = entries.count { it.verdict == LogVerdict.FAIL },
                lastId = last.id,
                lastTime = last.time,
            )
        }.sortedWith(
            compareByDescending<LogTargetSummary> { it.verdict == LogVerdict.FAIL }
                .thenByDescending { it.failures }
                .thenByDescending { it.observations }
                .thenByDescending { it.lastId },
        ).take(max)
    }
}
