#!/system/bin/sh

MODDIR=${0%/*}
MAXMANAGER_CTL="$MODDIR/bin/maxmanager_ctl"

echo "MaxManager"
echo ""

if [ -f "$MODDIR/rom-native-mode" ]; then
    echo "Backend mode: ROM-native update layer"
else
    echo "Backend mode: standalone root module"
fi

if /system/bin/pm path nd.max >/dev/null 2>&1; then
    echo "App: installed"
else
    echo "App: missing; flash the module again from your root manager"
fi

MAXMANAGER_PIDS="$(pidof maxmanager_daemon 2>/dev/null)"
if [ -z "$MAXMANAGER_PIDS" ]; then
    echo "Daemon: waiting for activation"
    echo "Install the module and reboot when you are ready."
    exit 0
fi

if [ -f "$MAXMANAGER_CTL" ]; then
    MAXMANAGER_PING="$($MAXMANAGER_CTL PING 2>&1)"
    case "$MAXMANAGER_PING" in
        "OK PONG "*)
            echo "Daemon: healthy"
            echo "Daemon PID: $MAXMANAGER_PIDS"
            echo "Protocol: ${MAXMANAGER_PING#OK PONG }"
            ;;
        *)
            echo "Daemon: running but did not answer"
            echo "$MAXMANAGER_PING"
            exit 1
            ;;
    esac
else
    echo "Daemon: running (ctl not available)"
fi

# Get snapshot from daemon if available
if [ -f "$MAXMANAGER_CTL" ]; then
    MAXMANAGER_SNAPSHOT="$($MAXMANAGER_CTL GET snapshot 2>/dev/null)"
    echo "$MAXMANAGER_SNAPSHOT" | tr ';' '\n' | grep -E '^(cpu|thermal|battery|profile)=' 2>/dev/null
fi
