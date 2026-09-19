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
 * `OCR-10` — **مرشّح البحث**: بعد أن تكتب حرفين، المطلوب أن تُقلّص بـ«صور أكبر من ١٠ م.ب» لا
 * بأن تكتب اسمًا تعرفه أصلًا.
 *
 * **ولماذا تصنيف بالامتداد لا بـ`FileKind` وحده:** `FileKind` عندنا يقول «مجلد · ملف · رابط ·
 * غير ذلك» — أي **شكل** المدخل في نظام الملفات لا نوع محتواه. والفرق ليس أكاديميًّا: `IMG_2.jpg`
 * و`backup.zip` كلاهما `File` عند `FileKind`، والمرشّح الذي يطلبه المستخدم هو «صور» لا «ملفات».
 *
 * **وقاعدة الصدق في المجهول (وهي الأهم هنا):** حجم أو تاريخ **غير مقيس** (`null`) لا يُعَد
 * مطابقًا لمرشّح يقيسه. من رشّح «أكبر من ١٠ م.ب» يريد ما نعرف أنه أكبر؛ وإدخال ما لا نعرف حجمه
 * في النتيجة يجعل الرقم المعروض كاذبًا. والعدد المعروض في الشاشة يكشف الفرق فورًا — وهو نفس
 * انضباط «المجهول `status_unknown`» (ADR-07) مطبَّقًا على مرشّح ملفات.
 */
package nd.max.ui.util

object FileSearchFilters {

    /** تصنيف المحتوى بالامتداد. و`ANY` ليست نوعًا بل «لا ترشيح». */
    enum class Kind { ANY, FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK }

    /** حدود الحجم بالأسماء لا بالأرقام في الواجهة: القائمة تعرض «‏> ١٠ م.ب» لا «10485760». */
    enum class Size { ANY, OVER_1MB, OVER_10MB, OVER_100MB }

    /** نافذة زمنية على آخر تعديل. */
    enum class Age { ANY, TODAY, WEEK, MONTH }

    data class Filter(
        val kind: Kind = Kind.ANY,
        val size: Size = Size.ANY,
        val age: Age = Age.ANY,
    ) {
        val isActive: Boolean get() = kind != Kind.ANY || size != Size.ANY || age != Age.ANY
    }

    private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "dng", "raw")
    private val VIDEO = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "ts", "mpeg", "mpg", "wmv")
    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "amr", "mid", "midi", "aiff")
    private val DOCUMENT = setOf(
        "pdf", "doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp",
        "txt", "md", "csv", "json", "xml", "html", "log", "epub", "ini", "conf", "yaml", "yml"
    )
    private val ARCHIVE = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "lz4", "iso", "cab")
    private val APK = setOf("apk", "apks", "xapk", "apkm", "aab")

    /** امتداد الملف صغيرًا، أو `""` — والاسم الذي ينتهي بنقطة لا امتداد له. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase()
    }

    fun classify(name: String, isDirectory: Boolean): Kind {
        if (isDirectory) return Kind.FOLDER
        return when (extensionOf(name)) {
            in IMAGE -> Kind.IMAGE
            in VIDEO -> Kind.VIDEO
            in AUDIO -> Kind.AUDIO
            in DOCUMENT -> Kind.DOCUMENT
            in ARCHIVE -> Kind.ARCHIVE
            in APK -> Kind.APK
            else -> Kind.ANY
        }
    }

    fun sizeFloor(size: Size): Long? = when (size) {
        Size.ANY -> null
        Size.OVER_1MB -> 1024L * 1024L
        Size.OVER_10MB -> 10L * 1024L * 1024L
        Size.OVER_100MB -> 100L * 1024L * 1024L
    }

    fun ageWindowSeconds(age: Age): Long? = when (age) {
        Age.ANY -> null
        Age.TODAY -> 24L * 60L * 60L
        Age.WEEK -> 7L * 24L * 60L * 60L
        Age.MONTH -> 30L * 24L * 60L * 60L
    }

    /**
     * هل يطابق المدخل المرشّح؟ و[nowEpochSec] زمن «الآن» بالثواني — يُمرَّر لا يُقرأ من الساعة،
     * فيكون الناتج قابلًا للقياس بلا انتظار.
     */
    fun matches(entry: FileEntry, filter: Filter, nowEpochSec: Long): Boolean {
        if (!filter.isActive) return true

        // النوع: `ANY` في المدخل تعني «لم نعرف نوعه» — تُطابق فقط حين لا ترشيح نوع.
        if (filter.kind != Kind.ANY && classify(entry.name, entry.isDirectory) != filter.kind) return false

        val floor = sizeFloor(filter.size)
        if (floor != null) {
            val bytes = entry.sizeBytes ?: return false
            if (bytes < floor) return false
        }

        val window = ageWindowSeconds(filter.age)
        if (window != null) {
            val modified = entry.modifiedEpochSec ?: return false
            // `now - window`: المدخل الأحدث من النافذة يطابق، والمستقبلي أيضًا (ساعة الجهاز
            // قد تتأخر عن زمن ملف كُتب على تخزين آخر — ولا يُستبعد لأنه «من المستقبل»).
            if (modified < nowEpochSec - window) return false
        }
        return true
    }

    fun apply(entries: List<FileEntry>, filter: Filter, nowEpochSec: Long): List<FileEntry> {
        if (!filter.isActive) return entries
        return entries.filter { matches(it, filter, nowEpochSec) }
    }
}
