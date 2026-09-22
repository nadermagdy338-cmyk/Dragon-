#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""بصمة المصادر — «هل شجرتك هي هذه الشجرة؟» سؤال يُجاب برقم لا برأي.

**المشكلة المقيسة (تكملات ٨٨–٩٢):** بناء ينجح عندنا بكل أرقامه (اختبارات · R8 · APK) ويفشل
على CI في ملف **يبدو سليمًا هنا**. والممكن حينها واحد من اثنين لا ثالث: شجرتان مختلفتان، أو
بيئة مختلفة. وهذا التشخيص كان يكلّف جولة كاملة لأن سجل CI لا يسمّي الفرق.

فهذه الأداة تُنتج قائمة `sha256  مسار` **مطابقة حرفيًّا بين أي جهاز**، وتُقارن بالمرجع المرفوع
فتطبع الفروق **بالاسم** داخل سجل CI نفسه.

    python3 tools/source_manifest.py                  # اطبع القائمة كاملة
    python3 tools/source_manifest.py --summary        # رقمان: عدد الملفات + بصمة واحدة
    python3 tools/source_manifest.py --write          # اكتب المرجع في docs/ai/source-manifest.txt
    python3 tools/source_manifest.py --check          # قارن بالمرجع: مختلف/غائب/زائد بالاسم
    python3 tools/source_manifest.py --check --assert # رمز خروج 1 عند أي فرق
    python3 tools/source_manifest.py --self-test      # الأداة تقيس نفسها

**وحدّها المعلن:** تقول **أيّ** ملف اختلف ولا تقول **لماذا**؛ ولماذا يبقى عملًا بشريًّا.
ولا تُعدّ الأداة بوابة إلزامية (`--assert` لا يُستعمل في CI): اختلاف البصمة **تشخيص**، لا فشل.
"""

from __future__ import annotations

import argparse
import glob
import hashlib
import os
import shutil
import sys
import tempfile

# ── ما يدخل البصمة: كل ما يُصرَّف أو يحكم البناء ────────────────────────────────
#
# المدى مقصود ولا يُوسَّع عشوائيًّا: `manager/app/src` (المصادر والموارد) · ملفات البناء
# التي تُهيّئ الوحدات كلها · مخطّطات CI · أدوات البوابات. و`docs/**` **خارجها عن قصد**:
# التوثيق يتغيّر في كل تسليم، فإدخاله يجعل كل مقارنة «مختلفة» فتفقد البصمة معناها.
ROOTS = (
    "manager/app/src",
    ".github/workflows",
    "tools",
)

EXACT_FILES = (
    "manager/settings.gradle.kts",
    "manager/build.gradle.kts",
    "manager/gradle.properties",
    "manager/gradle/libs.versions.toml",
    "manager/gradle/wrapper/gradle-wrapper.properties",
)

GLOBS = (
    "manager/*/build.gradle",
)

# امتدادات النصّ التي تُصرَّف أو تُقرأ: المحرك الأصلي للبناء ووحداته.
EXTS = frozenset(
    {
        ".kt", ".kts", ".java", ".xml", ".gradle", ".properties",
        ".toml", ".yml", ".yaml", ".py", ".pro", ".cfg",
    }
)

# مجلّدات لا تُقرأ أبدًا: مخرجات بناء أو مخازن أو نسخ.
SKIP_DIRS = frozenset({"build", ".gradle", "target", "node_modules", ".git", ".idea"})

MANIFEST_REL = "docs/ai/source-manifest.txt"
HEADER = "# بصمة المصادر — لا تُحرَّر يدويًّا: `python3 tools/source_manifest.py --write`\n"


def repo_root(start: str | None = None) -> str:
    """جذر المستودع من موضع الأداة (لا من مجلّد العمل) — فلا يهمّ من أين تُنادى."""
    here = os.path.abspath(start or os.path.dirname(os.path.abspath(__file__)))
    d = here
    for _ in range(8):
        if os.path.isdir(os.path.join(d, "manager")) and os.path.isdir(os.path.join(d, "tools")):
            return d
        parent = os.path.dirname(d)
        if parent == d:
            break
        d = parent
    return here


def _rel(path: str, root: str) -> str:
    return os.path.relpath(path, root).replace(os.sep, "/")


def _wanted(rel: str) -> bool:
    _, ext = os.path.splitext(rel)
    return ext.lower() in EXTS


def collect(root: str) -> list[str]:
    """مسارات نسبية مرتّبة لكل ملف يدخل البصمة. الترتيب جزء من العقد: البصمة تُقارن."""
    found: set[str] = set()

    for sub in ROOTS:
        base = os.path.join(root, sub)
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
            for name in filenames:
                rel = _rel(os.path.join(dirpath, name), root)
                if _wanted(rel):
                    found.add(rel)

    for rel in EXACT_FILES:
        if os.path.isfile(os.path.join(root, rel)):
            found.add(rel)

    for pattern in GLOBS:
        for path in glob.glob(os.path.join(root, pattern)):
            if os.path.isfile(path) and not any(p in SKIP_DIRS for p in _rel(path, root).split("/")):
                found.add(_rel(path, root))

    return sorted(found)


def _sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def lines(root: str, paths: list[str] | None = None) -> list[str]:
    """أسطر `sha256  مسار` — صيغة `sha256sum` نفسها، فلا تحتاج أداة خصوصية للقراءة."""
    paths = collect(root) if paths is None else paths
    return [f"{_sha256(os.path.join(root, p))}  {p}" for p in paths]


def digest(manifest_lines: list[str]) -> str:
    """بصمة واحدة للنصّ كله: رقم يقارَن في سطر واحد."""
    return hashlib.sha256(("".join(l + "\n" for l in manifest_lines)).encode("utf-8")).hexdigest()


def read_manifest(root: str) -> list[str] | None:
    path = os.path.join(root, MANIFEST_REL)
    if not os.path.isfile(path):
        return None
    out = []
    with open(path, encoding="utf-8") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            out.append(line)
    return out


def compare(reference: list[str], current: list[str]) -> dict[str, list[str]]:
    ref = {l.split("  ", 1)[1]: l.split("  ", 1)[0] for l in reference}
    cur = {l.split("  ", 1)[1]: l.split("  ", 1)[0] for l in current}
    return {
        "changed": sorted(p for p in ref.keys() & cur.keys() if ref[p] != cur[p]),
        "missing": sorted(ref.keys() - cur.keys()),
        "added": sorted(cur.keys() - ref.keys()),
    }


# ── الأوضاع ────────────────────────────────────────────────────────────────────


def mode_summary(root: str) -> int:
    paths = collect(root)
    ls = lines(root, paths)
    print(f"SOURCE-FILES: {len(paths)}")
    print(f"SOURCE-DIGEST: {digest(ls)[:16]}")
    return 0


def mode_write(root: str) -> int:
    paths = collect(root)
    ls = lines(root, paths)
    dest = os.path.join(root, MANIFEST_REL)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "w", encoding="utf-8") as fh:
        fh.write(HEADER)
        fh.write("\n".join(ls))
        fh.write("\n")
    print(f"كُتب المرجع: {MANIFEST_REL} · {len(ls)} ملفًا · بصمة {digest(ls)[:16]}")
    return 0


def mode_check(root: str, assert_on_diff: bool = False) -> int:
    reference = read_manifest(root)
    if reference is None:
        print(f"لا مرجع مرفوع ({MANIFEST_REL}) — أنشئه بـ`--write`.")
        return 0

    current = lines(root)
    diff = compare(reference, current)
    total = sum(len(v) for v in diff.values())

    print(f"SOURCE-FILES: {len(current)}   (المرجع: {len(reference)})")
    print(f"SOURCE-DIGEST: {digest(current)[:16]}   (المرجع: {digest(reference)[:16]})")

    if not total:
        print("✅ الشجرة مطابقة للمرجع ملفًا بملف.")
        return 0

    print(f"⚠️ فرق في {total} ملفًا عن الشجرة المرجعية:")
    for label, key in (("مختلف", "changed"), ("غائب عندك", "missing"), ("زائد عندك", "added")):
        items = diff[key]
        if not items:
            continue
        print(f"  ── {label} ({len(items)}):")
        for path in items[:25]:
            print(f"       {path}")
        if len(items) > 25:
            print(f"       … و{len(items) - 25} أخرى")
    return 1 if assert_on_diff else 0


def mode_self_test() -> int:
    """الأداة تقيس نفسها: شجرة مصنوعة، ونتائج متوقّعة معروفة مسبقًا."""
    checks: list[tuple[str, bool, str]] = []
    tmp = tempfile.mkdtemp(prefix="srcman-")
    try:
        root = os.path.join(tmp, "repo")
        def put(rel: str, body: str) -> None:
            path = os.path.join(root, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(body)

        put("manager/app/src/main/A.kt", "a")
        put("manager/app/src/main/sub/B.xml", "b")
        put("manager/app/src/test/T.kt", "t")
        put("manager/app/src/main/build/Ignored.kt", "x")   # مجلّد build: مُستثنى
        put("manager/app/src/main/target/Ignored2.kt", "x")  # مجلّد target: مُستثنى
        put("manager/app/src/main/notes.md", "n")           # امتداد خارج القائمة
        put(".github/workflows/w.yml", "w")
        put("tools/t.py", "p")
        put("manager/settings.gradle.kts", "s")
        put("manager/app/build.gradle", "g")
        put("manager/app/build/Generated.kt", "x")          # داخل build: مُستثنى
        put("docs/ai/source-manifest.txt", "")              # ليس مصدرًا

        got = collect(root)
        expected = sorted(
            [
                ".github/workflows/w.yml",
                "manager/app/build.gradle",
                "manager/app/src/main/A.kt",
                "manager/app/src/main/sub/B.xml",
                "manager/app/src/test/T.kt",
                "manager/settings.gradle.kts",
                "tools/t.py",
            ]
        )
        checks.append(("الجمع يستثني build/target ويرفض الامتدادات الأخرى", got == expected, f"{got}"))

        first = digest(lines(root, got))

        # تغيير بايت واحد في ملف واحد ⇒ البصمة تتغيّر (وإلا فالأداة لا تقيس شيئًا).
        put("manager/app/src/main/sub/B.xml", "b2")
        checks.append(("تغيير بايت يغيّر البصمة", digest(lines(root, got)) != first, ""))

        # ملف جديد ⇒ العدد يزيد (وإلا فالجمع ناقص).
        put("manager/app/src/main/sub/C.kt", "c")
        checks.append(("ملف جديد يزيد العدد", len(collect(root)) == len(expected) + 1, ""))

        # مقارنة بمرجع: مطابق ⇒ صفر · مُعدَّل ⇒ `changed` ∩ `added`
        ref = lines(root)
        checks.append(("المطابق يُقرأ مطابقًا", compare(ref, ref) == {"changed": [], "missing": [], "added": []}, ""))
        put("manager/app/src/main/sub/B.xml", "b3")
        d = compare(ref, lines(root))
        checks.append(("المُعدَّل يُسمّى في changed", d["changed"] == ["manager/app/src/main/sub/B.xml"], f"{d['changed']}"))

        # الجذر يُكتشف من موضع الأداة لا من مجلّد العمل.
        checks.append(("اكتشاف الجذر", repo_root(os.path.join(root, "tools")) == root, ""))

        ok = True
        for name, passed, detail in checks:
            print(("✅ " if passed else "❌ ") + name + (f"  ← {detail}" if detail and not passed else ""))
            ok = ok and passed
        print(f"\nنتيجة الاختبار الذاتي: {sum(1 for _, p, _ in checks if p)}/{len(checks)}")
        return 0 if ok else 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="بصمة المصادر: قائمة هاش قابلة للمقارنة بين أي جهازين.")
    parser.add_argument("--summary", action="store_true", help="رقمان: عدد الملفات + بصمة واحدة")
    parser.add_argument("--write", action="store_true", help="اكتب المرجع في docs/ai/")
    parser.add_argument("--check", action="store_true", help="قارن بالمرجع وسمِّ الفروق")
    parser.add_argument("--assert", dest="assert_on_diff", action="store_true", help="مع --check: exit 1 عند أي فرق")
    parser.add_argument("--self-test", action="store_true", help="الأداة تقيس نفسها")
    parser.add_argument("--root", default=None, help="جذر الشجرة (افتراضيًا يُكتشف من موضع الأداة)")
    args = parser.parse_args(argv)

    if args.self_test:
        return mode_self_test()
    root = repo_root(args.root)
    if args.write:
        return mode_write(root)
    if args.check:
        return mode_check(root, args.assert_on_diff)
    if args.summary:
        return mode_summary(root)
    print("\n".join(lines(root)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
