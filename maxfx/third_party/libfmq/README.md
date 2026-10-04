# `maxfx/third_party/libfmq` — نسخة libfmq داخليّة (منسوخة من AOSP)

| الرخصة | Apache-2.0 (بنسبة الفضل) |
| --- | --- |
| المصدر | `https://android.googlesource.com/platform/system/libfmq` (`refs/heads/main`) |
| المنسوخ | `include/fmq/*.h` · `base/fmq/MQDescriptorBase.h` · `src/EventFlag.cpp` · `src/FmqInternal.cpp` |
| المُضاف من عندنا | `compat/` (رؤوس بديلة لِما ليست في الـNDK) — وهذا وحده ليس من AOSP |

## لماذا نسخةٌ داخليّة

عقدُ AIDL للمؤثّرات يتبادل بيانات الصوت عبر **FMQ** (`OpenEffectReturn` يحمل ثلاثة مقابض:
الحالة · الدخل · الخرج)، ولا يقبل الإطار مؤثّرًا بلا مقابض دخلٍ وخرج
(`EffectsFactoryHalAidl` → `EffectHalAidl::process` يشترط وجودهما). و`libfmq` **ليست في
الـNDK** (`developer.android.com/ndk/guides/stable_apis` تذكر `libbinder_ndk` ولا تذكر
`libfmq`)، فإمّا تُبنى من مصدرها هنا، وإمّا يُخترع تخطيط الذاكرة المشتركة من الذاكرة — والثاني
عطبُ صوتٍ صامت. وأصل `system/libfmq` نفسه (سطر `srcs: ["EventFlag.cpp", "FmqInternal.cpp"]`)
هو ما نُبنيه، فالتخطيط مُطابقٌ لِما يقرؤه الإطار بالضرورة.

## `compat/` — وما فيها وما سببها

`libfmq` في AOSP تُربط بـ`libbase` و`liblog` و`libcutils` و`libutils`، وهذه الأربع ليست في
الـNDK. فبدائل `compat/` تُوفّر **سطح الاستعمال الفعليّ فقط** لا المكتبات كاملة:

| البديل | يُنفَّذ بـ | ملاحظة |
| --- | --- | --- |
| `android-base/unique_fd.h` | إغلاقٌ مملوكٌ بسيط | السطح مقيس من مواضع الاستعمال في `MessageQueueBase.h` |
| `android-base/logging.h` | `__android_log_print` | `LOG(SEV) << …` و`CHECK(expr)` |
| `utils/Log.h` | `__android_log_print` | `ALOG*` و`android_errorWriteLog` |
| `utils/Errors.h` | قيم AOSP الحقيقيّة | `OK=0`، والباقي سالب `-errno` |
| `utils/SystemClock.h` | `clock_gettime(CLOCK_MONOTONIC)` | رتيبة، وإلا صارت المهلة سالبة |
| `cutils/ashmem.h` | `ASharedMemory_*` من الـNDK | نفس الطبقة التي كانت `ashmem_*` غلافًا عليها |
| `cutils/native_handle.h` + `native_handle_shim.cpp` | تنفيذٌ محلّيّ | الدوالّ في `libcutils` لا في رأسٍ فقط |

## حدّ مُعلن

هذه **نسخة** من `main` وقت النسخ، وليست مضمونة التطابق سطرًا بسطر مع نسخة أيّ جهاز — المُطابَق
هو **التخطيط** (بنية `MQDescriptorBase` وحلقة الرموز وأعلام الحدث)، وهو مستقرّ منذ Android 12.
وما لا يُثبته قياسٌ على جهاز يبقى **غير مُتحقَّق على العتاد**.
