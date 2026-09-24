package nd.max.core.atlas

import nd.max.core.hardware.AtlasAdaptiveExecutor
import nd.max.core.hardware.AtlasAdapterAssessment
import nd.max.core.hardware.AtlasAdapterChoice
import nd.max.core.hardware.AtlasAdapterContext
import nd.max.core.hardware.AtlasAdapterPlan
import nd.max.core.hardware.AtlasAdapterRegistry
import nd.max.core.hardware.AtlasCeilingAccess
import nd.max.core.hardware.AtlasControlAdapter
import nd.max.core.hardware.AtlasControlRequest
import nd.max.core.hardware.AtlasRouteBinding
import nd.max.core.hardware.AtlasRouteMemory
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.HardwareRepairExecutor
import nd.max.core.hardware.HardwareRepairRequest
import nd.max.core.hardware.ManualControlLocks
import nd.max.core.hardware.SharedHardwareOwnershipStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * الدورة كاملة على جهاز مزيّف يُقاس: Discover/Map عبر التمثيل، وAdapt/Execute/Verify/Learn عبر
 * المعاملة الحقيقية (مُحكِّم + مُنفِّذ معاملة + ذاكرة تعلّم).
 *
 * الأحكام المثبَّتة هنا هي أحكام طلب المالك نفسها: **لا نجاح بلا قراءة تُثبته**، وقاعدة عدم اللمس
 * فوق الجميع (ترفض قبل أي كتابة)، وغياب الملاءِم فجوة مُعلنة لا صمت، وخريطة التمثيل **لا تكتب**،
 * والنجاح لا يظهر في الخريطة إلا خلف تحقق مُثبت.
 */
class MaxAtlasTest {

    private lateinit var access: FakeCeilingAccess
    private lateinit var arbiter: HardwareControlArbiter
    private lateinit var memory: AtlasRouteMemory

    @Before
    fun configureControlPlane() {
        val root = Files.createTempDirectory("max-atlas-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }

        access = FakeCeilingAccess()
        access.nodes[POLICY_PATH] = "$LIVE_MIN:$PROVEN_MAX"
        arbiter = HardwareControlArbiter()
        memory = AtlasRouteMemory(
            io = InMemoryStoreIo(),
            clockMs = { 0L },
            bootGeneration = { 1L },
            privilegeGeneration = { 0L },
        )
    }

    private fun maxAtlas(registry: AtlasAdapterRegistry = AtlasAdapterRegistry.defaults()): MaxAtlas = MaxAtlas(
        registry = registry,
        executor = AtlasAdaptiveExecutor(HardwareRepairExecutor(arbiter, sleep = {}), memory = memory),
        memory = memory,
        identity = AtlasDeviceIdentity(supportedAbis = listOf("arm64-v8a"), apiLevel = 34),
        catalogVersion = "test-catalog",
        clockMs = { 100L },
        bootGeneration = { 1L },
        privilegeGeneration = { 0L },
    )

    private fun context(facts: List<AtlasCpuPolicyFact> = listOf(fact())) = AtlasAdapterContext(
        privileged = true,
        cpuFacts = facts,
        ceilingAccess = access,
    )

    private fun ceilings(
        target: AtlasControlTarget = AtlasControlTarget.CPU_FREQUENCY,
        ceilings: Map<String, Long?> = mapOf("policy0" to HALF),
        reviewed: (String) -> Boolean = { true },
    ) = AtlasControlRequest.CpuCeilings(
        intent = AtlasControlIntent(target = target, goal = AtlasControlGoal.SUSTAINED_PERFORMANCE),
        owner = ControlOwnership.Owner.MAX_AI,
        token = TOKEN,
        ceilingKHzByKnob = ceilings,
        reviewed = reviewed,
    )

    @Test
    fun `thermal monitoring reads as read-only and silence reads as unknown`() {
        val readable = maxAtlas().profile(
            AtlasAdapterContext(privileged = true, thermalStatusReadable = true),
            listOf(AtlasControlTarget.THERMAL_PROFILE),
        ).capabilities.forTarget(AtlasControlTarget.THERMAL_PROFILE)
        // مراقبة تُقرأ ولا تكتب: «للقراءة فقط» هي الحقيقة — لا «يحتاج مُلاءِمًا» يَعِد بمسار لا يحتاجه أحد.
        assertEquals(AtlasCapabilityState.READ_ONLY, readable?.state)

        val silent = maxAtlas().profile(
            AtlasAdapterContext(privileged = true),
            listOf(AtlasControlTarget.THERMAL_PROFILE),
        ).capabilities.forTarget(AtlasControlTarget.THERMAL_PROFILE)
        // وما لم يُقَس يبقى مجهولًا — لا «مدعوم» ولا «غير متاح».
        assertEquals(AtlasCapabilityState.UNKNOWN, silent?.state)
    }

    @Test
    fun `a governor the build writes is a missing method, never read-only`() {
        val capability = maxAtlas().profile(
            context(),
            listOf(AtlasControlTarget.CPU_GOVERNOR),
        ).capabilities.forTarget(AtlasControlTarget.CPU_GOVERNOR)

        // الكتابة قائمة خارج السجل (`setGovernor`) — فالخريطة تدعوه لإضافة طريقة ولا تَعِد بعدمها.
        assertEquals(AtlasCapabilityState.NEEDS_ADAPTER, capability?.state)
    }

    @Test
    fun `a write proven by read-back is the only verdict that claims success`() {
        val report = maxAtlas().execute(context(), ceilings())

        assertEquals(AtlasExecutionVerdict.VERIFIED, report.verdict)
        assertEquals("cpu-limits-policy0", report.selectedRouteId)
        assertEquals("$LIVE_MIN:$HALF", access.nodes[POLICY_PATH])
        // النجاح يظهر في الخريطة خلف التحقق وحده.
        assertEquals(AtlasCapabilityState.SUPPORTED, report.capability.state)
        // والتعلّم وقع فعلًا: المسار محفوظ كمُثبَت على هذا الجهاز في هذا الجيل.
        assertEquals("cpu-limits-policy0", memory.preferredRoute("cpu_frequency"))
    }

    @Test
    fun `a writer that refuses is never reported as success`() {
        access.refuseWrites = true
        val report = maxAtlas().execute(context(), ceilings())

        assertNotEquals(AtlasExecutionVerdict.VERIFIED, report.verdict)
        assertEquals(AtlasExecutionVerdict.STATE_UNKNOWN, report.verdict)
        assertFalse(report.verified)
        assertNotEquals(AtlasCapabilityState.SUPPORTED, report.capability.state)
        assertEquals(null, memory.preferredRoute("cpu_frequency"))
    }

    @Test
    fun `an unreviewed route is refused by the planner and nothing is written`() {
        val report = maxAtlas().execute(context(), ceilings(reviewed = { false }))

        assertEquals(AtlasExecutionVerdict.BLOCKED, report.verdict)
        assertEquals(0, access.writes)
        assertEquals(AtlasCapabilityState.READ_ONLY, report.capability.state)
    }

    @Test
    fun `a denied interface is refused before any write, whatever route asks`() {
        val adapter = DeniedAdapter()
        val report = maxAtlas(AtlasAdapterRegistry(listOf(adapter)))
            .execute(context(), ceilings(target = AtlasControlTarget.THERMAL_PROFILE))

        assertEquals(AtlasExecutionVerdict.REFUSED_SAFETY, report.verdict)
        assertEquals("never-touch:thermal-trips", report.refusalCode)
        assertEquals("خطة تُبنى ثم تُمنع — ولا يُستدعى فعل الكتابة أبدًا", 0, adapter.applyCalls)
        assertEquals(AtlasCapabilityState.NEVER_TOUCH, report.capability.state)
    }

    @Test
    fun `no adapter is an honest gap naming the need for a way, not a silent failure`() {
        val report = maxAtlas(AtlasAdapterRegistry(emptyList())).execute(context(), ceilings())

        assertEquals(AtlasExecutionVerdict.NO_ADAPTER, report.verdict)
        assertEquals(AtlasCapabilityState.NEEDS_ADAPTER, report.capability.state)
        assertEquals(0, access.writes)
    }

    @Test
    fun `the capability map claims writable before any proven write and supported after one`() {
        val atlas = maxAtlas()
        assertEquals(
            AtlasCapabilityState.WRITABLE,
            atlas.profile(context()).capabilities.forTarget(AtlasControlTarget.CPU_FREQUENCY)?.state,
        )

        atlas.execute(context(), ceilings())
        assertEquals(
            AtlasCapabilityState.SUPPORTED,
            atlas.profile(context()).capabilities.forTarget(AtlasControlTarget.CPU_FREQUENCY)?.state,
        )
    }

    @Test
    fun `a capability probe builds routes and writes nothing`() {
        val before = access.writes
        val profile = maxAtlas().profile(context())

        assertEquals(before, access.writes)
        assertTrue(profile.capabilities.forTarget(AtlasControlTarget.CPU_FREQUENCY)?.state != AtlasCapabilityState.UNKNOWN)
        assertTrue(profile.learnedRoutes.isEmpty())
    }

    @Test
    fun `the registry gap and the chosen adapter stay distinguishable`() {
        val chosen = AtlasAdapterRegistry.defaults().choose(AtlasControlTarget.CPU_FREQUENCY, context(), ceilings())
        assertTrue(chosen is AtlasAdapterChoice.Chosen)
        val gap = AtlasAdapterRegistry.defaults().choose(AtlasControlTarget.CPU_FREQUENCY, context(emptyList()), ceilings())
        assertTrue(gap is AtlasAdapterChoice.Gap)
    }

    private fun fact(ladder: List<Long> = LADDER): AtlasCpuPolicyFact = AtlasCpuPolicyFact(
        name = "policy0",
        path = POLICY_PATH,
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

    /** وصول وهمي يقيس القراءات والكتابات بدل أن يدّعيها. */
    private class FakeCeilingAccess : AtlasCeilingAccess {
        override var privileged: Boolean = true
        val nodes: MutableMap<String, String> = linkedMapOf()
        var writes: Int = 0
        var refuseWrites: Boolean = false

        override fun readLimits(policyPath: String): String? = nodes[policyPath]

        override fun writeLimits(policyPath: String, range: String): Boolean {
            writes += 1
            if (refuseWrites) return false
            nodes[policyPath] = range
            return true
        }
    }

    /** مخزن بلا قرص: التعلّم يُختبَر بلا ملفات. */
    private class InMemoryStoreIo : nd.max.core.atlas.AtlasStoreIo {
        private val entries = linkedMapOf<String, String>()
        override fun read(name: String): String? = entries[name]
        override fun write(name: String, text: String): Boolean {
            entries[name] = text
            return true
        }

        override fun delete(name: String): Boolean = entries.remove(name) != null
        override fun list(): List<String> = entries.keys.toList()
    }

    /**
     * مُلاءِم مزيّف يبني مسارًا على عقدة **ممنوعة** (`thermal_trip_points`) — والمقاس هو أن الحارس
     * يرفضه قبل استدعاء فعل الكتابة. (و`CpuCeilings` هنا وسيلة نقل نيّة؛ المُلاءِم لا يقرأ صيغتها.)
     */
    private class DeniedAdapter : AtlasControlAdapter {
        override val id: String = "denied-surface"
        override val target: AtlasControlTarget = AtlasControlTarget.THERMAL_PROFILE
        override val transport: AtlasControlTransport = AtlasControlTransport.ARBITER_SYSFS
        var applyCalls: Int = 0

        override fun assess(context: AtlasAdapterContext, request: AtlasControlRequest?): AtlasAdapterAssessment =
            AtlasAdapterAssessment.Applicable(id, "synthetic")

        override fun plan(context: AtlasAdapterContext, request: AtlasControlRequest): AtlasAdapterPlan =
            AtlasAdapterPlan(id, listOf(deniedBinding()), emptyList())

        override fun probe(context: AtlasAdapterContext): AtlasAdapterPlan =
            AtlasAdapterPlan(id, listOf(deniedBinding()), emptyList())

        private fun deniedBinding(): AtlasRouteBinding = AtlasRouteBinding(
            candidate = AtlasRouteCandidate(
                id = "denied-trip-route",
                evidence = AtlasRouteEvidence(
                    providerId = "synthetic",
                    transport = AtlasControlTransport.ARBITER_SYSFS,
                    target = AtlasControlTarget.THERMAL_PROFILE,
                    readable = true,
                    privilegeAvailable = true,
                    unitProven = true,
                    baselineReadable = true,
                    rollbackProven = true,
                    reviewed = true,
                ),
                priority = 0,
            ),
            request = HardwareRepairRequest(
                routeId = "denied-trip-route",
                key = "thermal_trip_points",
                owner = ControlOwnership.Owner.MAX_AI,
                token = "synthetic",
                desired = "1",
                apply = { applyCalls += 1; true },
                read = { "1" },
                restore = { true },
            ),
        )
    }

    private companion object {
        const val POLICY_PATH = "/sys/devices/system/cpu/cpufreq/policy0"
        const val LIVE_MIN = 300_000L
        const val PROVEN_MAX = 2_000_000L
        const val HALF = 1_000_000L
        val LADDER = listOf(LIVE_MIN, HALF, PROVEN_MAX)
        const val TOKEN = "atlas-cycle-test"
    }
}
