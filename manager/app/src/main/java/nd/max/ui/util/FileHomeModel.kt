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
 * `UX-01` — **نموذج «الرئيسية»**: التصنيفات الستّة، وعدّ كل تصنيف وحجمه، وحالة كل صفّ.
 *
 * **ولماذا نموذجٌ قبل واجهة:** الواجهة لا تُقاس في JVM، وهذا الحساب هو ما يُخطئ فعلًا —
 * رقم يُعرض خطأً في شاشة ملفات أسوأ من رقم غائب. فيُفصل الحساب هنا، ويُقاس بمدخلات وصفيّة
 * تُنتج مخرجات متوقّعة، ثمّ تُرسم النتيجة في `UX-03` بلا حساب داخل الرسم.
 *
 * **والقاعدة الحاكمة: لا صفر كاذب.** المصدر (MediaStore أو مسح محدود) قد يعجز عن القراءة،
 * وقد لا يُسمح بها؛ وكلاهما **مجهول** لا `0`:
 *
 * | ما وصل | ما يُعرض | لماذا |
 * | --- | --- | --- |
 * | `permitted = false` | `بلا إذن` | لا ندّعي فقرًا في مكان مُنعنا منه |
 * | `count = null` | `غير مقروء` | «لم أقرأ» ≠ «لا شيء» (ADR-07) |
 * | `count = 0` | `فارغ` | الصفر الوحيد الصادق: قرأنا فعلًا ولم نجد |
 * | `count > 0` وحجم مجهول | العدد بلا حجم | العدّ معلوم والحجم مجهول — لا يُخترع صفر |
 *
 * و**الصفر في الحجم ليس كذبًا**: عشرة ملفّات فارغة حجمها `0 B` حقيقة، فالحجم الصفريّ
 * يُعرض كما هو؛ والمقصود بالمجهول هو **الغياب** (`null`) لا الصفر. وكذلك `count > 0` وحجمه
 * صفر يبقى صفرًا — لأن المصدر قرأ ووجد، لا لأننا افترضنا.
 *
 * **وما لا يفعله هذا الملف:** لا يقرأ، ولا يرسم، ولا يعرف `R` ولا `MediaStore`. مدخلاته
 * قائمة قراءات، ومخرجاته صفوف جاهزة للرسم — فيُختبر وحده بلا جهاز ولا مُصرّف واجهة.
 */
package nd.max.ui.util

/**
 * تصنيفات «الرئيسية». النوع مغلق بقصد: الأقسام قرار معلن، فلا يُضاف قسم في الواجهة وحدها
 * فيصير للشاشة تصنيف لا يعرفه النموذج ولا اختباره.
 */
enum class FileCategory {
    Images,
    Videos,
    Audio,
    Documents,
    Archives,
    Packages,
}

/**
 * حالة صفّ قسم. أربع حالات لا خامسة: ما لا يدخل فيها لا يُعرض برقم.
 */
enum class FileRowState {
    /** قرأنا ووجدنا شيئًا. */
    HasData,

    /** قرأنا ولم نجد شيئًا — الصفر الصادق الوحيد. */
    Empty,

    /** لم نقرأ: مهلة، أو فشل استعلام. */
    Unreadable,

    /** مُنعنا: لا إذن لهذا المصدر. */
    NoPermission,
}

/**
 * قراءة تصنيف واحد كما وصلت من المصدر — **بلا حكم**، فالحكم في [FileHomeModel.rowOf].
 *
 * و`count`/`sizeBytes` قابلان للغياب بقصد: الغياب معلومة، لا نقص يُكمَّل بصفر.
 */
data class CategoryReading(
    val category: FileCategory,
    val count: Long? = null,
    val sizeBytes: Long? = null,
    val permitted: Boolean = true,
)

/**
 * صفّ جاهز للرسم.
 *
 * و`count` يبقى `Long?` لأن الصفّ «غير المقروء» يجب أن **يصرّح** بالجهل؛ وتحويله إلى `0`
 * هنا كان سيجعل الغشّ سهلًا في الرسم بلا أثر في الاختبار.
 */
data class FileHomeRow(
    val category: FileCategory,
    val count: Long?,
    val sizeBytes: Long?,
    val state: FileRowState,
) {
    val hasData: Boolean get() = state == FileRowState.HasData

    /** هل الحجم معلوم؟ الرسم يعرض شرطة عند الجهل ولا يعرض صفرًا. */
    val sizeKnown: Boolean get() = sizeBytes != null

    /** هل العدّ معلوم؟ */
    val countKnown: Boolean get() = count != null
}

/**
 * مجاميع «الرئيسية».
 *
 * و`complete` تعني: **كل** صفّ عدّه معلوم — وقيمةٌ ناقصة تُقرأ كأنها كاملة هي الخطأ الذي
 * يمنعه هذا الحقل.
 *
 * و`sizeBytes` يبقى `null` إلا إذا كان **كل** صفّ حجمه معلوم: مجموع ناقص يُقرأ كأنه الحجم
 * كلّه، ولا شيء في الواجهة يشرح للمستخدم أن الرقم ناقص.
 */
data class FileHomeTotals(
    val count: Long,
    val sizeBytes: Long?,
    val complete: Boolean,
)

object FileHomeModel {

    /**
     * ترتيب الأقسام المعلن — قرار لا صدفة.
     *
     * والصور أوّلًا لأنها أكثر ما يُبحث عنه في مدير ملفات على الهاتف، والتطبيقات (APK) في
     * النهاية لأنها أقلّها استعمالًا يوميًّا. والترتيب **هنا** لا في الواجهة: قائمة تُرتَّب
     * في الرسم تُرتَّب مرّتين إذا رسمناها مرّتين.
     */
    val ORDER: List<FileCategory> = listOf(
        FileCategory.Images,
        FileCategory.Videos,
        FileCategory.Audio,
        FileCategory.Documents,
        FileCategory.Archives,
        FileCategory.Packages,
    )

    /** سقف «مؤخرًا» المعروض في الرئيسية: ما زاد صار سجلًّا لا استقبالًا. */
    const val RECENT_LIMIT: Int = 6

    /** سقف المفضّلة المعروضة: قائمة طويلة في الأعلى تدفن الأقسام. */
    const val BOOKMARK_LIMIT: Int = 8

    /**
     * حكم صفّ واحد.
     *
     * والأعداد السالبة تُعامل **كمجهولة** لا كصفر: عدد سالب لا وجود له، فهو أثر قراءة
     * خاطئة — وردّه إلى المجهول أصدق من عرضه.
     */
    fun rowOf(reading: CategoryReading): FileHomeRow {
        val count = reading.count?.takeIf { it >= 0L }
        val size = reading.sizeBytes?.takeIf { it >= 0L }

        return when {
            !reading.permitted -> FileHomeRow(reading.category, null, null, FileRowState.NoPermission)
            count == null -> FileHomeRow(reading.category, null, null, FileRowState.Unreadable)
            count == 0L -> FileHomeRow(reading.category, 0L, 0L, FileRowState.Empty)
            else -> FileHomeRow(reading.category, count, size, FileRowState.HasData)
        }
    }

    /**
     * صفوف الرئيسية بالترتيب المعلن، **وكل قسم معلن يظهر**.
     *
     * ولماذا يُكمَّل الناقص: قسم غاب من القراءة (فشل استعلامه وحده) كان سيختفي من الشاشة
     * فيظنّ المستخدم أن الجهاز لا صور فيه. الظهور بحالة «غير مقروء» أصدق من الغياب.
     *
     * وتكرار القراءة للقسم نفسه: **الأولى تفوز** — فالحكم ثابت لا يعتمد على ترتيب وصل
     * الاستعلامات.
     */
    fun rows(readings: List<CategoryReading>): List<FileHomeRow> {
        val byCategory = LinkedHashMap<FileCategory, CategoryReading>()
        for (reading in readings) byCategory.putIfAbsent(reading.category, reading)
        return ORDER.map { category ->
            byCategory[category]?.let(::rowOf) ?: rowOf(CategoryReading(category))
        }
    }

    /**
     * المجاميع — **بلا تكميل بالصفر**.
     *
     * والعدّ المجموع يجمع المعروف فقط، و`complete` تقول إن كان كلّه معروفًا. والحجم يظهر
     * فقط إذا كان كل صفّ حجمه معروفًا، وإلا فهو `null` («مجهول») — لأن مجموعًا ناقصًا
     * يُقرأ كأنه كامل.
     */
    fun totals(rows: List<FileHomeRow>): FileHomeTotals {
        val sizeComplete = rows.isNotEmpty() && rows.all { it.sizeKnown }
        return FileHomeTotals(
            count = rows.sumOf { it.count ?: 0L },
            sizeBytes = if (sizeComplete) rows.sumOf { it.sizeBytes ?: 0L } else null,
            complete = rows.isNotEmpty() && rows.all { it.countKnown },
        )
    }

    /**
     * «مؤخرًا» من السجل: بلا تكرار، والأحدث أوّلًا، بسقف.
     *
     * وتُعاد التصفية والتقريب هنا **وإن كان `FileHistory.push` يحفظ الترتيب أصلًا**: السجل
     * يُقرأ من ملفّ كتبته نسخة أقدم، فلا يُبنى العرض على ترتيب مُدَّعى في ملف. ومفتاح
     * التكرار هو `FileBrowser.normalize` نفسه المستعمل في الكتابة — فلا يرى النموذج مسارين
     * للمجلد نفسه.
     */
    fun recent(history: List<HistoryEntry>, limit: Int = RECENT_LIMIT): List<HistoryEntry> {
        if (limit <= 0) return emptyList()
        val newest = LinkedHashMap<String, HistoryEntry>()
        for (entry in history.sortedByDescending { it.atMs }) {
            val key = FileBrowser.normalize(entry.path)
            val seen = newest[key]
            if (seen == null || entry.atMs > seen.atMs) newest[key] = entry
        }
        return newest.values.take(limit)
    }

    /**
     * المفضّلة المعروضة — **بترتيب المستخدم كما هو**.
     *
     * ولا يُعاد ترتيبها أبجديًّا ولا بالاستعمال: ترتيبها فعلُ مستخدم (السحب في `FileBookmarks.move`)،
     * وترتيبها من جديد هنا كان سيهدم فعله في الشاشة التي تليه.
     */
    fun bookmarks(bookmarks: List<FileBookmark>, limit: Int = BOOKMARK_LIMIT): List<FileBookmark> =
        if (limit <= 0) emptyList() else bookmarks.take(limit)
}
