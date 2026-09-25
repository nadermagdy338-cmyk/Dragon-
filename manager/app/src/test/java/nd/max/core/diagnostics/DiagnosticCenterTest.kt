/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * عقد مركز التشخيص: يعمل دائمًا، يزيل التكرار، يحوّل أول ظهور فقط،
 * ولا يحوّل INFO أبدًا. هذا المكوّن هو أساس حلقة التشخيص المغلقة —
 * أي انحدار فيه يعني بلاغات مستخدمين بلا أثر في السجل المُصدَّر.
 */
class DiagnosticCenterTest {

    private val forwarded = mutableListOf<Triple<String, DiagnosticCenter.Level, String>>()
    private val productionForwarder = DiagnosticCenter.forwarder

    @Before
    fun setUp() {
        DiagnosticCenter.resetForTesting()
        forwarded.clear()
        // حاقن معزول: لا محاولة shell في بيئة JVM إطلاقًا.
        DiagnosticCenter.forwarder = { c, l, m -> forwarded.add(Triple(c, l, m)) }
    }

    @After
    fun tearDown() {
        DiagnosticCenter.resetForTesting()
        DiagnosticCenter.forwarder = productionForwarder
    }

    @Test
    fun `record deduplicates identical entries by incrementing count`() {
        repeat(5) {
            DiagnosticCenter.record("jni", "nativePredictLoad failed :: boom")
        }
        val block = DiagnosticCenter.formatBlock()
        assertTrue(block.contains("(x5)"))
        assertTrue(block.contains("nativePredictLoad failed"))
        // مدخل واحد فقط في المخزن رغم 5 تسجيلات
        assertEquals(1, Regex("\\[ERROR\\]").findAll(block).count())
    }

    @Test
    fun `forwarding happens only on first occurrence`() {
        repeat(10) {
            DiagnosticCenter.record("jni", "nativePredictLoad failed :: boom")
        }
        assertEquals(1, forwarded.size)
        assertEquals("jni", forwarded.first().first)
        assertEquals(DiagnosticCenter.Level.ERROR, forwarded.first().second)
    }

    @Test
    fun `INFO entries never forward and never count as issues`() {
        DiagnosticCenter.record("engine", "cyclic tick", level = DiagnosticCenter.Level.INFO)
        assertEquals(0, forwarded.size)
        assertEquals(0, DiagnosticCenter.issueCount.value)
        assertFalse(DiagnosticCenter.hasIssues())
    }

    @Test
    fun `issueCount tracks WARN and ERROR only`() {
        DiagnosticCenter.record("a", "w1", level = DiagnosticCenter.Level.WARN)
        DiagnosticCenter.record("a", "e1", level = DiagnosticCenter.Level.ERROR)
        DiagnosticCenter.record("a", "i1", level = DiagnosticCenter.Level.INFO)
        assertEquals(2, DiagnosticCenter.issueCount.value)
        assertTrue(DiagnosticCenter.hasIssues())
    }

    @Test
    fun `different messages create distinct entries`() {
        DiagnosticCenter.record("jni", "nativePredictLoad failed")
        DiagnosticCenter.record("jni", "nativeAnalyzeThermal failed")
        assertEquals(2, DiagnosticCenter.issueCount.value)
    }

    @Test
    fun `same message different component stays separate`() {
        DiagnosticCenter.record("jni", "op failed")
        DiagnosticCenter.record("engine", "op failed")
        val block = DiagnosticCenter.formatBlock()
        assertTrue(block.contains("jni: op failed"))
        assertTrue(block.contains("engine: op failed"))
    }

    @Test
    fun `formatBlock reports empty state explicitly`() {
        val block = DiagnosticCenter.formatBlock()
        assertTrue(block.contains("no issues recorded this session"))
    }

    @Test
    fun `ring buffer caps at 64 entries`() {
        repeat(70) { i -> DiagnosticCenter.record("c$i", "m$i") }
        // أحدث 64 تبقى؛ المدخل الأول (c0) طُرد، والأخير (m69) موجود.
        val block = DiagnosticCenter.formatBlock()
        assertFalse(block.contains("m0:"))
        assertTrue(block.contains("m69"))
        assertEquals(64, Regex("\\[ERROR\\]").findAll(block).count())
    }

    @Test
    fun `messages are capped to 240 chars`() {
        // 'y' لا يظهر في أي موضع آخر بالسطر — لا في [ERROR] ولا الطابع
        // الزمني ولا عدّاد التكرار (x1) — فيبقى العدّ مطابقًا للرسالة
        // المقتطعة وحدها. (النسخة السابقة عدّت 'x' فأخطأت بعلامة (x1).)
        val long = "y".repeat(500)
        DiagnosticCenter.record("c", long)
        val block = DiagnosticCenter.formatBlock()
        val line = block.lineSequence().first { it.contains("yyy") }
        assertEquals(240, line.count { it == 'y' })
    }

    @Test
    fun `daemon level mapping matches CLIUtility contract`() {
        // CLIUtility.c: 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR, 4=FATAL
        assertEquals(1, DiagnosticCenter.Level.INFO.daemonLevel)
        assertEquals(2, DiagnosticCenter.Level.WARN.daemonLevel)
        assertEquals(3, DiagnosticCenter.Level.ERROR.daemonLevel)
    }

    @Test
    fun `record is safe to call from concurrent threads`() {
        val threads = (1..8).map {
            Thread {
                repeat(50) {
                    DiagnosticCenter.record("jni", "nativePredictLoad failed")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // 400 تسجيلًا متزامنًا: مدخل واحد، عدّاد 400، بلا انهيار
        assertTrue(DiagnosticCenter.formatBlock().contains("(x400)"))
        assertEquals(1, forwarded.size)
    }
}
