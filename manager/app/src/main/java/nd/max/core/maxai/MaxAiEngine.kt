package nd.max.core.maxai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nd.max.MaxManagerProps
import nd.max.core.diagnostics.DiagnosticCenter
import nd.max.core.hardware.AndroidContextDataSource
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.DeviceStateCollector
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.ProfileApplier
import nd.max.core.hardware.RootFileAccess
import nd.max.core.jni.ContextBridge
import nd.max.core.jni.PredictorBridge
import nd.max.core.recommendation.RecommendationAction
import nd.max.core.recommendation.RecommendationTextClassifier
import nd.max.ui.util.EventLog
import nd.max.ui.util.PropertyUtils
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MAX AI PERFORMANCE ENGINE — المحرك الذكي الموحد.
 *
 * يحل محل المحركات المتفرقة السابقة (توصيات/تنبؤ/تعلم) بشركة واحدة:
 * كشف → قرار → فحص أمان → تنفيذ → قياس النتيجة → مكافأة حقيقية.
 *
 * المبادئ الملزِمة (مواصفة المالك):
 *  - الإعداد الافتراضي: مطفأ، تحكم يدوي. لا تفعيل ذاتي إطلاقًا.
 *  - عند التفعيل: الملفات مقفلة، والتعديل اليدوي المتعارض يُحفظ
 *    "معلقًا" ويُطبق لحظة الإيقاف — لا يضيع شيء.
 *  - تطبيق بملف خاص في المقدمة → APP PROFILE يملك، والمحرك يراقب
 *    (قياس + تغذية التوأم + أمان) دون تدخل.
 *  - الأمان فوق الجميع دائمًا — حتى والمحرك مطفأ.
 *  - كل قرار يُنفَّذ عبر نقطة تحكم واحدة (المُحكِّم) ثم تُقاس نتيجته
 *    الفعلية؛ لا قرار بلا تنفيذ، ولا مكافأة بلا قياس.
 *  - الشاشة مطفأة → لا تدخل (مبدأ FDE.AI)، والدورة بطيئة (~30 ث)
 *    كي لا يتأرجح التحكم ولا يُستنزف قيدًا.
 *  - الواجهة ترى أعدادًا حقيقية فقط: قرارات/ناجحة/معدلة/محجوبة
 *    للأمان/خطوات تعلم — لا ثقة مصطنعة ولا عشوائية.
 */
@Singleton
class MaxAiEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val contextDataSource: AndroidContextDataSource,
    private val arbiter: HardwareControlArbiter,
    private val ceilingKnobs: CpuCeilingKnobs,
    private val safetyEngine: SafetyEngine,
    private val pendingStore: PendingManualStore,
) {
    companion object {
        private const val TAG = "MaxAiEngine"

        /** ملكية المحرك في المُحكِّم الموحد. */
        const val TOKEN = "max-ai"

        /** دورة المراقبة: بطيئة عمدًا (مبدأ FDE.AI — تحكم لا يتأرجح). */
        private const val CYCLE_MS = 30_000L

        /** مهلة استجابة النظام بين التنفيذ وقياس النتيجة. */
        private const val RESPONSE_WINDOW_MS = 10_000L

        /** أفق التنبؤ الحراري الأمامي لمحرك الأمان (خطوة = دورة). */
        private const val THERMAL_FORECAST_STEPS = 6

        /**
         * إحماء التدريب: حتى يُجرَّب كل إجراء [TRIALS_PER_ACTION] مرة
         * يختار الوكيل الأقل تجربةً (حتمي)، ثم يتحول إلى argmax خالص.
         */
        private const val TRIALS_PER_ACTION = 8L
        private const val WARMUP_STEPS = 6L * TRIALS_PER_ACTION

        /** سقف خفض التردد عند قرار "خفض التردد" (نسبة مدى العتاد). */
        private const val CAP_FRACTION = 0.6f

        private const val PREFS = "maxai_engine"
        private const val PREF_DECISIONS = "decisions"
        private const val PREF_SUCCESSFUL = "successful"
        private const val PREF_ADJUSTED = "adjusted"
        private const val PREF_BLOCKED = "blocked"

        /** ملف حالة الرفيق الذي يكتبه AppMonitor (نفس مسار service.sh). */
        private const val APP_STATUS_PATH = "/data/adb/.config/MaxManager/app_status"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(MaxAiState())
    val state: StateFlow<MaxAiState> = _state.asStateFlow()

    val safety: StateFlow<SafetyStatus> = safetyEngine.status

    private val _profileRequest = MutableStateFlow(ProfileRequestState())
    val profileRequest: StateFlow<ProfileRequestState> = _profileRequest.asStateFlow()

    private val cycleMutex = Mutex()
    private val requestedGeneration = AtomicLong(0L)

    /** آخر ملف طبّقه المحرك — يمنع إعادة تطبيق نفس الملف كل دورة. */
    @Volatile private var lastAppliedProfile: String? = null

    /** التعديلات المعلقة المخبأة للعرض الفوري (المصدر: PendingManualStore). */
    @Volatile private var cachedPending: List<PendingManualChange> = emptyList()

    @Volatile private var started = false

    // ── دورة الحياة ──────────────────────────────────────────────────

    /**
     * يبدأ المحرك عند إقلاع التطبيق (مرة واحدة، بلا توقف): حلقة واحدة
     * دائمة بطيئة. عندما يكون AI مطفأً تكتفي الدورة بالأمان والنشر —
     * صفر قرارات وصفر كتابات على العتاد.
     */
    fun start() {
        if (started) return
        started = true

        // استمرارية المعرفة: تحميل نموذج الوكيل المحفوظ إن وُجد.
        val modelPath = File(appContext.filesDir, "maxai_rl_model.json").absolutePath
        val restored = PredictorBridge.initRLAgent(modelPath)
        if (restored) {
            EventLog.userAction("MaxAiEngine", "rl_model", "fresh", "restored")
        }

        scope.launch {
            // تحميل التعديلات المعلقة المحفوظة من جلسة سابقة.
            cachedPending = runCatching { pendingStore.all() }.getOrDefault(emptyList())
            while (isActive) {
                runCatching { runCycleSingleFlight() }
                    .onFailure {
                        Log.w(TAG, "engine cycle failed", it)
                        DiagnosticCenter.record(
                            "maxai", "engine cycle failed: ${it.message ?: it.javaClass.simpleName}"
                        )
                    }
                delay(CYCLE_MS)
            }
        }
        EventLog.userAction("MaxAiEngine", "engine", "stopped", "started")
    }

    // ── الدورة الواحدة ───────────────────────────────────────────────

    private suspend fun runCycleSingleFlight() {
        val requested = requestedGeneration.incrementAndGet()
        cycleMutex.withLock {
            var handled = requested - 1L
            while (handled < requestedGeneration.get()) {
                handled = requestedGeneration.get()
                cycle()
            }
        }
    }

    private suspend fun cycle() = withContext(Dispatchers.IO) {
        val aiEnabled = readAiEnabled()

        // 1) كشف: القياس المشترك الموحد (يغذي المتنبئ والتوأم أيضًا).
        val snapshot = runCatching { DeviceStateCollector.collect(appContext) }.getOrNull()
        if (snapshot != null) {
            PredictorBridge.updateDigitalTwin(
                snapshot.cpuLoad, snapshot.thermal, snapshot.battery, snapshot.appIntent,
                snapshot.screenOn, snapshot.memoryUsage, snapshot.networkSpeed
            )
        }

        // 2) الأمان أولًا ودائمًا — قبل أي قرار ومن فوق أي مالك.
        val thermalC = (snapshot?.thermal ?: 0f) * 100f
        val predictedC = PredictorBridge.predictThermal(THERMAL_FORECAST_STEPS)?.maxOrNull()
        val safetyNow = safetyEngine.evaluate(thermalC, predictedC)

        // 3) AI مطفأ: تحكم يدوي. لا قرارات. لو بقيت تعديلات معلقة من
        //    جلسة AI سابقة تُطبق الآن — عودة السيادة للمستخدم.
        if (!aiEnabled) {
            applyPendingManualChangesIfAny()
            publish(
                aiEnabled = false,
                controller = manualController(safetyNow),
                snapshot = snapshot,
            )
            return@withContext
        }

        // 4) تطبيق بملف خاص في المقدمة؟ APP PROFILE يملك والمحرك يراقب
        //    فقط: لا قرار ولا كتابة — قياس وتوأم وأمان حسب ترتيب الأولوية.
        val appProfileActive = readAppProfileActive()
        if (appProfileActive) {
            publish(aiEnabled = true, controller = MaxAiController.APP_PROFILE, snapshot = snapshot)
            return@withContext
        }

        // 5) شاشة مطفأة: لا تدخل (مبدأ FDE.AI) وتحرير سقوف AI.
        if (snapshot == null || snapshot.screenOn < 0.5f) {
            ceilingKnobs.leaveAll(TOKEN)
            publish(aiEnabled = true, controller = MaxAiController.MAX_AI, snapshot = snapshot)
            return@withContext
        }

        // 6) القرار → فحص الأمان → التنفيذ → قياس النتيجة → المكافأة.
        decisionCycle(snapshot, safetyNow)
    }

    private suspend fun decisionCycle(
        before: DeviceStateCollector.DeviceSnapshot,
        safetyNow: SafetyStatus,
    ) = withContext(Dispatchers.IO) {
        val decision = decide(before)
        val label = decision.label
        val reason = decision.reason

        if (label == null || label == "لا شيء") {
            // قرار تدريبي مفتوح ("لا شيء") لن يُنفَّذ — يُنسى فورًا حتى
            // لا تُعزى مكافأة دورة قادمة إلى فعلٍ لم يحدث.
            PredictorBridge.forgetPendingTransition()
            publish(aiEnabled = true, controller = controllerFor(safetyNow), snapshot = before)
            return@withContext
        }

        // فحص الأمان قبل التنفيذ: أثناء تدخل حراري، أي قرار يرفع
        // الأداء يُحجب (الأمان فوق AI)؛ قرارات الخفض تمر لأنها تعينه.
        if (safetyNow.engaged && isPerformanceRaising(label)) {
            // الفعل المحجوب لم يحدث — انتقال التدريب المفتوح (إن وُجد)
            // يُنسى: لا تعلم من إجراء منعه الأمان.
            PredictorBridge.forgetPendingTransition()
            bumpCounter(PREF_BLOCKED)
            EventLog.userAction(
                "MaxAiEngine", "decision", label,
                "blocked_for_safety@${safetyNow.thermalC.toInt()}C"
            )
            publish(
                aiEnabled = true,
                controller = MaxAiController.SAFETY_OVERRIDE,
                snapshot = before,
            ) {
                copy(lastDecision = DecisionRecord(label, System.currentTimeMillis(), DecisionResult.BLOCKED_FOR_SAFETY, reason))
            }
            return@withContext
        }

        val outcome = executeDecision(label)

        if (outcome.executed) {
            // مهلة استجابة ثم قياس النتيجة الفعلية — هذه هي حلقة
            // التعلم الحقيقية: إجراء نُفِّذ فعلًا ثم قياس أثره.
            delay(RESPONSE_WINDOW_MS)
            val after = runCatching { DeviceStateCollector.collect(appContext) }.getOrNull()
            if (after != null) {
                if (decision.fromAgentTraining) {
                    // الإجراء صادر عن مسار التدريب: تُغلق المكافأة المقيسة
                    // الانتقال المفتوح المطابق لهذا الفعل بالضبط.
                    val reward = RewardCalculator.compute(before, after, executed = true)
                    PredictorBridge.submitMeasuredReward(
                        reward,
                        after.cpuLoad, after.thermal, after.battery, after.appIntent,
                        after.screenOn, after.memoryUsage, after.networkSpeed
                    )
                } else {
                    // إجراء قواعدي (خارج الوكيل): يُقاس ويُعدّ ولا يُدرَّس —
                    // الوكيل يتعلم من قراراته هو فقط.
                    PredictorBridge.forgetPendingTransition()
                }
            } else {
                // تعذّر قياس النتيجة بعد التنفيذ: الانتقال يُنسى بصدق —
                // لا مكافأة من عدم، ولا تعلم من قياس غير موجود.
                PredictorBridge.forgetPendingTransition()
            }
            bumpCounter(PREF_DECISIONS)
            when (outcome.result) {
                DecisionResult.ADJUSTED -> bumpCounter(PREF_ADJUSTED)
                DecisionResult.VERIFIED, DecisionResult.EXECUTED -> bumpCounter(PREF_SUCCESSFUL)
                else -> {}
            }
            EventLog.userAction(
                "MaxAiEngine", "decision", "pending",
                "$label:${outcome.result.name.lowercase()}"
            )
            publish(
                aiEnabled = true,
                controller = controllerFor(safetyNow),
                snapshot = after ?: before,
            ) {
                copy(
                    lastDecision = DecisionRecord(
                        label, System.currentTimeMillis(), outcome.result,
                        "$reason :: ${outcome.detail}"
                    )
                )
            }
        } else {
            // لم يُنفَّذ (الملف هو الحالي فعلًا، أو فشل): بلا عداد نجاح
            // وبلا مكافأة — الانتقال المفتوح يُنسى كي لا تُعزى مكافأة
            // قادمة إلى فعلٍ لم يحدث، والفاشل يُعدّ ويُسجَّل للتشخيص.
            PredictorBridge.forgetPendingTransition()
            if (outcome.result == DecisionResult.FAILED) {
                bumpCounter(PREF_DECISIONS)
                DiagnosticCenter.record(
                    "maxai",
                    "decision failed: $label :: ${outcome.detail}"
                )
            }
            publish(
                aiEnabled = true,
                controller = controllerFor(safetyNow),
                snapshot = before,
            ) {
                copy(
                    lastDecision = DecisionRecord(
                        label, System.currentTimeMillis(), outcome.result,
                        "$reason :: ${outcome.detail}"
                    )
                )
            }
        }
    }

    // ── القرار ───────────────────────────────────────────────────────

    /** قرار واحد مع مصدره: الوكيل يتعلم من أفعاله هو فقط. */
    private data class Decision(
        val label: String?,
        val reason: String,
        /** القرار صادر عن مسار التدريب (ينتظر مكافأة مقيسة). */
        val fromAgentTraining: Boolean,
    )

    /**
     * مصدر القرار الواحد: الوكيل المتعلم إن اكتمل إحماؤه، وإلا فإجراء
     * تدريب حتمي (الأقل تجربةً)، وإن قرر "لا شيء" تُستشار القواعد
     * المبنية على السياق المقيس. كل المسارات حتمية.
     */
    private suspend fun decide(
        snapshot: DeviceStateCollector.DeviceSnapshot,
    ): Decision = withContext(Dispatchers.IO) {
        val measured = "حمل ${(snapshot.cpuLoad * 100).toInt()}%، حرارة " +
            "${(snapshot.thermal * 100).toInt()}°م، بطارية ${(snapshot.battery * 100).toInt()}%"

        val rlState = PredictorBridge.rlState
        val underTrained = PredictorBridge.nativeAvailable &&
            (rlState == null || rlState.steps < WARMUP_STEPS ||
                rlState.actionCounts.any { it < TRIALS_PER_ACTION })

        val agentLabel = if (underTrained) {
            PredictorBridge.selectTrainingAction(
                snapshot.cpuLoad, snapshot.thermal, snapshot.battery, snapshot.appIntent,
                snapshot.screenOn, snapshot.memoryUsage, snapshot.networkSpeed
            )
        } else {
            PredictorBridge.policyDecision(
                snapshot.cpuLoad, snapshot.thermal, snapshot.battery, snapshot.appIntent,
                snapshot.screenOn, snapshot.memoryUsage, snapshot.networkSpeed
            )
        }

        if (agentLabel != "لا شيء") {
            val src = if (underTrained) "تدريب حتمي" else "السياسة المتعلمة"
            return@withContext Decision(agentLabel, "[$src] $measured", fromAgentTraining = underTrained)
        }

        // الوكيل لا يرى حاجة للتدخل (وسياسة argmax لا تفتح انتقالًا) —
        // هل ترى القواعد حاجة؟ (سياق مقيس). أفعال القواعد خارج الوكيل:
        // تُنفَّذ وتُقاس لكنها لا تُدرَّس للوكيل (لا يتعلم إلا من قراراته).
        val context = runCatching { contextDataSource.getCurrentContext() }.getOrNull()
        if (context == null) return@withContext Decision(null, measured, fromAgentTraining = false)

        val recs = runCatching {
            ContextBridge.generateRecommendations(
                context.foregroundPackage, context.isScreenOn, context.ambientLightLux,
                context.audioVolumePercent, context.isCharging, context.batteryLevel,
                context.cpuLoadAvg, context.thermalZoneMax
            )
        }.getOrDefault(emptyArray())

        for (rec in recs) {
            val action = RecommendationTextClassifier.classify(rec)
            if (action != RecommendationAction.NoAction &&
                action !in setOf(
                    RecommendationAction.RebootDevice,
                    RecommendationAction.SuggestCharging,
                )
            ) {
                return@withContext Decision(
                    labelFor(action), "[قواعد السياق: $rec] $measured",
                    fromAgentTraining = false
                )
            }
        }
        Decision(null, measured, fromAgentTraining = false)
    }

    private fun labelFor(action: RecommendationAction): String = when (action) {
        RecommendationAction.ApplyPerformanceProfile -> "ملف الأداء"
        RecommendationAction.ApplyPowerSaveProfile -> "ملف توفير الطاقة"
        RecommendationAction.ApplyBalancedProfile -> "متوازن"
        RecommendationAction.IncreaseCpuFrequency -> "رفع التردد"
        RecommendationAction.ReduceCpuFrequency -> "خفض التردد"
        RecommendationAction.EnableGamingMode -> "وضع الألعاب"
        RecommendationAction.DisableGamingMode -> "خروج من الألعاب"
        RecommendationAction.SuggestClosingApps -> "إغلاق الخلفية"
        else -> "لا شيء"
    }

    /** هل القرار يرفع الأداء؟ (يُحجب أثناء تدخل الأمان) */
    private fun isPerformanceRaising(label: String): Boolean =
        label == "رفع التردد" || label == "ملف الأداء" || label == "وضع الألعاب"

    // ── التنفيذ (نقطة التحكم الموحدة) ────────────────────────────────

    private data class Outcome(val executed: Boolean, val result: DecisionResult, val detail: String)

    private fun executeDecision(label: String): Outcome {
        return when (label) {
            "رفع التردد" -> {
                val o = ceilingKnobs.release(ControlOwnership.Owner.MAX_AI, TOKEN)
                outcomeFromKnobs(o)
            }
            "خفض التردد" -> {
                val o = ceilingKnobs.cap(CAP_FRACTION, ControlOwnership.Owner.MAX_AI, TOKEN)
                outcomeFromKnobs(o)
            }
            "ملف الأداء" -> applyProfileIfChanged(ProfileApplier.PROFILE_PERFORMANCE, label)
            "ملف توفير الطاقة" -> applyProfileIfChanged(ProfileApplier.PROFILE_ECO, label)
            "متوازن" -> applyProfileIfChanged(ProfileApplier.PROFILE_BALANCED, label)
            "وضع الألعاب" -> {
                val boost = submitBoost(true)
                val profile = applyProfileIfChanged(ProfileApplier.PROFILE_PERFORMANCE, label)
                Outcome(
                    executed = profile.executed,
                    result = if (profile.result == DecisionResult.VERIFIED && boost) DecisionResult.VERIFIED
                    else if (profile.executed) DecisionResult.ADJUSTED else profile.result,
                    detail = "profile=${profile.detail}; boost=$boost"
                )
            }
            "خروج من الألعاب" -> {
                val boost = submitBoost(false)
                val profile = applyProfileIfChanged(ProfileApplier.PROFILE_BALANCED, label)
                Outcome(
                    executed = profile.executed,
                    result = if (profile.result == DecisionResult.VERIFIED && boost) DecisionResult.VERIFIED
                    else if (profile.executed) DecisionResult.ADJUSTED else profile.result,
                    detail = "profile=${profile.detail}; boost=$boost"
                )
            }
            "إغلاق الخلفية" -> {
                val r = Shell.cmd("am kill-all").exec()
                Outcome(r.isSuccess, if (r.isSuccess) DecisionResult.VERIFIED else DecisionResult.FAILED, "am kill-all")
            }
            else -> Outcome(false, DecisionResult.SKIPPED, "لا إجراء")
        }
    }

    private fun outcomeFromKnobs(o: CpuCeilingKnobs.KnobOutcome): Outcome = when {
        o.failed == 0 && o.blocked == 0 && o.applied > 0 ->
            Outcome(true, DecisionResult.VERIFIED, o.detail)
        o.applied > 0 ->
            Outcome(true, DecisionResult.ADJUSTED, "${o.applied} سياسة، ${o.blocked} محجوزة: ${o.detail}")
        else ->
            Outcome(false, DecisionResult.FAILED, o.detail)
    }

    /** تطبيق ملف عبر مسار AI المصرَّح — ببوابة "هو الحالي أصلًا". */
    private fun applyProfileIfChanged(profileId: String, label: String): Outcome {
        val current = runCatching { ProfileApplier.currentProfile() }.getOrNull()
        if (current == profileId) {
            lastAppliedProfile = profileId
            return Outcome(false, DecisionResult.SKIPPED, "الملف الحالي هو $label بالفعل")
        }
        if (lastAppliedProfile == profileId && current == null) {
            return Outcome(false, DecisionResult.FAILED, "تعذّر تأكيد الملف الحالي")
        }
        val ok = ProfileApplier.applyFromAi(profileId)
        lastAppliedProfile = if (ok) profileId else lastAppliedProfile
        return if (ok) {
            Outcome(true, DecisionResult.VERIFIED, "تم تطبيق $label عبر خدمة الوحدة (مسار AI)")
        } else {
            Outcome(false, DecisionResult.FAILED, "فشل تطبيق $label عبر خدمة الوحدة")
        }
    }

    /** تفعيل/تعطيل cpufreq boost عبر المُحكِّم (لا كتابة مباشرة). */
    private fun submitBoost(enabled: Boolean): Boolean {
        val node = CpuHardwareBackend.boostNode() ?: return false
        val desired = if (enabled) "1" else "0"
        val result = arbiter.submit(
            key = "cpu_boost",
            owner = ControlOwnership.Owner.MAX_AI,
            token = TOKEN,
            desired = desired,
            apply = { value -> CpuHardwareBackend.setBoost(value == "1").successful },
            read = { nd.max.core.hardware.RootFileAccess.read(node)?.trim() },
            baseline = if (enabled) "0" else "1",
            restore = { value -> CpuHardwareBackend.setBoost(value == "1").successful },
        )
        return result.verified
    }

    // ── واجهة المستخدم/الخدمات ───────────────────────────────────────

    /**
     * دورة فورية عند الطلب (دخول الشاشة مثلًا) كي تعكس الحالة القياسات
     * الحالية بلا انتظار دورة الثلاثين ثانية القادمة.
     */
    suspend fun requestRefresh() = withContext(Dispatchers.IO) {
        runCatching { runCycleSingleFlight() }
    }

    /**
     * تبديل Max AI. عند الإيقاف: تُطبق التعديلات اليدوية المعلقة فورًا
     * (عودة السيادة للمستخدم) وتُترك سقوف المحرك باسترجاع الأساس.
     */
    fun setAiEnabled(enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            val old = readAiEnabled()
            PropertyUtils.set(MaxManagerProps.Conf.AI_ENABLED, if (enabled) "1" else "0")
            Shell.cmd(
                "echo ${if (enabled) "1" else "0"} > /data/adb/.config/MaxManager/API/current_modes"
            ).submit()
            EventLog.userAction(
                "MaxAiEngine", "master_switch",
                if (old) "on" else "off", if (enabled) "on" else "off"
            )
            if (!enabled) {
                applyPendingManualChangesIfAny()
                ceilingKnobs.leaveAll(TOKEN)
            } else {
                lastAppliedProfile = null
            }
            runCatching { runCycleSingleFlight() }
        }
    }

    /** ينفذ الطلب على IO ويعيد true فقط عند تطبيق الملف الآن بنجاح. */
    suspend fun requestManualProfile(profileId: String, label: String): Boolean =
        withContext(Dispatchers.IO) {
            if (_profileRequest.value.inFlight) return@withContext false
            _profileRequest.value = ProfileRequestState(profileId = profileId, inFlight = true)
            try {
                if (readAiEnabled()) {
                    pendingStore.add(
                        PendingManualChange(
                            PendingManualStore.KEY_PROFILE, profileId, label,
                            System.currentTimeMillis()
                        )
                    )
                    cachedPending = runCatching { pendingStore.all() }.getOrDefault(cachedPending)
                    EventLog.userAction("MaxAiEngine", "manual_profile", "executed", "pending:$profileId")
                    publish(aiEnabled = true, controller = MaxAiController.MAX_AI)
                    _profileRequest.value = ProfileRequestState(profileId, false, DecisionResult.SKIPPED)
                    false
                } else {
                    val ok = ProfileApplier.apply(profileId)
                    EventLog.userAction(
                        "MaxAiEngine", "manual_profile", "pending",
                        "$profileId:${if (ok) "ok" else "failed"}"
                    )
                    _profileRequest.value = ProfileRequestState(
                        profileId, false,
                        if (ok) DecisionResult.VERIFIED else DecisionResult.FAILED
                    )
                    runCatching { runCycleSingleFlight() }
                    ok
                }
            } catch (t: Throwable) {
                _profileRequest.value = ProfileRequestState(profileId, false, DecisionResult.FAILED)
                throw t
            }
        }

    private suspend fun applyPendingManualChangesIfAny() {
        val pending = pendingStore.pendingProfile() ?: return
        val ok = ProfileApplier.apply(pending.value)
        EventLog.userAction(
            "MaxAiEngine", "pending_applied", "queued",
            "${pending.label}:${if (ok) "ok" else "failed"}"
        )
        if (ok) {
            pendingStore.clear()
            cachedPending = emptyList()
        }
    }

    // ── قراءات الحالة ────────────────────────────────────────────────

    fun readAiEnabled(): Boolean = runCatching {
        PropertyUtils.get(MaxManagerProps.Conf.AI_ENABLED, "0") == "1"
    }.getOrDefault(false)

    /**
     * هل تطبيق بملف خاص يملك العتاد الآن؟ يُقرأ من ملف الحالة الذي
     * يكتبه رفيق الوحدة (AppMonitor) — قيمة فعلية لا مؤقت.
     */
    private fun readAppProfileActive(): Boolean = runCatching {
        RootFileAccess.read(APP_STATUS_PATH)
            ?.lineSequence()
            ?.any { it.trim() == "perapp_active 1" }
            ?: false
    }.getOrDefault(false)

    private fun manualController(safetyNow: SafetyStatus): MaxAiController =
        if (safetyNow.engaged) MaxAiController.SAFETY_OVERRIDE else MaxAiController.MANUAL

    private fun controllerFor(safetyNow: SafetyStatus): MaxAiController =
        if (safetyNow.engaged) MaxAiController.SAFETY_OVERRIDE else MaxAiController.MAX_AI

    // ── العدادات والنشر ──────────────────────────────────────────────

    private fun bumpCounter(key: String) {
        val v = prefs.getLong(key, 0L) + 1L
        prefs.edit().putLong(key, v).apply()
    }

    /**
     * ينشر الحالة من قيم محسوبة فعلًا: قراءات الدورة، عدادات دائمة،
     * حالة الأمان، والتعديلات المعلقة المخبأة. القراءات الجذرية تتم
     * خارج دالة النشر نفسها (بلا تأثيرات جانبية داخل تحديث الحالة).
     */
    private fun publish(
        aiEnabled: Boolean,
        controller: MaxAiController,
        snapshot: DeviceStateCollector.DeviceSnapshot? = null,
        mutate: MaxAiState.() -> MaxAiState = { this },
    ) {
        val prev = _state.value
        val safetyNow = safetyEngine.status.value
        val profile = runCatching { ProfileApplier.currentProfile() }.getOrNull()

        val strategy = when {
            !aiEnabled -> "يدوي — المستخدم يتحكم"
            controller == MaxAiController.APP_PROFILE -> "ملف التطبيق يدير — مراقبة"
            controller == MaxAiController.SAFETY_OVERRIDE -> "أمان حراري — سقف آمن"
            lastAppliedProfile != null -> "ملف ${profileLabel(lastAppliedProfile!!)}"
            prev.lastDecision != null -> prev.lastDecision!!.label
            else -> "جارٍ التقييم"
        }

        _state.value = MaxAiState(
            aiEnabled = aiEnabled,
            controller = controller,
            strategyLabel = strategy,
            lastDecision = prev.lastDecision,
            safety = safetyNow,
            totalDecisions = prefs.getLong(PREF_DECISIONS, 0L),
            successfulDecisions = prefs.getLong(PREF_SUCCESSFUL, 0L),
            adjustedDecisions = prefs.getLong(PREF_ADJUSTED, 0L),
            blockedForSafety = prefs.getLong(PREF_BLOCKED, 0L),
            rlSteps = PredictorBridge.rlState?.steps ?: 0L,
            cpuLoadPercent = snapshot?.let { (it.cpuLoad * 100).toInt() } ?: prev.cpuLoadPercent,
            thermalC = snapshot?.let { it.thermal * 100f } ?: safetyNow.thermalC,
            batteryPercent = snapshot?.let { (it.battery * 100).toInt() } ?: prev.batteryPercent,
            screenOn = snapshot?.let { it.screenOn >= 0.5f } ?: prev.screenOn,
            pendingChanges = cachedPending,
            currentProfile = profile,
        ).mutate()
    }

    private fun profileLabel(id: String): String = when (id) {
        ProfileApplier.PROFILE_PERFORMANCE -> "أداء"
        ProfileApplier.PROFILE_BALANCED -> "متوازن"
        ProfileApplier.PROFILE_ECO -> "توفير طاقة"
        else -> id
    }
}
