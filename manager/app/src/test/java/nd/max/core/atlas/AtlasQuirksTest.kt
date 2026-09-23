package nd.max.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قاعدة الشواذّ — والاختبار المحوري هنا ليس «هل تنطبق القاعدة؟» بل **«هل تستطيع أن ترفع؟»**.
 *
 * وهذا سؤال لا يُجاب بمثال واحد: يُجاب بالمرور على **كل** أثر مُعلَن وكل درجة ثقة وكل توفّر،
 * والتأكّد أن النتيجة لا تتجاوز المبدأ في أي تركيبة. فوجود أثر رافع مستقبلًا يُسقط هذا الملف قبل
 * أن يُسقط أي شيء على جهاز.
 */
class AtlasQuirksTest {

    // ── لا رفعَ أبدًا، بأيّ تركيبة ────────────────────────────────────────────

    @Test
    fun `no effect can raise availability role or confidence above its baseline`() {
        val baselines = AtlasAvailability.entries.flatMap { availability ->
            AtlasInterfaceRole.entries.flatMap { role ->
                AtlasSourceConfidence.entries.map { confidence -> Triple(availability, role, confidence) }
            }
        }
        val quirks = AtlasClaimEffect.entries.map { effect ->
            // النطاق إلزامي بعقود `AtlasQuirkMatch` (قاعدة بلا نطاق تنطبق على كل جهاز)،
            // ومصدرها هنا هو نفسه مصدر الحكم — فتنطبق فعلاً وهي أسوأ حالة يُراد قياسها.
            quirk(
                id = "rule.${effect.name.lowercase()}",
                effect = effect,
                sourceScope = SOURCE,
                alternate = "alt.source",
            )
        }

        baselines.forEach { (availability, role, confidence) ->
            val verdict = AtlasQuirkBase.apply(
                quirks = quirks,
                sourceId = SOURCE,
                confidence = confidence,
                apiLevel = 34,
                availability = availability,
                role = role,
            )

            assertTrue(
                "availability rose from $availability to ${verdict.availability}",
                AtlasQuirkBase.lowerAvailability(availability, verdict.availability) == verdict.availability,
            )
            assertFalse(
                "role came back from CONTROL_PLANE_OWNED to ${verdict.role}",
                role == AtlasInterfaceRole.CONTROL_PLANE_OWNED && verdict.role != AtlasInterfaceRole.CONTROL_PLANE_OWNED,
            )
            assertTrue(
                "confidence rose from $confidence to ${verdict.confidenceCeiling}",
                AtlasConfidenceRules.lower(confidence, verdict.confidenceCeiling) == verdict.confidenceCeiling,
            )
        }
    }

    @Test
    fun `a report lowers a reviewed claim to reported and never the other way`() {
        val rule = quirk(effect = AtlasClaimEffect.MARK_CONFIDENCE_REPORTED, apiAtLeast = 30)

        val verdict = AtlasQuirkBase.apply(
            quirks = listOf(rule),
            sourceId = SOURCE,
            confidence = AtlasSourceConfidence.SOURCE_VERIFIED,
            apiLevel = 34,
        )

        assertEquals(AtlasSourceConfidence.REPORTED, verdict.confidenceCeiling)
        assertTrue(verdict.lowered)
    }

    @Test
    fun `reported is weaker than claimed and stronger than fetched`() {
        assertTrue(
            AtlasConfidenceRules.rank(AtlasSourceConfidence.REPORTED) <
                AtlasConfidenceRules.rank(AtlasSourceConfidence.CLAIMED),
        )
        assertTrue(
            AtlasConfidenceRules.rank(AtlasSourceConfidence.REPORTED) >
                AtlasConfidenceRules.rank(AtlasSourceConfidence.FETCHED),
        )
    }

    @Test
    fun `an expected denial is the lowest availability and cannot be raised back to expected`() {
        val denial = quirk(effect = AtlasClaimEffect.MARK_EXPECTED_DENIED, apiAtLeast = 30)

        val verdict = AtlasQuirkBase.apply(
            quirks = listOf(denial),
            sourceId = SOURCE,
            confidence = AtlasSourceConfidence.CLAIMED,
            apiLevel = 34,
            availability = AtlasAvailability.EXPECTED,
        )

        assertEquals(AtlasAvailability.EXPECTED_DENIED, verdict.availability)
        assertEquals(
            AtlasAvailability.EXPECTED_DENIED,
            AtlasQuirkBase.lowerAvailability(verdict.availability, AtlasAvailability.EXPECTED),
        )
    }

    @Test
    fun `an interface owned by another writer is marked as control plane owned and stays so`() {
        val rule = quirk(effect = AtlasClaimEffect.MARK_ROLE_CONTROL_PLANE_OWNED, sourceScope = SOURCE)

        val verdict = AtlasQuirkBase.apply(
            quirks = listOf(rule),
            sourceId = SOURCE,
            confidence = AtlasSourceConfidence.CLAIMED,
            role = AtlasInterfaceRole.OBSERVABLE,
        )

        assertEquals(AtlasInterfaceRole.CONTROL_PLANE_OWNED, verdict.role)
        assertEquals(
            AtlasInterfaceRole.CONTROL_PLANE_OWNED,
            AtlasQuirkBase.controlPlaneOwned(verdict.role),
        )
    }

    // ── النطاق: قاعدة بلا سياق تُطبَّق على كل جهاز، وهذا ممنوع ─────────────────

    @Test
    fun `a quirk without any scope cannot even be constructed`() {
        assertThrows(IllegalArgumentException::class.java) {
            AtlasQuirk(
                id = "quirk.no.scope",
                description = "applies everywhere, which is not knowledge",
                match = AtlasQuirkMatch(),
                effect = AtlasClaimEffect.MARK_EXPECTED_DENIED,
                sourceId = SOURCE,
                revision = "1",
            )
        }
    }

    @Test
    fun `a source scoped quirk does not touch another source`() {
        val rule = quirk(effect = AtlasClaimEffect.MARK_EXPECTED_DENIED, sourceScope = "other.source")

        val verdict = AtlasQuirkBase.apply(
            quirks = listOf(rule),
            sourceId = SOURCE,
            confidence = AtlasSourceConfidence.CLAIMED,
            apiLevel = 34,
        )

        assertEquals(AtlasAvailability.EXPECTED, verdict.availability)
        assertFalse(verdict.lowered)
        assertEquals(0, verdict.applied.size)
    }

    @Test
    fun `kernel scoped rules follow the kernel release and api rules follow the api level`() {
        val kernelRule = quirk(effect = AtlasClaimEffect.LOWER_TO_DEVICE_DEPENDENT, kernelPrefix = "4.19")
        val apiRule = quirk(effect = AtlasClaimEffect.MARK_EXPECTED_DENIED, apiAtLeast = 35)

        val onKernelMatch = AtlasQuirkBase.apply(
            quirks = listOf(kernelRule, apiRule),
            sourceId = SOURCE,
            confidence = AtlasSourceConfidence.CLAIMED,
            kernelRelease = "4.19.191-android13",
            apiLevel = 34,
        )
        val onApiMatch = AtlasQuirkBase.apply(
            quirks = listOf(kernelRule, apiRule),
            sourceId = SOURCE,
            confidence = AtlasSourceConfidence.CLAIMED,
            kernelRelease = "5.10.200",
            apiLevel = 35,
        )

        assertEquals(AtlasAvailability.DEVICE_DEPENDENT, onKernelMatch.availability)
        assertEquals(AtlasAvailability.EXPECTED_DENIED, onApiMatch.availability)
    }

    @Test
    fun `soc rules match case insensitively and never match a different soc`() {
        val rule = quirk(effect = AtlasClaimEffect.MARK_EXPECTED_DENIED, socModel = "MT6899")

        val matches = AtlasQuirkBase.apply(
            quirks = listOf(rule),
            sourceId = SOURCE,
            confidence = AtlasSourceConfidence.CLAIMED,
            socModel = "mt6899",
        )
        val other = AtlasQuirkBase.apply(
            quirks = listOf(rule),
            sourceId = SOURCE,
            confidence = AtlasSourceConfidence.CLAIMED,
            socModel = "SM8650",
        )

        assertEquals(AtlasAvailability.EXPECTED_DENIED, matches.availability)
        assertEquals(AtlasAvailability.EXPECTED, other.availability)
    }

    // ── الترتيب والجمع ───────────────────────────────────────────────────────

    @Test
    fun `application is order independent and alternate source never erases the original rule`() {
        val denial = quirk(id = "quirk.deny", effect = AtlasClaimEffect.MARK_EXPECTED_DENIED, apiAtLeast = 30)
        val alternate = quirk(
            id = "quirk.alternate",
            effect = AtlasClaimEffect.PREFER_ALTERNATE_SOURCE,
            apiAtLeast = 30,
            alternate = "alternate.source",
        )

        val forward = AtlasQuirkBase.apply(listOf(denial, alternate), SOURCE, AtlasSourceConfidence.CLAIMED, apiLevel = 34)
        val backward = AtlasQuirkBase.apply(listOf(alternate, denial), SOURCE, AtlasSourceConfidence.CLAIMED, apiLevel = 34)

        assertEquals(forward.availability, backward.availability)
        assertEquals(forward.alternateSourceId, backward.alternateSourceId)
        assertEquals(2, forward.applied.size)
        assertEquals(AtlasAvailability.EXPECTED_DENIED, forward.availability)
        assertEquals("alternate.source", forward.alternateSourceId)
    }

    @Test
    fun `preferring an alternate without naming it cannot be constructed`() {
        assertThrows(IllegalArgumentException::class.java) {
            AtlasQuirk(
                id = "quirk.alternate.empty",
                description = "prefers nothing",
                match = AtlasQuirkMatch(apiAtLeast = 30),
                effect = AtlasClaimEffect.PREFER_ALTERNATE_SOURCE,
                sourceId = SOURCE,
                revision = "1",
            )
        }
    }

    @Test
    fun `an unknown role stays unknown until a rule names it`() {
        val untouched = AtlasQuirkBase.apply(emptyList(), SOURCE, AtlasSourceConfidence.CLAIMED)

        assertEquals(AtlasInterfaceRole.UNKNOWN, untouched.role)
        assertEquals(AtlasAvailability.EXPECTED, untouched.availability)
        assertFalse(untouched.lowered)
        assertNull(untouched.alternateSourceId)
    }

    private fun quirk(
        id: String = "quirk.test",
        effect: AtlasClaimEffect,
        apiAtLeast: Int? = null,
        kernelPrefix: String? = null,
        socModel: String? = null,
        sourceScope: String? = null,
        alternate: String? = null,
    ) = AtlasQuirk(
        id = id,
        description = "a reported behavior on one device",
        match = AtlasQuirkMatch(
            socModel = socModel,
            kernelPrefix = kernelPrefix,
            apiAtLeast = apiAtLeast,
            sourceScope = sourceScope,
        ),
        effect = effect,
        sourceId = "report.2026-09",
        revision = "1",
        reportedOn = "POCO X7 / Android 15 / 5.2 (162)",
        alternateSourceId = alternate,
    )

    private companion object {
        const val SOURCE = "sys/class/devfreq/13000000.mali/fix_target_opp_index"
    }
}

/**
 * جرد التغطية — الأسباب لا تُجمع في «ناقص» واحد، لأن لكل سبب علاجًا مختلفًا.
 */
class AtlasEvidenceCoverageTest {

    @Test
    fun `a target with two fresh same boot samples is covered`() {
        val report = AtlasEvidenceCoverage.report(
            targets = listOf(TARGET),
            samplesByTarget = mapOf(TARGET to listOf(sample(0.4, 1_000L), sample(0.9, 9_000L))),
            nowElapsedMs = 10_000L,
            bootGeneration = BOOT,
        )

        assertEquals(1, report.coveredTargets)
        assertEquals(1000, report.coveragePermille)
        assertTrue(report.gaps.isEmpty())
    }

    @Test
    fun `each gap names its own reason instead of one lumped missing`() {
        // نافذة صلاحية معلنة (٥ ثوانٍ) حتى يفصل الزمن بين العيّنة القديمة (عمرها ٩ ثوانٍ
        // ⇒ متقادمة) والطازجة (عمرها ثانية ⇒ عدد لا يكفي) — وإلا فهاتان الحالتان لا تفترقان
        // في نافذة الافتراضي (١٠ دقائق) وتصنَّف كلتاهما «عيّنة واحدة».
        val report = AtlasEvidenceCoverage.report(
            targets = listOf("cpu.ceiling", "gpu.ceiling", "thermal.profile", "display.refresh"),
            samplesByTarget = mapOf(
                "gpu.ceiling" to listOf(sample(0.4, 1_000L, boot = BOOT + 1)),
                "thermal.profile" to listOf(sample(0.4, 1_000L)),
                "display.refresh" to listOf(sample(0.4, 9_000L)),
            ),
            nowElapsedMs = 10_000L,
            bootGeneration = BOOT,
            freshnessMs = 5_000L,
        )

        assertEquals(AtlasCoverageGap.NO_SAMPLE, report.gaps.single { it.target == "cpu.ceiling" }.gap)
        assertEquals(AtlasCoverageGap.OTHER_BOOT_SAMPLE, report.gaps.single { it.target == "gpu.ceiling" }.gap)
        assertEquals(AtlasCoverageGap.STALE_SAMPLE, report.gaps.single { it.target == "thermal.profile" }.gap)
        assertEquals(AtlasCoverageGap.SINGLE_SAMPLE, report.gaps.single { it.target == "display.refresh" }.gap)
        assertEquals(0, report.coveredTargets)
    }

    @Test
    fun `coverage permille is computed from the inventory not typed in`() {
        val report = AtlasEvidenceCoverage.report(
            targets = listOf("a.one", "b.two", "c.three"),
            samplesByTarget = mapOf("a.one" to listOf(sample(1.0, 1_000L), sample(1.0, 2_000L))),
            nowElapsedMs = 10_000L,
            bootGeneration = BOOT,
        )

        assertEquals(333, report.coveragePermille)
        assertEquals(2, report.gaps.size)
    }

    @Test
    fun `report lines carry stable tokens and the real counts`() {
        val report = AtlasEvidenceCoverage.report(
            targets = listOf("cpu.ceiling"),
            samplesByTarget = emptyMap(),
            nowElapsedMs = 1_000L,
            bootGeneration = BOOT,
        )

        assertEquals(
            "atlas-coverage covered=0/1 permille=0 min_samples=2 freshness_ms=600000",
            report.lines().first(),
        )
        assertTrue(report.lines()[1].contains("target=cpu.ceiling reason=no-sample samples=0"))
    }

    @Test
    fun `an empty inventory is zero coverage and not a perfect score`() {
        val report = AtlasEvidenceCoverage.report(
            targets = emptyList(),
            samplesByTarget = emptyMap(),
            nowElapsedMs = 1_000L,
            bootGeneration = BOOT,
        )

        assertEquals(0, report.totalTargets)
        assertEquals(0, report.coveragePermille)
    }

    private fun sample(value: Double, atMs: Long, boot: Long = BOOT) = AtlasEffectSample(
        metric = AtlasEffectMetric.THERMAL_HEADROOM,
        value = value,
        observedAtElapsedMs = atMs,
        source = "PowerManager.getThermalHeadroom",
        bootGeneration = boot,
    )

    private companion object {
        const val BOOT = 9L
        const val TARGET = "gpu.ceiling"
    }
}
