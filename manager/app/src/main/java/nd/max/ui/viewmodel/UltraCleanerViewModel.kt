/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * حالة شاشة التنظيف الفائق: **قياس، تأشير، حذف، وإعادة قياس**.
 *
 * وثلاث قواعد تحكم هذا الملفّ، وكلها من عقد §6:
 *
 * 1. **القياس لا يُقدَّر:** كل صفّ يحمل `Long?` — والـ`null` تعني «فشل القياس» وتُعرض
 *    «لم يُقس». ولا صفر يُكتب مكانها.
 * 2. **التأشير حالة جلسة:** يُبنى من `UltraCleanModel.safeSelection()` في كل فتح للشاشة،
 *    ولا يُخزَّن على الجهاز — فلا تعود فئة أطفأها المستخدم مرّةً معلَّمةً صامتة بعد يوم.
 * 3. **بعد التنظيف يُعاد القياس، ولا يُصدَّق الرقم المفترض:** المساحة المُزالة يجمعها المحرّك
 *    من فرق «قبل/بعد» لكل فئة، وهذا الرقم هو ما يُعرض — لا حجم ما طُلب حذفه.
 *
 * **والصلاحية تُقرأ سلبيًّا:** `cachedRootGranted()` لا تستدعي `su`، فلا تظهر نافذة صلاحية
 * لمجرد فتح شاشة تنظيف. ومن لا جذر له تُخفى عنه فئتان وتُعرض له جملة النطاق — لا زرّ يفشل
 * صامتًا ولا قائمة فئات فارغة.
 */
package nd.max.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.core.privilege.PrivilegeManager
import nd.max.ui.util.CleanMeasurement
import nd.max.ui.util.CleanMeasurementSet
import nd.max.ui.util.CleanOutcome
import nd.max.ui.util.UltraCleanCategory
import nd.max.ui.util.UltraCleanEngine
import nd.max.ui.util.UltraCleanModel
import javax.inject.Inject

data class UltraCleanerUiState(
    /** القياس الأول أو إعادة قياس جارية. */
    val measuring: Boolean = false,
    /** صحيح قبل أن يصل أول قياس — يفرّق في الشاشة بين «يُقاس» و«لم يُقس بعد». */
    val measuredOnce: Boolean = false,
    val measurements: CleanMeasurementSet = CleanMeasurementSet(),
    val selection: Set<UltraCleanCategory> = UltraCleanModel.safeSelection(),
    val cleaning: Boolean = false,
    /** مجاميع آخر عملية تنظيف: ما نُفِّذ، وما تحقّق، وما حُرِّر فعلًا. */
    val lastOutcomes: List<CleanOutcome> = emptyList(),
) {
    val totalBytes: Long get() = UltraCleanModel.total(selection, measurements).bytes

    val totalIsPartial: Boolean get() = UltraCleanModel.total(selection, measurements).hasUnmeasured

    /** ما حُرِّر في آخر عملية: مجموع فروق «قبل/بعد» المقيسة. */
    val freedBytes: Long? get() = lastOutcomes.takeIf { it.isNotEmpty() }?.sumOf { it.freedBytes }
}

@HiltViewModel
class UltraCleanerViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _state = MutableStateFlow(UltraCleanerUiState())
    val state: StateFlow<UltraCleanerUiState> = _state.asStateFlow()

    /**
     * الجذر متاح؟
     *
     * ويُقرأ عند كل نداء لا مرّة عند التركيب: من رجع من شاشة الصلاحيات ونجح طلبه يجب أن يرى
     * الفئات كلها في الجولة التالية — لا بعد إعادة فتح التطبيق.
     */
    private fun rootAvailable(): Boolean = PrivilegeManager.cachedRootGranted()

    /** يقيس كل فئة **مرئية** عند الطبقة الحالية، ويحفظ ما نجح فقط. */
    fun measureAll() {
        if (_state.value.measuring) return
        _state.value = _state.value.copy(measuring = true)
        viewModelScope.launch {
            val root = rootAvailable()
            val visible = UltraCleanCategory.entries.filter { UltraCleanModel.visibleAt(root, it) }
            val rows = withContext(Dispatchers.IO) {
                visible.map { UltraCleanEngine.measure(context, it, root) }
            }
            _state.value = _state.value.copy(
                measuring = false,
                measuredOnce = true,
                measurements = CleanMeasurementSet(rows),
                // والمختار يُصفّى إلى المرئي: فئة اختفت بحذف الجذر لا تظلّ في المجموع.
                selection = _state.value.selection.filter { it in visible }.toSet()
                    .ifEmpty { UltraCleanModel.safeSelection().filter { it in visible }.toSet() },
            )
        }
    }

    /** تأشير/إلغاء تأشير فئة. */
    fun toggle(category: UltraCleanCategory) {
        val current = _state.value.selection
        _state.value = _state.value.copy(
            selection = if (category in current) current - category else current + category
        )
    }

    /** إعادة الاختيار إلى المجموعة الآمنة (الكاش والصور المصغّرة والسجلات). */
    fun selectSafe() {
        _state.value = _state.value.copy(selection = UltraCleanModel.safeSelection())
    }

    /**
     * ينظّف المختار فئةً فئة، ثم **يعيد القياس**.
     *
     * والتسلسل لا التوازي: كل حذف يقرأ بعده، وأمران متوازيان على الشجرة نفسها يتنازعان على
     * المجلدات التي يتغيّر محتواها — والقراءة بعد الحذف تفقد معناها إن كان حذف آخر لم ينتهِ.
     */
    fun clean() {
        val current = _state.value
        if (current.cleaning || current.totalBytes <= 0L) return
        _state.value = current.copy(cleaning = true, lastOutcomes = emptyList())
        viewModelScope.launch {
            val root = rootAvailable()
            val outcomes = withContext(Dispatchers.IO) {
                current.selection.map { category ->
                    val before: CleanMeasurement? = current.measurements.of(category)
                    UltraCleanEngine.clean(context, category, before, root)
                }
            }
            _state.value = _state.value.copy(cleaning = false, lastOutcomes = outcomes)
            // وإعادة القياس بعد الحذف: الصفوف تعرض ما بقي، لا ما كان.
            measureAll()
        }
    }
}
