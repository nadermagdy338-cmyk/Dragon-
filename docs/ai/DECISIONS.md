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
**Amended (round 226, `AU-01`):** the hub count is **ten**, not nine — **Audio** joins as the tenth domain (output devices, declared effects, then streams in its own stage; `SOUND-SCREEN-PLAN`). It sits under `Control` because audio is a system world like power and display, and it is read-first. The "nine worlds" figure was quoted in comments and older plans; the registry (`MaxDestination.All`) was and remains the one source, so this line is annotated rather than rewritten (ADR-18).

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

> ⚠️ **أُبطل هذا القرار بـADR-44 (2026-09-24، تكملة ١١٠):** عاد العمودان إلى السلسلة كلها بأمر
> المالك («دعم كامل لـarmeabi-v7a»)، ولم يبقَ من ADR-39 إلا صيغته: لا استثناء صامت — الحرس
> انقلب من «امنع 32-بت» إلى «اطلب العمودين»، فأصير كل ما هنا تاريخًا موثّقًا لا حكمًا ساريًا.

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

## ADR-40 — بطاقة المصفوفة تعرض **سعة** لا **ضغطًا**، والمدى الحقيقي للترددات يُقرأ من مسار واحد (2026-09-23)

**السياق.** طلب المالك الصريح: «أضف بطاقة تحت بطاقة التخزين… بأفضل تصميم وتناسق مع الشاشة الرئيسية»،
ووقع الاختيار على **إعادة مصفوفة الذاكرة** (RAM · ZRAM · التخزين الداخلي) — وهي التي حذفتها المرحلة ٤ من
خطة `storyboard-home`، وبقيت نصوصها في الموارد **بلا مستهلك** (`home_memory_storage` · `_desc` ·
`home_available_memory` · `home_available_swap` · `home_available_storage` · `home_internal_storage`
— ستة مفاتيح، مترجمة في ٨٤ لغة، واستخدام صفرًا).

**القرار.** ① تُعاد البطاقة **كحقيقة سعة** لا كحُكم: تُعرض المستخدم من الإجمالي والمتاح وأشرطة النسبة،
**بلا عتبات امتلاء وبلا ألوان إنذار**؛ وألوان الصفوف هوية (الابتدائي/الثالثي/الثانوي) لا حكم. والسبب
نصّ `ADR-34`: الامتلاء لا يقيس الضغط (جهاز بـ٩٠٪ سريع وآخر بـ٦٠٪ مخنوق)، فإدخال عتبة امتلاء ملونة هنا
يعني إدخال الحكم المحرّم من الباب الخلفي. ويبقى الحكم في موضعه: `FocusCard` تقول «يكاد يمتلئ»، والمحرك
يقيس PSI. ② وتُستهلك **المفاتيح الستة النائمة** كما هي بلا نصّ جديد، فلا دين ترجمة على إعادة البطاقة.
③ والاستثناء الوحيد: مفتاح مسمّى واحد `home_memory_swap_label` بـ`translatable="false"` (‏`ZRAM` اسم
وحدة لا كلمة)، وهو **استثناء معلن على ADR-14** (لا نظير له في `values-ar` لأنه لا يُترجم أصلًا).

**وأصلح معها عطب مُبلَّغ عنه.** «رسم GPU لا يعمل»: كان الرسم يُبنى من المدى المعلن وحده، فجهاز لا تُعلن
نواته سقفًا (أو لا يردّ مسار البائع) يُنتج `graphCeiling = 0` ⇒ **رسم فارغ** بينما تردداته المقيسة موجودة.
فصار ① الاحتياط `max(ceiling, current, observed)` لا سقفًا مصنوعًا (كل حدّ فيه رقم مقيس أو معلن)،
② وتردّد الرسوم الحالي يُقرأ — عند صمت مسار البائع — من `GpuHardwareBackend` **نفسه** الذي ترسم به شاشة GPU
(مسار واحد لفكرة واحدة)، ③ ومدى الرسوم (أدنى/أعلى) يُحلّ مرة واحدة لكل إقلاع من الجهاز نفسه، ويُعرض كحدّين
تحت الرسم مع خطّ أرضية على مستواه الحقيقي. وأرضية المعالج تُقرأ مع سقفه من عقدة العنقود (`cpuinfo_min_freq`)
في الدورة نفسها — لا نداء جديد في كل دورة قياس. و**ما لا يُعلن يُترك فارغًا**: لا أرضية مصنوعة من أدنى عيّنة.

**الدعوى القابلة للتكذيب.** لا رقم في البطاقة يأتي من مصدر ثانٍ لمصدر الشاشة المالكة له، ولا عتبة امتلاء
تُلوّن شيئًا في هذه البطاقة، ولا سقف/أرضية يُخترعان عند الغياب. والمفاتيح الستة خرجت من قائمة «نصوص بلا
مستهلك»، وواحد فقط أُضيف.

**الحدود المُعلنة.** لم يُبنَ ولم يُشاهد على جهاز: هل تُقرأ عقدة الرسوم فعلًا على الجهاز المُبلّغ عنه (وحدها
تجعل الرسم غير فارغ) **لم يُجرَّب هنا**، ومطابقة `LocaleList.getDefault()` إلى المدعوم لم تُختبر على جهاز
متعدد اللغات. وشارة «نسبة اكتمال الترجمة» داخل منتقي اللغة **لم تُشحن**: تحتاج توليدًا وقت البناء من
`tools/i18n_coverage.py` (علامة `--json` + مهمة Gradle)، وقُدّم تأجيلها على رمي رقم غير مقيس.

## ADR-41 — السطح الأساسي: **أربع وجهات بترتيب المالك**، وMax AI مدخلٌ من البطاقة الأولى لا مقعدٌ خامس (2026-09-24)

**السياق.** أمر المالك الصريح: ترتيب الشريط السفلي **الرئيسية ← التطبيقات ← التحكم ← الإعدادات**، ومدخل
**Max AI** في **البطاقة الأولى** من الرئيسية بدلًا من كلمة «النظام مستقر»، و**السمة** في الإعدادات فوق صفّ
اللغة. وهذا يخالف ما بُنيت عليه `NT-01` («الشريط = Now · Control · Apps · Max AI»)، فالتعارض **مُعلَن لا
مسكوت عنه**: قرار المالك اللاحق ينسخ ترتيب `NT-01`، والرخصة نفسها باقية (السجل `MaxDestination` هو المصدر
الوحيد؛ لا مسار يُكتب مرتين).

**القرار.**
1. **الترتيب مصدره واحد:** `MaxDestination.PrimaryDestinations = listOf(Now, Apps, Control, Settings)`
   هو نفسه ما يُبنى منه الشريط (`MainActivity`) وما يُقاس به ظهوره (`PrimaryRoutes`) — لا قائمة ثانية.
2. **الإعدادات مقعد دائم** (`isPrimary = true`)، وكانت تُفتح من الشريط العلوي وحده (عيب قابلية وصول).
3. **Max AI يُخرج من المقاعد** ويبقى مدخلًا من **البطاقة الأولى** في الرئيسية (وسم `filled` بنجمة واسم،
   `onClick`)، ومن موضعه الثاني القائم (`MaxAiActiveBanner`) — فالخروج من الشريط لم يُيتمّ شاشة.
4. **والسمة تُعاد إلى الإعدادات** (`ColorPalette` أبوه `Settings`، وصفّها أول صفوف مجموعة الرأس فوق اللغة)،
   ولا تُكرَّر في `Control → Tools`: تكرارها كان يعني موضعين يفعلان شيئًا واحدًا.

**الدعوى القابلة للتكذيب.** لا وجهة تُفتح من مكانين مختلفين في الواجهة نفسها، ولا وجهة تُخرج من الشريط
إلا ولها مدخل معلوم في الشجرة (Max AI: البطاقة الأولى + البانر). و`ControlLayoutModelTest` يبقى صادقًا: يربط
كل وجهة أبوها `Control` بالنموذج، فوجهة نقل أبوها إلى `Settings` تخرج من مقارنته لا تُكسره.

**الحدود المُعلنة.** لم يُبنَ ولم يُرَ على شاشة: ترتيب الشريط وثبات المدخل البصري **يحتاجان لقطة من المالك**؛
والشريط يظهر بشرط (`rootStatus && moduleInstalled && currentRoute in primaryRoutes`) فلم يُجرَّب ظهوره بلا
وحدة مثبَّتة.

## ADR-42 — الجذر وShizuku **سطحٌ واحد** بكشفٍ تلقائي وفعلٍ صريح، لا ثلاثة أبواب لموضوع واحد (2026-09-24)

**السياق.** أمر المالك: «دمج صفحة الروت و شيزوكو في شاشة واحدة… بعد تحسين التصميم وايضا امكانية التغيير
في شاشة الاعدادات وان يكون الاكتشاف تلقائي وليس يدوي او الاثنين معا تلقائي ويدوي لا فرق».
والمقيس في الشجرة قبل القرار: للجذر بابان ظاهران (`GetStartedScreen` صفحة ١ + صفّ `root_grant_title` في
`SettingsScreen`)، وللشيزوكو بابٌ ثالث (`PrivilegePanel`)، واللوحة المشتركة كانت **أصلًا** تعرض الاثنين —
فالتَّكرار في الأسطح لا في المنطق، وعلاجه حذفُ الأسطح لا كتابة منطق جديد.

**القرار.**
1. **سطحٌ واحد للامتياز** هو `ui/component/PrivilegePanel.kt`، يُزرع في شاشة البداية وفي الإعدادات معًا —
   ولا يُنسخ نصٌّ من طبقة الامتياز في أي شاشة أخرى (مصدر حقيقة واحد للعبارة والحالة).
2. **القراءة تلقائية، والطلب ضغطة.** الفحص يجري عند فتح السطح وعند العودة إليه، والقراءة **سلبية**:
   `PrivilegeManager.cachedRootGranted()` يقرأ الصدفة المُخزَّنة ولا يستدعي `su`، وShizuku يُستعلَم بـ
   `pingBinder`. ولا يُطلب امتياز عند الإقلاع. وإعادة الفحص اليدوية تبقى متاحة (تلقائي + يدوي معًا).
3. **لا نتيجة عابرة لِما يُقاس:** نتيجة طلب الجذر تُعرض في موضعها داخل السطح (`root_grant_ok/denied`)
   لا في snackbar يختفي — الشرح والنتيجة يبقيان مقروءين.
4. **من الإعدادات يُغيَّر لا يُشاهد فقط:** صفّ الإعدادات الواحد يعرض الطبقة الحالية ويفتح السطح؛ وصفُّ
   طلب الجذر المستقلّ أُزيل مع بقاء نصوصه مستعملة داخل السطح.

**الدعوى القابلة للتكذيب.** لكل طبقة امتياز موضعٌ واحد في الواجهة يُقرأ منه ويُغيَّر؛ ولا يوجد مفتاح
نصّي لطبقة الامتياز مستعملًا في شاشتين مستقلّتين — فأي `PrivilegePanel()` آخر هو نقضٌ لهذا القرار.

**الحدود المُعلنة.** البناء (`:app:compileReleaseKotlin`) أثبت الترجمة لا السلوك: شكل السطح وRTL وحلقة
نافذة الجذر عند فتح صفحة الامتياز تحتاج جهازًا. وتبقى نقطة قرارٍ للمالك: الطلب التلقائي للجذر مرّةً واحدة
عند الوصول إلى الصفحة أُبقي كما كان، وحذفه موضعان معلومان في `GetStartedScreen.kt`.

## ADR-43 — Max Atlas نظام الذكاء والتكيف المركزي: دورة سبع مراحل، وخريطة قدرة بسبع حالات، ومواءِمون لا قلبٌ يُعاد (2026-09-24)

**السياق.** أمر المالك: «أعد هندسة وتطوير Max Atlas ليكون نظام الذكاء والتكيف المركزي في MaxManager، وليس
مجرد نسخة أقوى من Max AI… Atlas = كيف أجعل MaxManager يعمل على هذا الجهاز؟ Max AI = ماذا يجب أن أفعل الآن
لتحسين هذا الهاتف؟ لا تنقل مسؤوليات Max AI إلى Atlas، ولا تجعل Atlas مجرد محرك Performance آخر»، ودورة
`Discover → Understand → Map → Adapt → Execute → Verify → Learn`، وأعمدة `Capability Discovery + Adapters +
Device Profiles + Runtime Verification` حتى يُضاف جهاز/كيرنل جديد **دون إعادة كتابة القلب**. والمقيس قبل
القرار: أطلس كان يملك خمس مراحل من الدورة (اكتشاف محدود الميزانية · مفردات دلالية · تنفيذ بمعاملة مُتحقَّقة ·
تحقق قراءة مرتجعة · تعلّم لكل جهاز) وياله من **Map** ولا **Adapt** ولا ملف جهاز ولا واجهة مركزية — وكل مقبض
كتابة له مُنتِج واحد مكتوب بيده.

**القرار.**
1. **الدورة ملكٌ واحد لكل مرحلة، ومواقعها في الشيفرة معلومة:** Discover/Understand = `core/atlas`
   (`AtlasDiscovery`/`AtlasBackendProvider`/`AtlasModels`) · **Map = `AtlasCapabilityMap` + `AtlasSafetyPolicy`** ·
   **Adapt = `core/hardware/AtlasAdapters`** (عقد مُلاءِم + سجلّ حتميّ + مُلاءِم مُتحقَّق واحد على الأقل) ·
   Execute = `AtlasAdaptiveExecutor` ← `HardwareRepairExecutor` ← المُحكِّم (حدّ المعاملة الوحيد كما كان) ·
   Verify = `HardwareVerification`/`WriteVerification` + نافذة التأكيد · Learn = `AtlasRouteMemory`/`AtlasEvidenceStore`.
   و`MaxAtlas` واجهةٌ تركيبية تجمع الدورة وتكتب **جدول الفصل Atlas ≠ Max AI** في صدرها: أطلس لا هدف ولا
   أولوية ولا «متى»، وMAX AI لا يكتشف عتادًا ولا يختار مسارًا ولا يكتب مباشرة.
2. **خريطة القدرة بسبع حالات وأسبقية ثابتة:** `NEVER_TOUCH` (السلامة فوق الكل) ← `SUPPORTED` (مسار مؤهل
   **و**نتيجة مُثبتة على هذا الجهاز — وحدها تدّعي نجاحًا) ← `WRITABLE` ← `NEEDS_ADAPTER` (الجهاز يُظهر سطحًا
   ولا مسار لنا عليه) ← `UNAVAILABLE` (**غياب مُثبت** بجردٍ لا فارغ، لا فشل قراءة) ← `READ_ONLY` ← `UNKNOWN`
   («لم يُقَس» و«قِيس فلم يُجب» برمزين مختلفين). وجدول الاشتقاق **واحد** (`AtlasCapabilityRules`) يخدم مَن
   التمساه: التشخيص والتنفيذ والتمثيل — فلا يُجيب سطحان بجوابين على سؤال واحد.
3. **قائمة عدم اللمس مُراجَعة فوق الجميع:** نقاط الحرارة ومحاكاتها · panic/sysrq · watchdog · `/sys/power` ·
   uevent — بمطابقة مغلقة الفشل على المفتاح والمسار معًا، يرفضها المُلاءِم قبل البناء و`MaxAtlas` قبل التنفيذ،
   ولا يرفعها نجاحٌ قديم ولا مُلاءِم ولا ذاكرة. ويبقى `AtlasSafetyClass` في كتالوجَي القراءة **`READ_ONLY` كما
   كان**: حالات الخريطة أحكامٌ على مسارات لا سلطة على العتاد، والسلطة تبقى للمُحكِّم وحده.
4. **ملف الجهاز يُبنى ولا يُخزَّن:** الهوية + الخريطة + ما تعلّمته الذاكرة، بصلاحيات صريحة (جيل إقلاع · جيل
   امتياز) — جيلٌ آخر يبطل المعرفة فلا تتحوّل إلى افتراض ثابت، وإعادة البناء هي التحديث. (`STATIC` لا تنتهي
   بالساعة — قِيس لا فُرِض.)
5. **التوسعة = مُلاءِم جديد لا تعديل قلب:** أي جهاز/كيرنل/طريقة تُضاف بتسجيل `AtlasControlAdapter` في
   `AtlasAdapterRegistry` (assess مُقاس · plan بمعاملة · probe لا يُنفَّذ)، وغيابُ الجميع فجوة `Gap` مُسمّاة
   السبب لا صمت ولا مسار مُختلَق. وكتبٌ جديدة تُصنَّف في `AtlasArchitectureTest.declaredOffReadPath` بأسبابها.

**الدعوى القابلة للتكذيب.** (١) لا مسار كتابة في المشروع يتجاوز حدّ المعاملة المُتحقَّق، و`CpuCeilingAdapter`
يلفّ `AtlasDiscoveredControl` ولا يملك كاتبًا. (٢) لا حالة `SUPPORTED` بلا نتيجة مُثبتة على الجهاز نفسه — يُقاس
في `MaxAtlasTest` («مدعوم» بعد `VERIFIED` وحده). (٣) عقدة ممنوعة تُرفض **قبل** فعل الكتابة في موضعين
(ملاءِم + واجهة). (٤) جهاز لا ملاءِم له يقول `NEEDS_ADAPTER` لا يصمت ولا يدّعي. (٥) إضافة ملف إلى
`core/atlas` بلا تصنيف تُسقط `AtlasArchitectureTest`.

**الحدود المُعلنة.** حكم سلامة Luna **علَق ولم يُغلق** (الـdiff يمسّ `core/hardware`/`core/maxai`، ولا أداة
استدعاء في الجلسة)؛ والبناء (`:app:testReleaseUnitTest` = 1474 ناجحًا) يثبت الترجمة ومنطق JVM لا سلوك عتاد؛
وملاءِما GPU/Thermal وسطح عرض ملف الجهاز مُسجَّلون في `NEXT_TASK` كتوسعة لا ك חוב.

### ADR-43.1 — عقدا GPU المُقيسان (2026-09-24، `ATLAS-ADAPT-02`)

أُضيف للمواءمة ملاءِما GPU (`GpuDevfreqCeilingAdapter` · `GpuOppPinCeilingAdapter` في `AtlasGpuAdapters`،
و`ControlRegistry.gpuCeilingControl` صار مُستهلكهما الإنتاجي: «ماذا» لـMAX AI و«كيف» لأطلس)، وأثبتت
الاختبارات عقدين مُلزمانَين لكل مُلاءِم قادم — وكلاهما خرج من مُكذِّبة لا من رأي:

1. **عقد `realized` = «بلغت» لا «تحتوي»** — `ceilingSatisfied` معناها `node ≤ desired`، وبوصفه عقد
   التخطّي (`HardwareRepairRequest.realized`) قُرئ سقفٌ أدنى من الطلب «مُبلَّغًا فعلًا» فتُتخطّى
   الكتابة فلا تُكتب قيمة أعلى أبدًا (ظهر في الاختبار: `writes=[]` مع `VERIFIED`). فكل عقد `realized`
   يُثبت **تساوي القيمة** (أو تحريرًا مُثبتًا عند القدرة) — وهي قاعدة `HardwareVerification.ceilingReached`
   نفسها في مقبض CPU.
2. **التحرير فعلٌ لا يُنفَّذ من داخل `apply`** — عند القدرة يُعاد خط الأساس بعد المطابقة الفاشلة فيُمحى
   التحرير («Performance بلا أثر» المقيس). فالتحرير (`releaseVendorCeiling`) يُنفَّذ **بلا كتابة تردد**،
   والرفع فوق السقف الحيّ يُحرَّر أولًا (`releaseRequiredForRetarget` بـ`plannedRelease=false`:
   القرار يُقاس لا يُخطَّط).

ووحدتا الأرقام محسومتان في العقد: نصوص المعاملة بوحدة العقدة كما تحفظها `GpuHardwareBackend.Device`،
و`hzMultiplier` هو المحوِّل الوحيد إلى لغة `GpuCeilingPolicy` (Hz) — فلا تُقارَن أرقامٌ بوحدتين أبدًا.

**دعاواهما القابلتان للتكذيب.** (١) ملاءِمٌ يجعل `realized` «يحتوي» يُسقط اختبار «الكتابة تقع وتُقرأ»
(`writes=[…]` لا `[]` مع `VERIFIED`). (٢) تحريرٌ يُنفَّذ من داخل `apply` يُسقط اختبار «الطلب عند القدرة
يُعلن رقم ما لا نملكه ولا يفشل مُكرَّرًا».

### ADR-43.2 — عقدا صِدق الخريطة وتبعُد التقرير (2026-09-24، `ATLAS-MAP-02`)

1. **«يحتاج مُلاءِمًا» ≠ «للقراءة فقط»** — مدخل `writeExpected` في جدول الاشتقاق، وحكمه المُلزم:
   **طلب تحكم وصل ⇒ كتابة متوقّعة دائمًا** (فغياب الطريقة فجوةٌ تُصلَّح)، والخريطة وحدها تسأل
   «هل يدّعي هذا البناء طريقة؟» (حضور ملاءِم مُسجَّل) — فمراقبةٌ تقرأ ولا تكتب تُقرأ `READ_ONLY`،
   ولا يَعِد صفٌّ بمسار لا يحتاجه أحد.
2. **تقرير الدعم يحمل الخريطة رموزًا لا جملًا** (`map:<state>:<reason>` + معرّف ملاءِم + `verified`)
   — والطرح يبقى صفرًا: لا حقل يحتمل مسارًا أو رقمًا أو سرّ. و`SCHEMA 2` مع **قبول v1 صراحةً**
   في `decode`: مرفق الأمس يُقرأ ولا يُرفض، وإلا صار الإصدار الموثَّق امتناعًا عن إعادة القراءة.

**دعاواهما القابلتان للتكذيب.** (١) مراقبةٌ تقرأ وتُصنَّف «يحتاج مُلاءِمًا» يُسقط اختبار
`a monitoring-only surface that reads is read-only…`. (٢) رفض مرفق v1 يُسقوط اختبار
`a report written before capability rows existed is still readable`.

## ADR-44 — **Android 17 (API 37) و32-بت معًا**: إعادة العمودين إلى السلسلة كلها (2026-09-24)

**السياق.** طلب المالك: «اجعل تطبيقي يدعم أندرويد 17 ويدعم هواتف 32-بت» — وهو عكس ADR-39 حرفًا،
ومبرّره صريح: «أريد أن يعمل على كل أندرويد دون مشاكل»، وأرفق مشروعه القديم (`Dragon`) الذي كان يحمل
الخط الكامل لـ`arm64-v8a` و`armeabi-v7a` معًا. **وقبل التنفيذ قِيس الواقع لا ذُكِر:** 
`platforms;android-37.0` و`build-tools;37.0.0` **منشوران ومستقرّان** اليوم (فالتقييد القديم «API 37
غير منشور في مستودع SDK» كان صحيحًا وقته، وقد انتهى)، و`armeabi-v7a` يملك **ثنائيات جاهزة في المستودع
ذاته** (`kernel-flasher/src/main/jniLibs/armeabi-v7a`) كان الحرس القديم يحجبها عن جهازها بالاستثناء.

**القرار.** يُبنى العمودان ويُشحنان معًا، وAPI 37 يُعلَن: `compileSdk = 37` و`targetSdk = 37` في `:app`
و`:terminal-emulator` و`:terminal-view` و`:kernel-flasher`؛ و`abiFilters` = `arm64-v8a` + `armeabi-v7a`
في `:app` و`:kernel-flasher`؛ و`APP_ABI := arm64-v8a armeabi-v7a` في `archdaemon` و`preloadbin`؛ وهدفا
Rust (`aarch64-linux-android` + `armv7-linux-androideabi`) في كل نداء `cargo ndk` وفي `dtolnay/rust-toolchain`؛
و`compile_zip.sh` يملأ `libs/armeabi-v7a/` بالمعيار **المشدَّد** نفسه (`copy_binary` يرفض المفقود بالاسم
والمسار — لا `|| true` الذي كان في المشروع القديم)؛ و`customize.sh` يختار `ARCH_TMP` بحسب `ARCH`.

**والحُرّاس انقلبت لا أُزيلت** — وهذا جوهر الفرق عن ADR-39: كان CI يمنع وجود 32-بت عمدًا، وصار يطالب
بالعمودين عمدًا: `lib/arm64-v8a/` **و**`lib/armeabi-v7a/` في الـAPK، والخمسة أسماء المستخرجة في
`libs/<abi>/` لكل عمود، و`libtermux.so` يُبنى للعمودين **قبل Gradle** (كان مُلتزمًا لـ64-بت وحده، فكان
`System.loadLibrary("termux")` يسقط على هاتف 32-بت عند فتح الطرفية).

**وحجبت الترقية بأمان:** `compose-bom` و`material3` و`navigation` بقيت على إصداراتها المُثبَّتة رغم أن
مانعها (سقف SDK 36) زال — لأن رفع سطر Compose/Material كله جولة مقيسة مستقلة، لا أثرًا جانبيًّا لتغيير SDK.

**الدعوى القابلة للتكذيب.** (١) `:app:compileReleaseKotlin` يفشل إن لم يقبل AGP مستوى API 37.
(٢) اختبارات `:app:testReleaseUnitTest` تسقط إن غيّر الـSDK سلوكًا مقيسًا. (٣) فحص `aapt2 dump badging`
يُظهر `targetSdkVersion` غير `37` أو `native-code` بعمود واحد — فيُكذَّب القرار بأمر واحد.

**الحدّ المُعلن.** المُقاس هنا: الترجمة على API 37، و**1495 اختبارًا** جَرَت على الإعداد الجديد، و**APK
مُفحوص المحتوى** (`targetSdkVersion:'37'` · `compileSdkVersionCodename:'17'` · `native-code: 'arm64-v8a'
'armeabi-v7a'` · ٥ مكتبات v7a داخل الحزمة). **وما لا يُقاس محليًّا:** الثنائيات الأصلية للعمودين
(`ndk-build`/`cargo ndk` — تحتاج NDK، وCI هو من يبنيها)، وتركيب على هاتف 32-بت حقيقي.

## ADR-45 — **قاعدة النقل إلى Rust**: ما يُنقل وما لا يُنقل، ولماذا (2026-09-24)

**السياق.** أمر المالك: «حوّل كل شيء يحتاج إلى ويستحسن إلى Rust بعد وضع خطة… وإذا كان النقل يحسّن ولو بنسبة
بسيطة فلا تتردد». والتحقيق العميق أنتج أن **الاختناق ليس اللغة**: التطبيق يفتح **صدفة جذر لكل قراءة** في
٢١٠ موضعًا، ويعيد قراءة الأنواع الثابتة كل دورتين، ويُقرأ كل عقدة عبر **معاملة IPC منفردة**، وخدمة المراقبة
تنبض كل **٥٠٠ مللي** بكتابتين و`fsync`.

**القرار — قاعدة الحدّ:** يُنقل إلى Rust **ما هو قراءة أو معالجة كثيفة على حدود النظام**:
قراءة sysfs/`/proc` مجمَّعة · مسح المناطق الحرارية · عيّنات العتاد الدورية · تحليل نصوص كبيرة ·
ضغط/تجزئة/أرشيف. **ولا يُنقل:** واجهة Compose · سياسة أطلس وأحكامه · المُحكِّم والملكية (ADR-11) ·
وما يُختبَر بعقود نصية في JVM. والصيغة المختصرة: **ما كان قرارًا يبقى Kotlin، وما كان قراءة/معالجة كثيفة يصير Rust.**

**والمسار الموجي** (التفصيل ومعايير القبول في `NEXT_TASK` §`RUST-PLAN-01`):
**٠** المعمارية أولًا — دفعات IPC + كاش بجيل الإقلاع + قراءات مباشرة بدل الصدَف (**منجزة، Kotlin**، وهي ما
سيصير لاحقًا تنفيذًا بلغة أخرى خلف العقد نفسه) · **١** قارئ مجمَّع بلغة Rust داخل عملية الجذر ·
**٢** خادم عيّنات دائم يُخرج حلقة ٥٠٠ مللي من عملية التطبيق · **٣** محلّل السجلات والتجزئة/الأرشيف.

**ولماذا الموجة ٠ بلغة Kotlin ليست تأخيرًا:** العقد (دفعة واحدة لكل دورة، وكاش إقلاع) هو ما يجعل نقل التنفيذ
لاحقًا **تغييرًا موضعيًّا بلا مساس بالمتصلين**؛ لو نُقلت القراءة إلى Rust قبل ذلك لكانت النتيجة نفس عدد
الرحلات بلغة أخرى — أي أن اللغة لا تُصلح عطبًا معماريًّا.

**الدعوى القابلة للتكذيب.** كل موجة تُثبت «قبل/بعد» برقم مقيس، أو تُعلن «غير مُتحقَّقة في هذه البيئة»:
(١) دفعة الحرارة: `IMtkService.readNodes` تجعل قراءات N منطقة **معاملة واحدة** — يُمتحن بمراجعة
`RootFileAccess.readMany` وسقوطه الحرفي إلى `read()`؛ (٢) الموجات ١-٣ تُقاس بـ`cargo test` على شجرة sysfs
وهمية + زمن دورة على جهاز — وما لم يُثبَّت يُعلن.

**وحدّ لا يُتجاوَز:** **لا يُنقل أي مسار كتابة عتاد** إلى Rust قبل حكم سلامة (ADR-07/11/16)، ولا تُبنى مكتبة
جديدة بلا مُستدعٍ إنتاجي — «الكود بلا مستهلك دَين».

## ADR-46 — **الموجة ١ من النقل إلى Rust منفَّذة**: قارئ دفعات داخل العملية، وسلّم سقوط يحفظ القدرة (2026-09-24)

**القرار:** يُضاف في `manager/src/main/rust` وحدة `probe` (مُصدَّرة عبر JNI إلى
`nd.max.core.jni.ProbeBridge`) تقرأ **عدة عقد في نداء واحد** داخل العملية: قيمًا (`read_many`)، ووجودًا
(`existing`)، وأسماء مجلد (`list_names`). وتُصبح **الطبقة الأولى** في `RootFileAccess` قبل IPC الجذر
والصدفة. والوحدة **قراءة فقط**: لا كتابة ولا `chmod` ولا حذف — فلا تمسّ ADR-11 ولا تفتح بابًا جانبيًّا للامتياز.

**السلّم (وهو جوهر القرار):** قارئ أصلي ← **ما لم يُقرأ وحده** يُسأل عبر معاملة IPC واحدة إلى
`MtkRootService` ← ثم `read()` لكل عقدة (صدفة الجذر آخرًا). فالسرعة **لا تُشترى بقدرة**: كل عقدة كانت
تُقرأ بالجذر تبقى تُقرأ بالجذر، ولا تُخفى عقدة ولا تُفترض قيمة، والعقدة الغائبة من كل الطبقات تبقى `null`.

**عقد الصيغة بين لغتين — يُحرَس من الجانبين:** `paths` سطر لكل مسار · `values` سطر لكل مسار بالترتيب
(الفارغ = لم تُقرأ) · `\u0001` = سطر جديد داخل القيمة · `flags` = `1`/`0` لكل مسار. والاختبارات على
**نفس المُدخلات حرفيًّا** في Rust (`probe::tests::same_vectors`) وKotlin (`ProbePacketTest`)، فأي انحراف
على أي جانب يُسقط اختبارًا — لا يمرّ صامتًا. والمحاذاة تُرفض عند أي اختلاف في عدد الأسطر: **حزمة مشوَّهة
أخطر من غيابها**، لأنها تنسب قيمة عقدة إلى عقدة أخرى.

**وأخبار مقيسة تُضاف إلى القاعدة (لا استثناءات مُعلَنة بلا رقم):**

* **`AppMonitor` لا يحتاج إصلاح إهدار:** كاتبا الحالة وتطبيقات الخلفية **يُحجمان بالتبدّل أصلًا**
  (`if (currentStatus == lastStatus) return`) — فلا تُكتب الآن ملفات كل ٥٠٠ مللي بلا تغيّر، ولا عطب يُدَّعى.
* **نقل أطلس لم يُنفَّذ، وهذا صواب:** نقله الفعلي (`AtlasFileReadTransport`) يقرأ بـ`java.nio.file` **داخل
  العملية** بلا صدفة ولا binder، مع تعيين دقيق لكل `errno` (ABSENT ≠ PERMISSION_DENIED ≠ UNKNOWN_CAUSE)
  وحدّ بايتات وميزانية روابط معدودة. دفعة Rust ستُفقد **أسباب الفشل الدقيقة** التي تفرضها مواصفة أطلس
  («إن لم يجد طريقة موثوقة يقول غير مدعوم») مقابل ربح لا يقيسه شيء — **فلا يُنقل**.
* **اختبار Rust متعفّن وُجد عند أول تشغيل حقيقي:** `power_predictor::tests::nan_never_enters_history` كان
  يسأل عن المحور الخطأ ويفشل. وسببه بنيوي: **لا خط CI واحد كان يشغّل `cargo test`** لـcrate التطبيق (كان
  يُبنى للـABIs فقط). فأُصلح الاختبار **وأُضيف خط `cargo test` (مضيف) في CI** — قاعدة تكتب نفسها: **كل ما
  يُبنى بلا اختبار يُشغَّل يتعفّن**.

**الدعوى القابلة للتكذيب:** عدد الرحلات في مسار الدفعة = **نداء واحد** لكل مجموعة عقد (مُثبَّت بالبنية
واختبارات الجانبين)، وزمن الدورة/الفتحة قبل-بعد على جهاز **يحتاج جهازًا** — ولا يُدَّعى هنا.

## ADR-47 — **الموجة ٣**: تحليل السجلّات **دفعةً** في Rust، والموجة ١.ب **لا تُنفَّذ** لسبب مقيس (2026-09-24)

**القرار (أ):** يُنقل تحليل سطور السجلّات إلى Rust في وحدة `logparse`، لكن **دفعةً لا سطرًا سطرًا**:
سطور logcat تُخزَّن خامًا في نافذة الإفراغ (٣٠٠ مللي) ثم تُحلَّل كلها في **نداء JNI واحد**، فيصير
عدد العبور إلى المترجم مرّتين إلى ثلاث في الثانية بدل نداء لكل سطر.

**وهذا يقلب قرارًا سابقًا بالقياس لا بالمزاج:** كان ADR-45 يقول «محلّل السجلات: يحتاج قياسًا قبل
النقل». والقياس جرى هنا على نفس الآلة ونفس الأسطر (٢٠٠ ألف سطر نمطي):

| الجانب | الزمن | الدليل |
| --- | --- | --- |
| Kotlin/JVM (النمط المرجعي، بعد تسخين JIT) | **٢٧٦ مللي** | قياس مؤقت ثم **حُذف** (لا يبقى في المستودع) |
| Rust (`parse_batch_packed`، release) | **١٨٠ مللي** | ‏**×1.5**، وقيمة `cargo test` نفسه تشغيلًا |

وربح النقل ليس في النسبة وحدها: مطابقة Kotlin تُنتج لكل سطر `MatchResult` وسبع مجموعات وثمانية نصوص
جزئية ⇒ **مليون وستمائة ألف كائن** لكل ٢٠٠ ألف سطر على كومة التطبيق، والعمل بهذا الشكل يعود إلى JVM
في كل نافذة. **وحدّه المعلن:** الرقم على مضيف x86 بـHotSpot؛ وزمن ART على جهاز **يحتاج جهازًا** — ولا
يُدَّعى.

**القرار (ب):** **الموجة ١.ب (نقل القراءة إلى داخل عملية الجذر عبر `.so`) لا تُنفَّذ.** وسببها مُقاس من
الكود: `MtkRootService` (وراثة `libsu`'s `RootService`) ينفّذ `readNode` بـ`File(path).readText()`
**داخل عملية الجذر** أصلًا — أي قراءة ملف محلية بلا صدفة وبلا معاملة إضافية. فنقلها إلى Rust لا يوفر
رحلةً واحدة، ويقايض ذلك بوضع فشل جديد: تحميل مكتبة أصلية في عملية `app_process` مطلقة بالجذر (مسارات
المكتبات فيها ليست مسارات الحزمة). والقاعدة التي تحكم: **لا يُقايض سقوطٌ محتمل بربحٍ غير مقيس**.

**السلوك لا يتغيّر في الحالتين:** `logparse` **محاكاة حرفية** لنمطَي Kotlin (بما فيهما `\s` الـASCII)،
وفرع الاحتياط والإسقاط منقولان كما هما. وأمّا `isBlank()` فقد **صحّحت قياسًا لا اجتهادًا**: Kotlin تبنيها على
`Character.isWhitespace(c) || Character.isSpaceChar(c)` — فالاتّحاد يجعل **U+00A0 فراغًا** (خلافًا لـJava's
isWhitespace وحدها)، ويُخرج U+0085 (NEL) من الفراغ، ويشمل `U+001C..U+001F`. وكانت محاكاتي الأولى تستثني
U+00A0 خطأً، فأمسك الانحراف **اختبار JVM على الجانبين** (رسب عند أول تشغيل) قبل أن يظهر فرق في تحليل
سجلّات جهاز حقيقي — وهذا هو سبب وجود الاختبارات العابرة للّغات أصلًا. ويُحرَس
التطابق بحارسين: حارس **رموز JNI** (كل `external fun` له تصدير `Java_...` بالاسم نفسه، وكل تصدير له
مستدعٍ — فلا `UnsatisfiedLinkError` على جهاز ولا سطح ميت)، وحارس **تطابق النمطين** (النمطان الحرفيان
في `LogsViewerViewModel` يجب أن يكونا مذكورين في وحدة Rust نفسها). والمحلّل المرجعي الكامل يبقى في
الشجرة مسارَ سقوط عند غياب المكتبة — فيُختبر هو في JVM كما كان.

## ADR-48 — **بوابة رموز JNI**: ما يقرأ الثنائية فعلًا، وما يُعلن عجزه (2026-09-24)

**القرار:** تُضاف أداة `tools/jni_symbols.py` بثلاث طبقات، تُشغَّل في CI مرّتين — قبل البناء (طبقة المصدر،
ثوانٍ) وبعد بناء ثنائيي المكتبتين (الطبقة الفعلية). وصارت **البوابة الرابعة** إلى جانب `kt_balance`
و`code_health` و`i18n_coverage`.

**لماذا وُجدت (وما لا تكفيه البوابات القائمة):** `external fun nativeReadManyPacked` مع رمز غائب في `.so`
**لا يُسقط ترجمةً ولا بوابة بنية ولا فحص موارد** — بل `UnsatisfiedLinkError` عند أول نداء على الجهاز،
وهو المكان الوحيد الذي لا نقيس فيه. وصنفه وقع هنا فعلًا: `libtermux.so` كانت ٦٤-بت وحدها.

**الطبقات وحدّ كل واحدة:** (١) المصدر: كل `external fun`/`native` في Kotlin/Java ↔ `Java_*` في Rust —
تعمل بلا مُصرّف وبلا جهاز. (٢) الثنائيات: `nm -D --defined-only` لكل `.so` مُشحونة: نواقص · يتامى ·
**انحراف ABIs** · **تغطية الأعمدة**. (٣) ما لم يُبنَ (لا NDK محليًّا) يُعلن «غير مُتحقَّق» ولا يُدّعى.

**خمسة قرارات ملزمة داخل الأداة:**

1. **التصريح يُنسب إلى ثنائيته من `System.loadLibrary`** في ملفه نفسه — لا من جدول مخفي يُصان يدويًّا؛
   وملف بلا نداء تحميل يُعلن «مكتبة غير معروفة» ويُسقط البوابة بدل التخمين، ونطاق الطبقة ١ محصور
   بمكتبات Rust المستخرَجة من `Cargo.toml` و`Android.mk` (وإلا نُسب تنفيذ C إلى Rust فصار عطبًا وهميًّا دائمًا).
2. **الرمز اليتيم تحذيرٌ لا عطب** (ADR-18): كود ميت لا يضرّ، وإسقاط إصدار من أجله حذفٌ لعمل. و`--strict`
   لمن أراد عكسه صريحًا.
3. **`--require-binaries` هي التي تجعل غياب الثنائية عطبًا — ووضع CI وحده.** فتشغيل المالك محليًّا (بلا NDK)
   يبقى ناجحًا ويقول بصوت مسموع: «هذه الطبقة غير مُتحقّقة».
4. **تغطية الأعمدة** (مكتبة لها عقد في عمود وغائبة في آخر) عطبٌ — **وهي صنف عطب `libtermux` نفسه** —
   ولا تنشط إلا إذا كان في الشجرة أكثر من عمود، فلا عطب وهمي في بيئة بعمود واحد.
5. **الأداة تُقاس على نفسها (‏27 حالة) وتُصرَّف ثنائية C حقيقية ثم تُقرأ بـ`nm`** — لأن قياس تحليل نصّ
   لا يقيس استدعاء الأداة. وهذا ليس تفصيلًا: **أربعة عيوب في الأداة نفسها** أمسكها هذا القياس وهي تُبنى
   (‏`U` تُقرأ مُعرَّفة · أعمار Rust تبتلع سطورًا · نقطة الحزمة تُشفَّر `_0002e` فتفرغ قائمة الأيتام
   وتُخفي عطبًا حقيقيًّا · وقيمة افتراضية مُجمَّدة تُعطّل اختبار التكذيب) — وكلّها كانت **تشهد زورًا**.

**ودرس يعمّ كل بوابة (‏`code_health`):** أداة تسأل عن بيئتها وتقرأ الفشل عطبًا **تُدرَّب الناس على
تجاهلها**. `stray_root_file` كان يسقط في بيئة بلا git على ملف **مذكور في `.gitignore`** (سؤال git يعود
128 ⇒ «غير متعقّب وغير متجاهَل»). فصار السؤال الأول «هل git صالح؟»، وحين لا، يُبلَّغ الملف
`stray_root_file_unverified` — **لا يُسقط الصحة، ولا يُخفى**.

**الدعوى القابلة للتكذيب:** كل رمز يُسقطه أحد الجانبين يُمسك من الجانبين — مُثبت باختبارات تكذيب على
نسخ من ملفات المستودع الحقيقية (تصدير Rust مسقَط ⇒ «ناقص مصدر» · تصريح Kotlin مسقَط ⇒ «يتيم مصدر» ·
رمز مسقَط في ثنائية مُصرَّفة ⇒ «ناقص ثنائية»)، وبمحاكاة CI بأربع ثنائيات في العمودين (خضراء) وبثنائية في
عمود واحد (حمراء).**وحدّها:** `nm` يعطي **الأسماء لا التوقيعات** — فعدد المعاملات وأنواعها وحال `static` تحتاج مُصرّفًا، ولا يُدّعى فحصها.

## ADR-49 — **الموجة ٣.ب**: ضغط zip إلى Rust **مشروطًا بالمُحرّك**، والفكّ يبقى Kotlin بحكم مقيس (2026-09-24)

**القرار (أ) — الضغط يُنقل:** تُضاف وحدة `archive` في `manager/src/main/rust` (تُصدَّر عبر
`ArchiveBridge.nativeCreateZip`) وتصير **الطبقة الأولى** في `FileArchiveEngine.createZip`، ويبقى تنفيذ Kotlin
**مسار سقوط معلنًا** كما في بقية الجسور.

**والنقل مشروط بمُحرّك بعينه، لا بـ«Rust»:** التبعية مُثبَّتة على `zip` + `flate2/zlib-rs` **عمدًا**، لأن
المُحرّك الافتراضي النقيّ ‏(miniz_oxide) **أبطأ من zlib الأصلية** — أي أن نقلًا بغير هذا الشرط كان سيُبطئ
الميزة. والقياس على شجرتين حقيقيتين من المستودع نفسه، وبنفس دلالات `FileArchiveEngine` (ترتيب أبجدي
بوحدات UTF-16 · مجلد فارغ بشرطة أخيرة · استثناء الأرشيف نفسه · سقف عمق ٤٠) وبنفس الدفعة ٦٤ ك ونفس
المستوى ٦ (وهو افتراضي `Deflater` عند JDK):

| العملية | الشجرة | JDK `java.util.zip` | Rust + miniz_oxide | Rust + **zlib-rs** |
| --- | --- | --- | --- | --- |
| ضغط | ٥.٣ ميجا · ٤٢١ ملفًا (نصّ) | 211ms | 217ms (**×0.97 — أبطأ**) | **107ms (×1.97)** |
| ضغط | ٣٧.١ ميجا · ١١٥١ ملفًا (صور/خطوط) | 1321ms | 1387ms (**×0.95 — أبطأ**) | **706ms (×1.87)** |
| فكّ | ٤٢١ مدخلًا | 96ms | 44ms (×2.2) | **34ms (×2.8)** |
| فكّ | ١١٥١ مدخلًا | 261ms | 183ms (×1.4) | **173ms (×1.5)** |

**والصحّة مقيسة لا مُفترضة:** أرشيف Rust فكّه Java مطابق للشجرة الأصلية بايتًا ببايت، وأرشيف Java فكّه
Rust كذلك، و**قائمة المداخل متطابقة بالاسم والترتيب** (٤٢١ = ٤٢١) — فالتبادل بين المكدّسين ليس تخمينًا.

**القرار (ب) — الفكّ لا يُنقل، وهذا هو الأهم:** ربحه المقيس ‎٠.١–٠.٤‎ ثانية، ومقابله تنفيذ **ثانٍ** لقاعدة
أمنية قائمة — `zip-slip`: رفض مدخل غير آمن (`../` · مسار مطلق · `C:`) **قبل** أول بايت يُكتب، وهو ما
يُختبَر في Kotlin باختبار يفحص **أين كُتب** لا «هل نجح الفكّ». ومسار الفكّ المحور يبقى موضعًا واحدًا
تُحرَس فيه تلك القاعدة (ونصف جدول القياس أعلاه لا يزال مُسجَّلًا، فللرجوع عنه عُدّة أرقام لا مزاج).
و**حارسه:** `ArchiveBridgeSymbolTest.extraction deliberately has no native bridge` — إضافة جسر فكّ يومًا
تُسقط اختبارًا يُسأل صاحبه عن حكم سلامة.

**الثمن معلن رقمًا:** المكتبة الأصلية ‎729,904‎ بايت ← **‎1,097,280‎** (+367KB · +50٪) بـzlib-rs،
و‎1,009,136‎ (+279KB) بـminiz — أي ≈‎+0.73‎ ميجا في الحزمة للعمودين (arm64-v8a + armeabi-v7a). وهذا ثمن
يُدفع من حجم الإصدار مقابل ٢× في عملية يلمسها المستخدم مباشرة (ضغط مجلد) — وهو قرار مالك لا قرار تنفيذ.

**ثلاث قواعد سلوكية لا تتغيّر بالنقل:**

1. **التقدّم:** المسار الأصلي يكتب في نداء واحد فلا يُبلّغ تقدّمًا، فمن طلب تقدّمًا حقيقيًّا
   (‏`progress !== NO_PROGRESS`) أخذ مسار Kotlin بنفس دلالته السابقة — فلا شريطًا يتجمّد ولا تقدّمًا مُختلقًا.
2. **`NoSources` يُحكم في Kotlin بلا رحلة** — حكم يعرفه الطرفان ولا يحتاج مكتبة أصلية.
3. **سبب مجهول لا يُبتلع:** ما لا نعرفه من الأسباب الرمزية يُعلن `WriteFailed`، ولا يصير «نجاحًا» ولا
   «لا مصادر»؛ وردّ مشوَّه يُرفض كاملًا فيعود المتصل إلى Kotlin (لا محاذاة مخمّنة).

**الدعوى القابلة للتكذيب:** الضغط بالمسار الأصلي أسرع من JDK بنحو **×١.٩** على الشجرتين — ويُبطلها أن
يُقاس أبطأ أو مساويًا على جهاز حقيقي. **وحدودها:** الأرقام على مضيف x86-64 بـJDK 17/5 HotSpot؛ وزمن ART
على هاتف **يحتاج جهازًا** · وبناء ثنائيات Android للعمودين **في CI وحده** (لا NDK محليًّا) · ومقارنة الفكّ
جزئيًّا بين نمطين مختلفين للإدخال (‏JDK يبثّ تسلسليًّا بعد مسح مركزي، وRust يقرأ الفهرس ثم يقفز)
— وهذا مُعلن لا مستور.

## ADR-50 — **الموجة ٣.ج**: مسح المساحة إلى Rust — §مُسجَّل متأخرًا (§0.3: التوثيق آخرًا)

**القرار:** تمشية شجرة `StorageUtil.scan` تُنقل إلى وحدة `scan` في `manager/src/main/rust`، وتُصدَّر عبر
`ScanBridge` بثلاثة رموز لا رمز واحد (نداء يعمل · قراءة عدّاد التقدّم الحيّ · طلب إلغاء)، وتبقى حلقة
Kotlin **مسار سقوط معلنًا** في `StorageUtil.scan`.

**والقياس قبل النقل** (شجرتان حقيقيتان · ٢٣,٢٢٤ و١٨,٩٦٥ ملفًا · نفس التصنيف ونفس الترتيب):
**JDK 447ms ← Rust 209ms (×2.1)** و**JDK 285ms ← Rust 101ms (×2.8)**، والنتيجة مطابقة بالحرف (نفس
البايتات وعدد المداخل والمصارف). وسبب الفرق بنيوي: كل مدخل كان في JVM يكلّف كائن `File` وسؤالَي نظام
(`isDirectory()`/`length()`) وصندوق `runCatching`؛ ومسح ١٢٠ ألف مدخل يعني ملايين الكائنات الوسيطة.

**ثلاثة عقود تُحفظ كما هي لأنها الميزة لا التجميل:** السقف يُعلن (`truncated` — فلا يُقدَّم الناتج كأنه
الجهاز كله) · المتخطّى يُعدّ (`skipped`) · وملف بحجم غير موجب يُعدّ ولا يُجمع فيبقى «المسح» أوسع من «المجموع».

**عيب تصميم أمسكته اختبارات الوحدة نفسها (يُسجَّل لأنه لم يُشاهد في أي أداة أخرى):** علم الإلغاء **عام**
مع مسحَين متوازيين = تشويش متبادل (مسح يبدأ فيلغي طلب غيره، أو يمسح علم إلغاء غيره). أُصلح بقفل مسح
واحد + إبطال الطلب الأقدم **بعد** القفل لا قبله، وبأن بدء مسح لا يمحو طلب إلغاء من خيط آخر. وكان أول
إصلاح أنقص: «امسح العلم عند البداية» **كان يبتلع الإلغاء** — أي أن الاختبار كشف الإصلاح الخاطئ أيضًا.

**الحراسة:** ١٠ اختبارات Rust في الوحدة + `ScanPacketTest` (٧) + `StorageScanNativeMappingTest` (٤)،
وبوابة `jni_symbols.py` تحرس الرموز الثلاثة في المصدر وفي الثنائيات (ADR-48).
**والحدود:** الأرقام على مضيف x86-64 بأقراص محلية؛ وزمن ART وI/O الهاتف **يحتاج جهازًا**.

## ADR-51 — **الموجة ٢ (`sampled`) تُغلق بالقياس**: المكسب سقفه ٠٫٢ مللي، والثمن يزيد عليه (2026-09-24)

**ما كانت تطلبه الخطة:** خادم عيّنات دائم يقرأ العتاد كل ٥٠٠ مللي ويكتب **لقطة**، والتطبيق يقرأ اللقطة —
«يُخرج الحلقة والحمل من عملية التطبيق؛ ودورة اللوحة تصير صفر صدفة/نداء».

**وقبل بناء خدمة، قِيست الدعوى على ثلاث طبقات** (مسطرة خارج المستودع، ومضيف x86-64):

| الحدّ | المقيس | معناه |
| --- | --- | --- |
| دفعة داخل العملية: ١٢ عقدة حقيقية | **٢٣٤ ميكرو** | هذا **سقف** كل ما يمكن أن يوفّره نقل القراءة خارج العملية |
| قراءة لقطة مكتوبة مسبقًا | **٩–١٢ ميكرو** | التطبيق يوفّر ≈٢٢٢ ميكرو لكل دورة (بدل ٢٣٤) |
| نشر ذرّي (tmp+fsync+rename) | **٥٢٤ ميكرو** (وبلا fsync ١٣٦) | وكل خصيصة غير مضبوطة تُدفع من طرف المُنتِج |
| ولادة عملية واحدة | **١٣٤٥ ميكرو** | أرضية أي مُنتِج يُنادى بدل القراءة |

**والنتيجة البنيوية، وهي الأهم:** اللقطة **لا تُنقص عمل النواة ولا سؤالًا واحدًا** — التطبيق لا بدّ أن يحصل
على القيم ليُعرضها؛ فما يوفّره النقل هو **زمن القراءة نفسه** الذي سقفه ٢٣٤ ميكرو لكل دورة (≈٠٫٠١٪ حِمل)،
في حين يدفع المُنتِج **٥٢٤ ميكرو × ٤ مرّات** لكل دورتين (لقطة كل ٥٠٠ مللي ودورة كل ٢ ثانية) — أي **خسارة
صافية بمعامل ٢٫٥–٩×**، قبل خدمة جديدة (وحدة/إقلاع/SELinux) تحتاج جهازًا وحكم سلامة.

**وقد كان المعيار الثاني للقبول («صفر صدفة في دورة اللوحة») هو الحمل الحقيقي — وقد تحقّق في الموجة ٤**
لا بخدمة: دورة اللوحة كانت **٣٠٧٠٢ ميكرو** (صدفة تفرّخ `cat` لكل عنقود!) وصارت **٨٢ ميكرو** = **×٣٧٤**.
فالحمل لم يكن في **موضع** القراءة بل في **نمط إفراخ عملية لكل عقدة** — وهذا يُنتزع بالتحويل لا بخدمة.

**القرار:** (أ) **لا يُبنى خادم عيّنات** — لا في التطبيق ولا خارجًا. (ب) ولا تُبنى «لقطة داخل العملية»
للتوفير بين مستهلكين: سقف توفيرها هو تكرار القراءات، وهو أقلّ من ٠٫٠٤٪ حِمل (مقيس بالحدّ الأعلى).
(ج) **والاستعمال الوحيد الذي يُبرّر مُنتِجًا خارج العملية هو عيّنات التاريخ أثناء نوم التطبيق** — وهي
ميزة أخرى لها مستهلكها وقرارها، وليست «دورة اللوحة». (د) وصفة تكذيب على جهاز (٣ دقائق) في `VALIDATION` §11.

**الدعوى القابلة للتكذيب:** لا يوجد جهاز يُظهر أن نقل دورة اللوحة خارج العملية يوفّر أكثر من **٠٫٣ مللي**
لكل دورة، ولا أن الخدمة تُلغي **أي** سؤال نواة (لأن التطبيق يقرأ ما يعرضه في الحالتين).

## ADR-52 — **الموجة ٤**: الخصائص تُقرأ أصلًا، و«صدفة لكل عقدة» تُمحى (2026-09-24)

**القرار (أ) — قراءة الخصائص إلى bionic:** وحدة `sysprop` (`__system_property_get`) وتُصدَّر عبر
`PropBridge` (`nativeGetProp` · `nativeGetPropsPacked`)، و`PropertyUtils.get` صارت **الأصلي أولًا**
والانعكاس احتياطًا. والمقيس: `getprop` عبر صدفة **٢٣٦٦ ميكرو** ← **١٢٫٦ ميكرو** (**×١٨٧**) — وخصيصتان
في مسار مفتاح Max AI، وأربع في فحص إصدار HyperOS تُقرأ الآن **في نداء واحد**.

**والأهم أن الأصلي أوثق لا أسرع فقط:** انعكاس `android.os.SystemProperties` سطح مخفي غير مضمون عبر
الإصدارات، وحين يُحجب يرجع `def` **صامتًا** — أي قيمة تبدو مقروءة وهي ليست كذلك. و`__system_property_get`
سطح ثابت منذ Android 1.0، و`PropertyUtils.setAndConfirm` (تحقّق الكتابة) صار يقرأ الحقيقة منه مباشرة.

**القرار (ب) — «صدفة لكل عقدة» تُمحى:** `HomeDashboardViewModel.readCores` كان يبني صدفة تُفرّخ `cat`
لكل عقدة (١٦ عملية فرعية لدورة من ٨ أنوية). والقياس: **٣٠٧٠٢ ميكرو ← ٨٢ ميكرو** (**×٣٧٤**) لكل دورة كل
ثانيتين. ونفس النمط أُزيل من `readSwap` (`/proc/meminfo`) ومن `ZramHardwareBackend.isActiveSwap`
(`/proc/swaps`) ومن حكام mali (`glob` ← `listNames` + قراءة دفعة).

**والقراءة الأصلية لا تشتري قدرةً:** عقدة لا يقرأها uid التطبيق تعود `null` فيسألها سلّم السقوط المعلن
(IPC الجذر ثم الصدفة) كما كان — ولا تُخفى عقدة لصالح السرعة.

**والكتابة تبقى كما هي عمدًا:** `setprop` وعقد sysfs تحتاج جذرًا وسياسة SELinux، وثمنها **الولادة**
(١٣٤٥ ميكرو مقيسة) لا الكتابة — و‏Rust لا يُلغي ولادة عملية (ADR-47)، فالنقل هناك بلا مكسب يُقاس.

**وحدود معلنة:** أرقام المضيف (x86-64) وزمن bionic على ARM **يحتاج جهازًا** · والثنائيات لعمودَي أندرويد
تُبنى في CI وحده · و**عطبَان أمسكهما المُصرّف** في هذه الجولة (`raw.uppercase` على عنصر `String?` بعد
بندل nullable، و`$it` مقروءة استيفاءً في نصّ اختبار) — وهذا هو نفس مبرّر تشغيله عند كل تغيير Kotlin.

## ADR-53 — **ترتيب بناء `jniLibs` عقدٌ لا مصادفة**: `ndk-build` يحذف `lib*.so` في مجلد خروجه (2026-09-25)

**العطب الذي أطلقه سجل CI (لا شكوى):** بوابة `ADR-48` أسقطت التشغيل بـ
`⚠️ مكتبة معلنة بلا أي ثنائية: 1  maxmanager_native — معلنة في Kotlin ولا ثنائية لها في الشجرة`
و`ثنائيات مقروءة 2`: أي أن المقروءتين هما `libtermux.so` في العمودين، **ولا وجود لـ`libmaxmanager_native.so`**.

**والسبب الجذري قاعدة في `ndk-build` نفسه، لا في البوابة ولا في مسار `cargo ndk`:**
`ndk-build` يحذف **كل** `lib*.so` في مجلد خروج المكتبات لكل ABI **قبل** أن يثبّت وحداته — قاعدة
`clean-installed-binaries` في `build/core/` من الـNDK (`rm -f <libs>/<abi>/lib*.so`؛ وُجدت لتفادي المكتبات
البائتة، وتحذف ما لا تعرفه). وترتيب الخطوتين في `build.yml` كان `cargo ndk` ثم `ndk-build`، فالأخيرة
**محت** ما بنته الأولى قبل ثوانٍ — وهو تحديدًا ما رآه المُشغّل: ثنائيتان لا أربع.

**والأثر الحقيقي أخطر من فشل بوابة — وهذا ما يجعل القرار عقدًا لا تجميلًا:** في الفترة نفسها كان الـAPK
**يُحزَّم بلا الطبقة الأصلية**، فيسقط `System.loadLibrary("maxmanager_native")` ويعمل التطبيق في الاحتياط
الدائم (`nativeAvailable=false`) بلا وكيل ولا متنبّئ ولا توأم ولا محرك سياق — **في صمت**، لأن حرس الـAPK
القديم كان يقيس **وجود العمود** (`lib/<abi>/`) لا وجود **المكتبة** بالاسم، ووجودُ `libtermux.so` كان يكفيه.
فالبوابة الجديدة لم تكن عقبةً بل أوّل ما صدح بالحقيقة.

**القرار — ثلاثة أجزاء، كل جزء في موضعه الذي يقيس فيه:**

1. **الترتيب:** `ndk-build` (الطرفية) أولًا، ثم `cargo ndk` (التطبيق) فلا يمسّها ماسح. ولا يهمّ أيّهما قبل
   الآخر لـGradle: هو يحزم `jniLibs` بعد الاثنين، والمقصود ألّا تمسح خطوة ما بنته أختها.
2. **توكيد قبل البوابة** («Assert jniLibs holds both libraries in both ABIs»): يقيس الأربعة بالمسار،
   ويسمّي الغائب فيها **قبل** البوابة، ويقول السبب المتوقّع (قاعدة الحذف) فلا يُعاد تشخيصه من الصفر.
3. **حرس الـAPK يقيس المكتبتين بالاسم** في كل عمود، لا وجود العمود وحده — فيقيس **الحزمة النهائية**
   نفسها بعد التوقيع، وهي الحقيقة التي وصلت المستخدم.

**وحدود معلنة:** لا NDK في هذه البيئة، فلا مصدر r29 مقروءًا هنا ولا `ndk-build` يُشغَّل: القاعدة مأخوذة من
مصادر الـNDK (`build/core/`، وهي في الشجرة الرسمية وقاعدة قديمة مستقرّة)، ودليلها القاطع الملازم هو مطابقة
ما رآه المُشغّل لما تُنتجه القاعدة حرفيًّا: **ثنائيتان لا أربع**، وهما بالضبط `libtermux.so` في العمودين — أي
الباقي بعد حذف `lib*.so`، لا ناتج بناء فاشل (ولو فشل `cargo ndk` لسقطت الخطوة قبله). وتنفيذ الترتيب الجديد
يُقاس في CI وحده. والمقيس هنا اثنان:
**(أ)** تمثيل حالة ما بعد الإصلاح (ثنائيتا المضيف في مجلدَي ABI) ⇒ `jni_symbols --assert --require-binaries`
**خروج 0** بـ«ثنائيات مقروءة 4» و«تحقّقت الثنائيات لعقود: maxmanager_native, termux»، وبإزالتها ⇒ **خروج 1**
(فالبوابة تفصل الحالتين فعلًا، ولم تُرخَّ). **(ب)** منطق حرس الـAPK بالتكذيب على أرشيفين مبنيّين: الكامل ⇒ 0،
والناقص ⇒ «missing lib/armeabi-v7a/libmaxmanager_native.so» و1.

**ولماذا لم يُحذف الرمز اليتيم:** `Java_com_termux_terminal_JNI_setPtyUTF8Mode` مُعرَّف في `termux.c:191`
ولا مُعلن له في `JNI.java` (أربعة مُعلنات: `createSubprocess` · `setPtyWindowSize` · `waitFor` · `close`)
ولا مستدعي له في الكود، ووظيفته (`IUTF8`) تُؤدّى **أصلًا** عند إنشاء الـpty (`termux.c:57`). فهو كود ميت
لا يضرّ ⇒ يُعلَن ولا يُسقط الإصدار (ADR-18)، ولا يُحذف من ملفّ موروث لتحسين منظر.

## ADR-54 — **الفضل يُعلن في `README`، والإشعارات تبقى ملزمة في `THIRD_PARTY_NOTICES.md`** (2026-09-28)

**الأمر:** «أريدك أن تضيف في اقرأني.md credit» + «قم فقط بإضافة credit للمشاريع الخارجية التي عندنا
**بعد التحقق حقًّا**» + «لا تذكر مبنيّ على AZenith — أضف الفضل فقط».

**والعقد الذي كان قائمًا:** `THIRD_PARTY_NOTICES.md` كان ينصّ: «ولا يُعضَّد بذكرٍ دعائي **ولا يُعرض في
الواجهة ولا في `README`**». فأمر المالك يخالف حرف المبدأ — **والتعارض يُعلَن ولا يُسكت عنه**.

**القرار:** يُعدَّل المبدأ: يُحفظ القدر الذي تفرضه الرخصة، ولا يُعرض في الواجهة، **ويُذكر أصحاب المكوّنات
القائمة في `README` باسم العمل وحقّ النشر ورخصته (قسم `Credits`)** — إعلانًا للفضل لا نقلًا للإشعار؛
وملفّ الإشعارات يبقى **وحده** المرجع الملزم ووحده موضع النصّ الكامل للرخصة.

**ولماذا لا يناقض ذلك القاعدة:** الجدول ثلاثة أعمدة (العمل · صاحب الحقوق · الرخصة)، بلا وصف تسويقي
وبلا نصّ رخصة منسوخ ولا شعار. ولا واحد من المذكورين يشترط العرض في `README` — **إلّا AZenith** فهو
يطلب الفضل صراحةً («you may use it but give credits!») ويوزّع `NOTICE.md`، ومادّة Apache-2.0 §4(d)
تُوجب حمل إشعارات `NOTICE` في العمل المشتق، فالجدول هو النسخة المقروءة المطلوبة.

**وحدّ القياس (لا الذاكرة):** كل اسم في الجدول يقابله ملفّ في الشجرة — AZenith (٢٤٧ ملفًا بترويسة
Zexshia) · Encore Tweaks (٢٠ ملفًا) · Rianixia-ThermalCore (١٦ ملف Rust) · VMTouch
(`preloadbin/jni/main.c` المُضمَّن). **وأُزيل `AnyKernel3` من ملفّ الإشعارات ومن `license_audit.py`**
لأنه **لا ملفّ له في الشجرة** — ادّعاء بلا ملفّ يُحذف ولا يُبرَّر (والأسلوب الذي في `mainfiles/customize.sh`
أسلوب تثبيت لا كود منقول). **ولا بوابة جديدة** (قرار المالك: «أنا أفضل عدم ذلك») — فالرقابة على
القائمة مراجعة، وهي مشروطة بـ«بعد التحقق حقًّا» لا بأداة.

## ADR-55 — **الترخيص المتسامح يُنقل باعتماد، والمفروض لا يُنقل إطلاقًا** (2026-09-30)

**الأمر:** «لا تكون تقليد ونسخ من الآخرين بل مرجع، ولا تنسخ حتى الشكل لأن تطبيقي مغلق المصدر … لا
أمانع المشاريع صاحبة الترخيص التي تسمح بجعل المفتوح مغلق المصدر، ويمكنك حتى إضافتهم في credit … وإذا
كان الأمر صعبًا جدًا صنعه من الصفر للتعقيد … أنه أفضل نسخه بعض الإمكانات ووضعه في credit.»

**القرار:** ثلاث فئات لا رابعة:

1. **متسامح** (`MIT` · `Apache-2.0` · `BSD-3-Clause`) ⇒ **يجوز النقل ولو جزئيًّا**، مع الإشعار في
   `THIRD_PARTY_NOTICES.md` والفضل في `README` ← `Credits`.
2. **مفروض** (`GPL` · `AGPL` · أي ترخيص يوجب فتح مصدرنا) ⇒ **صفر كود، ولو سطرًا**؛ والمشروع مغلق ولا
   يُفتح أبدًا، والبوّابة `tools/license_audit.py --assert` تمنع الانزلاق.
3. **غير مُثبت** ⇒ **لا يُنقل منه شيء** حتى يُقرأ نصّ ترخيصه.

**السبب:** الترخيص المفروض يجعل «استخدام شيء» مساويًا «فتح المشروع»، وهو ما يرفضه المالك رفضًا مطلقًا —
فالحدّ قانونيّ لا ذوقيّ. وفي المقابل، رفضُ النقل من المتسامح مطلقًا يُكلّف إعادة اختراع بلا مقابل في
مواضع يستحيل فيها أو تكلف أكثر من اللازم.

**النتيجة:** (١) **الشكل/التصميم لا يُقلَّد** — الاستعارة معلومة ومنهج، والرسم من لغة التصميم عندنا
(`ui/design/`)، لا من شكل تطبيق مرجعيّ؛ (٢) **الفضل يُعلن حتى حين تُؤخذ الفكرة بلا كود** إذا كان
المشروع فريدًا (تعضيد لـADR-54)؛ (٣) كل خطة مراجع تُصنَّف مراجعها بهذه الفئات في **قسم صريح**؛
(٤) `license_audit.py --assert` تبقى البوّابة المقيسة، وكل ادّعاء اعتماد يقابله ملفّ أو مصدر مقيس.

**وسندها المقيس:** `license_audit.py --assert` ⇒ **exit 0** («لا مكوّن GPL في مسار الإصدار»)، والفضل
في `README` ← `## Credits`، والإشعارات في `THIRD_PARTY_NOTICES.md` (المكوّنات المشمولة كلها متسامحة:
Apache-2.0 · BSD-3-Clause · MIT).

## ADR-56 — **`AR-R1` يُعدَّل بأمر المالك: عزل الجذر لكل تطبيق مأذون به** (2026-09-30)

**الأمر:** «ابحث عن مشاريع مثل hma oos **يخفي الروت عن التطبيق نفسه الذي يختاره** … يدخل تطبيقي ويحدد
علامة على تطبيق البنك فيخفي ويعزله حتي يعمل التطبيقي البنكي ولا يكتشف شيئًا».

**والتعارض مُعلَن لا مسكوت عنه:** `AR-R1` («دمج إخفاء الجذر (SUSFS/Zygisk/DenyList)») كان **مرفوضًا**
بعلّتين: «خارج الهوية» و«سباق كشف لا ينتهي». وأمر المالك **يُعدّله**، و**العلّتان تبقَيان مكتوبتين** في
هذا القرار ولم تُمحَيا — لأن الكلفة عُرضت عليه ثلاث مرّات وقَبِلها صراحةً، والقرار قراره.

**القرار الجديد:** يُبنى «عزل الجذر لكل تطبيق» **بوصفنا مديرًا لِما ثبّته المستخدم**، لا مُنفّذًا للإخفاء:

1. **صفر كود** من `Magisk` · `Shamiko` · `ZygiskNext` · `ReZygisk` · `NoHello` · `SUSFS` (§0.4)؛ ونستدعي
   **واجهة** مدير الجذر — وهو **استعمال أداة لا اقتباس كود**.
2. **لا تثبيت وحدات** — `AR-R2` **يبقى كما هو**؛ نكشف الناقص ونُرشد، والتثبيت بيد المستخدم.
3. **كاتب واحد + journal + تحقّق بالقراءة** (انضباط `HardwareControlArbiter`) بمفتاح لكل تطبيق.
4. **لا وعد بأن بنكًا سيعمل** (ADR-07): الحكم يُقاس على جهاز المستخدم وتطبيقه.
5. **`DenyList` وحدها ليست إخفاءً** بنصّ مشرف Magisk نفسه (`#7418`)، وتعارُض `Enforce DenyList` مع
   `Shamiko`/`Zygisk-Assistant` **يُعلن في الواجهة** قبل أيّ علامة.

**النتيجة:** `AR-R1` **معدَّل لا محذوف**؛ والخطّة `docs/ai/ROOT-ISOLATION-PLAN.md`؛ ونطاق ملفّي
`HMA-INTEGRATION-PLAN.md` (§19) و`APP-LIST-HIDING-PLAN.md` (القرار ٢) **يُصحَّح بالإحالة إليه** فلا يُقرأ
أحدهما مناقضًا للآخر.

## ADR-57 — **المحرّك الصوتيّ: الشامل أساسًا وDolby اكتشافًا — والمستحيل بالـAPI العامّ لا يُوعد** (2026-10-01)

**الأمر:** «أنشئ `Audio Engine` لا يعتمد على Dolby أصلًا، ويستخدم **أفضل `Audio Backend` حقيقيّ متاح على**
الجهاز… ويتمّ اختيار الـBackend بناءً على **capability discovery + verification** وليس اسم الشركة المصنّعة»،
و«اجعل MaxManager يبحث **تلقائيًّا** عن Dolby… وإذا تمّ اكتشافه لكنّه غير قابل للوصول فيجب تسجيل
**`DETECTED_BUT_UNAVAILABLE`** بدل إظهار أنّه يعمل». وعرض المالك ثلاثة خيارات — (أ) Dolby كأساس · (ب) محرّك
شامل مستقلّ · (ج) هجين — وطلب تنفيذًا حقيقيًّا يشمل discovery · abstraction · adapter · runtime selection ·
verification · rollback · graceful fallback · حالة واجهة صادقة · اختبارات.

**القياس الذي حسم القرار (لا تفضيل):** على **نفس** `android.jar` الذي يُصرَّف عليه المشروع (`compileSdk 37`)
بـ`javap`، ومقابَلًا بمصدر AOSP (`frameworks/base`، فرع `main`):

| الواجهة | في سطح SDK العامّ؟ | الموضع |
| --- | --- | --- |
| `AudioEffect.queryEffects()` | **نعم** (`public static Descriptor[]`) | `javap` |
| `AudioEffect.Descriptor.uuid` | **نعم** (`public UUID uuid`) — معرّف **التنفيذ** | `javap` |
| `AudioEffect.EFFECT_TYPE_NULL` | **لا** (`@hide` · `ec7178ec-e5e1-4432-a3f4-4657e6795210`) | AOSP سطر ١٦٣ |
| `AudioEffect(UUID,UUID,int,int)` | **لا** (`@hide`) | AOSP سطر ٤٧٢ |
| `setParameter(byte[],byte[])` / `getParameter(byte[],byte[])` | **لا** (`@hide`) | AOSP ٧٣٨ / ٨٤٨ |

⇒ **الـSDK العامّ «يرى» مؤثّر المصنّع ولا «يلمسه».** فـ(أ) كأساس تعني وعدًا لا يُوفى على أكثر الأجهزة (الشاشة
كلها تصير «Dolby موجود ولا يعمل»)، و(ب) وحدها تُسقط ما هو حقيقيّ على أجهزة تُعلنه.

**القرار:** **(ج) هجين** — المحرّك الشامل (`AudioBackendLadder`: `VENDOR_EFFECT` → `PLATFORM_DYNAMICS` →
`PLATFORM_EQUALIZER` → `PLATFORM_SIMPLE` → `SYSTEM_LAYER`) هو **الطبقة الأساس**، وDolby **اكتشافٌ اختياريّ**
يُقاس في اللحظة ويُعرض بحالته، **ولا يقود إلّا إن ثبت أنّه قابل للقيادة**. والترتيب **بالقدرة لا بالمصنّع**.

1. **الحالة قياسٌ لا أمنيّة.** `AVAILABLE` = «قِيست وأمكن قيادتها»؛ `NEEDS_ADAPTER` = «حقيقيّة وتحتاج مسارًا»؛
   `DETECTED_BUT_UNAVAILABLE` = «موجودة ولا مسار» (والمطلوب صراحةً)؛ و**ثلاثيّات لا ثنائيّات** (ADR-07): قائمة
   مؤثّرات غير مقروءة ⇒ `UNKNOWN` ولا تُقرأ «غير موجود».
2. **الاختيار لا يرقّي الحالة** — الحالة مقياسٌ ثابت، و`primary` قرارٌ، فلا يُخلطان في حقل واحد، والبدائل تُقال.
3. **صفر كود من التطبيقات المرجعيّة (§0.4/ADR-55):** `RootlessJamesDSP`/`wecho` **GPL-3.0** ⇒ لا سطر منها؛
   وما نُقل هو **معرّف مؤثّر Dolby DAP** وحده من `DolbyUI` (**Apache-2.0** بنصّ ترويساتها) بترويسة نسبةٍ
   إنجليزية **أولى** في أوّل الملفّ (يشترطها `license_audit.py`، وإلّا صُنّف `DERIVED_UNDER_PROPRIETARY_HEADER`).
4. **لا رِفادة `SoftwareDSPBackend`** — وهذا **منعُ ادّعاء لا رفضُ فكرة**: هذا تطبيق تحكّم لا مشغّل وسائط، ومن
   يعالج الصوت داخل عمليّته يحتاج أن **يملك مسار الصوت** (التقاط `AudioPlaybackCapture`/`MediaProjection`) —
   مرفوضٌ صراحةً في `AUDIO-ADVANCED-PLAN` §4. فالمسار الشرعيّ الوحيد لمحرّك DSP حقيقيّ = **طبقة نظاميّة**
   (`AQ-09`/`AS-06`) = رِفادة `SYSTEM_LAYER` في السلّم، مسمّاةً لا موعودة.
5. **كل كتابة عبر المحكِّم** (`HardwareControlArbiter`) بمفتاح `audio_vendor:<effect>` (ADR-11)، وقراءةٌ بعد كل
   كتابة، و**لا مقبض قبل جلسة نملكها** (زرٌّ يعرف أنّه سيفشل أسوأ من زرٍّ غائب)، وتحريرٌ في اللحظة نفسها.
6. **تفريق `NO_BACKEND_AVAILABLE` عن `NO_BACKEND_MEASURED`** — «لا محرّك» يُقال فقط بعد قياسٍ فعليّ.
7. **الاختيار وقت التشغيل يُربط بالمقابض ولا يُعزل عنها:** `AudioBackendRole` (`PRIMARY`/`FALLBACK`/`OTHER`)
   يُحسب من الاختيار نفسه، ويُقال في تبويب المحرّك وفي قسم مؤثّر المصنّع — فما يُقاس هو ما يُعرض موضعه.
8. **الاكتشاف من مصدرين لا من واحد:** `AudioEffect.queryEffects()` تقول ما **حمّلته** المنصّة، ووثيقة
   `audio_effects.xml` تقول **أين** وبأيّ مكتبة. و**مُعرَّفٌ في التهيئة ولم يُحمَّل ⇒ `DETECTED_BUT_UNAVAILABLE`**
   (لا «غير موجود» ولا «يعمل») — **والجردة تُقدَّم على التهيئة**، فلا يُرقّى تعريفٌ إلى تحكّم بلا قياسٍ حقيقيّ.

**ولا تعارض مع تكملة ٢٣٠:** قولها «لا كشفَ DAP صُنع» معناه **لا حزمة Dolby نُقلت ولا كشفٌ بامتياز نظام**؛ وهذه
الموجة تضيف **اكتشافًا بالـSDK العامّ وحده** + بروتوكول DAP **نموذجًا صافيًا** — فالتصريح يُحدَّد لا يُنقض.

**وسندها المقيس:** `OK (163 tests)` (٣٦ ملفًّا · +٤٩ اختبارًا) · **٤٩٧ ملفًّا · ٢٩٩٠ صنفًا · `total errors: 0`**
(ترجمة أندرويد محلّية) · **`ran=32 FAIL=0`** لأوامر «Contract gates» محلّيًّا · **٨٤ مفتاحًا** في `values/`
و`values-ar/` وحدهما (§0.2) متطابقةً و`--prune` = صفر يتيم · و`license_audit --assert` ⇒ exit 0.
**وحدّه المعلن:** هل تُقبل جلسة ٠ · وهل تسمح سياسة الـAPI المخفيّة بالانعكاس · وهل يمنع محرّكٌ آخر ملكيّة المؤثّر ·
وهل يقبل `audioserver` الطبقة النظاميّة بعد الإقلاع — **يحتاج جهازًا**، ولا يُدَّعى خلاف ذلك.

## ADR-58 — **MaxFx: محرّكٌ صوتيٌّ مملوك كمؤثّرٍ نظاميّ، يُقاد بخاصيّاتٍ لا بـAPI مخفيّ** (2026-10-01)

**السبب:** أمر المالك: «إذا كان الأمر شبه مستحيل… فقط تنقّب في ١٠٠ مشروع مفتوح المصدر لنحقق فلسفة
التطبيق عن خيارات متقدّمة للصوت وتحسينات عميقة… مميزات صوت رائعة و**تعمل بشكل عام مهما كان** مثل
ViPER4Android-RE-Fork». والقياس على جهاز المالك يقول إنّ الطريق القائم (الـAPI المخفيّ + مؤثّرات المصنّع)
**لا يعمَّم**: `hidden-api-blocked-or-absent` على ROMه، وDolby = `DETECTED_BUT_UNAVAILABLE`. والطريق الذي
«يعمل بشكل عام» هو مؤثّرٌ **نظاميٌّ مملوك لنا** يحمّله `audioserver` على أيّ جهاز — هذا هو ما يفعله ViPER.

**القرار:** محرّك **MaxFx** في `maxfx/` — مؤثّرٌ نظاميٌّ (`libmaxfx.so`) يُثبَّت بطبقة `AQ-09`، ونواة DSP
نقّية مملوكة، وقِياسُه على المضيف قبل أيّ جهاز. وتسع قواعد:

1. **صفر كود من ViPER4Android-RE (`iscle/ViPER4Android-RE` = GPL-3.0)** (ADR-55) — الفكرة تُنسب والكود
   يُكتب ملكيًّا سطرًا سطرًا؛ ولا يُنقل من GPL/AGPL حرفٌ واحد. والمنهجيّات (فواصل النطاقات · ضاغط ·
   محدّد · tube) مفاهيمُ عامّة لا كود.
2. **الموقع: مؤثّرٌ نظاميّ يحمّله `audioserver`** — لا تطبيقٌ يعالج داخل عمليّته (وذلك يحتاج ملكيّة مسار
   الصوت = مرفوضٌ سلفًا). والوحدة (`AudioSystemModuleModel`) تضع المكتبة في `system/lib64/soundfx/`
   والتهرية في المسار الذي يقرأه الجهاز بالاسمين (`audio_effects.xml`/`audio_effects_config.xml`).
3. **قناة التحكّم: خاصيّات النظام** (`persist.audio.maxfx.<key>`) — لأنّ `AudioEffect.setParameter`
   والبانيّ **`@hide` ومحجوبٌ مقيسًا**، والخاصية يقرأها المؤثّر بلا صلاحيّة وبلا سياقٍ خاصّ **على أيّ ROM**.
   والقراءة دوريّة (~250ms من `process`) — تأخيرٌ مُعلن لضبطٍ لا يطلب سرعة؛ و`EFFECT_CMD_SET_PARAM`
   مطبَّقٌ أيضًا لمن يملك المقبض. والغياب **ليس صفرًا** (ADR-07) على الطرفين.
4. **العقد مصدرٌ واحد لا يُعاد:** `fixtures/contracts/maxfx_{params,identity}.tsv` يُقرأ من **طرفَي القياس**
   (`maxfx/tests/dsp_test.c` و`MaxFxModelTest.kt`) ويُقارَن بالنموذجين — فلا رقمَ مكتوبٌ من الذاكرة،
   و`id` المعاملات ثابتة لا تُعاد استخدامها بعد الحذف (المُستهلك على الجهاز قد يكون أقدم من الجدول).
5. **النواة نقّية ومضبوطة على المضيف:** `maxfx_dsp` بلا أندرويد وبلا عقد مؤثّرات، وتُقاس بأدّعاءاتٍ رقميّة
   قابلة للتكذيب **وطفراتٍ يجب أن تُسقطها**؛ وحالةُ كلّ قناةٍ مستقلّة (عطبٌ أمسكه القياس: حالةٌ مشتركة
   بين القنوات أمالت استجابة المعادل +1.95dB بدل +6dB)، والدخول المُشوَّه (NaN/Inf/ضخامة) يُخرج محدودًا
   سليمًا، و`process()` بلا `malloc`/`lock`/`I/O` (زمنٌ حقيقيّ).
6. **رؤوس ABI من AOSP بنسبة الفضل** (Apache-2.0 — ADR-55): `maxfx_effect_abi.h` مُجزَّأ حرفيًّا من رؤوس
   `audio_effect` الثلاثة لأنّه **عقدٌ ثنائيّ** مع `audioserver` — قيمةٌ مُغيَّرة = مكتبةٌ لا تُحمَّل أو ذاكرةٌ
   تُفسد؛ والإشعار في الترويسة + صفّ AOSP في `THIRD_PARTY_NOTICES.md`.
7. **الهويّة مقيسة لا مُنتقاة:** uuid النوع/التنفيذ والاسم والمكتبة وبادئة الخاصيّات كلّها في
   `maxfx_identity.tsv` — وتعديلُ هويّةٍ بعد تثبيت الوحدة يعني مؤثّرًا قديمًا لا يُربط بمصدره، والعقد يمنع
   ذلك بصمت (اختبارٌ يقرأه ويقارن، والقارئ يُسقط الصفوف الفائتة بلا سكوت).
8. **التركيب بالأداة الوحيدة:** `MaxFxModel.installAddition()` يولّد سطر الإضافة الذي يقبله محلّل الشاشة
   ويكتبه مولّد الوحدة — والتهريةُ في التهيئة هي **اسم الملفّ مجرّدًا** (صيغة AOSP) فيبحث عنه الحمّال في
   مجلّدات `soundfx`، والمعرّفُ يُشتقّ من اسم الملفّ (`libmaxfx.so` ⇐ `maxfx`).
9. **الحدّ المُعلن:** ما مُثبَت هنا أنّ العقد سليمٌ والمعالجة قياسها صحيح **على المضيف** — وأمّا «الصوت
   يتغيّر فعلًا على الهاتف» ف**يحتاج جهازًا**، ولا يُدَّعى قبل قياسٍ فيه. وبناء `libmaxfx.so` يحتاج NDK
   غير متوفّر محليًّا — فالتراجمة «غير مُتحقَّقة في هذه البيئة» لا «مُتحقَّقة».

**وسندها المقيس:** `maxfx/tests` = **246 ناجحة · 0 فاشلة** + **ثلاث طفراتٍ سقطت المجموعة كلّها**
(`self-check`) · `MaxFxModelTest` = 5 اختبارات · **`OK (171 tests)`** (١٦ ملفًّا + الجديد) · **499 ملفًا ·
2995 صنفًا · `total errors: 0`** · **`ran=32 FAIL=0`** · `license_audit --assert` exit 0 بعد صفّ AOSP المُحدَّث.

## ADR-59 — **`audio_maxfx:`: مقبضُ الخاصيّة مفتاحًا مُفرَّقًا، وكل كتابةٍ تُقرأ بعدها** (2026-10-02)

**السبب:** ربطُ MaxFx بالواجهة (تكملة ٢٣٥) أوجَب حسمَ ما كان مفتوحًا في تكملة ٢٣٤: أيّ مفتاحٍ لمحكِّم
يحمل معاملات MaxFx؟ بين `audio_vendor:<uuid>:<param>` (مفتاح مؤثّرٍ يُفتح بجلسة) ومفتاحٍ جديد. وثوابت
المقابض في `HardwareControlKey` تقول: **الاختلاف في الوصول هو ما يجب أن يُفرّق بين المقابض في السجلّ**
— فشلُ كتابةِ خاصيةٍ ليس فشلَ جلسةٍ، والعكس، وعلاجهما مختلف — وهي القاعدة نفسها التي فصّلت قديمًا
`audio_vendor:` عن `audio_effect:`.

**القرار:** مفتاحٌ رابع `audio_maxfx:<param>` + تحكّمٌ بالخاصيّات عبر المحكِّم، وسبع قواعد:

1. **المفتاح يُبنى في `HardwareControlKey` وحده** — والوسيط هو **مفتاح العقد نفسه** (`maxfx_params.tsv`)
   فلا تسميةٌ ثانية لاسمٍ واحد؛ وبوّابة `canonicalKeysAreNotReinvented` تحرس الموضع الواحد.
2. **كل كتابة تمرّ بالمحكِّم** (`Owner.GLOBAL_PROFILE`) بـ`apply = PropertyUtils.setAndConfirm`، وكلّ
   `apply` يقابله `read` من المنصّة نفسها و`restore` بالقيمة القديمة (ADR-11) — ولا كتابةٌ من `ui`.
3. **خطّ الأساس للخاصية غير المكتوبة فراغٌ مكتوب لا `null`:** المحكِّم لا يبني خطّ أساس من غير قراءة
   (`baseline-unreadable`)، والفراغ يُسترجع فراغًا فيعود افتراض العقد (`maxfx_config_default`).
   **والغياب ليس صفرًا** (ADR-07): ما يُعرض لغير المكتوب هو افتراض العقد موسومًا «افتراضي».
4. **الصياغة من العقد وحده** (`MaxFxModel.propValue`: اقتصاصٌ عند الحدود + تقريبُ عرضٍ للمنزلق) — فما
   يُعرض حرفيًّا هو ما يُكتب في الخاصية بالضبط، ولا صياغةٌ ثانية في الشاشة.
5. **القسم يُبنى من `MaxFxModel.params`** فلا جدول معاملاتٍ ثانٍ في الواجهة؛ ومعاملٌ بلا تسميةٍ معلومة
   **يُعرض باسمه في العقد** لا يُلحق بأقرب شبيه فيُقرأ باسمٍ ليس له.
6. **التثبيت فعلٌ واحد فنصّه واحد:** زرٌّ يولّد سطر الإضافة من `MaxFxModel.installAddition()` وينادي
   الطبقة النظاميّة (`AQ-09`) — **بنصّ التحذير ونافذة التأكيد نفسهما**، لا نسخةٌ ثانية تفترق مع الوقت.
7. **`libmaxfx.so` تُبنى في CI للعمودين ويُقاس رمز `AELI` على الثنائية** — ومكتبةٌ بلا رمزٍ مُصدَّر
   تُبنى «ناجحةً» ثم لا تُحمَّل أبدًا؛ و`maxfx/**` دخل مسارات التفعيل فتعديلٌ فيها يُشغّل القياس.

**وسندها المقيس:** ترجمة **502 ملفًا · 3005 صنفًا · `total errors: 0`** · `OK (171 tests)` ·
`maxfx/tests` **246 ناجحة · 0 فاشلة** + ثلاث طفراتٍ سقطت · **`ran=34 FAIL=0`** · `aapt2` على النصوص
الجديدة · `source_manifest --write` بعد الإضافة. **وأمّا «الصوت يتغيّر فعلًا» فكما كان: يحتاج جهازًا**
— والخاصية تصل المؤثّر خلال ~250ms (مُعلن من تكملة ٢٣٤)، وقبولُ `audioserver` للمؤثّر وسماعُ الفرق
يبقيان خارج ما يُقاس هنا.

---

## ADR-60 — **عقد الطبقة النظاميّة صار AIDL، و`type` على `<effect>` شرطُ تحميلٍ لا زينة** (2026-10-02)

**السبب — أمر المالك ثمّ قياسٌ نقض الافتراض:** أمر المالك: «اخبرتك أن تستسلم وتصنع شيئًا مشابهًا
لـ`AndroidAudioMods/ViPER4Android` وغيرها من المشاريع المشابهة» + مراجع AIDL الرسميّة. وكان الافتراض
السائد أنّ V4A «حلٌّ مثبتٌ يعمل على الأجهزة الحديثة» فيكفي تقليده. **والقياس نقض الافتراض من طرفيه:**

1. **الرخصة:** `AndroidAudioMods/ViPER4Android` **ليس فيه كود** («*The ViPER4Android apk source code is
   currently not open source*»)، والمصدر المفتوح `ViPERFX_RE` `module/LICENSE` = **GPL-3.0** (18092
   بايت) و`src/` بلا رخصة ⇒ **صفر كود** (ADR-55)، معماريّةٌ فقط.
2. **العقد المشحون فعلًا:** نُزّلت الوحدة الرسميّة `V4A_Magisk_Module_0.6.1.zip` وقِيست ثنائيّتها:
   `readelf --dyn-syms libv4a_re_arm64-v8a.so` ⇒ **`AELI` كائنٌ مُصدَّر (48 بايت)** و**صفر** من
   `createEffect`/`queryEffect`/`destroyEffect`؛ و`NEEDED` = `liblog`/`libm`/`libdl`/`libc` فقط.
   **أي أنّ `maxfx` عندنا ليس شاذًّا: هو العقد نفسه الذي يُشحنه V4A.**
3. **ومصنع AIDL لا يقرأه:** `EffectFactory.cpp` (AOSP `audio/aidl/default`، ٣٠٠ سطر، مقروء) يبني واجهته
   بـ`dlsym(h,"createEffect")`/`"queryEffect"`/`"destroyEffect"` حصرًا، وبغيابها يكتب
   `… not exist in library` — و**لا مسار `legacy`/`AELI` في المصنع ولا في `EffectConfig`/`EffectImpl`/
   `EffectThread`** (فحص الأربعة: صفر مطابقة لـ`aeli`/`AUDIO_EFFECT_LIBRARY`). ودليله الاجتماعيّ: تغيير
   LineageOS **مُدمَج 2026-09-05** عنوانه «*Add an AIDL wrapper for legacy effect libraries*» — أي أنّ
   دعم `AELI` **يُضاف** لأنّه غير موجود. **⇒ وحدة V4A المشحونة لا تُحمَّل على جهاز المالك أيضًا.**
4. **وجهاز المالك AIDL خالص (من لوقه):** كلّ مكتبةٍ تُفتح فعلًا `*aidl.so`؛ والمكتبات القديمة
   (`libbassboostsw.so` · `libequalizersw.so` · `libvolumesw.so` · …) تظهر في `parseLibrary`
   **ولا تُفتح بـ`openEffectLibrary` إطلاقًا**، و**`not exist in library` = 0 مرّة**.
5. **والسبب الأخير مقروء في المصدر:** `EffectConfig::parseLibrary` (سطرا ١٩٨ و٣٤١) يقرأ سمة **`type`**
   **من عنصر `<effect>`** ويخزّنها في `library.type`، و`findUuid` **يُعيد `false` بغيابها** ⇒
   `Factory::loadEffectLibs` يطبع `skipping` ولا يُنشئ هويّةً ولا يفتح المكتبة. **وهو تفسير ما في البند
   ٤ بالضبط** — مفعولات `*sw` في التهيئة بلا `type`، فلم تُحمَّل ولم تُفتح.

**القرار — أربعة بنود:**

1. **عقد الطبقة النظاميّة = عقد AIDL** (`createEffect`/`queryEffect`/`destroyEffect`، توقيعها المقيس في
   `EffectTypes.h`: `binder_exception_t (const AudioUuid*, std::shared_ptr<IEffect>*)` و…).
   ويبقى `AELI` **مبنيًّا كصيغةٍ بديلة** للأجهزة ذات الـHAL القديم (HIDL) — لا يُحذف ما يعمل على غيره.
   **والبناء يحتاج ترويسات AIDL من AOSP** (‏`aidl --lang=ndk` على `aidl_api/android.hardware.audio.effect`
   + `android.media.audio.common` + `android.hardware.common.fmq`) — وهي **Apache-2.0** فتُستعمل بنسبة
   الفضل لا تُنقل من GPL (ADR-55). وقياس البناء في فرع V4A غير المشحون
   (`Android.bp` · `BnEffect` · `EffectThread` · `android.hardware.audio.effect-V{1,2,3}-ndk`) — **يُقرأ
   كخريطةٍ لا يُنقل ككودّ.**
2. **`type` في `<effect>` شرطُ تحميل:** يُكتب بقيمة **`type_uuid` من عقد الهويّة نفسه** — وهي **مطابقةٌ
   قائمةٌ أصلًا**: `maxfx_descriptor()` تضبط `out->type = MAXFX_TYPE_UUID`، فالتهيئة والوصف يُعلنان
   النوع نفسه، واختلافهما كان سيجعل المؤثّر غير قابلٍ للربط. **وسطر الإضافة في الواجهة يحمله الآن
   حقلًا سادسًا** (`…|uuid|الأجهزة|النوع`) — في الذيل لا في الوسط، فسطرٌ قديم بخمسة حقول لا يتغيّر معناه.
3. **وما لا يُصلَح صامتًا يُرفض معلنًا:** إضافةٌ إلى مؤثّرٍ **قائم بلا `type`** تُرفض بـ`addition-effect-conflict`
   — لأنّ الطبقة **تُلحق ولا تُعدّل** عقدةً كتبها غيرنا (ADR-18)، فلا سبيل إلى إسناد سمةٍ لعنصرٍ قائم.
4. **و`libmaxfx.so` تُشحن** (الفجوة المقيسة: مولّد الوحدة يُخرج أربعة ملفّات نصّيّة ولا ينسخ المكتبة
   إطلاقًا — فلا وجود لمسار `cp` كما في وحدة V4A) — وتُوضع حيث يقرؤها الـHAL، ويبقى `path` في التهيئة
   **اسمَ الملفّ مجرّدًا** فيبحث عنه الحمّال في مجلّدات `soundfx`.

**وسنده المقيس (كلّه في هذه الجلسة):** `readelf` على ثنائيّة V4A · `EffectFactory.cpp` و`EffectConfig.cpp`
و`EffectTypes.h` و`EffectImpl.cpp` و`EffectThread.cpp` من googlesource (`?format=TEXT`) · `parseLibrary`
و`openEffectLibrary` و`not exist in library` من **لوق جهاز المالك** · وتغيير LineageOS 501218 عبر
`/changes/501218/detail` (`status: MERGED`) · ثمّ تعديل الكود: **`OK (177 tests)`** (كانت ١٧١ ⇒ +٦)
بعد ترجمة **38 ملفًا · 125 صنفًا · 0 خطأ** في `kverify-audio` · وستّ بوّابات: `kt_balance` · `code_health` ·
`i18n_coverage` · `--prune all` · `jni_symbols` · `resource_compile` — **كلّها PASS**.

**وحدوده المعلنة:** غلاف AIDL **غير مكتوب بعد** (لا تُبنى ترويسات AIDL في هذه البيئة) · و`libmaxfx.so` لم
تُنقل بعد إلى الوحدة · و**قبول `audioserver` للمؤثّر وسماعُ فرقٍ في الصوت يحتاجان جهازًا** (§0.1) —
فلا يُدَّعى صوتٌ لم يُسمَع.

## ADR-61 — إرفاق `DynamicsProcessing` **سلّمٌ يقيس نفسه**، لا محاولةٌ واحدة بهندسةٍ نفترضها

**الحالة:** ملزم · **السياق:** تكملة ٢٣٩ (تشخيص `effect-attach-refused` في سجلّ المالك).

**المشكلة المقيسة:** كان `AudioEffectBackend.createEffect` يُنشئ محرّك الديناميّ **مرّة واحدة** بهندسةٍ
**اخترعناها**: `Config.Builder(VARIANT_FAVOR_FREQUENCY_RESOLUTION, channels, true, 6, true, 4, true, 6, true)`
— أي أنّنا كنّا **نفرض هندسةً على عتادٍ لم نسأله عن هندسته**، وأيُّ فشلٍ في أيّ خطوةٍ يُطوى في رمزٍ واحد
`effect-attach-refused`. ونتيجةً لذلك: سجلّ المالك يقول `effect-attach-refused` **بلا تفصيل**، ولا محاولةَ
ثانية، فيبقى المحرّك — وهو موجودٌ في المنصّة أصلًا — **مُغلقًا على جهازه**.

**ومقيسٌ في مصدر AOSP:** `DynamicsProcessing(int session)` = `(0, session, null)` — أي **بلا `Config`**،
فتُبنى المحرّكة على **هندسة المنصّة نفسها**، ولا عددَ نطلب. وهذا هو الطريق الذي تسلكه المشاريع العاملة
(`allEQ` نمطًا)، ولم يكن في كودنا إطلاقًا.

**القرار:**

1. **السلّم مُعرَّفٌ ومرتَّبٌ في نواةٍ نقيّة** (`DYNAMICS_ATTACH_LADDER`): (١) **هندسة المنصّة** بلا `Config`،
   (٢) **دقّة التردّد** بأعدادٍ مقيسة إن وُجد قياس وإلا فطلبنا المُعلَن، (٣) **زمن الاستقرار**. والترتيب
   **ثابتٌ من التعريف لا من ترتيب وصول قياس**، فالشاشة والسجلّ يتّفقان.
2. **المقيس أولى من المطلوب** (`dynamicsBandCounts`): ما أعلنته المنصّة بعد إرفاقٍ ناجح يُبنى به في أيّ
   خطوةٍ تالية؛ وقياسٌ صفريّ **لا يُصدَّق** (يُستعمل المطلوب)، وعدد النطاقات لا يقلّ عن واحد.
3. **كل خطوة تُسمّى برمزها للتشخيص** (`dynamicsAttachRefusal`) وتُسجَّل في `audioOp` بـ`gated=false`،
   **والحكم المعروض واحدٌ صريح**: `effect-attach-refused-after-all-steps` — فلا يُظنّ أنّ محاولةً تُركت،
   ولا يُخلط تشخيصٌ بحكم (وهو نفس درس `AudioCapabilitySection`: النفي يُعلن عن شجرتنا لا عن الجهاز).
4. **لا قراءةَ من `Config` لما لا تُعلنه:** مقيسٌ بـ`javap` أنّ `DynamicsProcessing.Config` تُعلن أربعة
   قارئات فقط (`getVariant` · `getPreEqBandCount` · `getMbcBandCount` · `getPostEqBandCount`) **ولا
   `getChannelCount`**؛ فعدد القنوات يُمرَّر من الخطة (وهي مصدره المقيس من `AudioManager`)، لا من `config`.

**الأثر:** صار الفشل **يُشخَّص من السجلّ وحده**، وصار الإرفاق **يحاول ما تعرفه المنصّة قبل ما نظنّه**.
**وحدّه المُعلَن:** هل يُقبل الإرفاق فعلًا على جهازٍ ما — **يحتاج جهازًا** (§0.1)؛ ولا صوتَ موعود قبل سماع فرق.

## ADR-62 — **مكتبة المؤثّر تُنسخ إلى مجلّد مكتبات المنصّة**، ومساراتها وسمُها وصلاحيّتها تُقاس

**الحالة:** ملزم · **السياق:** تكملة ٢٤٠ (الفجوة المقيسة: «صفر سطرٍ ينسخ مكتبة»).

**المقيس الذي أنتج القرار — ثلاثة مصادر لا رأي:**

1. `EffectConfig::parseLibrary` (AOSP، مقروء في هذه الجولة): يقرأ **سمة `path` من `<library>`** ثمّ
   `resolveLibrary` يبني `directory + '/' + path` ويختبر `access()` على كل مجلّد في `kEffectLibPath`
   (وقبلها `apex/<vendor>/…`). ⇒ **موضع المكتبة ليس موضع ملفّ التهيئة**، وقد يقرأ الأخير من `/odm/etc`
   والمكتبة من `/vendor/lib64` — ولا نجاحَ إن لم تكن المكتبة في مجلّد يبحث فيه المصنع.
2. ملفّ `audio_effects_config.xml` المرجعيّ في AOSP: `<library name="reverb" path="libreverbaidl.so"/>`
   — **`path` اسمُ ملفّ مجرَّد** لا مسار. (وهو ما يفسّر `…soundfx//lib<name>.so` في اللوق: الشرطة
   المزدوجة من `directory + '/' + path`.) ويُضاف: **سمة `type` اختياريّة على `<effect>`** «*can be used
   to add any customized effect type*» — وهو تأكيدٌ صريح لإصلاح تكملة ٢٣٧.
3. لوق جهاز المالك: `parseLibrary <name> : /vendor/lib64/soundfx//lib<name>.so` — المسار المحلول
   **بالحرف**، فالمجلّد على عتاده معلوم لا مُخمَّن.

**القرار:**

1. **خطّة مكتبة نقيّة** ([`audioEffectLibraryPlan`]): تُبنى من (مجلّد مكتبات التطبيق · العمود · اسم
   الملفّ) وتُخرج المصدر، ومسار الجهاز (`/vendor/lib64|lib/soundfx/<name>.so`)، والمسار داخل الوحدة
   (`system/vendor/...`)، والوسم، والصلاحية. وتُرجع `null` بدل مسارٍ مظنون (عمود مجهول · اسمٌ فيه `/` ·
   مسارٌ لا يُركَّب). والعمود يحدّد `lib64` أو `lib` — فلا تُنسخ مكتبة ٦٤-بت إلى مجلّد ٣٢-بت.
2. **وسمٌ مغايرٌ لوسم التهيئة:** `/vendor/lib64/…` تُوسم `vendor_file`، و`/vendor/etc/…` تُوسم
   `vendor_configs_file`. وخلطُهما يمنع التحميل بـ`avc denied`. **وتقديرٌ مُعلَن** كأخيه (§0.1).
3. **نفس عطب الصلاحية بنفس الأثر:** المكتبة تُنسخ بصلاحية **٠٦٤٤** — و٠٦٠٠ `atomicWrite` تُركّب ولا
   يقرؤها المصنع. و`post-fs-data.sh` يُصلح الملكيّة والصلاحية والوسم عند كل إقلاع (كتلةٌ **لا تُكتب
   أصلًا** بلا مكتبة، فلا سطر ميّت يوهم بقارئ).
4. **النسخ داخل معاملة المحكِّم** لا بسكربت: ذرّيٌّ (`cp` لمسارٍ مؤقّت ثمّ `mv`)، **وفشله يُسقط الكتابة
   كلها فيُحذف المجلّد** (الرجوع التامّ). و**لا `cp` في سكربت Magisk** أبدًا.
5. **ثلاث رفضات مُعلَنة بدل «نجحتْ»:** `effect-library-not-shipped-in-app` (لا شيء يُنسخ) ·
   `effect-library-not-installed` (الطبقة مكتوبة والمكتبة غائبة) · `effect-library-not-world-readable`.
   والحكم يُخفض إليها **بعد قياس الوجود ثمّ الصلاحية**، **ولا يُخفض بجهلٍ** (تعذّر قراءة الصلاحية
   يُبقي الحكم — فالجهل ليس نفيًا).

**الأثر:** صار للمكتبة مسارٌ ووسمٌ وصلاحيةٌ وخطوةُ نسخ، وصار غيابُها **يُقال** لا يُخفي. **وحدّه المُعلَن:**
(أ) **لا يُقاد الآن من الشحنة الحقيقيّة:** `libmaxfx.so` ليس داخل الـAPK بعد (لا `jniLibs`)، ولو أُدخل
فإنّ `extractNativeLibs=false` يجعل `nativeLibraryDir` مسارًا داخل الحزمة لا مجلّدًا حقيقيًّا — فالشحنة
تحتاج خطوةً مكتوبة (استخراج المدخل `lib/<abi>/<name>.so` من حزمة التطبيق إلى ملفّ مؤقّت يقرؤه الجذر، أو
`android:extractNativeLibs="true"`). (ب) **هل يجد المصنع المكتبة ويُحمّلها فعلًا: يحتاج جهازًا** (§0.1)،
ولا صوتَ موعود قبل سماع فرق.

## ADR-63 — **المكتبة تُخرَج من حزمة التطبيق** (`nativeLibraryDir` ليس مجلّدًا)، وتُشحَن في الحزمة مع بوابة تُقيس المدخل

**الحالة:** ملزم · **السياق:** تكملة ٢٤١ (استكمال ADR-62؛ الفجوة: `libmaxfx.so` لم يكن مشحونًا أصلًا).

**المشكلة المقيسة:** مسار `audioEffectLibraryPlan` في ADR-62 كان يُبنى من
`applicationInfo.nativeLibraryDir` — **وهو ليس مجلّدًا حقيقيًّا** على الأجهزة الحديثة: `extractNativeLibs`
يساوي `false` افتراضيًّا، فتبقى المكتبات **داخل الحزمة** (`base.apk!/lib/…`) ولا تُفكّ إلى قرص.
فـ`File(nativeLibraryDir + "/libmaxfx.so").exists()` = `false`، و`cp` يفشل، **فتُثبَّت الطبقة بلا مكتبة**
— وهو «صفر تغيير» في لوق المالك بسببٍ **رابع** لم يكن معروفًا حين كُتب ADR-62.

**القرار:**

1. **الإخراج من الحزمة لا القراءة من مجلّد:** [`AudioEffectLibraryStaging`] تقرأ الحزمة بـ`ZipFile`،
   وتستخرج المدخل `lib/<abi>/<اسم>.so` (وهو عُرف AGP، لا تخمين) إلى ملفٍّ في مجلّد التطبيق.
   **ويعمل في الحالتين** (`extractNativeLibs` صحيحًا أو خاطئًا) **وبلا تغيير في بيان التطبيق** — فلا
   نطلب إعدادًا يُنقص الأداء (فكّ كلّ المكتبات عند التثبيت) من أجل ملفّ واحد.
2. **اسم المدخل يُحسب حصرًا:** لا بحثٌ جزئيّ ولا أوّل مدخل شبيه — **مكتبةٌ أخرى في الحزمة ليست
   مكتبتنا**. ومدخلٌ فارغ (`size == 0`) ليس مكتبة؛ وملفٌ ليس حزمةً يعود `null` لا استثناء.
3. **الكتابة ذرّيّة والترتيب محسوب:** استخراج إلى `<name>.tmp` ثمّ `renameTo` — فلا يظهر ملفّ نصف
   مكتوب قطّ، ودورةٌ قُوطعت لا تترك «مكتبة» مبتورة. **والصلاحية 0600 مقصودة:** الملفّ في مجلّد التطبيق
   الخاصّ، **والجذر يقرأ ما لا يقرؤه غيره** (`su` بـ`uid 0`).
4. **الترتيب: الحزمة أوّلًا ثمّ المجلّد المفكوك** احتياطًا (أجهزة `extractNativeLibs=true`)، ومن
   يُرجع `null` يعني **«المكتبة غير مشحونة»** — وهي حالةٌ **تُقال** (`effect-library-not-shipped-in-app`)
   ولا تُتجاهل بتثبيت طبقةٍ بلا مكتبة.
5. **والشحن في الحزمة:** `sourceSets["main"].jniLibs.srcDir(maxfx/libs)` **بشرط وجود المجلّد وقت
   التهيئة** — فالبناء المحليّ بلا `ndk-build` لا يُدخل مصدرًا غائبًا، والـCI يضيفه دائمًا (يُبني بـ
   `ndk-build` في **المهمّة نفسها** قبل Gradle).
6. **وبوابة CI تقيس المدخل:** حرس «Validate Manager APK» صار يشترط `lib/<abi>/libmaxfx.so` للعمودين
   مع `libmaxmanager_native.so` — **فمكتبةٌ خارج الحزمة = نسخٌ يفشل = زرّ يُرفض**، وهو عطبٌ كان صامتًا
   في كل الحالات السابقة.

**الأثر:** صارت السلسلة كاملة: مدخلٌ في الحزمة ⇐ إخراجٌ مقيس ⇐ خطّةٌ بمسار البحث الصحيح ⇐ نسخٌ داخل
معاملة المحكِّم ⇐ صلاحيةٌ ووسمٌ مضبوطان. **وحدّه المُعلَن:** تغليف المكتبة وتقييم إعداد Gradle **لم
يُقيَما في هذه البيئة** (لا Android SDK ولا NDK ولا JDK 17 فيها) — والقياس الحقيقيّ للحزم في CI؛
**وقبول المصنع للمكتبة وسماع فرق: يحتاج جهازًا** (§0.1).

## ADR-64 — **حالة المكتبة تُحكَم في نواةٍ نقيّة** وتُعرض في صفّها، فلا يُقرأ «نجحتْ» عن مكتبةٍ ممنوعة

**الحالة:** ملزم · **السياق:** تكملة ٢٤٢ (إظهار السلسلة كاملة في الشاشة).

**المشكلة:** ADR-62/63 بنتا مسارًا ووسمًا وصلاحيةً ونسخًا ورفضًا صريحًا — **ولم يكن شيء من ذلك مرئيًّا**:
الشاشة تعرض حالة الطبقة (`installed` · `moduleWritable` · `sourcePath`) ولا تعرض **المكتبة** أصلًا.
فالمستخدم (والمالك) لا يفرّق بين «الطبقة رُكّبت ومكتبتها مقروءة» و«الطبقة رُكّبت ومكتبتها ٠٦٠٠ فلن
يقرأها المصنع» — **وهما في السجلّ سطران مختلفان وفي الشاشة صفر فرق**. وهذا بالضبط نمط «نجحتْ ولا يُسمع
فرق» الذي جئنا نُزيله.

**القرار:**

1. **خمس حالات مُعرَّفة في نواةٍ نقيّة** ([`AudioEffectLibraryState`] + [`audioEffectLibraryState`]):
   `NOT_SHIPPED` (لا مصدر معلَن ⇒ لا شيء يُنسخ) · `NOT_INSTALLED` (غائبة عن مسار البحث) · `READABLE`
   (٠٦٤٤ — الشرط الكامل) · `NOT_READABLE` (موجودة وغير مقروءة ⇒ يُطبع «can't find» والعلّة صلاحية) ·
   `UNMEASURED`. **والحكم في الطبقة النقيّة لا في `Compose`** — فشرطٌ مكتوب في الواجهة لا يُختبر أبدًا،
   وهذه هي حالات العطب نفسها.
2. **والترتيب محسوب:** غيابُ الخطّة أسبق (لا يُقال «غائبة» عن مكتبةٍ لم نشحنها) · ثمّ **جهلُ الوجود**
   (‏`installed == null` ⇒ `UNMEASURED` — **الجهل ليس نفيًا**، ADR-07) · ثمّ الغياب المقيس · ثمّ
   الصلاحية؛ و`mode == null` لا يُقرأ قراءةً ولا منعًا.
3. **والواجهة تُترجم ولا تحكم:** صفٌّ واحد يسمّي الحالة والمسار الذي يبحث فيه المصنع، **ووسمٌ لونيّ
   واحدٌ إيجابيّ** للحالة `READABLE` وحدها؛ وما عداها تحذيرٌ أو حياد **لا نجاح**.
4. **والمسار يُعرض في الحالتين** — القارئ يعرف **أين يُبحث** لا أنّ شيئًا «مفقود» فقط؛ وأوّل ما يُفعَل
   عند عطبٍ هو مقارنة هذا المسار بما يطبعه `EffectConfig` في اللوق.

**الأثر:** صار الفرق بين «الطبقة موجودة» و«المكتبة محمَّلة فعلًا» **مقروءًا على الشاشة**، ومعه المسار
والصلاحية. **وحدّه المُعلَن:** يُقاس هذا كله على الجهاز فقط (§0.1)؛ وما يُقاس هنا هو **الحكم** (خمس حالات
بترتيبها) والترجمة.

## ADR-65 — **حارس التحكّم يستمع ولا يستطلع فقط**، ويُفرّق «لا نملكه» عن «الجهاز عطّله»

**الحالة:** ملزم · **السياق:** تكملة ٢٤٣ (البند ٣ح-أ، معلَّق منذ تكملة ٢٣١).

**المشكلة المقيسة — وهي جوهر شكوى المالك «مهما غيّرت لا يوجد تغيير»:** كان `AudioEffectSession` يستطلع
`hasControl()` **عند الكتابة فقط**، ولا يستمع لشيء. فحالتان تُقرأان «مطبَّق» وليس فيهما أثر مسموع:

1. **فقد التحكّم بين الكتابتين:** تطبيق آخر يأخذ المؤثّر بعد أن فتحناه ⇒ المقابض المعروضة ميتة ولا شيء
   يُعلن ذلك حتى يلمس المستخدم مقبضًا.
2. **مؤثّرٌ نملكه والجهاز معطّله** (`onEnableStatusChange(enabled=false)`): الكتابة عليه **تُقبل وتُقرأ
   مطابقةً** (`expected == actual`) ثمّ **لا تُسمع** — «مطبَّق» و«صفر تغيير» في السطر نفسه، بلا سبب
   يُقال. وهذه أخطر من الأولى لأنّ كل قياس لدينا يؤكّد النجاح.

**والواجهة مقيسة بالـ`javap` قبل الكتابة:** `setControlStatusListener` و`setEnableStatusListener`
**عامّتان** (`public void`)، وكذلك `hasControl()` و`getEnabled()` — فلا حاجة لـAPI مخفيّة (ADR-07/AQ-02).

**القرار:**

1. **حالةٌ نقيّة** ([`AudioEffectControlState`]) في **ملفٍّ نقيّ** (`AudioKnobVerdict.kt`) لا في ملفّ
   الأندرويد — **فالانتقال هو ما يُختبر، والخيط في أندرويد لا.** وهي ثلاثة مدخلات معلَنة: `controlled` ·
   `enabledByEngine` (كلٌّ `null` قبل أن تُعلن المنصّة = «لم تُقَس»).
2. **والجلسة تستمع عند الفتح** (`init`) لكلا الحدثين — **وعدمُ قبول المستمع لا يُسقط الجلسة**: يُبقى
   `null` أي «لم تُبلَّغ»، ولا يُدّعى ملكٌ ولا نفي.
3. **والحراسة طبقتان في `knob()` لا واحدة:** ما **أُعلن** بين الكتابتين أوّلًا (`blockReason`)، ثمّ
   الاستطلاع الحيّ `hasControl()` (وهو المرجع في لحظة الكتابة). **بلا الثانية تُعرض مقابض ميتة.**
4. **وسببان متمايزان لا سببٌ واحد:** `control-not-owned-by-us` (لا نملكه) · `effect-disabled-by-engine`
   (نملكه والجهاز عطّله) — لكلٍّ نصّه في `values/`+`values-ar/`. **ولا يُقال للمستخدم سببان لعلّةٍ
   واحدة:** عند فقد التحكّم يُنسب الإبلاغ إليه (الأسبق) ولا يُذكر التعطيل.
5. **ومصدرُ الرمز واحد:** `disabledReason` مشتقٌّ من `disabledByEngine` نفسه لا من شرطٍ ثانٍ مكرّر —
   فحكمٌ يُقال للمستخدم لا يجوز أن يكون له مصدران ينحرفان.

**الأثر:** يُمتنع أن تُكتب قيمةٌ على مؤثّرٍ لا يُسمع أثرُها، ويُقال السبب **قبل** الكتابة لا بعدها.
**وحدّه المُعلَن:** التخفيض **اللحظيّ** على الشاشة بلا لمس مقبض يحتاج أن يُعيد الـ`ViewModel` قراءة
أسباب المحرّك من الجلسات عند كل قياس (سقف الملفّ المجمَّد يمنع إضافتها الآن) — فالانتقال يُكتشف ويُمنع
عند أوّل كتابة، **ويُقاس على جهاز** (§0.1).

## ADR-66 — **العقد المزدوج: خمسة رموز في مكتبة واحدة** (وعدمٌ لا يُصلحه إلا مكتبةٌ تصدّر الاثنين)

**الحالة:** ملزم · **السياق:** تكملة ٢٤٤ (البند ٣ي، وهو أكبر بند معلّق في الملفّ).

**المشكلة المقيسة — وهي السبب الجذريّ لشكوى «مهما غيّرت لا يوجد تغيير في الصوت»:**
مكتبتنا `libmaxfx.so` تُصدِّر رمزًا واحدًا: **`AELI`** (كائن `audio_effect_library_t`،
`maxfx/src/maxfx_effect.c:414`). وهذا هو عقد الجيل **HIDL** (أندرويد ≤11). وأما الجيل **AIDL**
(12+، وجهاز المالك Android 16) فمصنعُه `EffectFactory.cpp` لا يعرف `AELI` أصلًا: يُجري `dlsym`
على **ثلاثة أسماء حرفيّة** — `createEffect` · `queryEffect` · `destroyEffect` — وإن غابت قال
في اللوق `create (0), query (0), or destroy (0) not exist in library` و**رمى المكتبة**.

ومكتبةٌ تُرفض في المصنع **لا تُحمَّل أبدًا**: لا خطأ يصل للتطبيق، ولا سطرُ عطبٍ واحد — «نجحتْ ولا
يُسمع فرق». وهذا مُثبتٌ من ثلاثة مواضع مستقلّة، لا مستنتَج:

1. **`EffectTypes.h`** (AOSP): توقيعات الدوالّ الثلاث بالحرف — وهي المنقولة في `maxfx_aidl.cpp`.
2. **`EffectsFactoryHalAidl.cpp`** (إطار `frameworks/av`): **صفر** `dlopen`/`dlsym`/مسار
   `legacy` فيه؛ فليس في الإطار طريقٌ ثانٍ يحمّل مؤثّرًا محليًّا على جهاز AIDL. (و`EffectsXmlConfigLoader`
   الذي يحمّل `AELI` بـ`dlopen` ما زال في الشجرة، لكن لا يمرّ منه جهازٌ بعقد AIDL.)
3. **تغيير LineageOS MERGED** (٥٠١٢٢٨، `lineage-24.0`): «Add an AIDL wrapper for legacy effect
   libraries» — كُتب **لنفس السبب**: «the HIDL effect HAL (which was itself only a wrapper around this
   C API) … a device moving to an AIDL core HAL would otherwise lose every prebuilt effect it ships».
   أي أنّ الصناعة نفسها اضطرّت لبناء غلافٍ لأنّ العقد القديم مات — وهو ما نبنيه هنا داخل مكتبتنا.

**القرار:**

1. **المكتبة الواحدة تصدّر الخمسة:** `AELI` **و**`createEffect`/`queryEffect`/`destroyEffect`
   معًا في `libmaxfx.so`. لا رمزَ يُناقض آخر (كائنٌ `<name>` ودوالُّ بأسماء أخرى)، ولذلك يعمل
   الجهاز على الجيلين بلا نسختين ولا تفريع في الشحن. **و`AELI` لا تُحذف** (ADR-18): أجهزة
   ≤11 قائمة، وهي تعمل بها اليوم.
2. **ولا نواة ثانية:** المعالجة كلّها في `maxfx_dsp` نفسها، وقناة التحكّم تبقى **الخاصيّات**
   (`persist.audio.maxfx.*`). الغلاف الجديد **مُحوِّلٌ لعقد**، لا نسخةٌ ثانية من المؤثّر.
3. **والنسخ المجمَّدة تُثبَّت بأرقامها لا بـ«current»:** `audio/aidl/Android.bp` يعلن للإصدار **1** من
   `android.hardware.audio.effect` استيراد `android.media.audio.common.types-V2`، والحزمة تُصدِّر
   `android.hardware.common-V2` و`android.hardware.common.fmq-V1`. فهذه النسخ الأربع هي التي كُتب
   بها الإطارُ الذي يقرأ ما نُرسل — وتخطيطُ الـparcelable هو العقد السلكيّ، ونسخةٌ أحدث تقرأ بايتاتٍ
   في غير مواضعها **بلا خطأ ظاهر**.
4. **و`libfmq` تُبنى معنا لا تُخترع:** عقد AIDL لا يقبل مؤثّرًا بلا مقابض دخلٍ وخرج (ثلاثة FMQ في
   `OpenEffectReturn`)، و`libfmq` **ليست في الـNDK**؛ فمصدرها منسوخٌ في `maxfx/third_party/libfmq`
   (Apache-2.0) مع بدائل `compat/` لأنّ `libbase`/`libcutils`/`libutils` ليست في الـNDK أيضًا.
   **والبديل المرفوض صراحةً:** كتابة تخطيط الذاكرة المشتركة يدويًّا — عطبُ صوتٍ صامت مقابل كودٍ منسوخ
   يُقاس.
5. **والبوابة على الرموز لا على «نجح البناء»:** CI يقيس `readelf --dyn-syms` على `libmaxfx.so`
   **للعمودين** ويُسقط إن غاب أيٌّ من الخمسة — فمكتبةٌ تُبنى ولا تُحمَّل هي بالضبط ما نسدّه.
6. **وحدودٌ مُعلنة في الغلاف الجديد:** `float` حصرًا (كما في `EffectImpl` المرجعيّ)، وقناتان أو
   واحدة وما عداهما مرورٌ حرفيّ، و`setParameter` يقبل ولا يُطبِّق (التحكّم من الخاصيّات) — فرقٌ
   مقصود عن المرجعيّ الذي يرفض ما لا يعرفه.

7. **واللقطات المجمَّدة لا تُنسخ أعمى وتُقاس بلا مُصرّف:** الجلبُ الأول لمجموعة الـ٩٤ ملفًّا
   **فشل لعشرةٍ منها صامتًا** (ملفّات فارغة أو ببقايا base64)، ولم يكشفه `kt_balance` (ملفٌّ
   فارغ متوازن) ولا `code_health` (ولا نصّ فيه). فصار للقطةٍ حكُمُ قبولٍ مُعلَن (حجم · سطر
   `package` واحد · علامة `IMMUTABLE` · قوس إغلاق) في `tools/fetch_maxfx_aidl.py`، **ويُستورد
   حكمه ولا يُعاد كتابته** في `tools/maxfx_contract.py` — مصدرٌ واحد، إذ مصدران ينحرفان =
   حكمان. وتقيس البوابةُ — **بلا مُصرّف** — الرموزَ الثلاثة وتوقيعاتها وبقاءَ `AELI` وسلامةَ
   اللقطات وإغلاقَ تضمين `libfmq` من أبوابنا الفعليّة واتساقَ البناء، مع `--self-test` يقيسها
   على أعطابٍ مغروسة. **وهذا قرارٌ لا تفصيل:** كل ما لا يُقاس إلا بعد بناءٍ يُقاس متأخّرًا.

**الأثر:** لم تبق ثغرةُ عقدٍ بيننا وبين أي جهاز: «مكتبةٌ تصدّر الخمسة يقبلها الجيلان». **وحدّه المُعلن:**
هذا يُزيل **رفض المصنع**، ولا يُثبت أن الصوت يتغيّر — فقبول `audioserver` وتحميل المكتبة وسماع الفرق
**يحتاج جهازًا** (§0.1). وما قِيس هنا هو البنية والتوقيعات والرموز، لا الصوت.

## ADR-67 — **ما يفرضه الـNDK على بناء عقد AIDL**: رؤوسٌ منسوخة · منصّةٌ `31` · ومصادرُ الرزم الأربع

**الحالة:** ملزم · **السياق:** تكملة ٢٤٥ (بناء `libmaxfx.so` أوّل مرّة، ثمّ نجاحه في CI).

ثلاثة قيود اكتُشفت **بالقياس في مُصرّف حقيقيّ** بعد أن كان الغلاف ٥٩٤ سطرًا لم تُترجم مرّة واحدة.
وكلٌّ منها كان يُكلّف دورة CI كاملة لو خُمِّن، فلذلك تُكتب هنا بأدلّتها:

1. **رؤوس الـC++ لـ`libbinder_ndk` منسوخةٌ عندنا** (`maxfx/third_party/libbinder_ndk_cpp`، ٨ رؤوس
   Apache-2.0 · ٣٢٠٤ سطرًا). السبب مقيسٌ على حزمة **NDK r29 نفسها** (`android-ndk-r29-linux.zip`
   · `Pkg.Revision = 29.0.14206865`، وهو بالحرف ما يستعمله CI) بقراءة فهرسها بنطاقات HTTP بلا
   تنزيلها: `sysroot/usr/include/android/` فيه **واجهة C فقط**، وكلّ رؤوس الـC++ غائبة، و**لا مجلّد
   `platforms/` في الحزمة إطلاقًا** — أي أنّ مسار `platforms/android-31/optional/libbinder_ndk_cpp`
   الذي وصفته مسألة NDK `android/ndk#2130` (المُغلقة «كما هو مقصود») **لا وجود له في r29**. والمسلك
   هو مسلك `libfmq` نفسه: تُنسخ رقعةٌ لا تُخترع، وتُحرس ببوابة.
2. **والرأس المنسوخ لا يُخلط بالنظام:** نُنسخ **ما غاب فقط**، ويبقى `binder_ibinder.h` و`binder_status.h`
   و`binder_parcel.h` من الـNDK — فرأسٌ زائد عندنا يُظلّل رأس الـNDK (نسخُ طبقةٍ بدل رقعة).
   والباقي ([`binder_shell.h`](../maxfx/third_party/libbinder_ndk_cpp/README.md)) **يُترك عن قصد**:
   يُضمَّن بـ`__has_include` وغيابُه يُطفئ مسار `dumpsys` لخدمةٍ لسنا هي.
3. **والمنصّة `31` لا `29`:** من جهة الوظيفة (ولادة مصنع AIDL و`ParcelableHolder`)، ومن جهة الحصانة
   (‏`libbinder_ndk.so` مشحونٌ في الـr29 للإصدارات **٢٩–٣٥ فقط**، فلا يُقفز إلى ما لا يُشحَن).
   **وثمنه مُعلَن:** المكتبة تُوسم بالجيل 12+، فمسار `AELI` على أجهزة ≤11 يبقى مُصدَّرًا لكنّ تحميله
   عليها لم يعد مضمونًا كالسابق — وهذا **حدّ عتادٍ يُحتاج جهازًا لقيسه**.
4. **و"الرؤوس تكفي" غير صحيح: المصادر تُصرَّف أيضًا — وللرزم الأربع.** قِيس ذلك بثلاث سقطات متتالية
   في CI، وكلّها **ربطٌ** لا ترجمة: `BnEffect::createBinder()` (تعريفه في مصدر النوع المُولَّد)، ثمّ
   `NativeHandle::readFromParcel` و`GrantorDescriptor::writeToParcel` و`AudioUuid::…` (تعريفاتها في
   مصادر رزم `hardware/common` و`hardware/common/fmq` و`media/audio/common`). والسبب بنيويّ: رأس
   المولِّد يضمّ هذه الأصناف **كنماذج** في `binder_parcel_utils.h`، فيُصدِر المُصرّف نداءً بلا تعريف.
   والمُصرَّف هو **مصادر رزمنا الأربع كلّها**، في **موضعين** لكلٍّ منها (`gen/android` للمصادر و
   `gen/aidl` للرؤوس) — وهما مجلّدان مختلفان لأنّ المولِّد يبني مسار المصدر من اسم الحزمة وحده.
5. **والبوابة تمتدّ إلى مسار الربط:** `tools/maxfx_contract.py` صار **٨ فحوص** (كانت ٦): فحصٌ
   للرؤوس المنسوخة (وجود · حجم · `#pragma once` · ترخيص Apache · **لا ظلّ على الـNDK** · إغلاق
   تضمينها الفعّال بوعي حرّاس `__has_include`)، وفحصٌ لنطاقات الأسماء (`::android::media::` و
   `::android::hardware::audio::` بلا `aidl` = عطب)، مع `--self-test` يُثبت الحالتين لكلّ قاعدة.

**وحدّ مُعلن:** كلّ ما تقدم يُثبت أنّ المكتبة **تُبنى وتُصدَّر**؛ ولا يُثبت أنّها **تُحمَّل ويُسمع أثرها**
— فذلك يحتاج جهازًا (§0.1)، وهو ما بقي.

## ADR-68 — أنماط الصوت: إعدادات مقروءة ومقارنة قابلة للرجوع، لا وعد بمعالجة مسموعة

**السياق:** أمر المالك «اكمل وحسن اكثر شاشة الصوت واجعله متطور ورائع»؛ التنفيذ `AUDIO-PRESETS-01`.

1. الأنماط الستة وصفات أصلية فوق مؤثرات Android الحالية، عبر `AudioEffectBackend` والمحكّم نفسه؛ لا root shell جديد ولا تجاوز للملكية ولا كود من المشاريع المرجعية.
2. النجاح يعني كتابة كل معاملات الخطة وقراءتها؛ الفشل يوقف الخطة ويجرب استعادة كل المقابض الملموسة، بما فيها المقبض الذي فشل. رفض الاسترجاع يُعلن ولا يُخفى.
3. «قبل» يستعيد إعدادات ما قبل أول نمط، و«بعد» يعيد آخر خطة ناجحة؛ المقارنة نقرٌ واضح قابل للوصول، لا ضغط مطوّل يعتمد على توقيت coroutine. الإيقاف يستعيد الإعدادات ولا يكتب أصفارًا اعتباطًا.
4. EQ يستوفي لوغاريتميًا عند مراكز الجهاز المقروءة (Android يعيد milli-Hz)، ويطرح أعلى رفع من المنحنى ليكتب خفضًا فقط ضمن حدود الجهاز. `preAmpDb` وصف نظري **لا** preamp مطبّق ولا limiter؛ BassBoost/Loudness قد يسببان تشويهًا، فلا ضمان نقاء.
5. الميزة الأساسية تستلزم جلسة وقراءات صالحة؛ الإضافات الثانوية غير المتاحة تُتخطّى ويُقال اسمها. تغيير النمط يعطّل المؤثرات غير المستخدمة من المجموعة المُدارة فقط. لا يُغيّر Dolby/MaxFx/Dynamics تلقائيًا.
6. التحكّم اليدوي مطويّ ومحجوب أثناء النمط كيلا يفسد خط أساس المقارنة. MaxFx يبقى تجريبيًا داخل الأدوات ولا يُحذف ولا يوصف بأنه يعمل على كل هاتف.
7. تمكين المؤثّر نفسه مستثنى من حارس «المحرك عطّله»، **لا** من حارس الملكية أو المحكّم: وإلا يستحيل Off→On. ترتيب الرجوع: تمكين مؤقت، قيم، ثم حالة التمكين الأصلية.
8. لا خدمة خلفية أو Shizuku جديدة في هذه الجولة؛ الجلسات تعيش مع ViewModel الملاحة وتُحرّر في `onCleared`. أثر مسموع، حياة الخلفية وتعارض التطبيقات وRTL/اللمس تحتاج جهازًا، ومراجعة السلامة المستقلة معلّقة.

### متابعة ADR-68 — AUDIO-UX-02 (2026-10-02)

- اختيار البلاطة صار **معاينة دون كتابة**، ويحتاج زر «طبّق»؛ هذا تغيير متعمد بطلب معاينة النمط قبل تطبيقه. النمط المعروض في بطاقة الحالة هو آخر تطبيق ناجح، لا البلاطة الجاري فحصها.
- القوة `0/50/100` تعني `0.35/1.0/1.4` من الوصفة، بمنحنى متصل ومتزايد؛ الافتراضي50. لا ادعاء أن0 إيقاف أو أن شدة المعالجة تساوي الجهارة المدركة.
- التشخيص قراءة صريحة فقط، بلا فتح جلسات أو تمكين أو كتابة. يقرأ الاتصال والتمكين وملكية التحكم والقراءات ويطابق الخطة المحفوظة للجانب الحالي من A/B. خطّة غائبة أو قيمة غائبة = مجهول، لا نجاح.
- التشخيص **لقطة غير مستمرة** تُمحى عند محاولة تغيير النمط/المقارنة وعند إعادة تحميل جرد الجهاز. نتيجة التطبيق السابقة تُسمى «آخر تطبيق» لا حالة حيّة.
- أولsink فيAudioInventory مخرج مكتشف، وليس مسار التشغيل النشط المثبت؛ بطاقة الحالة تقول ذلك صراحةً. المعاينة تقترح محاولة فقط، ولا تضمن بقاء الملكية بعد القراءة.
- حفظ أنماط لكل مخرج، منع قفزات الجهارة بقياسPCM، ومحركDSP أعمق ليست ضمن هذه المتابعة ولم تُنفّذ.

### مراجعة ADR-68 — AUDIO-REVIEW-03

الرجوع عند الفشل يستخدم تمكينًا مؤقتًا قبل معاملات المؤثّر وحالة التمكين الأصلية بعده، حتى إن لم تصل الكتابة بعد إلى مفتاحenabled. خطةAfterتضمقيمخطالأساسللمؤثراتالمستخدمةفيأنماطسابقة، فلايرجعA/Bبقيمناقصة. خطةcut-onlyترفضمدىEQالموجبفقطوالفهارسالمكررة. لاتعطيللمؤثرغيرمستخدمإذاكانتملكيتهغيرمقروءةtrue؛ الحارسالمركزييبقىمرجعكلعملية. الاختبارات تثبتترتيبالقراراتوالقيملاالصوتالمسموعأوتوافقROM.
