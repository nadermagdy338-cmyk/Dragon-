# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
#
# MaxManager — board fragment (BOARD_* only).
#
# **ولماذا صار هذا الملفّ `BOARD_*` وحده (تكملة ١٣٩):** كان يجمع `PRODUCT_PACKAGES`
# و`PRODUCT_COPY_FILES` و`BOARD_SEPOLICY_DIRS` في **ملفّ BoardConfig** الواحد. وعند
# AOSP، متغيّرات `PRODUCT_*` لا تُقرأ من `BoardConfig.mk` أصلًا: تُقرأ في مرحلة
# الـproduct، فيمرّ السطر بلا أثر ويظنّ قارئه أنه دمج التطبيق. وهو أسوأ أنواع العطب:
# صامت. فانقسم الملفّ: `BOARD_*` هنا، و`PRODUCT_*` في `product-inclusion.mk`.
#
# **والمسار** `device/maxmanager/` هو نفسه الذي تفترضه حزمة المطوّرين
# (`MaxManager-developer-bundle.zip`) — مسار واحد في الحالتين، بلا `[vendor]/[device]`
# يُنسى استبداله.

BOARD_SEPOLICY_DIRS += device/maxmanager/sepolicy

# ملاحظة: الـ`.rc` للـdaemon يُنسخ إلى `/system/etc/init/` لا إلى `vendor/etc/init/`
# — سطر `service` في `maxmanager.rc` يشير إلى `/system/bin/sys.maxmanager-service`،
# والوسم في `sepolicy/file_contexts` على المسار نفسه. ووضع الـ`.rc` في قسم آخر
# يخالف الثلاثة. (وسبب آخر: `PRODUCT_COPY_FILES` موضعه `product-inclusion.mk`.)
