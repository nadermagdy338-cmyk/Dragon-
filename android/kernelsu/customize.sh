#!/system/bin/sh
# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.

ui_print "***************************************"
ui_print "        MaxManager"
ui_print "   KernelSU Next / Magisk Module"
ui_print "   Intelligent Performance Management"
ui_print "***************************************"

if [ "${KSU:-false}" = "true" ]; then
    MANAGER="KernelSU Next"
    MANAGER_VERSION="${KSU_VER:-unknown}"
elif [ -n "${MAGISK_VER_CODE:-}" ]; then
    MANAGER="Magisk"
    MANAGER_VERSION="${MAGISK_VER:-unknown}"
else
    abort "! Install from KernelSU Next Manager or the Magisk app"
fi

ui_print "- Manager: $MANAGER $MANAGER_VERSION"

if [ "$BOOTMODE" != "true" ]; then
    abort "! Install from the root manager while Android is running"
fi

if [ "$ARCH" != "arm64" ]; then
    abort "! MaxManager requires an arm64 device"
fi

# Check required files
# الاسم الرسمي الوحيد للـdaemon هو `sys.maxmanager-service` ومكانه في الموديول `system/bin/`
# (يطابق ما يبنيه `archdaemon/jni/Android.mk` ويشغّله `service.sh`). ولا `bin/maxmanager_daemon`.
[ -f "$MODPATH/system/bin/sys.maxmanager-service" ] || abort "! Missing MaxManager daemon"
[ -f "$MODPATH/app/MaxManager.apk" ] || abort "! Missing MaxManager APK"
[ -f "$MODPATH/lib/libmaxmanager_native.so" ] || abort "! Missing native library"

# Detect if ROM-native daemon exists
MAXMANAGER_NATIVE_MODE=0
if [ -f "/system/bin/sys.maxmanager-service" ] || [ -f "/vendor/bin/sys.maxmanager-service" ]; then
    MAXMANAGER_NATIVE_MODE=1
    touch "$MODPATH/rom-native-mode"
    ui_print "- ROM-native daemon detected. Using existing daemon."
else
    rm -f "$MODPATH/rom-native-mode"
fi

# Skip mount for KernelSU (no system partition modification)
touch "$MODPATH/skip_mount"

# Set permissions
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755
set_perm_recursive "$MODPATH/app" 0 0 0755 0644
set_perm "$MODPATH/customize.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

if [ "$MAXMANAGER_NATIVE_MODE" -eq 1 ]; then
    ui_print "- Installing as update layer over ROM-native daemon"
else
    ui_print "- Installing standalone MaxManager module"
fi

ui_print "- Installation completed successfully!"