package nd.max.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الترويسة هي ما يجعل **ملف السجل وحده** كافيًا: من دونه لا يُعرف أيّ جهاز ولا أيّ إعداد ولا معنى
 * الرموز، فيصير كل تشخيص أسئلة.
 *
 * والفحص هنا يقيس ثلاثة أشياء: أن كل سطر حدث قابل للفكّ (`EVENT=LOG_HEADER`)، وأن الجهاز
 * والإعداد يُكتبان كما هما، وأن كتلة الجلسة تحمل **ما طُلب فعلًا** — وهي التي تجيب
 * «هل فشل التطبيق أم فشل الطلب؟».
 */
class LogHeaderTest {

    private val facts = DeviceFacts(
        appVersion = "3.1.0",
        moduleVersion = "v42",
        socModel = "MT6789",
        socManufacturer = "MediaTek",
        hardware = "rodin",
        apiLevel = 34,
        kernel = "5.10.136-android13",
        rooted = true,
        board = "rodin",
        abi = "arm64-v8a",
    )

    @Test
    fun `every header line is an event the same parser can read`() {
        val lines = LogHeader.startupLines(facts, listOf("detailed_log" to "1"))

        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            // السطر هنا هو **نصّ الرسالة** نفسه — وهو ما يُفكَّك فعلًا؛ فأي `TAG: ` يضيفه
            // الكاتب، والفكّ لا يمرّ به. واستخراجه بـ`substringAfter` كان يقطع السطر عند أول
            // `: ` داخل المعنى نفسه.
            val parsed = LogEventParser.parse(line)
            assertEquals("سطر ترويسة لا يُفكَّك كحدث: $line", LogHeader.EVENT, parsed?.event)
        }
    }

    @Test
    fun `the device block names the machine the log came from`() {
        val parsed = LogEventParser.parse(LogHeader.deviceLines(facts).single())!!

        assertEquals("device", parsed.field("kind"))
        assertEquals("3.1.0", parsed.field("app"))
        assertEquals("v42", parsed.field("module"))
        assertEquals("MediaTek/MT6789", parsed.field("soc"))
        assertEquals("34", parsed.field("api"))
        assertEquals("yes", parsed.field("root"))
    }

    @Test
    fun `a fact we could not read is a dash not a plausible word`() {
        val parsed = LogEventParser.parse(
            LogHeader.deviceLines(facts.copy(moduleVersion = null)).single(),
        )!!

        assertEquals("-", parsed.field("module"))
    }

    @Test
    fun `a blank setting is declared unset rather than left empty`() {
        val lines = LogHeader.settingsLines(listOf("detailed_log" to "1", "thermal_core" to ""))

        val values = lines.map { LogEventParser.parse(it)!!.field("value") }

        assertEquals(listOf("1", "unset"), values)
    }

    @Test
    fun `the guide and the legend travel inside the file`() {
        val lines = LogHeader.guideLines() + LogHeader.legendLines()

        assertTrue("شرح الرمز في مستند آخر يساوي بلا شرح", lines.any { it.contains("code=manual-lock") })
        assertTrue(lines.any { it.contains("unit=gpu_frequency:DEVICE") })
    }

    @Test
    fun `the file carries what to do and not only what happened`() {
        val lines = LogHeader.remedyLines()

        assertTrue("رمز عطل بلا إصلاح يعني سؤالًا آخر لصاحب الجهاز", lines.any { it.contains("code=manual-lock") })
        assertTrue(lines.all { it.contains("fix=") })
    }

    @Test
    fun `the reading order is written before the device so the file is read the right way`() {
        val lines = LogHeader.startupLines(facts, emptyList())

        val first = LogEventParser.parse(lines.first())!!
        assertEquals("howto", first.field("kind"))
        assertEquals(
            "خطوة القراءة تُرقَّم كما تُقرأ",
            "1",
            first.field("step"),
        )
        // والقواميس تُكتب كاملة: قَصّ جدول الإصلاح كان يُسقط ما يُقرأ الملف من أجله.
        assertTrue(
            "الترويسة بلغت حدّها وقُصّت",
            lines.none { LogEventParser.parse(it)?.field("kind") == "truncated" },
        )
    }

    @Test
    fun `a session block says which app started and what it asked for`() {
        val lines = LogHeader.sessionLines(
            sessionId = "sw-1798",
            packageName = "com.example.game",
            startedAt = "2026-09-21 10:15:32",
            knobs = listOf("gpu_profile" to "gaming", "cpu_limits:policy0" to "300000:2000000"),
        )

        val parsed = lines.map { LogEventParser.parse(it)!! }

        assertEquals("session", parsed.first().field("kind"))
        assertEquals("com.example.game", parsed.first().field("pkg"))
        assertEquals("sw-1798", parsed.first().field("id"))
        val knobs = parsed.filter { it.field("kind") == "session-knob" }
        assertEquals(2, knobs.size)
        assertEquals(
            "بدون هذا السطر يُعرف أن الكتابة فشلت ولا يُعرف ما طُلب أصلًا",
            "300000:2000000",
            knobs.first { it.field("knob") == "cpu_limits:policy0" }?.field("desired"),
        )
    }

    @Test
    fun `a global session is named global rather than left without a package`() {
        val parsed = LogEventParser.parse(
            LogHeader.sessionLines("sw-1", null, "2026-09-21 10:00:00", emptyList()).single(),
        )!!

        assertEquals("global", parsed.field("pkg"))
    }
}
