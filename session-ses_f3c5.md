# تطوير Max أطلس في المشروع

**Session ID:** ses_f3c539be4ffe75jztLqF6tMSH7
**Created:** 9/21/2026, 11:14:10 AM
**Updated:** 9/21/2026, 11:56:21 AM

---

## User

hi

---

## Assistant (Code · GPT-6 Astra · 11.0s)

_Thinking:_

**Crafting a greeting**

I need to come up with a simple greeting that doesn't include overly complicated questions. When the user says hi, a straightforward "Hello" is acceptable. I want to avoid opening with any kind of interjection or commentary. Just starting with "Hi" works well. It seems that I should stick to this guideline and not add any fluff to my responses. So, a direct greeting like "Hi" is perfect!

Hi.

---

## User

اريد منك تطوير max اطلس في مشروعي

---

## Assistant (Code · GPT-6 Astra · 21.0s)

سأفحص مكوّن «Max أطلس» في المشروع ووضعه الحالي، ثم أحدد نطاق تطوير واضح قبل تعديل الكود، مع الالتزام بعدم تشغيل بناء تلقائي.


**Tool: todowrite**

**Input:**
```json
{
  "todos": [
    {
      "content": "استكشاف Max أطلس وتعليمات المشروع وتحديد نطاق التطوير المناسب",
      "priority": "high",
      "status": "in_progress"
    },
    {
      "content": "تنفيذ تحسينات Max أطلس ضمن النطاق المتفق عليه",
      "priority": "high",
      "status": "pending"
    },
    {
      "content": "تشغيل بوابات التحقق وتوثيق النتائج والقيود",
      "priority": "high",
      "status": "pending"
    }
  ]
}
```

**Output:**
```
[
  {
    "content": "استكشاف Max أطلس وتعليمات المشروع وتحديد نطاق التطوير المناسب",
    "status": "in_progress",
    "priority": "high"
  },
  {
    "content": "تنفيذ تحسينات Max أطلس ضمن النطاق المتفق عليه",
    "status": "pending",
    "priority": "high"
  },
  {
    "content": "تشغيل بوابات التحقق وتوثيق النتائج والقيود",
    "status": "pending",
    "priority": "high"
  }
]
```

---

## Assistant (Code · GPT-6 Astra · 26.9s)

**Tool: bash**

**Input:**
```json
{
  "command": "git status --short",
  "description": "Check current worktree for existing changes"
}
```

**Output:**
```
(no output)
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/HANDOFF.md",
  "limit": 180
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/HANDOFF.md</path>
<type>file</type>
<content>
1: # HANDOFF
2: 
3: From: Architect pass (Notion AI), 2026-09-15 — exploration, product vision, documentation. **No app code was modified in this pass.**
4: To: DeepSeek Harness (Executor)
5: 
6: ## Read order
7: 
8: 0. `../../AGENTS.md` — روستر الفريق وتوزيع النماذج وبروتوكول التسليم؛ ملفات الأدوار في `team/`.
9: 1. `PROJECT_STATE.md` — where the repo and the toolchain stand.
10: 2. `DESIGN_VISION.md` — the product direction you are implementing.
11: 3. `DECISIONS.md` — binding rules (ADR-01–18).
12: 4. `NEXT_TASK.md` — **NT-01**, the task to execute now.
13: 5. `VALIDATION.md` — the gates you must run and report.
14: 6. `PROJECT_MAP.md`, `DISCOVERIES.md`, `KNOWN_ISSUES.md`, `COMPLETED_WORK.md` — reference while working.
15: 
16: ## Current state in one paragraph
17: 
18: MaxManager has a strong engine and a fragmented surface. `core/maxai` + `core/hardware` implement a real control plane (canonical knob keys, leases with baselines, verified apply/rollback, safety veto, per-knob manual locks, cross-process ownership journal, per-knob learning). A good design language exists in `ui/design/` and six screens have been rebuilt on it (TouchBoost is the reference). But navigation is written twice (bottom-bar routes vs a pager behind `use_scroll_animation`), 40+ route literals live inside an 817-line `MainActivity`, ~46 screens cover only ~9 device domains with duplicate/dead routes, Max AI is not a primary destination, and 33 screens still hand-roll their own `Scaffold`. The vision reframes the product as *a control plane that can prove what it is doing* with four destinations — Now, Control, Apps, Max AI — and NT-01 builds that spine.
19: 
20: ## Invariants — never break these
21: 
22: 1. **Control plane is the only write path.** UI/ViewModels must not write sysfs or run root shells directly; go through the arbiter/control plane so leases, baselines, verification and rollback hold (ADR-11).
23: 2. **No synthetic telemetry.** Unknown is `status_unknown`; stale is `Snapshot`; unsupported is `Unsupported`. Never format a fallback as a live value (ADR-07).
24: 3. **Manual intent wins per knob.** Touching a control locks only that knob (`ManualControlLocks`); never reintroduce a global “controller mode”.
25: 4. **Capability ≠ state.** Providers/capability sections stay visually separate from state controls; disabled controls always state `lockedReason` (ADR-08).
26: 5. **Localization + RTL.** New copy lands in `values/` and `values-ar/` together; motion must not imply LTR direction; no literals in Compose (ADR-14).
27: 6. **Don't redo finished work.** The 6 migrated screens and the control plane change only when a rule above requires an additive change (ADR-18).
28: 7. **Don't “fix” baseline noise.** `AppMonitor.kt` brace imbalance (I-43) and the vendored `kernel-flasher` duplicate theme files (I-44) are expected.
29: 8. **Commit the untracked foundation first.** `ui/design/`, `max_design_strings.xml`, `max_screen_strings.xml` are unversioned; commit before refactoring (I-41).
30: 
31: ## Environment facts you will hit
32: 
33: - Root: `/mnt/sdcard/MaxManger/optmize-main`; app sources at `manager/app/src/main/java/nd/max/`.
34: - JDK 25 present, but PATH `gradle` is 4.4.1 while the wrapper wants 9.5.1 and the network is down → **you cannot compile.** Use `VALIDATION.md` static gates and report “compilation unverified in this environment”.
35: - Working tree is intentionally dirty with the previous agent's redesign work — do not discard it.
36: 
37: ## What the Executor needs to produce for NT-01
38: 
39: - `ui/navigation/` package (destinations, graph, actions) as the single source of routes.
40: - `ControlScreen` + 9 domain hubs that *host* existing feature screens without moving their bodies.
41: - A slimmed `MainActivity` with one navigation model and 4 primary destinations, Settings moved to Now's top bar, Advanced tools gated.
42: - Gate results per `VALIDATION.md` §8 and a list of anything deferred.
43: 
44: ## Queued after NT-01
45: 
46: | ID | Task |
47: | --- | --- |
48: | NT-02 | Max AI as a primary surface: ownership map (`MaxOwnerChip`), change journal (`MaxJournalRow`), session bar, safety + credibility views; replace the card stack |
49: | NT-03 | Migrate hub bodies to `ui/design/` domain by domain (Power → Display → CPU → GPU → …), deleting legacy components as they are orphaned |
50: | NT-04 | Apps destination: per-app profile as one workspace (applist + app settings + process + freeze) |
51: | NT-05 | Settings & Appearance consolidation, ambient-effects switch default off, advanced-tools risk gate, delete unreferenced decorative engines |
52: | NT-06 | Command palette + device truth sheet (per-knob capability proof from `HardwareCapabilityResolver`) |
53: | NT-07 | Localization parity sweep + make the design-adoption static test blocking; first UI tests |
54: 
55: ## Open questions (decide by defaulting, do not block on the user)
56: 
57: - Whether `preferenced` rows split cleanly between CPU and Memory hubs — default: keep the screen whole under CPU and list a Memory row that deep-links to it, then split in NT-03.
58: - Whether `mtkscreen` tabs should be dissolved into the domain hubs — default: keep the tabbed screen for now, linked from CPU/GPU/Thermal; dissolve in NT-03 if each tab is thin.
59: - `LegendaryHomeDashboard` vs `HomeDashboardComponents` — default: keep whichever `HomeScreen` currently renders, delete the other in NT-03 after reference check.
60: 
61: ## Executor log
62: 
63: <!-- Append one entry per completed task: task id, files, gate results, deferred items, next suggestion. -->
64: 
65: ### ATLAS-ADAPTIVE-01 — أطلس ينفّذ ويتحقق ويتعلم بدل أن يكتفي بالقراءة — 2026-09-20
66: 
67: **TASK:** ATLAS-ADAPTIVE-01 (large) — `DONE_WITH_CONCERNS`
68: **FILES:** جديد منتج — `core/hardware/AtlasAdaptiveExecutor.kt` · `core/hardware/AtlasRouteMemory.kt` ·
69: `core/hardware/AtlasPrivilegedReadTransport.kt` · جديد اختبار — `test/.../hardware/AtlasAdaptiveExecutorTest.kt` ·
70: `test/.../hardware/AtlasRouteMemoryTest.kt` · `test/.../hardware/AtlasAdaptiveReadTransportTest.kt` ·
71: معدَّل منتج — `core/hardware/HardwareRepairExecutor.kt` (واجهة `AtlasRepairPort`) · `core/hardware/RootFileAccess.kt`
72: (`listNames`) · `core/maxai/MinimalPlanner.kt` (التنفيذ عبر Atlas بدل `arbiter.submit` المباشر) · `core/di/DataModule.kt`.
73: 
74: **الفجوة التي كان التسليم يجيب عنها:** `AtlasRoutePlanner` كان مخطِّطًا مُختبرًا **بلا مستهلك إنتاجي**
75: لكتابة، وقارئ أطلس كان **بلا مسار مميز** على جهاز مروّت، فكانت كل عقدة `/sys`/`/proc` محمية تُقرأ
76: `PERMISSION_DENIED` ويُظنّ أنها غير موجودة. الآن: التنفيذ يمرّ بـ`HardwareRepairExecutor` (خط أساس +
77: read-back + نافذة تأكيد + استرجاع)، و**التراجع المتحقَّق يفتح المسار التالي** بدل إعلان «الميزة لا تعمل»،
78: وفشل الاسترجاع **يوقف** التتابع لأن الحالة الفيزيائية صارت مجهولة.
79: 
80: **التعلّم لكل جهاز:** `AtlasRouteMemory` (JSON صريح الحقول، كتابة ذرّية عبر `AtlasStoreIo`، مدخل تالف
81: يُطرح لا يُقرأ جزئيًا، سقف مدخلات). قاعدتان ملزمتان: ① **التفضيل لا يتجاوز ترتيب السلامة** — يُطبَّق
82: كسر تعادل **داخل** طبقة النقل نفسها، فمسار sysfs متعلَّم لا يسبق مسار المنصّة؛ ② **الحجر ينتهي بالإقلاع**
83: (أو بتغيّر جيل الصلاحية) لأن حالة الـvendor تُبنى من جديد، فالاسترجاع الفاشل لا يمنع إعادة المحاولة أبدًا.
84: 
85: **القراءة privileged:** `AtlasAdaptiveReadTransport` يجرّب الجذر **المُخزَّن مسبقًا فقط** (لا نافذة صلاحية
86: عند فتح شاشة)، ويسقط إلى المسار العادي عند `PERMISSION_DENIED`/`BACKEND_UNAVAILABLE` — **ولا يسقط عند
87: `ABSENT`**، لأن «غير موجود» نتيجة معلنة لا خطأ صلاحية. والقصّ صار على حدّ محرف كي لا يُقسم محرف متعدد البايتات.
88: 
89: **إصلاح انحدار في التعلّم (وجدته بالمراجعة لا بالاختبار):** النسخة الأولى كانت تسجّل فشل المصداقية
90: أيضًا عند **الحجب** (قفل يدوي أو أسبقية مالك) — أي أن قرار ملكية كان يسمّم إشارة تعلّم المقبض. القاعدة
91: المُستعادة: لا تسجيل عند `BLOCKED` ولا عند فشل الاسترجاع.
92: 
93: **GATES:** `kt_balance --assert` = **734** ملفًا/0 عوائق · `code_health --assert` = exit 0 (الأربعة أصفار،
94: والدَّين **لم ينمُ**: 10 · 29 · 63 · 23) · `i18n_coverage --assert` = 0 عوائق والأكواد الثلاثة متطابقة ·
95: `repo_audit` = `PROBLEMS: 0`. لا نصّ واجهة جديد ⇒ لا ترجمة، ولا كتابة عتاد من الواجهة.
96: **BUILD:** **لم يُشغَّل ولا يُدَّعى.** محاولة التشغيل فشلت لأن هذه الشجرة **بلا JDK 17 وبلا Android SDK**
97: (`local.properties` يشير إلى مسار غير موجود، و`java` على PATH = 25) ⇒ «compilation unverified in this
98: environment»، والاختبارات الـ**٢٠** الجديدة (executor 6 · memory 10 · transport 2 + planner القائم) مكتوبة
99: وغير مُنفَّذة.
100: **RESIDUAL RISK:** ① لا route فعلية ثانية لأي هدف بعد (المستهلك يبني binding واحدًا) ⇒ التتابع مبنيّ ومُختبر
101: لكن مساحته لم تُملأ بعد. ② `HardwareRepairState.CONFIRMED_WINDOW` ≈ ٨٠ مللي ⇒ كاتب vendor دوري لا يُرى فيه؛
102: الانجراف الطويل يبقى على `AppMonitor` (كما كان). ③ الحجر لا يُميّز بين فشل استرجاع عابر وفشل بنيوي — القرار
103: عن قصد: إعادة المحاولة بعد إقلاع، لا حظر دائم.
104: **NEXT:** ملء الطبقات بمسارات فعلية لكل هدف (platform hint · vendor bridge · daemon · arbiter) وتشغيل
105: `:app:testReleaseUnitTest` عند توفر SDK على جهاز حقيقي.
106: 
107: ### AR-RESEARCH-2 — **فهرسة المستودعات** وتصحيح ادّعاء خاطئ — 2026-09-18
108: 
109: **TASK:** AR-RESEARCH-2 (medium) — `DONE_WITH_CONCERNS`
110: **FILES:** `docs/ai/EXTERNAL-RESEARCH-APP.md` (+§١٣ كاملة، وتصحيح §١٢) · `docs/ai/EXTERNAL-RESEARCH.md` (تصحيح §٩) ·
111: `docs/ai/NEXT_TASK.md` (`NT-37..NT-41`) · `docs/ai/KNOWN_ISSUES.md` (`I-69` مُصحَّح + `I-63` مُصحَّح + `I-70` جديد)
112: 
113: **أهم ما في هذا التسليم ليس فكرة، بل تصحيح:** جولتاي البحث السابقتان كتبنا فيهما أن **أداة
114: فهرسة مستودعات GitHub غير متاحة في هذه الجلسة**. تحقّقتُ هذه المرة بدل تكرار الادّعاء فوجدت
115: أن **واجهة GitHub البرمجية تعمل** وأن الادّعاء **خطأ مني**. فنُفِّذت الفهرسة فعلًا، ثم
116: **صُحِّح الادّعاء في مكانه** (لم يُمحَ) في الملفّين والسجلّين.
117: 
118: **الحصيلة المقيسة للفهرسة:** **٥٧ استعلامًا** ⇒ **٢٩٢٢ سجلًا** جُلب وفرُز ⇒ **٢٥٤٦ مستودعًا
119: فريدًا** (والكون المُعلن لتلك الاستعلامات **٣٩٥٦٣**). القراءة العميقة كانت على مجموعة أصغر،
120: وهذا لا يُخلط بالفهرسة في أي رقم أعلاه.
121: 
122: **درسان مذكوران للجولة القادمة (`I-70`):** ① استعلام بكلمتين ⇒ ٤٧٩٨ نتيجة، وبأربع كلمات تقنية
123: ⇒ **صفر** (GitHub يطلب كل الكلمات) ⇒ النافع: `topic:` + كلمات قليلة، ثم فرز بالوصف.
124: ② الكون مُلوَّث: استعلامات الحشو تجلب مستودعات بآلاف النجوم لا صلة لها بمجالنا ⇒ **الفرز
125: بالمفردات لا بالشهرة**. ③ أعلى مصدر عائد لم يكن مستودعًا بل **قائمتين منسَّقتين** تعملان
126: كأداة تعداد لمئات التطبيقات.
127: 
128: **أهم ما كشفته الفهرسة (وعجزت عنه الجولة السابقة):**
129: - **AR-20 طبقة امتياز ثانية (Shizuku):** جزء معتبر من أدواتنا (الكثافة، DNS، تعطيل حزم، AppOps،
130:   بطارية، سجلات) **لا يحتاج جذرًا** — لكن معمارنا يطلب جذرًا دائمًا. **النظير الصريح:** واحد من
131:   أقوى إشارات المجال: `awesome-shizuku` (١٠١٩٢★) وقائمة أدوات لا تحتاج جذرًا.
132: - **AR-21 مفتاح يحمي نفسه:** النظير `amply` يُعيد حدّ الشحن الحامي **تلقائيًا** بعد شحنة واحدة.
133:   وهذا هو النمط الذي يمنح **AR-19** شكلًا قابلًا للتنفيذ (تعطيل الحرارة المؤقت).
134: - **AR-32 تشخيص مربوط بالدليل** (نمط `SmartPerfetto`) وكشف انسداد الخيط الرئيسي بلا مخرجات مسبقة.
135: 
136: **GATES:** `code_health --assert` = exit 0 (الدَّين **لم ينمُ**: 10 · 29 · 80 · 27) ·
137: `i18n_coverage --assert` = exit 0 · `repo_audit` = `PROBLEMS: 0` · `git diff --check` نظيف.
138: **لا كود منتج ولا نص جديد** ⇒ لا بناء ولا ترجمة. والفهرس المؤقت كُتب في `/tmp/ghq/` **خارج
139: المستودع** عمدًا (لا شيم في `build/` ولا حطام في الجذر).
140: 
141: **RESIDUAL RISK:** الفلترة كانت على **الوصف والمواضيع** لا الكود لـ٢٥٤٦ مستودعًا · ولم تُقرأ
142: READMEs لأكثر من حفنة منها · واستعلامات `topic:android-performance`/`topic:android-tweaks`
143: أعطت كونًا صغيرًا (٤٤/٨) أي أن مجالات كاملة قد تكون تحمل مواضيع أخرى لم أجرّبها ·
144: **وAR-20 (Shizuku) قرار معماري كبير يحتاج قرار مالك قبل أي تنفيذ.**
145: 
146: **NEXT:** `NT-37` يحتاج قرار (طبقة Shizuku: نعم/لا/جزئية) · و`NT-26`/`NT-27` (صفحة الإنقاذ وتاريخ
147: الإقلاع) ما زالا **قابلين للبدء فورًا** لأنهما قراءة فقط بلا امتياز.
148: 
149: ### AR-RESEARCH — بحث خارجي على مستوى **التطبيق كله** + ثلاث دقائق مُتحقَّقة — 2026-09-18
150: 
151: **TASK:** AR-RESEARCH (medium) — `DONE_WITH_CONCERNS`
152: **FILES:** `docs/ai/EXTERNAL-RESEARCH-APP.md` (جديد، بنك `AR-01..AR-19` + `AR-R1..R6` مرفوضة) ·
153: `docs/ai/NEXT_TASK.md` (+قسم البنك، `NT-26..NT-36`) · `docs/ai/KNOWN_ISSUES.md` (`I-66..I-69`)
154: 
155: **ما تغيّر فعلًا:** جولة بحث ثانية — لكن على الطبقة التي لم تُبحَث: **التطبيق** لا المحرك.
156: والأهم: الجولة لم تبدأ بالمصادر بل بالكود، فأنتجت **ثلاث دقائق بأدلة أوامر** لا انطباعات:
157: 
158: 1. **`I-66` (P1):** مفتاح تعطيل الحرارة **مُوصَّل بالواجهة** (`PreferencedTweakScreen.kt:320`)
159:    ويسلسل إلى كتلة `DISABLE THERMAL` التي تقتل `thermald` وتُعطّل مناطق الحرارة وسياسات MTK PPM
160:    وحدود GPU الحرارية — أي أن المستودع يحمل في كتبه `XR-R4` **مرفوضًا** بقاعدة AOSP، وفي شيفرته
161:    مفتاحًا يخالفه. **لم أُعدّل المفتاح ولم أحذفه**: ذلك قرار مالك + مراجعة سلامة، وقد سُجِّل ليُتخذ صراحةً (`NT-30`).
162: 2. **`I-68` (P2):** الإنقاذ موجود ونتيجته غير مرئية — `package-recovery.log` يُكتب و**عدد قارئيه صفر**،
163:    و`count.sh`/`disable`/`module.prop.orig` لا يقرأها التطبيق. أي أن الوحدة تنجح في منع الكارثة وتفشل في الإبلاغ عنها.
164: 3. **`I-67` (P2):** هوية الإصدار في ثلاثة أماكن متفرّعة (`version` · `update.json` · `module.prop` — والأخير
165:    مشتقّ وقت التحزيم ومع ذلك هو ما يقرأه التطبيق)، و`zipUrl`/`updateJson` فارغان ⇒ لا فحص تحديث ممكن أصلًا.
166: 
167: **النمط المكشوف (وهو الفائدة الحقيقية):** أعلى قيمة في هذه الجولة ليست قدرة جديدة — بل **قراءة وحوكمة**.
168: ٥ من أول ٥ بنود موصى بها **لا تكتب عتادًا**. ومرتبة أولًا: **صفحة الإنقاذ داخل التطبيق** (`NT-26`)
169: — أرخص بند وأعلاها قيمة، وتعالج مفارقة أن التطبيق يلامس الإقلاع والحرارة ولا يشرح كيف يُصلح جهازه.
170: 
171: **الدعوى القابلة للتكذيب:** كل بند في §١ من الملف يمكن إعادة إنتاجه بأمر واحد مذكور معه —
172: ومن ينكر `I-66` عليه أن يُكذّب سلسلة الاستدعاء، لا أن يناقش الرأي.
173: 
174: **GATES:** `code_health --assert` = exit 0 (والدَّين **لم ينمُ**: 10 · 29 · 80 · 27) ·
175: `i18n_coverage --assert` = 0 عيوب · `repo_audit` = `PROBLEMS: 0` · `git diff --check` نظيف.
176: **لا كود منتج في هذا التسليم** ⇒ لا بناء ولا اختبارات مطلوبة، ولا نص جديد ⇒ لا ترجمة.
177: 
178: **RESIDUAL RISK:** لم تُفهرس ١٠٠٠ مستودع (١٦ استطلاعًا + ٦ قراءات كاملة، وأداة فهرسة GitHub غير متاحة
179: — `I-69`) · بند واحد لم أتحقّق منه بالكود (حقول البطارية في `DashboardDetailScreens`) وقد كُتب
180: **«يُتحقَّق قبل التنفيذ»** ولم يُقدَّم كفجوة · **و`I-66` بلا حكم سلامة مستقل، وهو بند سلامة لا بند راحة.**

(Showing lines 1-180 of 5171. Use offset=181 to continue.)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/REVIEW.md",
  "limit": 230
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/REVIEW.md</path>
<type>file</type>
<content>
1: # REVIEW.md — بروتوكول مراجعة مستقلة (متعدّدة النماذج)
2: 
3: الغرض: أن يستطيع **نموذج آخر لا يشارك هذه الجلسة أي سياق** أن يتحقّق من تسليم ما، ويقول
4: `PASS` أو `FAIL` بدليل يعيد إنتاجه بنفسه. المراجعة ليست قراءة إنشاء: هي **إعادة اشتقاق**.
5: 
6: > **مهم**: هذه الجلسة لا تستطيع استدعاء نموذج آخر بنفسها (لا أداة `spawn_agents` مُتاحة لي — موثَّق في
7: > `AGENTS.md` §2.1 مع الفرق بين ما يُوثّقه Freebuff رسميًا وما هو متاح في هذه الجلسة فعلًا).
8: > ما أستطيع فعله — وقد فعلته — هو إنتاج حزمة قابلة للنقل: الأوامر، والأرقام المتوقّعة، ومحاولات التكذيب.
9: >
10: > **طريقة التشغيل**: بدّل النموذج في المنتقي إلى GPT-5.6 Luna (أو غيره) وأرسل تسليم الإطلاق الجاهز
11: > في `AGENTS.md` §4 في **هذه المحادثة نفسها** — فيبقى السياق والـdiff كاملين، وتصبح المراجعة مستقلة لا
12: > مُكرَّرة. ثم أعِد النموذج السابق للإدماج.
13: 
14: ---
15: 
16: ## 1. القاعدتان اللتان تحكمان كل مراجعة
17: 
18: ### القاعدة ١ — لا تثق بالمنفّذ، ولا بنصّه
19: 
20: كل رقم في تقرير المنفّذ يجب أن يُعاد توليده بأمر. الرقم الذي لا يمكن إعادة توليده بأمر
21: **ليس دليلًا**، بل ادّعاء. ارفض التسليم الذي يقول «تحقّقتُ» بلا أمر يقابل كل ادعاء.
22: 
23: ### القاعدة ٢ — افحص أداتك قبل أن تحكم على الكود
24: 
25: هذه ليست نصيحة عامة؛ هي الدرس الأغلى في تاريخ هذا المستودع. **ثلاث مرات** في جلسة واحدة
26: أعطت أداة الفحص نتائج **كلها زائفة** وكان الكود سليمًا:
27: 
28: | الأداة | ما قالته | الحقيقة | سبب الخطأ |
29: | --- | --- | --- | --- |
30: | فحص «شبه ميت» بالاسم | ٣ ملفات ميتة في `ui/mainscreens/` | الملفات **حيّة** وتُستدعى ٧١ مرة على مستوى الرمز | الحكم بالاسم لا بالرمز |
31: | مسح قابلية الوصول | ٤ شاشات غير قابلة للوصول | **صفر** من ٤٥ شاشة | تجاهل مواضع الاستدعاء **داخل** ملف التعريف |
32: | أول نسخة من `code_health.py` | ١٣ مرجع `R.*` ناقص | **صفر** | ① قرأ XML فقط فلم يرَ موارد PNG ② قارن مديولَي Gradle بفهرس `res` واحد |
33: 
34: لذلك: **قبل أن تُبلّغ عن عيب، أثبت أن أداتك ترى الحالة الصحيحة**. اختبار عملي: شغّل الأداة
35: من مجلدين مختلفين؛ إن اختلف الناتج فالأداة معطوبة — وهذا بالضبط ما كان يفعله
36: `check2.py` قبل نقله (من `tools/` كان يطبع «٠ ملفات» وبلاغات مفقود **وهمية** بثقة).
37: 
38: ---
39: 
40: ## 2. حزمة إعادة الإنتاج (انسخ هذا الحرفي)
41: 
42: ```sh
43: # 0) المكان — كل الأوامر من جذر المستودع، والأدوات تعمل من أي مجلد
44: git rev-parse --show-toplevel && git status --porcelain | wc -l
45: 
46: # 1) بوابة اللغات (§3 · §3.1 في VALIDATION.md)
47: python3 tools/i18n_coverage.py --assert                          # exit 0
48: python3 tools/i18n_coverage.py | head -5                         # ar/es/fr = 2106/2106
49: python3 tools/i18n_coverage.py --check-codes                     # المُنتقي ↔ المجلدات ↔ locales_config
50: 
51: # 2) بوابة البنية — بلا مُصرّف (أمر المالك: لا بناء تلقائي، AGENTS.md §0.1)
52: python3 tools/kt_balance.py --assert                             # exit 0 · 670 ملفًا · 0 عوائق
53: python3 tools/kt_balance.py --self-test                           # أداة تُقاس على حالات معروفة: 17/17
54: 
55: # 3) بوابة النظافة (المرتفعة حديثًا)
56: python3 tools/code_health.py --assert                            # exit 0
57: python3 tools/code_health.py | head -20                          # الصحّة = 0 في الأربعة
58: 
59: # 4) الفاحص الإضافي (المستقل عن البوابات)
60: python3 tools/repo_audit.py                                      # PROBLEMS: 0
61: 
62: # 5) النظافة العامة
63: git diff --check                                                 # بلا مخرجات
64: ```
65: 
66: ### الأرقام المتوقّعة — إن خالفها أي رقم، فراجع الأمر قبل أن تراجع الكود
67: 
68: | ما يُقاس | القيمة المتوقّعة في الحالة الحالية | لو اختلف |
69: | --- | --- | --- |
70: | `i18n_coverage --assert` | `exit 0` | ① اسأل: هل شُغّل من جذر المستودع؟ |
71: | تغطية `ar` | `3203 / 3203` · **٠ ملفات ناقصة** | تحقّق من `values-ar/` مباشرة، لا من الأداة |
72: | تغطية `es` · `fr` | `2103 / 3203` (٦٥٫٧٪) · ٧ ملفات ناقصة لكل منهما | ترجمتها جولة منفصلة (`NT-11`)، وليست عطبًا |
73: | اللغات الهدف | `84` | `ls -d manager/app/src/main/res/values* \| wc -l` − 1 |
74: | ناقص الكل | يُقرأ من `--locale` لكل لغة لا من جدول ثابت (الرقم يتغيّر مع كل نصّ جديد) | لا تقسّم من الذاكرة — الأداة هي المرجع |
75: | `kt_balance --assert` | `exit 0` · `670` ملفًا · `0` عوائق · `--self-test` = `17/17` | إن مرّت على كل شيء فالأرجح أن الأداة مكسورة: شغّل `--self-test` أولًا |
76: | `code_health --assert` | `exit 0` · أربعة فحوص صحّة = `0` | القسم «الصحّة» في التقرير |
77: | دَين الصيانة | `oversized_files 10` · `own_wildcard_imports 29` · `hardcoded_ui_literals 66` · `presentation_hw_writes 26` | السقف في `tools/code_health_baseline.json` |
78: | `repo_audit.py` | `PROBLEMS: 0` · `353` ملفًا · `2540` مرجع `R.string` · `3205` مفتاحًا أساسيًا | أرقامه مقصورة على `manager/app`، ومفتاحه الأساسي `3205` مقابل `3203` لأداة اللغات — عدّان مختلفان لأداة تختلف شروط مسحها، فتُذكر مع أداة كلّ رقم |
79: | ملفات Kotlin (شجرة `manager`) | `490` ملفًا · `113350` سطرًا (`find`، بلا `build/`) | الفرق بين عدّين طريقة عدّ لا شجرة مختلفة (`code_health` يقول `113840`: يعدّ سطر الملف الأخير بلا `\n` ويضم ملفات توليد) |
80: | XML في `app/res` | يُحلَّل في البوابة بنجاح | أي فشل تحليل = بناء مكسور |
81: | مجلدات `values*` | `85` (الإنجليزية + ٨٤ لغة) · **صفر مفتاح مكرّر** | التكرار = فشل `aapt2` |
82: | **بناء** | `:app:compileDebugKotlin` · `:app:testReleaseUnitTest` · `:app:assembleDebug` · `:app:minifyReleaseWithR8` = `BUILD SUCCESSFUL` — **عند الطلب فقط** (`AGENTS.md` §0.1) | لا تثق بنتيجة بلا أمر مقابلها، ولا تُشغّل بناءً لم يُطلب |
83: 
84: > **أُعيد قياسه 2026-09-19 بعد جولة `HOME-01`** (الشاشة الرئيسية: التفاصيل · الأنوية · الطيف · إزالة التكرار)
85: > بعد حذف أربعة نصوص يتيمة: أساس اللغات `3203` بعد أن كان `3207`، وملفات Kotlin `477`/`109153`،
86: > و`repo_audit` `349`/`2540`/`3205`، وبوابة `kt_balance` الجديدة `657`/`0`. والسقف `10 / 29 / 66 / 26`
87: > **ثابت** — والشاشة كبرت بملفين جديدين (`109616` سطرًا بحسب `code_health`) ولم يصعد السقف.
88: >
89: > **حُدِّث الجدول 2026-09-19 (جولة `FM-02`)** لأن الأرقام السابقة كانت مكتوبة عن شجرة أخرى: `hardcoded_ui_literals` كان `80` والمقيس هنا `66`، وملفات Kotlin كانت `297`/`70148` والمقيس `431`/`102364`، ومفاتيح الإنجليزية كانت `2106` والمقيس `3179`.
90: >
91: > **حُدِّث 2026-09-20 (جولة `ATLAS` P2+P4)** — أُعيد قياس كل صف في هذه الجولة، لا تقديره:
92: > `kt_balance` `657`⇒`670` ملفًا و`--self-test` `14`⇒`17` حالة (أُضيف فحص الأسماء المائيّة التي أفشت
93: > إنذارًا كاذبًا)، وملفات Kotlin `477`/`109153`⇒**`490`/`113350`**، و`repo_audit` `349`⇒**`353`** ملفًا،
94: > و`code_health` `490`/`113840`. والسقف `10 / 29 / 66 / 26` **لم يتحرك** بعد ~١٢٧٥ سطرًا جديدًا.
95: > و**أُضيف صفّ البناء المقيس فعلًا** (بإذن المالك): `:app:testReleaseUnitTest` = **٨٨٢ اختبارًا · ٠ فشل** ·
96: > `:app:assembleDebug` = APK ‎`121,032,092`‎ بايت · `BUILD SUCCESSFUL 5m43s`.
97: > **ثم أُعيد قياسه 2026-09-19 بعد `MT-FM` + `MT-FM-03` + `MT-FM-04`** لأن شجرة الموارد تغيّرت بنصّ جديد (الشاشة الجديدة) وبمسح ٧٥ مفتاحًا يتيمًا: أساس اللغات `3207` بعد أن كان `3179`، وملفات Kotlin `471`/`108397`، وأرقام `repo_audit` `345`/`2544`/`3209`. والسقف (`10 / 29 / 66 / 26`) **لم يتغيّر** — وهذا ما يعنيه السقف: لا يصعد بما أُضيف.
98: >
99: > والدرس: **الرقم المتوقّع يُعاد قياسه قبل أن يُحكم بخلافه** — وإلا رفض المراجع تسليمًا سليمًا لأن جدوله هو القديم.
100: 
101: ---
102: 
103: ## 3. بطاقات الأدوار — ما يملك كل مراجع أن يرفض به
104: 
105: كل بطاقة مستقلة: لا تحتاج البطاقة الأخرى لتصدر حكمًا.
106: 
107: ### 3.1 Verifier — صحّة الروابط والموارد
108: 
109: - **يقرأ**: `tools/code_health.py` (قسم الصحّة)، `VALIDATION.md` §2 §3 §5.
110: - **يحكم على**: `package_mismatch` · `unresolved_resource` · `duplicate_string_key` · `stray_root_file` — الأربعة أصفار.
111: - **يرفض إذا**: وُجد `R.string.x` بلا مفتاح، أو ملف Kotlin لا تطابق حزمته مساره، أو حزمة قديمة
112:   (مثل `nd.max.ui.components`) لا يزال أحد يستوردها.
113: - **حدّ لا يتجاوزه**: لا يعدّل كود المنتج. يكتب تقريرًا واختبارًا فقط.
114: 
115: ### 3.2 Safety Reviewer — ما يلمس العتاد والامتيازات
116: 
117: - **يقرأ**: الـdiff كاملًا، ثم `core/hardware/**`, `core/maxai/**`, `res/xml/locales_config.xml`،
118:   و`AndroidManifest.xml`.
119: - **يحكم على** (كل بند قابل للتكذيب بأمر):
120: 
121:   1. **كتابة العتاد من طبقة العرض** (ADR-11). المسبار الصحيح هو مقياس `presentation_hw_writes`
122:      في `python3 tools/code_health.py`، وليس `grep '/sys/'`:
123: 
124:      > **تحذير من مسبار خاطئ**: `grep -rn '/sys/' .../ui` يُرجع **٨٧ نتيجة كلها ليست كتابة** —
125:      > معظمها قراءات (`readSysFile`, `cat /sys/...`)، وقد كتبتُ هذه البطاقة بهذا المسبار أولًا
126:      > ثم أصلحتها. وكذلك `>` يُطابق `2>/dev/null` وهو إخماد stderr لا كتابة. والسطر النصّي
127:      > داخل تعليق ليس كودًا. أي بلاغ من هذه الثلاثة **كاذب** إن ظهر في تقرير مراجع.
128: 
129:      الحالة اليوم، مقيسة: **`27` موضعًا** في `ui/**` (باستثناء `ui/util/` المشروعة)، أثقلها
130:      `viewmodel/ZramViewModel.kt` (١٠) و`viewmodel/ChargingViewModel.kt` (٣) و
131:      `viewmodel/DisplayStudioViewModel.kt` و`NetworkSchedulerViewModel.kt` و`TouchBoostViewModel.kt`
132:      و`subscreens/ZramManagerScreen.kt` (كتابة من داخل شاشة).
133:      **هذا دَين سابق موثَّق** — `docs/ai/VERIFICATION_NT01.md` يسجّل «١٩٩ موضعًا مباشرًا في `ui/**`»
134:      ويقرّر أن ADR-11 يُطبَّق على **الكود الجديد** وأن الترحيل يأتي منطقة‑منطقة. الجديد هنا أن
135:      الرقم صار قابلًا للفرض: السقف `27` في `tools/code_health_baseline.json`، وارتفاعه يفشل البوابة.
136:   2. **مسارات التنقّل** (ADR-02): لا حرف مسار داخل ملف شاشة. شغّل بوابة §5(f) **حرفيًا من الوثيقة**
137:      (لا تُعد اختراعها: نسخة مكتوبة بسرعة أعطت ٧ نتائج منها ٢ زائفتان على ملفي السجلّ).
138:      الناتج الموثَّق اليوم: **٨ نتائج كلها في `ui/flasher/KernelFlasherScreen.kt`** — المهمة المفتوحة
139:      `NT-07`، وصفر زائف. وبوابة (b): `grep -rn 'navigate("' … | grep -v '/ui/navigation/'` ⇒ لا مخرجات.
140:   3. **لا تليمتري مُصنَّع** (ADR-07): المجهول `status_unknown`.
141:   4. **تبديل اللغة**: على API 33+ يستلزم `android:localeConfig`؛ وعلى `minSdk=29` يحتاج مسلك
142:      `attachBaseContext` لأن `MainActivity` لا يرث `AppCompatActivity`. **كلاهما إلزامي معًا** —
143:      أحدهما وحده يترك نصف النسخ المدعومة صامتًا.
144: - **يرفض إذا**: أي كتابة عتاد **جديدة** لا تمرّ عبر الـarbiter (القديم دَين مُجمَّد لا يُغلق بمهمة واجهة)،
145:   أو أي ادّعاء «يعمل على ١٠–١٢ و١٣+» بلا تشغيل على جهاز.
146: 
147: ### 3.3 Architect — المعنى والقرارات
148: 
149: - **يقرأ**: `DECISIONS.md` (ADR-01…28)، `NEXT_TASK.md`، `CONCERNS.md`.
150: - **يسأل**: هل كل قرار جديد صار ADR؟ هل `NEXT_TASK.md` يحمل حالة **مُتحقَّقة** لكل بند لا نصوصًا منسوخة؟
151: - **يرفض إذا**: تغيّر سلوك بلا ADR، أو بقي بند «مفتوح» وُصف بأنه «مُصلح» في موضع آخر.
152: 
153: ### 3.4 Localizer — النصوص والوسائط
154: 
155: - **يقرأ**: `values*/**`, `tools/i18n_coverage.py`, `crowdin.yml`.
156: - **يحكم على**: `--assert` = `exit 0`؛ ومساواة **الوسائط** بين المصدر والترجمة.
157: - **يرفض إذا**: ترجمة تطلب وسيطًا (`%1$s`) لا يمرّره الكود — هذه ليست نصًّا رديئًا بل **انهيار شاشة**.
158: - **لا يلمس**: `\u00b7` و`\u00d7` و`\u2014` و`\u2026` و`\u00b0` — هي تهريب Unicode مقصود في كل
159:   اللغات، وأندرويد يحلّها. استبدالها بمحارف فعلية أو إسقاطها تغيير صامت.
160: 
161: ---
162: 
163: ## 4. التمرير العدائي — ٥ محاولات تكذيب بأوامر
164: 
165: مهمة المراجع ليست تأكيد التسليم، بل **محاولة إسقاطه**. كل محاولة هنا محددة ولها مخرج متوقّع.
166: 
167: | # | الادّعاء المُختبَر | الأمر | يُكذَّب إذا |
168: | --- | --- | --- | --- |
169: | ١ | «كل `R.string` في الكود له مفتاح» | `python3 tools/code_health.py \| grep unresolved` | ظهر أي سطر `R.string.*` |
170: | ٢ | «لا نقل ملف أفسد حزمته» | python المقطع في §5 أدناه | ظهر أي ملف بحزمة لا تطابق مساره |
171: | ٣ | «لا ملف محذوف ما زال مستدعى» | `git status --porcelain \| awk '$1=="D"{print $2}'` ثم `grep -rlF "<basename>"` | ظهر مرجع في كود المنتج |
172: | ٤ | «لا ترجمة تطلب وسيطًا غير موجود» | `python3 tools/i18n_coverage.py --assert` | `exit 1` أو أي `ERROR` |
173: | ٥ | «المُنتقي والمجلدات و`locales_config` متطابقة» | `python3 tools/i18n_coverage.py --check-codes` | ظهر فرق في العدد |
174: | ٦ | «لا كتابة عتاد جديدة من طبقة العرض» | `python3 tools/code_health.py --assert` | `presentation_hw_writes` تجاوز `27` |
175: | ٧ | «بوابة التنقّل لم تُوسَّع كي تُخفي شيئًا» | شغّل بوابة §5(f) من الوثيقة، وقارن بـ**٨** | اختلف العدد أو ظهرت نتيجة في ملف غير الفلاشر |
176: 
177: مقطع الفحص ٢ (انسخه كما هو):
178: 
179: ```sh
180: python3 - <<'PY'
181: import re, os, glob
182: bad=[]
183: for p in glob.glob("manager/app/src/**/*.kt", recursive=True):
184:     for m in ("/java/","/kotlin/"):
185:         if m in p: root=p.split(m)[0]+m[:-1]; break
186:     else: continue
187:     exp=os.path.relpath(os.path.dirname(p), root).replace(os.sep,".")
188:     d=re.search(r'^package\s+([\w.]+)', open(p,encoding="utf-8").read(), re.M)
189:     if d and d.group(1)!=exp: bad.append((p, d.group(1), exp))
190: print("package mismatches:", len(bad))
191: for b in bad: print("  ", b)
192: PY
193: ```
194: 
195: ---
196: 
197: ## 5. ما **لا** يمكن إثباته في هذه البيئة — لا تدّعِه
198: 
199: | الادّعاء | الحالة | السبب |
200: | --- | --- | --- |
201: | «الكود **يُترجم** بلا أخطاء» | **مُثبت 2026-09-18** | SDK في `~/android-sdk` + JDK 17، والنتائج الحرفية في `VALIDATION.md` §0: `:app:testDebugUnitTest :app:assembleDebug` = BUILD SUCCESSFUL 5m54s، 128 اختبارًا 0 فشل، APK موقّع v2؛ و`terminal-emulator` 145 اختبارًا؛ وR8/الموارد نجحا |
202: | «تبديل اللغة يعمل على جهاز حقيقي» | **غير مُثبت** | يستلزم بناءً وتشغيلًا على ١٠–١٢ و١٣+ |
203: | «الترجمات مراجَعة لغويًا» | **غير مُثبت** | كُتبت داخل المستودع، لا بمترجم أصلي |
204: | «صفر أخطاء وقت تشغيل» | **غير مُثبت** | الفحص ثابت فقط |
205: 
206: اللغة الصحيحة في التقرير بعد 2026-09-18: **«التجميع مُتحقَّق بالأمر في `VALIDATION.md` §0»**. وما زال **غير مُثبت** هنا: توقيع `release` (يحتاج سر CI) وسلوك تبديل اللغة على جهاز حقيقي — فلا تُخفِ هذين ولا تدّعِهما.
207: 
208: ---
209: 
210: ## 6. تسجيل النتيجة
211: 
212: كل مراجعة تُسجَّل في قسم `Executor log` داخل `HANDOFF.md` (ذاكرة واحدة، بحسب `AGENTS.md` §1)،
213: بهذا القالب:
214: 
215: ```
216: REVIEW <التاريخ> — <الدور> — <الحكم: PASS | FAIL | PARTIAL>
217: المُراجَع: <commit/ملفات>
218: الأوامر المنفَّذة: <النسخ الحرفي>
219: النتائج: <رقم مقابل كل رقم في §2، وأي فرق>
220: مُكذَّبات ناجحة: <ما حاول المراجع إسقاطه ولم يُسقط>
221: متبقٍ: <ما لم يستطع إثباته>
222: ```
223: 
224: ### 6.1 سجل المراجعات
225: 
226: | التاريخ | الدور | الحكم | المراجِع | ملاحظات |
227: | --- | --- | --- | --- | --- |
228: | 2026-09-18 | مالك (جلسة الطبقة ١) | PASS مُقيَّد | DeepSeek V4 Flash | أرصدَ صفر أخطاء في §2؛ تحديث «التجميع» ما زال مفتوحًا — **بانتظار مراجعة نموذج ثانٍ** |

(End of file - total 228 lines)
</content>
```

**Tool: grep**

**Input:**
```json
{
  "pattern": "[Aa][Tt][Ll][Aa][Ss]|أطلس|اطلس",
  "include": "*.{kt,md,xml}",
  "limit": 80
}
```

**Output:**
```
Found 80 matches (more matches available)
/mnt/sdcard/MaxManger/optmize-main/.planning/STATE.md:
  Line 8: **Current focus:** Phase 1 - Max Atlas compatibility and safe discovery

  Line 12: Phase: 1 of 1 (Max Atlas compatibility and safe discovery)

  Line 16: Last activity: 2026-09-20 - `P9.1` (fixtures) and `P13` (the Atlas doctor) delivered: a real run is now

  Line 27: Max Atlas is the product name selected under the owner's naming discretion.

  Line 38: doctor batch). Inside it, the Atlas and diagnostics scope is **22 classes / 306 tests / 0 failing**, and

  Line 46: - **P2** — the boundary is `core/hardware/ReadOnlyProbeAccess.kt` over an injected `AtlasReadTransport`

  Line 51: - **P3** — `AtlasBackendProvider` bridges the existing CPU/GPU parsers into Atlas without a second read;

  Line 56: - **P4** — `core/atlas/AtlasPlatformProvider.kt` (pure, injected `AtlasPlatformSource`) builds a support

  Line 63: - **P5** — `AtlasResolver` (ordered stages; cancellation is rethrown, never reported as exhaustion),

  Line 64:   `AtlasEvidenceStore` (explicit-field JSON, atomic write, corrupt file is dropped and the scan redone),

  Line 65:   `AtlasRepository` (one coalesced scan; cancellation publishes immediately and outranks a late answer),

  Line 66:   `AtlasFileReadTransport` and `AtlasFileStoreIo`. **23 of these tests run against the real host

  Line 71: - **P6** — `AtlasSupportReport` (minimized, numbered schema; refuses what it cannot parse; 256 KiB cap

  Line 72:   checked before the file exists; the preview is the bytes that get shared), `AtlasReportExporter`

  Line 76: - **P7** — `ui/viewmodel/AtlasViewModel.kt` with `AtlasPresentation` as pure rules tested separately

  Line 79:   counted as reviewed), and `ui/mainscreens/AtlasDiagnosticsSection.kt`, mounted by `DiagnosticsScreen`

  Line 82: - **P8** — Atlas is reachable without root and without the native module: the Diagnostics destination is

  Line 85: - **P9.1** — `core/atlas/AtlasFixture.kt` and `AtlasFixtureRecorder.kt`: the byte-level answers one real

  Line 89:   and never the phone. `AtlasFixtureRecorderTest` runs the same three requests twice — once over the

  Line 90:   shipped `AtlasFileReadTransport`, once over the fixture captured from it — and asserts the two result

  Line 92: - **P9.2** — `AtlasArchitectureTest` is the architecture guard (now 17 package files, both new files read).

  Line 93: - **P13** — `core/diagnostics/AtlasDoctor.kt`: does a replayed device still reproduce the report it came

  Line 97:   The outcome vocabulary is now published by `AtlasSupportReport` and imported, instead of copied.

  Line 98: - **P10** — `core/atlas/AtlasDeviceIdentity.kt` derives the device from what the platform declares (no

  Line 99:   `import android`, so the model is pure); `AtlasVendorTags` reads reviewed aliases and model-prefix

  Line 100:   families with a minimum token length; `AtlasKernelRelease` has nowhere to put a platform level; and

  Line 101:   `AtlasAnchors.REVIEWED_FILES` approves one reviewed file (`/proc/cpuinfo`) instead of approving

  Line 104: - **P12** — `AtlasFreshness` (a clock that moved backwards is stale, because an unmeasurable age is not a

  Line 105:   young age) with `AtlasStaleness` carrying the reason — `EXPIRED_BY_TIME`, `SUPERSEDED_BY_BOOT`,

  Line 107:   plus `AtlasFailureRetryPolicy`, `AtlasFailureLedger` (per-cause lifetimes, records dropped on a boot or

  Line 109:   remembered) and `AtlasProbeScheduler` (deterministic cheapest-first plan over the same budget P2

  Line 110:   enforces). **`AtlasFailure.STALE` still has no emitter**, and P12 is not the plan that should add one:

  Line 113: - **Automatic completion (owner directive of 2026-09-20)** — the second bank, `AtlasCommunityBank`:

  Line 130: than twelve edits — `AtlasCatalogScope` (`ROOT_FILE` / `CHILD_FILE`) plus an optional `childPrefix` (a

  Line 132: `AtlasAnchors.ENUMERABLE` grant.

  Line 134: **Atlas is no longer unreachable.** The earlier "zero call sites" finding is resolved: ten files outside

  Line 135: `core/atlas/` now name it and the UI reaches the repository through `AtlasViewModel` (Hilt-injected) →

  Line 136: `AtlasDiagnosticsSection`. "Delivered" for P7/P8 means source- and render-verified, not device-verified.

  Line 140: **implemented as recommended** in `AtlasReadBudget.DEFAULT` — one place, with the test harness delegating

  Line 158:   `UnavailableAtlasReadTransport` returns `BACKEND_UNAVAILABLE` meanwhile, so nothing is guessed at and

  Line 168:   granted false confidence; a tool false positive where `\bText\(` matched `AtlasTransportRead.Text(`).

  Line 180: debug APK produced, and the Atlas plus diagnostics scope inside it is **22 classes / 306 tests / 0 failing**

  Line 186: Resume file: .planning/phases/01-max-atlas-compatibility-and-safe-discovery/01-PLAN.md (section 15


/mnt/sdcard/MaxManger/optmize-main/.planning/ROADMAP.md:
  Line 1: # Roadmap: Max Atlas Compatibility

  Line 11: - [ ] **Phase 1: Max Atlas compatibility and safe discovery** - Expand device capability coverage without speculative hardware writes.

  Line 15: ### Phase 1: Max Atlas compatibility and safe discovery

  Line 18: results from reviewed knowledge, then bounded Max Atlas discovery, and only then guidance to

  Line 22: **Requirements:** ATLAS-01, ATLAS-02, ATLAS-03, ATLAS-04, ATLAS-05, ATLAS-06, ATLAS-07, ATLAS-08, ATLAS-09, ATLAS-10, ATLAS-11, ATLAS-12

  Line 37: **Plans:** written in `phases/01-max-atlas-compatibility-and-safe-discovery/01-PLAN.md` — ten

  Line 61: | P13 | On-device doctor + report→fixture loop | — | **done** (`AtlasDoctor`, plan §22.2) — the loop is built; running it on a phone is what remains |

  Line 64: **implemented as recommended** in `AtlasReadBudget.DEFAULT` — the single source the test harness

  Line 69: APK produced, and the Atlas plus diagnostics scope inside it is 22 classes / 306 tests with 0 failures —

  Line 72: (`:app:testDebugUnitTest --tests 'nd.max.core.atlas.*'`). `P2`'s T2.5 (the adapter over an existing

  Line 74: available in this runtime, and `UnavailableAtlasReadTransport` keeps that honest meanwhile.

  Line 80: | 1. Max Atlas compatibility and safe discovery | 12/14 | In progress — of the ten planned: `P0`, `P1`, `P3`, `P4`, `P5`, `P6`, `P7`, `P8` and `P9` delivered; `P2` delivered except its reviewed adapter (T2.5). Of the four proposed: `P10`, `P12` and `P13` delivered, `P11` not started (it can only *lower* confidence, so it must be built on real reports — which now have a mechanism). **The app reaches Atlas** (`AtlasViewModel` → `AtlasDiagnosticsSection`, plus a Settings entry), and a real run is now replayable data, so "needs device" is no longer permanent. What no run here can still prove is the device itself | - |


/mnt/sdcard/MaxManger/optmize-main/.planning/REQUIREMENTS.md:
  Line 1: # Requirements: Max Atlas

  Line 9: - [ ] **ATLAS-01**: Independently authored, versioned capability catalog with source provenance,

  Line 12: - [ ] **ATLAS-02**: Small generic/vendor/platform provider extensions use a stable capability

  Line 14: - [ ] **ATLAS-03**: Ordered per-feature resolution uses reviewed knowledge first, bounded safe

  Line 16: - [ ] **ATLAS-04**: Max Atlas is independent of Max AI policy and has no mutation authority;

  Line 18: - [ ] **ATLAS-05**: Evidence distinguishes absent, permission denied, read-only, malformed,

  Line 21: - [ ] **ATLAS-06**: Cache successful and failed observations with bounded retry and invalidation

  Line 24: - [ ] **ATLAS-07**: Reuse existing CPU/GPU/thermal/memory/power and other domain backends where

  Line 27: - [ ] **ATLAS-08**: Settings exposes discovery progress, per-feature reasons and retry, then a

  Line 30: - [ ] **ATLAS-09**: Support reports contain minimized versioned evidence, selected app diagnostics

  Line 33: - [ ] **ATLAS-10**: A maintainer can take a sanitized report, create a regression fixture, add

  Line 36: - [ ] **ATLAS-11**: Deterministic tests cover fallback, units, stale evidence, permissions,

  Line 39: - [ ] **ATLAS-12**: Discovery has explicit time/node/byte/concurrency limits, runs off main thread,

  Line 46: | ATLAS-01 | Phase 1 | Pending — partial evidence: P1 catalog schema, provenance and seed entries (15 entries after P10's `cpu.info.cpuinfo`), P10 device identity from declared-public surfaces only, P12 attaches lifetime to the freshness axis |

  Line 47: | ATLAS-02 | Phase 1 | Pending — partial evidence: P1 unknown-vendor fallback, reinforced by P10 (declared identity can only *add* vendor candidates, never remove one, and unknown text yields no hint, so an unknown device keeps every generic entry); providers land in P3 (`AtlasBackendProvider` over the existing CPU/GPU parsers) and P4 (`AtlasPlatformProvider`), and an unknown model cannot block another domain because the matrix refuses to exist without a row per domain |

  Line 48: | ATLAS-03 | Phase 1 | Pending — **code-complete, device-unverified**: P5's `AtlasResolver` runs ordered stages (reviewed knowledge first, then bounded discovery) and `AtlasRepository` publishes one coalesced result; unresolved stays explicit (`AtlasScanState.reviewedUnresolved` counts reviewed gaps only, so a missing candidate name is the expected case and not a finding); P6/P7 offer the report only after a finished, non-cancelled, non-bound-stopped pass |

  Line 49: | ATLAS-04 | Phase 1 | Pending — partial evidence: P1 no-authority guard, P2 boundary is read-only by construction (the transport interface has no mutating call, and the guard scans the boundary file too) |

  Line 50: | ATLAS-05 | Phase 1 | Pending — partial evidence: P1 orthogonal axes and unit/list rules, P2 preserves all 11 causes and never guesses a unit, P4 keeps battery dimensions separate, thermal scales source-declared, a malformed PSI value null with its raw text and privilege unverified without the control plane; P12 separates "still about now" from "still true" and gives staleness a **reason** (`AtlasStaleness`: expired by time · superseded by boot · superseded by privilege · unmeasurable clock) instead of one boolean — but `AtlasFailure.STALE` still has **no emitter**, and its rightful one is P5's resolver, because the ledger documents that staleness is not a read failure. P3 adds the reuse axis: the provider can only report what an existing parser returned, and a parser that cannot read yields no value rather than a default |

  Line 51: | ATLAS-06 | Phase 1 | Pending — **code-complete, device-unverified**: P12 supplies the rules (per-volatility lifetimes, reason-preserving staleness, per-cause retry, invalidation on a boot or privilege change, cancellation never remembered) and P5 supplies the store that uses them — `AtlasEvidenceStore` writes explicitly-versioned JSON atomically, drops a corrupt file and rescan rather than trusting half of it, and `AtlasRepository` keys evidence per scan context. Invalidation across a real reboot is still `needs device` |

  Line 52: | ATLAS-07 | Phase 1 | Pending — partial evidence: P4 builds a support matrix that cannot omit a domain, with every gap carrying a reason code (`no-source-wired`, `other-provider`, `partial-support`, `no-observation`, `unverified-identity`); P3 delivers the reuse half (the existing CPU/GPU backends feed Atlas through one provider with no second reading path). What is still missing is **real-device reads**, not another plan |

  Line 53: | ATLAS-08 | Phase 1 | Pending — **source/render verified, tap unverified**: P7/P8 put the section in Diagnostics and one entry in Settings, outside every `isLoaded` gate, with 76 paired EN/AR keys and no new primary navigation destination; no reading happens in the view model itself. "One finger opens it" stays `needs device` |

  Line 54: | ATLAS-09 | Phase 1 | Pending — **code-complete, device-unverified**: P6 (`AtlasSupportReport` + `AtlasReportExporter`) emits a minimized numbered schema, caps the artifact at 256 KiB **before** a file exists, previews the exact bytes that get shared, prunes by age, has no network or upload path, and `DiagnosticCenter.structured()` has no message field to forget to strip. Canaries plant a secret in every raw field and every exception |

  Line 55: | ATLAS-10 | Phase 1 | Pending — **the loop is built; no device has run it**: `P9.1` delivers the fixture format, a recorder over the shipped transport, and a replay transport; `P13` delivers `AtlasDoctor`, which compares a replayed run with the stored report (`Reproduced`/`Diverged`/`Refused`, with each difference naming both sides, and a missing candidate interface kept as advice). New support is therefore rejectable by a failing comparison, and no downloaded code runs. What is missing is a `DEVICE`-origin fixture — every capture today is `HOST`, and labelled so |

  Line 56: | ATLAS-11 | Phase 1 | Pending — partial evidence: P0 harness and its negative controls |

  Line 57: | ATLAS-12 | Phase 1 | Pending — partial evidence: P0 deadline/entry-budget tests, P2 enforces operation/job deadlines, entry, byte and operation budgets **before** work and stops cleanly when a limit is reached, T2.4's concurrency cap is atomic (increment-then-check with rollback), and P12 plans which probes a job runs over the same budget with a skip reason per probe; off-main-thread execution is now source-verified (`AtlasViewModel` launches every job on `dispatchers.io`, and `AtlasRepository` takes an injected dispatcher), while device timing remains open |


80 matches limit reached. Use limit=160 for more, or refine pattern.
```

---

## Assistant (Code · GPT-6 Astra · 16.0s)

_Thinking:_

**Clarifying exploration needs**

I need to ensure that I'm informed and have a solid background as I explore the user's question. It shouldn't be overly broad or ambiguous, so I should consider asking the user about their specific goals after looking at the available information. I wonder if I could read through three relevant documents to get a clearer understanding. This might help me determine actionable paths without needing to write anything extensive or recommend scopes without proper context.

**Tool: task**

**Input:**
```json
{
  "subagent_type": "explore",
  "description": "Map Max Atlas gaps",
  "background": true,
  "prompt": "Task ATLAS-SCOPE-20260921, research only, no writes and no build. User Arabic request: wants development of Max Atlas in this project but no particular feature specified. Role brief docs/ai/team/architect.md; read order docs/ai/HANDOFF.md Read order (focus current Atlas passages, docs may be stale, verify code). Thoroughness medium. Map actual Max Atlas production entrypoints core/atlas, hardware Atlas integrations, UI diagnostics/viewmodel and tests. Identify 2-3 concrete bounded development directions backed by actual code (not just historical notes), including safest useful default and key files/risk. Do not perform full audit; do not implement or run builds. Return concise Arabic or English summary with references and distinguish existing implemented behavior from unwired pieces. No model routing capabilities should be assumed."
}
```

**Output:**
```
<task id="ses_f3c52514affezdTXZoKxZL1HmE" state="running">
<summary>Background task started</summary>
<task_result>
The task is working in the background. You will be notified automatically when it finishes.
DO NOT sleep, poll for progress, ask the task for status, or duplicate this task's work — avoid working with the same files or topics it is using.
Work on non-overlapping tasks, or briefly tell the user what you launched and end your response.
</task_result>
</task>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/PROJECT_STATE.md",
  "limit": 200
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/PROJECT_STATE.md</path>
<type>file</type>
<content>
1: # PROJECT_STATE
2: 
3: Updated: 2026-09-15 (Architect pass, exploration + vision only — no app code changed)
4: 
5: ## What MaxManager is
6: 
7: An Android 11+ performance suite with two halves that ship together:
8: 
9: 1. **Magisk/KernelSU module** (`mainfiles/`, `binprofiles/`, `binutils/`, `archdaemon/`, `preloadbin/`, `thermalcore/`) — Rust/shell binaries that apply profiles, CPU/GPU policies, I/O scheduler, thermal service, game preload.
10: 2. **Manager app** (`manager/app`, package `nd.max`, Kotlin + Compose + Hilt) — the control surface and the home of **Max AI**, an autonomous knob-level optimizer with a safety governor.
11: 
12: Version: `5.2` / `Dazzling` (`version`, `version_type`, `module.json`). License Apache-2.0. Localized to ~100 locales via Crowdin.
13: 
14: ## Repo state at handoff
15: 
16: - HEAD: `4e5d833 Update MaxManager`, branch has a dirty working tree (intentional, previous agent's work in progress):
17:   - Modified: `AdaptiveProfileEngine`, `HardwareControlArbiter`, `ControlOutcomeModel`, `MaxAiEngine`, `MinimalPlanner`, `ResponseModel`, `SafetyGovernor`, `Dex2oatScreen`, `DozeModeScreen`, `FpsOverlayScreen`, `ResolutionScreen`, `TouchBoostScreen`, `ZramManagerScreen`, `Dex2oatViewModel`, `ZramViewModel`, `README.md`.
18:   - Untracked: `manager/app/src/main/java/nd/max/ui/design/` (the new Design Language), `res/values/max_design_strings.xml`, `res/values/max_screen_strings.xml`, `manager/kernel-flasher/schemas/`.
19: - **Nothing is committed for the redesign yet.** Do not `git checkout`/`stash` these paths.
20: - No `docs/ai/` existed before this pass; `docs/aegis/` holds the older spec/plan/work records (still useful history, superseded as product direction).
21: 
22: ## Build/verification reality on this machine
23: 
24: - `java` = OpenJDK 25.0.4, `gradle` on PATH = **4.4.1** (too old), wrapper wants **9.5.1** from `services.gradle.org` and the network is unavailable → **a real Gradle compile cannot be run here.**
25: - Consequence: verification is static (see `VALIDATION.md`). Every task must be written so it can be verified without compiling, and must state honestly that compilation is environmentally unverified.
26: 
27: ## Phase status
28: 
29: | Phase | Status |
30: | --- | --- |
31: | Design Language foundation (`ui/design/`) | Done, 7 files, used by 6 screens |
32: | Screen rebuilds on the new language | 6 of ~46 done |
33: | Max AI control plane (keys, arbiter, safety, planner) | Core done, UI exposure poor |
34: | Information architecture / navigation | **Not started — this is the next bottleneck** |
35: | Product vision | Defined in `DESIGN_VISION.md` (this pass) |
36: 
37: ## Next executable unit
38: 
39: `NEXT_TASK.md` → **NT-01: Navigation spine + Control hub.** Read `HANDOFF.md` first.

(End of file - total 39 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/DESIGN_VISION.md",
  "limit": 160
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/DESIGN_VISION.md</path>
<type>file</type>
<content>
1: # DESIGN_VISION
2: 
3: The product direction MaxManager should be rebuilt toward. This supersedes earlier UI plans (`docs/aegis/*`, `manager/FINAL_UI_AUDIT.md`) as *product direction*; it keeps and extends the `ui/design/` language.
4: 
5: ## 1. Positioning
6: 
7: > **MaxManager is a control plane for Android performance that can prove what it is doing.**
8: 
9: Not a tweak launcher. Not a skin. Three promises, in priority order:
10: 
11: 1. **Truth** — every number states how fresh it is and where it came from; nothing is invented, nothing stale is shown as live.
12: 2. **Safe control** — every write is a transaction: owner, captured baseline, verified read-back, rollback on failure, and a stated reason when a control is locked.
13: 3. **Auditable intelligence** — Max AI acts autonomously, but always explains what it changed, why, what it measured, and hands control back the moment the user touches a knob.
14: 
15: Design rule derived from this: **if a pixel does not carry truth, control, or explanation, it is decoration and must justify its cost.**
16: 
17: ## 2. What is wrong today (one line each)
18: 
19: - Two navigation models for the same four screens (D-01).
20: - The best engineering in the repo is hidden behind a non-primary route (D-02).
21: - 40+ flat routes for ~9 device domains, with duplicate and dead routes (D-03).
22: - Tweaks is a menu of screen names, not a view of the device (D-04).
23: - Two design systems; the good one covers 13% of screens (D-05).
24: - Decoration competes with the product's own claim of efficiency (D-07).
25: 
26: ## 3. New information architecture
27: 
28: Four primary destinations. Settings leaves the bottom bar; Max AI enters it.
29: 
30: | # | Destination | Answers | Absorbs |
31: | --- | --- | --- | --- |
32: | 1 | **Now** | “What is my device doing right now, and what changed?” | HomeScreen, dashboard details, recommendations, reboot/profile actions, activity journal |
33: | 2 | **Control** | “What can I change, what is applied, what is risky?” | Tweaks + all 24 feature subscreens, grouped into 8 domain hubs |
34: | 3 | **Apps** | “How does this specific app behave?” | Applist, per-app settings, process manager, debloat/freeze |
35: | 4 | **Max AI** | “Who is controlling my hardware, and why?” | MaxAiScreen + ownership map + decision journal + safety + learning |
36: 
37: Settings becomes a top-bar entry from **Now** (avatar/gear), containing: Appearance (theme, color scheme, wallpaper, ambient), App behaviour, Module & updates, Advanced tools, Diagnostics, Logs, About.
38: 
39: ### Control → 8 domain hubs (replaces 19 flat rows)
40: 
41: | Hub | Merges today's | Hub summary line shows |
42: | --- | --- | --- |
43: | **CPU** | `cpucorecontrol`, `governorsettings`, MTK CPU/Freq/PPM | cluster count, governor, live max freq, manual session state |
44: | **GPU** | `gpustudio` (+ drop `maligpufreq`/`adrenogpufreq`), MTK DRAM/Boost | driver, freq ladder, applied tweaks |
45: | **Memory** | `zrammanager`, swappiness/VM parts of `preferenced` | ZRAM size/algorithm, pressure |
46: | **Display** | `displaystudio`, `resolutionscreen`, refresh-rate controls | resolution, refresh rate, DPI |
47: | **Responsiveness** | `touchboost`, `fpsgoscreen`, `FasScreen`, `fpsoverlay` | touch provider, frame scheduler, overlay on/off |
48: | **Thermal** | `thermal_detail`, thermalcore controls, safety limits | hottest zone, throttle state |
49: | **Power** | `chargingscreen`, `bypasschg`, `bypasschg_check`, `dozemode` | charge state, bypass capability, doze policy |
50: | **Storage & compiler** | `dex2oat` (App Compiler), `storage_detail`, `debloatfreeze` storage side | free space, compiler filter |
51: | *(plus)* **Network** | `networkscheduler`, `network_detail` | scheduler, live throughput |
52: 
53: Each hub is one `MaxScreen` whose sections are today's screen bodies, reused not rewritten. A feature only keeps a dedicated screen when it is a deep workspace (CPU core grid, GPU ladder, per-app editor, kernel flasher).
54: 
55: ### Deletions / demotions
56: 
57: - Delete the pager navigation model and the `use_scroll_animation` flag (D-01).
58: - Delete route aliases `maligpufreq`, `adrenogpufreq`.
59: - Demote decorative engines (`WeatherEffects`, `VideoWallpaperPlayer`, `PulseFieldEngine`, `AmbientMotifOverlay`) behind one **Appearance → Ambient effects** switch, default **off**, with an honest cost note. Delete whatever no screen references after the migration.
60: - Retire `ui/components/` (merge its two files into `ui/component/`), then shrink `ui/component/` as screens migrate to `ui/design/`.
61: - `terminal`, `setedit`, `activitylauncher`, `kernelflasher` move under **Settings → Advanced tools** with an explicit risk gate, not the main IA.
62: 
63: ### New surfaces worth building
64: 
65: 1. **Command palette** (search icon in every top bar): fuzzy search over controls, apps, sysfs knobs and settings, returning direct actions. Solves discoverability of 40+ features without deep menus — the single highest-leverage new UI element.
66: 2. **Ownership strip** (`MaxOwnerChip`): on every control row/hub — `You` / `Max AI` / `Per-app` / `Module` / `Kernel default`, tap to release or lock. This is the UI that finally exposes the arbiter + `ManualControlLocks`.
67: 3. **Change journal** (in Now, and filtered per hub): timestamped stream of "who changed what, verified or rolled back", from `EventLog` + arbiter outcomes. Turns Max AI from a black box into a ledger.
68: 4. **Device truth sheet** (Diagnostics rebuilt): per-knob capability proof — readable / writable / ladder-proved / unsupported, straight from `HardwareCapabilityResolver`. Replaces feature-level "supported?" guessing.
69: 5. **Session bar**: when a manual session or an AI action is active, a persistent one-line bar with "applied / verifying / rolled back" and an undo affordance.
70: 
71: ## 4. Screen anatomy (one shape, every screen)
72: 
73: ```
74: top bar: title · subtitle(provider/path) · [search] [refresh] [actions]
75: [session bar]            ← only while a write/AI action is in flight
76: [condition]  OR  [banner + sections]
77: section: title + why-line
78:   group (hairline): rows (48dp min) — control rows | metric lines | destinations
79: ```
80: 
81: Rules (already encoded in `ui/design/`, to be enforced repo-wide):
82: - Screens never build their own `Scaffold`, gutters, or section spacing.
83: - Capability and state are never expressed by the same visual (the TouchBoost bug class).
84: - A disabled control always states its reason (`lockedReason`).
85: - Every metric carries `MaxDataTrust` + source.
86: - Colour is never the only carrier of meaning (tone + icon + label).
87: - Elevation only for floating surfaces; grouping is borders + whitespace.
88: - Motion budget ≤ `MaxDuration.deliberate` (360ms) and direction-neutral (RTL-safe).
89: 
90: ## 5. Design-language additions needed
91: 
92: Add to `ui/design/` (new files, no rewrites of existing ones):
93: 
94: | Component | Purpose |
95: | --- | --- |
96: | `MaxHubScreen` / `MaxDomainCard` | domain hub shell + summary row with live state |
97: | `MaxOwnerChip` | knob ownership + release/lock action |
98: | `MaxJournalRow` | one audit entry: actor, knob, before→after, verdict |
99: | `MaxSessionBar` | in-flight write/AI session with undo |
100: | `MaxRiskDialog` | destructive/irreversible confirm with named consequence |
101: | `MaxCommandPalette` | global search & jump |
102: | `MaxNumeric` | tabular-figure text style so live numbers stop jittering |
103: | `MaxSparkline` | the already-tokenized 28dp history strip, extracted |
104: 
105: ## 6. Non-negotiables for every task from here
106: 
107: 1. No behaviour regression in `core/maxai` / `core/hardware`; UI adapts to the control plane, never bypasses it (no direct `RootFileAccess.write` from UI).
108: 2. Every new user-visible string goes into `values/` **and** `values-ar/` in the same change.
109: 3. Every screen touched must leave `ui/design/` adoption strictly better, never worse.
110: 4. Static verification is mandatory (see `VALIDATION.md`); compilation is environmentally unverified and must be reported as such.
111: 5. Nothing is deleted without checking references first; dead code is removed in the same task that orphans it.
112: 
113: ## 7. Rollout order (why this order)
114: 
115: 1. **NT-01 Navigation spine + Control hubs** — unlocks every later task and removes the dual-nav tax. *(next task)*
116: 2. NT-02 Max AI as a primary destination: ownership map + journal + session bar.
117: 3. NT-03 Migrate hub bodies to `ui/design/` domain by domain (Power, Display, CPU, GPU…), deleting legacy components as they are orphaned.
118: 4. NT-04 Apps destination: per-app profile as one workspace.
119: 5. NT-05 Settings & Appearance consolidation + ambient-effects gate + advanced-tools risk gate.
120: 6. NT-06 Command palette + device truth sheet.
121: 7. NT-07 Localization parity sweep + adoption architecture test made blocking.

(End of file - total 121 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/ENGINEERING-CONTRACT.md",
  "limit": 250
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/ENGINEERING-CONTRACT.md</path>
<type>file</type>
<content>
1: MaxManager — Engineering Contract
2: 
3: > مرجع ملزم لكل وكيل: هذا الملف كان باسم `CLAUDE.md.bak`، ونُقل إلى هنا في 2026-09-18 ليصبح مرجعًا صريحًا
4: > بدل ملف باسم امتداد احتياطي. يُشار إليه من `AGENTS.md` وملفات `docs/ai/team/*`.
5: 
6: 1. Core Rules
7: 
8: - Understand before changing. Read the relevant code and existing patterns first.
9: - Never guess when the repository or tools can provide the answer.
10: - Verify libraries, APIs, functions, files, and behavior before relying on them.
11: - Prefer existing utilities and patterns over creating duplicates.
12: - Make the smallest change that correctly solves the problem.
13: - Do not modify unrelated code.
14: - Do not claim something was tested, built, inspected, or verified unless it actually was.
15: - If uncertain, state the uncertainty instead of inventing an answer.
16: - Push back on technically bad or unsafe ideas with concrete reasoning.
17: 
18: 2. Investigation
19: 
20: Before implementation, determine:
21: 
22: - Where the behavior originates.
23: - Which symbols/components are involved.
24: - Who calls the affected code.
25: - Relevant state, lifecycle, threading, and failure paths.
26: - Existing tests and conventions.
27: 
28: For non-trivial work, follow:
29: 
30: "Understand → Investigate → Plan → Implement → Verify → Review"
31: 
32: Use Serena first for semantic code navigation:
33: 
34: "find_symbol"
35: "find_referencing_symbols"
36: "find_implementations"
37: "find_declaration"
38: "get_symbols_overview"
39: "get_diagnostics_for_file"
40: 
41: Use normal search for strings, resources, Gradle, scripts, configuration, and logs.
42: 
43: 3. Task Triage
44: 
45: Before substantial work, classify:
46: 
47: "small | medium | large"
48: 
49: - Small: mechanical/non-behavioral change. Targeted checks only.
50: - Medium: localized behavior change or bug fix. Test the affected area.
51: - Large: cross-module feature, architecture, security, or high-risk change. Use broader investigation and verification.
52: 
53: Use the smallest verification scope that matches the change's blast radius.
54: 
55: 4. Context Discipline
56: 
57: Treat context as a limited resource.
58: 
59: - Do not dump large files when relevant symbols/sections are enough.
60: - Do not repeatedly inspect unchanged information.
61: - Do not explore unrelated directories.
62: - Use scripts for deterministic/repetitive work.
63: - Use Serena for semantic navigation instead of broad manual exploration.
64: 
65: 5. Android / Kotlin
66: 
67: Follow the project's existing architecture.
68: 
69: Prefer:
70: 
71: - Idiomatic Kotlin.
72: - Coroutines and Flow.
73: - Lifecycle-aware concurrency.
74: - Clear state ownership.
75: - Existing dependency-injection patterns.
76: - Jetpack Compose conventions.
77: - Testable, small functions.
78: 
79: Never perform blocking or expensive work on the main thread.
80: 
81: For root/system operations, handle failures, exit codes, cancellation, cleanup, and device/ROM differences explicitly.
82: 
83: 6. Build Efficiency
84: 
85: Do not run Gradle/NDK builds by default. Standing instruction from the user
86: (2026-09-16): skip builds instead of spending time and tokens on them; verify
87: statically. Builds here are Android Gradle builds with Hilt/KSP and cost minutes
88: per run, while a static pass catches the mistakes a compile would.
89: 
90: Prefer:
91: 
92: "inspect → edit → static verification → report"
93: 
94: Run a Gradle/NDK build only when the user explicitly asks for one.
95: 
96: Static verification checklist for every edit:
97: 
98: - bracket balance of each edited file, with string literals and comments
99:   stripped first (comments and strings legitimately contain unbalanced braces),
100: - imports: nothing imported is now unused; nothing used is missing an import,
101: - re-read the edited range: confirm nesting, scope receivers (RowScope /
102:   LazyListScope / ColumnScope) and named parameters,
103: - for any changed shared component, grep every call site and confirm the
104:   signature, parameter names and defaults still match,
105: - Serena `get_diagnostics_for_file` when the language server is available.
106: 
107: Never claim a change compiles or works because it was not built — say what was
108: checked and what was not. Do not skip necessary validation merely to save time;
109: the static pass *is* the validation here, and anything that cannot be validated
110: that way must be reported as unverified.
111: 
112: 7. Testing & Verification
113: 
114: Every meaningful behavioral change must be verified.
115: 
116: - Bug fixes should include regression coverage when practical.
117: - Test what was actually touched.
118: - Expand verification according to blast radius.
119: - Investigate failures before retrying.
120: - Never repeat the same failed action without understanding why it failed.
121: - Never substitute mocked behavior for testing the real logic.
122: 
123: Before reporting completion, inspect the resulting diff and verify the requested behavior.
124: 
125: 8. Critical Review
126: 
127: Before accepting a non-trivial implementation, try to falsify it.
128: 
129: Identify at least one concrete failure mode and its mitigation.
130: 
131: For high-blast-radius changes, identify multiple failure modes.
132: 
133: Ask:
134: 
135: «What could make this fail in production?»
136: 
137: Do not stop at the happy path.
138: 
139: 9. Safety
140: 
141: Never expose or commit:
142: 
143: - API keys
144: - tokens
145: - passwords
146: - signing keys
147: - keystores
148: - credentials
149: 
150: Never perform irreversible actions without explicit user permission:
151: 
152: - commit/push/deploy
153: - force-push
154: - reset --hard
155: - destructive deletion
156: - destructive database operations
157: 
158: When told to stop, stop completely.
159: 
160: 10. Code Quality
161: 
162: - Prefer self-documenting code.
163: - Comments should explain non-obvious intent, constraints, workarounds, or regressions.
164: - Avoid speculative abstractions.
165: - Avoid unnecessary dependencies.
166: - Avoid large rewrites when a focused fix is sufficient.
167: - Before deleting existing code, understand why it exists.
168: 
169: 11. Completion
170: 
171: Do not consider a task complete merely because code was written.
172: 
173: Report:
174: 
175: - What changed.
176: - Why it changed.
177: - What was actually verified.
178: - What remains unverified.
179: 
180: Use:
181: 
182: "DONE" — implementation and appropriate verification complete.
183: 
184: "DONE_WITH_CONCERNS" — implementation complete but meaningful verification remains.
185: 
186: "BLOCKED" — progress requires an external dependency or decision.
187: 
188: Never hide incomplete work.
189: 
190: 12. Communication
191: 
192: Be direct and technical.
193: 
194: For non-trivial proposals show:
195: 
196: What — change
197: Why — reason
198: Where — affected files/symbols
199: Risk — concrete failure + mitigation
200: How — implementation/verification
201: 
202: When explaining how something works, trace the actual code path rather than giving a generic explanation.
203: 
204: When multiple valid approaches materially differ, show the trade-offs before choosing.
205: 
206: 13. MaxManager Priorities
207: 
208: Pay particular attention to:
209: 
210: - Android lifecycle.
211: - Root/system operations.
212: - Concurrency.
213: - Performance/jank.
214: - Background execution.
215: - Boot-time behavior.
216: - APK installation/signing.
217: - Native/NDK code.
218: - Android/ROM/device compatibility.
219: - Security boundaries.
220: 
221: Prefer evidence from:
222: 
223: "source + Serena diagnostics + logs + targeted tests"
224: 
225: over assumptions.

(End of file - total 225 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/DECISIONS.md",
  "limit": 260
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/DECISIONS.md</path>
<type>file</type>
<content>
1: # DECISIONS
2: 
3: Binding decisions from the architect pass (2026-09-15). Executors follow these unless a decision is explicitly revised here. Format: decision → why → consequence.
4: 
5: ## ADR-01 — One navigation model; the pager mode is deleted
6: **Why:** `use_scroll_animation` produces two runtime navigation architectures for the same four screens, doubling every nav concern (D-01), and it is invisible to users.
7: **Consequence:** delete the `main` pager route, the flag read in `MainActivity`, and its settings toggle. Bottom bar + nav rail is the only model.
8: 
9: ## ADR-02 — Routes become a typed registry outside `MainActivity`
10: **Why:** 40+ string-literal routes inline in an 817-line activity produced dead aliases and untestable navigation (D-03).
11: **Consequence:** `ui/navigation/MaxDestinations.kt` is the single source of truth (route id, title res, icon, parent, risk level, deep-link key). `navigate("literal")` outside that package becomes a static-test violation.
12: 
13: ## ADR-03 — Primary destinations are Now / Control / Apps / Max AI; Settings leaves the bottom bar
14: **Why:** Settings held a primary slot while navigating to two destinations, and Max AI — the product differentiator — had no primary entry (D-02, D-09).
15: **Consequence:** Settings is reached from the Now top bar and aggregates appearance, module/update, diagnostics, logs, advanced tools, about.
16: 
17: ## ADR-04 — Control is organized by device domain, not by screen name
18: **Why:** ~9 real domains were split across 19 launcher rows with overlapping scope (D-03, D-04).
19: **Consequence:** 8–9 domain hubs (CPU, GPU, Memory, Display, Responsiveness, Thermal, Power, Storage & compiler, Network). A dedicated screen survives only for deep workspaces (CPU core grid, GPU ladder, per-app editor, kernel flasher).
20: 
21: ## ADR-05 — Merge by absorption, never by rewrite
22: **Why:** six screens were just rebuilt on the new language; re-doing them for the sake of the new IA would burn work and risk regressions (explicit user constraint).
23: **Consequence:** hubs host existing screen bodies as sections; migration of a body to `ui/design/` happens in its own later task, not in the IA task.
24: 
25: ## ADR-06 — `ui/design/` is the only design system; `ui/component/` is legacy in runoff
26: **Why:** two parallel systems, the better one covering 13% of screens (D-05).
27: **Consequence:** new UI imports `nd.max.ui.design` only. No new `Scaffold(` in screen files. Legacy components are deleted in the task that orphans them. `ui/components/` is folded into `ui/component/` and the duplicate package name disappears.
28: 
29: ## ADR-07 — Data trust is a product rule, not a screen detail
30: **Why:** `MaxDataTrust` + source labelling is the credible differentiator in a category full of fake telemetry (D-06).
31: **Consequence:** any surfaced number must carry freshness + source; unknown renders as `status_unknown`, never as a plausible value. Synthesizing or interpolating telemetry is a blocking defect.
32: 
33: ## ADR-08 — Capability and state must be visually separate
34: **Why:** the TouchBoost rebuild proved the old pattern conflated "device supports it" with "it is on".
35: **Consequence:** every control surface shows a providers/capability section (with node path + trust) distinct from its state switches; every disabled control supplies `lockedReason`.
36: 
37: ## ADR-09 — Ownership of every knob is user-visible
38: **Why:** the arbiter, manual locks, and cross-process ownership journal already exist but are invisible where they matter (D-02).
39: **Consequence:** `MaxOwnerChip` (You / Max AI / Per-app / Module / Kernel default) renders on control rows and hub summaries and can release or lock, backed by `HardwareControlArbiter` + `ManualControlLocks`. No new ownership concept is invented in the UI layer.
40: 
41: ## ADR-10 — Max AI must be auditable: ledger over dashboard
42: **Why:** trust in autonomy comes from history, not from status cards.
43: **Consequence:** a change journal (actor, knob, before→after, verified/rolled back, measurement) is a first-class surface in Now and in Max AI. `MaxAiScreen`'s generic cards are replaced by: objective, ownership map, journal, safety, learning credibility.
44: 
45: ## ADR-11 — UI never writes hardware directly
46: **Why:** bypassing the control plane breaks leases, baselines, rollback, and safety veto.
47: **Consequence:** no `RootFileAccess`/shell writes from composables or screen ViewModels; all writes go through the arbiter/control-plane APIs.
48: 
49: ## ADR-12 — Efficiency is part of the brand: decoration is opt-in and off by default
50: **Why:** blur, ambient glow, motif overlays, weather effects, and video wallpaper cost frames and battery in an app that promises to save both (D-07).
51: **Consequence:** one Appearance → Ambient effects switch, default off, with an honest cost note; effect engines are not composed when off; unreferenced engines are deleted.
52: 
53: ## ADR-13 — Semantic tones stay hard-coded, not dynamic
54: **Why:** Positive/Caution/Critical must survive any wallpaper-derived palette; a "critical" that renders pastel green is a safety bug.
55: **Consequence:** keep `MaxTone`'s fixed hues and the light/dark surface derivation in `MaxTokens.kt`; dynamic colour only drives Neutral/Accent.
56: 
57: ## ADR-14 — Localization parity is part of "done"
58: **Why:** new screens added 188 English-only keys in an RTL-first, ~100-locale app (D-10).
59: **Consequence:** every task that adds user-visible copy updates `values/` **and** `values-ar/` in the same change; no string literals in Compose.
60: 
61: ## ADR-15 — Static verification is the gate; compilation is best-effort
62: **Why:** no usable Gradle/JDK toolchain combination here (D-11).
63: **Consequence:** each task ships with grep/python contract checks (`VALIDATION.md`) and, where cheap, a JVM source-scan test in the existing architecture-test style. Reports must say "compilation unverified in this environment" rather than implying a build passed.
64: 
65: ## ADR-16 — High-risk tools are gated, not featured
66: **Why:** Terminal, SetEdit, ActivityLauncher, KernelFlasher can brick or wedge a device and currently sit beside ordinary tweaks.
67: **Consequence:** they live under Settings → Advanced tools with an explicit risk gate and `MaxRiskDialog` on destructive actions; risk level is declared in the destination registry (ADR-02).
68: 
69: ## ADR-17 — Root/module status becomes one app-level session state
70: **Why:** root is re-probed on every navigation event and each screen invents its own unsupported story (D-08).
71: **Consequence:** a single observable session state (root, module, SELinux, capability snapshot) provided once; screens read it and render `MaxCondition` from it. Not in NT-01's scope, but no new per-screen probes may be added.
72: 
73: ## ADR-18 — Nothing already-built is redone for aesthetic reasons
74: **Why:** explicit user constraint and respect for sunk, good work.
75: **Consequence:** the six migrated screens and the control-plane work are treated as foundations; they change only when a functional rule above (ownership chip, journal, localization) requires an additive change.
76: 
77: ## ADR-19 — دفتر الحلقات هو الذاكرة السردية، لا العدّادات
78: العدّادات الأربعة و"آخر إجراء" كانت تُهدر سلسلة السبب/النتيجة كل ٣٠ ثانية. صار كل قرار حلقة دائمة مُسلسلة حقلًا بحقل.
79: 
80: ## ADR-20 — إظهار المرشحين المرفوضين
81: القرار غير مفهوم بدون البدائل. `planWithTrace` يُخرج كل مرشح مع سبب الاستبعاد (`measured_harm`/`predicted_harm`/`no_step`/`unreadable`).
82: 
83: ## ADR-21 — `NO_ACTION` حدث يُسجَّل
84: المراقبة الواعية ليست خمولًا؛ تُسجل حلقة بلا تنفيذ (بخنق ٥ دقائق) كي لا يبدو النظام ميتًا حين يقرر ألا يتدخل.
85: 
86: ## ADR-22 — التنبؤ يُقارن بالقياس دائمًا
87: كل حلقة تحفظ `predictedGain` و`predictionErrorGain = |تنبؤ − مقيس|`، فتُعرض دقة النموذج بدل ادعائها.
88: 
89: ## ADR-23 — ما لا يُقاس يُعرض كغير متوفر
90: لا صفر افتراضي ولا رسم وهمي: `after == null` ⇒ "تعذر القياس"، والخط الزمني فارغ قبل أول حلقة حقيقية.
91: 
92: ## ADR-24 — شريط التطور في الذاكرة فقط
93: عيّنات الجلسة الحالية (120) لا تُحفظ على القرص كي لا يُعرض رسم "حي" من جلسة سابقة.
94: 
95: ## ADR-26 — كل ملف نصوص يُسجَّل في Crowdin صراحةً
96: **القرار:** `crowdin.yml` يسرد في `files:` كل ملف في `values/` يحمل نصًّا مرئيًا للمستخدم، وملف نصوص جديد يُسجَّل في نفس التغيير. التجاهل المقصود يُكتب لا يُترك ضمنيًا.
97: **لماذا:** كانت ٤٧١ نصًّا في ٥ ملفات (`max_ai` ٢٢٠، `max_screen` ١٧٧، `max_navigation` ٥٤، `max_design` ١١، `studio` ٩) خارج خط الترجمة لأن `crowdin.yml` كان مربوطًا بـ`strings.xml` وحده — فبقيت إنجليزية في تطبيق من ~١٠٠ لغة.
98: **النتيجة:** أي ملف نصوص غير مسجَّل = عيب. المرجع للتحقق: `.planning/codebase/INTEGRATIONS.md` → «Localization pipeline». وملفات `values-ar` المصاحبة تبقى مصدر الحقيقة المتزامن (ADR-14).
99: 
100: ## ADR-27 — ثلاثة أشياء تصف اللغات الـ٨٥، وأداة واحدة تمنع تباعدها
101: **القرار:** عدد اللغات ومصدرها واحد — مجلدات `res/values-*` الفعلية. منها تُشتقّ `AppLanguage.CODES` (منتقي داخل التطبيق) و`res/xml/locales_config.xml` (ما يعرضه النظام)، و`tools/i18n_coverage.py --check-codes` يقارن الثلاثة ويفشل عند أي اختلاف.
102: **لماذا:** `setApplicationLocales` يُمرّر الوسم إلى نظام أندرويد، ومنذ API 33 يرفض النظام الطلب ما لم يُعرَّف `android:localeConfig` في البيان — فبدون الملف يعمل التبديل على الإصدارات الأقدم ويفشل صامتًا على الحديثة. ووصف اللغات في مكانين بلا أداة يعني أن يتباعدا بلا أن يلاحظ أحد.
103: **النتيجة:** لا تُضاف لغة في الكود قبل أن يوجد مجلدها، ولا يُحرّر `locales_config.xml` يدويًا. أكواد المجلدات القديمة (`values-in`, `values-iw`, `values-tl`) تُكتب في الكود بصيغتها الحديثة (`id`, `he`, `fil`) لأن النظام يعرض الحديثة و`aapt2` يوفّق بينهما. التفاصيل والبوابة: `VALIDATION.md` §3.1.
104: 
105: **مسلكان للتطبيق لا مسلك واحد:** `AppCompatDelegate.setApplicationLocales` مسالك النظام على API 33+، لكنه يمرّ عبر `AppCompatActivity` ونشاط التطبيق `ComponentActivity` بسمة منصّية — فهو لا يُطبّق اللغة على أندرويد ١٠–١٢ (`minSdk = 29`). لذلك `MainActivity.attachBaseContext` يلفّ السياق بـ`AppLanguage.wrap` (لغة + اتجاه تخطيط). حذف أحدهما يُسقط نسخة من نسخات أندرويد المدعومة.
106: 
107: ## ADR-28 — الترجمة تُجمع وتُتحقق آليًا، ولا تُخترع
108: **القرار:** لا تُكتب ترجمة آلية داخل المستودع من نموذج لغوي. `tools/i18n_coverage.py` يجمع المفاتيح الناقصة لكل لغة في CSV، ويكتب العائد منها بأسلوب **الإضافة فقط** بعد التحقق من الوسائط، والبشر أو Crowdin هم من يترجم.
109: **لماذا:** ٨٤ لغة ناقصة ‎١٤٤٠٤٣‎ مفتاحًا. الاختراع الصامت لترجمة بهذا الحجم يعطي نصًّا غير مُراجَع يبدو مكتملًا وينقل خطأً حقيقيًا في تطبيق يتحكم بمسارات الجذر — والصف الذي يطلب وسيطًا لا يمرّره الكود يُسقط التطبيق لا يُظهر نصًّا رديئًا.
110: **النتيجة:** النقص مُقاس ومعلَن (اللغة غير المترجمة تُظهر الإنجليزية تلقائيًا، وهذا سلوك صحيح لا عيب)، وكل صف مرفوض يُطبع مع سببه.
111: 
112: **تعديل 2026-09-18 — قرار المالك: الـ٨٥ لغة تُكتمل بالترجمة الآلية.** منعُ الاختراع الصامت لا يعني منع الترجمة: يُسمح بالترجمة الآلية عبر **مزوّد مُعلَن** بمفتاح يضعه المالك (`tools/i18n_translate.py`)، وبثلاث ضمانات لا يُتازل عنها: ① الوسائط والمصطلحات تُحجب قبل الإرسال بصور حارسة وتُستعاد بعده (فلا مزوّد يستطيع قلب `%1$s` أو حذفه)، ② المخرج **لا يُكتب في الموارد**: يمرّ على `--apply-csv` بأسلوب الإضافة فقط، ③ مخرج مزوّد `stub` لا يُدمج أبدًا. ما يبقى مسؤولية المالك: اختيار المزوّد وتكلفته (٤٣٥٩٤٦٣ حرفًا مصدرًا: ~٧٧–٩٦ $ مرة واحدة، أو ٩ أشهر على الحصة المجانية، والذاكرة المؤقتة تمنع إعادة الفاتورة)، ومراجعة لغوية لاحقة إن أراد جودة أعلى من الآلة.
113: 
114: ## ADR-29 — قائمة اللغات تُكتب يدويًا، وتوليد AGP الآلي مُعطَّل
115: **القرار:** `androidResources { generateLocaleConfig = false }` مع إبقاء `android:localeConfig="@xml/locales_config"` وقائمة اللغات الصريحة في `res/xml/locales_config.xml`.
116: **لماذا:** المشروع كان يحمل `generateLocaleConfig = true` (2026-09-17) بينما جولة اللغة (2026-09-18) أضافت قائمة صريحة وربطها بالبيان — وهذا مزيج يرفضه AGP: «Locale config generation was requested but user locale config is present in manifest» فيسقط `:app:processDebugMainManifest` ويفشل البناء كله (لم يُكشف إلا ببناء حقيقي في جولة ١٠).
117: **النتيجة:** اختيار واحد من الاثنين لا كليهما. اختيرت القائمة الصريحة لأنها المصدر الوحيد الذي يضمن ظهور **اللغات الـ٨٥ كاملة** في منتقي النظام — ومنها ما لا يستنتجه التوليد الآلي من أسماء المجلدات (`values-b+sr+Latn`, `values-zh-rHK`, `values-pt-rPT`) — ولأنها الوجهة التي يتحقق منها `tools/i18n_coverage.py --check-codes` (ADR-27) مقابل المجلدات والمنتقي. التوليد الآلي يُسقط هذا الضمان بلا أن يشتكي أحد.
118: **إثبات:** `:app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL، والبيان المدمج في الـAPK يحمل `android:localeConfig`، والحزمة تضم `res/xml/locales_config.xml` (`VALIDATION.md` §0).
119: 
120: ## ADR-25 — بدائل سينمائية بلا تبعية للنواة
121: `MaxAiCinematics.kt` لا يستورد `nd.max.core.*` ولا التنقل، فتبقى لغة التصميم قابلة لإعادة الاستخدام والاختبار.
122: 
123: ## ADR-30 — الوتيرة تُشتق من إشارات مجانية، بحد أدنى معلن (2026-09-18)
124: 
125: **القرار:** إعادة التخطيط المبكر (`MaxAiCadence`) تُقرَّر من ثلاث حقائق فقط، ولا شيء غيرها: حالة الشاشة، ونوع الاستخدام المقيس في `DeviceStateCollector` (لعبة/عادي/مطفأ)، ومستوى حاكم الأمان. الإشارة تُبنى من اللقطة التي تُجمَع كل ثانية أصلًا للحلقة السريعة، وبدون أي قراءة جذرية جديدة (اسم الحزمة يُقرأ في دورة القرار عند تنفيذها فقط). ولا يُسمح بدورتين متتاليتين بسبب تغيّر حقيقي قبل `MIN_ADAPTIVE_INTERVAL_MS = 10_000`، وهو مساوٍ لنافذة قياس الأثر في المحرك.
126: 
127: **لماذا:** كان المحرك يفكر كل ٣٠ ثانية، والدورة الفورية لا تقع إلا بطلب المستخدم — فمن فتح لعبة كان التاريخي أن ينتظر حتى ٣٠ ثانية كي يلاحظ Max AI تغيّر السياق. لكن إزالة الانتظار بإطلاق حلقة استطلاع سريعة كانت ستقلب المبدأ المعاكس (تحكم لا يتأرجح، ADR-23/CYCLE_MS): عشر ثوانٍ من عمر البطارية لأجل طمأنينة بصرية ليست ذكاءً. الحل بالتفريق: **الهدوء يبقى، والاستثناء مؤهَّل ومحدود**. والحرارة والحمل مستبعدان عمدًا من الإشارة لأن لكل منهما مسارًا أسرع (حاكم الأمان كل ثانية)، وإدخالهما كان يجعل المحرك يستيقظ كل ثانية بلا سبب جديد.
128: 
129: **النتيجة:** لا سلطة جديدة ولا كتابة عتاد جديدة — الدورة المستيقَظة هي `cycle()` نفسها بكل سقوفها. والحد الأدنى لا يُنسي التغيّر: الإشارة السابقة لا تُحدَّث حين تأجيل الاستيقاظ، فيبقى الفرق قائمًا ويُعرض في الواجهة كعدّاد تنازلي، ثم يقع الاستيقاظ في أول ثانية تنتهي فيها المهلة. وكل استيقاظ يُوثَّق في `DiagnosticCenter` بسببه.
130: 
131: ## ADR-32 — السقف الحراري منحنى لا عتبتين، وكل فرق عن القديم زيادةُ حماية (2026-09-18)
132: 
133: **القرار:** يُستبدل السقفان الثابتان (`ENGAGE_CAP_FRACTION = 0.55` داخل نطاق التدخل، `CRITICAL_CAP_FRACTION = 0.35` عند الحرجة) بمنحنى `MaxAiThermalCurve`: سقف متناسب مع مقدار التجاوز، وبند تكاملي مُقيَّد يشدّد ولا يرخي، وشرط تبريد متتالٍ قبل الاسترجاع، وتقريب إلى خطوات ٥٪ مع حد كتابة واحد كل ١٥ ثانية. **وتاكيداً على عدم المساس بقواعد السلامة**: منحنى المقدار لا يقرر **مستوى** الحماية — آلة الحالة في `SafetyEngine` (هستيريسيس ٥ درجات + تنبؤ أمامي + إعادة محاولة بتراجع أُسّي) تبقى كما هي، والمنحنى يترجم مستواها إلى رقم.
134: 
135: **لماذا:** أربع درجات من التجاوز كانت تُعالج بنفس القسوة، والدرجة الخامسة بشيء. والتحكم المُكمَّم (سلّم ترددات العتاد) لا يصلحه البند التناسبي وحده — وهو ما تعالجه نواة لينكس في حاكم `power_allocator` بثابت مخصوص (`integral_cutoff`) ومُعامِل مختلف للتجاوز عن ما دونه (`k_po` مقابل `k_pu`)، وما تعالجه المتحكمات العملية بلا تماثل مقصود (تصعيد فوري، ترخية بعد قياسات مستقرة). وهذا كله مذكور بدليله في `docs/ai/EXTERNAL-RESEARCH.md` §١ — لا واحد منه منقول، وإنما أعيد تصميمه على قيودنا.
136: 
137: **الدعوى القابلة للتكذيب (شرط القبول):** `MaxAiThermalCurve` **لا يعطي سقفًا أخفّ من السقف القديم في أي نقطة**، والبند التكاملي لا يُضاف أبدًا. `MaxAiThermalCurveTest` يفحص ذلك شاملةً على النطاق ٤٨°–٦٤° بخطوة ربع درجة، وعند الحدّين بالضبط، وبالتراكم الطويل، وبالإعداد الفاسد. أي أن كل فرق مالحظ عن السلوك السابق هو كتابة في اتجاه الأمان، وهذا ما يجعل التغيير قابلًا للتسليم قبل تجربته على جهاز.
138: 
139: **الحدود المُعلنة (لا تُدّعى):** كسب البند التكاملي (`0.0015`) ومدى الأرضية (`0.25`) قيم تصميمية غير معايَرة على جهاز — وهي مذكورة كخطر متبقٍّ في `HANDOFF.md`. وأي تغيير يمسّ هذا المنحنى لاحقًا يُثبت بنفس الاختبار أو يُرفض.
140: 
141: ## ADR-31 — نطاق التعلّم لكل سياق، ولا يُخلط بعمود واحد (2026-09-18)
142: 
143: **القرار:** حكم المقبض يُحسب ويُعرض **داخل سياقه**: `Snapshot.knobs` تبقى أحكام السياق العام `*` (دلالة لم تتغير لأي شاشة قائمة)، و`knobsByContext` يحمل حكم كل سياق بمفاتيحه، و`contexts` مرتّبة: العام أولًا ثم الأغزر عيناتٍ ثم الأبجدي كسرًا للتعادل. والواجهة تُختار نطاقًا واحدًا وتقرأ أرقامه وحده — بلا دمج ولا معدّل مرجّح بين التطبيق والجهاز.
144: 
145: **لماذا:** نواة التعلّم كانت تقيس الأثر بمفتاح `(مقبض، اتجاه، سياق)` وتُغذّي السياقين معًا (الخاص والعام) من كل قياس، ثم يقرأ الاستخلاص **السياق العام وحده** ويعرضه للمستخدم. أي أن أدقّ ما يعرفه المحرك — «هذا المقبض ينفع في هذه اللعبة» — كان يُقاس ثم يُخفى، ويُستبدل بمتوسط عام يمكن أن يصفر بحق. وعرض المعدّل واحدًا كان سيكذب في الاتجاهين: مقبض نافع في تطبيق واحد يبدو فاشلًا بعد خلطه بسياق خامل، ومقبض عادل يبدو نافعًا بعد خلطه بسياق استثنائي.
146: 
147: **النتيجة:** `contextLabel` يحوّل اسم الحزمة إلى اسم التطبيق حين يسمح النظام، ويعود إلى اسم الحزمة حين لا يسمح — لا يُخترع اسم غير معروف (ADR-07/ADR-23). ومعه **تدقيق المحرك على نفسه**: صدق التنبؤ (`PREDICTION_TOLERANCE_GAIN = 5 × HELPFUL_GAIN`، مشتقّة لا مُختارة، و`hitRate` هي الحكم) وحاصل التجارب ونسب التجاوز والقياس، وكلها بمقام صفري يُعرض كغير متوفر لا كصفر (ADR-23). القاعدة الجامعة: **الرقم الذي يقيس المحرك نفسه يُعرض كما يقيسه الآخرون، لا كما يريد أن يُقاس.**
148: 
149: ## ADR-33 — يُقاس ما هو زمن لا ما هو حالة، والغياب يُكتب غيابًا لا صفرًا (2026-09-18)
150: 
151: **القرار:** يُضاف مقياس زمني واحد هو **ميزانية الحرارة** (`MaxAiThermalBudget`): الزمن من بدء حمل ثقيل مقيس إلى أول خنق، يُشتق من نفس قياسات الحلقة السريعة بلا عتاد جديد، والمحرك يستدعي `observe(...)` مرة كل ثانية و`statusOf(...)` للعرض. وثلاث قواعد قبول ملزمة: ① الجلسة التي تنتهي **بلا خنق** تُعرض «جلسة نظيفة» لا رقمًا مُقدّرًا؛ ② التدخل الثاني في الجلسة نفسها لا يُعيد قفل الميزانية (الرقم عمر الجلسة حتى **أول** خنق لا آخر خنق)؛ ③ إنهاء الجلسة بإطفاء الشاشة مع قيام الحمل **لا يُحتسب صمودًا** — لا نعرف هل صمد العتاد أم توقف الطلب. ومعها **ذيل الأثر** في `MaxAiInsights.DeltaSpread` (`worst/p10/median/best`) للقرارات المقيسة وحدها، بترتيب الأقرب لا بالاستقراء، وباستبعاد التجارب المعرفية وحلقات السلامة من التوزيع.
152: 
153: **لماذا:** كل ما يعرضه Max AI كان يصف **حالة** (حرارة، حمل، عدد تدخلات)، وسؤال اللاعب الحقيقي زمني: «هل أعتمد على هذا الجهاز ساعة كاملة أم يهبط بعد عشر دقائق؟». وأدبيات التخفيف الحراري في AOSP تقترح هذا القياس صراحةً (تسجيل `t0` عند `THERMAL_STATUS_NONE`، وقياس المنقضي إلى أول خفض)، وهو XR-05 في `docs/ai/EXTERNAL-RESEARCH.md`. وأما الذيل فلأن المتوسط يخفي الذيل: مكبسب متوسطه `+0.02` قد يأتي من عشرة نجاحات وخسارة كبيرة واحدة، والمتوسط وحده يقول «نجحنا».
154: 
155: **النتيجة (درس عيب حقيقي):** كشفت الاختبارات أن `sessionStartedAtMs = 0L` كان يُستعمل كعلامة «لا جلسة»، والصفر لحظة زمنية صحيحة (بدء التشغيل) — فقراءة ساعة عند الصفر كانت تلغي الجلسة صامتةً بلا خطأ ظاهر. أُبدلت العلامة بحقول `Long?`. **القاعدة المستخلصة:** استعمال قيمة من صميم مدى المُدخل كعلامة حالة ممنوع؛ الغياب يُكتب `null` (أو حقل منطقي صريح) حتى لو كان المُدخل في الإنتاج لا يساوي الصفر عمليًا — لأن العقد لا يُبنى على ما تصادف أن المُستدعي يمرّره.
156: 
157: **الخوارزميات المُعاد تصميمها (لا منقولة):** ترتيب الأقرب لـ`p10` (لا استقراء خطي بين عينتين — لا يُعرض رقم لم يُقس)، ووسيط حقيقي لحجم زوجي، وتوزيع مبني من `kind == DECISION` لا من كل حلقة. والمصادر في §١ و§٣ من `EXTERNAL-RESEARCH.md`.
158: 
159: **الحدود المُعلنة:** `MIN_SESSION_MS = 30s` و`DEMANDING_INTENT = 0.75` قيم تصميمية **غير معايَرة على جهاز**؛ و«حمل ثقيل» مشتق من `appIntent` المُقدّر لا من إطارات حقيقية — فجلسة مُصنَّفة خطأً تُقاس خطأً. مُسجَّلة في I-60/I-61.
160: 
161: ## ADR-34 — ضغط الذاكرة يُقاس بPSI لا بنسبة الامتلاء، والخنق مانع لا مشجّع (2026-09-18)
162: 
163: **القرار:** تُضاف إشارة `memoryStall` من `/proc/pressure/memory` (`full avg10`)، مقروءة بـ`MemoryPressureReader` ومحلَّلة بـ`MemoryStall` النقي. وتُستخدم في اتجاه واحد فقط: **تمنع رفع الأداء** حين يتجاوز الخنق [STALL_FRACTION]. ومعه قاعدتان: ① غير المدعوم (`UNSUPPORTED`) **لا يمنع شيئًا** — الجهل ليس خنقًا؛ ② الاستقصاء مُخزَّن سلبيًا: نواة أعلنت عدم الدعم لا تُسأل إلا كل [RETRY_AFTER_MS]، لأن `RootFileAccess.read` يجرّب قشرة عند فشل القراءة المباشرة — فاستقصاء كل ثانية كان يعني عملية قشرة كل ثانية مقابل قياس لا يُنتج رقمًا.
164: 
165: **لماذا:** `DeviceStateCollector.memoryUsage` يقيس **الامتلاء** لا **الأثر**؛ جهاز بـ٩٠٪ ذاكرة مستخدمة قد يكون سريعًا تمامًا وآخر بـ٦٠٪ في خنق شديد. والمقياس الصحيح موجود في النواة أصلًا: `some` (توقف بعض العمل) و`full` (توقف كل العمل غير الخامل = thrashing)، وعليه بُني `lmkd`. واستعماله في اتجاه واحد مقصود: رفع الترددات على جهاز يتوقف منتظرًا الذاكرة لا يعالج السبب بل يزيد الطاقة على نفس العمل المتوقف.
166: 
167: **الدعوى القابلة للتكذيب:** المنع **لا يستطيع إلا أن يقلب رفعًا إلى توفير**، ولا يقلب شيئًا في الاتجاه المعاكس، ولا يعمل بلا قياس. `ObjectiveTest` يفحص ذلك بتسلسل خنق تصاعدي (null → 1.0): رفع عند الأول، ثم منع دائم لا يعود إلى رفع. و`MemoryStallTest` يفحص الرفض: نواة تصدّر `some` وحدها تُعلن `UNSUPPORTED` (عرضها مكان `full` كان سيقلب «انتظار عادي» إلى «خنق»)، والأرقام المشوّهة (`NaN`/`Infinity`/نص) تُرفض لا تُقصّ، وما خرج عن المدى يُقصّ إلى حدّه.
168: 
169: **عبء إضافي مُعلن:** إشارة ثامنة في اللقطة، **لا تُمرَّر إلى موصل RL الأصلي** (عقده سبع إحداثيات ثابتة — زيادتها كانت ستقلب معنى كل وزن في النموذج).
170: 
171: **الحدود المُعلنة:** `STALL_FRACTION = 0.10` غير معايَرة على جهاز، ومسار القراءة الفعلي (صلاحية `/proc/pressure/memory` بلا جذر على إصدارات مختلفة) لم يُجرَّب على جهاز — مُسجَّل في I-65.
172: 
173: ## ADR-35 - Observable intent and evidence-first workspaces (2026-09-19)
174: 
175: Max AI remains one local control plane. UI preferences reflect observable engine state, not a remembered imperative getter. Request progress is distinct from verified hardware state; a tap never establishes success. Presentation freshness expires independently of engine emissions, rejects future timestamps as live, and never invents a reading before the first sample. A presentation clock does not request hardware reads.
176: 
177: The Max AI destination separates overview, journal, learning and controls while retaining the causal evidence and existing live command centre. Journal queries operate on recorded fields, preserve order, and do not mutate learning. Learning contexts are selected by stable key so new samples cannot silently switch the selected app. All changes reuse `ui/design/`, paired EN/AR strings, existing arbiter ownership and existing safety policy.
178: 
179: ## ADR-36 — إعادة بناء واجهة مدير الملفات على طلب المالك، والنموذج لا يُمَس (2026-09-19)
180: 
181: **القرار:** أُعيد بناء **عرض** شاشة مدير الملفات (`FM-02`) استجابةً لطلب المالك الصريح بأنها «عديمة الفائدة
182: وغير متناسقة وصعبة الاستخدام»، وبمرجع سبع لقطات لمدير ملفات آخر قُرئت بنصوصها (‏`tools/read_image_text.py`).
183: والتغيير مقيَّد بحدّين: ① طبقة العرض — والنموذج الخالص يُعدَّل **إضافةً** لا إبدالًا (`EntryCounts` · `DiskSpace`
184: · `isHidden/withoutHidden/counts` · `showHidden`/`tabs` · `FileSystemEngine.diskSpace`)، و② كل ما يُثبت
185: السلامة يبقى كما هو: الحرس قبل الـshell، والأحكام الثلاثة للنتيجة، والإعلان عن المجهول («غير مقروءة» لا صفرًا).
186: 
187: **لماذا لا يخالف `ADR-18`:** ذاك يمنع إعادة ما بُني **لأسباب جمالية**. والطلب هنا استعمالي مُعلن، والتشخيص
188: مقيس لا مُدَّعى: أربعة أشرطة قبل أول صفّ في كل لوح، وأفعال موزّعة على ثلاثة أشرطة بأيقونات بلا أسماء،
189: وصفوف بسطرين تحمل الصلاحيات في كل صفّ. فالقاعدة تُطبَّق على «أعيدوا بنائه ليبدو أحدث» لا على «لم يعد صالحًا
190: للاستعمال».
191: 
192: **النتيجة الملزمة للجولات القادمة:**
193: 
194: 1. **بنية الشاشة معلنة**: شريط أوامر الشاشة في الأعلى · **شريط سفلي واحد** يتبدّل دوره (أدوات اللوح النشط،
195:    أو إجراءات التحديد) · وداخل كل لوح: تبويباته · مساره · سطر حالته · قائمته — ولا شريط خامس يُضاف.
196: 2. **لكل فعل اسمه**: لا زرّ أيقونة في هذه الشاشة بلا `contentDescription`، ولا إجراء في قائمة إلا بنصّه.
197: 3. **الإخفاء يُعلَن**: كل ترشيح أو إخفاء يقصّ ما يُرى يجب أن يُصرّح بعدد ما أخفى في سطر الحالة — «مخفيّ
198:    ومُعلَن» غير «مفقود».
199: 4. **ما لا يُقاس لا يُعرض**: لا حجم لمجلد غير مقيس، ولا إجراء لا ينطبق (`FileActionSet`)، ولا مساحة تُكتب
200:    صفرًا حين لم تُقرأ.
201: 5. **التبويبات تخصّ اللوح** وتُحفظ مع حالته (`PaneSaver`)، ولا تُبنى من نصّ محفوظ في مكان آخر.
202: 
203: **الحدود المُعلنة:** لم تُرَ الشاشة على جهاز؛ وعدد التبويبات الكبير في لوح بعرض ~١٧٠ نقطة لم يُقس
204: (‏`HANDOFF` تكملة ٤١ §٦).
205: 
206: ## ADR-37 — أطلس: بنك معرفة ثانٍ من مشاريع مفتوحة المصدر — **مفردات واجهات لا جداول مسارات** (2026-09-20)
207: 
208: **السياق.** أمر المالك: «وخذ من SmartPack وغيرها من مشاريع مفتوحة المصدر معرفتهم، لأنهم يجمعون هذه
209: الأشياء لسنوات، فإذا ما هو مخزّن عندنا لا يعمل يقوم أطلس بإكماله تلقائيًا». وهذا يقع على `D-02`
210: و`ATLAS-01` اللذين يمنعان «نسخ الأكواد والسكربتات وقواعد المسارات»، وعلى حكم `S01` نفسه: «Do not import
211: path maps, scripts, custom controls, or generic governor defaults».
212: 
213: **القرار.** يُضاف `AtlasCommunityBank` كبنك ثانٍ، و`AtlasCompletion` كمرحلة ثالثة في المسح، بهذه الحدود:
214: 
215: 1. **ما يُنقل مفردات الواجهات**: اسم عقدة ↔ وحدة موثّقة ↔ جذر مُعدَد يمكن السير فيه. **ولا** جدول مسارات
216:    لكل جهاز، ولا سكربت، ولا معامل تحويل منسوخ، ولا سطر كود.
217: 2. **كل مدخل يحمل `provenance`** (مصدر + مرجع + إصدار + سجلّ ترخيص)، ويمرّ **بنفس** مدقّق الكتالوج المراجَع:
218:    جذر مُعتمد، اسم أساس آمن، لا مفاتيح مكرّرة، ولا تعارض وحدات.
219: 3. **الثقة لا تتجاوز `CLAIMED`/`FETCHED`**: ولا مدخل واحد بـ`SOURCE_VERIFIED`، لأنّ لا تنفيذ فُحص في هذه الجولة.
220: 4. **القراءة منه لا تُرقّى**: `semanticStatus = INFERRED` ومرحلة `CANDIDATE_INTERFACE` — واسم واجهة معروف
221:    الوجود ليس معنًى مُراجَعًا.
222: 5. **لا يُسأل إلا عن نقص**: النطاقات التي لم يُجب عنها البنك الأول، ولا تُعاد قراءة واجهة قُرئت، بحدّ ٣٢ مرشّحًا
223:    في المسح مضافًا إلى ميزانية النقل نفسها.
224: 6. **لا يحرّك بوابة التقرير**: `reviewedUnresolved` يحصي نقص المراجَع وحده؛ غياب اسم مرشّح هو الحال المتوقَّعة.
225: 
226: **البديل المرفوض.** نسخ خريطة مسارات SmartPack كما هي: تخلط GPL-3.0 بـApache-2.0، وتُدخل «تخمينًا عند
227: الفشل» (قائمة حكام ثابتة عند تعذّر القراءة)، وتحوّل غياب الاسم إلى غياب الواجهة — وهو ما يرفضه أطلس.
228: 
229: **العاقبة.** `D-02` تبقى قائمة للكود والسكربتات وجداول المسارات، ويُستدرك عليها بالمعرفة بصيغة المفردات
230: مع الإسناد. وهذا الاستدراك **لا يوسّع الصلاحيات**: البنك الثاني يقرأ من نفس الأسطح المعتمدة، وبلا جذر،
231: وبلا كتابة، وبميزانية واحدة مع البنك الأول.

(End of file - total 231 lines)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 119.9s)

_Thinking:_

**Considering development efficiency**

I’ve been calculating the context and time budget, figuring we might have around 36k tokens remaining from the 54k maximum, which feels quite substantial. It’s clear that I need to be efficient since the user is only looking for broad information on development before honing in on specifics. I also need to run a quick exploration and set up the gates before we clarify what we need to do, especially regarding baseline requirements.

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/kt_balance.py --self-test",
  "description": "Validate structural checker against known fixtures",
  "timeout": 120000
}
```

**Output:**
```
  ✓ توازن سليم: متوقّع 0 · حاصل 0
  ✓ قوس ناقص: متوقّع 1 · حاصل 1
        السطر 1: «{» لم يُغلق
  ✓ قوس زائد: متوقّع 1 · حاصل 1
        السطر 2: «}» بلا فاتح يقابله
  ✓ قوس داخل نص — لا يُحسب: متوقّع 0 · حاصل 0
  ✓ قوس داخل تعليق — لا يُحسب: متوقّع 0 · حاصل 0
  ✓ تعليق كتلة متداخل: متوقّع 0 · حاصل 0
  ✓ تعليق كتلة ناقص: متوقّع 1 · حاصل 1
        السطر 1: تعليق كتلة لم يُغلق
  ✓ نص خام فيه إشارات: متوقّع 0 · حاصل 0
  ✓ نص خام ناقص: متوقّع 1 · حاصل 1
        السطر 1: نص لم يُغلق
  ✓ قالب نصّي فيه نص داخلي: متوقّع 0 · حاصل 0
  ✓ نص عادي ناقص: متوقّع 1 · حاصل 1
        السطر 1: نص عادي عَبَر نهاية السطر بلا إغلاق
  ✓ محرف قوس — لا يُحسب: متوقّع 0 · حاصل 0
  ✓ اسم بعلامتين مائيّتين فيه فاصلة عليا: متوقّع 0 · حاصل 0
  ✓ اسم بعلامتين مائيّتين فيه قوس: متوقّع 0 · حاصل 0
  ✓ اسم بعلامتين مائيّتين لم يُغلق: متوقّع 1 · حاصل 1
        السطر 2: اسم بين علامتين مائيّتين لم يُغلق
  ✓ XML سليم: متوقّع 0 · حاصل 0
  ✓ XML مبتور: متوقّع 1 · حاصل 1
قياس الأداة: 17 حالة · إخفاقات 0

```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/kt_balance.py --assert",
  "description": "Measure Kotlin and XML structural baseline",
  "timeout": 120000
}
```

**Output:**
```
توازن البنية: 756 ملفًا · عوائق 0

```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/code_health.py --assert",
  "description": "Measure repository health and debt baseline",
  "timeout": 120000
}
```

**Output:**
```
════════════════════════════════════════════════════════════════════════
الصحّة — المطلوب صفر
════════════════════════════════════════════════════════════════════════
  ✓ package_mismatch: 0
  ✓ unresolved_resource: 0
  ✓ duplicate_string_key: 0
  ✓ stray_root_file: 0

════════════════════════════════════════════════════════════════════════
الدَّين — مُجمَّد عند سقف، لا يُسمح له بالنمو
════════════════════════════════════════════════════════════════════════
     10  oversized_files
     29  own_wildcard_imports
     63  hardcoded_ui_literals
     23  presentation_hw_writes
      2  todo_markers (تُراجَع، لا تُفرض)
    236  platform_wildcard_imports (لا تُحتسب)

  ملفات Kotlin: 575  ·  الأسطر: 135053

  أكبر الملفات (تجاوزت 1000 سطر):
     2451  manager/app/src/main/java/nd/max/AppMonitor.kt
     1843  manager/app/src/main/java/nd/max/ui/mainscreens/MaxAiScreen.kt
     1787  manager/app/src/main/java/nd/max/core/maxai/MaxAiEngine.kt
     1561  manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt
     1282  manager/app/src/main/java/nd/max/ui/subscreens/CustomThemeScreen.kt
     1147  manager/app/src/main/java/nd/max/ui/component/HomeComponents.kt
     1147  manager/app/src/main/java/nd/max/ui/flasher/KernelFlasherScreen.kt
     1118  manager/app/src/main/java/nd/max/ui/subscreens/CpuCoreControlScreen.kt
     1092  manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt
     1086  manager/app/src/main/java/nd/max/ui/component/ExpressiveListComponent.kt

  استيرادات شاملة لكود المشروع نفسه (تُخفي مصدر الرمز):
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/MainActivity.kt
    nd.max.ui.navigation  <-  manager/app/src/main/java/nd/max/MainActivity.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/MainActivity.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/component/ModuleBackupComponent.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/ui/component/SettingsHeaderComponent.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/mainscreens/HomeDashboardComponents.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
    nd.max.ui.viewmodel  <-  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
    nd.max.ui.mainscreens  <-  manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt
    nd.max.ui.subscreens  <-  manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt
    nd.max.ui.subscreens.hubs  <-  manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/AboutScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/BypassCheckScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/ColorSchemeScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/CpuCoreControlScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/CustomThemeScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/DebloatFreezeScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/DisplayStudioScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/FasSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/FpsGoSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt
    nd.max.ui.mainscreens  <-  manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/LogsViewerScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/PreferencedTweakScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/ProcessManagerScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/SetEditScreen.kt

  كتابة عتاد من طبقة العرض (ADR-11) — 23 موضعًا:
     10  manager/app/src/main/java/nd/max/ui/viewmodel/ZramViewModel.kt
      3  manager/app/src/main/java/nd/max/ui/viewmodel/TweakViewmodel.kt
      2  manager/app/src/main/java/nd/max/ui/subscreens/BypassChargeScreen.kt
      2  manager/app/src/main/java/nd/max/ui/subscreens/BypassCheckScreen.kt
      1  manager/app/src/main/java/nd/max/ui/mainscreens/GetStartedScreen.kt
      1  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
      1  manager/app/src/main/java/nd/max/ui/subscreens/ZramManagerScreen.kt
      1  manager/app/src/main/java/nd/max/ui/viewmodel/AppSettingsViewmodel.kt
      1  manager/app/src/main/java/nd/max/ui/viewmodel/DisplayStudioViewModel.kt
      1  manager/app/src/main/java/nd/max/ui/viewmodel/NetworkSchedulerViewModel.kt

  نصوص واجهة صلبة (أول 15 من 63):
    manager/app/src/main/java/nd/max/service/FpsOverlayService.kt: 'FPS'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'Not exported'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'System'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'Activities'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'Display Settings'
    manager/app/src/main/java/nd/max/ui/activitylauncher/FloatingActivityService.kt: 'Activity Logger'
    manager/app/src/main/java/nd/max/ui/activitylauncher/FloatingActivityService.kt: 'Preferences'
    manager/app/src/main/java/nd/max/ui/activitylauncher/FloatingActivityService.kt: 'Scanning Activities...'
    manager/app/src/main/java/nd/max/ui/component/StudioComponents.kt: 'MHz'
    manager/app/src/main/java/nd/max/ui/component/StudioComponents.kt: 'CPU'
    manager/app/src/main/java/nd/max/ui/component/ThermalPulseCard.kt: 'Temperature'
    manager/app/src/main/java/nd/max/ui/design/MaxHelp.kt: '\\u2022'
    manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt: 'Live hardware health'
    manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt: 'Active control ownership'
    manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt: 'Hardware report'

الحصيلة: صحّة نظيفة

بوابة النظافة: exit 0

```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/i18n_coverage.py --assert",
  "description": "Measure translation integrity and coverage baseline",
  "timeout": 120000
}
```

**Output:**
```
مجلدات values-*: 84 + en  ·  أكواد المنتقي: 85  ·  locales_config: 85
تطابق الأكواد الثلاثة: OK
عوائق (specifiers/تكرار/تطابق الأكواد): 0

```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/VALIDATION.md",
  "limit": 360
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/VALIDATION.md</path>
<type>file</type>
<content>
1: # VALIDATION
2: 
3: How to verify work in this environment. **البناء صار مُتحقَّقًا هنا (2026-09-18)**: Android SDK مثبَّت في `~/android-sdk`، والبوابات الثابتة أدناه تبقى الفحص السريع، والبناء هو الإثبات النهائي.
4: 
5: Run everything from repo root: `/mnt/sdcard/MaxManger/optmize-main` (على الجهاز) أو `/workspaces/Hi/MaxManager` (في حاوية CI/التطوير).
6: 
7: ## 0. Build — مُتحقَّق 2026-09-18 (كان يفشل: لا SDK)
8: 
9: البيئة المقيسة: **JDK 17** (`/usr/lib/jvm/java-17-openjdk-amd64`) · AGP **9.2.0** · Kotlin **2.3.10** · wrapper **Gradle 9.5.1** · `compileSdk 36`.
10: SDK: `~/android-sdk` = `platform-tools` 37.0.1 + `platforms;android-36` + `build-tools;36.0.0` (نُزِّل بـ`android sdk install`؛ `sdkmanager` صار مُهمَلًا).
11: 
12: ```sh
13: export ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk"
14: export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
15: cd manager
16: # ① مسار الـPR في CI (debug): اختبارات + APK
17: bash gradlew -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8" \
18:   :app:testDebugUnitTest :app:assembleDebug --build-cache --parallel
19: # ② اختبارات الوحدة غير المشغَّلة في CI (145 اختبارًا)
20: bash gradlew :terminal-emulator:testDebugUnitTest
21: # ③ مسار الإصدار بلا توقيع: R8 + تقليص الموارد + اختبارات release
22: bash gradlew :app:testReleaseUnitTest :app:minifyReleaseWithR8 :app:optimizeReleaseResources
23: ```
24: 
25: **النتائج الفعلية (كلها `BUILD SUCCESSFUL`):**
26: 
27: | الأمر | النتيجة |
28: | --- | --- |
29: | `:app:testDebugUnitTest :app:assembleDebug` | **5m54s** · 137 مهمة · **128 اختبارًا، 0 فشل** · APK `111,527,920` بايت |
30: | `:terminal-emulator:testDebugUnitTest` | **22s** · **145 اختبارًا، 0 فشل** (18 صنفًا) |
31: | `:app:testReleaseUnitTest :app:minifyReleaseWithR8 :app:optimizeReleaseResources` | **7m02s** · 128 اختبارًا، 0 فشل · R8 بلا أصناف مفقودة |
32: 
33: **تحقّق الـAPK** (كما يفعل CI): `apksigner verify` → `Verifies` ومخطّط v2 = true · `aapt dump badging` → `package: name='nd.max' versionCode='1' versionName='1.0'` · البيان المدمج يحمل `android:localeConfig` · والحزمة تضم `res/xml/locales_config.xml`.
34: 
35: ### 0.1 جولة NT-15-MAXAI — 2026-09-18 (تغيير Max AI + واجهته)
36: 
37: ```sh
38: export ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk"
39: export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
40: cd manager
41: bash gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin   # 2m13s · BUILD SUCCESSFUL
42: bash gradlew :app:testDebugUnitTest                                   # **160 اختبارًا، 0 فشل، 0 مُتخطّى**
43: bash gradlew :app:assembleDebug                                       # 2m34s · APK `116,778,525` بايت
44: ```
45: 
46: | الأمر | النتيجة |
47: | --- | --- |
48: | `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` | BUILD SUCCESSFUL **2m13s** |
49: | `:app:testDebugUnitTest` | BUILD SUCCESSFUL · **160 اختبارًا، 0 فشل، 0 مُتخطّى** (كان 128) |
50: | `:app:assembleDebug` | BUILD SUCCESSFUL **2m34s** · APK `116,778,525` بايت |
51: 
52: **عيب حقيقي كشفه البناء**: فاصلة عليا `'` غير مهرَّبة في نصّين إنجليزيين جديدين أسقطت `:app:mergeDebugResources` برسالة `Invalid unicode escape sequence` — **لا بوابة ثابتة تكشف هذا**؛ الدرس يستحق التسجيل: كل تسليم واجهة يُبنى، وتشغيل `aapt2` هو الفحص الوحيد الذي يرى أخطاء XML الحقيقية. الإصلاح: `\'` (نفس نمط `max_live_block_budget` القائم).
53: 
54: **الذي لا يُدّعى هنا**: مراجعة السلامة (I-61) والعرض على جهاز حقيقي (I-60).
55: 
56: ### 0.2 جولة FM-02 — 2026-09-19 (إعادة بناء شاشة مدير الملفات)
57: 
58: **تغيير في وضع البيئة، لا في الكود:** هذه الشجرة (`/workspaces/Ai`) لم يكن فيها SDK ولا JDK 17 أصلًا —
59: بينما `AGENTS.md` §5 يوثّق `~/android-sdk` وJDK 17. أُعيد إنشاء الوضع نفسه **بإذن المالك**، ثم بُني فعلًا:
60: 
61: ```sh
62: sudo apt-get install -y openjdk-17-jdk-headless          # 17.0.20
63: # SDK: cmdline-tools (11076708) + platform-tools + platforms;android-36 + build-tools;36.0.0 → ~/android-sdk
64: # manager/local.properties: sdk.dir=$HOME/android-sdk   (وهو في .gitignore فلا يدخل المستودع)
65: export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
66: export ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk"
67: cd manager
68: bash gradlew :app:compileDebugKotlin --build-cache --parallel -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
69: bash gradlew :app:testReleaseUnitTest :app:assembleDebug -x :app:lintVitalRelease --build-cache --parallel -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
70: bash gradlew :app:minifyReleaseWithR8 --build-cache --parallel -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
71: ```
72: 
73: | الأمر | النتيجة الحرفية |
74: | --- | --- |
75: | `:app:compileDebugKotlin` | **BUILD SUCCESSFUL in 1m 55s** · صفر خطأ · صفر تحذير في ملفات الجولة |
76: | `:app:testReleaseUnitTest :app:assembleDebug` | **BUILD SUCCESSFUL in 3m 42s** (وقياس أول 8m37s وفيه فشل واحد في اختبار جديد أُصلح) · **679 tests completed, 0 failed, 0 errors, 0 skipped** · APK `118,792,784` بايت في آخر بناء |
77: | `:app:minifyReleaseWithR8` | **BUILD SUCCESSFUL in 5m 35s** |
78: | `python3 tools/code_health.py --assert` | `exit 0` · «صحّة نظيفة» · الدَّين `10 / 29 / 66 / 26` (لا ارتفاع عن السقف) |
79: | `python3 tools/i18n_coverage.py --assert` | `exit 0` · عوائق `0` · تطابق الأكواد الثلاثة OK |
80: | `python3 tools/repo_audit.py` | `PROBLEMS: 0` |
81: 
82: **ما لا يُدّعى في هذه الجولة:** لم تُفتح الشاشة على جهاز (RTL · حجم خط كبير · لمس · جذر حقيقي)،
83: ولم تُلتقط لقطة، وشريط التبويبات عند ٦ تبويبات في لوح بعرض ~١٧٠ نقطة لم يُقس. والتوقيع بالإصدار يبقى
84: غير مُختبَر (يحتاج `KS_PWD`).
85: 
86: **④ الإصدار الموقّع — مُتحقَّق 2026-09-18 بعد توليد keystore جديد:**
87: 
88: ```sh
89: export KS_PWD="$(cat /tmp/keystore-new-password.txt)"   # السر ليس في المستودع
90: bash gradlew :app:testReleaseUnitTest :app:assembleRelease --build-cache --parallel
91: ```
92: 
93: `BUILD SUCCESSFUL in 6m13s` · 203 مهمة (منها `minifyReleaseWithR8` و`optimizeReleaseResources` و`lintVitalRelease`) · 128 اختبارًا 0 فشل.
94: `app-release.apk` = **20,780,698 بايت** (مقابل 111,527,920 لـdebug ⇒ R8 والتقليص عاملان) · `apksigner verify` → **Verifies** (v2) · بصمة الموقّع = `72e335af259a9770c79c5e2c66d2f7afbc194492b979444d2acab839022f0fc0` وهي المطابقة لـ`EXPECTED_RELEASE_SIGNER_SHA256` في `build.yml`.
95: 
96: **اللغات تنجو من مسار الإصدار (مُثبت):** `aapt dump configurations` = **89 تهيئة لغة في debug وrelease بالضبط**، و`type 17 (string) configCount=91`. تقليص الموارد يحذف كثافات (127→110 تهيئة) ولا يحذف لغات، ويُعيد تسمية مسارات الملفات (`res/xml/locales_config.xml` → `res/Br.xml` بنفس الحجم 7868) مع بقاء المورد بمعرّفه `nd.max:xml/locales_config`. **درس**: «ملف مفقود» في الحزمة المصغَّرة قد يكون مُعاد التسمية — افحص بجدول الموارد لا بأسماء الملفات.
97: 
98: **ما يبقى غير مُتحقَّق (لا تدّعِه):**
99: - التوقيع في **CI**: يحتاج ضبط سر `KEYSTORE_PASSWORD` بالكلمة الجديدة (البصمة في `build.yml` حُدِّثت بالفعل).
100: - البناء بلا `KS_PWD` **يفشل عمدًا**: `KS_PWD must be set to produce a signed release artifact` (`app/build.gradle.kts:30`) — لأن APK غير موقّع غير صالح كـpriv-app.
101: - سلوك تبديل اللغة على **جهاز حقيقي** (١٠–١٢ و١٣+).
102: - ملاحظة AGP 9: `shrinkReleaseRes` لم تعد موجودة؛ البديل `optimizeReleaseResources`.
103: 
104: ## 1. Hygiene gates (always)
105: 
106: ```sh
107: git status --porcelain
108: git diff --check                       # whitespace errors / conflict markers
109: grep -rn '<<<<<<<\|>>>>>>>' manager/app/src/main/java | grep -v Binary || echo OK
110: ```
111: 
112: ## 2. Kotlin structural sanity (per changed file)
113: 
114: ```sh
115: python3 - <<'PY'
116: import re,sys,subprocess
117: files=subprocess.run(["git","diff","--name-only","--diff-filter=ACM"],capture_output=True,text=True).stdout.split()
118: bad=[]
119: for f in [x for x in files if x.endswith(".kt")]:
120:     s=open(f,encoding="utf-8",errors="replace").read()
121:     s=re.sub(r'""".*?"""','""',s,flags=re.S)
122:     s=re.sub(r'(?<!\\)".*?(?<!\\)"','""',s)
123:     s=re.sub(r'//[^\n]*','',s); s=re.sub(r'/\*.*?\*/','',s,flags=re.S)
124:     for o,c in [("{","}"),("(",")"),("[","]")]:
125:         if s.count(o)!=s.count(c): bad.append((f,o,s.count(o),s.count(c)))
126: print("UNBALANCED:",bad) if bad else print("balanced OK")
127: PY
128: ```
129: Known exception: `AppMonitor.kt` (I-43) fails this naively at HEAD — ignore it unless you edited it.
130: 
131: ## 3. Resource gates
132: 
133: ```sh
134: # every XML the build reads parses — values*/, res/xml/ and the manifest.
135: # Not just the strings: res/xml/ holds files the manifest points at, and an XML comment may not
136: # contain a double hyphen. That rule caught a real defect on 2026-09-18: a locales_config.xml
137: # comment held the literal flag of a shell command, and aapt2 would have failed the build on it.
138: python3 -c "import glob,xml.etree.ElementTree as T;f=glob.glob('manager/app/src/main/res/values*/*.xml')+glob.glob('manager/app/src/main/res/xml/*.xml')+['manager/app/src/main/AndroidManifest.xml'];[T.parse(p) for p in f];print(len(f),'xml OK')"
139: 
140: # no duplicate keys inside a values folder
141: python3 - <<'PY'
142: import glob,collections,xml.etree.ElementTree as T
143: for d in sorted(glob.glob('manager/app/src/main/res/values*')):
144:     names=[e.get('name') for p in glob.glob(d+'/*.xml') for e in T.parse(p).getroot()]
145:     dup=[k for k,v in collections.Counter(names).items() if v>1]
146:     if dup: print('DUP',d,dup)
147: print('dup scan done')
148: PY
149: 
150: # Arabic parity for keys added in this change (ADR-14)
151: git diff -U0 -- manager/app/src/main/res/values | grep -o 'name="[^"]*"' | sort -u > /tmp/added.txt
152: while read -r k; do grep -qr "$k" manager/app/src/main/res/values-ar/ || echo "MISSING_AR $k"; done < /tmp/added.txt
153: 
154: # FULL parity — file level: every EN string file must have an AR counterpart
155: # (this is the gate that would have caught I-30: two files existed in EN only)
156: for f in manager/app/src/main/res/values/*.xml; do b=$(basename "$f");
157:   grep -q '<string ' "$f" || continue
158:   [ -f "manager/app/src/main/res/values-ar/$b" ] || echo "MISSING_AR_FILE $b"
159: done; echo "AR file parity done"
160: 
161: # FULL parity — key AND format-specifier level, per file pair (ADR-14 + ADR-26)
162: # The specifier rule is a SUBSET rule, not equality:
163: #   * AR asks for an argument the caller does not pass  -> CRASH (IllegalFormatException) => ERROR
164: #   * AR drops an argument that the caller still passes -> ignored by String.format  => WARN
165: #     (dropping is legitimate: EN uses %2$s as an English plural suffix "s", e.g. detail_readable_paths)
166: python3 - <<'PY'
167: import re, xml.etree.ElementTree as ET, os
168: R = "manager/app/src/main/res"
169: def load(p):
170:     return {e.get("name"): "".join(e.itertext())
171:             for e in ET.parse(p).getroot() if e.get("name")}
172: spec = re.compile(r'%\d+\$[sd]|%[sd]')
173: errors, warns = [], []
174: for f in sorted(os.listdir(f"{R}/values")):
175:     if not f.endswith(".xml") or not os.path.exists(f"{R}/values-ar/{f}"):
176:         continue
177:     en, ar = load(f"{R}/values/{f}"), load(f"{R}/values-ar/{f}")
178:     miss = sorted(set(en) - set(ar))
179:     if miss:
180:         warns.append(f"{f}: {len(miss)} keys not translated yet (coverage, Crowdin's job)")
181:     for k in en:
182:         if k not in ar:
183:             continue
184:         extra = set(spec.findall(ar[k])) - set(spec.findall(en[k]))
185:         if extra:
186:             errors.append(f"{f}:{k} AR needs {sorted(extra)} that callers never pass")
187:     names = [e.get("name") for e in ET.parse(f"{R}/values-ar/{f}").getroot() if e.get("name")]
188:     dup = sorted({n for n in names if names.count(n) > 1})
189:     if dup:
190:         errors.append(f"{f}: duplicate keys in AR {dup}")
191: for w in warns:
192:     print("WARN ", w)
193: for e in errors:
194:     print("ERROR", e)
195: print("key/specifier parity done — ERROR count:", len(errors))
196: PY
197: 
198: # every EN string file must be registered in the translation pipeline (ADR-26)
199: for f in manager/app/src/main/res/values/*.xml; do b=$(basename "$f");
200:   grep -q '<string ' "$f" || continue
201:   grep -q "values/$b" crowdin.yml || echo "NOT_IN_CROWDIN $b"
202: done; echo "crowdin registration done"
203: ```
204: 
205: ### 3.1 Language gate (ADR-27)
206: 
207: Three artifacts describe the same 85 languages and must not drift apart: the shipped folders
208: (`res/values-*`), the in-app picker (`AppLanguage.CODES`), and the system-visible list
209: (`res/xml/locales_config.xml`, required by `setApplicationLocales` on API 33+). One command
210: compares all three and, at the same time, re-checks every locale for specifier and duplicate-key
211: defects. Coverage numbers are printed but never fail the gate — translating 84 locales is
212: Crowdin's job, not a build blocker.
213: 
214: > **Why the batch command exists (`--todo`)**: the handoff recipe used to say "read
215: > `build/i18n/todo_<locale>.txt`". `build/` is gitignored, and **`glob` and search do not see it** — we tested
216: > it. So that instruction was unrunnable for any fresh agent, and the file could not be discovered even by
217: > accident. It is a command now. The same reasoning applies to every tool here: each resolves the repo root
218: > from `__file__`, because a gate that silently returns `0` when run from the wrong directory is worse than no
219: > gate at all.
220: 
221: ```sh
222: # ERROR (exit 1) only for real defects: specifier a locale asks for but the code never passes,
223: # duplicate keys, or the three language lists disagreeing.
224: python3 tools/i18n_coverage.py --assert
225: 
226: # what each locale still misses, per file, with the English source text
227: python3 tools/i18n_coverage.py            # coverage table for all 84 locales
228: python3 tools/i18n_coverage.py --locale de          # one locale, key by key
229: 
230: # a translation batch, straight to stdout — NOT a file under build/
231: python3 tools/i18n_coverage.py --todo de | sed -n '1,200p'
232: python3 tools/i18n_coverage.py --todo de | wc -l    # how many are left (de = 1726)
233: python3 tools/i18n_coverage.py --write-manifests    # build/i18n/to_translate_<locale>.csv (all locales)
234: 
235: # merge machine/human translations back — append-only, validated before writing, --dry-run first
236: python3 tools/i18n_coverage.py --locale de --apply-csv build/i18n/to_translate_de.csv --dry-run
237: ```
238: 
239: Filling all 84 locales is a separate, explicit step with a named provider (ADR-28, amended 2026-09-18).
240: No key is needed to see the cost first, and the provider's output never lands in the resources directly:
241: it goes through the same `--apply-csv` gate above.
242: 
243: ```sh
244: python3 tools/i18n_translate.py --estimate                      # 144,043 strings / 4,359,463 source chars, no calls
245: python3 tools/i18n_translate.py --provider deepl --locales all # DEEPL_API_KEY, resumable via build/i18n/cache/
246: python3 tools/i18n_translate.py --provider openai --locales ar,de --limit-keys 200   # MT_API_KEY + MT_BASE_URL + MT_MODEL
247: ```
248: 
249: Placeholders and the terms in `tools/i18n_glossary.csv` are swapped for sentinels before the request and
250: restored after it, so no engine can renumber or drop `%1$s`. Verified: `protect()`/`restore()` round-trips
251: byte-identically, a second run sends 0 characters (cache hit), and a merged file gained exactly the new
252: keys with zero deleted lines.
253: 
254: The specifier pattern itself was corrected on 2026-09-18 while translating Arabic, and both fixes matter
255: more than they look:
256: 
257: - **Decimal specifiers are real** — `%4$.2f`, `%2$.1f`, `%1$.1f`. The first pattern only knew `%n$s` / `%n$d`,
258:   so it rejected correct translations containing them **and could not see a type change inside one**.
259: - **`%%` is an intentional escaped percent**, not a lone `%`. A lone `%` is still an error: it becomes a
260:   conversion specifier the moment the string reaches `String.format`.
261: 
262: The pattern is `%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[sdfoxegX]` — it deliberately cannot start with a space, so a
263: plain percentage in prose ("100% من المساحة") is never read as a placeholder.
264: 
265: `--apply-csv` never rewrites an existing file: it appends new keys before `</resources>` and rejects
266: any row whose specifiers the caller does not pass, whose key already exists, or which duplicates a
267: key inside the same batch.
268: 
269: Two translation traps the gate cannot catch, so they are conventions instead — both were hit while
270: finishing Spanish in round 8:
271: 
272: - **Unicode is written as an escape, not as a character.** `strings.xml` uses `\u00b7` (·), `\u00d7` (×),
273:   `\u2014` (—), `\u2026` (…) and `\u00b0` (°) across English, Arabic and French alike. Android does resolve
274:   `\uXXXX` in string resources, so a translation must **copy the escape verbatim** — replacing it with the
275:   literal character silently changes what is compared, and dropping it drops a separator or a degree sign.
276: - **A row the gate rejects is not always a bad row.** Before translating, read the *English* source of the
277:   key, not the batch you are writing: `%%` (escaped percent) and mixed-script rows (`%1$d%% available · …`)
278:   look wrong in isolation and are correct. Verified on a temp copy: a 380-key `values-de/strings.xml` gained exactly
279: one line (`diff` = `382a383`) with `'` and `&` correctly escaped.
280: 
281: ## 4. Design-language gates (ADR-06, ADR-07, ADR-08, ADR-14)
282: 
283: ```sh
284: SRC=manager/app/src/main/java/nd/max
285: 
286: # a) no screen you touched may declare its own Scaffold
287: git diff --name-only --diff-filter=ACM -- "$SRC/ui" | xargs -r grep -ln 'Scaffold(' || echo "no new Scaffold OK"
288: 
289: # b) migrated screens must import the design language
290: for f in $(git diff --name-only --diff-filter=ACM -- "$SRC/ui/subscreens" "$SRC/ui/mainscreens"); do
291:   grep -q 'nd.max.ui.design' "$f" || echo "NO_DESIGN_IMPORT $f"
292: done
293: 
294: # c) no hardcoded user-visible copy in changed UI files
295: git diff --name-only --diff-filter=ACM -- "$SRC/ui" | xargs -r grep -n 'Text(\s*"' || echo "no literal Text OK"
296: 
297: # d) legacy design system must not be newly imported
298: git diff -U0 -- "$SRC/ui" | grep '^+.*nd.max.ui.component.MaxDesignSystem' && echo "LEGACY_IMPORT" || echo "no legacy import OK"
299: 
300: # e) adoption counter must go up, never down
301: grep -rl 'nd.max.ui.design' "$SRC/ui" | wc -l      # baseline 2026-09-15: 6 screens
302: grep -rl 'Scaffold(' "$SRC/ui" | wc -l             # baseline 2026-09-15: 33 files
303: ```
304: 
305: ## 5. Navigation gates (ADR-01, ADR-02) — required for NT-01
306: 
307: ```sh
308: SRC=manager/app/src/main/java/nd/max
309: 
310: # a) the pager model is gone
311: grep -rn 'use_scroll_animation\|HorizontalPager' "$SRC" || echo "pager removed OK"
312: 
313: # b) no route string literals outside the registry package
314: grep -rn 'navigate("' "$SRC" | grep -v '/ui/navigation/' || echo "no literal navigate OK"
315: 
316: # f) route literals in ANY form outside ui/navigation (ADR-02).
317: # Gate (b) only saw navigate("..."), so `const val X_ROUTE = "app_detail/"` and
318: # `composable("error/{m}")` slipped through. Two precise rules, no false positives:
319: #   * inline literals at a navigation call site
320: #   * constants whose NAME says ROUTE (catches the F-01 class)
321: # Deliberately NOT matched: pref keys / notification channels / tags — a value-based regex
322: # produced ~90 false positives out of 98 hits when this gate was first written.
323: python3 - <<'PY'
324: import re, os
325: SRC = "manager/app/src/main/java/nd/max"
326: NAV = os.path.join(SRC, "ui/navigation")
327: findings = []
328: for root, _, files in os.walk(SRC):
329:     if os.path.commonpath([root, NAV]) == NAV:
330:         continue
331:     for f in files:
332:         if not f.endswith(".kt"):
333:             continue
334:         p = os.path.join(root, f)
335:         for i, line in enumerate(open(p, encoding="utf-8"), 1):
336:             s = line.strip()
337:             if re.search(r'navigate\(\s*"|composable\(\s*"|startDestination\s*=\s*"', line):
338:                 findings.append(f"{p}:{i}: {s[:80]}")
339:             elif re.search(r'\bconst val [A-Z0-9_]*ROUTE[A-Z0-9_]*\s*=', line):
340:                 findings.append(f"{p}:{i}: ROUTE-CONST {s[:80]}")
341: print("ROUTE_LITERALS_OUTSIDE_NAV:", len(findings))
342: for x in findings:
343:     print("  ", x)
344: PY
345: 
346: # c) every registry route is registered in the graph, and vice versa (no orphans/dead routes)
347: python3 - <<'PY'
348: import re
349: reg=open('manager/app/src/main/java/nd/max/ui/navigation/MaxDestinations.kt',encoding='utf-8').read()
350: graph=open('manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt',encoding='utf-8').read()
351: routes=set(re.findall(r'route\s*=\s*"([^"]+)"',reg))
352: built=set(re.findall(r'composable\(\s*([A-Za-z0-9_.]+|"[^"]+")',graph))
353: print('declared',len(routes)); print('graph entries',len(built))
354: print('ROUTES_NOT_IN_GRAPH', sorted(r for r in routes if r not in graph))
355: PY
356: 
357: # d) every screen composable is reachable from the graph
358: for f in $(grep -rl '^fun .*Screen(' "$SRC/ui/subscreens" "$SRC/ui/mainscreens" 2>/dev/null); do
359:   n=$(basename "$f" .kt); grep -q "$n" "$SRC/ui/navigation/MaxNavGraph.kt" || echo "UNREACHABLE $n"
360: done

(Showing lines 1-360 of 409. Use offset=361 to continue.)
</content>
```

**Tool: grep**

**Input:**
```json
{
  "path": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/NEXT_TASK.md",
  "pattern": "ATLAS|Atlas|أطلس",
  "context": 4,
  "limit": 70
}
```

**Output:**
```
Found 25 matches
/mnt/sdcard/MaxManger/optmize-main/docs/ai/NEXT_TASK.md:
  [context] Line 3: **آخر تحديث:** 2026-09-20 (بعد إصلاح `PERAPP-CONTROL-01`: عزل مسارات البروفايل العام عن تحكم Per-App، وتثبيت مسار الانجراف، وتحسين كتابة Thermal؛ البناء لم يُشغّل وفق قاعدة «البناء عند الطلب»، والقياس على جهاز ما زال مطلوبًا).

  [context] Line 4: وقبلها (تكملة ٦٠ `I18N-01`: عطب بناء مُبلَّغ عنه — `lintVitalRelease` سقط بـ٦ أخطاء `ExtraTranslation` في `values-es`/`values-fr` (مفاتيح مُيتّمة بعد حذفها من `values/`)، فأُضيف كشف «المفتاح بلا نظير» داخل بوابة `i18n_coverage.py --assert` وأداة `--prune` لتنظيفه — وlint عاد أخضر).

  [context] Line 5: وقبلها (تكملة ٥٩ `HOME-04`: بطاقة «ما يحدث الآن؟» محلّ طيف الحِمل +

  [context] Line 6: موجة ساعة CPU/GPU محلّ الشريط المستقيم — والتفصيل في `HANDOFF` تكملة ٦٠). وقبلها (تكملة ٥٨: كاتب الشحن المتحقَّق `ChargingHardwareBackend` +

  [match] Line 7: توصيل الواجهة به (الحكم الثلاثي) + `ATLAS-01` كسؤال قرار بعد قياس «ما فائدة أطلس» بالأرقام).

  [context] Line 8: وقبلها (بعد `LOGS-01`: خمسة عيوب من حزمتي سجلّات جهاز حقيقي — `AppMonitor`

  [context] Line 9: (إعادة التطبيق على كل تبديل) · `CpuCeilingKnobs`/`CpuHardwareBackend` (سقوف ليست ترددات معلنة) · تقاط

  [context] Line 10: سقوف per-app · عطب مرآة `current_profile` · مستويات تشخيص كاذبة — والتفصيل في `HANDOFF` تكملة ٥٧).

  [match] Line 11: وقبلها: `ATLAS-P1`: اثنتا عشرة خُطّة مُنفَّذة ومُقاسة — `P0`·`P1`·`P3`·`P4`·`P5`·`P6`·`P7`·`P8`·`P9`·`P10`·`P12`·`P13` · + `HOME-01`: الشاشة

  [context] Line 12: الرئيسية + بوابة `kt_balance` الجديدة + **أمر المالك «لا بناء تلقائي»** في `AGENTS.md` §0.1 ⇒ البناء

  [context] Line 13: عند الطلب فقط). كل بند يحمل: الوضع المُتحقَّق منه اليوم،

  [context] Line 14: النطاق، معيار القبول، والدور المالك من `AGENTS.md` §3.

  [context] Line 15: 

  [context] Line 173: 

  [context] Line 174: **معيار القبول الثاني:** `:app:lintRelease` كاملًا (لا `lintVital` وحده) مرة واحدة لتعداد تحذيرات lint غير

  [context] Line 175: الفتّاكة — لم تُقرأ في هذه الجولة، ولا أدّعي أن الشجرة نظيفة منها.

  [context] Line 176: 

  [match] Line 177: ## ATLAS-01 — «هلاّ اكتشف أطلس المسار الصحيح عند فشل وظيفة؟»: مُرشِّح مسارات لمسار الكتابة — **غير مبدوء · يحتاج قرار المالك** · 2026-09-20

  [context] Line 178: 

  [match] Line 179: **السؤال كما وُرِد:** ما فائدة Max Atlas؟ أليس كان حتى لو فشل وظيفة يكتشف الطريق الصحيح؟

  [match] Line 180: **الجواب المقيس (التفصيل في `HANDOFF` تكملة ٥٨):** أطلس طبقة **أدلّة** (حكم بسبب، غياب مُثبَت، run

  [context] Line 181: قابل للإعادة، تقرير دعم) — واتجاه البيانات **واحد**: `Cpu/GpuHardwareBackend` تُغذّيه، وهو يُغذّي شاشة

  [match] Line 182: التشخيص. **لا مسار كتابة يأخذ سقف مساره من أطلس.** ومسارات العتاد قوائم ثابتة (`BATTERY_DIRS` ·

  [context] Line 183: `FAST_CHARGE_CURRENT_CANDIDATES` · `SIC_MODE_CANDIDATES` في الكوتلن · `bypass_list` في C).

  [context] Line 184: **والدليل من حزمتيك:** `CONFIG_LOADED … bypass_path=UNSUPPORTED` · `BATTERY_TELEMETRY … evidence=1 nodes`

  [context] Line 185: · لا قدرة `BYPASS_CHARGE` في المصفوفة. ومن مسارات الشحن الجانبي السبعة والسبعين، **٣١ داخل

  [match] Line 186: `/sys/class/power_supply` وهو جذر أطلس المعتمد القابل للتعداد** — أي أن شطرًا كبيرًا من الاكتشاف كان

  [context] Line 187: ممكنًا اليوم بلا تغيير عقد، ولم يسأله أحد.

  [context] Line 188: 

  [context] Line 189: **الخياران (اختر واحدًا قبل التنفيذ):**

  [context] Line 190: 

  [context] Line 191: | # | الخيار | ما يلمسه | ميزانية الإثبات اللازمة |

  [context] Line 192: | --- | --- | --- | --- |

  [match] Line 193: | أ | **مُرشِّح مسارات للكوِتلن**: عند فشل القائمة الثابتة يُعدّد الجذور المعتمدة ويطابق اسم الخاصية | `core/hardware` + `ChargingViewModel` — **بلا كتابة من أطلس** | اختبار بواجهة قراءة مُصطنعة: القائمة الثابتة تفوز عند وجودها · التعداد يُستخدم عند غيابها · غياب مُثبَت لا مجهول |

  [match] Line 194: | ب | **توسيع عدّادات أطلس** (`/sys/devices/platform` · `/proc/mtk_*` · `/sys/kernel/debug`) ثم الجسر الكامل | عقد أطلس + مراجعة مصادر | مصدر مُراجع لكل جذر + مراجعة سلامة + قياس جهاز |

  [context] Line 195: 

  [context] Line 196: **الممنوع اليوم:** لمس `bypass_list` في C (٧٧ مسارًا تمسّ عقد شحن، وعلى الجهاز انهيار نواة في سائق الشاحن

  [context] Line 197: نفسه) — يحتاج جهازًا ومراجعة سلامة، لا رقعة من هنا.

  [context] Line 198: 

  [context] Line 226:    في `mtk_charger_framework`، `Tainted: P W O`) — لم أجد في كودنا كتابةً إلى عقد شحن في تلك النافذة،

  [context] Line 227:    ولم أستطع نفي ذلك بلا جهاز؛ والحزمة الثانية تحمل انهيارًا آخر أقدم (نشاط ‎69175‎ ث).

  [context] Line 228: **الدور:** `executor-kotlin` (نُفِّذ في الطبقة ١) + `safety-reviewer` (معلَّق).

  [context] Line 229: 

  [match] Line 230: ## ATLAS-P1 — «Max Atlas» (مسار GSD مستقل) — **`P0`·`P1`·`P3`·`P4`·`P5`·`P6`·`P7`·`P8`·`P9`·`P10`·`P12`·`P13` مُنفَّذة ومُقاسة** · 2026-09-20

  [context] Line 231: 

  [context] Line 232: **الطلب:** توافق أوسع مع الأجهزة: معرفة مُراجَعة أولًا، ثم اكتشاف آمن محدود، ثم تقرير دعم اختياري

  [context] Line 233: من الإعدادات — بلا كتابة عتاد استكشافية وبلا كسر لسلسلة التحكّم القائمة.

  [context] Line 234: **المكان:** `.planning/phases/01-max-atlas-compatibility-and-safe-discovery/`

  [context] Line 235: (`01-CONTEXT` · `01-RESEARCH` · `01-PATTERNS` · `01-SOURCES` · **`01-PLAN`**). سلطة القرارات تبقى

  [context] Line 236: `docs/ai/DECISIONS.md`، وسجل التسليم الوحيد `docs/ai/HANDOFF.md` (تكملة ٤٨).

  [context] Line 237: **الحالة:** عشر خطط (`P0`–`P9`) مكتوبة بترتيب تبعية ونطاقات حصرية، وموصولة بالمطالب

  [match] Line 238: `ATLAS-01..12`. **و`P0`+`P1`+`P2` مُنفَّذة**: بيئة اختبار بلا جهاز، ومفردات الأدلة، وكتالوج مراجَع

  [context] Line 239: بـ**١٥** بذرة ذات مصدر إلزامي (بعد `P10`)، **وحدّ القراءة المحدود** (`ReadOnlyProbeAccess` فوق ناقل مُحقون — فالناقل

  [context] Line 240: الكاذب لا يزيّف إلا الإدخال/الإخراج، والقواعد المُختبَرة هي المُسلَّمة). `P2` يمنع: مسارًا غير آمن

  [context] Line 241: قبل أي نداء، وغيابًا بلا تعداد هذا العمل ذاته، وقيمة مقطوعة من أن تُقرأ جزئيًّا، ونداءً مميَّزًا بلا

  [context] Line 242: ناقل مُصرَّح (`BACKEND_UNAVAILABLE`، بلا سؤال)؛ ويُنفّذ كل ميزانية **قبل** العمل لا بعده.

  [match] Line 243: **و`P4` مُنفَّذة** (`AtlasPlatformProvider`): مصفوفة دعم **لا تُبنى بدومين ناقص**، فما لا خلفية له

  [context] Line 244: يظهر صفًّا مؤجَّلًا بسبب مسمّى لا يُحذف؛ ومعها `T2.4` (سقف التزامن) بدخول-ثم-فحص وتراجع عند الرفض.

  [context] Line 245: **و`P10`+`P12` مُنفَّذتان** (§19 في الخطة): هوية الجهاز من الأسطح العمومية **المعلنة** فقط (الوحدة بلا

  [context] Line 246: `import android` أصلًا)، و`REVIEWED_FILES` تُعتمد **ملفًا واحدًا** (`/proc/cpuinfo`) بدلًا من اعتماد

  [context] Line 247: `/proc` جذرًا — فيُرفض السبعة المحجوبة بالسياسة قبل أي سؤال للجهاز؛ وعمر الأدلة وسياسة المحاولات

  [match] Line 248: (`AtlasFreshness` · `AtlasFailureLedger` · `AtlasProbeScheduler`) — والتقيّد صار له **سبب** لا حكم واحد

  [match] Line 249: (`AtlasStaleness`: انتهى بالزمن · أبطلته إعادة إقلاع · أبطلته تغيير صلاحية · ساعة غير قابلة للقياس،

  [match] Line 250: بأسبقية ثابتة). و`AtlasFailure.STALE` **لا مُنتِج له بعد**: التقيّد ليس فشل قراءة (بتعليل `AtlasFailureLedger`

  [context] Line 251: نفسه)، فمحلّه مستودع `P5`؛ وقد صُحّح ادّعاء سابق خلاف ذلك في الخطة و`STATE` و`REQUIREMENTS`.

  [match] Line 252: **و`P3`+`P5`+`P6` مُنفَّذة** (الخطة §21، وسُجّلت متأخّرة فصُحّح السجلّ): `AtlasBackendProvider` يغذّي أطلس من

  [context] Line 253: محلّلات CPU/GPU القائمة بلا قراءة ثانية (وكشف عطبًا حقيقيًّا: محلّل GPU كان يقبل نصًّا مشذّبًا سلفًا فقط،

  [match] Line 254: فأي قارئ آخر يفقد الساعة صامتًا) · و`AtlasResolver`/`AtlasEvidenceStore`/`AtlasRepository` مراحل مرتّبة

  [match] Line 255: وإلغاء يُعاد رميه لا يُفسَّر كاستنفاد · و`AtlasSupportReport`/`AtlasReportExporter` تقرير مُصغَّر بحدّ

  [context] Line 256: ٢٥٦ KiB قبل وجود الملف وباختبارات طُعم. **و٢٣ اختبارًا منها يعمل على نظام تشغيل حقيقي** لا على وهم،

  [context] Line 257: وكشف ذلك **عطب إحداثيات** (`canonicalPath` من جذر المضيف مقابل مسار جهاز).

  [match] Line 258: **و`P7`+`P8`+`P9.2` مُنفَّذة** (§20): سطح مرئي (`AtlasViewModel` + `AtlasDiagnosticsSection` داخل شاشة

  [context] Line 259: التشخيص، ومدخل من الإعدادات) · مدخل بلا جذر متحقَّق على مستوى المصدر والرسم لا على جهاز · حارس معماري

  [match] Line 260: يقرأ مسار القراءة كله. **والبنك الثاني** (`AtlasCommunityBank`، ٥٠ واجهة، **ADR-37**) يُكمل تلقائيًّا

  [context] Line 261: فقط ما لم يُجب عنه البنك المراجَع، وبثقة لا تتجاوز `CLAIMED`/`FETCHED` ولا تُرقّى أبدًا.

  [context] Line 262: **الأدلة (الأحدث):** `:app:testReleaseUnitTest` + `:app:assembleDebug -x lintVitalRelease` =

  [context] Line 263: **BUILD SUCCESSFUL 4m51s · ١١٠٣ اختبارات · ٠ فشل · ٠ خطأ · ٠ مُتخطّى** · وAPK ‎121,071,768‎ بايت. ونطاق

  [match] Line 264: أطلس والتشخيص داخل هذا التشغيل = **٢٢ صنفًا / ٣٠٦ اختبارًا / ٠ فشل**، والنطاق المُركَّز

  [context] Line 265: (`:app:testDebugUnitTest`) = **٢١ صنفًا / ٢٧٩ اختبارًا / ٠ فشل**، والتحذير الوحيد في التشغيلين هو

  [context] Line 266: `MemoryStallTest.kt:122` القائم خارج النطاق (صفر تحذير من أي ملف لُمس) · الدَّين على سقفه `10/29/66/26`

  [context] Line 267: · `PROBLEMS: 0`.

  [context] Line 268: **و`P9.1`+`P13` مُنفَّذتان** (الخطة §22، وتكملة ٥٥): الـfixture صار **بيانات قابلة لإعادة التشغيل** عبر

  [context] Line 269: نفس واجهة الناقل الشحن (مسارات غير مُسجَّلة ⇒ `UNKNOWN_CAUSE` لا `ABSENT`؛ والغياب يُثبت بالتعداد وحده؛

  [match] Line 270: والأصل `DEVICE`/`HOST`/`SYNTHETIC` مُسجَّل)، و`AtlasDoctor` يقارن تشغيلًا مُعادًا بتقرير محفوظ ويحكم

  [context] Line 271: `Reproduced`/`Diverged`/`Refused` (ويُبطل المقارنة عند اختلاف مراجعة الكتالوج، ويُعدّ غياب واجهة مرشّحة

  [context] Line 272: **نصيحةً** لا انحرافًا).

  [context] Line 273: **الخطوة التالية:** `P11` (قاعدة الـquirks — تقفض الثقة ولا ترفعها) على بلاغات حقيقية؛ والوحيد الموقف هو

  [context] Line 274: `P2`/T2.5 على مراجعة عائلة نموذج أخرى، والوحيد الذي يحتاج جهدًا منك: تشغيل الطبيب على جهاز وحفظ ملف

  [match] Line 275: `DEVICE` — فكل لقطة موجودة اليوم `HOST` ومكتوب عليها ذلك. و`DECISION-1` **مُنفَّذ كما أُوصي** في `AtlasReadBudget.DEFAULT` (مكان واحد، وبيئة الاختبار

  [context] Line 276: تتفوّض إليه) فلم يبقَ حاجزًا؛ لكن تأكيد المالك ما زال مطلوبًا، و`P2`/T2.5 (ناقل مُصرَّح حقيقي) يبقى

  [context] Line 277: غير مبنيّ حتى تتوفّر مراجعة مستقلة.

  [context] Line 278: **ملعب بيئي:** `./gradlew` فقد بت التنفيذ بسبب المزامنة ⇒ استخدم `bash gradlew`.

  [context] Line 279: **معيار القبول قبل ما تبقّى:** الإجابة على القرارات الستة في `01-PLAN` §15 (أهمّها `DECISION-1`:

  [context] Line 281: (`P2`/`P3`) وإلا بقيت `UNREVIEWED`.

  [context] Line 282: **ما لا يُدّعى:** لا توافق عتاد ولا أداء ولا التزام ASVS؛ لا قياس على جهاز أصلًا (الميزانيات وأعمار

  [context] Line 283: الأدلة **قيم تصميمية**)، وكل ما يخصّ الأجهزة يبقى «غير متحقَّق حتى قياسه على جهاز» (بروتوكول P9 §T9.4).

  [context] Line 284: **تصحيح «ولا نقطة استدعاء واحدة»:** هذا الادّعاء **لم يبقَ صحيحًا** بعد `P7`/`P8`، والبديل المقيس:

  [match] Line 285: **عشرة ملفات** خارج `core/atlas/` تذكره، والواجهة تصل إليه فعليًّا (`AtlasViewModel` بحقن Hilt →

  [match] Line 286: `AtlasDiagnosticsSection` داخل شاشة التشخيص + مدخل في الإعدادات). فـ«مُسلَّم» الآن = وحدة مختبَرة **ووجهة

  [context] Line 287: تُرسم**، وما يبقى غير مُثبَت هو **اللمسة على جهاز**: هذه البيئة بلا `adb` وبلا محاكي.

  [context] Line 288: **ملاحظة حاجز:** عطب `code_health` السابق بسبب `txt.txt` لم يعد يُعاد إنتاجه — الملف غير موجود في

  [context] Line 289: الشجرة الآن و`--assert` = exit 0؛ لم يُحذفه هذا المسار ولم تُلمس أي قاعدة تجاهل أو بوابة.

  [context] Line 290: 

```

---

## Assistant (Code · GPT-6 Astra · 41.6s)

_Thinking:_



**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/repo_audit.py",
  "description": "Run independent repository audit baseline",
  "timeout": 120000
}
```

**Output:**
```
PROBLEMS: 0
kt files scanned: 398 | R.string refs: 2679 | base strings: 3349

```

**Tool: bash**

**Input:**
```json
{
  "command": "git diff --check",
  "description": "Verify current patch whitespace integrity"
}
```

**Output:**
```
(no output)
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/VALIDATION.md",
  "offset": 365,
  "limit": 60
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/VALIDATION.md</path>
<type>file</type>
<content>
365: 
366: ## 6. Control-plane safety gates (ADR-11)
367: 
368: ```sh
369: SRC=manager/app/src/main/java/nd/max
370: # (a) مهمة الواجهة تفحص ما أضافته هي فقط — بوابة الـdiff
371: git diff -U0 -- "$SRC/ui" | grep '^+' | grep -n 'RootFileAccess\|Shell.cmd\|su -c' && echo "UI_DIRECT_WRITE_VIOLATION" || echo "no direct hw write OK"
372: git diff --name-only -- "$SRC/core" || true    # core changes in a UI task need explicit justification
373: 
374: # (b) دَين قائم يُقاس ولا يُسمح له بالنمو — السقف ليس صفرًا لأن الميراث ليس صفرًا
375: python3 tools/code_health.py --assert           # presentation_hw_writes ≤ 27
376: ```
377: 
378: ### 6.1 لماذا بوابة (b) رغم وجود (a)
379: 
380: بوابة (a) تقرأ **الـdiff** فقط، فهي تمنع الانتهاكات الجديدة لكنها لا ترى الميراث ولا تستطيع أن
381: تقول كم بقي منه. و`docs/ai/VERIFICATION_NT01.md` سجّل «١٩٩ موضعًا مباشرًا في `ui/**`» كجملة
382: بلا رقم قابل للفرض. بوابة (b) تحوّل الجملة إلى **سقف يفشل البناء عند تجاوزه** صحيحًا، والفرق بين
383: الرقمين مقصود ومكتوب: `199` = كل موضع `RootFileAccess`/shell في `ui/**` (بما فيه القراءات التي
384: تسمح بها ADR-11)، و`27` = الكتابات الفعلية من طبقة العرض بعد استثناء `ui/util/` والتعليقات.
385: 
386: ### 6.2 مسبار خاطئ يجب ألا يُستخدم
387: 
388: `grep -rn '/sys/' "$SRC/ui"` يعطي **٨٧ نتيجة ليس فيها كتابة واحدة** — معظمها `readSysFile` و`cat /sys/...`،
389: وADR-11 تحكم **الكتابة** لا القراءة. ثلاثة أخطاء وقعت في هذا المسبار بالترتيب: ① مطابقة `>` أطلقت
390: `2>/dev/null` كأنه كتابة، ② وصف الحالة داخل KDoc عُدّ كودًا، ③ لا تصنيف بين عقدة عتاد وملف إعداد في
391: `/data/adb/.config/MaxManager/`. الفاحص الآن يستثني الثلاثة صراحةً (`COMMENT_LINE`, `SHELL_WRITE`).
392: 
393: ## 7. Unit tests (when a JVM toolchain becomes available)
394: 
395: ```sh
396: cd manager && ./gradlew :app:testDebugUnitTest --tests 'nd.max.core.*'
397: ```
398: Must stay green: `ControlPlaneArchitectureTest`, `HardwareControlArbiterTest`, `ManualControlLocksTest`, `MinimalPlannerTest`, `ControlOutcomeModelTest`, `DiagnosticCenterTest`.
399: 
400: ## 8. Reporting template for the Executor
401: 
402: ```
403: TASK: <id>
404: FILES: <added / modified / deleted>
405: GATES: 1 ✓  2 ✓  3 ✓(ar parity: n keys)  4 ✓(adoption 6→N, Scaffold 33→M)  5 ✓  6 ✓
406: BUILD: not verified — gradle 4.4.1 vs required 9.5.1, offline
407: RESIDUAL RISK: <what a compiler/device would still need to confirm>
408: NEXT: <suggested follow-up>
409: ```

(End of file - total 409 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/team/executor-kotlin.md",
  "limit": 150
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/team/executor-kotlin.md</path>
<type>file</type>
<content>
1: # الدور: Executor — Kotlin / Compose
2: 
3: **النموذج المُسند:** **DeepSeek V4 Flash** (الطبقة ١) — هذا هو الدور الأكثر حضورًا (قراءة ملف ← تعديل ← تحقق
4: ثابت ← تكرار)، وهو غير محجوب فلا يُستهلك الرصيد.
5: **الاحتياطي:** **GLM 5.3 Flash** عند نفاد الجلسات، أو **قارئ ثانٍ** لملف معقّد بلا أي تكلفة — لا يُستدعى Luna لهذا الدور إلا إن لمس التغيير مسار كتابة عتاد (فيصير دورًا عالي المخاطر).
6: **النموذج المرجعي في المشروع:** «DeepSeek Harness (Executor)» الذي أنجز NT-01/NT-02.
7: 
8: ## المهمة
9: 
10: تنفيذ `NT-xx` داخل `nd.max` بأصغر تغيير صحيح، باتباع الأنماط القائمة لا أنماط جديدة.
11: 
12: ## النطاق
13: 
14: - يملك: `manager/app/src/main/java/nd/max/**`، `manager/app/src/main/res/**`، `manager/app/src/test/java/nd/max/**`.
15: - يقرأ ولا يعدّل بلا تبرير صريح في المهمة: `core/hardware/**`، `core/maxai/**` (بوابة §6 في `VALIDATION.md`).
16: - ممنوع: `thermalcore/`, `binprofiles/`, `binutils/`, `archdaemon/`, `preloadbin/`, `mainfiles/`, `android/aosp/`, `manager/kernel-flasher/`.
17: 
18: ## قواعد ملزمة (من ADRs)
19: 
20: - `ui/design/` هو نظام التصميم الوحيد؛ لا `Scaffold(` جديد في ملفات الشاشات، ولا استيراد `ui/component/MaxDesignSystem` (ADR-06).
21: - التنقّل عبر `ui/navigation/MaxDestinations.kt` — لا `navigate("literal")` خارج حزمة التنقّل (ADR-02).
22: - لا كتابة مباشرة للعتاد من `ui/**` (لا `RootFileAccess`/`Shell.cmd`) (ADR-11).
23: - لا رقم معروض بلا مصدر/حداثة؛ المجهول `status_unknown` (ADR-07).
24: - لا نص جديد بلا `values/` + `values-ar/` معًا (ADR-14) — وإن وُجد Localizer، فالتنسيق عبره.
25: - لا إعادة تصميم شاشة مُنجزة لأسباب جمالية (ADR-18).
26: 
27: ## طريقة العمل في هذه البيئة
28: 
29: 1. اقرأ الملف المستهدف كاملًا وحُدّد موضع التغيير قبل الكتابة.
30: 2. عدّل بأصغر diff ممكن، واحترم تنسيق الجوار (4 مسافات، KDoc على الطبقات العامة).
31: 3. **لا تشغّل Gradle/NDK** إلا إذا طلب المستخدم بناءً صراحة (تعليمة قائمة: التكلفة دقائق وتُستنزف الرموز).
32: 4. تحقق ثابتًا: توازن الأقواس بعد تجريد النصوص والتعليقات، الاستيرادات (لا زائد/ناقص)، إعادة قراءة النطاق المعدّل،
33:    و`Serena get_diagnostics_for_file` إن توفّر.
34: 5. لاحظ استثناءين: `AppMonitor.kt` يفشل في فحص التوازن الساذج (I-43)، و`manager/kernel-flasher` نسخة مُضمَّنة (I-44) — لا «تصلحهما».
35: 
36: ## المخرجات
37: 
38: قالب `VALIDATION.md` §8 (TASK / FILES / GATES / BUILD / RESIDUAL RISK / NEXT) + حالة الإغلاق.
39: في `BUILD` اكتب حرفيًا: `not verified — compilation unverified in this environment`.
40: 
41: ## Kickoff prompt
42: 
43: ```
44: You are the Kotlin/Compose Executor for the MaxManager app (package nd.max, Compose + Hilt).
45: 
46: Read first: AGENTS.md, docs/ai/VALIDATION.md, docs/ai/HANDOFF.md (§Invariants), the relevant ADRs in
47: docs/ai/DECISIONS.md, and the task unit in docs/ai/NEXT_TASK.md. Then read the target files fully
48: before editing; identify the exact site of the behaviour (Serena find_symbol / find_referencing_symbols
49: when available).
50: 
51: Scope: manager/app/src/main/java/nd/max/** and manager/app/src/main/res/**. Never touch native layers
52: (thermalcore, binprofiles, binutils, archdaemon, preloadbin, mainfiles, android/aosp) or
53: manager/kernel-flasher. Do not modify core/hardware or core/maxai unless the task explicitly allows it.
54: 
55: Binding rules: ui/design/ is the only design system (no new Scaffold, no legacy MaxDesignSystem import);
56: navigation goes through ui/navigation/MaxDestinations.kt (no literal navigate("...")); the UI never writes
57: hardware directly (no RootFileAccess / Shell.cmd under ui/**); no synthesized telemetry (unknown renders
58: as status_unknown); every new user-visible string lands in values/ and values-ar/ together.
59: 
60: Do NOT run a Gradle or NDK build (standing user instruction: builds cost minutes and tokens). Verify
61: statically instead: brace balance of edited files after stripping strings/comments, import set
62: (no unused, none missing), re-read the edited range, grep every call site of changed shared components,
63: and use Serena get_diagnostics_for_file when available. AppMonitor.kt brace imbalance (I-43) and the
64: vendored kernel-flasher duplicates (I-44) are expected baseline noise — do not "fix" them.
65: 
66: Report with the VALIDATION.md §8 template and a DONE / DONE_WITH_CONCERNS / BLOCKED verdict; state
67: "compilation unverified in this environment" rather than implying a build passed.
68: ```

(End of file - total 68 lines)
</content>
```

**Tool: glob**

**Input:**
```json
{
  "pattern": "**/AGENTS.md",
  "path": "/mnt/sdcard/MaxManger/optmize-main/manager"
}
```

**Output:**
```
No files found
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/tools",
  "limit": 150
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/tools</path>
<type>directory</type>
<entries>
__pycache__/
code_health_baseline.json
code_health.py
i18n_coverage.py
i18n_glossary.csv
i18n_translate.py
kt_balance.py
read_image_text.py
repo_audit.py
test_maxai_jvm.py

(10 entries)
</entries>
```

---

## Assistant (Code · GPT-6 Astra · 15.0s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/tools/test_maxai_jvm.py",
  "limit": 210
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/tools/test_maxai_jvm.py</path>
<type>file</type>
<content>
1: #!/usr/bin/env python3
2: """Run focused production Kotlin/JUnit tests without Gradle, Android, or mocks.
3: 
4: Uses existing Gradle-cached compiler/runtime jars; never downloads dependencies.
5: This is not an Android compilation or device test.
6: """
7: 
8: import argparse
9: import os
10: from pathlib import Path
11: import subprocess
12: import tempfile
13: 
14: 
15: ROOT = Path(__file__).resolve().parents[1]
16: SOURCE = ROOT / "manager/app/src/main/java/nd/max"
17: TEST = ROOT / "manager/app/src/test/java/nd/max"
18: 
19: 
20: def main():
21:     parser = argparse.ArgumentParser(description=__doc__)
22:     parser.add_argument("--work-dir", type=Path, default=Path(tempfile.gettempdir()))
23:     args = parser.parse_args()
24:     cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
25: 
26:     def jar(group, artifact, version):
27:         matches = sorted((cache / group / artifact / version).glob(f"*/{artifact}-{version}.jar"))
28:         if not matches:
29:             parser.error(f"Missing cached {group}:{artifact}:{version}; no dependency was downloaded")
30:         return str(matches[0])
31: 
32:     stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.10")
33:     coroutines = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")
34:     annotations = jar("org.jetbrains", "annotations", "13.0")
35:     junit = jar("junit", "junit", "4.13.2")
36:     hamcrest = jar("org.hamcrest", "hamcrest-core", "1.3")
37:     compiler = jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.3.10")
38:     reflect = jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10")
39:     compiler_cp = os.pathsep.join([compiler, stdlib, coroutines, annotations, reflect])
40:     runtime_cp = os.pathsep.join([stdlib, coroutines, annotations, junit, hamcrest])
41:     sources = [
42:         SOURCE / "core/maxai/CoalescingCycleRunner.kt",
43:         SOURCE / "core/maxai/MaxAiEpisode.kt",
44:         SOURCE / "ui/mainscreens/MaxAiPresentation.kt",
45:         TEST / "core/maxai/CoalescingCycleRunnerTest.kt",
46:         TEST / "ui/mainscreens/MaxAiPresentationTest.kt",
47:         TEST / "ui/mainscreens/MaxAiTimelineFilterTest.kt",
48:         TEST / "ui/viewmodel/MaxAiPresentationArchitectureTest.kt",
49:         TEST / "core/maxai/MaxAiInterruptSafetyTest.kt",
50:         SOURCE / "ui/navigation/LaunchRoutes.kt",
51:         TEST / "ui/navigation/LaunchRouteTest.kt",
52:         # مكان الأرشيف وسياسة التقليم خالصان عن قصد: يُترجمان ويُقاسان هنا بلا Android
53:         # ولا جهاز. والقاعدة الثانية هي الوحيدة التي تحذف بيانات بلا سؤال.
54:         SOURCE / "ui/util/MaxBackupStorage.kt",
55:         TEST / "ui/util/MaxBackupStorageTest.kt",
56:         SOURCE / "ui/util/MaxBackupRetention.kt",
57:         TEST / "ui/util/MaxBackupRetentionTest.kt",
58:         # `OCR-01`/`OCR-02`: قراءة الوقت وحساب الموعد القادم وقواعد المجموعات وتخزينها
59:         # النصّي — أربعة أشياء تُحسب ولا تُرى، فتخطئ بصمت إن لم تُقَس في JVM عادي.
60:         SOURCE / "ui/util/MaxBackupSchedule.kt",
61:         SOURCE / "ui/util/MaxBackupFolders.kt",
62:         TEST / "ui/util/MaxBackupSchedulePlanTest.kt",
63:         # `OCR-04`: المفضّلة — التحقّق بالاسم، والترتيب، والملف. لا شيء منها يحتاج Android.
64:         SOURCE / "ui/util/MaxBackupFavorites.kt",
65:         TEST / "ui/util/MaxBackupFavoritesTest.kt",
66:         # `OCR-06`: الحصيلة وصفّ التجميع — رقمان يقفان بين المستخدم وقائمة بـ١٩١ نسخة.
67:         SOURCE / "ui/util/MaxBackupCounts.kt",
68:         TEST / "ui/util/MaxBackupCountsTest.kt",
69:         # نموذج اللوحين وحرس العمليات: قرار الترتيب يحدّد أيّ شاشتين تُركَّبان، والحرس
70:         # هو آخر ما يقف بين نقرة وحذف شجرة. كلاهما خالص فلا حاجة إلى Android لقياسه.
71:         SOURCE / "ui/util/FileSystemModel.kt",
72:         SOURCE / "ui/util/FileActionModel.kt",
73:         # `OCR-10`: مرشّح البحث خالص ويعتمد عليه `FilePaneModel.visible()`، فلو لم يُترجم
74:         # هنا لبقي قرار «ما يراه المستخدم» بلا قياس.
75:         SOURCE / "ui/util/FileSearchFilters.kt",
76:         TEST / "ui/util/FileSearchFiltersTest.kt",
77:         SOURCE / "ui/util/FilePaneModel.kt",
78:         TEST / "ui/util/FilePaneModelTest.kt",
79:         # شاشة التخزين: تصنيف المصارف وترتيب أكبر العناصر وتحليل العُقد — ثلاثة قرارات
80:         # تُنتج أرقامًا يعتمد عليها المستخدم في قرار حذف، فتُقاس في JVM لا على الجهاز.
81:         SOURCE / "ui/util/StorageScanModel.kt",
82:         TEST / "ui/util/StorageScanModelTest.kt",
83:         # شاشة الحرارة: العتبتان ونقاط التخفيف. `null` هنا يعني «لا نعرف» لا «آمن»،
84:         # وهذا الفرق هو ما يمنع طمأنة كاذبة عند جهاز لا يُعلن نقاطه.
85:         SOURCE / "ui/util/ThermalModel.kt",
86:         TEST / "ui/util/ThermalModelTest.kt",
87:         # حرّاس شكل على قائمة الاختيار الواحد: الصنف الذي أسقط شاشة مدير الملفات كان
88:         # «فهرسة قائمة أيقونات أقصر من الأسماء» — وهو عطبٌ لا يراه مصرّف ولا اختبار جهاز.
89:         TEST / "ui/design/MaxViewMenuContractTest.kt",
90:     ]
91:     tests = [
92:         "nd.max.core.maxai.CoalescingCycleRunnerTest",
93:         "nd.max.core.maxai.MaxAiInterruptSafetyTest",
94:         "nd.max.ui.mainscreens.MaxAiPresentationTest",
95:         "nd.max.ui.navigation.LaunchRouteTest",
96:         "nd.max.ui.mainscreens.MaxAiTimelineFilterTest",
97:         "nd.max.ui.mainscreens.MaxAiTimelineSearchTest",
98:         "nd.max.ui.viewmodel.MaxAiPresentationArchitectureTest",
99:         "nd.max.ui.util.MaxBackupStorageTest",
100:         "nd.max.ui.util.MaxBackupRetentionTest",
101:         "nd.max.ui.util.MaxBackupSchedulePlanTest",
102:         "nd.max.ui.util.MaxBackupFavoritesTest",
103:         "nd.max.ui.util.MaxBackupCountsTest",
104:         "nd.max.ui.util.FileSearchFiltersTest",
105:         "nd.max.ui.util.FilePaneModelTest",
106:         "nd.max.ui.util.StorageScanModelTest",
107:         "nd.max.ui.util.ThermalModelTest",
108:         "nd.max.ui.design.MaxViewMenuContractTest",
109:     ]
110:     live_test = TEST / "ui/mainscreens/MaxLivePresentationArchitectureTest.kt"
111:     if live_test.exists():
112:         sources.append(live_test)
113:         tests.append("nd.max.ui.mainscreens.MaxLivePresentationArchitectureTest")
114: 
115:     # شاشة التطبيقات: حرس عائلة الحالات الثلاث يقرأ الشاشة نصًّا بعد تنقيتها من التعليقات،
116:     # فلا يحتاج Compose ولا جهازًا — وكان تضاربها اللغوي هو سبب وجوده.
117:     applist_test = TEST / "ui/mainscreens/ApplistPresentationArchitectureTest.kt"
118:     if applist_test.exists():
119:         sources.append(applist_test)
120:         tests.append("nd.max.ui.mainscreens.ApplistPresentationArchitectureTest")
121: 
122:     # شاشتا التخزين والحرارة: حرس اللغة المشتركة والعنوان الإنجليزي الصلب ومصدر حرارة
123:     # البطارية. يقرأ الملفين نصًّا بعد تنقيتهما، فلا يحتاج Compose ولا جهازًا.
124:     details_test = TEST / "ui/subscreens/DetailScreensLanguageContractTest.kt"
125:     if details_test.exists():
126:         sources.append(details_test)
127:         tests.append("nd.max.ui.subscreens.DetailScreensLanguageContractTest")
128: 
129:     with tempfile.TemporaryDirectory(prefix="maxai-jvm-", dir=args.work_dir) as output:
130:         # Parse the Android-facing code with Kotlin's actual parser, without
131:         # substituting fake Android/Compose APIs or claiming type-check coverage.
132:         syntax = Path(output) / "CheckKotlinSyntax.java"
133:         syntax.write_text('''
134: import java.nio.file.*;
135: import org.jetbrains.kotlin.cli.jvm.compiler.*;
136: import org.jetbrains.kotlin.config.CompilerConfiguration;
137: import org.jetbrains.kotlin.psi.KtPsiFactory;
138: import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
139: import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement;
140: import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
141: class CheckKotlinSyntax {
142:     public static void main(String[] paths) throws Exception {
143:         var disposable = Disposer.newDisposable();
144:         try {
145:             var env = KotlinCoreEnvironment.createForProduction(disposable,
146:                 new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
147:             var factory = new KtPsiFactory(env.getProject(), false);
148:             int errors = 0;
149:             for (var name : paths) {
150:                 var path = Path.of(name);
151:                 var file = factory.createFile(path.getFileName().toString(), Files.readString(path));
152:                 for (var error : PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class)) {
153:                     System.err.println(name + ": " + error.getErrorDescription() + " at " + error.getTextOffset());
154:                     errors++;
155:                 }
156:             }
157:             if (errors != 0) throw new AssertionError(errors + " Kotlin syntax errors");
158:             System.out.println("Kotlin syntax: " + paths.length + " files, no parser errors (not type checking)");
159:         } finally { Disposer.dispose(disposable); }
160:     }
161: }
162: ''', encoding="utf-8")
163:         android_sources = [
164:             "core/maxai/MaxAiEngine.kt", "core/maxai/MaxAiModels.kt",
165:             "core/maxai/MaxAiJournal.kt", "ui/viewmodel/MaxAiViewModel.kt",
166:             "ui/mainscreens/MaxAiScreen.kt", "ui/mainscreens/MaxLiveScreen.kt",
167:             "ui/mainscreens/MaxAiRuntimeStatus.kt",
168:             # الملفات التي مسّها إصلاح مسار الإطلاق: تُعرَب بمُحلِّل Kotlin الحقيقي
169:             # حتى لو تعذّرت الترجمة لغياب Android SDK (لا فحص أنواع، إعراب فقط).
170:             "ui/navigation/LaunchRoutes.kt", "ui/navigation/MaxNavActions.kt",
171:             "ui/navigation/MaxDestinations.kt", "ui/navigation/MaxNavGraph.kt",
172:             "ui/mainscreens/HomeScreen.kt",
173:             "ui/mainscreens/ControlLayoutModel.kt", "ui/mainscreens/ControlScreen.kt",
174:             "ui/subscreens/MaxBackupScreen.kt", "ui/subscreens/PermissionsScreen.kt",
175:             # Max Backup بعد إعادة التصميم: الشاشة الرئيسية والمنتقي، ومكان الأرشيف،
176:             # والمحرّك والمستند اللذان مسّهما التغيير. إعراب فقط — لا فحص أنواع.
177:             "ui/subscreens/MaxBackupHubScreen.kt", "ui/subscreens/MaxBackupPickerScreen.kt",
178:             "ui/util/MaxBackupStorage.kt", "ui/util/MaxBackupEngine.kt",
179:             "ui/util/MaxBackupModel.kt",
180:             # المجموعات والجدول: الشاشتان الجديدتان والمُجدوِل الذي ينفّذهما. إعراب فقط.
181:             "ui/subscreens/MaxBackupScheduleSection.kt", "ui/subscreens/MaxBackupSystemSection.kt",
182:             "ui/util/MaxBackupScheduler.kt", "ui/util/MaxBackupSystemModel.kt",
183:             "ui/util/MaxBackupFolders.kt", "ui/util/MaxBackupSchedule.kt",
184:             "ui/util/MaxBackupFavorites.kt", "ui/subscreens/MaxBackupPickerScreen.kt",
185:             "ui/util/MaxBackupCounts.kt", "ui/subscreens/MaxBackupRecentItem.kt",
186:             # مدير الملفات بعد إضافة ترتيب اللوحين والتنقّل المرتبط: الشاشة واللغة ورؤوس
187:             # الألواح. إعراب فقط — لا فحص أنواع (لا Android SDK في هذه البيئة).
188:             "ui/subscreens/FileManagerScreen.kt", "ui/component/FilePaneColumn.kt",
189:             # شريط اللوح وخرائط الرفض والفرز انتقلت من الشاشة إلى مكوّناتها: تُعرَب في
190:             # مكانها الجديد حتى لا يصير النقل نقلًا أعمى.
191:             "ui/component/FileManagerPanels.kt",
192:             "ui/design/MaxViewMenu.kt", "ui/util/FileSearchFilters.kt",
193:             "ui/util/FilePaneModel.kt",
194:             # شاشة التطبيقات: إعراب فقط، لا فحص أنواع — يكفي ليمسك إعرابًا مكسورًا في
195:             # الشاشة التي عُدِّلت حالتها المشتركة.
196:             "ui/mainscreens/ApplistScreen.kt",
197:             # الشاشتان المعاد بناؤهما وقارئهما: إعراب فقط كما في بقية القائمة.
198:             "ui/subscreens/StorageDetailScreen.kt", "ui/util/StorageUtil.kt",
199:             "ui/subscreens/ThermalDetailScreen.kt", "ui/design/MaxBar.kt",
200:             "ui/subscreens/ChargingScreen.kt", "ui/mainscreens/DashboardDetailScreens.kt",
201:         ]
202:         subprocess.run([
203:             "java", "-cp", compiler_cp, str(syntax),
204:             *map(str, [SOURCE / name for name in android_sources]),
205:         ], cwd=ROOT, check=True)
206:         subprocess.run([
207:             "java", "-cp", compiler_cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
208:             "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", runtime_cp,
209:             "-d", output, *map(str, sources),
210:         ], cwd=ROOT, check=True)

(Showing lines 1-210 of 218. Use offset=211 to continue.)
</content>
```

**Tool: grep**

**Input:**
```json
{
  "path": "/mnt/sdcard/MaxManger/optmize-main/.planning/phases/01-max-atlas-compatibility-and-safe-discovery/01-CONTEXT.md",
  "pattern": "[Dd]ecision|[Dd]efer|[Aa]uthority|أطلس",
  "context": 3,
  "limit": 45
}
```

**Output:**
```
Found 9 matches
/mnt/sdcard/MaxManger/optmize-main/.planning/phases/01-max-atlas-compatibility-and-safe-discovery/01-CONTEXT.md:
  [context] Line 14: every control. Planning only in this run; no product implementation or publication.

  [context] Line 15: </domain>

  [context] Line 16: 

  [match] Line 17: <decisions>

  [match] Line 18: ## Implementation Decisions

  [context] Line 19: 

  [context] Line 20: ### Owner Requirements

  [context] Line 21: 

  [context] Line 28: - **D-05:** Settings provides a last-resort app support report; maintainer analysis leads to

  [context] Line 29:   tested device-support fixes and a normal reviewed publication, not runtime downloaded code.

  [context] Line 30: - **D-06:** Preserve the existing app/control plane and finished work. Keep docs/ai as the

  [match] Line 31:   canonical ADR authority and sole handoff log; this GSD track does not replace old tasks.

  [context] Line 32: 

  [context] Line 33: ### Engineering Constraints From Binding ADRs

  [context] Line 34: 

  [context] Line 51:   invent a server, account, recipient address or network-based model.

  [context] Line 52: - Preserve current minSdk/native ABIs; inventory their limits instead of quietly promising

  [context] Line 53:   older Android, 32-bit support, unrestricted HAL access or rootless tuning.

  [match] Line 54: </decisions>

  [context] Line 55: 

  [context] Line 56: <canonical_refs>

  [context] Line 57: ## Canonical References

  [context] Line 58: 

  [context] Line 59: - `AGENTS.md`: collaboration, safety review, localization and reporting contract.

  [context] Line 60: - `docs/ai/ENGINEERING-CONTRACT.md`: investigate first, minimal changes, truthful verification.

  [match] Line 61: - `docs/ai/DECISIONS.md`: binding architecture and safety decisions; do not duplicate ADRs here.

  [context] Line 62: - `docs/ai/VALIDATION.md`: repository and Android validation gates.

  [context] Line 63: - `docs/ai/REVIEW.md`: independent review and reproducibility rules.

  [context] Line 64: - `docs/ai/DESIGN_VISION.md`: Settings, Diagnostics, trust, canonical navigation/design language.

  [context] Line 65: - `docs/ai/NEXT_TASK.md`: existing task registry, left intact except a pointer for this request.

  [context] Line 66: - `docs/ai/HANDOFF.md`: read order and the only executor log.

  [match] Line 67: - `txt.txt`: owner-supplied reference inventory, not verified fact or implementation authority.

  [context] Line 68: - `https://github.com/SmartPack/SmartPack-Kernel-Manager`: explicitly requested primary reference.

  [context] Line 69: </canonical_refs>

  [context] Line 70: 

  [context] Line 79: of the whole application. Cancellation must never be reported as completed exhaustion.

  [context] Line 80: </specifics>

  [context] Line 81: 

  [match] Line 82: <deferred>

  [match] Line 83: ## Deferred Ideas

  [context] Line 84: 

  [context] Line 85: Not requested or authorized here: cloud AI, new Shizuku/vendor privilege transports, root exploits,

  [context] Line 86: SELinux changes, boot-time mutation, replacing Max AI, signing/releasing in this session, or

  [context] Line 87: importing third-party executable plugins. Architecture may leave seams for separately reviewed work.

  [match] Line 88: </deferred>

```

---

## Assistant (Code · GPT-6 Astra · 189.3s)

_Thinking:_



أطلس موجود بالفعل، وسجلّه يتضمن الاكتشاف والتشخيص والتنفيذ التكيفي؛ لذلك يختلف التطوير كثيرًا بحسب الهدف، وليس مجرد تعديل واجهة. الفحوص الأولية نجحت بلا بناء، وأحتاج تحديد الأولوية قبل تغيير سلوك قد يمس العتاد.


**Tool: question**

**Input:**
```json
{
  "questions": [
    {
      "header": "أولوية تطوير Max Atlas",
      "question": "ما النتيجة التي تريد التركيز عليها في هذه الجولة؟",
      "options": [
        {
          "label": "الاكتشاف والتوافق (Recommended)",
          "description": "تحسين اكتشاف قدرات الجهاز والمسارات المتاحة، ودقة أسباب عدم الدعم، مع إبقاء الاكتشاف للقراءة فقط."
        },
        {
          "label": "البدائل عند فشل التنفيذ",
          "description": "تطوير اختيار مسار بديل موثوق عند فشل وظيفة، مع التحقق والاسترجاع الآمن. هذا يمس طبقة التحكم ويحتاج تحققًا ومراجعة سلامة أوسع."
        },
        {
          "label": "الواجهة والتشخيص",
          "description": "تسهيل فهم نتائج أطلس، والبحث والتصفية وإعادة الفحص، وإظهار أسباب الفشل والأدلة بوضوح."
        }
      ]
    }
  ]
}
```

**Output:**
```
User has answered your questions: "ما النتيجة التي تريد التركيز عليها في هذه الجولة؟"="اريد الهدف النهائي من اطلس ان يجعل تطبيقي يعمل علي اغلب الاجهزة دون تدخل مني كانه كيان خارق فائق الذكاء فهو يحلل ويكتشف وصلح سبب الاخطاء والفشل مهما كانت الاجهزة مختلفة ويجعل كل وظيفة في تطبيقي يعمل". You can now continue with the user's answers in mind.
```

---

## Assistant (Code · GPT-6 Astra · 9.5s)

**Tool: board_read**

**Input:**
```json
{
  "limit": 10
}
```

**Output:**
```
{"observedAt":1789989711973,"agent":"main","participants":[{"id":"main","sessionID":"ses_f3c539be4ffe75jztLqF6tMSH7","label":"New session - 2026-09-21T11:14:10.075Z","agent":"code","state":"busy"},{"id":"ses_f3c52514affezdTXZoKxZL1HmE","sessionID":"ses_f3c52514affezdTXZoKxZL1HmE","label":"Map Max Atlas gaps (@explore subagent)","agent":"explore","state":"retry"}],"messages":[{"id":"board_2c1e677c-0dc5-4393-916c-547b08d6cc84","timestamp":1789989672262,"from":"ses_f3c52514affezdTXZoKxZL1HmE","to":"main","type":"INFO","body":"ATLAS scope: code is newer than NEXT_TASK/HANDOFF. Actual writes ARE wired: MinimalPlanner.kt:302-345 -> AtlasAdaptiveExecutor (single binding), and AppMonitor.kt:585-647 -> ThermalCeilingRouter.kt:130-183 -> same executor (platform-thermal vs user-static ceiling). Both thermal alternatives use existing arbiter transactions; PLATFORM_HINT label does not mean a separate PowerHAL writer. Real bounded gap: AtlasRouteMemory generation callbacks default 0 (73-74), DataModule.kt:105-114 and AppMonitor.kt:341 omit callbacks; AppMonitor.kt:328-331 explicitly admits quarantine does not expire on reboot in production. Safest useful default emerging: improve read-only Atlas diagnostics to expose existing per-domain outcomes/failure reasons beyond current counts/first rows, with no new probing or writes. Mapping/tests in progress; no files/builds changed.","fromLabel":"Map Max Atlas gaps (@explore subagent)","toLabel":"New session - 2026-09-21T11:14:10.075Z"}],"hasMore":false,"cursor":"board_2c1e677c-0dc5-4393-916c-547b08d6cc84"}
```

---

## Assistant (Code · GPT-6 Astra · 14.6s)

_Thinking:_



سأوجّه التطوير نحو توافق تلقائي فعلي: تشخيص الفشل، اكتشاف البدائل المعروفة، ثم تنفيذها عبر مسار التحكم الآمن والتحقق من النتيجة. لكن لا يمكن ضمان تشغيل كل وظيفة على كل جهاز؛ إذا كان العتاد أو النواة لا يدعمانها، يجب أن يوضح أطلس ذلك بدل ادعاء الإصلاح أو تجربة كتابة خطرة.


**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c52514affezdTXZoKxZL1HmE",
  "type": "INFO",
  "body": "User clarified priority: autonomous compatibility across devices, diagnosing/discovering/repairing failures, not UI polish. Finish bounded scope recommendation focused on real production discovery -> recovery integration and route-memory generation issue you found. Avoid additional broad scan; return concrete candidate files/consumer/test seams and safety boundary. Research only remains."
}
```

**Output:**
```
{"id":"board_44b45c6f-6c3f-40cd-9616-cabebdf2bd5a","timestamp":1789989726633,"from":"main","to":"ses_f3c52514affezdTXZoKxZL1HmE","type":"INFO","body":"User clarified priority: autonomous compatibility across devices, diagnosing/discovering/repairing failures, not UI polish. Finish bounded scope recommendation focused on real production discovery -> recovery integration and route-memory generation issue you found. Avoid additional broad scan; return concrete candidate files/consumer/test seams and safety boundary. Research only remains.","fromLabel":"New session - 2026-09-21T11:14:10.075Z","toLabel":"Map Max Atlas gaps (@explore subagent)","availability":{"observedAt":1789989726634,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"retry"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

---

## Assistant (Code · GPT-6 Astra · 14.1s)

_Thinking:_



**Tool: bash**

**Input:**
```json
{
  "command": "java -version",
  "description": "Check available Java runtime for targeted verification"
}
```

**Output:**
```
openjdk version "25.0.4" 2026-07-21
OpenJDK Runtime Environment (build 25.0.4+7-1-26.04-Ubuntu)
OpenJDK 64-Bit Server VM (build 25.0.4+7-1-26.04-Ubuntu, mixed mode, sharing)

```

**Tool: read**

**Input:**
```json
{
  "filePath": "/root/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable",
  "limit": 40
}
```

**Output:**
```
<path>/root/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable</path>
<type>directory</type>
<entries>
2.3.10/

(1 entries)
</entries>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/root/android-sdk",
  "limit": 40
}
```

**Error:**
```
File not found: /root/android-sdk
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/usr/lib/jvm",
  "limit": 40
}
```

**Output:**
```
<path>/usr/lib/jvm</path>
<type>directory</type>
<entries>
.java-1.17.0-openjdk-arm64.jinfo
.java-1.25.0-openjdk-arm64.jinfo
default-java/
java-1.17.0-openjdk-arm64/
java-1.25.0-openjdk-arm64/
java-17-openjdk-arm64/
java-25-openjdk-arm64/
openjdk-17/

(8 entries)
</entries>
```

**Tool: skill**

**Input:**
```json
{
  "name": "gsd-progress"
}
```

**Output:**
```
<skill_content name="gsd-progress">
# Skill: gsd-progress

<objective>
Check project progress, summarize recent work and what's ahead, then intelligently route to the next action.

Three modes:
- **default**: Show progress report + intelligently route to the next action (execute or plan). Provides situational awareness before continuing work.
- **--next**: Automatically advance to the next logical step without manual route selection. Reads STATE.md, ROADMAP.md, and phase directories. Supports `--force` to bypass safety gates.
- **--do "task description"**: Analyze freeform natural language and dispatch to the most appropriate GSD command. Never does the work itself — matches intent, confirms, hands off.
- **--forensic**: Append a 6-check integrity audit after the standard progress report.
</objective>

<flags>
- **--next**: Detect current project state and automatically invoke the next logical GSD workflow step. Scans all prior phases for incomplete work before routing. `--next --force` bypasses safety gates.
- **--do "..."**: Smart dispatcher — match freeform intent to the best GSD command using routing rules, confirm the match, then hand off.
- **--forensic**: Run 6-check integrity audit after the standard progress report.
- **(no flag)**: Standard progress check + intelligent routing (Routes A through F).
</flags>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/progress.md
@$HOME/.claude/get-shit-done/workflows/next.md
@$HOME/.claude/get-shit-done/workflows/do.md
@$HOME/.claude/get-shit-done/references/ui-brand.md
</execution_context>

<process>
Arguments provided: "$ARGUMENTS"
Parse the first token from the provided arguments:
- If it is `--next`: strip the flag, execute the next workflow (passing remaining args e.g. --force).
- If it is `--do`: strip the flag, pass remainder as freeform intent to the do workflow.
- Otherwise: execute the progress workflow end-to-end (pass --forensic through if present).

Preserve all routing logic from the target workflow.
</process>

Base directory for this skill: /root/.claude/skills/gsd-progress
Relative paths in this skill (e.g., scripts/, reference/) are relative to this base directory.
Note: file list is sampled.

<skill_files>

</skill_files>
</skill_content>
```

---

## Assistant (Code · GPT-6 Astra · 14.1s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/root/.claude/get-shit-done/workflows/do.md",
  "limit": 220
}
```

**Output:**
```
<path>/root/.claude/get-shit-done/workflows/do.md</path>
<type>file</type>
<content>
1: <purpose>
2: Analyze freeform text from the user and route to the most appropriate GSD command. This is a dispatcher — it never does the work itself. Match user intent to the best command, confirm the routing, and hand off.
3: </purpose>
4: 
5: <required_reading>
6: Read all files referenced by the invoking prompt's execution_context before starting.
7: </required_reading>
8: 
9: <process>
10: 
11: <step name="validate">
12: **Check for input.**
13: 
14: 
15: **Text mode (`workflow.text_mode: true` in config or `--text` flag):** Set `TEXT_MODE=true` if `--text` is present in `$ARGUMENTS` OR `text_mode` from init JSON is `true`. When TEXT_MODE is active, replace every `AskUserQuestion` call with a plain-text numbered list and ask the user to type their choice number. This is required for non-Claude runtimes (OpenAI Codex, Gemini CLI, etc.) where `AskUserQuestion` is not available.
16: If `$ARGUMENTS` is empty, ask via AskUserQuestion:
17: 
18: ```
19: What would you like to do? Describe the task, bug, or idea and I'll route it to the right GSD command.
20: ```
21: 
22: Wait for response before continuing.
23: </step>
24: 
25: <step name="check_project">
26: **Check if project exists.**
27: 
28: ```bash
29: INIT=$(gsd-sdk query state.load 2>/dev/null)
30: ```
31: 
32: Track whether `.planning/` exists — some routes require it, others don't.
33: </step>
34: 
35: <step name="route">
36: **Match intent to command.**
37: 
38: Evaluate `$ARGUMENTS` against these routing rules. Apply the **first matching** rule:
39: 
40: | If the text describes... | Route to | Why |
41: |--------------------------|----------|-----|
42: | Starting a new project, "set up", "initialize" | `/gsd:new-project` | Needs full project initialization |
43: | Mapping or analyzing an existing codebase | `/gsd:map-codebase` | Codebase discovery |
44: | A bug, error, crash, failure, or something broken | `/gsd:debug` | Needs systematic investigation |
45: | Spiking, "test if", "will this work", "experiment", "prove this out", validate feasibility | `/gsd:spike` | Throwaway experiment to validate feasibility |
46: | Sketching, "mockup", "what would this look like", "prototype the UI", "design this", explore visual direction | `/gsd:sketch` | Throwaway HTML mockups to explore design |
47: | Wrapping up spikes, "package the spikes", "consolidate spike findings" | `/gsd:spike --wrap-up` | Package spike findings into reusable skill |
48: | Wrapping up sketches, "package the designs", "consolidate sketch findings" | `/gsd:sketch --wrap-up` | Package sketch findings into reusable skill |
49: | Exploring, researching, comparing, or "how does X work" | `/gsd:explore` | Socratic ideation and idea routing |
50: | Discussing vision, "how should X look", brainstorming | `/gsd:discuss-phase` | Needs context gathering |
51: | A complex task: refactoring, migration, multi-file architecture, system redesign | `/gsd:phase` | Needs a full phase with plan/build cycle |
52: | Planning a specific phase or "plan phase N" | `/gsd:plan-phase` | Direct planning request |
53: | Executing a phase or "build phase N", "run phase N" | `/gsd:execute-phase` | Direct execution request |
54: | Running all remaining phases automatically | `/gsd:autonomous` | Full autonomous execution |
55: | A review or quality concern about existing work | `/gsd:verify-work` | Needs verification |
56: | Checking progress, status, "where am I" | `/gsd:progress` | Status check |
57: | Resuming work, "pick up where I left off" | `/gsd:resume-work` | Session restoration |
58: | A note, idea, or "remember to..." | `/gsd:capture` | Capture for later |
59: | Adding tests, "write tests", "test coverage" | `/gsd:add-tests` | Test generation |
60: | Completing a milestone, shipping, releasing | `/gsd:complete-milestone` | Milestone lifecycle |
61: | A specific, actionable, small task (add feature, fix typo, update config) | `/gsd:quick` | Self-contained, single executor |
62: 
63: **Requires `.planning/` directory:** All routes except `/gsd:new-project`, `/gsd:map-codebase`, `/gsd:spike`, `/gsd:sketch`, and `/gsd:help`. If the project doesn't exist and the route requires it, suggest `/gsd:new-project` first.
64: 
65: **Ambiguity handling:** If the text could reasonably match multiple routes, ask the user via AskUserQuestion with the top 2-3 options. For example:
66: 
67: ```
68: "Refactor the authentication system" could be:
69: 1. /gsd:phase — Full planning cycle (recommended for multi-file refactors)
70: 2. /gsd:quick — Quick execution (if scope is small and clear)
71: 
72: Which approach fits better?
73: ```
74: </step>
75: 
76: <step name="display">
77: **Show the routing decision.**
78: 
79: ```
80: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
81:  GSD ► ROUTING
82: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
83: 
84: **Input:** {first 80 chars of $ARGUMENTS}
85: **Routing to:** {chosen command}
86: **Reason:** {one-line explanation}
87: ```
88: </step>
89: 
90: <step name="dispatch">
91: **Invoke the chosen command.**
92: 
93: Run the selected `/gsd-*` command, passing `$ARGUMENTS` as args.
94: 
95: If the chosen command expects a phase number and one wasn't provided in the text, extract it from context or ask via AskUserQuestion.
96: 
97: After invoking the command, stop. The dispatched command handles everything from here.
98: </step>
99: 
100: </process>
101: 
102: <success_criteria>
103: - [ ] Input validated (not empty)
104: - [ ] Intent matched to exactly one GSD command
105: - [ ] Ambiguity resolved via user question (if needed)
106: - [ ] Project existence checked for routes that require it
107: - [ ] Routing decision displayed before dispatch
108: - [ ] Command invoked with appropriate arguments
109: - [ ] No work done directly — dispatcher only
110: </success_criteria>

(End of file - total 110 lines)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 25.4s)

_Thinking:_

**Refining task management**

I’m considering the importance of skill as a tool rather than a hierarchy while adapting to task scope. I realize that without complete research, we can't just jump back into action. There seems to be no non-overlap in the current planning, so I could establish a high-level plan for the next task before getting into the detailed file context. I need to avoid unnecessary delays since I estimate the agent should complete this in about 10 minutes, and 4 minutes have already passed.

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/build.gradle.kts",
  "limit": 230
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/build.gradle.kts</path>
<type>file</type>
<content>
1: @file:Suppress("UnstableApiUsage")
2: 
3: import com.android.build.gradle.internal.api.BaseVariantOutputImpl
4: import com.android.build.gradle.tasks.PackageAndroidArtifact
5: import org.jetbrains.kotlin.gradle.dsl.JvmTarget
6: 
7: plugins {
8:     alias(libs.plugins.kotlin.serialization)
9:     alias(libs.plugins.agp.app)
10:     alias(libs.plugins.compose.compiler)
11:     alias(libs.plugins.ksp)
12:     id("kotlin-parcelize")
13:     id("dagger.hilt.android.plugin")
14: }
15: 
16: android {
17:     namespace = "nd.max"
18:     // API 37 is not published on the GitHub-hosted SDK repository; API 36
19:     // is the newest stable level available there and satisfies the AAR
20:     // metadata floor of the current androidx alphas.
21:     compileSdk = 36
22: 
23:     // كلمة مرور مخزن المفاتيح من سر CI (KS_PWD / KEYSTORE_PASSWORD).
24:     // بناء release غير موقّع غير صالح كـ priv-app، لذلك نفشل مبكرًا عند طلبه.
25:     val ksPwd: String? = System.getenv("KS_PWD")
26:     val releaseArtifactRequested = gradle.startParameter.taskNames.any { requestedTask ->
27:         requestedTask.substringAfterLast(':') in setOf("assembleRelease", "bundleRelease")
28:     }
29:     if (releaseArtifactRequested && ksPwd.isNullOrEmpty()) {
30:         throw GradleException("KS_PWD must be set to produce a signed release artifact")
31:     }
32: 
33:     defaultConfig {
34:         applicationId = "nd.max"
35:         minSdk = 29
36:         targetSdk = 36
37:         versionCode = 1
38:         versionName = "1.0"
39:         vectorDrawables.useSupportLibrary = true
40:         buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
41:         ndk {
42:             abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
43:         }
44:     }
45: 
46:     tasks.matching { task ->
47:         task.name in setOf("packageRelease", "assembleRelease", "bundleRelease")
48:     }.configureEach {
49:         doFirst {
50:             check(!ksPwd.isNullOrEmpty()) {
51:                 "KS_PWD must be set to produce a signed release artifact"
52:             }
53:         }
54:     }
55: 
56:     signingConfigs {
57:         create("release") {
58:             storeFile = file("azenith.jks")
59:             if (!ksPwd.isNullOrEmpty()) {
60:                 storePassword = ksPwd
61:                 keyAlias = "azenith_key"
62:                 keyPassword = ksPwd
63:             }
64:         }
65:     }
66:     
67:     // ── ملاحظة عن `lintVitalRelease` (قِيست، ولا تُغيَّر هنا) ──
68:     //
69:     // `assembleRelease` يستدعي `lintVitalRelease` تلقائيًّا، وقِسناها في هذه البيئة:
70:     // **٣ دقائق و٢٩ ثانية** من أصل ~١٥ دقيقة (٤ أنوية · ١٥ جيجابايت، نفس مقاس runner).
71:     //
72:     // **ولا يُعطَّل هنا:** `checkReleaseBuilds = false` **يمحو المهمة نفسها** (تحقّقنا:
73:     // اختفت من `:app:tasks --all`)، فيصير الحاجز غائبًا لا منقولًا — وهذا إسقاط لجودة لا
74:     // تسريع لبناء. الفصل جرى في `.github/workflows/build.yml`: مهمة CI موازية تُشغّل
75:     // `:app:lintVitalRelease` كما هي، وبناء الحزمة يستثنيها بـ`-x :app:lintVitalRelease`.
76:     // الحاجز نفسه، وبالتوازي، وبلا مَسّ سلوك البناء المحلي.
77: 
78:     androidResources {
79:         // [FIX] AGP يرفض الجمع بين توليده الآلي و`android:localeConfig` الصريح في
80:         // البيان («Locale config generation was requested but user locale config is
81:         // present in manifest») فيفشل :app:processDebugMainManifest ويسقط البناء كله.
82:         // نُبقي قائمتنا الصريحة في res/xml/locales_config.xml لأنها المصدر الوحيد
83:         // الذي يضمن ظهور اللغات الـ85 كاملةً في منتقي النظام — ومنها ما لا يستنتجه
84:         // التوليد الآلي من أسماء المجلدات (b+sr+Latn، zh-rHK، pt-rPT…) — ولأن
85:         // tools/i18n_coverage.py --check-codes يتحقق من مطابقتها للمجلدات والمنتقي.
86:         generateLocaleConfig = false
87:     }
88: 
89:     buildTypes {
90:         release {
91:             isMinifyEnabled = true
92:             isShrinkResources = true
93:             vcsInfo.include = false
94:             signingConfig =
95:                 if (ksPwd.isNullOrEmpty()) null
96:                 else signingConfigs.getByName("release")
97:             
98:             proguardFiles(
99:                 getDefaultProguardFile("proguard-android-optimize.txt"), 
100:                 "proguard-rules.pro"
101:             )
102:         }
103:         debug {
104:             isMinifyEnabled = false
105:         }
106:     }
107: 
108:     buildFeatures {
109:         compose = true
110:         buildConfig = true
111:         aidl = true
112:     }
113: 
114:     packaging {
115:         resources {
116:             excludes += "/META-INF/{AL2.0,LGPL2.1}"
117:             excludes += "META-INF/*.version"
118:             excludes += "DebugProbesKt.bin"
119:             excludes += "kotlin-tooling-metadata.json"
120:         }
121:     }
122: 
123:     tasks.withType<PackageAndroidArtifact> {
124:         doFirst { appMetadata.asFile.orNull?.writeText("") }
125:     }
126: 
127:     compileOptions {
128:         sourceCompatibility = JavaVersion.VERSION_17
129:         targetCompatibility = JavaVersion.VERSION_17
130:     }
131: 
132:     kotlin {
133:         compilerOptions {
134:             jvmTarget.set(JvmTarget.JVM_17)
135:         }
136:     }
137: }
138: 
139: dependencies {
140:     implementation(platform(libs.androidx.compose.bom))
141:     
142:     implementation(libs.androidx.compose.material)
143:     implementation(libs.androidx.compose.ui)
144:     implementation(libs.androidx.compose.ui.text.google.fonts)
145:     implementation(libs.androidx.compose.ui.tooling.preview)
146:     implementation(libs.androidx.compose.animation)
147:     implementation(libs.androidx.compose.animation.core)
148: 
149:     implementation(libs.androidx.navigation.compose)
150:     
151:     implementation(libs.androidx.compose.material3)
152:     implementation(libs.androidx.material.icons.extended)
153:     implementation(libs.material.kolor)
154:     implementation(libs.haze)
155:     implementation(libs.haze.blur)
156:     implementation(libs.haze.materials)
157:     implementation(libs.me.zhanghai.android.appiconloader.coil)
158:     implementation(libs.io.coil.kt.coil.compose)
159:     implementation(libs.androidx.documentfile)
160: 
161:     implementation(libs.com.github.topjohnwu.libsu.core)
162:     implementation(libs.com.github.topjohnwu.libsu.service)
163:     implementation(libs.com.github.topjohnwu.libsu.io)
164: 
165:     // Shizuku: طبقة امتياز ثانية بمستوى ADB بلا جذر (AR-20).
166:     // provider ليس اختياريًا: يحمل ShizukuProvider الذي يُصرَّح به في البيان.
167:     implementation(libs.shizuku.api)
168:     implementation(libs.shizuku.provider)
169: 
170:     implementation(libs.androidx.activity.compose)
171:     implementation(libs.androidx.lifecycle.runtime.compose)
172:     implementation(libs.androidx.lifecycle.service)
173:     implementation(libs.yalantis.ucrop)
174:     implementation(libs.androidx.appcompat)
175:     implementation(libs.androidx.transition)
176:     implementation(libs.kotlinx.serialization.json)
177:     implementation(libs.androidx.lifecycle.viewmodel.compose)
178:     implementation(libs.ansi.library)
179:     implementation(libs.ansi.library.ktx)
180:     implementation(libs.hiddenapibypass)
181:     implementation(libs.coil.gif)
182:     implementation(libs.compose.markdown)
183: 
184:     // Hilt
185:     implementation("com.google.dagger:hilt-android:2.59.2")
186:     ksp("com.google.dagger:hilt-compiler:2.59.2")
187:     implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
188: 
189:     // Terminal (ported from ZKM)
190:     implementation(project(":terminal-view"))
191:     implementation(project(":kernel-flasher"))
192:     implementation(libs.media3.exoplayer)
193:     implementation(libs.media3.ui)
194: 
195:     // ── الاختبارات الوحدوية ──
196:     // عقد نصوص التوصيات↔الأفعال (RecommendationTextClassifierTest) —
197:     // أول خط دفاع ضد انزياح الصياغة بين المصادر والمصنِّف
198:     testImplementation("junit:junit:4.13.2")
199:     // Local JVM tests execute against the host JDK, so Android's framework
200:     // org.json stubs must be replaced by the real JSON implementation.
201:     testImplementation("org.json:json:20260814")
202: }

(End of file - total 202 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/local.properties",
  "limit": 40
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/local.properties</path>
<type>file</type>
<content>
1: sdk.dir=/home/codespace/android-sdk

(End of file - total 1 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/root/.gradle/caches/modules-2/files-2.1",
  "limit": 180
}
```

**Output:**
```
<path>/root/.gradle/caches/modules-2/files-2.1</path>
<type>directory</type>
<entries>
androidx.activity/
androidx.annotation/
androidx.appcompat/
androidx.arch.core/
androidx.autofill/
androidx.cardview/
androidx.collection/
androidx.compose.animation/
androidx.compose.foundation/
androidx.compose.material/
androidx.compose.material3/
androidx.compose.runtime/
androidx.compose.ui/
androidx.compose/
androidx.concurrent/
androidx.constraintlayout/
androidx.coordinatorlayout/
androidx.core/
androidx.cursoradapter/
androidx.customview/
androidx.databinding/
androidx.documentfile/
androidx.drawerlayout/
androidx.dynamicanimation/
androidx.emoji2/
androidx.exifinterface/
androidx.fragment/
androidx.graphics/
androidx.hilt/
androidx.interpolator/
androidx.lifecycle/
androidx.loader/
androidx.media/
androidx.media3/
androidx.navigation/
androidx.navigationevent/
androidx.profileinstaller/
androidx.recyclerview/
androidx.resourceinspection/
androidx.room/
androidx.savedstate/
androidx.sqlite/
androidx.startup/
androidx.tracing/
androidx.transition/
androidx.vectordrawable/
androidx.versionedparcelable/
androidx.viewpager/
androidx.viewpager2/
androidx.window/
com.android.application/
com.android.databinding/
com.android.tools.analytics-library/
com.android.tools.build.jetifier/
com.android.tools.build/
com.android.tools.ddms/
com.android.tools.external.com-intellij/
com.android.tools.external.org-jetbrains/
com.android.tools.layoutlib/
com.android.tools.lint/
com.android.tools.utp/
com.android.tools/
com.android/
com.atlassian.commonmark/
com.atlassian.pom/
com.github.ajalt.colormath/
com.github.Fox2Code.AndroidANSI/
com.github.jeziellago/
com.github.topjohnwu.libsu/
com.github.yalantis/
com.google.accompanist/
com.google.android.material/
com.google.auto.value/
com.google.auto/
com.google.code.findbugs/
com.google.code.gson/
com.google.crypto.tink/
com.google.dagger.hilt.android/
com.google.dagger/
com.google.devtools.ksp/
com.google.errorprone/
com.google.flatbuffers/
com.google.googlejavaformat/
com.google.guava/
com.google.j2objc/
com.google.jimfs/
com.google.protobuf/
com.googlecode.juniversalchardet/
com.intellij/
com.materialkolor/
com.squareup.okhttp3/
com.squareup.okio/
com.squareup/
com.sun.activation/
com.sun.istack/
com.sun.xml.bind.mvn/
com.sun.xml.bind/
com.sun.xml.fastinfoset/
commons-codec/
commons-io/
commons-logging/
dev.chrisbanes.haze/
dev.drewhamilton.poko/
io.coil-kt.coil3/
io.coil-kt/
io.noties.markwon/
jakarta.activation/
jakarta.inject/
jakarta.xml.bind/
javax.inject/
junit/
me.zhanghai.android.appiconloader/
net.java.dev.jna/
net.ltgt.gradle.incap/
net.sf.jopt-simple/
net.sf.kxml/
org.apache.commons/
org.apache.httpcomponents/
org.apache/
org.bitbucket.b_c/
org.bouncycastle/
org.checkerframework/
org.codehaus.groovy/
org.eclipse.ee4j/
org.glassfish.jaxb/
org.hamcrest/
org.jdom/
org.jetbrains.androidx.lifecycle/
org.jetbrains.androidx.savedstate/
org.jetbrains.compose.animation/
org.jetbrains.compose.collection-internal/
org.jetbrains.compose.foundation/
org.jetbrains.compose.material/
org.jetbrains.compose.material3/
org.jetbrains.compose.runtime/
org.jetbrains.compose.ui/
org.jetbrains.kotlin.android/
org.jetbrains.kotlin.plugin.compose/
org.jetbrains.kotlin.plugin.serialization/
org.jetbrains.kotlin/
org.jetbrains.kotlinx/
org.jetbrains/
org.jspecify/
org.junit/
org.jvnet.staxex/
org.lsposed.hiddenapibypass/
org.ow2.asm/
org.ow2/
org.slf4j/
org.sonatype.oss/
org.tensorflow/
org.xerial/

(152 entries)
</entries>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/team/safety-reviewer.md",
  "limit": 140
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/team/safety-reviewer.md</path>
<type>file</type>
<content>
1: # الدور: Safety & Control-plane Reviewer
2: 
3: **النموذج المُسند:** **GPT-5.6 Luna** — وهذا هو **التصعيد الافتراضي الأول في الفريق**: المنفّذ الأساسي هو
4: DeepSeek V4 Flash، فالمراجعة تأتي من عائلة مختلفة. نموذج يراجع عمله بنفسه يُخفي عماه — وهنا مصدر قيمة
5: هذا الدور، لا منافسته للمنفّذ.
6: **الميزانية:** حكم واحد لكل مهمة؛ حكم ثانٍ يحتاج سببًا جديدًا مكتوبًا (تعديل جوهرى بعد الحكم الأول).
7: **المرجع:** `docs/ai/ENGINEERING-CONTRACT.md` §8 (Critical Review) و§9 (Safety) و§13 (MaxManager Priorities).
8: 
9: ## المهمة
10: 
11: محاولة **تفنيد** العمل قبل قبوله، لا تأكيده. هذا الدور هو البوابة الأخيرة قبل الدمج، وهو الوحيد الذي
12: يحوّل الحكم إلى `BLOCKED`.
13: 
14: ## ما يراجعه
15: 
16: 1. **مسار الكتابة الوحيد** (ADR-11 / بوابة §6): هل ظهر `RootFileAccess` أو `Shell.cmd` أو `su -c`
17:    جديد تحت `ui/**`؟ هل أي كتابة عتاد تتجاوز `HardwareControlArbiter` (leases/baselines/rollback)؟
18: 2. **صدق البيانات** (ADR-07): هل يُعرض رقم بلا مصدر/حداثة؟ هل «غير معروف» صار `status_unknown`
19:    أم قيمة معقولة مُصنَّعة؟ أي تلمتري مُصنَّع = عيب مُعطِّل.
20: 3. **أمان الأسرار**: مفاتيح، tokens، keystores، `local.properties`، `KS_PWD`، سجلات — لا شيء منها يُودع أو يُطبع.
21: 4. **العمليات غير القابلة للرجوع**: push / reset --hard / حذف مدمر / كتابة مباشرة في عتاد المستخدم بلا تأكيد
22:    (`MaxRiskDialog` لأدوات ADR-16: Terminal، SetEdit، ActivityLauncher، KernelFlasher).
23: 5. **الموارد الحسّاسة للأخطاء**: دورة حياة أندرويد، التزامن (Mutex/SupervisorJob في `core/threading/`)،
24:    العمل على الخيط الرئيسي، سلوك الإقلاع (`post-fs-data.sh`/`service.sh`)، سياسة SELinux، توقيع/تثبيت APK،
25:    اختلافات الشريحة/ROM (Snapdragon/MTK/Exynos/Tensor/Unisoc).
26: 6. **الأداء**: إضافة عمل في التركيب (composition) أو حلقة daemon جديدة ⇒ كم مرّة تُنفَّذ في الثانية؟
27: 
28: ## طريقة الحكم
29: 
30: - لكل تغيير غير بديهي: **نموذج فشل واحد ملموس + تخفيفه**؛ وللتغييرات عالية الأثر ثلاثة نماذج فشل على الأقل.
31: - اسأل: «ما الذي يُسقط هذا في الإنتاج؟» ولا تكتفِ بالمسار السعيد.
32: - اذكر أدلة من: الكود + تشخيصات Serena + السجلات + الاختبارات المستهدفة — لا من الافتراض.
33: 
34: ## المخرجات
35: 
36: جدول: `site → failure mode → blast radius → mitigation → verdict`، ثم قرار واحد:
37: `APPROVE` / `APPROVE_WITH_CONCERNS` (مع شروط مكتوبة) / `BLOCK` (مع الشرط الواجب لإلغاء الحجب).
38: تُسجَّل الموافقة داخل تسليم المهمة في `docs/ai/HANDOFF.md`، لا في ملف سجل مستقل.
39: 
40: ## Kickoff prompt
41: 
42: ```
43: You are the Safety & Control-plane Reviewer for the MaxManager repo (a rooted Android performance module:
44: kernel/sysfs writes, SELinux policy, Magisk/KernelSU install scripts, plus an autonomous AI tuner).
45: 
46: Read: docs/ai/ENGINEERING-CONTRACT.md (§8 Critical Review, §9 Safety, §13 priorities), docs/ai/DECISIONS.md (ADR-07, ADR-11,
47: ADR-16, ADR-17), docs/ai/HANDOFF.md (§Invariants), docs/ai/VALIDATION.md §6, docs/ai/team/safety-reviewer.md.
48: 
49: You receive a diff. Try to falsify it, not to confirm it. Check, at minimum:
50: - the arbiter stays the only hardware write path (no new RootFileAccess / Shell.cmd / su -c under ui/**),
51: - no synthesized telemetry and unknown still renders as status_unknown (ADR-07),
52: - no secrets, keystores, tokens or signing material added, printed or committed,
53: - no irreversible or unreviewed device-mutating action (ADR-16 risk gate on Terminal/SetEdit/
54:   ActivityLauncher/KernelFlasher),
55: - lifecycle, threading, main-thread work, boot-time script order, SELinux policy, per-chipset/ROM
56:   differences, and frame/battery cost of anything added to composition or to a daemon loop.
57: 
58: For every non-trivial change name one concrete failure mode and its mitigation; for high-blast-radius
59: changes name at least three. Do not stop at the happy path.
60: 
61: Output a table site → failure mode → blast radius → mitigation → verdict, then exactly one of
62: APPROVE / APPROVE_WITH_CONCERNS (with written conditions) / BLOCK (with the condition that unblocks it).
63: State clearly what you could not verify and why.
64: ```

(End of file - total 64 lines)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 25.6s)

_Thinking:_



**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c52514affezdTXZoKxZL1HmE",
  "type": "INFO",
  "body": "I will inspect AtlasRouteMemory + production wiring now to implement your evidenced generation/quarantine issue as a prerequisite for autonomous recovery, not repeat the broad map. Please avoid further research on generation wiring; focus final on next real discovery/recovery bridge candidate. No writes in your scope."
}
```

**Output:**
```
{"id":"board_10ed9190-50e0-426b-8021-cd3ff1737a7f","timestamp":1789989806339,"from":"main","to":"ses_f3c52514affezdTXZoKxZL1HmE","type":"INFO","body":"I will inspect AtlasRouteMemory + production wiring now to implement your evidenced generation/quarantine issue as a prerequisite for autonomous recovery, not repeat the broad map. Please avoid further research on generation wiring; focus final on next real discovery/recovery bridge candidate. No writes in your scope.","fromLabel":"New session - 2026-09-21T11:14:10.075Z","toLabel":"Map Max Atlas gaps (@explore subagent)","availability":{"observedAt":1789989806344,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"busy"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

---

## Assistant (Code · GPT-6 Astra · 13.7s)

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt"
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import nd.max.core.atlas.AtlasStoreIo
4: import org.json.JSONObject
5: 
6: /** What the last attempt of one route did. Three facts, not two: see the class documentation. */
7: enum class AtlasRouteOutcome {
8:     /** The write was read back unchanged for the bounded confirmation window. */
9:     VERIFIED,
10: 
11:     /** The attempt did not take, but the baseline was restored. Retrying another route is safe. */
12:     FAILED,
13: 
14:     /**
15:      * The attempt did not take **and** the baseline could not be confirmed.
16:      *
17:      * This is deliberately not folded into [FAILED]: a route that failed cleanly teaches us nothing
18:      * about the device's safety, while a route that left an unknown state must not be tried again in
19:      * the same boot generation.
20:      */
21:     ROLLBACK_FAILED,
22: }
23: 
24: /** One route's remembered state, for one target, in one boot/privilege generation. */
25: data class AtlasRouteMemoryEntry(
26:     val target: String,
27:     val routeId: String,
28:     val lastOutcome: AtlasRouteOutcome,
29:     val lastVerifiedElapsedMs: Long?,
30:     val bootGeneration: Long,
31:     val privilegeGeneration: Long,
32:     val successes: Int,
33:     val failures: Int,
34:     val rollbackFailures: Int,
35: ) {
36:     /** Whether this route may still be attempted in the generation the entry was recorded in. */
37:     fun isQuarantinedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =
38:         rollbackFailures > 0 &&
39:             this.bootGeneration == bootGeneration &&
40:             this.privilegeGeneration == privilegeGeneration
41: 
42:     fun isVerifiedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =
43:         lastOutcome == AtlasRouteOutcome.VERIFIED &&
44:             successes > 0 &&
45:             this.bootGeneration == bootGeneration &&
46:             this.privilegeGeneration == privilegeGeneration
47: }
48: 
49: /**
50:  * Learns which route actually worked on **this** device, and remembers which routes must not be
51:  * retried.
52:  *
53:  * The point is that Atlas does not rediscover the same device every scan: once a route has been
54:  * verified on this phone, the next attempt can prefer it. Three rules keep that from becoming a
55:  * liability:
56:  *
57:  * 1. **Learning never overrides safety ordering.** The stored preference only breaks ties *inside* one
58:  *    transport tier (`PLATFORM_HINT` before `VENDOR_BRIDGE` before `ROOT_DAEMON` before
59:  *    `ARBITER_SYSFS`). A remembered sysfs route can never outrank a platform route.
60:  * 2. **A rollback failure is remembered as such, and expires with the boot.** Within one boot the
61:  *    route is quarantined, because the device was left in an unknown state. After a reboot the
62:  *    evidence is void — the vendor's own state was rebuilt — so it is retried rather than banned
63:  *    forever.
64:  * 3. **Nothing here is a claim about a device.** An entry records what *we* observed; it carries no
65:  *    value, no unit and no capability, so it cannot be promoted into a measurement.
66:  *
67:  * The store is bounded and self-validating: an entry this build cannot read is deleted and treated as
68:  * absent, never half-trusted.
69:  */
70: class AtlasRouteMemory(
71:     private val io: AtlasStoreIo,
72:     private val clockMs: () -> Long,
73:     private val bootGeneration: () -> Long = { 0L },
74:     private val privilegeGeneration: () -> Long = { 0L },
75:     private val maxEntries: Int = MAX_ENTRIES,
76: ) {
77: 
78:     init {
79:         require(maxEntries > 0) { "the route memory bound must be positive" }
80:     }
81: 
82:     /** The last route verified on this device for [target], or `null` when there is none. */
83:     fun preferredRoute(target: String): String? = entries()
84:         .filter { it.target == target && it.isVerifiedIn(bootGeneration(), privilegeGeneration()) }
85:         .maxByOrNull { it.lastVerifiedElapsedMs ?: Long.MIN_VALUE }
86:         ?.routeId
87: 
88:     /** Routes that must not be attempted again in this boot generation. */
89:     fun quarantinedRoutes(target: String): Set<String> = entries()
90:         .filter { it.target == target && it.isQuarantinedIn(bootGeneration(), privilegeGeneration()) }
91:         .map { it.routeId }
92:         .toSet()
93: 
94:     fun noteVerified(target: String, routeId: String) {
95:         val entry = load(target, routeId) ?: newEntry(target, routeId)
96:         store(copy(entry, AtlasRouteOutcome.VERIFIED, successes = entry.successes + 1))
97:     }
98: 
99:     /**
100:      * Records a failed attempt. [rollbackVerified] is required rather than optional because "we could
101:      * not restore the baseline" is the one failure that changes what may be attempted next.
102:      */
103:     fun noteFailed(target: String, routeId: String, rollbackVerified: Boolean) {
104:         val entry = load(target, routeId) ?: newEntry(target, routeId)
105:         store(
106:             copy(
107:                 entry,
108:                 if (rollbackVerified) AtlasRouteOutcome.FAILED else AtlasRouteOutcome.ROLLBACK_FAILED,
109:                 failures = entry.failures + 1,
110:                 rollbackFailures = entry.rollbackFailures + if (rollbackVerified) 0 else 1,
111:             ),
112:         )
113:     }
114: 
115:     /** Drops one target's memory. Used when a device config change invalidates what was learned. */
116:     fun forget(target: String): Int = io.list().count { name ->
117:         name.startsWith(NAME_PREFIX) && name.endsWith(NAME_SUFFIX) && name.contains(slug(target)) && io.delete(name)
118:     }
119: 
120:     fun clear(): Int = io.list().count { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) && io.delete(it) }
121: 
122:     fun entries(): List<AtlasRouteMemoryEntry> = io.list()
123:         .filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
124:         .mapNotNull { name -> io.read(name)?.let(::decode) }
125:         .sortedWith(compareBy({ it.target }, { it.routeId }))
126: 
127:     // ---- helpers -----------------------------------------------------------------------------------
128: 
129:     private fun newEntry(target: String, routeId: String) = AtlasRouteMemoryEntry(
130:         target = target,
131:         routeId = routeId,
132:         lastOutcome = AtlasRouteOutcome.FAILED,
133:         lastVerifiedElapsedMs = null,
134:         bootGeneration = bootGeneration(),
135:         privilegeGeneration = privilegeGeneration(),
136:         successes = 0,
137:         failures = 0,
138:         rollbackFailures = 0,
139:     )
140: 
141:     private fun copy(
142:         entry: AtlasRouteMemoryEntry,
143:         outcome: AtlasRouteOutcome,
144:         successes: Int = entry.successes,
145:         failures: Int = entry.failures,
146:         rollbackFailures: Int = entry.rollbackFailures,
147:     ) = entry.copy(
148:         lastOutcome = outcome,
149:         lastVerifiedElapsedMs =
150:             if (outcome == AtlasRouteOutcome.VERIFIED) clockMs() else entry.lastVerifiedElapsedMs,
151:         // The generations are refreshed on every write: the record always describes the context it was
152:         // last observed in, which is what makes the boot/privilege checks meaningful.
153:         bootGeneration = bootGeneration(),
154:         privilegeGeneration = privilegeGeneration(),
155:         successes = successes,
156:         failures = failures,
157:         rollbackFailures = rollbackFailures,
158:     )
159: 
160:     private fun load(target: String, routeId: String): AtlasRouteMemoryEntry? {
161:         val text = io.read(nameOf(target, routeId)) ?: return null
162:         val decoded = decode(text) ?: run {
163:             io.delete(nameOf(target, routeId))
164:             return null
165:         }
166:         return decoded.takeIf { it.target == target && it.routeId == routeId }
167:             ?: run {
168:                 io.delete(nameOf(target, routeId))
169:                 null
170:             }
171:     }
172: 
173:     private fun store(entry: AtlasRouteMemoryEntry) {
174:         pruneIfNeeded(entry.target, entry.routeId)
175:         io.write(nameOf(entry.target, entry.routeId), encode(entry))
176:     }
177: 
178:     /** Keeps the store bounded; the entry being written is always kept. */
179:     private fun pruneIfNeeded(target: String, routeId: String) {
180:         val names = io.list().filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
181:         if (names.size < maxEntries) return
182:         val keep = nameOf(target, routeId)
183:         names.filter { it != keep }.forEach { io.delete(it) }
184:     }
185: 
186:     // ---- explicit-shape serialization ---------------------------------------------------------------
187: 
188:     private fun encode(entry: AtlasRouteMemoryEntry): String {
189:         val root = JSONObject()
190:         root.put(KEY_SCHEMA, SCHEMA)
191:         root.put(KEY_TARGET, entry.target)
192:         root.put(KEY_ROUTE, entry.routeId)
193:         root.put(KEY_OUTCOME, entry.lastOutcome.name)
194:         root.put(KEY_VERIFIED_AT, entry.lastVerifiedElapsedMs ?: JSONObject.NULL)
195:         root.put(KEY_BOOT, entry.bootGeneration)
196:         root.put(KEY_PRIVILEGE, entry.privilegeGeneration)
197:         root.put(KEY_SUCCESSES, entry.successes)
198:         root.put(KEY_FAILURES, entry.failures)
199:         root.put(KEY_ROLLBACK_FAILURES, entry.rollbackFailures)
200:         return root.toString()
201:     }
202: 
203:     private fun decode(text: String): AtlasRouteMemoryEntry? {
204:         if (text.length > MAX_ENTRY_CHARS) return null
205:         val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
206:         if (root.optInt(KEY_SCHEMA, -1) != SCHEMA) return null
207:         if (!REQUIRED_KEYS.all { root.has(it) }) return null
208:         return runCatching {
209:             AtlasRouteMemoryEntry(
210:                 target = root.getString(KEY_TARGET),
211:                 routeId = root.getString(KEY_ROUTE),
212:                 lastOutcome = AtlasRouteOutcome.valueOf(root.getString(KEY_OUTCOME)),
213:                 lastVerifiedElapsedMs = if (root.isNull(KEY_VERIFIED_AT)) null else root.getLong(KEY_VERIFIED_AT),
214:                 bootGeneration = root.getLong(KEY_BOOT),
215:                 privilegeGeneration = root.getLong(KEY_PRIVILEGE),
216:                 successes = root.getInt(KEY_SUCCESSES).coerceAtLeast(0),
217:                 failures = root.getInt(KEY_FAILURES).coerceAtLeast(0),
218:                 rollbackFailures = root.getInt(KEY_ROLLBACK_FAILURES).coerceAtLeast(0),
219:             )
220:         }.getOrNull()
221:     }
222: 
223:     private fun nameOf(target: String, routeId: String): String =
224:         "$NAME_PREFIX${slug(target)}$NAME_SEPARATOR${slug(routeId)}$NAME_SUFFIX"
225: 
226:     /** Entry names are generated here, so an unusual target cannot name a file outside this store. */
227:     private fun slug(value: String): String {
228:         val cleaned = value.lowercase().map { character ->
229:             if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') {
230:                 character
231:             } else {
232:                 '-'
233:             }
234:         }.joinToString("").trim('-', '.').replace(Regex("-{2,}"), "-")
235:         return cleaned.take(MAX_SLUG_CHARS).ifEmpty { "unnamed" }
236:     }
237: 
238:     companion object {
239:         const val SCHEMA: Int = 1
240: 
241:         const val NAME_PREFIX: String = "route."
242:         const val NAME_SUFFIX: String = ".json"
243:         const val NAME_SEPARATOR: String = "."
244: 
245:         private const val MAX_ENTRIES = 64
246:         private const val MAX_ENTRY_CHARS = 2 * 1024
247:         private const val MAX_SLUG_CHARS = 48
248: 
249:         private const val KEY_SCHEMA = "schema"
250:         private const val KEY_TARGET = "target"
251:         private const val KEY_ROUTE = "route"
252:         private const val KEY_OUTCOME = "outcome"
253:         private const val KEY_VERIFIED_AT = "verifiedAt"
254:         private const val KEY_BOOT = "boot"
255:         private const val KEY_PRIVILEGE = "privilege"
256:         private const val KEY_SUCCESSES = "successes"
257:         private const val KEY_FAILURES = "failures"
258:         private const val KEY_ROLLBACK_FAILURES = "rollbackFailures"
259: 
260:         private val REQUIRED_KEYS: List<String> = listOf(
261:             KEY_SCHEMA,
262:             KEY_TARGET,
263:             KEY_ROUTE,
264:             KEY_OUTCOME,
265:             KEY_VERIFIED_AT,
266:             KEY_BOOT,
267:             KEY_PRIVILEGE,
268:             KEY_SUCCESSES,
269:             KEY_FAILURES,
270:             KEY_ROLLBACK_FAILURES,
271:         )
272:     }
273: }

(End of file - total 273 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/di/DataModule.kt",
  "limit": 320
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/di/DataModule.kt</path>
<type>file</type>
<content>
1: package nd.max.core.di
2: 
3: import android.app.ActivityManager
4: import android.content.Context
5: import android.os.Build
6: import android.os.SystemClock
7: import dagger.Module
8: import dagger.Provides
9: import dagger.hilt.InstallIn
10: import dagger.hilt.android.qualifiers.ApplicationContext
11: import dagger.hilt.components.SingletonComponent
12: import nd.max.core.atlas.AtlasDeviceIdentity
13: import nd.max.core.atlas.AtlasDiscovery
14: import nd.max.core.atlas.AtlasEvidenceStore
15: import nd.max.core.atlas.AtlasFailureLedger
16: import nd.max.core.atlas.AtlasFileReadTransport
17: import nd.max.core.atlas.AtlasFileStoreIo
18: import nd.max.core.atlas.AtlasRepository
19: import nd.max.core.atlas.AtlasResolver
20: import nd.max.core.atlas.AtlasReviewedSeeds
21: import nd.max.core.atlas.AtlasStoreIo
22: import nd.max.core.hardware.AndroidContextDataSource
23: import nd.max.core.hardware.AtlasAdaptiveExecutor
24: import nd.max.core.hardware.AtlasAdaptiveReadTransport
25: import nd.max.core.hardware.AtlasPrivilegedReadTransport
26: import nd.max.core.hardware.AtlasReadBudget
27: import nd.max.core.hardware.AtlasRouteMemory
28: import nd.max.core.hardware.HardwareControlArbiter
29: import nd.max.core.hardware.HardwareRepairExecutor
30: import nd.max.core.hardware.AtlasReadTransport
31: import nd.max.core.hardware.HardwareDataSource
32: import nd.max.core.privilege.PrivilegeManager
33: import nd.max.core.threading.DispatcherProvider
34: import nd.max.data.datasources.HardwareDataSourceImpl
35: import java.nio.file.Paths
36: import javax.inject.Singleton
37: 
38: /**
39:  * روابط طبقة البيانات للمحرك الموحد.
40:  *
41:  *  - HardwareDataSource يغذي لوحة القياسات الحية في الشاشة الرئيسية
42:  *    (HomeDashboardViewModel) بقياسات عتاد فعلية.
43:  *  - AndroidContextDataSource يغذي محرك MAX AI بسياق الجهاز (التطبيق
44:  *    الأمامي، البطارية، الحرارة، مستشعر الإضاءة...).
45:  *
46:  * مستودع التنبؤ القديم (IPredictorRepository/PredictorRepositoryImplV2)
47:  * حُذف مع شاشات التنبؤ المتفرقة — منطق التنبؤ الحي يعمل الآن داخل
48:  * محرك MAX AI عبر المتنبئ الأصلي (PredictorBridge) بلا وسيط مستودع.
49:  */
50: @Module
51: @InstallIn(SingletonComponent::class)
52: object DataModule {
53: 
54:     @Provides
55:     @Singleton
56:     fun provideHardwareDataSource(
57:         impl: HardwareDataSourceImpl
58:     ): HardwareDataSource = impl
59: 
60:     @Provides
61:     @Singleton
62:     fun provideAndroidContextDataSource(
63:         @ApplicationContext context: Context
64:     ): AndroidContextDataSource = AndroidContextDataSource(context)
65: 
66:     // ---- Max Atlas (`P5`/`T5.6`) -------------------------------------------------------------------
67:     //
68:     // Every Atlas binding is constructor injection with the process singletons wired once here. Two
69:     // properties matter more than the wiring itself:
70:     //  - **Nothing scans on construction.** Creating these objects reads nothing; a scan starts only
71:     //    when a caller asks for one, so building the graph cannot perform I/O on the main thread.
72:     //  - **The evidence directory is app-private and not backed up.** It is reconstructible, so it has
73:     //    no business in a backup, and a restored cache from another device would be evidence about a
74:     //    machine the user no longer has.
75: 
76:     // `Paths.get` لا `Path.of`: الآخيرة أُضيفت في API 34 (أو تحتاج core library desugaring) و`minSdk`
77:     // هنا 29، فكان النداء يرمي `NoSuchMethodError` على 29–33. `Paths.get` هي الطريق المتاح من API 26
78:     // لنفس النوع `java.nio.file.Path` — فالإصلاح استبدال النداء لا إسكات التحذير.
79:     @Provides
80:     @Singleton
81:     fun provideAtlasStoreIo(@ApplicationContext context: Context): AtlasStoreIo =
82:         AtlasFileStoreIo(Paths.get(AtlasFileStoreIo.directoryFor(context.noBackupFilesDir).absolutePath))
83: 
84:     @Provides
85:     @Singleton
86:     fun provideAtlasEvidenceStore(io: AtlasStoreIo): AtlasEvidenceStore = AtlasEvidenceStore(io)
87: 
88:     /**
89:      * Atlas mutations reuse the existing transaction boundary. The adaptive layer may choose another
90:      * route after a verified rollback, but it never owns a writer or bypasses the arbiter.
91:      */
92:     @Provides
93:     @Singleton
94:     fun provideAtlasRepairExecutor(arbiter: HardwareControlArbiter): HardwareRepairExecutor =
95:         HardwareRepairExecutor(arbiter)
96: 
97:     /**
98:      * What has actually worked on this device.
99:      *
100:      * It reuses the same atomic, app-private, non-backed-up store as the evidence cache but lives in
101:      * its own directory, so clearing one never clears the other.
102:      */
103:     @Provides
104:     @Singleton
105:     fun provideAtlasRouteMemory(
106:         @ApplicationContext context: Context,
107:         clockMs: () -> Long,
108:     ): AtlasRouteMemory = AtlasRouteMemory(
109:         io = AtlasFileStoreIo(
110:             Paths.get(AtlasFileStoreIo.directoryFor(context.noBackupFilesDir).absolutePath, ROUTE_MEMORY_DIR)
111:                 .toAbsolutePath(),
112:         ),
113:         clockMs = clockMs,
114:     )
115: 
116:     @Provides
117:     @Singleton
118:     fun provideAtlasAdaptiveExecutor(
119:         repairExecutor: HardwareRepairExecutor,
120:         routeMemory: AtlasRouteMemory,
121:     ): AtlasAdaptiveExecutor = AtlasAdaptiveExecutor(
122:         // `HardwareRepairExecutor` يُنفّذ `AtlasRepairPort` بنفسه، فلا غلاف مجهول هنا: كان
123:         // الغلاف يبدو كطبقة تُخفي التبعية وهو لا يُخفي شيئًا — والبناء كشفه لأنه لم يُوفِّر
124:         // النوع المطلوب في مسار آخر (AppMonitor) فسقطت الترجمة.
125:         repairExecutor = repairExecutor,
126:         memory = routeMemory,
127:     )
128: 
129:     /**
130:      * The identity, from what the platform **declares**.
131:      *
132:      * `Build.SOC_MODEL` and friends are the vendor's own statement; they are read here rather than
133:      * guessed from a file, and an empty value stays `null` instead of becoming a placeholder that
134:      * later looks like a real device name.
135:      */
136:     @Provides
137:     @Singleton
138:     fun provideAtlasDeviceIdentity(@ApplicationContext context: Context): AtlasDeviceIdentity {
139:         val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
140:         // `Build.SOC_MANUFACTURER` و`Build.SOC_MODEL` أُضيفا في API 31، و`minSdk` هنا 29 — وهما
141:         // **ليسا ثابتين يُدمجان** وقت الترجمة (قيمتهما تُقرأ من خصائص النظام في مُهيّئ الحقل)، فقراءتهما
142:         // على 29/30 ترمي `NoSuchFieldError`. والحماية صريحة هنا، ومجهولهما `null` لا نصًّا بديلًا
143:         // يشبه اسم جهاز حقيقي (وهو نفس المبدأ الذي يشرحه التعليق أعلاه).
144:         return AtlasDeviceIdentity(
145:             socManufacturer =
146:                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.ifBlank { null }
147:                 else null,
148:             socModel =
149:                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.ifBlank { null }
150:                 else null,
151:             hardware = Build.HARDWARE.ifBlank { null },
152:             board = Build.BOARD.ifBlank { null },
153:             supportedAbis = Build.SUPPORTED_ABIS.toList(),
154:             apiLevel = Build.VERSION.SDK_INT,
155:             kernelRelease = System.getProperty("os.version"),
156:             isLowRamDevice = activityManager?.isLowRamDevice,
157:             memoryClassMb = activityManager?.memoryClass,
158:         )
159:     }
160: 
161:     /**
162:      * Monotonic milliseconds. `elapsedRealtime` does not jump when the wall clock is corrected, which
163:      * is what evidence ages must be measured against.
164:      */
165:     @Provides
166:     @Singleton
167:     fun provideAtlasClockMs(): () -> Long = SystemClock::elapsedRealtime
168: 
169:     @Provides
170:     @Singleton
171:     fun provideAtlasFailureLedger(clockMs: () -> Long): AtlasFailureLedger =
172:         AtlasFailureLedger(clockMs = clockMs)
173: 
174:     @Provides
175:     @Singleton
176:     fun provideAtlasResolver(store: AtlasEvidenceStore, clockMs: () -> Long): AtlasResolver =
177:         AtlasResolver(store = store, clockMs = clockMs)
178: 
179:     /**
180:      * The real read path. Atlas prefers an already-authorized root read for protected kernel nodes,
181:      * while retaining the ordinary app read path for unrooted devices. Neither branch prompts or writes.
182:      */
183:     @Provides
184:     @Singleton
185:     fun provideAtlasReadTransport(): AtlasReadTransport = AtlasAdaptiveReadTransport(
186:         ordinary = AtlasFileReadTransport(),
187:         privileged = AtlasPrivilegedReadTransport(),
188:         privilegedAvailable = PrivilegeManager::cachedRootGranted,
189:     )
190: 
191:     /**
192:      * The two knowledge banks, in the order they are consulted: reviewed knowledge first, the
193:      * accumulated community vocabulary second. Both pass the same validator, and the order is the
194:      * whole design — the second bank exists to complete what the first could not answer, never to
195:      * overrule it.
196:      */
197:     @Provides
198:     @Singleton
199:     fun provideAtlasDiscovery(
200:         identity: AtlasDeviceIdentity,
201:         transport: AtlasReadTransport,
202:         clockMs: () -> Long,
203:     ): AtlasDiscovery = AtlasDiscovery(
204:         sources = AtlasDiscovery.defaultSources(),
205:         identity = identity,
206:         transport = transport,
207:         budget = AtlasReadBudget.DEFAULT,
208:         clockMs = clockMs,
209:     )
210: 
211:     @Provides
212:     @Singleton
213:     fun provideAtlasRepository(
214:         store: AtlasEvidenceStore,
215:         resolver: AtlasResolver,
216:         ledger: AtlasFailureLedger,
217:         identity: AtlasDeviceIdentity,
218:         dispatchers: DispatcherProvider,
219:     ): AtlasRepository = AtlasRepository(
220:         store = store,
221:         resolver = resolver,
222:         ledger = ledger,
223:         identity = identity,
224:         catalog = AtlasReviewedSeeds.catalog(),
225:         dispatcher = dispatchers.io,
226:     )
227: 
228:     /**
229:      * Subdirectory of the Atlas store that holds learned routes, kept apart from evidence.
230:      *
231:      * ويشير إلى ثابت `AtlasFileStoreIo` بدل نصّ ثانٍ: الرفيق (`AppMonitor`) يكتب المسارات في
232:      * عملية أخرى، وذاكرة مسارات بمجلدين مختلفين ليست ذاكرة واحدة.
233:      */
234:     private const val ROUTE_MEMORY_DIR = AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME
235: }

(End of file - total 235 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasAdaptiveExecutor.kt"
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasAdaptiveExecutor.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import nd.max.core.atlas.AtlasControlIntent
4: import nd.max.core.atlas.AtlasRouteCandidate
5: import nd.max.core.atlas.AtlasRouteDecision
6: import nd.max.core.atlas.AtlasRoutePlanner
7: import nd.max.core.atlas.AtlasRouteReason
8: import nd.max.core.atlas.AtlasRouteStatus
9: 
10: /**
11:  * One adaptive execution attempt owned by an Atlas route.
12:  *
13:  * The route only supplies a typed hardware transaction; it cannot supply a shell command or a raw
14:  * path to Atlas. [HardwareRepairExecutor] remains the single mutation boundary, so every attempt has
15:  * a baseline, read-back verification, a bounded confirmation window and rollback on drift.
16:  */
17: data class AtlasRouteBinding(
18:     val candidate: AtlasRouteCandidate,
19:     val request: HardwareRepairRequest,
20: )
21: 
22: /** The complete decision, including routes that were tried and why fallback stopped. */
23: data class AtlasAdaptiveResult(
24:     val decision: AtlasRouteDecision,
25:     val attempts: List<HardwareRepairResult>,
26:     val selectedRouteId: String? = null,
27:     val fallbackStopped: Boolean = false,
28:     val detail: String? = null,
29:     /** Routes that were not attempted, each with a stable reason code (never a sentence). */
30:     val skipped: List<Pair<String, String>> = emptyList(),
31: ) {
32:     val successful: Boolean get() = attempts.lastOrNull()?.successful == true
33: }
34: 
35: /**
36:  * Chooses and executes the best available route, then falls through to another route when the first
37:  * route proves unusable.
38:  *
39:  * This is deliberately not a "try every route blindly" loop:
40:  * - only routes that pass [AtlasRoutePlanner] are attempted;
41:  * - a verified rollback permits fallback;
42:  * - an unverified rollback stops immediately because the physical state is unknown;
43:  * - a successful route ends the search and becomes the route the caller can persist for this device.
44:  */
45: class AtlasAdaptiveExecutor(
46:     private val repairExecutor: AtlasRepairPort,
47:     /**
48:      * Optional per-device memory. When present it can only *narrow* what is attempted (a quarantined
49:      * route) or *break ties inside one safety tier* (a previously verified route). It can never
50:      * promote a route past the planner's transport ordering.
51:      */
52:     private val memory: AtlasRouteMemory? = null,
53: ) {
54:     fun execute(
55:         intent: AtlasControlIntent,
56:         bindings: List<AtlasRouteBinding>,
57:         measuredGoalAvailable: Boolean = true,
58:     ): AtlasAdaptiveResult {
59:         require(bindings.map { it.candidate.id }.distinct().size == bindings.size) {
60:             "Atlas route ids must be unique within one execution"
61:         }
62: 
63:         val targetKey = intent.target.name.lowercase()
64:         val quarantined = memory?.quarantinedRoutes(targetKey).orEmpty()
65:         val skipped = bindings
66:             .filter { it.candidate.id in quarantined }
67:             .map { it.candidate.id to SKIP_ROUTE_QUARANTINED }
68:         val preferred = memory?.preferredRoute(targetKey)
69: 
70:         val remaining = bindings
71:             .filter { it.candidate.id !in quarantined }
72:             .map { binding ->
73:                 // The boost is a priority shift, not a transport promotion: `AtlasRoutePlanner` orders
74:                 // by transport first, so a remembered sysfs route still loses to a platform route.
75:                 if (preferred != null && binding.candidate.id == preferred) {
76:                     binding.copy(candidate = binding.candidate.copy(priority = 0))
77:                 } else {
78:                     binding.copy(candidate = binding.candidate.copy(priority = binding.candidate.priority + 1))
79:                 }
80:             }
81:             .toMutableList()
82: 
83:         val attempts = mutableListOf<HardwareRepairResult>()
84:         var lastDecision = AtlasRouteDecision(
85:             status = AtlasRouteStatus.REVIEW_REQUIRED,
86:             reason = AtlasRouteReason.ROUTE_NOT_REVIEWED,
87:         )
88: 
89:         while (remaining.isNotEmpty()) {
90:             val decision = AtlasRoutePlanner.choose(
91:                 intent = intent,
92:                 candidates = remaining.map { it.candidate },
93:                 measuredGoalAvailable = measuredGoalAvailable,
94:             )
95:             lastDecision = decision
96:             val selected = decision.selected ?: break
97:             val binding = remaining.first { it.candidate.id == selected.id }
98:             remaining.removeAll { it.candidate.id == selected.id }
99: 
100:             val result = repairExecutor.execute(binding.request)
101:             attempts += result
102:             memory?.let { memory ->
103:                 when (result.state) {
104:                     HardwareRepairState.CONFIRMED_WINDOW -> memory.noteVerified(targetKey, selected.id)
105: 
106:                     HardwareRepairState.APPLY_FAILED_ROLLED_BACK,
107:                     HardwareRepairState.DRIFT_ROLLED_BACK,
108:                     -> memory.noteFailed(targetKey, selected.id, rollbackVerified = true)
109: 
110:                     HardwareRepairState.ROLLBACK_UNVERIFIED -> memory.noteFailed(targetKey, selected.id, rollbackVerified = false)
111: 
112:                     // A blocked attempt is an ownership decision, not a verdict about the route: the
113:                     // knob was never touched, so nothing about this route was learned.
114:                     HardwareRepairState.BLOCKED -> Unit
115:                 }
116:             }
117:             if (result.successful) {
118:                 return AtlasAdaptiveResult(
119:                     decision = decision,
120:                     attempts = attempts.toList(),
121:                     selectedRouteId = selected.id,
122:                     detail = "route verified",
123:                     skipped = skipped,
124:                 )
125:             }
126: 
127:             // A route that changed the device but could not restore the baseline is not safe to follow
128:             // with another route. The caller receives the failure and can surface recovery guidance.
129:             if (result.rollbackAttempted && result.rollbackVerified != true) {
130:                 return AtlasAdaptiveResult(
131:                     decision = decision,
132:                     attempts = attempts.toList(),
133:                     fallbackStopped = true,
134:                     detail = "fallback stopped: rollback was not verified",
135:                     skipped = skipped,
136:                 )
137:             }
138:         }
139: 
140:         return AtlasAdaptiveResult(
141:             decision = lastDecision,
142:             attempts = attempts.toList(),
143:             fallbackStopped = attempts.any { it.rollbackAttempted && it.rollbackVerified != true },
144:             detail = when {
145:                 attempts.isEmpty() && skipped.isNotEmpty() -> "every route for this target is quarantined"
146:                 attempts.isEmpty() -> "no eligible route"
147:                 else -> "all eligible routes failed"
148:             },
149:             skipped = skipped,
150:         )
151:     }
152: 
153:     private companion object {
154:         /** A stable machine reason, not a sentence: the caller owns the wording. */
155:         const val SKIP_ROUTE_QUARANTINED = "route-quarantined-after-unverified-rollback"
156:     }
157: }

(End of file - total 157 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/AppMonitor.kt",
  "offset": 260,
  "limit": 150
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/AppMonitor.kt</path>
<type>file</type>
<content>
260:             controlContext.filesDir,
261:             controlContext.applicationInfo.uid,
262:             android.os.Process.myPid(),
263:         )
264:         // Same directory as the journal, so this process honours the exact locks
265:         // the UI wrote: a per-app rule must never move a knob the user pinned.
266:         ManualControlLocks.configure(controlContext.filesDir)
267:         configureThermalRouter(controlContext)
268: 
269:         if (!initializeServices()) {
270:             AppMonitorLogger.fatal("Failed to initialize services (ActivityTaskManager/PowerManager/etc.), exiting")
271:             return
272:         }
273: 
274:         // ترويسة السجل بعد تهيئة الخدمات: تحتاج `packageManager` لقراءة إصدار التطبيق، وتُكتب
275:         // قبل الحلقة فتصير في أعلى الجلسة لا في وسطها.
276:         writeLogStartupHeader()
277: 
278:         runCatching { GpuTweakPersistence.applySaved() }
279:             .onFailure { AppMonitorLogger.e("startup: saved GPU Studio state failed", it) }
280:         // A stale Core Grid manual-frequency session flag (set before a crash)
281:         // would keep the module's profile binary from ever resetting CPU
282:         // limits. The property is non-persistent, so this only matters when
283:         // the companion restarts without a reboot.
284:         //
285:         // Durable manual locks must survive that restart: if the user still holds
286:         // a locked cpufreq knob, the stand-down flag is re-asserted from the lock
287:         // store instead of being cleared, otherwise the module's coarse shell
288:         // channel would reclaim knobs the user pinned (a lock the AI respects but
289:         // the service ignores is not a lock). Only cpufreq locks set this flag —
290:         // it is the CPU-limit channel's stand-down, not a global mode.
291:         val lockedCpuKnobs = ManualControlLocks.lockedKeys().filter(HardwareControlKey::isCpuLimits)
292:         if (lockedCpuKnobs.isNotEmpty()) {
293:             runCatching { shellExec("setprop sys.maxmanager.manual_freq_session 1") }
294:             AppMonitorLogger.i("startup: re-asserted manual session for ${lockedCpuKnobs.size} locked cpufreq knob(s)")
295:         } else {
296:             runCatching { shellExec("setprop sys.maxmanager.manual_freq_session 0") }
297:         }
298:         recoverStalePerAppState()
299:         // سجل نتائج الجلسة السابقة يخصّ عملية ماتت (والإقلاع يُصفّر العتاد أصلًا)، فإبقاؤه
300:         // يجعل الواجهة تعرض «نتيجة الآن» وهي نتيجة أمس. والتاريخ يبقى كاملًا في
301:         // `MaxManager.log` بأسطر `EVENT=PERAPP_KNOB` — فالمحو هنا لا يُفقد دليلًا.
302:         runCatching { PerAppHardwareStatus.clear() }
303:         AppMonitorLogger.i("AppMonitor companion started (pid=${android.os.Process.myPid()})")
304: 
305:         val lockChannel = acquireLock()
306: 
307:         val monitorThread = Thread.currentThread()
308:         Runtime.getRuntime().addShutdownHook(Thread {
309:             runCatching { revertPerAppConfig() }
310:             runCatching { shellExec("setprop sys.maxmanager.perapp.governor_isolation 0") }
311:             lockChannel?.close()
312:             monitorThread.interrupt()
313:         })
314: 
315:         runMonitorLoop()
316:     }
317: 
318:     /**
319:      * بناء مخطِّط مسارات سقف الحرارة مرّة واحدة في بداية العملية.
320:      *
321:      * `HardwareRepairExecutor` ثانٍ فوق **نفس** `mutationGate`: هذا ليس العطب المحذور في
322:      * `PerAppControlRegistry` (مُحكِّمان في عملية واحدة = جداول طلبات متنافرة)، بل غلاف رقيق حول
323:      * نفس البوّابة، والملكية تبقى في مكان واحد.
324:      *
325:      * وذاكرة المسارات في نفس مجلد الواجهة (`noBackupFilesDir/atlas/routes`) عبر ثابت مشترك في
326:      * `AtlasFileStoreIo` — فمساران مختلفان يعنيان ذاكرتين لا واحدة.
327:      *
328:      * **حدّ معروف ومعلن:** `bootGeneration`/`privilegeGeneration` تُترك على الافتراضي (صفر) كما
329:      * في مزوّد Hilt في الواجهة. فالنتيجة أن حجر مسار بعد استرجاع غير مؤكَّد يبقى حتى يُقلَّم المخزن،
330:      * لا حتى الإقلاع — أي أن قاعدة «الحجر ينتهي بالإقلاع» في `AtlasRouteMemory` غير مُشغَّلة بعد في
331:      * المسارين. ربط جيل إقلاع حقيقي تغيير يمسّ الواجهة والرفيق معًا، فلم يُفعل ضمنيًّا هنا.
332:      */
333:     private fun configureThermalRouter(controlContext: Context) {
334:         runCatching {
335:             val io = AtlasFileStoreIo(
336:                 Paths.get(
337:                     AtlasFileStoreIo.directoryFor(controlContext.noBackupFilesDir).absolutePath,
338:                     AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME,
339:                 ).toAbsolutePath(),
340:             )
341:             val memory = AtlasRouteMemory(io = io, clockMs = { android.os.SystemClock.elapsedRealtime() })
342:             thermalRouter = ThermalCeilingRouter(
343:                 registry = hardwareControlRegistry,
344:                 adaptive = AtlasAdaptiveExecutor(HardwareRepairExecutor(mutationGate), memory),
345:             )
346:         }.onFailure { AppMonitorLogger.e("EVENT=THERMAL_ROUTER_INIT_FAILED", it) }
347:     }
348: 
349:     // ── ترويسة السجل: تجعل ملف السجل يشرح نفسه ─────────────────────────────────
350: 
351:     /**
352:      * حقائق الجهاز والبناء كما تُقرأ هنا — لا تُخمَّن ولا تُترك فارغة.
353:      *
354:      * و`SOC_MODEL`/`SOC_MANUFACTURER` محميّان بـ API 31 (كما في `DataModule` و`LogsViewerViewModel`):
355:      * قراءتهما على 29/30 ترمي `NoSuchFieldError`، والمجهول يبقى `null` لا نصًّا يشبه اسم جهاز.
356:      */
357:     private fun logDeviceFacts(): DeviceFacts {
358:         val appVersion = runCatching {
359:             systemContext?.packageManager?.getPackageInfo("nd.max", 0)?.versionName
360:         }.getOrNull()
361:         val moduleVersion = runCatching {
362:             shellRead("grep '^version=' '${MaxManagerPaths.MODULE_DIR}/module.prop' 2>/dev/null | head -n1")
363:         }.getOrNull()?.substringAfter('=', "")?.trim()?.takeIf(String::isNotEmpty)
364:         return DeviceFacts(
365:             appVersion = appVersion,
366:             moduleVersion = moduleVersion,
367:             socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.ifBlank { null } else null,
368:             socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.ifBlank { null } else null,
369:             hardware = Build.HARDWARE.ifBlank { null },
370:             apiLevel = Build.VERSION.SDK_INT,
371:             kernel = System.getProperty("os.version"),
372:             // قياس لا ادّعاء: هذه العملية يبدأها `service.sh` بجذر، و"جذر" هنا هو هويّة العملية.
373:             rooted = runCatching { android.os.Process.myUid() == 0 }.getOrDefault(false),
374:             board = Build.BOARD.ifBlank { null },
375:             abi = Build.SUPPORTED_ABIS.firstOrNull(),
376:         )
377:     }
378: 
379:     /**
380:      * الإعداد الذي كان قائمًا وقت التشغيل — هو ما يجيب سؤال «عطل جهاز أم عطل إعداد؟».
381:      *
382:      * والقائمة تأتي من [LogSettingsDigest] لا من هنا: نفس القائمة تُكتب من عملية التطبيق أيضًا،
383:      * وقائمتان تتباعدان تُنتجان ملفين يبدوان صورة واحدة وهما ليستا كذلك.
384:      *
385:      * والقراءة عبر [PropertyUtils] (انعكاس على `SystemProperties`، بلا صندوق أوامر): أربعة عشر
386:      * `getprop` عند بدء العملية تعني أربعة عشر إنشاء عمل — كلفة بلا مقابل.
387:      */
388:     private fun logSettingsDigest(): List<Pair<String, String>> =
389:         LogSettingsDigest.of { key -> PropertyUtils.get(key) }
390: 
391:     /**
392:      * ترويسة التشغيل: الجهاز، والإعداد، ودليل القراءة، وقاموس الرموز — مرّة لكل عملية.
393:      *
394:      * ولماذا قبل الحلقة لا في تقرير منفصل: الملف المُرسَل هو الذي يجب أن يشرح نفسه، وترويسة
395:      * تُبنى عند المشاركة وحدها تترك أيّ نسخة مربوطة (`logcat` مثلًا) بلا سياق.
396:      */
397:     private fun writeLogStartupHeader() {
398:         if (logHeaderWritten) return
399:         logHeaderWritten = true
400:         runCatching { LogHeader.startupLines(logDeviceFacts(), logSettingsDigest()).forEach(AppMonitorLogger::i) }
401:             .onFailure { AppMonitorLogger.e("EVENT=LOG_HEADER_WRITE_FAILED", it) }
402:     }
403: 
404:     /**
405:      * كتلة جلسة التطبيق — تُكتب **بعد** محاولة تطبيق إعداداته، فتحمل ما طُلب فعلًا لا ما كان مأمورًا به.
406:      *
407:      * وهذا هو الفرق العملي: بلا `desired` في الملف يُعرف أن الكتابة فشلت ولا يُعرف أن المطلوب
408:      * كان `300000:2000000` — فيصير السؤال «هل فشل التطبيق أم فشل الطلب؟» بلا جواب.
409:      */

(Showing lines 260-409 of 2450. Use offset=410 to continue.)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 14.9s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt"
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import nd.max.core.atlas.AtlasStoreIo
4: import org.junit.Assert.assertEquals
5: import org.junit.Assert.assertFalse
6: import org.junit.Assert.assertNull
7: import org.junit.Assert.assertTrue
8: import org.junit.Test
9: 
10: class AtlasRouteMemoryTest {
11: 
12:     @Test
13:     fun `a verified route becomes this device's preferred route`() {
14:         val memory = memory()
15:         memory.noteVerified("cpu_frequency", "cpu.frequency.arbiter")
16: 
17:         assertEquals("cpu.frequency.arbiter", memory.preferredRoute("cpu_frequency"))
18:     }
19: 
20:     @Test
21:     fun `a clean failure does not quarantine the route`() {
22:         val memory = memory()
23:         memory.noteFailed("cpu_frequency", "cpu.frequency.arbiter", rollbackVerified = true)
24: 
25:         assertTrue(memory.quarantinedRoutes("cpu_frequency").isEmpty())
26:         assertNull(memory.preferredRoute("cpu_frequency"))
27:     }
28: 
29:     @Test
30:     fun `an unverified rollback quarantines the route inside the same generation`() {
31:         val memory = memory()
32:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
33: 
34:         assertEquals(setOf("gpu.frequency.arbiter"), memory.quarantinedRoutes("gpu_frequency"))
35:     }
36: 
37:     @Test
38:     fun `quarantine expires with the boot because the vendor state is rebuilt`() {
39:         var boot = 7L
40:         val io = InMemoryIo()
41:         val memory = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { boot })
42:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
43:         assertEquals(1, memory.quarantinedRoutes("gpu_frequency").size)
44: 
45:         boot = 8L
46:         assertTrue(
47:             "a route stranded in an unknown state last boot must be retried, not banned forever",
48:             memory.quarantinedRoutes("gpu_frequency").isEmpty(),
49:         )
50:     }
51: 
52:     @Test
53:     fun `a failure in another privilege generation does not quarantine the current one`() {
54:         var privilege = 1L
55:         val memory = AtlasRouteMemory(
56:             InMemoryIo(),
57:             clockMs = { 1_000L },
58:             bootGeneration = { 0L },
59:             privilegeGeneration = { privilege },
60:         )
61:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
62: 
63:         privilege = 2L
64:         assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())
65:     }
66: 
67:     @Test
68:     fun `the latest verified route wins`() {
69:         var now = 100L
70:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { 0L })
71:         memory.noteVerified("cpu_frequency", "cpu.frequency.arbiter")
72:         now = 500L
73:         memory.noteVerified("cpu_frequency", "cpu.frequency.vendor")
74: 
75:         assertEquals("cpu.frequency.vendor", memory.preferredRoute("cpu_frequency"))
76:     }
77: 
78:     @Test
79:     fun `a corrupt entry is discarded rather than half-trusted`() {
80:         val io = InMemoryIo()
81:         io.write("route.cpu_frequency.cpu.frequency.arbiter.json", "{\"schema\":1,\"target\":")
82:         val memory = AtlasRouteMemory(io, clockMs = { 0L })
83: 
84:         assertTrue(memory.entries().isEmpty())
85:         assertNull(memory.preferredRoute("cpu_frequency"))
86:     }
87: 
88:     @Test
89:     fun `an unknown schema is refused`() {
90:         val io = InMemoryIo()
91:         io.write(
92:             "route.gpu_frequency.gpu.frequency.arbiter.json",
93:             """
94:             {"schema":99,"target":"gpu_frequency","route":"gpu.frequency.arbiter","outcome":"VERIFIED",
95:              "verifiedAt":1,"boot":0,"privilege":0,"successes":1,"failures":0,"rollbackFailures":0}
96:             """.trimIndent(),
97:         )
98:         val memory = AtlasRouteMemory(io, clockMs = { 0L })
99: 
100:         assertTrue(memory.entries().isEmpty())
101:     }
102: 
103:     @Test
104:     fun `a target with unusual characters cannot name a file outside the store`() {
105:         val io = InMemoryIo()
106:         val memory = AtlasRouteMemory(io, clockMs = { 0L })
107:         memory.noteVerified("../../etc/passwd", "cpu.frequency.arbiter")
108: 
109:         assertTrue(io.list().all { !it.contains("..") && !it.contains("/") })
110:         assertEquals("cpu.frequency.arbiter", memory.preferredRoute("../../etc/passwd"))
111:     }
112: 
113:     @Test
114:     fun `the store stays bounded`() {
115:         val io = InMemoryIo()
116:         val memory = AtlasRouteMemory(io, clockMs = { 0L }, maxEntries = 4)
117:         repeat(12) { index -> memory.noteVerified("cpu_frequency", "route.number.$index") }
118: 
119:         assertTrue("the store must not grow without bound: ${io.list().size}", io.list().size <= 4)
120:     }
121: 
122:     private fun memory(): AtlasRouteMemory =
123:         AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { 0L })
124: 
125:     private class InMemoryIo : AtlasStoreIo {
126:         private val entries = LinkedHashMap<String, String>()
127:         override fun read(name: String): String? = entries[name]
128:         override fun write(name: String, text: String): Boolean {
129:             entries[name] = text
130:             return true
131:         }
132: 
133:         override fun delete(name: String): Boolean = entries.remove(name) != null
134:         override fun list(): List<String> = entries.keys.sorted()
135:     }
136: }

(End of file - total 136 lines)
</content>
```

**Tool: grep**

**Input:**
```json
{
  "pattern": "bootGeneration|BOOT_COUNT|boot_id|privilegeGeneration",
  "path": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max",
  "limit": 100
}
```

**Output:**
```
Found 83 matches
/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/AppMonitor.kt:
  Line 328:      * **حدّ معروف ومعلن:** `bootGeneration`/`privilegeGeneration` تُترك على الافتراضي (صفر) كما


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasResolver.kt:
  Line 87:     private val bootGeneration: () -> Long = { 0L },

  Line 88:     private val privilegeGeneration: () -> Long = { 0L },

  Line 95:         return lookup.evidence.freshnessAt().stalenessAt(clockMs(), bootGeneration(), privilegeGeneration())

  Line 125:                 .stalenessAt(clockMs(), bootGeneration(), privilegeGeneration())


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasRepository.kt:
  Line 133:     private val bootGeneration: () -> Long = { 0L },

  Line 134:     private val privilegeGeneration: () -> Long = { 0L },

  Line 149:         bootGeneration = bootGeneration(),

  Line 150:         privilegeGeneration = privilegeGeneration(),


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasModels.kt:
  Line 157:     val bootGeneration: Long,

  Line 158:     val privilegeGeneration: Long,

  Line 166:         require(bootGeneration >= 0L && privilegeGeneration >= 0L) { "generations are non-negative" }


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasPlatformProvider.kt:
  Line 838:         bootGeneration = 0L,

  Line 839:         privilegeGeneration = 0L,


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasEvidenceStore.kt:
  Line 63:         bootGeneration = observation.bootGeneration,

  Line 64:         privilegeGeneration = observation.privilegeGeneration,

  Line 103:         bootGeneration: Long,

  Line 104:         privilegeGeneration: Long,

  Line 107:         require(bootGeneration >= 0L && privilegeGeneration >= 0L) { "generations are non-negative" }

  Line 112:             bootGeneration.toString(),

  Line 113:             privilegeGeneration.toString(),

  Line 195:         root.put(KEY_BOOT, observation.bootGeneration)

  Line 196:         root.put(KEY_PRIVILEGE, observation.privilegeGeneration)

  Line 229:                 bootGeneration = root.getLong(KEY_BOOT),

  Line 230:                 privilegeGeneration = root.getLong(KEY_PRIVILEGE),


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasDiscovery.kt:
  Line 86:     private val bootGeneration: () -> Long = { 0L },

  Line 87:     private val privilegeGeneration: () -> Long = { 0L },

  Line 221:             currentGeneration = { bootGeneration() },

  Line 245:             bootGeneration = bootGeneration(),

  Line 246:             privilegeGeneration = privilegeGeneration(),


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasBackendProvider.kt:
  Line 43:     private val bootGeneration: Long = 0L,

  Line 44:     private val privilegeGeneration: Long = 0L,

  Line 49:         require(bootGeneration >= 0L && privilegeGeneration >= 0L) { "generations are non-negative" }

  Line 371:         bootGeneration = bootGeneration,

  Line 372:         privilegeGeneration = privilegeGeneration,


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasProbeSchedule.kt:
  Line 103:         bootGeneration: Long,

  Line 104:         privilegeGeneration: Long,

  Line 107:         require(bootGeneration >= 0L && privilegeGeneration >= 0L) { "generations are non-negative" }

  Line 122:                 held != null && !held.isStaleAt(now, bootGeneration, privilegeGeneration) ->


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasFreshness.kt:
  Line 48:     val bootGeneration: Long,

  Line 49:     val privilegeGeneration: Long,

  Line 53:         require(bootGeneration >= 0L) { "generations are non-negative" }

  Line 54:         require(privilegeGeneration >= 0L) { "generations are non-negative" }

  Line 70:     fun stalenessAt(nowMs: Long, bootGeneration: Long, privilegeGeneration: Long): AtlasStaleness {

  Line 71:         if (bootGeneration != this.bootGeneration) return AtlasStaleness.SUPERSEDED_BY_BOOT

  Line 72:         if (privilegeGeneration != this.privilegeGeneration) return AtlasStaleness.SUPERSEDED_BY_PRIVILEGE

  Line 83:     fun isStaleAt(nowMs: Long, bootGeneration: Long, privilegeGeneration: Long): Boolean =

  Line 84:         stalenessAt(nowMs, bootGeneration, privilegeGeneration).isStale


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasFailureLedger.kt:
  Line 142:     private val bootGeneration: () -> Long = { 0L },

  Line 143:     private val privilegeGeneration: () -> Long = { 0L },

  Line 151:         val bootGeneration: Long,

  Line 152:         val privilegeGeneration: Long,

  Line 165:         val currentBoot = bootGeneration()

  Line 173:             bootGeneration = currentBoot,

  Line 174:             privilegeGeneration = privilegeGeneration(),

  Line 198:         if (record.bootGeneration != bootGeneration()) {

  Line 202:         if (record.privilegeGeneration != privilegeGeneration()) {


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt:
  Line 30:     val bootGeneration: Long,

  Line 31:     val privilegeGeneration: Long,

  Line 37:     fun isQuarantinedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =

  Line 39:             this.bootGeneration == bootGeneration &&

  Line 40:             this.privilegeGeneration == privilegeGeneration

  Line 42:     fun isVerifiedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =

  Line 45:             this.bootGeneration == bootGeneration &&

  Line 46:             this.privilegeGeneration == privilegeGeneration

  Line 73:     private val bootGeneration: () -> Long = { 0L },

  Line 74:     private val privilegeGeneration: () -> Long = { 0L },

  Line 84:         .filter { it.target == target && it.isVerifiedIn(bootGeneration(), privilegeGeneration()) }

  Line 90:         .filter { it.target == target && it.isQuarantinedIn(bootGeneration(), privilegeGeneration()) }

  Line 134:         bootGeneration = bootGeneration(),

  Line 135:         privilegeGeneration = privilegeGeneration(),

  Line 153:         bootGeneration = bootGeneration(),

  Line 154:         privilegeGeneration = privilegeGeneration(),

  Line 195:         root.put(KEY_BOOT, entry.bootGeneration)

  Line 196:         root.put(KEY_PRIVILEGE, entry.privilegeGeneration)

  Line 214:                 bootGeneration = root.getLong(KEY_BOOT),

  Line 215:                 privilegeGeneration = root.getLong(KEY_PRIVILEGE),


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/ReadOnlyProbeAccess.kt:
  Line 468:         bootGeneration = 0L,

  Line 469:         privilegeGeneration = currentGeneration(),


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/PerAppRecoveryStore.kt:
  Line 44:             put("boot_id", bootId())

  Line 64:             put("boot_id", snapshot.bootId)

  Line 108:             bootId = j.optString("boot_id"),

  Line 187:     fun bootId(): String = runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("")

```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasRoutePlanner.kt",
  "limit": 320
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasRoutePlanner.kt</path>
<type>file</type>
<content>
1: package nd.max.core.atlas
2: 
3: private val ATLAS_ROUTE_ID = Regex("^[a-z][a-z0-9_.-]{2,63}$")
4: 
5: /** Execution transports are ordered by safety and platform ownership. */
6: enum class AtlasControlTransport {
7:     PLATFORM_HINT,
8:     VENDOR_BRIDGE,
9:     ROOT_DAEMON,
10:     ARBITER_SYSFS,
11:     READ_ONLY,
12: }
13: 
14: enum class AtlasRouteStatus {
15:     ELIGIBLE,
16:     BLOCKED,
17:     UNSUPPORTED,
18:     REVIEW_REQUIRED,
19: }
20: 
21: enum class AtlasRouteReason {
22:     PLATFORM_ROUTE_AVAILABLE,
23:     VERIFIED_VENDOR_ROUTE,
24:     VERIFIED_DAEMON_ROUTE,
25:     VERIFIED_ARBITER_ROUTE,
26:     BASELINE_UNREADABLE,
27:     ROLLBACK_UNPROVEN,
28:     UNIT_AMBIGUOUS,
29:     PROVIDER_AMBIGUOUS,
30:     PRIVILEGE_UNAVAILABLE,
31:     GOAL_UNMEASURABLE,
32:     ROUTE_NOT_REVIEWED,
33: }
34: 
35: /** Read-only facts that a route planner is allowed to consume. */
36: data class AtlasRouteEvidence(
37:     val providerId: String,
38:     val transport: AtlasControlTransport,
39:     val target: AtlasControlTarget,
40:     val readable: Boolean,
41:     val privilegeAvailable: Boolean,
42:     val unitProven: Boolean,
43:     val baselineReadable: Boolean,
44:     val rollbackProven: Boolean,
45:     val reviewed: Boolean,
46:     val ambiguous: Boolean = false,
47:     val reason: String = "",
48: ) {
49:     init { require(providerId.isNotBlank()) { "providerId is required" } }
50: }
51: 
52: data class AtlasRouteCandidate(
53:     val id: String,
54:     val evidence: AtlasRouteEvidence,
55:     val priority: Int,
56: ) {
57:     init {
58:         require(id.matches(ATLAS_ROUTE_ID)) { "route id is not canonical: $id" }
59:         require(priority >= 0) { "route priority must be non-negative" }
60:     }
61: }
62: 
63: data class AtlasRouteDecision(
64:     val status: AtlasRouteStatus,
65:     val selected: AtlasRouteCandidate? = null,
66:     val skipped: List<Pair<String, AtlasRouteReason>> = emptyList(),
67:     val reason: AtlasRouteReason? = null,
68: ) {
69:     init {
70:         if (status == AtlasRouteStatus.ELIGIBLE) requireNotNull(selected) { "eligible needs a selected route" }
71:         if (status != AtlasRouteStatus.ELIGIBLE) require(selected == null) { "blocked decisions select no route" }
72:     }
73: }
74: 
75: /**
76:  * Pure route selection. It does not read a node, request root, or mutate ownership. The first eligible
77:  * route wins after deterministic safety ordering; candidate routes are never promoted by a heuristic.
78:  */
79: object AtlasRoutePlanner {
80: 
81:     fun choose(
82:         intent: AtlasControlIntent,
83:         candidates: List<AtlasRouteCandidate>,
84:         measuredGoalAvailable: Boolean = true,
85:     ): AtlasRouteDecision {
86:         val applicable = candidates
87:             .filter { it.evidence.target == intent.target }
88:             .sortedWith(compareBy<AtlasRouteCandidate> { transportRank(it.evidence.transport) }
89:                 .thenByDescending { it.evidence.reviewed }
90:                 .thenBy { it.priority }
91:                 .thenBy { it.id })
92: 
93:         val skipped = mutableListOf<Pair<String, AtlasRouteReason>>()
94:         applicable.forEach { candidate ->
95:             val evidence = candidate.evidence
96:             val reason = rejectionReason(evidence, measuredGoalAvailable)
97:             if (reason == null) {
98:                 return AtlasRouteDecision(
99:                     status = AtlasRouteStatus.ELIGIBLE,
100:                     selected = candidate,
101:                     skipped = skipped,
102:                 )
103:             }
104:             skipped += candidate.id to reason
105:         }
106: 
107:         val finalReason = skipped.lastOrNull()?.second ?: AtlasRouteReason.ROUTE_NOT_REVIEWED
108:         val status = when (finalReason) {
109:             AtlasRouteReason.ROUTE_NOT_REVIEWED -> AtlasRouteStatus.REVIEW_REQUIRED
110:             AtlasRouteReason.PRIVILEGE_UNAVAILABLE,
111:             AtlasRouteReason.BASELINE_UNREADABLE,
112:             AtlasRouteReason.ROLLBACK_UNPROVEN,
113:             AtlasRouteReason.UNIT_AMBIGUOUS,
114:             AtlasRouteReason.PROVIDER_AMBIGUOUS,
115:             AtlasRouteReason.GOAL_UNMEASURABLE,
116:             -> AtlasRouteStatus.BLOCKED
117: 
118:             else -> AtlasRouteStatus.UNSUPPORTED
119:         }
120:         return AtlasRouteDecision(status = status, skipped = skipped, reason = finalReason)
121:     }
122: 
123:     private fun rejectionReason(evidence: AtlasRouteEvidence, goalAvailable: Boolean): AtlasRouteReason? {
124:         if (evidence.transport == AtlasControlTransport.READ_ONLY) return AtlasRouteReason.PRIVILEGE_UNAVAILABLE
125:         if (evidence.ambiguous) return AtlasRouteReason.PROVIDER_AMBIGUOUS
126:         if (!evidence.readable) return AtlasRouteReason.PRIVILEGE_UNAVAILABLE
127:         if (!evidence.privilegeAvailable && evidence.transport != AtlasControlTransport.PLATFORM_HINT) {
128:             return AtlasRouteReason.PRIVILEGE_UNAVAILABLE
129:         }
130:         if (!evidence.unitProven) return AtlasRouteReason.UNIT_AMBIGUOUS
131:         if (!evidence.baselineReadable) return AtlasRouteReason.BASELINE_UNREADABLE
132:         if (!evidence.rollbackProven) return AtlasRouteReason.ROLLBACK_UNPROVEN
133:         if (!goalAvailable && evidence.transport != AtlasControlTransport.PLATFORM_HINT) {
134:             return AtlasRouteReason.GOAL_UNMEASURABLE
135:         }
136:         if (!evidence.reviewed) return AtlasRouteReason.ROUTE_NOT_REVIEWED
137:         return null
138:     }
139: 
140:     private fun transportRank(transport: AtlasControlTransport): Int = when (transport) {
141:         AtlasControlTransport.PLATFORM_HINT -> 0
142:         AtlasControlTransport.VENDOR_BRIDGE -> 1
143:         AtlasControlTransport.ROOT_DAEMON -> 2
144:         AtlasControlTransport.ARBITER_SYSFS -> 3
145:         AtlasControlTransport.READ_ONLY -> 4
146:     }
147: }

(End of file - total 147 lines)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 29.2s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/privilege/PrivilegeManager.kt",
  "limit": 280
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/privilege/PrivilegeManager.kt</path>
<type>file</type>
<content>
1: /*
2:  * Copyright (C) 2026-2027 MaxManager contributors
3:  *
4:  * Licensed under the Apache License, Version 2.0.
5:  */
6: package nd.max.core.privilege
7: 
8: import com.topjohnwu.superuser.Shell
9: import kotlinx.coroutines.flow.MutableStateFlow
10: import kotlinx.coroutines.flow.StateFlow
11: import kotlinx.coroutines.flow.asStateFlow
12: 
13: /**
14:  * يجمع طبقتَي الامتياز (الجذر + Shizuku) في حكم واحد معلن (`AR-20`).
15:  *
16:  * **القراءة السلبية أولًا:** [cachedRootGranted] يقرأ الصدفة المُخزَّنة فقط ولا يستدعي
17:  * `su` — فلا تظهر نافذة صلاحية لمجرد فتح شاشة. طلب الجذر فعل صريح عبر [requestRoot].
18:  */
19: object PrivilegeManager {
20: 
21:     data class Snapshot(
22:         val level: PrivilegeLevel = PrivilegeLevel.NONE,
23:         val shizuku: ShizukuState = ShizukuState(),
24:         val rootGranted: Boolean = false,
25:     )
26: 
27:     private val _snapshot = MutableStateFlow(Snapshot())
28:     val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()
29: 
30:     /** جذر مُخزَّن مسبقًا — بلا استدعاء صلاحية. */
31:     fun cachedRootGranted(): Boolean = runCatching {
32:         Shell.getCachedShell()?.isRoot == true
33:     }.getOrDefault(false)
34: 
35:     /** فعل صريح من المستخدم: يطلب الجذر (قد تُظهر نافذة الصلاحية). */
36:     fun requestRoot(): Boolean = runCatching {
37:         val cached = Shell.getCachedShell()
38:         if (cached != null && !cached.isRoot) cached.close()
39:         Shell.getShell().isRoot
40:     }.getOrDefault(false)
41: 
42:     fun start() {
43:         ShizukuGateway.start()
44:         refresh()
45:     }
46: 
47:     fun stop() {
48:         ShizukuGateway.stop()
49:     }
50: 
51:     /** يقرأ الطبقتين ويحدّث [snapshot]. الطبقة الأعلى المتاحة هي [Snapshot.level]. */
52:     fun refresh(): Snapshot {
53:         val root = cachedRootGranted()
54:         val shizuku = ShizukuGateway.refresh()
55:         val level = when {
56:             root -> PrivilegeLevel.ROOT
57:             shizuku.ready -> PrivilegeLevel.SHIZUKU
58:             else -> PrivilegeLevel.NONE
59:         }
60:         val next = Snapshot(level = level, shizuku = shizuku, rootGranted = root)
61:         _snapshot.value = next
62:         return next
63:     }
64: }

(End of file - total 64 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasRepository.kt",
  "limit": 300
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasRepository.kt</path>
<type>file</type>
<content>
1: package nd.max.core.atlas
2: 
3: import kotlinx.coroutines.CancellationException
4: import kotlinx.coroutines.CoroutineDispatcher
5: import kotlinx.coroutines.CoroutineScope
6: import kotlinx.coroutines.Job
7: import kotlinx.coroutines.SupervisorJob
8: import kotlinx.coroutines.currentCoroutineContext
9: import kotlinx.coroutines.ensureActive
10: import kotlinx.coroutines.flow.MutableStateFlow
11: import kotlinx.coroutines.flow.StateFlow
12: import kotlinx.coroutines.flow.asStateFlow
13: import kotlinx.coroutines.launch
14: 
15: /**
16:  * The lifecycle of one scan. `CANCELLED` and `PARTIAL` are separate on purpose: only one of them
17:  * means "we looked and the device did not answer".
18:  */
19: enum class AtlasScanStatus { IDLE, RUNNING, COMPLETED, PARTIAL, CANCELLED, DENIED, UNAVAILABLE }
20: 
21: /**
22:  * The candidate pass: a caller that can turn still-unresolved domains into extra features.
23:  *
24:  * A function type rather than an interface so a scan can be given one inline, and suspending because
25:  * expanding a bank means listing approved roots through the same bounded access the scan already holds.
26:  * It returns features to *attempt*, never conclusions: what a candidate interface means is decided by
27:  * the same resolution stages as everything else.
28:  */
29: typealias AtlasCompletion = suspend (Set<AtlasDomain>) -> List<AtlasFeatureRequest>
30: 
31: /** One feature to resolve, with the volatility that decides how long its evidence stays usable. */
32: data class AtlasFeatureRequest(
33:     val request: AtlasProbeRequest,
34:     val volatility: AtlasVolatility,
35: )
36: 
37: /**
38:  * An immutable snapshot of what the scan knows (`T5.2`).
39:  *
40:  * Published from a repository rather than assembled in a composable, so a recomposition cannot start
41:  * work and a screen cannot invent progress. Nothing here is derived from a timer: [percent] comes from
42:  * feature counts alone, and is `null` when the total is unknown instead of a made-up zero.
43:  */
44: data class AtlasScanState(
45:     val status: AtlasScanStatus = AtlasScanStatus.IDLE,
46:     val generation: Long = 0L,
47:     /** How many features this scan intends to resolve. Zero means "unknown", not "none". */
48:     val totalFeatures: Int = 0,
49:     val outcomes: List<AtlasFeatureOutcome> = emptyList(),
50:     val attempts: Int = 0,
51:     val cacheHits: Int = 0,
52:     val suppressed: Int = 0,
53:     val message: String? = null,
54: ) {
55: 
56: 
57:     val observed: List<AtlasFeatureOutcome.Observed> get() = outcomes.filterIsInstance<AtlasFeatureOutcome.Observed>()
58:     val unresolved: List<AtlasFeatureOutcome.Unresolved> get() = outcomes.filterIsInstance<AtlasFeatureOutcome.Unresolved>()
59: 
60:     /** True while a scan is in flight. */
61:     val isRunning: Boolean get() = status == AtlasScanStatus.RUNNING
62: 
63:     /**
64:      * Progress over *features*, or `null` when the total is unknown.
65:      *
66:      * There is no timer anywhere in this computation: a bar that creeps forward while nothing is being
67:      * read would be the fake progress this project refuses. Four of five features answered is 80%;
68:      * the same four with no known total is `null`, which the UI must render as unknown rather than 0.
69:      */
70:     val percent: Int? =
71:         if (totalFeatures <= 0) null
72:         else ((outcomes.size.toLong() * 100L) / totalFeatures.toLong()).toInt().coerceIn(0, 100)
73: 
74:     /** Features still to be resolved. */
75:     val remaining: Int get() = (totalFeatures - outcomes.size).coerceAtLeast(0)
76: 
77:     /**
78:      * Unresolved interfaces that a **reviewed** entry addressed.
79:      *
80:      * Kept apart from the candidate pass on purpose: a candidate interface is a name the community
81:      * knows, so it being missing on this device is the expected case and not a finding. A reviewed
82:      * interface that did not answer is the event a maintainer can act on.
83:      */
84:     val reviewedUnresolved: List<AtlasFeatureOutcome.Unresolved>
85:         get() = unresolved.filter { it.stage != AtlasStage.CANDIDATE_INTERFACE }
86: 
87:     /** Outcomes that came from the community bank, so a screen can count them without re-deriving it. */
88:     val candidateOutcomes: List<AtlasFeatureOutcome> get() = outcomes.filter { outcomeStage(it) == AtlasStage.CANDIDATE_INTERFACE }
89: 
90:     /**
91:      * Whether a report may be suggested yet (`T7.6`).
92:      *
93:      * A cancelled or still-running scan never qualifies: suggesting that the user send a diagnostic
94:      * report while the run is incomplete would misrepresent what the report contains. A finished scan
95:      * whose reviewed interfaces all answered qualifies for nothing either — there is nothing to report
96:      * about a device that answered everything it was asked. A candidate name that was not there does
97:      * not qualify on its own: that is what the second line of defense is expected to find.
98:      */
99:     val reportEligible: Boolean
100:         get() = (status == AtlasScanStatus.COMPLETED || status == AtlasScanStatus.PARTIAL ||
101:             status == AtlasScanStatus.DENIED || status == AtlasScanStatus.UNAVAILABLE) &&
102:             reviewedUnresolved.isNotEmpty()
103: 
104:     private fun outcomeStage(outcome: AtlasFeatureOutcome): AtlasStage = when (outcome) {
105:         is AtlasFeatureOutcome.Observed -> outcome.stage
106:         is AtlasFeatureOutcome.Unresolved -> outcome.stage
107:         is AtlasFeatureOutcome.Suppressed -> AtlasStage.REVIEWED_KNOWLEDGE
108:         is AtlasFeatureOutcome.Cancelled -> AtlasStage.BOUNDED_DISCOVERY
109:     }
110: }
111: 
112: /**
113:  * One coalesced scan, exposed as immutable state (`T5.2`, `T5.6`).
114:  *
115:  * Design points that are load-bearing:
116:  *
117:  * - **Constructors do not scan.** Creating this object reads nothing; a scan starts only when a caller
118:  *   asks for one, so a dependency-injection graph cannot perform I/O on the main thread.
119:  * - **One scan at a time.** A second `start` while one is running is refused and the caller keeps
120:  *   observing the same state, so two screens cannot double the read budget.
121:  * - **Cancellation is published immediately and outranks a late completion.** A result that arrives
122:  *   after the user left the screen must not overwrite `CANCELLED` with `COMPLETED`.
123:  * - **Failure memory is used, not re-derived.** A suppressed path never reaches the device, and the
124:  *   ledger stops remembering a path the moment it answers.
125:  */
126: class AtlasRepository(
127:     private val store: AtlasEvidenceStore,
128:     private val resolver: AtlasResolver,
129:     private val ledger: AtlasFailureLedger,
130:     private val identity: AtlasDeviceIdentity,
131:     private val catalog: AtlasCatalog,
132:     dispatcher: CoroutineDispatcher,
133:     private val bootGeneration: () -> Long = { 0L },
134:     private val privilegeGeneration: () -> Long = { 0L },
135: ) {
136: 
137:     private val scope = CoroutineScope(SupervisorJob() + dispatcher)
138: 
139:     private val _state = MutableStateFlow(AtlasScanState())
140:     val state: StateFlow<AtlasScanState> = _state.asStateFlow()
141: 
142:     private var scan: Job? = null
143:     private var generationCounter: Long = 0L
144: 
145:     /** The context fingerprint the current scan is keyed to. Private, and never reported. */
146:     fun fingerprint(): String = store.fingerprint(
147:         catalogVersion = catalog.version,
148:         identity = identity,
149:         bootGeneration = bootGeneration(),
150:         privilegeGeneration = privilegeGeneration(),
151:     )
152: 
153:     /**
154:      * Starts a scan, or refuses when one is already running.
155:      *
156:      * Returns true when this call started the scan and false when it coalesced into the running one.
157:      */
158:     fun start(
159:         features: List<AtlasFeatureRequest>,
160:         /**
161:          * The second line of defense. `null` means this scan asks only the first bank.
162:          *
163:          * Declared **before** [discover] on purpose: the discovery function is the one callers write as a
164:          * trailing lambda, and a parameter added after it would silently become the lambda's target at
165:          * every existing call site instead of failing to compile.
166:          */
167:         complete: AtlasCompletion? = null,
168:         discover: suspend (AtlasProbeRequest) -> AtlasReadResult,
169:     ): Boolean {
170:         if (scan?.isActive == true) return false
171:         val generation = generationCounter + 1
172:         generationCounter = generation
173:         val fingerprint = fingerprint()
174:         _state.value = AtlasScanState(
175:             status = AtlasScanStatus.RUNNING,
176:             generation = generation,
177:             totalFeatures = features.size,
178:         )
179:         scan = scope.launch {
180:             runScan(features, fingerprint, generation, discover, complete)
181:         }
182: 
183:         return true
184:     }
185: 
186:     /**
187:      * Cancels the running scan. The state says `CANCELLED` from this moment, and a result that arrives
188:      * later cannot change it — that is the difference between "the user left" and "the device has
189:      * nothing", and only one of those belongs in a report.
190:      */
191:     fun cancel(reason: String = "cancelled before the scan finished") {
192:         val running = scan
193:         if (running?.isActive == true) {
194:             running.cancel(CancellationException(reason))
195:             _state.value = _state.value.copy(status = AtlasScanStatus.CANCELLED, message = reason)
196:         }
197:     }
198: 
199:     /** A retry is a new scan: it reuses held evidence and re-attempts only what is stale or unknown. */
200:     fun retry(
201:         features: List<AtlasFeatureRequest>,
202:         complete: AtlasCompletion? = null,
203:         discover: suspend (AtlasProbeRequest) -> AtlasReadResult,
204:     ): Boolean = start(features, complete, discover)
205: 
206:     /** Drops every cached observation. The next scan reads the device again. */
207:     fun invalidate(): Int {
208:         val removed = store.clear()
209:         _state.value = AtlasScanState()
210:         return removed
211:     }
212: 
213:     private suspend fun runScan(
214:         features: List<AtlasFeatureRequest>,
215:         fingerprint: String,
216:         generation: Long,
217:         discover: suspend (AtlasProbeRequest) -> AtlasReadResult,
218:         complete: AtlasCompletion?,
219:     ) {
220:         val outcomes = mutableListOf<AtlasFeatureOutcome>()
221:         var attempts = 0
222:         var hits = 0
223:         var suppressed = 0
224:         var total = features.size
225:         try {
226:             features.forEach { feature ->
227:                 // Checked before every feature: without this a cancelled job would keep reading the
228:                 // device until the whole list finished.
229:                 currentCoroutineContext().ensureActive()
230:                 val suppression = ledger.suppressionFor(feature.request.path)
231:                 if (suppression != null) suppressed += 1
232:                 val outcome = resolver.resolve(
233:                     request = feature.request,
234:                     volatility = feature.volatility,
235:                     fingerprint = fingerprint,
236:                     suppressedBecause = suppression,
237:                     discover = discover,
238:                 )
239:                 outcomes += outcome
240:                 when (outcome) {
241:                     is AtlasFeatureOutcome.Observed -> {
242:                         if (outcome.fromCache) hits += 1 else attempts += 1
243:                         ledger.noteSuccess(feature.request.path)
244:                     }
245: 
246:                     is AtlasFeatureOutcome.Unresolved -> {
247:                         attempts += 1
248:                         ledger.record(feature.request.path, outcome.failure)
249:                     }
250: 
251:                     else -> Unit
252:                 }
253:                 publish(outcomes, attempts, hits, suppressed, generation, total, AtlasScanStatus.RUNNING, null)
254:             }
255:             currentCoroutineContext().ensureActive()
256: 
257:             // ---- the second line of defense ------------------------------------------------------
258:             // Only the domains the first bank could not resolve are asked about, and only interfaces
259:             // no attempt has addressed yet. This is completion, not a second opinion: a reviewed answer
260:             // is never re-asked, and a candidate answer never overwrites one.
261:             if (complete != null) {
262:                 val unresolvedDomains = unresolvedDomains(features, outcomes)
263:                 if (unresolvedDomains.isNotEmpty()) {
264:                     val candidates = complete(unresolvedDomains)
265:                     total += candidates.size
266:                     publish(outcomes, attempts, hits, suppressed, generation, total, AtlasScanStatus.RUNNING, null)
267:                     candidates.forEach { feature ->
268:                         currentCoroutineContext().ensureActive()
269:                         val suppression = ledger.suppressionFor(feature.request.path)
270:                         if (suppression != null) suppressed += 1
271:                         val outcome = resolver.resolve(
272:                             request = feature.request,
273:                             volatility = feature.volatility,
274:                             fingerprint = fingerprint,
275:                             suppressedBecause = suppression,
276:                             stage = AtlasStage.CANDIDATE_INTERFACE,
277:                             discover = discover,
278:                         )
279:                         outcomes += outcome
280:                         when (outcome) {
281:                             is AtlasFeatureOutcome.Observed -> {
282:                                 if (outcome.fromCache) hits += 1 else attempts += 1
283:                                 ledger.noteSuccess(feature.request.path)
284:                             }
285: 
286:                             is AtlasFeatureOutcome.Unresolved -> {
287:                                 attempts += 1
288:                                 ledger.record(feature.request.path, outcome.failure)
289:                             }
290: 
291:                             else -> Unit
292:                         }
293:                         publish(outcomes, attempts, hits, suppressed, generation, total, AtlasScanStatus.RUNNING, null)
294:                     }
295:                 }
296:             }
297: 
298:             currentCoroutineContext().ensureActive()
299:             publish(
300:                 outcomes = outcomes,

(Showing lines 1-300 of 366. Use offset=301 to continue.)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasDiscovery.kt",
  "limit": 280
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasDiscovery.kt</path>
<type>file</type>
<content>
1: package nd.max.core.atlas
2: 
3: import nd.max.core.hardware.AtlasReadBudget
4: import nd.max.core.hardware.AtlasReadTransport
5: import nd.max.core.hardware.ReadOnlyProbeAccess
6: 
7: /**
8:  * What one bounded discovery job is allowed to do (`P2` + `P5`).
9:  *
10:  * One instance serves one scan. It owns the [ReadOnlyProbeAccess] counters — operations, entries,
11:  * bytes, limits reached — so a report can say a limit stopped it rather than implying the device had
12:  * nothing to say. A new scan gets a new job, which is what makes the budgets per-scan rather than
13:  * cumulative over the process lifetime.
14:  */
15: class AtlasJob(
16:     internal val access: ReadOnlyProbeAccess,
17:     private val plan: AtlasProbePlan,
18: ) {
19:     /** The probes this job intends to run, cheapest first. */
20:     val due: List<String> get() = plan.due.map { it.id }
21: 
22:     /** Why a feature will not be attempted, per probe id. */
23:     fun skipReason(id: String): String? = plan.reasonFor(id)
24: 
25:     fun discover(request: AtlasProbeRequest): AtlasReadResult = access.read(request)
26: 
27:     /**
28:      * Names directly under an approved root, bounded by the same budget as every other attempt.
29:      *
30:      * This is the only way an interface that lives one level down can be addressed at all, and the only
31:      * way absence can ever be claimed: a name that was in a successful listing is present, and a name
32:      * that was not is absent — anything else stays unknown. A failed listing is returned as a failure
33:      * and never as an empty device.
34:      */
35:     fun childrenOf(root: String): AtlasDirectoryListing = access.list(root)
36: 
37:     /** True when any bound (time, entries, bytes, operations) actually stopped this job. */
38:     fun limitsReached(): Boolean = access.stats().limitsReached
39: 
40:     fun stats() = access.stats()
41: }
42: 
43: /**
44:  * A catalog together with **how its entries must be read**.
45:  *
46:  * The same review rules apply to every bank, but the strength of the claim does not: an entry from the
47:  * reviewed set is `REVIEWED_MATCH`, an entry from the community bank is `INFERRED` — a name known to
48:  * exist, not a meaning established. Keeping that in one place is what stops a bank from silently
49:  * upgrading its own evidence.
50:  */
51: data class AtlasSourceCatalog(
52:     val catalog: AtlasCatalog,
53:     val providerId: String,
54:     val semantic: AtlasSemanticStatus,
55: )
56: 
57: /**
58:  * Turns the reviewed catalog into work, and work into evidence.
59:  *
60:  * This is the piece that makes the difference between a designed library and a feature: without it
61:  * nothing in the app ever calls the transport, which is exactly the state the gap review found. Five
62:  * decisions live here:
63:  *
64:  * 1. **Two shapes, both concrete.** A root-level entry addresses `parentRoot/attribute`; an enumerated
65:  *    entry addresses `parentRoot/<child>/attribute` for each child the device actually listed. Twelve
66:  *    reviewed entries were written in the wrong shape before this was a grammar, and every one of them
67:  *    named a path that cannot exist while the interface it describes is present.
68:  * 2. **Only addressable entries become features.** The catalog's own validation already refuses an
69:  *    entry pinned to an unapproved root, and this class asks the same question again before building a
70:  *    request (`AtlasAnchors.isAddressable`, `AtlasAnchors.isEnumerable`), so a mis-edited catalog cannot
71:  *    reach the transport.
72:  * 3. **The reviewed `/proc` allowance is attached to the entry that earned it**, not decided at read
73:  *    time by a path prefix. A caller cannot inflate its own byte budget by naming a path.
74:  * 4. **A bank is consulted for the domains the previous one could not resolve**, and never for an
75:  *    interface that was already attempted: the second line of defense is coverage, not a second opinion.
76:  * 5. **A backend's parsed evidence is not a probe.** CPU/GPU facts come from the existing parsers
77:  *    through [AtlasBackendProvider]; requesting a *catalog path* for the same interface would read the
78:  *    kernel a second time for the same answer.
79:  */
80: class AtlasDiscovery(
81:     private val sources: List<AtlasSourceCatalog> = listOf(REVIEWED),
82:     private val identity: AtlasDeviceIdentity,
83:     private val transport: AtlasReadTransport,
84:     private val budget: AtlasReadBudget = AtlasReadBudget.DEFAULT,
85:     private val clockMs: () -> Long,
86:     private val bootGeneration: () -> Long = { 0L },
87:     private val privilegeGeneration: () -> Long = { 0L },
88: ) {
89: 
90:     /** The reviewed bank: the first line of defense. */
91:     val catalog: AtlasCatalog get() = sources.first().catalog
92: 
93:     /**
94:      * Vendor tags this device declares, resolved once.
95:      *
96:      * A vendor-tagged interface is only ever attempted on a device that claims that vendor: an entry
97:      * for MediaTek's `/proc/gpufreq` read on a Qualcomm phone answers nothing and costs a read, and a
98:      * catalog that ignores the tag turns "not your vendor" into noise indistinguishable from "missing".
99:      */
100:     private val deviceTags: Set<String> by lazy { identity.vendorHints() }
101: 
102:     /** Whether an attempt that needs privilege may proceed: never, without an authorized transport. */
103:     var privilegeAvailable: () -> Boolean = { false }
104: 
105:     /**
106:      * Every **root-level** feature of one bank this device context may address.
107:      *
108:      * Enumerated entries are not here on purpose: they need a job to be listed before they can be
109:      * addressed, so they are produced by [enumeratedFeatures] with that job. The first pass calls this
110:      * for [REVIEWED] only; the candidate pass calls it for [COMMUNITY] with [domains] narrowed to what
111:      * the first pass could not resolve.
112:      */
113:     fun features(
114:         source: AtlasSourceCatalog = sources.first(),
115:         domains: Set<AtlasDomain>? = null,
116:         volatilityOf: (AtlasCatalogEntry) -> AtlasVolatility = AtlasResolver::volatilityFor,
117:     ): List<AtlasFeatureRequest> = source.catalog.entries
118:         .filter { it.scope == AtlasCatalogScope.ROOT_FILE }
119:         .filter { it.safety == AtlasSafetyClass.READ_ONLY }
120:         .filter { domains == null || it.domain in domains }
121:         .filter { it.provider != AtlasProviderKind.VENDOR || it.vendorTags.any(deviceTags::contains) }
122:         .filter { AtlasAnchors.isAddressable(it.parentRoot, it.attribute) }
123:         .map { entry ->
124:             AtlasFeatureRequest(
125:                 request = requestFor(entry, source = source),
126:                 volatility = volatilityOf(entry),
127:             )
128:         }
129: 
130:     /** Paths a bank's root-level features address, so a later bank cannot read the same file twice. */
131:     fun rootPaths(source: AtlasSourceCatalog = sources.first()): Set<String> =
132:         features(source).map { it.request.path }.toSet()
133: 
134:     /**
135:      * Reviewed and candidate features whose attribute lives inside an enumerated child.
136:      *
137:      * Every returned request names a path the device itself produced (or a child that was listed and
138:      * whose attribute was not — in which case the read fails honestly). Nothing here guesses a device
139:      * name: `zram0` exists because a listing said so, not because a table said most devices have it.
140:      *
141:      * @param catalogs the banks to expand, in order. The first bank's entries are attempted first.
142:      * @param attemptedPaths paths already attempted in this scan, so no interface is read twice.
143:      * @param domains when non-null, only entries in these domains are expanded (the candidate pass).
144:      * @param limit the hard cap on returned features. A bank is a body of knowledge, not a budget.
145:      */
146:     fun enumeratedFeatures(
147:         job: AtlasJob,
148:         catalogs: List<AtlasSourceCatalog> = sources,
149:         attemptedPaths: Set<String> = emptySet(),
150:         domains: Set<AtlasDomain>? = null,
151:         limit: Int = DEFAULT_ENUMERATED_LIMIT,
152:     ): List<AtlasFeatureRequest> {
153:         val chosen = LinkedHashMap<String, AtlasFeatureRequest>()
154:         val listings = mutableMapOf<String, AtlasDirectoryListing>()
155: 
156:         catalogs.forEach { source ->
157:             source.catalog.entries
158:                 .filter { it.scope == AtlasCatalogScope.CHILD_FILE }
159:                 .filter { it.safety == AtlasSafetyClass.READ_ONLY }
160:                 .filter { domains == null || it.domain in domains }
161:                 .filter { it.provider != AtlasProviderKind.VENDOR || it.vendorTags.any(deviceTags::contains) }
162:                 .sortedWith(compareBy({ AtlasCatalog.providerRank(it.provider) }, { it.id }))
163:                 .forEach { entry ->
164:                     if (chosen.size >= limit) return@forEach
165:                     if (!AtlasAnchors.isEnumerable(entry.parentRoot)) return@forEach
166:                     val listing = listings.getOrPut(entry.parentRoot) { job.childrenOf(entry.parentRoot) }
167:                     // A root that could not be listed yields no features and no claim: the caller sees
168:                     // the failure in the job's own stats, and nothing is invented in its place.
169:                     if (listing.failure != AtlasFailure.NONE) return@forEach
170:                     listing.names
171:                         .filter { entry.childPrefix == null || it.startsWith(entry.childPrefix) }
172:                         .forEach { child ->
173:                             if (chosen.size >= limit) return@forEach
174:                             val path = "${entry.parentRoot}/$child/${entry.attribute}"
175:                             if (path in attemptedPaths || chosen.containsKey(path)) return@forEach
176:                             val id = perChildId(entry.id, child) ?: return@forEach
177:                             chosen[path] = AtlasFeatureRequest(
178:                                 request = requestFor(entry, source = source, child = child, id = id),
179:                                 volatility = AtlasResolver.volatilityForDomain(entry.domain),
180:                             )
181:                         }
182:                 }
183:         }
184:         return chosen.values.toList()
185:     }
186: 
187:     /** One catalog entry as a probe request. The reviewed `/proc` allowance follows the entry. */
188:     fun requestFor(
189:         entry: AtlasCatalogEntry,
190:         source: AtlasSourceCatalog = sources.first(),
191:         child: String? = null,
192:         id: String = entry.id,
193:     ): AtlasProbeRequest = AtlasProbeRequest(
194:         id = id,
195:         domain = entry.domain,
196:         providerId = source.providerId,
197:         catalogVersion = source.catalog.version,
198:         sourceId = entry.provenance.sourceId,
199:         path = pathFor(entry, child),
200:         unit = entry.unit,
201:         semanticStatus = source.semantic,
202:         requiresPrivilege = false,
203:         reviewedProcSummary = entry.provenance.sourceId in REVIEWED_PROC_SOURCES,
204:     )
205: 
206:     /** The concrete path an entry addresses, given the child it was enumerated under. */
207:     fun pathFor(entry: AtlasCatalogEntry, child: String?): String = when (entry.scope) {
208:         AtlasCatalogScope.ROOT_FILE -> "${entry.parentRoot}/${entry.attribute}"
209:         AtlasCatalogScope.CHILD_FILE -> requireNotNull(child) {
210:             "an enumerated entry addresses one child at a time: ${entry.id}"
211:         }.let { "${entry.parentRoot}/$it/${entry.attribute}" }
212:     }
213: 
214:     /** Opens a fresh bounded job for the given features. Constructors here perform no I/O. */
215:     fun newJob(features: List<AtlasFeatureRequest>): AtlasJob {
216:         val access = ReadOnlyProbeAccess(
217:             transport = transport,
218:             budget = budget,
219:             clockMs = clockMs,
220:             privilegeAvailable = { privilegeAvailable() },
221:             currentGeneration = { bootGeneration() },
222:         )
223:         return AtlasJob(access, planFor(features))
224:     }
225: 
226:     /**
227:      * Rebuilds a job's probe plan after enumeration produced more features than it was created with.
228:      *
229:      * Without this the plan would describe only what was known before the device was listed, and the
230:      * job's own `due` list would contradict what the scan actually attempts.
231:      */
232:     fun replan(job: AtlasJob, features: List<AtlasFeatureRequest>): AtlasJob =
233:         AtlasJob(job.access, planFor(features))
234: 
235:     private fun planFor(features: List<AtlasFeatureRequest>): AtlasProbePlan =
236:         AtlasProbeScheduler(budget, clockMs).plan(
237:             probes = features.map { feature ->
238:                 AtlasScheduledProbe(
239:                     id = feature.request.id,
240:                     path = feature.request.path,
241:                     cost = costOf(feature),
242:                     freshness = null,
243:                 )
244:             },
245:             bootGeneration = bootGeneration(),
246:             privilegeGeneration = privilegeGeneration(),
247:         )
248: 
249:     private fun costOf(feature: AtlasFeatureRequest) = AtlasProbeCost(
250:         opens = 1,
251:         maxBytes = budget.bytesFor(feature.request),
252:     )
253: 
254:     companion object {
255: 
256:         /** The reviewed bank, as a source. Everything read from here is a reviewed match. */
257:         val REVIEWED: AtlasSourceCatalog = AtlasSourceCatalog(
258:             catalog = AtlasReviewedSeeds.catalog(),
259:             providerId = "reviewed-catalog",
260:             semantic = AtlasSemanticStatus.REVIEWED_MATCH,
261:         )
262: 
263:         /**
264:          * The community bank, as a source. Everything read from here is `INFERRED`: the interface name
265:          * is known, the meaning of its number is not established by review.
266:          */
267:         val COMMUNITY: AtlasSourceCatalog = AtlasSourceCatalog(
268:             catalog = AtlasCommunityBank.catalog(),
269:             providerId = "community-bank",
270:             semantic = AtlasSemanticStatus.INFERRED,
271:         )
272: 
273:         /** The default chain: reviewed knowledge first, accumulated vocabulary second. */
274:         fun defaultSources(): List<AtlasSourceCatalog> = listOf(REVIEWED, COMMUNITY)
275: 
276:         /**
277:          * How many enumerated features one scan may add.
278:          *
279:          * A design value, not a measurement: the transport budget already caps operations, bytes and
280:          * time, and this cap exists so a device with two hundred enumerated children cannot turn one

(Showing lines 1-280 of 317. Use offset=281 to continue.)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasResolver.kt",
  "limit": 220
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasResolver.kt</path>
<type>file</type>
<content>
1: package nd.max.core.atlas
2: 
3: import kotlinx.coroutines.CancellationException
4: 
5: /**
6:  * Which stage produced an outcome (`P5`, slice `E`).
7:  *
8:  * The order is the product rule: reviewed knowledge and already-held evidence first, bounded
9:  * discovery second, and an explicit unresolved result before anything is suggested to the user.
10:  */
11: enum class AtlasStage {
12:     /** Answered from the catalog and the evidence store, without touching the device. */
13:     REVIEWED_KNOWLEDGE,
14: 
15:     /** Answered by one bounded read attempt on a reviewed catalog interface. */
16:     BOUNDED_DISCOVERY,
17: 
18:     /**
19:      * Answered by the community bank, for a domain the reviewed catalog could not resolve.
20:      *
21:      * It is its own stage rather than a flavour of discovery because it is weaker evidence: a reviewed
22:      * interface says "this is what has been reviewed to mean", a candidate interface says "this name is
23:      * known to exist". A reader that cannot tell the two apart would eventually treat a candidate name
24:      * as a reviewed fact, which is exactly the confusion this stage exists to prevent.
25:      */
26:     CANDIDATE_INTERFACE,
27: }
28: 
29: /**
30:  * One feature's resolution. Every state is distinguishable on purpose: a cancelled job and an
31:  * exhausted device look identical in a progress bar and mean completely different things to the
32:  * person reading it, and only one of them is worth a support report.
33:  */
34: sealed interface AtlasFeatureOutcome {
35:     val id: String
36: 
37:     data class Observed(
38:         override val id: String,
39:         val observation: AtlasObservation,
40:         val stage: AtlasStage,
41:         /** True when held evidence answered and nothing was read from the device this time. */
42:         val fromCache: Boolean,
43:         val cacheMiss: AtlasCacheMiss? = null,
44:     ) : AtlasFeatureOutcome
45: 
46:     data class Unresolved(
47:         override val id: String,
48:         val failure: AtlasFailure,
49:         val reason: String,
50:         val stage: AtlasStage,
51:         val cacheMiss: AtlasCacheMiss? = null,
52:     ) : AtlasFeatureOutcome
53: 
54:     /** Skipped before any attempt because a recent attempt failed and a retry could not change it. */
55:     data class Suppressed(
56:         override val id: String,
57:         val cause: AtlasFailure,
58:         val reason: String,
59:     ) : AtlasFeatureOutcome
60: 
61:     /**
62:      * The job was cancelled. Deliberately not a failure: no conclusion about the device follows from
63:      * the user leaving the screen, so this must never be reported as exhaustion.
64:      */
65:     data class Cancelled(
66:         override val id: String,
67:         val reason: String,
68:     ) : AtlasFeatureOutcome
69: }
70: 
71: /**
72:  * Ordered per-feature resolution (`T5.1`, `T5.4`).
73:  *
74:  * Two rules decide the whole design:
75:  *
76:  * 1. **A feature that is already answered is not asked again.** A fresh cached observation returns
77:  *    immediately, so a second scan of the same list performs zero reads — the property that makes a
78:  *    bounded budget meaningful.
79:  * 2. **Cancellation is not a cause.** `CancellationException` is rethrown, never converted into
80:  *    `Unresolved`: a cancelled job is a fact about the job, not about the device. Everything else a
81:  *    discovery stage can throw becomes `UNKNOWN_CAUSE` with a reason, because that is what it is —
82:  *    an error we did not recognize — rather than `ABSENT`, which would be a claim we cannot support.
83:  */
84: class AtlasResolver(
85:     private val store: AtlasEvidenceStore,
86:     private val clockMs: () -> Long,
87:     private val bootGeneration: () -> Long = { 0L },
88:     private val privilegeGeneration: () -> Long = { 0L },
89: ) {
90: 
91:     /** The freshness verdict of held evidence, exposed so a caller can explain a re-read. */
92:     fun heldStaleness(observationId: String, fingerprint: String): AtlasStaleness? {
93:         val lookup = store.load(observationId, fingerprint)
94:         if (lookup !is AtlasCacheLookup.Hit) return null
95:         return lookup.evidence.freshnessAt().stalenessAt(clockMs(), bootGeneration(), privilegeGeneration())
96:     }
97: 
98:     suspend fun resolve(
99:         request: AtlasProbeRequest,
100:         volatility: AtlasVolatility,
101:         fingerprint: String,
102:         /** Recorded so a caller can say "we already knew this" without re-deriving the decision. */
103:         suppressedBecause: AtlasSuppression? = null,
104:         /**
105:          * Which pass produced this attempt. Defaulted so every existing caller keeps its meaning, and
106:          * carried into the outcome because "a candidate name was not there" and "a reviewed interface
107:          * failed" must never be counted as the same event.
108:          */
109:         stage: AtlasStage = AtlasStage.BOUNDED_DISCOVERY,
110:         discover: suspend (AtlasProbeRequest) -> AtlasReadResult,
111:     ): AtlasFeatureOutcome {
112:         if (suppressedBecause != null) {
113:             return AtlasFeatureOutcome.Suppressed(
114:                 id = request.id,
115:                 cause = suppressedBecause.cause,
116:                 reason = suppressedBecause.reason,
117:             )
118:         }
119: 
120:         // ---- stage 1: reviewed knowledge and held evidence ------------------------------------------
121:         val lookup = store.load(request.id, fingerprint)
122:         val miss = (lookup as? AtlasCacheLookup.Miss)?.reason
123:         if (lookup is AtlasCacheLookup.Hit) {
124:             val verdict = lookup.evidence.freshnessAt()
125:                 .stalenessAt(clockMs(), bootGeneration(), privilegeGeneration())
126:             if (verdict == AtlasStaleness.FRESH) {
127:                 return AtlasFeatureOutcome.Observed(
128:                     id = request.id,
129:                     observation = lookup.evidence.observation,
130:                     // Held evidence answers in the reviewed-knowledge stage whatever bank asked for it:
131:                     // what is being reported is "this is what we already held", and that is the same
132:                     // fact for a candidate interface as for a reviewed one.
133:                     stage = AtlasStage.REVIEWED_KNOWLEDGE,
134:                     fromCache = true,
135:                 )
136:             }
137:         }
138: 
139:         // ---- stage 2: bounded discovery ------------------------------------------------------------
140:         // Stage 3 (candidate interfaces) is not decided here: it is a per-scan decision made after the
141:         // reviewed pass, because "which domains are still unresolved" is only known when the pass ends.
142:         val result = try {
143:             discover(request)
144:         } catch (cancelled: CancellationException) {
145:             // Rethrown on purpose: see the class documentation. A cancellation the caller cannot
146:             // distinguish from exhaustion is a lie by omission.
147:             throw cancelled
148:         } catch (error: Exception) {
149:             AtlasReadResult.Rejected(
150:                 path = request.path,
151:                 failure = AtlasFailure.UNKNOWN_CAUSE,
152:                 reason = "the discovery stage threw ${error.javaClass.simpleName} before it could answer",
153:             )
154:         }
155: 
156:         return when (result) {
157:             is AtlasReadResult.Observed -> {
158:                 // A write failure here loses speed, never truth: the observation is returned either way.
159:                 store.save(result.observation, volatility, fingerprint)
160:                 AtlasFeatureOutcome.Observed(
161:                     id = request.id,
162:                     observation = result.observation,
163:                     stage = stage,
164:                     fromCache = false,
165:                     cacheMiss = miss,
166:                 )
167:             }
168: 
169:             is AtlasReadResult.Rejected -> AtlasFeatureOutcome.Unresolved(
170:                 id = request.id,
171:                 failure = result.failure,
172:                 reason = result.reason,
173:                 stage = stage,
174:                 cacheMiss = miss,
175:             )
176:         }
177:     }
178: 
179:     companion object {
180:         /** How one catalogue entry's volatility is chosen, in one place. */
181:         fun volatilityFor(entry: AtlasCatalogEntry): AtlasVolatility = volatilityForDomain(entry.domain)
182: 
183:         /**
184:          * The same rule, for an interface that is not a catalog entry (a candidate interface).
185:          *
186:          * It exists so a second copy of the table cannot appear: a candidate reading's lifetime must be
187:          * the same as the reviewed reading of the same domain, or the cache would hold one and evict the
188:          * other for a reason nobody could explain.
189:          */
190:         fun volatilityForDomain(domain: AtlasDomain): AtlasVolatility = when (domain) {
191:             AtlasDomain.CPU, AtlasDomain.GPU, AtlasDomain.THERMAL, AtlasDomain.MEMORY -> AtlasVolatility.INSTANT
192:             AtlasDomain.POWER -> AtlasVolatility.FAST
193:             AtlasDomain.STORAGE -> AtlasVolatility.SLOW
194:             // Identity and capability: bound by the boot and the privilege generation, not by a clock.
195:             AtlasDomain.DISPLAY, AtlasDomain.SENSOR, AtlasDomain.NETWORK, AtlasDomain.PRIVILEGE ->
196:                 AtlasVolatility.STATIC
197:         }
198:     }
199: }

(End of file - total 199 lines)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 16.1s)

_Thinking:_

**Evaluating scope generation**

I’m considering the foundational aspects of scope generation. There seems to be a challenge where I can focus on large fixes but can’t find a new path for automatic repairs that are incomplete. I need to get the user to accept an incremental approach to finalize things. It could be beneficial to finish a robust pipeline or explore a fallback option with existing systems. An agent will be needed soon to assist with this process!

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/ReadOnlyProbeAccess.kt",
  "offset": 160,
  "limit": 350
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/ReadOnlyProbeAccess.kt</path>
<type>file</type>
<content>
160:     override fun readText(path: String, maxBytes: Int): AtlasTransportRead =
161:         AtlasTransportRead.Failed(AtlasFailure.BACKEND_UNAVAILABLE, REASON)
162: 
163:     override fun listNames(path: String, limit: Int): AtlasTransportList =
164:         AtlasTransportList(emptyList(), AtlasFailure.BACKEND_UNAVAILABLE, REASON)
165: 
166:     override fun canonicalPath(path: String, maxHops: Int): String? = null
167: }
168: 
169: /**
170:  * The bounded, read-only probe boundary (`P2`).
171:  *
172:  * Everything that can turn a failed look into a false statement about the device is decided here,
173:  * not in an adapter:
174:  *
175:  * - **A cause is never invented.** A transport failure keeps the cause it reported.
176:  * - **Absence is proven or it is not claimed.** `ABSENT` is only ever returned for a path whose
177:  *   parent *this instance successfully enumerated* and which was not in that listing. Anything else
178:  *   is `UNKNOWN_CAUSE` — "I did not establish that it is missing" is not "it is missing".
179:  * - **Partial values never survive.** A truncated read is `BUDGET_EXCEEDED`; the text is dropped
180:  *   rather than parsed.
181:  * - **Budgets are enforced before work, not after.** Operation, entry, byte and deadline bounds are
182:  *   checked before each attempt, so a runaway path stops instead of running to the end.
183:  * - **No prompt is ever raised.** A privileged request without an authorized transport is
184:  *   `BACKEND_UNAVAILABLE`.
185:  *
186:  * One instance serves one job; its counters start at construction and are reported by [stats].
187:  *
188:  * @param clockMs monotonic milliseconds. Injected so deadline boundaries are exact in tests.
189:  * @param privilegeAvailable consulted only when a request asks for privilege; never blocks, never asks.
190:  * @param approvedAnchor the anchor check, injectable so a test can prove the refusal path.
191:  * @param currentGeneration the generation a result must still match to be publishable.
192:  */
193: class ReadOnlyProbeAccess(
194:     private val transport: AtlasReadTransport = UnavailableAtlasReadTransport,
195:     private val budget: AtlasReadBudget = AtlasReadBudget.DEFAULT,
196:     private val clockMs: () -> Long,
197:     private val privilegeAvailable: () -> Boolean = { true },
198:     private val approvedAnchor: (String) -> Boolean = AtlasAnchors::isApproved,
199:     private val currentGeneration: () -> Long = { 0L },
200: ) {
201: 
202:     private val jobStartedAtMs: Long = clockMs()
203: 
204:     /** Parents this instance enumerated successfully: the only place absence can be proven from. */
205:     private val enumeratedParents: MutableSet<String> = mutableSetOf()
206: 
207:     /** In-flight attempts. Atomic because one job may be driven from more than one thread. */
208:     private val inFlight = AtomicInteger(0)
209:     private val privilegedInFlight = AtomicInteger(0)
210: 
211:     private var operations: Int = 0
212:     private var entriesVisited: Int = 0
213:     private var bytesRead: Int = 0
214:     private var limitsReached: Boolean = false
215: 
216:     /** One bounded scalar attempt. */
217:     fun read(request: AtlasProbeRequest): AtlasReadResult {
218:         val path = request.path
219: 
220:         if (!AtlasIds.isSafeAbsolutePath(path) || !AtlasIds.isSafeBasename(path.substringAfterLast('/'))) {
221:             return reject(path, AtlasFailure.MALFORMED, "refused before any transport call: $path is not a safe template")
222:         }
223:         if (AtlasAnchors.isPublicApiSurface(path)) {
224:             return reject(path, AtlasFailure.UNKNOWN_CAUSE, "a public-API surface is not a file path; not attempted")
225:         }
226:         if (!approvedAnchor(path)) {
227:             return reject(path, AtlasFailure.UNKNOWN_CAUSE, "outside the approved anchor roots; not attempted")
228:         }
229:         if (clockMs() - jobStartedAtMs > budget.jobDeadlineMs) {
230:             limitsReached = true
231:             return reject(path, AtlasFailure.BUDGET_EXCEEDED, "job deadline ${budget.jobDeadlineMs} ms exceeded")
232:         }
233:         if (operations >= budget.maxOperations) {
234:             limitsReached = true
235:             return reject(path, AtlasFailure.BUDGET_EXCEEDED, "operation budget ${budget.maxOperations} reached")
236:         }
237:         if (request.requiresPrivilege && !privilegeAvailable()) {
238:             return reject(path, AtlasFailure.BACKEND_UNAVAILABLE, "privileged read requested but no authorized transport exists")
239:         }
240: 
241:         tryEnter(request.requiresPrivilege)?.let { (cause, reason) ->
242:             limitsReached = true
243:             return reject(path, cause, reason)
244:         }
245: 
246:         val remaining = budget.maxAggregateBytes - bytesRead
247:         val limit = minOf(budget.bytesFor(request), remaining)
248:         if (limit <= 0) {
249:             limitsReached = true
250:             return reject(path, AtlasFailure.BUDGET_EXCEEDED, "aggregate byte budget ${budget.maxAggregateBytes} reached")
251:         }
252: 
253:         return try {
254:             performRead(request, limit)
255:         } finally {
256:             exit(request.requiresPrivilege)
257:         }
258:     }
259: 
260:     private fun performRead(request: AtlasProbeRequest, limit: Int): AtlasReadResult {
261:         val path = request.path
262:         operations += 1
263:         val deadline = clockMs() + budget.operationDeadlineMs
264:         val canonical = transport.canonicalPath(path, budget.maxSymlinkHops)
265:             ?: return reject(path, AtlasFailure.BACKEND_UNAVAILABLE, "path could not be resolved within ${budget.maxSymlinkHops} symlink hops")
266:         if (canonical != path) {
267:             if (!AtlasIds.isSafeAbsolutePath(canonical) || !approvedAnchor(canonical)) {
268:                 return reject(path, AtlasFailure.UNKNOWN_CAUSE, "resolved path escapes the approved anchors: $canonical")
269:             }
270:         }
271:         if (clockMs() > deadline) {
272:             limitsReached = true
273:             return reject(path, AtlasFailure.TIMED_OUT, "operation deadline ${budget.operationDeadlineMs} ms exceeded before the read")
274:         }
275: 
276:         return when (val attempt = transport.readText(canonical, limit)) {
277:             is AtlasTransportRead.Failed -> when (attempt.cause) {
278:                 AtlasFailure.ABSENT ->
279:                     if (absenceIsProven(path)) {
280:                         reject(path, AtlasFailure.ABSENT, attempt.reason)
281:                     } else {
282:                         reject(path, AtlasFailure.UNKNOWN_CAUSE, "absence not proven: ${parentOf(path)} was not enumerated")
283:                     }
284: 
285:                 else -> reject(path, attempt.cause, attempt.reason)
286:             }
287: 
288:             is AtlasTransportRead.Text -> {
289:                 if (clockMs() > deadline) {
290:                     limitsReached = true
291:                     return reject(path, AtlasFailure.TIMED_OUT, "operation deadline ${budget.operationDeadlineMs} ms exceeded")
292:                 }
293:                 if (attempt.truncated) {
294:                     limitsReached = true
295:                     return reject(path, AtlasFailure.BUDGET_EXCEEDED, "value exceeded $limit bytes; no partial parse")
296:                 }
297:                 bytesRead += attempt.text.toByteArray(Charsets.UTF_8).size
298:                 parse(request, attempt.text)
299:             }
300:         }
301:     }
302: 
303:     /**
304:      * One bounded enumeration.
305:      *
306:      * Names that are not safe basenames are dropped and the listing is marked truncated, so a caller
307:      * never sees an unsafe name and never mistakes dropped coverage for a complete one.
308:      */
309:     fun list(root: String): AtlasDirectoryListing {
310:         if (!AtlasIds.isSafeAbsolutePath(root)) {
311:             return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.MALFORMED, "refused: $root is not a safe template")
312:         }
313:         if (AtlasAnchors.isPublicApiSurface(root)) {
314:             return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.UNKNOWN_CAUSE, "a public-API surface is not a file path; not attempted")
315:         }
316:         if (!approvedAnchor(root)) {
317:             return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.UNKNOWN_CAUSE, "outside the approved anchor roots; not attempted")
318:         }
319:         if (clockMs() - jobStartedAtMs > budget.jobDeadlineMs) {
320:             limitsReached = true
321:             return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.BUDGET_EXCEEDED, "job deadline ${budget.jobDeadlineMs} ms exceeded")
322:         }
323:         if (operations >= budget.maxOperations) {
324:             limitsReached = true
325:             return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.BUDGET_EXCEEDED, "operation budget ${budget.maxOperations} reached")
326:         }
327: 
328:         tryEnter(requiresPrivilege = false)?.let { (cause, reason) ->
329:             limitsReached = true
330:             return AtlasDirectoryListing(root, emptyList(), false, cause, reason)
331:         }
332: 
333:         val limit = minOf(budget.maxEntriesPerRoot, budget.maxEntriesTotal - entriesVisited)
334:         if (limit <= 0) {
335:             limitsReached = true
336:             return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.BUDGET_EXCEEDED, "aggregate entry budget ${budget.maxEntriesTotal} reached")
337:         }
338: 
339:         return try {
340:             performList(root, limit)
341:         } finally {
342:             exit(requiresPrivilege = false)
343:         }
344:     }
345: 
346:     private fun performList(root: String, limit: Int): AtlasDirectoryListing {
347:         operations += 1
348:         val deadline = clockMs() + budget.operationDeadlineMs
349:         val listed = transport.listNames(root, limit)
350:         if (clockMs() > deadline) {
351:             limitsReached = true
352:             return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.TIMED_OUT, "operation deadline ${budget.operationDeadlineMs} ms exceeded")
353:         }
354:         if (listed.cause != AtlasFailure.NONE) {
355:             return AtlasDirectoryListing(root, emptyList(), false, listed.cause, listed.reason)
356:         }
357: 
358:         val distinct = listed.names.distinct()
359:         val safe = distinct.filter(AtlasIds::isSafeBasename).sorted()
360:         val dropped = distinct.size - safe.size
361:         entriesVisited += safe.size
362:         if (entriesVisited > budget.maxEntriesTotal) {
363:             limitsReached = true
364:             return AtlasDirectoryListing(root, emptyList(), false, AtlasFailure.BUDGET_EXCEEDED, "aggregate entry budget ${budget.maxEntriesTotal} exceeded")
365:         }
366: 
367:         // Only a listing that was actually read may license a later absence claim.
368:         enumeratedParents.add(root)
369: 
370:         val cutByTransport = listed.truncated || safe.size > limit
371:         val truncated = cutByTransport || dropped > 0
372:         if (truncated) limitsReached = true
373:         return when {
374:             dropped > 0 -> AtlasDirectoryListing(root, safe.take(limit), true, AtlasFailure.NONE, "dropped $dropped unsafe basenames")
375:             cutByTransport -> AtlasDirectoryListing(root, safe.take(limit), true, AtlasFailure.NONE, "truncated at $limit entries")
376:             else -> AtlasDirectoryListing(root, safe, false, AtlasFailure.NONE, "complete listing")
377:         }
378:     }
379: 
380:     /**
381:      * Admits an attempt, or refuses it because a cap is already held.
382:      *
383:      * The claim is made by **incrementing first and rolling back on refusal**. A check-then-enter
384:      * version is not atomic: several threads can all read `inFlight = 0`, all pass the check, and all
385:      * enter — the cap would hold for one thread at a time and fail exactly under the flood it exists
386:      * for. (That defect was real and was found by the threaded test, not by reading this code.)
387:      *
388:      * A stuck worker is answered with a refusal, never with another worker: queueing unbounded
389:      * replacements is how one slow kernel interface turns into a burst of parallel reads.
390:      */
391:     private fun tryEnter(requiresPrivilege: Boolean): Pair<AtlasFailure, String>? {
392:         val now = inFlight.incrementAndGet()
393:         if (now > budget.maxConcurrentReads) {
394:             inFlight.decrementAndGet()
395:             return AtlasFailure.BUDGET_EXCEEDED to
396:                 "concurrency cap ${budget.maxConcurrentReads} reached; refused instead of queued"
397:         }
398:         if (requiresPrivilege) {
399:             val privileged = privilegedInFlight.incrementAndGet()
400:             if (privileged > budget.maxConcurrentPrivileged) {
401:                 privilegedInFlight.decrementAndGet()
402:                 inFlight.decrementAndGet()
403:                 return AtlasFailure.BUDGET_EXCEEDED to
404:                     "privileged concurrency cap ${budget.maxConcurrentPrivileged} reached; refused instead of queued"
405:             }
406:         }
407:         return null
408:     }
409: 
410:     private fun exit(requiresPrivilege: Boolean) {
411:         if (requiresPrivilege) privilegedInFlight.decrementAndGet()
412:         inFlight.decrementAndGet()
413:     }
414: 
415:     /** A result may be published only from the generation that is still current. */
416:     fun isCurrent(generation: Long): Boolean = generation == currentGeneration()
417: 
418:     fun stats(): AtlasAccessStats = AtlasAccessStats(
419:         operations = operations,
420:         entriesVisited = entriesVisited,
421:         bytesRead = bytesRead,
422:         limitsReached = limitsReached,
423:         generation = currentGeneration(),
424:         inFlight = inFlight.get(),
425:     )
426: 
427:     private fun absenceIsProven(path: String): Boolean = parentOf(path) in enumeratedParents
428: 
429:     private fun parentOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")
430: 
431:     private fun reject(path: String, cause: AtlasFailure, reason: String): AtlasReadResult.Rejected =
432:         AtlasReadResult.Rejected(path, cause, reason)
433: 
434:     private fun parse(request: AtlasProbeRequest, raw: String): AtlasReadResult {
435:         if (request.unit == AtlasUnit.UNKNOWN) {
436:             return AtlasReadResult.Observed(observation(request, AtlasUnit.UNKNOWN, null, raw, raw))
437:         }
438:         val parsed = raw.trim().toDoubleOrNull()
439:             ?: return reject(request.path, AtlasFailure.MALFORMED, "value is not numeric for unit ${request.unit}")
440:         if (!parsed.isFinite()) {
441:             return reject(request.path, AtlasFailure.MALFORMED, "value is not a finite number")
442:         }
443:         return AtlasReadResult.Observed(observation(request, request.unit, parsed, null, raw))
444:     }
445: 
446:     private fun observation(
447:         request: AtlasProbeRequest,
448:         unit: AtlasUnit,
449:         value: Double?,
450:         textValue: String?,
451:         raw: String,
452:     ): AtlasObservation = AtlasObservation(
453:         id = request.id,
454:         domain = request.domain,
455:         providerId = request.providerId,
456:         catalogVersion = request.catalogVersion,
457:         sourceId = request.sourceId,
458:         path = request.path,
459:         access = AtlasAccess.READABLE,
460:         semanticStatus = request.semanticStatus,
461:         unit = unit,
462:         value = value,
463:         textValue = textValue,
464:         rawRepresentation = raw.take(512),
465:         failure = AtlasFailure.NONE,
466:         reason = "read",
467:         observedAtElapsedMs = clockMs(),
468:         bootGeneration = 0L,
469:         privilegeGeneration = currentGeneration(),
470:         truncated = false,
471:     )
472: }

(End of file - total 472 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/HardwareRepairExecutor.kt",
  "limit": 350
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/HardwareRepairExecutor.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: /** A mutation seam for Atlas; production uses [HardwareRepairExecutor], tests can replay route outcomes. */
4: interface AtlasRepairPort {
5:     fun execute(request: HardwareRepairRequest): HardwareRepairResult
6: }
7: 
8: /** The externally meaningful state of one route transaction. */
9: enum class HardwareRepairState {
10:     /**
11:      * The value was written and read back unchanged for the bounded confirmation window.
12:      *
13:      * **This is a fast confirmation, not a claim of sustained stability.** The default window is three
14:      * samples forty milliseconds apart — roughly eighty milliseconds — and a vendor writer that
15:      * republishes on a seconds-long timer cannot be observed inside it. The state was called
16:      * `VERIFIED_STABLE` before, which promised more than the measurement supports: a caller reading
17:      * "stable" would stop looking exactly when the interesting failure happens later.
18:      *
19:      * Sustained verification is owned by the caller's own drift loop, which re-reads on a much coarser
20:      * cadence (`AppMonitor.DRIFT_CHECK_INTERVAL_MS`, ten seconds) and is the only thing that can see a
21:      * periodic rewrite. `DRIFT_ROLLED_BACK` below is what that class of failure looks like when it
22:      * happens *inside* the window.
23:      */
24:     CONFIRMED_WINDOW,
25:     BLOCKED,
26:     APPLY_FAILED_ROLLED_BACK,
27: 
28:     /** An external writer took the value back inside the window and the baseline was restored. */
29:     DRIFT_ROLLED_BACK,
30:     ROLLBACK_UNVERIFIED,
31: }
32: 
33: data class HardwareRepairRequest(
34:     val routeId: String,
35:     val key: String,
36:     val owner: ControlOwnership.Owner,
37:     val token: String,
38:     val desired: String,
39:     val apply: (String) -> Boolean,
40:     val read: () -> String?,
41:     val restore: (String) -> Boolean,
42:     val baseline: String? = null,
43:     /**
44:      * حكم تلبية الطلب — `null` يعني التساوي الحرفي.
45:      *
46:      * يُمرَّر إلى المُحكِّم **وإلى نافذة التأكيد هنا** (`request.read()` مقارنًا بالمطلوب
47:      * عيّنةً بعد عيّنة): لو حكم المُحكِّم بالسقف وحكمت النافذة بالتساوي، لسقط طلبٌ صحيح
48:      * عند أول عيّنة. فحكم واحد يُستعمل في الموضعين وإلا تناقض المساران.
49:      */
50:     val verify: ((String, String?) -> Boolean)? = null,
51:     val stabilitySamples: Int = 3,
52:     val stabilityIntervalMs: Long = 40L,
53: ) {
54:     init {
55:         require(routeId.matches(ROUTE_ID)) { "routeId is not canonical" }
56:         require(key.isNotBlank() && token.isNotBlank()) { "hardware transaction identity is required" }
57:         require(stabilitySamples in 1..8) { "stability sample count is outside the bounded range" }
58:         require(stabilityIntervalMs in 0L..500L) { "stability interval is outside the bounded range" }
59:     }
60: 
61:     /** How long the read-back value must hold still before the transaction is called confirmed. */
62:     val confirmationWindowMs: Long get() = stabilityIntervalMs * (stabilitySamples - 1)
63: 
64:     companion object {
65:         val ROUTE_ID = Regex("^[a-z][a-z0-9_.-]{2,63}$")
66:     }
67: }
68: 
69: data class HardwareRepairResult(
70:     val routeId: String,
71:     val key: String,
72:     val state: HardwareRepairState,
73:     val requested: String,
74:     val actual: String?,
75:     val applied: Boolean,
76:     val verified: Boolean,
77:     val stabilitySamples: Int,
78:     val rollbackAttempted: Boolean,
79:     val rollbackVerified: Boolean?,
80:     val error: String? = null,
81: ) {
82:     val successful: Boolean get() = state == HardwareRepairState.CONFIRMED_WINDOW
83: }
84: 
85: /**
86:  * The only generic repair seam Atlas may use. It never owns a writer itself: all mutation and ownership
87:  * arbitration are delegated to [HardwareControlArbiter]. A successful first read-back is not enough;
88:  * the value must remain unchanged for the bounded confirmation window or the transaction is restored.
89:  *
90:  * **The window is deliberately short** (see [HardwareRepairState.CONFIRMED_WINDOW]): its job is to catch
91:  * a value that did not take at all, and it runs inside a caller that must not stall. A caller that needs
92:  * a long observation owns it in its own loop over `read()`, not here.
93:  */
94: class HardwareRepairExecutor(
95:     private val arbiter: HardwareControlArbiter,
96:     private val sleep: (Long) -> Unit = { delay -> if (delay > 0L) Thread.sleep(delay) },
97: ) : AtlasRepairPort {
98:     override fun execute(request: HardwareRepairRequest): HardwareRepairResult {
99:         val result = arbiter.submit(
100:             key = request.key,
101:             owner = request.owner,
102:             token = request.token,
103:             desired = request.desired,
104:             apply = request.apply,
105:             read = request.read,
106:             baseline = request.baseline,
107:             restore = request.restore,
108:             verify = request.verify,
109:         )
110:         if (!result.verified) {
111:             return HardwareRepairResult(
112:                 routeId = request.routeId,
113:                 key = request.key,
114:                 state = if (result.rollbackAttempted && result.rollbackVerified == true) {
115:                     HardwareRepairState.APPLY_FAILED_ROLLED_BACK
116:                 } else if (result.rollbackAttempted) {
117:                     HardwareRepairState.ROLLBACK_UNVERIFIED
118:                 } else {
119:                     HardwareRepairState.BLOCKED
120:                 },
121:                 requested = request.desired,
122:                 actual = result.actual,
123:                 applied = result.applied,
124:                 verified = false,
125:                 stabilitySamples = 0,
126:                 rollbackAttempted = result.rollbackAttempted,
127:                 rollbackVerified = result.rollbackVerified,
128:                 error = result.error,
129:             )
130:         }
131: 
132:         var lastActual = result.actual
133:         var stable = true
134:         for (sample in 0 until request.stabilitySamples) {
135:             if (sample > 0) sleep(request.stabilityIntervalMs)
136:             lastActual = runCatching { request.read() }.getOrNull()
137:             if (!satisfied(request, lastActual)) {
138:                 stable = false
139:                 break
140:             }
141:         }
142:         if (stable) {
143:             return HardwareRepairResult(
144:                 routeId = request.routeId,
145:                 key = request.key,
146:                 state = HardwareRepairState.CONFIRMED_WINDOW,
147:                 requested = request.desired,
148:                 actual = lastActual,
149:                 applied = true,
150:                 verified = true,
151:                 stabilitySamples = request.stabilitySamples,
152:                 rollbackAttempted = false,
153:                 rollbackVerified = null,
154:             )
155:         }
156: 
157:         val released = runCatching { arbiter.release(request.key, request.token, restore = true) }.getOrNull()
158:         val rollbackVerified = released?.rollbackVerified == true
159:         return HardwareRepairResult(
160:             routeId = request.routeId,
161:             key = request.key,
162:             state = if (rollbackVerified) {
163:                 HardwareRepairState.DRIFT_ROLLED_BACK
164:             } else {
165:                 HardwareRepairState.ROLLBACK_UNVERIFIED
166:             },
167:             requested = request.desired,
168:             actual = lastActual,
169:             applied = true,
170:             verified = false,
171:             stabilitySamples = request.stabilitySamples,
172:             rollbackAttempted = true,
173:             rollbackVerified = rollbackVerified,
174:             error = if (rollbackVerified) "external-writer-drift-baseline-restored" else "external-writer-drift-rollback-unverified",
175:         )
176:     }
177: 
178:     companion object {
179: 
180:         /** نفس حكم المُحكِّم بالضبط: الطلب هو مصدر الحكم، والتساوي الحرفي هو الافتراض. */
181:         private fun satisfied(request: HardwareRepairRequest, actual: String?): Boolean =
182:             request.verify?.invoke(request.desired, actual) ?: (actual != null && actual == request.desired)
183: 
184:         /**
185:          * A canonical transaction label for any control key.
186:          *
187:          * Keys carry separators that are meaningless in an id (`cpu_limits:/sys/devices/...` →
188:          * `cpu-limits-sys-devices-...`), so a label has to be derived rather than reused. It is
189:          * **reporting vocabulary only**: it never addresses a node, it is never parsed back, and the
190:          * hardware key inside the request is passed through untouched. Two different keys can collide
191:          * after canonicalisation, which is why the label is never used for ownership or comparison —
192:          * [HardwareControlArbiter] keys on `Request.key` alone.
193:          */
194:         fun labelFor(key: String): String {
195:             val slug = key.lowercase()
196:                 .map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '-' }
197:                 .joinToString("")
198:                 .replace(Regex("-{2,}"), "-")
199:                 .trim('-', '.')
200:             val prefixed = if (slug.firstOrNull()?.isLetter() == true) slug else "knob-$slug"
201:             val bounded = prefixed.take(63).trimEnd('-', '.')
202:             val label = if (bounded.length >= 3) bounded else (bounded + "-knob").take(63)
203:             require(label.matches(HardwareRepairRequest.ROUTE_ID)) { "cannot derive a canonical label from '$key'" }
204:             return label
205:         }
206:     }
207: }

(End of file - total 207 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveExecutorTest.kt",
  "limit": 300
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveExecutorTest.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import nd.max.core.atlas.AtlasControlGoal
4: import nd.max.core.atlas.AtlasControlIntent
5: import nd.max.core.atlas.AtlasControlTarget
6: import nd.max.core.atlas.AtlasControlTransport
7: import nd.max.core.atlas.AtlasRouteCandidate
8: import nd.max.core.atlas.AtlasRouteEvidence
9: import nd.max.core.atlas.AtlasStoreIo
10: import org.junit.Assert.assertEquals
11: import org.junit.Assert.assertFalse
12: import org.junit.Assert.assertTrue
13: import org.junit.Test
14: 
15: class AtlasAdaptiveExecutorTest {
16:     @Test
17:     fun `verified rollback lets Atlas try the next route`() {
18:         val calls = mutableListOf<String>()
19:         val executor = AtlasAdaptiveExecutor(port { request ->
20:             calls += request.routeId
21:             if (request.routeId == "route-first") {
22:                 failed(request, rollbackVerified = true)
23:             } else {
24:                 success(request)
25:             }
26:         })
27: 
28:         val result = executor.execute(
29:             intent = intent(),
30:             bindings = listOf(binding("route-first"), binding("route-second", priority = 1)),
31:         )
32: 
33:         assertEquals(listOf("route-first", "route-second"), calls)
34:         assertTrue(result.successful)
35:         assertEquals("route-second", result.selectedRouteId)
36:         assertFalse(result.fallbackStopped)
37:     }
38: 
39:     @Test
40:     fun `unverified rollback stops fallback to avoid compounding unknown state`() {
41:         val calls = mutableListOf<String>()
42:         val executor = AtlasAdaptiveExecutor(port { request ->
43:             calls += request.routeId
44:             failed(request, rollbackVerified = false)
45:         })
46: 
47:         val result = executor.execute(
48:             intent = intent(),
49:             bindings = listOf(binding("route-first"), binding("route-second", priority = 1)),
50:         )
51: 
52:         assertEquals(listOf("route-first"), calls)
53:         assertFalse(result.successful)
54:         assertTrue(result.fallbackStopped)
55:     }
56: 
57:     @Test
58:     fun `a route that previously stranded the baseline is not retried in this boot`() {
59:         val calls = mutableListOf<String>()
60:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 0L })
61:         memory.noteFailed("cpu_frequency", "route-first", rollbackVerified = false)
62:         val executor = AtlasAdaptiveExecutor(port { request ->
63:             calls += request.routeId
64:             success(request)
65:         }, memory = memory)
66: 
67:         val result = executor.execute(
68:             intent = intent(),
69:             bindings = listOf(binding("route-first"), binding("route-second", priority = 1)),
70:         )
71: 
72:         assertEquals(listOf("route-second"), calls)
73:         assertTrue(result.successful)
74:         assertEquals(listOf("route-first" to "route-quarantined-after-unverified-rollback"), result.skipped)
75:     }
76: 
77:     @Test
78:     fun `a verified route is attempted before its equally safe alternatives`() {
79:         val calls = mutableListOf<String>()
80:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 0L })
81:         memory.noteVerified("cpu_frequency", "route-second")
82:         val executor = AtlasAdaptiveExecutor(port { request ->
83:             calls += request.routeId
84:             success(request)
85:         }, memory = memory)
86: 
87:         executor.execute(
88:             intent = intent(),
89:             bindings = listOf(binding("route-first"), binding("route-second", priority = 1)),
90:         )
91: 
92:         assertEquals(listOf("route-second"), calls)
93:     }
94: 
95:     @Test
96:     fun `learning never promotes a route past a safer transport tier`() {
97:         val calls = mutableListOf<String>()
98:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 0L })
99:         // The remembered route is a sysfs writer; the alternative is a platform-owned hint.
100:         memory.noteVerified("cpu_frequency", "route-sysfs")
101:         val executor = AtlasAdaptiveExecutor(port { request ->
102:             calls += request.routeId
103:             success(request)
104:         }, memory = memory)
105: 
106:         executor.execute(
107:             intent = intent(),
108:             bindings = listOf(
109:                 binding("route-sysfs", transport = AtlasControlTransport.ARBITER_SYSFS),
110:                 binding("route-platform", transport = AtlasControlTransport.PLATFORM_HINT),
111:             ),
112:         )
113: 
114:         assertEquals(listOf("route-platform"), calls)
115:     }
116: 
117:     @Test
118:     fun `a successful attempt is remembered for the next time`() {
119:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 0L })
120:         val executor = AtlasAdaptiveExecutor(port { request -> success(request) }, memory = memory)
121: 
122:         executor.execute(intent = intent(), bindings = listOf(binding("route-first")))
123: 
124:         assertEquals("route-first", memory.preferredRoute("cpu_frequency"))
125:     }
126: 
127:     private fun port(block: (HardwareRepairRequest) -> HardwareRepairResult) = object : AtlasRepairPort {
128:         override fun execute(request: HardwareRepairRequest): HardwareRepairResult = block(request)
129:     }
130: 
131:     private fun intent() = AtlasControlIntent(
132:         target = AtlasControlTarget.CPU_FREQUENCY,
133:         goal = AtlasControlGoal.PERFORMANCE,
134:         desired = "next",
135:     )
136: 
137:     private fun binding(
138:         id: String,
139:         priority: Int = 0,
140:         transport: AtlasControlTransport = AtlasControlTransport.ROOT_DAEMON,
141:     ) = AtlasRouteBinding(
142:         candidate = AtlasRouteCandidate(
143:             id = id,
144:             priority = priority,
145:             evidence = AtlasRouteEvidence(
146:                 providerId = "test",
147:                 transport = transport,
148:                 target = AtlasControlTarget.CPU_FREQUENCY,
149:                 readable = true,
150:                 privilegeAvailable = true,
151:                 unitProven = true,
152:                 baselineReadable = true,
153:                 rollbackProven = true,
154:                 reviewed = true,
155:             ),
156:         ),
157:         request = HardwareRepairRequest(
158:             routeId = id,
159:             key = "cpu.test",
160:             owner = ControlOwnership.Owner.MAX_AI,
161:             token = "atlas-test",
162:             desired = "next",
163:             apply = { true },
164:             read = { "base" },
165:             restore = { true },
166:             baseline = "base",
167:         ),
168:     )
169: 
170:     private fun success(request: HardwareRepairRequest) = HardwareRepairResult(
171:         routeId = request.routeId,
172:         key = request.key,
173:         state = HardwareRepairState.CONFIRMED_WINDOW,
174:         requested = request.desired,
175:         actual = request.desired,
176:         applied = true,
177:         verified = true,
178:         stabilitySamples = 3,
179:         rollbackAttempted = false,
180:         rollbackVerified = null,
181:     )
182: 
183:     private fun failed(request: HardwareRepairRequest, rollbackVerified: Boolean) = HardwareRepairResult(
184:         routeId = request.routeId,
185:         key = request.key,
186:         state = if (rollbackVerified) HardwareRepairState.APPLY_FAILED_ROLLED_BACK else HardwareRepairState.ROLLBACK_UNVERIFIED,
187:         requested = request.desired,
188:         actual = null,
189:         applied = true,
190:         verified = false,
191:         stabilitySamples = 0,
192:         rollbackAttempted = true,
193:         rollbackVerified = rollbackVerified,
194:         error = "test-failure",
195:     )
196: 
197:     private class InMemoryIo : AtlasStoreIo {
198:         private val entries = LinkedHashMap<String, String>()
199:         override fun read(name: String): String? = entries[name]
200:         override fun write(name: String, text: String): Boolean {
201:             entries[name] = text
202:             return true
203:         }
204: 
205:         override fun delete(name: String): Boolean = entries.remove(name) != null
206:         override fun list(): List<String> = entries.keys.sorted()
207:     }
208: }

(End of file - total 208 lines)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 14.8s)

_Thinking:_



**Tool: grep**

**Input:**
```json
{
  "path": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max",
  "pattern": "AtlasRouteMemory\\(|AtlasResolver\\(|AtlasDiscovery\\(|AtlasFailureLedger\\(|AtlasRepository\\(",
  "limit": 70
}
```

**Output:**
```
Found 16 matches
/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/AppMonitor.kt:
  Line 341:             val memory = AtlasRouteMemory(io = io, clockMs = { android.os.SystemClock.elapsedRealtime() })


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasResolver.kt:
  Line 84: class AtlasResolver(


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasRepository.kt:
  Line 126: class AtlasRepository(


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasDiscovery.kt:
  Line 80: class AtlasDiscovery(


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasFailureLedger.kt:
  Line 136: class AtlasFailureLedger(


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt:
  Line 70: class AtlasRouteMemory(


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/di/DataModule.kt:
  Line 105:     fun provideAtlasRouteMemory(

  Line 108:     ): AtlasRouteMemory = AtlasRouteMemory(

  Line 171:     fun provideAtlasFailureLedger(clockMs: () -> Long): AtlasFailureLedger =

  Line 172:         AtlasFailureLedger(clockMs = clockMs)

  Line 176:     fun provideAtlasResolver(store: AtlasEvidenceStore, clockMs: () -> Long): AtlasResolver =

  Line 177:         AtlasResolver(store = store, clockMs = clockMs)

  Line 199:     fun provideAtlasDiscovery(

  Line 203:     ): AtlasDiscovery = AtlasDiscovery(

  Line 213:     fun provideAtlasRepository(

  Line 219:     ): AtlasRepository = AtlasRepository(

```

---

## Assistant (Code · GPT-6 Astra · 15.6s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasFileStoreIo.kt",
  "limit": 260
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/atlas/AtlasFileStoreIo.kt</path>
<type>file</type>
<content>
1: package nd.max.core.atlas
2: 
3: import java.io.File
4: import java.io.IOException
5: import java.nio.charset.StandardCharsets
6: import java.nio.file.AtomicMoveNotSupportedException
7: import java.nio.file.Files
8: import java.nio.file.Path
9: import java.nio.file.StandardCopyOption
10: 
11: /**
12:  * The real store backing (`T5.5`), used by the app and by its tests.
13:  *
14:  * On a device the directory is app-private (`noBackupFilesDir/atlas`), which is deliberate: this
15:  * evidence is reconstructible, so it has no business in a backup, and a restored cache from another
16:  * device would be evidence about a machine the user no longer has. In tests the same class is pointed
17:  * at a temporary directory, so the shipped write path — temporary file, fsync-by-close, atomic
18:  * rename — is the one exercised.
19:  *
20:  * **Atomic replace is not decoration.** A cache entry that is half-written parses as far as it goes
21:  * and then fails, and a reader cannot tell that from a genuinely different device. Writing to a
22:  * temporary name and renaming means a reader always sees either the previous entry or the new one.
23:  * `ATOMIC_MOVE` is requested and a plain replace is the fallback, because some filesystems refuse the
24:  * atomic flag; the fallback is still a rename inside one directory, never a truncate-in-place.
25:  */
26: class AtlasFileStoreIo(private val directory: Path) : AtlasStoreIo {
27: 
28:     override fun read(name: String): String? {
29:         val file = pathOf(name)
30:         if (!Files.isRegularFile(file)) return null
31:         return try {
32:             String(Files.readAllBytes(file), StandardCharsets.UTF_8)
33:         } catch (_: IOException) {
34:             null
35:         } catch (_: SecurityException) {
36:             null
37:         }
38:     }
39: 
40:     override fun write(name: String, text: String): Boolean {
41:         val target = pathOf(name)
42:         return try {
43:             Files.createDirectories(directory)
44:             val temporary = directory.resolve("$name.tmp")
45:             Files.write(temporary, text.toByteArray(StandardCharsets.UTF_8))
46:             try {
47:                 Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
48:             } catch (_: AtomicMoveNotSupportedException) {
49:                 Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
50:             }
51:             true
52:         } catch (_: IOException) {
53:             false
54:         } catch (_: SecurityException) {
55:             false
56:         }
57:     }
58: 
59:     override fun delete(name: String): Boolean = try {
60:         Files.deleteIfExists(pathOf(name))
61:     } catch (_: IOException) {
62:         false
63:     } catch (_: SecurityException) {
64:         false
65:     }
66: 
67:     override fun list(): List<String> = try {
68:         if (!Files.isDirectory(directory)) {
69:             emptyList()
70:         } else {
71:             Files.newDirectoryStream(directory).use { stream ->
72:                 // Sorted so a scan over the store is deterministic; a test that depends on directory
73:                 // order is a test that fails on another filesystem.
74:                 stream.map { it.fileName.toString() }.sorted()
75:             }
76:         }
77:     } catch (_: IOException) {
78:         emptyList()
79:     } catch (_: SecurityException) {
80:         emptyList()
81:     }
82: 
83:     private fun pathOf(name: String): Path {
84:         require(SAFE_NAME.matches(name)) { "a store entry name is a plain file name: $name" }
85:         return directory.resolve(name)
86:     }
87: 
88:     companion object {
89:         /** Entry names are generated by the store; anything else is refused rather than resolved. */
90:         private val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,120}$")
91: 
92:         /** The app-private directory the store lives in, relative to a context's private storage. */
93:         const val DIRECTORY_NAME: String = "atlas"
94: 
95:         /**
96:          * مجلد ذاكرة المسارات داخل مخزن Atlas — **منفصل عن مجلد الأدلة** كي لا يمحو أحدهما الآخر.
97:          *
98:          * والثابت هنا لا في كل مستدعٍ: المسار نفسه يحتاج أن يتّفق عليه مزوّد Hilt في الواجهة
99:          * والرفيق الذي يكتب المسارات في عملية أخرى — ومساران مختلفان يعنيان ذاكرتين لا واحدة.
100:          */
101:         const val ROUTE_MEMORY_DIRECTORY_NAME: String = "routes"
102: 
103:         /** The directory under a private files root. */
104:         fun directoryFor(privateRoot: File): File = File(privateRoot, DIRECTORY_NAME)
105:     }
106: }

(End of file - total 106 lines)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 45.0s)

_Thinking:_



**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c52514affezdTXZoKxZL1HmE",
  "type": "ASK",
  "body": "Please conclude research with the already-established findings now; no further file exploration needed. Need your final concrete discovery -> existing writer integration recommendation to lock implementation scope. I have read memory/DataModule/executor and found generation reset also retains prior rollbackFailures on subsequent success, plus storage pruning clears all routes. Will keep changes bounded rather than blanket rewrite."
}
```

**Output:**
```
{"id":"board_ba0f7d52-1a23-44c3-a1fc-14ac59215c2a","timestamp":1789989956210,"from":"main","to":"ses_f3c52514affezdTXZoKxZL1HmE","type":"ASK","body":"Please conclude research with the already-established findings now; no further file exploration needed. Need your final concrete discovery -> existing writer integration recommendation to lock implementation scope. I have read memory/DataModule/executor and found generation reset also retains prior rollbackFailures on subsequent success, plus storage pruning clears all routes. Will keep changes bounded rather than blanket rewrite.","fromLabel":"New session - 2026-09-21T11:14:10.075Z","toLabel":"Map Max Atlas gaps (@explore subagent)","availability":{"observedAt":1789989956213,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"retry"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

---

## Assistant (Code · GPT-6 Astra · 27.6s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/AppMonitor.kt",
  "limit": 130
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/AppMonitor.kt</path>
<type>file</type>
<content>
1: /*
2:  * Copyright (C) 2026 Rem01Gaming
3:  *
4:  * Licensed under the Apache License, Version 2.0 (the "License");
5:  * you may not use this file except in compliance with the License.
6:  * You may obtain a copy of the License at
7:  *
8:  * http://www.apache.org/licenses/LICENSE-2.0
9:  *
10:  * Unless required by applicable law or agreed to in writing, software
11:  * distributed under the License is distributed on an "AS IS" BASIS,
12:  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
13:  * See the License for the specific language governing permissions and
14:  * limitations under the License.
15:  */
16: 
17: package nd.max
18: 
19: 
20: import android.annotation.SuppressLint
21: import android.app.ActivityManager
22: import android.app.Notification
23: import android.app.NotificationChannel
24: import android.app.NotificationManager as AndroidNotificationManager
25: import android.app.PendingIntent
26: import android.content.ComponentName
27: import android.content.Intent
28: import android.content.Context
29: import android.os.BatteryManager
30: import android.os.Build
31: import android.os.IBinder
32: import android.os.PowerManager
33: import java.io.File
34: import java.io.FileOutputStream
35: import java.lang.reflect.Field
36: import java.lang.reflect.Method
37: import java.nio.channels.FileChannel
38: import java.nio.channels.FileLock
39: import java.nio.file.Paths
40: import java.nio.file.StandardOpenOption
41: import org.lsposed.hiddenapibypass.HiddenApiBypass
42: import android.hardware.display.DisplayManager
43: import android.view.Display
44: import android.provider.Settings
45: import android.media.AudioManager
46: import android.net.wifi.WifiManager
47: import java.text.SimpleDateFormat
48: import java.util.Date
49: import java.util.Locale
50: import nd.max.core.atlas.AtlasControlTarget
51: import nd.max.core.atlas.AtlasFileStoreIo
52: import nd.max.core.diagnostics.DeviceFacts
53: import nd.max.core.diagnostics.LogHeader
54: import nd.max.core.diagnostics.LogSettingsDigest
55: import nd.max.ui.util.PerAppKernelUtil
56: import nd.max.ui.util.ProfilePresetStore
57: import nd.max.core.hardware.AtlasAdaptiveExecutor
58: import nd.max.core.hardware.AtlasRouteMemory
59: import nd.max.core.hardware.CpuHardwareBackend
60: import nd.max.core.hardware.GpuHardwareBackend
61: import nd.max.core.hardware.HardwareRepairExecutor
62: import nd.max.core.hardware.GpuTweakPersistence
63: import nd.max.core.hardware.HardwareControlArbiter
64: import nd.max.core.hardware.HardwareControlKey
65: import nd.max.core.hardware.HardwareVerification
66: import nd.max.core.hardware.PerAppHardwareStatus
67: import nd.max.core.hardware.PerAppHardwareStatus.Outcome
68: import nd.max.core.hardware.RootFileAccess
69: import nd.max.core.hardware.ThermalCeilingRouter
70: import nd.max.core.hardware.ThermalGuard
71: import nd.max.core.hardware.SharedHardwareOwnershipStore
72: import nd.max.core.hardware.ManualControlLocks
73: import nd.max.core.hardware.PerAppControlRegistry
74: import nd.max.core.hardware.PerAppRecoveryStore
75: import nd.max.ui.util.PropertyUtils
76: import nd.max.ui.util.decodePerAppCpuPolicyControls
77: import nd.max.ui.viewmodel.TouchBoostViewModel
78: 
79: 
80: @SuppressLint("StaticFieldLeak", "DiscouragedPrivateApi", "PrivateApi")
81: object AppMonitor {
82:     private const val POLL_INTERVAL_MS = 500L
83:     // How often reassertDriftedKnobs() re-verifies GPU/CPU knobs against
84:     // what the per-app config actually asked for. Deliberately much coarser
85:     // than POLL_INTERVAL_MS: this is a safety net against vendor thermal
86:     // daemons undoing our writes, not a tight control loop.
87:     private const val DRIFT_CHECK_INTERVAL_MS = 10_000L
88:     private const val PID_RETRY_INTERVAL_MS = 50L
89:     private const val UNKNOWN_APP = "unknown 0 0"
90:     private const val NONE_APP = "none 0 0"
91:     // مهلة السماح قبل التراجع عن تعديلات تطبيق مغادر نحو غير مُدار:
92:     // التنقل السريع (إشعار ثم عودة) لا يخفق التعديلات، وموت العملية
93:     // يُعجّل التراجع فورًا دون انتظار. 10 ثوانٍ توافق مهلة الوحدة
94:     // الأصلية عند إطفاء الشاشة.
95:     private const val PERAPP_GRACE_MS = 10_000L
96: 
97:     private val FOREGROUND_METHOD_CANDIDATES = listOf(
98:         "getFocusedRootTaskInfo",
99:         "getFocusedRootTask",
100:         "getFocusedTaskInfo",
101:         "getFocusedStackInfo",
102:         "getTopActivity",
103:         "getTasks",
104:         "getRunningTasks"
105:     )
106: 
107:     private val COMPONENT_NAME_FIELDS = listOf(
108:         "topActivity",
109:         "topActivityComponent",
110:         "realActivity",
111:         "baseActivity",
112:         "origActivity",
113:         "activity"
114:     )
115: 
116:     private var systemContext: Context? = null
117: 
118:     private var activityTaskManager: Any? = null
119:     private var foregroundMethod: Method? = null
120:     private var powerManager: PowerManager? = null
121:     private var activityManager: ActivityManager? = null
122:     private var notificationManager: Any? = null
123:     private var batteryManager: BatteryManager? = null
124:     private var getZenModeMethod: Method? = null
125: 
126:     private var bruteForceCandidates: List<Method>? = null
127: 
128:     @Volatile
129:     private var lastStatus = ""
130:     

(Showing lines 1-130 of 2450. Use offset=131 to continue.)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 55.5s)

_Thinking:_



وجدت عائقًا مباشرًا أمام التعافي التلقائي: ذاكرة أطلس في التطبيق والعملية المرافقة تستخدم رقم إقلاع ثابتًا، فيمكن أن يبقى مسار محظورًا حتى بعد إعادة التشغيل. كما أن تسجيل نجاح لاحق قد يعيد حمل فشل الاسترجاع القديم؛ سأصلح دورة حياة هذه الذاكرة وأختبرها أولًا، دون إضافة كتابات عتاد أو تخمين مسارات.


**Tool: todowrite**

**Input:**
```json
{
  "todos": [
    {
      "content": "استكشاف Max أطلس وتحديد أول خطوة نحو التوافق والتعافي التلقائي",
      "priority": "high",
      "status": "completed"
    },
    {
      "content": "إصلاح ذاكرة التعافي وربط جيل الإقلاع الحقيقي بمستهلكي أطلس مع اختبارات انحدار",
      "priority": "high",
      "status": "in_progress"
    },
    {
      "content": "التحقق المستهدف ومراجعة السلامة وتوثيق ما تحقق وحدود التوافق المتبقية",
      "priority": "high",
      "status": "pending"
    }
  ]
}
```

**Output:**
```
[
  {
    "content": "استكشاف Max أطلس وتحديد أول خطوة نحو التوافق والتعافي التلقائي",
    "status": "completed",
    "priority": "high"
  },
  {
    "content": "إصلاح ذاكرة التعافي وربط جيل الإقلاع الحقيقي بمستهلكي أطلس مع اختبارات انحدار",
    "status": "in_progress",
    "priority": "high"
  },
  {
    "content": "التحقق المستهدف ومراجعة السلامة وتوثيق ما تحقق وحدود التوافق المتبقية",
    "status": "pending",
    "priority": "high"
  }
]
```

---

## Assistant (Code · GPT-6 Astra · 21.5s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/docs/ai/NEXT_TASK.md",
  "offset": 175,
  "limit": 28
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/docs/ai/NEXT_TASK.md</path>
<type>file</type>
<content>
175: الفتّاكة — لم تُقرأ في هذه الجولة، ولا أدّعي أن الشجرة نظيفة منها.
176: 
177: ## ATLAS-01 — «هلاّ اكتشف أطلس المسار الصحيح عند فشل وظيفة؟»: مُرشِّح مسارات لمسار الكتابة — **غير مبدوء · يحتاج قرار المالك** · 2026-09-20
178: 
179: **السؤال كما وُرِد:** ما فائدة Max Atlas؟ أليس كان حتى لو فشل وظيفة يكتشف الطريق الصحيح؟
180: **الجواب المقيس (التفصيل في `HANDOFF` تكملة ٥٨):** أطلس طبقة **أدلّة** (حكم بسبب، غياب مُثبَت، run
181: قابل للإعادة، تقرير دعم) — واتجاه البيانات **واحد**: `Cpu/GpuHardwareBackend` تُغذّيه، وهو يُغذّي شاشة
182: التشخيص. **لا مسار كتابة يأخذ سقف مساره من أطلس.** ومسارات العتاد قوائم ثابتة (`BATTERY_DIRS` ·
183: `FAST_CHARGE_CURRENT_CANDIDATES` · `SIC_MODE_CANDIDATES` في الكوتلن · `bypass_list` في C).
184: **والدليل من حزمتيك:** `CONFIG_LOADED … bypass_path=UNSUPPORTED` · `BATTERY_TELEMETRY … evidence=1 nodes`
185: · لا قدرة `BYPASS_CHARGE` في المصفوفة. ومن مسارات الشحن الجانبي السبعة والسبعين، **٣١ داخل
186: `/sys/class/power_supply` وهو جذر أطلس المعتمد القابل للتعداد** — أي أن شطرًا كبيرًا من الاكتشاف كان
187: ممكنًا اليوم بلا تغيير عقد، ولم يسأله أحد.
188: 
189: **الخياران (اختر واحدًا قبل التنفيذ):**
190: 
191: | # | الخيار | ما يلمسه | ميزانية الإثبات اللازمة |
192: | --- | --- | --- | --- |
193: | أ | **مُرشِّح مسارات للكوِتلن**: عند فشل القائمة الثابتة يُعدّد الجذور المعتمدة ويطابق اسم الخاصية | `core/hardware` + `ChargingViewModel` — **بلا كتابة من أطلس** | اختبار بواجهة قراءة مُصطنعة: القائمة الثابتة تفوز عند وجودها · التعداد يُستخدم عند غيابها · غياب مُثبَت لا مجهول |
194: | ب | **توسيع عدّادات أطلس** (`/sys/devices/platform` · `/proc/mtk_*` · `/sys/kernel/debug`) ثم الجسر الكامل | عقد أطلس + مراجعة مصادر | مصدر مُراجع لكل جذر + مراجعة سلامة + قياس جهاز |
195: 
196: **الممنوع اليوم:** لمس `bypass_list` في C (٧٧ مسارًا تمسّ عقد شحن، وعلى الجهاز انهيار نواة في سائق الشاحن
197: نفسه) — يحتاج جهازًا ومراجعة سلامة، لا رقعة من هنا.
198: 
199: ## LOGS-01 — حزمتا سجلّات جهاز حقيقي (MT6899): ستّة عيوب مقيسة أُصلحت — **منجز تقنيًّا · مُعلَّق على مراجعة سلامة وقياس جهاز** · 2026-09-20
200: 
201: **المدخل:** حزمتا `MaxManager_Logs_٢٠٢٦٠٩٢٠_١٣٤٠٤٢.tar.gz` و`…_١٠٢١٠٤.tar.gz` (جهاز `rodin` · MT6899 ·
202: Android 16 · KernelSU). التفصيل والقياسات والقيود كلها في `docs/ai/HANDOFF.md` **تكملة ٥٧**.

(Showing lines 175-202 of 679. Use offset=203 to continue.)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 49.1s)

_Thinking:_



**Tool: apply_patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/docs/ai/NEXT_TASK.md\n@@\n-## ATLAS-01 — «هلاّ اكتشف أطلس المسار الصحيح عند فشل وظيفة؟»: مُرشِّح مسارات لمسار الكتابة — **غير مبدوء · يحتاج قرار المالك** · 2026-09-20\n+## ATLAS-RECOVERY-02 — ذاكرة تعافٍ صالحة بين الإقلاعات — قيد التنفيذ · 2026-09-21\n+\n+**الهدف الذي حدّده المالك:** توافق تلقائي على أكبر عدد ممكن من الأجهزة؛ اكتشاف أسباب الفشل والبدائل\n+وإصلاح ما يمكن إصلاحه دون تدخّل يدوي. هذا هدف ممتد، وليس ضمانًا بتوفير وظيفة لا يدعمها العتاد أو النواة.\n+\n+**هذه الجولة (medium):** إصلاح شرط سابق للتعافي: `AtlasRouteMemory` تحتفظ بجيل إقلاع صفري في\n+`DataModule` و`AppMonitor`، وتنسخ عدّاد فشل الاسترجاع من إقلاع قديم إلى الإقلاع الجديد عند أول نتيجة.\n+ربط المصنعين بقراءة `Settings.Global.BOOT_COUNT` المشتركة، وإبقاء الحجر عند تعذّر معرفة الإقلاع،\n+وإعادة تهيئة نتائج المسار عندما يتغير السياق المعلوم. الاختبارات تثبت التعافي بعد إعادة الإقلاع وعدم\n+اعتبار إعادة تشغيل العملية إقلاعًا جديدًا. لا توسيع لجذور الاكتشاف ولا كتابة عتاد ولا تغيير SELinux.\n+\n+**النطاق:** `core/hardware/AtlasRouteMemory*`، موضعا الإنشاء في `core/di/DataModule.kt` و`AppMonitor.kt`،\n+واختبارات الذاكرة/التنفيذ. **القبول:** الاختبارات المستهدفة + البوابات الثابتة + مراجعة مستقلة؛ التجربة\n+على جهاز والحكم المطلوب من مراجع السلامة المحدد في `AGENTS.md` يبقيان معلنين إن تعذّرا.\n+\n+## ATLAS-01 — «هلاّ اكتشف أطلس المسار الصحيح عند فشل وظيفة؟»: مُرشِّح مسارات لمسار الكتابة — سجل سابق · 2026-09-20\n+\n+**تصحيح الحالة 2026-09-21:** وصف «لا مسار كتابة» أدناه تاريخي، لا يصف الشجرة الحالية:\n+`MinimalPlanner` و`ThermalCeilingRouter` يستعملان `AtlasAdaptiveExecutor` بالفعل. تعميم اكتشاف\n+البدائل على باقي الوظائف لم يكتمل؛ لا يُستنتج من وجود منفّذ عام أن كل وظيفة موصولة به.\n*** Add File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt\n+package nd.max.core.hardware\n+\n+import android.content.Context\n+import android.provider.Settings\n+import nd.max.core.atlas.AtlasFileStoreIo\n+import java.nio.file.Paths\n+\n+/** The app and companion must agree on both the store and the device boot, not the process lifetime. */\n+object AtlasRouteMemoryFactory {\n+    fun create(context: Context, clockMs: () -> Long): AtlasRouteMemory {\n+        // Deferred until the first memory operation on the caller's worker. Never prompt for root.\n+        val bootGeneration by lazy {\n+            AtlasRouteMemory.generationForBootCount(runCatching {\n+                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)\n+            }.getOrNull())\n+        }\n+        return AtlasRouteMemory(\n+            io = AtlasFileStoreIo(\n+                Paths.get(\n+                    AtlasFileStoreIo.directoryFor(context.noBackupFilesDir).absolutePath,\n+                    AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME,\n+                ).toAbsolutePath(),\n+            ),\n+            clockMs = clockMs,\n+            bootGeneration = { bootGeneration },\n+            // Permission changes do not establish that an unknown physical state was restored.\n+            // Both processes keep quarantine until a different, known boot has rebuilt vendor state.\n+            privilegeGeneration = { 0L },\n+        )\n+    }\n+}\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt\n@@\n-    /** Whether this route may still be attempted in the generation the entry was recorded in. */\n+    /** An unknown boot cannot prove that a previous unsafe state has gone away. */\n     fun isQuarantinedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =\n         rollbackFailures > 0 &&\n-            this.bootGeneration == bootGeneration &&\n-            this.privilegeGeneration == privilegeGeneration\n+            (this.bootGeneration == 0L || bootGeneration == 0L ||\n+                (this.bootGeneration == bootGeneration && this.privilegeGeneration == privilegeGeneration))\n@@\n         lastOutcome == AtlasRouteOutcome.VERIFIED &&\n             successes > 0 &&\n+            rollbackFailures == 0 &&\n+            bootGeneration > 0L &&\n             this.bootGeneration == bootGeneration &&\n@@\n-    fun preferredRoute(target: String): String? = entries()\n-        .filter { it.target == target && it.isVerifiedIn(bootGeneration(), privilegeGeneration()) }\n-        .maxByOrNull { it.lastVerifiedElapsedMs ?: Long.MIN_VALUE }\n-        ?.routeId\n+    fun preferredRoute(target: String): String? {\n+        val boot = bootGeneration()\n+        val privilege = privilegeGeneration()\n+        return entries()\n+            .filter { it.target == target && it.isVerifiedIn(boot, privilege) }\n+            .maxByOrNull { it.lastVerifiedElapsedMs ?: Long.MIN_VALUE }\n+            ?.routeId\n+    }\n@@\n-    fun quarantinedRoutes(target: String): Set<String> = entries()\n-        .filter { it.target == target && it.isQuarantinedIn(bootGeneration(), privilegeGeneration()) }\n-        .map { it.routeId }\n-        .toSet()\n+    fun quarantinedRoutes(target: String): Set<String> {\n+        val boot = bootGeneration()\n+        val privilege = privilegeGeneration()\n+        return entries()\n+            .filter { it.target == target && it.isQuarantinedIn(boot, privilege) }\n+            .map { it.routeId }\n+            .toSet()\n+    }\n@@\n-        val entry = load(target, routeId) ?: newEntry(target, routeId)\n+        val entry = currentEntry(target, routeId)\n@@\n-        val entry = load(target, routeId) ?: newEntry(target, routeId)\n+        val entry = currentEntry(target, routeId)\n@@\n-    private fun newEntry(target: String, routeId: String) = AtlasRouteMemoryEntry(\n+    private fun currentEntry(target: String, routeId: String): AtlasRouteMemoryEntry {\n+        val boot = bootGeneration()\n+        val privilege = privilegeGeneration()\n+        val previous = load(target, routeId)\n+        if (previous != null && (\n+                (previous.bootGeneration == boot && previous.privilegeGeneration == privilege) ||\n+                    previous.isQuarantinedIn(boot, privilege)\n+            )) {\n+            return previous.copy(bootGeneration = boot, privilegeGeneration = privilege)\n+        }\n+        // Counters describe one generation. Copying an old rollback failure here would quarantine\n+        // the route again immediately after its first successful recovery in the new boot.\n+        return AtlasRouteMemoryEntry(\n@@\n-        bootGeneration = bootGeneration(),\n-        privilegeGeneration = privilegeGeneration(),\n+        bootGeneration = boot,\n+        privilegeGeneration = privilege,\n@@\n-    )\n+        )\n+    }\n@@\n-        // The generations are refreshed on every write: the record always describes the context it was\n-        // last observed in, which is what makes the boot/privilege checks meaningful.\n-        bootGeneration = bootGeneration(),\n-        privilegeGeneration = privilegeGeneration(),\n+        // currentEntry captured one context for the whole update, including counter reset.\n@@\n     companion object {\n         const val SCHEMA: Int = 1\n+\n+        /** Zero means unknown (including legacy records); Android's valid zero count maps to one. */\n+        fun generationForBootCount(bootCount: Int?): Long =\n+            bootCount?.takeIf { it >= 0 }?.toLong()?.plus(1L) ?: 0L\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/di/DataModule.kt\n@@\n import nd.max.core.hardware.AtlasRouteMemory\n+import nd.max.core.hardware.AtlasRouteMemoryFactory\n@@\n-    ): AtlasRouteMemory = AtlasRouteMemory(\n-        io = AtlasFileStoreIo(\n-            Paths.get(AtlasFileStoreIo.directoryFor(context.noBackupFilesDir).absolutePath, ROUTE_MEMORY_DIR)\n-                .toAbsolutePath(),\n-        ),\n-        clockMs = clockMs,\n-    )\n+    ): AtlasRouteMemory = AtlasRouteMemoryFactory.create(context, clockMs)\n@@\n-    /**\n-     * Subdirectory of the Atlas store that holds learned routes, kept apart from evidence.\n-     *\n-     * ويشير إلى ثابت `AtlasFileStoreIo` بدل نصّ ثانٍ: الرفيق (`AppMonitor`) يكتب المسارات في\n-     * عملية أخرى، وذاكرة مسارات بمجلدين مختلفين ليست ذاكرة واحدة.\n-     */\n-    private const val ROUTE_MEMORY_DIR = AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME\n }\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/AppMonitor.kt\n@@\n-import java.nio.file.Paths\n@@\n-import nd.max.core.atlas.AtlasFileStoreIo\n@@\n-import nd.max.core.hardware.AtlasRouteMemory\n+import nd.max.core.hardware.AtlasRouteMemoryFactory\n@@\n-     * وذاكرة المسارات في نفس مجلد الواجهة (`noBackupFilesDir/atlas/routes`) عبر ثابت مشترك في\n-     * `AtlasFileStoreIo` — فمساران مختلفان يعنيان ذاكرتين لا واحدة.\n-     *\n-     * **حدّ معروف ومعلن:** `bootGeneration`/`privilegeGeneration` تُترك على الافتراضي (صفر) كما\n-     * في مزوّد Hilt في الواجهة. فالنتيجة أن حجر مسار بعد استرجاع غير مؤكَّد يبقى حتى يُقلَّم المخزن،\n-     * لا حتى الإقلاع — أي أن قاعدة «الحجر ينتهي بالإقلاع» في `AtlasRouteMemory` غير مُشغَّلة بعد في\n-     * المسارين. ربط جيل إقلاع حقيقي تغيير يمسّ الواجهة والرفيق معًا، فلم يُفعل ضمنيًّا هنا.\n+     * المصنع المشترك مع Hilt يربط الذاكرة بنفس المجلد ونفس عدّاد إقلاع الجهاز.\n+     * إعادة تشغيل الرفيق ليست إقلاعًا جديدًا، وتعذّر قراءة العدّاد لا يرفع الحجر.\n@@\n-            val io = AtlasFileStoreIo(\n-                Paths.get(\n-                    AtlasFileStoreIo.directoryFor(controlContext.noBackupFilesDir).absolutePath,\n-                    AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME,\n-                ).toAbsolutePath(),\n-            )\n-            val memory = AtlasRouteMemory(io = io, clockMs = { android.os.SystemClock.elapsedRealtime() })\n+            val memory = AtlasRouteMemoryFactory.create(controlContext) { android.os.SystemClock.elapsedRealtime() }\n*** End Patch"
}
```

**Output:**
```
Success. Updated the following files:
M docs/ai/NEXT_TASK.md
A manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt
M manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
M manager/app/src/main/java/nd/max/core/di/DataModule.kt
M manager/app/src/main/java/nd/max/AppMonitor.kt
```

---

## Assistant (Code · GPT-6 Astra · 30.6s)

_Thinking:_



**Tool: task**

**Input:**
```json
{
  "subagent_type": "general",
  "description": "Build focused Atlas verification",
  "background": true,
  "prompt": "Task ATLAS-RECOVERY-02 testing infrastructure ONLY. Read docs/ai/team/verifier.md, HANDOFF invariants, VALIDATION §8 and ENGINEERING-CONTRACT. User goal autonomous compatibility/recovery. Main is fixing AtlasRouteMemory lifecycle and shared AtlasRouteMemoryFactory (Android BOOT_COUNT, same store app/companion) and will add memory/executor regression tests. Your exclusive WRITE scope: NEW tools/test_atlas_jvm.py only; optionally NEW manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryWiringTest.kt only (source architecture guards app/companion use shared factory, no root prompt; test do not require Android). Do not edit any other files. Use existing tools/test_maxai_jvm.py as runner convention: cached Kotlin 2.3.10 / coroutines 1.10.2 / JUnit4.13.2, real production Kotlin files, no fake Android APIs, no Gradle/NDK build. Create focused runner compiling actual pure dependencies for AtlasRouteMemoryTest AND AtlasAdaptiveExecutorTest (plus existing HardwareRepairExecutorTest/AtlasRoutePlannerTest if achievable without platform stubs); sources classes HardwareControlArbiter etc may have Android deps so inspect and adjust scope honestly if cannot execute executor tests. Android-facing changed files should be parsed with real Kotlin PSI parser not typecheck claims. Existing org.json jar may be absent cached; support --json-jar external provided path and never auto download, declare version existing app gradle org.json:json:20260814. Temp output under --work-dir default /tmp/kilo. Do not run build or commit. You may run targeted JVM runner; coordinate source set blockers on board. Return exact runnable command, limitations and files changed. Parent has already run baseline gates green (756 structural files). Avoid source implementation/research duplicate."
}
```

**Output:**
```
<task id="ses_f3c460af9ffegFtpDQxOyzwjXg" state="running">
<summary>Background task started</summary>
<task_result>
The task is working in the background. You will be notified automatically when it finishes.
DO NOT sleep, poll for progress, ask the task for status, or duplicate this task's work — avoid working with the same files or topics it is using.
Work on non-overlapping tasks, or briefly tell the user what you launched and end your response.
</task_result>
</task>
```

**Tool: bash**

**Input:**
```json
{
  "command": "ls /tmp/kilo",
  "description": "Verify approved temporary artifact directory"
}
```

**Output:**
```
framework.aidl.bak
libsu
logs
logs_92811812546
max-live-architecture-tests.jar

```

**Tool: grep**

**Input:**
```json
{
  "path": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/AppMonitor.kt",
  "pattern": "Paths\\.|AtlasFileStoreIo|AtlasRouteMemory",
  "limit": 20
}
```

**Output:**
```
Found 7 matches
/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/AppMonitor.kt:
  Line 56: import nd.max.core.hardware.AtlasRouteMemoryFactory

  Line 328:             val memory = AtlasRouteMemoryFactory.create(controlContext) { android.os.SystemClock.elapsedRealtime() }

  Line 349:             shellRead("grep '^version=' '${MaxManagerPaths.MODULE_DIR}/module.prop' 2>/dev/null | head -n1")

  Line 919:             val configFile = File(MaxManagerPaths.APPLIST_JSON)

  Line 1014:             val file = File(MaxManagerPaths.APPLIST_JSON)

  Line 1803:             MaxManagerPaths.PER_APP_CPU_STATUS,

  Line 2358:         val path = MaxManagerPaths.APPLIST_JSON

```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt",
  "offset": 120,
  "limit": 90
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt</path>
<type>file</type>
<content>
120:                 rollbackFailures = entry.rollbackFailures + if (rollbackVerified) 0 else 1,
121:             ),
122:         )
123:     }
124: 
125:     /** Drops one target's memory. Used when a device config change invalidates what was learned. */
126:     fun forget(target: String): Int = io.list().count { name ->
127:         name.startsWith(NAME_PREFIX) && name.endsWith(NAME_SUFFIX) && name.contains(slug(target)) && io.delete(name)
128:     }
129: 
130:     fun clear(): Int = io.list().count { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) && io.delete(it) }
131: 
132:     fun entries(): List<AtlasRouteMemoryEntry> = io.list()
133:         .filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
134:         .mapNotNull { name -> io.read(name)?.let(::decode) }
135:         .sortedWith(compareBy({ it.target }, { it.routeId }))
136: 
137:     // ---- helpers -----------------------------------------------------------------------------------
138: 
139:     private fun currentEntry(target: String, routeId: String): AtlasRouteMemoryEntry {
140:         val boot = bootGeneration()
141:         val privilege = privilegeGeneration()
142:         val previous = load(target, routeId)
143:         if (previous != null && (
144:                 (previous.bootGeneration == boot && previous.privilegeGeneration == privilege) ||
145:                     previous.isQuarantinedIn(boot, privilege)
146:             )) {
147:             return previous.copy(bootGeneration = boot, privilegeGeneration = privilege)
148:         }
149:         // Counters describe one generation. Copying an old rollback failure here would quarantine
150:         // the route again immediately after its first successful recovery in the new boot.
151:         return AtlasRouteMemoryEntry(
152:         target = target,
153:         routeId = routeId,
154:         lastOutcome = AtlasRouteOutcome.FAILED,
155:         lastVerifiedElapsedMs = null,
156:         bootGeneration = boot,
157:         privilegeGeneration = privilege,
158:         successes = 0,
159:         failures = 0,
160:         rollbackFailures = 0,
161:         )
162:     }
163: 
164:     private fun copy(
165:         entry: AtlasRouteMemoryEntry,
166:         outcome: AtlasRouteOutcome,
167:         successes: Int = entry.successes,
168:         failures: Int = entry.failures,
169:         rollbackFailures: Int = entry.rollbackFailures,
170:     ) = entry.copy(
171:         lastOutcome = outcome,
172:         lastVerifiedElapsedMs =
173:             if (outcome == AtlasRouteOutcome.VERIFIED) clockMs() else entry.lastVerifiedElapsedMs,
174:         // currentEntry captured one context for the whole update, including counter reset.
175:         successes = successes,
176:         failures = failures,
177:         rollbackFailures = rollbackFailures,
178:     )
179: 
180:     private fun load(target: String, routeId: String): AtlasRouteMemoryEntry? {
181:         val text = io.read(nameOf(target, routeId)) ?: return null
182:         val decoded = decode(text) ?: run {
183:             io.delete(nameOf(target, routeId))
184:             return null
185:         }
186:         return decoded.takeIf { it.target == target && it.routeId == routeId }
187:             ?: run {
188:                 io.delete(nameOf(target, routeId))
189:                 null
190:             }
191:     }
192: 
193:     private fun store(entry: AtlasRouteMemoryEntry) {
194:         pruneIfNeeded(entry.target, entry.routeId)
195:         io.write(nameOf(entry.target, entry.routeId), encode(entry))
196:     }
197: 
198:     /** Keeps the store bounded; the entry being written is always kept. */
199:     private fun pruneIfNeeded(target: String, routeId: String) {
200:         val names = io.list().filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
201:         if (names.size < maxEntries) return
202:         val keep = nameOf(target, routeId)
203:         names.filter { it != keep }.forEach { io.delete(it) }
204:     }
205: 
206:     // ---- explicit-shape serialization ---------------------------------------------------------------
207: 
208:     private fun encode(entry: AtlasRouteMemoryEntry): String {
209:         val root = JSONObject()

(Showing lines 120-209 of 297. Use offset=210 to continue.)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 12.3s)

_Thinking:_



**Tool: board_read**

**Input:**
```json
{
  "since": "board_2c1e677c-0dc5-4393-916c-547b08d6cc84",
  "limit": 10
}
```

**Output:**
```
{"observedAt":1789990153403,"agent":"main","participants":[{"id":"main","sessionID":"ses_f3c539be4ffe75jztLqF6tMSH7","label":"New session - 2026-09-21T11:14:10.075Z","agent":"code","state":"busy"},{"id":"ses_f3c460af9ffegFtpDQxOyzwjXg","sessionID":"ses_f3c460af9ffegFtpDQxOyzwjXg","label":"Build focused Atlas verification (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c52514affezdTXZoKxZL1HmE","sessionID":"ses_f3c52514affezdTXZoKxZL1HmE","label":"Map Max Atlas gaps (@explore subagent)","agent":"explore","state":"completed"}],"messages":[{"id":"board_44b45c6f-6c3f-40cd-9616-cabebdf2bd5a","timestamp":1789989726633,"from":"main","to":"ses_f3c52514affezdTXZoKxZL1HmE","type":"INFO","body":"User clarified priority: autonomous compatibility across devices, diagnosing/discovering/repairing failures, not UI polish. Finish bounded scope recommendation focused on real production discovery -> recovery integration and route-memory generation issue you found. Avoid additional broad scan; return concrete candidate files/consumer/test seams and safety boundary. Research only remains.","fromLabel":"New session - 2026-09-21T11:14:10.075Z","toLabel":"Map Max Atlas gaps (@explore subagent)"},{"id":"board_10ed9190-50e0-426b-8021-cd3ff1737a7f","timestamp":1789989806339,"from":"main","to":"ses_f3c52514affezdTXZoKxZL1HmE","type":"INFO","body":"I will inspect AtlasRouteMemory + production wiring now to implement your evidenced generation/quarantine issue as a prerequisite for autonomous recovery, not repeat the broad map. Please avoid further research on generation wiring; focus final on next real discovery/recovery bridge candidate. No writes in your scope.","fromLabel":"New session - 2026-09-21T11:14:10.075Z","toLabel":"Map Max Atlas gaps (@explore subagent)"},{"id":"board_ba0f7d52-1a23-44c3-a1fc-14ac59215c2a","timestamp":1789989956210,"from":"main","to":"ses_f3c52514affezdTXZoKxZL1HmE","type":"ASK","body":"Please conclude research with the already-established findings now; no further file exploration needed. Need your final concrete discovery -> existing writer integration recommendation to lock implementation scope. I have read memory/DataModule/executor and found generation reset also retains prior rollbackFailures on subsequent success, plus storage pruning clears all routes. Will keep changes bounded rather than blanket rewrite.","fromLabel":"New session - 2026-09-21T11:14:10.075Z","toLabel":"Map Max Atlas gaps (@explore subagent)"},{"id":"board_4ea9e11f-f891-424e-9dd2-90f98f4287de","timestamp":1789990110729,"from":"ses_f3c52514affezdTXZoKxZL1HmE","to":"main","type":"RESULT","body":"Research concluded (no further exploration/writes). Key actual gap: production search `AtlasBackendProvider\\s*\\(|AtlasPlatformProvider\\s*\\(|AtlasDoctor\\.|AtlasFixtureRecorder\\s*\\(` finds only class definitions, no call sites. AtlasDiscovery.kt:76-78 claims backend projection in comments, but actual VM pipeline (AtlasViewModel:159-178, Discovery:113-204) builds catalog-path requests, and AtlasJob.discover:25 -> ReadOnlyProbeAccess.read. Thus provider projections/doctor/recorder exist + tests, but are not wired into production scan. Recommended next compatibility slice after generation prerequisite: ONE target (GPU_FREQUENCY) — wire AtlasBackendProvider.gpu's ambiguity/unit-aware evidence (59-133) through bounded read adapters into scan outcome; only existing reviewed control bindings may enter MinimalPlanner:302-345 -> AtlasAdaptiveExecutor -> HardwareRepairExecutor. Do NOT turn community-bank INFERRED paths directly into writable nodes, or claim new automatic transport fallback: MinimalPlanner still listOf(one binding), thermal alternatives use the same registry transaction with differing desired ceilings. Provider integration first needs read-only tests for ambiguous provider/unit, no duplicate probing, bounded budgets; actual write-path extension then independent safety + device verification. Existing tests: AtlasBackendProviderTest, AtlasResolverTest, AtlasCompletionTest, AtlasAdaptiveExecutorTest, ThermalCeilingRouterTest; tests not run here.","fromLabel":"Map Max Atlas gaps (@explore subagent)","toLabel":"New session - 2026-09-21T11:14:10.075Z"}],"hasMore":false,"cursor":"board_4ea9e11f-f891-424e-9dd2-90f98f4287de"}
```

---

## User

---

## Assistant (Code · GPT-6 Astra · 39.6s)

_Thinking:_

**Evaluating memory roadmap**

I’m thinking about how to flag memory hardening in the final roadmap. I can't really claim self-healing capabilities yet. Maybe the user’s ultimate goal is about setting expectations properly; it's okay for the first meaningful step to be acceptable rather than immediate. I should ensure that we have tests and a factory source since there aren't any actual settings installed to verify, as noted.

**Tool: apply_patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt\n@@\n         // the route again immediately after its first successful recovery in the new boot.\n         return AtlasRouteMemoryEntry(\n-        target = target,\n-        routeId = routeId,\n-        lastOutcome = AtlasRouteOutcome.FAILED,\n-        lastVerifiedElapsedMs = null,\n-        bootGeneration = boot,\n-        privilegeGeneration = privilege,\n-        successes = 0,\n-        failures = 0,\n-        rollbackFailures = 0,\n+            target = target,\n+            routeId = routeId,\n+            lastOutcome = AtlasRouteOutcome.FAILED,\n+            lastVerifiedElapsedMs = null,\n+            bootGeneration = boot,\n+            privilegeGeneration = privilege,\n+            successes = 0,\n+            failures = 0,\n+            rollbackFailures = 0,\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt\n@@\n     fun `quarantine expires with the boot because the vendor state is rebuilt`() {\n@@\n         )\n     }\n+\n+    @Test\n+    fun `successful recovery after reboot does not inherit the old rollback failure`() {\n+        var boot = 7L\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })\n+        memory.noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+\n+        boot = 8L\n+        memory.noteVerified(\"gpu_frequency\", \"gpu.frequency.arbiter\")\n+\n+        assertTrue(memory.quarantinedRoutes(\"gpu_frequency\").isEmpty())\n+        assertEquals(\"gpu.frequency.arbiter\", memory.preferredRoute(\"gpu_frequency\"))\n+        val entry = memory.entries().single()\n+        assertEquals(8L, entry.bootGeneration)\n+        assertEquals(1, entry.successes)\n+        assertEquals(0, entry.failures)\n+        assertEquals(0, entry.rollbackFailures)\n+    }\n+\n+    @Test\n+    fun `clean failure after reboot does not revive a quarantine from the previous boot`() {\n+        var boot = 7L\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })\n+        memory.noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+\n+        boot = 8L\n+        memory.noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = true)\n+\n+        assertTrue(memory.quarantinedRoutes(\"gpu_frequency\").isEmpty())\n+        assertEquals(1, memory.entries().single().failures)\n+        assertEquals(0, memory.entries().single().rollbackFailures)\n+    }\n+\n+    @Test\n+    fun `restarting the process in the same boot preserves quarantine`() {\n+        val io = InMemoryIo()\n+        AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })\n+            .noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+\n+        val restarted = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 7L })\n+\n+        assertEquals(setOf(\"gpu.frequency.arbiter\"), restarted.quarantinedRoutes(\"gpu_frequency\"))\n+    }\n+\n+    @Test\n+    fun `unknown current boot does not release an existing quarantine`() {\n+        var boot = 7L\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })\n+        memory.noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+\n+        boot = 0L\n+\n+        assertEquals(setOf(\"gpu.frequency.arbiter\"), memory.quarantinedRoutes(\"gpu_frequency\"))\n+    }\n+\n+    @Test\n+    fun `legacy unknown boot quarantine is not silently cleared by a known boot`() {\n+        val io = InMemoryIo()\n+        val legacy = AtlasRouteMemory(io, clockMs = { 1_000L })\n+        legacy.noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+        val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 8L })\n+\n+        assertEquals(setOf(\"gpu.frequency.arbiter\"), current.quarantinedRoutes(\"gpu_frequency\"))\n+    }\n+\n+    @Test\n+    fun `an unknown boot cannot advertise a remembered route as verified`() {\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L })\n+        memory.noteVerified(\"gpu_frequency\", \"gpu.frequency.arbiter\")\n+\n+        assertNull(memory.preferredRoute(\"gpu_frequency\"))\n+    }\n+\n+    @Test\n+    fun `a later success in the same boot cannot erase an unsafe rollback`() {\n+        val memory = memory()\n+        memory.noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+        memory.noteVerified(\"gpu_frequency\", \"gpu.frequency.arbiter\")\n+\n+        assertEquals(setOf(\"gpu.frequency.arbiter\"), memory.quarantinedRoutes(\"gpu_frequency\"))\n+        assertNull(memory.preferredRoute(\"gpu_frequency\"))\n+    }\n+\n+    @Test\n+    fun `verified preference expires at reboot even when new uptime exceeds the old timestamp`() {\n+        var boot = 7L\n+        var now = 1_000L\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { boot })\n+        memory.noteVerified(\"gpu_frequency\", \"gpu.frequency.arbiter\")\n+\n+        boot = 8L\n+        now = 100_000L\n+\n+        assertNull(memory.preferredRoute(\"gpu_frequency\"))\n+    }\n+\n+    @Test\n+    fun `unknown and invalid boot counts stay unknown while zero is a real boot`() {\n+        assertEquals(0L, AtlasRouteMemory.generationForBootCount(null))\n+        assertEquals(0L, AtlasRouteMemory.generationForBootCount(-1))\n+        assertEquals(1L, AtlasRouteMemory.generationForBootCount(0))\n+        assertEquals(2L, AtlasRouteMemory.generationForBootCount(1))\n+        assertEquals(2_147_483_648L, AtlasRouteMemory.generationForBootCount(Int.MAX_VALUE))\n+    }\n@@\n-            bootGeneration = { 0L },\n+            bootGeneration = { 1L },\n@@\n         privilege = 2L\n         assertTrue(memory.quarantinedRoutes(\"gpu_frequency\").isEmpty())\n+\n+        memory.noteVerified(\"gpu_frequency\", \"gpu.frequency.arbiter\")\n+        assertTrue(memory.quarantinedRoutes(\"gpu_frequency\").isEmpty())\n+        assertEquals(0, memory.entries().single().rollbackFailures)\n@@\n-        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { 0L })\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { 1L })\n@@\n-        val memory = AtlasRouteMemory(io, clockMs = { 0L })\n+        val memory = AtlasRouteMemory(io, clockMs = { 0L }, bootGeneration = { 1L })\n         memory.noteVerified(\"../../etc/passwd\", \"cpu.frequency.arbiter\")\n@@\n-        AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { 0L })\n+        AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { 1L })\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveExecutorTest.kt\n@@\n-        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 0L })\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })\n@@\n-        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 0L })\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })\n@@\n-        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 0L })\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })\n@@\n-        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 0L })\n+        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })\n@@\n         assertEquals(\"route-first\", memory.preferredRoute(\"cpu_frequency\"))\n     }\n+\n+    @Test\n+    fun `a reboot recovers a quarantined route and subsequent calls keep using it`() {\n+        val io = InMemoryIo()\n+        val firstBoot = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })\n+        val firstExecutor = AtlasAdaptiveExecutor(port { failed(it, rollbackVerified = false) }, firstBoot)\n+        assertTrue(firstExecutor.execute(intent(), listOf(binding(\"route-first\"))).fallbackStopped)\n+\n+        val calls = mutableListOf<String>()\n+        val afterReboot = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 8L })\n+        val recovered = AtlasAdaptiveExecutor(port { request ->\n+            calls += request.routeId\n+            success(request)\n+        }, afterReboot)\n+\n+        repeat(2) {\n+            assertTrue(recovered.execute(intent(), listOf(binding(\"route-first\"))).successful)\n+        }\n+        assertEquals(listOf(\"route-first\", \"route-first\"), calls)\n+        assertEquals(0, afterReboot.entries().single().rollbackFailures)\n+    }\n+\n+    @Test\n+    fun `process restart with unknown boot does not retry a quarantined writer`() {\n+        val io = InMemoryIo()\n+        AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })\n+            .noteFailed(\"cpu_frequency\", \"route-first\", rollbackVerified = false)\n+        var called = false\n+        val executor = AtlasAdaptiveExecutor(port {\n+            called = true\n+            success(it)\n+        }, AtlasRouteMemory(io, clockMs = { 2_000L }))\n+\n+        val result = executor.execute(intent(), listOf(binding(\"route-first\")))\n+\n+        assertFalse(called)\n+        assertFalse(result.successful)\n+        assertEquals(listOf(\"route-first\" to \"route-quarantined-after-unverified-rollback\"), result.skipped)\n+    }\n*** End Patch"
}
```

**Output:**
```
Success. Updated the following files:
M manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
M manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt
M manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveExecutorTest.kt
```

---

## Assistant (Code · GPT-6 Astra · 28.6s)

_Thinking:_



**Tool: bash**

**Input:**
```json
{
  "command": "curl --fail --location --connect-timeout 15 --max-time 60 --output /tmp/kilo/json-20260814.jar https://repo.maven.apache.org/maven2/org/json/json/20260814/json-20260814.jar",
  "description": "Fetch declared JSON test runtime outside repository",
  "timeout": 65000
}
```

**Output:**
```
  % Total    % Received % Xferd  Average Speed  Time    Time    Time   Current
                                 Dload  Upload  Total   Spent   Left   Speed
  0      0   0      0   0      0      0      0                              0100  89800 100  89800   0      0 156.1k      0                              0100  89800 100  89800   0      0 156.0k      0                              0100  89800 100  89800   0      0 156.0k      0                              0

```

**Tool: bash**

**Input:**
```json
{
  "command": "git diff --stat",
  "description": "Inspect scope of Atlas recovery changes"
}
```

**Output:**
```
 docs/ai/NEXT_TASK.md                               |  21 +++-
 manager/app/src/main/java/nd/max/AppMonitor.kt     |  21 +---
 .../app/src/main/java/nd/max/core/di/DataModule.kt |  16 +--
 .../java/nd/max/core/hardware/AtlasRouteMemory.kt  |  80 +++++++++-----
 .../max/core/hardware/AtlasAdaptiveExecutorTest.kt |  47 ++++++++-
 .../nd/max/core/hardware/AtlasRouteMemoryTest.kt   | 116 ++++++++++++++++++++-
 6 files changed, 233 insertions(+), 68 deletions(-)

```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/kt_balance.py --assert",
  "description": "Check structural integrity after recovery changes",
  "timeout": 120000
}
```

**Output:**
```
توازن البنية: 757 ملفًا · عوائق 0

```

**Tool: board_read**

**Input:**
```json
{
  "since": "board_4ea9e11f-f891-424e-9dd2-90f98f4287de",
  "limit": 10
}
```

**Output:**
```
{"observedAt":1789990216250,"agent":"main","participants":[{"id":"main","sessionID":"ses_f3c539be4ffe75jztLqF6tMSH7","label":"تطوير Max أطلس في المشروع","agent":"code","state":"busy"},{"id":"ses_f3c460af9ffegFtpDQxOyzwjXg","sessionID":"ses_f3c460af9ffegFtpDQxOyzwjXg","label":"Build focused Atlas verification (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c52514affezdTXZoKxZL1HmE","sessionID":"ses_f3c52514affezdTXZoKxZL1HmE","label":"Map Max Atlas gaps (@explore subagent)","agent":"explore","state":"completed"}],"messages":[],"hasMore":false,"cursor":"board_4ea9e11f-f891-424e-9dd2-90f98f4287de"}
```

---

## Assistant (Code · GPT-6 Astra · 15.8s)

_Thinking:_

**Considering memory adjustments**

I need to change the default semantics tests related to `ThermalCeilingRouterTest`. It might be useful to add a new fake memory default. I’m thinking about scanning both production and current tests to ensure all scoped memory calls are accounted for. This way, I can identify any inconsistencies or necessary improvements. It feels like a thorough approach, and I want to make sure everything aligns properly!

**Tool: grep**

**Input:**
```json
{
  "path": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test",
  "pattern": "AtlasRouteMemory\\(",
  "limit": 80
}
```

**Output:**
```
Found 27 matches
/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/ThermalCeilingRouterTest.kt:
  Line 262:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L })

  Line 299:             memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }),


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt:
  Line 41:         val memory = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { boot })

  Line 55:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })

  Line 73:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })

  Line 87:         AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })

  Line 90:         val restarted = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 7L })

  Line 98:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })

  Line 109:         val legacy = AtlasRouteMemory(io, clockMs = { 1_000L })

  Line 111:         val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 8L })

  Line 118:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L })

  Line 138:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { boot })

  Line 159:         val memory = AtlasRouteMemory(

  Line 178:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { 1L })

  Line 190:         val memory = AtlasRouteMemory(io, clockMs = { 0L })

  Line 206:         val memory = AtlasRouteMemory(io, clockMs = { 0L })

  Line 214:         val memory = AtlasRouteMemory(io, clockMs = { 0L }, bootGeneration = { 1L })

  Line 224:         val memory = AtlasRouteMemory(io, clockMs = { 0L }, maxEntries = 4)

  Line 231:         AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { 1L })


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveExecutorTest.kt:
  Line 60:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })

  Line 80:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })

  Line 98:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })

  Line 119:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }, bootGeneration = { 1L })

  Line 130:         val firstBoot = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })

  Line 135:         val afterReboot = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 8L })

  Line 151:         AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })

  Line 157:         }, AtlasRouteMemory(io, clockMs = { 2_000L }))

```

**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c460af9ffegFtpDQxOyzwjXg",
  "type": "INFO",
  "body": "Declared org.json:json:20260814 downloaded successfully from Maven Central to /tmp/kilo/json-20260814.jar (89,800 bytes), available for runner --json-jar. Main memory/executor regression tests now updated. Factory create(context,clockMs) lazily reads Settings.Global.BOOT_COUNT maps valid count+1, failure/negative unknown0. Hilt+AppMonitor now both call AtlasRouteMemoryFactory.create. New boot resets old counters; unknown boot preserves quarantine/no verified preference. No Android SDK at /root/android-sdk or declared /home/codespace path, JDK17 exists /usr/lib/jvm/java-17-openjdk-arm64."
}
```

**Output:**
```
{"id":"board_ba5dd655-89c5-47cd-87e5-02426773b415","timestamp":1789990237108,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"Declared org.json:json:20260814 downloaded successfully from Maven Central to /tmp/kilo/json-20260814.jar (89,800 bytes), available for runner --json-jar. Main memory/executor regression tests now updated. Factory create(context,clockMs) lazily reads Settings.Global.BOOT_COUNT maps valid count+1, failure/negative unknown0. Hilt+AppMonitor now both call AtlasRouteMemoryFactory.create. New boot resets old counters; unknown boot preserves quarantine/no verified preference. No Android SDK at /root/android-sdk or declared /home/codespace path, JDK17 exists /usr/lib/jvm/java-17-openjdk-arm64.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)","availability":{"observedAt":1789990237110,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"retry"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

**Tool: bash**

**Input:**
```json
{
  "command": "git diff -- manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt manager/app/src/main/java/nd/max/core/di/DataModule.kt manager/app/src/main/java/nd/max/AppMonitor.kt",
  "description": "Review production recovery lifecycle and wiring diff"
}
```

**Output:**
```
diff --git a/manager/app/src/main/java/nd/max/AppMonitor.kt b/manager/app/src/main/java/nd/max/AppMonitor.kt
index 6755425..42d1628 100644
--- a/manager/app/src/main/java/nd/max/AppMonitor.kt
+++ b/manager/app/src/main/java/nd/max/AppMonitor.kt
@@ -36,7 +36,6 @@ import java.lang.reflect.Field
 import java.lang.reflect.Method
 import java.nio.channels.FileChannel
 import java.nio.channels.FileLock
-import java.nio.file.Paths
 import java.nio.file.StandardOpenOption
 import org.lsposed.hiddenapibypass.HiddenApiBypass
 import android.hardware.display.DisplayManager
@@ -48,14 +47,13 @@ import java.text.SimpleDateFormat
 import java.util.Date
 import java.util.Locale
 import nd.max.core.atlas.AtlasControlTarget
-import nd.max.core.atlas.AtlasFileStoreIo
 import nd.max.core.diagnostics.DeviceFacts
 import nd.max.core.diagnostics.LogHeader
 import nd.max.core.diagnostics.LogSettingsDigest
 import nd.max.ui.util.PerAppKernelUtil
 import nd.max.ui.util.ProfilePresetStore
 import nd.max.core.hardware.AtlasAdaptiveExecutor
-import nd.max.core.hardware.AtlasRouteMemory
+import nd.max.core.hardware.AtlasRouteMemoryFactory
 import nd.max.core.hardware.CpuHardwareBackend
 import nd.max.core.hardware.GpuHardwareBackend
 import nd.max.core.hardware.HardwareRepairExecutor
@@ -322,23 +320,12 @@ object AppMonitor {
      * `PerAppControlRegistry` (مُحكِّمان في عملية واحدة = جداول طلبات متنافرة)، بل غلاف رقيق حول
      * نفس البوّابة، والملكية تبقى في مكان واحد.
      *
-     * وذاكرة المسارات في نفس مجلد الواجهة (`noBackupFilesDir/atlas/routes`) عبر ثابت مشترك في
-     * `AtlasFileStoreIo` — فمساران مختلفان يعنيان ذاكرتين لا واحدة.
-     *
-     * **حدّ معروف ومعلن:** `bootGeneration`/`privilegeGeneration` تُترك على الافتراضي (صفر) كما
-     * في مزوّد Hilt في الواجهة. فالنتيجة أن حجر مسار بعد استرجاع غير مؤكَّد يبقى حتى يُقلَّم المخزن،
-     * لا حتى الإقلاع — أي أن قاعدة «الحجر ينتهي بالإقلاع» في `AtlasRouteMemory` غير مُشغَّلة بعد في
-     * المسارين. ربط جيل إقلاع حقيقي تغيير يمسّ الواجهة والرفيق معًا، فلم يُفعل ضمنيًّا هنا.
+     * المصنع المشترك مع Hilt يربط الذاكرة بنفس المجلد ونفس عدّاد إقلاع الجهاز.
+     * إعادة تشغيل الرفيق ليست إقلاعًا جديدًا، وتعذّر قراءة العدّاد لا يرفع الحجر.
      */
     private fun configureThermalRouter(controlContext: Context) {
         runCatching {
-            val io = AtlasFileStoreIo(
-                Paths.get(
-                    AtlasFileStoreIo.directoryFor(controlContext.noBackupFilesDir).absolutePath,
-                    AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME,
-                ).toAbsolutePath(),
-            )
-            val memory = AtlasRouteMemory(io = io, clockMs = { android.os.SystemClock.elapsedRealtime() })
+            val memory = AtlasRouteMemoryFactory.create(controlContext) { android.os.SystemClock.elapsedRealtime() }
             thermalRouter = ThermalCeilingRouter(
                 registry = hardwareControlRegistry,
                 adaptive = AtlasAdaptiveExecutor(HardwareRepairExecutor(mutationGate), memory),
diff --git a/manager/app/src/main/java/nd/max/core/di/DataModule.kt b/manager/app/src/main/java/nd/max/core/di/DataModule.kt
index cd0ec99..e10c01c 100644
--- a/manager/app/src/main/java/nd/max/core/di/DataModule.kt
+++ b/manager/app/src/main/java/nd/max/core/di/DataModule.kt
@@ -25,6 +25,7 @@ import nd.max.core.hardware.AtlasAdaptiveReadTransport
 import nd.max.core.hardware.AtlasPrivilegedReadTransport
 import nd.max.core.hardware.AtlasReadBudget
 import nd.max.core.hardware.AtlasRouteMemory
+import nd.max.core.hardware.AtlasRouteMemoryFactory
 import nd.max.core.hardware.HardwareControlArbiter
 import nd.max.core.hardware.HardwareRepairExecutor
 import nd.max.core.hardware.AtlasReadTransport
@@ -105,13 +106,7 @@ object DataModule {
     fun provideAtlasRouteMemory(
         @ApplicationContext context: Context,
         clockMs: () -> Long,
-    ): AtlasRouteMemory = AtlasRouteMemory(
-        io = AtlasFileStoreIo(
-            Paths.get(AtlasFileStoreIo.directoryFor(context.noBackupFilesDir).absolutePath, ROUTE_MEMORY_DIR)
-                .toAbsolutePath(),
-        ),
-        clockMs = clockMs,
-    )
+    ): AtlasRouteMemory = AtlasRouteMemoryFactory.create(context, clockMs)
 
     @Provides
     @Singleton
@@ -225,11 +220,4 @@ object DataModule {
         dispatcher = dispatchers.io,
     )
 
-    /**
-     * Subdirectory of the Atlas store that holds learned routes, kept apart from evidence.
-     *
-     * ويشير إلى ثابت `AtlasFileStoreIo` بدل نصّ ثانٍ: الرفيق (`AppMonitor`) يكتب المسارات في
-     * عملية أخرى، وذاكرة مسارات بمجلدين مختلفين ليست ذاكرة واحدة.
-     */
-    private const val ROUTE_MEMORY_DIR = AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME
 }
diff --git a/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt b/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
index 3a87ed9..a3fa3f6 100644
--- a/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
+++ b/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
@@ -33,15 +33,17 @@ data class AtlasRouteMemoryEntry(
     val failures: Int,
     val rollbackFailures: Int,
 ) {
-    /** Whether this route may still be attempted in the generation the entry was recorded in. */
+    /** An unknown boot cannot prove that a previous unsafe state has gone away. */
     fun isQuarantinedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =
         rollbackFailures > 0 &&
-            this.bootGeneration == bootGeneration &&
-            this.privilegeGeneration == privilegeGeneration
+            (this.bootGeneration == 0L || bootGeneration == 0L ||
+                (this.bootGeneration == bootGeneration && this.privilegeGeneration == privilegeGeneration))
 
     fun isVerifiedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =
         lastOutcome == AtlasRouteOutcome.VERIFIED &&
             successes > 0 &&
+            rollbackFailures == 0 &&
+            bootGeneration > 0L &&
             this.bootGeneration == bootGeneration &&
             this.privilegeGeneration == privilegeGeneration
 }
@@ -80,19 +82,27 @@ class AtlasRouteMemory(
     }
 
     /** The last route verified on this device for [target], or `null` when there is none. */
-    fun preferredRoute(target: String): String? = entries()
-        .filter { it.target == target && it.isVerifiedIn(bootGeneration(), privilegeGeneration()) }
-        .maxByOrNull { it.lastVerifiedElapsedMs ?: Long.MIN_VALUE }
-        ?.routeId
+    fun preferredRoute(target: String): String? {
+        val boot = bootGeneration()
+        val privilege = privilegeGeneration()
+        return entries()
+            .filter { it.target == target && it.isVerifiedIn(boot, privilege) }
+            .maxByOrNull { it.lastVerifiedElapsedMs ?: Long.MIN_VALUE }
+            ?.routeId
+    }
 
     /** Routes that must not be attempted again in this boot generation. */
-    fun quarantinedRoutes(target: String): Set<String> = entries()
-        .filter { it.target == target && it.isQuarantinedIn(bootGeneration(), privilegeGeneration()) }
-        .map { it.routeId }
-        .toSet()
+    fun quarantinedRoutes(target: String): Set<String> {
+        val boot = bootGeneration()
+        val privilege = privilegeGeneration()
+        return entries()
+            .filter { it.target == target && it.isQuarantinedIn(boot, privilege) }
+            .map { it.routeId }
+            .toSet()
+    }
 
     fun noteVerified(target: String, routeId: String) {
-        val entry = load(target, routeId) ?: newEntry(target, routeId)
+        val entry = currentEntry(target, routeId)
         store(copy(entry, AtlasRouteOutcome.VERIFIED, successes = entry.successes + 1))
     }
 
@@ -101,7 +111,7 @@ class AtlasRouteMemory(
      * not restore the baseline" is the one failure that changes what may be attempted next.
      */
     fun noteFailed(target: String, routeId: String, rollbackVerified: Boolean) {
-        val entry = load(target, routeId) ?: newEntry(target, routeId)
+        val entry = currentEntry(target, routeId)
         store(
             copy(
                 entry,
@@ -126,17 +136,30 @@ class AtlasRouteMemory(
 
     // ---- helpers -----------------------------------------------------------------------------------
 
-    private fun newEntry(target: String, routeId: String) = AtlasRouteMemoryEntry(
-        target = target,
-        routeId = routeId,
-        lastOutcome = AtlasRouteOutcome.FAILED,
-        lastVerifiedElapsedMs = null,
-        bootGeneration = bootGeneration(),
-        privilegeGeneration = privilegeGeneration(),
-        successes = 0,
-        failures = 0,
-        rollbackFailures = 0,
-    )
+    private fun currentEntry(target: String, routeId: String): AtlasRouteMemoryEntry {
+        val boot = bootGeneration()
+        val privilege = privilegeGeneration()
+        val previous = load(target, routeId)
+        if (previous != null && (
+                (previous.bootGeneration == boot && previous.privilegeGeneration == privilege) ||
+                    previous.isQuarantinedIn(boot, privilege)
+            )) {
+            return previous.copy(bootGeneration = boot, privilegeGeneration = privilege)
+        }
+        // Counters describe one generation. Copying an old rollback failure here would quarantine
+        // the route again immediately after its first successful recovery in the new boot.
+        return AtlasRouteMemoryEntry(
+            target = target,
+            routeId = routeId,
+            lastOutcome = AtlasRouteOutcome.FAILED,
+            lastVerifiedElapsedMs = null,
+            bootGeneration = boot,
+            privilegeGeneration = privilege,
+            successes = 0,
+            failures = 0,
+            rollbackFailures = 0,
+        )
+    }
 
     private fun copy(
         entry: AtlasRouteMemoryEntry,
@@ -148,10 +171,7 @@ class AtlasRouteMemory(
         lastOutcome = outcome,
         lastVerifiedElapsedMs =
             if (outcome == AtlasRouteOutcome.VERIFIED) clockMs() else entry.lastVerifiedElapsedMs,
-        // The generations are refreshed on every write: the record always describes the context it was
-        // last observed in, which is what makes the boot/privilege checks meaningful.
-        bootGeneration = bootGeneration(),
-        privilegeGeneration = privilegeGeneration(),
+        // currentEntry captured one context for the whole update, including counter reset.
         successes = successes,
         failures = failures,
         rollbackFailures = rollbackFailures,
@@ -238,6 +258,10 @@ class AtlasRouteMemory(
     companion object {
         const val SCHEMA: Int = 1
 
+        /** Zero means unknown (including legacy records); Android's valid zero count maps to one. */
+        fun generationForBootCount(bootCount: Int?): Long =
+            bootCount?.takeIf { it >= 0 }?.toLong()?.plus(1L) ?: 0L
+
         const val NAME_PREFIX: String = "route."
         const val NAME_SUFFIX: String = ".json"
         const val NAME_SEPARATOR: String = "."

```

---

## Assistant (Code · GPT-6 Astra · 23.3s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/home/codespace/android-sdk",
  "limit": 20
}
```

**Error:**
```
File not found: /home/codespace/android-sdk
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/ThermalCeilingRouterTest.kt",
  "offset": 240,
  "limit": 105
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/ThermalCeilingRouterTest.kt</path>
<type>file</type>
<content>
240:         key: String,
241:         desired: String,
242:         apply: (String) -> Boolean,
243:         read: () -> String?,
244:     ): PerAppControlRegistry {
245:         val registry = PerAppControlRegistry(gate, confirmationSamples = 1, confirmationIntervalMs = 0L, sleep = {})
246:         assertTrue(registry.ownValue(key, desired, apply, read))
247:         return registry
248:     }
249: 
250:     /**
251:      * مسار محجور يُعلَن ولا يُجرَّب.
252:      *
253:      * وهذه هي الحالة التي كانت **صامتة تمامًا**: مسار حُجر لأنه ترك خط الأساس غير مؤكَّد في إقلاع
254:      * سابق، فيتخطّاه Atlas ولا يُجرّبه — فيبدو للحارس أن «لا شيء حدث»، ويقرأ من يُصلح العطل سطرًا
255:      * يقول فشلًا بلا سبب. فالاختبار يُثبت الأمرين معًا: أن المحجور لا يُنفَّذ، وأن تخطّيه يُبلَّغ.
256:      */
257:     @Test
258:     fun `a route quarantined in this boot is skipped and the skip is reported`() {
259:         var live = "754000000"
260:         val gate = HardwareControlArbiter()
261:         val registry = registryOwning(gate, KEY, "754000000", { live = it; true }, { live })
262:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L })
263:         memory.noteFailed("gpu_frequency", ThermalCeilingRoutes.PLATFORM_ROUTE_ID, rollbackVerified = false)
264:         val router = ThermalCeilingRouter(
265:             registry = registry,
266:             adaptive = AtlasAdaptiveExecutor(
267:                 repairExecutor = HardwareRepairExecutor(gate),
268:                 memory = memory,
269:             ),
270:         )
271: 
272:         val outcome = router.apply(
273:             key = KEY,
274:             target = AtlasControlTarget.GPU_FREQUENCY,
275:             packageName = "com.example.game",
276:             userCeiling = "754000000",
277:             ladder = ladder,
278:             pressure = ThermalGuard.Pressure.SEVERE,
279:         )
280: 
281:         assertEquals(
282:             "المسار الذي حُجر لا يُجرَّب: الطلب يذهب إلى سقف المستخدم",
283:             ThermalCeilingRoutes.STATIC_ROUTE_ID,
284:             outcome.routeId,
285:         )
286:         assertTrue(outcome.verified)
287:         assertEquals("754000000", live)
288:         assertEquals(
289:             "وتخطّي المسار يُعلَن بسبب ثابت لا بصمت",
290:             listOf(ThermalCeilingRoutes.PLATFORM_ROUTE_ID to "route-quarantined-after-unverified-rollback"),
291:             outcome.skipped,
292:         )
293:     }
294: 
295:     private fun routerWith(registry: PerAppControlRegistry, gate: HardwareControlArbiter) = ThermalCeilingRouter(
296:         registry = registry,
297:         adaptive = AtlasAdaptiveExecutor(
298:             repairExecutor = HardwareRepairExecutor(gate),
299:             memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 0L }),
300:         ),
301:     )
302: 
303:     private class InMemoryIo : AtlasStoreIo {
304:         private val files = mutableMapOf<String, String>()
305:         override fun list(): List<String> = files.keys.sorted()
306:         override fun read(name: String): String? = files[name]
307:         override fun write(name: String, text: String): Boolean {
308:             files[name] = text
309:             return true
310:         }
311: 
312:         override fun delete(name: String): Boolean = files.remove(name) != null
313:     }
314: 
315:     private companion object {
316:         const val KEY = "gpu_frequency:kgsl-3d0"
317:     }
318: }

(End of file - total 318 lines)
</content>
```

**Tool: bash**

**Input:**
```json
{
  "command": "git status --short",
  "description": "Check all modified and newly added files"
}
```

**Output:**
```
 M docs/ai/NEXT_TASK.md
 M manager/app/src/main/java/nd/max/AppMonitor.kt
 M manager/app/src/main/java/nd/max/core/di/DataModule.kt
 M manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
 M manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveExecutorTest.kt
 M manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt
?? manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt

```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt",
  "limit": 260
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import nd.max.core.atlas.AtlasStoreIo
4: import org.junit.Assert.assertEquals
5: import org.junit.Assert.assertFalse
6: import org.junit.Assert.assertNull
7: import org.junit.Assert.assertTrue
8: import org.junit.Test
9: 
10: class AtlasRouteMemoryTest {
11: 
12:     @Test
13:     fun `a verified route becomes this device's preferred route`() {
14:         val memory = memory()
15:         memory.noteVerified("cpu_frequency", "cpu.frequency.arbiter")
16: 
17:         assertEquals("cpu.frequency.arbiter", memory.preferredRoute("cpu_frequency"))
18:     }
19: 
20:     @Test
21:     fun `a clean failure does not quarantine the route`() {
22:         val memory = memory()
23:         memory.noteFailed("cpu_frequency", "cpu.frequency.arbiter", rollbackVerified = true)
24: 
25:         assertTrue(memory.quarantinedRoutes("cpu_frequency").isEmpty())
26:         assertNull(memory.preferredRoute("cpu_frequency"))
27:     }
28: 
29:     @Test
30:     fun `an unverified rollback quarantines the route inside the same generation`() {
31:         val memory = memory()
32:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
33: 
34:         assertEquals(setOf("gpu.frequency.arbiter"), memory.quarantinedRoutes("gpu_frequency"))
35:     }
36: 
37:     @Test
38:     fun `quarantine expires with the boot because the vendor state is rebuilt`() {
39:         var boot = 7L
40:         val io = InMemoryIo()
41:         val memory = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { boot })
42:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
43:         assertEquals(1, memory.quarantinedRoutes("gpu_frequency").size)
44: 
45:         boot = 8L
46:         assertTrue(
47:             "a route stranded in an unknown state last boot must be retried, not banned forever",
48:             memory.quarantinedRoutes("gpu_frequency").isEmpty(),
49:         )
50:     }
51: 
52:     @Test
53:     fun `successful recovery after reboot does not inherit the old rollback failure`() {
54:         var boot = 7L
55:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })
56:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
57: 
58:         boot = 8L
59:         memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")
60: 
61:         assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())
62:         assertEquals("gpu.frequency.arbiter", memory.preferredRoute("gpu_frequency"))
63:         val entry = memory.entries().single()
64:         assertEquals(8L, entry.bootGeneration)
65:         assertEquals(1, entry.successes)
66:         assertEquals(0, entry.failures)
67:         assertEquals(0, entry.rollbackFailures)
68:     }
69: 
70:     @Test
71:     fun `clean failure after reboot does not revive a quarantine from the previous boot`() {
72:         var boot = 7L
73:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })
74:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
75: 
76:         boot = 8L
77:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = true)
78: 
79:         assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())
80:         assertEquals(1, memory.entries().single().failures)
81:         assertEquals(0, memory.entries().single().rollbackFailures)
82:     }
83: 
84:     @Test
85:     fun `restarting the process in the same boot preserves quarantine`() {
86:         val io = InMemoryIo()
87:         AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })
88:             .noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
89: 
90:         val restarted = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 7L })
91: 
92:         assertEquals(setOf("gpu.frequency.arbiter"), restarted.quarantinedRoutes("gpu_frequency"))
93:     }
94: 
95:     @Test
96:     fun `unknown current boot does not release an existing quarantine`() {
97:         var boot = 7L
98:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })
99:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
100: 
101:         boot = 0L
102: 
103:         assertEquals(setOf("gpu.frequency.arbiter"), memory.quarantinedRoutes("gpu_frequency"))
104:     }
105: 
106:     @Test
107:     fun `legacy unknown boot quarantine is not silently cleared by a known boot`() {
108:         val io = InMemoryIo()
109:         val legacy = AtlasRouteMemory(io, clockMs = { 1_000L })
110:         legacy.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
111:         val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 8L })
112: 
113:         assertEquals(setOf("gpu.frequency.arbiter"), current.quarantinedRoutes("gpu_frequency"))
114:     }
115: 
116:     @Test
117:     fun `an unknown boot cannot advertise a remembered route as verified`() {
118:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L })
119:         memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")
120: 
121:         assertNull(memory.preferredRoute("gpu_frequency"))
122:     }
123: 
124:     @Test
125:     fun `a later success in the same boot cannot erase an unsafe rollback`() {
126:         val memory = memory()
127:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
128:         memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")
129: 
130:         assertEquals(setOf("gpu.frequency.arbiter"), memory.quarantinedRoutes("gpu_frequency"))
131:         assertNull(memory.preferredRoute("gpu_frequency"))
132:     }
133: 
134:     @Test
135:     fun `verified preference expires at reboot even when new uptime exceeds the old timestamp`() {
136:         var boot = 7L
137:         var now = 1_000L
138:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { boot })
139:         memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")
140: 
141:         boot = 8L
142:         now = 100_000L
143: 
144:         assertNull(memory.preferredRoute("gpu_frequency"))
145:     }
146: 
147:     @Test
148:     fun `unknown and invalid boot counts stay unknown while zero is a real boot`() {
149:         assertEquals(0L, AtlasRouteMemory.generationForBootCount(null))
150:         assertEquals(0L, AtlasRouteMemory.generationForBootCount(-1))
151:         assertEquals(1L, AtlasRouteMemory.generationForBootCount(0))
152:         assertEquals(2L, AtlasRouteMemory.generationForBootCount(1))
153:         assertEquals(2_147_483_648L, AtlasRouteMemory.generationForBootCount(Int.MAX_VALUE))
154:     }
155: 
156:     @Test
157:     fun `a failure in another privilege generation does not quarantine the current one`() {
158:         var privilege = 1L
159:         val memory = AtlasRouteMemory(
160:             InMemoryIo(),
161:             clockMs = { 1_000L },
162:             bootGeneration = { 1L },
163:             privilegeGeneration = { privilege },
164:         )
165:         memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
166: 
167:         privilege = 2L
168:         assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())
169: 
170:         memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")
171:         assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())
172:         assertEquals(0, memory.entries().single().rollbackFailures)
173:     }
174: 
175:     @Test
176:     fun `the latest verified route wins`() {
177:         var now = 100L
178:         val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { 1L })
179:         memory.noteVerified("cpu_frequency", "cpu.frequency.arbiter")
180:         now = 500L
181:         memory.noteVerified("cpu_frequency", "cpu.frequency.vendor")
182: 
183:         assertEquals("cpu.frequency.vendor", memory.preferredRoute("cpu_frequency"))
184:     }
185: 
186:     @Test
187:     fun `a corrupt entry is discarded rather than half-trusted`() {
188:         val io = InMemoryIo()
189:         io.write("route.cpu_frequency.cpu.frequency.arbiter.json", "{\"schema\":1,\"target\":")
190:         val memory = AtlasRouteMemory(io, clockMs = { 0L })
191: 
192:         assertTrue(memory.entries().isEmpty())
193:         assertNull(memory.preferredRoute("cpu_frequency"))
194:     }
195: 
196:     @Test
197:     fun `an unknown schema is refused`() {
198:         val io = InMemoryIo()
199:         io.write(
200:             "route.gpu_frequency.gpu.frequency.arbiter.json",
201:             """
202:             {"schema":99,"target":"gpu_frequency","route":"gpu.frequency.arbiter","outcome":"VERIFIED",
203:              "verifiedAt":1,"boot":0,"privilege":0,"successes":1,"failures":0,"rollbackFailures":0}
204:             """.trimIndent(),
205:         )
206:         val memory = AtlasRouteMemory(io, clockMs = { 0L })
207: 
208:         assertTrue(memory.entries().isEmpty())
209:     }
210: 
211:     @Test
212:     fun `a target with unusual characters cannot name a file outside the store`() {
213:         val io = InMemoryIo()
214:         val memory = AtlasRouteMemory(io, clockMs = { 0L }, bootGeneration = { 1L })
215:         memory.noteVerified("../../etc/passwd", "cpu.frequency.arbiter")
216: 
217:         assertTrue(io.list().all { !it.contains("..") && !it.contains("/") })
218:         assertEquals("cpu.frequency.arbiter", memory.preferredRoute("../../etc/passwd"))
219:     }
220: 
221:     @Test
222:     fun `the store stays bounded`() {
223:         val io = InMemoryIo()
224:         val memory = AtlasRouteMemory(io, clockMs = { 0L }, maxEntries = 4)
225:         repeat(12) { index -> memory.noteVerified("cpu_frequency", "route.number.$index") }
226: 
227:         assertTrue("the store must not grow without bound: ${io.list().size}", io.list().size <= 4)
228:     }
229: 
230:     private fun memory(): AtlasRouteMemory =
231:         AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { 1L })
232: 
233:     private class InMemoryIo : AtlasStoreIo {
234:         private val entries = LinkedHashMap<String, String>()
235:         override fun read(name: String): String? = entries[name]
236:         override fun write(name: String, text: String): Boolean {
237:             entries[name] = text
238:             return true
239:         }
240: 
241:         override fun delete(name: String): Boolean = entries.remove(name) != null
242:         override fun list(): List<String> = entries.keys.sorted()
243:     }
244: }

(End of file - total 244 lines)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 67.1s)

_Thinking:_



**Tool: task**

**Input:**
```json
{
  "subagent_type": "general",
  "description": "Review Atlas recovery safety",
  "background": true,
  "prompt": "Task ATLAS-RECOVERY-02 independent safety/code review, READ ONLY, no edits/builds. Read docs/ai/team/safety-reviewer.md, ENGINEERING-CONTRACT §§8/9/13, DECISIONS ADR07/11/16/17, HANDOFF invariants, VALIDATION §6. Review current uncommitted product diff in core/hardware/AtlasRouteMemory.kt + NEW AtlasRouteMemoryFactory.kt + wiring DataModule.kt and AppMonitor.kt; tests AtlasRouteMemoryTest and AtlasAdaptiveExecutorTest. Scope: boot-aware adaptive route-memory recovery, not adding routes or general Atlas overhaul. Factory shared between app and companion lazily reads Settings.Global.BOOT_COUNT, maps valid count+1, error/invalid=>0 unknown. Unknown boot cannot expire quarantines/advertise verified preference; known changed boot resets previous generation counters. Privilege callbacks stay constant0 deliberately because permission changes don't reset physical state. Existing legacy records generation0 treated unknown quarantine retained. Reviewer should try to falsify new behavior and check safe production semantics, not blindly approve. Identify introduced regression vs existing pre-change defects explicitly. Known pre-existing memory pruning clears all other entries at capacity and file IO nontransactional cross-process; main hasn't changed those yet, don't misattribute to diff. Verification agent separately constructing actual Kotlin/JUnit runner, Android SDK missing, no Gradle/build allowed by default. Available tool doesn't select named model or family: DO NOT claim to be Luna or satisfy different-family review requirement. Return site->failure mode->blast radius->mitigation->verdict plus APPROVE/APPROVE_WITH_CONCERNS/BLOCK with evidence; post only material blockers. No full repo audit beyond scope."
}
```

**Output:**
```
<task id="ses_f3c4351f7ffegIB7hG5Gz08O2p" state="running">
<summary>Background task started</summary>
<task_result>
The task is working in the background. You will be notified automatically when it finishes.
DO NOT sleep, poll for progress, ask the task for status, or duplicate this task's work — avoid working with the same files or topics it is using.
Work on non-overlapping tasks, or briefly tell the user what you launched and end your response.
</task_result>
</task>
```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/code_health.py --assert",
  "description": "Validate health and frozen debt after implementation",
  "timeout": 120000
}
```

**Output:**
```
════════════════════════════════════════════════════════════════════════
الصحّة — المطلوب صفر
════════════════════════════════════════════════════════════════════════
  ✓ package_mismatch: 0
  ✓ unresolved_resource: 0
  ✓ duplicate_string_key: 0
  ✓ stray_root_file: 0

════════════════════════════════════════════════════════════════════════
الدَّين — مُجمَّد عند سقف، لا يُسمح له بالنمو
════════════════════════════════════════════════════════════════════════
     10  oversized_files
     29  own_wildcard_imports
     63  hardcoded_ui_literals
     23  presentation_hw_writes
      2  todo_markers (تُراجَع، لا تُفرض)
    236  platform_wildcard_imports (لا تُحتسب)

  ملفات Kotlin: 576  ·  الأسطر: 135231

  أكبر الملفات (تجاوزت 1000 سطر):
     2438  manager/app/src/main/java/nd/max/AppMonitor.kt
     1843  manager/app/src/main/java/nd/max/ui/mainscreens/MaxAiScreen.kt
     1787  manager/app/src/main/java/nd/max/core/maxai/MaxAiEngine.kt
     1561  manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt
     1282  manager/app/src/main/java/nd/max/ui/subscreens/CustomThemeScreen.kt
     1147  manager/app/src/main/java/nd/max/ui/component/HomeComponents.kt
     1147  manager/app/src/main/java/nd/max/ui/flasher/KernelFlasherScreen.kt
     1118  manager/app/src/main/java/nd/max/ui/subscreens/CpuCoreControlScreen.kt
     1092  manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt
     1086  manager/app/src/main/java/nd/max/ui/component/ExpressiveListComponent.kt

  استيرادات شاملة لكود المشروع نفسه (تُخفي مصدر الرمز):
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/MainActivity.kt
    nd.max.ui.navigation  <-  manager/app/src/main/java/nd/max/MainActivity.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/MainActivity.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/component/ModuleBackupComponent.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/ui/component/SettingsHeaderComponent.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/mainscreens/HomeDashboardComponents.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
    nd.max.ui.viewmodel  <-  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
    nd.max.ui.mainscreens  <-  manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt
    nd.max.ui.subscreens  <-  manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt
    nd.max.ui.subscreens.hubs  <-  manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/AboutScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/BypassCheckScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/ColorSchemeScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/CpuCoreControlScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/CustomThemeScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/DebloatFreezeScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/DisplayStudioScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/FasSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/FpsGoSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt
    nd.max.ui.mainscreens  <-  manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/LogsViewerScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/PreferencedTweakScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/ProcessManagerScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/SetEditScreen.kt

  كتابة عتاد من طبقة العرض (ADR-11) — 23 موضعًا:
     10  manager/app/src/main/java/nd/max/ui/viewmodel/ZramViewModel.kt
      3  manager/app/src/main/java/nd/max/ui/viewmodel/TweakViewmodel.kt
      2  manager/app/src/main/java/nd/max/ui/subscreens/BypassChargeScreen.kt
      2  manager/app/src/main/java/nd/max/ui/subscreens/BypassCheckScreen.kt
      1  manager/app/src/main/java/nd/max/ui/mainscreens/GetStartedScreen.kt
      1  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
      1  manager/app/src/main/java/nd/max/ui/subscreens/ZramManagerScreen.kt
      1  manager/app/src/main/java/nd/max/ui/viewmodel/AppSettingsViewmodel.kt
      1  manager/app/src/main/java/nd/max/ui/viewmodel/DisplayStudioViewModel.kt
      1  manager/app/src/main/java/nd/max/ui/viewmodel/NetworkSchedulerViewModel.kt

  نصوص واجهة صلبة (أول 15 من 63):
    manager/app/src/main/java/nd/max/service/FpsOverlayService.kt: 'FPS'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'Not exported'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'System'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'Activities'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'Display Settings'
    manager/app/src/main/java/nd/max/ui/activitylauncher/FloatingActivityService.kt: 'Activity Logger'
    manager/app/src/main/java/nd/max/ui/activitylauncher/FloatingActivityService.kt: 'Preferences'
    manager/app/src/main/java/nd/max/ui/activitylauncher/FloatingActivityService.kt: 'Scanning Activities...'
    manager/app/src/main/java/nd/max/ui/component/StudioComponents.kt: 'MHz'
    manager/app/src/main/java/nd/max/ui/component/StudioComponents.kt: 'CPU'
    manager/app/src/main/java/nd/max/ui/component/ThermalPulseCard.kt: 'Temperature'
    manager/app/src/main/java/nd/max/ui/design/MaxHelp.kt: '\\u2022'
    manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt: 'Live hardware health'
    manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt: 'Active control ownership'
    manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt: 'Hardware report'

الحصيلة: صحّة نظيفة

بوابة النظافة: exit 0

```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/i18n_coverage.py --assert",
  "description": "Verify resources remain consistent after recovery changes",
  "timeout": 120000
}
```

**Output:**
```
مجلدات values-*: 84 + en  ·  أكواد المنتقي: 85  ·  locales_config: 85
تطابق الأكواد الثلاثة: OK
عوائق (specifiers/تكرار/تطابق الأكواد): 0

```

---

## Assistant (Code · GPT-6 Astra · 25.5s)

_Thinking:_

**Considering implementation limits**

I have about 19k tokens left, so I need to be mindful not to go overboard with length. It’s clear I should complete the implementation and mark it next because I've added my tests and verified everything. I’ll also need to remove any unused test assertions and unnecessary import data. It's better to keep things minimal, so I’ll apply a patch to the real file to make the necessary test additions.

**Tool: apply_patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt\n@@\n-import nd.max.core.atlas.AtlasStoreIo\n+import nd.max.core.atlas.AtlasFileStoreIo\n+import nd.max.core.atlas.AtlasStoreIo\n import org.junit.Assert.assertEquals\n-import org.junit.Assert.assertFalse\n import org.junit.Assert.assertNull\n import org.junit.Assert.assertTrue\n+import org.junit.Rule\n import org.junit.Test\n+import org.junit.rules.TemporaryFolder\n@@\n class AtlasRouteMemoryTest {\n+    @get:Rule\n+    val temporary = TemporaryFolder()\n@@\n     fun `restarting the process in the same boot preserves quarantine`() {\n@@\n         assertEquals(setOf(\"gpu.frequency.arbiter\"), restarted.quarantinedRoutes(\"gpu_frequency\"))\n     }\n+\n+    @Test\n+    fun `independent file stores retain quarantine then persist recovery in a new boot`() {\n+        val directory = temporary.newFolder(\"routes\").toPath()\n+        AtlasRouteMemory(AtlasFileStoreIo(directory), clockMs = { 1_000L }, bootGeneration = { 7L })\n+            .noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+\n+        val sameBoot = AtlasRouteMemory(AtlasFileStoreIo(directory), clockMs = { 2_000L }, bootGeneration = { 7L })\n+        assertEquals(setOf(\"gpu.frequency.arbiter\"), sameBoot.quarantinedRoutes(\"gpu_frequency\"))\n+\n+        val nextBoot = AtlasRouteMemory(AtlasFileStoreIo(directory), clockMs = { 1_000L }, bootGeneration = { 8L })\n+        assertTrue(nextBoot.quarantinedRoutes(\"gpu_frequency\").isEmpty())\n+        nextBoot.noteVerified(\"gpu_frequency\", \"gpu.frequency.arbiter\")\n+\n+        val reopened = AtlasRouteMemory(AtlasFileStoreIo(directory), clockMs = { 2_000L }, bootGeneration = { 8L })\n+        assertTrue(reopened.quarantinedRoutes(\"gpu_frequency\").isEmpty())\n+        assertEquals(\"gpu.frequency.arbiter\", reopened.preferredRoute(\"gpu_frequency\"))\n+    }\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/di/DataModule.kt\n@@\n         dispatcher = dispatchers.io,\n     )\n-\n }\n*** End Patch"
}
```

**Output:**
```
Success. Updated the following files:
M manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt
M manager/app/src/main/java/nd/max/core/di/DataModule.kt
```

---

## Assistant (Code · GPT-6 Astra · 11.1s)

**Tool: todowrite**

**Input:**
```json
{
  "todos": [
    {
      "content": "استكشاف Max أطلس وتحديد أول خطوة نحو التوافق والتعافي التلقائي",
      "priority": "high",
      "status": "completed"
    },
    {
      "content": "إصلاح ذاكرة التعافي وربط جيل الإقلاع الحقيقي بمستهلكي أطلس مع اختبارات انحدار",
      "priority": "high",
      "status": "completed"
    },
    {
      "content": "التحقق المستهدف ومراجعة السلامة وتوثيق ما تحقق وحدود التوافق المتبقية",
      "priority": "high",
      "status": "in_progress"
    }
  ]
}
```

**Output:**
```
[
  {
    "content": "استكشاف Max أطلس وتحديد أول خطوة نحو التوافق والتعافي التلقائي",
    "status": "completed",
    "priority": "high"
  },
  {
    "content": "إصلاح ذاكرة التعافي وربط جيل الإقلاع الحقيقي بمستهلكي أطلس مع اختبارات انحدار",
    "status": "completed",
    "priority": "high"
  },
  {
    "content": "التحقق المستهدف ومراجعة السلامة وتوثيق ما تحقق وحدود التوافق المتبقية",
    "status": "in_progress",
    "priority": "high"
  }
]
```

---

## Assistant (Code · GPT-6 Astra · 17.4s)

_Thinking:_



**Tool: board_read**

**Input:**
```json
{
  "since": "board_4ea9e11f-f891-424e-9dd2-90f98f4287de",
  "limit": 10
}
```

**Output:**
```
{"observedAt":1789990381803,"agent":"main","participants":[{"id":"main","sessionID":"ses_f3c539be4ffe75jztLqF6tMSH7","label":"تطوير Max أطلس في المشروع","agent":"code","state":"busy"},{"id":"ses_f3c4351f7ffegIB7hG5Gz08O2p","sessionID":"ses_f3c4351f7ffegIB7hG5Gz08O2p","label":"Review Atlas recovery safety (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c460af9ffegFtpDQxOyzwjXg","sessionID":"ses_f3c460af9ffegFtpDQxOyzwjXg","label":"Build focused Atlas verification (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c52514affezdTXZoKxZL1HmE","sessionID":"ses_f3c52514affezdTXZoKxZL1HmE","label":"Map Max Atlas gaps (@explore subagent)","agent":"explore","state":"completed"}],"messages":[{"id":"board_ba5dd655-89c5-47cd-87e5-02426773b415","timestamp":1789990237108,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"Declared org.json:json:20260814 downloaded successfully from Maven Central to /tmp/kilo/json-20260814.jar (89,800 bytes), available for runner --json-jar. Main memory/executor regression tests now updated. Factory create(context,clockMs) lazily reads Settings.Global.BOOT_COUNT maps valid count+1, failure/negative unknown0. Hilt+AppMonitor now both call AtlasRouteMemoryFactory.create. New boot resets old counters; unknown boot preserves quarantine/no verified preference. No Android SDK at /root/android-sdk or declared /home/codespace path, JDK17 exists /usr/lib/jvm/java-17-openjdk-arm64.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)"},{"id":"board_9a95941f-0595-4f25-87e9-4e53e5e5d2ca","timestamp":1789990301097,"from":"ses_f3c460af9ffegFtpDQxOyzwjXg","to":"main","type":"HOLD","body":"Runner dependency blocker confirmed: AtlasAdaptiveExecutorTest imports HardwareRepairRequest/Result/AtlasRepairPort, all colocated in HardwareRepairExecutor.kt; that class requires HardwareControlArbiter -> SharedHardwareOwnershipStore -> android.system.ErrnoException/Os/OsConstants (direct imports). Cannot honestly compile full executor source set without Android stubs. I will deliver memory + planner + source wiring guards and PSI-parse executor/Android-facing files, explicitly reporting executor suites unexecuted unless you already intend a production pure-type split. No such refactor requested from my scope. JSON external jar availability noted.","fromLabel":"Build focused Atlas verification (@general subagent)","toLabel":"تطوير Max أطلس في المشروع"}],"hasMore":false,"cursor":"board_9a95941f-0595-4f25-87e9-4e53e5e5d2ca"}
```

---

## Assistant (Code · GPT-6 Astra · 25.5s)

_Thinking:_



**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c460af9ffegFtpDQxOyzwjXg",
  "reply_to": "board_9a95941f-0595-4f25-87e9-4e53e5e5d2ca",
  "type": "INFO",
  "body": "Resolved plan for dependency blocker: I will move the existing AtlasRepairPort/HardwareRepairState/HardwareRepairRequest/HardwareRepairResult (HardwareRepairExecutor.kt first83 lines, no API/behavior changes) into new pure HardwareRepairModels.kt now. You can include that plus ControlOwnership.kt and actual AtlasAdaptiveExecutor in focused compile, not actual Android-linked HardwareRepairExecutor/arbiter. This enables original executor tests without stubs. Keep real executor/arbiter PSI parsed, do not claim their behavior tested. Main also added real AtlasFileStoreIo persistence roundtrip regression in memory test, include production AtlasFileStoreIo.kt."
}
```

**Output:**
```
{"id":"board_e8956511-7c08-4b44-8ec7-a6f0adf07a8a","timestamp":1789990408262,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"Resolved plan for dependency blocker: I will move the existing AtlasRepairPort/HardwareRepairState/HardwareRepairRequest/HardwareRepairResult (HardwareRepairExecutor.kt first83 lines, no API/behavior changes) into new pure HardwareRepairModels.kt now. You can include that plus ControlOwnership.kt and actual AtlasAdaptiveExecutor in focused compile, not actual Android-linked HardwareRepairExecutor/arbiter. This enables original executor tests without stubs. Keep real executor/arbiter PSI parsed, do not claim their behavior tested. Main also added real AtlasFileStoreIo persistence roundtrip regression in memory test, include production AtlasFileStoreIo.kt.","reply_to":"board_9a95941f-0595-4f25-87e9-4e53e5e5d2ca","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)","availability":{"observedAt":1789990408264,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"busy"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

---

## Assistant (Code · GPT-6 Astra · 66.6s)

_Thinking:_



اكتمل ربط الذاكرة بعدّاد إقلاع الجهاز، وأضفت اختبارات تميّز بين إعادة تشغيل التطبيق وإعادة تشغيل الجهاز، وتحافظ على الحظر حين تكون حالة الإقلاع مجهولة. لا يتوفر Android SDK هنا؛ سأفصل أنواع معاملات الإصلاح عن المنفّذ دون تغيير واجهتها، حتى أختبر منطق أطلس الحقيقي على JVM بدل الاكتفاء بفحص الأقواس.


**Tool: apply_patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Add File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/HardwareRepairModels.kt\n+package nd.max.core.hardware\n+\n+/** A mutation seam for Atlas; production uses [HardwareRepairExecutor], tests can replay route outcomes. */\n+interface AtlasRepairPort {\n+    fun execute(request: HardwareRepairRequest): HardwareRepairResult\n+}\n+\n+/** The externally meaningful state of one route transaction. */\n+enum class HardwareRepairState {\n+    /**\n+     * The value was written and read back unchanged for the bounded confirmation window.\n+     *\n+     * **This is a fast confirmation, not a claim of sustained stability.** The default window is three\n+     * samples forty milliseconds apart — roughly eighty milliseconds — and a vendor writer that\n+     * republishes on a seconds-long timer cannot be observed inside it. The state was called\n+     * `VERIFIED_STABLE` before, which promised more than the measurement supports: a caller reading\n+     * \"stable\" would stop looking exactly when the interesting failure happens later.\n+     *\n+     * Sustained verification is owned by the caller's own drift loop, which re-reads on a much coarser\n+     * cadence (`AppMonitor.DRIFT_CHECK_INTERVAL_MS`, ten seconds) and is the only thing that can see a\n+     * periodic rewrite. `DRIFT_ROLLED_BACK` below is what that class of failure looks like when it\n+     * happens *inside* the window.\n+     */\n+    CONFIRMED_WINDOW,\n+    BLOCKED,\n+    APPLY_FAILED_ROLLED_BACK,\n+\n+    /** An external writer took the value back inside the window and the baseline was restored. */\n+    DRIFT_ROLLED_BACK,\n+    ROLLBACK_UNVERIFIED,\n+}\n+\n+data class HardwareRepairRequest(\n+    val routeId: String,\n+    val key: String,\n+    val owner: ControlOwnership.Owner,\n+    val token: String,\n+    val desired: String,\n+    val apply: (String) -> Boolean,\n+    val read: () -> String?,\n+    val restore: (String) -> Boolean,\n+    val baseline: String? = null,\n+    /**\n+     * حكم تلبية الطلب — `null` يعني التساوي الحرفي.\n+     *\n+     * يُمرَّر إلى المُحكِّم **وإلى نافذة التأكيد هنا** (`request.read()` مقارنًا بالمطلوب\n+     * عيّنةً بعد عيّنة): لو حكم المُحكِّم بالسقف وحكمت النافذة بالتساوي، لسقط طلبٌ صحيح\n+     * عند أول عيّنة. فحكم واحد يُستعمل في الموضعين وإلا تناقض المساران.\n+     */\n+    val verify: ((String, String?) -> Boolean)? = null,\n+    val stabilitySamples: Int = 3,\n+    val stabilityIntervalMs: Long = 40L,\n+) {\n+    init {\n+        require(routeId.matches(ROUTE_ID)) { \"routeId is not canonical\" }\n+        require(key.isNotBlank() && token.isNotBlank()) { \"hardware transaction identity is required\" }\n+        require(stabilitySamples in 1..8) { \"stability sample count is outside the bounded range\" }\n+        require(stabilityIntervalMs in 0L..500L) { \"stability interval is outside the bounded range\" }\n+    }\n+\n+    /** How long the read-back value must hold still before the transaction is called confirmed. */\n+    val confirmationWindowMs: Long get() = stabilityIntervalMs * (stabilitySamples - 1)\n+\n+    companion object {\n+        val ROUTE_ID = Regex(\"^[a-z][a-z0-9_.-]{2,63}$\")\n+    }\n+}\n+\n+data class HardwareRepairResult(\n+    val routeId: String,\n+    val key: String,\n+    val state: HardwareRepairState,\n+    val requested: String,\n+    val actual: String?,\n+    val applied: Boolean,\n+    val verified: Boolean,\n+    val stabilitySamples: Int,\n+    val rollbackAttempted: Boolean,\n+    val rollbackVerified: Boolean?,\n+    val error: String? = null,\n+) {\n+    val successful: Boolean get() = state == HardwareRepairState.CONFIRMED_WINDOW\n+}\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/HardwareRepairExecutor.kt\n@@\n-/** A mutation seam for Atlas; production uses [HardwareRepairExecutor], tests can replay route outcomes. */\n-interface AtlasRepairPort {\n-    fun execute(request: HardwareRepairRequest): HardwareRepairResult\n-}\n-\n-/** The externally meaningful state of one route transaction. */\n-enum class HardwareRepairState {\n-    /**\n-     * The value was written and read back unchanged for the bounded confirmation window.\n-     *\n-     * **This is a fast confirmation, not a claim of sustained stability.** The default window is three\n-     * samples forty milliseconds apart — roughly eighty milliseconds — and a vendor writer that\n-     * republishes on a seconds-long timer cannot be observed inside it. The state was called\n-     * `VERIFIED_STABLE` before, which promised more than the measurement supports: a caller reading\n-     * \"stable\" would stop looking exactly when the interesting failure happens later.\n-     *\n-     * Sustained verification is owned by the caller's own drift loop, which re-reads on a much coarser\n-     * cadence (`AppMonitor.DRIFT_CHECK_INTERVAL_MS`, ten seconds) and is the only thing that can see a\n-     * periodic rewrite. `DRIFT_ROLLED_BACK` below is what that class of failure looks like when it\n-     * happens *inside* the window.\n-     */\n-    CONFIRMED_WINDOW,\n-    BLOCKED,\n-    APPLY_FAILED_ROLLED_BACK,\n-\n-    /** An external writer took the value back inside the window and the baseline was restored. */\n-    DRIFT_ROLLED_BACK,\n-    ROLLBACK_UNVERIFIED,\n-}\n-\n-data class HardwareRepairRequest(\n-    val routeId: String,\n-    val key: String,\n-    val owner: ControlOwnership.Owner,\n-    val token: String,\n-    val desired: String,\n-    val apply: (String) -> Boolean,\n-    val read: () -> String?,\n-    val restore: (String) -> Boolean,\n-    val baseline: String? = null,\n-    /**\n-     * حكم تلبية الطلب — `null` يعني التساوي الحرفي.\n-     *\n-     * يُمرَّر إلى المُحكِّم **وإلى نافذة التأكيد هنا** (`request.read()` مقارنًا بالمطلوب\n-     * عيّنةً بعد عيّنة): لو حكم المُحكِّم بالسقف وحكمت النافذة بالتساوي، لسقط طلبٌ صحيح\n-     * عند أول عيّنة. فحكم واحد يُستعمل في الموضعين وإلا تناقض المساران.\n-     */\n-    val verify: ((String, String?) -> Boolean)? = null,\n-    val stabilitySamples: Int = 3,\n-    val stabilityIntervalMs: Long = 40L,\n-) {\n-    init {\n-        require(routeId.matches(ROUTE_ID)) { \"routeId is not canonical\" }\n-        require(key.isNotBlank() && token.isNotBlank()) { \"hardware transaction identity is required\" }\n-        require(stabilitySamples in 1..8) { \"stability sample count is outside the bounded range\" }\n-        require(stabilityIntervalMs in 0L..500L) { \"stability interval is outside the bounded range\" }\n-    }\n-\n-    /** How long the read-back value must hold still before the transaction is called confirmed. */\n-    val confirmationWindowMs: Long get() = stabilityIntervalMs * (stabilitySamples - 1)\n-\n-    companion object {\n-        val ROUTE_ID = Regex(\"^[a-z][a-z0-9_.-]{2,63}$\")\n-    }\n-}\n-\n-data class HardwareRepairResult(\n-    val routeId: String,\n-    val key: String,\n-    val state: HardwareRepairState,\n-    val requested: String,\n-    val actual: String?,\n-    val applied: Boolean,\n-    val verified: Boolean,\n-    val stabilitySamples: Int,\n-    val rollbackAttempted: Boolean,\n-    val rollbackVerified: Boolean?,\n-    val error: String? = null,\n-) {\n-    val successful: Boolean get() = state == HardwareRepairState.CONFIRMED_WINDOW\n-}\n-\n*** End Patch"
}
```

**Output:**
```
Success. Updated the following files:
A manager/app/src/main/java/nd/max/core/hardware/HardwareRepairModels.kt
M manager/app/src/main/java/nd/max/core/hardware/HardwareRepairExecutor.kt
```

---

## Assistant (Code · GPT-6 Astra · 34.1s)

_Thinking:_



**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c4351f7ffegIB7hG5Gz08O2p",
  "type": "INFO",
  "body": "Verification dependency necessitated source-only move: existing first83 lines of HardwareRepairExecutor.kt (AtlasRepairPort/HardwareRepairState/Request/Result) moved byte-identically into NEW HardwareRepairModels.kt, same package/API. Executor body untouched. This enables actual AtlasAdaptiveExecutorTest without pulling SharedHardwareOwnershipStore android.system dependency; reviewer include move, don't treat as new write behavior. Added real AtlasFileStoreIo persistence roundtrip memory test. Focus remains medium boot lifecycle."
}
```

**Output:**
```
{"id":"board_a468da81-e5b7-4019-9f0e-3e4d8631dfa9","timestamp":1789990502776,"from":"main","to":"ses_f3c4351f7ffegIB7hG5Gz08O2p","type":"INFO","body":"Verification dependency necessitated source-only move: existing first83 lines of HardwareRepairExecutor.kt (AtlasRepairPort/HardwareRepairState/Request/Result) moved byte-identically into NEW HardwareRepairModels.kt, same package/API. Executor body untouched. This enables actual AtlasAdaptiveExecutorTest without pulling SharedHardwareOwnershipStore android.system dependency; reviewer include move, don't treat as new write behavior. Added real AtlasFileStoreIo persistence roundtrip memory test. Focus remains medium boot lifecycle.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Review Atlas recovery safety (@general subagent)","availability":{"observedAt":1789990502781,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"busy"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c460af9ffegFtpDQxOyzwjXg",
  "type": "INFO",
  "body": "HardwareRepairModels.kt now exists; same package/API, first83 lines moved from executor unchanged. Pure source set can now include it and actual adaptive executor. Main implementation edits complete for verification."
}
```

**Output:**
```
{"id":"board_7e75fca7-f947-406a-8a72-3c8630c5045e","timestamp":1789990503438,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"HardwareRepairModels.kt now exists; same package/API, first83 lines moved from executor unchanged. Pure source set can now include it and actual adaptive executor. Main implementation edits complete for verification.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)","availability":{"observedAt":1789990503442,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"busy"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

**Tool: grep**

**Input:**
```json
{
  "path": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test",
  "pattern": "HardwareRepairExecutor.kt|AtlasRouteMemoryFactory|DataModule.kt|ROUTE_MEMORY_DIR",
  "limit": 50
}
```

**Output:**
```
Found 6 matches
/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryWiringTest.kt:
  Line 22:         listOf("core/di/DataModule.kt", "AppMonitor.kt").forEach { path ->

  Line 24:             assertTrue("$path must call the shared factory", Regex("AtlasRouteMemoryFactory\\s*\\.\\s*create\\s*\\(").containsMatchIn(text))

  Line 31:         val text = source("core/hardware/AtlasRouteMemoryFactory.kt")

  Line 33:         assertTrue(text.contains("AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME"))

  Line 42:         val text = source("core/hardware/AtlasRouteMemoryFactory.kt")


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/ui/navigation/AtlasReadOnlyEntryTest.kt:
  Line 76:         val module = AtlasSourceGuard.read("src/main/java/nd/max/core/di/DataModule.kt")

```

**Tool: bash**

**Input:**
```json
{
  "command": "git diff --check",
  "description": "Check whitespace after dependency-neutral source move"
}
```

**Output:**
```
(no output)
```

---

## Assistant (Code · GPT-6 Astra · 74.4s)

_Thinking:_



_Thinking:_



**Tool: apply_patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt\n@@\n     fun quarantinedRoutes(target: String): Set<String> {\n         val boot = bootGeneration()\n         val privilege = privilegeGeneration()\n         return entries()\n             .filter { it.target == target && it.isQuarantinedIn(boot, privilege) }\n+            .onEach { entry ->\n+                if (entry.bootGeneration == 0L && boot > 0L) {\n+                    // Legacy/unknown failures remain blocked now. Anchor them to this known boot so\n+                    // a later real reboot can release them without a manual memory reset.\n+                    store(entry.copy(bootGeneration = boot, privilegeGeneration = privilege))\n+                }\n+            }\n             .map { it.routeId }\n             .toSet()\n@@\n         val names = io.list().filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }\n-        if (names.size < maxEntries) return\n         val keep = nameOf(target, routeId)\n+        // Refreshing an existing entry consumes no capacity and must not evict other quarantines.\n+        if (keep in names || names.size < maxEntries) return\n         names.filter { it != keep }.forEach { io.delete(it) }\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt\n@@\n     fun `legacy unknown boot quarantine is not silently cleared by a known boot`() {\n@@\n         assertEquals(setOf(\"gpu.frequency.arbiter\"), current.quarantinedRoutes(\"gpu_frequency\"))\n     }\n+\n+    @Test\n+    fun `legacy quarantine anchors to a known boot and recovers after the following reboot`() {\n+        val io = InMemoryIo()\n+        AtlasRouteMemory(io, clockMs = { 1_000L })\n+            .noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+        val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 8L })\n+        assertEquals(setOf(\"gpu.frequency.arbiter\"), current.quarantinedRoutes(\"gpu_frequency\"))\n+        assertEquals(8L, current.entries().single().bootGeneration)\n+\n+        val rebooted = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 9L })\n+\n+        assertTrue(rebooted.quarantinedRoutes(\"gpu_frequency\").isEmpty())\n+        rebooted.noteVerified(\"gpu_frequency\", \"gpu.frequency.arbiter\")\n+        assertTrue(rebooted.quarantinedRoutes(\"gpu_frequency\").isEmpty())\n+        assertEquals(\"gpu.frequency.arbiter\", rebooted.preferredRoute(\"gpu_frequency\"))\n+    }\n+\n+    @Test\n+    fun `anchoring a full store does not delete another targets quarantine`() {\n+        val io = InMemoryIo()\n+        val legacy = AtlasRouteMemory(io, clockMs = { 1_000L }, maxEntries = 2)\n+        legacy.noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+        legacy.noteFailed(\"cpu_frequency\", \"cpu.frequency.arbiter\", rollbackVerified = false)\n+        val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 8L }, maxEntries = 2)\n+\n+        assertEquals(setOf(\"gpu.frequency.arbiter\"), current.quarantinedRoutes(\"gpu_frequency\"))\n+        assertEquals(setOf(\"cpu.frequency.arbiter\"), current.quarantinedRoutes(\"cpu_frequency\"))\n+        assertEquals(2, current.entries().size)\n+    }\n*** End Patch"
}
```

**Output:**
```
Success. Updated the following files:
M manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
M manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt
```

---

## Assistant (Code · GPT-6 Astra · 25.1s)

_Thinking:_



**Tool: board_read**

**Input:**
```json
{
  "since": "board_9a95941f-0595-4f25-87e9-4e53e5e5d2ca",
  "limit": 10
}
```

**Output:**
```
{"observedAt":1789990608795,"agent":"main","participants":[{"id":"main","sessionID":"ses_f3c539be4ffe75jztLqF6tMSH7","label":"تطوير Max أطلس في المشروع","agent":"code","state":"busy"},{"id":"ses_f3c4351f7ffegIB7hG5Gz08O2p","sessionID":"ses_f3c4351f7ffegIB7hG5Gz08O2p","label":"Review Atlas recovery safety (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c460af9ffegFtpDQxOyzwjXg","sessionID":"ses_f3c460af9ffegFtpDQxOyzwjXg","label":"Build focused Atlas verification (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c52514affezdTXZoKxZL1HmE","sessionID":"ses_f3c52514affezdTXZoKxZL1HmE","label":"Map Max Atlas gaps (@explore subagent)","agent":"explore","state":"completed"}],"messages":[{"id":"board_e8956511-7c08-4b44-8ec7-a6f0adf07a8a","timestamp":1789990408262,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"Resolved plan for dependency blocker: I will move the existing AtlasRepairPort/HardwareRepairState/HardwareRepairRequest/HardwareRepairResult (HardwareRepairExecutor.kt first83 lines, no API/behavior changes) into new pure HardwareRepairModels.kt now. You can include that plus ControlOwnership.kt and actual AtlasAdaptiveExecutor in focused compile, not actual Android-linked HardwareRepairExecutor/arbiter. This enables original executor tests without stubs. Keep real executor/arbiter PSI parsed, do not claim their behavior tested. Main also added real AtlasFileStoreIo persistence roundtrip regression in memory test, include production AtlasFileStoreIo.kt.","reply_to":"board_9a95941f-0595-4f25-87e9-4e53e5e5d2ca","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)"},{"id":"board_a468da81-e5b7-4019-9f0e-3e4d8631dfa9","timestamp":1789990502776,"from":"main","to":"ses_f3c4351f7ffegIB7hG5Gz08O2p","type":"INFO","body":"Verification dependency necessitated source-only move: existing first83 lines of HardwareRepairExecutor.kt (AtlasRepairPort/HardwareRepairState/Request/Result) moved byte-identically into NEW HardwareRepairModels.kt, same package/API. Executor body untouched. This enables actual AtlasAdaptiveExecutorTest without pulling SharedHardwareOwnershipStore android.system dependency; reviewer include move, don't treat as new write behavior. Added real AtlasFileStoreIo persistence roundtrip memory test. Focus remains medium boot lifecycle.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Review Atlas recovery safety (@general subagent)"},{"id":"board_7e75fca7-f947-406a-8a72-3c8630c5045e","timestamp":1789990503438,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"HardwareRepairModels.kt now exists; same package/API, first83 lines moved from executor unchanged. Pure source set can now include it and actual adaptive executor. Main implementation edits complete for verification.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)"},{"id":"board_ba29bebe-dd7f-4449-9af8-eb001ae69ef8","timestamp":1789990568490,"from":"ses_f3c460af9ffegFtpDQxOyzwjXg","to":"main","type":"INFO","body":"Runner now targets HardwareRepairModels.kt + ControlOwnership.kt + actual AtlasAdaptiveExecutor, alongside memory/planner/wiring guards and real AtlasFileStoreIo. Only HardwareRepairExecutorTest remains explicitly unexecuted; production transaction/arbiter/store PSI parsed. Will run once new models file exists with JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 python3 tools/test_atlas_jvm.py --json-jar /tmp/kilo/json-20260814.jar --work-dir /tmp/kilo. No edits outside my two allowed new files.","reply_to":"board_e8956511-7c08-4b44-8ec7-a6f0adf07a8a","fromLabel":"Build focused Atlas verification (@general subagent)","toLabel":"تطوير Max أطلس في المشروع"}],"hasMore":false,"cursor":"board_ba29bebe-dd7f-4449-9af8-eb001ae69ef8"}
```

---

## Assistant (Code · GPT-6 Astra · 49.5s)

**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c4351f7ffegIB7hG5Gz08O2p",
  "type": "INFO",
  "body": "Material refinement now in diff: legacy boot0 quarantine is anchored to CURRENT known boot during quarantinedRoutes(), without releasing now; NEXT known reboot then safely expires it. Otherwise legacy records would remain stuck forever, contradicting autonomy. Anchoring write failure keeps original boot0, thus keeps quarantine. To avoid migration erasing other records at capacity, pruneIfNeeded now returns immediately when updating an existing name (new-entry wholesale pruning remains pre-existing limitation). Added two regression tests legacy->known->nextBoot, full-store anchor preserving different-target quarantine. Please review final form including this."
}
```

**Output:**
```
{"id":"board_9136bb6c-b13d-415e-9797-b30693ad214e","timestamp":1789990658444,"from":"main","to":"ses_f3c4351f7ffegIB7hG5Gz08O2p","type":"INFO","body":"Material refinement now in diff: legacy boot0 quarantine is anchored to CURRENT known boot during quarantinedRoutes(), without releasing now; NEXT known reboot then safely expires it. Otherwise legacy records would remain stuck forever, contradicting autonomy. Anchoring write failure keeps original boot0, thus keeps quarantine. To avoid migration erasing other records at capacity, pruneIfNeeded now returns immediately when updating an existing name (new-entry wholesale pruning remains pre-existing limitation). Added two regression tests legacy->known->nextBoot, full-store anchor preserving different-target quarantine. Please review final form including this.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Review Atlas recovery safety (@general subagent)","availability":{"observedAt":1789990658453,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"busy"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

---

## Assistant (Code · GPT-6 Astra · 27.9s)

_Thinking:_



**Tool: apply_patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt\n@@\n     fun `anchoring a full store does not delete another targets quarantine`() {\n@@\n         assertEquals(2, current.entries().size)\n     }\n+\n+    @Test\n+    fun `failed legacy migration write keeps quarantine across subsequent boots`() {\n+        val io = InMemoryIo()\n+        AtlasRouteMemory(io, clockMs = { 1_000L })\n+            .noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+        io.writable = false\n+\n+        for (boot in listOf(8L, 9L)) {\n+            val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { boot })\n+            assertEquals(setOf(\"gpu.frequency.arbiter\"), current.quarantinedRoutes(\"gpu_frequency\"))\n+            assertEquals(0L, current.entries().single().bootGeneration)\n+            assertNull(current.preferredRoute(\"gpu_frequency\"))\n+        }\n+    }\n@@\n     private class InMemoryIo : AtlasStoreIo {\n         private val entries = LinkedHashMap<String, String>()\n+        var writable: Boolean = true\n         override fun read(name: String): String? = entries[name]\n         override fun write(name: String, text: String): Boolean {\n+            if (!writable) return false\n             entries[name] = text\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/docs/ai/NEXT_TASK.md\n@@\n-ربط المصنعين بقراءة `Settings.Global.BOOT_COUNT` المشتركة، وإبقاء الحجر عند تعذّر معرفة الإقلاع،\n-وإعادة تهيئة نتائج المسار عندما يتغير السياق المعلوم. الاختبارات تثبت التعافي بعد إعادة الإقلاع وعدم\n+ربط المصنعين بقراءة `Settings.Global.BOOT_COUNT` المشتركة، وإبقاء الحجر عند تعذّر معرفة الإقلاع،\n+وتثبيت السجل القديم مجهول الإقلاع على الإقلاع المعلوم الحالي دون رفع الحجر حتى الإقلاع التالي.\n+إعادة تهيئة نتائج المسار عندما يتغير السياق المعلوم. الاختبارات تثبت التعافي بعد إعادة الإقلاع وعدم\n@@\n-واختبارات الذاكرة/التنفيذ. **القبول:** الاختبارات المستهدفة + البوابات الثابتة + مراجعة مستقلة؛ التجربة\n+واختبارات الذاكرة/التنفيذ. فُصلت أنواع `HardwareRepairModels.kt` من `HardwareRepairExecutor.kt`\n+بنفس الحزمة والواجهة، لقياس المنفّذ التكيفي الحقيقي على JVM دون بدائل وهمية لأندرويد.\n+**القبول:** الاختبارات المستهدفة + البوابات الثابتة + مراجعة مستقلة؛ التجربة\n*** End Patch"
}
```

**Output:**
```
Success. Updated the following files:
M manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt
M docs/ai/NEXT_TASK.md
```

---

## Assistant (Code · GPT-6 Astra · 63.4s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/tools/test_atlas_jvm.py",
  "limit": 320
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/tools/test_atlas_jvm.py</path>
<type>file</type>
<content>
1: #!/usr/bin/env python3
2: """Run focused Atlas production Kotlin/JUnit tests without Gradle or Android stubs.
3: 
4: Uses only existing Gradle-cached jars, or an explicitly supplied --json-jar.
5: The app declares org.json:json:20260814. Nothing is downloaded by this runner.
6: Android-facing files are parsed with real Kotlin PSI, not type-checked.
7: 
8: HardwareRepairExecutorTest is not executed: HardwareRepairExecutor depends on
9: HardwareControlArbiter -> SharedHardwareOwnershipStore -> android.system.
10: That transaction implementation is parsed only, never stripped or faked.
11: AtlasAdaptiveExecutorTest executes the real adaptive executor through its
12: existing AtlasRepairPort test seam, not the Android-linked transaction engine.
13: """
14: 
15: import argparse
16: import os
17: from pathlib import Path
18: import subprocess
19: import tempfile
20: import zipfile
21: 
22: 
23: ROOT = Path(__file__).resolve().parents[1]
24: SOURCE = ROOT / "manager/app/src/main/java/nd/max"
25: TEST = ROOT / "manager/app/src/test/java/nd/max"
26: JSON_VERSION = "20260814"
27: 
28: SYNTAX_CHECK = """
29: import java.nio.file.*;
30: import org.jetbrains.kotlin.cli.jvm.compiler.*;
31: import org.jetbrains.kotlin.config.CompilerConfiguration;
32: import org.jetbrains.kotlin.psi.KtPsiFactory;
33: import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
34: import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement;
35: import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
36: class CheckAtlasKotlinSyntax {
37:     public static void main(String[] paths) throws Exception {
38:         var disposable = Disposer.newDisposable();
39:         try {
40:             var env = KotlinCoreEnvironment.createForProduction(disposable,
41:                 new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
42:             var factory = new KtPsiFactory(env.getProject(), false);
43:             var valid = factory.createFile("Valid.kt", "fun valid(): Int = 1");
44:             var invalid = factory.createFile("Invalid.kt", "fun invalid( {");
45:             if (!PsiTreeUtil.findChildrenOfType(valid, PsiErrorElement.class).isEmpty()
46:                 || PsiTreeUtil.findChildrenOfType(invalid, PsiErrorElement.class).isEmpty()) {
47:                 throw new AssertionError("Kotlin PSI positive/negative self-test failed");
48:             }
49:             System.out.println("Kotlin PSI self-test: valid accepted, invalid rejected");
50:             int errors = 0;
51:             for (var name : paths) {
52:                 var path = Path.of(name);
53:                 var file = factory.createFile(path.getFileName().toString(), Files.readString(path));
54:                 for (var error : PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class)) {
55:                     System.err.println(name + ": " + error.getErrorDescription()
56:                         + " at offset " + error.getTextOffset());
57:                     errors++;
58:                 }
59:             }
60:             if (errors != 0) throw new AssertionError(errors + " Kotlin syntax errors");
61:             System.out.println("Kotlin syntax: " + paths.length
62:                 + " files, no parser errors (not type checking)");
63:         } finally { Disposer.dispose(disposable); }
64:     }
65: }
66: """
67: 
68: 
69: def main():
70:     parser = argparse.ArgumentParser(description=__doc__)
71:     parser.add_argument("--work-dir", type=Path, default=Path("/tmp/kilo"),
72:                         help="existing directory for temporary output (default: /tmp/kilo)")
73:     parser.add_argument("--json-jar", type=Path,
74:                         help=f"external org.json:json:{JSON_VERSION} jar instead of the Gradle cache")
75:     parser.add_argument("--syntax-only", action="store_true",
76:                         help="run Kotlin PSI parsing only; no JUnit execution or org.json required")
77:     args = parser.parse_args()
78:     if not args.work_dir.is_dir():
79:         parser.error(f"--work-dir must be an existing directory: {args.work_dir}")
80:     cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
81: 
82:     def jar(group, artifact, version):
83:         matches = sorted((cache / group / artifact / version).glob(f"*/{artifact}-{version}.jar"))
84:         if not matches:
85:             hint = "; provide --json-jar /path/to/json-20260814.jar" if group == "org.json" else ""
86:             parser.error(f"Missing cached {group}:{artifact}:{version}{hint}; no dependency was downloaded")
87:         return str(matches[0])
88: 
89:     stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.10")
90:     coroutines = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")
91:     annotations = jar("org.jetbrains", "annotations", "13.0")
92:     compiler = jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.3.10")
93:     reflect = jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10")
94:     compiler_cp = os.pathsep.join([compiler, stdlib, coroutines, annotations, reflect])
95:     java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else "java"
96: 
97:     runtime_cp = None
98:     if not args.syntax_only:
99:         junit = jar("junit", "junit", "4.13.2")
100:         hamcrest = jar("org.hamcrest", "hamcrest-core", "1.3")
101:         json_jar = args.json_jar.expanduser().resolve() if args.json_jar else Path(jar("org.json", "json", JSON_VERSION))
102:         try:
103:             with zipfile.ZipFile(json_jar) as archive:
104:                 required = {"org/json/JSONObject.class", "org/json/JSONArray.class", "org/json/JSONTokener.class"}
105:                 if not required.issubset(archive.namelist()):
106:                     parser.error(f"--json-jar does not contain the required org.json classes: {json_jar}")
107:                 metadata = "META-INF/maven/org.json/json/pom.properties"
108:                 if metadata in archive.namelist():
109:                     properties = dict(line.split("=", 1) for line in archive.read(metadata).decode().splitlines()
110:                                       if "=" in line and not line.startswith("#"))
111:                     if properties.get("version") != JSON_VERSION:
112:                         parser.error(f"Expected org.json:json:{JSON_VERSION}, got {properties.get('version')}")
113:                 else:
114:                     print("org.json jar has no Maven version metadata; version not independently verified", flush=True)
115:         except (OSError, zipfile.BadZipFile) as error:
116:             parser.error(f"Cannot read --json-jar {json_jar}: {error}")
117:         print(f"org.json:json:{JSON_VERSION} declared by app; using {json_jar}", flush=True)
118:         runtime_cp = os.pathsep.join([stdlib, coroutines, annotations, junit, hamcrest, str(json_jar)])
119: 
120:     sources = [
121:         SOURCE / "core/atlas/AtlasModels.kt",
122:         SOURCE / "core/atlas/AtlasDeviceIdentity.kt",
123:         SOURCE / "core/atlas/AtlasFreshness.kt",
124:         SOURCE / "core/atlas/AtlasEvidenceStore.kt",
125:         SOURCE / "core/atlas/AtlasFileStoreIo.kt",
126:         SOURCE / "core/atlas/AtlasControlIntent.kt",
127:         SOURCE / "core/atlas/AtlasRoutePlanner.kt",
128:         SOURCE / "core/hardware/ControlOwnership.kt",
129:         SOURCE / "core/hardware/HardwareRepairModels.kt",
130:         SOURCE / "core/hardware/AtlasAdaptiveExecutor.kt",
131:         SOURCE / "core/hardware/AtlasRouteMemory.kt",
132:         TEST / "core/hardware/AtlasRouteMemoryTest.kt",
133:         TEST / "core/hardware/AtlasAdaptiveExecutorTest.kt",
134:         TEST / "core/atlas/AtlasRoutePlannerTest.kt",
135:         TEST / "core/hardware/AtlasRouteMemoryWiringTest.kt",
136:     ]
137:     tests = [
138:         "nd.max.core.hardware.AtlasRouteMemoryTest",
139:         "nd.max.core.hardware.AtlasAdaptiveExecutorTest",
140:         "nd.max.core.atlas.AtlasRoutePlannerTest",
141:         "nd.max.core.hardware.AtlasRouteMemoryWiringTest",
142:     ]
143:     syntax_sources = [
144:         SOURCE / "core/hardware/AtlasRouteMemoryFactory.kt",
145:         SOURCE / "core/di/DataModule.kt",
146:         SOURCE / "AppMonitor.kt",
147:         SOURCE / "core/hardware/AtlasAdaptiveExecutor.kt",
148:         SOURCE / "core/hardware/HardwareRepairExecutor.kt",
149:         SOURCE / "core/hardware/HardwareControlArbiter.kt",
150:         SOURCE / "core/hardware/SharedHardwareOwnershipStore.kt",
151:         TEST / "core/hardware/AtlasAdaptiveExecutorTest.kt",
152:         TEST / "core/hardware/HardwareRepairExecutorTest.kt",
153:     ]
154:     for source in syntax_sources + ([] if args.syntax_only else sources):
155:         if not source.is_file():
156:             parser.error(f"Required source not found: {source}")
157: 
158:     print("NOT EXECUTED: HardwareRepairExecutorTest; "
159:           "production dependency chain reaches android.system via SharedHardwareOwnershipStore. "
160:           "No platform stubs or source extraction used.", flush=True)
161:     with tempfile.TemporaryDirectory(prefix="atlas-jvm-", dir=args.work_dir) as output:
162:         syntax = Path(output) / "CheckAtlasKotlinSyntax.java"
163:         syntax.write_text(SYNTAX_CHECK, encoding="utf-8")
164:         subprocess.run([
165:             java, f"-Djava.io.tmpdir={output}", "-cp", compiler_cp, str(syntax),
166:             *map(str, syntax_sources),
167:         ], cwd=ROOT, check=True)
168:         if args.syntax_only:
169:             print("Syntax-only mode: no production type checking or JUnit execution", flush=True)
170:             return
171:         print(f"Compiling {len(sources)} real Kotlin source/test files; running {len(tests)} JUnit suites", flush=True)
172:         subprocess.run([
173:             java, f"-Djava.io.tmpdir={output}", "-cp", compiler_cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
174:             "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", runtime_cp,
175:             "-d", output, *map(str, sources),
176:         ], cwd=ROOT, check=True)
177:         subprocess.run([
178:             java, f"-Djava.io.tmpdir={output}", "-cp", output + os.pathsep + runtime_cp,
179:             "org.junit.runner.JUnitCore", *tests,
180:         ], cwd=ROOT, check=True)
181:         print("Verified only the listed JVM suites and Kotlin syntax; "
182:               "Android compilation and device recovery remain unverified.", flush=True)
183: 
184: 
185: if __name__ == "__main__":
186:     main()

(End of file - total 186 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryWiringTest.kt",
  "limit": 150
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryWiringTest.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import java.io.File
4: import org.junit.Assert.assertFalse
5: import org.junit.Assert.assertTrue
6: import org.junit.Test
7: 
8: /** Source guards only: no Android classes loaded, no claim about Context behavior on a device. */
9: class AtlasRouteMemoryWiringTest {
10:     private val sourceRoot = listOf(
11:         File("src/main/java/nd/max"),
12:         File("app/src/main/java/nd/max"),
13:         File("manager/app/src/main/java/nd/max"),
14:     ).firstOrNull { it.isDirectory } ?: error("Cannot locate nd.max sources; wiring guard cannot run")
15: 
16:     private fun source(path: String): String = File(sourceRoot, path).readText()
17:         .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
18:         .replace(Regex("//[^\\n]*"), "")
19: 
20:     @Test
21:     fun `app and companion both construct route memory through the shared factory`() {
22:         listOf("core/di/DataModule.kt", "AppMonitor.kt").forEach { path ->
23:             val text = source(path)
24:             assertTrue("$path must call the shared factory", Regex("AtlasRouteMemoryFactory\\s*\\.\\s*create\\s*\\(").containsMatchIn(text))
25:             assertFalse("$path must not construct independent route memory", Regex("\\bAtlasRouteMemory\\s*\\(").containsMatchIn(text))
26:         }
27:     }
28: 
29:     @Test
30:     fun `factory uses shared non backed up storage and actual boot count`() {
31:         val text = source("core/hardware/AtlasRouteMemoryFactory.kt")
32:         assertTrue(text.contains("AtlasFileStoreIo.directoryFor(context.noBackupFilesDir)"))
33:         assertTrue(text.contains("AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME"))
34:         assertTrue(text.contains("Settings.Global.BOOT_COUNT"))
35:         assertTrue(text.contains("AtlasRouteMemory.generationForBootCount"))
36:         assertTrue(Regex("bootGeneration\\s*=\\s*\\{\\s*bootGeneration\\s*}").containsMatchIn(text))
37:         assertTrue(Regex("privilegeGeneration\\s*=\\s*\\{\\s*0L\\s*}").containsMatchIn(text))
38:     }
39: 
40:     @Test
41:     fun `factory never opens a privilege transport to obtain boot identity`() {
42:         val text = source("core/hardware/AtlasRouteMemoryFactory.kt")
43:         listOf(
44:             "RootFileAccess", "RootIpcManager", "Shell", "ProcessBuilder",
45:             "Runtime.getRuntime", "requestRoot", "requestPermission", "ensureRoot",
46:             "PrivilegeManager", "com.topjohnwu", "rikka.shizuku",
47:         ).forEach { forbidden ->
48:             assertFalse("Factory must not request privilege through $forbidden", text.contains(forbidden))
49:         }
50:     }
51: }

(End of file - total 51 lines)
</content>
```

**Tool: board_read**

**Input:**
```json
{
  "since": "board_ba29bebe-dd7f-4449-9af8-eb001ae69ef8",
  "limit": 10
}
```

**Output:**
```
{"observedAt":1789990750039,"agent":"main","participants":[{"id":"main","sessionID":"ses_f3c539be4ffe75jztLqF6tMSH7","label":"تطوير Max أطلس في المشروع","agent":"code","state":"busy"},{"id":"ses_f3c4351f7ffegIB7hG5Gz08O2p","sessionID":"ses_f3c4351f7ffegIB7hG5Gz08O2p","label":"Review Atlas recovery safety (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c460af9ffegFtpDQxOyzwjXg","sessionID":"ses_f3c460af9ffegFtpDQxOyzwjXg","label":"Build focused Atlas verification (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c52514affezdTXZoKxZL1HmE","sessionID":"ses_f3c52514affezdTXZoKxZL1HmE","label":"Map Max Atlas gaps (@explore subagent)","agent":"explore","state":"completed"}],"messages":[{"id":"board_e4826a92-ab55-4784-86ca-f8de7ade01e3","timestamp":1789990610519,"from":"ses_f3c4351f7ffegIB7hG5Gz08O2p","to":"main","type":"VETO","body":"Material introduced safety blocker: AtlasRouteMemoryFactory.kt:12-15 treats Settings.Global.BOOT_COUNT change as proof physical/vendor state reset; AOSP android-16.0.0_r1 PowerManagerService.java:1351-1357 calls incrementBootCount unconditionally on PHASE_THIRD_PARTY_APPS_CAN_START, and :5355-5365 unconditionally persists count+1. This phase runs again after framework/system_server restart without kernel reboot. Evidence fetched directly: https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/services/core/java/com/android/server/power/PowerManagerService.java#L1351-L1357 and #L5355-L5365. Failure trace: quarantine at count6/gen7 -> framework-only restart (same kernel/sysfs state) -> new factory count7/gen8 -> isQuarantinedIn(8,0)=false -> writer retried; currentEntry also resets unsafe counters. Prechange constant0 did NOT clear on this event, so this is introduced, unlike pruning/IO. Need actual kernel-boot identity or independent proof of physical reset; otherwise changed BOOT_COUNT must stay conservative unknown for quarantine expiration. Tests currently treat supplied generation change as reboot, do not falsify framework-only restart. No code/builds run; source-grounded counterexample, device reproduction remains unperformed.","fromLabel":"Review Atlas recovery safety (@general subagent)","toLabel":"تطوير Max أطلس في المشروع"},{"id":"board_9136bb6c-b13d-415e-9797-b30693ad214e","timestamp":1789990658444,"from":"main","to":"ses_f3c4351f7ffegIB7hG5Gz08O2p","type":"INFO","body":"Material refinement now in diff: legacy boot0 quarantine is anchored to CURRENT known boot during quarantinedRoutes(), without releasing now; NEXT known reboot then safely expires it. Otherwise legacy records would remain stuck forever, contradicting autonomy. Anchoring write failure keeps original boot0, thus keeps quarantine. To avoid migration erasing other records at capacity, pruneIfNeeded now returns immediately when updating an existing name (new-entry wholesale pruning remains pre-existing limitation). Added two regression tests legacy->known->nextBoot, full-store anchor preserving different-target quarantine. Please review final form including this.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Review Atlas recovery safety (@general subagent)"}],"hasMore":false,"cursor":"board_9136bb6c-b13d-415e-9797-b30693ad214e"}
```

---

## Assistant (Code · GPT-6 Astra · 54.5s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/PerAppRecoveryStore.kt",
  "offset": 15,
  "limit": 75
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/PerAppRecoveryStore.kt</path>
<type>file</type>
<content>
15:         val packageName: String,
16:         val gpuNode: String,
17:         val gpuGovernor: String,
18:         val gpuMin: String,
19:         val gpuMax: String,
20:         val cpuGovernors: Map<String, String>,
21:         val cpuMinFreqs: Map<String, String> = emptyMap(),
22:         val cpuMaxFreqs: Map<String, String> = emptyMap(),
23:         val zenMode: String = "",
24:         val peakRefresh: String = "",
25:         val minRefresh: String = "",
26:         val vendorRefreshNamespace: String = "",
27:         val vendorRefreshKey: String = "",
28:         val vendorRefreshValue: String = "",
29:         val vendorRefreshAltKey: String = "",
30:         val vendorRefreshAltValue: String = "",
31:         val thermalProfile: String = "",
32:         val hwUiProp: String = "",
33:         val disableHwProp: String = "",
34:         val hapticEnabled: String = "",
35:     )
36: 
37: 
38:     @Synchronized
39:     fun markActive(packageName: String, baselines: Map<String, String>) {
40:         val json = JSONObject().apply {
41:             put("active", true)
42:             put("package", packageName)
43:             put("pid", android.os.Process.myPid())
44:             put("boot_id", bootId())
45:             val cpu = JSONObject()
46:             baselines.filterKeys { it.startsWith("cpu_governor:") }.forEach { (k, v) ->
47:                 cpu.put(k.removePrefix("cpu_governor:"), v)
48:             }
49:             put("cpu_governors", cpu)
50:             put("gpu_node", baselines["gpu_node"].orEmpty())
51:             put("gpu_governor", baselines["gpu_governor"].orEmpty())
52:             put("gpu_min_freq", baselines["gpu_min_freq"].orEmpty())
53:             put("gpu_max_freq", baselines["gpu_max_freq"].orEmpty())
54:         }
55:         write(json)
56:     }
57: 
58:     @Synchronized
59:     fun capture(snapshot: Snapshot) {
60:         val json = JSONObject().apply {
61:             put("active", true)
62:             put("package", snapshot.packageName)
63:             put("pid", snapshot.pid)
64:             put("boot_id", snapshot.bootId)
65:             put("gpu_node", snapshot.gpuNode)
66:             put("gpu_governor", snapshot.gpuGovernor)
67:             put("gpu_min_freq", snapshot.gpuMin)
68:             put("gpu_max_freq", snapshot.gpuMax)
69:             put("zen_mode", snapshot.zenMode)
70:             put("peak_refresh", snapshot.peakRefresh)
71:             put("min_refresh", snapshot.minRefresh)
72:             put("thermal_profile", snapshot.thermalProfile)
73:             put("hw_ui_prop", snapshot.hwUiProp)
74:             put("disable_hw_prop", snapshot.disableHwProp)
75:             put("haptic_enabled", snapshot.hapticEnabled)
76:             val cpu = JSONObject()
77:             snapshot.cpuGovernors.forEach { (k, v) -> cpu.put(k, v) }
78:             put("cpu_governors", cpu)
79:             val cpuMin = JSONObject()
80:             snapshot.cpuMinFreqs.forEach { (k, v) -> cpuMin.put(k, v) }
81:             put("cpu_min_freqs", cpuMin)
82:             val cpuMax = JSONObject()
83:             snapshot.cpuMaxFreqs.forEach { (k, v) -> cpuMax.put(k, v) }
84:             put("cpu_max_freqs", cpuMax)
85:             put("vendor_refresh_namespace", snapshot.vendorRefreshNamespace)
86:             put("vendor_refresh_key", snapshot.vendorRefreshKey)
87:             put("vendor_refresh_value", snapshot.vendorRefreshValue)
88:             put("vendor_refresh_alt_key", snapshot.vendorRefreshAltKey)
89:             put("vendor_refresh_alt_value", snapshot.vendorRefreshAltValue)

(Showing lines 15-89 of 199. Use offset=90 to continue.)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/PerAppRecoveryStore.kt",
  "offset": 166,
  "limit": 40
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/PerAppRecoveryStore.kt</path>
<type>file</type>
<content>
166:         if (state.minRefresh.isNotBlank()) ok = putSetting("system", "min_refresh_rate", state.minRefresh) && ok
167:         if (state.hapticEnabled.isNotBlank()) ok = putSetting("system", "haptic_feedback_enabled", state.hapticEnabled) && ok
168:         if (state.thermalProfile.isNotBlank()) ok = setProp("sys.thermal.profile", state.thermalProfile) && ok
169:         if (state.hwUiProp.isNotBlank()) ok = setProp("persist.sys.ui.hw", state.hwUiProp) && ok
170:         if (state.disableHwProp.isNotBlank()) ok = setProp("debug.viewroot.disableHW", state.disableHwProp) && ok
171:         return ok
172:     }
173: 
174:     private fun putSetting(namespace: String, key: String, value: String): Boolean {
175:         if (namespace !in setOf("system", "secure", "global")) return false
176:         return RootFileAccess.exec("settings put $namespace ${quote(key)} ${quote(value)}") == 0
177:     }
178: 
179:     private fun setProp(key: String, value: String): Boolean =
180:         RootFileAccess.exec("setprop ${quote(key)} ${quote(value)}") == 0
181: 
182:     private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
183: 
184:     @Synchronized
185:     fun clear() { runCatching { file.delete() } }
186: 
187:     fun bootId(): String = runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("")
188: 
189:     private fun write(value: JSONObject) {
190:         val tmp = File(file.parentFile, file.name + ".tmp")
191:         tmp.parentFile?.mkdirs()
192:         tmp.outputStream().use { out ->
193:             out.write(value.toString().toByteArray(StandardCharsets.UTF_8))
194:             out.flush()
195:             runCatching { out.fd.sync() }
196:         }
197:         if (!tmp.renameTo(file)) throw IllegalStateException("failed to replace ${file.absolutePath}")
198:     }
199: }

(End of file - total 199 lines)
</content>
```

**Tool: webfetch**

**Input:**
```json
{
  "url": "https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/services/core/java/com/android/server/power/PowerManagerService.java",
  "format": "text",
  "timeout": 30
}
```

**Output:**
```
/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.power;

import static android.hardware.display.DisplayManagerInternal.DisplayPowerRequest.policyToString;
import static android.os.IServiceManager.DUMP_FLAG_PRIORITY_CRITICAL;
import static android.os.PowerManager.GO_TO_SLEEP_REASON_DISPLAY_GROUPS_TURNED_OFF;
import static android.os.PowerManager.GO_TO_SLEEP_REASON_DISPLAY_GROUP_REMOVED;
import static android.os.PowerManager.WAKE_REASON_DISPLAY_GROUP_ADDED;
import static android.os.PowerManager.WAKE_REASON_DISPLAY_GROUP_TURNED_ON;
import static android.os.PowerManagerInternal.MODE_DEVICE_IDLE;
import static android.os.PowerManagerInternal.MODE_DISPLAY_INACTIVE;
import static android.os.PowerManagerInternal.WAKEFULNESS_ASLEEP;
import static android.os.PowerManagerInternal.WAKEFULNESS_AWAKE;
import static android.os.PowerManagerInternal.WAKEFULNESS_DOZING;
import static android.os.PowerManagerInternal.WAKEFULNESS_DREAMING;
import static android.os.PowerManagerInternal.isInteractive;
import static android.os.PowerManagerInternal.wakefulnessToString;
import static android.service.dreams.Flags.allowDreamWhenPostured;

import static com.android.internal.util.LatencyTracker.ACTION_TURN_ON_SCREEN;
import static com.android.server.deviceidle.Flags.disableWakelocksInLightIdle;
import static com.android.server.display.DisplayDeviceConfig.INVALID_BRIGHTNESS_IN_CONFIG;
import static com.android.server.display.brightness.BrightnessUtils.isValidBrightnessValue;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_UNKNOWN;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_WAKE_LOCK_DEATH;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.SynchronousUserSwitchObserver;
import android.app.compat.CompatChanges;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledSince;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.PermissionChecker;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.SensorManager;
import android.hardware.SystemSensorManager;
import android.hardware.devicestate.DeviceState;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.display.AmbientDisplayConfiguration;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.power.Boost;
import android.hardware.power.Mode;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.BatteryManagerInternal;
import android.os.BatterySaverPolicyConfig;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.IScreenTimeoutPolicyListener;
import android.os.IWakeLockCallback;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelDuration;
import android.os.PowerManager;
import android.os.PowerManager.GoToSleepReason;
import android.os.PowerManager.ServiceType;
import android.os.PowerManager.WakeReason;
import android.os.PowerManagerInternal;
import android.os.PowerSaveState;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.os.WorkSource.WorkChain;
import android.provider.DeviceConfigInterface;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.service.dreams.DreamManagerInternal;
import android.sysprop.PowerProperties;
import android.util.ArrayMap;
import android.util.IntArray;
import android.util.KeyValueListParser;
import android.util.LongArray;
import android.util.PrintWriterPrinter;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TimeUtils;
import android.util.proto.ProtoOutputStream;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.KeyEvent;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.IBatteryStats;
import com.android.internal.display.BrightnessSynchronizer;
import com.android.internal.foldables.FoldGracePeriodProvider;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.LatencyTracker;
import com.android.internal.util.Preconditions;
import com.android.server.EventLogTags;
import com.android.server.LockGuard;
import com.android.server.PackageWatchdog;
import com.android.server.ServiceThread;
import com.android.server.SystemService;
import com.android.server.UiThread;
import com.android.server.Watchdog;
import com.android.server.am.BatteryStatsService;
import com.android.server.display.feature.DeviceConfigParameterProvider;
import com.android.server.lights.LightsManager;
import com.android.server.lights.LogicalLight;
import com.android.server.policy.WindowManagerPolicy;
import com.android.server.power.AmbientDisplaySuppressionController.AmbientDisplaySuppressionChangedCallback;
import com.android.server.power.batterysaver.BatterySaverController;
import com.android.server.power.batterysaver.BatterySaverPolicy;
import com.android.server.power.batterysaver.BatterySaverStateMachine;
import com.android.server.power.batterysaver.BatterySavingStats;
import com.android.server.power.feature.PowerManagerFlags;

import dalvik.annotation.optimization.NeverCompile;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * The power manager service is responsible for coordinating power management
 * functions on the device.
 */
public final class PowerManagerService extends SystemService
        implements Watchdog.Monitor {
    private static final String TAG = "PowerManagerService";

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SPEW = DEBUG && true;

    // Message: Sent when a user activity timeout occurs to update the power state.
    private static final int MSG_USER_ACTIVITY_TIMEOUT = 1;
    // Message: Sent when the device enters or exits a dreaming or dozing state.
    private static final int MSG_SANDMAN = 2;
    // Message: Sent when the screen brightness boost expires.
    private static final int MSG_SCREEN_BRIGHTNESS_BOOST_TIMEOUT = 3;
    // Message: Polling to look for long held wake locks.
    private static final int MSG_CHECK_FOR_LONG_WAKELOCKS = 4;
    // Message: Sent when an attentive timeout occurs to update the power state.
    private static final int MSG_ATTENTIVE_TIMEOUT = 5;

    // Message: Sent when the policy want to release all timeout override wake locks.
    private static final int MSG_RELEASE_ALL_OVERRIDE_WAKE_LOCKS = 6;

    // Dirty bit: mWakeLocks changed
    private static final int DIRTY_WAKE_LOCKS = 1 << 0;
    // Dirty bit: mWakefulness changed
    private static final int DIRTY_WAKEFULNESS = 1 << 1;
    // Dirty bit: user activity was poked or may have timed out
    private static final int DIRTY_USER_ACTIVITY = 1 << 2;
    // Dirty bit: actual display power state was updated asynchronously
    private static final int DIRTY_ACTUAL_DISPLAY_POWER_STATE_UPDATED = 1 << 3;
    // Dirty bit: mBootCompleted changed
    private static final int DIRTY_BOOT_COMPLETED = 1 << 4;
    // Dirty bit: settings changed
    private static final int DIRTY_SETTINGS = 1 << 5;
    // Dirty bit: mIsPowered changed
    private static final int DIRTY_IS_POWERED = 1 << 6;
    // Dirty bit: mStayOn changed
    private static final int DIRTY_STAY_ON = 1 << 7;
    // Dirty bit: battery state changed
    private static final int DIRTY_BATTERY_STATE = 1 << 8;
    // Dirty bit: proximity state changed
    private static final int DIRTY_PROXIMITY_POSITIVE = 1 << 9;
    // Dirty bit: dock state changed
    private static final int DIRTY_DOCK_STATE = 1 << 10;
    // Dirty bit: brightness boost changed
    private static final int DIRTY_SCREEN_BRIGHTNESS_BOOST = 1 << 11;
    // Dirty bit: sQuiescent changed
    private static final int DIRTY_QUIESCENT = 1 << 12;
    // Dirty bit: attentive timer may have timed out
    private static final int DIRTY_ATTENTIVE = 1 << 14;
    // Dirty bit: display group wakefulness has changed
    private static final int DIRTY_DISPLAY_GROUP_WAKEFULNESS = 1 << 16;
    // Dirty bit: device postured state has changed
    private static final int DIRTY_POSTURED_STATE = 1 << 17;

    // Summarizes the state of all active wakelocks.
    static final int WAKE_LOCK_CPU = 1 << 0;
    static final int WAKE_LOCK_SCREEN_BRIGHT = 1 << 1;
    static final int WAKE_LOCK_SCREEN_DIM = 1 << 2;
    static final int WAKE_LOCK_BUTTON_BRIGHT = 1 << 3;
    static final int WAKE_LOCK_PROXIMITY_SCREEN_OFF = 1 << 4;
    static final int WAKE_LOCK_STAY_AWAKE = 1 << 5; // only set if already awake
    static final int WAKE_LOCK_DOZE = 1 << 6;
    static final int WAKE_LOCK_DRAW = 1 << 7;
    static final int WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE = 1 << 8;

    // Summarizes the user activity state.
    static final int USER_ACTIVITY_SCREEN_BRIGHT = 1 << 0;
    static final int USER_ACTIVITY_SCREEN_DIM = 1 << 1;
    static final int USER_ACTIVITY_SCREEN_DREAM = 1 << 2;

    // Default timeout in milliseconds.  This is only used until the settings
    // provider populates the actual default value (R.integer.def_screen_off_timeout).
    static final int DEFAULT_SCREEN_OFF_TIMEOUT = 15 * 1000;
    private static final int DEFAULT_SLEEP_TIMEOUT = -1;

    // Screen brightness boost timeout.
    // Hardcoded for now until we decide what the right policy should be.
    // This should perhaps be a setting.
    private static final int SCREEN_BRIGHTNESS_BOOST_TIMEOUT = 5 * 1000;

    // How long a partial wake lock must be held until we consider it a long wake lock.
    static final long MIN_LONG_WAKE_CHECK_INTERVAL = 60*1000;

    // Default setting for double tap to wake.
    private static final int DEFAULT_DOUBLE_TAP_TO_WAKE = 0;

    // System property indicating that the screen should remain off until an explicit user action
    private static final String SYSTEM_PROPERTY_QUIESCENT = "ro.boot.quiescent";

    // System Property indicating that retail demo mode is currently enabled.
    private static final String SYSTEM_PROPERTY_RETAIL_DEMO_ENABLED = "sys.retaildemo.enabled";

    // System property for last reboot reason
    private static final String SYSTEM_PROPERTY_REBOOT_REASON = "sys.boot.reason";

    // Possible reasons for shutting down or reboot for use in
    // SYSTEM_PROPERTY_REBOOT_REASON(sys.boot.reason) which is set by bootstat
    private static final String REASON_SHUTDOWN = "shutdown";
    private static final String REASON_REBOOT = "reboot";
    private static final String REASON_USERREQUESTED = "shutdown,userrequested";
    private static final String REASON_THERMAL_SHUTDOWN = "shutdown,thermal";
    private static final String REASON_LOW_BATTERY = "shutdown,battery";
    private static final String REASON_BATTERY_THERMAL_STATE = "shutdown,thermal,battery";

    static final String TRACE_SCREEN_ON = "Screen turning on";

    /** If turning screen on takes more than this long, we show a warning on logcat. */
    private static final int SCREEN_ON_LATENCY_WARNING_MS = 200;

    /** Constants for {@link #shutdownOrRebootInternal} */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({HALT_MODE_SHUTDOWN, HALT_MODE_REBOOT, HALT_MODE_REBOOT_SAFE_MODE})
    public @interface HaltMode {}
    private static final int HALT_MODE_SHUTDOWN = 0;
    private static final int HALT_MODE_REBOOT = 1;
    private static final int HALT_MODE_REBOOT_SAFE_MODE = 2;

    /**
     * How stale we'll allow the enhanced discharge prediction values to get before considering them
     * invalid.
     */
    private static final long ENHANCED_DISCHARGE_PREDICTION_TIMEOUT_MS = 30 * 60 * 1000L;

    /**
     * The minimum amount of time between sending consequent
     * {@link PowerManager#ACTION_ENHANCED_DISCHARGE_PREDICTION_CHANGED} broadcasts.
     */
    private static final long ENHANCED_DISCHARGE_PREDICTION_BROADCAST_MIN_DELAY_MS = 60 * 1000L;

    /**
     * Apps targeting Android V and above need to define
     * {@link android.Manifest.permission#TURN_SCREEN_ON} in their manifest for
     * {@link android.os.PowerManager#ACQUIRE_CAUSES_WAKEUP} to have any effect.
     * Note that most applications should use {@link android.R.attr#turnScreenOn} or
     * {@link android.app.Activity#setTurnScreenOn(boolean)} instead, as this prevents the
     * previous foreground app from being resumed first when the screen turns on.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT)
    public static final long REQUIRE_TURN_SCREEN_ON_PERMISSION = 216114297L;

    /** Reason ID for holding display suspend blocker. */
    private static final String HOLDING_DISPLAY_SUSPEND_BLOCKER = "holding display";

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

    /** Display group IDs representing only DEFAULT_DISPLAY_GROUP. */
    private static final IntArray DEFAULT_DISPLAY_GROUP_IDS =
            IntArray.wrap(new int[]{Display.DEFAULT_DISPLAY_GROUP});

    private final Context mContext;
    private final ServiceThread mHandlerThread;
    private final Handler mHandler;
    private final FoldGracePeriodProvider mFoldGracePeriodProvider;
    private final AmbientDisplayConfiguration mAmbientDisplayConfiguration;
    @Nullable
    private final BatterySaverStateMachine mBatterySaverStateMachine;
    private final LowPowerStandbyController mLowPowerStandbyController;
    private final AttentionDetector mAttentionDetector;
    private final FaceDownDetector mFaceDownDetector;
    private final ScreenUndimDetector mScreenUndimDetector;
    private final BinderService mBinderService;
    private final LocalService mLocalService;
    private final NativeWrapper mNativeWrapper;
    private final SystemPropertiesWrapper mSystemProperties;
    private final Clock mClock;
    private final Injector mInjector;
    private final PermissionCheckerWrapper mPermissionCheckerWrapper;
    private final PowerPropertiesWrapper mPowerPropertiesWrapper;
    private final DeviceConfigParameterProvider mDeviceConfigProvider;
    // True if battery saver is supported on this device.
    private final boolean mBatterySaverSupported;

    private final PowerManagerFlags mFeatureFlags;

    private boolean mDisableScreenWakeLocksWhileCached;

    private LightsManager mLightsManager;
    private BatteryManagerInternal mBatteryManagerInternal;
    private DisplayManagerInternal mDisplayManagerInternal;
    private DisplayManager mDisplayManager;
    private IBatteryStats mBatteryStats;
    private WindowManagerPolicy mPolicy;
    private Notifier mNotifier;
    private WirelessChargerDetector mWirelessChargerDetector;
    private SettingsObserver mSettingsObserver;
    private DreamManagerInternal mDreamManager;
    private LogicalLight mAttentionLight;

    private final InattentiveSleepWarningController mInattentiveSleepWarningOverlayController;
    private final AmbientDisplaySuppressionController mAmbientDisplaySuppressionController;

    private final Object mLock = LockGuard.installNewLock(LockGuard.INDEX_POWER);

    // A bitfield that indicates what parts of the power state have
    // changed and need to be recalculated.
    private int mDirty;

    // Indicates whether the device is awake or asleep or somewhere in between.
    // This is distinct from the screen power state, which is managed separately.
    // Do not access directly; always use {@link #setWakefulness} and {@link getWakefulness}.
    private int mWakefulnessRaw;
    private boolean mWakefulnessChanging;

    // True if MSG_SANDMAN has been scheduled.
    private boolean mSandmanScheduled;

    // Table of all suspend blockers.
    // There should only be a few of these.
    private final ArrayList<SuspendBlocker> mSuspendBlockers = new ArrayList<>();

    // Table of all wake locks acquired by applications.
    private final ArrayList<WakeLock> mWakeLocks = new ArrayList<>();

    // A bitfield that summarizes the state of all active wakelocks.
    private int mWakeLockSummary;

    // Have we scheduled a message to check for long wake locks?  This is when we will check.
    private long mNotifyLongScheduled;

    // Last time we checked for long wake locks.
    private long mNotifyLongDispatched;

    // The time we decided to do next long check.
    private long mNotifyLongNextCheck;

    // If true, instructs the display controller to wait for the proximity sensor to
    // go negative before turning the screen on.
    private boolean mRequestWaitForNegativeProximity;

    // Timestamp of the last time the device was awoken or put to sleep.
    private long mLastGlobalWakeTime;
    private long mLastGlobalSleepTime;

    // Timestamp (in the elapsed realtime timebase) of the last time was awoken or put to sleep.
    private long mLastGlobalWakeTimeRealtime;
    private long mLastGlobalSleepTimeRealtime;

    // Last reason the device went to sleep.
    private @WakeReason int mLastGlobalWakeReason;
    private @GoToSleepReason int mLastGlobalSleepReason;

    // Timestamp of last time power boost interaction was sent.
    private long mLastInteractivePowerHintTime;

    // Timestamp of the last screen brightness boost.
    private long mLastScreenBrightnessBoostTime;
    private boolean mScreenBrightnessBoostInProgress;

    private final PowerGroupWakefulnessChangeListener mPowerGroupWakefulnessChangeListener;

    // The suspend blocker used to keep the CPU alive while the device is booting.
    private final SuspendBlocker mBootingSuspendBlocker;

    // True if the wake lock suspend blocker has been acquired.
    private boolean mHoldingBootingSuspendBlocker;

    // The suspend blocker used to keep the CPU alive when an application has acquired
    // a wake lock.
    private final SuspendBlocker mWakeLockSuspendBlocker;

    // True if the wake lock suspend blocker has been acquired.
    private boolean mHoldingWakeLockSuspendBlocker;

    // The suspend blocker used to keep the CPU alive when the display is on, the
    // display is getting ready or there is user activity (in which case the display
    // must be on).
    private final SuspendBlocker mDisplaySuspendBlocker;

    // True if the display suspend blocker has been acquired.
    private boolean mHoldingDisplaySuspendBlocker;

    // True if systemReady() has been called.
    private boolean mSystemReady;

    // True if boot completed occurred. We keep the screen on until this happens.
    // The screen will be off if we are in quiescent mode.
    private boolean mBootCompleted;

    // True if auto-suspend mode is enabled.
    // Refer to autosuspend.h.
    private boolean mHalAutoSuspendModeEnabled;

    // True if the device uses auto-suspend mode.
    private final boolean mUseAutoSuspend;

    // True if interactive mode is enabled.
    // Refer to power.h.
    private boolean mHalInteractiveModeEnabled;

    // True if the device is plugged into a power source.
    private boolean mIsPowered;

    // The current plug type, such as BatteryManager.BATTERY_PLUGGED_WIRELESS.
    private int mPlugType;

    // The current battery level percentage.
    private int mBatteryLevel;

    // The amount of battery drained while the device has been in a dream state.
    private int mDreamsBatteryLevelDrain;

    // True if updatePowerStateLocked() is already in progress.
    // TODO(b/215518989): Remove this once transactions are in place
    private boolean mUpdatePowerStateInProgress;

    /**
     * The lock that should be held when interacting with {@link #mEnhancedDischargeTimeElapsed},
     * {@link #mLastEnhancedDischargeTimeUpdatedElapsed}, and
     * {@link #mEnhancedDischargePredictionIsPersonalized}.
     */
    private final Object mEnhancedDischargeTimeLock = new Object();

    /**
     * The time (in the elapsed realtime timebase) at which the battery level will reach 0%. This
     * is provided as an enhanced estimate and only valid if
     * {@link #mLastEnhancedDischargeTimeUpdatedElapsed} is greater than 0.
     */
    @GuardedBy("mEnhancedDischargeTimeLock")
    private long mEnhancedDischargeTimeElapsed;

    /**
     * Timestamp (in the elapsed realtime timebase) of last update to enhanced battery estimate
     * data.
     */
    @GuardedBy("mEnhancedDischargeTimeLock")
    private long mLastEnhancedDischargeTimeUpdatedElapsed;

    /**
     * Whether or not the current enhanced discharge prediction is personalized to the user.
     */
    @GuardedBy("mEnhancedDischargeTimeLock")
    private boolean mEnhancedDischargePredictionIsPersonalized;

    // The current dock state.
    private int mDockState = Intent.EXTRA_DOCK_STATE_UNDOCKED;

    /**
     * Whether the device is upright and stationary.
     */
    private boolean mDevicePostured;

    // True to decouple auto-suspend mode from the display state.
    private boolean mDecoupleHalAutoSuspendModeFromDisplayConfig;

    // True to decouple interactive mode from the display state.
    private boolean mDecoupleHalInteractiveModeFromDisplayConfig;

    // True if the device should wake up when plugged or unplugged.
    private boolean mWakeUpWhenPluggedOrUnpluggedConfig;

    // True if the device should wake up when plugged or unplugged in theater mode.
    private boolean mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig;

    // True if the device should suspend when the screen is off due to proximity.
    private boolean mSuspendWhenScreenOffDueToProximityConfig;

    // Default value for attentive timeout.
    private int mAttentiveTimeoutConfig;

    // True if dreams are supported on this device.
    private boolean mDreamsSupportedConfig;

    // Default value for dreams enabled
    private boolean mDreamsEnabledByDefaultConfig;

    // Default value for dreams activate-on-sleep
    private boolean mDreamsActivatedOnSleepByDefaultConfig;

    // Default value for dreams activate-on-dock
    private boolean mDreamsActivatedOnDockByDefaultConfig;

    /** Default value for whether dreams are activated when postured (stationary + upright) */
    private boolean mDreamsActivatedWhilePosturedByDefaultConfig;

    // True if dreams can run while not plugged in.
    private boolean mDreamsEnabledOnBatteryConfig;

    // Minimum battery level to allow dreaming when powered.
    // Use -1 to disable this safety feature.
    private int mDreamsBatteryLevelMinimumWhenPoweredConfig;

    // Minimum battery level to allow dreaming when not powered.
    // Use -1 to disable this safety feature.
    private int mDreamsBatteryLevelMinimumWhenNotPoweredConfig;

    // If the battery level drops by this percentage and the user activity timeout
    // has expired, then assume the device is receiving insufficient current to charge
    // effectively and terminate the dream.  Use -1 to disable this safety feature.
    private int mDreamsBatteryLevelDrainCutoffConfig;

    // Whether dreams should be disabled when ambient mode is suppressed.
    private boolean mDreamsDisabledByAmbientModeSuppressionConfig;

    // True if dreams are enabled by the user.
    private boolean mDreamsEnabledSetting;

    // True if dreams should be activated on sleep.
    private boolean mDreamsActivateOnSleepSetting;

    // True if dreams should be activated on dock.
    private boolean mDreamsActivateOnDockSetting;

    /** Whether dreams should be activated when device is postured (stationary and upright) */
    private boolean mDreamsActivateWhilePosturedSetting;

    // True if doze should not be started until after the screen off transition.
    private boolean mDozeAfterScreenOff;

    // True if bright policy should be applied when we have entered dozing wakefulness but haven't
    // started doze component.
    private boolean mBrightWhenDozingConfig;

    // The minimum screen off timeout, in milliseconds.
    private long mMinimumScreenOffTimeoutConfig;

    // The screen dim duration, in milliseconds.
    // This is subtracted from the end of the screen off timeout so the
    // minimum screen off timeout should be longer than this.
    private long mMaximumScreenDimDurationConfig;

    // The maximum screen dim time expressed as a ratio relative to the screen
    // off timeout.  If the screen off timeout is very short then we want the
    // dim timeout to also be quite short so that most of the time is spent on.
    // Otherwise the user won't get much screen on time before dimming occurs.
    private float mMaximumScreenDimRatioConfig;

    // Whether device supports double tap to wake.
    private boolean mSupportsDoubleTapWakeConfig;

    // The screen off timeout setting value in milliseconds.
    private long mScreenOffTimeoutSetting;

    // Default for attentive warning duration.
    private long mAttentiveWarningDurationConfig;

    // The sleep timeout setting value in milliseconds.
    private long mSleepTimeoutSetting;

    // How long to show a warning message to user before the device goes to sleep
    // after long user inactivity, even if wakelocks are held.
    private long mAttentiveTimeoutSetting;

    // The maximum allowable screen off timeout according to the device
    // administration policy.  Overrides other settings.
    private long mMaximumScreenOffTimeoutFromDeviceAdmin = Long.MAX_VALUE;

    // The stay on while plugged in setting.
    // A bitfield of battery conditions under which to make the screen stay on.
    private int mStayOnWhilePluggedInSetting;

    // True if the device should stay on.
    private boolean mStayOn;

    // True if the lights should stay off until an explicit user action.
    private static boolean sQuiescent;

    // True if the proximity sensor reads a positive result.
    private boolean mProximityPositive;

    // Indicates that we have already intercepted the power key to temporarily ignore the proximity
    // wake lock and turn the screen back on. This should get reset when prox reads 'far' again
    // (when {@link #mProximityPositive} is set to false).
    private boolean mInterceptedPowerKeyForProximity;

    // Screen brightness setting limits.
    public final float mScreenBrightnessMinimum;
    public final float mScreenBrightnessMaximum;
    public final float mScreenBrightnessDefault;
    public final float mScreenBrightnessDim;

    // Value we store for tracking face down behavior.
    @VisibleForTesting
    boolean mIsFaceDown = false;
    private long mLastFlipTime = 0L;

    // The window manager has determined the user to be inactive via other means.
    // Set this to false to disable.
    private boolean mUserInactiveOverrideFromWindowManager;

    // The next possible user activity timeout after being explicitly told the user is inactive.
    // Set to -1 when not told the user is inactive since the last period spent dozing or asleep.
    private long mOverriddenTimeout = -1;

    // The user activity timeout override from the window manager
    // to allow the current foreground activity to override the user activity timeout.
    // Use -1 to disable.
    private long mUserActivityTimeoutOverrideFromWindowManager = -1;

    // The screen state to use while dozing.
    private int mDozeScreenStateOverrideFromDreamManager = Display.STATE_UNKNOWN;

    private int mDozeScreenStateOverrideReasonFromDreamManager = Display.STATE_REASON_UNKNOWN;

    // The screen brightness between 1 and 255 to use while dozing.
    private int mDozeScreenBrightnessOverrideFromDreamManager = PowerManager.BRIGHTNESS_DEFAULT;

    /**
     * The screen brightness between {@link PowerManager#BRIGHTNESS_MIN} and
     * {@link PowerManager.BRIGHTNESS_MAX} to use while dozing.
     */
    private float mDozeScreenBrightnessOverrideFromDreamManagerFloat =
            PowerManager.BRIGHTNESS_INVALID_FLOAT;
    private boolean mUseNormalBrightnessForDoze;

    // Keep display state when dozing.
    private boolean mDrawWakeLockOverrideFromSidekick;

    // Time when we last logged a warning about calling userActivity() without permission.
    private long mLastWarningAboutUserActivityPermission = Long.MIN_VALUE;

    // True if the battery level is currently considered low.
    private boolean mBatteryLevelLow;

    // True if we are currently in device idle mode.
    @GuardedBy("mLock")
    private boolean mDeviceIdleMode;

    // True if we are currently in light device idle mode.
    @GuardedBy("mLock")
    private boolean mLightDeviceIdleMode;

    // Set of app ids that we will respect the wake locks for while in device idle mode.
    int[] mDeviceIdleWhitelist = new int[0];

    // Set of app ids that are temporarily allowed to acquire wakelocks due to high-pri message
    int[] mDeviceIdleTempWhitelist = new int[0];

    // Set of uids that are allowed to acquire wakelocks while low power standby is active
    int[] mLowPowerStandbyAllowlist = new int[0];

    private boolean mLowPowerStandbyActive;

    private final SparseArray<UidState> mUidState = new SparseArray<>();

    // A mapping from DisplayGroup Id to PowerGroup. There is a 1-1 mapping between DisplayGroups
    // and PowerGroups. For simplicity the same ids are being used.
    @GuardedBy("mLock")
    private final SparseArray<PowerGroup> mPowerGroups = new SparseArray<>();

    // We are currently in the middle of a batch change of uids.
    private boolean mUidsChanging;

    // Some uids have actually changed while mUidsChanging was true.
    private boolean mUidsChanged;

    // True if theater mode is enabled
    private boolean mTheaterModeEnabled;

    // True if always on display is enabled
    private boolean mAlwaysOnEnabled;

    // True if double tap to wake is enabled
    private boolean mDoubleTapWakeEnabled;

    // True if we in the process of performing a forceSuspend
    private boolean mForceSuspendActive;

    // Transition to Doze is in progress.  We have transitioned to WAKEFULNESS_DOZING,
    // but the DreamService has not yet been told to start (it's an async process).
    private boolean mDozeStartInProgress;

    // Whether to keep dreaming when the device is unplugging.
    private boolean mKeepDreamingWhenUnplugging;

    @GuardedBy("mLock")
    private ScreenTimeoutOverridePolicy mScreenTimeoutOverridePolicy;

    private final class DreamManagerStateListener implements
            DreamManagerInternal.DreamManagerStateListener {
        @Override
        public void onKeepDreamingWhenUnpluggingChanged(boolean keepDreaming) {
            synchronized (mLock) {
                mKeepDreamingWhenUnplugging = keepDreaming;
            }
        }
    }

    private final class PowerGroupWakefulnessChangeListener implements
            PowerGroup.PowerGroupListener {
        @GuardedBy("mLock")
        @Override
        public void onWakefulnessChangedLocked(int groupId, int wakefulness, long eventTime,
                int reason, int uid, int opUid, String opPackageName, String details) {
            mWakefulnessChanging = true;
            mDirty |= DIRTY_WAKEFULNESS;
            mInjector.invalidateIsInteractiveCaches();
            if (wakefulness == WAKEFULNESS_AWAKE) {
                // Kick user activity to prevent newly awake group from timing out instantly.
                // The dream may end without user activity if the dream app crashes / is updated,
                // don't poke the user activity timer for these wakes.
                int flags = reason == PowerManager.WAKE_REASON_DREAM_FINISHED
                        ? PowerManager.USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS : 0;
                userActivityNoUpdateLocked(mPowerGroups.get(groupId), eventTime,
                        PowerManager.USER_ACTIVITY_EVENT_OTHER, flags, uid);
            }

            // Release wake lock when default display is not interactive.
            if (mScreenTimeoutOverridePolicy != null && groupId == Display.DEFAULT_DISPLAY_GROUP) {
                mScreenTimeoutOverridePolicy.onWakefulnessChange(mWakeLockSummary, wakefulness);
            }

            mDirty |= DIRTY_DISPLAY_GROUP_WAKEFULNESS;
            mNotifier.onGroupWakefulnessChangeStarted(groupId, wakefulness, reason, eventTime);
            updateGlobalWakefulnessLocked(eventTime, reason, uid, opUid, opPackageName, details);
            updatePowerStateLocked();
        }
    }

    private final class DisplayListener implements DisplayManager.DisplayListener {

        @Override
        public void onDisplayAdded(int displayId) {

        }

        @Override
        public void onDisplayRemoved(int displayId) {
            mNotifier.clearScreenTimeoutPolicyListeners(displayId);
        }

        @Override
        public void onDisplayChanged(int displayId) {

        }
    }

    private final class DisplayGroupPowerChangeListener implements
            DisplayManagerInternal.DisplayGroupListener {

        static final int DISPLAY_GROUP_ADDED = 0;
        static final int DISPLAY_GROUP_REMOVED = 1;
        static final int DISPLAY_GROUP_CHANGED = 2;

        @Override
        public void onDisplayGroupAdded(int groupId) {
            synchronized (mLock) {
                if (mPowerGroups.contains(groupId)) {
                    Slog.e(TAG, "Tried to add already existing group:" + groupId);
                    return;
                }
                // For now, only the default group supports sandman (dream/AOD).
                final boolean supportsSandman = groupId == Display.DEFAULT_DISPLAY_GROUP;
                final PowerGroup powerGroup = new PowerGroup(
                        groupId,
                        mPowerGroupWakefulnessChangeListener,
                        mNotifier,
                        mDisplayManagerInternal,
                        WAKEFULNESS_AWAKE,
                        /* ready= */ false,
                        supportsSandman,
                        mClock.uptimeMillis(),
                        mFeatureFlags);
                mPowerGroups.append(groupId, powerGroup);
                onPowerGroupEventLocked(DISPLAY_GROUP_ADDED, powerGroup);
            }
        }

        @Override
        public void onDisplayGroupRemoved(int groupId) {
            synchronized (mLock) {
                if (groupId == Display.DEFAULT_DISPLAY_GROUP) {
                    Slog.wtf(TAG, "Tried to remove default display group: " + groupId);
                    return;
                }
                if (!mPowerGroups.contains(groupId)) {
                    Slog.e(TAG, "Tried to remove non-existent group:" + groupId);
                    return;
                }
                onPowerGroupEventLocked(DISPLAY_GROUP_REMOVED, mPowerGroups.get(groupId));
            }
        }

        @Override
        public void onDisplayGroupChanged(int groupId) {
            synchronized (mLock) {
                if (!mPowerGroups.contains(groupId)) {
                    Slog.e(TAG, "Tried to change non-existent group: " + groupId);
                    return;
                }
                onPowerGroupEventLocked(DISPLAY_GROUP_CHANGED, mPowerGroups.get(groupId));
            }
        }
    }

    private final class ForegroundProfileObserver extends SynchronousUserSwitchObserver {
        @Override
        public void onUserSwitching(@UserIdInt int newUserId) throws RemoteException {
            synchronized (mLock) {
                mUserId = newUserId;
            }
        }

        @Override
        public void onForegroundProfileSwitch(@UserIdInt int newProfileId) throws RemoteException {
            final long now = mClock.uptimeMillis();
            synchronized (mLock) {
                mForegroundProfile = newProfileId;
                maybeUpdateForegroundProfileLastActivityLocked(now);
            }
        }
    }

    // User id corresponding to activity the user is currently interacting with.
    private @UserIdInt int mForegroundProfile;
    // User id of main profile for the current user (doesn't include managed profiles)
    private @UserIdInt int mUserId;

    // Per-profile state to track when a profile should be locked.
    private final SparseArray<ProfilePowerState> mProfilePowerState = new SparseArray<>();

    private static final class ProfilePowerState {
        // Profile user id.
        final @UserIdInt int mUserId;
        // Maximum time to lock set by admin.
        long mScreenOffTimeout;
        // Like top-level mWakeLockSummary, but only for wake locks that affect current profile.
        int mWakeLockSummary;
        // Last user activity that happened in an app running in the profile.
        long mLastUserActivityTime;
        // Whether profile has been locked last time it timed out.
        boolean mLockingNotified;

        public ProfilePowerState(@UserIdInt int userId, long screenOffTimeout, long now) {
            mUserId = userId;
            mScreenOffTimeout = screenOffTimeout;
            // Not accurate but at least won't cause immediate locking of the profile.
            mLastUserActivityTime = now;
        }
    }

    /**
     * All times are in milliseconds. These constants are kept synchronized with the system
     * global Settings. Any access to this class or its fields should be done while
     * holding the PowerManagerService.mLock lock.
     */
    private final class Constants extends ContentObserver {
        // Key names stored in the settings value.
        private static final String KEY_NO_CACHED_WAKE_LOCKS = "no_cached_wake_locks";

        private static final boolean DEFAULT_NO_CACHED_WAKE_LOCKS = true;

        // Prevent processes that are cached from holding wake locks?
        public boolean NO_CACHED_WAKE_LOCKS = DEFAULT_NO_CACHED_WAKE_LOCKS;

        private ContentResolver mResolver;
        private final KeyValueListParser mParser = new KeyValueListParser(',');

        public Constants(Handler handler) {
            super(handler);
        }

        public void start(ContentResolver resolver) {
            mResolver = resolver;
            mResolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.POWER_MANAGER_CONSTANTS), false, this);
            updateConstants();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateConstants();
        }

        private void updateConstants() {
            synchronized (mLock) {
                try {
                    mParser.setString(Settings.Global.getString(mResolver,
                            Settings.Global.POWER_MANAGER_CONSTANTS));
                } catch (IllegalArgumentException e) {
                    // Failed to parse the settings string, log this and move on
                    // with defaults.
                    Slog.e(TAG, "Bad alarm manager settings", e);
                }

                NO_CACHED_WAKE_LOCKS = mParser.getBoolean(KEY_NO_CACHED_WAKE_LOCKS,
                        DEFAULT_NO_CACHED_WAKE_LOCKS);
            }
        }

        void dump(PrintWriter pw) {
            pw.println("  Settings " + Settings.Global.POWER_MANAGER_CONSTANTS + ":");

            pw.print("    "); pw.print(KEY_NO_CACHED_WAKE_LOCKS); pw.print("=");
            pw.println(NO_CACHED_WAKE_LOCKS);
        }

        void dumpProto(ProtoOutputStream proto) {
            final long constantsToken = proto.start(PowerManagerServiceDumpProto.CONSTANTS);
            proto.write(PowerManagerServiceDumpProto.ConstantsProto.IS_NO_CACHED_WAKE_LOCKS,
                    NO_CACHED_WAKE_LOCKS);
            proto.end(constantsToken);
        }
    }

    /**
     * Wrapper around the static-native methods of PowerManagerService.
     *
     * This class exists to allow us to mock static native methods in our tests. If mocking static
     * methods becomes easier than this in the future, we can delete this class.
     */
    @VisibleForTesting
    public static class NativeWrapper {
        /** Wrapper for PowerManager.nativeInit */
        public void nativeInit(PowerManagerService service) {
            service.nativeInit();
        }

        /** Wrapper for PowerManager.nativeAcquireSuspectBlocker */
        public void nativeAcquireSuspendBlocker(String name) {
            PowerManagerService.nativeAcquireSuspendBlocker(name);
        }

        /** Wrapper for PowerManager.nativeReleaseSuspendBlocker */
        public void nativeReleaseSuspendBlocker(String name) {
            PowerManagerService.nativeReleaseSuspendBlocker(name);
        }

        /** Wrapper for PowerManager.nativeSetAutoSuspend */
        public void nativeSetAutoSuspend(boolean enable) {
            PowerManagerService.nativeSetAutoSuspend(enable);
        }

        /** Wrapper for PowerManager.nativeSetPowerBoost */
        public void nativeSetPowerBoost(int boost, int durationMs) {
            PowerManagerService.nativeSetPowerBoost(boost, durationMs);
        }

        /** Wrapper for PowerManager.nativeSetPowerMode */
        public boolean nativeSetPowerMode(int mode, boolean enabled) {
            return PowerManagerService.nativeSetPowerMode(mode, enabled);
        }

        /** Wrapper for PowerManager.nativeForceSuspend */
        public boolean nativeForceSuspend() {
            return PowerManagerService.nativeForceSuspend();
        }
    }

    /** Functional interface for providing time. */
    @VisibleForTesting
    interface Clock {
        /**
         * Returns current time in milliseconds since boot, not counting time spent in deep sleep.
         */
        long uptimeMillis();

        /**
         * Returns milliseconds since boot, including time spent in sleep.
         */
        long elapsedRealtime();
    }

    @VisibleForTesting
    static class Injector {
        Notifier createNotifier(Looper looper, Context context, IBatteryStats batteryStats,
                SuspendBlocker suspendBlocker, WindowManagerPolicy policy,
                FaceDownDetector faceDownDetector, ScreenUndimDetector screenUndimDetector,
                Executor backgroundExecutor, PowerManagerFlags powerManagerFlags) {
            return new Notifier(
                    looper, context, batteryStats, suspendBlocker, policy, faceDownDetector,
                    screenUndimDetector, backgroundExecutor, powerManagerFlags, /*injector=*/ null);
        }

        SuspendBlocker createSuspendBlocker(PowerManagerService service, String name) {
            SuspendBlocker suspendBlocker = service.new SuspendBlockerImpl(name);
            service.mSuspendBlockers.add(suspendBlocker);
            return suspendBlocker;
        }

        BatterySaverStateMachine createBatterySaverStateMachine(Object lock, Context context) {
            BatterySavingStats batterySavingStats = new BatterySavingStats(lock);
            BatterySaverPolicy batterySaverPolicy = new BatterySaverPolicy(lock, context,
                    batterySavingStats);
            BatterySaverController batterySaverController = new BatterySaverController(lock,
                    context, BackgroundThread.get().getLooper(),
                    batterySaverPolicy, batterySavingStats);
            return new BatterySaverStateMachine(lock, context, batterySaverController);
        }

        NativeWrapper createNativeWrapper() {
            return new NativeWrapper();
        }

        WirelessChargerDetector createWirelessChargerDetector(
                SensorManager sensorManager, SuspendBlocker suspendBlocker, Handler handler) {
            return new WirelessChargerDetector(sensorManager, suspendBlocker, handler);
        }

        AmbientDisplayConfiguration createAmbientDisplayConfiguration(Context context) {
            return new AmbientDisplayConfiguration(context);
        }

        AmbientDisplaySuppressionController createAmbientDisplaySuppressionController(
                @NonNull AmbientDisplaySuppressionChangedCallback callback) {
            return new AmbientDisplaySuppressionController(callback);
        }

        InattentiveSleepWarningController createInattentiveSleepWarningController() {
            return new InattentiveSleepWarningController();
        }

        FoldGracePeriodProvider createFoldGracePeriodProvider() {
            return new FoldGracePeriodProvider();
        }

        public SystemPropertiesWrapper createSystemPropertiesWrapper() {
            return new SystemPropertiesWrapper() {
                @Override
                public String get(String key, String def) {
                    return SystemProperties.get(key, def);
                }

                @Override
                public void set(String key, String val) {
                    SystemProperties.set(key, val);
                }
            };
        }

        Clock createClock() {
            return new Clock() {
                @Override
                public long uptimeMillis() {
                    return SystemClock.uptimeMillis();
                }

                @Override
                public long elapsedRealtime() {
                    return SystemClock.elapsedRealtime();
                }
            };

        }

        /**
         * Handler for asynchronous operations performed by the power manager.
         */
        Handler createHandler(Looper looper, Handler.Callback callback) {
            return new Handler(looper, callback, /* async= */ true);
        }

        void invalidateIsInteractiveCaches() {
            PowerManager.invalidateIsInteractiveCaches();
        }

        LowPowerStandbyController createLowPowerStandbyController(Context context, Looper looper) {
            return new LowPowerStandbyController(context, looper);
        }

        PermissionCheckerWrapper createPermissionCheckerWrapper() {
            return PermissionChecker::checkPermissionForDataDelivery;
        }

        PowerPropertiesWrapper createPowerPropertiesWrapper() {
            return new PowerPropertiesWrapper() {
                @Override
                public boolean waive_target_sdk_check_for_turn_screen_on() {
                    return PowerProperties.waive_target_sdk_check_for_turn_screen_on().orElse(
                            false);
                }

                @Override
                public boolean permissionless_turn_screen_on() {
                    return PowerProperties.permissionless_turn_screen_on().orElse(false);
                }
            };
        }

        DeviceConfigParameterProvider createDeviceConfigParameterProvider() {
            return new DeviceConfigParameterProvider(DeviceConfigInterface.REAL);
        }

        PowerManagerFlags getFlags() {
            return new PowerManagerFlags();
        }
    }

    /** Interface for checking an app op permission */
    @VisibleForTesting
    interface PermissionCheckerWrapper {
        /**
         * Checks whether a given data access chain described by the given {@link AttributionSource}
         * has a given permission and whether the app op that corresponds to this permission
         * is allowed.
         * See {@link PermissionChecker#checkPermissionForDataDelivery} for more details.
         *
         * @param context Context for accessing resources.
         * @param permission The permission to check.
         * @param pid The process id for which to check. Use {@link PermissionChecker#PID_UNKNOWN}
         *    if the PID is not known.
         * @param attributionSource the permission identity
         * @param message A message describing the reason the permission was checked
         * @return The permission check result which is any of
         *     {@link PermissionChecker#PERMISSION_GRANTED},
         *     {@link PermissionChecker#PERMISSION_SOFT_DENIED},
         *     or {@link PermissionChecker#PERMISSION_HARD_DENIED}.
         */
        int checkPermissionForDataDelivery(@NonNull Context context, @NonNull String permission,
                int pid, @NonNull AttributionSource attributionSource, @Nullable String message);
    }

    /** Interface for querying {@link PowerProperties} */
    @VisibleForTesting
    interface PowerPropertiesWrapper {
        /**
         * Waives the minimum target-sdk check for android.os.PowerManager#ACQUIRE_CAUSES_WAKEUP
         * and only allows the flag for apps holding android.permission.TURN_SCREEN_ON
         */
        boolean waive_target_sdk_check_for_turn_screen_on();

        /**
         * Allows apps to turn the screen on with android.os.PowerManager#ACQUIRE_CAUSES_WAKEUP
         * without being granted android.app.AppOpsManager#OP_TURN_SCREEN_ON.
         */
        boolean permissionless_turn_screen_on();
    }

    final Constants mConstants;

    private native void nativeInit();
    private static native void nativeAcquireSuspendBlocker(String name);
    private static native void nativeReleaseSuspendBlocker(String name);
    private static native void nativeSetAutoSuspend(boolean enable);
    private static native void nativeSetPowerBoost(int boost, int durationMs);
    private static native boolean nativeSetPowerMode(int mode, boolean enabled);
    private static native boolean nativeForceSuspend();

    public PowerManagerService(Context context) {
        this(context, new Injector());
    }

    @VisibleForTesting
    PowerManagerService(Context context, Injector injector) {
        super(context);

        mContext = context;
        mBinderService = new BinderService(mContext);

...275411 bytes truncated...

The tool call succeeded but the output was truncated. Full output saved to: /root/.local/share/kilo/tool-output/tool_0c3c41ee6001MCwAKO64bVHLCg
Use the Task tool to have explore agent process this file with Grep and Read (with offset/limit). Do NOT read the full file yourself - delegate to save context.
```

---

## Assistant (Code · GPT-6 Astra · 72.2s)

_Thinking:_



كشفت المراجعة سببًا مهمًا لتغيير الاختيار: عدّاد إقلاع أندرويد قد يزيد عند إعادة تشغيل خدمات النظام دون إعادة تشغيل النواة، فلا يصلح وحده لرفع حظر مسار غير آمن. سأستخدم هوية إقلاع النواة نفسها، وأبقي الحظر إذا تعذرت قراءتها.


**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasPrivilegedReadTransport.kt",
  "limit": 180
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasPrivilegedReadTransport.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import nd.max.core.atlas.AtlasFailure
4: import java.io.File
5: import java.nio.charset.StandardCharsets
6: 
7: /**
8:  * Root-backed read transport for Atlas. It has no mutation API and is selected only when root is
9:  * already cached as granted; opening Atlas never prompts for privilege. The boundary above this class
10:  * still enforces approved anchors, path safety and read budgets.
11:  */
12: class AtlasPrivilegedReadTransport : AtlasReadTransport {
13:     override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
14:         val raw = RootFileAccess.read(path)
15:             ?: return if (RootFileAccess.exists(path)) {
16:                 AtlasTransportRead.Failed(AtlasFailure.PERMISSION_DENIED, "privileged read was refused")
17:             } else {
18:                 AtlasTransportRead.Failed(AtlasFailure.ABSENT, "the interface does not exist")
19:             }
20:         val bytes = raw.toByteArray(StandardCharsets.UTF_8)
21:         if (bytes.size <= maxBytes) return AtlasTransportRead.Text(raw, truncated = false)
22:         // Truncation is cut on a character boundary: a byte slice could split a multi-byte character and
23:         // hand the parser a replacement glyph that looks like the interface's own text.
24:         val cut = String(bytes, 0, maxBytes, StandardCharsets.UTF_8).dropLastWhile { it == '\uFFFD' }
25:         return AtlasTransportRead.Text(cut, truncated = true)
26:     }
27: 
28:     override fun listNames(path: String, limit: Int): AtlasTransportList {
29:         val names = RootFileAccess.listNames(path)
30:         if (names.isEmpty() && !RootFileAccess.exists(path)) {
31:             return AtlasTransportList(emptyList(), AtlasFailure.ABSENT, "the directory does not exist")
32:         }
33:         return AtlasTransportList(
34:             names = names.take(limit),
35:             truncated = names.size > limit,
36:             reason = "listed through the authorized root read path",
37:         )
38:     }
39: 
40:     override fun canonicalPath(path: String, maxHops: Int): String? = runCatching {
41:         File(path).canonicalPath.takeIf { it.startsWith("/") }
42:     }.getOrNull()
43: }
44: 
45: /**
46:  * Chooses privileged reads when already available and falls back to the ordinary transport otherwise.
47:  * A root read failure is not hidden: fallback is attempted only for permission/backend failures, not
48:  * for a proven missing interface.
49:  */
50: class AtlasAdaptiveReadTransport(
51:     private val ordinary: AtlasReadTransport,
52:     private val privileged: AtlasReadTransport,
53:     private val privilegedAvailable: () -> Boolean,
54: ) : AtlasReadTransport {
55:     override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
56:         if (!privilegedAvailable()) return ordinary.readText(path, maxBytes)
57:         return when (val elevated = privileged.readText(path, maxBytes)) {
58:             is AtlasTransportRead.Text -> elevated
59:             is AtlasTransportRead.Failed -> when (elevated.cause) {
60:                 AtlasFailure.PERMISSION_DENIED,
61:                 AtlasFailure.BACKEND_UNAVAILABLE,
62:                 -> ordinary.readText(path, maxBytes)
63:                 else -> elevated
64:             }
65:         }
66:     }
67: 
68:     override fun listNames(path: String, limit: Int): AtlasTransportList {
69:         if (!privilegedAvailable()) return ordinary.listNames(path, limit)
70:         val elevated = privileged.listNames(path, limit)
71:         return if (elevated.cause == AtlasFailure.PERMISSION_DENIED ||
72:             elevated.cause == AtlasFailure.BACKEND_UNAVAILABLE
73:         ) {
74:             ordinary.listNames(path, limit)
75:         } else {
76:             elevated
77:         }
78:     }
79: 
80:     override fun canonicalPath(path: String, maxHops: Int): String? {
81:         val elevated = if (privilegedAvailable()) privileged.canonicalPath(path, maxHops) else null
82:         return elevated ?: ordinary.canonicalPath(path, maxHops)
83:     }
84: }

(End of file - total 84 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/PerAppRecoveryStore.kt",
  "limit": 16
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/PerAppRecoveryStore.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import org.json.JSONObject
4: import java.io.File
5: import java.nio.charset.StandardCharsets
6: 
7: /** Crash/restart journal for per-app overrides. */
8: object PerAppRecoveryStore {
9:     private const val PATH = "/data/adb/.config/MaxManager/API/per-app-recovery.json"
10:     private val file: File get() = File(PATH)
11: 
12:     data class Snapshot(
13:         val bootId: String,
14:         val pid: Int = android.os.Process.myPid(),
15:         val packageName: String,
16:         val gpuNode: String,

(Showing lines 1-16 of 199. Use offset=17 to continue.)
</content>
```

**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c4351f7ffegIB7hG5Gz08O2p",
  "reply_to": "board_e4826a92-ab55-4784-86ca-f8de7ade01e3",
  "type": "INFO",
  "body": "Accepted source-grounded blocker; replacing BOOT_COUNT with actual /proc/sys/kernel/random/boot_id via existing boot identity reader or bounded direct read, no root prompting. Will derive deterministic positive 63-bit SHA256 token from strictly valid normalized UUID; unknown0 fail closed, hash collisions can only keep quarantine longer not release within same kernel boot. Cached per process lazily. Schema remains persisted Long with legacy0 anchoring. BOOT_COUNT API and helper will be removed, tests include same kernel UUID across fresh memory process -> still quarantined, malformed IDs -> unknown."
}
```

**Output:**
```
{"id":"board_22802a15-51d4-411e-9322-38ab91dddb3c","timestamp":1789990875684,"from":"main","to":"ses_f3c4351f7ffegIB7hG5Gz08O2p","type":"INFO","body":"Accepted source-grounded blocker; replacing BOOT_COUNT with actual /proc/sys/kernel/random/boot_id via existing boot identity reader or bounded direct read, no root prompting. Will derive deterministic positive 63-bit SHA256 token from strictly valid normalized UUID; unknown0 fail closed, hash collisions can only keep quarantine longer not release within same kernel boot. Cached per process lazily. Schema remains persisted Long with legacy0 anchoring. BOOT_COUNT API and helper will be removed, tests include same kernel UUID across fresh memory process -> still quarantined, malformed IDs -> unknown.","reply_to":"board_e4826a92-ab55-4784-86ca-f8de7ade01e3","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Review Atlas recovery safety (@general subagent)","availability":{"observedAt":1789990875687,"total":1,"active":0,"inactive":1,"unknown":0,"recipientState":"completed"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients.","warning":"The direct recipient's state was completed at this post attempt. Its invocation has ended; do not expect a reply to this message. This post is stored only and does not wake or resume the recipient. Do not resume it just to deliver this note."}
```

**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c460af9ffegFtpDQxOyzwjXg",
  "type": "INFO",
  "body": "Safety reviewer found BOOT_COUNT can increment on system_server restart without kernel reboot (AOSP PowerManagerService). Main replacing factory source with actual kernel boot_id, derived positive stable token generationForBootId(String?) using normalized UUID SHA256, malformed/missing=>0. Please adjust your wiring guards to require this and forbid Settings.Global.BOOT_COUNT. No edit main files yourself. Runner source set remains same unless separate Boot helper necessary (not intended)."
}
```

**Output:**
```
{"id":"board_ff1d51f3-f95d-499f-a270-9301b756d5e6","timestamp":1789990875756,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"Safety reviewer found BOOT_COUNT can increment on system_server restart without kernel reboot (AOSP PowerManagerService). Main replacing factory source with actual kernel boot_id, derived positive stable token generationForBootId(String?) using normalized UUID SHA256, malformed/missing=>0. Please adjust your wiring guards to require this and forbid Settings.Global.BOOT_COUNT. No edit main files yourself. Runner source set remains same unless separate Boot helper necessary (not intended).","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)","availability":{"observedAt":1789990875761,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"busy"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

**Tool: board_read**

**Input:**
```json
{
  "since": "board_9136bb6c-b13d-415e-9797-b30693ad214e",
  "limit": 10
}
```

**Output:**
```
{"observedAt":1789990876102,"agent":"main","participants":[{"id":"main","sessionID":"ses_f3c539be4ffe75jztLqF6tMSH7","label":"تطوير Max أطلس في المشروع","agent":"code","state":"busy"},{"id":"ses_f3c460af9ffegFtpDQxOyzwjXg","sessionID":"ses_f3c460af9ffegFtpDQxOyzwjXg","label":"Build focused Atlas verification (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c4351f7ffegIB7hG5Gz08O2p","sessionID":"ses_f3c4351f7ffegIB7hG5Gz08O2p","label":"Review Atlas recovery safety (@general subagent)","agent":"general","state":"completed"},{"id":"ses_f3c52514affezdTXZoKxZL1HmE","sessionID":"ses_f3c52514affezdTXZoKxZL1HmE","label":"Map Max Atlas gaps (@explore subagent)","agent":"explore","state":"completed"}],"messages":[{"id":"board_ae1b1e82-8c56-4069-969a-ec4a4e55fe03","timestamp":1789990789341,"from":"ses_f3c460af9ffegFtpDQxOyzwjXg","to":"main","type":"RESULT","body":"First targeted runner successful: JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 python3 tools/test_atlas_jvm.py --json-jar /tmp/kilo/json-20260814.jar --work-dir /tmp/kilo => Kotlin PSI self-test valid accepted/invalid rejected; 9 files parsed with 0 errors; 15 real Kotlin source/test files compiled; JUnit 4.13.2 OK (39 tests), including AtlasRouteMemoryTest + AtlasAdaptiveExecutorTest + AtlasRoutePlannerTest + new AtlasRouteMemoryWiringTest. HardwareRepairExecutorTest explicitly NOT executed (android.system chain); Android compile/device unverified. Gates rerun: kt_balance759/0, selftest17/0, health all0 debt10/29/63/23, i18n0, diff --check clean. Final runner failure-path/cwd checks in progress.","fromLabel":"Build focused Atlas verification (@general subagent)","toLabel":"تطوير Max أطلس في المشروع"},{"id":"board_22802a15-51d4-411e-9322-38ab91dddb3c","timestamp":1789990875684,"from":"main","to":"ses_f3c4351f7ffegIB7hG5Gz08O2p","type":"INFO","body":"Accepted source-grounded blocker; replacing BOOT_COUNT with actual /proc/sys/kernel/random/boot_id via existing boot identity reader or bounded direct read, no root prompting. Will derive deterministic positive 63-bit SHA256 token from strictly valid normalized UUID; unknown0 fail closed, hash collisions can only keep quarantine longer not release within same kernel boot. Cached per process lazily. Schema remains persisted Long with legacy0 anchoring. BOOT_COUNT API and helper will be removed, tests include same kernel UUID across fresh memory process -> still quarantined, malformed IDs -> unknown.","reply_to":"board_e4826a92-ab55-4784-86ca-f8de7ade01e3","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Review Atlas recovery safety (@general subagent)"},{"id":"board_ff1d51f3-f95d-499f-a270-9301b756d5e6","timestamp":1789990875756,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"Safety reviewer found BOOT_COUNT can increment on system_server restart without kernel reboot (AOSP PowerManagerService). Main replacing factory source with actual kernel boot_id, derived positive stable token generationForBootId(String?) using normalized UUID SHA256, malformed/missing=>0. Please adjust your wiring guards to require this and forbid Settings.Global.BOOT_COUNT. No edit main files yourself. Runner source set remains same unless separate Boot helper necessary (not intended).","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)"}],"hasMore":false,"cursor":"board_ff1d51f3-f95d-499f-a270-9301b756d5e6"}
```

---

## User

---

## Assistant (Code · GPT-6 Astra · 85.0s)

_Thinking:_

**Evaluating BootID Generation**

I've been spending some time thinking about hardware safety and the urgency to replace code quickly. The BootID generation function using SHA256 is crucial. I need to ensure the UUID is well-formed, and it should reject any nilUUID or all-zero outputs. The JVM tests need to be deterministic, and it’s essential to validate the format correctly. I must consider that different Linux kernels could emit other versions of UUID, so using a normalized regex can help. I’ll avoid unnecessary fallbacks and simplify the code.

**Tool: apply_patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt\n@@\n-import android.provider.Settings\n@@\n-            AtlasRouteMemory.generationForBootCount(runCatching {\n-                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)\n-            }.getOrNull())\n+            // Android's BOOT_COUNT also changes on framework-only restarts. Only the kernel's\n+            // boot_id identifies the reset that makes an old hardware quarantine safe to expire.\n+            AtlasRouteMemory.generationForBootId(PerAppRecoveryStore.bootId())\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt\n@@\n import nd.max.core.atlas.AtlasStoreIo\n import org.json.JSONObject\n+import java.nio.ByteBuffer\n+import java.security.MessageDigest\n+import java.util.Locale\n@@\n-        /** Zero means unknown (including legacy records); Android's valid zero count maps to one. */\n-        fun generationForBootCount(bootCount: Int?): Long =\n-            bootCount?.takeIf { it >= 0 }?.toLong()?.plus(1L) ?: 0L\n+        /**\n+         * A stable token for a validated kernel boot UUID; zero means unknown, including legacy records.\n+         * A digest collision can only retain quarantine too long, never release it inside one boot.\n+         * The raw boot identity is neither stored nor reported.\n+         */\n+        fun generationForBootId(bootId: String?): Long {\n+            val normalized = bootId?.trim()?.lowercase(Locale.ROOT) ?: return 0L\n+            if (!BOOT_UUID.matches(normalized) || normalized == \"00000000-0000-0000-0000-000000000000\") return 0L\n+            val digest = MessageDigest.getInstance(\"SHA-256\").digest(normalized.toByteArray(Charsets.US_ASCII))\n+            return (ByteBuffer.wrap(digest).long and Long.MAX_VALUE).coerceAtLeast(1L)\n+        }\n@@\n         private const val MAX_SLUG_CHARS = 48\n+\n+        private val BOOT_UUID = Regex(\"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$\")\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt\n@@\n-    fun `unknown and invalid boot counts stay unknown while zero is a real boot`() {\n-        assertEquals(0L, AtlasRouteMemory.generationForBootCount(null))\n-        assertEquals(0L, AtlasRouteMemory.generationForBootCount(-1))\n-        assertEquals(1L, AtlasRouteMemory.generationForBootCount(0))\n-        assertEquals(2L, AtlasRouteMemory.generationForBootCount(1))\n-        assertEquals(2_147_483_648L, AtlasRouteMemory.generationForBootCount(Int.MAX_VALUE))\n+    fun `missing malformed and nil boot identities stay unknown`() {\n+        listOf(null, \"\", \"permission denied\", \"1-1-1-1-1\", \"00000000-0000-0000-0000-000000000000\").forEach {\n+            assertEquals(0L, AtlasRouteMemory.generationForBootId(it))\n+        }\n+    }\n+\n+    @Test\n+    fun `kernel boot identity is positive stable and normalized across readers`() {\n+        val id = \"550e8400-e29b-41d4-a716-446655440000\"\n+        val first = AtlasRouteMemory.generationForBootId(id)\n+        assertTrue(first > 0L)\n+        assertEquals(first, AtlasRouteMemory.generationForBootId(\"  550E8400-E29B-41D4-A716-446655440000\\n\"))\n+        assertTrue(first != AtlasRouteMemory.generationForBootId(\"550e8400-e29b-41d4-a716-446655440001\"))\n+    }\n+\n+    @Test\n+    fun `framework restart retains quarantine when the kernel boot identity is unchanged`() {\n+        val io = InMemoryIo()\n+        val bootId = \"550e8400-e29b-41d4-a716-446655440000\"\n+        AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { AtlasRouteMemory.generationForBootId(bootId) })\n+            .noteFailed(\"gpu_frequency\", \"gpu.frequency.arbiter\", rollbackVerified = false)\n+\n+        // A recreated Android framework/app has the same kernel UUID, regardless of BOOT_COUNT.\n+        val restarted = AtlasRouteMemory(io, clockMs = { 2_000L },\n+            bootGeneration = { AtlasRouteMemory.generationForBootId(bootId) })\n+        assertEquals(setOf(\"gpu.frequency.arbiter\"), restarted.quarantinedRoutes(\"gpu_frequency\"))\n+\n+        val rebooted = AtlasRouteMemory(io, clockMs = { 1_000L },\n+            bootGeneration = { AtlasRouteMemory.generationForBootId(\"550e8400-e29b-41d4-a716-446655440001\") })\n+        assertTrue(rebooted.quarantinedRoutes(\"gpu_frequency\").isEmpty())\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/docs/ai/NEXT_TASK.md\n@@\n-ربط المصنعين بقراءة `Settings.Global.BOOT_COUNT` المشتركة، وإبقاء الحجر عند تعذّر معرفة الإقلاع،\n+ربط المصنعين بهوية إقلاع النواة `/proc/sys/kernel/random/boot_id` عبر القارئ القائم\n+`PerAppRecoveryStore.bootId()`، وإبقاء الحجر عند تعذّر معرفة الإقلاع،\n@@\n اعتبار إعادة تشغيل العملية إقلاعًا جديدًا. لا توسيع لجذور الاكتشاف ولا كتابة عتاد ولا تغيير SELinux.\n+\n+**تصحيح أثناء المراجعة:** رُفض اختيار `Settings.Global.BOOT_COUNT` الأول لأن AOSP يزيده أيضًا\n+عند إعادة تشغيل إطار النظام دون النواة. هوية النواة وحدها أساس انتهاء الحجر؛ اختبار الانحدار يحافظ\n+على الحجر عند إنشاء ذاكرة جديدة بنفس UUID، ويحرره عند UUID جديد معلوم.\n*** End Patch"
}
```

**Output:**
```
Success. Updated the following files:
M manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt
M manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
M manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt
M docs/ai/NEXT_TASK.md
```

---

## Assistant (Code · GPT-6 Astra · 60.7s)

_Thinking:_



**Tool: task**

**Input:**
```json
{
  "task_id": "ses_f3c4351f7ffegIB7hG5Gz08O2p",
  "subagent_type": "general",
  "description": "Verify corrected kernel identity",
  "background": true,
  "prompt": "Substantive re-review of corrected diff for your blocker, READ ONLY no edits/build. Factory now uses existing PerAppRecoveryStore.bootId() (/proc/sys/kernel/random/boot_id direct File.readText catches errors, no root prompt), lazily cached. AtlasRouteMemory.generationForBootId strict complete UUID regex, normalized Locale.ROOT, nil rejected, SHA-256 first63bits positive, unknown0. Hash collision conservatively keeps quarantine longer not releases inside same UUID. BOOT_COUNT implementation removed. New tests missing/malformed/nil ID, normalization/different UUID, framework process restart same kernelUUID preserves quarantine and new kernelUUID clears. Main also added legacy0 anchoring to known boot without immediate release + failure-to-persist migration stays quarantined + full-store existing record update does not prune others. Re-review final source and determine if your introduced blocker resolved. No need full redo prior unchanged findings. Account for direct boot_id inaccessible on app process => unknown0 keeps quarantine; portability limitation not silent permission escalation. Document residual old new-entry pruning/crossprocess IO as pre-existing, not now fixed. Verification runner will run revised actual memory/adaptive/planner suites; no named model-family approval available, do not claim Luna. Return final decision and exact remaining blocker if any."
}
```

**Output:**
```
<task id="ses_f3c4351f7ffegIB7hG5Gz08O2p" state="running">
<summary>Background task started</summary>
<task_result>
The task is working in the background. You will be notified automatically when it finishes.
DO NOT sleep, poll for progress, ask the task for status, or duplicate this task's work — avoid working with the same files or topics it is using.
Work on non-overlapping tasks, or briefly tell the user what you launched and end your response.
</task_result>
</task>
```

**Tool: board_post**

**Input:**
```json
{
  "to": "ses_f3c460af9ffegFtpDQxOyzwjXg",
  "type": "INFO",
  "body": "Factory correction implemented now: AtlasRouteMemory.generationForBootId(PerAppRecoveryStore.bootId()), existing direct fixed-path reader no root prompt. No new source helper. Please adjust wiring test exact expected source accordingly and include PerAppRecoveryStore.kt in PSI parsing if desired. Memory tests now include UUID malformed/nil, deterministic normalized token, framework process restart same kernel UUID. No more main product edits planned pending safety."
}
```

**Output:**
```
{"id":"board_1c796171-22ea-4e1c-a2eb-bcfd3268bb47","timestamp":1789991016378,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"Factory correction implemented now: AtlasRouteMemory.generationForBootId(PerAppRecoveryStore.bootId()), existing direct fixed-path reader no root prompt. No new source helper. Please adjust wiring test exact expected source accordingly and include PerAppRecoveryStore.kt in PSI parsing if desired. Memory tests now include UUID malformed/nil, deterministic normalized token, framework process restart same kernel UUID. No more main product edits planned pending safety.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)","availability":{"observedAt":1789991016387,"total":1,"active":1,"inactive":0,"unknown":0,"recipientState":"busy"},"receipt":"Stored only. This does not confirm delivery, reading, or action, and does not wake recipients."}
```

**Tool: bash**

**Input:**
```json
{
  "command": "git diff --check",
  "description": "Check corrected kernel identity diff for whitespace"
}
```

**Output:**
```
(no output)
```

**Tool: grep**

**Input:**
```json
{
  "path": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware",
  "pattern": "generationForBootCount|Settings.Global.BOOT_COUNT|generationForBootId",
  "limit": 20
}
```

**Output:**
```
Found 2 matches
/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt:
  Line 14:             AtlasRouteMemory.generationForBootId(PerAppRecoveryStore.bootId())


/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt:
  Line 277:         fun generationForBootId(bootId: String?): Long {

```

---

## Assistant (Code · GPT-6 Astra · 34.0s)

_Thinking:_



_Thinking:_



**Tool: board_read**

**Input:**
```json
{
  "since": "board_ff1d51f3-f95d-499f-a270-9301b756d5e6",
  "limit": 10
}
```

**Output:**
```
{"observedAt":1789991057637,"agent":"main","participants":[{"id":"main","sessionID":"ses_f3c539be4ffe75jztLqF6tMSH7","label":"تطوير Max أطلس في المشروع","agent":"code","state":"busy"},{"id":"ses_f3c4351f7ffegIB7hG5Gz08O2p","sessionID":"ses_f3c4351f7ffegIB7hG5Gz08O2p","label":"Review Atlas recovery safety (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c460af9ffegFtpDQxOyzwjXg","sessionID":"ses_f3c460af9ffegFtpDQxOyzwjXg","label":"Build focused Atlas verification (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c52514affezdTXZoKxZL1HmE","sessionID":"ses_f3c52514affezdTXZoKxZL1HmE","label":"Map Max Atlas gaps (@explore subagent)","agent":"explore","state":"completed"}],"messages":[{"id":"board_5b0d5e04-5975-40bf-9402-c9e75936b6d8","timestamp":1789990964949,"from":"ses_f3c460af9ffegFtpDQxOyzwjXg","to":"main","type":"INFO","body":"Wiring guard updated to require AtlasRouteMemory.generationForBootId and explicitly reject BOOT_COUNT, retaining shared store/no-root checks. Prior 39-test pass was on BOOT_COUNT snapshot and is superseded pending rerun on your new identity source. Will inspect only final factory API for guard alignment, not duplicate research.","reply_to":"board_ff1d51f3-f95d-499f-a270-9301b756d5e6","fromLabel":"Build focused Atlas verification (@general subagent)","toLabel":"تطوير Max أطلس في المشروع"},{"id":"board_1c796171-22ea-4e1c-a2eb-bcfd3268bb47","timestamp":1789991016378,"from":"main","to":"ses_f3c460af9ffegFtpDQxOyzwjXg","type":"INFO","body":"Factory correction implemented now: AtlasRouteMemory.generationForBootId(PerAppRecoveryStore.bootId()), existing direct fixed-path reader no root prompt. No new source helper. Please adjust wiring test exact expected source accordingly and include PerAppRecoveryStore.kt in PSI parsing if desired. Memory tests now include UUID malformed/nil, deterministic normalized token, framework process restart same kernel UUID. No more main product edits planned pending safety.","fromLabel":"تطوير Max أطلس في المشروع","toLabel":"Build focused Atlas verification (@general subagent)"}],"hasMore":false,"cursor":"board_1c796171-22ea-4e1c-a2eb-bcfd3268bb47"}
```

---

## Assistant (Code · GPT-6 Astra · 75.4s)

_Thinking:_

**Evaluating testing options**

I need to finalize my tasks, but there are long runs still waiting. I can test my revised memory against the old code to demonstrate how new tests fail when the old counter is reset. The agent runner doesn’t fit with logic mutation. I should design a temporary mutation harness with `--source-dir`, though manual modifications to the main source can cause issues. I’m thinking about using a CLI variant with git to manage these changes, but it will require a temp script.

_Thinking:_

**Assessing test reproduction**

I’m trying to reproduce a baseline test using previous Kotlin snippets, focusing on the `AtlasRouteMemory`. The old version lacks helper methods, and the new tests have unresolved dependencies, so I need to keep the helpers. The aim is to avoid manual changes and use `apply_patch` to add the existing source code from Git. Since the runner environment isn’t configurable, I'll need to look closely at the boilerplate to check for any type issues. It's also essential to verify compatibility with Android API levels.

_Thinking:_

**Evaluating task progress and calculations**

I’m checking on SHA-256 compliance with FIPS on Android. The default value for the clock lambda doesn’t require root access, and I’m using `context.noBackupFilesDir` with a minimum API of 21. For the PerApp helper, the `File.readText()` method defaults to UTF-8 for parsing, and I'm keeping tabs on ongoing tasks. I calculated a total output count for tests: 25 in total when combining original and additional tests, plus considering the executor and planner scores. It's a lot to keep track of!

_Thinking:_



**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/kt_balance.py --assert",
  "description": "Validate final Kotlin and XML structural integrity",
  "timeout": 120000
}
```

**Output:**
```
توازن البنية: 759 ملفًا · عوائق 0

```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/repo_audit.py",
  "description": "Audit final resource references and repository integrity",
  "timeout": 120000
}
```

**Output:**
```
PROBLEMS: 0
kt files scanned: 400 | R.string refs: 2679 | base strings: 3349

```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/i18n_coverage.py --assert",
  "description": "Validate final localization and locale configuration",
  "timeout": 120000
}
```

**Output:**
```
مجلدات values-*: 84 + en  ·  أكواد المنتقي: 85  ·  locales_config: 85
تطابق الأكواد الثلاثة: OK
عوائق (specifiers/تكرار/تطابق الأكواد): 0

```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/code_health.py --assert",
  "description": "Verify final health and unchanged debt ceilings",
  "timeout": 120000
}
```

**Output:**
```
════════════════════════════════════════════════════════════════════════
الصحّة — المطلوب صفر
════════════════════════════════════════════════════════════════════════
  ✓ package_mismatch: 0
  ✓ unresolved_resource: 0
  ✓ duplicate_string_key: 0
  ✓ stray_root_file: 0

════════════════════════════════════════════════════════════════════════
الدَّين — مُجمَّد عند سقف، لا يُسمح له بالنمو
════════════════════════════════════════════════════════════════════════
     10  oversized_files
     29  own_wildcard_imports
     63  hardcoded_ui_literals
     23  presentation_hw_writes
      2  todo_markers (تُراجَع، لا تُفرض)
    236  platform_wildcard_imports (لا تُحتسب)

  ملفات Kotlin: 578  ·  الأسطر: 135397

  أكبر الملفات (تجاوزت 1000 سطر):
     2438  manager/app/src/main/java/nd/max/AppMonitor.kt
     1843  manager/app/src/main/java/nd/max/ui/mainscreens/MaxAiScreen.kt
     1787  manager/app/src/main/java/nd/max/core/maxai/MaxAiEngine.kt
     1561  manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt
     1282  manager/app/src/main/java/nd/max/ui/subscreens/CustomThemeScreen.kt
     1147  manager/app/src/main/java/nd/max/ui/component/HomeComponents.kt
     1147  manager/app/src/main/java/nd/max/ui/flasher/KernelFlasherScreen.kt
     1118  manager/app/src/main/java/nd/max/ui/subscreens/CpuCoreControlScreen.kt
     1092  manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt
     1086  manager/app/src/main/java/nd/max/ui/component/ExpressiveListComponent.kt

  استيرادات شاملة لكود المشروع نفسه (تُخفي مصدر الرمز):
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/MainActivity.kt
    nd.max.ui.navigation  <-  manager/app/src/main/java/nd/max/MainActivity.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/MainActivity.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/component/ModuleBackupComponent.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/ui/component/SettingsHeaderComponent.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/mainscreens/HomeDashboardComponents.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
    nd.max.ui.viewmodel  <-  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
    nd.max.ui.mainscreens  <-  manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt
    nd.max.ui.subscreens  <-  manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt
    nd.max.ui.subscreens.hubs  <-  manager/app/src/main/java/nd/max/ui/navigation/MaxNavGraph.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/AboutScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/AppSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/BypassCheckScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/ColorSchemeScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/CpuCoreControlScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/CustomThemeScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/DebloatFreezeScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/DisplayStudioScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/FasSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/FpsGoSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt
    nd.max.ui.mainscreens  <-  manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt
    nd.max.ui.util  <-  manager/app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/LogsViewerScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/PreferencedTweakScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/ProcessManagerScreen.kt
    nd.max.ui.component  <-  manager/app/src/main/java/nd/max/ui/subscreens/SetEditScreen.kt

  كتابة عتاد من طبقة العرض (ADR-11) — 23 موضعًا:
     10  manager/app/src/main/java/nd/max/ui/viewmodel/ZramViewModel.kt
      3  manager/app/src/main/java/nd/max/ui/viewmodel/TweakViewmodel.kt
      2  manager/app/src/main/java/nd/max/ui/subscreens/BypassChargeScreen.kt
      2  manager/app/src/main/java/nd/max/ui/subscreens/BypassCheckScreen.kt
      1  manager/app/src/main/java/nd/max/ui/mainscreens/GetStartedScreen.kt
      1  manager/app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt
      1  manager/app/src/main/java/nd/max/ui/subscreens/ZramManagerScreen.kt
      1  manager/app/src/main/java/nd/max/ui/viewmodel/AppSettingsViewmodel.kt
      1  manager/app/src/main/java/nd/max/ui/viewmodel/DisplayStudioViewModel.kt
      1  manager/app/src/main/java/nd/max/ui/viewmodel/NetworkSchedulerViewModel.kt

  نصوص واجهة صلبة (أول 15 من 63):
    manager/app/src/main/java/nd/max/service/FpsOverlayService.kt: 'FPS'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'Not exported'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'System'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'Activities'
    manager/app/src/main/java/nd/max/ui/activitylauncher/ActivitylauncherScreen.kt: 'Display Settings'
    manager/app/src/main/java/nd/max/ui/activitylauncher/FloatingActivityService.kt: 'Activity Logger'
    manager/app/src/main/java/nd/max/ui/activitylauncher/FloatingActivityService.kt: 'Preferences'
    manager/app/src/main/java/nd/max/ui/activitylauncher/FloatingActivityService.kt: 'Scanning Activities...'
    manager/app/src/main/java/nd/max/ui/component/StudioComponents.kt: 'MHz'
    manager/app/src/main/java/nd/max/ui/component/StudioComponents.kt: 'CPU'
    manager/app/src/main/java/nd/max/ui/component/ThermalPulseCard.kt: 'Temperature'
    manager/app/src/main/java/nd/max/ui/design/MaxHelp.kt: '\\u2022'
    manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt: 'Live hardware health'
    manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt: 'Active control ownership'
    manager/app/src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt: 'Hardware report'

الحصيلة: صحّة نظيفة

بوابة النظافة: exit 0

```

**Tool: bash**

**Input:**
```json
{
  "command": "git diff --numstat",
  "description": "Review final change size before handoff"
}
```

**Output:**
```
28	1	docs/ai/NEXT_TASK.md
4	17	manager/app/src/main/java/nd/max/AppMonitor.kt
2	15	manager/app/src/main/java/nd/max/core/di/DataModule.kt
74	29	manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
0	82	manager/app/src/main/java/nd/max/core/hardware/HardwareRepairExecutor.kt
43	4	manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveExecutorTest.kt
206	5	manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt

```

---

## User

---

## Assistant (Code · GPT-6 Astra · 20.2s)

_Thinking:_



**Tool: board_read**

**Input:**
```json
{
  "since": "board_1c796171-22ea-4e1c-a2eb-bcfd3268bb47",
  "limit": 10
}
```

**Output:**
```
{"observedAt":1789991153639,"agent":"main","participants":[{"id":"main","sessionID":"ses_f3c539be4ffe75jztLqF6tMSH7","label":"تطوير Max أطلس في المشروع","agent":"code","state":"busy"},{"id":"ses_f3c460af9ffegFtpDQxOyzwjXg","sessionID":"ses_f3c460af9ffegFtpDQxOyzwjXg","label":"Build focused Atlas verification (@general subagent)","agent":"general","state":"busy"},{"id":"ses_f3c4351f7ffegIB7hG5Gz08O2p","sessionID":"ses_f3c4351f7ffegIB7hG5Gz08O2p","label":"Review Atlas recovery safety (@general subagent)","agent":"general","state":"completed"},{"id":"ses_f3c52514affezdTXZoKxZL1HmE","sessionID":"ses_f3c52514affezdTXZoKxZL1HmE","label":"Map Max Atlas gaps (@explore subagent)","agent":"explore","state":"completed"}],"messages":[{"id":"board_c2035a6f-e5cc-4ab9-be8f-263d084f06f2","timestamp":1789991083861,"from":"ses_f3c4351f7ffegIB7hG5Gz08O2p","to":"main","type":"INFO","body":"Blocker resolved in corrected source. Re-reviewed AtlasRouteMemoryFactory.kt:11-14 -> PerAppRecoveryStore.bootId():187 (direct fixed /proc/sys/kernel/random/boot_id read, catches failure; no root prompt), generationForBootId():277-281 (full UUID/nil validation, Locale.ROOT normalization, deterministic positive digest, unknown0). Framework restart cannot change this generation for the same kernel UUID. Legacy anchor :98-111 returns quarantine before/after migration; failed write retains persisted0; existing-name capacity guard :209-214 prevents refresh eviction. Inspected new tests :169-180, :215-244; no tests/builds run by reviewer. No remaining introduced blocker found. Final APPROVE_WITH_CONCERNS: revised runner evidence still needed; app SELinux denial stays unknown/long-lived quarantine; new-entry wholesale pruning + cross-process IO remain pre-existing risks, not fixed. This is not named-model/different-family approval.","reply_to":"board_e4826a92-ab55-4784-86ca-f8de7ade01e3","fromLabel":"Review Atlas recovery safety (@general subagent)","toLabel":"تطوير Max أطلس في المشروع"}],"hasMore":false,"cursor":"board_c2035a6f-e5cc-4ab9-be8f-263d084f06f2"}
```

---

## Assistant (Code · GPT-6 Astra · 29.9s)

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt"
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import android.content.Context
4: import nd.max.core.atlas.AtlasFileStoreIo
5: import java.nio.file.Paths
6: 
7: /** The app and companion must agree on both the store and the device boot, not the process lifetime. */
8: object AtlasRouteMemoryFactory {
9:     fun create(context: Context, clockMs: () -> Long): AtlasRouteMemory {
10:         // Deferred until the first memory operation on the caller's worker. Never prompt for root.
11:         val bootGeneration by lazy {
12:             // Android's BOOT_COUNT also changes on framework-only restarts. Only the kernel's
13:             // boot_id identifies the reset that makes an old hardware quarantine safe to expire.
14:             AtlasRouteMemory.generationForBootId(PerAppRecoveryStore.bootId())
15:         }
16:         return AtlasRouteMemory(
17:             io = AtlasFileStoreIo(
18:                 Paths.get(
19:                     AtlasFileStoreIo.directoryFor(context.noBackupFilesDir).absolutePath,
20:                     AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME,
21:                 ).toAbsolutePath(),
22:             ),
23:             clockMs = clockMs,
24:             bootGeneration = { bootGeneration },
25:             // Permission changes do not establish that an unknown physical state was restored.
26:             // Both processes keep quarantine until a different, known boot has rebuilt vendor state.
27:             privilegeGeneration = { 0L },
28:         )
29:     }
30: }

(End of file - total 30 lines)
</content>
```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt",
  "limit": 335
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import nd.max.core.atlas.AtlasStoreIo
4: import org.json.JSONObject
5: import java.nio.ByteBuffer
6: import java.security.MessageDigest
7: import java.util.Locale
8: 
9: /** What the last attempt of one route did. Three facts, not two: see the class documentation. */
10: enum class AtlasRouteOutcome {
11:     /** The write was read back unchanged for the bounded confirmation window. */
12:     VERIFIED,
13: 
14:     /** The attempt did not take, but the baseline was restored. Retrying another route is safe. */
15:     FAILED,
16: 
17:     /**
18:      * The attempt did not take **and** the baseline could not be confirmed.
19:      *
20:      * This is deliberately not folded into [FAILED]: a route that failed cleanly teaches us nothing
21:      * about the device's safety, while a route that left an unknown state must not be tried again in
22:      * the same boot generation.
23:      */
24:     ROLLBACK_FAILED,
25: }
26: 
27: /** One route's remembered state, for one target, in one boot/privilege generation. */
28: data class AtlasRouteMemoryEntry(
29:     val target: String,
30:     val routeId: String,
31:     val lastOutcome: AtlasRouteOutcome,
32:     val lastVerifiedElapsedMs: Long?,
33:     val bootGeneration: Long,
34:     val privilegeGeneration: Long,
35:     val successes: Int,
36:     val failures: Int,
37:     val rollbackFailures: Int,
38: ) {
39:     /** An unknown boot cannot prove that a previous unsafe state has gone away. */
40:     fun isQuarantinedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =
41:         rollbackFailures > 0 &&
42:             (this.bootGeneration == 0L || bootGeneration == 0L ||
43:                 (this.bootGeneration == bootGeneration && this.privilegeGeneration == privilegeGeneration))
44: 
45:     fun isVerifiedIn(bootGeneration: Long, privilegeGeneration: Long): Boolean =
46:         lastOutcome == AtlasRouteOutcome.VERIFIED &&
47:             successes > 0 &&
48:             rollbackFailures == 0 &&
49:             bootGeneration > 0L &&
50:             this.bootGeneration == bootGeneration &&
51:             this.privilegeGeneration == privilegeGeneration
52: }
53: 
54: /**
55:  * Learns which route actually worked on **this** device, and remembers which routes must not be
56:  * retried.
57:  *
58:  * The point is that Atlas does not rediscover the same device every scan: once a route has been
59:  * verified on this phone, the next attempt can prefer it. Three rules keep that from becoming a
60:  * liability:
61:  *
62:  * 1. **Learning never overrides safety ordering.** The stored preference only breaks ties *inside* one
63:  *    transport tier (`PLATFORM_HINT` before `VENDOR_BRIDGE` before `ROOT_DAEMON` before
64:  *    `ARBITER_SYSFS`). A remembered sysfs route can never outrank a platform route.
65:  * 2. **A rollback failure is remembered as such, and expires with the boot.** Within one boot the
66:  *    route is quarantined, because the device was left in an unknown state. After a reboot the
67:  *    evidence is void — the vendor's own state was rebuilt — so it is retried rather than banned
68:  *    forever.
69:  * 3. **Nothing here is a claim about a device.** An entry records what *we* observed; it carries no
70:  *    value, no unit and no capability, so it cannot be promoted into a measurement.
71:  *
72:  * The store is bounded and self-validating: an entry this build cannot read is deleted and treated as
73:  * absent, never half-trusted.
74:  */
75: class AtlasRouteMemory(
76:     private val io: AtlasStoreIo,
77:     private val clockMs: () -> Long,
78:     private val bootGeneration: () -> Long = { 0L },
79:     private val privilegeGeneration: () -> Long = { 0L },
80:     private val maxEntries: Int = MAX_ENTRIES,
81: ) {
82: 
83:     init {
84:         require(maxEntries > 0) { "the route memory bound must be positive" }
85:     }
86: 
87:     /** The last route verified on this device for [target], or `null` when there is none. */
88:     fun preferredRoute(target: String): String? {
89:         val boot = bootGeneration()
90:         val privilege = privilegeGeneration()
91:         return entries()
92:             .filter { it.target == target && it.isVerifiedIn(boot, privilege) }
93:             .maxByOrNull { it.lastVerifiedElapsedMs ?: Long.MIN_VALUE }
94:             ?.routeId
95:     }
96: 
97:     /** Routes that must not be attempted again in this boot generation. */
98:     fun quarantinedRoutes(target: String): Set<String> {
99:         val boot = bootGeneration()
100:         val privilege = privilegeGeneration()
101:         return entries()
102:             .filter { it.target == target && it.isQuarantinedIn(boot, privilege) }
103:             .onEach { entry ->
104:                 if (entry.bootGeneration == 0L && boot > 0L) {
105:                     // Legacy/unknown failures remain blocked now. Anchor them to this known boot so
106:                     // a later real reboot can release them without a manual memory reset.
107:                     store(entry.copy(bootGeneration = boot, privilegeGeneration = privilege))
108:                 }
109:             }
110:             .map { it.routeId }
111:             .toSet()
112:     }
113: 
114:     fun noteVerified(target: String, routeId: String) {
115:         val entry = currentEntry(target, routeId)
116:         store(copy(entry, AtlasRouteOutcome.VERIFIED, successes = entry.successes + 1))
117:     }
118: 
119:     /**
120:      * Records a failed attempt. [rollbackVerified] is required rather than optional because "we could
121:      * not restore the baseline" is the one failure that changes what may be attempted next.
122:      */
123:     fun noteFailed(target: String, routeId: String, rollbackVerified: Boolean) {
124:         val entry = currentEntry(target, routeId)
125:         store(
126:             copy(
127:                 entry,
128:                 if (rollbackVerified) AtlasRouteOutcome.FAILED else AtlasRouteOutcome.ROLLBACK_FAILED,
129:                 failures = entry.failures + 1,
130:                 rollbackFailures = entry.rollbackFailures + if (rollbackVerified) 0 else 1,
131:             ),
132:         )
133:     }
134: 
135:     /** Drops one target's memory. Used when a device config change invalidates what was learned. */
136:     fun forget(target: String): Int = io.list().count { name ->
137:         name.startsWith(NAME_PREFIX) && name.endsWith(NAME_SUFFIX) && name.contains(slug(target)) && io.delete(name)
138:     }
139: 
140:     fun clear(): Int = io.list().count { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) && io.delete(it) }
141: 
142:     fun entries(): List<AtlasRouteMemoryEntry> = io.list()
143:         .filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
144:         .mapNotNull { name -> io.read(name)?.let(::decode) }
145:         .sortedWith(compareBy({ it.target }, { it.routeId }))
146: 
147:     // ---- helpers -----------------------------------------------------------------------------------
148: 
149:     private fun currentEntry(target: String, routeId: String): AtlasRouteMemoryEntry {
150:         val boot = bootGeneration()
151:         val privilege = privilegeGeneration()
152:         val previous = load(target, routeId)
153:         if (previous != null && (
154:                 (previous.bootGeneration == boot && previous.privilegeGeneration == privilege) ||
155:                     previous.isQuarantinedIn(boot, privilege)
156:             )) {
157:             return previous.copy(bootGeneration = boot, privilegeGeneration = privilege)
158:         }
159:         // Counters describe one generation. Copying an old rollback failure here would quarantine
160:         // the route again immediately after its first successful recovery in the new boot.
161:         return AtlasRouteMemoryEntry(
162:             target = target,
163:             routeId = routeId,
164:             lastOutcome = AtlasRouteOutcome.FAILED,
165:             lastVerifiedElapsedMs = null,
166:             bootGeneration = boot,
167:             privilegeGeneration = privilege,
168:             successes = 0,
169:             failures = 0,
170:             rollbackFailures = 0,
171:         )
172:     }
173: 
174:     private fun copy(
175:         entry: AtlasRouteMemoryEntry,
176:         outcome: AtlasRouteOutcome,
177:         successes: Int = entry.successes,
178:         failures: Int = entry.failures,
179:         rollbackFailures: Int = entry.rollbackFailures,
180:     ) = entry.copy(
181:         lastOutcome = outcome,
182:         lastVerifiedElapsedMs =
183:             if (outcome == AtlasRouteOutcome.VERIFIED) clockMs() else entry.lastVerifiedElapsedMs,
184:         // currentEntry captured one context for the whole update, including counter reset.
185:         successes = successes,
186:         failures = failures,
187:         rollbackFailures = rollbackFailures,
188:     )
189: 
190:     private fun load(target: String, routeId: String): AtlasRouteMemoryEntry? {
191:         val text = io.read(nameOf(target, routeId)) ?: return null
192:         val decoded = decode(text) ?: run {
193:             io.delete(nameOf(target, routeId))
194:             return null
195:         }
196:         return decoded.takeIf { it.target == target && it.routeId == routeId }
197:             ?: run {
198:                 io.delete(nameOf(target, routeId))
199:                 null
200:             }
201:     }
202: 
203:     private fun store(entry: AtlasRouteMemoryEntry) {
204:         pruneIfNeeded(entry.target, entry.routeId)
205:         io.write(nameOf(entry.target, entry.routeId), encode(entry))
206:     }
207: 
208:     /** Keeps the store bounded; the entry being written is always kept. */
209:     private fun pruneIfNeeded(target: String, routeId: String) {
210:         val names = io.list().filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
211:         val keep = nameOf(target, routeId)
212:         // Refreshing an existing entry consumes no capacity and must not evict other quarantines.
213:         if (keep in names || names.size < maxEntries) return
214:         names.filter { it != keep }.forEach { io.delete(it) }
215:     }
216: 
217:     // ---- explicit-shape serialization ---------------------------------------------------------------
218: 
219:     private fun encode(entry: AtlasRouteMemoryEntry): String {
220:         val root = JSONObject()
221:         root.put(KEY_SCHEMA, SCHEMA)
222:         root.put(KEY_TARGET, entry.target)
223:         root.put(KEY_ROUTE, entry.routeId)
224:         root.put(KEY_OUTCOME, entry.lastOutcome.name)
225:         root.put(KEY_VERIFIED_AT, entry.lastVerifiedElapsedMs ?: JSONObject.NULL)
226:         root.put(KEY_BOOT, entry.bootGeneration)
227:         root.put(KEY_PRIVILEGE, entry.privilegeGeneration)
228:         root.put(KEY_SUCCESSES, entry.successes)
229:         root.put(KEY_FAILURES, entry.failures)
230:         root.put(KEY_ROLLBACK_FAILURES, entry.rollbackFailures)
231:         return root.toString()
232:     }
233: 
234:     private fun decode(text: String): AtlasRouteMemoryEntry? {
235:         if (text.length > MAX_ENTRY_CHARS) return null
236:         val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
237:         if (root.optInt(KEY_SCHEMA, -1) != SCHEMA) return null
238:         if (!REQUIRED_KEYS.all { root.has(it) }) return null
239:         return runCatching {
240:             AtlasRouteMemoryEntry(
241:                 target = root.getString(KEY_TARGET),
242:                 routeId = root.getString(KEY_ROUTE),
243:                 lastOutcome = AtlasRouteOutcome.valueOf(root.getString(KEY_OUTCOME)),
244:                 lastVerifiedElapsedMs = if (root.isNull(KEY_VERIFIED_AT)) null else root.getLong(KEY_VERIFIED_AT),
245:                 bootGeneration = root.getLong(KEY_BOOT),
246:                 privilegeGeneration = root.getLong(KEY_PRIVILEGE),
247:                 successes = root.getInt(KEY_SUCCESSES).coerceAtLeast(0),
248:                 failures = root.getInt(KEY_FAILURES).coerceAtLeast(0),
249:                 rollbackFailures = root.getInt(KEY_ROLLBACK_FAILURES).coerceAtLeast(0),
250:             )
251:         }.getOrNull()
252:     }
253: 
254:     private fun nameOf(target: String, routeId: String): String =
255:         "$NAME_PREFIX${slug(target)}$NAME_SEPARATOR${slug(routeId)}$NAME_SUFFIX"
256: 
257:     /** Entry names are generated here, so an unusual target cannot name a file outside this store. */
258:     private fun slug(value: String): String {
259:         val cleaned = value.lowercase().map { character ->
260:             if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') {
261:                 character
262:             } else {
263:                 '-'
264:             }
265:         }.joinToString("").trim('-', '.').replace(Regex("-{2,}"), "-")
266:         return cleaned.take(MAX_SLUG_CHARS).ifEmpty { "unnamed" }
267:     }
268: 
269:     companion object {
270:         const val SCHEMA: Int = 1
271: 
272:         /**
273:          * A stable token for a validated kernel boot UUID; zero means unknown, including legacy records.
274:          * A digest collision can only retain quarantine too long, never release it inside one boot.
275:          * The raw boot identity is neither stored nor reported.
276:          */
277:         fun generationForBootId(bootId: String?): Long {
278:             val normalized = bootId?.trim()?.lowercase(Locale.ROOT) ?: return 0L
279:             if (!BOOT_UUID.matches(normalized) || normalized == "00000000-0000-0000-0000-000000000000") return 0L
280:             val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.US_ASCII))
281:             return (ByteBuffer.wrap(digest).long and Long.MAX_VALUE).coerceAtLeast(1L)
282:         }
283: 
284:         const val NAME_PREFIX: String = "route."
285:         const val NAME_SUFFIX: String = ".json"
286:         const val NAME_SEPARATOR: String = "."
287: 
288:         private const val MAX_ENTRIES = 64
289:         private const val MAX_ENTRY_CHARS = 2 * 1024
290:         private const val MAX_SLUG_CHARS = 48
291: 
292:         private val BOOT_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
293: 
294:         private const val KEY_SCHEMA = "schema"
295:         private const val KEY_TARGET = "target"
296:         private const val KEY_ROUTE = "route"
297:         private const val KEY_OUTCOME = "outcome"
298:         private const val KEY_VERIFIED_AT = "verifiedAt"
299:         private const val KEY_BOOT = "boot"
300:         private const val KEY_PRIVILEGE = "privilege"
301:         private const val KEY_SUCCESSES = "successes"
302:         private const val KEY_FAILURES = "failures"
303:         private const val KEY_ROLLBACK_FAILURES = "rollbackFailures"
304: 
305:         private val REQUIRED_KEYS: List<String> = listOf(
306:             KEY_SCHEMA,
307:             KEY_TARGET,
308:             KEY_ROUTE,
309:             KEY_OUTCOME,
310:             KEY_VERIFIED_AT,
311:             KEY_BOOT,
312:             KEY_PRIVILEGE,
313:             KEY_SUCCESSES,
314:             KEY_FAILURES,
315:             KEY_ROLLBACK_FAILURES,
316:         )
317:     }
318: }

(End of file - total 318 lines)
</content>
```

**Tool: bash**

**Input:**
```json
{
  "command": "git status --short",
  "description": "Confirm final worktree contains only intended changes"
}
```

**Output:**
```
 M docs/ai/NEXT_TASK.md
 M manager/app/src/main/java/nd/max/AppMonitor.kt
 M manager/app/src/main/java/nd/max/core/di/DataModule.kt
 M manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemory.kt
 M manager/app/src/main/java/nd/max/core/hardware/HardwareRepairExecutor.kt
 M manager/app/src/test/java/nd/max/core/hardware/AtlasAdaptiveExecutorTest.kt
 M manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryTest.kt
?? manager/app/src/main/java/nd/max/core/hardware/AtlasRouteMemoryFactory.kt
?? manager/app/src/main/java/nd/max/core/hardware/HardwareRepairModels.kt
?? manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryWiringTest.kt
?? tools/test_atlas_jvm.py

```

---

## Assistant (Code · GPT-6 Astra · 48.4s)

_Thinking:_



**Tool: apply_patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: /mnt/sdcard/MaxManger/optmize-main/docs/ai/HANDOFF.md\n@@\n <!-- Append one entry per completed task: task id, files, gate results, deferred items, next suggestion. -->\n+\n+### ATLAS-RECOVERY-02 — تعافٍ مرتبط بإقلاع النواة، لا بعمر العملية — 2026-09-21\n+\n+**TASK:** ATLAS-RECOVERY-02 (medium) — التنفيذ مكتمل؛ التحقق النهائي قيد التسجيل.\n+المالك حدّد الهدف الممتد: توافق تلقائي على أغلب الأجهزة، وتشخيص الفشل واكتشاف البدائل وإصلاح ما يمكن\n+إصلاحه دون تدخّل يدوي. هذه الجولة شرط تأسيسي لهذا الهدف، وليست ادعاءً بأن كل وظائف التطبيق باتت\n+مدعومة على كل الأجهزة.\n+\n+**FILES:** تعديل `core/hardware/AtlasRouteMemory.kt`، وموضعي الإنشاء في `core/di/DataModule.kt`\n+و`AppMonitor.kt`؛ إضافة `AtlasRouteMemoryFactory.kt`. نقل أنواع المعاملة من `HardwareRepairExecutor.kt`\n+إلى `HardwareRepairModels.kt` بنفس الحزمة والواجهة ودون تغيير جسم المنفّذ، لتمكين اختبار المنفّذ\n+التكيفي الفعلي دون بدائل وهمية لـAndroid. تعديل اختباري الذاكرة والمنفّذ التكيفي، وإضافة حارس الربط\n+`AtlasRouteMemoryWiringTest.kt` و`tools/test_atlas_jvm.py`. تحديث `NEXT_TASK.md` بهذا النطاق.\n+\n+**ما تغيّر:** مصدر إقلاع واحد مشترك بين التطبيق والرفيق عبر القارئ القائم\n+`PerAppRecoveryStore.bootId()`؛ UUID صحيح كامل يُطبّع ويُحوّل إلى رمز موجب ثابت، ولا تُحفظ الهوية الخام.\n+إعادة تشغيل العملية أو إطار النظام لا ترفع الحجر. المجهول يبقى صفرًا، ولا ينتج تفضيلًا متحققًا ولا\n+يرفع الحجر. السجل القديم ذو الجيل الصفري يُثبّت على الإقلاع المعلوم الحالي دون تحريره فيه، ثم يُسمح\n+بالتعافي بعد إقلاع نواة لاحق معلوم. فشل كتابة الترحيل يبقي الحجر. أُصلح أيضًا نسخ عدّاد فشل الاسترجاع\n+القديم إلى نجاح جديد، وتقليم بقية الذاكرة لمجرد تحديث مدخل موجود عند بلوغ السعة.\n+\n+**تصحيح مهم أثناء المراجعة:** الاختيار الأول كان `Settings.Global.BOOT_COUNT`، ورفضه المراجع بدليل\n+من AOSP: `PowerManagerService` يزيده في `PHASE_THIRD_PARTY_APPS_CAN_START`، فيمكن أن يتغير دون\n+إقلاع نواة جديد. أُزيل الاعتماد عليه قبل التسليم، وأُضيف اختبار إعادة إنشاء الذاكرة بنفس UUID.\n+المصدر: `aosp-mirror/platform_frameworks_base`، الوسم `android-16.0.0_r1`،\n+`services/core/java/com/android/server/power/PowerManagerService.java:1351-1357,5355-5365`.\n+أُعيدت المراجعة لهذا التغيير الجوهري، وكان الحكم `APPROVE_WITH_CONCERNS`، والمانع الأول محلول.\n+\n+**GATES:** آخر قياس للمنسّق: `kt_balance --assert` = **759 ملفًا / 0 عوائق** (قبل الجولة 756)،\n+و`--self-test` = **17 / 0**؛ `code_health --assert` = أربعة أصفار، والدَّين ثابت **10 / 29 / 63 / 23**،\n+وملفات Kotlin **578** (قبل الجولة 575). `i18n_coverage --assert` = **0 عوائق**، وتطابق الأكواد\n+**85 / 85 / 85**. `repo_audit.py` = **PROBLEMS: 0**، **400** ملف منتج، **2679** مرجع نص،\n+**3349** مفتاحًا. لا نص واجهة جديد، ولا كتابة عتاد جديدة، ولا تعديل لسياسة SELinux.\n+\n+**BUILD:** لا Gradle ولا APK في هذه الجولة. JDK 17 موجود في `/usr/lib/jvm/java-17-openjdk-arm64`،\n+لكن Android SDK غير موجود في المسار المعلن في `local.properties` ولا في `~/android-sdk`؛\n+**compilation unverified in this environment** يخص تطبيق Android، لا الاختبارات الخالصة التي يقيسها\n+مشغّل JVM. مشغّل الاختبار لا يُنزّل شيئًا؛ اعتماد JSON المعلن في التطبيق (`20260814`) جُلب من Maven\n+Central إلى `/tmp/kilo/json-20260814.jar` خارج المستودع، ولا تغيير لاعتمادات التطبيق.\n+\n+**RESIDUAL RISK:** توافق الأجهزة والقراءة تحت SELinux وإعادة الإقلاع الفعلية تحتاج جهازًا. عند منع\n+قراءة `boot_id` يبقى الجيل مجهولًا طوال عمر نسخة الذاكرة، دون طلب صلاحية تلقائي. المخزن القائم ما زال\n+يُقلّم بقية المداخل عند إدخال **مدخل جديد** فوق السعة، وعمليات التحديث بين العمليتين ليست معاملة\n+واحدة مقفلة؛ هذان خطران سابقان لم تُصلحهما هذه الجولة. المراجعة المستقلة المتاحة ليست إثباتًا لمراجعة\n+النموذج المسمّى/العائلة المختلفة المطلوبة في `AGENTS.md`، فلا يُدّعى إغلاق اعتماد السلامة الرسمي.\n+\n+**NEXT:** تحصين مخزن المسارات عند التشبع والتزامن، ثم توصيل أدلة GPU/CPU الفعلية إلى الاكتشاف\n+والتعافي هدفًا بهدف. الاستطلاع وجد `AtlasBackendProvider` و`AtlasPlatformProvider` والطبيب/المسجّل\n+موجودة دون مستهلك إنتاجي مباشر، بينما `MinimalPlanner` يمرّر مسارًا واحدًا؛ وجود منفّذ عام لا يعني\n+وجود بدائل حقيقية لكل وظيفة. لا تُرقّى واجهات بنك المجتمع `INFERRED` إلى صلاحية كتابة.\n \n ### ATLAS-ADAPTIVE-01 — أطلس ينفّذ ويتحقق ويتعلم بدل أن يكتفي بالقراءة — 2026-09-20\n*** End Patch"
}
```

**Output:**
```
Success. Updated the following files:
M docs/ai/HANDOFF.md
```

---

## Assistant (Code · GPT-6 Astra · 23.5s)

_Thinking:_

**Performing a final check**

I'm focusing on using `bash git diff --check` for a targeted look at the changes. It's not about being repetitive; it's really about making sure the final documentation is correct. Just one thorough pass is all I need to ensure everything's in order. It feels quite important to be thorough here, given how easy it is to overlook things! So, I'll make sure I get this right.

**Tool: bash**

**Input:**
```json
{
  "command": "git diff --check",
  "description": "Validate final handoff and patch whitespace"
}
```

**Output:**
```
(no output)
```

**Tool: bash**

**Input:**
```json
{
  "command": "python3 tools/kt_balance.py --self-test",
  "description": "Recheck structural validator positive and negative controls",
  "timeout": 120000
}
```

**Output:**
```
  ✓ توازن سليم: متوقّع 0 · حاصل 0
  ✓ قوس ناقص: متوقّع 1 · حاصل 1
        السطر 1: «{» لم يُغلق
  ✓ قوس زائد: متوقّع 1 · حاصل 1
        السطر 2: «}» بلا فاتح يقابله
  ✓ قوس داخل نص — لا يُحسب: متوقّع 0 · حاصل 0
  ✓ قوس داخل تعليق — لا يُحسب: متوقّع 0 · حاصل 0
  ✓ تعليق كتلة متداخل: متوقّع 0 · حاصل 0
  ✓ تعليق كتلة ناقص: متوقّع 1 · حاصل 1
        السطر 1: تعليق كتلة لم يُغلق
  ✓ نص خام فيه إشارات: متوقّع 0 · حاصل 0
  ✓ نص خام ناقص: متوقّع 1 · حاصل 1
        السطر 1: نص لم يُغلق
  ✓ قالب نصّي فيه نص داخلي: متوقّع 0 · حاصل 0
  ✓ نص عادي ناقص: متوقّع 1 · حاصل 1
        السطر 1: نص عادي عَبَر نهاية السطر بلا إغلاق
  ✓ محرف قوس — لا يُحسب: متوقّع 0 · حاصل 0
  ✓ اسم بعلامتين مائيّتين فيه فاصلة عليا: متوقّع 0 · حاصل 0
  ✓ اسم بعلامتين مائيّتين فيه قوس: متوقّع 0 · حاصل 0
  ✓ اسم بعلامتين مائيّتين لم يُغلق: متوقّع 1 · حاصل 1
        السطر 2: اسم بين علامتين مائيّتين لم يُغلق
  ✓ XML سليم: متوقّع 0 · حاصل 0
  ✓ XML مبتور: متوقّع 1 · حاصل 1
قياس الأداة: 17 حالة · إخفاقات 0

```

**Tool: read**

**Input:**
```json
{
  "filePath": "/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryWiringTest.kt",
  "limit": 100
}
```

**Output:**
```
<path>/mnt/sdcard/MaxManger/optmize-main/manager/app/src/test/java/nd/max/core/hardware/AtlasRouteMemoryWiringTest.kt</path>
<type>file</type>
<content>
1: package nd.max.core.hardware
2: 
3: import java.io.File
4: import org.junit.Assert.assertFalse
5: import org.junit.Assert.assertTrue
6: import org.junit.Test
7: 
8: /** Source guards only: no Android classes loaded, no claim about Context behavior on a device. */
9: class AtlasRouteMemoryWiringTest {
10:     private val sourceRoot = listOf(
11:         File("src/main/java/nd/max"),
12:         File("app/src/main/java/nd/max"),
13:         File("manager/app/src/main/java/nd/max"),
14:     ).firstOrNull { it.isDirectory } ?: error("Cannot locate nd.max sources; wiring guard cannot run")
15: 
16:     private fun source(path: String): String = File(sourceRoot, path).readText()
17:         .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
18:         .replace(Regex("//[^\\n]*"), "")
19: 
20:     @Test
21:     fun `app and companion both construct route memory through the shared factory`() {
22:         listOf("core/di/DataModule.kt", "AppMonitor.kt").forEach { path ->
23:             val text = source(path)
24:             assertTrue("$path must call the shared factory", Regex("AtlasRouteMemoryFactory\\s*\\.\\s*create\\s*\\(").containsMatchIn(text))
25:             assertFalse("$path must not construct independent route memory", Regex("\\bAtlasRouteMemory\\s*\\(").containsMatchIn(text))
26:         }
27:     }
28: 
29:     @Test
30:     fun `factory uses shared non backed up storage and kernel boot identity`() {
31:         val text = source("core/hardware/AtlasRouteMemoryFactory.kt")
32:         assertTrue(text.contains("AtlasFileStoreIo.directoryFor(context.noBackupFilesDir)"))
33:         assertTrue(text.contains("AtlasFileStoreIo.ROUTE_MEMORY_DIRECTORY_NAME"))
34:         assertTrue(text.contains("AtlasRouteMemory.generationForBootId(PerAppRecoveryStore.bootId())"))
35:         assertFalse("Framework restart must not look like a kernel reboot", text.contains("BOOT_COUNT"))
36:         assertTrue(Regex("bootGeneration\\s*=\\s*\\{\\s*bootGeneration\\s*}").containsMatchIn(text))
37:         assertTrue(Regex("privilegeGeneration\\s*=\\s*\\{\\s*0L\\s*}").containsMatchIn(text))
38:     }
39: 
40:     @Test
41:     fun `factory never opens a privilege transport to obtain boot identity`() {
42:         val text = source("core/hardware/AtlasRouteMemoryFactory.kt")
43:         listOf(
44:             "RootFileAccess", "RootIpcManager", "Shell", "ProcessBuilder",
45:             "Runtime.getRuntime", "requestRoot", "requestPermission", "ensureRoot",
46:             "PrivilegeManager", "com.topjohnwu", "rikka.shizuku",
47:         ).forEach { forbidden ->
48:             assertFalse("Factory must not request privilege through $forbidden", text.contains(forbidden))
49:         }
50:     }
51: }

(End of file - total 51 lines)
</content>
```

---

## Assistant (Code · GPT-6 Astra · 76.0s)

_Thinking:_



أُزيل مانع السلامة الذي كشفته المراجعة، واجتازت التعديلات بوابات البنية والموارد والنظافة دون زيادة الدَّين التقني. يجري الآن تشغيل الاختبارات على النسخة المصححة؛ والنتيجة المقصودة لهذه الجولة هي تعافٍ أكثر موثوقية، لا ادعاء أن جميع وظائف التطبيق أصبحت متوافقة تلقائيًا.


_Thinking:_

**Exploring code review process**

It’s important that the code review identifies independent expected numbers without duplication. The agent needs to run precise tests independently because that's required for the review. Integration serves a different purpose than just duplication. The input file is stable from the latest read, so it seems okay to proceed without applying the agent’s code right now. I need to ensure there's clear communication regarding testing at the repository root and confirming the final counts.

**Tool: bash**

**Input:**
```json
{
  "command": "JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 python3 tools/test_atlas_jvm.py --json-jar /tmp/kilo/json-20260814.jar --work-dir /tmp/kilo",
  "description": "Run final integrated Atlas JVM regression suites",
  "timeout": 120000
}
```

**Output:**
```
org.json:json:20260814 declared by app; using /tmp/kilo/json-20260814.jar
NOT EXECUTED: HardwareRepairExecutorTest; production dependency chain reaches android.system via SharedHardwareOwnershipStore. No platform stubs or source extraction used.
Kotlin PSI self-test: valid accepted, invalid rejected
Kotlin syntax: 9 files, no parser errors (not type checking)
Compiling 15 real Kotlin source/test files; running 4 JUnit suites
JUnit suites: nd.max.core.hardware.AtlasRouteMemoryTest, nd.max.core.hardware.AtlasAdaptiveExecutorTest, nd.max.core.atlas.AtlasRoutePlannerTest, nd.max.core.hardware.AtlasRouteMemoryWiringTest
JUnit version 4.13.2
.........................................
Time: 1.178

OK (41 tests)

Verified only the listed JVM suites and Kotlin syntax; Android compilation and device recovery remain unverified.

```

---

## Assistant (Code · GPT-6 Astra · 15.4s)

---

## User

---

## User

اكمل

---

## User

اكمل

---

## User

اكمل

---

## Assistant (Code · Claude Opus 4.8 · 90.2s)

---

## User

اكمل

---

## User

اكمل

---

## Assistant (Code · GPT-6 Astra · 8.2s)

---

