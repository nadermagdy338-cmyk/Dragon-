#!/system/bin/sh

MODDIR=${0%/*}
MAXMANAGER_ROOT=/data/adb/maxmanager
MAXMANAGER_DAEMON="$MODDIR/bin/maxmanager_daemon"
MAXMANAGER_LOG="$MAXMANAGER_ROOT/daemon.log"
MAXMANAGER_WATCHDOG_PID="$MAXMANAGER_ROOT/watchdog.pid"
MAXMANAGER_WATCHDOG_LOCK="$MAXMANAGER_ROOT/watchdog.lock"
MAXMANAGER_SERVICE_PATH="$MODDIR/service.sh"
MAXMANAGER_CURRENT_BOOT_ID="$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
MAXMANAGER_PACKAGE=nd.max

export MAXMANAGER_REVERSE_IPC=1
export MAXMANAGER_LOOPBACK_IPC=1

# Detect if ROM-native daemon exists
if [ -f "$MODDIR/rom-native-mode" ]; then
    export MAXMANAGER_STATE_DIR="/data/system/maxmanager"
else
    export MAXMANAGER_STATE_DIR="$MAXMANAGER_ROOT"
fi

umask 077
mkdir -p "$MAXMANAGER_ROOT"
chmod 0700 "$MAXMANAGER_ROOT" 2>/dev/null

if [ -f "$MODDIR/rom-native-mode" ]; then
    mkdir -p "/data/system/maxmanager"
    chmod 0700 "/data/system/maxmanager" 2>/dev/null
    /system/bin/restorecon -RF "/data/system/maxmanager" 2>/dev/null || true
fi

# Rotate logs
MAXMANAGER_LOG_BYTES=0
if [ -f "$MAXMANAGER_LOG" ]; then
    MAXMANAGER_LOG_BYTES="$(wc -c <"$MAXMANAGER_LOG" 2>/dev/null)"
fi
case "$MAXMANAGER_LOG_BYTES" in
    ''|*[!0-9]*) ;;
    *)
        if [ "$MAXMANAGER_LOG_BYTES" -gt 1048576 ]; then
            mv -f "$MAXMANAGER_LOG" "$MAXMANAGER_LOG.1"
        fi
        ;;
esac

# Watchdog lock to prevent duplicate daemons
if ! mkdir "$MAXMANAGER_WATCHDOG_LOCK" 2>/dev/null; then
    MAXMANAGER_EXISTING_PID="$(cat "$MAXMANAGER_WATCHDOG_LOCK/pid" 2>/dev/null)"
    MAXMANAGER_EXISTING_BOOT_ID="$(cat "$MAXMANAGER_WATCHDOG_LOCK/boot_id" 2>/dev/null)"
    if [ -n "$MAXMANAGER_CURRENT_BOOT_ID" ] \
        && [ "$MAXMANAGER_EXISTING_BOOT_ID" = "$MAXMANAGER_CURRENT_BOOT_ID" ] \
        && [ "$MAXMANAGER_EXISTING_PID" != "$$" ] \
        && kill -0 "$MAXMANAGER_EXISTING_PID" 2>/dev/null; then
        MAXMANAGER_EXISTING_CMDLINE="$(tr '\000' ' ' <"/proc/$MAXMANAGER_EXISTING_PID/cmdline" 2>/dev/null)"
        case "$MAXMANAGER_EXISTING_CMDLINE" in
            *"$MAXMANAGER_SERVICE_PATH"*) exit 0 ;;
        esac
    fi
    rm -f "$MAXMANAGER_WATCHDOG_LOCK/pid" "$MAXMANAGER_WATCHDOG_LOCK/boot_id"
    rmdir "$MAXMANAGER_WATCHDOG_LOCK" 2>/dev/null || exit 0
    mkdir "$MAXMANAGER_WATCHDOG_LOCK" 2>/dev/null || exit 0
fi

printf '%s\n' "$$" >"$MAXMANAGER_WATCHDOG_PID"
printf '%s\n' "$$" >"$MAXMANAGER_WATCHDOG_LOCK/pid"
printf '%s\n' "$MAXMANAGER_CURRENT_BOOT_ID" >"$MAXMANAGER_WATCHDOG_LOCK/boot_id"

# Start the daemon
if [ ! -f "$MODDIR/rom-native-mode" ]; then
    if [ -f "$MAXMANAGER_DAEMON" ]; then
        "$MAXMANAGER_DAEMON" >> "$MAXMANAGER_LOG" 2>&1 &
    else
        echo "MaxManager daemon not found" >> "$MAXMANAGER_LOG"
    fi
fi

# Log startup
echo "MaxManager service started at $(date)" >> "$MAXMANAGER_LOG"