/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.platform

import java.util.Locale
import nd.max.ui.component.hudFieldText
import nd.max.ui.component.hudFrameTimeText
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * منطق الجلسة في لوحة الأداء — الجزء النقيّ، مُختبَر بلا جهاز.
 *
 * **ولماذا هنا:** الجلسة كُتبت لتُقاس، فلا يصحّ أن يكون قياسها نفسه غير مُختبَر. وما يُختبر
 * ثلاثة أشياء لا تُرى بالعين على شاشة: **الغائب لا يصير صفرًا** في الإحصاء ولا في الملفّ،
 * و**سقف الذاكرة يُعلَن** ولا يُخفى (فالإحصاء يصف آخر العيّنات لا الجلسة كلها)، و**زمن الملفّ
 * نسبيّ** لأوّل عيّنة فيُقارَن بين جهازين.
 */
class HudSessionTest {

    private var defaultLocale: Locale? = null

    /**
     * الأرقام تُنسَّق بـ`"%.1f".format(…)` تتبع لغة الجهاز، فتُثبَّت هنا كما في `ProcessFeedTest`:
     * اختبار ينجح على جهاز ويفشل على آخر ليس اختبارًا — ول "١٦٫٧" و"16.7" يختلفان فاصلةً ورقمًا.
     */
    @Before
    fun pinLocale() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        defaultLocale?.let { Locale.setDefault(it) }
    }

    @Test
    fun `a missing reading stays null and never becomes zero`() {
        val reading = HudReading(frames = 60f, heat = null, cpu = null)
        assertEquals(60f, reading.frames)
        assertNull(reading.heat)
        assertNull(reading.cpu)
        assertTrue(reading.framesAnswered)
        assertFalse(HudReading().framesAnswered)
    }

    @Test
    fun `empty journal reports a null statistic, not a fabricated zero`() {
        val tally = HudJournal().tally()
        assertEquals(0, tally.samples)
        assertTrue(tally.isEmpty)
        assertNull(tally.framesAverage)
        assertNull(tally.framesLow)
        assertNull(tally.heatPeak)
        assertNull(tally.powerAverage)
    }

    @Test
    fun `tally averages only the samples that answered`() {
        val journal = HudJournal()
        journal.record(HudReading(frames = 60f, heat = 40f, watt = 4f), 1_000)
        journal.record(HudReading(frames = 30f, heat = null, watt = 6f), 2_000)
        journal.record(HudReading(frames = 90f, heat = 44f, watt = null), 3_000)

        val tally = journal.tally()
        assertEquals(3, tally.samples)
        assertEquals(2_000L, tally.spanMs)
        assertEquals(60f, tally.framesAverage!!, 0.001f)   // (60+30+90)/3
        assertEquals(30f, tally.framesLow!!, 0.001f)
        assertEquals(90f, tally.framesHigh!!, 0.001f)
        assertEquals(44f, tally.heatPeak!!, 0.001f)
        assertEquals(5f, tally.powerAverage!!, 0.001f)     // (4+6)/2 — والغائب ليس صفرًا
    }

    @Test
    fun `a zero frame count is treated as no answer, not as a dead device`() {
        val journal = HudJournal()
        journal.record(HudReading(frames = 0f), 1_000)
        journal.record(HudReading(frames = 45f), 2_000)
        val tally = journal.tally()
        assertEquals(2, tally.samples)
        assertEquals(45f, tally.framesAverage!!, 0.001f)
        assertEquals(45f, tally.framesLow!!, 0.001f)
    }

    @Test
    fun `over the cap the oldest samples drop and the drop is reported`() {
        val journal = HudJournal(capacity = 3)
        repeat(5) { index -> journal.record(HudReading(frames = index.toFloat() + 1f), (index + 1) * 1_000L) }

        val tally = journal.tally()
        assertEquals(3, tally.samples)
        assertEquals(2, tally.droppedSamples)
        assertEquals(3f, tally.framesLow!!, 0.001f)   // العيّنتان الأولى والثانية سقطتا
        assertEquals(5f, tally.framesHigh!!, 0.001f)
        assertEquals(2_000L, tally.spanMs)            // المدى من الباقي لا من بداية الجلسة
    }

    @Test
    fun `clearing a session leaves nothing behind`() {
        val journal = HudJournal()
        journal.record(HudReading(frames = 60f), 1_000)
        journal.clear()
        assertEquals(0, journal.size)
        assertEquals(0, journal.droppedSamples)
        assertTrue(journal.tally().isEmpty)
    }

    @Test
    fun `csv keeps the header, writes empty cells for absent values and counts from the first row`() {
        val rows = listOf(
            HudJournal.Sample(10_000, HudReading(frames = 59.5f, cpu = 24, heat = 38.4f)),
            HudJournal.Sample(11_000, HudReading(frames = 61f, cpu = null, heat = 39f))
        )
        val lines = hudSessionCsv(rows).trim().split("\n")
        assertEquals(3, lines.size)
        assertEquals("elapsed_ms,frames,cpu_percent,ram_mb,watt,battery_c,renderer", lines[0])
        assertEquals("0,59.5,24,,,38.4,", lines[1])
        assertEquals("1000,61.0,,,,39.0,", lines[2])
    }

    @Test
    fun `csv of an empty session is a header alone, not a broken file`() {
        assertEquals("elapsed_ms,frames,cpu_percent,ram_mb,watt,battery_c,renderer\n", hudSessionCsv(emptyList()))
    }

    @Test
    fun `csv never lets a renderer name break the column count`() {
        val rows = listOf(HudJournal.Sample(1_000, HudReading(frames = 30f, renderer = "Adreno, Vulkan")))
        val body = hudSessionCsv(rows).trim().split("\n")[1]
        assertEquals(7, body.split(",").size)
        assertTrue(body.endsWith("Adreno  Vulkan"))
    }

    @Test
    fun `recorder keeps a stopped session until it is discarded`() {
        HudRecorder.clear()
        assertFalse(HudRecorder.isLive)
        HudRecorder.start()
        assertTrue(HudRecorder.isLive)
        HudRecorder.feed(HudReading(frames = 60f), 1_000)
        HudRecorder.stop()
        assertFalse(HudRecorder.isLive)
        assertEquals(1, HudRecorder.tally()!!.samples)   // الإيقاف لا يُتلف ما سُجّل
        HudRecorder.clear()
        assertNull(HudRecorder.tally())
    }

    @Test
    fun `a recorder that is not live ignores what it is fed`() {
        HudRecorder.clear()
        HudRecorder.feed(HudReading(frames = 60f), 1_000)
        assertNull(HudRecorder.tally())
    }

    @Test
    fun `field text renders the dash for anything the source did not answer`() {
        val reading = HudReading(frames = 61.4f, cpu = 22, watt = null, heat = 38.6f)
        assertEquals("61", hudFieldText(reading, HudField.Frames))
        assertEquals("22%", hudFieldText(reading, HudField.Cpu))
        assertEquals("39°C", hudFieldText(reading, HudField.Heat))
        assertEquals(MAX_VALUE_UNAVAILABLE, hudFieldText(reading, HudField.Power))
        assertEquals(MAX_VALUE_UNAVAILABLE, hudFieldText(reading, HudField.Renderer))
        assertEquals(MAX_VALUE_UNAVAILABLE, hudFieldText(null, HudField.Frames))
    }

    @Test
    fun `the ring has no arrangement to offer and says so`() {
        assertTrue(HudForm.Strip.acceptsArrangement())
        assertTrue(HudForm.Pane.acceptsArrangement())
        assertTrue(HudForm.Badge.acceptsArrangement())
        assertFalse(HudForm.Ring.acceptsArrangement())
    }

    @Test
    fun `frame time turns the frame rate into milliseconds and refuses to divide by nothing`() {
        assertEquals("16.7 ms", hudFrameTimeText(60f))
        assertEquals("33.3 ms", hudFrameTimeText(30f))
        // ما لم يُقَس لا يُرقَّم: الغائب والصفر والسالب كلّها شرطة لا `∞` ولا صفرًا.
        assertEquals(MAX_VALUE_UNAVAILABLE, hudFrameTimeText(null))
        assertEquals(MAX_VALUE_UNAVAILABLE, hudFrameTimeText(0f))
        assertEquals(MAX_VALUE_UNAVAILABLE, hudFrameTimeText(-4f))
    }
}
