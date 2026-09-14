package nd.max.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nd.max.core.maxai.MaxAiEngine
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
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
    val profileRequest: StateFlow<ProfileRequestState> = engine.profileRequest

    /** المفتاح الرئيسي: تفعيل/إيقاف Max AI (قرار المستخدم وحده). */
    fun setAiEnabled(enabled: Boolean) {
        engine.setAiEnabled(enabled)
    }

    /** اختيار ملف أساس يدوي يُطبَّق فورًا عبر حد التوافق الخارجي. */
    fun requestProfile(profileId: String, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            engine.requestManualProfile(profileId, label)
        }
    }

    /** إجبار دورة محرك فورية لتحديث الحالة المعروضة بلا انتظار. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { engine.requestRefresh() }
    }

    /**
     * تفضيل المستخدم لوزن الهدف (قرار #10): أداء / توازن / بطارية.
     *
     * يُسأل مرة عند التفعيل ثم يظل قابلًا للتعديل. الوزن يغيّر اتجاه
     * القرار وترتيب المقابض — لا يبدّل ملفًا، فالمستخدم يحدد الأولوية
     * والعقل يختار المقبض.
     */
    fun setObjectivePreference(preference: String) {
        viewModelScope.launch(Dispatchers.IO) { engine.setObjectivePreference(preference) }
    }

    /** التفضيل الحالي لعرضه في الواجهة (توازن قبل أن يُسأل المستخدم). */
    fun objectivePreference(): String = engine.objectivePreference()
}
