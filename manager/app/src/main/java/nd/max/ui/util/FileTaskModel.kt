/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * `MT-FM` — **المهام الخلفية**: نسخ/نقل/ضغط/حذف يجري في الخلفية والمستخدم يتنقّل.
 *
 * ولماذا: العملية الجارية في الواجهة تمنع التنقّل، ومنع التنقّل يسلب مدير الملفات
 * معناه (نسخ من هنا إلى هناك يعني التنقّل بينهما). فالمهمة تصير سجلًّا صغيرًا يُعلن
 * حالته ونسبته، والإلغاء متاح في أي لحظة.
 *
 * والنسبة **مقيسة لا مصنّعة**: إن لم تُقرأ كمّية الوجهة تُعاد `null` وتُعلَن «تقدّم
 * غير معروف» (ADR-07). شريط يتحرّك بلا قياس يقنع المستخدم بشيء لم يُقَس، وهو أسوأ من
 * غياب الشريط.
 */
package nd.max.ui.util

enum class FileTaskKind { Copy, Move, Compress, Extract, Delete }

enum class FileTaskState { Running, Done, Failed, Cancelled, Unknown }

/**
 * مهمة واحدة.
 *
 * و`reasonKey` **مفتاح نصّ** لا جملة: هذا الملف لا يعرف `R` ولا لغة المستخدم
 * (قاعدة النماذج في هذا المستودع)، والشاشة هي التي تترجم.
 */
data class FileTask(
    val id: Long,
    val kind: FileTaskKind,
    val sources: List<String>,
    val destination: String,
    val totalBytes: Long? = null,
    val doneBytes: Long? = null,
    val state: FileTaskState = FileTaskState.Running,
    val startedAtMs: Long = 0L,
    val endedAtMs: Long? = null,
    val reasonKey: String? = null,
) {
    val isRunning: Boolean get() = state == FileTaskState.Running

    /** نسبة صحيحة، أو `null` حين لا يُقاس هذا التقدّم — ولا تُخترع نسبة بديلة. */
    val percent: Int?
        get() {
            val total = totalBytes ?: return null
            val done = doneBytes ?: return null
            if (total <= 0L) return null
            return ((done * 100L) / total).coerceIn(0L, 100L).toInt()
        }

    val elapsedMs: Long
        get() = ((endedAtMs ?: startedAtMs) - startedAtMs).coerceAtLeast(0L)

    val count: Int get() = sources.size
}

object FileTaskQueue {

    /** ما يُعرض في الشريط: ثلاث مهمات — الجارية أولًا. أكثر من ذلك يحوّل الشريط إلى قائمة. */
    const val MAX_VISIBLE: Int = 3

    fun nextId(tasks: List<FileTask>): Long = (tasks.maxOfOrNull { it.id } ?: 0L) + 1L

    fun add(tasks: List<FileTask>, task: FileTask): List<FileTask> = tasks + task

    fun progress(tasks: List<FileTask>, id: Long, doneBytes: Long?): List<FileTask> =
        tasks.map { if (it.id == id) it.copy(doneBytes = doneBytes) else it }

    fun finish(
        tasks: List<FileTask>,
        id: Long,
        state: FileTaskState,
        atMs: Long,
        reasonKey: String? = null,
    ): List<FileTask> = tasks.map {
        if (it.id == id) it.copy(state = state, endedAtMs = atMs, reasonKey = reasonKey) else it
    }

    /**
     * طلب الإلغاء: المهمة تُوسم ملغاة فورًا فلا تنتظر الشريط أن يتحقّق من الجهاز.
     * والوقت الحقيقي للتنفيذ مسؤولية المنفّذ، لكن الإعلان للمستخدم واجب فوري.
     */
    fun cancel(tasks: List<FileTask>, id: Long, atMs: Long): List<FileTask> =
        finish(tasks, id, FileTaskState.Cancelled, atMs, reasonKey = "cancelled")

    fun running(tasks: List<FileTask>): List<FileTask> = tasks.filter { it.isRunning }

    /** الجارية أولًا، ثم ما انتهى (الأحدث أولًا) — الترتيب الذي يُقرأ منه. */
    fun visible(tasks: List<FileTask>): List<FileTask> {
        val running = tasks.filter { it.isRunning }
        val finished = tasks.filterNot { it.isRunning }.sortedByDescending { it.endedAtMs ?: it.startedAtMs }
        return (running + finished).take(MAX_VISIBLE)
    }

    /** إزالة مهام منتهية من الشريط — المهام الجارية لا تُزال. */
    fun clearFinished(tasks: List<FileTask>): List<FileTask> = tasks.filter { it.isRunning }
}
