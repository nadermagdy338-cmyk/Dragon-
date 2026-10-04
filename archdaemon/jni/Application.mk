# Copyright (C) 2024-2025 Rem01Gaming x Zexshia
#
# Derived from Encore Tweaks (https://github.com/Rem01Gaming/encore),
# modified for MaxManager (modifications: Copyright (C) 2026 Nader Magdy).
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# **العمودان معًا (قرار المالك، تكملة ١١٠ — عكس تكملة ٨٢):** هواتف 32-بت مدعومة كاملة،
# وانقلب الحرس: كان `arm64-v8a` وحده والمنصّب يرفض 32-بت، واليوم يُبنى الاثنان والمنصّب
# يختار بحسب `ARCH` (`mainfiles/customize.sh`)، وCI يرفض حزمة يغيب منها أحد العمودين.
APP_ABI := arm64-v8a armeabi-v7a
APP_PLATFORM := android-29
APP_OPTIM := release
