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
 * `MT-FM/ب` — محرّك البحث العميق بالاسم: كشف بالعرض (BFS) بحدود **معلنة**.
 *
 * ولماذا يقبل القارئ (`lister`) كوسيط: هذا يجعل المحرّك **قابلًا للقياس في JVM عادي**
 * بشجرة مصنوعة (تصاعد حدود العمق والنتائج والمهلة والإلغاء) بلا جهاز ولا شجرة `/data`
 * حقيقية. والمسار الحقيقي يمرّر `FileSystemEngine::list` — نفس القراءة التي تملأ اللوحة،
 * فلا يوجد قارئ ثانٍ يخالف الأول في سلوكه.
 */
package nd.max.ui.util

object FileSearchEngine {

    /**
     * بحث بالاسم تحت جذر الطلب.
     *
     * @param lister قارئ مجلد — يُمرَّر من الخارج ليُقاس المحرّك بشجرة مصنوعة.
     * @param clock ساعة يُمرَّر وقتها من الخارج؛ المهلة تُقاس بها لا بساعة داخلية.
     * @param isCancelled يُسأل في كل دورة: الإلغاء يجب أن يكون **فوريًّا** لا بعد انتهاء الشجرة.
     */
    fun search(
        request: DeepSearchRequest,
        lister: (String) -> DirectoryListing,
        clock: () -> Long = { System.currentTimeMillis() },
        isCancelled: () -> Boolean = { false },
    ): DeepSearchOutcome {
        val limits = request.limits
        val startedAt = clock()
        val hits = ArrayList<FileEntry>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue += FileBrowser.normalize(request.root) to 0

        var scanned = 0
        var unreadable = 0
        var hitLimit = false
        var depthLimit = false
        var timedOut = false

        while (queue.isNotEmpty()) {
            if (isCancelled()) {
                return DeepSearchOutcome(
                    hits = hits,
                    scannedFolders = scanned,
                    unreadableFolders = unreadable,
                    hitLimitReached = hitLimit,
                    depthLimitReached = depthLimit,
                    cancelled = true,
                )
            }
            if (clock() - startedAt >= limits.timeoutMs) {
                timedOut = true
                break
            }

            val (path, depth) = queue.removeFirst()
            when (val listing = lister(path)) {
                is DirectoryListing.Unreadable -> unreadable++
                is DirectoryListing.Entries -> {
                    scanned++
                    for (entry in listing.entries) {
                        if (FileSearchPlan.matches(entry, request.query)) {
                            hits += entry
                            if (hits.size >= limits.maxResults) {
                                hitLimit = true
                                break
                            }
                        }
                        if (entry.isDirectory) {
                            if (depth + 1 > limits.maxDepth) {
                                depthLimit = true
                            } else {
                                queue += entry.path to depth + 1
                            }
                        }
                    }
                }
            }
            if (hitLimit) break
        }

        return DeepSearchOutcome(
            hits = hits.sortedWith(compareBy({ it.path.count { c -> c == '/' } }, { it.name })),
            scannedFolders = scanned,
            unreadableFolders = unreadable,
            hitLimitReached = hitLimit,
            depthLimitReached = depthLimit,
            timedOut = timedOut,
        )
    }
}
