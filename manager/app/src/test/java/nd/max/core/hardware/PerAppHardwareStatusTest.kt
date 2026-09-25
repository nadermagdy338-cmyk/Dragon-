/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قناة الحالة التي تقرؤها الواجهة: ما يُكتب يجب أن يُقرأ، وما يُقرأ يجب أن يخصّ التطبيق نفسه.
 *
 * وهذا هو العقد الذي كان ناقصًا: كتلة GPU كانت تُخرج خمس نتائج صامتة، فلا الواجهة تعرف ولا
 * السجل يحمل سطرًا. والترميز/الفكّ هنا دالتان خالصتان تُقاسان بلا جذر لأن كتابة ملف تحت
 * `/data/adb` لا تُقاس في بيئة الاختبار — ونقطة الفشل الحقيقية هي الصيغة لا الكتابة.
 */
class PerAppHardwareStatusTest {

    @Test
    fun `every recorded knob survives the round trip with its reason`() {
        val text = PerAppHardwareStatus.encode(
            pkg = "com.example.game",
            atMs = 1_758_000_000_000L,
            records = listOf(
                PerAppHardwareStatus.Record("gpu_profile", "not-writable", "gpu-provider-not-writable:kgsl-3d0", "gaming", ""),
                PerAppHardwareStatus.Record("cpu_limits:policy0", "blocked", "manual-lock", "300000:2000000", "300000:2000000"),
            ),
        )

        val snapshot = PerAppHardwareStatus.parse(text)

        assertEquals("com.example.game", snapshot?.pkg)
        assertEquals(1_758_000_000_000L, snapshot?.atMs)
        assertEquals(2, snapshot?.records?.size)
        val gpu = snapshot!!.records.first { it.knob == "gpu_profile" }
        assertEquals("not-writable", gpu.outcome)
        assertEquals("gpu-provider-not-writable:kgsl-3d0", gpu.reason)
        assertEquals("gaming", gpu.expected)
        assertEquals("غياب قيمة مقروءة يُكتب صريحًا لا فراغًا", "unreadable", gpu.live)
        assertTrue(gpu.isFailure)
    }

    @Test
    fun `applied and skipped are not failures`() {
        assertTrue(!PerAppHardwareStatus.Record("gpu_profile", "applied", "verified", "", "").isFailure)
        assertTrue(!PerAppHardwareStatus.Record("cpu_governor", "skipped", "governor-is-default", "", "").isFailure)
    }

    @Test
    fun `a blank reason is written as unspecified rather than an empty field`() {
        val text = PerAppHardwareStatus.encode("pkg", 0L, listOf(PerAppHardwareStatus.Record("thermal", "unsupported", "", "", "")))
        assertTrue(text.contains("reason=unspecified"))
        assertEquals("unsupported", PerAppHardwareStatus.parse(text)?.records?.single()?.outcome)
    }

    @Test
    fun `a file with no package line is not a snapshot`() {
        assertNull("«لا سجل» ليست «سجل سليم»", PerAppHardwareStatus.parse("at=5\nknob=x outcome=applied reason=verified expected=- live=-"))
    }

    @Test
    fun `whitespace inside a value cannot split one record into two fields`() {
        val text = PerAppHardwareStatus.encode(
            "com.example.game",
            0L,
            listOf(PerAppHardwareStatus.Record("gpu_profile", "not-verified", "live governor differs", "a b", "c d")),
        )
        val record = PerAppHardwareStatus.parse(text)?.records?.single()
        assertEquals("live_governor_differs", record?.reason)
        assertEquals("a_b", record?.expected)
        assertEquals("c_d", record?.live)
    }
}
