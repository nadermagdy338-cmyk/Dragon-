# ⚠️ تذكير دائم بهدف المشروع (MaxManager)

اقرأ هذا الملف **قبل** إضافة أي ميزة جديدة، خصوصًا الميزات المأخوذة من مشاريع
مخصّصة لشركة/جهاز واحد (زي Xiaomi Parts، MIUI/HyperOS tweaks، إلخ).

## 🎯 الهدف الأساسي

> **MaxManager لازم يشتغل صح على أكبر عدد ممكن من الأجهزة**، مش بس أجهزة شاومي.
> أي ميزة جديدة يجب أن تكون **إضافة اختيارية آمنة (opt-in / auto-detected)**،
> مش شرط أساسي يكسر أو يبطئ التجربة على باقي الشركات (Samsung, Realme,
> OnePlus, Motorola... إلخ) أو باقي الشرائح (MediaTek, Snapdragon, Unisoc,
> Exynos, Tensor).

## ✅ اللي اتعمل في هذا التعديل

تمت إضافة ملف جديد: **`mainfiles/xiaomi-extras.sh`**

مستوحى من منطق `ThermalUtils.kt` / `ThermalService.kt` في مشروع Xiaomi Parts:
- يكتب في عقدة `sconfig` الخاصة بمحرك الحرارة الإضافي عند أجهزة MIUI/HyperOS
  (`/sys/devices/virtual/thermal/thermal_message/sconfig`).
- بيرفع بروفايل "شحن" لما الجهاز يكون بيشحن.
- بيرجع للبروفايل الافتراضي لما الشاشة تكون مقفولة.
- بيرفع بروفايل "قيمنق" لو التطبيق الحالي موجود في `maxmanagerApplist.json`
  (نفس القايمة اللي المشروع بيستخدمها أصلاً، مفيش تكرار).

**ليه الطريقة دي أمنة على باقي الأجهزة:**
السطر الأول في الملف بيتحقق من وجود عقدة `sconfig`، ولو مش موجودة (أي جهاز
مش شاومي/MIUI تقريبًا) الملف بيعمل `exit 0` فورًا من غير ما يلمس أي حاجة تانية.

**ملاحظة تنفيذية مهمة:** لازم يتضاف سطر checksum (`.sha256`) للملف الجديد في
الـ CI/build pipeline بتاع الريليز (زي باقي ملفات `.sh`)، وإلا هيفشل التحقق في
`verify.sh` عند التثبيت.

## ✅ تحديث: Touch Sampling Boost و Display Color Mode اتعملوا فعلاً

بعد ما كانوا مؤجلين، اتضافوا في `manager/app/.../zx/maxmanager/XiaomiVendorFeatures.kt`
+ hook بسيط في `AppMonitor.kt` (نفس البروسيس اللي أصلاً بيجمع `screen_awake`/
`is_charging`/`focused_app` كل نص ثانية).

**المشكلة التقنية اللي كانت موجودة:** الـ interfaces دي
وتأكد أن لا يتكرر نفس الوظيفة كمثال شيئ يتعلق بتخطي الشحن ثم نقوم نحن بصنع شيئ مشابه في مكان آخر هذا غباء فقط قل أنه هناك ميزة بنفس الفكرة 
(`vendor.xiaomi.hw.touchfeature.ITouchFeature` و
`vendor.xiaomi.hardware.displayfeature_aidl.IDisplayFeature`) مش SDK عام —
هي static libs موجودة بس جوه شجرة مصدر ROM شاومي نفسها (Soong build)، مش
متاحة على Maven، فمفيش طريقة تتضاف كـ dependency عادي في Gradle.

**الحل اللي اتعمل:** بدل ما نعمل نسخة محلية مخمّنة من ملف الـ `.aidl` (خطر
فعلي — لو الترتيب غلط هيكلم method غلط على هاردوير حقيقي)، الكود بيدور وقت
التشغيل عن الـ jar الحقيقي اللي فيه الـ Stub class الأصلي (لو موجود على
الجهاز أصلاً في `/system/framework` أو `/system_ext/framework` أو غيرهم)،
يحمّله بـ `PathClassLoader`، ويستخدم الـ method بتاعه عن طريق reflection.
بالطريقة دي أي binder call بيمر من الكود الأصلي اللي الـ ROM نفسها مبنية
بيه، فمفيش تخمين لـ transaction code خالص.

**النتيجة:**
- على أي جهاز مش شاومي: الملف كله no-op (مفيش jar يتلاقي أصلاً).
- على أجهزة شاومي بتدعم الـ HIDL القديم (`touchfeature@1.0`) بدل الـ AIDL
  الجديد: برضه no-op متعمد — مش هنحاول ندعمه دلوقتي لاختلاف الـ transport
  (hwbinder مش binder عادي)، محتاج شغل إضافي منفصل.
- على أجهزة HyperOS/MIUI اللي فيها الـ jar والـ service فعلاً: هيشتغل.

**Touch Boost:** بيتفعّل تلقائيًا لما تطبيق من `maxmanagerApplist.json` يكون
هو التطبيق النشط والشاشة شغالة، ويرجع للوضع العادي لما تقفل اللعبة —
بنفس الـ 6 نداءات اللي `TouchSamplingService.kt` بتستخدمها.

**Display Color Mode:** بيفرض STANDARD أثناء الـ AOD (نفس منطق
`ColorService.kt`) ويرجّع المود اللي المستخدم فعلاً مختاره من `Settings`
لما الشاشة تفتح تاني — مش قيمة ثابتة.

### وظيفة اتأجلت لسه: CIT Calibration Launchers

| الميزة | ليه اتأجلت |
|---|---|
| **CIT Calibration Launchers (fingerprint/speaker)** | خاصة تمامًا بأجهزة Xiaomi (تطبيقات `com.miui.cit` و `com.jiiov.fingerprint_factorytest`) ومفيش فايدة منها لأي جهاز تاني أو لموديول تحسين أداء عام — مناسبة لتطبيق "Xiaomi Parts" نفسه بس. |

## 📏 قواعد عامة لازم تتاخد بالسيريس (بتوفر وقت ومشاكل)

1. **افحص وجود المسار قبل الكتابة، دايمًا.**
   نفس النمط المستخدم أصلاً في `write_val()` بملف `preferenced-tweaks.sh`:
   `[ -e "$path" ] || return 1` قبل أي `echo > $path`.

2. **أي ميزة خاصة بشركة/كيرنل واحد = ملف منفصل + auto-detect بمسار hardware
   فعلي، مش بـ `getprop ro.product.brand`.**
   التحقق من وجود العقدة/الـ service نفسه أدق من التحقق من اسم الشركة (بعض
   أجهزة شاومي القديمة أو ROMs معدّلة ممكن ميكونش عندها نفس العقدة).

3. **متكررش منطق موجود.** المشروع أصلاً عنده:
   - كشف التطبيق الحالي + الشحن + حالة الشاشة → عن طريق `AppMonitor.kt`
     (بيكتب في `app_status` كل نص ثانية).
   - قايمة الألعاب المعتمدة → `maxmanagerApplist.json`.
   استخدم المصادر دي بدل ما تعمل polling/detection جديد من الصفر.

4. **جرّب على كل عائلة شرائح موجودة في `binprofiles/src/chipsets/`**
   (mediatek, snapdragon, unisoc, exynos, tensor) قبل ما تعتبر أي تعديل
   "خلص" — الميزة اللي بتشتغل صح على Snapdragon ممكن تفشل بصمت على Unisoc.

5. **خليها fail-safe.** أي سكربت جديد لازم يفشل بهدوء (`return`/`exit 0`
   بدون كسر باقي السكربت) لو المسار مش موجود، عشان مايوقفش تشغيل باقي
   موديول MaxManager على أجهزة تانية.

6. **سجّل بس لما تحتاج.** استخدم `write_log`/`verbose_log` عن طريق
   `sys.maxmanager-service --log/--verboselog` بدل `echo`/`Log.d` مباشر، عشان
   اللوج يتجمع في مكان واحد ويسهل تتبعه وقت الدعم الفني.

7. **متكسرش anti-bootloop.** أي حلقة `while true` أو proccess خلفي جديد
   لازم يتقتل في `post-fs-data.sh` عند إعادة التشغيل (زي ما اتعمل هنا مع
   `pgrep -f xiaomi-extras.sh`) وإلا هيتكرر العملية كل بووت.

## 🗺️ الخطوة الجاية (لو حابب نكمل)

- إضافة toggle داخل تطبيق الـ manager (Compose UI) لتفعيل/تعطيل طبقة
  Xiaomi extras يدويًا، بدل ما تكون auto-detect بس.
- نقل ميزة Touch Sampling Boost لتطبيق الـ manager (كـ Service Kotlin) لو
  حابين ندعم أجهزة شاومي بشكل أعمق.
- توثيق أي عقدة/AIDL جديدة هنا في الجدول فوق أول ما تتضاف، عشان الملف يفضل
  مرجع محدث.
