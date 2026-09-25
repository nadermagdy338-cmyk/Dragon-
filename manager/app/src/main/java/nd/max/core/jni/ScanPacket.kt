package nd.max.core.jni

/**
 * حزمة مسح المساحة — **نفس الصيغة في Rust حرفيًّا** (`src/scan.rs`).
 *
 * سطر رأس ثم صفوف مصارف ثم صفوف أكبر العناصر:
 *  - `S<sep>scanned<sep>skipped<sep>truncated<sep>cancelled`
 *  - `B<sep>kind<sep>bytes<sep>files` (متكرّر، مرتَّب: البايتات تنازليًّا ثم الاسم)
 *  - `L<sep>path<sep>name<sep>bytes` (متكرّر، بحدّ ثمانية)
 *
 * و**الرموز لا نصوص الواجهة**: الصنف يُشحن `images`/`video`/… والنصّ المعروض يُصاغ في طبقة
 * الواجهة من [nd.max.ui.util.StorageBucketKind].
 *
 * و**الرفض كامل**: أي سطر لا يطابق العقد (وسم مجهول، رقم غير رقميّ، ترتيب مقلوب، رأس ناقص)
 * يجعل العائد `null` — فيعود المتصل إلى مسح Kotlin. ولا محاذاة مخمَّنة: مسحٌ يُقرأ نصفه
 * يُنتج أرقامًا تبدو مقيسة وهي مقتطعة.
 */
internal object ScanPacket {

    /** الفاصل بين حقول الصفّ — مطابق لـ`scan::FIELD_SEP` في Rust. */
    const val FIELD_SEP: Char = '\u0001'

    /** حقول كل صفّ: الرأس خمسة، والمصرف أربعة، والعنصر أربعة. */
    private const val HEADER_FIELDS = 5
    private const val BUCKET_FIELDS = 4
    private const val LARGEST_FIELDS = 4

    /** صفّ مصرف كما وصل — بلا ترجمة ولا تفسير. */
    data class BucketRow(val kind: String, val bytes: Long, val files: Long)

    /** صفّ عنصر كبير كما وصل. */
    data class LargestRow(val path: String, val name: String, val bytes: Long)

    /** نتيجة مسح كاملة كما وصلت. */
    data class Snapshot(
        val scanned: Long,
        val skipped: Long,
        val truncated: Boolean,
        val cancelled: Boolean,
        val buckets: List<BucketRow>,
        val largest: List<LargestRow>,
    )

    /** جذور المسح كما تُشحن — بنفس عقد الدفعة ([ProbePacket.packPaths]): جذر لكل سطر. */
    fun packRoots(roots: List<String>): String = ProbePacket.packPaths(roots)

    /** هل تصلح الجذور للحزمة؟ (سطر جديد أو الفاصل داخل مسار يكسر المحاذاة.) */
    fun packableRoots(roots: List<String>): Boolean = ProbePacket.packable(roots)

    /** تفكيك حزمة المسح — و`null` لأي انحراف عن العقد. */
    fun unpack(text: String): Snapshot? {
        if (text.isEmpty()) return null
        val rows = text.split('\n')
        val header = rows.first().split(FIELD_SEP)
        if (header.size != HEADER_FIELDS || header[0] != "S") return null
        val scanned = header[1].toLongOrNull() ?: return null
        val skipped = header[2].toLongOrNull() ?: return null
        if (scanned < 0 || skipped < 0) return null
        val truncated = flag(header[3]) ?: return null
        val cancelled = flag(header[4]) ?: return null

        val buckets = mutableListOf<BucketRow>()
        val largest = mutableListOf<LargestRow>()
        for (row in rows.drop(1)) {
            val fields = row.split(FIELD_SEP)
            when (fields[0]) {
                "B" -> {
                    if (fields.size != BUCKET_FIELDS) return null
                    val kind = fields[1]
                    if (kind.isBlank()) return null
                    val bytes = fields[2].toLongOrNull() ?: return null
                    val files = fields[3].toLongOrNull() ?: return null
                    if (bytes < 0 || files < 0) return null
                    buckets += BucketRow(kind, bytes, files)
                }
                "L" -> {
                    if (fields.size != LARGEST_FIELDS) return null
                    val path = fields[1]
                    if (path.isBlank()) return null
                    val bytes = fields[3].toLongOrNull() ?: return null
                    if (bytes < 0) return null
                    largest += LargestRow(path, fields[2], bytes)
                }
                else -> return null
            }
        }
        return Snapshot(scanned, skipped, truncated, cancelled, buckets, largest)
    }

    /** علم `0`/`1` — وأي نصّ آخر يُرفض بدل أن يُقرأ خطأً. */
    private fun flag(raw: String): Boolean? = when (raw) {
        "1" -> true
        "0" -> false
        else -> null
    }
}
