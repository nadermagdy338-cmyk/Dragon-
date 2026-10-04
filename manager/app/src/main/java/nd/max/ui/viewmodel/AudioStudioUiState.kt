/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * حالة سطح استوديو الصوت — **نقلٌ حرفيٌّ لا تعديل** (تكملة ٢٣٥).
 *
 * **ولماذا انتقلت إلى ملفّها:** `AudioStudioViewModel.kt` بلغ سقف «الملف الضخم» في `code_health`
 * (١٠٠٠ سطر)، وكلّ سطرٍ جديد فيه يفتح مجددًا ما أُغلق عمدًا — والحالةُ صنفٌ مستقلّ بلا منطق،
 * فمكانها الطبيعيّ ملفٌّ مستقلّ، وبقي الـViewModel غلافًا رقيقًا كما يقول تعليقه الأول.
 *
 * والقاعدة الحاكمة كما كانت: **الغياب `null` لا صفر** (ADR-07) — «لا قراءة» تختلف عن «مكتوم»
 * و«صامت»، وكلّ حقلٍ يحمل سببَ غيابه.
 */
package nd.max.ui.viewmodel

import nd.max.core.audio.AudioPresetDiagnostic
import nd.max.core.audio.AudioPresetApplyResult
import nd.max.core.audio.AudioSoundPreset
import nd.max.core.audio.AudioBackendCandidate
import nd.max.core.audio.AudioBackendSelection
import nd.max.core.audio.AudioDeviceDescriptor
import nd.max.core.audio.AudioDynamicsSnapshot
import nd.max.core.audio.AudioEffectKind
import nd.max.core.audio.AudioEqSnapshot
import nd.max.core.audio.AudioFeatureVerdict
import nd.max.core.audio.AudioKnobVerdict
import nd.max.core.audio.AudioMixerSnapshot
import nd.max.core.audio.AudioOutputCapabilities
import nd.max.core.audio.AudioProfileV2
import nd.max.core.audio.AudioRouteSnapshot
import nd.max.core.audio.AudioSpectrumFrame
import nd.max.core.audio.AudioStreamReading
import nd.max.core.audio.AudioStrengthSnapshot
import nd.max.core.audio.AudioSystemSnapshot
import nd.max.core.audio.AudioVolumeReading
import nd.max.core.audio.AudioWriteVerdict
import nd.max.core.audio.DolbyDapParam
import nd.max.core.audio.VendorAttachProbe
import nd.max.core.audio.VendorAudioSnapshot

/**
 * حالة السطح. و**الغياب هنا `null` لا صفر**: لا معدّل عيّنة ⇒ «—»، و«لا قراءة» تختلف عن «مكتوم»
 * و«صامت» (ADR-07).
 */
data class AudioStudioUiState(
    val loading: Boolean = true,
    val soundPreset: AudioSoundPreset = AudioSoundPreset.OFF,
    val presetIntensity: Int = 50,
    val presetDiagnostics: List<AudioPresetDiagnostic>? = null,
    val presetDiagnosticBusy: Boolean = false,
    val presetBusy: Boolean = false,
    val comparingOriginal: Boolean = false,
    val presetResult: AudioPresetApplyResult? = null,
    val output: AudioOutputCapabilities? = null,
    /** أوّل مخرج تُعلنه المنصّة — والقائمة مرتَّبة في الطبقة الصافية (مخارج أوّلًا)، فلا حكم ذوق. */
    val activeDevice: AudioDeviceDescriptor? = null,
    val readings: List<AudioStreamReading> = emptyList(),
    /** حكم آخر كتابة: يُعرض سطرًا واحدًا ثم يُنسى — ولا يبقى ادّعاء بلا قراءة. */
    val lastVerdict: AudioWriteVerdict? = null,
    val lastWrittenToken: String? = null,
    /**
     * حالة كل ميزة وأسبابها — تُقاس عند كل قراءة (`AQ-01`)، و`null` تعني «لم تُقس بعد»
     * فتُعرض «قيد القراءة» ولا تُعرض مصفوفة فارغة تُقرأ «لا ميزات». والقياس في `core/audio/`
     * وحده، فلا استعمال لـ`AudioEffect` من طبقة الواجهة (ADR-11).
     */
    val capability: List<AudioFeatureVerdict>? = null,
    /** المعادل: النطاقات والأنماط كما قرأها المحرّك — و`null` يعني «لا جلسة معادل». */
    val eq: AudioEqSnapshot? = null,
    /** الديناميكيّ: كل معامليه كما تُعيدها `getConfig()` بعد الكتابة. */
    val dynamics: AudioDynamicsSnapshot? = null,
    /** المؤثّرات البسيطة: قوّة كل مؤثّر مفتوح — وما ليس له جلسة لا سطر له. */
    val strengths: Map<AudioEffectKind, AudioStrengthSnapshot> = emptyMap(),
    /** تمكين كل مؤثّر مفتوح — يُقرأ بعد الكتابة. */
    val enabled: Map<AudioEffectKind, Boolean?> = emptyMap(),
    /** سبب تعذّر فتح مؤثّر — يُعرض بجانب قسمه، فلا قسم صامت. */
    val engineReasons: Map<AudioEffectKind, String> = emptyMap(),
    /** حكم آخر مقبض (معادل/ديناميكيّ/قوّة/مازج/توجيه) — وسببُه مكتوب. */
    val knobVerdict: AudioKnobVerdict? = null,
    val knobTarget: String? = null,
    val mixer: AudioMixerSnapshot? = null,
    val route: AudioRouteSnapshot? = null,
    val volumes: List<AudioVolumeReading> = emptyList(),
    /** هل أُذن التسجيل؟ — يُفعَل به قسم الطيف ويُقال سببه، ولا يُطلب من الشاشة تلقائيًّا. */
    val spectrumPermission: Boolean = false,
    val spectrumRunning: Boolean = false,
    val spectrum: AudioSpectrumFrame? = null,
    /**
     * طبقة مؤثّرات النظام كما قِيست الآن (`AQ-09`) — و`null` تعني «لم تُقس بعد» فلا تُعرض حالة
     * مصنوعة، وحكمها منفصل عن حكم المحرّك ([systemVerdict]).
     */
    val system: AudioSystemSnapshot? = null,
    /** حكم آخر محاولة تثبيت/إلغاء للطبقة — وسببُه مكتوب كما يُقرأ من المحكِّم. */
    val systemVerdict: AudioKnobVerdict? = null,
    val profiles: List<AudioProfileV2> = emptyList(),
    /** مدخلات أُسقطت من آخر بصمة لعدم توفّر مؤثّرها — تُقال ولا تُخفى. */
    val profilesDropped: List<String> = emptyList(),
    /** نصّ التصدير جاهز للتنسخ — تُفرّغه الشاشة بعد نسخه، فلا يبقى في الحالة. */
    val profilesExport: String? = null,
    /**
     * اكتشاف مؤثّر المصنّع (Dolby) كما قِيس — و`null` تعني «لم يُقس بعد»، فلا تُعرض حالة مصنوعة.
     *
     * **ويفصل عن [vendorVerdict]**: هذه الجردة والحكم، وتلك أثر محاولة اللمس وحدها — والخَلط بينهما
     * كان سيُظهر «موجود» ثمّ «غير متاح» في سطر واحد بلا سبب يفصل بينهما.
     */
    val vendor: VendorAudioSnapshot? = null,
    /** أثر محاولة اللمس المقيسة ([`VendorAudioDiscovery.probeAttach`]) — و`null` = لم تُجرَّب. */
    val vendorProbe: VendorAttachProbe? = null,
    /**
     * سلّم المحرّكات لهذا الجهاز ([`audioBackendLadder`]) — و`null` تعني «لم يُقس».
     *
     * **ويُبنى في الطبقة الصافية لا في الشاشة**: الترتيب والحالات قاعدةٌ تُقاس على JVM. وما في
     * الشاشة إلّا العرض. و[backend] قرار الاختيار المشتقّ منه، فلا يُعاد حسابه في الرسم.
     */
    val ladder: List<AudioBackendCandidate>? = null,
    /** المحرّك الذي يقود وبدائله — يُقرأ من السلّم ولا يُختار في الشاشة. */
    val backend: AudioBackendSelection? = null,
    /** هل جلسة مؤثّر المصنّع مفتوحة للكتابة؟ — والكتابة تحتاج جلسةً نملكها، فلا تُدّعى بلا فتح. */
    val vendorOpen: Boolean = false,
    /** حالة تمكين معالج Dolby كما قرأتها المادّة — و`null` لا تعني صفرًا. */
    val vendorDapEnabled: Int? = null,
    /** رقم الملفّ الشخصيّ المقروء — و`null` حين لا يُقرأ. */
    val vendorProfile: Int? = null,
    /** حالة تمكين المؤثّر نفسه (`AudioEffect.setEnabled`) — مقبضٌ ثانٍ غير تمكين المعالج. */
    val vendorEffectEnabled: Boolean? = null,
    /** آخر حكم مقبض لمؤثّر المصنّع — وسببُه مكتوب كما يُقرأ من المحكِّم. */
    val vendorKnob: AudioKnobVerdict? = null,
    /** قيم معاملات المصنّع كما قُرئت بعد الكتابة — مفتاحها المعامل، فلا قيمة بلا قراءة. */
    val vendorValues: Map<DolbyDapParam, IntArray> = emptyMap(),
    /**
     * قيم خصائص MaxFx كما قُرئت (`persist.audio.maxfx.<key>`) — ومفتاحها مفتاح العقد
     * (`maxfx_params.tsv`)، وغياب المفتاح يعني «لم تُكتب بعد» فيُعرض افتراض العقد لا صفر (ADR-07).
     */
    val maxFxValues: Map<String, String> = emptyMap(),
    /** آخر حكم كتابة في MaxFx — وسببُه مكتوب؛ ومُنفصل عن [knobVerdict] بالقاعدة نفسها التي فصلت [vendorKnob]. */
    val maxFxVerdict: AudioKnobVerdict? = null,
)
