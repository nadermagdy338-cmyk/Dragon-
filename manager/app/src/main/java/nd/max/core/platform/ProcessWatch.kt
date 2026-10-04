/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.platform

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ما يطلبه مالك واحد من القارئ المشترك: كم صفًّا يحتاج، وكل كم يريد عيّنة. */
data class ProcessWatchSettings(val limit: Int, val intervalSeconds: Int)

/**
 * القارئ المشترك لجدول العمليات — **حلقة واحدة** لكل من يطلب، مهما كان عدد الطالبين.
 *
 * ### لماذا
 *
 * التراكب والشاشة يسألان السؤال نفسه: «أعطني جدول العمليات». ولو قرأ كلٌّ لنفسه لصار في الجهاز
 * `top` يعمل مرّتين كل ثانية على جهاز تُقاس فيه الألعاب — والأسوأ أنّ اللوحتين قد تعرضان
 * لحظتين مختلفتين فيبدو فرقهما كعطب. فالحلقة هنا واحدة، والطارئون يُسجّلون [start] ويُلغون
 * [stop]، والقراءة تتوقّف حين لا طالب.
 *
 * ### والعقد المعلَن: النافذة تقصّ بالمعالج
 *
 * القراءة تجلب أعلى [readLimit] عملية **بالمعالج**، ومن يعرض بالذاكرة يرتّب **داخل هذه النافذة**.
 * وهو حدّ حقيقي: عملية خاملة تمامًا بذاكرة ضخمة قد لا تكون في النافذة. عُولج بأنّ السقف أعلى من
 * أيّ عدد يطلبه عارض ([MAX_ROWS])، فالنافذة تغطّي ما يُعرض عادةً — ويُقال بدل أن يُخفى.
 *
 * ### والفواصل
 *
 * فاصل الحلقة **أقصر** ما طلبه الطالبون، وسقف الصفوف **أكبر** ما طلبوه: الواحد لا يُبطّئ الآخر،
 * ولا يقصّ القصير على الطويل.
 */
object ProcessWatch {

    /** سقف الصفوف المقروءة — أعلى من أيّ عدد تطلبه الشاشة أو التراكب. */
    const val MAX_ROWS = 60

    private const val DEFAULT_INTERVAL_SECONDS = 2

    private val _snapshot = MutableStateFlow(ProcessSample.noAnswer)

    /** آخر ما قُرئ. تبدأ بـ«لا جواب» لا بقائمة فارغة، فالعرض قبل أوّل عيّنة لا يكذب. */
    val snapshot: StateFlow<ProcessSample> = _snapshot.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val requests = mutableMapOf<String, () -> ProcessWatchSettings>()

    /** عدّاد الدورات — يتغيّر مع كل عيّنة، فيُعيد التكوين المحسوبات المشتقّة منه. */
    private val _samples = MutableStateFlow(0)
    val samples: StateFlow<Int> = _samples.asStateFlow()

    /**
     * يسجّل طالبًا. التسجيل لمالك واحد **لا يُكرَّر**: نداء ثانٍ بالمعرّف نفسه يستبدل الإعداد
     * ولا يُشغّل حلقة أخرى.
     */
    fun start(
        context: Context,
        owner: String,
        scope: CoroutineScope,
        settings: () -> ProcessWatchSettings
    ) {
        val appContext = context.applicationContext
        requests[owner] = settings
        if (jobs[owner]?.isActive == true) return
        jobs[owner] = scope.launch {
            while (isActive) {
                val plan = plan()
                val sample = withContext(Dispatchers.IO) {
                    ProcessFeed.read(
                        context = appContext,
                        limit = plan.limit,
                        // الترتيب هنا ثابت (المعالج) والتصفية والترتيب المعروضان في الشاشة:
                        // القارئ يجيب سؤال «ما يجري الآن»، وكل عارض يرتّب ما وصله.
                        sort = ProcessSort.Cpu,
                        scope = ProcessScope.All,
                        query = ""
                    )
                }
                _snapshot.value = sample
                _samples.value += 1
                delay(plan.intervalSeconds.coerceAtLeast(1) * 1000L)
            }
        }
    }

    /** يُلغي تسجيل طالب؛ وحين لا يبقى طالب تتوقّف الحلقة. */
    fun stop(owner: String) {
        requests.remove(owner)
        jobs.remove(owner)?.cancel()
    }

    /** بلا طالب لا قراءة — تُستعمل في الاختبار وفي إطفاء كل شيء عند إغلاق التطبيق. */
    fun stopAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        requests.clear()
    }

    private fun plan(): ProcessWatchSettings {
        val live = requests.values.mapNotNull { runCatching { it() }.getOrNull() }
        if (live.isEmpty()) return ProcessWatchSettings(limit = MAX_ROWS, intervalSeconds = DEFAULT_INTERVAL_SECONDS)
        return ProcessWatchSettings(
            limit = live.maxOf { it.limit }.coerceIn(1, MAX_ROWS),
            intervalSeconds = live.minOf { it.intervalSeconds }
        )
    }
}
