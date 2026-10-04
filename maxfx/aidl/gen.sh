#!/bin/sh
# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
#
# توليد رؤوس AIDL بلغة NDK من النسخ المجمَّدة المشحونة في `maxfx/aidl/`.
#
# **ولماذا مجمَّدة لا «current»:** الشُّحنة على الجهاز بُنيت من لقطةٍ مجمَّدة
# (`aidl_api/android.hardware.audio.effect/1/` وإخوانها)، والتخطيط السلكيّ للـparcelable
# يُقاس على تلك اللقطة — فبناءُ نسخةٍ من `current` قد يُدخل حقولًا إضافيّة لا يعرفها الإطار
# فيقرأ بايتاتٍ في غير مواضعها. والمسارات والنسخ مكتوبة في `maxfx/aidl/README.md`.
#
# والاستعمال: `sh maxfx/aidl/gen.sh` (يُنادى من `maxfx/jni/Android.mk` ومن CI).
# المخرج: `maxfx/gen/aidl/<package>/<Type>.h` — وهو في `.gitignore` (مُولَّد لا مُشحون).
#
# وغياب أداة `aidl` **لا يُفشل** السكربت: يطبع `GEN=skipped` وتُبنى مكتبة `AELI` وحدها،
# و**بوابة الرموز الخمسة في CI** هي التي تكشف الغياب — فلا يمرّ نقصٌ صامت.
set -e

here=$(dirname "$0")
root=$(cd "$here/.." && pwd)
out="$root/gen"

tool="${AIDL_TOOL:-}"
if [ -z "$tool" ]; then
    tool=$(command -v aidl 2>/dev/null || true)
fi
if [ -z "$tool" ]; then
    for candidate in "$ANDROID_HOME"/build-tools/*/aidl "$ANDROID_SDK_ROOT"/build-tools/*/aidl \
                     "$HOME"/android-sdk/build-tools/*/aidl; do
        if [ -x "$candidate" ]; then
            tool="$candidate"
            break
        fi
    done
fi
if [ -z "$tool" ]; then
    echo "GEN=skipped reason=no-aidl (شُغِّلت مكتبة AELI وحدها)"
    exit 0
fi

count=$(find "$root/aidl" -name '*.aidl' | wc -l | tr -d ' ')
if [ "$count" -eq 0 ]; then
    echo "GEN=skipped reason=no-aidl-sources"
    exit 0
fi

mkdir -p "$out"
# ── مُعاملات — وكلُّها **قِيس بخطأ في CI أو بقراءة مصدر الأداة** لا خُمِّنت ──
#
# ① في التشغيل الأوّل (`37018104762`) مرّرنا `-o` وحده، فقال حرفيًّا:
#       ERROR: Header output directory is not set. Set with --header_out.
# ② وفي الثاني (`37018686826`) مرّرنا `--header_out` وحده (وقد زعم تعليقٌ هنا أنّ `--out`
#    زائدة — وهو استنتاجٌ لم يكن مقيسًا)، فقال حرفيًّا:
#       ERROR: Output directory is not set. Set with --out.
#
# فالدرس مكتوب كما وقع: `--lang=ndk` يطلب **الاثنين**، ولا يُستغنى بأحدهما. وما يُقاس في
# تشغيلٍ لا يُستنتج من تشغيلٍ آخر — وقد كان هذا بالضبط موضع الخطأ في الثانية.
#
# ③ وفي الثالث (`37019671833`) مرّ الإصلاح السابق، فظهر ما كان يستّره — ونصّه سطران:
#       ERROR: …/VendorExtension.aidl:36.11-27: Must compile @VintfStability type w/
#              aidl_interface 'stability: "vintf"'
#       ERROR: …/VendorExtension.aidl:36.11-27: Must compile @VintfStability type w/
#              aidl_interface --structured
#
#    وهما **شرطان لا شرط**، وقد قُدِّما معًا هنا بعد قراءة الشرط الذي يُنتجهما في مصدر الأداة
#    نفسها لا بعد التخمين (‏`system/tools/aidl/aidl.cpp`):
#         if (defined_type->IsVintfStability()) {
#           if (options.GetStability() != Options::Stability::VINTF) { …'stability: "vintf"' }
#           if (!options.IsStructured())                          { …--structured }
#    ولقطاتنا من `aidl_api/**` مسجَّلة `@VintfStability` في رأس ملفّها، والحالتان الافتراضيّتان
#    في الأداة — `stability_ = Stability::UNSPECIFIED` و`structured_ = false` — ترفضانها.
#    فالعلمان معًا: `--structured` و`--stability=vintf`، وقيمته الوحيدة المقبولة هي `vintf`
#    (‏`StabilityFromString` لا تعرف غيرها). والملفّ المُسمّى في البلاغ موجود في اللقطة، فالحلّ
#    تمرير العلم لا حذف الملفّ.
#
# ④ وفي الرابع (`37020305510`) مرّت الثلاثة، فظهر سقف الإصدار — ونصّه:
#       ERROR: …/VendorExtension.aidl:37.1-19:  ParcelableHolder is available since SDK = 31.
#              Current min_sdk_version is 29.
#    و29 ليست اختيارًا منّا: هي `DEFAULT_SDK_VERSION_NDK` في الأداة (‏`options.cpp`،
#    `DefaultMinSdkVersionForLang`) حين لا يُمرَّر `--min_sdk_version`. واللقطة تستعمل
#    `ParcelableHolder` لتضمين الحقول المستقبليّة، وهو موجود من Android 12 — وهو نفس
#    الإصدار الذي وُلد فيه مصنع AIDL للمؤثّرات (ADR-66) ⇒ **31** هو الحدّ الصحيح لا الأكبر.
#    (ويُقبل أي إصدار ≥ الافتراضي 29؛ و`31` يستدعي `S` في جدول الأسماء نفسه.)
#    وحدّ مقيس يُعلن: الأداة تُصدر في الكود المُولَّد `#if __ANDROID_API__ >= 31` حول
#    مسار `fromBinder` (‏`generate_ndk.cpp:875`)، و`__ANDROID_API__` يأتي من `APP_PLATFORM`
#    في `Application.mk` — وقد رُفع هناك إلى `android-31`، وسببه وثمنه مُعلنان فيه.
#
# ⑤ وفي الخامس (`37021189677`) نجح التوليد كلّه — `GEN=ok … headers=282` — وسقط البناء بعده،
#    ونصّه:
#       fatal error: 'android/binder_interface_utils.h' file not found
#    وهذا الرأس يُضمِّنه **الكود المُولَّد** نفسه (‏`GenerateHeaderIncludes`)، وقد أُخرج من
#    الـsysroot في r28 — و**قِيس** في `37022847814` و`37023690115` أنّه ليس في NDK r29 في
#    أيّ موضع (لا `platforms/` ولا `optional/`)، فحُلَّ بالنسخ في
#    `third_party/libbinder_ndk_cpp` (القياس كاملًا في `README` تلك الشجرة).
#    فالدرس المنقول هنا: نجاح التوليد **لا يعني** نجاح الترجمة — والبوابة التي تُسمّي الرموز
#    الخمسة هي التي ترى الفرق؛ ودرس ثانٍ: ما يُنقل عن تخطيط أداةٍ لا يُغني عن قراءة تقديمها.
#
# ونداءٌ واحد على كل الملفّات: الـ`aidl` يحلّ الاستيراد بين الحِزم من `-I`، والنسخ المجمَّدة
# تستعمل أسماءً مؤهَّلة بلا `import` — فالتقسيم إلى دفعات يفشل عند أوّل حزمةٍ ناقصة.
find "$root/aidl" -name '*.aidl' -print0 |
    xargs -0 -n 400 "$tool" --lang=ndk --structured --stability=vintf --min_sdk_version=31 \
        -I"$root/aidl" --out="$out" --header_out="$out"

# ── توحيد التخطيط: `aidl/` هما بادئة الاستدعاء في الكود المُولَّد نفسه ──
#
# الكود المُولَّد يُضمِّن `#include <aidl/…/BnEffect.h>`، فالمجلَّد المارَّر إلى `-I` يجب أن
# يحتوي شجرةً اسمها `aidl/`. ولو كتب المُصرِّف الشجرة بدونها، فنُعيد ترتيبها هنا بأمرٍ
# صريح — لأنّ بناءً يبحث عن `<aidl/…>` ولا يجده يسقط برسالة لا تدلّ على سببها.
if [ ! -d "$out/aidl" ] && [ -d "$out/android" ]; then
    mkdir -p "$out/aidl"
    mv "$out/android" "$out/aidl/android"
    echo "GEN=normalized added=/aidl prefix"
fi

header="$out/aidl/android/hardware/audio/effect/BnEffect.h"
if [ ! -f "$header" ]; then
    # لا يُدَّعى نجاح: الملفّ المطلوب يُذكر باسمه، وما وُجد يُطبع للمقارنة.
    echo "GEN=failed reason=missing-header expected=$header"
    find "$out" -name 'BnEffect.h' -o -name 'IEffect.h' | head -5
    exit 1
fi

generated=$(find "$out" -name '*.h' | wc -l | tr -d ' ')
echo "GEN=ok tool=$tool inputs=$count headers=$generated out=$out"
echo "GEN=header $header"
