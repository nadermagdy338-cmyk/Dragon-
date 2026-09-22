package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuControlModelTest {
    private class FakeIo(
        private val values: MutableMap<String, String>,
        private val writable: Set<String>,
        private val failWrites: MutableSet<String> = mutableSetOf(),
        private val failAlways: Set<String> = emptySet(),
        private val failAfterSuccess: MutableMap<String, Int> = mutableMapOf(),
        private val enforceInvariant: Boolean = false,
    ) : GpuHardwareBackend.Io {
        val writes = mutableListOf<Pair<String, String>>()
        var invariantViolated = false

        override fun exists(path: String) = path in values || values.keys.any { it.startsWith("$path/") }
        override fun writable(path: String) = path in writable
        override fun read(path: String) = values[path]
        override fun write(path: String, value: String): Boolean {
            writes += path to value
            if (path in failAlways) return false
            val remaining = failAfterSuccess[path]
            if (remaining != null) {
                if (remaining <= 0) return false
                failAfterSuccess[path] = remaining - 1
            }
            if (path in failWrites) { failWrites.remove(path); return false }
            if (path !in writable) return false
            if (enforceInvariant && (path.endsWith("/min_freq") || path.endsWith("/max_freq"))) {
                val base = path.substringBeforeLast('/')
                val candidateMin = if (path.endsWith("/min_freq")) value.toLong() else values["$base/min_freq"]?.toLong()
                val candidateMax = if (path.endsWith("/max_freq")) value.toLong() else values["$base/max_freq"]?.toLong()
                if (candidateMin != null && candidateMax != null && candidateMin > candidateMax) {
                    invariantViolated = true
                    return false
                }
            }
            values[path] = value
            return true
        }
        override fun listDirectories(path: String): List<String> = when (path) {
            "/sys/class/devfreq" -> values.keys.mapNotNull {
                it.takeIf { key -> key.startsWith("$path/") }
                    ?.removePrefix("$path/")?.substringBefore('/')?.takeIf(String::isNotBlank)
            }.distinct()
            "/sys/class/thermal" -> emptyList()
            else -> emptyList()
        }

        /**
         * نداءات سلطة المنصّة كـ`lock` و`release` مستقلّين.
         *
         * ووُجد ليسجّل **الفصل** بينهما: كان `releaseVendorCeiling` يُمرَّر كـ`lock`، فطلبٌ عند قدرة
         * الجهاز (وهو عكس التثبيت) كان **يطفئ DVFS** ويثبّت التردد الذي صادف وجوده. وهذا لا يظهر
         * في أي عقدة وهمية — يظهر في الوسيطين.
         */
        val vendorCeilingCalls = mutableListOf<Pair<Boolean, Boolean>>()

        override fun permitVendorCeiling(lock: Boolean, release: Boolean): Boolean {
            vendorCeilingCalls += lock to release
            return false
        }

        fun value(path: String) = values[path]
        fun put(path: String, value: String) { values[path] = value }
    }

    private fun io(
        name: String = "mali-gpu",
        min: String? = "100000000",
        max: String? = "800000000",
        failWrites: MutableSet<String> = mutableSetOf(),
        failAlways: Set<String> = emptySet(),
        failAfterSuccess: MutableMap<String, Int> = mutableMapOf(),
        enforceInvariant: Boolean = false,
    ): FakeIo {
        val base = "/sys/class/devfreq/test-gpu"
        val values = mutableMapOf(
            "$base/device_name" to name,
            "$base/available_frequencies" to "100000000 200000000 400000000 800000000",
            "$base/cur_freq" to "200000000",
            "$base/governor" to "simple_ondemand",
            "$base/available_governors" to "simple_ondemand performance",
        )
        min?.let { values["$base/min_freq"] = it }
        max?.let { values["$base/max_freq"] = it }
        return FakeIo(
            values,
            setOf("$base/min_freq", "$base/max_freq", "$base/governor"),
            failWrites,
            failAlways,
            failAfterSuccess,
            enforceInvariant,
        )
    }

    private fun mtkIo(
        activeIndex: String = "-1",
        failWrites: MutableSet<String> = mutableSetOf(),
    ): FakeIo {
        val base = "/sys/class/devfreq/mali0"
        val lock = "/proc/gpufreqv2/fix_target_opp_index"
        return FakeIo(
            mutableMapOf(
                "$base/device_name" to "mali-gpu",
                "$base/cur_freq" to "400000",
                "$base/governor" to "simple_ondemand",
                "$base/available_governors" to "simple_ondemand performance",
                "/proc/gpufreqv2/stack_signed_opp_table" to "[0] freq=800 MHz volt=900000\n[1] freq=400 MHz volt=800000\n[2] freq=200 MHz volt=700000",
                lock to activeIndex,
            ),
            setOf(lock, "$base/governor"),
            failWrites,
        )
    }

    /**
     * جهاز MediaTek حقيقي التركيب: `min_freq`/`max_freq` قابلان للكتابة **و**مسار قفل OPP موجود،
     * وسقف المصنّع الحيّ (٧٥٤) أقلّ من أعلى درجة معلنة (١٣٠٠) — أي الشكل المقيس على rodin.
     *
     * وهذا بالضبط ما كان يُبطل كل شيء: الشرط القديم `rangeWritable = … && mtkFixedIndexPath == null`
     * كان يجعل المسار الوحيد المتاح هو **تثبيت** درجة واحدة (تجميد)، ويُسقط سقف المصنّع من القراءة.
     */
    private fun mtkCeilingIo(activeIndex: String = "-1"): FakeIo {
        val base = "/sys/class/devfreq/mali0"
        val lock = "/proc/gpufreqv2/fix_target_opp_index"
        return FakeIo(
            mutableMapOf(
                "$base/device_name" to "mali-gpu",
                "$base/available_frequencies" to "260000000 400000000 650000000 754000000 1300000000",
                "$base/min_freq" to "260000000",
                "$base/max_freq" to "754000000",
                "$base/cur_freq" to "400000000",
                "$base/governor" to "simple_ondemand",
                "$base/available_governors" to "simple_ondemand performance",
                "/proc/gpufreqv2/stack_signed_opp_table" to
                    "[0] freq=1300 MHz volt=900000\n[1] freq=754 MHz volt=850000\n[2] freq=650 MHz volt=800000",
                lock to activeIndex,
            ),
            setOf(lock, "$base/min_freq", "$base/max_freq", "$base/governor"),
        )
    }

    @Test fun mtkVendorCeilingIsNotDiscardedByTheFixedIndexPath() {
        // قياس rodin: السقف الحيّ ٧٥٤ وأعلى درجة معلنة ١٣٠٠ — وكان سطر الفحص يقول `live_max=1300000000`
        // لأن الفرع أخذ الحدّين من جدول القفل وحده. الإسقاط هو ما جعل الحاكم يحكم «مُلبّى» بلا كتابة.
        val device = GpuHardwareBackend.selection(mtkCeilingIo()).device!!
        assertEquals(1_300_000_000L, device.frequencies.maxOrNull())
        assertEquals(754_000_000L, device.maxFreq)
        assertEquals(754_000_000L, GpuHardwareBackend.configurableMaxFrequency(device))
        // ومع ذلك القدرة كاملة معلنة، فالطلب عند القدرة يبقى تحريرًا لا كتابة.
        assertEquals(754_000_000L, GpuHardwareBackend.snapToAvailableAtOrBelow(device, 1_300_000_000L))
    }

    @Test fun mtkCeilingWritesTheRangeInsteadOfPinningAnOpp() {
        val fake = mtkCeilingIo()
        val device = GpuHardwareBackend.selection(fake).device!!
        val lock = "/proc/gpufreqv2/fix_target_opp_index"
        // السؤال الصحيح للسقف: هل تقبل عقدتا المدى كتابة؟ ومكان القفل لا يُبطله.
        assertTrue(device.devfreqCeilingWritable)
        assertFalse("المسار القديم يمنع المدى عند وجود قفل — وهذا ما حوّل السقف إلى تثبيت", device.rangeWritable)

        val result = GpuHardwareBackend.applyValidated(
            device,
            GpuHardwareBackend.Request(260_000_000L, 650_000_000L, releaseVendorCeiling = false),
            fake,
        )
        assertTrue(result.verified)
        assertEquals("650000000", fake.value("/sys/class/devfreq/mali0/max_freq"))
        assertEquals("-1", fake.value(lock))
        assertFalse("لا كتابة على عقدة القفل لطلب سقف", fake.writes.any { it.first == lock })
        assertTrue("ولا يُطلب إطفاء DVFS لطلب سقف", fake.vendorCeilingCalls.none { it.first })
    }

    @Test fun mtkCeilingClearsAStaleOppLockAndRestoresItOnExit() {
        // قفل باقٍ من جلسة سابقة يجمّد التردد من خارج devfreq: كتابة المدى وحدها بلا أثر مرئي.
        val fake = mtkCeilingIo(activeIndex = "2")
        val device = GpuHardwareBackend.selection(fake).device!!
        val lock = "/proc/gpufreqv2/fix_target_opp_index"
        assertEquals(650_000_000L, device.maxFreq)
        // وخط الأساس يُلتقط **قبل** الكتابة (كما يفعل `AppMonitor`) وإلا لكان قد سجّل ما بعدها.
        val baseline = GpuHardwareBackend.captureBaseline(device, fake)

        val result = GpuHardwareBackend.applyValidated(
            device,
            GpuHardwareBackend.Request(260_000_000L, 754_000_000L, releaseVendorCeiling = false),
            fake,
        )
        assertTrue(result.verified)
        assertEquals("-1", fake.value(lock))
        assertTrue(GpuHardwareBackend.restoreBaseline(baseline, fake))
        assertEquals("2", fake.value(lock))
    }

    @Test fun aFailedPinRestoresDynamicScalingAndTheBaselineIndex() {
        // التراجع في مسار القفل لا يُعيد الفهرس وحده: التثبيت **يوقف DVFS** (إن طُلب رفع سقف
        // المصنّع)، فإبقاؤه موقوفًا بعد تراجع يُنتج العطب الذي جاء التراجع لإلغائه: الجهاز يبقى على
        // الدرجة التي صادف وجودها وقت الطلب. وهذا لا يظهر في أي عقدة — يظهر في نداء سلطة المنصّة.
        val lock = "/proc/gpufreqv2/fix_target_opp_index"
        val fake = mtkIo(activeIndex = "1", failWrites = mutableSetOf(lock))
        val device = GpuHardwareBackend.selection(fake).device!!

        val result = GpuHardwareBackend.applyValidated(
            device,
            GpuHardwareBackend.Request(
                minFreq = 400_000_000L,
                maxFreq = 400_000_000L,
                releaseVendorCeiling = true,
            ),
            fake,
        )
        assertFalse(result.verified)
        assertTrue(result.rollbackAttempted)
        assertEquals("الثابت الأصلي يعود", "1", fake.value(lock))
        assertEquals(
            "آخر نداء سلطة يجب أن يُعيد التوسّع الديناميكي، لا أن يُبقيه موقوفًا",
            false to false,
            fake.vendorCeilingCalls.last(),
        )
        assertTrue("وقد طُلب رفع سقف المصنّع قبل الكتابة", fake.vendorCeilingCalls.any { it.first })
    }

    @Test fun aHeldOppLockIsVisibleInTheCeilingReading() {
        // ولا تظهر هذه الحالة في أي قراءة تردد: `max_freq` يقرأ القدرة والجهاز مجمَّد على درجة.
        val held = mtkCeilingIo(activeIndex = "1")
        val heldDevice = GpuHardwareBackend.selection(held).device!!
        assertEquals(true, GpuHardwareBackend.ceilingReading(heldDevice, held).lockActive)

        val free = mtkCeilingIo()
        val freeDevice = GpuHardwareBackend.selection(free).device!!
        assertEquals(false, GpuHardwareBackend.ceilingReading(freeDevice, free).lockActive)
    }

    @Test fun liveGpuCapWinsOverHigherAdvertisedOpp() {
        val fake = io(max = "754000000")
        fake.put(
            "/sys/class/devfreq/test-gpu/available_frequencies",
            "100000000 200000000 400000000 676000000 754000000 800000000",
        )
        val device = GpuHardwareBackend.selection(fake).device!!
        assertEquals(754_000_000L, GpuHardwareBackend.configurableMaxFrequency(device))
        assertEquals(800_000_000L, device.frequencies.maxOrNull())
        assertEquals(754_000_000L, GpuHardwareBackend.snapToAvailableAtOrBelow(device, 1_300_000_000L))
    }

    /**
     * القياس الذي أوجب تغيير هذا التوقّع (rodin · MT6899 · 2026-09-22): طلبُ وضع القدرة كان
     * يُقصّ إلى **السقف الحيّ** — وهو قيمةٌ يكتبها هذا التطبيق نفسه (سقف بروفايل per-app) —
     * فيصير خفضُنا نحن «قدرةَ الجهاز» في نظر الطلب التالي، ولا يزيد التردد عمّا نُقص أبدًا.
     *
     * والتوقّع القديم كان يثبّت العطب: `ADAPTIVE` ⇒ ٧٥٤ بينما القدرة المُعلنة ٨٠٠.
     * والتخطيط الآن من القدرة المُعلنة، والسقف الحيّ يبقى مقروءًا ويُعلَن بعد الكتابة.
     */
    @Test fun capabilityIntentIsPlannedFromAdvertisedCapacityNotFromTheLiveCeiling() {
        val fake = io(max = "754000000")
        fake.put(
            "/sys/class/devfreq/test-gpu/available_frequencies",
            "100000000 200000000 400000000 676000000 754000000 800000000",
        )
        val device = GpuHardwareBackend.selection(fake).device!!
        val request = GpuHardwareBackend.requestForMode(device, GpuHardwareBackend.IntentMode.ADAPTIVE)!!
        assertEquals("وضع القدرة الكاملة يطلب أعلى درجة مُعلنة", 800_000_000L, request.maxFreq)
        assertEquals("والسقف الحيّ يبقى مقروءًا كما هو — يُقاس ولا يُستخدم ضدّ المستخدم", 754_000_000L, GpuHardwareBackend.configurableMaxFrequency(device))
    }

    /**
     * ونفس القياس على شكل الجهاز نفسه: قفل OPP موجود، وسقف حيّ ٦٥٠ **كتبناه نحن**، ثم طلب قدرة.
     */
    @Test fun aRaiseAfterOurOwnLoweringPlansTheAdvertisedTopOpp() {
        val fake = mtkCeilingIo()
        fake.put("/sys/class/devfreq/mali0/max_freq", "650000000")
        val device = GpuHardwareBackend.selection(fake).device!!
        assertEquals(650_000_000L, GpuHardwareBackend.configurableMaxFrequency(device))
        assertEquals(
            "خفضنا السابق ليس قدرة الجهاز",
            1_300_000_000L,
            GpuHardwareBackend.requestForMode(device, GpuHardwareBackend.IntentMode.ADAPTIVE)!!.maxFreq,
        )
    }

    /**
     * و«بعد إغلاق التطبيق لا يرجع للوضع الافتراضي»: على MediaTek كان شرط استرجاع المدى
     * `live.mtkFixedIndexPath == null` — أي أن المدى **لا يُستعاد أبدًا** على جهاز به مسار قفل OPP،
     * فيبقى `max_freq` على آخر قيمة كتبناها. والشرط الآن هو الانحراف نفسه لا وجود المسار.
     */
    @Test fun mtkRangeIsRestoredWhenItDriftedFromTheBaseline() {
        val fake = mtkCeilingIo()
        val base = "/sys/class/devfreq/mali0"
        val device = GpuHardwareBackend.selection(fake).device!!
        val baseline = GpuHardwareBackend.captureBaseline(device, fake)

        assertTrue(
            GpuHardwareBackend.applyValidated(
                device,
                GpuHardwareBackend.Request(260_000_000L, 400_000_000L, releaseVendorCeiling = false),
                fake,
            ).verified,
        )
        assertEquals("400000000", fake.value("$base/max_freq"))

        assertTrue("الاسترجاع يجب أن يُعيد المدى لا الفهرس وحده", GpuHardwareBackend.restoreBaseline(baseline, fake))
        assertEquals("754000000", fake.value("$base/max_freq"))
        assertEquals("260000000", fake.value("$base/min_freq"))
    }

    @Test fun anUndriftedRangeIsNotRewrittenOnExit() {
        val fake = mtkCeilingIo()
        val baseline = GpuHardwareBackend.captureBaseline(GpuHardwareBackend.selection(fake).device!!, fake)
        val before = fake.writes.size
        assertTrue(GpuHardwareBackend.restoreBaseline(baseline, fake))
        assertFalse(
            "لا كتابة على المدى إن كان هو خط الأساس — فلا ضجيج على مقبض لم يُمَسّ",
            fake.writes.drop(before).any { it.first.endsWith("max_freq") },
        )
    }

    @Test fun intentModesUseOnlyAdvertisedFrequencies() {
        val gpu = GpuHardwareBackend.selection(io()).device!!
        val modes = GpuHardwareBackend.IntentMode.entries.map { GpuHardwareBackend.requestForMode(gpu, it)!! }
        modes.forEach { assert(it.minFreq in gpu.frequencies); assert(it.maxFreq in gpu.frequencies) }
        assertEquals(100_000_000L, modes[1].minFreq)
        assertEquals(800_000_000L, modes[1].maxFreq)
        assertNull(modes[1].governor)
    }

    @Test fun mixedUnitsBecomeReadOnlyAndRejectGovernor() {
        val mixed = io()
        mixed.put("/sys/class/devfreq/test-gpu/cur_freq", "200000")
        val selection = GpuHardwareBackend.selection(mixed)
        val device = selection.device!!
        assertEquals(GpuHardwareBackend.FrequencyUnit.AMBIGUOUS, device.frequencyUnit)
        assertEquals(GpuHardwareBackend.SelectionState.READ_ONLY, selection.state)
        assertEquals("ambiguous-frequency-unit", GpuHardwareBackend.validate(device, GpuHardwareBackend.Request(governor = "performance")))
    }

    @Test fun genericGpuIdentityDoesNotPretendToBeMali() {
        val fake = io(name = "gpu0")
        fake.put("/sys/class/devfreq/test-gpu/device_name", "generic-accelerator")
        val device = GpuHardwareBackend.selection(fake).device!!
        assertEquals(GpuHardwareBackend.Family.UNKNOWN, device.family)
    }

    @Test fun equallyProvenProvidersAreAmbiguous() {
        val fake = io()
        val second = "/sys/class/devfreq/other-gpu"
        listOf(
            "device_name" to "mali-gpu",
            "available_frequencies" to "100000000 200000000 400000000 800000000",
            "cur_freq" to "200000000",
            "min_freq" to "100000000",
            "max_freq" to "800000000",
            "governor" to "simple_ondemand",
            "available_governors" to "simple_ondemand performance",
        ).forEach { (field, value) -> fake.put("$second/$field", value) }
        assertEquals(GpuHardwareBackend.SelectionState.AMBIGUOUS, GpuHardwareBackend.selection(fake).state)
    }

    @Test fun unreadableBaselineBlocksRangeMutation() {
        val fake = io(min = null)
        val device = GpuHardwareBackend.selection(fake).device!!
        val result = GpuHardwareBackend.apply(device, GpuHardwareBackend.Request(200_000_000L, 400_000_000L), fake)
        assertFalse(result.writeSucceeded)
        assertEquals("range-read-only-or-unproven", result.error)
        assertNull(fake.value("/sys/class/devfreq/test-gpu/min_freq"))
    }

    @Test fun partialFailureRestoresExactBaseline() {
        val maxPath = "/sys/class/devfreq/test-gpu/max_freq"
        val fake = io(failWrites = mutableSetOf(maxPath))
        val device = GpuHardwareBackend.selection(fake).device!!
        val result = GpuHardwareBackend.apply(device, GpuHardwareBackend.Request(200_000_000L, 400_000_000L), fake)
        assertFalse(result.verified)
        assertTrue(result.rollbackAttempted)
        assertTrue(result.rollbackVerified == true)
        assertEquals("100000000", fake.value("/sys/class/devfreq/test-gpu/min_freq"))
        assertEquals("800000000", fake.value(maxPath))
    }

    @Test fun rollbackFailureStillAttemptsGovernorRestore() {
        val base = "/sys/class/devfreq/test-gpu"
        val fake = io(
            failWrites = mutableSetOf("$base/governor"),
            failAfterSuccess = mutableMapOf("$base/min_freq" to 1),
        )
        val device = GpuHardwareBackend.selection(fake).device!!
        val request = GpuHardwareBackend.Request(200_000_000L, 400_000_000L, "performance")
        val result = GpuHardwareBackend.apply(device, request, fake)
        assertFalse(result.verified)
        assertTrue(result.rollbackAttempted)
        assertFalse(result.rollbackVerified!!)
        assertTrue(fake.writes.contains("$base/governor" to "simple_ondemand"))
    }

    @Test fun writesNeverBreakMinMaxInvariant() {
        val fake = io(min = "100000000", max = "200000000", enforceInvariant = true)
        val device = GpuHardwareBackend.selection(fake).device!!
        val result = GpuHardwareBackend.apply(device, GpuHardwareBackend.Request(400_000_000L, 800_000_000L), fake)
        assertTrue(result.verified)
        assertFalse(fake.invariantViolated)
        assertEquals("/sys/class/devfreq/test-gpu/max_freq", fake.writes.first().first)
    }

    @Test fun mtkExactLockReportsEffectiveRangeAndReleases() {
        val fake = mtkIo()
        val device = GpuHardwareBackend.selection(fake).device!!
        val result = GpuHardwareBackend.apply(device, GpuHardwareBackend.Request(400_000_000L, 400_000_000L), fake)
        assertTrue(result.verified)
        assertEquals(400_000_000L, result.actual?.minFreq)
        assertEquals(400_000_000L, result.actual?.maxFreq)
        assertEquals(400_000_000L, GpuHardwareBackend.currentExactLockFrequency(result.actual!!, fake))
        assertTrue(GpuHardwareBackend.releaseExactLock(fake))
    }

    @Test fun activeMtkLockIsReflectedDuringDiscovery() {
        val device = GpuHardwareBackend.selection(mtkIo(activeIndex = "1")).device!!
        assertEquals(400_000_000L, device.minFreq)
        assertEquals(400_000_000L, device.maxFreq)
        assertEquals(400_000_000L, device.currentFreq)
    }

    @Test fun mtkSmartIntentsMapToLocksAndRelease() {
        val device = GpuHardwareBackend.selection(mtkIo()).device!!
        val efficiency = GpuHardwareBackend.requestForMode(device, GpuHardwareBackend.IntentMode.EFFICIENCY)!!
        val adaptive = GpuHardwareBackend.requestForMode(device, GpuHardwareBackend.IntentMode.ADAPTIVE)!!
        val sustained = GpuHardwareBackend.requestForMode(device, GpuHardwareBackend.IntentMode.SUSTAINED)!!
        assertEquals(200_000_000L, efficiency.minFreq)
        assertEquals(efficiency.minFreq, efficiency.maxFreq)
        assertTrue(adaptive.releaseLock)
        assertNull(adaptive.minFreq)
        assertNull(adaptive.governor)
        assertEquals(400_000_000L, sustained.minFreq)
        assertEquals(sustained.minFreq, sustained.maxFreq)
    }

    @Test fun mtkAdaptiveReleaseIsVerifiedAndRestoresEffectiveRange() {
        val lock = "/proc/gpufreqv2/fix_target_opp_index"
        val fake = mtkIo(activeIndex = "1")
        val device = GpuHardwareBackend.selection(fake).device!!
        val result = GpuHardwareBackend.apply(device, GpuHardwareBackend.Request(releaseLock = true), fake)
        assertTrue(result.verified)
        assertEquals("-1", fake.value(lock))
        val released = result.actual!!
        assertEquals(200_000_000L, released.minFreq)
        assertEquals(800_000_000L, released.maxFreq)
    }

    @Test fun mtkAdaptiveOnReleasedLockIsAVerifiedNoop() {
        val fake = mtkIo(activeIndex = "-1")
        val device = GpuHardwareBackend.selection(fake).device!!
        val result = GpuHardwareBackend.apply(device, GpuHardwareBackend.Request(releaseLock = true), fake)
        assertTrue(result.verified)
    }

    @Test fun mtkReleaseFailureRollsBackToBaselineIndex() {
        val lock = "/proc/gpufreqv2/fix_target_opp_index"
        val failWrites = mutableSetOf<String>()
        val fake = mtkIo(activeIndex = "1", failWrites = failWrites)
        failWrites.add(lock)
        val device = GpuHardwareBackend.selection(fake).device!!
        val result = GpuHardwareBackend.apply(device, GpuHardwareBackend.Request(releaseLock = true), fake)
        assertFalse(result.verified)
        assertTrue(result.rollbackAttempted)
        assertTrue(result.rollbackVerified == true)
        assertEquals("1", fake.value(lock))
        assertEquals("apply-not-verified-baseline-restored", result.error)
    }

    @Test fun releaseLockRequestMustBeExclusiveAndSupported() {
        val mtk = GpuHardwareBackend.selection(mtkIo()).device!!
        val generic = GpuHardwareBackend.selection(io()).device!!
        assertEquals(
            "invalid-request",
            GpuHardwareBackend.validate(mtk, GpuHardwareBackend.Request(200_000_000L, 200_000_000L, releaseLock = true)),
        )
        assertEquals("release-lock-unavailable", GpuHardwareBackend.validate(generic, GpuHardwareBackend.Request(releaseLock = true)))
        assertNull(GpuHardwareBackend.validate(mtk, GpuHardwareBackend.Request(releaseLock = true)))
    }

    // ── سلطة المنصّة: معاملان لا معامل ──────────────────────────────────────

    @Test fun aCeilingAtCapabilityNeverTurnsOffDvfs() {
        // القياس الذي أوجب هذا الاختبار: `lock` كان يُشتقّ من `releaseVendorCeiling`، فصار طلب
        // سقف عند قدرة الجهاز يُطفئ DVFS ويجمّد التردد الحاضر — أي عكس غرضه.
        val fake = io()
        val device = GpuHardwareBackend.selection(fake).device!!
        val request = GpuHardwareBackend.Request(200_000_000L, 800_000_000L, releaseVendorCeiling = true)
        assertTrue(GpuHardwareBackend.apply(device, request, fake).verified)
        assertEquals(listOf(false to true), fake.vendorCeilingCalls)
    }

    @Test fun aCoolingCeilingLeavesThePlatformCapAlone() {
        // سقف أدنى من القدرة (بروفايل تبريد) لا يرفع حماية المصنّع: طلب تبريد يجوز أن ينزل، ولا
        // يجوز أن يرفع السقف ثم يكتب سقفه فوقه.
        val fake = io()
        val device = GpuHardwareBackend.selection(fake).device!!
        val request = GpuHardwareBackend.Request(200_000_000L, 400_000_000L, releaseVendorCeiling = false)
        assertTrue(GpuHardwareBackend.apply(device, request, fake).verified)
        assertEquals(listOf(false to false), fake.vendorCeilingCalls)
    }

    @Test fun anExactPinAsksForBothTheReleaseAndTheDvfsHold() {
        val fake = io()
        val device = GpuHardwareBackend.selection(fake).device!!
        val request = GpuHardwareBackend.Request(400_000_000L, 400_000_000L, releaseVendorCeiling = true)
        assertTrue(GpuHardwareBackend.apply(device, request, fake).verified)
        assertEquals(listOf(true to true), fake.vendorCeilingCalls)
    }

    @Test fun conflictingOppIndicesAreNotGuessed() {
        // فهرسان مختلفان لنفس التردد في جدولين مختلفين = هذا التردد لا يُفهرَس بأمان. ولو أخذنا
        // الأخير (سلوك «آخر-يفوز» السابق) لَكتبنا في `fix_target_opp_index` رقمًا يُفسَّر على جدول
        // آخر، فيُثبَّت تردد غير الذي طُلِب ويُعلَن نجاح لأن التحقّق من صدى الفهرس لا من التردد.
        // وإسقاط الفهرس يُبطل مسار القفل كلّه: نرفض التخمين ونُعالج الجهاز عبر مسار المدى.
        val fake = mtkIo()
        fake.put(
            "/proc/gpufreqv2/stack_signed_opp_table",
            "[0] freq=800 MHz volt=900000\n[1] freq=400 MHz volt=800000",
        )
        fake.put("/proc/gpufreqv2/gpu_working_opp_table", "[5] freq=800 MHz\n[6] freq=400 MHz")
        val device = GpuHardwareBackend.selection(fake).device!!
        assertNull("فهرس متعارض لا يُختار منه واحد", device.mtkFixedIndexPath)
        assertTrue(device.mtkOppIndexByFrequency.isEmpty())
        assertFalse(device.exactLockWritable)
        assertTrue("والسلّم نفسه يبقى مرجع القدرة", device.frequencies.contains(800_000_000L))
    }

    @Test fun unprovenBareOppLinesAreRejectedNotGuessed() {
        val fake = mtkIo()
        fake.put("/proc/gpufreqv2/stack_signed_opp_table", "[0] 800000 900000\n[1] 400000 800000")
        val selection = GpuHardwareBackend.selection(fake)
        val device = selection.device!!
        assertTrue(device.mtkOppIndexByFrequency.isEmpty())
        assertNull(device.mtkFixedIndexPath)
        assertFalse(device.exactLockWritable)
    }

    @Test fun labeledAndUnitBearingOppLinesParseAcrossFormats() {
        val fake = mtkIo()
        fake.put(
            "/proc/gpufreqv2/stack_signed_opp_table",
            "[0] freq=800 MHz volt=900000\n[1] 700 MHz 875000\n[2] 0.6 GHz\n[3] volt=825000 freq=500000",
        )
        val device = GpuHardwareBackend.selection(fake).device!!
        val map = device.mtkOppIndexByFrequency
        assertEquals(mapOf(800_000_000L to "0", 700_000_000L to "1", 600_000_000L to "2", 500_000_000L to "3"), map)
    }

    @Test fun mixedIndexedAndUnindexedTablesNeverPinToTheSmallerIndexedTable() {
        val fake = mtkIo()
        fake.put(
            "/proc/gpufreq/gpufreq_opp_dump",
            "freq = 1300000\\nfreq = 975000\\nfreq = 546000",
        )
        val device = GpuHardwareBackend.selection(fake).device!!
        assertEquals(1_300_000_000L, device.frequencies.maxOrNull())
        assertNull(device.mtkFixedIndexPath)
        assertFalse(device.exactLockWritable)
    }

    @Test fun capabilityIsDiscoveredFromLegacyDumpWithoutADeviceSpecificConstant() {
        val fake = mtkIo()
        fake.put(
            "/proc/gpufreq/gpufreq_opp_dump",
            "freq = 1300000\\nfreq = 975000\\nfreq = 546000",
        )
        val device = GpuHardwareBackend.selection(fake).device!!
        assertEquals("the discovered table, not a fixed phone value, defines the capability", 1_300_000_000L, device.frequencies.maxOrNull())
        assertEquals(1_300_000_000L, GpuHardwareBackend.snapToAvailableAtOrBelow(device, Long.MAX_VALUE, respectLiveCeiling = false))
    }

    // ── صيغة قيمة مفتاح gpu_frequency ────────────────────────────────────────
    //
    // المُحكِّم يُثبت المعاملة بتساوي نصّي المطلوب والمقروء. فالمدى وحده لم يكن
    // يكفي لطلب يمسّ المُحكِّم أو يحرّر قفل OPP: الكتابة تنجح، والمقروء لا يساوي
    // المطلوب أبدًا، فيُعاد خط الأساس على تغيير **ناجح**. الاختبارات التالية
    // تُثبت أن العطب لم يعد ممكنًا في الحالتين، وأن تساويًا كاذبًا لا يتحقق.

    @Test fun governorOnlyRequestIsVerifiableInTheSharedValueSchema() {
        val fake = io()
        val device = GpuHardwareBackend.selection(fake).device!!
        val request = GpuHardwareBackend.Request(governor = "performance")
        val desired = GpuHardwareBackend.encodeRequest(request)

        // الصيغة القديمة (المدى وحده) كانت تعطي "100000000:800000000" — نصًّا
        // لا يمكن أن يساوي مطلب "غيّر المُحكِّم".
        assertNotEquals("${device.minFreq ?: ""}:${device.maxFreq ?: ""}", desired)
        // قبل الكتابة الإسقاط الحي مخالف، فالمُحكِّم يكتب فعلًا...
        assertNotEquals(desired, GpuHardwareBackend.encodeLive(device, request, fake))
        assertTrue(GpuHardwareBackend.apply(device, request, fake).verified)
        // ...وبعدها الإسقاط الحي نفسه يساوي المطلوب: مُثبَت لا مدَّعى.
        val live = GpuHardwareBackend.refresh(device.path, fake)!!
        assertEquals(desired, GpuHardwareBackend.encodeLive(live, request, fake))
    }

    @Test fun releasedFixedLockIsVerifiableInTheSharedValueSchema() {
        val fake = mtkIo(activeIndex = "1")
        val device = GpuHardwareBackend.selection(fake).device!!
        val request = GpuHardwareBackend.Request(releaseLock = true)
        val desired = GpuHardwareBackend.encodeRequest(request)

        assertNotEquals(desired, GpuHardwareBackend.encodeLive(device, request, fake))
        assertTrue(GpuHardwareBackend.apply(device, request, fake).verified)
        val live = GpuHardwareBackend.refresh(device.path, fake)!!
        assertEquals(desired, GpuHardwareBackend.encodeLive(live, request, fake))
    }

    @Test fun selectingAnIntentOnAnMtkLockVerifiesThroughTheSchema() {
        for (mode in GpuHardwareBackend.IntentMode.entries) {
            val fake = mtkIo(activeIndex = "1")
            val device = GpuHardwareBackend.selection(fake).device!!
            val request = GpuHardwareBackend.requestForMode(device, mode)!!
            val desired = GpuHardwareBackend.encodeRequest(request)
            assertTrue(mode.name, GpuHardwareBackend.apply(device, request, fake).verified)
            val live = GpuHardwareBackend.refresh(device.path, fake)!!
            assertEquals(mode.name, desired, GpuHardwareBackend.encodeLive(live, request, fake))
        }
    }

    @Test fun plainRangeStillVerifiesInTheSharedValueSchema() {
        val fake = io()
        val device = GpuHardwareBackend.selection(fake).device!!
        val request = GpuHardwareBackend.Request(200_000_000L, 400_000_000L)
        val desired = GpuHardwareBackend.encodeRequest(request)
        assertTrue(GpuHardwareBackend.apply(device, request, fake).verified)
        val live = GpuHardwareBackend.refresh(device.path, fake)!!
        assertEquals(desired, GpuHardwareBackend.encodeLive(live, request, fake))
    }

    @Test fun untouchedFieldsAreProjectedAwayNotComparedAsAbsent() {
        val fake = io()
        val device = GpuHardwareBackend.selection(fake).device!!
        // جهاز مُحكِّمه الحالي مختلف عن المطلوب: لا تساوي قبل الكتابة، ولو أُسقط
        // حقل المُحكِّم من الإسقاط لتساوى الاثنان كذبًا وصار الطلب "مُثبتًا" بلا كتابة.
        val governorRequest = GpuHardwareBackend.Request(governor = "performance")
        val desired = GpuHardwareBackend.encodeRequest(governorRequest)
        assertEquals("simple_ondemand", device.governor)
        assertNotEquals(desired, GpuHardwareBackend.encodeLive(device, governorRequest, fake))
        // وطلبٌ لا يمسّ المُحكِّم لا يُقارَن على المُحكِّم: مدى مطابق يكفي للإثبات
        // وإن اختلف المُحكِّم الحي — الحقل مُسقَط لا مُقارَن بغياب.
        val rangeOnly = GpuHardwareBackend.Request(100_000_000L, 800_000_000L)
        val live = device.copy(governor = "mali-something-else")
        assertEquals(GpuHardwareBackend.encodeRequest(rangeOnly), GpuHardwareBackend.encodeLive(live, rangeOnly, fake))
    }

    @Test fun bothEncodingsAlwaysShareTheirFieldCount() {
        val fake = io()
        val device = GpuHardwareBackend.selection(fake).device!!
        val requests = listOf(
            GpuHardwareBackend.Request(100_000_000L, 400_000_000L),
            GpuHardwareBackend.Request(governor = "performance"),
            GpuHardwareBackend.Request(200_000_000L, 400_000_000L, "performance"),
            GpuHardwareBackend.Request(releaseLock = true),
        )
        requests.forEach { request ->
            assertEquals(
                GpuHardwareBackend.encodeRequest(request).split("|").size,
                GpuHardwareBackend.encodeLive(device, request, fake).split("|").size,
            )
        }
    }

    @Test fun providerReadingsKeepTheProvidersOwnUnit() {
        // عقدة تتكلّم kHz: السلّم والمقروءات تُحفظ كما قرأها السائق، و`frequencyUnit` يسمّي تلك
        // اللغة. تحويلها إلى Hz في الاكتشاف كان يضاعف التحويل عند العرض (`frequencyMHz`) ويسجّل
        // قيمة لا تساويها `encodeLive`، فتنجح الكتابة وتُصنَّف فاشلة.
        val fake = io()
        listOf(
            "available_frequencies" to "100000 200000 400000 800000",
            "min_freq" to "100000",
            "max_freq" to "800000",
            "cur_freq" to "200000",
        ).forEach { (field, value) -> fake.put("/sys/class/devfreq/test-gpu/$field", value) }
        val device = GpuHardwareBackend.selection(fake).device!!
        assertEquals(GpuHardwareBackend.FrequencyUnit.KHZ, device.frequencyUnit)
        assertEquals(listOf(100_000L, 200_000L, 400_000L, 800_000L), device.frequencies)
        assertEquals(100_000L, device.minFreq)
        assertEquals(800_000L, device.maxFreq)
        assertEquals(200_000L, device.currentFreq)
        assertEquals(200L, GpuHardwareBackend.frequencyMHz(device, device.currentFreq))
    }

    @Test fun unitConversionUsesSnapshotUnit() {
        val gpu = GpuHardwareBackend.selection(io()).device!!
        assertEquals(650L, GpuHardwareBackend.frequencyMHz(gpu, 650_000_000L))
        assertNull(GpuHardwareBackend.frequencyMHz(gpu.copy(frequencyUnit = GpuHardwareBackend.FrequencyUnit.AMBIGUOUS), 650_000_000L))
    }
}
