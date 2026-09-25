/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import nd.max.core.atlas.AtlasBackendProvider
import nd.max.core.atlas.AtlasCpuFrequencySource
import nd.max.core.atlas.AtlasCpuPolicyFact
import nd.max.core.atlas.AtlasDeviceIdentity
import nd.max.core.atlas.MaxAtlas
import nd.max.core.hardware.AtlasAdaptiveExecutor
import nd.max.core.hardware.AtlasAdapterRegistry
import nd.max.core.hardware.AtlasCeilingAccess
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.HardwareRepairExecutor
import nd.max.core.hardware.ManualControlLocks
import nd.max.core.hardware.SharedHardwareOwnershipStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * **مسار أطلس الإنتاجي**: مقبض سقف CPU في MAX AI يكتب من دليلٍ مقيس لا من ادّعاء.
 *
 * ولماذا هذا الاختبار موجود أصلًا: كان جسر أطلس (اكتشاف ⇒ معاملة كتابة) قائمًا ومُختبَرًا،
 * **ولا مُستدعي إنتاجي له** — أي أن أطلس كان قادرًا على الكتابة ولا أحد يشغّله. فما يُقاس هنا هو
 * الربط نفسه: الطلب يمرّ على المخطِّط، والكتابة تقع على العقدة، وكل رفض يبقى **بلا كتابة** ويُسمّى
 * بسبب رمزه.
 *
 * والأرقام مختارة عن قصد: العقدة على أعلى السلّم، والطلب عند نصف المدى المُثبت، فيقع فعل الكتابة
 * فعلًا. ولولا ذلك لكان الطلب مُلبّى أصلًا فما كُتب شيء (سلوك مقصود يمنع الكتابات بلا داعٍ).
 */
class CpuCeilingKnobsAtlasTest {

    private lateinit var access: FakeCeilingAccess
    private lateinit var arbiter: HardwareControlArbiter
    private lateinit var knobs: CpuCeilingKnobs

    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("cpu-ceiling-knobs-atlas-test").toFile()
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
        knobs = CpuCeilingKnobs(
            arbiter = arbiter,
            atlasDiscovery = AtlasBackendProvider(elapsedMs = { 0L }),
            // المسار الإنتاجي صار كله عبر نظام Max Atlas المركزي: الملاءِم يختار «كيف»، والطلب
            // يُنفَّذ بالمعاملة المُتحقَّقة — وهذه الحشوة هي ما يُبقيه قابلًا للقياس في JVM.
            maxAtlas = MaxAtlas(
                registry = AtlasAdapterRegistry.defaults(),
                executor = AtlasAdaptiveExecutor(HardwareRepairExecutor(arbiter, sleep = {})),
                identity = AtlasDeviceIdentity(supportedAbis = listOf("arm64-v8a"), apiLevel = 34),
                catalogVersion = "test-catalog",
            ),
        )
    }

    @Test
    fun `a discovered ceiling is planned from measured evidence and written through the arbiter`() {
        val outcome = knobs.capDiscovered(
            facts = listOf(fact()),
            fraction = 0.5f,
            owner = ControlOwnership.Owner.MAX_AI,
            token = TOKEN,
            access = access,
        )

        assertEquals("سياسة واحدة مكتشفة ⇒ معاملة واحدة مكتوبة", 1, outcome.applied)
        assertEquals(0, outcome.failed)
        // 0.5 من المدى المُثبت = 1_150_000 ⇒ تُقصّ إلى السلّم المُعلن (1_000_000)، والأرضية تُقرأ حيّة.
        assertEquals("$LIVE_MIN:$HALF", access.nodes[POLICY_PATH])
        assertEquals(1, access.writes)
        assertTrue("السجل يقول أيّ مسار كتب", outcome.detail.contains("atlas=cpu-limits-policy0"))
        assertTrue(outcome.detail.contains("planned=1"))
        // والمالك المُعلن هو مالك MAX AI نفسه (الأسبقية في المُحكِّم، لا كتابة خاصة).
        val lease = ControlOwnership.winner(HardwareControlKey.cpuLimits("policy0"))
        assertEquals(ControlOwnership.Owner.MAX_AI, lease?.owner)
        assertEquals(TOKEN, lease?.ownerToken)
    }

    @Test
    fun `a policy with no published ladder is skipped and nothing is written`() {
        val outcome = knobs.capDiscovered(
            facts = listOf(fact(ladder = emptyList())),
            fraction = 0.5f,
            owner = ControlOwnership.Owner.MAX_AI,
            token = TOKEN,
            access = access,
        )

        assertEquals(0, outcome.applied)
        assertEquals(0, access.writes)
        assertEquals("$LIVE_MIN:$PROVEN_MAX", access.nodes[POLICY_PATH])
        assertTrue("التخطّي يُسمّى لا يُسكَت عنه", outcome.detail.contains("planned=0"))
        assertTrue(outcome.detail.contains("skipped=1"))
    }

    @Test
    fun `an unreviewed route is refused by the planner and nothing is written`() {
        val outcome = knobs.capDiscovered(
            facts = listOf(fact()),
            fraction = 0.5f,
            owner = ControlOwnership.Owner.MAX_AI,
            token = TOKEN,
            access = access,
            reviewed = { false },
        )

        assertEquals(0, outcome.applied)
        assertEquals(0, access.writes)
        assertTrue(
            "المسار غير المُراجَع يُرفض بسببه، لا بمحاولة كتابة فاشلة",
            outcome.detail.contains("no-route"),
        )
    }

    @Test
    fun `a writer that refuses never becomes a claimed success`() {
        access.refuseWrites = true
        val outcome = knobs.capDiscovered(
            facts = listOf(fact()),
            fraction = 0.5f,
            owner = ControlOwnership.Owner.MAX_AI,
            token = TOKEN,
            access = access,
        )

        assertTrue("كاتبٌ يرفض ⇒ صفر نجاح معلن", access.writes > 0)
        assertEquals(0, outcome.applied)
        // والسبب مُسمّى برمزه: الكتابة لم تثبت، فالاسترجاع جُرّب ولم يُثبت أيضًا ⇒ المسار يتوقّف
        // (`rollback was not verified`) ولا يُتبَع بمسار آخر على جهاز مجهولة حالته.
        assertTrue(
            "وفشل المسار يُسمّى كما هو، لا يُقرأ نجاحًا",
            outcome.detail.contains("rollback was not verified"),
        )
        assertFalse(outcome.detail.contains("atlas=cpu-limits"))
    }

    /**
     * ولا نيّة ⇒ لا مسار: قائمة سياسات فارغة (جهاز لا يُعلن شيئًا، أو اكتشاف فشل) تُنتج صفر كتابات
     * وسببًا مكتوبًا — والمستدعي يُكمل على المسار المُتحقَّق القائم، فلا يكون أطلس شرطًا لعمل التحكم.
     */
    @Test
    fun `no discovered policy yields no route and no write`() {
        val outcome = knobs.capDiscovered(
            facts = emptyList(),
            fraction = 0.5f,
            owner = ControlOwnership.Owner.MAX_AI,
            token = TOKEN,
            access = access,
        )

        assertEquals(0, outcome.applied)
        assertEquals(0, outcome.failed)
        assertEquals(0, access.writes)
        assertTrue(outcome.detail.contains("planned=0"))
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

    private companion object {
        const val POLICY_PATH = "/sys/devices/system/cpu/cpufreq/policy0"
        const val LIVE_MIN = 300_000L
        const val PROVEN_MAX = 2_000_000L

        /** نصف المدى = 1_150_000 ⇒ تُقصّ إلى 1_000_000 (أقرب عضو في السلّم تحته). */
        const val HALF = 1_000_000L
        val LADDER = listOf(LIVE_MIN, HALF, PROVEN_MAX)
        const val TOKEN = "max-ai-ceiling-test"
    }
}
