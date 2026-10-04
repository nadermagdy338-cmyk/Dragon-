#!/usr/bin/env python3
"""بوابة نظام التصميم — تمنع تكرار الرموز الحرفية في طبقة الواجهة.

لماذا وُجدت: الجولة التي أُمرتُ فيها بمراجعة الاتساق قاست الحالة الفعلية فوجدت
**٢٠ نصف قطر مختلفًا** و**١٤ قيمة حشو** مكتوبة حرفيًّا في `ui/**`، و**٣٨ تعريف
`*Card`** لا يجمعها شيء. وهذا ليس عطبًا يُصلح مرّة واحدة ثم ينتهي: هو **ميل** يعود
مع كل شاشة جديدة تُكتب على عجل. فالبوابة لا تُصلح — **تُثبّت السقف** على ما بقي.

القاعدة: الرقم المرجعي (`tools/design_tokens_baseline.json`) هو **أفضل ما وصلنا
إليه**، لا ما نرضاه. أي تعديل يزيد حرفيًّا واحدًا في أي فئة يُحمرّ البناء، ويستلزم
قرارًا مكتوبًا بـ`--update` ينزل بالرقم لا يرفعه.

أمثلة:
    python3 tools/design_tokens.py --assert     # يفشل إن زاد أي حرفيّ عن الأساس
    python3 tools/design_tokens.py --update     # يقبل الحالة الحالية كأساس جديد (قرار)
    python3 tools/design_tokens.py --self-test  # يقيس الأداة نفسها على نصّ معروف
    python3 tools/design_tokens.py --json       # جداول كاملة
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

# نطاق واحد مُعلَن: طبقة العرض وحدها. الأدوات والوثائق ليست قيم تصميم.
SCAN_ROOT = os.path.join(ROOT, "manager/app/src/main/java/nd/max/ui")
BASELINE_REL = os.path.join("tools", "design_tokens_baseline.json")

# الفئات: كل واحدة صنف انزياح يمكن أن يُقاس، لا «عدد أسطر».
#   radius   — `RoundedCornerShape(18.dp)`: شكل مكتوب خارج `MaxRadius`.
#   pad_h    — `.padding(horizontal = 16.dp)`: هامش صفحة مكتوب خارج `MaxSpace`.
#   pad_v    — `.padding(vertical = 12.dp)`
#   pad_all  — `.padding(16.dp)`
#   gap      — `Arrangement.spacedBy(10.dp)`
#   border   — `.border(1.dp…)` / `BorderStroke(1.dp…)`
PATTERNS: dict[str, re.Pattern[str]] = {
    "radius": re.compile(r"RoundedCornerShape\(\s*(\d+(?:\.\d+)?)\.dp"),
    "pad_h": re.compile(r"\.padding\(\s*horizontal\s*=\s*(\d+(?:\.\d+)?)\.dp"),
    "pad_v": re.compile(r"\.padding\(\s*vertical\s*=\s*(\d+(?:\.\d+)?)\.dp"),
    "pad_all": re.compile(r"\.padding\(\s*(\d+(?:\.\d+)?)\.dp\s*\)"),
    "gap": re.compile(r"(?:spacedBy|Arrangement\.spacedBy)\(\s*(\d+(?:\.\d+)?)\.dp"),
    "border": re.compile(r"(?:\.border|BorderStroke)\(\s*(\d+(?:\.\d+)?)\.dp"),
}

# الرموز: تُستثنى من العدّ لأنها **البديل** المطلوب، لا الحرفيّ.
TOKEN_MARKERS = ("MaxRadius.", "MaxSpace.", "MaxSize.", "MaxCardSpec.", "MaxAlpha.")


def collect_files(root: str) -> list[str]:
    found: list[str] = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(d for d in dirnames if d not in {"build", ".gradle"})
        for name in sorted(filenames):
            if name.endswith(".kt"):
                found.append(os.path.join(dirpath, name))
    return found


def scan(root: str) -> tuple[dict[str, int], dict[str, list[str]], dict[str, set[str]], dict[str, dict[str, int]]]:
    """يعدّ الحرفيّات في [root].

    يُعيد: (عدد كل فئة · مواضع كل فئة · القيم المتمايزة · العدد لكل ملف).

    والعدّ **لكل ملف** ليس ترفًا: بلا تقسيم لكل ملف لا يستطيع التقرير أن يسمّي الملف الذي
    أضاف الحرفيّ الجديد، فيأخذ آخر ما سجّله ويعرضه — أي يسمّي ملفًّا بريئًا ويضيّع وقت
    من يقرأ الفشل. وهذا العطب نفسه وقع في أول طفرة قِيست.

    والتقارير تُبنى بـ`file:line` لا بمسار مطلق، فلا تختلف النتيجة بين آلة وأخرى
    — وهذا شرط أن يكون الأساس قابلًا للمقارنة في CI.
    """
    counts = {key: 0 for key in PATTERNS}
    sites: dict[str, list[str]] = {key: [] for key in PATTERNS}
    values: dict[str, set[str]] = {key: set() for key in PATTERNS}
    by_file: dict[str, dict[str, int]] = {}

    for path in collect_files(root):
        rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
        per_file = {key: 0 for key in PATTERNS}
        with open(path, encoding="utf-8") as fh:
            for lineno, line in enumerate(fh, 1):
                # سطر تعليق لا يُعدّ: ذكر `16.dp` داخل شرح ليس رمزًا حرفيًّا.
                stripped = line.lstrip()
                if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                    continue
                for key, pattern in PATTERNS.items():
                    for match in pattern.finditer(line):
                        counts[key] += 1
                        per_file[key] += 1
                        sites[key].append(f"{rel}:{lineno}")
                        values[key].add(match.group(1))
        if any(per_file.values()):
            by_file[rel] = dict(sorted(per_file.items()))
    return counts, sites, values, by_file


def distinct_radius(root: str) -> int:
    """عدد قيم نصف القطر المتمايزة — وهو الرقم الذي قاسته المراجعة (٢٠)."""
    return len(scan(root)[2]["radius"])


def newly_literal_files(counts: dict[str, int], by_file: dict[str, dict[str, int]],
                        baseline: dict) -> list[str]:
    """يسمّي الملفات التي **زاد فيها** الحرفيّ عن الأساس، مرتّبة بالأثر.

    ملفّ لم يكن في الأساس يُعامل بأنه زاد من الصفر — وهذا هو حال شاشة جديدة.
    """
    base_files: dict[str, dict[str, int]] = baseline.get("by_file", {})
    grew: list[tuple[int, str, str]] = []
    for rel, per_file in by_file.items():
        base = base_files.get(rel, {})
        for key, now in per_file.items():
            delta = now - base.get(key, 0)
            if delta > 0:
                grew.append((delta, rel, key))
    grew.sort(reverse=True)
    return [f"{rel}  ({key} +{delta})" for delta, rel, key in grew[:5]]


def load_baseline() -> dict | None:
    """الأساس، أو `None` إن لم يُكتب بعد — فالغياب يُعلَن لا يُترجم إلى انهيار."""
    path = os.path.join(ROOT, BASELINE_REL)
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def write_baseline(counts: dict[str, int], radii: int, by_file: dict[str, dict[str, int]]) -> None:
    payload = {
        "_comment": "سقف مفروض لرموز التصميم الحرفية. لا يُرفع؛ يُنزَل بـ--update بعد إصلاح.",
        "counts": dict(sorted(counts.items())),
        "distinct_radii": radii,
        # عدّ لكل ملف: بدونه لا يستطيع التقرير أن يسمّي من أضاف الحرفيّ الجديد.
        "by_file": dict(sorted(by_file.items())),
    }
    with open(os.path.join(ROOT, BASELINE_REL), "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2, sort_keys=True)
        fh.write("\n")


def self_test() -> int:
    """يقيس الأداة نفسها: أداة تمرّ على كل شيء لا تُثبت شيئًا."""
    failures = 0

    def check(label: str, ok: bool) -> None:
        nonlocal failures
        print(f"  {'✅' if ok else '❌'} {label}")
        if not ok:
            failures += 1

    sample = "\n".join(
        [
            "RoundedCornerShape(MaxRadius.group),",  # رمز: لا يُعدّ
            "RoundedCornerShape(18.dp),",  # حرفيّ: يُعدّ
            "// .padding(horizontal = 16.dp) داخل تعليق",  # تعليق: لا يُعدّ
            "modifier.padding(horizontal = 16.dp),",  # حرفيّ
            "modifier.padding(horizontal = MaxSpace.gutter),",  # رمز
            "Arrangement.spacedBy(10.dp),",  # حرفيّ
            "Arrangement.spacedBy(MaxCardSpec.gridSpacing),",  # رمز
            "BorderStroke(1.dp, color),",  # حرفيّ
        ]
    )
    counts: dict[str, int] = {key: 0 for key in PATTERNS}
    values: dict[str, set[str]] = {key: set() for key in PATTERNS}
    for line in sample.splitlines():
        stripped = line.lstrip()
        if stripped.startswith("//"):
            continue
        for key, pattern in PATTERNS.items():
            for match in pattern.finditer(line):
                counts[key] += 1
                values[key].add(match.group(1))
    check("حرفيّ نصف قطر واحد", counts["radius"] == 1)
    check("حرفيّ حشو أفقي واحد", counts["pad_h"] == 1)
    check("التعليق لم يُعدّ", "16" in values["pad_h"] and counts["pad_h"] == 1)
    check("الرمز لم يُعدّ", counts["radius"] == 1 and "gap" in counts)
    check("حرفيّ التباعد واحد", counts["gap"] == 1)
    check("حرفيّ الحدّ واحد", counts["border"] == 1)
    check("القيم المتمايزة صحيحة", values["radius"] == {"18"} and values["gap"] == {"10"})
    print(f"\nالنتيجة: {7 - failures}/7")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="بوابة رموز التصميم")
    # `assert` كلمة محجوزة في Python فلا تصلح اسمًا للسمة: dest صريح.
    parser.add_argument("--assert", dest="assert_gate", action="store_true",
                        help="يفشل إن تجاوز أي حرفيّ سقفه")
    parser.add_argument("--update", action="store_true", help="يُثبّت الحالة الحالية سقفًا جديدًا")
    parser.add_argument("--self-test", action="store_true", help="يقيس الأداة نفسها")
    parser.add_argument("--json", action="store_true", help="جداول كاملة")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    if not os.path.isdir(SCAN_ROOT):
        print(f"نطاق غير موجود: {SCAN_ROOT}", file=sys.stderr)
        return 2

    counts, sites, values, by_file = scan(SCAN_ROOT)
    radii = len(values["radius"])

    if args.update:
        write_baseline(counts, radii, by_file)
        print(f"✅ حُدّث الأساس: {sum(counts.values())} حرفيًّا · {radii} نصف قطر متمايز")
        return 0

    if args.json:
        print(json.dumps({"counts": counts, "distinct_radii": radii, "sites": sites}, ensure_ascii=False, indent=2))
        return 0

    baseline = load_baseline()
    if baseline is None:
        # لا سقف بعد = لا بوابة. نُعلن ذلك بوضوح بدل أن نطبع «نجاحًا» بلا معنى.
        print(f"لا أساس مكتوب ({BASELINE_REL}). شغّل `--update` لتثبيت الحالة الحالية سقفًا.")
        return 1 if args.assert_gate else 0

    base_counts: dict[str, int] = baseline["counts"]
    base_radii: int = baseline["distinct_radii"]

    print("فئة        الآن   السقف   الفرق")
    for key in sorted(counts):
        ceiling = base_counts.get(key, 0)
        delta = counts[key] - ceiling
        print(f"{key:<10} {counts[key]:>5} {ceiling:>7} {delta:>+7}")
    print(f"{'نصف قطر متمايز':<10} {radii:>5} {base_radii:>7} {radii - base_radii:>+7}")
    print(f"المجموع     {sum(counts.values()):>5} {sum(base_counts.values()):>7} "
          f"{sum(counts.values()) - sum(base_counts.values()):>+7}")

    if not args.assert_gate:
        return 0

    regressions: list[str] = []
    for key, now in counts.items():
        ceiling = base_counts.get(key, 0)
        if now > ceiling:
            regressions.append(f"{key}: {now} > {ceiling}")
    if radii > base_radii:
        regressions.append(f"distinct_radii: {radii} > {base_radii}")

    if regressions:
        print("\n❌ حرفيّات تصميم جديدة تجاوزت السقف:", file=sys.stderr)
        for line in regressions:
            print(f"   {line}", file=sys.stderr)
        # الملفّ الذي زاد فيه الحرفيّ — بالاسم، لا آخر ما مرّ به الماسح.
        culprits = newly_literal_files(counts, by_file, baseline)
        if culprits:
            print("\nالملفات التي زاد فيها الحرفيّ:", file=sys.stderr)
            for line in culprits:
                print(f"   {line}", file=sys.stderr)
        print("\nاستعمل رمزًا من `MaxRadius`/`MaxSpace`/`MaxCardSpec` بدل الرقم،"
              " وإن كان الرقم جديدًا فاستصوب إضافته إلى طبقة الرموز لا إلى الشاشة.", file=sys.stderr)
        return 1

    print("\nبوابة رموز التصميم: exit 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
