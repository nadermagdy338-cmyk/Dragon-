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

SKIPUNZIP=1

# Paths
MODULE_CONFIG="/data/adb/.config/MaxManager"
device_codename=$(getprop ro.product.board)
chip=$(getprop ro.hardware)
HM_DIR="/data/adb/hybrid-mount"
HM_CONFIG="$HM_DIR/config.toml"
API_LEVEL=$(getprop ro.build.version.sdk)
readonly APK_COMP="$MODPATH/system/product/priv-app/MaxManager/MaxManager.apk"

# Create File
make_node() {
	[ ! -f "$2" ] && echo "$1" >"$2"
}

# Set a persist prop to a default value, only if it isn't already set —
# collapses the `if [ -z "$(getprop KEY)" ]; then setprop KEY VAL; fi`
# pattern that used to be repeated by hand for every simple on/off or
# single-value default below. Props whose default also needs writing a
# companion file under $MODULE_CONFIG (freqoffset, bypass*) keep their own
# block since they do more than just set the prop.
set_default_prop() {
	key="$1"
	val="$2"
	[ -z "$(getprop "$key")" ] && setprop "$key" "$val"
}


abort_api() {
  echo ""
  echo "! Installation Aborted"
  echo "! Unsupported Android Version Detected"
  echo "! MaxManager requires Android 10 (API 29) or newer."
  abort "! Your device is currently running API $API_LEVEL."
}

abort_corrupted() {
  clear
  echo ""
  echo "! Installation Aborted"
  echo "! The MaxManager package appears to be corrupted or incomplete."
  echo "! Required installation files were not found."
  echo ""
  abort "! Please re-download the module and try again."
}

abort_arch() {
  clear
  echo "! Installation Aborted"
  echo "! Unsupported CPU Architecture Detected"
  echo "! Your device architecture is not compatible with this build of MaxManager."
  echo "! Supported architectures:"
  abort "  • arm64-v8a\n  • armeabi-v7a"
}

installation_complete() {
  echo "- MaxManager has been successfully installed"
  echo "- Thank you for choosing MaxManager!"
  echo "- Please reboot your device."
  echo "- Open Manager from Action"
  echo "- Don't forget to grant root access."
}

# Displaybanner
echo ""
echo "              MaxManager              "
echo ""
echo "- Installing MaxManager..."

# API Level Check (Require API 29+)
[ "$API_LEVEL" -lt 29 ] && abort_api

# Extract Module Directiories
mkdir -p "$MODULE_CONFIG"
mkdir -p "$MODULE_CONFIG/debug"
mkdir -p "$MODULE_CONFIG/API"
mkdir -p "$MODULE_CONFIG/preload"
mkdir -p "$MODULE_CONFIG/bypasschgconfig"
mkdir -p "$MODULE_CONFIG/gamelist"
mkdir -p "$MODPATH/system/bin"
echo "- Create module config"

# Flashable integrity checkup
echo "- Extracting verify.sh"
unzip -o "$ZIPFILE" 'verify.sh' -d "$TMPDIR" >&2
[ ! -f "$TMPDIR/verify.sh" ] && abort_corrupted
source "$TMPDIR/verify.sh"

# Target architecture detection
case $ARCH in
"arm64") ARCH_TMP="arm64-v8a" ;;
# **٣٢-بت مدعوم كاملًا (قرار المالك، تكملة ١١٠ — عكس تكملة ٨٢):** لـ`armeabi-v7a`
# ثنائياته الخمسة في `libs/armeabi-v7a/` تمامًا كما لـ64-بت (CI يتحقّق من الوجودين)،
# فيُستخرج ويُركَّب بالاسم نفسه — لا فرع خاص ولا استثناء.
"arm") ARCH_TMP="armeabi-v7a" ;;
*) abort_arch ;;
esac

echo "- Extracting binaries for $ARCH_TMP..."
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.maxmanager-service" "$TMPDIR"
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.maxmanager-profilesettings" "$TMPDIR"
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.maxmanager-rianixiathermalcore" "$TMPDIR"
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.maxmanager-utilityconf" "$TMPDIR"
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.maxmanager-preloadbin" "$TMPDIR"
cp "$TMPDIR/libs/$ARCH_TMP/"* "$MODPATH/system/bin/"
rm -rf "$TMPDIR/libs"
echo "- All binaries installed successfully"

# Extract Module standard files
echo "- Extracting props.sh..."
extract "$ZIPFILE" props.sh "$MODPATH"
# shellcheck source=props.sh
. "$MODPATH/props.sh"
echo "- Extracting service.sh..."
extract "$ZIPFILE" service.sh "$MODPATH"
echo "- Extracting post-fs-data.sh..."
extract "$ZIPFILE" post-fs-data.sh "$MODPATH"
echo "- Extracting action.sh..."
extract "$ZIPFILE" action.sh "$MODPATH"
echo "- Extracting preferenced-tweaks.sh..."
extract "$ZIPFILE" preferenced-tweaks.sh "$MODPATH"
echo "- Extracting module.prop..."
extract "$ZIPFILE" module.prop "$MODPATH"
cp "$MODPATH/module.prop" "$MODPATH/module.prop.orig"
echo "- Extracting uninstall.sh..."
extract "$ZIPFILE" uninstall.sh "$MODPATH"
if [ ! -f "$MODULE_CONFIG/gamelist/maxmanagerApplist.json" ]; then
    echo "- Extracting Applist.json..."
    extract "$ZIPFILE" maxmanagerApplist.json "$MODULE_CONFIG/gamelist"
fi
echo "- Extracting module banner..."
extract "$ZIPFILE" module.banner.avif "$MODPATH"

# Skip mountify
touch "$MODPATH/skip_mountify"

# Skip hybrid mount
if [ -f "$HM_CONFIG" ]; then
    echo "- Hybrid Mount detected, configuring rules..."

    HM_BIN=""
    if [ -x "/data/adb/modules/hybrid_mount/hybrid-mount" ]; then
        HM_BIN="/data/adb/modules/hybrid_mount/hybrid-mount"
    elif command -v hybrid-mount >/dev/null 2>&1; then
        HM_BIN="hybrid-mount"
    fi

    if [ -n "$HM_BIN" ]; then
        echo "- Found Hybrid Mount CLI at: $HM_BIN"
        $HM_BIN api config-patch --patch '{"rules":{"MaxManager":{"default_mode":"ignore"}}}' --apply-runtime >/dev/null 2>&1
        echo "- Runtime policy for MaxManager updated to 'ignore'."
    else
        echo "- Warning: CLI binary not found in standard paths. Skipping live patch."
    fi

    if ! grep -q "\[rules\.MaxManager\]" "$HM_CONFIG"; then
        echo "" >> "$HM_CONFIG"
        echo "[rules.MaxManager]" >> "$HM_CONFIG"
        echo 'default_mode = "ignore"' >> "$HM_CONFIG"
        echo "- Permanent rule added to config.toml."
    fi
else
    echo "- Hybrid Mount is not installed. Skipping configuration."
fi

# Use Symlink for APatch / KernelSU
if [ "$KSU" = "true" ] || [ "$APATCH" = "true" ]; then
	# IMPORTANT: system/product/priv-app is the delivery mechanism for the
	# manager APK on every root solution — it must be overlay-mounted, so
	# no skip_mount here. Only the bin/ symlinks are KSU/AP-specific.
	echo "- KSU/AP detected, keeping module mount for system tree"
	# symlink ourselves on $PATH
	manager_paths="/data/adb/ap/bin /data/adb/ksu/bin"
	BIN_PATH="/data/adb/modules/MaxManager/system/bin"
	for dir in $manager_paths; do
		[ -d "$dir" ] && {
			echo "- Creating symlink in $dir"
			ln -sf "$BIN_PATH/sys.maxmanager-service" "$dir/sys.maxmanager-service"
			ln -sf "$BIN_PATH/sys.maxmanager-service" "$dir/zx" # Binary calls for CLI
			ln -sf "$BIN_PATH/sys.maxmanager-profilesettings" "$dir/sys.maxmanager-profilesettings"
			ln -sf "$BIN_PATH/sys.maxmanager-utilityconf" "$dir/sys.maxmanager-utilityconf"
			ln -sf "$BIN_PATH/sys.maxmanager-preloadbin" "$dir/sys.maxmanager-preloadbin"
            ln -sf "$BIN_PATH/sys.maxmanager-rianixiathermalcore" "$dir/sys.maxmanager-rianixiathermalcore"
		}
	done
fi

# Apply Tweaks Based on Chipset
echo "- Checking device soc"
chipset=$(grep -i 'hardware' /proc/cpuinfo | uniq | cut -d ':' -f2 | sed 's/^[ \t]*//')
[ -z "$chipset" ] && chipset="$(getprop ro.board.platform) $(getprop ro.hardware)"
case "$(echo "$chipset" | tr '[:upper:]' '[:lower:]')" in
*mt* | *MT*)
	soc="MediaTek"
	echo "- Applying Tweaks for $soc"
	setprop "$PROP_SOC_TYPE" 1
	;;
*sm* | *qcom* | *SM* | *QCOM* | *Qualcomm* | *sdm* | *snapdragon*)
	soc="Snapdragon"
	echo "- Applying Tweaks for $soc"
	setprop "$PROP_SOC_TYPE" 2
	;;
*exynos* | *Exynos* | *EXYNOS* | *universal* | *samsung* | *erd* | *s5e*)
	soc="Exynos"
	echo "- Applying Tweaks for $soc"
	setprop "$PROP_SOC_TYPE" 3
	;;
*Unisoc* | *unisoc* | *ums*)
	soc="Unisoc"
	echo "- Applying Tweaks for $soc"
	setprop "$PROP_SOC_TYPE" 4
	;;
*gs* | *Tensor* | *tensor*)
	soc="Tensor"
	echo "- Applying Tweaks for $soc"
	setprop "$PROP_SOC_TYPE" 5
	;;
*)
	soc="Unknown"
	echo "- Applying Tweaks for $chipset"
	setprop "$PROP_SOC_TYPE" 0
	;;
esac

# Soc Type
# 1) MediaTek
# 2) Snapdragon
# 3) Exynos
# 4) Unisoc
# 5) Tensor
# 0) Unknown

# Set default freqoffset
if [ -z "$(getprop "$PROP_CONF_FREQ_OFFSET")" ]; then
	setprop "$PROP_CONF_FREQ_OFFSET" "Disabled"
	touch "$MODULE_CONFIG/freqoffset"
	echo "Disabled" > "$MODULE_CONFIG/freqoffset"
fi

# Set default color scheme if not set
set_default_prop "$PROP_CONF_SCHEME_CONFIG" "1000 1000 1000 1000"

# Initiate bypasspath default value
if [ -z "$(getprop "$PROP_CONF_BYPASS_PATH")" ]; then
	setprop "$PROP_CONF_BYPASS_PATH" "NEED_SETUP"
	touch "$MODULE_CONFIG/bypasschgconfig/bypasspath"
	echo "NEED_SETUP" > "$MODULE_CONFIG/bypasschgconfig/bypasspath"
fi

# Initiate bypasspath default value
if [ -z "$(getprop "$PROP_CONF_BYPASS_CHARGE_THRESHOLD")" ]; then
	setprop "$PROP_CONF_BYPASS_CHARGE_THRESHOLD" "20"
	touch "$MODULE_CONFIG/bypasschgconfig/bypasschgthreshold"
	echo "20" > "$MODULE_CONFIG/bypasschgconfig/bypasschgthreshold"
fi

# Initiate bypasscharging state
if [ -z "$(getprop "$PROP_CONF_BYPASS_CHARGE")" ]; then
	setprop "$PROP_CONF_BYPASS_CHARGE" "0"
	touch "$MODULE_CONFIG/bypasschgconfig/bypasschg"
	echo "0" > "$MODULE_CONFIG/bypasschgconfig/bypasschg"
fi

# Daemon Configurations
set_default_prop "$PROP_CONF_SHOW_TOAST" 0
set_default_prop "$PROP_PROFILE_NOTIFICATIONS" 1
set_default_prop "$PROP_DROP_FOREGROUND" 0
set_default_prop "$PROP_DISABLE_TWEAK" 0
set_default_prop "$PROP_CONF_IO_SCHED" 1
set_default_prop "$PROP_CONF_RENDERER" default
set_default_prop "$PROP_CONF_PRELOAD_BUDGET" 500M

if [ -z "$(getprop "$PROP_CONF_AI_ENABLED")" ]; then
    # MAX AI معطل افتراضيًا: التحكم اليدوي هو الحالة الأولى للمستخدم.
    # التفعيل قرار صريح منه (شاشة MAX AI أو الإعدادات).
    echo "- Setting MAX AI off by default (manual control)"
    setprop "$PROP_CONF_AI_ENABLED" 0
    echo 0 > "$MODULE_CONFIG/API/current_modes"
fi

echo "- Disable Debugmode"
setprop "$PROP_DEBUG_MODE" "false"

# Set config properties to use
echo "- Setting config properties..."
props="
$PROP_CONF_LOGD
$PROP_CONF_DYNAMIC_THERMAL
$PROP_CONF_SFL
$PROP_CONF_MALI_SCHED
$PROP_CONF_FPS_GED
$PROP_CONF_SCHED_TUNES
$PROP_CONF_CLEAR_BG
$PROP_CONF_AUTO_PRELOAD
$PROP_CONF_CPU_LIMIT
$PROP_CONF_DND
$PROP_CONF_JUST_IN_TIME
$PROP_CONF_DISABLE_TRACE
$PROP_CONF_THERMAL_CORE
$PROP_CONF_WALT_TUNES
$PROP_CONF_FSTRIM
$PROP_CONF_USE_FPSGO
"
for prop in $props; do
	set_default_prop "$prop" 0
done

# Install Apps
# The APK ships as a systemless priv-app under system/product/priv-app:
# on Magisk the module tree is overlaid on /product, so the app appears
# as a real privileged system app at next boot. KernelSU/APatch users
# get the same tree via overlayfs magic mount. No pm install is needed
# on either path; runtime appop permissions are granted in service.sh
# after boot so they survive without a user-data install.
echo "- Extracting priv-app APK (checksum-verified)..."
extract "$ZIPFILE" "system/product/priv-app/MaxManager/MaxManager.apk" "$MODPATH"
echo "- Extracting privileged permissions whitelist..."
extract "$ZIPFILE" "system/product/etc/permissions/privapp-permissions-nd.max.xml" "$MODPATH"
[ -f "$MODPATH/system/product/etc/permissions/privapp-permissions-nd.max.xml" ] || abort_corrupted

# نسخة تطبيق-مستخدم بجانب نسخة الـpriv-app — لا بدلًا منها.
#
# كان هنا **حذف** أي نسخة في /data/app، والسبب كان منطقيًّا لمن يبحث عن مصدر واحد: ألّا يبقى
# تطبيق قديم يعارض نسخة النظام. لكن الثمن ظهر في مكان آخر: الحزمة صارت **تطبيق نظام فقط**،
# ومديرو الروت (KernelSU Next · APatch · Magisk) يعرضون في قوائمهم تطبيقات المستخدم — فتغيب
# الحزمة عن القائمة التي يُمنح منها إذن الروت، ويقرأ المستخدم «تطبيقي لا يظهر في su next».
#
# والحلّ هو الحالة المعيارية في أندرويد: أصل في الـpriv-app ونسخة مطابقة في قسم البيانات =
# **تطبيق نظام مُحدَّث**. يبقى `isPrivilegedApp` صحيحًا (الأصل في priv-app فلا تُفقد الصلاحيات)
# ويظهر التطبيق مع التطبيقات العادية في المشغّل وفي قوائم مديري الروت. والنسخة نفسها من نفس
# الملف، فلا تعارض إصدارات؛ و`-d` تمنع رفض التثبيت لو كانت نسخة النظام أحدث.
#
# والمفتاح `-3` لا يُعيد التثبيت عند كل تفليش: إن وُجدت النسخة فلا شيء يُفعل.
if [ -f "$MODPATH/system/product/priv-app/MaxManager/MaxManager.apk" ] \
    && ! pm path nd.max 2>/dev/null | grep -q '/data/app/'; then
    echo "- Installing user-app copy (visible to launcher and root managers)"
    cp "$MODPATH/system/product/priv-app/MaxManager/MaxManager.apk" /data/local/tmp/MaxManager.apk
    chmod 644 /data/local/tmp/MaxManager.apk
    pm install -r -d --user 0 /data/local/tmp/MaxManager.apk >/dev/null 2>&1 \
        || echo "- user-app copy not installed now; service.sh retries after boot"
    rm -f /data/local/tmp/MaxManager.apk
fi

# Remove old module files if available
echo "- Cleaning old files..."
[ -f "/data/local/tmp/module.avatar.webp" ] && rm -f "/data/local/tmp/module.avatar.webp"
if pm list packages | grep -q "maxmanager.toast"; then
    echo "- Uninstalling old components"
    pm uninstall --user 0 maxmanager.toast > /dev/null 2>&1
fi

set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755

# priv-app tree needs system ownership and read-only modes, exactly like
# a real /product/priv-app entry the framework scans at boot.
set_perm_recursive "$MODPATH/system/product" 0 0 0755 0644

installation_complete
