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

    // ── طلب السقف: هل يلزم تحرير، وهل وقع فعلًا؟ ─────────────────────────────

    @Test
    fun `a request above the live ceiling needs the vendor cap released`() {
        // العطب المقيس: طلبٌ فوق السقف الحيّ يُكتب بلا تحرير فتقع كتابته على سقف مكتوب أصلًا،
        // فيقرأ المستخدم القيمة نفسها ولا يرى أثرًا — «أختار gaming ولا شيء يتغيّر».
        assertTrue(GpuCeilingPolicy.releaseRequired(1_105_000_000L, 754_000_000L))
        assertTrue(GpuCeilingPolicy.releaseRequired(1_300_000_000L, 754_000_000L))
        // وطلب عند السقف نفسه: تحرير أيضًا — فالكتابة تساويه ولا تُنفّذ شيئًا بنفسها.
        assertTrue(GpuCeilingPolicy.releaseRequired(754_000_000L, 754_000_000L))
        // وطلب تبريد (دون السقف الحيّ) لا يرفع حماية وضعها المصنّع — وإلا صار التبريد تسخينًا.
        assertFalse(GpuCeilingPolicy.releaseRequired(650_000_000L, 754_000_000L))
        // وسقف حيّ غير مقروء يُسلَك به مسلك التحرير: لا يُدَّعى أن الطلب تحت حماية لم تُقس.
        assertTrue(GpuCeilingPolicy.releaseRequired(400_000_000L, null))
    }

    @Test
    fun `a retarget above our own lowered cap is released, so the ceiling can rise again`() {
        // العطب المُبلَّغ عنه حرفيًّا: «اختر power ثم ارجع إلى performance — يبقى عالقًا على 520».
        // والمسار: المقبض يُسجَّل مرّة بطلب تبريد، والحارس الحراري يُعيد استهدافه **برفع** — وكتلة
        // الرفع كانت تُقيَّد بالسقف الحيّ، وهو **خفضُنا نحن** (٥٢٠)، فلا يرتفع أبدًا.
        assertTrue(
            "رفعٌ فوق خفضنا ⇒ يلزم تحرير، وإلا انقصّ الطلب إلى ٥٢٠",
            GpuCeilingPolicy.releaseRequiredForRetarget(
                requestedHz = 1_300_000_000L,
                liveCeilingHz = 520_000_000L,
                plannedRelease = false,
            ),
        )
        assertTrue(
            "والقيمة الوسطى كذلك (٧٠٢ كطلب المستخدم بعد خفض إلى ٥٢٠)",
            GpuCeilingPolicy.releaseRequiredForRetarget(702_000_000L, 520_000_000L, plannedRelease = false),
        )
        assertFalse(
            "وطلب تبريدٍ يساوي السقف الحيّ لا يرفع حماية المصنّع (وإلا صار التبريد تسخينًا)",
            GpuCeilingPolicy.releaseRequiredForRetarget(520_000_000L, 520_000_000L, plannedRelease = false),
        )
        assertFalse(
            "وطلبٌ دون السقف الحيّ تبريدٌ محض: يُكتب ولا يلمس السلطة",
            GpuCeilingPolicy.releaseRequiredForRetarget(416_000_000L, 1_300_000_000L, plannedRelease = false),
        )
        assertTrue(
            "وقرارُ تحرير اتُّخذ عند التسجيل لا يُلغى بإعادة استهداف (بنية المعاملة شُكِّلت عليه)",
            GpuCeilingPolicy.releaseRequiredForRetarget(520_000_000L, 416_000_000L, plannedRelease = true),
        )
        assertFalse(
            "وسقف حيّ غير مقروء لا يُضيف تحريرًا بالتشقيق (لا يُدَّعى ما لم يُقس)",
            GpuCeilingPolicy.releaseRequiredForRetarget(780_000_000L, null, plannedRelease = false),
        )
    }

    @Test
    fun `a release is judged on what we own, not on the policy the device keeps`() {
        val released = GpuCeilingPolicy.CeilingReading(1_300_000_000L, 0L, false, lockActive = false)
        val verdict = GpuCeilingPolicy.releaseVerdict(released, 1_300_000_000L)
        assertTrue(verdict.satisfied)
        assertEquals(GpuCeilingPolicy.ReleaseVerdict.RELEASED, verdict.token)

        // وكل قنواتنا مرفوعة والمنصّة تحتفظ بسقف أدنى: نجاحٌ لِما نملك، ورقمٌ يُعلن لما لا نملك.
        // وهذه بالحرف الحالة التي كان الحكم فيها «فشل» فيُستعاد خط الأساس — **فيمحو التحرير نفسه**
        // ويضمن ألّا يقع تغيير أبدًا («Performance لا يعمل»).
        val kept = GpuCeilingPolicy.CeilingReading(754_000_000L, 0L, false, lockActive = false)
        val below = GpuCeilingPolicy.releaseVerdict(kept, 1_300_000_000L)
        assertTrue(below.satisfied)
        assertEquals("gpu-ceiling-open-below-request:754000000", below.reason)

        // وسقفٌ من عندنا لم يُرفع: فشل صريح — لا نجاح كاذب ولا حذف لرقم.
        val heldUpbound = GpuCeilingPolicy.releaseVerdict(kept.copy(platformUpbound = 650_000_000L), 1_300_000_000L)
        assertFalse(heldUpbound.satisfied)
        assertEquals(GpuCeilingPolicy.ReleaseVerdict.HELD, heldUpbound.token)
        assertFalse(GpuCeilingPolicy.releaseVerdict(kept.copy(platformCoolingHeld = true), 1_300_000_000L).satisfied)

        // وقفل OPP: يُقبل عند الطلب أو فوقه (تثبيت على أعلى درجة هو مسلك الجهاز الوحيد حين لا
        // يقبل المدى)، ويُرفض دونه: ذاك جمود من جلسة سابقة يخنق التردد.
        val pinnedTop = kept.copy(nodeCeilingHz = 1_300_000_000L, lockActive = true)
        assertEquals(GpuCeilingPolicy.ReleaseVerdict.PINNED, GpuCeilingPolicy.releaseVerdict(pinnedTop, 1_300_000_000L).token)
        assertTrue(GpuCeilingPolicy.releaseVerdict(pinnedTop, 1_300_000_000L).satisfied)
        val pinnedLow = kept.copy(nodeCeilingHz = 650_000_000L, lockActive = true)
        assertFalse(GpuCeilingPolicy.releaseVerdict(pinnedLow, 1_300_000_000L).satisfied)

        // وقراءة غير ممكنة ليست تحريرًا: «لم أقِس» ليست «نجح».
        assertFalse(GpuCeilingPolicy.releaseVerdict(null, 1_300_000_000L).satisfied)
        assertFalse(GpuCeilingPolicy.releaseVerdict(kept.copy(nodeCeilingHz = null), 1_300_000_000L).satisfied)
        assertEquals(
            GpuCeilingPolicy.ReleaseVerdict.UNREADABLE,
            GpuCeilingPolicy.releaseVerdict(null, 1_300_000_000L).token,
        )
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
