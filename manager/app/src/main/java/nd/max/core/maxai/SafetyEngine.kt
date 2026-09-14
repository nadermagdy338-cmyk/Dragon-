package nd.max.core.maxai

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nd.max.core.diagnostics.DiagnosticCenter
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.DeviceStateCollector
import nd.max.core.hardware.PredictiveSafety
import nd.max.core.jni.PredictorBridge
import nd.max.ui.util.EventLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * محرك الأمان — الطبقة الوحيدة التي لا يعلوها أحد.
 *
 * الأولوية المطلقة (Owner.SAFETY فوق كل مالك في المُحكِّم): حدّ حراري
 * يتجاوز العتبة → سقف أداء آمن فورًا مهذا كان المتحكم: Max AI، ملف
 * تطبيق، أو تعديل يدوي. يتدخل ويتراجع بستيرِيسيس (لا يتأرجح حول
 * العتبة)، ويعمل دائمًا — تشغيل AI أو إيقافه لا يوقف الأمان.
 *
 * مبدأ FDE.AI المُتبنى: الحرارة في الاعتبار دائمًا، والتدخل وقائي —
 * تنبؤ nativePredictThermal يُقيَّم مع القياسات اللحظي كي يهبط السقف
 * قبل تجاوز الحد لا بعده.
 */
@Singleton
class SafetyEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ceilingKnobs: CpuCeilingKnobs,
    private val predictiveSafety: PredictiveSafety? = null // Optional predictive safety
) {
    companion object {
        const val TOKEN = "safety-engine"

        /** بدء التدخل: أعلى حرارة CPU/GPU المقيسة تتجاوز هذا الحد. */
        const val ENGAGE_TEMP_C = 48f

        /** تدخل حرج: سقف صارم + ملف توفير (المستوى الآمن). */
        const val CRITICAL_TEMP_C = 52f

        /** تراجع التدخل (هستيريسيس 5 درجات تحت بداية التدخل). */
        const val RELEASE_TEMP_C = 43f

        /** تنبؤ يتجاوز هذا الحد خلال الأفق الأمامي → تدخل وقائي. */
        const val PREDICTED_ENGAGE_C = 52f

        /** سقف المدى عند التدخل (نسبة مدى العتاد). */
        const val ENGAGE_CAP_FRACTION = 0.55f

        /** سقف المدى عند الحرج. */
        const val CRITICAL_CAP_FRACTION = 0.35f

        private const val PREF_INTERVENTIONS = "interventions"
        private val RETRY_DELAYS_MS = longArrayOf(120_000L, 600_000L, 1_800_000L)
    }

    private data class RetryState(
        val level: SafetyLevel,
        val capFraction: Float,
        val attempt: Int,
        val dueAtMs: Long,
    )

    private var retry: RetryState? = null

    /** قابلة للاستبدال في اختبارات JVM دون انتظار فعلي. */
    internal var monotonicNowMs: () -> Long = { SystemClock.elapsedRealtime() }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("maxai_safety", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(
        SafetyStatus(interventions = prefs.getLong(PREF_INTERVENTIONS, 0L))
    )
    val status: StateFlow<SafetyStatus> = _status.asStateFlow()

    val engaged: Boolean get() = _status.value.engaged
    val interventions: Long get() = _status.value.interventions

    /**
     * دورة تقييم واحدة — تُستدعى من حلقة MaxAiEngine الدائمة.
     *
     * @param thermalC أعلى حرارة CPU/GPU مقيسة الآن (°C).
     * @param deviceState Optional device snapshot for predictive safety
     * @param predictedThermalC أعلى قيمة في التنبؤ الأمامي، أو null
     * إن لم يتوفر نصاب كافٍ (صادق: العمل على اللحظي فقط).
     * @return الحالة بعد التقييم.
     */
    @JvmOverloads
    fun evaluate(
        thermalC: Float,
        deviceState: DeviceStateCollector.DeviceSnapshot? = null,
        predictedThermalC: Float? = null
    ): SafetyStatus {
        // Use PredictiveSafety if available
        val fromPredictive = if (predictiveSafety != null && deviceState != null) {
            val spikePredicted = predictiveSafety.predictThermalSpike(thermalC, deviceState)
            if (spikePredicted) thermalC + 4f else null
        } else null
        val predicted = fromPredictive ?: predictedThermalC ?: thermalC

        val current = _status.value
        if (thermalC <= 0f) return current

        val desired = when {
            thermalC >= CRITICAL_TEMP_C || predicted >= CRITICAL_TEMP_C + 2f ->
                SafetyLevel.CRITICAL
            thermalC >= ENGAGE_TEMP_C || predicted >= PREDICTED_ENGAGE_C ->
                SafetyLevel.ENGAGED
            current.engaged && thermalC >= RELEASE_TEMP_C -> current.level
            else -> SafetyLevel.NORMAL
        }

        val now = monotonicNowMs()
        val scheduledRetry = retry?.takeIf {
            it.level == desired && now >= it.dueAtMs
        }
        val next = when {
            desired == SafetyLevel.NORMAL && current.engaged -> {
                retry = null
                release(
                    thermalC,
                    "انخفضت الحرارة إلى ${thermalC.toInt()}°م تحت عتبة التراجع ${RELEASE_TEMP_C.toInt()}°م"
                )
            }
            desired == SafetyLevel.NORMAL -> current.copy(thermalC = thermalC)
            !current.engaged || desired != current.level -> {
                retry = null
                engage(thermalC, predicted, desired, isRetry = false)
            }
            scheduledRetry != null -> engage(thermalC, predicted, desired, isRetry = true)
            else -> current.copy(thermalC = thermalC)
        }

        if (next != current) _status.value = next
        return next
    }

    /**
     * Backward-compatible evaluate without deviceState for existing callers.
     */
    fun evaluate(thermalC: Float, predictedThermalC: Float?): SafetyStatus =
        evaluate(thermalC, null, predictedThermalC)

    private fun engage(
        thermalC: Float,
        predictedC: Float,
        level: SafetyLevel,
        isRetry: Boolean,
    ): SafetyStatus {
        val capFraction = if (level == SafetyLevel.CRITICAL) {
            CRITICAL_CAP_FRACTION
        } else {
            ENGAGE_CAP_FRACTION
        }
        val outcome = ceilingKnobs.cap(
            capFraction,
            ControlOwnership.Owner.SAFETY,
            TOKEN,
        )
        val enforcement = when {
            outcome.applied > 0 && outcome.failed == 0 && outcome.blocked == 0 -> SafetyEnforcement.APPLIED
            outcome.applied > 0 -> SafetyEnforcement.PARTIAL
            outcome.failed > 0 || outcome.blocked > 0 -> SafetyEnforcement.FAILED
            else -> SafetyEnforcement.UNAVAILABLE
        }
        if (enforcement == SafetyEnforcement.APPLIED) {
            retry = null
        } else {
            val previousAttempt = retry?.takeIf {
                it.level == level && it.capFraction == capFraction
            }?.attempt ?: 0
            val nextAttempt = (previousAttempt + 1).coerceAtMost(RETRY_DELAYS_MS.size)
            retry = RetryState(
                level = level,
                capFraction = capFraction,
                attempt = nextAttempt,
                dueAtMs = monotonicNowMs() + RETRY_DELAYS_MS[nextAttempt - 1],
            )
        }

        val count = if (isRetry) {
            _status.value.interventions
        } else {
            prefs.getLong(PREF_INTERVENTIONS, 0L) + 1L
        }
        if (!isRetry) prefs.edit().putLong(PREF_INTERVENTIONS, count).apply()

        val reason = when (level) {
            SafetyLevel.CRITICAL -> "حرارة ${thermalC.toInt()}°م " +
                (if (predictedC >= CRITICAL_TEMP_C + 2f) "(والتنبؤ ${predictedC.toInt()}°م) " else "") +
                "تجاوز الحد الحرج ${CRITICAL_TEMP_C.toInt()}°م — خفض جراحي مباشر لسقف التردد"
            else -> "حرارة ${thermalC.toInt()}°م " +
                (if (predictedC >= PREDICTED_ENGAGE_C) "(والتنبؤ ${predictedC.toInt()}°م) " else "") +
                "تجاوز عتبة الأمان ${ENGAGE_TEMP_C.toInt()}°م — سقف أداء آمن"
        }
        DiagnosticCenter.record(
            "safety",
            "SAFETY_${if (isRetry) "RETRY" else "ENGAGED"} level=$level " +
                "applied=${outcome.applied} blocked=${outcome.blocked} failed=${outcome.failed} :: ${outcome.detail}"
        )
        if (!isRetry) {
            EventLog.userAction(
                screen = "SafetyEngine",
                field = "thermal_guard",
                old = if (_status.value.engaged) _status.value.level.name else "normal",
                new = "$level@${thermalC.toInt()}C",
            )
        }

        return SafetyStatus(
            level = level,
            thermalC = thermalC,
            engaged = true,
            interventions = count,
            lastReason = reason,
            enforcement = enforcement,
            enforcementDetail = "applied=${outcome.applied}, blocked=${outcome.blocked}, failed=${outcome.failed}",
        )
    }

    private fun release(thermalC: Float, reason: String): SafetyStatus {
        retry = null
        ceilingKnobs.leaveAll(TOKEN)
        DiagnosticCenter.record(
            "safety",
            "SAFETY_RELEASED temp=${thermalC.toInt()}C — استرجاع خط الأساس"
        )
        EventLog.userAction(
            screen = "SafetyEngine",
            field = "thermal_guard",
            old = "engaged",
            new = "released@${thermalC.toInt()}C",
        )
        val current = _status.value
        return SafetyStatus(
            level = SafetyLevel.NORMAL,
            thermalC = thermalC,
            engaged = false,
            interventions = current.interventions,
            lastReason = reason,
        )
    }
}
