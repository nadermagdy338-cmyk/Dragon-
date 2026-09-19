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

package nd.max.ui.util

import java.util.Locale

/**
 * تحليل المساحة: قرارات خالصة بلا Android.
 *
 * فُصلت عن قارئ الملفات لسبب واحد: «أي مصرف يزيد حجمه» و«أي ملف هو الأكبر» قرارات
 * تُختبر في JVM عادي، بينما `File.length()` و`StatFs` لا يُختبران إلا على جهاز. وخلط
 * الاثنين هو ما يجعل عطبًا في التصنيف يظهر كرقم غريب على شاشة المستخدم بلا اختبار يمسكه.
 *
 * وهذا الملف لا يعرف `R` ولا Compose ولا `Context`: يُترجم ويُقاس في `tools/test_maxai_jvm.py`.
 */
enum class StorageBucketKind {
    Apps,
    Images,
    Video,
    Audio,
    Documents,
    Archives,
    Other
}

/** مصرف واحد: صنف، وحجمه بالبايت، وعدد ملفاته. */
data class StorageBucket(
    val kind: StorageBucketKind,
    val bytes: Long,
    val files: Int
)

/** عنصر كبير واحد. `name` محفوظ ليُعرض بلا تقسيم مسار في الواجهة. */
data class StorageLargestItem(
    val path: String,
    val name: String,
    val bytes: Long
)

/**
 * نتيجة مسح واحد.
 *
 * @param skippedDirectories مجلدات لم تُقرأ (صلاحية أو خطأ إدخال) — تُعدّ وتُعلَن، ولا
 *        تُسقط من غير أن يقول المستخدم إن المسح ناقص.
 * @param truncated صحيح إذا توقّف المسح عند السقف قبل أن ينتهي. ومعه لا يُقال «هذا كل
 *        ما في الجهاز»، بل «هذا ما قيس».
 */
data class StorageScanResult(
    val buckets: List<StorageBucket>,
    val largest: List<StorageLargestItem>,
    val scannedEntries: Int,
    val skippedDirectories: Int,
    val truncated: Boolean
) {
    val measuredBytes: Long get() = buckets.sumOf { it.bytes }

    val isEmpty: Boolean get() = scannedEntries == 0 && buckets.isEmpty()
}

object StorageScanModel {

    /** عدد «أكبر العناصر» المعروض. ثمانية تكفي شاشة، وتتجاوزها القائمة تُطيل بلا فائدة. */
    const val LARGEST_LIMIT = 8

    private val IMAGE_EXT = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "dng", "raw",
        "svg", "tif", "tiff"
    )
    private val VIDEO_EXT = setOf(
        "mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv", "m4v", "ts", "m2ts", "mpg", "mpeg"
    )
    private val AUDIO_EXT = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "wma", "mid", "midi", "aiff"
    )
    private val DOCUMENT_EXT = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf", "odt",
        "ods", "odp", "csv", "epub", "mobi", "azw3", "json", "xml", "html", "log"
    )
    private val ARCHIVE_EXT = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "iso", "jar", "apk", "apks",
        "xapk", "obb", "lz4", "tgz", "tbz2"
    )

    /**
     * صنف الملف من اسمه.
     *
     * القرار بالامتداد لا بنوع المدخل: `FileKind` في مدير الملفات يصف **شكل** المدخل
     * (مجلد/أرشيف/نصّ…) لأغراض العرض، وهذا يصف **مصرف المساحة** لأغراض الحساب. واستعمال
     * أحدهما مكان الآخر كان سيصنّف كل مجلد كأنه ملف.
     *
     * ملف بلا امتداد معروف يُحسب `Other` ولا يُسقط: إسقاطه يجعل مجموع المصارف لا يساوي
     * المقيس، وهو أسوأ من تصنيفٍ فاضح.
     */
    fun kindOf(fileName: String): StorageBucketKind {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0 || dot == fileName.length - 1) return StorageBucketKind.Other
        return when (fileName.substring(dot + 1).lowercase(Locale.US)) {
            in IMAGE_EXT -> StorageBucketKind.Images
            in VIDEO_EXT -> StorageBucketKind.Video
            in AUDIO_EXT -> StorageBucketKind.Audio
            in DOCUMENT_EXT -> StorageBucketKind.Documents
            in ARCHIVE_EXT -> StorageBucketKind.Archives
            else -> StorageBucketKind.Other
        }
    }

    /**
     * ترتيب ثابت للمصارف: الأكبر أولًا، ثم بالاسم عند التعادل.
     *
     * الترتيب بالحجم وحده يترك صنفين متساويين يتنقلان بين الجولات، فيبدو الرقم كأنه
     * يتغيّر من نفسه. والاسم يكسر التعادل بنفس النتيجة دائمًا.
     */
    fun rank(buckets: List<StorageBucket>): List<StorageBucket> =
        buckets.sortedWith(compareByDescending<StorageBucket> { it.bytes }.thenBy { it.kind.name })

    /**
     * إضافة ملف إلى المصارف، وإرجاع القائمة الجديدة.
     *
     * يرجع قائمة بدل أن يعدّل واحدة: نداء يُعدّل مخفيًّا كان سيصنع رقمًا يعتمد على عدد
     * المرات التي نودي فيها لا على الملفات التي مُسحت.
     */
    fun accumulate(buckets: List<StorageBucket>, kind: StorageBucketKind, bytes: Long): List<StorageBucket> {
        val safeBytes = bytes.coerceAtLeast(0L)
        val existing = buckets.firstOrNull { it.kind == kind }
        val updated = StorageBucket(kind, (existing?.bytes ?: 0L) + safeBytes, (existing?.files ?: 0) + 1)
        return buckets.filterNot { it.kind == kind } + updated
    }

    /**
     * يُبقي أكبر [limit] عنصر.
     *
     * التعادل يُكسر بالمسار تصاعديًّا: قائمتان بنفس الحجم يجب أن تنتج نفس الترتيب في كل
     * مسح، وإلا تغيّرت الشاشة بلا أن يتغيّر الملف.
     */
    fun keepLargest(
        current: List<StorageLargestItem>,
        candidate: StorageLargestItem,
        limit: Int = LARGEST_LIMIT
    ): List<StorageLargestItem> {
        if (limit <= 0) return emptyList()
        val merged = current + candidate
        return merged
            .sortedWith(compareByDescending<StorageLargestItem> { it.bytes }.thenBy { it.path })
            .take(limit)
    }

    /** النسبة المئوية الصحيحة، وصفر عندما لا يوجد مقام — لا قسمة على صفر ولا `NaN`. */
    fun percentOf(bytes: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((bytes.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    /**
     * زوج (إجمالي العُقد، المتاح منها) من مخرجات أدوات النظام.
     *
     * `StatFs` لا يُعلن العُقد أصلًا (لا `fileCount` ولا `availableFiles`)، فالمصدر
     * الوحيد `stat -f -c '%c %d'` أو `df -i` — وكلتا الصيغتين تختلف بين coreutils
     * وtoybox. ولذلك التحليل هنا لا في طبقة التنفيذ: يُقاس في JVM بمخرجات حقيقية من
     * الصيغتين، ولا يُخترع رقم إن لم يصلح أيّ سطر.
     *
     * ويعيد `null` عند الفشل — لا صفرًا، لأن الصفر يعني «لا عُقد متاحة» وهو خبر آخر.
     */
    fun parseInodeCounts(statOutput: String?, dfOutput: String?): Pair<Long, Long>? {
        statOutput?.trim()?.takeIf { it.isNotEmpty() }?.let { line ->
            val numbers = line.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
            if (numbers.size >= 2 && numbers[0] > 0L) return numbers[0] to numbers[1]
        }
        dfOutput?.lineSequence()?.forEach { raw ->
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.size >= 4) {
                val total = parts[1].toLongOrNull()
                val free = parts[3].toLongOrNull()
                if (total != null && total > 0L && free != null && free >= 0L) return total to free
            }
        }
        return null
    }

    /** حجم مقروء. الوحدات رموز عالمية، فلا تُترجم؛ والرقم لاتيني كباقي أرقام الشاشة. */
    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe >= 1024L * 1024L * 1024L * 1024L ->
                String.format(Locale.US, "%.2f TB", safe / 1099511627776.0)
            safe >= 1024L * 1024L * 1024L ->
                String.format(Locale.US, "%.2f GB", safe / 1073741824.0)
            safe >= 1024L * 1024L ->
                String.format(Locale.US, "%.1f MB", safe / 1048576.0)
            safe >= 1024L -> String.format(Locale.US, "%.0f KB", safe / 1024.0)
            else -> "$safe B"
        }
    }
}
