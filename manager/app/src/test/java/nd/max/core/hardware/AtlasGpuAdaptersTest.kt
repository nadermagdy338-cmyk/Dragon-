/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import nd.max.core.atlas.AtlasCapabilityState
import nd.max.core.atlas.AtlasControlGoal
import nd.max.core.atlas.AtlasControlIntent
import nd.max.core.atlas.AtlasControlTarget
import nd.max.core.atlas.AtlasDeviceIdentity
import nd.max.core.atlas.AtlasExecutionVerdict
import nd.max.core.atlas.AtlasStoreIo
import nd.max.core.atlas.MaxAtlas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * ملاءِما GPU — ما يُقاس هنا هو جوهر طلب المالك: **الطريقة تُختار بالقياس لا بالاسم** («MediaTek
 * ≠ MediaTek على كيرنل آخر»)، والجهاز الذي لا طريقة موثوقة له يُعلن فجوة لا يُخترع له مسار،
 * والنجاح يُثبته قراءةُ الجهاز لا إرسالُ الأمر.
 *
 * والوحدات مقصودة العبث: أجهزة الاختبار بوحدة Hz ومقياسٍ آخر بوحدة MHz — فالعقود تُحوِّل بلسان
 * واحد ([GpuCeilingContracts]) ولا تُقارَن أرقامٌ بوحدتين أبدًا.
 */
class AtlasGpuAdaptersTest {

    private lateinit var access: FakeGpuAccess
    private lateinit var memory: AtlasRouteMemory
    private lateinit var atlas: MaxAtlas

    @Before
    fun configureControlPlane() {
        val root = Files.createTempDirectory("atlas-gpu-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }

        access = FakeGpuAccess()
        memory = AtlasRouteMemory(
            io = InMemoryStoreIo(),
            clockMs = { 0L },
            bootGeneration = { 1L },
            privilegeGeneration = { 0L },
        )
        atlas = MaxAtlas(
            registry = AtlasAdapterRegistry.defaults(),
            executor = AtlasAdaptiveExecutor(HardwareRepairExecutor(HardwareControlArbiter(), sleep = {}), memory = memory),
            memory = memory,
            identity = AtlasDeviceIdentity(supportedAbis = listOf("arm64-v8a"), apiLevel = 34),
            catalogVersion = "test-catalog",
            clockMs = { 100L },
            bootGeneration = { 1L },
            privilegeGeneration = { 0L },
        )
    }

    // ── Adapt: الطريقة تُختار بالقياس ───────────────────────────────────────

    @Test
    fun `the ceiling method wins where the device accepts a range and pin refuses`() {
        val context = context(devfreqDevice())

        assertTrue(GpuDevfreqCeilingAdapter().assess(context) is AtlasAdapterAssessment.Applicable)
        // وجود مسار تثبيت OPP إلى جانبه لا يغيّر اختيار السقف — الخلط هو العطب المقيس على rodin.
        assertEquals(
            "gpu-devfreq-ceiling",
            (AtlasAdapterRegistry.defaults().choose(AtlasControlTarget.GPU_FREQUENCY, context)
                as AtlasAdapterChoice.Chosen).adapter.id,
        )
        assertEquals(
            "devfreq-ceiling-preferred",
            (GpuOppPinCeilingAdapter().assess(context) as AtlasAdapterAssessment.NotApplicable).reason,
        )
    }

    @Test
    fun `pinning applies only where the device accepts no range`() {
        val context = context(pinDevice())

        assertTrue(GpuOppPinCeilingAdapter().assess(context) is AtlasAdapterAssessment.Applicable)
        assertEquals(
            "gpu-opp-pin-ceiling",
            (AtlasAdapterRegistry.defaults().choose(AtlasControlTarget.GPU_FREQUENCY, context)
                as AtlasAdapterChoice.Chosen).adapter.id,
        )
        assertEquals(
            "devfreq-ceiling-not-writable",
            (GpuDevfreqCeilingAdapter().assess(context) as AtlasAdapterAssessment.NotApplicable).reason,
        )
    }

    @Test
    fun `an unmeasured unit is refused with its own reason, never guessed`() {
        val context = context(devfreqDevice().copy(frequencyUnit = GpuHardwareBackend.FrequencyUnit.AMBIGUOUS))

        assertEquals(
            "frequency-unit-ambiguous",
            (GpuDevfreqCeilingAdapter().assess(context) as AtlasAdapterAssessment.NotApplicable).reason,
        )
        assertNull(GpuCeilingContracts.requestFor(devfreqDevice().copy(frequencyUnit = GpuHardwareBackend.FrequencyUnit.AMBIGUOUS), HALF))
    }

    @Test
    fun `an unknown device is a declared gap with reasons, not silence`() {
        val choice = AtlasAdapterRegistry.defaults()
            .choose(AtlasControlTarget.GPU_FREQUENCY, context(device = null))

        assertTrue(choice is AtlasAdapterChoice.Gap)
        assertEquals(
            listOf("gpu-devfreq-ceiling" to "no-gpu-device-seen", "gpu-opp-pin-ceiling" to "no-gpu-device-seen"),
            (choice as AtlasAdapterChoice.Gap).rejections,
        )
    }

    // ── Map: القصّ إلى السلّم المُعلن، والفجوة تُعلن ─────────────────────────

    @Test
    fun `the ceiling snaps to the advertised ladder and a request below the floor plans nothing`() {
        val high = GpuDevfreqCeilingAdapter()
            .plan(context(devfreqDevice()), gpuCeilings(mapOf(GPU_NAME to 800_000_000L)))
        // طلبٌ أعلى من درجة مُعلنة يُقصّ إلى أعلاها لا يتجاوزها — ولا قيمة مُخترعة أبدًا.
        assertEquals("754000000", high.bindings.single().request.desired)

        val low = GpuDevfreqCeilingAdapter()
            .plan(context(devfreqDevice()), gpuCeilings(mapOf(GPU_NAME to 100_000_000L)))
        assertTrue(low.bindings.isEmpty())
        assertEquals(GPU_KEY to "no-proven-range", low.skipped.single())
    }

    @Test
    fun `the map declares adapter gaps instead of guessing`() {
        val profile = atlas.profile(
            context(readOnlyDevice()),
            listOf(AtlasControlTarget.GPU_FREQUENCY),
        )

        val capability = profile.capabilities.forTarget(AtlasControlTarget.GPU_FREQUENCY)
        assertEquals(AtlasCapabilityState.NEEDS_ADAPTER, capability?.state)
        assertEquals("adapter-gap", capability?.reason)
    }

    // ── Execute · Verify: لا نجاح إلا وقد أثبتته قراءة ───────────────────────

    @Test
    fun `a gpu ceiling proven by read-back is the only verdict that claims success`() {
        val report = atlas.execute(context(devfreqDevice()), gpuCeilings(mapOf(GPU_NAME to HALF)))

        assertEquals(AtlasExecutionVerdict.VERIFIED, report.verdict)
        assertEquals(listOf(HALF.toString()), access.writes)
        assertEquals(
            HardwareRepairExecutor.labelFor(HardwareControlKey.gpuFrequency(GPU_NAME)),
            report.selectedRouteId,
        )
        assertEquals(AtlasCapabilityState.SUPPORTED, report.capability.state)
        // والتعلّم وقع: المسار محفوظ مُثبَتًا لهذا الجيل.
        assertTrue(memory.preferredRoute("gpu_frequency") != null)
    }

    @Test
    fun `a writer that refuses is never reported as success`() {
        access.refuse = true

        val report = atlas.execute(context(devfreqDevice()), gpuCeilings(mapOf(GPU_NAME to HALF)))

        assertNotEquals(AtlasExecutionVerdict.VERIFIED, report.verdict)
        assertEquals(AtlasExecutionVerdict.STATE_UNKNOWN, report.verdict)
        assertFalse(report.verified)
        assertNull(memory.preferredRoute("gpu_frequency"))
    }

    @Test
    fun `pin execution is judged by the clock the device runs at`() {
        val report = atlas.execute(context(pinDevice()), gpuCeilings(mapOf(GPU_NAME to HALF)))

        assertEquals(AtlasExecutionVerdict.VERIFIED, report.verdict)
        assertEquals(listOf(HALF.toString()), access.pins)
        assertTrue(access.writes.isEmpty())
    }

    @Test
    fun `never-touch surfaces are refused before any binding survives`() {
        val device = devfreqDevice().copy(
            path = "/sys/class/thermal/thermal_zone0/trip_point_0_temp",
            name = "trip_point_0_temp",
        )

        val report = atlas.execute(
            context(device),
            gpuCeilings(mapOf("trip_point_0_temp" to HALF)),
        )

        assertEquals(AtlasExecutionVerdict.REFUSED_SAFETY, report.verdict)
        assertEquals("never-touch:thermal-trips", report.refusalCode)
        assertEquals(AtlasCapabilityState.NEVER_TOUCH, report.capability.state)
        assertTrue(access.writes.isEmpty())
        assertTrue(access.pins.isEmpty())
    }

    // ── عقود الحكم — جدول حقيقة، ووحدات لا تُخلط ────────────────────────────

    @Test
    fun `the ceiling contract accepts containment and refuses a frozen lock`() {
        // «لا تتجاوز» يكفيه السقف المكتوب؛ والتبريد القاصّ تحت الطلب لا يضرّه.
        assertTrue(GpuCeilingContracts.contained(HALF.toString(), token(HALF), HZ))
        // لكن سقفًا فوق الطلب = تجاوز، وقفلٌ مُثبِّت = تجميد لا سقف.
        assertFalse(GpuCeilingContracts.contained(HALF.toString(), token(FULL), HZ))
        assertFalse(GpuCeilingContracts.contained(HALF.toString(), token(HALF, lockActive = true), HZ))
        assertFalse(GpuCeilingContracts.contained(HALF.toString(), "not-a-token", HZ))
        assertFalse(GpuCeilingContracts.contained(null, token(HALF), HZ))
    }

    @Test
    fun `the reached contract declares what the platform kept instead of failing`() {
        // بلغ الطلب فعلًا ⇒ نجاح. وأمّا «يحتويه» وحده (سقفٌ أدنى من الطلب) فلا: بلا هذا التفرّق
        // تُقرأ قراءةٌ أدنى «مُلبّاة» فتُتخطّى الكتابة فلا تُكتب قيمة أعلى أبدًا.
        assertTrue(GpuCeilingContracts.reachedOrReleased(HALF.toString(), token(HALF), HZ, FULL))
        assertFalse(GpuCeilingContracts.reachedOrReleased(HALF.toString(), token(LADDER.first()), HZ, FULL))
        // وفوق قدرة المنصّة المُعلنة: تحريرٌ وقع والمنصّة تحتفظ بأدنى — نجاحٌ لِما نملك ورقمٌ
        // يُعلن ما لا نملك (`OPEN_BELOW_REQUEST`)، لا فشلٌ مُكرَّر.
        assertTrue(GpuCeilingContracts.reachedOrReleased(FULL.toString(), token(HALF), HZ, FULL))
        // أمّا التبريد القاصّ فلم نُحرِّر شيئًا ⇒ لا نجاح كاذب.
        val held = GpuCeilingPolicy.CeilingReading(
            nodeCeilingHz = HALF,
            platformUpbound = 0L,
            platformCoolingHeld = true,
        )
        assertFalse(GpuCeilingContracts.reachedOrReleased(FULL.toString(), held.token, HZ, FULL))
    }

    @Test
    fun `the pin contract reads the clock and never max_freq`() {
        assertTrue(GpuCeilingContracts.pinHolds(HALF.toString(), HALF.toString(), HZ))
        assertTrue(GpuCeilingContracts.pinMeasured(HALF.toString(), HALF.toString(), HZ))
        // ساعةٌ تخالف المثبَّت = انحراف يُعلن؛ وغيابُ الساعة = أقوى دليل هو صدى الفهرس (يُقبل في
        // العقد الضعيف وحده، لا في حكم «البلغ» القوي).
        assertFalse(GpuCeilingContracts.pinHolds(HALF.toString(), FULL.toString(), HZ))
        assertFalse(GpuCeilingContracts.pinMeasured(HALF.toString(), FULL.toString(), HZ))
        assertTrue(GpuCeilingContracts.pinHolds(HALF.toString(), null, HZ))
        assertFalse(GpuCeilingContracts.pinMeasured(HALF.toString(), null, HZ))
    }

    @Test
    fun `units convert once at the contract and never mix`() {
        // جهاز MHz: الأرقام كلها بوحدة العقدة، والتوكن بلغة السقف (Hz) — والمقاس هو التحويل نفسه.
        val mhz = 1_000L
        assertTrue(
            GpuCeilingContracts.reachedOrReleased("1000", token(1_000_000_000L), mhz, 1_000L),
        )
        assertTrue(GpuCeilingContracts.pinHolds("442", "442", mhz))
        assertFalse(GpuCeilingContracts.pinHolds("442", "754", mhz))
    }

    @Test
    fun `the write shape is decided once and the release never becomes a pin`() {
        // الطلب عند القدرة = تحرير: مدىٌ مفتوح لا تثبيت — والتثبيت يجمّد فلا يُسمّى سقفًا.
        val release = GpuCeilingContracts.requestFor(devfreqDevice(), FULL)
        assertEquals(LADDER.first(), release?.minFreq)
        assertEquals(FULL, release?.maxFreq)
        assertNotEquals(release?.minFreq, release?.maxFreq)

        // وجهاز التثبيت وحده يكتب `min == max` — درجة واحدة بفهرسها.
        val pin = GpuCeilingContracts.requestFor(pinDevice(), HALF)
        assertEquals(HALF, pin?.minFreq)
        assertEquals(HALF, pin?.maxFreq)

        // وطلبٌ دون أدنى درجة مُعلنة لا شكل له: لا طلب مُخترع.
        assertNull(GpuCeilingContracts.requestFor(devfreqDevice(), 100_000_000L))
    }

    // ── مساعدات ────────────────────────────────────────────────────────────

    private fun context(device: GpuHardwareBackend.Device?) = AtlasAdapterContext(
        privileged = true,
        gpuDevice = device,
        gpuAccess = access,
    )

    private fun gpuCeilings(
        ceilings: Map<String, Long?>,
        reviewed: (String) -> Boolean = { true },
    ) = AtlasControlRequest.GpuCeilings(
        intent = AtlasControlIntent(
            target = AtlasControlTarget.GPU_FREQUENCY,
            goal = AtlasControlGoal.SUSTAINED_PERFORMANCE,
        ),
        owner = ControlOwnership.Owner.MAX_AI,
        token = "atlas-gpu-test",
        ceilingByKnob = ceilings,
        reviewed = reviewed,
    )

    private fun devfreqDevice() = GpuHardwareBackend.Device(
        path = GPU_PATH,
        name = GPU_NAME,
        governor = "msel",
        governors = listOf("msel"),
        minFreq = LADDER.first(),
        maxFreq = HALF,
        currentFreq = HALF,
        frequencies = LADDER,
        frequencyUnit = GpuHardwareBackend.FrequencyUnit.HZ,
        minWritable = true,
        maxWritable = true,
    )

    /** MEDIATEK ونحوه: لا يقبل المدى، وله فهرس OPP مكتوب ومُقاس — التثبيت يبقى لجهاز كهذا. */
    private fun pinDevice() = GpuHardwareBackend.Device(
        path = GPU_PATH,
        name = GPU_NAME,
        governor = "msel",
        governors = listOf("msel"),
        minFreq = LADDER.first(),
        maxFreq = FULL,
        currentFreq = HALF,
        frequencies = LADDER,
        frequencyUnit = GpuHardwareBackend.FrequencyUnit.HZ,
        minWritable = false,
        maxWritable = false,
        mtkFixedIndexPath = "/proc/gpufreqv2/fix_target_opp_index",
        mtkLockWritable = true,
        mtkOppIndexByFrequency = LADDER.mapIndexed { index, hz -> hz to index.toString() }.toMap(),
    )

    /** يقرأ ولا يكتب بأيّ طريقة — الفجوة تُعلن «يحتاج ملاءِمًا» لا «غير مدعوم». */
    private fun readOnlyDevice() = GpuHardwareBackend.Device(
        path = GPU_PATH,
        name = GPU_NAME,
        governor = null,
        governors = emptyList(),
        minFreq = LADDER.first(),
        maxFreq = FULL,
        currentFreq = HALF,
        frequencies = LADDER,
        frequencyUnit = GpuHardwareBackend.FrequencyUnit.HZ,
    )

    private fun token(nodeHz: Long?, lockActive: Boolean = false): String =
        GpuCeilingPolicy.CeilingReading(
            nodeCeilingHz = nodeHz,
            platformUpbound = 0L,
            platformCoolingHeld = false,
            lockActive = lockActive,
        ).token

    /** ثقب مزيّف يكتب في ذاكرته — والقراءة المرتجعة هي ما يصنع النجاح أو تمنعه. */
    private class FakeGpuAccess : AtlasGpuCeilingAccess {
        override val privileged: Boolean = true

        // الحالة الابتدائية **أدنى درجة** لا الطلب نفسه: قراءةٌ تساوي الطلب تجعل المُحكِّم يقرّر
        // «مُبلَّغ فعلًا» فيتخطّى الكتابة ([HardwareRepairRequest.realized]) — والمقاس هو أن الكتابة
        // تقع وتُقرأ، لا أنها تُرسل.
        var ceilingToken: String? = GpuCeilingPolicy.CeilingReading(
            nodeCeilingHz = LADDER.first(),
            platformUpbound = 0L,
            platformCoolingHeld = false,
            lockActive = false,
        ).token

        var clockNode: String? = LADDER.first().toString()
        var refuse: Boolean = false
        val writes = mutableListOf<String>()
        val pins = mutableListOf<String>()

        override fun readCeiling(devicePath: String): String? = ceilingToken
        override fun readClock(devicePath: String): String? = clockNode

        override fun writeCeiling(devicePath: String, ceilingNode: String): Boolean {
            if (refuse) return false
            writes += ceilingNode
            ceilingToken = GpuCeilingPolicy.CeilingReading(
                nodeCeilingHz = ceilingNode.toLongOrNull(),
                platformUpbound = 0L,
                platformCoolingHeld = false,
                lockActive = false,
            ).token
            clockNode = ceilingNode
            return true
        }

        override fun pinFrequency(devicePath: String, frequencyNode: String): Boolean {
            if (refuse) return false
            pins += frequencyNode
            clockNode = frequencyNode
            ceilingToken = GpuCeilingPolicy.CeilingReading(
                nodeCeilingHz = frequencyNode.toLongOrNull(),
                platformUpbound = 0L,
                platformCoolingHeld = false,
                lockActive = true,
            ).token
            return true
        }

        override fun restoreCeiling(devicePath: String, baselineToken: String): Boolean {
            if (refuse) return false
            ceilingToken = baselineToken
            return true
        }

        override fun restoreClock(devicePath: String, baselineNode: String): Boolean {
            if (refuse) return false
            clockNode = baselineNode
            return true
        }
    }

    /** مخزن بلا قرص: التعلّم يُختبَر بلا ملفات. */
    private class InMemoryStoreIo : AtlasStoreIo {
        private val entries = linkedMapOf<String, String>()
        override fun read(name: String): String? = entries[name]
        override fun write(name: String, text: String): Boolean {
            entries[name] = text
            return true
        }

        override fun delete(name: String): Boolean = entries.remove(name) != null
        override fun list(): List<String> = entries.keys.toList()
    }

    private companion object {
        const val GPU_NAME = "13000000.mali"
        const val GPU_PATH = "/sys/class/devfreq/13000000.mali"
        const val GPU_KEY = "gpu_frequency:13000000.mali"
        const val HZ = 1L
        const val HALF = 442_000_000L
        const val FULL = 1_000_000_000L
        val LADDER = listOf(220_000_000L, 442_000_000L, 754_000_000L, FULL)
    }
}
