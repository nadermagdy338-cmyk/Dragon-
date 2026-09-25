#!/usr/bin/env python3
"""رموز JNI: كل `native`/`external fun` يجب أن يقابله رمز مُصدَّر فعلًا — وقياسٌ بلا جهاز.

لماذا وُجد
----------
`external fun nativeReadManyPacked(...)` في Kotlin ورمزٌ غائب في `.so` لا يُنتج خطأ ترجمة،
ولا يمسكه `kt_balance` (البنية سليمة) ولا `code_health` (لا كتابة sysfs ولا نصّ صلب) — بل
`UnsatisfiedLinkError` **عند أول نداء على الجهاز**، أي في المكان الوحيد الذي لا نقيس فيه.
وقد وقع هذا الصنف من العطب فعلًا في هذا المستودع: كانت `libtermux.so` تُشحن ٦٤-بت وحدها،
فيسقط `System.loadLibrary("termux")` على هاتف ٣٢-بت بلا أن يقول أيُّ فحص شيئًا.

فالطبقات ثلاث، ولكل طبقة حدّها المعلن:

1. **المصدر (تعمل دائمًا، بلا مُصرّف)** — كل تصريح `native` في Kotlin/Java يُقابَل بالدوال
   المصدرة في `Java_*` داخل Rust. تكشف انصراف الاسم بين اللغتين في ثوانٍ.
2. **الثنائيات (حين تُوجد)** — `nm -D --defined-only` على كل `.so` مُشحونة: نواقصٌ (يستحيل
   تحميلها) · يتامى (رمز بلا مُعلن = كود ميت أو خطأ إملائي) · **انحراف ABIs** (رمز في
   arm64-v8a دون armeabi-v7a ⇒ انهيار على صنفٍ كامل من الأجهزة).
3. **غير مُتحقّق** — ما لم يُبنَ محليًّا (لا NDK هنا) يُعلن صريحًا؛ ولا يُقال «يمرّ».

⚠️ وحدوده: `nm` يُعيد الأسماء لا التوقيعات. فالعدد والنوع ومعاملات الدوال **لا** تُفحص هنا
(ذلك يحتاج مُصرّفًا)، وكذلك حال الدالة `static` مقابل دالة على المثيل (اسم الرمز واحد). وما
يُفحص هو **الوجود** و**التطابق بين الأبنية** — وهما بالضبط ما يسقط في `UnsatisfiedLinkError`.

الصيغة
------
    python3 tools/jni_symbols.py                      # الطبقة ١ + ما وُجد من ثنائيات
    python3 tools/jni_symbols.py --assert             # سطر حكم واحد، وتُخرج بخطأ عند عطب
    python3 tools/jni_symbols.py --so path/to/lib.so  # ثنائية محدّدة (تتكرّر)
    python3 tools/jni_symbols.py --strict             # الرمز اليتيم عطبٌ لا تحذير
    python3 tools/jni_symbols.py --require-binaries   # غياب الثنائيات عطب (وضع CI)
    python3 tools/jni_symbols.py --self-test          # يقيس الأداة على حالات مغروسة

ولا تكتب الأداة شيئًا ولا تُعدّل كودًا — تقرأ وتُبلّغ فقط (ADR-18: لا «إصلاح» لما لم يُطلب).
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.dirname(_HERE)
MANAGER = os.path.join(_REPO, "manager")

# مجلدات المصادر التي تُعلن دوال أصلية (Kotlin/Java).
DEFAULT_SOURCE_ROOTS = (
    os.path.join(MANAGER, "app", "src", "main", "java"),
    os.path.join(MANAGER, "terminal-emulator", "src", "main", "java"),
)

# مصادر Rust التي تُصدّر رموز JNI (crate التطبيق).
RUST_ROOTS = (os.path.join(MANAGER, "src", "main", "rust", "src"),)


# ─────────────────────────── تجريد النص من التعليقات والنصوص ───────────────────────────

def strip_literals(text: str) -> str:
    """يُفرّغ محتوى التعليقات والنصوص ويُبقي أطوال الأسطر (وإلا انحرفت أرقام الأسطر).

    Kotlin وJava تشتركان هنا: `//` و`/* */` (متشابكة في Kotlin كما في C لا كما في Java،
    ونتعامل معها كمتشابكة في الحالتين — التعليق المتشابك الخطأ ليس ما نقيسه هنا)،
    والنصّ `"..."` (وفيه `\\\"`)، والنصّ الخام `\"\"\"...\"\"\"`، والمحرف `'x'`.
    """
    out: list[str] = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            depth = 1
            out.append("  ")
            i += 2
            while i < n and depth:
                if text.startswith("/*", i):
                    depth += 1
                    out.append("  ")
                    i += 2
                elif text.startswith("*/", i):
                    depth -= 1
                    out.append("  ")
                    i += 2
                else:
                    out.append("\n" if text[i] == "\n" else " ")
                    i += 1
            continue
        if text.startswith('"""', i):
            out.append("   ")
            i += 3
            while i < n and not text.startswith('"""', i):
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append("   ")
            i += 3 if i < n else 0
            continue
        if ch == '"' or ch == "'":
            quote = ch
            out.append(" ")
            i += 1
            while i < n and text[i] != quote:
                if text[i] == "\\" and i + 1 < n:
                    out.append("  ")
                    i += 2
                    continue
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append(" ")
            i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out)


# ─────────────────────────── التصريحات الأصلية في Kotlin/Java ───────────────────────────

PACKAGE_PATTERN = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
LOAD_LIBRARY_PATTERN = re.compile(r"""loadLibrary\s*\(\s*"?([\w.-]+)"?""")

KT_TYPE_PATTERN = re.compile(r"(?<![\w.$])(object|class|interface)\s+([A-Za-z_]\w*)")
KT_NATIVE_PATTERN = re.compile(r"\bexternal\s+fun\s+([A-Za-z_]\w*)\s*\(")
JAVA_TYPE_PATTERN = re.compile(r"(?<![\w.$])(class|interface|enum|record)\s+([A-Za-z_]\w*)")
JAVA_NATIVE_PATTERN = re.compile(r"\bnative\b[^;{}=()]*?([A-Za-z_]\w*)\s*\(")


def jni_escape(name: str) -> str:
    """قواعد التحويل في مواصفة JNI: `_`→`_1` · `;`→`_2` · `[`→`_3` · وما عداها `_0xxxx`.

    `$` (بين الصفوف المتداخلة وأصنافها المصاحبة) يصير `_00024` — وهو الشائع فعلًا:
    `Outer$Companion` ← `Outer_00024Companion`.
    """
    out: list[str] = []
    for ch in name:
        if ch == "_":
            out.append("_1")
        elif ch == ";":
            out.append("_2")
        elif ch == "[":
            out.append("_3")
        elif ch.isascii() and ch.isalnum():
            out.append(ch)
        else:
            out.append("_0%04x" % ord(ch))
    return "".join(out)


def package_prefix(package: str) -> str:
    """بادئة رمز JNI لحزمة: `com.termux.terminal` ← `Java_com_termux_terminal_`.

    والتقسيم على النقاط **قبل** التحويل مقصود: النقطة فاصلٌ يصير شرطةً سفلية، ولا يُشفَّر
    إلى `_0002e` (وهو للتحويل داخل **اسم** الصنف وحده). وخلط الاثنين أفرغ قائمة
    «الأيتام» فأخفى رمزًا ميتًا فعلًا في `libtermux.so` — أي أنّ الأداة عطّلت نفسها بلا أن
    تشكو، ولذلك تُقاس هذه الدالّة في `--self-test` باسمها.
    """
    parts = [jni_escape(part) for part in package.split(".") if part]
    return "Java_" + ("_".join(parts) + "_" if parts else "")


def jni_symbol(package: str, types: list[str], method: str) -> str:
    return package_prefix(package) + jni_escape("$".join(types)) + "_" + jni_escape(method)


class Declaration:
    """تصريح دالة أصلية: صاحبه من المصدر، ورمزه المتوقّع، والمكتبة التي تحمله."""

    def __init__(self, symbol: str, owner: str, library: str | None, where: str):
        self.symbol = symbol
        self.owner = owner
        self.library = library
        self.where = where


def parse_declarations(path: str) -> list[Declaration]:
    """يقرأ ملف مصدر ويُعيد تصريحاته الأصلية برموز JNI المتوقّعة لها."""
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        raw = handle.read()
    text = strip_literals(raw)

    package_match = PACKAGE_PATTERN.search(text)
    package = package_match.group(1) if package_match else ""
    # المكتبة تُقرأ من الملف **قبل** التجريد: `System.loadLibrary("...")` نصٌّ يُفرَّغ أعلاه.
    library_match = LOAD_LIBRARY_PATTERN.search(raw)
    library = library_match.group(1) if library_match else None

    is_java = path.endswith(".java")
    type_pattern = JAVA_TYPE_PATTERN if is_java else KT_TYPE_PATTERN
    native_pattern = JAVA_NATIVE_PATTERN if is_java else KT_NATIVE_PATTERN

    events: list[tuple[int, str, object]] = []
    for match in type_pattern.finditer(text):
        events.append((match.start(), "type", match.group(2)))
    for match in native_pattern.finditer(text):
        events.append((match.start(), "native", match.group(1)))
    for index, char in enumerate(text):
        if char == "{":
            events.append((index, "open", None))
        elif char == "}":
            events.append((index, "close", None))
    events.sort(key=lambda row: (row[0], row[1]))

    stack: list[str] = []
    pending: str | None = None
    found: list[Declaration] = []
    for _, kind, payload in events:
        if kind == "type":
            pending = str(payload)
        elif kind == "open":
            stack.append(pending if pending else "")
            pending = None
        elif kind == "close":
            if stack:
                stack.pop()
        else:
            path_types = [name for name in stack if name]
            owner = ".".join(filter(None, [package, "$".join(path_types)]))
            symbol = jni_symbol(package, path_types, str(payload))
            relative = os.path.relpath(path, _REPO)
            found.append(Declaration(symbol, owner, library, relative))
    return found


def source_files(roots: tuple[str, ...]) -> list[str]:
    files: list[str] = []
    for root in roots:
        if not os.path.isdir(root):
            continue
        for base, _, names in os.walk(root):
            if os.sep + "build" + os.sep in base + os.sep:
                continue
            for name in names:
                if name.endswith((".kt", ".java")):
                    files.append(os.path.join(base, name))
    return sorted(files)


# ─────────────────────────── رموز Rust المصدرية ───────────────────────────

RUST_FN_PATTERN = re.compile(r"\bfn\s+(Java_[A-Za-z0-9_]+)")
# محرف Rust: `'x'` أو `'\n'` — وأمّا `'a` في `<'a>` فهو **عمر** لا محرف،
# وخلطهما يُفرّغ من `'a` إلى الفاصلة العليا التالية سطورًا كاملة فيها تصديرٌ حقيقي.
RUST_CHAR_LITERAL = re.compile(r"'(?:\\.|[^'\\\n])'")


def strip_rust_literals(text: str) -> str:
    """يُفرّغ تعليقات Rust والنصوص الخام، ويُبقي الأعمار (`<'local>`) كما هي.

    وحاجة ذلك مقيسة لا مُفترضة: صيغة Kotlin/Java العامة المُستخدمة أعلاه أكلت `<'local>`
    في `lib.rs` فأفرغت كل تصديرات `ProbeBridge` — أي أداة تشهد بعطب غير موجود. فالأعمار
    وحدها هي الفرق، وتعاملنا معها هنا صريحًا.
    """
    out: list[str] = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if ch == "/" and text[i : i + 2] == "//":
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if text[i : i + 2] == "/*":
            depth = 1
            out.append("  ")
            i += 2
            while i < n and depth:
                if text[i : i + 2] == "/*":
                    depth += 1
                    out.append("  ")
                    i += 2
                elif text[i : i + 2] == "*/":
                    depth -= 1
                    out.append("  ")
                    i += 2
                else:
                    out.append("\n" if text[i] == "\n" else " ")
                    i += 1
            continue
        if ch == "r" and i + 1 < n and text[i + 1] in '#"':
            hashes = 0
            j = i + 1
            while j < n and text[j] == "#":
                hashes += 1
                j += 1
            if j < n and text[j] == '"':
                out.append(" " * (hashes + 2))
                i = j + 1
                terminator = '"' + "#" * hashes
                while i < n and not text.startswith(terminator, i):
                    out.append("\n" if text[i] == "\n" else " ")
                    i += 1
                out.append(" " * len(terminator))
                i += len(terminator)
                continue
        if ch == '"':
            out.append(" ")
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\" and i + 1 < n:
                    out.append("  ")
                    i += 2
                    continue
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append(" ")
            i += 1
            continue
        if ch == "'":
            match = RUST_CHAR_LITERAL.match(text, i)
            if match:
                out.append(" " * (match.end() - i))
                i = match.end()
                continue
        out.append(ch)
        i += 1
    return "".join(out)


def rust_exports(roots: tuple[str, ...] | None = None) -> dict[str, str]:
    # و`None` ← القيمة الافتراضية **وقت النداء** لا وقت التعريف: تعريفها في التوقيع يجمّدها
    # فيصير تغييرها (كما يفعل أي اختبار عطب) بلا أثر — وقد حدث ذلك فعلًا في اختبار التكذيب.
    exports: dict[str, str] = {}
    for root in (RUST_ROOTS if roots is None else roots):
        if not os.path.isdir(root):
            continue
        for base, _, names in os.walk(root):
            for name in names:
                if not name.endswith(".rs"):
                    continue
                path = os.path.join(base, name)
                with open(path, "r", encoding="utf-8", errors="replace") as handle:
                    text = strip_rust_literals(handle.read())
                for match in RUST_FN_PATTERN.finditer(text):
                    exports.setdefault(match.group(1), os.path.relpath(path, _REPO))
    return exports


CARGO_NAME_PATTERN = re.compile(r'^\s*name\s*=\s*"([\w.-]+)"', re.MULTILINE)
ANDROID_MODULE_PATTERN = re.compile(r"^\s*LOCAL_MODULE\s*:?=\s*(\S+)", re.MULTILINE)


def implementation_languages() -> dict[str, str]:
    """يحصر: أي مكتبة تُنفَّذ بلغة أي مصدر — من ملفات البناء نفسها لا من الذاكرة.

    ولماذا يلزم: تصريح في `JNI.java` لا يخصّ Rust إطلاقًا (تنفيذه `termux.c`)، ونسبه
    إلى Rust كان يصنع عطبًا وهميًّا في كل تشغيل — وأداة تُصرخ كذبًا تُهمَل، فيموت الفحص.
    """
    languages: dict[str, str] = {}
    cargo = os.path.join(MANAGER, "src", "main", "rust", "Cargo.toml")
    if os.path.isfile(cargo):
        with open(cargo, "r", encoding="utf-8", errors="replace") as handle:
            match = CARGO_NAME_PATTERN.search(handle.read())
        if match:
            languages[match.group(1)] = "rust"
    for base, _, names in os.walk(os.path.join(MANAGER)):
        if os.sep + "build" + os.sep in base + os.sep:
            continue
        for name in names:
            if name != "Android.mk":
                continue
            path = os.path.join(base, name)
            with open(path, "r", encoding="utf-8", errors="replace") as handle:
                for match in ANDROID_MODULE_PATTERN.finditer(handle.read()):
                    module = match.group(1)
                    languages[module[3:] if module.startswith("lib") else module] = "c"
    return languages


# ─────────────────────────── الرموز في الثنائيات ───────────────────────────

NM_PATTERN = re.compile(r"^[0-9a-fA-F]+\s+([A-Za-z])\s+(Java_\S+)$")

# أنواع `nm` التي تعني **معرّفًا ومرئيًا**. و`U` ليست منها: هي رمز **مستورد** (غير معرّف)،
# وخلطها بالمعرّف كان سيُظهر عقدًا سليمة كأنها مُصدَّرة — أي أداة تشهد زورًا. والصغيرة
# (`t`/`d`/`b`) محلّية لا يربطها محمّل JVM، فلا تُحسب إصدارًا كذلك.
DEFINED_NM_TYPES = frozenset("TDBRWVSGA")


def parse_nm(text: str) -> list[str]:
    """يستخرج الرموز المُصدَّرة **والمُعرَّفة** من مخرَج `nm -D --defined-only`."""
    symbols: list[str] = []
    for row in text.splitlines():
        match = NM_PATTERN.match(row.strip())
        if match and match.group(1) in DEFINED_NM_TYPES:
            symbols.append(match.group(2))
    return symbols


def read_binary(path: str) -> list[str]:
    """رموز ثنائية عبر `nm`؛ و`llvm-nm` بديل عند غيابه، ولا استثناء صامت."""
    tool = shutil.which("nm") or shutil.which("llvm-nm")
    if tool is None:
        raise RuntimeError("لا `nm` ولا `llvm-nm` في هذه البيئة")
    done = subprocess.run(
        [tool, "-D", "--defined-only", path],
        capture_output=True, text=True, check=False,
    )
    if done.returncode != 0:
        done = subprocess.run(
            [tool, "--dynamic", "--defined-only", path],
            capture_output=True, text=True, check=False,
        )
    if done.returncode != 0:
        raise RuntimeError(f"فشل قراءة {os.path.basename(path)}: {done.stderr.strip()[:200]}")
    return parse_nm(done.stdout)


def discover_binaries(extra: list[str]) -> dict[str, dict[str, str]]:
    """يحصر ثنائيات jniLibs بالتخطيط: {اسم المكتبة: {ABI: مسار}}."""
    found: dict[str, dict[str, str]] = {}
    candidates: list[str] = list(extra)
    jni_libs = os.path.join(MANAGER, "app", "src", "main", "jniLibs")
    if os.path.isdir(jni_libs):
        for abi in sorted(os.listdir(jni_libs)):
            abi_dir = os.path.join(jni_libs, abi)
            if not os.path.isdir(abi_dir):
                continue
            for name in sorted(os.listdir(abi_dir)):
                if name.endswith(".so"):
                    candidates.append(os.path.join(abi_dir, name))
    for path in candidates:
        if not os.path.isfile(path):
            continue
        abi = os.path.basename(os.path.dirname(path))
        if abi == "release" or abi == "debug":
            abi = "host(غير مُشحونة)"
        library = os.path.basename(path)[3:-3] if os.path.basename(path).startswith("lib") else os.path.basename(path)[:-3]
        found.setdefault(library, {})[abi] = path
    return found


# ─────────────────────────── المقارنة والحكم ───────────────────────────

class Report:
    def __init__(self) -> None:
        self.missing_source: list[str] = []
        self.orphan_source: list[str] = []
        self.unmappable: list[str] = []
        self.ambiguous: list[str] = []
        self.missing_binary: list[str] = []
        self.orphan_binary: list[str] = []
        self.abi_drift: list[str] = []
        self.abi_coverage: list[str] = []
        self.library_absent: list[str] = []
        self.checked_libraries: list[str] = []
        self.skipped_libraries: list[str] = []
        self.source_scope: dict[str, int] = {}
        self.declarations = 0
        self.binaries = 0

    def failures(self, strict: bool, require_binaries: bool = False) -> list[str]:
        rows = list(self.missing_source) + list(self.orphan_source) + list(self.unmappable)
        rows += list(self.missing_binary) + list(self.abi_drift) + list(self.abi_coverage)
        if require_binaries:
            rows += list(self.library_absent)
        if strict:
            rows += list(self.orphan_binary)
        return rows


def scan_sources(roots: tuple[str, ...]) -> tuple[list[Declaration], set[str]]:
    """يمرّ على المصادر **مرة واحدة**: التصريحات + أسماء الحِزم المعروفة (لحصر الرموز)."""
    declarations: list[Declaration] = []
    packages: set[str] = set()
    for path in source_files(roots):
        with open(path, "r", encoding="utf-8", errors="replace") as handle:
            text = strip_literals(handle.read())
        match = PACKAGE_PATTERN.search(text)
        if match:
            packages.add(match.group(1))
        declarations.extend(parse_declarations(path))
    return declarations, packages


def build_report(roots: tuple[str, ...], binaries: dict[str, dict[str, str]]) -> Report:
    report = Report()
    declarations, packages = scan_sources(roots)
    report.declarations = len(declarations)

    # الطبقة ١: المصدر Kotlin/Java ↔ Rust — لمكتبات Rust وحدها، وبحصر من ملف البناء
    languages = implementation_languages()
    exports = rust_exports()
    declared_symbols = {decl.symbol for decl in declarations}
    rust_libraries = {library for library, lang in languages.items() if lang == "rust"}
    for decl in declarations:
        if decl.library is None:
            label = "بلا loadLibrary"
        else:
            label = languages.get(decl.library, "لغة غير معروفة")
        report.source_scope[label] = report.source_scope.get(label, 0) + 1
    # أسماء متكرّرة (تحميل زائد) لا يمكن رمزها بلا توقيع ⇒ تُعلن ولا تُخمَّن.
    counts: dict[str, int] = {}
    for decl in declarations:
        counts[decl.symbol] = counts.get(decl.symbol, 0) + 1
    ambiguous = {symbol for symbol, count in counts.items() if count > 1}
    export_prefixes = {symbol.rsplit("_", 1)[0] for symbol in exports}
    for symbol in sorted(ambiguous):
        if symbol in exports or symbol.rsplit("_", 1)[0] in export_prefixes:
            report.ambiguous.append(symbol)
        else:
            report.missing_source.append(symbol)
    for decl in declarations:
        if decl.symbol in ambiguous or decl.library not in rust_libraries:
            continue
        if decl.symbol not in exports and not any(
            row.startswith(decl.symbol + "__") for row in exports
        ):
            row = f"{decl.symbol} — {decl.owner or '(بلا حزمة)'} في {decl.where} ولا تصدير له في Rust"
            if row not in report.missing_source:
                report.missing_source.append(row)
    # يتيم المصدر: تصدير Rust لا يقابله تصريح. يُحصر في العائلات التي نعرف حِزمها،
    # وإلا لنسبنا إلى أنفسنا رموزًا لطرف ثالث في ثنائيات مُشحونة.
    known_prefixes = {package_prefix(package) for package in packages}
    for symbol, where in sorted(exports.items()):
        if symbol in declared_symbols or any(row.startswith(symbol + "__") for row in declared_symbols):
            continue
        if any(symbol.startswith(prefix) for prefix in known_prefixes):
            report.orphan_source.append(f"{symbol} — مُصدَّر في {where} ولا مُعلن له في Kotlin/Java")

    # الطبقة ٢: الثنائيات
    unmapped = sorted({decl.where for decl in declarations if not decl.library})
    for where in unmapped:
        report.unmappable.append(f"{where} — لا `System.loadLibrary` فيه ⇒ لا يُعرف أيّ ثنائية تحمله")

    # مكتبة يعقدها Kotlin ولا ثنائية لها إطلاقًا. في CI هذا عطب (البُناء أنتجها فعلًا)، ومع
    # المالك محليًّا (لا NDK) هو «الطبقة ٢ غير مُتحقَّقة» — وكلاهما يُقال ولا يُخمَّن.
    declared_libraries = sorted({decl.library for decl in declarations if decl.library})
    for library in declared_libraries:
        if library not in binaries:
            report.library_absent.append(f"{library} — معلنة في Kotlin ولا ثنائية لها في الشجرة")

    # تغطية الأعمدة: مكتبة لها عقد Kotlin يجب أن تُبنى لكل عمود موجود (وليس بعضها).
    # **وهذا هو صنف العطب نفسه الذي وقع في `libtermux.so`**: كانت ٦٤-بت وحدها فيسقط
    # `loadLibrary` على هاتف ٣٢-بت. والقاعدة **لا تنشط إلا إذا كان في الشجرة أكثر من عمود**،
    # فالبيئة المحلية (عمود واحد في `jniLibs`) لا يُصنع فيها عطب وهميًّا. وبناء المضيف
    # (`target/`) لا يُحسب عمودًا مُشحونًا.
    shipped_abis = sorted(
        {abi for abis in binaries.values() for abi in abis if not abi.startswith("host")}
    )
    if len(shipped_abis) > 1:
        for library in declared_libraries:
            abis = binaries.get(library)
            if not abis:
                continue
            absent = [abi for abi in shipped_abis if abi not in abis]
            if absent:
                report.abi_coverage.append(
                    f"{library} — مبنيّة في {'، '.join(sorted(abis))} وغائبة في "
                    f"{'، '.join(absent)}"
                )

    for library, abis in sorted(binaries.items()):
        owned = [decl for decl in declarations if decl.library == library]
        if not owned:
            # ثنائية بلا تصريح يخصّها: تُقاس رموزها للانحراف وحده (لا نعرف عقدها).
            owned = []
        paths = sorted(abis.items())
        export_sets: dict[str, set[str]] = {}
        for abi, path in paths:
            symbols = read_binary(path)
            report.binaries += 1
            export_sets[abi] = set(symbols)
        if owned:
            report.checked_libraries.append(library)
        else:
            report.skipped_libraries.append(library)
        for abi, symbols in export_sets.items():
            if owned:
                for decl in owned:
                    if decl.symbol not in symbols:
                        report.missing_binary.append(
                            f"{decl.symbol} — مُعلن في {decl.where} وليس مُصدَّرًا في "
                            f"{os.path.basename(abis[abi])} ({abi})"
                        )
                for symbol in sorted(symbols):
                    if symbol in declared_symbols or any(
                        row.startswith(symbol + "__") for row in declared_symbols
                    ):
                        continue
                    if any(symbol.startswith(prefix) for prefix in known_prefixes):
                        report.orphan_binary.append(
                            f"{symbol} — مُصدَّر في {abi}/{os.path.basename(abis[abi])} بلا مُعلن"
                        )
        # انحراف ABIs: رمز في بناء صنفٍ دون آخر ⇒ `UnsatisfiedLinkError` على ذلك الصنف وحده.
        # وبناء المضيف (target/) يُستثنى: ليس ABI مُشحونة، ومقارنته بها انحرافٌ مصطنع.
        shipped = {abi: symbols for abi, symbols in export_sets.items() if not abi.startswith("host")}
        if len(shipped) > 1:
            union = set().union(*shipped.values())
            for symbol in sorted(union):
                holders = [abi for abi, symbols in shipped.items() if symbol in symbols]
                if len(holders) != len(shipped):
                    absent = sorted(set(shipped) - set(holders))
                    report.abi_drift.append(
                        f"{symbol} — موجود في {', '.join(sorted(holders))} وغائب في {', '.join(absent)}"
                    )
    return report


def print_report(report: Report, strict: bool, require_binaries: bool = False) -> None:
    print(f"عقود JNI: تصريحات {report.declarations} · ثنائيات مقروءة {report.binaries}")
    # والعلم الثالث `informational` يمنع علامة العطب على سطر لا يُسقط البوابة: الخلط بينهما
    # يجعل القارئ يظن أن التشغيل فشل وهو ناجح — وهو أسوأ من عدم الطباعة.
    sections = (
        ("ناقص في Rust (مصدر)", report.missing_source, False),
        ("يتيم في Rust (بلا مُعلن)", report.orphan_source, False),
        ("مكتبة غير معروفة", report.unmappable, False),
        ("اسم مُحمَّل مرّات (يحتاج مُصرّفًا)", report.ambiguous, False),
        ("ناقص في الثنائية (يستحيل تحميله)", report.missing_binary, False),
        ("انحراف بين الأبنية (ABIs)", report.abi_drift, False),
        ("مكتبة في عمود واحد دون بقية الأعمدة", report.abi_coverage, False),
        ("مكتبة معلنة بلا أي ثنائية", report.library_absent, not require_binaries),
    )
    for title, rows, informational in sections:
        if not rows:
            continue
        mark = "•" if informational else "⚠️"
        print(f"  {mark} {title}: {len(rows)}")
        for row in rows[:12]:
            print(f"      {row}")
        if len(rows) > 12:
            print(f"      … و{len(rows) - 12} أخرى")
    if report.orphan_binary:
        mark = "⚠️" if strict else "•"
        print(f"  {mark} يتيم في الثنائية (كود ميت لا يضرّ): {len(report.orphan_binary)}")
        for row in report.orphan_binary[:6]:
            print(f"      {row}")
        if len(report.orphan_binary) > 6:
            print(f"      … و{len(report.orphan_binary) - 6} أخرى")
    if report.checked_libraries:
        print(f"  ✓ تحقّقت الثنائيات لعقود: {', '.join(report.checked_libraries)}")
    if report.skipped_libraries:
        print(f"  • ثنائيات بلا عقود معلنة (انحرافها فقط يُقاس): {', '.join(report.skipped_libraries)}")
    if report.source_scope:
        scope = " · ".join(f"{name}: {count}" for name, count in sorted(report.source_scope.items()))
        print(f"  • نطاق المصدر (طبقة ١ تُطبَّق على Rust وحده): {scope}")


# ─────────────────────────── قياس الأداة نفسها ───────────────────────────

SELF_TEST_KOTLIN = """package nd.max.demo

import android.util.Log

object Probe {
    private external fun nativeRead(packed: String): String
    private external fun native_write(path: String): String

    object Inner {
        private external fun nestedCall(): Int
    }
}
"""

SELF_TEST_JAVA = """package com.demo.term;

final class JNI {
    static { System.loadLibrary("termux"); }
    /** this native comment must not count */
    public static native int createSubprocess(String cmd);
    public static native void setPtyWindowSize(int fd);
}
"""

SELF_TEST_RUST = """pub extern "system" fn Java_nd_max_demo_Probe_nativeRead() {}
// pub extern "system" fn Java_nd_max_demo_Fake_x() {} — تعليق لا يُحسب
pub extern "system" fn Java_nd_max_demo_Probe_native_1write() {}
/* fn Java_nd_max_demo_Fake_y() {} */
const ONE: char = '\\u{1}';
/// ويليها عمرٌ في دالّة حقيقية — وهذا ما أفرغ التصدير فعلًا قبل الإصلاح.
pub extern "system" fn Java_nd_max_demo_Probe_00024Inner_nestedCall<'local>(
    _env: *mut u8,
) {
    let raw = r"fn Java_nd_max_demo_Fake_z() {}";
    let _ = (raw, ONE);
}
"""

# وفيها نوعان يجب أن **يُرفضا**: `U` (مستورد لا معرّف) و`t` (محلّي لا يربطه محمّل JVM).
# ثنائيتان لنفس «المكتبة» بأسماء تطابق مصدر Kotlin المجاور (demo2/Two).
SELF_TEST_C_KT_FULL = """\
__attribute__((visibility("default")))
void Java_nd_max_demo2_Two_nativeRead(void *env, void *self) { (void)env; (void)self; }
__attribute__((visibility("default")))
void Java_nd_max_demo2_Two_ghost(void *env, void *self) { (void)env; (void)self; }
"""

SELF_TEST_C_KT_PARTIAL = """\
__attribute__((visibility("default")))
void Java_nd_max_demo2_Two_nativeRead(void *env, void *self) { (void)env; (void)self; }
"""

# وفيها نوعان يجب أن **يُرفضا**: `U` (مستورد لا معرّف) و`t` (محلّي لا يربطه محمّل JVM).
SELF_TEST_NM = """\
0000000000001234 T Java_nd_max_demo_Probe_nativeRead
0000000000001235 T Java_nd_max_demo_Probe_native_1write
0000000000001236 W Java_nd_max_demo_Probe_00024Inner_nestedCall
0000000000001237 U Java_nd_max_demo_Probe_missing
0000000000001238 t Java_nd_max_demo_Probe_localOnly
0000000000001239 D not_a_jni_symbol
"""

# بلا `jni.h` عمدًا: الرمز في JNI هو اسم دالّة C عادية، وربطه بترويسة كان سيجعل
# قياس الأداة رهينًا بترويسات JDK بدل كونه رهينًا بالمُصرّف وحده.
SELF_TEST_C = """\
__attribute__((visibility("default")))
void Java_nd_max_demo_Probe_nativeRead(void *env, void *self) { (void)env; (void)self; }

__attribute__((visibility("default")))
void Java_nd_max_demo_Probe_ghost(void *env, void *self) { (void)env; (void)self; }
"""


def self_test() -> int:
    failures = 0
    ran = 0
    skipped = 0

    def check(name: str, got: object, expected: object) -> None:
        nonlocal failures, ran
        ran += 1
        ok = got == expected
        failures += 0 if ok else 1
        print(f"  {'✓' if ok else '✗'} {name}: متوقّع {expected!r} · حاصل {got!r}")

    with tempfile.TemporaryDirectory() as tmp:
        kt_dir = os.path.join(tmp, "kt", "nd", "max", "demo")
        os.makedirs(kt_dir)
        kt_path = os.path.join(kt_dir, "Probe.kt")
        with open(kt_path, "w", encoding="utf-8") as handle:
            handle.write(SELF_TEST_KOTLIN)
        java_dir = os.path.join(tmp, "java", "com", "demo", "term")
        os.makedirs(java_dir)
        java_path = os.path.join(java_dir, "JNI.java")
        with open(java_path, "w", encoding="utf-8") as handle:
            handle.write(SELF_TEST_JAVA)

        kt = {decl.symbol for decl in parse_declarations(kt_path)}
        check(
            "Kotlin: اسم بسيط",
            "Java_nd_max_demo_Probe_nativeRead" in kt,
            True,
        )
        check(
            "Kotlin: `_` يصير `_1`",
            "Java_nd_max_demo_Probe_native_1write" in kt,
            True,
        )
        check(
            "Kotlin: صنف متداخل `$`←`_00024`",
            "Java_nd_max_demo_Probe_00024Inner_nestedCall" in kt,
            True,
        )
        check("Kotlin: العدد", len(kt), 3)
        check(
            "Kotlin: تعليق مزيّف لا يُحسب",
            "Java_nd_max_demo_Probe_this" in kt,
            False,
        )
        check("بادئة الحزمة: النقطة فاصل لا `_0002e`", package_prefix("com.termux.terminal"), "Java_com_termux_terminal_")
        check("بادئة الحزمة: بلا حزمة", package_prefix(""), "Java_")
        check(
            "بادئة الحزمة: مطابقة رمز صنف كامل",
            jni_symbol("com.termux.terminal", ["JNI"], "close"),
            "Java_com_termux_terminal_JNI_close",
        )
        java = {decl.symbol for decl in parse_declarations(java_path)}
        check("Java: دالة أصلية", "Java_com_demo_term_JNI_createSubprocess" in java, True)
        check("Java: تعليق KDoc لا يُحسب", len(java), 2)
        check(
            "Java: مكتبة مُستخرجة",
            {decl.library for decl in parse_declarations(java_path)},
            {"termux"},
        )

        rust_dir = os.path.join(tmp, "rust")
        os.makedirs(rust_dir)
        with open(os.path.join(rust_dir, "lib.rs"), "w", encoding="utf-8") as handle:
            handle.write(SELF_TEST_RUST)
        exports = rust_exports((rust_dir,))
        check("Rust: استخراج التصدير (مع أعمارٍ ونصوص خامّة)", len(exports), 3)
        check(
            "Rust: تصدير داخل تعليق أو نصّ خام لا يُحسب",
            [key for key in exports if "Fake" in key],
            [],
        )
        check(
            "Rust: العمر `<'local>` لا يبتلع الدالّة التالية",
            "Java_nd_max_demo_Probe_00024Inner_nestedCall" in exports,
            True,
        )

        nm_symbols = parse_nm(SELF_TEST_NM)
        check(
            "nm: المعرّف المرئي وحده (U مستورد وt محلّي يُرفضان)",
            sorted(nm_symbols),
            sorted(
                [
                    "Java_nd_max_demo_Probe_nativeRead",
                    "Java_nd_max_demo_Probe_native_1write",
                    "Java_nd_max_demo_Probe_00024Inner_nestedCall",
                ]
            ),
        )

        # المقارنة: ناقص + يتيم + انحراف، كلٌّ بعائد **مُتوقَّع بالاسم** لا بعائد الأداة نفسها.
        symbols = set(nm_symbols)
        declared = {"Java_nd_max_demo_Probe_nativeRead", "Java_nd_max_demo_Probe_missing"}
        check("مقارنة: ناقص يُكتشف", sorted(declared - symbols), ["Java_nd_max_demo_Probe_missing"])
        check(
            "مقارنة: يتيم يُكتشف",
            sorted(symbols - declared),
            [
                "Java_nd_max_demo_Probe_00024Inner_nestedCall",
                "Java_nd_max_demo_Probe_native_1write",
            ],
        )

        drift_sets = {"arm64-v8a": {"a", "b"}, "armeabi-v7a": {"a"}}
        union = set().union(*drift_sets.values())
        drift = [
            s for s in sorted(union)
            if sum(1 for rows in drift_sets.values() if s in rows) != len(drift_sets)
        ]
        check("انحراف ABIs: يُكتشف", drift, ["b"])

        # اختبار من الطرف إلى الطرف: ثنائية حقيقية بمُصرّف C ثم قراءة `nm` فعلية —
        # وهذا وحده يقيس أن مسار `nm` نفسه يعمل، لا أن تحليل نصّه يعمل.
        cc = shutil.which("cc") or shutil.which("gcc")
        if cc is None:
            skipped += 1
            print("  • اختبار الطرف إلى الطرف: متخطّى — لا مُصرّف C في هذه البيئة")
        else:
            c_path = os.path.join(tmp, "demo.c")
            so_path = os.path.join(tmp, "libdemo.so")
            with open(c_path, "w", encoding="utf-8") as handle:
                handle.write(SELF_TEST_C)
            built = subprocess.run(
                [cc, "-shared", "-fPIC", "-o", so_path, c_path],
                capture_output=True, text=True, check=False,
            )
            if built.returncode != 0:
                check(f"بناء الثنائية الاختبارية ({built.stderr.strip()[:120]})", False, True)
            else:
                real = set(read_binary(so_path))
                check("ثنائية حقيقية: الرمز المعلن يُقرأ", "Java_nd_max_demo_Probe_nativeRead" in real, True)
                check("ثنائية حقيقية: الشبح يُكتشف يتيمًا", sorted(real - kt), ["Java_nd_max_demo_Probe_ghost"])

                # انحراف ABIs ورمزٌ ناقص في بناء واحد: ثنائيتان حقيقيتان بأبناء ABIs
                # موازية، ومصدر Kotlin يعلن الرمزين ⇒ يجب أن يُكتشف الاثنان.
                kit_dir = os.path.join(tmp, "kt2", "nd", "max", "demo2")
                os.makedirs(kit_dir)
                with open(os.path.join(kit_dir, "Two.kt"), "w", encoding="utf-8") as handle:
                    handle.write(
                        "package nd.max.demo2\n\n"
                        "object Two {\n"
                        '    private val available = runCatching { System.loadLibrary("demo2") }.isSuccess\n'
                        "    private external fun nativeRead(): Unit\n"
                        "    private external fun ghost(): Unit\n"
                        "}\n"
                    )
                full_c = os.path.join(tmp, "demo2_full.c")
                partial_c = os.path.join(tmp, "demo2_partial.c")
                full_so = os.path.join(tmp, "libdemo2_full.so")
                partial_so = os.path.join(tmp, "libdemo2_partial.so")
                with open(full_c, "w", encoding="utf-8") as handle:
                    handle.write(SELF_TEST_C_KT_FULL)
                with open(partial_c, "w", encoding="utf-8") as handle:
                    handle.write(SELF_TEST_C_KT_PARTIAL)
                built_full = subprocess.run(
                    [cc, "-shared", "-fPIC", "-o", full_so, full_c],
                    capture_output=True, text=True, check=False,
                )
                built_partial = subprocess.run(
                    [cc, "-shared", "-fPIC", "-o", partial_so, partial_c],
                    capture_output=True, text=True, check=False,
                )
                if built_full.returncode != 0 or built_partial.returncode != 0:
                    check("بناء ثنائيتي الانحراف", False, True)
                else:
                    drift_report = build_report(
                        (os.path.join(tmp, "kt2"),),
                        {
                            "demo2": {
                                "arm64-v8a": full_so,
                                "armeabi-v7a": partial_so,
                            }
                        },
                    )
                    check("انحراف ABIs على ثنائيتين حقيقيتين", len(drift_report.abi_drift), 1)
                    check("ناقص في ABI واحدة", len(drift_report.missing_binary), 1)
                    check("والعطب يُسقط البوابة", bool(drift_report.failures(False)), True)
                    check("والتغطية كاملة فلا تُبلَّغ", len(drift_report.abi_coverage), 0)
                    # عمود واحد ⇒ قاعدة التغطية غير ناشطة (وإلا صارت البيئة المحلية عطبًا)
                    single = build_report(
                        (os.path.join(tmp, "kt2"),),
                        {"demo2": {"arm64-v8a": full_so}},
                    )
                    check("عمود واحد لا يُصنع عطبًا", len(single.abi_coverage), 0)
                    # وعمودان ومكتبة معلنة في أحدهما ⇒ عطب (صنف عطب libtermux ٦٤-بت)
                    partial_coverage = build_report(
                        (os.path.join(tmp, "kt2"),),
                        {
                            "demo2": {"arm64-v8a": full_so},
                            "skipped_lib": {"arm64-v8a": full_so, "armeabi-v7a": full_so},
                        },
                    )
                    check("مكتبة في عمود واحد من عمودين", len(partial_coverage.abi_coverage), 1)
                    check(
                        "وتُسقط البوابة (مُتحقَّق بلا --require-binaries)",
                        bool(partial_coverage.failures(False)),
                        True,
                    )
    print(f"قياس الأداة: {ran} حالة · متخطّى {skipped} · إخفاقات {failures}")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="تطابق رموز JNI بين المصدر والثنائيات")
    parser.add_argument("--assert", dest="gate", action="store_true", help="حكم واحد وتُخرج بخطأ عند عطب")
    parser.add_argument("--self-test", dest="self_test", action="store_true", help="قياس الأداة على حالات مغروسة")
    parser.add_argument("--strict", action="store_true", help="الرمز اليتيم في الثنائية عطبٌ لا تحذير")
    parser.add_argument("--require-binaries", dest="require_binaries", action="store_true",
                        help="غياب الثنائيات عطب (وضع CI: بينى CI الثنائيات فعلًا)")
    parser.add_argument("--so", action="append", default=[], help="ثنائية محدّدة (تتكرّر)")
    parser.add_argument("--source-root", action="append", default=[], help="جذر مصادر (يتكرّر)")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    roots = tuple(args.source_root) if args.source_root else DEFAULT_SOURCE_ROOTS
    # مسار ثنائية مُعلن وغير موجود = التباس في النداء لا «لا ثنائيات»: يُعلن ويسقط، وإلا
    # ظنّ المشغّل أنه قاس وهو لم يقس. (وهذا حدّ مستقل عن --require-binaries.)
    missing_paths = [path for path in args.so if not os.path.isfile(path)]
    for path in missing_paths:
        print(f"  ⚠️ مسار ثنائية مُعلن وغير موجود: {path}")
    binaries = discover_binaries(args.so)
    report = build_report(roots, binaries)
    print_report(report, args.strict, args.require_binaries)

    failures = report.failures(args.strict, args.require_binaries)
    failures += missing_paths
    if not report.binaries:
        note = "لا ثنائيات في هذه البيئة ⇒ الطبقة ٢ (الرموز الفعلية) غير مُتحقّقة"
        if args.require_binaries:
            print(f"  ❌ {note} — والمطلوب هنا ثنائيات")
            failures = failures + [note]
        else:
            print(f"  • {note}")

    verdict = (
        f"رموز JNI: تصريحات {report.declarations} · ثنائيات {report.binaries} · "
        f"نواقص {len(report.missing_source) + len(report.missing_binary) + len(report.abi_drift)} · "
        f"يتامى {len(report.orphan_source) + len(report.orphan_binary)}"
    )
    if args.gate:
        print(verdict)
        return 1 if failures else 0
    print(verdict)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
