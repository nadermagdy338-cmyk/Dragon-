#!/bin/env bash
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

# Put critical files and folders here
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
	"mainfiles/module.banner.avif"
    "mainfiles/maxmanagerApplist.json"
    "mainfiles/system/product/priv-app/MaxManager/MaxManager.apk"
    "mainfiles/system/product/etc/permissions/privapp-permissions-nd.max.xml"
    "mainfiles/props.sh"
)

# Version info
version="$(cat version)"
version_type="$(cat version_type | tr -d '\n\r ')" # Hapus spasi/newline agar presisi
version_code="$(git rev-list HEAD --count)"
release_code="$(git rev-list HEAD --count)-$(git rev-parse --short HEAD)-$version_type"
sed -i "s/version=.*/version=$version ($release_code)/" mainfiles/module.prop
sed -i "s/versionCode=.*/versionCode=$version_code/" mainfiles/module.prop

# Set Profile Folder untuk Rust berdasarkan version_type
RUST_PROFILE="release"
if [ "$version_type" == "experimental" ]; then
    RUST_PROFILE="debug"
fi
echo "Using Rust build profile: $RUST_PROFILE"

# arm64-v8a وحده (قرار المالك، تكملة ٨٢): الأجهزة 32-بت لم تبقَ مدعومة، والمنصّب يرفضها
# برسالة صريحة (mainfiles/customize.sh)
mkdir -p mainfiles/libs/arm64-v8a
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

# Other Files
cp maxmanagerApplist.json mainfiles/
cp LICENSE mainfiles/ 2>/dev/null
cp NOTICE.md mainfiles/ 2>/dev/null

# Copy Manager APK as a systemless priv-app. The module system tree is
# overlaid on /product by Magisk (OverlayFS/magic mount) so the APK is
# scanned as a real priv-app with privileged permission grants from the
# permissions XML at boot — no pm install, no user-data install.
# Image modders use these exact paths when repacking super.img.
PRIVAPP_DIR="mainfiles/system/product/priv-app/MaxManager"
PERMS_DIR="mainfiles/system/product/etc/permissions"
mkdir -p "$PRIVAPP_DIR" "$PERMS_DIR"

case "$version_type" in
    experimental)
        APK_PATH="manager/app/build/outputs/apk/debug/app-debug.apk"
        ;;
    *)
        # Pull requests build an unsigned debug APK instead (see
        # "Build Manager APK" in build.yml) since KS_PWD/KEYSTORE_PASSWORD
        # is never available to PR runs.
        if [ "${GITHUB_EVENT_NAME:-}" == "pull_request" ]; then
            APK_PATH="manager/app/build/outputs/apk/debug/app-debug.apk"
        else
            APK_PATH="manager/app/build/outputs/apk/release/app-release.apk"
        fi
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

# Parse version info to module prop
zipName="MaxManager-$version-$release_code.zip"
echo "zipName=$zipName" >>"$GITHUB_OUTPUT"
artifactName="${zipName%.zip}"
echo "artifactName=$artifactName" >>"$GITHUB_OUTPUT"

# Generate sha256sum for integrity checkup
for file in "${need_integrity[@]}"; do
	bash .github/scripts/generatesha256.sh "$file"
done

# Zip the file
cd ./mainfiles || {
	echo "Unable to cd to ./mainfiles" >&2
	exit 1
}

zip -r9 ../"$zipName" * -x *placeholder* *.map .shellcheckrc
zip -z ../"$zipName" <<EOF
$version-$release_code
Build Date $(date +"%a %b %d %H:%M:%S %Z %Y")
EOF
