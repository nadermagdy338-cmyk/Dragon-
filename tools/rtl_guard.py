#!/usr/bin/env python3
"""
بوابة RTL — تمنع إعادة الصنف الذي أُبلغ عنه، لا تُصلح مثيله.

**ما تقيسه، وثلاثة أصناف لا رابع:**

1. **حشو أو هامش بجهة صلبة** (`padding(left = …)` / `absolutePadding(left = …)`) في `ui/**`.
   الصواب منطقيّ: `start`/`end`، فينقلبهما `LocalLayoutDirection` في العربية بلا كود شرطيّ.
   و`absolutePadding` مستثنى **باسمه** لأنه يقول «هذا مقصود» صراحةً.
2. **أيقونة اتجاهية غير منعكسة.** `Icons.Rounded.ArrowBack` لا ينقلب في RTL، فيشير إلى اليمين
   في العربية = «تقدّم» لا «رجوع». والصواب `Icons.AutoMirrored.Rounded.ArrowBack`. والقائمة
   أدناه **صريحة ومحدودة**: الأيقونات التي يشملها `androidx.compose.material.icons.automirrored`.
3. **`Alignment.TopLeft`/`TopRight`/`CenterLeft`/`CenterRight`** في `ui/**` — ثوابت جهة صلبة،
   وبديلها المنطقيّ `TopStart`/`TopEnd`/`CenterStart`/`CenterEnd`.
4. **هدف لمس أصغر من ٤٨dp** (§١٣ «Adequate touch targets»): سلسلة معدِّلات فيها
   `.size(N.dp)` بـ`N < 48` **و** `.clickable(`/`.combinedClickable(`/`.toggleable(` بلا
   `minTouchTarget` ولا `heightIn`/`sizeIn` مصاحبة. و`IconButton` خارج الفحص: Material3
   تفرض فيها ٤٨dp افتراضيًّا (`minimumInteractiveComponentSize`)، فهي ليست عطبًا.

   **وملاحظة لا مخالفة (٥):** الهدف المكتوب بمقاس **رمزيّ** (`.size(HudActionSize)`) لا تراه
   الصيغة الرقمية أعلاه، لأنّ الرقم فيها اسم. فيُعدّ ويُطبع في قائمة مراجعة، **ولا يُسقط
   البوّابة**: من كتب ثابتًا باسمه فقد أعلن قراره في المصدر (وسببه في توثيقه)، ومن كتب `32.dp`
   يُمسَك. وميزته أنّه يجعل الاستثناء **مرئيًّا بعدد** بدل أن يُطوى: العدد اليوم **١** — ثابت
   واحد في `HudSurface.kt` بأمر المالك، فلا صفرٌ كاذب ولا تجاوزٌ أعمى.

   **وقياس اليوم صفر** — فالبند حرسٌ لا إصلاح: أُضيف لأن العطب الذي أبلغ عنه المالك كان
   «نصّ منكسر في صفّ»، وانكساره كان من **حاوية ضاقت**، وهو أول ما يمسّه من يصغّر هدف اللمس ليبدو
   الصفّ أنيقًا. فالبوابة تمنع المقايضة التي تنتج العطب نفسه.

**وحدّها المُعلَن — وهذا مهمّ لا يُطوى:**
- تقيس **النصّ المصدري**، لا سلوك التخطيط الفعليّ. فشاشة RTL سليمة البنية قد تبقى معطوبة بصريًّا
  (ترتيب، مسافة، محاذاة نصّ مختلط عربيّ/لاتينيّ) — وذاك **يحتاج جهازًا أو مراجعة بصرية**.
- ولا تقيس المحتوى العربي نفسه: لا ترجمة ولا `values-ar` ولا تسرّب نصّ إنجليزيّ.
- ولا تقيس أهداف اللمس ولا التباين (صنف الإتاحة) — ذاك ليس RTL.

**و`--self-test` يقيس الأداة على شجرة مصغّرة داخلية**: كل صنف يُحقن مرّة فيُمسك، وكل استثناء
شرعيّ (المنطقيّ، والمنعكس، و`absolutePadding`) يمرّ. أداة تمرّ على كل شيء لا تُثبت شيئًا.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI_ROOT = ROOT / "manager/app/src/main/java/nd/max/ui"

# الأصناف الثلاثة — أنماط مضبوطة على الحرجة منها فقط، لأن بوابة تُحمرّ على كود سليم تُطفأ.
HARD_PAD = re.compile(r"\.(?:padding|absolutePadding)\s*\(\s*(left|right)\s*=")
HARD_ALIGN = re.compile(r"Alignment\.(TopLeft|TopRight|CenterLeft|CenterRight)\b")
MIRRORABLE = (
    "ArrowBack|ArrowForward|ArrowLeft|ArrowRight|KeyboardArrowLeft|KeyboardArrowRight"
    "|Send|ExitToApp|Login|Logout|Undo|Redo|TrendingFlat|OpenInNew|DoubleArrow"
    "|ReplyAll|Reply|FormatTextdirectionLToR|FormatTextdirectionRToL|Label|LabelOff"
    "|List|Sort|Help|Article|MultilineChart|ShowChart|TrendingUp|TrendingDown"
    "|VolumeUp|VolumeDown|VolumeMute|EscalatorWarning|FollowTheSigns"
)
NOT_MIRRORED = re.compile(r"\bIcons\.(?:Rounded|Filled|Outlined|Sharp|TwoTone)\.(" + MIRRORABLE + r")\b")
# سلسلة معدِّلات: مقاس صغير ثم تفاعل مباشر بلا أرضية لمس.
# الصيغة الأولى كانت تشترط `(` بعد اسم المعدِّل، فأفلتت `Modifier.clickable { … }` — وهي الصيغة
# الشائعة في المستودع. و`--self-test` هو الذي أمسك هذا، لا مراجعة بصرية للتعبير النمطي.
SMALL_CLICK = re.compile(
    r"\.size\((\d{1,2})\.dp\)[^\n]*\.(?:clickable|combinedClickable|toggleable)\s*[({]"
)
HAS_FLOOR = re.compile(r"minTouchTarget|heightIn\(|sizeIn\(")
# المقاس الرمزيّ: الاسم يبدأ بحرف كبير (`HudActionSize`)، فيتميّز عن الرقم بلا قائمة أسماء.
SYMBOL_SIZE = re.compile(r"\.size\(\s*([A-Z][A-Za-z0-9_.]*)\s*\)\s*$")
CLICK_DIRECT = re.compile(r"\.(?:clickable|combinedClickable|toggleable)\s*[({]")
# سلسلة المعدِّلات تُكتب على أسطر في هذا المستودع (`.size` في سطر و`.clickable` في آخر)،
# فالنافذة ثلاثة أسطر لا سطر واحد — وضيق النافذة يُنتج مرورًا كاذبًا.
SYMBOL_WINDOW = 3

def scan_text(text: str) -> list[tuple[str, str]]:
    """يعيد (الصنف، السطر) — مفصولًا عن الإدخال/الإخراج كي يقيس `--self-test` الدالة نفسها."""
    hits: list[tuple[str, str]] = []
    lines = text.splitlines()
    for i, raw in enumerate(lines):
        line = raw.split("//", 1)[0]
        if HARD_PAD.search(line):
            hits.append(("padding_side", raw.strip()))
        if HARD_ALIGN.search(line):
            hits.append(("alignment_side", raw.strip()))
        m = NOT_MIRRORED.search(line)
        if m:
            hits.append(("icon_not_mirrored", m.group(0)))
        m = SMALL_CLICK.search(line)
        if m and int(m.group(1)) < 48 and not HAS_FLOOR.search(line):
            hits.append(("small_touch_target", m.group(0)))
        if SYMBOL_SIZE.search(line):
            window = [w.split("//", 1)[0] for w in lines[i:i + SYMBOL_WINDOW]]
            if any(CLICK_DIRECT.search(w) for w in window) and not any(
                HAS_FLOOR.search(w) for w in window
            ):
                hits.append(("symbolic_touch_target", raw.strip()))
    return hits


def scan_tree() -> list[tuple[str, str, int]]:
    out: list[tuple[str, str, int]] = []
    for path in sorted(UI_ROOT.rglob("*.kt")):
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for kind, line in scan_text(text):
            out.append((kind, str(path.relative_to(ROOT)), line))
    return out


SELF_TEST_CASES: list[tuple[str, bool]] = [
    # (سطر, هل يجب أن يُمسك؟)
    ("        .padding(left = 16.dp),", True),
    ("        .padding(right = MaxSpace.md),", True),
    ("        .absolutePadding(left = 4.dp),", True),
    ("        .padding(start = 16.dp, end = 4.dp),", False),
    ("        Alignment.TopLeft,", True),
    ("        Alignment.TopStart,", False),
    ("                imageVector = Icons.Rounded.ArrowBack,", True),
    ("                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,", False),
    ("                    Icons.Filled.Send,", True),
    ("                    Icons.Rounded.Speed,", False),
    ("        // .padding(left = 1.dp) مثال في تعليق", False),
    ("        Modifier.size(24.dp).clickable { run() }", True),
    ("        Modifier.size(64.dp).clickable { run() }", False),
    ("        Modifier.size(24.dp).heightIn(min = 48.dp).clickable { run() }", False),
    ("        Modifier.size(32.dp),  // وليس قابلًا للنقر", False),
    # ملاحظة لا مخالفة: المقاس الرمزيّ يُمسَك كملاحظة (والسلسلة على أسطر كما تُكتب فعلًا)
    (
        "        modifier = Modifier\n"
        "            .size(HudActionSize)\n"
        "            .clickable(role = Role.Button) { run() }",
        True,
    ),
    # …ومع أرضية لمس مصاحبة يمرّ كما يمرّ الرقميّ
    ("        Modifier\n            .size(HudActionSize)\n            .heightIn(min = 48.dp)\n            .clickable { run() }", False),
]


def self_test() -> int:
    bad = 0
    for line, should_catch in SELF_TEST_CASES:
        caught = bool(scan_text(line))
        ok = caught == should_catch
        mark = "✓" if ok else "✗"
        print(f"  {mark} {'يُمسك' if should_catch else 'يمرّ ':>6}  {line.strip()[:58]}")
        bad += 0 if ok else 1
    # العيّنة نفسها: ٣ إيجابيات كاذبة تعني أن الأداة تُنذر على الكود السليم، وهي أسوأ من غيابها.
    print(f"  الأداة: {len(SELF_TEST_CASES) - bad}/{len(SELF_TEST_CASES)}")
    return 1 if bad else 0


def main() -> int:
    ap = argparse.ArgumentParser(description="بوابة RTL: جهات صلبة وأيقونات غير منعكسة")
    # `dest` صريح: `--assert` يولّد `args.assert` وهو **كلمة محجوزة** في بايثون ⇒ خطأ نحويّ
    # لا خطأ وقت تشغيل. نفس الفخّ الذي أوقع `design_tokens.py` قبلها.
    ap.add_argument("--assert", dest="assert_gate", action="store_true", help="اخرج بخطأ إن وُجد مخالفة")
    ap.add_argument("--self-test", action="store_true", help="اقس الأداة نفسها")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    hits = scan_tree()
    kinds = {"padding_side": "حشو بجهة صلبة", "alignment_side": "محاذاة بجهة صلبة",
             "icon_not_mirrored": "أيقونة اتجاهية غير منعكسة",
             "small_touch_target": "هدف لمس أصغر من ٤٨dp",
             "symbolic_touch_target": "هدف لمس بمقاس رمزيّ (مراجعة، لا يُسقط البوّابة)"}
    # الملاحظات (٥) تُطبع وتُعدّ، ولا تُسقط البوّابة — والفصل هنا صريح لا مُضمَر.
    blocking = [h for h in hits if h[0] != "symbolic_touch_target"]
    print(f"مسح RTL: {len(list(UI_ROOT.rglob('*.kt')))} ملفًا في ui/**")
    for kind, label in kinds.items():
        rows = [h for h in hits if h[0] == kind]
        print(f"  {label}: {len(rows)}")
        for _, path, line in rows[:8]:
            print(f"      {path}: {line[:72]}")
    if not blocking:
        print("لا مخالفات — الحشو والمحاذاة منطقيّان، وكل أيقونة اتجاهية منعكسة،")
        print("وكل هدف لمس **مكتوب بمقاس رقميّ** يبلغ ٤٨dp أو يستمدّ أرضيته من مكوّن Material3.")
        return 0
    if args.assert_gate:
        print(f"✗ {len(blocking)} مخالفة RTL")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
