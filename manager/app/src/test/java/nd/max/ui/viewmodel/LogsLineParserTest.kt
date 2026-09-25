/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **النمط المرجعي** للسجلّات — يُقاس هنا مباشرةً، بنفس المُدخلات والتوقّعات المكتوبة في اختبارات
 * Rust (`logparse::tests`).
 *
 * وهذه هي الحلقة التي كانت ناقصة: النمط في Kotlin هو ما يعمل على أجهزة حقيقية، ولا يجوز أن يكون
 * نقله إلى Rust بلا **تثبيت** لدلالته. فإن انحرف أي الجانبين سقط اختبار هناك أو هنا — لا على
 * جهازك.
 */
class LogsLineParserTest {

    private fun logcat(line: String): LogsViewerViewModel.LogEntry? =
        LogsLineParser.parseLogcatBatch(listOf(line)).firstOrNull()

    @Test
    fun `threadtime line yields the same seven fields as the rust parser`() {
        val entry = logcat("09-24 18:12:03.456  1234  5678 I MaxManager: EVENT=BOOST sw=1")
        assertNotNull(entry)
        assertEquals("09-24", entry!!.date)
        assertEquals("18:12:03.456", entry.time)
        assertEquals("1234", entry.pid)
        assertEquals("5678", entry.tid)
        assertEquals(LogsViewerViewModel.LogLevel.INFO, entry.level)
        assertEquals("MaxManager", entry.tag)
        assertEquals("EVENT=BOOST sw=1", entry.message)
        assertEquals("09-24 18:12:03.456  1234  5678 I MaxManager: EVENT=BOOST sw=1", entry.raw)
    }

    @Test
    fun `tabs separate fields exactly as java regex whitespace does`() {
        val entry = logcat("09-24\t18:12:03.456\t1234\t5678\tW\tPower: message")
        assertEquals(LogsViewerViewModel.LogLevel.WARN, entry!!.level)
        assertEquals("Power", entry.tag)
        assertEquals("message", entry.message)
    }

    @Test
    fun `the tag ends at the first colon and the message keeps the rest`() {
        val entry = logcat("09-24 18:12:03.456 1 2 E My:Tag: inner: text")
        assertEquals("My", entry!!.tag)
        assertEquals("Tag: inner: text", entry.message)
    }

    @Test
    fun `a missing colon is no match so the line is kept as raw text`() {
        val entry = logcat("09-24 18:12:03.456 1 2 I no colon here")
        assertNotNull(entry)
        assertEquals("System", entry!!.tag)
        assertEquals(LogsViewerViewModel.LogLevel.VERBOSE, entry.level)
        assertEquals("09-24 18:12:03.456 1 2 I no colon here", entry.message)
        assertEquals("", entry.date)
        assertEquals("?", entry.pid)
    }

    @Test
    fun `blank and separator lines are dropped while text lines survive`() {
        assertNull(logcat(""))
        assertNull(logcat("   \t  "))
        assertNull(logcat("--------- begin ---------"))
        assertNotNull(logcat("some plain text"))
    }

    @Test
    fun `blank mirrors kotlin rules not java isWhitespace alone`() {
        // Kotlin's isBlank = Character.isWhitespace(c) || Character.isSpaceChar(c) ⇒ فواصل
        // المسافة اليونيكودية (بما فيها NBSP) فراغ، وU+0085 (NEL) ليس فراغًا عندها.
        // **وهذا القياس أمسك انحرافًا حقيقيًّا في محاكاة Rust** (كانت تستثني NBSP خطأً)، فأُصلحت
        // قبل أن يظهر الفرق في تحليل سجلّات جهاز حقيقي.
        assertNull(logcat("\u00A0"))
        assertNull(logcat("\u202F"))
        assertNull(logcat("\u2028"))
        assertNull(logcat("\u001C\u001F"))
        assertNotNull(logcat("\u0085"))
    }

    @Test
    fun `a malformed line falls back instead of being misread as fields`() {
        // مستوى صغير · تاريخ من رقم واحد · مللي غير مكتمل · أنوية ناقصة
        assertTrue(logcat("09-24 18:12:03.456 1 2 i tag: x")!!.tag == "System")
        assertTrue(logcat("9-24 18:12:03.456 1 2 I tag: x")!!.tag == "System")
        assertTrue(logcat("09-24 18:12:03.45 1 2 I tag: x")!!.tag == "System")
        assertTrue(logcat("09-24 18:12:03.456 1 I tag: x")!!.tag == "System")
    }

    @Test
    fun `signed log lines keep their timestamp level tag and message`() {
        val entry = LogsLineParser
            .parseUnifiedBatch(listOf("2026-08-27 10:15:32 I MaxManager: EVENT=PROFILE key=v"))
            .firstOrNull()
        assertNotNull(entry)
        assertEquals("2026-08-27 10:15:32", entry!!.timestamp)
        assertEquals(LogsViewerViewModel.UnifiedLogLevel.INFO, entry.level)
        assertEquals("MaxManager", entry.rawTag)
        assertEquals("EVENT=PROFILE key=v", entry.message)
        assertEquals("PROFILE", entry.eventType)
    }

    @Test
    fun `signed log rejects bad levels tags with spaces and short dates`() {
        assertTrue(LogsLineParser.parseUnifiedBatch(listOf("2026-08-27 10:15:32 X Tag: m")).isEmpty())
        assertTrue(LogsLineParser.parseUnifiedBatch(listOf("2026-08-27 10:15:32 I Ta g: m")).isEmpty())
        assertTrue(LogsLineParser.parseUnifiedBatch(listOf("2026-8-27 10:15:32 I Tag: m")).isEmpty())
        assertTrue(LogsLineParser.parseUnifiedBatch(listOf("no timestamp here")).isEmpty())
    }

    @Test
    fun `a batch keeps one entry per matching line in arrival order`() {
        val lines = listOf(
            "09-24 18:12:03.456 1 2 I Tag: first",
            "plain text",
            "",
            "09-24 18:12:03.457 1 2 I Tag: second",
        )
        val batch = LogsLineParser.parseLogcatBatch(lines)
        assertEquals(3, batch.size)
        assertEquals("first", batch[0].message)
        assertEquals("plain text", batch[1].message)
        assertEquals("second", batch[2].message)
        // والمعرّفات تتصاعد بترتيب الوصول — فمفاتيح القائمة لا تتضارب.
        assertEquals(batch[0].id + 1, batch[1].id)
        assertEquals(batch[1].id + 1, batch[2].id)
    }

    @Test
    fun `id counters can be reset for a restarted stream`() {
        LogsLineParser.resetLogcatIds()
        val first = logcat("09-24 18:12:03.456 1 2 I Tag: a")
        LogsLineParser.resetLogcatIds()
        val second = logcat("09-24 18:12:03.456 1 2 I Tag: b")
        assertEquals(first!!.id, second!!.id)
    }
}
