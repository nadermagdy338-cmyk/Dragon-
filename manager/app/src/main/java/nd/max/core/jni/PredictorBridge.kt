package nd.max.core.jni

import nd.max.core.diagnostics.DiagnosticCenter

/**
 * جسر JNI لمحرك MAX AI الأصلي (Rust).
 *
 * المكتبة تُبنى في CI عبر cargo-ndk وتُحقن في jniLibs — لكن يبقى
 * التحمل الآمن إلزاميًا: لا انهيار إذا غابت. البدائل الاحتياطية في
 * هذا الجسر **حتمية بالكامل** — لا عشوائية إطلاقًا (قاعدة المالك):
 * السياسة الاحتياطية جدول عتبات فوق القياسات الفعلية نفسها.
 *
 * عقد الوحدات (موثق لا مفترض):
 *  - الحالة السبعية لوكيل RL: كل المحاور في [0,1] (DeviceStateCollector).
 *  - updatePowerPredictor: cpu% (0-100)، حرارة °C، بطارية %.
 *  - predictThermal: خطوات مستقبلية بحرارة °C — تُستهلك من محرك الأمان.
 *  - القرارات تُعاد كتسميات عربية تطابق جدول فك MaxAiEngine.
 */
object PredictorBridge {

    /** هل تحميل libmaxmanager_native.so نجح؟ يُحسب مرة واحدة فقط. */
    val nativeAvailable: Boolean = runCatching {
        System.loadLibrary("maxmanager_native")
    }.isSuccess

    init {
        // السؤال الأول في أي تشخيص: هل المكتبة الأصلية تحمّلت أصلًا؟
        // بدون هذا السطر كان التصدير يخرج بلا إجابة — والمحرك يستمر
        // في وضع الاحتياط بصمت تام.
        if (!nativeAvailable) {
            DiagnosticCenter.record(
                "init",
                "libmaxmanager_native unavailable - MAX AI runs deterministic Kotlin fallback policy",
                level = DiagnosticCenter.Level.WARN
            )
        }
    }

    // ── الدوال الأصلية — الواجهة الجديدة الحتمية ────────────────────

    /** تهيئة الوكيل بمسار نمموذج دائم؛ يعيد true إذا حُمّل نموذج سابق. */
    private external fun nativeInitRLAgent(modelPath: String): Boolean

    /** إجراء التدريب (حتمي: الأقل تجربةً ثم argmax) — يفتح انتقالًا. */
    private external fun nativeSelectTrainingAction(state: FloatArray): String

    /** استشارة السياسة الصافية (argmax) — لا تسجيل، بلا تلويث للتدريب. */
    private external fun nativePolicyDecision(state: FloatArray): String

    /** يغلق الانتقال المفتوح بمكافأة مقاسة ويحدّث الأوزان ويحفظ. */
    private external fun nativeSubmitMeasuredReward(reward: Float, nextState: FloatArray)

    /** ينسى الانتقال المفتوح — قياس ما بعد التنفيذ تعذّر فلا يُتعلم منه. */
    private external fun nativeForgetPendingTransition()

    /** حالة الوكيل الحقيقية: خطوات/عدادات/مسار النموذج — لا ثقة مصطنعة. */
    private external fun nativeGetRLState(): String

    /** تسجيل قياسات حقيقية في تاريخ المتنبئ (حمل%، حرارة °C، بطارية%). */
    private external fun nativeUpdatePredictor(cpuLoad: Float, thermal: Float, battery: Float)

    /** تنبؤ حراري أمامي (°C) — نظرة محرك الأمان. فارغ قبل اكتمال النصاب. */
    private external fun nativePredictThermal(steps: Int): FloatArray

    private external fun nativeUpdateDigitalTwin(
        cpuLoad: Float, thermal: Float, battery: Float, appIntent: Float,
        screenOn: Float, memoryUsage: Float, networkSpeed: Float
    )

    private external fun nativeGetTwinAnalysis(): String

    // ── حالة الوكيل المنظورة من Kotlin ────────────────────────────────

    /** عدد تنفيذ كل إجراء من الوكيل الأصلي — null بلا مكتبة (لا تزييف). */
    data class RLState(val steps: Long, val actionCounts: List<Long>, val modelPath: String)

    @Volatile
    var rlState: RLState? = null
        private set

    // ── تهيئة الوكيل ──────────────────────────────────────────────────

    /**
     * تهيئة الوكيل مع الاستمرارية: النموذج يُحمَّل من مساره الدائم.
     * @return true إذا وُجد نموذج محفوظ (المعرفة عبر إعادة التشغيل).
     */
    fun initRLAgent(modelPath: String): Boolean {
        if (!nativeAvailable) return false
        return runCatching { nativeInitRLAgent(modelPath) }
            .onFailure { DiagnosticCenter.record("jni", "nativeInitRLAgent failed", it) }
            .getOrDefault(false)
            .also { refreshRlState() }
    }

    // ── القرار (حتمي في المسارين) ────────────────────────────────────

    /**
     * إجراء التدريب للحالة الحالية. المسار الأصلي حتمي (الأقل تجربةً
     * ثم argmax). البديل الاحتياطي: نفس السياسة القاعدية الحتمية —
     * بلا متعلم لا معنى للاستكشاف، لكن القرار يبقى من القياسات.
     */
    fun selectTrainingAction(
        cpuLoad: Float, thermal: Float, battery: Float, appIntent: Float,
        screenOn: Float, memoryUsage: Float, networkSpeed: Float
    ): String {
        if (!nativeAvailable) {
            return fallbackPolicy(
                cpuLoad, thermal, battery, appIntent, screenOn, memoryUsage, networkSpeed
            )
        }
        return runCatching {
            nativeSelectTrainingAction(
                floatArrayOf(cpuLoad, thermal, battery, appIntent, screenOn, memoryUsage, networkSpeed)
            )
        }.onFailure { DiagnosticCenter.record("jni", "nativeSelectTrainingAction failed", it) }
            .getOrDefault("لا شيء")
    }

    /**
     * استشارة السياسة المتعلمة. بلا متعلم: السياسة القاعدية الحتمية
     * فوق نفس القياسات — عتبات معلنة، لا عشوائية ولا ثقة زائفة.
     */
    fun policyDecision(
        cpuLoad: Float, thermal: Float, battery: Float, appIntent: Float,
        screenOn: Float, memoryUsage: Float, networkSpeed: Float
    ): String {
        if (!nativeAvailable) {
            return fallbackPolicy(
                cpuLoad, thermal, battery, appIntent, screenOn, memoryUsage, networkSpeed
            )
        }
        return runCatching {
            nativePolicyDecision(
                floatArrayOf(cpuLoad, thermal, battery, appIntent, screenOn, memoryUsage, networkSpeed)
            )
        }.onFailure { DiagnosticCenter.record("jni", "nativePolicyDecision failed", it) }
            .getOrDefault("لا شيء")
    }

    // ── المكافأة المقاسة ──────────────────────────────────────────────

    /**
     * يغلق الانتقال المفتوح بمكافأة مقاسة فعليًا (من RewardCalculator:
     * قياس قبل → تنفيذ حقيقي → قياس بعد → مكافأة محسوبة).
     * بلا متعلم: لا-op صادق — لا شيء يتعلم، والمحرك يعتمد العدادات
     * المحلية للحقيقة العددية في الواجهة.
     */
    fun submitMeasuredReward(
        reward: Float,
        cpuLoad: Float, thermal: Float, battery: Float, appIntent: Float,
        screenOn: Float, memoryUsage: Float, networkSpeed: Float
    ) {
        if (!nativeAvailable) return
        runCatching {
            nativeSubmitMeasuredReward(
                reward,
                floatArrayOf(cpuLoad, thermal, battery, appIntent, screenOn, memoryUsage, networkSpeed)
            )
        }.onFailure { DiagnosticCenter.record("jni", "nativeSubmitMeasuredReward failed", it) }
        refreshRlState()
    }

    /**
     * ينسى الانتقال التدريبي المفتوح — يُستدعى حين تعذّر قياس الحالة
     * التالية بعد تنفيذ الإجراء: تعلم من قياس غير موجود = تزييف.
     */
    fun forgetPendingTransition() {
        if (!nativeAvailable) return
        runCatching { nativeForgetPendingTransition() }
            .onFailure { DiagnosticCenter.record("jni", "nativeForgetPendingTransition failed", it) }
    }

    /** ينعش الحالة المنظورة للوكيل من الأصل — أعداد حقيقية فقط. */
    fun refreshRlState() {
        if (!nativeAvailable) return
        runCatching { nativeGetRLState() }
            .onSuccess { json -> rlState = parseRlState(json) }
            .onFailure { DiagnosticCenter.record("jni", "nativeGetRLState failed", it) }
    }

    private fun parseRlState(json: String): RLState? = runCatching {
        val obj = org.json.JSONObject(json)
        val counts = obj.optJSONArray("counts")?.let { arr ->
            (0 until arr.length()).map { i -> arr.optLong(i, 0L) }
        } ?: emptyList()
        RLState(
            steps = obj.optLong("steps", 0L),
            actionCounts = counts,
            modelPath = obj.optString("model", "")
        )
    }.getOrNull()

    // ── المتنبئ (تاريخ حقيقي لثلاثة محاور) ────────────────────────────

    /** تسجيل قياس حقيقي في تاريخ المتنبئ — يعمل دائمًا بلا أثر جانبي. */
    fun updatePowerPredictor(cpuLoad: Float, thermal: Float, battery: Float) {
        if (!nativeAvailable) return
        runCatching { nativeUpdatePredictor(cpuLoad, thermal, battery) }
            .onFailure { DiagnosticCenter.record("jni", "nativeUpdatePredictor failed", it) }
    }

    /**
     * تنبؤ حراري أمامي لمحرك الأمان. بلا مكتبة: null صادق — الأمان
     * يعمل على القيمة اللحظية فقط، بلا ادعاء تنبؤ غير موجود.
     */
    fun predictThermal(steps: Int): FloatArray? {
        if (!nativeAvailable) return null
        return runCatching { nativePredictThermal(steps) }
            .onFailure { DiagnosticCenter.record("jni", "nativePredictThermal failed", it) }
            .getOrNull()
    }

    // ── التوأم الرقمي (قيم مقيسة فقط) ────────────────────────────────

    fun updateDigitalTwin(
        cpuLoad: Float, thermal: Float, battery: Float, appIntent: Float,
        screenOn: Float, memoryUsage: Float, networkSpeed: Float
    ) {
        if (!nativeAvailable) return
        runCatching {
            nativeUpdateDigitalTwin(
                cpuLoad, thermal, battery, appIntent, screenOn, memoryUsage, networkSpeed
            )
        }.onFailure { DiagnosticCenter.record("jni", "nativeUpdateDigitalTwin failed", it) }
    }

    /** تحليل التوأم كـ JSON من قيم مقيسة؛ null بلا مكتبة (لا بديل مزيف). */
    fun getTwinAnalysis(): String? {
        if (!nativeAvailable) return null
        return runCatching { nativeGetTwinAnalysis() }
            .onFailure { DiagnosticCenter.record("jni", "nativeGetTwinAnalysis failed", it) }
            .getOrNull()
    }

    // ── السياسة الاحتياطية الحتمية ────────────────────────────────────
    //
    // جدول عتبات فوق القياسات الفعلية نفسها — لا عشوائية، لا ثقة
    // مصطنعة. الترتيب مقصود: الحرارة أولًا (مبدأ FDE.AI: الحرارة
    // دائمًا في الاعتبار)، ثم البطارية، ثم الحمل/النية.
    // العتبات بوحدات القياس المقيسة (thermal*100 = °C، cpuLoad*100 = %).

    private fun fallbackPolicy(
        cpuLoad: Float, thermal: Float, battery: Float, appIntent: Float,
        screenOn: Float, memoryUsage: Float, networkSpeed: Float
    ): String {
        val tempC = thermal * 100f
        val cpuPct = cpuLoad * 100f
        return when {
            screenOn < 0.5f -> "لا شيء" // شاشة مطفأة: لا تدخل — مبدأ FDE.AI
            tempC >= 46f -> "خفض التردد" // حرارة مرتفعة: خفض فوري
            battery <= 0.15f && appIntent < 0.5f -> "ملف توفير الطاقة"
            tempC >= 43f && cpuPct >= 70f -> "خفض التردد" // اتجاه حراري سلبي مع ضغط
            cpuPct >= 80f && appIntent >= 0.5f -> "رفع التردد" // ضغط حقيقي بلا حرارة
            cpuPct < 25f && appIntent < 0.3f -> "خفض التردد" // خمول مؤكد
            battery <= 0.2f -> "ملف توفير الطاقة"
            cpuPct >= 85f -> "ملف الأداء"
            else -> "لا شيء" // المنطقة المتوازنة: لا تدخل بلا سبب مقيس
        }
    }
}
