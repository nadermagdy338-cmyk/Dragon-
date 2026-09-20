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
 * `MT-FM` — **البحث العميق بالاسم**: حدود معلنة ونتيجة تصدق عن نقصها.
 *
 * ولماذا الحدود في النموذج لا في الشاشة: البحث في شجرة `/data` بلا سقف عمق ولا سقف
 * نتائج يبتلع الجهاز دقائق. والأخطر أن نتيجة **مقطوعة** تُعرض كأنها كاملة، فيستنتج
 * المستخدم أن ملفًا غير موجود لأنه لم يظهر — وهذا هو الكذب الذي تمنعه ADR-07.
 * فكل حدٍّ هنا يترك **علمًا** في النتيجة يُقرأ في الشاشة ويُعلَن للمستخدم.
 */
package nd.max.ui.util

/** حدود البحث: العمق، وعدد النتائج، والمهلة. */
data class SearchLimits(
    val maxDepth: Int = DEFAULT_DEPTH,
    val maxResults: Int = DEFAULT_RESULTS,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_DEPTH: Int = 5
        const val MIN_DEPTH: Int = 1
        const val MAX_DEPTH: Int = 12
        const val DEFAULT_RESULTS: Int = 500
        const val MIN_RESULTS: Int = 10
        const val MAX_RESULTS: Int = 5_000
        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
        const val MIN_TIMEOUT_MS: Long = 1_000L
        const val MAX_TIMEOUT_MS: Long = 120_000L
    }
}

/** طلب بحث: من أين، وبأيّ نصّ، وبأيّ حدود. */
data class DeepSearchRequest(val root: String, val query: String, val limits: SearchLimits)

/**
 * نتيجة البحث — **بأعلامها** لا بعدد وحده.
 *
 * و`complete` لا تكون `true` إلا حين لم ينتهِ حدٌّ ولم يسقط مجلد: النقص يُعلن، ولا
 * يُقدَّم بحث ناقص كأنه مسح كامل.
 */
data class DeepSearchOutcome(
    val hits: List<FileEntry> = emptyList(),
    val scannedFolders: Int = 0,
    val unreadableFolders: Int = 0,
    val hitLimitReached: Boolean = false,
    val depthLimitReached: Boolean = false,
    val timedOut: Boolean = false,
    /** أُلغى بقرار المستخدم: نتيجة أضعف من «كاملة» ولا تُعرض أبدًا على أنها كذلك. */
    val cancelled: Boolean = false,
) {
    val complete: Boolean
        get() = !hitLimitReached && !depthLimitReached && !timedOut && !cancelled && unreadableFolders == 0
}

object FileSearchPlan {

    /** خطة بحث، أو `null` إن كان الاستعلام فارغًا (البحث بلا نصّ ليس بحثًا). */
    fun of(root: String, query: String, limits: SearchLimits = SearchLimits()): DeepSearchRequest? {
        val needle = query.trim()
        if (needle.isEmpty()) return null
        return DeepSearchRequest(
            root = FileBrowser.normalize(root),
            query = needle,
            limits = clamp(limits),
        )
    }

    /** تضييق الحدود إلى مدى معقول — فالحدود تأتي من واجهة قد تُخطئ في الرقم. */
    fun clamp(limits: SearchLimits): SearchLimits = SearchLimits(
        maxDepth = limits.maxDepth.coerceIn(SearchLimits.MIN_DEPTH, SearchLimits.MAX_DEPTH),
        maxResults = limits.maxResults.coerceIn(SearchLimits.MIN_RESULTS, SearchLimits.MAX_RESULTS),
        timeoutMs = limits.timeoutMs.coerceIn(SearchLimits.MIN_TIMEOUT_MS, SearchLimits.MAX_TIMEOUT_MS),
    )

    /** عمق مسار تحت الجذر: الجذر نفسه صفر، وابنه المباشر ١. */
    fun depthOf(root: String, path: String): Int {
        val base = FileBrowser.normalize(root)
        val target = FileBrowser.normalize(path)
        if (target == base) return 0
        if (!FileBrowser.isInside(target, base)) return Int.MAX_VALUE
        val rest = target.removePrefix(if (base == "/") "/" else "$base/")
        return rest.split('/').count { it.isNotEmpty() }
    }

    fun withinDepth(root: String, path: String, limits: SearchLimits): Boolean =
        depthOf(root, path) <= limits.maxDepth

    /** مطابقة الاسم — نفس قاعدة البحث السطحي، فلا يختلف بحثان في هذا المستودع. */
    fun matches(entry: FileEntry, query: String): Boolean = entry.name.contains(query, ignoreCase = true)

    /**
     * هل يتوقّف البحث الآن؟ تُستدعى قبل إضافة نتيجة، فالعلم يُرفع حينها فقط.
     * و[elapsedMs] يُمرَّر من الخارج: الزمن يُقاس بساعة المستدعي لا بساعة النموذج.
     */
    fun shouldStop(
        hits: Int,
        elapsedMs: Long,
        limits: SearchLimits,
    ): Boolean = hits >= limits.maxResults || elapsedMs >= limits.timeoutMs
}
