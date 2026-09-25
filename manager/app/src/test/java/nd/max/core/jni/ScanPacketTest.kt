/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.jni

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * حزمة مسح المساحة — تُقاس هنا **وفي Rust** على نفس الصيغة (`scan::tests`).
 *
 * وأهمّ ما يُحرَس: أن **انحراف العقد يُرفض كاملًا**. مسحٌ يُقرأ نصفه يُنتج شاشة بأرقام تبدو
 * مقيسة وهي مقتطعة — وذلك أسوأ من العودة إلى مسح Kotlin.
 */
class ScanPacketTest {

    private val header = "S\u000123224\u00010\u00010\u00010"

    @Test
    fun `a full packet decodes into header buckets and largest`() {
        val text = listOf(
            header,
            "B\u0001images\u0001123\u00012",
            "B\u0001other\u00017\u00011",
            "L\u0001/data/a.jpg\u0001a.jpg\u0001100",
        ).joinToString("\n")

        val snapshot = ScanPacket.unpack(text)!!
        assertEquals(23224L, snapshot.scanned)
        assertEquals(0L, snapshot.skipped)
        assertTrue(!snapshot.truncated && !snapshot.cancelled)
        assertEquals(listOf("images", "other"), snapshot.buckets.map { it.kind })
        assertEquals(2L, snapshot.buckets.first().files)
        assertEquals("/data/a.jpg", snapshot.largest.single().path)
        assertEquals("a.jpg", snapshot.largest.single().name)
    }

    @Test
    fun `flags are read strictly and anything else is refused`() {
        assertTrue(ScanPacket.unpack("S\u00010\u00010\u00011\u00010")!!.truncated)
        assertTrue(ScanPacket.unpack("S\u00010\u00010\u00010\u00011")!!.cancelled)
        assertNull(ScanPacket.unpack("S\u00010\u00010\u0001yes\u00010"))
        assertNull(ScanPacket.unpack("S\u00010\u00010\u0001\u00010"))
    }

    @Test
    fun `malformed packets are refused whole, never half-read`() {
        assertNull("فارغ", ScanPacket.unpack(""))
        assertNull("بلا رأس", ScanPacket.unpack("B\u0001images\u00011\u00011"))
        assertNull("رأس ناقص", ScanPacket.unpack("S\u00011\u00010\u00010"))
        assertNull("رقم غير رقمي", ScanPacket.unpack("S\u0001x\u00010\u00010\u00010"))
        assertNull("وسم صفّ مجهول", ScanPacket.unpack("$header\nX\u00011\u00012"))
        assertNull("صفّ مصرف ناقص", ScanPacket.unpack("$header\nB\u0001images\u00011"))
        assertNull("صنف فارغ", ScanPacket.unpack("$header\nB\u0001\u00011\u00011"))
        assertNull("مسار فارغ", ScanPacket.unpack("$header\nL\u0001\u0001a\u00011"))
        assertNull("بايتات سالبة", ScanPacket.unpack("$header\nB\u0001images\u0001-1\u00011"))
    }

    @Test
    fun `roots are packed with the same batch contract as probe reads`() {
        assertEquals("/sdcard/a\n/data/b", ScanPacket.packRoots(listOf("/sdcard/a", "/data/b")))
        assertTrue(ScanPacket.packableRoots(listOf("/sdcard/a")))
        assertTrue("جذر فيه سطر جديد يكسر المحاذاة", !ScanPacket.packableRoots(listOf("/a\n/b")))
        assertTrue("جذر فارغ", !ScanPacket.packableRoots(listOf("")))
    }
}

/**
 * حارس ربط JNI لمسح المساحة — وحارس **جداول الامتدادات**: التصنيف مكتوب مرّتين (Kotlin
 * للعرض والمرجع، وRust للمسح)، فانحراف جدول واحد يعني صنفًا يُحسب هنا ولا يُحسب هناك.
 */
class ScanBridgeSymbolTest {

    private lateinit var repoRoot: File

    @Before
    fun locateRepoRoot() {
        val found = listOf(File("."), File(".."), File("../.."))
            .firstOrNull { File(it, "manager/src/main/rust/src/lib.rs").isFile }
        assumeTrue("Cannot locate the rust crate; guard not evaluated", found != null)
        repoRoot = found!!
    }

    private fun bridgeText(): String =
        File(repoRoot, "manager/app/src/main/java/nd/max/core/jni/ScanBridge.kt").readText()

    private fun rustText(): String =
        File(repoRoot, "manager/src/main/rust/src/lib.rs").readText()

    @Test
    fun `every declared native method has a rust export with the same name`() {
        val declared = Regex("""private external fun (\w+)\(""").findAll(bridgeText())
            .map { it.groupValues[1] }.toList()
        assertEquals(
            "ثلاث دوال: مسح · تقدّم · إلغاء",
            listOf("nativeScan", "nativeScanProgress", "nativeCancelScan").sorted(),
            declared.sorted(),
        )
        val exported = Regex("""Java_nd_max_core_jni_ScanBridge_(\w+)""").findAll(rustText())
            .map { it.groupValues[1] }.toSet()
        val missing = declared.filterNot { it in exported }
        assertTrue("دوال بلا تصدير (UnsatisfiedLinkError على الجهاز): $missing", missing.isEmpty())
    }

    @Test
    fun `no rust export is left without a caller in the bridge`() {
        val declared = Regex("""private external fun (\w+)\(""").findAll(bridgeText())
            .map { it.groupValues[1] }.toSet()
        val exported = Regex("""Java_nd_max_core_jni_ScanBridge_(\w+)""").findAll(rustText())
            .map { it.groupValues[1] }.toSet()
        assertTrue("تصديرات بلا مستدعٍ: ${exported - declared}", (exported - declared).isEmpty())
    }

    @Test
    fun `the rust extension tables quote every extension the kotlin model knows`() {
        val kotlin = File(repoRoot, "manager/app/src/main/java/nd/max/ui/util/StorageScanModel.kt").readText()
        val rust = File(repoRoot, "manager/src/main/rust/src/scan.rs").readText()

        val kotlinTables = tables(
            kotlin,
            Regex("""private val (\w+_EXT) = setOf\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL),
        )
        val rustTables = tables(
            rust,
            Regex("""const (\w+_EXT): &\[&str\] = &\[([^]]*)\]""", RegexOption.DOT_MATCHES_ALL),
        )
        assertEquals("نفس الجداول الخمسة", kotlinTables.keys, rustTables.keys)
        kotlinTables.forEach { (name, extensions) ->
            assertEquals("جدول $name منحرف بين اللغتين", extensions, rustTables[name])
        }
        assertTrue("لا جدول فارغ", kotlinTables.values.all { it.isNotEmpty() })
    }

    private fun tables(text: String, pattern: Regex): Map<String, Set<String>> =
        pattern.findAll(text).associate { match ->
            val extensions = Regex(""""([a-z0-9]+)"""").findAll(match.groupValues[2])
                .map { it.groupValues[1] }.toSet()
            match.groupValues[1] to extensions
        }
}
