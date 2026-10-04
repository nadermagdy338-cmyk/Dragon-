# ARCHITECTURE-AUDIT — الخادم وحدود الطبقات في MaxManager

**التاريخ:** 2026-09-26 · **النوع:** تحليل فقط — **لا نقل ملفات، ولا إعادة كتابة، ولا فصل جديد.**
**المصدر:** كل رقم هنا مقيس من الشجرة بأمر، لا مكتوب من الذاكرة. وما لا يُقاس في هذه البيئة مذكور
بحدّه صريحًا (§١).

---

## ٠. الخلاصة التنفيذية — القرار أولًا

**الخادم ليس «يجب أن يُفصل» — هو مفصول أصلًا.** الجرد (§٢) يعدّ **ثماني نقاط دخول منفصلة** تعمل اليوم:
عملية الواجهة، وعملية جذر مربوطة بـbinder، ورفيق Java (`AppMonitor`)، وخادم C، وثنائيتا Rust،
و`thermalcore`، و`preloadbin`، ومكتبة JNI داخل العملية.

⇒ فالسؤال الذي طرحه المالك («هل الفصل الكامل أفضل؟») **محسوم بالواقع**: الفصل قائم. والسؤال الصحيح
الذي يكشفه الـAudit هو:

> **هل الحدود بين هذه العمليات متعاقدة (contracts) ومُختبرة؟ — الجواب: لا.**

> ⚠️ **وإن كان المقصود بـ«الخادم» daemon تنفيذيًّا Linux حقيقيًّا (لا APK آخر) — فذلك مُتحقَّق منه في
> §٠.١**: الـdaemon **يُبنى** كـELF تنفيذي واسمه `sys.maxmanager-service` (بـ`daemon(0,0)` وحلقة `while(1)`
> مقيمة، uid 0)، ويستقرّ بعد التثبيت في `$MODDIR/system/bin/` — والفحص هناك يعيد صياغة القرار A/B/C على هذا الأساس.
>
> 🔴 **وتصحيح مُلزم (بمُلاحظة المالك: «في module لا يوجد شيء في مجلد bin»)** — صحيح تمامًا، والتمييز
> الحاسم بين **حالة الشجرة** و**حالة التثبيت** في §٠.١.١.أ: **الثنائي التنفيذي ناتجُ بناءٍ لا موجودٌ
> في الشجرة**، و`mainfiles/system/bin/` فيه `.placeholder` وحده. فكل ما بُني عليه استدلال §٠.١ يبقى
> **تصميمًا ووصفةَ بناءٍ مُثبَتة**، لا **أثرًا حاضرًا**.
>
> ✅ **وأُصلح في S9 (والتصريف صار مُتحقَّقًا):** ثُبّت **NDK r29 (= إصدار CI)** و`ndk-build` أنتج `ELF`
> صالحًا للعمودين، وأصبح الـdaemon **حاضرًا في `system/bin/` داخل الحزمة** بحرس `file`/`readelf` يُفشل
> البناء نفسه — والتفصيل والإثبات في §٠.١.١.أ.

**القرار:**
1. **لا نغيّر المعمارية ولا ننقل شيئًا.** لا فصلًا جديدًا ولا دمجًا.
2. **نثبّت عقود الحدود القائمة** (§١٢) — إدخال/إخراج، حالات خطأ، حالات تحقّق، ملكية، دورة حياة،
   مهلة/استرجاع، توافق إصدارات.
3. **ثم نبني الـfixtures على هذه العقود** (§١٣) — لا على خادم متخيَّل.
4. **لا يُمسّ أي مكوّن أصليّ** (Max AI · Atlas · Safety · Arbiter · Profile System) — بل تُحدَّد مواضع
   اتصالها فقط (§١١).

**الفائدة الهندسية التي نستهدفها** (بحسب معيار المالك §٧): **stability + maintainability + testability**
أولًا — لا «شكل معماري أجمل». وأيّ بند آخر (performance/memory/battery) **لا يُدَّعى هنا** لأنه يحتاج
جهازًا (§٨).

---

## ٠.١ إعادة التقييم بمفهوم المالك: «daemon تنفيذي حقيقي» لا APK آخر

**تنبيه على المصطلح:** كلمة «الخادم» في §٢ **هي نفسها** الـdaemon التنفيذي الذي يقصده المالك — لا يوجد
«server» من نوع آخر في المشروع. والمراجعة التالية تتحقّق من ذلك بالملف التنفيذي المُثبَّت، ثم تعيد صياغة
القرار على هذا الأساس.

### ٠.١.١ هل يوجد executable daemon حقيقي؟ **يُبنى نعم — وليس حاضرًا في الشجرة**

**التمييز الحاكم (لئلا يُقرأ الجدول على غير وجهه):** «موجود» هنا تعني **مُعرَّفًا ومُبنىّ الاسم والمسار**،
لا **ملفًّا حاضرًا في المستودع**. العمود الأخير يقول الحقيقة بلا تجميل.

| الوحدة | ELF تنفيذي؟ | كيف تُبنى | المسار **بعد التثبيت** | في الشجرة؟ | مقيم/عند الطلب | uid |
| --- | --- | --- | --- | --- | --- | --- |
| **الـdaemon** `sys.maxmanager-service` | ✅ **`BUILD_EXECUTABLE`** | `archdaemon/jni` (ndk-build) | `$MODDIR/system/bin/sys.maxmanager-service` (+رابط `zx`) | ❌ ناتج بناء | **مقيم** (`--run`) | **0** |
| `sys.maxmanager-profilesettings` | ✅ | `binprofiles` (cargo) | نفس المجلد | ❌ ناتج بناء | عند الطلب | 0 |
| `sys.maxmanager-utilityconf` | ✅ | `binutils` (cargo) | نفس المجلد | ❌ ناتج بناء | عند الطلب | 0 |
| `sys.maxmanager-rianixiathermalcore` | ✅ | `thermalcore` (cargo) | نفس المجلد | ❌ ناتج بناء | **مقيم** عند تفعيله | 0 |
| `sys.maxmanager-preloadbin` | ✅ | `preloadbin` (ndk-build) | نفس المجلد | ❌ ناتج بناء | عند الطلب | 0 |
| رفيق Java `AppMonitor` | ❌ **ليس ELF** | `app_process` يشغّل dex من الـAPK | — | — | **مقيم** | 0 |
| `RootNodeService` | ❌ خدمة binder | `libsu` `RootService` | — | — | مقيم عند الربط | 0 |

### ٠.١.١.أ أين الثنائي في الشجرة؟ **لا يوجد — وهذا صحيح لا خطأ**

هذا هو موضع التصحيح (تحقّق المالك):

```
$ find mainfiles/system/bin -maxdepth 1
mainfiles/system/bin/.placeholder        ← لا شيء غيره؛ لا ELF ولا `.so`
```

**والسبب مسار بناء صريح لا إغفال** — الثنائيات تُنتَج في CI ثم تُحشى في الحزمة، ويستخرجها المنصّب وقت التثبيت:

| الخطوة | الدليل | ما يحدث |
| --- | --- | --- |
| ١. الإنتاج | `.github/scripts/compile_zip.sh:69-77` | `copy_binary` ينسخ Rust (3) إلى `mainfiles/libs/<abi>/` **ويرفض المفقود**؛ و`:57-59` ينسخ `archdaemon/libs/*` و`preloadbin/libs/*` |
| ٢. الجدولان | `compile_zip.sh:53-55` | `mkdir -p mainfiles/libs/arm64-v8a` + `armeabi-v7a` + `system/bin` |
| ٣. الاستخراج | `mainfiles/customize.sh:117-122` | `extract "$ZIPFILE" "libs/$ARCH_TMP/sys.maxmanager-service" "$TMPDIR"` ×5 ← ثم `cp "$TMPDIR/libs/$ARCH_TMP/"* "$MODPATH/system/bin/"` |
| ٤. الصلاحيات | `mainfiles/customize.sh:369` | `set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755` |

⇒ **كان `system/bin` في الشجرة والحزمة مجلد هبوط فارغًا عن قصد** (لأن `system/bin/` **واحد** لا يحمل عمودي
`APP_ABI`)، وملءه يقع على `libs/<abi>/` في الـzip.

#### ✅ وأُصلح في **S9** — والـdaemon الآن حاضر في `system/bin/` داخل الحزمة

**والمُدخل الحاسم:** لا يلزم حسم العمودين لملء `system/bin`؛ يوضع **عمود 64-بت** (الغالب) للتمثيل والتحقّق العام،
ويبقى عمود 32-بت في `libs/armeabi-v7a/` **و`customize.sh:122` ينسخه فوقه على جهاز 32-بت** ⇒ **صفر تغيير
في منطق التثبيت**. وأُضيف حرس `ELF` يُفشل البناء نفسه:

| الحرس | الأداة | الشرط |
| --- | --- | --- |
| نوع العمود | `file -b` | `ELF 64-bit` + `aarch64` لـ`system/bin/<daemon>` |
| تنفيذي لا مكتبة | `readelf -h` | `Type: DYN\|EXEC` |
| عمود 32-بت | `file -b` | `ELF 32-bit` + `ARM` (كي لا يُهمل مسار 32-بت) |

و**القِيس فعلًا** (لا ادّعاء) في هذه البيئة بعد تثبيت NDK r29 `29.0.14206865` (= إصدار CI):

```
$ ndk-build  (في archdaemon/)  → exit 0
archdaemon/libs/arm64-v8a/sys.maxmanager-service    ELF 64-bit LSB pie, ARM aarch64, 73,616 B
archdaemon/libs/armeabi-v7a/sys.maxmanager-service  ELF 32-bit LSB pie, ARM EABI5, 59,524 B
                                                        Class ELF64 · Type DYN (PIE) · Entry 0x8100
                                                        NEEDED: liblog.so · libc.so · libm.so · libdl.so
$ unzip -p …zip system/bin/sys.maxmanager-service | file -
ELF 64-bit LSB pie executable, ARM aarch64, interpreter /system/bin/linker64  ✅
$ cmp  (المستخرج من الحزمة ↔ مخرَج ndk-build)  → مطابق بايت-ببايت
```

**البقايا الصادقة:** (١) التصريف مُتحقَّق على المضيف بـNDK r29، **والسلوك على جهاز لا يزال يحتاج جهازًا**.
(٢) الثنائيات الأربعة الأخرى (Rust) **لم تُصرَّف هنا** (`cargo` غير متاح) ⇒ حزمة كاملة لا تُنتَج محليًّا.
(٣) نظام `sha256` في `need_integrity` يولّد `system/bin/sys.maxmanager-service.sha256` زائدًا عن الحاجة
(غير مستهلك، لأن `verify.sh` يتحقّق من المستخرج من `libs/` فقط) — ضجيج صغير مقبول ومسجَّل.

### ٠.١.١.ب عطب مُكتشَف أثناء التحقّق: **اسمَا الـdaemon لا يتطابقان** → ✅ وُحِّد في تكملة ١١٢

`android/kernelsu/*` و`android/aosp/*` يسمّون الـdaemon **`maxmanager_daemon`** وجذره `/system/bin/` —
وهو **اسم لا ينتجه أي بناء في المستودع** (المنتجات الخمسة كلها `sys.maxmanager-*`). والفرق ليس لفظيًّا
بل **مسارا تثبيت متوازيان**:

| | مسار `mainfiles` (المُشتغل فعلًا) | مسار `android/kernelsu` |
| --- | --- | --- |
| اسم الـdaemon | `sys.maxmanager-service` | `maxmanager_daemon` ← **لا مُنتِج** |
| مجلد الثنائيات | `system/bin/` | `bin/` |
| المكتبة | `system/product/…/priv-app/.../MaxManager.apk` | `app/MaxManager.apk` + `lib/libmaxmanager_native.so` |

و**`android/kernelsu/` معزول**: لا CI ولا سكربت ولا أداة تشير إليه (فحص شامل = 0 مُشير)، بينما
`android/aosp/` يُستعمل **كتثبيتة fixture** في `tools/sepolicy_matrix.py` فقط. ⇒ **دَين صريح: توحيد اسم
الـdaemon وحسم أي المنصّبين هو الحقيقي** (§١٢/§١٣) — ولا يُحسم بلا القياس على جهاز.

**وهو عطب وظيفي لا تجميلي — بثلاثة آثار مقيسة:**

| الأثر | الدليل | النتيجة إن دُمج مسار AOSP |
| --- | --- | --- |
| الـ`init` يبدأ ثنائيًّا لا يوجد | `android/aosp/maxmanager.rc:5` `service maxmanager_daemon /system/bin/maxmanager_daemon` | **فشل تشغيل دائم** — لا بناء ينتج هذا المسار |
| وسم SELinux لا يُصيب الـdaemon الحقيقي | `file_contexts:4,8` تُوسِم `/system/bin/\|/vendor/bin/maxmanager_daemon` | `sys.maxmanager-service` **غير موسوم** ⇒ `maxmanager_exec` لا ينطبق ⇒ رفض |
| منصّب KSU يطلب مسارًا آخر | `android/kernelsu/customize.sh:33` `$MODPATH/bin/maxmanager_daemon` + `service.sh:8` | تركيب ناقص/منصّب لا يعمل |

**والمسار المُشتغل (`mainfiles/`) متّسق 100%**: `customize.sh:122,195-201` · `service.sh:21,188` ·
`action.sh:20,65` · `post-fs-data.sh:55` — كلها على `sys.maxmanager-service` وحدها.

#### ✅ الحسم (تكملة ١١٢): طبيعة `android/aosp` و`android/kernelsu` + التوحيد المنفَّذ

**أولًا — هل هي هدف بناء مستقل أم بقايا مسار قديم؟ القياس الفاصل:**

| مدّعى سابق | القياس | الحكم |
| --- | --- | --- |
| «حزمة المطوّر تتضمّن `android/aosp/Android.bp`» | `build.yml:958-1000` **يولّد** `developer-bundle/aosp/Android.bp` داخليًّا (`android_app_import` للـAPK الجاهز)، وREADME الحزمة ينصّ: *ships prebuilts only: no app sources, no init service, no SELinux policy* | **غير صحيح** — `android/aosp/` **لا يدخل أي حزمة** |
| «مسار AOSP هدف بناء» | لا CI ولا سكربت يستدعيه؛ وكان `Android.bp` يعلن `runtime/daemon-rust/` **غير الموجود** | **قالب معزول** لا هدف بناء |
| «`android/kernelsu/` مسار تغليف موازٍ فعّال» | `compile_zip.sh` يغلق `mainfiles/` وحدها؛ صفر مُشير إلى `android/kernelsu/` في CI/سكربت | **قالب معزول** |
| **الوحيد المستدعى فعلًا** | `android/overlay/product/etc/permissions/privapp-permissions-nd.max.xml` — ينسخه `build.yml:967` و`compile_zip.sh:156` | ✅ **حيّ ومُشتغل** |

⇒ **القرار (مُوثَّق لا منفَّذ بالحذف):** `android/aosp/` و`android/kernelsu/` **قالبان معزولان** لم يُحذفا
(كما أمر المالك)، ووُحِّد اسمهما ليمثّلا الـdaemon الرسمي بدقّة إن استُعملا. أما `android/overlay/` فهو
**مصدر حقيقة حيّ** يستهلكه CI.

**ثانيًا — التوحيد المنفَّذ (الاسم الرسمي الوحيد: `sys.maxmanager-service`):**

| الملف | قبل | بعد |
| --- | --- | --- |
| `android/aosp/maxmanager.rc` | `service maxmanager_daemon /system/bin/maxmanager_daemon` (+3 `start/stop`) | `service sys.maxmanager-service /system/bin/sys.maxmanager-service` |
| `android/aosp/sepolicy/file_contexts` | وسم `/system/bin/` و`/vendor/bin/maxmanager_daemon` | وسم `/system/bin/sys.maxmanager-service` وحده (حُذف سطر `/vendor/bin` لأنه لم يبقَ مُنتَجًا) |
| `android/aosp/Android.bp` | `cc_binary name: "maxmanager_daemon"` بمصادر Rust غير موجودة و`vendor`+`product_specific` معًا | `name: "sys.maxmanager-service"` بمصادره الحقيقية `archdaemon/jni` (كما `Android.mk`) وقسم `system` واحد |
| `android/aosp/BoardConfig.mk` | `maxmanager_daemon` في `PRODUCT_PACKAGES` | `sys.maxmanager-service` |
| `android/kernelsu/customize.sh` | `$MODPATH/bin/maxmanager_daemon` + `/system/bin\|/vendor/bin/maxmanager_daemon` | `$MODPATH/system/bin/sys.maxmanager-service` |
| `android/kernelsu/service.sh` | `$MODDIR/bin/maxmanager_daemon` | `$MODDIR/system/bin/sys.maxmanager-service` |
| `android/kernelsu/action.sh` | `pidof maxmanager_daemon` | `pidof sys.maxmanager-service` |
| `android/kernelsu/uninstall.sh` | `maxmanager_daemon` (×2) | `sys.maxmanager-service` |

**والأثر المقيس على بوابة `sepolicy_matrix`:** الأعطاب **١٦ → ١٢**؛ وكل أعطاب اسم الـdaemon زالت
(`no-dead-reference` 5→4 · `no-dead-source` 2→1 · `path-conflict` 3→1)، و`install-labeled` ما زال **0**
(أي أن `sys.maxmanager-service` **موسوم** ومتّسق مع مسار init). والـ12 المتبقّية **سابقة ولا علاقة لها بالاسم**
(مسارَا `label-not-stale`، و`$MODPATH/app`/`lib`/`sepolicy.rule`/`vest.apk`، وتعريف `libmaxmanager_native`
— طبقة Max AI الأصلية **ولم يُمَس**، و4 `policy-gap`).

**دَين مُصرَّح متبقٍّ (لم يُمَس عمدًا):** `cc_library_shared libmaxmanager_native` في `Android.bp` ما زال
يعلن `runtime/daemon-rust/src/lib.rs` غير الموجود ويجمع قسمين — وهو **طبقة Max AI الأصلية**، فتُرِك حتى
قرار المالك (توصيله بمصدره الحقيقي `manager/src/main/rust` أو إعادة تنظيم القالب كله).

**ثالثًا — الإثبات النهائي على الـartifact الحقيقي (لا proof ZIP منفصل):**

```
SOURCE  archdaemon/jni (+ Android.mk)
  → ndk-build (NDK r29 29.0.14206865)              → exit 0
  → ELF   archdaemon/libs/arm64-v8a/sys.maxmanager-service
  → compile_zip.sh   (شُغّل الحقيقي، بلا تعديل)     → exit 0
  → ZIP   MaxManager-5.2-1-72ac768-Dazzling.zip    (42,758,087 B)
  → system/bin/sys.maxmanager-service               ← المالك يفتحه ويراه
$ unzip -p …Zip system/bin/sys.maxmanager-service | file -
  ELF 64-bit LSB pie executable, ARM aarch64, interpreter /system/bin/linker64     ✅
$ readelf -h → Class ELF64 · Type DYN (PIE) · Machine AArch64 · Entry 0x8100        ✅
$ sha256sum (المستخرج ↔ مخرَج ndk-build) → 49bd392c…de3a0a  (متطابقان)              ✅
$ cmp      (المستخرج ↔ مخرَج ndk-build) → مطابق بايت-ببايت                          ✅
$ الثنائيات الخمسة × العمودين في `libs/` → ✅×١٠
```

**وحدّ هذا الإثبات:** السلوك على **جهاز** يبقى يحتاج جهازًا · و`compile_zip.sh` هنا شُغّل بـ
`GITHUB_EVENT_NAME=pull_request` (APK **debug**، لأن توقيع الإصدار يحتاج `KS_PWD` غير المتاح) — وتوقيع
الإصدار يُقاس على CI وحده.

**والدليل على أنه daemon حقيقي لا «خادم» مجازي** — `archdaemon/jni/Main.c` و`System.c`:

```c
daemon(0, 0);                    // انفصال كامل عن الطرفية (POSIX daemonize)
signal(SIGINT/SIGTERM, sighandler);
setspid();                       // ملف PID
while (1) { process_inotify_events(inotify_fd, &ctx, poll_timeout); }   // حلقة مقيمة event-driven
```

**ونمط «ctl + daemon في ثنائية واحدة» قائم أصلًا** — نفس الملف يتفرّع:
`--run` = الخادم المقيم · وبقية الأعلام (`--profile` · `--rerun` · `--clearlogs` · `--version` …) = CLI
عابر. أي أن `rodin_daemon` + `rodin_ctl` عندنا **ثنائية واحدة** لا ثنائيتان — وهو الأنسب (لا نسختان تتباعدان).

### ٠.١.٢ الفرق بين ما كتبته وما يقصده المالك

| | ما يقصده المالك | ما هو موجود فعلًا |
| --- | --- | --- |
| **الطبيعة** | ملف ELF في `system/bin/` يعمل كـroot | ✅ **مطابق** — `sys.maxmanager-service` |
| **الاسم** | `maxmanager_daemon` | `sys.maxmanager-service` — **هوية MaxManager أصلًا** |
| **العلاقة بالـAPK** | مستقلّ تمامًا | ✅ مستقلّ — يُطلقه `service.sh` بـ`exec`، لا يعتمد على الـAPK في التشغيل |

⇒ **الفجوة الحقيقية ليست «لا daemon»** — بل أن **العدد ٥ لا ١**، وأن **العقود غير معلنة** (§٤، §١٢).

### ٠.١.٣ من يملك العتاد فعلًا؟ — القياس الفاصل (٣٤٩ هدفًا · ٦٤ مشتركًا · ٢١ لثلاثة)

فحصتُ مَن يذكر كل هدف تحكّم (`/sys` · `/proc` · `sys.*` prop) عبر مكوّنات التنفيذ الستة:

```
إجمالي الأهداف المتميّزة : 349
مشتركة بين مكوّنين أو أكثر : 64
منها ثلاثة كتّاب أو أكثر  : 21
```

| الهدف الأكثر خطورة | المكوّنات | النوع |
| --- | --- | --- |
| `/proc/gpufreq/gpufreq_opp_freq` | daemon(C) · profilesettings(Rust) · app(Kotlin) | **كتابة/كتابة** |
| `/proc/gpufreqv2/fix_target_opp_index` | daemon(C) · profilesettings(Rust) · app(Kotlin) | **كتابة/كتابة** |
| `/sys/devices/system/cpu/cpu*/cpufreq/scaling_governor` | profilesettings · utilityconf | كتابة/كتابة |
| `/sys/block/{}/queue/scheduler` | profilesettings · utilityconf | كتابة/كتابة |
| `/sys/class/devfreq/*.mali/governor` | profilesettings · utilityconf | كتابة/كتابة |
| `persist.sys.maxmanagerconf.*` (١٥ اسمًا) | app · daemon · profilesettings | **قراءة مشتركة** (لا تعارض) |

**(أ) أخطر حالة — وليست خطأً بل تضاعفًا مقصودًا:** قفل GPU على MediaTek **يكتبه** الرفيق Java
(`MtkUtils`)، **ويحرّره** الـdaemon C عند خروجه. والنصّ في `System.c` يشرح السبب نصًّا:

> «The Kotlin companion normally releases this through MtkUtils, **but the native daemon can outlive a
> crashed/force-killed companion. Never leave `fix_target_opp_index` pinned across daemon shutdown.**»

⇒ فهذه **ليست فوضى ملكية** — بل **تعافٍ متقاطع** (cross-process redundancy) يجعل فصل العمليات قيمة
هندسية مثبتة: لو دُمج الرفيق في الـdaemon لضاع هذا التعافي بالضبط.

**(ب) وأمّا الـ61 الباقية** فمعظمها **قراءة/كتابة** (التطبيق يكتب الإعداد، والـdaemon/`profilesettings`
يقرأه) — أي أنها **فجوة عقد لا فجوة ملكية** ⇒ تُحلّ بـ§١٢ لا بإعادة معمارية.

### ٠.١.٤ القرار: **A — الإبقاء على البنية الحالية** (مع إعلان العقود)، لا B ولا C

**لماذا A:** البنية **هي أصلًا** ما يرسمه المالك في مخطّطه (APK ← IPC ← daemon تنفيذي root ← عتاد).
تغييرها إلى «daemon واحد» لن يحقّق فائدة مقيسة، بينما يحمل **ثلاث خسائر مؤكّدة**:

1. **فقدان التعافي المتقاطع** المقيس في §٠.١.٣ (أ) — الـdaemon يحرّر قفل GPU بعد موت الرفيق.
2. **ارتباط تراخيص**: `thermalcore` (مشتقّ Rianixia/Apache-2.0) و`preloadbin` (vmtouch/BSD-3) — دمجُهما
   في ثنائية واحدة **يزيد التشابك** ويجعل حدود الإسناد أصعب، بلا مقابل مقيس.
3. **بلا فائدة قابلة للقياس هنا**: RAM/بطارية الفرق بين ٥ عمليات وعملية واحدة **يحتاج جهازًا** — وقاعدة
   المالك (§٧) ترفض تغييرًا بلا فائدة مقيسة. ودورة حياة `thermalcore` (حلقة PID خاصة) **مستقلّة بطبعها**
   عن دورة حياة الـdaemon.

**لماذا لا B:** B تعني «إنشاء daemon حقيقي» — وهو **موجود**؛ وتنفيذها عمليًّا = دمجه مع ٤ ثنائيات أخرى،
أي **إعادة كتابة حدود موروثة** (ADR-05، ADR-18) بلا فائدة مقيسة، مع خطر إسقاط سلوك لا يُثبَت إلا على جهاز.

**لماذا لا C:** لم يظهر من فحص الكود تصميم ثالث أفضل. والمرشّح الوحيد لتحسين حقيقي (لا تغيير معماري)
هو: **رفيق Java يستطلع كل ٥٠٠ م.ث** بينما الـdaemon حلقة أحداث (`poll(-1)` في السكون) — فرق بطارية
**مرشّح للقياس على الجهاز** (§٨)، لا سببًا لإعادة تصميم الآن.

### ٠.١.٥ خريطة مخطّط المالك إلى الواقع (لا تغيير مطلوب)

```
MaxManager.apk
        │  ├── UI                ✔ موجود
        │  ├── Max AI            ✔ موجود (core/maxai, 23 ملفًا · 6,371 سطرًا)
        │  ├── Safety Engine     ✔ موجود (SafetyEngine · SafetyGovernor)
        │  ├── Profile System    ✔ موجود
        │  └── Atlas/orchestration ✔ موجود (core/atlas)
        │            │
        │            │  IPC/Binder/API   ← ① AIDL IRootNodeService (مهيكل)
        │            ▼                    ← ② props (50) + ملفات (27) + CLI (13)
  sys.maxmanager-service   ✔ ELF محقق (NDK r29) → system/bin داخل الحزمة (S9) · uid 0 · daemon(0,0)+while(1)
                             (يُبنى في CI؛ ومُصرَّف ومُقاس في هذه البيئة — §٠.١.١.أ)
        │
        ├── CPU/GPU · Thermal · RAM/ZRAM · sysfs/procfs · low-level
```

**وما ينقص ليس صندوقًا في المخطّط — بل الخطوط بين الصناديق:** عقود معلنة واختبار حدود (§١٢ · §١٣).

### ٠.١.٦ لو أُريد B لاحقًا: مسار آمن مُدرَّج (لا يُنفَّذ قبل إثبات الفائدة)

إن أثبت **قياسٌ على جهاز** أن RAM/البطارية تنقص فعليًّا بالدمج — فالطريق ليس إعادة كتابة، بل:

```
1) reference   : أبقِ الثنائيات الخمس كما هي (لا حذف)
2) contract    : ثبّت §١٢ + fixtures §١٣  ⟵ شرط دخول لا يُتجاوز
3) adapter     : الـdaemon يستدعي المكوّنات عبر واجهة داخلية موحّدة (بلا دمج فيزيائي)
4) parity      : Fixture parity: مرجع مقابل موحَّد — سلوك + خطأ + تحقّق
5) switch      : علّم الأساس خلف flag بناء (مثبَّت افتراضيًّا على القديم)
6) rollback    : مسار عودة جاهز دائمًا (القديم باقٍ حتى يمرّ real-device)
7) cleanup     : الحذف **فقط** بعد parity + جهاز + مراجعة سلامة
```

**وشرط الإغلاق المطلق (المالك §10):** لا يُحذف أي daemon/server/native قبل Feature Parity وfixtures تثبت السلوك.

### ٠.١.٧ سجلّ المرحلة (بحسب طلب المالك)

| البند | |
| --- | --- |
| **فحصت** | `Main.c`/`System.c` (دورة الحياة · `daemon()` · الحلقة) · `Android.mk`/`compile_zip.sh`/`customize.sh` (مسارات التثبيت) · ٣٤٩ هدف تحكّم ومَن يملكه · `MtkUtils`↔`System.c` (تعافي قفل GPU) |
| **قرّرت** | **A** — لا تغيير معماري · الـdaemon **يُبنى** واسمه كافٍ · الدَّين الحقيقي = العقود (لا الصندوق) + **توحيد اسمَي الـdaemon** (§٠.١.١.ب) — **وقد وُحِّد فعلاً في تكملة ١١٢** |
| **تغيّر** | هذه الوثيقة فقط (=S8/S8.١) · **ثمّ في S9:** `compile_zip.sh` + `build.yml` (الـdaemon صار حاضرًا في `system/bin/` داخل الحزمة بحرس `ELF`) · **وفي S10:** `android/aosp/*` + `android/kernelsu/*` (توحيد الاسم على `sys.maxmanager-service`) |
| **لم يتغيّر** | **صفر ملف كود** · لا نقل · لا دمج · لا حذف · ولا مساس بـMax AI/Safety/Atlas/Profile |
| **اختبارات** | Kotlin 1587/0 · C 69/69 (مضيف) · Rust 59 (CI) · البوابات الخضراء |
| **مخاطر** | كما §١٦، ويضاف: **٢١ هدفًا بثلاثة كتّاب** — أخطرها nodes الـGPU، ويُعالَج بـ§١٢ لا بإعادة تصميم |
| **معلّق على المالك** | اعتماد §١٢ (العقود) → ثم تنفيذ fixtures §١٣ |

---

## ١. المنهج والحدود — ما فُحص وما لا يُفحص هنا

**فُحص بالقياس:** مسارات البناء (`Android.mk`/`Makefile`/`Cargo.toml`/`build.gradle.kts`)، سكربتات
الوحدة (`mainfiles/`)، أسماء الثنائيات ومخرجاتها، نقاط الاتصال (props/files/CLI/AIDL/broadcast/locks)،
حلقة الخادم الرئيسة، نقاط الـJNI، وأحجام الطبقات وعدّ الاختبارات.

**لا يُفحص في هذه البيئة (يحتاج جهازًا):** استهلاك RAM/بطارية فعليًّا · زمن الاستجابة الحقيقي ·
سلوك sysfs على عتاد بعينه · مؤقتات النواة · توقيع الإصدار (`KS_PWD`). وكل ما هو من هذا القبيل مكتوب
«يحتاج جهازًا» ولا يُقدَّم كقياس.

---

## ٢. جرد الواقع — نقاط الدخول الثمانية (مقيسة)

| # | العملية/الوحدة | كيف تُبنى | المستخدم | uid | تُشغَّل متى | مصدرها |
| --- | --- | --- | --- | --- | --- | --- |
| ١ | **عملية الواجهة** `nd.max` | APK (Compose) | المستخدم | تطبيق (+`su` عند الطلب) | عند فتح التطبيق | MaxManager |
| ٢ | **عامل الجذر المربوط** `RootNodeService` | داخل APK (AIDL `IRootNodeService`) | التطبيق (binder) | **0** | يُربط من `MainActivity` | MaxManager |
| ٣ | **رفيق Java** `app_process … nd.max.AppMonitor` | dex من APK | `service.sh` | **0** | كل إقلاع (`service.sh`) | MaxManager |
| ٤ | **الـdaemon التنفيذي** `sys.maxmanager-service` (ELF — **يُبنى لا حاضر في الشجرة**، §٠.١.١) | `archdaemon/jni` (`BUILD_EXECUTABLE`) | `service.sh` (`exec … --run`) · يستخرجه `customize.sh:117-122` من `libs/<abi>/` | **0** | كل إقلاع | Encore/AZenith (موروث) |
| ٥ | **ثنائية Rust** `sys.maxmanager-profilesettings` | `binprofiles/` (Cargo) | الخادم **وحده** | **0** | عند تغيّر ملف | MaxManager |
| ٦ | **ثنائية Rust** `sys.maxmanager-utilityconf` | `binutils/` (Cargo) | الخادم **والتطبيق** | **0** | عند الطلب | MaxManager |
| ٧ | **خادم حراري** `sys.maxmanager-rianixiathermalcore` | `thermalcore/` (Cargo) | الخادم | **0** | عند `thermalcore=1` | Rianixia (موروث) |
| ٨ | **`sys.maxmanager-preloadbin`** | `preloadbin/` (ndk-build) | الوحدة/الخادم | **0** | عند preload | vmtouch (BSD) |
| + | **مكتبة JNI** `libmaxmanager_native.so` | `manager/src/main/rust` (`cdylib`) | عملية الواجهة **داخلها** | التطبيق | عند `System.loadLibrary` | MaxManager (Rust) |

**قراءة هذا الجدول:** الفصل ليس مطلبًا مستقبليًّا — بل **واقع قائم منذ اليوم**؛ وأخطر ما فيه أنّ
**كل شيء تقريبًا يعمل بـ`uid 0`** (§٦).

---

## ٣. المسؤوليات الحالية — من يفعل ماذا (مقيسة من الكود)

### ٣.١ طبقة التطبيق (Kotlin — 108,557 سطرًا)

| المكوّن | الحجم | المسؤولية | أصليّ؟ |
| --- | --- | --- | --- |
| `core/maxai/` (23 ملفًا) | 6,371 | **القرار**: يوتّم القيادة، يتعلّم أثر كل مقبض، يوازن الهدف مقابل الحرارة | ✔ MaxManager |
| `core/atlas/` (23 ملفًا) | 6,823 | **خريطة التنفيذ**: مسارات العتاد، بنك المعرفة، مسارات القياس | ✔ MaxManager |
| `core/hardware/` (48 ملفًا) | 9,134 | **التنفيذ والحكُم**: `HardwareControlArbiter` (ملكية المفاتيح + journal)، `SafetyEngine`/`SafetyGovernor`، بوابات العتاد | ✔ MaxManager |
| `core/diagnostics/` (11 ملفًا) | 2,637 | حقائق الجهاز، سجلّ الإعدادات، التقارير | ✔ MaxManager |
| `core/ipc/` (2 ملفًا) | 162 | القناة الوحيدة إلى عامل الجذر | ✔ MaxManager |
| `ui/navigation/` (6 ملفات) | 932 | مصدر المسارات الواحد | ✔ MaxManager |

**والقاعدة المعلنة في الكود نفسه (نصًّا):** `ProfileApplier` — «هذه هي الطريقة الوحيدة الصحيحة لتبديل
الملف العام: الوحدة الأصلية تدير المحافظ والحدود و I/O كوحدة واحدة، فأي كتابة مباشرة منفصلة كانت
ستتصارع معها». أي أن مبدأ «مالك واحد» **مكتوب ومقصود** لا عارض.

### ٣.٢ الرفيق Java (AppMonitor — 2,885 سطرًا، يعمل بـuid 0)

كشف الملف الأمامي/التركيز (بـ`HiddenApiBypass` على واجهات مخفيّة، مع قائمة بدائل للأصناف)، ويطبّق
تعديلات **per-app** على العتاد (Atlas executor، سقوف GPU/CPU)، ويعيد تأكيد الانحراف كل ١٠ ثوان،
وينشر النتيجة فى `app_status`.

### ٣.٣ خادم C (5,129 سطرًا / 40 ملفًا)

`main_daemon()`: `verify_system_integrity` ← `daemon(0,0)` (انفصال) ← استعادة حالة ← `recover_per_app_thermal_policy`
← **انتظار رفيق Java** ← خيط مراقبة `java.lock` ← `read_app_status` ← `reload_gamelist_cache` ←
`setup_inotify_watchers` ← `validateprop` ← `runthermalcore` ← `run_profiler(PERFCOMMON)` ← **حلقة رئيسة
event-driven** (`inotify`+`poll` بمهلة ديناميكية: `-1` سكون، `0` فوري، أو مهلة سماح محسوبة).

مسؤولياته: الملف العام (عبر `profilesettings`)، شحن التجاوز، الـrenderer، الـresolution، preload الألعاب،
تتبّع PID، إدارة قفل الـJava، وحفظ حالة قابلة للاستعادة (`daemon_state`).

### ٣.٤ الطبقة الأصلية Rust

| الوحدة | الحجم | ما تقدّمه |
| --- | --- | --- |
| `manager/src/main/rust` (`cdylib`) | 3,668 | 21 نقطة JNI: `ProbeBridge` · `PredictorBridge` (RL/توقّع حراري) · `ScanBridge` · `ArchiveBridge` · `PropBridge` · `ContextBridge` |
| `binprofiles` | 2,262 | تطبيق إعدادات الملف — **يرفض العمل إلا إن كان المستدعي هو الخادم** (فحص `/proc/<pid>/cmdline`) |
| `binutils` | 506 | `setsgov · setsIO · setsMaliGov · setthermalcore · checkmalipath · FSTrim · enable/disableDND · setrefreshrates · restartservice · setrender` |
| `thermalcore` | 2,113 | مراقبة حرارية + PID + تبريد + متعلّم داخلي (`ThermalAI`) |
| `preloadbin` | 831 | vmtouch (تثبيت صفحات في الذاكرة) |

---

## ٤. أسطح الاتصال الحالية (مقيسة) — وهنا المشكلة الحقيقية

| السطح | العدد | الشكل | الإصدار | هل يُختبر؟ |
| --- | --- | --- | --- | --- |
| **خصائص النظام** `persist.sys.maxmanager*` | **٩٣** في السطح المُعلَن: ٩١ في `MaxManagerProps` + ٢ للخادم (`.state` · `.service`)، وفحص الخادم = ٤٨ مُدخلًا (٤٧ اسمًا فريدًا) + **٤** بادئات | `getprop`/`setprop` | لا (لكن المُعلَق صار مُفهرَسًا) | ✅ `SystemPropertiesContractTest` + `suite_prop_validator` |
| **ملفات** تحت `/data/adb/.config/MaxManager` | **٣٥** مسارًا (كانت ٢٧ في نطاق أضيق) | نصوص حرّة (`app_status` سطر مفصول بمسافات، `current_modes` رقم، `gameinfo` سطر) | **لا** | ✅ `fixtures/contracts/file_paths.tsv` + `FilePathsContractTest` (حرسان: صدق واكتمال) |
| **CLI الخادم** | **١٣ عَلَمًا**: `--run · --rerun · --profile <n> [--from-ai] · --log · --verboselog · --clearlogs · --version · --help · --appactivity · --bypasspathlist · --checkbypasschg · --shownotifications · --hidenotifications` | argv | **لا** — `--version` يُطبع `MODULE_VERSION` وهو **إصدار بناء** يُعاد كتابته في CI ولا يحمل رقم البروتوكول | ✅ `fixtures/contracts/daemon_cli.tsv` + `DaemonCliContractTest` (العَلَم · المُرادف · الدالّة · **وموضع خطّ البوّابة**) · و`--profile` سلوكيًّا في `suite_cli_profile` |
| **CLI الثنائيات** | `profilesettings` (٦ أوامر + مرادفات) · `utilityconf` (١١ أمرًا) | argv | لا | ✅ الاثنان: `binutils_cli.tsv` و`profilesettings_cli.tsv` + `cargo test` في الحزمتين |
| **AIDL** `IRootNodeService` | 5 عمليات (`readText · readTexts · exists · writeText · listNames`) | **binder مُهيكل** | لا (لكنه **أفضل الأسطح اليوم**) | ✅ العقد (`RootNodeContractTest` + دلالتا `null`/`""`) · البقية: ربط binder حقيقي — جهاز |
| **بثّ** `nd.max.ACTION_MANAGE` | إشعار/توست من الخادم | Intent extras | لا | ✅ `fixtures/contracts/broadcast_extras.tsv` (٨ صفوف) + `suite_broadcast` (C، ٢٩ دعوى على السطر **المُصدَر فعلًا** عبر `__real_notify`) + `BroadcastContractTest` (Kotlin، صدق + اكتمال في الاتجاهين + إعلان المانيفست) |
| **أقفال** `java.lock` · `API/.lock` | قفلان | `flock` | لا | مساراهما ✅ (بجدول المسارات) · سلوك `flock` نفسه **يحتاج جهازًا** |

### ٤.١ اكتشافان مهمّان

**(أ) آليّتان مختلفتان للوصول إلى `uid 0` — بلا توحيد:**
- **binder/AIDL** إلى `RootNodeService` (مهيكل، قراءة دفعية، `null` تعني «لا قناة» لا فشل).
- **تنفيذ صدفة/CLI** (`sys.maxmanager-service`, `utilityconf`, `su -c`).

فنفس القدرة تُنجَز بطريقتين، ولكلٍّ دلالة فشل مختلفة — وهذا بالضبط مصدر «حدود موحلة».

**(ب) كتابة مشتركة بلا عقد:** `app_status` **يكتبه** الرفيق Java و**يقرؤه** الخادم C (+`RefreshRateHandler`)،
و`current_modes` **يكتبه** Max AI (Kotlin) و**يقرؤه** الخادم (`InotifyWatcher`). والشكل نصّي حرّ بلا
مخطَّط — فأي تغيير في صيغة السطر **يكسر الطرف الآخر في صمت**.

---

## ٥. دورة الحياة والإقلاع/الاستعادة (مقيسة من `mainfiles/`)

```
post-fs-data.sh  ← قفل نسخة واحدة (/dev/.maxmanagerSingleInstance) · مضاد bootloop (count.sh)
                 ← قتل بقايا sys.maxmanager-service و sys.maxmanager-appmonitoring (KSU soft-reboot)
                 ← مسح أقفال قديمة (java.lock · API/.lock)

service.sh       ← ينتظر sys.boot_completed · يصفّر العداد · --clearlogs
                 ← بوابة تعافي الحزمة (priv-app ← install-existing ← نسخة بيانات)
                 ← nohup app_process … nd.max.AppMonitor  (الرفيق Java)
                 ← sleep 1 && exec sys.maxmanager-service --run  (الخادم C، detached)
```

**نقاط قوّة قائمة:** مضاد bootloop · قفل نسخة واحدة · معالجة soft-reboot · سكربت uninstall · تعافي
الحزمة بثلاث طبقات · `daemon_state` لاستعادة الحالة بعد restart · `recover_per_app_thermal_policy`
لمعاملة حراريّة مبتورة.

**نقاط ضعف:** (١) **لا مصافحة إصدار** بين التطبيق والوحدة — التطبيق قد يُحدَّث والوحدة لا (و`check_module_version`
يفحص وجود الوحدة لا توافق العقد). (٢) **الاقتران بالحياة:** الخادم **يُنهي نفسه** إذا حُرّر قفل الرفيق
Java (`java_daemon_died`) — قرار مقصود ومُعلن، لكنه يعني أنّ موت الرفيق يُسقط كل المحرّك. (٣) الخادم
يُشغَّل بـ`exec` بلا مشرف (supervisor) — لا إعادة تشغيل تلقائي إن مات (خارج إقلاع كامل).

---

## ٦. نموذج root/الصلاحيات

| العملية | كيف تحصل على الجذر | المخاطرة |
| --- | --- | --- |
| `service.sh` | يعمل كـroot من مدير الوحدة (Magisk/KSU/APatch) | مشروط بمدير معروف |
| الخادم C · الثنائيات · thermalcore · preloadbin | يورثون root من `service.sh` | **كلها uid 0** |
| رفيق Java (`AppMonitor`) | `app_process` من `service.sh` + `HiddenApiBypass` | **كود التطبيق نفسه يُنفَّذ كـroot مع تجاوز الواجهات المخفيّة** |
| `RootNodeService` | `libsu` `RootService` (binder) | **الأفضل عزلًا**: سطح واحد مُهيكل (٥ عمليات) وبلا صدفة |
| عملية الواجهة | `su` عند الطلب | الحد الأدنى |

**القراءة:** سطح الجذر **واسع** بطبعه في تطبيق من هذا النوع، لكن **الأنظف فيه** هو `RootNodeService`
(AIDL مُهيكل، بلا صدفة، بلا تخمين أوامر). وأوسعُه هو **رفيق Java** لأنه يحمل منطق التطبيق كاملًا
(2,885 سطرًا + Atlas + بوابات عتاد) داخل `uid 0`.

---

## ٧. crash isolation وreliability

| السؤال | الواقع المقيس |
| --- | --- |
| هل انهيار جزء يسقط الباقي؟ | **لا** — العمليات منفصلة (عزل جيد بالفعل) |
| لكن… | **الخادم C يسقط عمدًا إذا مات رفيق Java** (`java.lock` يُحرَّر → `EVENT=DAEMON_STOPPED`) |
| إعادة تشغيل تلقائية؟ | **لا مشرف**: موت الخادم لا يُعيد إطلاقه إلا بإقلاع جديد أو `restartservice` |
| استعادة الحالة؟ | `daemon_state` + `recover_per_app_thermal_policy` — موجودة |
| أقفال معلّقة بعد soft-reboot؟ | تُمسح في `post-fs-data.sh` — موجودة |
| مضاد bootloop؟ | موجود (`count.sh`) |

⇒ **العزل ممتاز، والتعافي متوسط:** المكوّنات لا تُسقِط بعضها، لكن لا شيء يُعيد تشغيل ما مات.

---

## ٨. الأداء · RAM · البطارية — ما يُقاس هنا وما لا يُقاس

**يُقاس (من الشيفرة):**
- الخادم C: **حلقة event-driven** — `poll` بمهلة `-1` في السكون ⇒ **صفر استيقاظ دوري** عند الخمول.
- الرفيق Java: **حلقة استطلاع كل ٥٠٠ م.ث** (`POLL_INTERVAL_MS`) + فحص انحراف كل ١٠ ثوان ⇒ استيقاظ
  دوري مستمر طوال التشغيل.
- عدد العمليات المقيمة: **≥ ٥** (واجهة · عامل جذر · رفيق Java · خادم C · thermalcore عند التمكين).

**لا يُقاس هنا (يحتاج جهازًا):** استهلاك RAM لكل عملية · نسبة CPU الحقيقية للحلقة ٥٠٠ م.ث · أثر
البطارية · زمن JNI مقابل binder.

**ملاحظة محايدة:** الفارق بين الحلقتين (event-driven مقابل استطلاع ٥٠٠ م.ث) **مرشّح حقيقي** لتحسين
بطارية/CPU — لكن **لا يُقدَّم كرقم** بلا جهاز.

---

## ٩. Testing وdebugging

| الطبقة | الاختبارات | ملاحظة |
| --- | --- | --- |
| Kotlin | **156 ملفًا · 1,587 اختبارًا · 28,687 سطرًا** | تغطية قويّة لـMax AI/Atlas/Arbiter |
| Rust (الكل) | **59 `#[test]`** | `cargo test` في CI |
| C (خادم) | **69 دعوى مضيف** (جديدة) | `archdaemon/tests/` — منطق محض بلا جهاز |
| **العقود/الحدود (IPC)** | **0** | ❌ **لا اختبار واحد لـprops/files/CLI/AIDL** — وهذه الفجوة الكبرى **(لقطة الأساس قبل §١٣؛ والحصيلة بعد الإغلاق في §١٥: كل سطح في §٤ صار له عقد يُقاس على المضيف)** |

**debugging:** سجلّ موحّد (`debug/MaxManager.log` + verbose + preload) بمعرّف تبديل `sw=<id>` يربط
الخادم والتطبيق لنفس الحدث، `DiagnosticCenter`، `LogsViewer`. جيدة — لكن بلا عقد مُعلَن للصيغة.

---

## ١٠. تأثير الفصل على Feature Parity

- **الوظائف الحسّاسة للفصل:** الملف العام (عبر `profilesettings`)، شحن التجاوز، preload، thermalcore،
  تتبّع PID، ومراقبة قفل Java — كلها **داخل الخادم C**، وموروثة من Encore/AZenith.
- **الخطر المقيس:** أي إعادة تصميم لهذه الحدود **تُسقط سلوكًا لا يمكن إثباته هنا** (يحتاج جهازًا).
  ومقابلتنا أثبتت أن اتجاه MaxManager **يضيف** (٦ ملفات لا نظير لها: `MaxManager.h` · `MaxManagerUtility/*`
  · `PerAppKernel.c` · `PerAppThermal.c`) ولا يحذف.
- ⇒ **الخلاصة: الفصل/الدمج لا يُقرَّر هنا.** ما يُقرَّر هو **تثبيت العقد** حتى يصبح تغيير أي طرف آمنًا.

---

## ١١. مواضع اتصال المكوّنات الأصلية (لا إعادة تصميم)

| المكوّن الأصلي | **لا يتغيّر** | موضع اتصاله بالخادم |
| --- | --- | --- |
| **Max AI** | منطقه وقراره | **يكتب** `API/current_modes` (يقرؤه الخادم) · **يطلب** الملف عبر `profile --from-ai` |
| **Safety Engine / Governor** | حدوده وفيتواته | يمنع الطلب **قبل** بلوغ الحدود — لا اتصال مباشر بالخادم |
| **Arbiter** | ملكية المفاتيح والـjournal | يوجّه الكتابة إلى `RootNodeService` أو مسار العتاد — لا اتصال بالخادم C |
| **Atlas** | خريطة التنفيذ | يعمل في الواجهة والرفيق Java |
| **Profile System** | تعريف الملفات | **يعبُر** إلى الخادم عبر `--profile` فقط (مالك واحد) |

**الترتيب المقصود (يُثبَّت لا يُغيّر):** **Max AI يقرّر ← Safety يفرض ← Arbiter/Atlas ينفّذ ← الخادم
يخدم عمليات النظام ويعيد حالة موثّقة.**

---

## ١٢. العقود — **مُعتمدة ومُجمَّدة v1** (اعتماد المالك)

**المبدأ:** لا نغيّر العمليات. نُعلن العقود التي تعمل اليوم **ضمنيًّا** ونثبّتها في ملف واحد لكل قناة،
ومعه اختبار عقد. **والجداول أدناه ليست تصميمًا مُتخيَّلًا — كل صفّ فيها مقيس من المصدر نفسه**
(بأرقام الأسطر وقت الاعتماد)، وكل صفّ صار له اختبار يفشل لو انحرف.

**مصافحة الإصدار — `PROTOCOL_VERSION = 1`.** تُعلَن في السطر الأول من `app_status` (`v 1`) وسطر
`v=1` في `per_app_hw_status`. والقارئان **يتجاهلان ما لا يعرفانه** أصلًا (مطابقة بادئة لا مطابقة
كاملة)، فإضافة سطر الإصدار **إضافة لا كسر** (ADR-05) — وهو مقيس باختبار على القارئ C نفسه.

**⚠️ وتصحيح مقيس (جولة CI):** كان مكتوبًا هنا إن `--version` يُعلن `PROTOCOL_VERSION` عبر
`MODULE_VERSION "V1"` في `MaxManager.h:99`. **وهذا خطأ في التشخيص لا في الكود:**
`.github/scripts/verify.sh` (الخطوة ٣ في `build.yml`) **يعيد كتابة** ذلك السطر قبل كل بناء إلى
`<نسخة> (<عدّ الالتزامات>-<sha>-<نوع>)` — فهو **إصدار بناء** لا رقم بروتوكول، وقيمة أي حزمة
نُشحنها ليست `V1`. وما كان يشترط `"V<رقم>"` **سقط في CI على كل تشغيل** حتى صُوّب (المقياس:
نسخة نظيفة من الالتزام + `verify.sh` ⇒ `BUILD FAILED`، ثم `BUILD SUCCESSFUL` بعد التصويب).
والتصويب لم يُلغِ الحرس بل وجّهه إلى ما هو حقيقيّ: **قارئ C يطابق ٨ حقول بيانات بالاسم**
(`StatusMonitor.c`) — وهذا العقد عبر اللغتين صار مقيسًا باسمه لا مُعلَنًا في Kotlin وحدها.

### ١٢.١ العقد بين **التطبيق ← الخادم** (Control) — مقيس من `BinaryCLI/CLIUtility.c`

| البند | المقيس |
| --- | --- |
| المدخل | `sys.maxmanager-service --profile <n> [--from-ai]` — `n` نصّي، و`--from-ai` يُقبل في **أي فهرس ≥ ٣** (`CLIUtility.c:42`) |
| بوّابة التوفّر | `require_daemon_running()` قبل كل أوامر ما بعد `--rerun`: خادم غير عامل ⇒ `stderr` سطران + خروج **1** (`Main.c:67` · `CLIUtility.c:196`) |
| بوّابة الوضع التلقائي | تقرأ `persist.sys.maxmanagerconf.AIenabled`؛ و`"1"` بلا `--from-ai` ⇒ `EVENT=CLI_PROFILE_MANUAL_BLOCKED ai=1` + خروج **1** (`CLIUtility.c:51`) |
| المخرج | `run_profiler(n)` يكتب `API/current_profile` **و**`/data/data/nd.max/API/current_profile` بصيغة `"%d\n"` (`ProfileUtility.c:71-72`) ويعيّن `sys.maxmanager.perapp.governor_isolation` |
| أحداث السجل | ن=١/٢/٣ ⇒ `EVENT=CLI_PROFILE_APPLY profile=PERFORMANCE\|BALANCED\|ECO` · ن=٠ ⇒ `EVENT=CLI_PROFILE_APPLY_REJECTED reason=profile_zero_is_initialize_only` (`CLIUtility.c:60,72,79`) |
| حالات الخطأ | لا وسيط: `ERROR: Missing profile number. Use --profile <1\|2\|3>` + خروج **1** · قيمة خارج ٠..٣: `Invalid profiles.` + خروج **1** |
| حالة ن=٠ | **تُعلَن وتحمل خروج ٠** (ليست فشلًا: «لا شيء يُنفَّذ» مقصود) — تُثبَّت كما هي ولا تُغيَّر |
| الملكية | **الخادم وحده** يطبّق الملف العام (مثبَّت بـ`profilesettings` الذي يرفض غير الخادم) |
| توافق الإصدار | `--version` ⇒ `V1` |

### ١٢.٢ العقد بين **الرفيق Java ← الخادم** (State)

**أ. `app_status`** — `/data/adb/.config/MaxManager/app_status`، سطر لكل حقل، `key value` مفصولة بفراغ، LF.
الكاتب **واحد**: الرفيق (`AppMonitor.buildStatus` عبر `AppStatusProtocol`). القارئ **واحد**: الخادم
(`AppLoader/StatusMonitor.c:24`، يُنادى من `System.c:97` وعند حدث inotify على اسم الملف، `InotifyWatcher.c:159`).

| الحقل | عند الكاتب | ما يقرؤه C | الحدّ |
| --- | --- | --- | --- |
| `v` | `1` (ثابت) | *يتخطّاه* (لا مطابقة بادئة) | سطر أول |
| `focused_app` | `pkg pid uid` | `sscanf(line+12,"%127s %d %d")` | ٣ رموز |
| `screen_awake` | `0\|1` | `sscanf("%d")` | int |
| `battery_saver` | `0\|1` | `sscanf("%d")` | int |
| `zen_mode` | int | `sscanf("%d")` | int |
| `battery_level` | int | `sscanf("%d")` | int |
| `is_charging` | `0\|1` | `sscanf("%d")` | int |
| `app_name` | نصّ | `strncpy(…, line+9)` + قصّ `\n` | حجم الحقل |
| `switch_id` | `sw-<millis>` | `sscanf(line+10,"%31s")` | ≤ ٣١ محرفًا |
| `refresh_rate` · `max_refresh_rate` · `perapp_active` | أرقام | **يتجاهلها الخادم** (تُقرأ في Kotlin) | — |

**قواعد مقيسة:** مفتاح **مجهول يُتخطّى** (توافق أمامي) · وغياب مفتاح يُبقي **الافتراضي المُعلن** للخادم
(`app_name = "Unknown"` · `battery_level = -1` · الباقي صفر) · و**إعادة تعيين** كل الحقول قبل القراءة
(`StatusMonitor.c:30-35`) فلا تتسرّب حالة دورة سابقة.

**ب. `API/current_modes`** — نطاق القيمة **`{"0","1"}`** + LF. الكاتب Max AI
(`MaxAiEngine.setAiEnabled` ⇒ `echo $value >`)، والقارئان الخادم عند الإقلاع (`System.c:108`) وعند حدث
inotify (`InotifyWatcher.c:176`). **والدلالة مقيسة:** كل ما لا يساوي `"1"` حرفيًّا يُقرأ **مطفأً** — فقيمة
مشوّهة تُقرأ «إيقافًا» لا خطأً.

**ج. `runtime/per_app_hw_status`** — قناة Kotlin↔Kotlin اليوم (الكاتب الرفيق، والقارئ الواجهة/التشخيص؛
لا قارئ C بعد). الصيغة المُعلَنة في `core/hardware/PerAppHardwareStatus.kt`: سطر `pkg=<pkg>` · سطر
`at=<millis>` · ثم سطر لكل مقبض `knob=… outcome=… reason=… expected=… live=…`. و`outcome` **enum معلن**:
`applied · blocked · unsupported · not-writable · not-verified · skipped` — وقيمة خارجها **لا تُفسَّر
نجاحًا** (تُقرأ كما هي، والرمز المجهول يبقى مجهولًا).

| البند | المقيس |
| --- | --- |
| حالات الخطأ | لكل سطر حالة مستقلة (`applied`/`blocked`/`unsupported`/`not-writable`/`not-verified`/`skipped`) — لا «نجاح» ضمني |
| حالة التحقّق | `Outcome` enum معلن + `Record.isFailure` يعدّ كل ما ليس `applied`/`skipped` فشلًا |
| التنقية | كل قيمة: فراغات ⇒ `_`، وحد ١٦٠ محرفًا (`singleLine`) — فلا ينقسم السطر إلى حقلين |
| الملكية | الرفيق **يكتب** · الواجهة **تقرأ فقط** (اتجاه واحد مفروض) |
| توافق الإصدار | سطر `v=1` (يضاف مع تخطّي القارئ له) |

### ١٢.٣ العقد بين **التطبيق ← عامل الجذر** (RootNode) — *الأقوى اليوم*

`IRootNodeService` مُهيكل فعلًا (٥ عمليات: `readText` · `readTexts` · `exists` · `writeText` · `listNames`).
**القانون المُثبَّت:** `null` = **«لا قناة الآن» لا فشل** (الربط غير متزامن، والطريق الاحتياطي في
`RootFileAccess`؛ `RootNodeChannel.kt`) · `""` = **«لم تُقرأ»** · `readTexts` تُعيد `""` للمسار الذي لم يُقرأ ·
`exists=false` = «لا وجود» لا «لا صلاحية» · `writeText=true` = «نُفّذت» لا «تحقّقت» (التحقّق طبقة أعلى،
`WriteVerification`). **لا تغيير في الـAIDL** — العقد يُثبَّت باختبار، لا بإعادة تصميم.

### ١٢.٤ قواعد مشتركة لكل القنوات

- **اتجاه واحد لكل ملف** (كاتب واحد، قرّاء كثيرون) — يمنع تنازع الملكية.
- **لا قيمة مُخترعة**: المجهول يُعلَن (`status_unknown`) كما في ADR-07.
- **كل فشل يحمل سببًا نصيًّا** (نمط `EVENT=… reason=…` القائم).
- **لا انتظار بلا حدّ.**
- **الإضافة لا الكسر:** قارئ ملفات الحالة **يتخطّى ما لا يعرفه** — فلا يُشترط تحديث الطرفين معًا؛
  ويُثبَّت ذلك باختبار (مفتاح مجهول لا يُفسد بقية السطور).

### ١٢.٥ أين يُنفَّذ العقد — لا في وثيقة فقط

| القناة | الملف الذي يحمل العقد | الاختبار الذي يُسقط الانحدار |
| --- | --- | --- |
| Control (CLI) | `archdaemon/jni/src/BinaryCLI/CLIUtility.c` | `archdaemon/tests/cli_profile.c` + `fixtures/contracts/cli_profile.tsv` |
| State `app_status` | `AppMonitor` عبر `nd.max.core.platform.AppStatusProtocol` | `archdaemon/tests/file_protocols.c` + `nd.max.contract.AppStatusProtocolContractTest` |
| State `per_app_hw_status` | `nd.max.core.hardware.PerAppHardwareStatus` | `nd.max.contract.PerAppHardwareStatusContractTest` |
| Control `current_modes` | `MaxAiEngine.setAiEnabled` | `nd.max.contract.FileProtocolContractTest` + `fixtures/contracts/current_modes.*` |
| RootNode | `aidl/nd/max/core/ipc/IRootNodeService.aidl` | `nd.max.contract.RootNodeContractTest` |
| خصائص النظام | `MaxManagerProps.kt` + `PropValidator.c` + `props.rs` + `props.sh` | `fixtures/contracts/system_properties.tsv` + `SystemPropertiesContractTest` + `suite_prop_validator` |
| **CLI الثنائية `utilityconf`** | `binutils/src/utils/plan.rs` (تفكيك + جدول أثر خالص) | `cargo test` في `binutils` + `fixtures/contracts/binutils_cli.tsv` |
| **CLI الثنائية `profilesettings`** | `binprofiles/src/plan.rs` (تفكيك + `caller_is_trusted` + `should_run_external`) | `cargo test` في `binprofiles` + `fixtures/contracts/profilesettings_cli.tsv` + `ExecOwnershipContractTest` |
| **مسارات الملفات** | `fixtures/contracts/file_paths.tsv` (لاحقة + ملفات مُعلِنة لكل لغة) | `FilePathsContractTest` |
| **بثّ `nd.max.ACTION_MANAGE`** | `DaemonUtility.c::notify/toast` + `CLIUtility.c::clearlogs/hidenotifications` ↔ `MaxManagerReceiver.kt` | `archdaemon/tests/broadcast.c` (السطر المُصدَر فعلًا عبر `__real_notify`) + `nd.max.contract.BroadcastContractTest` + `broadcast_extras.tsv` |
| **CLI الخادم (١٣ عَلَمًا)** | `archdaemon/jni/Main.c` (الموزِّع) + `BinaryCLI.c` (نصّ `--help`) | `nd.max.contract.DaemonCliContractTest` + `daemon_cli.tsv` |
| **نواة القرار الحرارية (PID)** | `thermalcore/src/policy_manager.rs` (`PidController::compute`) | `cargo test` في `thermalcore` + `thermal_policy.tsv` |

**والرابط بين اللغتين ملفٌ واحد:** `fixtures/contracts/app_status.valid.txt` **يُنتجه** Kotlin بحرفيّته
ويُقرأ في C بالقيم المُعلَنة نفسها — فإن انحرف أحد الطرفين سقط أحد الاختبارين لا الوثيقة.

### ١٢.٦ تدقيق التغطية — **أي بند من حالات المُعلَنة محروس، وأيّها لا**

كتبتُ هذا الجدول **بقراءة العقود بندًا بندًا** ومقارنتها بحرّاسها، لا بمقارنة عدد الحرّاس بعدد البنود —
فذلك يخفي الفجوات. وأول نسخة منه أعلنت **ثلاث فجوات**؛ ثم أُغلقت الثلاث فعليًّا بما لا يحتاج جهازًا
(الأسطر المؤشَّرة ⬛ أدناه)، ويبقى في كل واحدة **بقية تحتاج جهازًا** أُعلنت معها ولم تُطوَ.

| البند | الحرس الفعلي | الحالة |
| --- | --- | --- |
| ١٢.١ شكل المدخل + `--from-ai` في أي فهرس ≥ ٣ | `cli_profile.tsv` (١٦ صفًّا، منها صفّ بعَلَمين) | ✅ |
| ١٢.١ بوّابة التوفّر (`require_daemon_running`) | فرعان مقيسان بعد أن كان الـstub يعلن الخادم عاملًا دائمًا | ✅ |
| ١٢.١ بوّابة MAX AI + أحداث السجل + رموز الخروج | ١٦ صفًّا بمقارنة `exit` و`log` و`notify` | ✅ |
| ١٢.١ ن=٠ تُقبل بخروج ٠ | صفّ مخصّص | ✅ |
| ١٢.١ `--version` ↔ `PROTOCOL_VERSION` | ❌ **مصحّح:** لا مصافحة هنا — `MODULE_VERSION` إصدار **بناء** يُعاد كتابته في CI، و C لا يقرأ سطر الإصدار أصلًا. والحرس المُثبَّت بدلًا منه: `AppStatusProtocolContractTest` يقرأ **بادئات `strncmp` الثمانية من `StatusMonitor.c` نفسها** ويقارنها بـ`DAEMON_CONSUMED_FIELDS`، ويشترط أن `MODULE_VERSION` إمّا `V<n>` وإمّا سلسلة إصدار CI (فلا يمرّ شكل ثالث بصمت) | ✅ (مُوجَّه إلى الحقيقة) |
| **⬛ ١٢.١ كتابة `API/current_profile`** | ✅ أُغلقت: `write2file` صار يُسجّل **المسار والصيغة والقيمة**، والدعوى تشترط أن الملفّين (`PROFILE_MODE` و`PROFILE_MODE_APP`) تلقّيا نفس الرقم المطلوب (١٦ صفًّا) | 📄 البقية: أن البايتات بلغت قرص **هاتف** — جهاز |
| **⬛ ١٢.١ ملكية الملف العام** | ✅ أُغلقت على مستوى **القرار**: `ExecOwnershipContractTest` يثبّت قراءة `/proc/<ppid>/cmdline`، والشرطين (والثاني **حامل لا زائد**: الأب قد يكون `sh` الذي يلفّه `systemv`)، والخروج بغير صفر عند الرفض، واتفاق الاسم مع `compile_zip.sh` | الـ✅ **توسّعت من النصّ إلى القرار**: `binprofiles/src/plan.rs::caller_is_trusted` صار يُقاس بـ`cargo test` على **سطري الأوامر الحقيقيين** (أب = الخادم · أب = `sh` الذي يلفّه `systemv`) وعلى `caller_is_trusted` — وفضفاضيَّة الشرط الثاني مُعلَنة بصفّ صريح لا مطويّة. 📄 البقية: **تشغيل** الثنائية كمستدعٍ غير الخادم على **جهاز** — أما **بناؤها فلم يبقَ مانعًا**: بُنيت لـ`arm64-v8a` و`armeabi-v7a` بـNDK r29 وقيست رموزها |
| ١٢.٢ حقول `app_status` + الافتراضي + تخطّي المجهول | `file_protocols.c` على ٣ ملفات | ✅ |
| ١٢.٢ مصافحة الإصدار في `app_status` | قياسها في القارئ C + إنتاجها في Kotlin | ✅ |
| ١٢.٢ `per_app_hw_status` + الرموز الستة + عدم اختراع النجاح | `PerAppHardwareStatusContractTest` | ✅ |
| ١٢.٢ نطاق `current_modes` والحرف الوحيد | `FileProtocolContractTest` (فيكسترات + حرف المقارنة في المصدر) | ✅ |
| ١٢.٣ العمليات الخمس بالضبط | `RootNodeContractTest` | ✅ |
| **⬛ ١٢.٣ دلالتا `null`/`""`** | ✅ أُغلقت **عبر أثرهما في المستدعين**: الـمُعلَن فراغيّ بكاتب خاص، ولا `!!` في أي مستدعٍ، و`""` تُطرح (`takeIf { it.isNotEmpty() }`) وتهبط إلى الطريق البديل · ومنع النوعية يُقاس أيضًا (جرّبنا الوصول المباشر فسقط البناء) | 📄 البقية: دورة حياة ربط binder حقيقية — جهاز |
| ١٢.٤ لا قيمة مُخترعة | رمز `outcome` مجهول + لا مفتاح بلا غطاء | ✅ (في القناتين، لا عمومًا) |
| **١٢.٤ «كل فشل يحمل سببًا» + «لا انتظار بلا حدّ»** | ❌ لم يُقس الشمول. والانتظار الوحيد في `binutils` **محدود ومقيس**: ثانية واحدة ثابتة في `setthermalcore` (لا حلقة تنتظر حدثًا) | ❌ (البقية: شمول الرسائل، وبقية الانتظارات) |

**والخلاصة الصادقة:** ما يُقاس الآن هو **السلوك والصيغة والقرارات والغطاء والوجهة** — وما يبقى غير مقيس
هو **الفيزياء**: أن البايتات بلغت قرصًا، وأن binder رُبط فعلًا، وأن الثنائية Rust **تنفّذ أثرها** على جهاز
(وقد **بُنيت** فعلًا لـABIين وقيست رموزها، فلم يبقَ منها إلا التشغيل) — ثلاثتها **يحتاج جهازًا**. وسُجّل ذلك في `HANDOFF` تكملة ١٢٤ ولم يُطوَ داخل «٢١٦ دعوى C + ٤٥ دعوى Kotlin».

**وثغرتان في هذا الـfixture نفسه وجدتُهما بالسؤال لا بالمراجعة، وأُغلقتا (١٥ طفرة أُسقطت):**

1. **مسار استطلاع `checkmalipath`** (`/sys/class/devfreq/*.mali`) كان **نصًّا حرًّا بلا حرس** — ومسار
   استطلاع خاطئ = `false` صامتة ⇒ يختفي قسم Mali من الواجهة بلا خطأ ظاهر. صار ثابتًا (`MALI_DIR_GLOB`)
   ونوع أثر **جديدًا** (`probe`: يُستطلَع لا يُكتب) — لأن استعمال `path` (يُكتب) كان سيكذب على القارئ.
2. **مفاتيح الخصائص الثلاثة في هذه الحزمة** (`debugmode` · `state` · `conf.fstrim`) كانت **خارج** حارس سطح
   الخصائص: `SystemPropertiesContractTest` يقرأ `binprofiles/src/props.rs` وحده، وصفوف الجدول التي تخصّ
   `state`/`fstrim` تقول `rust=-`. فصار للثلاثة حرس في `cargo test` يقيّدها بجدول السطح نفسه.

**وبقيت مساحة ثالثة أُعلنها ولا أُغلقها هنا (لأنها سطح آخر، لا بند في §١٣):** مسارات الملفات تحت
`/data/adb/.config/MaxManager` (§٤: **٢٧ مسارًا · ❌ لا يُختبر**) — وقد قِستُ عيّنة منها: مسار السجل الواحد
`…/debug/MaxManager.log` مُعلَن في **أربعة مواضع بثلاث لغات** (`binutils/src/utils/logger.rs` ·
`archdaemon/jni/include/MaxManager.h` · `manager/.../MaxManagerPaths.kt` · ونصّ `rm -f` في `CLIUtility.c`)
**ولا حرس يقارنها** (مقيس: لا إشارة إلى `MAXMANAGER_LOG` في شجرة اختبار Kotlin إطلاقًا، و`log_gate.py`
تحكم على حزمة سجل جهاز لا على اتفاق المسارات). فانحراف أحدها = سجل يكتبه الطرف ولا تراه الواجهة.

**وحدّان منهجيان أُعلنا لأنهما كلّفا قياسًا فعليًّا:**

1. **`--from-ai` وغيرها تُقاس لأن الطريق كامل يُنفَّذ**: بعد إزالة الالتفاف عن `run_profiler` لم يبقَ
   أمام الدعوى إلا الأمر الحقيقي وكتابة الملفّين الحقيقية — فالقياس على الدالّة لا على سجلّ بديل.
2. **حرس يقرأ النوعية يُسجّل ما يفرضه المُصرّف، ولا يُدّعي أمانًا مضافًا**: أول «طفرة» على قناة RootNode
   كانت حذف `?.` — **فسقط البناء لا الاختبار**، لأن Kotlin يفرض النداء الآمن على النوع الفراغي. فصارت
   الدعوى تُعلن ذلك: قيمتها أنها تحرس **النوعية نفسها** وألّا يعود `!!`. وما لا يفرضه المُصرّف — `!!` —
   هو ما كُذّب فعلًا وسقط الاختبار.

---

## ١٣. الـfixtures — المنفَّذ والمتبقّي

| الطبقة | الحالة | الملفات/النمط |
| --- | --- | --- |
| **منطق C المحض** | ✅ **مُوسَّع**: `archdaemon/tests/` صارت **٢١٦ دعوى + ٥ طفرات** (كانت ٦٩ + ١) | مضيف + شيم |
| **بروتوكول الملفات** | ✅ **مُنجَز**: `fixtures/contracts/` (`app_status` · `current_modes` · `per_app_hw_status`، منها سليم ومنها مشوّه) يُقرأ من الطرفين | `suite_file_protocols` (C) + `nd.max.contract.*` (Kotlin) |
| **CLI** | ✅ **مُنجَز**: `archdaemon/tests/cli_profile.c` على `CLIUtility.c` الحقيقي — الجدول `fixtures/contracts/cli_profile.tsv` | fixture «مرجع مقابل جديد» |
| **AIDL** | ✅ **مُنجَز**: `nd.max.contract.RootNodeContractTest` يثبّت العمليات الخمس ودلالتَي `null`/`""` — بلا جهاز | قراءة العقد من الـ`.aidl` |
| **خصائص النظام** | ✅ **مُنجَز**: `fixtures/contracts/system_properties.tsv` (٩٣ صفًّا: مالك · غطاء الخادم · مرآة Rust · مرآة الصدفة) | `SystemPropertiesContractTest` (٦ دعاوى) + ٩ دعاوى C في `suite_prop_validator` + طفرة رابعة |
| **ثنائيات Rust `utilityconf`** | ✅ **مُنجَز**: `binutils/src/utils/plan.rs` (تفكيك + أثر خالص) على **٦٧ أثرًا في ٢٣ مجموعة** | `fixtures/contracts/binutils_cli.tsv` + ١٠ دعاوى `cargo test` |
| **ثنائية Rust `profilesettings`** | ✅ **مُنجَز**: `binprofiles/src/plan.rs` (تفكيك + أسماء ومرادفات + **قرار الملكية** + قاعدة الاحتياط) على **٢٠ صفًّا** | `fixtures/contracts/profilesettings_cli.tsv` + ٥ دعاوى `cargo test` |
| **مسارات الملفات (٣٥)** | ✅ **مُنجَز**: `fixtures/contracts/file_paths.tsv` (لاحقة نسبةً للجذر — فحرس واحد يقيس C وKotlin وRust) | `FilePathsContractTest` (٦ دعاوى: صدق · اكتمال · نطاق · الجذر · تكذيب) |
| **بثّ `nd.max.ACTION_MANAGE`** | ✅ **مُنجَز** (كان آخر سطح «❌» في §٤): `fixtures/contracts/broadcast_extras.tsv` (٨ صفوف) — الطرفان مقيسان، و**اكتمال في الاتجاهين** | `suite_broadcast` (٢٩ دعوى C على السطر المُصدَر فعلًا) + `BroadcastContractTest` (٦ دعاوى Kotlin) + طفرة خامسة في `self-check` |
| **CLI الخادم (١٣ عَلَمًا)** | ✅ **مُنجَز**: `fixtures/contracts/daemon_cli.tsv` — العَلَم والمُرادف والدالّة **وموضع خطّ `require_daemon_running()`** (٩ قبله · ٤ بعده) — والحرس كشف **٤ أعلام غائبة عن `--help`** فأُضيفت | `DaemonCliContractTest` (٦ دعاوى) · و`--profile` سلوكيًّا في `suite_cli_profile` |
| **نواة القرار الحرارية (PID)** | ✅ **مُنجَز**: `fixtures/contracts/thermal_policy.tsv` (١٣ صفًّا · ٦ تشغيلات، بقيم **مشتقّة حسابيًّا**) — والربط صار مشروطًا بـ`android` فصار `cargo test` يعمل على المضيف | ٣ دعاوى `cargo test` في `thermalcore` · **١١/١١ طفرة أُسقطت** |
| **ما تبقّى من Rust** | 📄 **مُعلَن**: `preloadbin` (١٥٠ سطرًا C في `jni/main.c` — vmtouch: تثبيت صفحات، لا نواة قرار) · `manager/src/main/rust` (٥٩ `#[test]` قائمة في CI) · وبقية `thermalcore` (دورة المراقبة نفسها: `inotify` + قراءة `sysfs`) | يحتاج جهازًا للسلوك · أو تجريدًا لدورة المراقبة |
| **`thermalcore/src/prediction.rs` — ملفّ خارج شجرة الوحدات** | 📄 **مُسجَّل لا مُنظَّف** (ADR-18): لا `mod prediction` في `lib.rs`، ولو وُصل لَما تُرجم — يقرأ سبعة حقول لا وجود لها في `ThermalEvent` | إصلاحه **قرار سلوك** لا قرار تنسيق، فيبقى مُعلنًا |

**ما قِيس فعلًا في هذه الطبقة (وقُل كمًا لا وصفًا):**

- **C (٢١٦/٢١٦ · و٥/٥ طفرات أُسقطت):** `suite_file_protocols` تقرأ `app_status.valid.txt` بالقيم المُعلَنة،
  و**تُثبت أن سطر `v 1` ومفتاحًا مجهولًا وقيمة مشوّهة لا تُفسد بقية السطور**، وأن غياب الملف **لا يُصفّر**؛
  و`suite_cli_profile` تُثبّت **١٦ حالة** لـ`handle_profile` (منها الرفض اليدوي أثناء AI، وبوّابة التوفّر في
  فرعيها، و`--from-ai` بعد عَلَم غير معروف) — والأثر يُقاس على **الأمر الحقيقي** الذي تُصدره `run_profiler`
  غير الملتفّ حولها؛ و`suite_prop_validator`
  تُثبت أن **٩ مفاتيح مُعلَنة** (واحد عن كل صنف غطاء) لا تُحذف — وهي الدعوى التي كشفت عطب `gpu_studio`.
- **Kotlin (٤٥ دعوى في `nd.max.contract.*`، منها `BroadcastContractTest` و`DaemonCliContractTest` المُغلقتان لسطحَي §٤ الأخيرين، و`ExecOwnershipContractTest` و`RootNodeSemanticsContractTest` المُغلقتان لفجوتي §١٢.٦):**
  `AppStatusProtocol.encode` يُنتج `app_status.valid.txt`
  **بحرفيّته**؛ و`PerAppHardwareStatus` يُثبت دورة `encode→parse` وأن رمزًا مجهولًا **لا يُقرأ نجاحًا**؛
  و`RootNodeContractTest` يثبّت العمليات الخمس بالضبط؛ و`SystemPropertiesContractTest` يُثبت **الاكتمال في
  الاتجاهين** بين الجدول وإعلان Kotlin، وصدق كل ادّعاء غطاء مقابل المصادر الأربعة.
- **Rust (`binutils`) — ١٠ دعاوى خضراء، و١٥ طفرة ذات أثر أُسقطت كلها** (وطفرة إعادة تسمية عنوان بقيت **عمدًا** لأن العنوان ليس عقدًا، وواحدة كانت مرساة خاطئة عندي لا بقاءً):
  `fixtures/contracts/binutils_cli.tsv` يحمل **٦٧ أثرًا في ٢٣ مجموعة** (٧ مسارات · ١ استطلاع · ١٥ أمر صدفة · ٣٧ `setprop` · ٧ `resetprop`)،
  ودعوى واحدة تعيد تشغيل التفكيك والأثر على الجدول،  وأخرى تُقلب فيها **كل** قيمة متوقّعة (٦٧ طفرة داخلية) لتُثبت
  أن المقارنة ليست فارغة، وأخريان تثبّتان عقد `checkmalipath` (النصّ **ورمز الخروج** معًا) وفرق الحذف في
  `prop_invocation` (`setprop` بقيمة فارغة ≠ `resetprop --delete`) — والجدول **عضو في القياس**: حذف صفّ
  واحد يُسقط الدعوى (فالأعداد مفروضة بالمساواة الدقيقة لا كحدّ أدنى).
  **ولماذا «تجريد `systemv`» كان شرطًا مُعلنًا:** الربط مع `liblog` كان غير مشروط، فكان `cargo test`
  يسقط على المضيف (`unable to find library -llog`) — وهذا سبب مقيس لبقاء الثنائية بلا اختبار واحد.
  وصار الربط `#[cfg(target_os = "android")]` وحده، والفرق **مقيس على المخرج لا موصوف**: ثنائيّا الـABI
  يطلبان `liblog.so` ويستعملان `__android_log_write` الحقيقي (`llvm-readelf -d` · `llvm-nm -D`)،
  والمضيف لا يطلب أيًّا منهما.

**وعطبٌ حقيقي وُجد بهذه الطبقة (لا بالمراجعة):** أربعة مفاتيح `persist.sys.maxmanager.gpu_studio.*` كانت
داخل نطاق فحص الخادم بلا غطاء ⇒ تُوسم `STALE_PROP` **وتُحذف عند كل إقلاع**. أُصلح ببادئة رابعة في
`PropValidator.c` (وقياسه مكتوب بجانبها)، وصار له حرّاسان. راجع `fixtures/contracts/README.md`.

**وأخيرًا: المرجع مقابل الجديد** — الـfixtures تثبّت **السلوك الحالي** كمرجع (reference behavior)،
فأي إعادة تنفيذ لاحقة تُقاس عليه. **ولا يُحذف تنفيذ قديم قبل ثبات البديل** (شرط المالك).

**حدّ هذه الطبقة (يُعلن لا يُطوى):** كل ما هنا يقيس **المنطق والصيغة** لا الجهاز. لا عقدة sysfs تُفتح، ولا
`inotify` يعمل، ولا ثنائية ARM تُنتج — وما يقيسه المضيف يدلّ على «الشكل سليم»، ولا يدلّ على «الجهاز يعمل».
**وحدّ ثانٍ مقيس:** حرّاس Kotlin التي تقرأ ملفات المستودع **ليست مُدخَلات لمهمة Gradle**، فتغيير
`PropValidator.c` وحده لا يُبطل نتائجها مخزّنة — تُعاد بالقوة (`--rerun-tasks` أو حذف مجلد النتائج). وهذا
يُكتب لأنه كلّفني تكذيبًا كاذبًا قبل أن أكتشفه، لا لأنه نظري.

---

## ١٤. ما الذي تغيّر / ما الذي لم يتغيّر

| تغيّر | لم يتغيّر |
| --- | --- |
| **وُثّق المعمار القائم بالقياس** (هذه الوثيقة) | **لا ملف نُقل** · لا خادم أُعيد كتابته · لا فصل/دمج |
| **حُدِّدت الفجوة الحقيقية:** لا عقود ولا اختبار حدود | **كل المكوّنات الأصلية** (Max AI · Atlas · Safety · Arbiter · Profile) كما هي |
| **صُمِّمت العقود والـfixtures** للاعتماد | **لا تغيير سلوكي** من هذا الـAudit إطلاقًا |

---

## ١٥. نتائج الاختبارات المُرافقة

```
Kotlin : :app:testReleaseUnitTest → **170 suite · 1632 اختبارًا · 0 فشل · 0 خطأ · 0 متخطّى** (منها ٤٥ في nd.max.contract)
Rust   : 59 #[test] في حزمة التطبيق + **10** في `binutils` + **5** في `binprofiles` + **3** في `thermalcore` (الأربع في CI بـcargo test)
C استضافة: archdaemon/tests → **216/216** + self-check (٥ طفرات تُسقط الدعاوى)
Rust ثنائيات: binutils → 10/10 على binutils_cli.tsv + 15 طفرة · binprofiles → 5/5 على profilesettings_cli.tsv + 6 طفرات
‏thermalcore: 3/3 على thermal_policy.tsv + **11 طفرة أُسقطت كلها** (ومعها تغيير الجدول: حذف صفّ يُسقط دعوى الحجم)
مسارات : FilePathsContractTest → 6 دعاوى · وطفرتان على المصدر نفسه (تباعد C↔الآخرين · مسار يُضاف بصمت) أُسقطتا
بثّ     : suite_broadcast → 29 دعوى C على السطر المُصدَر فعلًا · BroadcastContractTest → 6 دعاوى
         · و٤ طفرات مصدرية أُسقطتها (إعادة تسمية extra في C · مفتاح مختلف في المستقبِل · نقل `--version` عبر خطّ البوّابة · حذف عَلَم من `--help`)
بوابات : kt_balance · code_health · i18n(+prune) · jni_symbols · license(--assert+self-test 28/28)
         · repo_audit · source_manifest --check --assert · sepolicy_matrix · dead_modules  → كلها خضراء
```

**وتجربة خطوات CI محليًّا على نسخة نظيفة من الالتزام نفسه** (`git worktree` — أي ما يراه CI بالحرف)،
لا على مجلّد العمل. والسبب: مستودع Actions **خاص** و`gh` غير موثَّق هنا، فلا دفع ولا قراءة لتشغيل؛
فالتجربة هي أقرب قياس متاح — وقد **وجدت عطبًا كسر البناء** لحسن الحظ قبل الدفع:

```
٢ checkout كامل        ✅   ٣ verify.sh (حقن الإصدار) ✅   ٤ Contract gates ✅ (19 ث، ومنها dead_modules)
٥ دستور الأقران        ✅ 216/216 + 5/5 طفرات        ٦ changelog ✅       ٨ سلسلة إصدار الخادم ✅
١٧–٠٢ cargo tests ×٤   ✅  ٢١–٢٢ مكتبة JNI + تأكيد العمودين ✅ (1,164,912 · 864,688 بايت)
٢٣ jni_symbols --require-binaries ✅ **الطبقة ٢ مقيسة محليًّا لأول مرة**: ٢١ تصريحًا في الـ`.so` الحقيقية
                                          للـABIين · نواقص ٠ · يتامى ٠
٢٤ بناءات أصلية ×٥ ×عمودين ✅ (archdaemon · preloadbin · thermalcore · binprofiles · binutils)
      وبصفر سطر خطأ/تحذير في `native-build.log`
‏testReleaseUnitTest --rerun  ✅ ١٧٠ مجموعة · ١٦٣٣ اختبارًا · ٠ فشل · ٠ خطأ · ٠ متخطّى (منها ٤٦ عقدًا)
‏assembleDebug                ✅ BUILD SUCCESSFUL (4:53)
‏step 40 Validate APK (فرع debug) ✅ apksigner v2 · الاسم والرمز يطابقان المحقون
‏minifyReleaseWithR8           ✅ BUILD SUCCESSFUL (6:13)
⛔ assembleRelease (فرع release) — يحتاج `KS_PWD`؛ توقيع الإصدار لا يُختبر محليًّا (AGENTS.md §5)
⛔ steps ٤١–٤٣ (الحزمة/zip/القطع) وتحميل تيليجرام — تتبع APK الإصدار أو أسرارًا/شبكة
```

**والعطب الذي كشفته التجربة وأُغلق:** `AppStatusProtocolContractTest` كان يشترط `MODULE_VERSION "V<رقم>"`،
و`verify.sh` **يعيد كتابته** قبل Gradle ⇒ **سقط في النسخة النظيفة بعد الحقن** (`BUILD FAILED` مُثبت،
8 دقائق). والحرس الآن يقيس ما هو حقيقيّ: **بادئات `strncmp` الثمانية من `StatusMonitor.c`** مقابل
`DAEMON_CONSUMED_FIELDS` (عقد أسماء عبر لغتين لم يكن مقيسًا في C إطلاقًا)، وأنّ `MODULE_VERSION` إمّا
`V<n>` وإمّا سلسلة إصدار CI — وبعد التصويب: `BUILD SUCCESSFUL`.

---

## ١٦. المخاطر المتبقية

1. **`uid 0` واسع** — خصوصًا رفيق Java (كود تطبيق كامل كـroot مع `HiddenApiBypass`).
2. **بلا مصافحة إصدار** بين التطبيق والوحدة ⇒ تحديث أحدهما منفردًا قد يكسر العقد.
3. **موت الرفيق يُسقط الخادم** (مقصود، لكنه نقطة فشل واحدة بدلالة غير معلَنة للمستخدم).
4. **بلا مشرف** ⇒ لا إعادة تشغيل تلقائي للخادم في التشغيل الجاري.
5. **صيغ ملفات غير مُصدَّرة** ⇒ كسر صامت محتمل عند أي تغيير.
6. **أداء/بطارية** غير مقيسين هنا (يحتاجان جهازًا) — والفارق بين حلقة الخادم (event) وحلقة الرفيق
   (٥٠٠ م.ث) مرشّح حقيقي للفحص على الجهاز.

---

## ١٧. الأثر على ترتيب العمل (بحسب أمر المالك §٨)

```
Architecture Audit  ✅ هذه الوثيقة
        ↓
Daemon decision + boundaries   → ✅ القرار: **A** · الـdaemon يُبنى والتصريف **مُتحقَّق بـNDK r29**، وحاضر في `system/bin/` داخل الحزمة (§٠.١.١.أ) · والعقود (§١٢) **مُعتمدة ومُجمَّدة v1**
Naming drift                   → ✅ وُحِّد: اسم رسمي واحد `sys.maxmanager-service` في `android/aosp` + `android/kernelsu` — §٠.١.١.ب (تكملة ١١٢)
AOSP/KernelSU functional gap   → ✅ init يشير الآن إلى `/system/bin/sys.maxmanager-service` الموسوم، والقالبان معزولان مُوثَّقان (لم يُحذفا)
        ↓
Parity fixtures design         → ✅ §١٣
        ↓
Parity tests                   → ✅ **اكتمل — كل سطح في §٤ له عقد يُقاس هنا**: بروتوكول الملفات + CLI الخادم (C)
                                 + **١٣ عَلَمًا للخادم** + CLI الثنائيتين (`utilityconf` و`profilesettings` في Rust)
                                 + AIDL + جدول الخصائص + **مسارات الملفات (٣٥)** + **بثّ `ACTION_MANAGE`**
                                 + **نواة قرار `thermalcore` (PID)** — كلها على المضيف بلا جهاز
        ↓
Safe migration                 → غير مطلوب أصلًا ما لم يُثبت Audit خلاف ذلك
        ↓
Benchmark · Real-device validation · Cleanup   → يحتاج جهازًا
```

**والمتبقّي عليك بعد اعتماد §١٢:** لا قرار حاجز. والطبقتان المُوعَدتان (جدول الخصائص وثنائيات Rust)
أُنجزتا على العقد المُجمَّد نفسه — ثم **مسارات الملفات (٣٥) وثنائية `profilesettings`** — وسُجِّلتا في `HANDOFF`.

**وما تبقّى بعد هذا، صنفان لا ثالث (كلّها مُعلَنة لا مطويّة):**
1. **يحتاج جهازًا** — الفيزياء (بايتات على قرص · ربط binder · تشغيل ثنائية كمستدعٍ غير الخادم · كتابة `sysfs` ·
   `inotify` · الإقلاع/SELinux) والأداء/البطارية وتوقيع الإصدار (`KS_PWD`).
2. **سلوك داخلي بلا سطح CLI** — `preloadbin` (vmtouch: تثبيت صفحات، لا نواة قرار) · `manager/src/main/rust`
   (٥٩ `#[test]` قائمة) · و**دورة** `thermalcore` نفسها (`inotify` + قراءة `sysfs`)؛ أمّا **نواة قرارها — PID —
   فقد أُغلفت وقِيست هذه الجولة**. وتثبيت الدورة نفسها يحتاج تجريدًا لها (عمل آخر لا بند في §١٣).

**وقد أُغلق الصنف الذي كان مُعلَقًا على قرارك:** بثّ `nd.max.ACTION_MANAGE` لم يُعد يحتاج قرار «عقد أم واجهة
داخلية» — قِسناه كعقد، لأن الأثر المُشترك (اسم مفتاح بين C وKotlin) هو نفسه صنف العطب الصامت الذي قدّرت
البقية بإغلاقه. وما يبقى منه **يحتاج جهازًا**: أن يصل الـ`Intent` فعلًا وأن يعرض النظام الإشعار.

---

## ١٨. جرد الوحدات الميتة — ملفّ موجود **لا يدخل أي بناء** (مقيس بأداة تُقيس نفسها)

**الفرق الذي يُقاس هنا مقصود:** «مُعلَّقة-غير-مُستدعاة» ليست «ميتة». الملف **داخل** شجرة الوحدات يُصرَّف
ويُقاس وإن لم ينادِه أحد؛ والميت صنفان فقط:

| الصنف | التعريف | لماذا لا يراه المُصرّف |
| --- | --- | --- |
| **Rust خارج الشجرة** | ملفّ `.rs` لا `mod <name>;` (ولا `#[path]`) يصل إليه من `lib.rs`/`main.rs` | الملف غير المُعلَن **لا يُصرَّف أصلًا** — فلا خطأ ولا تحذير، وثمرته تعفّن صامت |
| **C بلا مُستخدِم** | وحدة تُبنى فعلًا (بويلدكارد `Android.mk`: `Main.c` + `src/*/*.c`) ولا يشير أي تعريف فيها (دالّة أو بيانات) من خارجها | تُصرَّف وتُربط وتُشحن — والفقد كود ممتد لا يُنفَّذ |

**والأداة `tools/dead_modules.py`** (مع `--self-test` كما بقية البوابات، و`--json` للقياس): نطاقها مُعلَن
فيها، والأساس مُعلَن فيها أيضًا — فإضافة وحدة ميتة **أو** إزالة وحدة من الأساس تُسقط `--assert`؛ فالمرور
يستلزم قرارًا مكتوبًا لا انزلاقًا.

**النتيجة المقيسة على الشجرة:**

| المجال | العدد | الميت |
| --- | --- | --- |
| Rust (‏`thermalcore` · `binutils` · `binprofiles` · `manager/src/main/rust`) | ٤١ ملفًّا | **١** — `thermalcore/src/prediction.rs` |
| C الخادم (`Main.c` + ٣٣ وحدة تحت `src/*/*.c`) | ٣٤ ملفًّا | **٠** |
| ترويسات C (‏`archdaemon/jni/include`) | ٣ | **٠** بلا مُضمِّن |

**وثلاثة ليست «ميتة» بالمعنى المقيس لكنها لا تدخل أي حزمة نشحنها — تُقاس وتُعلَن ولا تُسقط البناء:**

1. **`thermalcore/src/simulator.rs`** (٩٠ سطرًا) موصول بـ`#[cfg(feature = "simulator")]`، والخاصيّة مُعلَنة
   في `Cargo.toml` **ولا باني في المستودع يُفعّلها** (مقيس: لا ذكر لها في `.github/` ولا `android/` ولا
   `mainfiles/`) ⇒ لا تدخل أي حزمة. **ولم أَحذفها** لأنها أداة مطوّر اختيارية، **وقِستُ أنها تُصرَّف**:
   `cargo check --features simulator` ينجح (بتحذير استيراد غير مستعمل واحد) — فليست كودًا ميتًا بالمعنى
   المقيس أعلى الجدول، بل مسار **غير مُختبر في CI**، وهذا حدّ يُقال.
2. **`archdaemon/jni/Makefile`** (٢١ سطرًا) — مسار بناء ثالث للخادم نفسه (مع `Android.mk` و`Android.bp`)
   **صفر إشارة إليه** في المستودع كله (مقيس بـ`grep`). لم يُحذف (ADR-18)، ويُسجَّل هنا: مسار بناء لا
   يشغّله شيء — وشِقّه الخطر أنه يمنح ثقة كاذبة بأن للخادم طريقًا يُختبر غير طريق CI.
3. **مرجع معلَّق في `android/aosp/Android.bp`** — وحدة `libmaxmanager_native` تُعلن مصدرًا
   `runtime/daemon-rust/src/lib.rs` **غير موجود** (مقيس: `android/aosp/runtime/` لا وجود له أصلًا).
   والملف نفسه يعلنها دينًا مُوثَّقًا لم يُمَس (ADR-18)، فتسجيل لا تنظيف.

**ونقطتان منهجيتان ضمنا صدق الأداة:**

1. **الأداة تُقاس قبل أن تُصدَّق** — `--self-test` على شجرة مُصنَّعة معلومة النتيجة. وأول تشغيل أسقط
   **توقّعي أنا** لا الأداة: كتبتُ حالتين وكانت توقّعاتي ناقصة (`nested/mod.rs` غير مُعلَن ⇒ ميت فعلًا،
   و`caller.c` بلا مُستدعٍ ⇒ ميت فعلًا).
2. **وعطب حقيقي في أول نسخة من الأداة:** كانت تقرأ **المتغيّرات المحلّية** تعريفاتٍ على مستوى الملف،
   فأعلنت `preloadbin` ميتًا لعشرين رمزًا محليًّا. فصار شرط **العمود 0** جزءًا من التعريف، وحالة الاختبار
   الخامسة تقيس ذلك تحديدًا. (وأول جرد C للخادم أعلن `ChargingNodes.c` «ميتًا» وهو **سجلّ بيانات** تقرؤه
   ثلاثة ملفات — فالأداة وسّعت التعريف إلى البيانات لا الدوال وحدها.)

**والتكذيب:** **٣/٣ أُسقطت** ثم عادت خضراء — ملفّ `.rs` يُضاف بلا `mod` ⇒ سقوط · حذف ملفّ مُدرَج في
الأساس ⇒ سقوط («الأساس يقول إنه ميت ولم يعد كذلك») · وحدة C مبنية بلا مُستخدِم ⇒ سقوط. والبوابة مُدرَجة
في خطوة **Contract gates** في CI.

**وحدّها المُعلَن (لا يُطوى):** تحليل نصّي لا مُصرّف ولا رابط — فالنداء عبر مؤشر دالّة أو جدول توزيع أو
`extern` خارج المستودع لا يراه الفحص، ولذلك حكمه على أي ملفّ **يحتاج قراءة** قبل الحذف. والـC يُفحص في
نطاق الخادم المعلَن وحده، لا في المستودع كله.

---

## ١٩. تجربة خطوات CI محليًّا — وما أمكن قياسه وما لم يُمكن

مستودع `origin` **خاص** (`api.github.com/repos/assets 404` بلا توثيق) و`gh` **غير موثَّق** هنا، فلا دفع
ولا قراءة لتشغيل Actions. فالمتاح هو **إعادة تمثيل الخطوات على نسخة نظيفة من الالتزام نفسه**
(`git worktree add /tmp/ci HEAD`) — وهي أقرب ما يراه CI بالحرف، وتقيس **محتوى الالتزام** أيضًا
(هل سقط ملفّ يحتاجه البناء؟).

**والنتيجة:** كل خطوة قابلة للقياس محليًّا **خضراء** (التفصيل في §١٥)، ومنها لأول مرة:
**طبقة `jni_symbols` الثانية** (٢١ رمزًا في الـ`.so` الحقيقية للعمودين)، وبناءات الأصول الخمسة لعمودين،
و`assembleDebug`، و`minifyReleaseWithR8`، و**الاختبارات كاملة على نسخة CI**: **١٧٠ مجموعة ·
١٦٣٣ اختبارًا · ٠ فشل · ٠ خطأ · ٠ متخطّى**.

**وما لا يُقاس محليًّا — يُعلَن ولا يُطوى:** `assembleRelease` يحتاج `KS_PWD` (حارس في `build.gradle.kts`
يرمي قبل أي عمل)، وتتبعه خطوات الحزمة/zip/القطع وتحميل تيليجرام. وهذا هو **حدّ التوقيع** نفسه المُعلَن
في `AGENTS.md` §5 — وتجربة CI لا تُلغيه.

**وتصحيح جهة الخطأ (وهو الأهمّ):** أول ما أسقطته التجربة **لم يكن عطبًا في المصدر** بل **حرسًا صار
خاطئًا بعد أن تغيّر مُدخله في CI**: `MODULE_VERSION` يُعاد كتابته قبل Gradle، وكان الحرس يشترط شكلًا
لا يُوجَد إلا قبل الحقن. فالعطب كان **في مزعم العقد** لا في الكود — وهو ما يُكتشف بالقياس لا بالمراجعة.

---

## §٢٠ — عقد البطاقة الواحد، وسقف الرموز المفروض

**سبب وجود هذا القسم:** مراجعة اتساق الواجهات قاست الحالة قبل الإصلاح، فوجدت أن المشكلة ليست في
شاشة ولا شاشتين بل في **غياب مستوى واحد للبطاقة**:

| المقياس قبل الإصلاح (في `ui/**`) | العدد |
| --- | ---: |
| أرقام حرفية إجمالًا | **٤٨٠** |
| أنصاف أقطار متمايزة | **٢٢** |
| حشو أفقي حرفيّ | ٩٣ |
| تباعد حرفيّ (`spacedBy`) | ١٤٢ |
| تعريفات `*Card` مختلفة | **٣٨** |

و**ثلاث مقاييس أشكال متوازية**: `MaxRadius` (12/14/22/28) و`theme/Shape.kt` (6/10/18/26/32)
و`MaxUiMetrics` (28/18/12) — أي أن «زاوية البطاقة» كان لها ثلاث تهجئات، وقيمتان مختلفتان.

### القاعدة الملزمة

1. **البطاقة شيء واحد يُنادى، لا نمط يُنسخ:** `ui/design/MaxCard.kt` — `MaxCard` (أيقونة ← عنوان ←
   وصف) و`MaxCardGrid`. ولا تُبنى بطاقة جديدة بترتيب داخلي مختلف.
2. **العنوان يحجز `MaxCardSpec.titleLines` سطرين دائمًا**، فلا ينزلق وصف بطاقة لأن عنوان جارتها
   انطبق في سطر. و**بطاقات الصفّ الواحد تتشارك ارتفاعًا واحدًا** (`IntrinsicSize.Min` + `fillMaxHeight`).
3. **الشبكة تُقلّص الأعمدة ولا تقصّ الكلمات:** عدد الأعمدة يُشتقّ من العرض المتاح مقابل
   `MaxCardSpec.minColumnWidth`، لا من صنف الجهاز. فـ`Powe…` يقع لأن عمودًا سُمح له بأن يصير أضيق
   من كلمة — وهذا مُمنوع في `MaxCardGrid`.
4. **الهيكل يملك هامش الصفحة:** `MaxListScreen`/`MaxScreen`/`MaxSplitScreen` تُطبّق `MaxSpace.gutter`.
   وشاشة تضاعفها بحشو أفقي محلّي تُنتج 36dp بدل 20dp — وهو بعينه سبب «المحتوى المضغوط» المُبلَّغ عنه.
5. **مقياس واحد للأشكال:** `theme/Shape.kt` يُشتقّ من `MaxRadius`، و`MaxUiAlpha`/`MaxUiMetrics`
   تُفوَّض إلى `MaxAlpha`/`MaxSpace`/`MaxCardSpec` (ADR-05: نفس الأسماء، القيم القياسية).

### والسقف المفروض

`tools/design_tokens.py` — `--assert` يُحمرّ البناء إن زاد أي حرفيّ عن `design_tokens_baseline.json`
**ويسمّي الملفّ الذي أضافه**؛ و`--update` يُثبّت حالة جديدة بعد إصلاح (قرار مكتوب). وموضعها في خطوة
**Contract gates** في `build.yml`. وحدّها المُعلن: تقيس **الأرقام الحرفية**، ولا تقيس تناسق
الترتيب الداخلي ولا سلوك RTL ولا التجاوب على مقاسات شاشات مختلفة — وتلك تحتاج جهازًا أو مراجعة بصرية.

---

## §٢١ — ما أُضيف إلى عقد الواجهة: صفّ القائمة، شريط التبويبات، رأس القسم، وسلّم القراءة

**سبب القسم:** مراجعة الاتساق قيست مرّتين — الأولى أثمرت `MaxCardSpec` (§٢٠)، والثانية أعادت المالك
قراءة طلبه كاملًا فظهر أن فيه **ثلاثة عطب بنيوي** لم تُمسّ.

### ١) خانة الذيل في صفّ القائمة كانت **بلا حدّ عرض**

العطب المبلَّغ عنه: «Language» في الإعدادات تنكسر **حرفًا في كل سطر**. والسبب ليس النصّ ولا
`maxLines`: `ExpressiveListItem` كان يرسم

```
Column(weight(1f))   ← العنوان: يأخذ ما يتبقّى
Box                 ← الذيل:  بلا أي قيد عرض ⇒ يُقاس عند عرضه الأقصى
```

فاسم لغة طويلة + السهم يأخذان معظم الصفّ، ويبقى للعنوان عمود بعرض حرف. **ولا ينجو من ذلك أي
إعداد `maxLines`/`overflow`، لأن الحاوية هي التي ضاقت — لا النصّ الذي طال.**

**القاعدة الملزمة:** في `ExpressiveListItem` / `ExpressiveListItemHighlight` / `ExpressiveInfoCard`
لا تتجاوز خانة الذيل **`TRAILING_MAX_FRACTION = 0.45`** من عرض الصفّ، فيبقى للعنوان ≥٥٥٪. والقيمة
مقيسة: أطول قيمة ذيل فعليّة `Português (Brasil)` ≈ ١٢٠dp بخطّ `labelMedium`، وأطول كلمة في عنوان
(`Language`) ≈ ٦٥dp بخطّ أكبر — التضخيم إلى الضعف على شاشة ٣٢٠dp داخل الحدّ.

وأُضيف معه `LocalTextStyle` يحمل `LineBreak.Heading` لكل عنوان يمرّ من هذه الصفوف: هو ما يمنع
الكسر **داخل الكلمة** حين يُسحب العنصر إلى عمود أضيق من عرض الكلمة. وموضع واحد يُصلح كل صفّ في
المستودع (ADR-12: منع التعطّل لا إصلاح مثيل).

### ٢) شريط التبويبات: `MaxTabStrip` بدل `TabRow`

`TabRow` تقسم العرض **بالسوية** على التبويبات، فخمسة تبويبات على ٣٦٠dp تعطي كل واحد ≈٦٤dp بعد
الأيقونة — أقل من عرض كلمة `Gaming`. فالنتيجة `Gami…`/`Powe…` **بتصميم المكوّن لا بإعداد خاطئ**،
ولا ينفع فيها `maxLines`، ولا يجوز تصغير الخطّ (نصّ الطلب).

**القاعدة:** كل تبويب يأخذ عرضه الطبيعي (`softWrap = false`) فيستحيل قصّه، والشريط كله يتحرّك
أفقيًّا (`LazyRow`) إن زادت عن الشاشة. **البديل الوحيد للنصّ المقصوص هو مساحة — وهي متوفّرة أفقيًّا
ومعدومة داخل مقعد ثابت.** وصندوق اللمس ٤٨dp لكل مقطع، و`Role.Tab` مع `selected` لقارئ الشاشة.

### ٣) سلسلة الأقسام: `MaxSectionSpec` وترتيب ملزم

كان في التطبيق **ثلاثة رؤوس أقسام بـثلاثة أحجام**: `MaxSectionHeader` (شرطة ٢٨×٤ + `titleLarge`)،
و`NeuralSectionHeader` (٣dp دائريًّا + `15sp`)، و`SettingsSectionTitle` (صندوق ٢٨dp + `titleSmall`
= **حجم عنوان البطاقة نفسه**). فلا يعرف القارئ أيّها عنوان قسم وأيّها عنوان بطاقة.

**والترتيب الملزم الآن، من الأكبر إلى الأصغر، ولا طبقتان بنفس الحجم:**

| الطبقة | النمط |
| --- | --- |
| عنوان صفحة | شارات التطبيق (`headlineSmall` وأكبر) |
| **عنوان قسم** | `titleMedium` عريض — من `MaxSectionSpec` |
| عنوان بطاقة | `titleSmall` — من `MaxCardSpec` |
| وصف مساند | `bodySmall` |

### ٤) بوابة RTL (`tools/rtl_guard.py`)

ثلاثة أصناف مقيسة على مصدر `ui/**`: حشو/هامش بجهة صلبة · `Alignment.TopLeft/TopRight/...` ·
أيقونة اتجاهية غير منعكسة. و`--self-test` **١١/١١** (وفيه ثلاث حالات سالبة شرعيّة: المنطقيّ،
والمنعكس، و`absolutePadding`، وسطر داخل تعليق).

**وأوّل تشغيل لها أمسك ٤ مخالفات لم يرها فحص يدويّ ضيّق:** `Icons.Rounded.Undo` · `Redo`
(`FileEditorDialog`) و`OpenInNew` ×٢ (`FileManagerCommands`) — ثلاثتها لها صيغة `AutoMirrored`.
وأُصلحت الخمس كلها (مع `ArrowBack` في `FileWindowChrome`).

**وحدّها المُعلن:** تقيس **المصدر** لا التخطيط الفعليّ. فشاشة سليمة البنية قد تبقى معطوبة بصريًّا
(ترتيب، مسافة، محاذاة نصّ عربيّ/لاتينيّ مختلط) — **وذاك يحتاج جهازًا**. ولا تقيس الإتاحة (أهداف
اللمس، التباين).

### ٥) وسقف حجم الملفّ صار حدًّا مُنفَّذًا لا توجيهًا

`code_health.py` (سقف ١٠٠٠ سطر) أمسك أن تعديلات هذه الجولة **رفعت ملفّين فوق السقف**
(`NeuralDashboardKit` ١٠٣٥ · `LegendaryHomeDashboard` ١٠٠٦). فالتفكيك لا التوسيع:
`NeuralPill.kt` · `HomeCommandDeck.kt` — ومعها `neuralClickable` من `private` إلى `internal`.

---

## §٢٢ — قشرة البطاقة كائن يُنادى، وأنصاف الأقطار صارت ١٢ لا ٢٢

**الطلب (§٢ · §١١ · §١٢) كان:** «أنشئ رموزًا مركزيّة… وإن غيّرت نصف قطر البطاقة لاحقًا فيجب أن
أغيّره عالميًّا لا في عشرات الشاشات».

### ما قِيس قبل

| المقياس في `ui/**` | قبل §٢٠ | بعد §٢٠ | **الآن** |
| --- | ---: | ---: | ---: |
| أرقام حرفية إجمالًا | ٤٨٠ | ٤٧٣ | **٣٧٤** |
| أنصاف أقطار متمايزة | ٢٢ | ٢٢ | **١٢** |
| `RoundedCornerShape(<رقم>)` | — | ١٣١ | **٣٣** |

### والقاعدتان

**١) `MaxCardShell` — القشرة المشتركة.** `MaxCard` يفرض العقد على بطاقة «أيقونة ← عنوان ← وصف»
فقط، وهي **٤ من ٣٨**؛ والبقية (مقياس دائريّ · مخطط · جدول · معاينة سمة) محتواها لا يشبه ذلك
أصلًا. **ولن يُشوَّه المحتوى ليُوحَّد** — مقياسٌ ليس أيقونة. فما يُوحَّد هو **القشرة**: نصف قطر ·
حدّ · خلفية · حشو · صندوق لمس. و`MaxCardShell` تعطي ذلك كاملًا مع خانة محتوى حرّة.

**٢) والاستبدال كان مُحافظًا للقيمة حيث يجب.** الرموز الجديدة `MaxRadius.tile` (18) و
`MaxRadius.inset` (16) تحمل **القيم نفسها**: صفر تغيير بصريّ والاسم هو المُكتسَب. وإنما وُحّدت
**قيم صنف البطاقة وحده** (18/24/26 → 22): هذه كلها حاويات، وقارئ لا يفرّق بين 22 و24 — فالاختلاف
ضجيج لا تصميم. وما دون ذلك (٢ · ٣ · ٤ · ٦ · ٨ · ١٠) أشكال مجهرية (شرائط تقدّم · مؤشّرات) تختلف
لأنها **أشياء مختلفة** لا لأن أحدًا نسي.

**وحدّ البند المُعلن:** التوحيد **هندسيّ**؛ و**الكثافة** لا تُوحَّد. بطاقة محتواها جدول من ٩ صفوف
تبقى أطول من بطاقة محتواها سطر — لأن تسوية الارتفاع بين محتويين مختلفين تعني حشوًا فارغًا، والحشو
الفارغ **نقص معلومة** لا اتساق.
