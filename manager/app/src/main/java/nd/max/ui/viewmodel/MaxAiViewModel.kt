package nd.max.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import nd.max.core.maxai.MaxAiEngine
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.SafetyStatus
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel شاشة MAX AI — غلاف رقيق فوق المحرك الموحد.
 *
 * لا منطق قرار هنا إطلاقًا: كل ما تراه الواجهة يأتي من حالة المحرك
 * الحقيقية (أعداد فعلية)، وكل ما تفعله الواجهة يمر بالمحرك (تفعيل/
 * إيقاف، طلب ملف يدوي) كي تظل سلسلة التنفيذ واحدة:
 * UI → ViewModel → MaxAiEngine → SafetyEngine → HardwareControlArbiter.
 */
@HiltViewModel
class MaxAiViewModel @Inject constructor(
    private val engine: MaxAiEngine,
) : ViewModel() {

    val state: StateFlow<MaxAiState> = engine.state
    val safety: StateFlow<SafetyStatus> = engine.safety

    /** المفتاح الرئيسي: تفعيل/إيقاف Max AI (قرار المستخدم وحده). */
    fun setAiEnabled(enabled: Boolean) {
        engine.setAiEnabled(enabled)
    }

    /**
     * طلب ملف يدوي. AI مطفأ → تنفيذ فوري. AI مفعل → يُحفظ معلقًا
     * ويُطبق لحظة الإيقاف (يعيد true إذا نُفِّذ فورًا).
     */
    fun requestProfile(profileId: String, label: String): Boolean =
        engine.requestManualProfile(profileId, label)

    /** إجبار دورة محرك فورية لتحديث الحالة المعروضة بلا انتظار. */
    fun refresh() {
        viewModelScope.launch { engine.requestRefresh() }
    }
}
