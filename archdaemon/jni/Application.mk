# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
# **العمودان معًا (قرار المالك، تكملة ١١٠ — عكس تكملة ٨٢):** هواتف 32-بت مدعومة كاملة،
# وانقلب الحرس: كان `arm64-v8a` وحده والمنصّب يرفض 32-بت، واليوم يُبنى الاثنان والمنصّب
# يختار بحسب `ARCH` (`mainfiles/customize.sh`)، وCI يرفض حزمة يغيب منها أحد العمودين.
APP_ABI := arm64-v8a armeabi-v7a
APP_PLATFORM := android-29
APP_OPTIM := release
