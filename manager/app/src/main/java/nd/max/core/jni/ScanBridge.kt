/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.jni

/**
 * جسر JNI لمسح المساحة (Rust) — ثلاث دوال لأن العملية **طويلة**:
 *
 * 1. [scan] نداء يعمل (يُنادى من `Dispatchers.IO`) ويُرجع حزمة النتيجة.
 * 2. [scanProgress] عدّاد حيّ يُقرأ من خيط الواجهة كل ~١٠٠ مللي، فيبقى العدّاد متحرّكًا
 *    بلا أن يُخترع رقم (القيمة المقروءة هي ما مُسح فعلًا).
 * 3. [cancelScan] يوقف الحلقة عند حدّ المجلد، فلا تُكمل ١٢٠ ألف مدخل بعد أن رحل من طلبها.
 *
 * **والقرار مبنيّ على قياس** (ADR-50): نفس الشجرة ونفس التصنيف ونفس الترتيب — **JDK 447ms
 * ← Rust 209ms (×2.1)** على ٢٣,٢٢٤ ملفًا، و**285ms ← 101ms (×2.8)** على ١٨,٩٦٥، والنتيجة
 * مطابقة بالحرف (البايتات والمداخل والمصارف).
 *
 * **و`null` تعني «اسأل غيري»** (نفس بقية الجسور): المكتبة غائبة، أو الجذور لا تُشحن بأمان،
 * أو الردّ لا يطابق العقد — فيعود المتصل إلى مسح Kotlin بنفس دلالاته.
 *
 * ولا كتابة هنا إطلاقًا: الوحدة تقرأ وتُجمّع فقط (ADR-11 سليم).
 */
object ScanBridge {

    /** هل حُمِّلت المكتبة الأصلية؟ يُحسب مرة واحدة، وغيابها ليس عطبًا. */
    val nativeAvailable: Boolean = runCatching {
        System.loadLibrary("maxmanager_native")
    }.isSuccess

    private external fun nativeScan(packedRoots: String, maxEntries: Int): String

    private external fun nativeScanProgress(): Long

    private external fun nativeCancelScan()

    /**
     * مسح الجذور. والعائد `null` ليعود المتصل إلى مسح Kotlin، وحزمة صحيحة عند النجاح
     * (وقد تحمل `cancelled = true` إن طلب أحدٌ الإلغاء — وهي حالة يُعلنها المتصل، لا نجاح).
     */
    internal fun scan(roots: List<String>, maxEntries: Int): ScanPacket.Snapshot? {
        if (roots.isEmpty()) return null
        if (!nativeAvailable || !ScanPacket.packableRoots(roots)) return null
        val packed = runCatching {
            nativeScan(ScanPacket.packRoots(roots), maxEntries)
        }.getOrNull() ?: return null
        if (packed.isEmpty()) return null
        return ScanPacket.unpack(packed)
    }

    /** ما مُسح حتى اللحظة — `0` إن كانت المكتبة غائبة (ولا يُخترع تقدّم). */
    fun scanProgress(): Long = if (!nativeAvailable) 0L else runCatching {
        nativeScanProgress()
    }.getOrDefault(0L)

    /** طلب إلغاء المسح الجاري. */
    fun cancelScan() {
        if (nativeAvailable) runCatching { nativeCancelScan() }
    }
}
