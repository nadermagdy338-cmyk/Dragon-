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
}
