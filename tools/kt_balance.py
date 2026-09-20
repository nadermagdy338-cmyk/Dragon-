#!/usr/bin/env python3
"""توازن البنية في ملفات Kotlin/XML — تحقق بلا مُصرّف ولا جهاز.

لماذا وُجد
----------
أمر المالك: **لا تُشغّل بناءً كقاعدة**؛ البناء عند الطلب فقط. ولكن «بلا بناء» لا تعني
«بلا تحقّق» — وإلا صار التعديل تخمينًا يُسلَّم. فهذه الأداة تمسك أخطر ما يمسكه المُصرّف
في أول سطر: قوسٌ لم يُغلق، نصٌّ لم يُنهَ، تعليقٌ ابتلع بقية الملف.

وهي **ليست** بديلًا عن البناء، ولا تدّعي ذلك: تتحقق من **البنية** وحدها — لا أنواع ولا
دلالة. فحين لا يُبنى الملف، يجب أن يُقال صريحًا «الترجمة غير مُتحقّقة» في التسليم، وهذه
الأداة تقلّل احتمال أن يكون السبب سطرًا تافهًا.

ما تفحصه
--------
* `{}` `()` `[]` في Kotlin، مع تمييز ما هو كود ممّا هو نصّ:
  - تعليقات السطر، وتعليقات الكتل (**متداخلة** في Kotlin بخلاف C).
  - النصوص العادية `"..."`، والنصوص الخام `\"\"\"...\"\"\"` (وفيها تُحسب الإشارات الزائدة
    جزءًا من النص: `\"\"\"...\"update\"\"\"\"` نهايةٌ صحيحة كما يفهمها المُصرّف).
  - المحارف `'x'`، والقالب `${...}` الذي يُفحص **كوده** داخل النص لا عكسه.
* نصٌّ لم يُنهَ · تعليق كتلة لم يُغلق · قوسٌ مُغلق بلا فاتح · فاتحٌ لم يُغلق — برقم السطر.
* ملفات `res/**/**.xml`: صحة XML عبر محلّل المكتبة القياسية (بلا تبعيّة تُثبَّت).

الصيغة
------
    python3 tools/kt_balance.py                     # كل ملفات manager
    python3 tools/kt_balance.py --changed           # ما تغيّر في git فقط (الأسرع أثناء العمل)
    python3 tools/kt_balance.py --assert            # سطر حكم واحد وتُخرج بخطأ عند عطب
    python3 tools/kt_balance.py some/File.kt more.xml
    python3 tools/kt_balance.py --self-test         # يقيس الأداة نفسها على حالات معروفة

حدوده المعلنة
-------------
لا يقرأ الأنواع ولا الدلالة ولا الأسماء، ولا يفحص تطابق `package` مع المسار (ذلك في
`tools/code_health.py`). ونجاحه يعني «البنية سليمة»، لا «الشاشة تعمل». و`--self-test`
هو ضمانه: أداة تمرّ على كل شيء لا تُثبت شيئًا، فإن لم تمسك الحالات المعروفة فحكمها مردود.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.dirname(_HERE)
MANAGER = os.path.join(_REPO, "manager")

OPENERS = {"{": "}", "(": ")", "[": "]"}
CLOSERS = {v: k for k, v in OPENERS.items()}
LINE_NUMBER = re.compile(r"(\d+)")

# قوالب أسماؤها عربية في المخرجات لأنها اللغة المعتمدة في تقارير هذا المستودع.
PROBLEM_LINES = {
    "bracket_open": "«{opener}» لم يُغلق",
    "bracket_mismatch": "«{closer}» يغلق «{opener}» المفتوح في السطر {opened}",
    "bracket_extra": "«{closer}» بلا فاتح يقابله",
    "string_open": "نص لم يُغلق",
    "string_eol": "نص عادي عَبَر نهاية السطر بلا إغلاق",
    "block_comment": "تعليق كتلة لم يُغلق",
    "template_open": "قالب ${...} لم يُغلق",
    "backtick_open": "اسم بين علامتين مائيّتين لم يُغلق",
    "xml": "XML غير صالح: {detail}",
}


def repo_relative(path: str) -> str:
    return os.path.relpath(path, _REPO)


def manager_files() -> list[str]:
    """ملفات Kotlin وXML داخل manager، بلا مجلدات بناء."""
    out: list[str] = []
    for dp, dn, fn in os.walk(MANAGER):
        dn[:] = [d for d in dn if d not in ("build", ".gradle", ".kotlin")]
        for name in fn:
            if name.endswith((".kt", ".xml")):
                out.append(os.path.join(dp, name))
    return sorted(out)


def changed_files() -> list[str]:
    """ما تغيّر في git (بما فيه الجديد غير المُتتبَّع) من Kotlin/XML."""
    names: list[str] = []
    for args in (["git", "diff", "--name-only", "--diff-filter=ACMRT", "HEAD"],
                 ["git", "ls-files", "--others", "--exclude-standard"]):
        try:
            done = subprocess.run(args, cwd=_REPO, capture_output=True, text=True, check=False)
        except OSError:
            continue
        names += [line.strip() for line in done.stdout.splitlines() if line.strip()]
    picked = []
    for name in dict.fromkeys(names):
        full = os.path.join(_REPO, name)
        if name.endswith((".kt", ".xml")) and os.path.isfile(full):
            picked.append(full)
    return sorted(picked)


def check_xml(path: str) -> list[str]:
    try:
        ET.parse(path)
    except ET.ParseError as error:
        return [PROBLEM_LINES["xml"].format(detail=error)]
    return []


def check_kotlin(path: str) -> list[str]:
    """يمشي حرفًا حرفًا بمكدّس سياقات، فلا يخدعه قوسٌ في نص أو تعليق.

    السياقات: `bracket` (قوس كود) · `string` (نص عادي) · `template` (قالب `${`)
    بينهما. والوضع الحالي هو سياق القمة: قوسٌ أو قالب ⇐ كود، ونصٌّ ⇐ نص.
    """
    text = open(path, encoding="utf-8", errors="replace").read()
    problems: list[tuple[int, str]] = []
    frames: list[dict] = []
    line = 1
    i = 0
    n = len(text)

    def add(kind: str, at: int, **fields: object) -> None:
        problems.append((at, PROBLEM_LINES[kind].format(**fields)))

    while i < n:
        char = text[i]
        top = frames[-1] if frames else None

        # ── داخل نص عادي: لا تُحسب الأقواس، إلا قالب `${` فما داخله كود
        if top is not None and top["kind"] == "string":
            if char == "\\":
                i += 2
                continue
            if char == "\n":
                add("string_eol", top["line"], opened=top["line"])
                frames.pop()
                continue
            if char == top["quote"]:
                frames.pop()
                i += 1
                continue
            if char == "$" and i + 1 < n and text[i + 1] == "{":
                frames.append({"kind": "template", "line": line})
                i += 2
                continue
            i += 1
            continue

        # ── كود
        if char == "\n":
            line += 1
            i += 1
            continue

        if text.startswith("//", i):
            end = text.find("\n", i)
            i = n if end < 0 else end
            continue

        if text.startswith("/*", i):
            depth = 1
            opened = line
            i += 2
            while i < n and depth > 0:
                if text.startswith("/*", i):
                    depth += 1
                    i += 2
                elif text.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    if text[i] == "\n":
                        line += 1
                    i += 1
            if depth > 0:
                add("block_comment", opened)
            continue

        if text.startswith('"""', i):
            opened = line
            i += 3
            closed = False
            while i < n:
                if text[i] == '"':
                    run = i
                    while run < n and text[run] == '"':
                        run += 1
                    if run - i >= 3:
                        # آخر ثلاث إشارات هي النهاية، وما زاد عليها جزءٌ من النص.
                        i = run
                        closed = True
                        break
                    i = run
                    continue
                if text[i] == "\n":
                    line += 1
                i += 1
            if not closed:
                add("string_open", opened)
            continue

        # ── اسم بين علامتين مائيّتين (``fun `name with's apostrophe`()``): كود، لا نص
        #    ولا يُفتح قبله «نص» لمحرف `'` داخله. اسم اختبار فيه فاصلة عليا كان يُسقط الحكم كله.
        if char == "`":
            end = text.find("`", i + 1)
            if end < 0:
                add("backtick_open", line)
                i = n
                continue
            line += text[i:end].count("\n")
            i = end + 1
            continue

        if char in ('"', "'"):
            frames.append({"kind": "string", "quote": char, "line": line})
            i += 1
            continue

        if char in OPENERS:
            frames.append({"kind": "bracket", "opener": char, "line": line})
            i += 1
            continue

        if char in CLOSERS:
            if char == "}" and top is not None and top["kind"] == "template":
                frames.pop()
                i += 1
                continue
            if top is None or top["kind"] != "bracket":
                add("bracket_extra", line, closer=char)
                i += 1
                continue
            frames.pop()
            if OPENERS[top["opener"]] != char:
                add("bracket_mismatch", line, closer=char, opener=top["opener"], opened=top["line"])
            i += 1
            continue

        i += 1

    for frame in frames:
        if frame["kind"] == "bracket":
            add("bracket_open", frame["line"], opener=frame["opener"])
        elif frame["kind"] == "template":
            add("template_open", frame["line"])
        else:
            add("string_open", frame["line"])

    problems.sort(key=lambda row: row[0])
    return [f"السطر {at}: {text}" if at else text for at, text in problems]


def check(path: str) -> list[str]:
    return check_xml(path) if path.endswith(".xml") else check_kotlin(path)


# ─────────────────────────── قياس الأداة نفسها ───────────────────────────

SELF_TEST_CASES: list[tuple[str, str, int]] = [
    ("توازن سليم", "fun a() { val b = listOf(1, 2) }\n", 0),
    ("قوس ناقص", "fun a() {\n    val b = 1\n", 1),
    ("قوس زائد", "fun a() {}\n}\n", 1),
    ("قوس داخل نص — لا يُحسب", 'val s = "}{"\nfun a() {}\n', 0),
    ("قوس داخل تعليق — لا يُحسب", "// }\nfun a() {}\n", 0),
    ("تعليق كتلة متداخل", "/* /* } */ */\nfun a() {}\n", 0),
    ("تعليق كتلة ناقص", "/* {\nfun a() {}\n", 1),
    ("نص خام فيه إشارات", '@Query("""SELECT * FROM "update"""")\nfun a() {}\n', 0),
    ("نص خام ناقص", 'val s = """abc\nfun a() {}\n', 1),
    ("قالب نصّي فيه نص داخلي", 'val s = "\'${value.replace("\'", "")}\'"\nfun a() {}\n', 0),
    ("نص عادي ناقص", 'val s = "abc\nfun a() {}\n', 1),
    ("محرف قوس — لا يُحسب", "val c = '{'\nfun a() {}\n", 0),
    ("اسم بعلامتين مائيّتين فيه فاصلة عليا", "@Test\nfun `a provider's domain`() {}\n", 0),
    ("اسم بعلامتين مائيّتين فيه قوس", "@Test\nfun `a { bracket }`() {}\n", 0),
    ("اسم بعلامتين مائيّتين لم يُغلق", "@Test\nfun `a name() {}\n", 1),
]


def self_test() -> int:
    failures = 0
    with tempfile.TemporaryDirectory() as tmp:
        for name, body, expected in SELF_TEST_CASES:
            path = os.path.join(tmp, "case.kt")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(body)
            got = check_kotlin(path)
            ok = len(got) == expected
            failures += 0 if ok else 1
            mark = "✓" if ok else "✗"
            print(f"  {mark} {name}: متوقّع {expected} · حاصل {len(got)}")
            for row in got:
                print(f"        {row}")
        xml_ok = os.path.join(tmp, "ok.xml")
        with open(xml_ok, "w", encoding="utf-8") as handle:
            handle.write("<resources><string name=\"a\">b</string></resources>\n")
        xml_bad = os.path.join(tmp, "bad.xml")
        with open(xml_bad, "w", encoding="utf-8") as handle:
            handle.write("<resources><string name=\"a\">b</resources>\n")
        for name, path, expected in (("XML سليم", xml_ok, 0), ("XML مبتور", xml_bad, 1)):
            got = check_xml(path)
            ok = len(got) == expected
            failures += 0 if ok else 1
            print(f"  {'✓' if ok else '✗'} {name}: متوقّع {expected} · حاصل {len(got)}")
    print(f"قياس الأداة: {len(SELF_TEST_CASES) + 2} حالة · إخفاقات {failures}")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="توازن أقواس Kotlin وصحة XML بلا بناء")
    parser.add_argument("paths", nargs="*", help="ملفات محدّدة (الافتراضي: كل manager)")
    parser.add_argument("--changed", action="store_true", help="ما تغيّر في git فقط")
    parser.add_argument("--assert", dest="gate", action="store_true", help="حكم واحد وتُخرج بخطأ عند عطب")
    parser.add_argument("--self-test", action="store_true", help="قياس الأداة على حالات معروفة")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    if args.paths:
        files = [os.path.abspath(p) for p in args.paths]
    else:
        files = changed_files() if args.changed else manager_files()
    files = [f for f in files if os.path.isfile(f)]

    broken: list[tuple[str, list[str]]] = []
    for path in files:
        problems = check(path)
        if problems:
            broken.append((repo_relative(path), problems))

    if args.gate:
        print(f"توازن البنية: {len(files)} ملفًا · عوائق {len(broken)}")
        for name, problems in broken:
            print(f"  {name}")
            for row in problems[:6]:
                print(f"      {row}")
        return 1 if broken else 0

    print(f"فُحص {len(files)} ملفًا (Kotlin/XML) — عوائق: {len(broken)}")
    for name, problems in broken:
        print(f"\n{name}")
        for row in problems:
            print(f"    {row}")
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
