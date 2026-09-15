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
    private val credibility: CredibilityStore,
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

        /**
         * نافذة الحكم المؤجل: عشر ثوانٍ تقيس الأداء والحرارة جيدًا، وتقيس
         * البطارية صفرًا لأن النسبة لا تتحرك أصلًا في هذا المدى. لذلك تُعاد
         * الحلقة نفسها بعد ربع ساعة لتحمل حكمًا ثانيًا مقيسًا لا ادعاءً.
         */
        private const val DEFERRED_WINDOW_MS = 900_000L

        /**
         * نافذة ربط تجاوز المستخدم بالتدخل الذي أثاره. بعد خمس دقائق
         * يصبح الربط تخمينًا، وعقوبة مبنية على تخمين تفسد التعلّم.
         */
        private const val OVERRIDE_WINDOW_MS = 300_000L

        /** تهدئة حلقات الانحراف لكل مقبض — الانحراف قد يدوم دورات. */
        private const val DRIFT_COOLDOWN_MS = 300_000L

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

        /** خطوة التنبّؤ الحراري = دورة مراقبة واحدة. */
        private const val FORECAST_STEP_MS = CYCLE_MS

        /** عدد نقاط منحنى التوقع مقابل الواقع المحفوظة في الذاكرة. */
        private const val FORECAST_CAPACITY = 24

        /** سماحة مطابقة تنبّؤ سابق بقياس اللحطة الحالية. */
        private const val FORECAST_MATCH_TOLERANCE_MS = 15_000L
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

    /** تنبّؤ سابق ينتظر لحظته كي يُحاكم بالقياس الفعلي. */
    private data class PendingForecast(
        val targetMs: Long,
        val forecastC: Float,
        val madeAtMs: Long,
    )

    private val pendingForecasts = ArrayDeque<PendingForecast>()
    private val forecastPoints = ArrayDeque<MaxAiForecastPoint>()
    private val forecastLock = Any()
    private var latestForecast: List<Float> = emptyList()
    private var latestForecastAtMs = 0L

    /** حالة طبقة الثقة والاستكشاف من الدورة الأخيرة — تُنشر للواجهة. */
    @Volatile private var lastTrust: List<TrustModel.KnobTrust> = emptyList()
    @Volatile private var lastExploration = ExplorationState()
    @Volatile private var lastProbeAtMs = 0L
    private val probesThisSession = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * آخر تدخل مقيس يمكن أن يُرفض — مرجع ربط رد فعل المستخدم بالحلقة
     * التي أثارته، وبالمفتاح/الاتجاه/السياق المعاقَب في المصداقية.
     */
    private data class ActedKnob(
        val episodeId: Long,
        val knobKey: String,
        val direction: ControlRegistry.Direction,
        val appContext: String,
        val atMs: Long,
    )

    @Volatile private var lastActed: ActedKnob? = null

    /** بصمة آخر حالة سلامة مسجّلة — لا تُسجّل نفس الحالة مرتين. */
    @Volatile private var lastSafetySignature: String? = null

    /** لحطة أحدث حلقة انحراف لكل مقبض (تهدئة). */
    private val driftNoticedAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // ── دورة الحياة ───────────────────────────────────────

    /**
     * يبدأ المحرك عند إقلاع التطبيق (مرة واحدة، بلا توقف): حلقة واحدة
     * دائمة بطيئة. عندما يكون AI مطفأً تكتفي الدورة بالأمان والنشر —
     * صفر قرارات وصفر كتابات على العتاد.
     */
    fun start() {
        if (started) return
        started = true

        // رد فعل المستخدم إشارة مجانية ومقيسة: قفل يدوي على نفس المقبض
        // خلال دقائق من تدخلنا = رفض، لا استبيان ولا سؤال.
        ManualControlLocks.observe { lock ->
            noteUserOverride(MaxAiOverride.LOCK, lock.key)
        }

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
                    // السلامة حلقة من الدرجة الأولى: «لماذا خفّض جهازي نفسه؟» لا
                    // يجيبه عداّد؛ كل تبدل حالة موثّق يدخل نفس الخط الزمني.
                    recordSafetyEpisode(safetyEngine.status.value, snapshot)
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
        // التنبّؤ الأمامي كان يُحسب للسلامة ثم يُرمى. الآن يُحفظ كاملًا كي
        // يُقارن لاحقًا بما حدث فعلًا: أصدق دليل على فهم الجهاز، بتكلفة صفر.
        val forecast = PredictorBridge.predictThermal(THERMAL_FORECAST_STEPS)
        val predictedC = forecast?.maxOrNull()
        safetyEngine.evaluate(thermalC, snapshot, predictedC)
        if (snapshot != null) recordForecast(thermalC, forecast)

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

        // الانحراف حلقة من الدرجة الأولى: مقبض يملكه المحرك وقيمته الحية
        // ليست المطلوبة يعني أن النطام أعاد قيمته بعد كتابة مؤكَّدة. الكشف
        // من قراءة حية لا من ادعاء، ومع تهدئة لكل مفتاح كي لا يغرق الدفتر.
        val driftNow = System.currentTimeMillis()
        controls.forEach { control ->
            val winner = ownership[control.key] ?: return@forEach
            if (winner.token != TOKEN) return@forEach
            val desired = winner.desired
            if (desired.isNullOrEmpty()) return@forEach
            val live = runCatching { control.read() }.getOrNull() ?: return@forEach
            if (live == desired) return@forEach
            if (driftNow - (driftNoticedAtMs[control.key] ?: 0L) < DRIFT_COOLDOWN_MS) return@forEach
            driftNoticedAtMs[control.key] = driftNow
            journal.record(
                buildSystemEpisode(
                    kind = MaxAiEpisodeKind.DRIFT,
                    snapshot = before,
                    objective = objective,
                    objectiveSource = objectiveSource,
                    appContextKey = appContextKey,
                    knobKey = control.key,
                    knobLabel = control.label,
                    fromValue = desired,
                    toValue = live,
                    verdict = MaxAiVerdict.REGRESSED_STUCK,
                    detail = "انحراف مقيس: المطلوب $desired والقيمة الحية $live",
                )
            )
            DiagnosticCenter.record("maxai", "drift ${control.key} desired=$desired live=$live")
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

        // طبقة الثقة: ماذا يعرف المحرك عن كل مقبض في الاتجاه المطلوب، وما
        // لا يزال يجهله. تُحسب من خرائط الأثر المقيسة وحدها — لا تقدير جديد.
        val direction = plan.direction
        val trustList = prioritizedControls.map { control ->
            TrustModel.assess(
                key = control.key,
                label = control.label,
                direction = direction,
                effect = planner.effectOf(control.key, direction, appContextKey),
            )
        }
        lastTrust = trustList.sortedByDescending { it.informationGain }

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
            // الجهاز محقق لهدفه = أرخص لحطة ممكنة لقياس مقبض مجهول. البوابة
            // وحدها تقرر، وهي ترفض افتراضيًا ما لم تجتمع كل شروط انخفاض
            // تكلفة الخطأ.
            if (plan.satisfied &&
                exploreIfWorthwhile(
                    controls = prioritizedControls,
                    before = before,
                    objective = objective,
                    objectiveSource = objectiveSource,
                    appContextKey = appContextKey,
                    direction = direction,
                    trust = trustList,
                )
            ) {
                return@withContext
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
            val regressedEpisode = buildEpisode(
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
            journal.record(regressedEpisode)
            // حتى التراجع المسترجَع يستحق قياسًا ثانيًا: إن بقي أثر بعد ربع
            // ساعة فالاسترجاع لم يعد الجهاز إلى حالته فعلًا.
            noteActed(regressedEpisode, step, appContextKey)
            scheduleDeferredVerdict(regressedEpisode, objective)
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
        val improvedEpisode = buildEpisode(
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
        journal.record(improvedEpisode)
        // الحكم الفوري مسجل أعلاه؛ والبطارية تُقاس بعد ربع ساعة في نفس الحلقة.
        noteActed(improvedEpisode, step, appContextKey)
        scheduleDeferredVerdict(improvedEpisode, objective)
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

    // ── طبقة الثقة والاستكشاف المحروس ──────────────────

    /**
     * يقيّم بوابة الاستكشاف ثم ينفّذ تجربة واحدة إن سمحت.
     *
     * الفرق الجوهري عن "التجريب لأجل التعلّم": القرار لا ينطلق من
     * رغبة في التعلّم بل من معرفة ما لا نعرفه ([TrustModel.Knowledge.UNKNOWN])
     * مقرونًا بتكلفة خطأ منخفضة مقيسة الآن: حرارة وبطارية وشاشة
     * وسلامة وميزانية جلسة. وكل سبب منع يُنشر للواجهة بلا تلطيف.
     *
     * @return true حين نُفّذت تجربة ونُشرت حالتها (فلا تحتاج الدورة نشرًا آخر).
     */
    private suspend fun exploreIfWorthwhile(
        controls: List<ControlRegistry.Control>,
        before: DeviceStateCollector.DeviceSnapshot,
        objective: Objective,
        objectiveSource: String,
        appContextKey: String,
        direction: ControlRegistry.Direction,
        trust: List<TrustModel.KnobTrust>,
    ): Boolean {
        val trustByKey = trust.associateBy { it.key }
        val candidates = controls.mapNotNull { control ->
            val knobTrust = trustByKey[control.key] ?: return@mapNotNull null
            val current = runCatching { control.read() }.getOrNull() ?: return@mapNotNull null
            val next = control.step(current, direction) ?: return@mapNotNull null
            TrustModel.ProbeCandidate(
                trust = knobTrust,
                stepFraction = control.stepFraction(current, next),
                controlCost = control.cost,
                // خط أساس مقروء = استرجاع ممكن ومتحقق منه لاحقًا.
                hasBaseline = true,
            )
        }
        val risk = TrustModel.RiskContext(
            thermalC = before.thermal * 100f,
            batteryPercent = (before.battery * 100f).toInt(),
            cpuLoadPercent = (before.cpuLoad * 100f).toInt(),
            screenOn = before.screenOn >= 0.5f,
            safetyNormal = safetyEngine.status.value.level == SafetyLevel.NORMAL,
        )
        val gate = TrustModel.gate(
            candidates = candidates,
            risk = risk,
            nowMs = System.currentTimeMillis(),
            lastProbeAtMs = lastProbeAtMs,
            probesThisSession = probesThisSession.get(),
        )
        lastExploration = ExplorationState(
            blockReason = gate.blockReason,
            targetLabel = gate.target?.trust?.label,
            worstCaseCost = gate.cost?.worstCase,
            informationGain = gate.target?.trust?.informationGain,
            probesThisSession = probesThisSession.get(),
            budget = TrustModel.MAX_PROBES_PER_SESSION,
            lastProbeAtMs = lastProbeAtMs,
        )
        val target = gate.target ?: return false
        if (!gate.allowed) return false
        val control = controls.firstOrNull { it.key == target.trust.key } ?: return false
        return runProbe(
            control = control,
            candidate = target,
            cost = gate.cost,
            before = before,
            objective = objective,
            objectiveSource = objectiveSource,
            appContextKey = appContextKey,
            direction = direction,
        )
    }

    /**
     * تجربة معرفية واحدة: خطوة واحدة → نافذة قياس → تسجيل أثر → **استرجاع
     * إلزامي**. التجربة قياس لا سياسة، فلا تبقى قيمة تجريبية على الجهاز ولو
     * بدت نافعة: إن ثبت نفعها فسيختارها المخطّط بنفسه في دورة قرار لاحقة.
     *
     * نفس نقطة التحكم الوحيدة ([SafetyGovernor]) ونفس نوافذ القياس؛ لا مسار
     * كتابة موازٍ للاستكشاف.
     */
    private suspend fun runProbe(
        control: ControlRegistry.Control,
        candidate: TrustModel.ProbeCandidate,
        cost: TrustModel.ProbeCost?,
        before: DeviceStateCollector.DeviceSnapshot,
        objective: Objective,
        objectiveSource: String,
        appContextKey: String,
        direction: ControlRegistry.Direction,
    ): Boolean {
        val from = runCatching { control.read() }.getOrNull() ?: return false
        val to = control.step(from, direction) ?: return false
        val worstCase = cost?.worstCase
        val reason = "تجربة معرفية — جهل " +
            String.format(java.util.Locale.US, "%.3f", candidate.trust.epistemic) +
            " وتكلفة " +
            String.format(java.util.Locale.US, "%.2f", worstCase ?: 0f)
        val step = MinimalPlanner.Step(
            control = control,
            from = from,
            to = to,
            direction = direction,
            reason = reason,
            stepFraction = control.stepFraction(from, to),
        )
        val baseScore = objective.score(before)
        val probePlan = MinimalPlanner.Plan(
            step = step,
            score = baseScore,
            gap = MinimalPlanner.SATISFIED_SCORE - baseScore,
            satisfaction = MinimalPlanner.SATISFIED_SCORE,
            direction = direction,
            // لا ترشيح بالمنفعة حدث هنا؛ ترك القائمة فارغة أصدق من أرقام ملفّقة.
            candidates = emptyList(),
            satisfied = true,
        )
        val effectBefore = planner.effectOf(control.key, direction, appContextKey)
        lastProbeAtMs = System.currentTimeMillis()
        probesThisSession.incrementAndGet()
        DiagnosticCenter.record("maxai", "probe ${control.key} $from → $to :: $reason")

        val transaction = safetyGovernor.execute(
            step = step,
            appContext = appContextKey,
            token = TOKEN,
            readState = { null },
        )
        val outcome = transaction.outcome
        if (!outcome.verified) {
            // لم تُكتب التجربة أصلًا: لا قياس ولا معرفة جديدة، وتُسجّل كما هي.
            val blockedBySafety = outcome.blocked || transaction.safetyReason == "pre-veto"
            if (blockedBySafety) bumpCounter(PREF_BLOCKED)
            journal.record(
                buildEpisode(
                    plan = probePlan,
                    step = step,
                    objective = objective,
                    objectiveSource = objectiveSource,
                    appContextKey = appContextKey,
                    before = before,
                    after = null,
                    appliedValue = outcome.actual,
                    verdict = if (blockedBySafety) {
                        MaxAiVerdict.BLOCKED_SAFETY
                    } else {
                        MaxAiVerdict.WRITE_FAILED
                    },
                    detail = outcome.detail,
                    effectBefore = effectBefore,
                    effectAfter = effectBefore,
                    exploration = true,
                    reverted = false,
                    informationGain = candidate.trust.informationGain,
                    probeCost = worstCase,
                )
            )
            publish(aiEnabled = true, snapshot = before)
            return true
        }

        delay(RESPONSE_WINDOW_MS)
        val after = runCatching { DeviceStateCollector.collect(appContext) }.getOrNull()
        val postSafety = safetyGovernor.enforcePost(step, outcome, after, TOKEN, appContextKey)
        if (after != null) {
            // هذا هو المكسب الحقيقي من التجربة: عينة مقيسة تقلّل الجهل.
            planner.recordMeasuredOutcome(step, appContextKey, before, after, objective)
        }
        val reverted = rollbackOnRegression(step, outcome)
        val effectAfter = planner.effectOf(control.key, direction, appContextKey)
        val gain = after?.let { objective.score(it) - baseScore }
        val verdict = when {
            postSafety.safetyReason == "post-veto" -> MaxAiVerdict.BLOCKED_SAFETY
            after == null -> MaxAiVerdict.UNMEASURED
            gain != null && gain > 0f -> MaxAiVerdict.IMPROVED
            else -> MaxAiVerdict.REGRESSED_ROLLED_BACK
        }
        journal.record(
            buildEpisode(
                plan = probePlan,
                step = step,
                objective = objective,
                objectiveSource = objectiveSource,
                appContextKey = appContextKey,
                before = before,
                after = after,
                appliedValue = outcome.actual,
                verdict = verdict,
                detail = "${outcome.detail} :: probe restore=" +
                    if (reverted) "verified" else "FAILED",
                effectBefore = effectBefore,
                effectAfter = effectAfter,
                exploration = true,
                reverted = reverted,
                informationGain = candidate.trust.informationGain,
                probeCost = worstCase,
            )
        )
        publish(aiEnabled = true, snapshot = after ?: before) {
            copy(
                lastDecision = DecisionRecord(
                    control.label, System.currentTimeMillis(),
                    DecisionResult.EXECUTED, reason,
                )
            )
        }
        return true
    }

    // ── التوقع الحراري مقابل الواقع ──────────────────────

    /**
     * يطابق قياس اللحطة مع التنبّؤ الذي قيل عنها سابقًا، ثم يخزّن
     * تنبّؤ الدورة القادمة ليُحاكم هو أيضًا. لا تخزين على القرص: هذا
     * منحنى جلسة حية، ورسمه من جلسة سابقة كان سيكون إيهامًا.
     */
    private fun recordForecast(measuredC: Float, forecast: FloatArray?) {
        val now = System.currentTimeMillis()
        synchronized(forecastLock) {
            var matched: PendingForecast? = null
            while (pendingForecasts.isNotEmpty()) {
                val head = pendingForecasts.first()
                if (head.targetMs > now + FORECAST_MATCH_TOLERANCE_MS) break
                pendingForecasts.removeFirst()
                if (now - head.targetMs <= FORECAST_STEP_MS) matched = head
            }
            forecastPoints.addLast(
                MaxAiForecastPoint(
                    timestampMs = now,
                    actualC = measuredC,
                    forecastC = matched?.forecastC,
                    leadMs = matched?.let { it.targetMs - it.madeAtMs } ?: 0L,
                    future = false,
                )
            )
            while (forecastPoints.size > FORECAST_CAPACITY) forecastPoints.removeFirst()

            if (forecast != null && forecast.isNotEmpty()) {
                latestForecast = forecast.toList()
                latestForecastAtMs = now
                pendingForecasts.addLast(
                    PendingForecast(
                        targetMs = now + FORECAST_STEP_MS,
                        forecastC = forecast[0],
                        madeAtMs = now,
                    )
                )
                while (pendingForecasts.size > FORECAST_CAPACITY) pendingForecasts.removeFirst()
            }
        }
    }

    /** الماضي المطابَق ثم امتداد مستقبلي من أحدث تنبّؤ فعلي. */
    private fun forecastSnapshot(): List<MaxAiForecastPoint> = synchronized(forecastLock) {
        val base = latestForecastAtMs
        val future = if (base == 0L) {
            emptyList()
        } else {
            latestForecast.mapIndexed { index, value ->
                MaxAiForecastPoint(
                    timestampMs = base + (index + 1) * FORECAST_STEP_MS,
                    actualC = null,
                    forecastC = value,
                    leadMs = (index + 1) * FORECAST_STEP_MS,
                    future = true,
                )
            }
        }
        forecastPoints.toList() + future
    }

    /** متوسط |تنبّؤ − مقيس| للنقاط التي وُجد لها الطرفان. */
    private fun forecastError(): Float? = synchronized(forecastLock) {
        val errors = forecastPoints.mapNotNull { point ->
            val actual = point.actualC ?: return@mapNotNull null
            val predicted = point.forecastC ?: return@mapNotNull null
            abs(actual - predicted)
        }
        if (errors.isEmpty()) null else errors.average().toFloat()
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
        exploration: Boolean = false,
        reverted: Boolean = false,
        informationGain: Float? = null,
        probeCost: Float? = null,
    ): MaxAiEpisode {
        val beforeReading = reading(before, objective)
        val afterReading = after?.let { reading(it, objective) }
        val gain = afterReading?.let { it.objectiveScore - beforeReading.objectiveScore }
        val predictedGain = step?.predicted?.objectiveGain

        // تطوّر المعرفة: يُشتق من خرائط الأثر نفسها قبل/بعد — لا تقدير
        // جديد، ولا ادعاء تعلّم ما لم تتغير الأرقام فعلًا.
        val epistemicBefore = effectBefore?.let {
            TrustModel.epistemicUncertainty(it.samples, it.gainStdDev)
        }
        val epistemicAfter = effectAfter?.let {
            TrustModel.epistemicUncertainty(it.samples, it.gainStdDev)
        }
        val knowledgeBefore = effectBefore?.let {
            TrustModel.knowledgeOf(
                samples = it.samples,
                meanGain = it.meanGain,
                meanThermalC = it.meanThermal,
                epistemic = epistemicBefore ?: TrustModel.PRIOR_EPISTEMIC,
            ).name
        }
        val knowledgeAfter = effectAfter?.let {
            TrustModel.knowledgeOf(
                samples = it.samples,
                meanGain = it.meanGain,
                meanThermalC = it.meanThermal,
                epistemic = epistemicAfter ?: TrustModel.PRIOR_EPISTEMIC,
            ).name
        }

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
            kind = if (exploration) MaxAiEpisodeKind.PROBE else MaxAiEpisodeKind.DECISION,
            exploration = exploration,
            reverted = reverted,
            knowledgeBefore = knowledgeBefore,
            knowledgeAfter = knowledgeAfter,
            epistemicBefore = epistemicBefore,
            epistemicAfter = epistemicAfter,
            informationGain = informationGain,
            probeCost = probeCost,
        )
    }

    /** يتذكر آخر تدخل مقيس كي يُربط به رد فعل المستخدم إن حدث. */
    private fun noteActed(
        episode: MaxAiEpisode,
        step: MinimalPlanner.Step,
        appContextKey: String,
    ) {
        lastActed = ActedKnob(
            episodeId = episode.id,
            knobKey = step.control.key,
            direction = step.direction,
            appContext = appContextKey,
            atMs = System.currentTimeMillis(),
        )
    }

    /**
     * تجاوز يدوي خلال [OVERRIDE_WINDOW_MS] من تدخل مقيس = رفض مقيس.
     *
     * يُسجّل في نفس الحلقة (المرحلة التاسعة) ثم يُغذّى عقوبةً في
     * [CredibilityStore] عبر نفس القناة التي تكافئ النجاح المقيس، فلا يبقى
     * المقبض المرفوض مرشّحًا بنفس قوته في الدورة التالية.
     *
     * التقييد مقصود: قفل مقبض مختلف عن المقبض المُدار ليس رفضًا لهذا
     * القرار، وعقوبة بلا ربط مقيس تفسد التعلّم بدل أن تخدمه.
     */
    fun noteUserOverride(kind: String, key: String?) {
        val acted = lastActed ?: return
        val now = System.currentTimeMillis()
        if (now - acted.atMs > OVERRIDE_WINDOW_MS) return
        if (kind == MaxAiOverride.LOCK && key != null && key != acted.knobKey) return
        val amended = journal.amend(acted.episodeId) { episode ->
            if (episode.userOverrideAtMs != null) {
                episode
            } else {
                episode.copy(userOverrideAtMs = now, userOverrideKind = kind)
            }
        }
        if (!amended) return
        // رفض واحد لكل تدخل: لا تتراكم العقوبة من تكرار نفس الفعل.
        lastActed = null
        credibility.record(
            key = acted.knobKey,
            direction = acted.direction,
            appContext = acted.appContext,
            verified = false,
        )
        DiagnosticCenter.record(
            "maxai",
            "user override $kind :: ${acted.knobKey} after ${(now - acted.atMs) / 1000}s",
        )
        EventLog.userAction("MaxAiEngine", "override", acted.knobKey, kind)
    }

    /**
     * يعيد فتح الحلقة بعد [DEFERRED_WINDOW_MS] ليسجل ما لا تقيسه عشر ثوانٍ:
     * انحدار البطارية الفعلي والحرارة المستقرة. لا حلقة ثانية تكرّر نفس
     * القرار: الحلقة الواحدة تحمل حكمًا فوريًا وحكمًا مؤجلًا.
     */
    private fun scheduleDeferredVerdict(episode: MaxAiEpisode, objective: Objective) {
        scope.launch {
            delay(DEFERRED_WINDOW_MS)
            val later = runCatching { DeviceStateCollector.collect(appContext) }.getOrNull()
                ?: return@launch
            val laterReading = reading(later, objective)
            journal.amend(episode.id) { stored ->
                if (stored.deferredAtMs != null) {
                    stored
                } else {
                    stored.copy(
                        deferredAtMs = System.currentTimeMillis(),
                        deferredBatteryDeltaPercent =
                            laterReading.batteryPercent - stored.before.batteryPercent,
                        deferredThermalDeltaC = laterReading.thermalC - stored.before.thermalC,
                        deferredObjectiveDelta =
                            laterReading.objectiveScore - stored.before.objectiveScore,
                    )
                }
            }
        }
    }

    /**
     * يحوّل تبدل حالة محرك الأمان إلى حلقة كاملة. العودة للطبيعي تُسجّل
     * أيضًا لأن «متى استعاد جهازي سقفه؟» سؤال مشروع بقدر «لماذا خُفّض؟».
     */
    private fun recordSafetyEpisode(
        status: SafetyStatus,
        snapshot: DeviceStateCollector.DeviceSnapshot,
    ) {
        val signature = "${status.level.name}|${status.interventions}"
        val first = lastSafetySignature == null
        if (signature == lastSafetySignature) return
        lastSafetySignature = signature
        // القيمة الأولى عند الإقلاع وصف حالة لا حدِث.
        if (first && status.level == SafetyLevel.NORMAL) return
        val context = lastCycleContext
        val objective = context?.objective ?: Objective.BALANCED
        val detail = buildString {
            append(status.lastReason.ifBlank { "مستوى السلامة: ${status.level.name}" })
            append(" :: الفرض ").append(status.enforcement.name)
            if (status.enforcementDetail.isNotBlank()) {
                append(" (").append(status.enforcementDetail).append(")")
            }
        }
        journal.record(
            buildSystemEpisode(
                kind = MaxAiEpisodeKind.SAFETY,
                snapshot = snapshot,
                objective = objective,
                objectiveSource = context?.objectiveSource ?: "learned",
                appContextKey = context?.appContextKey ?: currentAppContextKey(),
                knobKey = null,
                knobLabel = null,
                fromValue = null,
                toValue = null,
                verdict = if (status.level == SafetyLevel.NORMAL) {
                    MaxAiVerdict.NO_ACTION
                } else {
                    MaxAiVerdict.BLOCKED_SAFETY
                },
                detail = detail,
            )
        )
    }

    /**
     * حلقة نطام (سلامة أو انحراف): لا مخطِّط ولا مرشحين ولا تنبّء، فتلك
     * الحقول تبقى null بدل أرقام مجاملة. القراءة قبلًا حقيقية تمامًا.
     */
    private fun buildSystemEpisode(
        kind: MaxAiEpisodeKind,
        snapshot: DeviceStateCollector.DeviceSnapshot,
        objective: Objective,
        objectiveSource: String,
        appContextKey: String,
        knobKey: String?,
        knobLabel: String?,
        fromValue: String?,
        toValue: String?,
        verdict: MaxAiVerdict,
        detail: String,
    ): MaxAiEpisode {
        val beforeReading = reading(snapshot, objective)
        return MaxAiEpisode(
            id = System.currentTimeMillis(),
            appContext = appContextKey,
            objectiveLabel = objectiveLabelOf(objective, objectiveSource),
            objectiveSource = objectiveSource,
            weightPerformance = objective.performance,
            weightBattery = objective.battery,
            weightThermal = objective.thermalHeadroom,
            satisfactionTarget = MinimalPlanner.SATISFIED_SCORE,
            gap = MinimalPlanner.SATISFIED_SCORE - beforeReading.objectiveScore,
            before = beforeReading,
            after = null,
            knobKey = knobKey,
            knobLabel = knobLabel,
            direction = null,
            fromValue = fromValue,
            toValue = toValue,
            appliedValue = null,
            stepFraction = 0f,
            predictedGain = null,
            predictedThermalC = null,
            predictionConfidence = null,
            candidates = emptyList(),
            verdict = verdict,
            detail = detail,
            objectiveDelta = null,
            thermalDeltaC = null,
            cpuDeltaPercent = null,
            batteryDeltaPercent = null,
            samplesBefore = 0,
            samplesAfter = 0,
            confidenceBefore = 0f,
            confidenceAfter = 0f,
            predictionErrorGain = null,
            safetyLevel = safetyEngine.status.value.level.name,
            kind = kind,
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
                if (ok) {
                    // بروفايل أساس يدوي بعد تدخل مقيس = رفض مقيس أيضًا.
                    noteUserOverride(MaxAiOverride.PROFILE, null)
                    runCatching { runCycleSingleFlight() }
                }
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
            thermalForecast = forecastSnapshot(),
            forecastErrorC = forecastError(),
            trust = lastTrust,
            exploration = lastExploration,
        ).mutate()
    }

}
