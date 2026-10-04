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

عقد المحتوى القابل للرسم (أُضيف بعد عطب CI حقيقي)
-------------------------------------------------
`content: ColumnScope.() -> Unit` بلا `@Composable` لا يُفسد ملفه وحده: يُفسد **كل موضع
نداء** له، برسالة `@Composable invocations can only happen from the context of a @Composable
function` على أسطر لا علاقة لها بالملف المعطوب. وهذا جعل عطب سطر واحد يظهر كخمسة أخطاء في
ملف آخر، فبُحث عنه في المكان الخطأ. الفحص هنا يقرأ **التصريح** (لا موضع الاستعمال) ويقول
الملف والسطر الحقيقيان.
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
# `REPAIR_NOTES.md` مُعلَن هنا لأنه **نثر مشروع** (تحليل إصلاح مُسلَّم في الحزمة)، لا ملف
# شخصي ولا حطام. والقائمة نفسها هي القرار: ما عداها = يُسأل عنه لا يُسمح به صامتًا.
ROOT_ALLOWED = {
    ".gitattributes", ".gitignore", "AGENTS.md", "LICENSE", "THIRD_PARTY_NOTICES.md", "README.md",
    # النسخة العربية من الـREADME: ملفّ جذر مشروع لا حطام. أُضيف بعد أن أسقطته البوابة فعلًا
    # (`stray_root_file: 1`) وهو ملفّ جديد غير متعقّب بعد — فالاسم يُعلن هنا لا يُستثنى مؤقّتًا.
    "README.ar.md",
    # وثيقة لغة التصميم. ومكانها **الجذر** لا `docs/` بحكم الصيغة نفسها: مفهوم `DESIGN.md`
    # (ومن ورائه مجموعة `VoltAgent/awesome-design-md`) يقوم على أنّ الوكيل يقرأه من جذر المشروع.
    # وأسقطتها هذه البوابة فعلًا (`stray_root_file: 1`) عند إضافتها — فالاسم يُعلن هنا بقرار،
    # لا يُستثنى بمسار آخر لأجل تمرير الفحص.
    "DESIGN.md",
    "REPAIR_NOTES.md", "changelog.md", "crowdin.yml", "logo.jpg", "maxmanagerApplist.json",
    "module.json", "update.json", "version", "version_type",
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
    2. `R` صنف **لكل موديل**، لا للمستودع كله. وكان فحص مراجع `kernel-flasher`
       (وحدة متضمّنة، حُذفت في جولة تدقيق الأصل `PROVENANCE-01`) ضد موارد `app`
       يُنتج ٧٤ بلاغًا كاذبًا. لذلك يأخذ هذا الدال مجلد res الخاص بالموديل، ويُستدعى
       مرة لكل موديل — وهو ما يبقيه صحيحًا لأي وحدة تُضاف غدًا.
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

# ── نصوص الواجهة في موضع *وسيط* لا في `Text("…")` ──────────────────────────
#
# **وسبب وجود هذا الفحص مقيس:** `TEXT_LITERAL` أعلى يُحصي `Text("…")` وحده، فأبلغ
# عن **٣٢** نصًّا و«نظيف» وهو ليس نظيفًا: شاشة إعدادات التطبيق كانت تحمل وحدهـا
# أكثر من **٤٥** نصًّا إنجليزيًّا يقرأها المستخدم عربيًّا — في `title =` و`summary =`
# و`contentDescription =` و`confirmText =`. أي أنّ الرقم المُعلن كان **٣٢ من ٧٧**،
# والبوابة تُطمئن على نصف الحقيقة. (وقد قِيس بعد الإصلاح: ٣٢ ⟶ ٥.)
#
# ونطاقه **ضيّق عن قصد**، لأن `label =` في Compose يُستعمل لتسمية حركة
# (`AnimatedContent(label = "donut")`) ولأسماء ألوان في `Theme.kt`: أضافتهما
# أعطت **١١٢** بلاغًا أغلبها كاذب، وفيها يُقرأ العطب الحقيقي ضجيجًا. فقائمة
# الوسائط هنا هي مواضع النسخ التي تُعرض للمستخدم فعلًا، لا كل ما يشبهها.
COPY_PARAM = re.compile(
    r'(?<![\w.])(title|summary|subtitle|description|contentDescription|headline|supporting'
    r'|confirmText|dismissText)\s*=\s*"([^"\\]*(?:\\.[^"\\]*)*)"'
)
# نصّ فيه `$` أو `%` أو `_`/`=`/`.` هو وسيط برمجيّ أو مفتاح أو وحدة، لا جملة تُترجم.
CODEISH_LITERAL = re.compile(r"[$%_=/.]|\b(nd|android|max)\.")


# فاصلة عليا غير مُهرَّبة داخل قيمة نصية: AAPT2 يرفض الملف كله عندها.
#
# لماذا هنا ولا في `i18n_coverage`: العطب ليس ترجميًّا — ملفٌ واحد فيه `app's` يُسقط
# `mergeReleaseResources` برسالة لا تسمّي السبب («Can not extract resource from ParsedResource»)
# فيقضي صاحب المشروع جولة CI كاملة (٤ دقائق) على لغز لا علاقة له بما عدّله. والقاعدة في أندرويد
# واحدة: إمّا `\'` أو إحاطة القيمة بعلامتَي تنصيص `"…"` — وهذا ما يقيسه هذا الفحص.
#
# **⚠️ وكان الحرس نصفه أعمى، وهذا مُقاس (تكملة ١٤٢):** الصيغة القديمة `(?<!\\)'` ترى الفاصلة
# العارية **وحدها**، فلا ترى `\\'` — وهي أيضًا لا يقبلها aapt2: `\\` شرطة خلفية حرفية ثم `'`
# غير مُهرَّبة. وهذان الحرفان بالضبط ما أنتجه `escape_android` في `--apply-csv` (كان يُهرّب كل
# `'` بلا فحص، فيُضاعف المُهرَّبة سابقًا): **٥٨٥ موضعًا في ٨٢ لغة** سقط بها
# `mergeReleaseResources` بينما هذه البوابة **خضراء** («صحّة = 0») والباني لا يصل إليها في CI
# أصلًا. فالمقياس الصحيح **زوجيّة عدد الشرطات قبل الفاصلة**: فرديّ = مُهرَّبة (تمرّ) · زوجيّ =
# عارية (تُبلَّغ). والتعبير يقيس ذلك حرفيًّا: لا شرطة قبله (`(?<!\\)`) ثم أزواج من الشرطات.
APOSTROPHE = re.compile(r"(?<!\\)(?:\\\\)*'")
QUOTED_VALUE = re.compile(r'^\s*".*"\s*$', re.S)
VALUE_PARENTS = {"string", "string-array", "plurals", "array"}


def apostrophe_offenders() -> list[str]:
    """كل قيمة نصية في كل موديل تحمل فاصلة عليا لا يقبلها AAPT2."""
    offenders: list[str] = []
    for res_dir in sorted(glob.glob(os.path.join(MANAGER, "*", "src", "*", "res"))):
        for p in glob.glob(os.path.join(res_dir, "values*", "**", "*.xml"), recursive=True):
            try:
                root = ET.parse(p).getroot()
            except ET.ParseError:
                continue
            parents = {child: parent for parent in root.iter() for child in parent}
            for node in root.iter():
                parent = parents.get(node)
                is_value = node.tag == "string" or (
                    node.tag == "item" and parent is not None and parent.tag in VALUE_PARENTS
                )
                if not is_value:
                    continue
                text = "".join(node.itertext())
                if not text.strip() or QUOTED_VALUE.match(text):
                    continue
                if APOSTROPHE.search(text):
                    offenders.append(
                        f"{rel(p)}: <{node.tag} name={node.get('name')}> {text.strip()[:60]!r}"
                    )
    return offenders


def check_correctness(files: list[str], mods: dict) -> dict:
    result: dict[str, list[str]] = {
        "package_mismatch": [], "unresolved_resource": [], "duplicate_string_key": [],
        "unescaped_apostrophe": [], "stray_root_file": [],
        "stray_root_file_unverified": [],
        "noncomposable_content_lambda": [],
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

    result["unescaped_apostrophe"].extend(apostrophe_offenders())
    result["noncomposable_content_lambda"].extend(content_contract_offenders(files))

    # ملف الجذر يُعدّ حطامًا فقط إن لم يكن متعقّبًا **ولم يكن متجاهلًا**: علامة أداة
    # محلية مذكورة في .gitignore ليست ضجيجًا، فلا تُبلَّغ.
    #
    # ⚠️ وقبل ذلك: بلا مستودع git **لا سبيل** إلى هذا التمييز، وسؤال git يفشل بـ128 فيُقرأ
    # خطأً «غير متعقّب وغير متجاهل» ⇒ **عطبٌ وهميّ**. وهذا وقع فعلًا: `.maxmanager-sync-root`
    # مذكور في `.gitignore:40` وأُبلغ عطبًا في بيئة بلا git (تكملة ١١٤). فالحكم الصادق حينها
    # «غير مُتحقَّق» لا «عطب»: لا يُدّعى عطب بلا دليل، ولا يُقال «نظيف» بلا قياس. وفي CI
    # (حيث git موجود) يبقى الفحص كاملًا كما كان.
    git_usable = subprocess.run(
        ["git", "rev-parse", "--is-inside-work-tree"],
        cwd=_REPO, capture_output=True,
    ).returncode == 0
    for name in sorted(os.listdir(_REPO)):
        if not git_usable:
            if not name.startswith(".git") and name != "build":
                full_name = os.path.join(_REPO, name)
                if os.path.isfile(full_name) and name not in ROOT_ALLOWED:
                    result["stray_root_file_unverified"].append(name)
            continue
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


# عقد المحتوى القابل للرسم.
# القاعدة مشتقّة من قياس المستودع كاملًا: 31 بارامتر محتوى في واجهات المكوّنات، 30 منها
# `@Composable` والوحيد الشاذ شرعيّ (نطاق `LazyListScope` لا يُرسم أصلًا). فالقاعدة تصف
# الواقع القائم، لا رأيًا جديدًا. والاستثناء مبنيّ على **نوع النطاق** لا على اسم ملف،
# فلا يحتاج قائمة استثناءات تفنى مع أول إعادة تسمية.
CONTENT_PARAM = re.compile(r"^\s*(content[A-Za-z]*)\s*:\s*(.*->.*?)\s*,?\s*$")
DECL_FUN = re.compile(r"^\s*(?:@\w+\s+)*(?:public|private|internal|protected|external|override|actual|expect|operator|infix|inline|suspend|tailrec|open|final|abstract|sealed|const|lateinit|\s)*fun\s")
DECL_TYPE = re.compile(r"^\s*(?:@\w+\s+)*(?:data|sealed|enum|value|annotation|abstract|open|private|internal|public|\s)*(?:class|interface|object)\s")
LAZY_SCOPE = re.compile(r"\bLazy[A-Za-z]*Scope\b")
# اسم الدالة من سطر إعلانها: يتخطّى الوسائط النوعية (`fun <T>`) والامتداد (`fun A.b`).
FUN_NAME = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\s*\.\s*)?(\w+)")


def _enclosing_owner(lines: list[str], idx: int) -> str | None:
    """نوع المالك الذي ينتمي إليه السطر: 'fun' أو 'type' أو None."""
    for j in range(idx - 1, -1, -1):
        line = lines[j]
        if DECL_FUN.match(line):
            return "fun"
        if DECL_TYPE.match(line):
            return "type"
    return None


def _is_composable_fun(lines: list[str], idx: int) -> bool:
    """هل الدالة الحاوية لهذا السطر موسومة `@Composable`؟"""
    owner = None
    for j in range(idx - 1, -1, -1):
        if DECL_FUN.match(lines[j]):
            owner = j
            break
        if DECL_TYPE.match(lines[j]):
            return False
    if owner is None:
        return False
    k = owner - 1
    while k >= 0:
        stripped = lines[k].strip()
        if not stripped or stripped.startswith("//"):
            k -= 1
            continue
        if stripped == "@Composable" or stripped.startswith("@Composable("):
            return True
        if stripped.startswith("@"):
            k -= 1
            continue
        return False
    return False


def _enclosing_fun_name(lines: list[str], idx: int) -> str | None:
    """اسم الدالة الحاوية — ليكون البلاغ **قابلًا للعمل** لا وصفًا مبهمًا.

    وهذا ليس تجميلًا: البلاغ قبل ذلك كان يسمّي **الوسيط** ('content') ولا يسمّي المكوّن،
    فقارئه لا يعرف أيّ دالة يُصلح. وقد كشفه الفحص الذاتي: تأكيده طلب اسم الدالة (`Bad`)
    فلم يجده أبدًا. أي أن المقياس أمسك عطبًا في **محتوى البلاغ** نفسه.
    """
    for j in range(idx - 1, -1, -1):
        if DECL_FUN.match(lines[j]):
            m = FUN_NAME.search(lines[j])
            return m.group(1) if m else None
        if DECL_TYPE.match(lines[j]):
            return None
    return None


def content_contract_offenders(files: list[str]) -> list[str]:
    offenders: list[str] = []
    for p in files:
        lines = open(p, encoding="utf-8", errors="replace").read().splitlines()
        for i, line in enumerate(lines):
            m = CONTENT_PARAM.match(line)
            if not m:
                continue
            name, typ = m.group(1), m.group(2)
            if "@Composable" in typ or LAZY_SCOPE.search(typ):
                continue
            if _enclosing_owner(lines, i) != "fun":
                continue
            if not _is_composable_fun(lines, i):
                continue
            owner = _enclosing_fun_name(lines, i)
            where = f"fun {owner}" if owner else "دالة قابلة للرسم"
            offenders.append(
                f"{rel(p)}:{i + 1}: في {where} — '{name}: {typ.strip()}' محتوى بلا @Composable"
                f" ⟶ الإصلاح: '{name}: @Composable {typ.strip()},'"
            )
    return offenders


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
    inline_ui_copy: list[tuple[str, str]] = []
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

        for i, line in enumerate(txt.splitlines(), 1):
            stripped = line.strip()
            if COMMENT_LINE.match(stripped) or stripped.startswith("import"):
                continue
            for m in COPY_PARAM.finditer(line):
                value = m.group(2)
                if not HAS_LETTER.search(value) or CODEISH_LITERAL.search(value):
                    continue
                inline_ui_copy.append((f"{rel(p)}:{i}", f"{m.group(1)} = {value[:60]}"))

        todos += len(re.findall(r"\b(TODO|FIXME|XXX|HACK)\b", txt))

        # كتابة عتاد من طبقة العرض — ADR-11.
        # النطاق صريح: تطبيق `nd.max` وحده. ولا يُحتسب `ui/util/` (طبقة الأدوات، وهي
        # المسار المشروع للكتابة بحسب نصّ ADR-11). وكان `kernel-flasher` خارج النطاق
        # أيضًا (وحدة متضمّنة بمسؤوليتها)، وقد حُذفت في `PROVENANCE-01`.
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
        "inline_ui_copy": inline_ui_copy,
        "platform_wildcard_imports": platform_wildcards,
        "todo_markers": todos,
        "counts": {
            "oversized_files": len(oversized),
            "own_wildcard_imports": len(own_wildcards),
            "inline_ui_copy": len(inline_ui_copy),
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
        # «غير مُتحقَّق» ليس عطبًا: يُعرض بعلامة مستقلة ولا يُسقط الصحّة (والأداة تفشل عند
        # العطب وحده — وإلا صارت تُبلّغ عن بيئتها لا عن الكود).
        informational = key.endswith("_unverified")
        mark = "•" if informational else ("✓" if not issues else "✗")
        print(f"  {mark} {key}: {len(issues)}")
        for line in issues[:8]:
            print(f"      - {line}")
        if len(issues) > 8:
            print(f"      … و{len(issues) - 8} غيرها")
        clean = clean and (informational or not issues)

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

    if debt["inline_ui_copy"]:
        print()
        print(f"  نسخ واجهة في موضع وسيط — لا تراه بوابة `Text(\"…\")` "
              f"(أول 15 من {len(debt['inline_ui_copy'])}):")
        for name, lit in debt["inline_ui_copy"][:15]:
            print(f"    {name}: {lit}")

    print()
    print("الحصيلة:", "صحّة نظيفة" if clean else "توجد عيوب صحّة — انظر أعلاه")


# ─────────────────── فحص الأداة نفسها (‏`--self-test`) ───────────────────
#
# **ولماذا وُجد، مقيسًا:** هذه البوابة هي **الأولى في `AGENTS.md` §5 وفي خطوة
# «Contract gates» كلها** — تُسقط التشغيل عند أوّل مفتاح نصّ ينزاح أو فاصلة عليا
# غير مُهرَّبة. وكانت — ومثلها `i18n_coverage.py` — **بلا `--self-test`**: أي أنّ
# أداة تحرس المستودع كله لا يقيس أحدٌ أنّها تقيس شيئًا. والفرق ليس نظريًّا: في الجولة
# نفسها أُضيف فحص `inline_ui_copy` إلى هذه الأداة، فاحتاج **قياسًا يدويًّا** لمعرفة
# أن نطاقه الضيّق مقصود (‏١١٢ بلاغًا قبل التضييق، ٥ بعده). ولو كان الفحص الذاتي
# موجودًا لكان الجواب في أمر واحد.
#
# و**ثلاث مصائد موثَّقة في الملفّ نفسه تُقاس هنا صريحةً**، لأن كلًّا منها أنتج بلاغًا
# كاذبًا أو تغطية ناقصة في تاريخ هذا المستودع:
#   ① `&apos;` و`\'` — كلاهما `'` بعد `ElementTree`؛ البوابة تطالب بـ`\'` أو بفتح
#      القيمة بعلامة تنصيص. (قِيست في تكملة ١٣٨ حين كتبتُ `&apos;`.)
#   ② `2>/dev/null` **إخماد لا كتابة** — مطابقة `>` وحدها أعطت ٨٧ بلاغًا كاذبًا.
#   ③ التعليق ليس كودًا — سطر في `NetworkSchedulerViewModel` كان مطابقًا نصيًّا.
# و**كل تأكيد يقابل حالةً متعاكسة**: عطب يُمسك، ومثيله السليم **يمرّ**. أداة تُبلّغ عن
# الاثنين لا تفرّق بين عطب وسلامة، وهي أسوأ من غياب أداة.

def _write(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def build_fixture() -> str:
    """شجرة مصغرة تُعيد إنتاج أشكال هذه البوابة وأضدادها المعلومة النتيجة."""
    import tempfile

    fixture = tempfile.mkdtemp(prefix="code-health-selftest-")
    res = os.path.join(fixture, "manager/app/src/main/res")
    java = os.path.join(fixture, "manager/app/src/main/java")

    # ①الفواصل العليا: عطب واحد، ومثيلان سليمان (مُهرَّب ومُقتبَس)، ومثيل ثانٍ للعطب (زوجيّ).
    _write(os.path.join(res, "values/strings.xml"),
           '<resources>\n'
           '  <string name="broken">don\'t</string>\n'
           '  <string name="escaped">don\\\'t</string>\n'
           '  <string name="doubled">don\\\\\'t</string>\n'
           '  <string name="quoted">"quoted \'ok\'"</string>\n'
           '</resources>\n')
    # ②مفتاح مكرّر عبر ملفّين في مجلد values واحد.
    _write(os.path.join(res, "values/extra.xml"),
           '<resources>\n  <string name="broken">dup</string>\n</resources>\n')
    # ③مورد معلَن ومورد غير معلَن + حزمة مخالفة للمسار.
    _write(os.path.join(java, "nd/max/ui/Refs.kt"),
           'package nd.max.ui\n\n'
           'val a = R.string.declared_only\n'
           'val b = R.string.nowhere\n')
    # ④عقد محتوى بلا @Composable (يُفسد كل مواضع النداء) + نظيراه السليمان.
    #
    # **والوسيط في سطر مستقلّ عن قصد** — وهذا هو الشكل الذي يقرأه `CONTENT_PARAM`
    # (`^\s*content…`)، والشكل الذي يُكتب به في هذا المستودع. وأول نسخة من هذا التجهيز
    # كتبت الدالة في سطر واحد فلم يُمسك العطب، وكان الظاهر أن الأداة لا تقيس — والخطأ
    # كان في التجهيز. وهو نفس درس `--self-test` في `bundle_contract.py`.
    # والثالث يقيس **الاستثناء الموثَّق** (‏Lazy scope): محتواه `item { … }` بلا @Composable.
    _write(os.path.join(java, "nd/max/ui/Content.kt"),
           'package nd.max.ui\n\n'
           '@Composable\nfun Bad(\n    content: () -> Unit\n) {}\n\n'
           '@Composable\nfun Good(\n    content: @Composable () -> Unit\n) {}\n\n'
           '@Composable\nfun Lazy(\n    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit\n) {}\n\n'
           # ⑤الرابع: دالة **غير** قابلة للرسم بنفس الوسيط — لا تُبلَّغ، لأنّ عقد المحتوى
           # عقدُ مكوّن قابل للرسم لا عقدُ كل دالة. وبلا هذا السطر كان نصف القاعدة بلا مقياس.
           'fun Plain(\n    content: () -> Unit\n) {}\n')
    # ⑤نصوص الواجهة: مباشرة (حساب) وفي موضع وسيط (حساب) وفي تعليق (لا).
    _write(os.path.join(java, "nd/max/ui/Literals.kt"),
           'package nd.max.ui\n\n'
           'fun Panel() = Column {\n'
           '    Text("Straight")\n'
           '    Text(stringResource(R.string.tokened))\n'
           '    Text("$dynamic")\n'
           '    // title = "In a comment"\n'
           '    Card(title = "In argument", summary = "Also argument")\n'
           '    Card(title = "nd.max")\n'
           '}\n')
    # ⑥كتابة عتاد من طبقة العرض: كتابة حقيقية، وإخماد، وتعليق.
    _write(os.path.join(java, "nd/max/ui/Hw.kt"),
           'package nd.max.ui\n\n'
           'fun write() {\n'
           '    exec("echo 1 > /sys/devices/x")\n'
           '    exec("cmd 2>/dev/null")\n'
           '    // exec("echo 1 > /sys/devices/y")\n'
           '}\n')
    # ⑦استيراد شامل لموديل تابع مقابل استيراد منصّة.
    _write(os.path.join(java, "nd/max/ui/Imports.kt"),
           'package nd.max.ui\n\n'
           'import nd.max.ui.component.*\n'
           'import androidx.compose.runtime.*\n\n'
           '// TODO: يُقاس كدَين مراجعة لا كعطب\n')
    # ⑧ملف ضخم واحد ومثيله السليم أسفله بسطر.
    _write(os.path.join(java, "nd/max/ui/Big.kt"),
           'package nd.max.ui\n' + '\n' * (OVERSIZE_LINES + 1))
    _write(os.path.join(java, "nd/max/ui/JustUnder.kt"),
           'package nd.max.ui\n' + '\n' * (OVERSIZE_LINES - 2))
    # ⑨مفتاح نصّي مُعلَن في values كي يُقارن به المرجع.
    _write(os.path.join(res, "values/declared.xml"),
           '<resources>\n  <string name="declared_only">ok</string>\n</resources>\n')
    return fixture


def self_test() -> int:
    """يقيس الأداة على شجرة مصنوعة معلومة النتيجة: ١٥ تأكيدًا متعاكسًا."""
    global _REPO, APP, RES, JAVA, MANAGER

    print("═══ الفحص الذاتي: فاحص النظافة يُقاس على شجرة معلومة ═══")
    fixture = build_fixture()
    saved = (_REPO, APP, RES, JAVA, MANAGER)
    checks: list[tuple[str, bool, str]] = []

    def expect(label: str, actual, wanted) -> None:
        checks.append((label, actual == wanted, f"متوقّع {wanted!r} · حاصل {actual!r}"))

    try:
        _REPO = fixture
        APP = os.path.join(fixture, "manager/app/src/main")
        RES = os.path.join(APP, "res")
        JAVA = os.path.join(APP, "java")
        MANAGER = os.path.join(fixture, "manager")

        files = kt_files()
        mods = module_resources()
        correctness = check_correctness(files, mods)
        debt = collect_debt(files)

        def blob(key: str) -> str:
            return "\n".join(correctness[key])

        # ①الفواصل العليا: العطب وحده — والمُهرَّبة والمُقتبَسة تمرّان — و**الشرطة الزوجية تُمسك**
        # (وهي الحالة التي كانت البوابة عمياء عنها وأسقطت البناء في ٥٨٥ موضعًا: تكملة ١٤٢).
        expect("apostrophe: العطب انمسك", blob("unescaped_apostrophe").count("broken"), 1)
        expect("apostrophe: `\\'` يمرّ", "escaped" in blob("unescaped_apostrophe"), False)
        expect("apostrophe: الزوجيّ (شرطتان) انمسك", blob("unescaped_apostrophe").count("doubled"), 1)
        expect("apostrophe: القيمة المُقتبَسة تمرّ", "quoted" in blob("unescaped_apostrophe"), False)
        # ②المفتاح المكرّر عبر ملفّين.
        expect("duplicate_string_key: انمسك", len(correctness["duplicate_string_key"]), 1)
        # ③المورد غير المعلَن وحده.
        expect("unresolved_resource: الغائب انمسك", blob("unresolved_resource").count("nowhere"), 1)
        expect("unresolved_resource: المُعلَن لم يُبلّغ", "declared_only" in blob("unresolved_resource"), False)
        # ④عقد المحتوى: بلا @Composable فقط (وسليل `ColumnScope` استثناء موثَّق).
        content = blob("noncomposable_content_lambda")
        # والتأكيد باسم **الدالة** لا باسم الوسيط: الأول ما يميّز البلاغ عن غيره،
        # والثاني ('content') يشترك فيه كل بلاغ فيخفي أيّ دالة عُطبت.
        expect("content contract: Bad انمسك", "fun Bad" in content, True)
        expect("content contract: Good يمرّ", "fun Good" in content, False)
        expect("content contract: استثناء Lazy scope يمرّ", "fun Lazy" in content, False)
        expect("content contract: دالة غير قابلة للرسم تمرّ", "fun Plain" in content, False)
        # ⑤العدّ: الحرفي المباشر والوسيط نعم، والقالب والتعليق لا.
        literals = {lit for _, lit in debt["hardcoded_ui_literals"]}
        inline = {lit for _, lit in debt["inline_ui_copy"]}
        expect("literals: Text(\"…\") حُسب", "Straight" in literals, True)
        expect("literals: القالب لم يُحسب", any("$" in lit for lit in literals), False)
        expect("inline: الوسيط حُسب", sum("argument" in lit for lit in inline), 2)
        expect("inline: التعليق لم يُحسب", any("comment" in lit for lit in inline), False)
        expect("inline: نصّ شبه-كوديّ لم يُحسب", any("nd.max" in lit for lit in inline), False)
        # ⑥كتابة العتاد: الكتابة وحدها — لا الإخماد ولا التعليق.
        writes = len(debt["presentation_hw_writes"])
        expect("hw writes: الكتابة حُسبت", writes, 1)
        expect("hw writes: `2>/dev/null` لم يُحسب", any("dev/null" in w for w in debt["presentation_hw_writes"]), False)
        expect("hw writes: التعليق لم يُحسب", any("/sys/devices/y" in w for w in debt["presentation_hw_writes"]), False)
        # ⑦الملف الضخم على الحدّ بالضبط، والاستيراد الشامل بقسميه.
        expect("oversized: على الحدّ انمسك", len(debt["oversized_files"]), 1)
        expect("oversized: أسفل الحدّ مرّ", debt["oversized_files"][0][0].endswith("Big.kt"), True)
        expect("wildcards: التابع حُسب", len(debt["own_wildcard_imports"]), 1)
        expect("wildcards: المنصّة لم تُحتسب", debt["platform_wildcard_imports"], 1)
        expect("todo: حُسب", debt["todo_markers"], 1)
        # ⑧بلا git: الحكم «غير مُتحقَّق» لا «عطب» — وهو فرع موثَّق ومقيس.
        expect("بلا git: بلاغ غير مُتحقَّق لا عطب", len(correctness["stray_root_file"]), 0)
    finally:
        _REPO, APP, RES, JAVA, MANAGER = saved
        import shutil
        shutil.rmtree(fixture, ignore_errors=True)

    failures = 0
    for label, ok, detail in checks:
        print(f"  {'✓' if ok else '✗'} {label}" + ("" if ok else f"\n      {detail}"))
        failures += 0 if ok else 1
    print(f"\nالنتيجة: {len(checks) - failures}/{len(checks)}")
    return 1 if failures else 0


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
    ap.add_argument("--self-test", action="store_true", help="يقيس الأداة نفسها")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

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
            "inline_ui_copy": debt["counts"]["inline_ui_copy"],
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
        if issues and not key.endswith("_unverified"):
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
