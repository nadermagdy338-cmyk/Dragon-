/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * دفقات الصوت — **الصافي**: أيّ دفقات نعرض، ورمز كلٍّ منها، وكيف تُحوَّل قراءته إلى نسبة.
 *
 * **وما لا يعرفه هذا الملفّ مقصود:** لا `AudioManager` ولا معرّف مورد ولا `R`. فالرموز تُسمّى هنا
 * بالأسماء، وتُترجم إلى ثوابت المنصّة في `AudioStreamBackend`، وإلى نصوص الواجهة في الشاشة. وهذا
 * ما جعل `AudioCapabilities` قابلًا للقياس على JVM، وهو نفسه ما يجعل هذا قابلًا له.
 *
 * **والدفقات الستّة مختارة لا مأخوذة كلّها:** `STREAM_MUSIC` · `STREAM_RING` · `STREAM_ALARM` ·
 * `STREAM_NOTIFICATION` · `STREAM_SYSTEM` · `STREAM_VOICE_CALL`. وما عداها إمّا مُهمَل في المنصّة
 * (`STREAM_SYSTEM_ENFORCED` · `STREAM_DTMF`) أو لا مستوى له بمعنى يُضبط. فلا يُعرض دفقٌ يعمل
 * نعمله: «كل دفق نعرضه يعمل، وما لا نعرضه لا يُعرض مُعطَّلًا» (ADR-07).
 */
package nd.max.core.audio

import kotlin.math.roundToInt

/**
 * دفقٌ واحد له مستوى يُقرأ ويُكتب.
 *
 * **ولا رمز أيقونة هنا**، وقد كان ثمّ حُذف: `MaxSliderRow` لا يقبل أيقونة، فرمزٌ لا يرسمه أحد
 * وعدٌ لا تفي به الواجهة. ولو أُضيف صفٌّ بأيقونة يومًا عاد الرمز معه.
 *
 * @param token المعرّف في مفتاح الـarbiter ولديها الخريطة إلى ثوابت المنصّة — **كلمة واحدة لا
 *   رقم**، فيبقى المفتاح مقروءًا في السجلّ (`audio_stream:media`).
 * @param supportsMute هل تُعلن المنصّة كتمًا لهذا الدفق؟ (`isStreamMute`) وليس كلّها.
 */
data class AudioStreamInfo(
    val token: String,
    val supportsMute: Boolean,
)

object AudioStreamCatalog {

    /** بترتيب الشاشة: ما يُستعمل أكثر أوّلًا — والترتيب جزءٌ من التصميم لا من الطبقة. */
    val streams: List<AudioStreamInfo> = listOf(
        AudioStreamInfo("media", supportsMute = true),
        AudioStreamInfo("call", supportsMute = true),
        AudioStreamInfo("ring", supportsMute = true),
        AudioStreamInfo("notification", supportsMute = true),
        AudioStreamInfo("alarm", supportsMute = true),
        AudioStreamInfo("system", supportsMute = true),
    )

    fun stream(token: String): AudioStreamInfo? = streams.firstOrNull { it.token == token }

    /** هل هذا الرمز دفقٌ نعرفه؟ — يُستعمل في مفتاح الـarbiter وفي قراءة ما يصل من المنصّة. */
    fun isKnown(token: String): Boolean = stream(token) != null

    /**
     * نسبة المستوى من سقفه: `null` حين لا قراءة أو حين سقفٌ غير صالح.
     *
     * **و`null` ليست صفرًا:** الصفر مستوى حقيقيّ (صامت)، وغياب القراءة غيابها. ولو أُعيد الصفر
     * لبدا الدفق مكتومًا وهو لم يُقرأ بعد (وهو ما يمنعه ADR-07 في كل شاشاتنا).
     */
    fun fractionOf(level: Int?, maxLevel: Int?): Float? {
        if (level == null || maxLevel == null || maxLevel <= 0) return null
        return (level.toFloat() / maxLevel).coerceIn(0f, 1f)
    }

    /**
     * مستوى صحيح من نسبة — **ولا يتجاوز السقف المُعلَن أبدًا**: سقفٌ يُقرأ من الجهاز ونسبةٌ من
     * واجهة لا يجوز أن تُنتج رقمًا يرفضه الجهاز. وهو سبب وجود الدالة في الطبقة الصافية لا في الشاشة.
     */
    fun levelOf(fraction: Float, maxLevel: Int): Int {
        if (maxLevel <= 0) return 0
        return (fraction.coerceIn(0f, 1f) * maxLevel).roundToInt().coerceIn(0, maxLevel)
    }

    /** ما يُعرض تحت الشريط: «٧ / ١٥» — الرقم الحيّ وسقفه، **بلا نسبة مئوية مُصنَّعة**. */
    fun levelText(level: Int, maxLevel: Int): String = "$level / $maxLevel"
}
