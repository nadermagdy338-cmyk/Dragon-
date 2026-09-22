package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * تسجيل كتابة من جهاز حقيقي — يُعاد في اختبار JVM بلا جهاز.
 *
 * المصدر: `EVENT=WRITE_CHECK` في حزمة المالك (rodin · MT6899 · Android 16 · 2026-09-22)، والسطور
 * أدناه مأخوذة حرفيًّا (والوقت في `at` هو وقت السطر في الحزمة، ليربط القارئ كل قيمة بمصدرها).
 *
 * ولماذا هذا ليس `FakeIo` آخر: `FakeIo` يقيس **افتراض مؤلفه** عن الجهاز؛ وهذا يقيس **أجوبة الجهاز**
 * نفسها، ويُظهر في العدّ ما ليس مسجَّلًا:
 *
 * | العدّاد | معناه |
 * | --- | --- |
 * | `replayed` | كتابةٌ سطرُها في التسجيل — شهادةُ الجهاز |
 * | `inferred` | كتابةٌ قيمتها من الترددات المرصودة ولم تُسجَّل — نمذجة معلنة، لا شهادة |
 * | `refused` | كتابةٌ لا تسجيلَ لها ولا رصد ⇒ لا تُنفَّذ |
 *
 * والقيم المرصودة على عقدة `mali/max_freq` في الحزمة: `260000000` · `416000000` · `520000000` ·
 * `780000000` · `1092000000` · `1300000000` — كلها `verdict=matched` (الجهاز يردّ ما كُتب له)،
 * و**`702000000` لا سطر كتابة له في الحزمة كلها** رغم أن الجهاز عرضه في جدوله (`PERAPP_GPU_REALIZED
 * … target=702000000`). وهذا بالضبط ما يقيسه `inferred` في الاختبار الثالث.
 */
class RecordedDeviceWriteTest {

    @Before
    fun configureSharedOwner() {
        val root = Files.createTempDirectory("recorded-write-test").toFile()
        SharedHardwareOwnershipStore.configure(
            root,
            appUid = 0,
            processId = ProcessHandle.current().pid().toInt(),
        )
        ManualControlLocks.configure(root)
        ManualControlLocks.clearAll()
        ControlOwnership.snapshot().forEach { ControlOwnership.release(it.key) }
    }

    /** الجهاز كما رأيناه: سقفه الحيّ ٥٢٠ في بداية الجلسة المقيسة (بروفايل «power»). */
    private fun rodin(): DeviceWriteRecording = DeviceWriteRecording(
        origin = DeviceRecordingOrigin.DEVICE,
        build = "rodin · MT6899 · Android 16 · 5.2 (137) · 2026-09-22",
        device = RecordedGpuDevice(
            path = "/sys/class/devfreq/13000000.mali",
            name = "13000000.mali",
            advertisedMaxHz = 1_300_000_000L,
            liveMaxHz = 520_000_000L,
            liveMinHz = 260_000_000L,
            // الحزمة نفسها تقول `current=none` على هذه العقدة: لا تردد جارٍ مقروء، ولا يُخترع.
            currentHz = null,
            observedFrequencies = listOf(
                260_000_000L,
                416_000_000L,
                520_000_000L,
                702_000_000L,
                780_000_000L,
                1_092_000_000L,
                1_300_000_000L,
            ),
            governor = "dummy",
            governors = listOf("dummy"),
            fixIndexPath = "/proc/gpufreqv2/fix_target_opp_index",
            fixIndexEcho = "[GPUFREQ-DEBUG] fix GPU/STACK OPP index is disabled",
        ),
        writes = listOf(
            write("/sys/class/devfreq/13000000.mali/governor", "dummy", "dummy", "20:37:37.644"),
            write("/sys/class/devfreq/13000000.mali/min_freq", "260000000", "260000000", "20:37:03.700"),
            write("/sys/class/devfreq/13000000.mali/max_freq", "520000000", "520000000", "20:37:03.797"),
            write("/sys/class/devfreq/13000000.mali/min_freq", "416000000", "416000000", "22:44:59.513"),
            write("/sys/class/devfreq/13000000.mali/max_freq", "780000000", "780000000", "22:46:49.560"),
            write("/sys/class/devfreq/13000000.mali/max_freq", "1092000000", "1092000000", "22:46:20.474"),
            write("/sys/class/devfreq/13000000.mali/max_freq", "1300000000", "1300000000", "22:46:00.071"),
            // وصدى عقدة القفل: الجهاز لا يردّ الرقم بل نصّ تشخيص — ولذلك `verdict=differs` عليه،
            // وقراءة الفهرس تُستخرج منه بـ[MtkGpuOppTable.parseIndex] لا بالتساوي الحرفي.
            write(
                "/proc/gpufreqv2/fix_target_opp_index",
                "-1",
                "[GPUFREQ-DEBUG] fix GPU/STACK OPP index is disabled",
                "20:37:37.532",
                RecordedWrite.Verdict.DIFFERS,
            ),
            write(
                "/proc/gpufreqv2/fix_target_opp_index",
                "30",
                "[GPUFREQ-DEBUG] fix GPU/STACK OPP index: 30/30",
                "21:55:27.683",
                RecordedWrite.Verdict.DIFFERS,
            ),
        ),
        extraReads = mapOf(
            // عقدة سقف GED قرأتها الحزمة بنفس النصّ: `wrote=0 read=0 …pid… ori:0 value:0`.
            "/sys/kernel/ged/hal/custom_upbound_gpu_freq" to
                "0 4917291448102:pid:9446 ori:0 value:0 user_id:0(nd.max:root:0)",
        ),
    )

    private fun write(
        path: String,
        wrote: String,
        read: String,
        at: String,
        verdict: RecordedWrite.Verdict = RecordedWrite.Verdict.MATCHED,
    ) = RecordedWrite(path, wrote, read, verdict, at)

    /** ١ · الجهاز يشهد لكتابةٍ سُجّلت: القيمة تُكتب ويُقرأ صدى الجهاز نفسه. */
    @Test
    fun `a recorded write is replayed and echoed by the node`() {
        val recording = rodin()
        val io = RecordingIo(recording, modelObservedFrequencies = false)
        val device = recording.device.toDevice()

        val result = GpuHardwareBackend.applyValidated(
            device,
            GpuHardwareBackend.Request(minFreq = 260_000_000L, maxFreq = 780_000_000L, releaseVendorCeiling = false),
            io,
        )

        assertTrue(result.verified)
        assertEquals("كلتا الكتابتين في التسجيل ⇒ شهادة الجهاز", 2, io.replayed)
        assertEquals("ولا نمذجة ولا رفض", 0, io.inferred)
        assertEquals(0, io.refused)
        assertEquals("780000000", io.value("${device.path}/max_freq"))
    }

    /**
     * ٢ · العطب المقيس نفسه، على أسطر الحزمة: طلب ٧٠٢ على سقف ٥٢٠ كتبناه.
     *
     * بدون دليل الكتابة (`realized`) يمرّ الطلب **بصفر كتابة** ويُعلن نجاحًا — وهي الأسطر ٢٤ في
     * الحزمة (`PERAPP_COMMIT … requested=1092000000 … verified=true live=520000000` في واتساب
     * وتشات‌جي‌بي‌تي وغيرها). والاختبار يثبّت أن المُحكِّم القديم يفعل هذا على جهاز مسجَّل أيضًا.
     */
    @Test
    fun `the recorded device keeps the lowered ceiling when the request carries no proof`() {
        val recording = rodin()
        val io = RecordingIo(recording)
        val device = recording.device.toDevice()
        val arbiter = HardwareControlArbiter()

        val result = arbiter.submit(
            key = HardwareControlKey.gpuFrequency(device.name),
            owner = ControlOwnership.Owner.PER_APP,
            token = "per-app:com.example",
            desired = "702000000",
            apply = { raise(device, io, 702_000_000L) },
            read = { GpuHardwareBackend.refresh(device.path, io)?.maxFreq?.toString() },
            baseline = "520000000",
            restore = { value ->
                value.toLongOrNull()?.let { restore(device, io, it) } ?: false
            },
            verify = HardwareVerification::ceilingAtMost,
        )

        assertTrue("القيمة الأدنى من الطلب كانت تُقرأ «مُلبّاة»", result.verified)
        assertTrue("وبلا كتابة على العقدة أصلًا", io.applied.isEmpty())
        assertEquals("والسقف يبقى ٥٢٠ كما تركه خفضنا", "520000000", io.value("${device.path}/max_freq"))
    }

    /** ٣ · وبدليل الكتابة: الرفع يُكتب فعلًا، والعدّاد يقول أيّ شهادة وأيّ نمذجة. */
    @Test
    fun `the same raise is written once the request carries the proof`() {
        val recording = rodin()
        val io = RecordingIo(recording)
        val device = recording.device.toDevice()
        val arbiter = HardwareControlArbiter()

        val result = arbiter.submit(
            key = HardwareControlKey.gpuFrequency(device.name),
            owner = ControlOwnership.Owner.PER_APP,
            token = "per-app:com.example",
            desired = "702000000",
            apply = { raise(device, io, 702_000_000L) },
            read = { GpuHardwareBackend.refresh(device.path, io)?.maxFreq?.toString() },
            baseline = "520000000",
            restore = { value ->
                value.toLongOrNull()?.let { restore(device, io, it) } ?: false
            },
            verify = HardwareVerification::ceilingAtMost,
            realized = HardwareVerification::ceilingReached,
        )

        assertTrue(result.verified)
        assertEquals("702000000", io.value("${device.path}/max_freq"))
        assertTrue(
            "الكتابة وقعت على عقدة السقف",
            io.applied.any { it.first.endsWith("/max_freq") && it.second == "702000000" },
        )
        assertEquals(
            "والتسجيل لا يشهد لها: ٧٠٢ لم يُكتب في الحزمة أبدًا — وهو العطب؛ فالنمذجة تُعدّ وحدها",
            1,
            io.inferred,
        )
        assertEquals(0, io.refused)
    }

    /**
     * ٤ · وبنفس الطلب في **إعادة صارمة** (بلا نمذجة): الكتابة تُرفض ولا تُنفَّذ.
     *
     * وهذا حرس على الأمانة: التسجيل لا يشهد لقيمة لم يرها، فالاختبار الذي يمرّ هنا كان سيقيس
     * جهازًا لم نُسجّله. والنتيجة الفاشلة هي الجواب الصحيح.
     */
    @Test
    fun `a strict replay refuses a value the recording never saw`() {
        val recording = rodin()
        val io = RecordingIo(recording, modelObservedFrequencies = false)
        val device = recording.device.toDevice()

        val result = GpuHardwareBackend.applyValidated(
            device,
            GpuHardwareBackend.Request(minFreq = 260_000_000L, maxFreq = 702_000_000L, releaseVendorCeiling = false),
            io,
        )

        assertFalse("لا شهادة ⇒ لا نجاح", result.verified)
        assertEquals("والرفض يُعدّ ولا يُسكَت عنه", 1, io.refused)
        assertEquals("ولم يُنفَّذ على العقدة", null, io.applied.firstOrNull { it.second == "702000000" })
    }

    /** ٥ · وصدى عقدة القفل يُقرأ كفهرس — قياس لا تخمين. */
    @Test
    fun `the recorded index echo is parsed as the lock index`() {
        assertEquals("30", MtkGpuOppTable.parseIndex("[GPUFREQ-DEBUG] fix GPU/STACK OPP index: 30/30"))
        assertEquals("-1", MtkGpuOppTable.parseIndex("[GPUFREQ-DEBUG] fix GPU/STACK OPP index is disabled"))
        assertEquals("0", MtkGpuOppTable.parseIndex("[GPUFREQ-DEBUG] fix GPU/STACK OPP index: 0/0"))
    }

    // ── إغلاقات الكتابة/الاسترجاع: نفس شكل ما يفعله مسار per-app ────────────────────────────────

    /** رفع السقف: مدى كامل من أدنى درجة معلنة إلى المطلوب، عبر التطبيق المُتحقَّق نفسه. */
    private fun raise(device: GpuHardwareBackend.Device, io: GpuHardwareBackend.Io, hz: Long): Boolean {
        val live = GpuHardwareBackend.refresh(device.path, io) ?: return false
        val low = live.frequencies.firstOrNull { it <= hz } ?: return false
        return GpuHardwareBackend.applyValidated(
            live,
            GpuHardwareBackend.Request(minFreq = low, maxFreq = hz, releaseVendorCeiling = false),
            io,
        ).verified
    }

    /** والاسترجاع نفس المسار بالقيمة التي يطلبها المُحكِّم من خط الأساس. */
    private fun restore(device: GpuHardwareBackend.Device, io: GpuHardwareBackend.Io, hz: Long): Boolean =
        raise(device, io, hz)
}
