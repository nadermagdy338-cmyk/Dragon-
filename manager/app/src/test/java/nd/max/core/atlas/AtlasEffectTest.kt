package nd.max.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * طبقة الأثر — مُختبرة بلا جهاز، والحالات مأخوذة من سجلات جهاز حقيقي:
 *
 * ```
 * WRITE_CHECK … mali/max_freq wrote=520000000 matched      (power)
 * PERAPP_GPU_CAPABILITY_REQUESTED requested=702000000 live_before=520000000
 * ```
 *
 * أي أن السؤال الذي وُلد هذا الملف من أجله هو: «بعد أن كتبنا ٥٢٠ ثم طلبنا ٧٠٢ — هل تحقّق الهدف؟»
 * وليس «هل نجحت الكتابة؟» — وهذا هو الفرق بين أطلس الذي يقرأ العقدة وأطلس الذي يُثبت النتيجة.
 */
class AtlasEffectTest {

    // ── الاتجاه: الخطّاف الذي يُقلب أكثر ما يُقلب ─────────────────────────────

    @Test
    fun `raising the gpu ceiling after a cut is an improvement`() {
        val before = sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 520_000.0, atMs = 1_000L)
        val after = sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 702_000.0, atMs = 11_000L)

        val delta = AtlasEffectMath.compare(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, before, after)

        assertEquals(AtlasEffectVerdict.IMPROVED, delta.verdict)
        assertEquals(AtlasEffectReasons.IMPROVED, delta.reason)
        assertTrue(delta.measured)
        assertEquals(182_000.0, delta.delta!!, 0.001)
    }

    @Test
    fun `thermal headroom rising is a worsening even though its name sounds like more room`() {
        // الدلالة الموثَّقة: ٠ بارد · ١٫٠ عتبة الخنق الشديد ⇒ الرقم الأكبر أسوأ.
        val before = sample(AtlasEffectMetric.THERMAL_HEADROOM, 0.42, atMs = 1_000L)
        val after = sample(AtlasEffectMetric.THERMAL_HEADROOM, 0.91, atMs = 11_000L)

        val delta = AtlasEffectMath.compare(AtlasEffectMetric.THERMAL_HEADROOM, before, after)

        assertEquals(AtlasEffectDirection.LOWER_IS_BETTER, AtlasEffectMetric.THERMAL_HEADROOM.direction)
        assertEquals(AtlasEffectVerdict.WORSENED, delta.verdict)
    }

    // ── قاعدة «لا مقارنة عبر إقلاع» ──────────────────────────────────────────

    @Test
    fun `two samples from different boots are unmeasurable not unchanged`() {
        val before = sample(AtlasEffectMetric.CPU_FREQUENCY_TICK_WEIGHTED, 520_000.0, atMs = 1_000L, boot = 7L)
        val after = sample(AtlasEffectMetric.CPU_FREQUENCY_TICK_WEIGHTED, 520_000.0, atMs = 2_000L, boot = 8L)

        val delta = AtlasEffectMath.compare(AtlasEffectMetric.CPU_FREQUENCY_TICK_WEIGHTED, before, after)

        assertEquals(AtlasEffectVerdict.UNMEASURABLE, delta.verdict)
        assertEquals(AtlasEffectReasons.BOOT_CHANGED, delta.reason)
        assertFalse(delta.measured)
        assertNull(delta.delta)
    }

    @Test
    fun `a privilege change invalidates the comparison too`() {
        val before = sample(AtlasEffectMetric.POWER_WATTS, 4.2, atMs = 1_000L, privilege = 1L)
        val after = sample(AtlasEffectMetric.POWER_WATTS, 3.4, atMs = 2_000L, privilege = 2L)

        val delta = AtlasEffectMath.compare(AtlasEffectMetric.POWER_WATTS, before, after)

        assertEquals(AtlasEffectReasons.PRIVILEGE_CHANGED, delta.reason)
    }

    @Test
    fun `a missing sample is unmeasurable with no delta and no confidence`() {
        val after = sample(AtlasEffectMetric.FRAME_TIME_P95, 12.0, atMs = 2_000L)

        val delta = AtlasEffectMath.compare(AtlasEffectMetric.FRAME_TIME_P95, null, after)

        assertEquals(AtlasEffectVerdict.UNMEASURABLE, delta.verdict)
        assertEquals(AtlasEffectReasons.MISSING_SAMPLE, delta.reason)
        assertEquals(AtlasEffectConfidence.UNMEASURABLE, delta.confidence)
        assertFalse(delta.measured)
    }

    // ── التسامح: ما دونه ضجيج لا إنجاز ───────────────────────────────────────

    @Test
    fun `a change inside the tolerance is noise and never an improvement`() {
        val before = sample(AtlasEffectMetric.FPS_MEAN, 60.0, atMs = 1_000L)
        val after = sample(AtlasEffectMetric.FPS_MEAN, 60.1, atMs = 11_000L)

        val delta = AtlasEffectMath.compare(AtlasEffectMetric.FPS_MEAN, before, after, tolerancePermille = 30L)

        assertEquals(AtlasEffectVerdict.UNCHANGED, delta.verdict)
        assertEquals(AtlasEffectReasons.WITHIN_TOLERANCE, delta.reason)
    }

    @Test
    fun `a change beyond the tolerance is judged by direction`() {
        val before = sample(AtlasEffectMetric.JANK_PERCENT, 8.0, atMs = 1_000L)
        val after = sample(AtlasEffectMetric.JANK_PERCENT, 4.0, atMs = 11_000L)

        val delta = AtlasEffectMath.compare(AtlasEffectMetric.JANK_PERCENT, before, after)

        assertEquals(AtlasEffectVerdict.IMPROVED, delta.verdict)
        assertEquals(-500L, delta.relativePermille!!)
    }

    // ── التقاطع: لا دمج صامت لمصدرين ─────────────────────────────────────────

    @Test
    fun `two independent sources agreeing are corroborated`() {
        val primary = AtlasEffectMath.compare(
            AtlasEffectMetric.FRAME_TIME_P95,
            sample(AtlasEffectMetric.FRAME_TIME_P95, 18.0, 1_000L, source = SOURCE_A),
            sample(AtlasEffectMetric.FRAME_TIME_P95, 12.0, 11_000L, source = SOURCE_A),
        )
        val secondary = AtlasEffectMath.compare(
            AtlasEffectMetric.FRAME_TIME_P95,
            sample(AtlasEffectMetric.FRAME_TIME_P95, 17.5, 1_000L, source = SOURCE_B),
            sample(AtlasEffectMetric.FRAME_TIME_P95, 11.0, 11_000L, source = SOURCE_B),
        )

        val merged = AtlasEffectMath.corroborate(primary, secondary)

        assertEquals(AtlasEffectVerdict.IMPROVED, merged.verdict)
        assertEquals(AtlasEffectConfidence.CORROBORATED, merged.confidence)
        assertTrue(merged.measured)
    }

    @Test
    fun `sources contradicting each other are declared and neither is chosen silently`() {
        val primary = AtlasEffectMath.compare(
            AtlasEffectMetric.FRAME_TIME_P95,
            sample(AtlasEffectMetric.FRAME_TIME_P95, 18.0, 1_000L, source = SOURCE_A),
            sample(AtlasEffectMetric.FRAME_TIME_P95, 12.0, 11_000L, source = SOURCE_A),
        )
        val secondary = AtlasEffectMath.compare(
            AtlasEffectMetric.FRAME_TIME_P95,
            sample(AtlasEffectMetric.FRAME_TIME_P95, 12.0, 1_000L, source = SOURCE_B),
            sample(AtlasEffectMetric.FRAME_TIME_P95, 18.0, 11_000L, source = SOURCE_B),
        )

        val merged = AtlasEffectMath.corroborate(primary, secondary)

        assertEquals(AtlasEffectVerdict.UNMEASURABLE, merged.verdict)
        assertEquals(AtlasEffectReasons.CONTRADICTED, merged.reason)
        assertFalse(merged.measured)
    }

    @Test
    fun `an unmeasured secondary cannot be claimed as corroboration`() {
        val primary = AtlasEffectMath.compare(
            AtlasEffectMetric.FRAME_TIME_P95,
            sample(AtlasEffectMetric.FRAME_TIME_P95, 18.0, 1_000L, source = SOURCE_A),
            sample(AtlasEffectMetric.FRAME_TIME_P95, 12.0, 11_000L, source = SOURCE_A),
        )
        val secondary = AtlasEffectMath.compare(
            AtlasEffectMetric.FRAME_TIME_P95,
            null,
            sample(AtlasEffectMetric.FRAME_TIME_P95, 11.0, 11_000L, source = SOURCE_B),
        )

        val merged = AtlasEffectMath.corroborate(primary, secondary)

        assertEquals(AtlasEffectVerdict.IMPROVED, merged.verdict)
        assertEquals(AtlasEffectReasons.ESTIMATED_ONLY, merged.reason)
        assertEquals(AtlasEffectConfidence.MEASURED, merged.confidence)
    }

    @Test
    fun `an unmeasured primary keeps its own reason instead of being rewritten`() {
        val primary = AtlasEffectMath.compare(
            AtlasEffectMetric.FRAME_TIME_P95,
            sample(AtlasEffectMetric.FRAME_TIME_P95, 18.0, 1_000L, boot = 7L),
            sample(AtlasEffectMetric.FRAME_TIME_P95, 12.0, 11_000L, boot = 8L),
        )
        val secondary = AtlasEffectMath.compare(
            AtlasEffectMetric.FRAME_TIME_P95,
            sample(AtlasEffectMetric.FRAME_TIME_P95, 17.5, 1_000L),
            sample(AtlasEffectMetric.FRAME_TIME_P95, 11.0, 11_000L),
        )

        val merged = AtlasEffectMath.corroborate(primary, secondary)

        assertEquals(AtlasEffectReasons.BOOT_CHANGED, merged.reason)
    }

    // ── الإقامة: أين أقام المعالج فعلًا، لا آخر قيمة طُلبت ────────────────────

    @Test
    fun `residency shares and tick weighted mean come from the counter deltas`() {
        val before = "1800000 100\n2200000 50"
        val after = "1800000 300\n2200000 250\n2600000 25"

        val residency = AtlasResidencyMath.fromTimeInState(before, after)!!

        assertEquals(425L, residency.tickTotal)
        // ٢٥ تِكًّا من ٤٢٥ — والأنصبة مجموعها واحد، وهذا ما يجعلها صالحة كإقامة لا كعدّ خام.
        assertEquals(25.0 / 425.0, residency.shares.getValue(2600000L), 1e-9)
        assertEquals(1.0, residency.shares.values.sum(), 1e-9)
        // تعادلٌ في النصيب (٢٠٠ تِكّ لكل من ١٨٠٠ و٢٢٠٠): الحدّ الأقصى الأول، بحكم ترتيب القراءة.
        assertEquals(1800000L, residency.topFrequency)
        val expectedMean = (1_800_000.0 * 200 + 2_200_000.0 * 200 + 2_600_000.0 * 25) / 425.0
        assertEquals(expectedMean, residency.weightedMeanKHz!!, 0.001)
        assertEquals(AtlasUnit.KILO_HERTZ, residency.frequencyUnit)
    }

    @Test
    fun `a counter reset is dropped because a negative difference is not residency`() {
        val before = "1800000 500\n2200000 500"
        val after = "1800000 100\n2200000 700"

        val residency = AtlasResidencyMath.fromTimeInState(before, after)!!

        assertFalse(residency.shares.containsKey(1800000L))
        assertEquals(200L, residency.tickTotal)
    }

    @Test
    fun `malformed lines are skipped without discarding the whole node`() {
        val after = "garbage\n1800000\n1800000 50\nnotanumber 12"

        val residency = AtlasResidencyMath.fromTimeInState(null, after)!!

        assertEquals(50L, residency.tickTotal)
        assertEquals(1, residency.shares.size)
    }

    @Test
    fun `an unparseable or empty node yields nothing rather than zero ticks`() {
        assertNull(AtlasResidencyMath.fromTimeInState("1800000 5", "garbage"))
        assertNull(AtlasResidencyMath.fromTimeInState("1800000 5", "1800000 5"))
    }

    @Test
    fun `residency becomes an explicit effect sample and hertz is converted not guessed`() {
        val residency = AtlasResidencyMath.fromTimeInState(null, "1800000 100")!!

        val kilo = AtlasResidencyMath.toSample(residency, 5_000L, SOURCE_A, bootGeneration = 7L)!!
        val hertz = AtlasResidencyMath.fromTimeInState(null, "1800000 100", frequencyUnit = AtlasUnit.HERTZ)!!
        val hertzSample = AtlasResidencyMath.toSample(hertz, 5_000L, SOURCE_A, bootGeneration = 7L)!!

        assertEquals(1_800_000.0, kilo.value, 0.001)
        assertEquals(1_800.0, hertzSample.value, 0.001)
        assertEquals(AtlasSemanticStatus.INFERRED, kilo.semanticStatus)
        assertEquals(AtlasEffectMetric.CPU_FREQUENCY_TICK_WEIGHTED, kilo.metric)
    }

    // ── السجل: «قبل» مُعطى محفوظ لا قراءة ثانية ───────────────────────────────

    @Test
    fun `latest before selects the last sample at or before the moment and never one after it`() {
        val store = AtlasEffectStore(InMemoryStoreIo())
        listOf(1_000L, 2_000L, 3_000L).forEach { at ->
            assertTrue(
                store.record(
                    AtlasEffectRecord(
                        target = TARGET_CPU,
                        sample = sample(AtlasEffectMetric.CPU_FREQUENCY_TICK_WEIGHTED, at.toDouble(), at),
                    ),
                ),
            )
        }

        assertEquals(2_000.0, store.latestBefore(TARGET_CPU, AtlasEffectMetric.CPU_FREQUENCY_TICK_WEIGHTED, 2_500L)!!.value, 0.001)
        assertEquals(3_000.0, store.latest(TARGET_CPU, AtlasEffectMetric.CPU_FREQUENCY_TICK_WEIGHTED)!!.value, 0.001)
        assertNull(store.latestBefore(TARGET_CPU, AtlasEffectMetric.CPU_FREQUENCY_TICK_WEIGHTED, 500L))
    }

    @Test
    fun `per app samples are separated by package inside one target`() {
        val store = AtlasEffectStore(InMemoryStoreIo())
        store.record(AtlasEffectRecord(TARGET_GPU, sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 520_000.0, 1_000L), "com.example.game"))
        store.record(AtlasEffectRecord(TARGET_GPU, sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 780_000.0, 1_000L), "com.example.video"))

        assertEquals(520_000.0, store.latest(TARGET_GPU, AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, "com.example.game")!!.value, 0.001)
        assertEquals(780_000.0, store.latest(TARGET_GPU, AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, "com.example.video")!!.value, 0.001)
    }

    @Test
    fun `the ledger is pruned per metric so one busy metric cannot swallow another`() {
        val store = AtlasEffectStore(InMemoryStoreIo(), maxRecordsPerMetric = 3)
        (1..5).forEach { index ->
            store.record(AtlasEffectRecord(TARGET_CPU, sample(AtlasEffectMetric.FRAME_TIME_P95, index.toDouble(), index * 1_000L)))
        }
        store.record(AtlasEffectRecord(TARGET_CPU, sample(AtlasEffectMetric.POWER_WATTS, 4.0, 9_000L)))

        assertEquals(listOf(3.0, 4.0, 5.0), store.history(TARGET_CPU, AtlasEffectMetric.FRAME_TIME_P95).map { it.value })
        assertEquals(1, store.history(TARGET_CPU, AtlasEffectMetric.POWER_WATTS).size)
    }

    @Test
    fun `a corrupt ledger is deleted and reported not half trusted`() {
        val io = InMemoryStoreIo()
        io.write("effect.$TARGET_CPU.json", "{not json at all")

        val lookup = AtlasEffectStore(io).load(TARGET_CPU)

        assertEquals(AtlasEffectMiss.CORRUPT, (lookup as AtlasEffectLedger.Missing).reason)
        assertNull(io.read("effect.$TARGET_CPU.json"))
    }

    @Test
    fun `an oversize ledger is deleted rather than truncated into plausibility`() {
        val io = InMemoryStoreIo()
        io.write("effect.$TARGET_CPU.json", "x".repeat(200))

        val lookup = AtlasEffectStore(io, maxEntryBytes = 64).load(TARGET_CPU)

        assertEquals(AtlasEffectMiss.OVERSIZE, (lookup as AtlasEffectLedger.Missing).reason)
        assertNull(io.read("effect.$TARGET_CPU.json"))
    }

    @Test
    fun `a ledger written by a newer schema is a distinct cause from corruption`() {
        val io = InMemoryStoreIo()
        io.write("effect.$TARGET_CPU.json", """{"schema":99,"target":"$TARGET_CPU","records":[]}""")

        val lookup = AtlasEffectStore(io).load(TARGET_CPU)

        assertEquals(AtlasEffectMiss.UNSUPPORTED_SCHEMA, (lookup as AtlasEffectLedger.Missing).reason)
    }

    @Test
    fun `an absent ledger is absent and reading it writes nothing`() {
        val io = InMemoryStoreIo()
        val lookup = AtlasEffectStore(io).load(TARGET_CPU)

        assertEquals(AtlasEffectMiss.ABSENT, (lookup as AtlasEffectLedger.Missing).reason)
        assertEquals(0, io.list().size)
    }

    @Test
    fun `a record round trips with its generations and source intact`() {
        val io = InMemoryStoreIo()
        val store = AtlasEffectStore(io)
        val original = AtlasEffectRecord(
            target = TARGET_GPU,
            packageName = "com.example.game",
            sample = sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 702_000.0, 11_000L, boot = 12L, privilege = 4L),
        )

        assertTrue(store.record(original))
        val reloaded = AtlasEffectStore(io).latest(TARGET_GPU, AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, "com.example.game")!!

        assertEquals(original.sample.value, reloaded.value, 0.001)
        assertEquals(original.sample.source, reloaded.source)
        assertEquals(12L, reloaded.bootGeneration)
        assertEquals(4L, reloaded.privilegeGeneration)
        assertEquals(listOf(TARGET_GPU), AtlasEffectStore(io).targets())
    }

    // ── القبول: القرار لا يقول «نجح» بلا قياس ────────────────────────────────

    @Test
    fun `an unmeasured goal can never become keep`() {
        val goal = AtlasEffectMath.compare(
            AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED,
            null,
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 702_000.0, 11_000L),
        )

        val outcome = AtlasEffectAcceptance.decide(goal, WINDOW)

        assertEquals(AtlasEffectDecision.INCONCLUSIVE, outcome.decision)
        assertEquals(AtlasAcceptanceReason.GOAL_UNMEASURED, outcome.reason)
        assertFalse(AtlasEffectAcceptance.requiresWrite(outcome.decision))
    }

    @Test
    fun `a window shorter than the settling time invalidates even a strong delta`() {
        val goal = AtlasEffectMath.compare(
            AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED,
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 520_000.0, 0L),
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 1_300_000.0, 500L),
        )

        val outcome = AtlasEffectAcceptance.decide(goal, AtlasEffectWindow(0L, 500L))

        assertEquals(AtlasEffectDecision.INCONCLUSIVE, outcome.decision)
        assertEquals(AtlasAcceptanceReason.WINDOW_UNUSABLE, outcome.reason)
    }

    @Test
    fun `a worsened goal is the only decision that writes and it writes a revert`() {
        val goal = AtlasEffectMath.compare(
            AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED,
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 1_300_000.0, 0L),
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 520_000.0, 11_000L),
        )

        val outcome = AtlasEffectAcceptance.decide(goal, WINDOW)

        assertEquals(AtlasEffectDecision.REVERT, outcome.decision)
        assertEquals(AtlasAcceptanceReason.GOAL_WORSENED, outcome.reason)
        assertTrue(AtlasEffectAcceptance.requiresWrite(outcome.decision))
    }

    @Test
    fun `an improved goal with an unchanged cost is kept`() {
        val goal = AtlasEffectMath.compare(
            AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED,
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 520_000.0, 0L),
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 702_000.0, 11_000L),
        )
        val cost = AtlasEffectMath.compare(
            AtlasEffectMetric.POWER_WATTS,
            sample(AtlasEffectMetric.POWER_WATTS, 4.0, 0L),
            sample(AtlasEffectMetric.POWER_WATTS, 4.1, 11_000L),
        )

        val outcome = AtlasEffectAcceptance.decide(goal, WINDOW, cost = cost)

        assertEquals(AtlasEffectDecision.KEEP, outcome.decision)
        assertEquals(AtlasAcceptanceReason.GOAL_IMPROVED_COST_OK, outcome.reason)
    }

    @Test
    fun `an improved goal that cost more is named as a trade not as a plain success`() {
        val goal = AtlasEffectMath.compare(
            AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED,
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 520_000.0, 0L),
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 702_000.0, 11_000L),
        )
        val cost = AtlasEffectMath.compare(
            AtlasEffectMetric.POWER_WATTS,
            sample(AtlasEffectMetric.POWER_WATTS, 4.0, 0L),
            sample(AtlasEffectMetric.POWER_WATTS, 6.0, 11_000L),
        )

        val outcome = AtlasEffectAcceptance.decide(goal, WINDOW, cost = cost)

        assertEquals(AtlasEffectDecision.KEEP, outcome.decision)
        assertEquals(AtlasAcceptanceReason.GOAL_IMPROVED_COST_WORSENED, outcome.reason)
    }

    @Test
    fun `cost paid without any measured gain is reverted`() {
        val goal = AtlasEffectMath.compare(
            AtlasEffectMetric.FPS_MEAN,
            sample(AtlasEffectMetric.FPS_MEAN, 60.0, 0L),
            sample(AtlasEffectMetric.FPS_MEAN, 60.1, 11_000L),
        )
        val cost = AtlasEffectMath.compare(
            AtlasEffectMetric.POWER_WATTS,
            sample(AtlasEffectMetric.POWER_WATTS, 4.0, 0L),
            sample(AtlasEffectMetric.POWER_WATTS, 5.5, 11_000L),
        )

        val outcome = AtlasEffectAcceptance.decide(goal, WINDOW, cost = cost)

        assertEquals(AtlasEffectDecision.REVERT, outcome.decision)
        assertEquals(AtlasAcceptanceReason.COST_PAID_WITHOUT_GAIN, outcome.reason)
    }

    @Test
    fun `no measured change and no measured cost stays inconclusive and claims nothing`() {
        val goal = AtlasEffectMath.compare(
            AtlasEffectMetric.FPS_MEAN,
            sample(AtlasEffectMetric.FPS_MEAN, 60.0, 0L),
            sample(AtlasEffectMetric.FPS_MEAN, 60.1, 11_000L),
        )

        val outcome = AtlasEffectAcceptance.decide(goal, WINDOW)

        assertEquals(AtlasEffectDecision.INCONCLUSIVE, outcome.decision)
        assertEquals(AtlasAcceptanceReason.NO_CHANGE_NO_COST, outcome.reason)
    }

    @Test
    fun `the described decision carries stable tokens and real numbers only`() {
        val goal = AtlasEffectMath.compare(
            AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED,
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 520_000.0, 0L),
            sample(AtlasEffectMetric.GPU_FREQUENCY_TICK_WEIGHTED, 702_000.0, 11_000L),
        )

        val description = AtlasEffectAcceptance.describe(AtlasEffectAcceptance.decide(goal, WINDOW))

        assertTrue(description.contains("decision=keep"))
        assertTrue(description.contains("reason=goal-improved-cost-ok"))
        assertTrue(description.contains("before=520000"))
        assertTrue(description.contains("after=702000"))
        assertTrue(description.contains("source=$SOURCE_A"))
    }

    // ── أدوات الاختبار ───────────────────────────────────────────────────────

    private fun sample(
        metric: AtlasEffectMetric,
        value: Double,
        atMs: Long,
        source: String = SOURCE_A,
        boot: Long = 7L,
        privilege: Long = 3L,
    ) = AtlasEffectSample(
        metric = metric,
        value = value,
        observedAtElapsedMs = atMs,
        source = source,
        bootGeneration = boot,
        privilegeGeneration = privilege,
    )

    private class InMemoryStoreIo : AtlasStoreIo {
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
        const val TARGET_CPU = "cpu.ceiling"
        const val TARGET_GPU = "gpu.ceiling"
        const val SOURCE_A = "sys/class/devfreq/13000000.mali/max_freq"
        const val SOURCE_B = "stats/time_in_state"
        val WINDOW = AtlasEffectWindow(0L, 11_000L)
    }
}
