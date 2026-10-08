#!/usr/bin/env bash
# لقطات الرئيسية بحجم الخط العادي و٢٠٠٪ — تحتاج جهازًا متصلًا بـ adb وتطبيق Max مفتوحًا على الرئيسية.
# الاستعمال: bash tools/home_snapshots.sh [مجلد_الإخراج]
# يعيد حجم الخط إلى ١٠٠٪ عند الخروج مهما حدث.
set -euo pipefail
OUT="${1:-build/home-snapshots}"
mkdir -p "$OUT"
restore_font() { adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true; }
trap restore_font EXIT
for scale in 1.0 2.0; do
  adb shell settings put system font_scale "$scale"
  sleep 3
  adb exec-out screencap -p > "$OUT/home_font_${scale}.png"
  echo "saved $OUT/home_font_${scale}.png"
done
