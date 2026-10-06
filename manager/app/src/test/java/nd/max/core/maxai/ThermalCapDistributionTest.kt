/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.maxai

import nd.max.core.maxai.ThermalCapDistribution.Config
import nd.max.core.maxai.ThermalCapDistribution.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يقيس توزيع السقف الحراري على العناقيد — وهو الإصلاح الذي طلبه المالك صراحةً: لا تُخفض
 * كل الأنوية إلى الحدّ الأدنى، بل يُخفَّض العنقود الأكبر أكثر ويبقى الأصغر قابلًا للاستعمال.
 *
 * القياس هنا بلا Android وبلا عتاد: الموزِّع دالّة نقية، فتُقاس بكسور صريحة.
 */
class ThermalCapDistributionTest {

    private val clusters = listOf(
        ThermalCapDistribution.Cluster("policy0", 2_000_000L),
        ThermalCapDistribution.Cluster("policy4", 2_850_000L),
        ThermalCapDistribution.Cluster("policy7", 3_350_000L),
    )

    @Test
    fun `roles follow the range order not the vendor names`() {
        val roles = ThermalCapDistribution.roles(clusters)
        assertEquals(Role.LITTLE, roles["policy0"])
        assertEquals(Role.MID, roles["policy4"])
        assertEquals(Role.PRIME, roles["policy7"])
    }

    @Test
    fun `discovery order does not matter`() {
        val shuffled = clusters.reversed()
        assertEquals(
            ThermalCapDistribution.fractions(clusters, 0.35f),
            ThermalCapDistribution.fractions(shuffled, 0.35f),
        )
    }

    @Test
    fun `the small cluster is lifted and the prime cluster is not`() {
        val fractions = ThermalCapDistribution.fractions(clusters, 0.35f)
        assertEquals(0.60f, fractions.getValue("policy0"), 1e-4f)
        assertEquals(0.47f, fractions.getValue("policy4"), 1e-4f)
        assertEquals(0.35f, fractions.getValue("policy7"), 1e-4f)
    }

    @Test
    fun `no cluster is ever capped harder than the requested fraction`() {
        for (base in listOf(0.05f, 0.25f, 0.35f, 0.55f, 0.8f, 1f)) {
            val fractions = ThermalCapDistribution.fractions(clusters, base)
            for ((name, fraction) in fractions) {
                assertTrue(
                    "$name at base=$base gave $fraction",
                    fraction >= base.coerceIn(0f, 1f) - 1e-4f,
                )
                assertTrue("$name at base=$base gave $fraction", fraction <= 1f)
            }
        }
    }

    @Test
    fun `floors hold at the harshest overshoot`() {
        val fractions = ThermalCapDistribution.fractions(clusters, 0.10f)
        assertEquals(0.60f, fractions.getValue("policy0"), 1e-4f)
        assertEquals(0.45f, fractions.getValue("policy4"), 1e-4f)
        assertEquals(0.25f, fractions.getValue("policy7"), 1e-4f)
    }

    /**
     * جهاز بعنقود واحد: لا تُخفَّف حمايته. لو صُنِّف `LITTLE` لأخذ رفعًا ٠٫٢٥ فصار السقف ٠٫٦٠ من
     * مدى الجهاز كله في أخطر لحظة — أي تخفيف الحماية عن كل النوى بلا مقابل، لأن التوزيع نفسه
     * بلا معنى هناك (لا «خلفية» و«أداء» يفترقان). فيبقى على القاعدة القديمة.
     */
    @Test
    fun `a single cluster keeps the plain fraction and gets no lift`() {
        val one = listOf(ThermalCapDistribution.Cluster("policy0", 2_000_000L))
        val fractions = ThermalCapDistribution.fractions(one, 0.35f)
        assertEquals(Role.PRIME, ThermalCapDistribution.roles(one)["policy0"])
        assertEquals(0.35f, fractions.getValue("policy0"), 1e-4f)
    }

    @Test
    fun `two clusters are the small one and the prime one`() {
        val two = listOf(
            ThermalCapDistribution.Cluster("policy0", 1_800_000L),
            ThermalCapDistribution.Cluster("policy6", 3_200_000L),
        )
        val roles = ThermalCapDistribution.roles(two)
        assertEquals(Role.LITTLE, roles["policy0"])
        assertEquals(Role.PRIME, roles["policy6"])
    }

    @Test
    fun `an open ceiling writes nothing`() {
        val fractions = ThermalCapDistribution.fractions(clusters, 1f)
        assertEquals(setOf(1f), fractions.values.toSet())
    }

    @Test
    fun `equal ranges are separated by name so roles never swap between cycles`() {
        val tied = listOf(
            ThermalCapDistribution.Cluster("policy4", 2_400_000L),
            ThermalCapDistribution.Cluster("policy0", 2_400_000L),
        )
        val roles = ThermalCapDistribution.roles(tied)
        assertEquals(Role.LITTLE, roles["policy0"])
        assertEquals(Role.PRIME, roles["policy4"])
        assertEquals(ThermalCapDistribution.roles(tied), ThermalCapDistribution.roles(tied.reversed()))
    }

    @Test
    fun `tightening the base never loosens any cluster`() {
        val open = ThermalCapDistribution.fractions(clusters, 0.55f)
        val tight = ThermalCapDistribution.fractions(clusters, 0.35f)
        for (name in open.keys) {
            assertTrue("$name", tight.getValue(name) <= open.getValue(name) + 1e-4f)
        }
    }

    @Test
    fun `an out of range base is clamped instead of trusted`() {
        val low = ThermalCapDistribution.fractions(clusters, -3f)
        val high = ThermalCapDistribution.fractions(clusters, 9f)
        assertEquals(ThermalCapDistribution.fractions(clusters, 0f), low)
        assertEquals(setOf(1f), high.values.toSet())
    }

    @Test
    fun `the distribution is inert for an empty device`() {
        assertTrue(ThermalCapDistribution.fractions(emptyList(), 0.35f).isEmpty())
        assertTrue(ThermalCapDistribution.roles(emptyList()).isEmpty())
    }

    @Test
    fun `a custom config is honoured`() {
        val config = Config(littleLift = 0f, midLift = 0f, littleFloor = 0f, midFloor = 0f, primeFloor = 0f)
        val fractions = ThermalCapDistribution.fractions(clusters, 0.35f, config)
        assertEquals(setOf(0.35f), fractions.values.toSet())
    }
}
