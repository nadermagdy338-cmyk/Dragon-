package nd.max.core.jni

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * صيغة ردّ الضغط — تُقاس هنا **وفي Rust** على نفس المتجهات
 * (`archive::tests::result_packet_round_trips_and_rejects_malformed`).
 *
 * وأهمّ ما يُحرَس: أن الردّ **المشوَّه يُرفض كاملًا** (`null`) ولا يُقرأ «نجاحًا بأصفار».
 * فالفرق بين «فشل معلن» و«نجاح مزعوم» هو الفرق بين ميزة تعتذر وميزة تكذب.
 */
class ArchivePacketTest {

    @Test
    fun `an ok result carries entries and bytes`() {
        val result = ArchivePacket.unpack("OK\u0001421\u00015300620")
        assertEquals(ArchivePacket.Result.Ok(421, 5_300_620L), result)
    }

    @Test
    fun `a failure keeps its symbolic reason and subject`() {
        val result = ArchivePacket.unpack("FAIL\u0001unreadable\u0001/data/x.txt")
        assertEquals(ArchivePacket.Result.Failed("unreadable", "/data/x.txt"), result)
    }

    @Test
    fun `an empty subject becomes null instead of an empty sentence`() {
        assertEquals(
            ArchivePacket.Result.Failed("no_sources", null),
            ArchivePacket.unpack("FAIL\u0001no_sources\u0001"),
        )
    }

    @Test
    fun `malformed results are refused whole rather than guessed`() {
        assertNull("فارغ", ArchivePacket.unpack(""))
        assertNull("حقل ناقص", ArchivePacket.unpack("OK\u00013"))
        assertNull("حقل زائد", ArchivePacket.unpack("OK\u00011\u00012\u00013"))
        assertNull("رمز غير معروف", ArchivePacket.unpack("MAYBE\u00011\u00012"))
        assertNull("عدد غير رقمي", ArchivePacket.unpack("OK\u0001x\u00012"))
        assertNull("بایت سالب", ArchivePacket.unpack("OK\u0001-1\u00012"))
        assertNull("سبب فارغ", ArchivePacket.unpack("FAIL\u0001\u0001/"))
    }

    @Test
    fun `sources are packed with the same batch contract as probe reads`() {
        // عقد واحد للمسارات في المستودع: مسار لكل سطر (ProbePacket) — فلا تطبيع ثانٍ.
        assertEquals("/a/one\n/b/two", ArchivePacket.packSources(listOf("/a/one", "/b/two")))
        assertTrue(ArchivePacket.packable(listOf("/a/one", "/b/two")))
        assertFalse("سطر جديد يكسر المحاذاة", ArchivePacket.packable(listOf("/a/one\n/b/two")))
        assertFalse("مسار فارغ", ArchivePacket.packable(listOf("")))
    }
}

/**
 * حارس ربط JNI لجسر الأرشيف — نفس صنف العطب الذي يحرسه `RustBridgeSymbolTest`: اسم مخالف
 * بين `private external fun` والرمز في Rust لا يُسقط بناءً ولا اختبارًا، بل ينفجر
 * بـ`UnsatisfiedLinkError` عند أول ضغط على جهاز حقيقي.
 */
class ArchiveBridgeSymbolTest {

    private lateinit var repoRoot: File

    @Before
    fun locateRepoRoot() {
        val found = listOf(File("."), File(".."), File("../.."))
            .firstOrNull { File(it, "manager/src/main/rust/src/lib.rs").isFile }
        assumeTrue("Cannot locate the rust crate; guard not evaluated", found != null)
        repoRoot = found!!
    }

    private fun bridgeText(): String =
        File(repoRoot, "manager/app/src/main/java/nd/max/core/jni/ArchiveBridge.kt").readText()

    private fun rustText(): String =
        File(repoRoot, "manager/src/main/rust/src/lib.rs").readText()

    @Test
    fun `every declared native method has a rust export with the same name`() {
        val declared = Regex("""private external fun (\w+)\(""").findAll(bridgeText())
            .map { it.groupValues[1] }.toList()
        assertTrue("الجسر يجب أن يُعلن دالة أصلية", declared.isNotEmpty())

        val exported = Regex("""Java_nd_max_core_jni_ArchiveBridge_(\w+)""").findAll(rustText())
            .map { it.groupValues[1] }.toSet()

        val missing = declared.filterNot { it in exported }
        assertTrue(
            "دوال بلا تصدير في Rust (‏UnsatisfiedLinkError على الجهاز): $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `no rust export is left without a caller in the bridge`() {
        val declared = Regex("""private external fun (\w+)\(""").findAll(bridgeText())
            .map { it.groupValues[1] }.toSet()
        val exported = Regex("""Java_nd_max_core_jni_ArchiveBridge_(\w+)""").findAll(rustText())
            .map { it.groupValues[1] }.toSet()
        val orphans = exported - declared
        assertTrue("تصديرات بلا مستدعٍ (سطح أصلي ميت): $orphans", orphans.isEmpty())
    }

    @Test
    fun `extraction deliberately has no native bridge`() {
        // قرار ADR-49: قاعدة `zip-slip` تبقى في Kotlin وحدها. فإن أُضيف جسر فكّ يومًا،
        // يسقط هذا الحارس ويُسأل صاحبه عن حكم السلامة — لا يُضاف صامتًا.
        val rust = rustText()
        assertFalse(
            "لا تصدير لفكّ الأرشيف قبل حكم سلامة (ADR-49)",
            rust.contains("Java_nd_max_core_jni_ArchiveBridge_nativeExtract"),
        )
        assertFalse(
            "ولا دالة أصلية لفكّ في الجسر",
            bridgeText().contains("nativeExtract"),
        )
    }
}
