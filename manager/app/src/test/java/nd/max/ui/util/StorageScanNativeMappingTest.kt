package nd.max.ui.util

import nd.max.core.jni.ScanPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تحويل حزمة المسح الأصلية إلى نموذج الشاشة — والقياس هنا ضروري لأن المسار الأصلي **لا
 * يُنفَّذ في اختبارات JVM** (لا مكتبة أصلية للمضيف): فيُقاس المنطق الذي يحوّل ردّه، وتُقاس
 * قواعد الترتيب والحدّ التي تضمن أن الشاشة لا تفرق بين المسارين.
 */
class StorageScanNativeMappingTest {

    private fun snapshot(
        buckets: List<ScanPacket.BucketRow> = emptyList(),
        largest: List<ScanPacket.LargestRow> = emptyList(),
        scanned: Long = 0,
        skipped: Long = 0,
        truncated: Boolean = false,
        cancelled: Boolean = false,
    ) = ScanPacket.Snapshot(scanned, skipped, truncated, cancelled, buckets, largest)

    @Test
    fun `every symbolic kind maps to its screen kind`() {
        val result = snapshot(
            buckets = listOf(
                ScanPacket.BucketRow("apps", 10, 1),
                ScanPacket.BucketRow("images", 20, 2),
                ScanPacket.BucketRow("video", 30, 3),
                ScanPacket.BucketRow("audio", 40, 4),
                ScanPacket.BucketRow("documents", 50, 5),
                ScanPacket.BucketRow("archives", 60, 6),
                ScanPacket.BucketRow("other", 70, 7),
            ),
        ).toScanResult()!!

        // الترتيب: البايتات تنازليًّا — فالأكبر أولًا مهما كان ترتيب الأصل.
        assertEquals(
            listOf(
                StorageBucketKind.Other,
                StorageBucketKind.Archives,
                StorageBucketKind.Documents,
                StorageBucketKind.Audio,
                StorageBucketKind.Video,
                StorageBucketKind.Images,
                StorageBucketKind.Apps,
            ),
            result.buckets.map { it.kind },
        )
        assertEquals(280L, result.measuredBytes)
    }

    @Test
    fun `an unknown kind refuses the whole result instead of dropping one bucket`() {
        val result = snapshot(
            buckets = listOf(
                ScanPacket.BucketRow("images", 20, 2),
                ScanPacket.BucketRow("something_new", 5, 1),
            ),
        ).toScanResult()
        assertNull("صنف مجهول ⇒ لا شاشة بأرقام ناقصة", result)
    }

    @Test
    fun `largest keeps eight items and breaks ties by path`() {
        val rows = (1..12).map { index ->
            ScanPacket.BucketRow("images", index.toLong(), 1)
        }.let { rows ->
            // العناصر: عشرة بنفس الحجم واثنان أكبر — الحدّ ثمانية والتعادل بالمسار.
            (1..10).map { ScanPacket.LargestRow("/b/$it", "f$it", 5) } +
                listOf(ScanPacket.LargestRow("/a/big", "big", 9), ScanPacket.LargestRow("/c/big2", "big2", 9))
        }
        val result = snapshot(buckets = listOf(ScanPacket.BucketRow("images", 1, 1)), largest = rows).toScanResult()!!

        assertEquals(StorageScanModel.LARGEST_LIMIT, result.largest.size)
        assertEquals(9L, result.largest.first().bytes)
        // وبين المتعادلين: الأصغر مسارًا أولًا.
        assertEquals("/a/big", result.largest[0].path)
        assertEquals("/c/big2", result.largest[1].path)
    }

    @Test
    fun `counters and flags cross the boundary unchanged`() {
        val result = snapshot(scanned = 120_000, skipped = 3, truncated = true).toScanResult()!!
        assertEquals(120_000, result.scannedEntries)
        assertEquals(3, result.skippedDirectories)
        assertTrue("بلوغ السقف يبقى معلنًا عبر الحدّ", result.truncated)
    }
}
