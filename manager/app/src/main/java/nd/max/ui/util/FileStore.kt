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
 * `MT-FM-04` — **مخزن المفضّلة والسجل**: ما اختاره المستخدم يبقى بعد إغلاق الشاشة.
 *
 * ولماذا ملفّان لا ملف واحد، ولا `SharedPreferences`: النموذجان (`FileBookmark` ·
 * `HistoryEntry`) قائمان ومُختبران، فالمطلوب **ترميز أمين** لا نظام تخزين جديد. والترميز
 * هنا سطرٌ لكل عنصر بفاصل محجوز، فيُقرأ بالعين عند الحاجة، ولا يحتاج مكتبةً تُضاف
 * إلى المشروع لأجل قائمتين.
 *
 * والقاعدة الحاكمة: **ما لا يُفهم لا يُسقط الشاشة**. ملف قديم أو مبتور أو مكتوب بيد
 * يعطي ما فُهم ويُسقط ما لم يُفهم — لا استثناء يصل إلى المستخدم.
 */
package nd.max.ui.util

import java.io.File

/**
 * سطرٌ لا **حرف مرئيّ** فيه (بايتات ثنائية · بايت NUL · محارف تحكّم) ليس مسارًا اختاره
 * مستخدم. ولهذا يُفحص قبل `trim`، لأن `trim` تُنزع المسافات وحدها ولا ترى `\u0000`
 * و`\u007F` — فيمرّ ملفٌّ ثنائي كـ«مسار» مشوّه بدل أن يُسقط نفسه.
 */
private fun isVisiblePath(raw: String): Boolean =
    raw.any { !it.isISOControl() && !it.isWhitespace() }

/** ترميز عناصر المفضّلة. */
object FileBookmarkCodec {

    /** سقف معلن للمفضّلة: قائمة تُستعمل بالإصبع لا ملف بيانات يتضخّم. */
    const val MAX: Int = 50

    /** فاصل محجوز داخل السطر — يُنزَع من القيم قبل الكتابة فلا يُقسَّم سطر إلى عنصرين. */
    private const val FIELD = "\u001F"

    fun encode(bookmarks: List<FileBookmark>): String =
        bookmarks.take(MAX).joinToString("\n") { bookmark ->
            clean(bookmark.path) + FIELD + clean(bookmark.label)
        }

    /**
     * يفهم ثلاث حالات: سطر بفاصل (نسختنا) · سطر بمسار وحده (نسخة أقدم أو كتابة بيد) ·
     * وسطرًا لا مسار فيه (يُسقط وحده). والتكرار يُدمج بالمسار المُطبَّع — الأسبق يفوز.
     */
    fun decode(text: String?): List<FileBookmark> {
        if (text.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<FileBookmark>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val at = line.indexOf(FIELD)
            val rawPath = (if (at < 0) line else line.substring(0, at)).trim()
            // مسار فارغ يُسقط السطر: `normalize("")` تُعيد `/`، ولو مرّت لسكن الجذر في المفضّلة.
            // وكذلك ما لا حرف مرئيّ فيه: لا يُعرض «مسار» من بايتات تحكّم.
            if (!isVisiblePath(rawPath)) continue
            val path = FileBrowser.normalize(rawPath)
            if (!seen.add(path)) continue
            val rawLabel = if (at < 0) "" else line.substring(at + 1)
            result += FileBookmark(path, rawLabel.trim().takeIf { it.isNotBlank() }.orEmpty())
            if (result.size >= MAX) break
        }
        return result
    }

    private fun clean(value: String): String =
        value.replace(FIELD, "").replace("\n", " ").replace("\r", "")
}

/** ترميز مدخلات السجل. */
object FileHistoryCodec {

    /** فاصل محجوز كما في المفضّلة. */
    private const val FIELD = "\u001F"

    fun encode(history: List<HistoryEntry>): String =
        history.take(FileHistory.LIMIT).joinToString("\n") { entry ->
            clean(entry.path) + FIELD + entry.atMs.toString()
        }

    /**
     * والطابع الزمني شرط لا زينة: سطر بلا زمن صالح (أو بزمن غير موجب) يُسقط وحده،
     * فلا يظهر في السجل يومٌ لا وجود له. والباقي يُرتَّب كما كُتب (الأحدث أولًا).
     */
    fun decode(text: String?): List<HistoryEntry> {
        if (text.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<HistoryEntry>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val at = line.indexOf(FIELD)
            val rawPath = (if (at < 0) "" else line.substring(0, at)).trim()
            if (!isVisiblePath(rawPath)) continue
            val path = FileBrowser.normalize(rawPath)
            val atMs = line.substring(at + 1).trim().toLongOrNull() ?: continue
            if (atMs <= 0L) continue
            if (!seen.add(path)) continue
            result += HistoryEntry(path, atMs)
            if (result.size >= FileHistory.LIMIT) break
        }
        return result
    }

    private fun clean(value: String): String =
        value.replace(FIELD, "").replace("\n", " ").replace("\r", "")
}

/**
 * مخزن ملف واحد. **استعمل كل ملف لغرض واحد** (ملف للمفضّلة وملف للسجل): الدالّتان
 * تُعيدان ما فُهم، وفشل القراءة أو الكتابة لا يُسقط الشاشة — ما لم يُقرأ يعود افتراضيًّا،
 * وما لم يُكتب يبقى في الذاكرة حتى الجلسة القادمة.
 */
class FileStore(private val file: File) {

    fun loadBookmarks(): List<FileBookmark> = FileBookmarkCodec.decode(read())

    fun saveBookmarks(bookmarks: List<FileBookmark>) = write(FileBookmarkCodec.encode(bookmarks))

    fun loadHistory(): List<HistoryEntry> = FileHistoryCodec.decode(read())

    fun saveHistory(history: List<HistoryEntry>) = write(FileHistoryCodec.encode(history))

    private fun read(): String? = runCatching {
        if (file.isFile) file.readText() else null
    }.getOrNull()

    private fun write(text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
    }
}
