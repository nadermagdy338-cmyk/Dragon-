#!/usr/bin/env python3
"""فاحص نظافة المشروع — يقيس دَين الصيانة بالأرقام ويُفرض على شكل بوابة.

لماذا وُجد هذا الملف
--------------------
«الكود نظيف» حكم شخصي، والأحكام الشخصية تُنسى بين الجلسات. هذا الفاحص يحوّلها إلى
رقمين: **صحّة** (يجب أن تكون صفرًا) و**دَين** (مسموح به اليوم، ممنوع أن يزيد غدًا).

التشغيل (من أي مجلد — الجذر يُستنتج من موقع الملف):
    python3 tools/code_health.py              # تقرير كامل
    python3 tools/code_health.py --assert     # يخرج بـ 1 عند أي تراجع
    python3 tools/code_health.py --baseline   # يثبّت الدَّين الحالي كسقف (قرار مُعلن)

الفرق بين القسمين
-----------------
- **صحّة**: عيب يُفسد البناء أو يُظهر نصًّا خطأ. المطلوب صفر دائمًا.
- **دَين**: حجم ملف، استيراد شامل، نصُّ واجهة صلب. ليس خطأً بحد ذاته، لكنه يكلّف كل
  من يقرأ الكود بعده — ومنهم وكلاء الذكاء الاصطناعي. لذلك يُجمَّد عند سقف ولا يُسمح
  له بالنمو. إصلاح جزء منه يُخفض السقف عمدًا بـ `--baseline`.

قاعدة صارمة نتعلّمناها في هذه الشجرة: **لا تحكم على رمز من اسم ملف، ولا على ملف من
مجلد**. كل فحص هنا يقرأ الرمز أو المصدر، لا الاسم.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.dirname(_HERE)
APP = os.path.join(_REPO, "manager", "app", "src", "main")
RES = os.path.join(APP, "res")
JAVA = os.path.join(APP, "java")
MANAGER = os.path.join(_REPO, "manager")
BASELINE_PATH = os.path.join(_HERE, "code_health_baseline.json")

if not os.path.isdir(APP):
    sys.exit(f"لم يُعثر على شجرة التطبيق: {APP}")

# سقف «الملف الضخم». الملف الذي يتجاوز هذا الحد يستهلك نافذة قراءة كاملة لتعديل سطر
# واحد، وهذا هو أغلى ما يدفعه من يعدّل المشروع بعدنا.
OVERSIZE_LINES = 1000

# ملفات الجذر المسموح بها. أي ملف آخر في الجذر = حطام أو ملف شخصي تسرّب.
ROOT_ALLOWED = {
    ".gitattributes", ".gitignore", "AGENTS.md", "LICENSE", "NOTICE.md", "README.md",
    "changelog.md", "crowdin.yml", "logo.jpg", "maxmanagerApplist.json", "module.json",
    "update.json", "version", "version_type",
}


# ─────────────────────────── أدوات مساعدة ───────────────────────────

def kt_files() -> list[str]:
    """ملفات Kotlin الحقيقية في شجرة manager، بلا مجلدات بناء."""
    out = []
    for dp, dn, fn in os.walk(MANAGER):
        dn[:] = [d for d in dn if d not in ("build", ".gradle", ".kotlin")]
        for f in fn:
            if f.endswith(".kt"):
                out.append(os.path.join(dp, f))
    return sorted(out)


def rel(p: str) -> str:
    return os.path.relpath(p, _REPO)


def path_package(p: str) -> str | None:
    """الحزمة التي يفرضها مسار الملف. None إن لم يكن تحت java/ أو kotlin/."""
    for marker in (os.sep + "java" + os.sep, os.sep + "kotlin" + os.sep):
        if marker in p:
            root = p.split(marker)[0] + marker[:-1]
            return os.path.relpath(os.path.dirname(p), root).replace(os.sep, ".")
    return None


def resource_index(res_dir: str) -> dict[str, set[str]]:
    """كل أسماء الموارد المتاحة داخل مجلد `res` واحد، لكل نوع.

    درسان دفعتا ثمنهما، وكلاهما يُنتج بلاغات **زائفة** لا أعطالًا حقيقية:

    1. الموارد غير النصية (drawable/mipmap/raw/font) ملفات **ثنائية** لا XML، فأي
       فاحص يقرأ XML وحده يُبلّغ عنها كـ«ناقصة» كذبًا — حدث مع ٥ نداءات
       `R.drawable.*` صحيحة تمامًا.
    2. `R` صنف **لكل موديل**، لا للمستودع كله. فحص مراجع `kernel-flasher` ضد موارد
       `app` يُنتج ٧٤ بلاغًا كاذبًا. لذلك يأخذ هذا الدال مجلد res الخاص بالموديل،
       ويُستدعى مرة لكل موديل.
    """
    idx: dict[str, set[str]] = {}

    def add(kind: str, name: str) -> None:
        idx.setdefault(kind, set()).add(name)

    for kind in ("drawable", "mipmap", "raw", "font", "xml", "anim", "color", "menu"):
        for p in glob.glob(os.path.join(res_dir, f"{kind}*", "*")):
            if os.path.isfile(p):
                add(kind, os.path.splitext(os.path.basename(p))[0])

    value_kinds = {
        "string", "string-array", "plurals", "color", "bool", "integer", "dimen",
        "style", "array", "id", "attr", "fraction",
    }
    for p in glob.glob(os.path.join(res_dir, "values", "**", "*.xml"), recursive=True):
        try:
            root = ET.parse(p).getroot()
        except ET.ParseError:
            continue
        for node in root.iter():
            if node.tag in value_kinds and node.get("name"):
                add(node.tag, node.get("name"))

    # المعرّفات تُعلَن داخل التخطيطات والقوائم، لا في values/.
    for sub in ("layout", "menu", "xml"):
        for p in glob.glob(os.path.join(res_dir, sub, "**", "*.xml"), recursive=True):
            try:
                txt = open(p, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            for name in re.findall(r'@\+?id/(\w+)', txt):
                add("id", name)

    return idx


def module_resources() -> dict[str, dict[str, set[str]]]:
    """خريطة: جذر الموديل ← فهرس موارده. موديل واحد قد يحمل أكثر من مجلد res."""
    mods: dict[str, dict[str, set[str]]] = {}
    for res_dir in sorted(glob.glob(os.path.join(MANAGER, "*", "src", "*", "res"))):
        module = res_dir.split(os.sep + "src" + os.sep)[0]
        merged = mods.setdefault(module, {})
        for kind, names in resource_index(res_dir).items():
            merged.setdefault(kind, set()).update(names)
    return mods


def module_of(path: str, mods: dict) -> str | None:
    for module in mods:
        if path.startswith(module + os.sep):
            return module
    return None


# ─────────────────────────── الفحوص ───────────────────────────

R_REF = re.compile(r"(?<!android\.)\bR\.(\w+)\.(\w+)")
WILDCARD = re.compile(r"^import\s+([\w.]+)\.\*$", re.M)
PKG = re.compile(r"^package\s+([\w.]+)", re.M)
# نص واجهة صلب: Text("…") أو Text(text = "…") بنص حرفي لا قالب ولا رموز فقط.
# نص واجهة صلب = استدعاء Compose `Text("...")`. و`\b` وحدها لا تكفي: نقطة قبلها
# (`AtlasTransportRead.Text("42000\n")`) تجعل الحدّ قائمًا، فيُحسب **نصّ اختبار** نصًّا صلبًا
# ويصير السقف عددًا يتغيّر بكتابة اختبار. الاستثناء الصريح: لا نقطة ولا محرف كلمة قبل `Text(`.
TEXT_LITERAL = re.compile(r'(?<![\w.])Text\(\s*(?:text\s*=\s*)?"([^"\\]*(?:\\.[^"\\]*)*)"')
HAS_LETTER = re.compile(r"[A-Za-z\u0600-\u06FF]")


def check_correctness(files: list[str], mods: dict) -> dict:
    result: dict[str, list[str]] = {
        "package_mismatch": [], "unresolved_resource": [], "duplicate_string_key": [],
        "stray_root_file": [],
    }

    for p in files:
        expected = path_package(p)
        m = PKG.search(open(p, encoding="utf-8", errors="replace").read())
        if expected and m and m.group(1) != expected:
            result["package_mismatch"].append(
                f"{rel(p)}: معلن '{m.group(1)}' والمتوقع '{expected}'"
            )

    for p in files:
        idx = mods.get(module_of(p, mods) or "", {})
        txt = open(p, encoding="utf-8", errors="replace").read()
        for kind, name in R_REF.findall(txt):
            if kind in idx and name not in idx[kind]:
                result["unresolved_resource"].append(f"{rel(p)}: R.{kind}.{name}")

    for d in sorted(glob.glob(os.path.join(RES, "values*"))):
        seen: dict[str, str] = {}
        for p in glob.glob(os.path.join(d, "*.xml")):
            try:
                root = ET.parse(p).getroot()
            except ET.ParseError as exc:
                result["duplicate_string_key"].append(f"{rel(p)}: XML غير صالح ({exc})")
                continue
            for node in root:
                name = node.get("name")
                if name in seen:
                    result["duplicate_string_key"].append(
                        f"{os.path.basename(d)}: '{name}' في {seen[name]} و{os.path.basename(p)}"
                    )
                elif name:
                    seen[name] = os.path.basename(p)

    # ملف الجذر يُعدّ حطامًا فقط إن لم يكن متعقّبًا **ولم يكن متجاهلًا**: علامة أداة
    # محلية مذكورة في .gitignore ليست ضجيجًا، فلا تُبلَّغ.
    for name in sorted(os.listdir(_REPO)):
        if name.startswith(".git") or name == "build":
            continue
        full = os.path.join(_REPO, name)
        if not os.path.isfile(full) or name in ROOT_ALLOWED:
            continue
        tracked = subprocess.run(
            ["git", "ls-files", "--error-unmatch", name],
            cwd=_REPO, capture_output=True,
        ).returncode == 0
        ignored = subprocess.run(
            ["git", "check-ignore", "-q", name], cwd=_REPO, capture_output=True,
        ).returncode == 0
        if not tracked and not ignored:
            result["stray_root_file"].append(name)

    return {k: sorted(set(v)) for k, v in result.items()}


# كتابة العتاد من طبقة العرض (ADR-11).
# الاصطياد هنا يجب أن يكون دقيقًا، وقد أخطأنا مرتين قبل أن يستقيم:
#   ① `2>/dev/null` إخماد لـ stderr لا كتابة إلى عقدة — مجرّد مطابقة `>` أعطت ٨٧ بلاغًا كاذبًا.
#   ② وصف الحالة داخل تعليق/KDoc ليس كودًا — سطر واحد في NetworkSchedulerViewModel مطابق نصيًا.
# ولذلك: نقرأ الكود المُنفّذ فقط، ونطلب أداة كتابة صريحة (echo/printf/tee) مع إعادة توجيه.
SHELL_WRITE = re.compile(r"\b(echo|printf|tee)\b[^\n]*>>?[^\n/]", re.I)
ROOTFILE_WRITE = re.compile(r"RootFileAccess\s*\.\s*(atomicWriteText|write|writeText|append)\s*\(")
COMMENT_LINE = re.compile(r"^(//|\*|/\*)")


def collect_debt(files: list[str]) -> dict:
    """دَين الصيانة القابل للقياس."""
    oversized, own_wildcards, literals = [], [], []
    presentation_writes = []
    platform_wildcards = 0
    todos = 0

    for p in files:
        txt = open(p, encoding="utf-8", errors="replace").read()
        lines = txt.count("\n") + 1
        if lines > OVERSIZE_LINES:
            oversized.append((rel(p), lines))

        for imp in WILDCARD.findall(txt):
            if imp == "nd.max" or imp.startswith("nd.max."):
                own_wildcards.append((rel(p), imp))
            else:
                platform_wildcards += 1

        for lit in TEXT_LITERAL.findall(txt):
            if "$" in lit or not HAS_LETTER.search(lit) or not lit.strip():
                continue
            literals.append((rel(p), lit))

        todos += len(re.findall(r"\b(TODO|FIXME|XXX|HACK)\b", txt))

        # كتابة عتاد من طبقة العرض — ADR-11.
        # النطاق صريح: تطبيق `nd.max` وحده. لا يُحتسب `kernel-flasher` (فورك متضمّن بمسؤوليته)
        # ولا `ui/util/` (طبقة الأدوات، وهي المسار المشروع للكتابة بحسب نصّ ADR-11).
        rp = rel(p).replace(os.sep, "/")
        if "/app/src/main/java/nd/max/ui/" in rp and "/ui/util/" not in rp:
            for i, line in enumerate(txt.splitlines(), 1):
                s = line.strip()
                if COMMENT_LINE.match(s):
                    continue
                hit = SHELL_WRITE.search(s) or ROOTFILE_WRITE.search(s)
                if hit:
                    presentation_writes.append(f"{rp}:{i}: {s[:70]}")

    return {"presentation_hw_writes": presentation_writes,
        "oversized_files": oversized,
        "own_wildcard_imports": own_wildcards,
        "hardcoded_ui_literals": literals,
        "platform_wildcard_imports": platform_wildcards,
        "todo_markers": todos,
        "counts": {
            "oversized_files": len(oversized),
            "own_wildcard_imports": len(own_wildcards),
            "hardcoded_ui_literals": len(literals),
            "presentation_hw_writes": len(presentation_writes),
        },
    }


# ─────────────────────────── العرض ───────────────────────────

def show_report(correctness: dict, debt: dict, files: list[str]) -> None:
    total_lines = sum(open(p, encoding="utf-8", errors="replace").read().count("\n") + 1 for p in files)

    print("═" * 72)
    print("الصحّة — المطلوب صفر")
    print("═" * 72)
    clean = True
    for key, issues in correctness.items():
        mark = "✓" if not issues else "✗"
        print(f"  {mark} {key}: {len(issues)}")
        for line in issues[:8]:
            print(f"      - {line}")
        if len(issues) > 8:
            print(f"      … و{len(issues) - 8} غيرها")
        clean = clean and not issues

    print()
    print("═" * 72)
    print("الدَّين — مُجمَّد عند سقف، لا يُسمح له بالنمو")
    print("═" * 72)
    for key, value in debt["counts"].items():
        print(f"  {value:>5}  {key}")
    print(f"  {debt['todo_markers']:>5}  todo_markers (تُراجَع، لا تُفرض)")
    print(f"  {debt['platform_wildcard_imports']:>5}  platform_wildcard_imports (لا تُحتسب)")
    print()
    print(f"  ملفات Kotlin: {len(files)}  ·  الأسطر: {total_lines}")

    if debt["oversized_files"]:
        print()
        print("  أكبر الملفات (تجاوزت 1000 سطر):")
        for name, lines in sorted(debt["oversized_files"], key=lambda x: -x[1]):
            print(f"    {lines:>5}  {name}")

    if debt["own_wildcard_imports"]:
        print()
        print("  استيرادات شاملة لكود المشروع نفسه (تُخفي مصدر الرمز):")
        for name, imp in debt["own_wildcard_imports"]:
            print(f"    {imp}  <-  {name}")

    if debt["presentation_hw_writes"]:
        print()
        print(f"  كتابة عتاد من طبقة العرض (ADR-11) — {len(debt['presentation_hw_writes'])} موضعًا:")
        by_file: dict[str, int] = {}
        for row in debt["presentation_hw_writes"]:
            by_file[row.split(":")[0]] = by_file.get(row.split(":")[0], 0) + 1
        for name, n in sorted(by_file.items(), key=lambda x: -x[1]):
            print(f"    {n:>3}  {name}")

    if debt["hardcoded_ui_literals"]:
        print()
        print(f"  نصوص واجهة صلبة (أول 15 من {len(debt['hardcoded_ui_literals'])}):")
        for name, lit in debt["hardcoded_ui_literals"][:15]:
            print(f"    {name}: {lit[:60]!r}")

    print()
    print("الحصيلة:", "صحّة نظيفة" if clean else "توجد عيوب صحّة — انظر أعلاه")


def main() -> int:
    ap = argparse.ArgumentParser(description="فاحص نظافة المشروع")
    ap.add_argument(
        "--assert",
        dest="assert_gate",  # 'assert' كلمة محجوزة، فلا يمكن استخدامها كاسم سمة
        action="store_true",
        help="يفشل عند أي تراجع عن السقف",
    )
    ap.add_argument("--baseline", action="store_true", help="يثبّت الدَّين الحالي كسقف")
    ap.add_argument("--json", action="store_true", help="يخرج تقريرًا JSON")
    args = ap.parse_args()

    files = kt_files()
    mods = module_resources()
    correctness = check_correctness(files, mods)
    debt = collect_debt(files)

    if args.baseline:
        payload = {
            "_note": "سقف دَين الصيانة. انخفاضه مطلوب عند كل إصلاح؛ ارتفاعه قرار يجب مبرّره.",
            "oversized_files": debt["counts"]["oversized_files"],
            "own_wildcard_imports": debt["counts"]["own_wildcard_imports"],
            "hardcoded_ui_literals": debt["counts"]["hardcoded_ui_literals"],
            "presentation_hw_writes": debt["counts"]["presentation_hw_writes"],
        }
        with open(BASELINE_PATH, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        print(f"ثُبّت السقف في {rel(BASELINE_PATH)}: {payload}")
        return 0

    if args.json:
        print(json.dumps({"correctness": correctness, "debt": debt}, ensure_ascii=False, indent=2))
        return 0

    show_report(correctness, debt, files)

    if not args.assert_gate:
        return 0

    failures: list[str] = []
    for key, issues in correctness.items():
        if issues:
            failures.append(f"{key}: {len(issues)}")

    if os.path.exists(BASELINE_PATH):
        base = json.load(open(BASELINE_PATH, encoding="utf-8"))
        for key, now in debt["counts"].items():
            was = base.get(key, now)
            if now > was:
                failures.append(f"{key}: {was} -> {now} (تجاوز السقف)")
            elif now < was:
                print(f"  ملاحظة: {key} انخفض {was} -> {now}؛ خفّض السقف بـ --baseline.")
    else:
        failures.append("لا يوجد سقف مثبّت — شغّل --baseline")

    if failures:
        print()
        print("فشل البوابة:")
        for f in failures:
            print("  ✗", f)
        return 1
    print()
    print("بوابة النظافة: exit 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
