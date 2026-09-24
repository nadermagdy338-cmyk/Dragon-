package nd.max.core.hardware

import nd.max.core.atlas.AtlasControlGoal
import nd.max.core.atlas.AtlasControlIntent
import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasControlTransport
import nd.max.core.atlas.AtlasCpuFrequencySource
import nd.max.core.atlas.AtlasCpuPolicyFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * طبقة المواءمة (Adapt) — الاختيار حتميّ، والغياب فجوة مُسمّاة، والعقدة الممنوعة لا تُبنى أبدًا.
 *
 * وهذه هي نقطة التوسعة نفسها التي طلبها المالك: «يمكن إضافة دعم لجهاز أو Kernel جديد دون إعادة
 * كتابة قلب Max Atlas» — يُسجَّل مُلاءِم جديد هنا، ولا يُمسّ القلب. فيُقاس هنا العقد لا الأسماء.
 */
class AtlasAdapterRegistryTest {

    private val context = AtlasAdapterContext(
        privileged = true,
        cpuFacts = listOf(fact()),
        ceilingAccess = FakeCeilingAccess(),
    )

    @Test
    fun `selection is deterministic - transport order first, then id`() {
        val platform = FakeAdapter("b-platform", AtlasControlTransport.PLATFORM_HINT)
        val sysfs = FakeAdapter("a-sysfs", AtlasControlTransport.ARBITER_SYSFS)
        val first = AtlasAdapterRegistry(listOf(sysfs, platform)).choose(AtlasControlTarget.CPU_FREQUENCY, context, null)
        val second = AtlasAdapterRegistry(listOf(platform, sysfs)).choose(AtlasControlTarget.CPU_FREQUENCY, context, null)

        assertEquals("b-platform", (first as AtlasAdapterChoice.Chosen).adapter.id)
        // الترتيب يُنتج القرار نفسه مهما كان ترتيب التسجيل — لا اختياران على جهاز واحد.
        assertEquals((first as AtlasAdapterChoice.Chosen).adapter.id, (second as AtlasAdapterChoice.Chosen).adapter.id)
    }

    @Test
    fun `a gap names every rejection instead of staying silent`() {
        val choice = AtlasAdapterRegistry(listOf(FakeAdapter("vendor-x", applicable = false)))
            .choose(AtlasControlTarget.CPU_FREQUENCY, context, null)

        val gap = choice as AtlasAdapterChoice.Gap
        assertEquals(listOf("vendor-x" to "no-vendor-bridge-on-this-device"), gap.rejections)
    }

    @Test
    fun `duplicate adapter ids are a wiring bug, not a registry`() {
        try {
            AtlasAdapterRegistry(listOf(FakeAdapter("same"), FakeAdapter("same")))
            throw AssertionError("two adapters may not share one id")
        } catch (expected: IllegalArgumentException) {
            // الفشل المغلق في البناء نفسه: لا اختيار غامض وقت التشغيل.
        }
    }

    @Test
    fun `the cpu ceiling adapter refuses when no policy was seen`() {
        val assessment = CpuCeilingAdapter().assess(
            AtlasAdapterContext(privileged = true, ceilingAccess = FakeCeilingAccess()),
            null,
        )
        assertEquals(AtlasAdapterAssessment.NotApplicable("no-cpufreq-policy-seen"), assessment)
    }

    @Test
    fun `a policy on a denied node is skipped by rule and never bound`() {
        val denied = AtlasAdapterContext(
            privileged = true,
            cpuFacts = listOf(fact(path = "/sys/class/thermal/thermal_zone0/trip_point_0_temp")),
            ceilingAccess = FakeCeilingAccess(),
        )
        val plan = CpuCeilingAdapter().plan(denied, ceilings())

        assertEquals(0, plan.bindings.size)
        assertEquals(
            listOf("cpu_limits:policy0" to "never-touch:thermal-trips"),
            plan.skipped,
        )
    }

    @Test
    fun `a probe builds the representative route and writes nothing`() {
        val access = FakeCeilingAccess()
        access.nodes[POLICY_PATH] = "$LIVE_MIN:$PROVEN_MAX"
        val plan = CpuCeilingAdapter().probe(
            AtlasAdapterContext(privileged = true, cpuFacts = listOf(fact()), ceilingAccess = access),
        )

        assertEquals(1, plan.bindings.size)
        assertEquals("cpu-limits-policy0", plan.bindings.single().candidate.id)
        assertEquals("خط الأساس يُقرأ تمثيلًا، ولا يُكتب شيء", 0, access.writes)
    }

    @Test
    fun `a fact with no published ladder is skipped with its named reason`() {
        val access = FakeCeilingAccess()
        access.nodes[POLICY_PATH] = "$LIVE_MIN:$PROVEN_MAX"
        val plan = CpuCeilingAdapter().plan(
            AtlasAdapterContext(privileged = true, cpuFacts = listOf(fact(ladder = emptyList())), ceilingAccess = access),
            ceilings(),
        )

        assertEquals(0, plan.bindings.size)
        assertEquals(listOf("cpu_limits:policy0" to "no-proven-range"), plan.skipped)
    }

    @Test
    fun `without a write seam no write route is built`() {
        val plan = CpuCeilingAdapter().plan(
            AtlasAdapterContext(privileged = true, cpuFacts = listOf(fact()), ceilingAccess = null),
            ceilings(),
        )
        assertEquals(0, plan.bindings.size)
        assertEquals(listOf("cpu-ceiling" to "no-write-seam-available"), plan.skipped)
    }

    private fun ceilings() = AtlasControlRequest.CpuCeilings(
        intent = AtlasControlIntent(
            target = AtlasControlTarget.CPU_FREQUENCY,
            goal = AtlasControlGoal.SUSTAINED_PERFORMANCE,
        ),
        owner = ControlOwnership.Owner.MAX_AI,
        token = "adapter-test",
        ceilingKHzByKnob = mapOf("policy0" to 1_000_000L),
        reviewed = { true },
    )

    private fun fact(path: String = POLICY_PATH, ladder: List<Long> = LADDER): AtlasCpuPolicyFact =
        AtlasCpuPolicyFact(
            name = "policy0",
            path = path,
            governor = "schedutil",
            governors = listOf("schedutil"),
            minKHz = LIVE_MIN,
            maxKHz = PROVEN_MAX,
            provenMinKHz = LIVE_MIN,
            provenMaxKHz = PROVEN_MAX,
            boundsDeclaredByKernel = true,
            frequencyLadderKHz = ladder,
            currentKHz = PROVEN_MAX,
            currentSource = AtlasCpuFrequencySource.SCALING_CUR_FREQ,
        )

    private class FakeAdapter(
        override val id: String,
        override val transport: AtlasControlTransport = AtlasControlTransport.ARBITER_SYSFS,
        private val applicable: Boolean = true,
    ) : AtlasControlAdapter {
        override val target: AtlasControlTarget = AtlasControlTarget.CPU_FREQUENCY

        override fun assess(context: AtlasAdapterContext, request: AtlasControlRequest?): AtlasAdapterAssessment =
            if (applicable) {
                AtlasAdapterAssessment.Applicable(id, "synthetic")
            } else {
                AtlasAdapterAssessment.NotApplicable("no-vendor-bridge-on-this-device")
            }

        override fun plan(context: AtlasAdapterContext, request: AtlasControlRequest): AtlasAdapterPlan =
            AtlasAdapterPlan(id, emptyList(), emptyList())

        override fun probe(context: AtlasAdapterContext): AtlasAdapterPlan =
            AtlasAdapterPlan(id, emptyList(), emptyList())
    }

    private class FakeCeilingAccess : AtlasCeilingAccess {
        override var privileged: Boolean = true
        val nodes: MutableMap<String, String> = linkedMapOf()
        var writes: Int = 0

        override fun readLimits(policyPath: String): String? = nodes[policyPath]

        override fun writeLimits(policyPath: String, range: String): Boolean {
            writes += 1
            nodes[policyPath] = range
            return true
        }
    }

    private companion object {
        const val POLICY_PATH = "/sys/devices/system/cpu/cpufreq/policy0"
        const val LIVE_MIN = 300_000L
        const val PROVEN_MAX = 2_000_000L
        val LADDER = listOf(LIVE_MIN, 1_000_000L, PROVEN_MAX)
    }
}
