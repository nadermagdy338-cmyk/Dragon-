package nd.max.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * جسر الأسطر — وحالتان من حزمة جهاز حقيقي تحكمان هذا الملف:
 *
 * ```
 * WRITE_CHECK … mali/max_freq wrote=520000000 matched     (بروفايل power)
 * PERAPP_GPU_CAPABILITY_REQUESTED requested=702000000 live_before=520000000
 * PERAPP_COMMIT … applied=true verified=true live=520000000
 * ```
 *
 * الثالث هو العطب: «نجاح» بلا حركة. والسطر الذي يفرّق بينهما هو ما يُختبر هنا.
 */
class AtlasEffectLinesTest {

    @Test
    fun `a raise that landed is reported as improved with both readings and the goal`() {
        val payload = AtlasEffectLines.requestEffect(
            metric = AtlasEffectMetric.GPU_FREQUENCY_INSTANT,
            before = 520_000_000L,
            after = 702_000_000L,
            requested = 702_000_000L,
            source = SOURCE,
            atMs = 1_000L,
        )

        assertTrue(payload.startsWith("improved"))
        assertTrue(payload.contains("before=520000000"))
        assertTrue(payload.contains("after=702000000"))
        assertTrue(payload.contains("requested=702000000"))
        assertTrue(payload.contains("goal=higher-is-better"))
        assertTrue(payload.contains("confidence=measured"))
        assertEquals("effect=$payload", AtlasEffectLines.line(payload))
    }

    @Test
    fun `the same movement is judged by the goal so a cut that landed is also an improvement`() {
        val payload = AtlasEffectLines.requestEffect(
            metric = AtlasEffectMetric.GPU_FREQUENCY_INSTANT,
            before = 1_092_000_000L,
            after = 520_000_000L,
            requested = 520_000_000L,
            source = SOURCE,
            atMs = 1_000L,
        )

        assertTrue(payload.startsWith("improved"))
        assertTrue(payload.contains("goal=lower-is-better"))
    }

    @Test
    fun `a successful write that did not move the clock is not dressed as success`() {
        // حزمة ٢٠٢٦-٠٩-٢٢ حرفيًّا: طُلب ٧٠٢ والقراءة بقيت ٥٢٠.
        val payload = AtlasEffectLines.requestEffect(
            metric = AtlasEffectMetric.GPU_FREQUENCY_INSTANT,
            before = 520_000_000L,
            after = 520_000_000L,
            requested = 702_000_000L,
            source = SOURCE,
            atMs = 1_000L,
        )

        assertTrue(payload.startsWith("unchanged"))
        assertTrue(payload.contains(AtlasEffectReasons.WITHIN_TOLERANCE))
    }

    @Test
    fun `an unread side states unmeasured with its reason instead of pretending no change`() {
        val payload = AtlasEffectLines.requestEffect(
            metric = AtlasEffectMetric.GPU_FREQUENCY_INSTANT,
            before = null,
            after = 702_000_000L,
            requested = 702_000_000L,
            source = SOURCE,
            atMs = 1_000L,
        )

        assertTrue(payload.startsWith(AtlasEffectLines.UNMEASURED))
        assertTrue(payload.contains("reason=${AtlasEffectReasons.MISSING_SAMPLE}"))
        assertFalse(payload.contains("unchanged"))
    }

    @Test
    fun `a request equal to the live value declares no goal rather than guessing one`() {
        val payload = AtlasEffectLines.requestEffect(
            metric = AtlasEffectMetric.GPU_FREQUENCY_INSTANT,
            before = 702_000_000L,
            after = 702_000_000L,
            requested = 702_000_000L,
            source = SOURCE,
            atMs = 1_000L,
        )

        assertTrue(payload.contains("goal=none"))
    }

    @Test
    fun `goal direction comes from the numbers and an unknown side yields none`() {
        assertEquals(AtlasEffectDirection.HIGHER_IS_BETTER, AtlasEffectLines.goalDirection(520L, 702L))
        assertEquals(AtlasEffectDirection.LOWER_IS_BETTER, AtlasEffectLines.goalDirection(1_092L, 520L))
        assertEquals(null, AtlasEffectLines.goalDirection(520L, 520L))
        assertEquals(null, AtlasEffectLines.goalDirection(null, 702L))
        assertEquals(null, AtlasEffectLines.goalDirection(520L, null))
    }

    @Test
    fun `the payload never contains whitespace because the status channel splits lines on it`() {
        val payloads = listOf(
            AtlasEffectLines.requestEffect(AtlasEffectMetric.GPU_FREQUENCY_INSTANT, 520L, 702L, 702L, SOURCE, 1_000L),
            AtlasEffectLines.requestEffect(AtlasEffectMetric.GPU_FREQUENCY_INSTANT, null, 702L, 702L, SOURCE, 1_000L),
            AtlasEffectLines.requestEffect(AtlasEffectMetric.CPU_FREQUENCY_INSTANT, 1_000L, 1_000L, 1_200L, SOURCE, 1_000L),
        )

        payloads.forEach { payload ->
            assertFalse("a payload with a space would split into two fields: $payload", payload.any { it.isWhitespace() })
            assertTrue(payload.isNotBlank())
        }
    }

    @Test
    fun `an unchanged reading well inside tolerance is unchanged and not a rounding success`() {
        val payload = AtlasEffectLines.requestEffect(
            metric = AtlasEffectMetric.GPU_FREQUENCY_INSTANT,
            before = 520_000_000L,
            after = 521_000_000L,
            requested = 702_000_000L,
            source = SOURCE,
            atMs = 1_000L,
            tolerancePermille = 30L,
        )

        assertTrue(payload.startsWith("unchanged"))
    }

    private companion object {
        const val SOURCE = "sys/class/devfreq/13000000.mali"
    }
}
