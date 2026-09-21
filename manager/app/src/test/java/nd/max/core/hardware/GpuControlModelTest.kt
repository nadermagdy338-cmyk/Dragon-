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

    @Test fun liveGpuCapSelectsHighestUsableOppForPerformanceIntent() {
        val fake = io(max = "754000000")
        fake.put(
            "/sys/class/devfreq/test-gpu/available_frequencies",
            "100000000 200000000 400000000 676000000 754000000 800000000",
        )
        val device = GpuHardwareBackend.selection(fake).device!!
        val request = GpuHardwareBackend.requestForMode(device, GpuHardwareBackend.IntentMode.ADAPTIVE)!!
        assertEquals(754_000_000L, request.maxFreq)
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

    @Test fun unitConversionUsesSnapshotUnit() {
        val gpu = GpuHardwareBackend.selection(io()).device!!
        assertEquals(650L, GpuHardwareBackend.frequencyMHz(gpu, 650_000_000L))
        assertNull(GpuHardwareBackend.frequencyMHz(gpu.copy(frequencyUnit = GpuHardwareBackend.FrequencyUnit.AMBIGUOUS), 650_000_000L))
    }
}
