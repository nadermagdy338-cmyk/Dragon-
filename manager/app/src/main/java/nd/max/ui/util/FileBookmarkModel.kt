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
 * `MT-FM` — **المفضّلة والسجل**: أول ما يجلبه مدير ملفات يعمل بالجذر.
 *
 * والمفضّلة ليست «المواقع السريعة» المزروعة: تلك مسارات عامّة يضعها المطوّر، وهذه
 * مسارات **اختارها المستخدم** (مجلد وحدة معيّن، أو `data/local/tmp`، أو مجلد لعبة).
 * ولذلك الإضافة والحذف **والترتيب** كلها أفعال مستخدم، والترتيب يعيش في النموذج.
 *
 * والسجل يحفظ ما زُرت لا ما بحثت: سقف صريح، وبلا تكرار متتالٍ، واليوم يُحسب بمفتاح
 * يُمرَّر من الخارج — فالمنطقة الزمنية شأن عرض، لا شأن نموذج يُقاس في JVM.
 */
package nd.max.ui.util

/** عنصر مفضّل: مساره وتسميته (التسمية الفارغة تعني «اسم المجلد»). */
data class FileBookmark(val path: String, val label: String = "") {
    val display: String
        get() = label.ifBlank { FileBrowser.nameOf(FileBrowser.normalize(path)) }
}

object FileBookmarks {

    /** الإضافة إلى **الرأس**: ما أُضيف للتوّ هو الأقرب للإصبع في المرة القادمة. */
    fun add(bookmarks: List<FileBookmark>, path: String, label: String = ""): List<FileBookmark> {
        val target = FileBrowser.normalize(path)
        if (bookmarks.any { FileBrowser.normalize(it.path) == target }) return bookmarks
        return listOf(FileBookmark(target, label)) + bookmarks
    }

    fun remove(bookmarks: List<FileBookmark>, path: String): List<FileBookmark> {
        val target = FileBrowser.normalize(path)
        return bookmarks.filterNot { FileBrowser.normalize(it.path) == target }
    }

    fun rename(bookmarks: List<FileBookmark>, path: String, label: String): List<FileBookmark> {
        val target = FileBrowser.normalize(path)
        return bookmarks.map { if (FileBrowser.normalize(it.path) == target) it.copy(label = label) else it }
    }

    /** إعادة ترتيب بالسحب: فهارس خارج المدى تُتجاهل بلا استثناء (هو نقر لا برمجة). */
    fun move(bookmarks: List<FileBookmark>, from: Int, to: Int): List<FileBookmark> {
        if (from !in bookmarks.indices) return bookmarks
        val target = to.coerceIn(0, bookmarks.lastIndex)
        if (from == target) return bookmarks
        val mutable = bookmarks.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(target, item)
        return mutable
    }

    fun contains(bookmarks: List<FileBookmark>, path: String): Boolean {
        val target = FileBrowser.normalize(path)
        return bookmarks.any { FileBrowser.normalize(it.path) == target }
    }
}

/** مدخل سجل: مسار وطابعه الزمني. */
data class HistoryEntry(val path: String, val atMs: Long)

object FileHistory {

    /** سقف معلن: يكفي أسابيع من التنقّل ولا يتحوّل إلى ملف بيانات يتضخّم. */
    const val LIMIT: Int = 200

    /**
     * إضافة زيارة.
     *
     * وزيارة المجلد نفسه مرّتين متتاليتين **تُحدَّث لا تُكرَّر**: من ضغط «تحديث» عشر
     * مرات لا يريد عشرة أسطر في السجل.
     */
    fun push(history: List<HistoryEntry>, path: String, atMs: Long): List<HistoryEntry> {
        val target = FileBrowser.normalize(path)
        val without = history.filterNot { FileBrowser.normalize(it.path) == target }
        return (listOf(HistoryEntry(target, atMs)) + without).take(LIMIT)
    }

    fun clear(): List<HistoryEntry> = emptyList()

    /**
     * تجميع باليوم. والمفتاح يُمرَّر من الخارج لأن التوقيت المحلي قرار عرض:
     * الاختبار يمرّر مفتاحًا مصنوعًا فيقيس التجميع بلا ساعة ولا منطقة زمنية.
     */
    fun grouped(
        history: List<HistoryEntry>,
        dayKey: (Long) -> String,
    ): List<Pair<String, List<HistoryEntry>>> {
        val groups = LinkedHashMap<String, MutableList<HistoryEntry>>()
        for (entry in history.sortedByDescending { it.atMs }) {
            groups.getOrPut(dayKey(entry.atMs)) { mutableListOf() } += entry
        }
        return groups.map { (key, value) -> key to value.toList() }
    }
}
