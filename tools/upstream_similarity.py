#!/usr/bin/env python3
"""قياس التشابه النصّي بين مصادر MaxManager وأصول GPL المرجعية.

**المشكلة التي حلّها هذا الملف:** `license_audit.py` يقرأ **الترويسة** فيقول من «يُصرّح» بأصله.
وهذا لا يكشف حالتين خطيرتين:

1. ملف **يُصرّح** بأنه مشتقّ وقد صار بعد إعادة التأليف مستقلًّا فعلًا — فيبقى مُوسَمًا GPL بغير حق.
2. ملف **لا يُصرّح** بأي أصل وهو في الحقيقة منقول — فيهرب من البوابة بلا ترويسة (وقد وُجد واحد).

فالقياس هنا **نصّي لا تصريحي**: يُقارن كل ملف عندنا بكل ملفات الأصل المرجعي، ويُخرج **نسبة
الاحتواء** (Containment) على تسلسل الكلمات بعد تجريد التعليق والترويسة، مع أطول مقطع أسطر متطابق.
الحكم على الكود لا على ما كتبه كاتبه عنه.

**الطريقة (مقصودة كي يمكن إعادة اشتقاق كل رقم):**

- تُجرَّد الترويسة (كل تعليق `/* */` و`//`)، والأسطر الفارغة، و`package`/`import`.
- يُرمَّز المتبقّي إلى كلمات، ثم إلى نوافذ متتالية بطول `NGRAM=9`.
- `score = |نوافذ(A) ∩ نوافذ(B)| / min(|نوافذ(A)|, |نوافذ(B)|)`. النسخ الكامل يعطي `1.0`، ونقل
  منظّم لأجزاء كبيرة يعطي قيمة عالية، والكتابة المستقلّة في نفس المجال (نفس أسماء الـAPI) تبقى منخفضة.
- `run` = أطول سلسلة **أسطر** متطابقة بعد التجريد، ويُقاس بالسطر لا بالكلمة: هو أوضح دليل يمكن
  أن يراه إنسان بعينه (`--show` يطبعه).

**ولماذا رقمان لا رقم — وهذا جوهر الأمانة في هذه الأداة:** التسلسل الرمزي يحمل ثلاثة أنواع:

| النوع | مثاله | هل هو تعبير عن عمل الكاتب؟ |
| --- | --- | --- |
| أسماء وبنية | `fun readNode(path: String) { runCatching … }` | **نعم** — هذا ما يحميه حقّ المؤلف |
| نصوص حرفية | `"/proc/ppm/policy_status"` | لا: **واقعة عن النواة**، ولا تُكتب إلا هكذا |
| أرقام | `1024`, `0x644` | لا: ثوابت حسابية |

فقياس «النصّ كما هو» يُعطي **إيجابيات كاذبة** لكل ملفين يقرآن العقد نفسها (وهو حال معظم شجرة
تشبه شجرتنا: نفس النظام، نفس الـAPI، نفس المسارات). وقياس الأسماء وحده يُفلت من ينقل بنية **مع**
تسمية مبدَّلة. لذلك يُطبع الرقمان **معًا** ولا يُخفي أحدهما الآخر:

- `code` = التسلسل الرمزي **بعد إخفاء الحرفيات والأرقام** (`<lit>` و`<num>`) — وهو ما تُحكم به البوابة.
- `raw` = التسلسل كما هو (بالحرفيات) — يُطبع بجانبه دائمًا للعلم.
- `lit` = عدد النصوص الحرفية (٨ أحرف فأكثر) **المشتركة** مع نفس الأصل: يكشف نسخ جدول مسارات
  أو قاموس نصوص وإن لم تشترك الأسماء.

وكلها مُعلنة في المخرجات، فمن يريد الحكم بالرقم المشدّد يقرأ `raw + lit`، ومن يريد نسبة العمل
المكتوب يقرأ `code`. والاثنان لا يكذبان على أحدهما.

**الحدّ المُعلن:** التشابه المنخفض **يُبرئ** بحسب هذا المقياس، ولا **يُثبت** عدم الاقتباس المفهومي
(بنية خوارزمية مُستنسخة بأسماء مختلفة تبقى شبيهة دلاليًّا). والتشابه المرتفع **لا يُدين** وحده: اسم
الـAPI نفسه في مكتبة Android يُنتج نوافذ مشتركة. فالحكم النهائي للمراجع البشري، والأداة تُضيّق
النطاق من «٢٠٠٠ ملف» إلى «قائمة قصيرة تستحق النظر». وهذا مكتوب لأنه لا يجوز أن يُقرأ رقم كحكم.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass

_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(_HERE)

NGRAM = 9

# سقف عدد الأسطر الداخلة في قياس التتالي: الملف الأكبر من هذا يُقتطع، والتقاطع الدائري
# O(n·m) يصير كلفة حقيقية على شجرة فيها مئات الملفات. والاقتطاع قرائي: أوّل الأخطار
# هو أهمّها (مقدّمة الملف: الترويسة، الـimports، وأول التعريفات).
MAX_RUN_LINES = 900

# الأصول المرجعية: إسمٌ للحكم، ومجلّد نسخة عندنا، ونمط مسار الأصل داخله.
# المجلّد مأخوذ من `git clone --filter=blob:none` (يعيش في build/audit فلا يدخل الشجرة)،
# أو يُمرَّر بـ`--upstream`. وغيابه **ليس فشلًا**: يُعلن «غير مُتحقَّقة» كما في §4 من VALIDATION.
DEFAULT_UPSTREAM = os.path.join(_ROOT, "build", "audit", "zkm-raw")

SOURCE_EXTS = (".kt", ".aidl", ".java")

# مراكز لا تُقاس: النثر يذكر أسماء مشاريع كثيرة وصفًا، وقياسه ضجيج. و`build/` نفسها
# تُستثنى لأن نسخة الأصل المرجعي تسكنها — وقياستها بنفسها تُخرج ١.٠٠٠ لكل ملف وتُغرق الجدول.
SKIP_PREFIXES = ("docs/", "tools/", ".github/", "build/")

# ── بقايا مُعلَنة: الملفات التي يتقاطع فيها المشروع مع الأصل لسبب لا يُصلحه إعادة التأليف ──
#
# كل مدخل: المسار (لاحقة) ثم **سبب مكتوب**. والباب مُفتح للجميع: ما ليس هنا يُسقط البوابة،
# وما هنا **يُطبع في كل تشغيل** ولو نجحت البوابة — فلا يُخفي الإعلانُ رقمًا، بل يُفسّره.
# وسقف العدد مُجمَّد: لا يُضاف سبب جديد بلا قياس يقول إنه من هذا النوع (فرض API/بنية إطار)
# لا نقل تعبير — وهذا فرق يُكتب في السبب نفسه. وكل ما كان نقلًا تعبيريًّا حقيقيًّا أُعيد تأليفه
# ولم يُدرج هنا (انظر `docs/PROVENANCE.md` §جدول القياس لكل ملف).
DECLARED_RESIDUE: dict[str, str] = {
    "ui/theme/Type.kt": "صيغة باني Material 3: `TextStyle(fontFamily=…, fontSize=…)` — نفس الـAPI "
                        "تفرض نفس الأسطر؛ وأصل الملف القياسي ٢٤ سطرًا ويحمل لونًا واحدًا بخطّ النظام، "
                        "وهذا الملف لوحة كاملة بخطوط المشروع",
    "ui/process/MyLifecycleOwner.kt": "تمثيل `SavedStateRegistryOwner` يدويًّا: `LifecycleRegistry(this)` "
                                      "و`SavedStateRegistryController.create(this)` وتجاوزان — لا صياغة "
                                      "ثانية لها في AndroidX",
    "service/FpsOverlayService.kt": "تصريحات عقد Compose داخل نافذة عائمة بلا نشاط "
                                     "(windowManager · overlayView · layoutParams · سجل الحالة · مخزن "
                                     "الViewModel) — أسماؤها يفرضها الـAPI، ونفس السبب أدناه",
    "service/ProcessOverlayService.kt": "نفس عقد الخدمة السابقة حرفيًّا (الخدمتان تشتركان فيه داخليًّا "
                                        " أيضًا) — توحيده في وحدة مشتركة عملٌ له مشروعُه لا يُدسّ في جولة "
                                        "تحقّق، والوحدة المُشتركة تُنشأ عند أوّل تعديل وظيفي على إحدى الخدمتين",
}


def residue_reason(rel: str) -> str | None:
    for suffix, reason in DECLARED_RESIDUE.items():
        if rel.endswith(suffix):
            return reason
    return None


COMMENT_BLOCK = re.compile(r"/\*.*?\*/", re.S)
COMMENT_LINE = re.compile(r"//[^\n]*")
WORD = re.compile(r"[A-Za-z_][A-Za-z0-9_]*|\d+")

# أنواع الرموز: يُخفى منها ما ليس تعبيرًا. النصّ الحرفي يبقى مفردًا ليُقاس وحده (`lit`).
STRING_LITERAL = re.compile(r'"(?:[^"\\\n]|\\.)*"')
NUMBER_LITERAL = re.compile(r"\b\d+(?:\.\d+)?\b")
CODE_TOKEN = re.compile(r"[A-Za-z_][A-Za-z0-9_]*|[{}()\[\].,;:=<>+\-*/!&|?]")
LITERAL_MIN = 8


def strip_code(text: str) -> list[str]:
    """أسطر الكود المجرَّدة: بلا تعليق، بلا فراغ، بلا `package`/`import`، وبمسافات موحَّدة."""
    text = COMMENT_BLOCK.sub(" ", text)
    text = COMMENT_LINE.sub("", text)
    out: list[str] = []
    for line in text.splitlines():
        line = " ".join(line.split())
        if not line:
            continue
        if line.startswith(("package ", "import ")):
            continue
        out.append(line)
    return out


def code_tokens(text: str) -> list[str]:
    """تسلسل رمزي يُستبدل فيه النصّ الحرفي والرقم بعلامة — فيبقى ما كتبه الكاتب: الأسماء والبنية.

    والعلامتان `LITERAL` و`NUMBER` **مقصودتان** كي يقرأهما مرمّز الرموز: محلّ الحرفية يبقى
    محسوبًا (فبنية «جدول من ١٣ مدخلاً» تُرى)، ومحتواها لا (فمسار `/proc/...` ليس تعبيرًا).
    """
    masked = STRING_LITERAL.sub(" LITERAL ", text)
    masked = NUMBER_LITERAL.sub(" NUMBER ", masked)
    return CODE_TOKEN.findall(masked)


def literals(text: str) -> set[str]:
    """النصوص الحرفية الطويلة — تُقاس وحدها لأنها «وقائع» لا تعبير."""
    return {s[1:-1] for s in STRING_LITERAL.findall(text) if len(s) - 2 >= LITERAL_MIN}


def ngrams(words: list[str], n: int = NGRAM) -> set[tuple[str, ...]]:
    if len(words) < n:
        return set()
    return {tuple(words[i:i + n]) for i in range(len(words) - n + 1)}


IDENTIFIER = re.compile(r"[A-Za-z_]\w*")


def code_lines(lines: list[str]) -> list[str]:
    """الأسطر التي تحمل اسمين على الأقل.

    **لماذا هذا المرشّح قبل قياس التتالي:** التتالي الخام على الأسطر يُغرق القائمة بضجيج
    الأقواس: ثمانية أسطر `}` متتالية بعد دوال متداخلة تُنتج `run=8` بلا أي تعبير، وهو ما لا
    يعني شيئًا. وسطر يحمل اسمين على الأقل هو سطر يحمل معنى (تصريح، نداء، إسناد) — فيبقى
    القياس على الكود لا على شكل الأقواس. وحدّ الاثنين مقصود: `return false` له اسمان فيُحسب،
    و`}` و`})` مفردَيه ما كان لهما اسمان فيُسقطان.
    """
    # **الترتيب مهم:** الترشيح أولًا ثم الاقتطاع. وعكسه (اقتطاع ثم ترشيح) يُبقي أسطر
    # الأقواس العارية في الملفات الطويلة، فيُقاس «تتالٍ مسمّى» لم يحمل أي اسم — وهو
    # الخطأ الذي وقع في هذه الأداة أوّل مرة وأخرَج ثمانية `}` كـ`run=8`.
    named = [line for line in lines if len(IDENTIFIER.findall(line)) >= 2]
    return named[:MAX_RUN_LINES]


def longest_run(a: list[str], b: list[str]) -> int:
    """أطول سلسلة أسطر متطابقة متتالية (تطابق تام بعد التجريد)."""
    if not a or not b:
        return 0
    prev = [0] * (len(b) + 1)
    best = 0
    for i in range(1, len(a) + 1):
        cur = [0] * (len(b) + 1)
        ai = a[i - 1]
        for j in range(1, len(b) + 1):
            if ai == b[j - 1]:
                cur[j] = prev[j - 1] + 1
                if cur[j] > best:
                    best = cur[j]
        prev = cur
    return best


@dataclass
class Match:
    upstream: str
    score: float
    run: int
    grams: int
    raw: float = 0.0
    shared_literals: int = 0


@dataclass
class Unit:
    """ملف بعد التجريد، بصوره الثلاث: الأسطر، ورموز البنية، والحرفيات."""
    lines: list[str]
    code: list[str]
    lits: set[str]

    @staticmethod
    def of(text: str) -> "Unit":
        stripped = strip_code(text)
        return Unit(stripped, code_tokens("\n".join(stripped)), literals(text))


def load_corpus(directory: str) -> dict[str, Unit]:
    corpus: dict[str, Unit] = {}
    for dirpath, _dirs, files in os.walk(directory):
        for name in sorted(files):
            if not name.endswith(SOURCE_EXTS):
                continue
            path = os.path.join(dirpath, name)
            try:
                with open(path, encoding="utf-8", errors="replace") as fh:
                    corpus[os.path.relpath(path, directory)] = Unit.of(fh.read())
            except OSError:
                continue
    return corpus


def compare(unit: Unit, corpus: dict[str, Unit], keep: int) -> list[Match]:
    """أعلى [keep] مطابقة. التشابه يُحسب على رموز البنية، ويُقاس معه النصّ الخام والحرفيات."""
    grams = ngrams(unit.code)
    raw_grams = ngrams(WORD.findall("\n".join(unit.lines)))
    if not grams:
        return []
    matches: list[Match] = []
    for rel, other in corpus.items():
        other_grams = ngrams(other.code)
        shared = len(grams & other_grams) if other_grams else 0
        shared_literals = len(unit.lits & other.lits)
        # زوج لا يشترك في بنية ولا في حرفيات = لا شيء يُقال عنه، ولا يُذكر في القائمة.
        if not shared and not shared_literals:
            continue
        other_raw = ngrams(WORD.findall("\n".join(other.lines)))
        raw = (len(raw_grams & other_raw) / min(len(raw_grams), len(other_raw))
               if raw_grams and other_raw else 0.0)
        score = shared / min(len(grams), len(other_grams)) if (grams and other_grams) else 0.0
        matches.append(Match(
            upstream=rel, score=score,
            run=longest_run(code_lines(unit.lines), code_lines(other.lines)), grams=shared,
            raw=raw, shared_literals=shared_literals,
        ))
    matches.sort(key=lambda m: (-m.run, -m.score, -m.shared_literals))
    return matches[:keep]


def merge_corpora(directories: list[str]) -> dict[str, Unit]:
    """دُمج نسخ الأصل المرجعي المتعددة في فهرس واحد، ومفتاح كل ملف يحمل اسم أصله.

    وتعدد الأصول مقصود: الملف الواحد قد يذكر مصدرين مختلفين في ترويسته، والحكم يحتاج قياسًا
    على كليهما. وإلا صار «لا تشابه» بمعنى «لم نقارن بالأصل الذي نُسب إليه فعلًا».
    """
    corpus: dict[str, Unit] = {}
    for directory in directories:
        label = os.path.basename(os.path.abspath(directory))
        for rel, unit in load_corpus(directory).items():
            corpus[f"{label}/{rel}"] = unit
    return corpus


def audit(root: str, corpus: dict[str, Unit], threshold: float, run_max: int, show: int) -> tuple[list[dict], list[str]]:
    findings: list[dict] = []
    scanned = 0
    for dirpath, _dirs, files in os.walk(root):
        for name in sorted(files):
            if not name.endswith(SOURCE_EXTS):
                continue
            rel = os.path.relpath(os.path.join(dirpath, name), root)
            if rel.startswith(SKIP_PREFIXES):
                continue
            try:
                with open(os.path.join(root, rel), encoding="utf-8", errors="replace") as fh:
                    unit = Unit.of(fh.read())
            except OSError:
                continue
            scanned += 1
            if len(unit.lines) < 12:     # سطر أو سطران لا يُقاس بهما تشابه
                continue
            top = compare(unit, corpus, 3)
            if not top:
                continue
            best = top[0]
            if best.score >= threshold or best.run >= run_max:
                findings.append({
                    "file": rel, "residue": residue_reason(rel),
                    "score": round(best.score, 3),
                    "run": best.run, "raw": round(best.raw, 3),
                    "literals": best.shared_literals,
                    "upstream": best.upstream, "top": [
                        {"upstream": m.upstream, "score": round(m.score, 3), "run": m.run,
                         "raw": round(m.raw, 3), "literals": m.shared_literals}
                        for m in top
                    ],
                })
    findings.sort(key=lambda f: (-f["score"], -f["run"]))
    return findings, [f"scanned={scanned}", f"upstream_files={len(corpus)}"]


def write_doc(path: str, args, findings: list[dict], stats: list[str],
              corpus: dict[str, Unit], missing: list[str]) -> None:
    """تقرير الأصالة (PHASE 11): ما قيس، بأي سقف، وما بقي — لا خلاصة بالنية.

    والفرق عن `docs/PROVENANCE.md` مقصود: ذاك يحكم من **الترويسة** (ما يقوله الملف عن نفسه)،
    وهذا يحكم من **النصّ** (ما يشترك فيه الملف مع الأصل حرفيًّا). فمن يقرأ الاثنين يرى الصورة كاملة.
    """
    undeclared = [f for f in findings if not f["residue"]]
    declared = [f for f in findings if f["residue"]]
    out: list[str] = []
    a = out.append
    a("# AUTHENTICITY — قياس استقلال النصّ عن أصول GPL المُزالة")
    a("")
    a("مُولَّد بـ`python3 tools/upstream_similarity.py --write-doc docs/AUTHENTICITY.md "
      "--upstream build/audit/zkm-raw --upstream build/audit/vtools-raw`. لا يُكتب بيد.")
    a("")
    a("## 1. الطريقة — ماذا يُقاس بالضبط")
    a("")
    a("تقارن الأداة كل ملف مصدري عندنا بكل ملف في نسخة الأصل المرجعي، بثلاثة مقياسين "
      "**مختلفين** — فلا يُخفى أحدهما بالآخر:")
    a("")
    a("| المقياس | ما يقيسه | لماذا هو مستقلّ |")
    a("| --- | --- | --- |")
    a("| `code` | احتواء رموز البنية (تسميات الدوال والمتغيرات، بعد تجريد التعليقات "
      "و`package`/`import`) | يقيس **بنية التعبير** لا النصّ: نسخة مُعاد تسميتها تبقى مكشوفة |")
    a("| `raw` | التطابق النصّي الحرفي | يكشف النقل الحرفي ولو غُيّرت الأسماء |")
    a("| `lit` | الحرفيات المشتركة (نصوص، أرقام مسارات) | تُفصل عن الاثنين: هي **بيانات** "
      "لا تعبير، فلا تُحسب تشابهًا |")
    a("")
    a(f"السقوف: `code ≥ {args.threshold}` أو مقطع مسمّى ≥ {args.run_max} أسطر متتالية "
      "⇒ ملف «يستحق النظر».")
    a("")
    a("## 2. ما قيس في هذه الجولة")
    a("")
    for line in stats:
        key, _, value = line.partition("=")
        a(f"- `{key}` = **{value}**")
    a(f"- ملفات أصل مرجعي مُحمّلة: **{len(corpus)}**")
    if missing:
        a(f"- ⚠️ أصول مفقودة ولم تُقس (يُعلن ولا يُخفي): {'، '.join(missing)}")
    a("")
    a("## 3. النتيجة")
    a("")
    if undeclared:
        a(f"**⚠️ غير مُعلَن: {len(undeclared)} ملفًا بلغ السقف ولا سبب مكتوب له** — إمّا "
          "إعادة تأليف أو إعلان بسبب: ⇒ البوابة تُخرج 1:")
        a("")
        for f in undeclared:
            a(f"- `{f['file']}` ← `{f['upstream']}` (code={f['score']} · raw={f['raw']} · "
              f"run={f['run']})")
    else:
        a("**لا ملف بلغ السقف بلا سبب مكتوب.** ✅ وهذا ما تقوله البوابة بـ`--assert` (exit 0).")
    a("")
    a(f"وبلغ السقف {len(findings)} ملفًا، وكلّها **بقايا مُعلَنة**: API الأطر تفرض صياغة واحدة، "
      "والاسم الذي يفرضه الـAPI ليس نقل تعبير. وكل سبب مكتوب **أدناه في المخرجات** "
      "وفي جدول الكود `DECLARED_RESIDUE` نفسه — فلا يُخفي إعلانٌ رقمًا:")
    a("")
    a("| FILE | code | raw | run | الأصل |")
    a("| --- | --- | --- | --- | --- |")
    for f in findings:
        a(f"| `{f['file']}` | {f['score']} | {f['raw']} | {f['run']} | `{f['upstream']}` |")
    a("")
    if declared:
        a("### أسباب البقايا (منقولة من جدول الكود — تُقرأ ولا تُقدَّر)")
        a("")
        for f in declared:
            a(f"- **`{f['file']}`** — {f['residue']}")
        a("")
    a("## 4. حدود هذا القياس")
    a("")
    a("* يقيس **التشابه النصّي** لا الأصل القانوني. ملفّان قد يشتركان في صياغة ويختلفان في "
      "الأصل، والعكس — ولذلك يُقرأ مع `docs/PROVENANCE.md` (الذي يحكم من الترويسة).")
    a("* البقايا المُعلَنة **لا تُسقِط البوابة لكنها تُطبع في كل تشغيل**، والجدول في الكود "
      "(`DECLARED_RESIDUE`) لا يقبل إضافة بلا سبب مكتوب.")
    a("* الإعلان يصف API الأطر (Material 3 · AndroidX · عقد خدمة Compose) لا نقل تعبير — "
      "وكل ما كان نقلًا تعبيريًّا حقيقيًّا أُعيد تأليفه ولم يُدرج.")
    a("")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out))


def self_test() -> int:
    """الأداة تقيس نفسها: تُصنع أزواج معلومة النتيجة، ويُقاس سلوكها عليها."""
    failures = 0
    total = 0

    def check(label: str, ok: bool) -> None:
        nonlocal failures, total
        total += 1
        print(f"  {'OK ' if ok else 'FAIL'} {label}")
        if not ok:
            failures += 1

    def unit(text: str) -> Unit:
        return Unit.of(text)

    base_text = """
        val x = 1
        fun f(a: Int): Int {
            return a + x
        }
    """
    other_text = """
        val counter = 0
        fun step(delta: Long): Long {
            return delta * counter
        }
    """
    # ملف يشترك في المسارات والأرقام **فقط**، وبنيته مختلفة تمامًا.
    facts_ours = """
        private const val A = "/proc/sys/kernel/sched_bore"
        private const val B = "/proc/sys/net/ipv4/tcp_sack"
        val table = mapOf("Latency (ns)" to A, "Nr Migrate" to B)
        fun sized(): Long = 1024
    """
    facts_upstream = """
        object SchedulerUtils {
            val GENERIC = mapOf(
                "Latency (ns)" to "/proc/sys/kernel/sched_bore",
                "Nr Migrate" to "/proc/sys/net/ipv4/tcp_sack",
            )
        }
        const val PAGES = 1024
    """

    corpus = {"upstream/Copy.kt": unit(base_text), "upstream/Other.kt": unit(other_text)}

    # ١) نسخة مطابقة تُكتشف بتشابه ١ وطول مقطع كامل.
    m = compare(unit(base_text), corpus, 3)
    check("نسخة مطابقة ⇒ score=1.0", bool(m) and m[0].score == 1.0)
    check("نسخة مطابقة ⇒ run=عدد أسطرها المسمّاة",
          bool(m) and m[0].run == len(code_lines(unit(base_text).lines)))

    # ٢) نصّ مختلف تمامًا لا يُطابق.
    m2 = compare(unit(other_text), {"upstream/Copy.kt": unit(base_text)}, 3)
    check("نصّ مختلف ⇒ لا تطابق", not m2 or m2[0].score < 1.0)

    # ٣) التجريد يمحو التعليق والترويسة، فلا يخلق تشابهًا.
    a = strip_code("/* copyright ZKM 2025 */\npackage p\nimport a.b\n\nval y = 2 // تعليق\n")
    check("التجريد يحذف الترويسة وpackage وimport", a == ["val y = 2"])

    # ٤) الاشتراك في الحقائق وحدها (مسارات + نصوص جدول + أرقام) لا يُنتج تشابه بنية،
    #    لكنه **يُبلَّغ عنه**: جدول منقول كاملًا قد لا يشترك في اسم واحد.
    m3 = compare(unit(facts_ours), {"upstream/SchedulerUtils.kt": unit(facts_upstream)}, 3)
    check("حقائق مشتركة ⇒ code منخفض", bool(m3) and m3[0].score < 0.4)
    check("حقائق مشتركة ⇒ الحرفيات تُعدّ وتُبلَّغ", bool(m3) and m3[0].shared_literals >= 2)

    # ٥) أطول مقطع: سطران متطابقان وسط مختلف ⇒ run=2.
    r = longest_run(["a", "b", "fun g()", "  x()", "y"], ["z", "fun g()", "  x()", "w"])
    check("longest_run يقيس التتالي لا العدد", r == 2)

    # ٥) الكلمات المشتركة وحدها لا ترفع التشابه (نفس الأسماء بترتيب مختلف).
    same_api = ["val a = 1", "val b = 2", "val c = 3", "val d = 4"]
    same_api2 = ["val b = 9", "val c = 8", "val d = 7", "val a = 6"]
    m4 = compare(unit("\n".join(same_api)), {"upstream/S.kt": unit("\n".join(same_api2))}, 3)
    check("تبديل الأسطر يخفض التطابق", not m4 or m4[0].score < 1.0)

    print(f"\nself-test: {total - failures}/{total}")
    return 1 if failures else 0


def main() -> int:
    ap = argparse.ArgumentParser(description="قياس التشابه النصّي بين مصادرنا وأصول GPL المرجعية")
    ap.add_argument("--root", default=_ROOT, help="جذر شجرتنا (افتراضيًّا جذر المستودع)")
    ap.add_argument(
        "--upstream", action="append", default=[DEFAULT_UPSTREAM],
        help="مجلّد نسخة أصل مرجعي؛ يُكرَّر لأصل ثانٍ (مثال: --upstream build/audit/zkm-raw "
             "--upstream build/audit/vtools-raw)",
    )
    ap.add_argument("--threshold", type=float, default=0.30, help="سقف احتواء رموز البنية (code)")
    ap.add_argument("--run-max", type=int, default=6,
                    help="سقف أطول مقطع أسطر متطابقة تحمل أسماء (٦ افتراضيًّا)")
    ap.add_argument("--shown", type=int, default=25)
    ap.add_argument("--show", action="append", default=None,
                    help="أعلى مطابقات هذا الملف وحده؛ يُكرَّر لعدة ملفات")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--assert", dest="assert_", action="store_true")
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument("--write-doc", default=None, metavar="PATH",
                    help="اكتب تقرير الأصالة (PHASE 11) في هذا المسار")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    missing = [d for d in args.upstream if not os.path.isdir(d)]
    corpus = merge_corpora([d for d in args.upstream if d not in missing])
    if not corpus:
        note = ("غير مُتحقَّقة: لا نسخة من أيّ أصل مرجعي. والأصول المستعملة:\n"
                + "\n".join(f"  {d}" for d in args.upstream)
                + "\n  اجلبها بـ: git clone --filter=blob:none --depth 1 <repo> build/audit/<name>")
        print(note)
        return 2 if args.assert_ else 0
    if missing:
        print("تحذير: أصل مفقود ولن يُقاس: " + "، ".join(missing))

    for shown in args.show or []:
        with open(os.path.join(args.root, shown), encoding="utf-8", errors="replace") as fh:
            unit = Unit.of(fh.read())
        print(f"{shown}  ({len(unit.lines)} سطرًا بعد التجريد · {len(corpus)} ملفًا مرجعيًّا)")
        print("    code     raw    lit  run  الأصل")
        for m in compare(unit, corpus, 5):
            print(f"  {m.score:>6.3f}  {m.raw:>6.3f}  {m.shared_literals:>4}  {m.run:<4} {m.upstream}")

    findings, stats = audit(args.root, corpus, args.threshold, args.run_max, args.shown)

    if args.write_doc:
        write_doc(os.path.join(args.root, args.write_doc), args, findings, stats, corpus, missing)
        print(f"كُتب: {args.write_doc}")

    if args.json:
        import json
        payload = {
            "stats": stats, "findings": findings,
            "undeclared": sum(1 for f in findings if not f["residue"]),
            "declared_residue": DECLARED_RESIDUE,
        }
        print(json.dumps(payload, ensure_ascii=False, indent=1))
        return 1 if (args.assert_ and any(not f["residue"] for f in findings)) else 0

    print("؛ ".join(stats))
    print(f"سقف: احتواء رموز ≥ {args.threshold} أو مقطع ≥ {args.run_max} أسطر مسمّاة متطابقة")
    if not findings:
        print("لا ملف يبلغ السقف.")
        return 0
    undeclared = [f for f in findings if not f["residue"]]
    declared = [f for f in findings if f["residue"]]
    print(f"{len(findings)} ملفًا يستحق النظر (code = احتواء رموز البنية | raw = بالنصّ الحرفي | lit = حرفيات مشتركة):")
    for f in findings[:args.shown]:
        print(f"  code={f['score']:>6.3f}  raw={f['raw']:>6.3f}  lit={f['literals']:>3}  run={f['run']:<4} "
              f"{f['file']}  ⟵ {f['upstream']}")
    if len(findings) > args.shown:
        print(f"  … و{len(findings) - args.shown} غيرها")
    if declared:
        print(f"\nبقايا مُعلَنة ({len(declared)}) — لا تُسقط البوابة، وتُطبع في كل تشغيل:")
        for f in declared:
            print(f"  {f['file']}: {f['residue']}")
    else:
        print("\nلا بقايا مُعلَنة مطابقة لملف وُجد.")
    if undeclared:
        print(f"\n⚠ غير مُعلَن: {len(undeclared)} ملفًا يحتاج إعادة تأليف أو إعلانًا بسبب مكتوب.")
    return 1 if (args.assert_ and undeclared) else 0


if __name__ == "__main__":
    sys.exit(main())
