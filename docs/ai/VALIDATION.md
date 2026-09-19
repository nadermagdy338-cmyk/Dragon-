# VALIDATION

How to verify work in this environment. **البناء صار مُتحقَّقًا هنا (2026-09-18)**: Android SDK مثبَّت في `~/android-sdk`، والبوابات الثابتة أدناه تبقى الفحص السريع، والبناء هو الإثبات النهائي.

Run everything from repo root: `/mnt/sdcard/MaxManger/optmize-main` (على الجهاز) أو `/workspaces/Hi/MaxManager` (في حاوية CI/التطوير).

## 0. Build — مُتحقَّق 2026-09-18 (كان يفشل: لا SDK)

البيئة المقيسة: **JDK 17** (`/usr/lib/jvm/java-17-openjdk-amd64`) · AGP **9.2.0** · Kotlin **2.3.10** · wrapper **Gradle 9.5.1** · `compileSdk 36`.
SDK: `~/android-sdk` = `platform-tools` 37.0.1 + `platforms;android-36` + `build-tools;36.0.0` (نُزِّل بـ`android sdk install`؛ `sdkmanager` صار مُهمَلًا).

```sh
export ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
cd manager
# ① مسار الـPR في CI (debug): اختبارات + APK
bash gradlew -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8" \
  :app:testDebugUnitTest :app:assembleDebug --build-cache --parallel
# ② اختبارات الوحدة غير المشغَّلة في CI (145 اختبارًا)
bash gradlew :terminal-emulator:testDebugUnitTest
# ③ مسار الإصدار بلا توقيع: R8 + تقليص الموارد + اختبارات release
bash gradlew :app:testReleaseUnitTest :app:minifyReleaseWithR8 :app:optimizeReleaseResources
```

**النتائج الفعلية (كلها `BUILD SUCCESSFUL`):**

| الأمر | النتيجة |
| --- | --- |
| `:app:testDebugUnitTest :app:assembleDebug` | **5m54s** · 137 مهمة · **128 اختبارًا، 0 فشل** · APK `111,527,920` بايت |
| `:terminal-emulator:testDebugUnitTest` | **22s** · **145 اختبارًا، 0 فشل** (18 صنفًا) |
| `:app:testReleaseUnitTest :app:minifyReleaseWithR8 :app:optimizeReleaseResources` | **7m02s** · 128 اختبارًا، 0 فشل · R8 بلا أصناف مفقودة |

**تحقّق الـAPK** (كما يفعل CI): `apksigner verify` → `Verifies` ومخطّط v2 = true · `aapt dump badging` → `package: name='nd.max' versionCode='1' versionName='1.0'` · البيان المدمج يحمل `android:localeConfig` · والحزمة تضم `res/xml/locales_config.xml`.

### 0.1 جولة NT-15-MAXAI — 2026-09-18 (تغيير Max AI + واجهته)

```sh
export ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
cd manager
bash gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin   # 2m13s · BUILD SUCCESSFUL
bash gradlew :app:testDebugUnitTest                                   # **160 اختبارًا، 0 فشل، 0 مُتخطّى**
bash gradlew :app:assembleDebug                                       # 2m34s · APK `116,778,525` بايت
```

| الأمر | النتيجة |
| --- | --- |
| `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` | BUILD SUCCESSFUL **2m13s** |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL · **160 اختبارًا، 0 فشل، 0 مُتخطّى** (كان 128) |
| `:app:assembleDebug` | BUILD SUCCESSFUL **2m34s** · APK `116,778,525` بايت |

**عيب حقيقي كشفه البناء**: فاصلة عليا `'` غير مهرَّبة في نصّين إنجليزيين جديدين أسقطت `:app:mergeDebugResources` برسالة `Invalid unicode escape sequence` — **لا بوابة ثابتة تكشف هذا**؛ الدرس يستحق التسجيل: كل تسليم واجهة يُبنى، وتشغيل `aapt2` هو الفحص الوحيد الذي يرى أخطاء XML الحقيقية. الإصلاح: `\'` (نفس نمط `max_live_block_budget` القائم).

**الذي لا يُدّعى هنا**: مراجعة السلامة (I-61) والعرض على جهاز حقيقي (I-60).

### 0.2 جولة FM-02 — 2026-09-19 (إعادة بناء شاشة مدير الملفات)

**تغيير في وضع البيئة، لا في الكود:** هذه الشجرة (`/workspaces/Ai`) لم يكن فيها SDK ولا JDK 17 أصلًا —
بينما `AGENTS.md` §5 يوثّق `~/android-sdk` وJDK 17. أُعيد إنشاء الوضع نفسه **بإذن المالك**، ثم بُني فعلًا:

```sh
sudo apt-get install -y openjdk-17-jdk-headless          # 17.0.20
# SDK: cmdline-tools (11076708) + platform-tools + platforms;android-36 + build-tools;36.0.0 → ~/android-sdk
# manager/local.properties: sdk.dir=$HOME/android-sdk   (وهو في .gitignore فلا يدخل المستودع)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk"
cd manager
bash gradlew :app:compileDebugKotlin --build-cache --parallel -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
bash gradlew :app:testReleaseUnitTest :app:assembleDebug -x :app:lintVitalRelease --build-cache --parallel -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
bash gradlew :app:minifyReleaseWithR8 --build-cache --parallel -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
```

| الأمر | النتيجة الحرفية |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL in 1m 55s** · صفر خطأ · صفر تحذير في ملفات الجولة |
| `:app:testReleaseUnitTest :app:assembleDebug` | **BUILD SUCCESSFUL in 3m 42s** (وقياس أول 8m37s وفيه فشل واحد في اختبار جديد أُصلح) · **679 tests completed, 0 failed, 0 errors, 0 skipped** · APK `118,792,784` بايت في آخر بناء |
| `:app:minifyReleaseWithR8` | **BUILD SUCCESSFUL in 5m 35s** |
| `python3 tools/code_health.py --assert` | `exit 0` · «صحّة نظيفة» · الدَّين `10 / 29 / 66 / 26` (لا ارتفاع عن السقف) |
| `python3 tools/i18n_coverage.py --assert` | `exit 0` · عوائق `0` · تطابق الأكواد الثلاثة OK |
| `python3 tools/repo_audit.py` | `PROBLEMS: 0` |

**ما لا يُدّعى في هذه الجولة:** لم تُفتح الشاشة على جهاز (RTL · حجم خط كبير · لمس · جذر حقيقي)،
ولم تُلتقط لقطة، وشريط التبويبات عند ٦ تبويبات في لوح بعرض ~١٧٠ نقطة لم يُقس. والتوقيع بالإصدار يبقى
غير مُختبَر (يحتاج `KS_PWD`).

**④ الإصدار الموقّع — مُتحقَّق 2026-09-18 بعد توليد keystore جديد:**

```sh
export KS_PWD="$(cat /tmp/keystore-new-password.txt)"   # السر ليس في المستودع
bash gradlew :app:testReleaseUnitTest :app:assembleRelease --build-cache --parallel
```

`BUILD SUCCESSFUL in 6m13s` · 203 مهمة (منها `minifyReleaseWithR8` و`optimizeReleaseResources` و`lintVitalRelease`) · 128 اختبارًا 0 فشل.
`app-release.apk` = **20,780,698 بايت** (مقابل 111,527,920 لـdebug ⇒ R8 والتقليص عاملان) · `apksigner verify` → **Verifies** (v2) · بصمة الموقّع = `72e335af259a9770c79c5e2c66d2f7afbc194492b979444d2acab839022f0fc0` وهي المطابقة لـ`EXPECTED_RELEASE_SIGNER_SHA256` في `build.yml`.

**اللغات تنجو من مسار الإصدار (مُثبت):** `aapt dump configurations` = **89 تهيئة لغة في debug وrelease بالضبط**، و`type 17 (string) configCount=91`. تقليص الموارد يحذف كثافات (127→110 تهيئة) ولا يحذف لغات، ويُعيد تسمية مسارات الملفات (`res/xml/locales_config.xml` → `res/Br.xml` بنفس الحجم 7868) مع بقاء المورد بمعرّفه `nd.max:xml/locales_config`. **درس**: «ملف مفقود» في الحزمة المصغَّرة قد يكون مُعاد التسمية — افحص بجدول الموارد لا بأسماء الملفات.

**ما يبقى غير مُتحقَّق (لا تدّعِه):**
- التوقيع في **CI**: يحتاج ضبط سر `KEYSTORE_PASSWORD` بالكلمة الجديدة (البصمة في `build.yml` حُدِّثت بالفعل).
- البناء بلا `KS_PWD` **يفشل عمدًا**: `KS_PWD must be set to produce a signed release artifact` (`app/build.gradle.kts:30`) — لأن APK غير موقّع غير صالح كـpriv-app.
- سلوك تبديل اللغة على **جهاز حقيقي** (١٠–١٢ و١٣+).
- ملاحظة AGP 9: `shrinkReleaseRes` لم تعد موجودة؛ البديل `optimizeReleaseResources`.

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
# every XML the build reads parses — values*/, res/xml/ and the manifest.
# Not just the strings: res/xml/ holds files the manifest points at, and an XML comment may not
# contain a double hyphen. That rule caught a real defect on 2026-09-18: a locales_config.xml
# comment held the literal flag of a shell command, and aapt2 would have failed the build on it.
python3 -c "import glob,xml.etree.ElementTree as T;f=glob.glob('manager/app/src/main/res/values*/*.xml')+glob.glob('manager/app/src/main/res/xml/*.xml')+['manager/app/src/main/AndroidManifest.xml'];[T.parse(p) for p in f];print(len(f),'xml OK')"

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

# FULL parity — file level: every EN string file must have an AR counterpart
# (this is the gate that would have caught I-30: two files existed in EN only)
for f in manager/app/src/main/res/values/*.xml; do b=$(basename "$f");
  grep -q '<string ' "$f" || continue
  [ -f "manager/app/src/main/res/values-ar/$b" ] || echo "MISSING_AR_FILE $b"
done; echo "AR file parity done"

# FULL parity — key AND format-specifier level, per file pair (ADR-14 + ADR-26)
# The specifier rule is a SUBSET rule, not equality:
#   * AR asks for an argument the caller does not pass  -> CRASH (IllegalFormatException) => ERROR
#   * AR drops an argument that the caller still passes -> ignored by String.format  => WARN
#     (dropping is legitimate: EN uses %2$s as an English plural suffix "s", e.g. detail_readable_paths)
python3 - <<'PY'
import re, xml.etree.ElementTree as ET, os
R = "manager/app/src/main/res"
def load(p):
    return {e.get("name"): "".join(e.itertext())
            for e in ET.parse(p).getroot() if e.get("name")}
spec = re.compile(r'%\d+\$[sd]|%[sd]')
errors, warns = [], []
for f in sorted(os.listdir(f"{R}/values")):
    if not f.endswith(".xml") or not os.path.exists(f"{R}/values-ar/{f}"):
        continue
    en, ar = load(f"{R}/values/{f}"), load(f"{R}/values-ar/{f}")
    miss = sorted(set(en) - set(ar))
    if miss:
        warns.append(f"{f}: {len(miss)} keys not translated yet (coverage, Crowdin's job)")
    for k in en:
        if k not in ar:
            continue
        extra = set(spec.findall(ar[k])) - set(spec.findall(en[k]))
        if extra:
            errors.append(f"{f}:{k} AR needs {sorted(extra)} that callers never pass")
    names = [e.get("name") for e in ET.parse(f"{R}/values-ar/{f}").getroot() if e.get("name")]
    dup = sorted({n for n in names if names.count(n) > 1})
    if dup:
        errors.append(f"{f}: duplicate keys in AR {dup}")
for w in warns:
    print("WARN ", w)
for e in errors:
    print("ERROR", e)
print("key/specifier parity done — ERROR count:", len(errors))
PY

# every EN string file must be registered in the translation pipeline (ADR-26)
for f in manager/app/src/main/res/values/*.xml; do b=$(basename "$f");
  grep -q '<string ' "$f" || continue
  grep -q "values/$b" crowdin.yml || echo "NOT_IN_CROWDIN $b"
done; echo "crowdin registration done"
```

### 3.1 Language gate (ADR-27)

Three artifacts describe the same 85 languages and must not drift apart: the shipped folders
(`res/values-*`), the in-app picker (`AppLanguage.CODES`), and the system-visible list
(`res/xml/locales_config.xml`, required by `setApplicationLocales` on API 33+). One command
compares all three and, at the same time, re-checks every locale for specifier and duplicate-key
defects. Coverage numbers are printed but never fail the gate — translating 84 locales is
Crowdin's job, not a build blocker.

> **Why the batch command exists (`--todo`)**: the handoff recipe used to say "read
> `build/i18n/todo_<locale>.txt`". `build/` is gitignored, and **`glob` and search do not see it** — we tested
> it. So that instruction was unrunnable for any fresh agent, and the file could not be discovered even by
> accident. It is a command now. The same reasoning applies to every tool here: each resolves the repo root
> from `__file__`, because a gate that silently returns `0` when run from the wrong directory is worse than no
> gate at all.

```sh
# ERROR (exit 1) only for real defects: specifier a locale asks for but the code never passes,
# duplicate keys, or the three language lists disagreeing.
python3 tools/i18n_coverage.py --assert

# what each locale still misses, per file, with the English source text
python3 tools/i18n_coverage.py            # coverage table for all 84 locales
python3 tools/i18n_coverage.py --locale de          # one locale, key by key

# a translation batch, straight to stdout — NOT a file under build/
python3 tools/i18n_coverage.py --todo de | sed -n '1,200p'
python3 tools/i18n_coverage.py --todo de | wc -l    # how many are left (de = 1726)
python3 tools/i18n_coverage.py --write-manifests    # build/i18n/to_translate_<locale>.csv (all locales)

# merge machine/human translations back — append-only, validated before writing, --dry-run first
python3 tools/i18n_coverage.py --locale de --apply-csv build/i18n/to_translate_de.csv --dry-run
```

Filling all 84 locales is a separate, explicit step with a named provider (ADR-28, amended 2026-09-18).
No key is needed to see the cost first, and the provider's output never lands in the resources directly:
it goes through the same `--apply-csv` gate above.

```sh
python3 tools/i18n_translate.py --estimate                      # 144,043 strings / 4,359,463 source chars, no calls
python3 tools/i18n_translate.py --provider deepl --locales all # DEEPL_API_KEY, resumable via build/i18n/cache/
python3 tools/i18n_translate.py --provider openai --locales ar,de --limit-keys 200   # MT_API_KEY + MT_BASE_URL + MT_MODEL
```

Placeholders and the terms in `tools/i18n_glossary.csv` are swapped for sentinels before the request and
restored after it, so no engine can renumber or drop `%1$s`. Verified: `protect()`/`restore()` round-trips
byte-identically, a second run sends 0 characters (cache hit), and a merged file gained exactly the new
keys with zero deleted lines.

The specifier pattern itself was corrected on 2026-09-18 while translating Arabic, and both fixes matter
more than they look:

- **Decimal specifiers are real** — `%4$.2f`, `%2$.1f`, `%1$.1f`. The first pattern only knew `%n$s` / `%n$d`,
  so it rejected correct translations containing them **and could not see a type change inside one**.
- **`%%` is an intentional escaped percent**, not a lone `%`. A lone `%` is still an error: it becomes a
  conversion specifier the moment the string reaches `String.format`.

The pattern is `%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[sdfoxegX]` — it deliberately cannot start with a space, so a
plain percentage in prose ("100% من المساحة") is never read as a placeholder.

`--apply-csv` never rewrites an existing file: it appends new keys before `</resources>` and rejects
any row whose specifiers the caller does not pass, whose key already exists, or which duplicates a
key inside the same batch.

Two translation traps the gate cannot catch, so they are conventions instead — both were hit while
finishing Spanish in round 8:

- **Unicode is written as an escape, not as a character.** `strings.xml` uses `\u00b7` (·), `\u00d7` (×),
  `\u2014` (—), `\u2026` (…) and `\u00b0` (°) across English, Arabic and French alike. Android does resolve
  `\uXXXX` in string resources, so a translation must **copy the escape verbatim** — replacing it with the
  literal character silently changes what is compared, and dropping it drops a separator or a degree sign.
- **A row the gate rejects is not always a bad row.** Before translating, read the *English* source of the
  key, not the batch you are writing: `%%` (escaped percent) and mixed-script rows (`%1$d%% available · …`)
  look wrong in isolation and are correct. Verified on a temp copy: a 380-key `values-de/strings.xml` gained exactly
one line (`diff` = `382a383`) with `'` and `&` correctly escaped.

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

# f) route literals in ANY form outside ui/navigation (ADR-02).
# Gate (b) only saw navigate("..."), so `const val X_ROUTE = "app_detail/"` and
# `composable("error/{m}")` slipped through. Two precise rules, no false positives:
#   * inline literals at a navigation call site
#   * constants whose NAME says ROUTE (catches the F-01 class)
# Deliberately NOT matched: pref keys / notification channels / tags — a value-based regex
# produced ~90 false positives out of 98 hits when this gate was first written.
python3 - <<'PY'
import re, os
SRC = "manager/app/src/main/java/nd/max"
NAV = os.path.join(SRC, "ui/navigation")
findings = []
for root, _, files in os.walk(SRC):
    if os.path.commonpath([root, NAV]) == NAV:
        continue
    for f in files:
        if not f.endswith(".kt"):
            continue
        p = os.path.join(root, f)
        for i, line in enumerate(open(p, encoding="utf-8"), 1):
            s = line.strip()
            if re.search(r'navigate\(\s*"|composable\(\s*"|startDestination\s*=\s*"', line):
                findings.append(f"{p}:{i}: {s[:80]}")
            elif re.search(r'\bconst val [A-Z0-9_]*ROUTE[A-Z0-9_]*\s*=', line):
                findings.append(f"{p}:{i}: ROUTE-CONST {s[:80]}")
print("ROUTE_LITERALS_OUTSIDE_NAV:", len(findings))
for x in findings:
    print("  ", x)
PY

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
# (a) مهمة الواجهة تفحص ما أضافته هي فقط — بوابة الـdiff
git diff -U0 -- "$SRC/ui" | grep '^+' | grep -n 'RootFileAccess\|Shell.cmd\|su -c' && echo "UI_DIRECT_WRITE_VIOLATION" || echo "no direct hw write OK"
git diff --name-only -- "$SRC/core" || true    # core changes in a UI task need explicit justification

# (b) دَين قائم يُقاس ولا يُسمح له بالنمو — السقف ليس صفرًا لأن الميراث ليس صفرًا
python3 tools/code_health.py --assert           # presentation_hw_writes ≤ 27
```

### 6.1 لماذا بوابة (b) رغم وجود (a)

بوابة (a) تقرأ **الـdiff** فقط، فهي تمنع الانتهاكات الجديدة لكنها لا ترى الميراث ولا تستطيع أن
تقول كم بقي منه. و`docs/ai/VERIFICATION_NT01.md` سجّل «١٩٩ موضعًا مباشرًا في `ui/**`» كجملة
بلا رقم قابل للفرض. بوابة (b) تحوّل الجملة إلى **سقف يفشل البناء عند تجاوزه** صحيحًا، والفرق بين
الرقمين مقصود ومكتوب: `199` = كل موضع `RootFileAccess`/shell في `ui/**` (بما فيه القراءات التي
تسمح بها ADR-11)، و`27` = الكتابات الفعلية من طبقة العرض بعد استثناء `ui/util/` والتعليقات.

### 6.2 مسبار خاطئ يجب ألا يُستخدم

`grep -rn '/sys/' "$SRC/ui"` يعطي **٨٧ نتيجة ليس فيها كتابة واحدة** — معظمها `readSysFile` و`cat /sys/...`،
وADR-11 تحكم **الكتابة** لا القراءة. ثلاثة أخطاء وقعت في هذا المسبار بالترتيب: ① مطابقة `>` أطلقت
`2>/dev/null` كأنه كتابة، ② وصف الحالة داخل KDoc عُدّ كودًا، ③ لا تصنيف بين عقدة عتاد وملف إعداد في
`/data/adb/.config/MaxManager/`. الفاحص الآن يستثني الثلاثة صراحةً (`COMMENT_LINE`, `SHELL_WRITE`).

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
