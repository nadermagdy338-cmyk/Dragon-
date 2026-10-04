#!/usr/bin/env python3
"""بوابة ترجمة الموارد — تُشغّل **aapt2 الحقيقي** على كل مجلّد `res` قبل البناء.

لماذا وُجدت
-----------
عطبُ موردٍ لا يُشخَّص بقراءة الكود: `aapt2` هو الحاكم، وحكمه يُقرأ بالتشغيل. وقد كلّف غياب هذا
التشغيل المبكر جولتَي CI كاملتين في تكملة ١٤٢، ورسالته **لا تسمّي السبب**:

    ✗ Can not extract resource from aaptcompiler.ParsedResource@…
    ✗ multiple substitutions specified in non-positional format; did you mean to add
      the formatted="false" attribute?

القياس الذي بُنيت عليه (تكملة ١٤٢، على ١٣٦٣ ملفّ موارد في `manager/*/src/*/res/values-*`):

| العطب | العدد | كيف بقي صامتًا |
| --- | --- | --- |
| `\\\\'` (شرطة خلفية حرفية ثم فاصلة عارية) | **٥٨٥ موضعًا في ٨٢ لغة** | `escape_android` كان يُهرّب كل `'` بلا فحص فيُضاعف المُهرَّبة، و`code_health` لم تكن ترى إلا `'` بلا شرطة قبلها |
| فاصلة `%` عارية زادها المترجم | **٥ مواضع** (eu · tr) | مقارنة *مجموعة* الوسائط تُمرّرها: `%1$d` موجودة عند الطرفين، والزائد حرفٌ لم يُقارن |
| `%` عارية في نصّ غير مُنسَّق (‏`(85%)`) | **٨٣ ملفًّا** | لا بوّابة كانت تقيس ما يقيسه `aapt2` |

فالبوّابة هنا **لا تُعيد تمثيل حكم aapt2** (قِيس أن تمثيله ليس حتميًّا: `100% and 50%` يمرّ
وحده ويرسب مع نصٍّ آخر في الملفّ نفسه): تُشغّله. وهي تُغلَق في **ثوانٍ** لا في أربع دقائق،
وقبل تنزيل الأصليّ وRust.

والحدّ معلَن: بلا `aapt2` على الجهاز تُطبع «غير مُتحقَّقة في هذه البيئة» ولا يُدَّعى نجاح —
وهو نفس حدّ `--require-binaries` في `jni_symbols.py`.

الاستعمال
--------
```sh
python3 tools/resource_compile.py            # تقرير
python3 tools/resource_compile.py --assert   # exit 1 عند أي مورد يرفضه aapt2
python3 tools/resource_compile.py --json     # للقراءة الآلية
python3 tools/resource_compile.py --self-test  # يقيس الأداة على شجرة مصنوعة معلومة النتيجة
```
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# سطر خطأ aapt2 الذي يسمّي موضعًا: `<file>:<line>: error: <message>`
ERROR_LINE = re.compile(r"^(?P<file>[^:]+):(?P<line>\d+): error: (?P<message>.+)$")
FAILED_FILE = re.compile(r"^(?P<file>[^:]+): error: file failed to compile\.$")


def find_aapt2() -> str | None:
    """يبحث عن `aapt2` كما يبحث عنه البناء: SDK من البيئة، ثم أحدث build-tools، ثم PATH."""
    roots = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        os.environ.get("ANDROID_SDK"),
        os.path.expanduser("~/android-sdk"),
    ]
    candidates: list[str] = []
    for root in roots:
        if not root:
            continue
        candidates.extend(glob.glob(os.path.join(root, "build-tools", "*", "aapt2")))
        candidates.extend(glob.glob(os.path.join(root, "build-tools", "*", "aapt2.exe")))

    def version_of(path: str) -> tuple:
        parent = os.path.basename(os.path.dirname(path))
        return tuple(int(part) if part.isdigit() else 0 for part in re.split(r"[.\-]", parent))

    usable = [path for path in candidates if os.access(path, os.X_OK)]
    if usable:
        return max(usable, key=version_of)
    return shutil.which("aapt2")


def res_dirs(root: str) -> list[str]:
    """مجلّدات `res` الخمسة عشرية في كل موديول — نفس نطاق الحاجز الذي يُسقط البناء."""
    found = glob.glob(os.path.join(root, "manager", "*", "src", "*", "res"))
    return sorted(path for path in found if os.path.isdir(path))


def compile_dir(aapt2: str, directory: str) -> tuple[int, str]:
    with tempfile.TemporaryDirectory() as tmp:
        proc = subprocess.run(
            [aapt2, "compile", "--dir", directory, "-o", os.path.join(tmp, "out.zip")],
            capture_output=True,
            text=True,
        )
    return proc.returncode, proc.stderr


def offenders(aapt2: str, directory: str, root: str) -> list[str]:
    """ملفّات aapt2 الرافضة داخل مجلّد — بأسمائها وأسطرها، لا برسالة عامّة.

    وaapt2 يتوقّف عند **عشر أخطاء** في الاستدعاء الواحد؛ فيُعاد الاستدعاء على ما بقي؟ لا:
    الرسالة تحمل `file failed to compile` لكل ملفّ رافض، فيُستخرج الملفّ منه ويُطبع مساره،
    فيكفي أن يُعاد التشغيل بعد الإصلاح. والحدّ مُعلَن لا مخفيّ.
    """
    code, stderr = compile_dir(aapt2, directory)
    if code == 0:
        return []
    problems: list[str] = []
    for line in stderr.splitlines():
        match = ERROR_LINE.match(line.strip())
        if match:
            where = os.path.relpath(match.group("file"), root)
            problems.append(f"{where}:{match.group('line')}: {match.group('message')}")
            continue
        match = FAILED_FILE.match(line.strip())
        if match:
            where = os.path.relpath(match.group("file"), root)
            problems.append(f"{where}: الملفّ لم يُترجم")
    return problems


def self_test() -> int:
    """يقيس الأداة على شجرة مصنوعة: عيبان معلومان يُمسكان، وسليمان يمرّان."""
    aapt2 = find_aapt2()
    print("═══ الفحص الذاتي: بوابة الموارد على شجرة معلومة النتيجة ═══")
    if not aapt2:
        print("  ⚠️ لا `aapt2` في هذه البيئة — **غير مُتحقَّقة** (لا يُدَّعى نجاح)")
        return 0
    print(f"  aapt2: {aapt2}")

    with tempfile.TemporaryDirectory() as root:
        res = os.path.join(root, "manager/app/src/main/res/values")
        os.makedirs(res)
        # السليمان: فاصلة مُهرَّبة، وقيمة مُقتبَسة، ونصّ مُنسَّق موضعيًّا، و`%%` مُهرَّبة.
        with open(os.path.join(res, "good.xml"), "w", encoding="utf-8") as handle:
            handle.write(
                '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '  <string name="escaped">don\\\'t</string>\n'
                '  <string name="quoted">"quoted \'ok\'"</string>\n'
                '  <string name="positional">%1$d%% of %2$s</string>\n'
                '  <string name="prose" formatted="false">85% and 100%</string>\n'
                "</resources>\n"
            )
        cases: list[tuple[str, bool]] = []

        def run() -> list[str]:
            return offenders(aapt2, os.path.dirname(res), root)

        # ①شجرة سليمة لا تُنتج عطبًا — وإلا فالبوّابة تُسقط كل شيء.
        first = run()
        cases.append(("شجرة سليمة تمرّ", first == []))

        # ②و`\\'` (فاصلة عارية بعد شرطة خلفية) تُمسك **بالسطر**.
        with open(os.path.join(res, "apostrophe.xml"), "w", encoding="utf-8") as handle:
            handle.write(
                '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '  <string name="doubled">don\\\\\'t</string>\n'
                "</resources>\n"
            )
        caught = run()
        cases.append(
            (
                "`\\\\'` تُمسك باسم الملفّ",
                any("apostrophe.xml" in item for item in caught),
            )
        )
        os.remove(os.path.join(res, "apostrophe.xml"))

        # ③ووسائط غير موضعيّة تُمسك. **وحالة `%` العارية وحدها لا تُستعمل هنا عن قياس**:
        # `85% and 100%` **يمرّ** عند aapt2 حين يكون وحده في الملفّ، ويرسب مع نصّ آخر فيه
        # (قِيس الفرق: `100% and 50%` وحده ok · `100% and 50% ok` راسب). فحكم aapt2 ليس
        # تمثيلًا يمكن إعادة بنائه، ولهذا تُشغَّل الأداة نفسها ولا يُقلَّد حكمها.
        with open(os.path.join(res, "nonpos.xml"), "w", encoding="utf-8") as handle:
            handle.write(
                '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '  <string name="two_nonpos">%s and %s</string>\n'
                "</resources>\n"
            )
        caught = run()
        cases.append(("وسائط غير موضعيّة تُمسك", any("nonpos.xml" in item for item in caught)))

        failures = 0
        for title, ok in cases:
            print(f"  {'✓' if ok else '✗'} {title}")
            failures += 0 if ok else 1
        print(f"\nالنتيجة: {len(cases) - failures}/{len(cases)}")
        return 1 if failures else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="بوابة ترجمة موارد أندرويد بـaapt2")
    parser.add_argument("--assert", dest="assert_on_fail", action="store_true", help="exit 1 عند أي مورد رافض")
    parser.add_argument("--json", action="store_true", help="تقرير للقراءة الآلية")
    parser.add_argument("--self-test", action="store_true", help="يقيس الأداة نفسها")
    parser.add_argument("--root", default=REPO, help="جذر المستودع (للفحص الذاتي)")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    root = os.path.abspath(args.root)
    aapt2 = find_aapt2()
    if not aapt2:
        print("⚠️ لا `aapt2` في هذه البيئة ⇒ ترجمة الموارد **غير مُتحقَّقة** (لا يُقال «تمرّ»)")
        if args.json:
            print(json.dumps({"verified": False, "reason": "no aapt2", "dirs": 0, "offenders": []}, ensure_ascii=False))
        return 0

    dirs = res_dirs(root)
    problems: list[str] = []
    for directory in dirs:
        problems.extend(offenders(aapt2, directory, root))

    if args.json:
        print(
            json.dumps(
                {"verified": True, "aapt2": aapt2, "dirs": len(dirs), "offenders": problems},
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        print(f"aapt2: {aapt2}")
        print(f"مجلّدات res: {len(dirs)}")
        if problems:
            print(f"\n✗ موارد يرفضها aapt2: {len(problems)}")
            for item in problems[:20]:
                print(f"  · {item}")
            if len(problems) > 20:
                print(f"  · … و{len(problems) - 20} أخرى")
        else:
            print("\n✓ كل مجلّدات الموارد تُترجم")
    return 1 if (args.assert_on_fail and problems) else 0


if __name__ == "__main__":
    sys.exit(main())
