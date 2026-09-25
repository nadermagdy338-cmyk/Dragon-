package nd.max.core.hardware

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * «أول مرشّح موجود» — السؤال الذي تطرحه شاشات الاكتشاف (العرض، اللمس، الشحن، الشبكة).
 *
 * وما يُقاس هنا هو ما كان يُشترى برحلة كاملة لكل مرشّح: **المحاذاة** (المرشّح رقم n
 * يُنسب إلى علمه رقم n) و**الأولوية** (الأول الموجود يفوز، لا آخرهم)، و**غياب الجميع**
 * يُعيد `null` كما كانت `firstOrNull { exists(it) }` — لا مرشّحًا مخترعًا.
 *
 * والملفات المؤقتة حقيقية: في JVM لا مكتبة أصلية ولا خدمة جذر، فيسقط الطريق إلى `File`
 * وهو نفسه الاحتياط المصرَّح على جهاز بلا ثنائيات — فالاختبار يقيس سلسلة الاحتياط فعلًا.
 */
class RootFileAccessDiscoveryTest {

    private fun tempFile(): File = File.createTempFile("mm-probe-", ".node").apply { deleteOnExit() }

    private fun missingPath(): String = "/definitely/not/here/${System.nanoTime()}"

    @Test
    fun `an empty candidate list resolves to nothing without any lookup`() {
        assertEquals(emptyList<Boolean>(), RootFileAccess.existing(emptyList()))
        assertNull(RootFileAccess.firstExisting(emptyList<String>()) { it })
    }

    @Test
    fun `existing reports per index and never shifts`() {
        val present = tempFile()
        val flags = RootFileAccess.existing(listOf(missingPath(), present.absolutePath, missingPath()))
        assertEquals(listOf(false, true, false), flags)
    }

    @Test
    fun `the first existing candidate wins even when a later one also exists`() {
        val first = tempFile()
        val second = tempFile()
        val chosen = RootFileAccess.firstExisting(
            listOf(missingPath(), first.absolutePath, second.absolutePath)
        ) { it }
        assertEquals(first.absolutePath, chosen)
    }

    @Test
    fun `no candidate existing yields null - not a made-up node`() {
        assertNull(
            RootFileAccess.firstExisting(listOf(missingPath(), missingPath())) { it }
        )
    }

    private data class Node(val label: String, val path: String)

    @Test
    fun `the answer is the candidate object itself and the mapping is applied per item`() {
        val present = tempFile()
        val candidates = listOf(
            Node("absent", missingPath()),
            Node("live", present.absolutePath),
        )
        assertEquals("live", RootFileAccess.firstExisting(candidates) { it.path }?.label)
    }

    @Test
    fun `flags decode matches the rust packet vectors`() {
        assertEquals(listOf(true, false, true), nd.max.core.jni.ProbePacket.unpackFlags("1\n0\n1", 3))
        // عدد مخالف = رفض كامل: لا وجود يُنسب إلى مسار لم يُسأل عنه.
        assertNull(nd.max.core.jni.ProbePacket.unpackFlags("1\n0", 3))
    }
}
