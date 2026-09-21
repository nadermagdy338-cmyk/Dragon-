package nd.max.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد فكّ أسطر الأحداث. وكل حالة هنا مأخوذة من صيغة فعلية في المستودع، لا من نموذج مُتخيَّل:
 * `PERAPP_KNOB` و`PERAPP_THERMAL_GUARD` و`WRITE_CHECK` و`UI_ERROR` هي ما يكتبه المحرّك فعلًا.
 */
class LogEventLineTest {

    @Test
    fun `a knob line is split into named fields with its own reason`() {
        val line = LogEventParser.parse(
            "EVENT=PERAPP_KNOB knob=gpu_profile outcome=not-verified " +
                "reason=apply-not-verified-baseline-restored expected=1300000000 live=754000000 " +
                "pkg=com.example.game sw=sw-42",
        )

        assertEquals("PERAPP_KNOB", line?.event)
        assertEquals("gpu_profile", line?.field("knob"))
        assertEquals("not-verified", line?.field("outcome"))
        assertEquals("apply-not-verified-baseline-restored", line?.field("reason"))
        assertEquals("1300000000", line?.field("expected"))
        assertEquals("754000000", line?.field("live"))
        assertEquals("gpu_profile", line?.target)
        assertEquals("com.example.game", line?.packageName)
        assertEquals("EVENT is not repeated as a field", null, line?.field("EVENT"))
    }

    @Test
    fun `a value that contains spaces stays one field`() {
        // EventLog.error يضع رسالة الاستثناء كما هي في detail=، والفراغ داخلها ليس فاصلًا.
        val line = LogEventParser.parse(
            "EVENT=UI_ERROR screen=AppSettings operation=apply_profile " +
                "detail=IllegalStateException: node not writable at /sys/foo",
        )

        assertEquals("IllegalStateException: node not writable at /sys/foo", line?.field("detail"))
        assertEquals("AppSettings", line?.field("screen"))
    }

    @Test
    fun `a line without an event is not an event`() {
        assertNull(LogEventParser.parse("D MaxManager: started ok"))
        assertNull(LogEventParser.parse(""))
        assertNull("حدث بلا اسم ليس حدثًا", LogEventParser.parse("EVENT= stage=boot"))
    }

    @Test
    fun `an event with no fields still parses`() {
        val line = LogEventParser.parse("EVENT=DAEMON_READY")

        assertEquals("DAEMON_READY", line?.event)
        assertTrue(line?.fields?.isEmpty() == true)
    }

    @Test
    fun `the area follows the knob when the event name is generic`() {
        // PERAPP_KNOB حدث «لتطبيق»، لكن المقبض يقول إن العطل في CPU — وهو ما يُبحث عنه.
        assertEquals(
            LogArea.CPU,
            LogArea.of("PERAPP_KNOB", "cpu_limits:policy0"),
        )
        assertEquals(LogArea.GPU, LogArea.of("PERAPP_KNOB", "gpu_frequency:kgsl-3d0"))
    }

    @Test
    fun `a thermal event under per-app is classified as thermal`() {
        assertEquals(
            "الأخصّ يفوز: عطل حراري لا يجوز أن يُصنَّف «تطبيق» فيضيع",
            LogArea.THERMAL,
            LogArea.of("PERAPP_THERMAL_GUARD", "gpu_profile"),
        )
        assertEquals(LogArea.THERMAL, LogArea.of("PERAPP_THERMAL_UNSUPPORTED", null))
    }

    @Test
    fun `the remaining families are classified without a per-event table`() {
        assertEquals(LogArea.DISPLAY, LogArea.of("REFRESH_RATE_CAPPED", null))
        assertEquals(LogArea.USER, LogArea.of("USER_ACTION", null))
        assertEquals(LogArea.ENGINE, LogArea.of("APPLY_FAILED", null))
        assertEquals(LogArea.SYSTEM, LogArea.of("MODULE_INTEGRITY_FAILED", null))
        assertEquals(LogArea.APPS, LogArea.of("PRELOAD_SPAWN_FAILED", null))
        assertEquals(LogArea.CHARGING, LogArea.of("BYPASS_CHARGE_ENABLED", null))
        assertEquals(LogArea.OTHER, LogArea.of("SOMETHING_NEW", null))
    }

    @Test
    fun `a field decides the verdict before the event name does`() {
        val applied = LogEventParser.parse("EVENT=PERAPP_KNOB knob=gpu_profile outcome=applied reason=verified")
        val notVerified = LogEventParser.parse("EVENT=PERAPP_KNOB knob=gpu_profile outcome=not-verified reason=manual-lock")

        assertEquals(LogVerdict.OK, LogVerdict.of("PERAPP_KNOB", applied, "I"))
        assertEquals(LogVerdict.FAIL, LogVerdict.of("PERAPP_KNOB", notVerified, "W"))
    }

    @Test
    fun `a skipped knob is neither success nor failure`() {
        val skipped = LogEventParser.parse("EVENT=PERAPP_KNOB knob=gpu_governor outcome=skipped reason=governor-is-default")

        assertEquals(
            "«تُرك عمدًا» ليست «طُبِّق» ولا «فشل»: الخلط بينهما يجعل البروفايل الافتراضي يبدو عطلًا",
            LogVerdict.UNKNOWN,
            LogVerdict.of("PERAPP_KNOB", skipped, "I"),
        )
    }

    @Test
    fun `the write verdict words are read as the engine writes them`() {
        val differs = LogEventParser.parse("EVENT=WRITE_CHECK path=/sys/x wrote=1300000000 read=754000000 verdict=differs")
        val unreadable = LogEventParser.parse("EVENT=WRITE_CHECK path=/sys/x wrote=1300000000 read=? verdict=unreadable")

        assertEquals(LogVerdict.FAIL, LogVerdict.of("WRITE_CHECK", differs, "D"))
        assertEquals(
            "تعذّر القراءة لا يُصنَّف فشلًا ولا نجاحًا",
            LogVerdict.UNKNOWN,
            LogVerdict.of("WRITE_CHECK", unreadable, "D"),
        )
    }

    @Test
    fun `a failure is a failure even when its line is not an error level`() {
        // هذا هو العطب الذي وُجد التصنيف من أجله: فشلٌ كُتب بمستوى معلوماتي يختفي من «المشاكل فقط»
        // إذا كان الحكم من المستوى وحده.
        assertEquals(LogVerdict.FAIL, LogVerdict.of("APPLY_DRIFT_REASSERT_FAILED", null, "I"))
        assertEquals(LogVerdict.FAIL, LogVerdict.of("PERAPP_GOVERNOR_REJECTED", null, "I"))
        assertEquals(LogVerdict.UNKNOWN, LogVerdict.of("PROFILE_APPLY", null, "I"))
    }

    @Test
    fun `before the name rules, an explicit result field wins`() {
        val ok = LogEventParser.parse("EVENT=OP_RESULT screen=Debloat action=compile ok=false duration_ms=120")

        assertEquals(LogVerdict.FAIL, LogVerdict.of("OP_RESULT", ok, "I"))
    }

    @Test
    fun `history keeps the last measured value and counts the failures`() {
        val observations = listOf(
            observation(id = 1, target = "gpu_profile", verdict = LogVerdict.OK, reason = "verified", live = "754000000"),
            observation(id = 2, target = "cpu_limits:policy0", verdict = LogVerdict.FAIL, reason = "manual-lock"),
            observation(id = 3, target = "gpu_profile", verdict = LogVerdict.OK, reason = "verified"),
        )

        val summaries = LogTargetHistory.summarise(observations)

        assertEquals(
            "الفشل أولًا وإن كان أحدث سطر يخصّ غيره",
            "cpu_limits:policy0",
            summaries.first().target,
        )
        val gpu = summaries.first { it.target == "gpu_profile" }
        assertEquals(2, gpu.observations)
        assertEquals(0, gpu.failures)
        assertEquals("آخر قيمة مُقاسة باقية وإن جاء سطر بلا قياس بعدها", "754000000", gpu.live)
    }

    @Test
    fun `a later line without a reading does not erase the last one`() {
        val observations = listOf(
            observation(id = 1, target = "gpu_profile", verdict = LogVerdict.FAIL, reason = "drift", live = "490000000"),
            observation(id = 2, target = "gpu_profile", verdict = LogVerdict.FAIL, reason = "restore-not-verified"),
        )

        val summary = LogTargetHistory.summarise(observations).single()

        assertEquals("490000000", summary.live)
        assertEquals("restore-not-verified", summary.reason)
    }

    private fun observation(
        id: Long,
        target: String,
        verdict: LogVerdict,
        reason: String,
        live: String = "",
    ) = LogObservation(
        id = id,
        time = "10:00:0$id",
        target = target,
        verdict = verdict,
        reason = reason,
        expected = "",
        live = live,
        source = "appmonitor",
    )
}
