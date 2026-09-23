package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختيار مجسّات الشبكة الحرارية — القواعد الثلاث المعلنة تُقاس هنا لا في الواجهة:
 * حدّ أربع فئات، وشبكة لا تنفرغ، ومحفوظ تالف يُطهَّر لا يُكسر.
 */
class ThermalGridModelTest {

    @Test
    fun `stored void falls back to the default grid`() {
        assertEquals(ThermalGridModel.DEFAULT, ThermalGridModel.normalize(emptyList()))
    }

    @Test
    fun `stored garbage falls back to the default grid`() {
        assertEquals(
            ThermalGridModel.DEFAULT,
            ThermalGridModel.normalize(listOf("", "  ", "NoSuchZone", "cpu-0")),
        )
    }

    @Test
    fun `unknown tokens drop while known ones survive`() {
        assertEquals(
            listOf("GPU", "Charger"),
            ThermalGridModel.normalize(listOf("bogus", "GPU", "Charger")),
        )
    }

    @Test
    fun `duplicates collapse keeping the first position`() {
        assertEquals(
            listOf("GPU", "CPU"),
            ThermalGridModel.normalize(listOf("GPU", "CPU", "GPU", "CPU")),
        )
    }

    @Test
    fun `stored selection beyond the limit truncates`() {
        val stored = listOf("CPU", "GPU", "Skin", "Battery", "Charger", "Modem")
        assertEquals(ThermalGridModel.LIMIT, ThermalGridModel.normalize(stored).size)
        assertEquals(listOf("CPU", "GPU", "Skin", "Battery"), ThermalGridModel.normalize(stored))
    }

    @Test
    fun `normalization keeps the user's own order`() {
        assertEquals(
            listOf("Modem", "CPU", "System"),
            ThermalGridModel.normalize(listOf("Modem", "CPU", "System")),
        )
    }

    @Test
    fun `every declared category is acceptable as stored`() {
        val roundTrip = ThermalGridModel.normalize(ThermalGridModel.KNOWN)
        assertEquals(ThermalGridModel.KNOWN.take(ThermalGridModel.LIMIT), roundTrip)
    }

    @Test
    fun `toggle pins one more category`() {
        val pinned = ThermalGridModel.toggle(listOf("CPU", "GPU"), "Charger")
        assertEquals(listOf("CPU", "GPU", "Charger"), pinned)
    }

    @Test
    fun `toggle unpins a pinned category`() {
        assertEquals(
            listOf("CPU", "Skin"),
            ThermalGridModel.toggle(listOf("CPU", "GPU", "Skin"), "GPU"),
        )
    }

    @Test
    fun `the grid never empties - the last pin stays`() {
        assertEquals(listOf("CPU"), ThermalGridModel.toggle(listOf("CPU"), "CPU"))
    }

    @Test
    fun `a new pin at the limit is refused and the pinned stay`() {
        val pinned = listOf("CPU", "GPU", "Skin", "Battery")
        assertEquals(pinned, ThermalGridModel.toggle(pinned, "Charger"))
    }

    @Test
    fun `toggling an unknown token is a no-op`() {
        val pinned = listOf("CPU", "GPU")
        assertEquals(pinned, ThermalGridModel.toggle(pinned, "Not-A-Zone"))
    }

    @Test
    fun `options always carry the defaults`() {
        val options = ThermalGridModel.options(emptyList())
        ThermalGridModel.DEFAULT.forEach { assertTrue("الافتراضي حاضر دائمًا: $it", it in options) }
    }

    @Test
    fun `options expose only categories this kernel measures`() {
        val options = ThermalGridModel.options(setOf("Charger", "Modem", "Ghost"))
        assertTrue("Charger" in options)
        assertTrue("Modem" in options)
        assertFalse("Ghost in options", "Ghost" in options)
        assertFalse("Camera not measured by this kernel", "Camera" in options)
    }

    @Test
    fun `options keep the declared order regardless of input order`() {
        val options = ThermalGridModel.options(setOf("Modem", "Charger"))
        assertEquals(options, options.sortedBy { ThermalGridModel.KNOWN.indexOf(it) })
    }
}
