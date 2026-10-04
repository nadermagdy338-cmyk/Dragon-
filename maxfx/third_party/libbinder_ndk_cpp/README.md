# `maxfx/third_party/libbinder_ndk_cpp` — رؤوس الـC++ لـ`libbinder_ndk` (منسوخة من AOSP)

| الرخصة | Apache-2.0 (بنسبة الفضل — رأس الترخيص محفوظ حرفيًّا في كل ملفّ) |
| --- | --- |
| المصدر | `https://android.googlesource.com/platform/frameworks/native/+-/refs/heads/main/libs/binder/ndk/` |
| المنسوخ | `include_cpp/android/*.h` (سبعة) و`include_platform/android/binder_stability.h` — **٨ رؤوس · ٣٢٠٤ سطرًا** |
| المُضاف من عندنا | لا شيء — نسخٌ حرفيّة بلا تعديل سطر |

## لماذا نسخةٌ داخليّة — والقياس الذي أوجبها

غلاف العقد المزدوج (`src/maxfx_aidl.cpp`) يُترجم على **كود مُولَّد** من `aidl --lang=ndk`، وهذا
الكود يُضمِّن من `android/`:

```
binder_ibinder.h · binder_stability.h · binder_interface_utils.h
binder_parcel_utils.h · binder_parcelable_utils.h · binder_to_string.h · binder_enums.h
```

(المصدر المقيس: `system/tools/aidl/generate_ndk.cpp` دالّة `GenerateHeaderIncludes`، السطور
292–398 — منها يُبنى الاسم في رأس كل ملفّ مُولَّد.)

و`libbinder_ndk` **تُشحَن في الـNDK** كواجهة C فقط منذ r28. والقياس — لا الاستنتاج — من حزمة
**NDK r29 نفسها** (`android-ndk-r29-linux.zip` · 783,549,481 بايت · `Pkg.Revision =
29.0.14206865` المقروء من `source.properties` — وهو **بالحرف** ما يستعمله CI) بقراءة فهرسها
بنطاقات HTTP بلا تنزيلها:

| ما يُشحَنه الـr29 في `sysroot/usr/include/android/` | الحكم |
| --- | --- |
| `binder_ibinder.h` · `binder_status.h` · `binder_parcel.h` · `binder_ibinder_jni.h` · `binder_parcel_jni.h` · `persistable_bundle.h` | **موجودة** (واجهة C) |
| `binder_interface_utils.h` · `binder_auto_utils.h` · `binder_stability.h` · `binder_parcel_utils.h` · `binder_to_string.h` · `binder_parcelable_utils.h` · `binder_enums.h` · `binder_internal_logging.h` · `binder_shell.h` | **غائبة كلّها** |
| مجلّدات `platforms/*/optional/libbinder_ndk_cpp` | **لا وجود لها** — لا مجلّد `platforms/` في الحزمة أصلًا |

وهذا يُصحّح ما نُقل عن مسألة NDK الرسمية `android/ndk#2130` (المُغلقة «كما هو مقصود») من أنّ
الرؤوس «تحت `platforms/android-31/optional/…`»: ذاك وصفُ r28، **والمقيس في r29 أنّها غير موجودة
في أيّ موضع من الحزمة**. فيبقى الحلّ: النسخ — وهو نفس مسلك `maxfx/third_party/libfmq`.

## الإغلاق التعدّي (ولماذا هذه الثمانية لا غيرها)

حُسب الإغلاق آليًّا: يُبدأ بما يُضمِّنه الكود المُولَّد، ويُتبع `#include` في كل رأس يُنزَّل،
وما كان **موجودًا في الـNDK يُستثنى** (فلا يُظلَّل رأس النظام). فالنتيجة:

```
binder_interface_utils.h → binder_auto_utils.h → binder_internal_logging.h (+ iostream/unistd)
binder_stability.h (include_platform) · binder_parcelable_utils.h · binder_parcel_utils.h
binder_to_string.h · binder_enums.h
```

و`binder_shell.h` **متروك عن قصد**: لا يُضمَّن إلا داخل `#if __has_include(<android/binder_shell.h>)`
في `binder_interface_utils.h` — فغيابه يُطفئ تسجيل أمر `dumpsys` لخدمةٍ لسنا هي (نحن **مكتبة
مؤثّر** يخدمها مصنع الإطار). والحدّ مُعلن: هذا مسار مُطفأ بإرادتنا لا عطب.

## حدّ مُعلن

- **لا رأس نظام واحد يُظلَّل**: ننسخ ما غاب فقط، فيبقى `binder_ibinder.h` و`binder_status.h`
  و`binder_parcel.h` من الـNDK نفسه.
- النسخة من `refs/heads/main` وقت النسخ (مطابقة لِمَا نُسخت به لقطات `maxfx/aidl/`)، وليست
  مضمونة التطابق سطرًا بسطر مع رؤوس أيّ جهاز — المُطابَق هو **واجهة C** التي تُصدِّرها
  `libbinder_ndk.so`، وهي ما تُربط بها هذه الرؤوس.
- **لم تُترجم بعد**: ترجمة `maxfx_aidl.cpp` معها لم تُقَس في هذه البيئة (لا NDK محليًّا) —
  القياس في CI، وبوابة `tools/maxfx_contract.py` تحرس وجودها وسلامتها قبل ذلك.
