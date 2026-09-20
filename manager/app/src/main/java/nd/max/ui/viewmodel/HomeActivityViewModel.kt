package nd.max.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.core.maxai.MaxAiEngine
import nd.max.core.maxai.MaxAiEpisodeKind
import nd.max.ui.util.ActivitySignals
import javax.inject.Inject

/**
 * جسر **واحد** بين حالة المحرك وبطاقة «ما يحدث الآن؟» في الشاشة الرئيسية.
 *
 * **ولا نظام بيانات ثانٍ هنا:** لا قياس، ولا مؤقّت يقرأ العتاد، ولا تخزين. المصادر هي
 * المصادر القائمة نفسها — `MaxAiEngine.state` (وفيه آخر قرار والتطبيق في المقدمة) و
 * `safety` و`episodes` (اليومية المقيسة) و`profileRequest` (طلب قيد التنفيذ) — وهذا
 * الملف يقرؤها ويُسقطها فقط. فسبب وجوده أن الرئيسية لم تكن تقرأ المحرك أصلًا، فصارت
 * البطاقة تُبنى على ادّعاءات الواجهة بدل حقيقة المحرك.
 *
 * **وثلاث مسؤوليات لا يجوز أن تتسرّب لغيرها:**
 *
 * 1. **زمن فتح التطبيق**: المحرك يعرف *أي* تطبيق في المقدمة ولا يعرف *منذ متى*، فمن
 *    يجيب «فُتح الآن» يجب أن يتذكّر اللحظة. والذاكرة هنا في الذاكرة فقط، ومُهيّأة صريحة:
 *    **أول تطبيق نراه ليس حدث فتح** — كان مفتوحًا قبل أن يبدأ التطبيق، فادّعاء «فُتح الآن»
 *    عنده كذبٌ يقع في كل تشغيل.
 * 2. **اسم التطبيق**: الحزمة رقم، والاسم من `PackageManager` — ويُحلّ مرة واحدة لكل حزمة
 *    خارج الخيط الرئيسي. وفشل الحلّ لا يُسقط شيئًا: تُعرض الحزمة نفسها.
 * 3. **الإسقاط ليس هنا**: `HomeActivityModel.project` دالّة صافية تُقاس في JVM
 *    (`HomeActivityModelTest`)، وهذه الطبقة تُغذّيها بحقائق فقط.
 */
@HiltViewModel
class HomeActivityViewModel @Inject constructor(
    private val engine: MaxAiEngine,
    application: Application,
) : ViewModel() {

    /**
     * سياق التطبيق لا سياق الشاشة: هذا الـViewModel يعيش أطول من أي نشاط، وحمل نشاط
     * كان سيُسرّب شاشةً إلى كائن يُعاد استخدامه.
     */
    private val context: Context = application.applicationContext

    /** ما نعرفه عن التطبيق في المقدمة: أيّ حزمة، ومنذ متى (0 = لا حدث فتح). */
    private data class AppWatch(val packageName: String?, val sinceMs: Long)

    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())
    val appLabels: StateFlow<Map<String, String>> = labels.asStateFlow()

    /**
     * ذاكرة التطبيق في المقدمة. `runningFold` لا `scan` يدويًّا: الحالة السابقة جزء من
     * التعريف، فلا تصلح متغيّرات قابلة للتعديل تُقرأ من خيوط مختلفة.
     */
    private val appWatch = engine.state
        .map { state -> state.appContext.trim().takeIf { it.isNotEmpty() && it != SYSTEM_CONTEXT } }
        .distinctUntilChanged()
        .runningFold(AppWatch(null, 0L)) { previous, packageName ->
            when {
                packageName == null -> AppWatch(null, 0L)
                // أول رؤية في هذه الجلسة ليست حدث فتح: التطبيق كان مفتوحًا قبلنا.
                previous.packageName == null -> AppWatch(packageName, 0L)
                packageName == previous.packageName -> previous
                else -> AppWatch(packageName, System.currentTimeMillis())
            }
        }

    val signals: StateFlow<ActivitySignals> = combine(
        engine.state,
        engine.safety,
        engine.episodes,
        engine.profileRequest,
        appWatch,
    ) { state, safety, episodes, request, watch ->
        // اليومية تُرتَّب الأحدث أولًا (`MaxAiJournal.record`)، فالأول هو آخر حلقة.
        val episode = episodes.firstOrNull()
        val decision = state.lastDecision
        ActivitySignals(
            aiEnabled = state.aiEnabled,
            profileRequestInFlight = request.inFlight,
            safetyLevel = safety.level,
            safetyEngaged = safety.engaged,
            safetyReason = safety.lastReason.takeIf { it.isNotBlank() },
            lastVerdict = episode?.verdict,
            lastResult = decision?.result,
            lastKnobLabel = episode?.knobLabel ?: decision?.label,
            lastValue = episode?.appliedValue ?: episode?.toValue,
            // نصّ المحرك كما هو: الإسقاط لا يعيد صياغة قياس.
            lastDetail = decision?.reason?.takeIf { it.isNotBlank() } ?: episode?.detail,
            lastAtMs = decision?.timestampMs ?: 0L,
            lastExploration = episode?.kind == MaxAiEpisodeKind.PROBE || episode?.exploration == true,
            appContext = watch.packageName,
            appSinceMs = watch.sinceMs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ActivitySignals())

    init {
        viewModelScope.launch {
            appWatch
                .map { it.packageName }
                .distinctUntilChanged()
                .collect { packageName ->
                    if (packageName == null || labels.value.containsKey(packageName)) return@collect
                    val label = withContext(Dispatchers.IO) { resolveLabel(packageName) }
                    labels.value = labels.value + (packageName to (label ?: packageName))
                }
        }
    }

    /**
     * اسم التطبيق كما يعرضه النظام. والفشل يُعيد `null` فيُعرض المعرّف بدل أن يختفي السطر:
     * «فُتح com.tencent.ig» أقل جمالًا من «PUBG»، وأصدق من لا شيء.
     */
    private fun resolveLabel(packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        /** قيمة المحرك حين لا تطبيق حقيقي في المقدمة. */
        const val SYSTEM_CONTEXT = "system"

        /** يُغلق الجسر بعد ٥ ثوانٍ من بقاء الشاشة خارج التركيز، فيتوقّف الحساب لا العرض. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
