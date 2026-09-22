package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قرار تنفيذ سقف GPU — مُختبر **بلا جهاز**، لأن الحالة التي أوجبت هذا الملف مقيسة من جهاز حقيقي
 * ولا يجوز أن تبقى بلا اختبار انحدار:
 *
 * ```
 * PERAPP_GPU_CAPABILITY_SCAN … advertised_max=1300000000 live_max=1300000000 current=260000000
 * PERAPP_KNOB knob=gpu_profile outcome=applied reason=verified expected=1300000000 live=1300000000
 * ```
 *
 * أي: `max_freq` يقرأ أعلى درجة عند الجهاز أصلًا ⇒ الحاكم يحكم «مُلبّى» ويتخطّى `apply` ⇒ ٧٥ جلسة
 * «أداء» بصفر كتابة على أي عقدة GPU. فالاختبارات هنا تُثبّت الحدّين معًا: أن طلب القدرة **تحرير**
 * لا كتابة، وأن «مُلبّى» لا تُقال إلا بعد أن يزول ما يقصّ فعلًا.
 */
class GpuCeilingPolicyTest {

    // ── شكل التنفيذ ──────────────────────────────────────────────────────────

    @Test
    fun `a request at the advertised maximum is a release and never a frequency write`() {
        // حالة الجهاز المقيس: القدرة ١٣٠٠، و`max_freq` يقرأها أصلًا ⇒ لا شيء يُكتب.
        assertEquals(
            GpuCeilingPolicy.Realization.RELEASE_ONLY,
            GpuCeilingPolicy.realize(
                requestedHz = 1_300_000_000L,
                advertisedMaxHz = 1_300_000_000L,
                rangeWritable = true,
                pinAvailable = true,
            ),
        )
        // والأهم: على جهاز لا يقبل إلا تثبيت OPP (MTK) يبقى **تحريرًا** — لا تُثبَّت درجة عند
        // قدرة الجهاز، لأن التثبيت يجمّد التردد بدل أن يطلقه.
        assertEquals(
            GpuCeilingPolicy.Realization.RELEASE_ONLY,
            GpuCeilingPolicy.realize(
                requestedHz = 1_300_000_000L,
                advertisedMaxHz = 1_300_000_000L,
                rangeWritable = false,
                pinAvailable = true,
            ),
        )
    }

    @Test
    fun `a ceiling below capability uses the range when the device accepts one`() {
        assertEquals(
            GpuCeilingPolicy.Realization.RANGE,
            GpuCeilingPolicy.realize(700_000_000L, 1_300_000_000L, rangeWritable = true, pinAvailable = true),
        )
    }

    @Test
    fun `a pin is used only when no range is writable and a pin exists`() {
        assertEquals(
            GpuCeilingPolicy.Realization.PIN,
            GpuCeilingPolicy.realize(700_000_000L, 1_300_000_000L, rangeWritable = false, pinAvailable = true),
        )
        assertEquals(
            GpuCeilingPolicy.Realization.UNSUPPORTED,
            GpuCeilingPolicy.realize(700_000_000L, 1_300_000_000L, rangeWritable = false, pinAvailable = false),
        )
        assertEquals(
            GpuCeilingPolicy.Realization.UNSUPPORTED,
            GpuCeilingPolicy.realize(null, 1_300_000_000L, rangeWritable = true, pinAvailable = true),
        )
    }

    @Test
    fun `an unknown capability can never be treated as a release`() {
        // بلا قدرة معلنة لا نعرف أن الطلب «عند القدرة»، فلا يُدّعى تحرير. والمخرج الوحيد المتاح
        // هو مسار الكتابة إن قبله الجهاز، وإلا فعدم الدعم صراحةً.
        assertEquals(
            GpuCeilingPolicy.Realization.RANGE,
            GpuCeilingPolicy.realize(700_000_000L, null, rangeWritable = true, pinAvailable = true),
        )
    }

    // ── القراءة والحكم ───────────────────────────────────────────────────────

    @Test
    fun `the measured device state does not satisfy the request until the platform cap is gone`() {
        // ما قرأته الحزمة: سقف العقدة عند القدرة، وسقف منصّة مخصّص قائم ⇒ لم تُلبَّ بعد.
        val held = GpuCeilingPolicy.CeilingReading(
            nodeCeilingHz = 1_300_000_000L,
            platformUpbound = 754_000_000L,
            platformCoolingHeld = false,
        )
        assertFalse(
            "سقف منصّة غير صفري يعني أن أحدًا يقصّ، فلا يُدّعى تلبية",
            GpuCeilingPolicy.ceilingSatisfied(1_300_000_000L, held),
        )
        assertEquals("gpu-ceiling-held", GpuCeilingPolicy.ceilingReason(1_300_000_000L, held))

        // وبعد التحرير (صفر = بلا سقف مخصّص) يُصدَّق الطلب.
        val released = held.copy(platformUpbound = 0L)
        assertTrue(GpuCeilingPolicy.ceilingSatisfied(1_300_000_000L, released))
        assertEquals("gpu-ceiling-released", GpuCeilingPolicy.ceilingReason(1_300_000_000L, released))

        // ورفع حالة تبريد GPU يقصّ أيضًا وإن لم يُقرأ مقداره.
        assertFalse(GpuCeilingPolicy.ceilingSatisfied(1_300_000_000L, released.copy(platformCoolingHeld = true)))
    }

    @Test
    fun `a capability request is not satisfied while the device still caps below its top step`() {
        // الحالة المقيسة بالحرف: أعلى درجة معلنة ١٣٠٠ وسقف الجهاز ٧٥٤. القاعدة الوحيدة كانت
        // `node ≤ desired`، وهي تقول «نُفِّذ» لطلب القدرة بينما الجهاز مخنوق — فيُقال للمستخدم
        // «طُبِّق» وهو لا يرى فرقًا. والاتجاه الآن يتبع نوع الطلب.
        val capped = GpuCeilingPolicy.CeilingReading(754_000_000L, 0L, false, lockActive = false)
        assertFalse(
            GpuCeilingPolicy.ceilingSatisfied(1_300_000_000L, capped, capabilityHz = 1_300_000_000L),
        )
        assertEquals("gpu-ceiling-held", GpuCeilingPolicy.ceilingReason(1_300_000_000L, capped, 1_300_000_000L))

        // وبعد أن يزول كل سقف دون القدرة يُصدَّق الطلب.
        assertTrue(
            GpuCeilingPolicy.ceilingSatisfied(
                1_300_000_000L,
                capped.copy(nodeCeilingHz = 1_300_000_000L),
                capabilityHz = 1_300_000_000L,
            ),
        )

        // وطلبُ تبريد (أدنى من القدرة) يبقى يُلبَّى بالسقف الأدنى نفسه.
        assertTrue(GpuCeilingPolicy.ceilingSatisfied(754_000_000L, capped, capabilityHz = 1_300_000_000L))
        assertFalse(GpuCeilingPolicy.ceilingSatisfied(650_000_000L, capped, capabilityHz = 1_300_000_000L))
    }

    @Test
    fun `a node ceiling above the request is not a satisfied request`() {
        val reading = GpuCeilingPolicy.CeilingReading(
            nodeCeilingHz = 1_300_000_000L,
            platformUpbound = 0L,
            platformCoolingHeld = false,
        )
        assertFalse(GpuCeilingPolicy.ceilingSatisfied(700_000_000L, reading))
    }

    @Test
    fun `a held fixed-opp lock is never a released ceiling`() {
        // العطب الذي لا تظهره أي قراءة تردد: الفهرس الثابت (`fix_target_opp_index`) يقصّ من **خارج**
        // `devfreq`، فيقرأ `max_freq` القدرة الكاملة بينما الجهاز مجمَّد على درجة واحدة — وهي الحالة
        // المقيسة («أداء» يعطي ٦٥٠ وقفلُ جلسة سابقة باقٍ).
        val pinned = GpuCeilingPolicy.CeilingReading(
            nodeCeilingHz = 1_300_000_000L,
            platformUpbound = 0L,
            platformCoolingHeld = false,
            lockActive = true,
        )
        assertFalse(
            "قفل OPP قائم يعني أن الجهاز لا يتوسّع، فقراءة السقف لا تصدّقه",
            GpuCeilingPolicy.ceilingSatisfied(1_300_000_000L, pinned),
        )
        assertEquals("gpu-opp-lock-held", GpuCeilingPolicy.ceilingReason(1_300_000_000L, pinned))

        // وبعد رفع القفل يُصدَّق الطلب، وعلى جهاز لا مسار قفل فيه (`null`) لا يُدَّعى قفل أصلًا.
        assertTrue(GpuCeilingPolicy.ceilingSatisfied(1_300_000_000L, pinned.copy(lockActive = false)))
        assertTrue(GpuCeilingPolicy.ceilingSatisfied(1_300_000_000L, pinned.copy(lockActive = null)))
    }

    @Test
    fun `an unmeasured ceiling is never reported as satisfied`() {
        val unreadable = GpuCeilingPolicy.CeilingReading(
            nodeCeilingHz = null,
            platformUpbound = 0L,
            platformCoolingHeld = false,
        )
        assertFalse("لم أقِس ليست نجحت", GpuCeilingPolicy.ceilingSatisfied(1_300_000_000L, unreadable))
        assertFalse(GpuCeilingPolicy.ceilingSatisfied(1_300_000_000L, null))
        assertEquals("gpu-node-ceiling-unreadable", GpuCeilingPolicy.ceilingReason(1_300_000_000L, unreadable))
        assertEquals("gpu-node-ceiling-unreadable", GpuCeilingPolicy.ceilingReason(1_300_000_000L, null))
    }

    @Test
    fun `a reading survives its own token round trip`() {
        // المُحكِّم يقارن نصًّا بنصّ (المقروء/خط الأساس)، فالصيغة تُكتب وتُقرأ في ملف واحد.
        val cases = listOf(
            GpuCeilingPolicy.CeilingReading(1_300_000_000L, 0L, false),
            GpuCeilingPolicy.CeilingReading(1_300_000_000L, 754_000_000L, true),
            GpuCeilingPolicy.CeilingReading(null, null, null),
            GpuCeilingPolicy.CeilingReading(1_300_000_000L, null, false),
            GpuCeilingPolicy.CeilingReading(1_300_000_000L, 0L, false, lockActive = true),
            GpuCeilingPolicy.CeilingReading(1_300_000_000L, 0L, false, lockActive = false),
        )
        cases.forEach { reading ->
            assertEquals(reading, GpuCeilingPolicy.CeilingReading.parse(reading.token))
        }
        assertNull(GpuCeilingPolicy.CeilingReading.parse(null))
        assertNull("صيغة لا نعرفها لا تُفسَّر", GpuCeilingPolicy.CeilingReading.parse("1300000000"))
        assertNull(GpuCeilingPolicy.CeilingReading.parse("1300000000|0|held|extra"))
    }

    // ── حكم التثبيت ──────────────────────────────────────────────────────────

    @Test
    fun `a pin is verified only when the clock agrees with the pinned step`() {
        assertEquals(GpuCeilingPolicy.PinVerdict.VERIFIED, GpuCeilingPolicy.pinVerdict(780_000_000L, 780_000_000L))
        // وهذه هي الحالة التي كانت تُعلَن نجاحًا بصدى الفهرس وحده: الفهرس قُبل، والتردد غيره.
        assertEquals(
            GpuCeilingPolicy.PinVerdict.CLOCK_MISMATCH,
            GpuCeilingPolicy.pinVerdict(780_000_000L, 650_000_000L),
        )
        assertEquals(GpuCeilingPolicy.PinVerdict.BY_INDEX, GpuCeilingPolicy.pinVerdict(780_000_000L, null))
        assertEquals(GpuCeilingPolicy.PinVerdict.UNREADABLE, GpuCeilingPolicy.pinVerdict(null, 650_000_000L))
        assertNotNull(GpuCeilingPolicy.PinVerdict.CLOCK_MISMATCH.token)
    }
}
