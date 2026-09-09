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

readonly LAUNCHER_STATE="$MODULE_CONFIG/launcher_visibility"
readonly LEGACY_LAUNCHER_MARKER="$MODULE_CONFIG/.launcher_enabled"
readonly RECOVERY_LOG="$MODULE_CONFIG/package-recovery.log"

log_recovery() {
    printf '%s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" >> "$RECOVERY_LOG"
}

package_installed() {
    pm path nd.max >/dev/null 2>&1
}

package_known() {
    pm list packages -u 2>/dev/null | grep -qx 'package:nd.max'
}

# PackageManager may finish scanning product apps after boot_completed. Keep
# mount state separate from package state so a mounted but rejected APK does
# not suppress recovery.
PACKAGE_READY=0
attempt=0
while [ "$attempt" -lt 30 ]; do
    if package_installed; then
        PACKAGE_READY=1
        break
    fi
    attempt=$((attempt + 1))
    sleep 2
done

if [ "$PACKAGE_READY" -eq 0 ]; then
    if [ -f /product/priv-app/MaxManager/MaxManager.apk ]; then
        log_recovery "product APK mounted but package unavailable after scan wait"
    else
        log_recovery "product APK not mounted after scan wait"
    fi

    if package_known; then
        log_recovery "package known; attempting install-existing for user 0"
        cmd package install-existing --user 0 nd.max >> "$RECOVERY_LOG" 2>&1
        package_installed && PACKAGE_READY=1
    fi
fi

if [ "$PACKAGE_READY" -eq 0 ] && [ -f "$APK_COMP" ]; then
    log_recovery "installing degraded user-app fallback"
    cp "$APK_COMP" /data/local/tmp/MaxManager.apk
    chmod 644 /data/local/tmp/MaxManager.apk
    pm install -r -d --user 0 /data/local/tmp/MaxManager.apk >> "$RECOVERY_LOG" 2>&1
    rm -f /data/local/tmp/MaxManager.apk
    package_installed && PACKAGE_READY=1
fi

if [ "$PACKAGE_READY" -eq 1 ]; then
    log_recovery "package available path=$(pm path nd.max 2>/dev/null | tr '\n' ' ')"

    # The privileged allowlist is framework-owned. These runtime permissions
    # and appops are applied only after package recovery succeeds.
    pm grant nd.max android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
    pm grant nd.max android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1
    pm grant nd.max android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1
    appops set nd.max SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
    appops set nd.max WRITE_SETTINGS allow >/dev/null 2>&1

    # Persist explicit user intent rather than trusting a marker that can drift
    # away from PackageManager's component state. Missing state migrates to the
    # visible default; hidden is always respected.
    launcher_visibility=$(cat "$LAUNCHER_STATE" 2>/dev/null)
    case "$launcher_visibility" in
        hidden)
            if pm disable --user 0 nd.max/.Launcher >/dev/null 2>&1; then
                rm -f "$LEGACY_LAUNCHER_MARKER"
            fi
            ;;
        shown)
            if pm enable --user 0 nd.max/.Launcher >/dev/null 2>&1; then
                rm -f "$LEGACY_LAUNCHER_MARKER"
            fi
            ;;
        *)
            if pm enable --user 0 nd.max/.Launcher >/dev/null 2>&1; then
                printf '%s\n' shown > "$LAUNCHER_STATE"
                chmod 600 "$LAUNCHER_STATE" 2>/dev/null
                rm -f "$LEGACY_LAUNCHER_MARKER"
            else
                log_recovery "launcher migration failed"
            fi
            ;;
    esac
else
    log_recovery "package recovery failed; app companion not started"
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

# Exec Java Companion Daemon only when PackageManager can resolve the app.
if [ "$PACKAGE_READY" -eq 1 ]; then
    nohup app_process -Djava.class.path="$APK_MOUNTED" / \
        --nice-name=sys.maxmanager-appmonitoring nd.max.AppMonitor \
        "$MODULE_CONFIG/app_status" \
        "$MODULE_CONFIG/background_apps" \
        "$MODULE_CONFIG/java.lock" >"$MODULE_CONFIG/sysmon.log" 2>&1 &
fi

# Run MaxManager service
sleep 1 && exec "$BIN_SVC" --run
