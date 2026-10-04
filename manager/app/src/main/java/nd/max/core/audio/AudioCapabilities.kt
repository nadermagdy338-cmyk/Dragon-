/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Audio — **ما يُعلنه هذا الجهاز**: النموذج النقيّ وحده، بلا قارئ.
 *
 * **ولماذا ملفٌ صافٍ وقارئ مفصولان (نمط `SensorInventory` و`SensorMonitorUtil`):** القياس على
 * JVM هنا، والقارئ (`AudioDeviceCatalog` · `AudioEffectProbe`) يلمس `AudioManager` و`AudioEffect`
 * فلا يُقاس إلا على جهاز أو مُصرّف. والقاعدة على كل حقل: **ما لم يُقرأ `null`** — لا صفر
 * ولا اسمٌ مُخترع (ADR-07).
 *
 * **وكل رقم في هذا الملف مقروءٌ من مصدر المنصّة لا من ذاكرة:**
 * - رموز الأجهزة: `AudioDeviceInfo.java` (فرع `android16-release`، AOSP).
 * - معرّفات المؤثرات: `AudioEffect.java` (AOSP) — وهي في `audioEffectTypeToken`.
 * ومعهما اختبار CI يقارن الرموز بثوابت المنصّة نفسها، فلا تنحرف صامتةً يومًا.
 */
package nd.max.core.audio

import java.util.Locale

/**
 * جهاز صوتيّ واحد كما أعلنته المنصّة — والأرقام **كما هي** (رمز النوع والمدى والمعدّلات)،
 * والاسم يأتي من العتاد لا منّا. و`productName` قد يغيب فيبقى `null` بلا ملء.
 *
 * **ولا يُقرأ هنا `AudioDeviceInfo.getAddress()` بقصد:** عنوان جهاز بلوتوث معرّفٌ شخصيّ
 * (‏MAC)، ونصّ المالك «عدم عرض بيانات حساسة دون حاجة» — فلا يُقرأ أصلًا ولا يُخزَّن.
 */
data class AudioDeviceDescriptor(
    val id: Int,
    val productName: String?,
    val typeCode: Int,
    val isSink: Boolean,
    val sampleRatesHz: List<Int> = emptyList(),
    val channelCounts: List<Int> = emptyList(),
    val encodings: List<Int> = emptyList(),
)

/**
 * المخرج الأساسيّ كما تُعلنه `AudioManager.PROPERTY_OUTPUT_*` ومعها جردة الأجهزة — وكلٌّ
 * اختياريّ، فالجهاز الذي لا يُعلن معدّل عيّنة يُقال عنه «غير مقروء» ولا يُخترع له `48000`.
 */
data class AudioOutputCapabilities(
    val sampleRateHz: Int?,
    val framesPerBuffer: Int?,
    val devices: List<AudioDeviceDescriptor>,
)

/**
 * خاصيّتا المنصّة نصّيّتان (`getProperty` تُعيد `String`)، فالتحويل إلى رقم ومعناه الحقيقيّ
 * **في هذا الدالّة الصافية** لا في القارئ: نصٌّ ليس رقمًا، أو `"0"`، أو سالبٌ ⇒ `null`.
 *
 * **ولماذا `0` ليس قراءة:** «معدّل عيّنة صفر» لا وجود له في العتاد؛ فالرقم هنا لا يعني قياسًا
 * بل غياب قيمة، وقاعدتنا أن الغياب `null` يُعرض «—» ولا يُعرض `0` (ADR-07). وهذا ما كان
 * يُخفي عطبًا: قارئٌ يُمرّر `toIntOrNull()` وحدها كان يُظهر `0 Hz` على جهاز صامت الخاصيّة.
 *
 * والترتيب **مُطبَّق هنا** أيضًا، فالجهاز الذي تُعيده المنصّة بترتيب مختلف يُعرض بالترتيب نفسه
 * في كل فتح (والاختبار يثبّت ذلك).
 */
fun audioOutputCapabilitiesOf(
    sampleRateText: String?,
    framesPerBufferText: String?,
    devices: List<AudioDeviceDescriptor>,
): AudioOutputCapabilities = AudioOutputCapabilities(
    sampleRateHz = positiveIntOrNull(sampleRateText),
    framesPerBuffer = positiveIntOrNull(framesPerBufferText),
    devices = audioDevicesSorted(devices),
)

/** رقمٌ موجب من نصّ المنصّة — وما عداه لا قيمة له هنا. */
private fun positiveIntOrNull(text: String?): Int? =
    text?.trim()?.toIntOrNull()?.takeIf { it > 0 }

/**
 * رمز نوع الجهاز (`AudioDeviceInfo.TYPE_*`) ← رمز لاتينيّ ثابت، والشاشة تترجمه في مواردها.
 *
 * **والأرقام مقروءة من `AudioDeviceInfo.java` (AOSP، فرع `android16-release`) لا مُقدَّرة:**
 * ١ أذن · ٢ سماعة · ٣ سماعة رأس بميكروفون · ٤ سماعة رأس · ٥ خط تماثليّ · ٦ خط رقميّ ·
 * ٧ بلوتوث مكالمات · ٨ بلوتوث A2DP · ٩ HDMI · ١٠ HDMI ARC · ١١ USB · ١٢ USB إضافيّ ·
 * ١٣ حوض · ١٤ FM · ١٥ ميكروفون · ١٦ موالف FM · ١٧ موالف تلفاز · ١٨ هاتف · ١٩ خط مساعد ·
 * ٢٠ IP · ٢١ BUS · ٢٢ USB رأس · ٢٣ معين سمع · ٢٤ سماعة آمنة · ٢٥ خلط بعيد ·
 * ٢٦ BLE رأس · ٢٧ BLE سماعة · ٢٩ HDMI eARC · ٣٠ بثّ BLE · ٣١ حوض تماثليّ.
 * **و`28` (‏`TYPE_ECHO_REFERENCE`) متروك عمدًا:** مُعلَّم `@hide` ويطلب
 * `CAPTURE_AUDIO_OUTPUT` المُخوَّل، فلا نسمّيه ولا نطلبه.
 *
 * **والمجهول `null`** — لا اسم مخترع لرقم لم نعرفه (القاعدة نفسها في `hdrTypeNames`).
 */
fun audioDeviceKindToken(typeCode: Int): String? = when (typeCode) {
    1 -> "builtin_earpiece"
    2 -> "builtin_speaker"
    3 -> "wired_headset"
    4 -> "wired_headphones"
    5 -> "line_analog"
    6 -> "line_digital"
    7 -> "bluetooth_sco"
    8 -> "bluetooth_a2dp"
    9 -> "hdmi"
    10 -> "hdmi_arc"
    11 -> "usb_device"
    12 -> "usb_accessory"
    13 -> "dock"
    14 -> "fm"
    15 -> "builtin_mic"
    16 -> "fm_tuner"
    17 -> "tv_tuner"
    18 -> "telephony"
    19 -> "aux_line"
    20 -> "ip"
    21 -> "bus"
    22 -> "usb_headset"
    23 -> "hearing_aid"
    24 -> "builtin_speaker_safe"
    25 -> "remote_submix"
    26 -> "ble_headset"
    27 -> "ble_speaker"
    29 -> "hdmi_earc"
    30 -> "ble_broadcast"
    31 -> "dock_analog"
    else -> null
}

/**
 * ترتيب الأجهزة: **المخارج قبل المداخل**، ثم برمز النوع، ثم بالاسم، ثم بالمعرّف.
 *
 * **ولماذا ترتيبٌ صريح:** `getDevices` تُعيد ما شاء ترتيب النظام الداخليّ، فقائمةٌ تُعرض
 * بترتيب مختلف في كل فتح تُقرأ كقائمة مختلفة — والاختبار يثبّت أنها حتميّة ومُوسَمة. والنوع
 * المجهول يُؤخَّر (بعلامة `\uFFFF` أعلى من أي حرف) فلا يتقدّم على ما نعرف اسمه.
 */
fun audioDevicesSorted(devices: List<AudioDeviceDescriptor>): List<AudioDeviceDescriptor> =
    devices.sortedWith(
        compareBy<AudioDeviceDescriptor> { if (it.isSink) 0 else 1 }
            .thenBy { audioDeviceKindToken(it.typeCode) ?: "\uFFFF" }
            .thenBy { it.productName.orEmpty().lowercase(Locale.US) }
            .thenBy { it.id },
    )

/**
 * مؤثّر صوتيّ كما أعلنته المنصّة — **والحقول خمسة، وكلّها أعضاءٌ عامّة في `Descriptor`**،
 * والترتيب واحد في الـjar وفي هذا النوع (مقيس بـ`javap` على `android.jar`: `uuid` · `type` ·
 * `name` · `implementor` · `connectMode`).
 *
 * **و`uuid` معرّف التنفيذ لا معرّف الواجهة** — وهذا فرقٌ حملته هذه الجولة (تكملة ٢٣٠) لأنّ
 * **مؤثّرات المصنّع تُسجَّل بنوعٍ عامّ (`EFFECT_TYPE_NULL`)**، فلا يُعرف Dolby بشاشة `type` أبدًا،
 * وإنما بمعرّف تنفيذه.
 *
 * **و«مُحمَّل مسبقًا» ليس منها — وهذا مقيس لا مُقدَّر:** `AudioEffect.Descriptor` في AOSP
 * يحمل هذه الأعضاء وحدها، وكلمة `preload` لا ترد في `AudioEffect.java` أصلًا (فُحصت بالبحث
 * في المصدر). ⇒ فلا يُعرض عمودٌ لذلك ولا يُكتب مكانه «0» ولا كلمة تُخترع (ADR-07)، وهذا
 * مسجَّل في `SOUND-SCREEN-PLAN` عند `AU-04`.
 */
data class AudioEffectInfo(
    val name: String?,
    val typeUuid: String?,
    val implementor: String?,
    val connectMode: String?,
    /** معرّف **التنفيذ** (`Descriptor.uuid`) — و`null` حين لا يُقرأ، ولا يُخترع مكانه معرّف النوع. */
    val uuid: String? = null,
)

/**
 * معرّف نوع المؤثّر (`AudioEffect.EFFECT_TYPE_*`) ← رمز ثابت. **والمعرّفات مقروءة حرفًا
 * حرفًا من `AudioEffect.java` (AOSP)** — فمعرّفٌ من الذاكرة يبدو صحيحًا ويكون خاطئًا،
 * والقاعدة: لا معرّف بلا مصدر. والمجهول يُحذف ولا يُسمّى بتحسين خاطئ.
 */
fun audioEffectTypeToken(typeUuid: String?): String? = when (typeUuid?.lowercase(Locale.US)) {
    "0bed4300-ddd6-11db-8f34-0002a5d5c51b" -> "equalizer"
    "0634f220-ddd4-11db-a0fc-0002a5d5c51b" -> "bass_boost"
    "37cc2c00-dddd-11db-8577-0002a5d5c51b" -> "virtualizer"
    "47382d60-ddd8-11db-bf3a-0002a5d5c51b" -> "preset_reverb"
    "c2e5d5f0-94bd-4763-9cac-4e234d06839e" -> "env_reverb"
    "7b491460-8d4d-11e0-bd61-0002a5d5c51b" -> "aec"
    "0a8abfe0-654c-11e0-ba26-0002a5d5c51b" -> "agc"
    "58b4b260-8e06-11e0-aa8e-0002a5d5c51b" -> "ns"
    "fe3199be-aed0-413f-87bb-11260eb63cf1" -> "loudness_enhancer"
    "7261676f-6d75-7369-6364-28e2fd3ac39e" -> "dynamics_processing"
    "1411e6d6-aecd-4021-a1cf-a6aceb0d71e5" -> "haptic_generator"
    else -> null
}

/**
 * نمط الوصل كما تُعلنه المنصّة **نصًّا** (`AudioEffect.EFFECT_INSERT` = `"Insert"` و
 * `EFFECT_AUXILIARY` = `"Auxiliary"`) — وما عداهما لا يُسمّى (وحتى لو ظهر غدًا نمطٌ ثالث
 * فسيُقال «غير معروف» بدل أن يُلحق بأحدهما).
 */
fun audioEffectConnectToken(connectMode: String?): String? = when (connectMode?.trim()) {
    "Insert" -> "insert"
    "Auxiliary" -> "auxiliary"
    else -> null
}

/**
 * ترتيب المؤثرات: بالنوع (والمجهول آخرًا)، ثم بالاسم، ثم بالمُنفّذ — ترتيبٌ حتميّ لا يتبع
 * ترتيب ما أعاده النظام، ولا يتغيّر بين فتحين بلا سبب.
 */
fun audioEffectsSorted(effects: List<AudioEffectInfo>): List<AudioEffectInfo> =
    effects.sortedWith(
        compareBy<AudioEffectInfo> { audioEffectTypeToken(it.typeUuid) ?: "\uFFFF" }
            .thenBy { it.name.orEmpty().lowercase(Locale.US) }
            .thenBy { it.implementor.orEmpty().lowercase(Locale.US) },
    )
