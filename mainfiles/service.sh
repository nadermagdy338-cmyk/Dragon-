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
readonly APK_COMP="$MODDIR/MaxManager.apk"

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
nohup app_process -Djava.class.path="$APK_COMP" / \
    --nice-name=sys.maxmanager-appmonitoring nd.max.AppMonitor \
    "$MODULE_CONFIG/app_status" \
    "$MODULE_CONFIG/background_apps" \
    "$MODULE_CONFIG/java.lock" >"$MODULE_CONFIG/sysmon.log" 2>&1 &

# Run MaxManager service
sleep 1 && exec "$BIN_SVC" --run
