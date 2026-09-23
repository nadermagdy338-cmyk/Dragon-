package nd.max.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * إشارة هامش الحرارة (`getThermalHeadroom`) — القواعد هنا من وثيقة المنصة، لا من التخمين:
 * ‏٠ بارد · ‏١٫٠ عتبة الخنق الشديد · والاستقصاء الأسرع من عشر ثوانٍ قد يُعيد `NaN`.
 *
 * وما لا يُثبته هذا الملف: هل تُعيد الإشارة قيمًا على MediaTek أصلًا؟ وبأي عتبات؟ — **يحتاج جهازًا**.
 */
class AtlasThermalHeadroomTest {

    @Test
    fun `the platform threshold is one and our bands are declared around it`() {
        assertEquals(AtlasHeadroomBand.COOL, band(0.30))
        assertEquals(AtlasHeadroomBand.WARMING, band(0.60))
        assertEquals(AtlasHeadroomBand.NEAR_THROTTLE, band(0.90))
        assertEquals(AtlasHeadroomBand.THROTTLED, band(1.0))
        assertEquals(AtlasHeadroomBand.THROTTLED, band(1.4))
    }

    @Test
    fun `an unread value is unmeasurable and never rounded down to cool`() {
        assertEquals(AtlasHeadroomBand.UNMEASURABLE, AtlasThermalHeadroomPolicy.band(reading(null)))
        assertEquals(AtlasHeadroomBand.UNMEASURABLE, band(-0.1))
    }

    @Test
    fun `NaN cannot even be constructed as a reading`() {
        assertThrows(IllegalArgumentException::class.java) {
            AtlasHeadroomReading(Double.NaN, AtlasThermalHeadroomPolicy.DEFAULT_FORECAST_SECONDS, 0L)
        }
    }

    @Test
    fun `polling faster than the documented spacing is refused so a NaN is never stored`() {
        val policy = AtlasThermalHeadroomPolicy
        assertTrue(policy.mayPoll(lastPollAtElapsedMs = null, nowElapsedMs = 5_000L))
        assertFalse(policy.mayPoll(lastPollAtElapsedMs = 5_000L, nowElapsedMs = 14_999L))
        assertTrue(policy.mayPoll(lastPollAtElapsedMs = 5_000L, nowElapsedMs = 15_000L))
    }

    @Test
    fun `a measured reading becomes an effect sample whose direction is lower is better`() {
        val sample = AtlasThermalHeadroomPolicy.asEffectSample(
            reading = reading(0.72, atMs = 9_000L),
            source = SOURCE,
            bootGeneration = 4L,
        )!!

        assertEquals(AtlasEffectMetric.THERMAL_HEADROOM, sample.metric)
        assertEquals(AtlasEffectDirection.LOWER_IS_BETTER, sample.metric.direction)
        assertEquals(AtlasSemanticStatus.REVIEWED_MATCH, sample.semanticStatus)
        assertEquals(0.72, sample.value, 1e-9)
        assertEquals(9_000L, sample.observedAtElapsedMs)
    }

    @Test
    fun `an unread reading yields no effect sample rather than a zero`() {
        assertNull(
            AtlasThermalHeadroomPolicy.asEffectSample(reading(null), SOURCE, bootGeneration = 4L),
        )
    }

    // ── التقاطع: الإشارة أمامًا والمتنبئ أمامًا، فلا يُدمجان في رقم واحد ──────

    @Test
    fun `platform says throttling ahead while the predictor says cooling is a declared contradiction`() {
        val result = AtlasThermalCrossCheck.evaluate(reading(0.95), predictedCelsius = 60.0, currentCelsius = 70.0)

        assertEquals(AtlasCrossCheckOutcome.CONTRADICTED, result.outcome)
        assertEquals(AtlasCrossCheckReasons.HOT_VERSUS_COOLING, result.reason)
        // القيمتان محفوظتان معًا: الاختلاف يُعرض ولا يُختار أحدهما بالصمت.
        assertEquals(0.95, result.headroom!!, 1e-9)
        assertEquals(60.0, result.predictedCelsius!!, 1e-9)
        assertEquals(70.0, result.currentCelsius!!, 1e-9)
    }

    @Test
    fun `platform says cool while the predictor says a sharp rise is a declared contradiction too`() {
        val result = AtlasThermalCrossCheck.evaluate(reading(0.20), predictedCelsius = 82.0, currentCelsius = 70.0)

        assertEquals(AtlasCrossCheckOutcome.CONTRADICTED, result.outcome)
        assertEquals(AtlasCrossCheckReasons.COOL_VERSUS_HEATING, result.reason)
    }

    @Test
    fun `agreeing signals agree and small moves are not treated as direction`() {
        assertEquals(
            AtlasCrossCheckOutcome.AGREE,
            AtlasThermalCrossCheck.evaluate(reading(0.95), predictedCelsius = 74.0, currentCelsius = 70.0).outcome,
        )
        assertEquals(
            AtlasCrossCheckOutcome.AGREE,
            AtlasThermalCrossCheck.evaluate(reading(0.90), predictedCelsius = 70.5, currentCelsius = 70.0).outcome,
        )
    }

    @Test
    fun `cross checking without both signals is unmeasurable not agreement`() {
        assertEquals(
            AtlasCrossCheckOutcome.UNMEASURABLE,
            AtlasThermalCrossCheck.evaluate(null, 70.0, 70.0).outcome,
        )
        assertEquals(
            AtlasCrossCheckOutcome.UNMEASURABLE,
            AtlasThermalCrossCheck.evaluate(reading(0.9), null, 70.0).outcome,
        )
    }

    // ── الوصل مع طبقة الأثر: الإشارة كلفةً تُقاس في القرار ───────────────────

    @Test
    fun `a raised gpu ceiling that heats the device is kept but named as a trade`() {
        val goal = AtlasEffectMath.compare(
            AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED,
            gpuSample(520_000.0, atMs = 0L),
            gpuSample(702_000.0, atMs = 11_000L),
        )
        val cost = AtlasEffectMath.compare(
            AtlasEffectMetric.THERMAL_HEADROOM,
            headroomSample(0.40, atMs = 0L),
            headroomSample(0.92, atMs = 11_000L),
        )

        val outcome = AtlasEffectAcceptance.decide(goal, AtlasEffectWindow(0L, 11_000L), cost = cost)

        assertEquals(AtlasEffectVerdict.WORSENED, cost.verdict)
        assertEquals(AtlasEffectDecision.KEEP, outcome.decision)
        assertEquals(AtlasAcceptanceReason.GOAL_IMPROVED_COST_WORSENED, outcome.reason)
    }

    @Test
    fun `headroom readings recorded and compared through the ledger answer whether it got hotter`() {
        val io = MemoryIo()
        val store = AtlasEffectStore(io)
        store.record(AtlasEffectRecord(TARGET, headroomSample(0.40, atMs = 0L)))
        store.record(AtlasEffectRecord(TARGET, headroomSample(0.92, atMs = 11_000L)))

        val before = store.latestBefore(TARGET, AtlasEffectMetric.THERMAL_HEADROOM, atMs = 5_000L)
        val after = store.latest(TARGET, AtlasEffectMetric.THERMAL_HEADROOM)
        val delta = AtlasEffectMath.compare(AtlasEffectMetric.THERMAL_HEADROOM, before, after)

        assertEquals(AtlasEffectVerdict.WORSENED, delta.verdict)
    }

    // ── أدوات الاختبار ───────────────────────────────────────────────────────

    private fun band(value: Double) = AtlasThermalHeadroomPolicy.band(reading(value))

    private fun reading(headroom: Double?, atMs: Long = 1_000L) = AtlasHeadroomReading(
        headroom = headroom,
        forecastSeconds = AtlasThermalHeadroomPolicy.DEFAULT_FORECAST_SECONDS,
        observedAtElapsedMs = atMs,
    )

    private fun headroomSample(value: Double, atMs: Long) = AtlasEffectSample(
        metric = AtlasEffectMetric.THERMAL_HEADROOM,
        value = value,
        observedAtElapsedMs = atMs,
        source = SOURCE,
        bootGeneration = 4L,
    )

    private fun gpuSample(value: Double, atMs: Long) = AtlasEffectSample(
        metric = AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED,
        value = value,
        observedAtElapsedMs = atMs,
        source = GPU_SOURCE,
        bootGeneration = 4L,
    )

    private class MemoryIo : AtlasStoreIo {
        private val entries = LinkedHashMap<String, String>()
        override fun read(name: String): String? = entries[name]
        override fun write(name: String, text: String): Boolean {
            entries[name] = text
            return true
        }

        override fun delete(name: String): Boolean = entries.remove(name) != null
        override fun list(): List<String> = entries.keys.sorted()
    }

    private companion object {
        const val SOURCE = "PowerManager.getThermalHeadroom"
        const val GPU_SOURCE = "sys/class/devfreq/13000000.mali/max_freq"
        const val TARGET = "gpu.ceiling"
    }
}
