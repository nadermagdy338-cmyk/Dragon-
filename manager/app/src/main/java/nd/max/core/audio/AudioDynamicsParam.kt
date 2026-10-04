/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **مفردات معاملات الديناميكيّ** (`AQ-04`): اسمٌ واحد لكل معامل، ومعرفةُ كيف يُكتب ويُقرأ.
 *
 * **ووُجدت لأنّ التسمية وحدها لا تكفي:** لو مرّرت الشاشة اسم المعامل نصًّا لاحتاج المحرّك سلسلة
 * `when` في موضعين (كتابة وقراءة) — وسلسلتان تفترقان أوّل تعديل فيُكتب معاملٌ ويُقرأ آخر بصمت.
 * فالمعرفة هنا **واحدة**، والرمز (`token`) يُبنى عليه مفتاح المقبض في `HardwareControlKey`.
 *
 * **و`read` هنا لا تُقارِن:** هي تُعيد ما تحمله `MbcBand`/`Limiter` ليكتبه المحرّك في الحكم؛
 * والمقارنة الحرفيّة في المحكِّم. فلا حكمَ في هذا الملفّ — مفردات فقط.
 */
package nd.max.core.audio

import android.media.audiofx.DynamicsProcessing

/** معامل واحد في الضاغط متعدّد النطاقات أو في المُحدِّد — والأسماء من واجهة المنصّة نفسها. */
enum class DynamicsParam(val token: String) {
    THRESHOLD("threshold"),
    RATIO("ratio"),
    ATTACK("attack"),
    RELEASE("release"),
    KNEE("knee"),
    GATE("gate"),
    EXPANDER("expander"),
    PRE_GAIN("pre_gain"),
    POST_GAIN("post_gain");

    /** يكتب المعامل في نطاق ضغط — **ولا يلمس غيره** (النطاق يُبنى نسخةً من القائم قبل التعديل). */
    fun write(band: DynamicsProcessing.MbcBand, value: Float) {
        when (this) {
            THRESHOLD -> band.threshold = value
            RATIO -> band.ratio = value
            ATTACK -> band.attackTime = value
            RELEASE -> band.releaseTime = value
            KNEE -> band.kneeWidth = value
            GATE -> band.noiseGateThreshold = value
            EXPANDER -> band.expanderRatio = value
            PRE_GAIN -> band.preGain = value
            POST_GAIN -> band.postGain = value
        }
    }

    /** يقرأ المعامل من نطاق ضغط — وهي التي تُقارَن بها الكتابة. */
    fun read(band: DynamicsProcessing.MbcBand): Float = when (this) {
        THRESHOLD -> band.threshold
        RATIO -> band.ratio
        ATTACK -> band.attackTime
        RELEASE -> band.releaseTime
        KNEE -> band.kneeWidth
        GATE -> band.noiseGateThreshold
        EXPANDER -> band.expanderRatio
        PRE_GAIN -> band.preGain
        POST_GAIN -> band.postGain
    }

    /**
     * يكتب المعامل في المُحدِّد — **والمُحدِّد لا يعرف إلا خمسة منها**: عتبة · نسبة · هجوم · تحرير ·
     * كسب بعديّ. وما عداها **لا يُكتب ولا يُقرأ** في المُحدِّد (الركبة والبوابة والموسّع نطاقيّة فقط)،
     * فيُترك المُحدِّد كما هو بدل أن يُخترع له معامل لا وجود له.
     */
    fun writeLimiter(limiter: DynamicsProcessing.Limiter, value: Float) {
        when (this) {
            THRESHOLD -> limiter.threshold = value
            RATIO -> limiter.ratio = value
            ATTACK -> limiter.attackTime = value
            RELEASE -> limiter.releaseTime = value
            POST_GAIN -> limiter.postGain = value
            else -> Unit
        }
    }

    /** يقرأ المعامل من المُحدِّد — ومعاملٌ لا يعرفه المُحدِّد يعود `0f` ويُقال ذلك في الشاشة. */
    fun readLimiter(limiter: DynamicsProcessing.Limiter): Float = when (this) {
        THRESHOLD -> limiter.threshold
        RATIO -> limiter.ratio
        ATTACK -> limiter.attackTime
        RELEASE -> limiter.releaseTime
        POST_GAIN -> limiter.postGain
        else -> 0f
    }

    /** هل يعرفه المُحدِّد؟ — تُستعمل لحجب مقبضٍ لا وجود له بدل عرضه مُعطَّلًا. */
    val isLimiterParam: Boolean
        get() = this == THRESHOLD || this == RATIO || this == ATTACK || this == RELEASE || this == POST_GAIN
}
