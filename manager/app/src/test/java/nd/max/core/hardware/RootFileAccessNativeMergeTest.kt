/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * دمج الدفعة الأصلية مع الطريق المصرَّح — الخصلتان التي إن سقطتا صار التسريع عطبًا:
 *
 * 1. **لا يُطلب بالجذر ما قُرئ أصلًا** (وإلا لم نُوفّر شيئًا).
 * 2. **ولا يُسقط ما فشل**: العقدة التي حجبها النواة عن uid التطبيق تُسأل بالطريق المصرَّح
 *    في موضعها بالضبط — فالقدرة لا تُشترى بالسرعة، والمحاذاة لا تُخمَّن.
 */
class RootFileAccessNativeMergeTest {

    @Test
    fun `all read natively means the root path is never touched`() {
        var privilegedCalls = 0
        val paths = listOf("/a", "/b", "/c")
        val merged = RootFileAccess.mergeNativeAndPrivileged(paths, listOf("1", "2", "3")) {
            privilegedCalls++
            listOf("x", "y", "z")
        }
        assertEquals(listOf("1", "2", "3"), merged)
        assertEquals(0, privilegedCalls)
    }

    @Test
    fun `unreadable nodes are asked for - and only those - in index order`() {
        val paths = listOf("/a", "/b", "/c", "/d")
        var asked: List<String>? = null
        val merged = RootFileAccess.mergeNativeAndPrivileged(paths, listOf("1", null, "3", null)) { missing ->
            asked = missing
            listOf("B-VIA-ROOT", "D-VIA-ROOT")
        }
        assertEquals(listOf("/b", "/d"), asked)
        assertEquals(listOf("1", "B-VIA-ROOT", "3", "D-VIA-ROOT"), merged)
    }

    @Test
    fun `a shortfall from the root path keeps nulls instead of shifting values`() {
        val paths = listOf("/a", "/b", "/c")
        val merged = RootFileAccess.mergeNativeAndPrivileged(paths, listOf(null, null, null)) { listOf("only-first") }
        assertEquals(listOf("only-first", null, null), merged)
    }

    @Test
    fun `native absence or a mismatched row count hands the whole job to the root path`() {
        val paths = listOf("/a", "/b")
        assertEquals(listOf("R1", "R2"), RootFileAccess.mergeNativeAndPrivileged(paths, null) { listOf("R1", "R2") })
        // ثلاث قيم لطلبين: محاذاة مخمَّنة كانت ستنسب قيمة عقدة إلى أخرى — تُرفض كاملةً.
        assertEquals(listOf("R1", "R2"), RootFileAccess.mergeNativeAndPrivileged(paths, listOf("1", "2", "3")) { listOf("R1", "R2") })
    }

    @Test
    fun `an empty request resolves without any file access`() {
        var privilegedCalls = 0
        val merged = RootFileAccess.mergeNativeAndPrivileged(emptyList(), emptyList()) {
            privilegedCalls++
            emptyList()
        }
        assertEquals(emptyList<String?>(), merged)
        assertEquals(0, privilegedCalls)
    }

    /*
     * ── الدفعة العائدة من القناة: الحجم المطابق لا يعني «هذه كل الإجابات» ─────────────────
     *
     * هذا هو العطب المقيس: `RootNodeService.readTexts` تُعيد `""` للعقدة التي لم تُقرأ لا
     * `null`، فالردّ **بحجم مطابق** كان يُقبل نهائيًّا. فمسح حراري على جهاز يعلن ٦٦ منطقة
     * عاد كلّه أصفارًا (لا قراءة واحدة) وظهرت مكانه جملة «لا توجد مناطق حرارة مكشوفة». وهذه
     * الدعاوى تُثبّت أن القيمة الفارغة تُكمل من الطبقة التالية، وأن الغياب التام لا يُخفى.
     */

    @Test
    fun `a row that came back empty is completed from the next layer`() {
        val paths = listOf("/sys/class/thermal/thermal_zone0/temp", "/sys/class/thermal/thermal_zone0/mode")
        val asked = mutableListOf<String>()
        val completed = RootFileAccess.completeBatch(paths, listOf("45000", "")) { path ->
            asked.add(path)
            if (path.endsWith("mode")) "enabled" else null
        }
        assertEquals(listOf("/sys/class/thermal/thermal_zone0/mode"), asked)
        assertEquals(listOf("45000", "enabled"), completed)
    }

    @Test
    fun `an all-empty batch is re-asked, not accepted as final`() {
        val paths = listOf("/t0/temp", "/t0/mode", "/t1/temp", "/t1/mode")
        var asked = 0
        val completed = RootFileAccess.completeBatch(paths, listOf("", "", "", "")) {
            asked++
            "read-$asked"
        }
        assertEquals(4, asked)
        assertEquals(listOf("read-1", "read-2", "read-3", "read-4"), completed)
    }

    @Test
    fun `a batch that never arrived asks every path in order`() {
        val paths = listOf("/a", "/b")
        val asked = mutableListOf<String>()
        val completed = RootFileAccess.completeBatch(paths, null) { path ->
            asked.add(path)
            "V:$path"
        }
        assertEquals(paths, asked)
        assertEquals(listOf("V:/a", "V:/b"), completed)
    }

    @Test
    fun `a mismatched batch is refused whole, never realigned by guess`() {
        val paths = listOf("/a", "/b", "/c")
        val completed = RootFileAccess.completeBatch(paths, listOf("1", "2")) { path -> "R:$path" }
        assertEquals(listOf("R:/a", "R:/b", "R:/c"), completed)
    }

    @Test
    fun `a value that stays unreadable ends as null, never as zero`() {
        val completed = RootFileAccess.completeBatch(listOf("/only"), listOf("")) { null }
        assertEquals(listOf<String?>(null), completed)
    }
}
