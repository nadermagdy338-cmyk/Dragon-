# Max Atlas Self-Repair Plan

**الحالة:** خطة بحث وتصميم فقط — لا كود منتج، لا بناء، لا تثبيت، ولا تجربة كتابة على جهاز.
**التاريخ:** 2026-09-20.
**الهدف:** جعل Atlas يكتشف المسار الصحيح الآمن، ويشغّل الإصلاح عبر control plane الموجود، ويثبت النتيجة أو يتراجع عنها، مع قياس تغطية واقعي بدل وعد «يعمل على 90%» بلا مقام.

---

## 1. النتيجة المطلوبة بصيغة قابلة للقياس

لا نعني بـ«90%» أن كل CPU/GPU في كل هاتف سيقبل كل تعديل. التعريف القابل للقياس هو:

> **تغطية 90% من حالات التحكم التي يعلن الجهاز أدلة كافية لها، ضمن مصفوفة الاختبار المحددة، مع نجاح موثق للكتابة والقراءة العكسية وعدم كسر baseline.**

ويُحسب كل جهاز/تحكم بهذه الحالات:

1. `SUPPORTED_VERIFIED`: Atlas اختار route، والـarbiter طبّقه، والقراءة العكسية طابقت الطلب.
2. `UNSUPPORTED_HONEST`: لا route آمن مثبت؛ لم تُجرَّ كتابة تجريبية، وظهر السبب.
3. `BLOCKED_PRIVILEGE`: المسار معروف لكن الصلاحية/SELinux/transport منعته.
4. `REJECTED_UNSAFE`: الطلب غير آمن، خارج OPP معلن، متعارض مع thermal/safety أو بلا baseline.
5. `FAILED_ROLLED_BACK`: جرت معاملة مصرح بها، فشلت القراءة العكسية، وتم التراجع والتحقق منه.
6. `FAILED_ROLLBACK_UNVERIFIED`: حالة حرجة لا تُحسب نجاحًا وتفتح تقريرًا.

**المقام:** الحالات التي تملك هوية provider وقراءة baseline وOPP/قيمة صالحة ومسارًا يمكن اختباره بأمان. أما جهاز بلا root أو بلا عقد تحكم فلا يدخل مقام «فشل التطبيق»؛ يظهر Unsupported/Blocked.

**مؤشرات الإغلاق:**

- ≥90% `SUPPORTED_VERIFIED` على مصفوفة الأجهزة المعتمدة لكل capability مقاس.
- 0 نجاح وهمي: لا `writeSucceeded=true` بلا read-back مطابق.
- 100% من الفشل إما `UNSUPPORTED/BLOCKED/REJECTED` أو `FAILED_ROLLED_BACK`.
- 0 حالة يكتب فيها Atlas خارج `HardwareControlArbiter`.
- 0 حالة يتغلب فيها global profile أو daemon على Per-App ownership بعد تثبيت الإصلاح.
- كل route له provenance، preconditions، rollback، وآخر نتيجة جهازية.

---

## 2. ما أثبته البحث والسياق الحالي

### 2.1 من المستودع

- `AtlasSafetyClass` لا يزال `READ_ONLY`؛ نموذج Atlas لا يملك صلاحية تحكم، وهذا صحيح أمنيًا لكنه يفسر عدم قدرته على الإصلاح.
- `AtlasBackendProvider` يعيد استخدام CPU/GPU discovery عبر seams للقراءة فقط؛ لا يملك `write`، ولا ينبغي أن يملكها.
- `AtlasRepository` يملك ترتيبًا جيدًا: reviewed knowledge ثم bounded discovery ثم candidate bank، مع أسباب فشل منفصلة وميزانية وإلغاء.
- `HardwareControlArbiter` هو المكان الصحيح للمعاملة: owner priority، baseline، journal، apply، read-back، rollback، وshared lock.
- `RootFileAccess.writeOutcome` يسجل الكتابة ويقرأ بعدها، لكن معرفة route الصحيح ليست من اختصاصه.
- `CpuHardwareBackend` يعتمد `policy*` ويدعم legacy alias grouping وOPP ladder؛ ويمنع القيم غير المعلنة عبر `snapToAvailableAtOrBelow`.
- `GpuHardwareBackend` يميز Qualcomm/Mali، devfreq وMediaTek OPP/index، ويرفض ambiguity ويملك rollback لبعض المسارات.
- `PerAppControlRegistry` يملك النية ويطلب الإصلاح من الـarbiter، لكن لا يوجد حتى الآن جسر رسمي من Atlas diagnosis إلى repair plan ثم registry.
- السجلات التي ظهرت في هذه المحادثة (`live-value-mismatch`، `permission denied`، وفشل `gpu_profile`/`thermal`) تدل على فشل control-plane/route/ownership، لا على أن واجهة Atlas اكتشفت المسار ثم نفذته.
- الإصلاحات السابقة عالجت تنافس `Balanced/Eco/applyfreqbalance` وتمرير `cpu_policy_controls`، لكنها لا تثبت نجاح جهاز جديد قبل إعادة بناء الوحدة والتطبيق واختبارهما عليه.

### 2.2 من المصادر الخارجية

- توثيق Linux CPUFreq الرسمي يوضح أن التحكم يتم على مستوى **policy** قد يشمل عدة أنوية، وليس على core منفرد دائمًا. لذلك يجب أن يخطط Atlas للـpolicy/related_cpus لا لتخمين عدد الأنوية.
- توثيق devfreq يوضح أن واجهة GPU تشبه CPU لكنها ليست موحدة بين كل السائقين؛ وجود `devfreq` لا يعني أن كل الحقول قابلة للكتابة أو أن governor يدير التردد بنفس الطريقة.
- SmartPack Kernel Manager يؤكد قيمة inventory واسع وcustom controllers وطلب «paths + content + apply method» عند البلاغات، لكنه ليس دليلًا على أن path معروف قابل للكتابة على جهاز آخر. لن ننسخ path database أو كود GPL إلى المشروع.
- AKTune يثبت نمطًا مهمًا: daemon واحد، lock، baseline لكل boot، blocked nodes، استعادة القيم، وترك controls الخطرة خارج النطاق. هذا نمط معماري نستفيد منه، لا كودًا أو مسارات.
- AOSP يثبت أن SELinux يطبق default-deny حتى مع root؛ لذلك root وحده لا يثبت قابلية الكتابة، ولا يجوز جعل `test -w` أو `chmod` دليل نجاح.
- KernelSU يوضح أن Root Profile قد يقيد capabilities وSELinux domain؛ لذلك يجب تسجيل **privilege/backend generation** واعتبار تغيره سببًا لإبطال evidence والـleases.

### 2.3 ما لن نفعله

- لن يجعل Atlas يرسل أوامر shell حرة أو يبحث في النظام بـglob ثم يكتب أول نتيجة.
- لن نجرّب الكتابة «لنعرف هل تعمل»؛ discovery يبقى read-only.
- لن نرفع `INFERRED` إلى `REVIEWED` بسبب نجاح واحد.
- لن نضيف fallback ثانيًا يكتب مباشرة بجانب الـarbiter.
- لن نوقف thermal أو daemon أو خدمة vendor لإجبار القيمة.
- لن نعتبر shell exit code نجاحًا؛ النجاح هو live read-back مطابق + ownership سليم.
- لن نستورد قاعدة SmartPack أو Kelvin أو Calibrate-SoC بلا فحص license/provenance وبلا تكييف semantics؛ المصادر مراجع تصميم وليست ضمان توافق.

---

## 3. المعمارية المقترحة

```text
User intent / Per-App profile
          |
          v
Atlas Diagnosis (read-only evidence)
          |
          v
Route Planner (pure, no I/O, no authority)
          |
          | ranked RepairPlan with preconditions
          v
HardwareControlArbiter (single writer + ownership)
          |
          v
Verified Route Executor (bounded transaction)
          |
          +--> read-back / stability window / conflict check
          |
          +--> commit evidence OR rollback + quarantine route
          v
Atlas Outcome + Support Report + failure ledger
```

### 3.1 Atlas Diagnosis

يستمر Atlas في القراءة فقط، لكن يضيف إلى evidence ما يحتاجه المخطط:

- provider identity: Qualcomm KGSL، generic devfreq، MediaTek OPP/index، cpufreq policy، vendor daemon.
- exact interface identity، unit، OPP ladder، current value، baseline، parent/listing proof.
- access state: readable، denied، read-only observed، ambiguous، unavailable.
- privilege/backend generation، boot generation، daemon/module version إن كان متاحًا بأمان.
- writer-conflict hints من السجلات المسموح بها، دون تصدير raw logs أو أسرار.

### 3.2 Route Catalog / Knowledge Bank

ينشأ catalog versioned وموقّع داخل التطبيق، لا runtime download:

```text
RouteId
Domain / capability
Provider identity predicates
Required evidence
Allowed request shape
Preconditions
Apply adapter key
Verification rule
Rollback rule
Safety class
Provenance + source revision
Known failures / quarantine conditions
```

أمثلة route مفاهيمية (ليست paths جديدة بعد):

- `CPUFREQ_POLICY_RANGE`: policy + advertised OPP + min/max + governor capability.
- `GPU_DEVFREQ_RANGE`: selected devfreq provider + explicit frequency table + min/max.
- `GPU_MTK_FIXED_OPP`: signed OPP map + fixed-index node + index read-back.
- `GPU_GOVERNOR`: advertised governor list + governor node + read-back.
- `PER_APP_PROFILE`: route group composed from CPU/GPU/Thermal controls with one ownership token.

كل route جديد يبدأ `CANDIDATE` أو `REVIEW_REQUIRED`. لا يصبح `REVIEWED` بالاستنتاج من الاسم أو حجم الرقم.

### 3.3 Pure Route Planner

الـplanner لا يقرأ ولا يكتب. يأخذ `AtlasSnapshot + UserIntent + SafetyState` ويعيد:

- ordered candidate plans;
- سبب ترتيب كل plan؛
- preconditions المطلوبة؛
- fields التي سيلمسها فقط؛
- rollback strategy؛
- maximum attempts؛
- `NO_SAFE_ROUTE` إن لم يوجد route مكتمل.

الترتيب المقترح:

1. route reviewed لنفس provider/ABI والـunit.
2. route generic مطابق لـkernel-declared OPP وcontrol semantics.
3. route candidate موثق لكنه يحتاج موافقة/fixture؛ لا يُنفذ تلقائيًا.
4. `UNSUPPORTED_HONEST`.

نجاح route لا يمنح routes أخرى ثقة تلقائيًا؛ كل provider/capability له evidence مستقل.

### 3.4 Repair Executor عبر الـArbiter

نضيف seam منفصلًا، لا نوسّع Atlas reader إلى writer:

- `RepairPlan` immutable.
- `RepairExecutor` يستدعي `HardwareControlArbiter.submit` فقط.
- `HardwareControlKey` canonical؛ لا key من path خام أو package name فقط.
- baseline يُلتقط قبل أول mutation ويُربط بـboot/provider/ownership generation.
- apply يلمس الحقول التي يطلبها plan فقط.
- read-back يطابق semantics الطلب: range، governor، index، release-lock، لا مقارنة string كلية لحالة لا يطلبها المستخدم.
- stability window قصيرة ومحدودة للتحقق من أن daemon لم يعكس القيمة فورًا.
- عند الفشل: rollback للحقول الملموسة فقط، read-back للـbaseline، ثم quarantine للـroute في هذا boot.
- لا retry غير محدود؛ budget ثابت، وretry لا يعيد تجربة route ثبت أنه unsafe.

### 3.5 Per-App lifecycle

- `beginApp(package)` يحرر lease السابق ثم ينشئ token واحدًا لكل app profile.
- كل CPU/GPU/Thermal route في profile يسجل تحت نفس ownership session لكن بمفاتيح hardware مستقلة.
- global profile و`applyfreqbalance/applyfreqgame` لا يكتبان knob يملكه Per-App، في كل modes.
- daemon لا يُعد «خصمًا» بالاسم فقط؛ إذا غيّر القيمة بعد commit يسجل `EXTERNAL_WRITER_DRIFT`، ثم يقرر arbiter إن كان repair آمنًا أو يجب quarantine.
- profile apply يكون atomic قدر الإمكان: إن فشل GPU بعد CPU، تُستعاد CPU أو يتحول الملف إلى partial explicit state؛ لا واجهة تقول Applied للجميع.

---

## 4. مراحل التنفيذ

### R0 — تثبيت بروتوكول الحقيقة (لا كتابة)

**الهدف:** إعادة إنتاج الفشل الحالي ببيانات حديثة، لا الاعتماد على screenshots أو binary قديم.

- استخراج device matrix read-only: SoC/vendor، Android/API، kernel، root transport، SELinux mode، module/daemon revision.
- تسجيل لكل knob: path/provider، raw baseline، writable observation، actual write route الحالي، read-back، actor الذي أعاد الكتابة.
- إضافة correlation id موحد من UI → registry → arbiter → native/daemon.
- فصل `permission denied`، `node absent`، `readback differs`، `external drift`، `rollback failed`.
- لا إغلاق لأي compatibility claim قبل أن يصل log من build حديث مثبت على الجهاز.

**مخرج:** fixtures sanitized + device evidence table.

### R1 — Route contracts وknowledge bank

- تعريف route schema وprovenance وversion.
- تحويل seeds الحالية إلى routes بلا تغيير سلوك الكتابة.
- ربط كل route بـexisting backend adapter أو `DEFERRED`.
- إضافة negative cases: ambiguous GPU، missing OPP، untrusted unit، partial policy، denied SELinux، conflicting daemon.
- مراجعة licenses للمصادر قبل أي reuse؛ لا نسخ GPL code إلى Apache project بلا قرار قانوني.

**بوابة:** pure planner tests + catalog validator + no writer in catalog/planner.

### R2 — Repair planner

- pure ranking and precondition engine.
- لا route إن كان baseline غير مقروء، أو unit غير موثوق، أو request خارج advertised values، أو safety state يمنع.
- إظهار سبب القرار للمستخدم: `Selected route because ...` و`Skipped route because ...`.
- candidate route لا ينفذ تلقائيًا؛ ينتج `REVIEW_REQUIRED`.

**بوابة:** deterministic trace tests، لا جهاز ولا mutation.

### R3 — Arbiter bridge وtransaction protocol

- تنفيذ `RepairExecutor` فوق `HardwareControlArbiter`.
- استبدال أي direct repair path في Per-App بالexecutor الموحد.
- توحيد baseline/rollback وfield-scoped read-back.
- stability check بعد commit.
- quarantine/backoff لكل route failure، مع إبطال quarantine عند boot/provider/privilege generation جديد.

**بوابة:** fake-I/O tests تثبت zero direct Atlas writes، ownership tests، rollback tests، conflict tests.

**مراجعة سلامة مستقلة إلزامية:** لأن هذه المرحلة تمس hardware/control plane/native ownership.

### R4 — دمج daemon/native ومنع الكتابة المتنافسة

- contract واضح بين Kotlin arbiter و`archdaemon/binprofiles`: current owner، token، desired fields، commit/release.
- daemon يعيد `accepted/applied/verified/rejected/conflict` بدل boolean.
- أي writer دوري يستعلم عن ownership قبل الكتابة؛ لا يعتمد على `per_app_active` غير الموثق وحده.
- native thermal path يميز node read-only عن wrong route، ولا يعتبر chmod نجاحًا.
- one-writer lock عبر process death/restart مع stale-token recovery.

**بوابة:** native/Rust static checks + simulator/fake daemon + device read-only diagnostics ثم device controlled test.

### R5 — التعلم من الفشل دون سلوك خطير

Atlas لا «يتعلم» بتغيير عشوائي. التعلم هنا هو evidence ledger:

- route success يرفع evidence لهذا **provider + capability + kernel identity** فقط.
- route failure يسجل cause ويخفض الثقة ويحجر route خلال boot.
- repeated verified success عبر أجهزة/إصدارات مستقلة يسمح بتغيير route من `CANDIDATE` إلى `REVIEWED` عبر إصدار catalog جديد، وليس runtime.
- لا ترسل بيانات الجهاز تلقائيًا ولا تنزل routes من الشبكة.
- user may export sanitized report and opt-in to contribute fixture.

### R6 — واجهة Atlas AI

- شاشة تعرض: diagnosis، selected route، preconditions، transaction stages، read-back، rollback.
- أزرار `Try safe repair` و`Do not retry this route`، لا زر «force anyway» مخفي.
- أثناء الفشل تعرض الفرق بين:
  - التطبيق لم يكتب؛
  - الكاتب رفض؛
  - الكاتب قبل ثم driver صحح القيمة؛
  - writer آخر أعادها؛
  - rollback لم يثبت.
- Per-App تعرض partial state إن نجح CPU وفشل GPU، ولا تعرض profile كأنه مطبق بالكامل.

### R7 — قياس 90% والإغلاق

مصفوفة دنيا:

- Qualcomm/KGSL.
- MediaTek/Mali مع devfreq.
- MediaTek fixed OPP/index.
- Exynos/Tensor/unknown generic devfreq.
- policy* الحديثة.
- legacy cpuN/cpufreq aliases.
- root denied / SELinux enforcing.
- no-root read-only.
- daemon competing writer.
- multiple GPU-like providers / ambiguity.

لكل جهاز، لكل capability: run baseline → request safe in-table value → verify → wait stability window → switch global/per-app → verify ownership → release → verify restoration. لا overclock، لا thermal bypass، ولا values خارج الجدول في qualification الأولى.

---

## 5. استراتيجية الاختبار

### JVM / static

- planner deterministic tests.
- route schema/provenance tests.
- CPU policy grouping and OPP snapping.
- GPU provider ambiguity and unit tests.
- field-scoped verification tests.
- rollback and quarantine tests.
- ownership/preemption/daemon drift tests.
- source guard: Atlas cannot import/use `RootFileAccess.write`, shell, chmod, or native writer.

### Simulator

- fake cpufreq policies: heterogeneous policy sets, offline CPUs, missing ladder.
- fake devfreq: Qualcomm, generic Mali, MTK index, read-only node, corrected-value driver.
- competing daemon changes value between write and read-back.
- permission denied and backend unavailable.
- process death with stale journal.

### Device protocol

- build حديث للتطبيق والوحدة، لا اختبار على binary قديم.
- read-only inventory first.
- one safe in-table CPU request and one safe GPU request only.
- capture all event IDs.
- test Per-App → switch profile → wait → return app → release.
- stop immediately on rollback failure, thermal anomaly, or unexplained writer.
- report exact build/module/daemon hashes.

**قاعدة:** fixtures تثبت منطق parser/planner فقط؛ لا تثبت أن route يعمل على هاتف حقيقي.

---

## 6. تعريف «Max AI» داخل المنتج

Max AI ليس نموذجًا لغويًا يكتب أوامر shell. دوره المنتجّي الآمن:

1. يقرأ evidence typed.
2. يطابقه مع knowledge bank ذي provenance.
3. يشرح الفجوة ويصنف السبب.
4. يبني RepairPlan محدودًا.
5. يمرر التنفيذ إلى arbiter.
6. يقرأ النتيجة ويقرر commit/rollback/report.
7. يتذكر **نتيجة route الموثقة**، لا يخترع قاعدة عامة من جهاز واحد.

بهذا يصبح مفيدًا مثل ما طلبت، دون أن يتحول إلى root agent قد يضر الهاتف. الذكاء الحقيقي هنا هو **اختيار route الصحيح والتحقق والتراجع**، لا عدد المسارات التي يجربها.

---

## 7. مراجع مفتوحة المصدر ناجحة ودروسها لـ Atlas

هذه ليست قائمة أسماء فقط؛ فُحصت وثائق المشاريع وادعاءاتها التشغيلية، وفُصل بين ما هو موثق في الكود/السجل وما هو مجرد README claim.

| المشروع | ما أثبته أو يقدمه | ما نأخذه | ما لا نأخذه |
|---|---|---|---|
| **Calibrate-SoC** (Apache-2.0) | أقوى مرجع مباشر: يذكر اختبارات يومية على Odin 3 وRetroid Pocket 6 وAYANEO Pocket DS، وvendor bridges، AutoTDP goal-seeking، per-game learning، provider tiers، route fallback، safety gate، restore، ونتيجة جهازية موثقة مثل `EACCES` للـdirect sysfs ثم نجاح PServer bridge وعودة القيم بعد Stop | provider registry، حل privilege إلى طبقات، fallback صريح، control loop بهدف قابل للقياس، profile learning، تقرير «لا أستطيع» بدل fake success، وdevice-support report format | لا ننسخ `PServer` أو vendor Binder، ولا ندعي أن generic fallback يثبت التوافق. المشروع **pre-alpha** ودعمه الحقيقي محدود بأجهزة handheld معينة |
| **Kelvin** (MIT) | يفصل التطبيق عن daemon: Kotlin UI لا يلمس `/sys`، daemon native يملك providers، socket contract، profile engine، hard thermal ceiling، stock backup، bootloop watchdog، hysteresis، per-app profiles، ويدعي providers لـ Qualcomm/MediaTek/Exynos-Tensor/generic | فصل control plane، daemon-side allowlist، provider واحد لكل SoC، hysteresis، boot guard، safety interlock، status protocol بدل Boolean | لا نعتمد ادعاء «كل هاتف» دون سجلات أجهزة مستقلة؛ README يذكر أن vendor thermal engine قد يعيد كتابة `scaling_max` وأن بعض التحكم best-effort. لا ننسخ كودًا أو مسارات قبل مراجعة الملف والرخصة |
| **AKTune** (MIT) | نموذج عملي محافظ: daemon واحد مع lock، baseline لكل boot، blocked nodes، pending marker، restore، refusing competition with a running daemon، وحذف controls الخطرة بدل توسيعها | one-writer lock، boot-scoped baseline، quarantine، unfinished-transaction guard، عدم منافسة daemon، إبقاء max/thermal intact، واستعادة محددة | لا نأخذ فلسفة «أقل controls» كبديل لهدف Max Atlas؛ نستخدمها كحد أمان للـroutes غير المثبتة |
| **SmartPack / Kernel Adiutor** (GPL) | خبرة طويلة في inventory واسع، custom controllers، CPU/GPU/thermal profiles، واشتراط إرسال path/content/apply method عند طلب دعم؛ متوافق عمليًا مع عدد كبير من kernels لكنه يعتمد على known interfaces | catalog واسع مع per-interface semantics، custom controller concept، device report format، وفصل profile عن UI | لا ننسخ GPL code أو path tables؛ path معروف لا يساوي verified write، والمشروع ليس self-healing AI |
| **N0Kontzzz Kernel Manager** (GPL) | per-app profiles وRust telemetry وbackup/restore، ويذكر أن restore يتحقق من القيم مقابل kernel الحالي؛ لكنه مخصص لـ Poco F4/kernels محددة | validate imported profile against live kernel قبل التطبيق، hardware-specific scope، Rust read layer حيث يفيد | لا نستخدمه كمرجع universal؛ اعترافه الصريح بأنه Poco F4-only دليل على أن التخصص أصدق من وعد 90% |
| **RvKernel Manager** (GPL) | مرجع UI وميزات CPU/GPU/profile، لكنه يعلن صراحة Snapdragon-only وroot-required | عرض capability حسب الجهاز، وعدم إظهار knobs غير المدعومة | لا نأخذ منه claim التغطية العامة ولا نفترض أن واجهة slider تعني نجاحًا |
| **external_thermal_daemon / thermald** (GPL) | daemon ناضج متعدد المنصات، thermal zones/trips/PID، fallback/workaround، logging وblacklists؛ ليس Android CPU/GPU manager عامًا | thermal policy كحلقة تحكم لها safety ceiling وtrip semantics وblacklist، لا مجرد slider | لا نستخدم مسارات Intel أو نتجاوز Android vendor thermal owners، والرخصة تمنع النسخ غير المدروس |

### الحكم

**أفضل مزيج مرجعي لـ Atlas هو:**

```text
Calibrate-SoC  = discovery + privilege routing + goal-seeking + device evidence
Kelvin         = split planes + providers + safety interlock + daemon protocol
AKTune         = lock + baseline + quarantine + restore discipline
SmartPack      = breadth of interface inventory + custom-controller workflow
N0Kontzzz      = live-kernel validation before restore/profile import
thermald       = thermal control-loop semantics and hard safety boundaries
```

ولا يوجد مشروع واحد وجدناه يحقق «يفهم كل هاتف ويصلح كل route» بالكامل. أقرب مشروعين لهدفك هما **Calibrate-SoC** من جهة التحقق على أجهزة حقيقية، و**Kelvin** من جهة فصل daemon/providers والسلامة؛ وكلاهما لا يثبت 90% من كل الهواتف.

### تعديلات لازمة على خطة Atlas بعد البحث

1. R1 سيضم `ProviderRegistry` و`PrivilegeRouter` بدل catalog مسارات فقط.
2. R3 سيضم one-writer/daemon handshake وboot-scoped baseline وpending-transaction guard.
3. R3/R4 سيضيفان `stability window` و`external writer drift`، لأن Kelvin وCalibrate أثبتا أن vendor daemon قد يعكس القيمة.
4. R5 سيأخذ تعلم Calibrate-SoC لكن بصيغة evidence scoped إلى `provider + kernel + capability`؛ لا ثقة عامة من جهاز واحد.
5. R6 سيعرض `unsupported/blocked/verified/rolled-back`، لا slider enabled لمجرد أن path ظهر.
6. سنحتفظ بفصل Atlas read-only؛ صلاحية التنفيذ تبقى في repair executor والـarbiter.
7. أي route مأخوذ من مشروع GPL يحتاج إعادة تنفيذ مستقل أو قرار ترخيص قبل إضافته؛ المراجع المعمارية لا تعني إذن نسخ.

---

## 8. مراجع الجولة الأعمق: ما نجح فعلًا وما نرفضه

### PULSE — أقوى مرجع لـ closed-loop + quiet UX

الرابط: https://github.com/keiretrogaming/pulse

PULSE يذكر دعمًا موثقًا على أجهزة handheld محددة، ويجمع بين:

- قراءة clusters وOPP وGPU power levels من الجهاز وقت التشغيل.
- AutoTDP مغلق الحلقة: target FPS → قياس الأداء → تقليل الطاقة حتى الحد الأدنى الذي يحافظ على الهدف.
- تعلم floor لكل لعبة.
- per-app overrides تتغلب على global profile.
- معرفة أن target غير قابل للتحقيق، والتوقف عن حرق الطاقة خلفه.
- fallback صريح: إذا لم يوجد PServerBinder يظهر incompatible ولا يطلب root.
- تحويل unit عند boundary: CPU kHz، وAdreno power-level index، لا خلط بينهما.
- reapply-on-boot، sleep-aware switching، وprofiles قابلة للاستيراد مع حدود الجهاز.

**ما نأخذه:** هدف قابل للقياس، controller مغلق الحلقة، target-unreachable state، live OPP snap، boundary conversion، quiet automation.

**ما لا نأخذه:** PServer الخاص بأجهزة معينة، ولا ادعاء universal compatibility. PULSE نفسه يحدد الأجهزة التي تم اختبارها فعليًا.

### PerfMTK — مرجع provider/daemon لكن ليس مرجع ثقة

الرابط: https://github.com/JUANIMAN/PerfMTK

مفيد في:

- native daemon وforeground detection متعدد الطبقات.
- profiles وQuick Settings وCLI.
- predictive thermal slope وdebounce.
- MediaTek-specific providers.

لكن README يذكر claims واسعة جدًا مثل universal OEM coverage و120 FPS، ويعرض خيارات تعطيل OEM thermal وthermal bypass. لم نجد في هذه الجولة دليلًا مستقلًا يكفي لاعتماد هذه الادعاءات لكل الأجهزة.

**القرار:** نأخذ provider decomposition وevent detection فقط، ونرفض thermal disable/bypass وclaims التوافق غير المقاسة.

### Stellar Tweaks — مرجع orchestration فقط

الرابط: https://github.com/kanaodnd/Stellar-Tweaks

يفيد في:

- daemon Rust.
- profile transitions.
- cached hardware mapping.
- clean tweaks/full rollback.
- root/non-root capability split.

لكن claims التوافق و"decision intelligence" في README ليست كافية وحدها كدليل جهاز مستقل.

**القرار:** لا يدخل knowledge bank؛ يدخل قائمة architectural inspirations فقط حتى تظهر fixtures وسجلات جهازية.

### AOSP GameManagerService + ADPF — المرجع الرسمي الأول

المراجع:

- https://android.googlesource.com/platform/frameworks/base/+/12f5992e4df6/services/core/java/com/android/server/app/GameManagerService.java
- https://source.android.com/docs/core/power/performance
- https://developer.android.com/codelabs/adaptability-codelab

AOSP يثبت أن الطريقة الأكثر أمانًا ليست دائمًا كتابة `/sys`:

- Game Mode مرتبط بالـpackage والـforeground game.
- Power HAL يمكنه تنفيذ sustained performance على مستوى OEM.
- ADPF يعطي thermal headroom وPerformance Hint Session.
- validation الرسمي يقيس ثبات FPS خلال 30 دقيقة ويطلب تغيرًا أقل من 5% في sustained mode.
- platform hint يسمح للنظام أن يختار provider الداخلي بدل أن يتصارع Atlas مع vendor thermal engine.

**القرار المعماري الجديد:** route priority يصبح:

```text
AOSP GameMode / ADPF / Performance Hint
→ verified vendor bridge
→ verified root daemon/provider
→ existing arbiter sysfs route
→ read-only / unsupported
```

لا نستخدم root route إذا كان platform route يحقق الهدف بأمان أكبر.

### مصفوفة الثقة النهائية

| المستوى | المشروع/المصدر | الثقة | الاستخدام |
|---|---|---:|---|
| A | AOSP GameManager/ADPF + Linux ABI | عالية جدًا | semantics وplatform-first safety |
| A | Calibrate-SoC | عالية ضمن الأجهزة المثبتة | provider routing وclosed-loop وdevice proof |
| A- | PULSE | عالية ضمن handhelds المثبتة | AutoTDP وquiet UX وunit boundaries |
| B+ | Kelvin | قوية معماريًا، hardware scope يحتاج تحقق مستقل | daemon split وinterlock وwatchdog |
| B | AKTune | قوية في lifecycle discipline | lock/baseline/quarantine/restore |
| B | SmartPack/Kernel Adiutor | قوية تاريخيًا في breadth، لا self-repair | catalog/custom-controller workflow |
| C | N0Kontzzz/RvKernel | device-specific | live-kernel validation وcapability-gated UI |
| C | PerfMTK/Stellar | أفكار واعدة لكن claims واسعة | لا اعتماد route قبل device evidence |

---

## 9. ثغرات الخطة المكتشفة بعد التحقق الثاني

### G-UX-01 — Atlas ظاهر كأداة تشخيص لا كخدمة ذكية

الحالة الحالية في `AtlasViewModel` و`AtlasDiagnosticsSection` تتطلب `Start/Retry` وتعرض progress وtiers وreport. هذا مفيد للمطور، لكنه يخالف تجربة «المستخدم لا يشعر بشيء».

**الإصلاح المخطط:** فصل وضعين:

- **Quiet mode (افتراضي):** لا شاشة scan، لا dialog، لا progress، ولا طلب report. يعمل فقط عند وجود intent أو event مهم، ويعرض آخر حالة صغيرة في صفحة التحكم عند فتحها.
- **Expert mode:** الشاشة الحالية الموسعة: evidence، provider، route، read-back، rollback، reason codes، والتقرير.

لا نخلطهما بإخفاء الفشل: quiet mode يخفي التفاصيل، لا الحقيقة. الفشل المهم يظهر كحالة صغيرة قابلة للنقر، لا كنافذة توقف المستخدم.

### G-UX-02 — لا نملك بعد lifecycle صامتًا

فتح شاشة Atlas هو الذي يبدأ scan اليوم. هذا لا يكفي لتفعيل Per-App أو إصلاح drift أثناء اللعب.

**الإصلاح المخطط:**

- `AtlasSessionCoordinator` طويل العمر لكن لا يبدأ sweep تلقائيًا عند الإنشاء.
- يبدأ فقط من events محددة: foreground package change، profile intent، ownership drift، boot/module/privilege generation change، أو طلب إصلاح.
- scan صغير incremental ومخزن مؤقتًا؛ لا full discovery عند كل recomposition أو app switch.
- الـdaemon/module المستمر هو الذي يراقب hardware إذا احتجنا tick متكررًا؛ التطبيق لا يحاول إبقاء foreground service مخفيًا.

### G-UX-03 — وهم التشغيل الخفي مع Android الحديث

توثيق Android الرسمي يوضح أن foreground service عملية ملحوظة ويجب أن تعرض status-bar notification، كما أن تشغيلها من الخلفية مقيد في الإصدارات الحديثة. لذلك «لا يشعر المستخدم» لا يعني «نخالف نظام Android».

**السياسة:**

- العملية القصيرة المرتبطة بإجراء المستخدم: صامتة داخل التطبيق ولا notification.
- المراقبة المستمرة: daemon/module إن كانت متاحة، أو foreground service بإشعار منخفض الأولوية وصريح عند الضرورة.
- لا نبدأ FGS من الخلفية بلا event/استثناء قانوني.
- لا notification عند نجاح route عادي؛ notification فقط لـ permission required، rollback failed، safety intervention، أو daemon unavailable المستمر.
- كل notification يشرح الحالة في سطر واحد ويقود إلى شاشة التفاصيل، ولا يعرض raw path أو سجلات.

### G-TECH-01 — نجاح لحظي لا يساوي ثباتًا

قراءة القيمة بعد الكتابة مرة واحدة لا تكشف writer آخر يعيدها بعد 100–1000 ms.

**الإصلاح:** `read-back → stability window → ownership re-check`، مع sampling محدود ووقف آمن. النتيجة `VERIFIED_STABLE` أو `VERIFIED_BUT_REWRITTEN`، وليس `verified=true` واحدًا.

### G-TECH-02 — route صحيح لكن transport غير صحيح

Calibrate-SoC يثبت أن direct sysfs قد يفشل بينما vendor bridge ينجح. وجود root أو `test -w` لا يكفي.

**الإصلاح:** `PrivilegeRouter` يختبر transports بالترتيب من evidence، ولا يخلط vendor Binder مع root shell. كل transport يملك capability contract وسبب رفض منفصل.

### G-TECH-03 — تبديل تطبيق سريع يسبب معاملات متداخلة

Per-App الحالي يتأثر بتبديل foreground والـdaemon والدورات العامة. بدون session generation يمكن أن يصل commit قديم بعد بدء profile جديد.

**الإصلاح:** كل profile session يحمل `generation + token + cancellation fence`. النتيجة القديمة لا تستطيع تحرير أو استعادة lease الجديد. آخر profile فقط يملك commit.

### G-TECH-04 — فشل جزئي في profile متعدد المجالات

CPU قد ينجح وGPU يفشل وThermal يرفض. لا يجوز عرض profile كأنه Applied بالكامل.

**الإصلاح:** state machine لكل field:

```text
PLANNED → APPLYING → VERIFIED_STABLE
                  ↘ ROLLED_BACK
                  ↘ BLOCKED / UNSUPPORTED
```

والـprofile الكلي يصبح `FULL`, `PARTIAL`, `RESTORED`, أو `FAILED_SAFE` مع إجراء المستخدم الوحيد عند الحاجة.

### G-TECH-05 — التعلم قد يلوث أجهزة أخرى

نتيجة route ناجح على MT6899 لا تعني نجاحها على MT6895 أو kernel vendor مختلف.

**الإصلاح:** مفتاح التعلم الأدنى:

```text
provider identity + SoC family/model + kernel ABI fingerprint
+ Android/API + capability + transport + daemon revision
```

والنتيجة لا تنتشر إلى catalog reviewed إلا عبر fixture/device review وإصدار catalog جديد.

### G-TECH-06 — التراجع نفسه قد يفشل

vendor daemon أو thermal safety قد يمنع restore، أو الجهاز قد يعاد تشغيله أثناء transaction.

**الإصلاح:** baseline journal durable، `apply_pending` marker، boot recovery guard، restore verification، وحالة حرجة لا تعيد المحاولة تلقائيًا إذا كان rollback غير مؤكد.

### G-TECH-07 — اتساع 90% يضخم المخاطر

«كل تحكمات التطبيق» تعني مئات semantics مختلفة، لا route واحدًا. توسيع catalog لا يساوي توسيع compatibility.

**الإصلاح:** capability cohorts مستقلة، وfeature flag لكل cohort، وqualification منفصل. لا يُفعّل route جديد لمجرد أنه مرّ من parser.

---

### G-TECH-08 — لا يمكن التحكم بذكاء بلا هدف قابل للرصد

PULSE وCalibrate-SoC ينجحان لأنهما لا يسألان «ارفع الأداء» فقط؛ بل يملكان target مثل FPS أو utilization band أو temperature ceiling. Max Atlas يحتاج intent typed:

```text
PERFORMANCE_TARGET(fps / latency / sustained)
POWER_TARGET(watts / battery / quiet)
THERMAL_TARGET(max temperature / headroom)
PROFILE_TARGET(game / app / global)
```

إذا لم يملك Atlas FPS أو power أو headroom موثوقًا، لا يخترع هدفًا؛ يستخدم safe bounded profile أو يطلب اختيارًا واحدًا بسيطًا. هذا يمنع AI من تحسين metric غير مقاس.

### G-TECH-09 — platform hints مفقودة من route ranking

إضافة root route قبل ADPF/Game Mode ستجعل Atlas ينافس النظام. لذلك يجب أن تبدأ كل capability بـ capability negotiation:

1. هل Game Mode/ADPF متاح؟
2. هل provider يثبت أن hint سيصل إلى power HAL؟
3. هل vendor bridge موثق ومقروء؟
4. هل root route هو الحل الأخير؟

### G-TECH-10 — stability يجب أن تقيس النتيجة لا التردد فقط

AOSP sustained-performance validation يقيس FPS stability خلال 30 دقيقة، لا مجرد clock value. لذلك route evaluator يجب أن يملك outcome types منفصلة:

```text
CONTROL_VERIFIED       = القيمة تطابقت
GOAL_VERIFIED          = الهدف المقاس تحقق
SUSTAINED_VERIFIED     = الهدف بقي ثابتًا خلال نافذة الاختبار
CONTROL_ONLY           = تغيرت العقدة لكن النتيجة غير مقاسة
```

لا نعرض `GOAL_VERIFIED` من `CONTROL_VERIFIED` وحده.

### G-TECH-11 — foreground detection ليس مصدر حقيقة وحيدًا

PerfMTK يعرض عدة طبقات لاكتشاف التطبيق الأمامي، بينما Android GameManager يملك package/game state رسميًا. يجب أن يملك Atlas source precedence وconfidence، وألا يبدل profile بسبب قراءة واحدة أو race بين Activity وprocess.

### G-TECH-12 — all-controls scope يحتاج cohorts

لا يمكن تأهيل CPU/GPU/Thermal/charging/display/storage/network في route واحد. سنقسمها إلى cohorts مستقلة، ولكل cohort:

- provider catalog.
- safety boundary.
- goal model.
- transaction/rollback semantics.
- qualification fixtures.
- kill switch مستقل.

لا يفتح cohort جديد صلاحية cohorts أخرى.

---

## 10. تجربة المستخدم: لا يشعر بالتعقيد، ولا يفقد الحقيقة

### 9.1 القاعدة الذهبية

المستخدم العادي يريد نتيجة، لا route graph. لذلك:

```text
Normal user: intent → quiet apply → small status → done
Power user: intent → details → route/evidence/read-back/rollback
```

### 9.2 التدفق الافتراضي

عند تغيير GPU/CPU أو اختيار Per-App:

1. لا نفتح شاشة Atlas.
2. لا نعرض قائمة paths.
3. لا نطلب اختيار backend.
4. يظهر control بحالة `Applying…` محليًا فقط إذا استغرق أكثر من حد قصير.
5. عند النجاح: يعود control إلى القيمة المطلوبة مع علامة تحقق بسيطة أو لا شيء إذا كان النجاح فوريًا.
6. عند fallback الناجح: لا نزعج المستخدم؛ يسجل Atlas route داخليًا، وتظهر `Details` اختيارية.
7. عند الرفض الآمن: يظهر سبب قابل للفهم مثل «هذا الجهاز لا يتيح هذا التحكم» مع بديل إن وجد.
8. عند rollback: يظهر «تمت استعادة الإعداد السابق للحماية»؛ لا يدّعي أن التغيير طُبق.
9. عند فشل rollback: يظهر تنبيه واضح ولا يعيد المحاولة تلقائيًا.

### 9.3 ما يظهر وما يختفي

**يظهر افتراضيًا:**

- الحالة الحالية فقط.
- `Applied` لا تظهر إلا بعد `VERIFIED_STABLE`.
- `Unavailable` و`Blocked` و`Restored` بألفاظ بسيطة.
- زر `Details` عند fallback أو failure.

**يُخفى افتراضيًا:**

- raw sysfs paths.
- أسماء routes الداخلية.
- candidate count.
- progress التفصيلي.
- stack traces وshell output.
- report/export controls.

**يظهر في Expert details:**

- route المختار ولماذا.
- transport المستخدم.
- requested/live/baseline.
- stability result.
- actor الذي سبب drift.
- rollback verdict.
- زر export sanitized report.

### 9.4 لا نستخدم نجاحًا بصريًا كاذبًا

- لا نلون slider أخضر بعد إرسال الأمر مباشرة.
- لا نكتب «تم» عند shell exit code فقط.
- لا نعرض `100%` أثناء scan إذا كان العدد الكلي غير معروف.
- لا نعرض `CPU/GPU controlled` إذا كان الموجود telemetry فقط.
- لا نزعج المستخدم بتكرار نفس failure؛ suppression/backoff يعملان بصمت، لكن آخر سبب يبقى متاحًا.

### 9.5 الأداء والبطارية

- لا full scan عند فتح الشاشة.
- لا polling من Compose.
- no-op إذا لم تتغير identity/privilege/profile generation.
- batch reads داخل budget.
- cooldown بعد route failure.
- daemon tick فقط إذا كان هناك active profile أو safety requirement.
- عند screen-off: release أو تخفيف حسب profile، ولا استمرار غير مبرر.

### 9.6 الوصول واللغة

- كل حالة لها نص، لا لون فقط.
- `Applying`, `Verified`, `Restored`, `Blocked`, `Unsupported` لها ترجمة عربية وإنجليزية.
- TalkBack يقرأ النتيجة النهائية لا raw transitions.
- لا تظهر رسائل تقنية إلا داخل Details.
- لا نستخدم dialog لكل fallback؛ snackbar/status chip يكفي، والحرج فقط modal.

### 9.7 ما أخذناه من البحث الخارجي

- Calibrate-SoC: onboarding يقرر طبقة access، ويدعم fallback الصريح وعبارة unknown device بدل تعطيل تجربة المستخدم.
- Kelvin: التطبيق يعرض status من daemon ولا يملك `/sys`، والـHUD/الملف الشخصي يعملان بلا إجبار المستخدم على فهم provider.
- AKTune: التحولات الصامتة تتم في daemon، وتظهر السجلات عند الحاجة فقط، مع restore وblocked state.
- Unity Adaptive Performance: التطبيق يستهلك thermal/performance feedback ويعدل هدفه بدل جعل المستخدم يضبط عقد النظام يدويًا.

---

## 11. خطة التحقق من UX والفشل

### اختبارات بدون جهاز

- لا يظهر dialog عند fallback ناجح.
- لا يظهر `Applied` قبل stable read-back.
- rollback يظهر فقط عند فشل التغيير، وليس عند cancellation العادي.
- rapid app switch لا يسمح للـold generation بتحرير الـnew lease.
- partial profile لا يصبح full success.
- repeated failure لا يكرر snackbar/notification ضمن suppression window.
- screen rotation لا تعيد transaction أو chooser.
- quiet mode لا يبدأ scan عند recomposition.
- Expert mode يعرض نفس frozen artifact الذي سيُصدّر.
- accessibility يميز status text عن اللون.

### اختبارات جهازية لاحقة

- measure apply-to-stable latency.
- measure writer drift latency.
- verify no notification for short user-triggered transaction.
- verify required notification/foreground behavior for continuous daemon/service.
- profile switch during game and during screen-off.
- kill app/daemon mid-transaction and reboot recovery.

---

## 12. قرارات المالك والتفسير الهندسي

أجاب المالك في هذه الجولة:

1. **الهدف المطلوب:** 90% من كل الهواتف، لا 90% من الحالات القابلة للإثبات فقط.
2. **السلوك المطلوب:** Atlas يجب أن يفهم ويحلل ويقرر ويعرف طريقة تفعيل route الصحيح، لا أن يكتفي باكتشافه.
3. **النطاق المطلوب:** كل تحكمات التطبيق، لا CPU/GPU فقط.
4. **اختبار الجهاز:** غير متاح حاليًا.

### ترجمة هذه القرارات إلى سياسة قابلة للتنفيذ

الهدف السوقي سيبقى **هدفًا استراتيجيًا** لا ادعاءً مقاسًا. لن نكتب «90%» في واجهة التطبيق أو تقرير النجاح حتى تتوفر مصفوفة أجهزة وبيانات فعلية. أثناء التنفيذ نستخدم coverage denominator مؤقتًا، ثم نوسع المختبر تدريجيًا.

«الذكاء الفائق» في Atlas يعني حلقة قرار كاملة:

```text
intent → evidence → hypothesis → route ranking → preconditions
→ arbiter transaction → read-back → stability → commit/rollback
→ failure explanation → scoped evidence update
```

ولا يعني تجربة paths عشوائية أو تجاوز SELinux. إذا لم يثبت Atlas الـABI أو الـunit أو الـbaseline أو rollback، فالقرار الصحيح هو **عدم الكتابة**، مع شرح السبب واقتراح ما يلزم جمعه. هذا ليس نقصًا في الذكاء؛ بل شرط أن يكون القرار آمنًا وقابلًا للتكذيب.

بما أن المستخدم طلب كل تحكمات التطبيق، ستُبنى R1–R6 كـroute engine عام، لكن ترتيب qualification يبدأ CPU/GPU/Thermal/Per-App لأنها العلة الحالية. بقية التحكمات تدخل catalog عبر نفس العقد، ولا تحصل على صلاحية تنفيذ قبل provider-specific tests.

بما أن اختبار الجهاز غير متاح حاليًا:

- يمكن تنفيذ planner، catalog، simulator، rollback، ownership، daemon-conflict وprivacy tests.
- لا يمكن إغلاق أي claim خاص بجهاز أو SoC.
- لا يمكن اعتماد route جديد لمجرد نجاح fixture.
- الحالة عند نهاية الكود ستكون `DONE_WITH_CONCERNS` إلى أن تصل سجلات build حديث من جهاز فعلي.

### بوابة تنفيذ إلزامية

لن يُنفذ أي route تلقائيًا إلا إذا حقق جميع الشروط التالية: provider/ABI موثق، unit وOPP مثبتان، baseline مقروء، safety state يسمح، ownership عبر arbiter، rollback قابل للتحقق، وread-back مطابق. Route مجهول لا يكتب؛ route candidate يعرض فرضية وأسبابها ويمكن ترقيته لاحقًا بدليل جهاز، لكن لا يتحول إلى صلاحية عامة من تحليل لغوي وحده.

---

## 13. مراجع البحث

- Linux CPUFreq documentation: https://docs.kernel.org/admin-guide/pm/cpufreq.html
- Linux devfreq documentation: https://docs.kernel.org/5.19/driver-api/devfreq.html
- AOSP SELinux: https://source.android.com/docs/security/features/selinux
- SmartPack Kernel Manager: https://github.com/SmartPack/SmartPack-Kernel-Manager
- KernelSU App Profile: https://kernelsu.org/guide/app-profile.html
- AKTune (baseline/lock/blocked-node patterns): https://github.com/iodn/android-kernel-tweaker
- Linux devfreq ABI: https://gitlabci.ic.unicamp.br/lkcamp/linux-staging/-/blob/7378487d5585187d1288486d4627873170d0005a/Documentation/ABI/testing/sysfs-class-devfreq
- PULSE: https://github.com/keiretrogaming/pulse
- PerfMTK: https://github.com/JUANIMAN/PerfMTK
- Stellar Tweaks: https://github.com/kanaodnd/Stellar-Tweaks
- AOSP performance management: https://source.android.com/docs/core/power/performance
- AOSP GameManagerService: https://android.googlesource.com/platform/frameworks/base/+/12f5992e4df6/services/core/java/com/android/server/app/GameManagerService.java
- ADPF adaptability codelab: https://developer.android.com/codelabs/adaptability-codelab
- Android foreground service constraints: https://developer.android.com/develop/background-work/services/fgs
- Project research ledger: `01-SOURCES.md`, especially S01/S02/S03/L01/A04.

---

## 14. الحالة بعد هذه الجولة

**DONE_WITH_CONCERNS (planning):** البحث والخطة مكتملان، لكن لا يوجد ادعاء بأن Atlas يصلح جهازًا فعليًا بعد. السبب ليس نقص فكرة فقط؛ الجسر من Atlas إلى arbiter، وتوحيد daemon ownership، وdevice qualification ما زالت مراحل تنفيذ مستقلة. لا يبدأ تعديل الكود قبل اعتماد تعريف 90% ونطاق R0–R3.
