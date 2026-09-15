# VALIDATION

How to verify work in this environment. **A real Gradle build cannot run here** (PATH gradle 4.4.1, wrapper needs 9.5.1, no network). Static gates below are therefore the contract. Never report “build passes” — report “compilation unverified in this environment”.

Run everything from repo root: `/mnt/sdcard/MaxManger/optmize-main`.

## 0. Build attempt (optional, expected to fail offline)

```sh
java -version
cd manager && ./gradlew --offline :app:compileDebugKotlin ; echo "exit=$?"
```
If it fails on distribution download or Gradle version, record the exact message and continue with the static gates.

## 1. Hygiene gates (always)

```sh
git status --porcelain
git diff --check                       # whitespace errors / conflict markers
grep -rn '<<<<<<<\|>>>>>>>' manager/app/src/main/java | grep -v Binary || echo OK
```

## 2. Kotlin structural sanity (per changed file)

```sh
python3 - <<'PY'
import re,sys,subprocess
files=subprocess.run(["git","diff","--name-only","--diff-filter=ACM"],capture_output=True,text=True).stdout.split()
bad=[]
for f in [x for x in files if x.endswith(".kt")]:
    s=open(f,encoding="utf-8",errors="replace").read()
    s=re.sub(r'""".*?"""','""',s,flags=re.S)
    s=re.sub(r'(?<!\\)".*?(?<!\\)"','""',s)
    s=re.sub(r'//[^\n]*','',s); s=re.sub(r'/\*.*?\*/','',s,flags=re.S)
    for o,c in [("{","}"),("(",")"),("[","]")]:
        if s.count(o)!=s.count(c): bad.append((f,o,s.count(o),s.count(c)))
print("UNBALANCED:",bad) if bad else print("balanced OK")
PY
```
Known exception: `AppMonitor.kt` (I-43) fails this naively at HEAD — ignore it unless you edited it.

## 3. Resource gates

```sh
# every strings file parses
python3 -c "import glob,xml.etree.ElementTree as T;[T.parse(p) for p in glob.glob('manager/app/src/main/res/values*/*.xml')];print('xml OK')"

# no duplicate keys inside a values folder
python3 - <<'PY'
import glob,collections,xml.etree.ElementTree as T
for d in sorted(glob.glob('manager/app/src/main/res/values*')):
    names=[e.get('name') for p in glob.glob(d+'/*.xml') for e in T.parse(p).getroot()]
    dup=[k for k,v in collections.Counter(names).items() if v>1]
    if dup: print('DUP',d,dup)
print('dup scan done')
PY

# Arabic parity for keys added in this change (ADR-14)
git diff -U0 -- manager/app/src/main/res/values | grep -o 'name="[^"]*"' | sort -u > /tmp/added.txt
while read -r k; do grep -qr "$k" manager/app/src/main/res/values-ar/ || echo "MISSING_AR $k"; done < /tmp/added.txt
```

## 4. Design-language gates (ADR-06, ADR-07, ADR-08, ADR-14)

```sh
SRC=manager/app/src/main/java/nd/max

# a) no screen you touched may declare its own Scaffold
git diff --name-only --diff-filter=ACM -- "$SRC/ui" | xargs -r grep -ln 'Scaffold(' || echo "no new Scaffold OK"

# b) migrated screens must import the design language
for f in $(git diff --name-only --diff-filter=ACM -- "$SRC/ui/subscreens" "$SRC/ui/mainscreens"); do
  grep -q 'nd.max.ui.design' "$f" || echo "NO_DESIGN_IMPORT $f"
done

# c) no hardcoded user-visible copy in changed UI files
git diff --name-only --diff-filter=ACM -- "$SRC/ui" | xargs -r grep -n 'Text(\s*"' || echo "no literal Text OK"

# d) legacy design system must not be newly imported
git diff -U0 -- "$SRC/ui" | grep '^+.*nd.max.ui.component.MaxDesignSystem' && echo "LEGACY_IMPORT" || echo "no legacy import OK"

# e) adoption counter must go up, never down
grep -rl 'nd.max.ui.design' "$SRC/ui" | wc -l      # baseline 2026-09-15: 6 screens
grep -rl 'Scaffold(' "$SRC/ui" | wc -l             # baseline 2026-09-15: 33 files
```

## 5. Navigation gates (ADR-01, ADR-02) — required for NT-01

```sh
SRC=manager/app/src/main/java/nd/max

# a) the pager model is gone
grep -rn 'use_scroll_animation\|HorizontalPager' "$SRC" || echo "pager removed OK"

# b) no route string literals outside the registry package
grep -rn 'navigate("' "$SRC" | grep -v '/ui/navigation/' || echo "no literal navigate OK"

# c) every registry route is registered in the graph, and vice versa (no orphans/dead routes)
python3 - <<'PY'
import re
reg=open('manager/app/src/main/java/nd/max/ui/navigation/MaxDestinations.kt',encoding='utf-8').read()
graph=open('manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt',encoding='utf-8').read()
routes=set(re.findall(r'route\s*=\s*"([^"]+)"',reg))
built=set(re.findall(r'composable\(\s*([A-Za-z0-9_.]+|"[^"]+")',graph))
print('declared',len(routes)); print('graph entries',len(built))
print('ROUTES_NOT_IN_GRAPH', sorted(r for r in routes if r not in graph))
PY

# d) every screen composable is reachable from the graph
for f in $(grep -rl '^fun .*Screen(' "$SRC/ui/subscreens" "$SRC/ui/mainscreens" 2>/dev/null); do
  n=$(basename "$f" .kt); grep -q "$n" "$SRC/ui/navigation/MaxNavGraph.kt" || echo "UNREACHABLE $n"
done

# e) dead aliases are gone
grep -rn 'maligpufreq\|adrenogpufreq' "$SRC" || echo "aliases removed OK"
```

## 6. Control-plane safety gates (ADR-11)

```sh
SRC=manager/app/src/main/java/nd/max
git diff -U0 -- "$SRC/ui" | grep '^+' | grep -n 'RootFileAccess\|Shell.cmd\|su -c' && echo "UI_DIRECT_WRITE_VIOLATION" || echo "no direct hw write OK"
git diff --name-only -- "$SRC/core" || true    # core changes in a UI task need explicit justification
```

## 7. Unit tests (when a JVM toolchain becomes available)

```sh
cd manager && ./gradlew :app:testDebugUnitTest --tests 'nd.max.core.*'
```
Must stay green: `ControlPlaneArchitectureTest`, `HardwareControlArbiterTest`, `ManualControlLocksTest`, `MinimalPlannerTest`, `ControlOutcomeModelTest`, `DiagnosticCenterTest`.

## 8. Reporting template for the Executor

```
TASK: <id>
FILES: <added / modified / deleted>
GATES: 1 ✓  2 ✓  3 ✓(ar parity: n keys)  4 ✓(adoption 6→N, Scaffold 33→M)  5 ✓  6 ✓
BUILD: not verified — gradle 4.4.1 vs required 9.5.1, offline
RESIDUAL RISK: <what a compiler/device would still need to confirm>
NEXT: <suggested follow-up>
```
