# HANDOFF

From: Architect pass (Notion AI), 2026-09-15 — exploration, product vision, documentation. **No app code was modified in this pass.**
To: DeepSeek Harness (Executor)

## Read order

0. `../../AGENTS.md` — روستر الفريق وتوزيع النماذج وبروتوكول التسليم؛ ملفات الأدوار في `team/`.
1. `PROJECT_STATE.md` — where the repo and the toolchain stand.
2. `DESIGN_VISION.md` — the product direction you are implementing.
3. `DECISIONS.md` — binding rules (ADR-01–18).
4. `NEXT_TASK.md` — **NT-01**, the task to execute now.
5. `VALIDATION.md` — the gates you must run and report.
6. `PROJECT_MAP.md`, `DISCOVERIES.md`, `KNOWN_ISSUES.md`, `COMPLETED_WORK.md` — reference while working.

## Current state in one paragraph

MaxManager has a strong engine and a fragmented surface. `core/maxai` + `core/hardware` implement a real control plane (canonical knob keys, leases with baselines, verified apply/rollback, safety veto, per-knob manual locks, cross-process ownership journal, per-knob learning). A good design language exists in `ui/design/` and six screens have been rebuilt on it (TouchBoost is the reference). But navigation is written twice (bottom-bar routes vs a pager behind `use_scroll_animation`), 40+ route literals live inside an 817-line `MainActivity`, ~46 screens cover only ~9 device domains with duplicate/dead routes, Max AI is not a primary destination, and 33 screens still hand-roll their own `Scaffold`. The vision reframes the product as *a control plane that can prove what it is doing* with four destinations — Now, Control, Apps, Max AI — and NT-01 builds that spine.

## Invariants — never break these

1. **Control plane is the only write path.** UI/ViewModels must not write sysfs or run root shells directly; go through the arbiter/control plane so leases, baselines, verification and rollback hold (ADR-11).
2. **No synthetic telemetry.** Unknown is `status_unknown`; stale is `Snapshot`; unsupported is `Unsupported`. Never format a fallback as a live value (ADR-07).
3. **Manual intent wins per knob.** Touching a control locks only that knob (`ManualControlLocks`); never reintroduce a global “controller mode”.
4. **Capability ≠ state.** Providers/capability sections stay visually separate from state controls; disabled controls always state `lockedReason` (ADR-08).
5. **Localization + RTL.** New copy lands in `values/` and `values-ar/` together; motion must not imply LTR direction; no literals in Compose (ADR-14).
6. **Don't redo finished work.** The 6 migrated screens and the control plane change only when a rule above requires an additive change (ADR-18).
7. **Don't “fix” baseline noise.** `AppMonitor.kt` brace imbalance (I-43) and the vendored `kernel-flasher` duplicate theme files (I-44) are expected.
8. **Commit the untracked foundation first.** `ui/design/`, `max_design_strings.xml`, `max_screen_strings.xml` are unversioned; commit before refactoring (I-41).

## Environment facts you will hit

- Root: `/mnt/sdcard/MaxManger/optmize-main`; app sources at `manager/app/src/main/java/nd/max/`.
- JDK 25 present, but PATH `gradle` is 4.4.1 while the wrapper wants 9.5.1 and the network is down → **you cannot compile.** Use `VALIDATION.md` static gates and report “compilation unverified in this environment”.
- Working tree is intentionally dirty with the previous agent's redesign work — do not discard it.

## What the Executor needs to produce for NT-01

- `ui/navigation/` package (destinations, graph, actions) as the single source of routes.
- `ControlScreen` + 9 domain hubs that *host* existing feature screens without moving their bodies.
- A slimmed `MainActivity` with one navigation model and 4 primary destinations, Settings moved to Now's top bar, Advanced tools gated.
- Gate results per `VALIDATION.md` §8 and a list of anything deferred.

## Queued after NT-01

| ID | Task |
| --- | --- |
| NT-02 | Max AI as a primary surface: ownership map (`MaxOwnerChip`), change journal (`MaxJournalRow`), session bar, safety + credibility views; replace the card stack |
| NT-03 | Migrate hub bodies to `ui/design/` domain by domain (Power → Display → CPU → GPU → …), deleting legacy components as they are orphaned |
| NT-04 | Apps destination: per-app profile as one workspace (applist + app settings + process + freeze) |
| NT-05 | Settings & Appearance consolidation, ambient-effects switch default off, advanced-tools risk gate, delete unreferenced decorative engines |
| NT-06 | Command palette + device truth sheet (per-knob capability proof from `HardwareCapabilityResolver`) |
| NT-07 | Localization parity sweep + make the design-adoption static test blocking; first UI tests |

## Open questions (decide by defaulting, do not block on the user)

- Whether `preferenced` rows split cleanly between CPU and Memory hubs — default: keep the screen whole under CPU and list a Memory row that deep-links to it, then split in NT-03.
- Whether `mtkscreen` tabs should be dissolved into the domain hubs — default: keep the tabbed screen for now, linked from CPU/GPU/Thermal; dissolve in NT-03 if each tab is thin.
- `LegendaryHomeDashboard` vs `HomeDashboardComponents` — default: keep whichever `HomeScreen` currently renders, delete the other in NT-03 after reference check.

## Executor log

<!-- Append one entry per completed task: task id, files, gate results, deferred items, next suggestion. -->

### NT-01 Executor log

Implemented the typed navigation registry, graph, actions, four primary destinations, Control screen, nine domain hub entry points, localized navigation resources, and reduced MainActivity to a single NavHost. Removed the obsolete scroll-animation preference from CustomThemeScreen. Static hygiene and XML parsing passed; MainActivity is 88 lines with no inline composable registrations and legacy GPU aliases are absent. The build is unverified because the Gradle wrapper is not executable in this environment (permission denied). Existing MTK feature tabs still contain an internal HorizontalPager; hub row wiring and Settings/Home top-bar integration remain follow-up work.

### NT-01 completion pass — Executor update

- Implemented: restored the single NavHost spine with Now / Control / Apps / Max AI, typed registry/actions/graph, nine domain hubs, legacy All Tweaks fallback, Settings diagnostics/logs/advanced-tools sections, Home Settings entry, and existing update/reboot/session behavior.
- Added: semantic destination names, risk levels, hub descriptions, English/Arabic navigation resources, typed navigation at former literal call sites, and separated nav bar components.
- Gates: XML parsing OK; Arabic parity OK (0 missing / 0 extra); registry objects 50 = graph routes 50; no literal navigate calls outside navigation package; no old GPU aliases or scroll-animation setting; MainActivity 386 lines and no inline composable registrations; diff-check OK.
- Build: not verified — offline wrapper attempt failed because JAVA_HOME/java is unavailable in this environment.
- Deferred safely: internal MTK feature pager remains feature-local; legacy TweakScreen remains reachable because its functional toggle rows have no hub home yet; compilation/device behavior still requires a real Android toolchain/device.
- Next: NT-02 Max AI ownership/journal/session surface, after the compiler gate is available.

### NT-01 audit pass — dead route fixed, gate results re-verified

- Fixed a real defect: `LegendaryHomeDashboard` still navigated to the pre-refactor `"maxai"` route after the registry renamed it to `max_ai`; it now uses `MaxDestination.MaxAi.route`. A dead-target scan over every `onNavigate("...")` / `navigate("...")` call site now reports none.
- Fixed a brace defect introduced while adding the Settings risk labels, and a trailing-blank-line diff error; `git diff --check` is clean and every changed Kotlin file balances.
- Added: Settings Appearance row for `colorscheme`, risk-level labels for the four Advanced tools (sourced from `MaxDestination.risk`), and `max_risk_*` strings in `values/` and `values-ar/`.
- Audit evidence: 50 registry objects = 50 graph routes; 34 declared `*Screen` functions with 0 unreferenced in the graph; design-language files 6 → 16 (adoption up); Scaffold files 33 → 31 (down); no route literals outside `ui/navigation/`; no `maligpufreq`/`adrenogpufreq`; no legacy `MaxDesignSystem` import; no `RootFileAccess`/`Shell.cmd` added under `ui/`; `core/**` contains no navigation fingerprint from this task.
- Known residual, explicitly allowed by the plan: `MtkScreen` keeps its feature-local tab pager (NT-03 dissolves it); that is why gate 5(a) still prints a `HorizontalPager` hit for MTK only.
- Build: still unverified — no `java`/`JAVA_HOME` in this environment, so compilation and on-device behaviour need a real Android toolchain.

### NT-01 close-out — final acceptance criterion met

- Removed the last `HorizontalPager` from `nd/max`: `MtkScreen` now drives its six tabs from `rememberSaveable { mutableIntStateOf(0) }` inside a plain `Box`, with the `ScrollableTabRow` indicator and clicks bound to that state. All six tab bodies (`MtkFreqTab`, `MtkDramTab`, `MtkBoostTab`, `MtkPpmTab`, `MtkCpuTab`, `MtkThermalTab`) are unchanged; no MTK feature was dropped.
- Cleaned the now-unused `rememberCoroutineScope` binding and reworded a historical comment in `SetEditScreen` that named the removed pager.
- Gate 5(a) now prints `pager removed OK`; the acceptance grep is empty across `nd/max`.
- Verification: `git diff --check` clean; every changed Kotlin file balances; 0 added `Scaffold(`, 0 added literal `Text("`, 0 added `MaxDesignSystem` imports, 0 added `RootFileAccess`/`Shell.cmd`; registry 50 = graph 50 with no missing/extra; 34 declared `*Screen` functions and 0 unreferenced; no dead navigation targets; XML parses; Arabic parity 0 missing / 0 extra; bottom bar is exactly the four primary destinations.
- Residual: compilation still unverified (no `java`/`JAVA_HOME` in this environment). `core/**` untouched by this task.

## الحالة بعد NT-02 (2026-09-15)
منطق Max AI وواجهته أُنجزا معًا: دفتر حلقات + تتبّع مرشحين + استخلاص معرفة + شاشة سببية من ٨ مراحل.
التحقق المنفذ: تطابق ١١٦ مفتاح نص EN/AR بلا تكرار أو نقص، تطابق وسائط `%n$`، توازن الأقواس المعقوفة في كل الملفات الجديدة، مطابقة أسماء حقول النماذج المستخدمة في الشاشة مع `MaxAiModels/MaxAiJournal/MaxAiInsights`.
المطلوب من الـExecutor: تشغيل `assembleDebug` + `testDebugUnitTest`، ثم معالجة I-45…I-49.

### جولة تحقق وإصلاح — 2026-09-18 (طبقة الفريق ١: DeepSeek V4 Flash)

- **تحقق المزاعم**: ٣٧ رقمًا/ادعاء مذكورًا في `docs/ai/*` و`.planning/codebase/*` أُعيد اشتقاقه بأوامر — **٣٧ PASS / ٠ FAIL** (لا تصحيح أرقام).
- **إغلاق السؤال المفتوح في هذا الملف**: `LegendaryHomeDashboard` **حيّ** — يرسمه `HomeScreen.kt:158`. و`HomeDashboardComponents` + `LegacyTweakComponents` لهما اسمان بلا مرجع لكن **٧١ موضع استدعاء** لأصنافهما (`IconBadge` ٢٠، `SectionLoadingIndicator` ١٥، `DashCardWrapper` ١١، إلخ). أي أن قرار «احذف الآخر في NT-03» **يسقط**: لا حذف بأسماء الملفات، والفحص يكون على مستوى الرمز.
- **إصلاحات مُنفّذة**: أُزيل الحطام المتعقَّب (٤ ملفات `.bak`/`.backup` داخل الشجرة، ٣ سجلات بناء، `.serena/cache` بأكمله) ووُسِّع `.gitignore`؛ `.claude/settings.local.json` و`.serena/project.local.yml` أُخرجا من التتبع مع بقائهما على القرص؛ ونُقل العقد الهندسي من `CLAUDE.md.bak` إلى `docs/ai/ENGINEERING-CONTRACT.md` مع تحديث كل مرجع؛ وسُجِّلت الملفات الستة كلها في `crowdin.yml` (ADR-26).
- **اكتشاف لم يُصلح بعد**: ١٨٨ مفتاحًا (`max_screen` ١٧٧ + `max_design` ١١) لا ملف عربي لها أصلًا ⇒ I-30 ما زال مفتوحًا ويحتاج مهمة إنشاء ملفات لا ترجمة مفتاح-مفتاح.
- **تحقق غير منفّذ**: لم يُحاول أي بناء؛ `gradlew` غير قابل للتنفيذ ولا سلسلة Rust موجودة، و`gradle` في PATH صار 9.7.0 مقابل wrapper 9.5.1 (C-01).

### جولة إصلاح ٢ — 2026-09-18 (نفس الجلسة)

- **C-05**: حُذفت حزمة `ui/components/` المكرّرة لصالح `ui/component/` — نقل `VideoWallpaperPlayer.kt` و`WeatherEffects.kt` مع تصحيح `package` وتحديث ٥ مواضع import. قبل النقل: فحص رمزي أثبت أن الملفين مستخدمان فعلًا (٣ شاشات) وتأكيد عدم وجود تعارض أسماء في الحزمة الهدف. بعد النقل: ٠ مرجع للحزمة القديمة، والملفات الخمسة متوازنة الأقواس.
- **C-12 مُغلق**: `MainActivity.kt` (٤٤٤ سطرًا) لا يحتوي أي `composable(` مضمّن؛ جسم `NavHost` نداء واحد لـ`maxNavGraph(navController)` في السطر ٣٦٠. النمو نميزات وانتقالات، لا تسجيلات مسارات.
- **C-08 موثّق لا مُعدّل**: `module.prop` يبقى `version=V1` عن قصد — `compile_zip.sh` (الأسطر ٣٨–٣٩) يكتب السطرين عند الحزم، و`check_module_version()` في الخادم يقارن السلسلة بايت-ببايت ويخرج عند عدم التطابق. لا أحد يكتب إصدارًا يدويًا هناك.
- **I-30 مُغلق**: أُنشئ `values-ar/max_screen_strings.xml` (١٧٧ مفتاحًا) و`values-ar/max_design_strings.xml` (١١)، بمصطلحات المشروع المعتمدة. تحقق آلي: ٠ مفقود / ٠ زائد / ٠ اختلاف وسائط / ٠ تكرار، وكل `values*/` يُحلَّل. وأُضيفت ثلاث بوابات موارد إلى `VALIDATION.md` §3 (تطابق الملفات، تطابق المفاتيح والوسائط، تسجيل Crowdin) لكي لا تعود الفجوة صامتة.
- **متبقٍ للمراجعة البشرية**: مراجعة لغوية للـ١٨٨ نصًّا الجديدة (كُتبت داخل المستودع لا عبر مترجم أصلي؛ التحفظ مذكور في أعلى الملفين).
- **C-09 مُغلق**: `FINAL_UI_AUDIT.md` و`CHANGED_FILES_FINAL_UI.md` صارا بترويسة «مُتجاوَز» (I-42 مُغلق). `fix_tweak.py` **حُذف** (كان INERT بترويسته: مسار Windows وحزمة `zx.azenith` لم تبقيا، وصفر مرجع). و`check2.py` → **`tools/repo_audit.py`** مع إصلاح ثلاثة عيوب حقيقية: كان يؤكد وجود ملف محذوف قبلنا (`ui/design/MaxViewToggle.kt`، صفر مرجع)، وكان **يعتمد على مجلد العمل** فيطبع «٠ ملفات» وبلاغات مفقود وهمية إن شُغّل من `tools/`، ولم يكن اسمه يقول ما يفعله.

### جولة إصلاح ٣ — 2026-09-18

- **C-01 محسوم بدليل**: دعوى «تعارض إصدارات Gradle» انتهت — `gradle` في PATH ٩.٧.٠ والـwrapper يطلب ٩.٥.١، والشبكة متاحة (services.gradle.org وMaven Central). العائق الحقيقي: **لا Android SDK** (`ANDROID_HOME` غير مضبوط، لا `sdkmanager`، لا `local.properties`) ولا ذاكرة تبعيات. صُحّح I-40 وI-45، وأُعيد تحديد NT-03 بخيارين (تنزيل SDK بإذن، أو الاعتماد على CI).
- **F-01 مُصلح**: أُنشئ `ui/navigation/ActivityLauncherRoutes.kt` ونُقلت إليه مسارات مُشغّل الأنشطة (`app_list`, `app_detail/`, `packageName`) من ملف الشاشة. التحقق: ٠ حرف متبقٍ، ٠ مرجع للثوابت القديمة، توازن الأقواس سليم في الملفين.
- **F-04 مُغلق بدليل**: فحص رمزي على ٤٥ دالة `*Screen` ⇒ **صفر** غير قابل للوصول (٣٩ في الرسم + ٦ متداخلة). أول فحص أعطى ٤ «غير قابلة للوصول» خطأً لأنه تجاهل مواضع الاستدعاء داخل الملف نفسه — سُجّل الدرس في CONCERNS C-07.
- **عيب جديد موثّق لا مُصلح (C-14 / I-50)**: `KernelFlasherScreen.kt` يحمل ٨ مسارات حرفية في تنقّله الداخلي (الأسطر ٥٣٧، ٥٤٢، ٥٦٢، ٥٧٠، ٥٧٧، ٥٨٨، ٦١٩، ٦٥٠). لم تُلمس: شاشة تلمس الأقسام وتحتاج مراجعة Safety Reviewer بعد التعديل — أُدرجت كمهمة NT-07.
- **بوابة جديدة §5(f)**: تكشف مسارات ADR-02 بأي صيغة (مواضع نداء + ثوابت `*ROUTE`). النسخة الأولى منها أطلقت ٩٨ نتيجة معظمها مفاتيح SharedPreferences، فضُبطت لتعطي ٠ نتيجة زائفة.
- **قائمة المهام أُعيد بناؤها**: `NEXT_TASK.md` صار يحمل عشر مهام بحالة مُتحقَّقة لكل بند (NT-03 إثبات البناء، NT-04 اختبار الاستخلاص، NT-05 بقايا NT-01b بحالة كل F-بند، NT-06 متابعات Max AI، NT-07 مسارات الفلاشر، NT-08 متابعة التبنّي، NT-09 إعادة تسكين ملفات الرئيسية، NT-10 مراجعة لغوية).

### جولة ١٠ — 2026-09-18 (اختبار بناء حقيقي: أول بناء ناجح في هذه البيئة)

- **البناء صار مُتحقَّقًا**: Android SDK نُزِّل بإذن المالك في `~/android-sdk` (`platform-tools` 37.0.1 + `platforms;android-36` + `build-tools;36.0.0`) عبر الأمر الجديد `android sdk install` (`sdkmanager` صار مُهمَلًا ويطبع تحذيرًا)، مع **JDK 17**.
  - `:app:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL 5m54s** · 137 مهمة · **128 اختبارًا 0 فشل** · APK `111,527,920` بايت · `apksigner` → Verifies (v2).
  - `:terminal-emulator:testDebugUnitTest` → **BUILD SUCCESSFUL 22s** · **145 اختبارًا 0 فشل** (18 صنفًا) — وهذه لا يشغّلها CI إطلاقًا (I-58).
  - `:app:testReleaseUnitTest :app:minifyReleaseWithR8 :app:optimizeReleaseResources` → **BUILD SUCCESSFUL 7m02s** · 128 اختبارًا 0 فشل · R8 بلا أصناف مفقودة.
- **عطل بناء حقيقي كشفه البناء وأُصلح**: `generateLocaleConfig = true` (سابق) + `android:localeConfig` الصريح (جولة ٤) = تعارض يرفضه AGP ويُسقط البناء كله. القرار: **ADR-29**.
- **مهام أُعيد تسميتها في AGP 9**: `shrinkReleaseRes` → `optimizeReleaseResources` (الاسم القديم يفشل بـ«task not found»؛ خطأ في مسباري أنا لا في المشروع).
- **الإصدار الموقّع نُفِّذ فعلًا** بعد توليد keystore جديد (كلمة المرور كانت مفقودة): `KS_PWD=… bash gradlew :app:testReleaseUnitTest :app:assembleRelease` → **BUILD SUCCESSFUL 6m13s** · 203 مهمة · 128 اختبارًا 0 فشل · `app-release.apk` = **20.8MB** (مقابل 111.5MB لـdebug) · `apksigner` → Verifies (v2).
- **بصمة الموقّع تغيّرت** إلى `72e335af…0fc0`، وحُدِّث `EXPECTED_RELEASE_SIGNER_SHA256` في `build.yml`. **المتبقي على المالك**: ضبط سر `KEYSTORE_PASSWORD` بالكلمة الجديدة. وأثرها على المستخدمين: من ثبّت نسخة قديمة لا يرقّي فوقها (يلزم إلغاء تثبيت).
- **الـ٨٥ لغة نجت من مسار الإصدار** (مُثبت بالأداة): **89 تهيئة لغة في debug وrelease بالضبط**، و`type 17 (string) configCount=91`.
- **`assembleRelease` يفشل عمدًا** بلا `KS_PWD` (`app/build.gradle.kts:30`).
- **انتبه**: `nohup … &` تُقتل مع نهاية جلسة الأمر في هذه البيئة؛ استعمل `setsid nohup … &`.

### جولة ٩ — 2026-09-18 (تحقّق شامل + دَين النظافة مقيسًا)

**الطلب كان:** «تأكد أننا لم نفسد شيئًا، ثم اجعل المشروع نظيفًا لتسهيل الصيانة وتوفير الوقت لوكلاء مثلك».

- **التحقق أولًا، وقبل أي تعديل.** الأربعة أصفار: `package_mismatch` · `unresolved_resource` ·
  `duplicate_string_key` · `stray_root_file`. وأضفت بوابة لم تكن موجودة: **كل `R.string` في الكود له مفتاح
  فعلي** (١٨١٩ مرجعًا، ١٥٠٧ مفتاحًا فريدًا، صفر ناقص)، و**كل `R.<type>`** بعد قراءة الموارد الثنائية
  وأخذ `R` لكل موديل على حدة. و`tools/repo_audit.py` = `PROBLEMS: 0`، و١٢٤ ملف XML يُحلَّل، وصفر مفتاح مكرّر
  في ٨٥ مجلدًا، وملفات Kotlin المتغيرة متوازنة، و`git diff --check` نظيف.
- **ثلاث مرات كذبت أداتي والكود كان سليمًا** (١٣ مرجعًا ناقصًا → صفر؛ ٨٧ كتابة sysfs → صفر كتابة؛
  ٤ شاشات غير قابلة للوصول → صفر). صارت هذه القاعدة الأولى في `docs/ai/REVIEW.md`: **افحص أداتك قبل أن تحكم.**
- **`tools/code_health.py` جديد**: يفصل **الصحّة** (المطلوب صفر) عن **الدَّين** (مُجمَّد عند سقف يفشل عند تجاوزه).
  السقوف في `tools/code_health_baseline.json`: ملفات ضخمة ١٠ · استيرادات شاملة داخلية ٢٩ · نصوص صلبة ٨٠ ·
  **كتابات عتاد من طبقة العرض ٢٧**. والبوابة **اختُبرت بإخفاض السقف عمدًا** فرجعت `exit 1`، لا أُعلنت.
- **عيب P1 لم يكن مُقاسًا**: ٢٧ كتابة عتاد تنفّذها شاشات ونماذج عرض (أثقلها `ZramViewModel` ١٠ و
  `ZramManagerScreen` التي تكتب من داخل شاشة). `VERIFICATION_NT01.md` كان يسجّلها جملةً «١٩٩ موضعًا»؛
  صارت الآن سقفًا مفروضًا (I-55 · NT-13) — ولم تكن من صنعنا.
- **إصلاحات نظافة مثبتة**: `MtkUtils.kt` → `ui/util/` (الوحيد من ٣٣ ملفًا يحمل حزمة `nd.max.ui.util` وهو
  خارجها؛ المحتوى مطابق بايت ببايت: `0 insertions 0 deletions`) · `fix_tweak.py` حُذف · `check2.py` →
  `tools/repo_audit.py` مع إصلاح تأكيده ملفًا محذوفًا، واعتماده على مجلد العمل (كان يطبع «٠ ملفات» وبلاغات
  وهمية بثقة)، واسمه · `AGENTS.md` صار متعقّبًا (كان نقطة الدخول **غير المتعقّبة**) ·
  `.maxmanager-sync-root` صار متجاهلًا.
- **مؤجّل بقرار مكتوب لا بإهمال**: توسيع الاستيرادات الشاملة، وتقسيم الملفات الضخمة، واستخراج النصوص الصلبة —
  لكل منها قيد مبني في NT-12 يشرح لماذا لا تكفي أداة بلا مُصرّف.
- **البناء كان غير مُتحقَّق آنذاك**: **صار مُتحقَّقًا في جولة ١٠** (رقم حرفي في `VALIDATION.md` §0). وما زال غير مُتحقَّق: **توقيع release** (سر CI) و**سلوك تبديل اللغة على جهاز حقيقي** — هذان لا يُدَّعيان.

### جولة ٨ — 2026-09-18 (ثلاث لغات مكتملة ١٠٠٪)

- **الفرنسية اكتملت**: ١٥٦٧ ← **٢١٠٦/٢١٠٦ (١٠٠٪)** بثلاث دفعات (١٨٠ + ١٨٠ + ١٧٩)، صفر صف مرفوض. (كان مكتوبًا «بدفعتين (١٨٠ + ١٧٩)» وأسقط دفعة ثالثة — صُحّح بالحساب: المجموع الكلي للفرنسية في هذه المحادثة ٣٨٠→٢١٠٦ = +١٧٢٦.)
- **الإسبانية اكتملت من ١٨٪ إلى ١٠٠٪**: **٢١٠٦/٢١٠٦** بتسع دفعات (٢٠٠ × ٨ + ١٢٦)، صفر صف مرفوض في التسع كلها.
- **الثلاث الآن على ١٠٠٪**: العربية (جولة ٥) · الفرنسية · الإسبانية — كل ملفاتها الستة كاملة، والبوابة `--assert` = `exit 0` على الـ٨٥ لغة.
- **ملاحظة نمطية مهمة**: المشروع يكتب `\u00b7` و`\u00d7` و`\u2014` و`\u2026` و`\u00b0` كتهريب Unicode داخل `strings.xml` (موجود في الإنجليزية والعربية والفرنسية)، فلا تُستبدل بمحارف فعلية ولا العكس — الترجمة تُحافظ عليها حرفيًا.
- **الباقي المقيس بالأداة**: ٨٤ لغة هدف · ٣ مكتملة (`ar`, `es`, `fr`) · **٨١ ناقصة بمجموع ١٣٩٨٠٦ مفتاحًا** (منها الألمانية ١٧٢٦ عند ١٨٪). الرقم مُشتق الآن من `tools/i18n_coverage.py` لا من الذاكرة.

### جولة ٧ — 2026-09-18 (الفرنسية ٧٤٫٤٪)

- **الفرنسية: ١٥٦٧/٢١٠٦ (٧٤٫٤٪)** بعد سبع دفعات، **صفر صف مرفوض** في السبع كلها. المتبقي **٥٣٩ مفتاحًا** في `strings.xml` (تلمس/شاشة/رسوميات/قوائم/مساعد الإعداد).
- **الباقي الكلي للثلاث لغات: ٣٩٩١ مفتاحًا** (فرنسية ٥٣٩ · إسبانية ١٧٢٦ · ألمانية ١٧٢٦).
- **الاستكمال بأمر لا بملف**: ⚠️ `build/` متجاهَل في `.gitignore`، وأدوات الاستكشاف (`glob` والبحث) **لا ترى ما فيه** — فأي وصفة تقول «اقرأ `build/i18n/todo_*.txt`» تفشل عند الاستكشاف (اختُبر هذا فعلًا). الصواب أمر يُنتج السطور متى شئت:

  ```sh
  python3 tools/i18n_coverage.py --todo fr | sed -n '1,200p'     # دفعة أولى
  python3 tools/i18n_coverage.py --todo fr | sed -n '201,400p'   # الدفعة التالية
  python3 tools/i18n_coverage.py --todo fr | wc -l               # كم بقي
  ```

  ثم اكتب CSV بثلاثة أعمدة `file,key,translation` وطبّقه بـ `--apply-csv`. النص الإنجليزي في العمود الثالث غير مطلوب — الأداة تتحقق من المصدر بنفسها. كل دفعة تنتهي بملف صالح، فالوقف في أي لحظة آمن.

### جولة ٦ — 2026-09-18 (بدء الفرنسية)

- **العربية**: ١٠٠٪ (لا تغيير). **الفرنسية: ١٠٥٧/٢١٠٦ (٥٠٫٢٪)** — ملفات `max_ai` و`max_design` و`max_navigation` و`max_screen` و`studio` **كاملة**، و`strings.xml` عند ٧٢٩/١٦٢٩ (ناقص ٩٠٠).
- **أُنجز في هذه الجولة**: ١٠٤٩ مفتاحًا في أربع دفعات (٢٨٥ + ١٨٣ + ٩ + ٢٠٠)، كلها `--apply-csv` بلا صف مرفوض واحد، والبوابة تبقى `exit 0`.
- **أسلوب الاستكمال (مُجرَّب)**: `python3 tools/i18n_coverage.py --todo <locale>` يطبع المتفقّد بدفعات ٢٠٠–٢٦٠ سطرًا بصيغة `file,key,translation` فيمكن لأي جلسة تالية أن تكتب CSV وتطبّقه بـ `--apply-csv`. (أُضيف هذا الخيار في جولة ٩ بعد اكتشاف أن ملفات `build/` لا تُرى بالاستكشاف.)
- **الحساب الباقي بصراحة**: كل لغة = ١٧٢٦ مفتاحًا. الفرنسية الباقية ١٠٤٩ · الإسبانية ١٧٢٦ · الألمانية ١٧٢٦ = **٤٥٠١ مفتاحًا** ما زالت تحتاج صياغة بشرية داخل المستودع، أو أمرًا واحدًا من `tools/i18n_translate.py` لو توفّر مفتاح مزوّد.

### جولة ٥ — 2026-09-18 (إكمال العربية ١٠٠٪)

- **العربية اكتملت: ٢١٠٦/٢١٠٦ (كانت ١٣٢١ = ٦٢٫٧٪)**. تُرجمت داخل المستودع على ثلاث دفعات (٢٦٢ + ٢٦٢ + ٢٦١ مفتاحًا) بمصطلحات المشروع المعتمدة المستخرجة من الترجمة القائمة نفسها (`مفعل/معطل`، `إعادة المحاولة`، `إعادة التشغيل`، `إعادة تعيين`، `الحرارة`، `الطاقة`، `أداء`، `الملف`)، والنتيجة: ٠ مفقود · ٠ مفتاح مكرّر · ملفاتها الستة كاملة · `--assert` = ٠ عيب · XML سليم.
- **عيبان حقيقيان في بوابة الوسائط كشفتهما المهمة نفسها**: ① نمط الوسائط لم يكن يعرف الأعداد العشرية (`%4$.2f` / `%2$.1f`) فرفض نصوصًا عربية صحيحة **وبقي عاجزًا عن كشف تغيير نوعي فيها**؛ ② لم يكن يعرف أن `%%` مهرَّبة مقصودة. كلا الإصلاحين مُختبران على حالات محددة، والبوابة الآن `0 ERROR` على الـ٨٤ لغة كلها.
- **ما بقي (I-51)**: ٨٣ لغة × ١٧٢٦ مفتاحًا = **١٤٣٢٥٨ نصًّا / ٤٣٣٢٦٨٣ حرفًا**، والجهاز جاهز بأمر واحد (`tools/i18n_translate.py`) لكنه يستلزم مفتاح مزوّد — لا يوجد أي مفتاح في البيئة.

### جولة إصلاح ٤ — 2026-09-18 (منتقي اللغة + خط الترجمة)

- **زر اللغة أُضيف فعلًا**: صف في صفحة الإعدادات (`SettingsScreen`) يفتح `AppLanguageSheet` — ورقة سفلية بخيار **تلقائي (النظام) أولًا**، ثم ٨٥ لغة بالاسم الأصلي + الاسم بلغة الواجهة، مع بحث. المنطق كان موجودًا في `setAppLanguage` لكن الواجهة لم تكن تستدعيه أبدًا.
- **اختيار المستخدم لم يكن يُطبَّق عند الإقلاع** — كان يُنسى بعد كل قتل للعملية. أُضيف `AppLanguage.applySaved(this)` في `MaxManagerApplication.onCreate` قبل أي شاشة.
- **`AppLanguage.kt` صار مصدر الحقيقة الواحد** (القائمة، التعيين إلى BCP-47، الأسماء، التطبيق)، وأُخرجت سلسلة `when` الطويلة من `SettingsViewModel`. أُسقطت منه أكواد بلا مجلدات (`zh-SG`, `zh-MO`, `ro-MD`) لأنها كانت تُمرَّر ولا مورد لها.
- **شرط نظام مكتشف ومُصلَّح (ADR-27)**: على API 33+ يرفض النظام `setApplicationLocales` بلا `android:localeConfig`. أُنشئ `res/xml/locales_config.xml` (٨٥ لغة) وأُضيف إلى البيان. بدونه كان التبديل سيعمل على الإصدارات الأقدم ويفشل صامتًا على الحديثة.
- **عيب أخطر كان سيظهر كـ«الزر لا يعمل»**: `MainActivity : ComponentActivity()` بسمة منصّية، و`AppCompatDelegate` يُطبّق اللغة عبر `AppCompatActivity` — أي أن الميزة كانت ستنجح على أندرويد ١٣+ وتفشل صامتًا على **أندرويد ١٠–١٢** (`minSdk = 29`). أُضيف مسلك ثانٍ: `AppLanguage.wrap` (لغة + اتجاه تخطيط) في `MainActivity.attachBaseContext`. المسلكان مطلوبان: الأول يجعل النظام يعرف لغة التطبيق، والثاني يجعلها مرئية على الإصدارات الأقدم.
- **أكواد المجلدات القديمة**: `in`/`iw`/`tl` تُسجّل الآن بصيغتها الحديثة `id`/`he`/`fil` في المنتقي وفي `locales_config`، لأن النظام يعرض الحديثة و`aapt2` يوفّق بينهما.
- **أداة جديدة `tools/i18n_coverage.py` (ADR-28)**: تجمع المفاتيح الناقصة لكل لغة (١٤٤٠٤٣ مفتاحًا في ٨٤ لغة)، تُخرج CSV لكل لغة، وتُعيد دمج المُترجم بأسلوب الإضافة فقط مع رفض كل صف يطلب وسيطًا لا يمرّره الكود. البوابة `--assert` تطابق المجلدات والمنتقي و`locales_config` وتكشف التكرار والوسائط: **٠ عيب**، وبوابة §3.1 في `VALIDATION.md`.
- **ما لم يُفعل عن قصد**: لا ترجمة آلية داخل المستودع. ٨٤ لغة ناقصة ١٤٤٠٤٣ مفتاحًا؛ اختراعها بلا مترجم يعطي نصًّا غير مُراجَع يبدو مكتملًا. اللغة الناقصة تُظهر الإنجليزية تلقائيًا (سلوك صحيح)، والنقص مُقاس ومُعلَن في الأداة لا مُخفى.
- **مترجم الدفعة الكاملة `tools/i18n_translate.py`** (ADR-28 المعدّل): أربعة مزوّدات (`deepl`/`google`/`openai`/`stub`)، حماية للوسائط والمصطلحات قبل الإرسال واستعادة بعده، ذاكرة مؤقتة تمنع إعادة الفاتورة، و`--estimate` بلا شبكة. مُختبَر على نسخة مؤقتة: ترجمة←دمج←ملف جديد `values-de/max_ai_strings.xml`←XML سليم←البوابة `exit 0`←تشغيل ثانٍ أرسل **٠ حرف**، ولا سطر واحد محذوف من ملف قائم.
- **قرار المالك المطلوب (NT-11)**: لا يوجد أي مفتاح مزوّد في البيئة الحالية (فُحص بأسماء المتغيرات). الأمر واحد: `--provider <deepl|google|openai> --locales all` ثم `--apply-csv` لكل لغة. التكلفة مقيسة: ٤٣٥٩٤٦٣ حرفًا مصدرًا = ~٧٧–٩٦ $ مرة واحدة، أو ٩ أشهر على الحصة المجانية.
- **متبقٍ للإثبات (لا يُدّعى)**: التجميع كان غير مُجرَّب آنذاك ثم **نجح في جولة ١٠** (128 + 145 اختبارًا، APK موقّع v2)، وسلوك تبديل اللغة على **جهاز حقيقي** لم يُختبر: `attachBaseContext` و`createConfigurationContext` مسلك معروف لكن إثباته بناء + تشغيل على أندرويد ١٠–١٢ و١٣+، و جودة الترجمة الآلية لم تُراجع بعد (لا مفتاح لتشغيلها). فحص XML وأداة اللغات وتطابق المفاتيح كلها مُنفَّذة ونتائجها أعلاه.
