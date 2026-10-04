# AUDIO-ADVANCED-PLAN — من «مستويات» إلى مركز تحكّم صوتيّ متقدّم (مقيس، لا موعود)

> **حالة الوثيقة:** خطّة تنفيذ — **تُقدَّم قبل الكود** كما اشترط المالك بالنصّ: «قبل البدء في كتابة
> الكود، افحص تنفيذ شاشة الصوت الحالي وبنية المشروع بالكامل، وحدد ما هي إمكانيات الصوت الحقيقية».
> **وترتيبها في الأسرة الوثائقية:** هذه **توسّع** `SOUND-SCREEN-PLAN` (المراحل `AU-01…AU-08`) و
> `AUDIO-STUDIO-PLAN` (المراحل `AS-01…AS-06`) ولا تُبطل شيئًا فيهما — بل تُبدّل **ما أُنجز فعلًا**
> بـ**سطح تحكّم حقيقيّ**، وتُضيف الطبقات التي لم تكن في الخطتين (`DynamicsProcessing` · المزج ·
> التوجيه · المحلّل الطيفيّ).

---

## 0. أمر المالك — وماذا يعني بالضبط

نصّه: «التنفيذ الحالي يضيف فقط خيارات الصوت الأساسية الموجودة أصلًا في إعدادات Android، وهذا ليس ما
أريده… أريد أن تتحوّل الشاشة إلى **نظام تحكّم صوتي متقدّم وحقيقي**… لا أريد فقط إضافة المزيد من
المفاتيح وخيارات On/Off… **مهم جدًا:** اتبع نفس فلسفة الأمان والتحقّق الموجودة في بقية التطبيق. لا تعرض
أي خيار على أنه يعمل إذا كان مجرد واجهة بدون Backend حقيقي.»

**والتصحيح الذي يستوجبه الصدق:** شكوى المالك **صحيحة مقيسةً**. الشاشة اليوم (٣٥٩ سطرًا + الـViewModel)
تعرض: بطاقة محرّك بأربع قراءات، وستة أشرطة مستويات دفقات، وأربعة صفوف تشخيص **نصّية ثابتة**، وبابًا إلى
«معلومات الجهاز». وهذا **حرفيًّا** «خيارات الصوت الأساسية + On/Off» — لا معادل، ولا مؤثّر، ولا ديناميكيّ.

**وقاعدتان تسريان على كل سطر في هذه الخطّة:**

1. **كل مقبض يُعرض = كتابة حقيقيّة تُقرأ بعدها** (نمط `AudioStreamBackend` القائم: `setStreamVolume`
   تُعيد `Unit`، فالحكم قراءةٌ لا ادّعاء).
2. **كل ما لا يُكتب يُسمّى بسببٍ مكتوب** — لا يُعرض مُعطَّلًا ليملأ السطح (ADR-07 · قاعدة `maxHubRows`).

---

## 1. ما تفعله الشاشة اليوم — مقيس، لا موصوف

| الموضع | المقيس | الحكم |
| --- | --- | --- |
| `ui/subscreens/audio/AudioStudioScreen.kt` | ٣٥٩ سطرًا · تبويبان `LIVE`/`SYSTEM` | هيكل، لا محرّك |
| بطاقة المحرّك | `sampleRateHz` · `framesPerBuffer` · الجهاز النشط · (`audioWriteVerdict`) | **قراءة** صحيحة، لا تحكّم |
| `LIVE` | ستة أشرطة: `media` `call` `ring` `notification` `alarm` `system` | **الكتابة الوحيدة الحقيقيّة اليوم** |
| `SYSTEM` | أربعة صفوف نصّ ثابتة (`diagnosticRows`) | **نصٌّ لا قياس**: تُحكي الحدود ولا تُقاس على الجهاز |
| `core/audio/` | ٧ ملفات: `AudioCapabilities` · `AudioDeviceCatalog` · `AudioEffectProbe` · `AudioInventory` · `AudioStreamBackend` · `AudioStreamCatalog` · `AudioStreamVerdict` | بنية سليمة تُعاد الاستفادة منها |
| مؤثّرات (`AudioEffect`/`Equalizer`/`DynamicsProcessing`) في كل الشجرة | **صفر استعمال** — `AudioEffectProbe` يقرأ `queryEffects()` **وصفًا** فقط | لا محرّك مؤثّرات إطلاقًا |
| أذونات الصوت في `AndroidManifest.xml` | **لا** `MODIFY_AUDIO_SETTINGS` · **لا** `RECORD_AUDIO` (مقيس بالبحث) | يجب إعلان ما نحتاجه فعلًا لا أكثر |

⇒ **البنية موجودة، والمحرّك غائب.** هذه الخطّة تبني المحرّك فوق البنية القائمة، لا بجانبها.

---

## 2. سطح المنصّة الحقيقيّ — **مقيس بـ`javap` على `android.jar` الذي يُصرَّف عليه المشروع**

**كيف قِيس:** كل ما في الجدول أدناه مُستخرَج من `android.jar` نفسه (المخزون الذي يبنيه المشروع
عليه، `compileSdk 37`) بأداة `javap` — **لا من ذاكرة ولا من مدوّنة**. وما لم يوجد في الـjar **يُقال
إنه غير موجود**.

### 2.1 أصناف المؤثّرات الموجودة فعلًا (وهذا كلّها)

`AudioEffect` · `Equalizer` · `BassBoost` · `Virtualizer` · `LoudnessEnhancer` · `PresetReverb` ·
`EnvironmentalReverb` · **`DynamicsProcessing`** · `HapticGenerator` · `Visualizer` ·
`AcousticEchoCanceler` · `AutomaticGainControl` · `NoiseSuppressor`.

**وغائب منها — وهذا هو جوهر الصدق:** **لا صنف «Convolver» ولا «Impulse Response» ولا «Parametric EQ»
باسمها.** ⇒ الالتفاف و«الاستجابة النبضيّة» **لا مسار عامًّا لهما**، و«المعادل البارامتريّ» يُبنى من
`DynamicsProcessing` بحدوده (عتبة قطع + كسب لكل نطاق، **بلا `Q`**) أو لا يُبنى.

### 2.2 `DynamicsProcessing` — المحرّك الحقيقيّ (API 28+)، وواجهته كاملة

| المكوّن | المقيس في الـjar | ما يعنيه عندنا |
| --- | --- | --- |
| `getConfig()` | `Config` كامل: `getPreEqBandCount` · `getMbcBandCount` · `getPostEqBandCount` · `isPreEqInUse/isMbcInUse/isLimiterInUse` · `getInputGainByChannelIndex` · `getVariant` · `getPreferredFrameDuration` | **قراءة ما صار فعلًا** — أساس التحقّق لا الادّعاء |
| `EqBand` | `setGain` · `isEnabled` · **`BandBase.setCutoffFrequency`** | معادل متعدّد النطاقات بعتبات قابلة للضبط (pre/post) |
| `MbcBand` | `threshold` · `ratio` · `attackTime` · `releaseTime` · `kneeWidth` · `noiseGateThreshold` · `expanderRatio` · `preGain` · `postGain` | **ضاغط متعدّد النطاقات حقيقيّ** بكل معامله |
| `Limiter` | `threshold` · `ratio` · `attackTime` · `releaseTime` · `postGain` · `linkGroup` | **مُحدِّد حقيقيّ** |
| `setInputGainbyChannel(int, float)` / `setInputGainAllChannelsTo(float)` | عامّة | **توازن القنوات وضبط الدخل** — واجهة عامّة واحدة لهذا الغرض |
| الباني | `DynamicsProcessing(int channelCount)` لتكوين `Config`، ثمّ `(priority, session, config)` للتطبيق | بناء مُهيّأ قبل الإرفاق |

### 2.3 `AudioManager` — ما هو عامّ في API 37 (وما يعنيه)

| الواجهة (مقيسة في الـjar) | ما تُعطينا | حكمنا |
| --- | --- | --- |
| `getStreamVolume` · `setStreamVolume` · `getStreamMaxVolume` · **`getStreamMinVolume`** · **`getStreamVolumeDb`** | مستوى كل دفق + حدّه الأدنى + **قيمته بالديسيبل** | الكتابة القائمة + **عرض صادق بالديسيبل** |
| **`getVolumeGroupIdForAttributes`** · **`adjustVolumeGroupVolume`** · **`isVolumeGroupMuted`** | نموذج **مجموعة الجهارة** الحديث (أندرويد ١١+) — وهي الوحدة التي بات النظام يوحّد بها الدفقات المتساوية على الجهاز نفسه | **يُقاس على الجهاز**: نقارن ما تقوله المجموعة بما يقوله الدفق، ونعرض ما تقوله المنصّة لا ما نفترضه |
| **`getAudioDevicesForAttributes(AudioAttributes)`** · `getSupportedDeviceTypes` | أيّ جهاز **سيعزف فعلًا** لسمات معيّنة | وجهة «الجهاز النشط» تصير سماتيّة لا أوّلَ مخرج في القائمة |
| **`getAvailableCommunicationDevices`** · **`setCommunicationDevice`** · `getCommunicationDevice` · `clearCommunicationDevice` | توجيه صوت **المكالمة**: سمّاعة/مكبّر/بلوتوث/سمّاعة رأس | **تحكّم حقيقيّ عامّ** لم يكن في الشاشتين السابقتين |
| **`getSupportedMixerAttributes(AudioDeviceInfo)`** · **`setPreferredMixerAttributes(AudioAttributes, AudioDeviceInfo, AudioMixerAttributes)`** · **`getPreferredMixerAttributes`** · `add/removeOnPreferredMixerAttributesChangedListener` | **طلب معدّل العيّنة والقناة والسلوك للمازج** على جهاز معيّن، وقراءة ما صار | **أعلى ميزة «صوتيّة» عامّة**: `AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT` و`getFormat()` — «دقّة المخرج» بحدودها الصريحة |
| `isVolumeFixed` · `registerAudioDeviceCallback` · `getProperty(PROPERTY_OUTPUT_*)` | القائم | يُستعمل كما هو |

### 2.4 ما يقوله الـjar عن **«لكل تطبيق»** — وهو الجواب النهائيّ

`AudioPlaybackConfiguration` (API 37) أعضاؤه العامّة: `getAudioAttributes()` و`getAudioDeviceInfo()` —
**ولا معرّف جلسة، ولا `uid`، ولا `pid`**. ⇒ **لا سياسة صوتيّة لكل تطبيق عبر واجهة عامّة**، وهذا
**قياسٌ من الـjar** يقفل الباب الذي فتحه بحث `ViPER4Android` في `SOUND-SCREEN-PLAN` §3.3.

### 2.5 `Visualizer` — المحلّل الطيفيّ (الرسم الحقيقيّ)

`setDataCaptureListener` · `getFft(byte[])` · `getWaveForm(byte[])` · `getMeasurementPeakRms` ·
`getCaptureSizeRange()` · `getMaxCaptureRate()` · `getSamplingRate()` · `SCALING_MODE_AS_PLAYED/NORMALIZED` ·
`MEASUREMENT_MODE_PEAK_RMS`.
**وشرطه:** جلسة صوت + `RECORD_AUDIO` (إذن **غير مُعلَن** في بياننا اليوم — مقيس). ⇒ **يُطلب باختيار
صريح** أو لا يُبنى.

### 2.6 `AudioEffect` — الجلسة والحكم

`hasControl()` · `getEnabled()` · `setEnabled(boolean)` · `getId()` · `setControlStatusListener` ·
`setEnableStatusListener` · `ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION` · `EFFECT_INSERT/AUXILIARY` ·
`EFFECT_PRE/POST_PROCESSING`.
**وحدّها المقيس من الوثائق التنفيذيّة:** إرفاق مؤثّر على **الجلسة ٠ (المزج العامّ) موصوفٌ بأنه مُهمَل**
(«deprecated») منذ أندرويد ٩، ويبقى يعمل على أجهزة كثيرة عبر مسار الوسائط. ⇒ **لا نعِد به ولا نرفضه:
نُحاول، ونقول الحكم** (`attached` / `denied` / `no-engine`) — وهذا هو الفرق بين الخطّة والادّعاء.

---

## 3. مصفوفة القدرات — كل ما طلبه المالك، وحكمه الصادق

**الرموز:** ✅ يُبنى ويعمل · 🔶 يُبنى **بشرط** (يُقاس ويُعرض بحالته) · ❌ لا يُبنى ولا يُوعد.

| # | ما طلبه المالك | الحكم | الخلفيّة الحقيقيّة (Backend) | كيف يُتحقّق |
| --- | --- | --- | --- | --- |
| ١ | Equalizer متعدّد النطاقات بتحكّم دقيق في الكسب | 🔶 إذا أعلن الجهاز `Equalizer` | `getNumberOfBands` · `getCenterFreq` · `getBandFreqRange` · `getBandLevelRange` · `setBandLevel` | قراءة كل نطاق بعد الكتابة (`getBandLevel`) — والكتابة تمرّ بالـarbiter |
| ٢ | Parametric EQ | 🔶 **بحدّه الصادق**: `DynamicsProcessing` (pre/post) — **عتبة قطع + كسب لكل نطاق، بلا `Q`** | `EqBand.setGain/setCutoffFrequency` · `Eq` لكل قناة | قراءة `getConfig().getPreEqBandByChannelIndex(...)` |
| ٣ | Bass Boost | ✅ | `BassBoost.setStrength` (+ `DynamicsProcessing` pre-EQ منخفض) | `getRoundedStrength` + `PARAM_STRENGTH_SUPPORTED` |
| ٤ | Treble Enhancement | 🔶 | post-EQ عالٍ في `DynamicsProcessing`، أو أعلى نطاقات `Equalizer` | قراءة النطاق بعد الكتابة |
| ٥ | Loudness Normalization | 🔶 **بالاسم الصادق** | `LoudnessEnhancer.setTargetGain(int mB)` — **تعزيز جهارة، لا تسوية LUFS** | `getTargetGain`؛ والتسوية الحقيقيّة تحتاج **قياس الدفق** = التقاط ⇒ مرفوض |
| ٦ | Compressor وLimiter | ✅ **حقيقيّ كامل** | `DynamicsProcessing`: `MbcBand` (عتبة · نسبة · هجوم · تحرير · ركبة · بوابة · موسّع) و`Limiter` | `getConfig()` تُعيد كل معامر |
| ٧ | Stereo / Channel Balance | ✅ | `DynamicsProcessing.setInputGainbyChannel(ch, dB)` — و`Virtualizer` للمحيط | `getInputGainByChannelIndex(ch)` |
| ٨ | Virtualizer / Spatial Audio | 🔶 | `Virtualizer`: `getStrengthSupported` · `setStrength` · `canVirtualize(deviceType, channelMask)` · `forceVirtualizationMode` · `getVirtualizationMode` · `getSpeakerAngles` | كلّها تُقرأ بعد الكتابة |
| ٩ | Convolver ودعم Impulse Response | ❌ | **لا صنف مؤثّر التفاف في المنصّة** (مقيس بالـjar)؛ يلزم محرّكنا (رفض ترخيصيّ) أو مؤثّر نظاميّ | يُعرض بحالته: «يحتاج مكوّنًا نظاميًّا» — `AS-06` الجذريّ، بلا وعد |
| ١٠ | Per-App Audio Processing | ❌ | **قفل بالقياس:** `AudioPlaybackConfiguration` بلا `uid`/`session`، والإرفاق لكل جلسة يلزمه نظام/جذر | «غير متاح» **بسببه** في التشخيص |
| ١١ | Presets جاهزة + إنشاء وحفظ | ✅ | مخزننا القائم + **أنماط `Equalizer` المُعلَنة من المنصّة** (`getNumberOfPresets`/`getPresetName`/`usePreset`) + بصمتنا مرقّمة `schemaVersion` | تُحفظ وتُقرأ بعد إعادة التشغيل (اختبار JVM) |
| ١٢ | Visualization: Spectrum + Frequency Response | 🔶 | طيف/موجة/Peak-RMS عبر `Visualizer` (يحتاج `RECORD_AUDIO` + جلسة). **ومنحنى الاستجابة:** يُرسم **مطلوبًا** (ما كتبناه) ويُسمّى «منحنى مستهدف» — القياس الحقيقيّ يحتاج التقاطًا | `getFft` فعليّ على جهاز؛ والمنحنى يُقاس على JVM من النموذج |
| ١٣ | (زائد) دقّة المخرج: معدّل/قناة/bit-perfect | 🔶 | `setPreferredMixerAttributes` + `AudioMixerAttributes.BIT_PERFECT` | `getPreferredMixerAttributes` تُعيد ما صار |
| ١٤ | (زائد) توجيه صوت المكالمة | ✅ | `getAvailableCommunicationDevices`/`setCommunicationDevice` | `getCommunicationDevice` بعد الكتابة |
| ١٥ | (زائد) Jهارة بالديسيبل ونموذج المجموعات | ✅ | `getStreamVolumeDb` · `getVolumeGroupIdForAttributes` · `isVolumeGroupMuted` | مقارنة قراءة الدفق بالمجموعة |
| ١٦ | (زائد) كتم/إلغاء كتم لكل دفق | ✅ | `isStreamMute` · `adjustStreamVolume(ADJUST_MUTE/UNMUTE)` — وينقل كتم `AppMonitor` القائم إلى مسار واحد | قراءة الكتم بعد الكتابة |

**و«حالة كل ميزة» التي طلبها المالك هي بالضبط هذا الجدول، معروضًا في الشاشة:** لكل صفٍّ وسمٌ من خمسة
لا خامس لها — `مدعومة` · `قابلة للكتابة والقراءة` · `للقراءة فقط` · `تحتاج محوّلًا (adapter)` · `غير متاحة`
— **ودائمًا مع السبب**. والمحوّل نوعان حقيقيّان فقط: `جلسة نملكها` (المؤثّر يعمل على ما نشغّله) و
`مكوّن نظاميّ` (`AS-06`، يحتاج وحدةً وجهازًا).

---

## 4. ما نرفضه صراحةً (ولماذا) — امتداد `SOUND-SCREEN-PLAN` §3.2

| المرفوض | السبب |
| --- | --- |
| أيّ كود من `RootlessJamesDSP` · `JamesDSPManager` · `ViPER4Android` وإخوتها | **GPL-3.0/AGPL وأحدها مؤرشف وبعضها بلا ترخيص** (مقيس في `AUDIO-STUDIO-PLAN` §2) ⇒ **صفر كود** (ADR-55) |
| التقاط الصوت الداخليّ (`MediaProjection`/`AudioPlaybackCapture`/`DUMP`+`PROJECT_MEDIA`) | يكسر تطبيقات تحجب الالتقاط، ويضيف زمن تأخير، ويحتاج إذنًا غير مُعلَن — مرفوض في الخطّتين |
| مؤثّر لكل تطبيق | يحتاج جذرًا أو تثبيتًا نظاميًّا (`/system/priv-app` + `MODIFY_AUDIO_ROUTING` + قواعد SELinux) — يخرج عن توزيعنا |
| تعديل `mixer_paths.xml` · `audio_policy.conf` · `tinymix` · `/sys` صوتيّ | مسارات جذرية على قسم النظام؛ **ولا تُبنى بلا جهاز** (§0.1-3ج) |
| محرّك DSP مكتوب من الصفر بالباعث (Playback a-t-m) | طلبٌ لا نملك أدلّته الآن: يحتاج مسار صوت نملكه + اختبار استماع على جهاز |
| أيّ شريط/رسم يتحرّك بلا Backend | شرط المالك الصريح + ADR-07 |

---

## 5. المعمارية — ما يُضاف، وما لا يُمسّ

**يُعاد استعماله بالكامل (لا نسخة ثانية):** `HardwareControlArbiter` + `HardwareControlKey` +
`ControlOwnership` + `SharedHardwareOwnershipStore` (المسار الوحيد للكتابة، ADR-11) ·
`AudioInventory`/`AudioDeviceCatalog`/`AudioCapabilities` (القراءة) · `AudioStreamBackend`/`Catalog`/`Verdict`
(المستويات) · `AudioStudioViewModel` (الحقن) · `MaxSegmented`/`MaxGroup`/`MaxRow`/`MaxSliderRow`/`MaxSwitchRow`/
`NeuralPanel`/`NeuralKpiTile`/`MaxCapsule`/`MaxSection` · **`CapabilityMatrixCard`** (موجودة وتصلح لمصفوفة
القدرات حرفيًّا) · `LiveHistoryGraph`/`MiniSparkline` (موجودان للرسم) · `StudioPerformanceHero`.

```
core/audio/
  AudioEngineVerdict.kt      ⬜ صافٍ — حالة كل ميزة (مدعومة/للقراءة/تحتاج محوّلًا/غير متاحة) + سببها
  AudioEffectBackend.kt      ⬜ الوحيد الذي يلمس AudioEffect: إنشاء/إرفاق/تحرير + قراءة الحالة
  AudioEffectSession.kt      ⬜ استراتيجية الجلسة: ٠ (عامّ) إن قُبِل، وإلا «جلسة نملكها» — والحكم يُقال
  AudioEqModel.kt            ⬜ صافٍ — نطاقات، تحويل كسب↔ديسيبل، منحنى مستهدف، تحقّق
  AudioDynamicsModel.kt      ⬜ صافٍ — pre-EQ · MBC · Limiter · دخل كل قناة (التوازن)
  AudioMixerAttributes.kt    ⬜ طلب معدّل/قناة/سلوك المازج + حكمه
  AudioSpectrum.kt           ⬜ Visualizer (FFT/موجة/Peak-RMS) خلف إذن صريح
  AudioRoutingBackend.kt     ⬜ أجهزة سماتيّة + جهاز المكالمة
  AudioProfileV2.kt/+Store   ⬜ بصمة مرقّمة (schemaVersion) تُرحَّل بلا كسر
core/hardware/HardwareControlKey.kt   ⬜ توسيع: audio_effect:<type>:<param> · audio_route · audio_mixer
ui/subscreens/audio/
  AudioStudioScreen.kt       ⟳ يُعاد هيكلته: نظرة · المعادل · الديناميكيّ · المحيط · الأنماط · التشخيص
  AudioEqGraph.kt            ⬜ منحنى المعادل (Canvas من مفرداتنا)
  AudioSpectrumCanvas.kt     ⬜ الطيف
ui/viewmodel/AudioStudioViewModel.kt  ⟳ يُضاف إليه لا يُستبدل
```

**قواعد ملزمة:** صفر كتابة من `ui/**` (ADR-11) · كل كتابة تُنتج حكمًا `outcome/reason/expected/live` ·
المجهول يبقى مجهولًا (`null` ⇒ «—») · كل واجهة أحدث من `minSdk 29` تُحرَس بـ`Build.VERSION.SDK_INT`
(الـjar يُثبت **الوجود** لا **المستوى**؛ والمستوى يُقرأ من مرجع أندرويد عند التنفيذ) · النصوص في
`values/`+`values-ar/` فقط (§0.2) · الألوان من `MaterialTheme` والرموز من `MaxTokens`.

---

## 6. المراحل — ترتيب ملزم، وكل مرحلة تُغلق بمعيار يُقاس

| # | المرحلة | ما يُبنى | القبول (يُقاس) |
| --- | --- | --- | --- |
| `AQ-01` | **مصفوفة القدرات المقيسة** | `AudioEngineVerdict` + مسبار موسّع: أيّ مؤثّر موجود؟ · هل تُقبل جلسة ٠؟ · هل تُدعم سمات المازج؟ · هل `Visualizer` متاح؟ | كل صفٍّ له حالة وسبب · **صفر رقم مصنوع** · يُقاس على JVM في الطبقة الصافية، وعلى جهاز في المسبار |
| `AQ-02` | **محرّك المؤثّرات** | `AudioEffectBackend` + `AudioEffectSession` + صفّ مؤثّر عامّ (تمكين/تعطيل + ملكيّة التحكّم + قراءة) | `hasControl=false` ⇒ «مملوك لتطبيق آخر» **لا فشل** · كل تغيير يُقرأ بعده · تحرير المؤثّر عند مغادرة الشاشة |
| `AQ-03` | **المعادل** | `AudioEqModel` + الرسم + أشرطة النطاقات + أنماط المنصّة | على جهاز بلا `Equalizer`: القسم **«غير متاح» بسببه** ولا شريط قابل للسحب · وعلى جهاز به: كسب كل نطاق يُقرأ مطابقًا |
| `AQ-04` | **الديناميكيّ** | `AudioDynamicsModel` + pre/post-EQ + MBC + Limiter + **التوازن** (دخل القنوات) | `getConfig()` تُقرأ بعد الكتابة لكل معامل · الخفض المتساوي لا يرفع الصوت (لا تفاعل غير مقصود يُخفى) |
| `AQ-05` | **المؤثّرات البسيطة** | Bass · Virtualizer (قوّته ونمطه) · LoudnessEnhancer · Reverb | كلٌّ يُعرض **حيث يُعلن نفسه** فقط · وقوّته تُقرأ بعد الكتابة |
| `AQ-06` | **المزج والتوجيه** | معدّل/قناة/bit-perfect + جهاز المكالمة + الأجهزة السماتيّة + المجموعات + الديسيبل | ما طُلب يُقرأ مطابقًا (`getPreferredMixerAttributes`) أو يُقال «رفضته المنصّة» |
| `AQ-07` | **الأنماط والبصمات** | `AudioProfileV2` (`schemaVersion`) + حفظ/تحميل/تصدير + ربط الجهاز | إنشاء/تعديل/حذف يبقى بعد إعادة التشغيل · ترحيل نسخة أقدم يُختبر على JVM · **لا نمط يَعِد بمؤثّر غير موجود** |
| `AQ-08` | **التحليل البصريّ** | `AudioSpectrum` (FFT + موجة + Peak/RMS) + المنحنى المستهدف | الإذن يُطلب **بشرح** · بلا إذن: القسم مُعطَّل **بسببه** لا مُخفى · الطيف يتحرّك من بيانات حقيقيّة على جهاز |
| `AQ-09` | **الجذريّ — نُفِّذ القابل للقياس منه (تكملة ٢٢٩)** | مؤثّر نظاميّ (`audio_effects.xml`): قراءة ملفّ الجهاز شجرةً عامّة، ودمجٌ **بإلحاقٍ فقط**، ثمّ وحدة Magisk تُكتب عبر المحكِّم وتُقرأ بعدها | **المقيس هنا:** ٤٤ اختبارَ JVM على النموذج الصافي · سقف الطبقة `system/…` من دليل Magisk (لا جذر الوحدة) · وسقف الصلاحية ٠٦٤٤ شرطَ نجاحٍ · **والحدّ:** قبول `audioserver` للطبقة بعد الإقلاع **يحتاج جهازًا** ولا يُدَّعى (§0.1) |

**تعديل مؤرَّخ (تكملة ٢٢٩، 2026-10-01):** `AQ-09` كانت «خارج الموجة الحالية» — ونُفِّذ منها **القابل للقياس**
بأمر المالك («نفّذ القابل للقياس من `AQ-09` الآن»)، ويبقى منها **التحقّق على جهاز** وصفُّ الطبقة في مصفوفة
القدرات. وما نُفِّذ لم يُغيَّر حكمُه في التصنيف: ما لا يُقاس هنا مُعلَنٌ «غير مُتحقَّق في هذه البيئة».

**الترتيب ملزم:** `AQ-01` ← `AQ-02` ← … ← `AQ-08`؛ و`AQ-09` خارج الموجة الحالية. و`AQ-01` تُقدَّم لأنها
**البوّابة**: بها تُعرف أيّ مرحلة ستُعرض وبأيّ حالة على جهاز المالك تحديدًا.

---

## 7. المخاطر

| # | الخطر | الاحتواء |
| --- | --- | --- |
| ١ | «معادل شامل» يُخفق على أجهزة كثيرة (إرفاق الجلسة ٠ مُهمَل) | لا نعِد به: نحاول ونعرض الحكم، والبديل المعلَن «جلسة نملكها» |
| ٢ | كل نداء `AudioEffect` يرمي على أجهزة بلا محرّك خاصّ | كل نداء داخل `runCatching` + حكمٌ مكتوب؛ القسم يُعرض بحالته لا يُخفي الشاشة |
| ٣ | المؤثّر يبقى معلّقًا بعد مغادرة الشاشة فيُغيّر صوت المستخدم | تحرير صريح (`release`) عند `onDispose` + تسجيل الحالة — شرط قبول `AQ-02` |
| ٤ | `Visualizer` إذنٌ حسّاس | طلبٌ صريح بشرح، ودونه لا شيء من الطيف يعمل (لا وهم) |
| ٥ | اشتقاق ترخيصيّ | صفر كود GPL؛ المفاهيم تُستأنس ويُوثَّق الرجوع (§4) |
| ٦ | تجاوز `minSdk 29` بلا حارس | كل واجهة أحدث تُحرَس بـ`SDK_INT`، وتُقرأ مستوياتها من المرجع |
| ٧ | تلويث طبقة `ui/` بكتابة عتاد | كل الكتابة عبر الـarbiter؛ `ControlPlaneArchitectureTest` تُشغَّل |

---

## 8. المجاهيل — تُقاس على جهاز، ولا تُدَّعى

1. **إرفاق مؤثّر على الجلسة ٠** على `rodin` (MT6899 · أندرويد ١٦) — ويبدّله وجود وحدة صوت أخرى مثبّتة
   (`ViPER4Android-RE-AIDL` كانت مثبّتة، مقيسة في حزمة ٢٠٢٦-١٠-٠١).
2. **قبول `Visualizer` على الجلسة ٠** (قد يلزمه التقاط مُخوَّل).
3. **`setPreferredMixerAttributes`** على مسار USB/بلوتوث على هذا الجهاز.
4. ما إذا كانت **المجموعة** (`VolumeGroup`) تُوحّد `ring`/`notification` على هذا الـROM.
5. وجود **مؤثّرات مصنّع** (Dolby/Dirac) وقابليّة الإرفاق بها بلا ملكيّة.

---

## 9. أسئلة المالك — ثلاثة فقط، والباقي محسوم

1. **`RECORD_AUDIO` للمحلّل الطيفيّ:** نطلبه (مع شاشة شرح) أم نتركه خلف مفتاح اختياريّ معطّل افتراضيًّا؟
2. **ميزة المزج (معدّل/قناة/bit-perfect):** نبنى الآن في `AQ-06` أم نؤجّلها؟
3. **الطبقة الجذرية (`AQ-09`)**: الآن (مع إعلانها «تحتاج جهازًا») أم نتركها بعد إغلاق `AQ-01…AQ-08`؟

> **ولا سؤال رابع:** كل ما في §3 محسوم بالقياس، وما رُفض مُثبَّت بسببه في §4.
