# KNOWN_ISSUES

Open problems as of 2026-09-15. `P1` blocks the redesign, `P2` degrades quality, `P3` is debt to retire opportunistically. Each item names where it is fixed.

## Architecture / navigation

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-01 | P1 | Dual navigation models (`use_scroll_animation` → bottom-bar routes vs `main` pager) duplicate every nav concern | `MainActivity.kt` | NT-01 / ADR-01 |
| I-02 | P1 | 40+ string-literal routes registered inline in an 817-line activity; no typed registry | `MainActivity.kt` | NT-01 / ADR-02 |
| I-03 | P1 | Dead route aliases `maligpufreq`, `adrenogpufreq` both resolve to `GpuStudioScreen` | `MainActivity.kt` | NT-01 |
| I-04 | P1 | Max AI has no primary entry point despite being the product differentiator | route `maxai` | NT-01 → NT-02 |
| I-05 | P2 | Settings holds a primary slot but navigates to only 2 destinations | `SettingsScreen.kt` | NT-01 / ADR-03 |
| I-06 | P2 | Three competing Home implementations (`HomeScreen` 185 + `HomeDashboardComponents` 355 + `LegendaryHomeDashboard` 696 + `ui/component/HomeComponents` 1142) | file sizes | NT-01/NT-03 |
| I-07 | P2 | Root/module status re-probed on every route change and pager swipe; no app-level session state | `LaunchedEffect(rawRoute, pagerState.currentPage)` | ADR-17, own task |
| I-08 | P3 | Nine independent persistence surfaces, several read inside composition | see PROJECT_MAP §state | after NT-05 |

## Design system

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-10 | P1 | Only 6 of ~46 screens use `ui/design/`; 33 declare their own `Scaffold` | grep `Scaffold(` | NT-03 (per domain) |
| I-11 | P2 | Two design systems + two near-identical packages `ui/component/` and `ui/components/` | package listing | ADR-06 |
| I-12 | P2 | **ملفات ضخمة (رقم مقيس، لا أمثلة يدوية): `10` ملفات فوق 1000 سطر** — `AppMonitor` 1943 · `MaxAiEngine` 1616 · `MaxAiScreen` 1366 · `CustomThemeScreen` 1279 · `AppSettingsScreen` 1204 · `HomeComponents` 1143 · `ExpressiveListComponent` 1086 · `ActivitylauncherScreen` 1081 · `CpuCoreControlScreen` 1043 · `KernelFlasherScreen` 1007. موصولة الآن بسقف: `oversized_files` في `tools/code_health_baseline.json` | `python3 tools/code_health.py` | NT-12 |
| I-13 | P2 | Decorative engines (pulse field, ambient glow/motif, weather, video wallpaper, haze blur) cost frames/battery in a performance app | `ui/component/`, `ui/components/` | ADR-12 / NT-05 |
| I-14 | P3 | `MainActivity` defines its own `ExpressiveShapes` duplicating theme shapes | `MainActivity.kt` | NT-01 |

## Product truthfulness

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-20 | P1 | Knob ownership, verification results, and rollbacks are invisible in manual control screens | `core/hardware` vs `ui/subscreens` | NT-02 / ADR-09 |
| I-21 | P1 | No audit journal for Max AI actions in the UI | `MaxAiScreen.kt` (card-only) | NT-02 / ADR-10 |
| I-22 | P2 | Unmigrated screens show metrics without freshness/source; “unknown” handling is inconsistent | 33 screens | NT-03 / ADR-07 |
| I-23 | P2 | Capability vs state conflated outside the 6 migrated screens | 33 screens | ADR-08 |
| I-24 | P2 | High-risk tools (Terminal, SetEdit, ActivityLauncher, KernelFlasher) sit beside ordinary tweaks with no risk gate | `tweaks` list | NT-05 / ADR-16 |

## Localization / accessibility

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-53 | P2 | ~~الفرنسية ١٥٦٧/٢١٠٦~~ **مُغلق 2026-09-18**: الفرنسية **١٠٠٪ (٢١٠٦/٢١٠٦)**، ملفاتها الستة كاملة، `--assert` = ٠ عيب. أُكملت بتسع دفعات (صفر صف مرفوض في التسع) | `python3 tools/i18n_coverage.py --locale fr` | أُنجز |
| I-54 | P2 | ~~الإسبانية لم تبدأ~~ **مُغلق 2026-09-18**: الإسبانية **١٠٠٪ (٢١٠٦/٢١٠٦)** بعد تسع دفعات من ١٨٪، صفر صف مرفوض. **الألمانية ما زالت عند ١٨٪ (٣٨٠/٢١٠٦ · ناقص ١٧٢٦)** | `python3 tools/i18n_coverage.py --locale es` · `--locale de` | جزئي — الألمانية مفتوحة |
| I-51 | P1 | **٨١ لغة ناقصة ١٣٩٨٠٦ مفتاحًا** (من أصل ٨٤ لغة هدف؛ المكتملة ٣: `ar` · `es` · `fr`). الجهاز جاهز: `tools/i18n_translate.py --estimate` ثم `--provider <deepl\|google\|openai> --locales all`، والدمج مُتحقَّق (إضافة فقط + فحص وسائط). الناقص مفتاح المزوّد أو مترجم بشري (ADR-28) | `python3 tools/i18n_coverage.py` · `crowdin.yml` | مفتوح |
| I-52 | P2 | ~~تغطية العربية ١٣٢١/٢١٠٦~~ **مُغلق 2026-09-18**: العربية **١٠٠٪ (٢١٠٦/٢١٠٦)** وكل ملفاتها الستة كاملة، ٠ مفتاح مكرّر، `--assert` = ٠ عيب. تُرجمت داخل المستودع على ثلاث دفعات (٧٨٥ مفتاحًا) بمصطلحات المشروع المعتمدة | `python3 tools/i18n_coverage.py --locale ar` | أُنجز |
| I-30 | P1 | ~~New design strings exist only in `values/`~~ **مُغلق 2026-09-18**: أُنشئ `values-ar/max_screen_strings.xml` (١٧٧ مفتاحًا) و`values-ar/max_design_strings.xml` (١١)، وكل ملفات النصوص الستة صار لها نظير عربي (تحقق: ٠ مفقود، ٠ زائد، ٠ اختلاف في وسائط `%n$`). الباقي هو تغطية `strings.xml` (١٦٢٩ EN مقابل ٨٤٤ AR) وهي مهمة Crowdin | `docs/ai/VALIDATION.md` §3 (ثلاث بوابات جديدة) · §3.1 (بوابة اللغات) | أُنجز |
| I-31 | P2 | **نصوص واجهة صلبة: `62` موضعًا مقيسة** (`Text("…")` بنص حرفي، بلا قوالب `$` ولا رموز فقط) في شاشات Compose غير مُرحَّلة. مُجمَّدة بسقف `hardcoded_ui_literals`، و**السقف خُفِّض ٦٣ ⇒ ٦٢ في ٢٠٢٦-٠٩-٢١** عند نقل نصوص قسم الحرارة/GPU في شاشة التطبيق إلى `strings.xml` | `python3 tools/code_health.py` | NT-12 |
| I-32 | P2 | No UI/instrumentation tests at all; a11y (state descriptions, touch targets) enforced only by convention in `ui/design/` | test dirs | NT-07 (static test) |

## Control plane (ADR-11)

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-55 | P1 | **`23` كتابة مباشرة من طبقة العرض** (خُفِّض السقف ٢٧ ⇒ ٢٣ وقيست القيمة في ٢٠٢٦-٠٩-٢١) في **١٢ ملفًا** بدلًا من الـarbiter — فالعقود والـrollback وحق النقض الأمني لا تشمل هذه المفاتيح. التوزيع مقيس: `viewmodel/ZramViewModel` ١٠ · `ChargingViewModel` ٣ · `TweakViewmodel` ٣ · `subscreens/BypassChargeScreen` ٢ · `BypassCheckScreen` ٢ · وواحد لكل من `mainscreens/GetStartedScreen` · `SettingsScreen` · `subscreens/ZramManagerScreen` (**كتابة من داخل شاشة** — الأخطر) · `viewmodel/AppSettingsViewmodel` · `DisplayStudioViewModel` · `NetworkSchedulerViewModel` · `TouchBoostViewModel`. **تنبيه للتصنيف**: ليس كلها عقد عتاد — بعضها ملفات إعداد في `/data/adb/.config/MaxManager/`، وهي آمنة وظيفيًا لكنها تنتمي لنفس الترحيل. **دَين سابق معروف نصًّا** (`VERIFICATION_NT01.md`: «١٩٩ موضعًا مباشرًا في `ui/**`، ADR-11 للكود الجديد») لكنه لم يكن مقيسًا ولا مفروضًا | `python3 tools/code_health.py` → `presentation_hw_writes` | NT-13 — مُجمَّد عند ٢٧ حتى الترحيل |
| I-57 | P2 | **التحكّم الحراري اليدوي (policy · أجهزة التبريد) بلا شاشة**: `ThermalDevicesViewModel` يكتب `thermal_policy` و`cooling_device*/cur_state` **ولا مستهلك له في أي شاشة** (صفر مرجع خارج ملفه)، و`ThermalDetailScreen` (٤٧٠ سطرًا) **يقرأ فقط**: يعرض المناطق وأجهزة التبريد ولا يغيّرها. الأثر الحقيقي: طبقة `MANUAL` في نطاق الحرارة لا تُنتَج اليوم إلا من `CpuCoreControlViewModel`، وقفل جهاز التبريد الذي يتخطّاه `PlatformCeilingAuthority` لا يمكن للمستخدم أن يضعه أصلاً | `grep -rn "ThermalDevicesViewModel" main/` · `grep -n "setCoolingState\|setThermalPolicy" ui/` | قرار: إظهار التحكّم في `ThermalDetailScreen` أو حذف الـVM |
| I-56 | P2 | **`29` استيرادًا شاملًا لكود المشروع** (`import nd.max.ui.*`) يُخفي مصدر الرمز عن أي قارئ — أهمها `MainActivity` (٣ حزم) و`MaxNavGraph` (٣) و`SettingsScreen` (٣). ٢٢٨ استيرادًا شاملًا لمنصّة أندرويد باقية **متعمّدة** (نمط Compose) | `python3 tools/code_health.py` | NT-12 — سقف ٢٩ |

## Environment / process

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-58 | P2 | **١٤٥ اختبارًا في `:terminal-emulator` لا يشغّلها CI**: `build.yml` يستدعي `:app:testDebugUnitTest` فقط، والموديول يحمل ١٩ ملف اختبار وتبعية JUnit معلَنة في `terminal-emulator/build.gradle:33`. تشغيلها يدويًا نجح في **22 ثانية** و**0 فشل** — أي أن إضافة سطر واحد للـCI تنقذ تغطية كاملة كانت ميتة | `grep -n gradlew .github/workflows/build.yml` مقابل `find terminal-emulator/src/test` | NT-14 (مقترح) |
| I-59 | ~~P3~~ | ~~keystore بلا قاعدة تجاهل~~ **مُحدَّث 2026-09-18**: كلمة المرور كانت مفقودة، فوُلِّد keystore جديد (`alias=azenith_key`، RSA 4096، صالح حتى 2054-02-03) وبُنيت نسخة **موقّعة وناجحة** منه. **إبقاء الملف في المستودع قرار مقصود**: `build.yml` يقرأ `app/azenith.jks` للتوقيع، فالحدّ الأمني هو **كلمة المرور في سر `KEYSTORE_PASSWORD`** لا الملف. البديل الأكثر صرامة: حذف الملف من المستودع وفكّ ترميزه من سر base64 في CI. **الأثر على المستخدمين**: بصمة الموقّع تغيّرت، فمن ثبّت نسخة قديمة **لا يستطيع الترقية فوقها** (يلزم إلغاء تثبيت أولًا) | `apksigner verify --print-certs` = `72e335af…0fc0` · `keytool -list` · تحديث `EXPECTED_RELEASE_SIGNER_SHA256` في `build.yml` | مُنجز (I-59 مغلق بقرار موثَّق) |
| I-57 | P2 | ~~`AGENTS.md` غير متعقّب + ملف عالم محلي يلوّث `git status`~~ **مُغلق 2026-09-18**: `AGENTS.md` (نقطة دخول كل وكيل) كان **غير متعقّب وغير متجاهَل** أي أنه يضيع في أي استنساخ جديد — صار متعقّبًا. و`.maxmanager-sync-root` (فارغ، محلي) أُضيف إلى `.gitignore` فخرج من الضجيج. وبوابة `stray_root_file` تكشف أي ملف جذر جديد غير متعقّب وغير متجاهَل | `python3 tools/code_health.py` | أُنجز |

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-40 | ~~P1~~ | ~~تعذّر البناء هنا~~ **مُغلق 2026-09-18 (جولة ١٠)**: نُزِّل Android SDK في `~/android-sdk` (`platform-tools` 37.0.1 + `platforms;android-36` + `build-tools;36.0.0`) مع JDK 17، والبناء **نجح**: `:app:testDebugUnitTest :app:assembleDebug` = BUILD SUCCESSFUL 5m54s · **128 اختبارًا 0 فشل** · APK موقّع (v2) · و`:terminal-emulator` **145 اختبارًا 0 فشل** · وR8/تقليص الموارد نجحا. **المتبقي (لا يُدَّعى)**: توقيع `release` (يحتاج سر CI) وسلوك تبديل اللغة على جهاز حقيقي | مخرجات Gradle الحرفية في `VALIDATION.md` §0 | أُنجز |
| I-41 | P2 | Redesign work is uncommitted/untracked (`ui/design/`, new string files) — easy to lose | `git status` | commit early in NT-01 |
| I-42 | P2 | ~~`manager/FINAL_UI_AUDIT.md` references screens that no longer exist~~ **مُغلق 2026-09-18**: أُضيف شريط «مُتجاوَز» في أعلى `FINAL_UI_AUDIT.md` و`CHANGED_FILES_FINAL_UI.md` يحيل إلى `docs/ai/` و`.planning/codebase/` | file vs tree | أُنجز |
| I-43 | P3 | `AppMonitor.kt` fails naive brace-balance checks (pre-existing lexer artifact, identical to HEAD) — do not “fix” in UI work | aegis checkpoint | ignore, baseline |
| I-44 | P3 | `manager/kernel-flasher` is a vendored fork with its own theme/type files — duplicate-looking files are expected | package `com.github.capntrips.kernelflasher` | leave alone |

## بعد NT-02 (Max AI)
- I-45: ~~تعذّر البناء محليًا~~ **مُغلق 2026-09-18 (جولة ١٠)**: السبب كان غياب Android SDK وذاكرة التبعيات فقط، لا إصدار Gradle ولا الشبكة. بعد التنزيل بإذن المالك نجح البناء والاختبارات (التفاصيل في I-40 و`VALIDATION.md` §0).
- I-50: `KernelFlasherScreen.kt` يحمل ٨ مسارات حرفية في تنقّله الداخلي خارج `ui/navigation` (الأسطر ٥٣٧، ٥٤٢، ٥٦٢، ٥٧٠، ٥٧٧، ٥٨٨، ٦١٩، ٦٥٠) — نفس عيب F-01 لكن في شاشة عالية الخطورة (تلمس الأقسام والأقسام الاحتياطية)؛ لم تُلمس بلا بناء. تكشفه بوابة §5(f) الجديدة.
- I-46: ~~أحكام السياق لكل تطبيق غير معروضة~~ **مُغلق 2026-09-18 (NT-15-MAXAI)**: `KnobInsight.context` + `Snapshot.contexts/knobsByContext/knobsIn` + منتقي نطاق في الشاشة (ADR-31). العام لا يُخلط بخاص، واسم التطبيق يُقرأ من النظام مع سقوط آمن إلى اسم الحزمة.
- I-47: ~~لا بحث نصّي في الدفتر~~ **مُغلق 2026-09-18 (NT-15-MAXAI)**: `TimelineSearch` يبحث في الحقول الخام (لا النص المترجم) عبر `MaxSearchField` الجديد في `ui/design/`. فلتر الحكم من NT-06-I47-PERF قائم فوقه، والاختبارات الأربعة تُشغَّل مع `:app:testDebugUnitTest`.
- I-48: ~~لا زر لمسح الدفتر~~ **مُغلق 2026-09-18 (NT-15-MAXAI)**: صف في سطح التحكم + تأكيد يسمّي الأثر، والمسح لا يمسّ الأقفال ولا الملكية ولا خرائط التعلّم، ويُوثَّق في `EventLog`.
- I-49: ~~`MaxAiInsightsTest` لم يُكتب~~ **مُغلق 2026-09-18 (NT-15-MAXAI)**: كُتب **وشُغِّل** (160 اختبارًا، 0 فشل) مع `MaxAiCadenceTest` و`MaxAiTimelineSearchTest`.
- I-60 (P2، جديد): **تدقيق المحرك على نفسه ووتيرة إعادة التخطيط غير مُتحقَّقين على جهاز.** العرض الفعلي لمنتقي النطاق وكبسولة المعايرة وشريط البحث وعدّاد الوتيرة (RTL/خط كبير/حالة شاشة مطفأة) لا يُثبته إلا تشغيل. الإثبات المتاح اليوم: التجميع + 160 اختبار JVM + APK. **المسار:** تشغيل على جهاز وتسجيل النتيجة في `VALIDATION.md` §0.
- I-62 (P2، جديد): **ثوابت المنحنى الحراري (`MaxAiThermalCurve`) غير معايَرة على جهاز.** كسب البند التكاملي `0.0015`، والأرضية `0.25`، وخطوة التقريب `0.05`، وحد الكتابة `15s`، و`releaseConfirmSamples = 3` — كلها قيم تصميمية مبنية على مبادئ تحكم مُوثَّقة لا على قياس على هذا الجهاز. **ما هو مُثبَت:** أن المنحنى لا يعطي سقفًا أخفّ من القاعدة القديمة (١٤ حالة JVM). **ما ليس مُثبَتًا:** مقدار الاستفادة الحرارية ولا كلفتها على الأداء ولا زمن الاستقرار. **المسار:** قياس على جهاز ثم تسجيل الأرقام، أو استبدال الكسب بمعايرة من `ResponseModel` (XR-04/NT-24).
- I-63 (P3): **حدود البحث الخارجي معلنة ولا تُنسى.** `docs/ai/EXTERNAL-RESEARCH.md` بُني على ~٢٠ استطلاعًا و٦ قراءات مرجعية كاملة. كل فكرة غير منفذة فيه **مقترح لا ميزة**، واستكماله موصوف في §٩ منه. **تصحيح 2026-09-18:** كان فيه ادّعاء أن أداة فهرسة المستودعات **غير متاحة** في هذه الجلسة — وهذا **خطأ مني**: الواجهة البرمجية تعمل، ونُفِّذت بها فهرسة **٥٧ استعلامًا ⇒ ٢٥٤٦ مستودعًا فريدًا** في `EXTERNAL-RESEARCH-APP.md` §١٣. الادّعاء القديم بقي في مكانه في §٩ مع تصحيح بجانبه (لا يُمحى الخطأ بل يُصحَّح).
- I-65 (P2، جديد): **مسار قراءة PSI غير مُجرَّب على جهاز، وعتبة الخنق غير معايَرة.** ما هو مُثبَت: المحلّل النقي على نصوص PSI حقيقية، وسياسة الاستقصاء، ودعوى «المنع لا يرفع أبدًا». ما ليس مُثبَتًا: أن `/proc/pressure/memory` مقروء على الإصدارات المستهدفة (بلا جذر قد يكون محجوبًا، وإن رُفض مرة يُعلن `UNSUPPORTED` عشر دقائق)، وأن `full avg10 = 10٪` هو العتبة الصحيحة لهذه الأجهزة، وأن رسم «توقف الذاكرة» يظهر صحيحًا في RTL/خط كبير. **المسار:** تشغيل على أجهزة بنواكين مختلفين وتسجيل النتيجة في `VALIDATION.md` §0.
- I-64 (P2، جديد): **ثوابت ميزانية الحرارة غير معايَرة، وتعريف "حمل ثقيل" مُشتق لا مقيس.** `MaxAiThermalBudget.MIN_SESSION_MS = 30s` و`DEMANDING_INTENT = 0.75` قيم تصميمية؛ والأهم أن "حمل ثقيل" يأتي من `appIntent` المُقدَّر لا من إطارات/FPS حقيقية — فجلسة تُصنَّف خطأً (لعبة خفيفة، أو تشغيل في الخلفية) تُنتج رقمًا يبدو دقيقًا وهو خطأ. **المسار:** NT-19/NT-23 (PSI وتصنيف العنق) ثم معايرة على جهاز، وتسجيل الأرقام في `VALIDATION.md` §0.
- I-61 (P1، جديد): **مراجعة سلامة Luna على تغيير `core/maxai` معلّقة.** `AGENTS.md` §2 يمنع إغلاق أي تغيير في `core/maxai` بلا حكم سلامة من عائلة نموذج مختلفة. الحالة: الدفعة مُجمَّعة بلا مراجعة مستقلة. **المسار:** NT-16 (تسليم الإطلاق في `AGENTS.md` §4).

## من جولة البحث على مستوى التطبيق (`docs/ai/EXTERNAL-RESEARCH-APP.md` §1) — 2026-09-18

كل بند هنا **دليل بأمر**، لا انطباع: سلسلة الاستدعاء كاملة مذكورة في الملف.

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-66 | **P1** | **تناقض معلن: خط أحمر مكتوب ومفتاح مُوصَّل يخالفه.** `EXTERNAL-RESEARCH.md` §٧ يسجّل `XR-R4` («تعطيل تخفيف المنصة») كفكرة **مرفوضة** بنص AOSP: «لا يجب أن تعطّل وظيفة التخفيف الحراري». وفي المقابل يوجد صف واجهة `disable_thermals` (MediaTek) يسلسل إلى كتلة `DISABLE THERMAL` التي تنفّذ فعلًا: `pkill thermald/thermal-engine/mtk_thermal` · `thermal_zone*/mode=disabled` · `policy=userspace` · `chmod 000` على `temp`/`trip_point_*` · تعطيل سياسات MTK PPM (`THERMAL`/`PWR_THRO`/`FORCE_LIMIT`) · `ignore_thermal_protect` لـGPU · `cmd thermalservice override-status 0`. وباب ثانٍ منفصل في `ThermalUtil.setZoneEnabled`. **الأثر على الصدق أيضًا:** عتبات `SafetyEngine` (48/52) تُقرأ قراءة مضلّلة إذا كان التخفيف الحراري مُعطَّلًا | السلسلة كاملة (مُتحقَّق سطرًا سطرًا): `PreferencedTweakScreen.kt:320` (قراءة `:168`، كتابة `:327`) → `MaxManagerProps.kt:67` (`persist.sys.maxmanagerconf.DThermal`) → `props.sh:47` → `preferenced-tweaks.sh:34` ثم `:434` · وباب ثانٍ: `ThermalUtil.kt:245` (`setZoneEnabled`) | **NT-30** — يبدأ بقرار المالك ثم `safety-reviewer` ثم ADR. **لم يُعدَّل المفتاح** (قرار مالك لا قرار منفّذ) |
| I-67 | P2 | **هوية الإصدار مكتوبة في ثلاثة أماكن وتتفرّع، ومسار التحديث خامل.** `version` = `5.2Dazzling` · `update.json` = `"5.2 (1823-…)"` و`versionCode 1823` · `module.prop` = `V1`/`versionCode=1`؛ و`module.prop` **مشتقّ وقت التحزيم** (`compile_zip.sh` يكتب سطر `version=` ويقارنه `check_module_version()` في الخادم)، ومع ذلك `RootUtil.getModuleVersionCode()` يقرأ `versionCode` **منه**. و`update.json.zipUrl` و`module.prop updateJson=` **فارغان** ⇒ لا فحص تحديث ممكن أصلًا | `version` · `update.json` · `mainfiles/module.prop` · `RootUtil.kt:77` · `build.yml:52-68` | **NT-32** |
| I-68 | P2 | **الإنقاذ والتعافي يحدثان بلا أن يعلم بهما أحد.** ① مكان تعافٍ يُكتَب ولا يُقرأ: `package-recovery.log` مُعرَّف في `service.sh:48` ويُكتب فيه، و**عدد قارئيه على مستوى المستودع = صفر** (لا في `mainfiles/**` ولا في التطبيق). ② مخرجات الإنقاذ غير مرئية: `post-fs-data.sh` يكتب `count.sh`/`disable` ويعدّل `description=` في `module.prop`، وبحث التطبيق عن `count.sh`/`module.prop.orig`/`bootloop` = **صفر نتيجة**. النتيجة: المستخدم يعلم بالأمر فقط إن فتح **تطبيق جذر آخر**. ③ تعافٍ حقيقي (حلقة ٣٠×٢ ثانية + `cmd package install-existing`) لا يُعرض ولا يُعَدّ | `mainfiles/service.sh:48,41` · `mainfiles/post-fs-data.sh:31-46` · grep في `manager/app/**` = ٠ | **NT-28** + **NT-29** |
| I-69 | ~~P3~~ | ~~حدود جولة البحث على مستوى التطبيق~~ **مُصحَّح ومُغلق جزئيًا 2026-09-18:** كان مكتوبًا أن **أداة فهرسة GitHub غير متاحة** — **وهذا ادّعاء خاطئ مني، لا قيد في البيئة.** واجهة GitHub البرمجية تعمل من هذه الجلسة (`/search/repositories`)، وقد نُفِّذت بها الموجة الثانية: **٥٧ استعلامًا ⇒ ٢٩٢٢ سجلًا ⇒ ٢٥٤٦ مستودعًا فريدًا** (الكون المُعلن ٣٩٥٦٣). التفاصيل في `EXTERNAL-RESEARCH-APP.md` §١٣. **المتبقي (لا يُدّعى):** الفحص كان على **الوصف/المواضيع** لـ٢٥٤٦ مستودعًا، والقراءة العميقة على مجموعة أصغر؛ وترتيب النجوم **مضلِّل** (استعلامات عريضة جلبت مستودعات غير ذات صلة بأعداد نجوم عالية). وما لم يُقرأ كوده بعينه (مثل حقول البطارية في `DashboardDetailScreens`) ما زال **«يُتحقَّق قبل التنفيذ»** | `docs/ai/EXTERNAL-RESEARCH-APP.md` §١٣ (جدول الحصيلة) | أُنجز + درس منهجي مسجَّل |
| I-70 | P2 | **درس الفهرسة المنهجي (يُقرأ قبل أي جولة بحث قادمة).** ① استعلام بكلمتين أعطى ٤٧٩٨ نتيجة، وبأربع كلمات تقنية أعطى **صفرًا** (`uclamp cpuset android scheduler`) لأن GitHub يطلب **كل** الكلمات ⇒ الفهرسة النافعة: **مواضيع ضيّقة (`topic:`) + كلمات قليلة**، ثم فرز بالوصف. ② الكون مُلوَّث: «android performance» جلبت منشورات سياسية وكتبًا بآلاف النجوم ⇒ **الفرز بالمفردات التقنية لا بالشهرة**. ③ أعلى مصدر عائد لم يكن مستودعًا بل **قائمتين منسَّقتين** (`awesome-shizuku` ١٠١٩٢★ · `awesome-android-root` ٤٦٨٥★) تعملان كأداة تعداد لمئات التطبيقات | `/tmp/ghq/` (فهرس مؤقت خارج المستودع) · §١٣ في ملف البحث | يُطبَّق في الجولة القادمة |

## من جولة تنفيذ AR-20/AR-02 — 2026-09-18

- I-71 (**P1**، بيئة/أداة): **البناء يفشل بالإعداد الافتراضي للمُصرّف.** القيمة
  `kotlin.compiler.execution.strategy=in-process` في `manager/gradle.properties` تُسقط
  البناء بـ`java.lang.NoSuchMethodError` في
  `CompilerPluginRegistrar$ExtensionStorage.registerExtension` (مُسجِّل إضافة Compose)،
  ويفشل في `:kernel-flasher` — وحدة **لم نلمسها** — قبل أن يصل إلى `:app`. **الإثبات
  العكسي:** البناء ينجح بـ`-Pkotlin.compiler.execution.strategy=daemon` من سطر الأمر
  (`:app:compileDebugKotlin` = BUILD SUCCESSFUL، و`:app:testDebugUnitTest` = ٣٤٠ اختبارًا
  / ٠ فشل). أي أن العطب في استراتيجية التنفيذ داخل العملية لا في الكود. **لم نُعدّل
  `gradle.properties`** لأنه إعداد مشروع لا قرار منفّذ. **المسار:** قرار مالك (daemon، أو
  رفع ذاكرة Gradle) ثم إثبات في CI.
- I-72 (**P2**): **تنفيذ أوامر shell بامتياز Shizuku غير مُنفَّذ بعد.** طبقة الاتصال/الإذن
  والفهرس تعمل، لكن `Shizuku.newProcess` **خاصّ (غير عام)** في
  `dev.rikka.shizuku:api:13.1.5` — مُتحقَّق بـ`javap` على الـAAR المستخرج من ذاكرة Gradle.
  فالواجهة العامة تعطي: التوفر، طلب/فحص الإذن، `ShizukuSystemProperties`، والـUserService.
  **لذلك لا يُدَّعى أن أدوات طبقة SHIZUKU تُنفَّذ الآن** — تُعرَض كـ«أدنى طبقة تحتاجها»،
  والـUserService (`bindUserService` + AIDL) هو الخطوة التالية. **المسار:** تنفيذ
  UserService + اختبار على جهاز بـShizuku، وتسجيل النتيجة في `VALIDATION.md` §0.
- I-73 (**P2**): **`PrivilegePanel` وسلوك شاشة البداية عند مستخدم بلا جذر لم يُجرَّبا على
  جهاز.** المُثبَت: التجميع + ٣٤٠ اختبار JVM (منها `BootHistoryUtilTest` = ٦) + سليمة
  `ControlLayoutModelTest` بعد إضافة وجهة `privilege`. **غير المُثبَت:** الربط الفعلي
  بـShizuku على جهاز، وسلوك صفحة الامتياز الجديدة في RTL/خط كبير/شاشة صغيرة (محتواها
  طويل فعُزِل في قائمة قابلة للتمرير)، وأن `sys.boot.reason` و`/sys/fs/pstore` مقروءان
  فعلًا على إصدارات/مُصنّعين مختلفين.
- I-74 (**P1**، صحة إصدار/تحزيم — **اكتُشف أثناء تنفيذ `AR-04`، مُتحقَّق من الملفات لا من الذاكرة**):
  **المستودع يحمل `module.prop` بـمعرّفين ورقمَي إصدار متناقضين، والتطبيق كان يحكم على
  «هل هناك تحديث» بمقارنة رقمية بينهما.**
  - `mainfiles/module.prop`: `id=MaxManager` · `version=V1` · **`versionCode=1`**.
  - `android/kernelsu/module.prop`: `id=nees_maxmanager` · `version=v1.0.0-rc` ·
    **`versionCode=10000`**.
  - والأثر **ليس نظريًّا**: `MainActivity` كان يقرأ رقم وحدة بـ`grep` على
    `/data/adb/modules/MaxManager/module.prop` ثم يقارن `appVC < moduleVC` ليقرّر إظهار
    حوار «يوجد تحديث». مع `versionCode=1` لا يظهر الحوار أبدًا (`1 < 1` = خطأ)، ومع
    `10000` يظهر دائمًا مع أي APK متاح. **أي أن سلوكًا يرى المستخدم يتغيّر حسب أيّ نسخة
    من الوحدة رُكِّبت — من دون أن يعلم.**
  - **ما فُعل:** قارئٌ واحد (`VersionIdentity`) + قراءة `id`/`version` من الوحدة المركَّبة
    فعلًا + عرض **التطابق/الاختلاف** في شاشة صحة الوحدة بدل الصمت، وإزالة القارئ الثاني
    (`RootUtil.getModuleVersionCode` صار يفوّض للقارئ الواحد). ووُضع شرط `appVC >= 0` حتى
    لا يُبنى حكم على قراءة فاشلة.
  - **ما لم يُفعل عن قصد:** **لم أُعدّل أي `module.prop`** ولا سكربت تحزيم — تحديد أيّهما
    يُشحن قرار مالك + سلامة، وليس إصلاح وكيل منفّذ. **المسار:** قرار: `mainfiles` أم
    `android/kernelsu` هو المصدر؟ ثم توحيد المعرّف ونطاق `versionCode` **مع** رقم إصدار
    التطبيق (`versionCode = 1` في `build.gradle.kts`) — ثم إعادة تشغيل البناء وفحص
    I-71. حتى ذلك الحين: **الاختلاف ظاهر في الواجهة، ولا يُخمَّن أيّهما «الصحيح».**

## من جولة AR-GAP-01 (`Max Backup`) — 2026-09-18

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-75 | **P1** | **مسار الاسترجاع يكتب إلى `/data/data/<pkg>` ولم يُشغَّل على جهاز ولا راجعه مراجع سلامة.** أُضيف في `GAP-01`: `MaxBackupEngine.restore` يعيد تثبيت الـAPK (`pm install -r -d`) ثم يُوقف التطبيق قسرًا (`am force-stop`) ثم يفكّ الأرشيف فوق بياناته ثم يُصلح الملكية (`chown -R <uid>`) والسياق (`restorecon -RF`). الحماية القائمة: بوابة `restoreDecision` **ترفض بلا تحقّق كامل للبصمات**، وحوار تأكيد بصيغة صريحة، وتسجيل النتيجة. **لكنه بحكم طبيعته يمسّ بيانات المستخدم، ولم يُقَس.** | `manager/app/src/main/java/nd/max/ui/util/MaxBackupEngine.kt` (`restore`, `extractInto`) · `MaxBackupModel.restoreDecision` | **يحتاج `safety-reviewer` (Luna) + جهازًا** — لم يُغلق |
| I-76 | P2 | **`Max Backup` يعتمد على أدوات toybox بلا إثبات سلوكها.** `du -sk` · `find … | wc -l` · `tar -c -z` · `tar -x -z` · `sha256sum` · `stat -c %u` تُنادى كلها عبر `Shell.cmd`، ولم يُتحقَّق على جهاز أن هذه الأعلام مدعومة في كل إصدار وأن مخرجاتها كما توقّعنا. **ونتيجة القراءة الفاشلة تُعالَج بالتصميم**: حجم غير مقيس يبقى `null` (لا صفرًا)، وبصمة غير مقروءة ⇒ `UNVERIFIABLE` ⇒ **لا استرجاع**. أي أن الفشل مُعلَن لا صامت. | `MaxBackupEngine.directoryBytes` · `sha256Of` · `tarInto` · `extractInto` | يُقاس على جهاز؛ حتى ذلك الحين غير مُدّعى |
| I-77 | P2 | **ملفات أنشأها root داخل مجلدنا الخارجي: هل تُقرأ فعلًا من التطبيق؟** على أندرويد ١١+ يُرى تخزين التطبيق عبر FUSE؛ `tar` يُنفَّذ بـroot فيكتب ملفًا يملكه root. عالجناها بـ`chown <uid>:<uid>` + `chmod 600` + `restorecon` بعد كل أرشيف — **قياسًا على ما يفعله `LogUtil` بملفّه** — لكن **النجاح لم يُثبَت على جهاز**. وإن فشل، فالأثر **قراءة فقط** (`verify` و`restore` يفشلان بصراحة، لا يُفسدان شيئًا). | `MaxBackupEngine.adoptOwnership` · `ui/util/LogUtil.kt` (السابقة المتبعة) | يُتحقَّق على جهاز |
| I-78 | P3 | **`Max Backup` بلا تشفير، وقرار ذلك مكتوب في الواجهة لا مخفيًّا.** النظائر تعرض «تشفير»؛ ونحن لا نعرضه لأن مفتاحًا مشحونًا داخل الـAPK ليس سرًّا، فادّعاؤه أسوأ من غيابه. والمُقدَّم بدلًا منه **سلامة قابلة للفحص** (`sha256` لكل ملف، وتحقّق إجباري قبل الاسترجاع). وتشفير حقيقي يحتاج مفتاحًا يملكه المستخدم (كلمة مرور + KDF) — **غير منفَّذ** ومُعلَن في `HANDOFF.md`. | `MaxBackupModel.Manifest.encrypted` (يُكتب `false` دائمًا) · نصّ `max_backup_no_encryption_desc` (EN+AR) | قرار مقصود — لا يُغلق |

## من تكملة AR-GAP-01 (بيانات النظام) — 2026-09-18

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-79 | **P1** | **مسار كتابة جديد خارج مجلدنا: `/data/misc/wifi` و`/data/misc/bluedroid`.** استرجاع الواي‑فاي والبلوتوث يفكّ `tar` إلى `/` ليعيد الملف إلى مساره المسجَّل. الحماية القائمة: **حرس مسارات قبل الفكّ** (`tarEntriesAllowed` — كل مدخل في `tar -t` يجب أن يطابق مسارًا مُعلَنًا للفئة بالضبط، والرفض قبل الكتابة)، وفحص بصمة إلزامي، وحوار تأكيد. **لكنه يمسّ إعدادات نظام، ولم يُقَس على جهاز، ولم يراجعه مراجع سلامة.** | `MaxBackupSystemEngine.tarEntriesAllowed` · `restore` · `MaxBackupSystem.SOURCES` | **يحتاج `safety-reviewer` (Luna) + جهازًا** — لم يُغلق |
| I-80 | P2 | **مفاتيح التطبيع (الدمج) غير مُختبَرة على بيانات حقيقية متنوّعة.** جهاتٌ بلا رقم · أرقام بخدمات قصيرة · رسائل متطابقة النص في اللحظة نفسها · أرقام محوّلة. المحرّك **لا يُنشئ تكرارًا للأرقام المُطبَّعة** (آخر ٩ خانات) ويُبلّغ عن الصفوف التي **بلا مفتاح** بالعدد بدل ادّعاء عدم التكرار — لكن ذلك لم يُقس على جهاز فيه آلاف الصفوف. | `MaxBackupSystem.normalizeNumber` · `mergePlan` · `MaxBackupSystemTest` | يُقاس على جهاز |
| I-81 | P3 | **مسارات ملفات النظام المُعلَنة لم تُتحقَّق على الجهاز المستهدف.** نجرّب مسارَي أندرويد ١٤ (`/data/misc/apexdata/com.android.wifi/`) وما قبله (`/data/misc/wifi/`)، و**الجرد يقول أيّها وُجد فعلًا** ولا يَدّعي نجاحًا. لكن المسارات قائمة مُعلَنة، وليست مقيسة. | `MaxBackupSystem.SOURCES` (`Kind.WIFI`, `Kind.BLUETOOTH`) | يُتحقَّق على جهاز |
| I-82 | P3 | **فئة «الأرقام المحجوبة» أُزيلت عن قصد لأنّها وعد لا يمكن الوفاء به.** `READ_BLOCKED_NUMBERS`/`WRITE_BLOCKED_NUMBERS` ليست في `Manifest.permission` العام (`javap` على `android-36/android.jar`) لأنها `signature|privileged` في AOSP ⇒ تطبيق عادي لا ينالها أبدًا، فكانت الفئة ستُعرَض «تحتاج إذنًا» للأبد. أُزيلت بدل شحنها، وسُجّل هنا حتى لا يُعاد إضافتها بالخطأ. | `docs/ai/HANDOFF.md` §«AR-GAP-01 (تكملة)» · `javap -p -constants` على `android.jar` | قرار مقصود — لا يُغلق |

- **I-83 — قراءة إضاءة البيئة كانت تفشل دائمًا (أُصلحت، وتنتظر تأكيدًا على جهاز).**
  `core/hardware/ContextData.kt` كان يسجّل مستمع الضوء ثم يُلغيه في الكتلة نفسها ⇒ لا حدث قطّ
  والقيمة `0f` أبدًا، أي «مظلم» بدل «لم أقرأ». صارت القراءة بمهلة، والقيمة `null` عند العجز.
  وأسوأ ما فيه أن `AndroidContextDataSource` **غير مُستهلَك** (DI يوفّره ولا أحد يحقنه)، فالعطب
  كان كامنًا. **يحتاج:** جهازًا لقياس زمن وصول أول حدث، وحكم سلامة لأن الملف في `core/hardware`.

- **I-84 — شاشة GPU كانت تُخرج التطبيق عند فتحها (أُصلح).**
  السبب: `viewModel()` بدل `hiltViewModel()` مع ViewModel له `@Inject constructor(arbiter)` بلا
  مُنشئ بلا وسائط ⇒ `RuntimeException: Cannot create an instance of class GpuStudioViewModel`.
  وكان **الوحيد** من أربعة ViewModels مُحقونة يخرج عن القالب، و**الوحيد** الذي ينقصه
  `@HiltViewModel` أيضًا. أُصلح الاثنان، وأُضيف `ViewModelInstantiationTest` يمنع تكرار النوع.
  **يحتاج:** تشغيل الشاشة على جهاز للتأكد أن محتواها (devfreq · الشرائح · الحفظ) يعمل كذلك.

- **I-85 — كراش شاشة SetEdit عند التمرير (أُصلح مرشَّحه الأقوى، ويحتاج تأكيدًا على جهاز).**
  القائمة كانت تبني مفاتيح `LazyColumn` من مخرج shell خام بلا تنقية، وCompose يرمي عند تكرار
  مفتاح — **أثناء التمرير لا عند الفتح**. صار التفرد مضمونًا (`parseOutput` + `dedupe` +
  `SetEditItem.lazyKey`)، وصار التكرار يُسجَّل باسم `duplicate_keys` بدل أن يُسكت عنه.
  وكُشف عطب مصاحب: مفتاح فارغ كان يُقبل من `"="` و`"[]: [v]"`.
  **يحتاج:** إن بقي الكراش ⇒ سطر `logcat` واحد لتحديد السبب الحقيقي.
