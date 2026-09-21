#!/system/bin/sh

#
# Copyright (C) 2026-2027 Zexshia
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

readonly MODDIR="${0%/*}"
readonly BIN_SVC="$MODDIR/system/bin/sys.maxmanager-service"

readonly APK_COMP="$MODDIR/system/product/priv-app/MaxManager/MaxManager.apk"

# Check if app is installed
_app_installed() {
	pm path nd.max >/dev/null 2>&1
}

# Check if service binary exists
_service_exists() {
	[ -f "$BIN_SVC" ]
}

# The APK is a systemless priv-app mounted from the module system tree,
# so installation is not an action concern anymore: just launch it.
clear

if ! _service_exists; then
	echo "[!] Service binary not found at $BIN_SVC" >&2
	exit 1
fi

if ! _app_installed; then
	if pm list packages -u 2>/dev/null | grep -qx 'package:nd.max'; then
		echo "[*] Restoring MaxManager for user 0..."
		cmd package install-existing --user 0 nd.max
	fi
fi

# نسخة تطبيق-مستخدم إلى جانب نسخة الـpriv-app: مديرو الروت (KernelSU Next · APatch · Magisk)
# يعرضون تطبيقات المستخدم في قوائمهم، فتغيب الحزمة عن قائمة منح الإذن إن كانت **تطبيق نظام فقط**.
# وزر الوحدة هو المكان الطبيعي لإصلاح ذلك بلا إعادة تفليش: التثبيت مقيَّد بغياب النسخة وبنفس
# ملف الـAPK، فيصير التطبيق «تطبيق نظام مُحدَّث» ويظهر مع التطبيقات العادية.
if _app_installed && ! pm path nd.max 2>/dev/null | grep -q '/data/app/' && [ -f "$APK_COMP" ]; then
	echo "[*] Making MaxManager visible to root managers (user-app copy)..."
	cp "$APK_COMP" /data/local/tmp/MaxManager.apk
	chmod 644 /data/local/tmp/MaxManager.apk
	pm install -r -d --user 0 /data/local/tmp/MaxManager.apk >/dev/null 2>&1 \
		|| echo "[!] User-app copy not installed (root may be required)."
	rm -f /data/local/tmp/MaxManager.apk
fi

if _app_installed; then
	echo "[*] Launching MaxManager..."
	exec "$BIN_SVC" --appactivity
fi

if [ -f "$APK_COMP" ]; then
	echo "[!] MaxManager is present in the module but PackageManager has not scanned it." >&2
	echo "[!] Reboot once so the module tree mounts into /product, then try Open again." >&2
else
	echo "[!] MaxManager APK is missing from the module tree." >&2
fi
exit 1
