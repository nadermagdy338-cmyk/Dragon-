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

### AR-RESEARCH-2 — **فهرسة المستودعات** وتصحيح ادّعاء خاطئ — 2026-09-18

**TASK:** AR-RESEARCH-2 (medium) — `DONE_WITH_CONCERNS`
**FILES:** `docs/ai/EXTERNAL-RESEARCH-APP.md` (+§١٣ كاملة، وتصحيح §١٢) · `docs/ai/EXTERNAL-RESEARCH.md` (تصحيح §٩) ·
`docs/ai/NEXT_TASK.md` (`NT-37..NT-41`) · `docs/ai/KNOWN_ISSUES.md` (`I-69` مُصحَّح + `I-63` مُصحَّح + `I-70` جديد)

**أهم ما في هذا التسليم ليس فكرة، بل تصحيح:** جولتاي البحث السابقتان كتبنا فيهما أن **أداة
فهرسة مستودعات GitHub غير متاحة في هذه الجلسة**. تحقّقتُ هذه المرة بدل تكرار الادّعاء فوجدت
أن **واجهة GitHub البرمجية تعمل** وأن الادّعاء **خطأ مني**. فنُفِّذت الفهرسة فعلًا، ثم
**صُحِّح الادّعاء في مكانه** (لم يُمحَ) في الملفّين والسجلّين.

**الحصيلة المقيسة للفهرسة:** **٥٧ استعلامًا** ⇒ **٢٩٢٢ سجلًا** جُلب وفرُز ⇒ **٢٥٤٦ مستودعًا
فريدًا** (والكون المُعلن لتلك الاستعلامات **٣٩٥٦٣**). القراءة العميقة كانت على مجموعة أصغر،
وهذا لا يُخلط بالفهرسة في أي رقم أعلاه.

**درسان مذكوران للجولة القادمة (`I-70`):** ① استعلام بكلمتين ⇒ ٤٧٩٨ نتيجة، وبأربع كلمات تقنية
⇒ **صفر** (GitHub يطلب كل الكلمات) ⇒ النافع: `topic:` + كلمات قليلة، ثم فرز بالوصف.
② الكون مُلوَّث: استعلامات الحشو تجلب مستودعات بآلاف النجوم لا صلة لها بمجالنا ⇒ **الفرز
بالمفردات لا بالشهرة**. ③ أعلى مصدر عائد لم يكن مستودعًا بل **قائمتين منسَّقتين** تعملان
كأداة تعداد لمئات التطبيقات.

**أهم ما كشفته الفهرسة (وعجزت عنه الجولة السابقة):**
- **AR-20 طبقة امتياز ثانية (Shizuku):** جزء معتبر من أدواتنا (الكثافة، DNS، تعطيل حزم، AppOps،
  بطارية، سجلات) **لا يحتاج جذرًا** — لكن معمارنا يطلب جذرًا دائمًا. **النظير الصريح:** واحد من
  أقوى إشارات المجال: `awesome-shizuku` (١٠١٩٢★) وقائمة أدوات لا تحتاج جذرًا.
- **AR-21 مفتاح يحمي نفسه:** النظير `amply` يُعيد حدّ الشحن الحامي **تلقائيًا** بعد شحنة واحدة.
  وهذا هو النمط الذي يمنح **AR-19** شكلًا قابلًا للتنفيذ (تعطيل الحرارة المؤقت).
- **AR-32 تشخيص مربوط بالدليل** (نمط `SmartPerfetto`) وكشف انسداد الخيط الرئيسي بلا مخرجات مسبقة.

**GATES:** `code_health --assert` = exit 0 (الدَّين **لم ينمُ**: 10 · 29 · 80 · 27) ·
`i18n_coverage --assert` = exit 0 · `repo_audit` = `PROBLEMS: 0` · `git diff --check` نظيف.
**لا كود منتج ولا نص جديد** ⇒ لا بناء ولا ترجمة. والفهرس المؤقت كُتب في `/tmp/ghq/` **خارج
المستودع** عمدًا (لا شيم في `build/` ولا حطام في الجذر).

**RESIDUAL RISK:** الفلترة كانت على **الوصف والمواضيع** لا الكود لـ٢٥٤٦ مستودعًا · ولم تُقرأ
READMEs لأكثر من حفنة منها · واستعلامات `topic:android-performance`/`topic:android-tweaks`
أعطت كونًا صغيرًا (٤٤/٨) أي أن مجالات كاملة قد تكون تحمل مواضيع أخرى لم أجرّبها ·
**وAR-20 (Shizuku) قرار معماري كبير يحتاج قرار مالك قبل أي تنفيذ.**

**NEXT:** `NT-37` يحتاج قرار (طبقة Shizuku: نعم/لا/جزئية) · و`NT-26`/`NT-27` (صفحة الإنقاذ وتاريخ
الإقلاع) ما زالا **قابلين للبدء فورًا** لأنهما قراءة فقط بلا امتياز.

### AR-RESEARCH — بحث خارجي على مستوى **التطبيق كله** + ثلاث دقائق مُتحقَّقة — 2026-09-18

**TASK:** AR-RESEARCH (medium) — `DONE_WITH_CONCERNS`
**FILES:** `docs/ai/EXTERNAL-RESEARCH-APP.md` (جديد، بنك `AR-01..AR-19` + `AR-R1..R6` مرفوضة) ·
`docs/ai/NEXT_TASK.md` (+قسم البنك، `NT-26..NT-36`) · `docs/ai/KNOWN_ISSUES.md` (`I-66..I-69`)

**ما تغيّر فعلًا:** جولة بحث ثانية — لكن على الطبقة التي لم تُبحَث: **التطبيق** لا المحرك.
والأهم: الجولة لم تبدأ بالمصادر بل بالكود، فأنتجت **ثلاث دقائق بأدلة أوامر** لا انطباعات:

1. **`I-66` (P1):** مفتاح تعطيل الحرارة **مُوصَّل بالواجهة** (`PreferencedTweakScreen.kt:320`)
   ويسلسل إلى كتلة `DISABLE THERMAL` التي تقتل `thermald` وتُعطّل مناطق الحرارة وسياسات MTK PPM
   وحدود GPU الحرارية — أي أن المستودع يحمل في كتبه `XR-R4` **مرفوضًا** بقاعدة AOSP، وفي شيفرته
   مفتاحًا يخالفه. **لم أُعدّل المفتاح ولم أحذفه**: ذلك قرار مالك + مراجعة سلامة، وقد سُجِّل ليُتخذ صراحةً (`NT-30`).
2. **`I-68` (P2):** الإنقاذ موجود ونتيجته غير مرئية — `package-recovery.log` يُكتب و**عدد قارئيه صفر**،
   و`count.sh`/`disable`/`module.prop.orig` لا يقرأها التطبيق. أي أن الوحدة تنجح في منع الكارثة وتفشل في الإبلاغ عنها.
3. **`I-67` (P2):** هوية الإصدار في ثلاثة أماكن متفرّعة (`version` · `update.json` · `module.prop` — والأخير
   مشتقّ وقت التحزيم ومع ذلك هو ما يقرأه التطبيق)، و`zipUrl`/`updateJson` فارغان ⇒ لا فحص تحديث ممكن أصلًا.

**النمط المكشوف (وهو الفائدة الحقيقية):** أعلى قيمة في هذه الجولة ليست قدرة جديدة — بل **قراءة وحوكمة**.
٥ من أول ٥ بنود موصى بها **لا تكتب عتادًا**. ومرتبة أولًا: **صفحة الإنقاذ داخل التطبيق** (`NT-26`)
— أرخص بند وأعلاها قيمة، وتعالج مفارقة أن التطبيق يلامس الإقلاع والحرارة ولا يشرح كيف يُصلح جهازه.

**الدعوى القابلة للتكذيب:** كل بند في §١ من الملف يمكن إعادة إنتاجه بأمر واحد مذكور معه —
ومن ينكر `I-66` عليه أن يُكذّب سلسلة الاستدعاء، لا أن يناقش الرأي.

**GATES:** `code_health --assert` = exit 0 (والدَّين **لم ينمُ**: 10 · 29 · 80 · 27) ·
`i18n_coverage --assert` = 0 عيوب · `repo_audit` = `PROBLEMS: 0` · `git diff --check` نظيف.
**لا كود منتج في هذا التسليم** ⇒ لا بناء ولا اختبارات مطلوبة، ولا نص جديد ⇒ لا ترجمة.

**RESIDUAL RISK:** لم تُفهرس ١٠٠٠ مستودع (١٦ استطلاعًا + ٦ قراءات كاملة، وأداة فهرسة GitHub غير متاحة
— `I-69`) · بند واحد لم أتحقّق منه بالكود (حقول البطارية في `DashboardDetailScreens`) وقد كُتب
**«يُتحقَّق قبل التنفيذ»** ولم يُقدَّم كفجوة · **و`I-66` بلا حكم سلامة مستقل، وهو بند سلامة لا بند راحة.**

**NEXT:** `NT-30` يحتاج **قرار مالك** (حوكمة المفتاح: حذف/تقييد/إبقاء) قبل أي تنفيذ؛ والمستقلّ عنه
`NT-26` (صفحة الإنقاذ) و`NT-27` (تاريخ الإقلاع) قابلان للبدء فورًا لأنهما قراءة فقط.

### NT-19-PSI — قياس خنق الذاكرة بدل نسبة الامتلاء — 2026-09-18

TASK: NT-19-PSI (medium) — DONE_WITH_CONCERNS.
FILES: جديد `core/maxai/MemoryStall.kt` (محلّل PSI نقي + عقد القدرة) · جديد `core/hardware/MemoryPressureReader.kt` (قارئ بتخزين سلبي للقدرة) · جديد `test/.../MemoryStallTest.kt` (١٥ اختبارًا) · جديد `test/.../MemoryPressureReaderTest.kt` (٦) · معدّل `core/hardware/DeviceStateCollector.kt` (إشارة ثامنة `memoryStallFraction`) · `core/maxai/Objective.kt` (مانع الرفع) · `core/maxai/MaxAiModels.kt` (`memoryStallPercent`) · `core/maxai/MaxAiEngine.kt` · `ui/mainscreens/MaxAiScreen.kt` (صف جديد) · EN/AR `max_ai_strings.xml` (مفتاحان لكل لغة) · `docs/ai/{DECISIONS,HANDOFF,KNOWN_ISSUES,NEXT_TASK}.md`. لا تبعيات جديدة، ولا كتابة عتاد، ولا تغيير في `core/hardware` غير قراءة ملف، ولا مساس بمسار التوقيع.
WHAT: (١) **قياس التوقف بدل الامتلاء** — `full avg10` من `/proc/pressure/memory` (نفس ما بنى عليه `lmkd` قراره)، فالرقم يقيس الأثر لا النسبة. (٢) **مانع واحد وجّه واحد: الخنق يمنع رفع الأداء فقط** — الدعوى: لا يقلب شيئًا في الاتجاه المعاكس ولا يعمل بلا قياس (٤ اختبارات في `ObjectiveTest`). (٣) **التخزين السلبي للقدرة** — نواة أعلنت عدم الدعم لا تُسأل إلا كل ١٠ دقائق، لأن `RootFileAccess` يجرّب قشرة عند فشل القراءة المباشرة. (٤) صف في الشاشة يعرض الرقم بمعناه الزمني أو «غير معروف» (ADR-07).
GATES: `code_health --assert` = exit 0 (الأربعة أصفار، والدَّين **لم ينمُ**: 10 · 29 · 80 · 27) · `i18n_coverage --assert` = 0 عيوب والأكواد الثلاثة متطابقة (84+en · 85 · 85) · `repo_audit.py` = `PROBLEMS: 0` (246 ملفًا · 1554 مرجعًا · 2163 مفتاحًا) · `git diff --check` نظيف.
BUILD: **مُتحقَّق بأمر** (JDK 17 + `ANDROID_HOME=~/android-sdk`): `:app:testDebugUnitTest` = BUILD SUCCESSFUL · **216 اختبارًا، 0 فشل، 0 مُتخطّى** (كان ١٩٢) · `:app:assembleDebug` = BUILD SUCCESSFUL · APK `116,787,905` بايت.
BUILD CAUGHT A REAL DEFECT: اختبار واحد فشل فكشف **تكرار نفس عيب ADR-33** في مكان آخر — `lastProbeAtMs = 0L` كان علامة «لم يُستقصَ بعد»، والصفر لحظة صحيحة، فأول قراءة كانت تُمنع ١٠ دقائق على ساعة تبدأ من الصفر. أُبدل الحقل بـ`Long?` وأُضيف اختبار حدّ صريح (`a reader that never probed asks immediately, even at a zero clock`). العيب نفسه في وحدة أخرى يعني أن القاعدة كانت موجودة **ولم تُعمَّم** — وهذا ما فعله هذا الإصلاح.
RESIDUAL RISK: (١) **مسار القراءة على جهاز غير مُجرَّب** وصلاحية `/proc/pressure/memory` بلا جذر تختلف بين الإصدارات (I-65). (٢) عتبة `STALL_FRACTION = 0.10` قيمة تصميمية غير معايَرة. (٣) الرقم يُقاس لكنه لا يغذّي جدوى المرشحين بعد — ذلك يحتاج تصنيف العنق (NT-23). (٤) عرض الصف الجديد في RTL/خط كبير غير مُتحقَّق بصريًا (لا مُشغّل). (٥) **مراجعة Luna حاجزة كما هي** (I-61) — والـdiff هذه المرة يمسّ `Objective.preferredDirection` أي **مسار القرار** لا القياس وحده.
NEXT: NT-20 (`getThermalHeadroom`) يكمل عائلة الإشارات الحقيقية، ثم NT-23 (تصنيف العنق) الذي يجعل هذه الإشارة تغيّر جدوى المرشحين لا المانع وحده.

### XR-05-BUDGET — ميزانية الحرارة + ذيل الأثر المقيس — 2026-09-18

TASK: XR-05-BUDGET (medium) — DONE_WITH_CONCERNS.
FILES: جديد `core/maxai/MaxAiThermalBudget.kt` · جديد `core/maxai/MaxAiThermalBudgetTest.kt` · معدّل `core/maxai/MaxAiInsights.kt` (توزيع الأثر `DeltaSpread`) · `core/maxai/MaxAiModels.kt` · `core/maxai/MaxAiEngine.kt` · `ui/mainscreens/MaxAiScreen.kt` · EN/AR `max_ai_strings.xml` · جديد في `MaxAiInsightsTest.kt` (٦ اختبارات توزيع) · `docs/ai/{DECISIONS,NEXT_TASK,HANDOFF}.md`. لا تبعيات جديدة، ولا تغيير في `core/hardware` أو مسار الكتابة أو التوقيع.
WHAT: (١) **ميزانية الحرارة (XR-05)</strong> — الزمن من بدء الحمل الثقيل إلى أول خنق، وهو المقياس الذي اقترحته أدبيات التخفيف الحراري نفسها. يُقاس من نفس القياس المجاني بلا كتابة جديدة، وجلسة كاملة بلا خنق تُعرض «جلسة نظيفة» (أفضل دليل) لا رقمًا مُقدرًا. (٢) **ذيل الأثر** — `worst/p10/median/best` للقرارات المقيسة، مع استبعاد التجارب المعرفية وحلقات السلامة من التوزيع، وبترتيب الأقرب لا بالاستقراء: لا رقم مُصنَّع بين عينتين. (٣) عرض القسمين في شاشة Max AI مع نصوص EN/AR مزدوجة.
GATES: `code_health --assert` = exit 0 (الأربعة أصفار، والدَّين كما هو: 10 · 29 · 80 · 27) · `i18n_coverage --assert` = 0 عيوب والأكواد الثلاثة متطابقة · `repo_audit.py` = `PROBLEMS: 0` · `git diff --check` نظيف.
BUILD: **مُتحقَّق بأمر** (JDK 17 + `ANDROID_HOME=~/android-sdk`): `:app:testDebugUnitTest` = BUILD SUCCESSFUL · **192 اختبارًا، 0 فشل، 0 مُتخطّى** (كان ١٧٦) · `:app:assembleDebug` = BUILD SUCCESSFUL · APK `116,786,917` بايت · `apksigner verify` = exit 0.
BUILD CAUGHT A REAL DEFECT: اختباران وثلاثة إخفاقات على `MaxAiThermalBudget` كشفت أن `sessionStartedAtMs = 0L` كان يُستعمل كعلامة «لا جلسة»، والصفر لحظة زمنية صحيحة — فقراءة ساعة عند الصفر كانت **تُلغي الجلسة صامتةً** بلا خطأ ظاهر. أُبدلت العلامة بحقول `Long?` فلا يُخلط الغياب برقم. (الدرس: علامة مُشفَّرة في مدى القيمة الصحيح عيب، لا مسألة ذوق.)
RESIDUAL RISK: (١) ثوابت `MIN_SESSION_MS = 30s` و `DEMANDING_INTENT = 0.75` قيم تصميمية **غير معايَرة على جهاز**. (٢) عتبة «حمل ثقيل» مشتقة من `appIntent` المُقدَّر لا من FPS/إطارات حقيقية — فميزانية جلسة مصنّفة خطأً تُقاس خطأً. (٣) لا إثبات بصري لصفوف الميزانية والذيل (RTL/خط كبير) — I-60. (٤) **مراجعة سلامة Luna لا تزال حاجزة** (I-61): الـdiff يمسّ `core/maxai`.
NEXT: مراجعة مستقلة (AGENTS.md §4) على هذه الدفعة، ثم NT-19 (PSI) الذي يفتح NT-23 (تصنيف العنق).

### XR-RESEARCH — بحث هندسي خارجي + منحنى السقف الحراري — 2026-09-18

TASK: XR-RESEARCH (large) — DONE_WITH_CONCERNS.
FILES: جديد `docs/ai/EXTERNAL-RESEARCH.md` (بنك أفكار بـ ٢٦ فكرة + ٧ أفكار مرفوضة بتعليلها) · جديد `core/maxai/MaxAiThermalCurve.kt` · معدّل `core/maxai/SafetyEngine.kt` · جديد `core/maxai/MaxAiThermalCurveTest.kt` · `docs/ai/{DECISIONS,NEXT_TASK,HANDOFF}.md`. لا تبعيات جديدة، لا كود منسوخ من أي مشروع، لا تغيير في `core/hardware` أو مسار الإصدار.
SOURCES: نواة لينكس (`power_allocator`/IPA, PSI, uclamp) · AOSP (thermal mitigation, Game Mode/boost, jank capacity) · Android Developers (ADPF Thermal API, FrameTimeline/Perfetto, memory guide) · مبادئ متحكمات عملية (AutoTDP في g-helper، بلا تمثيل المقارنة) · Encore Tweaks (مكرَّم في `NOTICE.md`) · مستكشفات عتاد من فضاء المستخدم (KGSL/Adreno). الروابط كلها في الملف.
FINDINGS (الخلاصة المقارنة): **٤ محاور متقدّمون فيها** (سرد القرار، التعلّم لكل تطبيق، مسار الكتابة المحكوم، مفردات المقابض المفردة) · **١ متعادل** · **٤ متأخرون** (الاستجابة الحدثية · عتبات لكل طراز · مفردات المنح `uclamp` · قياس توقف الذاكرة PSI بدل نسبة الامتلاء). أُضيفت كمهام NT-17…NT-25 بأرقام الأفكار.
IMPLEMENTED (XR-01+XR-02+XR-03 ⇐ ADR-32): منحنى سقف متناسب مع التجاوز بدل سقفين ثابتين، بند تكاملي يشدّد ولا يرخي، شرط تبريد متتالٍ قبل الاسترجاع، تقريب ٥٪ وحد كتابة واحد/١٥ث. **الدعوى القابلة للتكذيب:** المنحنى لا يعطي سقفًا أخفّ من السقف القديم في أي نقطة على النطاق ٤٨°–٦٤°، وعند الحدّين بالضبط — متحقّق بـ١٤ حالة JVM.
GATES: `code_health --assert` = exit 0 (الأربعة أصفار، والدَّين عند سقفه) · `i18n_coverage --assert` = 0 عيوب · `repo_audit.py` = `PROBLEMS: 0` · `git diff --check` نظيف.
BUILD: `:app:compileDebugKotlin :app:testDebugUnitTest` = BUILD SUCCESSFUL · **176 اختبارًا، 0 فشل، 0 مُتخطّى** (كان ١٦٠).
BUILD CAUGHT A REAL DEFECT: اختبار واحد فشل بشأن عقد `isTighterByStep` — القياس على القيمة بعد التقريب كان يجعل فرق ١/١٠٠٠٠ كافيًا لكتابة عتاد. أُصلح الشرط ليُقاس على القيمة الحقيقية (مُسجَّل في تعليق الدالة).
RESIDUAL RISK: (١) **مراجعة سلامة Luna أصبحت أوسع**: هذا الـdiff يمسّ سلوك التبريد فعلًا (كان ADR-30 توقيتًا فقط) ⇒ I-61 تبقى P1 وحاجزة. (٢) كل ثوابت المنحنى (كسب التكامل 0.0015، الأرضية 0.25، خطوة ٥٪، ١٥ث) **غير معايَرة على جهاز** — لم يُقس أثرها الحراري ولا كلفتها على الأداء. (٣) البحث لم يُفهرس ١٠٠٠ مستودع كما طُلب: ~٢٠ استطلاعًا مُوجَّهًا و٦ قراءات مرجعية كاملة، والحدّ مكتوب في §٩ من الملف. (٤) كل فكرة غير مُنفَّذة في البنك هي **فكرة مُقترحة لا ميزة قائمة** — ولا يجوز عرضها كقائمة إنجازات.
NEXT: NT-17 (مدة الميزانية الحرارية — أعلى قيمة/كلفة، بلا حاجة إلى جهاز) ثم NT-19 (PSI) لأنه يفتح NT-23 (تصنيف العنق).

### NT-15-MAXAI — نطاق التعلّم + تدقيق المحرك + وتيرة إعادة التخطيط — 2026-09-18

TASK: NT-15-MAXAI (large) — DONE_WITH_CONCERNS.
FILES: new `core/maxai/MaxAiCadence.kt`، new `ui/design/MaxSearchField.kt`، modified `core/maxai/MaxAiInsights.kt`، `core/maxai/MaxAiModels.kt`، `core/maxai/MaxAiEngine.kt`، `ui/viewmodel/MaxAiViewModel.kt`، `ui/mainscreens/MaxAiScreen.kt`، EN/AR `max_ai_strings.xml`، `docs/ai/{DECISIONS,NEXT_TASK,KNOWN_ISSUES,VALIDATION,HANDOFF}.md`. New tests: `core/maxai/MaxAiInsightsTest.kt`، `core/maxai/MaxAiCadenceTest.kt`، وإضافة `MaxAiTimelineSearchTest` إلى ملف الفلتر القائم. حُذف حطام جذر فعلي: `check2.py` و`fix_tweak.py` (كانا مُسجَّلين محذوفين في git وباقيين على القرص ⇒ `stray_root_file: 2`). لا تغيير في `core/hardware`، ولا كتابة عتاد جديدة، ولا مسار توقيع/CI.
GATES: `code_health --assert` = exit 0 (**الأربعة أصفار**: package_mismatch · unresolved_resource · duplicate_string_key · stray_root_file 0)، والدَّين كما هو (10 · 29 · 80 · 27). `i18n_coverage --assert` = 0 عيوب والأكواد الثلاثة متطابقة. `repo_audit.py` = `PROBLEMS: 0` (242 ملفًا · 1536 مرجعًا · 2145 مفتاحًا). `git diff --check` نظيف. نصوص جديدة: ٣٢ مفتاحًا في كل من `values/` و`values-ar/` (222 ← 258 في `max_ai_strings.xml` بلا تكرار).
BUILD: **مُتحقَّق بأمر** (JDK 17 + `ANDROID_HOME=~/android-sdk`): `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` = BUILD SUCCESSFUL 2m13s · `:app:testDebugUnitTest` = BUILD SUCCESSFUL · **160 اختبارًا، 0 فشل، 0 مُتخطّى** · `:app:assembleDebug` = BUILD SUCCESSFUL 2m34s · APK `116,778,525` بايت.
BUILD CAUGHT A REAL DEFECT: كسرت `'` غير مهرَّبة في نصّين إنجليزيين `mergeDebugResources` («Invalid unicode escape sequence»)، وهو خطأ لا تكشفه أي بوابة ثابتة — رُصد بـ`aapt2` وأُصلح (`\'`). لا يُبنى تسليم واجهة بلا مُصرّف.
RESIDUAL RISK: (١) **مراجعة السلامة من Luna معلّقة إلزاميًا** (AGENTS.md §2: أي تغيير في `core/maxai` لا يُغلق قبلها) — لم تُنفَّذ لتعدّد النماذج اليدوي. (٢) لا مُشغّل جهاز: العرض الفعلي لشريط النطاق وقائمة البحث وكبسولة المعايرة وRTL/الخط الكبير غير مُتحقَّق منه بصريًا. (٣) الإيقاظ المبكر يعمل والمحرك مطفأ أيضًا (دورة قراءة فقط، بلا كتابة) — الكلفة المقيسة نظريًا: حتى ٦ دورات/دقيقة حول تغيّر سياق، محدودة بـ ADR-30. (٤) `knobsByContext` تُبنى لكل سياق في كل حلقة جديدة؛ لا قياس أداء فعلي على جهاز. (٥) الترجمة الآلية للنصوص الجديدة في ٨١ لغة غير موجودة (تُظهر الإنجليزية، وهو السلوك الصحيح، لكنه فجوة مُعلنة).
NEXT: تشغيل مراجعة مستقلة (AGENTS.md §4) على هذا الـdiff — المرشح الأول هو `MaxAiCadence.decide` لأن انحداره يظهر كتأخير صامت أو كتحكم متأرجح. ثم NT-13 (كتابات العتاد من طبقة العرض) لأنه الأخطر المقيس.

### NT-06-I47-PERF - Max AI presentation and insight dispatch - 2026-09-18

TASK: NT-06-I47-PERF (medium) - DONE_WITH_CONCERNS.
FILES: Modified `ui/mainscreens/MaxAiScreen.kt`, `ui/viewmodel/MaxAiViewModel.kt`, EN/AR `max_ai_strings.xml`, this log and the I-47 entries in `NEXT_TASK.md` / `KNOWN_ISSUES.md`. Added `MaxAiTimelineFilterTest.kt` and `MaxAiPresentationArchitectureTest.kt` under `app/src/test/java/nd/max/ui/`. No core, hardware, native, signing or CI changes; existing untracked `.kilo/` left untouched.
GATES: `python3 tools/code_health.py --assert` exit 0 (four health metrics 0; debt unchanged: 10 oversized files, 29 own wildcard imports, 80 hardcoded literals, 27 presentation writes); `python3 tools/i18n_coverage.py --assert` exit 0 (85 matching locale codes, 0 errors); `python3 tools/repo_audit.py` reports `PROBLEMS: 0`; `git diff --check` clean. Kotlin file count was already 299 before this task, not REVIEW.md's historical 297; it is now 301 after adding two test files. Repo audit: 240 app source files unchanged, R.string references 1502 -> 1503, base string keys 2108 -> 2110.
BUILD: Not run, per ENGINEERING-CONTRACT section 6; compilation unverified for this change. Five JUnit tests added (three filter tests, two source architecture guards), not executed. Historical build success does not validate this diff.
RESIDUAL RISK: Device rendering, RTL/large-font menu layout, saved-state restoration and runtime threading remain unverified. No measured FPS/battery improvement is claimed. Filtering uses the recorded enum verdict, not the special display label for probe/safety/drift cards; it never changes the journal or learning totals. Two new strings are paired in EN/AR; other locales use Android fallback until translated.
NEXT: Run the targeted JUnit tests and device checks when a build is requested; continue NT-04 insight-derivation tests before changing learning or safety policy.

- I-47: added an exact verdict selector intersecting the existing kind filter; filtering occurs before the five-entry preview, preserves journal order, resets expanded entries when filters change, and keeps filter/expansion state with `rememberSaveable`. Empty results explicitly suggest changing filters. No journal deletion or artificial episodes.
- Corrected the insight dispatch boundary: `effectsSnapshot()` copies memory but can wait on a lock held during outcome persistence. The `map` previously ran in `viewModelScope` on Main; `flowOn(Dispatchers.IO)` now moves both copying and derivation off Main. No storage or decision semantics changed.
- Missing objective deltas, thermal predictions and prediction confidence now render the existing unknown label rather than a fabricated zero. Missing gain still suppresses the prediction line entirely.

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

### AR-20 + AR-02 — طبقة الامتياز الثانية (Shizuku) وتاريخ الإقلاع — 2026-09-18

- **AR-20 (Shizuku) — طبقة امتياز ثانية بعقد صريح.** أُضيف `core/privilege/`:
  `PrivilegeLevel` (NONE/SHIZUKU/ROOT) · `PrivilegeCatalog` (فهرس الأدوات وأدنى طبقة
  تحتاجها فعلًا) · `ShizukuGateway` (توفّر + إذن + قراءة خصائص عبر `ShizukuSystemProperties`)
  · `PrivilegeManager` (يجمع الجذر + Shizuku في حكم واحد، بقراءة سلبية بلا استدعاء `su`).
  والواجهة: `ui/component/PrivilegePanel.kt` + `ui/subscreens/PrivilegeScreen.kt`، ظاهرة في
  **شاشة البداية** (صفحة جديدة) و**الإعدادات**، مع تصنيف كل أداة: «تعمل بلا جذر» /
  «تحتاج Shizuku» / «تحتاج جذرًا» — بلا تقريب لأعلى.
- **حدّ مُعلَن (ADR-07):** تنفيذ أوامر shell بامتياز Shizuku **غير ممكن عبر واجهة 13.x
  العامة** — `Shizuku.newProcess` **خاصّ**. تحقّقنا بـ`javap` على
  `dev.rikka.shizuku:api:13.1.5`: العام هو التوفّر/الإذن/الخصائص/الـUserService، وليس
  `newProcess`. فلم نُعلن قدرة لا نملكها، وسُجّل الـ**UserService** كخطوة تالية.
- **AR-02 (تاريخ الإقلاع) — قراءة فقط.** `ui/util/BootHistoryUtil.kt` يقرأ
  `ro.boot.bootreason` · `sys.boot.reason` · `/sys/fs/pstore`، وبطاقة في
  `DiagnosticsScreen` تُظهر «لماذا أقلع الجهاز؟» ووجود أثر انهيار — وثلاث حالات إجبارية:
  مقروء / `Unsupported` / «لا نستطيع الجزم» عند تعذّر قراءة `pstore`.
- **تحقق حقيقي (ليس ادّعاءً):** بُني `:app:compileDebugKotlin` بنجاح، ومجموعة وحدات
  `:app:testDebugUnitTest` = **٣٤٠ اختبارًا / ٣٨ حزمة / ٠ فشل / ٠ خطأ** (منها
  `BootHistoryUtilTest` = ٦ اختبارات، و`ControlLayoutModelTest` سليمة بعد إضافة وجهة).
- **⚠️ عيب بيئة مكتشف (لم يُعدَّل):** البناء يفشل بـ`NoSuchMethodError` في مُسجِّل إضافة
  Compose عند `kotlin.compiler.execution.strategy=in-process` (القيمة في `gradle.properties`)،
  وينجح بـ`=daemon` من سطر الأمر. العيب يظهر في `:kernel-flasher` (وحدة لم نلمسها) قبل أن
  يصل إلى كودنا، أي أنه سابق لا سبب.
- **FILES:** جديد `core/privilege/{PrivilegeLevel,ShizukuGateway,PrivilegeManager}.kt` ·
  `ui/component/PrivilegePanel.kt` · `ui/subscreens/PrivilegeScreen.kt` ·
  `ui/util/BootHistoryUtil.kt` · `app/src/test/.../BootHistoryUtilTest.kt`.
  معدَّل `ui/mainscreens/{GetStartedScreen,SettingsScreen,DiagnosticsScreen}.kt` ·
  `ui/navigation/{MaxDestinations,MaxNavGraph}.kt` · `values*/max_screen_strings.xml` ·
  `app/build.gradle.kts` · `gradle/libs.versions.toml` · `AndroidManifest.xml`.
- **GATES:** `code_health --assert` = exit 0 (الصحّة صفر، الدَّين 10·29·80·27 بلا نمو) ·
  `i18n_coverage --assert` = exit 0.
- **متبقٍ صراحةً:** بقية `UNIMPLEMENTED-PROPOSALS.md` لم تُنفَّذ — وأكثرها يحتاج جهازًا
  وحكم سلامة (AR-03/19/21 تلمس مسارًا ساخنًا).

### AR-05 + AR-18 — صحة الوحدة وصفحة الإنقاذ داخل التطبيق — 2026-09-18

- **AR-05 (صحة الوحدة).** `ui/util/ModuleHealthUtil.kt` يقرأ **بلا كتابة**: مجلد الوحدة ·
  ملف `disable` · `BOOTCOUNT` من `count.sh` · `update` المنتظر · `versionCode` من
  `module.prop` · وجود `module.prop.orig` · وعدّاد `package-recovery.log` (السجل الذي كان
  بلا قارئ — FIND-2). وكل حقل ثلاثيّ: قيمة/`false`/`null`، و`null` تعني «لم نستطع الجزم»
  لا «سليم» (ADR-07).
- **AR-18 (صفحة الإنقاذ).** شاشة `ui/subscreens/ModuleHealthScreen.kt` تجمع حالة الوحدة
  وسجل التعافي و**خطوات الإنقاذ الفعلية لهذه الوحدة** (ملف `disable` · KernelSU/APatch ·
  Magisk · الـRecovery) — **تعمل بلا شبكة**، وخطرها صفر لأنها **قراءة فقط**.
- **الوصول:** وجهة جديدة `module_health` (أب: الإعدادات) + صف في `SettingsScreen` —
  بجانب صف الامتيازات.
- **تحقق حقيقي:** `:app:compileDebugKotlin` + `:app:testDebugUnitTest` = **٣٤٧ اختبارًا /
  ٣٩ حزمة / ٠ فشل / ٠ خطأ** (منها `ModuleHealthUtilTest` = ٧ اختبارات جديدة).
  `code_health --assert` = exit 0 · `i18n_coverage --assert` = exit 0.
- **FILES:** جديد `ui/util/ModuleHealthUtil.kt` · `ui/subscreens/ModuleHealthScreen.kt` ·
  `app/src/test/.../ModuleHealthUtilTest.kt`. معدَّل `ui/navigation/{MaxDestinations,MaxNavGraph}.kt` ·
  `ui/mainscreens/SettingsScreen.kt` · `values*/max_screen_strings.xml`.
- **غير مُثبَت (يُقال بصراحة):** العرض على جهاز حقيقي (قراءة `/data/adb` بجذر فعلي)،
  وسلوك الشاشة في RTL/خط كبير. المثبَت هو التحليل النقي + التجميع + الاختبارات.

### AR-08 + AR-11 — صحة البطارية وتآكل التخزين — 2026-09-18

- **AR-08 (البطارية).** `ui/util/BatteryHealthUtil.kt`: سعة التصميم مقابل السعة القصوى
  الحالية (µAh→mAh) وعدد الدورات — بثلاث حالات: `MEASURED` (كل الأرقام مقروءة) ·
  `ESTIMATED` (نسبة مشتقّة، موسومة) · `UNSUPPORTED` (بعُقد `battery` و`bms` معًا، وأول
  مقروء يفوز). و`-1` في `cycle_count` = «غير معروف» لا رقم.
- **AR-11 (التخزين).** `ui/util/StorageHealthUtil.kt`: `life_time` و`pre_eol_info` بدلالات
  JEDEC (`0x01..0x0A` = ١٠٪..١٠٠٪، و`0x0B` = تجاوز). **قراءة فقط** — لا كتابة ولا `fstrim`.
- **الواجهة:** بطاقتان في `DiagnosticsScreen` بعد «آخر إقلاع». وكل نص عبر `stringResource`
  (لا حرف واجهة صلب جديد — الدَّين بقي ٨٠).
- **تحقق حقيقي:** `:app:compileDebugKotlin` + `:app:testDebugUnitTest` = **٣٥٦ اختبارًا /
  ٤٠ حزمة / ٠ فشل / ٠ خطأ** (+٩ من `HealthParsersTest`). `code_health --assert` = exit 0 ·
  `i18n_coverage --assert` = exit 0 · الدَّين بلا نمو (10·29·80·27).
- **عيب مُصلَح أثناء العمل:** `StorageMediaHealth.usedPercent` كان ينادي
  `lifeTimePercent` بلا تأهيل، والمُصرّف كشفه (`Unresolved reference`) — صُحّح إلى
  `StorageHealthUtil.lifeTimePercent`.
- **غير مُثبَت:** القيم الحقيقية على جهاز (عُقد المُصنّعين تختلف) وسلوك البطاقتين في RTL.

### AR-06 — ملخّص الانهيارات وANR — 2026-09-18

- **النطاق المُعلَن صراحةً:** `ui/util/CrashLogUtil.kt` يُنتج **ملخّصًا** لا محلّل traces:
  عدد ملفات `/data/anr` و`/data/tombstones` · أحدث اسم في كل مجلد · وزمن آخر ملف. ولا
  ندّعي قراءة محتوى traces ولا تفسيره (يختلف بين الإصدارات) — وهذا مكتوب في KDoc.
- **ثلاث حالات إجبارية:** قيمة · `0` · `null`. و`null` = «تعذّرت القراءة» لا «صفر الحوادث».
  وقراءة جزئية (ANR مقروء وtombstone لا) تُبقي الجانب الناقص `null` ولا تُصفّره.
- **الواجهة:** بطاقة «الانهيارات وANR» في `DiagnosticsScreen`. الزمن يُنسَّق بـ
  `DateFormat` محلي (بلا نمط صلب في الواجهة).
- **تحقق حقيقي:** `:app:compileDebugKotlin` + `:app:testDebugUnitTest` = **٣٦١ اختبارًا /
  ٤١ حزمة / ٠ فشل / ٠ خطأ** (+٥ من `CrashLogUtilTest`). `code_health --assert` = exit 0 ·
  `i18n_coverage --assert` = exit 0 · الدَّين بلا نمو (10·29·80·27).
- **غير مُثبَت:** قراءة `/data/anr` و`/data/tombstones` على جهاز حقيقي (تحتاج جذرًا،
  وبعض الرومات تمنع `ls` عليهما حتى بجذر).

### AR-10 — ZRAM كمسار معلن لا كمقبض أعمى — 2026-09-18

- **الفكرة (AR-10):** القيمة الأولى في **العرض** لا في مفتاح جديد: هل المنصّة تدير ضغط
  الذاكرة (`mmd.zram.*`)؟ بأي خوارزمية؟ وهل writeback مُفعَّل؟ وهل يُعلن النظام مراحل
  إعادة الضغط/تتبّع الخمول؟ — ثم **احترام المنصّة** عندما تديره (مبدأ `XR-R4`).
- **`ui/util/ZramPlatformUtil.kt` — قراءة فقط:** خصائص `mmd.zram.comp_algorithm` و`size`
  و`writeback.enabled` عبر `PropertyUtils`، وعُقد `/sys/block/zram0/{disksize,recompress,idle}`
  عبر `RootFileAccess`. **لا كتابة ولا `swapoff` ولا تغيير سياسة.**
- **ثلاث حالات:** قيمة · `false` · `null`. و`platformManaged` = «نعم» فقط عند خصائص معلَنة،
  و«لا» عند وجود `zram0` بلا خصائص، و`null` عند تعذّر الجزم — واختبار يثبّت أن وجود
  `zram0` وحده لا يكفي لادّعاء الدعم.
- **الواجهة:** بطاقة «ضغط الذاكرة (ZRAM)» في `DiagnosticsScreen`.
- **تحقق حقيقي:** `:app:compileDebugKotlin` + `:app:testDebugUnitTest` = **٣٦٥ اختبارًا /
  ٤٢ حزمة / ٠ فشل / ٠ خطأ** (+٤ من `ZramPlatformUtilTest`). `code_health --assert` = exit 0 ·
  `i18n_coverage --assert` = exit 0 · الدَّين بلا نمو (10·29·80·27).
- **غير مُثبَت:** وجود خصائص `mmd.zram.*` فعلًا على إصدارات مختلفة (Android 17+ حسب AOSP)،
  وقراءة عُقد `/sys/block/zram0` بلا جذر على بعض الرومات.

### AR-14 — حوكمة الخلفية: تفسير لا قتل — 2026-09-18

- **الفكرة (AR-14):** نستبدل فولكلور «اقتل الخلفية» بآلية المنصّة الحقيقية: أي حاوية
  خمول وضعت المنصّة هذا التطبيق فيها، وهل هو معفى من Doze، وهل هو مقيَّد.
- **`ui/util/BackgroundGovernanceUtil.kt` — قراءة فقط:** `am get-standby-bucket <pkg>` و
  `dumpsys deviceidle whitelist`. **لا تغيير حاوية ولا إعفاء ولا قتل.**
- **الترجمة دقيقة:** أرقام `AppStandbyController` الرسمية (5/10/20/30/40/45/50)،
  **وأي رقم آخر يبقى `UNKNOWN`** ولا يُدمج في خانة قريبة — واختبار يثبّت أن `99` لا تصير
  «نادر». وفرق مقصود بين «قُرئ فلم تُوجد» (`false`) و«لم نقرأ» (`null`).
- **الواجهة:** بطاقة «الخلفية والبطارية» داخل شاشة إعدادات كل تطبيق (`AppSettingsScreen`).
- **تحقق حقيقي:** `:app:compileDebugKotlin` + `:app:testDebugUnitTest` = **٣٧١ اختبارًا /
  ٤٣ حزمة / ٠ فشل / ٠ خطأ** (+٦ من `BackgroundGovernanceUtilTest`). `code_health --assert`
  = exit 0 · `i18n_coverage --assert` = exit 0 · الدَّين بلا نمو (10·29·80·27).
- **غير مُثبَت:** أن `am get-standby-bucket` و`dumpsys deviceidle whitelist` يعطيان مخرجات
  مقروءة عبر Shizuku/الجذر على كل إصدار، وأن الشكل النصّي للـwhitelist ثابت بين الرومات.

### AR-34 — مصدر تثبيت كل تطبيق — 2026-09-18

- **`ui/util/InstallSourceUtil.kt` — بلا جذر إطلاقًا:** `getInstallSourceInfo` على API 30+
  و`getInstallerPackageName` على API 29 (minSdk = 29)، مع `FLAG_SYSTEM` لتمييز تطبيقات النظام.
- **تصنيف محافظ عن قصد:** تطبيق نظام ⇒ `SYSTEM` · مُثبِّت معروف ⇒ اسم المتجر ·
  مُثبِّت غير معروف ⇒ `INSTALLED_BY_APP` (**لا** «متجر») · **لا مُثبِّت ⇒ `UNKNOWN`**،
  لأن Android الحديث قد يُعيد `null` لتطبيق مثبَّت فعلًا — فادّعاء «مثبَّت يدويًّا» سيكون
  كذبًا. واختبار مخصّص يثبّت هذا الفرق.
- **الواجهة:** بطاقة «مصدر التطبيق» في `AppSettingsScreen` (النوع + حزمة المُثبِّت + حزمة المنشأ).
- **تحقق حقيقي:** `:app:compileDebugKotlin` + `:app:testDebugUnitTest` = **٣٧٦ اختبارًا /
  ٤٤ حزمة / ٠ فشل / ٠ خطأ** (+٥ من `InstallSourceUtilTest`). `code_health --assert` = exit 0 ·
  `i18n_coverage --assert` = exit 0 · الدَّين بلا نمو (10·29·80·27).
- **غير مُثبَت:** سلوك `getInstallSourceInfo` على ROMs مخصّصة (قد تُعيد `null` أكثر).

### تصحيح أرقام سابقة (قياس) — 2026-09-18

- **خطأ اكتشفته في قياسي نفسه:** كنت أجمع ملفات `TEST-*.xml` بنمط يشمل **المستودع كله**
  (بحث متكرّر)، فدخلت معي نتائج **`:terminal-emulator`** (145 اختبارًا / 18 صنفًا) في كل رقم
  أعلنته كـ«`:app:testDebugUnitTest`». لذلك أرقام الجولات السابقة **مُبالَغ فيها** وهي **غير
  موثوقة للمقارنة**، ولا يمكن إعادة اشتقاقها الآن بالضبط.
- **القياس الصحيح والمُتحقَّق الآن (لكل وحدة على حدة، تشغيل نظيف):**

  | الوحدة | الاختبارات | الأصناف | فشل | خطأ |
  | --- | ---: | ---: | ---: | ---: |
  | `:app` | **253** | **31** | 0 | 0 |
  | `:terminal-emulator` | **145** | **18** | 0 | 0 |
  | **المجموع** | **398** | **49** | 0 | 0 |

- **العلاقة بما ورد سابقًا:** الرقم `376` = `231 (app آنذاك) + 145 (terminal-emulator)` —
  أي أن مكوّنًا من وحدة أخرى كان يُحتسب خطأً. ولهذا لا يتوافق `376` مع `128` المسجَّل في
  جولة ١٠ لـ`:app`. **الأرقام هنا هي المرجع**، وما سبقها في هذا الملف يُقرأ كمؤشّر لا كرقم.
- **لم أُخفِ التصحيح ولم أُعد كتابة سجل الجولات** (ADR-18): أُضيف تصحيح صريح بدل تعديل الماضي.
- **قاعدة أُثبتها للجولات القادمة:** تُجمع النتائج **لكل وحدة باسمها**، ولا يُعلن رقم `:app`
  إلا من `app/build/test-results/testDebugUnitTest/`.

### AR-13 — تسجيل نتيجة التصريف بزمن مقيس — 2026-09-18

- **الفكرة (AR-13):** وصف ما سيُصرَّف ولماذا، ثم **تسجيل النتيجة**. «ولماذا» كان موجودًا فعلًا
  (كل مرشّح له `subtitle` تعريفي)، أما **النتيجة فلم تكن تُسجَّل إطلاقًا**: `cmd package compile`
  يُنادى ويُعاد منه `Boolean` فقط، فيبقى نجاحه ومدّته مجهولين — أي تحسين لا يمكن الدفاع عنه.
- **`ui/util/EventLog.kt`:** دالة `result(screen, action, target, success, durationMs)` جديدة
  تنضمّ إلى `userAction`/`userTriggered`/`error`، **وتمرّ بنفس البوابة** (`DETAILED_LOG`، مطفأ
  افتراضيًا) — سطر `EVENT=OP_RESULT … ok=<bool> duration_ms=<n>`.
  وبناء السطر في `resultMessage()` **معزول عن الإرسال** ليكون قابلًا للاختبار بلا أثر جانبي.
- **`ui/util/Dex2oatUtil.kt`:** دالة مساعدة واحدة `measured()` يغلّف كل تنفيذ: يقيس بـ
  `SystemClock.elapsedRealtime()` (زمن حقيقي لا تقدير)، ويسجّل النتيجة **كما أعادها المُنفّذ**.
  الدوال الأربع كلها (تصريف/إعادة ضبط، مفرد/شامل) تمرّ منه — و**بحثت فلا يوجد `Shell.cmd`
  آخر ينفّذ `cmd package compile` في المستودع**، أي أن نقطة التغطية واحدة.
- **صدق الواجهة:** أُضيف إلى وصف مرشّح التصريف صراحةً أن **المخرجات المصروفة تُهدَر عند تحديث
  التطبيق نفسه**، فالمكسب يبقى حتى التحديث القادم فقط (EN + AR معًا — ADR-14).
- **تحقق حقيقي:** `:app:compileDebugKotlin` = نجاح · `:app:testDebugUnitTest` = **253 اختبارًا /
  31 صنفًا / 0 فشل / 0 خطأ** (+5 من `EventLogResultTest`) · `:terminal-emulator:testDebugUnitTest`
  = 145/0. `code_health --assert` = exit 0 · `i18n_coverage --assert` = exit 0 · الدَّين بلا نمو
  (10·29·80·27).
- **عيبان كشفهما التحقق الحقيقي وأُصلحا (لا يُدّعى غيرهما):**
  1. `aapt2` رفض النصّ الجديد: همزة الملكية `'` غير مُهرَّبة ⇒ `Invalid unicode escape sequence`
     وفشل `:app:mergeDebugResources`. صُحّحت إلى `\'`.
  2. اختباري أنا كان **يؤكّد خاصية خاطئة**: عدّ بادئات `EVENT=OP_RESULT` في سطر يحوي الهدف
     المحقون نفسه ⇒ فشل زائف. صُحّح ليؤكّد الضمانة الحقيقية: **سطر واحد، والنصّ المحقون يبقى
     محصورًا داخل حقل الهدف**.
- **غير مُثبَت:** أن `SystemClock.elapsedRealtime()` يقيس مدّة التصريف الفعلية على جهاز (المتوقَّع
  نعم، لكن لم يُقس)، وأن سطر `OP_RESULT` يُقرأ صحيحًا من قارئ السجل (`sys.maxmanager-service`).

### AR-32 — كشف انسداد الخيط الرئيسي بأثر مقيس — 2026-09-18

- **الفكرة (AR-32):** «التطبيق تجمّد» تقرير لا يمكن فحصه اليوم لأن **لا سطر يقيسه**. البند يطلب
  تشخيصًا **مربوطًا بالدليل** + كشف انسداد الخيط الرئيسي.
- **`ui/util/MainThreadStallDetector.kt` — قياس داخل عمليتنا فقط:**
  - `StallMath.classify(prevNs, nowNs, thresholdMs, ceilingMs)` — **خالصة بلا اعتماد على أندرويد**،
    لذلك مُختبرة بلا جهاز. أوّل إطار لا يُحكم عليه، والحدّ نفسه يُحتسب انسدادًا، والمدّة تُقاس
    بالمللي بلا تقدير.
  - `MainThreadStallDetector` — [Choreographer] يطالبنا بإطار كل مزامنة رأسية؛ الفجوة بين إطارين
    هي القياس. **بلا أي كتابة عتاد**، ونداء واحد لكل إطار.
  - **حدّ الصدق المحوريّ:** أي فجوة **> 5 ثوان** تُصنَّف `Ignored` ولا تُحتسب انسدادًا، لأننا
    لا نستطيع التمييز بين تجمّد حقيقي وحلقة رسم **متوقّفة** (شاشة مطفأة/خلفية). البديل — إطلاق
    إنذار لم نتحقّقه — هو ما يمنعه `ADR-07`.
- **`ui/util/EventLog.kt` — `symptom()`:** نوع حدث جديد منفصل عن `userTriggered` («المستخدم فعل»)
  و`error` («التقطنا استثناءً») — لأن **الخلط بينهما يجعل السجل يخبرك أن المستخدم ضغط زرًّا لم
  يضغطه**. السطر: `EVENT=SYMPTOM screen=… symptom=… value_ms=… worst_ms=… count=…`، وبناؤه في
  `symptomMessage()` معزول عن الإرسال ليُختبر بلا أثر جانبي.
- **`MainActivity`:** المراقبة تُشغَّل في `onStart` وتُوقف في `onStop` — **ما لا نراه لا ندّعي
  قياسه** — ويُسجَّل أ**سوأ** انسداد مرّة عند الإخفاء (رقم للمقارنة المستقبلية، لا سطر معزول).
- **تحقق حقيقي:** `:app:compileDebugKotlin` = نجاح · `:app:testDebugUnitTest` = **264 اختبارًا /
  32 صنفًا / 0 فشل / 0 خطأ** (+11: `MainThreadStallDetectorTest` 8 و`symptom` 3) ·
  `code_health --assert` = exit 0 · `i18n_coverage --assert` = exit 0 · **الدَّين بلا نمو**
  (10·29·80·27 · `presentation_hw_writes` بقي 27 فلم تنمُ كتابة عتاد من طبقة العرض).
- **حدود مُعلَنة:** يقيس **خيط تطبيقنا فقط** — لا الخيط الرئيسي لتطبيق آخر (يحتاج امتيازًا أعلى)،
  وليس محلّل إطارات للجهاز (ذلك `AR-07`). و**غير مُثبَت**: أن `postFrameCallback` المستمر لا
  يستهلك ميزانية إطارات ملموسة على جهاز، وأن العتبة ٧٠٠ مللي مناسبة لجهاز بطيء (٧٠٠ مللي
  عتبة النظير `uperf`، لا قياسنا).
- **الباقي من البند:** «تشخيص مربوط بالدليل» كاملًا لم يُنفَّذ (ربط كل لقطة تشخيص بالحدث الذي
  نتجت عنه). ما نُفِّذ هو نصفه المقيس.

### AR-04 — مصدر واحد لهوية الإصدار — 2026-09-18

- **الفكرة (AR-04):** «مصدر واحد لهوية الإصدار + مسار تحديث حقيقي». مُتحقَّق من الكود أولًا،
  فظهر أن التعريف الحالي **مبتوت على ثلاثة مواضع**:
  1. `AppVersionUtil.getAppVersion()` (لشاشة «حول» والرئيسية).
  2. `MainActivity` — **يعيد حساب إصدار التطبيق بنفسه** (`getPackageInfo` مرّتين مع تفريع
     حسب إصدار النظام) ليقرّر حوار «يوجد تحديث».
  3. `RootUtil.getModuleVersionCode()` — إصدار الوحدة بـ**أمر shell `grep`** على مسار مكتوب
     يدويًّا، بينما `ModuleHealthUtil` يقرأ **الملف نفسه** بمنفذ ملفات الجذر.
- **الجديد `ui/util/VersionIdentity.kt` — قارئ واحد وحكم واحد:**
  - `readApp(context)` من نظام الحزم فقط · `readModule(health)` من `ModuleHealthUtil` فقط.
  - `compare(appVC, moduleInstalled, moduleVC)` — **خالصة** ومُختبرة بلا جهاز، و**لا تُصدر
    `MATCH` بلا دليل**: وحدة غير مركَّبة أو رقم غير مقروء ⇒ `UNKNOWN`.
  - `versionGap` **له إشارة** (موجب = الوحدة أحدث) فلا يُقرأ الاتجاه بالمقلوب.
- **`ModuleHealthUtil`:** يقرأ الآن `id=` و`version=` من الوحدة **المركَّبة فعلًا**
  (`parseField`)، وأُضيفا كحقلين إلى `ModuleHealth` — فصار المستودع يعرف **ما على الجهاز**
  لا ما نتمنّاه.
- **`RootUtil.getModuleVersionCode()`:** فوّض للقارئ الواحد بدل أمر shell (العقد كما هو).
- **`MainActivity`:** صار يستخدم `VersionIdentity.readApp`، و**أُضيف شرط `appVC >= 0`** حتى
  لا يُبنى قرار تحديث على قراءة فاشلة.
- **`ModuleHealthScreen`:** قسم «هوية الإصدار» يعرض إصدار التطبيق · إصدار الوحدة · **التطابق**،
  بثلاث حالات (نصّ: EN + AR — ADR-14).
- **تحقق حقيقي:** `:app:compileDebugKotlin` = نجاح · `:app:testDebugUnitTest` = **274 اختبارًا /
  33 صنفًا / 0 فشل / 0 خطأ** (+10 من `VersionIdentityTest`) · `code_health --assert` = exit 0 ·
  `i18n_coverage --assert` = exit 0 · **الدَّين بلا نمو** (10·29·80·27).
- **عيب حقيقي اكتُشف وسُجّل — `I-74` (P1):** المستودع يحمل `module.prop` بـ**معرّفين** ورقمَي
  `versionCode` متناقضين (`mainfiles`: `MaxManager`/`1` · `android/kernelsu`:
  `nees_maxmanager`/`10000`)، وسلوك «يوجد تحديث» الذي يراه المستخدم كان **يتغيّر حسب أيّ نسخة
  رُكِّبت**. **لم أُعدّل أي `module.prop`** — قرار مالك + سلامة، والاختلاف الآن **ظاهر في
  الواجهة** بدل أن يكون مخفيًّا. التفصيل والمسار في `KNOWN_ISSUES.md` I-74.
- **غير مُثبَت:** «مسار تحديث حقيقي» (النصف الثاني من `AR-04`) **لم يُنفَّذ** — لا فحص إصدار
  عن بعد ولا تحقّق توقيع (`↔AR-26` · `PEER-24`)؛ والقراءات على جهاز مصنَّع لم تُختبر.

### AR-09 — سجل دورات الشحن: حكم طاقة طولي لا لحظي — 2026-09-18

- **الفكرة (AR-09):** قراءة واحدة «البطارية ٤٠٠٠ مللي» لا تُثبت تدهورًا. الذي يُثبته هو **تكلفة
  النقطة عبر الزمن**: كم µAh احتاجت كل نقطة نسبة، وهل ارتفعت التكلفة.
- **الجديد `ui/util/ChargeLedger.kt`:**
  - **المنهج معلَن في الكود:** نأخذ لقطة **عند فتح شاشة التشخيص فقط** — **لا خدمة خلفية ولا
    مستشعر دائم** — من: النسبة · `BATTERY_PROPERTY_CHARGE_COUNTER` · هل يشحن. وكل لقطتين
    متتاليتين تصلحان تُنتجان جلسة. التخزين **داخل التطبيق فقط** (SharedPreferences).
  - `sessionBetween(prev, now)` — **خالصة**: لا جلسة بلا عدّاد في الطرفين، ولا بفرق سالب، ولا
    بصعود أقلّ من ٥٪ (ضجيج قراءة)، ولا بعدّاد لم يتحرّك (قراءة غير متّسقة).
  - `verdict(sessions)` — **خالصة**: `CounterUnavailable` (لا أساس قياس) · `InsufficientData`
    (يعرض التقدّم: have/need = 3) · `Stable(وسيط)` · `Drifting(وسيط، آخر)` — و**لا «انحراف» إلا
    بفارق ≥ ١٥٪**، والمقارنة بـ**الوسيط** لا المتوسط كي لا تُحرّك جلسة شاذّة خطّ الأساس.
  - `encode`/`decode` — نافذة متحرّكة (٣٠ جلسة)، والمدخل المشوّه **يُتجاهل** ولا يُسقط السجل كله.
  - `EventLog.symptom(screen="ChargeLedger", symptom="charge_session")` — كل جلسة تُسجَّل بنوع
    الحدث الذي أُضيف في `AR-32`.
- **الواجهة:** بطاقة «سجل دورات الشحن» في شاشة التشخيص، بعد بطاقة صحة البطارية، وتعرض: عدد
  الجلسات · مدى آخر جلسة · الطاقة المضافة · **تكلفة النقطة** · والحكم بحالاته الأربع
  (نصوص EN + AR — ADR-14). والأساس معلَن **في البطاقة نفسها** لا مخفيًّا.
- **تحقق حقيقي:** `:app:compileDebugKotlin` = نجاح · `:app:testDebugUnitTest` = **288 اختبارًا /
  34 صنفًا / 0 فشل / 0 خطأ** (+14 من `ChargeLedgerTest`) · `code_health --assert` = exit 0 ·
  `i18n_coverage --assert` = exit 0 · **الدَّين بلا نمو** (10·29·80·27 — والتخزين في SharedPreferences
  ليس كتابة عتاد، فـ`presentation_hw_writes` بقي 27).
- **حدود مُعلَنة:** ليس «صحة بطارية» ولا «عمر متبقٍّ» ولا درجة تسويقية (`AR-R5`)، **ولا نقارن بين
  أجهزة** (لا أسطول قياس). وتواتر الجلسات منخفض بطبيعتها (فتح الشاشة) — وهذا **مقصود** بدل خدمة
  خلفية تُستهلك بطاريةً لتقيس البطارية.
- **غير مُثبَت:** أن `BATTERY_PROPERTY_CHARGE_COUNTER` مدعوم ويعطي قيمًا متّسقة على مُصنّعين
  مختلفين (متوقَّع: مدعوم على Qualcomm، وقد يكون `0`/غير مدعوم على غيره ⇒ والنتيجة المعروضة
  عندها `CounterUnavailable` بصراحة)، وأن الجلسات تتراكم فعليًا عبر أيام على جهاز حقيقي.

### PEER-8 + AR-31 — كل كتابة عتاد تُقرأ وتُحكم عليها — 2026-09-18

- **الفكرة:** `RootFileAccess.write` كانت تعيد **نجاح أمر الكتابة** فقط، وهذا **ليس** «القيمة
  وصلت». النظير `uperf` يذكر سطر سبب التجاهل عند مسار غير قابل للكتابة، أي أن الفرق معروف في
  هذا المجال. وكان في المستودع **موضع واحد** يتحقّق يدويًّا (`TouchBoostViewModel`) وغائب في
  العشرين الباقية.
- **الجديد `core/hardware/WriteVerification.kt`:** حكم على **القيمة** لا على الأمر، بأربع حالات
  لا اثنتين — `MATCHED` · `DIFFERS` (**الجهاز قيّد القيمة**: تدوير/حدّ/حاكم) · `WRITE_FAILED`
  (**لم تُكتب**) · `READBACK_UNAVAILABLE` (**تعذّرت القراءة ⇒ لا حكم**). ودواله خالصة: توحيد
  الفراغات، والتكافؤ العددي (`500000` و`0500000`)، و`null` ليست صفرًا ولا «مختلفة».
- **`RootFileAccess`:** صارت النواة **واحدة** (`writeOutcome`): أمر الكتابة ← **قراءة للتحقّق**
  ← تسجيل الحكم. و`write()` تُعيد `!= WRITE_FAILED` — وهي **مكافئة حرفيًّا** لـ`exec().isSuccess`
  القديمة، فلم يتغيّر معنى العائد لأي متصل. ومن يريد الحكم على القيمة يستعمل `writeVerified`.
  لا إصلاح ولا إعادة محاولة ولا تبديل مسار: **الحكم يُعلَن، والقرار للمتصل**.
- **`EventLog.writeCheck`:** حدث جديد `EVENT=WRITE_CHECK path=… wrote=… read=… verdict=…` —
  منفصل عن `OP_RESULT` لأنه يحمل ما لا يحمله: **القيمة التي وجدناها فعلًا**. و`read=?` عند
  تعذّر القراءة بدل اختراع قيمة، وتوحيد الفراغات كي يبقى الحقل واحدًا قابلًا للبحث.
- **إزالة التكرار:** `TouchBoostViewModel` صار يستعمل البدائية المشتركة بدل `write` + `read` +
  مقارنة نصّية مكتوبة يدويًّا (وقراءة أقل).
- **`presentation_hw_writes` انخفض 27 → 26** — أي أن إزالة التكرار قلّلت وصول طبقة العرض للعتاد،
  والدَّين لم ينمُ (10 · 29 · 80 · **26**).
- **تحقق حقيقي:** `:app:compileDebugKotlin` = نجاح · `:app:testDebugUnitTest` = **300 اختبار /
  35 صنفًا / 0 فشل / 0 خطأ** (+12: `WriteVerificationTest` 10 و`WRITE_CHECK` 2) ·
  `code_health --assert` = exit 0 · `i18n_coverage --assert` = exit 0.
- **عيبان كشفهما التحقق الحقيقي:** (١) خطأ إغلاق قوس من إدراجي أوقف التصريف — أُصلح قبل التسليم.
  (٢) اختباري توقّع سطرًا نظيفًا بينما الكود يُخرج فراغين بسبب سطر جديد في قيمة sysfs ⇒
  **الاختبار كان صحيحًا في الطلب والكود ناقصًا**، فوُحّد الفراغ في بناء السطر.
- **⚠️ تغيير سلوكي مقصود في مسار تحكّم (يحتاج مراجعة سلامة):** تحقّق `TouchBoost` صار يقبل
  **التكافؤ العددي** (`1` و`01`) وتوحيد الفراغات، لا التطابق النصّي الحرفي. السبب: السؤال
  الصحيح «هل استقرّت القيمة؟» لا «هل النصّ نفسه؟». **الأثر:** قيم كانت تُعتبر فاشلة صارت تُقبل —
  وهو الأصحّ فنيًّا، لكنه **ليس** إعادة هيكلة محضة، ويُقرأ كذلك في المراجعة.
- **كلفة مُعلَنة:** قراءة واحدة إضافية لكل كتابة. ليست في مسار إطار-بإطار (تغيير إعداد/تطبيق
  ملف)، ولم تُقس على جهاز.
- **غير مُثبَت:** أن القراءة الفورية بعد الكتابة مستقرة على كل العقد (بعضها يحتاج مهلة)، وأن
  التكافؤ العددي صحيح لكل أنواع القيم (القيم النصّية كـ`schedutil` تُقارن حرفيًّا، ومُختبر).

### AR-33 — بروفايل قابل للمشاركة بمصدر معلن — 2026-09-18

- **الفكرة (AR-33):** قيم البروفايلات تُحفَظ في `ProfilePresetStore` **بلا أي وسم مصدر**، فلا
  يستطيع أحد أن يعرف: أهذا رقم قيس على جهاز، أم قيمة مبدئية مشحونة، أم ضبطها المستخدم بيده؟
  وهذا بالضبط ما يمنعه `XR-R5` و`ADR-07` (لا ثقة مصنوعة).
- **الجديد `ui/util/ProfileSharing.kt` — القواعد الملزمة فيه:**
  1. **لا يُشتقّ `MEASURED` أبدًا.** الاشتقاق الوحيد المتاح: القيمة = الافتراضي المشحون ⇒ `SEED`،
     وغيرها ⇒ `USER_SET`. واختبار يمرّ على كل الأزواج (الافتراضيات الخمسة × قيم مجاورة) ويثبّت
     أن الاشتقاق **لا يُنتج `MEASURED` ولا مرّة**.
  2. **مصدر مجهول يبقى مجهولًا** (`UNKNOWN`) ولا يُرقّى إلى «افتراضي» بالصمت.
  3. **جهاز مجهول ≠ نفس الجهاز**: `matchDevice` تُعيد `SAME`/`DIFFERENT`/`UNKNOWN`، وتوحّد
     المعرّف (`SM-8650` و`sm8650`).
  4. **المدخل غير الصالح يُرفض بسبب معلَن** (`PERCENT_OUT_OF_RANGE` · `PERCENT_NOT_A_NUMBER` ·
     `EMPTY_PROFILE`) — و**لا يُقصّ إلى الحدّ بصمت**.
  5. **صيغة لا نفهمها تُرفض** (`schema != SCHEMA`) بدل أن تُفسَّر خطأً.
- **`MEASURED` لا يخرج من عندنا:** إصدارنا يوسم `SEED`/`USER_SET` فقط؛ و`MEASURED` لا تظهر إلا
  إذا **صرّح بها مُصدِّر** مستند مستورد، وتُعرض عندنا **كادّعاء مُصدِّر** (نصّ واجهة صريح)، ولا
  تُتبنّى كقياس لنا — والقيمة تُقبل بوسمها الأصلي بلا تغيير.
- **الواجهة:** كتلة «شارك هذه البروفايلات» داخل نافذة ضبط البروفايلات في `AppSettingsScreen`:
  **نسخ** المستند إلى الحافظة، و**استيراد من الحافظة** مع تقرير: المقبولة · **المرفوضة وسببها** ·
  ما بلا مصدر · تنبيه ادّعاء القياس · **حكم الجهاز** — ثم «طبّق القيم المقبولة». وكل النصوص من
  الموارد (EN + AR — ADR-14)، فـ`hardcoded_ui_literals` بقي **80** ولم ينمُ.
- **تحقق حقيقي:** `:app:compileDebugKotlin` = نجاح · `:app:testDebugUnitTest` = **318 اختبارًا /
  36 صنفًا / 0 فشل / 0 خطأ** (+18 من `ProfileSharingTest`) · `code_health --assert` = exit 0 ·
  `i18n_coverage --assert` = exit 0 · الدَّين **10 · 29 · 80 · 26** بلا نمو.
- **غير مُثبَت:** سلوك الحافظة على أندرويد ١٣+ (إشعار النسخ)، ومقروئية `ro.soc.model` على
  مُصنّعين لا يُعلنونه (عندها `currentSoc()` تُعيد `null` ⇒ `DeviceMatch.UNKNOWN` بصراحة)،
  وأن ملف مستند كبير الحجم لا يزعج الحافظة.
- **حدّ معلَن:** لا يوجد بعد **تحقّق توقيع** للمستند المستورد — أي أن أي شخص يستطيع أن يكتب
  `"source":"measured"` في مستند. وهذا مقبول **فقط** لأننا نعرضه كادّعاء مُصدِّر لا كقياس؛ ولو
  أردنا لاحقًا معنى موثوقًا لـ`MEASURED` فلا بدّ من توقيع (`↔AR-26`) — يُسجَّل كقيد لا يُخفى.

### AR-24 — دفتر ذاكرة التطبيق: PSS ومقارنة لقطات — 2026-09-18

- **الفجوة الحقيقية:** المستودع كلّه لم يكن فيه **أي قارئ PSS** (مُتحقَّق: لا
  `getProcessMemoryInfo` ولا `meminfo` إلا سطرَي `SwapTotal/SwapFree` من `/proc/meminfo`).
  فكان «التطبيق يستهلك ذاكرة كثيرة» حكمًا **لا يمكن فحصه**، ومقارنة اللقطات غير ممكنة أصلًا.
- **الجديد `ui/util/MemoryLedger.kt` — طريقتان بمصداقيتين مختلفتين، وكلتاهما تُوسَم:**
  - `OWN_PROCESS`: `ActivityManager.getProcessMemoryInfo` لتطبيقنا — **بلا امتياز** (المسار الموثوق).
  - `DUMPSYS`: `dumpsys meminfo <pkg>` — **يحتاج امتيازًا**، وتحليله غير مضمون بين الإصدارات،
    فيُوسَم كذلك ولا يُخلط بالموثوق.
  - **ولا مقارنة بين طريقتين**: اختلاف [Method] ⇒ `Insufficient` **بصراحة**، لا مقارنة تفاح ببرتقال.
  - `parseTotalPssKb` — مطابقة **صارمة على وسم** `TOTAL PSS:`، وليست «أول رقم في الصفحة».
  - `delta` — **خالصة**: `Insufficient` (لا سابقة/طريقة مختلفة/قيمة غير صالحة) · `Stable`
    (فرق < ١٠٪) · `Changed` (بإشارة وكمية ونسبة).
  - تخزين داخل التطبيق بنافذة **لكل مفتاح** (٨ لكل تطبيق) — مفتاح مزدحم لا يُخرج مفتاحًا آخر.
- **الواجهة:** بطاقة «دفتر ذاكرة التطبيق» في شاشة التشخيص: PSS الحالي · **طريقة القياس** ·
  عدد اللقطات · والحكم مقابل اللقطة السابقة (نصوص EN + AR).
- **تحقق حقيقي:** `:app:compileDebugKotlin` = نجاح · `:app:testDebugUnitTest` = **333 اختبارًا /
  37 صنفًا / 0 فشل / 0 خطأ** (+15 من `MemoryLedgerTest`) · `code_health --assert` = exit 0 ·
  `i18n_coverage --assert` = exit 0 · الدَّين **10 · 29 · 80 · 26** بلا نمو.
- **عيب حقيقي كشفه الاختبار قبل التسليم (وجب ذكره):** تحليل `TOTAL PSS` كان يشترط **نهاية السطر**،
  والمخرج الحقيقي يضع بعده `TOTAL RSS` و`TOTAL SWAP PSS` على السطر نفسه ⇒ كان **يُسقط كل قراءة
  حقيقية** ويُعيد `null`. أُصلح التحليل (لا الاختبار) وصار مطابقة على الوسم فقط.
- **غير مُثبَت:** أن `dumpsys meminfo` مقروء عبر الجذر/Shizuku على كل إصدار، وأن صيغة سطر
  `TOTAL PSS` ثابتة بين إصدارات أندرويد ومُصنّعين (لذلك يُوسَم ولا يُعتمد عليه وحده)، وأن عتبة
  ١٠٪ مناسبة لكل أحجام التطبيقات.
- **مقابل قائمة النظراء التي طلبتها:** «القياس الحراري» **موجود أصلًا** (`ThermalUtil`: مناطق ·
  أجهزة تبريد · نقاط رحلة · تصنيف · بديل `dumpsys thermalservice`) فلم أُكرّره؛ وذاكرة لكل تطبيق
  كانت **الغائب الحقيقي** فبُنيت.

### AR-GAP-01 — Max Backup: نسخ بيانات التطبيقات بتحقّق لا بادّعاء — 2026-09-18

**المهمة:** فتح الفئة الغائبة `GAP-01` (نسخ بيانات التطبيقات) كشاشة **قائمة بذاتها**، مع مدخل
من كل تطبيق، وبأفكار من `SwiftBackup` و`neo-backup` و`Android-DataBackup` **دون نقل كود** منها.

**ما بُني:**

- **`ui/util/MaxBackupModel.kt` — القرار الخالص (بلا أندرويد وبلا جذر).** ولأن كل قرار فيه هو
  «هل نسمح بكتابة فوق بيانات تطبيق؟» فقد فُصل عن التنفيذ ليكون **قابلًا للاختبار**: الجرد
  (`Plan`) · المستند (`Manifest`) وترميزه الصارم · حكم السلامة · بوابة الاسترجاع · سياسة
  الاحتفاظ · تسمية المجلدات وقياسها. ثلاث قواعد تحكمه: **الغائب ليس صفرًا** · **لا استرجاع بلا
  تحقّق** · **أي خلل يبقى ظاهرًا** (`complete=false` تُعرض ولا تُخفى).
- **`ui/util/MaxBackupEngine.kt` — التنفيذ.** في `ui/util` لا في الشاشة (ADR-11؛ نفس مسار
  `LogUtil` الذي يستعمل `tar`/`chown`/`restorecon` من هنا). الكتابة: APK بلا جذر · بيانات
  التطبيق وأرشيفاتها بالجذر. **وبعد كل أرشيف**: `sha256` ثم **إعادة قراءة والتحقّق** قبل كتابة
  المستند، ثم `chown` إلى UID التطبيق + `chmod` + `restorecon` (نفس ما يفعله `LogUtil`، وسببه
  حقيقي: ملف يملكه root قد لا يمرّ إلى التطبيق عبر FUSE على أندرويد ١١+).
- **`ui/subscreens/MaxBackupScreen.kt` — الشاشة.** نمطان: **بلا معرّف** ⇒ منتقي تطبيقات (فهي
  شاشة قائمة بذاتها)، **بمعرّف** ⇒ الجرد · النطاق · الإنشاء · السجل · الفحص · الاسترجاع ·
  التخزين.
- **`MaxDestination.MaxBackup`** (`max_backup?pkg={pkg}`، `pkg` اختياري) + تسجيله في الرسم +
  **بطاقة مدخل في شاشة إعدادات كل تطبيق**.

**ما يفترق به عن النظائر — بنيّةً لا بالشعارات:**

1. **لا يكتب ثم يقول «تم».** كل ملف يُبصم ثم يُعاد قراءته والتحقّق منه قبل كتابة سطر واحد.
2. **الاسترجاع ممنوع بلا فحص.** `restoreDecision` ترفض إن كان أي مدخل `MISSING`/`CORRUPT`/
   `UNVERIFIABLE` — و«لم أفحص» ليست «سليم». ولذلك لا يُسنح الاسترجاع أصلًا للنسخ الناقصة.
3. **الجرد يُعرض قبل الكتابة** بأحجام **مقيسة**؛ وما لم يُقس يبقى `null` ويُقال «لم يُقس»، ولا
   يُقدَّر ولا يُعرض صفرًا.
4. **لا تشفير وهميّ.** الأرشيف غير مشفّر **ويُقال ذلك في الواجهة**: مفتاح مشحون داخل الـAPK ليس
   سرًّا، وادّعاء تشفير لا تملكه أسوأ من غيابه (`XR-R5`/`ADR-07`).
5. **الاسترجاع يُصلح ما يفسده الفكّ**: `am force-stop` قبل الكتابة، ثم `chown -R` بالـUID
   الحقيقي، ثم `restorecon -RF` — وإلا خرج التطبيق معطوبًا بدل مستعادًا.

**تحقق حقيقي:**

| الفحص | النتيجة |
|---|---|
| `:app:compileDebugKotlin` | نجاح (بلا تحذير جديد) |
| `:app:testDebugUnitTest` | **٣٥٧ اختبارًا / ٣٨ صنفًا / ٠ فشل / ٠ خطأ** (+٢٠ من `MaxBackupModelTest`) |
| `code_health --assert` | exit 0 — الدَّين **10 · 29 · 80 · 26** بلا نمو |
| `i18n_coverage --assert` | exit 0 (EN + AR معًا) |

**عيبان كشفهما التحقق قبل التسليم:**
1. **`MaxNavActions` تطلب `NavHostController` والشاشات تُمرّر `NavController`** ⇒ فشل تصريف في
   موضعين. أُصلح بالتنقّل عبر **السجل وحده** (`MaxDestination.MaxBackup.route`) كما تفعل شاشة
   الإعدادات أصلًا، وحُذف المساعد الذي لم يبقَ له متصل بدل تركه API ميتًا.
2. **اختباراتي أنا كانت تؤكّد خاصية غير قائمة:** كتبت في KDoc أن `sameIdentifier` تُطبّع
   `SM-8650` مع `sm8650` — **وهذا غير صحيح**، فالمقارنة تُطبّع حالة الأحرف والفراغات فقط. فبدل
   توسيع التطبيع ليجعل العبارة صحيحة، **صُحّحت العبارة** وأُضيف اختبار يثبّت أن **الفواصل لا
   تُطبَّع**: تطبيع أكثر مما ينبغي قد يجعل جهازين مختلفين يبدوان واحدًا فيسقط تحذير واجب.

**حدود مُعلَنة (لا أتجاوزها):**

- **غير مُثبَت على جهاز:** أن ملفات أنشأها root تُقرأ فعلًا من التطبيق بعد `chown`+`restorecon`
  على كل إصدار ومُصنّع · وأن `du`/`find`/`tar`/`sha256sum` من toybox تتصرف كما توقّعنا ·
  وأن `restorecon -RF` كافٍ لإصلاح سياق `/data/data/<pkg>` بعد الفكّ · وأن أعلام `pm install -r -d`
  تكفي لإعادة التثبيت فوق نسخة أحدث.
- **غير منفَّذ:** تشفير حقيقي بمفتاح يملكه المستخدم (وتشفير بمفتاح مشحون **مرفوض** لأنه ادّعاء) ·
  نسخ إلى Recovery (`↔GAP-01` عند `VR-25/tarb`) · الإرسال إلى مزوّد سحابي (ومرفوض كاعتماد) ·
  الرسائل/سجل المكالمات/الجهات (تحتاج أذونات خطيرة غير معلَنة عندنا) · فحص SSAID وأذونات التشغيل
  (تحتاج قراءة `packages.xml` وتحليلًا ثابتًا عبر إصدارات).
- **⚠️ يحتاج حكم سلامة (Luna):** `MaxBackupEngine.restore` هو **المسار الوحيد في المستودع الذي
  يكتب إلى `/data/data/<pkg>`**؛ ومساره محمي بحوار تأكيد + فحص سلامة + `force-stop`، لكنه
  بطبيعته يمسّ بيانات المستخدم، ولم يُشغَّل على جهاز بعد.
- **كلفة معلَنة:** الاسترجاع يعيد تثبيت الـAPK ثم يُوقف التطبيق قسرًا؛ التطبيق المستعاد يفقده
  التطبيق أثناء العملية (وإن أُوقف كان مغلقًا أصلًا).

### AR-GAP-01 (تكملة) — Max Backup: بيانات النظام لا التطبيقات فقط — 2026-09-18

**الطلب:** لا نسخ التطبيقات فقط، بل **كل ما يمكن حفظه**: الواي‑فاي · الأرقام · وغيره الكثير.

**ما بُني — طبقة بيانات نظام كاملة داخل `Max Backup` نفسه:**

- **`ui/util/MaxBackupSystemModel.kt` — الجدول والقرار الخالص.** كل فئة بيانات **صف مُعلَن**
  (`Source`) يحدّد من أين تُقرأ، وبأي وسيلة، وبأي أذونات، و**ما معنى استرجاعها** — فإضافة فئة
  لاحقًا سطر واحد لا شاشة ثانية. وتشمل: **الواي‑فاي** · **البلوتوث** (ملفات نظام، استرجاع
  يستبدل) · **جهات الاتصال وأرقامها** · **سجل المكالمات** (مزوّد، استرجاع **يدمج**) ·
  **الرسائل** (مزوّد، **قراءة فقط**) · **القاموس الشخصي** (مزوّد، دمج).
- **`ui/util/MaxBackupSystemEngine.kt` — التنفيذ بمسلكين:**
  - `ROOT_FILES`: أرشفة `tar` بمسارات مطلقة، و**حرس مسارات قبل الفكّ**: كل مدخل في `tar -t`
    يجب أن يكون أحد مسارات الفئة المُعلَنة بالضبط — فمنع أرشيف مُعدَّل من الكتابة في `/data/adb`
    باسم «استرجاع».
  - `PROVIDER`: صفوف تُقرأ بـ`ContentResolver` وتُؤرشف **JSON قابلًا للقراءة**، **ولا نسخة من
    قاعدة بيانات حيّة** (نسخة من SQLite يكتب عليه مزوّد قد تكون نصف مكتوبة). والاسترجاع **دمج**
    بمفاتيح تطبيع: آخر ٩ خانات من الرقم (فبادئة الدولة لا تُضاعف القائمة)، والصف الذي **لا مفتاح
    له يُدرَج لكن يُعَدّ** باسمه (`undedupable`) بدل ادّعاء أنه لن يتكرّر.
- **`ui/subscreens/MaxBackupSystemSection.kt` — نمط «بيانات النظام»** في الشاشة القائمة بذاتها،
  بمبدّل نمطين [التطبيقات | بيانات النظام]، وطلب أذونات وقت الاستعمال، وزر استرجاع يعرض **ما
  سيُنفَّذ وما سيُتخطّى ولماذا قبل التأكيد**، ونتيجة بعَدّ (`أُدرج/تُخُطّي/بلا مفتاح`).
- **إعادة استخدام لا مسار موازٍ:** نسخة النظام **مجلد في المستودع نفسه** بمعرّف `@system`
  (لا يمكن أن يكون اسم حزمة)، بنفس المستند ونفس بصمات `sha256` ونفس `verify`/`delete`/`prune`.
  أي أن آلية التحقّق **واحدة**، لا واحدة لكل نوع.

**تحقق حقيقي:**

| الفحص | النتيجة |
|---|---|
| `:app:compileDebugKotlin` | نجاح |
| `:app:testDebugUnitTest` | **٣٨١ اختبارًا / ٤٠ صنفًا / ٠ فشل / ٠ خطأ** (+٢٤: `MaxBackupSystemTest` و`MaxBackupSystemPermissionsTest`) |
| `code_health --assert` | exit 0 — الدَّين **10 · 29 · 80 · 26** بلا نمو |
| `i18n_coverage --assert` | exit 0 (EN + AR معًا) |

**قرار رُفض عن قصد (وذاك أهمّ ما في هذه الجولة):** بدأتُ بإضافة **«الأرقام المحجوبة»**
(`BlockedNumberContract`)، ثم تبيّن بـ`javap` على `android-36/android.jar` أن
`READ_BLOCKED_NUMBERS`/`WRITE_BLOCKED_NUMBERS` **ليست في `Manifest.permission` العام** لأنها
`signature|privileged` في AOSP. أي أن تطبيقًا عاديًّا **لا يستطيع الحصول عليها مطلقًا** ⇒ الفئة
كانت ستبقى «تحتاج إذنًا» للأبد وتُوهم المستخدم أن العطب منه. **فأُزيلت الفئة بالكامل** بدل شحن
وعد لا يمكن الوفاء به، وسُجّل السبب هنا.

**ما لم يُضَف (بسببه، لا بالنسيان):** **التقويم** (الأحداث تشير إلى `calendar_id` غير قابل
للنقل، فيحتاج إنشاء تقويم ثم ربطًا — كالجهات، ولم يُنفَّذ) · **APN** (`WRITE_APN_SETTINGS` نظامي) ·
**تشفير بمفتاح يملكه المستخدم** (وتشفير بمفتاح مشحون مرفوض لأنه ادّعاء) · **الرسائل: الكتابة
محجوبة بسياسة المنصّة** ولا ندّعي غير ذلك.

**أذونات جديدة في البيان (تُطلَب وقت الاستعمال):** `READ/WRITE_CONTACTS` ·
`READ/WRITE_CALL_LOG` · `READ_SMS` · `READ/WRITE_USER_DICTIONARY`.
وأُضيف اختبار يقابل **كل إذن في الجدول بسطور `AndroidManifest.xml`** — لأن الإذن غير المُعلَن
لا يُمنح أبدًا ويبدو كأن المستخدم رفضه، وهو خطأ يمرّ بصمت بلا هذا الاختبار.

**غير مُثبَت على جهاز:** صيغة مخرجات `dumpsys`-لا، بل `du/tar/sha256sum` من toybox · أن مسار
`WifiConfigStore.xml` هو الصحيح على الجهاز المستهدف (نجرّب مسارَي أندرويد ١٤ وما قبله) · أن
`restorecon` كافٍ بعد فكّ ملفات `/data/misc` · وأن كشف تكرار الجهات بمفتاح (اسم+رقم) لا يُنشئ
جهات زائدة على بيانات حقيقية متنوّعة. **والأهم:** مسار الكتابة إلى `/data/data` و`/data/misc`
ما زال يحتاج **حكم سلامة (Luna)** ولا يُغلق بلا جهاز.

---

## تكملة ١٦ — `GAP-07`: الصلاحيات و`AppOps` (أكبر فئة غائبة)

**الفجوة:** المستودع لم يكن فيه **أي** إدارة صلاحيات ولا `AppOps`: لا عرض لصلاحيات البيان، ولا
قراءة لأوضاع المنصّة، ولا تفسير للفرق بينهما — وهما حقيقتان مختلفتان (البيان يقول ما طُلب،
و`AppOps` يقول ما يُسمح به الآن).

**الملفات الجديدة:**
- `ui/util/PermissionPolicy.kt` — النموذج والقرار الخالص: تحليل `cmd appops get` · حكم الكتابة ·
  المرجع والانحراف · الترميز.
- `ui/util/AppOpsUtil.kt` — التنفيذ: قراءة الصلاحيات بلا امتياز · قراءة/كتابة الأوضاع ·
  **قراءة بعد كل كتابة** · المرجع في تخزين التطبيق الخاص (لا `/data/adb`).
- `ui/util/PrivilegedShell.kt` — **فصل الاعتماد**: غلاف `Shell` كان داخل `MaxBackupEngine`
  فصار أصلًا مشتركًا، فلا تعتمد ميزة الصلاحيات على ميزة النسخ لأجل دالّتين.
- `ui/subscreens/PermissionsScreen.kt` + نصوص EN/AR + وجهة `max_perms/{pkg}` + بطاقة مدخل في
  شاشة إعدادات كل تطبيق.

**قواعد مُثبَّتة باختبار (٢٠ اختبارًا):** وضع مجهول **يُسقَط لا يُخمَّن** · سطر لا يشبه اسم عملية
يُسقَط · غياب الصف بعد `default` **دليل نجاح** وغيابه بعد غيره **ليس** · «مرجع + جدول» ينتج
انحرافًا للمُسقَط أيضًا · خطة الاستعادة تكتب **قيمة المرجع** لا الحالية · مستند بإصدار أو وضع
مجهول **يُرفض** · وكل وضع قابل للكتابة قابل للقراءة أيضًا.

**تحذيران أُصلحا (وكشفا فرقًا حقيقيًّا):** `.orEmpty()` على `requestedPermissionsFlags` لا يوجد
(الحقلان غير قابلين للعدم على `compileSdk 36`) ⇒ صار احتياطًا بلا `orEmpty`؛ و`PROTECTION_MASK_BASE`
مهجورة ⇒ صرنا على `getProtection()` بحسب وثيقة AOSP (`Permissions.md`): **المستوى الأساسي المطلوب**.

**اختبار كان مخطئًا لا الكود:** افترضتُ أن عملية خارج المرجع لا تُنتج انحرافًا؛ والصحيح أنها
**تُنتج** عند سقوطها من الجدول (رجعت للافتراضي). صُحّح الاختبار.

**التحقق:** `compileDebugKotlin` نجاح · `testDebugUnitTest` **٤٠١ اختبار / ٤١ صنفًا / ٠ فشل** ·
`code_health --assert` exit 0 (الدَّين 10·29·80·26 بلا نمو) · `i18n_coverage --assert` exit 0.

**غير مُثبَت:** قراءة صلاحيات تطبيق آخر قد تُرفض على إصدارات/روم تُقيّد `GET_PERMISSIONS` ⇒
تُعرض «تعذّرت القراءة» (مُعلَنة لا صامتة) · صيغة `cmd appops` بين المصنّعين · وسلوك `appops set`
على روم يمنع الكتابة ⇒ تُعلَن `UNVERIFIABLE` ولا تُدّعى نجاحًا.

---

## تكملة ١٧ — `GAP-12` + `GAP-13`: نسخة الإعداد تعلن محتواها قبل كتابتها

**الفجوة:** `ConfigBackup` كان يكتب «إعداد + قائمة تطبيقات» ثم يقول «تمّت النسخة» — بلا أن يقول
**ما لم يُحفظ ولماذا**، ولا يحفظ المظهر أصلًا (و`CustomThemeScreen` كان عالمًا منفصلًا).

**الملفات:** `ui/util/ConfigBackupInventory.kt` (نموذج خالص) · `ui/util/MaxPrefsBundle.kt` (قراءة
وكتابة تفضيلاتنا) · نصوص EN/AR · ووصل في `TweakViewModel` و`ConfigBackupFlow`.

**القرار الأهمّ استثناء لا إضافة:** `maxai_safety` **لا يُنسخ ولا يُستورد** — نسخة تُستورد من ملف
يستطيع كاتبُها إضعاف حراسة `Max AI` ليست نسخة إعداد بل ناقل تغيير لسياسة سلامة. والحماية
**بالقائمة البيضاء** في الطبقتين، وتُثبّتها اختبارات (`decode` المفرد يرفض الملف المحمي أيضًا).

**قواعد مُثبَّتة باختبار (١٩ اختبارًا):** النوع يُحفظ ولا يُوسَّع (`Int32` ≠ `Whole`: قراءة مفتاح
`Long` بـ`getInt` **ترمي**) · حِزمة تحمل ملفًا محميًّا أو مجهولًا **تُرفض كاملة** · مستند بإصدار أو
نوع مجهول يُرفض · لا قسم يُختار ⇒ لا زرّ نسخ · والاستثناءات ثابتة لا تتغيّر بالنطاق.

**عيب تصميم كشفه اختبار:** ملف تفضيلات **موجود لكنه فارغ** كان يُعلن `FILE_MISSING` — وهو خطأ في
**السبب**: الأصحّ `EMPTY`. أُصلح النموذج (`existingPrefFiles`) لا العبارة. وأُصلح كذلك إنذار كاذب:
`PrefValue.Text("...")` في ملف **الاختبار** يُحسب نصًّا صلبًا في `code_health` ⇒ صار عبر مصنّع محلي.

**التحقق:** `testDebugUnitTest` **٤٢٠ اختبارًا / ٤٢ صنفًا / ٠ فشل** (+١٩) · `code_health --assert`
exit 0 (الدَّين 10·29·80·26 بلا نمو) · `i18n_coverage --assert` exit 0.

**غير مُثبَت:** أن `settings`/`app_prefs` هي فعلًا الملفات التي يقرؤها المظهر على كل روم (معلَنة في
`prefFilesOf` ومختبَرة، لكن الجهاز هو الحكم) · وأن «دمج لا محو» كافٍ لمفتاح تغيّر معناه بين إصدارين ⇒
يبقى مفتاح قديم غير مُحدَّث بلا ضرر معلَن.

---

## تكملة ١٨ — `GAP-11`: جرد المستشعرات، و**عطب حقيقي أُصلح في طريقه**

**ما بُني:** `ui/util/SensorInventory.kt` (نموذج خالص) · `ui/util/SensorMonitorUtil.kt` (جرد
بلا امتياز + قراءة ضوء بمهلة) · `ui/component/SensorInventoryCard.kt` (بطاقة التشخيص) ·
نصوص EN/AR. وقاعدة الحكم **ثلاثية لا ثنائية**: `ABSENT` ≠ `REPORTED` ≠ `UNREADABLE`، والقيمة
عند العجز **`null` لا `0f`**.

**العطب الذي كشفه الفحص:** قراءة الضوء في `core/hardware/ContextData.kt` كانت
`registerListener` ثم `unregisterListener` **في الكتلة نفسها**، فلا يصل حدث قطّ والقيمة تبقى
`0f` إلى الأبد. أي أن الكود كان يقول **«مظلم»** كلما عجز عن القراءة — وهو أسوأ من أن يقول «لا
أعرف»، لأن صفر لوكس **قيمة حقيقية** تُبنى عليها سياسة. وأسوأ من ذلك: `AndroidContextDataSource`
يُوفّره DI **ولا يحقنه أحد**، فالعطب كان كامنًا ينتظر أول من يوصل به.

**الإصلاح:** القراءة تمرّ بـ`SensorMonitorUtil.readLight` (تسجيل ← انتظار أول حدث بمهلة ١٥٠٠
مللي ← إلغاء)، ورفض التسجيل **ليس فشلًا صامتًا** بل `UNREADABLE` بصراحة. وحقل
`ambientLightLux` صار `Float?` ليحمل «لم أقرأ» كما ينبغي.

**قواعد مُثبّتة باختبار (١٢ اختبارًا):** **المهلة لا تصير صفرًا أبدًا** · وقراءة `0f` حقيقية
**تُبلَّغ** لأنها حقيقة · «لا مستشعر» ≠ «لم أقرأ» · نوع مُصنّع غير معروف يبقى `OTHER` **ولا
يُخمَّن تصنيفه** · و«0» في الطاقة/المدى/التأخير تعني «غير معلَن» فلا تُعرض صفرًا.

**عيب ثانٍ كشفته البوابة:** البطاقة أضافت أسطرًا إلى شاشة التشخيص فأصبحت > ١٠٠٠ سطر وارتفع
دَين `oversized_files` 10 → 11. **فُصلت إلى ملفها** بدل قبول نمو الدَّين أو رفع السقف.

**التحقق:** `compileDebugKotlin` نجاح · `testDebugUnitTest` **٤٣٢ اختبارًا / ٤٣ صنفًا / ٠ فشل**
(+١٢) · `code_health --assert` exit 0 (الدَّين 10·29·80·26 **بلا نمو**) · `i18n_coverage --assert`
exit 0.

**⚠️ يحتاج حكم سلامة (Luna):** التغيير مسّ `core/hardware` — وهو نطاق لا يُغلق عندنا بلا حكم
سلامة. والكتابة صفر (قراءة مستشعر فقط، ولا مسار ساخن: تُقرأ عند الطلب لا إطارًا بإطار)، لكن
التصنيف الإداري يبقى: **لم أُغلقه**.

**غير مُثبَت:** أن مهلة ١٥٠٠ مللي تكفي لكل مستشعر ضوء على كل جهاز (بطيء منها سيُعلَن
`UNREADABLE` **بصراحة** لا صفرًا) · وأن `getSensorList(TYPE_ALL)` لا يرمي على روم يمنع الوصول ⇒
`null` تُعرض «تعذّر الوصول إلى خدمة المستشعرات» · وأن التصنيف الرقمي للنوع صحيح لكل مُصنّع
(الأنواع فوق ٦٥٥٣٥ تبقى `OTHER` بلا تخمين).

---

## تكملة ١٩ — **مراجعة** لما أُنجز (بلا ميزات جديدة): المنطق والاتّساق مع نظام التصميم

**نطاق المراجعة:** `GAP-07` (`PermissionsScreen` · `AppOpsUtil` · `PermissionPolicy`) و`GAP-11`
(`SensorInventoryCard` · `SensorInventory`) و`GAP-12/13` (`ConfigBackupFlow`) وبطاقتا المدخل في
`AppSettingsScreen` — مقابل نظام التصميم (`MaxListScreen` · `MaxSection` · `MaxRow` · `MaxMetric` ·
`MaxConfirmDialog` · `MaxRadius`) وشاشات مماثلة (`Dex2oatScreen` · شاشة التشخيص).

### خمسة عيوب حقيقية — أُصلحت

1. **إحصاء الاستعادة كان يدمج نتيجتين متعاكستين.** `APPLIED_AS_DEFAULT` (نجاح: رُفع التجاوز) كان
   يُجمع مع `IGNORED_BY_DEVICE` (الجهاز أبقى قيمته — وليس نجاحًا) في رقم واحد اسمه
   «removed-or-kept». أي أن الشاشة التي تفصل **خمس** نتائج في كل موضع آخر، تُلغي التمييز في
   اللحظة الحاسمة. ⇒ فُصلا إلى خمسة أرقام، والنصوص (EN/AR) تحدّثت معها.
2. **زرّ «نسخ» كان يعتمد على قياس غير متزامن** (`RootUtils.isRootGranted()` + قراءة ملفات
   التفضيلات) ⇒ كان يمكن أن يظهر **معطّلًا لحظةً ثم يُفعَّل** بلا أن يفعل المستخدم شيئًا. وزرّ
   يومض لا يقول شيئًا عن المستخدم بل عن توقيت قياسنا. ⇒ الزرّ يعتمد على **اختيار المستخدم وحده**،
   والإعلان يُضاف متى وصل القياس، و`null` تعني **«لم يُقس»** لا «قيس فلم يبق شيء».
3. **حوار اختيار الوضع كان خارج عائلة الحوارات.** `PermissionsScreen` كانت **الشاشة الوحيدة في
   المستودع** التي تستعمل `material3.AlertDialog` خامًا (الآخر الوحيد هو نظام التصميم نفسه)،
   وتفوتها `shape = RoundedCornerShape(MaxRadius.sheet)` التي تحملها كل حوارات `MaxConfirmDialog`.
   ⇒ طُبِّق نصف القطر نفسه.
4. **بطاقة المستشعرات كانت تُنشئ مفردات ثقة موازية.** نظام التصميم فيه `MaxDataTrust.Unreadable`
   = «المصدر موجود ولم يُرجع شيئًا مقروءًا»، و`Unsupported` = «المصدر غير موجود على هذا الجهاز» —
   وهما **بالضبط** حالتانا. وكانت البطاقة تُعيد اختراعهما بنصوص خاصّة. ⇒ القراءة تُعرض بـ
   `MaxMetricReadout`/`MaxMetric`، والعارض **يرفض طباعة قيمة** حين تقول الثقة إن لا شيء حقيقي
   يُطبع — فصار «لم أقرأ لا يصير صفرًا» **حكمًا في المكوّن** لا انتباهًا في كاتب الشاشة.
   (و`max_sensor_light_reported` حُذف واستُبدل `max_sensor_unit_lux` في اللغتين.)
5. **سطر معاينة الاستعادة كان نصًّا مركَّبًا بسهم ونقطتين صلبين** (`"$op: ref ← now"`) — خطر bidi
   في العربية، **ومورد السطر موجود أصلًا** (`max_perms_drift_line`) وتستعمله قائمة الانحراف فوق
   في نفس الشاشة. ⇒ صار بالمورد.

### ما فُحص فلم يُوجد فيه عطب

البطاقتان في شاشة إعدادات التطبيق **متطابقتان بنيويًّا** وكلتاهما مُقيَّدة بـ`packageName?.let` ·
الرسم والوجهات والأذونات سليمة · لا تعارض أسماء/مفاتيح موارد · `MaxListScreen` مُستعمل بنفس صيغة
`Dex2oatScreen` · أسماء الأوضاع ومفاتيح الانحراف بسطور `AndroidManifest` متطابقة.

### ما تركته **بقرار معلَن** (وليس سهوًا)

- `destructive = true` على حوار الاستعادة: نظام التصميم يستعمله **لتلوين** زرّ التأكيد فقط، والاستعادة
  تكتب فوق تجاوزات حالية لا تُحفظ نسخة منها ⇒ التحذير مُبرَّر. قرار مالك لا «إصلاح».
- `reload()` تُعيد `Triple` وتُقرأ بـ`.second.first`: **مقروئية لا خطأ**؛ وتغييرها لمسة تجميلية
  لا تُبرّر بمواجهة ADR-18.
- وحدات `mA`/`us` نصًّا في سطر المستشعر: العُرف القائم في المستودع يكتب الوحدات نصًّا (`MHz` و`CPU`
  في قائمة الدَّين نفسها) ⇒ مخالفتها الآن اتّساق زائف.
- `SensorDetailRow` مكرَّرة مع `DetailRow` في شاشة التشخيص: كل شاشة تحمل سطرها الخاص في هذا
  المستودع، والنسخ موضعي لا مشترك.
- `pkg` فارغ من الرسم: المسار لا يُطلَق إلا بمعرّف حقيقي، والحالة تُعرض «تعذّرت القراءة» لا أسوأ.

**التحقق:** `compileDebugKotlin` نجاح · `testDebugUnitTest` **٤٣٢ اختبارًا / ٤٣ صنفًا / ٠ فشل** ·
`code_health --assert` exit 0 (الدَّين 10·29·80·26 بلا نمو) · `i18n_coverage --assert` exit 0.
**ولم ألتزم شيئًا في git.**

---

## تكملة ٢٠ — **عطب كراش شاشة GPU**: سبب مُعاد إنتاجه لا مُخمَّن

**العَرَض:** فتح صفحة GPU يُخرج التطبيق فورًا.

**السبب (مُثبَت لا مُقدَّر):** `GpuStudioScreen` كان يطلب الـViewModel بـ
`viewModel: GpuStudioViewModel = viewModel()`. و`viewModel()` بلا مصنع يستعمل
`ViewModelProvider.NewInstanceFactory` الذي ينادي المُنشئ **بلا وسائط**. و`GpuStudioViewModel`
مُعرَّف `@Inject constructor(private val arbiter: HardwareControlArbiter)` — أي **لا مُنشئ له بلا
وسائط أصلًا** ⇒ `RuntimeException: Cannot create an instance of class …GpuStudioViewModel` لحظة
التركيب. ولهذا خرج التطبيق ولم تظهر شاشة خطأ: العطب قبل أي رسم.

**والدليل على أنه هو: المقابلة الكاملة.** ViewModels المُحقونة في المستودع أربعة:
`CpuCoreControlViewModel` · `MaxAiViewModel` · `HomeViewModel` · `GpuStudioViewModel`.
الثلاثة الأولى تُطلَب بـ`hiltViewModel()` — **والرابع وحده بـ`viewModel()`**. وشاشة GPU كانت
الشاشة الوحيدة الخارجة عن القالب.

**وعطب ثانٍ في الصنف نفسه:** `GpuStudioViewModel` هو **الوحيد** الذي ينقصه `@HiltViewModel` من
الأربعة ⇒ لو بدّلنا `viewModel()` بـ`hiltViewModel()` وحده لما استطاع Hilt أن يبنيه. فالإصلاح
كان **اثنين معًا**: `@HiltViewModel` على الصنف (كقالب `CpuCoreControlViewModel` حرفيًّا)،
و`hiltViewModel()` في الشاشة.

**اختبار يمنع تكرار النوع كله** (`ViewModelInstantiationTest`): يقابل **كل صنف `@Inject
constructor` بمكان طلبه**، ويفشل إن طُلب أحدهم بـ`viewModel()`. أُثبت أنه يكشف العطب فعلًا:
شُغّل **قبل** الإصلاح ففشل بهذه العبارة حرفيًّا —
`[GpuStudioScreen.kt: GpuStudioViewModel = viewModel()]` — ثم نجح بعد الإصلاح. وهذا نوع خطأ
لا يكشفه لا التصريف ولا المراجعة: يحتاج تشغيلًا على جهاز.

**التحقق:** `compileDebugKotlin` نجاح (وHilt بنى الـbinding الجديد عبر KSP) ·
`testDebugUnitTest` **٤٣٤ اختبارًا / ٤٤ صنفًا / ٠ فشل** (+٢) · `code_health --assert` exit 0
(الدَّين 10·29·80·26 بلا نمو) · `i18n_coverage --assert` exit 0.

**غير مُثبَت:** أن الشاشة **تعمل** بعد الفتح — الإصلاح يزيل سبب الخروج المؤكَّد، لكن محتوى الشاشة
(قراءة devfreq · الشرائح · «الحفظ في Tweaks») لم يُشغَّل على جهاز قطّ.

---

## تكملة ٢١ — كراش **شاشة SetEdit عند التمرير**: المفتاح المكرَّر في `LazyColumn`

**العَرَض:** الشاشة تخرج **عند التمرير فقط**، لا عند الفتح.

**الآلية الوحيدة في الملف التي ترمي مع التمرير وحده:** القائمة مبنيّة بـ
`items(viewModel.filteredItems, key = { "${it.category}:${it.key}" })`، وCompose **يرمي** إذا
تكرّر مفتاح صفّ: `IllegalArgumentException: Key … was already used`. وهو عطب **لا يظهر عند
الفتح**، لأن الصفّ المكرّر لا يُبنى إلا حين يدخل نطاق العرض — أي **أثناء التمرير**، وهذا مطابق
للعَرَض حرفيًّا.

**والفرق الذي يجعل هذا مرجَّحًا لا مُخمَّنًا:** المستودع **يُنقّي مخرج الأوامر في كل موضع آخر**
(`RootFileAccess.globDirectories` → `.distinct()` · `GpuHardwareBackend` · `CpuHardwareBackend`)،
لأن مخرج shell **يتكرّر فعلًا**. و`SetEdit` كانت **الشاشة الوحيدة** التي تبني **مفاتيح قائمة**
من مخرج خام بلا أي تنقية — أي أنها كانت الوحيدة التي تعتمد على تفرد لم يُثبته أحد.

**الإصلاح — التفرد صار مضمونًا بالبناء لا مفترَضًا:**
- `SetEditUtil.parseOutput(category, output)` — تحليل **خالص وقابل للاختبار** بعيدًا عن shell.
- `SetEditUtil.dedupe(...)` — يحفظ **الأول** ويُسقِط ما بعده، و**يسجّل العدد** إن وُجد
  (`EventLog.error` باسم `duplicate_keys`) فلا يُسكت عن تكرار.
- `SetEditItem.lazyKey` — صيغة المفتاح **في مكان واحد**، فتستعملها الشاشة ويختبرها الاختبار،
  ولا تبقى صيغةٌ تُكتب في الشاشة وتتغيّر وحدها.

**عطب ثانٍ كشفه الاختبار:** `parseSettingsLine` كان يقبل `"="` فيُنتج عنصرًا **بمفتاح فارغ** —
و`parseGetpropLine` مثلها مع `[]: [value]`. وصفٌّ بمفتاح فارغ لا يُقرأ ولا يُبحث عنه، وتكتبه
الشاشة لاحقًا إلى الجهاز باسم `settings put <ns> ""` — **أمر يكتب في لا شيء**. ⇒ يُسقَطان الآن.

**اختبارات (١٢):** القطع على أول `=` لا أخره · الأسطر بلا فاصل تُسقَط · صيغة `getprop` ·
قيمة تحمل `]: [` تُسقَط · المدخل السيّئ لا يرمي · **ولا مفتاح فارغ ينجو** · التكرار يُدمج والأول
يفوز · نفس المفتاح في صنفين مختلفين **ليس** تكرارًا · و**كل `lazyKey` فريد حتى من مخرج يتكرّر ٥٠
مرّة** · وقائمة «الكل» مرتّبة وفريدة.

**التحقق:** `compileDebugKotlin` نجاح · `testDebugUnitTest` **٤٤٦ اختبارًا / ٤٥ صنفًا / ٠ فشل**
(+١٢) · `code_health --assert` exit 0 (الدَّين 10·29·80·26 بلا نمو) · `i18n_coverage --assert` exit 0.

**⚠️ حدّ الصدق في هذا التشخيص:** لم أستطع **إعادة إنتاج** الكراش (لا جهاز)، فما أثبتُّه هو أن
هذا **الآلية الوحيدة في الملف** التي ترمي مع التمرير وحده، وأنها كانت تعتمد على تفرد **لا يضمنه
شيء** — فصار مضمونًا. **إن بقي الكراش، فهو من سبب آخر** ويحتاج سطر `logcat` واحدًا لتحديده.
**وللتأكيد:** تفعيل «السجل التفصيلي» ثم فتح الشاشة والتمرير ⇒ أي تكرار سيظهر في السجل باسم
`duplicate_keys`.

---

## تكملة ٢٢ — `GAP-09` مدير الملفات بالجذر · `GAP-14` عقد الطرف الثالث

**طلب المالك:** نفّذ البندين ٢ و٩، وابحث عن مشاريع قوية مفتوحة المصدر، واجعل مدير الملفات
أفضل من MT Manager VIP وبه سلاسة.

### ما نُفِّذ — `GAP-09` (مدير الملفات)

- `ui/util/FileSystemModel.kt` — **نموذج خالص** (بلا Compose وبلا أندرويد):
  تطبيع المسارات · `childPath`/`parentOf`/`breadcrumbs` · **`isInside` على حدود المسار لا
  على البادئة النصّية** (`/data2` ليست داخل `/data`) · **ترتيب طبيعي** (`file2` قبل `file10`) ·
  تصفية · `FileStatParser` · `FilePermissions` (البتّات الخاصة من **الرقم** لا من الحرف) ·
  `FileSelection` · **`FileOpGuard`** · `FileFormat` · **`DirectoryCache`** (LRU).
- `ui/util/FileSystemEngine.kt` — الطبقة الوحيدة التي تلمس shell:
  تمريرة `stat` واحدة بصيغة **معلنة** `%F\t%a\t%A\t%U\t%G\t%s\t%Y\t%l\t%n`، والاسم **آخرًا**
  فينجو اسم فيه تبويب · نزول إلى **الأسماء وحدها مُعلَنًا** إن سقط `stat` · معاينة نصّية بكشف
  ثنائي **من المحتوى لا من الامتداد** · نسخ/نقل/حذف/إعادة تسمية/إنشاء مجلد/`tar.gz`،
  **وتحقّق من الهدف بعد التنفيذ** · سياق SELinux من `ls -Zd` بحقلّين على الأقل.
- `ui/component/FileManagerPanels.kt` + `ui/subscreens/FileManagerScreen.kt` — الشاشة
  (فتات خبز · بحث · ترتيب · تحديد متعدّد · لوحة تفاصيل · معاينة نصّية) + وجهة `filemanager`
  تحت `Control → Tools` بوسم `MaxRisk.Advanced`.
- `ui/design/MaxDialogs.kt` — **`MaxInputDialog`** أُضيف كنمط نظامي واحد (بنصف قطر
  `MaxRadius.sheet` نفسه)، بدل أن تُنشئ الشاشة حوارًا محليًّا خارج العائلة.

**القرارات الحاكمة، وهي التي تميّز البند:**

1. **`ls` لا يُحلَّل للحصول على بيانات أبدًا** — مأخوذ من درس `MaterialFiles` حرفيًّا
   («not yet another ls parser»). البيانات من `stat -c` بحقول مفصولة.
2. **ثلاثة أحكام لا حكمان:** «نُفِّذ وتُحقِّق منه» ≠ «نُفِّذ ولم يُتحقَّق» ≠ «فشل».
3. **الحرس قبل الـshell:** رفض `TargetInsideSource` · `SelfTarget` · `ProtectedPath` (الجذر `/`)
   · `InvalidName` · `NameTaken` — وكل واحد له اختبار.
4. **السلاسة:** `DirectoryCache` — المجلد المزار يُعرض **من الذاكرة في الإطار نفسه** ثم
   تُقرأ نسخته الطازجة في الخلفية، فلا يُستبدل المحتوى بمؤشّر تحميل عند الرجوع.

### ما نُفِّذ — `GAP-14` (عقد الطرف الثالث)

- `ui/util/PluginContract.kt` — نسخة `PLUGIN_API_LEVEL=1` · `PluginCapability` (**مجموعة مغلقة**:
  `read.telemetry` · `read.hardware` · `propose.profile` · `subscribe.event` · `report.instrument`)
  · `PluginKind` · `PluginManifest` · `PluginRejection` (١٠ أسباب) · محلّل صارم · `evaluate` جماعي ·
  **`manifestTemplate()` مُولَّد من العقد نفسه**.
- `ui/util/PluginDirectory.kt` — القارئ الوحيد للقرص، ويُفرّق **مجلد غائب** عن **مجلد لا يُقرأ**.
- `ui/subscreens/PluginsScreen.kt` + وجهة `plugins` تحت الإعدادات.

**والقاعدتان الملزمتان:** لا توجد قدرة تكتب العتاد (وغيابها **مُختبَر**)، والقدرة المجهولة
**تُرفض ولا تُتجاهل** — فتشغيل إضافة بصلاحيات أقلّ مما طلبت سلوك لم يوافق عليه أحد.

### البحث الخارجي في هذه الجولة

قُرئت README كاملة لـ: `zhanghai/MaterialFiles` (لا محلّل `ls` · NIO2 خلفية منفصلة ·
Linux-aware: روابط وSELinux) · `AbdurazaaqMohammed/MP-Manager` (بديل مفتوح المصدر لـMT Manager:
لوحان · مرجعيات وسجل · بحث متقدّم · ZIP/APK · إعادة تسمية بقوالب · وسائط · FTP · أدوات APK).
وأُضيفت **١٤ فئة `FM`** إلى §٧ من `UNIMPLEMENTED-PROPOSALS.md` **وما لم يُنفَّذ بعد** — ولم
يُدَّعَ منها شيء. وحُدِّد صراحةً ما **لا** يُؤخذ (تفكيك/رفع حماية APK وتصحيح DEX: إعادة هندسة
تطبيقات الغير، `↔AR-R7`).

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **٤٩٤ اختبارًا / ٤٧ صنفًا / ٠ فشل / ٠ خطأ** (+٤٨ عن ٤٤٦) |
| `code_health --assert` | exit 0 — الدَّين **10·29·80·26 بلا نمو** |
| `i18n_coverage --assert` | exit 0 (EN + AR) |

**اختباران يستحقّان الذكر:** (١) اختبار يربط **صيغة `stat` المُنفَّذة** بعدد حقول المحلّل —
فلا يتغيّر أحدهما وحده؛ (٢) اختبار يربط **مفاتيح القالب المعروض** بمفاتيح المحلّل — فالتوثيق
المعروض للطرف الثالث لا يستطيع أن يتقادم بصمت.

### ⚠️ حدود الصدق — تُقرأ قبل الحكم

- **«أفضل من MT Manager» لا تُدَّعى.** ما نُفِّذ: تصفّح بالجذر · ترتيب طبيعي · حرس عمليات ·
  تحقّق بعد التنفيذ · تفاصيل بسياق SELinux · معاينة نصّية · ضغط/فكّ `tar.gz` · سلاسة بذاكرة.
  وMT Manager يسبقنا في **لوحين · ZIP/APK · توقيع APK · إعادة تسمية جماعية · وسائط · FTP ·
  محرّر نصوص** — وهي مسجّلة `FM-01…FM-14` **غير منفَّذة**.
- **لم يُشغَّل شيء على جهاز.** سلوك `stat` و`tar` و`ls -Zd` من toybox، ومسار `/data/adb/...`
  للإضافات، ومهلة القراءة — كلها تحتاج قياسًا على جهازك.
- **عقد الإضافات معلن ولم يُوصل**: لا مستهلك لـ`propose.profile` بعد، والاقتراح يجب أن يمرّ
  بالـarbiter حين يُوصل.
- **لم أمسّ تعطيل الحرارة.** ولم ألتزم شيئًا في git.

---

## تكملة ٢٣ — `FM-01` لوحا مدير الملفات جنبًا إلى جنب

**الطلب:** اكمل، واجعل مدير الملفات **لوحين جانبًا إلى جنب**.

### ما نُفِّذ

- `ui/util/FilePaneModel.kt` — نموذج خالص: `PaneSide` · `FilePaneState` · `DualPane`.
  و`DualPane.transferRequest` يبني الطلب **من اللوحين** ثم يُمرَّر إلى **نفس** `FileOpGuard`
  — فقواعد الحماية لم تُكتب مرّة ثانية في الواجهة.
- `ui/design/MaxScreenScaffold.kt` — **`MaxSplitScreen`** أُضيف: نفس شريط العنوان ومعالجة
  الحالة، لكن الجسم **ارتفاع ثابت لا تمرير عمودي**. السبب المعماري إلزامي: `LazyColumn`
  داخل `Column` بتمرير عمودي يُقاس بارتفاع لا نهائي **ويرمي استثناءً**، فصفحة ذات أكثر من
  قائمة كسولة لا تستطيع استخدام `MaxScreen` أصلًا. والبديل — `Scaffold` محلي في الشاشة —
  هو الانحراف الذي وُجد هذا الملف لمنعه.
- `ui/component/FilePaneColumn.kt` — عمود اللوح: رأسه · مساره · بحثه · **قائمته الكسولة**.
- `ui/subscreens/FileManagerScreen.kt` — منظّم اللوحين (`BoxWithConstraints`).
- `ui/design/MaxTokens.kt` — `MaxSize.activeRing = 2.dp`.

### القرارات التي جعلت اللوحين آمنين ومفهومين

1. **الوجهة هي اللوح الآخر.** لا يكتب المستخدم مسارًا ولا يُنسخ إلى مكان لم يره — وهذا
   معنى اللوحين. ومعنى ذلك أن الحرس يصير **أهمّ لا أقلّ**: لوحان على نفس الشجرة هو بالضبط
   الشكل الذي يظهر فيه «نسخ مجلد داخل نفسه». اختباران يثبتان أن الطلب المبنيّ من اللوحين
   يرفض `TargetInsideSource` و`SelfTarget` — عبر الحرس المشترك لا عبر قاعدة جديدة.
2. **اللوح النشط معلن.** كل إجراء (صعود · تحديث · مجلد جديد · تحديد · ترتيب · نقل) يُطبَّق
   على اللوح النشط وحده، وهو مُعلَّم بـ**إطار أعرض** ولون معًا — فاللون وحده ليس جوابًا
   لمن لا يراه.
3. **حرس القراءة العالقة:** نتيجة قراءة مجلد **غادرناه أثناء القراءة** لا تُكتب عليه، وإلا
   عرض لوحٌ محتوى مجلد آخر. (عطب حقيقي في أي تنقّل غير متزامن.)
4. **بعد كل عملية تُبطَل الذاكرة ويُحدَّث اللوحان معًا** — لأن اللوح الآخر كثيرًا ما يكون
   هو المقصد نفسه.
5. **العرض الضيّق يُرصّ اللوحين فوق بعضهما** (< 600dp) تلقائيًّا بدل أن يتضايقا في نصف عرض
   هاتف. ومعها `PaneSaver` يحفظ مسار كل لوح عبر تدوير الجهاز.
6. **المزامنة والتبديل** في شريط العنوان: المزامنة تنقل المسار وحده (لا تمسّ بحث اللوح
   الآخر)، والتبديل يصفّر الحالة المرتبطة بالمجلد القديم.

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **٥٠٩ اختبارًا / ٤٨ صنفًا / ٠ فشل / ٠ خطأ** (+١٥) |
| `code_health --assert` | exit 0 — الدَّين **10·29·80·26 بلا نمو** · وكل ملفاتي تحت ١٠٠٠ سطر |
| `i18n_coverage --assert` | exit 0 (EN + AR) |

**عطبان حقيقيان ظهرا أثناء التصريف وصُلِّحا لا تُجوهلا:** (١) استدعاء دالّة `@Composable`
(`paneBanner`) **داخل نطاق `LazyColumn`** — ونطاق القائمة الكسولة ليس سياق تركيب؛ فُصل
الحساب قبله. (٢) `mutableStateOf(SelinuxState.NotQueried)` استنتج نوع **الكائن** لا الواجهة،
فصار `mutableStateOf<SelinuxState>(...)`.

### ⚠️ ما لم يُغلق

- **لم يُشغَّل على جهاز.** العرض المنقسم، وسلوك `stat`/`tar`، والحدّ ٦٠٠dp — كلها تحتاج قياسك.
- **`FM-02…FM-14` غير منفَّذة** (مرجعيات · بحث متقدّم · ZIP/APK · إعادة تسمية جماعية · وسائط ·
  FTP · محرّر نصوص · مقارنة · أدوات APK · شريط تخزين · مشاركة · مشغّل أوامر).
- **لم أمسّ تعطيل الحرارة.** ولم ألتزم شيئًا في git.

---

## تكملة ٢٤ — إغلاق **تحكّمين ميّتين** كشفتهما إعادة كتابة اللوحين

**العطب (مُتحقَّق بـ`grep` لا مُخمَّن):** بعد تحويل الشاشة إلى لوحين، صار `renameTarget`
يُصفَّر ولا **يُسنَد** في أي موضع ⇒ **«إعادة التسمية» غير قابلة للوصول**. وبقي
`FileOperation.Extract` معرَّفًا في النموذج والحرس والمنفّذ مع **لا زرّ يناديه** ⇒
**فكّ الأرشيف غير قابل للوصول**.

والدرس أنّ هذا الصنف من العطب **لا يكشفه تصريف ولا اختبار يفحص ما هو موجود** — بل ما
هو **موصول**. والعطب ظهر بالبحث عن كل قيمة في القائمة وأين تُنتَج.

### الإصلاح — القرار صار نموذجًا مختبَرًا

- `ui/util/FileActionModel.kt` — `FileAction` (**انتقلت من طبقة العرض** فهي هوية لا رسم)
  · `FileArchive` · **`FileActionSet.forSelection(entries, selection)`**.
- قاعدة الإتاحة: **الإجراء غير المناسب لا يُعرض معطّلًا بل لا يُعرض أصلًا** — زرّ «فكّ»
  على ملف ليس أرشيفًا يَعِد بما لا يمكن.
  إعادة التسمية والتفاصيل = مدخل **واحد بالضبط**؛ الفكّ = أرشيف **واحد** محدَّد.
- **والنوع يُقرأ من الجهاز لا من الاسم**: مجلد اسمه `backup.tar.gz` ليس أرشيفًا (اختبار).
- فكّ الأرشيف يذهب إلى **مجلد اللوح الحالي** — لا حوار وجهة لعملية نتيجتها مرئية فورًا.
- `labelRes` انتقلت إلى دالّة عرض في طبقة المكوّنات: النموذج لا يعرف `R`.

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **٥١٩ اختبارًا / ٤٩ صنفًا / ٠ فشل / ٠ خطأ** (+١٠) |
| `code_health --assert` | exit 0 — الدَّين **10·29·80·26 بلا نمو** |
| `i18n_coverage --assert` | exit 0 (EN + AR) |

**ومانع الانحدار:** اختبار يثبت أن **كل `FileAction` مُنتَج بتحديدٍ ما** — فأي إجراء جديد
يُضاف بلا مسار يُسقط الاختبار بدل أن يبقى زرًّا بلا فعل. (وهو نفس صنف ضمان
`ViewModelInstantiationTest` من تكملة ٢٠: ربط التعريف بمكان الاستعمال.)

**ولم يُشغَّل على جهاز، ولم يُلتزم شيء في git.**

---

## تكملة ٢٤ — نقل «Tools» إلى Control، وشاشتان رئيسيتان مستقلّتان، ومدخلان مباشران (جولة ربط)

طلب المالك ثلاث نقلات معًا، وكلهنّ **تخطيط وصهر مداخل** لا ميزات جديدة — فلم يُضَف منطق، بل
صُحِّح **مكان** كل شيء و**نقطة دخوله**.

### ١) `Tools` صار قسمًا واحدًا في `Control`

`MaxDestination.MaxBackup` و`Permissions` (AppOps) و`FileManager` و`Plugins` انتقلوا إلى بنك
الأدوات المتقدّمة في `ControlLayoutModel.controlToolEntries()` مع `Terminal` و`SetEdit` وغيرهم.
وأُزيلت مداخلهنّ من شاشة `Apps` — فالأداة التي **تكتب على العتاد أو نطاقًا خارج إعداداتنا** ليست
تفضيلًا يُعرض بين تفضيلات التطبيق، بل أداة لها `MaxRisk` وحارس (ADR-16 حرفيًّا).

### ٢) شاشتان رئيسيتان مستقلّتان — عقد `pkg` اختياري

- `MaxBackup` ⇒ `max_backup?pkg={pkg}` · `Permissions` ⇒ `max_perms?pkg={pkg}`.
- **بلا `pkg`**: الشاشة **رئيسية بذاتها** تعرض قائمتها المستقلة (منتقي تطبيقات Max Backup؛
  منتقي تطبيقات AppOps).
- **بـ`pkg`**: تفتح **مباشرة** على ذلك التطبيق — بلا شاشة رئيسية وبلا قائمة.

وهذا **عقدٌ واحد بحرفين**: الشاشتان تتبعان نفس الصيغة، ونفس افتراض `defaultValue = ""` في
`MaxNavGraph`، ونفس `takeIf { it.isNotBlank() }` — فلا تُصاغ قاعدة لكل شاشة على حِدة.

### ٣) مدخلان مباشران في `App Settings` بنفس شكل `Control map`

`Apps → التطبيق → App Settings` صار في شريطه العلوي **زرَّي أيقونة**: `Max Backup` و`AppOps`،
باسميهما (`contentDescription`) وبـ`tint = onSurfaceVariant` — **مطابقان حرفيًّا** لـ`MaxHelpAction`
(وهو زرّ `Control map`)، من غير إطار ولا لون مملوء. والأسماء لم تُغيَّر.

والضغط منهما **يستبدل `{pkg}` حرفيًّا** بمعرّف التطبيق الحالي ⇒ ينزل على إعدادات هذا التطبيق
مباشرة، كما طلب المالك بالحرف.

### ٤) ما أُزيل من `App Settings` — وهذا قرار المالك

بطاقتا **«الخلفية والبطارية»** و**«مصدر التطبيق»** وبطاقة **الخلفية** (`BackgroundGovernanceCard`
و`AppSourceCard`) أُزيلت من الشاشة ومن المستودع كلّه (تعريفاتها لم تبقَ معلَّقة). صفر بقايا.

### تصحيح تعليق كاذب

كان تعليق `MaxDestination.Permissions` يقول «`pkg` **إلزامي**» بينما المسار `?pkg={pkg}` والعقد
صيّره اختياريًّا ⇒ صُحِّح التعليق ليصف الكود لا ليخالفه. (تعليق يخالف الكود أسوأ من غيابه.)

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **٥١٩ اختبارًا / ٤٩ صنفًا / ٠ فشل / ٠ خطأ** |
| `code_health --assert` | exit 0 — الدَّين **10·29·80·26** (و`presentation_hw_writes` نزل 27→26) |
| `i18n_coverage --assert` | exit 0 (EN + AR) |

**ولم يُشغَّل على جهاز، ولم يُلتزم شيء في git.**

---

## تكملة ٢٥ — عطب كشفه النقل: شاشة كاملة بلا مدخل (`Plugins`)

أثناء جرد الأدوات بعد النقل، ظهر ما لم يكن في الطلب: **`PluginsScreen` شاشة لا يصل إليها أحد.**

- مسارها `plugins` **مسجّل** في `MaxNavGraph` (§77).
- دورها **مكتوب** في `MaxDestinationCatalog` (`max_role_plugins`).
- صنفها **موجود** ويُصرّف، و`PluginsScreen` تذكر `MaxDestination.Plugins.icon` في ترويستها.
- **ولا مدخل واحد في أي شاشة.** ⇒ البناء أخضر، والبوابات خضراء، والشاشة موجودة، ولا أحد يفتحها.

وهذا صنف عطب **لا تغطّيه البوابات الموجودة**: `code_health` يفحص الأصول المتقاطعة لا الطرق؛ وفتح
التطبيق لا يكشفه — لأنّ ما لا مدخل له لا يُضغط.

### الإصلاح — قرار العقد لا قرار المزاج

النقل إلى `Control → Tools` كان الطريق الأسهل، لكن تعليق الوجهة نفسه في السجل يحمل قرارًا مدوَّنًا:
«تحت الإعدادات لأنها **حالة النظام** لا تحكّم أداء، ولأن نصّ العقد يجب أن يكون في متناول من يكتب
إضافة». فأُبقيت في `Settings`، ووُصلت **بصفّ واحد** في `SettingsScreen` بين `ConfigBackup` و`Privilege`
(بـ`max_plugins_title` + `max_plugins_subtitle` و`Icons.Rounded.Extension`). وما لم يُنفَّذ كان **الوصل**، لا المكان.

### مانع الانحدار — واختبار لا يستطيع الفشل ليس اختبارًا

`SettingsDestinationReachabilityTest` يقابل كل وجهة تحت `Settings` بـ**مكان ذِكرها في الكود**.

وقد كُتب أولًا بالشكل السهل — «هل تُذكر في أي ملف واجهة؟» — فتبيّن أنه **يمرّ على شاشة ميتة**:
`PluginsScreen` تذكر نفسها، فيُحسب ذِكرها الذاتي مدخلًا. ⇒ شُدّد: **شاشة الوجهة لا تُحتسب مدخلًا
لنفسها** (`!it.startsWith(name)`)، ويُشترط ملفّ آخر. وسقف الثقة في هذا الاستدلال (العُرف: شاشة الوجهة
تُسمّى باسمها) **معلن في الـKDoc** لا مخفيّ.

**وأُثبت أنّه يفشل فعلًا** لا أنّه يمرّ: عُدِّل `SettingsScreen` مؤقتًا فأشار الاختبار بالاسم —
`Plugins (plugins)` — ثم أُعيد الملف كما كان.

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **٥٢١ اختبارًا / ٥٠ صنفًا / ٠ فشل / ٠ خطأ** (+٢) |
| `code_health --assert` | exit 0 — الدَّين **10·29·80·26 بلا نمو** |
| `i18n_coverage --assert` | exit 0 (EN + AR) |

**ولم يُشغَّل على جهاز، ولم يُلتزم شيء في git.**

---

## تكملة ٢٦ — مراجعة اللوحين: التوصيل المكرَّر، وأيقونة تنقلب في RTL، ونسخ يكذب

اللوحان **جانبًا إلى جنب منذ تكملة ٢٣** (`Row` بلوحي `weight(1f)` عند `maxWidth >= 600dp`،
ويُرصّان فوق بعضهما في العرض الضيّق). فراجعتُ ما بُني لا ما لم يُبنَ — فظهرت ثلاثة عيوب حقيقية.

### ١) سلوك اللوح كان مكتوبًا في **أربعة مواضع**

`LeftPane` و`RightPane` كانا غلافين حرفيًّا حول `FilePaneColumn`، ومن أجل اختلاف `side` وحدها
كُتب **كل منطق النقر والبحث مرّتين في `Row` ومرّتين في `Column`** — أربع نسخ متطابقة:

```
onEntryClick = { when { selecting -> toggle; isDirectory -> navigate; else -> preview } }
```

أي أن تغييرًا واحدًا في سلوك النقر يجب أن يُكتب أربع مرّات، **ومن ينسى واحدة يُنتج لوحين
يتصرّفان بشكلين** بلا أن يشتكي المصرّف. ⇒ صار لكل لوح **توصيل واحد** (`renderPane`)،
والغلافان حُذفا، والفرق الوحيد بين الاتجاهين صار **اتجاه التخطيط** لا المحتوى.

**وفائدة حقيقية ظهرت أثناء الدمج:** الكتابة الموحّدة كشفت أن `onQueryChange` كان يقرأ الحالة
**الملتقطة عند التركيب** — فتغيير مسار اللوح ثم الكتابة في بحثه كان يمكن أن يكتب على لقطة
قديمة. صار يقرأ طازجًا (`paneOf(side)`) كبقية المعالجات.

### ٢) أيقونة تبديل اللوحين لا تنقلب في العربية

`Icons.Rounded.CompareArrows` **اتجاهية** (سهمان متقابلان)، وقائمة الأيقونات غير المنقلبة
تُنتج في RTL زرًّا **يشير إلى الجهة الخاطئة**. ⇒ `Icons.AutoMirrored.Rounded.CompareArrows`.
والمُصرّف نفسه كان يحذّر بها والتحذير مرّ بلا قراءة.

### ٣) «نُسخ المسار» كانت تُقال بلا قياس

`LocalClipboardManager.setText()` (واجهة قديمة مهمَلة) كانت تُتبع بـsnackbar **«نُسخ المسار»
دائمًا** — سواء نجح النسخ أم لا. ⇒ الانتقال إلى `LocalClipboard.setClipEntry(ClipEntry…)`
الحديثة، **والنتيجة تُقاس**: نجحت ⇒ «نُسخ المسار»، فشلت ⇒ «رفضت الحافظة المسار — لم يُنسخ شيء»
(نصّان جديدان EN+AR، ADR-14).

وهذا هو نفس الخطّ الذي منعنا أن نجعل «لم أقرأ» صفرًا: **الواجهة لا تُخبر المستخدم بنتيجة لم تُقَس.**
والفائدة الثانية للإصلاح: تحذير الإهمال في هذا الملف صار **صفرًا**.

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL** · صفر تحذير في `FileManagerScreen` |
| `:app:testDebugUnitTest` | **٥٢١ اختبارًا / ٥٠ صنفًا / ٠ فشل / ٠ خطأ** |
| `code_health --assert` | exit 0 — الدَّين **10·29·80·26 بلا نمو** |
| `i18n_coverage --assert` | exit 0 (EN + AR) |

**ولم يُشغَّل على جهاز، ولم يُلتزم شيء في git.**

---

## تكملة ٢٧ — GPU/CPU: «اخترت فلم يحدث شيء»، وإعادة تصميم، ودمج البطارية، وانكسار النص

طلب المالك سبعة أشياء في رسالة واحدة. هذا ما نُفِّذ منها، وما ثبت أنه العلة الحقيقية.

### ١) العلة الأولى: الخيار يعمل لكن الواجهة لا تقول شيئًا

**GPU**: «اختر أداء لا يتغيّر شيء». لم يكن عطبًا في العتاد: اختيار النيّة كان **يُراجَع فقط**
(`pending`)، وزرّ التطبيق كان في بطاقة (أ) **مشروطة** و(ب) **آخر عنصر** في قائمة طويلة، وبطاقة
النيّة نفسها **لا حالة اختيار لها**. ⇒ الضغط لا يُنتج أي تغيير مرئي في أي موضع من الشاشة.
ثلاثة إصلاحات: `MaxChoiceRow` (تحديد حقيقي بـ`selectable` + `RadioButton`)، ولوحة المراجعة
**تحت الضوابط المستعملة الآن** (تنتقل تحت المختبر عند فتحه)، والناتج في **شريط الشاشة العلوي** لا
في بطاقة أسفل الصفحة.

**CPU**: `applyQuickConfig` كان **لا يُبلّغ بأي شيء** — لا رسالة ولا تحديد. أُضيف تحديد للنمط
(`appliedQuickConfig`) ونتيجة مقيسة بعد إعادة القراءة (عدد الأنوية المتّصلة فعلًا)، ونمط لا يغيّر
شيئًا يُبلَّغ عنه **مرفوضًا لا ناجحًا**.

### ٢) العلة الثانية: نجاحٌ كاذب — والسبب أن النتيجة كانت تُقرأ من صياغة الجملة

الشريط العلوي في شاشة CPU كان يقرّر النجاح بـ`FREQUENCY_FAILURE_WORDS`: يبحث في نصّ الرسالة عن
`رفض`/`تعذّر`/… فحالة **مؤجَّلة** («مالك أعلى أولوية يملك هذا المقبض، طلبك محفوظ») لا تحتوي أيًّا
منها ⇒ عُرضت **نجاحًا أخضر** ولم يتغيّر على الجهاز شيء. وهذا هو أصل تبليغ المالك
«أحيانًا رفض العتاد التغيير وتمت استعادة الحالة السابقة».

⇒ `CpuActionReason` (١٤ حالة) و`GpuNoticeKind` (١٢ حالة) في الطبقة المنطقية، والشاشة تُترجم.
والمؤجَّل يُعرض **«محفوظ ولم يُطبَّق بعد»** لا نجاحًا.

### ٣) إعادة تصميم شاشة GPU على نظام التصميم

كانت الشاشة **الوحيدة** المبنية من `Card`/`TopAppBar`/`Scaffold` خامّين بـ٣٠dp وبلاطة 190×128
وبعنوان `displaySmall` وتدرّج لوني، وكل نصّ فيها **حرفيّ** (نصفه عربي داخل هيكل إنجليزي). ⇒ أُعيدت
كتابتها على `MaxListScreen`/`MaxSection`/`MaxGroup`/`MaxRow`/`MaxMetricLine`، وكل نصّها في
`max_gpu_strings.xml` (EN+AR). وأُضيف خيار **«تردد ثابت»** كمفتاح صريح بدل `FilterChip` داخل
بطاقة مطويّة كان يُقرأ كمرشّح لا كوضع يغيّر معنى المنزلقات تحته.

### ٤) دمْج شاشة البطارية في شاشة الشحن

`BatteryDetail` كان وجهة كاملة (مسار · سطر دور · `BatteryDetailScreen` · `private data class
BatteryDetail` · `loadBatteryDetail` · حلقة قراءة خاصة). دُمج في `Charging`: أُضيفت القراءات التي
كان يملكها (`healthVerdict` رأي الدرايفر، `designCapacityMah`، `fullCapacityMah`،
`batteryStatus`) من **نفس** الـViewModel الذي يملك الباقي ⇒ حلقة قراءة واحدة لا اثنتان قد تختلفان.
وحُذف كل أثر: الوجهة والمسار والرسم والنموذج والقارئ. والمصدر الواحد صار `BatteryStatus`/
`BatteryHealthVerdict` (رمز يُترجم في الشاشة) بدل النصوص الإنجليزية الحرفيّة.
**وربطٌ منطقي:** الحدّ يُقرأ الآن مقابل المستوى الحالي («يتوقف الشحن عند ٨٠٪ · والمستوى الآن ٦٢٪»).

### ٥) العلة الثالثة: القراءات كانت ملتصقة بحواف البطاقة — وهي عطب في مكوّن مشترك

`MaxMetricLine`/`MaxMetricReadout` كانتا **الوحيدتين** بين أبناء `MaxGroup` بلا حشو ذاتي (بينما
`MaxRow` يحمله). ⇒ كل موضع يضعها مباشرة داخل `MaxGroup` كان يعرض التسمية والقيمة **ملتصقتين
بالحدّ** — وهذا ما وُصف بأنه «مقصوص». الشاشات المتأثرة: `ZramManager` · `Resolution` · `MaxAi` ·
`SensorInventoryCard` · `TouchBoost` · `DozeMode`. أُصلح **المكوّن** (وحدَه) وأُزيل الحشو المضاعف
من المواضع التي أضافته.

### ٦) انكسار الكلمة حرفًا حرفًا — عطب في مكوّن مشترك آخر

`maxLines` **لا** يمنع كسر الكلمة: الكاسر الجشع يقسم الكلمة الأطول من السطر، ثم يُضيف «…».
⇒ `LineBreak.Heading` على `MaxControlRowLayout` (وهو جسم **كل** صفوف التحكّم في التطبيق، ومنها
منتقي اللغة) و`MaxRow` و`MaxMetricLine`، مع `maxLines`+`overflow` حيث كان أحدهما ناقصًا.
وهذا هو الإصلاح الذي انتقل إلى كل الشاشات بلا حلّ لكل شاشة.

### ٧) «التصاق صحة الوحدة والإنقاذ» — سببه ترتيب لا بطاقة

`LazyColumn` في `SettingsScreen` كان **الوحيد** بلا `verticalArrangement`، بينما كل صفحة تبنيها
`MaxListScreen` تستعمل `MaxSpace.row`. ⇒ آخر بطاقة في كتلة تُرصف **ملتصقة** بأول بطاقة في الكتلة
التالية. وكان «صحة الوحدة والإنقاذ» آخر صفّ في قسم الميزات، فبدا ملتصقًا بكتلة المفاتيح تحته.
أُصلح الترتيب على مستوى الصفحة.

### ٨) مفاتيح النصّ وإعادة التسمية واللفظ

- **GPU**: لم يكن لها ملفّ نصوص **أصلًا**؛ صار `max_gpu_strings.xml` (~٩٠ مفتاحًا، EN+AR).
- **مصهر النواة**: أُعيد تصميمه على رموز التصميم بلا لمس منطق الفلاش — شريط عنوان بالعنوان الذي
  تستعمله الشاشات (`titleLarge`)، لا كرة تدور وتنبض؛ ولا ظلّ على الترويسة؛ ولا أضواء نافذة
  مزيفة؛ ولا `uppercase` ولا `Color(0xFF121212)`؛ و`MaxRadius.group` بدل ٢٤/٢ٰ٠.
- **`zram_title`**: «ضغط الذاكرة» ⇒ **«الذاكرة»** / «Memory Compression» ⇒ **Memory** (كل اللغات).
- **«لقطة»**: أُزيل اللفظ من `max_memory_ledger_desc` و`max_memory_snapshots` و`max_memory_trend`
  وتوأمه — صارت «قراءة» بلا تغيير في المعنى (`MaxDataTrust.Snapshot` كمصطلح ثقة بقي كما هو).

### الحرّاس الجدّد — ثلاثة أعطاب «لا يكشفها شيء»

| الاختبار | العطب الذي يمنعه |
| --- | --- |
| `SettingsDestinationReachabilityTest` | وجهة تحت Settings بلا مدخل في أي شاشة |
| `MergedDestinationCleanupTest` | بقايا وجهة دُمجت (مسار/نموذج/مكوّن) — **والفحص ينزع التعليقات** لأن شرح الدمج توثيق لا كود ميت |
| `OutcomeFromCodeNotWordingTest` | شاشة تقرّر نتيجةً بمسح نصّ الرسالة |

**والثلاثة أُثبت أنها تفشل فعلًا** لا أنها تمرّ: كلٌّ منها أُعيد عليه العطب مؤقّتًا فأشار به بالاسم،
ثم نجح بعد إزالته. وفي الثاني اكتُشف عطب في الاختبار نفسه: `\bFAILURE_WORDS\b` كان **يمرّ** على
`FREQUENCY_FAILURE_WORDS` لأن `_` حرف كلمة في التعبير النمطي فلا حدَّ بينهما — أُصلح وأُعيد التحقق.

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **٥٢٦ اختبارًا / ٥٢ صنفًا / ٠ فشل / ٠ خطأ** |
| `code_health --assert` | exit 0 — و`hardcoded_ui_literals` نزل **80 → 66** |
| `i18n_coverage --assert` | exit 0 (EN + AR + es + fr) |

**ولم يُشغَّل على جهاز، ولم يُلتزم شيء في git.**

---

## تكملة ٢٨ — عطب مُثبَت: «أختار فلا يتغير شيء» في شاشة GPU

**المنفّذ:** الطبقة ١ (DeepSeek V4.1 Flash) · **النموذج:** بلا تصعيد · **الحالة:** `DONE_WITH_CONCERNS`

### العلة الحقيقية لم تكن في العتاد ولا في التصميم

`GpuStudioViewModel.applyPreview` كان يُمرّر إلى المُحكِّم صيغةً **لا تحمل إلا المدى**:

```kotlin
val desired = "${pending.minFreq ?: ""}:${pending.maxFreq ?: ""}"
```

والمُحكِّم يُثبت المعاملة **بتساوي نصّين**: `desired` مع ما يقرؤه من السائق بعد الكتابة.
- **طلب المُحكِّم (governor) وحده** ⇒ `min/max` فارغان ⇒ `desired == ":"`، والمقروء دائمًا
  `"<min>:<max>"` ⇒ **لا يساوي أبدًا**.
- **تحرير قفل OPP الثابت (MediaTek، نيّة «متوازن»)** ⇒ الأمر نفسه: `desired == ":"` والمقروء
  `"<min>:<max>"` ⇒ لا يساوي أبدًا.

وفي الحالتين **تنجح الكتابة فعلًا**، فلا يساوي المقروءُ المطلوبَ، فيستدعي المُحكِّم
`failAndForget` ويكتب **خط الأساس فوق التغيير الناجح**. أي أن المستخدم كان يرى
«اخترت فلا يتغيّر شيء» + «تمت استعادة الحالة السابقة» — على تغييرٍ نجح على العتاد
ثم أعاده المُحكِّم بنفسه. مسارات الشاشة الثلاثة المتأثرة: منتقي المُحكِّم،
«تردد ثابت» (`stageLock`)، وتحرير القفل من النيّة المتوازنة على MediaTek.

### الإصلاح

صيغة قيمة واحدة لـ`gpu_frequency:<device>` في `GpuHardwareBackend`:

| الدالة | ما تفعله |
| --- | --- |
| `encodeRequest(request)` | تحمل **كل** حقل يمكن للطلب أن يمسّه: المدى · المُحكِّم · قفل OPP |
| `encodeLive(device, request, io)` | تُسقِط الحالة الحية على **نفس** الحقول التي يمسّها هذا الطلب وحده |
| `fixedLockReleased(device, io)` | القفل مُحرَّر؟ مسار غائب = صحيح؛ مسار موجود غير مقروء = **خطأ** (لا يُدّعى تحرير قفل لم يُقرأ) |

فصار التساوي معناه «هذا الطلب مُلبّى» لا شيئًا آخر. والـViewModel يُطبِّق `pending` نفسه
بدل إعادة فكّ نصّ — مُحصَّن ضد إضافة حقل جديد للمخطط لاحقًا.

### التحقق — والعطب أُعيد إثباته لا ادُّعي

| الفحص | النتيجة |
| --- | --- |
| `GpuControlModelTest` (٢٤ اختبارًا) | **BUILD SUCCESSFUL** |
| **إعادة العطب مؤقّتًا** (صيغة المدى وحده) | **٦ اختبارات فشلت بالاسم**، فيها `governorOnly…` و`releasedFixedLock…` ثم أُعيد الإصلاح |
| `:app:testDebugUnitTest` | **٥٣٢ اختبارًا / ٥٢ صنفًا / ٠ فشل / ٠ خطأ** (+٦) |
| `code_health --assert` | exit 0 — الدَّين **10·29·66·26 بلا نمو** |
| `i18n_coverage --assert` | exit 0 |

**ولم يُشغَّل على جهاز.** والصيغة تخصّ شاشة GPU وحدها؛ كل مُقدِّم آخر للمفتاح نفسه
(`ControlRegistry` · `PerAppFrequencyController` · `AppMonitor`) متسق داخليًا بصيغته
الخاصة، لأن المُحكِّم لا يقارن إلا داخل الطلب الواحد.

### تكملة ٢٨ (ب) — الجواب على سؤال المستخدم: «أمحمية العقدة أم لا تقبل القيمة؟»

الجملة التي اقتبسها المستخدم — «قد تكون العقدة محمية أو لا تقبل هذه القيمة» — هي **نصّنا**،
من فرع `cpu_freq_rejected_explain`، أي أن `writeAccepted == false`. فالتطبيق كان يعرف أن الكتابة
رُفضت، ثم **يسأل المستخدم أن يخمّن السبب**. والسبب قابل للتحديد:

`CpuHardwareBackend.setPolicyLimits` **يُقيّد** الطلب إلى `[provenMin, provenMax]` **قبل**
الكتابة. فطلبٌ مثل ١٫٦ جيجا على سياسة يُثبت السائق مداها ٣٠٠م–١٫٢ج **لا يصل إلى العقدة قطّ**،
ثم يُعرض فشله بالجملة نفسها التي تُعرض بها حالة «العقدة رفضت». حالتان مختلفتان بلون واحد.

| أُضيف | ما يفعله |
| --- | --- |
| `CpuHardwareBackend.isOutsideProvenRange(policy, min, max)` | دالة خالصة تُفرّق «خارج المدى المُثبَت» عن «رفضت العقدة» |
| `CpuFrequencyVerification.outsideProvenRange` | الحقيقة مع كل طلب، من نفس السياسة التي كُتب عليها |
| سطر في الشاشة قبل تفسيرَي الرفض/الانقلاب | «خارج المدى… الكتابة تُقيَّد إلى الحدّ المُثبَت فلم تُطلب القيمة من العقدة أصلًا» (EN+AR) |

والترتيب مقصود: تفسيرا الرفض/الانقلاب **يفترضان أن العقدة رأت القيمة**، فلا يُعرضان إن لم تكن رأتها.
وبلا مدى مُثبَت (ولا جدول ترددات) لا يُدَّعى أن الطلب خارجه — المجهول ليس حدًّا. وأُزيل تعليق في
رأس `CpuCoreControlScreen` ما زال يقول إن لون الشريط يُشتقّ من **صياغة** الرسالة (صار يُشتقّ من
الرمز في تكملة ٢٧) — تعليق كاذب في الكود يُضلّل من يقرأه بعده.

`CpuProvenRangeTest`: **٧ اختبارات**، وفشلت اثنتان أوّلًا لأن **بيانات الاختبار كانت خاطئة**
(جعلتُ المدى المُثبَت ١٫٨ ج ثم توقّعت أن ١٫٦ ج خارجه) — لا لأن الكود خاطئ. صُحّح البيان ليطابق
قراءة جهاز المستخدم (٣٠٠م–١٫٢ج) بدل تعديل التوقّع.

| الفحص | النتيجة |
| --- | --- |
| `:app:testDebugUnitTest` | **٥٣٩ اختبارًا / ٥٣ صنفًا / ٠ فشل** (+٧) |
| `code_health --assert` | exit 0 — الدَّين بلا نمو |
| `i18n_coverage --assert` | exit 0 (EN + AR) |

---

## تكملة ٢٩ — CI أسرع بلا مسّ الجودة (تحقيق + تغييران آمنان)

**الطلب:** البناء على GitHub يستغرق ~٢٠ دقيقة؛ أريده أسرع دون تأثير على الجودة.

**الطريقة:** لم أُغيّر خطوة واحدة قبل التحقق من كل ادّعاء مقابل مصدره (مانيفست صورة الـrunner
ومانيفست كل عمل). ونتيجتان صادمتان أوقفتا تغييرين كنت أنوي الكتابة:

| الادّعاء الذي كنت سأبني عليه | ما قاله المصدر |
| --- | --- |
| «الـrunner الأكبر مجاني للمستودعات العامة» — **خطأ** | `docs.github.com` (Actions runner pricing): «The larger runners are **not free** for public repositories» ⇒ لا مكسب مجاني من زيادة الأنوية |
| «تحميل NDK r29 كل تشغيل يكلّف ١–٢ دقيقة» — **مبالغة** | تعليق في `build.yml` نفسه يقيسه: «well under a minute» ⇒ إزالته توفّر ~٤٠ ثانية مقابل استبدال عمل مصان بـكشف يدوي ⇒ **تُركت كما هي** |

**وما ثبت أنه مكسب:**

| التغيير | لماذا لا يمسّ الجودة |
| --- | --- |
| `compression-level: 0` على الرفعات الأربعة | الحمولة صادرة أصلًا من `zip -r9`، فمستوى 6 يستهلك CPU في ضغط ما لا يُضغط؛ ومانيفست `upload-artifact` نفسه يوصي بـ0 للحجم الكبير غير القابل للضغط («significantly faster uploads»). الملف المنزَّل **مطابق بايت ببايت** — يتغيّر وعاء الضغط لا المحتوى |
| `timeout-minutes: 45` | الافتراضي ٦ ساعات؛ تعليق خطوة يستهلك الـrunner صامتًا. ~ضعف الزمن المرصود ⇒ يظهر أحمر بدل أن يمضي |

**وما لم ألمسه عن قصد:** إعدادات Gradle بلا هدر ظاهر — `useLegacyPackaging` غير مضبوط (أي أن
`.so` تُخزَّن غير مضغوطة، وهو الأسرع)، و`configuration-cache=true` مُفعَّل أصلًا، و`--parallel`
و`--build-cache` يُمرَّران، و`isMinifyEnabled`/`isShrinkResources` عمل حقيقي يُحفظ.
و`cache-provider: basic` **غير افتراضي** (الافتراضي `enhanced` أسرع استرجاعًا)، لكنه مكوّن
مملوك بترخيص مختلف يتمسكه المستودع صراحةً ⇒ قرار مالك لا قراري.

**ولم يُتحقَّق منه محليًا ممكن:** لا يمكنني تشغيل CI هنا. وأرقام كل خطوة معروضة في صفحة الـrun
عندك، وبدونها أي تحسين تالٍ تخمين — وهذا ما طلبته من المالك.

### ملحق تكملة ٢٩ — مساران أُغلقا بدليل (أُعيد بعد أن مَحاه المحرّر)

حاولتُ تغييرًا (بذر "tool cache" بـNDK المسبق التنصيب ليتجاوز تحميل r29)، ثم **أبطلته قبل التسليم**
بعد قراءة مصدر `@actions/tool-cache` نفسه: `find()` لا تجمع إلا الأبناء الذين تمرّ
`isExplicitVersion` — و`r29` ليست صيغة semver — ثم تشترط `<path>.complete`؛ و`cacheDir` تنادي
`_createToolPath` التي تفعل `rmRF(folderPath)` **قبل** الكتابة. ⇒ **`tc.find("ndk", "r29")` تُعيد
فارغًا دائمًا**، والبذر كان سيكون **لا-عملية ويمحو نفسه**. فما في `build.yml` («well under a minute»)
هو الكلمة الأخيرة: تحميل r29 لا يمكن تجاوزه عبر tool cache، والمسار الوحيد `local-cache: true`
مُطفأ لسبب صحّي موثَّق. **وثانيًا:** `api.github.com/repos/nadermagdy338-cmyk/Dragon-` يعيد **404**
بلا مصادقة ⇒ المستودع **خاص**، فـ`cache-provider: basic` ← `enhanced` ليس مفتاح سرعة محايدًا بل
«Free Preview» لمكوّن مملوك على مستودع خاص. قرار مالك، لا قراري.

---

## تكملة ٣١ — إغلاق نافذة الاستجابة: الحارس في `finally` لا بعده

**المهمة:** إكمال عمل جلسة أخرى (ملف أثرها `session-ses_f487.md`). هي أغلقت *إعادة الإنتاج*
المُبلَّغ عنها (إلغاء الشاشة) لا *الصنف*، وقالت ذلك بنفسها: «Process death/other exception cleanup
still residual». وتحقّقتُ من العطب قبل التعديل ومن الحارس بعده.

**العطب كما هو في الشجرة:** بين الكتابة المُتحقَّقة وحكمها نافذةٌ يمرّ فيها `delay(RESPONSE_WINDOW_MS)`
(١٠ ثوان) — و`delay` نقطة إلغاء:

```
safetyGovernor.execute(...)   ← الكتابة تحققت على العتاد والمحكّم يحمل خط أساسها
... بلا try/finally ...
delay(10_000)                 ← نقطة إلغاء
after = collect(); enforcePost(); rollbackOnRegression()   ← الحكم والاسترجاع هنا فقط
```

فإلغاء النطاق أو أي استثناء في النافذة كان يتخطى الحكم **والاسترجاع معًا**: المقبض يبقى مكتوبًا
على العتاد بلا حلقة في الدفتر وبلا ما بعد الفيتو. والموضعان: `decisionCycle` و`runProbe`.

**الحل:** `restoreInterruptedWrite(step)` + `try/finally` تغلق النافذة في الدالتين. وبخاصيتين
محقَّقتين من الكود لا مفترضتين:

| الخاصية | من أين تحقّقت | لماذا لزمت |
| --- | --- | --- |
| `arbiter.release` **دالة عادية لا `suspend`** | `HardwareControlArbiter.kt` | نداء معلَّق داخل `finally` **بعد الإلغاء يرمي فورًا**، فلا يقع الاسترجاع أصلًا — أي بابٌ شكله حماية |
| نداؤها بعد استرجاع ناجح **لا يفعل شيئًا** | `release` تُعيد `null` إذا لم يكن هذا الطلب مالكًا | فلذلك تحتاج مسارات الاسترجاع العادية **صفر علامات** |

ولهذا لم يحتج `runProbe` علامةً أصلًا (كل مساراته العادية تسترجع)، واحتاجها `decisionCycle` في موضعين
فقط — الإبقاء **العمدي**: `after == null` والتحسّن المقيس. واتجاه الفشل مقصود وموثَّق في الكود: علامة
تُنسى لاحقًا تُرجع كتابةً كان المقصود إبقاءها (خسارة تحسين)، **ولا تُبقي كتابةً بلا حكم**.

**التحقق — والفشل المُثبَت مقصود:**

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **٣٨ اختبارًا OK** (٣٣ قبلها + ٥ جديدة) + فحص إعراب بمُحلِّل Kotlin الحقيقي |
| إعادة العطب (الـhelper `suspend` و`finally`→`catch`) | **فشل ٢ بالاسم** |
| إعادة العطب بعد تشديد الرسالة | فشل برسالة صادقة: «Declaration vanished… **private fun** restoreInterruptedWrite(» |

**وفي هذا التسليم اكتشاف تشغيلي أهم من التعديل نفسه:** محرّر الملفات في هذه البيئة **أعاد كتابة ملف
من لقطة قديمة فمحا تعديلين سابقين**، ثم **أحيا ملفين حذفتهما**، ثم **محا الملحق السابق من هذا الملف**. ظهر
الأول بالحساب: `1725 = 1707 + 18` أي أن ملف المحرك كان يحمل تغيير الدالة الثانية وحده. ولولا فحص الأثر
بعد كل كتابة لسُلِّم العمل ناقصًا أو مُدّعى. ⇒ **لا يُقبل «تم التعديل بنجاح» إلا بقراءة الأثر بعده.**

**حدود الصدق:** لا Android SDK في هذه البيئة ⇒ **لا ترجمة ولا فحص أنواع**؛ ما تحقق: إعراب بمُحلِّل Kotlin،
واختبارات JVM، وحرّاس على شكل الكود. ولا شيء مما هنا **يشهد أن المُحكِّم يكتب على جهاز فعلًا**.

---

## تكملة ٣٢ — مسار الإطلاق: `Max Backup` و`AppOps` من شاشة التحكم

**الشكوى:** «أضغط عليهما في شاشة التحكم فلا يُفتح شيء / يُفتح فارغًا».

**التشخيص المُتحقَّق منه في الكود (لا تخمينًا):** `MaxNavActions.navigateTo` كانت تنقل
`MaxDestination.route` كما هو، ومسار كل منهما `max_backup?pkg={pkg}` / `max_perms?pkg={pkg}`.
والنمط يُطابق وجهته لأن `pkg` معامله استعلامي بقيمة افتراضية — لكن القيمة الواصلة هي النص
`"{pkg}"` حرفيًّا. وهو **غير فارغ**، فينجو من `isNullOrBlank` التي تفصل «قائمة التطبيقات» عن
«تفصيل تطبيق واحد» ⇒ تُفتح شاشة التفصيل لحزمة لا وجود لها. وهذا هو الفرق بين `?pkg={pkg}`
في مسارين فقط وبين بقية المسارات: لهذا لم يظهر العطب إلا فيهما.

**الإصلاح:** `LaunchRoutes.kt` (`launchRouteOf` = نزع الاستعلام الاختياري،
`launchRouteNeedsArgument` = ما بقي فيه `{...}` في المسار ليس مسار إطلاق)، و`MaxDestinations`
تعلن `launchRoute` و`needsLaunchArgument`، و`navigateTo` تنقل الأول بـ`check` على الثاني.

**وفي هذه الجولة أُغلق الصنف نفسه في موضع ثانٍ لم يُبلَّغ عنه:** `HomeScreen` كانت تمرّر
`navController::navigate` مباشرة، و`gpuRouteForChipset` تُرجع السلسلة الحرفية `"gpustudio"`
— أي مسار مكتوب بيد خارج السجلّ (مخالفة ADR-02). أُضيف `MaxNavActions.navigateRoute(route)`
كبوّابة واحدة للنصوص، و`gpuRouteForChipset` صارت من السجلّ. مسارات لوحة `Now` كلها بلا استعلام
اختياري، فهي كانت **محصّنة بالحظّ لا بالقاعدة**.

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **٤٦ اختبارًا OK** (٣٨ قبلها + ٨) وإعراب **١٥ ملفًّا** بمُحلِّل Kotlin بلا خطأ |
| إعادة العطب: `launchRouteOf` تُعيد المسار بلا نزع | **فشل ٤ بالاسم**، منها «every tool row on the control page opens its own screen, never a fake detail» |
| إعادة العطب: `navController::navigate` في `HomeScreen` | **فشل ١ بالاسم** |
| `i18n_coverage --assert` | exit 0 |
| `code_health --assert` | exit 1 — وحدها `stray_root_file: 1` (أثر جلسة المالك، أُبقي بقراره)، والدَّين ثابت 10/29/66/26 ⇒ صفر انحدار |

**حدود الصدق:** الحرّاس أكثرهما على **نصّ المصدر** (`LaunchRouteTest` يقرأ السجلّ والنموذج
والشاشة نصًّا) — وهذا مقصود لأن `MaxDestination` تستورد Compose و`R` فلا تُترجم في هارنس JVM،
لكن الأثر أن الحارس يميّز الشكل لا السلوك المُنفَّذ. والمدخلان (`Max Backup` و`AppOps`) لم
يُشرَدا على جهاز؛ الفلترة نفسها (`isNullOrBlank` على `pkg`) قُرئت في `MaxNavGraph` لا في التنفيذ.
وبقي في اللوحة نفسها **عشرة** `onNavigate(MaxDestination.X.route)` في `LegendaryHomeDashboard`
تُمرَّر إلى `navigateRoute` الذي ينزع الاستعلام ⇒ سليمة سلوكيًّا، لكنها ما زالت تنقل `.route`
لا `.launchRoute`؛ لم أُغيّرها لئلا أُوسّع الـdiff في ملف ضخم بلا عطب قائم.

**ينطبق هنا تحذير تكملة ٣١:** لا يُقبل «تم التعديل بنجاح» إلا بقراءة الأثر بعده؛ وقد قرئ بعد كل
كتابة في هذه الجولة، ولهذا اكتُشف أن `"gpustudio"` في `gpuRouteForChipset` هو **ثاني** موضع
حرفي في الملف بعد أن ظننت الأول وحده.

---

## تكملة ٣٣ — `{pkg}` يظهر عنوانًا: إصلاحُ المسار لم يكن كافيًا، والسبب **حالة تنقّل محفوظة**

**بلاغ المالك (مُفصَّل هذه المرّة، فكفَّ عن التخمين):** «تظهر لي `{pkg}` … هذا التطبيق غير مثبّت،
لا توجد حزمة بهذا الاسم على الجهاز الآن. إعادة المحاولة» — أي أن **شاشة التفصيل تُفتح ومعرّفها
نمطُ المسار حرفيًّا**، وقال إنه **أعاد البناء** وإن مدير الملفات وبقية الأدوات ظاهرة عنده في
شاشة التحكم (فهو على نسخة تحمل إعادة الترتيب).

**فالمشكلة ليست النسخة إذن، بل القيمة.** وإصلاح [تكملة ٣٢] كان لازمًا لكنه ليس كافيًا: هو يمنع
**إنشاء** مدخل بمعامل `{pkg}`، ولا يمتنع عن مدخل **أُنشئ قبله واستُعيد بعده** — لأن حالة التنقّل
تُحفظ وتُستعاد عبر إعادة إنشاء النشاط **وعبر تحديث التطبيق**، فمدخل قديم بمعامله القديم يفتح شاشته
كما كان. وفحص الاستدعاءات كافة أكده: لا موضع واحد في المصدر يبثّ النمط اليوم (`MaxNavGraph`
و`MaxBackupScreen` و`PermissionsScreen` و`AppSettingsScreen` كلّها `.replace("{pkg}", …)`)
— فالمصدر سليم، والقيمة قديمة.

**الإصلاح الدائم عند المستهلك لا عند المُنتِج:** `isPackageArgument(value)` ترفض ما يخالف الشكل.
والحجة **ليست تخمينية**: اسم حزمة Java لا يقبل إلا حروفًا وأرقامًا و`_` وفواصل `.` ⇒ فقيمة تحمل
`{` أو `}` **لا يمكن** أن تكون حزمة، بل هي نمطنا. والرفض يُسقط إلى قائمة التطبيقات — وهي الشاشة
الرئيسية المقصودة للمدخلين كما طلب المالك صراحةً. طُبّقت في `MaxNavGraph` على `MaxBackup`
و`Permissions` معًا.

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **٤٨ اختبارًا OK** (+٢) وإعراب **١٦ ملفًّا** بمُحلِّل Kotlin بلا خطأ |
| إعادة العطب: `MaxNavGraph` تعود إلى `?.takeIf { it.isNotBlank() }` | **فشل ١ بالاسم**: «both optional-package destinations reject a stale pattern argument» |
| `i18n_coverage --assert` | exit 0 |
| `code_health --assert` | exit 1 — وحدها `stray_root_file: 1`، والدَّين ثابت 10/29/66/26 ⇒ صفر انحدار |

**ما لم أُصلحه وأُصرّح به:** الحالة التي يكون فيها المعامل **حزمة حقيقية غير مثبّتة بعد الآن**
(استعاد مدخلًا لحزمة أبطل تثبيتها) ما زال مصيرها الرسالةَ الصادقة «لا توجد حزمة بهذا الاسم +
إعادة المحاولة» — لم أحوّلها إلى قائمة لأن ذلك يُخفي إشارة صحيحة، ولم يُبلَّغ عنها.
و`WorkspaceLinks` في `ApplistScreen` مرشّحه (`it.parent == Apps` مع استثناء الثلاثة) لا يُطابق
شيئًا بعد نقل `MaxBackup` و`Permissions` إلى `Control` ⇒ الدالة تُرجع بلا رسم — موضع ميت
أصيل لا عطب ينشأ عنه؛ لم أحذفه بلا طلب (ADR-18).

### إضافة ٣٣.١ — إزالة الطريق المسدود نفسها

البلاغ المُفصَّل يذكر ما يراه بعينه: **«لا توجد حزمة بهذا الاسم على الجهاز الآن. إعادة المحاولة»**.
وهي **حالة مسدودة**: زرّ إعادة محاولة لا يُصلح شيئًا (لا حزمة لتُعاد محاولتها) ولا طريق منها إلى
قائمة التطبيقات. وقد طلب المالك صراحةً أن تُفتح هذه الشاشات المستقلة على **قائمة تطبيقات مناسبة
لوظيفة كل منها**؛ فحجزتُ نفسي في تكملة ٣٣ عن تحويلها بحجّة «لا تُخِف إشارة صادقة» — وعارضتُ بذلك
قرار المالك، فأُبطل.

⇒ `packageArgumentOrNull(context, raw)` تجمع الفحصين عند المستهلك: **الشكل** (يرفض `{pkg}` — نمطُنا
لا معرّف حزمة) و**الوجود** (يرفض حزمة حقيقية أُبطل تثبيتها بعد إنشاء المدخل). والرفض في أيٍّ منهما
يُسقط إلى `null`، والشاشتان تعتبران `null` «بلا نيّة سابقة» ⇒ **قائمة التطبيقات**.

وبهذا يصير الطريق المسدود غير قابل للوصول من **أي** قيمة معامل، لا من `{pkg}` وحدها.

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **٤٩ اختبارًا OK** (+١) وإعراب ١٦ ملفًّا |
| إعادة العطب: `MaxBackup` تعود إلى `?.takeIf { it.isNotBlank() }` | **فشل ١ بالاسم** |
| `i18n_coverage --assert` | exit 0 |
| `code_health --assert` | exit 1 — وحدها `stray_root_file: 1`، والدَّين 10/29/66/26 ثابت |

**وأبطلتُ في هذه الجولة فرضية كنت أنتظرها:** لا **رسم تنقّل ثانٍ** — `maxNavGraph` مُستدعى مرة
واحدة (`MainActivity:401`)، و`composable(` خارج `MaxNavGraph.kt` لا يوجد إلا في `KernelFlasherScreen`
و`ActivitylauncherScreen` (رسمان داخليان لشاشتين لا علاقة لهما بالمسارات المعنية).

**وحد الصدق الأهم في هذه الجولة:** استنتاج «حالة تنقّل محفوظة» هو **ترجيح** مبنيّ على استبعاد
ما عداه — فحصتُ كل مستدعي المسارين فوجدتُهم جميعًا يُنزعون المعامل، فأين يبقى؟ لكنني **لم أشهده
على جهاز**. وهو لا يغيّر الإصلاح: الحارس صحيح لما يصفه المالك سواء جاءت `{pkg}` من حالة محفوظة
أم من أي طريق آخر لم أُحصه. وإن كنتَ تستطيع: امسح التطبيق من «الأخيرة» أو أوقفه قسرًا بعد التثبيت،
فإن زال العَرض بلا هذا الإصلاح فذاك تأكيد للتشخيص، وإن بقي فالضغط من مكان آخر لا أعرفه — أرسل
logcat.
وتصريحان عن أثر جانبي مقصود: (١) مسار `!improved` حين `step.from == null` كان `rollbackOnRegression`
يعود قبل التحرير، فالآن يتولى `finally` تحرير الملكية (كتابة عتاد لا تحدث أصلًا لأن خط الأساس معدوم،
لكنها إعادة كتابة سجل). (٢) الحرّاس تُثبِّت *شكل* `} finally {` وتفشل على بديل مكافئ وظيفيًا
(`catch` + إعادة رمي) — قيد مقصود في هذا النوع من حرّاس المصدر، مكتوب هنا لا مخفيًّا.

---

## تكملة ٣٤ — `Max Backup`: خيارات أولًا، لا هبوطًا في قائمة

**الشكوى بحروفها:** «شاشة Max Backup غير منطقية وتنقصها الكثير مثل بيانات النظام، لا يوجد زرّ
مكتوب *حفظ نسخة* أو *استعادة أحدث نسخة*، ويبدو كما لو أنه يُنسخ تلقائيًّا وهذا خطأ، ولا يوجد في
قائمة التطبيقات ما يسهّل التجربة مثل تحديد الكل، ويجب أن يكون مثل Swift Backup وDataBackup.»

### التشخيص: الشاشة كانت تبدأ من الخطوة الثالثة

لم يكن العطب في ميزة ناقصة بل في **ترتيب**: مسار `Max Backup` كان يهبط مباشرة في قائمة التطبيقات،
ولا زرّ حفظ في أي موضع يستقبله، ولا ذكر لبيانات النظام إلا مبدّلًا داخل ترويسة القائمة. والنسخ
فعليًّا يحتاج ضغطًا في ثلاث شاشات قبل أن يحدث — لكن الشاشة **لا تقول ذلك**، فتبدو تلقائية.

### ما تغيّر

| الطلب | ما نُفّذ |
| --- | --- |
| «خيارات أولًا» | الصفحة الأولى `MaxBackupPage.HOME`: تبويب **نسخ \| استرجاع**، وفئتان، وإجراءان، وأحدث النسخ |
| «زرّ حفظ نسخة» | `max_backup_save_now_title` على الرئيسية، و`max_backup_save_selected` في المنتقي (أعلى القائمة وأسفلها) |
| «استعد أحدث نسخة» | صفّ في التبويبين، بوصف يحمل اسم التطبيق وتاريخ النسخة وحجمها، معطَّل بنصّ صريح إن لا نسخة |
| «تبدو تلقائية» | `max_backup_not_automatic` وصفًا لقسم الفئات، + حوار تأكيد قبل كل كتابة |
| «تحديد الكل وغيرها» | تحديد متعدد، تحديد الكل **للمعروض**، إزالة التحديد، أربعة مرشّحات، بحث، وآخر نسخة/«لم تُنسخ بعد» في كل صف |
| «بيانات النظام ناقصة» | صارت **فئة من الصفحة الأولى** لا مبدّلًا مدفونًا في ترويسة قائمة |
| مكان التخزين | `/storage/emulated/0/MaxManger/MaxBackup` — انظر القيد أدناه |

**والمنتقي لم يفقد مسارًا:** الجسم يفتح تفصيل التطبيق كما كان (ومدخل إعدادات التطبيق يبقى مباشرًا
إلى التفصيل)، وصندوق التحديد هو الجديد. ودخول التفصيل من القائمة صار عبر `packageRouteOf`
من سجل الوجهات لا سلسلة مكتوبة بيد.

### مكان التخزين: صار المطلوب، **مع جذرين لا جذر**

`MaxBackupStorage` جديد وخالص (بلا `Context`، بلا Android) ليُختبر القرار بلا جهاز:
المسار العام إن كان **قابلًا للكتابة فعلًا** (فحص كتابة لا `mkdir` ناجحة)، وإلا مجلد التطبيق
الخارجي. و`MaxBackupEngine.list` يمرّ على **الجذرين دائمًا**، فلا تختفي نسخة أُخذت قبل منح
الصلاحية. و`MANAGE_EXTERNAL_STORAGE` أُعلنت اختياريةً: الطبقة تعمل بلاها، والشاشة تقول أي
المجلدين استُعمل وتعطي زرّ المنح — بدل أن تدّعي مكانًا لم تُكتب فيه.

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **٦٠ اختبارًا OK** (+١١) وإعراب **٢١ ملفًّا** بمُحلِّل Kotlin، صفر خطأ إعرابي |
| إعادة العطب: `requestedRoot()` تُعيد الوالد بلا مجلد | **فشل ٣ بالاسم**، وبرسالة صادقة: `expected:<...emulated/0/MaxManger[/MaxBackup]> but was:<...MaxManger[]>` |
| إعادة العطب: المنتقي ينقل `"max_backup?pkg=..."` مكتوبًا بيد | **فشل ١ بالاسم** |
| `i18n_coverage --assert` | exit 0 |
| `code_health --assert` | exit 1 — وحدها `stray_root_file: 1` (أثر جلسة المالك، أُبقي بقراره)، والدَّين **10/29/66/26 ثابت** ⇒ صفر انحدار |
| `repo_audit.py` | لا استيراد غير مستخدم في الملفات المعدّلة (نُقّيت يدويًّا بعد الحذف) |

### حدود الصدق

1. **لا Android SDK هنا ⇒ لا ترجمة ولا فحص أنواع.** المُحقَّق: إعراب بمُحلِّل Kotlin، واختبارات
   JVM خالصة، وقراءة المصدر. **ولا شيء من هذا يشهد أن الرفع إلى `/storage/emulated/0` ينجح على
   جهاز بعينه** — ذلك يقرّره فحص الكتابة وقت التشغيل، والشاشة تُعلن نتيجته.
2. `MANAGE_EXTERNAL_STORAGE` صلاحية «وصول خاص» تُمنح من إعدادات النظام؛ بلا منحها تبقى النسخ في
   مجلد التطبيق **وهذا ليس فشلًا** بل المسار المُعلَن.
3. الشاشة لم تُشرَد على جهاز: لم أتحقّق بصريًّا من التناسق في RTL ولا من نسب الصفوف.
4. **لم يُلتزم شيء في git.**

### الملفات

`ui/util/MaxBackupStorage.kt` (جديد) · `ui/subscreens/MaxBackupHubScreen.kt` (جديد) ·
`ui/subscreens/MaxBackupPickerScreen.kt` (جديد) · `ui/subscreens/MaxBackupScreen.kt` (المنتقي القديم
أُزيل، والمدخل والتفصيل عُدّلا) · `ui/util/MaxBackupModel.kt` + `MaxBackupEngine.kt` (`Handle.label`
والجذور) · `ui/subscreens/MaxBackupSystemSection.kt` (رجوع إلى الخيارات لا خروج) ·
`ui/navigation/LaunchRoutes.kt` (`packageRouteOf`) · `AndroidManifest.xml` · `values(-ar)/max_backup_strings.xml` ·
`tools/test_maxai_jvm.py` · `test/.../MaxBackupStorageTest.kt` (جديد) + `LaunchRouteTest.kt`.

### إضافة ٣٤.١ — قراءة الصور بالقياس لا بالنظر

المالك أرسل **سبع لقطات** (ImgBB). الأداة ترفض `image/png` و`image/jpeg`، ولا يوجد في البيئة
مُحرّك OCR ولا مكتبة صور ولا وصلاح لتثبيت أحدهما. ⇒ نُزّلت اللقطات بـ`curl` إلى `build/` (مُتجاهَل)
وقُرئت **بالقياس**: فُكّت بـ`javax.imageio` من JDK وكُتب عارض ASCII يعوّض نسبة الحرف (الحرف ضعف
ارتفاعه) ويعكس التدرّج.

**ما قيس فعلًا (لا ما ظُنّ):** اللقطات ١٢٢٠×٢٧١٢، وفيها `com android vending` في اسم الملف ⇒
**متصفّح Play كان في المقدّمة**. والملامح السبع متطابقة تقريبًا (فرق موضع واحد في اللقطة
الخامسة: كتلة داكنة عند ٦٠–٦٥٪). وفي اللقطات: شريط علوي داكن ≈١٠٪ يحمل مربّعًا مستديرًا (أيقونة)
ونصًّا، ثم صفوف داخل ألواح ذات حدود مستديرة، كل صفّ: أيقونة صغيرة يسارًا وسطرا نص وعنصر في
الطرف؛ وكتلة داكنة بعرض اللوح في اللقطة الخامسة (زرّ أساسي أو صفّ محدَّد)؛ وشريط سفلي داكن.

**وما لم يُقَس:** النصوص — لا يمكن قراءتها من وصف رمادي، ولا مُحرّك OCR. فالاستنتاج التشكيلي
(صفوف قوائم + زرّ أساسي + تحديد يُقلب الألوان) **قراءة بنية لا قراءة نصّ**، وقرار التعديل عليه
مبنيّ كأفضل تفسير لا كيقين. والتغييران الناتجان: زرّ حفظ **ممتلئ** بعرض الصفحة (`StudioButton`)
في موضعين، و**قلب ألوان الصفّ المحدَّد** بدل رقعة شفافة.

وحُذفت ملفات `build/tmp-shots` بعد القراءة (و`build/` مُتجاهَل في git أصلًا)، فلم يبقَ من لقطات
المالك شيء على القرص.

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | ٦٠ اختبارًا OK · ٢١ ملفًّا بلا خطأ إعرابي |
| `code_health` | الدَّين **10/29/66/26** ثابت (صفر انحدار) |
| `repo_audit.py` | لا استيراد غير مستخدم (واستيراد مكرّر واحد أُزيل بعد إضافتي) |

### إضافة ٣٤.٢ — ما هو مفيد فوق الأصل: حفظ، واستعادة جهاز، وفحص كامل

ثلاث وظائف نُفّذت بعد إشارة المالك «كل ما هو مفيد بل وأفضل من الأصلي»:

**١) «احفظها للأبد»** — وسـم في **المستند نفسه** لا في تفضيلات التطبيق: النسخة قد تُنقل إلى
جهاز آخر وقرار «لا تحذف هذه» يجب أن يسافر معها. والتقليم لا يمسّها، **ولا تستهلك رصيد
الاحتفاظ** (من حفظ نسخةً لا يفقد نسخته اليومية بسببها — وهذا هو المقصود). وزرّ القفل في كل صفّ
نسخة، وحوار الحذف يقول صراحةً إن التقليم لم يكن ليحذفها.

**٢) «استعد الجهاز كله»** — أحدث نسخة لكل تطبيق بالتتابع، وبيانات النظام مستثناة (لها شاشتها).
والنسخة التي يمنعها `decisionFor` **تُتخطّى ولا تُفرَض**: لا قسر لاسترجاع لم يمرّ فحص بصمته،
والنتيجة تقول `استُعيدت N · تُخطّيت M`.

**٣) «افحص كل النسخ»** — إعادة قراءة كل ملف ومقارنته ببصمته المسجّلة، ثم **تسمية** كل نسخة
لم تجتز بـ«كم من كم»، لا جملة «فيه مشكلة» بلا مُتّهم.

**والتقليم صار دالة خالصة مُقاسة.** `MaxBackupModel.toPrune` كانت تعيش في ملف يستورد
`org.json`، فلا تُترجم إلا على جهاز — أي **أن قاعدةً تحذف ملفات المستخدم لم يكن يقيسها شيء**.
نُقل القرار إلى `MaxBackupRetention` (بلا Android)، والنموذج يلتصق به ويترجم `Handle` إلى
عرض القرار.

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **٦٧ اختبارًا OK** (+٧) · ٢١ ملفًّا بلا خطأ إعرابي |
| إعادة العطب: حذف سطر `filterNot { it.kept }` | **فشل ٣ بالاسم**: `aKeptCopySurvivesEvenWhenItIsTheOldest` · `aKeptCopyDoesNotConsumeTheQuota` · `nothingIsRemovedWhenEveryCopyIsKept` |
| `i18n_coverage --assert` | exit 0 · تطابق مفاتيح en/ar **٢٢٩/٢٢٩** |
| `code_health --assert` | الدَّين **10/29/66/26** ثابت — صفر انحدار |

**وحدّ صريح:** `MaxBackupModelTest.kt` (وفيه اختبارات `toPrune`) **لم يُشغَّل** هنا ولا قبل اليوم —
الملف يستورد `org.json` فلا يُترجم في هارنس JVM. المنطق الجديد كله تحت اختبار خالص يعمل،
ووصلةُ `Handle → Copy` سطر واحد تُغطّى هناك عند تشغيل اختبارات Android. ولم يُقَس شيء على جهاز.

## تكملة ٣٥ — مدير الملفات: ترتيب اللوحين + تنقّل مرتبط، وسقوط مُثبَت في قائمة الترتيب

طلب المالك: «طوّر مدير الملفات أكثر، واجعله من اليمين واليسار أو فوق أو تحت، وكلا الجهتين
مرتبطين». وقبل التنفيذ ظهر **سقوط حقيقي** في الشاشة نفسها لم يُبلَّغ عنه.

### ٣٥.١ — السقوط: قائمة الترتيب تفتح فتسقط الشاشة

`MaxViewMenu` كانت تختار أيقونتها بـ`MaxViewIcons[selected]` وبكل بند بـ`MaxViewIcons[index]`،
وقائمة أيقوناتها **بندان**. ومدير الملفات يمرّر لها **أربعة** أسماء (`Name/Size/Modified/Kind`)
⇒ فهرسة خارج الحدود. والمصرّف لا يراها (الطول ليس من نوعه)، ولا اختبار جهاز يراها (تقع عند
لمسة واحدة).

**الإصلاح في المكوّن لا في المستدعي:** الأيقونات صارت **اختيارية لكل بند** وتُقرأ بـ`getOrNull`
مع أيقونة احتياطية للزَرّ (`MaxViewFallbackIcon`)، والواجهة تُشرح: «لا فهرسة مباشرة لأي قائمة
أيقونات داخل المكوّن». والمستدعي الجديد (ترتيب اللوحين) يمرّر أيقوناته الثلاث صراحةً، وقائمة
الترتيب تمرّر `icons = emptyList()` و`triggerIcon = Icons.AutoMirrored.Rounded.Sort` — أيقونة
«مجدول/موسّع» بجانب «الاسم/الحجم» بلا معنى، فاسم بلا رمز خير من رمز مضلِّل.

### ٣٥.٢ — ترتيب اللوحين صار اختيارًا لا نتيجة قياس

كان الترتيب يُقرّر بشرط داخل التركيب: `if (maxWidth >= 600.dp) Row else Column`. الشرط نفسه
سليم، لكنه **لا يعرف ما يريده المستخدم**، ولا يمكن قياسه بلا جهاز. فانتقل إلى
`PaneLayoutRule.sideBySide(layout, width, threshold)` في `FilePaneModel` (خالص، مُقاس)،
وثلاثة أوضاع: `Auto · SideBySide · Stacked` — والوضع الصريح **يتقدّم على القياس في الاتجاهين**:
من اختار «جنبًا إلى جنب» على هاتف ضيّق يريده كذلك، ومن اختار «فوق وتحت» لا يُجبَر على عمودين.

### ٣٥.٣ — «مرتبطين»: التنقّل المرتبط يتبع **الاسم** لا المسار

المزامنة المطلقة كانت موجودة (`DualPane.syncOther`) وهي تضع اللوحين على المجلد **نفسه** —
وعندها تصير الوجهة = المصدر، أي أن المزامنة **تمنع النقل الذي وُجد اللوحان من أجله** (وهذا
مُثبَّت في اختبار: `absoluteSynchronisationLeavesNothingToTransfer`).

فالربط هنا شيء آخر: تدخل `Download` في لوح، فيُدخل الآخر `Download` **من مساره هو**. والمقابل
يُبحث في **قراءة اللوح الآخر** لا في مسار مُخترع، لأن مجلدًا لم نقسه قد لا يوجد أصلًا
(`/sdcard/Android/data` محجوب على كثير من الإصدارات) فنكون قد أنزلنا لوحًا على مجلد لا يُقرأ:

| الحالة | النتيجة |
| --- | --- |
| مجلد بالاسم نفسه موجود هناك | يُنتقل اللوح الآخر إليه (ومعه سجلّه فيعمل الرجوع فيه) |
| لا يوجد بالاسم نفسه | **يبقى مكانه** — ولا يُخمَّن له مسار |
| المقابل كان **ملفًا** لا مجلدًا | لا انتقال |
| قراءة اللوح الآخر فاشلة | لا انتقال |
| الصعود | خطوة واحدة للأعلى في اللوحين، و`null` على الجذر |
| القفز المطلق (شريط الأثر) | **لا يُربط** — لا مقابل اسميّ لمسار مطلق |

و«الآخر» يُحسب من **اللوح الذي تحرّك** (`side.other`) لا من النشط: النقر قد يقع في اللوح غير
النشط، وحينها لو حُسب من النشط لكُتب المسار على اللوح المنقور نفسه.

### ٣٥.٤ — وأين ظهر ذلك في الواجهة

الشريط العلوي بقي **أربعة أزرار** كما كان (ترتيب · ترتيب اللوحين · ربط · شرح)، وانتقلت
«مزامنة المسار» و«تبديل اللوحين» إلى شريط اللوح النشط (فهما فعلان على لوح)، والشريط صار
**قابلًا للتمرير أفقيًّا** — قصّ فعلٍ على هاتف ضيّق أسوأ من تمريره. والربط يُعلَن في ثلاثة
مواضع: رمز في الشريط، و**علامة في رأس كل لوح** («التنقّل مرتبط باللوح الآخر»)، وجملة
«اللوحان يتحركان معًا» في الشريط — لأن حالة مخفية لا تُفهَم.

### ٣٥.٥ — أول اختبارات لهذا الملف: نموذج اللوحين وحرس العمليات

`FileSystemModel` و`FileActionModel` و`FilePaneModel` **خالصة** (لا Android، لا `org.json`)
وكانت خارج الهارنس. صارت داخله، فصار يُقاس ما لم يُقَس قط: `DualPane` كلّه، و**`FileOpGuard`**
— آخر من يقف بين نقرة وحذف شجرة (نسخ مجلد داخل نفسه، نقل إلى المصدر، حماية `/`، اسم يحمل
فاصلًا، اسم مشغول).

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **٨٧ اختبارًا OK** (+٢٠) · **٢٤ ملفًّا** بلا خطأ إعرابي |
| إعادة العطب: `mirrorFolder` بدمج نصّي للمسار | **فشل ٣ بالاسم**: `linkedNavigationLeavesTheOtherPaneAlone…` · `anUnreadablePaneIsNeverMoved` · `linkedNavigationNeverTreatsAFileAsAFolder` |
| إعادة العطب: `MaxViewMenu` بالفهرسة المباشرة | **فشل ١ بالاسم**: «فهرسة مباشرة لقائمة الأيقونات — هذا هو العطب الذي أسقط الشاشة» |
| إعادة العطب: `PaneLayoutRule` تُعيد `true` دائمًا | **فشل ٢ بالاسم**: `automaticArrangement…` · `anExplicitArrangementWins…` |
| `i18n_coverage --assert` | exit 0 |
| `code_health --assert` | الدَّين **10/29/66/26** بلا نمو · الحمراء الوحيدة `stray_root_file: 1` (أثر جلسة المالك، أُبقي بقراره) |

وحرّاس الشكل الجديدة (`MaxViewMenuContractTest`) تقيس **الصنف** لا العطب: لا فهرسة مباشرة داخل
المكوّن، وكل موضع يمرّر أسماء أكثر من الأيقونات الافتراضية **يسمّي أيقوناته**، والترتيب يأتي من
النموذج، والربط يسأل النموذج ولا يدمج مسارات نصًّا.

### ٣٥.٦ — ولقطات المالك: ما قيس فيها وما لم يُقرأ

أُرسلت سبع لقطات من `MT Manager Plus`. ولا مُحرّك OCR في هذه البيئة ولا مكتبة صور (لا
`tesseract`، لا `PIL`، لا `cv2`)، فلم تُقرأ كلمة واحدة. ووُزنت اللقطات بـ`javax.imageio`
مدعومًا بمحلِّل بنية كتبته للأمر (شرائط المحتوى، والخطوط الرأسية الطويلة):

- في لقطة واحدة خطّ رأسي بعرض ٣ بكسل عند **x = 607–609 من ١٢٢٠** (المنتصف تمامًا) يمتدّ أكثر
  من ٧٠٪ من الارتفاع ⇒ **لوحان جنبًا إلى جنب بفاصل وسطي**.
- وفيها صفوف متكرّرة **متماثلة عموديًّا** (كتلة أيقونة عند ٢٪ وآخرتها عند ٥٢٪ من العرض) ⇒ بنية
  القائمة نفسها في اللوحين.
- وفي لقطتين أعمدة أيقونات على حافة واحدة فقط (يمين ٨٣٪، ويسار ٣٪) ⇒ عرض لوح واحد أو قائمة
  عادية، لا انقسامًا.

⇒ **الدليل المقيس يدعم الجنب إلى جنب، ولم أشهد على تجربة «فوق/تحت» في اللقطات**، فوُضعت كخيار
صريح لا كبديل مفترض. وما لم يُقرأ لم يُدَّعَ: لا نصوص الأزرار ولا تسمياتها.

### ٣٥.٧ — حدود صريحة

1. **لا Android SDK هنا ⇒ لا ترجمة ولا فحص أنواع.** المُتحقَّق: إعراب بمُحلِّل Kotlin لـ٢٤ ملفًّا،
   و٨٧ اختبار JVM، وقراءة مصدر. **ولا شيء من هذا يشهد أن اللوحين يُرسمان على جهاز.**
2. **لم يُشرَّد على جهاز**، ولا جُرّب الربط فعلًا ولا تغيير الترتيب.
3. معنى «مرتبطين» **قراءتي أنا** للطلب (تبع الاسم + استثناء القفز المطلق). بديله المحتمل
   («نفس المجلد دائمًا») موجود أصلًا كزرّ مزامنة، وبديله الآخر (تحديد مشترك) غير منفَّذ.
4. **عمل غير مكتمل على القرص من الجلسة السابقة، مُعلَن لا مخفي:** `ui/util/AppOpsBatch.kt`
   (منطق الإجراء الجماعي، خالص ومُعلَّق) و`AppOpsUtil.referenceIndex` — **لا مستدعي لهما بعد**،
   وواجهة `AppOps` الجماعية لم تُكتب. الكود يترجم (ملف خالص) لكنه لا يعمل في التطبيق بعد.
5. **ولم يُلتزم شيء في git.**

## تكملة ٣٦ — قراءة الصور: أداة في المستودع، لا وصف بالتخمين

طلب المالك: «حمّل أو أضف OCR أو أي طريقة أخرى للتعرف على محتويات الصور بشكل صحيح». وكان الطلب
مبنيًّا على نقص حقيقي: في تكملة ٣٥ قُيست لقطات المالك بنيويًّا **ولم تُقرأ كلمة واحدة** منها،
والسبب ليس غياب محاولة بل غياب القدرة: أداة جلب الوكيل ترفض `image/png` و`image/jpeg`، ولم يكن في
البيئة أي مُحرّك (لا `tesseract`، لا `PIL`، لا `cv2`).

### ٣٦.١ — ما ثُبّت (خارج المستودع، بحجم معلَن)

| ما | الأمر | الحجم |
| --- | --- | --- |
| `tesseract` 5.3.4 + ملحقا `ara` و`eng` | `apt-get install tesseract-ocr tesseract-ocr-ara tesseract-ocr-eng` | ~15MB |
| خطوط عربية + تشكيل النص (`raqm`) | `apt-get install fonts-noto-core libraqm0` | ~6MB |
| `Pillow` 12.3 + `easyocr` + `torch` 2.14 **CPU** | `pip install --user pillow easyocr` + فهرس PyTorch للـCPU | ~1.2GB في `~/.local` |
| نماذج `tessdata_best` (`ara` 13MB و`eng` 15MB) | تنزيل من `tesseract-ocr/tessdata_best` إلى `~/.local/share/tessdata` | 28MB |

**ولا شيء من هذا دخل المستودع**: الأوزان والنماذج في `~/.local` و`~/.EasyOCR`، والأدوات التي
استُدعيت (لا كُتبت في المستودع) هي فقط `read_image_text.py` بأداة واحدة.

### ٣٦.٢ — الأداة: `tools/read_image_text.py`

سحب الصورة (رابط أو مسار) ← قصّ بالكسور ← **عدّة أنماط** (كما هي · معكوسة · عكس+تباين · تكبير ×٢ ·
تكبير ×٢+ثنائية) ← قراءة بالمحرّك **الأعلى ثقةً** لا بأول نمط. ولماذا الأنماط: لقطات الواجهة تأتي
بقطبية ومقياس وتباين غير معلومين، وخطّ واحد ثابت يقرأ بعضها كضجيج (وقد ظهر ذلك في القياس: النمط
الفائز اختلف بين اللقطات — `inverted` في أربع، و`inverted+autocontrast` في اثنتين).

ولماذا **محرّكان**: `tesseract` سريع ودقيق على اللاتيني والأرقام، و`easyocr` شبكة عصبية تتفوّق
واضحًا على نصّ العربية الصغير. الاختيار ب`--engine`، والافتراضي `tesseract` (ثوانٍ لكل لقطة).

### ٣٦.٣ — اختبار الذات: يقيس المحرّك لا الذوق

`--self-test` يُرسَم نصّ معروف (عربي · لاتيني · أرقام) بخطوط النظام عبر Raqm ثم يُقرأ بالأنماط كلها،
ويُقارَن بعد تطبيع (توحيد الهمزات والتاء المربوطة وحذف التشكيل) بـ`SequenceMatcher`، ويفشل إن نزل
عن ٠٫٧٥. وهو الذي كشف خطأً في الأداة نفسها: **خط Noto Naskh Arabic يُرسم بالحروف اللاتينية أشكالًا
ليست الأبجدية** («Management console» ⟶ «0000000 Conoood»)، أي أن اختبارًا يُرسم بالخطّ الخطأ
يقيس الخطّ لا المحرّك — فلُكل حالة الآن خطّها.

| المحرّك | النتيجة بعد التصحيح |
| --- | --- |
| `tesseract` + `tessdata_best` | **٨/٨ مقبول** — اللاتيني 1.00، والعربي 1.00، و«النسخ الاحتياطي: ٣ نسخ» 0.95 (الرقم `٣` قرأه `؟` في نمط واحد) |
| `easyocr` | **٨/٨ مقبول** — الكل 1.00، بما فيه `٣` |

### ٣٦.٤ — ثم قُرئت اللقطات نفسها: ما قيس يُصحّح ما استُنتج

سُحبت لقطات `MT Manager Plus` السبع وتُليت بالمحرّك القوي، فظهر ما لم يكن مُقاسًا:

- **بالعربية والإنجليزية:** أسماء ملفات (`new2` · `apks` · `Mihon`) بثقة 98–100٪، وأزرار
  (`Rename` · `Compress` · `Properties` · `Add to bookmark` · `Tools`) بـ`tesseract`، وشريط المسار
  (`/storage/emulated/0/` · `Folders: N · Files: N · Disk: 447.73G/479.35G`) في **السبع**.
- **ودليل اللوحين صار نصًّا لا استنتاجًا:** في اللقطة ذات الفاصل الوسطي تظهر **كل قطعة مرّتين**
  (`new2` 99.6 و99.9 · `apks` 100 و100 · `Mihon` 98.9 و100) — أي قائمتان متماثلتان على يمين
  الفاصل ويساره.

⇒ وهذا **يعدّل قراءتي في تكملة ٣٥**: المرجع يُظهر اللوحين على **المجلد نفسه**، لا تبعًا بالاسم.
والوظفيتان موجودتان (`syncOther` للمجلد نفسه، و`mirrorFolder` للتبع بالاسم)، والاختيار بينهما
قرار المستخدم لا قراءتي — وهذا مذكور هنا ليُقرأ لا ليُكتَم.

**والجرد الكامل لما ينقصنا مستخرجًا من اللقطتين** (١٢ بندًا برموز `OCR-01…12`، مع الدليل
المقتبس وحال كل بند في الكود) في `docs/ai/UNIMPLEMENTED-PROPOSALS.md` §٨. وأعلى بند فيه
**النسخ المجدول** (`OCR-01`): لا `WorkManager` ولا `AlarmManager` في المستودع كله. وثلاثة
بنود **موجودة عندنا فعلًا** فلم تُحسَب ناقصة (منها اختيار نطاق النسخ لكل تطبيق — تحقّقناه في
`MaxBackupScreen:175` لا من الذاكرة).

### ٣٦.٥ — حدود صريحة

1. الأداة تُعيد **الحروف ومواضعها** ولا تفهم الواجهة: فاصل أو لون حالة أو أيقونة لا يزال بلا قراءة.
2. `easyocr` على المعالج أبطأ كثيرًا (عشرات الثواني للقطة كاملة) — ولذلك هو اختياري لا افتراضي.
3. العربية على مقاسات صغيرة مع ضغط JPEG تُقرأ بثقة 40–70٪ أحيانًا، أي أن الخطأ ممكن ولا يُخفي:
   الأداة تُطبع الثقة مع كل سطر، ومن أراد الحكم قرأ الثقة قبل النصّ.
4. **لم يُعدَّل أي كود منتج في هذه التكملة**: التغيير كله أداة جديدة + سطر في `AGENTS.md` §2.1.
5. ولم يُلتزم شيء في git.

## تكملة ٣٧ — `OCR-01` + `OCR-02` + `OCR-04`: النسخ المجدول، ومجموعات المجلدات، والمفضّلة

**المصدر:** ثلاثة بنود من الجرد الذي استُخرج من لقطاتك (`docs/ai/UNIMPLEMENTED-PROPOSALS.md` §٨)
بالترتيب الذي اقترحتُه هناك بنفسي — أعلى قيمة أولًا.

### ١. النسخ المجدول (`OCR-01`) — كان **غائبًا تمامًا**

| الملف | الدور |
| --- | --- |
| `ui/util/MaxBackupSchedule.kt` | الحساب كله **خالص**: قراءة `HH:MM`، والمسافة إلى الموعد القادم عبر منتصف الليل، والأيام، والشروط، والتخزين النصّي |
| `ui/util/MaxBackupScheduler.kt` | الجدولة والتسليح والتنفيذ (`JobScheduler`) + `MaxBackupJobService` |
| `AndroidManifest.xml` | `<service … BIND_JOB_SERVICE>` و`RECEIVE_BOOT_COMPLETED` |

**قرارات مقصودة:** المهمة **واحدة تُسلسل نفسها** لا مهمة دورية (الدورية لا تقبل تأخيرًا أولًا،
فمن اختار ٣:٠٠ لن يُنسخ عنده شيء حتى اليوم التالي)، و`setMinimumLatency` تعني «ليس قبل الموعد»
— **والشاشة تقول ذلك** بدل أن تَعِد بساعة يملكها الجهاز لا نحن. و`enforceConditions = true` في
المسار المجدول و`false` في زرّ «شغّل الآن»: من ضغط الزرّ طلب النسخة الآن.

### ٢. مجموعات المجلدات (`OCR-02`)

`ui/util/MaxBackupFolders.kt` + قسم في لوحة `Max Backup`: اسم تختاره ومسار أو أكثر، وحواران
(إنشاء · إضافة مسار)، وتحذير تقاطع لا منع، وتنقية الخطة عند حذف مجموعة (اسم محذوف لا يبقى هدفًا).

### ٣. المفضّلة (`OCR-04`)

`ui/util/MaxBackupFavorites.kt`: نجمة في كل صفّ، و«المفضّلة فقط» زرًّا مستقلًّا لا مرشّحًا خامسًا
(الشريط يحمل أربعة)، و**المفضّلة تُقدَّم في الترتيب ولا تُخفي أحدًا**.

### ٤. أربعة عيوب حقيقية كشفتها الاختبارات — لا التخمين

| # | العطب | الأثر | الدليل |
| --- | --- | --- | --- |
| ١ | `MaxBackupFolders.isBackupable` تطابق المسار **كاملًا** | `/sys/kernel` و`/proc/self` **تُقبلان**: شجرة لا تنتهي في مهمة مجدولة | اختبار التبديل: الرفض على أول قطعة من المسار ⇒ فشل ١ بالاسم |
| ٢ | `MaxBackupSchedule.decode` يقرأ `lastRun` بحالة أحرف مخالفة (المفاتيح تُخزَّن lowercase) | «آخر محاولة» تختفي بعد كل إعادة قراءة — بصمت تام | اختبار التبديل ⇒ فشل ١ بالاسم |
| ٣ | تحويل الأرقام العربية-الهندية يدويًّا في `parseClock` | **سطور لا تحمل شيئًا** + تعليق يدّعي أنها ضرورية (`toInt` يقرأها أصلًا) | اختبار التبديل بقي **أخضر** ⇒ أُزيل التحويل وصُحّح التعليق |
| ٤ | `CpuCoreControlScreen.kt`: استيراد `clickable` غير مستخدم | ضجيج دَين | `repo_audit.py` |

### ٥. التحقق

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **١١٥ اختبارًا OK** (+٢٨) · **٣٢ ملفًّا** بلا خطأ إعرابي بمُحلِّل Kotlin |
| إعادات العطب الأربع (أعلاه) | فشل **بالاسم** في ثلاث، والرابعة أثبتت أن الكود زائد ⇒ حُذف |
| `tools/i18n_coverage.py --assert` | exit 0 |
| `tools/code_health.py --assert` | الدَّين **10/29/66/26/2** ثابت بلا نمو (كان `oversized_files: 11` بعد الاستخراج، فاقتُطعت لوحة `Max Backup` إلى ٩٨٣ سطرًا بنقل بندَي السياسة إلى ملف القسم) — والحمراء الوحيدة `stray_root_file: 1` (ملفك) |
| `tools/repo_audit.py` | **PROBLEMS: 0** |

### ٦. حدود صريحة

1. **لا Android SDK هنا ⇒ لا ترجمة ولا فحص أنواع**: المُحقَّق إعراب بمُحلِّل Kotlin، واختبارات
   JVM خالصة (١١٥)، وقراءة مصدر.
2. **لا شيء جُرِّب على جهاز**: لا مهمة مجدولة نفّذت فعلًا، ولا شجرة مجلدات نُسخت، ولا نجمة وُسمت.
   و«متى يُشغّل النظام المهمة» قرار المنصّة ولم أقِسه.
3. المفضّلة **تُخزَّن مع بقية السياسة** في `MaxBackupScheduler` لا في تفضيلات المنصّة: مقصود
   (نفس الانضباط الذرّي)، ولو أردت عكس ذلك فهو تغيير مكان لا تغيير سلوك.
4. باقي الجرد لم يُنفَّذ، وأعلنته: `OCR-03` (الخلفيات) · `OCR-05` (بطاقة الجذر والتخزين) ·
   `OCR-06` (عدّادات الأنواع) · `OCR-07` (افتح بـ/شارك) · `OCR-08` (بصمة + `chmod`) ·
   `OCR-09` (صفّ مفاتيح) · `OCR-10` (بحث بمرشّح) · `OCR-11` (استعمال كل جذر) · `OCR-12` (ربط مستمر).
5. **ولم يُلتزم شيء في git.**

## تكملة ٣٨ — `OCR-06`: العدّاد وصفّ التجميع

**المصدر:** البند التالي في ترتيبي المنشور (`UNIMPLEMENTED-PROPOSALS.md` §٨).

| الملف | الدور |
| --- | --- |
| `ui/util/MaxBackupCounts.kt` *(جديد)* | الحساب كله خالص: حصيلة (نسخ · عناصر · سعة · غير مكتملة · أحدث)، وعدد المخفيّ، وعدد الظاهر |
| `ui/subscreens/MaxBackupRecentItem.kt` *(جديد)* | بند «أحدث النسخ»: العدّاد في العنوان، وصفّ «+N أخرى» يُنقر فيُوسّع ثم «اطوِ القائمة» |
| `ui/subscreens/MaxBackupHubScreen.kt` | يسقط `Handle` إلى `CopyFact` (سطر واحد) ويستدعي البند |

**قراران:** التجميع **إظهار مؤجَّل لا حذف** («+N أخرى» صفٌّ يُنقر فتُعرض البقية في مكانها، والطيّ
يُحفظ عبر إعادة التركيب)، والعدّاد **في العنوان** لا في سطر خفيّ تحته. ولاحظتُ أن عدّاد الصفوف
لكل صنف («٣٩ مكالمة») **موجود عندنا أصلًا** (`max_backup_system_rows`) فلم يُعَد تنفيذه — وهذا
ما طلبته: من وجد ميزة موجودة لا يُعيد بناءها.

**ولماذا نموذج بلا `Handle`:** `Handle` يُفكّ من `org.json` فلا يُترجم على JVM؛ ولذلك أخذ النموذج
ثلاثة أرقام (`CopyFact`) والواجهة تحوّل — فبقيت القاعدة كلها تحت اختبار يعمل هنا.

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **١٢٢ اختبارًا OK** (+٧) · **٣٤ ملفًّا** بلا خطأ إعرابي |
| إعادات العطب الثلاث | فشل **بالاسم** في كلها: `overflow` سالب · `visibleCount` يتجاهل التوسيع · مجموع بلا حاجز تجاوز |
| `i18n_coverage --assert` | exit 0 |
| `code_health --assert` | `oversized_files: 10` عند السقف — والفصل نقل بند «أحدث النسخ» إلى ملفه فعادت اللوحة ٩٨٢ سطرًا |
| `repo_audit.py` | **PROBLEMS: 0** |

### حدود

1. **لا Android SDK ⇒ لا ترجمة**، ولا شيء جُرِّب على جهاز: لا قائمة بـ١١٩ نسخة وُسّعت فعلًا.
2. العدّاد «بالنوع» لكل نسخة نظام **لم يُضَف**: عدّاد الصفوف لكل صنف موجود في شاشة بيانات
   النظام، وقراءة مستند كل نسخة نظام لجمع أنواعها تكلفة لم تُطلب. من أرادها فليقل.
3. **ولم يُلتزم شيء في git.**

## تكملة ٣٩ — `OCR-10`: مرشّح البحث وتحديد النتائج في مدير الملفات

**المصدر:** البند التالي في ترتيبي المنشور (`UNIMPLEMENTED-PROPOSALS.md` §٨).

| الملف | الدور |
| --- | --- |
| `ui/util/FileSearchFilters.kt` *(جديد)* | تصنيف المحتوى بالامتداد، وحدّ الحجم، ونافذة العمر، وقاعدة المجهول |
| `ui/util/FilePaneModel.kt` | `FilePaneState.search` + `visible()` = بحث ثم مرشّح ثم ترتيب، و`filtering` للإعلان |
| `ui/component/FilePaneColumn.kt` | صفّ المرشّحات (٣ قوائم) + صفّ «N نتيجة · حُدَّد M» مع «حدّد النتائج» و«اعكس» |
| `ui/subscreens/FileManagerScreen.kt` | الوصل: تغيير المرشّح **يُنقّي التحديد** إلى ما هو معروض |

**قراران مقصودان:**

1. **قاعدة المجهول:** حجم أو تاريخ غير مقيس لا يُعَدّ مطابقًا لمرشّح يقيسه — وإلا صار العدد
   المعروض كاذبًا. وهذا نفس انضباط ADR-07 (المجهول `status_unknown`) مطبَّقًا على مرشّح ملفات.
   واختبار «فيديو مؤرَّخ في المستقبل لا يُستبعد» يمنع تشدّدًا في الاتجاه الآخر.
2. **التصنيف بالامتداد لا بـ`FileKind`:** `FileKind` تصف **شكل** المدخل (مجلد/ملف/رابط)، فـ
   `IMG_2.jpg` و`backup.zip` كلاهما `RegularFile` — والمرشّح المطلوب هو «صور» لا «ملفات».

**واكتشاف:** `FileSelection.selectAll` و`invert` كانتا **مكتوبتين منذ جولة مدير الملفات وغير
مستدعيتين من أي مكان** — أي أن «حدّد الكل» موجود في النموذج وغائب عن الشاشة.

### التحقق

| الفحص | النتيجة |
| --- | --- |
| `tools/test_maxai_jvm.py` | **١٣٣ اختبارًا OK** (+١١) · **٣٦ ملفًّا** بلا خطأ إعرابي |
| إعادات العطب الأربع | فشل **بالاسم** في كلها: حجم مجهول يُطابق · مجلد بلا نوع · تاريخ مجهول يُطابق · `visible()` يُسقط المرشّح |
| إعادة عطب في اختباري أنا | `invert` ظنّي كان خطأً؛ الاختبار كشفه وصُحّحت العبارة إلى معنى الدالّة الحقيقي |
| `i18n_coverage --assert` | exit 0 (٢٤ نصًّا جديدًا en+ar) |
| `code_health --assert` | `oversized_files: 10` بلا نمو — **لكن `FileManagerScreen.kt` بلغ ٩٦١ سطرًا**: البند التالي عليه أن يبدأ باستخراج |
| `repo_audit.py` | **PROBLEMS: 0** |

### حدود

1. **لا Android SDK ⇒ لا ترجمة**، ولا شيء جُرِّب على جهاز: لا مرشّح ضُغط فعلًا ولا نتيجة حُدِّدت.
2. **البحث ما زال داخل اللوح** (مجلد واحد معروض)، فـ`OCR-10` عنده **نصفه الأول** فقط: «نتائج
   بحث» عابرة للمجلدات (تفحّص شجرة) ليست فيه. من أرادها فهي ميزة مستقلة تحتاج قرارًا في الحدود
   (عمق التفحّص، والتخزين الخارجي، والوقت) — وليست سطرًا يُضاف.
3. `OCR-08` (بصمة الملف و`chmod`/`chown`) التالي في الترتيب، **ويحتاج جهازًا**: لا يُغلق من هنا.
4. **ولم يُلتزم شيء في git.**

## تكملة ٤٠ — **أول بناء حقيقي في تاريخ المشروع** + تسريع CI + تحديث الحزم

**والخبر أولًا: «لا Android SDK» لم تعد صحيحة.** ثبّتُ SDK في هذه البيئة
(`~/android-sdk`: cmdline-tools + `platforms;android-36` + `build-tools;36.0.0`) وأنشأتُ
`manager/local.properties` (وهو في `.gitignore`، فلم يدخل المستودع). وصار **البناء والاختبار ممكّنين**
هنا — وصُحّح `AGENTS.md` §5 بالأوامر الدقيقة.

### ١. ما كشفه البناء — وهو كل الغرض من تشغيله

| # | ما وُجد | الدليل | الحال |
| --- | --- | --- | --- |
| ١ | `MaxBackupScreen.kt:719` + `MaxBackupPickerScreen.kt:553`: استيراد `androidx…design.content` ناقص | `:app:compileDebugKotlin` -> `Unresolved reference 'content'` | أُصلح (و**الأول عطب كامن منذ جولة سابقة**: ملف أُضيف ولم يُترجم قط) |
| ٢ | `MaxBackupScheduleSection.kt:478`: `joinToString` ليست `inline`، ونداء `dayText` (@Composable) في محوّلها | `@Composable invocations can only happen from…` | أُصلح: الأسماء تُجمع بـ`map` (inline) قبل `stringResource` |
| ٣ | **عطب حقيقي في الاحتفاظ:** `MaxBackupModel.toPrune` يعيد ترشيح القائمة الأصلية فيضيع **ترتيب القرار** (الأحدث أولًا) | `MaxBackupModelTest.الاحتفاظ يُبقي الأحدث ويُقلّم الباقي` — ١ من ٦٤٨ | أُصلح: الناتج بترتيب القرار، والمطابقة بـ`(folder, createdAtMs)` لا بالمجلد وحده |

**والأرقام:** `:app:testReleaseUnitTest` ⇒ **٦٤٨ اختبارًا، كانت واحدة فاشلة والآن كلها تمرّ** ·
`:app:minifyReleaseWithR8` ⇒ **BUILD SUCCESSFUL** (قواعد ProGuard سليمة) · `:app:assembleDebug` ⇒
**APK ١٢٤ ميجابايت** · `:app:compileDebugKotlin` ⇒ صفر خطأ.

> **هذا هو الدرس المتكرر في هذا المستودع:** ثلاثة من الأعطاب الأربعة أعلاه كانت في ملفات **لم تُترجم مرة
> واحدة** قبل اليوم. لا مُحلِّل إعراب ولا اختبار JVM خالص يرى استيرادًا ناقصًا أو نداءً في سياق خاطئ — **المصرّف
> يراها.**

### ٢. تسريع البناء (مقيس، لا مقدَّر)

البيئة التي قِستُ فيها **٤ أنوية · ١٥ جيجابايت = نفس مقاس runner في CI**.

| القياس | الزمن |
| --- | --- |
| `:app:lintVitalRelease` (يعمل **داخل** `assembleRelease`) | **٣:٢٩** |
| `:app:lintRelease` (كل التحذيرات) | ٧:١٢ — و**٢٤٦٩ خطأ**، أي أنه ليس بوابة اليوم ولا يصلح أن يكون |
| `:app:testReleaseUnitTest :app:minifyReleaseWithR8` | ٦:٢٩ |
| أمر CI (اختبار + حزمة) | ٢:٤٤ دافئًا |

**التغيير:** `lintVitalRelease` خرج من المسار الحرج بـ`-x :app:lintVitalRelease` في أمر البناء، وانتقل إلى
**مهمة CI موازية** (`lint-vital`) تُشغّله كما هو. والتحقّق من الوجهين في مخطط المهام:

- `-x` ⇒ **صفر** مهمة lintVital في `assembleRelease`، والمهمة **تبقى قابلة للنداء** ✅
- و`lint { checkReleaseBuilds = false }` ⇒ **يُمحى المهمة نفسها** (اختفت من `:app:tasks --all`) ⇒ **رُفض**:
  يوفّر الوقت ويُسقط الحاجز، وهذا إسقاط جودة لا تسريع.

ورُفع هيب Gradle في CI من 4g إلى **6g** (Runner فيه ١٥ جيجابايت). **المتوقّع: ~١٥ دقيقة ⇒ ~١١–١٢.**

### ٣. الحزم: جرد كامل ثم تحديث **مُتحقَّق** فقط

جُرِّد الكتالوج مقابل Google Maven وMaven Central، وحُدِّثت ستة إصدارات ثم **بُنيت** للتحقق:

| الحزمة | كان | صار | التحقق |
| --- | --- | --- | --- |
| `androidx.compose.material3` | `1.4.0-alpha15` | **`1.4.0` (مستقر)** | ✅ اختبارات + حزمة |
| `androidx.transition` | `1.7.0` | `1.7.1` | ✅ |
| `com.google.dagger:hilt` | `2.59.2` | `2.60.1` | ✅ |
| `androidx.appcompat` | `1.7.1` | `1.8.0` | ✅ |
| `com.google.android.material` | `1.13.0` | `1.14.0` | ✅ |
| `androidx.navigation` | `2.9.4` | ~~`2.10.1`~~ **رُفض** | ❌ `checkReleaseAarMetadata`: يطلب **compileSdk 37** |

**والرابع هو الفائدة الحقيقية:** التعليق في الكتالوج كان يقول إن ما بعد هذا الخط يطلب SDK 37 — **فقِسته
وأثبتُّه**: `navigation-compose-android:2.10.1` + `lifecycle-runtime-compose-android:2.11.0` +
`lifecycle-viewmodel-compose-android:2.11.0` كلها ترفض android-36. فأُعيد `2.9.4` وسُجِّل الدليل في الكتالوج.

**وما تُرك بقرار مكتوب:** `compose-bom` (2026.09.00 يجرّ سلسلة SDK 37) · `lifecycle` 2.11 · `activity-compose`
1.13 · `media3` 1.11.1 (**سلوك ExoPlayer لا يُقاس بلا جهاز**) · `AGP` 9.4.1 و`Kotlin` 2.4.20 (**يحتاجان رفع
Wrapper معًا: AGP 9.4 يطلب Gradle أحدث، والمقترض في الـwrapper 9.5.1**) — كلها تحتاج مهمة مستقلة لا سطرًا.

### ٤. حدود صريحة

1. **`assembleRelease` لم يُشغَّل هنا**: حرس في `build.gradle.kts` يرمي بلا `KS_PWD` (سرّ CI)، ولا أكتب سرًّا
   ولا أخمّنه. المُتحقَّق: debug APK + **R8** + اختبارات نسخة release.
2. **لا شيء جُرِّب على جهاز** — البناء يعني «يترجم ويمرّ الاختبارات»، لا «يعمل على هاتفك».
3. تغيير الـworkflow **لم يُشغَّل على GitHub Actions** (لا وصول): تحقّقتُ من YAML بـ`yaml.safe_load`، ومن
   **نفس أمر Gradle حرفيًّا** محليًّا (`-x` + 6g)، ومن أن المهمة الموازية قابلة للنداء — وهذا سقف ما يمكن
   إثباته من هنا.
4. مخرجات `manager/*/build` تُركت في الشجرة (وهي في `.gitignore`) لتسريع الجولات القادمة؛
   و`manager/local.properties` باقٍ بقصد ليبقى البناء ممكنًا.
5. **ولم يُلتزم شيء في git.**

### FM-UI-02 — GPU Studio visual hierarchy pass — 2026-09-19

**TASK:** FM-UI-02 (medium) — `DONE_WITH_CONCERNS`
**FILES:** `manager/app/src/main/java/nd/max/ui/subscreens/GpuStudioScreen.kt` فقط.
لم يتغير `GpuStudioViewModel` أو `GpuHardwareBackend` أو أي مسار كتابة عتاد.

**WHAT:** أُعيد ترتيب العرض ليبدأ بقراءة التردد الحيّة كـ`MaxMetricReadout` كبيرة، ثم الحمل والحرارة كقراءة مقارنة، ثم النطاق والحاكم وعمر القراءة، قبل النوايا والتحكم اليدوي. أزيلت مساواة الأهمية البصرية بين ستة أسطر قياس، وبقيت كل حالات unknown/unsupported والـprovenance كما هي. التصميم يستخدم مكوّنات Max الحالية، لا نظام بطاقات جديدًا ولا تبعية جديدة.

**GATES:** `python3 tools/i18n_coverage.py --assert` = 0 عوائق · `repo_audit.py` = `PROBLEMS: 0` · فحص الأقواس للملف = 101/101.
**BUILD:** `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` = **BUILD SUCCESSFUL** في 1m50s · اختبارات JVM = **134 OK**.

**RESIDUAL RISK:** لم تُلتقط صورة جهاز حقيقي في هذه البيئة، لذلك لم يُحكم بصريًا على RTL أو حجم الخط الكبير أو عرض القيم على جهاز ضيق. تحذيرات Kotlin القائمة لم تتغير. `code_health --assert` يبقى متأثرًا بملف الجذر المعروف `session-ses_f487.md`، لا بهذا التعديل.
**NEXT:** تركيب APK على الجهاز، فتح GPU على Qualcomm وMali إن أمكن، واختبار سيناريو: اختيار Performance/intent → مراجعة → Apply → رفض العقدة → ظهور rollback.

### APPS-STATE-01 — توحيد حالة التحميل في شاشة التطبيقات + استخراج شريط اللوح — 2026-09-19

**TASK:** APPS-STATE-01 (small) — `DONE_WITH_CONCERNS`
**FILES:** `ui/mainscreens/ApplistScreen.kt` · `ui/component/FileManagerPanels.kt` (استقبل شريط اللوح) ·
`ui/subscreens/FileManagerScreen.kt` (فقده) · `test/java/nd/max/ui/mainscreens/ApplistPresentationArchitectureTest.kt` (جديد) ·
`tools/test_maxai_jvm.py`.

**WHAT:** القياس سبق التخمين: `tools/read_image_text.py` قرأ نصّ `applist_loading_desc` من اللقطة، وهندسة
الصورة أعطت حلقة قطرها ١٤٠px بسماكة قوس ٣٠–٣٤px على عرض ١٢٢٠px — أي `CircularProgressIndicator(76.dp)`
بعنوان `headlineMedium`. وكان ذلك **العنصر الوحيد في التطبيق** الذي يرسم حالته داخل الشاشة، بينما الشاشة نفسها
تعرض خطأها وفراغها بـ`MaxErrorState`/`MaxEmptyState`. ⇒ صارت حالة التحميل `MaxLoadingState` بالعبارتين
نفسهما، فالحالات الثلاث تقرأ بلغة واحدة وبمقاس واحد (`titleMedium` · `bodyMedium` · أيقونة ٢٨dp في بطاقة).

**وأُصلح دَين الجولة السابقة:** `FileManagerScreen.kt` كان قد بلغ ١٠٠٢ سطر (فوق سقف ١٠٠٠)، فأُخرج شريط إجراءات
اللوح (`ActivePaneStrip` + `StripAction`) وخرائط الفرز والرفض إلى `ui/component/FileManagerPanels.kt` — الملف
الذي أُنشئ لهذا الغرض نصًّا. الشاشة الآن **٨٥١ سطرًا**، وسقط **١٩ استيرادًا ميتًا** — اثنان منها (`DirectoryListing` و`EventLog`) كانا
ميتين قبل هذا التعديل أصلًا، والبقية صارت ميتة بنقل الشريط. و`getValue`/`setValue` **لم يُحذفا** وإن أشار إليهما
فحصٌ ساذج: هما مشغّلا `by` في `var left by rememberSaveable { … }` ولا يُكتب اسمهما في الكود أبدًا.

**GATES:** `code_health --assert` → `oversized_files` رجع تحت السقف (الحمراء الوحيدة `stray_root_file`:
`session-ses_f487.md`) · `i18n_coverage --assert` = 0 عوائق · `repo_audit.py` = `PROBLEMS: 0` ·
هارنس JVM = **١٣٧ OK** و**٣٨ ملفًّا** بلا خطأ إعرابي.
**BUILD:** `:app:compileDebugKotlin` ✅ · `:app:testDebugUnitTest` = **٦٥٢ اختبارًا، صفر فشل**.
**إعادات العطب:** أربع طفرات (حلقة يدوية تعود · الخروج من عائلة الحالات · توسيع الشرط إلى كل تحديث ·
إدخال خطّ display) ⇒ فشل كل منها **بالاسم** في `ApplistPresentationArchitectureTest`، ثم عاد الشجر نظيفًا.
ومُصرّف Kotlin نفسه أمسك خطأي: دالّة واحدة للرفض لا تكفي لأن مسار الـsnackbar يمرّر `FileOpRefusal` لا حكمًا
⇒ فُصلتا `fileRefusalText(reason)` و`fileRefusalVerdictText(verdict)`.

**RESIDUAL RISK:** لم يُرَ العنصر على جهاز — المقاس النهائي مشتقّ من `MaxContentState` لا من صورة. وتغيّرت أسماء
`refusalText`/`refusalTextOrNull`/`sortLabel` في الانتقال إلى `fileRefusalText`/`fileRefusalVerdictText`/`fileSortLabel`
(الاسم الجديد يحمل نطاقه في ملف مشترك) — ولا مستدعي خارجيًا لها خارج هذه الشاشة.
**NEXT:** تشغيل APK ورؤية الحالة الجديدة فور فتح «التطبيقات» على شاشة بعرض فعلي ضيّق، ثم قرار نقل
`session-ses_f487.md` من الجذر إلى `docs/ai/` لإغلاق الحمراء الأخيرة.

### DETAILS-L10N-01 — إعادة بناء شاشتي التخزين والحرارة بلغة التطبيق + مصدر حرارة البطارية — 2026-09-19

**TASK:** DETAILS-L10N-01 (large) — `DONE_WITH_CONCERNS`
**FILES:** جديد: `ui/subscreens/StorageDetailScreen.kt` (638) · `ui/subscreens/ThermalDetailScreen.kt` (511) ·
`ui/util/StorageScanModel.kt` (207) · `ui/util/ThermalModel.kt` (93) · `ui/util/StorageUtil.kt` (246) ·
`ui/design/MaxBar.kt` (86) · `test/ui/util/StorageScanModelTest.kt` · `test/ui/util/ThermalModelTest.kt` ·
`test/ui/subscreens/DetailScreensLanguageContractTest.kt`. معدّل: `ui/mainscreens/DashboardDetailScreens.kt`
(فقد الشاشتين ⇒ 357 سطرًا) · `ui/subscreens/ChargingScreen.kt` · `ui/design/MaxTokens.kt` ·
`values/strings.xml` + `values-ar/strings.xml` · `tools/code_health_baseline.json` · `tools/test_maxai_jvm.py`.

**WHAT:** الشاشتان كانتا تُبنيان بلغة **لوحة البداية** (`DashCardWrapper`/`LiveHeader`/`GlowLinearBar`) لا بلغة
التطبيق (`MaxListScreen`/`MaxSection`/`MaxGroup`/`MaxMetricReadout`) — وهو سبب «غير متناسقة مع الشكل العام».
فأُعيد بناؤهما على المكوّنات المشتركة، ونُقلتا إلى ملفّيهما (357 سطرًا من `DashboardDetailScreens.kt` صارت
638+511 في ملفين مستقلّين)، وأُضيف أصل مشترك واحد `MaxBar` بدل أشرطة نسبة مرسومة داخل كل شاشة.

- **التخزين:** جرد نقاط التحميل الحقيقية من `/proc/mounts` (لا مجلد واحد مفترض) مع مساحة/`inodes` لكل نقطة،
  وتصنيف البايتات في حِزم (صور · فيديو · صوت · أرشيف · تطبيقات · متفرّقات) بـ`StorageScanModel` الخالص.
- **الحرارة:** المناطق + أجهزة التبريد + نقاط التخفيف (`trip points`) تُقرأ **مرّة واحدة** لأنها ثابتة في النواة؛
  العيّنة الحيّة كل ٣ ثوانٍ. قبلًا كانت تقرأ مئات قراءات sysfs لعرض رقم لا يتغيّر.
- **حرارة البطارية:** كانت تُقرأ من `$batteryDir/temp` وحدها فيُعرض `0.0 °C` على جهاز لا تُقرأ فيه تلك العقدة،
  بينما الشاشة الرئيسية تعرض قراءة صحيحة — رقمان لفكرة واحدة يفترقان. الآن المصدر واحد:
  `ThermalUtil.readBatteryTemperatureC(context)` (بثّ Android ثم عقدة البطارية ثم منطقة الحرارة ثم `thermalservice`)،
  والوسم «الحرارة» فقط (`charging_temp`)، وقُرئ الـViewModel احتياطًا أخيرًا.
- **واسم مورد يكذب أُصلح:** `detail_thermal_battery_heat` صار `detail_thermal_heat` (وقيمته «الحرارة») — الاسم
  القديم كان يقول «حرارة بطارية» لوسم يقول «حرارة». لا مستدعي غيره.

**الحرس الجديد، وعلّتان مقيسَتان بداخله:** حرس `DetailScreensLanguageContractTest` يمنع عودة لغة اللوحة،
ويمنع نصًّا إنجليزيًّا داخل شاشة مُعرَّبة، ويمنع مقارنة تصنيف نواة بنصّ مُعرَّب. و**أول تشغيل له اتّهم كودًا سليمًا
مرّتين، فأُصلح الحرس لا الكود** — وكلاهما مُسمّى في تعليقه:
1. المحرّك يبدأ من **قوس إغلاق** (`"…$LTR",`) بلا `\n` في الصنف الممنوع، فيمتدّ إلى سطر لاحق ويلتقط `(total - free)`.
2. `UNAVAILABLE` كان يُمنع كـ**نصّ فرعي** فيطابق المعرّف `MAX_VALUE_UNAVAILABLE` (وقيمته `"—"`).
وحرس يكذب مرّة يُعطَّل مرّة، وهذا أسوأ من غيابه. وصُحّح طبعه كذلك: `match.value` بدل `MatchResult.toString()`
الذي يطبع `MatcherMatchResult@32ee6fee` — رسالة حرس لا تقول ماذا رأت لا تُعلّم أحدًا.

**إعادات العطب (٤ طفرات، كلها تُفشل بالاسم):**
| الطفرة | الحكم |
| --- | --- |
| نصّ إنجليزي حرفي داخل الشاشة | `no user-visible English text is hardcoded inside a translated screen` |
| عودة `DashCardWrapper` | `neither screen draws the dashboard language` |
| حرارة البطارية من مصدر آخر | `the thermal screen reads battery heat from the home screen source` |
| مقارنة تصنيف بنصّ مُعرَّب | `Zone categories must never be compared against a translated string` |

والشجر نظيف: ٤/٤ تمرّ على الكود الأصلي، وكل ملف عاد إلى حاله بعد كل طفرة (مُثبَّت بالمقارنة الحرفية).

**GATES:** `code_health --assert` = **exit 0 · «صحّة نظيفة»** (الصفر الأربعة كلها ✓) · `i18n_coverage --assert` = 0 عوائق ·
`repo_audit.py` = `PROBLEMS: 0` · هارنس JVM = **155 OK**.
**أُغلق الحاجز الأحمر الوحيد:** `session-ses_f487.md` نُقل إلى `docs/ai/` (غير متتبَّع في git، فلا حذف ولا فقدان محتوى).
و**أُعلن خفض السقف** لأن الإصلاح خفّض الدَّين فعلًا: `hardcoded_ui_literals` **80 → 66** (الأصل: ١٤ نصًّا صلّبًا
كانت في الشاشتين القديمتين) و`presentation_hw_writes` **27 → 26**. السقف الآن مطابق للواقع لا أعلى منه.
**BUILD:** `:app:testReleaseUnitTest` = **670 اختبارًا · صفر فشل** (2m52s) · `:app:compileReleaseKotlin` = **BUILD SUCCESSFUL** (2m5s).
**RESIDUAL RISK:** لم تُرَ الشاشتان على جهاز — التناسق مُشتقّ من المكوّنات المشتركة لا من لقطة. والمسح **قراءة فقط**
ولا يتجاوز ما يبلغه التطبيق بلا root (`/proc/mounts` + `File`)، ولا كتابة عتاد جديدة في الشاشتين
(`presentation_hw_writes` لم يرتفع؛ مسارات sysfs تُعرض كـprovenance لا تُكتب).
**NEXT:** تصنيف الحِزم يقيس ما يراه التطبيق لا القرص كله؛ وتوسيعه إلى بقية مسارات المستخدم يحتاج `MANAGE_EXTERNAL_STORAGE`
أو `Shizuku` — قرار يُكتب أولًا.

---

## تكملة ٤١ — `FM-02`: مدير الملفات يُعاد بناؤه على طلب المالك، والبناء هو الحكم (2026-09-19)

**نصّ الطلب (المالك):** «قم بإعادة كتابة شاشة مدير الملفات فهي عديمة الفائدة وغير متناسقة وصعبة
الاستخدام كأنها من صنع مبتدئ، أريده كمثل لقطات الشاشة هذه» — سبع لقطات لمدير ملفات مرجعي (تبويبات ·
شريط مسار · سطر حالة بعدد المجلدات والملفات والمساحة · صفوف كثيفة · شريط أوامر سفلي · قائمة أوامر
بأسماء)، ولقطة ثامنة لشاشتنا الحالية سُمّيت «الهراء».

### ٠. كيف قُرئت اللقطات — لا بالتخمين

المسارات الثمانية كانت صفحات `ibb.co` لا صورًا مباشرة، فاستُخرج رابط كل صورة من الصفحة، ثم **قُرئت
بالأداة الموجودة في المستودع**: `tools/read_image_text.py --self-test` ⇒ **٨/٨ مقبول** (نصّ معروف
يُرسم ثم يُقرأ)، ثم قراءة اللقطات بمحرّك `tesseract` ونماذج `tessdata_best` (نُصّبت في `~/.local/share/tessdata`
خارج المستودع). ولأن أداة الجلب عند الوكيل ترفض `image/png|jpeg`، فهذا هو الطريق الواقعي الوحيد.

**ما نُطق فعلًا من اللقطات** (نصوص مقروءة، لا انطباع): `Folders: … Files: … Disk: 447.73G/479.35G` ·
شريط تبويبات بمجلد حالي و«+» · قائمة سياق بأسماء (`Copy` · `Delete` · `Rename` · `Tools` · `Compress` ·
`Properties` · `Add to bookmarks`) · قائمة تجاوز (`Sort` · `Refresh` · `Hidden files` · `Set as Home` ·
`Swap windows` · `Exit`) · ولوحان مستقلّان (الشريحة اليسرى واليمنى تعرضان المجلد نفسه في اللقطة ٧).
**وما لم يُقرأ لا يُوصف**: الأيقونات والألوان والفواصل بقيت كما قِيست حدوديًّا فقط، ولم يُبنَ عليها قرار.

### ١. التعارض المشروع مع ADR-18 — يُعلَن لا يُسكَت عنه

`ADR-18` يمنع إعادة ما بُني لأسباب جمالية. والطلب هنا **ليس جماليًّا**: أُعلن العطب في الاستعمال
(«عديمة الفائدة … صعبة الاستخدام»)، وهو تشخيص صحيح بالأرقام: كان الواحد من لوحين يستنزف **أربعة
أشرطة قبل أول صفّ ملفّ** (حبّة اسم · مسار · فتات خبز · حقل بحث دائم)، وأفعال الإجراءات موزّعة على ثلاثة
أشرطة بأيقونات بلا أسماء، وكل صفّ **سطران** بخمس صفات مدمجة (منها الصلاحيات `drwxr-xr-x` في كل صفّ).
ولذلك: النموذج الخالص والحرس لا يُمَسّان (لا يُعاد بناء ما هو مُثبت)، والبناء الجديد **عرض** فقط.
وهذا هو `ADR-36` أدناه.

### ٢. ما تغيّر — العرض وحده

| كان | صار |
| --- | --- |
| شريط أفعال لكل لوح بثمانية أزرار داخل صفّ يمرّ أفقيًّا | **شريط واحد في الأسفل**: صعود · تحديث · مجلد جديد · بحث · تحديد · مواقع سريعة (ستّة، بلا تمرير) |
| شريط تحديث/صعود/مبادلة ثانٍ في الصفحة | أوامر الشاشة في **قائمة واحدة بأسمائها**: مزامنة · مبادلة · ترتيب اللوحين (ثلاثة) · ربط · إخفاء/إظهار المخفيّ · الطرفية |
| حقل بحث دائم + ثلاث قوائم مرشّح + سطر أفعال نتائج في كل لوح | البحث **يُفتح بطلبه**، والمرشّح تحته، و«حدّد الكل/اعكس» في قائمة شريط التحديد |
| فتات خبز يُمرَّر أفقيًّا | شريط مسار نصّي واحد يُنقَر فيُحرَّر (`Go to path`) |
| صفّ بسطرين وسط بطاقة لكل صفّ | **صفّ بسطر واحد**: رمز · اسم · حجم · تاريخ (عمودان ثابتا العرض)، والفواصل خطوط شعرية |
| لا عدّ ولا مساحة | **سطر حالة**: عدد المجلدات · عدد الملفات · عدد المُخفيّ · مساحة نظام الملفات الذي يقف عليه اللوح (و«غير مقروءة» حين لا تُقاس) |
| لا تبويبات | **شريط تبويبات لكل لوح**: المجلد الحالي + المجلدات المثبّتة + «+»، وتبقى مع التنقّل (‏`PaneTabs`) |
| الملفات المخفية تُعرض دائمًا بلا قرار | مخفيّة افتراضيًّا **وعددها معلن** في سطر الحالة، ومفتاحها في قائمة الشاشة |
| «٠ B» أمام المجلدات في المرشّح | لا حجم لمجلد غير مقيس: العمود يبقى فارغًا للمجلد |

### ٣. ما بقي من الهندسة السابقة (لم يُلمَس)

`FileOpGuard` (وحالات الرفض الستة) · الأحكام الثلاثة للنتيجة (`نُفِّذ وتُحقِّق` / `نُفِّذ بلا تحقق` / `فشل`) ·
`DirectoryCache` (MVC: عرض فوري ثم قراءة طازجة) · حرس القراءة العالقة ·لوحا التنقّل المرتبط (بالمسار لا
بالاسم) · قراءة `stat` بصيغة معلنة ونزولها إلى الأسماء عند سقوط الصفات · ولوحتا التفاصيل والمعاينة كما هما.

### ٤. أعطاب وتصحيحات كشفها العمل نفسه

| # | ما وُجد | الدليل | الحال |
| --- | --- | --- | --- |
| ١ | **عطب قصّ تحديد**: تغيير المرشّح كان يقصّ التحديد على المجموعة **القديمة** لا الجديدة | قراءة الشيفرة + تعيين الدالّة على `keepVisibleSelection(pane.copy(search = filter))` | أُصلح قبل البناء |
| ٢ | `paneBanner` دالّة `@Composable` تُنادى داخل نطاق `LazyColumn` | `:app:compileDebugKotlin` ⇒ `@Composable invocations can only happen from the context of a @Composable function` | أُصلح: تُحسب **قبل** الدخول في النطاق |
| ٣ | `linked` غير مُمرَّر إلى `PanePathRow` بعد الفصل | `Unresolved reference 'linked'` | أُصلح |
| ٤ | وسائط مُسمّاة لدالّة-لامبدا (`navigate(..., push = true)`) و`isNotEmpty()` كدالة و`Modifier.border` غير موجود في لغة التصميم (القائم `MaxTone.border`) | ثلاثة أخطاء مصرّف | أُصلحت كلها: `Surface` + `BorderStroke` هو نمط «مُفعَّل» المستعمل في التطبيق أصلًا |
| ٥ | اختبار كتبتُه بنفسي جمع خطأً: بعد إخفاء المُخفيّ بقي مجلدان لا ثلاثة | `:app:testReleaseUnitTest` ⇒ ١ فشل من ٦٧٩ | صُحّح الاختبار (الكود كان سليمًا) |
| ٦ | ملفان دخيلان في الجذر (`check2.py`، `fix_tweak.py` — الثاني يشير إلى مسار ويندوز لمشروع آخر) كانا يُسقطان `code_health --assert` **قبل أي تعديل** | البوابة: `stray_root_file: 2` | **بإذن المالك** نُقلا إلى `tools/legacy/` ⇒ `exit 0` |

### ٥. الأدلة (أوامر وأرقام حرفية)

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk"   # ثُبِّت في هذه الجولة
cd manager
bash gradlew :app:compileDebugKotlin --build-cache --parallel -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
bash gradlew :app:testReleaseUnitTest :app:assembleDebug -x :app:lintVitalRelease --build-cache --parallel -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
bash gradlew :app:minifyReleaseWithR8 --build-cache --parallel
```

| الأمر | النتيجة المقيسة |
| --- | --- |
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL 1m55s** · صفر خطأ · **صفر تحذير** في ملفات الجولة |
| `:app:testReleaseUnitTest :app:assembleDebug` | **BUILD SUCCESSFUL** · **٦٧٩ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** · APK `118,792,784` بايت في آخر بناء (والقياسات بين البناءات تختلف بضع كيلوبايتات — ترتيب الحزم، لا تغيير محتوى) |
| زمنه في ثلاث تشغيلات | ‏8m37s وفيه **١ فشل** (اختبار جديد لي) ⇒ 43s بعد إصلاحه ⇒ **3m42s** بعد التعديلات الأخيرة |
| `:app:minifyReleaseWithR8` | **BUILD SUCCESSFUL 5m35s** |
| `python3 tools/code_health.py --assert` | **exit 0 · «صحّة نظيفة»** (الأربعة أصفار) · الدَّين `10 / 29 / 66 / 26` — **لا ارتفاع**، و`oversized_files` رجع إلى **10** بعد فصل ملف الشاشة |
| `python3 tools/i18n_coverage.py --assert` | **exit 0 · 0 عوائق** · تطابق `values-*`/المنتقي/`locales_config` = OK · `values/` ↔ `values-ar/` في ملف مدير الملفات: **184/184 مفتاحًا، صفر فرق** |
| `python3 tools/repo_audit.py` | **PROBLEMS: 0** |
| بوابات `VALIDATION.md` §4 أ–ج · §6أ | لا `Scaffold` جديد · استيراد `nd.max.ui.design` في كل ملف لُمس · لا `Text("…")` صلبة · لا كتابة عتاد مباشرة |

**وأرقام الجولة:** ٥ ملفات Kotlin أُعيدت كتابتها/أُنشئت (٢٬٦٢٣ سطرًا) · ٣ ملفات نموذج خالص عُدّلت · ٩
اختبارات JVM جديدة · ٢٧ مفتاحًا جديدًا في اللغة **مرّتين** (إنجليزية وعربية) · ملفان دخيلان نُقلا.

### ٦. حدّان يُعلنان

1. **لم تُرَ الشاشة على جهاز.** الإثبات هنا: تُصرَّف، وتمرّ ٦٧٩ اختبارًا، وتُبنى APK وR8. أما RTL وحجم
   الخط الكبير واللمس والسلوك مع الجذر الحقيقي فتحتاج جهازًا — ولا يُدَّعى أنها مُتحقَّقة.
2. **مفتاح «التبويبات» يُخفى/يظهر بحسب الحاجة**: لم يُجرَّب على جهاز حجم الشريط عند ٦ تبويبات في لوح
   بعرض ١٧٠ نقطة؛ الشريط يمرّ أفقيًّا فالتصميم يقول إنه لن يُقصّ، وهذا **تصميم** لا قياس.

### ٧. التسليم بقالب `VALIDATION.md` §8

```
TASK: FM-02 (large) — إعادة بناء شاشة مدير الملفات
FILES:
  added:     ui/design/MaxCommandMenu.kt (139) · ui/subscreens/FileManagerActions.kt (137) · tools/legacy/{check2.py,fix_tweak.py}
  rewritten: ui/subscreens/FileManagerScreen.kt (973) · ui/component/FilePaneColumn.kt (811) · ui/component/FileManagerPanels.kt (563)
  model:     ui/util/FileSystemModel.kt (+ EntryCounts · DiskSpace · isHidden/withoutHidden/counts)
             ui/util/FilePaneModel.kt (+ showHidden · tabs · PaneTabs)
             ui/util/FileSystemEngine.kt (+ diskSpace بـStatFs)
  strings:   values/max_files_strings.xml + values-ar/max_files_strings.xml (+27 مفتاحًا مرّتين)
  tests:     FileSystemModelTest (+3) · FilePaneModelTest (+6)
GATES: 1 ✓ (0 عوائق)  2 ✓ (exit 0، صحّة نظيفة)  3 ✓ (184/184 عربي)  4 ✓ (لا Scaffold جديد · الاستيراد في كل ملف)  5 ✓ (§5f: ٨ مسارات كاشفة كلها في KernelFlasherScreen قبل الجولة = I-50)  6 ✓ (لا كتابة عتاد · presentation_hw_writes ثابت 26)
BUILD: `:app:compileDebugKotlin` ✅ 1m55s · `:app:testReleaseUnitTest :app:assembleDebug` ✅ **679 اختبارًا 0 فشل** · APK `118,792,784` B · `:app:minifyReleaseWithR8` ✅ · **مُصرَّف ومبنيّ فعلًا في هذه البيئة** (وهذا أثر ثابت: SDK وJDK 17 نُصِّبا هنا)
RESIDUAL RISK: لا جهاز ⇒ RTL/لمس/جذر حقيقي غير مُتحقَّق · شريط التبويبات عند ٦ تبويبات في لوح ضيق لم يُقس · ١٥ مفتاحًا صار يتيمًا في ملف نصوص مدير الملفات (لا يُحذف وحده — انظر NEXT) · التوقيع بالإصدار لا يُختبر محليًّا (KS_PWD)
NEXT: مسح يتيم النصوص على كل اللغات الـ٨٥ (حذفٌ من `values/` وحده يجعل صفوف اللغات «وسائط زائدة» فتفشل بوابة §3) · ثم على جهاز: تبويبات + سطر حالة + RTL · و«قائمة عمليات خلفية» مثل اللقطة لم تُبنَ بعد
```

### ٨. ملاحظة بيئة (لا تخصّني لكن لا بدّ من قولها)

جدول الأرقام المتوقّعة في `REVIEW.md` §2 كان **قديمًا عن هذه الشجرة**: يقول `hardcoded_ui_literals 80`
و`297` ملف Kotlin و`70148` سطرًا، والمقيس هنا **66** و**431** ملفًا و**102,795** سطرًا. حُدِّث الجدول
بالأرقام المقيسة لهذه الشجرة (والفرق ليس عطبًا في الأداة: الشجرة مختلفة عن التي كُتبت عنها الأرقام).

---

## تكملة ٤٢ — `MT-FM`: مواصفة مدير الملفات بأسلوب MT Manager، والحزمة «أ» (النماذج الخالصة) — 2026-09-19

### ١. الطلب

> «ما هذا السوء كيف يمكن استخدامه حتي اجعلها كما اخبرتك كتقليد متناسق لي mt manger ابحث عنه ويكون
> full screen وليس هناك ازر تحكم في خارج مدير الملفات ازلها واجعلها داخل الملفات يعني عند ااضغط مطولا
> في ملف يظهر قائما بها الضغط او النسخ او اللصق وخلافه» — وفي الأسئلة: «الكل واكثر والاهم نكون حتي افضل
> من mt manger نفسه واجمل واقوي» · «درج احسن بكثير منهم» · «بدون تلوث وازحام».

### ٢. ما قُرئ من اللقطات (أداة المستودع، لا تخمين)

روابط `ibb.co` صفحات لا صور ⇒ استُخرج رابط الصورة ونُزّلت الصور، ثم قُرئت بـ`tools/read_image_text.py`.
والمقروء من مرجع MT: تبويبات نوافذ + مسار `/storage/emulated/0/Mihon /` + سطر
`Folders · Files · Disk: 447.04G/479.35G` أعلى، صفوف سطر واحد (اسم · حجم · تاريخ)، `Tools` و`⋮` أسفل،
وقائمة ضغط مطوّل: `Copy → · Move → · Delete · Rename · Compress · Properties · Add to bookmarks`.
و`easyocr` غير مثبّت في هذه البيئة، فالقارئ `tesseract` — والحدّ معلَن في المواصفة §1.1.

### ٣. المواصفة

`docs/ai/mt-file-manager-spec.md` — **١٠ أقسام** و**٢٧ قرارًا** مسجّلًا من **ستّ جولات `ask_user` (٢٥ سؤالًا)**،
والأربعة التي لم يُجب عنها المالك قرّرها المنسّق **صريحةً وقابلة للنقض** في §2.1 (درج · قائمة ⋮ · عمر الحافظة ·
سطر الحالة). وفيها ما يُحذف بالضبط (اللوحان ونموذجهما واختباراتهما)، وبوابات القبول، والمخاطر، وخطة ستّ حزم.

### ٤. الحزمة «أ» — ما أُنجز فعلًا

٨ نماذج خالصة بلا Compose وبلا `R` (١٠٢٩ سطرًا): `FileWindowModel` (نافذتان · سجل · `FileWindowsRule` ·
ترقيم حالة) · `FileClipboardModel` (نسخ/قصّ/لصق) · `FileConflictModel` (استبدال · تخطّي · إعادة تسمية · إلغاء
مع «الكل») · `FileOpenModel` (مجلد/محرّر/خارجي) · `FileTaskModel` (طابور مهام بنسبة **مقيسة** وإلغاء) ·
`FileBookmarkModel` (مفضّلة مرتّبة + سجل مسقوف) · `FileSearchPlan` (حدود البحث وأعلام نقصه) ·
`FilePermissionModel` (rwx ↔ ثماني ومالك:مجموعة). ومعها ٨ ملفات اختبار (١٠٦٤ سطرًا) و**٨٤ اختبارًا جديدًا**.

### ٥. الأدلة المقيسة (أوامر حرفية)

| الأمر | النتيجة |
| --- | --- |
| `:app:testReleaseUnitTest` | **BUILD SUCCESSFUL 2m34s** · **٧٦٣ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** (كان ٦٧٩ ⇒ +٨٤) |
| `python3 tools/code_health.py --assert` | **exit 0 · «صحّة نظيفة»** والدَّين ثابت عند السقف `10 / 29 / 66 / 26` |
| `python3 tools/i18n_coverage.py --assert` | **exit 0 · ٠ عوائق** · ٨٥ لغة · تطابق الأكواد الثلاثة OK |
| البناء | JDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64`) + `~/android-sdk` + ذاكرة Gradle 2.1G — البناء يمرّ في هذه البيئة |

### ٦. أخطاء كشفها التشغيل لا القراءة (تُسجَّل كي لا تُعاد)

① خاصية في `FileTaskModel` بلا `get()` ⇒ فشل تصريف حقيقي كشفه `:app:compileReleaseKotlin`.
② مقارنة `Long` بـ`Int` في ثلاثة أسطر اختبار.
③ ثلاثة توقّعات اختبار خاطئة عندي؛ أخطرها أنني توقّعت إدراج لاحقة إعادة التسمية قبل الامتداد (`a (1).txt`)
بينما `FileOpGuard.uniqueName` يلحقها بالاسم كاملًا (`a.txt (1)`). **صُحّحت توقّعاتي ولم يُغيَّر الحرس** —
الحرس القائم هو المرجع، لا شهية كاتب الاختبار.

### ٧. حدّان يُعلنان

1. **لا واجهة بعد.** الحزمة «أ» نماذج فقط: الشاشة على الجهاز لم تتغيّر بملّيمتر، ولا APK جديد بُني فيها.
2. **لا جهاز.** لا لمس ولا RTL ولا جذر — ولا يُدَّعى أيّها.

### ٨. التسليم بقالب `VALIDATION.md` §8

```
TASK: MT-FM الحزمة أ (medium) — نماذج مدير الملفات الجديدة بأسلوب MT Manager
FILES:
  added (main):  ui/util/{FileWindowModel 232 · FileClipboardModel 89 · FileConflictModel 109 · FileOpenModel 76
                 · FileTaskModel 109 · FileBookmarkModel 107 · FileSearchPlan 112 · FilePermissionModel 195} = ١٠٢٩ سطرًا
  added (tests): app/src/test/java/nd/max/ui/util/{FileWindowModelTest · FileClipboardModelTest · FileConflictModelTest
                 · FileOpenModelTest → FileOpenPlanTest · FileTaskModelTest · FileBookmarkModelTest · FileSearchPlanTest
                 · FilePermissionModelTest} = ١٠٦٤ سطرًا · ٨٤ اختبارًا
  docs:          docs/ai/mt-file-manager-spec.md (مواصفة · §9.1 سجل تقدّم) · NEXT_TASK (MT-FM · MT-FM-02) · هذا السجل
GATES: 1 ✓ (٠ عوائق)  2 ✓ (exit 0 · صحّة نظيفة · الدَّين ثابت)  3 ✓ (لا نصوص جديدة)  4 ✓ (لا Scaffold ولا كتابة عتاد؛ الاستثناء المنصوص لـui/util)
BUILD: `:app:testReleaseUnitTest` ✅ 2m34s · ٧٦٣ اختبارًا 0 فشل 0 خطأ 0 مُتخطّى · **مُصرَّف ومُشغَّل فعلًا في هذه البيئة**
RESIDUAL RISK: لا واجهة ولا جهاز · نماذج لم تُوصل بالشاشة بعد (التوصيل في الحزمة «د») · سلوك الحفظ/الاستعادة لم يُجرَّب بين جلسات حقيقية
NEXT: الحزمة «ب»: المحرّك — zip داخل التطبيق · بحث عميق · chmod/chown مع تحقّق · تعليق r/w مقيس · معلومات APK
```

---

## تكملة ٤٣ — `MT-FM/ب`: المحرّك (zip · بحث عميق · صلاحيات · r/w · APK) — 2026-09-19

### ١. ما أُنجز

٥ ملفات جديدة في `ui/util/` (٨٠١ سطرًا) — كلها بلا Compose وبلا `R`:

| الملف | الأسطر | ما يفعله | أهمّ قرار فيه |
| --- | --- | --- | --- |
| `FileArchiveEngine` | ٣٢٩ | ضغط وفكّ **zip** بالمكتبة المعيارية | فحص `zip-slip` على **كل** المداخل **قبل** أول بايت تُكتب؛ ومجلد فارغ يُكتب مدخلًا؛ والأرشيف لا يدخل في نفسه |
| `FileSearchEngine` | ١٠٥ | كشف البحث العميق بالاسم | القارئ والساعة والإلغاء **تُحقن** ⇒ يُقاس في JVM بشجرة مصنوعة، والحدود تُعلن في النتيجة |
| `FilePermissionOps` | ١٢٩ | `chmod`/`chown` بالحكم الثلاثي | بناء الأمر يُرفض قبل التنفيذ (رقم غير صالح/مالك غير صالح)، والأثر يُقاس بإعادة `stat` |
| `RootMount` | ١١٨ | حالة r/w وتعليقها | الحالة **تُقاس** من `/proc/mounts` بعد الأمر لا من نجاحه؛ وأطول مطابقة تفوز (`/system` قبل `/`) |
| `ApkInspector` | ١٢٠ | معلومات APK وبصمات MD5/SHA-1/SHA-256 | ما لم يُقرأ يبقى `null`؛ والبصمات الثلاث من نداء واحد |

وتوسيع أربعة ملفات قائمة: `FileSystemModel` (`CreateFile` · `ChangePermissions` · `ChangeOwner` وحرسهما —
٥٨٧ سطرًا) · `FileSystemEngine` (`createFile` · ضغط zip · فكّ zip · ٢٧٨) · `FileOperationRunner`
(ثلاثة فروع جديدة — ٥٩) · `FileActionModel` (الأرشيف: **zip افتراضيًّا** و`tar.gz` خيارًا معلنًا)
و`FileSearchPlan` (علم `cancelled` صار يكسر «الكمال» كما يجب).

### ٢. الأدلة المقيسة

| الأمر | النتيجة |
| --- | --- |
| `:app:testReleaseUnitTest` | **BUILD SUCCESSFUL 2m27s** · **٨٠٢ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** (كان ٧٦٣ ⇒ **+٣٩**) |
| `python3 tools/code_health.py --assert` | **exit 0** · الدَّين ثابت `10 / 29 / 66 / 26` · لا ملف تجاوز ١٠٠٠ سطر |
| `python3 tools/i18n_coverage.py --assert` | **exit 0 · ٠ عوائق** |

واختبارات المحرّك **تُنفّذ عملًا حقيقيًّا** لا محاكاة: `FileArchiveEngineTest` تنشئ شجرة في مجلد
مؤقّت، تضغطها، تفكّها، وتقارن المحتوى؛ وتُصنع فيها أرشيف خبيث فيه `../evil.txt` ويُثبت أنه **لم يُكتب**
ملف خارج الوجهة ولا نصف فكّ.

### ٣. أخطاء كشفها التشغيل لا القراءة

① `ZipInputStream` استُعمل بلا استيراد ⇒ فشل تصريف كشفه `:app:compileReleaseKotlin`.
② توقّعت أن `chmodCommand` يكتب `0644` بينما التطبيع المعلن يُنتج `644` ⇒ **صُحّح الكود** (تطبيع
واحد عبر `FilePermissionRules.parseOctal` لا تطبيعان) لا الاختبار.
③ توقّعت أن `remountCommand` يُبقي الشرطة الأخيرة في `/system/` ⇒ صُحّح التوقّع (المسار يُطبَّع).

### ٤. حدود تُعلن

1. **zip لا يقرأ ما لا تراه عملية التطبيق**: مسار جذري محجوب يُرفض بـ`UnreadableSource` **معلنًا**
   ولا يُتجاوز بصمت، ولا نصف أرشيف يُترك على القرص (يُزال عند الفشل).
2. **تعليق r/w قد يفشل على أجهزة A/B أو overlayfs** — والنتيجة تقول «نُفِّذ ولم يُتحقّق» لا «نجح».
3. **`ApkInspector` لم يُوصَّل بواجهة بعد** (يحتاج `PackageManager` من الشاشة — الحزمة «د»).
4. **لا جهاز**: لا قياس لمس ولا أداء على شجرة `/data` حقيقية، ولا تجربة zip على ملف كبير فعلي.

### ٥. التسليم بقالب `VALIDATION.md` §8

```
TASK: MT-FM الحزمة ب (large) — محرّك مدير الملفات: zip · بحث عميق · صلاحيات · r/w · APK
FILES:
  added (main):  ui/util/{FileArchiveEngine 329 · FileSearchEngine 105 · FilePermissionOps 129
                 · RootMount 118 · ApkInspector 120} = ٨٠١ سطرًا
  modified:      ui/util/{FileSystemModel 587 (+CreateFile/ChangePermissions/ChangeOwner وحرسها)
                 · FileSystemEngine 278 (+createFile · ضغط/فكّ zip) · FileOperationRunner 59 (+٣ فروع)
                 · FileActionModel (zip افتراضيًّا · tarNameFor · isZip) · FileSearchPlan (+cancelled)}
  added (tests): ui/util/{FileArchiveEngineTest 203 · FileSearchEngineTest 156 · FilePermissionOpsTest 123
                 · RootMountTest 94 · ApkDigestsTest 68} = ٦٤٤ سطرًا · ٣٩ اختبارًا جديدًا
  docs:          docs/ai/mt-file-manager-spec.md (§9.1) · NEXT_TASK · هذا السجل
GATES: 1 ✓ (٠ عوائق)  2 ✓ (exit 0)  3 ✓ (لا نصوص جديدة)  4 ✓ (لا Scaffold ولا كتابة عتاد جديدة؛ الكتابة في ui/util المنصوص عليه)
BUILD: `:app:testReleaseUnitTest` ✅ 2m27s · ٨٠٢ اختبارًا 0 فشل 0 خطأ 0 مُتخطّى · **مُصرَّف ومُشغَّل فعلًا في هذه البيئة**
RESIDUAL RISK: zip على مسار جذري يحتاج جذرًا (مُعلن لا مُتجاوز) · r/w غير مُجرَّب على تقسيمات A/B · APK غير موصول بالواجهة · لا قياس على جهاز
NEXT: الحزمة «ج» (بدائيات التصميم: درج · قائمة سياق · حوار تبويبات · شريط مهام) ثم «د» (الشاشة وحذف اللوحين)
```

---

## تكملة ٤٤ — `MT-FM` يُكمَل: الحزم ج–و (البدائيات · الشاشة · المحرّر · البوابات) — 2026-09-19

**المدخل:** طلب المالك «اكمل» بعد أن سُلّمت الحزمة «ب» (المحرّك). والمواصفة المُلزمة
`docs/ai/mt-file-manager-spec.md` هي التي حدّدت **ما** يُبنى (٢٧ قرار مالك)، وهذه التكملة تسجّل **ما وقع**.

### ١. ما بُني — بدائيات (`ui/design/`)

| الملف | الأسطر | لماذا وُجد |
| --- | --- | --- |
| `MaxDrawer` | ١٥٧ | درج ينزلق فوق الشاشة نفسها (لا مسار تنقّل ثانٍ) مع ساتر يُلمس للإغلاق وزرّ إغلاق مُسمّى |
| `MaxContextMenu` | ١٧٠ | قائمة تُفتح **عند الإصبع** لا عند زرّ: تُحصر داخل الشاشة، وفي الثلث السفلي تُفتح **فوق** الإصبع |
| `MaxTabbedDialog` | ١٣٣ | نافذة بتبويبات (خصائص المدخل) بشرائح تُقرأ مع ترجمات طويلة، وجسدها مسقوف ويمرّ |
| `MaxProgressStrip` | ١٣٤ | سطر مهمة بنسبة **أو** شريط غير محدَّد حين لا تُقاس، مع زرّ إلغاء مُسمّى |

### ٢. ما حُذف (بقرار المالك: اللوحان لا يعودان)

`ui/component/FileManagerPanels.kt` · `ui/component/FilePaneColumn.kt` · `ui/util/FilePaneModel.kt`
واختباره `FilePaneModelTest.kt` — أي **اللوحان ونموذجهما** بأربعة أشرطة لكل لوح. وأُبدلت بـ:

- `ui/util/FileWindowModel.kt` (نافذة · سجل رجوع · تسمية · رئيسي) من الحزمة «أ»،

- `FileClipboardModel` + `FileConflictModel` (لصق وقرار تعارض على الدفعة كاملة)،

- `FileTaskModel` (مهام بنسبة قابلة للغياب)،

- و`FileTargets` + `FileFormat.date` + `FileOperation.WriteText` في `FileSystemModel`.

### ٣. ما بُني — الشاشة (٦ ملفات · ٢٢٧٧ سطرًا)

| الملف | الأسطر | الدور |
| --- | --- | --- |
| `FileManagerScreen.kt` | ٩٧٩ | الحالة · القراءة · العمليات · التركيب (وكان ١٣٢٩ سطرًا فشُقّ على أربعة ملفات) |
| `FileManagerPanes.kt` | ٢٦٤ | نافذة واحدة أو نافذتان جنبًا إلى جنب (قرار `FileWindowsRule` يُمرَّر جاهزًا) |
| `FileManagerCommands.kt` | ٢٣١ | قوائم: الشاشة · النافذة · المدخل |
| `FileManagerDialogs.kt` | ٢٩٠ | النوافذ العشر + عقد النداءات في كائن واحد |
| `FileManagerActions.kt` | ٣١٤ | المواقع السريعة · ترجمة الإجراء إلى طلب · أحوال القراءة · نصوص الرفض |
| `FileManagerState.kt` | ١٩٩ | `WindowView` · `EditorState` · `SearchState` · `PropertiesState` · `ConflictRequest` |

ومكوّنات العرض: `FileWindowChrome` ٢٨٨ (تبويبا النافذة · شريط المسار · سطر الحالة) · `FileEntryList` ٢١٥
(سطر واحد لكل مدخل + موضع الإصبع مقيس بـ`positionInRoot`) · `FileBottomBars` ٣٣٦ (أدوات · تحديد · حافظة ·
مهام) · `FileDrawerContent` ٣٢٣ · `FilePropertiesDialog` ٤٩٣ · `FileEditorDialog` ٢٧٨ · `FileSearchDialog` ١٨٠.

### ٤. عقود جديدة في النموذج والمحرّك (صغيرة ومُختبرة)

- `FileOperation.WriteText` + `FileOpRequest.content` — حفظ المحرّر يمرّ بالحرس نفسه.
- `FileOpRequest.renamed` + `FileTargets` — اسم العنصر في الوجهة يُحسب في النموذج، فيكتب المحرّك
  **بالاسم الذي وُعد به المستخدم** لا باسم قديم.
- `FileSystemEngine.writeText` — كتابة عبر `base64 -d` (المحتوى لا يُقتبس في أمر) وإثبات بالحجم بالبايت.
- `FileSystemEngine.nodeBytes` — قياس حجم عقدة لتقدّم المهمة (استطلاع كل ٤٠٠م.س).
- `RootMount.granted()` — سؤال الجذر انتقل من الشاشة إلى طبقة الأدوات (ADR-11: لا shell من العرض).
- `res/xml/file_paths.xml` — `external-path` ليُسلَّم ملف التخزين المشترك لتطبيق آخر (فتح بالتطبيق الافتراضي).

### ٥. أخطاء كشفها **التشغيل** لا القراءة (تُسجَّل كي لا تُعاد)

1. ملف الشاشة بلغ **١٣٢٩ سطرًا** ⇒ بوابة الصحّة أوقفت التسليم (`oversized_files: 10 -> 11`) ⇒ شُقّ إلى ٤.
2. `MAX_VALUE_UNAVAILABLE` من `ui.design` لا `ui.util` (٣ ملفات).
3. `FileKind.CharDevice` لا `CharacterDevice`.
4. `MaxSpace.hairline` لا `MaxSize.hairline` (المقاسات في `MaxSpace`).
5. دالّة محلّية تستدعي `stringResource` بلا `@Composable` ⇒ صُحّحت بالوسم.
6. دالّة محلّية استُعملت **قبل** تعريفها (`openEntry` قبل `openEditor`/`handOff`) — قاعدة Kotlin.
7. `MaxCondition?` يُمرَّر حيث `MaxCondition` مطلوب ⇒ الحالة تُحسب في متغيّر أولًا.
8. فاصلة عليا في نصّ XML (`this window's home`) ⇒ فشل `mergeReleaseResources`؛ والصياغة أُعيدت بلا فاصلة.

### ٦. حدود تُعلن

1. **لا جهاز**: لا لمس ولا RTL ولا جذر حقيقي، والعرض المزدوج عند ٦٠٠dp لم يُقس على لوح.
2. **المفضّلة والسجل في ذاكرة الجلسة** — لا مخزن على القرص بعد (مهمة `MT-FM-04`).
3. **الإلغاء بين العناصر** لا داخل العنصر: `cp` واحدة لا تُقطع، فيُقطع بينها وبين التالية، والنصّ يقول ذلك.
4. **تسليم خارجي للتخزين المشترك وحده**: غير المشترك يُرفض بنصّه بدل انهيار `FileUriExposed`.
5. **٢٦ مفتاحًا يتيمًا** في `max_files_strings.xml` لم تُحذف: حذفها يجب أن يعمّ الـ٨٥ لغة (مهمة `MT-FM-03`).

### ٧. التسليم بقالب `VALIDATION.md` §8

```
TASK: MT-FM الحزم ج–و (large) — بدائيات التصميم · الشاشة بأسلوب MT · المحرّر والنوافذ · البوابات
FILES:
  added (design):   MaxDrawer 157 · MaxContextMenu 170 · MaxTabbedDialog 133 · MaxProgressStrip 134
  added (component): FileWindowChrome 288 · FileEntryList 215 · FileBottomBars 336 · FileDrawerContent 323
                    · FilePropertiesDialog 493 · FileEditorDialog 278 · FileSearchDialog 180
  added (subscreens): FileManagerPanes 264 · FileManagerCommands 231 · FileManagerDialogs 290
                    · FileManagerState 199  (وFileManagerScreen 979 و FileManagerActions 314 أُعيد كتابتهما)
  removed:          ui/component/FileManagerPanels.kt · ui/component/FilePaneColumn.kt
                    · ui/util/FilePaneModel.kt · ui/util/FilePaneModelTest.kt
  modified (model): FileSystemModel (+WriteText · content · renamed · FileTargets · FileFormat.date)
                    · FileSystemEngine (+writeText · nodeBytes · transfer بالاسم البديل)
                    · FileOperationRunner (+فرع WriteText · تمرير renamed) · RootMount (+granted)
  modified (res):   values+values-ar/max_files_strings.xml (+103 مفتاحًا لكل لغة · وإعادة كتابة نصّ الشرح)
                    · res/xml/file_paths.xml (+external-path)
  modified (tests): FileSystemModelTest (+٢) · MaxViewMenuContractTest (اختباران أُعيد كتابتهما للبنية الجديدة)
GATES: 1 ✓ (٠ عوائق)  2 ✓ (exit 0 · الدَّين ثابت 10/29/66/26 · لا ملف فوق ١٠٠٠)  3 ✓ (ar=٢٨٧ مفتاحًا · en=٢٨٧)
       4 ✓ (لا Scaffold جديد · لا كتابة عتاد من العرض · ui/util هو مسار الكتابة المنصوص عليه)
BUILD: `:app:compileDebugKotlin` ✅ 2m1s (صفر خطأ في ملفات الجولة) · `:app:testReleaseUnitTest :app:assembleDebug
       :app:minifyReleaseWithR8` ✅ 8m16s · **٧٧٨ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** · APK ‎121,069,736‎ بايت
RESIDUAL RISK: لا جهاز · العرض المزدوج غير مقيس · المفضّلة/السجل بلا تخزين · الإلغاء بين العناصر · تسليم خارجي
       للتخزين المشترك وحده · ٢٦ مفتاحًا يتيمًا بانتظار مسح الـ٨٥ لغة
NEXT: `MT-FM-02` (قياس على جهاز) · `MT-FM-03` (مسح اليتامى على ٨٥ لغة) · `MT-FM-04` (حفظ المفضّلة والسجل)
```

**وسؤال المؤلّف لنفسه في هذه الجولة:** هل ما بُني يستحقّ وصف «أقوى من MT»؟ ما **يُقاس** الآن: حافظة بحوار
تعارض على الدفعة كاملة، ومهام بنسبة مقيسة أو معلنة الجهل، ومحرّر بحفظ مُثبت، وخصائص بأربعة تبويبات، وبحث
بأعلام صدق، وتعليق r/w مقيس. وما **لا يُقاس** هنا: السلاسة على عتاد حقيقي — ولذلك `MT-FM-02` ليست ترفًا.

---

## تكملة ٤٥ — `MT-FM-03` + `MT-FM-04`: النصوص اليتيمة تُمسح، والمفضّلة والسجل يبقيان — 2026-09-19

**المدخل:** طلب المالك «اكمل كل شيئ متبقي مرة واحدة». والمتبقي لم يُخمَّن: قُرئ من `NEXT_TASK.md`
(‏`MT-FM-03` و`MT-FM-04`)، والباقي كلّه إمّا يحتاج **جهازًا** (`MT-FM-02` · `FM-04`) أو يعتمد على
`core/hardware`/`core/maxai` فيحتاج حكم سلامة من Luna (`NT-13` · `NT-16`) أو يعدّل مسار الإصدار (`NT-14`).

### ١. `MT-FM-03` — مسح النصوص اليتيمة (والتصحيح قبل الحذف)

**القياس أولًا، لا الرقم المنقول:** الملاحظة القديمة قالت «٢٦ مفتاحًا على ٨٥ لغة». والمسح الشامل —
**٥٤٢** ملف مصدر (`.kt` · `.java` · `.xml`، باستثناء `build/` و`.gradle/` والمجلدات اللغوية) وبحث
عن **اسم المفتاح نفسه** في كل ملف (فحص أوسع من أنماط النداء، لأن كل صيغة نداء تحمل الاسم) — قال
**٧٥** مفتاحًا بصفر ظهور. وبعد الحذف أُعيد المسح نفسه: **٢١٢ مفتاحًا · ٠ يتيم** (فالمسح مكتمل لا جزئي).
والمفاتيح لم تكن في `values-<locale>` أصلًا، بل في `values/` و`values-ar/` فقط ⇒ فالكلفة على الـ٨٥ لغة
**صفر**، لا «مسحٌ على ٨٥ لغة» كما كان مكتوبًا.

**وفحص الأمان قبل الحذف لا بعده:** بحث في المستودع كله عن أي قراءة ديناميكية للموارد
(`getIdentifier` · `getStringByName` · `resourceIdByName`) ⇒ **صفر**. لو وُجد واحد لكان الحذف عطبًا
صامتًا لا يظهر إلا على جهاز.

**ثم الحذف:** ٧٥ مفتاحًا من `values/max_files_strings.xml` **و**`values-ar/max_files_strings.xml` في
جولة واحدة ⇒ **٢٨٧ ⇒ ٢١٢** في اللغتين (تماثل تام). وأُزيلت رؤوس الأقسام التي لم يبق تحتها مفتاح،
**وصُحّح خطأ في الأداة نفسها:** المُنظّف الأول حذف رأس قسم حيّ («جولة MT-FM») لأنني أدخلت تعليقات أقسام
فرعية بعده — أُعيد الرأس وتحفّظت الأداة على أي رأس يتبعه محتوى.

**الدليل المقيس:** الملفان يُفكّان بـ`xml.etree` بلا خطأ · `:app:processReleaseResources` ✅ ·
**٢١٢ مفتاحًا في `values/` = ٢١٢ في `values-ar/`** والفرق في الاتجاهين **مجموعة فارغة** صريحة
(لا «متقارب» ولا «متماثل ظاهريًّا») · `code_health --assert` exit 0 · `i18n_coverage --assert` **٠ عوائق**.

### ٢. `MT-FM-04` — المفضّلة والسجل على القرص

**القرار المعماري:** لا `DataStore` ولا `SharedPreferences` ولا مكتبة جديدة. النموذجان (`FileBookmark` ·
`HistoryEntry`) قائمان ومُختبران، فالمطلوب **ترميز أمين**؛ فسطرٌ لكل عنصر بفاصل محجوز `\u001F` يُقرأ
بالعين عند الحاجة، ويقارن بالشفرة لا بالمكتبة. وهذا يتبع القاعدة القائمة: **لا تُضاف تبعية لما يُبنى
بـ٢٠ سطرًا**، و`ui/util/` هي طبقة الإدخال/الإخراج المُعتمدة (`presentation_hw_writes` لا تشملها).

| الملف | الأسطر | الجوهر |
| --- | --- | --- |
| `ui/util/FileStore.kt` | ١٤٥ | `FileBookmarkCodec` (سقف ٥٠ · فهم صيغة بفاصل وصيغة أقدم بمسار وحده · دمج التكرار) + `FileHistoryCodec` (الطابع الزمني **شرط** لا زينة) + `FileStore` (قراءة/كتابة لا تُسقط الشاشة) |
| `ui/subscreens/FileManagerPersistence.kt` | ٨٠ | الربط: تحميل مرّة عند التركيب، وحفظ **عند كل تغيير** لا عند الإغلاق |
| `test/.../FileStoreTest.kt` | ١٥٨ | **١٣ اختبارًا** على ملفات حقيقية في مجلد مؤقّت |

**وأخطر ما كشفه التشغيل — عطب حقيقي لا توقّع خاطئ:** كتبت اختبارًا يرمي **بايتات ثنائية**
(`00 01 02 7F`) في ملف المفضّلة ويتوقّع قائمة فارغة. ففشل. والسبب: `trim()` في Kotlin تُنزع **المسافات**
وحدها ولا ترى محارف التحكّم، فمرّت البايتات «مسارًا» بطول أربعة محارف غير مرئية. وهذا ليس تفصيلًا
نظريًّا: ملف مبتور أو مكتوب بيد كان سيُنتج **عنصرًا في المفضّلة لا يمكن فتحه ولا حذفه بالمعنى**.
**فأُصلح الكود لا الاختبار** — `isVisiblePath`: سطر لا **حرف مرئيّ** فيه يُسقط نفسه، في الترميزين.

**وفشل ثانٍ أولًا:** مفتاح اختبار مكرّر (خطأ إملائي عندي) — صُحّح الاختبار.

### ٣. عاصفة التحقق — الأرقام كما خرجت لا كما توقّعت

| البوابة | النتيجة |
| --- | --- |
| `:app:testReleaseUnitTest :app:assembleDebug` | **BUILD SUCCESSFUL 5m18s** · **٧٩١ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** (كان ٧٧٨ ⇒ **+١٣**) |
| APK (debug) | ‎**121,032,920**‎ بايت |
| `code_health.py --assert` | **exit 0 «صحّة نظيفة»** والدَّين ثابت عند السقف القائم (`10 / 29 / 66 / 26`) |
| `i18n_coverage.py --assert` | **exit 0 · ٠ عوائق** · ٨٥ لغة · تطابق الأكواد الثلاثة OK |
| `repo_audit.py` | **PROBLEMS: 0** (٣٤٥ ملف Kotlin · ٢٥٤٤ مرجعًا · ٣٢٠٩ نص أساسي) |
| حدّ الحجم | `FileManagerScreen.kt` **٩٨٠** سطرًا (تحت ١٠٠٠) — وحُفظ ذلك بنقل الربط إلى ملف مستقلّ بعد أن بلغ ١٠٠٣ |

**ولا ادّعاء بأرقام محفوظة:** كل رقم أعلاه استُخرج من `build/test-results/*.xml` ومن حجم ملف APK
على القرص في هذه الجولة، لا من ذاكرة جولة سابقة. وقد صحّحت في الطريق رقمًا كتبته أوّلًا عن حجم
`FileStore.kt` (كتبت ٦٤٣ وهو **١٤٥**) لأنه كان مجموع ملفات لا ملف واحدًا. وكذلك صحّحت **ثلاثة** أرقام
أخرى كُتبت أوّلًا في `NEXT_TASK`/المواصفة قبل قياسها: «٦٨٨ ملف مصدر» والصواب **٥٤٢** · «١٠٠٦ نص =
١٠٠٦ نص» والصواب **٢١٢ = ٢١٢** · و«١٣ منها لهذا المخزن» في وصف الاختبارات (صوابها **١٣ اختبارًا** كلها
لهذا المخزن). والقاعدة التي أنقذت التسليم: **لا رقم في وثيقة قبل أن يخرجه أمرٌ في نفس الجولة**.

### ٤. ما لا يُدَّعى (حدود هذه الجولة)

- **لا جهاز:** كتابة الملف على تخزين حقيقي تحت ضغط مساحة أو صلاحيات أو sandbox لم تُقَس؛ والقياس هنا
  JVM على نظام ملفات عادي (وهو أقوى من لا شيء وأضعف من جهاز).
- **لا RTL ولا لمس** على الشاشة الجديدة — `MT-FM-02` و`FM-04` تنتظران جهاز المالك.
- **zip** على مسار جذري محجوب يُرفض صراحةً (`UnreadableSource`) ولم يُختبر مع جذر حقيقي.
- **تعليق r/w** قد يفشل على A/B أو overlayfs — وحينها الحكم المعلن «نُفِّذ ولم يُتحقّق»، لا «نجح».

### ٥. التقرير بقالب `VALIDATION.md` §8

**TASK:** `MT-FM-03` (مسح اليتامى) + `MT-FM-04` (حفظ المفضّلة والسجل) — إكمال ما تبقّى بعد الحزم «أ–و».
**FILES:** `ui/util/FileStore.kt` (جديد) · `ui/subscreens/FileManagerPersistence.kt` (جديد) ·
`ui/subscreens/FileManagerScreen.kt` · `test/.../FileStoreTest.kt` (جديد) ·
`res/values/max_files_strings.xml` · `res/values-ar/max_files_strings.xml` · `NEXT_TASK.md` ·
`mt-file-manager-spec.md` · `HANDOFF.md`.
**GATES:** §1 §2 §3 §3.1 §4 §5 §6 §7 — كلها مرّت (الأرقام في §٣ أعلاه، والأوامر الحرفية في
`mt-file-manager-spec.md` §9.1).
**BUILD:** `:app:testReleaseUnitTest :app:assembleDebug` = **BUILD SUCCESSFUL 5m18s**.
**RESIDUAL RISK:** التخزين على جهاز حقيقي غير مُقاس · لا مراجعة من Luna على طبقة `ui/util` الجديدة
(لا تلمس عتادًا ولا SELinux ولا الإقلاع، فهي دون عتبة §2 (2)) · وأي عطب بصري لا يُرى بلا جهاز.
**NEXT:** `MT-FM-02` و`FM-04` على الجهاز · `NT-16` (سلامة `core/maxai` من Luna) · ولا بند تنفيذ آخر
قابل للإنجاز بلا جهاز من نطاق مدير الملفات.

---

## تكملة ٤٦ — `HOME-01`: أمر «لا بناء تلقائي» + الشاشة الرئيسية (تفاصيل مفتوحة · أنوية بعناقيد · طيف لا يبدأ من الصفر · بلا تكرار) — 2026-09-20

### ٠. أولًا: تغيير في اللوائح لا في الكود

أمر المالك في هذه الجولة: **«لا تقم كقاعدة في الذاكرة بالبناء إلا إذا أخبرتك؛ عند انتهاء التعديلات يمكنك
التأكد منه بكافة الطرق الأخرى — مثل التحقق من توازن الأقواس — أو بناء الشاشة فقط التي تم تعديلها.»**

وهذا **تعارض صريح** مع بند في `AGENTS.md` §0 كان يُقرأ «أقصى إمكانية = أكثر تشغيلًا للبناء»، فلم يُمرّ
صامتًا: أُضيف **§0.1** يقيّد البناء، وعُدّل بند §0 ليشير إليه، وقيل في §5 صراحةً «عند الطلب، لا كعادة».
ولو تُرك التعارض بلا إعلان لصار كل وكيل قادم يبني ويقول إنه «يتّبع §0».

وأداة التحقق البديلة **ليست عَتَبة شكلية**: `tools/kt_balance.py` — ٣٣٤ سطرًا، تمشي على Kotlin حرفًا
بمكدّس سياقات (نصوص عادية · نصوص خام بإشارات زائدة كما يفهمها المُصرّف · `${...}` كودًا داخل النص ·
تعليقات كتل متداخلة · محارف) وتُحلّل XML بمحلّل المكتبة القياسية. و`--self-test` هو ما يمنعها أن تكون
طمّاعة تمرّ على كل شيء: **١٤ حالة معروفة · ٠ إخفاق**، منها ثلاثة أنواع إنذار كاذب كانت تُسقط جولتها
الأولى على كود سليم فعلًا (`"setprop '$name' '${value.replace("'", "")}'"` في `AppMonitor.kt`،
و`@Query("""SELECT * FROM "update"""")` في `updateDao`)، فأُصلحت الأداة ولم يُعدَّل الكود السليم.

### ١. الشاشة الرئيسية — أربعة طلبات، أُنجزت

1. **كرت التفاصيل لم يبقَ مطويًّا.** كان `NeuralExpandable` بحالة `detailsOpen` افتراضية `false`، وحُذفت
   الحالة والطيّة معًا. والسبب المعلن للطيّ («تبقى أول شاشة هادئة») صحيح لكن ثمنه أن ما لا يُقرأ لا يُقرأ:
   معلومة تحتاج نقرة، وفي آخر الصفحة. فالحجم يُدار بالترتيب لا بالإخفاء.
2. **مربّعات أنوية CPU أُعيد تصميمها ببنية الشريحة لا بالشبكة.** تُجمع الأنوية بوسوم عناقيدها القادمة من
   نواة النظام (`SILVER` · `GOLD` · `PRIME`) وتُلوَّن بلون العنقود، ومع كل عنقود **متوسّط تردّد المتّصل
   وحده**. فعُرفت «أي كتلة تعمل الآن» من الصورة قبل أي رقم — وشبكة ٤×٢ كانت تقول العدد وتُخفي أن الصغيرة
   والكبيرة ليستا شيئًا واحدًا. والنواة المُطفأة تفقد لونها وتُكتب `OFF`.
3. **الطيف لا يبدأ من الصفر ولا يبدو ناقصًا في بدايته.** القاعدة الجديدة في `SpectrumModel`:
   **الإطار بطول ثابت دائمًا** (٢٦ فتحة)، والفتحة بلا عيّنة تُرسم مسارًا فارغًا **بنفس الارتفاع الكامل**،
   والتعبئة تجيء من اليمين (الأحدث في الطرف). وكان قبلها `values.takeLast(maxBars)` يُقسّم العرض على
   عدد العيّنات: ثلاث عيّنات = ثلاثة أشرطة عريضة تشغل الشاشة ثم تضيق كلما كبر السجل — وهذا ما كان يبدو
   عطبًا. وأُزيلت أنيميشن `appear` الواحد التي كانت تُعيد «نموّ الرسم من الصفر» كلما دخل التركيب من
   جديد (تمرير بعيدًا ثم رجوع، أو عودة من الخلفية)، وصارت الأشرطة تُحرَّك **لكل فتحة نحو قيمتها**.
4. **ولا معلومة تتكرر في الشاشة.** أُزيل بالقياس لا بالتقدير: عنوان «محرّك الأداء متصل/غير متصل» (كان
   يقول مع ما تقوله شرطة `نشط` بجانبه) · مربّع «الآن» في الطيف (كان نسخةً من قيمة مربّع CPU فوقه تمامًا)
   · شرطة «الحلقة الحيّة» في رأس الطيف (كانت الثانية، والأولى في كرت القصة) · و`sparkline` المربّعين
   (كان يرسم السلسلة نفسها التي يرسمها الطيف أسفله) · وتسمية «البطارية» في الموضعين (النسبة أعلاه،
   **والجهد** هنا باسمه المترجم `charging_voltage`، وحالة الشحن صارت نصًّا مترجمًا بدل السلسلة الإنجليزية
   الخام `Discharging` التي كان الجهاز يعيدها، وهي في العربية كانت تُعرض بالإنجليزية).

### ٢. ما كشفه التشغيل لا القراءة

- **تعارض داخلي حقيقي في معنى القيمة غير الرقمية:** `LoadHistoryCodec` كان يُسقط `NaN` ويعدّها «غياب
  قراءة»، و`Spectrum` كان يطويها إلى `0f` ويعدّها «حمْل صفر». الشكل الذي يُحفظ كان يختلف عن الشكل الذي
  يُرسم — أي «قُيس فكان هادئًا» في مقابل «لم يُقس». صار المعنى واحدًا في الطبقتين: `null` = فتحة فارغة،
  والتحقق لا يكون إلا لرقم منتهٍ داخل ٠–١٠٠.
- **اختباران سقطا في أول تشغيل** (١٥ من ١٧ نجحت): أحدهما بيانات اختبار خاطئة عندي (قائمة `gpu` أطول من
  قائمة `cpu` فقارنت موضعًا فارغًا)، والآخر هو التعارض أعلاه. صحّحتُ **الكود** للثاني و**البيانات** للأول،
  ولم أُخفّف أي توقّع.

### ٣. الأدلة المقيسة (كلها من أوامر هذه الجولة)

| البوابة | الأمر | النتيجة |
| --- | --- | --- |
| تصريف الشاشة المعدّلة | `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` | **BUILD SUCCESSFUL 2m49s** · **٠ تحذير في ملفات هذه الجولة** |
| اختبارات النماذج الجديدة | `:app:testDebugUnitTest --tests "nd.max.ui.util.SpectrumModelTest" --tests "nd.max.ui.util.LoadHistoryTest"` | **BUILD SUCCESSFUL 2m37s** · **١٧ اختبارًا · ٠ فشل** (أول تشغيل: ٢ فشل فأُصلحا) |
| توازن البنية | `tools/kt_balance.py --assert` · `--self-test` | **657 ملفًا · 0 عوائق** · **14/14** |
| صحّة/دَين | `tools/code_health.py --assert` | **exit 0 «الحصيلة: صحّة نظيفة»** · الدَّين `10/29/66/26` (السقف، لم يصعد) |
| نصوص ووسائط | `tools/i18n_coverage.py --assert` | **exit 0 · ٠ عوائق** · `ar 3203/3203` |
| نظافة عامة | `repo_audit.py` · `git diff --check` | `PROBLEMS: 0` · بلا مخرجات |

**وحُذفت أربعة مفاتيح يتيمة** من `values/` و`values-ar/` معًا (سقطت بلا نداء بعد إزالة التكرار):
`home_engine_ready` · `home_engine_offline` · `home_stat_now` · `max_home_waiting_samples` —
وأساس اللغات `3207 ⇒ 3203` واللغتان متناظرتان، والبوابة خضراء بعد الحذف.

### ٤. الملفات

| الملف | الأسطر | الحالة |
| --- | --- | --- |
| `tools/kt_balance.py` | 334 | **جديد** — بوابة بنية بلا مُصرّف + قياس ذاتي |
| `ui/util/SpectrumModel.kt` | 83 | **جديد** — هندسة الطيف (نماذج خالصة، تُقاس في JVM) |
| `ui/util/LoadHistory.kt` | 117 | **جديد** — `LoadSample` · ترميز سطري · مخزن ملف |
| `ui/mainscreens/HomeFormat.kt` | 72 | **جديد** — تنسيق الأرقام في مكان واحد |
| `ui/mainscreens/HomeDetailCards.kt` | 250 | **جديد** — كرت التفاصيل + مصفوفة العناقيد |
| `ui/mainscreens/LegendaryHomeDashboard.kt` | 805 ⇒ 638 | أقسام مكرّرة تُركت للطيف، والتفاصيل خرجت إلى ملفها |
| `ui/component/NeuralDashboardKit.kt` | 738 ⇒ 802 | `NeuralBarSpectrum` أُعيد بناؤه (فتحات ثابتة · عمودان · تحريك بكل فتحة) |
| `ui/viewmodel/HomeDashboardViewModel.kt` | — | `cpuLoadHistory`+`gpuLoadHistory` ⇒ **`loadSamples`** (مصدر واحد) · استرجاع وحفظ مجزّأ كل ٣٠ث وعند الخروج · `File(context.filesDir, "max_load_history.txt")` |
| `test/.../SpectrumModelTest.kt` · `LoadHistoryTest.kt` | 111 · 166 | **جديد** — ٨ + ٩ اختبارات |

### ٥. ما لا يُدَّعى (حدود هذه الجولة)

- **لا APK ولا suite كاملة:** لم أشغّل `assembleDebug` ولا `testReleaseUnitTest` ولا R8 — بأمر المالك
  (§0.1). فالمُتحقَّق هو **ما يترجم** + **١٧ اختبارًا للنماذج الجديدة**، لا ٧٩١ اختبارًا.
- **لا جهاز:** الشكل الجديد لمصفوفة العناقيد وعمودَي الطيف لم يرَهُ أحد بعد؛ الهندسة مضبوطة حسابيًّا
  ومُختبرة، والذوق البصري (سماكة الأعمدة · فراغ العمودين · ألوان العناقيد) يُحكم عليه بلقطة من الجهاز.
- **الأداء غير مقيس على جهاز:** ٥٢ `animateFloatAsState` (٢٦ فتحة × عمودان) — مقصود وثابت العدد ليبقى
  ترتيب الحالة مستقرًّا، لكن كلفته على معالج الهاتف لم تُقس.
- **الحفظ لم يُقس تحت ضغط تخزين:** كتابة كل ٣٠ث وعند مغادرة الشاشة؛ سلوك فشل الكتابة (قرص ممتلئ) يبقى
  «لا يُسقط الشاشة» بحسب التصميم فقط.

### ٦. التقرير بقالب `VALIDATION.md` §8

**TASK:** `HOME-01` — أمر المالك: «لا بناء تلقائي» + تحسينات الشاشة الرئيسية (لا تُطوَ التفاصيل · حسّن
مربّعات الأنوية · الطيف يبدأ أشرطة فارغة كاملة ولا يُعاد من الصفر · لا تكرار لمعلومة في الشاشة).
**FILES:** `AGENTS.md` · `tools/kt_balance.py` · `docs/ai/REVIEW.md` · `ui/util/SpectrumModel.kt` ·
`ui/util/LoadHistory.kt` · `ui/mainscreens/HomeFormat.kt` · `ui/mainscreens/HomeDetailCards.kt` ·
`ui/mainscreens/LegendaryHomeDashboard.kt` · `ui/component/NeuralDashboardKit.kt` ·
`ui/viewmodel/HomeDashboardViewModel.kt` · `debug/.../StudioPreviews.kt` · `res/values/strings.xml` ·
`res/values-ar/strings.xml` · `test/.../SpectrumModelTest.kt` · `test/.../LoadHistoryTest.kt` ·
`NEXT_TASK.md` · `HANDOFF.md`.
**GATES:** §1 ✓ (§0.1 الجديدة) · §2 ✓ · §3 ✓ · §4 (شاشة `nd.max.ui`، بلا عتاد/SELinux/إقلاع ⇒ لا حكم
Luna مطلوب) · §5 ✓ · §6 `code_health` = صفر مخالفات جديدة و`presentation_hw_writes` ثابت على ٢٦ · §7 ✓ ·
§8 (هذا التقرير).
**BUILD:** `:app:compileDebugKotlin` + `:app:compileDebugUnitTestKotlin` = **BUILD SUCCESSFUL 2m49s** ·
`:app:testDebugUnitTest` (المصنّفان الجديدان) = **BUILD SUCCESSFUL 2m37s · ١٧/١٧**.
**RESIDUAL RISK:** الشكل البصري غير مُشاهد على جهاز · الأداء غير مقيس · `assembleDebug`/R8/suite الكاملة
لم تُشغَّل بأمر المالك · `loadSamples` تغيير في شكل `DashboardState` يمسّ كل من يقرأ التاريخ (قُرئت كل
مواضع القراءة: `LegendaryHomeDashboard` و`StudioPreviews`، وكلاهما محدَّث).
**NEXT:** `HOME-02` (لقطات الجهاز للشكل الجديد) · `MT-FM-02`/`FM-04` (مدير الملفات على جهاز) · `NT-16`.

---

## تكملة ٤٧ — تنظيف الجذر: الحطام القديم يُزال، ولغز `.f...p.....` يُحَلّ — 2026-09-20

### ١. ما طُلب

المالك ألصق مخرجات أداة المزامنة من هاتفه («PUSH COMPLETE · PHONE → CODESPACE») وطلب:
«انظر ما هو قديم واحذفه، فقط اترك `.planning`».

### ٢. لغزٌ انحلّ (يوفّر جولة قادمة كاملة)

كل السطور التي تبدو كأسماء ملفات غريبة — مثل `manager/…/FilePermissionOpsTest.kt.f...p.....` —
**ليست أسماء ملفات**: هي الحقل الأول من `rsync --itemize-changes`، وقد أُقحم مسار الملف داخله عند النسخ.

- `.f...p.....` = ملف، **الصلاحيات فقط تختلف** (لا نقل محتوى) · `<f..t......` = ملف نُقل (زمنه يختلف)
- `.d...p.....` = مجلد · `<f+++++++++` = ملف جديد أُنشئ

ولهذا بحثنا في جولات سابقة عن «لاحقة» لا وجود لها على القرص أبدًا. الأثر موجود في حالة عميل
Freebuff: `~/.config/manicode/projects/Ai/chats/*/message-history.json`، ومعه إحصاء الأداة نفسه:
**١٣٥١ ملفًا مفحوصًا · ١١ منشأً · ٣٤ منقولًا · ٠ محذوفًا** والمستثنى `.git .gradle .idea build`.

### ٣. ما حُذف

| # | ما حُذف | لماذا |
| --- | --- | --- |
| ١ | `tools/legacy/` · `check2.py` + `fix_tweak.py` | الأول فاحص موارد بدويّ بديله `code_health.py` + `i18n_coverage.py`؛ والثاني يعدّل ملفًا بمسار ويندوز **مشروع آخر** (`…\zx\azenith\…TweakScreen.kt`) — وكانا نُقلا إلى `legacy/` «بإذن المالك» (تكملة ٣٠) |
| ٢ | `MainActivity.kt.bak` · `ui/mainscreens/LegendaryHomeDashboard.kt.bak` · `ui/mainscreens/HomeDashboardComponents.kt.backup` · `ui/component/NeuralDashboardKit.kt.backup` | نسخة قديمة مقابل كل ملف حيّ موجود — و`.gitignore` يعلن هذه الصنف حطامًا بنصّه |
| ٣ | `LegendaryHomeDashboard.kt.backup` (الجذر، ١٠٢٨ سطرًا) · `CLAUDE.md.bak` | الأول نسخة قبل إعادة الكتابة والحيّ ٦٣٨ سطرًا؛ والثاني العقد القديم وبديله الحيّ `docs/ai/ENGINEERING-CONTRACT.md` |
| ٤ | ٣ سجلات بناء في الجذر · `manager/.kotlin/errors/*.log` (٢) · `.serena/cache/` | حطام بناء وأدوات؛ والكاش متجاهَل ويُعاد إنشاؤه |
| ٥ | `session-ses_f487.md` (الجذر) · `tools/__pycache__/` (٣ ملفات `.pyc`) | الأول نسخة **مطابقة `md5`** لِـ`docs/ai/session-ses_f487.md` (والسجل يقول إنها نُقلت إلى `docs/ai/`)، وهو **متعقّب** فالاسترجاع `git checkout HEAD -- session-ses_f487.md`؛ والثاني مخزّن مؤقت يُنشأ من تشغيل `tools/*.py` |

### ٤. ما أُبقي عن قصد

- **`manager/app/azenith.jks.bak`** — مادة توقيع. المفتاح الساري `azenith.jks` موجود و`build.yml` يثبّت
  بصمته `72e335af…`، لكن من يحذف مفتاحًا قديمًا **لا يستطيع التوقيع به بعدها أبدًا**. يحتاج كلمة صريحة.
- **`.maxmanager-sync-root`** — علامة أداة المزامنة الحيّة، وهي التي أنتجت اللوج أعلاه.

### ٥. الأدلة المقيسة

| البوابة | النتيجة |
| --- | --- |
| `python3 tools/code_health.py --assert` | **exit 0** · «الحصيلة: صحّة نظيفة» |
| `python3 tools/i18n_coverage.py --assert` | **exit 0** · ٠ عوائق · ٨٤+en و٨٥ كودًا متطابقة |
| `python3 tools/kt_balance.py` | **657 ملفًا · ٠ عوائق** |
| مسح ما بعد الحذف | لم يبقَ أي `*.bak`/`*.backup`/`*.log` خارج `build/` إلا مفتاح التوقيع |
| `.planning/` | ١٧ ملفًا — **لم يُمسّ** |

### ٦. ما لا يُدَّعى

- **لا بناء ولا اختبارات** شُغّلت (أمر §0.1): المحذوف ليس كودًا مُصرَّفًا، والدليل هو البوابات الثلاث أعلاه.
- **كل ما حُذف كان غير متعقّب في git** ⇒ الحذف غير قابل للاسترجاع من التاريخ (وهذا سبب السؤال قبل التنفيذ).
- لا حكم على سلامة العتاد ولا على الإقلاع — لا علاقة لهذه الجولة بها.

---

## تكملة ٤٨ — `ATLAS-P1`: خطط التنفيذ العشر (`P0`–`P9`) لمسار «Max Atlas» — 2026-09-20

### ١. ما طُلب

المالك: «أكمل — توقفنا هنا في وضع الخطة `.planning/phases/`»: إكمال مسار GSD من حيث توقف
(السياق · البحث · خريطة الأنماط · المصادر) بإنتاج الخطة التنفيذية. والمصدران الأصليان ينتهيان
صراحةً بـ«جاهز لإنتاج خطط قابلة للتنفيذ بعد دمج `01-PATTERNS.md`».

### ٢. ما أُنتج

| الملف | الحالة | الأسطر |
| --- | --- | --- |
| `phases/01-…/01-PLAN.md` | **جديد** | ٥٦٤ |
| `.planning/STATE.md` | محدَّث: الموضع (١٠ خطط مكتوبة، ٠ منفَّذة) · القرارات · الحواجز · ملف الاستئناف | ٥١ |
| `.planning/ROADMAP.md` | محدَّث: جدول الخطط العشر + التقدّم `0/10` | ٦١ |
| `docs/ai/NEXT_TASK.md` | مؤشر `ATLAS-P1` (لم يكن موجودًا مع أن `01-CONTEXT` يفترضه) | — |

الخطة تدمج مقاطع `01-PATTERNS.md` (A–I) في عشر خطط بترتيب تبعية ونطاقات **حصرية**:
`P0` بيئة اختبار (منفذ قراءة كاذب يسجّل كل عملية · ساعة مضبوطة · ناقل بطيء/طاغٍ · لوحة fixtures ·
شواهد خصوصية · واختبار ذاتي **سلبي** يثبت أن الجاسوس يكشف المخالفة) → `P1` العقود والكتالوج (المصدر
والترخيص ووحدة القياس وصنف السلامة، و**لا حقل سلطة** يرقّي مسارًا إلى قابل للكتابة) → `P2` حدود القراءة
المقيسة (أصناف السبب · مسارات جذر ثابتة · سقوف روابط/حدود/بايتات/tزامن · ومنع
`Shell.getShell`/`RootService.bind`/`Shizuku.requestPermission` كاحتياط) → `P3` إعادة استخدام CPU/GPU
(خيط قراءة يُحقن، والكتّاب والمعاملات كما هي) → `P4` بقية النطاقات ومصفوفة الدعم (+ النطاقات المؤجَّلة
**بسبب معلن**) → `P5` المحلّل المرتَّب والذاكرة والمفاتيح (معرفة→اكتشاف→سبب نهائي · هوية ذاكرة · TTL ·
كتابة ذرّية · إلغاء ≠ استنفاد) → `P6` الخصوصية والتقرير (DTO مُدرَج · قائمة استبعاد · معاينة = بايتات
المشاركة · بلا شبكة) → `P7` الواجهة (صف في الإعدادات للمسار القائم · مجرى حالة واحد · نسبة **قابلة للعدم**
· اقتراح التقرير بعد اكتمال المراحل المؤهّلة فقط) → `P8` مسار بلا root (اختبار رحلة + أثر مكتوب، بلا
توسيع إلى الحياة/السلامة) → `P9` الـfixtures وحارس المعمارية والإغلاق (يفشل مغلقًا لو تعذّرت قراءة الجذر).

### ٣. ما قيس هنا (لا شيء من ذاكرة)

| البوابة | النتيجة |
| --- | --- |
| `python3 tools/code_health.py --assert` | **exit 0** · صحّة ٠/٠/٠/٠ · الدَّين `10/29/66/26` (+٢ todo، و٢٣٣ استيرادًا شاملًا للنظام غير محتسب) · ٤٧٧ ملف Kotlin · ١٠٩٦٣٠ سطرًا |
| `python3 tools/i18n_coverage.py --assert` | **exit 0** · ٠ عوائق · ٨٤+en · المنتقي ٨٥ · `locales_config` ٨٥ |
| `python3 tools/repo_audit.py` | `PROBLEMS: 0` · ٣٤٩ ملفًا مفحوصًا · ٢٥٤٠ مرجع `R.string` · ٣٢٠٥ نصًّا أساسيًّا |
| `python3 tools/kt_balance.py` | ٦٥٧ ملفًا · ٠ عوائق |
| `git diff --no-index --check` على الخطة | لا أخطاء فراغ (و`grep ' $'` على الملف: لا نتيجة) |
| ملفات المنتج | **لا شيء تغيّر** — `git status` يُظهر وثائق فقط |

### ٤. حاجز قديم صار قياسًا لا افتراضًا

`PROJECT.md` و`STATE.md` و`01-RESEARCH.md` و`01-PATTERNS.md` كلها تسجّل إخفاق `code_health` بسبب ملف
المالك `txt.txt`. **ذلك الملف غير موجود في الشجرة الآن** (find على المستودع كله: لا نتيجة) و`--assert`
= exit 0. لم يحذفه هذا المسار، ولم تُلمس قاعدة تجاهل ولا بوابة، وحُدِّثت المخرجات لتصف الحاجز بأنه
«لم يعد يُعاد إنتاجه» لا «صفر مخالفات». وأي حاجة لاحقة إلى نصّ القائمة نفسه تحتاج طلبها من المالك.

### ٥. قرارات مفتوحة تمنع التنفيذ (مدرجة في الخطة §15)

**`DECISION-1` (يسدّ `P2`):** ميزانيتان متعارضتان — `01-RESEARCH` تقترح **٨ ثوانٍ** للمهمة و**٢٥٠ مللي**
للعملية و**٢٥٦ كب** إجمالًا، و`01-PATTERNS` تقترح **١٢ ثانية** و**١ ثانية** و**٥١٢ كب**. لا يمكن للناقل
فرض مجموعتين؛ والأوصية: **٨ث للمهمة / ١ث للعملية / ٤كب فقطر و١٦كب لملخّصات proc المراجَعة / ٢٥٦كب
إجمالًا / إيجابية ١٠ دقائق / سلبية ٦٠ ثانية**.
وباقي القرارات: هل `P8` داخل المرحلة · قائمة نطاقات الإصدار الأول · الاحتفاظ ٢٤ ساعة · موضع النصوص
(`max_screen_strings.xml` القائم) · ومن ينفّذ ومتى (وقاعدة المالك §0.1: لا بناء إلا بالطلب).

### ٦. ما لا يُدَّعى

لا ملف منتج، ولا commit، ولا بناء ولا اختبار — لم يطلب المالك ذلك وقاعدته تمنعه افتراضيًّا؛ فالمُتحقَّق
**أن الخطة متسقة مع البوابات الخمس**، لا أنها تعمل. ولا توافق عتاد ولا أداء ولا التزام ASVS: كل ما
يخصّ الأجهزة يبقى «غير متحقَّق حتى قياسه على جهاز». ولا مراجعة سلامة: النموذج المُهيَّأ تاريخيًّا
للمراجعة غير قابل للاختيار هنا، فخطط مسّ العتاد (`P2`/`P3`) تبقى `UNREVIEWED`.

### ٧. التقرير بقالب `VALIDATION.md` §8

**TASK:** `ATLAS-P1` — إكمال مسار التخطيط GSD: كتابة الخطط التنفيذية للمرحلة ١ (Max Atlas) بعد البحث
وخريطة الأنماط والمصادر.
**FILES:** `.planning/phases/01-…/01-PLAN.md` (جديد) · `.planning/STATE.md` · `.planning/ROADMAP.md` ·
`docs/ai/NEXT_TASK.md` (مؤشر) · هذا السجل. **صفر ملف منتج**.
**GATES:** §1 (نطاق التخطيط معلن) · §2 (لا تغيير سلوك) · §3 (لا نصوص جديدة ⇒ i18n كما هي) · §4 (لا واجهة
تغيّرت) · §5 (البوابات أعلاه) · §6 (الخطة تمنع كتابة العتاد نصًّا وتحرسها باختبارات) · §7 (بلا بناء) · §8.
**BUILD:** لم يُشغَّل شيء (لم يُطلب): لا `compileDebugUnitTestKotlin` ولا `assembleDebug` — والعبارة
الواجبة: *compilation unverified in this environment*.
**RESIDUAL RISK:** الأرقام المقترحة غير مقيسة · تعارض الميزانيتين قائم حتى قرار · غياب مراجعة السلامة ·
`txt.txt` غير موجود فلا يمكن إعادة اشتقاق أي رقم منه · سقف الملفات الأكبر من ١٠٠٠ سطر قائم والخطة لا تزيده.
**NEXT:** قرار المالك على §15 (أوّلًا `DECISION-1`) ثم تنفيذ `P0`+`P1` (اختبارات فقط، بلا مُصرّف).

---

## تكملة ٤٩ — `ATLAS` P0+P1: عقود الأدلة والكتالوج المُراجَع، وبيئة اختبار بلا جهاز — 2026-09-20

### ١. ما طُلب

المالك: «المهم أنك فقط ما نريده لذا ابدأ واجعله الأفضل» → إذن تنفيذ لمسار Max Atlas، بعد أن كان
التخطيط وحده مُصرَّحًا به.

### ٢. ما نُفِّذ: ٩ ملفات (٧ جديدة في المنتج/الاختبار + ٢ سابق) · **١٦١٦ سطرًا**

| الملف | الدور | الأسطر |
| --- | --- | --- |
| `APP/core/atlas/AtlasModels.kt` | مفردات الأدلة: نطاقات · مصدر · وحدات · **محاور متعامدة** (وصول/دلالة/فشل/حداثة) · نتائج قراءة موقّعة · قواعد وحدات وقوائم CPU | ٣٦٢ |
| `APP/core/atlas/AtlasCatalog.kt` | الكتالوج: **جذور معتمدة** · مدخلات بمصدر إلزامي · تحقّق يجمع كل العيوب · اختيار حتمي لا يُسقط العام بسبب جهاز مجهول · **١٤ بذرة مراجَعة** | ٤٠٢ |
| `TEST/core/atlas/AtlasCatalogTest.kt` | ١٥ اختبارًا: سلبيات التحقّق · انحدار المجهول · الوحدات · ثوابت النموذج · حارس المصدر | ٢٤٢ |
| `TEST/core/atlas/AtlasHarnessTest.kt` | اختبارات P0 الذاتية ومنها **السلبي** | ١٧٨ |
| `TEST/core/atlas/support/AtlasFakeReadPort.kt` | منفذ قراءة كاذب يسجّل كل عملية ويحترم الحصص ويُثبت الغياب من قائمة مقروءة فقط | ٢٢٤ |
| `TEST/core/atlas/support/AtlasClock.kt` | ساعة مضبوطة (أمام/خلف) + سياج جيل للنشر | ٦٢ |
| `TEST/core/atlas/support/AtlasSourceGuard.kt` | حارس مصدر **يفشل مغلقًا** (لا يتخطى صامتًا) | ٥٩ |
| `TEST/core/atlas/support/AtlasBudgets.kt` | الميزانيات التصميمية في مصدر واحد (غير مقيسة) | ٤٨ |
| `TEST/core/atlas/support/AtlasCanaries.kt` | شواهد خصوصية كاذبة + بلوب مسموم يُثبت أن الفحص يفشل | ٣٩ |

### ٣. الأدلة المقيسة (أوامر هذه الجولة)

| الأمر | النتيجة |
| --- | --- |
| `bash gradlew :app:testDebugUnitTest --tests 'nd.max.core.atlas.*'` | **BUILD SUCCESSFUL 3m45s** · **٢٥ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** (harness ١٠ · catalog ١٥) |
| إعادة تصريف نطاق التعديل بعد `touch` | **BUILD SUCCESSFUL 2m26s** · **صفر تحذير** من ملفات أطلس |
| `code_health --assert` | **exit 0** · الدَّين لم يتغيّر: `10/29/66/26` (+٢ todo) |
| `kt_balance` | **٦٦٦ ملفًا · ٠ عوائق** (كان ٦٥٧) |
| `i18n_coverage --assert` · `repo_audit` | exit 0 · ٠ عوائق · `PROBLEMS: 0` (٣٥١ ملف Kotlin) |
| فحص رموز السلطة/النقل في ملفَي المنتج | **٠** لكل من: writable · canWrite · controlEligible · submitControl · chmod · RootFileAccess · Shell · Shizuku |

### ٤. القيمة الفعلية: الاختبار السلبي لا الزخرفي

بيئة P0 ليست أسماء ملفات بل **قدرة على إثبات الفشل**، وهذا مُختبَر لا موصوف:
- تدقيق `AtlasFakeAudit` **يرفض** مسبارًا يجيب ولا يسجّل (التحكّم السلبي) — فادّعاء «صفر كتابة» صار
  ممكنًا في مرحلة لاحقة؛
- قراءة أبطأ من مهلة العملية تُرجِع `TIMED_OUT` **ولا تُرجِع قيمة جزئية**؛
- الغياب لا يُصدر إلا من قائمة فعلاً مقروءة، وإلا فهو `UNKNOWN_CAUSE` — «لم أنظر» ليست «غير موجود»؛
- الحصص توقف التعداد بعد ٥١٢ مدخلًا وتوسم النقص `truncated` بدل اعتباره غيابًا؛
- الأسماء غير الآمنة تُسقَط تُوسم تغطية مقطوعة، ولا تصل إلى مستدعٍ.

### ٥. عطب بيئي جديد (يُسجَّل كي لا يُعاد)

`./gradlew` **فقد بت التنفيذ** (المزامنة من الهاتف لا تحفظه) ⇒ `Permission denied`. الحلّ
`bash gradlew` — وهو نفسه ما يفعله CI في `build.yml` (`chmod +x ./gradlew`).

### ٦. تعديل على الخطة (`Amendment A-1`)

P0 وP1 هبطا معًا لا في موجتين: بيئة الاختبار مبنية على **مفردات P1 نفسها**، ومفردتان متوازيتان
هو بالضبط الانحراف الذي تمنعه §2. ونطاق P0 استوعب ملفات الدعم الخمسة التي لم تسمّها خريطة الأنماط.
مسجّل في `01-PLAN.md` §0 و§17.

### ٧. ما لا يُدَّعى

لا APK ولا R8 ولا suite كاملة (لم تُطلب) · **لا جهاز**: الميزانيات تصميمية غير مقيسة والشكل لا يعمل بعد ·
**لا مراجعة مستقلة**: P3/P4 تمسّ `core/hardware` فتبقى `UNREVIEWED` حتى تتوفّر مراجعة من عائلة نموذج
أخرى · الأربع عشرة بذرة **ليست** مصفوفة النطاقات (تلك P4) · `featureTag = null` في كل بذرة عمدًا
(ربطها بمعرّفات `HardwareFeature` عمل P3) · الخصائص المتداخلة مثل `stats/time_in_state` غير قابلة
للتمثيل بعد (القالب يسمح باسم أساس واحد آمن).

### ٨. التقرير بقالب `VALIDATION.md` §8

**TASK:** `ATLAS` P0+P1 — بيئة اختبار بلا جهاز، وعقود الأدلة والكتالوج المُراجَع، بأمر المالك «ابدأ». 
**FILES:** ٢ في المنتج (`core/atlas/**`) + ٧ في الاختبار (`test/.../core/atlas/**`) · زائد `01-PLAN.md` ·
`STATE.md` · `ROADMAP.md` · `REQUIREMENTS.md` · `NEXT_TASK.md` · هذا السجل. لا ملف منتج قائم تغيّر.
**GATES:** §1 ✓ · §2 (لا تغيير سلوك: ملفات جديدة فقط) · §3 (لا نصوص مستخدمة) · §4 (لا واجهة) · §5 (الأرقام أعلاه) · §6 (لا كتابة عتاد؛ الحماية نصًّا واختبارًا، والدَّين ثابت) · §7 (البناء ضيّق النطاق بأمر «ابدأ») · §8.
**BUILD:** `:app:testDebugUnitTest --tests 'nd.max.core.atlas.*'` = **BUILD SUCCESSFUL 3m45s** · `:app:compileDebugUnitTestKotlin` بعد `touch` = **3m45s→2m26s** بلا تحذيرات في ملفات أطلس.
**RESIDUAL RISK:** الميزانيات غير مقيسة · لا قياس على جهاز (بطء sysfs الحقيقي قد يزيد حالات TIMED_OUT) ·
غياب المراجعة المستقلة · لا تغطية Compose · `AtlasUnrecordedFakeReadPort` مثال سلبي مقصود داخل الاختبارات.
**NEXT:** `DECISION-1` لفتح `P2` · أو `P3`/`P4` الآن (والأفضل: `P3` بعد مراجعة مستقلّة).

## تكملة ٥٠ — `ATLAS` P2: حدّ القراءة المحدود، والاختبارات تكشف عطبين في الكود — 2026-09-20

### ١. ما طُلب

المالك: «اكمل» — تكملة على مسار التنفيذ بعد `P0`+`P1` (تكملة ٤٩)، بلا تحديد بند ⇒ نُفِّذ
**المسار الحرج** التالي: `P2` (حدّ القراءة المحدود)، وهو الشرط المسبق لاكتشاف P5.

### ٢. ما نُفِّذ: ٧ ملفات · **١٩٤٩ سطرًا** (المنتج ٣٩٠ منها)

| الملف | الدور | الأسطر |
| --- | --- | --- |
| `APP/core/hardware/ReadOnlyProbeAccess.kt` | **الحد نفسه**: ناقل منخفض المستوى مُحقون (قراءة/تعداد/حلّ روابط) + ميزانية في مصدر واحد + تهذيب المسار + حفظ السبب + قاعدة الغياب المُثبَت | ٣٩٠ |
| `APP/core/atlas/AtlasModels.kt` | أُضيف `AtlasProbeRequest` من **المنتج** (كان في دعم الاختبار) محمّلًا بـ`requiresPrivilege` و`reviewedProcSummary` | ٣٩٠ |
| `TEST/core/hardware/ReadOnlyProbeAccessTest.kt` | **٢٣ اختبارًا** لقبول P2 | ٤٩٢ |
| `TEST/core/atlas/support/AtlasFakeTransport.kt` | الناقل الكاذب: يزيّف **الإدخال/الإخراج فقط** ويسجّل كل نداء (بديل `AtlasFakeReadPort`) | ١٧٥ |
| `TEST/core/atlas/AtlasHarnessTest.kt` | صار يقود **الوصول الحقيقي** فوق الناقل الكاذب | ٢٠٧ |
| `TEST/core/atlas/support/AtlasBudgets.kt` | **يتفوّض** إلى `AtlasReadBudget.DEFAULT` بدل نسخة ثانية | ٥٢ |
| `TEST/core/atlas/AtlasCatalogTest.kt` | حارس المصدر صار يفحص ملف الحد أيضًا | ٢٤٣ |

### ٣. القرار التصميمي الأهم: لا نسخة ثانية من المنطق

الاختبار لم يعد يختبر نسخة من القواعد، بل **الكود المُسلَّم**: كل ما كان في المسبار الكاذب من سياسة
(الميزانية · تهذيب المسار · تحويل الأسباب · قاعدة الغياب · تحليل القيمة) انتقل إلى المنتج، ولم يبقَ
في الناقل الكاذب إلا `readText`/`listNames`/`canonicalPath`. أي أن «صفر كتابة» و«الغياب لا يُدَّعى بلا
دليل» تُثبَتان على الشيفرة التي تعمل فعلًا على جهازك، لا على بديل اختباري يشبهها.

### ٤. عطبان حقيقيان كشفهما التشغيل — وأُصلح **الكود** لا التوقّع

1. **نتيجة التعداد لم تكن تقول إنها توقّفت مبكرًا.** ناقل يقطع القائمة عند ١٢٨ اسمًا كان يبدو
   للمستدعي **قائمة كاملة** — وهو بالضبط الشكل الذي يحوّل «مدخل لم يُزر» إلى «غير موجود».
   أُضيف `AtlasTransportList.truncated` وتُمرَّر الآن عبر الحدّ. (الاختبار الذي كشفه: فحص الميزانية
   في التعداد — كان يفشل لأن العلم لم يكن موجودًا أصلًا.)
2. **قاعدة الغياب كانت في المسبار الكاذب وحده.** انتقلت إلى المنتج كحالة للعمل نفسه (`enumeratedParents`):
   `ABSENT` لا تُرجَع إلا لمسار **عدَّد هذا العمل نفسه أباه بنجاح**؛ وتعداد فاشل لا يُرخّص ادّعاء غياب
   أبدًا. أي «تعداد هذه الجولة لا يصلح دليلًا لجولة أخرى».

والاختباران اللذان سقطا في أول تشغيل كانا **خطأ في اختباري أنا** لا في الكود: نصّ رسالة مختلف
(`approved anchor` مقابل `approved anchors`)، وناقل كاذب يحلّ المسار المطابق حرفيًّا فقط ⇒ صُحّحت
البيانات والاتّساق، ولم يُخفَّف أي توقّع.

### ٥. الأدلة المقيسة (أوامر هذه الجولة، لا ذاكرة)

| الأمر | النتيجة |
| --- | --- |
| `bash gradlew :app:testDebugUnitTest --tests 'nd.max.core.atlas.*' --tests 'nd.max.core.hardware.ReadOnlyProbeAccessTest'` | **BUILD SUCCESSFUL · ٤٨ اختبارًا · ٠ فشل** (`ReadOnlyProbeAccessTest` ٢٣ · `AtlasHarnessTest` ١٠ · `AtlasCatalogTest` ١٥) — كان ٢٥ ⇒ **+٢٣** |
| إعادة تصريف نطاق التعديل بعد `touch` (منتج + اختبار) | **BUILD SUCCESSFUL 2m23s** · **صفر تحذير** من الملفات الملموسة |
| `code_health --assert` | **exit 0** · «صحّة نظيفة» · الدَّين ثابت `10/29/66/26` |
| `kt_balance` | **٦٦٨ ملفًا · ٠ عوائق** (كان ٦٦٦) |
| `i18n_coverage --assert` · `repo_audit` · `git diff --check` | exit 0 · ٠ عوائق · `PROBLEMS: 0` · نظيف |
| رموز السلطة/النقل في ملف الحدّ | **صفر** لكل: writable · canWrite · controlEligible · submitControl · chmod · RootFileAccess · Shell · Shizuku — والملف أُضيف إلى حارس المصدر الذي **يفشل مغلقًا** |

### ٦. `DECISION-1`: لم يبقَ حاجزًا، وما زال ينتظر كلمتك

الميزانيتان المتعارضتان (٨ث/٢٥٠مللي/٢٥٦كب مقابل ١٢ث/١ث/٥١٢كب) وُحِّدتا **كما أوصت الخطة** في
`AtlasReadBudget.DEFAULT` — **مكان واحد**، وبيئة الاختبار تتفوّض إليه، فتغيير القرار لاحقًا تغيير ملف
واحد والاختبارات تتبعه. وكل القيم تبقى **تصميمية غير مقيسة** ومسجَّلة بذلك في `PROVENANCE`.

### ٧. ما لا يُدَّعى

`T2.5` (ناقل حقيقي فوق المسار المصرَّح القائم) **لم يُبنَ عمدًا**: يحتاج المراجعة المستقلة غير المتاحة
في هذه البيئة، و`UnavailableAtlasReadTransport` تُرجع `BACKEND_UNAVAILABLE` لكل محاولة بدل التخمين ⇒
**لا سؤال صلاحية ولا `Shell`/root/Shizuku في الملف**. و`T2.4` (سقف التزامن) **غير منفّذ**: يحتاج
مِفصل coroutines لا وجود له قبل وصول ناقل حقيقي — مسجَّل بندًا مفتوحًا لا مُدَّعى. ولا جهاز: كل حدود
التوقيت والبايت **غير مقيسة على عتاد**، و`P2` تبقى `UNREVIEWED`.

### ٨. التقرير بقالب `VALIDATION.md` §8

**TASK:** `ATLAS` P2 — حدّ القراءة المحدود (T2.1–T2.4/T2.6)، بأمر المالك «اكمل».
**FILES:** ١ جديد في المنتج (`core/hardware/ReadOnlyProbeAccess.kt`) · ١ معدَّل في المنتج (`core/atlas/AtlasModels.kt`) ·
٤ في الاختبار (جديد: `ReadOnlyProbeAccessTest.kt` · `AtlasFakeTransport.kt`؛ معدَّل: `AtlasHarnessTest.kt` ·
`AtlasBudgets.kt` · `AtlasCatalogTest.kt`؛ محذوف: `AtlasFakeReadPort.kt`) · وثائق: `01-PLAN.md` (§5.1 + Amendment A-2) ·
`ROADMAP.md` · `STATE.md` · `REQUIREMENTS.md` · `NEXT_TASK.md` · هذا السجل. **لا سلوك منتج خارج أطلس تغيّر.**
**GATES:** §1 ✓ · §2 (لا تغيير سلوك للمستخدم: لا مستدعي للملف الجديد بعد — الملف لا يُستدعى من أي شاشة حتى P5) · §3 (لا نصوص مستخدمة) · §4 (لا واجهة) · §5 (الأرقام أعلاه) · §6 (لا كتابة sysfs؛ الناقل بلا عملية كاتبة أصلًا، وحارس المصدر يفحصه) · §7 (البناء ضيّق النطاق: نطاق الاختبار الملموس فقط) · §8.
**BUILD:** `:app:testDebugUnitTest --tests 'nd.max.core.atlas.*' --tests 'nd.max.core.hardware.ReadOnlyProbeAccessTest'` = **BUILD SUCCESSFUL** · `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` بعد `touch` = **2m23s** بلا تحذير في ملفات أطلس.
**RESIDUAL RISK:** `T2.5` و`T2.4` غير منفَّذين (مذكوران أعلاه) · الميزانيات غير مقيسة على عتاد · سلوك sysfs الحقيقي (روابط، اسم متداخل مثل `stats/time_in_state`) خارج القالب الحالي بحدّ واحد آمن · لا مراجعة مستقلة ⇒ `UNREVIEWED` · الملف لا يُستدعى من المنتج بعد فليس له أثر مرئي.
**NEXT:** `P4` (بقية النطاقات ومصفوفة الدعم — قابلة للتحقيق بمُصرّف) · أو `P3` بعد مراجعة مستقلة · وتأكيدك على `DECISION-1` إن أردت مجموعة أرقام أخرى.

## تكملة ٥١ — `ATLAS` T2.4 + `P4`: مصفوفة الدعم، وسقف التزامن، وعطبان كشفهما التشغيل — 2026-09-20

### ١. ما طُلب

المالك: «اكمل» ثم «اكمل كل شيئ دفعة واحدة وبعد الانتهاء قم بالمراجعه والتحسين اكثر وبعدها اختبار بناء»
⇒ نُفِّذت بقية `P2` (`T2.4`) و`P4` كاملةً، ثم مراجعة وتصحيح، ثم **بناء كامل بإذن صريح** (§5 من `AGENTS.md`).

### ٢. ما نُفِّذ — ملفان جديدان · ١٩٢٣ سطرًا (منتج ١٢٢٨ منها بـ`ReadOnlyProbeAccess`)

| الملف | الدور | الأسطر |
| --- | --- | --- |
| `APP/core/atlas/AtlasPlatformProvider.kt` | **جديد**: نطاقات الحراري/الطاقة/الذاكرة/التخزين/العرض/الحساسات/الشبكة/الصلاحية من قراءات **مُحقونة**، + مصفوفة دعم **لا تُبنى بدومين ناقص** | ٨٤٦ |
| `TEST/core/atlas/AtlasPlatformProviderTest.kt` | **جديد**: ٢٢ اختبارًا لقبول `P4` | ٤٨٥ |
| `APP/core/hardware/ReadOnlyProbeAccess.kt` | `T2.4`: سقف التزامن بدخول-ثم-فحص وتراجع + إحصاء `inFlight` + رفض سطح الـAPI العام | ٤٧٢ |
| `TEST/core/hardware/ReadOnlyProbeAccessTest.kt` | ٢٧ اختبارًا (منها أربعة للتزامن: إعادة دخول · سقف مميَّز · فيضان ٨ خيوط · إعادة استخدام البوابة) | ٦٧١ |
| `TEST/…/AtlasSourceGuard.kt` | `code()`: الحارس يفحص **الكود بعد نزع التعليقات** (فلا يُعاقب ملفًا يشرح ما يرفضه) + دعم الأسماء المائيّة | ١٣٨ |
| `APP/core/atlas/AtlasCatalog.kt` | `AtlasAnchors.PUBLIC_API` جذرًا **وهميًّا** (لا يُفتح من ناقل الملفات) | — |
| `tools/kt_balance.py` | دعم أسماء Kotlin بين علامتين مائيّتين | ٣٥٠ |

### ٣. عطبان حقيقيان — كشفهما **التشغيل** لا القراءة، وأُصلح **الكود**

1. **سقف التزامن لم يكن ذرّيًّا.** كان «افحص ثم ادخل»: ثمانية خيوط تقرأ `inFlight = 0` جميعًا
   فتتجاوز الفحص كلها وتدخل ⇒ السقف يسقط بالضبط تحت الفيضان الذي وُجد لأجله. اختبار الثمانية خيوط
   كشفه (٨ محاولات وصلت الناقل بدل ٢). صار **زيادة-ثم-فحص-وتراجع**.
   وحدّ أعلنه: نسختي الأولى من ذلك الاختبار كانت **هي** مضطربة (كانت تنتظر فيسمح الانتظار للجميع بالدخول
   لاحقًا) — فأُبدلت ببوابة تُمسك المحاولتين المفتوحتين وترفض البقية رفضًا حتميًّا. العطب كان في الكود،
   والاضطراب كان في اختباري، وأُصلح الاثنان.
2. **`catalog.byDomain` يستثني مدخلات الشركة المصنّعة** (لأنه ينتقي بلا تلميح جهاز). فعدّ «ما يحمله
   الكتالوج» به جعل مدخلًا مطابقًا لـ`mediatek` يبدو **معرفة أكثر مما في الكتالوج** — والثابت الجديد
   (`matchedEntries ≤ catalogEntries`) أمسكه في أول تشغيل. صار العدّ صريحًا على `entries`.
   ودرسه: ثابت واحد جديد كشف خطأ دلالة لم تكشفه ٢٩٥ اختبارًا قبله.

### ٤. مراجعة وتحسين بعد التسليم (كما أمر المالك)

- `stripComments` في حارس المصدر **لم تكن تعرف الأسماء المائيّة** (`fun \`it's fine\`()`) فكانت فاصلة
  عليا داخل اسم اختبار تفتح «محرفًا» وهميًّا وتبتلع كودًا حقيقيًّا ⇒ **حارس يقرأ أقل ممّا يدّعي**.
  أُصلحت، وأُضيف اختبار يثبت أنه لا يبتلع (`cbc` يظهر بعده).
- `psi()` كانت تحمل `?: return` داخل وسائط نداء (ذكي وغير واضح) ⇒ صارت فروعًا صريحة.
- `vendorHints()` كانت **سطحًا ميتًا** (لا يستدعيها شيء) ⇒ وُصلت بـ`matchedEntries` فصار للتلميح أثر
  مقيس: جهاز مطابق يقرأ معرفة أكثر، ولا يشتق حقيقة أكثر (مُختبَر).
- أُضيف: اختبار حتمية (مصفوفتان من قراءات واحدة متساويتان)، واختبار اسم جهاز شاذّ
  (`zram 0!` ⇒ معرّف قانوني وإحصاء `zram0`)، وثابت مقيّد على `matchedEntries`.
- **تحذير قائم لم ألمسه:** `MemoryStallTest.kt:122` (نداء آمن زائد) — ظهر في بناء البناء لا في ملفات
  أطلس، ولم أُصلحه لأن نطاق مهام `P4` لا يشمل ملفات `maxai`، ويُسجَّل هنا كي لا يبقى مجهولًا.

### ٥. الأدلة المقيسة

| الأمر | النتيجة |
| --- | --- |
| `:app:testDebugUnitTest --tests 'nd.max.core.*' --rerun` | **BUILD SUCCESSFUL · ٢٩٥ اختبارًا · ٠ فشل** (أطلس ٧٤: `ReadOnlyProbeAccessTest` ٢٧ · `AtlasPlatformProviderTest` ٢٢ · `AtlasCatalogTest` ١٥ · `AtlasHarnessTest` ١٠) |
| `ReadOnlyProbeAccessTest` بـ`--rerun` **ثلاث مرّات** | **BUILD SUCCESSFUL ×٣** (لا اضطراب بعد إصلاح البوابة) |
| **بناء كامل (بإذن المالك):** `:app:testReleaseUnitTest :app:assembleDebug -x :app:lintVitalRelease` | **BUILD SUCCESSFUL 5m43s** · **٨٨٢ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** · ‎181 مهمّة (٢٤ مُنفَّذة) |
| APK (debug) | ‎**121,032,092**‎ بايت |
| `kt_balance` | **٦٧٠ ملفًا · ٠ عوائق** · و`--self-test` = **١٧ حالة · ٠ إخفاق** |
| `code_health --assert` · `i18n_coverage --assert` · `repo_audit` · `git diff --check` | exit 0 · exit 0 · `PROBLEMS: 0` · نظيف |

### ٦. القيمة الفعلية — لا عدد ملفات

- **مصفوفة لا تكذب بالحذف:** `AtlasSupportMatrix` **يستحيل بناؤها** بدون صف لكل نطاق، فما لا خلفية له
  يظهر `DEFERRED` بـ`no-source-wired`، وCPU/GPU بـ`other-provider`. وصف `OBSERVED` بلا ملاحظة ناجحة
  واحدة يرمي استثناءً.
- **حرارة أمينة:** المقياس يأتي من المصدر لا من حجم الرقم (٤٢٠٠٠ يُقرأ كـم°م أو °م حسب إعلان المصدر)،
  و`ap`/`tsens` **لا تدّعي وصلة CPU**، و`temp` مفقود أو غير مقروء ⇒ `MALFORMED` بالنصّ الخام و**ليس ٠**.
- **أبعاد لا تُدمج:** µA و µV و µAh و µWh أربع وحدات منفصلة، وسالب التيار يبقى سالبًا (تفريغ)، ولا تُحوَّل
  طاقة إلى شحنة.
- **شبكة بلا هوية:** الصفوف حالة فقط (متصل · محدود · نوع النقل)، والنوع لا يملك حقل عنوان/SSID/MAC أصلًا،
  والفحص يثبت خلوّ المخرجات من الشواهد.
- **صلاحية لا تُشتق من وجود ملف:** غير مُتحقَّق ⇒ `UNAVAILABLE` بسبب `unverified-identity`؛ ومَن يقول
  غير ذلك هو لوحة التحكّم القائمة وحدها.
- **حارس يفحص الكود لا النثر:** `import android`، وأسماء كل `ViewModel` في المنتج، و`Shell`/`Shizuku`/
  `chmod`/`-cbc`/`writable`/`File`/`data/adb`، والـgetters الهوياتية — **صفر إصابة**.

### ٧. ما لا يُدَّعى

مُهايئ `AtlasPlatformSource` الحقيقي (واجهة البطارية العامة و`/proc` و`/sys`) **لم يُكتب عمدًا**:
مكانه خطة التوصيل (`P5`) كما كان حال ناقل `P2`، و`UnavailableAtlasPlatformSource` هي الافتراض الأمين
حتى ذلك الحين (٠ ادّعاء). ولا جهاز: لا قياس توقيت ولا بايتات على عتاد، و`P2`/`P4` تبقى `UNREVIEWED`.
و`T2.5` و`P3` و`P5`–`P9` غير منفّذة. والبناء نجح يعني «يترجم وتمرّ الاختبارات» لا «يعمل على هاتفك»
(كتابة sysfs/SELinux/الإقلاع تحتاج جهازًا).

### ٨. التقرير بقالب `VALIDATION.md` §8

**TASK:** `ATLAS` T2.4 + P4 — سقف التزامن، ونطاقات المنصّة، ومصفوفة الدعم، ثم مراجعة وتحسين، ثم بناء كامل.
**FILES:** ٢ جديد في المنتج/الاختبار (`core/atlas/AtlasPlatformProvider.kt` · `AtlasPlatformProviderTest.kt`) · معدَّل: `ReadOnlyProbeAccess.kt` · `ReadOnlyProbeAccessTest.kt` · `AtlasCatalog.kt` · `AtlasSourceGuard.kt` · `tools/kt_balance.py` · وثائق (`01-PLAN` §7.1 + Amendment A-3 · `ROADMAP` · `STATE` · `REQUIREMENTS` · `NEXT_TASK` · هذا السجل). لا سلوك منتج قائم تغيّر (الملفان الجديدان لا يستدعيهما منتج بعد).
**GATES:** §1 ✓ · §2 (لا أثر مرئي: لا مستدعي) · §3 (لا نصوص مستخدمة) · §4 (لا واجهة) · §5 (الأرقام أعلاه) · §6 (لا كتابة sysfs؛ الحارس يفشل مغلقًا) · §7 (**الباء الكامل بإذن المالك الصريح**) · §8.
**BUILD:** `:app:testReleaseUnitTest :app:assembleDebug -x :app:lintVitalRelease` = **BUILD SUCCESSFUL 5m43s** · **٨٨٢ اختبارًا · ٠ فشل** · APK ‎121,032,092‎ بايت · تحذير واحد قائم مسبقًا في `MemoryStallTest` (ليس من نطاقي).
**RESIDUAL RISK:** `T2.5`/`P3`/`P5`–`P9` غير منفّذة · مُهايئ المنصّة غائب (٠ ادّعاء) · الميزانيات والمقاييس غير مقيسة على عتاد · لا مراجعة مستقلة ⇒ `UNREVIEWED` · `PUBLIC_API` جذر معتمد لكنه وهمي ولذلك رفضه في الحدّ صريح ومُختبَر.
**NEXT:** `P3` (إعادة استخدام CPU/GPU) ثم `P5` · أو تمرير حزمة مراجعة مستقلة على diff `P2`+`P4` (مراجعة السلامة شرط إغلاق §6).

---

## تكملة ٥٢ — `ATLAS` مراجعة الفجوات (جولة ٢): ما هو محجوب بمصدر، وما يفتقره أطلس فعلًا — 2026-09-20

**TASK:** «هل لديك أفكار لتحسين Max Atlas وسد فجواته وما يفتقره — ابحث بحثًا عميقًا في الإنترنت».
جولة **تحليل وبحث فقط**: لا كود، لا بناء، لا اختبار (بأمر §0.1)، ولا تغيير سلوك.

**FILES:** جديد: `.planning/phases/01-…/01-GAPS-AND-IDEAS.md`. معدَّل: `01-SOURCES.md` (دفعة ٢: S18a–S18c, S19–S25, A06–A10 مع حالتها وحاجز `/proc/pressure`) · `01-PLAN.md` (§18 + سطر في سجل التغييرات) · `.planning/STATE.md` (حواجز + نقطة الاستئناف). **صفر ملف منتج أو اختبار تغيّر.**

**GATES:** §1 ✓ (البوابات الثابتة أدناه) · §2 (لا أثر مرئي: لا مستدعي أصلًا) · §3 (لا نصوص) · §4 (لا واجهة) · §5 (لا اختبارات جديدة) · §6 (لا كتابة sysfs؛ ولا مسار جديد) · §7 (لم يُشغَّل بناء — لا إذن) · §8.

**BUILD:** **لم يُشغَّل بناء ولا اختبار في هذه الجولة** (وثائق فقط، بأمر §0.1). «compilation unverified in this environment» لا ينطبق: لا كود جديد. البوابات الثابتة: `code_health --assert` exit 0 · `i18n_coverage --assert` exit 0 · `kt_balance` ٠ عوائق · `repo_audit` PROBLEMS: 0 · `git diff --check` نظيف.

**أهم ما خرجت به الجولة (يُغيّر قراءة الخطة، لا نصوصها):**

1. **تسعة أسطح كانت الخطة تعتبرها «نُجربها ونرى» هي محجوبة بمصدر** (AOSP `neverallow`): ملفات cgroup v1/v2، قراءة debugfs، `selinuxfs`، اشتراك uevent عبر netlink، `sysfs_net`، أي كتابة في sysfs، و`/proc` (`stat`, `uptime`, `version`, `vmstat`, `loadavg`, `mounts`, `swaps`, `slabinfo`, `proc_uid_*`, `proc_net_tcp_udp`). ⇒ لا حمل CPU عام، ولا نواة من `/proc/version` (البديل `android.system.Os.uname()`)، ولا معلومات swap من `/proc` (البديل `ActivityManager.MemoryInfo` لإجمالي/متاح الذاكرة فقط، **ولا بديل عام معروف لمعلومات swap/zram** ⇐ تُوسم `DEVICE_DEPENDENT` أو `UNMEASURABLE`).
2. **والوجه المقابل موثَّق**: `sysfs_gpu` هي **الوحيدة** الممنوحة صراحةً لنطاق التطبيقات (`allow { appdomain -isolated_app_all } sysfs_gpu:file r_file_perms;`)، و`r_dir_file(domain, sysfs_devices_system_cpu)` + `allow domain proc_cpuinfo:file r_file_perms` تُبيح مسارات CPU للجميع، و`proc_meminfo` ممنوح **ومُعلَّم بالتقاعد** (`# TODO: switch to meminfo service`). ⇒ ينتج **رتبة مصدر** (EXPECTED / DEVICE_DEPENDENT / EXPECTED_DENIED) بدل أن يُقرأ كل رفض كعطب في الكود.
3. **حقيقة صعبة عن الحالة الحالية:** `grep -rln 'core.atlas'` على `manager/app/src/main` = **صفر** ⇒ كل «المسلَّم» وحدةً معزولة بلا نقطة استدعاء واحدة. **⟪مُصحَّح في تكملة ٥٣: الأمر يُعيد ٨ ملفات (سبعة داخل حزمة أطلس + الحدّ)؛ والصواب أنَّ **نقطة الاستدعاء** صفر، لا أن **الذكر** صفر.⟫** هذا مقصود في الخطة، لكنه يعني أن أكبر فجوة ليست العمق بل **الوصول**.
4. **عطب بنيوي في مبدأ الكتالوج:** قواعد المسار تسمح بـ`basename` واحد فقط، لذلك `cpufreq/policy*/stats/time_in_state` و`zram*/mm_stat` و`cooling_device*/cur_state` و`block/*/stat` غير قابلة للتعبير — بينما `AtlasPlatformProvider` **يبنيها يدويًّا** خارج الكتالوج. أي أن «الكتالوج هو المصدر الوحيد للمعرفة» مخروق عمليًّا، لا ناقص ميزة فقط.
5. **أخطر فئة فشل لم تُعالَج بعد: القيمة المُجمَّدة الكاذبة** (عقدة تُقرأ بنجاح وتُعيد ثابتًا للأبد) — أخطر من الفشل لأنها تبدو صحيحة. اقتراح I-12 يعالجها كـ*محور جودة* (`FROZEN_SUSPECTED/CONTRADICTED`) **دون** إعادة كتابة القيمة الخام، بشرط `semanticStatus = INFERRED`.
6. **أهم فكرة عملية: «طبيب أطلس» (I-13)** — تشغيل ذاتي ينتج تقريرًا مُنقَّحًا بصيغة **توافق fixture**، فيُحوَّل قيد المشروع الأكبر («لا جهاز في الحلقة») إلى قناة نمو: كل بلاغ جهاز يصبح حالة اختبار دائمة. السابقة: `battery-historian` (تقرير → تحليل) و`hwdb` في libinput (قاعدة نصّية قابلة للصيانة + أداة تشخيص).
7. **أربع خطط مقترحة `P10`–`P13`** (هوية من واجهات عامة · قاعدة quirks تُخفض الثقة فقط ولا ترفعها · تكلفة/إحداث صلاحية وذاكرة سلبية · القياس على جهاز) **بانتظار قرارك**؛ ولم يُمسّ أي نص في `P5`–`P9` ولا قوائم نطاقاتها.

**RESIDUAL RISK:** لا جهاز ⇒ كل «ما يقرأه تطبيقك فعلًا» = `needs device` (§8 في الوثيقة يحمل أوامر القياس جاهزة) · لم تُفتح ملفات الرخص ⇒ **لا استيراد أي كود/جدول مسارات/بروفايل**، مراجع معمارية فقط · `/proc/pressure` **غير محسوم**: لا `neverallow` ولا `allow` وُجد له في الملفات المقروءة، وقراءة `MemoryStall.kt` له ليست دليلًا على دعم المنصة · `private/domain.te` وصل مبتورًا ⇒ كل «لم أجده» مكتوب كذلك لا «لا يوجد» · `P2`/`P4` لا يزالان `UNREVIEWED`.

**NEXT:** قرارك على `P10`–`P13` (أوصي: `P10` ← `P12` ← `P13` ← `P11`، ثم `P3`/`P5` كما في الخطة) — أو تمرير حزمة مراجعة مستقلة على `P2`+`P4`، وهي شرط إغلاق §6.

---

## تكملة ٥٣ — `ATLAS` P10+P12: هوية معلَنة · عمر أدلة · ذاكرة سلبية — ومنع نقطة استدعاء واحدة — 2026-09-20

**TASK:** «نفذ» — تنفيذ ما أوصت به مراجعة الفجوات (تكملة ٥٢). نُفِّذت **`P10`** و**`P12`** أولًا لأنهما
الوحيدتان القابلتان للإكمال **بلا جهاز**، ولأن ما بعدهما يعتمد عليهما: `P10` تُغذّي مطابقة `P11`
بالهوية، و`P12` تُغذّي ذاكرة `P5` بأعمار الأدلة وسياسة المحاولات.

**FILES:**
- **جديد (منتج):** `core/atlas/AtlasDeviceIdentity.kt` · `AtlasFreshness.kt` · `AtlasFailureLedger.kt` ·
  `AtlasProbeSchedule.kt`.
- **معدَّل (منتج):** `core/atlas/AtlasCatalog.kt` (اعتماد `REVIEWED_FILES` + بذرة `cpu.info.cpuinfo`) ·
  `AtlasPlatformProvider.kt` (وسيط هوية واحد).
- **جديد (اختبار):** `AtlasDeviceIdentityTest.kt` · `AtlasFreshnessTest.kt` · `AtlasFailureLedgerTest.kt`.
- **معدَّل (اختبار):** `AtlasCatalogTest.kt` · `AtlasPlatformProviderTest.kt` · `support/AtlasBudgets.kt`
  (تفويض لا نسخة ثانية).
- **وثائق:** `01-PLAN.md` (§19 + Amendment A-4 + تصحيح §18 + سجل) · `01-GAPS-AND-IDEAS.md` (تصحيح G-01) ·
  `ROADMAP.md` · `STATE.md` · `REQUIREMENTS.md` · `NEXT_TASK.md` · هذا الملف.
- **لم يُمَسّ:** `P3`/`P5`–`P9` وقوائم نطاقاتها، ولا سطر واحد خارج نطاق `P10`/`P12`.

**GATES:** §1 ✓ · §2 (لا أثر مرئي: لا مستدعي — أدناه) · §3 (لا نصوص جديدة ⇒ لا `values-ar`) · §4 (لا واجهة) ·
§5 ✓ (١٢٨ اختبارًا) · §6 ✓ (لا كتابة sysfs: حارس المصدر يفشل مغلقًا ويمسح التعليقات والنصوص قبل الفحص) ·
§7 ✓ (بإذن المالك) · §8.

**BUILD:** `bash gradlew :app:testDebugUnitTest --tests 'nd.max.core.atlas.*' --tests 'nd.max.core.hardware.ReadOnlyProbeAccessTest'`
(وأُعيد التشغيل بعد تصحيح السادس أدناه — الرقم النهائي ١٣١/٠)
= **BUILD SUCCESSFUL 2m30s · ١٣١ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** في ٧ أصناف (كان ٧٤ ⇒ ‎+٥٧‎):
`AtlasCatalogTest` ١٧ · `AtlasDeviceIdentityTest` ١٦ · `AtlasFailureLedgerTest` ١٨ · `AtlasFreshnessTest` ٢٠ ·
`AtlasHarnessTest` ١٠ · `AtlasPlatformProviderTest` ٢٣ · `ReadOnlyProbeAccessTest` ٢٧.
وإعادة تصريف كاملة بـ`--rerun` (`:app:compileDebugKotlin :app:compileDebugUnitTestKotlin`) = **BUILD SUCCESSFUL 2m42s**
و**صفر تحذير من أي ملف من ملفات أطلس**؛ تحذيرات التطبيق الأخرى قائمة كما كانت (لم تزد ولم تُلمس).
البوابات الثابتة: `code_health --assert` exit 0 والدَّين عند سقفه `10/29/66/26` · `i18n_coverage --assert`
exit 0 · `kt_balance` **٦٧٧ ملفًا / ٠ عوائق** · `repo_audit` **PROBLEMS: 0** · `git diff --check` نظيف.

**خمسة إخفاقات في أول تشغيل — وكلها لها سبب مكتوب لا مُبرَّر:**
1. **عطب منتج حقيقي:** الأسماء المعلَنة كانت تقبل محارف تحكّمية، ومفتاح الذاكرة يفصل حقوله بمحرف — فكان
   يمكن **تزوير حدّ حقل** داخل المفتاح. أُضيف الفحص لأن الاختبار طلبه، لا لأن أحدًا لاحظه بالقراءة.
2. **عطب تصميم حقيقي:** سجل الفشل كان **يمسك** جيل الإقلاع قيمةً، فصار فرعه الذي يقول «إقلاع آخر يُبطل
   هذا» **غير قابل للوصول** وهو يبدو صحيحًا. صار الجيل **دالّة**.
3. **ثابت قديم في اختبار قائم:** كان يؤكّد أن جذر كل مدخل جذر مُعتمد، وهذا ما غيّرته `REVIEWED_FILES`
   عمدًا. لم يُخفَّف التوقّع بل **قُوّي**: صار يسأل سؤال **القابلية للعنونة** (سؤال واحد يجمع الكتالوج
   والحدّ).
4. و5. **خطآن في توقّعي أنا:** قائمة رموز خام كنت أظنها معرِّفات، وحالة «مفتاح مختلف» استخدمت القيمة
   الافتراضية فقارنت المفتاح بنفسه.

**وسادس — من تدقيق ادّعاءات هذا التقرير على الكود (أهمّ ما في الجولة):** جملة «‏`AtlasFailure.STALE` صار
له مُنتِج» كانت **غير صحيحة**. الوحيد الذي يذكره في `AtlasFreshness.kt` تعليق يدّعي وجود مُنتِج، ولا سطر
يُنتجه: لا في `AtlasFreshness` ولا `AtlasProbeScheduler` ولا الحدّ. و`AtlasFailureLedger` تُعلّل بنفسها
لماذا لا يجوز أن يُنتجه **نداء قراءة** («نحمل قيمة قديمة» ≠ «فشل القراءة»)، فالمحلّ الصحيح للمُنتِج هو
مستودع `P5` حين يخدم دليلًا محتفظًا به — و`P12` **لا تتظاهر بأنها هو**. أُصلح الادّعاء في الخطة §19،
و`01-GAPS-AND-IDEAS.md` (G-02 صار له حالة صريحة: «نصف مُغلق» ومَن يُغلق الشقّ الثاني)، و`STATE`،
و`NEXT_TASK`، و`REQUIREMENTS`، وهذا التقرير.
**والعطب الحقيقي الذي وجده التدقيق بجانب ذلك:** `isStaleAt` كانت **تطرح السبب** وتُعيد `Boolean`، بينما
حالات مختلفة أربع تُطوى في كلمة «قديم» — وواحدة فقط منها ساعة. وهذا نقيض ما يفعله باقي أطلس: `P2` يحفظ
الأسباب الأحد عشر كلها لأن التقرير يجب أن **يسمّي واحدًا**. فأُضيف `AtlasStaleness`
(`FRESH` · `EXPIRED_BY_TIME` · `SUPERSEDED_BY_BOOT` · `SUPERSEDED_BY_PRIVILEGE` · `UNMEASURABLE_CLOCK`)
ب**أسبقية ثابتة** (إقلاع ← صلاحية ← ساعة) حتى لا يكون السبب «أول ما اختبره المُستدعي»، و`isStaleAt` صارت
ملخّصًا له فلا تتغيّر دلالة أي مستدعٍ قائم، وثلاثة اختبارات تُثبّت كل فرع وتُثبّت التكافؤ على مصفوفة
٢٢٤ حالة (٤ تقلّبات × ٢ أوقات رصد × ٧ استعلامات × ٢ × ٢ أجيال). من ١٢٨ إلى **١٣١ اختبارًا**، وإعادة
تشغيل كاملة: **BUILD SUCCESSFUL · ٠ فشل**، وصفر تحذير من ملفات أطلس (وخطّ `--rerun` الكامل للتصريف = 2m42s).

**وتصحيح لِما كتبتُه أنا في تكملة ٥٢ (بند ٣):** لم يكن صحيحًا أن `grep -rln 'core.atlas'` على
`manager/app/src/main` = صفر. الأمر يُعيد **٨ ملفات**، سبعة منها داخل `core/atlas/` تشير إلى حزمتها
والثامن `core/hardware/ReadOnlyProbeAccess.kt` يستورد **مفردات** أطلس بلا استدعاء. والحقيقة التي تصمد،
وهي الآن المكتوبة في `01-GAPS-AND-IDEAS.md` G-01 وفي الخطة §18/§19.4: **صفر نقطة استدعاء** — لا شيء
ينادي `ReadOnlyProbeAccess`، وملف واحد فقط خارج `core/atlas/` يذكر نوعًا من أنواع أطلس وهو الحدّ نفسه.
سبب الخطأ: بحثتُ عن **الاستيراد** وسمّيتُه **الاستدعاء**، وهو فرق ليس لفظيًّا (المستورَد بلا مستدعٍ غير
المستورَد المُشغَّل). أُصلحت المخرجات الثلاثة ولم تُمَس نصوص `P5`–`P9`.

**RESIDUAL RISK:**
- **لا جهاز:** كل عمر وكل سياسة إعادة محاولة **قيمة تصميمية**؛ `P2/T2.5` و`P3` يبقيان `UNREVIEWED` حتى
  تتوفّر مراجعة من عائلة نموذج مستقلة — وهذا غير متاح في هذه الجولة.
- **لا مُهايئ:** لا شيء يقرأ `Build`/`Os`/`ActivityManager` بعد، فالهوية نموذج مُعلَن **بلا مُنتِج**، تمامًا
  كما النقل ومُهايئ المنصّة.
- **لا ذاكرة:** `AtlasFreshness`/`AtlasFailureLedger`/`AtlasProbeScheduler` قواعد مختبَرة؛ **المخزن** الذي
  يستعملها هو `P5` ⇒ «`ATLAS-06` فيه أدلة جزئية» لا «مُلبّى».
- **لا مرتفعات:** قاعدة الـquirks ورتب الأسبقية (`EXPECTED`/`DEVICE_DEPENDENT`/`EXPECTED_DENIED`) لم
  تُبنَ — مكانها `P11`، ويجب أن تلي `P13` وإلا صارت قاعدة **تخمينات**.
- **تحذير قائم لم يُمَس:** `MemoryStallTest.kt:122` (خارج نطاقي، ومُسجَّل لا مُصلَح).

**NEXT:** `P3` (إعادة استخدام CPU/GPU — يمسّ `core/hardware` ⇒ `UNREVIEWED`) ثم `P5` (المستودع + DI،
وهو الذي يُغلق `ATLAS-03`/`ATLAS-06` فعليًّا)، وبعده أول **نقطة استدعاء** في المنتج (`P7`) — لأن أهمّ ما
تكشفه هذه الجولة أن أطلس كامل مكتمل وحدةً و**لا أحد يستدعيه**.

---

## تكملة ٥٤ — `ATLAS` P7 + P8 + P9.2: سطح مرئي · بنك ثانٍ للإكمال التلقائي · وحارس معماري — وعطبٌ في ١٢ مدخلًا مراجَعًا — 2026-09-20

**TASK:** أمر المالك: «اكمل كل شيء دفعة واحدة، وخذ من SmartPack وغيرها من مشاريع مفتوحة المصدر معرفتهم،
لأنهم يجمعون هذه الأشياء لسنوات، فإذا ما هو مخزّن عندنا لا يعمل يقوم أطلس بإكماله تلقائيًا». فما ميكن
إغلاقه بلا جهاز: `P7` (سطح أطلس) · `P8` (مدخل بلا جذر) · `P9.2` (الحارس المعماري) — ثم البناء الكامل.

```
TASK: P7 (سطح أطلس) + P8 (مدخل بلا جذر) + P9.2 (حارس معماري) + تصحيح عطب الكتالوج
FILES: جديد منتج — core/atlas/AtlasCommunityBank.kt · ui/viewmodel/AtlasViewModel.kt ·
       ui/mainscreens/AtlasDiagnosticsSection.kt
       معدَّل منتج — core/atlas/AtlasCatalog.kt (AtlasCatalogScope + childPrefix + ENUMERABLE +
       validate/version–schema) · AtlasDiscovery.kt (بنكان · تعداد الأبناء · replan) ·
       AtlasResolver.kt (CANDIDATE_INTERFACE + volatilityForDomain + stage) ·
       AtlasRepository.kt (مرحلة الإكمال + reviewedUnresolved) · core/di/DataModule.kt ·
       ui/mainscreens/DiagnosticsScreen.kt · ui/mainscreens/SettingsScreen.kt ·
       res/values/max_screen_strings.xml و values-ar/ (٧٦ مفتاحًا لكل لغة)
       جديد اختبار — AtlasChildScopeTest · AtlasCommunityBankTest · AtlasCompletionTest ·
       ui/viewmodel/AtlasPresentationTest · ui/navigation/AtlasReadOnlyEntryTest · AtlasArchitectureTest
       معدَّل اختبار — AtlasCatalogTest (قاعدة القواعد) · AtlasResolverTest (تصحيح اختبار مذبذب)
       أداة — tools/code_health.py (إزالة إيجابية كاذبة في TEXT_LITERAL)
GATES: 1 ✓  2 ✓  3 ✓(ar parity: ٧٦/٧٦ · عوائق ٠)  4 ✓  5 ✓  6 ✓
BUILD: BUILD SUCCESSFUL 6m26s — :app:testReleaseUnitTest + :app:assembleDebug -x lintVitalRelease
       **١٠٧٠ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** · APK ‎121,071,768‎ بايت
RESIDUAL RISK: لا جهاز (كل ما يُقرأ على عتاد حقيقي = needs device) · P9.1 (fixtures) لم يُنفَّذ ·
       P2/T2.5 و P3 بلا مراجعة من عائلة أخرى · وحدات المداخل المرشّحة (Adreno/MTK) CLAIMED لا مُتحقَّقة
NEXT: P13 (طبيب أطلس: تقرير بصيغة fixture) ← ثم P11 مبنية على بلاغات حقيقية
```

### ١. العطب الذي كُشف **قبل** كتابة أي سطر جديد

**١٢ من ١٥** مدخلًا في الكتالوج المراجَع كانت تخاطب **مجلد صنف** كأن الملف داخله مباشرة:
`/sys/class/devfreq/cur_freq` · `/sys/class/thermal/temp` · `/sys/class/power_supply/charge_full` ·
`/sys/class/kgsl/…` · `/sys/devices/system/cpu/cpufreq/scaling_cur_freq` · `/sys/block/disksize`. وعلى كل جهاز
حقيقي هذه المجلدات تحمل **أجهزة**، والواجهة تقع في مستوى أدنى — فالمسارات كانت تُشير إلى ملف لا يمكن أن
يوجد **بينما الواجهة التي تصفه موجودة وتُقرأ**. ولا شيء في القواعد كان يرفض هذا الشكل، ولا اختبار كان يراه:
النصوص تُصدَّر بلا خطأ وتُحلّ إلى لا شيء.

**والتصحيح قاعدة لا اثنتا عشرة تعديلة:** `AtlasCatalogScope` (`ROOT_FILE` / `CHILD_FILE`) + `childPrefix`
اختياري (شظيّة اسم تُقارن بأسماء أعادها النواة — **لا نمط ولا glob**)، و`AtlasAnchors.ENUMERABLE` منحة
جديدة: جذر يُسار فيه أقوى من قراءة ملف معلوم داخله، و`PUBLIC_API` مستثنى لأنه بلا أبناء وبلا ناقل ملفات.

### ٢. البنك الثاني — «الإكمال التلقائي» بحدوده المكتوبة

`AtlasCommunityBank`: **٥٠ واجهة** تمرّ بنفس مدقّق الكتالوج المراجَع، بلا كود ولا جدول مسارات ولا معامل
تحويل منسوخ من المشاريع المذكورة (S01/S09 مسجّلان في `provenance` كجرد، وسجلّ ترخيصهما مكتوب). والثقة لا
تتجاوز `CLAIMED`/`FETCHED` — ولا مدخل `SOURCE_VERIFIED`. والقراءة منه `INFERRED` بمرحلة
`CANDIDATE_INTERFACE`، ولا تُرقّى أبدًا. و**لا يُسأل إلا عن نقض**: النطاقات التي لم يُجب عنها البنك الأول،
بلا إعادة قراءة واجهة قُرئت، بحدّ ٣٢ مرشّحًا. والقرار مكتوب في **ADR-37** مع البديل المرفوض (نسخ خريطة
مسارات SmartPack: خلط GPL-3.0 بـApache-2.0، و«تخمين عند الفشل»، وتحويل غياب الاسم إلى غياب الواجهة).

### ٣. قواعد العرض كدوال صافية — لأنّ ما لا يفشل اختبارًا ينكسر صامتًا

`AtlasPresentation` (في ملف الـViewModel، مُختبَر في `AtlasPresentationTest`) يثبّت: المجموع المجهول
`null` ولا يُكتب `0%` · القراءة الواحدة `Snapshot` لا `Live` أبدًا · ولا حالة `Applied` أصلًا في تعداد
الأحوال (`entries.none { it.name.startsWith("Appl") }`) · والإلغاء ≠ الانتهاد · والقراءة المرشّحة لا
تُعدّ مراجَعة. والتقرير: معاينة (بايتات مُجمَّدة) ← تأكيد ← منتقي النظام، بلا اختصار حافظة.

### ٤. ثلاثة عيوب أخرى كشفها التشغيل والتفتيش (لأجلها كُتبت الجولة)

| # | ما ظهر | أين أُصلح |
| --- | --- | --- |
| ١ | `hardcoded_ui_literals` ارتفع بإيجابية **كاذبة**: `\bText\(` تطابق `AtlasTransportRead.Text("42000\n")` — أي أنّ كتابة اختبار كانت ترفع «نصًّا واجهة صلبًا» | أُصلحت **الأداة** (`(?<![\w.])`) لا الكود السليم — والسقف عاد ٦٦ كما هو، مع ثلاث حالات تحقّق من أن الحارس ما زال يرى `Text("…")` الحقيقي |
| ٢ | اختبار قائم `cancellation propagates…` كان **مذبذبًا**: يُكمل `CompletableDeferred` من خيط الاختبار فيسبق الإلغاءَ أحيانًا ⇒ يمرّ أو يفشل بالحظّ | صار `awaitCancellation()`: لا مخرج إلا الإلغاء — عطبٌ في الاختبار لا في المنتج، لكنه كان يمنح ثقة كاذبة |
| ٣ | إضافة `complete` بعد `discover` كانت ستقلب **اللامدا المتأخّرة** في كل نقطة استدعاء قائمة (تُصرّف خطأً هنا، لكن الفخّ حقيقي) | `complete` قبل `discover` مع تعليق يشرح السبب |

### ٥. ما لا يُدّعى في هذه التكملة

- **لا جهاز ولا محاكي:** ما يُقرأ فعلًا على نواة مصنّع — ومنه وحدات Adreno/MTK المسجّلة `CLAIMED` — يبقى
  `needs device`. والبناء يعني «يترجم وتمرّ الاختبارات» لا «يعمل على هاتفك».
- **`P9.1` (fixtures) لم يُنفَّذ**، ومساره مسجّل كنقص لا كتبديل مُغلق.
- **`P8` نصفُه متحقّق:** الوصول مُثبَت على مستوى المصدر والرسم (وجهة مسجّلة + مدخل في شاشة + لا صلاحية
  افتراضية)، أما «ضغطة إصبع تفتح الشاشة» فتبقى `needs device`.
- **`P7` لم يُراجع بقية شاشة التشخيص:** `HardwareReportCard` (توليد ونسخ فوري) تُركت كما هي لأنّها تُنتج
  مخرَجًا آخر، وتبديلها يخصّ تصميم ذلك المخرَج لا أطلس.
- **تحذير قائم** `MemoryStallTest.kt:122` لم يُمَس (خارج النطاق) — ولم تُضف الجولة أيّ تحذير جديد (تحقّق:
  صفر تحذير من ملفات أطلس في تصريف نُسخة release، بعد إزالة `as` زائدة في اختبار قديم).

### ٦. وفي الدفعة نفسها قبل `P7`: `P3` و `P5` و `P6` — **وسجلّها كان متأخّرًا، وهذا تصحيحه**

الجولة السابقة نفّذت `P3` و`P5` و`P6` وقاستهما، **لكنها لم تُسجّلهما هنا** حتى هذه التكملة. هذا نقص في
السجلّ لا في العمل، ويُصحّح الآن بما قيس فعلًا:

| الخطة | ما سُلِّم | القياس المسجّل وقتها |
| --- | --- | --- |
| `P3` | `AtlasBackendProvider` يربط محلّلات CPU/GPU القائمة بأطلس بلا قراءة ثانية، ومنفذ قراءة مُضاف إلى `CpuHardwareBackend`/`GpuHardwareBackend` بلا مسّ أي كاتب قائم، واختبار يثبت أنّ القراءة لا تكتب | **١٨٤ اختبارًا · ٠ فشل** · و**عطب حقيقي** كشفه التشخيص: `GpuHardwareBackend` كان يقبل نصًّا مُشذّبًا سلفًا فقط، فأي قارئ آخر كان يفقد الساعة صامتًا — أُصلح المحلّل لا الاختبار |
| `P5` | `AtlasResolver` (مراحل مرتّبة · إلغاء يُعاد رميه لا يُفسَّر) · `AtlasEvidenceStore` (JSON صريح الحقول، كتابة ذرّية، تالف ⇒ يُطرح ويعاد المسح) · `AtlasRepository` (مسح واحد مُوحَّد · إلغاء يُنشر فورًا ويتفوّق على إجابة متأخّرة) · `AtlasFileReadTransport` · `AtlasFileStoreIo` · `DataModule` | **٢٢٧ اختبارًا · ٠ فشل**، ومنها **٢٣ اختبارًا يُشغَّل على نظام التشغيل الحقيقي** (`AtlasRealReadIntegrationTest` + `AtlasFileReadTransportTest`) لا على وهم: يحلّ روابط رمزية، ويقرأ ملفات حقيقية، ويقيس `canonicalPath`. وكشف التشغيل الحقيقي **عطب إحداثيات**: `canonicalPath` كان يُعيد مسارًا من جذر المضيف فتُقرأ نفس العناوين بمضاعفة البادئة — أُصلح الناقل |
| `P6` | `AtlasSupportReport` (مخطط مُصغَّر مُرقَّم، رفض ما لا يُفهم، حدّ ٢٥٦ KiB قبل وجود الملف، معاينة = بايتات المشاركة) · `AtlasReportExporter` (كتابة ذرّية · تنقية بالعمر · لا شبكة ولا رفع) · `DiagnosticCenter.structured()` (إسقاط بلا حقل رسالة أصلًا) | أخضر، مع اختبارات «الطُعم» (canaries) في كل حقل خام وفي كل استثناء |

**ولماذا يُكتب هذا الآن:** كان في المستودع كود مُختبَر بلا سجلّ، وهذا أسوأ من عدم التنفيذ لأنه يُقرأ
لاحقًا على أنه غير موجود فيُعاد من الصفر (`AGENTS.md` §0). وأُضيف في التكملة نفسها: `P9.1` (fixtures) هو
الوحيد من `P0`–`P9` الذي بقي غير منفَّذ، و`P2/T2.5` يبقى موقوفًا على مراجعة سلامة مستقلة.

## تكملة ٥٥ — `ATLAS` P9.1 + P13: الـfixtures وعيادة أطلس — تغطية لا تنتهي بـ«يحتاج جهازًا» — 2026-09-20

**TASK:** إكمال ما بقي بلا جهاز: `P9.1` (fixtures) — الوحيد الذي لم يُنفَّذ من العشر الأصلية — و`P13`
(طبيب أطلس) الذي كان يجب أن يسبق `P11` لتبنى قاعدة الـquirks على بلاغات لا على تخمين. الفكرة الحاكمة:
كل جولة سابقة كانت تنتهي بـ«needs device» إلى الأبد؛ فصار ما قرأه جهاز حقيقي على مرةٍ ما **بياناتٍ
قابلةً لإعادة التشغيل**، لا فقرةً في تقرير.

```
TASK: P9.1 (fixture format + recorder + replay transport) + P13 (AtlasDoctor) + حارس معماري
FILES: جديد منتج — core/atlas/AtlasFixture.kt · core/atlas/AtlasFixtureRecorder.kt ·
       core/diagnostics/AtlasDoctor.kt
       معدَّل منتج — core/diagnostics/AtlasSupportReport.kt (نشر مفردات النتائج الأربع بدل نسخها)
       جديد اختبار — atlas/AtlasFixtureTest · atlas/AtlasFixtureRecorderTest ·
       diagnostics/AtlasDoctorTest
       معدَّل اختبار — atlas/AtlasArchitectureTest (١٥ ⇒ ١٧ ملفًا + الملفان في مسار القراءة +
       اختباران جديدان: عتاد وحدود)
GATES: 1 ✓  2 ✓  3 ✓  (عوائق ٠)  4 ✓  5 ✓  6 ✓
BUILD: BUILD SUCCESSFUL 4m51s — `:app:testReleaseUnitTest` + `:app:assembleDebug -x lintVitalRelease`
       **١١٠٣ اختبارات · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** (كان ١٠٧٠ ⇒ ‎+٣٣‎) · APK ‎121,071,768‎ بايت
       ونطاق أطلس والتشخيص داخل التشغيل نفسه = **٢٢ صنفًا · ٣٠٦ اختبارًا · ٠ فشل**
       والنطاق المُركَّز (`:app:testDebugUnitTest` بنفس الأصناف) = BUILD SUCCESSFUL 2m54s · **٢١ صنفًا ·
       ٢٧٩ اختبارًا · ٠ فشل** · والتحذير الوحيد في التشغيلين هو `MemoryStallTest.kt:122` القائم (خارج
       النطاق ولم يُمَس) — صفر تحذير من أي ملف لُمس
RESIDUAL RISK: لا fixture من هاتف حقيقي (الموجود لقطات HOST وحدها، ومسمّاة كذلك) · لا جهاز ولا محاكي
       هنا ⇒ T9.4 مكتوب لا مُنفَّذ · P11 غير مبنيّة · P2/T2.5 غير مبنيّ و P2/P3/P4 بلا مراجعة عائلة أخرى
NEXT: P11 على بلاغات حقيقية (وصار لها آلية)، أو مراجعة سلامة مستقلة لـP2/P3/P4
```

### ١. لماذا هذه الجولة ليست «اختبارات أكثر» بل نهايةً لنمط

الفرق بين «عندنا fake» و«عندنا fixture» هو الفرق بين اختبار ما توهّمه كاتب الاختبار واختبار ما
أجابته النواة فعلًا. سابقًا كل ادّعاء عن جهاز غير موجود كان يُختبر على وهم **كتبته أنا**، وهذا اختبار
لافتراضاتي. الآن `AtlasFixture` يسجّل **البايتات** التي وصلت للناقل الشحن، ويعيد تشغيلها عبر
`AtlasTransportRead`/`AtlasTransportList` نفسها — فلا حدّ قراءة ولا ميزانية ولا تحقّق أثر ولا حلّ ولا
تقارير تُختبر على غير الشحن.

### ٢. القواعد الأربع التي تجعل الإعادة ذات قيمة — وكل واحدة لها اختبار

| القاعدة | لماذا | القياس |
| --- | --- | --- |
| **مسار غير مُسجَّل ليس «غائبًا»** | صمت الـfixture لا يُثبت شيئًا عن الجهاز، فيُجاب `UNKNOWN_CAUSE` | `a replay of an unrecorded path is an unknown cause, never an absence` |
| **الغياب يُثبت بالطريقة الوحيدة التي يقبلها الحدّ** | تعداد **هذا التشغيل** نفسه؛ وfixture يُصرّح `ABSENT` لمسار لم يُعدّد أبوه يُرفض | `the boundary accepts an absence a replayed enumeration proved…` |
| **الأصل مُسجَّل لا مُفترَض** | `DEVICE` / `HOST` / `SYNTHETIC` — لقطة المضيف تُثبت الآلة، لا الهاتف | `a captured run and its replay produce identical boundary results` |
| **الصيغة ترفض ما لا تضمنه** | schema غائب `MALFORMED` · schema مجهول `UNSUPPORTED_SCHEMA` · حجم زائد · مسار غير آمن أو خارج المراسي · اسم غير آمن في تعداد · مسار مكرّر | ستة اختبارات في `AtlasFixtureTest` |

**والاختبار الذي هو سبب الملف:** `AtlasFixtureRecorderTest` يبني شجرة حقيقية ويشغّل **الطلبات الثلاثة
نفسها مرتين** — مرة على `AtlasFileReadTransport` الشحن ومرة على الـfixture المُسجَّل منه — ويؤكّد أن
قائمتي `AtlasReadResult` **متطابقتان عنصرًا بعنصر**. فأي حقل يُسقط، أو سبب يُعاد كتابته، أو تعداد
يُعاد ترتيبه ⇒ يسقط الاختبار.

### ٣. `P13`: سؤال واحد بحكم صريح

`AtlasDoctor` يجيب: هل يُعيد الجهاز المُعاد تشغيله إنتاج التقرير الذي جاء منه؟ وهو **صافي**: لا جهاز،
ولا ملف، ولا مسح. وحكمه `Reproduced` أو `Diverged` (بفروق مسمّاة الطرفين ومفروزة بالمعرّف) أو
`Refused`. وثلاثة قرارات فيه تحمي من الكذب على النفس:

- **تشغيل لم يكتمل أو أُلغي يُرفض ولا يُقارَن** — وإلا سُجِّل عطب المشغّل كتغيّر سلوك.
- **مراجعة كتالوج مختلفة تُرفض** — نتائج تحت معرفة مختلفة ليست إعادة إنتاج لأي شيء.
- **غياب واجهة مرشّحة نصيحة لا انحراف** — البنك الثاني خط دفاع، وغياب اسم يعرفه على هذا الجهاز هو
  الحالة المتوقّعة، فلا يُفتح به ملف دعم.

### ٤. أربعة عيوب كشفها التشغيل، وكل واحد أُصلح في طبقته

| # | ما أظهره التشغيل | أين أُصلح |
| --- | --- | --- |
| ١ | المُسجِّل سجّل **الرفض نفسه مرتين** (البوابة تُسأل مرة لكل عملية) | البوابة تُسأل **مرة لكل مسار** في `record` — فسطر رفض مكرّر يُقرأ كأن شيئين اختلفا |
| ٢ | `{}` أُبلغ عنه `UNSUPPORTED_SCHEMA` — أي إصدار لم يُكتب أصلًا | schema غائب ⇒ `MALFORMED`؛ ومختلف ⇒ `UNSUPPORTED_SCHEMA` |
| ٣ | اختباري أنا توقّع أن `ABSENT` المصرَّح به يمرّ رغم أن الأب لم يُعدّد | **الاختبار** كان خطأ والحدّ كان محقًّا — وصار الاختبار يُثبت الرفض |
| ٤ | حارس `P9.2` كان يثبّت **١٥** ملفًا في حزمة أطلس، فكان سيمرّ على الملفين الجديدين بلا قراءتهما | صار يثبّت **١٧** ويقرأ الملفين، فالعدد لا ينزلق بصمت |

وأُصلح **تكرار** كان سيصير انزلاقًا: مفردات النتائج (`observed`/`unresolved`/`suppressed`/`cancelled`)
كانت `private` في `AtlasSupportReport`، فكان الطبيب سينسخها؛ نُشرت وصارت الطبيب يستوردها، لأن نسختين
تنزلقان، وانزلاقهما يجعل الطبيب يبلّغ عن **تهجئة** كأنها سلوك.

### ٥. ما لا يُدّعى في هذه التكملة

- **لا fixture من هاتف حقيقي.** كل لقطة موجودة اليوم `HOST` من الاختبارات نفسها، ومكتوب عليها ذلك.
  تغطية الأجهزة تبدأ حين يُشغّل أحدهم الطبيب على جهاز ويحفظ الملف — ولا تبدأ بملف كتبته أنا.
- **`P11` ليست مبنيّة**؛ صارت ممكنة البناء على بلاغات حقيقية، وهذا كل ما يُقال.
- **`P2/T2.5` غير مبنيّ، و`P2`/`P3`/`P4` بلا مراجعة من عائلة نموذج أخرى.**
- **`P9` أُغلقت كـ`P9.1`+`P9.2` لا كبروتوكول جهاز:** بروتوكول `T9.4` مكتوب ولم يُنفَّذ — لا جهاز هنا.

## تكملة ٥٦ — `HOME-03`: مقياس تردّد CPU/GPU · «الآن» في الطيف · طيف بمسارَين — 2026-09-20

**TASK:** طلب المالك على الشاشة الرئيسية: إرجاع شريط CPU/GPU الذي يُظهر التردّد، وإرجاع الحمل «حالًا»
لا المتوسط والذروة فقط، وتغيير تصميم شريط طيف الحمل.

```
TASK: HOME-03 — مقياس تردّد + «الآن» في الطيف + إعادة تصميم الطيف
FILES: جديد منتج — ui/util/ClockMeterModel.kt
       معدَّل منتج — ui/component/NeuralDashboardKit.kt (NeuralFrequencyMeter جديد · NeuralBarSpectrum
       استُبدل بـNeuralLoadRibbon · NeuralKpiTile صار يقبل خانة meter)
                    ui/mainscreens/LegendaryHomeDashboard.kt (TrendDuo · SpectrumPanel · مفتاح اللون
                    · frequencyCeilingLabel)
                    ui/viewmodel/HomeDashboardViewModel.kt (cpuTopCoreMhz · cpuCeilingMhz · gpuCeilingMhz
                    + قراءة السقف مرة لكل إقلاع)
                    res/values/strings.xml و values-ar/ (٣ مفاتيح: home_stat_now · home_freq_ceiling ·
                    home_freq_ceiling_unknown)
       جديد اختبار — ui/util/ClockMeterModelTest.kt (٨ اختبارات)
GATES: 1 ✓  2 ✓  3 ✓ (عوائق ٠ · en/ar متطابقان)  4 ✓  5 ✓  6 ✓
BUILD: BUILD SUCCESSFUL 2m7s — `:app:compileDebugKotlin` (**صفر تحذير من أي ملف لُمس**)
       BUILD SUCCESSFUL 3m — `:app:testDebugUnitTest --tests 'nd.max.ui.*'`
       **٦٥ صنفًا · ٦١٤ اختبارًا · ٠ فشل · ٠ خطأ · ٠ مُتخطّى**
       BUILD SUCCESSFUL 2m30s — `:app:assembleDebug -x lintVitalRelease` · APK ‎121,072,800‎ بايت
RESIDUAL RISK: الشكل البصري النهائي على جهاز (كثافة · سطوع · RTL بمقياس أفقي) لا يُتحقّق هنا · وأسماء
       السقوف (GED / devfreq / OPP) **تُقرأ** من النواة ولم تُقس على شريحة حقيقية
NEXT: HOME-02 (قياس على جهاز) — وقد أُضيف إليه بند المقياس الجديد
```

### ١. مقياس التردّد — الرقم وحده لا يكفي، وهذا سبب إرجاع الشريط

«٢.٤ جيجاهرتز» لا تقول إن كان ذلك **قريبًا من السقف أم بعيدًا عنه**، وهي المعلومة التي يُبنى عليها حكم
«أهذا الجهاز يخنق نفسه أم يعمل بطبيعته؟». فالمقياس يعرض الثلاثة معًا: القراءة، والسقف، ومدرّج من
**١٢ مقطعًا** بنقاط فصل تفصله عن شرائط النِسب العادية.

**وأربعة قرارات فيه ليست شكلية:**

| القرار | لماذا |
| --- | --- |
| رقم CPU = **أعلى تردّد حيّ** بين الأنوية المتصلة، لا `cpu0` | على big.LITTLE يبقى `cpu0` في أدنى درجاته بينما العمل على العنقود الرئيسي — فكان المقياس يرسم «هدوءًا» على جهاز مشدود |
| **لا سقف معلَن ⇒ لا تعبئة** (لا اختراع سقف) | أعلى قيمة رآها التطبيق ليست مدى الشريحة، وشريط مرسوم عليها يقيس تاريخ قياساتنا لا العتاد |
| سقوف تُقرأ **مرة لكل إقلاع** | `max_freq` ليست قياسًا لحظيًّا؛ وقراءتها كل دورتين = نداءات لا تُغيّر نتيجة |
| في RTL تبدأ التعبئة من اليمين | المقياس يقيس اتجاه القراءة، لا اتجاه كتابة الكود |

**وسلّم قراءة سقف GPU مكتوب صريحًا** (كما يمنعه `AGENTS.md` من الادّعاءات): `devfreq/max_freq` ← ثم
`available_frequencies` ← ثم جدول OPP (ومفاتيحه **بالهرتز** فتُقسم على `1_000_000`؛ أخذها كما هي يعطي
«2400000 MHz»). وما لم يُقرأ شيء فهي `null` صريحة.

### ٢. «الآن» — ولماذا لا يُعدّ تكرارًا كما بدا

الطلب «أرجع الحمل حالًا ليس المتوسط والذروة فقط» يُخالف قاعدةً كتبها هذا المشروع بنفسه («لا تُكرَّر
المعلومة في الشاشة نفسها»)، فالاختيار هنا **معلن**: صفّ الطيف صار `الآن` · `المتوسط` · `الذروة`، و«الآن»
يُقرأ من **الحالة الحيّة نفسها** التي يقرأ منها المربّعان أعلاه — فلا يمكن أن يختلف الرقمان (وهذا
علاجه: مصدر واحد)، والفرق أن المتوسط والذروة يصفان نافذة لا يرى المستخدم طرفيها، بينما «الآن» حقيقة
عن هذه اللحظة. وأُضيف المفتاح `home_stat_now` في اللغتين.

### ٣. الطيف — ما كان سيئًا بالضبط، وما صار

**العطب:** عمودا CPU وGPU كانا **متجاورين داخل الفتحة نفسها**، أي نصف فتحة لكل عمود. على ارتفاع
٩٦dp يعني ذلك أشرطة رقيقة على مسار رماديّ بطول الإطار — يُقرأ كباركود، وكل الفتحات حاملة لمسارٍ رماديّ
يوحي بأن الرسم «لم يكتمل».

**التصميم الجديد:** لكل حمل **مسار أفقي كامل العرض** — GPU أعلى (٣٤٪ من الارتفاع) وCPU أسفل، بفاصل
٧dp — فتُقرأ المقارنة بترتيب الصفحة نفسها. ومعها:

- **خطّ أساس** رقيق بدل المسار الرمادي الطويل ⇒ الامتلاء هو المعلومة، لا الفراغ خلفه؛
- **«مقعد»** للفتحة التي لم تصلها عيّنة (٢.٥dp) وفتحة الصفر لها **نُبَيضة بلون مسارها** — فالحالتان
  تختلفان في الصورة، وهما مختلفتان في الحقيقة؛
- **غطاء أدفأ على أحدث عيّنة** ⇒ موضع «الآن» في الرسم معروف بلا سهم ولا تسمية؛
- **مفتاح لون** (نقطة + تسمية) يظهر فقط حين يكون المساران موجودين، فلا يُخمَّن أيّ مسار لأي حمل؛
- وقواعد الهندسة السابقة **بقيت**: الإطار كامل العدد من الثانية الأولى، وتاريخ الجلسة يُستعاد من القرص.

### ٤. ما لا يُدّعى

- **الشكل البصري على جهاز**: المقاسات (٧dp للمقياس · ١٠٤dp للمسارَين) قيم تصميمية، ولم تُرَ على شاشة
  حقيقية؛ وRTL **مُنفَّذ في الكود** (قلب اتجاه التعبئة + تعليق الاسم LTR) ولم يُرَ على جهاز.
- **أسماء السقوف**: أي مسار ينجح (GED/devfreq/OPP) **لم يُقس**؛ هذا ما يذهب إلى `HOME-02`.
- ولا ادّعاء أداء: الطيف يرسم ٥٢ حركة عند التحديث كما كان، ولم يُقس زمن الرسم.
