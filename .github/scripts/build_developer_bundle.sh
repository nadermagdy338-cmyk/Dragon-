#!/bin/env bash
# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
#
# حزمة تكامل المطوّرين — **مجلّد واحد، ونسخة واحدة من كلّ أثر** (تكملة ١٣٩).
#
# ── ما كان، ولماذا لم يبقَ ────────────────────────────────────────────────
#
# كانت الحزمة فرعين: `overlay/` (نسخة من التطبيق + XML الصلاحيات) و`aosp/`
# (نسخة **ثانية** من التطبيق في `prebuilt/` + XML **ثانٍ** + `Android.bp`)، أي أنّ
# التطبيق نفسه يسافر **مرّتين** داخل الحزمة الواحدة، **وثلاث مرّات** مع حزمة الموديول
# التي تُبنى في التشغيل نفسه. و`android/aosp/` في المستودع (الـ`.rc` + `sepolicy/` +
# `BoardConfig.mk`) لم يكن يقرأه أحد: الحزمة كانت تُنشئ نسخة مبتورة منه بلا خدمة
# ولا سياسة، ثم تقول في `README.txt` «no init service, no SELinux policy».
#
# ── وما صار ───────────────────────────────────────────────────────────────
#
# مجلّد واحد `maxmanager/` **يحاكي مسارات القسم الحقيقي**، فينسخه معدّل الصور حرفيًّا
# إلى صورة `product`/`system`، **و**يقرأ منه بانِ AOSP عبر `Android.bp` **في مكانه**
# (لا نسخة ثانية لأجله). و`:sys.maxmanager-service` صار **مُدرَجًا فعلًا**: بلا ديمون
# لا مسار كتابة للعتاد في ROM (ADR-11: كل الكتابات عبر الـarbiter = الديمون)، فيصل
# التطبيق إلى النظام بلا خدمة تُنفّذ طلبه.
#
# والمصادر **واحدة**: `android/aosp/{maxmanager.rc,sepolicy/,BoardConfig.mk}` هي
# المنشأ، وتُنسَخ هنا كما هي. لا نسخة يدوية ثانية تنزاح عن الأولى.
set -euo pipefail

REPO_ROOT="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_ROOT"

BUNDLE_DIR="${BUNDLE_DIR:-developer-bundle}"
BUNDLE_NAME="${BUNDLE_NAME:-MaxManager-developer-bundle.zip}"
OUT_DIR="${OUT_DIR:-$REPO_ROOT}"
APP_DIR="maxmanager"

APK_PATH="${APK_PATH:-}"
if [ -z "$APK_PATH" ]; then
	# نفس المصدر الواحد الذي يحكم حزمة الموديول: `SIGNED_BUILD` يُكتب مرّةً في
	# خطوة الـkeystore، ولا يُعاد اشتقاقه هنا (اشتقاقان ينزاحان — وقد انزاحا فعلًا).
	case "${SIGNED_BUILD:-0}" in
		1) APK_PATH="manager/app/build/outputs/apk/release/app-release.apk" ;;
		*) APK_PATH="manager/app/build/outputs/apk/debug/app-debug.apk" ;;
	esac
fi

# الديمون: نفس الثنائيّ الذي تتحقّق منه حزمة الموديول (ورفضه هنا **مقصود**: حزمة
# رومات بلا خدمة ليست حزمة رومات، فالمفقود يُفشل البناء باسمه بدل أن يُشحن ناقصًا).
DAEMON_SRC="${DAEMON_SRC:-archdaemon/libs/arm64-v8a/sys.maxmanager-service}"
[ -f "$DAEMON_SRC" ] || DAEMON_SRC="mainfiles/system/bin/sys.maxmanager-service"

[ -f "$APK_PATH" ] || { echo "ERROR: validated APK is missing: $APK_PATH" >&2; exit 1; }
[ -f "$DAEMON_SRC" ] || { echo "ERROR: the daemon is missing: $DAEMON_SRC" >&2; exit 1; }
for src in android/aosp/maxmanager.rc android/aosp/sepolicy/maxmanager.te \
           android/aosp/sepolicy/file_contexts android/aosp/BoardConfig.mk \
           android/overlay/product/etc/permissions/privapp-permissions-nd.max.xml; do
	[ -f "$src" ] || { echo "ERROR: bundle input missing: $src" >&2; exit 1; }
done

# الديمون يجب أن يكون تنفيذيًّا 64-بت ARM64 — يُقاس لا يُفترض: وجود الملف لا يقول
# إنه صالح للتشغيل، وحزمة رومات تحمل نصًّا أو `.so` مقنّعًا يُكتشف على الجهاز لا هنا.
daemon_desc="$(file -b "$DAEMON_SRC")"
case "$daemon_desc" in
	*"ELF 64-bit"*"aarch64"*) ;;
	*) echo "ERROR: $DAEMON_SRC is not an ARM64 ELF: $daemon_desc" >&2; exit 1 ;;
esac
if command -v readelf >/dev/null 2>&1; then
	readelf -h "$DAEMON_SRC" | grep -qE '^[[:space:]]+Type:[[:space:]]+(DYN|EXEC)' \
		|| { echo "ERROR: $DAEMON_SRC is not an executable ELF:" >&2; readelf -h "$DAEMON_SRC" >&2; exit 1; }
fi
echo "Daemon verified: $DAEMON_SRC → $daemon_desc"

rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/$APP_DIR"/{product/priv-app/MaxManager,product/etc/permissions,system/bin,system/etc/init,sepolicy}

root="$BUNDLE_DIR/$APP_DIR"

# ── نسخة واحدة من كلّ أثر، في مسار القسم الحقيقي ───────────────────────────
cp "$APK_PATH"      "$root/product/priv-app/MaxManager/MaxManager.apk"
cp "$DAEMON_SRC"    "$root/system/bin/sys.maxmanager-service"
cp android/overlay/product/etc/permissions/privapp-permissions-nd.max.xml \
	"$root/product/etc/permissions/"
cp android/aosp/maxmanager.rc "$root/system/etc/init/maxmanager.rc"
cp android/aosp/sepolicy/maxmanager.te "$root/sepolicy/"
cp android/aosp/sepolicy/file_contexts "$root/sepolicy/"
cp android/aosp/BoardConfig.mk "$root/BoardConfig.mk"
chmod 0755 "$root/system/bin/sys.maxmanager-service"

# ── `Android.bp`: يقرأ الأثرين **في مكانهما** فلا نسخة ثانية ────────────────
# `presigned` افتراضيّ لأنّ APK الشحنة موقّع ومتحقَّق منه في CI؛ و`certificate:
# "platform"` (المستعمل قبل هذا) مُوثَّق كبديل لمن يريد إعادة التوقيع بمفتاح رومه.
cat > "$root/Android.bp" <<'SOONG'
// MaxManager — AOSP integration (prebuilt variant).
//
// The two artifacts this file imports are the files sitting next to it: there is
// no second copy for the source build. If you build from the full MaxManager
// tree instead, use android/aosp/Android.bp (that one compiles the app from
// sources and the daemon from archdaemon/jni).

android_app_import {
    name: "MaxManager",
    apk: "product/priv-app/MaxManager/MaxManager.apk",
    // `presigned` keeps the shipped, CI-verified signature. Swap it for
    // `certificate: "platform"` if your build must re-sign with the platform key.
    presigned: true,
    privileged: true,
    product_specific: true,
    dex_preopt: {
        enabled: true,
    },
}

// The daemon must be `system`-only: the init service in
// system/etc/init/maxmanager.rc points at /system/bin/sys.maxmanager-service and
// the label in sepolicy/file_contexts is on that same path. Declaring it
// `vendor` as well made Soong publish it to /vendor/bin and disagree with init.
cc_prebuilt_binary {
    name: "sys.maxmanager-service",
    srcs: ["system/bin/sys.maxmanager-service"],
    strip: {
        none: true,
    },
    compile_multilib: "64",
    multilib: {
        lib64: {
            enabled: true,
        },
        lib32: {
            enabled: false,
        },
    },
}
SOONG

cat > "$root/product-inclusion.mk" <<'MAKEFILE'
# Include MaxManager in your product. Adjust the leading path to wherever you
# copied this directory (here: device/maxmanager).
#
# `libmaxmanager_native` is deliberately absent: it is the app's JNI library,
# and in this prebuilt variant it already lives inside MaxManager.apk. Naming it
# here would ask Soong for a module that does not exist in this tree. The source
# variant (android/aosp/product-inclusion.mk) does list it, because there it is
# built as its own cc_library_shared.
PRODUCT_PACKAGES += \
    MaxManager \
    sys.maxmanager-service

PRODUCT_COPY_FILES += \
    device/maxmanager/system/etc/init/maxmanager.rc:$(TARGET_COPY_OUT_SYSTEM)/etc/init/maxmanager.rc

# Privileged permissions for nd.max on the product partition. The allowlist is
# matched by package name, so it must land on the same partition as the APK.
PRODUCT_COPY_FILES += \
    device/maxmanager/product/etc/permissions/privapp-permissions-nd.max.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/permissions/privapp-permissions-nd.max.xml

# Same two properties the source variant sets. maxmanager.enable=1 is what
# system/etc/init/maxmanager.rc reacts to; without it the daemon still starts at
# sys.boot_completed=1, so this is a default, not a requirement.
PRODUCT_PROPERTY_OVERRIDES += \
    maxmanager.enable=1 \
    maxmanager.verbose=0
MAKEFILE

# ── README: جدول النسخ، لأن «مسار واحد» لا يكفي لمن ينقل ملفًّا ملفًّا ──────
cat > "$root/README.txt" <<'README'
MaxManager — ROM / AOSP integration
===================================

One tree, one copy of every artifact. Nothing here is duplicated, and nothing
here needs the app sources: the APK and the daemon both ship as prebuilts.

Layout (paths are the real destination paths)
--------------------------------------------
  product/priv-app/MaxManager/MaxManager.apk
      the app; already signed by CI, and verified after signing
  product/etc/permissions/privapp-permissions-nd.max.xml
      privileged permission allowlist for nd.max (matched by package name)
  system/bin/sys.maxmanager-service
      the daemon; ARM64 executable ELF
  system/etc/init/maxmanager.rc
      init service; starts the daemon at sys.boot_completed=1
  sepolicy/maxmanager.te, sepolicy/file_contexts
      SELinux domain, exec label and the daemon's data directory
  BoardConfig.mk
      board-level fragment: BOARD_SEPOLICY_DIRS only (PRODUCT_* is not read from
      a BoardConfig, which is why product-inclusion.mk exists)
  Android.bp, product-inclusion.mk
      Soong module definitions and the product make include (PRODUCT_PACKAGES,
      the .rc copy rule, the permission XML copy rule, property overrides)

A) Image modders (super.img unpack/repack, HyperOS ports)
--------------------------------------------------------
Copy product/** and system/** into the product and system images at the same
paths. Owners root:root, mode 0644 (0755 for system/bin/sys.maxmanager-service).
No re-signing: the APK's signature is the one CI validated.

B) AOSP source builds
---------------------
1. Copy this directory to device/maxmanager/ (any path; the paths inside
   product-inclusion.mk and BoardConfig.mk assume device/maxmanager).
2. Include product-inclusion.mk from your product makefile.
3. Add $(LOCAL_PATH)/sepolicy to BOARD_SEPOLICY_DIRS — BoardConfig.mk shows the
   line, with the path left for you to fill.
4. Build. Android.bp imports the APK and the daemon from this same tree; if you
   prefer re-signing with your platform key, replace `presigned: true` with
   `certificate: "platform"`.
5. If you build from the full MaxManager tree instead of this bundle, use
   android/aosp/Android.bp — that variant compiles the app from sources and the
   daemon from archdaemon/jni.

C) Rooted users
---------------
Use the flashable module zip (MaxManager-v1.0.zip), not this bundle. This tree
has no installer and no Magisk metadata.

The daemon is required
----------------------
The app does not write kernel nodes itself: every hardware write goes through
the daemon (the arbiter). A ROM that includes only the APK gets an app whose
performance controls have nothing to execute them. The three pieces above —
binary, init service, SELinux policy — are the whole reason this tree exists.

SELinux
-------
The policy here is a starting point, not a drop-in: verify it against your own
build with `neverallow` checks before shipping. It grants sysfs/proc read+write
and CAP_SYS_ADMIN, and it must stay consistent with three paths that this bundle
keeps aligned — /system/bin/sys.maxmanager-service in Android.bp, in
file_contexts and in maxmanager.rc.

Verified in CI
--------------
Before this bundle is uploaded, CI checks: the tree contains exactly one copy of
the APK and it is byte-identical to the validated build; the daemon is an ARM64
executable ELF; Android.bp, product-inclusion.mk, the init script, the policy and
the permission XML are all present; and no .sha256 file is inside the archive
(checksums ship next to the zip as checksums.sha256).
README

# ── الضغط: بلا `.sha256` داخل الحزمة، والمانيفست خارجها ──────────────────
(
	cd "$BUNDLE_DIR"
	zip -r9 "$OUT_DIR/$BUNDLE_NAME" . -x '*.sha256'
)

# مانيفست التحقّق: **ملفّ واحد خارج الحزمة**، **مشتقّ منها** لا من الشجرة، فتكون
# مسارات أسطره مسارات الاستخراج حرفيًّا (`sha256sum -c` يعمل بعد `unzip` مباشرةً).
checksum_manifest="${CHECKSUM_MANIFEST:-$OUT_DIR/checksums-developer-bundle.sha256}"
bash "$REPO_ROOT/.github/scripts/generatesha256.sh" "$OUT_DIR/$BUNDLE_NAME" "$checksum_manifest"

if unzip -l "$OUT_DIR/$BUNDLE_NAME" | grep -q '\.sha256'; then
	echo "ERROR: $BUNDLE_NAME still contains .sha256 entries" >&2
	unzip -l "$OUT_DIR/$BUNDLE_NAME" | grep '\.sha256' >&2
	exit 1
fi

# **حرس التكرار** — العطب الذي وُجدت هذه الجولة من أجله: أثر واحد، لا اثنان.
apk_entries="$(unzip -l "$OUT_DIR/$BUNDLE_NAME" | grep -cE '(MaxManager\.apk)$' || true)"
daemon_entries="$(unzip -l "$OUT_DIR/$BUNDLE_NAME" | grep -cE '(sys\.maxmanager-service)$' || true)"
[ "$apk_entries" = "1" ] || { echo "ERROR: expected exactly 1 APK in the bundle, found $apk_entries" >&2; exit 1; }
[ "$daemon_entries" = "1" ] || { echo "ERROR: expected exactly 1 daemon in the bundle, found $daemon_entries" >&2; exit 1; }
echo "✅ $BUNDLE_NAME: one APK, one daemon, no .sha256"
