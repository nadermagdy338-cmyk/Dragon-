"""فاحص ثابت — أدوات المستودع، لا CI.

يفحص: مفاتيح النصوص وتكرارها، ملفات مرشّحة (TARGETS)، توازن الأقواس، رموز ميتة (DEAD)،
صحة XML، واستيرادات غير مستخدمة في ملفات جديدة.

التشغيل: `python3 tools/repo_audit.py` **من أي مجلد** — الجذر يُستنتج من موقع الملف،
فلا تعتمد المخرجات على مجلد العمل.
ليس بديلًا عن بوابات docs/ai/VALIDATION.md — تلك هي العقد، وهذا فحص إضافي.

صيانة: قائمتا TARGETS وNEW مكتوبتان يدويًا. عند حذف ملف عن قصد من الشجرة يجب حذف سطره
هنا أيضًا، ولكي لا تصبح القائمة بالية بصمت يصرّح الفاحص بأي سطر يشير إلى ملف غير موجود.
"""
import os, re, sys, glob, collections

# الجذر يُستنتج من موقع هذا الملف (tools/ ← جذر المستودع)، فلا يتوقف الفحص على CWD.
_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.dirname(_HERE)
ROOT = os.path.join(_REPO, "manager", "app", "src", "main")
if not os.path.isdir(ROOT):
    sys.exit(f"ROOT not found: {ROOT}\nشغّل الملف من داخل شجرة المستودع.")
JAVA = os.path.join(ROOT, "java")
RES = os.path.join(ROOT, "res")

problems = []

# ---------- 1. string resources ----------
defined = collections.defaultdict(list)
for d in sorted(glob.glob(os.path.join(RES, "values*"))):
    locale = os.path.basename(d)
    for f in sorted(glob.glob(os.path.join(d, "*.xml"))):
        txt = open(f, encoding="utf-8").read()
        for m in re.finditer(r'<(string|plurals|string-array)\s+name="([^"]+)"', txt):
            defined[(locale, m.group(2))].append(os.path.basename(f))

# duplicates per locale
for (locale, name), files in sorted(defined.items()):
    if len(files) > 1:
        problems.append(f"DUPLICATE RESOURCE [{locale}] {name} in {files}")

base = {n for (loc, n) in defined if loc == "values"}

# ---------- 2. R.string refs ----------
kt_files = []
for dp, dn, fn in os.walk(JAVA):
    for f in fn:
        if f.endswith(".kt"):
            kt_files.append(os.path.join(dp, f))

refs = collections.defaultdict(set)
for p in kt_files:
    txt = open(p, encoding="utf-8").read()
    for m in re.finditer(r'R\.string\.([A-Za-z0-9_]+)', txt):
        refs[m.group(1)].add(p)
missing = sorted(n for n in refs if n not in base)
for n in missing:
    problems.append(f"MISSING R.string.{n} -> used in {sorted(os.path.basename(x) for x in refs[n])}")

# ---------- 3. bracket balance ----------
def balance(path):
    txt = open(path, encoding="utf-8").read()
    i, n = 0, len(txt)
    depth = {"(": 0, "[": 0, "{": 0}
    close = {")": "(", "]": "[", "}": "{"}
    while i < n:
        c = txt[i]
        if c == "/" and i + 1 < n and txt[i+1] == "/":
            j = txt.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and i + 1 < n and txt[i+1] == "*":
            j = txt.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if txt.startswith('"""', i):
            j = txt.find('"""', i + 3)
            i = n if j < 0 else j + 3
            continue
        if c == '"':
            i += 1
            while i < n and txt[i] != '"':
                if txt[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if c == "'":
            i += 1
            while i < n and txt[i] != "'":
                if txt[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if c in depth:
            depth[c] += 1
        elif c in close:
            depth[close[c]] -= 1
        i += 1
    return depth

TARGETS = [
    "ui/design/MaxHelp.kt",
    "ui/component/ConfigBackupFlow.kt",
    "ui/subscreens/hubs/MaxDomainHubScreen.kt",
    "ui/subscreens/ConfigBackupScreen.kt",
    "ui/mainscreens/ControlScreen.kt",
    "ui/mainscreens/SettingsScreen.kt",
    "ui/mainscreens/ApplistScreen.kt",
    "ui/navigation/MaxDestinations.kt",
    "ui/navigation/MaxNavGraph.kt",
    "ui/subscreens/ChargingScreen.kt",
    "ui/subscreens/ZramManagerScreen.kt",
    "ui/subscreens/NetworkSchedulerScreen.kt",
    "ui/subscreens/CpuCoreControlScreen.kt",
    "ui/mainscreens/LegacyTweakComponents.kt",
]
for rel in TARGETS:
    p = os.path.join(JAVA, "nd/max", rel)
    if not os.path.exists(p):
        problems.append(f"MISSING FILE {rel}")
        continue
    d = balance(p)
    if any(v != 0 for v in d.values()):
        problems.append(f"UNBALANCED {rel}: {d}")

# ---------- 4. dead symbols ----------
DEAD = ["AllTweaks", "TweakScreen", "AppListHero", "AppsProductBridge", "AppMetric",
        "controlDomainProfile", "ControlDomainProfile", "controlAdvancedTools",
        "controlDomainScreenCount", "max_nav_all_tweaks"]
for sym in DEAD:
    hits = []
    for p in kt_files:
        txt = open(p, encoding="utf-8").read()
        if re.search(r'\b' + re.escape(sym) + r'\b', txt):
            hits.append(os.path.relpath(p, JAVA))
    if hits:
        problems.append(f"DEAD SYMBOL STILL REFERENCED: {sym} -> {hits}")

# ---------- 5. XML well-formedness ----------
import xml.etree.ElementTree as ET
for f in sorted(glob.glob(os.path.join(RES, "values*", "*.xml"))):
    try:
        ET.parse(f)
    except Exception as e:
        problems.append(f"XML PARSE {f}: {e}")

# ---------- 6. imports declared but symbol unused (new files only) ----------
NEW = ["ui/design/MaxHelp.kt",
       "ui/component/ConfigBackupFlow.kt",
       "ui/subscreens/hubs/MaxDomainHubScreen.kt",
       "ui/subscreens/ConfigBackupScreen.kt", "ui/mainscreens/ControlScreen.kt",
       "ui/subscreens/ChargingScreen.kt",
       "ui/subscreens/ZramManagerScreen.kt",
       "ui/subscreens/NetworkSchedulerScreen.kt",
       "ui/subscreens/CpuCoreControlScreen.kt",
       "ui/mainscreens/LegacyTweakComponents.kt"]
for rel in NEW:
    p = os.path.join(JAVA, "nd/max", rel)
    if not os.path.exists(p):
        continue
    lines = open(p, encoding="utf-8").read().split("\n")
    body = "\n".join(l for l in lines if not l.startswith("import "))
    for l in lines:
        if l.startswith("import ") and not l.rstrip().endswith("*"):
            sym = l.split(".")[-1].strip()
            if sym in ("getValue", "setValue"):
                continue
            if not re.search(r'\b' + re.escape(sym) + r'\b', body):
                problems.append(f"UNUSED IMPORT {rel}: {sym}")

# ---------- 7. nd.max named imports must resolve to a real declaration ----------
# Catches the class of regression that broke the build: a top-level composable is
# deleted, but other files still import it by name. Section 4 only covers a manual
# DEAD list, so this generalises it to every nd.max import in the module.
DECL_RE = re.compile(r'\b(?:fun|val|var|class|object|interface|typealias)\s+(?:<[^>\n]*>\s*)?([A-Za-z_][A-Za-z0-9_.]*)')
TYPE_RE = re.compile(r'\b(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)')

def _pkg_of(txt):
    m = re.search(r'^package\s+([A-Za-z0-9_.]+)', txt, re.M)
    return m.group(1) if m else ""

pkg_decls = collections.defaultdict(set)
all_decls = set()
kt_text = {}
for p in kt_files:
    txt = open(p, encoding="utf-8").read()
    kt_text[p] = txt
    names = {m.group(1).split(".")[-1] for m in DECL_RE.finditer(txt)}
    pkg_decls[_pkg_of(txt)] |= names
    all_decls |= names

# java sources and AIDL-generated interfaces are declarations too
for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if not (f.endswith(".java") or f.endswith(".aidl")):
            continue
        txt = open(os.path.join(dp, f), encoding="utf-8", errors="ignore").read()
        names = {m.group(1) for m in TYPE_RE.finditer(txt)}
        pkg_decls[_pkg_of(txt)] |= names
        all_decls |= names

GENERATED = {"R", "BuildConfig", "Companion"}
for p in kt_files:
    for line in kt_text[p].split("\n"):
        s = line.strip()
        if not s.startswith("import nd.max") or s.endswith("*"):
            continue
        path = s[len("import "):].split(" as ")[0].strip()
        sym = path.split(".")[-1]
        pkg = path.rsplit(".", 1)[0]
        if sym in GENERATED:
            continue
        rel = os.path.relpath(p, JAVA)
        if pkg in pkg_decls:
            if sym not in pkg_decls[pkg]:
                where = "declared in another package" if sym in all_decls else "DECLARED NOWHERE"
                problems.append(f"UNRESOLVED IMPORT {rel}: {path} ({where})")
        else:
            parent = path.rsplit(".", 2)[-2] if path.count(".") >= 2 else ""
            if sym not in all_decls and parent not in all_decls:
                problems.append(f"UNRESOLVED IMPORT {rel}: {path} (no declaration found)")

print("PROBLEMS:", len(problems))
for p in problems:
    print(" -", p)
print("kt files scanned:", len(kt_files), "| R.string refs:", len(refs), "| base strings:", len(base))
