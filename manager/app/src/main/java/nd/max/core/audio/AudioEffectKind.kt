/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **مفردات المؤثّرات القابلة للإرفاق**، ربطًا بين رمز المنصّة وحالة القدرات.
 *
 * **ووجود هذا الملفّ قرارٌ لا ترتيب ملفّات:** المحرّك لا يُنشئ إلا مؤثّرًا **يُعلنه الجهاز** ويقول مقياس
 * `AQ-01` إنه قابل للكتابة (أو يحتاج محوّلًا نُجرّبه). فالاتحاد هنا يحمل `feature`، والشاشة تسأل القدرات
 * قبل أن تسأل المحرّك — فلا يُبنى مقبض لشيء لا وجود له (ADR-07 · قاعدة المستودع).
 *
 * **والرموز مطابقة لمخرَج `audioEffectTypeToken` حرفيًّا** لأنها المفاتيح التي تُقارن بها القائمة
 * المُعلَنة؛ ورمزٌ يختلف بحرف يعني ميزةً لا تُطابق أبدًا فلا تُعرض — وهو عطبٌ صامت لا يُنتجه مُصرّف.
 */
package nd.max.core.audio

/**
 * مؤثّر يمكن إرفاقه والكتابة فيه.
 *
 * @param token نفس رمز [`audioEffectTypeToken`] — يُقاس عليه الوجود في قائمة المنصّة.
 * @param feature الصفُّ الذي يحكم عليه مقياس `AQ-01`، فتُحجب الميزة إن لم تُعلَن.
 */
enum class AudioEffectKind(val token: String, val feature: AudioFeature) {
    EQUALIZER("equalizer", AudioFeature.EQUALIZER),
    DYNAMICS("dynamics_processing", AudioFeature.DYNAMICS_PROCESSING),
    BASS_BOOST("bass_boost", AudioFeature.BASS_BOOST),
    VIRTUALIZER("virtualizer", AudioFeature.VIRTUALIZER),
    LOUDNESS("loudness_enhancer", AudioFeature.LOUDNESS_ENHANCER),
    PRESET_REVERB("preset_reverb", AudioFeature.PRESET_REVERB);

    companion object {
        fun ofToken(token: String): AudioEffectKind? = entries.firstOrNull { it.token == token }
    }
}

/**
 * هل يُسمح بمحاولة الإرفاق لهذه الميزة؟
 *
 * **والقاعدة مُشتقّة من حكم القدرات نفسه لا من نسخة ثانية منه:** `writable` ⇒ نعم بالمعنى الكامل،
 * و`needs_adapter` ⇒ **نعم نحاول**: المحوّل (جلسة نملكها) هو ما نجرّبه الآن، فإن نجح صار فعليًّا وإن رفض
 * قالت الشاشة سبب الرفض. وأمّا `unavailable`/`unknown` فـ**لا محاولة**: الأولى نفيٌ مقيس، والثانية
 * «لم أقس» — ولا يُبنى زرٌّ على ما لم يُقس.
 */
fun audioEffectAttachable(verdicts: List<AudioFeatureVerdict>?, kind: AudioEffectKind): Boolean {
    val support = verdicts?.firstOrNull { it.feature == kind.feature }?.support ?: return false
    return support == AudioSupport.WRITABLE || support == AudioSupport.NEEDS_ADAPTER
}

/** حكم الميزة نفسه — تُعرض حالته ونصّ سببه في الشاشة بجانب مقابضها. */
fun audioEffectSupport(verdicts: List<AudioFeatureVerdict>?, kind: AudioEffectKind): AudioFeatureVerdict? =
    verdicts?.firstOrNull { it.feature == kind.feature }
