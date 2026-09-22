package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * العطب الجذري المقيس: **رفع سقفٍ كتبناه نحن لم يكن يُكتب أبدًا.**
 *
 * القياس (rodin · MT6899 · 2026-09-22) من حزمة سجل الجهاز، سطرًا بسطر:
 *
 * ```
 * 22:46:20  WRITE_CHECK path=…mali/max_freq wrote=1092000000 read=1092000000 verdict=matched   (gaming)
 * 22:46:49  WRITE_CHECK path=…mali/max_freq wrote=780000000  read=780000000  verdict=matched   (balanced)
 * 22:47:28  WRITE_CHECK path=…mali/max_freq wrote=520000000  read=520000000  verdict=matched   (power)
 * 22:48:27  PERAPP_GPU_CAPABILITY_REQUESTED requested=702000000 live_before=520000000
 * 22:48:33  PERAPP_COMMIT knob=gpu_frequency:13000000.mali requested=702000000 applied=true verified=true live=520000000
 * 22:48:37  APPLY_DRIFT_REPAIRED knob=gpu_frequency:13000000.mali expected=702000000 live=520000000
 * ```
 *
 * لا سطر `WRITE_CHECK` على `max_freq` بين ٢٢:٤٨:٢٧ و٢٢:٤٨:٣٧: **صفر كتابة**. والقراءة الحيّة
 * ٥٢٠ في سطر «نجاح» ليست قمعًا من المنصّة — هي **خفضُنا نحن** في السطر الذي قبله بستّين ثانية.
 *
 * والآلية اثنتان لا واحدة، وكلاهما مسجَّلة هنا:
 *
 * 1. المُحكِّم يتخطّى الكتابة إذا كانت القراءة «مُلبّاة» بالمعنى المتسامح (`live ≤ wanted`)، وهي
 *    مقارنة **أحادية الاتجاه**: صحيحة لِما لا نملكه، خاطئة لِما نملكه — فسقفُنا السابق يُقرأ
 *    دليلًا على أن الطلب نُفِّذ ([HardwareVerification.ceilingReached] هي الدليل الصحيح).
 * 2. وخط الأساس نفسه يُلتقط من حالةٍ خفّضناها نحن، فلا يعود الجهاز إلى ما كان بعد الخروج
 *    (`GpuHardwareBackend.restoreBaseline` على MediaTek — انظر `GpuControlModelTest`).
 *
 * والحدّان محفوظان معًا: لا نجاح بلا دليل كتابة، ولا استرجاع مدمِّر لطلبٍ كتبناه ولم تبلغه المنصّة.
 */
class CeilingRaiseTest {

    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("ceiling-raise-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }
    }

    /** الحالة المقيسة نفسها: سقف ٥٢٠ كتبناه، ثم طلبٌ عند ٧٠٢. */
    @Test
    fun `a raise above a ceiling this app wrote is written, not read as satisfied`() {
        val arbiter = HardwareControlArbiter()
        var live = "520000000"
        var writes = 0

        val result = arbiter.submit(
            key = "gpu_frequency:13000000.mali",
            owner = ControlOwnership.Owner.PER_APP,
            token = "per-app:com.example",
            desired = "702000000",
            apply = { value -> writes += 1; live = value; true },
            read = { live },
            baseline = "520000000",
            restore = { value -> live = value; true },
            verify = HardwareVerification::ceilingAtMost,
            realized = HardwareVerification::ceilingReached,
        )

        assertEquals("الرفع يجب أن يُكتب فعلًا", 1, writes)
        assertEquals("702000000", live)
        assertTrue(result.verified)
        assertFalse("ولا استرجاع: الطلب نُفِّذ", result.rollbackAttempted)
    }

    /**
     * وحرس الانحدار الآخر: كل من لم يُمرّر `realized` يبقى على سلوكه بالحرف.
     *
     * وهذا ليس تفصيلًا: المُحكِّم مشترك بين كل مقابض التطبيق، وتغيير حكمه بلا مَرَرٍّ صريح كان
     * سيغيّر كتابات لم تُقَس ولم تُراجَع.
     */
    @Test
    fun `the same submit without the proof keeps the old behaviour`() {
        val arbiter = HardwareControlArbiter()
        var live = "520000000"
        var writes = 0

        val result = arbiter.submit(
            key = "gpu_frequency:13000000.mali",
            owner = ControlOwnership.Owner.PER_APP,
            token = "per-app:com.example",
            desired = "702000000",
            apply = { value -> writes += 1; live = value; true },
            read = { live },
            baseline = "520000000",
            verify = HardwareVerification::ceilingAtMost,
        )

        assertEquals("بلا دليل كتابة لا يُكتب شيء — وهذا ما كان", 0, writes)
        assertTrue(result.verified)
        assertEquals("520000000", live)
    }

    /**
     * ومنصّة تُمسك سقفًا أدنى حقًّا: الكتابة تُحاول مرّة، ثم يحكم الحكم المتسامح فلا استرجاع مدمِّر.
     *
     * وهذا هو النصف الذي كان صحيحًا ولا يجوز كسره: الاسترجاع عند كل دورة انحراف كان يمحو التحرير
     * نفسه ويضمن ألّا يقع تغيير أبدًا. فلا خفقان، ولا ادّعاء كتابة لم تبلغ.
     */
    @Test
    fun `a platform that holds a lower ceiling is attempted once and never rolled back`() {
        val arbiter = HardwareControlArbiter()
        var live = "754000000"
        var writes = 0

        val result = arbiter.submit(
            key = "gpu_frequency:13000000.mali",
            owner = ControlOwnership.Owner.PER_APP,
            token = "per-app:com.example",
            desired = "1092000000",
            // نواة/مُلطِّف يعيد ٧٥٤: الكتابة تُقبل ثم تُقصّ.
            apply = { writes += 1; live = "754000000"; true },
            read = { live },
            baseline = "754000000",
            restore = { value -> live = value; true },
            verify = HardwareVerification::ceilingAtMost,
            realized = HardwareVerification::ceilingReached,
        )

        assertEquals("محاولة واحدة — لا تخطّي لأن السقف أدنى من الطلب", 1, writes)
        assertTrue("والحكم المتسامح يبقى: لا استرجاع يمحو ما فُتح", result.verified)
        assertFalse(result.rollbackAttempted)
        assertEquals("754000000", live)
    }

    /** والسجل يُمرّر الدليل كما يُمرّر الحكم: من `ownValue` إلى المعاملة إلى المُحكِّم. */
    @Test
    fun `the registry hands the proof to the arbiter`() {
        var live = "520000000"
        val registry = PerAppControlRegistry(
            mutationGate = HardwareControlArbiter(),
            confirmationSamples = 3,
            confirmationIntervalMs = 0L,
            sleep = {},
        )

        val owned = registry.ownValue(
            key = "gpu_frequency:13000000.mali",
            desired = "702000000",
            apply = { value -> live = value; true },
            read = { live },
            baseline = "520000000",
            restore = { value -> live = value; true },
            verify = HardwareVerification::ceilingAtMost,
            realized = HardwareVerification::ceilingReached,
        )

        assertTrue(owned)
        assertEquals("702000000", live)
        assertEquals(mapOf("gpu_frequency:13000000.mali" to "702000000"), registry.ownedDesired())
        assertTrue(
            "ودورة الانحراف لا تجد ما تصلحه: المقبض يقرأ ما طُلب منه",
            registry.verifyAndRepair().single().let { it.successful && it.driftedBefore == false },
        )
    }
}
