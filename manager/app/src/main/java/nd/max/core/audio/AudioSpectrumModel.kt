/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **المحلّل الطيفيّ: النموذج الصافي** (`AQ-08`).
 *
 * **وما يُقَاس هنا هو ما كان يُظنّ أنه لا يُقاس:** تحويل مخرَج `Visualizer.getFft` (بايتات مشكّلة
 * `8-bit` بلا إشارة) إلى مستويات نطاقات **قابل للقياس على JVM** بمدخلٍ مصنوع — فالحكم على الرسم لا
 * يكون بالعين وحدها. والتحويل هنا **دالّة صافية**: صفر عشوائيّة، وصفر زمن، ونفس المدخل يعطي نفس
 * المخرج، وهذا شرط أن يُثبَّت باختبار.
 *
 * **والصدق في الرسم:** ما نرسمه هو **FFT مشكّل بمقياس المنصّة** — لا تحليلًا موسيقيًّا ولا «قياس
 * استجابة». فالتسمية «مستويات الطيف» صدق، وتسميتها «تحليل صوتيّ» ادّعاء لا يسنده شيء.
 *
 * **وبدون إذن `RECORD_AUDIO` لا شيء هنا يعمل**، والحكم يأتي من القدرات (`AQ-01`) لا من هذا الملفّ.
 */
package nd.max.core.audio

import kotlin.math.abs

/** إطار طيفيّ مقروء: مستويات نطاقات مُطبَّعة + ذروة/RMS إن قُرئا. */
data class AudioSpectrumFrame(
    /** مستويات `0f..1f` من الأدنى ترددًا إلى الأعلى — وطولها ثابت (`bandCount`). */
    val bands: List<Float>,
    /** ذروة `MEASUREMENT_MODE_PEAK_RMS` بالملّي‑ديسيبل، و`null` حين لا قياس. */
    val peakMb: Int?,
    /** RMS بالملّي‑ديسيبل، و`null` حين لا قياس. */
    val rmsMb: Int?,
)

/**
 * يحوّل مخرَج `getFft` إلى [bandCount] مستوى.
 *
 * **والقواعد المقيسة، لا الذوقيّة:**
 * ① البايت `8-bit` **بلا إشارة**، و`Byte` في JVM مُوقّع ⇒ القيمة الصحيحة تُؤخذ بـ`and 0xFF` أوّلًا،
 *    **ثمّ** يُطرح ١٢٨. وطرحُ ١٢٨ من بايتٍ مُوقّع كان يجعل الصمت (`0x80`) أعلى مقدار في الإطار —
 *    وهو عطبٌ يُقلب به الرسم رأسًا على عقب ولا يُنتجه مُصرّف. (ثمّ القيمة المطلقة مقدارٌ لوغاريتميّ.)
 * ② كل مستوى يُطبَّع على **أقصى مقدار في الإطار نفسه** ⇒ الرسم يُقرأ دائمًا مهما كانت جهارة المصدر
 *    (وهو ما تسمّيه المنصّة `SCALING_MODE_NORMALIZED`، ونحن نُطبّعه بأنفسنا فلا نعتمد على ضبطها).
 * ③ إطارٌ صامت (أقصى = صفر) ⇒ **أصفار**، وهذا **قياسٌ** لا غياب قراءة: المنصّة قالت «لا صوت».
 * ④ نصيب كل نطاق من الترددات **متساوٍ بالطول** لأنّ `getFft` تُعيد بايتات مرتّبة تصاعديًّا في التردد
 *    وطولها معلوم؛ فتقسيمٌ بحسب الطول هو التقسيم الصادق بلا معرفة معدّل العيّنة.
 */
fun audioSpectrumBandsOf(fft: ByteArray?, bandCount: Int): List<Float> {
    if (bandCount <= 0) return emptyList()
    if (fft == null) return List(bandCount) { 0f }
    if (fft.isEmpty()) return List(bandCount) { 0f }
    // والبايت هنا **بلا إشارة**: `and 0xFF` قبل الطرح، وإلا صار الصمت أعلى مقدار (انظر رأس الملفّ).
    val magnitudes = IntArray(fft.size) { abs((fft[it].toInt() and 0xFF) - 128) }
    val peak = magnitudes.maxOrNull() ?: 0
    if (peak <= 0) return List(bandCount) { 0f }
    val span = fft.size
    return (0 until bandCount).map { band ->
        val start = band * span / bandCount
        val end = (((band + 1) * span) / bandCount).coerceAtMost(span)
        if (end <= start) return@map 0f
        var sum = 0
        for (index in start until end) sum += magnitudes[index]
        val average = sum.toFloat() / (end - start).toFloat()
        (average / peak.toFloat()).coerceIn(0f, 1f)
    }
}

/** معدّل التقاط المنصّة ← حجم القفزة الزمنيّة: `rateHz` تُقاس بـ«إطار لكل ثانية». */
fun spectrumFrameIntervalMs(rateHz: Int?): Long {
    val rate = rateHz?.takeIf { it > 0 } ?: 20
    return (1000L / rate).coerceAtLeast(16L)
}

/** هل يُسمح بمحاولة الالتقاط؟ — الإذن شرطٌ، **و`false` تُقال بسببها في الشاشة لا تُخفى**. */
fun spectrumCapturable(permissionGranted: Boolean): Boolean = permissionGranted
