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

mkdir -p mainfiles/libs/arm64-v8a
mkdir -p mainfiles/libs/armeabi-v7a
mkdir -p mainfiles/system/bin

[ -d "libs" ] && cp -r libs/* mainfiles/libs/ 2>/dev/null
[ -d "archdaemon/libs" ] && cp -r archdaemon/libs/* mainfiles/libs/ 2>/dev/null
[ -d "preloadbin/libs" ] && cp -r preloadbin/libs/* mainfiles/libs/ 2>/dev/null

# Ambil binari Rust berdasarkan RUST_PROFILE (debug / release)
cp thermalcore/target/aarch64-linux-android/$RUST_PROFILE/rianixia-thermalcore mainfiles/libs/arm64-v8a/sys.maxmanager-rianixiathermalcore 2>/dev/null || true
cp binprofiles/target/aarch64-linux-android/$RUST_PROFILE/maxmanager-profilesettings mainfiles/libs/arm64-v8a/sys.maxmanager-profilesettings 2>/dev/null || true
cp binutils/target/aarch64-linux-android/$RUST_PROFILE/maxmanager-utilityconf mainfiles/libs/arm64-v8a/sys.maxmanager-utilityconf 2>/dev/null || true

cp thermalcore/target/armv7-linux-androideabi/$RUST_PROFILE/rianixia-thermalcore mainfiles/libs/armeabi-v7a/sys.maxmanager-rianixiathermalcore 2>/dev/null || true
cp binprofiles/target/armv7-linux-androideabi/$RUST_PROFILE/maxmanager-profilesettings mainfiles/libs/armeabi-v7a/sys.maxmanager-profilesettings 2>/dev/null || true
cp binutils/target/armv7-linux-androideabi/$RUST_PROFILE/maxmanager-utilityconf mainfiles/libs/armeabi-v7a/sys.maxmanager-utilityconf 2>/dev/null || true

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

APK_PATH=$(find manager/app/build/outputs/apk/release -name "*.apk" | head -n 1)
APK_PATH_DEBUG=$(find manager/app/build/outputs/apk/debug -name "*.apk" | head -n 1)
if [ -n "$APK_PATH" ]; then
    cp "$APK_PATH" "$PRIVAPP_DIR/MaxManager.apk"
    echo "APK found at $APK_PATH and installed at $PRIVAPP_DIR successfully."
elif [ -n "$APK_PATH_DEBUG" ]; then
    cp "$APK_PATH_DEBUG" "$PRIVAPP_DIR/MaxManager.apk"
    echo "APK found at $APK_PATH_DEBUG and installed at $PRIVAPP_DIR successfully."
else
    echo "ERROR: No APK found!"
    exit 1
fi

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
