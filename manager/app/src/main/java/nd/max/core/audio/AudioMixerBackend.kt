/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **مازج المخرج**: ما يُعلنه الجهاز من سمات، وما يقبله، وما صار فعلًا (`AQ-06`).
 *
 * **وهذه أعلى ميزة صوتيّة عامّة في المنصّة، وهي أيضًا أكثرهنّ تواضعًا في الوعد:** نعرض **ما أعلنه
 * الجهاز نفسه** (`getSupportedMixerAttributes`) ولا نبني قائمة من عندنا؛ والمستخدم يختار من المُعلَن،
 * فطلبُ ما لا يُدعم لا يقع أصلًا — وهذا هو احتواء الخطر الذي دفعنا إلى هذا التصميم.
 *
 * **و`bit-perfect` تُقرأ من المنصّة ولا تُفترض:** `AudioMixerAttributes.getMixerBehavior()` تُعيد
 * إمّا `MIXER_BEHAVIOR_BIT_PERFECT` وإمّا الافتراضيّ. فما يُعرض في الشاشة هو ما قبلته، وما لم يُقرأ
 * يبقى `null` (ADR-07).
 *
 * **وكل واجهة أحدث من `minSdk 29` محروسة بـ`SDK_INT`:** سمات المازج من أندرويد ١٤ (٣٤) — والـjar يُثبت
 * **الوجود** لا **المستوى**، وهذا هو الفرق الذي يُسقط بناءً إن نُسي.
 */
package nd.max.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.SharedHardwareOwnershipStore
import javax.inject.Inject
import javax.inject.Singleton

/** نتيجة القراءة: الأجهزة المُعلَنة والسمة السارية — وكلٌّ `null` حين لا يُقرأ. */
data class AudioMixerSnapshot(
    val deviceId: Int?,
    val deviceName: String?,
    val supported: List<AudioMixerAttribute>,
    val current: AudioMixerAttribute?,
)

@Singleton
class AudioMixerBackend @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {

    /** لقطة واحدة: جهاز المخرج الأوّل، وما يُعلنه من سمات، وما هو ساريٌّ عليه الآن. */
    fun snapshot(context: Context): AudioMixerSnapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val manager = managerOf(context) ?: return null
        val device = firstSink(manager) ?: return null
        val supported = runCatching {
            manager.getSupportedMixerAttributes(device).map(::attributeOf)
        }.getOrNull() ?: return null
        val current = runCatching {
            manager.getPreferredMixerAttributes(mediaAttributes(), device)?.let(::attributeOf)
        }.getOrNull()
        return AudioMixerSnapshot(
            deviceId = device.id,
            deviceName = device.productName?.toString()?.takeIf { it.isNotBlank() },
            supported = supported.distinctBy(::audioMixerSignature),
            current = current,
        )
    }

    /**
     * يطلب سمة مازج على جهاز المخرج — **ثمّ يقرأ `getPreferredMixerAttributes` بعدها**.
     *
     * والقيمة المطلوبة تُبنى **كما أعلنها الجهاز** (المستخدم يختار من المُعلَن)، فلا نُخترع ترميزًا ولا
     * معدّلًا: `AudioFormat.Builder` تُبنى من الحقول الثلاثة التي أعلنها الجهاز نفسه.
     */
    fun request(context: Context, target: AudioMixerAttribute, auditToken: String): AudioKnobVerdict {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return audioKnobNotAttempted(AudioEffectReason.BELOW_API, audioMixerSignature(target))
        }
        val manager = managerOf(context)
            ?: return audioKnobNotAttempted(AudioEffectReason.NO_MANAGER, audioMixerSignature(target))
        val device = firstSink(manager)
            ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, audioMixerSignature(target))
        val desired = audioMixerSignature(target)
        val mixer = runCatching { mixerOf(target) }.getOrNull()
            ?: return audioKnobNotAttempted(AudioEffectReason.NOT_SUPPORTED_BY_DEVICE, desired)
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return audioKnobNotAttempted(AudioEffectReason.STORE_UNCONFIGURED, desired)
        }

        val key = HardwareControlKey.audioMixer(device.id, "preferred")
        val result = runCatching {
            arbiter.submit(
                key = key,
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = desired,
                apply = { value ->
                    runCatching {
                        val built = audioMixerOfSignature(value)?.let(::mixerOf) ?: return@runCatching false
                        manager.setPreferredMixerAttributes(mediaAttributes(), device, built)
                    }.getOrDefault(false)
                },
                read = {
                    runCatching {
                        manager.getPreferredMixerAttributes(mediaAttributes(), device)
                            ?.let { attributeOf(it) }
                            ?.let(::audioMixerSignature)
                    }.getOrNull()
                },
                // والمقارنة متسامحة: حقل لم نطلبه لا يُفشل، والحقل المطلوب يُقارَن بحرفه.
                verify = { expected, actual ->
                    val wanted = audioMixerOfSignature(expected)
                    val got = audioMixerOfSignature(actual)
                    wanted != null && got != null && audioMixerMatches(wanted, got)
                },
            )
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.ARBITER_UNAVAILABLE, desired)

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

    /** يلغي التفضيل فيعود الجهاز إلى ما تختاره المنصّة — **وهو أيضًا كتابةٌ تُقرأ بعدها**. */
    fun clear(context: Context, auditToken: String): AudioKnobVerdict {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return audioKnobNotAttempted(AudioEffectReason.BELOW_API)
        }
        val manager = managerOf(context)
            ?: return audioKnobNotAttempted(AudioEffectReason.NO_MANAGER)
        val device = firstSink(manager)
            ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE)
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return audioKnobNotAttempted(AudioEffectReason.STORE_UNCONFIGURED)
        }
        val key = HardwareControlKey.audioMixer(device.id, "preferred")
        val result = runCatching {
            arbiter.submit(
                key = key,
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = CLEARED,
                apply = {
                    runCatching { manager.clearPreferredMixerAttributes(mediaAttributes(), device) }
                        .getOrDefault(false)
                },
                read = {
                    // «مُلغًى» = المنصّة لا تُعلن تفضيلًا لهذا الجهاز — وهي القراءة، لا نيّتنا.
                    runCatching {
                        val preferred = manager.getPreferredMixerAttributes(mediaAttributes(), device)
                        if (preferred == null) CLEARED else audioMixerSignature(attributeOf(preferred))
                    }.getOrNull()
                },
            )
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.ARBITER_UNAVAILABLE)

        return audioKnobVerdict(
            attempted = true,
            blocked = result.blocked,
            applied = result.applied,
            verified = result.verified,
            expected = CLEARED,
            actual = result.actual,
            error = result.error,
        )
    }

    private fun attributeOf(mixer: AudioMixerAttributes): AudioMixerAttribute = AudioMixerAttribute(
        sampleRateHz = runCatching { mixer.format.sampleRate }.getOrNull()?.takeIf { it > 0 },
        channelCount = runCatching { mixer.format.channelCount }.getOrNull()?.takeIf { it > 0 },
        encodingToken = runCatching { encodingToken(mixer.format.encoding) }.getOrNull(),
        bitPerfect = runCatching { mixer.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            .getOrDefault(false),
    )

    /** `AudioFormat` ← سمتنا — وترميزٌ لا نعرفه يبقى `null` (ولا يُلحق بأقرب شبيه). */
    private fun mixerOf(attribute: AudioMixerAttribute): AudioMixerAttributes {
        val format = AudioFormat.Builder()
            .setEncoding(encodingCode(attribute.encodingToken))
            .setSampleRate(attribute.sampleRateHz ?: DEFAULT_SAMPLE_RATE)
            .setChannelMask(channelMask(attribute.channelCount ?: DEFAULT_CHANNELS))
            .build()
        return AudioMixerAttributes.Builder(format)
            .setMixerBehavior(
                if (attribute.bitPerfect) AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                else AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT,
            )
            .build()
    }

    private fun encodingToken(encoding: Int): String? = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> AUDIO_ENCODING_PCM_8
        AudioFormat.ENCODING_PCM_16BIT -> AUDIO_ENCODING_PCM_16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> AUDIO_ENCODING_PCM_24
        AudioFormat.ENCODING_PCM_32BIT -> AUDIO_ENCODING_PCM_32
        AudioFormat.ENCODING_PCM_FLOAT -> AUDIO_ENCODING_PCM_FLOAT
        else -> null
    }

    private fun encodingCode(token: String?): Int = when (token) {
        AUDIO_ENCODING_PCM_8 -> AudioFormat.ENCODING_PCM_8BIT
        AUDIO_ENCODING_PCM_24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
        AUDIO_ENCODING_PCM_32 -> AudioFormat.ENCODING_PCM_32BIT
        AUDIO_ENCODING_PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
        else -> AudioFormat.ENCODING_PCM_16BIT
    }

    /** قناة المنصّة ← قناع قنوات؛ وما نعرفه ١ و٢، وما عداه يُسقَط إلى الستيريو. */
    private fun channelMask(channels: Int): Int = when (channels) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        else -> AudioFormat.CHANNEL_OUT_STEREO
    }

    private fun mediaAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun firstSink(manager: AudioManager): AudioDeviceInfo? = runCatching {
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.isSink }
    }.getOrNull()

    private fun managerOf(context: Context): AudioManager? =
        runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 48000
        const val DEFAULT_CHANNELS = 2

        /** نصّ «لا تفضيل» — يُخزَّن في المفتاح فيُقرأ بعد الإلغاء كما يُقرأ غيره. */
        const val CLEARED = "cleared"
    }
}
