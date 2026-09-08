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
readonly MODULE_CONFIG="/data/adb/.config/MaxManager"
readonly BIN_SVC="$MODDIR/system/bin/sys.maxmanager-service"
readonly APK_COMP="$MODDIR/system/product/priv-app/MaxManager/MaxManager.apk"
# The app mounts as a priv-app from the module system tree; MODDIR works
# for Magisk, but KSU/APatch soft-reboots can re-point script locations,
# so resolve the APK through the live /product mount instead.
APK_MOUNTED="/product/priv-app/MaxManager/MaxManager.apk"
[ -f "$APK_MOUNTED" ] || APK_MOUNTED="$APK_COMP"

# shellcheck source=props.sh
. "$MODDIR/props.sh"

# Wait boot to complete
until [ "$(getprop sys.boot_completed)" = "1" ]; do 
    sleep 1
done

# Remove Single Instance since we dont need it anymore
rm -f /dev/.maxmanagerSingleInstance

# Reset anti bootloop
echo "BOOTCOUNT=0" > "$MODDIR/count.sh"

# Clear Old Logs
"$BIN_SVC" --clearlogs

# Grant runtime appop permissions to the priv-app. The privileged
# allowlist (WRITE_SECURE_SETTINGS) is granted by the framework at boot
# from the overlaid privapp-permissions XML; these appops cannot be
# part of that file, so they are set here — after the package is
# scanned — and survive reboots because the module re-runs each boot.
GRANT_PKGS="nd.max"
for pkg in $GRANT_PKGS; do
    pm grant "$pkg" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
    pm grant "$pkg" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1
    pm grant "$pkg" android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1
    appops set "$pkg" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
    appops set "$pkg" WRITE_SETTINGS allow >/dev/null 2>&1
done

# Reveal the launcher alias exactly once per install lifecycle. The
# alias is disabled by default in the manifest and the app manages it
# itself (SettingsScreen/GetStartedScreen "hide icon" toggles), so an
# unconditional per-boot enable would override the user's choice. The
# marker lives in $MODULE_CONFIG, which persists across module updates
# and is wiped on uninstall — in lockstep with the persisted component
# state.
if [ ! -f "$MODULE_CONFIG/.launcher_enabled" ]; then
    pm enable --user 0 nd.max/.Launcher >/dev/null 2>&1 \
        && touch "$MODULE_CONFIG/.launcher_enabled"
fi

# Safety net for root managers that do not overlay the module system
# tree onto /product (older KernelSU/APatch builds): install the APK as
# a plain user app instead. Degraded mode — WRITE_SECURE_SETTINGS stays
# ungranted since the privapp XML is not read for user apps — but the
# app and all root-driven features keep working. Idempotent: skipped
# when the overlay worked or the package already exists.
if [ ! -f /product/priv-app/MaxManager/MaxManager.apk ] \
   && ! pm path nd.max >/dev/null 2>&1; then
    if [ -f "$APK_COMP" ]; then
        cp "$APK_COMP" /data/local/tmp/MaxManager.apk
        chmod 644 /data/local/tmp/MaxManager.apk
        pm install -r -d --user 0 /data/local/tmp/MaxManager.apk >/dev/null 2>&1
        rm -f /data/local/tmp/MaxManager.apk
    fi
fi

# Remove reboot flag
if [ -f "$MODDIR/reboot" ]; then
    rm -f "$MODDIR/reboot"
fi

# Refresh MaxManager daemon state
STATE=$(getprop "$PROP_STATE")
{ [ -z "$STATE" ] || { [ "$STATE" = "running" ] && [ -z "$(/system/bin/toybox pidof sys.maxmanager-service)" ]; }; } && {
    setprop "$PROP_STATE" stopped
    setprop "$PROP_SERVICE" ""
}

# Exec Java Companion Daemon
nohup app_process -Djava.class.path="$APK_MOUNTED" / \
    --nice-name=sys.maxmanager-appmonitoring nd.max.AppMonitor \
    "$MODULE_CONFIG/app_status" \
    "$MODULE_CONFIG/background_apps" \
    "$MODULE_CONFIG/java.lock" >"$MODULE_CONFIG/sysmon.log" 2>&1 &

# Run MaxManager service
sleep 1 && exec "$BIN_SVC" --run
