# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
#
# بناء مكتبة المؤثّر النظاميّ `libmaxfx.so` — **يحمّلها `audioserver`** من
# `system/<abi>/soundfx/` بعد تركيب طبقة `AQ-09`، فلا علاقة لها بـ`System.loadLibrary`.
#
# و`LOCAL_PATH` مُعاد توجيهه إلى جذر `maxfx/` لأنّ المصادر في `src/` لا في `jni/` —
# و`ndk-build` يُنادى من `maxfx/` فيجد `jni/Android.mk` و`jni/Application.mk` تلقائيًّا،
# بنفس نمط `archdaemon/` و`preloadbin/`.
#
# ── العقد المزدوج: `AELI` + `createEffect`/`queryEffect`/`destroyEffect` ──
#
# الرمز `AELI` يخدم أجهزة ≤11 (HIDL)، والثلاثة تخدم 12+ (AIDL) — ومصنعُ AIDL
# (`hardware/interfaces/audio/aidl/default/EffectFactory.cpp`) يبحث عنها بـ`dlsym`، وغيابها
# يعني «المكتبة لا تُحمَّل» أي «لا تغيّر في الصوت» **بلا سطر عطلٍ واحد**.
#
# وبناءُ غلاف AIDL مشروطٌ **بوجود رؤوسه المُولَّدة** (`maxfx/gen/…/BnEffect.h`):
#   · وُجدت  ⇒ تُبنى المكتبة الواحدة بالرموز الخمسة (المسار الكامل، وهو مسار CI).
#   · غابت   ⇒ تُبنى مكتبة `AELI` وحدها مع تحذيرٍ صريح — لأنّ غيابها يعني غياب `aidl`
#               و`libbinder_ndk` و`libfmq` عن بيئة البناء، وهذه **حدود بيئة** لا نيّة.
#               و**بوابة الرموز الخمسة في CI تكشف هذا الغياب**، فلا يمرّ نقصٌ صامت.
#
# ⚠️ وحدُّ لا يُتجاوز: لا نُعدّل مصدرًا منسوخًا من AOSP لأجل تحذيرٍ في بناءنا (ADR-18) —
# السبيل الوحيد عندها هو `-Wno-error=<diag>` هنا، ويُكتب سببه في السجل.

JNI_DIR := $(call my-dir)
LOCAL_PATH := $(JNI_DIR)/..

MAXFX_AIDL_GEN := $(LOCAL_PATH)/gen
MAXFX_AIDL_ROOT := $(LOCAL_PATH)/aidl
MAXFX_BINDER_HEADER := $(MAXFX_AIDL_GEN)/aidl/android/hardware/audio/effect/BnEffect.h
MAXFX_FMQ := $(LOCAL_PATH)/third_party/libfmq

# التوليد **قبل** الترجمة: `$(shell)` يُنفَّذ عند قراءة الملفّ، فيجد المترجم الرؤوس جاهزة.
ifeq ($(wildcard $(MAXFX_BINDER_HEADER)),)
  ifneq ($(wildcard $(MAXFX_AIDL_ROOT)),)
    $(shell sh $(MAXFX_AIDL_ROOT)/gen.sh 2>&1 | sed 's/^/maxfx-aidl: /' >&2)
  endif
endif

include $(CLEAR_VARS)
LOCAL_MODULE := maxfx

LOCAL_SRC_FILES := \
    src/maxfx_dsp.c \
    src/maxfx_effect.c \
    src/maxfx_props_android.c

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include $(LOCAL_PATH)/src

# نفس انضباط `archdaemon`: لا تحذير ولا خطأ — وتحذيرٌ اليوم عطبٌ غدًا على جهاز.
LOCAL_CFLAGS := -Wall -Wextra -Werror -O2 -fPIC
LOCAL_CONLYFLAGS := -std=c23
# `libm` للمرشّحات؛ ولا `liblog` في نواة المؤثّر (المسار الحيّ بلا I/O).
LOCAL_LDLIBS := -lm

ifeq ($(wildcard $(MAXFX_BINDER_HEADER)),)

  # ── مسار AELI وحده (بيئة بلا SDK/fmq) ──
  $(warning maxfx: رؤوس AIDL غائبة ⇒ تُبنى مكتبة AELI وحدها (لا createEffect/queryEffect/destroyEffect))

else

  # ── المصدر المُولَّد للواجهة: `IEffect.cpp` — وهو ليس «مصدرًا اختياريًّا» ──
  #
  # المولِّد يكتب **ثلاثة رؤوس** في `--header_out` (`IEffect.h` · `BpEffect.h` · `BnEffect.h`)
  # و**مصدرًا واحدًا** في `--out` باسم النوع نفسه، لا باسم الأصناف بداخله
  # (‏`GenerateNdk` في `generate_ndk.cpp`: السطور ١١٤–١٢٣ — ثلاثة `OutputHeaderDir` ومصدرٌ
  # واحد؛ و`GetOutputFilePath` في `aidl.cpp` تُعطي `.cpp` لأنّ `IsCppOutput()` تضمّ `NDK`
  # أيضًا — `options.h` سطر ١٥٢).
  #
  # وتركُه عطبُ **ربطٍ** لا ترجمة، قِيس في التشغيل `37031374179`:
  #   ld.lld: error: undefined symbol: …::BnEffect::createBinder()  (ومعه الباني والمُدمِّر)
  # أي أنّ الرؤوس وحدها لا تكفي وإن ترجمت كلّها بلا خطأ — فالبناء الناجح يُترجم ثم يُربط.
  #
  # ❗ ولم يكفِ مصدر الواجهة وحده: كلّ parcelable مُستعمل من الرؤوس يجب أن يُعرّف هو أيضًا،
  # ولا يُعرف أيّها يُستدعى إلا من كود المولِّد — فالمُصرَّف **مصادر رزمنا الأربع** كلّها.
  # (وهذا تصحيحٌ لِما كُتب هنا أوّلًا: «نُصرّف ما نستعمله فقط» — قِيس خطؤه في `37034950650`.)
  # ⚠️ والمسار **نسبيّ إلى `LOCAL_PATH`** ولا يُبنى عليه: `ndk-build` تُسبِق كلّ مدخل في
  # `LOCAL_SRC_FILES` بـ`$(LOCAL_PATH)/` تلقائيًّا، فكتابة المسار كاملًا تُنتج مسارًا
  # **مضعَّفًا** — قِيس في `37033371291`:
  #   make: *** No rule to make target 'jni/../jni/../gen/…/IEffect.cpp'
  # (و`LOCAL_PATH` هنا `jni/..` لأنّ `$(call my-dir)` أعادت مسارًا نسبيًّا.)
  # وأمّا `LOCAL_C_INCLUDES` فتُستعمل كما هي، ولذلك مسارات الرؤوس مبنيّة على `LOCAL_PATH`.
  #
  # ⚠️⚠️ والموضع مُجمَّع بـ`wildcard` على **موضعين** وهما ليسا واحدًا — وهذا مقيس أيضًا
  # (التشغيل `37034110669`: الفحص بحث في `gen/aidl/…/IEffect.cpp` فلم يجده):
  #   • الرؤوس تُكتَب بـ`--header_out` وبادئةٍ من المولِّد نفسه: `gen/`**`aidl/`**`android/…`
  #   • والمصادر تُكتَب بـ`--out` من `GetOutputFilePath` التي تبني من **اسم الحزمة** وحده:
  #     `gen/`**`android/…`** — بلا البادئة. ومجلّدان مختلفان، فلا يُفترض أنّ أحدهما يُنتج الآخر.
  # ❗ ولم تكفِ حزمة `effect` وحدها: قِيس في `37034950650` أنّ الربط يطلب تعريفات
  # `readFromParcel`/`writeToParcel` لأصنافٍ في **الرزم الثلاث الأخرى**:
  #   aidl::android::hardware::common::NativeHandle
  #   aidl::android::hardware::common::fmq::GrantorDescriptor
  #   aidl::android::media::audio::common::{AudioUuid · AudioConfig · …}
  # وهي مُضمَّنة في رأس المولِّد كنماذج (templates) في `binder_parcel_utils.h`، فالمُصرّف
  # يُصدِر نداءً بلا تعريف — والرؤوس وحدها لا تُعرّف. فالمُصرَّف هي **مصادر رزمنا الأربع**
  # (وهي المذكورة بالاسم في `maxfx/aidl/README.md`)، وفي الموضعين لكلٍّ منها.
  MAXFX_AIDL_PKG_DIRS := \
      gen/android/hardware/audio/effect \
      gen/android/hardware/common/fmq \
      gen/android/hardware/common \
      gen/android/media/audio/common \
      gen/aidl/android/hardware/audio/effect \
      gen/aidl/android/hardware/common/fmq \
      gen/aidl/android/hardware/common \
      gen/aidl/android/media/audio/common
  MAXFX_AIDL_GEN_SRCS := $(foreach d,$(MAXFX_AIDL_PKG_DIRS),$(wildcard $(d)/*.cpp))
  ifeq ($(strip $(MAXFX_AIDL_GEN_SRCS)),)
    $(warning maxfx: لا مصدر مُولَّد في الرزم الأربع ⇒ ربطٌ بلا تعريف (راجع تشخيص CI)
  endif

  LOCAL_SRC_FILES += \
      src/maxfx_aidl.cpp \
      $(MAXFX_AIDL_GEN_SRCS) \
      third_party/libfmq/src/EventFlag.cpp \
      third_party/libfmq/src/FmqInternal.cpp \
      third_party/libfmq/compat/native_handle_shim.cpp

  # ── رؤوس الـC++ لـ`libbinder_ndk`: **منسوخة عندنا** — لأنّ الـNDK لم يعد يُشحَنها ──
  #
  # الكود المُولَّد من `aidl --lang=ndk` يُضمِّن سبعة رؤوس من `android/` ليست في الـNDK
  # (مسردها المقيس: دالّة `GenerateHeaderIncludes` في `system/tools/aidl/generate_ndk.cpp`،
  # السطور ٢٩٢–٣٩٨)، وهي في AOSP تحت `libs/binder/ndk/include_cpp` و`include_platform`.
  #
  # والقياس الذي أوجب النسخ — على حزمة **NDK r29** نفسها بقراءة فهرسها بلا تنزيل:
  #   • `sysroot/usr/include/android/` فيه واجهة C فقط (`binder_ibinder.h` · `binder_status.h`
  #     · `binder_parcel.h` …)، **وكلّ رؤوس الـC++ غائبة**.
  #   • **لا مجلّد `platforms/` في الحزمة إطلاقًا** — فلا مسار `optional/libbinder_ndk_cpp`
  #     الذي وصفته مسألة NDK `android/ndk#2130` (وهي تصف r28؛ وقد أُغلقت «كما هو مقصود»).
  # والتفصيل في `third_party/libbinder_ndk_cpp/README.md`، وبوابة `tools/maxfx_contract.py`
  # تحرس وجود الرؤوس الثمانية قبل أن يبدأ البناء.
  MAXFX_BINDER_NDK_CPP := $(LOCAL_PATH)/third_party/libbinder_ndk_cpp
  ifeq ($(wildcard $(MAXFX_BINDER_NDK_CPP)/android/binder_interface_utils.h),)
    $(warning maxfx: رؤوس libbinder_ndk المنسوخة غائبة عن $(MAXFX_BINDER_NDK_CPP))
  endif

  # `compat` **أوّلًا**: بُدَلنا لـ`cutils/native_handle.h` و`utils/*` و`android-base/*`
  # يجب أن تُسبق رؤوس النظام في القرار. و`base` هو مسار `libfmq-base` (`fmq/MQDescriptorBase.h`).
  LOCAL_C_INCLUDES += \
      $(MAXFX_AIDL_GEN) \
      $(MAXFX_BINDER_NDK_CPP) \
      $(MAXFX_FMQ)/compat \
      $(MAXFX_FMQ)/include \
      $(MAXFX_FMQ)/base

  # `-std=c++20` لـAIDL و`libfmq`؛ و`-fexceptions` لأنّ `ndk::ScopedAStatus` و
  # `std::shared_ptr` يحتاجانها. و`-Werror` مفعّل هنا أيضًا — وإن سقط على ملفٍّ منسوخ،
  # فالعلاج `-Wno-error=<diag>` مع سببٍ مكتوب، لا تعديلُ المنسوخ.
  #
  # ⚠️ الاستثناء القائم — وهو من عائلةٍ أخرى (`-Wdeprecated-copy-*` توسّع في Clang الجديد،
  # فلا تُصلح مصدرًا منسوخًا لتحذير في بناءنا):
  #   `third_party/libfmq/include/fmq/MessageQueueBase.h:274` — `MemRegion` يملك مُعامل
  #   إسنادٍ بنسخ مُعرَّفًا وبناؤه الضمنيّ مهجور، وكلاهما **صحيحٌ ومقصود** في AOSP. قِيس في
  #   التشغيل `37027906076`: `-Werror,-Wdeprecated-copy-with-user-provided-copy`، مرّتين
  #   (نسخة `float` ونسخة `IEffect::Status`).
  LOCAL_CPPFLAGS := -std=c++20 -fexceptions -Wall -Wextra -Werror \
      -Wno-error=deprecated-copy-with-user-provided-copy
  LOCAL_CPP_FEATURES := exceptions

  # `libbinder_ndk` **تُشحَن في الـNDK** (libbinder_ndk.so + رؤوسها)،
  # و`libandroid` لـ`ASharedMemory_*` (أساس منطقة FMQ)، و`liblog` للسجل.
  # ولا `libfmq`: نُبنى نسختها المشحونة معنا (المصدر المنسوخ) — فالمكتبة **مكتفية ذاتيًّا**.
  LOCAL_LDLIBS += -lbinder_ndk -landroid -llog

endif

include $(BUILD_SHARED_LIBRARY)
