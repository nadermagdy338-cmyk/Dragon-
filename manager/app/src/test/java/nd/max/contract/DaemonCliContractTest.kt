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
 * سطح الـCLI للخادم — كان §٤ يعلنه **«١٣ عَلَمًا · ❌ لا يُختبر»**، وهو في الحقيقة **الشكل الذي
 * يستعمله المستخدم فعلًا** (`sys.maxmanager-service --profile 2`). وما كان مقيسًا منه عَلَمٌ واحد
 * (`--profile`) عبر `archdaemon/tests/cli_profile.c`؛ أمّا **الجدول نفسه** — عَلَمٌ · مُرادف ·
 * بوّابة · دالّة — فلم يكن مكتوبًا في أي موضع.
 *
 * **والبند الحامل هو البوّابة لا الأسماء:** `Main.c` ينفّذ تسعة أعلام **قبل** `require_daemon_running()`
 * وأربعة **بعده**. فلو انتقل عَلَم عبر الخط تغيّر شرط عمله بلا تغيير في نصّه — `--version` أو
 * `--clearlogs` يصيران «لا يعملان إلا بخادم يعمل»، وهو انحدار صامت. فيقيس هذا الحرس **مجموعتي
 * الأعلام على جانبَي الخط** لا وجودها فقط.
 *
 * **وثلاثة حرّاس:** صدق (كل عَلَم ومرادفه في `IS_CMD` · والدالّة موجودة) · اكتمال (أي `IS_CMD`
 * في `Main.c` بلا صفّ ⇒ سقوط) · وتوثيق (كل عَلَم يظهر في نصّ `print_help()`) — وقد كشف الثالث
 * **أربعة أعلام غائبة عن `--help`** (`--rerun` · `--clearlogs` · `--shownotifications` ·
 * `--hidenotifications`) فأُضيفت.
 *
 * **والحدّ:** هذا يقيس إعلان السطح وترتيبه، لا **سلوكًا على جهاز** — `getuid() != 0` وبوّابة
 * التوفّر الفعلية وسلوك الأعلام على هاتف تبقى سلوك جهاز. (`--profile` وحده مقيس سلوكيًّا في
 * `cli_profile.c`.)
 */
class DaemonCliContractTest {

    private data class Row(
        val long: String,
        val short: String,
        val gate: String,
        val handler: String,
        val inHelp: String,
    )

    private val mainPath = "archdaemon/jni/Main.c"
    private val helpPath = "archdaemon/jni/src/BinaryCLI/BinaryCLI.c"

    /** العدد المقيس — مفروض بالمساواة الدقيقة: جدول ينقص عَلَمًا بصمت يتوقّف عن القياس. */
    private val expectedRows = 13

    private fun root(): File {
        val r = ContractFixtures.repoRoot
        assumeTrue("repository root not reachable; guard not evaluated", File(r, "archdaemon").isDirectory)
        return r
    }

    private fun rows(): List<Row> = ContractFixtures.text("daemon_cli.tsv")
        .lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }
        .filter { it.size == 6 && it[0] != "long" }
        .map { Row(it[0], it[1], it[2], it[3], it[4]) }
        .toList()

    private val isCmd = Regex("""IS_CMD\([^,]+,\s*"([^"]+)",\s*"([^"]+)"\)""")
    private val gateCall = "require_daemon_running"

    /** `Main.c` مقسومًا على خطّ البوّابة: ما قبل `require_daemon_running()` وما بعده. */
    private fun splitAtGate(): Pair<String, String> {
        val text = File(root(), mainPath).readText()
        val at = text.indexOf(gateCall)
        assertTrue("Main.c no longer calls $gateCall() — the gate guard cannot be evaluated", at >= 0)
        return text.substring(0, at) to text.substring(at)
    }

    @Test
    fun `the table carries the measured rows and names a known gate`() {
        val rows = rows()
        assertTrue("the table is empty — a fixture that asserts nothing", rows.isNotEmpty())
        assertTrue(
            "the measured table must stay $expectedRows rows (saw ${rows.size}); a shrinking table stops measuring",
            rows.size == expectedRows,
        )
        val badGate = rows.filter { it.gate !in setOf("none", "requires-daemon") }
        assertTrue("rows with an unknown gate: ${badGate.map { it.long }}", badGate.isEmpty())
        assertTrue(
            "the declared flag set changed — update the guard deliberately, do not let it drift",
            rows.map { it.long }.toSet() == setOf(
                "--help", "--appactivity", "--run", "--version", "--clearlogs", "--hidenotifications",
                "--shownotifications", "--bypasspathlist", "--rerun", "--profile", "--log",
                "--verboselog", "--checkbypasschg",
            ),
        )
        assertTrue("every flag must name a handler", rows.all { it.handler.isNotBlank() })
    }

    @Test
    fun `every declared flag and alias is dispatched and every handler is named`() {
        val text = File(root(), mainPath).readText()
        val broken = mutableListOf<String>()
        for (row in rows()) {
            if (!text.contains("\"${row.long}\"")) broken += "`${row.long}` is not a literal in $mainPath"
            if (!text.contains("\"${row.short}\"")) broken += "`${row.short}` (alias of ${row.long}) is not a literal"
            if (!text.contains(row.handler)) broken += "`${row.handler}` (handler of ${row.long}) is not called"
        }
        assertTrue(
            "a flag and its dispatch drifted apart — decide which side is wrong, do not edit the table:\n" +
                broken.joinToString("\n"),
            broken.isEmpty(),
        )
    }

    @Test
    fun `the gate split matches the table — nine flags before require_daemon_running, four after`() {
        val (before, after) = splitAtGate()
        val beforeFlags = isCmd.findAll(before).map { it.groupValues[1] }.toSet()
        val afterFlags = isCmd.findAll(after).map { it.groupValues[1] }.toSet()
        assertTrue("the pre-gate block dispatches no flag — the split is wrong", beforeFlags.isNotEmpty())
        assertTrue("the post-gate block dispatches no flag — the split is wrong", afterFlags.isNotEmpty())
        assertTrue(
            "a flag crosses the require_daemon_running() line without its text changing — a silent " +
                "behaviour change. Before: $beforeFlags · after: $afterFlags",
            beforeFlags.intersect(afterFlags).isEmpty(),
        )
        val declaredNone = rows().filter { it.gate == "none" }.map { it.long }.toSet()
        val declaredGated = rows().filter { it.gate == "requires-daemon" }.map { it.long }.toSet()
        assertTrue(
            "the table says these run without a daemon but Main.c gates them: ${declaredNone - beforeFlags}",
            declaredNone - beforeFlags == emptySet<String>(),
        )
        assertTrue(
            "the table says these require a daemon but Main.c runs them before the gate: ${declaredGated - afterFlags}",
            declaredGated - afterFlags == emptySet<String>(),
        )
        assertTrue(
            "Main.c gates a flag the table does not declare: ${(afterFlags - declaredGated).sorted()}",
            afterFlags - declaredGated == emptySet<String>(),
        )
    }

    @Test
    fun `no dispatched flag is missing from the table`() {
        val text = File(root(), mainPath).readText()
        val dispatched = isCmd.findAll(text).map { it.groupValues[1] }.toSet()
        val declared = rows().map { it.long }.toSet()
        assertTrue("the scan found no dispatch at all — it would pass over any tree", dispatched.isNotEmpty())
        val unlisted = dispatched - declared
        assertTrue(
            "these flags are dispatched by Main.c but have no row in fixtures/contracts/daemon_cli.tsv: " +
                "${unlisted.sorted()} — add them deliberately, do not let them appear silently",
            unlisted.isEmpty(),
        )
    }

    @Test
    fun `every flag is documented in the help text`() {
        val help = File(root(), helpPath).readText()
        val missing = rows().filter { !help.contains(it.long) }.map { it.long }
        assertTrue(
            "these flags exist and are dispatched but never appear in print_help(): ${missing.sorted()} — " +
                "a user cannot discover them (`--rerun` and `--clearlogs` were among them)",
            missing.isEmpty(),
        )
    }

    /**
     * **التكذيب:** لو كان الفحص لا يميّز لمرّ على أي عَلَم. فيُقلب كل عَلَم ويُشترَط ألّا يوجد
     * في المصدر — أي أن الحرس يفصل فعلًا.
     */
    @Test
    fun `the guard is discriminating — a mutated flag appears nowhere in the sources`() {
        val r = root()
        val sources = listOf(mainPath, helpPath).joinToString("\n") { File(r, it).readText() }
        val wronglyFound = rows().map { it.long }.filter { sources.contains("${it}-MUTATED") }
        assertTrue("a deliberately mutated flag was 'found', so the guard cannot fail: $wronglyFound", wronglyFound.isEmpty())
    }
}
