package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageScanModelTest {

    @Test
    fun `extension decides the bucket, and case does not matter`() {
        assertEquals(StorageBucketKind.Images, StorageScanModel.kindOf("IMG_0042.JPG"))
        assertEquals(StorageBucketKind.Images, StorageScanModel.kindOf("avatar.webp"))
        assertEquals(StorageBucketKind.Video, StorageScanModel.kindOf("clip.MP4"))
        assertEquals(StorageBucketKind.Audio, StorageScanModel.kindOf("song.flac"))
        assertEquals(StorageBucketKind.Documents, StorageScanModel.kindOf("report.Pdf"))
        assertEquals(StorageBucketKind.Archives, StorageScanModel.kindOf("backup.zip"))
    }

    @Test
    fun `a file whose kind cannot be told is kept as Other, never dropped`() {
        assertEquals(StorageBucketKind.Other, StorageScanModel.kindOf("noextension"))
        assertEquals(StorageBucketKind.Other, StorageScanModel.kindOf(".hidden"))
        assertEquals(StorageBucketKind.Other, StorageScanModel.kindOf("trailing."))
        assertEquals(StorageBucketKind.Other, StorageScanModel.kindOf(""))
        // أرشيف مزدوج الامتداد: القرار على الأخير، وإلا صُنّف نصًّا.
        assertEquals(StorageBucketKind.Archives, StorageScanModel.kindOf("system.tar.gz"))
    }

    @Test
    fun `accumulating returns a new list and sums both bytes and files`() {
        val empty = emptyList<StorageBucket>()
        val once = StorageScanModel.accumulate(empty, StorageBucketKind.Images, 1_000L)
        val twice = StorageScanModel.accumulate(once, StorageBucketKind.Images, 500L)

        assertEquals(listOf(StorageBucketKind.Images), once.map { it.kind })
        assertEquals(1, once.single().files)
        assertEquals(1_000L, once.single().bytes)
        // ملفّان لأن النداءَين أدخلا ملفّين — وعدد الملفات ليس نفس عدد المصارف.
        assertEquals(2, twice.single().files)
        assertEquals(1_500L, twice.single().bytes)
        // القائمة الأولى لم تُعدَّل: نداء يُعدّل مخفيًّا يجعل الرقم يعتمد على عدد النداءات.
        assertEquals(1_000L, once.single().bytes)
    }

    @Test
    fun `a negative length can never subtract from a bucket`() {
        val bucket = StorageScanModel.accumulate(
            StorageScanModel.accumulate(emptyList(), StorageBucketKind.Video, 4_000L),
            StorageBucketKind.Video,
            -9_000L
        )
        assertEquals(4_000L, bucket.single().bytes)
        assertEquals(2, bucket.single().files)
    }

    @Test
    fun `ranking is by size then by name so equal buckets keep one order`() {
        val ranked = StorageScanModel.rank(
            listOf(
                StorageBucket(StorageBucketKind.Video, 100L, 1),
                StorageBucket(StorageBucketKind.Audio, 100L, 1),
                StorageBucket(StorageBucketKind.Images, 900L, 3)
            )
        )
        assertEquals(
            listOf(StorageBucketKind.Images, StorageBucketKind.Audio, StorageBucketKind.Video),
            ranked.map { it.kind }
        )
    }

    @Test
    fun `largest keeps the top entries and breaks ties by path`() {
        var kept = emptyList<StorageLargestItem>()
        kept = StorageScanModel.keepLargest(kept, StorageLargestItem("/b", "b", 100L), limit = 2)
        kept = StorageScanModel.keepLargest(kept, StorageLargestItem("/a", "a", 100L), limit = 2)
        kept = StorageScanModel.keepLargest(kept, StorageLargestItem("/c", "c", 50L), limit = 2)
        kept = StorageScanModel.keepLargest(kept, StorageLargestItem("/d", "d", 900L), limit = 2)

        assertEquals(listOf("/d", "/a"), kept.map { it.path })
        assertTrue(StorageScanModel.keepLargest(emptyList(), StorageLargestItem("/x", "x", 1L), limit = 0).isEmpty())
    }

    @Test
    fun `a percentage never divides by zero and never exceeds a hundred`() {
        assertEquals(0, StorageScanModel.percentOf(500L, 0L))
        assertEquals(0, StorageScanModel.percentOf(500L, -10L))
        assertEquals(50, StorageScanModel.percentOf(50L, 100L))
        assertEquals(100, StorageScanModel.percentOf(500L, 100L))
        assertEquals(0, StorageScanModel.percentOf(0L, 100L))
    }

    @Test
    fun `byte formatting switches unit at the right boundary`() {
        assertEquals("512 B", StorageScanModel.formatBytes(512L))
        assertEquals("1 KB", StorageScanModel.formatBytes(1024L))
        assertEquals("1.5 MB", StorageScanModel.formatBytes(1_572_864L))
        assertEquals("2.00 GB", StorageScanModel.formatBytes(2_147_483_648L))
        assertEquals("1.00 TB", StorageScanModel.formatBytes(1_099_511_627_776L))
        assertEquals("0 B", StorageScanModel.formatBytes(-5L))
    }

    @Test
    fun `inode counts are read from stat first then from df, and never invented`() {
        assertEquals(11_358_208L to 9_724_227L, StorageScanModel.parseInodeCounts("11358208 9724227", null))

        val toyboxDf = """
            Filesystem            Inodes      IUsed     IFree  IUse% Mounted on
            /dev/block/dm-57      11358208    1633981   9724227    15% /data
        """.trimIndent()
        assertEquals(11_358_208L to 9_724_227L, StorageScanModel.parseInodeCounts(null, toyboxDf))

        // جذر مقطوع أو قيمة غير رقمية ⇒ مجهول، ولا يُعرض صفر مكانه.
        assertNull(StorageScanModel.parseInodeCounts("", null))
        assertNull(StorageScanModel.parseInodeCounts("0 -", null))
        assertNull(StorageScanModel.parseInodeCounts(null, "Filesystem Inodes IUsed IFree\n/dev/x - - -"))
        assertNull(StorageScanModel.parseInodeCounts(null, null))
    }
}
