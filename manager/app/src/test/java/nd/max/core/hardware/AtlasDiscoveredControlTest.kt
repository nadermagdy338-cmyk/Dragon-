package nd.max.core.hardware

import nd.max.core.atlas.AtlasCpuFrequencySource
import nd.max.core.atlas.AtlasCpuPolicyFact
import nd.max.core.atlas.AtlasRouteReason
import nd.max.core.atlas.AtlasRouteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * قياس الجسر الذي يجعل أطلس يكتب من دليله: كل حقل في أدلّة المسار يجب أن يكون **مشتقًّا**،
 * وكل كتابة بلا إثبات يجب أن تُرفض بـ**رمز المخطِّط القياسي** مع صفر كتابات على العقدة.
 *
 * والأرقام مختارة عن قصد: العقدة الحيّة على **أعلى** السلّم (`$LIVE_MAX`)، والطلب أقلّ منها،
 * فيقع فعل الكتابة فعلًا. ولو طلبنا سقفًا مُلبّى أصلًا لما كُتب شيء — وهذا سلوك مقصود يُثبته
 * اختبار مستقل ([`a ceiling the device already holds is reported without a write`]).
 */
class AtlasDiscoveredControlTest {

    private lateinit var access: FakeCeilingAccess

    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("atlas-discovered-control-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }
        access = FakeCeilingAccess()
        access.nodes[POLICY_PATH] = "$LIVE_MIN:$LIVE_MAX"
    }

    @Test
    fun `a discovered policy yields one route with measured evidence`() {
        val plan = control().cpuCeilingPlan(listOf(fact()), ceilingKHz = { REQUESTED }, reviewed = { true })

        val binding = plan.bindings.single()
        val evidence = binding.candidate.evidence
        assertEquals(HardwareControlKey.cpuLimits("policy0"), binding.request.key)
        assertEquals(HardwareRepairExecutor.labelFor(binding.request.key), binding.candidate.id)
        assertTrue(evidence.readable)
        assertTrue(evidence.unitProven)
        assertTrue(evidence.baselineReadable)
        assertTrue(evidence.rollbackProven)
        assertTrue(evidence.reviewed)
        assertTrue(evidence.reason.contains("ladder=3"))
        assertTrue(evidence.reason.contains("kernelBounds=true"))
    }

    @Test
    fun `the same knob keeps one canonical key and never grows a second vocabulary`() {
        val binding = control()
            .cpuCeilingPlan(listOf(fact()), ceilingKHz = { REQUESTED }, reviewed = { true })
            .bindings
            .single()

        assertEquals("cpu_limits:policy0", binding.request.key)
        assertEquals("cpu-limits-policy0", binding.candidate.id)
        assertEquals(binding.request.key, HardwareControlKey.cpuLimits("policy0"))
    }

    @Test
    fun `an unreadable ceiling node is refused by the planner and nothing is written`() {
        access.nodes.clear()
        val result = control().applyCpuCeiling(
            executor = executor(),
            facts = listOf(fact()),
            ceilingKHz = { REQUESTED },
            reviewed = { true },
        )

        assertFalse(result.verified)
        assertEquals(AtlasRouteStatus.BLOCKED, result.adaptive?.decision?.status)
        assertEquals(AtlasRouteReason.BASELINE_UNREADABLE, result.adaptive?.decision?.reason)
        assertEquals(0, access.writes)
    }

    @Test
    fun `an unreviewed node directory is never written`() {
        val result = control().applyCpuCeiling(
            executor = executor(),
            facts = listOf(fact()),
            ceilingKHz = { REQUESTED },
            reviewed = { false },
        )

        assertFalse(result.verified)
        assertEquals(AtlasRouteStatus.REVIEW_REQUIRED, result.adaptive?.decision?.status)
        assertEquals(AtlasRouteReason.ROUTE_NOT_REVIEWED, result.adaptive?.decision?.reason)
        assertEquals(0, access.writes)
        assertEquals("$LIVE_MIN:$LIVE_MAX", access.nodes[POLICY_PATH])
    }

    @Test
    fun `a policy without kernel declared bounds is refused as an ambiguous unit`() {
        val result = control().applyCpuCeiling(
            executor = executor(),
            facts = listOf(fact(boundsDeclaredByKernel = false)),
            ceilingKHz = { REQUESTED },
            reviewed = { true },
        )

        assertEquals(AtlasRouteStatus.BLOCKED, result.adaptive?.decision?.status)
        assertEquals(AtlasRouteReason.UNIT_AMBIGUOUS, result.adaptive?.decision?.reason)
        assertEquals(0, access.writes)
    }

    @Test
    fun `without the verified writer the route is refused and nothing is written`() {
        access.privileged = false
        val result = control().applyCpuCeiling(
            executor = executor(),
            facts = listOf(fact()),
            ceilingKHz = { REQUESTED },
            reviewed = { true },
        )

        assertEquals(AtlasRouteStatus.BLOCKED, result.adaptive?.decision?.status)
        assertEquals(AtlasRouteReason.PRIVILEGE_UNAVAILABLE, result.adaptive?.decision?.reason)
        assertEquals(0, access.writes)
    }

    @Test
    fun `a verified ceiling reaches the node and reports the applied range`() {
        val result = control().applyCpuCeiling(
            executor = executor(),
            facts = listOf(fact()),
            ceilingKHz = { REQUESTED },
            reviewed = { true },
        )

        assertTrue(result.verified)
        assertEquals(1, access.writes)
        assertEquals("cpu-limits-policy0", result.selectedRouteId)
        assertEquals("$LIVE_MIN:$REQUESTED", access.nodes[POLICY_PATH])
    }

    @Test
    fun `a ceiling the device already holds is reported without a write`() {
        val result = control().applyCpuCeiling(
            executor = executor(),
            facts = listOf(fact()),
            ceilingKHz = { LIVE_MAX },
            reviewed = { true },
        )

        assertTrue(result.verified)
        assertEquals(0, access.writes)
        assertEquals("$LIVE_MIN:$LIVE_MAX", access.nodes[POLICY_PATH])
    }

    @Test
    fun `an external writer inside the confirmation window restores the baseline`() {
        access.takeBackAtRead = 5
        val result = control().applyCpuCeiling(
            executor = executor(),
            facts = listOf(fact()),
            ceilingKHz = { REQUESTED },
            reviewed = { true },
        )

        assertFalse(result.verified)
        assertEquals(HardwareRepairState.DRIFT_ROLLED_BACK, result.adaptive?.attempts?.last()?.state)
        assertTrue(result.adaptive?.attempts?.last()?.rollbackVerified == true)
        assertEquals("$LIVE_MIN:$LIVE_MAX", access.nodes[POLICY_PATH])
    }

    @Test
    fun `a ceiling above the proven maximum is snapped to the announced ladder, never invented`() {
        val binding = control()
            .cpuCeilingPlan(listOf(fact()), ceilingKHz = { 9_000_000L }, reviewed = { true })
            .bindings
            .single()

        assertEquals("$LIVE_MIN:$PROVEN_MAX", binding.request.desired)
        assertTrue(binding.request.desired.split(':')[1].toLong() <= PROVEN_MAX)
    }

    @Test
    fun `a policy with no requested ceiling produces no route and says why`() {
        val plan = control().cpuCeilingPlan(listOf(fact()), ceilingKHz = { null }, reviewed = { true })

        assertTrue(plan.bindings.isEmpty())
        assertEquals(
            listOf(HardwareControlKey.cpuLimits("policy0") to AtlasDiscoveredControl.SKIP_NO_REQUEST),
            plan.skipped,
        )
    }

    @Test
    fun `a policy that announced no ladder is skipped rather than guessed at`() {
        val plan = control().cpuCeilingPlan(
            listOf(fact(ladder = emptyList())),
            ceilingKHz = { REQUESTED },
            reviewed = { true },
        )

        assertTrue(plan.bindings.isEmpty())
        assertEquals(
            listOf(HardwareControlKey.cpuLimits("policy0") to AtlasDiscoveredControl.SKIP_NO_PROVEN_RANGE),
            plan.skipped,
        )
    }

    @Test
    fun `a manual lock on the same knob blocks the automated route`() {
        ManualControlLocks.lock(
            key = HardwareControlKey.cpuLimits("policy0"),
            token = "user",
            desired = "$LIVE_MIN:$LIVE_MAX",
            baseline = "$LIVE_MIN:$LIVE_MAX",
        )
        val result = control().applyCpuCeiling(
            executor = executor(),
            facts = listOf(fact()),
            ceilingKHz = { REQUESTED },
            reviewed = { true },
        )

        assertFalse(result.verified)
        assertEquals(HardwareRepairState.BLOCKED, result.adaptive?.attempts?.last()?.state)
        assertEquals(0, access.writes)
        assertNull(ControlOwnership.winner(HardwareControlKey.cpuLimits("policy0")))
    }

    private fun executor(): AtlasAdaptiveExecutor =
        AtlasAdaptiveExecutor(HardwareRepairExecutor(HardwareControlArbiter(), sleep = {}))

    private fun control(): AtlasDiscoveredControl =
        AtlasDiscoveredControl(
            access = access,
            token = "atlas-test",
            confirmationSamples = 3,
            confirmationIntervalMs = 0L,
        )

    private fun fact(
        boundsDeclaredByKernel: Boolean = true,
        ladder: List<Long> = LADDER,
    ): AtlasCpuPolicyFact = AtlasCpuPolicyFact(
        name = "policy0",
        path = POLICY_PATH,
        governor = "schedutil",
        governors = listOf("schedutil", "performance"),
        minKHz = LIVE_MIN,
        maxKHz = LIVE_MAX,
        provenMinKHz = LIVE_MIN,
        provenMaxKHz = PROVEN_MAX,
        boundsDeclaredByKernel = boundsDeclaredByKernel,
        frequencyLadderKHz = ladder,
        currentKHz = LIVE_MAX,
        currentSource = AtlasCpuFrequencySource.SCALING_CUR_FREQ,
    )

    /** وصول وهمي يقيس **القراءات والكتابات** بدل أن يدّعيها — وهو ما تُبنى عليه كل مطالبة هنا. */
    private class FakeCeilingAccess(
        override var privileged: Boolean = true,
    ) : AtlasCeilingAccess {

        val nodes: MutableMap<String, String> = linkedMapOf()
        var writes: Int = 0
        private var reads: Int = 0

        /** رقم القراءة التي يفوز فيها كاتب خارجي (محاكاة خصم داخل نافذة التأكيد). */
        var takeBackAtRead: Int? = null

        override fun readLimits(policyPath: String): String? {
            reads += 1
            val takeBack = takeBackAtRead
            if (takeBack != null && reads == takeBack && writes > 0) {
                nodes[policyPath] = VENDOR_VALUE
                takeBackAtRead = null
            }
            return nodes[policyPath]
        }

        override fun writeLimits(policyPath: String, range: String): Boolean {
            writes += 1
            nodes[policyPath] = range
            return true
        }
    }

    private companion object {
        const val POLICY_PATH = "/sys/devices/system/cpu/cpufreq/policy0"
        const val LIVE_MIN = 300_000L
        const val LIVE_MAX = 2_000_000L
        const val PROVEN_MAX = 2_000_000L

        /** طلبٌ **أدنى** من الحدّ الحيّ، وإلا لكان مُلبّى أصلًا فلم تُكتب عقدة. */
        const val REQUESTED = 1_000_000L
        /**
         * قيمة الكاتب الخارجي **فوق** السقف المطلوب عن قصد: الانحراف المقصود هو أن يرفع مُلطِّف
         * سقفنا من أعلى. وقيمةٌ داخل المدى تُقرأ «مُلبّاة» بحكم السقف نفسه (`rangeContained`)
         * فلا يُكتشف انحراف أصلًا — وهذا صحيح: المدى ليس «ساوِ» بل «ابقَ داخله».
         */
        const val VENDOR_VALUE = "300000:2500000"
        val LADDER = listOf(LIVE_MIN, REQUESTED, PROVEN_MAX)
    }
}
