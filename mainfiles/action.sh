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

if _app_installed; then
	if _service_exists; then
		echo "[*] Launching MaxManager..."
		exec "$BIN_SVC" --appactivity >/dev/null 2>&1
	else
		echo "[!] Service binary not found at $BIN_SVC" >&2
		exit 1
	fi
else
	echo "[*] App not scanned yet. Reboot once so the module tree mounts"
	echo "    into /product and the package manager picks up the priv-app."
	echo "[*] Launching anyway (fallback)..."
	if _service_exists; then
		exec "$BIN_SVC" --appactivity >/dev/null 2>&1
	fi
fi
