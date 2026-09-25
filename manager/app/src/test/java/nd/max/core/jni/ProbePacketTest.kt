package nd.max.core.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * صيغة الحزمة بين Kotlin وRust — تُقاس هنا **وفي Rust** (`probe::tests::same_vectors`
 * بنفس المُدخلات). وهذا هو المغزى: العقد بين لغتين لا يُحرَس في لغة واحدة؛ فإن انحرف أي
 * الجانبين سقط اختبار هناك أو هنا، بدل أن تصل قيم عقدة إلى عقدة أخرى صامتة.
 */
class ProbePacketTest {

    @Test
    fun `same vectors as the rust side`() {
        // المُدخلات المشتركة حرفيًّا مع `same_vectors` في src/probe.rs:
        assertEquals(listOf("/a/one", "/b/two"), ProbePacket.packPaths(listOf("/a/one", "/b/two")).split('\n'))
        assertEquals("754000", "754000 \n".trim())
        assertEquals("l1${ProbePacket.NEWLINE_ESCAPE}l2", "l1\nl2".replace("\n", ProbePacket.NEWLINE_ESCAPE.toString()))
        assertEquals("", "  \n ".trim())
    }

    @Test
    fun `unpack keeps row count and turns blank rows into nulls`() {
        val values = ProbePacket.unpackValues("42000\n\ncpu-0-0", expected = 3)
        assertEquals(listOf("42000", null, "cpu-0-0"), values)
    }

    @Test
    fun `a multiline node is restored to one row`() {
        val packed = "line1${ProbePacket.NEWLINE_ESCAPE}line2\n1300000"
        assertEquals(listOf("line1\nline2", "1300000"), ProbePacket.unpackValues(packed, expected = 2))
    }

    @Test
    fun `a packet with the wrong row count is rejected whole - never aligned by guess`() {
        // حزمة تحمل ٣ أسطر لطلبين: لا يمكن نسبتها بثقة، فتُرفض كاملةً.
        val values = ProbePacket.unpackValues("1\n2\n3", expected = 2)
        assertEquals(listOf(null, null), values)
    }

    @Test
    fun `empty request yields empty list not a crash`() {
        assertEquals(emptyList<String?>(), ProbePacket.unpackValues("", expected = 0))
    }

    @Test
    fun `paths that cannot be packed are refused before crossing the bridge`() {
        // المشروع يُقبل: مسارات sysfs العادية.
        assertTrue(ProbePacket.packable(listOf("/sys/ok")))
        assertTrue(ProbePacket.packable(listOf("/sys/a", "/proc/stat", "/sys/class/thermal/thermal_zone0/temp")))
        // وهذه تُرفض: مسار فيه سطر أو الفاصل أو فراغ يُفسد محاذاة الأسطر.
        assertFalse(ProbePacket.packable(listOf("/sys/a\nb")))
        assertFalse(ProbePacket.packable(listOf("/sys/a${ProbePacket.NEWLINE_ESCAPE}b")))
        assertFalse(ProbePacket.packable(listOf("   ")))
    }

    @Test
    fun `names are trimmed deduped and blanks dropped`() {
        // الترتيب كما وصل (والترتيب في القارئ الأصلي مرتّب أصلًا) — والقصّ يوحّد التباعد.
        assertEquals(listOf("b", "a"), ProbePacket.unpackNames("b\na\n\n a \n"))
        assertEquals(emptyList<String>(), ProbePacket.unpackNames(""))
    }

    @Test
    fun `a batch where every node failed to read keeps its row count`() {
        // هذه هي مخرجات Rust الحقيقية حين لا تُقرأ أي عقدة: سطر فارغ لكل مسار.
        val values = ProbePacket.unpackValues("\n\n", expected = 3)
        assertEquals(listOf(null, null, null), values)
        // ومسار واحد غير مقروء = نصّ فارغ تمامًا (لا أسطر) — ويبقى سطرًا واحدًا.
        assertEquals(listOf(null), ProbePacket.unpackValues("", expected = 1))
    }

    @Test
    fun `value trimming matches the read contract`() {
        // `RootFileAccess.read` تُرجع المقصوص غير الفارغ — والحزمة تحمل نفس الدلالة.
        // وهذه صيغة Rust الحقيقية: لا سطر جديد زائد في نهاية الحزمة إطلاقًا.
        assertNull(ProbePacket.unpackValues("", expected = 1).first())
        assertEquals("1300000", ProbePacket.unpackValues("  1300000 ", expected = 1).first())
        // وسطر جديد زائد **يُرفض** تفسيره: كان سيصير سطرًا إضافيًّا ينزاح به المقياس.
        assertEquals(listOf(null), ProbePacket.unpackValues("  1300000 \n", expected = 1))
    }
}
