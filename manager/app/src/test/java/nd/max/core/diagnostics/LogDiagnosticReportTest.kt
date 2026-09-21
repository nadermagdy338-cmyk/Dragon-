package nd.max.core.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد التقرير التشخيصي: ما يجب أن يقوله لمن يقرأه لأول مرة — أيّ جهاز، وما أكثر ما يتكرّر عطله،
 * وهل النصّ كامل أم نافذة مقتطعة.
 */
class LogDiagnosticReportTest {

    private val facts = DeviceFacts(
        appVersion = "3.1.0",
        moduleVersion = "v42",
        socModel = "MT6789",
        socManufacturer = "MediaTek",
        hardware = "rodin",
        apiLevel = 34,
        kernel = "5.10.136-android13",
        rooted = true,
    )

    @Test
    fun `the report names the device and the app before anything else`() {
        val report = LogDiagnosticReport.build(facts, emptyList(), "2026-09-21 10:00:00", truncated = false)

        assertTrue(report.contains("app_version=3.1.0"))
        assertTrue(report.contains("module_version=v42"))
        assertTrue(report.contains("soc=MediaTek/MT6789"))
        assertTrue(report.contains("android_api=34"))
        assertTrue("غياب الجذر حقيقة تشخيصية لا تفصيل", report.contains("root=yes"))
    }

    @Test
    fun `an unknown fact is a dash and never a plausible word`() {
        val report = LogDiagnosticReport.build(
            facts.copy(moduleVersion = null, kernel = "  "),
            emptyList(),
            "2026-09-21 10:00:00",
            truncated = false,
        )

        assertTrue(report.contains("module_version=-"))
        assertTrue(report.contains("kernel=-"))
        // والتحقق على سطر الحقل نفسه لا على الملف كله: كلمة `unknown` موجودة في القاموس كرمز
        // شرعي («سبب لم يُعرف»)، والفحص الشامل كان سيصنّف القاموس نفسه خطأً.
        assertFalse("حقل مجهول يُرسم شرطة، ولا يُشبه قيمة حقيقية", report.contains("module_version=unknown"))
        assertFalse(report.contains("kernel=unknown"))
    }

    @Test
    fun `failures are counted by area and by repeated reason`() {
        val report = LogDiagnosticReport.build(
            facts,
            listOf(
                line(id = 1, area = LogArea.CPU, reason = "apply-not-verified", raw = "EVENT=PERAPP_KNOB knob=cpu_limits:policy0"),
                line(id = 2, area = LogArea.CPU, reason = "apply-not-verified", raw = "EVENT=PERAPP_KNOB knob=cpu_limits:policy0"),
                line(id = 3, area = LogArea.GPU, reason = "manual-lock", raw = "EVENT=PERAPP_KNOB knob=gpu_profile"),
            ),
            "2026-09-21 10:00:00",
            truncated = false,
        )

        assertTrue(report.contains("failures=3"))
        assertTrue(report.contains("cpu=2"))
        assertTrue(report.contains("gpu=1"))
        assertTrue(
            "التكرار هو ما يفرّق عطلًا عارضًا عن عطل مزمن",
            report.contains("2x cpu/PERAPP_KNOB reason=apply-not-verified"),
        )
    }

    @Test
    fun `a failure line keeps its raw reason so it stays greppable`() {
        val report = LogDiagnosticReport.build(
            facts,
            listOf(line(id = 1, reason = "route-verified", raw = "EVENT=PERAPP_THERMAL_GUARD route=thermal.platform-status verified=false")),
            "2026-09-21 10:00:00",
            truncated = false,
        )

        assertTrue(report.contains("reason=route-verified"))
        assertTrue(report.contains("thermal/PERAPP_THERMAL_GUARD"))
    }

    @Test
    fun `a clean buffer says none instead of an empty section`() {
        val report = LogDiagnosticReport.build(
            facts,
            listOf(line(id = 1, verdict = LogVerdict.OK, reason = "verified", raw = "EVENT=PERAPP_KNOB outcome=applied")),
            "2026-09-21 10:00:00",
            truncated = false,
        )

        assertTrue(report.contains("failures=0"))
        assertTrue(report.contains("-- failures by area --\nnone"))
    }

    @Test
    fun `a truncated window declares itself`() {
        val report = LogDiagnosticReport.build(facts, emptyList(), "2026-09-21 10:00:00", truncated = true)

        assertTrue("تقرير يبدو كاملًا وهو نافذة أسوأ من تقرير يقول إنه ناقص", report.contains("window_truncated=true"))
    }

    @Test
    fun `the artifact stays bounded even with a huge buffer`() {
        val many = (1..5_000).map { index ->
            line(id = index.toLong(), reason = "repeat-$index", raw = "EVENT=PERAPP_KNOB knob=gpu_profile outcome=not-verified line=$index")
        }

        val report = LogDiagnosticReport.build(facts, many, "2026-09-21 10:00:00", truncated = true)

        assertTrue("الحدّ قبل البناء لا بعده", report.length <= LogDiagnosticReport.MAX_CHARS)
        assertTrue(report.contains("failures=5000"))
        assertTrue(
            "وما أُسقط يُعلن عدده بدل أن يختفي",
            report.contains("more failure lines omitted"),
        )
    }

    private fun line(
        id: Long,
        area: LogArea = LogArea.CPU,
        verdict: LogVerdict = LogVerdict.FAIL,
        reason: String = "apply-not-verified",
        raw: String = "EVENT=PERAPP_KNOB",
    ) = ReportLine(
        time = "10:00:${id.toString().padStart(2, '0')}",
        level = "W",
        source = "appmonitor",
        event = "PERAPP_KNOB",
        area = area,
        verdict = verdict,
        reason = reason,
        raw = raw,
    )

    @Test
    fun `the tail keeps the most recent lines in reading order`() {
        val lines = (1..10).map { line(id = it.toLong(), verdict = LogVerdict.OK, reason = "verified", raw = "EVENT=PERAPP_KNOB line=$it") }

        val report = LogDiagnosticReport.build(facts, lines, "2026-09-21 10:00:00", truncated = false)
        val tail = report.substringAfter("-- raw log (oldest first) --")

        assertTrue("الأقدم أولًا: القارئ يقرأ السبب قبل النتيجة", tail.indexOf("line=1") < tail.indexOf("line=10"))
    }

    @Test
    fun `the bundle carries the settings that were in effect`() {
        val report = LogDiagnosticReport.build(
            facts,
            emptyList(),
            "2026-09-21 10:00:00",
            truncated = false,
            settings = listOf("detailed_log" to "1", "log_max_kb" to "3072", "thermal_core" to ""),
        )

        assertTrue("-- configured --" in report)
        assertTrue(report.contains("detailed_log=1"))
        assertTrue("إعداد فارغ يُعلَن غير مضبوط لا يُترك فراغًا", report.contains("thermal_core=unset"))
    }

    @Test
    fun `the bundle explains its own fields units and codes`() {
        val report = LogDiagnosticReport.build(
            facts,
            listOf(line(id = 1, reason = "apply-not-verified-baseline-restored")),
            "2026-09-21 10:00:00",
            truncated = false,
        )

        assertTrue("-- how to read --" in report)
        assertTrue("وحدة المقبض أخطر ما كان غائبًا", report.contains("unit gpu_frequency:DEVICE = Hz"))
        assertTrue(report.contains("cpu_limits:POLICY = kHz"))
        assertTrue("-- code legend --" in report)
        assertTrue(report.contains("apply-not-verified-baseline-restored = "))
        assertTrue("وقرار Atlas يُشرح بأحد أجزائه", report.contains("privilege-unavailable = "))
    }

    @Test
    fun `a bundle asked for the whole log carries it`() {
        val lines = (1..400).map { line(id = it.toLong(), verdict = LogVerdict.OK, reason = "verified", raw = "EVENT=PERAPP_KNOB line=$it") }

        val report = LogDiagnosticReport.build(
            facts,
            lines,
            "2026-09-21 10:00:00",
            truncated = false,
            rawTailLimit = Int.MAX_VALUE,
        )

        assertTrue("الحزمة تشمل السجل كامًلا لا ذيله", report.contains("EVENT=PERAPP_KNOB line=1") && report.contains("line=400"))
        assertTrue("وعنوانها يقول حزمة لا تقرير مختصر", report.lineSequence().first() == "MaxManager log bundle")
    }

    @Test
    fun `extra sections are carried as given`() {
        val report = LogDiagnosticReport.build(
            facts,
            emptyList(),
            "2026-09-21 10:00:00",
            truncated = false,
            extraSections = listOf("per-app knob status" to listOf("knob=gpu_profile outcome=not-verified reason=manual-lock")),
        )

        assertTrue(report.contains("-- per-app knob status --"))
        assertTrue(report.contains("reason=manual-lock"))
    }

    @Test
    fun `what to do carries the action for every failure in this file`() {
        val report = LogDiagnosticReport.build(
            facts,
            listOf(
                line(id = 1, reason = "manual-lock"),
                line(id = 2, reason = "manual-lock"),
                line(id = 3, reason = "outside-proven-hardware-bounds"),
            ),
            "2026-09-21 10:00:00",
            truncated = false,
        )

        val actions = report.substringAfter("-- what to do --").substringBefore("-- failure lines")

        assertTrue("الرمز يُتبع بإصلاحه لا بمعناه فقط", actions.contains("fix=${LogCodeGlossary.remedyOf("manual-lock")}"))
        assertTrue("والأكثر تكرارًا أولًا: هو ما يُصلَح أولًا", actions.indexOf("reason=manual-lock") < actions.indexOf("reason=outside-proven-hardware-bounds"))
        assertTrue(actions.contains("reason=manual-lock count=2"))
    }

    @Test
    fun `a failure with no encoded remedy is declared instead of passed over`() {
        val report = LogDiagnosticReport.build(
            facts,
            listOf(line(id = 1, reason = "a-code-nobody-encoded")),
            "2026-09-21 10:00:00",
            truncated = false,
        )

        assertTrue(
            "الرمز الجديد يُعلَن نصًّا فالمرّة القادمة تُقرأ بلا تخمين",
            report.contains("reason=a-code-nobody-encoded count=1 fix=no-fix-encoded"),
        )
    }

    @Test
    fun `a clean file says so instead of offering a fix list`() {
        val report = LogDiagnosticReport.build(
            facts,
            listOf(line(id = 1, verdict = LogVerdict.OK, reason = "verified")),
            "2026-09-21 10:00:00",
            truncated = false,
        )

        val actions = report.substringAfter("-- what to do --").substringBefore("-- failure lines")

        assertTrue("لا فشل يعني لا إصلاح، ولم يُدَّع عطل", actions.contains("no failures in this file"))
        assertFalse(actions.contains("fix="))
    }

    @Test
    fun `a failing knob row in a section also reaches the action list`() {
        // سطور حالة المقابض ليست أسطر سجل، لكنها تجيب «لماذا لم يعمل؟» — وإغفالها كان يجعل
        // أهمّ مقطع في الحزمة هو الوحيد بلا إصلاح. وتُحكَم بنفس قواعد الحكم لا بقواعد ثانية.
        val report = LogDiagnosticReport.build(
            facts,
            listOf(line(id = 1, verdict = LogVerdict.OK, reason = "verified")),
            "2026-09-21 10:00:00",
            truncated = false,
            extraSections = listOf(
                "per-app knob status" to listOf(
                    "knob=cpu_limits:policy0 outcome=not-verified reason=live-value-mismatch",
                    "knob=gpu_profile outcome=applied reason=verified",
                ),
            ),
        )

        val actions = report.substringAfter("-- what to do --").substringBefore("-- failure lines")

        assertTrue(actions.contains("reason=live-value-mismatch count=1"))
        assertFalse("مقبض نجح لا يُدرج إصلاحًا", actions.contains("reason=verified"))
    }

    @Test
    fun `the bundle teaches how to read it before it shows the log`() {
        val report = LogDiagnosticReport.build(facts, emptyList(), "2026-09-21 10:00:00", truncated = false)

        val howTo = report.substringAfter("-- how to read --").substringBefore("-- code legend --")

        LogCodeGlossary.howToRead.forEachIndexed { index, step ->
            assertTrue("خطوة القراءة ${index + 1} غائبة عن الحزمة", howTo.contains(step))
        }
    }
}
