import os, re, sys, glob, collections

ROOT = "manager/app/src/main"
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
    "ui/subscreens/hubs/MaxDomainHubScreen.kt",
    "ui/subscreens/ConfigBackupScreen.kt",
    "ui/mainscreens/ControlScreen.kt",
    "ui/mainscreens/SettingsScreen.kt",
    "ui/mainscreens/ApplistScreen.kt",
    "ui/navigation/MaxDestinations.kt",
    "ui/navigation/MaxNavGraph.kt",
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
NEW = ["ui/design/MaxHelp.kt", "ui/subscreens/hubs/MaxDomainHubScreen.kt",
       "ui/subscreens/ConfigBackupScreen.kt", "ui/mainscreens/ControlScreen.kt"]
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

print("PROBLEMS:", len(problems))
for p in problems:
    print(" -", p)
print("kt files scanned:", len(kt_files), "| R.string refs:", len(refs), "| base strings:", len(base))
