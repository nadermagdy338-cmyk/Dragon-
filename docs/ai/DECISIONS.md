# DECISIONS

Binding decisions from the architect pass (2026-09-15). Executors follow these unless a decision is explicitly revised here. Format: decision → why → consequence.

## ADR-01 — One navigation model; the pager mode is deleted
**Why:** `use_scroll_animation` produces two runtime navigation architectures for the same four screens, doubling every nav concern (D-01), and it is invisible to users.
**Consequence:** delete the `main` pager route, the flag read in `MainActivity`, and its settings toggle. Bottom bar + nav rail is the only model.

## ADR-02 — Routes become a typed registry outside `MainActivity`
**Why:** 40+ string-literal routes inline in an 817-line activity produced dead aliases and untestable navigation (D-03).
**Consequence:** `ui/navigation/MaxDestinations.kt` is the single source of truth (route id, title res, icon, parent, risk level, deep-link key). `navigate("literal")` outside that package becomes a static-test violation.

## ADR-03 — Primary destinations are Now / Control / Apps / Max AI; Settings leaves the bottom bar
**Why:** Settings held a primary slot while navigating to two destinations, and Max AI — the product differentiator — had no primary entry (D-02, D-09).
**Consequence:** Settings is reached from the Now top bar and aggregates appearance, module/update, diagnostics, logs, advanced tools, about.

## ADR-04 — Control is organized by device domain, not by screen name
**Why:** ~9 real domains were split across 19 launcher rows with overlapping scope (D-03, D-04).
**Consequence:** 8–9 domain hubs (CPU, GPU, Memory, Display, Responsiveness, Thermal, Power, Storage & compiler, Network). A dedicated screen survives only for deep workspaces (CPU core grid, GPU ladder, per-app editor, kernel flasher).

## ADR-05 — Merge by absorption, never by rewrite
**Why:** six screens were just rebuilt on the new language; re-doing them for the sake of the new IA would burn work and risk regressions (explicit user constraint).
**Consequence:** hubs host existing screen bodies as sections; migration of a body to `ui/design/` happens in its own later task, not in the IA task.

## ADR-06 — `ui/design/` is the only design system; `ui/component/` is legacy in runoff
**Why:** two parallel systems, the better one covering 13% of screens (D-05).
**Consequence:** new UI imports `nd.max.ui.design` only. No new `Scaffold(` in screen files. Legacy components are deleted in the task that orphans them. `ui/components/` is folded into `ui/component/` and the duplicate package name disappears.

## ADR-07 — Data trust is a product rule, not a screen detail
**Why:** `MaxDataTrust` + source labelling is the credible differentiator in a category full of fake telemetry (D-06).
**Consequence:** any surfaced number must carry freshness + source; unknown renders as `status_unknown`, never as a plausible value. Synthesizing or interpolating telemetry is a blocking defect.

## ADR-08 — Capability and state must be visually separate
**Why:** the TouchBoost rebuild proved the old pattern conflated "device supports it" with "it is on".
**Consequence:** every control surface shows a providers/capability section (with node path + trust) distinct from its state switches; every disabled control supplies `lockedReason`.

## ADR-09 — Ownership of every knob is user-visible
**Why:** the arbiter, manual locks, and cross-process ownership journal already exist but are invisible where they matter (D-02).
**Consequence:** `MaxOwnerChip` (You / Max AI / Per-app / Module / Kernel default) renders on control rows and hub summaries and can release or lock, backed by `HardwareControlArbiter` + `ManualControlLocks`. No new ownership concept is invented in the UI layer.

## ADR-10 — Max AI must be auditable: ledger over dashboard
**Why:** trust in autonomy comes from history, not from status cards.
**Consequence:** a change journal (actor, knob, before→after, verified/rolled back, measurement) is a first-class surface in Now and in Max AI. `MaxAiScreen`'s generic cards are replaced by: objective, ownership map, journal, safety, learning credibility.

## ADR-11 — UI never writes hardware directly
**Why:** bypassing the control plane breaks leases, baselines, rollback, and safety veto.
**Consequence:** no `RootFileAccess`/shell writes from composables or screen ViewModels; all writes go through the arbiter/control-plane APIs.

## ADR-12 — Efficiency is part of the brand: decoration is opt-in and off by default
**Why:** blur, ambient glow, motif overlays, weather effects, and video wallpaper cost frames and battery in an app that promises to save both (D-07).
**Consequence:** one Appearance → Ambient effects switch, default off, with an honest cost note; effect engines are not composed when off; unreferenced engines are deleted.

## ADR-13 — Semantic tones stay hard-coded, not dynamic
**Why:** Positive/Caution/Critical must survive any wallpaper-derived palette; a "critical" that renders pastel green is a safety bug.
**Consequence:** keep `MaxTone`'s fixed hues and the light/dark surface derivation in `MaxTokens.kt`; dynamic colour only drives Neutral/Accent.

## ADR-14 — Localization parity is part of "done"
**Why:** new screens added 188 English-only keys in an RTL-first, ~100-locale app (D-10).
**Consequence:** every task that adds user-visible copy updates `values/` **and** `values-ar/` in the same change; no string literals in Compose.

## ADR-15 — Static verification is the gate; compilation is best-effort
**Why:** no usable Gradle/JDK toolchain combination here (D-11).
**Consequence:** each task ships with grep/python contract checks (`VALIDATION.md`) and, where cheap, a JVM source-scan test in the existing architecture-test style. Reports must say "compilation unverified in this environment" rather than implying a build passed.

## ADR-16 — High-risk tools are gated, not featured
**Why:** Terminal, SetEdit, ActivityLauncher, KernelFlasher can brick or wedge a device and currently sit beside ordinary tweaks.
**Consequence:** they live under Settings → Advanced tools with an explicit risk gate and `MaxRiskDialog` on destructive actions; risk level is declared in the destination registry (ADR-02).

## ADR-17 — Root/module status becomes one app-level session state
**Why:** root is re-probed on every navigation event and each screen invents its own unsupported story (D-08).
**Consequence:** a single observable session state (root, module, SELinux, capability snapshot) provided once; screens read it and render `MaxCondition` from it. Not in NT-01's scope, but no new per-screen probes may be added.

## ADR-18 — Nothing already-built is redone for aesthetic reasons
**Why:** explicit user constraint and respect for sunk, good work.
**Consequence:** the six migrated screens and the control-plane work are treated as foundations; they change only when a functional rule above (ownership chip, journal, localization) requires an additive change.

## ADR-19 — دفتر الحلقات هو الذاكرة السردية، لا العدّادات
العدّادات الأربعة و"آخر إجراء" كانت تُهدر سلسلة السبب/النتيجة كل ٣٠ ثانية. صار كل قرار حلقة دائمة مُسلسلة حقلًا بحقل.

## ADR-20 — إظهار المرشحين المرفوضين
القرار غير مفهوم بدون البدائل. `planWithTrace` يُخرج كل مرشح مع سبب الاستبعاد (`measured_harm`/`predicted_harm`/`no_step`/`unreadable`).

## ADR-21 — `NO_ACTION` حدث يُسجَّل
المراقبة الواعية ليست خمولًا؛ تُسجل حلقة بلا تنفيذ (بخنق ٥ دقائق) كي لا يبدو النظام ميتًا حين يقرر ألا يتدخل.

## ADR-22 — التنبؤ يُقارن بالقياس دائمًا
كل حلقة تحفظ `predictedGain` و`predictionErrorGain = |تنبؤ − مقيس|`، فتُعرض دقة النموذج بدل ادعائها.

## ADR-23 — ما لا يُقاس يُعرض كغير متوفر
لا صفر افتراضي ولا رسم وهمي: `after == null` ⇒ "تعذر القياس"، والخط الزمني فارغ قبل أول حلقة حقيقية.

## ADR-24 — شريط التطور في الذاكرة فقط
عيّنات الجلسة الحالية (120) لا تُحفظ على القرص كي لا يُعرض رسم "حي" من جلسة سابقة.

## ADR-26 — كل ملف نصوص يُسجَّل في Crowdin صراحةً
**القرار:** `crowdin.yml` يسرد في `files:` كل ملف في `values/` يحمل نصًّا مرئيًا للمستخدم، وملف نصوص جديد يُسجَّل في نفس التغيير. التجاهل المقصود يُكتب لا يُترك ضمنيًا.
**لماذا:** كانت ٤٧١ نصًّا في ٥ ملفات (`max_ai` ٢٢٠، `max_screen` ١٧٧، `max_navigation` ٥٤، `max_design` ١١، `studio` ٩) خارج خط الترجمة لأن `crowdin.yml` كان مربوطًا بـ`strings.xml` وحده — فبقيت إنجليزية في تطبيق من ~١٠٠ لغة.
**النتيجة:** أي ملف نصوص غير مسجَّل = عيب. المرجع للتحقق: `.planning/codebase/INTEGRATIONS.md` → «Localization pipeline». وملفات `values-ar` المصاحبة تبقى مصدر الحقيقة المتزامن (ADR-14).

## ADR-27 — ثلاثة أشياء تصف اللغات الـ٨٥، وأداة واحدة تمنع تباعدها
**القرار:** عدد اللغات ومصدرها واحد — مجلدات `res/values-*` الفعلية. منها تُشتقّ `AppLanguage.CODES` (منتقي داخل التطبيق) و`res/xml/locales_config.xml` (ما يعرضه النظام)، و`tools/i18n_coverage.py --check-codes` يقارن الثلاثة ويفشل عند أي اختلاف.
**لماذا:** `setApplicationLocales` يُمرّر الوسم إلى نظام أندرويد، ومنذ API 33 يرفض النظام الطلب ما لم يُعرَّف `android:localeConfig` في البيان — فبدون الملف يعمل التبديل على الإصدارات الأقدم ويفشل صامتًا على الحديثة. ووصف اللغات في مكانين بلا أداة يعني أن يتباعدا بلا أن يلاحظ أحد.
**النتيجة:** لا تُضاف لغة في الكود قبل أن يوجد مجلدها، ولا يُحرّر `locales_config.xml` يدويًا. أكواد المجلدات القديمة (`values-in`, `values-iw`, `values-tl`) تُكتب في الكود بصيغتها الحديثة (`id`, `he`, `fil`) لأن النظام يعرض الحديثة و`aapt2` يوفّق بينهما. التفاصيل والبوابة: `VALIDATION.md` §3.1.

**مسلكان للتطبيق لا مسلك واحد:** `AppCompatDelegate.setApplicationLocales` مسالك النظام على API 33+، لكنه يمرّ عبر `AppCompatActivity` ونشاط التطبيق `ComponentActivity` بسمة منصّية — فهو لا يُطبّق اللغة على أندرويد ١٠–١٢ (`minSdk = 29`). لذلك `MainActivity.attachBaseContext` يلفّ السياق بـ`AppLanguage.wrap` (لغة + اتجاه تخطيط). حذف أحدهما يُسقط نسخة من نسخات أندرويد المدعومة.

## ADR-28 — الترجمة تُجمع وتُتحقق آليًا، ولا تُخترع
**القرار:** لا تُكتب ترجمة آلية داخل المستودع من نموذج لغوي. `tools/i18n_coverage.py` يجمع المفاتيح الناقصة لكل لغة في CSV، ويكتب العائد منها بأسلوب **الإضافة فقط** بعد التحقق من الوسائط، والبشر أو Crowdin هم من يترجم.
**لماذا:** ٨٤ لغة ناقصة ‎١٤٤٠٤٣‎ مفتاحًا. الاختراع الصامت لترجمة بهذا الحجم يعطي نصًّا غير مُراجَع يبدو مكتملًا وينقل خطأً حقيقيًا في تطبيق يتحكم بمسارات الجذر — والصف الذي يطلب وسيطًا لا يمرّره الكود يُسقط التطبيق لا يُظهر نصًّا رديئًا.
**النتيجة:** النقص مُقاس ومعلَن (اللغة غير المترجمة تُظهر الإنجليزية تلقائيًا، وهذا سلوك صحيح لا عيب)، وكل صف مرفوض يُطبع مع سببه.

**تعديل 2026-09-18 — قرار المالك: الـ٨٥ لغة تُكتمل بالترجمة الآلية.** منعُ الاختراع الصامت لا يعني منع الترجمة: يُسمح بالترجمة الآلية عبر **مزوّد مُعلَن** بمفتاح يضعه المالك (`tools/i18n_translate.py`)، وبثلاث ضمانات لا يُتازل عنها: ① الوسائط والمصطلحات تُحجب قبل الإرسال بصور حارسة وتُستعاد بعده (فلا مزوّد يستطيع قلب `%1$s` أو حذفه)، ② المخرج **لا يُكتب في الموارد**: يمرّ على `--apply-csv` بأسلوب الإضافة فقط، ③ مخرج مزوّد `stub` لا يُدمج أبدًا. ما يبقى مسؤولية المالك: اختيار المزوّد وتكلفته (٤٣٥٩٤٦٣ حرفًا مصدرًا: ~٧٧–٩٦ $ مرة واحدة، أو ٩ أشهر على الحصة المجانية، والذاكرة المؤقتة تمنع إعادة الفاتورة)، ومراجعة لغوية لاحقة إن أراد جودة أعلى من الآلة.

## ADR-29 — قائمة اللغات تُكتب يدويًا، وتوليد AGP الآلي مُعطَّل
**القرار:** `androidResources { generateLocaleConfig = false }` مع إبقاء `android:localeConfig="@xml/locales_config"` وقائمة اللغات الصريحة في `res/xml/locales_config.xml`.
**لماذا:** المشروع كان يحمل `generateLocaleConfig = true` (2026-09-17) بينما جولة اللغة (2026-09-18) أضافت قائمة صريحة وربطها بالبيان — وهذا مزيج يرفضه AGP: «Locale config generation was requested but user locale config is present in manifest» فيسقط `:app:processDebugMainManifest` ويفشل البناء كله (لم يُكشف إلا ببناء حقيقي في جولة ١٠).
**النتيجة:** اختيار واحد من الاثنين لا كليهما. اختيرت القائمة الصريحة لأنها المصدر الوحيد الذي يضمن ظهور **اللغات الـ٨٥ كاملة** في منتقي النظام — ومنها ما لا يستنتجه التوليد الآلي من أسماء المجلدات (`values-b+sr+Latn`, `values-zh-rHK`, `values-pt-rPT`) — ولأنها الوجهة التي يتحقق منها `tools/i18n_coverage.py --check-codes` (ADR-27) مقابل المجلدات والمنتقي. التوليد الآلي يُسقط هذا الضمان بلا أن يشتكي أحد.
**إثبات:** `:app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL، والبيان المدمج في الـAPK يحمل `android:localeConfig`، والحزمة تضم `res/xml/locales_config.xml` (`VALIDATION.md` §0).

## ADR-25 — بدائل سينمائية بلا تبعية للنواة
`MaxAiCinematics.kt` لا يستورد `nd.max.core.*` ولا التنقل، فتبقى لغة التصميم قابلة لإعادة الاستخدام والاختبار.

## ADR-30 — الوتيرة تُشتق من إشارات مجانية، بحد أدنى معلن (2026-09-18)

**القرار:** إعادة التخطيط المبكر (`MaxAiCadence`) تُقرَّر من ثلاث حقائق فقط، ولا شيء غيرها: حالة الشاشة، ونوع الاستخدام المقيس في `DeviceStateCollector` (لعبة/عادي/مطفأ)، ومستوى حاكم الأمان. الإشارة تُبنى من اللقطة التي تُجمَع كل ثانية أصلًا للحلقة السريعة، وبدون أي قراءة جذرية جديدة (اسم الحزمة يُقرأ في دورة القرار عند تنفيذها فقط). ولا يُسمح بدورتين متتاليتين بسبب تغيّر حقيقي قبل `MIN_ADAPTIVE_INTERVAL_MS = 10_000`، وهو مساوٍ لنافذة قياس الأثر في المحرك.

**لماذا:** كان المحرك يفكر كل ٣٠ ثانية، والدورة الفورية لا تقع إلا بطلب المستخدم — فمن فتح لعبة كان التاريخي أن ينتظر حتى ٣٠ ثانية كي يلاحظ Max AI تغيّر السياق. لكن إزالة الانتظار بإطلاق حلقة استطلاع سريعة كانت ستقلب المبدأ المعاكس (تحكم لا يتأرجح، ADR-23/CYCLE_MS): عشر ثوانٍ من عمر البطارية لأجل طمأنينة بصرية ليست ذكاءً. الحل بالتفريق: **الهدوء يبقى، والاستثناء مؤهَّل ومحدود**. والحرارة والحمل مستبعدان عمدًا من الإشارة لأن لكل منهما مسارًا أسرع (حاكم الأمان كل ثانية)، وإدخالهما كان يجعل المحرك يستيقظ كل ثانية بلا سبب جديد.

**النتيجة:** لا سلطة جديدة ولا كتابة عتاد جديدة — الدورة المستيقَظة هي `cycle()` نفسها بكل سقوفها. والحد الأدنى لا يُنسي التغيّر: الإشارة السابقة لا تُحدَّث حين تأجيل الاستيقاظ، فيبقى الفرق قائمًا ويُعرض في الواجهة كعدّاد تنازلي، ثم يقع الاستيقاظ في أول ثانية تنتهي فيها المهلة. وكل استيقاظ يُوثَّق في `DiagnosticCenter` بسببه.

## ADR-32 — السقف الحراري منحنى لا عتبتين، وكل فرق عن القديم زيادةُ حماية (2026-09-18)

**القرار:** يُستبدل السقفان الثابتان (`ENGAGE_CAP_FRACTION = 0.55` داخل نطاق التدخل، `CRITICAL_CAP_FRACTION = 0.35` عند الحرجة) بمنحنى `MaxAiThermalCurve`: سقف متناسب مع مقدار التجاوز، وبند تكاملي مُقيَّد يشدّد ولا يرخي، وشرط تبريد متتالٍ قبل الاسترجاع، وتقريب إلى خطوات ٥٪ مع حد كتابة واحد كل ١٥ ثانية. **وتاكيداً على عدم المساس بقواعد السلامة**: منحنى المقدار لا يقرر **مستوى** الحماية — آلة الحالة في `SafetyEngine` (هستيريسيس ٥ درجات + تنبؤ أمامي + إعادة محاولة بتراجع أُسّي) تبقى كما هي، والمنحنى يترجم مستواها إلى رقم.

**لماذا:** أربع درجات من التجاوز كانت تُعالج بنفس القسوة، والدرجة الخامسة بشيء. والتحكم المُكمَّم (سلّم ترددات العتاد) لا يصلحه البند التناسبي وحده — وهو ما تعالجه نواة لينكس في حاكم `power_allocator` بثابت مخصوص (`integral_cutoff`) ومُعامِل مختلف للتجاوز عن ما دونه (`k_po` مقابل `k_pu`)، وما تعالجه المتحكمات العملية بلا تماثل مقصود (تصعيد فوري، ترخية بعد قياسات مستقرة). وهذا كله مذكور بدليله في `docs/ai/EXTERNAL-RESEARCH.md` §١ — لا واحد منه منقول، وإنما أعيد تصميمه على قيودنا.

**الدعوى القابلة للتكذيب (شرط القبول):** `MaxAiThermalCurve` **لا يعطي سقفًا أخفّ من السقف القديم في أي نقطة**، والبند التكاملي لا يُضاف أبدًا. `MaxAiThermalCurveTest` يفحص ذلك شاملةً على النطاق ٤٨°–٦٤° بخطوة ربع درجة، وعند الحدّين بالضبط، وبالتراكم الطويل، وبالإعداد الفاسد. أي أن كل فرق مالحظ عن السلوك السابق هو كتابة في اتجاه الأمان، وهذا ما يجعل التغيير قابلًا للتسليم قبل تجربته على جهاز.

**الحدود المُعلنة (لا تُدّعى):** كسب البند التكاملي (`0.0015`) ومدى الأرضية (`0.25`) قيم تصميمية غير معايَرة على جهاز — وهي مذكورة كخطر متبقٍّ في `HANDOFF.md`. وأي تغيير يمسّ هذا المنحنى لاحقًا يُثبت بنفس الاختبار أو يُرفض.

## ADR-31 — نطاق التعلّم لكل سياق، ولا يُخلط بعمود واحد (2026-09-18)

**القرار:** حكم المقبض يُحسب ويُعرض **داخل سياقه**: `Snapshot.knobs` تبقى أحكام السياق العام `*` (دلالة لم تتغير لأي شاشة قائمة)، و`knobsByContext` يحمل حكم كل سياق بمفاتيحه، و`contexts` مرتّبة: العام أولًا ثم الأغزر عيناتٍ ثم الأبجدي كسرًا للتعادل. والواجهة تُختار نطاقًا واحدًا وتقرأ أرقامه وحده — بلا دمج ولا معدّل مرجّح بين التطبيق والجهاز.

**لماذا:** نواة التعلّم كانت تقيس الأثر بمفتاح `(مقبض، اتجاه، سياق)` وتُغذّي السياقين معًا (الخاص والعام) من كل قياس، ثم يقرأ الاستخلاص **السياق العام وحده** ويعرضه للمستخدم. أي أن أدقّ ما يعرفه المحرك — «هذا المقبض ينفع في هذه اللعبة» — كان يُقاس ثم يُخفى، ويُستبدل بمتوسط عام يمكن أن يصفر بحق. وعرض المعدّل واحدًا كان سيكذب في الاتجاهين: مقبض نافع في تطبيق واحد يبدو فاشلًا بعد خلطه بسياق خامل، ومقبض عادل يبدو نافعًا بعد خلطه بسياق استثنائي.

**النتيجة:** `contextLabel` يحوّل اسم الحزمة إلى اسم التطبيق حين يسمح النظام، ويعود إلى اسم الحزمة حين لا يسمح — لا يُخترع اسم غير معروف (ADR-07/ADR-23). ومعه **تدقيق المحرك على نفسه**: صدق التنبؤ (`PREDICTION_TOLERANCE_GAIN = 5 × HELPFUL_GAIN`، مشتقّة لا مُختارة، و`hitRate` هي الحكم) وحاصل التجارب ونسب التجاوز والقياس، وكلها بمقام صفري يُعرض كغير متوفر لا كصفر (ADR-23). القاعدة الجامعة: **الرقم الذي يقيس المحرك نفسه يُعرض كما يقيسه الآخرون، لا كما يريد أن يُقاس.**

## ADR-33 — يُقاس ما هو زمن لا ما هو حالة، والغياب يُكتب غيابًا لا صفرًا (2026-09-18)

**القرار:** يُضاف مقياس زمني واحد هو **ميزانية الحرارة** (`MaxAiThermalBudget`): الزمن من بدء حمل ثقيل مقيس إلى أول خنق، يُشتق من نفس قياسات الحلقة السريعة بلا عتاد جديد، والمحرك يستدعي `observe(...)` مرة كل ثانية و`statusOf(...)` للعرض. وثلاث قواعد قبول ملزمة: ① الجلسة التي تنتهي **بلا خنق** تُعرض «جلسة نظيفة» لا رقمًا مُقدّرًا؛ ② التدخل الثاني في الجلسة نفسها لا يُعيد قفل الميزانية (الرقم عمر الجلسة حتى **أول** خنق لا آخر خنق)؛ ③ إنهاء الجلسة بإطفاء الشاشة مع قيام الحمل **لا يُحتسب صمودًا** — لا نعرف هل صمد العتاد أم توقف الطلب. ومعها **ذيل الأثر** في `MaxAiInsights.DeltaSpread` (`worst/p10/median/best`) للقرارات المقيسة وحدها، بترتيب الأقرب لا بالاستقراء، وباستبعاد التجارب المعرفية وحلقات السلامة من التوزيع.

**لماذا:** كل ما يعرضه Max AI كان يصف **حالة** (حرارة، حمل، عدد تدخلات)، وسؤال اللاعب الحقيقي زمني: «هل أعتمد على هذا الجهاز ساعة كاملة أم يهبط بعد عشر دقائق؟». وأدبيات التخفيف الحراري في AOSP تقترح هذا القياس صراحةً (تسجيل `t0` عند `THERMAL_STATUS_NONE`، وقياس المنقضي إلى أول خفض)، وهو XR-05 في `docs/ai/EXTERNAL-RESEARCH.md`. وأما الذيل فلأن المتوسط يخفي الذيل: مكبسب متوسطه `+0.02` قد يأتي من عشرة نجاحات وخسارة كبيرة واحدة، والمتوسط وحده يقول «نجحنا».

**النتيجة (درس عيب حقيقي):** كشفت الاختبارات أن `sessionStartedAtMs = 0L` كان يُستعمل كعلامة «لا جلسة»، والصفر لحظة زمنية صحيحة (بدء التشغيل) — فقراءة ساعة عند الصفر كانت تلغي الجلسة صامتةً بلا خطأ ظاهر. أُبدلت العلامة بحقول `Long?`. **القاعدة المستخلصة:** استعمال قيمة من صميم مدى المُدخل كعلامة حالة ممنوع؛ الغياب يُكتب `null` (أو حقل منطقي صريح) حتى لو كان المُدخل في الإنتاج لا يساوي الصفر عمليًا — لأن العقد لا يُبنى على ما تصادف أن المُستدعي يمرّره.

**الخوارزميات المُعاد تصميمها (لا منقولة):** ترتيب الأقرب لـ`p10` (لا استقراء خطي بين عينتين — لا يُعرض رقم لم يُقس)، ووسيط حقيقي لحجم زوجي، وتوزيع مبني من `kind == DECISION` لا من كل حلقة. والمصادر في §١ و§٣ من `EXTERNAL-RESEARCH.md`.

**الحدود المُعلنة:** `MIN_SESSION_MS = 30s` و`DEMANDING_INTENT = 0.75` قيم تصميمية **غير معايَرة على جهاز**؛ و«حمل ثقيل» مشتق من `appIntent` المُقدّر لا من إطارات حقيقية — فجلسة مُصنَّفة خطأً تُقاس خطأً. مُسجَّلة في I-60/I-61.

## ADR-34 — ضغط الذاكرة يُقاس بPSI لا بنسبة الامتلاء، والخنق مانع لا مشجّع (2026-09-18)

**القرار:** تُضاف إشارة `memoryStall` من `/proc/pressure/memory` (`full avg10`)، مقروءة بـ`MemoryPressureReader` ومحلَّلة بـ`MemoryStall` النقي. وتُستخدم في اتجاه واحد فقط: **تمنع رفع الأداء** حين يتجاوز الخنق [STALL_FRACTION]. ومعه قاعدتان: ① غير المدعوم (`UNSUPPORTED`) **لا يمنع شيئًا** — الجهل ليس خنقًا؛ ② الاستقصاء مُخزَّن سلبيًا: نواة أعلنت عدم الدعم لا تُسأل إلا كل [RETRY_AFTER_MS]، لأن `RootFileAccess.read` يجرّب قشرة عند فشل القراءة المباشرة — فاستقصاء كل ثانية كان يعني عملية قشرة كل ثانية مقابل قياس لا يُنتج رقمًا.

**لماذا:** `DeviceStateCollector.memoryUsage` يقيس **الامتلاء** لا **الأثر**؛ جهاز بـ٩٠٪ ذاكرة مستخدمة قد يكون سريعًا تمامًا وآخر بـ٦٠٪ في خنق شديد. والمقياس الصحيح موجود في النواة أصلًا: `some` (توقف بعض العمل) و`full` (توقف كل العمل غير الخامل = thrashing)، وعليه بُني `lmkd`. واستعماله في اتجاه واحد مقصود: رفع الترددات على جهاز يتوقف منتظرًا الذاكرة لا يعالج السبب بل يزيد الطاقة على نفس العمل المتوقف.

**الدعوى القابلة للتكذيب:** المنع **لا يستطيع إلا أن يقلب رفعًا إلى توفير**، ولا يقلب شيئًا في الاتجاه المعاكس، ولا يعمل بلا قياس. `ObjectiveTest` يفحص ذلك بتسلسل خنق تصاعدي (null → 1.0): رفع عند الأول، ثم منع دائم لا يعود إلى رفع. و`MemoryStallTest` يفحص الرفض: نواة تصدّر `some` وحدها تُعلن `UNSUPPORTED` (عرضها مكان `full` كان سيقلب «انتظار عادي» إلى «خنق»)، والأرقام المشوّهة (`NaN`/`Infinity`/نص) تُرفض لا تُقصّ، وما خرج عن المدى يُقصّ إلى حدّه.

**عبء إضافي مُعلن:** إشارة ثامنة في اللقطة، **لا تُمرَّر إلى موصل RL الأصلي** (عقده سبع إحداثيات ثابتة — زيادتها كانت ستقلب معنى كل وزن في النموذج).

**الحدود المُعلنة:** `STALL_FRACTION = 0.10` غير معايَرة على جهاز، ومسار القراءة الفعلي (صلاحية `/proc/pressure/memory` بلا جذر على إصدارات مختلفة) لم يُجرَّب على جهاز — مُسجَّل في I-65.

## ADR-35 - Observable intent and evidence-first workspaces (2026-09-19)

Max AI remains one local control plane. UI preferences reflect observable engine state, not a remembered imperative getter. Request progress is distinct from verified hardware state; a tap never establishes success. Presentation freshness expires independently of engine emissions, rejects future timestamps as live, and never invents a reading before the first sample. A presentation clock does not request hardware reads.

The Max AI destination separates overview, journal, learning and controls while retaining the causal evidence and existing live command centre. Journal queries operate on recorded fields, preserve order, and do not mutate learning. Learning contexts are selected by stable key so new samples cannot silently switch the selected app. All changes reuse `ui/design/`, paired EN/AR strings, existing arbiter ownership and existing safety policy.

## ADR-36 — إعادة بناء واجهة مدير الملفات على طلب المالك، والنموذج لا يُمَس (2026-09-19)

**القرار:** أُعيد بناء **عرض** شاشة مدير الملفات (`FM-02`) استجابةً لطلب المالك الصريح بأنها «عديمة الفائدة
وغير متناسقة وصعبة الاستخدام»، وبمرجع سبع لقطات لمدير ملفات آخر قُرئت بنصوصها (‏`tools/read_image_text.py`).
والتغيير مقيَّد بحدّين: ① طبقة العرض — والنموذج الخالص يُعدَّل **إضافةً** لا إبدالًا (`EntryCounts` · `DiskSpace`
· `isHidden/withoutHidden/counts` · `showHidden`/`tabs` · `FileSystemEngine.diskSpace`)، و② كل ما يُثبت
السلامة يبقى كما هو: الحرس قبل الـshell، والأحكام الثلاثة للنتيجة، والإعلان عن المجهول («غير مقروءة» لا صفرًا).

**لماذا لا يخالف `ADR-18`:** ذاك يمنع إعادة ما بُني **لأسباب جمالية**. والطلب هنا استعمالي مُعلن، والتشخيص
مقيس لا مُدَّعى: أربعة أشرطة قبل أول صفّ في كل لوح، وأفعال موزّعة على ثلاثة أشرطة بأيقونات بلا أسماء،
وصفوف بسطرين تحمل الصلاحيات في كل صفّ. فالقاعدة تُطبَّق على «أعيدوا بنائه ليبدو أحدث» لا على «لم يعد صالحًا
للاستعمال».

**النتيجة الملزمة للجولات القادمة:**

1. **بنية الشاشة معلنة**: شريط أوامر الشاشة في الأعلى · **شريط سفلي واحد** يتبدّل دوره (أدوات اللوح النشط،
   أو إجراءات التحديد) · وداخل كل لوح: تبويباته · مساره · سطر حالته · قائمته — ولا شريط خامس يُضاف.
2. **لكل فعل اسمه**: لا زرّ أيقونة في هذه الشاشة بلا `contentDescription`، ولا إجراء في قائمة إلا بنصّه.
3. **الإخفاء يُعلَن**: كل ترشيح أو إخفاء يقصّ ما يُرى يجب أن يُصرّح بعدد ما أخفى في سطر الحالة — «مخفيّ
   ومُعلَن» غير «مفقود».
4. **ما لا يُقاس لا يُعرض**: لا حجم لمجلد غير مقيس، ولا إجراء لا ينطبق (`FileActionSet`)، ولا مساحة تُكتب
   صفرًا حين لم تُقرأ.
5. **التبويبات تخصّ اللوح** وتُحفظ مع حالته (`PaneSaver`)، ولا تُبنى من نصّ محفوظ في مكان آخر.

**الحدود المُعلنة:** لم تُرَ الشاشة على جهاز؛ وعدد التبويبات الكبير في لوح بعرض ~١٧٠ نقطة لم يُقس
(‏`HANDOFF` تكملة ٤١ §٦).

## ADR-37 — أطلس: بنك معرفة ثانٍ من مشاريع مفتوحة المصدر — **مفردات واجهات لا جداول مسارات** (2026-09-20)

**السياق.** أمر المالك: «وخذ من SmartPack وغيرها من مشاريع مفتوحة المصدر معرفتهم، لأنهم يجمعون هذه
الأشياء لسنوات، فإذا ما هو مخزّن عندنا لا يعمل يقوم أطلس بإكماله تلقائيًا». وهذا يقع على `D-02`
و`ATLAS-01` اللذين يمنعان «نسخ الأكواد والسكربتات وقواعد المسارات»، وعلى حكم `S01` نفسه: «Do not import
path maps, scripts, custom controls, or generic governor defaults».

**القرار.** يُضاف `AtlasCommunityBank` كبنك ثانٍ، و`AtlasCompletion` كمرحلة ثالثة في المسح، بهذه الحدود:

1. **ما يُنقل مفردات الواجهات**: اسم عقدة ↔ وحدة موثّقة ↔ جذر مُعدَد يمكن السير فيه. **ولا** جدول مسارات
   لكل جهاز، ولا سكربت، ولا معامل تحويل منسوخ، ولا سطر كود.
2. **كل مدخل يحمل `provenance`** (مصدر + مرجع + إصدار + سجلّ ترخيص)، ويمرّ **بنفس** مدقّق الكتالوج المراجَع:
   جذر مُعتمد، اسم أساس آمن، لا مفاتيح مكرّرة، ولا تعارض وحدات.
3. **الثقة لا تتجاوز `CLAIMED`/`FETCHED`**: ولا مدخل واحد بـ`SOURCE_VERIFIED`، لأنّ لا تنفيذ فُحص في هذه الجولة.
4. **القراءة منه لا تُرقّى**: `semanticStatus = INFERRED` ومرحلة `CANDIDATE_INTERFACE` — واسم واجهة معروف
   الوجود ليس معنًى مُراجَعًا.
5. **لا يُسأل إلا عن نقص**: النطاقات التي لم يُجب عنها البنك الأول، ولا تُعاد قراءة واجهة قُرئت، بحدّ ٣٢ مرشّحًا
   في المسح مضافًا إلى ميزانية النقل نفسها.
6. **لا يحرّك بوابة التقرير**: `reviewedUnresolved` يحصي نقص المراجَع وحده؛ غياب اسم مرشّح هو الحال المتوقَّعة.

**البديل المرفوض.** نسخ خريطة مسارات SmartPack كما هي: تخلط GPL-3.0 بـApache-2.0، وتُدخل «تخمينًا عند
الفشل» (قائمة حكام ثابتة عند تعذّر القراءة)، وتحوّل غياب الاسم إلى غياب الواجهة — وهو ما يرفضه أطلس.

**العاقبة.** `D-02` تبقى قائمة للكود والسكربتات وجداول المسارات، ويُستدرك عليها بالمعرفة بصيغة المفردات
مع الإسناد. وهذا الاستدراك **لا يوسّع الصلاحيات**: البنك الثاني يقرأ من نفس الأسطح المعتمدة، وبلا جذر،
وبلا كتابة، وبميزانية واحدة مع البنك الأول.

## ADR-38 — سقف الحرارة لكل تطبيق: مرجعه **قدرة الجهاز**، وبروفايلا القوّة يرفعان ولا ينزلان (2026-09-21)

**السياق.** قياس على جهاز صاحبه، ووصفه: «الحرارة في شاشة التطبيقات لا تزيد أبدًا عن القيمة الافتراضية،
وإنما تنقص». والسبب مُثبَت في الكود: النسبة كانت تُحسب من **السقف الحيّ** (`scaling_max_freq` كما هو
الآن). فجهاز سياسة الـvendor فيه تخفض السقف الحيّ إلى جزء من قدرته صار فيه «Gaming ٨٥٪» **أدنى من
الجهاز غير الممسوس**، و«Performance» لا يجد ما ينزله فيمرّ بلا أي كتابة على CPU.

**القرار.** يُصبح مرجع النسبة **القدرة المكتشفة** (`cpuinfo_max_freq`، أو أعلى درجة في سلّم السائق
المُعلن)، وللسقف نمطان لا نمط واحد:

```
بروفايلا القوّة (performance · gaming): target = max(النسبة من القدرة، السقف الحيّ)  ⇒ رفع مشروع، ولا خفض أبدًا
بروفايلات التبريد (balanced · power · custom): target = النسبة من القدرة، وتُكتب فقط إذا كانت أدنى من الحيّ
```

**الحدود غير القابلة للتفاوض.** الرفع متعهّد في المسار المحكوم القائم: ملكية `HardwareControlKey.cpuLimits`
· `CpuHardwareBackend.setPolicyLimits` (وقدّم `min <= max` والقصّ إلى الحدود المُثبتة) · `HardwareVerification`
· الاسترجاع إلى خط الأساس عند مغادرة التطبيق · وقفل المستخدم اليدوي يوقف المنحنى على تلك السياسة
(`cpu_policy_controls` لا يُمَسّ). ولا يُرفع إلا إلى **قيمة أعلنها السائق** — لا ثابت في الكود ولا رقم جهاز.

**الدعوى القابلة للتكذيب.** لا حالة واحدة يخرج فيها بروفايل قوّة **أدنى** من السقف الحيّ، ولا حالة يخرج
فيها بروفايل تبريد **أعلى** منه، ولا سقف مكتوب **فوق** النسبة المطلوبة من القدرة، ولا درجة غير مُعلنة
إلا حين لا يُعلن السائق سلّمًا (فعندها القدرة وحدها من `cpuinfo_max_freq`). وهذا مُثبَت بـ**١٥ حالة**
JVM في `ThermalCurveTest` — منها حالة القياس نفسها (قدرة ٢٤٠٠ تخفضها المنصة إلى ١٥٠٠ ⇒ Gaming يطلب
٢٠٠٠، لا ١٢٧٥).

**الحدود المُعلنة (لا تُدّعى).** أن المنصّة قد تعيد فرض سقفها بعد الكتابة (يُكشفه `direction=raise` مع
`live` أقل من الهدف في السطر نفسه) · وأن ارتفاع سقف CPU معنى حراري (يزيد الحرارة) ولذلك تبقى الطبقات
الأدنى مسؤولة عن الردّ: `ThermalGuard` التفاعليّ + `MaxAiThermalCurve` + خنق النواة. وأي تغيير يمسّ هذه
القاعدة يُثبت بنفس الاختبار أو يُرفض.

## ADR-39 — الإصدار **64-بت وحده**: `arm64-v8a` يفقد 32-بت من السلسلة كلها (2026-09-22)

**السياق.** طلب المالك تسريع البناء مرارًا، وعُرضت عليه مقايضات صريحة. واختار إسقاط `armeabi-v7a`
مع علمه بالثمن: **اثنا عشر بناء هدفًا في كل دفع** (خادم + preload بـ`ndk-build` عبر هدفين، وأربعة
crates Rust عبر هدفين) تصير **ستة**، ومقابلها تفقد الأجهزة 32-بت الدعم. **والثمن مُعلَن لا مسكوت عنه.**
وعند التدقيق: `terminal-emulator` لا يبني أصلًا (`externalNativeBuild` مُزال منه بقرار سابق)، و`libtermux.so`
يُشحن مبنيًّا لـarm64 وحده — فلم يكن له سهم في الحساب.

**القرار.** `arm64-v8a` هو المعمار الوحيد من أول السلسلة إلى آخرها، ومصادر الحقيقة صارت واحدة في كل موضع:
`archdaemon/jni/Application.mk` و`preloadbin/jni/Application.mk` (`APP_ABI`) · `:app` و`:kernel-flasher`
(`abiFilters` + استثناء تغليف في `:app`) · `dtolnay/rust-toolchain` + كل نداء `cargo ndk` ·
`compile_zip.sh` (دليل `libs/` الوحيد).

**والحدّ الذي لا يُتجاوَز.** الحزمة ترفض 32-بت **رفضًا صريحًا** لا تركيبًا ناقصًا: `mainfiles/customize.sh`
صار يرفض `"arm"` بـ`abort_arch` مع رسالة تسمّي المدعوم وحده، وبعده يُثبِت CI العقد من الطرفين:
الحزمة لا تحمل `libs/armeabi-v7a/`، والـAPK لا يحمل `lib/armeabi-v7a/` (وإن حمل يفشل البناء بأسماء
الملفات)، ويُتحقَّق من الأسماء الخمسة التي يستخرجها المنصّب (كان يُتحقَّق من اسم واحد).

**الدعوى القابلة للتكذيب.** لا سطر في مسار البناء يشير إلى `armv7`/`armeabi-v7a` إلا لحظره، ولا حزمة
تُنتج ثنائيًّا 32-بت، ولا جهاز 32-بت يُركّب ناقصًا. وهذا مُتحقَّق منه ثابتًا (`grep` على مسار البناء +
`yaml.safe_load` + `bash -n`)، و**يحتاج تشغيل CI واحد** ليُثبت الطرفين معًا.

**الحدود المُعلنة.** لم يُقَس شيء على جهاز: لا تركيب على جهاز 32-بت (يُتوقَّع رسالة الإجهاض)، ولا تشغيل
حزمة 64-بت على الجهاز. وملفات 32-بت المتبقّية في المستودع (`kernel-flasher/src/main/jniLibs/armeabi-v7a`)
**لم تُحذف**: حُرسها في التغليف بدل حذفها، فإن أُريد إعادة الدعم يومًا فالمصادر سليمة.
