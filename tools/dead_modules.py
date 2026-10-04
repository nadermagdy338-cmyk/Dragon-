#!/usr/bin/env python3
"""جرد الوحدات الميتة — `tools/dead_modules.py`

**ما يقيسه:** ملفّ مصدر **موجود على القرص ولا يدخل أي بناء**:

* **Rust:** ملفّ `.rs` **خارج شجرة الوحدات** — لا `mod <name>;` (ولا `#[path]`) في أي وحدة موصولة
  من `lib.rs`/`main.rs`. وهذا صنف لا يراه المُصرّف: الملف غير المُعلَن **لا يُصرَّف أصلًا**، فيبقى
  يتعفّن بلا خطأ ولا تحذير (والدليل المقيس: `thermalcore/src/prediction.rs` يقرأ سبعة حقول
  لا وجود لها في `ThermalEvent` — ولو وُصل لَما تُرجم).
* **C:** وحدة `.c` **تُبنى فعلًا** (بويلدكارد `Android.mk`: `src/*/*.c` + `Main.c`) لكن **لا أحد
  خارجها يشير إلى أي تعريف فيها** — لا دالّة ولا بيانات. فالكود يُصرَّف ويُربط ويُشحن بلا مستدعٍ.

**ما لا يقيسه (حدّ مُعلَن لا مطويّ):** هذا تحليل نصّي لا مُصرّف ولا رابط.
* «غير مُستدعاة» لا تعني «لا تُنفَّذ»: نداء عبر مؤشر دالّة، أو جدول توزيع، أو `extern` خارج المستودع
  لا يراه هذا الفحص. فالحكم على ملفّ **يحتاج قراءة** قبل أي حذف.
* الـ`static` داخل الملف لا تُعدّ تعريفًا ملفًّا (وهو صحيح: لا تُصدَّر).
* والـC يُفحص في نطاق واحد معلَن أدناه (الخادم)، لا في المستودع كله.

**الوحدات المُعلَّقة-غير-المُستدعاة ليست ميتة:** الفرق مقصود — الملف الذي يُصرَّف داخل الوحدة
وإن لم ينادِه أحد **حيّ** بهذا التعريف (يُبنى ويُقاس)، والميت هو **ما لا يُبنى إطلاقًا** (Rust)
أو **يُبنى بلا مُستخدِم خارجي** (C).
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
import tempfile

# ───────────────────────────── النطاق (مُعلَن لا مُفترَض) ─────────────────────────────

RUST_CRATES = [
    "thermalcore",
    "binutils",
    "binprofiles",
    "manager/src/main/rust",
]
# جذور C: بويلدكارد `Android.mk` = `Main.c` + `src/*/*.c` (مقيس: ٣٤ ملفًّا)
C_ROOT = "archdaemon/jni"
C_UNITS_GLOB = "src/*/*.c"

# ───────────────────────────── الأساس المقيس (يُحدَّث بقرار لا بانزلاق) ─────────────────────────────
#
# كل صفّ هنا **مقيس** ومُعلَن بسببه. و`--assert` يسقط عند: وحدة ميتة جديدة، **أو** صفّ أساس لم
# يعد ميتًا (فإزالة ملف أو وصله تستلزم تحديث الأساس صراحةً — لا أن يمرّ التغيير بصمت).

RUST_DEAD_BASELINE = {
    "thermalcore/src/prediction.rs": (
        "خارج شجرة الوحدات (لا `mod prediction` في lib.rs) — ولو وُصل لَما تُرجم: يقرأ سبعة حقول "
        "لا وجود لها في `ThermalEvent`. مُسجَّل لا مُنظَّف ولا مُوصَّل (ADR-18): إصلاحه قرار سلوك."
    ),
}

C_DEAD_BASELINE: dict[str, str] = {}  # مقيس: صفر — لا وحدة C مبنية بلا مُستخدِم خارجي

# ما ليس ميتًا لكنه **لا يُبنى في أي مُخرَج نُنتجه** — يُقاس ويُعلَن، ولا يُسقط البناء (اختياريّ بتصميم).
INERT_DECLARED = {
    "thermalcore/src/simulator.rs": (
        'يُوصَل بـ`#[cfg(feature = "simulator")]`، والخاصيّة مُعلَنة في `Cargo.toml` '
        "**ولا باني في المستودع يُفعّلها** (مقيس: لا ذكر لها في `.github/` ولا `android/` ولا `mainfiles/`) "
        "⇒ لا تدخل أي حزمة نشحنها. ولم تُحذف: هي أداة مطوّر اختيارية، ومقيس أنها **تُصرَّف** بـ"
        "`cargo check --features simulator` (بلا خطأ، بتحذير استيراد غير مستعمل واحد)."
    ),
}

# ───────────────────────────── الكشف ─────────────────────────────

MOD_DECL = re.compile(r'(?:#\[path\s*=\s*"([^"]+)"\]\s*)?(?:pub\s+)?mod\s+([A-Za-z_]\w*)\s*;')


def rust_reachable(root: pathlib.Path) -> tuple[set[pathlib.Path], list[pathlib.Path]]:
    src = root / "src"
    if not src.is_dir():
        return set(), []
    files = sorted(src.rglob("*.rs"))
    roots = [p for p in files if p.name in ("lib.rs", "main.rs")] or [p for p in files if p.parent == src]
    reach: set[pathlib.Path] = set()
    frontier = list(roots)
    while frontier:
        f = frontier.pop()
        if f in reach:
            continue
        reach.add(f)
        for m in MOD_DECL.finditer(f.read_text(errors="replace")):
            base = (f.parent / m.group(1)) if m.group(1) else (f.parent / m.group(2))
            for cand in (base.with_suffix(".rs"), base / "mod.rs"):
                if cand.is_file():
                    frontier.append(cand)
    return reach, files


def rust_dead(repo: pathlib.Path) -> list[pathlib.Path]:
    out: list[pathlib.Path] = []
    for crate in RUST_CRATES:
        reach, files = rust_reachable(repo / crate)
        out += [p for p in files if p not in reach]
    return sorted(out)


def c_defs(text: str) -> set[str]:
    """تعريفات على **مستوى الملف** وغير ساكنة: دوال وبيانات.

    الشرط الحاسم: السطر يبدأ عند العمود 0. بغيره تُقرأ المتغيّرات المحلّية تعريفاتٍ ملفّية —
    وهو عطب حقيقي وقع في أول نسخة من هذه الأداة (أعلنت `preloadbin` ميتًا لعشرين رمزًا محليًّا)،
    فصار الحرس على العمود جزءًا من التعريف لا تحسينًا عليه.
    """
    names: set[str] = set()
    for raw in text.splitlines():
        if not raw or raw[0].isspace():
            continue
        s = raw.rstrip()
        if s.startswith(("//", "/*", "*", "#", "typedef", "struct", "enum", "union", "static", "}", "extern")):
            continue
        m = re.match(r"^[A-Za-z_][A-Za-z0-9_ \t\*]*?\b([A-Za-z_]\w*)\s*\([^;{]*\)\s*\{", s)
        if m:
            names.add(m.group(1))
            continue
        m = re.match(r"^(?:const\s+)?[A-Za-z_][A-Za-z0-9_ \t\*]*?\b([A-Za-z_]\w*)\s*(?:\[[^\]]*\]\s*)?=", s)
        if m and "(" not in s.split("=")[0]:
            names.add(m.group(1))
    return names - {"main"}


def c_dead(repo: pathlib.Path) -> list[pathlib.Path]:
    jni = repo / C_ROOT
    units = sorted(jni.glob(C_UNITS_GLOB)) + [jni / "Main.c"]
    headers = "\n".join(p.read_text(errors="replace") for p in (jni / "include").rglob("*.h"))
    texts = {p: p.read_text(errors="replace") for p in units if p.is_file()}
    out: list[pathlib.Path] = []
    for f, text in texts.items():
        if f.name.lower() == "main.c":
            continue  # الجذر
        syms = c_defs(text)
        if not syms:
            continue  # بلا تصدير — لا يمكن الحكم (مُعلَن في الحدّ)
        alive = any(
            re.search(r"\b" + re.escape(s) + r"\b", t)
            for s in syms
            for g, t in texts.items()
            if g != f
        ) or any(re.search(r"\b" + re.escape(s) + r"\b", headers) for s in syms)
        if not alive:
            out.append(f)
    return sorted(out)


# ───────────────────────────── قياس الأداة نفسها ─────────────────────────────

def self_test() -> int:
    """خمس حالات Rust وخمس حالات C معلومة النتيجة. أداة لا تُسقط شيئًا لا تُثبت شيئًا."""
    failures: list[str] = []

    def rust_case(declare: bool, expect: list[str]) -> None:
        with tempfile.TemporaryDirectory() as t:
            t = pathlib.Path(t)
            (t / "src").mkdir()
            (t / "src/nested").mkdir()
            (t / "src/lib.rs").write_text("mod a;\nmod b;\n" + ("mod nested;\n" if declare else ""))
            for n in ("a", "b"):
                (t / f"src/{n}.rs").write_text(f"pub fn f{n}() {{}}\n")
            (t / "src/orphan.rs").write_text("pub fn never() {}\n")
            (t / "src/nested/mod.rs").write_text("pub fn h() {}\n")
            reach, files = rust_reachable(t)
            got = sorted(str(p.relative_to(t / "src")) for p in files if p not in reach)
            if got != expect:
                failures.append(f"Rust: expected {expect}, got {got}")

    rust_case(True, ["orphan.rs"])  # وحدة معلّقة تُكشف · والمُعلَّقة-غير-المُستدعاة لا
    rust_case(False, ["nested/mod.rs", "orphan.rs"])  # إعلان ناقص ⇒ تُكشف

    def c_case(fixture: dict[str, str], expect: list[str], root: str = "int main(void){ return 0; }\n") -> None:
        with tempfile.TemporaryDirectory() as t:
            t = pathlib.Path(t)
            (t / "src/x").mkdir(parents=True)
            for name, body in fixture.items():
                (t / "src/x" / name).write_text(body)
            (t / "Main.c").write_text(root)
            units = sorted(t.glob("src/*/*.c")) + [t / "Main.c"]
            texts = {p: p.read_text() for p in units}
            got = []
            for f, text in texts.items():
                if f.name.lower() == "main.c":
                    continue
                syms = c_defs(text)
                if not syms:
                    continue
                if not any(
                    re.search(r"\b" + re.escape(s) + r"\b", tt) for s in syms for g, tt in texts.items() if g != f
                ):
                    got.append(f.name)
            if sorted(got) != expect:
                failures.append(f"C: expected {expect}, got {sorted(got)}")

    c_case(
        {
            "live.c": "int f(void){ return used_size; }\nconst int used_size = 1;\n",
            "dead_fn.c": "int nobody_calls_it(int x){ return x; }\n",
            "dead_data.c": "const int never_read[] = {7};\n",
            "local_only.c": "int wrapper(void){\n    int local_scratch = 3;\n    return local_scratch;\n}\n",
        },
        ["dead_data.c", "dead_fn.c", "local_only.c"],
        root="int f(void);\nint main(void){ return f(); }\n",  # الجذر يُستثنى بالاسم، ولذلك يحيا live.c
    )
    # والرجل الذي وقع فعلًا: ملفّ بيانات مُستخدَم يجب ألّا يُعدّ ميتًا
    c_case(
        {"data.c": "const int used_size = 1;\n", "user.c": "int g(void){ return used_size; }\n"},
        [],
        root="int g(void);\nint main(void){ return g(); }\n",
    )
    # والمتغيّر المحلّي **لا** يُقرأ تعريفًا ملفًّا
    if "local_scratch" in c_defs("int w(void){\n    int local_scratch = 3;\n    return local_scratch;\n}\n"):
        failures.append("C: an indented local variable was read as a file-scope definition")

    if failures:
        print("❌ قياس الأداة نفسه سقط:")
        for f in failures:
            print("   ·", f)
        return 1
    print("✓ self-test: ٥ حالات Rust/C + حالة المتغيّر المحلّي — الأداة تُسقط ما يجب وتَمرّ بما يجب")
    return 0


# ───────────────────────────── التشغيل ─────────────────────────────

def repo_root() -> pathlib.Path:
    here = pathlib.Path(__file__).resolve()
    for p in [here.parent.parent, *here.parents]:
        if (p / "AGENTS.md").is_file() and (p / "archdaemon").is_dir():
            return p
    return here.parent.parent


def main() -> int:
    ap = argparse.ArgumentParser(description="جرد الوحدات الميتة (Rust خارج الشجرة · C بلا مُستخدِم)")
    # `dest="assert_"` لأن `assert` كلمة محجوزة في Python — والخِيار يبقى `--assert` كما في بقية البوابات.
    ap.add_argument("--assert", dest="assert_", action="store_true", help="يسقط عند وحدة ميتة جديدة أو أساسٍ لم يعد ميتًا")
    ap.add_argument("--self-test", action="store_true", help="يقيس الأداة على شجرة مصغّرة معلومة النتيجة")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    repo = repo_root()
    rust = [str(p.relative_to(repo)) for p in rust_dead(repo)]
    cunits = [str(p.relative_to(repo)) for p in c_dead(repo)]
    inert = [k for k in INERT_DECLARED if (repo / k).is_file()]

    if args.json:
        print(json.dumps(
            {"rust_dead": rust, "c_dead": cunits, "inert": inert,
             "baseline_rust": sorted(RUST_DEAD_BASELINE), "baseline_c": sorted(C_DEAD_BASELINE)},
            ensure_ascii=False, indent=2))
        return 0

    print(f"Rust: {len(RUST_CRATES)} حزمة · ميت {len(rust)}")
    for r in rust:
        print(f"   ✗ {r}")
    print(f"C (الخادم): ميت {len(cunits)}")
    for c in cunits:
        print(f"   ✗ {c}")
    print(f"اختياريّ غير مُفعَّل (مُعلَن، لا يُسقط): {len(inert)}")
    for i in inert:
        print(f"   · {i}")

    if not args.assert_:
        return 0

    problems: list[str] = []
    for r in rust:
        if r not in RUST_DEAD_BASELINE:
            problems.append(f"وحدة Rust ميتة جديدة: {r} — إمّا تُوصَل ب`mod` أو تُسجَّل في الأساس بقرار")
    for c in cunits:
        if c not in C_DEAD_BASELINE:
            problems.append(f"وحدة C مبنية بلا مُستخدِم خارجي: {c} — إمّا تُحذف أو تُسجَّل في الأساس بقرار")
    for r in RUST_DEAD_BASELINE:
        if r not in rust:
            problems.append(f"الأساس يقول إن {r} ميت ولم يعد كذلك — حدِّث الأساس صراحةً")
    for c in C_DEAD_BASELINE:
        if c not in cunits:
            problems.append(f"الأساس يقول إن {c} ميت ولم يعد كذلك — حدِّث الأساس صراحةً")

    if problems:
        print("\n❌ بوابة الوحدات الميتة: " + str(len(problems)) + " نتيجة")
        for p in problems:
            print("   ·", p)
        return 1
    print("\n✓ لا وحدة ميتة خارج الأساس المُعلَن")
    return 0


if __name__ == "__main__":
    sys.exit(main())
