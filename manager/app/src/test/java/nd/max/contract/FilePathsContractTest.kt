/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.contract

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * مسارات الملفات تحت مجلّد الوحدة — السطح الذي كان §٤ يعلنه **«مساراتًا · ❌ لا يُختبر»**،
 * وكان فيه عطب مقيس: مسار السجل الواحد `…/debug/MaxManager.log` مُعلَن في **أربعة مواضع
 * بثلاث لغات** (`binutils/src/utils/logger.rs` · `archdaemon/jni/include/MaxManager.h` ·
 * `MaxManagerPaths.kt` · ونصّ `rm -f` في `CLIUtility.c`) **ولا حرس يقارنها** — فانحراف أحدها
 * يعني سجلًّا يكتبه طرف ولا تراه الواجهة، أو تنظيفًا يمسح ملفًّا آخر.
 *
 * **ولماذا يعمل الحرس بلغة واحدة على ثلاث لغات:** `file_paths.tsv` يسجّل **اللاحقة نسبةً إلى
 * جذر الوحدة**، وهي نصّ موجود حرفيًّا في الأشكال الثلاثة (`"/data/adb/…/app_status"` في C،
 * و`"$MODULE_CONFIG/app_status"` في Kotlin، والمسار المطلق في Rust) — فلا يحتاج محلّلًا لكل لغة
 * ولا نسخة من المنطق لكل طرف.
 *
 * **وحرسان لا واحد:**
 *   ١. **الصدق:** كل ملف مُعلَن في الجدول يحوي لاحقته — فتباعُد لغة عن لغة يُسقط هذا الاختبار.
 *   ٢. **الاكتمال:** أي لاحقة تظهر في مجلّدات المصادر الممسوحة وليست لها صفّ ⇒ سقوط. فلا يُضاف
 *      مسار بصمت، وهو العطب الذي لا يكشفه اختبار على الصيغة وحدها.
 *
 * **والحدّ المُعلن (يُقال ولا يُطوى):** المسح يقرأ الشكل **المطلق** وشكل `$MODULE_CONFIG`؛
 * والمسار المركَّب في C من ماكرو غير مطلق (`MODULE_DIR "/x"`) لا يكشفه المسح، بل يحرسه الحرس
 * الأول عبر ذكره ملفًا مُعلَنًا. فالإضافة الصامتة في الشكل المركَّب تكشفها المراجعة لا الحرس —
 * وهذا مكتوب في رأس الجدول أيضًا.
 */
class FilePathsContractTest {

    private val base = "/data/adb/.config/MaxManager"

    private data class Row(val path: String, val kind: String, val declarations: List<String>)

    /**
     * نطاق المسح **مكتوب في الحرس** (لا مُفترَض ولا مقروء من الجدول) — وهو النطاق نفسه الذي
     * قِيس به الجدول. و`manager/src/main/rust/src` مُدرَج رغم أنه **لا يحمل مسارًا اليوم**
     * (مقيس: صفر): إدراجه هو ما يجعل أول مسار يُضاف فيه يسقط الحرس بدل أن يمرّ.
     */
    private val scanDirs = listOf(
        "archdaemon/jni/src",
        "archdaemon/jni/include",
        "preloadbin",
        "binprofiles/src",
        "binutils/src",
        "thermalcore/src",
        "manager/app/src/main/java",
        "manager/src/main/rust/src",
        "mainfiles",
    )
    private val scanExtensions = setOf("c", "h", "rs", "kt", "sh")

    /** العدد المقيس — مفروض بالمساواة الدقيقة: جدول ينقص صفًّا بصمت يتوقّف عن القياس. */
    private val expectedRows = 35

    private fun root(): File {
        val r = ContractFixtures.repoRoot
        assumeTrue("repository root not reachable; guard not evaluated", File(r, "archdaemon").isDirectory)
        return r
    }

    private fun rows(): List<Row> = ContractFixtures.text("file_paths.tsv")
        .lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }
        .filter { it.size == 3 && it[0] != "path" }
        .map { Row(it[0], it[1], it[2].split(';').filter(String::isNotBlank)) }
        .toList()

    private val absolute = Regex("""/data/adb/\.config/MaxManager(?:/([A-Za-z0-9_./%-]+))?""")
    // `${'$'}` لأن السلاسل الخام في Kotlin لا تُهرَّب فيها `$`: فبغيرها يُقرأ `$MODULE_CONFIG`
    // نموذج نصّي (string template) لا حرفًا في تعبير نمطي — وهو أوّل خطأ أُمسك هنا بالمُصرّف.
    private val composed = Regex("""${'$'}MODULE_CONFIG(?:/([A-Za-z0-9_./%-]+))?""")

    private fun scannedFiles(): List<File> {
        val r = root()
        return scanDirs.flatMap { dir ->
            File(r, dir).walkTopDown()
                .filter { it.isFile && it.extension in scanExtensions }
                .toList()
        }
    }

    /**
     * `<base>` في الجدول **عنصر نائب** للجذر نفسه، وليس نصًّا يُبحث عنه — فالنصّ المقيس هو
     * الجذر المُعلَن. وبدونه كان الصفّ يُقارن بنصّ حرفي `<base>` فتُسقط تسعة ملفات سليمة
     * (وهو أوّل ما أمسكه تشغيلُ هذا الحرس، فلا يُنسى: العنصر النائب يُترجَم قبل المقارنة).
     */
    private fun needle(path: String): String = if (path == "<base>") base else path

    /** اللاحقات الموجودة فعلًا، مع الملفات التي تحملها — بهذا نكشف المسار غير المُدرَج. */
    private fun foundPaths(): Map<String, MutableSet<String>> {
        val r = root()
        val out = linkedMapOf<String, MutableSet<String>>()
        for (file in scannedFiles()) {
            val rel = file.relativeTo(r).path.replace(File.separatorChar, '/')
            val text = file.readText()
            for (re in listOf(absolute, composed)) {
                for (m in re.findAll(text)) {
                    val suffix = (m.groupValues[1].ifEmpty { "<base>" }).trimEnd('/', '.')
                    out.getOrPut(suffix) { linkedSetOf() }.add(rel)
                }
            }
        }
        return out
    }

    @Test
    fun `the table carries the measured rows and every row has a declaration`() {
        val rows = rows()
        assertTrue("the table is empty — a fixture that asserts nothing", rows.isNotEmpty())
        assertTrue(
            "the measured table must stay $expectedRows rows (saw ${rows.size}); a shrinking table stops measuring",
            rows.size == expectedRows,
        )
        val bare = rows.filter { it.declarations.isEmpty() }
        assertTrue("rows with no declaration cannot be checked: ${bare.map { it.path }}", bare.isEmpty())
    }

    @Test
    fun `every declared file really contains its path suffix`() {
        val r = root()
        val broken = mutableListOf<String>()
        for (row in rows()) {
            for (decl in row.declarations) {
                val f = File(r, decl)
                if (!f.isFile) {
                    broken += "`${row.path}`: declared file `$decl` does not exist"
                } else if (!f.readText().contains(needle(row.path))) {
                    broken += "`${row.path}`: `$decl` no longer contains it"
                }
            }
        }
        assertTrue(
            "a path and one of its declared files drifted apart — decide which side is wrong, do not edit the table:\n" +
                broken.joinToString("\n"),
            broken.isEmpty(),
        )
    }

    @Test
    fun `no module path exists in the scanned sources that the table does not list`() {
        val declared = rows().map { it.path }.toSet()
        val found = foundPaths()
        assertTrue("the scan found no module path at all — the guard would pass over any tree", found.isNotEmpty())
        val unlisted = found.keys - declared
        assertTrue(
            "these module paths exist in the tree but have no row in fixtures/contracts/file_paths.tsv: " +
                unlisted.sorted() + " — add them deliberately, do not let them appear silently",
            unlisted.isEmpty(),
        )
    }

    @Test
    fun `the scan scope really covers every language and is not a token gesture`() {
        val files = scannedFiles()
        val exts = files.map { it.extension }.toSet()
        assertTrue(
            "the scan must reach C, its headers, Rust, Kotlin and shell; it saw $exts",
            exts.containsAll(setOf("c", "h", "rs", "kt", "sh")),
        )
        assertTrue("the scan looked at only ${files.size} files — that cannot be the tree", files.size > 300)
    }

    @Test
    fun `the three languages declare the same module root`() {
        val r = root()
        // الجذر نفسه، من كل لغة بطريقتها: Kotlin ثابتًا، وC داخل ترويسة، وRust نصًّا في سجلّه
        for (decl in listOf(
            "manager/app/src/main/java/nd/max/MaxManagerPaths.kt",
            "archdaemon/jni/include/MaxManager.h",
            "binutils/src/utils/logger.rs",
        )) {
            val f = File(r, decl)
            assertTrue("`$decl` is missing; the base-path guard cannot be evaluated", f.isFile)
            assertTrue("`$decl` must contain the module root `$base`", f.readText().contains(base))
        }
        assertTrue(
            "Kotlin must build its paths from MODULE_CONFIG = \"$base\"",
            File(r, "manager/app/src/main/java/nd/max/MaxManagerPaths.kt").readText()
                .contains("MODULE_CONFIG = \"$base\""),
        )
    }

    /** **التكذيب:** لو كان الفحص لا يميّز لمرّ على أي مسار. فيُقلب كل صفّ ويُشترَط أن يُرفَض. */
    @Test
    fun `mutating a row's path makes the check reject it`() {
        val r = root()
        val notRejected = mutableListOf<String>()
        for (row in rows()) {
            val mutated = "${needle(row.path)}MUTATED"
            // الفحص هو `text.contains(path)`؛ فالمسار المُقلَب يجب ألّا يوجد في أي ملف مُعلَن
            for (decl in row.declarations) {
                val f = File(r, decl)
                if (!f.isFile) continue
                if (f.readText().contains(mutated)) notRejected += "${row.path} -> $decl"
            }
        }
        assertTrue(
            "a deliberately mutated path was 'found' in the sources, so the check cannot fail: $notRejected",
            notRejected.isEmpty(),
        )
    }
}
