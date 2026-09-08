package nd.max.core.maxai

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nd.max.core.diagnostics.DiagnosticCenter
import nd.max.core.hardware.ControlOwnership
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
 * تنبؤ nativePredictThermal يُقيَّم مع القياس اللحظي كي يهبط السقف
 * قبل تجاوز الحد لا بعده.
 */
@Singleton
class SafetyEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ceilingKnobs: CpuCeilingKnobs,
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
    }

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
     * @param predictedThermalC أعلى قيمة في التنبؤ الأمامي، أو null
     * إن لم يتوفر نصاب كافٍ (صادق: العمل على اللحظي فقط).
     * @return الحالة بعد التقييم.
     */
    fun evaluate(thermalC: Float, predictedThermalC: Float?): SafetyStatus {
        val predicted = predictedThermalC ?: thermalC
        val current = _status.value

        // بلا قياس صالح (0 = تعذر قراءة المناطق): لا نتصرف على عمى.
        if (thermalC <= 0f) return current

        val next = when {
            thermalC >= CRITICAL_TEMP_C || predicted >= CRITICAL_TEMP_C + 2f ->
                engage(
                    thermalC, predicted,
                    capFraction = CRITICAL_CAP_FRACTION,
                    reason = "حرارة ${thermalC.toInt()}°م " +
                        (if (predicted >= CRITICAL_TEMP_C + 2f) "(والتنبؤ ${predicted.toInt()}°م) " else "") +
                        "تجاوزت الحد الحرج ${CRITICAL_TEMP_C.toInt()}°م — سقف أمان صارم + ملف توفير"
                )
            thermalC >= ENGAGE_TEMP_C || predicted >= PREDICTED_ENGAGE_C ->
                engage(
                    thermalC, predicted,
                    capFraction = ENGAGE_CAP_FRACTION,
                    reason = "حرارة ${thermalC.toInt()}°م " +
                        (if (predicted >= PREDICTED_ENGAGE_C) "(والتنبؤ ${predicted.toInt()}°م) " else "") +
                        "تجاوزت عتبة الأمان ${ENGAGE_TEMP_C.toInt()}°م — سقف أداء آمن"
                )
            current.engaged && thermalC >= RELEASE_TEMP_C ->
                current // بين التراجع والبداية: نبقى متدخلين (هستيريسيس)
            current.engaged && thermalC < RELEASE_TEMP_C ->
                release(thermalC, "انخفضت الحرارة إلى ${thermalC.toInt()}°م تحت عتبة التراجع ${RELEASE_TEMP_C.toInt()}°م")
            else ->
                SafetyStatus(
                    level = SafetyLevel.NORMAL,
                    thermalC = thermalC,
                    engaged = false,
                    interventions = current.interventions,
                    lastReason = current.lastReason,
                )
        }

        if (next !== current) _status.value = next
        return next
    }

    private fun engage(
        thermalC: Float,
        predictedC: Float,
        capFraction: Float,
        reason: String,
    ): SafetyStatus {
        val outcome = ceilingKnobs.cap(
            capFraction,
            ControlOwnership.Owner.SAFETY,
            TOKEN,
        )
        val critical = thermalC >= CRITICAL_TEMP_C
        if (critical) {
            // المستوى الآمن عند الحرج: ملف توفير الطاقة عبر مسار AI
            // المصرَّح به (يحترم بوابة الوحدة ولا يدهس ملكية أعلى).
            runCatching { nd.max.core.hardware.ProfileApplier.applyFromAi("3") }
                .onFailure {
                    DiagnosticCenter.record(
                        "safety",
                        "safety eco profile apply failed: ${it.message}"
                    )
                }
        }

        val count = prefs.getLong(PREF_INTERVENTIONS, 0L) + 1L
        prefs.edit().putLong(PREF_INTERVENTIONS, count).apply()

        val level = if (critical) SafetyLevel.CRITICAL else SafetyLevel.ENGAGED
        DiagnosticCenter.record(
            "safety",
            "SAFETY_ENGAGED level=$level temp=${thermalC.toInt()}C predicted=${predictedC.toInt()}C " +
                "applied=${outcome.applied} blocked=${outcome.blocked} failed=${outcome.failed} :: ${outcome.detail}"
        )
        EventLog.userAction(
            screen = "SafetyEngine",
            field = "thermal_guard",
            old = "normal",
            new = "$level@${thermalC.toInt()}C",
        )

        return SafetyStatus(
            level = level,
            thermalC = thermalC,
            engaged = true,
            interventions = count,
            lastReason = reason,
        )
    }

    private fun release(thermalC: Float, reason: String): SafetyStatus {
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
