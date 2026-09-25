/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.jni

/**
 * جسر JNI لضغط zip الأصلي (Rust) — **كتابة أرشيف واحد** من مصادر يراها التطبيق.
 *
 * والقرار مبنيّ على قياس لا على انطباع (ADR-49): نفس الشجرة ونفس الدفعة ٦٤ ك ونفس المستوى ٦
 * — JDK `java.util.zip` ‏211ms · Rust بـminiz_oxide ‏217ms (**أبطأ**) · Rust بـzlib-rs
 * ‏107ms (**×1.97**). فالنقل هنا **مشروط بالمُحرّك**، ولذلك هو مُثبَّت في `Cargo.toml`.
 *
 * **والفكّ لا جسر له هنا عمدًا:** فكّ الأرشيف يحمل قاعدة `zip-slip` (رفض مدخل غير آمن قبل
 * أول بايت)، وتبقى في Kotlin وحدها حتى لا تُنفَّذ قاعدة أمنية مرّتين مقابل ربح مقيس ٠.١–٠.٤ث.
 *
 * **و`null` تعني «اسأل غيري»** — في ثلاثة مواضع: المكتبة غائبة، أو المصادر لا تُشحن بأمان
 * (سطر جديد أو فاصل داخل مسار)، أو الردّ لا يطابق العقد. وفي كلٍّ منها يعود المتصل إلى
 * التنفيذ المرجعي في Kotlin، فلا تنكسر ميزة لغياب مكتبة أصلية (نفس سلوك بقية الجسور).
 *
 * ولا تسجيل عند غياب المكتبة: `PredictorBridge` يسجّل ذلك في التشخيص بالفعل، وتكراره يُوهم
 * بعطبين.
 */
object ArchiveBridge {

    /** هل حُمِّلت المكتبة الأصلية؟ يُحسب مرة واحدة، وغيابها ليس عطبًا. */
    val nativeAvailable: Boolean = runCatching {
        System.loadLibrary("maxmanager_native")
    }.isSuccess

    private external fun nativeCreateZip(packedPaths: String, archivePath: String): String

    /**
     * ضغط المصادر في `archivePath` بالمسار الأصلي.
     *
     * والعائد [ArchivePacket.Result] عند نجاح النداء، و`null` ليعود المتصل إلى Kotlin.
     * ولا يُنادى أصلًا لقائمة مصادر فارغة: `NoSources` حكم يعرفه Kotlin وحده بلا رحلة.
     */
    /**
     * و`internal` لأن نوع العائد داخلي (نفس نمط `ProbeBridge.parseLogs`): واجهة عامّة تُظهر
     * عقدًا داخليًّا لا تُترجم — وهذا ما أمسكه المُصرّف أوّل مرّة فأُصلح في موضعه.
     */
    internal fun createZip(sources: List<String>, archivePath: String): ArchivePacket.Result? {
        if (sources.isEmpty()) return null
        if (!nativeAvailable || !ArchivePacket.packable(sources)) return null
        if (archivePath.isBlank() || archivePath.any { it == '\n' || it == '\r' || it == ArchivePacket.FIELD_SEP }) {
            return null
        }
        val packed = runCatching {
            nativeCreateZip(ArchivePacket.packSources(sources), archivePath)
        }.getOrNull() ?: return null
        if (packed.isEmpty()) return null
        return ArchivePacket.unpack(packed)
    }
}
