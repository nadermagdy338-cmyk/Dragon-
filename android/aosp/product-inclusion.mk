# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
#
# MaxManager — product fragment (PRODUCT_* only) for the **source-build** variant.
#
# وُجد هذا الملفّ لأن `PRODUCT_*` لا تُقرأ من `BoardConfig.mk` في AOSP (تفصيل السبب
# في `BoardConfig.mk`). وهو يقابل النمط الآخر: حزمة المطوّرين تُولِّد
# `product-inclusion.mk` بنفسها لنسخة **prebuilt**، والفرق الوحيد بينهما سطر واحد:
# `libmaxmanager_native`.
#
# **ولماذا الفرق سطر واحد فقط:** في نسخة المصادر تُبنى مكتبة الـJNI بوحدة Soong
# مستقلّة (`cc_library_shared` في `Android.bp`) فتُدرَج في `PRODUCT_PACKAGES`. وفي
# نسخة الـprebuilt تكون **داخل الـAPK** أصلًا، فإدراجها يطلب وحدة لا وجود لها ⇒ فشل
# البناء. وهذا فرق حقيقيّ بين بناءين، لا نسختان من ملفّ واحد.
#
# المسار `device/maxmanager/` — نفس الذي تفترضه الحزمة، ونفسه في `BoardConfig.mk`.

PRODUCT_PACKAGES += \
    MaxManager \
    sys.maxmanager-service \
    libmaxmanager_native

PRODUCT_COPY_FILES += \
    device/maxmanager/system/etc/init/maxmanager.rc:$(TARGET_COPY_OUT_SYSTEM)/etc/init/maxmanager.rc

# الصلاحيات المميّزة لـ`nd.max`: قائمة السماح تُطابق **باسم الحزمة**، فيجب أن تقع على
# القسم نفسه الذي يقع فيه الـAPK (`product_specific: true`). وكانت مكتوبة `nd.max.xml`
# على `system`، والملفّ الحقيقي `privapp-permissions-nd.max.xml` — أي اسم غير موجود
# وقسم غير الصحيح معًا.
PRODUCT_COPY_FILES += \
    device/maxmanager/product/etc/permissions/privapp-permissions-nd.max.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/permissions/privapp-permissions-nd.max.xml

PRODUCT_PROPERTY_OVERRIDES += \
    maxmanager.enable=1 \
    maxmanager.verbose=0
