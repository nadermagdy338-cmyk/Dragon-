/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * مستويات الدفقات — **القارئ والكاتب الوحيد** (`AU-03`).
 *
 * **ولماذا هنا لا في الشاشة (ADR-11):** الاستوديو يرسل نيّة، وهذا يُمرّرها إلى
 * `HardwareControlArbiter` بمفتاح `HardwareControlKey.audioStream(token)`. فلا كتابة من طبقة الواجهة `ui/`،
 * ولا مقبضٌ له كتابتان — وكتمُ دفق الإشعارات القائم في `AppMonitor` (٢١٤٩–٢١٥١ و٢٤٨٩–٢٤٩٠)
 * يُنقل إلى هذا المسار في مرحلته، فلا يبقى لمستوى واحد كاتبان.
 *
 * **والتحقّق قراءة لا ثقة:** `setStreamVolume` تُعيد `Unit` ولا تقول شيئًا، و«لا تزعج» يمنع
 * كتابة الرنين بلا استثناء. فالمحكِّم يقرأ بعد الكتابة، ويقارن **حرفيًّا**؛ وما لم يُقرأ مطابقًا
 * يُسترجَع ويُقال `failed` — **ولا تُكتب `applied=true` كاذبة** (شرط قبول `AU-03`).
 */
package nd.max.core.audio

import android.content.Context
import android.media.AudioManager
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.SharedHardwareOwnershipStore
import javax.inject.Inject
import javax.inject.Singleton

/** قراءة دفق واحد: الرقم وسقفه وكتمه — وكلّها من المنصّة بالاسم لا بالتخمين. */
data class AudioStreamReading(
    val token: String,
    /** المستوى الحاليّ، أو `null` حين لا قراءة (وهو غير الصفر). */
    val level: Int?,
    /** سقف المنصّة، أو `null` حين لا قراءة — ولا يُخترع سقف. */
    val maxLevel: Int?,
    val muted: Boolean?,
)

@Singleton
class AudioStreamBackend @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {

    /**
     * كل دفقاتنا كما تقرؤها المنصّة. لا استثناء يفلت: دفقٌ ثالث يُسقط القائمة كلها كان سيُخفي
     * الخمسة السليمة معه.
     */
    fun readings(context: Context): List<AudioStreamReading> {
        val manager = managerOf(context) ?: return AudioStreamCatalog.streams.map { unknown(it.token) }
        return AudioStreamCatalog.streams.map { stream ->
            val id = streamId(stream.token)
                ?: return@map unknown(stream.token)
            AudioStreamReading(
                token = stream.token,
                level = runCatching { manager.getStreamVolume(id) }.getOrNull(),
                maxLevel = runCatching { manager.getStreamMaxVolume(id) }.getOrNull(),
                muted = if (stream.supportsMute) {
                    runCatching { manager.isStreamMute(id) }.getOrNull()
                } else {
                    null
                },
            )
        }
    }

    /**
     * يكتب مستوى دفقٍ **عبر المحكِّم** ثم يقرأ الحكم.
     *
     * @param token معرّف الكتابة (نفسه يُعاد في `auditToken`)؛ ورمزٌ ثابت من الشاشة يجعل قفل
     *   المستخدم اليدويّ الشامل يرى الكتابة.
     * @return الحكم، و`null` حين لا `AudioManager` (فلا محاولة أصلًا).
     */
    fun setLevel(
        context: Context,
        token: String,
        targetLevel: Int,
        auditToken: String,
    ): AudioWriteVerdict {
        val stream = AudioStreamCatalog.stream(token)
            ?: return AudioWriteVerdict(AudioWriteOutcome.NOT_ATTEMPTED, "unknown-stream")
        val manager = managerOf(context)
            ?: return AudioWriteVerdict(AudioWriteOutcome.NOT_ATTEMPTED, "no-audio-manager")
        val id = streamId(stream.token)
            ?: return AudioWriteVerdict(AudioWriteOutcome.NOT_ATTEMPTED, "unknown-stream")
        // والمخزن غير المهيّأ **ليس فشلًا ولا نجاحًا**: لا محاولة، والحكم يقول ذلك (ADR-07).
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return AudioWriteVerdict(AudioWriteOutcome.NOT_ATTEMPTED, "control-store-unconfigured")
        }

        val key = HardwareControlKey.audioStream(stream.token)
        val maxLevel = runCatching { manager.getStreamMaxVolume(id) }.getOrNull() ?: return AudioWriteVerdict(
            AudioWriteOutcome.NOT_ATTEMPTED, "max-level-unreadable",
        )
        // ولا نتجاوز السقف المُعلَن: نسبةٌ من واجهة لا تُنتج رقمًا يرفضه الجهاز.
        val target = targetLevel.coerceIn(0, maxLevel)

        val result = runCatching {
            arbiter.submit(
                key = key,
                // والمالك: `GLOBAL_PROFILE` — القيمة إعدادٌ عامّ للمستخدم لا أمرٌ من العقل ولا سلامة.
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = target.toString(),
                apply = { value ->
                    runCatching {
                        manager.setStreamVolume(id, value.toInt(), 0)
                        true
                    }.getOrDefault(false)
                },
                read = { runCatching { manager.getStreamVolume(id) }.getOrNull()?.toString() },
                // والاسترجاع: خط الأساس هو ما كان قبل الكتابة، وهو الذي يقرؤه المحكِّم بنفسه.
                restore = { value ->
                    runCatching {
                        manager.setStreamVolume(id, value.toInt(), 0)
                        true
                    }.getOrDefault(false)
                },
            )
        }.getOrNull() ?: return AudioWriteVerdict(AudioWriteOutcome.NOT_ATTEMPTED, "arbiter-unavailable")

        return audioWriteVerdict(
            attempted = true,
            applied = result.applied,
            verified = result.verified,
            blocked = result.blocked,
            error = result.error,
            liveLevel = result.actual?.toIntOrNull(),
        )
    }

    /** يقرأ مستوى دفق واحد الآن — يُستعمل بعد كتابة أو عند تغيّر جهاز الإخراج. */
    fun levelOf(context: Context, token: String): Int? {
        val manager = managerOf(context) ?: return null
        val id = streamId(token) ?: return null
        return runCatching { manager.getStreamVolume(id) }.getOrNull()
    }

    private fun managerOf(context: Context): AudioManager? =
        runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    private fun unknown(token: String) = AudioStreamReading(token, level = null, maxLevel = null, muted = null)

    /**
     * الرمز ← ثابت المنصّة. **والمجهول يُعاد `null`** فلا يُكتب رقمٌ من عندنا.
     *
     * و`STREAM_RING` و`STREAM_NOTIFICATION` **متطابقان على أندرويد ١٠+**: المنصّة تُوحّدهما منذ
     * إعادة الهيكلة، فالكتابة على أحدهما تُقرأ على الآخر — وهذا يُقاس في CI على جهاز، ويُعرض
     * هنا كتدفّقين لأن المستخدم يعرفهما كذلك (والقراءة هي الحكم، لا افتراضُنا).
     */
    private fun streamId(token: String): Int? = when (token) {
        "media" -> AudioManager.STREAM_MUSIC
        "call" -> AudioManager.STREAM_VOICE_CALL
        "ring" -> AudioManager.STREAM_RING
        "notification" -> AudioManager.STREAM_NOTIFICATION
        "alarm" -> AudioManager.STREAM_ALARM
        "system" -> AudioManager.STREAM_SYSTEM
        else -> null
    }
}
