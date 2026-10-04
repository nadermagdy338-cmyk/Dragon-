# `maxfx/aidl` — نسخ AIDL مجمَّدة (منسوخة من AOSP)

هذه الشجرة **ليست كودنا**: نسخٌ حرفيّة من رؤوس AIDL المجمَّدة في AOSP، لازمَةٌ لأنّ مؤثّر
`Android 12+` يُطلب بعقد `AIDL` لا بعقد `AELI` (انظر `maxfx/src/maxfx_aidl.cpp` و`ADR-66`).

| الرخصة | Apache-2.0 (بنسبة الفضل — كما في `maxfx/include/maxfx_effect_abi.h`) |
| --- | --- |
| المصدر | `https://android.googlesource.com/platform/hardware/interfaces` و`…/platform/system/hardware/interfaces` |
| الفرع | `refs/heads/main` وقت النسخ |
| حالة الملفّات | مجمَّدة (`IMMUTABLE — DO NOT EDIT`)، ولا يُعدَّل منها سطر (ADR-18) |

## ما نُسخ وبأيّ نسخة (وهذا هو الحسّاس)

الحزمة الواحدة تُصدَّر بأكثر من نسخة مجمَّدة، والإطار على الجهاز بُني على **نسخةٍ بعينها**:

| الحزمة | النسخة المنسوخة | مصدرها |
| --- | --- | --- |
| `android.hardware.audio.effect` | **1** (`IEffect`, `Descriptor`, `Parameter`, `Capability`, … ٢٧ ملفًا) | `audio/aidl/aidl_api/android.hardware.audio.effect/1/…` |
| `android.hardware.common.fmq` | **1** (`MQDescriptor`, `GrantorDescriptor`, …) | `common/fmq/aidl/aidl_api/android.hardware.common.fmq/1/…` |
| `android.hardware.common` | **2** (`NativeHandle`, `Ashmem`, `MappableFile`) | `common/aidl/aidl_api/android.hardware.common/2/…` |
| `android.media.audio.common` | **2** (`AudioUuid`, `AudioConfig`, `AudioChannelLayout`, `PcmType`, … ٦٠ ملفًا) | `media/aidl_api/android.media.audio.common.types/2/…` |

**ولماذا هذه النسخ بالذات:** `audio/aidl/Android.bp` يعلن `versions_with_info` للإصدار **1** من
`android.hardware.audio.effect` باستيراد **`android.media.audio.common.types-V2`**، والحزمة
نفسها تُصدِّر `imports: ["android.hardware.common-V2", "android.hardware.common.fmq-V1"]`.
فهذه هي النسخ التي كُتب بها الإطارُ الذي يقرأ ما نُرسل — ونسخةٌ أحدث تُدخل حقولًا لا يعرفها
فيقرأ بايتاتٍ في غير مواضعها بلا خطأ ظاهر.

## التوليد

```sh
sh maxfx/aidl/gen.sh          # ⇒ maxfx/gen/aidl/<package>/<Type>.h  (مُولَّد، متجاهَل في git)
```

والسكربت يجد `aidl` من `$AIDL_TOOL` أو `PATH` أو `$ANDROID_HOME/build-tools/*/aidl`، ويطبع
`GEN=ok …` أو `GEN=skipped reason=…` — و**غياب الأداة لا يُفشل شيئًا هنا**: الذي يكشفه
**بوابة الرموز الخمسة في CI** (`readelf --dyn-syms` على `libmaxfx.so` للعمودين).

## ما لم يُنسخ (حدّ مُعلن)

- `IFactory.aidl` و`Processing.aidl` موجودان لكن لا يُستعملان هنا (نحن **مكتبةٌ** يخدمها
  مصنعُ الإطار، لا خدمةٌ قائمة بذاتها).
- `Range`/`Capability` تُترك فارغة في وصفنا: نوعُنا مُخصَّص، ومدىات الأنواع المعروفة
  (`Equalizer`, `BassBoost`, …) لا تنطبق عليه.
- ملاحظة `IFactory`/`Processing` لا تُشكّل خطرًا: `aidl --lang=ndk` يُولِّد رؤوسًا لكل الملفّات،
  ويُستهلك منها ما يُضمَّن فقط.
