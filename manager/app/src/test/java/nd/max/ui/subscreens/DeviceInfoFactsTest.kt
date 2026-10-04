package nd.max.ui.subscreens

import nd.max.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قياس رأس القسم وبطاقاته وشرائحه (تكملة ٢٢٥) — القواعد التي لا تُرى في الشاشة:
 * المقياس يُبنى من قراءتين لا من رقمٍ واحد، والبلاطة بلا قراءة تحمل ثقةً لا صفرًا،
 * والبطاقة الفارغة تُحذف لا تُعرض عنوانًا تحته لا شيء.
 */
class DeviceInfoFactsTest {

    // ── مقاييس الرأس: الكسر لا يُبنى من طرفٍ واحد ────────────────────────────

    @Test
    fun `a cpu gauge needs both the reading and its ceiling`() {
        // تردّد بلا سقف مُعلن = كسرٌ بلا مقام — فلا قوس على تخمين.
        val blind = heroOf(DeviceInfoSection.Cpu, DeviceInfoSnapshot(cpuFreqMhz = 2400))
        assertTrue("مقياس بلا سقف", blind.gauges.isEmpty())

        val gauge = heroOf(
            DeviceInfoSection.Cpu,
            DeviceInfoSnapshot(cpuFreqMhz = 2400, cpuCeilingMhz = 3350),
        ).gauges.single()
        assertEquals(2400f / 3350f, gauge.fraction, 1e-4f)
        assertEquals("2.40", gauge.valueText)
        assertEquals("GHz", gauge.unitText)
        assertTrue(gauge.live)
    }

    @Test
    fun `a memory gauge needs both sides, and overshoot is clamped to full`() {
        assertTrue(
            "مقياس بلا مستخدم",
            heroOf(DeviceInfoSection.Memory, DeviceInfoSnapshot(ramTotalMb = 8192)).gauges.isEmpty(),
        )
        val gauge = heroOf(
            DeviceInfoSection.Memory,
            DeviceInfoSnapshot(ramUsedMb = 5300, ramTotalMb = 8192),
        ).gauges.single()
        assertEquals(5300f / 8192f, gauge.fraction, 1e-4f)

        // قراءة أعلى من الكل تُقيَّد بواحد — ولا يُرسم قوسٌ يتجاوز الدائرة.
        val overshoot = heroOf(
            DeviceInfoSection.Memory,
            DeviceInfoSnapshot(ramUsedMb = 9000, ramTotalMb = 8192),
        ).gauges.single()
        assertEquals(1f, overshoot.fraction, 1e-4f)
    }

    @Test
    fun `the storage gauge is the memory gauge with the same rule and no liveness`() {
        val gauge = heroOf(
            DeviceInfoSection.Storage,
            DeviceInfoSnapshot(storageUsedGb = 44f, storageTotalGb = 256f),
        ).gauges.single()
        assertEquals(44f / 256f, gauge.fraction, 1e-4f)
        // التخزين لا ينبض: قراءةُ لحظةٍ واحدة فالحية زينة لا حقيقة.
        assertTrue("تخزين مُعلَن حيًّا", !gauge.live)
    }

    @Test
    fun `the gpu gauge prefers the OPP ceiling and falls back to the node ceiling`() {
        val nodeOnly = heroOf(
            DeviceInfoSection.Gpu,
            DeviceInfoSnapshot(gpuFreqMhz = 260, gpuCeilingMhz = 754),
        ).gauges.single()
        assertEquals(260f / 754f, nodeOnly.fraction, 1e-4f)

        val opp = heroOf(
            DeviceInfoSection.Gpu,
            DeviceInfoSnapshot(gpuFreqMhz = 260, gpuCeilingMhz = 754, gpuMaxSupportedMhz = 1400),
        ).gauges.single()
        assertEquals(260f / 1400f, opp.fraction, 1e-4f)
    }

    @Test
    fun `the battery gauge draws from zero to full, and zero is a reading`() {
        // صفر البطارية قراءةٌ حقيقية (فراغ) فالقوس يُرسم حتى الصفر — والصفر هنا ليس غيابًا.
        val empty = heroOf(DeviceInfoSection.Overview, DeviceInfoSnapshot(batteryPercent = 0))
            .gauges.single()
        assertEquals(0f, empty.fraction, 1e-4f)
        assertTrue(empty.live)

        // ونسبةٌ خارج المئة ليست نسبة، وغيابُها غيابُ مقياس.
        assertTrue(
            "مقياس على نسبة فاسدة",
            heroOf(DeviceInfoSection.Overview, DeviceInfoSnapshot(batteryPercent = 150)).gauges.isEmpty(),
        )
        assertTrue(
            "مقياس بلا بطارية",
            heroOf(DeviceInfoSection.Overview, DeviceInfoSnapshot()).gauges.isEmpty(),
        )
    }

    // ── البلاطات: قيمة أو ثقة، ولا صفر بدلًا عن الغياب ───────────────────────

    @Test
    fun `the overview hero titles itself with the device identity`() {
        val hero = heroOf(
            DeviceInfoSection.Overview,
            DeviceInfoSnapshot(deviceName = "POCO X6 Pro", model = "2311DRK48G", chipset = "Dimensity 8300"),
        )
        assertEquals("POCO X6 Pro", hero.title)
        assertEquals("2311DRK48G · Dimensity 8300", hero.subtitle)

        // وطرازٌ فارغ لا يترك فاصلةً معلّقة في السطر.
        val blank = heroOf(
            DeviceInfoSection.Overview,
            DeviceInfoSnapshot(deviceName = "POCO X6 Pro", model = "  ", chipset = "Dimensity 8300"),
        )
        assertEquals("Dimensity 8300", blank.subtitle)
    }

    @Test
    fun `an unread tile carries its trust and prints no zero`() {
        val hero = heroOf(DeviceInfoSection.Network, DeviceInfoSnapshot())
        val adb = hero.tiles.first { it.captionRes == R.string.devinfo_adb }
        assertNull("قيمة مخترعة لعدم قراءة", adb.value)
        assertNull(adb.valueRes)
        assertEquals(DeviceInfoTrust.Unreadable, adb.trust)

        // وعدّ الكاميرات بلا كاميرا: لا صفر عدسات — «لم يُقرأ».
        val camera = heroOf(DeviceInfoSection.Camera, DeviceInfoSnapshot()).tiles
            .first { it.captionRes == R.string.devinfo_camera_count }
        assertNull(camera.value)
        assertEquals(DeviceInfoTrust.Unreadable, camera.trust)
    }

    @Test
    fun `a negative current is a reading and keeps its sign`() {
        // التيار السالب تفريغٌ حقيقيّ — فلو صُنّف «عدم قراءة» لاختفى أهم سطر في البطارية.
        val tile = heroOf(DeviceInfoSection.Battery, DeviceInfoSnapshot(batteryCurrentMa = -500))
            .tiles.first { it.captionRes == R.string.devinfo_battery_current }
        assertEquals("-500", tile.value)
        assertEquals(DeviceInfoTrust.Live, tile.trust)
    }

    @Test
    fun `a state tile translates its value instead of printing a latin word`() {
        val on = heroOf(DeviceInfoSection.Network, DeviceInfoSnapshot(adbEnabled = true))
            .tiles.first { it.captionRes == R.string.devinfo_adb }
        assertEquals(R.string.devinfo_state_on, on.valueRes)

        val off = heroOf(DeviceInfoSection.Network, DeviceInfoSnapshot(adbEnabled = false))
            .tiles.first { it.captionRes == R.string.devinfo_adb }
        assertEquals(R.string.devinfo_state_off, off.valueRes)
    }

    // ── الشرائح: فهرس الملكية في الشبكة وحدها ─────────────────────────────

    @Test
    fun `the capability index lives in the network section alone`() {
        val chips = listOf(
            DeviceInfoChipRow(R.string.devinfo_cap_nfc, CapabilityState.Supported),
            DeviceInfoChipRow(R.string.devinfo_cap_uwb, CapabilityState.NotDeclared),
        )
        val s = DeviceInfoSnapshot(capabilities = chips)
        assertEquals(chips, chipsOf(DeviceInfoSection.Network, s))
        // فهرس «ماذا يملك جهازك» واحد — وتكراره في كل قسم ضجيج لا معلومات.
        assertTrue(chipsOf(DeviceInfoSection.Cpu, s).isEmpty())
        assertTrue(chipsOf(DeviceInfoSection.Camera, s).isEmpty())
    }

    // ── بطاقة DRM: تُحذف إن لم تُقرأ، ولـ«لا مخرج» سطرها ─────────────────────

    @Test
    fun `a drm card with nothing read is dropped entirely`() {
        assertTrue(cardsOf(DeviceInfoSection.System, DeviceInfoSnapshot()).isEmpty())

        val card = cardsOf(DeviceInfoSection.System, DeviceInfoSnapshot(drmSecurityLevel = "L1")).single()
        assertEquals(R.string.devinfo_drm_title, card.titleRes)
    }

    @Test
    fun `no digital output is a declared state with its own line`() {
        // `-1` في MediaDrm حالةٌ معلنة لا مستوى ولا غياب — فتبقى البطاقة وعليها سطرُها.
        val card = cardsOf(DeviceInfoSection.System, DeviceInfoSnapshot(drmNoDigitalOutput = true)).single()
        val hdcp = card.facts.first { it.label == R.string.devinfo_drm_hdcp }
        assertEquals(DeviceInfoTrust.Unsupported, hdcp.trust)
        assertEquals(R.string.devinfo_no_digital_output, hdcp.noteRes)
    }

    // ── بطاقات الكاميرا: عدسةٌ لكل بطاقة، والغائب «غير مقروء» ────────────────

    @Test
    fun `one camera card per lens, titled by its facing`() {
        val cards = cardsOf(
            DeviceInfoSection.Camera,
            DeviceInfoSnapshot(
                cameras = listOf(
                    DeviceCameraInfo(facing = CameraFacing.Back),
                    DeviceCameraInfo(facing = CameraFacing.Front),
                ),
            ),
        )
        assertEquals(2, cards.size)
        assertEquals(R.string.devinfo_camera_rear, cards[0].titleRes)
        assertEquals(R.string.devinfo_camera_front, cards[1].titleRes)
    }

    @Test
    fun `a lens field that was not read stays unread`() {
        val blind = cardsOf(
            DeviceInfoSection.Camera,
            DeviceInfoSnapshot(cameras = listOf(DeviceCameraInfo(facing = CameraFacing.Back))),
        ).single()
        val aperture = blind.facts.first { it.label == R.string.devinfo_camera_aperture }
        assertNull("فتحة مخترعة لعدسٍ أعمى", aperture.value)
        assertEquals(DeviceInfoTrust.Unreadable, aperture.trust)

        // والعدسة المُعلنة تُرجم فتحتُها كما هي بلا تقريب.
        val seeing = cardsOf(
            DeviceInfoSection.Camera,
            DeviceInfoSnapshot(
                cameras = listOf(DeviceCameraInfo(facing = CameraFacing.Back, apertures = listOf(1.7f))),
            ),
        ).single()
        assertEquals("f/1.7", seeing.facts.first { it.label == R.string.devinfo_camera_aperture }.value)
    }

    @Test
    fun `the camera list holds the count only, details live in the cards`() {
        val facts = factsOf(
            DeviceInfoSection.Camera,
            DeviceInfoSnapshot(cameras = listOf(DeviceCameraInfo(facing = CameraFacing.Back), DeviceCameraInfo(facing = CameraFacing.Front))),
        )
        assertEquals(1, facts.size)
        assertEquals("2", facts.single().value)
        assertTrue(
            "صفر عدسات مطبوع كقيمة",
            factsOf(DeviceInfoSection.Camera, DeviceInfoSnapshot()).single().value == null,
        )
    }

    // ── الحقول الثلاثية والعدّادات ─────────────────────────────────────────

    @Test
    fun `a state fact is three-valued, not two`() {
        val off = factsOf(DeviceInfoSection.Network, DeviceInfoSnapshot(adbEnabled = false))
            .first { it.label == R.string.devinfo_adb }
        assertEquals(R.string.devinfo_state_off, off.valueRes)
        assertEquals(DeviceInfoTrust.Snapshot, off.trust)

        // و«لم يُقرأ» ليس «مُعطّل» — فلا يُدّعى تعطيلٌ لم يُقَس.
        val unknown = factsOf(DeviceInfoSection.Network, DeviceInfoSnapshot())
            .first { it.label == R.string.devinfo_adb }
        assertNull(unknown.valueRes)
        assertEquals(DeviceInfoTrust.Unreadable, unknown.trust)
    }

    @Test
    fun `a zero charge counter is a reading, a negative one is not`() {
        val drained = factsOf(DeviceInfoSection.Battery, DeviceInfoSnapshot(batteryChargeCounterMah = 0))
            .first { it.label == R.string.devinfo_battery_charge_counter }
        assertEquals("0", drained.value)
        assertEquals(DeviceInfoTrust.Live, drained.trust)

        val broken = factsOf(DeviceInfoSection.Battery, DeviceInfoSnapshot(batteryChargeCounterMah = -5))
            .first { it.label == R.string.devinfo_battery_charge_counter }
        assertNull(broken.value)
        assertEquals(DeviceInfoTrust.Unreadable, broken.trust)

        val design = factsOf(DeviceInfoSection.Battery, DeviceInfoSnapshot(batteryDesignCapacityMah = 5000))
            .first { it.label == R.string.devinfo_battery_design_capacity }
        assertEquals("5000", design.value)
    }

    @Test
    fun `health words follow the platform code and never a guess`() {
        val good = factsOf(DeviceInfoSection.Battery, DeviceInfoSnapshot(batteryHealthCode = 2))
            .first { it.label == R.string.devinfo_battery_health_label }
        assertEquals(R.string.devinfo_battery_health_good, good.valueRes)
        assertEquals(DeviceInfoTrust.Snapshot, good.trust)

        // ورمزٌ لا تعرفه المنصّة لا يُحوَّل إلى كلمة.
        val unknown = factsOf(DeviceInfoSection.Battery, DeviceInfoSnapshot(batteryHealthCode = 99))
            .first { it.label == R.string.devinfo_battery_health_label }
        assertNull(unknown.valueRes)
        assertEquals(DeviceInfoTrust.Unreadable, unknown.trust)
    }
}
