package nd.max.ui.viewmodel

import javax.inject.Inject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.MaxManagerProps
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.ManualControlLocks
import nd.max.ui.util.PropertyUtils

data class GpuStudioUiState(
    val loading: Boolean = true,
    val selection: GpuHardwareBackend.Selection? = null,
    val device: GpuHardwareBackend.Device? = null,
    val pending: GpuHardwareBackend.Request? = null,
    val baseline: GpuHardwareBackend.Baseline? = null,
    val historyMHz: List<Float> = emptyList(),
    val applying: Boolean = false,
    val lastResult: GpuHardwareBackend.TransactionResult? = null,
    val message: String? = null,
    val verifiedSnapshot: GpuHardwareBackend.Device? = null,
    /** Smart-intent label behind the current staged/verified state, or null when custom. */
    val mode: String? = null,
)

/** Session orchestrator. Hardware discovery and mutation stay in GpuHardwareBackend. */
class GpuStudioViewModel @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) : ViewModel() {
    var state by mutableStateOf(GpuStudioUiState())
        private set

    private var pollJob: Job? = null
    private var baselineProviderPath: String? = null

    fun load() {
        if (pollJob != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val selection = GpuHardwareBackend.selection()
            val device = selection.device
            if (baselineProviderPath != device?.path) {
                baselineProviderPath = device?.path
                state = state.copy(baseline = device?.let(GpuHardwareBackend::captureBaseline))
            }
            state = state.copy(
                loading = false,
                selection = selection,
                device = device,
            )
            startPolling()
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val selection = GpuHardwareBackend.selection()
                val device = selection.device
                val sample = device?.let { GpuHardwareBackend.frequencyMHz(it, it.currentFreq)?.toFloat() }
                val verified = state.verifiedSnapshot
                val drifted = verified != null && (
                    device == null ||
                        device.path != verified.path ||
                        device.minFreq != verified.minFreq ||
                        device.maxFreq != verified.maxFreq ||
                        device.governor != verified.governor
                    )
                val providerChanged = baselineProviderPath != device?.path
                if (providerChanged) baselineProviderPath = device?.path
                state = state.copy(
                    loading = false,
                    selection = selection,
                    device = device,
                    baseline = if (providerChanged) device?.let(GpuHardwareBackend::captureBaseline) else state.baseline,
                    pending = if (providerChanged) null else state.pending,
                    historyMHz = if (providerChanged) listOfNotNull(sample) else if (sample != null) (state.historyMHz + sample).takeLast(60) else state.historyMHz,
                    verifiedSnapshot = if (drifted || providerChanged) null else verified,
                    message = when {
                        providerChanged -> "تغير مزوّد GPU؛ أُعيدت معاينة الجلسة بأمان"
                        drifted -> "تغيرت الحالة الحية؛ أُبطل الحفظ السابق"
                        else -> state.message
                    },
                )
                delay(1_500)
            }
        }
    }

    fun stageMode(mode: GpuHardwareBackend.IntentMode) {
        val device = state.device ?: return
        val request = GpuHardwareBackend.requestForMode(device, mode)
        state = if (request == null) state.copy(message = "لا توجد قائمة ترددات موثوقة لهذا الوضع")
        else state.copy(pending = request, lastResult = null, message = modeLabel(mode), verifiedSnapshot = null, mode = mode.name.lowercase())
    }

    fun stageRange(min: Long, max: Long) {
        val device = state.device ?: return
        val request = GpuHardwareBackend.Request(min, max, state.pending?.governor)
        val error = GpuHardwareBackend.validate(device, request)
        state = if (error == null) state.copy(pending = request, lastResult = null, message = "نطاق ديناميكي قيد المعاينة", verifiedSnapshot = null, mode = null)
        else state.copy(message = errorMessage(error))
    }

    fun stageLock(frequency: Long) {
        val governor = state.pending?.governor
        val request = GpuHardwareBackend.Request(frequency, frequency, governor)
        val device = state.device ?: return
        val error = GpuHardwareBackend.validate(device, request)
        state = if (error == null) state.copy(pending = request, lastResult = null, message = "قفل دقيق قيد المعاينة", verifiedSnapshot = null, mode = null)
        else state.copy(message = errorMessage(error))
    }

    fun stageGovernor(governor: String) {
        val device = state.device ?: return
        val current = state.pending
        val request = GpuHardwareBackend.Request(
            minFreq = current?.minFreq,
            maxFreq = current?.maxFreq,
            governor = governor,
        )
        val error = GpuHardwareBackend.validate(device, request)
        state = if (error == null) state.copy(pending = request, lastResult = null, message = "الحاكم قيد المعاينة", verifiedSnapshot = null, mode = null)
        else state.copy(message = errorMessage(error))
    }

    fun cancelPreview() {
        state = state.copy(pending = null, lastResult = null, message = "تم إلغاء المعاينة", verifiedSnapshot = null, mode = null)
    }

    fun applyPreview() {
        val device = state.device ?: return
        val pending = state.pending ?: return
        state = state.copy(applying = true, message = "جارٍ التطبيق والتحقق")
        viewModelScope.launch(Dispatchers.IO) {
            val key = HardwareControlKey.gpuFrequency(device.name)
            val token = "gpu-studio-${System.currentTimeMillis()}"
            val baseline = GpuHardwareBackend.captureBaseline(device)
            val result = arbiter.submit(
                key = key,
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = token,
                desired = "${pending.minFreq ?: ""}:${pending.maxFreq ?: ""}",
                apply = { GpuHardwareBackend.apply(device, pending).verified },
                read = { GpuHardwareBackend.readCurrent(device) },
                baseline = baseline,
                restore = { GpuHardwareBackend.restoreBaseline(baseline) },
            )
            val refreshed = GpuHardwareBackend.selection()
            state = state.copy(
                applying = false,
                selection = refreshed,
                device = refreshed.device,
                pending = if (result.verified) null else pending,
                lastResult = result,
                verifiedSnapshot = result.actual?.takeIf { result.verified },
                message = when {
                    result.verified -> "تم التطبيق والتحقق من العتاد"
                    result.rollbackVerified == true -> "رفض العتاد التغيير وتمت استعادة الحالة السابقة"
                    result.rollbackAttempted -> "فشل التطبيق والاستعادة؛ راجع الحالة الحية"
                    else -> errorMessage(result.error)
                },
            )
        }
    }

    fun restoreSession() {
        val baseline = state.baseline ?: return
        state = state.copy(applying = true, message = "جارٍ استعادة بداية الجلسة")
        viewModelScope.launch(Dispatchers.IO) {
            val restored = if (baseline.fixedIndex != null || (baseline.minFreq != null && baseline.maxFreq != null) || baseline.governor != null) {
                GpuHardwareBackend.restoreBaseline(baseline)
            } else false
            val selection = GpuHardwareBackend.selection()
            state = state.copy(
                applying = false,
                selection = selection,
                device = selection.device,
                pending = null,
                lastResult = null,
                verifiedSnapshot = null,
                mode = null,
                message = if (restored) "تمت استعادة بداية الجلسة والتحقق منها" else "تعذر التحقق من الاستعادة",
            )
        }
    }

    fun saveVerifiedToTweaks() {
        val device = state.device ?: return
        val verified = state.verifiedSnapshot ?: return
        if (verified.path != device.path) return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.GpuStudio.MIN_FREQ, verified.minFreq?.toString().orEmpty())
            PropertyUtils.set(MaxManagerProps.GpuStudio.MAX_FREQ, verified.maxFreq?.toString().orEmpty())
            PropertyUtils.set(MaxManagerProps.GpuStudio.GOVERNOR, verified.governor.orEmpty())
            PropertyUtils.set(MaxManagerProps.GpuStudio.MODE, state.mode ?: "custom")
            withContext(Dispatchers.Main) {
                state = state.copy(
                    verifiedSnapshot = null,
                    message = "تم حفظ الحالة الموثقة في Tweaks",
                )
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private fun modeLabel(mode: GpuHardwareBackend.IntentMode): String = when (mode) {
        GpuHardwareBackend.IntentMode.EFFICIENCY -> "وضع الكفاءة قيد المعاينة"
        GpuHardwareBackend.IntentMode.ADAPTIVE -> "الوضع المتوازن التكيفي قيد المعاينة"
        GpuHardwareBackend.IntentMode.SUSTAINED -> "وضع الأداء المستدام قيد المعاينة"
    }

    private fun errorMessage(error: String?): String = when (error) {
        "unsupported-frequency" -> "التردد غير معلن من الدرافر"
        "unsupported-governor" -> "الحاكم غير مدعوم"
        "invalid-range" -> "الحد الأدنى أعلى من الحد الأقصى"
        "range-read-only-or-unproven" -> "التحكم في النطاق غير مثبت على هذا الجهاز"
        "governor-read-only" -> "الحاكم للقراءة فقط"
        else -> "تعذر تنفيذ الطلب بأمان"
    }
}
