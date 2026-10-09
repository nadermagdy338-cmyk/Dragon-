#!/usr/bin/env python3
"""حرس وثيقة التصميم — `DESIGN.md` تُقاس مقابل مصدر الرموز، فلا تصير حكاية.

**لماذا أداة، ولماذا الآن:** `DESIGN.md` وثيقة يقرأها وكيل تصميم ويبني عليها، وقد وُلدت من فكرة
`DESIGN.md` (Google Stitch) كما تعرضها مجموعة `VoltAgent/awesome-design-md`. وتلك المجموعة تُعيد
نسخ أنظمتها من مواقع حيّة، فنسبها إلى الموقع هو ضمانها الوحيد. أمّا عندنا فالمصدر **موجود في
الشجرة**: `MaxTokens.kt` و`theme/Type.kt`. ووثيقة تقول «`gutter` = ٢٠dp» بينما الشفرة تقول
`28.dp` ليست وثيقة متأخّرة، بل **كذب موثَّق** — وهي أسوأ من غياب الوثيقة، لأن الجميع يبني عليها.
وهذه الأداة تجعل ذلك مستحيلًا: كل رقم في الوثيقة يُقارن بمصدره، وكل لون في الأصول يُقارن باللوحة
المُعلَنة، ونِسَب التجاذب المذكورة تُعاد حسابها.

**والقواعد التي تفرضها:**

  ① كل إشارة `Max<كائن>.<عضو>` في الوثيقة **موجودة فعلًا** في `MaxTokens.kt` — والاسم المجرّد
     (`MaxCardSpec` · `MaxMetricType` · `MAX_VALUE_UNAVAILABLE`) يُchecked كذلك، فلا يبقى في
     الوثيقة رمز مُتخيَّل ولا خطأ إملائي في اسم رمز.
  ② وكل قيمة مكتوبة بجانب الإشارة **تساوي القيمة في المصدر** (`(20dp)` · `= 2` · `| 0.16f |`).
     والإشارة بلا قيمة تُقبل: ليست كل جملة تذكر رقمًا.
  ③ كل لون في الوثيقة من اللوحة المُعلَنة (نغمات التطبيق + لوحة سطح الـREADME) — فلا يُخترع لون
     في وثيقة التصميم نفسها.
  ④ **كل لون في `docs/assets/*.svg` من لوحة الـREADME**: هذا هو الحرس الذي يمنع «اللون الشارد»
     — أصل واحد بلون لا يعرفه أحد يُقرأ كأن الصفحة جُمعت من مصادر لا كأنها منتج واحد.
  ⑤ صفوف المحارف في الوثيقة تساوي `theme/Type.kt` في الحجم وارتفاع السطر (والوزن إن ذُكر).
  ⑥ نِسَب التجاذب المكتوبة في جدول النغمات **تُعاد حسابها** من اللونين — رقم يُصدَّق أبدًا يُتلف
     الوثيقة أسرع من غيابها.

  `--self-test` يبني شجرة مصغّرة معلومة النتيجة ويطالب باسم كل قاعدة عند كسرها: أداة لا تُكسر لا تقيس.

الاستعمال:  python3 tools/design_doc.py --assert  ·  --self-test  ·  --list  ·  --json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TOKENS_KT = "manager/app/src/main/java/nd/max/ui/design/MaxTokens.kt"
TYPE_KT = "manager/app/src/main/java/nd/max/ui/theme/Type.kt"
DESIGN_MD = "DESIGN.md"
ASSETS_DIR = "docs/assets"

# مصادر التصميم التي يجوز للوثيقة أن تسمّي أسماءها: ملفّ الرموز أوّلًا، ثم طبقات الثيم التي
# تُنتج ما يستهلكه الرموز. ولو حُصرت في `MaxTokens.kt` لكان أفضل ما في الوثيقة (`MaxTextRole`
# و`MonoValueStyle*`) هو ما تفشل عليه القاعدة ① — وهو عكس المقصود: القاعدة تمنع الاسم المتخيَّل،
# لا الاسم الحقيقي في ملفّ آخر.
DESIGN_SOURCES = [
    TOKENS_KT,
    TYPE_KT,
    "manager/app/src/main/java/nd/max/ui/theme/MaxTypography.kt",
    "manager/app/src/main/java/nd/max/ui/theme/Shape.kt",
    "manager/app/src/main/java/nd/max/ui/theme/Fonts.kt",
]

# ألوان الوثيقة المسموحة تُقرأ من **ملفّ الرموز وحده** (النغمات الثابتة) + لوحة الـREADME:
# توسيعها بلوحة الثيم كانت ستسمح بأيّ لون دخل scheme يومًا، فتُفقد القاعدة ③ معناها.

# امتدادات ملفّات: `MaxTokens.kt` في ظهر مائل اسم ملفّ لا عضو في كائن.
FILE_EXT = {"kt", "kts", "py", "md", "yml", "yaml", "json", "svg", "sh", "toml", "xml", "txt"}

# وحدات تُكتب في الوثيقة وحدها: `MaxDuration` قيمها أعداد بالمللي ثانية في المصدر (بلا وحدة).
UNIT_OVERRIDE = {"MaxDuration": "ms"}

# خلفيّتان مرجعيّتان لقياس التجاذب في جدول النغمات (وهما المذكورتان في نصّ الوثيقة).
DARK_REFERENCE = "#101820"
LIGHT_REFERENCE = "#FFFFFF"

# جسر بين مفردات الوثيقة والمفردات في الشيفرة: الوثيقة تسمّي الأشياء بما تعنيه،
# والشفرة بما صار عليه الاسم — والجسر مُعلَن هنا بدل أن يُخفى.
TYPE_ALIASES = {
    "live-value-large": "MonoValueStyleLarge",
    "live-value-medium": "MonoValueStyleMedium",
    "live-value-small": "MonoValueStyleSmall",
}


# ── قراءة المصدر: الرموز ───────────────────────────────────────────────────
def _strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def parse_tokens(path: str) -> tuple[dict[str, tuple[float, str]], set[str], set[str]]:
    """يُعيد (الرموز بمنطوق `Object.name` → (قيمة، وحدة)، الأسماء المجرّدة، ألوانها).

    تُقرأ الرموز المحسوبة أيضًا (`MaxSpace.lg + 2.dp`) ويُحسب ناتجها، لأن رقمًا محسوبًا في المصدر
    ورقمًا مكتوبًا في الوثيقة لا يزالان قابلين للمقارنة — وتركه بلا فحص كان سيعني ثغرة في القاعدة ②.
    """
    raw = _strip_comments(open(path, encoding="utf-8").read())
    values: dict[str, tuple[float, str]] = {}
    names: set[str] = set()
    colours: set[str] = set()
    colours.update("#" + h.upper() for h in re.findall(r"Color\(0xFF([0-9A-Fa-f]{6})\)", raw))
    names.update(re.findall(r"\b([A-Z_]{4,})\b\s*(?::[^=]+)?=\s*\"—\"", raw))
    names.update(re.findall(r"const val ([A-Z_]+)", raw))

    pending: dict[str, str] = {}
    for obj, body in _objects(raw):
        names.add(obj)
        for entry in re.findall(r"^\s*([A-Z]\w*)\s*,?\s*$", body, re.M):
            names.add(entry)  # مدخلات enum (والأخير بلا فاصلة)
        for line in body.splitlines():
            m = re.match(r"\s*(?:const\s+)?val\s+(\w+)(?:\s*:\s*[\w<>]+)?\s*=\s*(.+?)\s*$", line)
            if not m:
                continue
            name, expr = m.group(1), m.group(2)
            names.add(name)
            if name.startswith("//"):
                continue
            pending[f"{obj}.{name}"] = expr

    # حلّ متكرّر: مرجع كائن آخر (`MaxSize.iconContainer`) أو حساب (`MaxSpace.lg + 2.dp`).
    for _ in range(6):
        changed = False
        for key, expr in list(pending.items()):
            if key in values:
                continue
            got = _resolve(expr, values)
            if got is not None:
                values[key] = got
                changed = True
        if not changed:
            break

    for key, (value, unit) in list(values.items()):
        override = UNIT_OVERRIDE.get(key.split(".")[0])
        if override and unit == "":
            values[key] = (value, override)
    return values, names, colours


def _objects(text: str) -> list[tuple[str, str]]:
    """جسم كل `object X { … }` و`enum class Y { … }` بعمق الأقواس — لا بتعبير يقف عند أول `}`.

    و`enum class` مطلوبة كما `object`: الوثيقة تسمّي `MaxTone.Accent` بالأسم، وهو مدخل في تعديد
    لا عضو في كائن — فبلا هذا السطر كان أفضل تعريف في الوثيقة هو ما تفشل عليه القاعدة ①.
    """
    out = []
    for m in re.finditer(r"\b(?:object|enum class)\s+(\w+)\s*\{", text):
        depth, start = 1, m.end()
        i = start
        while i < len(text) and depth:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
            i += 1
        out.append((m.group(1), text[start:i - 1]))
    return out


def _resolve(expr: str, known: dict[str, tuple[float, str]]):
    expr = expr.split("//")[0].strip()
    # الرقم: صحيح أو عشري بجزء كسري إلزامي بعد النقطة. كان النمط `[\d.]+` الشرِه يبتلع نقطة
    # الوحدة في `1.5.dp` فيعود `'1.5.'` ويسقط `float()` — فصار الرقم محدَّدًا والنقطة الفاصلة
    # قبل الوحدة اختيارية خارج المجموعة.
    num = r"-?(?:\d+(?:\.\d+)?|\.\d+)"
    m = re.fullmatch(rf"({num})\.?(dp|sp)?", expr)
    if m:
        return float(m.group(1)), m.group(2) or ""
    m = re.fullmatch(rf"({num})f", expr)
    if m:
        return float(m.group(1)), "f"
    if expr.count("+") == 1:
        left, right = [p.strip() for p in expr.split("+")]
        a = known.get(left) or _resolve(left, known)
        b = known.get(right) or _resolve(right, known)
        if a and b:
            return a[0] + b[0], a[1] or b[1]
    return known.get(expr)


def fmt(value: float, unit: str) -> str:
    num = f"{value:g}"
    return f"{num}{unit if unit != 'f' else 'f'}"


# ── قراءة المصدر: المحارف ─────────────────────────────────────────────────
def parse_types(path: str) -> dict[str, dict[str, str]]:
    raw = _strip_comments(open(path, encoding="utf-8").read())
    out: dict[str, dict[str, str]] = {}
    for m in re.finditer(r"(\w+)\s*=\s*TextStyle\(", raw):
        name, start = m.group(1), m.end()
        depth, i = 1, start
        while i < len(raw) and depth:
            if raw[i] == "(":
                depth += 1
            elif raw[i] == ")":
                depth -= 1
            i += 1
        body = raw[start:i - 1]
        entry: dict[str, str] = {}
        for field in ("fontSize", "lineHeight", "fontWeight"):
            v = re.search(rf"{field}\s*=\s*([^,\n]+)", body)
            if v:
                entry[field] = v.group(1).strip()
        out[name] = entry
    return out


def camel(kebab: str) -> str:
    parts = kebab.replace("_", "-").split("-")
    return parts[0] + "".join(p.title() for p in parts[1:])


# ── التجاذب ───────────────────────────────────────────────────────────────
def _srgb(c: float) -> float:
    c /= 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4


def contrast(a: str, b: str) -> float:
    def lum(h: str) -> float:
        h = h.lstrip("#")
        r, g, bl = (int(h[i:i + 2], 16) for i in (0, 2, 4))
        return 0.2126 * _srgb(r) + 0.7152 * _srgb(g) + 0.0722 * _srgb(bl)
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


# ── الفحص ─────────────────────────────────────────────────────────────────
def check_design(text: str, tokens: dict, names: set[str], typekt: dict,
                 allowed_colours: set[str]) -> list[str]:
    problems: list[str] = []
    flat = re.sub(r"[ \t]+", " ", text)

    for m in re.finditer(r"`([A-Za-z_][\w]*)(?:\.(\w+))?`", flat):
        head, member = m.group(1), m.group(2)
        if not (head.startswith("Max") or head.startswith("MAX_")):
            continue
        # اسم ملفّ (`MaxTokens.kt`) أو عنوان رابط (`[`MAX_TOKENS`](…)`) ليس رمزًا في الشيفرة.
        if member in FILE_EXT:
            continue
        if m.start() and flat[m.start() - 1] == "[":
            continue
        if head not in names and f"{head}.{member}" not in tokens:
            problems.append(f"① اسم غير موجود في مصدر الرموز: `{head}`")
            continue
        if member and f"{head}.{member}" not in tokens and member not in names:
            problems.append(f"① عضو غير موجود: `{head}.{member}`")
            continue

        # ② القيمة المكتوبة بجانب الإشارة — إن وُجدت — تساوي المصدر.
        full = f"{head}.{member}" if member else head
        if full not in tokens:
            continue
        value, unit = tokens[full]
        tail = flat[m.end():m.end() + 40]
        m2 = (re.match(r"\s*\(\s*(-?[\d.]+)\s*(dp|sp|ms|f)?\s*\)", tail)
              or re.match(r"\s*=\s*(-?[\d.]+)\s*(dp|sp|ms|f)?", tail)
              or re.match(r"\s*\|\s*(-?[\d.]+)\s*(dp|sp|ms|f)?\s*\|", tail))
        if not m2:
            continue
        cited_num = float(m2.group(1))
        cited_unit = m2.group(2) or ""
        if abs(cited_num - value) > 1e-9 or cited_unit != unit:
            problems.append(
                f"② `{full}` في الوثيقة {fmt(cited_num, cited_unit)} وفي المصدر {fmt(value, unit)}"
            )

    for m in re.finditer(r"`(#[0-9A-Fa-f]{6})`", text):
        if m.group(1).upper() not in allowed_colours:
            problems.append(f"③ لون خارج اللوحة المُعلَنة: {m.group(1)}")

    # ⑤ صفوف المحارف وكتل الواجهة الأمامية تساوي `Type.kt`.
    for m in re.finditer(r"^\|\s*(?:mono\s+)?`?([a-zA-Z][\w-]*)`?\s*\|\s*([\d.]+)sp\s*\|[^|]*\|"
                         r"\s*([\d.]+)sp\s*\|", text, re.M):
        role, size, lh = m.group(1), float(m.group(2)), float(m.group(3))
        src = typekt.get(TYPE_ALIASES.get(role, camel(role)))
        if not src:
            problems.append(f"⑤ دور محرفيّ غير موجود في `Type.kt`: {role}")
            continue
        for field, cited in (("fontSize", size), ("lineHeight", lh)):
            if field in src and abs(float(re.sub(r"[^\d.]", "", src[field])) - cited) > 1e-9:
                problems.append(f"⑤ `{role}`.{field}: الوثيقة {cited:g} والمصدر {src[field]}")
    for block in re.findall(r"^  ([\w-]+):\n((?:    .*\n)+)", text, re.M):
        key, body = block
        src = typekt.get(TYPE_ALIASES.get(key, camel(key)))
        if not src:
            continue
        for field in ("fontSize", "lineHeight"):
            m = re.search(rf"^\s+{field}:\s*([\d.]+)sp", body, re.M)
            if m and field in src:
                cited = float(m.group(1))
                if abs(float(re.sub(r"[^\d.]", "", src[field])) - cited) > 1e-9:
                    problems.append(f"⑤ {key}.{field}: الوثيقة {cited:g} والمصدر {src[field]}")

    # ⑥ نِسَب التجاذب في جدول النغمات تُعاد حسابها.
    for m in re.finditer(r"^\|\s*\w+\s*\|\s*`(#[0-9A-Fa-f]{6})`\s*\|\s*([\d.]+)\s*\|"
                         r"\s*`(#[0-9A-Fa-f]{6})`\s*\|\s*([\d.]+)\s*\|", text, re.M):
        for hexv, cited, bg in ((m.group(1), float(m.group(2)), DARK_REFERENCE),
                                (m.group(3), float(m.group(4)), LIGHT_REFERENCE)):
            real = round(contrast(hexv, bg), 2)
            if abs(real - cited) > 0.011:
                problems.append(
                    f"⑥ تجاذب {hexv} على {bg}: الوثيقة {cited:g} والمحسوب {real:g}"
                )
    return problems


# ── التشغيل ───────────────────────────────────────────────────────────────
def assess(root: str = ROOT) -> tuple[list[str], dict]:
    tokens: dict[str, tuple[float, str]] = {}
    names: set[str] = set()
    colours: set[str] = set()
    for rel in DESIGN_SOURCES:
        path = os.path.join(root, rel)
        if not os.path.exists(path):
            continue
        got_tokens, got_names, got_colours = parse_tokens(path)
        tokens.update(got_tokens)
        names |= got_names
        if rel == TOKENS_KT:
            colours |= got_colours
    typekt = parse_types(os.path.join(root, TYPE_KT))
    text = open(os.path.join(root, DESIGN_MD), encoding="utf-8").read()
    allowed = palette_under(root) | colours
    problems = check_design(text, tokens, names, typekt, allowed)
    problems += check_assets_under(root, allowed)[0]
    problems += check_palette_tones(root, colours)
    stats = {"tokens": len(tokens), "names": len(names), "colours": len(allowed)}
    return problems, stats


def palette_dicts(root: str) -> list[dict]:
    """اللوحتان المُعلَنتان للأصول — تُستوردان من المولّد نفسه، فليستا نسخة ثانية تُقارَن بنسخة."""
    sys.path.insert(0, os.path.join(root, "tools"))
    for mod in [m for m in sys.modules if m == "gen_readme_assets"]:
        del sys.modules[mod]  # الشجرة قد تُقاس أكثر من مرّة، والوحدة تُقرأ من القرص في كل مرّة
    import gen_readme_assets as gen  # noqa: E402
    return [gen.DARK, gen.LIGHT]


def palette_under(root: str) -> set[str]:
    out = set()
    for pal in palette_dicts(root):
        out.update(v.upper() for v in pal.values() if isinstance(v, str) and v.startswith("#"))
    return out


def check_palette_tones(root: str, app_colours: set[str]) -> list[str]:
    """⑦ نغمات الحالة في لوحة الأصول = ثوابت التطبيق — بالحرف لا بالتماثل.

    وهو أقوى ربط في الأداة: رسم الـREADME يقول لون «الحرارة مرتفعة»، وقيمة ذلك اللون في
    `MaxTokens.kt` ثابتٌ اختبارُه على خلفيتين. ولو تغيّر ثابت التطبيق يومًا وبقيت الصورة على
    القديم، لكانت الصفحة تعرض تحذيرًا بلون لم يعد التطبيق يستعمله — وتلك أرخص طريقة لتكذيب وثيقة
    تصميم: رسم لم يُحدَّث.
    """
    problems: list[str] = []
    for i, pal in enumerate(palette_dicts(root)):
        mode = "DARK" if i == 0 else "LIGHT"
        for key, value in pal.items():
            if key.startswith("tone") and value.upper() not in app_colours:
                problems.append(
                    f"⑦ نغمة {mode}.{key} = {value} وليست في ثوابت ألوان `MaxTokens.kt`"
                )
    return problems


def check_assets_under(root: str, allowed: set[str]) -> tuple[list[str], int]:
    problems: list[str] = []
    files = 0
    base = os.path.join(root, ASSETS_DIR)
    if not os.path.isdir(base):
        return problems, files
    for name in sorted(os.listdir(base)):
        if not name.endswith(".svg"):
            continue
        files += 1
        text = open(os.path.join(base, name), encoding="utf-8").read()
        for hexv in {h.upper() for h in re.findall(r"#[0-9A-Fa-f]{6}", text)}:
            if hexv not in allowed:
                problems.append(f"④ {name}: لون شارد {hexv} ليس في لوحة الـREADME")
    return problems, files


def self_test() -> int:
    import shutil
    import tempfile

    failures: list[str] = []
    tmp = tempfile.mkdtemp(prefix="design-doc-")
    try:
        os.makedirs(os.path.join(tmp, "tools"))
        os.makedirs(os.path.join(tmp, os.path.dirname(TOKENS_KT)))
        os.makedirs(os.path.join(tmp, os.path.dirname(TYPE_KT)))
        os.makedirs(os.path.join(tmp, ASSETS_DIR))
        tools_src = os.path.join(ROOT, "tools")
        for name in ("gen_readme_assets.py",):
            shutil.copy(os.path.join(tools_src, name), os.path.join(tmp, "tools", name))

        open(os.path.join(tmp, TOKENS_KT), "w", encoding="utf-8").write(
            "package nd.max.ui.design\n"
            "object MaxSpace {\n    val gutter: Dp = 20.dp\n    val lg: Dp = 16.dp\n    val hair: Dp = 1.5.dp\n}\n"
            "object MaxRadius {\n    val group: Dp = 22.dp\n}\n"
            "object MaxCardSpec {\n    val radius: Dp = MaxRadius.group\n"
            "    val titleLines: Int = 2\n    val pad: Dp = MaxSpace.lg + 2.dp\n}\n"
            "object MaxAlpha {\n    const val border = 0.16f\n}\n"
            "object MaxDuration {\n    const val instant = 90\n    const val deliberate = 360\n}\n"
            "enum class MaxTone {\n    Neutral,\n    Accent,\n}\n"
            "const val MAX_VALUE_UNAVAILABLE = \"—\"\n"
            "private val P = Color(0xFF5FD9AC)\n"
            "private val Q = Color(0xFF0B6B4F)\n"
            "private val R = Color(0xFFFFB86B)\n"
            "private val S = Color(0xFF8A5200)\n"
            "private val T = Color(0xFFFF9A90)\n"
            "private val U = Color(0xFF9A1B1B)\n")
        open(os.path.join(tmp, TYPE_KT), "w", encoding="utf-8").write(
            "package nd.max.ui.theme\n"
            "val Typography = Typography(\n"
            "    titleMedium = TextStyle(\n        fontFamily = DisplayFontFamily,\n"
            "        fontWeight = FontWeight.SemiBold,\n        fontSize = 16.sp,\n"
            "        lineHeight = 24.sp\n    ),\n)\n"
            "val MonoValueStyleLarge = TextStyle(\n    fontFamily = MonoFontFamily,\n"
            "    fontSize = 34.sp,\n    lineHeight = 38.sp\n)\n")

        good = ("# وثيقة\n\n"
                "| الرمز | القيمة |\n| --- | --- |\n"
                "| `MaxSpace.gutter` | 20dp |\n"
                "| `MaxSpace.hair` | 1.5dp |\n"
                "| `MaxAlpha.border` | 0.16f |\n"
                "| `MaxDuration.instant` | 90ms |\n\n"
                "`MaxCardSpec.titleLines` = 2 · `MaxCardSpec.pad` (18dp) · `MaxCardSpec.radius`\n"
                "والكائن `MaxCardSpec` و`MAX_VALUE_UNAVAILABLE` و`MaxTone.Accent`\n\n"
                "| الدور | الحجم | الوزن | ارتفاع السطر |\n| --- | --- | --- | --- |\n"
                "| `titleMedium` | 16sp | SemiBold | 24sp |\n"
                "| mono `live-value-large` | 34sp | SemiBold | 38sp |\n\n"
                "| النغمة | داكن | النسبة | فاتح | النسبة |\n| --- | --- | --- | --- | --- |\n"
                f"| Positive | `#5FD9AC` | {round(contrast('#5FD9AC', DARK_REFERENCE), 2)} | "
                f"`#0B6B4F` | {round(contrast('#0B6B4F', LIGHT_REFERENCE), 2)} |\n")
        open(os.path.join(tmp, DESIGN_MD), "w", encoding="utf-8").write(good)
        tile = "#5FD9AC"
        open(os.path.join(tmp, ASSETS_DIR, "a.svg"), "w", encoding="utf-8").write(
            f'<svg fill="{tile}"/>\n')

        problems, _ = assess(tmp)
        if problems:
            failures.append("الحالة السليمة رُفضت: " + "; ".join(problems))

        # ① اسم غير موجود
        bad = good.replace("`MaxSpace.gutter`", "`MaxSpace.gutters`")
        open(os.path.join(tmp, DESIGN_MD), "w", encoding="utf-8").write(bad)
        if not any(p.startswith("①") for p in assess(tmp)[0]):
            failures.append("① اسم غير موجود لم يُمسَك")

        # ② قيمة مخالفة للمصدر
        bad = good.replace("| `MaxSpace.gutter` | 20dp |", "| `MaxSpace.gutter` | 28dp |")
        open(os.path.join(tmp, DESIGN_MD), "w", encoding="utf-8").write(bad)
        if not any(p.startswith("②") for p in assess(tmp)[0]):
            failures.append("② قيمة مخالفة لم تُمسَك")

        # ③ لون خارج اللوحة داخل الوثيقة
        bad = good + "\nاللون `#123456` مُستعمل.\n"
        open(os.path.join(tmp, DESIGN_MD), "w", encoding="utf-8").write(bad)
        if not any(p.startswith("③") for p in assess(tmp)[0]):
            failures.append("③ لون خارج اللوحة في الوثيقة لم يُمسَك")

        # ④ لون شارد في أصل
        open(os.path.join(tmp, DESIGN_MD), "w", encoding="utf-8").write(good)
        open(os.path.join(tmp, ASSETS_DIR, "a.svg"), "w", encoding="utf-8").write(
            '<svg fill="#ABCDEF"/>\n')
        if not any(p.startswith("④") for p in assess(tmp)[0]):
            failures.append("④ لون شارد في أصل لم يُمسَك")

        # ⑤ محرف مخالف
        open(os.path.join(tmp, ASSETS_DIR, "a.svg"), "w", encoding="utf-8").write(
            f'<svg fill="{tile}"/>\n')
        bad = good.replace("| `titleMedium` | 16sp | SemiBold | 24sp |",
                           "| `titleMedium` | 18sp | SemiBold | 24sp |")
        open(os.path.join(tmp, DESIGN_MD), "w", encoding="utf-8").write(bad)
        if not any(p.startswith("⑤") for p in assess(tmp)[0]):
            failures.append("⑤ محرف مخالف لم يُمسَك")

        # ⑥ تجاذب مُدَّعى خطأً
        bad = good.replace(f"| Positive | `#5FD9AC` | {round(contrast('#5FD9AC', DARK_REFERENCE), 2)} |",
                           "| Positive | `#5FD9AC` | 3.10 |")
        open(os.path.join(tmp, DESIGN_MD), "w", encoding="utf-8").write(bad)
        if not any(p.startswith("⑥") for p in assess(tmp)[0]):
            failures.append("⑥ تجاذب خاطئ لم يُمسَك")

        # ⑦ نغمة في لوحة الأصول لا تعرفها الشيفرة: تُزرع مؤقّتًا في المولّد المنسوخ
        open(os.path.join(tmp, DESIGN_MD), "w", encoding="utf-8").write(good)
        gen_path = os.path.join(tmp, "tools", "gen_readme_assets.py")
        src = open(gen_path, encoding="utf-8").read()
        open(gen_path, "w", encoding="utf-8").write(src.replace(
            'DARK = dict(bg=', 'DARK = dict(toneBogus="#123456", bg=', 1))
        if not any(p.startswith("⑦") for p in assess(tmp)[0]):
            failures.append("⑦ نغمة غير معروفة في لوحة الأصول لم تُمسَك")
        open(gen_path, "w", encoding="utf-8").write(src)
        if assess(tmp)[0]:
            failures.append("الحالة السليمة رُفضت في الجولة الأخيرة")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    total = 8
    if failures:
        print(f"❌ الفحص الذاتي: {len(failures)} فشل من {total} حالة", file=sys.stderr)
        for line in failures:
            print(f"   · {line}", file=sys.stderr)
        return 1
    print(f"الفحص الذاتي: {total} حالة · 0 فشل")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="حرس وثيقة التصميم: DESIGN.md مقابل مصدر الرموز")
    ap.add_argument("--assert", dest="do_assert", action="store_true",
                    help="رماز خروج 1 عند أي انحراف بين الوثيقة والمصدر")
    ap.add_argument("--self-test", action="store_true", help="الأداة تقيس نفسها")
    ap.add_argument("--list", action="store_true", help="اطبع الرموز المقروءة من المصدر")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--root", default=ROOT)
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    tokens, names, colours = parse_tokens(os.path.join(args.root, TOKENS_KT))
    if args.list:
        for key in sorted(tokens):
            print(f"  {key:<34} {fmt(*tokens[key])}")
        print(f"\nالمجموع: {len(tokens)} رمزًا · {len(names)} اسمًا · {len(colours)} لونًا في المصدر")
        return 0

    problems, stats = assess(args.root)
    if args.json:
        print(json.dumps({"problems": problems, **stats}, ensure_ascii=False, indent=2))
    else:
        print(f"مقيس: {stats['tokens']} رمزًا · {stats['names']} اسمًا · "
              f"{stats['colours']} لونًا في اللوحة المُعلَنة")
        print("الحصيلة: وثيقة التصميم مطابقة لمصدر الرموز" if not problems
              else f"الحصيلة: {len(problems)} انحرافًا")

    if problems:
        print("\n❌ الوثيقة والمصدر لا يتفقان:", file=sys.stderr)
        for line in problems:
            print(f"   {line}", file=sys.stderr)
        print("\nصحّح المصدر أوّلًا (`MaxTokens.kt` / `theme/Type.kt`)، ثم انقل الرقم إلى الوثيقة."
              " والوثيقة التي تُصدّق رقمًا لم تعد تقيس.", file=sys.stderr)
        return 1
    if not args.do_assert:
        return 0
    print("بوابة وثيقة التصميم: exit 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
