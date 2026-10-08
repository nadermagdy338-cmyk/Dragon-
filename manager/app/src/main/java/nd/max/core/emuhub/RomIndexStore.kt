/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.emuhub

/**
 * فهرس المكتبة على القرص — **قراءة/كتابة مجموعة نصوص فقط**، والترميز/الفكّ دالتان نقيّتان.
 *
 * **لماذا لا ملفّ JSON ولا `filesDir`:** المفهرس يخزّن اسمًا وحجمًا لكل ملفّ، وهي حقول سطريّة
 * قصيرة. فمخزن `SharedPreferences` القائم (المفتاح `settings` نفسه الذي يستعمله التطبيق) يكفي
 * ويكلّف صفر بنية جديدة: لا `AtomicFile`، ولا صيغة إصدار، ولا كاتب ثانٍ. وهذا هو «حقل واحد ⇒ كاتب
 * واحد»: `emulator_documents` للملفّات المفردة، و`emulator_folders` للمجلّدات، و`emulator_index`
 * للفهرس — ثلاثة حقول، كلٌّ بكاتب واحد.
 *
 * **ووظيفته الوحيدة:** رسم الرفّ فورًا من آخر فهرس عند فتح الشاشة، ثم يُستبدل بنتيجة مسح طازج.
 * **وليس تجاوزًا للمسح:** الأختام الزمنيّة لشجرة SAF كثيرًا ما تُرجع `0` من المزوّد، فبناء تجاوز
 * عليها كان سيُجمّد الرفّ على فهرس قديم إلى الأبد. الفهرس هنا **ذاكرة عرض لا حكم**.
 *
 * **وحدّ واحد مُعلَن:** عدد الملفّات (`MAX_FILES`)؛ وتجاوزه **يُعلَن** نقصًا لا يُطوى، لأن مكتبة
 * ناقصة تُقرأ كأنها كلّ المكتبة.
 *
 * **ولا سقف على عدد المجلّدات:** كان فيه `MAX_FOLDERS = 32`، وحُذف لأنه صنع طريقًا مسدودًا —
 * بلوغ السقف يمنع الإضافة، ولا واجهة لإزالة مجلد، فلا مخرج. والسقف الحقيقي قائم في النظام نفسه
 * (`takePersistableUriPermission` يرفض بعد حدّه)، و[RomLibraryAccess.keep] يلتقط ذلك **ويُعلنه**
 * بالفعل. فسقفنا كان تكرارًا يخفي سببًا موجودًا ويضيف سببًا لا علاج له.
 */
object RomIndexStore {
    const val MAX_FILES = 5000

    /** عمق المشي الأقصى داخل المجلد — يمنع شجرة عميقة بلا نهاية من تعليق الفهرسة. */
    const val MAX_DEPTH = 8

    private const val FIELD = '\u0001'

    /**
     * سطر واحد لكل ملفّ: `uri ␁ parent ␁ name ␁ size`.
     *
     * الحقول المفصولة بحرف تحكّم لا يظهر في مسار ولا في اسم: اسم ملفّ فيه `|` شائع، وفيه `\u0001`
     * لا يكون. والحقل الغائب يُكتب فارغًا ويُقرأ `null` — فلا صفر كاذب لحجم لم يُقرأ.
     */
    fun encode(file: RomFile): String = listOf(
        file.uri,
        file.parent,
        file.name,
        file.sizeBytes?.toString().orEmpty(),
    ).joinToString(FIELD.toString())

    /** `null` لسطر معطوب أو لمفتاح مفقود — السطر التالف يُسقَط ولا يُفسد بقية الفهرس. */
    fun decode(line: String): RomFile? {
        val parts = line.split(FIELD)
        if (parts.size != 4) return null
        val uri = parts[0]
        val name = parts[2]
        if (uri.isEmpty() || name.isEmpty()) return null
        return RomFile(
            uri = uri,
            name = name,
            parent = parts[1],
            sizeBytes = parts[3].toLongOrNull()?.takeIf { it >= 0 },
        )
    }

    /** فكّ مجموعة كاملة، مع سقف يُطبَّق بعد الترتيب ليكون الحكم حتميًّا لا تابعًا لترتيب المجموعة. */
    fun decodeAll(lines: Set<String>): List<RomFile> =
        lines.mapNotNull(::decode).distinctBy { it.uri }.sortedBy { it.uri }.take(MAX_FILES)
}
