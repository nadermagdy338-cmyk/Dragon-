#!/bin/env bash
# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.

# مانيفست تحقّق واحد، **خارج الحزمة**، مشتقّ **من الحزمة نفسها**.
#
# ── لماذا من الحزمة لا من الشجرة (وهذا عطب قِيس لا افتُرض) ──────────────────
#
# أوّل نسخة من هذا الـscript كانت تُحصي الشجرة وحدها، فأعطت **٢٩** مدخلًا لحزمة
# الموديول — منها `.shellcheckrc` الذي **يستبعده** سطر الضغط نفسه
# (`zip … -x .shellcheckrc`). أي أنّ المانيفست وصف ملفًّا لا تحمله الحزمة، وكان
# `sha256sum -c` بعد فكّ الضغط **يفشل حتمًا** ولو كانت الحزمة سليمة تمامًا: بوابة
# تكذب على الحزمة التي وُجدت لتحرسها.
#
# والدرس: المانيفست يجب أن يصف **المُشحون**، والطريق الوحيد المضمون إلى ذلك أن
# يُقرأ **من الأرشيف** لا من مصدره. فما يخرج من الضغط يخرج من الحساب أيضًا تلقائيًّا،
# ولا يوجد `-x` ثانٍ يمكن أن ينزاح عن الأول.
#
# ── وما لم يُحذف ────────────────────────────────────────────────────────────
# التحقّق بالقيمة. الملفّ يُرفع مع الحزمتين لا داخلهما (طلب المالك: لا `.sha256`
# داخل الـzip)، ومسارات أسطره هي مسارات الاستخراج حرفيًّا، فيعمل
# `sha256sum -c checksums-*.sha256` بعد `unzip` مباشرةً.
#
# الاستعمال: $0 <archive.zip> [output-manifest]
set -euo pipefail

archive="${1:-}"
if [ -z "$archive" ]; then
	echo "Usage: $0 <archive.zip> [output-manifest]" >&2
	exit 1
fi
if [ ! -f "$archive" ]; then
	echo "ERROR: archive not found: $archive" >&2
	exit 1
fi

manifest="${2:-${CHECKSUM_MANIFEST:-./checksums-$(basename "${archive%.zip}").sha256}}"

# مدخلات الأرشيف بترتيب ثابت (بلا مجلّدات: المجلّد ليس ملفًّا يُجزم عليه)، ثم تُقرأ
# **من الأرشيف نفسه**. و`-Z1` يطبع الأسماء وحدها، فلا تعليقات ولا أعمدة تخدع القارئ.
entries="$(unzip -Z1 "$archive" | grep -v '/$' | LC_ALL=C sort)"

: > "$manifest"
printf '%s\n' "$entries" | while IFS= read -r entry; do
	[ -n "$entry" ] || continue
	digest="$(unzip -p "$archive" "$entry" | sha256sum | awk '{print $1}')"
	printf '%s  %s\n' "$digest" "$entry" >> "$manifest"
done

count="$(wc -l < "$manifest" | tr -d ' ')"
[ "$count" -gt 0 ] || { echo "ERROR: manifest is empty for $archive" >&2; exit 1; }
echo "Wrote $count checksums to $manifest"
