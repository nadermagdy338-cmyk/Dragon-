package nd.max.core.hardware

import nd.max.core.atlas.AtlasControlGoal
import nd.max.core.atlas.AtlasControlIntent
import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasRoutePlanner
import nd.max.core.atlas.AtlasRouteStatus
import nd.max.core.atlas.AtlasStoreIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * ربط الحارس الحراري بـAtlas: **الاختيار** يخرج من الحارس ولا يعود إليه.
 *
 * وما تحميه هذه الاختبارات بالضبط:
 *
 * 1. `PLATFORM_HINT` تسبق `ARBITER_SYSFS` — فإشارة المنصة تُقدَّم على سقف ثابت.
 * 2. غياب إشارة المنصة (UNKNOWN) يجعل المسار الأول **غير مؤهّل**، فيُنتقل إلى سقف المستخدم
 *    الثابت بدل إسكات كل شيء — وهذا فرق حقيقي عن السلوك السابق.
 * 3. الحارس لا يرفع فوق نيّة المستخدم أبدًا، ولا ينزل تحت أدنى تردد مُعلن.
 * 4. «لا تغيير مطلوب» ليست فشلًا ولا تُنفَّذ معاملة من أجلها.
 * 5. وأدلّة المسار **مقيسة لا حرفيّة**: مسارٌ بلا قراءة حيّة أو بسلّم ترددات غير مُعلن يُرفض برمز
 *    سببه القياسي، والرمز يُنقل إلى المستدعي (`evidence=`) ليعرف أيّ دليلٍ نقص.
 *
 * وملاحظة على بناء الاختبار نفسه: السجل وAtlas يتشاركان **نفس** `HardwareControlArbiter`. ومُحكِّم
 * ثانٍ في الاختبار كان يجعل الطلب يبدو بلا مالك، فيُصنَّف حجبًا لا تنفيذًا — أي أن الاختبار كان
 * سيقيس عطب بناء الاختبار لا سلوك المسار.
 */
class ThermalCeilingRouterTest {

    private val ladder = listOf(180_000_000L, 490_000_000L, 680_000_000L, 754_000_000L)

    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("thermal-router-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }
    }

    @Test
    fun `the platform route is ordered before the static ceiling`() {
        val candidates = ThermalCeilingRoutes.candidates(
            AtlasControlTarget.GPU_FREQUENCY,
            ThermalGuard.Pressure.MODERATE,
            gpuFacts(),
        )

        assertEquals(ThermalCeilingRoutes.PLATFORM_ROUTE_ID, candidates.first().id)
        assertEquals(2, candidates.size)
        assertTrue(
            "كل مرشّح يعرف هدفه؛ المخطِّط يرفض ما لا يطابق الهدف",
            candidates.all { it.evidence.target == AtlasControlTarget.GPU_FREQUENCY },
        )
    }

    @Test
    fun `an unanswered platform signal makes the platform route ineligible`() {
        val candidates = ThermalCeilingRoutes.candidates(
            AtlasControlTarget.GPU_FREQUENCY,
            ThermalGuard.Pressure.UNKNOWN,
            gpuFacts(),
        )

        val platform = candidates.first { it.id == ThermalCeilingRoutes.PLATFORM_ROUTE_ID }
        val static = candidates.first { it.id == ThermalCeilingRoutes.STATIC_ROUTE_ID }
        assertEquals("إشارة مجهولة ليست إشارة باردة", false, platform.evidence.readable)
        assertTrue("والسقف الذي اختاره المستخدم يبقى قابلًا للتنفيذ", static.evidence.readable)
    }

    @Test
    fun `the planner itself selects the platform route when it is eligible`() {
        val decision = AtlasRoutePlanner.choose(
            intent = AtlasControlIntent(
                target = AtlasControlTarget.GPU_FREQUENCY,
                goal = AtlasControlGoal.SUSTAINED_PERFORMANCE,
                desired = "754000000",
            ),
            candidates = ThermalCeilingRoutes.candidates(
                AtlasControlTarget.GPU_FREQUENCY,
                ThermalGuard.Pressure.MODERATE,
                gpuFacts(),
            ),
        )

        assertEquals(AtlasRouteStatus.ELIGIBLE, decision.status)
        assertEquals(ThermalCeilingRoutes.PLATFORM_ROUTE_ID, decision.selected?.id)
    }

    @Test
    fun `the planner falls back to the static ceiling when the signal is unanswered`() {
        val decision = AtlasRoutePlanner.choose(
            intent = AtlasControlIntent(
                target = AtlasControlTarget.GPU_FREQUENCY,
                goal = AtlasControlGoal.SUSTAINED_PERFORMANCE,
                desired = "754000000",
            ),
            candidates = ThermalCeilingRoutes.candidates(
                AtlasControlTarget.GPU_FREQUENCY,
                ThermalGuard.Pressure.UNKNOWN,
                gpuFacts(),
            ),
        )

        assertEquals(AtlasRouteStatus.ELIGIBLE, decision.status)
        assertEquals(ThermalCeilingRoutes.STATIC_ROUTE_ID, decision.selected?.id)
    }

    // ---- أدلّة مقيسة لا حرفيّة ----------------------------------------------------------------

    /**
     * عقدة تُقرأ الآن، ومعاملة قائمة عليها، وسلّم مُعلن ⇒ مسار مؤهّل. وهذا ما يُقارَن به ما يليه.
     */
    private fun gpuFacts(liveReadable: Boolean = true) = RouteEvidenceFacts.gpu(
        liveReadable = liveReadable,
        transactionHeld = true,
        unitTrusted = true,
        ladder = ladder,
    )

    @Test
    fun `an unmeasured knob is refused, and the refusal names the missing evidence`() {
        val candidates = ThermalCeilingRoutes.candidates(
            AtlasControlTarget.GPU_FREQUENCY,
            ThermalGuard.Pressure.SEVERE,
            RouteEvidenceFacts.UNMEASURED,
        )

        assertEquals("لا يُقاس شيء ⇒ لا مسار مؤهّل", false, candidates.any { it.evidence.readable })
        val decision = AtlasRoutePlanner.choose(
            intent = AtlasControlIntent(
                target = AtlasControlTarget.GPU_FREQUENCY,
                goal = AtlasControlGoal.SUSTAINED_PERFORMANCE,
                desired = "754000000",
            ),
            candidates = candidates,
        )
        assertEquals(AtlasRouteStatus.BLOCKED, decision.status)
        assertNull(decision.selected)
        assertEquals("none", RouteEvidenceFacts.UNMEASURED.codes())
    }

    @Test
    fun `a knob with no published ladder is refused as an ambiguous unit`() {
        val facts = RouteEvidenceFacts.cpu(liveReadable = true, transactionHeld = true, ladder = emptyList())
        val candidates = ThermalCeilingRoutes.candidates(
            AtlasControlTarget.CPU_FREQUENCY,
            ThermalGuard.Pressure.NONE,
            facts,
        )

        assertEquals(false, facts.unitProven)
        assertTrue("المقروء يُقاس ويُعلن", candidates.all { it.evidence.readable })
        val decision = AtlasRoutePlanner.choose(
            intent = AtlasControlIntent(
                target = AtlasControlTarget.CPU_FREQUENCY,
                goal = AtlasControlGoal.SUSTAINED_PERFORMANCE,
                desired = "300000:2000000",
            ),
            candidates = candidates,
        )
        assertEquals(AtlasRouteStatus.BLOCKED, decision.status)
        assertEquals("unit_ambiguous", decision.reason?.name?.lowercase())
    }

    @Test
    fun `the registry measures the evidence of an owned knob from a live read`() {
        var live = "754000000"
        val gate = HardwareControlArbiter()
        val registry = registryOwning(gate, KEY, "754000000", { live = it; true }, { live })

        val facts = registry.routeFacts(KEY, unitProven = ladder.any { it > 0L })

        assertEquals(true, facts.readable)
        assertEquals(true, facts.attemptAvailable)
        assertEquals("read+attempt+unit+baseline+rollback", facts.codes())
        // ومقبض لا يملكه السجل: لا أدلّة أصلًا — فشل مغلق لا تخمين.
        assertEquals(RouteEvidenceFacts.UNMEASURED, registry.routeFacts("gpu_frequency:absent", unitProven = true))
    }

    /**
     * وملاحظة على الحدّ بين القياسين: في هذا المسار **السلّم نفسه** هو دليل الوحدة، وهو نفسه ما
     * يخطّط الحارس به خفضًا — فجهاز لا يُعلن سلّمًا لا يُخطَّط له خفض (`planned == previous`)؛
     * والحارس حينها **يثبت ولا يكتب**، ولا يصل إلى باب الوحدة أصلًا. فلا تُدَّعى وحدة، ولا يُخمَّن
     * ما يُكتب، ولا يُسجَّل أن عملًا وقع — وهذا هو المقصود، لا أن يُرفض الطلب بفشل.
     */
    @Test
    fun `a knob with no published ladder is held, not written`() {
        var live = "754000000"
        val router = routerOwning(KEY, "754000000", { live = it; true }, { live })

        val outcome = router.apply(
            key = KEY,
            target = AtlasControlTarget.GPU_FREQUENCY,
            packageName = null,
            userCeiling = "754000000",
            ladder = emptyList(),
            pressure = ThermalGuard.Pressure.SEVERE,
        )

        assertEquals(false, outcome.acted)
        assertEquals(false, outcome.verified)
        assertEquals("ceiling-already-held", outcome.reason)
        assertEquals("754000000", live)
    }

    @Test
    fun `a throttling device routes the ceiling down to the platform value`() {
        var live = "754000000"
        val router = routerOwning(KEY, "754000000", { live = it; true }, { live })

        val outcome = router.apply(
            key = KEY,
            target = AtlasControlTarget.GPU_FREQUENCY,
            packageName = "com.example.game",
            userCeiling = "754000000",
            ladder = ladder,
            pressure = ThermalGuard.Pressure.SEVERE,
        )

        assertTrue(outcome.acted)
        assertTrue(outcome.verified)
        assertEquals(ThermalCeilingRoutes.PLATFORM_ROUTE_ID, outcome.routeId)
        assertEquals("680000000", live)
        assertEquals("680000000", outcome.desired)
    }

    @Test
    fun `cooling restores exactly the ceiling the user chose`() {
        var live = "680000000"
        val router = routerOwning(KEY, "680000000", { live = it; true }, { live })

        val outcome = router.apply(
            key = KEY,
            target = AtlasControlTarget.GPU_FREQUENCY,
            packageName = "com.example.game",
            userCeiling = "754000000",
            ladder = ladder,
            pressure = ThermalGuard.Pressure.NONE,
        )

        assertTrue(outcome.verified)
        assertEquals("754000000", live)
    }

    @Test
    fun `a hold with no change is not an attempt and not a failure`() {
        var live = "490000000"
        val router = routerOwning(KEY, "490000000", { live = it; true }, { live })

        val outcome = router.apply(
            key = KEY,
            target = AtlasControlTarget.GPU_FREQUENCY,
            packageName = null,
            userCeiling = "490000000",
            ladder = ladder,
            pressure = ThermalGuard.Pressure.NONE,
        )

        assertEquals(false, outcome.acted)
        assertEquals("ceiling-already-held", outcome.reason)
        assertEquals("490000000", live)
    }

    @Test
    fun `an unknown knob is never routed`() {
        val router = routerOwning(KEY, "754000000", { true }, { "754000000" }, own = false)

        val outcome = router.apply(
            key = KEY,
            target = AtlasControlTarget.GPU_FREQUENCY,
            packageName = null,
            userCeiling = "754000000",
            ladder = ladder,
            pressure = ThermalGuard.Pressure.MODERATE,
        )

        assertEquals(false, outcome.acted)
        assertEquals("knob-not-owned", outcome.reason)
        assertNull(outcome.routeId)
    }

    @Test
    fun `a cpu range keeps its shape and its floor follows the new ceiling`() {
        var live = "300000:2000000"
        val router = routerOwning("cpu_limits:policy0", "300000:2000000", { live = it; true }, { live })

        val outcome = router.apply(
            key = "cpu_limits:policy0",
            target = AtlasControlTarget.CPU_FREQUENCY,
            packageName = "com.example.game",
            userCeiling = "300000:2000000",
            ladder = listOf(300_000L, 800_000L, 1_400_000L, 2_000_000L),
            pressure = ThermalGuard.Pressure.CRITICAL,
        )

        assertTrue(outcome.verified)
        assertEquals("300000:1400000", live)
    }

    @Test
    fun `a blocked transaction leaves the user intent untouched`() {
        var live = "754000000"
        val gate = HardwareControlArbiter()
        val registry = registryOwning(gate, KEY, "754000000", { live = it; true }, { live })
        ManualControlLocks.lock(KEY, "user", "manual", live)
        val router = routerWith(registry, gate)

        val outcome = router.apply(
            key = KEY,
            target = AtlasControlTarget.GPU_FREQUENCY,
            packageName = "com.example.game",
            userCeiling = "754000000",
            ladder = ladder,
            pressure = ThermalGuard.Pressure.SEVERE,
        )

        assertEquals(false, outcome.verified)
        assertEquals("754000000", live)
        assertEquals(
            "قفل المستخدم يوقف الحارس ولا يمحو إعداده",
            "754000000",
            registry.ownedDesired()[KEY],
        )
    }

    private fun routerOwning(
        key: String,
        desired: String,
        apply: (String) -> Boolean,
        read: () -> String?,
        own: Boolean = true,
    ): ThermalCeilingRouter {
        val gate = HardwareControlArbiter()
        val registry = PerAppControlRegistry(
            mutationGate = gate,
            confirmationSamples = 1,
            confirmationIntervalMs = 0L,
            sleep = {},
        )
        if (own) assertTrue(registry.ownValue(key, desired, apply, read))
        return routerWith(registry, gate)
    }

    private fun registryOwning(
        gate: HardwareControlArbiter,
        key: String,
        desired: String,
        apply: (String) -> Boolean,
        read: () -> String?,
    ): PerAppControlRegistry {
        val registry = PerAppControlRegistry(gate, confirmationSamples = 1, confirmationIntervalMs = 0L, sleep = {})
        assertTrue(registry.ownValue(key, desired, apply, read))
        return registry
    }

    /**
     * مسار محجور يُعلَن ولا يُجرَّب.
     *
     * وهذه هي الحالة التي كانت **صامتة تمامًا**: مسار حُجر لأنه ترك خط الأساس غير مؤكَّد في إقلاع
     * سابق، فيتخطّاه Atlas ولا يُجرّبه — فيبدو للحارس أن «لا شيء حدث»، ويقرأ من يُصلح العطل سطرًا
     * يقول فشلًا بلا سبب. فالاختبار يُثبت الأمرين معًا: أن المحجور لا يُنفَّذ، وأن تخطّيه يُبلَّغ.
     */
    @Test
    fun `a route quarantined in this boot is skipped and the skip is reported`() {
        var live = "754000000"
        val gate = HardwareControlArbiter()
        val registry = registryOwning(gate, KEY, "754000000", { live = it; true }, { live })
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L })
        memory.noteFailed("gpu_frequency", ThermalCeilingRoutes.PLATFORM_ROUTE_ID, rollbackVerified = false)
        val router = ThermalCeilingRouter(
            registry = registry,
            adaptive = AtlasAdaptiveExecutor(
                repairExecutor = HardwareRepairExecutor(gate),
                memory = memory,
            ),
        )

        val outcome = router.apply(
            key = KEY,
            target = AtlasControlTarget.GPU_FREQUENCY,
            packageName = "com.example.game",
            userCeiling = "754000000",
            ladder = ladder,
            pressure = ThermalGuard.Pressure.SEVERE,
        )

        assertEquals(
            "المسار الذي حُجر لا يُجرَّب: الطلب يذهب إلى سقف المستخدم",
            ThermalCeilingRoutes.STATIC_ROUTE_ID,
            outcome.routeId,
        )
        assertTrue(outcome.verified)
        assertEquals("754000000", live)
        assertEquals(
            "وتخطّي المسار يُعلَن بسبب ثابت لا بصمت",
            listOf(ThermalCeilingRoutes.PLATFORM_ROUTE_ID to "route-quarantined-after-unverified-rollback"),
            outcome.skipped,
        )
    }

    private fun routerWith(registry: PerAppControlRegistry, gate: HardwareControlArbiter) = ThermalCeilingRouter(
        registry = registry,
        adaptive = AtlasAdaptiveExecutor(
            repairExecutor = HardwareRepairExecutor(gate),
            memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }),
        ),
    )

    private class InMemoryIo : AtlasStoreIo {
        private val files = mutableMapOf<String, String>()
        override fun list(): List<String> = files.keys.sorted()
        override fun read(name: String): String? = files[name]
        override fun write(name: String, text: String): Boolean {
            files[name] = text
            return true
        }

        override fun delete(name: String): Boolean = files.remove(name) != null
    }

    private companion object {
        const val KEY = "gpu_frequency:kgsl-3d0"
    }
}
