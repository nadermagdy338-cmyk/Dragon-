# Max Atlas: فجوات الجولة الثانية وأفكار من خارج الصندوق

**الحالة:** تحليل وتخطيط — **لا كود ولا بناء في هذه الجولة**.
**التاريخ:** 2026-09-20 · **المُعِدّ:** جلسة الطبقة ١ (المنسّق/المنفّذ) بعد `P0`+`P1`+`P2`+`P4`.
**العلاقة بسابقه:** `01-CONTEXT` (المتطلبات) → `01-RESEARCH`/`01-PATTERNS`/`01-SOURCES` (الخريطة والمصادر)
→ `01-PLAN` (الخطط P0–P9) → **هذه الوثيقة** (فجوات + مصادر جديدة + أفكار). لا تُلغي شيئًا
من الخطة؛ تُغنيها وتُصحّح افتراضاتها في ضوء مصادر لم تُقرأ سابقًا.

---

## 0. حدود هذا العمل — ما لم يُتحقق منه

| لم يُفعل | الأثر على القراءة |
| --- | --- |
| لا جهاز أندرويد ولا قياس تنفيذي | كل حكم على «ما تقرأه التطبيقات فعلًا» هو **من سياسة AOSP وإصدارات المنصة**، لا من قياس. يبقى `needs device` |
| لا بناء ولا اختبار في هذه الجولة (بأمر المالك §0.1) | لم يُشغَّل مُصرّف؛ البوابات الثابتة فقط (§9) |
| لم أقرأ شجرة `system/sepolicy` كاملة | `private/domain.te` وصل مبتورًا؛ احتمال وجود `allow` إضافي لم أره **قائم**، وكل ما بنيته على «غير موجود» مكتوب بصيغة «لم أجده» لا «لا يوجد» |
| لم أفتح ملفات الرخص | **لا استيراد كود ولا مسار ولا جدول** من أي مصدر أدناه؛ مراجع معمارية فقط. عمود الرخصة = «لم تُفحص هذه الجولة» |
| صفحة Google Issue Tracker تُرسَل بـJS | استُشهد بالعنوان والمقتطف فقط، لا بمحتوى الصفحة |

**قاعدة الإسناد المتبعة هنا** (تقوية لقواعد `01-SOURCES.md`): كل ادّعاء يحمل **blob/commit** أو رابطًا،
وكل ما لم أقرأه بنفسي مكتوب صراحةً `N` (لم يُفحص).

---

## 1. حالة أطلس المقيسة الآن (لإصلاح الخط الأساس)

| المقياس | القيمة | الدليل |
| --- | --- | --- |
| ملفات المنتج | 3 (`AtlasModels` 390 · `AtlasCatalog` 417 · `AtlasPlatformProvider` 846 · `ReadOnlyProbeAccess` 472) | `wc -l` |
| ملفات الاختبار | 8 · 74 اختبارًا في `nd.max.core.atlas.*` · 295 في `nd.max.core.*` | جولة سابقة |
| **نقاط الاستدعاء في المنتج** | **صفر** — لا ملف منتج خارج `core/atlas/` و`core/hardware/ReadOnlyProbeAccess.kt` يستوردها | `grep -rln … \| wc -l` = 0 |
| بوابات ثابتة | `code_health` exit 0 · الدَّين `10/29/66/26` · `i18n` 0 عوائق · `kt_balance` 0 عوائق | جولة سابقة |
| ملاحظة | الملفات كلها **لا تُصرّف في سياقها الحقيقي بعد** لأن لا مُهايئ (adapter) ولا مستدعي | — |

**القراءة الصحيحة لهذا الجدول:** أطلس اليوم **بنية تحتية مُختبرة وحداتيًا، بلا أثر منتج**. هذا مقصود
في الخطة (P5–P9)، لكنه يعني أن أكبر «فجوة» ليست في العمق بل في **الوصول**: لا شيء منها يعمل في التطبيق.

---

## 2. فجوات مؤكدة (G) — كل واحدة بدليل من الشجرة

### G-01 — لا نقطة استدعاء واحدة في المنتج
**الدليل (مُصحَّح 2026-09-20 — الصياغة الأولى كانت لا تُعيد إنتاج نفسها):**
`grep -rln 'core.atlas' manager/app/src/main/java/nd/max --include=*.kt` يُعيد **٨ ملفات** لا «لا شيء»:
٧ منها داخل `core/atlas/` تشير إلى حزمتها، والثامن `core/hardware/ReadOnlyProbeAccess.kt` يستورد
**مفردات** أطلس (`AtlasAccess` · `AtlasAnchors` · `AtlasFailure` · `AtlasProbeRequest` …) بلا استدعاء.
والحقيقة المقيسة التي تصمد:
- **صفر مستدعٍ:** `grep -rn 'ReadOnlyProbeAccess' manager/app/src/main --include=*.kt` خارج الملف نفسه ⇒ لا شيء.
- **صفر ذكر لأطلس في طبقة الواجهة:** `grep -rln 'Atlas[A-Z]' manager/app/src/main --include=*.kt` خارج `core/atlas/` ⇒ ملف واحد فقط، وهو الحدّ نفسه.
**لماذا فجوة:** كل ادّعاء «مصمَّم جيدًا» غير مُثبت من الطرف إلى الطرف؛ لا مستخدم يرى شيئًا، والحدّ الكامل
مُصرَّف ومختبَر **ولا يُشغّله أحد**.
**الإغلاق:** `P5` (مستودع + DI) ثم `P7` (شاشة) — لا تُغلق بأي شيء آخر.
**⇒ مُغلقة (2026-09-20، `P5` و`P7` و`P8`):** المقيس الآن **عشرة** ملفات خارج `core/atlas/` تذكره،
والواجهة تصل إليه فعلًا (`AtlasViewModel` → `AtlasDiagnosticsSection` داخل شاشة التشخيص، ومدخل من
الإعدادات). فالحدّ لم يبقَ يتيمًا، **لكن** «مُغلقة» هنا تعني مسارًا يُرسم واختبارًا يمرّ — لا قراءة
مُثبَتة على عتاد: هذه البيئة بلا `adb` وبلا محاكي.

### G-02 — محور `STALE` ميّت: لا سياسة عمر ولا TTL في الكود
**الدليل:** `AtlasModels.kt:88` يُعرِّفه؛ `grep -rn 'AtlasFailure.STALE'` ⇒ **لا مُنتِج واحد**.
**لماذا فجوة:** نموذج «حداثة» بلا TTL يصبح ادّعاءً؛ و`P5.4` يعد بسياسة TTL لكن **النوع لا يحملها**،
فأي مستهلك مستقبلي سيخترع سياسته. والأخطر: دمج ملاحظة عمرها 10 دقائق مع أخرى عمرها 100 مللي ثانية
بلا تمييز.
**الإغلاق:** نوع `AtlasFreshness` (فئة TTL + العمر) في `P5`، وإنتاج `STALE` عند التجاوز.
**الحالة بعد `P12` (2026-09-20):** **نصف مُغلق — والوصف الأول كان يخلط بين شيئين.**
- **مُغلق فعلًا:** جدول الأعمار صار في المنتج (`AtlasFreshnessPolicy`، و`AtlasBudgets` يفوّض إليه فلا
  نسخة ثانية)، والخريج صار يحمل **السبب** لا حكمًا واحدًا (`AtlasStaleness`: `FRESH` ·
  `EXPIRED_BY_TIME` · `SUPERSEDED_BY_BOOT` · `SUPERSEDED_BY_PRIVILEGE` · `UNMEASURABLE_CLOCK`
  بترتيب أسبقية ثابت)، وساعة ترجع للخلف تُوسم بذلك لا «حديثة».
- **غير مُغلق — وبيان:** `AtlasFailure.STALE` **لا مُنتِج له حتى الآن**، و`P12` لا تدّعي أنها صارت واحدًا.
  و`AtlasFailureLedger` تُعلّل ذلك بنفسها: التقيّد ليس فشل **قراءة** («نحمل قيمة قديمة» ≠ «فشل القراءة»)،
  فمحلّه المُنتِج هو مستودع `P5` حين يخدم دليلًا محتفظًا به. أي أن هذا البند يُغلق في **`P5`** لا `P12`،
  وقد كان في مستندات `P12` الأولى ادّعاء خلاف ذلك فصُحّح.

### G-03 — لا نموذج تكلفة ولا إيقاع ولا تراجع (backoff)
**الدليل:** `AtlasReadBudget` يعدّ `maxOperations/maxAggregateBytes/…` فقط؛ لا `cost` لكل مدخل، ولا
تصنيف تقلّب، ولا مهلة فشل. `AtlasAccessStats` يعدّ الإجماليات لا التوزيع.
**لماذا فجوة:** عقدة مرفوضة (`PERMISSION_DENIED`) لا يتغيّر جوابها إلا بتغيّر الصلاحية؛ إعادة سؤالها كل
دورة = إسراف + ضجيج `avc: denied` في سجل النظام (وهو بنفسه أثر جانبي غير مرغوب).
**الإغلاق:** `cost/volatility/expectedBytes` لكل مدخل + مجدول حزم + backoff أسّي مع jitter.

### G-04 — لا ذاكرة سلبية مرتبطة بالأجيال
**الدليل:** `privilegeGeneration` موجود في `AtlasObservation` و`ReadOnlyProbeAccess` يعرض
`currentGeneration()`، لكن **لا مستهلك** يخزّن «هذا المسار مرفوض حتى تتغيّر الجيل».
**لماذا فجوة:** الرمز موجود بلا معنى؛ والفشل سيُعاد اكتشافه كل مرة بلا داعٍ.
**الإغلاق:** مفتاح cache = `(path, bootGeneration, privilegeGeneration, cause)` مع TTL سلبي قصير.

### G-05 — البطارية مفردة بينما كل ما حولها جمع
**الدليل:** `AtlasPlatformProvider.kt:135` `fun battery(): AtlasBatteryReading? = null` بينما
`thermalZones()` و`zramDevices()` تُرجعان قوائم (سطور 134/137).
**لماذا فجوة:** أجهزة كثيرة تعرض `battery` + `battery2`/`bms`/`main`، أو تسمّيها `main`. القراءة
المفردة تعني: قيمة واحدة صحيحة أو صفر — والثانية تُهمَل بصمت. وهذا **بالضبط** نوع «الجواب الجزئي الذي
يبدو كاملًا» الذي وُجد أطلس لمنعه.
**الإغلاق:** `batteries(): List<AtlasBatteryReading>` + تصفية بالنوع (`type == "Battery"`) من تعداد
`/sys/class/power_supply` (لا اسم ثابت).

### G-06 — لا دور (role) للواجهة: «قابل للقياس» ≠ «مُدخل تحكّم يملكه وكيل آخر»
**الدليل:** `powerhint.json` الرسمي (S19 أدناه) يكتب إلى `scaling_max_freq`، `…/devfreq_*/min_freq`،
`/sys/kernel/vendor_sched/*`، `/dev/cpuset/*` — أي أن بعض ما نقرأه هو **مقبض سياسة PowerHAL** لا قدرة عتاد.
**لماذا فجوة:** قيمة `scaling_max_freq` المقروءة لحظة تصويت PowerHAL تخبرك **بسياسة**، لا بأقصى ما
يستطيعه الجهاز. تقديمها كـ«سقف العتاد» خطأ دلالي من نفس عائلة «المعنى المُرقّى» التي يحاربها النموذج.
**الإغلاق:** `AtlasInterfaceRole { OBSERVABLE, CONTROL_PLANE_OWNED, UNKNOWN }` على المدخل، والافتراضي
`UNKNOWN` **يخفض** الادّعاء بدل أن يرفعه.

### G-07 — قواعد المسار لا تعبّر عن التداخل والفهرسة
**الدليل:** تعليق الكتالوج: «nested kernel attributes such as `stats/time_in_state` are deliberately not
represented here because the template grammar allows exactly one safe basename» (`AtlasCatalog.kt`, قسم
`AtlasReviewedSeeds`)، ومع ذلك يبني `AtlasPlatformProvider` مسارات يدويًّا
(`"/sys/class/thermal/thermal_zone${zone.index}"`, `"/sys/block/${device.name}"`).
**لماذا فجوة مزدوجة:** (أ) الأسطح الأعلى قيمة غير قابلة للتعبير: `cpufreq/policy*/stats/time_in_state`،
`cpuidle/state*/time|usage`، `/sys/block/*/stat`، `…/zram*/mm_stat`، `cooling_device*/cur_state`.
(ب) **مبدأ مخروق**: الكتالوج يدّعي أنه المصدر الوحيد للمعرفة، بينما نصف المسارات تُبنى خارجه بلا
`sourceId` — فتُفقد قابلية التتبع التي بُني الكتالوج لأجلها.
**الإغلاق:** قاعدة قالب آمنة: `parentRoot` + `dirPattern` مقبول (فهرس رقمي أو اسم آمن) + `attribute`
واحد (ويمكن للمسار المتداخل أن يكون `dirPattern` + `attribute`)، مع **إلزام** أن يمر كل مسار مُنتَج
عبر مُوسِّع الكتالوج (`AtlasPathTemplate.expand`) واختبار يمنع بناء مسار خارجه.

### G-08 — جذور معتمدة ناقصة ولها أسطح مسموحة فعليًا
**الدليل:** `AtlasAnchors.APPROVED` (سطور 156–168) = 10 جذور. وبالمقابل AOSP **يسمح صراحةً** لجميع
النطاقات بقراءة `/proc/cpuinfo` (`allow domain proc_cpuinfo:file r_file_perms`) و`/proc/meminfo`
(`allow appdomain proc_meminfo:file r_file_perms`) — والأخير **معلَّم بالتقاعد**: `# TODO: switch to
meminfo service` (S18a).
**لماذا فجوة:** (أ) لا بذرة واحدة من `/proc/cpuinfo` (وهو **مسموح صراحةً**، أي أقوى دليل توفّر لغير
الجذر)، بينما `/proc/meminfo` يأتي عبر واجهة `AtlasMemoryReading` بلا مسار/مصدر ⇒ لا تتبع.
(ب) لا شيء في الكتالوج يوثّق أن `/proc/meminfo` **على مسار إزالة** ⇒ خطر اعتبار سطح إنتاجيًا دائمًا.
**الإغلاق:** بذور `cpu.info.*` بجذر `/proc` المُضاف إلى القوائم المعتمدة + `note` «التقاعد مُعلَن upstream».
**ويتبع ذلك تصحيح ثانٍ:** `AtlasMemoryReading` يجمع `memTotal/memAvailable` (لهما بديل عام) مع
`swapTotal/swapFree` (**لا بديل عام لهما**)، فالحقول الأربعة في نوع واحد توهم أنها من مصدر واحد ⇒ تُفصل
أو تُوسم كلّ بحسب مصدرها.

### G-09 — الكتالوج لا يعرف «المتوقع توفّره على هذا النوع من الأجهزة»
**الدليل:** §3/§4 أدناه يوثّق أن `sysfs_thermal` و`sysfs_batteryinfo` تُمنَحان في AOSP **لنطاقات النظام**
(system_server / health HAL / charger)، بينما `sysfs_gpu` هي **الوحيدة** التي وجدتها ممنوحة لـ`appdomain`
في `private/app.te`.
**لماذا فجوة:** بدون `expectedAvailability` (`EXPECTED`, `DEVICE_DEPENDENT`, `EXPECTED_DENIED`) سيقرأ
المطور «فشل القراءة» كعطب في كوده، بينما هو **السلوك الطبيعي** على AOSP. الأدب الخارجي يثبته: مشروع
`Android-Thermal-Monitor` يصرّح: «some devices don't allow access to thermal sensors without root, and some
devices don't even expose them at all» (S23).
**الإغلاق:** حقل `expectedAvailability` + إدراجه في مصفوفة الدعم: `PARTIAL` يُشرح سببه لا يُخفى.

### G-10 — لا هوية (SoC/ABI/نواة) في العقد، والجيل صفري ثابت
**الدليل:** `ReadOnlyProbeAccess.observation(...)` يضبط `bootGeneration = 0L` دائمًا، و`AtlasProbeRequest`
لا يحمل أي هوية، والكتالوج يُختار بـ`vendorHints()` التي **لا مُهايئ لها بعد** (سطر 145).
**لماذا فجوة:** `P5.3` يعد بمفتاح cache = «catalogVersion + device/build/kernel + ABI + boot + privilege»،
لكن النوع الحالي **لا يستطيع حمل** device/build/kernel/ABI ⇒ الوعد غير قابل للتنفيذ بلا توسيع النموذج.
**الإغلاق:** `AtlasDeviceIdentity(socManufacturer, socModel, api, abi, kernelRelease)` محقون من مصادر
عامة (A06/A07 أدناه) لا من `/sys`.

### G-11 — المسار القانوني (canonical) يُحسب ثم يُهمَل
**الدليل:** `ReadOnlyProbeAccess.performRead` يحل الروابط (`transport.canonicalPath`, سطر ~265) ويتحقق
من البقاء داخل الجذور، لكن `AtlasObservation` **لا يحمل** النتيجة.
**لماذا فجوة:** `/sys/class/thermal/thermal_zone0` و`/sys/devices/virtual/thermal/thermal_zone0` قد يكونان
الملف نفسه؛ وبلا المسار القانوني لا يمكن كشف «قرأنا الملف ذاته مرتين» ولا بناء الـfixture المخطط
`symlink-alias.json` (P9.1) بصدق. أطلس الحالي يقرأ مرتين ويحسب مرتين.
**الإغلاق:** `canonicalPath: String?` على الملاحظة الناجحة + قاعدة إسقاط التكرار داخل العمل الواحد.

### G-12 — لا درجة ثقة للبلاغات البشرية
**الدليل:** `AtlasSourceConfidence` = `FETCHED, CLAIMED, SOURCE_VERIFIED, NOT_INSPECTED, DESIGN` فقط.
**لماذا فجوة:** أي قاعدة `quirks` تُستقى من تقرير جهاز لمستخدم (وهو **الطريق الوحيد** لتوسيع التوافق
بلا مختبر) لا يمكن وصف مصدرها بصدق ⇒ ستُصنَّف `CLAIMED` وتُقرأ كأنها وثيقة kernel.
**الإغلاق:** `REPORTED` (بلاغ مستخدم، لم يُعَد إنتاجه) بشرط ألّا تُساوي `SOURCE_VERIFIED` أبدًا، وألّا
تُرقّى إلا بإعادة إنتاج ثانية.

### G-13 — الرفض المُبرَّر غير موثَّق كمصدر معرفة
**الدليل:** `AtlasSourceGuard` يمنع `Shell/chmod/canWrite/RootFileAccess/Shizuku` في ملفات أطلس (وهو
الصواب). لكن الشجرة لا تحتوي سجلًّا يقول: **لماذا** هذه الأسطح مستبعدة أصلًا (لأن المنصة تمنعها، لا
لأننا لم نجرب).
**لماذا فجوة:** القاعدة بلا سببها تُعاد مناقشتها كل جولة؛ والقرار الصحيح في `§3` أدناه هو **أصل** يستحق
التوثيق أكثر من كثير من الكود.
**الإغلاق:** قسم §3 من هذه الوثيقة يُنقل كمرجع في `01-SOURCES.md`، وكل مسار مرفوض يأخذ entry
بـ`expectedAvailability = EXPECTED_DENIED` بدل مسح صامت.

### G-14 — لا fixture حقيقي ولا قياس جهاز واحد
**الدليل:** `manager/app/src/test/resources/atlas/` غير موجود؛ كل البيئة الحالية كاذبة بطبيعتها
(`AtlasFakeTransport`). `P9` يعد بها.
**لماذا فجوة:** الأدلة كلها «تصميمية»؛ ولا شيء يمسك انحدارًا في فهم جهاز حقيقي، لأن لا جهاز حقيقي دخل
الاختبارات قط. **هذه أكبر فجوة في النظام كله، وأكبر فرصة أيضًا** (انظر I-13).
**تحديث (2026-09-20، تكملة ٥٥):** **نصفُها أُغلق — النصف الذي كان يمنع الإغلاق.** صار في المستودع
مسار كامل: `AtlasFixture` (صيغة صريحة مُرقّمة + `AtlasFixtureTransport`) و`AtlasFixtureRecorder`
(يسجّل تشغيلًا حقيقيًا عبر واجهة الناقل الشحن) و`AtlasDoctor` (يقارن تشغيلًا مُعادًا بتقرير محفوظ ويحكم
`Reproduced`/`Diverged`/`Refused`). فلم تبقَ الفجوة «لا آلية»، بل **«لا ملف أصله `DEVICE`»**: كل ما
وُجد حتى الآن لقطة `HOST` من الاختبارات، والأصل مكتوب في الملف نفسه فلا يُخلط. أي أن الفجوة صارت
**عملًا بشريًا** (تشغيل الطبيب على جهاز وحفظ الملف) لا عملًا برمجيًا — وهذا تغيير في نوع الفجوة لا في
حجمها: بلا تلك اللقطة، لا يزال لا شيء في الاختبارات يمسك انحدارًا في فهم نواة مصنّع.

---

## 3. ما لا يمكن بناؤه — رفض بمصدر (R)

هذا القسم يساوي في قيمته كل ما عداه: **يمنع جولات كاملة من العمل على أسطح محجوبة**. المصدر الأساسي:
`platform/system/sepolicy` عند `refs/heads/main`، ملفات `private/app_neverallows.te`
(blob `434fb132e7adb545dc511564ca3b631ec617115a`)، `private/app.te`
(blob `3219fbe9623f4b0a6a68da525534cbb85ee39cd3`)، `private/domain.te`
(blob `6999586eaf09978949b1ab3fce5bba738870c47a`) — فُحصت 2026-09-20. (رخصة AOSP: Apache-2.0
بحسب المستودع؛ **ملف الرخصة نفسه لم يُفتح هذه الجولة**.)

| # | السطح | الدليل | الحكم |
| --- | --- | --- | --- |
| **R-01** | `cgroup` / `cgroup_v2` (أي ملف) | `neverallow all_untrusted_apps cgroup:file *;` و`cgroup_v2:file *;` + `allow { domain -appdomain } cgroup:file w_file_perms` | **مستحيل.** لا PSI لكل تطبيق، لا `memory.pressure` داخل cgroup التطبيق |
| **R-02** | `debugfs` | `neverallow all_untrusted_apps { debugfs_type -debugfs_kcov }:file read;` | **مستحيل للقراءة.** `/sys/kernel/debug/*` مغلق (المتاح: `debugfs_trace_marker` للكتابة فقط) |
| **R-03** | لاصقة sysfs الافتراضية | `neverallow all_untrusted_apps sysfs:file no_rw_file_perms;` + `sysfs_type:file { no_w_file_perms no_x_file_perms }` | **لا قراءة عشوائية ولا أي كتابة.** القراءة تحتاج لاصقة ممنوحة صريحة ⇒ «امسح /sys بحثًا عن كل شيء» مرفوض بنيويًّا |
| **R-04** | `/proc/stat` · `/proc/uptime` · `/proc/version` · `/proc/vmstat` · `/proc/loadavg` · `/proc/mounts` · `/proc/swaps` · `/proc/slabinfo` · `/proc/pagetypeinfo` · `/proc/allocinfo` · `/proc/kmsg` · `/proc/asound` · `/proc/vmallocinfo` · `/proc/filesystems` (لغير mediaprovider) · `proc_uid_time_in_state` · `proc_uid_concurrent_*` · `proc_uid_cpupower` · `proc_net_tcp_udp` | قائمة `neverallow all_untrusted_apps { proc … proc_stat … proc_uptime … proc_version … proc_vmstat … }` + `neverallow appdomain proc_uid_*:file *;` + `neverallow { appdomain -shell } proc_net_tcp_udp:file *;` — والتعليق: «Avoid reads from generically labeled /proc files … Create a more specific label if needed» | **محجوب.** لا حمل CPU عام، ولا نواة من `/proc/version` (استعمل `Os.uname()`)، ولا معلومات swap من `/proc` (استعمل `ActivityManager.MemoryInfo`)، ولا نقاط وصل (`Os.statvfs`)، ولا time-per-uid. **ويُصحّح ذلك خطأً شائعًا:** تطبيقات كثيرة تقرأها وتفشل صامتة |
| **R-05** | `selinuxfs` | `allow domain selinuxfs:dir search; allow domain selinuxfs:file getattr;` + `neverallow all_untrusted_apps selinuxfs:file no_rw_file_perms;` | **لا يمكن قراءة حالة SELinux.** «SELinux مغلق» (P9.4) يُستنتَج من **نمط الرفض**، لا يُقرأ |
| **R-06** | اشتراك uevent (netlink) | `neverallow all_untrusted_apps domain:netlink_kobject_uevent_socket *;` | **لا اشتراك حدثي.** كل شيء استعلام دوري — أي أن «event-driven» ليس خيارًا متاحًا لتطبيق عادي |
| **R-07** | `sysfs_net` (MAC/واجهات الشبكة) | `neverallow all_untrusted_apps sysfs_net:file no_rw_file_perms;` | **محجوب.** الشبكة عبر `ConnectivityManager`/`TrafficStats` (عدّادات بايت لكل uid — عامة) |
| **R-08** | كتابة أي شيء في `/sys` | `neverallow all_untrusted_apps sysfs_type:file { no_w_file_perms no_x_file_perms };` | **مستحيل** — يعزّز قرار أطلس للقراءة-فقط: ليس اختيارًا أخلاقيًا فقط، بل قيد منصة |
| **R-09** | `proc_security` · usermodehelper · kernel keyring | `neverallow { domain -init -vendor_init } proc_security:file …` | خارج النطاق ومحجوب |

> **تنبيه منهجي:** وجود سطح في قائمة `neverallow` يعني «ممنوع على **التطبيقات غير الموثوقة**».
> نطاقات النظام (`system_server`, `hal_health_server`, `charger`) مسموح لها بأسطح أخرى — ولهذا
> «الحل» ليس طلب صلاحية إضافية، بل **تغيير المصدر** إلى واجهة عامة أو قبول `PERMISSION_DENIED` بصدق.

---

## 4. الوجه الآخر: ما هو مسموح فعلًا لتطبيق عادي (هذا هو الدليل الحاسم)

| السطح | الدليل الحرفي (AOSP main) | ما يعنيه لأطلس |
| --- | --- | --- |
| **GPU sysfs** | `allow { appdomain -isolated_app_all } sysfs_gpu:file r_file_perms;` (`private/app.te`) | **الوحيد** من أسطح sysfs الممنوحة صراحةً لـ`appdomain`. ⇒ بذور GPU في الكتالوج تستحق `SOURCE_VERIFIED` بدرجة أعلى من غيرها |
| **CPU sysfs** | `r_dir_file(domain, sysfs_devices_system_cpu)` بتعليق «Lots of processes access current CPU information» (`private/domain.te`) | قراءة شجرة CPU (`scaling_*`, `cpuinfo_*`, topology) ممنوحة لنطاق `domain` كله ⇒ `PERMISSION_DENIED` هنا **مفاجأة تستحق تسجيلًا**، لا قاعدة |
| **`/proc/cpuinfo`** | `allow domain proc_cpuinfo:file r_file_perms;` | سطح مراجَع ومتاح، وغير موجود في بذورنا |
| **`/proc/meminfo`** | `allow appdomain proc_meminfo:file r_file_perms;` + `# TODO: switch to meminfo service` | متاح **ومُعلَن التقاعد** ⇒ يُستعمل مع وسم «على مسار الإزالة» |
| **`/proc/pressure`** | **لم أجد** له `neverallow`، ولم أجد له `allow` صريحًا في الملفات التي قرأتها | **غير محسوم.** المنتج يقرأه اليوم (`MemoryStall.kt`). يبقى «يُقاس على جهاز» ولا يُدّعى دائمًا |
| **الخصائص العامة** | `get_prop(domain, soc_prop)`, `build_prop`, `fingerprint_prop`, `exported_default_prop` … | `Build.SOC_MODEL`/`SOC_MANUFACTURER` ليسا API فقط بل خصائص مقروءة ⇒ تحقّق متقاطع مجاني |
| **معرّفات النواة** | `proc_version` ممنوع، لكن `android.system.Os.uname()` عام (API 21) | إصدار النواة يُجلب من API لا من ملف |
| **الشبكة** | `TrafficStats` (عدّادات لكل uid)، `ConnectivityManager` (public) | بديل مقروء عن `sysfs_net` (والعدّادات **لكل uid**، أي بحجم لا بهوية — يوافق قاعدة أطلس لألا تحمل صفوف الشبكة هوية) |
| **إجمالي/متاح الذاكرة** | `ActivityManager.MemoryInfo` (`totalMem`, `availMem`, `lowMemory`, `threshold`) | بديل عام عن `/proc/meminfo` لإجمالي/متاح الذاكرة **فقط** |
| **التبديل (swap/zram)** | لا بديل عام معروف؛ `MemoryInfo` لا يحمل swap، و`/proc/swaps` محجوب (R-04) | **تصحيح لازم للبذور:** `memTotal/memAvailable/swapTotal/swapFree` في `AtlasMemoryReading` لا تُقاس كلّها من مصدر عام واحد ⇒ `memTotal/avail` من الواجهة، وبيانات swap/zram تبقى `DEVICE_DEPENDENT` (من `/sys/block/*`) أو `UNMEASURABLE` |
| **الحمل الحراري** | `PowerManager.getCurrentThermalStatus()` / `getThermalHeadroom()` (A08) | الطريق **المعتمد رسميًا للتطبيقات**؛ ومنهج AOSP يقول `IThermal` للمستمعين الموثوقين فقط، ومعلومات الحساسات المفصّلة **لعملاء موثوقين** |

**الخلاصة التشغيلية:** أطلس يحتاج **رتبة مصدر** لا «سطح واحد للقياس»: واجهة عامة (مراجَعة، مستقرة) >
sysfs ممنوح صراحةً (`sysfs_gpu`, `sysfs_devices_system_cpu`) > `/proc` ممنوح مع علم تقاعد > sysfs حسب
سياسة البائع (يُتوقَّع الرفض) > محجوب (`EXPECTED_DENIED`).

---

## 5. مصادر جديدة (S18… / A06… / L07…) — تُضاف إلى `01-SOURCES.md`

**الرخصة: لم تُفتح ملفات الرخص هذه الجولة؛ لا استيراد كود أو جدول أو مسار — مرجع معماري فقط.**

| ID | المصدر المُقاس | الإسناد | ما يثبته | الموقف |
| --- | --- | --- | --- | --- |
| **S18a** | AOSP `system/sepolicy` `private/app.te` | blob `3219fbe9623f4b0a6a68da525534cbb85ee39cd3` (`refs/heads/main`)، 2026-09-20 | `sysfs_gpu` ممنوح للتطبيقات؛ `proc_meminfo` ممنوح ومُعلَّم بالتقاعد؛ فتح مجالات CPU للجميع | **اعتماد كقاعدة توفّر** (R/A) لا ككود |
| **S18b** | AOSP `private/app_neverallows.te` | blob `434fb132e7adb545dc511564ca3b631ec617115a` | كل قائمة R أعلاه حرفيًّا | **اعتماد كرفض موثَّق** |
| **S18c** | AOSP `private/domain.te` | blob `6999586eaf09978949b1ab3fce5bba738870c47a` | `r_dir_file(domain, sysfs_devices_system_cpu)`؛ `proc_cpuinfo`؛ حدود cgroup/debugfs/selinuxfs | اعتماد |
| **S19** | AOSP `device/google/gs201/powerhint.json` | commit `a1deb18`، blob `131af2098740f22438f777a27c848ca60e474cdb` | **كتالوج عُقَد لكل جهاز**: `Name`/`Path`/`Values` + `Type: "Property"` + `ResetOnInit` — ويُثبت أن نفس المفهوم (سقف عنقود) يقع في مسارات مختلفة لكل SoC، وأن نصف هذه العُقَد **مقابض كتابة** يملكها PowerHAL | **اعتماد كمصدر فجوة G-06 وقاعدة quirks**، بلا استيراد أي مسار |
| **S20** | Chromium `gpu/config/gpu_driver_bug_list.json` | blob `b6c228eaa074f0122baec1510617eba50f48aef0` (`refs/heads/main`) | قاعدة استثناءات ناضجة: كل مدخل `id` + `description` + `cr_bugs` + مطابقة على `gl_vendor/gl_renderer/gl_version/driver_version/os` + `features`/`disabled_extensions` + `exceptions` | **نموذج معماري لقاعدة الـquirks** (I-04) |
| **S21** | libinput device quirks / `90-libinput-model-quirks.hwdb` | مستندات libinput 1.31 + ملف hwdb (لم أفحص الإصدار المثبّت) | قاعدة quirks نصّية مستقلة عن الكود، قابلة للتحديث والمجاوزة محليًّا، مع أداة تشخيص (`libinput quirks list`) | نموذج لـ«قاعدة قابلة للصيانة + أداة تشخيص» (I-04/I-13) |
| **S22** | مشروع `Android-Thermal-Monitor` | GitHub `saschabrunner/Android-Thermal-Monitor` (README) | «some devices don't allow access to thermal sensors without root, and some devices don't even expose them at all» | **دليل استقلالي** على أن الجهازية واقع لا نظريّة (G-09) |
| **S23** | `google/battery-historian` | GitHub `google/battery-historian` (README/analyzer) | خط **تقرير جهاز → تحليل**: يبدأ من bugreport ويتحول إلى رؤية | نموذج لـI-13 (بلاغ → fixture) |
| **S24** | Linux `Documentation/ABI/testing/sysfs-driver-ufs` + patch UFS health | `lkml` patch v1 4/9 `ufs: sysfs: health descriptor`؛ ABI: `life_time_a/b`, `pre_eol_info` (قراءة فقط) | سطح صحة تخزين مقروء **إن** سمحت اللاصقة/الأذونات، ويُقاس | مرشّح لبذور STORAGE مع `expectedAvailability = DEVICE_DEPENDENT` |
| **S25** | Linux `Documentation/ABI/testing/sysfs-class-powercap` | `android.googlesource.com/kernel/common` بأثر `d4504d1eba95` | `energy_uj`: عدّاد طاقة تراكمي (ميكروجول) عند وجود powercap/ODPM | **مرشّح مشروط** — لا دليل على إتاحته لتطبيق على أندرويد ⇒ `DEVICE_DEPENDENT` وقياس مطلوب |
| **A06** | `android.os.Build` — `SOC_MANUFACTURER`/`SOC_MODEL` | developer.android.com (أُضيف في API 31) + `get_prop(domain, soc_prop)` (S18c) | هوية SoC **بعامة** بلا ملف | **اعتماد فوري** لـ`vendorHints()` (I-01) |
| **A07** | `android.os.BatteryManager` — APIs صحة البطارية من Android 14 (عدد دورات الشحن، حالة الشحن، حالة الصحة) | developer.android.com + تغطية Android 14 | قيمة صحّة بطارية **بعامة** بديلة عن عُقَد البائع | **اعتماد** لبذور POWER |
| **A08** | AOSP «Thermal mitigation» | source.android.com/docs/core/power/thermal-mitigation (2026-09-20) | حالة الحرارة للتطبيقات = أكواد 0..6 عبر `PowerManager`؛ `IThermal` للحساسات **لعملاء موثوقين**؛ cooling devices عبر `getCurrentCoolingDevices` للموثوقين؛ HAL هو المصدر المُعتمد لأي كبح | **اعتماد**: يقلب ترتيب مصادر THERMAL (I-12) |
| **A09** | Google Issue Tracker 37140047 | العنوان/المقتطف فقط (الصفحة JS) | «Android O … removing access to the »stat« pseudofile by its SELinux configuration» | تأكيد مستقل لـR-04 |
| **A10** | ADPF: `PerformanceHintManager` + «Thermal API» للألعاب | developer.android.com | الواجهات العامة الموجودة فعلًا | اعتماد |

---

## 6. أفكار (I) — مرتّبة بالقيمة ÷ (التكلفة × الخطر)

### الطبقة أ — عائد عالٍ، خطر منخفض، تخدم قواعد المشروع نفسها

**I-01 · الهوية والقدرة من الواجهات العامة قبل أي ملف**
`Build.SOC_MANUFACTURER/SOC_MODEL` (API 31) + `Build.HARDWARE/BOARD/ABI` + `Os.uname()` +
`PackageManager.getSystemAvailableFeatures()` (واحدة، تُرجع كل القدرات) → تُغذّي `vendorHints()`
و`AtlasDeviceIdentity`. السبب: قاعدة المشروع «لا ادّعاء قدرة من وجود ملف»؛ والعكس الأنظف: **ادّعاء
القدرة من إعلان المنصة**، والملف مجرد تأكيد. السابقة: S18c (`soc_prop`/`build_prop` مقروءة).
*القبول:* اختبار يثبت أن اختيار الكتالوج يتغيّر بهوية SoC **بلا أي قراءة ملف**، وأن هوية مجهولة تُبقي
المدخلات العامة (لا تُسقطها) — وهو سلوك موجود أصلًا في `candidates()` ويصبح الآن مُغذّى فعلًا.

**I-02 · رتبة مصدر في الكتالوج (Source Tier)**
أضف `availability: EXPECTED | DEVICE_DEPENDENT | EXPECTED_DENIED` (G-09) واربطها بـ§3/§4.
*القيمة:* يحوّل «الفشل» من عطب إلى **حقيقة موثّقة**، ويمنع إعادة اكتشاف R-01…R-09 كل جولة.
*القبول:* كل بذرة حالية تأخذ قيمة؛ واختبار يمنع `EXPECTED` على أي مدخل جذره في R.

**I-03 · قاعدة quirks مع إسناد وبصمة GPU**
النموذج من S20/S21: `AtlasQuirk(id, match, effect, sourceId, evidenceLink, revision)`، حيث `match`
تطابق (SoC، إصدار النواة، باني المبني، بصمة `GL_RENDERER/GL_VENDOR/GL_VERSION`)، و`effect` **يخفض
الادّعاء فقط**: «توقّع الرفض»، «الحقل مُدَّعى خطأً على هذا المزوّد»، «فضّل المصدر ب».
*القيمة:* هذا هو المكافئ لـ`gpu_driver_bug_list` و`hwdb` — **الطريق الواقعي** لتوسيع التوافق بلا مختبر.
*القبول:* اختبار يثبت أن أي `effect` من نوع «ارفع الثقة/القدرة» **مرفوض بنيويًّا** (لا يوجد عضو نوع يقبله).

**I-04 · بصمة GPU من EGL/GLES بدل التخمين**
سياق EGL خارج الشاشة مرة واحدة → `GL_VENDOR/GL_RENDERER/GL_VERSION/GL_EXTENSIONS` + حدود `GL_MAX_*`.
*القيمة:* هوية GPU + إصدار مشغّل + مجموعة الإضافات، **بلا جذر وبلا sysfs**، ويُطابق مباشرة مفاتيح S20.
*الخطر:* تكلفة إنشاء سياق؛ يجب أن تكون **اختيارية وكسولة** وتُحرَّر فورًا، وبخيط خلفي.
*القبول:* لا تُنشأ إلا عند طلب درجة «التفصيل»، وتُسجَّل تكلفتها في `AtlasAccessStats`.

**I-05 · قراءة `uevent` واحدة بدل عشرين**
`/sys/class/power_supply/*/uevent` يحمل خريطة `POWER_SUPPLY_*` كاملة (شحن كامل، دورة، حرارة، صحة…)
⇒ قراءة واحدة تُغذّي 10–20 ملاحظة. *القيمة:* تخفيض العمليات والبايتات بعشرة أضعاف مع نفس المعنى.
*التحذير:* لا اشتراك uevent (R-06) — استعلام دوري فقط. *القبول:* اختبار يُثبت أن عدد العمليات لم يتغيّر
بعدد الحقول، وأن كسر أي مفتاح لا يُفقد الآخرين.

**I-06 · `time_in_state` بدل التردد اللحظي (+ وحدة صادقة)**
`scaling_cur_freq` = «آخر ما طُلب»، لا التردد الفعلي. فارق عيّنتين من `stats/time_in_state` يعطي
**توزيع الإقامة** ومنه متوسط فعلي خلال الفترة. *القيمة:* أكبر تحسين جودة قياس في نطاق CPU.
*الشرط:* إغلاق G-07 (نحو تداخل) + مفهوم «زوج عيّنات» في الـcache لا قيمة مفردة.
*الحدّ الصادق:* `time_in_state` وحدته تِكّات السائق؛ **لا تُحوّل إلى ثوانٍ بلا دليل HZ** — وبما أن
`/proc/stat`/`/proc/uptime` محجوبان (R-04)، فالحكم الصحيح: **الوحدة `UNKNOWN`** والفرق يُعرض كنسبة
داخلية لا كتردد بالهرتز. هذا استخدام «الجهل المُعلن» في موضعه الأمثل.

**I-07 · `cooling_device*` = «مَن يكبح الآن» + ترتيب مصادر THERMAL**
قراءة `cooling_device*/type|cur_state|max_state` تعطي دليلًا مباشرًا على الكابح (cpufreq/gpufreq/bcl)،
وتُوضع **فوق** `thermal_zone` في ترتيب المصادر، مع `PowerManager.getCurrentThermalStatus()` كحقيقة
المنصة المُعتمدة (A08). *القبول:* مصفوفة الدعم تشرح `PARTIAL` بسبب «الحساسات محجوبة، الحالة العامة متاحة».

**I-08 · جداول errno → سبب، مكتوبة الآن ومختبرة قبل وجود المُهايئ**
`EACCES/EPERM→PERMISSION_DENIED`, `ENOENT→ABSENT (فقط إن عُدّ الأب)`, `EROFS→READ_ONLY`,
`ELOOP/ENOTDIR/ENAMETOOLONG→AMBIGUOUS/MALFORMED`, `EIO/ENODATA→UNKNOWN_CAUSE`,
`any-unknown→UNKNOWN_CAUSE` (وليس `ABSENT` أبدًا).
*القيمة:* يمنع المُهايئ المستقبلي من اختراع تصنيفه (وهو خطر حقيقي: أخطاء errno تُسطَّح عادةً إلى «فشل»).

### الطبقة ب — قيمة عالية مع تكلفة تصميمية

**I-09 · نموذج تكلفة وإيقاع ومجدول حزم** (يسدّ G-03): `cost(opens, bytes)`, `volatility(INSTANT/1s/10s/STATIC)`,
وخطط لكل تِك، مع دمج الحقول المتقاربة و**backoff** أسّي + jitter لكل `(path, cause)`.
*القبول:* اختبار يثبت أن عقدة مرفوضة تُسأل **مرة واحدة** خلال ٦٠ ثانية، وأن حقلًا STATIC لا يُقرأ أكثر من
مرة لكل إقلاع.
**الحالة بعد `P12`: جزئيًا — وهذا تفصيله.** ما نُفِّذ: نموذج التكلفة (`AtlasProbeCost(opens, bytes)`)،
وتصنيف التقلّب (`INSTANT/FAST/SLOW/STATIC`)، ومدد إعادة المحاولة **لكل سبب** (وجدول مضاعفة للعابر
١ث→٦٠ث)، والجدولة حتمية بأسباب تخطٍّ صريحة. وما **لم يُنفَّذ**: الدمج (coalescing) للحقول المتقاربة،
والـjitter، والإيقاع لكل تِك الآتي من المستهلك، وأن يُخطَّط مقابل **لوح المحاولات** — وهذا الأخير لا يجوز
أن يُبنى هنا لأن اللوح يُبنى للمهمة الواحدة ويُفقد عند كل جولة، والمستودع هو الذي يبقيه (`P5`).

**I-10 · سلّم الحداثة والذاكرة السلبية** (يسدّ G-02/G-04): `AtlasFreshness` لكل ملاحظة، وإحياء `STALE`،
وقاعدة: **فشل لا يُطيل عمر نجاح**، ورفض يُحفظ حتى تغيّر جيل الصلاحية.
**الحالة بعد `P12`: جزئيًا.** `AtlasFreshness` لكل ملاحظة ✓ · «فشل لا يُطيل عمر نجاح» ✓ **بنيويًّا**
(السجل لا يمسّ عمر نجاح أصلًا، فليس هناك ما يُطيله) · «رفض يُحفظ حتى تغيّر جيل الصلاحية» ✓ (والرفض
الشكلي والأخطاء لها جدولها) · **«إحياء `STALE`» ✗ — لم يحدث**، ومحلّه `P5` حين يخدم دليلًا محتفظًا به.

**I-11 · فصل «القدرة» عن «القيمة»** — القدرة (هذا السطح موجود ومقروء على هذا الجهاز) عمرها **حتى
الإقلاع أو تغيّر الصلاحية**؛ القيمة عمرها **ثوانٍ**. اليوم `AtlasDomainSupport.state` يُستنتج من
الملاحظات فقط، فيختلط الزمني بالثابت. *القبول:* لوحة قدرات مستقلة بمفتاح `(path, identity, boot, privilege)`.
**الحالة بعد `P10`+`P12`: نصف الفصل.** التمييز **موجود في نموذج العمر**: `AtlasVolatility.STATIC` لا
تنتهي بساعة بل بجيل، و`AtlasStaleness.SUPERSEDED_BY_BOOT/SUPERSEDED_BY_PRIVILEGE` يسمّيان السبب،
والهوية صار لها مفتاح (`privateCacheKey`) يحمل الأجيال. و**لوحة القدرات المستقلّة غير مبنيّة**: الحالة في
مصفوفة الدعم ما زالت تُستنتج من الملاحظات (مع `unverified-identity` كسبب تأجيل)، ومحلّ اللوحة `P11`.

**I-12 · الكشف عن قيمة مُجمَّدة/بديلة كـ**محور جودة** لا كإعادة كتابة**
`AtlasValueQuality { LIVE_CORROBORATED, LIVE_UNVERIFIED, FROZEN_SUSPECTED, CONTRADICTED }` تُحسب من
عيّنتين+ ومقارنة مصدرين، **والقيمة الخام تبقى**. *القيمة:* يمسك عُقَد البائع الثابتة (التي تُقرأ بنجاح
وتكذب بصمت) — وهو أخطر من الفشل لأنه يبدو صحيحًا. *الخطر:* إن صُنّف كحقيقة صار «معنًى مُرقّى»؛ لذا
`semanticStatus = INFERRED` إلزامي.

**I-13 · «طبيب أطلس»: تقرير ← fixture (أهم فكرة عملية)**
شاشة/تشغيل ذاتي يشغّل مجموعة المسابر، يعرض نتيجة كل عقدة وزمنها، ويُصدّر تقريرًا **مُنقّحًا بصيغة توافق
fixture**. بعد مراجعتك، يُسقَط الملف كما هو في `test/resources/atlas/`. السابقة: S22/S23 (تقرير جهاز →
تحليل). *القيمة:* يحوّل أكبر قيد في المشروع («لا جهاز في الحلقة») إلى **قناة نمو**؛ وكل بلاغ مستخدم يصبح
حالة اختبار دائمة. *القبول:* التقرير المُصدَّر يُحلَّل بنفس المشغّل الحقيقي (I-14)، ولا يُقبل fixture لا
يمر بالمنقّح.

**I-14 · مشغّل يعيد تشغيل fixtures عبر المنفّذ الحقيقي**
`ReadOnlyProbeAccess` حقيقي فوق ناقل يقرأ من fixture (لا نسخة ثانية من السياسة). يوجد اليوم `AtlasFakeTransport`
جزئيًّا؛ المطلوب: تحميل من ملف + حالات (مرفوض، بتر، مُجمَّد، مسار متداخل). *القيمة:* يمنع «نختبر شبيه الكود».

**I-15 · `AtlasInterfaceRole`** (يسدّ G-06): `OBSERVABLE | CONTROL_PLANE_OWNED | UNKNOWN`، والافتراضي
`UNKNOWN` **يخفض** الادّعاء. تُغذّى بالدليل من S19 (عُقَد يكتبها PowerHAL).

### الطبقة ج — إضافات صغيرة عالية الرافعة

**I-16 · مسار قانوني على الملاحظة** (يسدّ G-11): `canonicalPath: String?` + إسقاط التكرار في العمل الواحد.

**I-17 · `REPORTED` في درجات الثقة** (يسدّ G-12): شرط صريح ألّا تساوي `SOURCE_VERIFIED`.

**I-18 · توسيع القوالب المعتمدة** (يسدّ G-08): إضافة `/proc` ببذور `cpu.info.*` (مسموح صراحةً) مع
`note` التقاعد لـ`meminfo`، وإضافة `/proc/pressure` كـ«غير محسوم — يُقاس».

**I-19 · «ماذا سيغيّر هذا الجواب» في الشاشة**: لكل `DEFERRED/UNAVAILABLE` سبب قابل للنقل: «يحتاج صلاحية
ممنوحة»/«البائع لم يُبيّن العقدة»/«المنصة تحجب هذا السطح». *القيمة:* صدق موجَّه للمستخدم بلا وعود كاذبة.

**I-20 · أرشفة الرفض** (يسدّ G-13): كل سطح مرفوض يدخل الكتالوج بـ`EXPECTED_DENIED` + مرجع §3 ⇒ تُقرأ
كواقع لا كعطب.

**I-21 · إمضاء تقاطع مصادر الحراري**: حرارة البطارية (`BatteryManager`) ↔ `power_supply/temp` ↔
منطقة `battery`، وحرارة النظام عبر `getThermalHeadroom` ↔ مناطق `ap/tsens`. عند التخالف خارج تسامح:
`CONTRADICTED` ونطاق، **لا اختيار واحد**.

**I-22 · `Os.statvfs` و`TrafficStats`** كمسار مراجَع للتخزين والشبكة بدل ملفات محجوبة/مشكوكة (R-07)، مع
إبقاء `/sys/block/*/disksize` للـzram (ممنوح ضمن `sysfs_devices_system_cpu`? لا — `/sys/block` ليس
ممنوحًا صراحةً ⇒ يُصنّف `DEVICE_DEPENDENT`، وهذا تصحيح لازم للبذور الحالية `storage.zram.disksize`).

---

## 7. خطط مقترحة (P10–P13) — تصبح جزءًا من الخطة عند موافقتك

| الخطة | الهدف | الطلبات | تُغلق |
| --- | --- | --- | --- |
| **P10 · الهوية والقدرة بعامة** | `AtlasDeviceIdentity` + تغذية `vendorHints` من `Build`/`Features`/`Os.uname` + بذور `/proc/cpuinfo` | A06/A07/S18c + I-01/I-18 | G-08/G-10 |
| **P11 · قاعدة الـquirks والإسناد** | `AtlasQuirk` + `availability` + `role` + `REPORTED` (كلها **تخفض** فقط) | S19/S20/S21 | G-06/G-09/G-12/G-13 |
| **P12 · الجدولة والحداثة** | تكلفة/إيقاع/backoff + TTL + ذاكرة سلبية + مشغّل fixtures حقيقي | I-05/I-09/I-10/I-11/I-14 | G-02/G-03/G-04/G-14 |
| **P13 · القياس على جهاز (الطبيب)** | تشغيل ذاتي + تقرير مُنقّح + أصل الرفض مُوثَّقًا | A08/S22/S23 + I-13/I-20 | G-09/G-14 |

**الترتيب الموصى به:** `P10` → `P12` (تكلفة/حداثة، بلا مُصرّف) → `P13` (يجمع الحقيقة من جهازك) →
`P11` (يبني القاعدة على بلاغات حقيقية لا على تخمين) → ثم `P5` → `P6` → `P7` كما في الخطة.
**تحديث (2026-09-20، بعد تكملة ٥٥):** `P5` و`P6` و`P7` و**`P13`** و**`P9.1`** سُلِّمت، فلم يبقَ من هذا
الترتيب إلا **`P11`** — وهي الآن مبنية على آلية حقيقية (`AtlasFixture` + `AtlasDoctor`): يُلتقط جهاز
مُعاد التشغيل ويُقارن بتقريره، فتُبنى القاعدة على بلاغات لا على تخمين. وما زال ناقصًا لقطة `DEVICE`:
كل ملف موجود اليوم أصله `HOST` من الاختبارات، ومكتوب عليه.
السبب: `P11` بلا `P13` سيكون **قاعدة تخمينات**؛ ومع `P13` يصبح قاعدة أدلة.

---

## 8. أوامر القياس على الجهاز (تُنقل إلى `P9.4` كما هي)

على جهازك، من `adb shell` (للمقارنة) ومن داخل التطبيق (للحقيقة). تُملأ النتيجة في `01-SOURCES` كصف
«قياس جهاز» لا كرأي:

```sh
# 1) هل يقرأ التطبيق فعلًا؟ قارن adb مع قراءة التطبيق لنفس الملف
adb shell 'cat /sys/class/thermal/thermal_zone0/temp; cat /sys/class/power_supply/battery/uevent'
# 2) الأسماء الحقيقية عندك (لا تفترض battery/battery0)
adb shell 'ls /sys/class/power_supply; for d in /sys/class/power_supply/*; do echo "$d $(cat $d/type 2>/dev/null)"; done'
# 3) الجهاز/SoC بلا أي ملف
adb shell 'getprop ro.soc.manufacturer; getprop ro.soc.model; getprop ro.board.platform; uname -r'
# 4) هل /proc/pressure متاح (موضع السؤال المفتوح)؟
adb shell 'cat /proc/pressure/memory; cat /proc/pressure/cpu'
# 5) أثر رفض SELinux: نفّذ القراءة من التطبيق ثم
adb shell 'dmesg | grep -i avc | tail -20'
```

**معيار القبول:** كل صف يُسجَّل بـ`measured-on-device` واسم الجهاز وبنية المنصة، أو يبقى `needs device`.

---

## 9. البوابات (ثابتة — لا بناء في هذه الجولة)

| البوابة | النتيجة المتوقعة |
| --- | --- |
| `python3 tools/code_health.py --assert` | exit 0 · الدَّين دون السقف |
| `python3 tools/i18n_coverage.py --assert` | exit 0 · 0 عوائق |
| `python3 tools/kt_balance.py` | 0 عوائق |
| `git diff --check` | نظيف |

**ما لا يُدَّعى:** لم يُشغَّل بناء ولا اختبار، ولا قياس جهاز، ولم تُفتح ملفات الرخص. كل حكم على سلوك
منصة أندرويد هو من **سياسة AOSP المقروءة**، وكل ما لمس «هل يقرأ تطبيقي هذا الملف على هاتفي» يبقى
`unverified — needs device` حتى §8.

---

## 10. سجل التغييرات

- **2026-09-20:** أُنشئت. تُجمع فيها: (أ) 14 فجوة مؤكدة بدليل من الشجرة، (ب) 9 أسطح **محجوبة بمصدر**
  كانت الخطة تظنّها قابلة للقراءة (`/proc/stat`, cgroup, debugfs, selinuxfs, uevent، …)، (ج) الوجه
  المقابل: قائمة ما هو **ممنوح فعلًا** (أهمها `sysfs_gpu` و`/proc/cpuinfo`)، (د) 10 مصادر خارجية جديدة
  بإسناد blob/commit، (هـ) 22 فكرة مرتّبة، (و) أربع خطط مقترحة `P10`–`P13`، (ز) أوامر قياس جهاز جاهزة.
  لا كود ولا بناء ولا تغيير في السلوك.
