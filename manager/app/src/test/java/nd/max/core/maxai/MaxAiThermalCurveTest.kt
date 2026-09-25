/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * عقد المنحنى الحراري — الشرط الذي يجعل تعديل سلوك التبريد قابلًا للتسليم.
 *
 * المحرك القديم كان يعطي سقفين ثابتين (0.55 داخل نطاق التدخل، 0.35 عند
 * الحرجة). المنحنى الجديد يعطي رقمًا متدرّجًا، وهو تغيير يمسّ حماية الجهاز،
 * فلا يُقبل بلا دعوى قابلة للتكذيب. الدعوى هنا واحدة ومُختبَرة:
 *
 *   **المنحنى لا يعطي سقفًا أخفّ من القاعدة القديمة في أي نقطة، والبند
 *   التكاملي يشدّد ولا يرخي.**
 *
 * أي أن كل فرق عن السلوك السابق هو كتابة في اتجاه الأمان — لا في اتجاه
 * الأداء. وهذا ما يجعل المخاطرة على المستخدم صفرًا حتى قبل التجربة على جهاز.
 */
class MaxAiThermalCurveTest {

    /**
     * نفس أرقام `SafetyEngine` الحالية. يُتحقّق من بقائها متطابقة بفحص مصدري
     * في [the engine builds the curve from its own safety constants]، وهو الأهم:
     * الرقم الواحد لا يُكتب مرتين في كود المنتج.
     */
    private val engageC = 48f
    private val criticalC = 52f
    private val releaseC = 43f
    private val engageCap = 0.55f
    private val criticalCap = 0.35f

    private fun config() = MaxAiThermalCurve.Config(
        engageC = engageC,
        criticalC = criticalC,
        releaseC = releaseC,
        engageCap = engageCap,
        criticalCap = criticalCap,
    )

    /** القاعدة القديمة التي يجب ألا يكون المنحنى أخفّ منها. */
    private fun legacyCap(level: SafetyLevel): Float =
        if (level == SafetyLevel.CRITICAL) criticalCap else engageCap

    private fun levelFor(temperatureC: Float): SafetyLevel =
        if (temperatureC >= criticalC) SafetyLevel.CRITICAL else SafetyLevel.ENGAGED

    private fun assessAt(temperatureC: Float, state: MaxAiThermalCurve.State = MaxAiThermalCurve.State()) =
        MaxAiThermalCurve.assess(
            thermalC = temperatureC,
            level = levelFor(temperatureC),
            previous = state,
            config = config(),
        )

    // ── الدعوى الأساسية: لا أخفّ من القاعدة القديمة ──────────────

    @Test
    fun `the curve is never looser than the legacy fixed caps`() {
        var temperature = engageC
        while (temperature <= criticalC + 12f) {
            val cap = assessAt(temperature).capFraction
            assertTrue(
                "عند $temperature° السقف $cap أخفّ من ${legacyCap(levelFor(temperature))}",
                cap <= legacyCap(levelFor(temperature)) + 1e-4f,
            )
            assertTrue("السقف لا ينزل تحت الأرضية المعلنة", cap >= 0.25f - 1e-4f)
            assertTrue("السقف لا يتجاوز 1", cap <= 1f)
            temperature += 0.25f
        }
    }

    @Test
    fun `the legacy boundaries are reproduced exactly with a fresh state`() {
        assertEquals(engageCap, assessAt(engageC).capFraction, 1e-4f)
        assertEquals(criticalCap, assessAt(criticalC).capFraction, 1e-4f)
    }

    @Test
    fun `an unengaged level never caps anything`() {
        val assessment = MaxAiThermalCurve.assess(
            thermalC = 40f,
            level = SafetyLevel.NORMAL,
            previous = MaxAiThermalCurve.State(),
            config = config(),
        )

        assertEquals(1f, assessment.capFraction, 0f)
        assertEquals(MaxAiThermalCurve.Reason.NORMAL, assessment.reason)
        assertFalse(assessment.capping)
    }

    @Test
    fun `the cap never rises while the temperature rises inside the engaged band`() {
        var previousCap = Float.MAX_VALUE
        var temperature = engageC
        while (temperature <= criticalC) {
            val cap = assessAt(temperature).capFraction
            assertTrue(
                "السقف ارتفع من $previousCap إلى $cap عند $temperature°",
                cap <= previousCap + 1e-4f,
            )
            previousCap = cap
            temperature += 0.25f
        }
    }

    @Test
    fun `beyond the critical threshold the cap keeps tightening towards the floor`() {
        val atCritical = assessAt(criticalC)
        val deep = assessAt(criticalC + 4f)
        val deeper = assessAt(criticalC + 20f)

        assertTrue(deep.capFraction < atCritical.capFraction)
        assertEquals(
            "النزول يتوقف عند الأرضية ولا يعبر إليها",
            0.25f,
            deeper.capFraction,
            1e-4f,
        )
        assertEquals(MaxAiThermalCurve.Reason.OVERSHOOT, deep.reason)
    }

    // ── البند التكاملي: يشدّد فقط ───────────────────────────────

    @Test
    fun `the integral term only ever tightens and is bounded`() {
        var state = MaxAiThermalCurve.State()
        var lastCap = assessAt(criticalC + 3f, state).capFraction
        repeat(2_000) {
            val assessment = assessAt(criticalC + 3f, state)
            state = MaxAiThermalCurve.State(assessment.integral, assessment.coolStreak)
            assertTrue("التكامل رخّى السقف", assessment.capFraction <= lastCap + 1e-4f)
            assertTrue(assessment.integral <= config().integralCap + 1e-4f)
            lastCap = assessment.capFraction
        }
        assertTrue(
            "بالتراكم الطويل يجب أن يصل التشديد إلى سقفه المعلن",
            state.integral >= config().integralCap - 1e-3f,
        )
    }

    @Test
    fun `the integral term stays quiet inside the cutoff band`() {
        val state = MaxAiThermalCurve.assess(
            thermalC = engageC + 0.1f,
            level = SafetyLevel.ENGAGED,
            previous = MaxAiThermalCurve.State(),
            config = config(),
        )

        assertEquals("ضجيج دون العتبة ليس انحرافًا نظاميًا", 0f, state.integral, 1e-6f)
    }

    @Test
    fun `a longer measurement gap accumulates more correction than a short one`() {
        val long = MaxAiThermalCurve.assess(
            thermalC = criticalC + 2f,
            level = SafetyLevel.CRITICAL,
            previous = MaxAiThermalCurve.State(),
            config = config(),
            intervalMs = 8_000L,
        )
        val short = MaxAiThermalCurve.assess(
            thermalC = criticalC + 2f,
            level = SafetyLevel.CRITICAL,
            previous = MaxAiThermalCurve.State(),
            config = config(),
            intervalMs = 1_000L,
        )

        assertTrue(long.integral > short.integral)
    }

    @Test
    fun `cooling decays the accumulated correction so a transient dip is not a life sentence`() {
        val hot = MaxAiThermalCurve.assess(
            thermalC = criticalC + 6f,
            level = SafetyLevel.CRITICAL,
            previous = MaxAiThermalCurve.State(),
            config = config(),
        )
        val cooled = MaxAiThermalCurve.assess(
            thermalC = releaseC - 2f,
            level = SafetyLevel.ENGAGED,
            previous = MaxAiThermalCurve.State(hot.integral, hot.coolStreak),
            config = config(),
        )

        assertTrue(cooled.integral < hot.integral)
        assertTrue(cooled.integral >= 0f)
    }

    // ── الاسترجاع: لا تأرجح حول العتبة ───────────────────────────

    @Test
    fun `release needs consecutive cool measurements, not a single one`() {
        val cfg = config()
        var state = MaxAiThermalCurve.State()

        val first = MaxAiThermalCurve.assess(releaseC - 1f, SafetyLevel.ENGAGED, state, cfg)
        assertFalse("قياس بارد واحد لا يكفي للاسترجاع", first.releaseReady)
        assertEquals(1, first.coolStreak)

        state = MaxAiThermalCurve.State(first.integral, first.coolStreak)
        val second = MaxAiThermalCurve.assess(releaseC - 1f, SafetyLevel.ENGAGED, state, cfg)
        assertFalse(second.releaseReady)

        state = MaxAiThermalCurve.State(second.integral, second.coolStreak)
        val third = MaxAiThermalCurve.assess(releaseC - 1f, SafetyLevel.ENGAGED, state, cfg)
        assertTrue("بعد ثلاث قياسات يُؤكَّد التبريد", third.releaseReady)
        assertEquals(cfg.releaseConfirmSamples, third.coolStreak)
    }

    @Test
    fun `one warm measurement resets the cool streak so flapping never releases`() {
        val cfg = config()
        val almost = MaxAiThermalCurve.State(integral = 0f, coolStreak = cfg.releaseConfirmSamples - 1)

        val warm = MaxAiThermalCurve.assess(releaseC + 1f, SafetyLevel.ENGAGED, almost, cfg)

        assertEquals(0, warm.coolStreak)
        assertFalse(warm.releaseReady)
    }

    // ── حالات شاذة: الصمت أصدق من تخمين ──────────────────────────

    @Test
    fun `a missing reading changes nothing`() {
        val before = MaxAiThermalCurve.State(integral = 0.05f, coolStreak = 2)
        val assessment = MaxAiThermalCurve.assess(
            thermalC = 0f,
            level = SafetyLevel.ENGAGED,
            previous = before,
            config = config(),
        )

        assertEquals(1f, assessment.capFraction, 0f)
        assertEquals(before.integral, assessment.integral, 0f)
        assertEquals(before.coolStreak, assessment.coolStreak)
        assertFalse(assessment.releaseReady)
    }

    @Test
    fun `a degenerate config does not divide by zero or escape the floor`() {
        val degenerate = MaxAiThermalCurve.Config(
            engageC = 50f,
            criticalC = 50f,
            releaseC = 50f,
            engageCap = 0.55f,
            criticalCap = 0.35f,
            overshootSpanC = 0f,
        )
        val assessment = MaxAiThermalCurve.assess(60f, SafetyLevel.CRITICAL, MaxAiThermalCurve.State(), degenerate)

        assertTrue(assessment.capFraction in 0.25f..1f)
        assertTrue(assessment.integral.isFinite())
    }

    // ── تقريب الكتابة: خطوة كاملة أو لا كتابة ────────────────────

    @Test
    fun `quantization snaps to the declared step`() {
        assertEquals(0.55f, MaxAiThermalCurve.quantize(0.55f), 1e-4f)
        assertEquals(0.5f, MaxAiThermalCurve.quantize(0.549f), 1e-4f)
        assertEquals(0.35f, MaxAiThermalCurve.quantize(0.35f), 1e-4f)
        assertEquals(0f, MaxAiThermalCurve.quantize(0.049f), 1e-4f)
    }

    @Test
    fun `only a full step tighter justifies another hardware write`() {
        assertTrue(MaxAiThermalCurve.isTighterByStep(current = 0.55f, candidate = 0.50f))
        assertTrue(MaxAiThermalCurve.isTighterByStep(current = 0.55f, candidate = 0.30f))
        assertFalse(
            "فرق عُشري أصغر من الخطوة ليس سببًا لكتابة",
            MaxAiThermalCurve.isTighterByStep(current = 0.55f, candidate = 0.52f),
        )
        assertFalse(
            "الترخية لا تمرّ من هذا الباب أبدًا",
            MaxAiThermalCurve.isTighterByStep(current = 0.55f, candidate = 0.60f),
        )
    }

    // ── حراسة المصدر: لا رقم واحد يُكتب مرتين ─────────────────────

    /**
     * حارس مصدري لا اختبار سلوك: القيم الحدّية للمنحنى يجب أن تُبنى من ثوابت
     * `SafetyEngine` نفسها. لو نسخ أحدهم الرقمين إلى المكانين، لأصبح للسلامة
     * مصدرا حقيقة يتفرّقان بصمت — وهذا بالضبط ما تمنعه هذه الحالة.
     */
    @Test
    fun `the engine builds the curve from its own safety constants`() {
        val root = listOf(
            File("src/main/java/nd/max/core/maxai"),
            File("app/src/main/java/nd/max/core/maxai"),
            File("../app/src/main/java/nd/max/core/maxai"),
            File("manager/app/src/main/java/nd/max/core/maxai"),
        ).firstOrNull { it.isDirectory }
        assumeTrue("لا يمكن تحديد مسار المصادر داخل مدير النصوص: لم يُقيَّم الحارس", root != null)

        val engine = File(root, "SafetyEngine.kt").readText()
        val curve = engine.substringAfter("private val CURVE = MaxAiThermalCurve.Config(", "")
            .substringBefore(")")
        listOf(
            "engageC = ENGAGE_TEMP_C",
            "criticalC = CRITICAL_TEMP_C",
            "releaseC = RELEASE_TEMP_C",
            "engageCap = ENGAGE_CAP_FRACTION",
            "criticalCap = CRITICAL_CAP_FRACTION",
        ).forEach { binding ->
            assertTrue("المنحنى لا يأخذ $binding من المحرك", curve.contains(binding))
        }
        assertFalse(
            "أرقام السلامة مكرّرة حرفيًا داخل كتلة المنحنى",
            Regex("= 0\\.(55|35)f").containsMatchIn(curve),
        )
    }

    @Before
    fun sanityCheckTheAssumptionsOfThisFile() {
        assumeTrue("افتراضات الملف تخص بنية المحرك الحالية", engageC < criticalC && releaseC < engageC)
    }
}
