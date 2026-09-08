#!/system/bin/sh

MODDIR=${0%/*}
MAXMANAGER_ROOT=/data/adb/maxmanager
MAXMANAGER_WATCHDOG_LOCK="$MAXMANAGER_ROOT/watchdog.lock"
MAXMANAGER_WATCHDOG_PID="$(cat "$MAXMANAGER_ROOT/watchdog.pid" 2>/dev/null)"
MAXMANAGER_PACKAGE=nd.max
MAXMANAGER_NATIVE_MODE=0

[ -f "$MODDIR/rom-native-mode" ] && MAXMANAGER_NATIVE_MODE=1

# Kill watchdog and daemon
case "$MAXMANAGER_WATCHDOG_PID" in
    ''|*[!0-9]*) ;;
    *) kill "$MAXMANAGER_WATCHDOG_PID" 2>/dev/null ;;
esac

for MAXMANAGER_PROCESS_NAME in maxmanager_daemon; do
    for MAXMANAGER_PID in $(pidof "$MAXMANAGER_PROCESS_NAME" 2>/dev/null); do
        kill "$MAXMANAGER_PID" 2>/dev/null
    done
done

sleep 1
for MAXMANAGER_PROCESS_NAME in maxmanager_daemon; do
    for MAXMANAGER_PID in $(pidof "$MAXMANAGER_PROCESS_NAME" 2>/dev/null); do
        kill -9 "$MAXMANAGER_PID" 2>/dev/null
    done
done

# Clean up state files
rm -f "$MAXMANAGER_ROOT/watchdog.pid" "$MAXMANAGER_WATCHDOG_LOCK/pid" "$MAXMANAGER_WATCHDOG_LOCK/boot_id"
rmdir "$MAXMANAGER_WATCHDOG_LOCK" 2>/dev/null
rm -f "$MAXMANAGER_ROOT/daemon.log" "$MAXMANAGER_ROOT/daemon.log.1"

if [ "$MAXMANAGER_NATIVE_MODE" -eq 1 ]; then
    # ROM-native mode: revert system app updates
    /system/bin/cmd package uninstall-system-updates "$MAXMANAGER_PACKAGE" \
        >"$MAXMANAGER_ROOT/native-app-rollback.log" 2>&1 || true
    /system/bin/pm enable --user 0 "$MAXMANAGER_PACKAGE" >/dev/null 2>&1 || true
fi

exit 0