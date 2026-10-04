/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * قسم الصوت في «معلومات الجهاز» (`AS-01`) — **نقلٌ لا إعادة كتابة**.
 *
 * **والسبب أمر المالك بالنصّ:** «انقل ما صنعته إلى `device info` لكي لا يضيع الجهد». فما كان جسمًا
 * مؤقّتًا في حوز الصوت (جردة ما يُعلنه الجهاز: الأجهزة والمعدّل والمؤثرات) صار **قسمًا هنا**، لأن
 * هذه الشاشة هي **أطلس الحقائق** في هذا المستودع — وحوز الصوت صار سطح تحكّم.
 *
 * **ومصدرُه واحد:** `AudioInventory` نفسه (لقطة واحدة لا يفلت منها استثناء). فلا قارئ ثانٍ، ولا
 * رقم مصنوع: قائمةٌ فارغة **قراءة** («لا مؤثرات مُعلَنة»)، و`null` **غياب قراءة** («غير مقروء»).
 *
 * **وجدولا الرموز هنا لا في الشاشة:** `audioDeviceKindToken`/`audioEffectTypeToken` (الطبقة الصافية)
 * تُترجم إلى نصوص الموارد هنا، لأن النموذج لا يرسم. وهما يغطّيان **٣٠ رمز جهاز و١١ رمز مؤثّر**
 * كاملة، فلا يبقى مفتاح بلا مستهلك.
 */
package nd.max.ui.subscreens

import nd.max.R
import nd.max.core.audio.AudioDeviceDescriptor
import nd.max.core.audio.AudioEffectInfo
import nd.max.core.audio.audioDeviceKindToken
import nd.max.core.audio.audioEffectConnectToken
import nd.max.core.audio.audioEffectTypeToken

/** من أين قُرئت الحقول: المنصّة بالاسم، لا تخمين. */
internal const val AUDIO_SERVICE = "AudioManager"

/** حقول قسم الصوت — ولا صفوف: كل ما يُعلنه الجهاز صار حقلًا باسم نوعه. */
internal fun audioFacts(s: DeviceInfoSnapshot): List<DeviceInfoFact> {
    val audio = s.audio
    val output = audio?.output
    return listOf(
        DeviceInfoFact(
            label = R.string.max_audio_sample_rate_caption,
            value = output?.sampleRateHz?.toString(),
            unit = output?.sampleRateHz?.let { "Hz" },
            trust = if (output?.sampleRateHz == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
            source = AUDIO_SERVICE,
        ),
        DeviceInfoFact(
            label = R.string.max_audio_frames_caption,
            value = output?.framesPerBuffer?.toString(),
            trust = if (output?.framesPerBuffer == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
            source = AUDIO_SERVICE,
        ),
        countFact(
            label = R.string.max_audio_devices_title,
            count = audio?.devices?.size,
            unreadableRes = R.string.max_audio_devices_unreadable,
        ),
        countFact(
            label = R.string.max_audio_effects_title,
            count = audio?.effects?.size,
            unreadableRes = R.string.max_audio_effects_unreadable,
        ),
    ) + deviceFacts(audio?.devices) + effectFacts(audio?.effects)
}

/**
 * العدّ **قراءةً لا ادّعاء**: الصفر رقم حقيقيّ («لا مؤثرات مُعلَنة»)، و`null` غياب قراءة — والاثنان
 * لا يتبادلان (وهذا نصّ شرط قبول `AU-04`).
 */
private fun countFact(label: Int, count: Int?, unreadableRes: Int): DeviceInfoFact = DeviceInfoFact(
    label = label,
    value = count?.toString(),
    trust = if (count == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live,
    source = AUDIO_SERVICE,
    noteRes = if (count == null) unreadableRes else null,
)

/**
 * جهازٌ لكل حقل: **عنوانه نوعُه المُعلَن** (مترجَم)، وقيمته اسمُه من المنصّة وما أعلنه من قنوات
 * ومعدّلات — فلا يُخترع وصف، ولا تُطبع رموز خام.
 */
private fun deviceFacts(devices: List<AudioDeviceDescriptor>?): List<DeviceInfoFact> =
    devices.orEmpty().map { device ->
        val kind = audioDeviceKindToken(device.typeCode)
        DeviceInfoFact(
            // والنوع المجهول يُعرض برقمه لا باسم مُخترع — والجدول يغطّي الثلاثين، فالفرع الأخير
            // لا يُبلَغ إلا بنوعٍ أضافته منصّةٌ بعدنا.
            label = kind?.let(::audioDeviceKindLabel) ?: R.string.max_audio_type_unknown_short,
            value = deviceValue(device, kind),
            trust = DeviceInfoTrust.Snapshot,
            source = AUDIO_SERVICE,
        )
    }

private fun deviceValue(device: AudioDeviceDescriptor, kind: String?): String {
    val name = device.productName
    val numbers = buildList {
        device.channelCounts.firstOrNull()?.let { add("${it}ch") }
        device.sampleRatesHz.firstOrNull()?.let { add("${it}Hz") }
    }
    return listOfNotNull(name, kind?.takeIf { name == null }, numbers.takeIf { it.isNotEmpty() }?.joinToString(" "))
        .joinToString(" · ")
        .ifBlank { kind.orEmpty() }
}

/**
 * مؤثّرٌ لكل حقل: عنوانه نوعُه (مترجَم)، وقيمته اسمُه والمُنفّذ كما أعلنتها المنصّة.
 *
 * **ونمط الوصل مفصولٌ عن القيمة بقصد:** المنصّة تكتبه بالإنجليزية دائمًا (`Insert` · `Auxiliary`)،
 * فطبعه مع الاسم كان يُدخل كلمةً إنجليزية في واجهة عربية. فيُقرأ بـ`audioEffectConnectToken`
 * (الطبقة الصافية) ويُترجم في الموارد — ونمطٌ ثالث لا نعرفه **يُترك بلا سطر** ولا يُخمَّن له اسم.
 */
private fun effectFacts(effects: List<AudioEffectInfo>?): List<DeviceInfoFact> =
    effects.orEmpty().map { effect ->
        val kind = audioEffectTypeToken(effect.typeUuid)
        DeviceInfoFact(
            label = kind?.let(::audioEffectTypeLabel) ?: R.string.max_audio_effect_unknown,
            value = listOfNotNull(effect.name, effect.implementor)
                .joinToString(" · ")
                .ifBlank { null },
            trust = DeviceInfoTrust.Snapshot,
            source = AUDIO_SERVICE,
            noteRes = audioEffectConnectLabel(effect.connectMode),
        )
    }

/** رمز نمط الوصل ← نصّه في الموارد؛ والمنصّة التي تكتب غير ما نعرفه لا تُنسب إلى اسم مخترع. */
private fun audioEffectConnectLabel(connectMode: String?): Int? =
    when (audioEffectConnectToken(connectMode)) {
        "insert" -> R.string.max_audio_connect_insert
        "auxiliary" -> R.string.max_audio_connect_auxiliary
        else -> null
    }

/**
 * رمز نوع الجهاز ← نصّه في الموارد. **وكل رمز في `audioDeviceKindToken` له سطر هنا** — والفرع
 * الأخير لا يُبلَغ بالرموز الثلاثين، ويبقى ليُقرأ الخطأ لا ليُسكَت عنه.
 */
internal fun audioDeviceKindLabel(token: String): Int = when (token) {
    "builtin_earpiece" -> R.string.max_audio_type_builtin_earpiece
    "builtin_speaker" -> R.string.max_audio_type_builtin_speaker
    "wired_headset" -> R.string.max_audio_type_wired_headset
    "wired_headphones" -> R.string.max_audio_type_wired_headphones
    "line_analog" -> R.string.max_audio_type_line_analog
    "line_digital" -> R.string.max_audio_type_line_digital
    "bluetooth_sco" -> R.string.max_audio_type_bluetooth_sco
    "bluetooth_a2dp" -> R.string.max_audio_type_bluetooth_a2dp
    "hdmi" -> R.string.max_audio_type_hdmi
    "hdmi_arc" -> R.string.max_audio_type_hdmi_arc
    "usb_device" -> R.string.max_audio_type_usb_device
    "usb_accessory" -> R.string.max_audio_type_usb_accessory
    "dock" -> R.string.max_audio_type_dock
    "fm" -> R.string.max_audio_type_fm
    "builtin_mic" -> R.string.max_audio_type_builtin_mic
    "fm_tuner" -> R.string.max_audio_type_fm_tuner
    "tv_tuner" -> R.string.max_audio_type_tv_tuner
    "telephony" -> R.string.max_audio_type_telephony
    "aux_line" -> R.string.max_audio_type_aux_line
    "ip" -> R.string.max_audio_type_ip
    "bus" -> R.string.max_audio_type_bus
    "usb_headset" -> R.string.max_audio_type_usb_headset
    "hearing_aid" -> R.string.max_audio_type_hearing_aid
    "builtin_speaker_safe" -> R.string.max_audio_type_builtin_speaker_safe
    "remote_submix" -> R.string.max_audio_type_remote_submix
    "ble_headset" -> R.string.max_audio_type_ble_headset
    "ble_speaker" -> R.string.max_audio_type_ble_speaker
    "hdmi_earc" -> R.string.max_audio_type_hdmi_earc
    "ble_broadcast" -> R.string.max_audio_type_ble_broadcast
    "dock_analog" -> R.string.max_audio_type_dock_analog
    else -> R.string.max_audio_type_unknown_short
}

/** رمز نوع المؤثّر ← نصّه في الموارد (الجدول نفسه، للسبب نفسه). */
internal fun audioEffectTypeLabel(token: String): Int = when (token) {
    "equalizer" -> R.string.max_audio_effect_equalizer
    "bass_boost" -> R.string.max_audio_effect_bass_boost
    "virtualizer" -> R.string.max_audio_effect_virtualizer
    "preset_reverb" -> R.string.max_audio_effect_preset_reverb
    "env_reverb" -> R.string.max_audio_effect_env_reverb
    "aec" -> R.string.max_audio_effect_aec
    "agc" -> R.string.max_audio_effect_agc
    "ns" -> R.string.max_audio_effect_ns
    "loudness_enhancer" -> R.string.max_audio_effect_loudness_enhancer
    "dynamics_processing" -> R.string.max_audio_effect_dynamics_processing
    "haptic_generator" -> R.string.max_audio_effect_haptic_generator
    else -> R.string.max_audio_effect_unknown
}
