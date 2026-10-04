#!/bin/env bash
# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
# shellcheck disable=SC2035

if [ -z "$GITHUB_WORKSPACE" ]; then
	echo "This script should only run on GitHub action!" >&2
	exit 1
fi

# Make sure we're on right directory
cd "$GITHUB_WORKSPACE" || {
	echo "Unable to cd to GITHUB_WORKSPACE" >&2
	exit 1
}

# Critical files and folders — the checksum manifest is now written from this
# list, and it lives OUTSIDE the zip (see below and `.github/scripts/generatesha256.sh`).
need_integrity=(
	"mainfiles/system/bin"
	"mainfiles/libs"
	"mainfiles/META-INF"
	"mainfiles/service.sh"
	"mainfiles/preferenced-tweaks.sh"
	"mainfiles/post-fs-data.sh"
	"mainfiles/action.sh"
	"mainfiles/uninstall.sh"
	"mainfiles/module.prop"
    "mainfiles/maxmanagerApplist.json"
    "mainfiles/system/product/priv-app/MaxManager/MaxManager.apk"
    "mainfiles/system/product/etc/permissions/privapp-permissions-nd.max.xml"
    "mainfiles/props.sh"
)

# Version info
#
# **`version=` لم يعد يُعاد كتابته هنا** (تكملة ١٣٩): سلسلة الإصدار لها **مصدر واحد** هو ملف
# `version`، ويجب أن تُطابق حرفيًّا ما في `mainfiles/module.prop` و`MaxManager.h` — لأن
# `check_module_version()` في الخادم يُشغّل `grep -q '^version=%s$' module.prop`
# (`ModuleIntegrity.c:44`)، فاختلاف بايت ببايت **يُخرج الخادم عند الإقلاع**. وكان هذا السطر
# يكتب `version=5.2 (1823-…-Dazzling)` — أي يخلق مصدرًا ثانيًا للحقيقة في نفس الملفّ الذي
# تُطالب به بوابة الـworkflow. الآن يُتحقّق من الثلاثة في الـworkflow، ولا يُكتب إلّا
# **الرقم التسلسلي** (وهو مُشتقّ من الالتزامات ولا يقرأه الخادم).
version="$(cat version | tr -d '\n\r ')"
version_type="$(cat version_type | tr -d '\n\r ')" # وضع البناء (stable/experimental)، لا كود اسم
version_code="$(git rev-list HEAD --count)"
sed -i "s/versionCode=.*/versionCode=$version_code/" mainfiles/module.prop

# Set Profile Folder untuk Rust berdasarkan version_type
RUST_PROFILE="release"
if [ "$version_type" == "experimental" ]; then
    RUST_PROFILE="debug"
fi
echo "Using Rust build profile: $RUST_PROFILE"

# العمودان (قرار المالك، تكملة ١١٠): 64-بت و32-بت معًا، والمنصّب يختار بحسب `ARCH`
# (mainfiles/customize.sh) — وكل عمود يحمل الثنائيات الخمسة نفسها بأسمائها.
mkdir -p mainfiles/libs/arm64-v8a
mkdir -p mainfiles/libs/armeabi-v7a
mkdir -p mainfiles/system/bin

[ -d "libs" ] && cp -r libs/* mainfiles/libs/ 2>/dev/null
[ -d "archdaemon/libs" ] && cp -r archdaemon/libs/* mainfiles/libs/ 2>/dev/null
[ -d "preloadbin/libs" ] && cp -r preloadbin/libs/* mainfiles/libs/ 2>/dev/null

# نسخة تُغلق بما هو مطلوب: ثنائيِّ مفقود يُفشل البناء باسمه ومساره، بدل أن يُسقطه
# `|| true` فيمرّ الموديول ويُركَّب ناقصًا على الجهاز (وهو ما كان يحدث صامتًا).
copy_binary() {
	[ -f "$1" ] || { echo "ERROR: missing built binary: $1" >&2; exit 1; }
	cp "$1" "$2"
}

# Ambil binari Rust berdasarkan RUST_PROFILE (debug / release)
copy_binary thermalcore/target/aarch64-linux-android/$RUST_PROFILE/rianixia-thermalcore mainfiles/libs/arm64-v8a/sys.maxmanager-rianixiathermalcore
copy_binary binprofiles/target/aarch64-linux-android/$RUST_PROFILE/maxmanager-profilesettings mainfiles/libs/arm64-v8a/sys.maxmanager-profilesettings
copy_binary binutils/target/aarch64-linux-android/$RUST_PROFILE/maxmanager-utilityconf mainfiles/libs/arm64-v8a/sys.maxmanager-utilityconf

# وعمود 32-بت بالمعيار نفسه: `copy_binary` يرفض المفقود بالاسم والمسار (لا `|| true`
# الذي كان يمرّر الموديول ناقصًا في المشروع القديم).
copy_binary thermalcore/target/armv7-linux-androideabi/$RUST_PROFILE/rianixia-thermalcore mainfiles/libs/armeabi-v7a/sys.maxmanager-rianixiathermalcore
copy_binary binprofiles/target/armv7-linux-androideabi/$RUST_PROFILE/maxmanager-profilesettings mainfiles/libs/armeabi-v7a/sys.maxmanager-profilesettings
copy_binary binutils/target/armv7-linux-androideabi/$RUST_PROFILE/maxmanager-utilityconf mainfiles/libs/armeabi-v7a/sys.maxmanager-utilityconf

# ── الـdaemon التنفيذي داخل `system/bin/` في الحزمة نفسها (طلب المالك، تكملة ١١١) ──
#
# **المشكلة التي يُعالجها هذا:** كان `system/bin/` في الحزمة **فارغًا** (لا `ELF` ولا `maxmanager_daemon`)،
# والثنائي موجود فقط في `libs/<abi>/` ويستخرجه المنصّب وقت التركيب (`mainfiles/customize.sh:117-122`).
# وقائل «سيُملأ أثناء التثبيت» صحيح وظيفيًّا، لكنه **لا يُثبت شيئًا** لمن يفتح الحزمة: لا دليل على أن
# الـdaemon نتج فعلًا، ولا أنه `ELF` صالح للأندرويد.
#
# **ولماذا 64-بت تحديدًا:** `APP_ABI := arm64-v8a armeabi-v7a` (تكملة ١١٠) — و`system/bin/` **مجلد واحد**
# لا يحمل عمودين. فوُضع عمود 64-بت (الغالب) للتحقّق العام والتمثيل، ويبقى عمود 32-بت في `libs/armeabi-v7a/`
# **والمنصّب ينسخه فوقه على جهاز 32-بت** في `customize.sh:122` (`cp "$TMPDIR/libs/$ARCH_TMP/"* "$MODPATH/system/bin/"`).
# ⇒ **صفر تغيير في منطق التثبيت**، وصفر مساس بدعم 32-بت؛ والثمن الوحيد نسخة زائدة (~74KB) في الحزمة.
DAEMON_NAME="sys.maxmanager-service"
DAEMON_ARM64="archdaemon/libs/arm64-v8a/$DAEMON_NAME"
DAEMON_ARM32="archdaemon/libs/armeabi-v7a/$DAEMON_NAME"
copy_binary "$DAEMON_ARM64" "mainfiles/system/bin/$DAEMON_NAME"

# وحرس `ELF` **قبل** الخروج بالحزمة: `file` يقول النوع والمعمارية، و`readelf` يقول إنه تنفيذي لا مكتبة.
# بلا هذا الحرس كان يمكن أن تُشحن نسخة نصية أو `.so` ويُكتشف العطب على الجهاز لا في CI.
assert_executable_elf() {
	local path="$1" want_class="$2" want_machine="$3"
	[ -f "$path" ] || { echo "ERROR: missing built daemon: $path" >&2; exit 1; }
	local desc
	desc="$(file -b "$path")"
	case "$desc" in
		*"ELF $want_class"*) ;;
		*) echo "ERROR: $path is not an ELF $want_class: $desc" >&2; exit 1 ;;
	esac
	case "$desc" in
		*"$want_machine"*) ;;
		*) echo "ERROR: $path is not $want_machine: $desc" >&2; exit 1 ;;
	esac
	if command -v readelf >/dev/null 2>&1; then
		readelf -h "$path" | grep -qE '^[[:space:]]+Type:[[:space:]]+(DYN|EXEC)' \
			|| { echo "ERROR: $path is not an executable ELF:" >&2; readelf -h "$path" >&2; exit 1; }
	fi
	echo "ELF verified: $path → $desc"
}
assert_executable_elf "mainfiles/system/bin/$DAEMON_NAME" "64-bit" "aarch64"
assert_executable_elf "$DAEMON_ARM32" "32-bit" "ARM"

# Other Files
cp maxmanagerApplist.json mainfiles/
cp LICENSE mainfiles/ 2>/dev/null
cp THIRD_PARTY_NOTICES.md mainfiles/ 2>/dev/null

# Copy Manager APK as a systemless priv-app. The module system tree is
# overlaid on /product by Magisk (OverlayFS/magic mount) so the APK is
# scanned as a real priv-app with privileged permission grants from the
# permissions XML at boot — no pm install, no user-data install.
# Image modders use these exact paths when repacking super.img.
PRIVAPP_DIR="mainfiles/system/product/priv-app/MaxManager"
PERMS_DIR="mainfiles/system/product/etc/permissions"
mkdir -p "$PRIVAPP_DIR" "$PERMS_DIR"

# Which APK the workflow actually produced is decided once, in the
# "Materialize the release keystore from CI secrets" step, and exported as
# SIGNED_BUILD. Re-deriving it here from V_TYPE/GITHUB_EVENT_NAME was a second
# copy of the same rule, and the two can drift (they did: this script and the
# Validate APK step both assumed a signed release while the keystore itself was
# never available to CI). SIGNED_BUILD is the single source of truth.
case "${SIGNED_BUILD:-0}" in
    1)
        APK_PATH="manager/app/build/outputs/apk/release/app-release.apk"
        ;;
    *)
        APK_PATH="manager/app/build/outputs/apk/debug/app-debug.apk"
        ;;
esac
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: Expected $version_type APK is missing: $APK_PATH" >&2
    exit 1
fi
cp "$APK_PATH" "$PRIVAPP_DIR/MaxManager.apk"
echo "Copied verified build output $APK_PATH to $PRIVAPP_DIR."

# Single-source privileged permissions (shared with the developer bundle)
cp android/overlay/product/etc/permissions/privapp-permissions-nd.max.xml "$PERMS_DIR/"

# اسم الملف — الإصدار وحده، بلا كود اسم وبلا رقم التزام (تكملة ١٣٩: «اسم احترافي»).
# `MaxManager-v1.0.zip`. ورقم البناء يبقى حيث يُقرأ آليًّا: `versionCode` داخل `module.prop`
# و`checksums.sha256` بجانب الأثر — لا في اسم يتغيّر كل بناء ويُفسد الروابط والمرايا.
zipName="MaxManager-$version.zip"
echo "zipName=$zipName" >>"$GITHUB_OUTPUT"
artifactName="${zipName%.zip}"
echo "artifactName=$artifactName" >>"$GITHUB_OUTPUT"

# Checksums — **مانيفست واحد خارج الحزمة**، لا `.sha256` بجانب كلّ ملفّ داخلها.
#
# كان كلّ نداء يكتب `${file}.sha256` **في الشجرة التي تُضغط**، فتسافر عشرات الملفّات الصغيرة
# داخل حزمة الموديول. الآن تُكتب في `checksums.sha256` عند جذر المستودع (خارج `mainfiles/`
# وخارج `developer-bundle/`)، وتُرفع أثرًا مستقلًّا في الـworkflow. والتحقّق باقٍ بالقيمة:
# `sha256sum -c checksums.sha256` بعد فك الضغط، والمسارات في الملف هي مساراتها داخل الحزمة.
integrity_inputs_present() {
	for file in "${need_integrity[@]}"; do
		[ -e "$file" ] || { echo "ERROR: integrity input missing: $file" >&2; exit 1; }
	done
}
integrity_inputs_present
for file in "${need_integrity[@]}"; do
	[ -e "$file" ] || { echo "ERROR: integrity input missing: $file" >&2; exit 1; }
done

# Zip the file
cd ./mainfiles || {
	echo "Unable to cd to ./mainfiles" >&2
	exit 1
}

# `*.sha256` مطرودة صراحةً: لا شيء منها يبقى في الشجرة بعد إصلاح `generatesha256.sh`،
# وهذا **حرس** لا تكرار — لو أعاد أحدهم كتابة واحد داخل `mainfiles/` لبان هنا.
zip -r9 ../"$zipName" * -x *placeholder* *.map .shellcheckrc '*.sha256'
zip -z ../"$zipName" <<EOF
$version (build $version_code · $(git rev-parse --short HEAD))
Build Date $(date +"%a %b %d %H:%M:%S %Z %Y")
EOF

# حرس: الحزمة لا تحمل أيّ `.sha256` — يُقاس على **المُشحون** لا على النيّة.
if unzip -l ../"$zipName" | grep -q '\.sha256'; then
	echo "ERROR: $zipName still contains .sha256 entries" >&2
	unzip -l ../"$zipName" | grep '\.sha256' >&2
	exit 1
fi
echo "✅ $zipName carries no .sha256 entries"

# المانيفست **مشتقّ من الحزمة** هذه لا من الشجرة: فما يستبعده الضغط يستبعده الحساب
# تلقائيًّا (وهو ما أفلت في أوّل نسخة: `.shellcheckrc` وُصف وهو غير موجود في الحزمة).
#
# **ومسارات مطلقة عن قصد:** الكود هنا يعمل **داخل `mainfiles/`** (بعد `cd ./mainfiles`
# أعلاه)، فـ`.github/…` النسبي يُقرأ `mainfiles/.github/…` ⇒ `No such file`.
module_manifest="${CHECKSUM_MANIFEST:-$GITHUB_WORKSPACE/checksums-module.sha256}"
bash "$GITHUB_WORKSPACE/.github/scripts/generatesha256.sh" \
	"$GITHUB_WORKSPACE/$zipName" "$module_manifest"
