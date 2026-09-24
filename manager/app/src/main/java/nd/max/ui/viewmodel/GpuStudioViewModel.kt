package nd.max.ui.viewmodel

import javax.inject.Inject

import dagger.hilt.android.lifecycle.HiltViewModel
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
import nd.max.core.hardware.GpuTweakPersistence
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.ManualControlLocks
import nd.max.ui.util.PropertyUtils

/**
 * ناتج ما حدث، **برمز لا بجملة**.
 *
 * كان الحقل جملة عربية يكتبها الـViewModel، فتظهر في الواجهة الإنجليزية عربيةً — وهذا عطب
 * في الترجمة يتكرّر في كل شاشة تكتب نصّها في الطبقة الخلفية. والرمز هنا يُترجم في الشاشة،
 * و`reason` يبقى كما هو من العتاد (كود إنجليزي ثابت) لأنه حقيقة تقنية لا جملة للمستخدم.
 */
enum class GpuNoticeKind {
    STAGED_INTENT,
    STAGED_RANGE,
    STAGED_LOCK,
    STAGED_GOVERNOR,
    CANCELLED,
    /** The device exposes no trustworthy frequency table for this intent. */
    NO_TABLE,
    /** A staged request was refused before it reached the hardware. */
    REFUSED,
    VERIFIED,
    ROLLED_BACK,
    ROLLBACK_FAILED,
    PROVIDER_CHANGED,
    DRIFTED,
}

@androidx.compose.runtime.Immutable
data class GpuNotice(
    val kind: GpuNoticeKind,
    /** Machine code straight from the backend (e.g. `unsupported-frequency`), or null. */
    val reason: String? = null,
    val intent: GpuHardwareBackend.IntentMode? = null,
)

data class GpuStudioUiState(
    val loading: Boolean = true,
    val selection: GpuHardwareBackend.Selection? = null,
    val device: GpuHardwareBackend.Device? = null,
    val pending: GpuHardwareBackend.Request? = null,
    val baseline: GpuHardwareBackend.Baseline? = null,
    val historyMHz: List<Float> = emptyList(),
    val applying: Boolean = false,
    val lastResult: GpuHardwareBackend.TransactionResult? = null,
    val notice: GpuNotice? = null,
    val verifiedSnapshot: GpuHardwareBackend.Device? = null,
    /** Smart-intent label behind the current staged/verified state, or null when custom. */
    val mode: String? = null,
    /** Which smart intent is currently staged, so the row can show real selection. */
    val stagedIntent: GpuHardwareBackend.IntentMode? = null,
    /**
     * الحالة **المحفوظة** التي يعيد تطبيقها كل إقلاع، مقروءةً من المفاتيح لا مُقدَّرة.
     *
     * ووجودها في الحالة لأن الشاشة تحتاج أن تقولها: `GpuTweakPersistence.applySaved()`
     * يعيد تطبيق المفاتيح في كل إقلاع وبعد كل تراجع per-app، فمن اختار «افتراضي» في هذه
     * الجلسة يجد حالته القديمة عائدة عند أول إقلاع. والقرار في محو المحفوظ قرار مالك؛ وما
     * هنا **قراءة خالصة** (`loadValidated` لا تكتب ولا تنادي `su`).
     */
    val savedRequest: GpuHardwareBackend.Request? = null,
)

/**
 * Session orchestrator. Hardware discovery and mutation stay in GpuHardwareBackend.
 *
 * و`@HiltViewModel` ليست زخرفة: الـViewModel له مُنشئ **بوسائط** (`arbiter`)، فلا مُنشئ له
 * بلا وسائط. وبلا هذا الوسم لا يستطيع Hilt أن يبنيه عند طلبه بـ`hiltViewModel()`، وبلا
 * `hiltViewModel()` ينادي `viewModel()` المصنعَ الافتراضي فيرمي عند فتح الشاشة — وهو العطل
 * الذي كان يخرج التطبيق. (ونفس قالب `CpuCoreControlViewModel`.)
 */
@HiltViewModel
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
                // تُقرأ عند فتح الشاشة، ثم تُقرأ مرّة أخرى إن تغيّر المزوّد (في الاستقصاء أدناه):
                // المفاتيح لا تتغيّر إلا بفعل المستخدم في هذه الشاشة، فموضعا التغيّر معروفان.
                savedRequest = device?.let(GpuTweakPersistence::loadValidated),
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
                    // مع تغيّر المزوّد قد تقرأ المفاتيح حالةً لا تُصدَّق على عتادٍ آخر
                    // (`loadValidated` تُحكّم الطلب على الجهاز الجديد وترد `null` إن لم يصلح)
                    // فلا يبقى سطرٌ يقول «محفوظة» لعتادٍ ليس هذا.
                    savedRequest = if (providerChanged) device?.let(GpuTweakPersistence::loadValidated) else state.savedRequest,
                    notice = when {
                        providerChanged -> GpuNotice(GpuNoticeKind.PROVIDER_CHANGED)
                        drifted -> GpuNotice(GpuNoticeKind.DRIFTED)
                        else -> state.notice
                    },
                )
                delay(1_500)
            }
        }
    }

    fun stageMode(mode: GpuHardwareBackend.IntentMode) {
        val device = state.device ?: return
        val request = GpuHardwareBackend.requestForMode(device, mode)
        state = if (request == null) {
            state.copy(notice = GpuNotice(GpuNoticeKind.NO_TABLE, intent = mode))
        } else {
            state.copy(
                pending = request,
                lastResult = null,
                notice = GpuNotice(GpuNoticeKind.STAGED_INTENT, intent = mode),
                verifiedSnapshot = null,
                mode = mode.name.lowercase(),
                stagedIntent = mode,
            )
        }
    }

    fun stageRange(min: Long, max: Long) {
        val device = state.device ?: return
        val request = GpuHardwareBackend.Request(min, max, state.pending?.governor)
        val error = GpuHardwareBackend.validate(device, request)
        state = if (error == null) {
            state.copy(
                pending = request,
                lastResult = null,
                notice = GpuNotice(GpuNoticeKind.STAGED_RANGE),
                verifiedSnapshot = null,
                mode = null,
                stagedIntent = null,
            )
        } else {
            state.copy(notice = GpuNotice(GpuNoticeKind.REFUSED, reason = error))
        }
    }

    fun stageLock(frequency: Long) {
        val governor = state.pending?.governor
        val request = GpuHardwareBackend.Request(frequency, frequency, governor)
        val device = state.device ?: return
        val error = GpuHardwareBackend.validate(device, request)
        state = if (error == null) {
            state.copy(
                pending = request,
                lastResult = null,
                notice = GpuNotice(GpuNoticeKind.STAGED_LOCK),
                verifiedSnapshot = null,
                mode = null,
                stagedIntent = null,
            )
        } else {
            state.copy(notice = GpuNotice(GpuNoticeKind.REFUSED, reason = error))
        }
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
        state = if (error == null) {
            state.copy(
                pending = request,
                lastResult = null,
                notice = GpuNotice(GpuNoticeKind.STAGED_GOVERNOR),
                verifiedSnapshot = null,
                mode = null,
                stagedIntent = null,
            )
        } else {
            state.copy(notice = GpuNotice(GpuNoticeKind.REFUSED, reason = error))
        }
    }

    fun cancelPreview() {
        state = state.copy(
            pending = null,
            lastResult = null,
            notice = GpuNotice(GpuNoticeKind.CANCELLED),
            verifiedSnapshot = null,
            mode = null,
            stagedIntent = null,
        )
    }

    fun applyPreview() {
        val device = state.device ?: return
        val pending = state.pending ?: return
        state = state.copy(applying = true)
        viewModelScope.launch(Dispatchers.IO) {
            val key = HardwareControlKey.gpuFrequency(device.name)
            val token = "gpu-studio-${System.currentTimeMillis()}"
            val baseline = GpuHardwareBackend.captureBaseline(device)
            // القيمة المطلوبة والصيغة المقروءة من **نفس** الشكل: `GpuHardwareBackend`
            // يحمل الحقول التي يمكن للطلب أن يمسّها (المدى · المُحكِّم · قفل OPP).
            // كان المدى وحده، فطلبٌ يمسّ المُحكِّم أو يحرّر القفل لا يساوي المقروء
            // أبدًا — فيُصنَّف نجاحه فشلًا وتُعاد الحالة السابقة (وهو ما يراه
            // المستخدم: «اخترت فلا يتغير شيء»).
            val desired = GpuHardwareBackend.encodeRequest(pending)
            val arbiterResult = arbiter.submit(
                key = key,
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = token,
                desired = desired,
                // الطلب نفسه يُطبَّق لا نصّ يُفكّ من جديد: المُحكِّم لا يُطبِّق إلا
                // `desired` الخاص بهذا الطلب، فإعادة الترميز هنا كانت تُسقط
                // `releaseLock` لو أُضيف حقل للمخطط لاحقًا.
                apply = { GpuHardwareBackend.applyValidated(device, pending).writeSucceeded },
                read = {
                    GpuHardwareBackend.refresh(device.path)?.let { live ->
                        GpuHardwareBackend.encodeLive(live, pending)
                    }
                },
                baseline = GpuHardwareBackend.encodeLive(device, pending),
                restore = { GpuHardwareBackend.restoreBaseline(baseline) },
            )
            val refreshed = GpuHardwareBackend.selection()
            val result = GpuHardwareBackend.TransactionResult(
                requested = pending,
                actual = refreshed.device,
                writeSucceeded = arbiterResult.applied,
                verified = arbiterResult.verified,
                rollbackAttempted = arbiterResult.rollbackAttempted,
                rollbackVerified = arbiterResult.rollbackVerified,
                error = arbiterResult.error,
            )
            state = state.copy(
                applying = false,
                selection = refreshed,
                device = refreshed.device,
                pending = if (result.verified) null else pending,
                lastResult = result,
                verifiedSnapshot = refreshed.device.takeIf { result.verified },
                mode = state.mode.takeIf { result.verified },
                stagedIntent = state.stagedIntent.takeIf { result.verified },
                notice = when {
                    result.verified -> GpuNotice(GpuNoticeKind.VERIFIED, reason = result.error)
                    result.rollbackVerified == true -> GpuNotice(GpuNoticeKind.ROLLED_BACK, reason = result.error)
                    result.rollbackAttempted -> GpuNotice(GpuNoticeKind.ROLLBACK_FAILED, reason = result.error)
                    else -> GpuNotice(GpuNoticeKind.REFUSED, reason = result.error)
                },
            )
        }
    }

    fun restoreSession() {
        val baseline = state.baseline ?: return
        state = state.copy(applying = true)
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
                stagedIntent = null,
                notice = GpuNotice(
                    if (restored) GpuNoticeKind.CANCELLED else GpuNoticeKind.ROLLBACK_FAILED,
                ),
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
                state = state.copy(verifiedSnapshot = null, notice = null)
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

}
