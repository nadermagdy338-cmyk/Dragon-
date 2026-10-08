/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Storyboard — الشاشة الرئيسية كلوحة تحكي **ما فعله اختيارك**، لا لوحة تقيس الجهاز.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * الشاشة الرئيسية كانت تقيس: حرارة، حمل معالج، رسم بياني، أنوية. وهي أرقام موجودة في شاشات
 * `Thermal` و`CPU` و`GPU` نفسها، فالقارئ يرى المعلومة مرّتين ولا يرى **النتيجة**: هل فعّلت
 * Max AI؟ هل تطبيقي عليه `gaming` الآن؟ هل `Kill Background Apps` على «تشغيل» أم نسيته؟ وهل
 * رفعت تردد نواة قبل قليل؟ هذه الأسئلة مكانها لوحة واحدة تُحكي، لأن الجواب موزّع على إحدى
 * وخمسين شاشة، والمستخدم يُطلب منه أن يبحث ليتأكّد.
 *
 * فالقاعدة هنا: **لا رقم جديد**. اللوحة تُركّب من مصادر قائمة، وكل سطر يحمل مصدره:
 *
 * | السطر | مصدره | ما يعنيه |
 * |---|---|---|
 * | تغيير مقبض (من كذا إلى كذا) | `PerAppHardwareStatus.Record` (مكتوب في العتاد ثم مقروء) | ما وقع فعلًا |
 * | اختيارك للبروفايل/المفتاح | `AppConfig` (ملف إعداداتك) | ما طلبتَه |
 * | إخفاق مع سببه | `Record.outcome` + `reason` | لماذا لم يقع |
 *
 * وقواعد الصدق، لأن اللوحة التي تُجمّل تصير أسوأ من غيابها:
 *
 * 1. **لا سطر بلا دليل**: سطر «من كذا إلى كذا» يحتاج `Record` مقيسًا؛ والاختيار وحده يُكتب
 *    «مُختار» لا «طُبِّق». (وهذا الفرق هو ما يمنع لوحة تُخبر المستخدم أن شيئًا يعمل وهو لا يعمل.)
 * 2. **الإخفاق يتقدّم الصفوف**: من فتح اللوحة يريد أن يعرف ما لم يعمل، لا أن يقرأ تنويعة نجاح.
 * 3. **الصمت عند عدم الفعل**: `default` ليست تغييرًا — فلا سطر لها أصلًا، بدل سطر يقول «لا شيء»
 *    في كل مرة تُفتح الشاشة.
 *
 * والدوال كلها **خالصة** (بلا Compose وبلا Android) لتُقاس باختبار وحدة في JVM.
 */
package nd.max.ui.mainscreens

import nd.max.core.hardware.HardwareControlKey

/*
 * والمدخلات هنا **أنواع اللوحة** لا أنواع المصادر: لو استوردت `PerAppHardwareStatus` أو فاكّ
 * `cpu_policy_controls` لجرّ الملفَين `RootFileAccess` و`android.content` معهما — وصارت اللوحة
 * غير قابلة للقياس في JVM. فالواجهة تُترجم من المصدر إلى هذه الأنواع، والقرار يبقى خالصًا.
 */

/** ما نوع المشهد الذي يُحكى: من اختار هذا؟ */
enum class SceneKind { PER_APP, MAX_AI, MANUAL }

/** حال السطر — وهو ما يُترجم إلى لون في الواجهة، ويُقاس هنا. */
enum class LineTone {
    /** كُتب وتحقّق (أو اختيار مؤكَّد أثره مقيس). */
    DONE,

    /** كُتب ثم لم يتحقّق، فاستُرجع خط الأساس. */
    FAILED,

    /** لم يُنفَّذ عمدًا: الجهاز لا يُعلن المقبض، أو مالك أعلى يمسكه. */
    HELD,

    /** المستخدم اختار «افتراضي» — لا تغيير، ولا يُعرض كسطر إنجاز. */
    OFF,
}

/**
 * سطر واحد في اللوحة.
 *
 * @param knob مفتاح المقبض كما في الإعدادات والسجل (`gpu_profile` · `cpu_limits:policy0` …)،
 *   فالواجهة تترجمه من جدول واحد، ومن يقرأ السجل يقابل السطر بمصدره بلا تخمين.
 * @param from القيمة قبل — `null` حين لا «قبل» لها (اختيار جديد لا تغيير داخل قيمة قائمة).
 * @param to القيمة بعد (المقروءة من العتاد إن كان السطر مقيسًا).
 * @param reason سبب الإخفاق/المنع حين يكون السطر `FAILED` أو `HELD`، وإلا `null`.
 */
data class StoryLine(
    val knob: String,
    val from: String?,
    val to: String?,
    val tone: LineTone,
    val reason: String? = null,
)

/**
 * مشهد: «هذا ما يحدث الآن» — لتطبيق، أو للذكاء، أو لتحكّمك اليدوي.
 *
 * @param labelKey مفتاح وصف المشهد (`scene.per_app` · `scene.max_ai` · `scene.manual`).
 * @param appLabel اسم التطبيق حين يكون المشهد خاصًّا بتطبيق.
 * @param atMs وقت قياس المصدر (من السجل نفسه)، لا وقت بناء الواجهة.
 */
data class StoryboardScene(
    val kind: SceneKind,
    val labelKey: String,
    val appLabel: String?,
    val packageName: String? = null,
    val lines: List<StoryLine>,
    val atMs: Long?,
)

/**
 * نتيجة مقبض **مقيسة** كما يكتبها العتاد: نفس حقول `PerAppHardwareStatus.Record` بلا تبعية عليه.
 *
 * @param outcome `applied` · `skipped` · `not-verified` · `blocked` · `unsupported` · `not-writable`.
 */
data class MeasuredOutcome(
    val knob: String,
    val outcome: String,
    val reason: String = "",
    val expected: String = "",
    val live: String = "",
) {
    val isFailure: Boolean get() = outcome != "applied" && outcome != "skipped"
}

/** نية المستخدم على سياسة CPU — بعد فكّ الترميز في طبقة الواجهة. */
data class CpuPolicyChoice(
    val policyName: String,
    val label: String,
    val minKHz: Long,
    val maxKHz: Long,
)

/** مدخلات مشهد التطبيق — كما تُقرأ من اختيارك ومن نتائج العتاد. */
data class PerAppSceneInput(
    val packageName: String,
    val appLabel: String,
    /** `gpu_profile` كما اخترته: `default` تعني «لا شيء» فلا مشهد. */
    val profile: String,
    /** نيّاتك على سياسات CPU (`cpu_policy_controls` بعد الفكّ). */
    val cpuPolicies: List<CpuPolicyChoice> = emptyList(),
    val killsBackground: Boolean = false,
    val refreshRate: String = "default",
    val renderer: String = "default",
    /** حاكم CPU الذي اخترته لهذا التطبيق (`default` تعني «لا تلمس»). */
    val cpuGovernor: String = "default",
    /** حاكم GPU الذي اخترته لهذا التطبيق. */
    val gpuGovernor: String = "default",
    /** سقف تردّد GPU الذي ثبّتته بنفسك (هرتز كما في الإعدادات)، أو `default`. */
    val gpuMaxFreq: String = "default",
    /** نتائج العتاد المقيسة لهذا التطبيق (قد تكون فارغة: لا قياس، فلا «طُبِّق»). */
    val outcomes: List<MeasuredOutcome> = emptyList(),
    /** وقت القياس كما يحمله ملف الحالة نفسه — يُعرض «متى» بدل «الآن» المُخترَع. */
    val measuredAtMs: Long? = null,
)

/** مدخلات مشهد الذكاء. */
data class MaxAiSceneInput(
    val active: Boolean,
    /** وصف الهدف كما اختاره المستخدم (`performance` · `balanced` …) أو `null`. */
    val objective: String? = null,
    /**
     * المقابض التي **يملكها** الذكاء فعلًا، كما يقرأها دفتر الملكية — لا كل مقبض لُمس.
     * والملكية هي الدليل، فلا يُكتب «الذكاء يضبط كذا» إلا لأصبع له على المقبض.
     */
    val ownedLines: List<StoryLine> = emptyList(),
    /** آخر تغيير مقيس نسبه الذكاء، إن وُجد. */
    val lastChange: StoryLine? = null,
)

/** مدخلات مشهد التحكّم اليدوي: مقبض لمسه المستخدم بنفسه. */
data class ManualSceneInput(
    val knob: String,
    val from: String?,
    val to: String?,
    val verified: Boolean = true,
    val reason: String? = null,
)

object StoryboardModel {

    /** حدّ أعلى مقصود: اللوحة لوحة، لا سجلّ. ما بعد الحدّ يُطرح لا يُصغَّر. */
    const val MAX_SCENES = 3
    const val MAX_LINES_PER_SCENE = 5

    /**
     * مشهد التطبيق، أو `null` إن كان كل شيء على «افتراضي».
     *
     * والترتيب داخل المشهد مقصود: **الإخفاق أولًا** ثم التغيير المقيس ثم الاختيار غير المقيس.
     * ومن يفتح اللوحة يسأل «هل عمل؟» — فيُجاب بالسؤال نفسه أولًا، لا بسرد نجاح يخفي سطرًا أحمر
     * في الأسفل.
     */
    fun perAppScene(input: PerAppSceneInput): StoryboardScene? {
        val measured = input.outcomes.associateBy { it.knob }
        val lines = mutableListOf<StoryLine>()

        // ١ · نتائج العتاد المقيسة — بأي حالة كانت، فالفشل سطر أيضًا.
        input.outcomes.forEach { record ->
            lines += StoryLine(
                knob = record.knob,
                from = record.expected.takeIf { it.isNotBlank() && it != "none" },
                to = record.live.takeIf { it.isNotBlank() && it != "none" },
                tone = when (record.outcome) {
                    "applied" -> LineTone.DONE
                    "skipped" -> LineTone.OFF
                    else -> LineTone.FAILED
                },
                reason = record.reason.takeIf { record.isFailure },
            )
        }

        // ٢ · اختياراتك التي لا يُقاس لها سطر عتاد (فلا تُدَّعى كإنجاز).
        cpuPolicyLines(input.cpuPolicies, measured.keys).forEach { lines += it }
        if (input.killsBackground && "kill_bg_apps" !in measured) {
            lines += StoryLine("kill_bg_apps", null, "on", LineTone.DONE)
        }
        refreshAndRenderer(input, measured.keys).forEach { lines += it }
        governorAndFrequencyLines(input, measured.keys).forEach { lines += it }

        // ٣ · البروفايل: بلا سطر عتاد يعني «اخترتَ ولم يُقس» — وهذا يُقال كما هو.
        val profile = input.profile.ifBlank { "default" }
        if (profile != "default" && "gpu_profile" !in measured) {
            lines += StoryLine("gpu_profile", null, profile, LineTone.DONE)
        }

        val ordered = orderLines(lines)
        if (ordered.isEmpty() || ordered.all { it.tone == LineTone.OFF }) return null
        return StoryboardScene(
            kind = SceneKind.PER_APP,
            labelKey = "scene.per_app",
            appLabel = input.appLabel.ifBlank { input.packageName },
            packageName = input.packageName.takeIf { it.isNotBlank() },
            lines = ordered.take(MAX_LINES_PER_SCENE),
            atMs = input.measuredAtMs,
        )
    }

    /** مشهد الذكاء: يظهر فقط وهو يعمل، أو حين غيّر شيئًا مقيسًا. */
    fun maxAiScene(input: MaxAiSceneInput): StoryboardScene? {
        if (!input.active && input.lastChange == null) return null
        val lines = mutableListOf<StoryLine>()
        input.lastChange?.let { lines += it }
        input.ownedLines.forEach { lines += it }
        if (lines.isEmpty() && !input.active) return null
        // سطر الهدف اختيارك لا إنجازك: يُكتب بلا «من/إلى» لأنه ليس تغييرًا داخل قيمة قائمة.
        input.objective?.takeIf { it.isNotBlank() }?.let { objective ->
            if (lines.none { it.knob == "max_ai_objective" }) {
                lines += StoryLine("max_ai_objective", null, objective, if (input.active) LineTone.DONE else LineTone.OFF)
            }
        }
        // وحين يعمل بلا تغيير مقيس بعد، فالسطر يقول «يعمل» — وهي حالة من مصدرها (`active`)
        // لا رقم مُخترع. وبلا هذا السطر كان المشهد يفرغ فيُطرح، فيرى من فعّل الذكاء لوحةً
        // لا تذكر أنه فعّله — وهي نفس الملاحظة التي وُلدت منها هذه اللوحة.
        if (input.active && lines.none { it.knob == "max_ai_active" }) {
            // بلا قيمة تالية مقصود: السطر يقول «يعمل» بعلامة صحّ لا بمقطع إنجليزي خام في واجهة
            // عربية («active») — والعلامة نفسها تعني «على» في كل لوحة.
            lines += StoryLine("max_ai_active", null, null, LineTone.DONE)
        }
        return StoryboardScene(
            kind = SceneKind.MAX_AI,
            labelKey = "scene.max_ai",
            appLabel = null,
            lines = orderLines(lines).take(MAX_LINES_PER_SCENE),
            atMs = null,
        )
    }

    /** مشهد التحكّم اليدوي: ما لمسته بيدك الآن، من كذا إلى كذا. */
    fun manualScene(items: List<ManualSceneInput>): StoryboardScene? {
        if (items.isEmpty()) return null
        val lines = items.map { item ->
            StoryLine(
                knob = item.knob,
                from = item.from,
                to = item.to,
                tone = if (item.verified) LineTone.DONE else LineTone.FAILED,
                reason = item.reason.takeIf { !item.verified },
            )
        }
        val ordered = orderLines(lines)
        if (ordered.isEmpty()) return null
        return StoryboardScene(
            kind = SceneKind.MANUAL,
            labelKey = "scene.manual",
            appLabel = null,
            lines = ordered.take(MAX_LINES_PER_SCENE),
            atMs = null,
        )
    }

    /**
     * ترتيب المشاهد: التطبيق أولًا (الأكثر تحديدًا: اسم تطبيق ونتائج مقيسة)، ثم الذكاء،
     * ثم تحكّمك اليدوي. والحدّ الأعلى يُطبَّق هنا لا في الواجهة، فلا تُبنى مشاهد تُطرح بعدها.
     */
    fun storyboard(scenes: List<StoryboardScene?>): List<StoryboardScene> = scenes
        .filterNotNull()
        .filter { it.lines.isNotEmpty() }
        .sortedBy { scene -> listOf(SceneKind.PER_APP, SceneKind.MAX_AI, SceneKind.MANUAL).indexOf(scene.kind) }
        .take(MAX_SCENES)

    /**
     * ترتيب الصفوف داخل المشهد: الإخفاق ← ثم المحجوز ← ثم ما نُفِّذ ← ثم ما أُوقف عمدًا.
     * والثبات داخل كل طبقة (ترتيب المصدر) مقصود، فلا تقفز الصفوف بين تحديثين.
     */
    fun orderLines(lines: List<StoryLine>): List<StoryLine> = lines
        .distinctBy { it.knob }
        .sortedBy { line ->
            when (line.tone) {
                LineTone.FAILED -> 0
                LineTone.HELD -> 1
                LineTone.DONE -> 2
                LineTone.OFF -> 3
            }
        }

    /** أسطر سياسات CPU من اختيارك — كل سياسة سطر، ولا يُكرَّر ما قِيس له سطر عتاد. */
    fun cpuPolicyLines(choices: List<CpuPolicyChoice>, measuredKnobs: Set<String>): List<StoryLine> =
        choices.mapNotNull { choice ->
            // المفتاح من `HardwareControlKey` لا نصًّا مكتوبًا هنا: صيغتان للمقبض نفسه تفصلان
            // السطر عن نتيجة العتاد التي كُتبت بالمفتاح القانوني، فيظهر السطر مرّتين أو لا يظهر.
            val knob = HardwareControlKey.cpuLimits(choice.policyName)
            if (knob in measuredKnobs) return@mapNotNull null
            StoryLine(knob, null, "${choice.label} ${readableRange(choice.minKHz, choice.maxKHz)}", LineTone.DONE)
        }

    /** اختيارك للحاكم أو لسقف تردّد الرسوم — سطر «مُختار» لا يلبس ثوب المقيس. */
    private fun governorAndFrequencyLines(input: PerAppSceneInput, measuredKnobs: Set<String>): List<StoryLine> =
        listOf(
            "cpu_governor" to input.cpuGovernor,
            "gpu_governor" to input.gpuGovernor,
            "gpu_max_freq" to input.gpuMaxFreq,
        ).mapNotNull { (knob, value) ->
            if (knob in measuredKnobs) return@mapNotNull null
            val chosen = value.takeIf { it.isNotBlank() && it != "default" } ?: return@mapNotNull null
            StoryLine(knob, null, if (knob == "gpu_max_freq") readableValue(chosen) else chosen, LineTone.DONE)
        }

    fun readableRange(minKHz: Long, maxKHz: Long): String {
        val min = readableValue(minKHz.toString())
        val max = readableValue(maxKHz.toString())
        return when {
            min == max -> min
            minKHz <= 0L -> max
            else -> "$min \u2013 $max"
        }
    }

    /**
     * قيمة التردد كما يقرؤها إنسان: `1300000000` (هرتز) و`1800000` (كيلوهرتز) كلتاهما تردّد
     * واحد المعنى، وعرضهما كأرقام خام يجعل السطر يُقرأ كمعرّف لا كسرعة.
     *
     * والقاعدة **تحويل صيغة لا اختراع رقم**: المجموعة العشرية هي كل ما يُضاف، وما لا يمثّل
     * تردّدًا (اسم حاكم، `on`، نصّ) يُعاد كما هو بلا لمس.
     */
    fun readableValue(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return raw

        // Kernel CPU policy values often arrive as `min:max`. Convert each endpoint
        // instead of leaking the raw kHz/Hz payload into the home card.
        if (value.contains(':')) {
            val parts = value.split(':')
            if (parts.size == 2 && parts.all { it.trim().all(Char::isDigit) }) {
                val left = readableValue(parts[0].trim())
                val right = readableValue(parts[1].trim())
                return "$left–$right"
            }
        }

        if (!value.all(Char::isDigit)) return raw
        val number = value.toLongOrNull() ?: return raw
        val mhz = when (value.length) {
            in 9..12 -> number / 1_000_000L      // Hz → MHz
            in 5..7 -> number / 1_000L           // kHz → MHz
            else -> return raw
        }
        return when {
            mhz >= 1_000L -> String.format(java.util.Locale.US, "%.2f GHz", mhz / 1_000.0)
            else -> "$mhz MHz"
        }
    }

    private fun refreshAndRenderer(input: PerAppSceneInput, measuredKnobs: Set<String>): List<StoryLine> = buildList {
        if (input.refreshRate.isNotBlank() && input.refreshRate != "default" && "refresh_rate" !in measuredKnobs) {
            add(StoryLine("refresh_rate", null, input.refreshRate, LineTone.DONE))
        }
        if (input.renderer.isNotBlank() && input.renderer != "default" && "renderer" !in measuredKnobs) {
            add(StoryLine("renderer", null, input.renderer, LineTone.DONE))
        }
    }
}
