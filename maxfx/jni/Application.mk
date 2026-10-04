# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
#
# **العمودان معًا** (قرار المالك، تكملة ١١٠): `arm64-v8a` و`armeabi-v7a` — مكتبةُ مؤثّرٍ
# تُشحن في الـAPK وتُنقل إلى `soundfx` عند التثبيت، فجهازٌ 32-بت بلا نسخةٍ يعود إلى
# «الميزة لا تعمل» بحجةٍ غامضة — وهو بالضبط ما تمنعه هذه الحزمة.
APP_ABI := arm64-v8a armeabi-v7a
APP_OPTIM := release

# ── `APP_PLATFORM` صار **31** — وكان 29، والرفع مقيس لا تفضيل ──
#
# سببه مُقاس في خطأين حقيقيّين في CI لا مُتخيَّل:
#
# ① **الوظيفة:** `31` هو الإصدار الذي وُلد فيه مصنع AIDL للمؤثّرات (ADR-66)، وهو نفس ما
#    يطلبه `ParcelableHolder` في لقطاتنا المجمَّدة — وهو نصّ خطأ المُصرّف المقيس:
#    «ParcelableHolder is available since SDK = 31.» (التشغيل `37020305510`).
#    و`--min_sdk_version=31` في `aidl/gen.sh` يقول الشيء نفسه من جهة المولِّد.
#
# ② **والحصانة:** الـsysroot في NDK r29 يُشحَن `libbinder_ndk.so` للإصدارات 29–35 فقط
#    (قِيس بمسح فهرس حزمة r29: `sysroot/usr/lib/<abi>/{29…35}/libbinder_ndk.so`، ولا 36).
#    فـ`31` داخل المدى المضمون للربط، ولا يُقفز إلى ما لا يُشحَن.
#
# ③ وحدّ مُعلَن: اختيار 31 **لا يُغني** عن الرؤوس — فهي غائبة عن الـNDK كليًّا، ومنسوخة في
#    `third_party/libbinder_ndk_cpp` (القياس في `Android.mk` و`README` تلك الشجرة).
#
# ⚠️ **الثمن مُعلَن:** المكتبة كلّها تُوسم الآن بالجيل 12+، فمسار `AELI` على أجهزة ≤11
#    يبقى **مُصدَّرًا** في الرموز لكنّ تحميله عليها لم يعد مضمونًا كالسابق (المكتبة تُشير
#    إلى رموز API 31). وهذا **حدّ عتاد يُحتاج جهازًا لقياسه**، ولا يُدَّعى. وفي المقابل
#    جهازُ المالك Android 16 — والمصنع الذي يقرأ العقد هنا هو مصنع AIDL.
APP_PLATFORM := android-31

# `c++_shared` لأنّ غلاف AIDL يستعمل `libbinder_ndk` وهي مبنيّة على `libc++_shared` —
# وربطُ `c++_static` معها يُنتج نسختين من الـSTL في عمليّة `audioserver` (عطبٌ صامت).
# والحدّ مُعلن: هذا يجعل المكتبة تعتمد على `libc++_shared.so`، **وهي موجودة في كل ROM**
# (تشحنها `libbinder` نفسها) — فلا تُضيف شرطًا على أيّ جهاز.
APP_STL := c++_shared
