/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * الصوت — **التوجيه والجهارة المقيسة بالديسيبل** (`AQ-06`).
 *
 * **وثلاثة أشياء حقيقيّة هنا، كلّها عامّة في المنصّة:**
 *
 * ① **جهاز صوت المكالمة** — `getAvailableCommunicationDevices` ثمّ `setCommunicationDevice` ثمّ
 *    `getCommunicationDevice` بعد الكتابة. وهذا تحكّمٌ **لم يكن في الشاشة قبل هذه الموجة**، ومعياره
 *    قراءة ما صار لا ثقة بما طُلب.
 * ② **الديسيبل** — `getStreamVolumeDb` تُعيد الجهارة **كوحدة صوت** لا كدرجة؛ وهي القياس الصادق، لأنّ
 *    الدرجة ٧ من ١٥ على جهاز ليست الدرجة ٧ على آخر. **وحدّها المقيس:** المنصّة تُعيد `-Infinity` حين
 *    يكون الدفق مكتومًا/صامتًا — وليست قيمة تُعرض، فتصير `null` ويُعرض `—` (وهو ما يفعله
 *    [`audioDbFormat`]).
 * ③ **مجموعة الجهارة** — `getVolumeGroupIdForAttributes` و`isVolumeGroupMuted`: المنصّة توحّد الدفقات
 *    المتساوية في مجموعة واحدة على أندرويد ١١+. **ولا نُفترض التوحيد:** نقارن ما تقوله المجموعة بما
 *    يقوله الدفق، ونعرض ما قالته المنصّة — فقد يُوحّد هذا الـROM الرنين والإشعار، وقد لا يُوحّد.
 *
 * **ولا كتابة جديدة للمستويات هنا:** تلك تبقى في [`AudioStreamBackend`] وحدها، فلا يبقى لمستوى واحد
 * كاتبان — وهو الشرط الذي بُني عليه `AudioStreamBackend` من أوّله.
 */
package nd.max.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.SharedHardwareOwnershipStore
import javax.inject.Inject
import javax.inject.Singleton

/** جهاز توجيه كما يوصفه سياق التطبيق — و`typeToken` من `audioDeviceKindToken` (لا نسخة ثانية). */
data class AudioRouteDevice(
    val id: Int,
    val typeToken: String?,
    val productName: String?,
)

/** لقطة التوجيه: أجهزة المكالمة المتاحة، والسارية منها. */
data class AudioRouteSnapshot(
    val available: List<AudioRouteDevice>,
    val active: AudioRouteDevice?,
    val supported: Boolean,
)

/** جهارة دفق بالديسيبل/الدرجة/المجموعة — وكلٌّ `null` حين لا يُقرأ. */
data class AudioVolumeReading(
    val token: String,
    val db: Float?,
    val groupId: Int?,
    val groupMuted: Boolean?,
)

@Singleton
class AudioRoutingBackend @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {

    /** لقطة التوجيه — و`supported=false` على إصدار أقدم من ١٢ بمعنى «المنصّة قاطعت» لا «لم تُقرأ». */
    fun snapshot(context: Context): AudioRouteSnapshot {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return AudioRouteSnapshot(available = emptyList(), active = null, supported = false)
        }
        val manager = managerOf(context) ?: return AudioRouteSnapshot(emptyList(), null, supported = false)
        val available = runCatching {
            manager.availableCommunicationDevices.map(::deviceOf)
        }.getOrNull() ?: return AudioRouteSnapshot(emptyList(), null, supported = false)
        val active = runCatching { manager.communicationDevice?.let(::deviceOf) }.getOrNull()
        return AudioRouteSnapshot(available = available, active = active, supported = true)
    }

    /**
     * يوجّه صوت المكالمة إلى جهاز — ثمّ يقرأ `getCommunicationDevice` بعده.
     *
     * **والحكم يُبنى على معرّف الجهاز لا على اسمه:** الأسماء قد تتكرّر (سمّاعتان بنفس الاسم)، والمعرّف
     * هو ما تعرفه المنصّة — ولذلك يُقارَن الرقم نصًّا في المحكِّم.
     */
    fun route(context: Context, target: AudioRouteDevice, auditToken: String): AudioKnobVerdict {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return audioKnobNotAttempted(AudioEffectReason.BELOW_API, target.id.toString())
        }
        val manager = managerOf(context)
            ?: return audioKnobNotAttempted(AudioEffectReason.NO_MANAGER, target.id.toString())
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return audioKnobNotAttempted(AudioEffectReason.STORE_UNCONFIGURED, target.id.toString())
        }
        val device = runCatching {
            manager.availableCommunicationDevices.firstOrNull { it.id == target.id }
        }.getOrNull() ?: return audioKnobNotAttempted(AudioEffectReason.UNKNOWN_ROUTE, target.id.toString())

        val desired = target.id.toString()
        val key = HardwareControlKey.audioRoute("communication")
        val result = runCatching {
            arbiter.submit(
                key = key,
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = desired,
                apply = { runCatching { manager.setCommunicationDevice(device) }.getOrDefault(false) },
                read = { runCatching { manager.communicationDevice?.id?.toString() }.getOrNull() },
                restore = { value ->
                    runCatching {
                        val previous = manager.availableCommunicationDevices
                            .firstOrNull { it.id == value.toIntOrNull() }
                        if (previous != null) manager.setCommunicationDevice(previous)
                        else manager.clearCommunicationDevice().let { true }
                    }.getOrDefault(false)
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

    /** يُلغي التوجيه فيعود الصوت إلى مسار المنصّة الافتراضيّ. */
    fun clearRoute(context: Context, auditToken: String): AudioKnobVerdict {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return audioKnobNotAttempted(AudioEffectReason.BELOW_API)
        }
        val manager = managerOf(context)
            ?: return audioKnobNotAttempted(AudioEffectReason.NO_MANAGER)
        if (!SharedHardwareOwnershipStore.isConfigured()) {
            return audioKnobNotAttempted(AudioEffectReason.STORE_UNCONFIGURED)
        }
        val key = HardwareControlKey.audioRoute("communication")
        val result = runCatching {
            arbiter.submit(
                key = key,
                owner = ControlOwnership.Owner.GLOBAL_PROFILE,
                token = auditToken,
                desired = CLEARED,
                apply = { runCatching { manager.clearCommunicationDevice(); true }.getOrDefault(false) },
                read = {
                    runCatching {
                        if (manager.communicationDevice == null) CLEARED else null
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

    /**
     * جهارة كل دفق بالديسيبل وبالمجموعة — **قراءة فقط**، فلا كتابة هنا ولو شاءت الشاشة.
     *
     * و`AudioStreamCatalog.streams.map` يجعل الترتيب واحدًا بين القارئين، فتُقارَن القراءتان سطرًا بسطر.
     */
    fun volumes(context: Context): List<AudioVolumeReading> {
        val manager = managerOf(context) ?: return emptyList()
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        val groupId = supportedGroupId(manager, media)
        return AudioStreamCatalog.streams.map { stream ->
            val id = streamType(stream.token)
            AudioVolumeReading(
                token = stream.token,
                // والديسيبل يُقاس على جهاز المخرج الافتراضيّ (٠ = افتراضيّ المنصّة).
                db = id?.let {
                    runCatching { manager.getStreamVolumeDb(it, manager.getStreamVolume(it), DEFAULT_DEVICE) }
                        .getOrNull()
                        ?.takeIf { value -> value.isFinite() }
                },
                groupId = groupId,
                groupMuted = groupId?.let { runCatching { manager.isVolumeGroupMuted(it) }.getOrNull() },
            )
        }
    }

    private fun supportedGroupId(manager: AudioManager, attributes: AudioAttributes): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching { manager.getVolumeGroupIdForAttributes(attributes) }.getOrNull()?.takeIf { it >= 0 }
    }

    private fun deviceOf(device: AudioDeviceInfo): AudioRouteDevice = AudioRouteDevice(
        id = device.id,
        typeToken = audioDeviceKindToken(device.type),
        productName = runCatching { device.productName?.toString() }.getOrNull()?.takeIf { it.isNotBlank() },
    )

    private fun managerOf(context: Context): AudioManager? =
        runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    /** رمز الدفق ← ثابت المنصّة — نسخةٌ واحدة مع [`AudioStreamBackend`] لا تُنسخ حرفيًّا هنا. */
    private fun streamType(token: String): Int? = when (token) {
        "media" -> AudioManager.STREAM_MUSIC
        "call" -> AudioManager.STREAM_VOICE_CALL
        "ring" -> AudioManager.STREAM_RING
        "notification" -> AudioManager.STREAM_NOTIFICATION
        "alarm" -> AudioManager.STREAM_ALARM
        "system" -> AudioManager.STREAM_SYSTEM
        else -> null
    }

    private companion object {
        /** `AudioManager` يعرّف `DEVICE_TYPE_DEFAULT` رقمًا ثابتًا؛ نُمرّره كما هو لا باسمٍ من عندنا. */
        const val DEFAULT_DEVICE = 0
        const val CLEARED = "cleared"
    }
}
