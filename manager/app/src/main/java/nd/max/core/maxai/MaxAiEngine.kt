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
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.DeviceStateCollector
import nd.max.core.hardware.HardwareCapabilityResolver
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.RootFileAccess
import nd.max.core.hardware.SharedHardwareOwnershipStore
import nd.max.core.hardware.ManualControlLocks
import nd.max.core.hardware.ProfileApplier
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import nd.max.core.jni.PredictorBridge
import nd.max.ui.util.EventLog
import nd.max.ui.util.PropertyUtils

/**
 * MAX AI PERFORMANCE ENGINE — المحرك الذكي الموحد.
 *
 * يحل محل المحركات المتفرقة السابقة (توصيات/تنبؤ/تعلم) بشركة واحدة:
 * كشف → قرار → فحص أمان → تنفيذ → قياس النتيجة → مكافأة حقيقية.
 *
 * المبادئ الملزِمة (مواصفة المالك):
 *  - الإعداد الافتراضي: مطفأ، تحكم يدوي. لا تفعيل ذاتي إطلاقًا.
 *  - عند التفعيل: المفردات هي مقابض العتاد المكتشفة فقط، وكل مقبض
 *    يملكه فائز واحد في دفتر الملكية المشترك — لا وضع تحكم عالمي ولا
 *    طابور تعديلات معلقة.
 *  - ملف الأداء العام يبقى خط أساس يدويًا (قرار #9 و#23) — يُطبَّق
 *    فورًا عبر خدمة الوحدة، ولا يتحول أبدًا إلى أمر للذكاء.
 *  - الأمان فوق الجميع دائمًا — حتى والمحرك مطفأ.
 *  - كل قرار يُنفَّذ عبر نقطة تحكم واحدة (المُحكِّم) ثم تُقاس نتيجته
 *    الفعلية؛ لا قرار بلا تنفيذ، ولا مكافأة بلا قياس.
 *  - الشاشة مطفأة → هدف مختلف (طاقة+حرارة)، والدورة بطيئة (~30 ث)
 *    كي لا يتأرجح التحكم ولا يُستنزف قيدًا.
 *  - الواجهة ترى أعدادًا حقيقية فقط: قرارات/ناجحة/معدلة/محجوبة
 *    للأمان/خطوات تعلم — لا ثقة مصطنعة ولا عشوائية.
 *
 * مرحلة الشرّافية (هذا التعديل): كان المحرك يقرر ويتعلّم جيدًا ثم **يرمي
 * كل تفكيره**: أربعة عدادات وسطر "آخر إجراء" يُستبدل بعد 30 ثانية.
 * فالمستخدم لا يملك أي وسيلة ليرى لماذا تغير شيء ولا ماذا ترتّب عليه.
 * الآن تُحفظ كل دورة قرار كحلقة كاملة في [MaxAiJournal] مع قياسات قبل/بعد،
 * وكل المرشحين وأسباب استبعادهم، والتنبؤ مقابل المقيس، وما تحرك في
 * التعلّم بعدها. لا يتغير القرار نفسه ولا تُضاف سلطة تحكم جديدة —
 * المُحكِّم والأمان ونماذج التعلّم هي نفسها؛ المضاف هو إمكانية التفسير.
 */
@Singleton
class MaxAiEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val arbiter: HardwareControlArbiter,
    private val ceilingKnobs: CpuCeilingKnobs,
    private val safetyEngine: SafetyEngine,
    private val safetyGovernor: SafetyGovernor,
    private val planner: MinimalPlanner,
    private val dynamicIntentLearner: DynamicIntentLearner,
    private val journal: MaxAiJournal,
) {
    companion object {
        private const val TAG = "MaxAiEngine"

        /** ملكية المحرك في المُحكِّم الموحد. */
        const val TOKEN = "max-ai"

        /** دورة المراقبة: بطيئة عمدًا (مبدأ FDE.AI — تحكم لا يتأرجح). */
        private const val CYCLE_MS = 30_000L
        private const val SAFETY_CYCLE_MS = 1_000L

        /** مهلة استجابة النظام بين التنفيذ وقياس النتيجة. */
        private const val RESPONSE_WINDOW_MS = 10_000L

        /** أفق التنبؤ الحراري الأمامي لمحرك الأمان (خطوة = دورة). */
        private const val THERMAL_FORECAST_STEPS = 6

        private const val PREFS = "maxai_engine"
        private const val PREF_DECISIONS = "decisions"
        private const val PREF_SUCCESSFUL = "successful"
        private const val PREF_ADJUSTED = "adjusted"
        private const val PREF_BLOCKED = "blocked"

        /** ملف حالة الرفيق الذي يكتبه AppMonitor (نفس مسار service.sh). */
        private const val APP_STATUS_PATH = "/data/adb/.config/MaxManager/app_status"

        /** طول شريط التطور المحفوظ في الذاكرة (~ساعة من دورات 30 ث). */
        private const val TREND_CAPACITY = 120

        /**
         * أقل مسافة زمنية بين حلقتي "فجوة بلا مرشح مؤهل".
         *
         * هذه الحالة تتكرر كل 30 ثانية ما دام السبب قائمًا، وتسجيلها كل
         * دورة كان سيغرق الدفتر بمائة حلقة متطابقة ويدفع القرارات الحقيقية
         * خارج السجل. تُسجل مرة كل خمس دقائق كإشارة حالة لا كسجل مستمر.
         */
        private const val NO_ACTION_MIN_INTERVAL_MS = 300_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(MaxAiState())
    val state: StateFlow<MaxAiState> = _state.asStateFlow()

    val safety: StateFlow<SafetyStatus> = safetyEngine.status

    /** دفتر الحلقات الحقيقية — مصدر الخط الزمني في الواجهة. */
    val episodes: StateFlow<List<MaxAiEpisode>> = journal.episodes

    private val _profileRequest = MutableStateFlow(ProfileRequestState())
    val profileRequest: StateFlow<ProfileRequestState> = _profileRequest.asStateFlow()

    private val cycleMutex = Mutex()
    private val requestedGeneration = AtomicLong(0L)

    @Volatile private var started = false

    /** سياق الدورة الأخيرة — يُنشر كي تفسر الواجهة الرقم المعروض. */
    private data class CycleContext(
        val objective: Objective,
        val objectiveSource: String,
        val appContextKey: String,
        val satisfaction: Float,
        val score: Float?,
    )

    @Volatile private var lastCycleContext: CycleContext? = null
    @Volatile private var lastNoActionAtMs = 0L

    private val trendSamples = ArrayDeque<MaxAiSample>()
    private val trendLock = Any()

    // ── دورة الحياة ───────────────────────────────────────

    /**
     * يبدأ المحرك عند إقلاع التطبيق (مرة واحدة، بلا توقف): حلقة واحدة
     * دائمة بطيئة. عندما يكون AI مطفأً تكتفي الدورة بالأمان والنشر —
     * صفر قرارات وصفر كتابات على العتاد.
     */
    fun start() {
        if (started) return
        started = true

        // الوكيل السياسي الأصلي تقاعد من القرار (قرار #17): التعلّم الآن
        // على مستوى المقابض في نواة Kotlin (ControlOutcomeModel +
        // ResponseModel). لا نحمل نموذجه — تحميل ملف لا يُقرأ هدر.
        // يبقى من طبقة Rust ما يخدم فعلًا: التنبؤ الحراري (predictThermal)
        // والتوأم الرقمي (updateDigitalTwin).

        scope.launch {
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
        scope.launch {
            while (isActive) {
                runCatching {
                    val snapshot = DeviceStateCollector.collect(appContext)
                    val predicted = PredictorBridge.predictThermal(THERMAL_FORECAST_STEPS)?.maxOrNull()
                    safetyEngine.evaluate(snapshot.thermal * 100f, snapshot, predicted)
                }.onFailure {
                    Log.w(TAG, "fast safety cycle failed", it)
                    DiagnosticCenter.record(
                        "safety", "fast cycle failed: ${it.message ?: it.javaClass.simpleName}"
                    )
                }
                delay(SAFETY_CYCLE_MS)
            }
        }
        EventLog.userAction("MaxAiEngine", "engine", "lifecycle", "started")
    }

    // ── الدورة الواحدة ────────────────────────────────────

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
        safetyEngine.evaluate(thermalC, snapshot, predictedC)

        // الهدف يُحسب حتى والمحرك مطفأ: درجة الرضا والأوزان وصف للحالة
        // المقيسة لا ناتج تدخل، فعرضها والمحرك مطفأ صادق ومفيد: يرى
        // المستخدم ماذا كان سيوازن لو فعّله.
        val appContextKey = currentAppContextKey()
        val preference = PropertyUtils.get(MaxManagerProps.Conf.AI_OBJECTIVE)
            .takeIf { it.isNotBlank() }
        val screenOn = (snapshot?.screenOn ?: 1f) >= 0.5f

        val objective: Objective
        val objectiveSource: String
        if (!screenOn) {
            // 3) الشاشة المطفأة هدف مختلف (طاقة+حرارة) لا "إيقاف" — أهم
            //    نافذة لإدارة الموارد لا تُهدر (قرار #11).
            objective = Objective.SCREEN_OFF
            objectiveSource = "screen_off"
        } else if (preference != null) {
            // ترتيب الأولوية (قرار #10): نية المستخدم الصريحة أولًا.
            objective = Objective.fromPreference(preference)
            objectiveSource = "user"
        } else if (snapshot != null) {
            objective = dynamicIntentLearner.dynamicObjective(
                snapshot,
                appContextKey,
                true,
                Objective.BALANCED,
            )
            objectiveSource = "learned"
        } else {
            objective = Objective.BALANCED
            objectiveSource = "learned"
        }

        val score = snapshot?.let { objective.score(it) }
        lastCycleContext = CycleContext(
            objective = objective,
            objectiveSource = objectiveSource,
            appContextKey = appContextKey,
            satisfaction = MinimalPlanner.SATISFIED_SCORE,
            score = score,
        )
        if (snapshot != null && score != null) {
            appendTrend(snapshot, score)
        }

        // 4) AI مطفأ: تحكم يدوي. لا قرارات ولا كتابات على العتاد.
        if (!aiEnabled) {
            publish(aiEnabled = false, snapshot = snapshot)
            return@withContext
        }

        // 5) لا لقطة ⇒ لا قرار (لا تدخل على قياس غائب).
        if (snapshot == null) {
            publish(aiEnabled = true, snapshot = null)
            return@withContext
        }

        // Observe with DynamicIntentLearner
        dynamicIntentLearner.observe(snapshot, appContextKey, screenOn)

        // 6) الحالة → الهدف → أصغر تدخل كافٍ → تحقق → تعلّم → توثيق.
        decisionCycle(snapshot, objective, objectiveSource, appContextKey)
    }

    private suspend fun decisionCycle(
        before: DeviceStateCollector.DeviceSnapshot,
        objective: Objective,
        objectiveSource: String,
        appContextKey: String,
    ) = withContext(Dispatchers.IO) {
        // المفردات تُبنى من قدرات هذا الجهاز في كل دورة: ما لا يُثبَت
        // أنه قابل للكتابة لا يوجد كمرشح أصلًا (INV-5) — نفس الكود
        // يعمل على جهاز بلا تحكم GPU دون أي فرع خاص به.
        val capabilities = runCatching { HardwareCapabilityResolver.resolve(appContext) }.getOrNull()
        val controls = capabilities?.let(ControlRegistry::build).orEmpty()
        val ownership = SharedHardwareOwnershipStore.winnerSnapshot().associateBy { it.key }
        val availableControls = controls.filter { control ->
            // INV-3: a knob the user manually locked is never touched by AI.
            // Safety/RECOVERY are excluded from this gate at a lower layer
            // (ManualControlLocks.blocks) so safety supremacy is preserved.
            if (control.key in ManualControlLocks.lockedKeys()) return@filter false
            val winner = ownership[control.key]
            winner == null || winner.token == TOKEN || winner.owner.priority <= ControlOwnership.Owner.MAX_AI.priority
        }

        // Prioritize controls with DynamicIntentLearner
        val prioritizedControls = dynamicIntentLearner.prioritizeControls(
            availableControls, appContextKey, objective.preferredDirection(before)
        )

        val plan = planner.planWithTrace(
            controls = prioritizedControls,
            state = before,
            objective = objective,
            appContext = appContextKey,
        )
        val step = plan.step

        if (step == null) {
            // لا فجوة مقيسة ⇒ لا فعل (INV-4). هذا هو السلوك الصحيح
            // لجهاز يعمل جيدًا، لا نقص في الذكاء.
            //
            // لكن "فجوة موجودة وكل المرشحين مستبعدون" حالة مختلفة
            // تمامًا ومعلومة جدًا للمستخدم: النطام رأى مشكلة وقرر أن
            // كل علاج متاح أسوأ منها. تُسجل كحلقة NO_ACTION مع أسباب
            // الاستبعاد، مع تهدئة زمنية كي لا تغرق الدفتر.
            val now = System.currentTimeMillis()
            if (!plan.satisfied &&
                plan.candidates.isNotEmpty() &&
                now - lastNoActionAtMs >= NO_ACTION_MIN_INTERVAL_MS
            ) {
                lastNoActionAtMs = now
                journal.record(
                    buildEpisode(
                        plan = plan,
                        step = null,
                        objective = objective,
                        objectiveSource = objectiveSource,
                        appContextKey = appContextKey,
                        before = before,
                        after = null,
                        appliedValue = null,
                        verdict = MaxAiVerdict.NO_ACTION,
                        detail = plan.candidates
                            .mapNotNull { it.rejection }
                            .distinct()
                            .joinToString(","),
                        effectBefore = null,
                        effectAfter = null,
                    )
                )
            }
            publish(aiEnabled = true, snapshot = before) {
                copy(
                    strategyLabel = if (plan.satisfied) {
                        "مراقبة — لا فجوة مقيسة"
                    } else {
                        "مراقبة — فجوة مقيسة بلا تدخل أقل ضررًا"
                    }
                )
            }
            return@withContext
        }

        // حالة التعلّم قبل التنفيذ — كي يكون "ماذا تعلّم" فرقًا مقيسًا
        // بين قيمتين حقيقيتين لا عبارة عامة.
        val effectBefore = planner.effectOf(step.control.key, step.direction, appContextKey)

        // SafetyGovernor owns the pre-write veto; the independent fast loop
        // keeps its status current between slow learning cycles.
        val transaction = safetyGovernor.execute(
            step = step,
            appContext = appContextKey,
            token = TOKEN,
            readState = { null },
        )
        val outcome = transaction.outcome
        bumpCounter(PREF_DECISIONS)

        if (!outcome.verified) {
            // لم يثبت التغيير على العتاد: لا مكافأة ولا ادعاء نجاح.
            if (!outcome.blocked) bumpCounter(PREF_ADJUSTED)
            DiagnosticCenter.record("maxai", "step not verified :: ${outcome.detail}")
            journal.record(
                buildEpisode(
                    plan = plan,
                    step = step,
                    objective = objective,
                    objectiveSource = objectiveSource,
                    appContextKey = appContextKey,
                    before = before,
                    after = null,
                    appliedValue = outcome.actual,
                    verdict = if (outcome.blocked) {
                        MaxAiVerdict.BLOCKED_SAFETY
                    } else {
                        MaxAiVerdict.WRITE_FAILED
                    },
                    detail = outcome.detail,
                    effectBefore = effectBefore,
                    effectAfter = planner.effectOf(step.control.key, step.direction, appContextKey),
                )
            )
            publish(aiEnabled = true, snapshot = before) {
                copy(
                    lastDecision = DecisionRecord(
                        step.control.label, System.currentTimeMillis(),
                        if (outcome.blocked) DecisionResult.BLOCKED_FOR_SAFETY else DecisionResult.FAILED,
                        outcome.detail
                    )
                )
            }
            return@withContext
        }

        // نُفِّذ وتُحقق منه: انتظر استجابة النطام ثم قِس الأثر الفعلي.
        delay(RESPONSE_WINDOW_MS)
        val after = runCatching { DeviceStateCollector.collect(appContext) }.getOrNull()
        val postSafety = safetyGovernor.enforcePost(step, outcome, after, TOKEN, appContextKey)
        if (postSafety.safetyReason == "post-veto") {
            bumpCounter(PREF_BLOCKED)
            DiagnosticCenter.record("maxai", postSafety.outcome.detail)
            journal.record(
                buildEpisode(
                    plan = plan,
                    step = step,
                    objective = objective,
                    objectiveSource = objectiveSource,
                    appContextKey = appContextKey,
                    before = before,
                    after = after,
                    appliedValue = outcome.actual,
                    verdict = MaxAiVerdict.BLOCKED_SAFETY,
                    detail = postSafety.outcome.detail,
                    effectBefore = effectBefore,
                    effectAfter = planner.effectOf(step.control.key, step.direction, appContextKey),
                )
            )
            publish(aiEnabled = true, snapshot = after ?: before) {
                copy(
                    lastDecision = DecisionRecord(
                        step.control.label, System.currentTimeMillis(),
                        DecisionResult.BLOCKED_FOR_SAFETY, postSafety.outcome.detail
                    )
                )
            }
            return@withContext
        }

        if (after == null) {
            DiagnosticCenter.record("maxai", "post-action measurement unavailable :: ${step.control.key}")
            bumpCounter(PREF_ADJUSTED)
            journal.record(
                buildEpisode(
                    plan = plan,
                    step = step,
                    objective = objective,
                    objectiveSource = objectiveSource,
                    appContextKey = appContextKey,
                    before = before,
                    after = null,
                    appliedValue = outcome.actual,
                    verdict = MaxAiVerdict.UNMEASURED,
                    detail = outcome.detail,
                    effectBefore = effectBefore,
                    effectAfter = planner.effectOf(step.control.key, step.direction, appContextKey),
                )
            )
            publish(aiEnabled = true, snapshot = before) {
                copy(
                    lastDecision = DecisionRecord(
                        step.control.label, System.currentTimeMillis(),
                        DecisionResult.FAILED, "تم التحقق من الكتابة لكن تعذّر قياس الأثر"
                    )
                )
            }
            return@withContext
        }

        val objectiveGain = objective.score(after) - objective.score(before)
        planner.recordMeasuredOutcome(step, appContextKey, before, after, objective)
        val effectAfter = planner.effectOf(step.control.key, step.direction, appContextKey)
        val improved = objectiveGain > 0f

        if (!improved) {
            val restored = rollbackOnRegression(step, outcome)
            bumpCounter(PREF_ADJUSTED)
            val result = if (restored) DecisionResult.ADJUSTED else DecisionResult.FAILED
            val detail = if (restored) {
                "تراجع مقيس (Δ%.3f) — استُرجع خط الأساس".format(objectiveGain)
            } else {
                "لا تحسن مقيس (Δ%.3f) — تعذر استرجاع خط الأساس".format(objectiveGain)
            }
            journal.record(
                buildEpisode(
                    plan = plan,
                    step = step,
                    objective = objective,
                    objectiveSource = objectiveSource,
                    appContextKey = appContextKey,
                    before = before,
                    after = after,
                    appliedValue = outcome.actual,
                    verdict = if (restored) {
                        MaxAiVerdict.REGRESSED_ROLLED_BACK
                    } else {
                        MaxAiVerdict.REGRESSED_STUCK
                    },
                    detail = detail,
                    effectBefore = effectBefore,
                    effectAfter = effectAfter,
                )
            )
            publish(aiEnabled = true, snapshot = after) {
                copy(
                    lastDecision = DecisionRecord(
                        step.control.label, System.currentTimeMillis(), result, detail
                    )
                )
            }
            return@withContext
        }

        bumpCounter(PREF_SUCCESSFUL)
        EventLog.userAction(
            "MaxAiEngine", "decision", step.control.key,
            "verified:${outcome.actual} gain=%.4f".format(objectiveGain)
        )
        journal.record(
            buildEpisode(
                plan = plan,
                step = step,
                objective = objective,
                objectiveSource = objectiveSource,
                appContextKey = appContextKey,
                before = before,
                after = after,
                appliedValue = outcome.actual,
                verdict = MaxAiVerdict.IMPROVED,
                detail = outcome.detail,
                effectBefore = effectBefore,
                effectAfter = effectAfter,
            )
        )
        publish(aiEnabled = true, snapshot = after) {
            copy(
                strategyLabel = step.control.label,
                lastDecision = DecisionRecord(
                    step.control.label, System.currentTimeMillis(),
                    DecisionResult.VERIFIED, "${step.reason} :: ${outcome.detail}"
                )
            )
        }
    }

    /**
     * استرجاع خط الأساس عند تراجع مقيس. يعيد true عندما يثبت الاسترجاع
     * بقراءة حية — وإلا يُترك القرار كما هو مع تسجيل صادق.
     */
    private fun rollbackOnRegression(
        step: MinimalPlanner.Step,
        outcome: MinimalPlanner.Outcome,
    ): Boolean {
        val baseline = step.from ?: return false
        // الاسترجاع نفسه يمر عبر مالك العتاد؛ لا كتابة سياسة مباشرة خارج
        // المُحكِّم، ولا طلب MAX_AI باقٍ بعد استرجاع خط أساسه.
        arbiter.release(step.control.key, TOKEN, restore = true)
        val restored = runCatching { step.control.read() }.getOrNull() == baseline
        DiagnosticCenter.record(
            "maxai",
            "regression rollback ${step.control.key} → $baseline :: " +
                if (restored) "verified" else "FAILED (was ${outcome.actual})"
        )
        return restored
    }

    /** سياق التعلّم: التطبيق في المقدمة كي تصبح المصداقية خاصة به (قرار #18). */
    private fun currentAppContextKey(): String = runCatching {
        RootFileAccess.read(APP_STATUS_PATH)
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("focused_app") }
            ?.split(" ")
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: "system"
    }.getOrDefault("system")

    // ── توثيق الحلقات ─────────────────────────────────────

    private fun reading(
        snapshot: DeviceStateCollector.DeviceSnapshot,
        objective: Objective,
    ): MaxAiReading = MaxAiReading(
        cpuLoadPercent = (snapshot.cpuLoad * 100f).toInt(),
        thermalC = snapshot.thermal * 100f,
        batteryPercent = (snapshot.battery * 100f).toInt(),
        memoryPercent = (snapshot.memoryUsage * 100f).toInt(),
        networkPercent = (snapshot.networkSpeed * 100f).toInt(),
        screenOn = snapshot.screenOn >= 0.5f,
        objectiveScore = objective.score(snapshot),
    )

    /**
     * يبني حلقة موثّقة من معطيات الدورة وحدها. كل حقل أحد أمرين: قراءة
     * عتاد أو خرج نموذج/مُحكِّم. ما لا يوجد له قياس يبقى null.
     */
    private fun buildEpisode(
        plan: MinimalPlanner.Plan,
        step: MinimalPlanner.Step?,
        objective: Objective,
        objectiveSource: String,
        appContextKey: String,
        before: DeviceStateCollector.DeviceSnapshot,
        after: DeviceStateCollector.DeviceSnapshot?,
        appliedValue: String?,
        verdict: MaxAiVerdict,
        detail: String,
        effectBefore: ControlOutcomeModel.Effect?,
        effectAfter: ControlOutcomeModel.Effect?,
    ): MaxAiEpisode {
        val beforeReading = reading(before, objective)
        val afterReading = after?.let { reading(it, objective) }
        val gain = afterReading?.let { it.objectiveScore - beforeReading.objectiveScore }
        val predictedGain = step?.predicted?.objectiveGain

        return MaxAiEpisode(
            id = System.currentTimeMillis(),
            appContext = appContextKey,
            objectiveLabel = objectiveLabelOf(objective, objectiveSource),
            objectiveSource = objectiveSource,
            weightPerformance = objective.performance,
            weightBattery = objective.battery,
            weightThermal = objective.thermalHeadroom,
            satisfactionTarget = plan.satisfaction,
            gap = plan.gap,
            before = beforeReading,
            after = afterReading,
            knobKey = step?.control?.key,
            knobLabel = step?.control?.label,
            direction = step?.direction?.name,
            fromValue = step?.from,
            toValue = step?.to,
            appliedValue = appliedValue,
            stepFraction = step?.stepFraction ?: 0f,
            predictedGain = predictedGain,
            predictedThermalC = step?.predicted?.thermalDeltaC,
            predictionConfidence = step?.predicted?.confidence,
            candidates = plan.candidates.map { trace ->
                MaxAiCandidate(
                    key = trace.key,
                    label = trace.label,
                    from = trace.from,
                    to = trace.to,
                    utility = trace.utility,
                    credibility = trace.credibility,
                    predictedGain = trace.predictedGain,
                    predictedThermalC = trace.predictedThermalC,
                    predictionConfidence = trace.predictionConfidence,
                    samples = trace.samples,
                    rejection = trace.rejection,
                    chosen = trace.chosen,
                )
            },
            verdict = verdict,
            detail = detail,
            objectiveDelta = gain,
            thermalDeltaC = afterReading?.let { it.thermalC - beforeReading.thermalC },
            cpuDeltaPercent = afterReading?.let { it.cpuLoadPercent - beforeReading.cpuLoadPercent },
            batteryDeltaPercent = afterReading?.let {
                it.batteryPercent - beforeReading.batteryPercent
            },
            samplesBefore = effectBefore?.samples ?: 0,
            samplesAfter = effectAfter?.samples ?: 0,
            confidenceBefore = effectBefore?.confidence ?: 0f,
            confidenceAfter = effectAfter?.confidence ?: 0f,
            predictionErrorGain = if (predictedGain != null && gain != null) {
                abs(predictedGain - gain)
            } else {
                null
            },
            safetyLevel = safetyEngine.status.value.level.name,
        )
    }

    private fun objectiveLabelOf(objective: Objective, source: String): String =
        if (source == "screen_off") "screen_off" else Objective.labelFor(objective)

    private fun appendTrend(
        snapshot: DeviceStateCollector.DeviceSnapshot,
        score: Float,
    ) {
        synchronized(trendLock) {
            trendSamples.addLast(
                MaxAiSample(
                    timestampMs = System.currentTimeMillis(),
                    cpuLoadPercent = (snapshot.cpuLoad * 100f).toInt(),
                    thermalC = snapshot.thermal * 100f,
                    batteryPercent = (snapshot.battery * 100f).toInt(),
                    memoryPercent = (snapshot.memoryUsage * 100f).toInt(),
                    objectiveScore = score,
                )
            )
            while (trendSamples.size > TREND_CAPACITY) trendSamples.removeFirst()
        }
    }

    private fun trendSnapshot(): List<MaxAiSample> = synchronized(trendLock) {
        trendSamples.toList()
    }

    // ── واجهة المستخدم/الخدمات ─────────────────────────────

    /** لقطة ما تعلّمه المحرك عن كل مقبض — مصدر قسم المعرفة في الواجهة. */
    fun effectsSnapshot(): Map<String, ControlOutcomeModel.Effect> = planner.effectsSnapshot()

    /**
     * دورة فورية عند الطلب (دخول الشاشة مثلًا) كي تعكس الحالة القياسات
     * الحالية بلا انتظار دورة الثلاثين ثانية القادمة.
     */
    suspend fun requestRefresh() = withContext(Dispatchers.IO) {
        runCatching { runCycleSingleFlight() }
    }

    /**
     * تبديل Max AI. عند الإيقاف تُترك سقوف المحرك باسترجاع الأساس.
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
                // إيقاف العقل = تحرير كل مقبض يملكه مع استرجاع خط أساسه
                // المحفوظ في المُحكِّم (قرار #6) — لا "حالة مستقرة" غامضة.
                ceilingKnobs.leaveAll(TOKEN)
                arbiter.releaseToken(TOKEN, restore = true)
            }
            runCatching { runCycleSingleFlight() }
        }
    }

    /**
     * يضبط وزن الهدف من تفضيل المستخدم (قرار #10). يُستدعى مرة عند
     * التفعيل، ثم يظل قابلًا للتعديل — والنطام يعدّله أيضًا من السلوك
     * عبر مصداقية المقابض فلا يبقى جامدًا.
     */
    fun setObjectivePreference(preference: String) {
        PropertyUtils.set(
            MaxManagerProps.Conf.AI_OBJECTIVE,
            Objective.labelFor(Objective.fromPreference(preference)),
        )
        scope.launch(Dispatchers.IO) { runCatching { runCycleSingleFlight() } }
    }

    /** التفضيل الحالي للعرض في الواجهة — التوازن إن لم يُسأل المستخدم بعد. */
    fun objectivePreference(): String =
        PropertyUtils.get(MaxManagerProps.Conf.AI_OBJECTIVE).takeIf { it.isNotBlank() }
            ?: Objective.labelFor(Objective.BALANCED)

    /**
     * Applies a user-selected base profile immediately through the existing
     * external compatibility service. Profiles remain baselines, not AI knobs.
     */
    suspend fun requestManualProfile(profileId: String, label: String): Boolean =
        withContext(Dispatchers.IO) {
            _profileRequest.value = ProfileRequestState(profileId = profileId, inFlight = true)
            try {
                val ok = ProfileApplier.apply(profileId)
                EventLog.userAction(
                    "MaxAiEngine", "base_profile", label,
                    "$profileId:${if (ok) "ok" else "failed"}"
                )
                _profileRequest.value = ProfileRequestState(
                    profileId, false,
                    if (ok) DecisionResult.VERIFIED else DecisionResult.FAILED
                )
                if (ok) runCatching { runCycleSingleFlight() }
                ok
            } catch (t: Throwable) {
                _profileRequest.value = ProfileRequestState(profileId, false, DecisionResult.FAILED)
                DiagnosticCenter.record(
                    "profile",
                    "manual profile failed: ${t.message ?: t.javaClass.simpleName}",
                    level = DiagnosticCenter.Level.ERROR,
                )
                false
            }
        }

    // ── قراءات الحالة ─────────────────────────────────────

    fun readAiEnabled(): Boolean = runCatching {
        PropertyUtils.get(MaxManagerProps.Conf.AI_ENABLED, "0") == "1"
    }.getOrDefault(false)

    // ── العدادات والنشر ──────────────────────────────────

    private fun bumpCounter(key: String) {
        val v = prefs.getLong(key, 0L) + 1L
        prefs.edit().putLong(key, v).apply()
    }

    /**
     * ينشر الحالة من قيم محسوبة فعلًا: قراءات الدورة، عدادات دائمة،
     * حالة الأمان ولقطة ملكية المقابض. القراءات الجذرية تتم
     * خارج دالة النشر نفسها (بلا تأثيرات جانبية داخل تحديث الحالة).
     */
    private fun publish(
        aiEnabled: Boolean,
        snapshot: DeviceStateCollector.DeviceSnapshot? = null,
        mutate: MaxAiState.() -> MaxAiState = { this },
    ) {
        val prev = _state.value
        val safetyNow = safetyEngine.status.value
        val profile = runCatching { ProfileApplier.currentProfile() }.getOrNull()
        val ownership = runCatching {
            SharedHardwareOwnershipStore.winnerSnapshot()
                .sortedBy { it.key }
                .map { winner ->
                    KnobOwnershipSnapshot(
                        key = winner.key,
                        owner = winner.owner,
                        desired = winner.desired,
                        state = if (winner.committed) OwnershipCommitState.VERIFIED
                        else OwnershipCommitState.PENDING,
                        locked = ManualControlLocks.find(winner.key) != null,
                    )
                }
        }.getOrDefault(prev.ownership)

        // Locks are listed from their own durable store, not from the journal:
        // after a restart the journal is empty while the knobs stay locked.
        val lockedKnobs = runCatching {
            ManualControlLocks.snapshot()
                .sortedBy { it.key }
                .map { LockedKnobSnapshot(it.key, it.desired, it.lockedAtMs) }
        }.getOrDefault(prev.lockedKnobs)

        val strategy = when {
            safetyNow.engaged || ownership.any { it.owner == ControlOwnership.Owner.SAFETY } ->
                "أمان حراري — حماية المقابض"
            !aiEnabled && ownership.isEmpty() -> "يدوي — لا ملكية مسجلة"
            !aiEnabled -> "يدوي — ${ownership.size} مقابض مملوكة"
            ownership.any { it.owner == ControlOwnership.Owner.MAX_AI } ->
                "Max AI — يدير ${ownership.count { it.owner == ControlOwnership.Owner.MAX_AI }} مقابض"
            ownership.isNotEmpty() -> "Max AI — يراقب المقابض المملوكة"
            prev.lastDecision != null -> prev.lastDecision!!.label
            else -> "Max AI — جارٍ التقييم"
        }

        val learning = planner.learningProgress()
        val context = lastCycleContext
        val trend = trendSnapshot()
        _state.value = MaxAiState(
            aiEnabled = aiEnabled,
            strategyLabel = strategy,
            lastDecision = prev.lastDecision,
            safety = safetyNow,
            totalDecisions = prefs.getLong(PREF_DECISIONS, 0L),
            successfulDecisions = prefs.getLong(PREF_SUCCESSFUL, 0L),
            adjustedDecisions = prefs.getLong(PREF_ADJUSTED, 0L),
            blockedForSafety = prefs.getLong(PREF_BLOCKED, 0L),
            learnedKnobs = learning.learnedKnobs,
            learningSamples = learning.samples,
            cpuLoadPercent = snapshot?.let { (it.cpuLoad * 100).toInt() } ?: prev.cpuLoadPercent,
            thermalC = snapshot?.let { it.thermal * 100f } ?: safetyNow.thermalC,
            batteryPercent = snapshot?.let { (it.battery * 100).toInt() } ?: prev.batteryPercent,
            screenOn = snapshot?.let { it.screenOn >= 0.5f } ?: prev.screenOn,
            ownership = ownership,
            lockedKnobs = lockedKnobs,
            currentProfile = profile,
            trend = trend,
            objectiveWeights = context?.objective,
            objectiveSource = context?.objectiveSource ?: prev.objectiveSource,
            appContext = context?.appContextKey ?: prev.appContext,
            objectiveScore = context?.score ?: prev.objectiveScore,
            satisfactionTarget = context?.satisfaction ?: MinimalPlanner.SATISFIED_SCORE,
            memoryPercent = snapshot?.let { (it.memoryUsage * 100f).toInt() } ?: prev.memoryPercent,
            lastSampleAtMs = if (snapshot != null) System.currentTimeMillis() else prev.lastSampleAtMs,
        ).mutate()
    }

}
