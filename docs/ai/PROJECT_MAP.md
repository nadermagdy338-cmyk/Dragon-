# PROJECT_MAP

خريطة MaxManager كما هو **اليوم** (٢٠٢٦-٠٩-٢١). المسارات نسبةً إلى جذر المستودع، وأكواد التطبيق تحت
`manager/app/src/main/java/nd/max/`.

كيف تُقرأ: §1 المركّبات · §2 العمليات (من يشتغل في أي عملية) · §3 الطبقات · §4 التنقّل · §5 مستوى
التحكّم (الأهمّ) · §6 مسارات القرار · §7 أين تعيش الحالة · §8 قنوات السجل · §9 التحقق · §10 ثغرات معلنة.

> النسخة السابقة (٢٠٢٦-٠٩-١٨) كانت تصف `ui/mtk/` و`TweakScreen` وشجرة مسارات `home|applist|tweaks|settings`.
> لا شيء من ذلك قائم الآن؛ أُعيدت كتابة الخريطة من الكود لا من السجلّ.

> **حالة النسخة المقروءة (مهمّ):** كل ما في هذه الخريطة مقيس على الشجرة **كما هي على القرص الآن**
> (آخر تعديل للمصادر ٢٠٢٦-٠٩-٢١ ١١:٥٧، ثم أُعيدت مزامنة المساحة من نسخة المستودع ~١٤:٣٣). أي أن
> إصلاحات جولة اليوم (مستوى `MANUAL` في السلّم · الطبقة الحراريّة المثبّتة · قفل مخزن Atlas · رموز
> السجل الجديدة) **ليست في هذه النسخة** — ولذلك لا يذكر §5 إلا ما هو موجود فعلًا.

## ١ · المركّبات

```
mainfiles/        وحدة Magisk/KernelSU: service.sh · post-fs-data.sh · action.sh · customize.sh · props.sh
archdaemon/jni/   الخفيّ الأصلي (C): ConfigHandler · InotifyHandler · PidTracker · StartupInit ·
                  SystemProfile/{SystemProfiles, ProfileUtility, PerAppKernel, PerAppThermal} ·
                  BypassCharge · GamePreload · ShellUtility · SystemLogger …
thermalcore/      Rust: التنبؤ الحراري · التبريد · مدير السياسة · التعلّم (يُبنى إلى libmaxmanager_native.so)
binutils/         Rust: أدوات الوحدة (main.rs + utils)
preloadbin/       مكتبة preload (Android.mk/Application.mk + main.c) — حقن الرمز (zygote)
binprofiles/      ملفات تهيئة النظام (bin profiles)
android/          aosp/ · kernelsu/ · overlay/ — تكامل المنصّة لا كود التطبيق
manager/          مشروع Gradle: app + kernel-flasher + terminal-emulator + terminal-view
docs/             ai/ (هذه الذاكرة) · aegis/ (سجلات أقدم)
tools/            بوّابات ثابتة بلا مُصرّف: kt_balance · code_health · i18n_coverage · repo_audit ·
                  log_gate (حزمة سجل جهاز) · sepolicy_matrix (تثبيت/وسم/مراجع SELinux) …
module.json version version_type update.json maxmanagerApplist.json crowdin.yml AGENTS.md
```

## ٢ · العمليات — من يشتغل في أي عملية

```
                       ┌──────────────────────────── Kernel / sysfs / props ───────────────────────────┐
                       │ cpufreq · devfreq(mali) · thermal* · workqueue · zram · charging · ged         │
                       └───────▲───────────────────────────────────────────────────────▲───────────────┘
                               │ كتابة/قراءة عقدة                            setprop · /proc
   ┌───────────────────────────┴──────────────────────────┐        ┌───────────────────┴──────────────────┐
   │ عملية الجذر: nd.max.AppMonitor (app_process، uid 0)   │        │ الخفيّ: sys.maxmanager-service (C)     │
   │  • mainfiles/service.sh تُشغّله مع:                   │        │  • يُطلقه service.sh كآخر أمر (exec)   │
   │    app_status · background_apps · java.lock           │◄──────►│  • يقرأ ملفات الإعداد ويراقبها ويردّ    │
   │  • يملك الحارس الحراري ومُحكِّم العتاد في هذه العملية  │ ملفات  │    (Inotify) ويطبّق البروفايلات        │
   │  • MtkRootService (libsu RootService، AIDL) ◄─────────┼──binder│  • --log · --clearlogs · --from-ai …   │
   │  • يكتب: app_status · background_apps · per_app_*     │        └────────────────────────────────────────┘
   └────────────────────────────┬──────────────────────────┘
                                │ ملفات الحالة + جسر AIDL (IMtkService: readNode/writeNode/nodeExists/listDirectories)
   ┌────────────────────────────┴──────────────────────────┐
   │ عملية التطبيق (UI): MainActivity + Compose           │
   │  • تقرأ ملفات الحالة وتكتبها (إعدادات/قوائم)          │
   │  • تكتب العتاد عبر RootIpcManager → MtkRootService    │
   │  • تحمّل libmaxmanager_native.so (PredictorBridge)    │
   │  • MaxAiEngine يعمل هنا (المحكّم Hilt @Singleton)     │
   └───────────────────────────────────────────────────────┘
```

ثلاث حقائق تشغيلية يُبنى عليها كل شيء:

1. **مُحكِّم العتاد كائن لكل عملية**، والتزامن بين العمليتين عبر **دفتر مشترك على القرص**
   (`SharedHardwareOwnershipStore`) — ولهذا يوجد `sharedTransaction` حول كل تعديل.
2. **الجذر ليس شرطًا للواجهة**: كل مسار عتاد له بديل `Shell`/`File` إن غاب الجسر
   (`RootIpcManager.ipc == null`), وكل كتابة تُقرأ بعدها ([WriteVerification]).
3. **القرار لا يُتخذ في الواجهة** (ADR-11): الشاشات تقرأ وتطلب؛ الكتابة في `core/**` أو في عملية
   الجذر.

## ٣ · الطبقات

```
nd/max/
  MainActivity.kt · MaxManagerApplication.kt · MaxManagerPaths.kt · MaxManagerProps.kt
  AppMonitor.kt          ← عملية الجذر: تطبيق per-app · حارس حراري · انحراف · استرجاع
  PerAppRefreshRateController.kt · RefreshRate.kt · XiaomiVendorFeatures.kt
  core/
    atlas/ (19)         كتالوج الجهاز ومسارات التحكّم: Discovery · Providers · Transports · Planner ·
                        RouteMemory (+Factory/FileStoreIo) · Adaptive/Repair Executors · Targets/Goals
    hardware/ (39)      مستوى التحكّم: HardwareControlArbiter · ControlOwnership · ManualControlLocks ·
                        SharedHardwareOwnershipStore · HardwareControlKey · Cpu/Gpu/Charging Backends ·
                        PerAppControlRegistry · ThermalGuard · ThermalCeilingRouter ·
                        PlatformCeilingAuthority · DriftGuard · HardwareVerification · PerAppHardwareStatus
    maxai/ (23)         MaxAiEngine · MinimalPlanner · Objective · SafetyEngine/Governor ·
                        ControlOutcomeModel · CredibilityStore · DynamicIntentLearner · ControlRegistry
    diagnostics/ (11)   DiagnosticCenter · DeviceBlueprint · LogCodeGlossary · LogEventLine/Header ·
                        LogSettingsDigest · AtlasDoctor · AtlasSupportReport
    privilege/ (3)      PrivilegeLevel · PrivilegeManager · ShizukuGateway
    jni/ (2)            ContextBridge · PredictorBridge (libmaxmanager_native + بدائل حتمية)
    di/ (2) · threading/ (1) · recommendation/ (2)
  service/              MtkRootService (uid 0 عبر libsu) · FpsOverlayService · ProcessOverlayService
  receiver/ · TileService/ (BypassCharge · Profile)
  data/datasources/
  ui/
    navigation/         MaxDestinations (الكتالوج القياسي) · MaxNavGraph · MaxNavBar · MaxNavActions
    mainscreens/ (18)   HomeScreen · LegendaryHomeDashboard · ControlScreen · ApplistScreen ·
                        MaxAiScreen · MaxLiveScreen · DiagnosticsScreen · SettingsScreen ·
                        DashboardDetailScreens · GetStartedScreen …
    subscreens/ (47)    كل شاشة ميزة (AppSettings · GpuStudio · CpuCoreControl · ThermalDetail ·
                        LogsViewer · FileManager · KernelFlasher …)
    viewmodel/ (24+) · util/ (EventLog · ProfilePresetStore · RootIpcManager · ThermalUtil …)
    component/ + components/ · design/ · theme/ · terminal/ · activitylauncher/ · flasher/ · process/
```

الأرقام المقيسة اليوم: **٤٠٠** ملف Kotlin في `main/`، **١٣٣** في `test/`، و`kt_balance` يحسب **٧٥٩**
ملفًا في المستودع كله بلا عوائق.

## ٤ · التنقّل

`MainActivity` يستضيف `MaxNavGraph`، والمصدر الوحيد للوجهات هو `MaxDestinations` (كتالوج بحقول:
`route` · `titleRes` · `icon` · `parent` · `risk` · `isPrimary`).

```
أربع أساسية (شريط سفلي):
  now        → HomeScreen              (لوحة الآن)
  control    → ControlScreen           (٩ hubs: cpu · gpu · memory · display · responsiveness ·
                                        thermal · power · storage · network، وكل hub يجمع أبناءه)    apps       → ApplistScreen           (ثم app_settings/{pkg} = شاشة التطبيق — ٢١ مفتاحًا في
                                        `updateSetting` + كتلة `cpu_policy_controls`)
  max_ai     → MaxAiScreen             (+ max_live)
ثانويّة: get_started · settings · diagnostics · logsviewer · config_backup · plugins · aboutscreen ·
         privilege · module_health · max_backup?pkg= · max_perms?pkg=
خطِرة: terminal · setedit · activitylauncher · filemanager · kernelflasher
```

ويُبنى الشريط من `MaxNavBar` ويُقرأ `risk` للتصنيف لا للّون فقط.

## ٥ · مستوى التحكّم — قلب المنتج

```
نية المستخدم (شاشة تطبيق / شاشة جهاز / بروفايل)
        │
        ▼
HardwareControlKey (هوية قياسية للمقبض: cpu_limits:P · gpu_frequency:DEV · thermal_policy · …)
        │
        ▼
HardwareControlArbiter.submit()            ← البوّابة الواحدة
  ├─ ManualControlLocks.blocks(owner)      ← القفل اليدوي يمنع **الأدنى منه فقط**
  ├─ SharedHardwareOwnershipStore          ← دفتر مشترك بين العملية والتطبيق (قفل ملفات)
  ├─ baseline = live                       ← ولا معاملة بلا قراءة أولى (baseline-unreadable)
  ├─ apply(desired) → read → satisfied?    ← الحكم من HardwareVerification لا من نجاح الأمر
  └─ فشل ⇒ restore(baseline) مؤكَّد، وإلا `rollback-not-verified`
        │
        ▼
الحُسّاد (Backends): CpuHardwareBackend · GpuHardwareBackend · ChargingHardwareBackend
  └─ PlatformCeilingAuthority  ← سلطة المنصّة: thermal_message/sconfig+cpu_limits ·
                                 powerhal_cpu_ctrl/perfserv_freq · cooling_deviceN · GED
```

### سلّم الأسبقية (مصدره `ControlOwnership.Owner.priority`)

```
SYSTEM(0) < GLOBAL_PROFILE(20) < MAX_AI(40) < PER_APP(60) < SAFETY(80) < RECOVERY(100)
  نظام         شاشة جهاز       Max AI       شاشة تطبيق   أمان       استرجاع
```

| المالك | من هو | مثال |
|---|---|---|
| `SYSTEM` | إعادة مزامنة داخلية | `reconcileAll` |
| `GLOBAL_PROFILE` | كتابة شاشة جهاز عابرة | GPU Studio · CpuCoreControl |
| `MAX_AI` | المحرك التكيّفي | `MinimalPlanner` |
| `PER_APP` | إعداد التطبيق في شاشة التطبيقات | `PerAppControlRegistry` |
| `SAFETY` | حماية الجهاز (فوق كل تفضيل بشري) | `SafetyEngine` |
| `RECOVERY` | إصلاح ما أفسده فشل | `HardwareRepairExecutor` |

**ولا مستوى `MANUAL` في هذه النسخة.** القفل اليدوي طبقة استثناء منفصلة على القرص
(`hardware-control-plane/manual-locks.json`)، وحكمها في `HardwareControlArbiter.submit` سطر ٦٣:
`ManualControlLocks.blocks(owner, key, token)` — يمرّ `SAFETY` وما فوقه ويُمنع ما دونه، **ومنه
`PER_APP`**. وأثره المقيس: قفل واحد من شاشة تحكّم يمنع كتابة شاشة التطبيقات والذكاء على المقبض نفسه،
ولا يوجد `Release.userClaimRestored` يعيد قيمة المستخدم عند تحرير المقبض الأقوى.

### البروفايل الحراري per-app — على هذه النسخة

```
ThermalGuard (على القرص): دالتان خالصتان فقط، والمنحنى خفضٌ تفاعليّ لا زمنيّ:
  nextCeiling(requestedHz, currentHz, ladder, requestedHz)  ── بلا خنق ⇒ النيّة كما هي
  nextRangeCeiling(userRange, currentRange, ladder, pressure) ── حكم MODERATE+ ⇒ درجة واحدة أسفل
        │
        └─ ThermalCeilingRouter: يُنفّذه كهدف Atlas (خط أساس · تحقّق · استرجاع) بحسب الحقل في
           شاشة التطبيق (thermal_policy / gpu_profile / cpu_profile …).
```

والنتيجة المقيسة: **لا كتابة إلّا عند إعلان المنصّة خنقًا** (`Pressure.isThrottling` = MODERATE وما
فوقها). فحقل الحرارة في شاشة التطبيقات لا يفعل شيئًا على جهاز يقرأ حالته بـ`UNKNOWN`، ولا سقف يثبّته
لحظة الاختيار، ولا يملك المقبض بنفسه (فلا يعمل إلّا فوق مقبض مملوك من مسار آخر).

## ٦ · مسارات القرار (من يُنادى ومتى)

| المسار | المُشغِّل | الإيقاع | يفعل |
|---|---|---|---|
| **per-app** | تبديل التطبيق في المقدّمة (عملية الجذر) | مرة لكل تبديل | يسجّل الملكية ويطبّق الإعدادات ويتحقّق ثم يعلن `PERAPP_COMMIT` |
| **drift** | نفس الحلقة | كل `DRIFT_CHECK_INTERVAL_MS` | يعيد ما سرقه مُلطِّف vendor · ثم الحارس الحراري · ثم معدّل التحديث |
| **thermal guard** | بعد دورة drift | نفسه | قرار السقف عبر Atlas (طبقة مثبّتة + انحدار حسب الضغط) |
| **MAX AI** | حلقة دائمة في التطبيق | `CYCLE_MS` | لقطة → هدف → أقلّ تدخّل كافٍ → تحقّق → تعلّم |
| **safety** | حلقة أسرع مستقلة | مستمرّة | حدّ حراري فوق كل مالك، وتقييد لا رفع |
| **revert** | خروج التطبيق أو انتهاء المهلة | عند الحدث | استرجاع خط الأساس لكل مقبض مملوك |

## ٧ · أين تعيش الحالة

```
/data/adb/.config/MaxManager/            (MaxManagerPaths)
  gamelist/maxmanagerApplist.json        قائمة التطبيقات وإعداداتها (AppConfig لكل حزمة)
  app_status · background_apps           تكتبهما عملية الجذر، وتقرأهما الواجهة وMaxAiEngine
  java.lock                              قفل العملية الواحدة للرفيق الجذري
  runtime/per_app_cpu_status             حالة CPU القديمة (متوافقة للخلف)
  runtime/per_app_hw_status              **حالة كل مقبض** مع رمز سببه (PerAppHardwareStatus)
  debug/MaxManager.log                   السجل الموحّد (الخفيّ يكتب، والواجهة تقرأ)
  hardware-control-plane/manual-locks.json   الأقفال اليدوية الدائمة
/data/adb/modules/MaxManager/system/bin/sys.maxmanager-service   الخفيّ (CLI)
data/ (تطبيق): filesDir + noBackupFilesDir/atlas/…   ذاكرة مسارات Atlas وجيل الإقلاع
```

وقنوات أخرى: `SharedPreferences("settings"/"app_prefs")` · `ProfilePresetStore` · `GpuTweakPersistence` ·
`PerAppRecoveryStore` · `CredibilityStore`. وما زالت بعض الشاشات تقرأ التفضيلات مباشرة (دَين قائم
مُقنَّن في `code_health`).

## ٨ · السجل والتشخيص

- **السطر واحد**: `TIME LEVEL TAG: EVENT=… key=value …`، و`EVENT` للمحرّك و`USER_ACTION` للمستخدم.
- **القاموس**: `LogCodeGlossary` يشرح كل رمز **وكل حقل** عند العتبة، ويُبنى من `HardwareControlKey`
  (لا نسخ نصّي — بوابة معمارية تمنعه)، وأسطر `code=`/`fix=`/`field=`/`unit=` تُكتب في **ترويسة**
  الملف نفسه، فيُشخَّص الملف وحده.
- **إصلاح لكل فشل**: `remedyOf(code)` — ومن لا إصلاح له يُعلن `fix=no-fix-encoded` بدل الصمت.
- **الحزمة**: `LogsViewer` تُصدّر الحزمة (السجل + logcat + dmesg + pstore + device_blueprint).
- **`PERAPP_KNOB`** سطر لكل مقبض في جلسة تطبيق: `outcome` · `reason` · `expected` · `live`.

## ٩ · التحقق المتاح هنا

بوّابات ثابتة (بلا مُصرّف):

```sh
python3 tools/kt_balance.py --assert      # 760 ملفًا · عوائق 0
python3 tools/code_health.py --assert     # صحّة = 0، والدَّين ≤ السقف (والتخفيض مطلوب)
python3 tools/i18n_coverage.py --assert   # لغات + مُحدِّدات + كتالوج المنتقي
python3 tools/repo_audit.py               # رأي ثانٍ مستقل
```

اختبارات حقيقيّة **بلا Android SDK**: تُبنى شريحة JVM من المصادر الحقيقيّة + شيمات خارج المستودع
(`Shell` مُفشَل عمدًا · `RootIpcManager.ipc = null` · `PowerManager`)، بمُصرّف Kotlin نفسه وJUnit حقيقي:

```
Kotlin 2.3.10 (نسخة المشروع) · JDK 21 · jvmTarget 17
JUnit 4.13.2 + hamcrest 1.3 · org.json 20250107 · kotlinx-coroutines-core-jvm · javax.inject
المُصرّف هو الحكم: تُضاف الملفات التي تُغلق الرموز وتُسقَط التي تحتاج أندرويد/Compose
آخر تشغيل ناجح كان على مصادر ما قبل إعادة المزامنة: 229 اختبارًا (hardware) + 56 (سجل/تشخيص) = 0 إخفاق.
```

ومقيس اليوم على **هذه** النسخة: `kt_balance` ٧٥٩/٠ ✅ · `i18n_coverage` ٠ عوائق ✅ · `repo_audit`
`PROBLEMS: 0` ✅ · `code_health` ✗ (عيب واحد: `stray_root_file`)، والدَّين عند سقفه (٦٣ · ٢٣ · ٢٩ · ١٠).

وعند تعذّر ذلك: `cd manager && ./gradlew :app:testDebugUnitTest --tests 'nd.max.core.*'`، ويجب أن تبقى
خضراء: `ControlPlaneArchitectureTest` · `HardwareControlArbiterTest` · `ManualControlLocksTest` ·
`ThermalGuardTest` · `ThermalCeilingRouterTest` · `AtlasRouteMemoryWiringTest` · `MinimalPlannerTest` ·
`ControlOutcomeModelTest` · `DiagnosticCenterTest` · `LogCodeGlossaryTest`.

## ١٠ · ثغرات الخريطة المعلنة

- جدول مسارات Atlas (Providers/Transports) ومدخلات كتالوج الجهاز مُجمَّلٌ هنا؛ تفصيله في
  `core/atlas/` نفسه (والـ`AtlasDoctor` يحكم على تغطية الكتالوج في الشاشة).
- **دورة أطلس (تكملة ١٠٥ — ADR-43):** `MaxAtlas` واجهةُ Discover→Understand→Map→Adapt→Execute→Verify→Learn؛
  وخريطة القدرة `AtlasCapabilityMap` (سبع حالات + `AtlasSafetyPolicy` عدم اللمس فوق الجميع) وملف الجهاز
  `AtlasDeviceProfile` (يُبنى لا يُخزَّن وينتهي بالجيل) في `core/atlas/`، وطبقة المواءمة `AtlasAdapters` (بملاءِمي GPU في `AtlasGpuAdapters`: مدى `devfreq` حيث تقبله العقدتا، وتثبيت OPP حيث لا تقبل)
  (مُلاءِمون + سجلّ حتميّ + `CpuCeilingAdapter`) في `core/hardware/`. والفصل **Atlas ≠ Max AI** جدولٌ في
  صدر `MaxAtlas.kt`: أطلس «كيف على هذا الجهاز» وMAX AI «ماذا ومتى» — ويُستهلك من موضعين إنتاجيين
  (`CpuCeilingKnobs` كلّه عبره، و`HardwareRouteHealth` من جدول الاشتقاق نفسه).
- شاشات `ui/**` لم تُوصف واحدةً واحدة: الشجرة في §3، والتفصيل في ملف كل شاشة.
- دبابيس أجهزة التبريد تُحترم عند **كل** كاتب — توجيه تبريد لا تفضيل أداء — لكن **لا شاشة تضعها
  اليوم** (`ThermalDevicesViewModel` بلا مستهلك: I-57)، فالحماية قائمة لمن يكتبها مستقبلًا لا أكثر.
- الدَّين الحالي (يُقاس في `code_health`، مُجمَّد عند السقف): ١٠ ملفات كبيرة · ٢٩ استيرادًا بنجمة
  داخلية · ٦٣ نصًّا صلبًا · ٢٣ كتابة عتاد من طبقة العرض.
- عيب صحّة كان قائمًا وأُصلح: `stray_root_file: 1` — ملف الجلسة كان في **جذر المستودع**
  (`session-ses_f3c5.md`) وهو ما كان يُسقط `code_health --assert`. نُقل إلى `docs/ai/sessions/`، والبوابة
  صارت `exit 0`. (إن أعادت المزامنة الملف إلى الجذر عاد العيب معه.)
