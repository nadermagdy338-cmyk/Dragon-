package nd.max.core.jni

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * حزمة تحليل السجلّات — تُقاس هنا **وفي Rust** على نفس المُدخلات.
 *
 * وما يُحرَس بالخصوص: **الترتيب والمحاذاة** (سطر نتيجة لكل سطر مُدخل)، و**الفصل** بين «تُسقَط»
 * و«احتياط» و«محلَّلة» — فخلط اثنين منها يُظهر سطورًا لم تُطابق أو يُخفي سطورًا مطابقة، وكلاهما
 * عطب يظهر للمستخدم لا للبناء. والرموز نفسها التي ينتجها `logparse` في Rust.
 */
class LogPacketTest {

    @Test
    fun `skip fallback and parsed rows decode to their kinds`() {
        val rows = ProbePacket.unpackLogRows("S\nF\nP\u0001a\u0001b", expected = 3)!!
        assertEquals('S', rows[0].kind)
        assertEquals('F', rows[1].kind)
        assertEquals('P', rows[2].kind)
        assertEquals(listOf("a", "b"), rows[2].fields)
    }

    @Test
    fun `a logcat row keeps its seven fields in the agreed order`() {
        val row = "P\u000109-24\u000118:12:03.456\u00011234\u00015678\u0001I\u0001MaxManager\u0001EVENT=BOOST sw=1"
        val fields = ProbePacket.unpackLogRows(row, expected = 1)!!.first().fields
        assertEquals(
            listOf("09-24", "18:12:03.456", "1234", "5678", "I", "MaxManager", "EVENT=BOOST sw=1"),
            fields,
        )
    }

    @Test
    fun `shifted separators inside a message are restored`() {
        val row = "P\u0001t\u0001l\u0001tag\u0001has\u0002sep and\u0003escape"
        val fields = ProbePacket.unpackLogRows(row, expected = 1)!!.first().fields
        assertEquals("has\u0001sep and\u0002escape", fields.last())
    }

    @Test
    fun `an empty message stays an empty field instead of vanishing`() {
        val row = "P\u0001t\u0001l\u0001tag\u0001"
        assertEquals(listOf("t", "l", "tag", ""), ProbePacket.unpackLogRows(row, expected = 1)!!.first().fields)
    }

    @Test
    fun `a row count that disagrees with the batch is refused whole`() {
        assertNull(ProbePacket.unpackLogRows("S\nF", expected = 3))
    }

    @Test
    fun `raw lines are shipped untouched so trimming cannot change a verdict`() {
        // محلّل Kotlin يقرأ السطر خامًا: سطر بمسافة بادئة لا يُطابق نمط `^` ⇒ يُسقَط/احتياط.
        val lines = listOf("  indented", "plain", "")
        assertEquals("  indented\nplain\n", ProbePacket.packRawLines(lines))
    }
}

/**
 * حارس ربط JNI — يمنع صنف العطب الذي لا يظهر إلا على الجهاز: اختلاف **اسم** بين
 * `private external fun` في Kotlin ورمز `Java_...` في Rust ⇒ `UnsatisfiedLinkError` عند
 * أول نداء، وبناء أخضر واختبارات خضراء حتى تلك اللحظة.
 *
 * والفحص مصدريّ (كما في بقية حوارس المستودع): كل دالة أصلية معلَنة في الجسر يجب أن يقابلها
 * تصدير بالاسم نفسه، ولا يُترك تصدير بلا مستدعٍ.
 */
class RustBridgeSymbolTest {

    private lateinit var repoRoot: File

    @Before
    fun locateRepoRoot() {
        val found = listOf(
            File("."),
            File(".."),
            File("../.."),
        ).firstOrNull { File(it, "manager/src/main/rust/src/lib.rs").isFile }
        assumeTrue("Cannot locate the rust crate; guard not evaluated", found != null)
        repoRoot = found!!
    }

    private fun bridgeText(): String = File(
        repoRoot, "manager/app/src/main/java/nd/max/core/jni/ProbeBridge.kt"
    ).readText()

    private fun rustText(): String = File(repoRoot, "manager/src/main/rust/src/lib.rs").readText()

    @Test
    fun `every declared native method has a rust export with the same name`() {
        val declared = Regex("""private external fun (\w+)\(""").findAll(bridgeText())
            .map { it.groupValues[1] }.toList()
        assertTrue("The bridge must declare native methods", declared.isNotEmpty())

        val exported = Regex("""Java_nd_max_core_jni_ProbeBridge_(\w+)""").findAll(rustText())
            .map { it.groupValues[1] }.toSet()

        val missing = declared.filterNot { it in exported }
        assertTrue("Native methods without a Rust export (UnsatisfiedLinkError on device): $missing", missing.isEmpty())
    }

    @Test
    fun `no rust export is left without a caller in the bridge`() {
        val declared = Regex("""private external fun (\w+)\(""").findAll(bridgeText())
            .map { it.groupValues[1] }.toSet()
        val exported = Regex("""Java_nd_max_core_jni_ProbeBridge_(\w+)""").findAll(rustText())
            .map { it.groupValues[1] }.toSet()
        val orphans = exported - declared
        assertTrue("Rust exports with no caller (dead native surface): $orphans", orphans.isEmpty())
    }

    @Test
    fun `the rust log parser documents the very patterns the kotlin reference uses`() {
        // النمطان في Kotlin هما المرجع الدلالي، وقاعدة Rust تقول إنها محاكاة حرفية لهما.
        // فإن تغيّر أحد الجانبين بلا الآخر سقط هذا الحارس قبل أن يظهر فرق على الشاشة.
        val kotlinSource = File(
            repoRoot, "manager/app/src/main/java/nd/max/ui/viewmodel/LogsLineParser.kt"
        ).readText()
        val patterns = Regex("""Regex\(\s*\"\"\"(.+?)\"\"\"\s*\)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(kotlinSource)
            .map { it.groupValues[1].trim() }
            .filter { it.startsWith("^") }
            .toList()
        assertEquals("expected logcat + unified patterns", 2, patterns.size)
        val rust = rustText() + File(repoRoot, "manager/src/main/rust/src/logparse.rs").readText()
        patterns.forEach { pattern ->
            assertTrue(
                "The Rust module must quote the Kotlin pattern it mirrors:\n$pattern",
                rust.contains(pattern),
            )
        }
    }
}
