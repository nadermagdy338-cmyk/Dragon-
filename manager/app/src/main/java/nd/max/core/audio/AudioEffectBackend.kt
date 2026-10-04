/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **محرّك المؤثّرات** (`AQ-02`…`AQ-05`): يفتح المؤثّر، ويقرؤه، ويكتب فيه **عبر المحكِّم**،
 * ويقرأه بعد الكتابة، ويُحرّره.
 *
 * **وهو الملفّ الثاني الذي يلمس `AudioEffect`** بعد المسبار — والفرق بينهما مقصود: المسبار يُنشئ
 * ويُحرّر في اللحظة نفسها ليحكم على الجلسة ٠، وهذا **يُبقي** المؤثّر حيًّا بين نداءٍ ونداء. ولذلك
 * كُلّ فتحٍ يقابله `close` صريح، وسجّلنا `hasControl` شرطًا للكتابة: مؤثّرٌ يملكه تطبيق آخر
 * (`hasControl=false`) **ليس فشلًا** — إنه «لا نملكه»، ويُقال بسببه لا بخطأ مبهم (معيار `AQ-02`).
 *
 * **ولا كتابة بلا قراءة (قاعدة المستودع الملزمة):** كل `apply` هنا يُقابله `read` من المنصّة نفسها،
 * والمحكِّم يقارن **حرفيًّا**؛ وما لم يطابق يُسترجَع ويُقال `failed` بسببه. فـ`AudioKnobVerdict`
 * يحمل `expected`/`actual` نصًّا — ما قُرئ، لا ما ظنّناه.
 *
 * **وحدود الديناميكيّ حدُّنا لا حدُّ العتاد:** المنصّة لا تُعلن مدًى لمعاملات الضاغط والمُحدِّد، وحدود
 * الواجهة في `AudioDynamicsBounds` مُسبَّقة بـ`UI_` لذلك؛ وما يعود من المنصّة **بعد** الكتابة هو
 * الحقيقة المعروضة، ولو قيّدت. وهذا هو الفرق بين شريط يقول ما نريد وشريط يقول ما صار.
 *
 * **والتحرير عند المغادرة شرطٌ لا نيّة حسنة:** من يُبقي `Equalizer` معلّقًا على الجلسة العامة يغيّر
 * صوت المستخدم بعد أن يغادر الشاشة — ولذلك `close` تُنادى من `onCleared` في الـViewModel.
 */
package nd.max.core.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import kotlin.math.roundToInt
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.SharedHardwareOwnershipStore
import nd.max.core.platform.EventLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * نتيجة محاولة الفتح — **الجلسة أو السبب، ولا ثالث**.
 *
 * و`session = null` مع `reason` مكتوب يعني «لم تُفتح»، و`reason = null` مع جلسة يعني «فُتحت» (ولو لم
 * نملك التحكّم — فذلك يُقاس عند الكتابة ويُقال هناك).
 */
data class AudioEffectOpen(
    val session: AudioEffectSession?,
    val reason: String? = null,
)

/**
 * عدد نطاقات pre/post-EQ التي نطلبها في `DynamicsProcessing` — **طلبٌ يُقال، والمنصّة تُقرّ**.
 * والثابت نفسه يعيش الآن في [`DEFAULT_REQUESTED_EQ_BANDS`] لأنّ اختبار السلّم النقيّ يقيسه،
 * وهذا الاسم يبقى وجهًا مقروءًا في هذا الملفّ.
 */
private const val DYNAMICS_EQ_BANDS = DEFAULT_REQUESTED_EQ_BANDS

/** عدد نطاقات الضاغط متعدّد النطاقات الذي نطلبه. */
private const val DYNAMICS_MBC_BANDS = DEFAULT_REQUESTED_MBC_BANDS

@Singleton
class AudioEffectBackend @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {

    // ─────────────────────────────── ‏AQ-02: الفتح والإغلاق والملكيّة ───────────────────────────────

    /**
     * يفتح مؤثّرًا من نوع [kind] على الجلسة العامة (٠).
     *
     * **ولا يرمي أبدًا:** كل مسار فاشل يعود بسبب مكتوب. والفشل هنا **نتيجةٌ تُقال**، لأنّ إرفاق الجلسة ٠
     * مُهمَل في المنصّة منذ أندرويد ٩، ويبقى يعمل على أجهزة كثيرة — فلا نعِد ولا نرفض: **نُجرّب ونقول**.
     */
    fun open(context: Context, kind: AudioEffectKind): AudioEffectOpen {
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return AudioEffectOpen(null, AudioEffectReason.STORE_UNCONFIGURED)
        }
        // ومحرّك الديناميكيّ **يُرفَق بسلّم لا بمحاولة** (والسبب مُقاس في [`DYNAMICS_ATTACH_LADDER`]):
        // كان الإرفاق الواحد يفشل على جهاز المالك فيُطوى السبب في رمزٍ لا يقول أين سقطنا.
        if (kind == AudioEffectKind.DYNAMICS) return openDynamics(context)
        val created = runCatching { createEffect(context, kind) }.getOrNull()
            ?: return AudioEffectOpen(null, AudioEffectReason.ATTACH_REFUSED)
        return AudioEffectOpen(AudioEffectSession(kind, created))
    }

    /**
     * إرفاق `DynamicsProcessing` — **سلّم من ثلاث خطوات، بترتيبه المعرَّف**.
     *
     * ١) **هندسة المنصّة نفسها** (`DynamicsProcessing(GLOBAL_SESSION)` بلا `Config`) — لا عدد نطلبه،
     *    ولا هندسة نفترضها؛ وهذا هو الفرق الجوهريّ عن `dynamicsOf` السابقة.
     * ٢) وإن رُفضت: **هندسة دقّة التردّد** بالأعداد المقيسة إن وُجد قياس، وإلا فطلبنا المُعلَن.
     * ٣) وإن رُفضت: **هندسة زمن الاستقرار** — فبعض المعالجات تُفضّلها.
     *
     * **وكلُّ خطوة تُسجَّل برمزها**، والحكم المعروض واحدٌ صريح («فشل السلّم كلّه») — فلا يُخلط
     * تشخيصٌ بحكم (وهو الدرس المقيس في `AudioCapabilitySection`: النفي يُعلن عن شجرتنا لا عن الجهاز).
     */
    private fun openDynamics(context: Context): AudioEffectOpen {
        val channels = outputChannelCount(context)
        var measured: DynamicsMeasuredArchitecture? = null
        var lastRefusal: String = AudioEffectReason.ATTACH_REFUSED
        for (step in DYNAMICS_ATTACH_LADDER) {
            val plan = dynamicsAttachPlan(
                step = step,
                channels = channels,
                requestedEqBands = DYNAMICS_EQ_BANDS,
                requestedMbcBands = DYNAMICS_MBC_BANDS,
                measured = measured,
            )
            val created = runCatching { dynamicsFor(plan) }.getOrNull()
            if (created != null) {
                // المقيس بعد الإرفاق **يُثبَّت** ليُستعمل في خطوةٍ تالية إنْ لزم، فلا نُخمّن عددًا أبدًا.
                measured = runCatching { measureArchitecture(created, plan.channels) }.getOrNull() ?: measured
                // يُسجّل **أيّ خطوةٍ** قُبلت، وبأيّ هندسة — فيُقارن سطرنا بسطر HAL بلا ترجمة بينهما.
                EventLog.audioOp(
                    target = "dynamics_attach:${plan.step.token}",
                    outcome = "applied",
                    expected = if (plan.usePlatformDefault) "platform-default" else "config",
                    actual = "preEq=${plan.preEqBands} mbc=${plan.mbcBands} " +
                        "postEq=${plan.postEqBands} channels=${plan.channels}",
                )
                return AudioEffectOpen(AudioEffectSession(AudioEffectKind.DYNAMICS, created))
            }
            lastRefusal = dynamicsAttachRefusal(plan.step)
            EventLog.audioOp(
                target = "dynamics_attach:${plan.step.token}",
                outcome = "refused",
                reason = lastRefusal,
            )
        }
        // والحكم **واحدٌ صريحٌ**: «جرّبنا الثلاثة ولا واحد قُبل» — ومعه آخر رمزٍ مقيس للتشخيص.
        EventLog.audioOp(
            target = "dynamics_attach",
            outcome = "refused",
            reason = DYNAMICS_ATTACH_ALL_REFUSED,
            actual = lastRefusal,
        )
        return AudioEffectOpen(null, DYNAMICS_ATTACH_ALL_REFUSED)
    }

    /**
     * ينشئ المحرّك من خطةٍ — **وخطوةُ المنصّة لا `Config` لها** (وهذا هو معناها الحرفيّ).
     * وإن رمى المُنشئ فذلك **رفضُ إرفاقٍ يُقال** لا استثناءٌ يهرب.
     */
    private fun dynamicsFor(plan: DynamicsAttachPlan): DynamicsProcessing {
        if (plan.usePlatformDefault) return DynamicsProcessing(GLOBAL_SESSION)
        return DynamicsProcessing(0, GLOBAL_SESSION, dynamicsConfig(plan))
    }

    /** يبني `Config` من الخطة — **قدْر ما طُلب فقط**، ولا عددَ يُكتب هنا حرفيًّا. */
    private fun dynamicsConfig(plan: DynamicsAttachPlan): DynamicsProcessing.Config =
        DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            plan.channels,
            true,
            plan.preEqBands,
            true,
            plan.mbcBands,
            true,
            plan.postEqBands,
            true,
        ).build()

    /**
     * ما أعلنته المنصّة عن هندستها بعد إرفاقٍ ناجح — **قراءةٌ لا افتراض** (ومنه تُبنى أيّ إعادة).
     *
     * **والتصحيح مقيس بـ`javap`:** `DynamicsProcessing.Config` تُعلن أربعة قارئات فقط — `getVariant`
     * و`getPreEqBandCount` و`getMbcBandCount` و`getPostEqBandCount` — **ولا `getChannelCount`**
     * (`Config` لا تحمل القنوات أصلًا: القنوات تُمرّز إلى `Builder` ولا تُقرأ منه). فعدد القنوات هنا
     * **يُمرَّر من الخطة** — وهي مصدره المقيس (`AudioManager.getDevices`)، لا اختراعٌ من `config`.
     */
    private fun measureArchitecture(effect: DynamicsProcessing, channels: Int): DynamicsMeasuredArchitecture {
        val config = effect.config
        return DynamicsMeasuredArchitecture(
            variant = runCatching { config.variant }.getOrNull(),
            channels = channels,
            preEqBands = config.preEqBandCount,
            mbcBands = config.mbcBandCount,
            postEqBands = config.postEqBandCount,
        )
    }

    /** يُحرّر المؤثّر — **يُنادى في كل مسار مغادرة**، ولو تكرّر فالتحرير مأمون التكرار. */
    fun close(session: AudioEffectSession) {
        session.release()
    }

    /**
     * تمكين/تعطيل عامّ — يُقرأ بعده من المنصّة.
     *
     * والتمكين ليس مقبضًا معامليًّا في المنصّة (`AudioEffect.setEnabled`)، فيُمرَّر عبر المحكِّم كغيره:
     * القفل اليدويّ الشامل يراه، والقراءة تُثبته.
     */
    fun setEnabled(session: AudioEffectSession, enabled: Boolean, auditToken: String): AudioKnobVerdict {
        val desired = if (enabled) "1" else "0"
        return knob(
            session = session,
            paramKey = "enabled",
            desired = desired,
            auditToken = auditToken,
            apply = { session.applyEnabled(it == "1") },
            read = { session.enabled?.let { value -> if (value) "1" else "0" } },
        )
    }

    // ─────────────────────────────────────── ‏AQ-03: المعادل ───────────────────────────────────────

    /** لقطة المعادل: كل نطاق كما أعلنته المنصّة، والأنماط المُعرَّفة عندها. */
    fun readEq(session: AudioEffectSession): AudioEqSnapshot? {
        val eq = session.effect as? Equalizer ?: return null
        return runCatching {
            val count = eq.numberOfBands.toInt()
            if (count <= 0) return null
            val range = runCatching { eq.bandLevelRange }.getOrNull()
            val bands = (0 until count).map { index ->
                val band = index.toShort()
                val freq = runCatching { eq.getBandFreqRange(band) }.getOrNull()
                AudioEqBand(
                    index = index,
                    centerHz = runCatching { eq.getCenterFreq(band) }.getOrNull()?.takeIf { it > 0 },
                    rangeLowHz = freq?.getOrNull(0)?.takeIf { it >= 0 },
                    rangeHighHz = freq?.getOrNull(1)?.takeIf { it >= 0 },
                    levelMb = runCatching { eq.getBandLevel(band) }.getOrNull()?.toInt(),
                    levelMinMb = range?.getOrNull(0)?.toInt(),
                    levelMaxMb = range?.getOrNull(1)?.toInt(),
                )
            }
            val presets = (0 until eq.numberOfPresets.toInt()).mapNotNull { index ->
                runCatching { eq.getPresetName(index.toShort()) }.getOrNull()?.takeIf { it.isNotBlank() }
            }
            AudioEqSnapshot(
                bands = bands,
                presets = presets,
                currentPreset = runCatching { eq.currentPreset.toInt() }.getOrNull(),
            )
        }.getOrNull()
    }

    /** يكتب كسب نطاق — **ويقرأ `getBandLevel` بعده**، فيُعرض ما ردّته المنصّة لا ما طلبناه. */
    fun writeEqBand(
        session: AudioEffectSession,
        index: Int,
        levelMb: Int,
        auditToken: String,
    ): AudioKnobVerdict {
        val eq = session.effect as? Equalizer
            ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, levelMb.toString())
        val band = runCatching { index.toShort() }.getOrNull()
            ?: return audioKnobNotAttempted(AudioEffectReason.BAND_OUT_OF_RANGE, levelMb.toString())
        val count = runCatching { eq.numberOfBands.toInt() }.getOrNull()
        if (count == null || index < 0 || index >= count) {
            return audioKnobNotAttempted(AudioEffectReason.BAND_OUT_OF_RANGE, levelMb.toString())
        }
        val requested = levelMb.toString()
        return knob(
            session = session,
            paramKey = "eq_band_$index",
            desired = requested,
            auditToken = auditToken,
            apply = { value ->
                runCatching {
                    val target = value.toIntOrNull()?.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        ?: return@runCatching false
                    eq.setBandLevel(band, target.toShort())
                    true
                }.getOrDefault(false)
            },
            read = { runCatching { eq.getBandLevel(band).toInt().toString() }.getOrNull() },
        )
    }

    /** يستعمل نمط المنصّة — ويُقرأ `getCurrentPreset` بعده؛ ورفضُ المنصّة يُقال لا يُخفى. */
    fun useEqPreset(session: AudioEffectSession, presetIndex: Int, auditToken: String): AudioKnobVerdict {
        val eq = session.effect as? Equalizer
            ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, presetIndex.toString())
        val requested = presetIndex.toString()
        return knob(
            session = session,
            paramKey = "eq_preset",
            desired = requested,
            auditToken = auditToken,
            apply = { value ->
                runCatching {
                    val index = value.toIntOrNull() ?: return@runCatching false
                    eq.usePreset(index.toShort())
                    true
                }.getOrDefault(false)
            },
            read = { runCatching { eq.currentPreset.toInt().toString() }.getOrNull() },
        )
    }

    // ────────────────────────────────────── ‏AQ-04: الديناميكيّ ──────────────────────────────────────

    /** لقطة المحرّك الديناميكيّ — تُقرأ من `getConfig()` **بعد** كل كتابة، لا مرّة واحدة. */
    fun readDynamics(session: AudioEffectSession): AudioDynamicsSnapshot? {
        val engine = session.effect as? DynamicsProcessing ?: return null
        return runCatching {
            val config = engine.config ?: return null
            val channels = engine.channelCount.takeIf { it > 0 } ?: return null
            AudioDynamicsSnapshot(
                channels = channels,
                preEqInUse = config.isPreEqInUse,
                postEqInUse = config.isPostEqInUse,
                mbcInUse = config.isMbcInUse,
                limiterInUse = config.isLimiterInUse,
                preEq = eqBandsOf(config, DynamicsStage.PRE, channels),
                postEq = eqBandsOf(config, DynamicsStage.POST, channels),
                mbc = mbcBandsOf(config, channels),
                limiter = runCatching {
                    val limiter = config.getLimiterByChannelIndex(0) ?: return@runCatching null
                    DynamicsLimiter(
                        enabled = limiter.isEnabled,
                        inUse = config.isLimiterInUse,
                        thresholdDb = limiter.threshold,
                        ratio = limiter.ratio,
                        attackMs = limiter.attackTime,
                        releaseMs = limiter.releaseTime,
                        postGainDb = limiter.postGain,
                    )
                }.getOrNull(),
                inputGainsDb = (0 until channels).map { channel ->
                    runCatching { config.getInputGainByChannelIndex(channel) }.getOrDefault(0f)
                },
            )
        }.getOrNull()
    }

    /**
     * يكتب كسب نطاق معادل محرّكٍ (pre/post) — **على كل القنوات معًا**.
     *
     * ولماذا «كل القنوات»: مقبضُ توازن القنوات **مستقلّ** عنه (`writeInputGain`)، فلو كتبنا الكسب على
     * قناةٍ واحدة لصار تغييرُ كسبٍ تغييرَ توازنٍ بالخطأ — وهو تفاعل غير مقصود يُخفى، وهو مرفوض.
     */
    fun writeEqBandGain(
        session: AudioEffectSession,
        stage: DynamicsStage,
        index: Int,
        gainDb: Float,
        auditToken: String,
    ): AudioKnobVerdict {
        val engine = session.effect as? DynamicsProcessing
            ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, d(gainDb))
        val config = runCatching { engine.config }.getOrNull()
            ?: return audioKnobNotAttempted(AudioEffectReason.PARAM_UNREADABLE, d(gainDb))
        val bandCount = if (stage == DynamicsStage.PRE) config.preEqBandCount else config.postEqBandCount
        if (index < 0 || index >= bandCount) {
            return audioKnobNotAttempted(AudioEffectReason.BAND_OUT_OF_RANGE, d(gainDb))
        }
        val current = runCatching {
            if (stage == DynamicsStage.PRE) config.getPreEqBandByChannelIndex(0, index)
            else config.getPostEqBandByChannelIndex(0, index)
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.PARAM_UNREADABLE, d(gainDb))

        val requested = d(gainDb)
        return knob(
            session = session,
            paramKey = "${stage.token}_eq_$index",
            desired = requested,
            auditToken = auditToken,
            apply = { value ->
                runCatching {
                    val gain = value.toFloatOrNull() ?: return@runCatching false
                    val band = DynamicsProcessing.EqBand(current.isEnabled, current.cutoffFrequency, gain)
                    if (stage == DynamicsStage.PRE) engine.setPreEqBandAllChannelsTo(index, band)
                    else engine.setPostEqBandAllChannelsTo(index, band)
                    true
                }.getOrDefault(false)
            },
            read = {
                runCatching {
                    val band = if (stage == DynamicsStage.PRE) {
                        engine.getPreEqBandByChannelIndex(0, index)
                    } else {
                        engine.getPostEqBandByChannelIndex(0, index)
                    }
                    d(band.gain)
                }.getOrNull()
            },
        )
    }

    /** يكتب عتبة قطع نطاق محرّكٍ (pre/post) على كل القنوات. */
    fun writeEqBandCutoff(
        session: AudioEffectSession,
        stage: DynamicsStage,
        index: Int,
        cutoffHz: Float,
        auditToken: String,
    ): AudioKnobVerdict {
        val engine = session.effect as? DynamicsProcessing
            ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, d(cutoffHz))
        val config = runCatching { engine.config }.getOrNull()
            ?: return audioKnobNotAttempted(AudioEffectReason.PARAM_UNREADABLE, d(cutoffHz))
        val bandCount = if (stage == DynamicsStage.PRE) config.preEqBandCount else config.postEqBandCount
        if (index < 0 || index >= bandCount) {
            return audioKnobNotAttempted(AudioEffectReason.BAND_OUT_OF_RANGE, d(cutoffHz))
        }
        val current = runCatching {
            if (stage == DynamicsStage.PRE) config.getPreEqBandByChannelIndex(0, index)
            else config.getPostEqBandByChannelIndex(0, index)
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.PARAM_UNREADABLE, d(cutoffHz))

        val requested = d(cutoffHz)
        return knob(
            session = session,
            paramKey = "${stage.token}_cutoff_$index",
            desired = requested,
            auditToken = auditToken,
            apply = { value ->
                runCatching {
                    val cutoff = value.toFloatOrNull() ?: return@runCatching false
                    val band = DynamicsProcessing.EqBand(current.isEnabled, cutoff, current.gain)
                    if (stage == DynamicsStage.PRE) engine.setPreEqBandAllChannelsTo(index, band)
                    else engine.setPostEqBandAllChannelsTo(index, band)
                    true
                }.getOrDefault(false)
            },
            read = {
                runCatching {
                    val band = if (stage == DynamicsStage.PRE) {
                        engine.getPreEqBandByChannelIndex(0, index)
                    } else {
                        engine.getPostEqBandByChannelIndex(0, index)
                    }
                    d(band.cutoffFrequency)
                }.getOrNull()
            },
        )
    }

    /**
     * يكتب معامل نطاق ضغطٍ واحدًا — بكل معامله (`DynamicsParam`).
     *
     * والقاعدة: **نقرأ النطاق، نُغيّر معاملًا واحدًا، نُعيد بناءه، نكتبه على كل القنوات، ثمّ نقرأه.**
     * ولا نبني نطاقًا من الصفر عند كل تغيير: ذلك يُصفّر بقية معامله، وهو عطبٌ صامت لا يُنتجه مُصرّف.
     */
    fun writeMbcParam(
        session: AudioEffectSession,
        index: Int,
        param: DynamicsParam,
        value: Float,
        auditToken: String,
    ): AudioKnobVerdict {
        val engine = session.effect as? DynamicsProcessing
            ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, d(value))
        val config = runCatching { engine.config }.getOrNull()
            ?: return audioKnobNotAttempted(AudioEffectReason.PARAM_UNREADABLE, d(value))
        if (index < 0 || index >= config.mbcBandCount) {
            return audioKnobNotAttempted(AudioEffectReason.BAND_OUT_OF_RANGE, d(value))
        }
        val current = runCatching { config.getMbcBandByChannelIndex(0, index) }.getOrNull()
            ?: return audioKnobNotAttempted(AudioEffectReason.PARAM_UNREADABLE, d(value))

        val requested = d(value)
        return knob(
            session = session,
            paramKey = "mbc_${param.token}_$index",
            desired = requested,
            auditToken = auditToken,
            apply = { text ->
                runCatching {
                    val target = text.toFloatOrNull() ?: return@runCatching false
                    val band = DynamicsProcessing.MbcBand(current)
                    param.write(band, target)
                    engine.setMbcBandAllChannelsTo(index, band)
                    true
                }.getOrDefault(false)
            },
            read = {
                runCatching {
                    val band = engine.getMbcBandByChannelIndex(0, index)
                    d(param.read(band))
                }.getOrNull()
            },
        )
    }

    /** يكتب معامل المُحدِّد الوحيد — و`Limiter` بناؤه من مثيلٍ قائم كي لا تُصفَّر معامله الأخرى. */
    fun writeLimiterParam(
        session: AudioEffectSession,
        param: DynamicsParam,
        value: Float,
        auditToken: String,
    ): AudioKnobVerdict {
        val engine = session.effect as? DynamicsProcessing
            ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, d(value))
        val current = runCatching { engine.getLimiterByChannelIndex(0) }.getOrNull()
            ?: return audioKnobNotAttempted(AudioEffectReason.PARAM_UNREADABLE, d(value))

        val requested = d(value)
        return knob(
            session = session,
            paramKey = "limiter_${param.token}",
            desired = requested,
            auditToken = auditToken,
            apply = { text ->
                runCatching {
                    val target = text.toFloatOrNull() ?: return@runCatching false
                    val limiter = DynamicsProcessing.Limiter(current)
                    param.writeLimiter(limiter, target)
                    engine.setLimiterAllChannelsTo(limiter)
                    true
                }.getOrDefault(false)
            },
            read = {
                runCatching { d(param.readLimiter(engine.getLimiterByChannelIndex(0))) }.getOrNull()
            },
        )
    }

    /**
     * يكتب **دخل قناة** — وهو التوازن: لا معامل DSP من عندنا، بل واجهة المنصّة العامة نفسها.
     *
     * ويُقرأ بعده من `getConfig().getInputGainByChannelIndex(ch)` — فالموضع المعروض في الشاشة يأتي من
     * المحرّك لا من الذاكرة.
     */
    fun writeInputGain(
        session: AudioEffectSession,
        channel: Int,
        gainDb: Float,
        auditToken: String,
    ): AudioKnobVerdict {
        val engine = session.effect as? DynamicsProcessing
            ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, d(gainDb))
        val channels = runCatching { engine.channelCount }.getOrNull()
        if (channels == null || channel < 0 || channel >= channels) {
            return audioKnobNotAttempted(AudioEffectReason.BAND_OUT_OF_RANGE, d(gainDb))
        }
        val requested = d(gainDb)
        return knob(
            session = session,
            paramKey = "input_gain_$channel",
            desired = requested,
            auditToken = auditToken,
            apply = { text ->
                runCatching {
                    val gain = text.toFloatOrNull() ?: return@runCatching false
                    engine.setInputGainbyChannel(channel, gain)
                    true
                }.getOrDefault(false)
            },
            read = {
                runCatching { d(engine.getInputGainByChannelIndex(channel)) }.getOrNull()
            },
        )
    }

    // ───────────────────────────────── ‏AQ-05: المؤثّرات البسيطة ─────────────────────────────────

    /** قراءة قوّة مؤثّر بسيط — وكل عود `null` يعني «لا قراءة»، لا صفرًا. */
    fun readStrength(session: AudioEffectSession): AudioStrengthSnapshot? {
        val effect = session.effect
        return runCatching {
            when (effect) {
                is BassBoost -> AudioStrengthSnapshot(
                    kind = session.kind,
                    supported = runCatching { effect.strengthSupported }.getOrNull(),
                    value = runCatching { effect.roundedStrength.toInt() }.getOrNull(),
                )
                is Virtualizer -> AudioStrengthSnapshot(
                    kind = session.kind,
                    supported = runCatching { effect.strengthSupported }.getOrNull(),
                    value = runCatching { effect.roundedStrength.toInt() }.getOrNull(),
                )
                is LoudnessEnhancer -> AudioStrengthSnapshot(
                    kind = session.kind,
                    supported = true,
                    value = runCatching { effect.targetGain.roundToInt() }.getOrNull(),
                )
                is PresetReverb -> AudioStrengthSnapshot(
                    kind = session.kind,
                    supported = true,
                    value = null,
                    presets = AudioReverbPreset.tokens,
                    currentPreset = runCatching {
                        AudioReverbPreset.tokens.indexOf(reverbTokenOf(effect.preset))
                    }.getOrNull()?.takeIf { it >= 0 },
                )
                else -> null
            }
        }.getOrNull()
    }

    /** يكتب قوّة مؤثّر بسيط — والوحدة من نوعه: `0..1000` للجهير/المحيط، و`mB` للجهارة. */
    fun writeStrength(
        session: AudioEffectSession,
        value: Int,
        auditToken: String,
    ): AudioKnobVerdict {
        val effect = session.effect
        val requested = value.toString()
        return when (effect) {
            is BassBoost -> knob(
                session = session,
                paramKey = "strength",
                desired = requested,
                auditToken = auditToken,
                apply = { text ->
                    runCatching {
                        val target = text.toIntOrNull() ?: return@runCatching false
                        effect.setStrength(target.coerceIn(0, 1000).toShort())
                        true
                    }.getOrDefault(false)
                },
                read = { runCatching { effect.roundedStrength.toInt().toString() }.getOrNull() },
            )
            is Virtualizer -> knob(
                session = session,
                paramKey = "strength",
                desired = requested,
                auditToken = auditToken,
                apply = { text ->
                    runCatching {
                        val target = text.toIntOrNull() ?: return@runCatching false
                        effect.setStrength(target.coerceIn(0, 1000).toShort())
                        true
                    }.getOrDefault(false)
                },
                read = { runCatching { effect.roundedStrength.toInt().toString() }.getOrNull() },
            )
            is LoudnessEnhancer -> knob(
                session = session,
                paramKey = "target_gain",
                desired = requested,
                auditToken = auditToken,
                apply = { text ->
                    runCatching {
                        val target = text.toIntOrNull() ?: return@runCatching false
                        effect.setTargetGain(target.coerceIn(0, AudioStrengthBounds.UI_LOUDNESS_MAX_MB))
                        true
                    }.getOrDefault(false)
                },
                read = { runCatching { effect.targetGain.roundToInt().toString() }.getOrNull() },
            )
            is PresetReverb -> knob(
                session = session,
                paramKey = "preset",
                desired = requested,
                auditToken = auditToken,
                apply = { text ->
                    runCatching {
                        val token = AudioReverbPreset.tokens.getOrNull(text.toIntOrNull() ?: -1)
                            ?: return@runCatching false
                        effect.preset = reverbCodeOf(token).toShort()
                        true
                    }.getOrDefault(false)
                },
                read = {
                    runCatching {
                        AudioReverbPreset.tokens.indexOf(reverbTokenOf(effect.preset)).toString()
                    }.getOrNull()
                },
            )
            else -> audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, requested)
        }
    }

    // ────────────────────────────────────────── الأدوات ──────────────────────────────────────────

    /**
     * الطريق الواحد للكتابة: حراسة الملكيّة ← حراسة المخزن ← المحكِّم ← حكم.
     *
     * **والترتيب مقصود:** الملكيّة والمخزن قبل المحكِّم، فلا يُسجَّل طلبٌ لا أمل في تنفيذه، ولا يُعاد
     * `arbiter-unavailable` مكان السبب الحقيقيّ.
     */
    private fun knob(
        session: AudioEffectSession,
        paramKey: String,
        desired: String,
        auditToken: String,
        apply: (String) -> Boolean,
        read: () -> String?,
    ): AudioKnobVerdict {
        val live = runCatching { read() }.getOrNull()
        // **وحراستان لا واحدة (تكملة ٢٤٣ · ٣ح-أ):** الاستطلاع ([`hasControl`]) حكمُ **اللحظة**، وهي
        // المرجع عند الكتابة؛ وما **أُعلن بين الكتابتين** ([`AudioEffectSession.control`]) يكشف مؤثّرًا
        // أخذه غيرنا والمقبض لم يُلمَس بعد. فبلا الثانية تُعرض مقابضُ ميْتة وتُقرأ «مطبَّقة».
        session.control.blockReason?.let { reason ->
            return audioKnobNotAttempted(reason, desired, live)
        }
        // **والحارس الثاني مسموعٌ لا شكليّ (تكملة ٢٤٣):** مؤثّرٌ نملكه والجهاز معطّله ⇒ الكتابة
        // تُقرأ مطابقةً ولا تُسمع. فتُقال قبل أن تُكتب، **لا يُقال «مطبَّق» على أثرٍ مُطفأ**.
        // Explicit enable/disable must remain possible for an engine-disabled effect.
        // Otherwise an Off → On toggle is permanently blocked by its own disabled state.
        if (paramKey != "enabled") {
            session.control.disabledReason?.let { reason ->
                return audioKnobNotAttempted(reason, desired, live)
            }
        }
        // ولا كتابة على مؤثّر يملكه غيرنا: «لا نملكه» نتيجةٌ تُقال، لا فشلٌ مبهم (معيار `AQ-02`).
        if (session.hasControl != true) {
            return audioKnobNotAttempted(AudioEffectReason.CONTROL_NOT_OWNED, desired, live)
        }
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return audioKnobNotAttempted(AudioEffectReason.STORE_UNCONFIGURED, desired, live)
        }
        val key = HardwareControlKey.audioEffect(session.kind.token, paramKey)
        val result = runCatching {
            arbiter.submit(
                key = key,
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = desired,
                apply = apply,
                read = read,
                // والاسترجاع هو الكتابة نفسها بالقيمة القديمة: خط الأساس يقرؤه المحكِّم بنفسه.
                restore = apply,
            )
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.ARBITER_UNAVAILABLE, desired, live)

        return audioKnobVerdict(
            attempted = true,
            blocked = result.blocked,
            applied = result.applied,
            verified = result.verified,
            expected = desired,
            actual = result.actual,
            error = result.error,
        )
    }

    /**
     * صياغة قيمة عشرية واحدة **موحَّدة بين الكتابة والقراءة** — وهذا شرط الحكم الحرفيّ.
     *
     * ولو كتبنا `3.5f.toString()` وقرأنا `3.5000001` لما طابق المحكِّم أبدًا. فالتقريب إلى منزلة واحدة
     * يجعل المقارنة قابلة للتحقّق **ويُعلن دقّة المقبض فعلًا**: أدقّ من ذلك لا يُقاس على هذه المعاملات.
     */
    private fun d(value: Float): String {
        val rounded = (value * 10f).roundToInt() / 10f
        return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
    }

    private fun eqBandsOf(
        config: DynamicsProcessing.Config,
        stage: DynamicsStage,
        channels: Int,
    ): List<DynamicsEqBand> {
        val count = if (stage == DynamicsStage.PRE) config.preEqBandCount else config.postEqBandCount
        if (count <= 0 || channels <= 0) return emptyList()
        return (0 until count).mapNotNull { index ->
            runCatching {
                val band = if (stage == DynamicsStage.PRE) {
                    config.getPreEqBandByChannelIndex(0, index)
                } else {
                    config.getPostEqBandByChannelIndex(0, index)
                }
                DynamicsEqBand(
                    index = index,
                    cutoffHz = band.cutoffFrequency,
                    gainDb = band.gain,
                    enabled = band.isEnabled,
                )
            }.getOrNull()
        }
    }

    private fun mbcBandsOf(config: DynamicsProcessing.Config, channels: Int): List<DynamicsMbcBand> {
        val count = config.mbcBandCount
        if (count <= 0 || channels <= 0) return emptyList()
        return (0 until count).mapNotNull { index ->
            runCatching {
                val band = config.getMbcBandByChannelIndex(0, index)
                DynamicsMbcBand(
                    index = index,
                    cutoffHz = band.cutoffFrequency,
                    enabled = band.isEnabled,
                    thresholdDb = band.threshold,
                    ratio = band.ratio,
                    attackMs = band.attackTime,
                    releaseMs = band.releaseTime,
                    kneeDb = band.kneeWidth,
                    noiseGateDb = band.noiseGateThreshold,
                    expanderRatio = band.expanderRatio,
                    preGainDb = band.preGain,
                    postGainDb = band.postGain,
                )
            }.getOrNull()
        }
    }

    /**
     * إنشاء المؤثّر — **والمُنشئون مختلفون**: `BassBoost`/`Virtualizer`/`PresetReverb`/`Equalizer`
     * تأخذ (أولويّة، جلسة)، و`LoudnessEnhancer` تأخذ (جلسة) وحدها، و`DynamicsProcessing` تُبنى من
     * `Config` مُهيّأ **قبل** الإرفاق (وهو الفرق الذي يجعل تهيئتها ممكنة أصلًا).
     */
    private fun createEffect(context: Context, kind: AudioEffectKind): AudioEffect = when (kind) {
        AudioEffectKind.EQUALIZER -> Equalizer(0, GLOBAL_SESSION)
        AudioEffectKind.DYNAMICS -> dynamicsOf(context)
        AudioEffectKind.BASS_BOOST -> BassBoost(0, GLOBAL_SESSION)
        AudioEffectKind.VIRTUALIZER -> Virtualizer(0, GLOBAL_SESSION)
        AudioEffectKind.LOUDNESS -> LoudnessEnhancer(GLOBAL_SESSION)
        AudioEffectKind.PRESET_REVERB -> PresetReverb(0, GLOBAL_SESSION)
    }

    /**
     * إنشاء محرّك الديناميّ — **للتوافق مع مسار [`createEffect`] العامّ**، وهو خطةُ الخطوة
     * المطلوبة عند عدم قياس؛ أمّا الإرفاق الفعليّ فيمرّ بالسلّم في [`openDynamics`].
     */
    private fun dynamicsOf(context: Context): DynamicsProcessing {
        val plan = dynamicsAttachPlan(
            step = DynamicsAttachStep.RESOLUTION_VARIANT,
            channels = outputChannelCount(context),
            requestedEqBands = DYNAMICS_EQ_BANDS,
            requestedMbcBands = DYNAMICS_MBC_BANDS,
        )
        return dynamicsFor(plan)
    }

    /**
     * عدد قنوات المخرج — **من الجهاز المُعلَن لا من خاصيّة مخفيّة**.
     *
     * **والتصحيح مقيس:** `AudioManager.PROPERTY_OUTPUT_CHANNELS` **غير موجود في سطح الـSDK** (مقيس
     * بـ`javap` على `android.jar` الذي يُصرَّف عليه المشروع في هذه الجولة — الموجود اثنتان فقط:
     * `PROPERTY_OUTPUT_SAMPLE_RATE` و`PROPERTY_OUTPUT_FRAMES_PER_BUFFER`) — فقراءته أمسكها المُصرّف
     * قبل الـCI. والمقيس المتاح فعلًا `AudioDeviceInfo.getChannelCounts()` على مخرج حقيقيّ.
     */
    private fun outputChannelCount(context: Context): Int {
        val counts = runCatching {
            managerOf(context)
                ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.firstOrNull { it.isSink }
                ?.channelCounts
        }.getOrNull()
        // والمحرّك يقبل ١ أو ٢؛ وأحاديٌّ مُعلَن صراحةً يُحترم، وما عداه ستيريو — وهو ما تقوله المنصّة.
        return if (counts != null && counts.isNotEmpty() && counts.all { it == 1 }) 1 else DEFAULT_CHANNELS
    }

    private fun managerOf(context: Context): AudioManager? =
        runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    private fun reverbCodeOf(token: String): Int = when (token) {
        AudioReverbPreset.NONE -> PresetReverb.PRESET_NONE.toInt()
        AudioReverbPreset.SMALL_ROOM -> PresetReverb.PRESET_SMALLROOM.toInt()
        AudioReverbPreset.MEDIUM_ROOM -> PresetReverb.PRESET_MEDIUMROOM.toInt()
        AudioReverbPreset.LARGE_ROOM -> PresetReverb.PRESET_LARGEROOM.toInt()
        AudioReverbPreset.MEDIUM_HALL -> PresetReverb.PRESET_MEDIUMHALL.toInt()
        AudioReverbPreset.LARGE_HALL -> PresetReverb.PRESET_LARGEHALL.toInt()
        AudioReverbPreset.PLATE -> PresetReverb.PRESET_PLATE.toInt()
        else -> PresetReverb.PRESET_NONE.toInt()
    }

    private fun reverbTokenOf(code: Short): String = when (code.toInt()) {
        PresetReverb.PRESET_SMALLROOM.toInt() -> AudioReverbPreset.SMALL_ROOM
        PresetReverb.PRESET_MEDIUMROOM.toInt() -> AudioReverbPreset.MEDIUM_ROOM
        PresetReverb.PRESET_LARGEROOM.toInt() -> AudioReverbPreset.LARGE_ROOM
        PresetReverb.PRESET_MEDIUMHALL.toInt() -> AudioReverbPreset.MEDIUM_HALL
        PresetReverb.PRESET_LARGEHALL.toInt() -> AudioReverbPreset.LARGE_HALL
        PresetReverb.PRESET_PLATE.toInt() -> AudioReverbPreset.PLATE
        else -> AudioReverbPreset.NONE
    }

    /** جلسة المزج العامّ — الرقم من المنصّة بالاسم لا سحرًا في موضعين. */
    private companion object {
        const val GLOBAL_SESSION = 0
        const val DEFAULT_CHANNELS = 2
    }
}

/** مرحلة المعادل داخل محرّك الديناميكيّ — رمزها يُبنى عليه مفتاح المقبض، فلا يُنسخ حرفيًّا. */
enum class DynamicsStage(val token: String) {
    PRE("pre"),
    POST("post"),
}
