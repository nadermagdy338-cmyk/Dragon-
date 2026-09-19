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
