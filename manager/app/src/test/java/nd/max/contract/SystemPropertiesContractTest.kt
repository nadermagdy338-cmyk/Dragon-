/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * سطح خصائص النظام — **العقد الذي كشف عطبًا حقيقيًّا عند كتابته**.
 *
 * لماذا هذا أهمّ من أنه «جدول + تحقّق»
 * -----------------------------------
 * المفاتيح الأربعة `persist.sys.maxmanager.gpu_studio.*` كانت مُعلَنة في التطبيق، وتقع تحت نطاق فحص
 * الخادم `persist.sys.maxmanager`، **وليست في قائمته الدقيقة ولا تحت أي بادئة فيه** ⇒ فكان الخادم
 * يوسمها `STALE_PROP` **ويحذفها عند كل إقلاع**، أي أن «حفظ GPU Studio» في التطبيق كان يُمحى بلا سطر
 * عطل ظاهر. وهذا بعينه صنف العطب الموثَّق في `PropValidator.c` نفسه (مفتاح `detailedlog` سابقًا).
 * أُصلح بإضافة البادئة الرابعة، والقياس مكتوب بجانب البادئة في المصدر.
 *
 * وما يقيسه هذا الحرس الآن — لا الوثيقة:
 *
 * 1. **الاكتمال في الاتجاهين**: مفاتيح `owner=kotlin` في الجدول ≡ ما يُعلنه `MaxManagerProps` بالضبط.
 *    فمفتاح جديد بلا صف يسقط، وصف لمفتاح أُزيل يسقط.
 * 2. **كل `exact` صادق**: النصّ الحرفي موجود فعلًا في `VALID_MAXMANAGER_PROPS[]`.
 * 3. **كل `prefix:P` صادق**: `P` مُعلَنة فعلًا في `VALID_PROP_PREFIXES[]` والمفتاح يبدأ بها.
 * 4. **لا مفتاح بلا غطاء**: لا صفّ بـ`DELETE_RISK`، ولا صفّ يخالف الغطاء الذي يدّعيه.
 * 5. **المرايا**: كل `rust=exact` موجود في `props.rs`، وكل مفتاح في `props.rs` له صف (لا مفتاح شبح).
 */
class SystemPropertiesContractTest {

    private data class Row(
        val key: String,
        val owner: String,
        val daemonScope: String,
        val rust: String,
        val script: String,
    )

    private val table: List<Row> by lazy {
        ContractFixtures.text("system_properties.tsv").lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
            .filter { it.size == 5 && it[0] != "key" }
            .map { Row(it[0], it[1], it[2], it[3], it[4]) }
            .toList()
    }

    private fun read(relative: String): String? {
        val f = File(ContractFixtures.repoRoot, relative)
        return if (f.isFile) f.readText() else null
    }

    /**
     * يطرح التعليقات قبل القراءة — لأن نصًّا **مُعلَّقًا** ليس إعلانًا.
     *
     * وهذا ليس ترفًا: أول تكذيب لهذا الحرس حذف البادئة **بتعليقها** فمرّ — لأن استخراج النصوص
     * كان يقرأ النصّ داخل التعليق على أنه مُعلَن. ثم كشف التكذيب الثاني عطبًا في هذا الطرح نفسه:
     * طَرْح الكتل أولًا يقرأ فتحة كتلة (شرطة مائلة + نجمة) واقعة **داخل** تعليق سطري في مسار
     * `net/ipv4` كفتح كتلة حقيقية، فيبتلع نصف الملف. فالترتيب الآن: تعليق سطري ثم كتلة.
     *
     * والدرس المزدوج: حرس يقرأ ملفًا يجب أن يقرأ **ما يُنفَّذ منه**، وأداة الحرس نفسها تُكذَّب مرّة.
     *
     * وحدّها المُعلن: نصّ في مقتطف يحوي `//` يُقصّ — لا وجود له الآن في الملفات المقروءة، ولو دخل
     * لنبّه كشفُ الاكتمال نفسه بدل أن يمرّ.
     */
    private fun stripComments(source: String): String = source
        .lines()
        .joinToString("\n") { line -> line.substringBefore("//") }
        .replace(Regex("(?s)/\\*.*?\\*/"), " ")

    /** النصوص الحرفية داخل `arrayName[] = { ... };` — القائمة الدقيقة أو قائمة البادئات. */
    private fun stringArray(source: String, arrayName: String): List<String> {
        val body = stripComments(source).substringAfter("$arrayName[] = {", "").substringBefore("};")
        return Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
    }

    /** القيم كما تُعلَن بالترتيب — قائمة لا مجموعة، حتى يُكشف التكرار لا أن يُطوى. */
    private fun kotlinDeclaredLiterals(): List<String> {
        val source = read("manager/app/src/main/java/nd/max/MaxManagerProps.kt")
        assumeTrue("MaxManagerProps.kt not reachable; guard not evaluated", source != null)
        return Regex("const val \\w+ = \"([^\"]+)\"")
            .findAll(stripComments(source!!)).map { it.groupValues[1] }.toList()
    }

    private fun daemonSource(): String? = read("archdaemon/jni/src/StartupInit/PropValidator.c")

    @Test
    fun `the table and the Kotlin declaration are the same set, in both directions`() {
        val literals = kotlinDeclaredLiterals()
        val declared = literals.toSet()
        val inTable = table.filter { it.owner == "kotlin" }.map { it.key }.toSet()

        val duplicated = literals.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        assertTrue(
            "a key string is declared twice in MaxManagerProps.kt: $duplicated — one of the two is dead weight",
            duplicated.isEmpty(),
        )

        val missingRows = (declared - inTable).sorted()
        val orphanRows = (inTable - declared).sorted()
        assertTrue("properties declared in MaxManagerProps.kt but absent from the table: $missingRows", missingRows.isEmpty())
        assertTrue("table rows with owner=kotlin that MaxManagerProps.kt no longer declares: $orphanRows", orphanRows.isEmpty())
    }

    @Test
    fun `every coverage claim is verified against the daemon's own whitelist`() {
        val source = daemonSource()
        assumeTrue("PropValidator.c not reachable; guard not evaluated", source != null)
        val src = source!!
        val exact = stringArray(src, "VALID_MAXMANAGER_PROPS").toSet()
        val prefixes = stringArray(src, "VALID_PROP_PREFIXES")

        val violations = mutableListOf<String>()
        for (row in table) {
            when {
                row.daemonScope == "exact" -> if (row.key !in exact) {
                    violations += "${row.key}: claimed exact but is not in VALID_MAXMANAGER_PROPS"
                }
                row.daemonScope.startsWith("prefix:") -> {
                    val prefix = row.daemonScope.removePrefix("prefix:")
                    if (prefix !in prefixes) violations += "${row.key}: prefix $prefix is not declared in VALID_PROP_PREFIXES"
                    if (!row.key.startsWith(prefix)) violations += "${row.key}: does not start with its claimed prefix $prefix"
                }
                row.daemonScope == "nonpersist" -> if (row.key.startsWith("persist.sys.maxmanager")) {
                    violations += "${row.key}: claimed nonpersist but the daemon does scan that namespace"
                }
                else -> violations += "${row.key}: unknown daemon_scope '${row.daemonScope}'"
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `no declared property may sit in the daemon's scan scope without a cover`() {
        val source = daemonSource()
        assumeTrue("PropValidator.c not reachable; guard not evaluated", source != null)
        val src = source!!
        val exact = stringArray(src, "VALID_MAXMANAGER_PROPS").toSet()
        val prefixes = stringArray(src, "VALID_PROP_PREFIXES")

        // إعادة اشتقاق الغطاء من المصدر، بلا قراءة عمود `daemon_scope` — فالإعلان لا يُصدَّق نفسه.
        val uncovered = table.filter { row ->
            row.key.startsWith("persist.sys.maxmanager") &&
                row.key !in exact &&
                prefixes.none { row.key.startsWith(it) }
        }.map { it.key }
        assertTrue(
            "these properties are inside the daemon's scan namespace but covered by nothing, so they " +
                "are flagged STALE_PROP and deleted on every daemon start (the gpu_studio regression): $uncovered",
            uncovered.isEmpty(),
        )
    }

    @Test
    fun `the Rust mirror agrees with the table, and has no ghost keys`() {
        val source = read("binprofiles/src/props.rs")
        assumeTrue("props.rs not reachable; guard not evaluated", source != null)
        val src = source!!
        val literals = Regex("=\\s*\"((?:persist\\.sys\\.maxmanager|sys\\.maxmanager)[^\"]*)\"")
            .findAll(src).map { it.groupValues[1] }.toSet()

        val claimed = table.filter { it.rust == "exact" }.map { it.key }.toSet()
        val lying = (claimed - literals).sorted()
        val ghost = (literals - table.map { it.key }.toSet()).sorted()
        assertTrue("rows claim rust=exact but props.rs does not contain them: $lying", lying.isEmpty())
        assertTrue("props.rs declares keys with no row in the contract table: $ghost", ghost.isEmpty())
    }

    @Test
    fun `the shell mirror's exact claims are true`() {
        val source = read("mainfiles/props.sh")
        assumeTrue("props.sh not reachable; guard not evaluated", source != null)
        val src = source!!
        // مفتاح مُقتبس أو عارٍ — كلاهما استخدام شرعي في الصدفة؛ المدّعى هو **وجود النصّ** لا شكله.
        val lying = table.filter { it.script == "exact" && !src.contains(it.key) }.map { it.key }
        assertTrue("rows claim script=exact but props.sh does not mention them: $lying", lying.isEmpty())
        // ولا يُقاس الاتجاه العكسي هنا: props.sh يستخدم أيضًا بادئتين عاريتين
        // (`persist.sys.maxmanager.` و`persist.sys.maxmanagerconf.`) ليستا مفاتيح، فادّعاء «لا مفتاح شبح»
        // فيه يكون كاذبًا بحكم الشكل لا بحكم العطب.
    }

    @Test
    fun `the table has no duplicate keys and every column is filled with a known value`() {
        val keys = table.map { it.key }
        assertEquals("duplicate rows in the contract table", keys.size, keys.toSet().size)

        val knownOwners = setOf("kotlin", "daemon")
        val badOwners = table.map { it.owner }.filterNot { it in knownOwners }.distinct()
        assertTrue("unknown owner values: $badOwners", badOwners.isEmpty())

        val knownFlags = setOf("exact", "-")
        val badFlags = (table.map { it.rust } + table.map { it.script }).filterNot { it in knownFlags }.distinct()
        assertTrue("unknown rust/script values: $badFlags", badFlags.isEmpty())

        // العدد مُثبَّت كما في قائمة التجاوز: تغييره الحقيقي مقصود، وتراجعه صامتًا عطب.
        assertEquals("the declared surface changed size; re-measure, do not re-baseline", 93, table.size)
    }
}
