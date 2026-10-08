/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.emuhub

/**
 * فهرسة مكتبة ROM — منطق نقيّ يُقاس بلا جهاز ولا صلاحية.
 *
 * **قاعدتان تحكمان كل ما هنا:**
 * ١. **لا يُخفي ملفًّا.** التجميع يقرّب ما ينتمي إلى لعبة واحدة، ولا يُسقط شيئًا وُجد على القرص:
 *    ملفّ `.bin` بلا `.cue` رفيق يبقى عنصرًا مستقلًّا يُرى.
 * ٢. **المجهول يُعلَن مجهولًا.** نظام غير معروف ⇒ `null`، وحجم غير مقروء ⇒ `null`؛ والواجهة
 *    تحوّل `null` إلى «—» لا إلى صفر (ADR-07).
 */

/** ملفّ واحد كما قرأه المفهرس: لا محتوى ولا بايتات — اسم ووصف وحجم وختم فقط. */
data class RomFile(
    val uri: String,
    val name: String,
    val parent: String,
    val sizeBytes: Long?,
    val lastModified: Long?,
)

/**
 * عنصر مكتبة: لعبة واحدة، بجزء واحد أو بعدّة أجزاء (مجموعة أقراص).
 *
 * [parts] **لا تكون فارغة أبدًا** — عنصر بلا ملفّ ليس عنصرًا. و`discSet` صحيح حين تكون
 * الأجزاء أكثر من واحد، وهو ما يجعل الواجهة تعرض «مجموعة أقراص (٢ ملفّات)» بنصّها.
 */
data class RomEntry(
    val name: String,
    val uri: String,
    val parts: List<RomFile>,
    val system: RomSystem?,
    val ambiguous: Boolean,
) {
    val discSet: Boolean get() = parts.size > 1

    /** مجموع الأحجام المعروفة، أو `null` إن لم يُقرأ حجم أيّ جزء (لا صفر كاذب). */
    val sizeBytes: Long? get() = parts.mapNotNull { it.sizeBytes }.takeIf { it.isNotEmpty() }?.sum()

    val lastModified: Long? get() = parts.mapNotNull { it.lastModified }.maxOrNull()
}

/**
 * يجمّع الأجزاء في عناصر. التجميع بمفتاح **(المجلد + الاسم بلا امتداد)**: ملفّان بنفس الاسم في
 * مجلدين مختلفين لعبتان لا واحدة.
 *
 * والمجموعة لا تُقبل إلا إذا كان فيها **جزء صيغته مجموعة أقراص** (`.cue` · `.bin` · `.iso` …):
 * `Game.gba` و`Game.sav` لا يصيران عنصرًا واحدًا لأن أحدهما ليس قرصًا أصلًا.
 */
fun groupDiscSets(files: List<RomFile>): List<RomEntry> =
    files.groupBy { it.parent to RomSystems.baseName(it.name) }.flatMap { (key, group) ->
        val base = key.second
        val cue = group.firstOrNull { RomSystems.extensionOf(it.name) == "cue" }
        // مجموعة أقراص = أكثر من جزء، و**أكثر من جزء واحد منها قرص**. وهذا الشرط الثاني هو الذي
        // يمنع ابتلاع `Game.gba` مع `Game.sav`: يتشاركان الاسم ولا أحدهما قرص، فهما لعبتان.
        if (group.size > 1 && group.count { RomSystems.isAmbiguous(it.name) } > 1) {
            val parts = group.sortedBy { it.uri != cue?.uri }
            listOf(RomEntry(base, parts.first().uri, parts, null, ambiguous = true))
        } else {
            group.map { file ->
                RomEntry(
                    name = file.name,
                    uri = file.uri,
                    parts = listOf(file),
                    system = RomSystems.systemFor(file.name),
                    ambiguous = RomSystems.isAmbiguous(file.name),
                )
            }
        }
    }.sortedWith(compareBy<RomEntry> { it.system == null }.thenBy { it.name.lowercase() })

/**
 * صفوف الرفّ بعد الترشيح: [system] = `null` يعني «الكل» (شريحة «الكل»)، و[query] بحث نصّي
 * بالحروف الصغيرة. البحث بسيط عمدًا: الطيّ العربي (ة≈ه · ى≈ي) طبقة واجهة قائمة (`MaxSearch`)
 * ولا تُنسخ هنا ثانية.
 */
fun shelfRows(entries: List<RomEntry>, system: RomSystem?, query: String): List<RomEntry> {
    val needle = query.trim().lowercase()
    return entries.filter { entry ->
        val systemOk = system == null || entry.system == system ||
            (entry.system == null && entry.parts.any { RomSystems.systemFor(it.name) == system })
        val queryOk = needle.isEmpty() || entry.name.lowercase().contains(needle)
        systemOk && queryOk
    }
}

/** عدد العناصر لكل نظام — تبني شرائح الأنظمة من **ما وُجد فعلًا** لا من جدول ثابت. */
fun systemCounts(entries: List<RomEntry>): Map<RomSystem, Int> =
    entries.mapNotNull { it.system }.groupingBy { it }.eachCount()

/**
 * هل يُعاد المسح؟ ختم زمنيّ محفوظ مقابل ختم المجلد الآن.
 *
 * **الغائب يُعيد المسح لا يُوقف الفهرسة:** `null` في أيّ من الطرفين يعني «لا نعرف» — وقراءة
 * مجهولة لا تُبنى عليها قائمة قديمة قد تكون ناقصة. هذا هو تجاوز المسح المنصوص عليه في معيار
 * قبول EH-01، وحدّه مُعلَن: التغيير **داخل** المجلد لا يحرّك ختم المجلد على كل مزوّد، فيبقى
 * زرّ التحديث اليدوي هو الطريق المضمون.
 */
fun shouldRescan(storedStamp: Long?, currentStamp: Long?): Boolean =
    storedStamp == null || currentStamp == null || currentStamp != storedStamp
