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
| I-31 | P2 | **نصوص واجهة صلبة: `80` موضعًا مقيسة** (`Text("…")` بنص حرفي، بلا قوالب `$` ولا رموز فقط) في شاشات Compose غير مُرحَّلة. مُجمَّدة بسقف `hardcoded_ui_literals` | `python3 tools/code_health.py` | NT-12 |
| I-32 | P2 | No UI/instrumentation tests at all; a11y (state descriptions, touch targets) enforced only by convention in `ui/design/` | test dirs | NT-07 (static test) |

## Control plane (ADR-11)

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-55 | P1 | **`27` كتابة مباشرة من طبقة العرض** في **١٢ ملفًا** بدلًا من الـarbiter — فالعقود والـrollback وحق النقض الأمني لا تشمل هذه المفاتيح. التوزيع مقيس: `viewmodel/ZramViewModel` ١٠ · `ChargingViewModel` ٣ · `TweakViewmodel` ٣ · `subscreens/BypassChargeScreen` ٢ · `BypassCheckScreen` ٢ · وواحد لكل من `mainscreens/GetStartedScreen` · `SettingsScreen` · `subscreens/ZramManagerScreen` (**كتابة من داخل شاشة** — الأخطر) · `viewmodel/AppSettingsViewmodel` · `DisplayStudioViewModel` · `NetworkSchedulerViewModel` · `TouchBoostViewModel`. **تنبيه للتصنيف**: ليس كلها عقد عتاد — بعضها ملفات إعداد في `/data/adb/.config/MaxManager/`، وهي آمنة وظيفيًا لكنها تنتمي لنفس الترحيل. **دَين سابق معروف نصًّا** (`VERIFICATION_NT01.md`: «١٩٩ موضعًا مباشرًا في `ui/**`، ADR-11 للكود الجديد») لكنه لم يكن مقيسًا ولا مفروضًا | `python3 tools/code_health.py` → `presentation_hw_writes` | NT-13 — مُجمَّد عند ٢٧ حتى الترحيل |
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
- I-46: `MaxAiInsights` يقرأ السياق العام `*` فقط؛ أحكام السياق لكل تطبيق غير معروضة بعد.
- I-47: خط الزمن بلا فلترة/بحث؛ عند ٨٠ حلقة قد يطول التمرير.
- I-48: لا زر لمسح الدفتر في الواجهة رغم وجود `MaxAiJournal.clear()`.
- I-49: `MaxAiInsightsTest` (JVM) لم يُكتب بعد.
