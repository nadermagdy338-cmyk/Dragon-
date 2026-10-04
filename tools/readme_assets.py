#!/usr/bin/env python3
"""حرس أصول README — الصور المتحركة والشيفرات التي تُدَّعى في المستودع.

**لماذا أداة لا عين:** ملفّات SVG في `docs/assets/` لا تُقرأ في هذه البيئة (لا مُصيَّر للصور)،
فالمراجعة بالعين غير ممكنة — والمراجعة التي لا تُقاس تُنتج ادّعاءً. وقد وُلدت هذه الأداة من عطب
حقيقي وقع في هذه الجولة: **`--` غير مشروع داخل تعليق XML**، فسقط ملفّان من التحليل ولم يكن في
التشغيل ما يكشفه. وهذه الأداة تكشفه في أجزاء من الثانية، مع سبع قواعد أخرى مأخوذة من سلوك GitHub
الفعلّي مع SVG في الـREADME (المرجع: مستودع svg-motion-cookbook — gotchas.md و github-readme.md).

**والقواعد التي تفرضها:**

  ① XML سليم — تعليق فيه `--` أو وسم غير مغلق يُسقط الملفّ من التحليل فلا يُصيَّر أصلًا.
  ② ما يُجرَّده GitHub: `<script>` · `<style>` · معالجات الأحداث (`onclick`…) · `<iframe>` ·
     `xlink:href` (يُجرَّد فيصبح `<animate>` معلّقًا بلا هدف) — كلّها تُفشل البوّابة.
  ③ `values` و`keyTimes` بعدد متساوٍ · `keyTimes` غير متناقص · يبدأ بـ0 · وينتهي بـ1 **إلا في
     `calcMode="discrete"`**: هناك القيمة الأخيرة تسري إلى نهاية الدورة وهذا نمط مقصود (تبديل نصّ).
  ④ `calcMode="spline"` يلزمه `keySplines` بأربعة أرقام لكل قطعة.
  ⑤ `<animate>` لا يُعلَّق على عنصر آخر: يجب أن يكون **ابنًا للعنصر الذي يُحرّكه** (الشكل البديل
     بـ`xlink:href` يُجرَّد على GitHub فيُهمل بصمت).
  ⑥ `opacity` على `<tspan>` غير موثوقة عبر المصيِّرات — الصواب `fill-opacity`.
  ⑦ `xml:space="preserve"` مع نصّ مُنسَّق بالتنصيف يُرسم إزاحةً حقيقيّة قبل النصّ — يُرفض.
  ⑧ الحجم: فوق 200KB تُسطَّح الصورة أحيانًا عند الوسيط فيفقدها الحركة، وفوق 5MB تُرفض.
  ⑨ كل أصل SVG يُشار إليه من `README.md` أو `README.ar.md` **موجود فعلًا** (وإلا فالصورة مكسورة)،
     ولقطات الشاشة المُعلَنة تُعدّ وتُعلن كمعلّقة لا تُفشل (المالك يضيفها).
  ⑩ `<title>` و`<desc>` لكل أصل: الصورة تعمل بنصّ بديل لا بشيء يُخمَّن.
  ⑪ **أرقام شرائح اللافتة = المقيس من الشجرة** (53 شاشة · 84 لغة · 1681 اختبارًا · 13 بوّابة):
     رقم يتقادم يُسقط التشغيل بدل أن يبقى يُصدَّق سنوات. وهو الفرق بين لافتة تُقاس ولافتة تُدَّعى.
  ⑫ **كل رابط في التوثيق العام يُحلّ**: ملفًّا كان أو نقطة تثبيت (`#عنوان` أو `<a id>`) — والصور بسطر
     Markdown أو `<img src>`. وتشمل **النقط الداخلية** (رابط `#قسم` في الصفحة نفسها)، لأن رابطه
     لا مُصرّف يراه ولا اختبار بنيويّ يمسكه. تُفحص الصفحات العامة وحدها (نصّ `PUBLIC_DOCS` أدناه):
     التوثيق المنشور للناس. ويُفحص `docs/ai/**` **بالطلب** بـ`--all` ولا يُفشل الافتراضيّ — فهو سجلّ داخليّ
     لا صفحة. وتُجرَّد كتل الشيفرة المُسيَّجة قبل الفحص: مثالٌ فيه رابط ليس رابطًا.

  ⑬ **عقد رمز الـREADME** (`docs/assets/ic-*.svg`): الرسم كله داخل **ورقة أمان [4, 20]** من شبكة 24
     (يُقاس من `d` مباشرةً، والأوامر النسبيّة إزاحة لا موضع)، **وبلا `<text>`** (رمز يعتمد على خطوط
     العارض لا يُقاس هنا فيُمنع بدل أن يُدَّعى)، **وبلا قوس `A`**، ومع **بلاطة 24×24**، و**لا رمز
     يتيم** (يُشار إليه من `README.md` **و**`README.ar.md`). ووُجدت هذه القاعدة بعد أن أضيف ٣٠ رمزًا:
     البوّابتان تقيسان النصّ والبلاطة والدائرة، و**لا تقيسان `path`** — فمسار يخرج من اللوحة كان يمرّ
     صامًتا، وهي "الأيقونة المشوّهة" بعينها. **وأول تشغيل لها كشف عطبين: عطبًا في نفسه** (خلط الإزاحة
     النسبيّة بالإحداثيّ المطلق فبلّغ ١٤ بلاغًا كاذبًا) **وعطبًا حقيقيًّا في الرسم** (طرفا المكعّب عند
     4.4/20.2 خارج الورقة).

  `--self-test` يبني شجرة مصغّرة معلومة النتيجة ويطالب باسم كل قاعدة عند كسرها — أداة لا تُكسر لا تقيس.

الاستعمال:  python3 tools/readme_assets.py --assert  ·  --self-test  ·  --list
"""

from __future__ import annotations

import argparse
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

# ── ⑬ رماز الـREADME: عقدها المقيس ─────────────────────────────────────────
# الرمز ملفّ `ic-*.svg` على شبكة 24×24، وله **ورقة أمان**: الرسم كله داخل [4, 20].
# ولماذا يوجد قياس هنا أصلًا: `svg_review` يقيس النصّ والبلاطات والدوائر، و**لا يقيس `path`**
# (حدّ مُعلَن هناك). ومعظم الرماز مسارات — فمسحُ مسار خرج من اللوحة يمرّ صامًتا في البوّابتين،
# وهذا بالضبط ما ينتج "أيقونة مشوّهة" بلا خطأ. والأرقام تُقرأ من `d` مباشرةً فلا تُصدّق الملفّ.
ICON_MIN, ICON_MAX = 4.0, 20.0

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "docs", "assets")
SHOTS = os.path.join(ROOT, "docs", "screenshots")
READMES = ("README.md", "README.ar.md")

# ⑫ الصفحات العامة: ما يُقرأ للناس. و`docs/ai/**` سجلّ داخليّ يُفحص بـ`--all` وحده.
# و`DESIGN.md` من الجذر داخل الحرس منذ أُضيف: وثيقة تصميم يقرأها وكيل ويبني عليها، وروابطها
# كانت **خارج** أيّ فحص لأنّها في الجذر لا في `docs/` — أي أنّ وثيقة عامّة بأربعة روابط لم يكن
# أحد يُحلّها. والقاعدة ⑫ تكشف الآن رابطًا ميتًا فيها كما تكشفه في الصفحة.
PUBLIC_DOCS = ("README.md", "README.ar.md", "DESIGN.md", "docs/*.md", "docs/screenshots/*.md")
ALL_DOCS = PUBLIC_DOCS + ("docs/ai/*.md", "docs/aegis/*.md")

LINK_RE = re.compile(r"\[[^\]]*\]\(([^)\s]+?)(?:\s+\"[^\"]*\")?\)")
MD_IMG_RE = re.compile(r"!\[[^\]]*\]\(([^)\s]+?)(?:\s+\"[^\"]*\")?\)")
HTML_IMG_RE = re.compile(r"<img[^>]*?\ssrc=\"([^\"]+)\"")
FENCE_RE = re.compile(r"^```.*?^```", re.S | re.M)
HEAD_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$", re.M)
RE_ID = re.compile(r"(?:\sid|\sname)=\"([^\"]+)\"")
SKIP_SCHEME = ("http://", "https://", "mailto:", "tel:", "data:")

SIZE_WARN = 200 * 1024
SIZE_FAIL = 5 * 1024 * 1024

BANNED = (
    ("<script", "وسم script — GitHub يحذفه"),
    ("<style", "وسم style — GitHub يحذفه"),
    ("@keyframes", "حركة CSS إطاريّة — GitHub يحذفها"),
    ("xlink:href", "xlink:href يُجرَّد فيصبح <animate> بلا هدف"),
    ("<iframe", "iframe يُجرَّد من الـREADME"),
    ("javascript:", "رابط javascript:"),
    ("data:image", "صورة بdata-URL — يُعاد ترميزها وقد تفقد الحركة"),
)
EVENT_ATTR = re.compile(r"\son[a-z]+\s*=")


def _comments(text: str) -> list[tuple[int, str]]:
    out = []
    for m in re.finditer(r"<!--(.*?)-->", text, re.S):
        line = text[: m.start()].count("\n") + 1
        out.append((line, m.group(1)))
    return out


def check_svg(name: str, text: str) -> list[str]:
    """يفحص نصّ SVG ويعيد قائمة المشاكل. دالّة خالصة كي يقيسها `--self-test` بلا ملفّات."""
    problems: list[str] = []

    # ① تعليق فيه `--` — عطب XML حقيقي أوقف ملفّين في هذه الجولة
    for line, body in _comments(text):
        if "--" in body:
            problems.append(
                f"① تعليق في السطر {line} يحوي '--' — غير مشروع في تعليق XML فيسقط الملفّ من التحليل"
            )

    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        problems.append(f"① XML غير سليم: {exc}")
        root = None

    # ② و⑦ على **ما عدا التعليقات**: التعليق يُحذف قبل العرض، وذكر وسمٍ فيه للتوثيق لا يُجرَّد
    # منه شيء — فقيس ما يُعرض فعلًا لا ما يُشرح. (وهذا فرق مقصود: القاعدة ① تبقى على التعليقات.)
    visible = re.sub(r"<!--.*?-->", "", text, flags=re.S)

    # ② ما يُجرّده GitHub
    for token, why in BANNED:
        if token in visible:
            problems.append(f"② موجود '{token}': {why}")
    if EVENT_ATTR.search(visible):
        problems.append("② معالج حدث (on* =) — يُجرَّد")

    # ⑦ xml:space مع نصّ مُنسَّق
    if 'xml:space="preserve"' in visible:
        problems.append(
            "⑦ xml:space=\"preserve\" يُبقي تنصيف المصدر مسافاتٍ مرسومة تُزيح النصّ — احذفه "
            "(الافتراضيّ يطوي الفراغات ويتجاهل البادئة، وهو المطلوب لملفّ مُنسَّق)"
        )

    if root is None:
        return problems

    ns = "{http://www.w3.org/2000/svg}"
    tags = [el.tag.replace(ns, "") for el in root.iter()]

    # ⑩ العنوان والوصف
    for need in ("title", "desc"):
        if need not in tags:
            problems.append(f"⑩ لا يوجد <{need}> — الصورةُ بلا نصّ بديل يُقرأ ولا يُخمَّن")

    # ⑤ و③ و④ لكل عنصر تحريك
    for el in root.iter():
        tag = el.tag.replace(ns, "")
        if tag not in ("animate", "animateTransform", "set"):
            continue
        attr = el.get("attributeName") or el.get("type") or "?"
        values = el.get("values")
        keytimes = el.get("keyTimes")
        mode = el.get("calcMode")

        if el.get("href") or el.get("xlink:href"):
            problems.append(f"⑤ <{tag} {attr}> معلَّق على عنصر آخر — يجب أن يكون ابنًا لهدفه")

        if values and keytimes:
            vs, ks = values.split(";"), keytimes.split(";")
            if len(vs) != len(ks):
                problems.append(
                    f"③ <{tag} {attr}>: values ({len(vs)}) وkeyTimes ({len(ks)}) بعدد مختلف"
                )
            try:
                k = [float(x) for x in ks]
            except ValueError:
                problems.append(f"③ <{tag} {attr}>: keyTimes غير رقمي: {keytimes}")
                k = []
            if k:
                if any(b < a for a, b in zip(k, k[1:])):
                    problems.append(f"③ <{tag} {attr}>: keyTimes متناقص: {keytimes}")
                if k[0] != 0.0:
                    problems.append(f"③ <{tag} {attr}>: keyTimes لا يبدأ بـ0: {keytimes}")
                if k[-1] != 1.0 and mode != "discrete":
                    problems.append(
                        f"③ <{tag} {attr}>: keyTimes ينتهي بـ{k[-1]} لا 1 (ومسموح فقط في "
                        f"calcMode=discrete حيث تسري القيمة الأخيرة إلى نهاية الدورة)"
                    )
        elif values and not keytimes and mode == "discrete":
            if len(values.split(";")) < 2:
                problems.append(f"③ <{tag} {attr}>: discrete بقيمة واحدة — لا تبديل")

        if mode == "spline":
            splines = el.get("keySplines")
            if not splines:
                problems.append(f"④ <{tag} {attr}>: calcMode=spline بلا keySplines")
            else:
                segs = len(splines.split(";"))
                bad = [s for s in splines.split(";") if len(s.split()) != 4]
                if bad:
                    problems.append(f"④ <{tag} {attr}>: keySplines ليست أربعة أرقام: {bad[0]}")
                if keytimes and segs != len(keytimes.split(";")) - 1:
                    problems.append(
                        f"④ <{tag} {attr}>: keySplines ({segs}) ≠ قطع keyTimes "
                        f"({len(keytimes.split(';')) - 1})"
                    )

    # ⑥ opacity على tspan
    for el in root.iter(f"{ns}tspan"):
        if el.get("opacity") is not None:
            problems.append("⑥ opacity على tspan غير موثوقة — استعمل fill-opacity")

    # ⑤ التحريك في جذر المستند (لا أب له) يُهمل بصمت
    for child in list(root):
        if child.tag.replace(ns, "") in ("animate", "animateTransform", "set"):
            problems.append("⑤ <animate> في جذر المستند بلا هدف — يُهمل بصمت")

    # ⑧ الحجم
    n = len(text.encode("utf-8"))
    if n > SIZE_FAIL:
        problems.append(f"⑧ الحجم {n}B فوق حدّ raw.githubusercontent (5MB)")
    elif n > SIZE_WARN:
        problems.append(f"⑧ الحجم {n}B فوق ~200KB — الوسيط قد يُسطّحها إلى PNG فتفقد الحركة")
    return problems


# ⑪ — الأرقام التي **تدّعيها** الصفحتان تُقاس على الشجرة، لا تُصدَّق.
#
# ⚠️ تاريخ القاعدة (I-102): كانت النمط `>(\d+) (screens|locales|tests|gates)<` أي **شريحة
# لافتة في SVG**، واللافتتان `banner-dark.svg`/`banner-light.svg` **لم تعودا تحملان رقمًا أصلًا**
# ⇒ كانت تُخرج **صفر بلاغ** في كل تشغيل، بينما الصفحتان تقولان «84 locales» و«53 screens»
# في **عشرة مواضع** بلا أيّ حارس. فالنمط صار يقرأ **النصّ** لا الوسم، فيعمل على الصفحة كما
# يعمل على الشريحة؛ ويقرأ العربية كما الإنجليزية — لأنّ الصفحتين تدّعيان الشيء نفسه بلغتين،
# والأرقام فيهما **هنديّة** (`٨٤ لغة`) فلا يكفي نمط إنجليزيّ واحد.
DIGITS = str.maketrans("٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹", "01234567890123456789")

# والكلمة تحدّد **أيّ رقم** نتكلّم عنه، والجمع يُقدَّم على المفرد في الترتيب (وإلا أُمسك
# `شاشة` من `شاشات` فبقي صفّ في الصفحة بلا قياس).
CLAIM_WORDS = {
    "screens": "screens", "screen": "screens", "شاشات": "screens", "شاشة": "screens",
    "locales": "locales", "locale": "locales", "لغات": "locales", "لغة": "locales",
    "tests": "tests", "test": "tests", "اختبارات": "tests", "اختبار": "tests",
    "gates": "gates", "gate": "gates",
    "بوّابات": "gates", "بوّابة": "gates", "بوابات": "gates", "بوابة": "gates",
}

# والحدود تمنع الإيجاب الزائف: `(?<![\w.])` يُقصي `v5.2` و`1.84`، و`(?!\w)` يُقصي `شاشاتنا`.
NUMBER_CLAIM = re.compile(
    r"(?<![\w.])(?P<num>[٠-٩0-9]{1,6})\s*(?P<kind>"
    + "|".join(sorted(CLAIM_WORDS, key=len, reverse=True))
    + r")(?!\w)"
)


def check_icon(rel: str, text: str) -> list[str]:
    """⑬ عقد رمز الـREADME — يقيس ما لا يُقاس في `svg_review` (`path` خارجيًّا).

    (أ) **الرسم داخل ورقة الأمان [4, 20]**: تُفكّك أرقام كل `d` / بلاطة / دائرة / قطع ناقص وتُقاس.
        وأقواس `A` ممنوعة في رمز — فلا تُقاس بحساب بديهيّ، ولأنّها تُصعّب المراجعة بلا مُصيِّر.
    (ب) **بلا نصّ**: رمز فيه `<text>` يعتمد على خطوط العارض فيختلف بين GitHub وغيره — وهو ما
        لا يمكن قياسه هنا، فيُمنع بدل أن يُدَّعى.
    (ج) **بلاطة واحدة كاملة 24×24** — تُستثنى من قصّ `svg_review` لأنها ≥ 90% من اللوحة، وهذا شرطها.
    """
    problems: list[str] = []
    body = re.sub(r"<!--.*?-->", "", text, flags=re.S)

    if "<text" in body:
        problems.append("⑬(~) رمز فيه <text> — يُرسم بخطوط العارض ويختلف بينها، والرمز هندسة فقط")
    if re.search(r'\sd="[^"]*[Aa][\s\d,.]', body):
        problems.append("⑬(~) في `d` قوس `A` — يُدخل قطعًا ناقصًا لا تقيسه هذه البوّابة")

    rects = [dict(re.findall(r'([a-zA-Z:-]+)="([^"]*)"', m.group(1)))
             for m in re.finditer(r"<rect\b([^>]*?)/?>", body)]
    if not any(r.get("width") == "24" and r.get("height") == "24" for r in rects):
        problems.append("⑬(~) لا بلاطة 24×24 في الجذر — الرمز يبدو عرفًا لا ملفًّا")

    cmd: str | None = None
    nums: list[float] = []
    for m in re.finditer(r'\sd="([^"]+)"', body):
        for tok in re.finditer(r"[MmLlHhVvCcSsQqTtAaZz]|"
                               r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?", m.group(1)):
            s = tok.group(0)
            if s.isalpha():
                cmd = s
            elif cmd in "MLHVCQST":
                # الأوامر الكبيرة مواضع، والصغيرة إزاحات (`h3.2` ليست إحداثيًّا 3.2) —
                # وخلطها كان أول عطب في هذه القاعدة نفسها: ١٤ رمزًا بلاغًا كاذبًا في أول تشغيل.
                nums.append(float(s))
    for a in rects:
        if a.get("width") == "24" and a.get("height") == "24":
            continue  # البلاطة نفسها
        x, y = float(a.get("x", 0)), float(a.get("y", 0))
        w, h = float(a["width"]), float(a["height"])
        nums += [x, y, x + w, y + h]
    for m in re.finditer(r"<circle\b([^>]*?)/?>", body):
        a = dict(re.findall(r'([a-zA-Z:-]+)="([^"]*)"', m.group(1)))
        cx, cy, r = float(a["cx"]), float(a["cy"]), float(a["r"])
        nums += [cx - r, cy - r, cx + r, cy + r]
    for m in re.finditer(r"<ellipse\b([^>]*?)/?>", body):
        a = dict(re.findall(r'([a-zA-Z:-]+)="([^"]*)"', m.group(1)))
        cx, cy = float(a["cx"]), float(a["cy"])
        rx, ry = float(a["rx"]), float(a["ry"])
        nums += [cx - rx, cy - ry, cx + rx, cy + ry]

    out = [v for v in nums if v < ICON_MIN - 1e-9 or v > ICON_MAX + 1e-9]
    if out:
        problems.append(
            f"⑬(أ) الرسم يخرج من ورقة الأمان [{ICON_MIN:g}, {ICON_MAX:g}] من 24: "
            f"{len(out)} إحداثيًّا، أوّلها {out[0]:g} — إمّا يُصلح الرسم أو يُضيّق الرمز"
        )
    return problems


def measure_tree() -> dict[str, int]:
    """الأرقام الأربعة التي تدّعيها شرائح اللافتة — تُقاس من الشجرة لا تُقرأ من الملفّ."""
    nav = os.path.join(ROOT, "manager", "app", "src", "main", "java", "nd", "max",
                       "ui", "navigation", "MaxDestinations.kt")
    screens = len(re.findall(r"data object \w+ : MaxDestination",
                             open(nav, encoding="utf-8").read()))
    locales = len(glob.glob(os.path.join(ROOT, "manager", "app", "src", "main", "res",
                                         "values-*")))
    tests = 0
    for dp, _, fn in os.walk(os.path.join(ROOT, "manager", "app", "src")):
        for name in fn:
            if name.endswith(".kt") and os.sep + "test" in dp + os.sep:
                tests += len(re.findall(r"@Test", open(os.path.join(dp, name), encoding="utf-8",
                                                        errors="replace").read()))
    wf = open(os.path.join(ROOT, ".github", "workflows", "build.yml"), encoding="utf-8").read()
    gates = len(re.findall(r"^\s+python3 tools/.*--assert$", wf, re.M))
    return {"screens": screens, "locales": locales, "tests": tests, "gates": gates}


def check_number_claims(text: str, measured: dict[str, int], where: str) -> list[str]:
    """⑪ كل رقم تدّعيه الصفحة أو الشريحة = المقيس من الشجرة.

    والفحص **لا يشترط وجود رقم**: غياب الادّعاء ليس عيبًا، والعيب أن يُعلَن رقمٌ
    ثم يتغيّر الواقع تحته فيبقى يُقرأ ويُصدَّق.
    """
    problems = []
    for m in NUMBER_CLAIM.finditer(_visible(text)):
        kind = CLAIM_WORDS[m.group("kind")]
        shown = int(m.group("num").translate(DIGITS))
        if kind in measured and measured[kind] != shown:
            problems.append(
                f"⑪ {where} تدّعي '{m.group(0).strip()}' والمقيس {measured[kind]} {kind} — "
                f"الرقم متقادم، فيُحدَّث أو يُعلن تاريخه"
            )
    return problems


def _visible(text: str) -> str:
    """يزيل كتل الشيفرة المُسيَّجة: ما في مثالٍ ليس رابطًا."""
    return FENCE_RE.sub("\n", text)


def slug(heading: str) -> str:
    """نقطة التثبيت كما يشتقّها GitHub من العنوان (يُبقي الحروف العربيّة وهي حرف `\\w`).

    **والفرق مقصود ومقيس:** GitHub يحذف الترقيم **ثم** يستبدل **كل** فراغ بشرطة واحدة، فلا يطوي
    الفراغين حول شرطة طويلة إلى شرطة واحدة: `## Top — Two` تصير `top--two` بشرطتين. والطيّ هنا
    (`\\s+`) كان يُنتج `top-two` — أي أن الأداة كانت **تُسقط رابطًا سليمًا** قبل هذا التصحيح.
    """
    s = re.sub(r"`([^`]*?)`", r"\1", heading.strip().lower()).replace("&amp;", "&")
    s = re.sub(r"[^\w\s-]", "", s, flags=re.U)
    return re.sub(r"\s", "-", s)


def anchors(path: str) -> set[str]:
    """نقط التثبيت التي يقبلها GitHub: عناوين صحيحة-slug **وأوسام `<a id>`/`name=`** الصريحة.

    والثانية ضروريّة هنا لا إضافةً: العناوين العربية لا تُشتقّ من لغتها اشتقاقًا موثوقًا، فالـREADME
    العربيّ يُعلن أهدافه بـ`<a id="why">` صراحةً — ولولا قراءتها لسقطت سبعة روابط سليمة."""
    text = _visible(open(path, encoding="utf-8").read())
    out = {slug(m.group(2)) for m in HEAD_RE.finditer(text)}
    out |= {m.group(1).strip().lower() for m in RE_ID.finditer(text)}
    return out


def doc_pages(all_pages: bool = False) -> list[str]:
    out: list[str] = []
    for pattern in (ALL_DOCS if all_pages else PUBLIC_DOCS):
        for path in sorted(glob.glob(os.path.join(ROOT, pattern))):
            out.append(os.path.relpath(path, ROOT).replace(os.sep, "/"))
    return sorted(set(out))


def check_pages(pages: list[str]) -> list[str]:
    """⑫ كل رابط نسبيّ في صفحة يُحلّ: الملفّ موجود، ونقطة التثبيت لها عنوان يطابقها."""
    problems: list[str] = []
    for rel in pages:
        full = os.path.join(ROOT, rel)
        if not os.path.exists(full):
            continue
        text = _visible(open(full, encoding="utf-8").read())
        base = os.path.dirname(full)
        targets = (LINK_RE.findall(MD_IMG_RE.sub("", text))
                   + MD_IMG_RE.findall(text)
                   + HTML_IMG_RE.findall(text))
        for raw in targets:
            if raw.startswith(SKIP_SCHEME) or "<" in raw or "{" in raw:
                continue
            path_part, _, frag = raw.partition("#")
            # وسلسلة الاستعلام تُجرَّد قبل المقابلة: `?v=2` ليست جزءًا من اسم الملفّ، وهي **الوسيلة
            # الوحيدة** لكسر كاش وسيط صور GitHub — ونصّها في `github-readme.md` من كتاب وصفات
            # الـSVG المتحرّكة: «GitHub caches images aggressively via its image proxy. After a
            # push, the live SVG may take a few minutes to refresh... Cache-bust by appending a
            # query string». وبلا هذا التجريد تصير كل صورة مُصدَّرة بـ`?v=2` «ملفًّا مفقودًا» — أي
            # أنّ العلاج نفسه كان يُسقط البوّابة، ولذلك حالة الفحص الذاتي أدناه تقيسه.
            path_part = path_part.partition("?")[0]
            if not path_part:
                # نقطة داخليّة: رابط `#قسم` في الصفحة نفسها — تُقاس كالخارجيّة تمامًا
                if frag and frag.lower() not in anchors(full):
                    problems.append(f"⑫ {rel}: '{raw}' يشير إلى قسم داخليّ غير موجود")
                continue
            target = os.path.normpath(os.path.join(base, path_part))
            if not os.path.exists(target):
                # اللقطات المُعلَنة تُعدّها القاعدة ⑨ وتُعلن **معلَّقة** (المالك يضيفها) — فلا تُفشل هنا
                rel_target = os.path.relpath(target, ROOT).replace(os.sep, "/")
                if rel_target.startswith("docs/screenshots/"):
                    continue
                problems.append(f"⑫ {rel} يشير إلى '{raw}' ولا وجود له")
                continue
            if frag and target.endswith(".md") and frag.lower() not in anchors(target):
                problems.append(f"⑫ {rel} يشير إلى '{raw}' ولا عنوان يطابقه في الملفّ الهدف")
    return problems


def referenced_assets() -> dict[str, list[str]]:
    """كل مسار `docs/assets/...` أو `docs/screenshots/...` يُشار إليه من ملفّات README."""
    found: dict[str, list[str]] = {}
    for readme in READMES:
        path = os.path.join(ROOT, readme)
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r"(docs/(?:assets|screenshots)/[A-Za-z0-9._/-]+)", text):
            found.setdefault(m.group(1), []).append(readme)
    return found


def run(warn_only: bool, all_pages: bool = False) -> int:
    problems: list[str] = []
    files = sorted(glob.glob(os.path.join(ASSETS, "*.svg")))
    if not files:
        problems.append("لا يوجد أيّ أصل في docs/assets/ — والـREADME يُشير إلى أصول مفقودة")

    measured = measure_tree()
    for path in files:
        rel = os.path.relpath(path, ROOT)
        text = open(path, encoding="utf-8").read()
        for p in check_svg(rel, text):
            problems.append(f"{rel}: {p}")
        if os.path.basename(rel).startswith("ic-"):
            for p in check_icon(rel, text):
                problems.append(f"{rel}: {p}")
        if rel.endswith("banner-dark.svg") or rel.endswith("banner-light.svg"):
            for p in check_number_claims(text, measured, rel):
                problems.append(p)

    # ⑪ والادّعاء يقرأه الناس في **الصفحتين** لا في رسم خالٍ من رقم — فمكان الفحص هنا
    claimed = 0
    for readme in READMES:
        path = os.path.join(ROOT, readme)
        if not os.path.exists(path):
            continue
        page = open(path, encoding="utf-8").read()
        claimed += len(NUMBER_CLAIM.findall(_visible(page)))
        for p in check_number_claims(page, measured, readme):
            problems.append(p)

    print("مقيس الآن: " + " · ".join(f"{v} {k}" for k, v in measured.items()))
    print(f"ادّعاءات رقمية في الصفحتين: {claimed} موضعًا")

    # ⑨ المراجع
    refs = referenced_assets()
    pending_shots = []
    for rel, where in sorted(refs.items()):
        full = os.path.join(ROOT, rel)
        if os.path.exists(full):
            continue
        if rel.startswith("docs/screenshots/"):
            pending_shots.append((rel, where))
        else:
            problems.append(f"⑨ {rel} مُشار إليه من {', '.join(where)} ولا وجود له")

    # ⑬(ج) لا رمز يتيم: الصفحتان مرآتان، فرمز في إحداهما وحدها انفصالٌ بصريّ لا يراه أحد
    icons = [os.path.relpath(p, ROOT).replace(os.sep, "/") for p in files
             if os.path.basename(p).startswith("ic-")]
    orphans = 0
    for rel in icons:
        missing = [r for r in READMES if r not in refs.get(rel, [])]
        if missing:
            orphans += 1
            problems.append(f"⑬(ج) {rel} غير مُشار إليه من {' و'.join(missing)} — والصفحتان مرآتان")
    print(f"رماز الـREADME: {len(icons)} ملفًّا · يتيمة: {orphans}")

    # ⑫ المراجع الداخليّة في التوثيق المنشور
    pages = doc_pages(all_pages)
    problems.extend(check_pages(pages))
    print(f"صفحات مفحوصة (⑫): {len(pages)}" + ("  [--all]" if all_pages else ""))

    print(f"أصول: {len(files)} ملفًّا · مراجع في الـREADME: {len(refs)}")
    for rel in sorted(refs):
        if os.path.exists(os.path.join(ROOT, rel)):
            size = os.path.getsize(os.path.join(ROOT, rel))
            print(f"  ✓ {rel} ({size}B)")
    if pending_shots:
        print(f"  • لقطات مُعلَنة ولم تُضف بعد: {len(pending_shots)} "
              f"(تُعلن معلَّقة ولا تُفشل — يضيفها المالك)")
        names = sorted({n for n, _ in pending_shots})
        print(f"    أوّلها: {names[0]} … آخرها: {names[-1]}")

    if problems:
        print("\nعوائق:")
        for p in problems:
            print("  ✗", p)
        print(f"\nالحصيلة: {len(problems)} عائقًا")
        return 0 if warn_only else 1
    print("\nالحصيلة: أصول سليمة (XML · SMIL · حجم · مراجع)")
    return 0


# ── الفحص الذاتي: كل قاعدة تُكسر مرة، والأداة تُطالب باسمها ─────────────────────
GOOD = (
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">'
    "<title>t</title><desc>d</desc>"
    '<rect width="1" height="1">'
    '<animate attributeName="width" values="0;1;1;0" keyTimes="0;0.2;0.8;1" dur="1s" '
    'repeatCount="indefinite"/>'
    "</rect>"
    '<text><tspan fill-opacity="1">x</tspan></text>'
    "</svg>"
)
BAD = {
    "①": '<svg xmlns="http://www.w3.org/2000/svg"><title>t</title><desc>d</desc><!-- a -- b --></svg>',
    "②": '<svg xmlns="http://www.w3.org/2000/svg"><title>t</title><desc>d</desc><script/></svg>',
    "③": '<svg xmlns="http://www.w3.org/2000/svg"><title>t</title><desc>d</desc><rect width="1">'
         '<animate attributeName="width" values="0;1" keyTimes="0;0.3;1" dur="1s"/></rect></svg>',
    "④": '<svg xmlns="http://www.w3.org/2000/svg"><title>t</title><desc>d</desc><rect width="1">'
         '<animate attributeName="width" values="0;1" keyTimes="0;1" dur="1s" calcMode="spline"/>'
         "</rect></svg>",
    "⑤": '<svg xmlns="http://www.w3.org/2000/svg"><title>t</title><desc>d</desc><rect width="1"/>'
         '<animate attributeName="width" values="0;1" keyTimes="0;1" dur="1s"/></svg>',
    "⑥": "<svg xmlns=\"http://www.w3.org/2000/svg\"><title>t</title><desc>d</desc>"
          '<text><tspan opacity="0">x</tspan></text></svg>',
    "⑦": '<svg xmlns="http://www.w3.org/2000/svg" xml:space="preserve"><title>t</title>'
         "<desc>d</desc></svg>",
    "⑩": '<svg xmlns="http://www.w3.org/2000/svg"><rect width="1" height="1"/></svg>',
}


def self_test() -> int:
    global ROOT
    cases = 0
    failures: list[str] = []

    if check_svg("good", GOOD):
        failures.append("الأصل السليم رُفض: " + "; ".join(check_svg("good", GOOD)))
    cases += 1

    for rule, body in BAD.items():
        got = check_svg("bad", body)
        cases += 1
        if not any(p.startswith(rule) for p in got):
            failures.append(f"كسر القاعدة {rule} لم يُمسَك (البلاغ: {got or 'لا شيء'})")
    # الثمن: ملفّ فوق السقف
    cases += 1
    big = GOOD[:-6] + "<path d='" + "M0 0" * 60000 + "'/></svg>"
    if not any("⑧" in p for p in check_svg("big", big)):
        failures.append("⑧ ملفّ ضخم لم يُمسَك")

    # ⑪ الادّعاء الرقمي: يُكسر **في اللافتة** كما يُكسر **في نصّ الصفحة**، ويُقرأ بالعربية
    # كما بالإنجليزية — وهذا هو العطب نفسه (I-102): القاعدة كانت تعمل على SVG فحسب،
    # فمرّت عشرة ادّعاءات في الصفحتين بلا قياس.
    truth = {"screens": 53, "locales": 84, "tests": 1694, "gates": 16}
    cases += 8
    if not any("⑪" in p for p in check_number_claims('<text>>12 gates</text>', truth, "banner")):
        failures.append("⑪ رقم متقادم في اللافتة (12 مقابل 16) لم يُمسَك")
    if check_number_claims('<text>>16 gates<</text><text>>53 screens</text>', truth, "banner"):
        failures.append("⑪ رقم مطابق في اللافتة رُفض")
    if check_number_claims("| **Languages** | 84 locales plus English |", truth, "README.md"):
        failures.append("⑪ ادّعاء إنجليزيّ مطابق رُفض")
    if not any("⑪" in p for p in check_number_claims("| 85 locales |", truth, "README.md")):
        failures.append("⑪ ادّعاء إنجليزيّ متقادم (85 مقابل 84) لم يُمسَك")
    if check_number_claims("- **٨٤ لغة** زائد الإنجليزية", truth, "README.ar.md"):
        failures.append("⑪ ادّعاء عربيّ مطابق رُفض")
    if not any("⑪" in p for p in check_number_claims("| **الشاشات** | ٥٤ شاشة |", truth, "README.ar.md")):
        failures.append("⑪ ادّعاء عربيّ متقادم (٥٤ مقابل 53) لم يُمسَك")
    # وإيجابان زائفان يمنعهما الحدّ: مثال داخل كتلة شيفرة (لا يُقرأ) ورقم إصدار (ليس ادّعاء عدد)
    if check_number_claims("```\n85 locales\n```", truth, "README.md"):
        failures.append("⑪ مثال داخل كتلة شيفرة حُسب ادّعاءً — إيجاب زائف")
    if check_number_claims("MaxManager v5.2 · 1.84 coverage", truth, "README.md"):
        failures.append("⑪ رقم إصدار/كسر حُسب ادّعاءً — إيجاب زائف")

    # ⑬ رمز سليم يمرّ، وأربعة أعطاب فيه تُمسك: رسم خارج الورقة · نصّ · بلا بلاطة 24×24 ·
    # وإزاحة نسبيّة (`h-3`) **لا تُحسب** إحداثيًّا (وهو العطب الذي وقع في هذه القاعدة أوّل مرة)
    ok_icon = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><title>t</title>'
               f'<desc>d</desc><rect x="0" y="0" width="24" height="24" rx="6"/>'
               f'<path d="M6 18 12 6 18 18z" fill="#5FD9AC"/></svg>')
    cases += 1
    if check_icon("ic-ok.svg", ok_icon):
        failures.append("⑬ رمز سليم رُفض: " + "; ".join(check_icon("ic-ok.svg", ok_icon)))
    for rule, body in [
        ("⑬(أ)", ok_icon.replace('M6 18 12 6 18 18z', 'M1 18 12 6 18 23z')),
        ("⑬(~)", ok_icon.replace("<path ", "<text x=\"6\" y=\"6\">x</text>\n<path ")),
        ("⑬(~)", ok_icon.replace('<rect x="0" y="0" width="24" height="24" rx="6"/>', '')),
    ]:
        cases += 1
        if not any(p.startswith(rule) for p in check_icon("ic-bad.svg", body)):
            failures.append(f"كسر قاعدة الرمز {rule} لم يُمسَك ({body[:40]}…)")

    cases += 1
    rel_icon = ok_icon.replace('M6 18 12 6 18 18z', 'M6 18 12 6 18 18h-3l-2 2z')
    if any(p.startswith("⑬(أ)") for p in check_icon("ic-rel.svg", rel_icon)):
        failures.append("⑬(أ) إزاحة نسبيّة (`h-3`) حُسبت إحداثيًّا — بلاغ كاذب")

    # ⑫ شجرة مصغّرة: رابط موجود بنقطة صحيحة (يمرّ)، ورابط مفقود، ونقطة تثبيت لا عنوان لها
    import shutil
    import tempfile
    saved, tmp = ROOT, tempfile.mkdtemp()
    try:
        os.makedirs(os.path.join(tmp, "docs"))
        with open(os.path.join(tmp, "README.md"), "w", encoding="utf-8") as fh:
            fh.write("[ok](docs/page.md#hello-there) [gone](docs/nope.md) "
                     "[anchor](docs/page.md#absent) ![img](docs/nope.png)\n"
                     "[self](#top--two) [bad](#nowhere) [md](#explicit-md)\n"
                     "[query](docs/page.md?v=2)\n"
                     '<a id="explicit-md"></a>\n## Top — Two\n'
                     "```\n[ignored](never.md)\n```\n")
        with open(os.path.join(tmp, "docs", "page.md"), "w", encoding="utf-8") as fh:
            fh.write("## Hello there\n")
        ROOT = tmp
        got = check_pages(["README.md"])
        joined = " | ".join(got)
        # المطلوب **أربعة** عوائق بالضبط: ملفّ مفقود · نقطة موجّهة لملفّ لا عنوان لها · صورة مفقودة ·
        # نقطة **داخليّة** مكسورة. والثلاثة السليمة تمرّ: `#hello-there` · `#top--two` (بشرطتين،
        # كما يشتقّها GitHub من `—`) · `<a id>` الصريح. والمثال داخل كتلة شيفرة لا يُقرأ رابطًا.
        want = ("docs/nope.md", "#absent", "nope.png", "#nowhere")
        cases += 1
        missing = [w for w in want if w not in joined]
        if len(got) != len(want) or missing:
            failures.append(
                f"⑫ المتوقّع {len(want)} عوائق {want} فجاء {len(got)} [{joined}] · الناقص: {missing}"
            )
        cases += 1
        if any(k in joined for k in ("hello-there", "#top--two", "#explicit-md", "never.md",
                                     "page.md?v=2")):
            failures.append(f"⑫ إيجاب زائف: {joined}")
    finally:
        ROOT = saved
        shutil.rmtree(tmp, ignore_errors=True)

    print(f"الفحص الذاتي: {cases} حالة · {len(failures)} فشل")
    for f in failures:
        print("  ✗", f)
    return 1 if failures else 0


def main() -> int:
    ap = argparse.ArgumentParser(description="حرس أصول README (SVG متحركة + مراجع)")
    # و`assert` كلمة محجوزة في Python، فالاسم المُخزَّن يُسمّى صراحةً بـ`dest`
    ap.add_argument("--assert", dest="assert_mode", action="store_true",
                    help="exit 1 عند أي عائق")
    ap.add_argument("--self-test", dest="self_test", action="store_true",
                    help="يقيس الأداة على حالات معلومة")
    ap.add_argument("--list", dest="list_only", action="store_true",
                    help="يعرض الأصول والمراجع")
    ap.add_argument("--all", dest="all_pages", action="store_true",
                    help="يشمل docs/ai/** وdocs/aegis/** في فحص الروابط ⑫")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    return run(warn_only=not args.assert_mode, all_pages=args.all_pages)


if __name__ == "__main__":
    sys.exit(main())
