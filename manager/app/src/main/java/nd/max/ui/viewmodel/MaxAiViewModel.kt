package nd.max.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nd.max.core.maxai.MaxAiEngine
import nd.max.core.maxai.MaxAiCycleStatus
import nd.max.core.maxai.MaxAiEpisode
import nd.max.core.maxai.MaxAiInsights
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.core.maxai.SafetyStatus
import javax.inject.Inject

/**
 * ViewModel شاشة MAX AI — غلاف رقيق فوق المحرك الموحد.
 *
 * لا منطق قرار هنا إطلاقًا: كل ما تراه الواجهة يأتي من حالة المحرك
 * الحقيقية (أعداد فعلية)، وكل ما تفعله الواجهة يمر بالمحرك (تفعيل/
 * إيقاف، طلب ملف يدوي) كي تظل سلسلة التنفيذ واحدة:
 * UI → ViewModel → MaxAiEngine → SafetyEngine → HardwareControlArbiter.
 *
 * المضاف مع الخط الزمني: حلقات الدفتر ولقطة المعرفة المشتقة منها.
 * نسخ خرائط الأثر واستخلاص المعرفة يجريان خارج خيط الواجهة؛ قفل
 * اللقطة مشترك مع حفظ نتائج التعلّم، وقد ينتظر اكتمال كتابة القرص.
 */
@HiltViewModel
class MaxAiViewModel @Inject constructor(
    private val engine: MaxAiEngine,
) : ViewModel() {

    val state: StateFlow<MaxAiState> = engine.state
    val safety: StateFlow<SafetyStatus> = engine.safety
    val profileRequest: StateFlow<ProfileRequestState> = engine.profileRequest
    val cycleStatus: StateFlow<MaxAiCycleStatus> = engine.cycleStatus
    private var refreshJob: Job? = null

    /** Presentation only: no polling or hardware work, stopped with the last subscriber. */
    val nowMs: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0L), System.currentTimeMillis())

    /** حلقات القرار الحقيقية، الأحدث أولًا — مصدر الخط الزمني. */
    val episodes: StateFlow<List<MaxAiEpisode>> = engine.episodes

    /**
     * لقطة المعرفة: حكم مقيس لكل مقبض + صدق التنبؤ + محصلة الأحكام.
     *
     * تُعاد الاشتقاق عند كل حلقة جديدة فقط، لأن خرائط الأثر لا تتغير إلا
     * مع حلقة مقيسة جديدة.
     */
    val insights: StateFlow<MaxAiInsights.Snapshot> = engine.episodes
        .map { MaxAiInsights.derive(engine.effectsSnapshot(), it) }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MaxAiInsights.Snapshot(),
        )

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

    /** إجبار دورة محرك فورية لتحديث الحالة المعروضة بلا انتطار. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) { engine.requestRefresh() }
    }

    /**
     * يمسح دفتر الحلقات — سرد فقط، لا سلطة ولا معرفة متعلّمة.
     *
     * يُنفَّذ على IO لأن الكتابة على القرص تجري هناك، ومسحه يُحدِّث
     * `episodes` تلقائيًا فتفرغ الشاشة بلا حالة إضافية تُصان في الViewModel.
     */
    fun clearJournal() {
        viewModelScope.launch(Dispatchers.IO) { engine.clearJournal() }
    }

    /**
     * تفضيل المستخدم لوزن الهدف (قرار #10): أداء / توازن / بطارية.
     *
     * يُسأل مرة عند التفعيل ثم يظل قابلًا للتعديل. الوزن يغير اتجاه
     * القرار وترتيب المقابض — لا يبدل ملفًا، فالمستخدم يحدد الأولوية
     * والعقل يختار المقبض.
     */
    fun setObjectivePreference(preference: String) {
        viewModelScope.launch(Dispatchers.IO) { engine.setObjectivePreference(preference) }
    }

}
