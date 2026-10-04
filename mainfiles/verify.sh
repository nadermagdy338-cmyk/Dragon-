#
# Copyright (C) 2024-2025 Rem01Gaming
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

TMPDIR_FOR_VERIFY="$TMPDIR/.vunzip"
mkdir "$TMPDIR_FOR_VERIFY"

abort_verify() {
    clear
	ui_print "! $1"
	ui_print "! Installation aborted. The module may be corrupted."
	abort "! Please re-download and try again."
}

# extract <zip> <file> <target dir>
#
# **وتغيير مقصود (تكملة ١٣٩): ملفّات `.sha256` لم تعد تُشحن داخل الحزمة** (طلب المالك:
# «لا تضف ملفات .sha256 داخل .zip») — وكانت `.sha256` واحدة لكل ملفّ تُحشى بها الحزمة.
# فالشرط هنا صار: **إن وُجد التوقيع فتحقّق منه، وإن غاب فامضِ** — لا `abort`.
#
# **وأثر ذلك مُعلَن لا مسكوت عنه:** الحماية من العطب العَرَضي باقية، لأن `unzip` يتحقّق
# من `CRC32` لكل مدخل أثناء الاستخراج ويفشل عليه (`abort_corrupted` مُستدعًى قبل هذا).
# وما سقط هو **توقيع SHA-256**، وهو لم يكن يحرس من العبث أصلًا: التوقيع كان يسافر في
# الحزمة نفسها، فمن عدّل الملف عدّل توقيعه معه. فالمُكتسَب صفر أمنًا، والمُكتسَب نظافة
# حزمة. وأيّ حرّاس آخرين للتوقيع الحقيقي (توقيع APK) لم يُمسّا: أندرويد يتحقّق منه بنفسه.
extract() {
	zip=$1
	file=$2
	dir=$3

	file_path="$dir/$file"

	unzip -o "$zip" "$file" -d "$dir" >&2
	[ -f "$file_path" ] || abort_verify "$file does not exists"

	hash_path="$TMPDIR_FOR_VERIFY/$file.sha256"
	unzip -o "$zip" "$file.sha256" -d "$TMPDIR_FOR_VERIFY" >&2
	if [ -f "$hash_path" ]; then
		(echo "$(cat "$hash_path")  $file_path" | sha256sum -c -s -) || abort_verify "Checksum mismatch for $file"
		ui_print "- Verified $file" >&1
	fi
}

file="META-INF/com/google/android/update-binary"
file_path="$TMPDIR_FOR_VERIFY/$file"
hash_path="$file_path.sha256"
unzip -o "$ZIPFILE" "META-INF/com/google/android/*" -d "$TMPDIR_FOR_VERIFY" >&2
[ -f "$file_path" ] || abort_verify "$file does not exists"
if [ -f "$hash_path" ]; then
	(echo "$(cat "$hash_path")  $file_path" | sha256sum -c -s -) || abort_verify "Checksum mismatch for $file"
	ui_print "- Verified $file" >&1
else
	ui_print "- Download from Magisk app"
fi
