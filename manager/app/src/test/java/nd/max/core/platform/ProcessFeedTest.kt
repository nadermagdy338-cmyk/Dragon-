/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.platform

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * قراءة العمليات — الجزء النقيّ، مُختبَر بلا جهاز وبلا `top`.
 *
 * **وما يُختبر هنا ثلاثة أشياء لا تُرى بالعين على شاشة:**
 *
 * 1. **العيّنة الأخيرة هي المرجع**: `top` يُخرج أكثر من عيّنة في الأمر الواحد، وقراءة الأولى
 *    تُعطي متوسّط ما بعد الإقلاع — وهو ما يجعل خامل الجهاز يبدو أثقل من مشغوله.
 * 2. **الغائب ليس صفرًا**: قائمة فارغة في `arrange` تعني «لا شيء يطابق»، والفشل له نوعه
 *    ([ProcessSample.failed]) فلا يُعرض «لا عمليات» حين يكون الخبر أنّ القراءة لم تصل.
 * 3. **التصفية والترتيب على عيّنة واحدة**: النطاق والبحث والترتيب والقَصّ تُنتج قائمة واحدة
 *    مفهومة، ولا تعتمد على ترتيب وصل الصفوف من الجهاز.
 */
class ProcessFeedTest {

    private val header = "  PID   RES  %CPU NAME"

    private var defaultLocale: Locale? = null

    /**
     * الأرقام تُنسَّق بـ`"%.1f".format(…)` كما في بقية المشروع (تتبع لغة الجهاز)، فالاختبار
     * **يُثبّت اللغة** بدل أن يعتمد على ما صادف أنّه مضبوط على الجهاز الذي يشغّله: اختبار ينجح
     * على جهاز ويفشل على آخر ليس اختبارًا.
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

    private fun reading(
        pid: Int,
        label: String,
        pkg: String = "com.example.app$pid",
        cpu: Float = 0f,
        kb: Long = 0,
        system: Boolean = false
    ) = ProcessReading(
        pid = pid,
        cpuPercent = cpu,
        residentKb = kb,
        packageName = pkg,
        label = label,
        isSystem = system
    )

    @Test
    fun `العيّنة الأخيرة هي المرجع لا الأولى`() {
        val lines = listOf(
            header,
            "  1  1.0M   50.0  com.old.app",
            "Task summary line that is not a row",
            header,
            "  2  2.0M   10.0  com.new.app"
        )
        val rows = ProcessFeed.parseSample(lines)
        assertEquals(1, rows.size)
        assertEquals(2, rows.first().pid)
        assertEquals(10f, rows.first().cpuPercent, 0.001f)
    }

    @Test
    fun `بلا سطر ترويسة لا تخمين`() {
        val rows = ProcessFeed.parseSample(listOf("  1  1.0M   50.0  com.old.app"))
        assertTrue("صفوف بلا ترويسة = لا شيء، لا قراءة جزئية", rows.isEmpty())
    }

    @Test
    fun `الوحدات تُقرأ كما يكتبها top`() {
        val lines = listOf(
            header,
            "  1  1024   1.0  com.a",        // مجرّد رقم = كيلوبايت
            "  2  1.5M   2.0  com.b",        // 1536
            "  3  2G     3.0  com.c",        // 2 097 152
            "  4  440K   4.0  com.d"         // 440
        )
        val sizes = ProcessFeed.parseSample(lines).associate { it.pid to it.residentKb }
        assertEquals(1024L, sizes[1])
        assertEquals(1536L, sizes[2])
        assertEquals(2_097_152L, sizes[3])
        assertEquals(440L, sizes[4])
    }

    @Test
    fun `النطاق يصفّي والنظاميّ يُستثنى`() {
        val readings = listOf(
            reading(1, "Chat", cpu = 5f),
            reading(2, "surfaceflinger", pkg = "surfaceflinger", system = true),
            reading(3, "Camera", cpu = 9f)
        )
        val apps = ProcessFeed.arrange(readings, ProcessScope.Apps, "", ProcessSort.Cpu, 10)
        val system = ProcessFeed.arrange(readings, ProcessScope.System, "", ProcessSort.Cpu, 10)
        assertEquals(listOf("Camera", "Chat"), apps.map { it.label })
        assertEquals(listOf("surfaceflinger"), system.map { it.label })
        assertEquals(3, ProcessFeed.arrange(readings, ProcessScope.All, "", ProcessSort.Cpu, 10).size)
    }

    @Test
    fun `البحث يطابق الاسم المعروض واسم الحزمة معًا`() {
        val readings = listOf(
            reading(1, "Chat", pkg = "com.acme.chat"),
            reading(2, "Camera", pkg = "com.acme.camera")
        )
        assertEquals(
            listOf("Chat"),
            ProcessFeed.arrange(readings, ProcessScope.All, "acme.chat", ProcessSort.Cpu, 10).map { it.label }
        )
        assertEquals(
            2,
            ProcessFeed.arrange(readings, ProcessScope.All, "acme", ProcessSort.Cpu, 10).size
        )
        assertTrue(ProcessFeed.arrange(readings, ProcessScope.All, "nothing", ProcessSort.Cpu, 10).isEmpty())
    }

    @Test
    fun `الترتيب بالذاكرة يخالف الترتيب بالمعالج`() {
        val readings = listOf(
            reading(1, "A", cpu = 40f, kb = 100),
            reading(2, "B", cpu = 5f, kb = 900),
            reading(3, "C", cpu = 12f, kb = 400)
        )
        assertEquals(
            listOf("A", "C", "B"),
            ProcessFeed.arrange(readings, ProcessScope.All, "", ProcessSort.Cpu, 10).map { it.label }
        )
        assertEquals(
            listOf("B", "C", "A"),
            ProcessFeed.arrange(readings, ProcessScope.All, "", ProcessSort.Memory, 10).map { it.label }
        )
        assertEquals(
            listOf("A", "B", "C"),
            ProcessFeed.arrange(readings, ProcessScope.All, "", ProcessSort.Name, 10).map { it.label }
        )
    }

    @Test
    fun `القَصّ يقع بعد الترتيب لا قبله`() {
        val readings = (1..10).map { reading(it, "App$it", cpu = it.toFloat(), kb = it * 10L) }
        val top = ProcessFeed.arrange(readings, ProcessScope.All, "", ProcessSort.Memory, 3)
        assertEquals(listOf("App10", "App9", "App8"), top.map { it.label })
        assertTrue(ProcessFeed.arrange(readings, ProcessScope.All, "", ProcessSort.Cpu, 0).isEmpty())
    }

    @Test
    fun `التعداد على ما قُرئ لا على ما عُرض`() {
        val readings = listOf(
            reading(1, "Chat"),
            reading(2, "Camera"),
            reading(3, "surfaceflinger", system = true)
        )
        val tally = ProcessFeed.tally(readings)
        assertEquals(3, tally.running)
        assertEquals(2, tally.apps)
        assertEquals(1, tally.system)
    }

    @Test
    fun `غياب الجواب له نوعه لا قائمةٌ فارغة`() {
        val noAnswer = ProcessSample.noAnswer
        assertTrue(noAnswer.failed)
        assertTrue(noAnswer.readings.isEmpty())
        // وقائمة فارغة **بقراءة ناجحة** حالة أخرى: تصفية بلا نتيجة، لا جهازًا لم يُقرأ.
        val emptyButRead = ProcessSample(emptyList(), ProcessFeed.tally(emptyList()), failed = false)
        assertTrue(!emptyButRead.failed)
    }

    @Test
    fun `نصّ الرقم يُبنى من الحقل العددي نفسه`() {
        assertEquals("512 KB", reading(1, "A", kb = 512).residentText)
        assertEquals("1.5 MB", reading(1, "A", kb = 1536).residentText)
        assertEquals("12.3%", reading(1, "A", cpu = 12.345f).cpuText)
    }
}
